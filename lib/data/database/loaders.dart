import 'package:drift/drift.dart';

import 'package:arn_kasir/data/database/app_database.dart';
import 'package:arn_kasir/data/database/mappers.dart';
import 'package:arn_kasir/domain/inventory/ingredient.dart';
import 'package:arn_kasir/domain/inventory/stock_state.dart';
import 'package:arn_kasir/domain/menu/menu_item.dart';

/// Pemuat bersama untuk repositori dan store. Ditulis sebagai ekstensi agar semua query
/// memakai pemetaan yang sama dari baris Drift ke model domain.
extension DatabaseLoaders on AppDatabase {
  /// Memuat bahan menurut id, termasuk yang diarsipkan.
  Future<Map<int, Ingredient>> loadIngredientsByIds(Iterable<int> ids) async {
    final wanted = ids.toSet();
    if (wanted.isEmpty) return <int, Ingredient>{};
    final rows =
        await (select(ingredients)..where((t) => t.id.isIn(wanted))).get();
    final unitRows = await (select(ingredientUnits)
          ..where((t) => t.ingredientId.isIn(wanted))
          ..orderBy(<OrderingTerm Function($IngredientUnitsTable)>[
            (t) => OrderingTerm.asc(t.id),
          ]))
        .get();
    final unitsByIngredient = <int, List<IngredientUnitRow>>{};
    for (final unit in unitRows) {
      unitsByIngredient
          .putIfAbsent(unit.ingredientId, () => <IngredientUnitRow>[])
          .add(unit);
    }
    return <int, Ingredient>{
      for (final row in rows)
        row.id: ingredientFromRows(
          row,
          unitsByIngredient[row.id] ?? const <IngredientUnitRow>[],
        ),
    };
  }

  /// Memuat menu yang belum dihapus beserta resepnya.
  Future<Map<int, MenuItem>> loadMenusByIds(Iterable<int> ids) async {
    final wanted = ids.toSet();
    if (wanted.isEmpty) return <int, MenuItem>{};
    final rows = await (select(menus)
          ..where((t) => t.id.isIn(wanted) & t.deletedAt.isNull()))
        .get();
    final recipeRows = await (select(recipeItems)
          ..where((t) => t.menuId.isIn(rows.map((r) => r.id)))
          ..orderBy(<OrderingTerm Function($RecipeItemsTable)>[
            (t) => OrderingTerm.asc(t.id),
          ]))
        .get();
    final recipeByMenu = <int, List<RecipeItemRow>>{};
    for (final item in recipeRows) {
      recipeByMenu.putIfAbsent(item.menuId, () => <RecipeItemRow>[]).add(item);
    }
    return <int, MenuItem>{
      for (final row in rows)
        row.id: menuFromRows(row, recipeByMenu[row.id] ?? const <RecipeItemRow>[]),
    };
  }

  /// Menulis keadaan stok bahan. Melempar [StateError] bila bahan tidak ada, agar
  /// transaksi yang memanggilnya dibatalkan.
  Future<void> writeIngredientStock(int ingredientId, StockState state) async {
    final updated = await (update(ingredients)
          ..where((t) => t.id.equals(ingredientId)))
        .write(
      IngredientsCompanion(
        baseStock: Value(state.quantity),
        stockValue: Value(state.value),
      ),
    );
    if (updated != 1) {
      throw StateError('Bahan $ingredientId tidak ditemukan saat memperbarui stok');
    }
  }
}
