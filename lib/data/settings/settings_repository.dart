import 'package:shared_preferences/shared_preferences.dart';

import 'package:arn_kasir/domain/menu/availability.dart';
import 'package:arn_kasir/domain/settings/app_settings.dart';

/// Membaca dan menulis [AppSettings] di `shared_preferences` (BACKLOG #10).
final class SettingsRepository {
  SettingsRepository(this._prefs);

  final SharedPreferences _prefs;

  static const String _theme = 'tema';
  static const String _dynamicColor = 'warna_dinamis';
  static const String _language = 'bahasa';
  static const String _stockPolicy = 'kebijakan_stok';
  static const String _shrinkage = 'susut_aktif';
  static const String _lowStock = 'notif_stok';
  static const String _reminderOn = 'pengingat_cadangan';
  static const String _reminderDays = 'pengingat_hari';
  static const String _lastBackup = 'cadangan_terakhir';

  AppSettings read() {
    final lastBackupMillis = _prefs.getInt(_lastBackup);
    final days = _prefs.getInt(_reminderDays);
    return AppSettings(
      theme: ThemePreference.fromStorage(_prefs.getString(_theme)),
      useDynamicColor: _prefs.getBool(_dynamicColor) ?? true,
      languageCode: _validLanguage(_prefs.getString(_language)),
      stockPolicy: StockPolicy.fromDb(_prefs.getString(_stockPolicy)),
      shrinkageEnabled: _prefs.getBool(_shrinkage) ?? true,
      lowStockNotifications: _prefs.getBool(_lowStock) ?? true,
      backupReminderEnabled: _prefs.getBool(_reminderOn) ?? true,
      backupReminderDays: (days == null || days < 1)
          ? AppSettings.defaultBackupReminderDays
          : days,
      lastBackupAt: lastBackupMillis == null
          ? null
          : DateTime.fromMillisecondsSinceEpoch(lastBackupMillis, isUtc: true),
    );
  }

  Future<void> write(AppSettings settings) async {
    await _prefs.setString(_theme, settings.theme.storageValue);
    await _prefs.setBool(_dynamicColor, settings.useDynamicColor);
    final language = settings.languageCode;
    if (language == null) {
      await _prefs.remove(_language);
    } else {
      await _prefs.setString(_language, language);
    }
    await _prefs.setString(_stockPolicy, settings.stockPolicy.dbValue);
    await _prefs.setBool(_shrinkage, settings.shrinkageEnabled);
    await _prefs.setBool(_lowStock, settings.lowStockNotifications);
    await _prefs.setBool(_reminderOn, settings.backupReminderEnabled);
    await _prefs.setInt(_reminderDays, settings.backupReminderDays);
    final last = settings.lastBackupAt;
    if (last != null) {
      await _prefs.setInt(_lastBackup, last.toUtc().millisecondsSinceEpoch);
    }
  }

  /// Pengaturan yang ikut berkas cadangan (`settings.json`). Waktu cadangan terakhir
  /// tidak disertakan karena mengacu pada perangkat ini, bukan pada isi data.
  Map<String, Object?> export() {
    final settings = read();
    return <String, Object?>{
      _theme: settings.theme.storageValue,
      _dynamicColor: settings.useDynamicColor,
      _language: settings.languageCode,
      _stockPolicy: settings.stockPolicy.dbValue,
      _shrinkage: settings.shrinkageEnabled,
      _lowStock: settings.lowStockNotifications,
      _reminderOn: settings.backupReminderEnabled,
      _reminderDays: settings.backupReminderDays,
    };
  }

  /// Menerapkan `settings.json` dari cadangan. Nilai yang tidak dikenal diabaikan agar
  /// cadangan dari versi aplikasi lain tetap dapat dipulihkan.
  Future<void> import(Map<String, Object?> json) async {
    final current = read();
    final days = json[_reminderDays];
    await write(
      AppSettings(
        theme: json[_theme] is String
            ? ThemePreference.fromStorage(json[_theme]! as String)
            : current.theme,
        useDynamicColor: json[_dynamicColor] is bool
            ? json[_dynamicColor]! as bool
            : current.useDynamicColor,
        languageCode: json.containsKey(_language)
            ? _validLanguage(json[_language] as String?)
            : current.languageCode,
        stockPolicy: json[_stockPolicy] is String
            ? StockPolicy.fromDb(json[_stockPolicy]! as String)
            : current.stockPolicy,
        shrinkageEnabled: json[_shrinkage] is bool
            ? json[_shrinkage]! as bool
            : current.shrinkageEnabled,
        lowStockNotifications: json[_lowStock] is bool
            ? json[_lowStock]! as bool
            : current.lowStockNotifications,
        backupReminderEnabled: json[_reminderOn] is bool
            ? json[_reminderOn]! as bool
            : current.backupReminderEnabled,
        backupReminderDays: (days is int && days >= 1) ? days : current.backupReminderDays,
        lastBackupAt: current.lastBackupAt,
      ),
    );
  }

  String? _validLanguage(String? code) =>
      SupportedLanguages.codes.contains(code) ? code : null;
}
