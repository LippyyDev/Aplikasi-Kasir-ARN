package com.example.kasirarn.data.repo

import androidx.room.withTransaction
import com.example.kasirarn.data.db.ArnDatabase
import com.example.kasirarn.data.db.BusinessProfileEntity
import com.example.kasirarn.data.db.ExpenseCategoryEntity
import com.example.kasirarn.data.db.ExpenseEntity
import com.example.kasirarn.data.db.ExpenseRow
import com.example.kasirarn.data.db.ExpenseSource
import com.example.kasirarn.data.db.GroupRow
import com.example.kasirarn.data.db.ReservedExpenseCategory
import com.example.kasirarn.data.db.SalesSummaryRow
import com.example.kasirarn.data.db.TopMenuRow
import com.example.kasirarn.data.db.TransactionEntity
import com.example.kasirarn.data.db.TrendRow
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.distinctUntilChanged

data class IncomeReport(
    val summary: SalesSummaryRow,
    val cogs: Long,
    val trend: List<TrendRow>,
    /** True bila [trend] berisi jam (0 sampai 23), false bila berisi hari (hari epoch lokal). */
    val hourly: Boolean,
    val topMenus: List<TopMenuRow>,
    val byCategory: List<GroupRow>,
    val byPayment: List<GroupRow>,
) {
    val grossProfit: Long get() = summary.total - cogs
    val average: Long get() = if (summary.txCount == 0) 0 else summary.total / summary.txCount
}

/**
 * Dua pandangan yang sengaja dipisah:
 * - Laba (akrual sederhana): penjualan bersih - HPP - biaya operasional. Pembelian bahan tidak dihitung
 *   di sini karena pemakaiannya sudah masuk HPP.
 * - Arus kas: penjualan bersih - semua uang keluar (pembelian bahan + biaya manual).
 */
data class Financials(
    val netSales: Long,
    val cogs: Long,
    val operating: Long,
    val ingredientPurchases: Long,
    val manualExpenses: Long,
) {
    val grossProfit: Long get() = netSales - cogs
    val netProfit: Long get() = grossProfit - operating
    val cashOut: Long get() = ingredientPurchases + manualExpenses
    val cashFlow: Long get() = netSales - cashOut
}

data class ExpenseReport(
    val total: Long,
    val byCategory: List<GroupRow>,
    val trend: List<TrendRow>,
)

class ReportRepository(db: ArnDatabase) {
    private val tx = db.transactionDao()
    private val ex = db.expenseDao()

    fun observeIncome(range: TimeRange): Flow<IncomeReport> {
        val offset = DayMath.offsetMs(range.from)
        val trend = if (range.isSingleDay) tx.observeTrendByHour(range.from, range.to, offset)
        else tx.observeTrendByDay(range.from, range.to, offset)
        val head = combine(
            tx.observeSummary(range.from, range.to),
            tx.observeCogs(range.from, range.to),
            trend,
        ) { s, c, t -> Triple(s, c, t) }
        val tail = combine(
            tx.observeTopMenus(range.from, range.to),
            tx.observeByCategory(range.from, range.to),
            tx.observeByPayment(range.from, range.to),
        ) { a, b, c -> Triple(a, b, c) }
        return combine(head, tail) { h, t ->
            IncomeReport(h.first, h.second, h.third, range.isSingleDay, t.first, t.second, t.third)
        }.distinctUntilChanged()
    }

    fun observeTransactions(range: TimeRange, limit: Int): Flow<List<TransactionEntity>> =
        tx.observeList(range.from, range.to, limit)

    fun observeFinancials(range: TimeRange): Flow<Financials> = combine(
        tx.observeSummary(range.from, range.to),
        tx.observeCogs(range.from, range.to),
        ex.observeBySource(range.from, range.to),
    ) { s, cogs, sources ->
        val bySource = sources.associate { it.label to it.amount }
        val manual = bySource[ExpenseSource.MANUAL] ?: 0L
        val loss = bySource[ExpenseSource.LOSS] ?: 0L
        val restock = bySource[ExpenseSource.RESTOCK] ?: 0L
        Financials(
            netSales = s.total,
            cogs = cogs,
            operating = manual + loss,
            ingredientPurchases = restock,
            manualExpenses = manual,
        )
    }

    fun observeExpenseReport(range: TimeRange): Flow<ExpenseReport> = combine(
        ex.observeByCategory(range.from, range.to),
        ex.observeTrendByDay(range.from, range.to, DayMath.offsetMs(range.from)),
    ) { cats, trend -> ExpenseReport(cats.sumOf { it.amount }, cats, trend) }

    fun observeExpenses(range: TimeRange, limit: Int): Flow<List<ExpenseRow>> =
        ex.observeList(range.from, range.to, limit)
}

