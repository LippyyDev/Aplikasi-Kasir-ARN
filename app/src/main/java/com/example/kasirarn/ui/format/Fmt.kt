package com.example.kasirarn.ui.format

import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.compositionLocalOf
import androidx.compose.runtime.remember
import com.example.kasirarn.data.settings.AppLanguage
import com.example.kasirarn.domain.Dimension
import com.example.kasirarn.domain.UnitConverter
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

/** Pemformat angka, uang, dan tanggal mengikuti bahasa pilihan di dalam aplikasi. */
class Fmt(val language: AppLanguage, val currency: String = "Rp") {
    val locale: Locale = Locale.forLanguageTag(language.tag)
    val decimalSeparator: Char = if (language == AppLanguage.ID) ',' else '.'
    private val groupSeparator: Char = if (language == AppLanguage.ID) '.' else ','

    fun number(value: Long): String {
        val negative = value < 0
        val digits = Math.abs(value).toString()
        val sb = StringBuilder()
        digits.forEachIndexed { i, c ->
            if (i > 0 && (digits.length - i) % 3 == 0) sb.append(groupSeparator)
            sb.append(c)
        }
        return if (negative) "-$sb" else sb.toString()
    }

    /** Contoh: "Rp 12.500" atau "-Rp 3.000". */
    fun money(value: Long): String {
        val abs = number(Math.abs(value))
        return (if (value < 0) "-" else "") + currency + " " + abs
    }

    /** Angka uang tanpa simbol, untuk isian. Kosong bila nol. */
    fun moneyInput(value: Long): String = if (value == 0L) "" else number(value)

    fun date(time: Long): String = SimpleDateFormat("d MMM yyyy", locale).format(Date(time))
    fun dateLong(time: Long): String = SimpleDateFormat("EEEE, d MMMM yyyy", locale).format(Date(time))
    fun time(time: Long): String = SimpleDateFormat("HH.mm", locale).format(Date(time))
    fun dateTime(time: Long): String = "${date(time)}, ${time(time)}"
    fun shortDate(time: Long): String = SimpleDateFormat("d MMM", locale).format(Date(time))

    fun quantity(base: Long, dimension: Dimension): String =
        UnitConverter.autoDisplay(base, dimension, decimalSeparator)
}

val LocalFmt = compositionLocalOf { Fmt(AppLanguage.ID) }

@Composable
fun ProvideFmt(language: AppLanguage, currency: String, content: @Composable () -> Unit) {
    val fmt = remember(language, currency) { Fmt(language, currency) }
    CompositionLocalProvider(LocalFmt provides fmt, content = content)
}
