import 'dart:convert';
import 'dart:io';
import 'dart:typed_data';

import 'package:archive/archive.dart';
import 'package:arn_kasir/data/backup/backup_archive.dart';
import 'package:arn_kasir/data/backup/backup_failure.dart';
import 'package:arn_kasir/data/backup/backup_service.dart';
import 'package:arn_kasir/data/backup/restore_service.dart';
import 'package:arn_kasir/data/backup/restore_swapper.dart';
import 'package:arn_kasir/data/backup/sqlite_inspector.dart';
import 'package:arn_kasir/data/database/app_database.dart';
import 'package:arn_kasir/data/database/mappers.dart';
import 'package:arn_kasir/data/paths/app_paths.dart';
import 'package:drift/drift.dart' show Value;
import 'package:drift/native.dart';
import 'package:flutter_test/flutter_test.dart';
import 'package:path/path.dart' as p;
import 'package:sqlite3/sqlite3.dart';

/// Satu "ponsel": folder data sendiri, database berkas, layanan cadangan dan pulihkan.
final class _Phone {
  _Phone._(this.root, this.paths, this.db);

  static Future<_Phone> create() async {
    final root = await Directory.systemTemp.createTemp('arn_backup_test_');
    final support = Directory(p.join(root.path, 'support'))..createSync();
    final temp = Directory(p.join(root.path, 'temp'))..createSync();
    final paths = AppPaths(supportDir: support, tempDir: temp);
    final db = AppDatabase(NativeDatabase(paths.databaseFile));
    // Memaksa pembuatan skema dan seed.
    await db.customSelect('SELECT 1').get();
    return _Phone._(root, paths, db);
  }

  final Directory root;
  final AppPaths paths;
  AppDatabase db;
  Map<String, Object?> settings = <String, Object?>{'tema': 'gelap'};
  Map<String, Object?>? appliedSettings;

  BackupService get backup => BackupService(
        database: db,
        paths: paths,
        appVersion: '1.0.0',
        exportSettings: () => settings,
        clock: () => DateTime.utc(2026, 10, 19, 3, 30),
      );

  RestoreService get restore => RestoreService(
        paths: paths,
        backupService: backup,
        closeDatabase: () => db.close(),
        applySettings: (json) async => appliedSettings = json,
      );

  Future<void> reopen() async {
    db = AppDatabase(NativeDatabase(paths.databaseFile));
    await db.customSelect('SELECT 1').get();
  }

  Future<void> seed() async {
    final category = await db.into(db.menuCategories).insert(
          MenuCategoriesCompanion.insert(name: 'Kopi'),
        );
    final ingredient = await db.into(db.ingredients).insert(
          IngredientsCompanion.insert(
            name: 'Kopi bubuk',
            dimension: 'massa',
            baseUnit: 'g',
            baseStock: const Value(1000),
            stockValue: const Value(200000),
          ),
        );
    final stamp = toDbTime(DateTime.utc(2026, 1, 1));
    final menu = await db.into(db.menus).insert(
          MenusCompanion.insert(
            name: 'Kopi Susu',
            price: 18000,
            categoryId: Value(category),
            photoPath: const Value('menu_1.jpg'),
            createdAt: stamp,
            updatedAt: stamp,
          ),
        );
    await db.into(db.recipeItems).insert(
          RecipeItemsCompanion.insert(
            menuId: menu,
            ingredientId: ingredient,
            baseQuantity: 15,
          ),
        );
    await paths.imagesDir.create(recursive: true);
    await File(p.join(paths.imagesDir.path, 'menu_1.jpg'))
        .writeAsBytes(Uint8List.fromList(List<int>.generate(2048, (i) => i % 251)));
  }

  Future<void> dispose() async {
    try {
      await db.close();
    } on Object {
      // sudah ditutup
    }
    await root.delete(recursive: true);
  }
}

Map<String, int> _counts(File dbFile) => SqliteInspector.inspect(dbFile).rowCounts;

