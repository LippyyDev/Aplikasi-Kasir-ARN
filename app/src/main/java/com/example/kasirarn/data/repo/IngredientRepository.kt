package com.example.kasirarn.data.repo

import androidx.room.withTransaction
import com.example.kasirarn.data.db.ArnDatabase
import com.example.kasirarn.data.db.ExpenseEntity
import com.example.kasirarn.data.db.ExpenseSource
import com.example.kasirarn.data.db.IngredientEntity
import com.example.kasirarn.data.db.MeasureEntity
import com.example.kasirarn.data.db.ReservedExpenseCategory
import com.example.kasirarn.data.db.StockMoveType
import com.example.kasirarn.data.db.StockMovementEntity
import com.example.kasirarn.domain.Dimension
import com.example.kasirarn.domain.Fraction
import com.example.kasirarn.domain.StockMath
import com.example.kasirarn.domain.StockState
import com.example.kasirarn.domain.UnitDef
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map

data class MeasureDraft(val name: String, val num: Long, val den: Long)

data class IngredientDraft(
    val id: Long?,
    val name: String,
    val dimension: Dimension,
    val purchaseUnit: String,
    val minStock: Long,
    val initialStock: Long,
    val initialValue: Long,
    val measures: List<MeasureDraft>,
)

sealed interface DeleteOutcome {
    data object Deleted : DeleteOutcome
    data object Archived : DeleteOutcome
}

/** Penanda alasan opname yang disimpan di kolom catatan dan diterjemahkan oleh layar. */
object Reason {
    const val INITIAL = "AWAL"
    const val DAMAGED = "RUSAK"
    const val SPILLED = "TUMPAH"
    const val MISCOUNT = "SALAH_HITUNG"
    const val OTHER = "LAINNYA"
}

/** Peristiwa yang perlu dikirim sebagai notifikasi sistem setelah transaksi database selesai. */
data class AlertEvent(val ingredient: IngredientEntity, val kind: StockAlerts.Kind)

object StockAlerts {
    enum class Kind { LOW, OUT }

    /** Hanya saat melewati batas dari atas, sehingga tidak berulang pada setiap transaksi. */
    fun detect(oldQty: Long, newQty: Long, min: Long): Kind? = when {
        oldQty > 0 && newQty <= 0 -> Kind.OUT
        min > 0 && oldQty > min && newQty <= min && newQty > 0 -> Kind.LOW
        else -> null
    }
}

fun IngredientEntity.toState() = StockState(stokDasar, nilaiStok, biayaTerakhirNilai, biayaTerakhirQty)

fun IngredientEntity.withState(s: StockState) = copy(
    stokDasar = s.qty,
    nilaiStok = s.value,
    biayaTerakhirNilai = s.lastCostValue,
    biayaTerakhirQty = s.lastCostQty,
)

fun IngredientEntity.dimension(): Dimension = Dimension.fromName(dimensi)

fun MeasureEntity.toUnit() = UnitDef(namaSatuan, Fraction.of(faktorPembilang, faktorPenyebut))

/** Satuan yang bisa dipilih untuk sebuah bahan: standar dimensinya ditambah takaran kustom. */
fun unitsFor(dimension: Dimension, measures: List<MeasureEntity>): List<UnitDef> =
    dimension.standardUnits + measures.map { it.toUnit() }

