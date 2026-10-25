package com.example.kasirarn

import android.content.Context
import androidx.room.Room
import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.example.kasirarn.data.backup.BackupArchive
import com.example.kasirarn.data.backup.BackupError
import com.example.kasirarn.data.backup.BackupException
import com.example.kasirarn.data.db.ArnDatabase
import com.example.kasirarn.data.db.PayMethod
import com.example.kasirarn.data.db.TxStatus
import com.example.kasirarn.data.notify.SystemNotifier
import com.example.kasirarn.data.repo.AppError
import com.example.kasirarn.data.repo.CheckoutRequest
import com.example.kasirarn.data.repo.ExpenseDraft
import com.example.kasirarn.data.repo.ExpenseRepository
import com.example.kasirarn.data.repo.IngredientDraft
import com.example.kasirarn.data.repo.IngredientRepository
import com.example.kasirarn.data.repo.MenuDraft
import com.example.kasirarn.data.repo.MenuRepository
import com.example.kasirarn.data.repo.NotificationRepository
import com.example.kasirarn.data.repo.Op
import com.example.kasirarn.data.repo.RecipeDraftLine
import com.example.kasirarn.data.repo.ReportRepository
import com.example.kasirarn.data.repo.SalesRepository
import com.example.kasirarn.data.repo.TimeRange
import com.example.kasirarn.data.repo.Period
import com.example.kasirarn.data.settings.SettingsRepository
import com.example.kasirarn.data.settings.StockPolicy
import com.example.kasirarn.domain.CartLine
import com.example.kasirarn.domain.Dimension
import com.example.kasirarn.domain.Discount
import com.example.kasirarn.domain.DiscountType
import com.example.kasirarn.domain.Fraction
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.runBlocking
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Assert.fail
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import java.io.File

/** Alur inti pada database Room sungguhan (in-memory): restok, penjualan atomik, void, laporan, arsip cadangan. */
@RunWith(AndroidJUnit4::class)
class CoreFlowTest {
    private lateinit var context: Context
    private lateinit var db: ArnDatabase
    private lateinit var settings: SettingsRepository
    private lateinit var ingredients: IngredientRepository
    private lateinit var menus: MenuRepository
    private lateinit var sales: SalesRepository
    private lateinit var reports: ReportRepository
    private lateinit var expenses: ExpenseRepository

    private var coffeeId = 0L
    private var milkId = 0L
    private var menuId = 0L

    @Before
    fun setUp() = runBlocking {
        context = ApplicationProvider.getApplicationContext()
        db = Room.inMemoryDatabaseBuilder(context, ArnDatabase::class.java)
            .addCallback(object : androidx.room.RoomDatabase.Callback() {
                override fun onCreate(db: androidx.sqlite.db.SupportSQLiteDatabase) {
                    listOf("Bahan baku", "Susut", "Sewa").forEachIndexed { i, n ->
                        db.execSQL("INSERT INTO kategori_pengeluaran (id, nama) VALUES (${i + 1}, '$n')")
                    }
                    db.execSQL("INSERT INTO profil_usaha (id, nama, logo_path, alamat, telepon, mata_uang) VALUES (1, '', NULL, '', '', 'Rp')")
                }
            })
            .allowMainThreadQueries()
            .build()
        settings = SettingsRepository(context)
        settings.setStockPolicy(StockPolicy.BLOCK)
        val texts = com.example.kasirarn.data.notify.Texts { id, args -> context.getString(id, *args) }
        val notifications = NotificationRepository(db, settings, SystemNotifier(context, texts), texts)
        ingredients = IngredientRepository(db, notifications)
        menus = MenuRepository(db)
        sales = SalesRepository(db, settings, notifications)
        reports = ReportRepository(db)
        expenses = ExpenseRepository(db)

        coffeeId = (ingredients.save(draft("Kopi", Dimension.MASS, min = 100)) as Op.Ok).value
        milkId = (ingredients.save(draft("Susu", Dimension.VOLUME, min = 500)) as Op.Ok).value
        // Kopi 1 kg seharga 100.000 (100 per g); susu 2 liter seharga 30.000 (15 per ml).
        assertTrue(ingredients.restock(coffeeId, 1000, 100_000) is Op.Ok)
        assertTrue(ingredients.restock(milkId, 2000, 30_000) is Op.Ok)
        val categoryId = (menus.addCategory("Kopi") as Op.Ok).value
        menuId = (
            menus.save(
                MenuDraft(
                    id = null, name = "Kopi susu", categoryId = categoryId, price = 20_000, photo = null, active = true,
                    notes = "", recipe = listOf(RecipeDraftLine(coffeeId, 20), RecipeDraftLine(milkId, 150)),
                ),
            ) as Op.Ok
            ).value
    }

    @After
    fun tearDown() {
        db.close()
    }

