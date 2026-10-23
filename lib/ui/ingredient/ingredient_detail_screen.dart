import 'package:flutter/material.dart';
import 'package:flutter_riverpod/flutter_riverpod.dart';
import 'package:go_router/go_router.dart';

import 'package:arn_kasir/app/providers.dart';
import 'package:arn_kasir/app/routes.dart';
import 'package:arn_kasir/app/services/sales_actions.dart';
import 'package:arn_kasir/domain/core/fraction.dart';
import 'package:arn_kasir/domain/core/rounding.dart';
import 'package:arn_kasir/domain/inventory/ingredient.dart';
import 'package:arn_kasir/domain/inventory/stock_change.dart';
import 'package:arn_kasir/domain/inventory/stock_state.dart';
import 'package:arn_kasir/domain/models/report_models.dart';
import 'package:arn_kasir/domain/models/stock_movement_view.dart';
import 'package:arn_kasir/ui/ingredient/ingredient_list_screen.dart';
import 'package:arn_kasir/ui/shared/l10n_context.dart';
import 'package:arn_kasir/ui/shared/quantity_input.dart';
import 'package:arn_kasir/ui/shared/shared_providers.dart';
import 'package:arn_kasir/ui/shared/widgets.dart';

/// Penyaring tanggal riwayat; `null` berarti semua.
final movementRangeProvider = NotifierProvider<MovementRange, DateRange?>(MovementRange.new);

class MovementRange extends Notifier<DateRange?> {
  @override
  DateRange? build() => null;

  void set(DateRange? range) => state = range;
}

final _ingredientProvider = StreamProvider.family<Ingredient?, int>(
  (ref, id) => ref.watch(ingredientRepositoryProvider).watch(id),
);

final _movementsProvider = StreamProvider.family<List<StockMovementView>, int>((ref, id) {
  final range = ref.watch(movementRangeProvider);
  return ref.watch(ingredientRepositoryProvider).watchMovements(id, range: range);
});

class IngredientDetailScreen extends ConsumerWidget {
  const IngredientDetailScreen({required this.ingredientId, super.key});

  final int ingredientId;

  @override
  Widget build(BuildContext context, WidgetRef ref) {
    final l10n = context.l10n;
    final ingredient = ref.watch(_ingredientProvider(ingredientId));

    return AsyncBody(
      value: ingredient,
      builder: (item) {
        if (item == null) {
          return Scaffold(
            appBar: AppBar(),
            body: EmptyState(icon: Icons.inventory_2_outlined, title: l10n.errIngredientNotFound),
          );
        }
        return _DetailBody(ingredient: item);
      },
    );
  }
}

class _DetailBody extends ConsumerWidget {
  const _DetailBody({required this.ingredient});

  final Ingredient ingredient;

