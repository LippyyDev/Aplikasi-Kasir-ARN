/// Sumber pengeluaran (PRD 6.3.7, kolom `pengeluaran.sumber`).
enum ExpenseSource {
  manual('manual'),
  restock('restok'),
  shrinkage('susut');

  const ExpenseSource(this.dbValue);
  final String dbValue;

  static ExpenseSource fromDb(String value) {
    for (final source in values) {
      if (source.dbValue == value) return source;
    }
    throw ArgumentError.value(value, 'value', 'Sumber pengeluaran tidak dikenal');
  }
}

/// Kategori pengeluaran bawaan sistem. Dibuat saat database dibuat dengan id tetap dan
/// tidak dapat diubah atau dihapus pengguna.
abstract final class SystemExpenseCategory {
  /// Pembelian bahan baku (otomatis dari restok).
  static const int ingredients = 1;

  /// Susut hasil opname (otomatis).
  static const int shrinkage = 2;

  static bool isSystem(int id) => id == ingredients || id == shrinkage;
}

/// Catatan pengeluaran yang akan ditulis.
final class ExpenseEntry {
  const ExpenseEntry({
    required this.time,
    required this.categoryId,
    required this.amount,
    required this.source,
    this.note,
    this.referenceId,
  });

  final DateTime time;
  final int categoryId;
  final int amount;
  final ExpenseSource source;
  final String? note;

  /// Id pergerakan stok penyebab (restok atau susut).
  final int? referenceId;
}
