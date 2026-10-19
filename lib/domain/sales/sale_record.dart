import 'package:arn_kasir/domain/sales/payment.dart';

enum SaleStatus {
  completed('selesai'),
  voided('batal');

  const SaleStatus(this.dbValue);
  final String dbValue;

  static SaleStatus fromDb(String value) {
    for (final status in values) {
      if (status.dbValue == value) return status;
    }
    throw ArgumentError.value(value, 'value', 'Status transaksi tidak dikenal');
  }
}

/// Snapshot pemakaian satu bahan oleh satu baris transaksi (tabel `transaksi_bahan`).
/// Dasar pembatalan: dibalik persis seperti tercatat, bukan dari resep terbaru.
final class UsageRecord {
  const UsageRecord({
    required this.ingredientId,
    required this.baseQuantity,
    required this.cost,
  });

  final int ingredientId;
  final int baseQuantity;
  final int cost;
}

/// Baris transaksi dengan snapshot nama, harga, HPP, dan pemakaian bahan.
final class SaleItemRecord {
  const SaleItemRecord({
    required this.menuId,
    required this.menuName,
    required this.unitPrice,
    required this.quantity,
    required this.unitCost,
    required this.usages,
    this.note,
  });

  final int menuId;
  final String menuName;
  final int unitPrice;
  final int quantity;

  /// HPP per porsi (dibulatkan HALF_UP dari biaya baris dibagi kuantitas). Laporan
  /// memakai jumlah biaya pada [usages], yang tepat, bukan [unitCost] x [quantity].
  final int unitCost;
  final List<UsageRecord> usages;
  final String? note;

  int get lineTotal => unitPrice * quantity;

  /// Total biaya bahan baris ini.
  int get totalCost => usages.fold(0, (sum, u) => sum + u.cost);
}

/// Transaksi penjualan lengkap (tabel `transaksi`, `transaksi_item`, `transaksi_bahan`).
final class SaleRecord {
  const SaleRecord({
    required this.number,
    required this.time,
    required this.subtotal,
    required this.discount,
    required this.total,
    required this.method,
    required this.paid,
    required this.change,
    required this.status,
    required this.items,
    this.id,
    this.note,
  });

  /// `null` sampai transaksi disimpan.
  final int? id;
  final String number;
  final DateTime time;
  final int subtotal;
  final int discount;
  final int total;
  final PaymentMethod method;
  final int paid;
  final int change;
  final SaleStatus status;
  final String? note;
  final List<SaleItemRecord> items;

  /// Jumlah seluruh biaya bahan transaksi (HPP).
  int get totalCost => items.fold(0, (sum, item) => sum + item.totalCost);

  int get grossProfit => total - totalCost;

  SaleRecord copyWith({int? id, SaleStatus? status}) => SaleRecord(
        id: id ?? this.id,
        number: number,
        time: time,
        subtotal: subtotal,
        discount: discount,
        total: total,
        method: method,
        paid: paid,
        change: change,
        status: status ?? this.status,
        note: note,
        items: items,
      );
}
