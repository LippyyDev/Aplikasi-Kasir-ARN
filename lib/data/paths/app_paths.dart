import 'dart:io';

import 'package:path/path.dart' as p;
import 'package:path_provider/path_provider.dart';

/// Lokasi berkas aplikasi pada penyimpanan internal privat (PRD 11, Keamanan dan privasi).
final class AppPaths {
  const AppPaths({required this.supportDir, required this.tempDir});

  /// Menentukan lokasi dari path_provider.
  static Future<AppPaths> resolve() async {
    final support = await getApplicationSupportDirectory();
    final temp = await getTemporaryDirectory();
    return AppPaths(supportDir: support, tempDir: temp);
  }

  final Directory supportDir;
  final Directory tempDir;

  File get databaseFile => File(p.join(supportDir.path, 'arn.db'));

  /// Foto menu dan logo usaha. Basis kolom `foto_path` dan `logo_path` (nama berkas relatif).
  Directory get imagesDir => Directory(p.join(supportDir.path, 'images'));

  /// Cadangan pengaman otomatis sebelum pemulihan (PRD 10.5 langkah 4).
  Directory get safetyBackupsDir =>
      Directory(p.join(supportDir.path, 'safety_backups'));

  /// Folder kerja sementara untuk membuat dan memulihkan cadangan.
  Directory get workDir => Directory(p.join(tempDir.path, 'arn_work'));

  /// Area penahapan pemulihan. Berada di volume yang sama dengan data aplikasi supaya
  /// penukaran berkas berupa `rename` atomik, bukan salin.
  Directory get restoreStagingDir =>
      Directory(p.join(supportDir.path, 'restore_staging'));

  /// Penanda bahwa penukaran data sedang berlangsung. Bila ada saat aplikasi dibuka,
  /// proses sebelumnya terputus di tengah pemulihan (TC K5).
  File get restoreJournal => File(p.join(supportDir.path, 'restore.journal'));

  /// Nama cadangan data lama selama penukaran.
  static const String oldSuffix = '.old';
}
