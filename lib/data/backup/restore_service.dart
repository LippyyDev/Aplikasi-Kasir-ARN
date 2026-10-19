import 'dart:convert';
import 'dart:io';
import 'dart:typed_data';

import 'package:path/path.dart' as p;

import 'package:arn_kasir/data/backup/backup_archive.dart';
import 'package:arn_kasir/data/backup/backup_failure.dart';
import 'package:arn_kasir/data/backup/backup_manifest.dart';
import 'package:arn_kasir/data/backup/backup_service.dart';
import 'package:arn_kasir/data/backup/restore_swapper.dart';
import 'package:arn_kasir/data/backup/sqlite_inspector.dart';
import 'package:arn_kasir/data/database/app_database.dart';
import 'package:arn_kasir/data/paths/app_paths.dart';

/// Ringkasan cadangan yang sudah divalidasi, ditampilkan sebelum pengguna mengonfirmasi.
final class RestorePreview {
  const RestorePreview._({
    required this.manifest,
    required this.menuCount,
    required this.ingredientCount,
    required this.saleCount,
    required this.imageCount,
    required this.needsMigration,
    required VerifiedBackup contents,
  }) : _contents = contents;

  final BackupManifest manifest;
  final int menuCount;
  final int ingredientCount;
  final int saleCount;
  final int imageCount;

  /// Cadangan berskema lebih lama; Drift menjalankan migrasi saat dibuka.
  final bool needsMigration;
  final VerifiedBackup _contents;
}

/// Pemulihan (PRD 10.5): validasi, cadangan pengaman, penukaran dengan jurnal, dan
/// pengembalian bila gagal.
final class RestoreService {
  RestoreService({
    required this.paths,
    required this.backupService,
    required this.closeDatabase,
    required this.applySettings,
  }) : _swapper = RestoreSwapper(paths);

  final AppPaths paths;
  final BackupService backupService;
  final RestoreSwapper _swapper;

  /// Menutup koneksi database sebelum berkasnya ditukar.
  final Future<void> Function() closeDatabase;

  /// Menerapkan `settings.json` hasil cadangan.
  final Future<void> Function(Map<String, Object?> settings) applySettings;

  /// Membaca dan memvalidasi berkas pilihan pengguna. Tidak mengubah data apa pun.
  Future<RestorePreview> inspect(File archiveFile) async {
    final Uint8List bytes;
    try {
      bytes = await archiveFile.readAsBytes();
    } on FileSystemException catch (e) {
      throw BackupIoFailure(e.message);
    }
    final verified = BackupArchive.decode(bytes);

    if (verified.manifest.schemaVersion > AppDatabase.currentSchemaVersion) {
      throw BackupSchemaTooNew(
        found: verified.manifest.schemaVersion,
        supported: AppDatabase.currentSchemaVersion,
      );
    }

    // Periksa database sungguhan di berkas sementara, bukan hanya percaya manifest.
    final probeDir = Directory(
      p.join(paths.workDir.path, 'probe_${DateTime.now().microsecondsSinceEpoch}'),
    );
    try {
      await probeDir.create(recursive: true);
      final probe = File(p.join(probeDir.path, BackupArchive.databaseName));
      await probe.writeAsBytes(verified.database, flush: true);
      final inspection = SqliteInspector.inspect(probe);
      if (inspection.userVersion != verified.manifest.schemaVersion) {
        throw const BackupDatabaseCorrupt(
          'Versi skema database tidak sama dengan manifest',
        );
      }
      return RestorePreview._(
        manifest: verified.manifest,
        menuCount: inspection.rowCounts['menu'] ?? 0,
        ingredientCount: inspection.rowCounts['bahan_baku'] ?? 0,
        saleCount: inspection.rowCounts['transaksi'] ?? 0,
        imageCount: verified.images.length,
        needsMigration:
            verified.manifest.schemaVersion < AppDatabase.currentSchemaVersion,
        contents: verified,
      );
    } finally {
      if (await probeDir.exists()) await probeDir.delete(recursive: true);
    }
  }

  /// Menjalankan pemulihan dari [preview] yang sudah dikonfirmasi pengguna.
  ///
  /// Urutan: cadangan pengaman, tahap berkas, tutup database, tukar dengan jurnal,
  /// terapkan pengaturan. Bila penukaran gagal, data lama dikembalikan dan
  /// [RestoreRolledBack] dilempar. Pemanggil harus memulai ulang aplikasi setelah sukses.
  Future<void> restore(RestorePreview preview) async {
    // Cadangan pengaman dibuat dari data yang masih terbuka.
    await backupService.createSafetyBackup();

    final staging = paths.restoreStagingDir;
    if (await staging.exists()) await staging.delete(recursive: true);
    await staging.create(recursive: true);

    final stagedDb = File(p.join(staging.path, BackupArchive.databaseName));
    final stagedImages = Directory(p.join(staging.path, 'images'));
    try {
      await stagedDb.writeAsBytes(preview._contents.database, flush: true);
      await stagedImages.create();
      for (final image in preview._contents.images.entries) {
        final name = image.key.substring(BackupArchive.imagesPrefix.length);
        await File(p.join(stagedImages.path, name)).writeAsBytes(image.value, flush: true);
      }
    } on Object catch (e) {
      await _deleteIfExists(staging);
      throw BackupIoFailure('Gagal menyiapkan berkas pemulihan: $e');
    }

    await closeDatabase();

    try {
      await _swapper.swap(stagedDb: stagedDb, stagedImages: stagedImages);
    } on Object catch (e) {
      await _swapper.rollback();
      await _deleteIfExists(staging);
      throw RestoreRolledBack(e);
    }

    await _deleteIfExists(staging);

    final settingsBytes = preview._contents.settings;
    if (settingsBytes != null) {
      try {
        final decoded = jsonDecode(utf8.decode(settingsBytes));
        if (decoded is Map<String, dynamic>) {
          await applySettings(Map<String, Object?>.from(decoded));
        }
      } on Object {
        // Pengaturan hanya kenyamanan; data utama sudah pulih.
      }
    }
  }

  static Future<void> _deleteIfExists(FileSystemEntity entity) async {
    if (await entity.exists()) await entity.delete(recursive: true);
  }
}
