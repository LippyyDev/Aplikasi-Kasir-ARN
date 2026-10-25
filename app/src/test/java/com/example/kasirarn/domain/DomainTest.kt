package com.example.kasirarn.domain

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Assert.fail
import org.junit.Test

class FractionTest {
    @Test fun normalizesSignAndGcd() {
        val f = Fraction.of(-6, -8)
        assertEquals(3L, f.num)
        assertEquals(4L, f.den)
        assertEquals(Fraction.of(-1, 2), Fraction.of(1, -2))
    }

    @Test fun zeroHasUnitDenominator() {
        assertEquals(1L, Fraction.of(0, 7).den)
        assertTrue(Fraction.of(0, 7).isZero)
    }

    @Test fun zeroDenominatorThrows() {
        try {
            Fraction.of(1, 0)
            fail("harus melempar")
        } catch (e: ArithmeticException) {
            // diharapkan
        }
    }

    @Test fun overflowThrows() {
        try {
            Fraction.of(Long.MAX_VALUE) * Fraction.of(2)
            fail("harus melempar")
        } catch (e: ArithmeticException) {
            // diharapkan
        }
    }

    @Test fun roundsHalfUp() {
        assertEquals(1L, Fraction.of(1, 2).roundHalfUp())
        assertEquals(0L, Fraction.of(49, 100).roundHalfUp())
        assertEquals(3L, Fraction.of(5, 2).roundHalfUp())
        assertEquals(1L, Fraction.of(149, 100).roundHalfUp())
        assertEquals(-1L, Fraction.of(-1, 2).roundHalfUp())
        assertEquals(0L, Fraction.of(-49, 100).roundHalfUp())
    }

    @Test fun arithmetic() {
        assertEquals(Fraction.of(5, 6), Fraction.of(1, 2) + Fraction.of(1, 3))
        assertEquals(Fraction.of(1, 6), Fraction.of(1, 2) - Fraction.of(1, 3))
        assertEquals(Fraction.of(1, 6), Fraction.of(1, 2) * Fraction.of(1, 3))
        assertEquals(Fraction.of(3, 2), Fraction.of(1, 2) / Fraction.of(1, 3))
        assertTrue(Fraction.of(1, 3) < Fraction.of(1, 2))
    }

    @Test fun decimalString() {
        assertEquals("2,5", Fraction.of(5, 2).toDecimalString())
        assertEquals("2.5", Fraction.of(5, 2).toDecimalString(decimalSeparator = '.'))
        assertEquals("3", Fraction.of(3).toDecimalString())
        assertEquals("0,333", Fraction.of(1, 3).toDecimalString(3))
        assertEquals("0,667", Fraction.of(2, 3).toDecimalString(3))
        assertEquals("1", Fraction.of(9999, 10000).toDecimalString(3))
        assertEquals("-1,5", Fraction.of(-3, 2).toDecimalString())
    }
}

class DecimalParserTest {
    @Test fun acceptsCommaAndDot() {
        assertEquals(Fraction.of(5, 2), DecimalParser.parse("2,5"))
        assertEquals(Fraction.of(5, 2), DecimalParser.parse("2.5"))
        assertEquals(Fraction.of(1, 8), DecimalParser.parse("0,125"))
        assertEquals(Fraction.of(3), DecimalParser.parse(" 3 "))
        assertEquals(Fraction.of(1, 2), DecimalParser.parse(",5"))
        assertEquals(Fraction.of(2), DecimalParser.parse("2,"))
    }

    @Test fun rejectsBadInput() {
        assertNull(DecimalParser.parse(""))
        assertNull(DecimalParser.parse("   "))
        assertNull(DecimalParser.parse("abc"))
        assertNull(DecimalParser.parse("1,2,3"))
        assertNull(DecimalParser.parse("1.2.3"))
        assertNull(DecimalParser.parse("-1"))
        assertNull(DecimalParser.parse("1e3"))
        assertNull(DecimalParser.parse(","))
        assertNull(DecimalParser.parse("0,1234567"))
    }

    @Test fun moneyParserIgnoresGrouping() {
        assertEquals(12500L, MoneyParser.parse("12.500"))
        assertEquals(12500L, MoneyParser.parse("Rp 12,500"))
        assertNull(MoneyParser.parse(""))
        assertNull(MoneyParser.parse("abc"))
    }
}

