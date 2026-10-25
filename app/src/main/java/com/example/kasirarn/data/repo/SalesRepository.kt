package com.example.kasirarn.data.repo

import androidx.room.withTransaction
import com.example.kasirarn.data.db.ArnDatabase
import com.example.kasirarn.data.db.BusinessProfileEntity
import com.example.kasirarn.data.db.IngredientEntity
import com.example.kasirarn.data.db.PayMethod
import com.example.kasirarn.data.db.StockMoveType
import com.example.kasirarn.data.db.StockMovementEntity
import com.example.kasirarn.data.db.TransactionEntity
import com.example.kasirarn.data.db.TransactionIngredientEntity
import com.example.kasirarn.data.db.TransactionItemEntity
import com.example.kasirarn.data.db.TxStatus
import com.example.kasirarn.data.settings.SettingsRepository
import com.example.kasirarn.data.settings.StockPolicy
import com.example.kasirarn.domain.CartLine
import com.example.kasirarn.domain.CartMath
import com.example.kasirarn.domain.Discount
import com.example.kasirarn.domain.StockMath
import com.example.kasirarn.domain.StockState
import kotlinx.coroutines.flow.Flow

data class CheckoutRequest(
    val lines: List<CartLine>,
    val discount: Discount,
    val method: String,
    val paid: Long,
    val note: String = "",
)

data class Receipt(
    val transaction: TransactionEntity,
    val items: List<TransactionItemEntity>,
    val profile: BusinessProfileEntity?,
)

object ReceiptNumber {
    /** Contoh: ARN-241024-007 (tahun dua digit, bulan, tanggal, nomor urut harian). */
    fun format(tx: TransactionEntity): String {
        val key = tx.kunciHari
        val yy = (key / 10_000) % 100
        val mm = (key / 100) % 100
        val dd = key % 100
        return "ARN-%02d%02d%02d-%03d".format(yy, mm, dd, tx.nomor)
    }
}

