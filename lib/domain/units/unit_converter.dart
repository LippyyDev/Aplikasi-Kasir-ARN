import 'package:arn_kasir/domain/core/failures.dart';
import 'package:arn_kasir/domain/core/fraction.dart';
import 'package:arn_kasir/domain/units/measure_unit.dart';

/// Konversi satuan tampil/input ke satuan dasar (bilangan bulat) dan sebaliknya.
///
/// Murni aritmetika pecahan bilangan bulat dengan pembulatan HALF_UP (PRD 7.1).
final class UnitConverter {
  const UnitConverter(this.catalog);

  final UnitCatalog catalog;

  /// Mengubah [amount] dalam [unitName] menjadi satuan dasar bilangan bulat.
  ///
  /// * [UnknownUnit] bila satuan tidak dikenal.
  /// * [DimensionMismatch] bila satuan berdimensi lain (mis. "liter" untuk gula).
  /// * [NonPositiveQuantity] bila [amount] nol atau negatif; dengan [allowZero] nol
  ///   diterima (dipakai untuk stok fisik pada opname).
  ///
  /// Hasil boleh nol bila jumlah positif sangat kecil dibulatkan ke bawah (0,04 g
  /// menjadi 0); pemanggil yang mensyaratkan jumlah bermakna (resep, restok) wajib
  /// menolaknya sendiri.
  int toBase(String unitName, Fraction amount, {bool allowZero = false}) {
    final unit = _resolveSameDimension(unitName);
    final invalid = allowZero ? amount.isNegative : !amount.isPositive;
    if (invalid) throw const NonPositiveQuantity();
    return (amount * unit.factor).roundHalfUp();
  }

  /// Seperti [toBase] dengan input teks ("2,5" atau "2.5").
  int toBaseFromText(String unitName, String text, {bool allowZero = false}) =>
      toBase(unitName, Fraction.parse(text), allowZero: allowZero);

  /// Mengubah jumlah satuan dasar menjadi pecahan dalam [unitName] (untuk tampilan).
  Fraction fromBase(String unitName, int baseAmount) {
    final unit = _resolveSameDimension(unitName);
    return Fraction(baseAmount) / unit.factor;
  }

  MeasureUnit _resolveSameDimension(String unitName) {
    final unit = catalog.resolve(unitName);
    if (unit.dimension != catalog.dimension) {
      throw DimensionMismatch(
        ingredientDimension: catalog.dimension,
        unitName: unit.name,
        unitDimension: unit.dimension,
      );
    }
    return unit;
  }
}
