import 'package:arn_kasir/domain/inventory/ingredient.dart';
import 'package:arn_kasir/domain/menu/menu_item.dart';

/// Tampilan baca menu dan bahan yang dibutuhkan aturan ketersediaan, HPP, dan penjualan.
final class StockCatalog {
  const StockCatalog({required this.menus, required this.ingredients});

  final Map<int, MenuItem> menus;
  final Map<int, Ingredient> ingredients;
}
