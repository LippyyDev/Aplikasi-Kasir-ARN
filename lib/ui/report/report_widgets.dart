import 'package:fl_chart/fl_chart.dart';
import 'package:flutter/material.dart';
import 'package:flutter_riverpod/flutter_riverpod.dart';

import 'package:arn_kasir/domain/models/report_models.dart';
import 'package:arn_kasir/ui/report/report_providers.dart';
import 'package:arn_kasir/ui/shared/formatters.dart';
import 'package:arn_kasir/ui/shared/l10n_context.dart';
import 'package:arn_kasir/ui/shared/shared_providers.dart';

/// Pemilih periode laporan: chip hari ini, 7 hari, bulan ini, dan rentang kustom.
class PeriodSelector extends ConsumerWidget {
  const PeriodSelector({super.key});

  @override
  Widget build(BuildContext context, WidgetRef ref) {
    final l10n = context.l10n;
    final fmt = ref.watch(formattersProvider);
    final selection = ref.watch(periodProvider);
    final range = ref.watch(periodRangeProvider);
    final controller = ref.read(periodProvider.notifier);

    Future<void> pickCustom() async {
      final now = DateTime.now();
      final picked = await showDateRangePicker(
        context: context,
        firstDate: DateTime(now.year - 5),
        lastDate: DateTime(now.year + 1),
        initialDateRange: selection.custom == null
            ? null
            : DateTimeRange(
                start: selection.custom!.from,
                end: selection.custom!.to.subtract(const Duration(days: 1)),
              ),
      );
      if (picked == null) return;
      controller.setCustom(
        DateRange(
          from: DateTime(picked.start.year, picked.start.month, picked.start.day),
          to: DateTime(picked.end.year, picked.end.month, picked.end.day)
              .add(const Duration(days: 1)),
        ),
      );
    }

    return Column(
      crossAxisAlignment: CrossAxisAlignment.start,
      children: <Widget>[
        SingleChildScrollView(
          scrollDirection: Axis.horizontal,
          child: Row(
            children: <Widget>[
              for (final entry in <(PeriodKind, String)>[
                (PeriodKind.today, l10n.periodToday),
                (PeriodKind.week, l10n.periodWeek),
                (PeriodKind.month, l10n.periodMonth),
              ])
                Padding(
                  padding: const EdgeInsets.only(right: 8),
                  child: ChoiceChip(
                    label: Text(entry.$2),
                    selected: selection.kind == entry.$1,
                    onSelected: (_) => controller.select(entry.$1),
                  ),
                ),
              ChoiceChip(
                avatar: const Icon(Icons.date_range, size: 18),
                label: Text(l10n.periodCustom),
                selected: selection.kind == PeriodKind.custom,
                onSelected: (_) => pickCustom(),
              ),
            ],
          ),
        ),
        const SizedBox(height: 4),
        Text(
          _rangeLabel(fmt, range),
          style: Theme.of(context).textTheme.bodySmall,
        ),
      ],
    );
  }

  static String _rangeLabel(AppFormatters fmt, DateRange range) {
    final last = range.to.subtract(const Duration(days: 1));
    final sameDay = range.from.year == last.year &&
        range.from.month == last.month &&
        range.from.day == last.day;
    return sameDay ? fmt.date(range.from) : '${fmt.date(range.from)} - ${fmt.date(last)}';
  }
}

/// Kartu angka ringkasan.
class StatCard extends StatelessWidget {
  const StatCard({
    required this.label,
    required this.value,
    this.emphasis = false,
    this.hint,
    super.key,
  });

  final String label;
  final String value;
  final bool emphasis;
  final String? hint;

  @override
  Widget build(BuildContext context) {
    final theme = Theme.of(context);
    return Card(
      color: emphasis ? theme.colorScheme.primaryContainer : null,
      child: Padding(
        padding: const EdgeInsets.all(14),
        child: Column(
          crossAxisAlignment: CrossAxisAlignment.start,
          mainAxisSize: MainAxisSize.min,
          children: <Widget>[
            Text(label, style: theme.textTheme.labelMedium),
            const SizedBox(height: 4),
            FittedBox(
              fit: BoxFit.scaleDown,
              alignment: Alignment.centerLeft,
              child: Text(
                value,
                style: theme.textTheme.titleLarge?.copyWith(fontWeight: FontWeight.bold),
              ),
            ),
            if (hint != null) ...<Widget>[
              const SizedBox(height: 2),
              Text(hint!, style: theme.textTheme.bodySmall),
            ],
          ],
        ),
      ),
    );
  }
}

/// Susunan kartu ringkasan dua kolom.
class StatGrid extends StatelessWidget {
  const StatGrid({required this.children, super.key});

  final List<Widget> children;

  @override
  Widget build(BuildContext context) {
    return LayoutBuilder(
      builder: (context, constraints) {
        final width = (constraints.maxWidth - 8) / 2;
        return Wrap(
          spacing: 8,
          runSpacing: 8,
          children: <Widget>[
            for (final child in children) SizedBox(width: width, child: child),
          ],
        );
      },
    );
  }
}

/// Grafik batang tren penjualan bersih per hari atau per jam.
///
/// `fl_chart` memakai `double` untuk sumbu; konversi hanya untuk menggambar, nilai
/// yang ditampilkan di tooltip tetap berasal dari `int` Rupiah.
class TrendChart extends ConsumerWidget {
  const TrendChart({required this.points, required this.range, required this.hourly, super.key});

