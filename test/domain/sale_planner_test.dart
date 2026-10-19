import 'package:arn_kasir/domain/core/failures.dart';
import 'package:arn_kasir/domain/inventory/ingredient.dart';
import 'package:arn_kasir/domain/inventory/stock_state.dart';
import 'package:arn_kasir/domain/menu/availability.dart';
import 'package:arn_kasir/domain/sales/cart.dart';
import 'package:arn_kasir/domain/sales/payment.dart';
import 'package:arn_kasir/domain/sales/sale_planner.dart';
import 'package:arn_kasir/domain/sales/sale_record.dart';
import 'package:flutter_test/flutter_test.dart';

import '../support/fixtures.dart';

CartLine kopiSusuLine(int quantity) => CartLine(
      menuId: FixtureA.kopiSusuId,
      menuName: FixtureA.kopiSusu,
      unitPrice: 18000,
      quantity: quantity,
    );

SalePlan plan({
  required int quantity,
  Map<int, StockState> stock = const <int, StockState>{},
  StockPolicy policy = StockPolicy.block,
  PaymentMethod method = PaymentMethod.cash,
  int? paid,
  Discount discount = const NoDiscount(),
}) =>
    SalePlanner.plan(
      catalog: FixtureA.catalog(stock: stock),
      lines: <CartLine>[kopiSusuLine(quantity)],
      discount: discount,
      method: method,
      paid: paid,
      policy: policy,
      time: DateTime.utc(2026, 10, 19, 3),
      number: 'ARN-20261019-0001',
    );

