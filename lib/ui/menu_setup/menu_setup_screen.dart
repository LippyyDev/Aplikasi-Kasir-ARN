import 'package:flutter/material.dart';
import 'package:flutter_riverpod/flutter_riverpod.dart';
import 'package:go_router/go_router.dart';

import 'package:arn_kasir/app/providers.dart';
import 'package:arn_kasir/app/routes.dart';
import 'package:arn_kasir/data/repositories/menu_repository.dart';
import 'package:arn_kasir/domain/inventory/ingredient.dart';
import 'package:arn_kasir/domain/menu/menu_costing.dart';
import 'package:arn_kasir/domain/menu/menu_item.dart';
import 'package:arn_kasir/domain/models/menu_category.dart';
import 'package:arn_kasir/ui/home/menu_card.dart';
import 'package:arn_kasir/ui/shared/l10n_context.dart';
import 'package:arn_kasir/ui/shared/shared_providers.dart';
import 'package:arn_kasir/ui/shared/widgets.dart';

/// Penyetelan Menu (PRD 6.3.4): tab Kategori dan tab Menu.
class MenuSetupScreen extends ConsumerWidget {
  const MenuSetupScreen({super.key});

  @override
  Widget build(BuildContext context, WidgetRef ref) {
    final l10n = context.l10n;
    return DefaultTabController(
      length: 2,
      child: Scaffold(
        appBar: AppBar(
          title: Text(l10n.drawerMenuSetup),
          bottom: TabBar(
            tabs: <Widget>[
              Tab(text: l10n.setupTabCategories),
              Tab(text: l10n.setupTabMenus),
            ],
          ),
        ),
        body: const TabBarView(
          children: <Widget>[_CategoriesTab(), _MenusTab()],
        ),
      ),
    );
  }
}

// --- Kategori -------------------------------------------------------------------

class _CategoriesTab extends ConsumerWidget {
  const _CategoriesTab();

  @override
  Widget build(BuildContext context, WidgetRef ref) {
    final l10n = context.l10n;
    final categories = ref.watch(categoriesProvider);
    final repository = ref.read(categoryRepositoryProvider);

    return Scaffold(
      body: AsyncBody(
        value: categories,
        builder: (list) {
          if (list.isEmpty) {
            return EmptyState(
              icon: Icons.category_outlined,
              title: l10n.categoryEmptyTitle,
              message: l10n.categoryEmptyMessage,
            );
          }
          return ReorderableListView.builder(
            padding: const EdgeInsets.only(bottom: 96),
            itemCount: list.length,
            onReorder: (oldIndex, newIndex) {
              final ids = <int>[for (final c in list) c.id];
              if (newIndex > oldIndex) newIndex -= 1;
              ids.insert(newIndex, ids.removeAt(oldIndex));
              repository.reorder(ids);
            },
            itemBuilder: (context, index) {
              final category = list[index];
              return ListTile(
                key: ValueKey<int>(category.id),
                leading: const Icon(Icons.drag_indicator),
                title: Text(category.name),
                trailing: PopupMenuButton<String>(
                  onSelected: (value) => value == 'rename'
                      ? _rename(context, ref, category)
                      : _delete(context, ref, category, list),
                  itemBuilder: (context) => <PopupMenuEntry<String>>[
                    PopupMenuItem<String>(value: 'rename', child: Text(l10n.actionRename)),
                    PopupMenuItem<String>(value: 'delete', child: Text(l10n.actionDelete)),
                  ],
                ),
              );
            },
          );
        },
      ),
      floatingActionButton: FloatingActionButton.extended(
        icon: const Icon(Icons.add),
        label: Text(l10n.categoryAdd),
        onPressed: () => _add(context, ref),
      ),
    );
  }

