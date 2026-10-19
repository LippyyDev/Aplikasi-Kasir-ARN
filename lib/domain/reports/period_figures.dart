/// Angka dasar sebuah periode, dijumlahkan dari data yang tidak dibatalkan.
final class PeriodTotals {
  const PeriodTotals({
    required this.netSales,
    required this.costOfGoodsSold,
    required this.ingredientPurchases,
    required this.manualExpenses,
    required this.shrinkage,
  });

  /// Penjualan bersih (setelah diskon) transaksi berstatus selesai.
  final int netSales;

  /// HPP: jumlah biaya bahan pada snapshot pemakaian transaksi selesai.
  final int costOfGoodsSold;

  /// Pembelian bahan baku (restok); pengeluaran kas.
  final int ingredientPurchases;

  /// Pengeluaran operasional manual (sewa, listrik, gaji, dan sebagainya).
  final int manualExpenses;

  /// Susut hasil opname; kerugian nilai persediaan, bukan kas keluar.
  final int shrinkage;
}

/// Tiga ukuran yang sengaja dipisah agar pembelian bahan tidak dihitung ganda (PRD 9.3).
final class PeriodFigures {
  const PeriodFigures({
    required this.grossProfit,
    required this.operatingProfit,
    required this.cashFlow,
  });

  factory PeriodFigures.from(PeriodTotals totals) {
    final grossProfit = totals.netSales - totals.costOfGoodsSold;
    return PeriodFigures(
      grossProfit: grossProfit,
      operatingProfit: grossProfit - totals.manualExpenses - totals.shrinkage,
      cashFlow:
          totals.netSales - totals.ingredientPurchases - totals.manualExpenses,
    );
  }

  /// Penjualan bersih dikurangi HPP (akrual sederhana).
  final int grossProfit;

  /// Laba kotor dikurangi pengeluaran non bahan (manual dan susut). Pembelian bahan
  /// tidak dikurangkan lagi karena pemakaiannya sudah ada di HPP.
  final int operatingProfit;

  /// Penjualan bersih dikurangi kas keluar: pembelian bahan dan pengeluaran manual.
  /// Susut tidak dihitung karena bukan pengeluaran kas.
  final int cashFlow;
}
