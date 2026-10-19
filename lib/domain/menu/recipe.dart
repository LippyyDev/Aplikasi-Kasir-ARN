import 'package:arn_kasir/domain/core/failures.dart';
import 'package:arn_kasir/domain/units/unit_converter.dart';

/// Satu baris resep: jumlah bahan per **satu porsi**, dalam satuan dasar (bilangan bulat).
final class RecipeLine {
  const RecipeLine({required this.ingredientId, required this.quantity});

  /// Mengonversi input pengguna (jumlah teks dalam satuan tertentu) menjadi baris resep.
  ///
  /// Menolak jumlah yang setelah dibulatkan menjadi 0 satuan dasar dengan
  /// [QuantityRoundsToZero] (TC A9).
  factory RecipeLine.fromInput({
    required int ingredientId,
    required UnitConverter converter,
    required String unitName,
    required String amountText,
  }) {
    final base = converter.toBaseFromText(unitName, amountText);
    if (base <= 0) throw const QuantityRoundsToZero();
    return RecipeLine(ingredientId: ingredientId, quantity: base);
  }

  final int ingredientId;

  /// Jumlah per porsi dalam satuan dasar; selalu lebih dari 0.
  final int quantity;

  @override
  bool operator ==(Object other) =>
      other is RecipeLine &&
      other.ingredientId == ingredientId &&
      other.quantity == quantity;

  @override
  int get hashCode => Object.hash(ingredientId, quantity);

  @override
  String toString() => 'RecipeLine(bahan $ingredientId x $quantity)';
}

/// Memvalidasi resep: tiap bahan paling banyak sekali dan jumlah lebih dari 0.
/// Resep kosong sah (produk titipan tanpa bahan terlacak).
void validateRecipe(Iterable<RecipeLine> lines) {
  final seen = <int>{};
  for (final line in lines) {
    if (line.quantity <= 0) {
      throw const InvalidRecipe(InvalidRecipeReason.nonPositiveQuantity);
    }
    if (!seen.add(line.ingredientId)) {
      throw const InvalidRecipe(InvalidRecipeReason.duplicateIngredient);
    }
  }
}
