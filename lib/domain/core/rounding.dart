import 'package:arn_kasir/domain/core/failures.dart';

/// Pembagian bilangan bulat dengan pembulatan HALF_UP (CLAUDE.md aturan 2 dan 6).
///
/// Untuk nilai positif rumusnya `(2n + d) ~/ (2d)`. Nilai negatif dibulatkan simetris
/// (0,5 menjauhi nol) sehingga `round(-x) == -round(x)`. Seluruh perhitungan antara
/// memakai [BigInt] agar hasil kali dua `int` 64 bit tidak meluap diam-diam.
BigInt divRoundHalfUpBig(BigInt numerator, BigInt denominator) {
  if (denominator == BigInt.zero) {
    throw ArgumentError.value(denominator, 'denominator', 'tidak boleh nol');
  }
  final negative = numerator.isNegative != denominator.isNegative;
  final absNumerator = numerator.abs();
  final absDenominator = denominator.abs();
  final quotient =
      (absNumerator * BigInt.two + absDenominator) ~/ (absDenominator * BigInt.two);
  return negative ? -quotient : quotient;
}

/// Mengonversi [value] ke `int` atau melempar [ArithmeticOverflow].
int bigToInt(BigInt value) {
  if (!value.isValidInt) throw const ArithmeticOverflow();
  return value.toInt();
}

/// `round_half_up(numerator / denominator)`.
int divRoundHalfUp(int numerator, int denominator) => bigToInt(
      divRoundHalfUpBig(BigInt.from(numerator), BigInt.from(denominator)),
    );

/// `round_half_up(a * b / divisor)` tanpa risiko luapan pada hasil kali `a * b`.
int mulDivRoundHalfUp(int a, int b, int divisor) => bigToInt(
      divRoundHalfUpBig(BigInt.from(a) * BigInt.from(b), BigInt.from(divisor)),
    );
