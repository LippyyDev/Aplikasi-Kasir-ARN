import 'package:drift/drift.dart';

import 'package:arn_kasir/data/database/app_database.dart';
import 'package:arn_kasir/data/database/loaders.dart';
import 'package:arn_kasir/data/database/mappers.dart';
import 'package:arn_kasir/domain/inventory/ingredient.dart';
import 'package:arn_kasir/domain/inventory/stock_change.dart';
import 'package:arn_kasir/domain/menu/stock_catalog.dart';
import 'package:arn_kasir/domain/sales/payment.dart';
import 'package:arn_kasir/domain/sales/sale_planner.dart';
import 'package:arn_kasir/domain/sales/sale_record.dart';
import 'package:arn_kasir/domain/sales/sale_store.dart';

/// Pengait yang dipanggil sebelum setiap pembaruan stok. Dipakai uji untuk menyuntikkan
/// galat di tengah transaksi (TC E8); di produksi tidak diisi.
typedef StockUpdateHook = void Function();

/// Implementasi [SaleStore] di atas Drift. Seluruh operasi penjualan berjalan di dalam
/// satu `database.transaction` yang dibuka lewat [transaction] (CLAUDE.md aturan 4).
final class DriftSaleStore implements SaleStore {
  DriftSaleStore(this._db, {StockUpdateHook? onStockUpdate})
      : _onStockUpdate = onStockUpdate;

  final AppDatabase _db;
  final StockUpdateHook? _onStockUpdate;

  @override
  Future<T> transaction<T>(Future<T> Function() action) =>
      _db.transaction(action);

  @override
  Future<StockCatalog> loadCatalog(Iterable<int> menuIds) async {
    final menus = await _db.loadMenusByIds(menuIds);
    final ingredientIds = <int>{
      for (final menu in menus.values)
        for (final line in menu.recipe) line.ingredientId,
    };
    final ingredients = await _db.loadIngredientsByIds(ingredientIds);
    return StockCatalog(menus: menus, ingredients: ingredients);
  }

  @override
  Future<String> allocateReceiptNumber(DateTime localTime) async {
    final prefix = 'ARN-${localTime.year.toString().padLeft(4, '0')}'
        '${localTime.month.toString().padLeft(2, '0')}'
        '${localTime.day.toString().padLeft(2, '0')}';
    // Transaksi tidak pernah dihapus (hanya batal), jadi hitungan ini tidak mundur.
    final row = await _db
        .customSelect(
          'SELECT COUNT(*) AS c FROM transaksi WHERE nomor LIKE ?',
          variables: <Variable<Object>>[Variable.withString('$prefix-%')],
          readsFrom: <ResultSetImplementation<dynamic, dynamic>>{
            _db.saleTransactions,
          },
        )
        .getSingle();
    final next = row.read<int>('c') + 1;
    return '$prefix-${next.toString().padLeft(4, '0')}';
  }

  @override
  Future<int> insertSale(SalePlan plan) async {
    final sale = plan.sale;
    final saleId = await _db.into(_db.saleTransactions).insert(
          SaleTransactionsCompanion.insert(
            number: sale.number,
            time: toDbTime(sale.time),
            subtotal: sale.subtotal,
            discount: sale.discount,
            total: sale.total,
            paymentMethod: sale.method.dbValue,
            paid: sale.paid,
            change: sale.change,
            status: Value(sale.status.dbValue),
            note: Value(sale.note),
          ),
        );

    for (final item in sale.items) {
      final itemId = await _db.into(_db.saleItems).insert(
            SaleItemsCompanion.insert(
              saleId: saleId,
              menuId: item.menuId,
              menuName: item.menuName,
              unitPrice: item.unitPrice,
              quantity: item.quantity,
              unitCost: item.unitCost,
              note: Value(item.note),
            ),
          );
      for (final usage in item.usages) {
        await _db.into(_db.saleIngredientUsages).insert(
              SaleIngredientUsagesCompanion.insert(
                saleItemId: itemId,
                ingredientId: usage.ingredientId,
                baseQuantity: usage.baseQuantity,
                cost: usage.cost,
              ),
            );
      }
    }

    for (final change in plan.stockChanges) {
      await _applyChange(
        change,
        type: MovementType.sale,
        time: sale.time,
        referenceId: saleId,
      );
    }
    return saleId;
  }

  @override
  Future<SaleRecord?> findSale(int saleId) async {
    final row = await (_db.select(_db.saleTransactions)
          ..where((t) => t.id.equals(saleId)))
        .getSingleOrNull();
    if (row == null) return null;

    final itemRows = await (_db.select(_db.saleItems)
          ..where((t) => t.saleId.equals(saleId))
          ..orderBy(<OrderingTerm Function($SaleItemsTable)>[
            (t) => OrderingTerm.asc(t.id),
          ]))
        .get();
    final usageRows = await (_db.select(_db.saleIngredientUsages)
          ..where((t) => t.saleItemId.isIn(itemRows.map((i) => i.id)))
          ..orderBy(<OrderingTerm Function($SaleIngredientUsagesTable)>[
            (t) => OrderingTerm.asc(t.id),
          ]))
        .get();
    final usagesByItem = <int, List<UsageRecord>>{};
    for (final usage in usageRows) {
      usagesByItem.putIfAbsent(usage.saleItemId, () => <UsageRecord>[]).add(
            UsageRecord(
              ingredientId: usage.ingredientId,
              baseQuantity: usage.baseQuantity,
              cost: usage.cost,
            ),
          );
    }

    return SaleRecord(
      id: row.id,
      number: row.number,
      time: fromDbTime(row.time),
      subtotal: row.subtotal,
      discount: row.discount,
      total: row.total,
      method: PaymentMethod.fromDb(row.paymentMethod),
      paid: row.paid,
      change: row.change,
      status: SaleStatus.fromDb(row.status),
      note: row.note,
      items: <SaleItemRecord>[
        for (final item in itemRows)
          SaleItemRecord(
            menuId: item.menuId,
            menuName: item.menuName,
            unitPrice: item.unitPrice,
            quantity: item.quantity,
            unitCost: item.unitCost,
            note: item.note,
            usages: List<UsageRecord>.unmodifiable(
              usagesByItem[item.id] ?? const <UsageRecord>[],
            ),
          ),
      ],
    );
  }

  @override
  Future<Map<int, Ingredient>> loadIngredients(Iterable<int> ids) =>
      _db.loadIngredientsByIds(ids);

  @override
  Future<void> applyVoid(VoidPlan plan, DateTime time) async {
    final updated = await (_db.update(_db.saleTransactions)
          ..where((t) => t.id.equals(plan.saleId)))
        .write(
      SaleTransactionsCompanion(status: Value(SaleStatus.voided.dbValue)),
    );
    if (updated != 1) {
      throw StateError('Transaksi ${plan.saleId} tidak ditemukan saat pembatalan');
    }
    for (final change in plan.stockChanges) {
      await _applyChange(
        change,
        type: MovementType.voidSale,
        time: time,
        referenceId: plan.saleId,
      );
    }
  }

  Future<void> _applyChange(
    StockChange change, {
    required MovementType type,
    required DateTime time,
    required int referenceId,
  }) async {
    _onStockUpdate?.call();
    await _db.writeIngredientStock(change.ingredientId, change.after);
    await _db.into(_db.stockMovements).insert(
          StockMovementsCompanion.insert(
            ingredientId: change.ingredientId,
            time: toDbTime(time),
            type: type.dbValue,
            baseQuantity: change.quantityDelta,
            value: change.valueDelta,
            referenceId: Value(referenceId),
          ),
        );
  }
}
