package com.example.kasirarn.data.backup

import org.json.JSONException
import org.json.JSONObject
import java.io.File
import java.io.FileInputStream
import java.io.FileOutputStream
import java.security.MessageDigest
import java.util.zip.ZipEntry
import java.util.zip.ZipException
import java.util.zip.ZipFile
import java.util.zip.ZipOutputStream

enum class BackupError {
    /** Bukan arsip ZIP yang sah atau tidak punya manifest. */
    NOT_ARCHIVE,
    MANIFEST_INVALID,
    CHECKSUM_MISMATCH,
    /** Cadangan dibuat oleh versi aplikasi yang lebih baru. */
    NEWER_SCHEMA,
    DB_INVALID,
    UNSAFE_ENTRY,
    IO,
}

class BackupException(val error: BackupError, message: String? = null) : Exception(message ?: error.name)

data class Manifest(
    val app: String,
    val appVersion: String,
    val appVersionCode: Int,
    val schemaVersion: Int,
    val createdAt: Long,
    val rowCounts: Map<String, Long>,
    /** Jalur di dalam arsip -> SHA-256 heksadesimal. */
    val files: Map<String, String>,
) {
    fun toJson(): JSONObject = JSONObject()
        .put("app", app)
        .put("appVersion", appVersion)
        .put("appVersionCode", appVersionCode)
        .put("schemaVersion", schemaVersion)
        .put("createdAt", createdAt)
        .put("rowCounts", JSONObject(rowCounts.mapValues { it.value }))
        .put("files", JSONObject(files))

    companion object {
        fun fromJson(json: JSONObject): Manifest {
            val counts = json.getJSONObject("rowCounts")
            val files = json.getJSONObject("files")
            return Manifest(
                app = json.getString("app"),
                appVersion = json.getString("appVersion"),
                appVersionCode = json.getInt("appVersionCode"),
                schemaVersion = json.getInt("schemaVersion"),
                createdAt = json.getLong("createdAt"),
                rowCounts = counts.keys().asSequence().associateWith { counts.getLong(it) },
                files = files.keys().asSequence().associateWith { files.getString(it) },
            )
        }
    }
}

/** Informasi yang dibaca dari berkas database di dalam arsip. */
data class DbInfo(val schemaVersion: Int, val rowCounts: Map<String, Long>)

object BackupArchive {
    const val MANIFEST = "manifest.json"
    const val DATABASE = "arn.db"
    const val SETTINGS = "settings.json"
    const val IMAGES = "images/"
    const val APP_NAME = "ARN Kasir"

    fun sha256(file: File): String {
        val md = MessageDigest.getInstance("SHA-256")
        FileInputStream(file).use { input ->
            val buf = ByteArray(64 * 1024)
            while (true) {
                val n = input.read(buf)
                if (n < 0) break
                md.update(buf, 0, n)
            }
        }
        return md.digest().joinToString("") { "%02x".format(it) }
    }

    /**
     * Menulis arsip dari [staging], yang berisi `arn.db`, `settings.json`, dan `images/`.
     * Manifest dibuat dari isi yang benar-benar ada, lengkap dengan checksum setiap berkas.
     */
    fun write(
        staging: File,
        output: File,
        appVersion: String,
        appVersionCode: Int,
        schemaVersion: Int,
        createdAt: Long,
        rowCounts: Map<String, Long>,
    ): Manifest {
        val entries = LinkedHashMap<String, File>()
        entries[DATABASE] = File(staging, DATABASE)
        File(staging, SETTINGS).takeIf { it.exists() }?.let { entries[SETTINGS] = it }
        File(staging, "images").listFiles()?.filter { it.isFile }?.sortedBy { it.name }?.forEach {
            entries[IMAGES + it.name] = it
        }
        val manifest = Manifest(
            app = APP_NAME,
            appVersion = appVersion,
            appVersionCode = appVersionCode,
            schemaVersion = schemaVersion,
            createdAt = createdAt,
            rowCounts = rowCounts,
            files = entries.mapValues { sha256(it.value) },
        )
        ZipOutputStream(FileOutputStream(output).buffered()).use { zip ->
            zip.putNextEntry(ZipEntry(MANIFEST))
            zip.write(manifest.toJson().toString(2).toByteArray(Charsets.UTF_8))
            zip.closeEntry()
            for ((name, file) in entries) {
                zip.putNextEntry(ZipEntry(name))
                FileInputStream(file).use { it.copyTo(zip) }
                zip.closeEntry()
            }
        }
        return manifest
    }