void main() {
  late _Phone source;
  late _Phone target;

  setUp(() async {
    source = await _Phone.create();
    target = await _Phone.create();
  });

  tearDown(() async {
    await source.dispose();
    await target.dispose();
  });

  Future<File> writeBackup(_Phone phone, Directory into, {String name = 'a.arnbak'}) async {
    final bytes = await phone.backup.buildVerifiedArchive();
    return File(p.join(into.path, name))..writeAsBytesSync(bytes);
  }

  group('K1 cadangan lalu pulihkan di instalasi bersih', () {
    test('jumlah baris tiap tabel sama dan gambar ikut pulih', () async {
      await source.seed();
      final file = await writeBackup(source, target.root);

      final preview = await target.restore.inspect(file);
      expect(preview.menuCount, 1);
      expect(preview.ingredientCount, 1);
      expect(preview.saleCount, 0);
      expect(preview.imageCount, 1);
      expect(preview.needsMigration, isFalse);

      await target.restore.restore(preview);
      await target.reopen();

      final sourceCounts = await _snapshotCounts(source);
      final targetCounts = _counts(target.paths.databaseFile);
      expect(targetCounts, sourceCounts);
      expect(targetCounts['menu'], 1);
      expect(targetCounts['resep_item'], 1);

      final restoredImage = File(p.join(target.paths.imagesDir.path, 'menu_1.jpg'));
      expect(restoredImage.existsSync(), isTrue);
      expect(
        restoredImage.readAsBytesSync(),
        File(p.join(source.paths.imagesDir.path, 'menu_1.jpg')).readAsBytesSync(),
      );
      expect(target.appliedSettings, <String, Object?>{'tema': 'gelap'});
      expect(target.paths.restoreJournal.existsSync(), isFalse);
    });

    test('cadangan pengaman dibuat sebelum data lama diganti', () async {
      await source.seed();
      final file = await writeBackup(source, target.root);
      final preview = await target.restore.inspect(file);
      await target.restore.restore(preview);

      final safety = target.paths.safetyBackupsDir.listSync().whereType<File>().toList();
      expect(safety, hasLength(1));
      expect(safety.single.path, endsWith('.arnbak'));
      // Cadangan pengaman sendiri harus dapat dibaca kembali.
      BackupArchive.decode(safety.single.readAsBytesSync());
    });
  });

  group('K2 berkas diubah', () {
    test('satu bita diubah di tengah arsip ditolak', () async {
      await source.seed();
      final bytes = Uint8List.fromList(await source.backup.buildVerifiedArchive());
      bytes[bytes.length ~/ 2] ^= 0x01;
      final file = File(p.join(target.root.path, 'rusak.arnbak'))..writeAsBytesSync(bytes);

      await expectLater(target.restore.inspect(file), throwsA(isA<BackupFailure>()));
    });

    test('isi berubah tetapi ZIP tetap valid: ditolak oleh checksum', () async {
      await source.seed();
      final original = BackupArchive.decode(await source.backup.buildVerifiedArchive());

      // Susun ulang arsip dengan manifest asli tetapi isi database berbeda.
      final tampered = Uint8List.fromList(original.database)..[100] ^= 0xFF;
      final intact = Uint8List.fromList(await source.backup.buildVerifiedArchive());
      // Ganti isi arn.db di dalam arsip tanpa memperbarui manifest.
      final replaced = _replaceEntry(intact, BackupArchive.databaseName, tampered);

      expect(
        () => BackupArchive.decode(replaced),
        throwsA(isA<BackupChecksumMismatch>()),
      );
    });

    test('data lama tetap utuh setelah penolakan', () async {
      await target.seed();
      final before = await _snapshotCounts(target);
      final broken = File(p.join(target.root.path, 'bukan.arnbak'))
        ..writeAsBytesSync(<int>[1, 2, 3, 4]);
      await expectLater(target.restore.inspect(broken), throwsA(isA<InvalidBackupArchive>()));
      expect(await _snapshotCounts(target), before);
    });
  });

  group('K3 dan K4 versi skema', () {
    test('K3: cadangan dari skema lebih baru ditolak', () async {
      await source.seed();
      final original = BackupArchive.decode(await source.backup.buildVerifiedArchive());
      final newer = BackupArchive.encode(
        appVersion: '9.9.9',
        schemaVersion: AppDatabase.currentSchemaVersion + 1,
        createdAt: DateTime.utc(2026, 10, 19),
        rowCounts: original.manifest.rowCounts,
        database: original.database,
        settings: original.settings!,
        images: <String, Uint8List>{},
      );
      final file = File(p.join(target.root.path, 'baru.arnbak'))..writeAsBytesSync(newer);

      await expectLater(
        target.restore.inspect(file),
        throwsA(
          isA<BackupSchemaTooNew>()
              .having((e) => e.found, 'found', AppDatabase.currentSchemaVersion + 1)
              .having((e) => e.supported, 'supported', AppDatabase.currentSchemaVersion),
        ),
      );
    });

    test('manifest dan user_version database harus sama', () async {
      await source.seed();
      final original = BackupArchive.decode(await source.backup.buildVerifiedArchive());
      final lying = BackupArchive.encode(
        appVersion: '1.0.0',
        schemaVersion: 1,
        createdAt: DateTime.utc(2026, 10, 19),
        rowCounts: original.manifest.rowCounts,
        // Database kosong tanpa user_version.
        database: _emptySqliteBytes(),
        settings: original.settings!,
        images: <String, Uint8List>{},
      );
      final file = File(p.join(target.root.path, 'bohong.arnbak'))..writeAsBytesSync(lying);
      await expectLater(target.restore.inspect(file), throwsA(isA<BackupDatabaseCorrupt>()));
    });
  });

  group('K5 pemulihan terputus', () {
    late Directory support;
    setUp(() => support = target.paths.supportDir);

    Future<void> stageHalfSwap(String phase) async {
      await target.db.close();
      File(target.paths.databaseFile.path).writeAsStringSync('BARU');
      File('${target.paths.databaseFile.path}${AppPaths.oldSuffix}').writeAsStringSync('LAMA');
      Directory(target.paths.imagesDir.path).createSync(recursive: true);
      File(p.join(target.paths.imagesDir.path, 'baru.jpg')).writeAsStringSync('b');
      final oldImages = Directory('${target.paths.imagesDir.path}${AppPaths.oldSuffix}')
        ..createSync(recursive: true);
      File(p.join(oldImages.path, 'lama.jpg')).writeAsStringSync('l');
      target.paths.restoreJournal.writeAsStringSync(jsonEncode(<String, Object?>{'phase': phase}));
      expect(support.existsSync(), isTrue);
    }

    test('terputus sebelum komit: data lama dikembalikan', () async {
      await stageHalfSwap('swapping');
      await RestoreSwapper(target.paths).recoverIfInterrupted();

      expect(target.paths.databaseFile.readAsStringSync(), 'LAMA');
      expect(File(p.join(target.paths.imagesDir.path, 'lama.jpg')).existsSync(), isTrue);
      expect(File(p.join(target.paths.imagesDir.path, 'baru.jpg')).existsSync(), isFalse);
      expect(target.paths.restoreJournal.existsSync(), isFalse);
      expect(File('${target.paths.databaseFile.path}${AppPaths.oldSuffix}').existsSync(), isFalse);
    });

    test('terputus setelah komit: data baru dipertahankan, sisa dibersihkan', () async {
      await stageHalfSwap('committed');
      await RestoreSwapper(target.paths).recoverIfInterrupted();

      expect(target.paths.databaseFile.readAsStringSync(), 'BARU');
      expect(File(p.join(target.paths.imagesDir.path, 'baru.jpg')).existsSync(), isTrue);
      expect(Directory('${target.paths.imagesDir.path}${AppPaths.oldSuffix}').existsSync(), isFalse);
      expect(target.paths.restoreJournal.existsSync(), isFalse);
    });

    test('jurnal rusak dianggap belum komit', () async {
      await stageHalfSwap('swapping');
      target.paths.restoreJournal.writeAsStringSync('{ rusak');
      await RestoreSwapper(target.paths).recoverIfInterrupted();
      expect(target.paths.databaseFile.readAsStringSync(), 'LAMA');
    });

    test('tanpa jurnal tidak ada yang diubah', () async {
      await target.seed();
      await target.db.close();
      final before = target.paths.databaseFile.readAsBytesSync();
      await RestoreSwapper(target.paths).recoverIfInterrupted();
      expect(target.paths.databaseFile.readAsBytesSync(), before);
    });
  });

  group('K6 cadangan saat database menerima tulisan', () {
    test('salinan konsisten: pasangan baris dalam satu transaksi tidak terpisah', () async {
      await source.seed();

      Future<void> writePair(int i) => source.db.transaction(() async {
            await source.db.into(source.db.expenses).insert(
                  ExpensesCompanion.insert(
                    date: toDbTime(DateTime.utc(2026, 10, 19)),
                    categoryId: 3,
                    amount: 1000 + i,
                  ),
                );
            await source.db.into(source.db.appNotifications).insert(
                  AppNotificationsCompanion.insert(
                    type: 'hasil_cadangan',
                    title: 'T$i',
                    body: 'B$i',
                    time: i,
                  ),
                );
          });

      final writers = <Future<void>>[for (var i = 0; i < 60; i++) writePair(i)];
      final backupBytes = source.backup.buildVerifiedArchive();
      await Future.wait(writers);
      final archive = BackupArchive.decode(await backupBytes);

      final probe = File(p.join(source.root.path, 'probe.db'))
        ..writeAsBytesSync(archive.database);
      final counts = _counts(probe);
      expect(counts['pengeluaran'], counts['notifikasi']);
    });
  });
}

