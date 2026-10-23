import 'package:flutter/material.dart';
import 'package:flutter_riverpod/flutter_riverpod.dart';

import 'package:arn_kasir/app/providers.dart';
import 'package:arn_kasir/domain/settings/app_settings.dart';
import 'package:arn_kasir/ui/shared/l10n_context.dart';

/// Pilihan tema (PRD 6.3.2). Berlaku seketika.
Future<void> showThemeDialog(BuildContext context, WidgetRef ref) {
  return showDialog<void>(
    context: context,
    builder: (context) => Consumer(
      builder: (context, ref, _) {
        final l10n = context.l10n;
        final settings = ref.watch(settingsProvider);
        final controller = ref.read(settingsProvider.notifier);
        return AlertDialog(
          title: Text(l10n.drawerTheme),
          content: Column(
            mainAxisSize: MainAxisSize.min,
            children: <Widget>[
              for (final entry in <(ThemePreference, String)>[
                (ThemePreference.system, l10n.themeSystem),
                (ThemePreference.light, l10n.themeLight),
                (ThemePreference.dark, l10n.themeDark),
              ])
                _ChoiceTile(
                  label: entry.$2,
                  selected: settings.theme == entry.$1,
                  onTap: () => controller.update((s) => s.copyWith(theme: entry.$1)),
                ),
              const Divider(),
              SwitchListTile(
                title: Text(l10n.themeDynamicColor),
                subtitle: Text(l10n.themeDynamicColorHint),
                value: settings.useDynamicColor,
                onChanged: (value) =>
                    controller.update((s) => s.copyWith(useDynamicColor: value)),
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

/// Pilihan bahasa (PRD 6.3.3): Indonesia, Inggris, atau ikuti sistem.
Future<void> showLanguageDialog(BuildContext context, WidgetRef ref) {
  return showDialog<void>(
    context: context,
    builder: (context) => Consumer(
      builder: (context, ref, _) {
        final l10n = context.l10n;
        final current = ref.watch(settingsProvider.select((s) => s.languageCode));
        final controller = ref.read(settingsProvider.notifier);
        return AlertDialog(
          title: Text(l10n.drawerLanguage),
          content: Column(
            mainAxisSize: MainAxisSize.min,
            children: <Widget>[
              for (final entry in <(String?, String)>[
                (null, l10n.languageSystem),
                ('id', l10n.languageIndonesian),
                ('en', l10n.languageEnglish),
              ])
                _ChoiceTile(
                  label: entry.$2,
                  selected: current == entry.$1,
                  onTap: () => controller.update(
                    (s) => entry.$1 == null
                        ? s.copyWith(clearLanguage: true)
                        : s.copyWith(languageCode: entry.$1),
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

/// Pilihan tunggal yang tidak bergantung pada API `Radio` (berubah antarversi Flutter).
class _ChoiceTile extends StatelessWidget {
  const _ChoiceTile({required this.label, required this.selected, required this.onTap});

  final String label;
  final bool selected;
  final VoidCallback onTap;

  @override
  Widget build(BuildContext context) {
    return Semantics(
      inMutuallyExclusiveGroup: true,
      selected: selected,
      child: ListTile(
        leading: Icon(selected ? Icons.radio_button_checked : Icons.radio_button_unchecked),
        title: Text(label),
        onTap: onTap,
      ),
    );
  }
}
