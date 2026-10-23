import 'package:drift/drift.dart';

import 'package:arn_kasir/data/database/app_database.dart';
import 'package:arn_kasir/data/database/mappers.dart';
import 'package:arn_kasir/domain/models/report_models.dart';
import 'package:arn_kasir/domain/models/sale_summary.dart';
import 'package:arn_kasir/domain/reports/period_figures.dart';
import 'package:arn_kasir/domain/sales/payment.dart';
import 'package:arn_kasir/domain/sales/sale_record.dart';

/// Agregasi laporan. Semua penjumlahan dikerjakan di SQL, bukan di memori (BACKLOG #20).
/// Hanya transaksi berstatus `selesai` yang dihitung; transaksi batal tetap tampil di
/// daftar tetapi tidak masuk angka.
final class ReportRepository {
  ReportRepository(this._db);

  final AppDatabase _db;

  Set<ResultSetImplementation<dynamic, dynamic>> get _salesTables =>
      <ResultSetImplementation<dynamic, dynamic>>{
        _db.saleTransactions,
        _db.saleItems,
        _db.saleIngredientUsages,
        _db.menus,
        _db.menuCategories,
      };

  List<Variable<Object>> _rangeVars(DateRange range) => <Variable<Object>>[
        Variable.withInt(toDbTime(range.from)),
        Variable.withInt(toDbTime(range.to)),
      ];

  /// Periode satu hari dikelompokkan per jam, selain itu per hari.
  static bool isSingleDay(DateRange range) =>
      range.to.difference(range.from) <= const Duration(hours: 25);

  Stream<IncomeOverview> watchIncome(DateRange range) {
    return _db.watchLoad<IncomeOverview>(
      <ResultSetImplementation<dynamic, dynamic>>[
        ..._salesTables,
        _db.expenses,
      ],
      () => loadIncome(range),
    );
  }

  Future<IncomeOverview> loadIncome(DateRange range) async {
    final hourly = isSingleDay(range);
    return IncomeOverview(
      report: await report(range),
      trend: await trend(range, hourly: hourly),
      hourly: hourly,
      topMenus: await topMenus(range),
      byCategory: await salesByCategory(range),
      byMethod: await salesByMethod(range),
      sales: await salesList(range),
    );
  }

  Stream<ExpenseOverview> watchExpenses(DateRange range) {
    return _db.watchLoad<ExpenseOverview>(
      <ResultSetImplementation<dynamic, dynamic>>[
        ..._salesTables,
        _db.expenses,
        _db.expenseCategories,
      ],
      () async => ExpenseOverview(
        report: await report(range),
        byCategory: await expensesByCategory(range),
      ),
    );
  }

  Stream<PeriodReport> watchReport(DateRange range) {
    return _db.watchLoad<PeriodReport>(
      <ResultSetImplementation<dynamic, dynamic>>[
        ..._salesTables,
        _db.expenses,
      ],
      () => report(range),
    );
  }

  Future<PeriodReport> report(DateRange range) async {
    final summary = await salesSummary(range);
    final totals = await periodTotals(range, summary: summary);
    return PeriodReport(
      summary: summary,
      totals: totals,
      figures: PeriodFigures.from(totals),
    );
  }

  Future<SalesSummary> salesSummary(DateRange range) async {
    final sales = await _db
        .customSelect(
          'SELECT COUNT(*) AS c, COALESCE(SUM(subtotal), 0) AS gross, '
          'COALESCE(SUM(diskon), 0) AS disc, COALESCE(SUM(total), 0) AS net '
          'FROM transaksi WHERE status = ? AND waktu >= ? AND waktu < ?',
          variables: <Variable<Object>>[
            Variable.withString(SaleStatus.completed.dbValue),
            ..._rangeVars(range),
          ],
          readsFrom: _salesTables,
        )
        .getSingle();
    final cogs = await _costOfGoodsSold(range);
    return SalesSummary(
      transactionCount: sales.read<int>('c'),
      grossSales: sales.read<int>('gross'),
      discounts: sales.read<int>('disc'),
      netSales: sales.read<int>('net'),
      costOfGoodsSold: cogs,
    );
  }

