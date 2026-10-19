import 'dart:convert';
import 'dart:io';

import 'package:arn_kasir/data/paths/app_paths.dart';

/// Fase jurnal penukaran data.
enum SwapPhase { swapping, committed }

/// Menukar database dan folder gambar secara atomik dengan jurnal (PRD 10.5 langkah 5
/// dan 6, TC K5).
///
/// Urutan: tulis jurnal `swapping`, sisihkan data lama menjadi `*.old`, pasang data baru
/// (`rename` di volume yang sama), tulis jurnal `committed`, hapus `*.old` dan jurnal.
/// Terputus sebelum `committed` berarti data lama dikembalikan; sesudahnya data baru
/// dipertahankan dan sisa berkas dibersihkan.
final class RestoreSwapper {
  const RestoreSwapper(this.paths);

  final AppPaths paths;

  List<String> get _dbFamily => <String>[
        paths.databaseFile.path,
        '${paths.databaseFile.path}-wal',
        '${paths.databaseFile.path}-shm',
      ];

  Future<void> swap({required File stagedDb, required Directory stagedImages}) async {
    await _writeJournal(SwapPhase.swapping);

    for (final path in _dbFamily) {
      await _moveIfExists(path, '$path${AppPaths.oldSuffix}');
    }
    await _moveIfExists(
      paths.imagesDir.path,
      '${paths.imagesDir.path}${AppPaths.oldSuffix}',
    );

    await stagedDb.rename(paths.databaseFile.path);
    await stagedImages.rename(paths.imagesDir.path);

    await _writeJournal(SwapPhase.committed);
    await _cleanupOld();
    await _deleteIfExists(paths.restoreJournal);
  }

  /// Mengembalikan data lama dari berkas `.old`. Aman dipanggil berulang.
  Future<void> rollback() async {
    for (final path in _dbFamily) {
      final old = '$path${AppPaths.oldSuffix}';
      if (await File(old).exists()) {
        await _deleteIfExists(File(path));
        await File(old).rename(path);
      }
    }
    final oldImages = Directory('${paths.imagesDir.path}${AppPaths.oldSuffix}');
    if (await oldImages.exists()) {
      await _deleteIfExists(paths.imagesDir);
      await oldImages.rename(paths.imagesDir.path);
    }
    await _deleteIfExists(paths.restoreJournal);
  }

  /// Dipanggil saat aplikasi dibuka, sebelum database dibuka.
  Future<void> recoverIfInterrupted() async {
    final journal = paths.restoreJournal;
    if (!await journal.exists()) return;

    var phase = SwapPhase.swapping;
    try {
      final decoded = jsonDecode(await journal.readAsString());
      if (decoded is Map<String, dynamic> &&
          decoded['phase'] == SwapPhase.committed.name) {
        phase = SwapPhase.committed;
      }
    } on Object {
      // Jurnal rusak berarti penulisannya terputus: perlakukan sebagai belum komit.
    }

    if (phase == SwapPhase.committed) {
      await _cleanupOld();
      await _deleteIfExists(journal);
    } else {
      await rollback();
    }
    await _deleteIfExists(paths.restoreStagingDir);
  }

  Future<void> _cleanupOld() async {
    for (final path in _dbFamily) {
      await _deleteIfExists(File('$path${AppPaths.oldSuffix}'));
    }
    await _deleteIfExists(Directory('${paths.imagesDir.path}${AppPaths.oldSuffix}'));
  }

  Future<void> _writeJournal(SwapPhase phase) async {
    final tmp = File('${paths.restoreJournal.path}.tmp');
    await tmp.writeAsString(jsonEncode(<String, Object?>{'phase': phase.name}), flush: true);
    await tmp.rename(paths.restoreJournal.path);
  }

  static Future<void> _moveIfExists(String from, String to) async {
    final type = FileSystemEntity.typeSync(from);
    if (type == FileSystemEntityType.notFound) return;
    final targetType = FileSystemEntity.typeSync(to);
    if (targetType == FileSystemEntityType.directory) {
      await Directory(to).delete(recursive: true);
    } else if (targetType != FileSystemEntityType.notFound) {
      await File(to).delete();
    }
    if (type == FileSystemEntityType.directory) {
      await Directory(from).rename(to);
    } else {
      await File(from).rename(to);
    }
  }

  static Future<void> _deleteIfExists(FileSystemEntity entity) async {
    if (await entity.exists()) await entity.delete(recursive: true);
  }
}
