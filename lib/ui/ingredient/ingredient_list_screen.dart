import 'package:flutter/material.dart';
import 'package:flutter_riverpod/flutter_riverpod.dart';
import 'package:go_router/go_router.dart';

import 'package:arn_kasir/app/providers.dart';
import 'package:arn_kasir/app/routes.dart';
import 'package:arn_kasir/domain/inventory/ingredient.dart';
import 'package:arn_kasir/domain/menu/availability.dart';
import 'package:arn_kasir/ui/shared/l10n_context.dart';
import 'package:arn_kasir/ui/shared/shared_providers.dart';
import 'package:arn_kasir/ui/shared/widgets.dart';

/// Tampilkan bahan yang diarsipkan di daftar.
final showArchivedProvider = NotifierProvider<ShowArchived, bool>(ShowArchived.new);

class ShowArchived extends Notifier<bool> {
  @override
  bool build() => false;

  void toggle() => state = !state;
}

/// Warna indikator tingkat stok: aman, menipis, habis (PRD 6.3.5).
Color stockLevelColor(BuildContext context, StockLevel level) {
  final scheme = Theme.of(context).colorScheme;
  return switch (level) {
    StockLevel.ok => const Color(0xFF2E7D32),
    StockLevel.low => scheme.tertiary,
    StockLevel.out => scheme.error,
  };
}

String stockLevelLabel(BuildContext context, StockLevel level) {
  final l10n = context.l10n;
  return switch (level) {
    StockLevel.ok => l10n.stockLevelOk,
    StockLevel.low => l10n.stockLevelLow,
    StockLevel.out => l10n.stockLevelOut,
  };
}

class IngredientListScreen extends ConsumerStatefulWidget {
  const IngredientListScreen({super.key});

  @override
  ConsumerState<IngredientListScreen> createState() => _IngredientListScreenState();
}

class _IngredientListScreenState extends ConsumerState<IngredientListScreen> {
  String _query = '';

  @override
  Widget build(BuildContext context) {
    final l10n = context.l10n;
    final showArchived = ref.watch(showArchivedProvider);
    final all = ref.watch(allIngredientsProvider);
    final fmt = ref.watch(formattersProvider);

    return Scaffold(
      appBar: AppBar(
        title: Text(l10n.drawerIngredients),
        actions: <Widget>[
          IconButton(
            icon: const Icon(Icons.settings_outlined),
            tooltip: l10n.stockSettingsTitle,
            onPressed: () => showStockSettingsDialog(context),
          ),
          IconButton(
            icon: Icon(showArchived ? Icons.archive : Icons.archive_outlined),
            tooltip: l10n.ingredientShowArchived,
            onPressed: ref.read(showArchivedProvider.notifier).toggle,
          ),
        ],
      ),
      body: AsyncBody(
        value: all,
        builder: (items) {
          final visible = <Ingredient>[
            for (final i in items)
              if ((showArchived || !i.archived) &&
                  (_query.isEmpty || i.name.toLowerCase().contains(_query)))
                i,
          ];
          if (items.where((i) => !i.archived).isEmpty && !showArchived) {
            return EmptyState(
              icon: Icons.inventory_2_outlined,
              title: l10n.ingredientEmptyTitle,
              message: l10n.ingredientEmptyMessage,
            );
          }
          return Column(
            children: <Widget>[
              Padding(
                padding: const EdgeInsets.fromLTRB(12, 8, 12, 8),
                child: SearchBar(
                  hintText: l10n.ingredientSearchHint,
                  leading: const Icon(Icons.search),
                  elevation: const WidgetStatePropertyAll<double>(0),
                  onChanged: (value) => setState(() => _query = value.trim().toLowerCase()),
                ),
              ),
              Expanded(
                child: visible.isEmpty
                    ? EmptyState(icon: Icons.search_off, title: l10n.homeNoSearchResult)
                    : ListView.separated(
                        padding: const EdgeInsets.only(bottom: 96),
                        itemCount: visible.length,
                        separatorBuilder: (_, __) => const Divider(height: 1),
                        itemBuilder: (context, index) {
                          final ingredient = visible[index];
                          final color = stockLevelColor(context, ingredient.level);
                          return ListTile(
                            leading: Tooltip(
                              message: stockLevelLabel(context, ingredient.level),
                              child: Icon(Icons.circle, color: color, size: 16),
                            ),
                            title: Text(
                              ingredient.archived
                                  ? '${ingredient.name} (${l10n.ingredientArchivedTag})'
                                  : ingredient.name,
                            ),
                            subtitle: Text(
                              l10n.ingredientStockLine(
                                fmt.baseQuantity(ingredient, ingredient.stock.quantity),
                                fmt.money(ingredient.stock.value),
                              ),
                            ),
                            trailing: const Icon(Icons.chevron_right),
                            onTap: () => context.push(
                              AppRoutes.ingredientDetailOf(ingredient.id),
                            ),
                          );
                        },
                      ),
              ),
            ],
          );
        },
      ),
      floatingActionButton: FloatingActionButton.extended(
        icon: const Icon(Icons.add),
        label: Text(l10n.ingredientAdd),
        onPressed: () => context.push(AppRoutes.ingredientEditOf(null)),
      ),
    );
  }
}

/// Pengaturan stok: kebijakan stok kurang dan pencatatan susut (PRD 7.3, 6.3.5).
Future<void> showStockSettingsDialog(BuildContext context) {
  return showDialog<void>(
    context: context,
    builder: (context) => Consumer(
      builder: (context, ref, _) {
        final l10n = context.l10n;
        final settings = ref.watch(settingsProvider);
        final controller = ref.read(settingsProvider.notifier);
        return AlertDialog(
          title: Text(l10n.stockSettingsTitle),
          content: SingleChildScrollView(
            child: Column(
              mainAxisSize: MainAxisSize.min,
              crossAxisAlignment: CrossAxisAlignment.start,
              children: <Widget>[
                Text(l10n.stockPolicyTitle, style: Theme.of(context).textTheme.titleSmall),
                const SizedBox(height: 8),
                SegmentedButton<StockPolicy>(
                  segments: <ButtonSegment<StockPolicy>>[
                    ButtonSegment<StockPolicy>(
                      value: StockPolicy.block,
                      label: Text(l10n.stockPolicyBlock),
                    ),
                    ButtonSegment<StockPolicy>(
                      value: StockPolicy.warn,
                      label: Text(l10n.stockPolicyWarn),
                    ),
                  ],
                  selected: <StockPolicy>{settings.stockPolicy},
                  onSelectionChanged: (value) =>
                      controller.update((s) => s.copyWith(stockPolicy: value.first)),
                ),
                const SizedBox(height: 8),
                Text(
                  settings.stockPolicy == StockPolicy.block
                      ? l10n.stockPolicyBlockHint
                      : l10n.stockPolicyWarnHint,
                  style: Theme.of(context).textTheme.bodySmall,
                ),
                const Divider(height: 24),
                SwitchListTile(
                  contentPadding: EdgeInsets.zero,
                  title: Text(l10n.shrinkageSetting),
                  subtitle: Text(l10n.shrinkageSettingHint),
                  value: settings.shrinkageEnabled,
                  onChanged: (value) =>
                      controller.update((s) => s.copyWith(shrinkageEnabled: value)),
                ),
              ],
            ),
          ),
          actions: <Widget>[
            TextButton(
              onPressed: () => Navigator.of(context).pop(),
              child: Text(l10n.actionClose),
            ),
          ],
        );
      },
    ),
  );
}
