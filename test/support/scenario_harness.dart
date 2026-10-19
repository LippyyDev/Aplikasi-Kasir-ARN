import 'package:arn_kasir/domain/expenses/expense.dart';
import 'package:arn_kasir/domain/inventory/inventory_store.dart';
import 'package:arn_kasir/domain/inventory/stock_change.dart';
import 'package:arn_kasir/domain/inventory/stock_state.dart';
import 'package:arn_kasir/domain/sales/sale_record.dart';
import 'package:arn_kasir/domain/sales/sale_store.dart';

final class MovementView {
  const MovementView({
    required this.ingredientName,
    required this.type,
    required this.quantityDelta,
    required this.valueDelta,
    this.referenceId,
    this.reason,
  });

  final String ingredientName;
  final MovementType type;
  final int quantityDelta;
  final int valueDelta;
  final int? referenceId;
  final String? reason;
}

final class ExpenseView {
  const ExpenseView({
    required this.categoryId,
    required this.amount,
    required this.source,
    this.referenceId,
  });

  final int categoryId;
  final int amount;
  final ExpenseSource source;
  final int? referenceId;
}

/// Pembungkus agar skenario kasus uji yang sama dapat dijalankan terhadap implementasi
/// dalam memori dan terhadap Drift (SQLite) sungguhan (BACKLOG #9).
abstract class ScenarioHarness {
  SaleStore get saleStore;
  InventoryStore get inventoryStore;

  /// Mengisi Fixture A (TEST_CASES_TAKARAN bagian B) dan menu Latte.
  Future<void> seedFixtureA();

  Future<int> ingredientId(String name);
  Future<int> menuId(String name);
  Future<StockState> stockOf(String ingredientName);

  /// Mengatur langsung keadaan stok sebagai prasyarat skenario.
  Future<void> overrideStock(String ingredientName, StockState state);

  /// Mengganti resep menu: nama bahan menjadi jumlah per porsi (satuan dasar).
  Future<void> changeRecipe(String menuName, Map<String, int> recipe);

  Future<SaleRecord> loadSale(int saleId);
  Future<int> saleCount();
  Future<List<MovementView>> movements();
  Future<List<ExpenseView>> expenses();

  /// Pembaruan stok ke-[updateNumber] berikutnya melempar galat (TC E8).
  void failOnStockUpdate(int updateNumber);

  Future<void> dispose();
}
