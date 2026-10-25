package com.example.kasirarn

import android.content.Context
import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.example.kasirarn.data.db.ArnDatabase
import com.example.kasirarn.data.db.ExpenseSource
import com.example.kasirarn.data.db.TxStatus
import com.example.kasirarn.data.dummy.DummyDataException
import com.example.kasirarn.data.dummy.DummyDataLoader
import com.example.kasirarn.data.notify.Texts
import com.example.kasirarn.data.repo.ReportRepository
import com.example.kasirarn.data.repo.TimeRange
import com.example.kasirarn.data.settings.SettingsRepository
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.runBlocking
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Assert.fail
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith

/** Memuat berkas JSON data contoh bawaan ke database sungguhan dan memeriksa hasilnya. */
@RunWith(AndroidJUnit4::class)
class DummyDataTest {
    private lateinit var context: Context
    private lateinit var db: ArnDatabase
    private lateinit var loader: DummyDataLoader

    @Before
    fun setUp() {
        context = ApplicationProvider.getApplicationContext()
        context.deleteDatabase(ArnDatabase.FILE_NAME)
        db = ArnDatabase.build(context)
        val texts = Texts { id, args -> context.getString(id, *args) }
        loader = DummyDataLoader(context, db, SettingsRepository(context), texts)
    }

    @After
    fun tearDown() {
        db.close()
        context.deleteDatabase(ArnDatabase.FILE_NAME)
    }

    @Test
    fun bundledFileLoadsCompletelyAndConsistently() = runBlocking {
        assertTrue(loader.isEmpty())
        val summary = loader.loadBundled()

        assertEquals(11, summary.ingredients)
        assertEquals(12, summary.menus)
        assertEquals("tidak boleh ada penjualan ditolak", 0, summary.skippedSales)
        assertTrue(summary.transactions > 100)
        assertEquals(3, summary.voided)
        assertEquals(5, summary.expenses)
        assertFalse(loader.isEmpty())

        // Stok tidak pernah negatif, dan ada bahan yang menipis supaya status stok terlihat.
        val ingredients = db.ingredientDao().getActive()
        assertTrue(ingredients.all { it.stokDasar >= 0 })
        assertTrue(ingredients.any { it.stokMinimum > 0 && it.stokDasar <= it.stokMinimum })

        // Laporan 14 hari terisi, transaksi batal tidak masuk pemasukan.
        val now = System.currentTimeMillis()
        val range = TimeRange(com.example.kasirarn.data.repo.DayMath.addDays(com.example.kasirarn.data.repo.DayMath.startOfDay(now), -14), now + 1)
        val income = ReportRepository(db).observeIncome(range).first()
        assertEquals(summary.transactions - summary.voided, income.summary.txCount)
        assertTrue(income.summary.total > 0 && income.cogs > 0 && income.grossProfit > 0)
        assertTrue(income.topMenus.isNotEmpty() && income.byCategory.isNotEmpty() && income.byPayment.size == 2)

        // Restok dan susut tercatat otomatis sebagai pengeluaran; manual dari berkas.
        val expenses = db.expenseDao().observeBySource(0, now + 1).first().associate { it.label to it.cnt }
        assertEquals(5L, expenses[ExpenseSource.MANUAL])
        assertEquals(summary.restocks.toLong(), expenses[ExpenseSource.RESTOCK])
        assertEquals(1L, expenses[ExpenseSource.LOSS])

        // Transaksi batal tetap ada dengan status batal; nomor harian berurutan.
        val todayList = db.transactionDao().observeList(0, now + 1, 1000).first()
        assertEquals(3, todayList.count { it.status == TxStatus.VOID })
        val perDay = todayList.groupBy { it.kunciHari }
        assertTrue(perDay.values.all { list -> list.map { it.nomor }.sorted() == (1..list.size).toList() })

        // Menu: satu nonaktif, satu tanpa kategori, resep terisi.
        val menus = db.menuDao().observeAll().first()
        assertEquals(1, menus.count { !it.aktif })
        assertEquals(1, menus.count { it.kategoriId == null })
        // Riwayat notifikasi stok terbentuk dari kejadian nyata selama pemuatan.
        assertTrue(db.notificationDao().observeAll().first().isNotEmpty())
    }

    @Test
    fun refusesToLoadWhenDataAlreadyExists() = runBlocking {
        loader.loadBundled()
        try {
            loader.loadBundled()
            fail("harus menolak")
        } catch (e: DummyDataException) {
            // diharapkan
        }
    }

    @Test
    fun rejectsUnknownFileVersion() = runBlocking {
        try {
            loader.load("""{"versi": 99}""")
            fail("harus menolak")
        } catch (e: DummyDataException) {
            // diharapkan
        }
    }
}