  @override
  Widget build(BuildContext context, WidgetRef ref) {
    final l10n = context.l10n;
    final fmt = ref.watch(formattersProvider);
    final theme = Theme.of(context);
    final movements = ref.watch(_movementsProvider(ingredient.id));
    final range = ref.watch(movementRangeProvider);
    final levelColor = stockLevelColor(context, ingredient.level);
    // Rata-rata biaya per 1.000 satuan dasar, murni bilangan bulat (tanpa double).
    final averageCostPerThousand = ingredient.stock.quantity > 0
        ? mulDivRoundHalfUp(ingredient.stock.value, 1000, ingredient.stock.quantity)
        : null;

    return Scaffold(
      appBar: AppBar(
        title: Text(ingredient.name),
        actions: <Widget>[
          IconButton(
            icon: const Icon(Icons.edit_outlined),
            tooltip: l10n.actionEdit,
            onPressed: () => context.push(AppRoutes.ingredientEditOf(ingredient.id)),
          ),
          IconButton(
            icon: const Icon(Icons.delete_outline),
            tooltip: l10n.actionDelete,
            onPressed: () => _delete(context, ref),
          ),
        ],
      ),
      body: ListView(
        padding: const EdgeInsets.all(16),
        children: <Widget>[
          Card(
            child: Padding(
              padding: const EdgeInsets.all(20),
              child: Column(
                crossAxisAlignment: CrossAxisAlignment.start,
                children: <Widget>[
                  Row(
                    children: <Widget>[
                      Icon(Icons.circle, size: 14, color: levelColor),
                      const SizedBox(width: 8),
                      Text(
                        stockLevelLabel(context, ingredient.level),
                        style: theme.textTheme.labelLarge?.copyWith(color: levelColor),
                      ),
                    ],
                  ),
                  const SizedBox(height: 8),
                  Text(
                    fmt.baseQuantity(ingredient, ingredient.stock.quantity),
                    style: theme.textTheme.headlineMedium?.copyWith(fontWeight: FontWeight.bold),
                  ),
                  if (ingredient.purchaseUnitName != null &&
                      ingredient.purchaseUnitName != ingredient.dimension.baseUnitName)
                    Text(
                      '= ${fmt.quantity(ingredient, ingredient.stock.quantity, ingredient.purchaseUnitName!)}',
                      style: theme.textTheme.bodyMedium,
                    ),
                  const SizedBox(height: 12),
                  _InfoRow(label: l10n.ingredientStockValue, value: fmt.money(ingredient.stock.value)),
                  _InfoRow(
                    label: l10n.ingredientAverageCost,
                    value: averageCostPerThousand == null
                        ? '-'
                        : l10n.ingredientAverageCostValue(
                            fmt.money(averageCostPerThousand),
                            '1.000 ${ingredient.dimension.baseUnitName}',
                          ),
                  ),
                  _InfoRow(
                    label: l10n.ingredientMinStockLabel,
                    value: fmt.baseQuantity(ingredient, ingredient.minStock),
                  ),
                  if (ingredient.stock.quantity < 0)
                    Padding(
                      padding: const EdgeInsets.only(top: 8),
                      child: Text(
                        l10n.ingredientNegativeStockHint,
                        style: theme.textTheme.bodySmall?.copyWith(color: theme.colorScheme.error),
                      ),
                    ),
                ],
              ),
            ),
          ),
          const SizedBox(height: 12),
          Row(
            children: <Widget>[
              Expanded(
                child: FilledButton.icon(
                  icon: const Icon(Icons.add_shopping_cart),
                  label: Text(l10n.restockAction),
                  onPressed: ingredient.archived ? null : () => _restock(context),
                ),
              ),
              const SizedBox(width: 12),
              Expanded(
                child: OutlinedButton.icon(
                  icon: const Icon(Icons.fact_check_outlined),
                  label: Text(l10n.adjustAction),
                  onPressed: ingredient.archived ? null : () => _adjust(context),
                ),
              ),
            ],
          ),
          const SizedBox(height: 24),
          Row(
            children: <Widget>[
              Text(l10n.movementHistory, style: theme.textTheme.titleMedium),
              const Spacer(),
              if (range != null)
                IconButton(
                  icon: const Icon(Icons.filter_alt_off_outlined),
                  tooltip: l10n.movementClearFilter,
                  onPressed: () => ref.read(movementRangeProvider.notifier).set(null),
                ),
              IconButton(
                icon: const Icon(Icons.date_range),
                tooltip: l10n.movementFilter,
                onPressed: () => _pickRange(context, ref),
              ),
            ],
          ),
          if (range != null)
            Text(
              '${fmt.date(range.from)} - ${fmt.date(range.to.subtract(const Duration(days: 1)))}',
              style: theme.textTheme.bodySmall,
            ),
          const SizedBox(height: 8),
          AsyncBody(
            value: movements,
            builder: (list) {
              if (list.isEmpty) {
                return Padding(
                  padding: const EdgeInsets.all(24),
                  child: Center(child: Text(l10n.movementEmpty)),
                );
              }
              return Column(
                children: <Widget>[
                  for (final movement in list)
                    _MovementTile(ingredient: ingredient, movement: movement),
                ],
              );
            },
          ),
        ],
      ),
    );
  }

