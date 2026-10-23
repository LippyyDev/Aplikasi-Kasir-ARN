import 'dart:io';

import 'package:file_picker/file_picker.dart';
import 'package:flutter/material.dart';
import 'package:flutter_riverpod/flutter_riverpod.dart';
import 'package:path/path.dart' as p;

import 'package:arn_kasir/app/providers.dart';
import 'package:arn_kasir/app/restart_scope.dart';
import 'package:arn_kasir/data/backup/backup_failure.dart';
import 'package:arn_kasir/data/backup/backup_service.dart';
import 'package:arn_kasir/data/backup/file_picker_destination.dart';
import 'package:arn_kasir/data/backup/restore_service.dart';
import 'package:arn_kasir/ui/shared/l10n_context.dart';
import 'package:arn_kasir/ui/shared/shared_providers.dart';
import 'package:arn_kasir/ui/shared/widgets.dart';

/// Cadangan dan Pulihkan (PRD 6.3.9 dan 10). Cadangan berupa satu berkas `.arnbak`
/// yang disimpan ke lokasi pilihan pengguna; tidak ada pengiriman ke server.
class BackupScreen extends ConsumerStatefulWidget {
  const BackupScreen({super.key});

  @override
  ConsumerState<BackupScreen> createState() => _BackupScreenState();
}

class _BackupScreenState extends ConsumerState<BackupScreen> {
  bool _busy = false;

  Future<void> _backup() async {
    final l10n = context.l10n;
    setState(() => _busy = true);
    try {
      final result = await ref.read(backupServiceProvider).createTo(
            FilePickerBackupDestination(dialogTitle: l10n.backupPickTitle),
          );
      if (result == null) return; // Pengguna membatalkan pemilih lokasi.
      await ref.read(settingsProvider.notifier).markBackupDone(result.createdAt);
      await _notifyBackup(success: true, detail: result.fileName);
      if (mounted) showMessage(context, l10n.backupDone(result.fileName));
    } on Object catch (e) {
      await _notifyBackup(success: false, detail: null);
      if (mounted) showFailure(context, ref, e);
    } finally {
      if (mounted) setState(() => _busy = false);
    }
  }

  Future<void> _notifyBackup({required bool success, String? detail}) async {
    try {
      await ref
          .read(notificationCenterProvider)
          .notifyBackupResult(success: success, restore: false, detail: detail);
    } on Object {
      // Cadangan sudah selesai atau gagal; notifikasi hanya pelengkap.
    }
  }

  Future<void> _restore() async {
    final file = await _pickArchive();
    if (file == null || !mounted) return;

    setState(() => _busy = true);
    try {
      final service = ref.read(restoreServiceProvider);
      final preview = await service.inspect(file);
      if (!mounted) return;
      final confirmed = await _confirmRestore(preview);
      if (!confirmed || !mounted) return;
      await service.restore(preview);
      if (mounted) await _showRestartDialog(success: true);
    } on RestoreRolledBack {
      // Database sudah ditutup; data lama dikembalikan, tetapi aplikasi perlu dimulai ulang.
      if (mounted) await _showRestartDialog(success: false);
    } on Object catch (e) {
      if (mounted) showFailure(context, ref, e);
    } finally {
      if (mounted) setState(() => _busy = false);
    }
  }

  Future<File?> _pickArchive() async {
    final l10n = context.l10n;
    try {
      final result = await FilePicker.platform.pickFiles(
        dialogTitle: l10n.restorePickTitle,
        type: FileType.any,
      );
      final path = result?.files.single.path;
      if (path == null) return null;
      if (p.extension(path).toLowerCase() != '.${BackupService.extension}') {
        if (mounted) showMessage(context, l10n.restoreWrongExtension);
        return null;
      }
      return File(path);
    } on Object catch (e) {
      if (mounted) showFailure(context, ref, e);
      return null;
    }
  }

