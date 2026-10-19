import 'package:arn_kasir/domain/core/failures.dart';

enum PaymentMethod {
  cash('tunai'),
  nonCash('non_tunai');

  const PaymentMethod(this.dbValue);
  final String dbValue;

  static PaymentMethod fromDb(String value) {
    for (final method in values) {
      if (method.dbValue == value) return method;
    }
    throw ArgumentError.value(value, 'value', 'Metode bayar tidak dikenal');
  }
}

final class PaymentResult {
  const PaymentResult({required this.paid, required this.change});

  final int paid;
  final int change;
}

/// Menghitung pembayaran. Tunai wajib dibayar paling sedikit sebesar [total] (TC E10)
/// dan kembaliannya dihitung; non tunai hanya dicatat sebagai metode (dibayar = total).
/// [paid] yang `null` pada tunai dianggap uang pas.
PaymentResult settlePayment({
  required PaymentMethod method,
  required int total,
  int? paid,
}) {
  switch (method) {
    case PaymentMethod.nonCash:
      return PaymentResult(paid: total, change: 0);
    case PaymentMethod.cash:
      final amount = paid ?? total;
      if (amount < total) {
        throw InsufficientPayment(total: total, paid: amount);
      }
      return PaymentResult(paid: amount, change: amount - total);
  }
}
