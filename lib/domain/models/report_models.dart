import 'package:arn_kasir/domain/models/sale_summary.dart';
import 'package:arn_kasir/domain/reports/period_figures.dart';

/// Rentang waktu setengah terbuka `[from, to)` dalam UTC.
final class DateRange {
  const DateRange({required this.from, required this.to});

  final DateTime from;
  final DateTime to;
}

/// Kartu ringkasan Info Pemasukan (PRD 6.3.6).
final class SalesSummary {
  const SalesSummary({
    required this.transactionCount,
    required this.grossSales,
    required this.discounts,
    required this.netSales,
    required this.costOfGoodsSold,
  });

  static const SalesSummary empty = SalesSummary(
    transactionCount: 0,
    grossSales: 0,
    discounts: 0,
    netSales: 0,
    costOfGoodsSold: 0,
  );

  final int transactionCount;
  final int grossSales;
  final int discounts;
  final int netSales;
  final int costOfGoodsSold;

  int get grossProfit => netSales - costOfGoodsSold;

  /// Rata-rata penjualan bersih per transaksi, dibulatkan HALF_UP.
  int get averagePerTransaction =>
      transactionCount == 0 ? 0 : (2 * netSales + transactionCount) ~/ (2 * transactionCount);
}

/// Satu titik grafik tren: kunci waktu (tanggal `yyyy-MM-dd` atau jam `HH`) dan nilai.
final class TrendPoint {
  const TrendPoint({required this.key, required this.netSales, required this.count});

  final String key;
  final int netSales;
  final int count;
}

final class TopMenuEntry {
  const TopMenuEntry({
    required this.menuName,
    required this.quantity,
    required this.revenue,
  });

  final String menuName;
  final int quantity;

  /// Pendapatan baris sebelum diskon transaksi.
  final int revenue;
}

/// Satu irisan pembagian (kategori menu, metode bayar, atau kategori pengeluaran).
final class BreakdownEntry {
  const BreakdownEntry({required this.label, required this.amount, this.key});

  /// Label dari data (`null` berarti "Tanpa kategori"); UI menerjemahkan kunci khusus.
  final String? label;
  final int amount;

  /// Kunci mesin, mis. `tunai` atau `non_tunai` untuk metode bayar.
  final String? key;
}

/// Gabungan angka periode untuk Info Pemasukan dan Info Pengeluaran.
final class PeriodReport {
  const PeriodReport({
    required this.summary,
    required this.totals,
    required this.figures,
  });

  final SalesSummary summary;
  final PeriodTotals totals;
  final PeriodFigures figures;
}

/// Semua angka layar Info Pemasukan untuk satu periode (PRD 6.3.6).
final class IncomeOverview {
  const IncomeOverview({
    required this.report,
    required this.trend,
    required this.topMenus,
    required this.byCategory,
    required this.byMethod,
    required this.sales,
    required this.hourly,
  });

  final PeriodReport report;

  /// Penjualan bersih per hari, atau per jam bila [hourly].
  final List<TrendPoint> trend;
  final bool hourly;
  final List<TopMenuEntry> topMenus;
  final List<BreakdownEntry> byCategory;
  final List<BreakdownEntry> byMethod;
  final List<SaleSummary> sales;
}

/// Semua angka layar Info Pengeluaran untuk satu periode (PRD 6.3.7).
final class ExpenseOverview {
  const ExpenseOverview({required this.report, required this.byCategory});

  final PeriodReport report;
  final List<BreakdownEntry> byCategory;
}
