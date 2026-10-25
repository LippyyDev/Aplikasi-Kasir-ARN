package com.example.kasirarn.domain

enum class DiscountType { NOMINAL, PERCENT }

/** Diskon tingkat transaksi. [value] untuk persen berarti angka persen (2,5 = 2,5%). */
data class Discount(val type: DiscountType, val value: Fraction) {
    companion object {
        val NONE = Discount(DiscountType.NOMINAL, Fraction.ZERO)
    }
}

data class CartLine(val menuId: Long, val quantity: Int, val note: String = "")

object CartMath {
    /** Diskon dalam Rupiah, tidak pernah melebihi [subtotal] dan tidak pernah negatif. */
    fun discountAmount(subtotal: Long, discount: Discount): Long {
        if (subtotal <= 0 || discount.value.isZero || discount.value.isNegative) return 0
        val raw = when (discount.type) {
            DiscountType.NOMINAL -> discount.value.roundHalfUp()
            DiscountType.PERCENT -> (Fraction.of(subtotal) * discount.value / Fraction.of(100)).roundHalfUp()
        }
        return raw.coerceIn(0, subtotal)
    }

    fun change(total: Long, paid: Long): Long = paid - total
}
