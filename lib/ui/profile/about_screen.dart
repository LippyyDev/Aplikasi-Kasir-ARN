import 'package:flutter/material.dart';
import 'package:flutter_riverpod/flutter_riverpod.dart';

import 'package:arn_kasir/app/providers.dart';
import 'package:arn_kasir/data/database/app_database.dart';
import 'package:arn_kasir/domain/models/business_profile.dart';
import 'package:arn_kasir/ui/shared/l10n_context.dart';
import 'package:arn_kasir/ui/shared/logo_image.dart';

/// Tentang aplikasi (PRD 6.3.8): identitas, versi, skema database, lisensi, dan
/// pernyataan bahwa seluruh data hanya ada di perangkat.
class AboutScreen extends ConsumerWidget {
  const AboutScreen({super.key});

  @override
  Widget build(BuildContext context, WidgetRef ref) {
    final l10n = context.l10n;
    final theme = Theme.of(context);
    final profile = ref.watch(businessProfileProvider).value ?? const BusinessProfile();
    final version = ref.watch(appVersionProvider);
    final build = ref.watch(buildNumberProvider);

    return Scaffold(
      appBar: AppBar(title: Text(l10n.aboutTitle)),
      body: ListView(
        padding: const EdgeInsets.all(16),
        children: <Widget>[
          Center(child: LogoImage(logoPath: profile.logoPath, size: 96)),
          const SizedBox(height: 12),
          Text(
            l10n.appName,
            textAlign: TextAlign.center,
            style: theme.textTheme.headlineSmall,
          ),
          if (profile.name.isNotEmpty)
            Text(
              profile.name,
              textAlign: TextAlign.center,
              style: theme.textTheme.titleMedium,
            ),
          const SizedBox(height: 24),
          Card(
            child: Column(
              children: <Widget>[
                ListTile(
                  leading: const Icon(Icons.info_outline),
                  title: Text(l10n.aboutVersion),
                  trailing: Text('$version ($build)'),
                ),
                ListTile(
                  leading: const Icon(Icons.storage_outlined),
                  title: Text(l10n.aboutSchema),
                  trailing: Text('${AppDatabase.currentSchemaVersion}'),
                ),
                ListTile(
                  leading: const Icon(Icons.description_outlined),
                  title: Text(l10n.aboutLicenses),
                  trailing: const Icon(Icons.chevron_right),
                  onTap: () => showLicensePage(
                    context: context,
                    applicationName: l10n.appName,
                    applicationVersion: version,
                  ),
                ),
              ],
            ),
          ),
          const SizedBox(height: 16),
          Card(
            color: theme.colorScheme.secondaryContainer,
            child: Padding(
              padding: const EdgeInsets.all(16),
              child: Row(
                crossAxisAlignment: CrossAxisAlignment.start,
                children: <Widget>[
                  Icon(Icons.lock_outline, color: theme.colorScheme.onSecondaryContainer),
                  const SizedBox(width: 12),
                  Expanded(
                    child: Text(
                      l10n.aboutPrivacy,
                      style: TextStyle(color: theme.colorScheme.onSecondaryContainer),
                    ),
                  ),
                ],
              ),
            ),
          ),
        ],
      ),
    );
  }
}
