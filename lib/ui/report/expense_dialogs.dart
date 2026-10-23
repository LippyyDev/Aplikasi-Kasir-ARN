import 'package:flutter/material.dart';
import 'package:flutter_riverpod/flutter_riverpod.dart';

import 'package:arn_kasir/app/providers.dart';
import 'package:arn_kasir/domain/expenses/expense.dart';
import 'package:arn_kasir/domain/models/expense_models.dart';
import 'package:arn_kasir/l10n/generated/app_localizations.dart';
import 'package:arn_kasir/ui/report/expense_providers.dart';
import 'package:arn_kasir/ui/shared/l10n_context.dart';
import 'package:arn_kasir/ui/shared/shared_providers.dart';
import 'package:arn_kasir/ui/shared/widgets.dart';

/// Nama kategori untuk tampilan. Kategori bawaan sistem diterjemahkan; kategori buatan
/// pengguna ditampilkan apa adanya.
String expenseCategoryLabel(AppLocalizations l10n, int id, String name) {
  switch (id) {
    case SystemExpenseCategory.ingredients:
      return l10n.expenseCategoryIngredients;
    case SystemExpenseCategory.shrinkage:
      return l10n.expenseCategoryShrinkage;
    default:
      return name;
  }
}

/// Dialog tambah atau ubah pengeluaran manual.
Future<void> showExpenseEntryDialog(
  BuildContext context, {
  ExpenseRecord? existing,
}) {
  return showDialog<void>(
    context: context,
    builder: (_) => _ExpenseEntryDialog(existing: existing),
  );
}

class _ExpenseEntryDialog extends ConsumerStatefulWidget {
  const _ExpenseEntryDialog({this.existing});

  final ExpenseRecord? existing;

  @override
  ConsumerState<_ExpenseEntryDialog> createState() => _ExpenseEntryDialogState();
}

class _ExpenseEntryDialogState extends ConsumerState<_ExpenseEntryDialog> {
  late DateTime _date;
  int? _categoryId;
  late final TextEditingController _amount;
  late final TextEditingController _note;
  String? _error;
  bool _saving = false;

  @override
  void initState() {
    super.initState();
    final existing = widget.existing;
    _date = existing?.date.toLocal() ?? DateTime.now();
    _categoryId = existing?.categoryId;
    _amount = TextEditingController(text: existing == null ? '' : '${existing.amount}');
    _note = TextEditingController(text: existing?.note ?? '');
  }

  @override
  void dispose() {
    _amount.dispose();
    _note.dispose();
    super.dispose();
  }

  Future<void> _pickDate() async {
    final now = DateTime.now();
    final picked = await showDatePicker(
      context: context,
      initialDate: _date,
      firstDate: DateTime(now.year - 5),
      lastDate: DateTime(now.year + 1),
    );
    if (picked == null) return;
    setState(() {
      // Jam dipertahankan agar urutan di daftar tetap wajar.
      _date = DateTime(picked.year, picked.month, picked.day, _date.hour, _date.minute);
    });
  }

  Future<void> _save() async {
    final l10n = context.l10n;
    final amount = parseMoney(_amount.text);
    if (_categoryId == null) {
      setState(() => _error = l10n.expenseCategoryRequired);
      return;
    }
    if (amount == null || amount <= 0) {
      setState(() => _error = l10n.expenseAmountRequired);
      return;
    }
    setState(() {
      _error = null;
      _saving = true;
    });
    final repository = ref.read(expenseRepositoryProvider);
    try {
      final existing = widget.existing;
      if (existing == null) {
        await repository.addManual(
          date: _date,
          categoryId: _categoryId!,
          amount: amount,
          note: _note.text,
        );
      } else {
        await repository.updateManual(
          id: existing.id,
          date: _date,
          categoryId: _categoryId!,
          amount: amount,
          note: _note.text,
        );
      }
      if (mounted) Navigator.of(context).pop();
    } on Object catch (e) {
      if (!mounted) return;
      setState(() => _saving = false);
      showFailure(context, ref, e);
    }
  }

