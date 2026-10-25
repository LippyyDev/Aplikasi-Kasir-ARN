package com.example.kasirarn.data.repo

import com.example.kasirarn.R
import com.example.kasirarn.data.db.ArnDatabase
import com.example.kasirarn.data.db.NotifType
import com.example.kasirarn.data.db.NotificationEntity
import com.example.kasirarn.data.notify.NotifChannel
import com.example.kasirarn.data.notify.SystemNotifier
import com.example.kasirarn.data.notify.Texts
import com.example.kasirarn.data.notify.get
import com.example.kasirarn.data.settings.SettingsRepository
import com.example.kasirarn.domain.UnitConverter

class NotificationRepository(
    db: ArnDatabase,
    private val settings: SettingsRepository,
    private val notifier: SystemNotifier,
    private val texts: Texts,
    private val clock: Clock = Clock.System,
    /** False saat memuat data contoh: riwayat dicatat tetapi tidak ada notifikasi sistem. */
    private val postSystem: Boolean = true,
) {
    private val dao = db.notificationDao()
    private val txDao = db.transactionDao()

    fun observeAll() = dao.observeAll()
    fun observeUnreadCount() = dao.observeUnreadCount()
    suspend fun markRead(id: Long) = dao.markRead(id)
    suspend fun markAllRead() = dao.markAllRead()
    suspend fun clear() = dao.clear()

    /** Stok menipis atau habis: dicatat di riwayat dan, bila diizinkan, dikirim ke sistem. */
    suspend fun raiseStockAlert(event: AlertEvent) {
        val s = settings.current()
        val ing = event.ingredient
        val stock = UnitConverter.autoDisplay(ing.stokDasar.coerceAtLeast(0), ing.dimension())
        when (event.kind) {
            StockAlerts.Kind.LOW -> {
                if (!s.notifyLowStock) return
                val title = texts.get(R.string.notif_low_title)
                val body = texts.get(R.string.notif_low_body, arrayOf(ing.nama, stock))
                record(NotifType.LOW_STOCK, title, body, ing.id, NotifChannel.STOCK)
            }
            StockAlerts.Kind.OUT -> {
                if (!s.notifyOutOfStock) return
                val title = texts.get(R.string.notif_out_title)
                val body = texts.get(R.string.notif_out_body, arrayOf(ing.nama))
                record(NotifType.OUT_OF_STOCK, title, body, ing.id, NotifChannel.STOCK)
            }
        }
    }

    suspend fun raiseBackupResult(success: Boolean, detail: String) {
        if (!settings.current().notifyBackupResult) return
        val title = texts.get(if (success) R.string.notif_backup_ok_title else R.string.notif_backup_fail_title)
        record(NotifType.BACKUP_RESULT, title, detail, null, NotifChannel.BACKUP)
    }

    /** Dipanggil harian oleh WorkManager. Satu pengingat per hari paling banyak. */
    suspend fun raiseBackupReminderIfDue() {
        val s = settings.current()
        if (!s.notifyBackupReminder) return
        // Pemakai baru yang belum punya transaksi belum perlu diingatkan.
        if (txDao.countAll() == 0) return
        val now = clock.now()
        val last = s.lastBackupAt
        val dueAfter = s.backupReminderDays * 86_400_000L
        if (last != 0L && now - last < dueAfter) return
        val todayStart = DayMath.startOfDay(now)
        if (dao.countSince(NotifType.BACKUP_REMINDER, todayStart) > 0) return
        val title = texts.get(R.string.notif_reminder_title)
        val body = if (last == 0L) {
            texts.get(R.string.notif_reminder_never)
        } else {
            texts.get(R.string.notif_reminder_body, arrayOf((now - last) / 86_400_000L))
        }
        record(NotifType.BACKUP_REMINDER, title, body, null, NotifChannel.BACKUP)
    }

    private suspend fun record(type: String, title: String, body: String, refId: Long?, channel: NotifChannel) {
        val id = dao.insert(
            NotificationEntity(jenis = type, judul = title, isi = body, waktu = clock.now(), referensiId = refId),
        )
        if (postSystem) notifier.show(id.toInt(), channel, title, body)
    }
}
