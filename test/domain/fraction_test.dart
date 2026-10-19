import 'package:arn_kasir/domain/core/failures.dart';
import 'package:arn_kasir/domain/core/fraction.dart';
import 'package:arn_kasir/domain/core/rounding.dart';
import 'package:flutter_test/flutter_test.dart';

void main() {
  group('Fraction', () {
    test('menormalkan pecahan: FPB 1 dan penyebut positif', () {
      expect(Fraction(10, 4), Fraction(5, 2));
      expect(Fraction(1, -2), Fraction(-1, 2));
      expect(Fraction(0, 7), Fraction.zero);
      expect(Fraction(6, 3).denominator, 1);
    });

    test('A10: "2,5" dan "2.5" keduanya diparse sebagai 5/2', () {
      expect(Fraction.parse('2,5'), Fraction(5, 2));
      expect(Fraction.parse('2.5'), Fraction(5, 2));
      expect(Fraction.parse(' 0,25 '), Fraction(1, 4));
      expect(Fraction.parse(',5'), Fraction(1, 2));
      expect(Fraction.parse('12'), Fraction(12));
      expect(Fraction.parse('-3'), Fraction(-3));
    });

    test('A11: teks bukan angka desimal valid ditolak', () {
      for (final bad in <String>['abc', '2,,5', '', '1e3', '1,2,3', '2.', ' ', '--1']) {
        expect(
          () => Fraction.parse(bad),
          throwsA(isA<InvalidNumberFormat>()),
          reason: '"$bad" seharusnya ditolak',
        );
        expect(Fraction.tryParse(bad), isNull);
      }
    });

    test('terlalu banyak digit ditolak agar tidak meluap', () {
      expect(
        () => Fraction.parse('1234567890123456'),
        throwsA(isA<InvalidNumberFormat>()),
      );
      expect(
        () => Fraction.parse('0,1234567'),
        throwsA(isA<InvalidNumberFormat>()),
      );
    });

    test('operasi aritmetika pecahan', () {
      expect(Fraction(1, 2) + Fraction(1, 3), Fraction(5, 6));
      expect(Fraction(1, 2) - Fraction(1, 3), Fraction(1, 6));
      expect(Fraction(5, 2) * Fraction(12), Fraction(30));
      expect(Fraction(30) / Fraction(12), Fraction(5, 2));
      expect(Fraction(5, 2).timesInt(3), Fraction(15, 2));
      expect(Fraction(1, 3).compareTo(Fraction(1, 2)), lessThan(0));
    });

    test('pembulatan HALF_UP menjauhi nol pada 0,5', () {
      expect(Fraction(15, 2).roundHalfUp(), 8); // 7,5
      expect(Fraction(5, 2).roundHalfUp(), 3); // 2,5
      expect(Fraction(7, 3).roundHalfUp(), 2); // 2,33
      expect(Fraction(1, 25).roundHalfUp(), 0); // 0,04
      expect(Fraction(-5, 2).roundHalfUp(), -3);
      expect(Fraction(-7, 3).roundHalfUp(), -2);
    });

    test('floor untuk nilai positif dan negatif', () {
      expect(Fraction(7, 2).floor(), 3);
      expect(Fraction(-7, 2).floor(), -4);
      expect(Fraction(4, 2).floor(), 2);
    });

    test('toDecimalString tanpa double', () {
      expect(Fraction(5, 2).toDecimalString(), '2,5');
      expect(Fraction(1, 3).toDecimalString(), '0,333');
      expect(Fraction(2, 3).toDecimalString(), '0,667');
      expect(Fraction(30).toDecimalString(), '30');
      expect(Fraction(1, 4).toDecimalString(decimalSeparator: '.'), '0.25');
      expect(Fraction(-1, 4).toDecimalString(), '-0,25');
      expect(Fraction(1, 3000).toDecimalString(), '0');
      expect(Fraction(1, 2000).toDecimalString(), '0,001'); // 0,0005 dibulatkan HALF_UP
      expect(Fraction(7, 2).toDecimalString(maxFractionDigits: 0), '4');
    });
  });

  group('Pembulatan bilangan bulat', () {
    test('divRoundHalfUp simetris untuk nilai negatif', () {
      expect(divRoundHalfUp(10, 3), 3);
      expect(divRoundHalfUp(7, 2), 4);
      expect(divRoundHalfUp(-7, 2), -4);
      expect(divRoundHalfUp(7, -2), -4);
      expect(divRoundHalfUp(-7, -2), 4);
      expect(divRoundHalfUp(0, 5), 0);
    });

    test('mulDivRoundHalfUp tidak meluap pada hasil kali besar', () {
      // 10^12 x 10^12 / 10^12 melampaui 2^63 pada hasil kali antara.
      const big = 1000000000000;
      expect(mulDivRoundHalfUp(big, big, big), big);
    });

    test('hasil di luar int melempar ArithmeticOverflow', () {
      const big = 9000000000000000000;
      expect(
        () => mulDivRoundHalfUp(big, 10, 1),
        throwsA(isA<ArithmeticOverflow>()),
      );
    });

    test('pembagi nol adalah kesalahan pemrograman', () {
      expect(() => divRoundHalfUp(1, 0), throwsArgumentError);
    });
  });
}