    /**
     * Membuka arsip, memvalidasi berurutan, dan mengekstrak ke [stagingDir]. Setiap kegagalan
     * dilempar sebagai [BackupException] tanpa menyentuh data aplikasi.
     *
     * Urutan: arsip dan manifest sah -> semua checksum cocok -> versi skema <= [currentSchema]
     * -> database di dalam arsip terbaca dan jumlah barisnya sama dengan manifest.
     */
    fun extractAndValidate(
        archive: File,
        stagingDir: File,
        currentSchema: Int,
        inspectDb: (File) -> DbInfo?,
    ): Manifest {
        stagingDir.deleteRecursively()
        stagingDir.mkdirs()
        val canonicalRoot = stagingDir.canonicalFile
        val zip = try {
            ZipFile(archive)
        } catch (e: ZipException) {
            throw BackupException(BackupError.NOT_ARCHIVE)
        } catch (e: java.io.IOException) {
            throw BackupException(BackupError.NOT_ARCHIVE)
        }
        zip.use { z ->
            val manifestEntry = z.getEntry(MANIFEST) ?: throw BackupException(BackupError.NOT_ARCHIVE)
            val manifest = try {
                Manifest.fromJson(JSONObject(z.getInputStream(manifestEntry).readBytes().toString(Charsets.UTF_8)))
            } catch (e: JSONException) {
                throw BackupException(BackupError.MANIFEST_INVALID)
            }
            if (manifest.app != APP_NAME || manifest.files[DATABASE] == null) {
                throw BackupException(BackupError.MANIFEST_INVALID)
            }

            // Ekstrak hanya entri yang terdaftar di manifest, dengan penjagaan jalur.
            val names = z.entries().asSequence().map { it.name }.filter { it != MANIFEST }.toList()
            if (names.any { it !in manifest.files }) throw BackupException(BackupError.CHECKSUM_MISMATCH, "entri tidak terdaftar")
            for (name in manifest.files.keys) {
                if (!isSafeName(name)) throw BackupException(BackupError.UNSAFE_ENTRY, name)
                val entry = z.getEntry(name) ?: throw BackupException(BackupError.CHECKSUM_MISMATCH, "hilang: $name")
                val target = File(stagingDir, name).canonicalFile
                if (!target.path.startsWith(canonicalRoot.path + File.separator)) {
                    throw BackupException(BackupError.UNSAFE_ENTRY, name)
                }
                target.parentFile?.mkdirs()
                z.getInputStream(entry).use { input -> FileOutputStream(target).use { input.copyTo(it) } }
            }
            for ((name, expected) in manifest.files) {
                if (sha256(File(stagingDir, name)) != expected) {
                    throw BackupException(BackupError.CHECKSUM_MISMATCH, name)
                }
            }
            if (manifest.schemaVersion > currentSchema) throw BackupException(BackupError.NEWER_SCHEMA)

            val info = inspectDb(File(stagingDir, DATABASE)) ?: throw BackupException(BackupError.DB_INVALID)
            if (info.schemaVersion > currentSchema) throw BackupException(BackupError.NEWER_SCHEMA)
            for ((table, count) in manifest.rowCounts) {
                if (info.rowCounts[table] != count) throw BackupException(BackupError.DB_INVALID, "baris $table")
            }
            return manifest
        }
    }

    private fun isSafeName(name: String): Boolean {
        if (name == DATABASE || name == SETTINGS) return true
        if (!name.startsWith(IMAGES)) return false
        val rest = name.removePrefix(IMAGES)
        return rest.isNotEmpty() && '/' !in rest && '\\' !in rest && rest != "." && rest != ".." && !rest.contains("..")
    }
}
