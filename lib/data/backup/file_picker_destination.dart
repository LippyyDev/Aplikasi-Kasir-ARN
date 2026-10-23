import 'dart:typed_data';

import 'package:file_picker/file_picker.dart';

import 'package:arn_kasir/data/backup/backup_service.dart';

/// Menyimpan cadangan lewat pemilih lokasi sistem (Storage Access Framework), sehingga
/// pengguna memilih sendiri folder atau penyimpanan awan tanpa izin penyimpanan luas.
final class FilePickerBackupDestination implements BackupDestination {
  const FilePickerBackupDestination({required this.dialogTitle});

  final String dialogTitle;

  @override
  Future<String?> save({required String fileName, required Uint8List bytes}) {
    return FilePicker.platform.saveFile(
      dialogTitle: dialogTitle,
      fileName: fileName,
      bytes: bytes,
    );
  }
}
