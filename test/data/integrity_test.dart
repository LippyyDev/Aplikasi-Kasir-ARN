import 'package:arn_kasir/data/repositories/category_repository.dart';
import 'package:arn_kasir/data/repositories/expense_repository.dart';
import 'package:arn_kasir/data/repositories/ingredient_repository.dart';
import 'package:arn_kasir/data/repositories/menu_repository.dart';
import 'package:arn_kasir/data/repositories/report_repository.dart';
import 'package:arn_kasir/domain/core/failures.dart';
import 'package:arn_kasir/domain/core/fraction.dart';
import 'package:arn_kasir/domain/expenses/expense.dart';
import 'package:arn_kasir/domain/inventory/stock_state.dart';
import 'package:arn_kasir/domain/menu/availability.dart';
import 'package:arn_kasir/domain/models/report_models.dart';
import 'package:arn_kasir/domain/sales/cart.dart';
import 'package:arn_kasir/domain/sales/payment.dart';
import 'package:arn_kasir/domain/sales/sale_store.dart';
import 'package:arn_kasir/domain/units/dimension.dart';
import 'package:arn_kasir/domain/units/measure_unit.dart';
import 'package:flutter_test/flutter_test.dart';

import '../support/drift_harness.dart';
import '../support/fixtures.dart';

/// TEST_CASES_TAKARAN bagian J (penghapusan dan integritas) terhadap SQLite sungguhan.
void main() {
  group('Penghapusan dan integritas', () {
    late DriftHarness h;
    late IngredientRepository ingredients;
    late MenuRepository menus;
    late CategoryRepository categories;

    setUp(() async {
      h = DriftHarness();
      await h.seedFixtureA();
      ingredients = IngredientRepository(h.db);
      menus = MenuRepository(h.db);
      categories = CategoryRepository(h.db);
    });

    tearDown(() async => h.dispose());

    test('J1: bahan yang dipakai resep ditolak beserta daftar menunya', () async {
      await expectLater(
        ingredients.delete(FixtureA.gulaId),
        throwsA(
          isA<IngredientInUse>().having(
            (e) => e.menuNames,
            'menuNames',
            contains(FixtureA.kopiSusu),
          ),
        ),
      );
      expect(await ingredients.find(FixtureA.gulaId), isNotNull);
    });

    test('J1 lanjutan: bahan tanpa riwayat dihapus, yang berriwayat diarsipkan', () async {
      final fresh = await ingredients.save(
        const IngredientDraft(name: 'Garam', dimension: Dimension.mass),
      );
      await ingredients.delete(fresh);
      expect(await ingredients.find(fresh), isNull);

      final stocked = await ingredients.save(
        const IngredientDraft(
          name: 'Teh',
          dimension: Dimension.mass,
          openingQuantity: 100,
          openingValue: 5000,
        ),
      );
      await ingredients.delete(stocked);
      // Diarsipkan: tidak ada di daftar aktif, tetapi riwayat dan datanya tetap ada.
      expect(await ingredients.find(stocked), isNotNull);
      final active = await ingredients.watchAll().first;
      expect(active.map((i) => i.name), isNot(contains('Teh')));
    });

    test('J2: menu yang pernah terjual dihapus lunak dan tetap ada di laporan', () async {
      final now = DateTime.utc(2026, 10, 19, 3);
      final menuId = await h.menuId(FixtureA.kopiSusu);
      await CheckoutUseCase(h.saleStore, clock: () => now)(
        CheckoutRequest(
          lines: <CartLine>[
            CartLine(
              menuId: menuId,
              menuName: FixtureA.kopiSusu,
              unitPrice: 18000,
              quantity: 1,
            ),
          ],
          method: PaymentMethod.nonCash,
          policy: StockPolicy.block,
        ),
      );

      await menus.softDelete(menuId);

      final listed = await menus.watchAll().first;
      expect(listed.map((m) => m.name), isNot(contains(FixtureA.kopiSusu)));

      final top = await ReportRepository(h.db).topMenus(
        DateRange(from: DateTime.utc(2026, 10, 19), to: DateTime.utc(2026, 10, 20)),
      );
      expect(top.single.menuName, FixtureA.kopiSusu);
    });

    test('J3: nama bahan dan kategori duplikat ditolak', () async {
      await expectLater(
        ingredients.save(const IngredientDraft(name: 'gula', dimension: Dimension.mass)),
        throwsA(isA<DuplicateName>()),
      );
      await expectLater(categories.add('Kopi'), throwsA(isA<DuplicateName>()));
      await expectLater(
        ExpenseRepository(h.db).addCategory('Susut'),
        throwsA(isA<DuplicateName>()),
      );
    });

    test('J4: hapus kategori berisi menu memindahkan menu, tidak menghapusnya', () async {
      final other = await categories.add('Makanan');
      expect(await categories.menuCount(1), 2);

      await categories.delete(1, moveMenusTo: other);
      expect(await categories.menuCount(other), 2);
      expect((await menus.watchAll().first).length, 2);

      await categories.delete(other);
      final all = await menus.watchAll().first;
      expect(all, hasLength(2));
      expect(all.every((m) => m.categoryId == null), isTrue);
    });

    test('J5: mengubah faktor satuan kustom tidak mengubah stok tersimpan', () async {
      final before = await h.stockOf(FixtureA.gula);
      await ingredients.save(
        IngredientDraft(
          id: FixtureA.gulaId,
          name: FixtureA.gula,
          dimension: Dimension.mass,
          purchaseUnitName: 'kg',
          minStock: 100,
          customUnits: <CustomUnit>[
            CustomUnit(name: 'sendok makan', factor: Fraction(15)),
          ],
        ),
      );
      expect(await h.stockOf(FixtureA.gula), before);
      expect(before, const StockState(500, 8000));

      final reloaded = await ingredients.find(FixtureA.gulaId);
      expect(reloaded!.customUnits.single.factor, Fraction(15));
    });

    test('pengeluaran otomatis dan kategori sistem tidak dapat diubah', () async {
      final expenses = ExpenseRepository(h.db);
      await expectLater(
        expenses.renameCategory(SystemExpenseCategory.ingredients, 'Lain'),
        throwsA(isA<ProtectedRecord>()),
      );
      await expectLater(
        expenses.deleteCategory(SystemExpenseCategory.shrinkage),
        throwsA(isA<ProtectedRecord>()),
      );

      final custom = await expenses.addCategory('Sewa');
      final id = await expenses.addManual(
        date: DateTime.utc(2026, 10, 19),
        categoryId: custom,
        amount: 100000,
      );
      // Kategori yang dipakai catatan pengeluaran tidak bisa dihapus.
      await expectLater(expenses.deleteCategory(custom), throwsA(isA<CategoryInUse>()));
      await expenses.deleteManual(id);
      await expenses.deleteCategory(custom);
    });

    test('pengeluaran manual harus lebih dari nol', () async {
      final expenses = ExpenseRepository(h.db);
      final custom = await expenses.addCategory('Sewa');
      await expectLater(
        expenses.addManual(date: DateTime.utc(2026, 10, 19), categoryId: custom, amount: 0),
        throwsA(isA<InvalidMoneyAmount>()),
      );
    });
  });
}
