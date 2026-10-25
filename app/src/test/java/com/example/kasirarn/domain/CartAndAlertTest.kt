package com.example.kasirarn.domain

import com.example.kasirarn.data.repo.StockAlerts
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class CartMathTest {
    @Test fun nominalDiscountNeverExceedsSubtotal() {
        assertEquals(5_000L, CartMath.discountAmount(20_000, Discount(DiscountType.NOMINAL, Fraction.of(5_000))))
        assertEquals(20_000L, CartMath.discountAmount(20_000, Discount(DiscountType.NOMINAL, Fraction.of(99_000))))
    }

    @Test fun percentDiscountRoundsHalfUp() {
        assertEquals(2_000L, CartMath.discountAmount(20_000, Discount(DiscountType.PERCENT, Fraction.of(10))))
        // 2,5% dari 1.010 = 25,25 -> 25 ; 2,5% dari 1.020 = 25,5 -> 26
        val pct = DecimalParser.parse("2,5")!!
        assertEquals(25L, CartMath.discountAmount(1_010, Discount(DiscountType.PERCENT, pct)))
        assertEquals(26L, CartMath.discountAmount(1_020, Discount(DiscountType.PERCENT, pct)))
        assertEquals(20_000L, CartMath.discountAmount(20_000, Discount(DiscountType.PERCENT, Fraction.of(150))))
    }

    @Test fun zeroOrEmptyGivesNoDiscount() {
        assertEquals(0L, CartMath.discountAmount(0, Discount(DiscountType.PERCENT, Fraction.of(10))))
        assertEquals(0L, CartMath.discountAmount(10_000, Discount.NONE))
    }

    @Test fun changeIsPaidMinusTotal() {
        assertEquals(8_000L, CartMath.change(62_000, 70_000))
    }
}

class StockAlertsTest {
    @Test fun alertsOnlyWhenCrossingFromAbove() {
        assertEquals(StockAlerts.Kind.LOW, StockAlerts.detect(oldQty = 600, newQty = 500, min = 500))
        assertNull(StockAlerts.detect(oldQty = 500, newQty = 400, min = 500)) // sudah menipis, tidak berulang
        assertEquals(StockAlerts.Kind.OUT, StockAlerts.detect(oldQty = 100, newQty = 0, min = 500))
        assertEquals(StockAlerts.Kind.OUT, StockAlerts.detect(oldQty = 900, newQty = -20, min = 500))
        assertNull(StockAlerts.detect(oldQty = 0, newQty = -10, min = 500)) // sudah habis
        assertNull(StockAlerts.detect(oldQty = 900, newQty = 800, min = 500))
        assertNull(StockAlerts.detect(oldQty = 900, newQty = 800, min = 0))
    }

    @Test fun alertsAgainAfterRecoveringAboveLimit() {
        assertNull(StockAlerts.detect(400, 300, 500))
        assertNull(StockAlerts.detect(300, 900, 500)) // restok
        assertEquals(StockAlerts.Kind.LOW, StockAlerts.detect(900, 450, 500))
    }
}
