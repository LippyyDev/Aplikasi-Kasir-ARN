import 'dart:ui';

import 'package:flutter_riverpod/flutter_riverpod.dart';

import 'package:arn_kasir/app/providers.dart';
import 'package:arn_kasir/domain/settings/app_settings.dart';
import 'package:arn_kasir/ui/shared/formatters.dart';

/// Kode bahasa yang berlaku: pilihan pengguna, atau bahasa sistem bila didukung,
/// selain itu Indonesia.
final effectiveLanguageProvider = Provider<String>((ref) {
  final chosen = ref.watch(settingsProvider.select((s) => s.languageCode));
  if (chosen != null) return chosen;
  final system = PlatformDispatcher.instance.locale.languageCode;
  return SupportedLanguages.codes.contains(system) ? system : 'id';
});

/// Pemformat angka dan tanggal mengikuti bahasa dan mata uang profil usaha.
final formattersProvider = Provider<AppFormatters>((ref) {
  final language = ref.watch(effectiveLanguageProvider);
  final currency = ref.watch(
    businessProfileProvider.select((p) => p.value?.currency ?? 'Rp'),
  );
  return AppFormatters(language, currency: currency);
});
