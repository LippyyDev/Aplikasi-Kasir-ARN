import 'package:flutter/material.dart';
import 'package:flutter/services.dart';
import 'package:flutter_riverpod/flutter_riverpod.dart';

import 'package:arn_kasir/ui/shared/failure_messages.dart';
import 'package:arn_kasir/ui/shared/l10n_context.dart';
import 'package:arn_kasir/ui/shared/shared_providers.dart';

/// Keadaan kosong dengan ajakan bertindak (PRD 12).
class EmptyState extends StatelessWidget {
  const EmptyState({
    required this.icon,
    required this.title,
    this.message,
    this.action,
    super.key,
  });

  final IconData icon;
  final String title;
  final String? message;
  final Widget? action;

  @override
  Widget build(BuildContext context) {
    final theme = Theme.of(context);
    return Center(
      child: Padding(
        padding: const EdgeInsets.all(32),
        child: Column(
          mainAxisSize: MainAxisSize.min,
          children: <Widget>[
            Icon(icon, size: 56, color: theme.colorScheme.outline),
            const SizedBox(height: 16),
            Text(title, style: theme.textTheme.titleMedium, textAlign: TextAlign.center),
            if (message != null) ...<Widget>[
              const SizedBox(height: 8),
              Text(
                message!,
                style: theme.textTheme.bodyMedium?.copyWith(
                  color: theme.colorScheme.onSurfaceVariant,
                ),
                textAlign: TextAlign.center,
              ),
            ],
            if (action != null) ...<Widget>[const SizedBox(height: 20), action!],
          ],
        ),
      ),
    );
  }
}

/// Menampilkan isi [AsyncValue] dengan keadaan memuat dan galat yang seragam.
class AsyncBody<T> extends ConsumerWidget {
  const AsyncBody({required this.value, required this.builder, super.key});

  final AsyncValue<T> value;
  final Widget Function(T data) builder;

  @override
  Widget build(BuildContext context, WidgetRef ref) {
    return value.when(
      data: builder,
      loading: () => const Center(child: CircularProgressIndicator()),
      error: (error, _) => EmptyState(
        icon: Icons.error_outline,
        title: failureMessage(context.l10n, ref.watch(formattersProvider), error),
      ),
    );
  }
}

/// Dialog konfirmasi. Mengembalikan `true` bila pengguna menyetujui.
Future<bool> confirmDialog(
  BuildContext context, {
  required String title,
  required String message,
  required String confirmLabel,
  bool destructive = false,
}) async {
  final result = await showDialog<bool>(
    context: context,
    builder: (context) => AlertDialog(
      title: Text(title),
      content: Text(message),
      actions: <Widget>[
        TextButton(
          onPressed: () => Navigator.of(context).pop(false),
          child: Text(context.l10n.actionCancel),
        ),
        FilledButton(
          style: destructive
              ? FilledButton.styleFrom(
                  backgroundColor: Theme.of(context).colorScheme.error,
                  foregroundColor: Theme.of(context).colorScheme.onError,
                )
              : null,
          onPressed: () => Navigator.of(context).pop(true),
          child: Text(confirmLabel),
        ),
      ],
    ),
  );
  return result ?? false;
}

/// Menampilkan galat sebagai *snackbar* dengan pesan yang dapat dibaca pengguna.
void showFailure(BuildContext context, WidgetRef ref, Object error) {
  final message = failureMessage(context.l10n, ref.read(formattersProvider), error);
  ScaffoldMessenger.of(context)
    ..hideCurrentSnackBar()
    ..showSnackBar(SnackBar(content: Text(message)));
}

void showMessage(BuildContext context, String message) {
  ScaffoldMessenger.of(context)
    ..hideCurrentSnackBar()
    ..showSnackBar(SnackBar(content: Text(message)));
}

/// Hanya digit, untuk isian uang dalam Rupiah bulat.
final List<TextInputFormatter> digitsOnly = <TextInputFormatter>[
  FilteringTextInputFormatter.digitsOnly,
  LengthLimitingTextInputFormatter(12),
];

/// Isian desimal dengan koma atau titik (diparse persis oleh `Fraction.parse`).
final List<TextInputFormatter> decimalInput = <TextInputFormatter>[
  FilteringTextInputFormatter.allow(RegExp(r'[0-9.,]')),
  LengthLimitingTextInputFormatter(18),
];

/// Mem-parse isian uang. `null` bila kosong atau tidak valid.
int? parseMoney(String text) {
  final digits = text.trim();
  if (digits.isEmpty) return null;
  return int.tryParse(digits);
}
