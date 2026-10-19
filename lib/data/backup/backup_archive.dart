import 'dart:convert';
import 'dart:typed_data';

import 'package:archive/archive.dart';
import 'package:crypto/crypto.dart';

import 'package:arn_kasir/data/backup/backup_failure.dart';
import 'package:arn_kasir/data/backup/backup_manifest.dart';

/// Isi arsip yang sudah lolos semua pemeriksaan keutuhan.
final class VerifiedBackup {
  const VerifiedBackup({required this.manifest, required this.files});

  final BackupManifest manifest;

  /// Isi berkas menurut path di arsip (`arn.db`, `settings.json`, `images/...`),
  /// tanpa `manifest.json`.
  final Map<String, Uint8List> files;

  Uint8List get database => files[BackupArchive.databaseName]!;

  Uint8List? get settings => files[BackupArchive.settingsName];

  Map<String, Uint8List> get images => <String, Uint8List>{
        for (final entry in files.entries)
          if (entry.key.startsWith(BackupArchive.imagesPrefix)) entry.key: entry.value,
      };
}

/// Pembaca dan penulis format `.arnbak` (PRD 10.2): ZIP berisi `manifest.json`, `arn.db`,
/// `settings.json`, dan `images/`.
abstract final class BackupArchive {
  static const String manifestName = 'manifest.json';
  static const String databaseName = 'arn.db';
  static const String settingsName = 'settings.json';
  static const String imagesPrefix = 'images/';

  static String checksum(List<int> bytes) => sha256.convert(bytes).toString();

  /// Menyusun arsip. [images] berkunci nama berkas polos (tanpa `images/`).
  static Uint8List encode({
    required String appVersion,
    required int schemaVersion,
    required DateTime createdAt,
    required Map<String, int> rowCounts,
    required Uint8List database,
    required Uint8List settings,
    required Map<String, Uint8List> images,
  }) {
    final entries = <String, Uint8List>{
      databaseName: database,
      settingsName: settings,
      for (final image in images.entries) '$imagesPrefix${image.key}': image.value,
    };
    final manifest = BackupManifest(
      appVersion: appVersion,
      schemaVersion: schemaVersion,
      createdAt: createdAt,
      rowCounts: rowCounts,
      checksums: <String, String>{
        for (final entry in entries.entries) entry.key: checksum(entry.value),
      },
    );

    final archive = Archive();
    final manifestBytes = Uint8List.fromList(utf8.encode(manifest.encode()));
    archive.addFile(ArchiveFile(manifestName, manifestBytes.length, manifestBytes));
    for (final entry in entries.entries) {
      archive.addFile(ArchiveFile(entry.key, entry.value.length, entry.value));
    }
    final encoded = ZipEncoder().encode(archive);
    if (encoded == null) {
      throw const BackupIoFailure('Gagal menyusun arsip ZIP');
    }
    return Uint8List.fromList(encoded);
  }

  /// Membaca arsip dan memverifikasi: ZIP valid, manifest terbaca, setiap berkas ada di
  /// manifest dan sebaliknya, path aman, serta *checksum* cocok (TC K2).
  static VerifiedBackup decode(Uint8List bytes) {
    final Archive archive;
    try {
      archive = ZipDecoder().decodeBytes(bytes, verify: true);
    } on Object catch (e) {
      throw InvalidBackupArchive('Bukan arsip ZIP yang valid: $e');
    }

    final raw = <String, Uint8List>{};
    for (final file in archive.files) {
      if (!file.isFile) continue;
      final name = file.name;
      if (!_isSafeName(name)) {
        throw InvalidBackupArchive('Nama berkas tidak diizinkan: $name');
      }
      if (raw.containsKey(name)) {
        throw InvalidBackupArchive('Berkas ganda: $name');
      }
      raw[name] = Uint8List.fromList(file.content as List<int>);
    }

    final manifestBytes = raw.remove(manifestName);
    if (manifestBytes == null) {
      throw const InvalidBackupArchive('manifest.json tidak ada');
    }
    final manifest = BackupManifest.decode(utf8.decode(manifestBytes));

    if (!raw.containsKey(databaseName)) {
      throw const InvalidBackupArchive('arn.db tidak ada');
    }
    // Berkas harus persis sama dengan yang tercatat di manifest.
    for (final name in raw.keys) {
      if (!manifest.checksums.containsKey(name)) {
        throw InvalidBackupArchive('Berkas tidak tercatat di manifest: $name');
      }
    }
    for (final entry in manifest.checksums.entries) {
      final content = raw[entry.key];
      if (content == null) {
        throw InvalidBackupArchive('Berkas hilang dari arsip: ${entry.key}');
      }
      if (checksum(content) != entry.value) {
        throw BackupChecksumMismatch(entry.key);
      }
    }
    return VerifiedBackup(manifest: manifest, files: raw);
  }

  /// Hanya `manifest.json`, `arn.db`, `settings.json`, dan `images/<nama polos>`.
  static bool _isSafeName(String name) {
    if (name == manifestName || name == databaseName || name == settingsName) {
      return true;
    }
    if (!name.startsWith(imagesPrefix)) return false;
    final rest = name.substring(imagesPrefix.length);
    return rest.isNotEmpty &&
        !rest.contains('/') &&
        !rest.contains(r'\') &&
        rest != '.' &&
        rest != '..';
  }
}
