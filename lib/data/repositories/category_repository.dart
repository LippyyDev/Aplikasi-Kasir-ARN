import 'package:drift/drift.dart';

import 'package:arn_kasir/data/database/app_database.dart';
import 'package:arn_kasir/domain/core/failures.dart';
import 'package:arn_kasir/domain/models/menu_category.dart';

/// Kategori menu (PRD 6.3.4a): tambah, ubah nama, urutkan, hapus.
final class CategoryRepository {
  CategoryRepository(this._db);

  final AppDatabase _db;

  Stream<List<MenuCategory>> watchAll() {
    final query = _db.select(_db.menuCategories)
      ..orderBy(<OrderingTerm Function($MenuCategoriesTable)>[
        (t) => OrderingTerm.asc(t.sortOrder),
        (t) => OrderingTerm.asc(t.id),
      ]);
    return query.watch().map(
          (rows) => <MenuCategory>[
            for (final r in rows)
              MenuCategory(id: r.id, name: r.name, sortOrder: r.sortOrder),
          ],
        );
  }

  /// Menambah kategori di urutan terakhir. Nama harus unik tanpa membedakan huruf besar
  /// kecil ([DuplicateName]) dan tidak kosong ([EmptyName]).
  Future<int> add(String name) async {
    final clean = _clean(name);
    return _db.transaction(() async {
      await _ensureUnique(clean);
      final row = await _db
          .customSelect(
            'SELECT COALESCE(MAX(urutan), -1) AS m FROM kategori_menu',
            readsFrom: <ResultSetImplementation<dynamic, dynamic>>{
              _db.menuCategories,
            },
          )
          .getSingle();
      final next = row.read<int>('m') + 1;
      return _db.into(_db.menuCategories).insert(
            MenuCategoriesCompanion(name: Value(clean), sortOrder: Value(next)),
          );
    });
  }

  Future<void> rename(int id, String name) async {
    final clean = _clean(name);
    await _db.transaction(() async {
      await _ensureUnique(clean, exceptId: id);
      await (_db.update(_db.menuCategories)..where((t) => t.id.equals(id)))
          .write(MenuCategoriesCompanion(name: Value(clean)));
    });
  }

  /// Menyimpan urutan baru sesuai posisi pada [orderedIds].
  Future<void> reorder(List<int> orderedIds) async {
    await _db.transaction(() async {
      for (var i = 0; i < orderedIds.length; i++) {
        await (_db.update(_db.menuCategories)
              ..where((t) => t.id.equals(orderedIds[i])))
            .write(MenuCategoriesCompanion(sortOrder: Value(i)));
      }
    });
  }

  /// Jumlah menu (yang belum dihapus) pada kategori ini.
  Future<int> menuCount(int id) async {
    final row = await _db
        .customSelect(
          'SELECT COUNT(*) AS c FROM menu WHERE kategori_id = ? AND dihapus_pada IS NULL',
          variables: <Variable<Object>>[Variable.withInt(id)],
          readsFrom: <ResultSetImplementation<dynamic, dynamic>>{_db.menus},
        )
        .getSingle();
    return row.read<int>('c');
  }

  /// Menghapus kategori. Menu di dalamnya dipindahkan ke [moveMenusTo], atau menjadi
  /// "Tanpa kategori" bila `null`; menu tidak ikut terhapus (TC J4).
  Future<void> delete(int id, {int? moveMenusTo}) async {
    await _db.transaction(() async {
      await (_db.update(_db.menus)..where((t) => t.categoryId.equals(id)))
          .write(MenusCompanion(categoryId: Value(moveMenusTo)));
      await (_db.delete(_db.menuCategories)..where((t) => t.id.equals(id))).go();
    });
  }

  String _clean(String name) {
    final clean = name.trim();
    if (clean.isEmpty) throw const EmptyName(NameSubject.menuCategory);
    return clean;
  }

  Future<void> _ensureUnique(String name, {int? exceptId}) async {
    final rows = await (_db.select(_db.menuCategories)
          ..where((t) => t.name.lower().equals(name.toLowerCase())))
        .get();
    if (rows.any((r) => r.id != exceptId)) {
      throw DuplicateName(NameSubject.menuCategory, name);
    }
  }
}
