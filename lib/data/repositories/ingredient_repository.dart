import 'package:drift/drift.dart';

import 'package:arn_kasir/data/database/app_database.dart';
import 'package:arn_kasir/data/database/mappers.dart';
import 'package:arn_kasir/domain/core/clock.dart';
import 'package:arn_kasir/domain/core/failures.dart';
import 'package:arn_kasir/domain/inventory/ingredient.dart';
import 'package:arn_kasir/domain/inventory/stock_change.dart';
import 'package:arn_kasir/domain/models/report_models.dart';
import 'package:arn_kasir/domain/models/stock_movement_view.dart';
import 'package:arn_kasir/domain/units/dimension.dart';
import 'package:arn_kasir/domain/units/measure_unit.dart';

/// Data bahan baku yang diisi pengguna pada formulir (PRD 6.3.5).
final class IngredientDraft {
  const IngredientDraft({
    required this.name,
    required this.dimension,
    this.id,
    this.purchaseUnitName,
    this.minStock = 0,
    this.customUnits = const <CustomUnit>[],
    this.openingQuantity = 0,
    this.openingValue = 0,
  });

  /// `null` untuk bahan baru.
  final int? id;
  final String name;
  final Dimension dimension;
  final String? purchaseUnitName;

  /// Batas peringatan dalam satuan dasar.
  final int minStock;
  final List<CustomUnit> customUnits;

  /// Stok awal (satuan dasar) dan nilainya (Rupiah); hanya dipakai saat bahan baru.
  final int openingQuantity;
  final int openingValue;
}

final class IngredientRepository {
  IngredientRepository(this._db, {Clock? clock}) : _clock = clock ?? systemClock;

  final AppDatabase _db;
  final Clock _clock;

  /// Daftar bahan berurutan menurut nama. Bahan yang diarsipkan hanya disertakan bila
  /// diminta.
  Stream<List<Ingredient>> watchAll({bool includeArchived = false}) {
    return _db.watchLoad<List<Ingredient>>(
      <ResultSetImplementation<dynamic, dynamic>>[
        _db.ingredients,
        _db.ingredientUnits,
      ],
      () => _loadAll(includeArchived: includeArchived),
    );
  }

  Stream<Ingredient?> watch(int id) {
    return _db.watchLoad<Ingredient?>(
      <ResultSetImplementation<dynamic, dynamic>>[
        _db.ingredients,
        _db.ingredientUnits,
      ],
      () => find(id),
    );
  }

  Future<List<Ingredient>> _loadAll({required bool includeArchived}) async {
    final query = _db.select(_db.ingredients)
      ..orderBy(<OrderingTerm Function($IngredientsTable)>[
        (t) => OrderingTerm.asc(t.name.lower()),
      ]);
    if (!includeArchived) query.where((t) => t.deletedAt.isNull());
    final rows = await query.get();
    final unitRows = await _db.select(_db.ingredientUnits).get();
    final unitsByIngredient = <int, List<IngredientUnitRow>>{};
    for (final unit in unitRows) {
      unitsByIngredient.putIfAbsent(unit.ingredientId, () => <IngredientUnitRow>[]).add(unit);
    }
    return <Ingredient>[
      for (final row in rows)
        ingredientFromRows(row, unitsByIngredient[row.id] ?? const <IngredientUnitRow>[]),
    ];
  }

  /// Memuat satu bahan, termasuk yang diarsipkan.
  Future<Ingredient?> find(int id) async {
    final row = await (_db.select(_db.ingredients)..where((t) => t.id.equals(id)))
        .getSingleOrNull();
    if (row == null) return null;
    final units = await (_db.select(_db.ingredientUnits)
          ..where((t) => t.ingredientId.equals(id))
          ..orderBy(<OrderingTerm Function($IngredientUnitsTable)>[
            (t) => OrderingTerm.asc(t.id),
          ]))
        .get();
    return ingredientFromRows(row, units);
  }

