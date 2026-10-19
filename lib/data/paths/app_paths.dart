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
}