  final List<TrendPoint> points;
  final DateRange range;
  final bool hourly;

  @override
  Widget build(BuildContext context, WidgetRef ref) {
    final fmt = ref.watch(formattersProvider);
    final theme = Theme.of(context);
    final byKey = <String, TrendPoint>{for (final p in points) p.key: p};

    // Lengkapi sumbu dengan hari atau jam tanpa penjualan supaya jarak antar batang jujur.
    final slots = <({String key, String label})>[];
    if (hourly) {
      final d = range.from;
      final prefix =
          '${d.year.toString().padLeft(4, '0')}-${d.month.toString().padLeft(2, '0')}-${d.day.toString().padLeft(2, '0')}';
      for (var h = 0; h < 24; h++) {
        final hh = h.toString().padLeft(2, '0');
        slots.add((key: '$prefix $hh', label: hh));
      }
    } else {
      var day = DateTime(range.from.year, range.from.month, range.from.day);
      while (day.isBefore(range.to)) {
        final key =
            '${day.year.toString().padLeft(4, '0')}-${day.month.toString().padLeft(2, '0')}-${day.day.toString().padLeft(2, '0')}';
        slots.add((key: key, label: fmt.dateShort(day)));
        day = DateTime(day.year, day.month, day.day + 1);
      }
    }

    var maxValue = 0;
    for (final p in points) {
      if (p.netSales > maxValue) maxValue = p.netSales;
    }
    if (maxValue == 0) maxValue = 1;

    final chart = SizedBox(
      height: 180,
      width: (slots.length * 34.0).clamp(280.0, 4000.0),
      child: BarChart(
        BarChartData(
          maxY: maxValue.toDouble() * 1.15,
          gridData: const FlGridData(show: false),
          borderData: FlBorderData(show: false),
          titlesData: FlTitlesData(
            leftTitles: const AxisTitles(sideTitles: SideTitles(showTitles: false)),
            rightTitles: const AxisTitles(sideTitles: SideTitles(showTitles: false)),
            topTitles: const AxisTitles(sideTitles: SideTitles(showTitles: false)),
            bottomTitles: AxisTitles(
              sideTitles: SideTitles(
                showTitles: true,
                reservedSize: 24,
                getTitlesWidget: (value, meta) {
                  final index = value.toInt();
                  if (index < 0 || index >= slots.length) return const SizedBox.shrink();
                  return SideTitleWidget(
                    meta: meta,
                    child: Text(slots[index].label, style: theme.textTheme.labelSmall),
                  );
                },
              ),
            ),
          ),
          barTouchData: BarTouchData(
            touchTooltipData: BarTouchTooltipData(
              getTooltipItem: (group, groupIndex, rod, rodIndex) {
                final slot = slots[group.x];
                final amount = byKey[slot.key]?.netSales ?? 0;
                return BarTooltipItem(
                  '${slot.label}\n${fmt.money(amount)}',
                  TextStyle(color: theme.colorScheme.onInverseSurface, fontSize: 12),
                );
              },
            ),
          ),
          barGroups: <BarChartGroupData>[
            for (var i = 0; i < slots.length; i++)
              BarChartGroupData(
                x: i,
                barRods: <BarChartRodData>[
                  BarChartRodData(
                    toY: (byKey[slots[i].key]?.netSales ?? 0).toDouble(),
                    width: 16,
                    color: theme.colorScheme.primary,
                    borderRadius: const BorderRadius.vertical(top: Radius.circular(4)),
                  ),
                ],
              ),
          ],
        ),
      ),
    );
    return SingleChildScrollView(scrollDirection: Axis.horizontal, child: chart);
  }
}

/// Daftar rincian dengan batang proporsional (pembagian per kategori, metode, dan
/// pengeluaran). Persentase dihitung dengan bilangan bulat.
class BreakdownList extends ConsumerWidget {
  const BreakdownList({required this.entries, required this.labelOf, super.key});

  final List<BreakdownEntry> entries;
  final String Function(BreakdownEntry entry) labelOf;

  @override
  Widget build(BuildContext context, WidgetRef ref) {
    final fmt = ref.watch(formattersProvider);
    final theme = Theme.of(context);
    var total = 0;
    for (final entry in entries) {
      total += entry.amount;
    }
    if (total == 0) total = 1;

    return Column(
      children: <Widget>[
        for (final entry in entries)
          Padding(
            padding: const EdgeInsets.symmetric(vertical: 6),
            child: Column(
              crossAxisAlignment: CrossAxisAlignment.start,
              children: <Widget>[
                Row(
                  children: <Widget>[
                    Expanded(child: Text(labelOf(entry))),
                    Text(
                      '${fmt.money(entry.amount)}  (${(entry.amount * 100 + total ~/ 2) ~/ total}%)',
                      style: theme.textTheme.bodyMedium,
                    ),
                  ],
                ),
                const SizedBox(height: 4),
                ClipRRect(
                  borderRadius: BorderRadius.circular(4),
                  child: LinearProgressIndicator(
                    // Hanya untuk menggambar panjang batang.
                    value: entry.amount / total,
                    minHeight: 8,
                  ),
                ),
              ],
            ),
          ),
      ],
    );
  }
}