  /// Menambah atau mengubah bahan. Mengembalikan id bahan.
  ///
  /// Perubahan satuan kustom tidak mengubah stok tersimpan (satuan dasar), hanya cara
  /// tampilnya (TC J5).
  Future<int> save(IngredientDraft draft) async {
    final name = draft.name.trim();
    if (name.isEmpty) throw const EmptyName(NameSubject.ingredient);

    // Validasi satuan: nama tidak bentrok, faktor positif, satuan beli dikenal dan sedimensi.
    final catalog = UnitCatalog(draft.dimension, draft.customUnits);
    final purchase = draft.purchaseUnitName?.trim();
    String? purchaseName;
    if (purchase != null && purchase.isNotEmpty) {
      final unit = catalog.resolve(purchase);
      if (unit.dimension != draft.dimension) {
        throw DimensionMismatch(
          ingredientDimension: draft.dimension,
          unitName: unit.name,
          unitDimension: unit.dimension,
        );
      }
      purchaseName = unit.name;
    }
    final minStock = draft.minStock < 0 ? 0 : draft.minStock;

    return _db.transaction(() async {
      final existing = await (_db.select(_db.ingredients)
            ..where((t) => t.name.lower().equals(name.toLowerCase())))
          .get();
      if (existing.any((r) => r.id != draft.id)) {
        throw DuplicateName(NameSubject.ingredient, name);
      }

      final id = draft.id;
      if (id == null) return _insert(draft, name, purchaseName, minStock);

      final current = await (_db.select(_db.ingredients)..where((t) => t.id.equals(id)))
          .getSingleOrNull();
      if (current == null) throw const RecordNotFound();
      if (current.dimension != draft.dimension.dbValue && await _hasHistory(id)) {
        throw const DimensionLocked();
      }

      await (_db.update(_db.ingredients)..where((t) => t.id.equals(id))).write(
        IngredientsCompanion(
          name: Value(name),
          dimension: Value(draft.dimension.dbValue),
          baseUnit: Value(draft.dimension.baseUnitName),
          purchaseUnit: Value(purchaseName),
          minStock: Value(minStock),
        ),
      );
      await _replaceUnits(id, draft.customUnits);
      return id;
    });
  }

  Future<int> _insert(
    IngredientDraft draft,
    String name,
    String? purchaseName,
    int minStock,
  ) async {
    final quantity = draft.openingQuantity < 0 ? 0 : draft.openingQuantity;
    final value = quantity == 0 || draft.openingValue < 0 ? 0 : draft.openingValue;

    final id = await _db.into(_db.ingredients).insert(
          IngredientsCompanion(
            name: Value(name),
            dimension: Value(draft.dimension.dbValue),
            baseUnit: Value(draft.dimension.baseUnitName),
            purchaseUnit: Value(purchaseName),
            baseStock: Value(quantity),
            stockValue: Value(value),
            minStock: Value(minStock),
          ),
        );
    await _replaceUnits(id, draft.customUnits);
    if (quantity > 0) {
      // Stok awal dicatat sebagai penyesuaian, bukan restok: tidak menjadi pengeluaran.
      await _db.into(_db.stockMovements).insert(
            StockMovementsCompanion(
              ingredientId: Value(id),
              time: Value(toDbTime(_clock())),
              type: Value(MovementType.adjustment.dbValue),
              baseQuantity: Value(quantity),
              value: Value(value),
              reason: const Value('Stok awal'),
            ),
          );
    }
    return id;
  }

  Future<void> _replaceUnits(int ingredientId, List<CustomUnit> units) async {
    await (_db.delete(_db.ingredientUnits)
          ..where((t) => t.ingredientId.equals(ingredientId)))
        .go();
    for (final unit in units) {
      await _db.into(_db.ingredientUnits).insert(
            IngredientUnitsCompanion(
              ingredientId: Value(ingredientId),
              unitName: Value(unit.name.trim()),
              factorNumerator: Value(unit.factor.numerator),
              factorDenominator: Value(unit.factor.denominator),
            ),
          );
    }
  }

