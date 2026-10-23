import 'package:arn_kasir/data/settings/settings_repository.dart';
import 'package:arn_kasir/domain/menu/availability.dart';
import 'package:arn_kasir/domain/settings/app_settings.dart';
import 'package:flutter_test/flutter_test.dart';
import 'package:shared_preferences/shared_preferences.dart';

void main() {
  late SettingsRepository repository;

  Future<SettingsRepository> create([Map<String, Object> initial = const {}]) async {
    SharedPreferences.setMockInitialValues(initial);
    return SettingsRepository(await SharedPreferences.getInstance());
  }

  setUp(() async => repository = await create());

  group('SettingsRepository', () {
    test('nilai bawaan sesuai keputusan PRD', () {
      final s = repository.read();
      expect(s.theme, ThemePreference.system);
      expect(s.useDynamicColor, isFalse);
      expect(s.languageCode, isNull);
      expect(s.stockPolicy, StockPolicy.block);
      expect(s.shrinkageEnabled, isTrue);
      expect(s.lowStockNotifications, isTrue);
      expect(s.backupReminderEnabled, isTrue);
      expect(s.backupReminderDays, AppSettings.defaultBackupReminderDays);
      expect(s.lastBackupAt, isNull);
    });

    test('tulis lalu baca menghasilkan nilai yang sama', () async {
      final written = AppSettings(
        theme: ThemePreference.dark,
        useDynamicColor: true,
        languageCode: 'en',
        stockPolicy: StockPolicy.warn,
        shrinkageEnabled: false,
        lowStockNotifications: false,
        backupReminderEnabled: false,
        backupReminderDays: 14,
        lastBackupAt: DateTime.utc(2026, 10, 19, 3),
      );
      await repository.write(written);

      final read = repository.read();
      expect(read.theme, ThemePreference.dark);
      expect(read.useDynamicColor, isTrue);
      expect(read.languageCode, 'en');
      expect(read.stockPolicy, StockPolicy.warn);
      expect(read.shrinkageEnabled, isFalse);
      expect(read.lowStockNotifications, isFalse);
      expect(read.backupReminderEnabled, isFalse);
      expect(read.backupReminderDays, 14);
      expect(read.lastBackupAt, DateTime.utc(2026, 10, 19, 3));
    });

    test('bahasa tidak dikenal dan hari pengingat tidak valid kembali ke bawaan', () async {
      repository = await create(<String, Object>{'bahasa': 'fr', 'pengingat_hari': 0});
      final s = repository.read();
      expect(s.languageCode, isNull);
      expect(s.backupReminderDays, AppSettings.defaultBackupReminderDays);
    });

    test('ekspor tidak memuat waktu cadangan terakhir', () async {
      await repository.write(AppSettings(lastBackupAt: DateTime.utc(2026, 1, 1)));
      expect(repository.export().containsKey('cadangan_terakhir'), isFalse);
    });

    test('impor menerapkan nilai sah, mengabaikan yang asing, dan menjaga cadangan terakhir',
        () async {
      final last = DateTime.utc(2026, 10, 1);
      await repository.write(AppSettings(lastBackupAt: last));

      await repository.import(<String, Object?>{
        'tema': 'gelap',
        'bahasa': 'en',
        'kebijakan_stok': 'peringatkan',
        'pengingat_hari': 'bukan angka',
        'kunci_dari_versi_lain': true,
      });

      final s = repository.read();
      expect(s.theme, ThemePreference.dark);
      expect(s.languageCode, 'en');
      expect(s.backupReminderDays, AppSettings.defaultBackupReminderDays);
      expect(s.lastBackupAt, last);
    });
  });

  group('AppSettings.backupOverdue', () {
    final now = DateTime.utc(2026, 10, 19);

    test('belum pernah dicadangkan berarti jatuh tempo', () {
      expect(const AppSettings().backupOverdue(now), isTrue);
    });

    test('mengikuti jumlah hari pengingat', () {
      final s = AppSettings(
        backupReminderDays: 7,
        lastBackupAt: now.subtract(const Duration(days: 6)),
      );
      expect(s.backupOverdue(now), isFalse);
      expect(s.backupOverdue(now.add(const Duration(days: 1))), isTrue);
    });

    test('pengingat dimatikan tidak pernah jatuh tempo', () {
      expect(const AppSettings(backupReminderEnabled: false).backupOverdue(now), isFalse);
    });
  });
}
