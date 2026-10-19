/// Jenis notifikasi dalam aplikasi (PRD 6.3.1).
enum NotificationType {
  lowStock('stok_menipis'),
  outOfStock('stok_habis'),
  backupReminder('pengingat_cadangan'),
  backupResult('hasil_cadangan');

  const NotificationType(this.dbValue);
  final String dbValue;

  static NotificationType fromDb(String value) {
    for (final type in values) {
      if (type.dbValue == value) return type;
    }
    throw ArgumentError.value(value, 'value', 'Jenis notifikasi tidak dikenal');
  }
}

final class AppNotification {
  const AppNotification({
    required this.id,
    required this.type,
    required this.title,
    required this.body,
    required this.time,
    required this.isRead,
    this.referenceId,
  });

  final int id;
  final NotificationType type;
  final String title;
  final String body;
  final DateTime time;
  final bool isRead;

  /// Id bahan untuk notifikasi stok.
  final int? referenceId;
}
