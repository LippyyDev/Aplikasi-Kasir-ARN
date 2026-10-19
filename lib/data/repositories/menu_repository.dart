import 'package:drift/drift.dart';

import 'package:arn_kasir/data/database/app_database.dart';
import 'package:arn_kasir/data/database/loaders.dart';
import 'package:arn_kasir/data/database/mappers.dart';
import 'package:arn_kasir/domain/core/clock.dart';
import 'package:arn_kasir/domain/core/failures.dart';
import 'package:arn_kasir/domain/menu/menu_item.dart';
import 'package:arn_kasir/domain/menu/recipe.dart';

/// Dipanggil setelah transaksi berhasil untuk menghapus berkas foto yang tidak lagi
/// dirujuk menu mana pun.
typedef PhotoRelease = Future<void> Function(String photoPath);

/// Data menu yang diisi pengguna pada formulir (PRD 6.3.4b).
final class MenuDraft {
  const MenuDraft({
    required this.name,
    required this.price,
    this.id,
    this.categoryId,
    this.active = true,
    this.note,
    this.photoPath,
    this.recipe = const <RecipeLine>[],
  });

  /// `null` untuk menu baru.
  final int? id;
  final String name;

  /// Harga jual, Rupiah bulat.
  final int price;
  final int? categoryId;
  final bool active;
  final String? note;

  /// Nama berkas foto relatif terhadap folder gambar aplikasi.
  final String? photoPath;
  final List<RecipeLine> recipe;

  factory MenuDraft.fromMenu(MenuItem menu) => MenuDraft(
        id: menu.id,
        name: menu.name,
        price: menu.price,
        categoryId: menu.categoryId,
        active: menu.active,
        note: menu.note,
        photoPath: menu.photoPath,
        recipe: menu.recipe,
      );
}

final class MenuRepository {
  MenuRepository(this._db, {Clock? clock, PhotoRelease? releasePhoto})
      : _clock = clock ?? systemClock,
        _releasePhoto = releasePhoto;

  final AppDatabase _db;
  final Clock _clock;
  final PhotoRelease? _releasePhoto;

  /// Semua menu yang belum dihapus, berurutan menurut nama. Menu nonaktif ikut disertakan;
  /// penyaringan untuk Halaman Utama dilakukan di lapisan presentasi.
  Stream<List<MenuItem>> watchAll() {
    return _db.watchLoad<List<MenuItem>>(
      <ResultSetImplementation<dynamic, dynamic>>[_db.menus, _db.recipeItems],
      _loadAll,
    );
  }

  Future<List<MenuItem>> _loadAll() async {
    final rows = await (_db.select(_db.menus)
          ..where((t) => t.deletedAt.isNull())
          ..orderBy(<OrderingTerm Function($MenusTable)>[
            (t) => OrderingTerm.asc(t.name.lower()),
            (t) => OrderingTerm.asc(t.id),
          ]))
        .get();
    final recipeRows = await (_db.select(_db.recipeItems)
          ..orderBy(<OrderingTerm Function($RecipeItemsTable)>[
            (t) => OrderingTerm.asc(t.id),
          ]))
        .get();
    final recipeByMenu = <int, List<RecipeItemRow>>{};
    for (final item in recipeRows) {
      recipeByMenu.putIfAbsent(item.menuId, () => <RecipeItemRow>[]).add(item);
    }
    return <MenuItem>[
      for (final row in rows)
        menuFromRows(row, recipeByMenu[row.id] ?? const <RecipeItemRow>[]),
    ];
  }

  Future<MenuItem?> find(int id) async =>
      (await _db.loadMenusByIds(<int>[id]))[id];

