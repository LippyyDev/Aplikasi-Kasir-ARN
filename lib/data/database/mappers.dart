import 'package:arn_kasir/data/database/app_database.dart';
import 'package:arn_kasir/domain/core/fraction.dart';
import 'package:arn_kasir/domain/inventory/ingredient.dart';
import 'package:arn_kasir/domain/inventory/stock_state.dart';
import 'package:arn_kasir/domain/menu/menu_item.dart';
import 'package:arn_kasir/domain/menu/recipe.dart';
import 'package:arn_kasir/domain/units/dimension.dart';
import 'package:arn_kasir/domain/units/measure_unit.dart';

/// Waktu disimpan sebagai epoch milidetik UTC (CLAUDE.md aturan 3).
int toDbTime(DateTime time) => time.toUtc().millisecondsSinceEpoch;

DateTime fromDbTime(int millis) =>
    DateTime.fromMillisecondsSinceEpoch(millis, isUtc: true);

Ingredient ingredientFromRows(
  IngredientRow row,
  Iterable<IngredientUnitRow> unitRows,
) {
  return Ingredient(
    id: row.id,
    name: row.name,
    dimension: Dimension.fromDb(row.dimension),
    stock: StockState(row.baseStock, row.stockValue),
    purchaseUnitName: row.purchaseUnit,
    minStock: row.minStock,
    customUnits: <CustomUnit>[
      for (final unit in unitRows)
        CustomUnit(
          name: unit.unitName,
          factor: Fraction(unit.factorNumerator, unit.factorDenominator),
        ),
    ],
    archived: row.deletedAt != null,
  );
}

MenuItem menuFromRows(MenuRow row, Iterable<RecipeItemRow> recipeRows) {
  return MenuItem(
    id: row.id,
    name: row.name,
    price: row.price,
    categoryId: row.categoryId,
    active: row.active,
    photoPath: row.photoPath,
    note: row.note,
    recipe: <RecipeLine>[
      for (final item in recipeRows)
        RecipeLine(ingredientId: item.ingredientId, quantity: item.baseQuantity),
    ],
  );
}
