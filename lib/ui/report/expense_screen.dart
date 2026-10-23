import 'package:flutter/material.dart';
import 'package:flutter_riverpod/flutter_riverpod.dart';

import 'package:arn_kasir/app/providers.dart';
import 'package:arn_kasir/domain/expenses/expense.dart';
import 'package:arn_kasir/domain/models/expense_models.dart';
import 'package:arn_kasir/domain/models/report_models.dart';
import 'package:arn_kasir/ui/report/expense_dialogs.dart';
import 'package:arn_kasir/ui/report/expense_providers.dart';
import 'package:arn_kasir/ui/report/report_providers.dart';
import 'package:arn_kasir/ui/report/report_widgets.dart';
import 'package:arn_kasir/ui/shared/l10n_context.dart';
import 'package:arn_kasir/ui/shared/shared_providers.dart';
import 'package:arn_kasir/ui/shared/widgets.dart';

/// Info Pengeluaran (PRD 6.3.7 dan 9.3): laba operasional dan arus kas ditampilkan
/// terpisah, lengkap dengan penjelasan mengapa angkanya bisa berbeda.
class ExpenseScreen extends ConsumerWidget {
  const ExpenseScreen({super.key});

  @override
  Widget build(BuildContext context, WidgetRef ref) {
    final l10n = context.l10n;
    final overview = ref.watch(expenseOverviewProvider);

    return Scaffold(
      appBar: AppBar(
        title: Text(l10n.expenseTitle),
        actions: <Widget>[
          IconButton(
            tooltip: l10n.expenseCategoriesTitle,
            icon: const Icon(Icons.category_outlined),
            onPressed: () => showExpenseCategoriesSheet(context),
          ),
        ],
      ),
      floatingActionButton: FloatingActionButton.extended(
        icon: const Icon(Icons.add),
        label: Text(l10n.expenseAddAction),
        onPressed: () => showExpenseEntryDialog(context),
      ),
      body: AsyncBody<ExpenseOverview>(
        value: overview,
        builder: (data) => _ExpenseBody(overview: data),
      ),
    );
  }
}

class _ExpenseBody extends ConsumerWidget {
  const _ExpenseBody({required this.overview});

  final ExpenseOverview overview;

  @override
  Widget build(BuildContext context, WidgetRef ref) {
    final l10n = context.l10n;
    final fmt = ref.watch(formattersProvider);
    final theme = Theme.of(context);
    final totals = overview.report.totals;
    final figures = overview.report.figures;
    final records = ref.watch(expenseRecordsProvider);
    final categories = ref.watch(expenseCategoriesProvider).value ?? const <ExpenseCategory>[];
    final filter = ref.watch(expenseCategoryFilterProvider);

    return ListView(
      padding: const EdgeInsets.fromLTRB(16, 8, 16, 96),
      children: <Widget>[
        const Align(alignment: Alignment.centerLeft, child: PeriodSelector()),
        const SizedBox(height: 12),
        StatGrid(
          children: <Widget>[
            StatCard(
              label: l10n.expenseOperatingProfit,
              value: fmt.money(figures.operatingProfit),
              emphasis: true,
              hint: l10n.expenseOperatingProfitHint,
            ),
            StatCard(
              label: l10n.expenseCashFlow,
              value: fmt.money(figures.cashFlow),
              emphasis: true,
              hint: l10n.expenseCashFlowHint,
            ),
          ],
        ),
        const SizedBox(height: 8),
        ExpansionTile(
          tilePadding: EdgeInsets.zero,
          title: Text(l10n.expenseWhyDifferentTitle),
          children: <Widget>[
            Align(
              alignment: Alignment.centerLeft,
              child: Padding(
                padding: const EdgeInsets.only(bottom: 12),
                child: Text(l10n.expenseWhyDifferentBody, style: theme.textTheme.bodyMedium),
              ),
            ),
          ],
        ),
        _Section(
          title: l10n.expenseComponents,
          child: Column(
            children: <Widget>[
              _FigureRow(label: l10n.incomeNetSales, value: fmt.money(totals.netSales)),
              _FigureRow(
                label: l10n.incomeCogs,
                value: fmt.money(-totals.costOfGoodsSold),
                note: l10n.expenseCogsNote,
              ),
              _FigureRow(
                label: l10n.incomeGrossProfit,
                value: fmt.money(figures.grossProfit),
                bold: true,
              ),
              _FigureRow(
                label: l10n.expenseManual,
                value: fmt.money(-totals.manualExpenses),
              ),
              _FigureRow(
                label: l10n.expenseShrinkage,
                value: fmt.money(-totals.shrinkage),
                note: l10n.expenseShrinkageNote,
              ),
              _FigureRow(
                label: l10n.expenseOperatingProfit,
                value: fmt.money(figures.operatingProfit),
                bold: true,
              ),
              const Divider(),
              _FigureRow(
                label: l10n.expenseIngredientPurchases,
                value: fmt.money(-totals.ingredientPurchases),
                note: l10n.expensePurchasesNote,
              ),
              _FigureRow(
                label: l10n.expenseCashFlow,
                value: fmt.money(figures.cashFlow),
                bold: true,
              ),
            ],
          ),
        ),
        if (overview.byCategory.isNotEmpty)
          _Section(
            title: l10n.expenseByCategory,
            child: BreakdownList(
              entries: overview.byCategory,
              labelOf: (entry) => expenseCategoryLabel(
                l10n,
                int.tryParse(entry.key ?? '') ?? 0,
                entry.label ?? '',
              ),
            ),
          ),
        _Section(
          title: l10n.expenseList,
          child: Column(
            crossAxisAlignment: CrossAxisAlignment.start,
            children: <Widget>[
              SingleChildScrollView(
                scrollDirection: Axis.horizontal,
                child: Row(
                  children: <Widget>[
                    Padding(
                      padding: const EdgeInsets.only(right: 8),
                      child: ChoiceChip(
                        label: Text(l10n.expenseFilterAll),
                        selected: filter == null,
                        onSelected: (_) =>
                            ref.read(expenseCategoryFilterProvider.notifier).select(null),
                      ),
                    ),
                    for (final c in categories)
                      Padding(
                        padding: const EdgeInsets.only(right: 8),
                        child: ChoiceChip(
                          label: Text(expenseCategoryLabel(l10n, c.id, c.name)),
                          selected: filter == c.id,
                          onSelected: (_) =>
                              ref.read(expenseCategoryFilterProvider.notifier).select(c.id),
                        ),
                      ),
                  ],
                ),
              ),
              const SizedBox(height: 8),
              records.when(
                data: (list) => list.isEmpty
                    ? Padding(
                        padding: const EdgeInsets.symmetric(vertical: 24),
                        child: Center(child: Text(l10n.expenseEmpty)),
                      )
                    : Column(
                        children: <Widget>[
                          for (final record in list) _ExpenseTile(record: record),
                        ],
                      ),
                loading: () => const Padding(
                  padding: EdgeInsets.all(24),
                  child: Center(child: CircularProgressIndicator()),
                ),
                error: (error, _) => Text(error.toString()),
              ),
            ],
          ),
        ),
      ],
    );
  }
}

