package com.example.kasirarn.data.repo

import java.util.Calendar
import java.util.TimeZone

/** Galat yang dapat diterjemahkan layar menjadi pesan bagi pengguna. */
enum class AppError {
    NAME_EMPTY,
    NAME_DUPLICATE,
    INVALID_NUMBER,
    PRICE_INVALID,
    NOT_FOUND,
    IN_USE,
    EMPTY_CART,
    INSUFFICIENT_STOCK,
    PAYMENT_SHORT,
    ALREADY_VOID,
    RESERVED,
    STORAGE,
    UNKNOWN,
}

sealed interface Op<out T> {
    data class Ok<T>(val value: T) : Op<T>
    data class Fail(val error: AppError, val detail: String? = null) : Op<Nothing>
}

fun <T> Op<T>.getOrNull(): T? = (this as? Op.Ok)?.value

/** Sumber waktu yang dapat diganti pada pengujian. */
fun interface Clock {
    fun now(): Long

    companion object {
        val System = Clock { java.lang.System.currentTimeMillis() }
    }
}

object DayMath {
    fun startOfDay(time: Long, zone: TimeZone = TimeZone.getDefault()): Long {
        val c = Calendar.getInstance(zone)
        c.timeInMillis = time
        c.set(Calendar.HOUR_OF_DAY, 0)
        c.set(Calendar.MINUTE, 0)
        c.set(Calendar.SECOND, 0)
        c.set(Calendar.MILLISECOND, 0)
        return c.timeInMillis
    }

    fun addDays(time: Long, days: Int, zone: TimeZone = TimeZone.getDefault()): Long {
        val c = Calendar.getInstance(zone)
        c.timeInMillis = time
        c.add(Calendar.DAY_OF_YEAR, days)
        return c.timeInMillis
    }

    /** yyyyMMdd dari hari lokal, dasar nomor transaksi harian. */
    fun dayKey(time: Long, zone: TimeZone = TimeZone.getDefault()): Int {
        val c = Calendar.getInstance(zone)
        c.timeInMillis = time
        return c.get(Calendar.YEAR) * 10_000 + (c.get(Calendar.MONTH) + 1) * 100 + c.get(Calendar.DAY_OF_MONTH)
    }

    fun startOfMonth(time: Long, zone: TimeZone = TimeZone.getDefault()): Long {
        val c = Calendar.getInstance(zone)
        c.timeInMillis = startOfDay(time, zone)
        c.set(Calendar.DAY_OF_MONTH, 1)
        return c.timeInMillis
    }

    fun offsetMs(time: Long, zone: TimeZone = TimeZone.getDefault()): Long = zone.getOffset(time).toLong()
}

enum class Period { TODAY, WEEK, MONTH, CUSTOM }

/** Rentang waktu setengah terbuka [from, to). */
data class TimeRange(val from: Long, val to: Long) {
    /** Satu hari penuh? Dipakai untuk memilih grafik per jam. */
    val isSingleDay: Boolean get() = to - from <= 25L * 3600_000L

    companion object {
        fun of(period: Period, now: Long, customFrom: Long = 0, customToInclusive: Long = 0): TimeRange {
            val today = DayMath.startOfDay(now)
            return when (period) {
                Period.TODAY -> TimeRange(today, DayMath.addDays(today, 1))
                Period.WEEK -> TimeRange(DayMath.addDays(today, -6), DayMath.addDays(today, 1))
                Period.MONTH -> TimeRange(DayMath.startOfMonth(now), DayMath.addDays(today, 1))
                Period.CUSTOM -> {
                    val a = DayMath.startOfDay(minOf(customFrom, customToInclusive))
                    val b = DayMath.addDays(DayMath.startOfDay(maxOf(customFrom, customToInclusive)), 1)
                    TimeRange(a, b)
                }
            }
        }
    }
}
