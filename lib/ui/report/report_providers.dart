import 'package:flutter_riverpod/flutter_riverpod.dart';

import 'package:arn_kasir/app/providers.dart';
import 'package:arn_kasir/domain/models/report_models.dart';

enum PeriodKind { today, week, month, custom }

/// Pilihan periode laporan (PRD 6.3.6): hari ini, 7 hari, bulan ini, atau rentang kustom.
final class PeriodSelection {
  const PeriodSelection(this.kind, {this.custom});

  final PeriodKind kind;
  final DateRange? custom;

  /// Rentang setengah terbuka `[from, to)` dalam waktu lokal pada [now].
  DateRange resolve(DateTime now) {
    final today = DateTime(now.year, now.month, now.day);
    switch (kind) {
      case PeriodKind.today:
        return DateRange(from: today, to: today.add(const Duration(days: 1)));
      case PeriodKind.week:
        return DateRange(
          from: today.subtract(const Duration(days: 6)),
          to: today.add(const Duration(days: 1)),
        );
      case PeriodKind.month:
        return DateRange(
          from: DateTime(now.year, now.month),
          to: DateTime(now.year, now.month + 1),
        );
      case PeriodKind.custom:
        return custom ??
            DateRange(from: today, to: today.add(const Duration(days: 1)));
    }
  }
}

/// Periode terpilih; dipakai bersama oleh laporan pemasukan dan pengeluaran.
final periodProvider = NotifierProvider<PeriodController, PeriodSelection>(
  PeriodController.new,
);

class PeriodController extends Notifier<PeriodSelection> {
  @override
  PeriodSelection build() => const PeriodSelection(PeriodKind.today);

  void select(PeriodKind kind) => state = PeriodSelection(kind, custom: state.custom);

  void setCustom(DateRange range) =>
      state = PeriodSelection(PeriodKind.custom, custom: range);
}

final periodRangeProvider = Provider<DateRange>((ref) {
  return ref.watch(periodProvider).resolve(DateTime.now());
});

final incomeOverviewProvider = StreamProvider<IncomeOverview>(
  (ref) => ref.watch(reportRepositoryProvider).watchIncome(ref.watch(periodRangeProvider)),
);

final expenseOverviewProvider = StreamProvider<ExpenseOverview>(
  (ref) => ref.watch(reportRepositoryProvider).watchExpenses(ref.watch(periodRangeProvider)),
);
