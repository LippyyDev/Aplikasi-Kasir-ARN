import 'package:flutter_riverpod/flutter_riverpod.dart';

import 'package:arn_kasir/app/providers.dart';
import 'package:arn_kasir/domain/models/expense_models.dart';
import 'package:arn_kasir/ui/report/report_providers.dart';

final expenseCategoriesProvider = StreamProvider<List<ExpenseCategory>>(
  (ref) => ref.watch(expenseRepositoryProvider).watchCategories(),
);

/// Filter kategori pada daftar pengeluaran; `null` berarti semua kategori.
final expenseCategoryFilterProvider =
    NotifierProvider<ExpenseCategoryFilter, int?>(ExpenseCategoryFilter.new);

class ExpenseCategoryFilter extends Notifier<int?> {
  @override
  int? build() => null;

  void select(int? categoryId) => state = categoryId;
}

final expenseRecordsProvider = StreamProvider<List<ExpenseRecord>>((ref) {
  return ref.watch(expenseRepositoryProvider).watchRecords(
        range: ref.watch(periodRangeProvider),
        categoryId: ref.watch(expenseCategoryFilterProvider),
      );
});