class UnitConverterTest {
    private val gram = Dimension.MASS.standardUnits[0]
    private val kg = Dimension.MASS.standardUnits[1]
    private val sdm = UnitDef("sendok makan", Fraction.of(12))
    private val sdt = UnitDef("sendok teh", Fraction.of(5, 2))

    @Test fun standardUnits() {
        assertEquals(1000L, UnitConverter.toBase(Fraction.ONE, kg))
        assertEquals(250L, UnitConverter.toBase(Fraction.of(1, 4), kg))
        assertEquals(1500L, UnitConverter.toBase(DecimalParser.parse("1,5")!!, kg))
    }

    @Test fun customUnits() {
        assertEquals(12L, UnitConverter.toBase(Fraction.ONE, sdm))
        assertEquals(30L, UnitConverter.toBase(DecimalParser.parse("2,5")!!, sdm))
        // 1 sendok teh = 2,5 g -> 3 sendok teh = 7,5 g -> 8 g (half up)
        assertEquals(8L, UnitConverter.toBase(Fraction.of(3), sdt))
        // 1 sendok teh = 2,5 g -> 1 sendok teh = 2,5 g -> 3 g (half up)
        assertEquals(3L, UnitConverter.toBase(Fraction.ONE, sdt))
    }

    @Test fun halfUpBoundary() {
        val half = UnitDef("setengah", Fraction.of(1, 2))
        assertEquals(1L, UnitConverter.toBase(Fraction.ONE, half))
        assertEquals(0L, UnitConverter.toBase(Fraction.of(49, 100), gram))
        assertEquals(1L, UnitConverter.toBase(Fraction.of(1, 2), gram))
    }

    @Test fun fromBase() {
        assertEquals("1,5", UnitConverter.fromBase(1500, kg).toDecimalString())
        assertEquals("2,5", UnitConverter.fromBase(30, UnitDef("x", Fraction.of(12))).toDecimalString())
    }

    @Test fun autoDisplay() {
        assertEquals("250 g", UnitConverter.autoDisplay(250, Dimension.MASS))
        assertEquals("1,5 kg", UnitConverter.autoDisplay(1500, Dimension.MASS))
        assertEquals("2 liter", UnitConverter.autoDisplay(2000, Dimension.VOLUME))
        assertEquals("12 buah", UnitConverter.autoDisplay(12, Dimension.COUNT))
        assertEquals("-300 ml", UnitConverter.autoDisplay(-300, Dimension.VOLUME))
    }
}

class StockMathTest {
    @Test fun weightedAverageAfterTwoPurchases() {
        // 1000 g seharga 20.000, lalu 1000 g seharga 30.000 -> 2000 g bernilai 50.000
        var s = StockState(0, 0)
        s = StockMath.restock(s, 1000, 20_000)
        s = StockMath.restock(s, 1000, 30_000)
        assertEquals(2000L, s.qty)
        assertEquals(50_000L, s.value)
        // pakai 500 g -> biaya 12.500 (rata-rata 25 per g)
        val r = StockMath.consume(s, 500)
        assertEquals(12_500L, r.cost)
        assertEquals(1500L, r.state.qty)
        assertEquals(37_500L, r.state.value)
        assertTrue(r.costKnown)
    }

    @Test fun consumingEverythingZeroesValue() {
        val s = StockMath.restock(StockState(0, 0), 3, 10)
        val r = StockMath.consume(s, 3)
        assertEquals(10L, r.cost)
        assertEquals(0L, r.state.qty)
        assertEquals(0L, r.state.value)
    }

    @Test fun roundingAcrossSeveralUsesNeverLeaksValue() {
        // 3 g seharga 10: tiga kali pakai 1 g; total biaya harus tepat 10.
        var s = StockMath.restock(StockState(0, 0), 3, 10)
        var total = 0L
        repeat(3) {
            val r = StockMath.consume(s, 1)
            total += r.cost
            s = r.state
        }
        assertEquals(10L, total)
        assertEquals(0L, s.value)
    }

    @Test fun reverseRestoresExactly() {
        val s = StockMath.restock(StockState(0, 0), 2000, 50_000)
        val r = StockMath.consume(s, 700)
        val back = StockMath.reverse(r.state, 700, r.cost)
        assertEquals(s.qty, back.qty)
        assertEquals(s.value, back.value)
    }