  Future<String?> _askName(BuildContext context, {String? initial}) {
    // Pengendali sengaja tidak di-dispose di sini: dialog masih beranimasi keluar saat
    // Future selesai, dan dispose lebih awal memicu galat "used after being disposed".
    final controller = TextEditingController(text: initial);
    return showDialog<String>(
      context: context,
      builder: (context) => AlertDialog(
        title: Text(initial == null ? context.l10n.categoryAdd : context.l10n.actionRename),
        content: TextField(
          controller: controller,
          autofocus: true,
          maxLength: 40,
          textCapitalization: TextCapitalization.words,
          decoration: InputDecoration(labelText: context.l10n.categoryNameLabel),
        ),
        actions: <Widget>[
          TextButton(
            onPressed: () => Navigator.of(context).pop(),
            child: Text(context.l10n.actionCancel),
          ),
          FilledButton(
            onPressed: () => Navigator.of(context).pop(controller.text),
            child: Text(context.l10n.actionSave),
          ),
        ],
      ),
    );
  }

  Future<void> _add(BuildContext context, WidgetRef ref) async {
    final name = await _askName(context);
    if (name == null || !context.mounted) return;
    try {
      await ref.read(categoryRepositoryProvider).add(name);
    } on Object catch (e) {
      if (context.mounted) showFailure(context, ref, e);
    }
  }

  Future<void> _rename(BuildContext context, WidgetRef ref, MenuCategory category) async {
    final name = await _askName(context, initial: category.name);
    if (name == null || !context.mounted) return;
    try {
      await ref.read(categoryRepositoryProvider).rename(category.id, name);
    } on Object catch (e) {
      if (context.mounted) showFailure(context, ref, e);
    }
  }

  Future<void> _delete(
    BuildContext context,
    WidgetRef ref,
    MenuCategory category,
    List<MenuCategory> all,
  ) async {
    final repository = ref.read(categoryRepositoryProvider);
    final count = await repository.menuCount(category.id);
    if (!context.mounted) return;

    if (count == 0) {
      final ok = await confirmDialog(
        context,
        title: context.l10n.categoryDeleteTitle,
        message: context.l10n.categoryDeleteEmpty(category.name),
        confirmLabel: context.l10n.actionDelete,
        destructive: true,
      );
      if (ok) await repository.delete(category.id);
      return;
    }

    // Masih berisi menu: pilih tujuan pemindahan atau "Tanpa kategori" (PRD 6.3.4a).
    final others = all.where((c) => c.id != category.id).toList();
    final choice = await showDialog<({bool confirmed, int? moveTo})>(
      context: context,
      builder: (context) => _MoveMenusDialog(category: category, count: count, others: others),
    );
    if (choice == null || !choice.confirmed) return;
    await repository.delete(category.id, moveMenusTo: choice.moveTo);
  }
}

class _MoveMenusDialog extends StatefulWidget {
  const _MoveMenusDialog({required this.category, required this.count, required this.others});

  final MenuCategory category;
  final int count;
  final List<MenuCategory> others;

  @override
  State<_MoveMenusDialog> createState() => _MoveMenusDialogState();
}

class _MoveMenusDialogState extends State<_MoveMenusDialog> {
  int? _target;

  @override
  Widget build(BuildContext context) {
    final l10n = context.l10n;
    return AlertDialog(
      title: Text(l10n.categoryDeleteTitle),
      content: Column(
        mainAxisSize: MainAxisSize.min,
        crossAxisAlignment: CrossAxisAlignment.start,
        children: <Widget>[
          Text(l10n.categoryDeleteWithMenus(widget.category.name, widget.count)),
          const SizedBox(height: 16),
          DropdownButtonFormField<int?>(
            initialValue: _target,
            isExpanded: true,
            decoration: InputDecoration(labelText: l10n.categoryMoveTo),
            items: <DropdownMenuItem<int?>>[
              DropdownMenuItem<int?>(value: null, child: Text(l10n.categoryNone)),
              for (final c in widget.others)
                DropdownMenuItem<int?>(value: c.id, child: Text(c.name)),
            ],
            onChanged: (value) => setState(() => _target = value),
          ),
        ],
      ),
      actions: <Widget>[
        TextButton(
          onPressed: () => Navigator.of(context).pop(),
          child: Text(l10n.actionCancel),
        ),
        FilledButton(
          onPressed: () => Navigator.of(context).pop((confirmed: true, moveTo: _target)),
          child: Text(l10n.actionDelete),
        ),
      ],
    );
  }
}

