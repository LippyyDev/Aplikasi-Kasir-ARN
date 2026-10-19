import 'package:arn_kasir/domain/core/failures.dart';
import 'package:arn_kasir/domain/menu/menu_item.dart';
import 'package:arn_kasir/domain/menu/stock_catalog.dart';

/// Kebijakan saat stok bahan tidak cukup (PRD 7.3). Dapat dipilih di pengaturan.
enum StockPolicy {
  /// Menu tidak dapat dijual (bawaan).
  block('blokir'),

  /// Tetap dapat dijual; stok dapat menjadi negatif dan ditandai untuk opname.
  warn('peringatkan');

  const StockPolicy(this.dbValue);
  final String dbValue;

  static StockPolicy fromDb(String? value) {
    for (final policy in values) {
      if (policy.dbValue == value) return policy;
    }
    return StockPolicy.block;
  }
}

/// Permintaan sejumlah porsi sebuah menu (satu baris keranjang).
final class DemandLine {
  const DemandLine({required this.menuId, required this.quantity});

  final int menuId;
  final int quantity;
}

/// Porsi menu yang masih dapat dibuat: nilai minimum `floor(stok / jumlah resep)` untuk
/// semua bahan resep (TC D1). `null` berarti tidak terbatas (menu tanpa resep).
/// Stok nol, negatif, atau bahan yang tidak ditemukan dihitung 0 porsi.
int? availablePortions(MenuItem menu, StockCatalog catalog) {
  if (!menu.hasRecipe) return null;
  int? minimum;
  for (final line in menu.recipe) {
    final stock = catalog.ingredients[line.ingredientId]?.stock.quantity ?? 0;
    final portions = stock <= 0 ? 0 : stock ~/ line.quantity;
    if (minimum == null || portions < minimum) minimum = portions;
  }
  return minimum;
}

/// Apakah sebuah porsi menu dapat ditambahkan ke keranjang kosong pada kebijakan ini.
bool isSellable(MenuItem menu, StockCatalog catalog, StockPolicy policy) {
  if (!menu.active) return false;
  if (policy == StockPolicy.warn) return true;
  final portions = availablePortions(menu, catalog);
  return portions == null || portions > 0;
}

/// Kekurangan stok untuk seluruh permintaan sekaligus.
///
/// Pemakaian bahan yang sama oleh beberapa menu dijumlahkan per bahan (TC D6), jadi
/// keranjang bisa ditolak walau tiap item sendirian cukup. Hasil kosong berarti cukup.
List<StockShortage> findShortages(
  Iterable<DemandLine> demand,
  StockCatalog catalog,
) {
  final required = <int, int>{};
  for (final line in demand) {
    final menu = catalog.menus[line.menuId];
    if (menu == null) throw MenuUnavailable(line.menuId);
    for (final recipeLine in menu.recipe) {
      required.update(
        recipeLine.ingredientId,
        (value) => value + recipeLine.quantity * line.quantity,
        ifAbsent: () => recipeLine.quantity * line.quantity,
      );
    }
  }

  final shortages = <StockShortage>[];
  required.forEach((ingredientId, needed) {
    final ingredient = catalog.ingredients[ingredientId];
    final available = ingredient?.stock.quantity ?? 0;
    if (needed > available) {
      shortages.add(
        StockShortage(
          ingredientId: ingredientId,
          ingredientName: ingredient?.name ?? '#$ingredientId',
          required: needed,
          available: available,
        ),
      );
    }
  });
  return shortages;
}
