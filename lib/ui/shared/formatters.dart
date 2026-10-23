import 'package:intl/intl.dart';

import 'package:arn_kasir/domain/core/fraction.dart';
import 'package:arn_kasir/domain/inventory/ingredient.dart';
import 'package:arn_kasir/domain/units/unit_converter.dart';

/// Pemformat tampilan yang mengikuti bahasa terpilih (PRD 6.3.3). Seluruhnya hanya
/// untuk tampilan: nilai tersimpan tetap `int` satuan dasar.
final class AppFormatters {
  AppFormatters(this.localeName, {this.currency = 'Rp'})
      : _integer = NumberFormat.decimalPattern(localeName);

  final String localeName;
  final String currency;
  final NumberFormat _integer;

  /// `Rp 18.000`. Bilangan negatif ditulis `-Rp 18.000`.
  String money(int amount) {
    final text = _integer.format(amount.abs());
    return '${amount < 0 ? '-' : ''}$currency $text';
  }

  String integer(int value) => _integer.format(value);

  /// Persen dari per mil, satu desimal bila perlu: 625 menjadi `62,5%`.
  String permille(int value) {
    final whole = value ~/ 10;
    final tenth = value.abs() % 10;
    final separator = _integer.symbols.DECIMAL_SEP;
    return tenth == 0 ? '$whole%' : '$whole$separator$tenth%';
  }

  String dateTime(DateTime time) =>
      DateFormat.yMMMd(localeName).add_Hm().format(time.toLocal());

  String date(DateTime time) => DateFormat.yMMMd(localeName).format(time.toLocal());

  String dateShort(DateTime time) => DateFormat.Md(localeName).format(time.toLocal());

  String time(DateTime time) => DateFormat.Hm(localeName).format(time.toLocal());

  /// Jumlah dalam satuan tampil, misalnya `1,5 kg`, tanpa `double`.
  String quantity(Ingredient ingredient, int baseAmount, String unitName) {
    final value = UnitConverter(ingredient.catalog).fromBase(unitName, baseAmount);
    return '${_decimal(value)} $unitName';
  }

  /// Stok dalam satuan dasar bahan, misalnya `1.000 g`.
  String baseQuantity(Ingredient ingredient, int baseAmount) =>
      '${_integer.format(baseAmount)} ${ingredient.dimension.baseUnitName}';

  String _decimal(Fraction value) => value.toDecimalString(
        maxFractionDigits: 3,
        decimalSeparator: _integer.symbols.DECIMAL_SEP,
      );

  /// Desimal dari pecahan untuk kolom isian (tanpa pemisah ribuan).
  String inputDecimal(Fraction value) => value.toDecimalString(
        maxFractionDigits: 3,
        decimalSeparator: _integer.symbols.DECIMAL_SEP,
      );
}
