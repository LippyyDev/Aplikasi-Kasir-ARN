import 'package:arn_kasir/domain/core/failures.dart';
import 'package:arn_kasir/domain/core/fraction.dart';
import 'package:arn_kasir/domain/expenses/expense.dart';
import 'package:arn_kasir/domain/inventory/inventory_store.dart';
import 'package:arn_kasir/domain/inventory/stock_change.dart';
import 'package:arn_kasir/domain/inventory/stock_state.dart';
import 'package:arn_kasir/domain/menu/availability.dart';
import 'package:arn_kasir/domain/sales/cart.dart';
import 'package:arn_kasir/domain/sales/payment.dart';
import 'package:arn_kasir/domain/sales/sale_record.dart';
import 'package:arn_kasir/domain/sales/sale_store.dart';
import 'package:flutter_test/flutter_test.dart';

import 'fixtures.dart';
import 'scenario_harness.dart';

/// Skenario TEST_CASES_TAKARAN bagian D (sisi penyimpanan), E, F, G, dan H. Dijalankan
/// terhadap implementasi dalam memori (domain) dan Drift (data) dengan harness berbeda.
void defineSalesScenarios(
  String label,
  Future<ScenarioHarness> Function() create,
) {
  group('Skenario penjualan dan stok ($label)', () {
    late ScenarioHarness h;
    late CheckoutUseCase checkout;
    late VoidSaleUseCase voidSale;
    late RestockUseCase restock;
    late AdjustStockUseCase adjust;
    final now = DateTime.utc(2026, 10, 19, 3);

    setUp(() async {
      h = await create();
      await h.seedFixtureA();
      checkout = CheckoutUseCase(h.saleStore, clock: () => now);
      voidSale = VoidSaleUseCase(h.saleStore, clock: () => now);
      restock = RestockUseCase(h.inventoryStore, clock: () => now);
      adjust = AdjustStockUseCase(h.inventoryStore, clock: () => now);
    });

    tearDown(() async => h.dispose());

    Future<CartLine> kopiSusuLine(int quantity) async => CartLine(
          menuId: await h.menuId(FixtureA.kopiSusu),
          menuName: FixtureA.kopiSusu,
          unitPrice: 18000,
          quantity: quantity,
        );

    /// Transaksi acuan: 3 Kopi Susu, diskon 10%, tunai Rp 50.000.
    Future<CheckoutResult> sellThree() async => checkout(
          CheckoutRequest(
            lines: <CartLine>[await kopiSusuLine(3)],
            discount: const PercentDiscount(10),
            method: PaymentMethod.cash,
            paid: 50000,
            policy: StockPolicy.block,
          ),
        );

    Future<void> expectFixtureUntouched() async {
      expect(await h.stockOf(FixtureA.kopi), const StockState(1000, 200000));
      expect(await h.stockOf(FixtureA.susu), const StockState(2000, 30000));
      expect(await h.stockOf(FixtureA.gula), const StockState(500, 8000));
      expect(await h.saleCount(), 0);
    }

    group('E. Penjualan', () {
      test('E1 dan E2: stok dan nilai stok berkurang sesuai takaran', () async {
        await sellThree();
        expect(await h.stockOf(FixtureA.kopi), const StockState(955, 191000));
        expect(await h.stockOf(FixtureA.susu), const StockState(1640, 24600));
        expect(await h.stockOf(FixtureA.gula), const StockState(470, 7520));
      });

      test('E3 sampai E6: HPP, subtotal, diskon, total, kembalian, laba kotor',
          () async {
        final result = await sellThree();
        final sale = await h.loadSale(result.sale.id!);
        expect(sale.totalCost, 14880);
        expect(sale.items.single.unitCost, 4960);
        expect(sale.subtotal, 54000);
        expect(sale.discount, 5400);
        expect(sale.total, 48600);
        expect(sale.paid, 50000);
        expect(sale.change, 1400);
        expect(sale.grossProfit, 33720);
        expect(sale.status, SaleStatus.completed);
      });

      test('E3: snapshot pemakaian bahan tersimpan per baris', () async {
        final result = await sellThree();
        final sale = await h.loadSale(result.sale.id!);
        final usages = sale.items.single.usages;
        expect(usages.map((u) => u.baseQuantity).toList(), <int>[45, 360, 30]);
        expect(usages.map((u) => u.cost).toList(), <int>[9000, 5400, 480]);
      });

      test('E7: tiga pergerakan penjualan negatif merujuk ke transaksi', () async {
        final result = await sellThree();
        final sales = (await h.movements())
            .where((m) => m.type == MovementType.sale)
            .toList();
        expect(sales, hasLength(3));
        expect(sales.every((m) => m.quantityDelta < 0), isTrue);
        expect(sales.every((m) => m.referenceId == result.sale.id), isTrue);
      });

      test('E8: galat pada bahan ke-3 membatalkan seluruh perubahan', () async {
        h.failOnStockUpdate(3);
        await expectLater(sellThree(), throwsA(isA<Object>()));
        await expectFixtureUntouched();
        expect(await h.movements(), isEmpty);
      });

      test('E9: keranjang kosong ditolak', () async {
        await expectLater(
          checkout(
            const CheckoutRequest(
              lines: <CartLine>[],
              method: PaymentMethod.cash,
              policy: StockPolicy.block,
            ),
          ),
          throwsA(isA<EmptyCart>()),
        );
        await expectFixtureUntouched();
      });

      test('E10: bayar tunai kurang dari total ditolak', () async {
        await expectLater(
          checkout(
            CheckoutRequest(
              lines: <CartLine>[await kopiSusuLine(3)],
              discount: const PercentDiscount(10),
              method: PaymentMethod.cash,
              paid: 40000,
              policy: StockPolicy.block,
            ),
          ),
          throwsA(isA<InsufficientPayment>()),
        );
        await expectFixtureUntouched();
      });

      test('nomor transaksi harian berurutan menurut urutan penyimpanan', () async {
        final first = await sellThree();
        await h.overrideStock(FixtureA.kopi, const StockState(1000, 200000));
        await h.overrideStock(FixtureA.susu, const StockState(2000, 30000));
        await h.overrideStock(FixtureA.gula, const StockState(500, 8000));
        final second = await sellThree();
        expect(first.sale.number, endsWith('-0001'));
        expect(second.sale.number, endsWith('-0002'));
        expect(first.sale.number.substring(0, first.sale.number.length - 4),
            second.sale.number.substring(0, second.sale.number.length - 4));
      });
    });

    group('D. Kebijakan stok pada penyimpanan', () {
      test('D2: blokir menolak penjualan dan tidak mengubah apa pun', () async {
        await h.overrideStock(FixtureA.susu, const StockState(100, 1500));
        await expectLater(
          checkout(
            CheckoutRequest(
              lines: <CartLine>[await kopiSusuLine(1)],
              method: PaymentMethod.cash,
              policy: StockPolicy.block,
            ),
          ),
          throwsA(isA<InsufficientStock>()),
        );
        expect(await h.stockOf(FixtureA.susu), const StockState(100, 1500));
        expect(await h.saleCount(), 0);
      });

      test('D3: peringatkan tetap menjual dan stok susu menjadi -20 ml', () async {
        await h.overrideStock(FixtureA.susu, const StockState(100, 1500));
        final result = await checkout(
          CheckoutRequest(
            lines: <CartLine>[await kopiSusuLine(1)],
            method: PaymentMethod.cash,
            policy: StockPolicy.warn,
          ),
        );
        final susuStock = await h.stockOf(FixtureA.susu);
        expect(susuStock.quantity, -20);
        expect(susuStock, const StockState(-20, -300));
        expect(
          result.overdrawnIngredientIds,
          <int>[await h.ingredientId(FixtureA.susu)],
        );
      });
    });

    group('F. Pembatalan (void)', () {
      test('F1 dan F2: stok dan nilai kembali dari snapshot, bukan resep baru',
          () async {
        final result = await sellThree();
        await h.changeRecipe(FixtureA.kopiSusu, <String, int>{
          FixtureA.kopi: 20,
          FixtureA.susu: 120,
          FixtureA.gula: 10,
        });
        await voidSale(result.sale.id!);
        expect(await h.stockOf(FixtureA.kopi), const StockState(1000, 200000));
        expect(await h.stockOf(FixtureA.susu), const StockState(2000, 30000));
        expect(await h.stockOf(FixtureA.gula), const StockState(500, 8000));
      });

      test('F3: transaksi berstatus batal dan tidak dihapus; ada pergerakan batal',
          () async {
        final result = await sellThree();
        await voidSale(result.sale.id!);
        final sale = await h.loadSale(result.sale.id!);
        expect(sale.status, SaleStatus.voided);
        expect(sale.items, hasLength(1));
        final voids = (await h.movements())
            .where((m) => m.type == MovementType.voidSale)
            .toList();
        expect(voids, hasLength(3));
        expect(voids.every((m) => m.quantityDelta > 0), isTrue);
        expect(await h.saleCount(), 1);
      });

      test('F5: void dua kali ditolak dan stok tidak bertambah lagi', () async {
        final result = await sellThree();
        await voidSale(result.sale.id!);
        await expectLater(
          voidSale(result.sale.id!),
          throwsA(isA<SaleAlreadyVoided>()),
        );
        expect(await h.stockOf(FixtureA.kopi), const StockState(1000, 200000));
      });

      test('void transaksi yang tidak ada ditolak', () async {
        await expectLater(voidSale(999), throwsA(isA<SaleNotFound>()));
      });

      test('void tetap tepat walau stok bergerak di antaranya', () async {
        final result = await sellThree();
        await restock(
          ingredientId: await h.ingredientId(FixtureA.gula),
          unitName: 'g',
          amount: Fraction(500),
          totalPrice: 9000,
        );
        await voidSale(result.sale.id!);
        // Gula: (470, 7520) + restok (500, 9000) = (970, 16520); void +30 g dan +480.
        expect(await h.stockOf(FixtureA.gula), const StockState(1000, 17000));
      });
    });

    group('G. Restok dan rata-rata tertimbang', () {
      test('G1: dua restok menghasilkan rata-rata tertimbang', () async {
        await h.overrideStock(FixtureA.gula, StockState.empty);
        final gulaId = await h.ingredientId(FixtureA.gula);
        await restock(
          ingredientId: gulaId,
          unitName: 'g',
          amount: Fraction(1000),
          totalPrice: 15000,
        );
        await restock(
          ingredientId: gulaId,
          unitName: 'g',
          amount: Fraction(500),
          totalPrice: 9000,
        );
        expect(await h.stockOf(FixtureA.gula), const StockState(1500, 24000));
      });

      test('G7: beli 2 kg gula Rp 30.000 menambah stok dan tercatat sebagai pengeluaran',
          () async {
        final result = await restock(
          ingredientId: await h.ingredientId(FixtureA.gula),
          unitName: 'kg',
          amount: Fraction(2),
          totalPrice: 30000,
        );
        expect(result.baseQuantity, 2000);
        expect(await h.stockOf(FixtureA.gula), const StockState(2500, 38000));

        final expenses = await h.expenses();
        expect(expenses, hasLength(1));
        expect(expenses.single.amount, 30000);
        expect(expenses.single.categoryId, SystemExpenseCategory.ingredients);
        expect(expenses.single.source, ExpenseSource.restock);
        expect(expenses.single.referenceId, result.movementId);

        final movements = (await h.movements())
            .where((m) => m.type == MovementType.restock)
            .toList();
        expect(movements, hasLength(1));
        expect(movements.single.quantityDelta, 2000);
        expect(movements.single.valueDelta, 30000);
      });

      test('restok dengan satuan lintas dimensi atau jumlah nol ditolak', () async {
        final gulaId = await h.ingredientId(FixtureA.gula);
        await expectLater(
          restock(
            ingredientId: gulaId,
            unitName: 'liter',
            amount: Fraction(1),
            totalPrice: 1000,
          ),
          throwsA(isA<DimensionMismatch>()),
        );
        await expectLater(
          restock(
            ingredientId: gulaId,
            unitName: 'g',
            amount: Fraction.zero,
            totalPrice: 1000,
          ),
          throwsA(isA<NonPositiveQuantity>()),
        );
        await expectLater(
          restock(
            ingredientId: gulaId,
            unitName: 'g',
            amount: Fraction(1, 25),
            totalPrice: 1000,
          ),
          throwsA(isA<QuantityRoundsToZero>()),
        );
        expect(await h.stockOf(FixtureA.gula), const StockState(500, 8000));
        expect(await h.expenses(), isEmpty);
      });
    });

    group('H. Penyesuaian stok (opname)', () {
      setUp(() async {
        // Keadaan gula setelah transaksi E: 470 g, nilai 7.520, rata-rata Rp 16 per g.
        await h.overrideStock(FixtureA.gula, const StockState(470, 7520));
      });

      Future<AdjustStockResult> opname(int grams, {required bool susut}) async =>
          adjust(
            ingredientId: await h.ingredientId(FixtureA.gula),
            unitName: 'g',
            physicalAmount: Fraction(grams),
            shrinkageEnabled: susut,
            reason: 'tumpah',
          );

      test('H1 dan H2: selisih -20 g bernilai Rp 320 dicatat sebagai susut', () async {
        final result = await opname(450, susut: true);
        expect(result.quantityDelta, -20);
        expect(result.shrinkageAmount, 320);
        expect(await h.stockOf(FixtureA.gula), const StockState(450, 7200));

        final movements = (await h.movements())
            .where((m) => m.type == MovementType.adjustment)
            .toList();
        expect(movements, hasLength(1));
        expect(movements.single.quantityDelta, -20);
        expect(movements.single.reason, 'tumpah');

        final expenses = await h.expenses();
        expect(expenses, hasLength(1));
        expect(expenses.single.amount, 320);
        expect(expenses.single.source, ExpenseSource.shrinkage);
        expect(expenses.single.categoryId, SystemExpenseCategory.shrinkage);
        expect(expenses.single.referenceId, result.movementId);
      });

      test('H3: fitur susut dimatikan tetap menyesuaikan stok tanpa pengeluaran',
          () async {
        await opname(450, susut: false);
        expect(await h.stockOf(FixtureA.gula), const StockState(450, 7200));
        expect(await h.expenses(), isEmpty);
      });

      test('H4: stok fisik lebih besar menambah stok tanpa pengeluaran', () async {
        final result = await opname(500, susut: true);
        expect(result.quantityDelta, 30);
        expect(await h.stockOf(FixtureA.gula), const StockState(500, 8000));
        expect(await h.expenses(), isEmpty);
      });

      test('tanpa selisih tidak ada pergerakan', () async {
        final result = await opname(470, susut: true);
        expect(result.movementId, isNull);
        expect(await h.movements(), isEmpty);
      });
    });
  });
}