  Future<void> _delete() async {
    final l10n = context.l10n;
    final confirmed = await confirmDialog(
      context,
      title: l10n.expenseDeleteTitle,
      message: l10n.expenseDeleteMessage,
      confirmLabel: l10n.actionDelete,
      destructive: true,
    );
    if (!confirmed || !mounted) return;
    try {
      await ref.read(expenseRepositoryProvider).deleteManual(widget.existing!.id);
      if (mounted) Navigator.of(context).pop();
    } on Object catch (e) {
      if (mounted) showFailure(context, ref, e);
    }
  }

  @override
  Widget build(BuildContext context) {
    final l10n = context.l10n;
    final fmt = ref.watch(formattersProvider);
    final categories = (ref.watch(expenseCategoriesProvider).value ?? const <ExpenseCategory>[])
        .where((c) => !c.isSystem)
        .toList();

    return AlertDialog(
      title: Text(widget.existing == null ? l10n.expenseAddTitle : l10n.expenseEditTitle),
      content: SingleChildScrollView(
        child: Column(
          mainAxisSize: MainAxisSize.min,
          crossAxisAlignment: CrossAxisAlignment.start,
          children: <Widget>[
            InkWell(
              onTap: _pickDate,
              child: InputDecorator(
                decoration: InputDecoration(
                  labelText: l10n.expenseDate,
                  suffixIcon: const Icon(Icons.calendar_today_outlined),
                ),
                child: Text(fmt.date(_date)),
              ),
            ),
            const SizedBox(height: 12),
            DropdownButtonFormField<int>(
              initialValue: categories.any((c) => c.id == _categoryId) ? _categoryId : null,
              decoration: InputDecoration(labelText: l10n.expenseCategory),
              items: <DropdownMenuItem<int>>[
                for (final c in categories)
                  DropdownMenuItem<int>(value: c.id, child: Text(c.name)),
              ],
              onChanged: (value) => setState(() => _categoryId = value),
            ),
            const SizedBox(height: 12),
            TextField(
              controller: _amount,
              keyboardType: TextInputType.number,
              inputFormatters: digitsOnly,
              decoration: InputDecoration(
                labelText: l10n.expenseAmount,
                prefixText: '${fmt.currency} ',
              ),
            ),
            const SizedBox(height: 12),
            TextField(
              controller: _note,
              textCapitalization: TextCapitalization.sentences,
              decoration: InputDecoration(labelText: l10n.expenseNote),
            ),
            if (_error != null) ...<Widget>[
              const SizedBox(height: 12),
              Text(_error!, style: TextStyle(color: Theme.of(context).colorScheme.error)),
            ],
          ],
        ),
      ),
      actions: <Widget>[
        if (widget.existing != null)
          TextButton(
            style: TextButton.styleFrom(foregroundColor: Theme.of(context).colorScheme.error),
            onPressed: _saving ? null : _delete,
            child: Text(l10n.actionDelete),
          ),
        TextButton(
          onPressed: _saving ? null : () => Navigator.of(context).pop(),
          child: Text(l10n.actionCancel),
        ),
        FilledButton(
          onPressed: _saving ? null : _save,
          child: Text(l10n.actionSave),
        ),
      ],
    );
  }
}

/// Lembar kelola kategori pengeluaran: tambah, ganti nama, hapus.
Future<void> showExpenseCategoriesSheet(BuildContext context) {
  return showModalBottomSheet<void>(
    context: context,
    isScrollControlled: true,
    showDragHandle: true,
    builder: (_) => const _ExpenseCategoriesSheet(),
  );
}

class _ExpenseCategoriesSheet extends ConsumerWidget {
  const _ExpenseCategoriesSheet();

