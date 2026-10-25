package com.example.kasirarn.data.notify

import android.Manifest
import android.annotation.SuppressLint
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.os.Build
import androidx.annotation.StringRes
import androidx.core.app.NotificationCompat
import androidx.core.app.NotificationManagerCompat
import androidx.core.content.ContextCompat
import com.example.kasirarn.MainActivity
import com.example.kasirarn.R

/** Menerjemahkan teks sesuai bahasa pilihan di dalam aplikasi (bukan bahasa sistem). */
fun interface Texts {
    fun get(@StringRes id: Int, args: Array<out Any>): String
}

fun Texts.get(@StringRes id: Int): String = get(id, emptyArray())

enum class NotifChannel(val id: String, @StringRes val nameRes: Int) {
    STOCK("stok", R.string.channel_stock),
    BACKUP("cadangan", R.string.channel_backup),
}

class SystemNotifier(private val context: Context, private val texts: Texts) {

    fun ensureChannels() {
        val manager = context.getSystemService(NotificationManager::class.java)
        NotifChannel.entries.forEach { ch ->
            manager.createNotificationChannel(
                NotificationChannel(ch.id, texts.get(ch.nameRes), NotificationManager.IMPORTANCE_DEFAULT),
            )
        }
    }

    /** Izin `POST_NOTIFICATIONS` (Android 13+) dan notifikasi aplikasi tidak dimatikan di sistem. */
    fun canPost(): Boolean {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU &&
            ContextCompat.checkSelfPermission(context, Manifest.permission.POST_NOTIFICATIONS) !=
            PackageManager.PERMISSION_GRANTED
        ) {
            return false
        }
        return NotificationManagerCompat.from(context).areNotificationsEnabled()
    }

    fun needsRuntimePermission(): Boolean =
        Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU &&
            ContextCompat.checkSelfPermission(context, Manifest.permission.POST_NOTIFICATIONS) !=
            PackageManager.PERMISSION_GRANTED

    @SuppressLint("MissingPermission")
    fun show(id: Int, channel: NotifChannel, title: String, body: String) {
        if (!canPost()) return
        val open = PendingIntent.getActivity(
            context,
            0,
            Intent(context, MainActivity::class.java).addFlags(Intent.FLAG_ACTIVITY_SINGLE_TOP or Intent.FLAG_ACTIVITY_CLEAR_TOP),
            PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT,
        )
        val n = NotificationCompat.Builder(context, channel.id)
            .setSmallIcon(R.drawable.ic_notification)
            .setContentTitle(title)
            .setContentText(body)
            .setStyle(NotificationCompat.BigTextStyle().bigText(body))
            .setContentIntent(open)
            .setAutoCancel(true)
            .build()
        NotificationManagerCompat.from(context).notify(id, n)
    }
}