class SalesRepository(
    private val db: ArnDatabase,
    private val settings: SettingsRepository,
    private val notifications: NotificationRepository,
    private val clock: Clock = Clock.System,
) {
    private val txDao = db.transactionDao()
    private val menuDao = db.menuDao()
    private val ingredientDao = db.ingredientDao()
    private val profileDao = db.profileDao()

    fun observeTransaction(id: Long): Flow<TransactionEntity?> = txDao.observe(id)
    fun observeItems(id: Long): Flow<List<TransactionItemEntity>> = txDao.observeItems(id)

    suspend fun receipt(id: Long): Receipt? {
        val tx = txDao.get(id) ?: return null
        return Receipt(tx, txDao.getItems(id), profileDao.get())
    }

    /**
     * Menyimpan transaksi, baris item, snapshot pemakaian bahan, pengurangan stok, dan pergerakan stok
     * dalam satu transaksi database. Gagal di tengah jalan berarti tidak ada yang tersimpan.
     */
    suspend fun checkout(req: CheckoutRequest): Op<Long> {
        if (req.lines.isEmpty() || req.lines.any { it.quantity <= 0 }) return Op.Fail(AppError.EMPTY_CART)
        val policy = settings.current().stockPolicy
        val events = mutableListOf<AlertEvent>()
        val result: Op<Long> = try {
            db.withTransaction {
                val now = clock.now()
                val menus = req.lines.map { it.menuId }.distinct().associateWith { menuDao.get(it) }
                if (menus.values.any { it == null || it.dihapusPada != null || !it.aktif }) {
                    return@withTransaction Op.Fail(AppError.NOT_FOUND)
                }
                val recipes = menus.keys.associateWith { menuDao.getRecipe(it) }
                val ingredientIds = recipes.values.flatten().map { it.bahanId }.distinct()
                val ingredients: MutableMap<Long, IngredientEntity> =
                    ingredientDao.getByIds(ingredientIds).associateBy { it.id }.toMutableMap()
                val before = ingredients.mapValues { it.value.stokDasar }

                if (policy == StockPolicy.BLOCK) {
                    val need = HashMap<Long, Long>()
                    for (line in req.lines) {
                        for (r in recipes.getValue(line.menuId)) {
                            need[r.bahanId] = (need[r.bahanId] ?: 0L) + r.jumlahDasar * line.quantity
                        }
                    }
                    val short = need.filter { (id, qty) -> (ingredients[id]?.stokDasar ?: 0L) < qty }.keys
                    if (short.isNotEmpty()) {
                        return@withTransaction Op.Fail(
                            AppError.INSUFFICIENT_STOCK,
                            short.mapNotNull { ingredients[it]?.nama }.joinToString(", "),
                        )
                    }
                }

                val subtotal = req.lines.sumOf { menus.getValue(it.menuId)!!.hargaJual * it.quantity }
                val discount = CartMath.discountAmount(subtotal, req.discount)
                val total = subtotal - discount
                val paid: Long
                val change: Long
                if (req.method == PayMethod.CASH) {
                    if (req.paid < total) return@withTransaction Op.Fail(AppError.PAYMENT_SHORT)
                    paid = req.paid
                    change = CartMath.change(total, paid)
                } else {
                    paid = total
                    change = 0
                }

                val dayKey = DayMath.dayKey(now)
                val txId = txDao.insert(
                    TransactionEntity(
                        nomor = txDao.nextNumber(dayKey),
                        kunciHari = dayKey,
                        waktu = now,
                        subtotal = subtotal,
                        diskon = discount,
                        total = total,
                        metodeBayar = req.method,
                        dibayar = paid,
                        kembalian = change,
                        status = TxStatus.DONE,
                        catatan = req.note.trim(),
                    ),
                )

                val states = ingredients.mapValues { it.value.toState() }.toMutableMap()
                for (line in req.lines) {
                    val menu = menus.getValue(line.menuId)!!
                    val rows = mutableListOf<Pair<Long, Pair<Long, Long>>>() // bahan -> (jumlah, biaya)
                    var hppTotal = 0L
                    for (r in recipes.getValue(line.menuId)) {
                        val used = r.jumlahDasar * line.quantity
                        val res = StockMath.consume(states.getValue(r.bahanId), used)
                        states[r.bahanId] = res.state
                        hppTotal += res.cost
                        rows += r.bahanId to (used to res.cost)
                    }
                    val itemId = txDao.insertItem(
                        TransactionItemEntity(
                            transaksiId = txId,
                            menuId = menu.id,
                            namaMenu = menu.nama,
                            hargaSatuan = menu.hargaJual,
                            kuantitas = line.quantity,
                            hppSatuan = StockMath.proportion(hppTotal, 1, line.quantity.toLong()),
                            hppTotal = hppTotal,
                            catatan = line.note.trim(),
                        ),
                    )
                    txDao.insertIngredientRows(
                        rows.map { (ingredientId, v) ->
                            TransactionIngredientEntity(
                                transaksiItemId = itemId,
                                bahanId = ingredientId,
                                jumlahDasar = v.first,
                                biaya = v.second,
                            )
                        },
                    )
                    for ((ingredientId, v) in rows) {
                        ingredientDao.insertMovement(
                            StockMovementEntity(
                                bahanId = ingredientId,
                                waktu = now,
                                jenis = StockMoveType.SALE,
                                jumlahDasar = -v.first,
                                nilai = -v.second,
                                referensiId = txId,
                            ),
                        )
                    }
                }
                for ((id, state) in states) {
                    val old = ingredients.getValue(id)
                    val updated = old.withState(state)
                    ingredientDao.update(updated)
                    StockAlerts.detect(before.getValue(id), updated.stokDasar, old.stokMinimum)?.let {
                        events += AlertEvent(updated, it)
                    }
                }
                Op.Ok(txId)
            }
        } catch (e: Exception) {
            Op.Fail(AppError.STORAGE, e.message)
        }
        if (result is Op.Ok) events.forEach { runCatching { notifications.raiseStockAlert(it) } }
        return result
    }

    /** Batalkan transaksi: stok dikembalikan dari snapshot pemakaian saat transaksi, bukan resep terbaru. */
    suspend fun voidTransaction(id: Long): Op<Unit> = try {
        db.withTransaction {
            val tx = txDao.get(id) ?: return@withTransaction Op.Fail(AppError.NOT_FOUND)
            if (tx.status == TxStatus.VOID) return@withTransaction Op.Fail(AppError.ALREADY_VOID)
            val now = clock.now()
            val rows = txDao.getIngredientRows(id)
            val perIngredient = rows.groupBy { it.bahanId }
            val ingredients = ingredientDao.getByIds(perIngredient.keys.toList()).associateBy { it.id }
            for ((ingredientId, list) in perIngredient) {
                val ing = ingredients[ingredientId] ?: continue
                val qty = list.sumOf { it.jumlahDasar }
                val cost = list.sumOf { it.biaya }
                val next: StockState = StockMath.reverse(ing.toState(), qty, cost)
                ingredientDao.update(ing.withState(next))
                ingredientDao.insertMovement(
                    StockMovementEntity(
                        bahanId = ingredientId,
                        waktu = now,
                        jenis = StockMoveType.VOID,
                        jumlahDasar = qty,
                        nilai = cost,
                        referensiId = id,
                    ),
                )
            }
            txDao.updateStatus(id, TxStatus.VOID)
            Op.Ok(Unit)
        }
    } catch (e: Exception) {
        Op.Fail(AppError.STORAGE, e.message)
    }
}
