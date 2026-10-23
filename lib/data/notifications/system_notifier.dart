import 'package:flutter_local_notifications/flutter_local_notifications.dart';

/// Kanal notifikasi sistem. Satu kanal per jenis agar pengguna dapat mengatur sendiri di
/// pengaturan Android.
enum NotificationChannelKind {
  stock('stok', 'Stok bahan'),
  backup('cadangan', 'Cadangan');

  const NotificationChannelKind(this.id, this.name);
  final String id;
  final String name;
}

/// Batas ke notifikasi sistem. Dipisahkan sebagai antarmuka agar logika yang memakainya
/// dapat diuji tanpa plugin.
abstract interface class SystemNotifier {
  Future<void> initialize();

  /// Meminta izin `POST_NOTIFICATIONS` (Android 13 ke atas). `true` bila diizinkan atau
  /// tidak perlu diminta.
  Future<bool> requestPermission();

  Future<void> show({
    required int id,
    required String title,
    required String body,
    required NotificationChannelKind channel,
  });
}

/// Implementasi dengan `flutter_local_notifications`.
final class LocalSystemNotifier implements SystemNotifier {
  final FlutterLocalNotificationsPlugin _plugin = FlutterLocalNotificationsPlugin();
  bool _ready = false;

  @override
  Future<void> initialize() async {
    try {
      await _plugin.initialize(
        const InitializationSettings(
          android: AndroidInitializationSettings('@mipmap/ic_launcher'),
        ),
      );
      _ready = true;
    } on Object {
      // Notifikasi sistem bersifat pelengkap; riwayat dalam aplikasi tetap berfungsi.
      _ready = false;
    }
  }

  @override
  Future<bool> requestPermission() async {
    final android = _plugin.resolvePlatformSpecificImplementation<
        AndroidFlutterLocalNotificationsPlugin>();
    if (android == null) return true;
    return await android.requestNotificationsPermission() ?? false;
  }

  @override
  Future<void> show({
    required int id,
    required String title,
    required String body,
    required NotificationChannelKind channel,
  }) async {
    if (!_ready) return;
    try {
      await _plugin.show(
        id,
        title,
        body,
        NotificationDetails(
          android: AndroidNotificationDetails(
            channel.id,
            channel.name,
            importance: Importance.defaultImportance,
            priority: Priority.defaultPriority,
          ),
        ),
      );
    } on Object {
      // Izin ditolak atau plugin gagal: abaikan, riwayat dalam aplikasi sudah tercatat.
    }
  }
}
