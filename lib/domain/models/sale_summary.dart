import 'package:arn_kasir/domain/sales/payment.dart';
import 'package:arn_kasir/domain/sales/sale_record.dart';

/// Ringkasan satu transaksi untuk daftar (tanpa memuat seluruh baris).
final class SaleSummary {
  const SaleSummary({
    required this.id,
    required this.number,
    required this.time,
    required this.total,
    required this.method,
    required this.status,
    required this.itemCount,
  });

  final int id;
  final String number;
  final DateTime time;
  final int total;
  final PaymentMethod method;
  final SaleStatus status;

  /// Jumlah porsi seluruh baris.
  final int itemCount;
}
