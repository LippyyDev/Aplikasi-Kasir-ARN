/// Galat cadangan dan pemulihan. Pesan untuk pengguna dipetakan di lapisan UI.
sealed class BackupFailure implements Exception {
  const BackupFailure();
}

/// Berkas bukan arsip ZIP yang valid atau isinya tidak lengkap.
final class InvalidBackupArchive extends BackupFailure {
  const InvalidBackupArchive(this.detail);
  final String detail;

  @override
  String toString() => 'InvalidBackupArchive($detail)';
}

/// Format manifest lebih baru daripada yang dipahami aplikasi ini.
final class UnsupportedBackupFormat extends BackupFailure {
  const UnsupportedBackupFormat(this.found);
  final int found;

  @override
  String toString() => 'UnsupportedBackupFormat($found)';
}

/// Isi berkas tidak cocok dengan *checksum* di manifest (TC K2).
final class BackupChecksumMismatch extends BackupFailure {
  const BackupChecksumMismatch(this.path);
  final String path;

  @override
  String toString() => 'BackupChecksumMismatch($path)';
}

/// Cadangan berasal dari versi skema yang lebih baru dari aplikasi (TC K3).
final class BackupSchemaTooNew extends BackupFailure {
  const BackupSchemaTooNew({required this.found, required this.supported});
  final int found;
  final int supported;

  @override
  String toString() => 'BackupSchemaTooNew(found: $found, supported: $supported)';
}

/// Database di dalam cadangan tidak dapat dibuka atau gagal pemeriksaan integritas.
final class BackupDatabaseCorrupt extends BackupFailure {
  const BackupDatabaseCorrupt(this.detail);
  final String detail;

  @override
  String toString() => 'BackupDatabaseCorrupt($detail)';
}

/// Galat berkas saat membuat atau menyalin cadangan.
final class BackupIoFailure extends BackupFailure {
  const BackupIoFailure(this.detail);
  final String detail;

  @override
  String toString() => 'BackupIoFailure($detail)';
}

/// Penukaran data gagal dan data lama sudah dikembalikan (PRD 10.5 langkah 6).
final class RestoreRolledBack extends BackupFailure {
  const RestoreRolledBack(this.cause);
  final Object cause;

  @override
  String toString() => 'RestoreRolledBack($cause)';
}