class IngredientRepository(
    private val db: ArnDatabase,
    private val notifications: NotificationRepository,
    private val clock: Clock = Clock.System,
) {
    private val dao = db.ingredientDao()
    private val menuDao = db.menuDao()
    private val expenseDao = db.expenseDao()

    fun observeIngredients(): Flow<List<IngredientEntity>> = dao.observeActive()

    fun observeMeasuresByIngredient(): Flow<Map<Long, List<MeasureEntity>>> =
        dao.observeAllMeasures().map { list -> list.groupBy { it.bahanId } }

    fun observeIngredient(id: Long) = dao.observe(id)

    suspend fun get(id: Long) = dao.get(id)
    suspend fun measures(id: Long) = dao.getMeasures(id)

    fun observeMovements(id: Long, from: Long, to: Long, limit: Int = 300) =
        dao.observeMovements(id, from, to, limit)

    suspend fun save(draft: IngredientDraft): Op<Long> {
        val name = draft.name.trim()
        if (name.isEmpty()) return Op.Fail(AppError.NAME_EMPTY)
        if (draft.minStock < 0 || draft.initialStock < 0 || draft.initialValue < 0) {
            return Op.Fail(AppError.INVALID_NUMBER)
        }
        val standardNames = draft.dimension.standardUnits.map { it.name.lowercase() }
        val measureNames = draft.measures.map { it.name.trim() }
        if (measureNames.any { it.isEmpty() }) return Op.Fail(AppError.NAME_EMPTY, "measure")
        if (measureNames.map { it.lowercase() }.toSet().size != measureNames.size ||
            measureNames.any { it.lowercase() in standardNames }
        ) {
            return Op.Fail(AppError.NAME_DUPLICATE, "measure")
        }
        if (draft.measures.any { it.num <= 0 || it.den <= 0 }) return Op.Fail(AppError.INVALID_NUMBER, "measure")

        return try {
            db.withTransaction {
                val existing = dao.findByName(name)
                if (existing != null && existing.id != draft.id) return@withTransaction Op.Fail(AppError.NAME_DUPLICATE)
                val now = clock.now()
                val dimension = if (draft.id != null) {
                    dao.get(draft.id)?.dimension() ?: draft.dimension
                } else {
                    draft.dimension
                }
                val validPurchase = (dimension.standardUnits.map { it.name } + measureNames)
                    .firstOrNull { it.equals(draft.purchaseUnit, ignoreCase = true) } ?: dimension.baseUnit
                val id: Long
                if (draft.id == null) {
                    id = dao.insert(
                        IngredientEntity(
                            nama = name,
                            dimensi = dimension.name,
                            satuanDasar = dimension.baseUnit,
                            satuanBeli = validPurchase,
                            stokDasar = draft.initialStock,
                            nilaiStok = if (draft.initialStock > 0) draft.initialValue else 0,
                            stokMinimum = draft.minStock,
                            biayaTerakhirNilai = if (draft.initialStock > 0) draft.initialValue else 0,
                            biayaTerakhirQty = if (draft.initialStock > 0 && draft.initialValue > 0) draft.initialStock else 0,
                        ),
                    )
                    if (draft.initialStock > 0) {
                        dao.insertMovement(
                            StockMovementEntity(
                                bahanId = id,
                                waktu = now,
                                jenis = StockMoveType.RESTOCK,
                                jumlahDasar = draft.initialStock,
                                nilai = draft.initialValue,
                                catatan = Reason.INITIAL,
                            ),
                        )
                    }
                } else {
                    val current = dao.get(draft.id) ?: return@withTransaction Op.Fail(AppError.NOT_FOUND)
                    id = current.id
                    dao.update(current.copy(nama = name, satuanBeli = validPurchase, stokMinimum = draft.minStock))
                }
                dao.deleteMeasures(id)
                dao.insertMeasures(
                    draft.measures.map {
                        val f = Fraction.of(it.num, it.den)
                        MeasureEntity(
                            bahanId = id,
                            namaSatuan = it.name.trim(),
                            faktorPembilang = f.num,
                            faktorPenyebut = f.den,
                        )
                    },
                )
                Op.Ok(id)
            }
        } catch (e: Exception) {
            Op.Fail(AppError.STORAGE, e.message)
        }
    }

    suspend fun delete(id: Long): Op<DeleteOutcome> = try {
        db.withTransaction {
            val users = menuDao.menusUsing(id)
            if (users.isNotEmpty()) {
                return@withTransaction Op.Fail(AppError.IN_USE, users.joinToString("\n") { it.nama })
            }
            if (dao.movementCount(id) > 0) {
                dao.archive(id, clock.now())
                Op.Ok(DeleteOutcome.Archived)
            } else {
                dao.hardDelete(id)
                Op.Ok(DeleteOutcome.Deleted)
            }
        }
    } catch (e: Exception) {
        Op.Fail(AppError.STORAGE, e.message)
    }

    /** Restok: stok, nilai, pergerakan, dan pengeluaran "Bahan baku" berubah dalam satu transaksi. */
    suspend fun restock(ingredientId: Long, qtyBase: Long, totalCost: Long): Op<Unit> {
        if (qtyBase <= 0 || totalCost < 0) return Op.Fail(AppError.INVALID_NUMBER)
        return try {
            db.withTransaction {
                val ing = dao.get(ingredientId) ?: return@withTransaction Op.Fail(AppError.NOT_FOUND)
                val now = clock.now()
                val next = StockMath.restock(ing.toState(), qtyBase, totalCost)
                dao.update(ing.withState(next))
                val moveId = dao.insertMovement(
                    StockMovementEntity(
                        bahanId = ingredientId,
                        waktu = now,
                        jenis = StockMoveType.RESTOCK,
                        jumlahDasar = qtyBase,
                        nilai = totalCost,
                    ),
                )
                if (totalCost > 0) {
                    expenseDao.insert(
                        ExpenseEntity(
                            tanggal = now,
                            kategoriId = ReservedExpenseCategory.INGREDIENTS,
                            nominal = totalCost,
                            catatan = ing.nama,
                            sumber = ExpenseSource.RESTOCK,
                            referensiId = moveId,
                        ),
                    )
                }
                Op.Ok(Unit)
            }
        } catch (e: Exception) {
            Op.Fail(AppError.STORAGE, e.message)
        }
    }

    /**
     * Opname: stok fisik dimasukkan, selisih dicatat sebagai penyesuaian dengan alasan.
     * Selisih berkurang dapat dicatat sebagai pengeluaran "Susut" bila [recordLoss].
     */
    suspend fun adjust(ingredientId: Long, physicalQty: Long, reason: String, recordLoss: Boolean): Op<List<AlertEvent>> {
        if (physicalQty < 0) return Op.Fail(AppError.INVALID_NUMBER)
        val events = mutableListOf<AlertEvent>()
        return try {
            val result = db.withTransaction {
                val ing = dao.get(ingredientId) ?: return@withTransaction Op.Fail(AppError.NOT_FOUND)
                val now = clock.now()
                val (next, valueChange) = StockMath.adjustTo(ing.toState(), physicalQty)
                val diff = physicalQty - ing.stokDasar
                if (diff == 0L) return@withTransaction Op.Ok(Unit)
                val updated = ing.withState(next)
                dao.update(updated)
                val moveId = dao.insertMovement(
                    StockMovementEntity(
                        bahanId = ingredientId,
                        waktu = now,
                        jenis = StockMoveType.ADJUST,
                        jumlahDasar = diff,
                        nilai = valueChange,
                        catatan = reason,
                    ),
                )
                if (diff < 0 && recordLoss && valueChange < 0) {
                    expenseDao.insert(
                        ExpenseEntity(
                            tanggal = now,
                            kategoriId = ReservedExpenseCategory.LOSS,
                            nominal = -valueChange,
                            catatan = ing.nama,
                            sumber = ExpenseSource.LOSS,
                            referensiId = moveId,
                        ),
                    )
                }
                StockAlerts.detect(ing.stokDasar, updated.stokDasar, ing.stokMinimum)?.let {
                    events += AlertEvent(updated, it)
                }
                Op.Ok(Unit)
            }
            when (result) {
                is Op.Ok -> {
                    events.forEach { notifications.raiseStockAlert(it) }
                    Op.Ok(events)
                }
                is Op.Fail -> result
            }
        } catch (e: Exception) {
            Op.Fail(AppError.STORAGE, e.message)
        }
    }
}
