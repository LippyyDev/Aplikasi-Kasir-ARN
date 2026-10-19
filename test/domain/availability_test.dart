import 'package:arn_kasir/domain/core/failures.dart';
import 'package:arn_kasir/domain/inventory/stock_state.dart';
import 'package:arn_kasir/domain/menu/availability.dart';
import 'package:arn_kasir/domain/menu/menu_item.dart';
import 'package:arn_kasir/domain/sales/cart.dart';
import 'package:flutter_test/flutter_test.dart';

import '../support/fixtures.dart';

void main() {
  final kopiSusu = FixtureA.menus().firstWhere((m) => m.id == FixtureA.kopiSusuId);
  final latte = FixtureA.menus().firstWhere((m) => m.id == FixtureA.latteId);

  group('D. Ketersediaan menu', () {
    test('D1: porsi tersedia = min(63, 13, 47) = 13', () {
      final catalog = FixtureA.catalog(
        stock: const <int, StockState>{
          FixtureA.kopiId: StockState(955, 191000),
          FixtureA.susuId: StockState(1640, 24600),
          FixtureA.gulaId: StockState(470, 7520),
        },
      );
      expect(availablePortions(kopiSusu, catalog), 13);
    });

    test('menu tanpa resep tidak terbatas (null)', () {
      final catalog = FixtureA.catalog();
      const titipan = MenuItem(id: 9, name: 'Titipan', price: 5000);
      expect(availablePortions(titipan, catalog), isNull);
      expect(isSellable(titipan, catalog, StockPolicy.block), isTrue);
    });

    test('stok nol atau negatif dihitung 0 porsi', () {
      final catalog = FixtureA.catalog(
        stock: const <int, StockState>{FixtureA.susuId: StockState(-20, -300)},
      );
      expect(availablePortions(kopiSusu, catalog), 0);
    });

    test('D2: blokir, susu 100 ml (resep 120): porsi 0 dan penambahan ditolak', () {
      final catalog = FixtureA.catalog(
        stock: const <int, StockState>{FixtureA.susuId: StockState(100, 1500)},
      );
      expect(availablePortions(kopiSusu, catalog), 0);
      expect(isSellable(kopiSusu, catalog, StockPolicy.block), isFalse);
      expect(
        () => const Cart().add(kopiSusu, catalog, StockPolicy.block),
        throwsA(isA<InsufficientStock>()),
      );
    });

    test('D3: peringatkan, kondisi D2: tetap dapat ditambahkan', () {
      final catalog = FixtureA.catalog(
        stock: const <int, StockState>{FixtureA.susuId: StockState(100, 1500)},
      );
      expect(isSellable(kopiSusu, catalog, StockPolicy.warn), isTrue);
      final cart = const Cart().add(kopiSusu, catalog, StockPolicy.warn);
      expect(cart.quantityOf(FixtureA.kopiSusuId), 1);
    });

    test('D4: keranjang 5 Kopi Susu, susu 700 ml (600 ml dipakai) diizinkan', () {
      final catalog = FixtureA.catalog(
        stock: const <int, StockState>{FixtureA.susuId: StockState(700, 10500)},
      );
      var cart = const Cart();
      for (var i = 0; i < 5; i++) {
        cart = cart.add(kopiSusu, catalog, StockPolicy.block);
      }
      expect(cart.quantityOf(FixtureA.kopiSusuId), 5);
    });

    test('D5: keranjang 6 Kopi Susu, susu 700 ml (720 ml) ditolak pada item ke-6', () {
      final catalog = FixtureA.catalog(
        stock: const <int, StockState>{FixtureA.susuId: StockState(700, 10500)},
      );
      var cart = const Cart();
      for (var i = 0; i < 5; i++) {
        cart = cart.add(kopiSusu, catalog, StockPolicy.block);
      }
      expect(
        () => cart.add(kopiSusu, catalog, StockPolicy.block),
        throwsA(
          isA<InsufficientStock>().having(
            (e) => e.shortages.single.required,
            'kebutuhan susu',
            720,
          ),
        ),
      );
      expect(cart.quantityOf(FixtureA.kopiSusuId), 5);
    });

    test('D6: pemakaian bersama dijumlahkan per bahan walau tiap item cukup sendiri', () {
      final catalog = FixtureA.catalog(
        stock: const <int, StockState>{FixtureA.susuId: StockState(600, 9000)},
      );
      // Tiap item sendirian cukup: 2 x 120 = 240 ml dan 2 x 200 = 400 ml.
      expect(
        findShortages(
          const <DemandLine>[
            DemandLine(menuId: FixtureA.kopiSusuId, quantity: 2),
          ],
          catalog,
        ),
        isEmpty,
      );
      expect(
        findShortages(
          const <DemandLine>[DemandLine(menuId: FixtureA.latteId, quantity: 2)],
          catalog,
        ),
        isEmpty,
      );

      // Gabungan 240 + 400 = 640 ml melebihi 600 ml.
      final shortages = findShortages(
        const <DemandLine>[
          DemandLine(menuId: FixtureA.kopiSusuId, quantity: 2),
          DemandLine(menuId: FixtureA.latteId, quantity: 2),
        ],
        catalog,
      );
      expect(shortages, hasLength(1));
      expect(shortages.single.ingredientName, FixtureA.susu);
      expect(shortages.single.required, 640);
      expect(shortages.single.available, 600);

      // Lewat keranjang: Latte kedua ditolak.
      var cart = const Cart()
          .add(kopiSusu, catalog, StockPolicy.block)
          .add(kopiSusu, catalog, StockPolicy.block)
          .add(latte, catalog, StockPolicy.block);
      expect(
        () => cart.add(latte, catalog, StockPolicy.block),
        throwsA(isA<InsufficientStock>()),
      );
      cart = cart.remove(FixtureA.latteId);
      expect(cart.itemCount, 2);
    });

    test('mengurangi kuantitas tidak diblokir walau stok sudah kurang', () {
      final catalog = FixtureA.catalog(
        stock: const <int, StockState>{FixtureA.susuId: StockState(100, 1500)},
      );
      final cart = const Cart(
        lines: <CartLine>[
          CartLine(
            menuId: FixtureA.kopiSusuId,
            menuName: FixtureA.kopiSusu,
            unitPrice: 18000,
            quantity: 3,
          ),
        ],
      );
      final reduced = cart.setQuantity(kopiSusu, 2, catalog, StockPolicy.block);
      expect(reduced.quantityOf(FixtureA.kopiSusuId), 2);
      expect(
        cart.setQuantity(kopiSusu, 0, catalog, StockPolicy.block).isEmpty,
        isTrue,
      );
    });
  });

  group('Keranjang: total dan diskon', () {
    final catalog = FixtureA.catalog();

    test('E4: subtotal 54.000, diskon 10% = 5.400, total 48.600', () {
      var cart = const Cart();
      for (var i = 0; i < 3; i++) {
        cart = cart.add(kopiSusu, catalog, StockPolicy.block);
      }
      final totals = cart.withDiscount(const PercentDiscount(10)).totals();
      expect(totals.subtotal, 54000);
      expect(totals.discount, 5400);
      expect(totals.total, 48600);
    });

    test('diskon nominal dan batasnya', () {
      final cart = const Cart().add(kopiSusu, catalog, StockPolicy.block);
      expect(cart.withDiscount(const AmountDiscount(3000)).totals().total, 15000);
      expect(
        () => cart.withDiscount(const AmountDiscount(18001)).totals(),
        throwsA(isA<InvalidDiscount>()),
      );
      expect(
        () => cart.withDiscount(const AmountDiscount(-1)).totals(),
        throwsA(isA<InvalidDiscount>()),
      );
      expect(
        () => cart.withDiscount(const PercentDiscount(101)).totals(),
        throwsA(isA<InvalidDiscount>()),
      );
    });

    test('diskon persen dibulatkan HALF_UP ke Rupiah', () {
      // 18.000 x 15% = 2.700 tepat; 18.001 x 15% = 2.700,15 menjadi 2.700;
      // 5 x 10% = 0,5 menjadi 1.
      expect(const PercentDiscount(15).resolve(18001), 2700);
      expect(const PercentDiscount(10).resolve(5), 1);
    });

    test('catatan item dipangkas dan dapat dihapus', () {
      var cart = const Cart().add(kopiSusu, catalog, StockPolicy.block);
      cart = cart.withNote(FixtureA.kopiSusuId, '  less sugar  ');
      expect(cart.lines.single.note, 'less sugar');
      cart = cart.withNote(FixtureA.kopiSusuId, '   ');
      expect(cart.lines.single.note, isNull);
    });

    test('menu tidak aktif tidak dapat ditambahkan', () {
      const inactive = MenuItem(id: 8, name: 'Nonaktif', price: 1000, active: false);
      expect(
        () => const Cart().add(inactive, catalog, StockPolicy.warn),
        throwsA(isA<MenuUnavailable>()),
      );
    });
  });
}
