package com.example.kasirarn

import android.content.Context
import android.net.Uri
import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.example.kasirarn.data.backup.BackupManager
import com.example.kasirarn.data.db.ArnDatabase
import com.example.kasirarn.data.repo.ImageStore
import com.example.kasirarn.data.repo.IngredientDraft
import com.example.kasirarn.data.repo.IngredientRepository
import com.example.kasirarn.data.repo.NotificationRepository
import com.example.kasirarn.data.repo.Op
import com.example.kasirarn.data.notify.SystemNotifier
import com.example.kasirarn.data.notify.Texts
import com.example.kasirarn.data.settings.SettingsRepository
import com.example.kasirarn.domain.Dimension
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.runBlocking
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import java.io.File

/**
 * Cadangan lalu pulihkan pada berkas database sungguhan, termasuk VACUUM INTO, verifikasi checksum,
 * cadangan pengaman, dan penukaran berkas. Berjalan pada data aplikasi di emulator uji.
 */
@RunWith(AndroidJUnit4::class)
class BackupRestoreTest {
    private lateinit var context: Context
    private lateinit var settings: SettingsRepository
    private val texts = Texts { _, _ -> "" }

    @Before
    fun setUp() {
        context = ApplicationProvider.getApplicationContext()
        context.deleteDatabase(ArnDatabase.FILE_NAME)
        File(context.filesDir, ImageStore.DIR).deleteRecursively()
        settings = SettingsRepository(context)
    }

    @After
    fun tearDown() {
        context.deleteDatabase(ArnDatabase.FILE_NAME)
    }

    private fun repo(db: ArnDatabase) = IngredientRepository(
        db,
        NotificationRepository(db, settings, SystemNotifier(context, texts), texts),
    )

    private fun draft(name: String) = IngredientDraft(null, name, Dimension.MASS, "g", 0, 0, 0, emptyList())

    @Test
    fun backupThenRestoreBringsBackExactState() = runBlocking {
        val images = ImageStore(context)
        File(images.directory, "menu_1.jpg").writeBytes(byteArrayOf(9, 8, 7))

        var db = ArnDatabase.build(context)
        var ingredients = repo(db)
        assertTrue(ingredients.save(draft("Kopi")) is Op.Ok)
        assertTrue(ingredients.save(draft("Gula")) is Op.Ok)
        val backup = BackupManager(context, db, settings, images)

        val file = File(context.cacheDir, "test.arnbak").also { it.delete() }
        val created = backup.createBackup(Uri.fromFile(file))
        assertTrue("createBackup: ${created.exceptionOrNull()}", created.isSuccess)
        assertTrue(file.length() > 0)
        assertTrue(settings.current().lastBackupAt > 0)

        // Ubah data setelah cadangan: tambah bahan dan hapus gambar.
        assertTrue(ingredients.save(draft("Susu")) is Op.Ok)
        File(images.directory, "menu_1.jpg").delete()
        assertEquals(3, ingredients.observeIngredients().first().size)

        val preview = backup.prepareRestore(Uri.fromFile(file))
        assertTrue("prepareRestore: ${preview.exceptionOrNull()}", preview.isSuccess)
        assertEquals(1, preview.getOrThrow().schemaVersion)

        val result = backup.applyRestore()
        assertTrue("applyRestore: ${result.error}", result.success)
        assertTrue(result.restartRequired)

        // Buka ulang database seperti setelah proses dimulai ulang.
        db = ArnDatabase.build(context)
        ingredients = repo(db)
        val names = ingredients.observeIngredients().first().map { it.nama }.sorted()
        assertEquals(listOf("Gula", "Kopi"), names)
        assertTrue(File(images.directory, "menu_1.jpg").exists())
        assertTrue(File(context.filesDir, "safety").listFiles()?.isNotEmpty() == true)
        db.close()
    }

    @Test
    fun corruptedBackupIsRejectedAndDataUntouched() = runBlocking {
        val images = ImageStore(context)
        val db = ArnDatabase.build(context)
        val ingredients = repo(db)
        ingredients.save(draft("Kopi"))
        val backup = BackupManager(context, db, settings, images)
        val file = File(context.cacheDir, "bad.arnbak").also { it.delete() }
        assertTrue(backup.createBackup(Uri.fromFile(file)).isSuccess)

        val bytes = file.readBytes()
        bytes[bytes.size / 2] = (bytes[bytes.size / 2] + 1).toByte()
        file.writeBytes(bytes)

        assertTrue(backup.prepareRestore(Uri.fromFile(file)).isFailure)
        assertEquals(1, ingredients.observeIngredients().first().size)
        db.close()
    }
}
