package com.example.kasirarn.data.settings

import android.content.Context
import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.booleanPreferencesKey
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.intPreferencesKey
import androidx.datastore.preferences.core.longPreferencesKey
import androidx.datastore.preferences.core.stringPreferencesKey
import androidx.datastore.preferences.preferencesDataStore
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.map
import org.json.JSONObject

enum class ThemeMode { SYSTEM, LIGHT, DARK }

/** Kebijakan saat stok bahan tidak cukup untuk satu porsi. */
enum class StockPolicy { BLOCK, WARN }

enum class AppLanguage(val tag: String) {
    ID("id"),
    EN("en");

    companion object {
        fun fromTag(tag: String?): AppLanguage = entries.firstOrNull { it.tag == tag } ?: ID
    }
}

data class AppSettings(
    val theme: ThemeMode = ThemeMode.SYSTEM,
    val dynamicColor: Boolean = false,
    val language: AppLanguage = AppLanguage.ID,
    val stockPolicy: StockPolicy = StockPolicy.BLOCK,
    val notifyLowStock: Boolean = true,
    val notifyOutOfStock: Boolean = true,
    val notifyBackupReminder: Boolean = true,
    val notifyBackupResult: Boolean = true,
    val backupReminderDays: Int = 7,
    /** Epoch ms cadangan terakhir; 0 berarti belum pernah. Khusus perangkat ini, tidak ikut cadangan. */
    val lastBackupAt: Long = 0,
    val guideDismissed: Boolean = false,
)

private val Context.dataStore: DataStore<Preferences> by preferencesDataStore(name = "arn_settings")

class SettingsRepository(private val context: Context) {

    private object Keys {
        val THEME = stringPreferencesKey("theme")
        val DYNAMIC = booleanPreferencesKey("dynamic_color")
        val LANGUAGE = stringPreferencesKey("language")
        val STOCK_POLICY = stringPreferencesKey("stock_policy")
        val N_LOW = booleanPreferencesKey("notify_low")
        val N_OUT = booleanPreferencesKey("notify_out")
        val N_REMINDER = booleanPreferencesKey("notify_backup_reminder")
        val N_RESULT = booleanPreferencesKey("notify_backup_result")
        val REMINDER_DAYS = intPreferencesKey("backup_reminder_days")
        val LAST_BACKUP = longPreferencesKey("last_backup_at")
        val GUIDE_DISMISSED = booleanPreferencesKey("guide_dismissed")
    }

    val settings: Flow<AppSettings> = context.dataStore.data.map { it.toSettings() }

    suspend fun current(): AppSettings = settings.first()

    suspend fun setTheme(mode: ThemeMode) = edit { it[Keys.THEME] = mode.name }
    suspend fun setDynamicColor(on: Boolean) = edit { it[Keys.DYNAMIC] = on }
    suspend fun setLanguage(lang: AppLanguage) = edit { it[Keys.LANGUAGE] = lang.tag }
    suspend fun setStockPolicy(p: StockPolicy) = edit { it[Keys.STOCK_POLICY] = p.name }
    suspend fun setNotifyLowStock(on: Boolean) = edit { it[Keys.N_LOW] = on }
    suspend fun setNotifyOutOfStock(on: Boolean) = edit { it[Keys.N_OUT] = on }
    suspend fun setNotifyBackupReminder(on: Boolean) = edit { it[Keys.N_REMINDER] = on }
    suspend fun setNotifyBackupResult(on: Boolean) = edit { it[Keys.N_RESULT] = on }
    suspend fun setReminderDays(days: Int) = edit { it[Keys.REMINDER_DAYS] = days.coerceIn(1, 90) }
    suspend fun setLastBackup(time: Long) = edit { it[Keys.LAST_BACKUP] = time }
    suspend fun setGuideDismissed(on: Boolean) = edit { it[Keys.GUIDE_DISMISSED] = on }

    /** Pengaturan yang ikut dalam cadangan (`settings.json`). */
    suspend fun exportJson(): JSONObject {
        val s = current()
        return JSONObject()
            .put("theme", s.theme.name)
            .put("dynamicColor", s.dynamicColor)
            .put("language", s.language.tag)
            .put("stockPolicy", s.stockPolicy.name)
            .put("notifyLowStock", s.notifyLowStock)
            .put("notifyOutOfStock", s.notifyOutOfStock)
            .put("notifyBackupReminder", s.notifyBackupReminder)
            .put("notifyBackupResult", s.notifyBackupResult)
            .put("backupReminderDays", s.backupReminderDays)
    }

    suspend fun importJson(json: JSONObject) {
        edit { p ->
            json.optString("theme").takeIf { it.isNotEmpty() }?.let { v ->
                if (ThemeMode.entries.any { it.name == v }) p[Keys.THEME] = v
            }
            if (json.has("dynamicColor")) p[Keys.DYNAMIC] = json.optBoolean("dynamicColor")
            json.optString("language").takeIf { it.isNotEmpty() }?.let { p[Keys.LANGUAGE] = AppLanguage.fromTag(it).tag }
            json.optString("stockPolicy").takeIf { it.isNotEmpty() }?.let { v ->
                if (StockPolicy.entries.any { it.name == v }) p[Keys.STOCK_POLICY] = v
            }
            if (json.has("notifyLowStock")) p[Keys.N_LOW] = json.optBoolean("notifyLowStock", true)
            if (json.has("notifyOutOfStock")) p[Keys.N_OUT] = json.optBoolean("notifyOutOfStock", true)
            if (json.has("notifyBackupReminder")) p[Keys.N_REMINDER] = json.optBoolean("notifyBackupReminder", true)
            if (json.has("notifyBackupResult")) p[Keys.N_RESULT] = json.optBoolean("notifyBackupResult", true)
            if (json.has("backupReminderDays")) p[Keys.REMINDER_DAYS] = json.optInt("backupReminderDays", 7).coerceIn(1, 90)
        }
    }

    private suspend fun edit(block: (androidx.datastore.preferences.core.MutablePreferences) -> Unit) {
        context.dataStore.edit { block(it) }
    }

    private fun Preferences.toSettings(): AppSettings = AppSettings(
        theme = this[Keys.THEME]?.let { v -> ThemeMode.entries.firstOrNull { it.name == v } } ?: ThemeMode.SYSTEM,
        dynamicColor = this[Keys.DYNAMIC] ?: false,
        language = AppLanguage.fromTag(this[Keys.LANGUAGE]),
        stockPolicy = this[Keys.STOCK_POLICY]?.let { v -> StockPolicy.entries.firstOrNull { it.name == v } } ?: StockPolicy.BLOCK,
        notifyLowStock = this[Keys.N_LOW] ?: true,
        notifyOutOfStock = this[Keys.N_OUT] ?: true,
        notifyBackupReminder = this[Keys.N_REMINDER] ?: true,
        notifyBackupResult = this[Keys.N_RESULT] ?: true,
        backupReminderDays = this[Keys.REMINDER_DAYS] ?: 7,
        lastBackupAt = this[Keys.LAST_BACKUP] ?: 0L,
        guideDismissed = this[Keys.GUIDE_DISMISSED] ?: false,
    )
}