  /// Menambah atau mengubah menu beserta resepnya dalam satu transaksi. Mengembalikan id.
  Future<int> save(MenuDraft draft) async {
    final name = draft.name.trim();
    if (name.isEmpty) throw const EmptyName(NameSubject.menu);
    if (draft.price < 0) throw InvalidMoneyAmount(draft.price);
    validateRecipe(draft.recipe);

    String? releasedPhoto;
    final id = await _db.transaction(() async {
      if (draft.recipe.isNotEmpty) {
        final known = await _db.loadIngredientsByIds(
          draft.recipe.map((line) => line.ingredientId),
        );
        for (final line in draft.recipe) {
          final ingredient = known[line.ingredientId];
          if (ingredient == null) throw IngredientNotFound(line.ingredientId);
        }
      }

      final now = toDbTime(_clock());
      final note = _blankToNull(draft.note);
      final existingId = draft.id;
      final int menuId;

      if (existingId == null) {
        menuId = await _db.into(_db.menus).insert(
              MenusCompanion.insert(
                name: name,
                price: draft.price,
                categoryId: Value(draft.categoryId),
                photoPath: Value(draft.photoPath),
                active: Value(draft.active),
                note: Value(note),
                createdAt: now,
                updatedAt: now,
              ),
            );
      } else {
        menuId = existingId;
        final current = await (_db.select(_db.menus)
              ..where((t) => t.id.equals(menuId) & t.deletedAt.isNull()))
            .getSingleOrNull();
        if (current == null) throw const RecordNotFound();
        if (current.photoPath != null && current.photoPath != draft.photoPath) {
          releasedPhoto = current.photoPath;
        }
        await (_db.update(_db.menus)..where((t) => t.id.equals(menuId))).write(
          MenusCompanion(
            name: Value(name),
            price: Value(draft.price),
            categoryId: Value(draft.categoryId),
            photoPath: Value(draft.photoPath),
            active: Value(draft.active),
            note: Value(note),
            updatedAt: Value(now),
          ),
        );
        await (_db.delete(_db.recipeItems)..where((t) => t.menuId.equals(menuId)))
            .go();
      }

      for (final line in draft.recipe) {
        await _db.into(_db.recipeItems).insert(
              RecipeItemsCompanion.insert(
                menuId: menuId,
                ingredientId: line.ingredientId,
                baseQuantity: line.quantity,
              ),
            );
      }
      return menuId;
    });

    await _releaseIfUnreferenced(releasedPhoto);
    return id;
  }

  /// Menggandakan menu (untuk varian) beserta resepnya. Foto tidak dibagi: salinan tanpa
  /// foto agar penghapusan foto salah satu menu tidak memengaruhi yang lain.
  Future<int> duplicate(int id) async {
    final source = await find(id);
    if (source == null) throw const RecordNotFound();
    return save(
      MenuDraft(
        name: '${source.name} (salinan)',
        price: source.price,
        categoryId: source.categoryId,
        active: source.active,
        note: source.note,
        recipe: source.recipe,
      ),
    );
  }

  /// Menu selalu dihapus lunak: transaksi lama merujuk ke menu ini (CLAUDE.md aturan 7).
  Future<void> softDelete(int id) async {
    String? photo;
    await _db.transaction(() async {
      final current = await (_db.select(_db.menus)
            ..where((t) => t.id.equals(id) & t.deletedAt.isNull()))
          .getSingleOrNull();
      if (current == null) throw const RecordNotFound();
      photo = current.photoPath;
      await (_db.update(_db.menus)..where((t) => t.id.equals(id))).write(
        MenusCompanion(
          deletedAt: Value(toDbTime(_clock())),
          photoPath: const Value(null),
        ),
      );
    });
    await _releaseIfUnreferenced(photo);
  }

  Future<void> setActive(int id, {required bool active}) async {
    final updated = await (_db.update(_db.menus)
          ..where((t) => t.id.equals(id) & t.deletedAt.isNull()))
        .write(
      MenusCompanion(
        active: Value(active),
        updatedAt: Value(toDbTime(_clock())),
      ),
    );
    if (updated != 1) throw const RecordNotFound();
  }

  Future<void> _releaseIfUnreferenced(String? photo) async {
    final release = _releasePhoto;
    if (photo == null || release == null) return;
    final row = await _db
        .customSelect(
          'SELECT COUNT(*) AS c FROM menu WHERE foto_path = ?',
          variables: <Variable<Object>>[Variable.withString(photo)],
          readsFrom: <ResultSetImplementation<dynamic, dynamic>>{_db.menus},
        )
        .getSingle();
    if (row.read<int>('c') == 0) await release(photo);
  }

  static String? _blankToNull(String? text) {
    final trimmed = text?.trim();
    return (trimmed == null || trimmed.isEmpty) ? null : trimmed;
  }
}