    @Test fun negativeStockUsesLastPurchasePrice() {
        var s = StockMath.restock(StockState(0, 0), 1000, 20_000) // 20 per g
        s = StockMath.consume(s, 1000).state
        assertEquals(0L, s.qty)
        val r = StockMath.consume(s, 100)
        assertEquals(-100L, r.state.qty)
        assertEquals(2000L, r.cost)
        assertTrue(r.costKnown)
        assertEquals(0L, r.state.value)
    }

    @Test fun neverPurchasedIsFlaggedUnknown() {
        val r = StockMath.consume(StockState(50, 0), 10)
        assertFalse(r.costKnown)
        assertEquals(0L, r.cost)
        assertEquals(40L, r.state.qty)
    }

    @Test fun usageLargerThanStockSplitsCost() {
        // 100 g seharga 1000 (10 per g); pakai 150 -> 100 g dari stok (1000) + 50 g harga terakhir (500)
        val s = StockMath.restock(StockState(0, 0), 100, 1000)
        val r = StockMath.consume(s, 150)
        assertEquals(1500L, r.cost)
        assertEquals(-50L, r.state.qty)
        assertEquals(0L, r.state.value)
    }

    @Test fun adjustDownRecordsLoss() {
        val s = StockMath.restock(StockState(0, 0), 1000, 10_000)
        val (ns, change) = StockMath.adjustTo(s, 800)
        assertEquals(800L, ns.qty)
        assertEquals(8_000L, ns.value)
        assertEquals(-2_000L, change)
    }

    @Test fun adjustUpAddsValueAtAverage() {
        val s = StockMath.restock(StockState(0, 0), 1000, 10_000)
        val (ns, change) = StockMath.adjustTo(s, 1100)
        assertEquals(1100L, ns.qty)
        assertEquals(11_000L, ns.value)
        assertEquals(1_000L, change)
    }

    @Test fun proportionRoundsHalfUp() {
        assertEquals(1L, StockMath.proportion(1, 1, 2))
        assertEquals(0L, StockMath.proportion(1, 1, 3))
        assertEquals(1L, StockMath.proportion(2, 1, 3))
    }
}

class AvailabilityAndCostTest {
    @Test fun portionsIsMinimumOfFloors() {
        val recipe = listOf(RecipeLine(1, 20), RecipeLine(2, 150))
        assertEquals(6L, Availability.portions(recipe, mapOf(1L to 150L, 2L to 1000L)))
        assertEquals(0L, Availability.portions(recipe, mapOf(1L to 500L, 2L to 149L)))
        assertEquals(0L, Availability.portions(recipe, mapOf(1L to 500L)))
        assertEquals(0L, Availability.portions(recipe, mapOf(1L to -5L, 2L to 1000L)))
    }

    @Test fun emptyRecipeIsUnlimited() {
        assertNull(Availability.portions(emptyList(), emptyMap()))
    }

    @Test fun portionCostFromAverage() {
        val states = mapOf(
            1L to StockMath.restock(StockState(0, 0), 1000, 20_000), // 20/g
            2L to StockMath.restock(StockState(0, 0), 1000, 5_000), // 5/ml
        )
        val c = CostCalculator.perPortion(listOf(RecipeLine(1, 15), RecipeLine(2, 200)), states)
        assertEquals(15 * 20L + 200 * 5L, c.total)
        assertTrue(c.complete)
    }

    @Test fun portionCostFlagsMissingAverage() {
        val states = mapOf(1L to StockState(0, 0))
        val c = CostCalculator.perPortion(listOf(RecipeLine(1, 15)), states)
        assertFalse(c.complete)
        assertEquals(0L, c.total)
    }

    @Test fun historicalCostDoesNotChangeWhenPriceMoves() {
        var s = StockMath.restock(StockState(0, 0), 1000, 10_000)
        val first = StockMath.consume(s, 100)
        val storedCost = first.cost // dicatat pada baris transaksi
        s = StockMath.restock(first.state, 1000, 90_000) // harga naik drastis
        val second = StockMath.consume(s, 100)
        assertEquals(1_000L, storedCost)
        assertTrue(second.cost > storedCost)
        assertEquals(1_000L, storedCost) // nilai tersimpan tidak ikut berubah
    }
}