  Future<void> _pickRange(BuildContext context, WidgetRef ref) async {
    final now = DateTime.now();
    final picked = await showDateRangePicker(
      context: context,
      firstDate: DateTime(now.year - 5),
      lastDate: DateTime(now.year + 1),
    );
    if (picked == null) return;
    ref.read(movementRangeProvider.notifier).set(
          DateRange(
            from: DateTime(picked.start.year, picked.start.month, picked.start.day),
            to: DateTime(picked.end.year, picked.end.month, picked.end.day)
                .add(const Duration(days: 1)),
          ),
        );
  }

  Future<void> _delete(BuildContext context, WidgetRef ref) async {
    final l10n = context.l10n;
    final ok = await confirmDialog(
      context,
      title: l10n.ingredientDeleteTitle,
      message: l10n.ingredientDeleteMessage(ingredient.name),
      confirmLabel: l10n.actionDelete,
      destructive: true,
    );
    if (!ok) return;
    try {
      await ref.read(ingredientRepositoryProvider).delete(ingredient.id);
      if (context.mounted) context.pop();
    } on Object catch (e) {
      if (context.mounted) showFailure(context, ref, e);
    }
  }

  Future<void> _restock(BuildContext context) {
    return showModalBottomSheet<void>(
      context: context,
      isScrollControlled: true,
      useSafeArea: true,
      showDragHandle: true,
      builder: (context) => _RestockSheet(ingredient: ingredient),
    );
  }

  Future<void> _adjust(BuildContext context) {
    return showModalBottomSheet<void>(
      context: context,
      isScrollControlled: true,
      useSafeArea: true,
      showDragHandle: true,
      builder: (context) => _AdjustSheet(ingredient: ingredient),
    );
  }
}

class _InfoRow extends StatelessWidget {
  const _InfoRow({required this.label, required this.value});

  final String label;
  final String value;

  @override
  Widget build(BuildContext context) {
    return Padding(
      padding: const EdgeInsets.symmetric(vertical: 2),
      child: Row(
        children: <Widget>[
          Expanded(child: Text(label)),
          Text(value, style: const TextStyle(fontWeight: FontWeight.w600)),
        ],
      ),
    );
  }
}

class _MovementTile extends ConsumerWidget {
  const _MovementTile({required this.ingredient, required this.movement});

  final Ingredient ingredient;
  final StockMovementView movement;

  @override
  Widget build(BuildContext context, WidgetRef ref) {
    final l10n = context.l10n;
    final fmt = ref.watch(formattersProvider);
    final theme = Theme.of(context);
    final incoming = movement.quantityDelta >= 0;
    final label = switch (movement.type) {
      MovementType.restock => l10n.movementRestock,
      MovementType.sale => l10n.movementSale,
      MovementType.voidSale => l10n.movementVoid,
      MovementType.adjustment => l10n.movementAdjustment,
    };
    final sign = incoming ? '+' : '-';
    return ListTile(
      contentPadding: EdgeInsets.zero,
      leading: Icon(
        incoming ? Icons.south_west : Icons.north_east,
        color: incoming ? const Color(0xFF2E7D32) : theme.colorScheme.error,
      ),
      title: Text(label),
      subtitle: Text(
        <String>[
          fmt.dateTime(movement.time),
          if (movement.reason != null && movement.reason!.isNotEmpty) movement.reason!,
        ].join('\n'),
      ),
      isThreeLine: movement.reason != null && movement.reason!.isNotEmpty,
      trailing: Column(
        mainAxisAlignment: MainAxisAlignment.center,
        crossAxisAlignment: CrossAxisAlignment.end,
        children: <Widget>[
          Text(
            '$sign${fmt.baseQuantity(ingredient, movement.quantityDelta.abs())}',
            style: theme.textTheme.titleSmall,
          ),
          Text(
            '${movement.valueDelta < 0 ? '-' : '+'}${fmt.money(movement.valueDelta.abs())}',
            style: theme.textTheme.bodySmall,
          ),
        ],
      ),
    );
  }
}

