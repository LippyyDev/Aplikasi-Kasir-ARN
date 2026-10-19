import 'package:arn_kasir/domain/inventory/ingredient.dart';
import 'package:arn_kasir/domain/inventory/stock_state.dart';

/// Jenis pergerakan stok (PRD 8, tabel `pergerakan_stok`).
enum MovementType {
  restock('restok'),
  sale('penjualan'),
  voidSale('batal'),
  adjustment('penyesuaian');

  const MovementType(this.dbValue);
  final String dbValue;

  static MovementType fromDb(String value) {
    for (final type in values) {
      if (type.dbValue == value) return type;
    }
    throw ArgumentError.value(value, 'value', 'Jenis pergerakan tidak dikenal');
  }
}

/// Perubahan keadaan stok satu bahan akibat satu operasi.
final class StockChange {
  const StockChange({
    required this.ingredientId,
    required this.before,
    required this.after,
  });

  final int ingredientId;
  final StockState before;
  final StockState after;

  /// Negatif untuk pengeluaran stok.
  int get quantityDelta => after.quantity - before.quantity;
  int get valueDelta => after.value - before.value;

  @override
  String toString() => 'StockChange($ingredientId: $before -> $after)';
}

/// Peringatan stok: bahan memburuk ke tingkat "menipis" atau "habis".
final class StockAlert {
  const StockAlert({
    required this.ingredientId,
    required this.ingredientName,
    required this.level,
  });

  final int ingredientId;
  final String ingredientName;
  final StockLevel level;

  @override
  String toString() => 'StockAlert($ingredientName: ${level.name})';
}

/// Peringatan hanya bila tingkat stok **memburuk** akibat perubahan ini. Karena hanya
/// transisi yang dihitung, peringatan tidak berulang pada setiap transaksi selama stok
/// belum pulih di atas batas (PRD 6.3.1).
StockAlert? detectAlert(Ingredient ingredient, StockState after) {
  final before = ingredient.level;
  final now = stockLevelOf(after.quantity, ingredient.minStock);
  if (now.index <= before.index) return null;
  return StockAlert(
    ingredientId: ingredient.id,
    ingredientName: ingredient.name,
    level: now,
  );
}
