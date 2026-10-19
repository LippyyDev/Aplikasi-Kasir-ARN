import 'package:arn_kasir/domain/core/failures.dart';
import 'package:arn_kasir/domain/core/fraction.dart';
import 'package:arn_kasir/domain/units/dimension.dart';

/// Satuan ukur beserta faktor ke satuan dasar dimensinya (1 [name] = [factor] satuan dasar).
final class MeasureUnit {
  const MeasureUnit({
    required this.name,
    required this.dimension,
    required this.factor,
  });

  final String name;
  final Dimension dimension;
  final Fraction factor;

  @override
  String toString() => 'MeasureUnit($name = $factor ${dimension.baseUnitName})';
}

/// Satuan buatan pengguna untuk satu bahan, mis. "sendok makan" = 12 g.
/// Dimensinya selalu mengikuti bahan pemiliknya.
final class CustomUnit {
  const CustomUnit({required this.name, required this.factor});

  final String name;
  final Fraction factor;

  @override
  bool operator ==(Object other) =>
      other is CustomUnit && other.name == name && other.factor == factor;

  @override
  int get hashCode => Object.hash(name, factor);

  @override
  String toString() => 'CustomUnit($name = $factor)';
}

/// Satuan baku yang tersedia untuk semua bahan. Satuan lintas dimensi sengaja ikut
/// terdaftar agar penggunaannya menghasilkan [DimensionMismatch], bukan [UnknownUnit].
abstract final class StandardUnits {
  static final List<MeasureUnit> all = <MeasureUnit>[
    MeasureUnit(name: 'mg', dimension: Dimension.mass, factor: Fraction(1, 1000)),
    MeasureUnit(name: 'g', dimension: Dimension.mass, factor: Fraction.one),
    MeasureUnit(name: 'kg', dimension: Dimension.mass, factor: Fraction(1000)),
    MeasureUnit(name: 'ml', dimension: Dimension.volume, factor: Fraction.one),
    MeasureUnit(
      name: 'liter',
      dimension: Dimension.volume,
      factor: Fraction(1000),
    ),
    MeasureUnit(name: 'buah', dimension: Dimension.count, factor: Fraction.one),
  ];
}

/// Kumpulan satuan yang berlaku untuk satu bahan: satuan baku dan satuan kustomnya.
final class UnitCatalog {
  UnitCatalog._(this.dimension, this.customUnits, this._byName);

  /// Membangun katalog. Melempar [InvalidUnitDefinition] bila nama kosong, bentrok dengan
  /// satuan baku atau satuan kustom lain (tanpa membedakan huruf besar kecil), atau
  /// faktor tidak positif.
  factory UnitCatalog(
    Dimension dimension, [
    Iterable<CustomUnit> customUnits = const <CustomUnit>[],
  ]) {
    final byName = <String, MeasureUnit>{};
    for (final unit in StandardUnits.all) {
      byName[_key(unit.name)] = unit;
    }

    final customs = <CustomUnit>[];
    for (final custom in customUnits) {
      final key = _key(custom.name);
      if (key.isEmpty) {
        throw InvalidUnitDefinition(custom.name, InvalidUnitReason.emptyName);
      }
      if (!custom.factor.isPositive) {
        throw InvalidUnitDefinition(
          custom.name,
          InvalidUnitReason.nonPositiveFactor,
        );
      }
      if (byName.containsKey(key)) {
        throw InvalidUnitDefinition(
          custom.name,
          InvalidUnitReason.nameCollision,
        );
      }
      byName[key] = MeasureUnit(
        name: custom.name.trim(),
        dimension: dimension,
        factor: custom.factor,
      );
      customs.add(custom);
    }
    return UnitCatalog._(dimension, List.unmodifiable(customs), byName);
  }

  final Dimension dimension;
  final List<CustomUnit> customUnits;
  final Map<String, MeasureUnit> _byName;

  static String _key(String name) => name.trim().toLowerCase();

  /// Mencari satuan menurut nama. Melempar [UnknownUnit] bila tidak ada.
  MeasureUnit resolve(String name) {
    final unit = _byName[_key(name)];
    if (unit == null) throw UnknownUnit(name);
    return unit;
  }

  /// Satuan yang dapat dipilih untuk bahan ini: satuan baku sedimensi (satuan dasar
  /// pertama) lalu satuan kustom.
  List<MeasureUnit> get selectable {
    final standard = StandardUnits.all
        .where((u) => u.dimension == dimension)
        .toList()
      ..sort((a, b) => a.factor.compareTo(b.factor));
    final customs = customUnits.map((c) => resolve(c.name));
    return <MeasureUnit>[...standard, ...customs];
  }

  /// Satuan dasar bahan (faktor 1).
  MeasureUnit get baseUnit => resolve(dimension.baseUnitName);
}
