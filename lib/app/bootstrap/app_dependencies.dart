import 'package:intl/date_symbol_data_local.dart';
import 'package:package_info_plus/package_info_plus.dart';
import 'package:shared_preferences/shared_preferences.dart';

import 'package:arn_kasir/data/backup/restore_swapper.dart';
import 'package:arn_kasir/data/database/app_database.dart';
import 'package:arn_kasir/data/database/connection.dart';
import 'package:arn_kasir/data/notifications/system_notifier.dart';
import 'package:arn_kasir/data/paths/app_paths.dart';

/// Semua yang harus siap sebelum layar pertama tampil. Dibuat sekali per "nyawa"
/// aplikasi; setelah pemulihan data, dibuat ulang lewat `RestartScope.restart`.
final class AppDependencies {
  const AppDependencies({
    required this.paths,
    required this.database,
    required this.preferences,
    required this.systemNotifier,
    required this.appVersion,
    required this.buildNumber,
  });

  final AppPaths paths;
  final AppDatabase database;
  final SharedPreferences preferences;
  final SystemNotifier systemNotifier;
  final String appVersion;
  final String buildNumber;

  /// Urutan penting: pemulihan yang terputus dibereskan **sebelum** database dibuka
  /// (TC K5), baru kemudian koneksi dan pengaturan disiapkan.
  static Future<AppDependencies> load() async {
    final paths = await AppPaths.resolve();
    await paths.supportDir.create(recursive: true);
    await RestoreSwapper(paths).recoverIfInterrupted();
    await paths.imagesDir.create(recursive: true);

    final database = AppDatabase(openAppConnection(paths.databaseFile));
    final preferences = await SharedPreferences.getInstance();
    final info = await PackageInfo.fromPlatform();
    await initializeDateFormatting();

    final notifier = LocalSystemNotifier();
    await notifier.initialize();

    return AppDependencies(
      paths: paths,
      database: database,
      preferences: preferences,
      systemNotifier: notifier,
      appVersion: info.version,
      buildNumber: info.buildNumber,
    );
  }
}
