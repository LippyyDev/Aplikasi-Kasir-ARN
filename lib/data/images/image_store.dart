import 'dart:io';
import 'dart:typed_data';

import 'package:flutter_image_compress/flutter_image_compress.dart';
import 'package:path/path.dart' as p;

import 'package:arn_kasir/data/paths/app_paths.dart';

/// Penyimpanan foto menu dan logo usaha di penyimpanan internal aplikasi (PRD 6.3.4b).
///
/// Basis data hanya menyimpan nama berkas relatif; folder dapat dipindahkan (misalnya
/// saat pemulihan) tanpa mengubah baris data.
final class ImageStore {
  ImageStore(this._paths);

  /// Sisi terpanjang hasil kompresi, dalam piksel.
  static const int maxSide = 1024;
  static const int jpegQuality = 80;

  final AppPaths _paths;

  /// Mengompres [source] (berkas pilihan pengguna) dan menyimpannya dengan nama unik.
  /// Mengembalikan nama berkas relatif.
  Future<String> saveFromFile(File source, {String prefix = 'img'}) async {
    await _paths.imagesDir.create(recursive: true);
    final name = '${prefix}_${DateTime.now().toUtc().microsecondsSinceEpoch}.jpg';
    final target = File(p.join(_paths.imagesDir.path, name));

    final compressed = await FlutterImageCompress.compressWithFile(
      source.path,
      minWidth: maxSide,
      minHeight: maxSide,
      quality: jpegQuality,
      format: CompressFormat.jpeg,
    );
    if (compressed == null) {
      // Pemampat tidak dapat memproses berkas; salin apa adanya agar pengguna tidak
      // kehilangan foto, ekstensi mengikuti sumber.
      final fallback = File(p.join(
        _paths.imagesDir.path,
        '${prefix}_${DateTime.now().toUtc().microsecondsSinceEpoch}${p.extension(source.path)}',
      ));
      await source.copy(fallback.path);
      return p.basename(fallback.path);
    }
    await target.writeAsBytes(Uint8List.fromList(compressed), flush: true);
    return name;
  }

  /// Berkas untuk nama relatif [name], atau `null` bila tidak ada di disk.
  File? resolve(String? name) {
    if (name == null || name.isEmpty) return null;
    // Cegah path traversal dari data yang dipulihkan: hanya nama berkas polos.
    if (name != p.basename(name)) return null;
    final file = File(p.join(_paths.imagesDir.path, name));
    return file.existsSync() ? file : null;
  }

  Future<void> delete(String name) async {
    final file = resolve(name);
    if (file != null) await file.delete();
  }
}