  Future<int> _costOfGoodsSold(DateRange range) async {
    final row = await _db
        .customSelect(
          'SELECT COALESCE(SUM(b.biaya), 0) AS cogs '
          'FROM transaksi_bahan b '
          'JOIN transaksi_item i ON i.id = b.transaksi_item_id '
          'JOIN transaksi t ON t.id = i.transaksi_id '
          'WHERE t.status = ? AND t.waktu >= ? AND t.waktu < ?',
          variables: <Variable<Object>>[
            Variable.withString(SaleStatus.completed.dbValue),
            ..._rangeVars(range),
          ],
          readsFrom: _salesTables,
        )
        .getSingle();
    return row.read<int>('cogs');
  }

  Future<PeriodTotals> periodTotals(DateRange range, {SalesSummary? summary}) async {
    final base = summary ?? await salesSummary(range);
    final rows = await _db
        .customSelect(
          'SELECT sumber, COALESCE(SUM(nominal), 0) AS total '
          'FROM pengeluaran WHERE tanggal >= ? AND tanggal < ? GROUP BY sumber',
          variables: _rangeVars(range),
          readsFrom: <ResultSetImplementation<dynamic, dynamic>>{_db.expenses},
        )
        .get();
    final bySource = <String, int>{
      for (final r in rows) r.read<String>('sumber'): r.read<int>('total'),
    };
    return PeriodTotals(
      netSales: base.netSales,
      costOfGoodsSold: base.costOfGoodsSold,
      ingredientPurchases: bySource['restok'] ?? 0,
      manualExpenses: bySource['manual'] ?? 0,
      shrinkage: bySource['susut'] ?? 0,
    );
  }

  /// Tren penjualan bersih. [hourly] mengelompokkan per jam (untuk periode sehari),
  /// selain itu per hari. Pengelompokan memakai zona waktu lokal pada awal rentang.
  Future<List<TrendPoint>> trend(DateRange range, {bool hourly = false}) async {
    final offsetSeconds = range.from.toLocal().timeZoneOffset.inSeconds;
    final format = hourly ? '%Y-%m-%d %H' : '%Y-%m-%d';
    final rows = await _db
        .customSelect(
          "SELECT strftime('$format', waktu / 1000 + ?, 'unixepoch') AS k, "
          'SUM(total) AS net, COUNT(*) AS c '
          'FROM transaksi WHERE status = ? AND waktu >= ? AND waktu < ? '
          'GROUP BY k ORDER BY k',
          variables: <Variable<Object>>[
            Variable.withInt(offsetSeconds),
            Variable.withString(SaleStatus.completed.dbValue),
            ..._rangeVars(range),
          ],
          readsFrom: _salesTables,
        )
        .get();
    return <TrendPoint>[
      for (final r in rows)
        TrendPoint(
          key: r.read<String>('k'),
          netSales: r.read<int>('net'),
          count: r.read<int>('c'),
        ),
    ];
  }

  Future<List<TopMenuEntry>> topMenus(DateRange range, {int limit = 5}) async {
    final rows = await _db
        .customSelect(
          'SELECT MAX(i.nama_menu) AS nama, SUM(i.kuantitas) AS qty, '
          'SUM(i.harga_satuan * i.kuantitas) AS rev '
          'FROM transaksi_item i JOIN transaksi t ON t.id = i.transaksi_id '
          'WHERE t.status = ? AND t.waktu >= ? AND t.waktu < ? '
          'GROUP BY i.menu_id ORDER BY qty DESC, rev DESC, nama ASC LIMIT ?',
          variables: <Variable<Object>>[
            Variable.withString(SaleStatus.completed.dbValue),
            ..._rangeVars(range),
            Variable.withInt(limit),
          ],
          readsFrom: _salesTables,
        )
        .get();
    return <TopMenuEntry>[
      for (final r in rows)
        TopMenuEntry(
          menuName: r.read<String>('nama'),
          quantity: r.read<int>('qty'),
          revenue: r.read<int>('rev'),
        ),
    ];
  }

