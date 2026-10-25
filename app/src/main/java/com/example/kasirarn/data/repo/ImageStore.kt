package com.example.kasirarn.data.repo

import android.content.Context
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.graphics.Matrix
import android.media.ExifInterface
import android.net.Uri
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.io.File
import java.io.FileOutputStream

/**
 * Foto menu dan logo disimpan sebagai berkas JPEG kecil di penyimpanan internal aplikasi.
 * Basis data hanya menyimpan nama berkas, bukan jalur penuh, supaya tetap sah setelah pemulihan.
 */
class ImageStore(private val context: Context, private val clock: Clock = Clock.System) {

    val directory: File get() = File(context.filesDir, DIR).also { it.mkdirs() }

    fun file(name: String?): File? = name?.takeIf { it.isNotBlank() }?.let { File(directory, it) }?.takeIf { it.exists() }

    suspend fun saveFromUri(uri: Uri, prefix: String, maxSide: Int = 800): String? = withContext(Dispatchers.IO) {
        try {
            val resolver = context.contentResolver
            val bounds = BitmapFactory.Options().apply { inJustDecodeBounds = true }
            resolver.openInputStream(uri)?.use { BitmapFactory.decodeStream(it, null, bounds) }
            if (bounds.outWidth <= 0 || bounds.outHeight <= 0) return@withContext null

            var sample = 1
            while (bounds.outWidth / sample > maxSide * 2 || bounds.outHeight / sample > maxSide * 2) sample *= 2
            val opts = BitmapFactory.Options().apply { inSampleSize = sample }
            var bmp = resolver.openInputStream(uri)?.use { BitmapFactory.decodeStream(it, null, opts) }
                ?: return@withContext null

            val rotation = resolver.openInputStream(uri)?.use { exifRotation(it) } ?: 0
            val scale = maxSide.toFloat() / maxOf(bmp.width, bmp.height)
            if (scale < 1f || rotation != 0) {
                val m = Matrix()
                if (scale < 1f) m.postScale(scale, scale)
                if (rotation != 0) m.postRotate(rotation.toFloat())
                val scaled = Bitmap.createBitmap(bmp, 0, 0, bmp.width, bmp.height, m, true)
                if (scaled !== bmp) bmp.recycle()
                bmp = scaled
            }
            val name = "${prefix}_${clock.now()}.jpg"
            FileOutputStream(File(directory, name)).use { bmp.compress(Bitmap.CompressFormat.JPEG, 85, it) }
            bmp.recycle()
            name
        } catch (e: Exception) {
            null
        }
    }

    fun delete(name: String?) {
        if (name.isNullOrBlank()) return
        File(directory, name).delete()
    }

    private fun exifRotation(stream: java.io.InputStream): Int = try {
        when (ExifInterface(stream).getAttributeInt(ExifInterface.TAG_ORIENTATION, ExifInterface.ORIENTATION_NORMAL)) {
            ExifInterface.ORIENTATION_ROTATE_90 -> 90
            ExifInterface.ORIENTATION_ROTATE_180 -> 180
            ExifInterface.ORIENTATION_ROTATE_270 -> 270
            else -> 0
        }
    } catch (e: Exception) {
        0
    }

    companion object {
        const val DIR = "images"
    }
}