    private fun draft(name: String, dim: Dimension, min: Long) = IngredientDraft(
        id = null, name = name, dimension = dim, purchaseUnit = dim.baseUnit, minStock = min,
        initialStock = 0, initialValue = 0, measures = emptyList(),
    )

    private fun request(qty: Int, paid: Long = 100_000, discount: Discount = Discount.NONE) = CheckoutRequest(
        lines = listOf(CartLine(menuId, qty)), discount = discount, method = PayMethod.CASH, paid = paid,
    )

    @Test
    fun restockCreatesExpenseAndWeightedAverage() = runBlocking {
        val ing = ingredients.get(coffeeId)!!
        assertEquals(1000L, ing.stokDasar)
        assertEquals(100_000L, ing.nilaiStok)
        val list = reports.observeExpenses(TimeRange.of(Period.TODAY, System.currentTimeMillis()), 10).first()
        assertEquals(2, list.size)
        assertEquals(130_000L, list.sumOf { it.nominal })
    }

    @Test
    fun checkoutDeductsStockAtomicallyAndStoresSnapshot() = runBlocking {
        val result = sales.checkout(request(qty = 2))
        assertTrue(result is Op.Ok)
        val txId = (result as Op.Ok).value
        // 2 porsi: kopi 40 g (4.000), susu 300 ml (4.500) => HPP 8.500
        assertEquals(960L, ingredients.get(coffeeId)!!.stokDasar)
        assertEquals(1700L, ingredients.get(milkId)!!.stokDasar)
        val tx = db.transactionDao().get(txId)!!
        assertEquals(40_000L, tx.total)
        assertEquals(60_000L, tx.kembalian)
        val item = db.transactionDao().getItems(txId).single()
        assertEquals(8_500L, item.hppTotal)
        assertEquals(4_250L, item.hppSatuan)
        assertEquals(2, db.transactionDao().getIngredientRows(txId).size)
    }

    @Test
    fun blockPolicyRejectsOversaleWithoutPartialWrites() = runBlocking {
        // Stok susu 2000 ml cukup untuk 13 porsi (150 ml), jadi 14 harus ditolak.
        val result = sales.checkout(request(qty = 14, paid = 1_000_000))
        assertTrue(result is Op.Fail)
        assertEquals(AppError.INSUFFICIENT_STOCK, (result as Op.Fail).error)
        assertEquals(1000L, ingredients.get(coffeeId)!!.stokDasar)
        assertEquals(2000L, ingredients.get(milkId)!!.stokDasar)
        assertEquals(0, db.transactionDao().countAll())
    }

    @Test
    fun warnPolicyAllowsNegativeStock() = runBlocking {
        settings.setStockPolicy(StockPolicy.WARN)
        val result = sales.checkout(request(qty = 14, paid = 1_000_000))
        assertTrue(result is Op.Ok)
        assertTrue(ingredients.get(milkId)!!.stokDasar < 0)
    }

    @Test
    fun voidRestoresStockFromSnapshotNotCurrentRecipe() = runBlocking {
        val txId = (sales.checkout(request(qty = 2)) as Op.Ok).value
        // Ubah resep setelah transaksi: pembatalan harus tetap memakai snapshot lama.
        val categoryId = menus.getMenu(menuId)!!.kategoriId
        menus.save(
            MenuDraft(menuId, "Kopi susu", categoryId, 20_000, null, true, "", listOf(RecipeDraftLine(coffeeId, 999))),
        )
        assertTrue(sales.voidTransaction(txId) is Op.Ok)
        assertEquals(1000L, ingredients.get(coffeeId)!!.stokDasar)
        assertEquals(2000L, ingredients.get(milkId)!!.stokDasar)
        assertEquals(100_000L, ingredients.get(coffeeId)!!.nilaiStok)
        assertEquals(TxStatus.VOID, db.transactionDao().get(txId)!!.status)
        val again = sales.voidTransaction(txId)
        assertEquals(AppError.ALREADY_VOID, (again as Op.Fail).error)
    }

    @Test
    fun reportsExcludeVoidAndKeepHistoricalCost() = runBlocking {
        val range = TimeRange.of(Period.TODAY, System.currentTimeMillis())
        sales.checkout(request(qty = 2))
        val second = (sales.checkout(request(qty = 1, discount = Discount(DiscountType.PERCENT, Fraction.of(10)))) as Op.Ok).value
        val report = reports.observeIncome(range).first()
        assertEquals(2, report.summary.txCount)
        assertEquals(40_000L + 18_000L, report.summary.total)
        assertEquals(2_000L, report.summary.discount)
        sales.voidTransaction(second)
        val after = reports.observeIncome(range).first()
        assertEquals(1, after.summary.txCount)
        assertEquals(40_000L, after.summary.total)
        assertEquals(8_500L, after.cogs)
        val fin = reports.observeFinancials(range).first()
        assertEquals(130_000L, fin.ingredientPurchases)
        // Laba tidak mengurangkan pembelian bahan; arus kas mengurangkannya.
        assertEquals(40_000L - 8_500L, fin.netProfit)
        assertEquals(40_000L - 130_000L, fin.cashFlow)
    }

