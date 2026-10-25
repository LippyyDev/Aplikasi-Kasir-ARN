package com.example.kasirarn.ui.home

import android.content.Context
import android.content.Intent
import android.graphics.Bitmap
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.Paint
import android.graphics.Typeface
import androidx.core.content.FileProvider
import com.example.kasirarn.R
import com.example.kasirarn.data.db.PayMethod
import com.example.kasirarn.data.db.TxStatus
import com.example.kasirarn.data.repo.Receipt
import com.example.kasirarn.data.repo.ReceiptNumber
import com.example.kasirarn.ui.format.Fmt
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.io.File
import java.io.FileOutputStream

/** Satu baris struk dengan gaya sederhana; dipakai bersama oleh versi teks dan versi gambar. */
data class ReceiptLine(val text: String, val bold: Boolean = false, val center: Boolean = false)

object ReceiptFormat {
    const val WIDTH = 32

    fun lines(context: Context, fmt: Fmt, receipt: Receipt): List<ReceiptLine> {
        val tx = receipt.transaction
        val profile = receipt.profile
        val out = mutableListOf<ReceiptLine>()
        val name = profile?.nama?.takeIf { it.isNotBlank() } ?: context.getString(R.string.app_name)
        wrap(name, WIDTH).forEach { out += ReceiptLine(it, bold = true, center = true) }
        profile?.alamat?.takeIf { it.isNotBlank() }?.let { a -> wrap(a, WIDTH).forEach { out += ReceiptLine(it, center = true) } }
        profile?.telepon?.takeIf { it.isNotBlank() }?.let { out += ReceiptLine(it, center = true) }
        out += rule()
        out += ReceiptLine(ReceiptNumber.format(tx))
        out += ReceiptLine(fmt.dateTime(tx.waktu))
        if (tx.status == TxStatus.VOID) out += ReceiptLine(context.getString(R.string.receipt_void), bold = true)
        out += rule()
        for (item in receipt.items) {
            val left = "${item.kuantitas}x ${item.namaMenu}"
            val right = number(fmt, item.hargaSatuan * item.kuantitas)
            out += leftRight(left, right)
            if (item.kuantitas > 1) out += ReceiptLine("   @ ${number(fmt, item.hargaSatuan)}")
            if (item.catatan.isNotBlank()) wrap("   * ${item.catatan}", WIDTH).forEach { out += ReceiptLine(it) }
        }
        out += rule()
        out += leftRight(context.getString(R.string.cart_subtotal), number(fmt, tx.subtotal))
        if (tx.diskon > 0) out += leftRight(context.getString(R.string.cart_discount), "-" + number(fmt, tx.diskon))
        out += leftRight(context.getString(R.string.cart_total), number(fmt, tx.total)).map { it.copy(bold = true) }
        if (tx.metodeBayar == PayMethod.CASH) {
            out += leftRight(context.getString(R.string.receipt_cash), number(fmt, tx.dibayar))
            out += leftRight(context.getString(R.string.receipt_change), number(fmt, tx.kembalian))
        } else {
            out += leftRight(context.getString(R.string.receipt_method), context.getString(R.string.pay_non_cash))
        }
        out += rule()
        out += ReceiptLine(context.getString(R.string.receipt_thanks), center = true)
        return out
    }

    fun text(context: Context, fmt: Fmt, receipt: Receipt): String =
        lines(context, fmt, receipt).joinToString("\n") { line ->
            if (line.center) line.text.padStart((WIDTH + line.text.length) / 2) else line.text
        }

    private fun number(fmt: Fmt, v: Long) = fmt.money(v)

    private fun rule() = ReceiptLine("-".repeat(WIDTH))

    private fun leftRight(left: String, right: String): List<ReceiptLine> {
        if (left.length + right.length + 1 <= WIDTH) {
            return listOf(ReceiptLine(left + " ".repeat(WIDTH - left.length - right.length) + right))
        }
        val wrapped = wrap(left, WIDTH)
        return wrapped.map { ReceiptLine(it) } + ReceiptLine(right.padStart(WIDTH))
    }

    private fun wrap(text: String, width: Int): List<String> {
        val lines = mutableListOf<String>()
        var current = StringBuilder()
        for (word in text.split(' ')) {
            var w = word
            while (w.length > width) {
                if (current.isNotEmpty()) {
                    lines += current.toString()
                    current = StringBuilder()
                }
                lines += w.take(width)
                w = w.drop(width)
            }
            if (current.isEmpty()) current.append(w)
            else if (current.length + 1 + w.length <= width) current.append(' ').append(w)
            else {
                lines += current.toString()
                current = StringBuilder(w)
            }
        }
        if (current.isNotEmpty()) lines += current.toString()
        return lines.ifEmpty { listOf("") }
    }

    /** Gambar struk hitam di atas putih, huruf monospace supaya kolom angka rata. */
    fun bitmap(context: Context, fmt: Fmt, receipt: Receipt): Bitmap {
        val lines = lines(context, fmt, receipt)
        val regular = Paint(Paint.ANTI_ALIAS_FLAG).apply {
            color = Color.BLACK
            typeface = Typeface.MONOSPACE
            textSize = 26f
        }
        val bold = Paint(regular).apply { typeface = Typeface.create(Typeface.MONOSPACE, Typeface.BOLD) }
        val charWidth = regular.measureText("M")
        val padding = 32f
        val width = (charWidth * WIDTH + padding * 2).toInt()
        val lineHeight = 36f
        val height = (lines.size * lineHeight + padding * 2).toInt()
        val bmp = Bitmap.createBitmap(width, height, Bitmap.Config.ARGB_8888)
        val canvas = Canvas(bmp)
        canvas.drawColor(Color.WHITE)
        var y = padding + 26f
        for (line in lines) {
            val paint = if (line.bold) bold else regular
            val x = if (line.center) (width - paint.measureText(line.text)) / 2 else padding
            canvas.drawText(line.text, x, y, paint)
            y += lineHeight
        }
        return bmp
    }

    fun shareText(context: Context, text: String, chooserTitle: String) {
        val send = Intent(Intent.ACTION_SEND).apply {
            type = "text/plain"
            putExtra(Intent.EXTRA_TEXT, text)
        }
        context.startActivity(Intent.createChooser(send, chooserTitle).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK))
    }

    /**
     * Membuat gambar struk, menulisnya ke cache, lalu membuka pemilih aplikasi. Pekerjaan berat (menggambar,
     * kompresi PNG, tulis berkas) berjalan di thread IO dan bitmap didaur ulang setelahnya.
     */
    suspend fun shareImage(context: Context, fmt: Fmt, receipt: Receipt, chooserTitle: String) {
        val uri = withContext(Dispatchers.IO) {
            val dir = File(context.cacheDir, "share").also { it.mkdirs() }
            val file = File(dir, "struk_${ReceiptNumber.format(receipt.transaction)}.png")
            val bitmap = bitmap(context, fmt, receipt)
            try {
                FileOutputStream(file).use { bitmap.compress(Bitmap.CompressFormat.PNG, 100, it) }
            } finally {
                bitmap.recycle()
            }
            FileProvider.getUriForFile(context, "${context.packageName}.fileprovider", file)
        }
        val send = Intent(Intent.ACTION_SEND).apply {
            type = "image/png"
            putExtra(Intent.EXTRA_STREAM, uri)
            addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
        }
        context.startActivity(Intent.createChooser(send, chooserTitle).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK))
    }
}
