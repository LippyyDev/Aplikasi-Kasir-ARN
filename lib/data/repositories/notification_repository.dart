import 'package:drift/drift.dart';

import 'package:arn_kasir/data/database/app_database.dart';
import 'package:arn_kasir/data/database/mappers.dart';
import 'package:arn_kasir/domain/core/clock.dart';
import 'package:arn_kasir/domain/models/app_notification.dart';

/// Riwayat notifikasi dalam aplikasi (PRD 6.3.1).
final class NotificationRepository {
  NotificationRepository(this._db, {Clock? clock}) : _clock = clock ?? systemClock;

  final AppDatabase _db;
  final Clock _clock;

  Stream<List<AppNotification>> watchAll({int limit = 200}) {
    final query = _db.select(_db.appNotifications)
      ..orderBy(<OrderingTerm Function($AppNotificationsTable)>[
        (t) => OrderingTerm.desc(t.time),
        (t) => OrderingTerm.desc(t.id),
      ])
      ..limit(limit);
    return query.watch().map(
          (rows) => <AppNotification>[
            for (final r in rows)
              AppNotification(
                id: r.id,
                type: NotificationType.fromDb(r.type),
                title: r.title,
                body: r.body,
                time: fromDbTime(r.time),
                isRead: r.isRead,
                referenceId: r.referenceId,
              ),
          ],
        );
  }

  Stream<int> watchUnreadCount() {
    return _db.watchLoad<int>(
      <ResultSetImplementation<dynamic, dynamic>>[_db.appNotifications],
      () async {
        final row = await _db
            .customSelect(
              'SELECT COUNT(*) AS c FROM notifikasi WHERE dibaca = 0',
              readsFrom: <ResultSetImplementation<dynamic, dynamic>>{
                _db.appNotifications,
              },
            )
            .getSingle();
        return row.read<int>('c');
      },
    );
  }

  Future<int> add({
    required NotificationType type,
    required String title,
    required String body,
    int? referenceId,
  }) {
    return _db.into(_db.appNotifications).insert(
          AppNotificationsCompanion.insert(
            type: type.dbValue,
            title: title,
            body: body,
            time: toDbTime(_clock()),
            referenceId: Value(referenceId),
          ),
        );
  }

  /// Waktu notifikasi terbaru berjenis [type], atau `null` bila belum pernah ada.
  Future<DateTime?> latestTimeOf(NotificationType type) async {
    final row = await _db
        .customSelect(
          'SELECT MAX(waktu) AS t FROM notifikasi WHERE jenis = ?',
          variables: <Variable<Object>>[Variable.withString(type.dbValue)],
          readsFrom: <ResultSetImplementation<dynamic, dynamic>>{
            _db.appNotifications,
          },
        )
        .getSingle();
    final millis = row.readNullable<int>('t');
    return millis == null ? null : fromDbTime(millis);
  }

  Future<void> markRead(int id) =>
      (_db.update(_db.appNotifications)..where((t) => t.id.equals(id)))
          .write(const AppNotificationsCompanion(isRead: Value(true)));

  Future<void> markAllRead() =>
      (_db.update(_db.appNotifications)..where((t) => t.isRead.equals(false)))
          .write(const AppNotificationsCompanion(isRead: Value(true)));

  Future<void> clear() => _db.delete(_db.appNotifications).go();
}
