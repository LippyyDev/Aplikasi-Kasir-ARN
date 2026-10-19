import 'package:arn_kasir/domain/expenses/expense.dart';
import 'package:arn_kasir/domain/inventory/ingredient.dart';
import 'package:arn_kasir/domain/inventory/inventory_store.dart';
import 'package:arn_kasir/domain/inventory/stock_change.dart';
import 'package:arn_kasir/domain/inventory/stock_state.dart';
import 'package:arn_kasir/domain/menu/menu_item.dart';
import 'package:arn_kasir/domain/menu/stock_catalog.dart';
import 'package:arn_kasir/domain/sales/sale_planner.dart';
import 'package:arn_kasir/domain/sales/sale_record.dart';
import 'package:arn_kasir/domain/sales/sale_store.dart';

final class StoredMovement {
  const StoredMovement({
    required this.id,
    required this.ingredientId,
    required this.time,
    required this.type,
    required this.quantityDelta,
    required this.valueDelta,
    this.referenceId,
    this.reason,
  });

  final int id;
  final int ingredientId;
  final DateTime time;
  final MovementType type;
  final int quantityDelta;
  final int valueDelta;
  final int? referenceId;
  final String? reason;
}

/// Penyimpanan dalam memori untuk menguji use case domain tanpa database.
/// Mendukung transaksi atomik (snapshot dan pemulihan) dan injeksi galat.
final class InMemoryStore implements SaleStore, InventoryStore {
  final Map<int, Ingredient> ingredients = <int, Ingredient>{};
  final Map<int, MenuItem> menus = <int, MenuItem>{};
  List<SaleRecord> sales = <SaleRecord>[];
  List<StoredMovement> movements = <StoredMovement>[];
  List<ExpenseEntry> expenses = <ExpenseEntry>[];

  int _nextSaleId = 1;
  int _nextMovementId = 1;

  /// Bila diisi n, pembaruan stok ke-n (sejak diatur) melempar galat.
  int? _failOnStockUpdate;
  int _stockUpdates = 0;

  void failOnStockUpdate(int updateNumber) {
    _failOnStockUpdate = updateNumber;
    _stockUpdates = 0;
  }

  void _beforeStockUpdate() {
    _stockUpdates += 1;
    if (_failOnStockUpdate == _stockUpdates) {
      throw StateError('Galat simulasi pada pembaruan stok ke-$_stockUpdates');
    }
  }

  void _setStock(int ingredientId, StockState state) {
    final current = ingredients[ingredientId];
    if (current == null) throw StateError('Bahan $ingredientId tidak ada');
    ingredients[ingredientId] = current.copyWith(stock: state);
  }

  @override
  Future<T> transaction<T>(Future<T> Function() action) async {
    final savedIngredients = Map<int, Ingredient>.of(ingredients);
    final savedSales = List<SaleRecord>.of(sales);
    final savedMovements = List<StoredMovement>.of(movements);
    final savedExpenses = List<ExpenseEntry>.of(expenses);
    final savedSaleId = _nextSaleId;
    final savedMovementId = _nextMovementId;
    try {
      return await action();
    } catch (_) {
      ingredients
        ..clear()
        ..addAll(savedIngredients);
      sales = savedSales;
      movements = savedMovements;
      expenses = savedExpenses;
      _nextSaleId = savedSaleId;
      _nextMovementId = savedMovementId;
      rethrow;
    }
  }

  // --- SaleStore -------------------------------------------------------------

  @override
  Future<StockCatalog> loadCatalog(Iterable<int> menuIds) async {
    final loadedMenus = <int, MenuItem>{};
    final ingredientIds = <int>{};
    for (final id in menuIds) {
      final menu = menus[id];
      if (menu == null) continue;
      loadedMenus[id] = menu;
      ingredientIds.addAll(menu.recipe.map((line) => line.ingredientId));
    }
    return StockCatalog(
      menus: loadedMenus,
      ingredients: <int, Ingredient>{
        for (final id in ingredientIds)
          if (ingredients[id] != null) id: ingredients[id]!,
      },
    );
  }

  @override
  Future<String> allocateReceiptNumber(DateTime localTime) async {
    final prefix = 'ARN-${localTime.year.toString().padLeft(4, '0')}'
        '${localTime.month.toString().padLeft(2, '0')}'
        '${localTime.day.toString().padLeft(2, '0')}';
    final count = sales.where((s) => s.number.startsWith(prefix)).length;
    return '$prefix-${(count + 1).toString().padLeft(4, '0')}';
  }

  @override
  Future<int> insertSale(SalePlan plan) async {
    final id = _nextSaleId++;
    sales.add(plan.sale.copyWith(id: id));
    for (final change in plan.stockChanges) {
      _beforeStockUpdate();
      _setStock(change.ingredientId, change.after);
      movements.add(
        StoredMovement(
          id: _nextMovementId++,
          ingredientId: change.ingredientId,
          time: plan.sale.time,
          type: MovementType.sale,
          quantityDelta: change.quantityDelta,
          valueDelta: change.valueDelta,
          referenceId: id,
        ),
      );
    }
    return id;
  }

  @override
  Future<SaleRecord?> findSale(int saleId) async {
    for (final sale in sales) {
      if (sale.id == saleId) return sale;
    }
    return null;
  }

  @override
  Future<Map<int, Ingredient>> loadIngredients(Iterable<int> ids) async => {
        for (final id in ids)
          if (ingredients[id] != null) id: ingredients[id]!,
      };

  @override
  Future<void> applyVoid(VoidPlan plan, DateTime time) async {
    final index = sales.indexWhere((s) => s.id == plan.saleId);
    if (index < 0) throw StateError('Transaksi ${plan.saleId} tidak ada');
    sales[index] = sales[index].copyWith(status: SaleStatus.voided);
    for (final change in plan.stockChanges) {
      _beforeStockUpdate();
      _setStock(change.ingredientId, change.after);
      movements.add(
        StoredMovement(
          id: _nextMovementId++,
          ingredientId: change.ingredientId,
          time: time,
          type: MovementType.voidSale,
          quantityDelta: change.quantityDelta,
          valueDelta: change.valueDelta,
          referenceId: plan.saleId,
        ),
      );
    }
  }

  // --- InventoryStore --------------------------------------------------------

  @override
  Future<Ingredient?> findIngredient(int id) async => ingredients[id];

  @override
  Future<int> recordMovement(StockMovementEntry entry) async {
    _beforeStockUpdate();
    _setStock(entry.ingredientId, entry.newState);
    final id = _nextMovementId++;
    movements.add(
      StoredMovement(
        id: id,
        ingredientId: entry.ingredientId,
        time: entry.time,
        type: entry.type,
        quantityDelta: entry.quantityDelta,
        valueDelta: entry.valueDelta,
        referenceId: entry.referenceId,
        reason: entry.reason,
      ),
    );
    return id;
  }

  @override
  Future<void> recordExpense(ExpenseEntry entry) async {
    expenses.add(entry);
  }
}
