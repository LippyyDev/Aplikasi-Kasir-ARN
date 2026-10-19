import 'package:arn_kasir/domain/inventory/stock_change.dart';

/// Satu baris riwayat pergerakan stok sebuah bahan.
final class StockMovementView {
  const StockMovementView({
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

  /// Bertanda, dalam satuan dasar.
  final int quantityDelta;
  final int valueDelta;
  final int? referenceId;
  final String? reason;
}
