import 'dart:io';

import 'package:drift/drift.dart';
import 'package:drift/native.dart';

/// Membuka database SQLite pada [file] di isolate latar belakang.
///
/// Mode WAL dipakai agar pembacaan (laporan, cadangan) tidak menghalangi penulisan.
QueryExecutor openAppConnection(File file) {
  return NativeDatabase.createInBackground(
    file,
    setup: (rawDb) {
      rawDb.execute('PRAGMA journal_mode = WAL;');
      rawDb.execute('PRAGMA foreign_keys = ON;');
    },
  );
}
