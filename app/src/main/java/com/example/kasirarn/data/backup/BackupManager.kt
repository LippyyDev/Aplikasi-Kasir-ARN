package com.example.kasirarn.data.backup

import android.content.Context
import android.content.Intent
import android.database.DatabaseUtils
import android.database.sqlite.SQLiteDatabase
import android.net.Uri
import android.os.Build
import com.example.kasirarn.BuildConfig
import com.example.kasirarn.data.db.ArnDatabase
import com.example.kasirarn.data.repo.Clock
import com.example.kasirarn.data.repo.ImageStore
import com.example.kasirarn.data.settings.SettingsRepository
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import org.json.JSONObject
import java.io.File
import java.io.FileInputStream
import java.io.FileOutputStream
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

data class BackupSummary(val fileName: String, val sizeBytes: Long, val createdAt: Long)

data class RestorePreview(
    val createdAt: Long,
    val appVersion: String,
    val schemaVersion: Int,
    val menuCount: Long,
    val ingredientCount: Long,
    val transactionCount: Long,
)

/** Hasil akhir pemulihan. [restartRequired] true bila database sudah ditutup dan aplikasi harus dimulai ulang. */
data class RestoreResult(val success: Boolean, val error: BackupError? = null, val restartRequired: Boolean = false)

class BackupManager(
    private val context: Context,
    private val db: ArnDatabase,
    private val settings: SettingsRepository,
    private val images: ImageStore,
    private val clock: Clock = Clock.System,
) {
    private val workDir get() = File(context.cacheDir, "backup_work")
    private val stagingDir get() = File(workDir, "restore_staging")
    private val safetyDir get() = File(context.filesDir, "safety")

    fun suggestedFileName(): String {
        val stamp = SimpleDateFormat("yyyyMMdd_HHmm", Locale.US).format(Date(clock.now()))
        return "ARN_Backup_$stamp.$EXTENSION"
    }

    /** Buat cadangan, verifikasi, lalu salin ke [target] yang dipilih pengguna lewat pemilih sistem. */
    suspend fun createBackup(target: Uri): Result<BackupSummary> = withContext(Dispatchers.IO) {
        runCatching {
            val archive = File(workDir, "out.$EXTENSION")
            val info = buildArchive(archive)
            // Verifikasi: buka ulang arsip dan cocokkan checksum sebelum menyentuh lokasi tujuan.
            val verifyDir = File(workDir, "verify")
            try {
                BackupArchive.extractAndValidate(archive, verifyDir, ArnDatabase.VERSION, ::inspectDb)
            } finally {
                verifyDir.deleteRecursively()
            }
            context.contentResolver.openOutputStream(target, "w")?.use { out ->
                FileInputStream(archive).use { it.copyTo(out) }
                out.flush()
            } ?: throw BackupException(BackupError.IO, "tidak dapat menulis ke tujuan")
            val size = archive.length()
            archive.delete()
            settings.setLastBackup(info)
            BackupSummary(target.lastPathSegment ?: suggestedFileName(), size, info)
        }.also { workDir.deleteRecursively() }
    }

    /** Baca dan validasi berkas cadangan tanpa mengubah data yang ada. */
    suspend fun prepareRestore(source: Uri): Result<RestorePreview> = withContext(Dispatchers.IO) {
        runCatching {
            workDir.deleteRecursively()
            workDir.mkdirs()
            val incoming = File(workDir, "incoming.$EXTENSION")
            context.contentResolver.openInputStream(source)?.use { input ->
                FileOutputStream(incoming).use { input.copyTo(it) }
            } ?: throw BackupException(BackupError.IO, "tidak dapat membaca berkas")
            val manifest = BackupArchive.extractAndValidate(incoming, stagingDir, ArnDatabase.VERSION, ::inspectDb)
            incoming.delete()
            RestorePreview(
                createdAt = manifest.createdAt,
                appVersion = manifest.appVersion,
                schemaVersion = manifest.schemaVersion,
                menuCount = manifest.rowCounts["menu"] ?: 0,
                ingredientCount = manifest.rowCounts["bahan_baku"] ?: 0,
                transactionCount = manifest.rowCounts["transaksi"] ?: 0,
            )
        }
    }

    fun discardPendingRestore() {
        workDir.deleteRecursively()
    }

    /**
     * Mengganti data dengan isi [stagingDir] hasil [prepareRestore]. Cadangan pengaman dibuat lebih dulu.
     * Penggantian memakai rename dalam direktori yang sama; bila gagal di tengah jalan, berkas lama dikembalikan.
     */
    suspend fun applyRestore(): RestoreResult = withContext(Dispatchers.IO) {
        if (!File(stagingDir, BackupArchive.DATABASE).exists()) return@withContext RestoreResult(false, BackupError.IO)

        // 1. Cadangan pengaman dari data saat ini.
        if (!saveSafetyCopy()) return@withContext RestoreResult(false, BackupError.IO)

        val settingsJson = File(stagingDir, BackupArchive.SETTINGS).takeIf { it.exists() }?.let {
            runCatching { JSONObject(it.readText()) }.getOrNull()
        }

        // 2. Tutup database lalu tukar berkas.
        db.close()
        val dbFile = context.getDatabasePath(ArnDatabase.FILE_NAME)
        val dbDir = dbFile.parentFile!!
        val filesDir = context.filesDir
        val imagesDir = File(filesDir, ImageStore.DIR)

        val dbOld = File(dbDir, "${ArnDatabase.FILE_NAME}.old")
        val dbNew = File(dbDir, "${ArnDatabase.FILE_NAME}.new")
        val imgOld = File(filesDir, "${ImageStore.DIR}.old")
        val imgNew = File(filesDir, "${ImageStore.DIR}.new")
        var dbSwapped = false
        var imgSwapped = false
        try {
            dbOld.delete(); dbNew.delete(); imgOld.deleteRecursively(); imgNew.deleteRecursively()
            File(stagingDir, BackupArchive.DATABASE).copyTo(dbNew, overwrite = true)
            val stagedImages = File(stagingDir, "images")
            imgNew.mkdirs()
            stagedImages.listFiles()?.forEach { it.copyTo(File(imgNew, it.name), overwrite = true) }

            if (dbFile.exists() && !dbFile.renameTo(dbOld)) throw java.io.IOException("rename db")
            dbSwapped = true
            File(dbDir, "${ArnDatabase.FILE_NAME}-wal").delete()
            File(dbDir, "${ArnDatabase.FILE_NAME}-shm").delete()
            if (!dbNew.renameTo(dbFile)) throw java.io.IOException("promote db")

            if (imagesDir.exists() && !imagesDir.renameTo(imgOld)) throw java.io.IOException("rename images")
            imgSwapped = true
            if (!imgNew.renameTo(imagesDir)) throw java.io.IOException("promote images")
        } catch (e: Exception) {
            // Kembalikan data lama.
            runCatching {
                if (imgSwapped) {
                    imagesDir.deleteRecursively()
                    if (imgOld.exists()) imgOld.renameTo(imagesDir)
                }
                if (dbSwapped) {
                    dbFile.delete()
                    if (dbOld.exists()) dbOld.renameTo(dbFile)
                }
                dbNew.delete(); imgNew.deleteRecursively()
            }
            return@withContext RestoreResult(false, BackupError.IO, restartRequired = true)
        }
        dbOld.delete()
        imgOld.deleteRecursively()
        workDir.deleteRecursively()
        if (settingsJson != null) runCatching { settings.importJson(settingsJson) }
        RestoreResult(true, restartRequired = true)
    }

    /** Salinan pengaman data saat ini di penyimpanan internal aplikasi; true bila berhasil. */
    private suspend fun saveSafetyCopy(): Boolean = try {
        safetyDir.mkdirs()
        val stamp = SimpleDateFormat("yyyyMMdd_HHmmss", Locale.US).format(Date(clock.now()))
        val safetyTmp = File(workDir, "safety_out.$EXTENSION")
        buildArchive(safetyTmp)
        safetyTmp.copyTo(File(safetyDir, "ARN_Safety_$stamp.$EXTENSION"), overwrite = true)
        safetyTmp.delete()
        safetyDir.listFiles()?.sortedByDescending { it.name }?.drop(MAX_SAFETY_COPIES)?.forEach { it.delete() }
        true
    } catch (e: Exception) {
        false
    }

    /**
     * Menghapus seluruh data usaha (database dan foto) dan mengembalikan aplikasi ke keadaan baru.
     * Salinan pengaman dibuat lebih dulu; bila gagal, tidak ada yang dihapus.
     */
    suspend fun wipeAll(): RestoreResult = withContext(Dispatchers.IO) {
        if (!saveSafetyCopy()) return@withContext RestoreResult(false, BackupError.IO)
        db.close()
        val dbFile = context.getDatabasePath(ArnDatabase.FILE_NAME)
        val ok = listOf(
            dbFile.delete() || !dbFile.exists(),
            File(dbFile.path + "-wal").let { it.delete() || !it.exists() },
            File(dbFile.path + "-shm").let { it.delete() || !it.exists() },
            File(context.filesDir, ImageStore.DIR).deleteRecursively(),
        ).all { it }
        settings.setLastBackup(0)
        settings.setGuideDismissed(false)
        workDir.deleteRecursively()
        // Database sudah ditutup, jadi aplikasi harus dimulai ulang apa pun hasilnya.
        RestoreResult(ok, if (ok) null else BackupError.IO, restartRequired = true)
    }

    /** Mulai ulang proses supaya Room membuka berkas database yang baru. */
    fun restartApp() {
        val launch = context.packageManager.getLaunchIntentForPackage(context.packageName)
            ?.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TASK) ?: return
        context.startActivity(launch)
        Runtime.getRuntime().exit(0)
    }

    // ---------- internal ----------

    /** Membangun arsip di [output]; mengembalikan waktu pembuatan. */
    private suspend fun buildArchive(output: File): Long {
        val staging = File(workDir, "build")
        staging.deleteRecursively()
        File(staging, "images").mkdirs()
        output.parentFile?.mkdirs()
        output.delete()

        snapshotDatabase(File(staging, BackupArchive.DATABASE))
        images.directory.listFiles()?.filter { it.isFile }?.forEach { it.copyTo(File(staging, "images/${it.name}"), overwrite = true) }
        File(staging, BackupArchive.SETTINGS).writeText(settings.exportJson().toString(2))

        val copy = inspectDb(File(staging, BackupArchive.DATABASE)) ?: throw BackupException(BackupError.DB_INVALID)
        val createdAt = clock.now()
        BackupArchive.write(
            staging = staging,
            output = output,
            appVersion = BuildConfig.VERSION_NAME,
            appVersionCode = BuildConfig.VERSION_CODE,
            schemaVersion = copy.schemaVersion,
            createdAt = createdAt,
            rowCounts = copy.rowCounts,
        )
        staging.deleteRecursively()
        return createdAt
    }

    /** Salinan database yang konsisten: `VACUUM INTO` (SQLite 3.27+, Android 11+) atau checkpoint WAL lalu salin berkas. */
    private fun snapshotDatabase(dest: File) {
        dest.delete()
        val writable = db.openHelper.writableDatabase
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R) {
            writable.execSQL("VACUUM INTO '${dest.absolutePath.replace("'", "''")}'")
        } else {
            writable.query("PRAGMA wal_checkpoint(TRUNCATE)").use { it.moveToFirst() }
            context.getDatabasePath(ArnDatabase.FILE_NAME).copyTo(dest, overwrite = true)
        }
    }

    private fun inspectDb(file: File): DbInfo? = try {
        SQLiteDatabase.openDatabase(file.absolutePath, null, SQLiteDatabase.OPEN_READONLY).use { sqlite ->
            val ok = sqlite.rawQuery("PRAGMA quick_check", null).use { c -> c.moveToFirst() && c.getString(0) == "ok" }
            if (!ok) {
                null
            } else {
                DbInfo(
                    schemaVersion = sqlite.version,
                    rowCounts = ArnDatabase.TABLES.associateWith { DatabaseUtils.queryNumEntries(sqlite, it) },
                )
            }
        }
    } catch (e: Exception) {
        null
    }

    companion object {
        const val EXTENSION = "arnbak"
        const val MIME = "application/octet-stream"
        private const val MAX_SAFETY_COPIES = 3
    }
}
