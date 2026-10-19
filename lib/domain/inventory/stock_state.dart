import 'package:arn_kasir/domain/core/rounding.dart';

/// Keadaan stok satu bahan: jumlah (satuan dasar) dan nilai (Rupiah), keduanya `int`.
///
/// Metode biaya adalah rata-rata tertimbang bergerak: rata-rata saat ini adalah
/// `value / quantity`. Nilai dan jumlah boleh negatif bila penjualan melebihi stok
/// (kebijakan "peringatkan"); nilai tetap mengikuti jumlah pada rata-rata terakhir.
final class StockState {
  const StockState(this.quantity, this.value);

  static const StockState empty = StockState(0, 0);

  final int quantity;
  final int value;

  @override
  bool operator ==(Object other) =>
      other is StockState && other.quantity == quantity && other.value == value;

  @override
  int get hashCode => Object.hash(quantity, value);

  @override
  String toString() => 'StockState($quantity, Rp$value)';
}

final class ConsumeResult {
  const ConsumeResult({required this.cost, required this.after});

  /// Biaya pemakaian yang dibebankan ke HPP.
  final int cost;
  final StockState after;
}

final class AdjustResult {
  const AdjustResult({
    required this.quantityDelta,
    required this.valueDelta,
    required this.after,
  });

  /// Perubahan jumlah (fisik dikurangi sistem).
  final int quantityDelta;

  /// Perubahan nilai pada rata-rata saat ini (negatif untuk kehilangan).
  final int valueDelta;
  final StockState after;
}

/// Operasi rata-rata tertimbang bergerak (PRD 7.4, CLAUDE.md aturan 6).
abstract final class StockLedger {
  /// Restok: jumlah dan nilai sama-sama ditambahkan.
  static StockState restock(
    StockState state, {
    required int quantity,
    required int value,
  }) {
    if (quantity <= 0) {
      throw ArgumentError.value(quantity, 'quantity', 'harus lebih dari 0');
    }
    if (value < 0) {
      throw ArgumentError.value(value, 'value', 'tidak boleh negatif');
    }
    return StockState(state.quantity + quantity, state.value + value);
  }

  /// Pemakaian [quantity] satuan dasar.
  ///
  /// Biaya = `round_half_up(quantity * value / stok)`; nilai baru adalah nilai lama
  /// dikurangi biaya itu (bukan dihitung ulang dari rata-rata). Dengan begitu jumlah
  /// seluruh biaya persis sama dengan nilai awal ketika stok habis, tanpa selisih
  /// pembulatan (TC G6). Bila stok tepat nol rata-rata tidak diketahui sehingga biaya 0.
  static ConsumeResult consume(StockState state, int quantity) {
    if (quantity <= 0) {
      throw ArgumentError.value(quantity, 'quantity', 'harus lebih dari 0');
    }
    final cost = state.quantity == 0
        ? 0
        : mulDivRoundHalfUp(quantity, state.value, state.quantity);
    return ConsumeResult(
      cost: cost,
      after: StockState(state.quantity - quantity, state.value - cost),
    );
  }

  /// Kebalikan [consume] untuk pembatalan transaksi: mengembalikan jumlah dan biaya
  /// persis sebagaimana tercatat pada snapshot.
  static StockState reverseConsume(
    StockState state, {
    required int quantity,
    required int cost,
  }) =>
      StockState(state.quantity + quantity, state.value + cost);

  /// Penyesuaian stok ke hasil hitung fisik [physicalQuantity] (opname).
  ///
  /// Selisih dinilai pada rata-rata saat ini sehingga rata-rata tidak berubah. Saat
  /// stok fisik nol, seluruh nilai tersisa ikut habis.
  static AdjustResult adjustTo(StockState state, int physicalQuantity) {
    final delta = physicalQuantity - state.quantity;
    if (delta == 0) {
      return AdjustResult(quantityDelta: 0, valueDelta: 0, after: state);
    }
    final valueDelta = state.quantity == 0
        ? 0
        : mulDivRoundHalfUp(delta, state.value, state.quantity);
    return AdjustResult(
      quantityDelta: delta,
      valueDelta: valueDelta,
      after: StockState(physicalQuantity, state.value + valueDelta),
    );
  }
}