// --- Menu -----------------------------------------------------------------------

class _MenusTab extends ConsumerWidget {
  const _MenusTab();

  @override
  Widget build(BuildContext context, WidgetRef ref) {
    final l10n = context.l10n;
    final menus = ref.watch(menusProvider);
    final ingredients = ref.watch(allIngredientsProvider).value ?? const <Ingredient>[];
    final categories = ref.watch(categoriesProvider).value ?? const <MenuCategory>[];
    final fmt = ref.watch(formattersProvider);
    final byId = <int, Ingredient>{for (final i in ingredients) i.id: i};
    final categoryName = <int, String>{for (final c in categories) c.id: c.name};
    final repository = ref.read(menuRepositoryProvider);

    return Scaffold(
      body: AsyncBody(
        value: menus,
        builder: (list) {
          if (list.isEmpty) {
            return EmptyState(
              icon: Icons.restaurant_menu,
              title: l10n.menuEmptyTitle,
              message: l10n.menuEmptyMessage,
            );
          }
          return ListView.separated(
            padding: const EdgeInsets.only(bottom: 96),
            itemCount: list.length,
            separatorBuilder: (_, __) => const Divider(height: 1),
            itemBuilder: (context, index) {
              final menu = list[index];
              final costing = MenuCosting.compute(menu, byId);
              return ListTile(
                leading: ClipRRect(
                  borderRadius: BorderRadius.circular(8),
                  child: MenuPhoto(photoPath: menu.photoPath, size: 48),
                ),
                title: Text(menu.name),
                subtitle: Text(
                  <String>[
                    fmt.money(menu.price),
                    if (menu.hasRecipe)
                      costing.incomplete
                          ? l10n.menuCostIncomplete
                          : l10n.menuCostLine(
                              fmt.money(costing.unitCost),
                              costing.marginPermille == null
                                  ? '-'
                                  : fmt.permille(costing.marginPermille!),
                            ),
                    menu.categoryId == null
                        ? l10n.menuNoCategoryWarning
                        : categoryName[menu.categoryId] ?? '',
                    if (!menu.active) l10n.menuInactive,
                  ].where((s) => s.isNotEmpty).join('  |  '),
                ),
                isThreeLine: false,
                onTap: () => context.push(AppRoutes.menuEditOf(menu.id)),
                trailing: PopupMenuButton<String>(
                  onSelected: (value) => _action(context, ref, repository, menu, value),
                  itemBuilder: (context) => <PopupMenuEntry<String>>[
                    PopupMenuItem<String>(value: 'edit', child: Text(l10n.actionEdit)),
                    PopupMenuItem<String>(value: 'duplicate', child: Text(l10n.menuDuplicate)),
                    PopupMenuItem<String>(
                      value: 'toggle',
                      child: Text(menu.active ? l10n.menuDeactivate : l10n.menuActivate),
                    ),
                    PopupMenuItem<String>(value: 'delete', child: Text(l10n.actionDelete)),
                  ],
                ),
              );
            },
          );
        },
      ),
      floatingActionButton: FloatingActionButton.extended(
        icon: const Icon(Icons.add),
        label: Text(l10n.menuAdd),
        onPressed: () => context.push(AppRoutes.menuEditOf(null)),
      ),
    );
  }

  Future<void> _action(
    BuildContext context,
    WidgetRef ref,
    MenuRepository repository,
    MenuItem menu,
    String action,
  ) async {
    final l10n = context.l10n;
    try {
      switch (action) {
        case 'edit':
          await context.push(AppRoutes.menuEditOf(menu.id));
        case 'duplicate':
          await repository.duplicate(menu.id);
        case 'toggle':
          await repository.setActive(menu.id, active: !menu.active);
        case 'delete':
          final ok = await confirmDialog(
            context,
            title: l10n.menuDeleteTitle,
            message: l10n.menuDeleteMessage(menu.name),
            confirmLabel: l10n.actionDelete,
            destructive: true,
          );
          if (ok) await repository.softDelete(menu.id);
      }
    } on Object catch (e) {
      if (context.mounted) showFailure(context, ref, e);
    }
  }
}
