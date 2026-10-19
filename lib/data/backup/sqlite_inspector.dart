import 'dart:io';

import 'package:sqlite3/sqlite3.dart';

import 'package:arn_kasir/data/backup/backup_failure.dart';

/// Hasil pemeriksaan berkas database SQLite.
final class SqliteInspection {
  const SqliteInspection({required this.userVersion, required this.rowCounts});

  /// `PRAGMA user_version`; Drift menyimpan `schemaVersion` di sini.
  final int userVersion;
  final Map<String, int> rowCounts;
}

/// Membuka berkas SQLite secara langsung (di luar Drift) untuk memeriksa salinan
/// cadangan. Seluruh pemanggilan API `sqlite3` dikumpulkan di sini agar perubahan versi
/// paket hanya menyentuh satu berkas.
abstract final class SqliteInspector {
  /// Memeriksa integritas lalu menghitung baris tiap tabel. Melempar
  /// [BackupDatabaseCorrupt] bila berkas tidak dapat dibuka atau tidak lolos
  /// `PRAGMA integrity_check`.
  static SqliteInspection inspect(File file) {
    if (!file.existsSync()) {
      throw const BackupDatabaseCorrupt('Berkas database tidak ada');
    }
    Database? db;
    try {
      db = sqlite3.open(file.path);
      final integrity = db.select('PRAGMA integrity_check');
      final verdict = integrity.isEmpty ? '' : '${integrity.first.values.first}';
      if (verdict != 'ok') throw BackupDatabaseCorrupt('integrity_check: $verdict');

      final version = db.select('PRAGMA user_version').first.values.first as int;

      final tables = db.select(
        "SELECT name FROM sqlite_master WHERE type = 'table' AND name NOT LIKE 'sqlite_%' ORDER BY name",
      );
      final counts = <String, int>{};
      for (final row in tables) {
        final name = row['name'] as String;
        final escaped = name.replaceAll('"', '""');
        counts[name] = db.select('SELECT COUNT(*) AS c FROM "$escaped"').first['c'] as int;
      }
      return SqliteInspection(userVersion: version, rowCounts: counts);
    } on SqliteException catch (e) {
      throw BackupDatabaseCorrupt('${e.message}');
    } finally {
      db?.dispose();
    }
  }
}
