package com.example.kasirarn.data.dummy

import android.content.Context
import com.example.kasirarn.data.db.ArnDatabase
import com.example.kasirarn.data.db.PayMethod
import com.example.kasirarn.data.db.BusinessProfileEntity
import com.example.kasirarn.data.notify.SystemNotifier
import com.example.kasirarn.data.notify.Texts
import com.example.kasirarn.data.repo.AppError
import com.example.kasirarn.data.repo.CheckoutRequest
import com.example.kasirarn.data.repo.Clock
import com.example.kasirarn.data.repo.DayMath
import com.example.kasirarn.data.repo.ExpenseDraft
import com.example.kasirarn.data.repo.ExpenseRepository
import com.example.kasirarn.data.repo.IngredientDraft
import com.example.kasirarn.data.repo.IngredientRepository
import com.example.kasirarn.data.repo.MeasureDraft
import com.example.kasirarn.data.repo.MenuDraft
import com.example.kasirarn.data.repo.MenuRepository
import com.example.kasirarn.data.repo.NotificationRepository
import com.example.kasirarn.data.repo.Op
import com.example.kasirarn.data.repo.ProfileRepository
import com.example.kasirarn.data.repo.RecipeDraftLine
import com.example.kasirarn.data.repo.SalesRepository
import com.example.kasirarn.data.settings.SettingsRepository
import com.example.kasirarn.domain.CartLine
import com.example.kasirarn.domain.DecimalParser
import com.example.kasirarn.domain.Dimension
import com.example.kasirarn.domain.Discount
import com.example.kasirarn.domain.DiscountType
import com.example.kasirarn.domain.Fraction
import com.example.kasirarn.domain.UnitConverter
import com.example.kasirarn.domain.UnitDef
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.withContext
import org.json.JSONArray
import org.json.JSONObject

data class DummySummary(
    val ingredients: Int,
    val menus: Int,
    val transactions: Int,
    val voided: Int,
    val restocks: Int,
    val expenses: Int,
    /** Penjualan di file yang ditolak aplikasi (misalnya karena stok); idealnya nol. */
    val skippedSales: Int,
)

class DummyDataException(message: String) : Exception(message)

/** Tiga ukuran data contoh: sedikit (3 hari), sedang (14 hari), banyak (45 hari, ribuan baris riwayat). */
enum class DummyScenario(val asset: String) {
    FEW("data_contoh/data_contoh_sedikit.json"),
    MEDIUM("data_contoh/data_contoh.json"),
    MANY("data_contoh/data_contoh_banyak.json"),
}

/**
 * Memuat berkas JSON data contoh lewat repositori yang sama dengan yang dipakai layar, sehingga stok,
 * HPP, pengeluaran, pergerakan stok, dan notifikasi terbentuk persis seperti pemakaian sungguhan.
 * Waktu setiap kejadian diatur lewat [Clock] sendiri agar riwayatnya tersebar selama beberapa hari.
 */
