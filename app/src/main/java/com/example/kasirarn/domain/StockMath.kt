package com.example.kasirarn.domain

/**
 * Keadaan stok satu bahan. Semua angka bilangan bulat dalam satuan dasar atau Rupiah.
 *
 * [lastCostValue] dan [lastCostQty] menyimpan pembelian terakhir (total harga dan jumlahnya)
 * sebagai harga cadangan saat stok sudah nol atau negatif, supaya HPP tidak jatuh ke nol.
 */
data class StockState(
    val qty: Long,
    val value: Long,
    val lastCostValue: Long = 0,
    val lastCostQty: Long = 0,
) {
    /** Rata-rata tertimbang diketahui hanya bila ada stok yang punya nilai. */
    val hasAverage: Boolean get() = qty > 0 && value > 0
}

data class ConsumeResult(
    val state: StockState,
    val cost: Long,
    /** False bila ada bagian pemakaian yang tidak punya dasar harga sama sekali. */
    val costKnown: Boolean,
)

/** Aritmetika stok dengan metode rata-rata tertimbang bergerak. */
object StockMath {

    fun restock(s: StockState, addQty: Long, totalCost: Long): StockState {
        require(addQty > 0) { "Jumlah restok harus lebih dari nol" }
        require(totalCost >= 0) { "Harga tidak boleh negatif" }
        return StockState(
            qty = s.qty + addQty,
            value = s.value + totalCost,
            lastCostValue = totalCost,
            lastCostQty = addQty,
        )
    }

    /** Biaya satu kali pemakaian [need] tanpa mengubah keadaan, untuk perkiraan HPP. */
    fun estimateCost(s: StockState, need: Long): ConsumeResult = consume(s, need)

    fun consume(s: StockState, need: Long): ConsumeResult {
        require(need >= 0) { "Pemakaian tidak boleh negatif" }
        if (need == 0L) return ConsumeResult(s, 0, true)

        var cost: Long
        var known: Boolean
        if (s.hasAverage) {
            val fromStock = minOf(need, s.qty)
            val stockCost = if (fromStock == s.qty) s.value else proportion(s.value, fromStock, s.qty)
            val rest = need - fromStock
            val restCost = if (rest > 0 && s.lastCostQty > 0) proportion(s.lastCostValue, rest, s.lastCostQty) else 0L
            cost = stockCost + restCost
            known = rest == 0L || s.lastCostQty > 0
            val newValue = (s.value - stockCost).coerceAtLeast(0)
            return ConsumeResult(s.copy(qty = s.qty - need, value = newValue), cost, known)
        }
        // Tidak ada rata-rata: pakai harga pembelian terakhir bila ada.
        known = s.lastCostQty > 0
        cost = if (known) proportion(s.lastCostValue, need, s.lastCostQty) else 0L
        return ConsumeResult(s.copy(qty = s.qty - need, value = if (s.qty - need <= 0) 0 else s.value), cost, known)
    }

    /** Kebalikan [consume] dari data snapshot (pembatalan transaksi). */
    fun reverse(s: StockState, qty: Long, cost: Long): StockState =
        s.copy(qty = s.qty + qty, value = s.value + cost)

    /** Penyesuaian opname ke [physicalQty]; nilai disesuaikan proporsional. */
    fun adjustTo(s: StockState, physicalQty: Long): Pair<StockState, Long> {
        val diff = physicalQty - s.qty
        if (diff == 0L) return s to 0L
        return if (diff < 0) {
            val r = consume(s, -diff)
            // Stok yang sudah negatif lalu ditambah: kembalikan ke nol nilainya.
            r.state to -r.cost
        } else {
            val valueChange = if (s.hasAverage) proportion(s.value, diff, s.qty)
            else if (s.lastCostQty > 0) proportion(s.lastCostValue, diff, s.lastCostQty) else 0L
            s.copy(qty = physicalQty, value = s.value + valueChange) to valueChange
        }
    }

    /** `round(value * part / whole)` setengah naik, memakai BigInteger agar tidak melimpah. */
    fun proportion(value: Long, part: Long, whole: Long): Long {
        if (whole == 0L) return 0
        val num = java.math.BigInteger.valueOf(value).multiply(java.math.BigInteger.valueOf(part))
        val den = java.math.BigInteger.valueOf(whole)
        val two = java.math.BigInteger.valueOf(2)
        val rounded = num.multiply(two).add(den).divide(den.multiply(two))
        // BigInteger.longValueExact baru ada di API 31; periksa rentang sendiri agar jalan di Android 8.
        if (rounded.bitLength() > 63) throw ArithmeticException("Hasil di luar rentang Long")
        return rounded.toLong()
    }
}

data class RecipeLine(val ingredientId: Long, val qty: Long)

object Availability {
    /**
     * Jumlah porsi yang masih dapat dibuat dari stok. Null berarti tak terbatas (resep kosong,
     * misalnya produk titipan).
     */
    fun portions(recipe: List<RecipeLine>, stock: Map<Long, Long>): Long? {
        if (recipe.isEmpty()) return null
        var min = Long.MAX_VALUE
        for (line in recipe) {
            if (line.qty <= 0) continue
            val have = stock[line.ingredientId] ?: 0L
            val p = if (have <= 0) 0L else have / line.qty
            if (p < min) min = p
        }
        return if (min == Long.MAX_VALUE) null else min
    }
}

data class PortionCost(val total: Long, val complete: Boolean)

object CostCalculator {
    /** HPP satu porsi dari resep berdasarkan rata-rata saat ini. */
    fun perPortion(recipe: List<RecipeLine>, states: Map<Long, StockState>): PortionCost {
        var total = 0L
        var complete = true
        for (line in recipe) {
            val s = states[line.ingredientId]
            if (s == null) {
                complete = false
                continue
            }
            val r = if (s.hasAverage) {
                ConsumeResult(s, StockMath.proportion(s.value, line.qty, s.qty), true)
            } else {
                // Tanpa rata-rata: harga pembelian terakhir; tanpa itu juga, tandai belum lengkap.
                if (s.lastCostQty > 0) {
                    ConsumeResult(s, StockMath.proportion(s.lastCostValue, line.qty, s.lastCostQty), true)
                } else {
                    ConsumeResult(s, 0, false)
                }
            }
            total += r.cost
            if (!r.costKnown) complete = false
        }
        return PortionCost(total, complete)
    }
}
