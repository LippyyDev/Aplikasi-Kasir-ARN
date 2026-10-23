import 'package:arn_kasir/domain/menu/availability.dart';

/// Preferensi tema (PRD 6.3.2).
enum ThemePreference {
  system('sistem'),
  light('terang'),
  dark('gelap');

  const ThemePreference(this.storageValue);
  final String storageValue;

  static ThemePreference fromStorage(String? value) {
    for (final preference in values) {
      if (preference.storageValue == value) return preference;
    }
    return ThemePreference.system;
  }
}

/// Bahasa aplikasi (PRD 6.3.3). `null` pada [AppSettings.languageCode] berarti mengikuti
/// bahasa sistem bila didukung, selain itu Indonesia.
abstract final class SupportedLanguages {
  static const List<String> codes = <String>['id', 'en'];
}

/// Pengaturan aplikasi yang disimpan di `shared_preferences`.
final class AppSettings {
  const AppSettings({
    this.theme = ThemePreference.system,
    this.useDynamicColor = false,
    this.languageCode,
    this.stockPolicy = StockPolicy.block,
    this.shrinkageEnabled = true,
    this.lowStockNotifications = true,
    this.backupReminderEnabled = true,
    this.backupReminderDays = defaultBackupReminderDays,
    this.lastBackupAt,
  });

  static const int defaultBackupReminderDays = 7;

  final ThemePreference theme;

  /// Warna dinamis Material You bila perangkat mendukung.
  final bool useDynamicColor;

  /// `id`, `en`, atau `null` (ikuti sistem).
  final String? languageCode;

  /// Kebijakan bila stok bahan kurang (PRD 15, asumsi bawaan: blokir).
  final StockPolicy stockPolicy;

  /// Mencatat selisih opname negatif sebagai pengeluaran `susut` (PRD 6.3.5).
  final bool shrinkageEnabled;

  final bool lowStockNotifications;
  final bool backupReminderEnabled;
  final int backupReminderDays;

  /// Waktu cadangan sukses terakhir (UTC). Tidak ikut dicadangkan.
  final DateTime? lastBackupAt;

  AppSettings copyWith({
    ThemePreference? theme,
    bool? useDynamicColor,
    String? languageCode,
    bool clearLanguage = false,
    StockPolicy? stockPolicy,
    bool? shrinkageEnabled,
    bool? lowStockNotifications,
    bool? backupReminderEnabled,
    int? backupReminderDays,
    DateTime? lastBackupAt,
  }) {
    return AppSettings(
      theme: theme ?? this.theme,
      useDynamicColor: useDynamicColor ?? this.useDynamicColor,
      languageCode: clearLanguage ? null : (languageCode ?? this.languageCode),
      stockPolicy: stockPolicy ?? this.stockPolicy,
      shrinkageEnabled: shrinkageEnabled ?? this.shrinkageEnabled,
      lowStockNotifications:
          lowStockNotifications ?? this.lowStockNotifications,
      backupReminderEnabled:
          backupReminderEnabled ?? this.backupReminderEnabled,
      backupReminderDays: backupReminderDays ?? this.backupReminderDays,
      lastBackupAt: lastBackupAt ?? this.lastBackupAt,
    );
  }

  /// Apakah pengingat cadangan sudah jatuh tempo pada [now].
  bool backupOverdue(DateTime now) {
    if (!backupReminderEnabled) return false;
    final last = lastBackupAt;
    if (last == null) return true;
    return now.difference(last) >= Duration(days: backupReminderDays);
  }
}