data class ExpenseDraft(
    val id: Long?,
    val date: Long,
    val categoryId: Long,
    val amount: Long,
    val note: String,
)

class ExpenseRepository(private val db: ArnDatabase) {
    private val dao = db.expenseDao()

    fun observeCategories() = dao.observeCategories()

    suspend fun save(draft: ExpenseDraft): Op<Long> {
        if (draft.amount <= 0) return Op.Fail(AppError.INVALID_NUMBER)
        if (ReservedExpenseCategory.isReserved(draft.categoryId)) return Op.Fail(AppError.RESERVED)
        return try {
            db.withTransaction {
                if (draft.id == null) {
                    Op.Ok(
                        dao.insert(
                            ExpenseEntity(
                                tanggal = draft.date,
                                kategoriId = draft.categoryId,
                                nominal = draft.amount,
                                catatan = draft.note.trim(),
                                sumber = ExpenseSource.MANUAL,
                            ),
                        ),
                    )
                } else {
                    val cur = dao.get(draft.id) ?: return@withTransaction Op.Fail(AppError.NOT_FOUND)
                    if (cur.sumber != ExpenseSource.MANUAL) return@withTransaction Op.Fail(AppError.RESERVED)
                    dao.update(
                        cur.copy(
                            tanggal = draft.date,
                            kategoriId = draft.categoryId,
                            nominal = draft.amount,
                            catatan = draft.note.trim(),
                        ),
                    )
                    Op.Ok(cur.id)
                }
            }
        } catch (e: Exception) {
            Op.Fail(AppError.STORAGE, e.message)
        }
    }

    suspend fun delete(id: Long): Op<Unit> = try {
        db.withTransaction {
            val cur = dao.get(id) ?: return@withTransaction Op.Fail(AppError.NOT_FOUND)
            if (cur.sumber != ExpenseSource.MANUAL) return@withTransaction Op.Fail(AppError.RESERVED)
            dao.delete(id)
            Op.Ok(Unit)
        }
    } catch (e: Exception) {
        Op.Fail(AppError.STORAGE, e.message)
    }

    suspend fun addCategory(name: String): Op<Long> {
        val n = name.trim()
        if (n.isEmpty()) return Op.Fail(AppError.NAME_EMPTY)
        return try {
            db.withTransaction {
                if (dao.findCategoryByName(n) != null) return@withTransaction Op.Fail(AppError.NAME_DUPLICATE)
                Op.Ok(dao.insertCategory(ExpenseCategoryEntity(nama = n)))
            }
        } catch (e: Exception) {
            Op.Fail(AppError.STORAGE, e.message)
        }
    }

    suspend fun renameCategory(id: Long, name: String): Op<Unit> {
        val n = name.trim()
        if (n.isEmpty()) return Op.Fail(AppError.NAME_EMPTY)
        if (ReservedExpenseCategory.isReserved(id)) return Op.Fail(AppError.RESERVED)
        return try {
            db.withTransaction {
                val dup = dao.findCategoryByName(n)
                if (dup != null && dup.id != id) return@withTransaction Op.Fail(AppError.NAME_DUPLICATE)
                dao.updateCategory(ExpenseCategoryEntity(id = id, nama = n))
                Op.Ok(Unit)
            }
        } catch (e: Exception) {
            Op.Fail(AppError.STORAGE, e.message)
        }
    }

    /** Hapus kategori; pengeluaran di dalamnya dipindah ke [moveTo]. */
    suspend fun deleteCategory(id: Long, moveTo: Long): Op<Unit> {
        if (ReservedExpenseCategory.isReserved(id)) return Op.Fail(AppError.RESERVED)
        return try {
            db.withTransaction {
                if (id == moveTo) return@withTransaction Op.Fail(AppError.INVALID_NUMBER)
                dao.moveExpenses(id, moveTo)
                dao.deleteCategory(id)
                Op.Ok(Unit)
            }
        } catch (e: Exception) {
            Op.Fail(AppError.STORAGE, e.message)
        }
    }

    suspend fun countIn(categoryId: Long) = dao.countInCategory(categoryId)
}

class ProfileRepository(db: ArnDatabase) {
    private val dao = db.profileDao()

    fun observe(): Flow<BusinessProfileEntity?> = dao.observe()
    suspend fun get(): BusinessProfileEntity = dao.get() ?: BusinessProfileEntity()

    suspend fun save(profile: BusinessProfileEntity) {
        dao.upsert(
            profile.copy(
                id = 1,
                nama = profile.nama.trim(),
                alamat = profile.alamat.trim(),
                telepon = profile.telepon.trim(),
                mataUang = profile.mataUang.trim().ifEmpty { "Rp" },
            ),
        )
    }
}
