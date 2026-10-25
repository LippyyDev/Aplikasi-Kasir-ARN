package com.example.kasirarn.domain

/** Dimensi bahan baku. Menentukan satuan dasar tempat semua stok dan resep disimpan. */
enum class Dimension(val baseUnit: String) {
    MASS("g"),
    VOLUME("ml"),
    COUNT("buah");

    /** Satuan standar dimensi ini, termasuk satuan dasar sebagai faktor 1. */
    val standardUnits: List<UnitDef>
        get() = when (this) {
            MASS -> listOf(UnitDef("g", Fraction.ONE), UnitDef("kg", Fraction.of(1000)))
            VOLUME -> listOf(UnitDef("ml", Fraction.ONE), UnitDef("liter", Fraction.of(1000)))
            COUNT -> listOf(UnitDef("buah", Fraction.ONE), UnitDef("lusin", Fraction.of(12)))
        }

    companion object {
        fun fromName(name: String): Dimension = entries.firstOrNull { it.name == name } ?: COUNT
    }
}

/** Satu satuan beserta faktor ke satuan dasar: `1 [name] = [factor] satuan dasar`. */
data class UnitDef(val name: String, val factor: Fraction)

object UnitConverter {

    /** Jumlah yang diketik pengguna dalam [unit] menjadi bilangan bulat satuan dasar (half up). */
    fun toBase(quantity: Fraction, unit: UnitDef): Long = (quantity * unit.factor).roundHalfUp()

    /** Nilai satuan dasar menjadi pecahan dalam [unit]. */
    fun fromBase(base: Long, unit: UnitDef): Fraction = Fraction.of(base) / unit.factor

    /**
     * Pilih satuan yang enak dibaca untuk tampilan ringkas: 1500 g menjadi "1,5 kg",
     * 250 g tetap "250 g".
     */
    fun autoDisplay(base: Long, dimension: Dimension, decimalSeparator: Char = ','): String {
        val abs = Math.abs(base)
        val units = dimension.standardUnits
        val unit = when {
            dimension == Dimension.COUNT -> units.first()
            abs >= 1000 -> units[1]
            else -> units.first()
        }
        val text = fromBase(base, unit).toDecimalString(2, decimalSeparator)
        return "$text ${unit.name}"
    }
}

/** Mengurai teks angka dari kolom isian. Menerima koma atau titik sebagai pemisah desimal. */
object DecimalParser {
    private const val MAX_DECIMALS = 6

    /** Mengembalikan null bila teks bukan angka non negatif yang sah. */
    fun parse(text: String): Fraction? {
        val t = text.trim()
        if (t.isEmpty()) return null
        var separators = 0
        for (c in t) {
            when {
                c in '0'..'9' -> Unit
                c == ',' || c == '.' -> separators++
                else -> return null
            }
        }
        if (separators > 1) return null
        val sepIndex = t.indexOfFirst { it == ',' || it == '.' }
        val intText = if (sepIndex < 0) t else t.substring(0, sepIndex)
        val fracText = if (sepIndex < 0) "" else t.substring(sepIndex + 1)
        if (intText.isEmpty() && fracText.isEmpty()) return null
        if (fracText.length > MAX_DECIMALS) return null
        return try {
            val intPart = if (intText.isEmpty()) 0L else intText.toLong()
            val scale = pow10(fracText.length)
            val fracPart = if (fracText.isEmpty()) 0L else fracText.toLong()
            Fraction.of(Math.addExact(Math.multiplyExact(intPart, scale), fracPart), scale)
        } catch (e: ArithmeticException) {
            null
        } catch (e: NumberFormatException) {
            null
        }
    }

    private fun pow10(n: Int): Long {
        var r = 1L
        repeat(n) { r *= 10 }
        return r
    }
}

/** Mengurai nominal uang bulat dari kolom isian; pemisah ribuan diabaikan. */
object MoneyParser {
    fun parse(text: String): Long? {
        val digits = text.filter { it.isDigit() }
        if (digits.isEmpty() || digits.length > 15) return null
        return digits.toLongOrNull()
    }
}