void main() {
  group('Perencana penjualan (murni)', () {
    test('E1, E2, E3: pengurangan stok, nilai stok, dan total HPP', () {
      final result = plan(
        quantity: 3,
        discount: const PercentDiscount(10),
        paid: 50000,
      );
      final changes = {for (final c in result.stockChanges) c.ingredientId: c};
      expect(changes[FixtureA.kopiId]!.after, const StockState(955, 191000));
      expect(changes[FixtureA.susuId]!.after, const StockState(1640, 24600));
      expect(changes[FixtureA.gulaId]!.after, const StockState(470, 7520));
      expect(result.sale.totalCost, 14880);
      expect(result.sale.items.single.unitCost, 4960);
      expect(result.sale.items.single.totalCost, 3 * 4960);
    });

    test('perubahan stok mengikuti urutan resep (bahan ke-3 adalah gula)', () {
      final result = plan(quantity: 1);
      expect(
        result.stockChanges.map((c) => c.ingredientId).toList(),
        <int>[FixtureA.kopiId, FixtureA.susuId, FixtureA.gulaId],
      );
    });

    test('E4 sampai E6: subtotal, diskon, total, kembalian, laba kotor', () {
      final result = plan(
        quantity: 3,
        discount: const PercentDiscount(10),
        paid: 50000,
      );
      expect(result.sale.subtotal, 54000);
      expect(result.sale.discount, 5400);
      expect(result.sale.total, 48600);
      expect(result.sale.paid, 50000);
      expect(result.sale.change, 1400);
      expect(result.sale.grossProfit, 33720);
      expect(result.sale.status, SaleStatus.completed);
      expect(result.sale.id, isNull);
    });

    test('non tunai dicatat saja: dibayar sama dengan total, kembalian 0', () {
      final result = plan(quantity: 1, method: PaymentMethod.nonCash);
      expect(result.sale.paid, 18000);
      expect(result.sale.change, 0);
    });

    test('E10: tunai kurang dari total ditolak', () {
      expect(
        () => plan(quantity: 3, paid: 40000),
        throwsA(isA<InsufficientPayment>()),
      );
    });

    test('E9: tanpa baris ditolak', () {
      expect(
        () => SalePlanner.plan(
          catalog: FixtureA.catalog(),
          lines: const <CartLine>[],
          discount: const NoDiscount(),
          method: PaymentMethod.cash,
          policy: StockPolicy.block,
          time: DateTime.utc(2026),
          number: 'x',
        ),
        throwsA(isA<EmptyCart>()),
      );
    });

    test('menu yang tidak ada atau tidak aktif ditolak', () {
      expect(
        () => SalePlanner.plan(
          catalog: FixtureA.catalog(),
          lines: const <CartLine>[
            CartLine(menuId: 99, menuName: 'Hantu', unitPrice: 1, quantity: 1),
          ],
          discount: const NoDiscount(),
          method: PaymentMethod.cash,
          policy: StockPolicy.block,
          time: DateTime.utc(2026),
          number: 'x',
        ),
        throwsA(isA<MenuUnavailable>()),
      );
    });

    test('D3: peringatkan menjual melebihi stok; susu -20 ml ditandai', () {
      final result = plan(
        quantity: 1,
        stock: const <int, StockState>{FixtureA.susuId: StockState(100, 1500)},
        policy: StockPolicy.warn,
      );
      final susu = result.stockChanges.firstWhere((c) => c.ingredientId == FixtureA.susuId);
      expect(susu.after.quantity, -20);
      expect(result.overdrawnIngredientIds, <int>[FixtureA.susuId]);
    });

    test('D2: blokir menolak penjualan bila stok kurang', () {
      expect(
        () => plan(
          quantity: 1,
          stock: const <int, StockState>{FixtureA.susuId: StockState(100, 1500)},
        ),
        throwsA(isA<InsufficientStock>()),
      );
    });

    test('pemakaian melebihi stok kecil dibiayai pada rata-rata terakhir', () {
      final result = SalePlanner.plan(
        catalog: FixtureA.catalog(
          stock: const <int, StockState>{FixtureA.susuId: StockState(3, 10)},
        ),
        lines: const <CartLine>[
          CartLine(menuId: FixtureA.latteId, menuName: 'Latte', unitPrice: 22000, quantity: 1),
        ],
        discount: const NoDiscount(),
        method: PaymentMethod.cash,
        policy: StockPolicy.warn,
        time: DateTime.utc(2026),
        number: 'x',
      );
      // Latte memakai 200 ml dari stok 3 ml bernilai 10: biaya 200 x 10 / 3 = 667 (666,67).
      final usage = result.sale.items.single.usages
          .firstWhere((u) => u.ingredientId == FixtureA.susuId);
      expect(usage.baseQuantity, 200);
      expect(usage.cost, 667);
    });
  });

  group('Peringatan stok hanya saat memburuk', () {
    Ingredient susuWithStock(int quantity) => FixtureA
        .ingredients()
        .firstWhere((i) => i.id == FixtureA.susuId)
        .copyWith(stock: StockState(quantity, quantity * 15));

    test('stok turun ke batas minimum memicu peringatan menipis', () {
      // Minimum susu 500 ml; 700 - 360 = 340 ml (menipis).
      final result = plan(
        quantity: 3,
        stock: const <int, StockState>{FixtureA.susuId: StockState(700, 10500)},
      );
      expect(result.alerts, hasLength(1));
      expect(result.alerts.single.ingredientId, FixtureA.susuId);
      expect(result.alerts.single.level, StockLevel.low);
    });

    test('stok sudah menipis lalu masih menipis tidak memicu peringatan ulang', () {
      final result = plan(
        quantity: 1,
        stock: const <int, StockState>{FixtureA.susuId: StockState(400, 6000)},
      );
      expect(result.alerts.where((a) => a.ingredientId == FixtureA.susuId), isEmpty);
    });

    test('dari menipis ke habis memicu peringatan habis', () {
      final result = plan(
        quantity: 1,
        stock: const <int, StockState>{FixtureA.susuId: StockState(120, 1800)},
      );
      final alert = result.alerts.firstWhere((a) => a.ingredientId == FixtureA.susuId);
      expect(alert.level, StockLevel.out);
    });

    test('tingkat stok mengikuti batas minimum', () {
      expect(susuWithStock(501).level, StockLevel.ok);
      expect(susuWithStock(500).level, StockLevel.low);
      expect(susuWithStock(1).level, StockLevel.low);
      expect(susuWithStock(0).level, StockLevel.out);
      expect(susuWithStock(-5).level, StockLevel.out);
    });
  });

  group('F. Rencana pembatalan', () {
    test('F1 dan F2: stok dan nilai kembali dari snapshot', () {
      final sale = plan(
        quantity: 3,
        discount: const PercentDiscount(10),
        paid: 50000,
      ).sale.copyWith(id: 1);

      final afterSale = <int, Ingredient>{
        FixtureA.kopiId: FixtureA.ingredients()[0].copyWith(stock: const StockState(955, 191000)),
        FixtureA.susuId: FixtureA.ingredients()[1].copyWith(stock: const StockState(1640, 24600)),
        FixtureA.gulaId: FixtureA.ingredients()[2].copyWith(stock: const StockState(470, 7520)),
      };
      final voidPlan = SalePlanner.planVoid(sale: sale, ingredients: afterSale);
      final after = {for (final c in voidPlan.stockChanges) c.ingredientId: c.after};
      expect(after[FixtureA.kopiId], const StockState(1000, 200000));
      expect(after[FixtureA.susuId], const StockState(2000, 30000));
      expect(after[FixtureA.gulaId], const StockState(500, 8000));
    });

    test('F5: transaksi yang sudah batal tidak dapat dibatalkan lagi', () {
      final sale = plan(quantity: 1)
          .sale
          .copyWith(id: 1, status: SaleStatus.voided);
      expect(
        () => SalePlanner.planVoid(sale: sale, ingredients: const <int, Ingredient>{}),
        throwsA(isA<SaleAlreadyVoided>()),
      );
    });
  });
}
