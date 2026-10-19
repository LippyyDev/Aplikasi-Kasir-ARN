import 'package:arn_kasir/domain/inventory/ingredient.dart';
import 'package:arn_kasir/domain/inventory/stock_state.dart';
import 'package:arn_kasir/domain/menu/menu_item.dart';
import 'package:arn_kasir/domain/menu/recipe.dart';
import 'package:arn_kasir/domain/menu/stock_catalog.dart';
import 'package:arn_kasir/domain/units/dimension.dart';
import 'package:arn_kasir/domain/units/measure_unit.dart';
import 'package:arn_kasir/domain/core/fraction.dart';

/// Fixture A (TEST_CASES_TAKARAN bagian B) ditambah menu Latte untuk TC D6.
abstract final class FixtureA {
  static const int kopiId = 1;
  static const int susuId = 2;
  static const int gulaId = 3;
  static const int kopiSusuId = 1;
  static const int latteId = 2;

  static const String kopi = 'Kopi bubuk';
  static const String susu = 'Susu';
  static const String gula = 'Gula';
  static const String kopiSusu = 'Kopi Susu';
  static const String latte = 'Latte';

  static List<Ingredient> ingredients() => <Ingredient>[
        Ingredient(
          id: kopiId,
          name: kopi,
          dimension: Dimension.mass,
          stock: const StockState(1000, 200000),
          purchaseUnitName: 'kg',
          minStock: 100,
          customUnits: <CustomUnit>[
            CustomUnit(name: 'sachet', factor: Fraction(25)),
            CustomUnit(name: 'sendok teh', factor: Fraction(5, 2)),
          ],
        ),
        const Ingredient(
          id: susuId,
          name: susu,
          dimension: Dimension.volume,
          stock: StockState(2000, 30000),
          purchaseUnitName: 'liter',
          minStock: 500,
        ),
        Ingredient(
          id: gulaId,
          name: gula,
          dimension: Dimension.mass,
          stock: const StockState(500, 8000),
          purchaseUnitName: 'kg',
          minStock: 100,
          customUnits: <CustomUnit>[
            CustomUnit(name: 'sendok makan', factor: Fraction(12)),
          ],
        ),
      ];

  static List<MenuItem> menus() => const <MenuItem>[
        MenuItem(
          id: kopiSusuId,
          name: kopiSusu,
          price: 18000,
          categoryId: 1,
          recipe: <RecipeLine>[
            RecipeLine(ingredientId: kopiId, quantity: 15),
            RecipeLine(ingredientId: susuId, quantity: 120),
            RecipeLine(ingredientId: gulaId, quantity: 10),
          ],
        ),
        MenuItem(
          id: latteId,
          name: latte,
          price: 22000,
          categoryId: 1,
          recipe: <RecipeLine>[
            RecipeLine(ingredientId: kopiId, quantity: 15),
            RecipeLine(ingredientId: susuId, quantity: 200),
          ],
        ),
      ];

  /// Katalog Fixture A; [stock] menimpa keadaan stok bahan menurut id.
  static StockCatalog catalog({Map<int, StockState> stock = const <int, StockState>{}}) {
    return StockCatalog(
      menus: <int, MenuItem>{for (final m in menus()) m.id: m},
      ingredients: <int, Ingredient>{
        for (final i in ingredients())
          i.id: i.copyWith(stock: stock[i.id] ?? i.stock),
      },
    );
  }
}