  /// Penjualan kotor per kategori menu saat ini. Menu tanpa kategori memakai label `null`.
  Future<List<BreakdownEntry>> salesByCategory(DateRange range) async {
    final rows = await _db
        .customSelect(
          'SELECT k.id AS kid, k.nama AS label, '
          'SUM(i.harga_satuan * i.kuantitas) AS amount '
          'FROM transaksi_item i '
          'JOIN transaksi t ON t.id = i.transaksi_id '
          'JOIN menu m ON m.id = i.menu_id '
          'LEFT JOIN kategori_menu k ON k.id = m.kategori_id '
          'WHERE t.status = ? AND t.waktu >= ? AND t.waktu < ? '
          'GROUP BY k.id ORDER BY amount DESC',
          variables: <Variable<Object>>[
            Variable.withString(SaleStatus.completed.dbValue),
            ..._rangeVars(range),
          ],
          readsFrom: _salesTables,
        )
        .get();
    return <BreakdownEntry>[
      for (final r in rows)
        BreakdownEntry(
          label: r.readNullable<String>('label'),
          amount: r.read<int>('amount'),
          key: r.readNullable<int>('kid')?.toString(),
        ),
    ];
  }

  Future<List<BreakdownEntry>> salesByMethod(DateRange range) async {
    final rows = await _db
        .customSelect(
          'SELECT metode_bayar AS m, SUM(total) AS amount FROM transaksi '
          'WHERE status = ? AND waktu >= ? AND waktu < ? '
          'GROUP BY metode_bayar ORDER BY amount DESC',
          variables: <Variable<Object>>[
            Variable.withString(SaleStatus.completed.dbValue),
            ..._rangeVars(range),
          ],
          readsFrom: _salesTables,
        )
        .get();
    return <BreakdownEntry>[
      for (final r in rows)
        BreakdownEntry(
          label: null,
          amount: r.read<int>('amount'),
          key: PaymentMethod.fromDb(r.read<String>('m')).dbValue,
        ),
    ];
  }

  Future<List<BreakdownEntry>> expensesByCategory(DateRange range) async {
    final rows = await _db
        .customSelect(
          'SELECT k.id AS kid, k.nama AS label, SUM(p.nominal) AS amount '
          'FROM pengeluaran p JOIN kategori_pengeluaran k ON k.id = p.kategori_id '
          'WHERE p.tanggal >= ? AND p.tanggal < ? '
          'GROUP BY k.id ORDER BY amount DESC',
          variables: _rangeVars(range),
          readsFrom: <ResultSetImplementation<dynamic, dynamic>>{
            _db.expenses,
            _db.expenseCategories,
          },
        )
        .get();
    return <BreakdownEntry>[
      for (final r in rows)
        BreakdownEntry(
          label: r.read<String>('label'),
          amount: r.read<int>('amount'),
          key: r.read<int>('kid').toString(),
        ),
    ];
  }

  /// Daftar transaksi dalam rentang (termasuk yang batal), terbaru di atas.
  Stream<List<SaleSummary>> watchSales(DateRange range, {int limit = 200, int offset = 0}) {
    return _db.watchLoad<List<SaleSummary>>(
      <ResultSetImplementation<dynamic, dynamic>>[
        _db.saleTransactions,
        _db.saleItems,
      ],
      () => salesList(range, limit: limit, offset: offset),
    );
  }

  Future<List<SaleSummary>> salesList(
    DateRange range, {
    int limit = 200,
    int offset = 0,
  }) async {
    final rows = await _db
        .customSelect(
          'SELECT t.id AS id, t.nomor AS nomor, t.waktu AS waktu, t.total AS total, '
          't.metode_bayar AS metode, t.status AS status, '
          'COALESCE((SELECT SUM(kuantitas) FROM transaksi_item WHERE transaksi_id = t.id), 0) AS n '
          'FROM transaksi t WHERE t.waktu >= ? AND t.waktu < ? '
          'ORDER BY t.waktu DESC, t.id DESC LIMIT ? OFFSET ?',
          variables: <Variable<Object>>[
            ..._rangeVars(range),
            Variable.withInt(limit),
            Variable.withInt(offset),
          ],
          readsFrom: _salesTables,
        )
        .get();
    return <SaleSummary>[
      for (final r in rows)
        SaleSummary(
          id: r.read<int>('id'),
          number: r.read<String>('nomor'),
          time: fromDbTime(r.read<int>('waktu')),
          total: r.read<int>('total'),
          method: PaymentMethod.fromDb(r.read<String>('metode')),
          status: SaleStatus.fromDb(r.read<String>('status')),
          itemCount: r.read<int>('n'),
        ),
    ];
  }
}
