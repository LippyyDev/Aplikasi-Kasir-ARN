import 'package:drift/drift.dart';

import 'package:arn_kasir/data/database/app_database.dart';
import 'package:arn_kasir/data/database/mappers.dart';
import 'package:arn_kasir/domain/core/failures.dart';
import 'package:arn_kasir/domain/expenses/expense.dart';
import 'package:arn_kasir/domain/models/expense_models.dart';
import 'package:arn_kasir/domain/models/report_models.dart';

/// Kategori dan catatan pengeluaran (PRD 6.3.7). Pengeluaran bersumber `restok` dan
/// `susut` dibuat otomatis oleh use case stok dan tidak dapat diubah dari sini.
final class ExpenseRepository {
  ExpenseRepository(this._db);

  final AppDatabase _db;

  Stream<List<ExpenseCategory>> watchCategories() {
    final query = _db.select(_db.expenseCategories)
      ..orderBy(<OrderingTerm Function($ExpenseCategoriesTable)>[
        (t) => OrderingTerm.asc(t.id),
      ]);
    return query.watch().map(
          (rows) => <ExpenseCategory>[
            for (final r in rows) ExpenseCategory(id: r.id, name: r.name),
          ],
        );
  }

  Future<int> addCategory(String name) async {
    final clean = _cleanName(name);
    return _db.transaction(() async {
      await _ensureUniqueName(clean);
      return _db.into(_db.expenseCategories).insert(
            ExpenseCategoriesCompanion.insert(name: clean),
          );
    });
  }

  Future<void> renameCategory(int id, String name) async {
    if (SystemExpenseCategory.isSystem(id)) throw const ProtectedRecord();
    final clean = _cleanName(name);
    await _db.transaction(() async {
      await _ensureUniqueName(clean, exceptId: id);
      final updated = await (_db.update(_db.expenseCategories)
            ..where((t) => t.id.equals(id)))
          .write(ExpenseCategoriesCompanion(name: Value(clean)));
      if (updated != 1) throw const RecordNotFound();
    });
  }

  /// Kategori bawaan sistem tidak dapat dihapus ([ProtectedRecord]); kategori yang masih
  /// dipakai catatan pengeluaran ditolak ([CategoryInUse]).
  Future<void> deleteCategory(int id) async {
    if (SystemExpenseCategory.isSystem(id)) throw const ProtectedRecord();
    await _db.transaction(() async {
      final used = await (_db.select(_db.expenses)
            ..where((t) => t.categoryId.equals(id))
            ..limit(1))
          .get();
      if (used.isNotEmpty) throw const CategoryInUse();
      await (_db.delete(_db.expenseCategories)..where((t) => t.id.equals(id))).go();
    });
  }

  Stream<List<ExpenseRecord>> watchRecords({
    required DateRange range,
    int? categoryId,
  }) {
    return _db.watchLoad<List<ExpenseRecord>>(
      <ResultSetImplementation<dynamic, dynamic>>[
        _db.expenses,
        _db.expenseCategories,
      ],
      () => _loadRecords(range, categoryId),
    );
  }

  Future<List<ExpenseRecord>> _loadRecords(DateRange range, int? categoryId) async {
    final query = _db.select(_db.expenses).join(<Join>[
      innerJoin(
        _db.expenseCategories,
        _db.expenseCategories.id.equalsExp(_db.expenses.categoryId),
      ),
    ])
      ..where(
        _db.expenses.date.isBiggerOrEqualValue(toDbTime(range.from)) &
            _db.expenses.date.isSmallerThanValue(toDbTime(range.to)),
      )
      ..orderBy(<OrderingTerm>[
        OrderingTerm.desc(_db.expenses.date),
        OrderingTerm.desc(_db.expenses.id),
      ]);
    if (categoryId != null) {
      query.where(_db.expenses.categoryId.equals(categoryId));
    }
    final rows = await query.get();
    return <ExpenseRecord>[
      for (final row in rows)
        ExpenseRecord(
          id: row.readTable(_db.expenses).id,
          date: fromDbTime(row.readTable(_db.expenses).date),
          categoryId: row.readTable(_db.expenses).categoryId,
          categoryName: row.readTable(_db.expenseCategories).name,
          amount: row.readTable(_db.expenses).amount,
          source: ExpenseSource.fromDb(row.readTable(_db.expenses).source),
          note: row.readTable(_db.expenses).note,
          referenceId: row.readTable(_db.expenses).referenceId,
        ),
    ];
  }

  /// Mencatat pengeluaran manual. Nominal harus lebih dari nol.
  Future<int> addManual({
    required DateTime date,
    required int categoryId,
    required int amount,
    String? note,
  }) async {
    if (amount <= 0) throw InvalidMoneyAmount(amount);
    return _db.into(_db.expenses).insert(
          ExpensesCompanion.insert(
            date: toDbTime(date),
            categoryId: categoryId,
            amount: amount,
            note: Value(_blankToNull(note)),
          ),
        );
  }

  Future<void> updateManual({
    required int id,
    required DateTime date,
    required int categoryId,
    required int amount,
    String? note,
  }) async {
    if (amount <= 0) throw InvalidMoneyAmount(amount);
    final updated = await (_db.update(_db.expenses)
          ..where((t) => t.id.equals(id) & t.source.equals(ExpenseSource.manual.dbValue)))
        .write(
      ExpensesCompanion(
        date: Value(toDbTime(date)),
        categoryId: Value(categoryId),
        amount: Value(amount),
        note: Value(_blankToNull(note)),
      ),
    );
    if (updated != 1) throw const ProtectedRecord();
  }

  /// Hanya pengeluaran manual yang dapat dihapus; yang otomatis mengikuti riwayat stok.
  Future<void> deleteManual(int id) async {
    final deleted = await (_db.delete(_db.expenses)
          ..where((t) => t.id.equals(id) & t.source.equals(ExpenseSource.manual.dbValue)))
        .go();
    if (deleted != 1) throw const ProtectedRecord();
  }

  String _cleanName(String name) {
    final clean = name.trim();
    if (clean.isEmpty) throw const EmptyName(NameSubject.expenseCategory);
    return clean;
  }

  Future<void> _ensureUniqueName(String name, {int? exceptId}) async {
    final rows = await (_db.select(_db.expenseCategories)
          ..where((t) => t.name.lower().equals(name.toLowerCase())))
        .get();
    if (rows.any((r) => r.id != exceptId)) {
      throw DuplicateName(NameSubject.expenseCategory, name);
    }
  }

  static String? _blankToNull(String? text) {
    final trimmed = text?.trim();
    return (trimmed == null || trimmed.isEmpty) ? null : trimmed;
  }
}
