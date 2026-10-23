import 'package:dynamic_color/dynamic_color.dart';
import 'package:flutter/material.dart';
import 'package:flutter_localizations/flutter_localizations.dart';
import 'package:flutter_native_splash/flutter_native_splash.dart';
import 'package:flutter_riverpod/flutter_riverpod.dart';

import 'package:arn_kasir/app/providers.dart';
import 'package:arn_kasir/app/router.dart';
import 'package:arn_kasir/app/theme/app_theme.dart';
import 'package:arn_kasir/domain/settings/app_settings.dart';
import 'package:arn_kasir/l10n/generated/app_localizations.dart';
import 'package:arn_kasir/ui/shared/shared_providers.dart';

/// Akar aplikasi: tema, bahasa, dan router.
class ArnApp extends ConsumerStatefulWidget {
  const ArnApp({super.key});

  @override
  ConsumerState<ArnApp> createState() => _ArnAppState();
}

class _ArnAppState extends ConsumerState<ArnApp> {
  @override
  void initState() {
    super.initState();
    // Layar pertama baru dilepas setelah frame pertama tergambar, agar tidak ada kilatan
    // layar kosong antara splash dan beranda.
    WidgetsBinding.instance.addPostFrameCallback((_) {
      FlutterNativeSplash.remove();
      _remindBackup();
    });
  }

  Future<void> _remindBackup() async {
    try {
      await ref.read(notificationCenterProvider).remindBackupIfDue();
    } on Object {
      // Pengingat hanya pelengkap; kegagalannya tidak boleh mengganggu aplikasi.
    }
  }

  @override
  Widget build(BuildContext context) {
    final settings = ref.watch(settingsProvider);
    final language = ref.watch(effectiveLanguageProvider);
    final router = ref.watch(routerProvider);

    return DynamicColorBuilder(
      builder: (lightDynamic, darkDynamic) {
        final useDynamic = settings.useDynamicColor;
        return MaterialApp.router(
          onGenerateTitle: (context) => AppLocalizations.of(context).appName,
          debugShowCheckedModeBanner: false,
          theme: AppTheme.light(useDynamic ? lightDynamic?.harmonized() : null),
          darkTheme: AppTheme.dark(useDynamic ? darkDynamic?.harmonized() : null),
          themeMode: switch (settings.theme) {
            ThemePreference.system => ThemeMode.system,
            ThemePreference.light => ThemeMode.light,
            ThemePreference.dark => ThemeMode.dark,
          },
          locale: Locale(language),
          supportedLocales: AppLocalizations.supportedLocales,
          localizationsDelegates: const <LocalizationsDelegate<Object>>[
            AppLocalizations.delegate,
            GlobalMaterialLocalizations.delegate,
            GlobalWidgetsLocalizations.delegate,
            GlobalCupertinoLocalizations.delegate,
          ],
          routerConfig: router,
        );
      },
    );
  }
}
