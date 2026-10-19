import 'package:arn_kasir/domain/core/failures.dart';
import 'package:arn_kasir/domain/core/rounding.dart';

/// Pecahan bilangan bulat yang selalu ternormalisasi (penyebut positif, FPB 1).
///
/// Dipakai untuk faktor konversi satuan (2,5 g = 5/2) dan input teks pengguna agar tidak
/// ada `double` pada jalur stok, resep, maupun uang (CLAUDE.md aturan 1 dan 2).
final class Fraction implements Comparable<Fraction> {
  const Fraction._(this.numerator, this.denominator);

  /// Membuat pecahan `numerator / denominator`. [denominator] tidak boleh nol.
  factory Fraction(int numerator, [int denominator = 1]) {
    if (denominator == 0) {
      throw ArgumentError.value(denominator, 'denominator', 'tidak boleh nol');
    }
    return _fromBig(BigInt.from(numerator), BigInt.from(denominator));
  }

  /// Mengurai teks desimal persis menjadi pecahan. Pemisah desimal boleh koma atau titik
  /// ("2,5" dan "2.5" keduanya 5/2). Pemisah ribuan tidak didukung.
  ///
  /// Melempar [InvalidNumberFormat] untuk "abc", "2,,5", "", "1e3", dan sejenisnya.
  factory Fraction.parse(String input) {
    final match = _decimalPattern.firstMatch(input.trim());
    if (match == null) throw InvalidNumberFormat(input);

    final negative = match.group(1) == '-';
    final whole = match.group(2) ?? '0';
    final fraction = match.group(3) ?? match.group(4) ?? '';
    if (whole.length + fraction.length > _maxDigits ||
        fraction.length > _maxFractionDigits) {
      throw InvalidNumberFormat(input);
    }

    var numerator = BigInt.parse('$whole$fraction');
    if (negative) numerator = -numerator;
    return _fromBig(numerator, BigInt.from(10).pow(fraction.length));
  }

  /// Seperti [Fraction.parse] tetapi mengembalikan `null` bila format salah.
  static Fraction? tryParse(String input) {
    try {
      return Fraction.parse(input);
    } on InvalidNumberFormat {
      return null;
    }
  }

  static final RegExp _decimalPattern =
      RegExp(r'^(-?)(?:(\d+)(?:[.,](\d+))?|[.,](\d+))$');
  static const int _maxDigits = 15;
  static const int _maxFractionDigits = 6;

  static const Fraction zero = Fraction._(0, 1);
  static const Fraction one = Fraction._(1, 1);

  static Fraction _fromBig(BigInt numerator, BigInt denominator) {
    var n = numerator;
    var d = denominator;
    if (d.isNegative) {
      n = -n;
      d = -d;
    }
    final divisor = n.gcd(d);
    if (divisor > BigInt.one) {
      n = n ~/ divisor;
      d = d ~/ divisor;
    }
    return Fraction._(bigToInt(n), bigToInt(d));
  }

  final int numerator;
  final int denominator;

  bool get isZero => numerator == 0;
  bool get isPositive => numerator > 0;
  bool get isNegative => numerator < 0;

  Fraction operator +(Fraction other) => _fromBig(
        BigInt.from(numerator) * BigInt.from(other.denominator) +
            BigInt.from(other.numerator) * BigInt.from(denominator),
        BigInt.from(denominator) * BigInt.from(other.denominator),
      );

  Fraction operator -(Fraction other) => _fromBig(
        BigInt.from(numerator) * BigInt.from(other.denominator) -
            BigInt.from(other.numerator) * BigInt.from(denominator),
        BigInt.from(denominator) * BigInt.from(other.denominator),
      );

  Fraction operator *(Fraction other) => _fromBig(
        BigInt.from(numerator) * BigInt.from(other.numerator),
        BigInt.from(denominator) * BigInt.from(other.denominator),
      );

  /// Pembagian pecahan. Melempar [ArgumentError] bila [other] nol.
  Fraction operator /(Fraction other) {
    if (other.isZero) {
      throw ArgumentError.value(other, 'other', 'pembagi tidak boleh nol');
    }
    return _fromBig(
      BigInt.from(numerator) * BigInt.from(other.denominator),
      BigInt.from(denominator) * BigInt.from(other.numerator),
    );
  }

  Fraction timesInt(int factor) => this * Fraction(factor);

  /// Pembulatan HALF_UP ke bilangan bulat terdekat (0,5 menjauhi nol).
  int roundHalfUp() => divRoundHalfUp(numerator, denominator);

  /// Pembulatan ke bawah.
  int floor() {
    if (numerator >= 0) return numerator ~/ denominator;
    return -((-numerator + denominator - 1) ~/ denominator);
  }

  /// Teks desimal dengan paling banyak [maxFractionDigits] digit pecahan (HALF_UP);
  /// nol di belakang koma dibuang. Murni aritmetika bilangan bulat.
  String toDecimalString({
    int maxFractionDigits = 3,
    String decimalSeparator = ',',
  }) {
    final scale = BigInt.from(10).pow(maxFractionDigits);
    final scaled = divRoundHalfUpBig(
      BigInt.from(numerator).abs() * scale,
      BigInt.from(denominator),
    );
    final whole = scaled ~/ scale;
    final fraction = (scaled % scale)
        .toString()
        .padLeft(maxFractionDigits, '0')
        .replaceFirst(RegExp(r'0+$'), '');
    final sign = numerator < 0 && scaled != BigInt.zero ? '-' : '';
    if (maxFractionDigits == 0 || fraction.isEmpty) return '$sign$whole';
    return '$sign$whole$decimalSeparator$fraction';
  }

  @override
  int compareTo(Fraction other) => (BigInt.from(numerator) *
          BigInt.from(other.denominator))
      .compareTo(BigInt.from(other.numerator) * BigInt.from(denominator));

  @override
  bool operator ==(Object other) =>
      other is Fraction &&
      other.numerator == numerator &&
      other.denominator == denominator;

  @override
  int get hashCode => Object.hash(numerator, denominator);

  @override
  String toString() => '$numerator/$denominator';
}