// --- Restok -----------------------------------------------------------------------

class _RestockSheet extends ConsumerStatefulWidget {
  const _RestockSheet({required this.ingredient});

  final Ingredient ingredient;

  @override
  ConsumerState<_RestockSheet> createState() => _RestockSheetState();
}

class _RestockSheetState extends ConsumerState<_RestockSheet> {
  String _unit = '';
  String _text = '';
  final TextEditingController _price = TextEditingController();
  final TextEditingController _note = TextEditingController();
  bool _saving = false;

  @override
  void dispose() {
    _price.dispose();
    _note.dispose();
    super.dispose();
  }

  @override
  Widget build(BuildContext context) {
    final l10n = context.l10n;
    final fmt = ref.watch(formattersProvider);
    final ingredient = widget.ingredient;
    final base = previewBase(ingredient, _unit, _text);
    final price = parseMoney(_price.text);
    final ready = base != null && base > 0 && price != null;
    final purchase = ingredient.purchaseUnitName;

    return Padding(
      padding: EdgeInsets.fromLTRB(16, 0, 16, 16 + MediaQuery.viewInsetsOf(context).bottom),
      child: SingleChildScrollView(
        child: Column(
          mainAxisSize: MainAxisSize.min,
          crossAxisAlignment: CrossAxisAlignment.stretch,
          children: <Widget>[
            Text(
              l10n.restockTitle(ingredient.name),
              style: Theme.of(context).textTheme.titleLarge,
            ),
            const SizedBox(height: 16),
            QuantityInput(
              catalog: ingredient.catalog,
              label: l10n.restockAmount,
              initialUnit: purchase,
              autofocus: true,
              onChanged: (unit, text) => setState(() {
                _unit = unit;
                _text = text;
              }),
            ),
            if (base != null)
              Padding(
                padding: const EdgeInsets.only(top: 4),
                child: Text(
                  l10n.recipeStoredAs(fmt.baseQuantity(ingredient, base)),
                  style: Theme.of(context).textTheme.bodySmall,
                ),
              ),
            const SizedBox(height: 12),
            TextField(
              controller: _price,
              keyboardType: TextInputType.number,
              inputFormatters: digitsOnly,
              onChanged: (_) => setState(() {}),
              decoration: InputDecoration(
                labelText: l10n.restockTotalPrice,
                prefixText: '${fmt.currency} ',
                helperText: l10n.restockPriceHelper,
              ),
            ),
            const SizedBox(height: 12),
            TextField(
              controller: _note,
              maxLength: 80,
              decoration: InputDecoration(labelText: l10n.restockNote),
            ),
            const SizedBox(height: 8),
            FilledButton(
              onPressed: ready && !_saving ? _submit : null,
              child: Text(l10n.restockConfirm),
            ),
          ],
        ),
      ),
    );
  }

  Future<void> _submit() async {
    setState(() => _saving = true);
    try {
      await ref.read(stockActionsProvider).restock(
            ingredientId: widget.ingredient.id,
            unitName: _unit,
            amount: Fraction.parse(_text),
            totalPrice: parseMoney(_price.text)!,
            note: _note.text.trim().isEmpty ? null : _note.text.trim(),
          );
      if (!mounted) return;
      Navigator.of(context).pop();
      showMessage(context, context.l10n.restockDone);
    } on Object catch (e) {
      if (!mounted) return;
      setState(() => _saving = false);
      showFailure(context, ref, e);
    }
  }
}

// --- Opname -----------------------------------------------------------------------

class _AdjustSheet extends ConsumerStatefulWidget {
  const _AdjustSheet({required this.ingredient});

  final Ingredient ingredient;

