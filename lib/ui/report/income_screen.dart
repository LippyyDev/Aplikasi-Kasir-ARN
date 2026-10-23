import 'package:flutter/material.dart';
import 'package:flutter_riverpod/flutter_riverpod.dart';
import 'package:go_router/go_router.dart';

import 'package:arn_kasir/app/routes.dart';
import 'package:arn_kasir/domain/models/report_models.dart';
import 'package:arn_kasir/domain/models/sale_summary.dart';
import 'package:arn_kasir/domain/sales/payment.dart';
import 'package:arn_kasir/domain/sales/sale_record.dart';
import 'package:arn_kasir/ui/report/report_providers.dart';
import 'package:arn_kasir/ui/report/report_widgets.dart';
import 'package:arn_kasir/ui/shared/l10n_context.dart';
import 'package:arn_kasir/ui/shared/shared_providers.dart';
import 'package:arn_kasir/ui/shared/widgets.dart';

/// Info Pemasukan (PRD 6.3.6): ringkasan, tren, menu terlaris, pembagian, dan daftar
/// transaksi untuk periode terpilih.
class IncomeScreen extends ConsumerWidget {
  const IncomeScreen({super.key});

  @override
  Widget build(BuildContext context, WidgetRef ref) {
    final l10n = context.l10n;
    final overview = ref.watch(incomeOverviewProvider);

    return Scaffold(
      appBar: AppBar(title: Text(l10n.incomeTitle)),
      body: Column(
        children: <Widget>[
          const Padding(
            padding: EdgeInsets.fromLTRB(16, 8, 16, 8),
            child: Align(alignment: Alignment.centerLeft, child: PeriodSelector()),
          ),
          Expanded(
            child: AsyncBody<IncomeOverview>(
              value: overview,
              builder: (data) => _IncomeBody(overview: data),
            ),
          ),
        ],
      ),
    );
  }
}

class _IncomeBody extends ConsumerWidget {
  const _IncomeBody({required this.overview});

  final IncomeOverview overview;

  @override
  Widget build(BuildContext context, WidgetRef ref) {
    final l10n = context.l10n;
    final fmt = ref.watch(formattersProvider);
    final range = ref.watch(periodRangeProvider);
    final summary = overview.report.summary;
    final theme = Theme.of(context);

    if (summary.transactionCount == 0 && overview.sales.isEmpty) {
      return EmptyState(
        icon: Icons.bar_chart_outlined,
        title: l10n.incomeEmptyTitle,
        message: l10n.incomeEmptyMessage,
      );
    }

    String methodLabel(BreakdownEntry entry) => entry.key == PaymentMethod.cash.dbValue
        ? l10n.paymentCash
        : l10n.paymentNonCash;

    String categoryLabel(BreakdownEntry entry) => entry.label ?? l10n.categoryNone;

    return ListView(
      padding: const EdgeInsets.fromLTRB(16, 0, 16, 24),
      children: <Widget>[
        StatGrid(
          children: <Widget>[
            StatCard(
              label: l10n.incomeNetSales,
              value: fmt.money(summary.netSales),
              emphasis: true,
              hint: summary.discounts == 0
                  ? null
                  : l10n.incomeDiscountHint(fmt.money(summary.discounts)),
            ),
            StatCard(
              label: l10n.incomeTransactions,
              value: fmt.integer(summary.transactionCount),
              hint: l10n.incomeAverageHint(fmt.money(summary.averagePerTransaction)),
            ),
            StatCard(label: l10n.incomeCogs, value: fmt.money(summary.costOfGoodsSold)),
            StatCard(label: l10n.incomeGrossProfit, value: fmt.money(summary.grossProfit)),
          ],
        ),
        const SizedBox(height: 20),
        _Section(
          title: overview.hourly ? l10n.incomeTrendHourly : l10n.incomeTrendDaily,
          child: TrendChart(points: overview.trend, range: range, hourly: overview.hourly),
        ),
        _Section(
          title: l10n.incomeTopMenus,
          child: overview.topMenus.isEmpty
              ? Text(l10n.incomeNoData, style: theme.textTheme.bodyMedium)
              : Column(
                  children: <Widget>[
                    for (var i = 0; i < overview.topMenus.length; i++)
                      ListTile(
                        contentPadding: EdgeInsets.zero,
                        leading: CircleAvatar(
                          radius: 14,
                          child: Text('${i + 1}', style: theme.textTheme.labelMedium),
                        ),
                        title: Text(overview.topMenus[i].menuName),
                        subtitle: Text(l10n.incomePortions(overview.topMenus[i].quantity)),
                        trailing: Text(fmt.money(overview.topMenus[i].revenue)),
                      ),
                  ],
                ),
        ),
        if (overview.byCategory.isNotEmpty)
          _Section(
            title: l10n.incomeByCategory,
            child: BreakdownList(entries: overview.byCategory, labelOf: categoryLabel),
          ),
        if (overview.byMethod.isNotEmpty)
          _Section(
            title: l10n.incomeByMethod,
            child: BreakdownList(entries: overview.byMethod, labelOf: methodLabel),
          ),
        _Section(
          title: l10n.incomeSaleList,
          child: Column(
            children: <Widget>[
              for (final sale in overview.sales) _SaleTile(sale: sale),
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
      padding: const EdgeInsets.only(bottom: 20),
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

class _SaleTile extends ConsumerWidget {
  const _SaleTile({required this.sale});

  final SaleSummary sale;

  @override
  Widget build(BuildContext context, WidgetRef ref) {
    final l10n = context.l10n;
    final fmt = ref.watch(formattersProvider);
    final theme = Theme.of(context);
    final voided = sale.status == SaleStatus.voided;
    final strike = voided
        ? TextStyle(
            decoration: TextDecoration.lineThrough,
            color: theme.colorScheme.onSurfaceVariant,
          )
        : null;

    return ListTile(
      contentPadding: EdgeInsets.zero,
      onTap: () => context.push(AppRoutes.saleDetailOf(sale.id)),
      title: Text(sale.number, style: strike),
      subtitle: Text(
        '${fmt.dateTime(sale.time)}  |  ${l10n.incomePortions(sale.itemCount)}  |  '
        '${sale.method == PaymentMethod.cash ? l10n.paymentCash : l10n.paymentNonCash}',
      ),
      trailing: Column(
        mainAxisAlignment: MainAxisAlignment.center,
        crossAxisAlignment: CrossAxisAlignment.end,
        children: <Widget>[
          Text(fmt.money(sale.total), style: strike),
          if (voided)
            Text(
              l10n.saleVoidedBadge,
              style: theme.textTheme.labelSmall?.copyWith(color: theme.colorScheme.error),
            ),
        ],
      ),
    );
  }
}
