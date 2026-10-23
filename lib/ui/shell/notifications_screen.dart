import 'package:flutter/material.dart';
import 'package:flutter_riverpod/flutter_riverpod.dart';

import 'package:arn_kasir/app/providers.dart';
import 'package:arn_kasir/domain/models/app_notification.dart';
import 'package:arn_kasir/ui/shared/l10n_context.dart';
import 'package:arn_kasir/ui/shared/shared_providers.dart';
import 'package:arn_kasir/ui/shared/widgets.dart';

/// Riwayat notifikasi dan pengaturannya (PRD 6.3.1).
class NotificationsScreen extends ConsumerWidget {
  const NotificationsScreen({super.key});

  @override
  Widget build(BuildContext context, WidgetRef ref) {
    final l10n = context.l10n;
    final notifications = ref.watch(notificationsProvider);
    final repository = ref.read(notificationRepositoryProvider);

    return Scaffold(
      appBar: AppBar(
        title: Text(l10n.drawerNotifications),
        actions: <Widget>[
          IconButton(
            icon: const Icon(Icons.done_all),
            tooltip: l10n.notifMarkAllRead,
            onPressed: repository.markAllRead,
          ),
          PopupMenuButton<String>(
            onSelected: (value) async {
              if (value == 'settings') {
                await _showSettings(context);
              } else if (value == 'clear') {
                final ok = await confirmDialog(
                  context,
                  title: l10n.notifClearTitle,
                  message: l10n.notifClearMessage,
                  confirmLabel: l10n.notifClear,
                  destructive: true,
                );
                if (ok) await repository.clear();
              }
            },
            itemBuilder: (context) => <PopupMenuEntry<String>>[
              PopupMenuItem<String>(value: 'settings', child: Text(l10n.notifSettings)),
              PopupMenuItem<String>(value: 'clear', child: Text(l10n.notifClear)),
            ],
          ),
        ],
      ),
      body: AsyncBody(
        value: notifications,
        builder: (items) {
          if (items.isEmpty) {
            return EmptyState(
              icon: Icons.notifications_none,
              title: l10n.notifEmptyTitle,
              message: l10n.notifEmptyMessage,
            );
          }
          return ListView.separated(
            itemCount: items.length,
            separatorBuilder: (_, __) => const Divider(height: 1),
            itemBuilder: (context, index) => _NotificationTile(item: items[index]),
          );
        },
      ),
    );
  }

  Future<void> _showSettings(BuildContext context) {
    return showDialog<void>(
      context: context,
      builder: (context) => Consumer(
        builder: (context, ref, _) {
          final l10n = context.l10n;
          final settings = ref.watch(settingsProvider);
          final controller = ref.read(settingsProvider.notifier);
          return AlertDialog(
            title: Text(l10n.notifSettings),
            content: Column(
              mainAxisSize: MainAxisSize.min,
              children: <Widget>[
                SwitchListTile(
                  title: Text(l10n.notifSettingStock),
                  value: settings.lowStockNotifications,
                  onChanged: (value) async {
                    if (value) {
                      await ref.read(systemNotifierProvider).requestPermission();
                    }
                    await controller.update((s) => s.copyWith(lowStockNotifications: value));
                  },
                ),
                SwitchListTile(
                  title: Text(l10n.notifSettingBackup),
                  subtitle: Text(l10n.notifSettingBackupDays(settings.backupReminderDays)),
                  value: settings.backupReminderEnabled,
                  onChanged: (value) async {
                    if (value) {
                      await ref.read(systemNotifierProvider).requestPermission();
                    }
                    await controller.update((s) => s.copyWith(backupReminderEnabled: value));
                  },
                ),
                if (settings.backupReminderEnabled)
                  Slider(
                    min: 1,
                    max: 30,
                    divisions: 29,
                    label: '${settings.backupReminderDays}',
                    value: settings.backupReminderDays.clamp(1, 30).toDouble(),
                    onChanged: (value) => controller.update(
                      (s) => s.copyWith(backupReminderDays: value.round()),
                    ),
                  ),
              ],
            ),
            actions: <Widget>[
              TextButton(
                onPressed: () => Navigator.of(context).pop(),
                child: Text(l10n.actionClose),
              ),
            ],
          );
        },
      ),
    );
  }
}

class _NotificationTile extends ConsumerWidget {
  const _NotificationTile({required this.item});

  final AppNotification item;

  @override
  Widget build(BuildContext context, WidgetRef ref) {
    final fmt = ref.watch(formattersProvider);
    final theme = Theme.of(context);
    final (icon, color) = switch (item.type) {
      NotificationType.lowStock => (Icons.warning_amber_rounded, theme.colorScheme.tertiary),
      NotificationType.outOfStock => (Icons.error_outline, theme.colorScheme.error),
      NotificationType.backupReminder => (Icons.backup_outlined, theme.colorScheme.secondary),
      NotificationType.backupResult => (Icons.cloud_done_outlined, theme.colorScheme.primary),
    };
    return ListTile(
      leading: Icon(icon, color: color),
      title: Text(
        item.title,
        style: TextStyle(fontWeight: item.isRead ? FontWeight.normal : FontWeight.bold),
      ),
      subtitle: Text('${item.body}\n${fmt.dateTime(item.time)}'),
      isThreeLine: item.body.isNotEmpty,
      trailing: item.isRead
          ? null
          : Icon(Icons.circle, size: 10, color: theme.colorScheme.primary),
      onTap: () => ref.read(notificationRepositoryProvider).markRead(item.id),
    );
  }
}