  @override
  ConsumerState<_AdjustSheet> createState() => _AdjustSheetState();
}

class _AdjustSheetState extends ConsumerState<_AdjustSheet> {
  String _unit = '';
  String _text = '';
  final TextEditingController _reason = TextEditingController();
  bool _saving = false;

  @override
  void dispose() {
    _reason.dispose();
    super.dispose();
  }

  @override
  Widget build(BuildContext context) {
    final l10n = context.l10n;
    final fmt = ref.watch(formattersProvider);
    final shrinkageOn = ref.watch(settingsProvider.select((s) => s.shrinkageEnabled));
    final ingredient = widget.ingredient;
    final physical = previewBase(ingredient, _unit, _text, allowZero: true);
    final preview = physical == null ? null : StockLedger.adjustTo(ingredient.stock, physical);

    return Padding(
      padding: EdgeInsets.fromLTRB(16, 0, 16, 16 + MediaQuery.viewInsetsOf(context).bottom),
      child: SingleChildScrollView(
        child: Column(
          mainAxisSize: MainAxisSize.min,
          crossAxisAlignment: CrossAxisAlignment.stretch,
          children: <Widget>[
            Text(
              l10n.adjustTitle(ingredient.name),
              style: Theme.of(context).textTheme.titleLarge,
            ),
            const SizedBox(height: 4),
            Text(
              l10n.adjustSystemStock(fmt.baseQuantity(ingredient, ingredient.stock.quantity)),
              style: Theme.of(context).textTheme.bodyMedium,
            ),
            const SizedBox(height: 16),
            QuantityInput(
              catalog: ingredient.catalog,
              label: l10n.adjustPhysicalStock,
              autofocus: true,
              onChanged: (unit, text) => setState(() {
                _unit = unit;
                _text = text;
              }),
            ),
            if (preview != null) ...<Widget>[
              const SizedBox(height: 8),
              if (preview.quantityDelta == 0)
                Text(l10n.adjustNoDifference)
              else
                Text(
                  l10n.adjustDifference(
                    '${preview.quantityDelta > 0 ? '+' : '-'}'
                    '${fmt.baseQuantity(ingredient, preview.quantityDelta.abs())}',
                    fmt.money(preview.valueDelta.abs()),
                  ),
                ),
              if (shrinkageOn && preview.quantityDelta < 0 && preview.valueDelta < 0)
                Text(
                  l10n.adjustShrinkageNote(fmt.money(-preview.valueDelta)),
                  style: Theme.of(context).textTheme.bodySmall,
                ),
            ],
            const SizedBox(height: 12),
            Wrap(
              spacing: 8,
              children: <Widget>[
                for (final reason in <String>[
                  l10n.adjustReasonSpoiled,
                  l10n.adjustReasonSpilled,
                  l10n.adjustReasonMiscount,
                ])
                  ActionChip(
                    label: Text(reason),
                    onPressed: () => setState(() => _reason.text = reason),
                  ),
              ],
            ),
            const SizedBox(height: 8),
            TextField(
              controller: _reason,
              maxLength: 80,
              decoration: InputDecoration(labelText: l10n.adjustReasonLabel),
            ),
            FilledButton(
              onPressed: physical != null && !_saving ? _submit : null,
              child: Text(l10n.adjustConfirm),
            ),
          ],
        ),
      ),
    );
  }

  Future<void> _submit() async {
    setState(() => _saving = true);
    try {
      await ref.read(stockActionsProvider).adjust(
            ingredientId: widget.ingredient.id,
            unitName: _unit,
            physicalAmount: Fraction.parse(_text),
            reason: _reason.text.trim().isEmpty ? null : _reason.text.trim(),
          );
      if (!mounted) return;
      Navigator.of(context).pop();
      showMessage(context, context.l10n.adjustDone);
    } on Object catch (e) {
      if (!mounted) return;
      setState(() => _saving = false);
      showFailure(context, ref, e);
    }
  }
}
