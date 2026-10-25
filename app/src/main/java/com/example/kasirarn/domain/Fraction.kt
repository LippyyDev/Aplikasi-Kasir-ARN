package com.example.kasirarn.domain

/**
 * Pecahan bilangan bulat. Dipakai untuk semua faktor konversi supaya tidak ada galat
 * pembulatan bilangan desimal biner. Penyebut selalu positif dan pecahan selalu dalam bentuk
 * paling sederhana. Overflow dilempar sebagai [ArithmeticException].
 */
class Fraction private constructor(val num: Long, val den: Long) : Comparable<Fraction> {

    val isZero: Boolean get() = num == 0L
    val isNegative: Boolean get() = num < 0L
    val isWhole: Boolean get() = den == 1L

    operator fun times(other: Fraction): Fraction {
        // Saling kurangi dulu supaya hasil antara tidak melimpah tanpa perlu.
        val g1 = gcd(Math.abs(num), other.den)
        val g2 = gcd(Math.abs(other.num), den)
        val n = Math.multiplyExact(num / g1, other.num / g2)
        val d = Math.multiplyExact(den / g2, other.den / g1)
        return of(n, d)
    }

    operator fun times(value: Long): Fraction = times(of(value))

    operator fun plus(other: Fraction): Fraction {
        val n = Math.addExact(
            Math.multiplyExact(num, other.den),
            Math.multiplyExact(other.num, den),
        )
        return of(n, Math.multiplyExact(den, other.den))
    }

    operator fun minus(other: Fraction): Fraction = plus(of(-other.num, other.den))

    operator fun div(other: Fraction): Fraction {
        require(!other.isZero) { "Pembagian dengan nol" }
        return times(of(other.den, other.num))
    }

    /** Bulatkan ke bilangan bulat terdekat, setengah dibulatkan menjauhi nol. */
    fun roundHalfUp(): Long {
        val negative = num < 0
        val absNum = Math.abs(num)
        val q = absNum / den
        val r = absNum % den
        // r/den >= 1/2  <=>  2r >= den  (r < den, jadi 2r tidak melimpah bila den <= Long.MAX/2)
        val up = if (r >= den - r) 1L else 0L
        val result = q + up
        return if (negative) -result else result
    }

    /** floor(num / den) untuk nilai non negatif dan negatif. */
    fun floor(): Long = Math.floorDiv(num, den)

    /**
     * Tulis sebagai desimal dengan paling banyak [maxDecimals] angka di belakang pemisah,
     * dibulatkan setengah naik, nol di belakang dibuang. Tanpa `Double`.
     */
    fun toDecimalString(maxDecimals: Int = 3, decimalSeparator: Char = ','): String {
        val negative = num < 0
        val scale = pow10(maxDecimals)
        val scaled = Fraction(Math.abs(num), den).times(of(scale)).roundHalfUp()
        val intPart = scaled / scale
        val fracPart = scaled % scale
        val sb = StringBuilder()
        if (negative && scaled != 0L) sb.append('-')
        sb.append(intPart)
        if (fracPart != 0L) {
            var digits = fracPart.toString().padStart(maxDecimals, '0')
            digits = digits.trimEnd('0')
            sb.append(decimalSeparator).append(digits)
        }
        return sb.toString()
    }

    override fun compareTo(other: Fraction): Int {
        // Bandingkan num/den dengan other.num/other.den lewat perkalian silang (den > 0).
        val left = java.math.BigInteger.valueOf(num).multiply(java.math.BigInteger.valueOf(other.den))
        val right = java.math.BigInteger.valueOf(other.num).multiply(java.math.BigInteger.valueOf(den))
        return left.compareTo(right)
    }

    override fun equals(other: Any?): Boolean = other is Fraction && num == other.num && den == other.den
    override fun hashCode(): Int = 31 * num.hashCode() + den.hashCode()
    override fun toString(): String = if (den == 1L) "$num" else "$num/$den"

    companion object {
        val ZERO = Fraction(0, 1)
        val ONE = Fraction(1, 1)

        fun of(value: Long): Fraction = Fraction(value, 1)

        fun of(num: Long, den: Long): Fraction {
            if (den == 0L) throw ArithmeticException("Penyebut tidak boleh nol")
            if (num == 0L) return ZERO
            var n = num
            var d = den
            if (d < 0) {
                n = Math.negateExact(n)
                d = Math.negateExact(d)
            }
            val g = gcd(Math.abs(n), d)
            return Fraction(n / g, d / g)
        }

        private fun gcd(a: Long, b: Long): Long {
            var x = a
            var y = b
            while (y != 0L) {
                val t = x % y
                x = y
                y = t
            }
            return if (x == 0L) 1L else x
        }

        private fun pow10(n: Int): Long {
            var r = 1L
            repeat(n) { r = Math.multiplyExact(r, 10L) }
            return r
        }
    }
}