  /// Nama menu (yang belum dihapus) yang resepnya memakai bahan ini.
  Future<List<String>> menusUsing(int ingredientId) async {
    final rows = await _db
        .customSelect(
          'SELECT DISTINCT m.nama AS nama FROM resep_item r '
          'JOIN menu m ON m.id = r.menu_id '
          'WHERE r.bahan_id = ? AND m.dihapus_pada IS NULL ORDER BY m.nama',
          variables: <Variable<Object>>[Variable.withInt(ingredientId)],
          readsFrom: <ResultSetImplementation<dynamic, dynamic>>{
            _db.recipeItems,
            _db.menus,
          },
        )
        .get();
    return <String>[for (final r in rows) r.read<String>('nama')];
  }

  Future<bool> _hasHistory(int ingredientId) async {
    final row = await _db
        .customSelect(
          'SELECT '
          '(SELECT COUNT(*) FROM pergerakan_stok WHERE bahan_id = ?1) + '
          '(SELECT COUNT(*) FROM resep_item WHERE bahan_id = ?1) + '
          '(SELECT COUNT(*) FROM transaksi_bahan WHERE bahan_id = ?1) AS c',
          variables: <Variable<Object>>[Variable.withInt(ingredientId)],
          readsFrom: <ResultSetImplementation<dynamic, dynamic>>{
            _db.stockMovements,
            _db.recipeItems,
            _db.saleIngredientUsages,
          },
        )
        .getSingle();
    return row.read<int>('c') > 0;
  }

  /// Menghapus bahan (PRD 9.1).
  ///
  /// * Dipakai resep menu aktif: ditolak dengan [IngredientInUse] beserta daftar menunya (TC J1).
  /// * Sudah punya riwayat atau pernah dirujuk resep: hanya diarsipkan.
  /// * Selain itu dihapus permanen.
  Future<void> delete(int id) async {
    await _db.transaction(() async {
      final menus = await menusUsing(id);
      if (menus.isNotEmpty) throw IngredientInUse(menus);

      if (await _hasHistory(id)) {
        await (_db.update(_db.ingredients)..where((t) => t.id.equals(id))).write(
          IngredientsCompanion(deletedAt: Value(toDbTime(_clock()))),
        );
      } else {
        await (_db.delete(_db.ingredients)..where((t) => t.id.equals(id))).go();
      }
    });
  }

  Future<void> unarchive(int id) async {
    await (_db.update(_db.ingredients)..where((t) => t.id.equals(id)))
        .write(const IngredientsCompanion(deletedAt: Value(null)));
  }

  /// Riwayat pergerakan stok sebuah bahan, terbaru di atas, dengan penyaring tanggal.
  Stream<List<StockMovementView>> watchMovements(
    int ingredientId, {
    DateRange? range,
    int limit = 300,
  }) {
    final query = _db.select(_db.stockMovements)
      ..where((t) => t.ingredientId.equals(ingredientId))
      ..orderBy(<OrderingTerm Function($StockMovementsTable)>[
        (t) => OrderingTerm.desc(t.time),
        (t) => OrderingTerm.desc(t.id),
      ])
      ..limit(limit);
    if (range != null) {
      query.where(
        (t) =>
            t.time.isBiggerOrEqualValue(toDbTime(range.from)) &
            t.time.isSmallerThanValue(toDbTime(range.to)),
      );
    }
    return query.watch().map(
          (rows) => <StockMovementView>[
            for (final r in rows)
              StockMovementView(
                id: r.id,
                ingredientId: r.ingredientId,
                time: fromDbTime(r.time),
                type: MovementType.fromDb(r.type),
                quantityDelta: r.baseQuantity,
                valueDelta: r.value,
                referenceId: r.referenceId,
                reason: r.reason,
              ),
          ],
        );
  }
}
