import 'dart:ui';

import 'package:arn_kasir/data/notifications/system_notifier.dart';
import 'package:arn_kasir/data/repositories/notification_repository.dart';
import 'package:arn_kasir/domain/core/clock.dart';
import 'package:arn_kasir/domain/inventory/ingredient.dart';
import 'package:arn_kasir/domain/inventory/stock_change.dart';
import 'package:arn_kasir/domain/models/app_notification.dart';
import 'package:arn_kasir/domain/settings/app_settings.dart';
import 'package:arn_kasir/l10n/generated/app_localizations.dart';

/// Membuat notifikasi: riwayat dalam aplikasi (selalu) dan notifikasi sistem (bila
/// diizinkan). Teks ditulis dengan bahasa terpilih pada saat kejadian (PRD 6.3.1).
final class NotificationCenter {
  NotificationCenter({
    required NotificationRepository repository,
    required SystemNotifier system,
    required AppSettings Function() settings,
    required Locale Function() platformLocale,
    Clock? clock,
  })  : _repository = repository,
        _system = system,
        _settings = settings,
        _platformLocale = platformLocale,
        _clock = clock ?? systemClock;

  final NotificationRepository _repository;
  final SystemNotifier _system;
  final AppSettings Function() _settings;
  final Locale Function() _platformLocale;
  final Clock _clock;

  /// Jarak minimum antar pengingat cadangan.
  static const Duration reminderInterval = Duration(hours: 24);

  AppLocalizations get _l10n {
    final code = _settings().languageCode ?? _platformLocale().languageCode;
    final supported = SupportedLanguages.codes.contains(code) ? code : 'id';
    return lookupAppLocalizations(Locale(supported));
  }

  /// Peringatan stok dari transaksi atau opname. Hanya transisi memburuk yang sampai ke
  /// sini (lihat `detectAlert`), jadi tidak berulang pada tiap transaksi.
  Future<void> notifyStockAlerts(Iterable<StockAlert> alerts) async {
    if (!_settings().lowStockNotifications) return;
    final l10n = _l10n;
    for (final alert in alerts) {
      final isOut = alert.level == StockLevel.out;
      final title = isOut ? l10n.notifOutOfStockTitle : l10n.notifLowStockTitle;
      final body = isOut
          ? l10n.notifOutOfStockBody(alert.ingredientName)
          : l10n.notifLowStockBody(alert.ingredientName);
      await _repository.add(
        type: isOut ? NotificationType.outOfStock : NotificationType.lowStock,
        title: title,
        body: body,
        referenceId: alert.ingredientId,
      );
      await _system.show(
        id: alert.ingredientId * 10 + (isOut ? 2 : 1),
        title: title,
        body: body,
        channel: NotificationChannelKind.stock,
      );
    }
  }

  Future<void> notifyBackupResult({required bool success, required bool restore, String? detail}) async {
    final l10n = _l10n;
    final String title;
    if (restore) {
      title = success ? l10n.notifRestoreSuccessTitle : l10n.notifRestoreFailedTitle;
    } else {
      title = success ? l10n.notifBackupSuccessTitle : l10n.notifBackupFailedTitle;
    }
    final body = detail ?? '';
    await _repository.add(
      type: NotificationType.backupResult,
      title: title,
      body: body,
    );
    await _system.show(
      id: 9001,
      title: title,
      body: body,
      channel: NotificationChannelKind.backup,
    );
  }

  /// Membuat pengingat cadangan bila sudah jatuh tempo dan belum diingatkan dalam
  /// [reminderInterval]. Dipanggil saat aplikasi dibuka atau kembali ke depan.
  Future<void> remindBackupIfDue() async {
    final settings = _settings();
    final now = _clock();
    if (!settings.backupOverdue(now)) return;

    final last = await _repository.latestTimeOf(NotificationType.backupReminder);
    if (last != null && now.difference(last) < reminderInterval) return;

    final l10n = _l10n;
    final body = settings.lastBackupAt == null
        ? l10n.notifBackupReminderNever
        : l10n.notifBackupReminderBody(
            now.difference(settings.lastBackupAt!).inDays,
          );
    await _repository.add(
      type: NotificationType.backupReminder,
      title: l10n.notifBackupReminderTitle,
      body: body,
    );
    await _system.show(
      id: 9002,
      title: l10n.notifBackupReminderTitle,
      body: body,
      channel: NotificationChannelKind.backup,
    );
  }
}
