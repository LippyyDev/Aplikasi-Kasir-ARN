import 'package:arn_kasir/data/database/app_database.dart';
import 'package:arn_kasir/data/database/loaders.dart';
import 'package:arn_kasir/data/database/mappers.dart';
import 'package:arn_kasir/data/repositories/drift_inventory_store.dart';
import 'package:arn_kasir/data/repositories/drift_sale_store.dart';
import 'package:arn_kasir/domain/expenses/expense.dart';
import 'package:arn_kasir/domain/inventory/inventory_store.dart';
import 'package:arn_kasir/domain/inventory/stock_change.dart';
import 'package:arn_kasir/domain/inventory/stock_state.dart';
import 'package:arn_kasir/domain/sales/sale_record.dart';
import 'package:arn_kasir/domain/sales/sale_store.dart';
import 'package:drift/drift.dart';
import 'package:drift/native.dart';

import 'fixtures.dart';
import 'scenario_harness.dart';

/// Menjalankan skenario kontrak terhadap SQLite sungguhan (database dalam memori), jadi
/// atomisitas TC E8 diuji pada transaksi Drift, bukan tiruan.
final class DriftHarness extends ScenarioHarness {
  DriftHarness() : db = AppDatabase(NativeDatabase.memory()) {
    _saleStore = DriftSaleStore(db, onStockUpdate: _hook);
    _inventoryStore = DriftInventoryStore(db, onStockUpdate: _hook);
  }

  final AppDatabase db;
  late final DriftSaleStore _saleStore;
  late final DriftInventoryStore _inventoryStore;

  int? _failOn;
  int _updates = 0;

  void _hook() {
    _updates += 1;
    if (_failOn == _updates) {
      throw StateError('Galat simulasi pada pembaruan stok ke-$_updates');
    }
  }

  @override
  SaleStore get saleStore => _saleStore;

  @override
  InventoryStore get inventoryStore => _inventoryStore;

  @override
  Future<void> seedFixtureA() async {
    await db.into(db.menuCategories).insert(
          MenuCategoriesCompanion.insert(name: 'Kopi', sortOrder: const Value(0)),
        );
    for (final ingredient in FixtureA.ingredients()) {
      await db.into(db.ingredients).insert(
            IngredientsCompanion.insert(
              id: Value(ingredient.id),
              name: ingredient.name,
              dimension: ingredient.dimension.dbValue,
              baseUnit: ingredient.dimension.baseUnitName,
              purchaseUnit: Value(ingredient.purchaseUnitName),
              baseStock: Value(ingredient.stock.quantity),
              stockValue: Value(ingredient.stock.value),
              minStock: Value(ingredient.minStock),
            ),
          );
      for (final unit in ingredient.customUnits) {
        await db.into(db.ingredientUnits).insert(
              IngredientUnitsCompanion.insert(
                ingredientId: ingredient.id,
                unitName: unit.name,
                factorNumerator: unit.factor.numerator,
                factorDenominator: unit.factor.denominator,
              ),
            );
      }
    }
    final stamp = toDbTime(DateTime.utc(2026, 1, 1));
    for (final menu in FixtureA.menus()) {
      await db.into(db.menus).insert(
            MenusCompanion.insert(
              id: Value(menu.id),
              name: menu.name,
              price: menu.price,
              categoryId: Value(menu.categoryId),
              createdAt: stamp,
              updatedAt: stamp,
            ),
          );
      for (final line in menu.recipe) {
        await db.into(db.recipeItems).insert(
              RecipeItemsCompanion.insert(
                menuId: menu.id,
                ingredientId: line.ingredientId,
                baseQuantity: line.quantity,
              ),
            );
      }
    }
  }

  @override
  Future<int> ingredientId(String name) async => (await (db.select(db.ingredients)
            ..where((t) => t.name.equals(name)))
          .getSingle())
      .id;

  @override
  Future<int> menuId(String name) async =>
      (await (db.select(db.menus)..where((t) => t.name.equals(name))).getSingle())
          .id;

  @override
  Future<StockState> stockOf(String ingredientName) async {
    final id = await ingredientId(ingredientName);
    return (await db.loadIngredientsByIds(<int>[id]))[id]!.stock;
  }

  @override
  Future<void> overrideStock(String ingredientName, StockState state) async =>
      db.writeIngredientStock(await ingredientId(ingredientName), state);

  @override
  Future<void> changeRecipe(String menuName, Map<String, int> recipe) async {
    final id = await menuId(menuName);
    await (db.delete(db.recipeItems)..where((t) => t.menuId.equals(id))).go();
    for (final entry in recipe.entries) {
      await db.into(db.recipeItems).insert(
            RecipeItemsCompanion.insert(
              menuId: id,
              ingredientId: await ingredientId(entry.key),
              baseQuantity: entry.value,
            ),
          );
    }
  }

  @override
  Future<SaleRecord> loadSale(int saleId) async =>
      (await _saleStore.findSale(saleId))!;

  @override
  Future<int> saleCount() async =>
      (await db.select(db.saleTransactions).get()).length;

  @override
  Future<List<MovementView>> movements() async {
    final rows = await (db.select(db.stockMovements)
          ..orderBy(<OrderingTerm Function($StockMovementsTable)>[
            (t) => OrderingTerm.asc(t.id),
          ]))
        .get();
    final ingredients = await db.select(db.ingredients).get();
    final nameById = <int, String>{for (final i in ingredients) i.id: i.name};
    return <MovementView>[
      for (final r in rows)
        MovementView(
          ingredientName: nameById[r.ingredientId]!,
          type: MovementType.fromDb(r.type),
          quantityDelta: r.baseQuantity,
          valueDelta: r.value,
          referenceId: r.referenceId,
          reason: r.reason,
        ),
    ];
  }

  @override
  Future<List<ExpenseView>> expenses() async {
    final rows = await (db.select(db.expenses)
          ..orderBy(<OrderingTerm Function($ExpensesTable)>[
            (t) => OrderingTerm.asc(t.id),
          ]))
        .get();
    return <ExpenseView>[
      for (final r in rows)
        ExpenseView(
          categoryId: r.categoryId,
          amount: r.amount,
          source: ExpenseSource.fromDb(r.source),
          referenceId: r.referenceId,
        ),
    ];
  }

  @override
  void failOnStockUpdate(int updateNumber) {
    _failOn = updateNumber;
    _updates = 0;
  }

  @override
  Future<void> dispose() => db.close();
}
