import 'dart:convert';
import 'dart:io';
import 'dart:typed_data';

import 'package:path/path.dart' as p;

import 'package:arn_kasir/data/backup/backup_archive.dart';
import 'package:arn_kasir/data/backup/backup_failure.dart';
import 'package:arn_kasir/data/backup/sqlite_inspector.dart';
import 'package:arn_kasir/data/database/app_database.dart';
import 'package:arn_kasir/data/paths/app_paths.dart';
import 'package:arn_kasir/domain/core/clock.dart';

/// Tujuan penyimpanan cadangan. Implementasi nyata memakai pemilih lokasi sistem
/// (`file_picker`); uji memakai folder biasa.
abstract interface class BackupDestination {
  /// Menyimpan [bytes] sebagai [fileName]. Mengembalikan lokasi/penanda hasil, atau
  /// `null` bila pengguna membatalkan.
  Future<String?> save({required String fileName, required Uint8List bytes});
}

/// Hasil pembuatan cadangan.
final class BackupResult {
  const BackupResult({
    required this.fileName,
    required this.sizeBytes,
    required this.createdAt,
    required this.location,
  });

  final String fileName;
  final int sizeBytes;
  final DateTime createdAt;

  /// Lokasi hasil dari [BackupDestination.save].
  final String location;
}

/// Membuat berkas `.arnbak` (PRD 10.3).
///
/// Langkah: salinan konsisten database (`VACUUM INTO`), kumpulkan gambar dan pengaturan,
/// susun ZIP, buka ulang dan cocokkan *checksum*, baru serahkan ke tujuan.
final class BackupService {
  BackupService({
    required this.database,
    required this.paths,
    required this.appVersion,
    required Map<String, Object?> Function() exportSettings,
    Clock? clock,
  })  : _exportSettings = exportSettings,
        _clock = clock ?? systemClock;

  final AppDatabase database;
  final AppPaths paths;
  final String appVersion;
  final Map<String, Object?> Function() _exportSettings;
  final Clock _clock;

  static const String extension = 'arnbak';

  /// Nama bawaan `ARN_Backup_<tanggal>_<jam>.arnbak` dalam waktu lokal.
  String suggestedFileName({String prefix = 'ARN_Backup'}) {
    final t = _clock().toLocal();
    String two(int v) => v.toString().padLeft(2, '0');
    return '${prefix}_${t.year}${two(t.month)}${two(t.day)}_${two(t.hour)}${two(t.minute)}.$extension';
  }

  /// Membuat arsip terverifikasi di memori. Tidak menulis ke tujuan akhir.
  Future<Uint8List> buildVerifiedArchive() async {
    final work = Directory(
      p.join(paths.workDir.path, 'backup_${_clock().microsecondsSinceEpoch}'),
    );
    try {
      await work.create(recursive: true);
      final snapshot = File(p.join(work.path, BackupArchive.databaseName));
      try {
        await database.vacuumInto(snapshot.path);
      } on Object catch (e) {
        throw BackupIoFailure('Gagal menyalin database: $e');
      }

      final inspection = SqliteInspector.inspect(snapshot);
      final databaseBytes = await snapshot.readAsBytes();

      final images = <String, Uint8List>{};
      if (await paths.imagesDir.exists()) {
        await for (final entity in paths.imagesDir.list(followLinks: false)) {
          if (entity is File) {
            images[p.basename(entity.path)] = await entity.readAsBytes();
          }
        }
      }

      final settingsBytes =
          Uint8List.fromList(utf8.encode(jsonEncode(_exportSettings())));

      final bytes = BackupArchive.encode(
        appVersion: appVersion,
        schemaVersion: inspection.userVersion,
        createdAt: _clock(),
        rowCounts: inspection.rowCounts,
        database: databaseBytes,
        settings: settingsBytes,
        images: images,
      );

      // Verifikasi: arsip yang baru dibuat harus lolos pembacaan yang sama dengan pemulihan.
      BackupArchive.decode(bytes);
      return bytes;
    } on FileSystemException catch (e) {
      throw BackupIoFailure(e.message);
    } finally {
      if (await work.exists()) await work.delete(recursive: true);
    }
  }

  /// Membuat cadangan dan menyerahkannya ke [destination]. Mengembalikan `null` bila
  /// pengguna membatalkan pemilih lokasi.
  Future<BackupResult?> createTo(BackupDestination destination) async {
    final bytes = await buildVerifiedArchive();
    final name = suggestedFileName();
    final location = await destination.save(fileName: name, bytes: bytes);
    if (location == null) return null;
    return BackupResult(
      fileName: name,
      sizeBytes: bytes.length,
      createdAt: _clock(),
      location: location,
    );
  }

  /// Cadangan pengaman otomatis di penyimpanan internal sebelum pemulihan (PRD 10.5
  /// langkah 4). Mengembalikan berkas yang dibuat.
  Future<File> createSafetyBackup() async {
    final bytes = await buildVerifiedArchive();
    await paths.safetyBackupsDir.create(recursive: true);
    final file = File(
      p.join(paths.safetyBackupsDir.path, suggestedFileName(prefix: 'ARN_Pengaman')),
    );
    await file.writeAsBytes(bytes, flush: true);
    await _pruneSafetyBackups(keep: 3);
    return file;
  }

  Future<void> _pruneSafetyBackups({required int keep}) async {
    final files = <File>[
      await for (final e in paths.safetyBackupsDir.list())
        if (e is File && e.path.endsWith('.$extension')) e,
    ]..sort((a, b) => b.path.compareTo(a.path));
    for (final stale in files.skip(keep)) {
      await stale.delete();
    }
  }
}
