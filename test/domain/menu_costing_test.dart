import 'package:arn_kasir/domain/inventory/ingredient.dart';
import 'package:arn_kasir/domain/inventory/stock_state.dart';
import 'package:arn_kasir/domain/menu/menu_costing.dart';
import 'package:arn_kasir/domain/menu/menu_item.dart';
import 'package:arn_kasir/domain/menu/recipe.dart';
import 'package:arn_kasir/domain/units/dimension.dart';
import 'package:flutter_test/flutter_test.dart';

import '../support/fixtures.dart';

void main() {
  group('C. HPP dan margin', () {
    final catalog = FixtureA.catalog();
    final kopiSusu = catalog.menus[FixtureA.kopiSusuId]!;

    test('C1: HPP per porsi Kopi Susu = 3000 + 1800 + 160 = Rp 4.960', () {
      final costing = MenuCosting.compute(kopiSusu, catalog.ingredients);
      expect(costing.unitCost, 4960);
      expect(costing.incomplete, isFalse);
    });

    test('C2: margin per porsi Rp 13.040 (72,4%)', () {
      final costing = MenuCosting.compute(kopiSusu, catalog.ingredients);
      expect(costing.margin, 13040);
      expect(costing.marginPermille, 724);
    });

    test('C3: menu tanpa resep (produk titipan) berHPP 0 dan tidak "belum lengkap"', () {
      const titipan = MenuItem(id: 9, name: 'Keripik titipan', price: 5000);
      final costing = MenuCosting.compute(titipan, catalog.ingredients);
      expect(costing.unitCost, 0);
      expect(costing.incomplete, isFalse);
      expect(costing.margin, 5000);
    });

    test('C4: bahan yang belum pernah dibeli menandai "HPP belum lengkap"', () {
      const belumDibeli = Ingredient(
        id: 7,
        name: 'Sirup',
        dimension: Dimension.volume,
        stock: StockState.empty,
      );
      const menu = MenuItem(
        id: 10,
        name: 'Es Sirup',
        price: 8000,
        recipe: <RecipeLine>[RecipeLine(ingredientId: 7, quantity: 30)],
      );
      final costing = MenuCosting.compute(
        menu,
        <int, Ingredient>{7: belumDibeli},
      );
      expect(costing.incomplete, isTrue);
      expect(costing.unitCost, 0);
    });

    test('bahan yang tidak ditemukan juga menandai belum lengkap', () {
      final costing = MenuCosting.compute(kopiSusu, const <int, Ingredient>{});
      expect(costing.incomplete, isTrue);
    });

    test('margin per mil bernilai null bila harga jual nol', () {
      const free = MenuItem(id: 11, name: 'Gratis', price: 0);
      expect(MenuCosting.compute(free, const <int, Ingredient>{}).marginPermille, isNull);
    });
  });
}
