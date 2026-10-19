import 'dart:convert';

import 'package:arn_kasir/data/backup/backup_failure.dart';

/// Isi `manifest.json` pada berkas `.arnbak` (PRD 10.2).
final class BackupManifest {
  const BackupManifest({
    required this.appVersion,
    required this.schemaVersion,
    required this.createdAt,
    required this.rowCounts,
    required this.checksums,
    this.formatVersion = currentFormatVersion,
  });

  /// Versi tata letak arsip (bukan versi skema database).
  static const int currentFormatVersion = 1;
  static const String appName = 'ARN Kasir';

  final int formatVersion;
  final String appVersion;
  final int schemaVersion;
  final DateTime createdAt;

  /// Jumlah baris per tabel pada `arn.db` di dalam cadangan.
  final Map<String, int> rowCounts;

  /// SHA 256 (heksadesimal) per berkas: `arn.db`, `settings.json`, `images/<nama>`.
  final Map<String, String> checksums;

  Map<String, Object?> toJson() => <String, Object?>{
        'app': appName,
        'formatVersion': formatVersion,
        'appVersion': appVersion,
        'schemaVersion': schemaVersion,
        'createdAt': createdAt.toUtc().toIso8601String(),
        'rowCounts': rowCounts,
        'files': checksums,
      };

  String encode() => const JsonEncoder.withIndent('  ').convert(toJson());

  /// Mem-parse manifest. Melempar [InvalidBackupArchive] bila bentuknya salah dan
  /// [UnsupportedBackupFormat] bila format lebih baru.
  factory BackupManifest.decode(String text) {
    final Object? decoded;
    try {
      decoded = jsonDecode(text);
    } on FormatException catch (e) {
      throw InvalidBackupArchive('manifest.json bukan JSON: ${e.message}');
    }
    if (decoded is! Map<String, dynamic>) {
      throw const InvalidBackupArchive('manifest.json bukan objek');
    }
    if (decoded['app'] != appName) {
      throw const InvalidBackupArchive('Berkas bukan cadangan ARN Kasir');
    }

    final format = decoded['formatVersion'];
    if (format is! int || format < 1) {
      throw const InvalidBackupArchive('formatVersion tidak valid');
    }
    if (format > currentFormatVersion) throw UnsupportedBackupFormat(format);

    final schema = decoded['schemaVersion'];
    final created = decoded['createdAt'];
    final appVersion = decoded['appVersion'];
    final counts = decoded['rowCounts'];
    final files = decoded['files'];
    if (schema is! int ||
        schema < 1 ||
        created is! String ||
        appVersion is! String ||
        counts is! Map<String, dynamic> ||
        files is! Map<String, dynamic>) {
      throw const InvalidBackupArchive('Bidang manifest tidak lengkap');
    }
    final createdAt = DateTime.tryParse(created);
    if (createdAt == null) {
      throw const InvalidBackupArchive('createdAt tidak valid');
    }

    final rowCounts = <String, int>{};
    for (final entry in counts.entries) {
      final value = entry.value;
      if (value is! int || value < 0) {
        throw InvalidBackupArchive('rowCounts.${entry.key} tidak valid');
      }
      rowCounts[entry.key] = value;
    }
    final checksums = <String, String>{};
    for (final entry in files.entries) {
      final value = entry.value;
      if (value is! String || !_sha256Hex.hasMatch(value)) {
        throw InvalidBackupArchive('checksum ${entry.key} tidak valid');
      }
      checksums[entry.key] = value;
    }

    return BackupManifest(
      formatVersion: format,
      appVersion: appVersion,
      schemaVersion: schema,
      createdAt: createdAt.toUtc(),
      rowCounts: rowCounts,
      checksums: checksums,
    );
  }

  static final RegExp _sha256Hex = RegExp(r'^[0-9a-f]{64}$');
}