    @Test
    fun manualExpenseAffectsProfitAndCashFlow() = runBlocking {
        val range = TimeRange.of(Period.TODAY, System.currentTimeMillis())
        sales.checkout(request(qty = 2))
        assertTrue(expenses.save(ExpenseDraft(null, System.currentTimeMillis(), 3, 5_000, "sewa")) is Op.Ok)
        val fin = reports.observeFinancials(range).first()
        assertEquals(40_000L - 8_500L - 5_000L, fin.netProfit)
        assertEquals(40_000L - 130_000L - 5_000L, fin.cashFlow)
        val reserved = expenses.save(ExpenseDraft(null, System.currentTimeMillis(), 1, 5_000, ""))
        assertEquals(AppError.RESERVED, (reserved as Op.Fail).error)
    }

    @Test
    fun ingredientInUseCannotBeDeletedAndHistoryArchives() = runBlocking {
        val blocked = ingredients.delete(coffeeId)
        assertEquals(AppError.IN_USE, (blocked as Op.Fail).error)
        val lonely = (ingredients.save(draft("Gula", Dimension.MASS, 0)) as Op.Ok).value
        assertTrue(ingredients.delete(lonely) is Op.Ok)
        val withHistory = (ingredients.save(draft("Teh", Dimension.MASS, 0)) as Op.Ok).value
        ingredients.restock(withHistory, 100, 1000)
        val r = ingredients.delete(withHistory) as Op.Ok
        assertEquals(com.example.kasirarn.data.repo.DeleteOutcome.Archived, r.value)
        assertTrue(ingredients.observeIngredients().first().none { it.id == withHistory })
    }

    @Test
    fun duplicateNamesAreRejected() = runBlocking {
        val r = ingredients.save(draft("kopi", Dimension.MASS, 0))
        assertEquals(AppError.NAME_DUPLICATE, (r as Op.Fail).error)
        assertEquals(AppError.NAME_DUPLICATE, (menus.addCategory("KOPI") as Op.Fail).error)
    }

    @Test
    fun backupArchiveRoundTripAndTamperDetection() {
        val dir = File(context.cacheDir, "bk_test").also { it.deleteRecursively(); it.mkdirs() }
        val staging = File(dir, "stage").also { File(it, "images").mkdirs() }
        val dbBytes = ByteArray(2048).also { java.util.Random(42).nextBytes(it) }
        File(staging, BackupArchive.DATABASE).writeBytes(dbBytes)
        File(staging, BackupArchive.SETTINGS).writeText("{}")
        File(staging, "images/a.jpg").writeBytes(byteArrayOf(1, 2, 3))
        val archive = File(dir, "x.arnbak")
        val counts = mapOf("menu" to 1L)
        BackupArchive.write(staging, archive, "1.0", 1, 1, 123L, counts)

        val inspector = { _: File -> com.example.kasirarn.data.backup.DbInfo(1, counts) }
        val manifest = BackupArchive.extractAndValidate(archive, File(dir, "out"), 1, inspector)
        assertEquals(3, manifest.files.size)

        // Cadangan dari skema lebih baru ditolak.
        try {
            BackupArchive.extractAndValidate(archive, File(dir, "out2"), 0) { com.example.kasirarn.data.backup.DbInfo(1, counts) }
            fail()
        } catch (e: BackupException) {
            assertEquals(BackupError.NEWER_SCHEMA, e.error)
        }

        // Ubah satu bita di dalam arsip: checksum harus menolak.
        val bytes = archive.readBytes()
        val index = indexOf(bytes, dbBytes.copyOfRange(0, 64))
        assertTrue(index > 0)
        bytes[index + 10] = (bytes[index + 10] + 1).toByte()
        val tampered = File(dir, "t.arnbak").also { it.writeBytes(bytes) }
        try {
            BackupArchive.extractAndValidate(tampered, File(dir, "out3"), 1, inspector)
            fail()
        } catch (e: BackupException) {
            assertTrue(e.error == BackupError.CHECKSUM_MISMATCH || e.error == BackupError.NOT_ARCHIVE)
        }

        // Bukan arsip.
        val junk = File(dir, "j.arnbak").also { it.writeText("bukan zip") }
        try {
            BackupArchive.extractAndValidate(junk, File(dir, "out4"), 1, inspector)
            fail()
        } catch (e: BackupException) {
            assertEquals(BackupError.NOT_ARCHIVE, e.error)
        }
        dir.deleteRecursively()
    }

    private fun indexOf(haystack: ByteArray, needle: ByteArray): Int {
        outer@ for (i in 0..haystack.size - needle.size) {
            for (j in needle.indices) if (haystack[i + j] != needle[j]) continue@outer
            return i
        }
        return -1
    }
}
