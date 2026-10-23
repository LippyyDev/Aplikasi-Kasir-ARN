import 'package:flutter_riverpod/flutter_riverpod.dart';

import 'package:arn_kasir/app/providers.dart';
import 'package:arn_kasir/domain/core/fraction.dart';
import 'package:arn_kasir/domain/inventory/inventory_store.dart';
import 'package:arn_kasir/domain/inventory/stock_change.dart';
import 'package:arn_kasir/domain/sales/payment.dart';
import 'package:arn_kasir/domain/sales/sale_store.dart';
import 'package:arn_kasir/ui/cart/cart_controller.dart';

/// Operasi tulis tingkat aplikasi: menyatukan use case domain, keranjang, dan notifikasi.
/// Widget memanggil kelas ini, bukan repositori atau database langsung.
final salesActionsProvider = Provider<SalesActions>(SalesActions.new);

final class SalesActions {
  SalesActions(this._ref);

  final Ref _ref;

  /// Membayar keranjang saat ini. Sukses mengosongkan keranjang; gagal membiarkannya
  /// utuh dan melempar galat domain (TC E8: tidak ada yang tersimpan sebagian).
  Future<CheckoutResult> checkout({
    required PaymentMethod method,
    int? paid,
    String? note,
  }) async {
    final cart = _ref.read(cartProvider);
    final settings = _ref.read(settingsProvider);

    final result = await _ref.read(checkoutUseCaseProvider)(
      CheckoutRequest(
        lines: cart.lines,
        discount: cart.discount,
        method: method,
        paid: paid,
        policy: settings.stockPolicy,
        note: note,
      ),
    );
    _ref.read(cartProvider.notifier).clear();
    await _notifySafely(result.alerts);
    return result;
  }

  Future<void> voidSale(int saleId) => _ref.read(voidSaleUseCaseProvider)(saleId);

  Future<void> _notifySafely(Iterable<StockAlert> alerts) async {
    try {
      await _ref.read(notificationCenterProvider).notifyStockAlerts(alerts);
    } on Object {
      // Penjualan sudah tersimpan; kegagalan notifikasi tidak boleh membatalkannya.
    }
  }
}

/// Restok dan opname.
final stockActionsProvider = Provider<StockActions>(StockActions.new);

final class StockActions {
  StockActions(this._ref);

  final Ref _ref;

  Future<RestockResult> restock({
    required int ingredientId,
    required String unitName,
    required Fraction amount,
    required int totalPrice,
    String? note,
  }) {
    return _ref.read(restockUseCaseProvider)(
      ingredientId: ingredientId,
      unitName: unitName,
      amount: amount,
      totalPrice: totalPrice,
      note: note,
    );
  }

  Future<AdjustStockResult> adjust({
    required int ingredientId,
    required String unitName,
    required Fraction physicalAmount,
    String? reason,
  }) async {
    final settings = _ref.read(settingsProvider);
    final result = await _ref.read(adjustStockUseCaseProvider)(
      ingredientId: ingredientId,
      unitName: unitName,
      physicalAmount: physicalAmount,
      shrinkageEnabled: settings.shrinkageEnabled,
      reason: reason,
    );
    final alert = result.alert;
    if (alert != null) {
      try {
        await _ref
            .read(notificationCenterProvider)
            .notifyStockAlerts(<StockAlert>[alert]);
      } on Object {
        // Lihat catatan pada SalesActions.
      }
    }
    return result;
  }
}
