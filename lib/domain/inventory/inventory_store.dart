import 'package:arn_kasir/domain/core/clock.dart';
import 'package:arn_kasir/domain/core/failures.dart';
import 'package:arn_kasir/domain/core/fraction.dart';
import 'package:arn_kasir/domain/expenses/expense.dart';
import 'package:arn_kasir/domain/inventory/ingredient.dart';
import 'package:arn_kasir/domain/inventory/stock_change.dart';
import 'package:arn_kasir/domain/inventory/stock_state.dart';
import 'package:arn_kasir/domain/units/unit_converter.dart';

/// Pergerakan stok yang akan ditulis bersama keadaan stok baru bahan.
final class StockMovementEntry {
  const StockMovementEntry({
    required this.ingredientId,
    required this.time,
    required this.type,
    required this.quantityDelta,
    required this.valueDelta,
    required this.newState,
    this.referenceId,
    this.reason,
  });

  final int ingredientId;
  final DateTime time;
  final MovementType type;
  final int quantityDelta;
  final int valueDelta;

  /// Keadaan stok bahan setelah pergerakan ini; ditulis ke bahan dalam transaksi yang sama.
  final StockState newState;
  final int? referenceId;
  final String? reason;
}

/// Penyimpanan untuk restok dan penyesuaian stok.
abstract interface class InventoryStore {
  Future<T> transaction<T>(Future<T> Function() action);

  /// Memuat bahan menurut id (termasuk yang diarsipkan); `null` bila tidak ada.
  Future<Ingredient?> findIngredient(int id);

  /// Menulis keadaan stok baru bahan dan satu baris pergerakan stok.
  /// Mengembalikan id pergerakan.
  Future<int> recordMovement(StockMovementEntry entry);

  Future<void> recordExpense(ExpenseEntry entry);
}

final class RestockResult {
  const RestockResult({
    required this.movementId,
    required this.baseQuantity,
    required this.after,
  });

  final int movementId;
  final int baseQuantity;
  final StockState after;
}

/// Restok (pembelian bahan): menambah stok dan nilai (rata-rata tertimbang) dan otomatis
/// menjadi pengeluaran kategori "Bahan baku" (TC G1, G7).
final class RestockUseCase {
  RestockUseCase(this._store, {Clock? clock}) : _clock = clock ?? systemClock;

  final InventoryStore _store;
  final Clock _clock;

  Future<RestockResult> call({
    required int ingredientId,
    required String unitName,
    required Fraction amount,
    required int totalPrice,
    String? note,
  }) {
    final now = _clock();
    return _store.transaction(() async {
      final ingredient = await _store.findIngredient(ingredientId);
      if (ingredient == null) throw IngredientNotFound(ingredientId);
      if (totalPrice < 0) throw InvalidMoneyAmount(totalPrice);

      final base = UnitConverter(ingredient.catalog).toBase(unitName, amount);
      if (base <= 0) throw const QuantityRoundsToZero();

      final after = StockLedger.restock(
        ingredient.stock,
        quantity: base,
        value: totalPrice,
      );
      final movementId = await _store.recordMovement(
        StockMovementEntry(
          ingredientId: ingredientId,
          time: now,
          type: MovementType.restock,
          quantityDelta: base,
          valueDelta: totalPrice,
          newState: after,
          reason: note,
        ),
      );
      if (totalPrice > 0) {
        await _store.recordExpense(
          ExpenseEntry(
            time: now,
            categoryId: SystemExpenseCategory.ingredients,
            amount: totalPrice,
            source: ExpenseSource.restock,
            note: ingredient.name,
            referenceId: movementId,
          ),
        );
      }
      return RestockResult(
        movementId: movementId,
        baseQuantity: base,
        after: after,
      );
    });
  }
}

final class AdjustStockResult {
  const AdjustStockResult({
    required this.movementId,
    required this.quantityDelta,
    required this.valueDelta,
    required this.shrinkageAmount,
    required this.after,
    this.alert,
  });

  /// `null` bila tidak ada selisih (tidak ada pergerakan yang ditulis).
  final int? movementId;
  final int quantityDelta;
  final int valueDelta;

  /// Nilai susut yang dicatat sebagai pengeluaran (0 bila tidak dicatat).
  final int shrinkageAmount;
  final StockState after;
  final StockAlert? alert;
}

/// Penyesuaian stok (opname): stok fisik menggantikan stok sistem; selisih dicatat sebagai
/// pergerakan `penyesuaian` beserta alasan. Selisih negatif bernilai kerugian dapat dicatat
/// sebagai pengeluaran `susut` bila fitur susut aktif (TC H1 sampai H4).
final class AdjustStockUseCase {
  AdjustStockUseCase(this._store, {Clock? clock}) : _clock = clock ?? systemClock;

  final InventoryStore _store;
  final Clock _clock;

  Future<AdjustStockResult> call({
    required int ingredientId,
    required String unitName,
    required Fraction physicalAmount,
    required bool shrinkageEnabled,
    String? reason,
  }) {
    final now = _clock();
    return _store.transaction(() async {
      final ingredient = await _store.findIngredient(ingredientId);
      if (ingredient == null) throw IngredientNotFound(ingredientId);

      final physical = UnitConverter(ingredient.catalog).toBase(
        unitName,
        physicalAmount,
        allowZero: true,
      );
      final result = StockLedger.adjustTo(ingredient.stock, physical);
      if (result.quantityDelta == 0) {
        return AdjustStockResult(
          movementId: null,
          quantityDelta: 0,
          valueDelta: 0,
          shrinkageAmount: 0,
          after: ingredient.stock,
        );
      }

      final movementId = await _store.recordMovement(
        StockMovementEntry(
          ingredientId: ingredientId,
          time: now,
          type: MovementType.adjustment,
          quantityDelta: result.quantityDelta,
          valueDelta: result.valueDelta,
          newState: result.after,
          reason: reason,
        ),
      );

      var shrinkage = 0;
      if (shrinkageEnabled && result.quantityDelta < 0 && result.valueDelta < 0) {
        shrinkage = -result.valueDelta;
        await _store.recordExpense(
          ExpenseEntry(
            time: now,
            categoryId: SystemExpenseCategory.shrinkage,
            amount: shrinkage,
            source: ExpenseSource.shrinkage,
            note: ingredient.name,
            referenceId: movementId,
          ),
        );
      }
      return AdjustStockResult(
        movementId: movementId,
        quantityDelta: result.quantityDelta,
        valueDelta: result.valueDelta,
        shrinkageAmount: shrinkage,
        after: result.after,
        alert: detectAlert(ingredient, result.after),
      );
    });
  }
}