class DummyDataLoader(
    private val context: Context,
    private val db: ArnDatabase,
    private val settings: SettingsRepository,
    private val texts: Texts,
) {
    suspend fun isEmpty(): Boolean = withContext(Dispatchers.IO) {
        val sqlite = db.openHelper.readableDatabase
        listOf("bahan_baku", "menu", "kategori_menu", "transaksi").all { table ->
            sqlite.query("SELECT COUNT(*) FROM $table").use { c -> c.moveToFirst() && c.getLong(0) == 0L }
        }
    }

    suspend fun loadBundled(
        scenario: DummyScenario = DummyScenario.MEDIUM,
        now: Long = System.currentTimeMillis(),
    ): DummySummary {
        val json = withContext(Dispatchers.IO) {
            context.assets.open(scenario.asset).bufferedReader(Charsets.UTF_8).use { it.readText() }
        }
        return load(json, now)
    }

    suspend fun load(jsonText: String, now: Long = System.currentTimeMillis()): DummySummary = withContext(Dispatchers.IO) {
        if (!isEmpty()) throw DummyDataException("Data tidak kosong")
        val root = JSONObject(jsonText)
        if (root.optInt("versi") != 1) throw DummyDataException("Versi berkas data contoh tidak dikenal")

        var current = now
        val clock = Clock { current }
        val notifier = SystemNotifier(context, texts)
        val notifications = NotificationRepository(db, settings, notifier, texts, clock, postSystem = false)
        val ingredients = IngredientRepository(db, notifications, clock)
        val menus = MenuRepository(db, clock)
        val sales = SalesRepository(db, settings, notifications, clock)
        val expenses = ExpenseRepository(db)
        val today = DayMath.startOfDay(now)
        fun timeOf(daysAgo: Int, hour: Int, minute: Int) =
            minOf(DayMath.addDays(today, -daysAgo) + hour * HOUR + minute * MINUTE, now)

        val firstDay = (root.getJSONArray("kejadian").maxOfInt { it.getInt("hariLalu") }).coerceAtLeast(0)
        current = timeOf(firstDay, 6, 0)

        // Profil usaha
        root.optJSONObject("profil")?.let {
            ProfileRepository(db).save(
                BusinessProfileEntity(
                    nama = it.optString("nama"), alamat = it.optString("alamat"),
                    telepon = it.optString("telepon"), mataUang = it.optString("mataUang", "Rp"),
                ),
            )
        }

        // Bahan baku
        val ingredientIds = HashMap<String, Long>()
        val ingredientUnits = HashMap<String, List<UnitDef>>()
        val ingredientDimension = HashMap<String, Dimension>()
        val ingredientArr = root.getJSONArray("bahan")
        for (i in 0 until ingredientArr.length()) {
            val b = ingredientArr.getJSONObject(i)
            val name = b.getString("nama")
            val dim = Dimension.valueOf(b.getString("dimensi"))
            val measures = b.optJSONArray("takaran").toList().map {
                val f = fraction(it.getString("setara"))
                MeasureDraft(it.getString("nama"), f.num, f.den)
            }
            val units = dim.standardUnits + measures.map { UnitDef(it.name, Fraction.of(it.num, it.den)) }
            val initial = b.optJSONObject("stokAwal")
            val draft = IngredientDraft(
                id = null, name = name, dimension = dim, purchaseUnit = b.getString("satuanBeli"),
                minStock = fraction(b.optString("stokMinimum", "0")).roundHalfUp(),
                initialStock = initial?.let { quantity(it.getString("jumlah"), it.getString("satuan"), units) } ?: 0,
                initialValue = initial?.optLong("nilai") ?: 0,
                measures = measures,
            )
            val saved = ingredients.save(draft)
            ingredientIds[name] = (saved as? Op.Ok)?.value ?: throw DummyDataException("Bahan gagal disimpan: $name")
            ingredientUnits[name] = units
            ingredientDimension[name] = dim
        }

        // Kategori
        val categoryIds = HashMap<String, Long>()
        val categories = root.getJSONArray("kategori")
        for (i in 0 until categories.length()) {
            val name = categories.getString(i)
            categoryIds[name] = (menus.addCategory(name) as? Op.Ok)?.value ?: throw DummyDataException("Kategori gagal: $name")
        }

        // Menu dan resep
        val menuIds = HashMap<String, Long>()
        val menuArr = root.getJSONArray("menu")
        for (i in 0 until menuArr.length()) {
            val m = menuArr.getJSONObject(i)
            val name = m.getString("nama")
            val recipe = m.getJSONArray("resep").toList().map { line ->
                val ing = line.getString("bahan")
                RecipeDraftLine(
                    ingredientIds[ing] ?: throw DummyDataException("Bahan tidak dikenal di resep: $ing"),
                    quantity(line.getString("jumlah"), line.getString("satuan"), ingredientUnits.getValue(ing)),
                )
            }
            val result = menus.save(
                MenuDraft(
                    id = null, name = name, categoryId = if (m.isNull("kategori")) null else categoryIds[m.getString("kategori")],
                    price = m.getLong("harga"), photo = null, active = m.optBoolean("aktif", true),
                    notes = m.optString("catatan"), recipe = recipe,
                ),
            )
            menuIds[name] = (result as? Op.Ok)?.value ?: throw DummyDataException("Menu gagal disimpan: $name")
        }

        // Kejadian berurutan waktu: restok, penjualan, pembatalan, opname
        data class Event(val time: Long, val json: JSONObject)
        val events = root.getJSONArray("kejadian").toList()
            .map { Event(timeOf(it.getInt("hariLalu"), it.getInt("jam"), it.getInt("menit")), it) }
            .sortedBy { it.time }
        var transactions = 0
        var voided = 0
        var restocks = 0
        var skipped = 0
        for (e in events) {
            current = e.time
            val ev = e.json
            when (ev.getString("jenis")) {
                "restok" -> {
                    val name = ev.getString("bahan")
                    val qty = quantity(ev.getString("jumlah"), ev.getString("satuan"), ingredientUnits.getValue(name))
                    if (ingredients.restock(ingredientIds.getValue(name), qty, ev.getLong("harga")) is Op.Ok) restocks++
                }
                "jual" -> {
                    val lines = ev.getJSONArray("item").toList().map {
                        CartLine(menuIds.getValue(it.getString("menu")), it.getInt("qty"), it.optString("catatan"))
                    }
                    val method = ev.optString("bayar", PayMethod.CASH)
                    val discount = ev.optJSONObject("diskon")?.let {
                        Discount(
                            if (it.getString("jenis") == "PERSEN") DiscountType.PERCENT else DiscountType.NOMINAL,
                            fraction(it.getString("nilai")),
                        )
                    } ?: Discount.NONE
                    val result = sales.checkout(CheckoutRequest(lines, discount, method, ev.optLong("uang", 0)))
                    when {
                        result is Op.Ok -> {
                            transactions++
                            if (ev.optBoolean("dibatalkan")) {
                                current = e.time + 10 * MINUTE
                                if (sales.voidTransaction(result.value) is Op.Ok) voided++
                            }
                        }
                        result is Op.Fail && result.error == AppError.INSUFFICIENT_STOCK -> skipped++
                        else -> skipped++
                    }
                }
                "opname" -> {
                    val name = ev.getString("bahan")
                    val physical = quantity(ev.getString("fisik"), ev.getString("satuan"), ingredientUnits.getValue(name))
                    ingredients.adjust(ingredientIds.getValue(name), physical, ev.getString("alasan"), ev.optBoolean("catatSusut"))
                }
            }
        }

        // Pengeluaran manual
        val categoryByName = expenses.observeCategories().first().associate { it.nama to it.id }
        var expenseCount = 0
        val expenseArr = root.optJSONArray("pengeluaran")
        for (i in 0 until (expenseArr?.length() ?: 0)) {
            val x = expenseArr!!.getJSONObject(i)
            val categoryId = categoryByName[x.getString("kategori")] ?: continue
            val date = DayMath.addDays(today, -x.getInt("hariLalu")) + 12 * HOUR
            if (expenses.save(ExpenseDraft(null, minOf(date, now), categoryId, x.getLong("nominal"), x.optString("catatan"))) is Op.Ok) {
                expenseCount++
            }
        }

        DummySummary(
            ingredients = ingredientIds.size, menus = menuIds.size, transactions = transactions,
            voided = voided, restocks = restocks, expenses = expenseCount, skippedSales = skipped,
        )
    }

    private fun fraction(text: String): Fraction =
        DecimalParser.parse(text) ?: throw DummyDataException("Angka tidak valid: $text")

    /** Jumlah [amount] dalam satuan bernama [unit] menjadi satuan dasar. */
    private fun quantity(amount: String, unit: String, units: List<UnitDef>): Long {
        val def = units.firstOrNull { it.name.equals(unit, ignoreCase = true) }
            ?: throw DummyDataException("Satuan tidak dikenal: $unit")
        return UnitConverter.toBase(fraction(amount), def)
    }

    private fun JSONArray?.toList(): List<JSONObject> =
        if (this == null) emptyList() else (0 until length()).map { getJSONObject(it) }

    private fun JSONArray.maxOfInt(selector: (JSONObject) -> Int): Int = toList().maxOfOrNull(selector) ?: 0

    companion object {
        const val ASSET = "data_contoh/data_contoh.json" // skenario sedang
        private const val MINUTE = 60_000L
        private const val HOUR = 3_600_000L
    }
}