class _Section extends StatelessWidget {
  const _Section({required this.title, required this.child});

  final String title;
  final Widget child;

  @override
  Widget build(BuildContext context) {
    return Padding(
      padding: const EdgeInsets.only(top: 12, bottom: 12),
      child: Column(
        crossAxisAlignment: CrossAxisAlignment.start,
        children: <Widget>[
          Text(title, style: Theme.of(context).textTheme.titleMedium),
          const SizedBox(height: 8),
          child,
        ],
      ),
    );
  }
}

class _FigureRow extends StatelessWidget {
  const _FigureRow({
    required this.label,
    required this.value,
    this.note,
    this.bold = false,
  });

  final String label;
  final String value;
  final String? note;
  final bool bold;

  @override
  Widget build(BuildContext context) {
    final theme = Theme.of(context);
    final style = bold
        ? theme.textTheme.bodyLarge?.copyWith(fontWeight: FontWeight.bold)
        : theme.textTheme.bodyLarge;
    return Padding(
      padding: const EdgeInsets.symmetric(vertical: 4),
      child: Row(
        crossAxisAlignment: CrossAxisAlignment.start,
        children: <Widget>[
          Expanded(
            child: Column(
              crossAxisAlignment: CrossAxisAlignment.start,
              children: <Widget>[
                Text(label, style: style),
                if (note != null)
                  Text(
                    note!,
                    style: theme.textTheme.bodySmall?.copyWith(
                      color: theme.colorScheme.onSurfaceVariant,
                    ),
                  ),
              ],
            ),
          ),
          const SizedBox(width: 12),
          Text(value, style: style),
        ],
      ),
    );
  }
}

class _ExpenseTile extends ConsumerWidget {
  const _ExpenseTile({required this.record});

  final ExpenseRecord record;

  @override
  Widget build(BuildContext context, WidgetRef ref) {
    final l10n = context.l10n;
    final fmt = ref.watch(formattersProvider);
    final isManual = record.source == ExpenseSource.manual;
    final category = expenseCategoryLabel(l10n, record.categoryId, record.categoryName);
    final sourceLabel = switch (record.source) {
      ExpenseSource.manual => null,
      ExpenseSource.restock => l10n.expenseSourceRestock,
      ExpenseSource.shrinkage => l10n.expenseSourceShrinkage,
    };
    final subtitleParts = <String>[
      fmt.date(record.date),
      if (sourceLabel != null) sourceLabel,
      if (record.note != null) record.note!,
    ];

    return ListTile(
      contentPadding: EdgeInsets.zero,
      title: Text(category),
      subtitle: Text(subtitleParts.join('  |  ')),
      trailing: Text(fmt.money(record.amount)),
      onTap: isManual ? () => showExpenseEntryDialog(context, existing: record) : null,
      onLongPress: isManual ? () => _confirmDelete(context, ref) : null,
    );
  }

  Future<void> _confirmDelete(BuildContext context, WidgetRef ref) async {
    final l10n = context.l10n;
    final confirmed = await confirmDialog(
      context,
      title: l10n.expenseDeleteTitle,
      message: l10n.expenseDeleteMessage,
      confirmLabel: l10n.actionDelete,
      destructive: true,
    );
    if (!confirmed || !context.mounted) return;
    try {
      await ref.read(expenseRepositoryProvider).deleteManual(record.id);
    } on Object catch (e) {
      if (context.mounted) showFailure(context, ref, e);
    }
  }
}
