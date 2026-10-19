import 'package:arn_kasir/domain/inventory/stock_state.dart';
import 'package:arn_kasir/domain/units/dimension.dart';
import 'package:arn_kasir/domain/units/measure_unit.dart';

/// Tingkat stok menurut batas minimum. Urutan indeks menandakan tingkat keparahan.
enum StockLevel { ok, low, out }

/// Aman bila di atas batas minimum, menipis bila kurang dari atau sama dengan batas,
/// habis bila nol atau kurang (PRD 6.3.1).
StockLevel stockLevelOf(int quantity, int minStock) {
  if (quantity <= 0) return StockLevel.out;
  if (quantity <= minStock) return StockLevel.low;
  return StockLevel.ok;
}

/// Bahan baku beserta stok, nilai, dan satuannya.
final class Ingredient {
  const Ingredient({
    required this.id,
    required this.name,
    required this.dimension,
    required this.stock,
    this.purchaseUnitName,
    this.minStock = 0,
    this.customUnits = const <CustomUnit>[],
    this.archived = false,
  });

  final int id;
  final String name;
  final Dimension dimension;

  /// Jumlah (satuan dasar) dan nilai (Rupiah) stok.
  final StockState stock;

  /// Nama satuan beli (kg, liter, karton); `null` berarti satuan dasar.
  final String? purchaseUnitName;

  /// Batas peringatan dalam satuan dasar.
  final int minStock;

  final List<CustomUnit> customUnits;
  final bool archived;

  UnitCatalog get catalog => UnitCatalog(dimension, customUnits);

  StockLevel get level => stockLevelOf(stock.quantity, minStock);

  /// Rata-rata biaya hanya terdefinisi bila ada stok positif (PRD 7.4: "HPP belum lengkap").
  bool get hasAverageCost => stock.quantity > 0;

  Ingredient copyWith({StockState? stock}) => Ingredient(
        id: id,
        name: name,
        dimension: dimension,
        stock: stock ?? this.stock,
        purchaseUnitName: purchaseUnitName,
        minStock: minStock,
        customUnits: customUnits,
        archived: archived,
      );

  @override
  String toString() => 'Ingredient($id, $name, $stock)';
}