  @override
  Widget build(BuildContext context, WidgetRef ref) {
    final l10n = context.l10n;
    final categories = ref.watch(expenseCategoriesProvider).value ?? const <ExpenseCategory>[];

    return DraggableScrollableSheet(
      expand: false,
      initialChildSize: 0.6,
      maxChildSize: 0.9,
      builder: (context, controller) => Column(
        children: <Widget>[
          Padding(
            padding: const EdgeInsets.symmetric(horizontal: 16),
            child: Row(
              children: <Widget>[
                Expanded(
                  child: Text(
                    l10n.expenseCategoriesTitle,
                    style: Theme.of(context).textTheme.titleLarge,
                  ),
                ),
                FilledButton.tonalIcon(
                  icon: const Icon(Icons.add),
                  label: Text(l10n.actionAdd),
                  onPressed: () => _editName(context, ref),
                ),
              ],
            ),
          ),
          Expanded(
            child: ListView(
              controller: controller,
              children: <Widget>[
                for (final c in categories)
                  ListTile(
                    title: Text(expenseCategoryLabel(l10n, c.id, c.name)),
                    subtitle: c.isSystem ? Text(l10n.expenseCategorySystemHint) : null,
                    trailing: c.isSystem
                        ? const Icon(Icons.lock_outline)
                        : Row(
                            mainAxisSize: MainAxisSize.min,
                            children: <Widget>[
                              IconButton(
                                tooltip: l10n.actionRename,
                                icon: const Icon(Icons.edit_outlined),
                                onPressed: () => _editName(context, ref, existing: c),
                              ),
                              IconButton(
                                tooltip: l10n.actionDelete,
                                icon: const Icon(Icons.delete_outline),
                                onPressed: () => _delete(context, ref, c),
                              ),
                            ],
                          ),
                  ),
              ],
            ),
          ),
        ],
      ),
    );
  }

  Future<void> _editName(
    BuildContext context,
    WidgetRef ref, {
    ExpenseCategory? existing,
  }) async {
    final l10n = context.l10n;
    final controller = TextEditingController(text: existing?.name ?? '');
    final name = await showDialog<String>(
      context: context,
      builder: (context) => AlertDialog(
        title: Text(existing == null ? l10n.expenseCategoryAdd : l10n.actionRename),
        content: TextField(
          controller: controller,
          autofocus: true,
          textCapitalization: TextCapitalization.words,
          decoration: InputDecoration(labelText: l10n.expenseCategoryName),
        ),
        actions: <Widget>[
          TextButton(
            onPressed: () => Navigator.of(context).pop(),
            child: Text(l10n.actionCancel),
          ),
          FilledButton(
            onPressed: () => Navigator.of(context).pop(controller.text),
            child: Text(l10n.actionSave),
          ),
        ],
      ),
    );
    controller.dispose();
    if (name == null || !context.mounted) return;
    final repository = ref.read(expenseRepositoryProvider);
    try {
      if (existing == null) {
        await repository.addCategory(name);
      } else {
        await repository.renameCategory(existing.id, name);
      }
    } on Object catch (e) {
      if (context.mounted) showFailure(context, ref, e);
    }
  }

  Future<void> _delete(BuildContext context, WidgetRef ref, ExpenseCategory category) async {
    final l10n = context.l10n;
    final confirmed = await confirmDialog(
      context,
      title: l10n.expenseCategoryDeleteTitle(category.name),
      message: l10n.expenseCategoryDeleteMessage,
      confirmLabel: l10n.actionDelete,
      destructive: true,
    );
    if (!confirmed || !context.mounted) return;
    try {
      await ref.read(expenseRepositoryProvider).deleteCategory(category.id);
      if (ref.read(expenseCategoryFilterProvider) == category.id) {
        ref.read(expenseCategoryFilterProvider.notifier).select(null);
      }
    } on Object catch (e) {
      if (context.mounted) showFailure(context, ref, e);
    }
  }
}