Future<Map<String, int>> _snapshotCounts(_Phone phone) async {
  final bytes = await phone.backup.buildVerifiedArchive();
  final probe = File(p.join(phone.root.path, 'snapshot_${bytes.length}.db'))
    ..writeAsBytesSync(BackupArchive.decode(bytes).database);
  return _counts(probe);
}

/// Berkas SQLite sah dengan `user_version` 0, jadi tidak cocok dengan manifest skema 1.
Uint8List _emptySqliteBytes() {
  final dir = Directory.systemTemp.createTempSync('arn_empty_');
  try {
    final file = File(p.join(dir.path, 'x.db'));
    final db = sqlite3.open(file.path)..execute('CREATE TABLE t (x INTEGER)');
    db.dispose();
    return file.readAsBytesSync();
  } finally {
    dir.deleteSync(recursive: true);
  }
}

/// Mengganti isi satu entri ZIP tanpa menyentuh manifest. ZIP hasilnya tetap valid
/// (CRC dihitung ulang), sehingga hanya *checksum* manifest yang dapat menangkapnya.
Uint8List _replaceEntry(Uint8List zip, String name, Uint8List content) {
  final source = ZipDecoder().decodeBytes(zip);
  final rebuilt = Archive();
  for (final file in source.files) {
    final bytes = file.name == name ? content : Uint8List.fromList(file.content as List<int>);
    rebuilt.addFile(ArchiveFile(file.name, bytes.length, bytes));
  }
  return Uint8List.fromList(ZipEncoder().encode(rebuilt)!);
}
