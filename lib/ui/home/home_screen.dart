import 'package:flutter/material.dart';
import 'package:flutter_riverpod/flutter_riverpod.dart';
import 'package:go_router/go_router.dart';

import 'package:arn_kasir/app/providers.dart';
import 'package:arn_kasir/app/routes.dart';
import 'package:arn_kasir/ui/cart/cart_bar.dart';
import 'package:arn_kasir/ui/home/home_providers.dart';
import 'package:arn_kasir/ui/home/menu_card.dart';
import 'package:arn_kasir/ui/shared/l10n_context.dart';
import 'package:arn_kasir/ui/shared/widgets.dart';
import 'package:arn_kasir/ui/shell/app_drawer.dart';

/// Halaman Utama (PRD 6.2): top bar, chip kategori, grid menu, dan bilah keranjang.
class HomeScreen extends ConsumerWidget {
  const HomeScreen({super.key});

  @override
  Widget build(BuildContext context, WidgetRef ref) {
    final l10n = context.l10n;
    final categories = ref.watch(categoriesProvider);
    final menus = ref.watch(menusProvider);

    return Scaffold(
      drawer: const AppDrawer(),
      appBar: AppBar(
        titleSpacing: 0,
        leading: Builder(
          builder: (context) => IconButton(
            icon: const Icon(Icons.menu),
            tooltip: l10n.homeOpenMenu,
            onPressed: () => Scaffold.of(context).openDrawer(),
          ),
        ),
        title: const _SearchField(),
        actions: <Widget>[
          IconButton(
            icon: const Icon(Icons.account_circle_outlined),
            tooltip: l10n.homeBusinessProfile,
            onPressed: () => context.push(AppRoutes.profile),
          ),
        ],
      ),
      body: AsyncBody(
        value: categories,
        builder: (categoryList) {
          if (categoryList.isEmpty) {
            return EmptyState(
              icon: Icons.category_outlined,
              title: l10n.homeNoCategoryTitle,
              message: l10n.homeNoCategoryMessage,
              action: FilledButton.icon(
                icon: const Icon(Icons.tune),
                label: Text(l10n.drawerMenuSetup),
                onPressed: () => context.push(AppRoutes.menuSetup),
              ),
            );
          }
          return Column(
            children: <Widget>[
              _CategoryChips(
                categories: <({int? id, String name})>[
                  (id: null, name: l10n.homeAllCategories),
                  for (final c in categoryList) (id: c.id, name: c.name),
                ],
              ),
              Expanded(
                child: AsyncBody(
                  value: menus,
                  builder: (_) => const _MenuGrid(),
                ),
              ),
            ],
          );
        },
      ),
      bottomNavigationBar: const CartBar(),
    );
  }
}

class _SearchField extends ConsumerWidget {
  const _SearchField();

  @override
  Widget build(BuildContext context, WidgetRef ref) {
    return SearchBar(
      hintText: context.l10n.homeSearchHint,
      leading: const Icon(Icons.search),
      elevation: const WidgetStatePropertyAll<double>(0),
      padding: const WidgetStatePropertyAll<EdgeInsets>(
        EdgeInsets.symmetric(horizontal: 12),
      ),
      constraints: const BoxConstraints(minHeight: 44, maxHeight: 44),
      onChanged: ref.read(searchQueryProvider.notifier).set,
    );
  }
}

class _CategoryChips extends ConsumerWidget {
  const _CategoryChips({required this.categories});

  final List<({int? id, String name})> categories;

  @override
  Widget build(BuildContext context, WidgetRef ref) {
    final selected = ref.watch(selectedCategoryProvider);
    return SizedBox(
      height: 56,
      child: ListView.separated(
        scrollDirection: Axis.horizontal,
        padding: const EdgeInsets.symmetric(horizontal: 12, vertical: 8),
        itemCount: categories.length,
        separatorBuilder: (_, __) => const SizedBox(width: 8),
        itemBuilder: (context, index) {
          final category = categories[index];
          return ChoiceChip(
            label: Text(category.name),
            selected: selected == category.id,
            onSelected: (_) =>
                ref.read(selectedCategoryProvider.notifier).select(category.id),
          );
        },
      ),
    );
  }
}

class _MenuGrid extends ConsumerWidget {
  const _MenuGrid();

  @override
  Widget build(BuildContext context, WidgetRef ref) {
    final l10n = context.l10n;
    final tiles = ref.watch(homeTilesProvider);
    final hasQuery = ref.watch(searchQueryProvider).trim().isNotEmpty;

    if (tiles.isEmpty) {
      return EmptyState(
        icon: hasQuery ? Icons.search_off : Icons.restaurant_menu,
        title: hasQuery ? l10n.homeNoSearchResult : l10n.homeNoMenuTitle,
        message: hasQuery ? null : l10n.homeNoMenuMessage,
        action: hasQuery
            ? null
            : FilledButton.icon(
                icon: const Icon(Icons.add),
                label: Text(l10n.drawerMenuSetup),
                onPressed: () => context.push(AppRoutes.menuSetup),
              ),
      );
    }

    return GridView.builder(
      padding: const EdgeInsets.fromLTRB(12, 4, 12, 16),
      gridDelegate: const SliverGridDelegateWithMaxCrossAxisExtent(
        maxCrossAxisExtent: 220,
        mainAxisSpacing: 12,
        crossAxisSpacing: 12,
        childAspectRatio: 0.82,
      ),
      itemCount: tiles.length,
      itemBuilder: (context, index) => MenuCard(tile: tiles[index]),
    );
  }
}
