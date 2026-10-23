import 'package:flutter/material.dart';
import 'package:flutter_riverpod/flutter_riverpod.dart';
import 'package:go_router/go_router.dart';

import 'package:arn_kasir/app/providers.dart';
import 'package:arn_kasir/app/routes.dart';
import 'package:arn_kasir/ui/settings/appearance_dialogs.dart';
import 'package:arn_kasir/ui/shared/l10n_context.dart';
import 'package:arn_kasir/ui/shared/shared_providers.dart';

/// Laci samping (burger menu, PRD 6.3).
class AppDrawer extends ConsumerWidget {
  const AppDrawer({super.key});

  @override
  Widget build(BuildContext context, WidgetRef ref) {
    final l10n = context.l10n;
    final profile = ref.watch(businessProfileProvider).value;
    final unread = ref.watch(unreadNotificationCountProvider).value ?? 0;
    final lastBackup = ref.watch(settingsProvider.select((s) => s.lastBackupAt));
    final fmt = ref.watch(formattersProvider);
    final theme = Theme.of(context);

    void go(String route) {
      Navigator.of(context).pop();
      context.push(route);
    }

    return NavigationDrawer(
      children: <Widget>[
        Padding(
          padding: const EdgeInsets.fromLTRB(28, 24, 16, 12),
          child: Row(
            children: <Widget>[
              Image.asset('assets/branding/arn_logo.png', width: 48, height: 48),
              const SizedBox(width: 12),
              Expanded(
                child: Text(
                  (profile?.name.isNotEmpty ?? false) ? profile!.name : l10n.appName,
                  style: theme.textTheme.titleLarge,
                  maxLines: 2,
                  overflow: TextOverflow.ellipsis,
                ),
              ),
            ],
          ),
        ),
        const Divider(indent: 28, endIndent: 28),
        _item(
          icon: Icons.notifications_outlined,
          label: l10n.drawerNotifications,
          badge: unread,
          onTap: () => go(AppRoutes.notifications),
        ),
        _item(
          icon: Icons.brightness_6_outlined,
          label: l10n.drawerTheme,
          onTap: () {
            Navigator.of(context).pop();
            showThemeDialog(context, ref);
          },
        ),
        _item(
          icon: Icons.language,
          label: l10n.drawerLanguage,
          onTap: () {
            Navigator.of(context).pop();
            showLanguageDialog(context, ref);
          },
        ),
        const Divider(indent: 28, endIndent: 28),
        _item(
          icon: Icons.tune,
          label: l10n.drawerMenuSetup,
          onTap: () => go(AppRoutes.menuSetup),
        ),
        _item(
          icon: Icons.inventory_2_outlined,
          label: l10n.drawerIngredients,
          onTap: () => go(AppRoutes.ingredients),
        ),
        _item(
          icon: Icons.trending_up,
          label: l10n.drawerIncome,
          onTap: () => go(AppRoutes.incomeReport),
        ),
        _item(
          icon: Icons.trending_down,
          label: l10n.drawerExpense,
          onTap: () => go(AppRoutes.expenseReport),
        ),
        const Divider(indent: 28, endIndent: 28),
        _item(
          icon: Icons.backup_outlined,
          label: l10n.drawerBackup,
          subtitle: lastBackup == null
              ? l10n.drawerBackupNever
              : l10n.drawerBackupLast(fmt.dateTime(lastBackup)),
          warning: _isOverdue(ref),
          onTap: () => go(AppRoutes.backup),
        ),
        _item(
          icon: Icons.info_outline,
          label: l10n.drawerAbout,
          onTap: () => go(AppRoutes.about),
        ),
      ],
    );
  }

  bool _isOverdue(WidgetRef ref) =>
      ref.read(settingsProvider).backupOverdue(DateTime.now().toUtc());

  Widget _item({
    required IconData icon,
    required String label,
    required VoidCallback onTap,
    int badge = 0,
    String? subtitle,
    bool warning = false,
  }) {
    return _DrawerTile(
      icon: icon,
      label: label,
      onTap: onTap,
      badge: badge,
      subtitle: subtitle,
      warning: warning,
    );
  }
}

class _DrawerTile extends StatelessWidget {
  const _DrawerTile({
    required this.icon,
    required this.label,
    required this.onTap,
    required this.badge,
    required this.warning,
    this.subtitle,
  });

  final IconData icon;
  final String label;
  final VoidCallback onTap;
  final int badge;
  final String? subtitle;
  final bool warning;

  @override
  Widget build(BuildContext context) {
    final theme = Theme.of(context);
    return Padding(
      padding: const EdgeInsets.symmetric(horizontal: 12),
      child: ListTile(
        shape: RoundedRectangleBorder(borderRadius: BorderRadius.circular(28)),
        leading: Badge(
          isLabelVisible: badge > 0,
          label: Text('$badge'),
          child: Icon(icon),
        ),
        title: Text(label),
        subtitle: subtitle == null
            ? null
            : Text(
                subtitle!,
                style: warning ? TextStyle(color: theme.colorScheme.error) : null,
              ),
        onTap: onTap,
      ),
    );
  }
}
