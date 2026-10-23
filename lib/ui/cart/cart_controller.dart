import 'package:flutter_riverpod/flutter_riverpod.dart';

import 'package:arn_kasir/app/providers.dart';
import 'package:arn_kasir/domain/core/failures.dart';
import 'package:arn_kasir/domain/menu/availability.dart';
import 'package:arn_kasir/domain/menu/menu_item.dart';
import 'package:arn_kasir/domain/menu/stock_catalog.dart';
import 'package:arn_kasir/domain/sales/cart.dart';

/// Keranjang yang sedang dibuat kasir. Hanya hidup di memori: keranjang yang belum
/// dibayar tidak perlu bertahan setelah aplikasi ditutup.
final cartProvider = NotifierProvider<CartController, Cart>(CartController.new);

class CartController extends Notifier<Cart> {
  @override
  Cart build() => const Cart();

  /// Melempar `InsufficientStock` atau `MenuUnavailable`; pemanggil menampilkan pesannya.
  void add(MenuItem menu) {
    state = _normalized(state.add(menu, _catalog, _policy));
  }

  void setQuantity(MenuItem menu, int quantity) {
    state = _normalized(state.setQuantity(menu, quantity, _catalog, _policy));
  }

  void remove(int menuId) => state = _normalized(state.remove(menuId));

  void setNote(int menuId, String? note) => state = state.withNote(menuId, note);

  /// Melempar `InvalidDiscount` bila diskon di luar batas untuk subtotal saat ini.
  void setDiscount(Discount discount) {
    final next = state.withDiscount(discount);
    next.totals();
    state = next;
  }

  void clear() => state = state.cleared();

  /// Diskon yang tidak lagi sah setelah isi keranjang berubah (misalnya nominal melebihi
  /// subtotal baru) dibuang, supaya total keranjang selalu dapat dihitung.
  Cart _normalized(Cart cart) {
    try {
      cart.totals();
      return cart;
    } on InvalidDiscount {
      return cart.withDiscount(const NoDiscount());
    }
  }

  StockCatalog get _catalog => ref.read(stockCatalogProvider);
  StockPolicy get _policy => ref.read(settingsProvider).stockPolicy;
}