  Future<bool> _confirmRestore(RestorePreview preview) async {
    final l10n = context.l10n;
    final fmt = ref.read(formattersProvider);
    final manifest = preview.manifest;
    final result = await showDialog<bool>(
      context: context,
      builder: (context) => AlertDialog(
        title: Text(l10n.restorePreviewTitle),
        content: SingleChildScrollView(
          child: Column(
            mainAxisSize: MainAxisSize.min,
            crossAxisAlignment: CrossAxisAlignment.start,
            children: <Widget>[
              Text(l10n.restorePreviewCreated(fmt.dateTime(manifest.createdAt))),
              const SizedBox(height: 8),
              Text(l10n.restorePreviewCounts(
                preview.menuCount,
                preview.ingredientCount,
                preview.saleCount,
                preview.imageCount,
              )),
              Text(l10n.restorePreviewVersion(
                manifest.appVersion,
                manifest.schemaVersion,
              )),
              if (preview.needsMigration) ...<Widget>[
                const SizedBox(height: 8),
                Text(l10n.restoreMigrationNote),
              ],
              const SizedBox(height: 16),
              Text(
                l10n.restoreWarning,
                style: TextStyle(
                  color: Theme.of(context).colorScheme.error,
                  fontWeight: FontWeight.w600,
                ),
              ),
            ],
          ),
        ),
        actions: <Widget>[
          TextButton(
            onPressed: () => Navigator.of(context).pop(false),
            child: Text(l10n.actionCancel),
          ),
          FilledButton(
            style: FilledButton.styleFrom(
              backgroundColor: Theme.of(context).colorScheme.error,
              foregroundColor: Theme.of(context).colorScheme.onError,
            ),
            onPressed: () => Navigator.of(context).pop(true),
            child: Text(l10n.restoreConfirm),
          ),
        ],
      ),
    );
    return result ?? false;
  }

  Future<void> _showRestartDialog({required bool success}) {
    final l10n = context.l10n;
    return showDialog<void>(
      context: context,
      barrierDismissible: false,
      builder: (dialogContext) => PopScope(
        canPop: false,
        child: AlertDialog(
          icon: Icon(success ? Icons.check_circle_outline : Icons.warning_amber_outlined),
          title: Text(success ? l10n.restoreDoneTitle : l10n.restoreRolledBackTitle),
          content: Text(success ? l10n.restoreDoneBody : l10n.restoreRolledBackBody),
          actions: <Widget>[
            FilledButton(
              onPressed: () {
                Navigator.of(dialogContext).pop();
                RestartScope.restart(context);
              },
              child: Text(l10n.restoreRestartAction),
            ),
          ],
        ),
      ),
    );
  }

  @override
  Widget build(BuildContext context) {
    final l10n = context.l10n;
    final fmt = ref.watch(formattersProvider);
    final theme = Theme.of(context);
    final settings = ref.watch(settingsProvider);
    final last = settings.lastBackupAt;
    final overdue = settings.backupOverdue(DateTime.now().toUtc());

    return Scaffold(
      appBar: AppBar(title: Text(l10n.backupTitle)),
      body: Stack(
        children: <Widget>[
          ListView(
            padding: const EdgeInsets.all(16),
            children: <Widget>[
              Card(
                color: overdue ? theme.colorScheme.errorContainer : null,
                child: ListTile(
                  leading: Icon(
                    overdue ? Icons.warning_amber_outlined : Icons.history,
                    color: overdue ? theme.colorScheme.onErrorContainer : null,
                  ),
                  title: Text(l10n.backupLastLabel),
                  subtitle: Text(last == null ? l10n.backupNever : fmt.dateTime(last)),
                ),
              ),
              const SizedBox(height: 16),
              Text(l10n.backupCreateTitle, style: theme.textTheme.titleMedium),
              const SizedBox(height: 4),
              Text(l10n.backupCreateBody, style: theme.textTheme.bodyMedium),
              const SizedBox(height: 12),
              FilledButton.icon(
                icon: const Icon(Icons.backup_outlined),
                label: Text(l10n.backupCreateAction),
                onPressed: _busy ? null : _backup,
              ),
              const SizedBox(height: 8),
              _Note(icon: Icons.lock_open_outlined, text: l10n.backupUnencryptedWarning),
              const SizedBox(height: 28),
              Text(l10n.restoreTitle, style: theme.textTheme.titleMedium),
              const SizedBox(height: 4),
              Text(l10n.restoreBody, style: theme.textTheme.bodyMedium),
              const SizedBox(height: 12),
              OutlinedButton.icon(
                icon: const Icon(Icons.restore_outlined),
                label: Text(l10n.restoreAction),
                onPressed: _busy ? null : _restore,
              ),
              const SizedBox(height: 8),
              _Note(icon: Icons.shield_outlined, text: l10n.restoreSafetyNote),
            ],
          ),
          if (_busy)
            const ColoredBox(
              color: Color(0x66000000),
              child: Center(child: CircularProgressIndicator()),
            ),
        ],
      ),
    );
  }
}

class _Note extends StatelessWidget {
  const _Note({required this.icon, required this.text});

  final IconData icon;
  final String text;

  @override
  Widget build(BuildContext context) {
    final theme = Theme.of(context);
    return Row(
      crossAxisAlignment: CrossAxisAlignment.start,
      children: <Widget>[
        Icon(icon, size: 18, color: theme.colorScheme.outline),
        const SizedBox(width: 8),
        Expanded(
          child: Text(
            text,
            style: theme.textTheme.bodySmall?.copyWith(
              color: theme.colorScheme.onSurfaceVariant,
            ),
          ),
        ),
      ],
    );
  }
}
