/// Dimensi fisik sebuah bahan baku. Dimensi menentukan satuan dasar tempat stok dan
/// resep disimpan sebagai bilangan bulat (PRD 7.1).
enum Dimension {
  mass(dbValue: 'massa', baseUnitName: 'g'),
  volume(dbValue: 'volume', baseUnitName: 'ml'),
  count(dbValue: 'hitungan', baseUnitName: 'buah');

  const Dimension({required this.dbValue, required this.baseUnitName});

  /// Nilai yang disimpan pada kolom `bahan_baku.dimensi`.
  final String dbValue;

  /// Nama satuan dasar yang disimpan pada kolom `bahan_baku.satuan_dasar`.
  final String baseUnitName;

  static Dimension fromDb(String value) {
    for (final dimension in values) {
      if (dimension.dbValue == value) return dimension;
    }
    throw ArgumentError.value(value, 'value', 'Dimensi tidak dikenal');
  }
}
