import 'dart:math';

import 'package:arn_kasir/domain/inventory/stock_state.dart';
import 'package:flutter_test/flutter_test.dart';

void main() {
  group('G. Rata-rata tertimbang dan pembulatan', () {
    test('G1: dua restok menghasilkan stok 1500 g, nilai 24.000, rata-rata Rp 16', () {
      var state = StockState.empty;
      state = StockLedger.restock(state, quantity: 1000, value: 15000);
      state = StockLedger.restock(state, quantity: 500, value: 9000);
      expect(state, const StockState(1500, 24000));
      expect(state.value ~/ state.quantity, 16);
    });

    test('G2: pakai 300 g berbiaya 4.800, sisa nilai 19.200, sisa stok 1200 g', () {
      final result = StockLedger.consume(const StockState(1500, 24000), 300);
      expect(result.cost, 4800);
      expect(result.after, const StockState(1200, 19200));
    });

    test('G3: stok 3 nilai Rp 10, pakai 1: biaya 3 (3,33), sisa nilai 7, stok 2', () {
      final result = StockLedger.consume(const StockState(3, 10), 1);
      expect(result.cost, 3);
      expect(result.after, const StockState(2, 7));
    });

    test('G4: lanjutan G3, pakai 1: 7/2 = 3,5 HALF_UP menjadi 4, sisa nilai 3', () {
      final result = StockLedger.consume(const StockState(2, 7), 1);
      expect(result.cost, 4);
      expect(result.after, const StockState(1, 3));
    });

    test('G5: lanjutan G4, pakai 1 (stok habis): biaya 3, sisa nilai 0, stok 0', () {
      final result = StockLedger.consume(const StockState(1, 3), 1);
      expect(result.cost, 3);
      expect(result.after, StockState.empty);
    });

    test('G6: jumlah biaya G3 + G4 + G5 sama dengan nilai awal (10)', () {
      var state = const StockState(3, 10);
      var total = 0;
      for (var i = 0; i < 3; i++) {
        final result = StockLedger.consume(state, 1);
        total += result.cost;
        state = result.after;
      }
      expect(total, 10);
      expect(state, StockState.empty);
    });

    test('G6 properti: untuk urutan pemakaian acak, jumlah biaya = nilai awal', () {
      final random = Random(20261019);
      for (var trial = 0; trial < 3000; trial++) {
        final initialQuantity = 1 + random.nextInt(5000);
        final initialValue = random.nextInt(10000000);
        var state = StockState(initialQuantity, initialValue);
        var totalCost = 0;
        while (state.quantity > 0) {
          final use = 1 + random.nextInt(state.quantity);
          final result = StockLedger.consume(state, use);
          totalCost += result.cost;
          state = result.after;
        }
        expect(totalCost, initialValue, reason: 'percobaan $trial');
        expect(state, StockState.empty);
      }
    });

    test('pemakaian melebihi stok: nilai ikut negatif pada rata-rata terakhir', () {
      final result = StockLedger.consume(const StockState(100, 1600), 120);
      expect(result.cost, 1920);
      expect(result.after, const StockState(-20, -320));
    });

    test('restok setelah stok negatif memulihkan rata-rata semula', () {
      final overdrawn = StockLedger.consume(const StockState(100, 1600), 120).after;
      final restocked = StockLedger.restock(overdrawn, quantity: 1000, value: 16000);
      expect(restocked, const StockState(980, 15680));
    });

    test('stok tepat nol: rata-rata tidak diketahui sehingga biaya 0', () {
      final result = StockLedger.consume(StockState.empty, 5);
      expect(result.cost, 0);
      expect(result.after, const StockState(-5, 0));
    });

    test('jumlah pemakaian nol atau negatif adalah kesalahan pemrograman', () {
      expect(() => StockLedger.consume(const StockState(10, 10), 0), throwsArgumentError);
      expect(() => StockLedger.consume(const StockState(10, 10), -1), throwsArgumentError);
    });

    test('restok menolak jumlah nol dan nilai negatif', () {
      expect(
        () => StockLedger.restock(StockState.empty, quantity: 0, value: 10),
        throwsArgumentError,
      );
      expect(
        () => StockLedger.restock(StockState.empty, quantity: 1, value: -1),
        throwsArgumentError,
      );
    });

    test('pembatalan mengembalikan jumlah dan biaya persis seperti snapshot', () {
      final sold = StockLedger.consume(const StockState(3, 10), 2);
      final restored = StockLedger.reverseConsume(
        sold.after,
        quantity: 2,
        cost: sold.cost,
      );
      expect(restored, const StockState(3, 10));
    });
  });

  group('H. Penyesuaian stok', () {
    test('H1 dan H2: fisik 450 dari 470 g (nilai 7.520): selisih -20, nilai -320', () {
      final result = StockLedger.adjustTo(const StockState(470, 7520), 450);
      expect(result.quantityDelta, -20);
      expect(result.valueDelta, -320);
      expect(result.after, const StockState(450, 7200));
    });

    test('H4: fisik lebih besar menambah nilai pada rata-rata saat ini', () {
      final result = StockLedger.adjustTo(const StockState(470, 7520), 500);
      expect(result.quantityDelta, 30);
      expect(result.valueDelta, 480);
      expect(result.after, const StockState(500, 8000));
    });

    test('fisik nol menghabiskan seluruh nilai tersisa', () {
      final result = StockLedger.adjustTo(const StockState(3, 10), 0);
      expect(result.valueDelta, -10);
      expect(result.after, StockState.empty);
    });

    test('dari stok negatif ke nol menetralkan nilai negatif', () {
      final result = StockLedger.adjustTo(const StockState(-20, -320), 0);
      expect(result.quantityDelta, 20);
      expect(result.valueDelta, 320);
      expect(result.after, StockState.empty);
    });

    test('tanpa selisih tidak ada perubahan', () {
      final result = StockLedger.adjustTo(const StockState(470, 7520), 470);
      expect(result.quantityDelta, 0);
      expect(result.valueDelta, 0);
      expect(result.after, const StockState(470, 7520));
    });
  });
}
