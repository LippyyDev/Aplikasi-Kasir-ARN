import 'package:arn_kasir/domain/core/failures.dart';
import 'package:arn_kasir/domain/core/fraction.dart';
import 'package:arn_kasir/domain/menu/recipe.dart';
import 'package:arn_kasir/domain/units/dimension.dart';
import 'package:arn_kasir/domain/units/measure_unit.dart';
import 'package:arn_kasir/domain/units/unit_converter.dart';
import 'package:flutter_test/flutter_test.dart';

UnitConverter gulaConverter() => UnitConverter(
      UnitCatalog(Dimension.mass, <CustomUnit>[
        CustomUnit(name: 'sendok makan', factor: Fraction(12)),
      ]),
    );

UnitConverter kopiConverter() => UnitConverter(
      UnitCatalog(Dimension.mass, <CustomUnit>[
        CustomUnit(name: 'sachet', factor: Fraction(25)),
        CustomUnit(name: 'sendok teh', factor: Fraction(5, 2)),
      ]),
    );

UnitConverter susuConverter() => UnitConverter(
      UnitCatalog(Dimension.volume, <CustomUnit>[
        CustomUnit(name: 'cup', factor: Fraction(240)),
      ]),
    );

void main() {
  group('A. Konversi satuan', () {
    test('A1: gula 2,5 sendok makan (1 sdm = 12 g) menjadi 30 g', () {
      expect(gulaConverter().toBase('sendok makan', Fraction.parse('2,5')), 30);
    });

    test('A2: gula 0,25 kg menjadi 250 g', () {
      expect(gulaConverter().toBase('kg', Fraction.parse('0,25')), 250);
    });

    test('A3: susu 1,5 cup (1 cup = 240 ml) menjadi 360 ml', () {
      expect(susuConverter().toBase('cup', Fraction.parse('1,5')), 360);
    });

    test('A4: kopi 3 sachet (1 sachet = 25 g) menjadi 75 g', () {
      expect(kopiConverter().toBase('sachet', Fraction(3)), 75);
    });

    test('A5: kopi 3 sendok teh (2,5 g) = 7,5 dibulatkan menjadi 8 g', () {
      expect(kopiConverter().toBase('sendok teh', Fraction(3)), 8);
    });

    test('A6: kopi 1 sendok teh (2,5 g) dibulatkan menjadi 3 g', () {
      expect(kopiConverter().toBase('sendok teh', Fraction(1)), 3);
    });

    test('A7: bahan bermassa dengan satuan volume ditolak (antardimensi)', () {
      expect(
        () => gulaConverter().toBase('liter', Fraction(1)),
        throwsA(isA<DimensionMismatch>()),
      );
      expect(
        () => susuConverter().toBase('kg', Fraction(1)),
        throwsA(isA<DimensionMismatch>()),
      );
    });

    test('A8: jumlah nol atau negatif ditolak', () {
      expect(
        () => gulaConverter().toBase('g', Fraction.zero),
        throwsA(isA<NonPositiveQuantity>()),
      );
      expect(
        () => gulaConverter().toBase('g', Fraction(-3)),
        throwsA(isA<NonPositiveQuantity>()),
      );
      expect(
        () => gulaConverter().toBaseFromText('g', '-3'),
        throwsA(isA<NonPositiveQuantity>()),
      );
      expect(
        () => gulaConverter().toBaseFromText('g', '0'),
        throwsA(isA<NonPositiveQuantity>()),
      );
    });

    test('A8: nol diterima hanya bila allowZero (stok fisik opname)', () {
      expect(gulaConverter().toBase('g', Fraction.zero, allowZero: true), 0);
      expect(
        () => gulaConverter().toBase('g', Fraction(-1), allowZero: true),
        throwsA(isA<NonPositiveQuantity>()),
      );
    });

    test('A9: 0,04 g dibulatkan menjadi 0 dan ditolak untuk resep', () {
      expect(gulaConverter().toBase('g', Fraction.parse('0,04')), 0);
      expect(
        () => RecipeLine.fromInput(
          ingredientId: 3,
          converter: gulaConverter(),
          unitName: 'g',
          amountText: '0,04',
        ),
        throwsA(isA<QuantityRoundsToZero>()),
      );
    });

    test('A10: "2,5" dan "2.5" keduanya menghasilkan 30 g', () {
      expect(gulaConverter().toBaseFromText('sendok makan', '2,5'), 30);
      expect(gulaConverter().toBaseFromText('sendok makan', '2.5'), 30);
    });

    test('A11: teks bukan angka ditolak', () {
      for (final bad in <String>['abc', '2,,5', '', '1e3']) {
        expect(
          () => gulaConverter().toBaseFromText('g', bad),
          throwsA(isA<InvalidNumberFormat>()),
          reason: '"$bad"',
        );
      }
    });

    test('resep valid dibentuk dari input teks', () {
      final line = RecipeLine.fromInput(
        ingredientId: 3,
        converter: gulaConverter(),
        unitName: 'sendok makan',
        amountText: '1',
      );
      expect(line.quantity, 12);
    });

    test('satuan tidak dikenal ditolak', () {
      expect(
        () => gulaConverter().toBase('karung', Fraction(1)),
        throwsA(isA<UnknownUnit>()),
      );
    });

    test('nama satuan tidak membedakan huruf besar dan kecil', () {
      expect(gulaConverter().toBase('KG', Fraction(1)), 1000);
      expect(gulaConverter().toBase('Sendok Makan', Fraction(1)), 12);
    });

    test('fromBase mengembalikan jumlah pada satuan tampil', () {
      expect(gulaConverter().fromBase('kg', 250), Fraction(1, 4));
      expect(gulaConverter().fromBase('sendok makan', 30), Fraction(5, 2));
    });
  });

  group('Katalog satuan', () {
    test('satuan kustom tidak boleh bentrok dengan satuan baku atau satuan lain', () {
      expect(
        () => UnitCatalog(Dimension.mass, <CustomUnit>[
          CustomUnit(name: 'KG', factor: Fraction(1000)),
        ]),
        throwsA(isA<InvalidUnitDefinition>()),
      );
      expect(
        () => UnitCatalog(Dimension.mass, <CustomUnit>[
          CustomUnit(name: 'sdm', factor: Fraction(12)),
          CustomUnit(name: 'SDM', factor: Fraction(15)),
        ]),
        throwsA(isA<InvalidUnitDefinition>()),
      );
    });

    test('faktor satuan kustom harus positif dan nama tidak kosong', () {
      expect(
        () => UnitCatalog(Dimension.mass, <CustomUnit>[
          CustomUnit(name: 'x', factor: Fraction.zero),
        ]),
        throwsA(isA<InvalidUnitDefinition>()),
      );
      expect(
        () => UnitCatalog(Dimension.mass, <CustomUnit>[
          CustomUnit(name: '  ', factor: Fraction(1)),
        ]),
        throwsA(isA<InvalidUnitDefinition>()),
      );
    });

    test('daftar pilihan memuat satuan baku sedimensi lalu satuan kustom', () {
      final names = gulaConverter().catalog.selectable.map((u) => u.name).toList();
      expect(names, <String>['mg', 'g', 'kg', 'sendok makan']);
      expect(gulaConverter().catalog.baseUnit.name, 'g');
    });
  });
}
