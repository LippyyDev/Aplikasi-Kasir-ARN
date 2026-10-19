import 'package:drift/drift.dart';

import 'package:arn_kasir/data/database/app_database.dart';
import 'package:arn_kasir/data/database/loaders.dart';
import 'package:arn_kasir/data/database/mappers.dart';
import 'package:arn_kasir/data/repositories/drift_sale_store.dart';
import 'package:arn_kasir/domain/expenses/expense.dart';
import 'package:arn_kasir/domain/inventory/ingredient.dart';
import 'package:arn_kasir/domain/inventory/inventory_store.dart';

/// Implementasi [InventoryStore] di atas Drift untuk restok dan penyesuaian stok.
final class DriftInventoryStore implements InventoryStore {
  DriftInventoryStore(this._db, {StockUpdateHook? onStockUpdate})
      : _onStockUpdate = onStockUpdate;

  final AppDatabase _db;
  final StockUpdateHook? _onStockUpdate;

  @override
  Future<T> transaction<T>(Future<T> Function() action) =>
      _db.transaction(action);

  @override
  Future<Ingredient?> findIngredient(int id) async =>
      (await _db.loadIngredientsByIds(<int>[id]))[id];

  @override
  Future<int> recordMovement(StockMovementEntry entry) async {
    _onStockUpdate?.call();
    await _db.writeIngredientStock(entry.ingredientId, entry.newState);
    return _db.into(_db.stockMovements).insert(
          StockMovementsCompanion.insert(
            ingredientId: entry.ingredientId,
            time: toDbTime(entry.time),
            type: entry.type.dbValue,
            baseQuantity: entry.quantityDelta,
            value: entry.valueDelta,
            referenceId: Value(entry.referenceId),
            reason: Value(entry.reason),
          ),
        );
  }

  @override
  Future<void> recordExpense(ExpenseEntry entry) async {
    await _db.into(_db.expenses).insert(
          ExpensesCompanion.insert(
            date: toDbTime(entry.time),
            categoryId: entry.categoryId,
            amount: entry.amount,
            note: Value(entry.note),
            source: Value(entry.source.dbValue),
            referenceId: Value(entry.referenceId),
          ),
        );
  }
}
