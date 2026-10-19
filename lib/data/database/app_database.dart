import 'package:drift/drift.dart';

import 'package:arn_kasir/data/database/tables.dart';
import 'package:arn_kasir/domain/expenses/expense.dart';

part 'app_database.g.dart';

/// Database lokal aplikasi (SQLite lewat Drift). Dibuat ulang pada tiap pemulihan data.
@DriftDatabase(
  tables: <Type>[
    MenuCategories,
    Menus,
    Ingredients,
    IngredientUnits,
    RecipeItems,
    SaleTransactions,
    SaleItems,
    SaleIngredientUsages,
    StockMovements,
    ExpenseCategories,
    Expenses,
    AppNotifications,
    BusinessProfiles,
  ],
)
class AppDatabase extends _$AppDatabase {
  AppDatabase(super.e);

  /// Naikkan bersama migrasi eksplisit dan ekspor skema ke `drift_schemas/`
  /// (CLAUDE.md, Konvensi). Dipakai juga oleh manifest cadangan.
  static const int currentSchemaVersion = 1;

  @override
  int get schemaVersion => currentSchemaVersion;

  @override
  MigrationStrategy get migration => MigrationStrategy(
        onCreate: (Migrator m) async {
          await m.createAll();
          await _seed();
        },
        onUpgrade: (Migrator m, int from, int to) async {
          // Belum ada versi sebelumnya. Setiap kenaikan schemaVersion wajib menambah
          // langkah migrasi di sini beserta uji migrasi (BACKLOG #28).
        },
        beforeOpen: (OpeningDetails details) async {
          await customStatement('PRAGMA foreign_keys = ON');
        },
      );

  Future<void> _seed() async {
    // Kategori pengeluaran bawaan dengan id tetap (lihat SystemExpenseCategory).
    await into(expenseCategories).insert(
      ExpenseCategoriesCompanion(
        id: const Value(SystemExpenseCategory.ingredients),
        name: const Value('Bahan baku'),
      ),
    );
    await into(expenseCategories).insert(
      ExpenseCategoriesCompanion(
        id: const Value(SystemExpenseCategory.shrinkage),
        name: const Value('Susut'),
      ),
    );
    for (final name in const <String>[
      'Sewa',
      'Listrik',
      'Gaji',
      'Kemasan',
      'Lain-lain',
    ]) {
      await into(expenseCategories).insert(
        ExpenseCategoriesCompanion(name: Value(name)),
      );
    }
    await into(businessProfiles).insert(
      const BusinessProfilesCompanion(id: Value(1)),
    );
  }

  /// Aliran hasil [load] yang dimuat ulang setiap kali salah satu [tables] berubah.
  ///
  /// Dipakai untuk tampilan yang menggabungkan beberapa tabel (menu dan resep, bahan dan
  /// satuan) sehingga satu perubahan pada tabel mana pun memicu pembaruan layar.
  Stream<T> watchLoad<T>(
    Iterable<ResultSetImplementation<dynamic, dynamic>> tables,
    Future<T> Function() load,
  ) async* {
    yield await load();
    await for (final _ in tableUpdates(TableUpdateQuery.onAllTables(tables))) {
      yield await load();
    }
  }
}
