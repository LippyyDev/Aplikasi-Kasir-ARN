import 'package:arn_kasir/domain/core/failures.dart';
import 'package:arn_kasir/domain/core/rounding.dart';
import 'package:arn_kasir/domain/menu/availability.dart';
import 'package:arn_kasir/domain/menu/menu_item.dart';
import 'package:arn_kasir/domain/menu/stock_catalog.dart';

/// Diskon tingkat transaksi: nominal atau persen (PRD 6.2 D).
sealed class Discount {
  const Discount();

  /// Nominal diskon untuk [subtotal]. Melempar [InvalidDiscount] bila di luar batas.
  int resolve(int subtotal);
}

final class NoDiscount extends Discount {
  const NoDiscount();

  @override
  int resolve(int subtotal) => 0;
}

final class AmountDiscount extends Discount {
  const AmountDiscount(this.amount);
  final int amount;

  @override
  int resolve(int subtotal) {
    if (amount < 0) throw const InvalidDiscount(InvalidDiscountReason.negative);
    if (amount > subtotal) {
      throw const InvalidDiscount(InvalidDiscountReason.exceedsSubtotal);
    }
    return amount;
  }
}

/// Diskon persen bulat 0 sampai 100, dibulatkan HALF_UP ke Rupiah.
final class PercentDiscount extends Discount {
  const PercentDiscount(this.percent);
  final int percent;

  @override
  int resolve(int subtotal) {
    if (percent < 0 || percent > 100) {
      throw const InvalidDiscount(InvalidDiscountReason.percentOutOfRange);
    }
    return divRoundHalfUp(subtotal * percent, 100);
  }
}

/// Satu baris keranjang. Nama dan harga disalin dari menu saat ditambahkan.
final class CartLine {
  const CartLine({
    required this.menuId,
    required this.menuName,
    required this.unitPrice,
    required this.quantity,
    this.note,
  });

  final int menuId;
  final String menuName;
  final int unitPrice;
  final int quantity;
  final String? note;

  int get lineTotal => unitPrice * quantity;

  CartLine copyWith({int? quantity, String? note, bool clearNote = false}) =>
      CartLine(
        menuId: menuId,
        menuName: menuName,
        unitPrice: unitPrice,
        quantity: quantity ?? this.quantity,
        note: clearNote ? null : (note ?? this.note),
      );
}

final class CartTotals {
  const CartTotals({
    required this.subtotal,
    required this.discount,
    required this.total,
  });

  final int subtotal;
  final int discount;
  final int total;
}

/// Keranjang belanja yang tidak dapat diubah. Setiap operasi mengembalikan keranjang baru.
final class Cart {
  const Cart({
    this.lines = const <CartLine>[],
    this.discount = const NoDiscount(),
  });

  final List<CartLine> lines;
  final Discount discount;

  bool get isEmpty => lines.isEmpty;

  int get subtotal => lines.fold(0, (sum, line) => sum + line.lineTotal);
  int get itemCount => lines.fold(0, (sum, line) => sum + line.quantity);

  int quantityOf(int menuId) {
    for (final line in lines) {
      if (line.menuId == menuId) return line.quantity;
    }
    return 0;
  }

  CartTotals totals() {
    final sub = subtotal;
    final disc = discount.resolve(sub);
    return CartTotals(subtotal: sub, discount: disc, total: sub - disc);
  }

  /// Menambah satu porsi [menu]. Pada kebijakan blokir, ditolak dengan
  /// [InsufficientStock] bila gabungan seluruh isi keranjang melebihi stok bahan
  /// (TC D2, D5, D6); pada kebijakan peringatkan selalu diizinkan.
  Cart add(MenuItem menu, StockCatalog catalog, StockPolicy policy) =>
      setQuantity(menu, quantityOf(menu.id) + 1, catalog, policy);

  /// Mengatur kuantitas [menu]; nol atau kurang menghapus baris. Pemeriksaan stok hanya
  /// dilakukan saat kuantitas naik sehingga keranjang selalu dapat dikurangi.
  Cart setQuantity(
    MenuItem menu,
    int quantity,
    StockCatalog catalog,
    StockPolicy policy,
  ) {
    if (quantity <= 0) return remove(menu.id);
    final current = quantityOf(menu.id);
    if (quantity > current && !menu.active) throw MenuUnavailable(menu.id);

    final next = <CartLine>[];
    var replaced = false;
    for (final line in lines) {
      if (line.menuId == menu.id) {
        next.add(line.copyWith(quantity: quantity));
        replaced = true;
      } else {
        next.add(line);
      }
    }
    if (!replaced) {
      next.add(
        CartLine(
          menuId: menu.id,
          menuName: menu.name,
          unitPrice: menu.price,
          quantity: quantity,
        ),
      );
    }

    if (policy == StockPolicy.block && quantity > current) {
      final withMenu = StockCatalog(
        menus: <int, MenuItem>{...catalog.menus, menu.id: menu},
        ingredients: catalog.ingredients,
      );
      final shortages = findShortages(
        next.map((l) => DemandLine(menuId: l.menuId, quantity: l.quantity)),
        withMenu,
      );
      if (shortages.isNotEmpty) throw InsufficientStock(shortages);
    }
    return Cart(lines: List.unmodifiable(next), discount: discount);
  }

  Cart remove(int menuId) => Cart(
        lines: List.unmodifiable(lines.where((l) => l.menuId != menuId)),
        discount: discount,
      );

  Cart withNote(int menuId, String? note) => Cart(
        lines: List.unmodifiable(
          lines.map((l) {
            if (l.menuId != menuId) return l;
            final trimmed = note?.trim();
            return trimmed == null || trimmed.isEmpty
                ? l.copyWith(clearNote: true)
                : l.copyWith(note: trimmed);
          }),
        ),
        discount: discount,
      );

  Cart withDiscount(Discount value) => Cart(lines: lines, discount: value);

  Cart cleared() => const Cart();
}
