import 'package:arn_kasir/domain/core/rounding.dart';
import 'package:arn_kasir/domain/inventory/ingredient.dart';
import 'package:arn_kasir/domain/inventory/stock_state.dart';
import 'package:arn_kasir/domain/menu/menu_item.dart';

/// HPP dan margin per porsi sebuah menu pada keadaan stok saat ini (PRD 7.4).
final class MenuCosting {
  const MenuCosting({
    required this.unitCost,
    required this.price,
    required this.incomplete,
  });

  /// Menghitung HPP per porsi: jumlah biaya pemakaian tiap bahan resep pada rata-rata
  /// tertimbang saat ini. Menu tanpa resep berbiaya 0 dan tidak pernah "belum lengkap".
  ///
  /// [incomplete] bernilai `true` bila ada bahan resep yang rata-ratanya belum diketahui
  /// (tidak ditemukan, atau stok nol/negatif sehingga belum pernah dibeli).
  factory MenuCosting.compute(
    MenuItem menu,
    Map<int, Ingredient> ingredients,
  ) {
    var cost = 0;
    var incomplete = false;
    for (final line in menu.recipe) {
      final ingredient = ingredients[line.ingredientId];
      if (ingredient == null || !ingredient.hasAverageCost) {
        incomplete = true;
        continue;
      }
      cost += StockLedger.consume(ingredient.stock, line.quantity).cost;
    }
    return MenuCosting(unitCost: cost, price: menu.price, incomplete: incomplete);
  }

  /// HPP per porsi dalam Rupiah.
  final int unitCost;
  final int price;

  /// "HPP belum lengkap".
  final bool incomplete;

  /// Margin per porsi dalam Rupiah.
  int get margin => price - unitCost;

  /// Margin dalam per mil dari harga jual (724 berarti 72,4%), dibulatkan HALF_UP.
  /// `null` bila harga jual nol.
  int? get marginPermille =>
      price <= 0 ? null : mulDivRoundHalfUp(margin, 1000, price);
}
