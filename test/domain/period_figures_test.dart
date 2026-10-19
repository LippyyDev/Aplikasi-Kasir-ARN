import 'package:arn_kasir/domain/reports/period_figures.dart';
import 'package:flutter_test/flutter_test.dart';

void main() {
  group('I. Laporan periode tanpa perhitungan ganda', () {
    const totals = PeriodTotals(
      netSales: 48600,
      costOfGoodsSold: 14880,
      ingredientPurchases: 30000,
      manualExpenses: 5000,
      shrinkage: 0,
    );
    final figures = PeriodFigures.from(totals);

    test('I1: laba kotor = 48.600 - 14.880 = 33.720', () {
      expect(figures.grossProfit, 33720);
    });

    test('I2: laba bersih operasional = 33.720 - 5.000; pembelian bahan tidak dikurangi lagi', () {
      expect(figures.operatingProfit, 28720);
    });

    test('I3: arus kas = 48.600 - 30.000 - 5.000 = 13.600', () {
      expect(figures.cashFlow, 13600);
    });

    test('susut mengurangi laba bersih tetapi bukan kas keluar', () {
      final withShrinkage = PeriodFigures.from(
        const PeriodTotals(
          netSales: 48600,
          costOfGoodsSold: 14880,
          ingredientPurchases: 30000,
          manualExpenses: 5000,
          shrinkage: 320,
        ),
      );
      expect(withShrinkage.operatingProfit, 28400);
      expect(withShrinkage.cashFlow, 13600);
    });
  });
}
