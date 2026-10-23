import 'package:flutter_riverpod/flutter_riverpod.dart';

import 'package:arn_kasir/app/providers.dart';
import 'package:arn_kasir/domain/menu/availability.dart';
import 'package:arn_kasir/domain/menu/menu_item.dart';

/// Kategori yang dipilih pada chip; `null` berarti "Semua".
final selectedCategoryProvider = NotifierProvider<SelectedCategory, int?>(
  SelectedCategory.new,
);

class SelectedCategory extends Notifier<int?> {
  @override
  int? build() => null;

  void select(int? categoryId) => state = categoryId;
}

/// Teks pencarian menu pada top bar.
final searchQueryProvider = NotifierProvider<SearchQuery, String>(SearchQuery.new);

class SearchQuery extends Notifier<String> {
  @override
  String build() => '';

  void set(String value) => state = value;
}

/// Satu kartu menu di Halaman Utama beserta status ketersediaannya.
final class MenuTile {
  const MenuTile({
    required this.menu,
    required this.sellable,
    required this.portions,
  });

  final MenuItem menu;

  /// Apakah satu porsi dapat ditambahkan pada kebijakan stok saat ini.
  final bool sellable;

  /// Sisa porsi dari stok bahan; `null` untuk menu tanpa resep.
  final int? portions;
}

/// Menu yang tampil di Halaman Utama: aktif, sudah berkategori (PRD 6.2 C), cocok dengan
/// kategori terpilih dan pencarian (tanpa membedakan huruf besar kecil).
final homeTilesProvider = Provider<List<MenuTile>>((ref) {
  final menus = ref.watch(menusProvider).value ?? const <MenuItem>[];
  final catalog = ref.watch(stockCatalogProvider);
  final policy = ref.watch(settingsProvider.select((s) => s.stockPolicy));
  final category = ref.watch(selectedCategoryProvider);
  final query = ref.watch(searchQueryProvider).trim().toLowerCase();

  return <MenuTile>[
    for (final menu in menus)
      if (menu.active &&
          menu.categoryId != null &&
          (category == null || menu.categoryId == category) &&
          (query.isEmpty || menu.name.toLowerCase().contains(query)))
        MenuTile(
          menu: menu,
          sellable: isSellable(menu, catalog, policy),
          portions: availablePortions(menu, catalog),
        ),
  ];
});
