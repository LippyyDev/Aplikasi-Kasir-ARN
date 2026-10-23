import 'package:arn_kasir/data/repositories/expense_repository.dart';
import 'package:arn_kasir/data/repositories/report_repository.dart';
import 'package:arn_kasir/domain/core/fraction.dart';
import 'package:arn_kasir/domain/inventory/inventory_store.dart';
import 'package:arn_kasir/domain/menu/availability.dart';
import 'package:arn_kasir/domain/models/report_models.dart';
import 'package:arn_kasir/domain/sales/cart.dart';
import 'package:arn_kasir/domain/sales/payment.dart';
import 'package:arn_kasir/domain/sales/sale_store.dart';
import 'package:flutter_test/flutter_test.dart';

import '../support/drift_harness.dart';
import '../support/fixtures.dart';

/// TEST_CASES_TAKARAN bagian I (laporan tidak menghitung ganda) terhadap SQLite sungguhan,
/// ditambah ringkasan, menu terlaris, dan pembagian untuk layar Info Pemasukan.
void main() {
  group('Laporan periode', () {
    late DriftHarness h;
    late ReportRepository reports;
    late int saleId;

    final now = DateTime.utc(2026, 10, 19, 3);
    final today = DateRange(
      from: DateTime.utc(2026, 10, 19),
      to: DateTime.utc(2026, 10, 20),
    );
    final otherDay = DateRange(
      from: DateTime.utc(2026, 10, 25),
      to: DateTime.utc(2026, 10, 26),
    );

    setUp(() async {
      h = DriftHarness();
      await h.seedFixtureA();
      reports = ReportRepository(h.db);

      final checkout = CheckoutUseCase(h.saleStore, clock: () => now);
      final restock = RestockUseCase(h.inventoryStore, clock: () => now);
      final expenses = ExpenseRepository(h.db);

      // Data TC I: penjualan bersih E4 = 48.600, HPP E3 = 14.880, restok G7 = 30.000,
      // listrik = 5.000.
      final result = await checkout(
        CheckoutRequest(
          lines: <CartLine>[
            CartLine(
              menuId: await h.menuId(FixtureA.kopiSusu),
              menuName: FixtureA.kopiSusu,
              unitPrice: 18000,
              quantity: 3,
            ),
          ],
          discount: const PercentDiscount(10),
          method: PaymentMethod.cash,
          paid: 50000,
          policy: StockPolicy.block,
        ),
      );
      saleId = result.sale.id!;
      await restock(
        ingredientId: await h.ingredientId(FixtureA.gula),
        unitName: 'kg',
        amount: Fraction(2),
        totalPrice: 30000,
      );
      final electricity = await expenses.addCategory('Listrik');
      await expenses.addManual(date: now, categoryId: electricity, amount: 5000);
    });

    tearDown(() async => h.dispose());

    test('I1 sampai I3: laba kotor, laba operasional, dan arus kas dipisah', () async {
      final report = await reports.report(today);
      expect(report.summary.netSales, 48600);
      expect(report.summary.costOfGoodsSold, 14880);
      expect(report.totals.ingredientPurchases, 30000);
      expect(report.totals.manualExpenses, 5000);

      expect(report.figures.grossProfit, 33720); // I1
      expect(report.figures.operatingProfit, 28720); // I2
      expect(report.figures.cashFlow, 13600); // I3
    });

    test('ringkasan: jumlah transaksi, kotor, diskon, dan rata-rata', () async {
      final summary = (await reports.report(today)).summary;
      expect(summary.transactionCount, 1);
      expect(summary.grossSales, 54000);
      expect(summary.discounts, 5400);
      expect(summary.netSales, 48600);
      expect(summary.averagePerTransaction, 48600);
    });

    test('periode tanpa transaksi menghasilkan nol, bukan galat', () async {
      final report = await reports.report(otherDay);
      expect(report.summary.transactionCount, 0);
      expect(report.summary.netSales, 0);
      expect(report.summary.averagePerTransaction, 0);
      expect(report.figures.cashFlow, 0);
    });

    test('transaksi batal tidak masuk angka tetapi tetap tampil di daftar', () async {
      await VoidSaleUseCase(h.saleStore, clock: () => now)(saleId);

      final report = await reports.report(today);
      expect(report.summary.transactionCount, 0);
      expect(report.summary.netSales, 0);
      expect(report.summary.costOfGoodsSold, 0);
      // Pembelian bahan adalah kas keluar yang nyata dan tidak ikut dibatalkan.
      expect(report.totals.ingredientPurchases, 30000);

      final list = await reports.salesList(today);
      expect(list, hasLength(1));
      expect(list.single.id, saleId);
    });

    test('menu terlaris, pembagian kategori dan metode bayar', () async {
      final top = await reports.topMenus(today);
      expect(top.single.menuName, FixtureA.kopiSusu);
      expect(top.single.quantity, 3);
      expect(top.single.revenue, 54000);

      final byCategory = await reports.salesByCategory(today);
      expect(byCategory.single.label, 'Kopi');
      expect(byCategory.single.amount, 54000);

      final byMethod = await reports.salesByMethod(today);
      expect(byMethod.single.key, PaymentMethod.cash.dbValue);
      expect(byMethod.single.amount, 48600);
    });

    test('pengeluaran per kategori memisahkan restok dan manual', () async {
      final byCategory = await reports.expensesByCategory(today);
      final amounts = <String?, int>{for (final e in byCategory) e.label: e.amount};
      expect(amounts['Listrik'], 5000);
      expect(amounts.values.fold<int>(0, (a, b) => a + b), 35000);
    });

    test('tren sehari dikelompokkan per jam, beberapa hari per tanggal', () async {
      expect(ReportRepository.isSingleDay(today), isTrue);
      final hourly = await reports.trend(today, hourly: true);
      expect(hourly.fold<int>(0, (a, p) => a + p.netSales), 48600);
      expect(hourly.single.key.length, 13); // yyyy-MM-dd HH

      final week = DateRange(
        from: DateTime.utc(2026, 10, 13),
        to: DateTime.utc(2026, 10, 20),
      );
      expect(ReportRepository.isSingleDay(week), isFalse);
      final daily = await reports.trend(week);
      expect(daily.fold<int>(0, (a, p) => a + p.netSales), 48600);
      expect(daily.single.key.length, 10); // yyyy-MM-dd
    });
  });
}
