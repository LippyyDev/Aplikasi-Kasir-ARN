import 'package:arn_kasir/domain/expenses/expense.dart';

final class ExpenseCategory {
  const ExpenseCategory({required this.id, required this.name});

  final int id;
  final String name;

  bool get isSystem => SystemExpenseCategory.isSystem(id);
}

/// Catatan pengeluaran yang sudah tersimpan.
final class ExpenseRecord {
  const ExpenseRecord({
    required this.id,
    required this.date,
    required this.categoryId,
    required this.categoryName,
    required this.amount,
    required this.source,
    this.note,
    this.referenceId,
  });

  final int id;
  final DateTime date;
  final int categoryId;
  final String categoryName;
  final int amount;
  final ExpenseSource source;
  final String? note;
  final int? referenceId;
}
