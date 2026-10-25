package com.example.kasirarn

import android.app.Application
import android.content.Context
import android.content.res.Configuration
import androidx.work.CoroutineWorker
import androidx.work.ExistingPeriodicWorkPolicy
import androidx.work.PeriodicWorkRequestBuilder
import androidx.work.WorkManager
import androidx.work.WorkerParameters
import com.example.kasirarn.data.backup.BackupManager
import com.example.kasirarn.data.db.ArnDatabase
import com.example.kasirarn.data.dummy.DummyDataLoader
import com.example.kasirarn.data.notify.SystemNotifier
import com.example.kasirarn.data.notify.Texts
import com.example.kasirarn.data.repo.ExpenseRepository
import com.example.kasirarn.data.repo.ImageStore
import com.example.kasirarn.data.repo.IngredientRepository
import com.example.kasirarn.data.repo.MenuRepository
import com.example.kasirarn.data.repo.NotificationRepository
import com.example.kasirarn.data.repo.ProfileRepository
import com.example.kasirarn.data.repo.ReportRepository
import com.example.kasirarn.data.repo.SalesRepository
import com.example.kasirarn.data.settings.AppLanguage
import com.example.kasirarn.data.settings.AppSettings
import com.example.kasirarn.data.settings.SettingsRepository
import com.example.kasirarn.ui.home.CartState
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.launch
import java.util.Locale
import java.util.concurrent.TimeUnit

/** Penyedia dependensi manual: satu instans per proses, dibuat oleh [ArnApp]. */
class AppContainer(private val app: Context) {
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.Default)

    val settings = SettingsRepository(app)

    @Volatile
    private var language: AppLanguage = AppLanguage.ID

    val texts = Texts { id, args -> localizedContext(app, language).getString(id, *args) }

    val db: ArnDatabase by lazy { ArnDatabase.build(app) }
    val notifier = SystemNotifier(app, texts)
    val images = ImageStore(app)
    val notifications by lazy { NotificationRepository(db, settings, notifier, texts) }
    val ingredients by lazy { IngredientRepository(db, notifications) }
    val menus by lazy { MenuRepository(db) }
    val sales by lazy { SalesRepository(db, settings, notifications) }
    val reports by lazy { ReportRepository(db) }
    val expenses by lazy { ExpenseRepository(db) }
    val profile by lazy { ProfileRepository(db) }
    val backup by lazy { BackupManager(app, db, settings, images) }
    val dummy by lazy { DummyDataLoader(app, db, settings, texts) }
    val cart = CartState()

    /** Pengaturan yang sudah terbaca saat [ready] menjadi true, supaya layar pertama tidak berkedip. */
    @Volatile
    var initialSettings: AppSettings = AppSettings()
        private set

    private val _ready = MutableStateFlow(false)

    /** True setelah pengaturan terbaca dan database terbuka; splash ditahan sampai saat ini. */
    val ready: StateFlow<Boolean> = _ready

    fun start() {
        notifier.ensureChannels()
        scope.launch {
            initialSettings = settings.current()
            language = initialSettings.language
            db.openHelper.writableDatabase
            _ready.value = true
        }
        scope.launch { settings.settings.collect { language = it.language } }
    }

    companion object {
        fun localizedContext(base: Context, language: AppLanguage): Context {
            val config = Configuration(base.resources.configuration)
            val locale = Locale.forLanguageTag(language.tag)
            config.setLocale(locale)
            return base.createConfigurationContext(config)
        }
    }
}

class ArnApp : Application() {
    lateinit var container: AppContainer
        private set

    override fun onCreate() {
        super.onCreate()
        if (BuildConfig.DEBUG) enableStrictMode()
        container = AppContainer(this)
        container.start()
        WorkManager.getInstance(this).enqueueUniquePeriodicWork(
            "backup-reminder",
            ExistingPeriodicWorkPolicy.KEEP,
            PeriodicWorkRequestBuilder<ReminderWorker>(1, TimeUnit.DAYS).build(),
        )
    }
}

/** Di build debug, catat ke logcat bila ada akses disk di thread utama atau objek yang bocor. */
private fun enableStrictMode() {
    android.os.StrictMode.setThreadPolicy(
        android.os.StrictMode.ThreadPolicy.Builder().detectAll().penaltyLog().build(),
    )
    android.os.StrictMode.setVmPolicy(
        android.os.StrictMode.VmPolicy.Builder()
            .detectLeakedClosableObjects()
            .detectLeakedSqlLiteObjects()
            .detectActivityLeaks()
            .detectLeakedRegistrationObjects()
            .penaltyLog()
            .build(),
    )
}

val Context.container: AppContainer
    get() = (applicationContext as ArnApp).container

/** Memeriksa harian apakah cadangan sudah terlalu lama dan membuat pengingat bila perlu. */
class ReminderWorker(context: Context, params: WorkerParameters) : CoroutineWorker(context, params) {
    override suspend fun doWork(): Result {
        applicationContext.container.notifications.raiseBackupReminderIfDue()
        return Result.success()
    }
}
