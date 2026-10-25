package com.example.kasirarn.data.db

import androidx.room.Dao
import androidx.room.Delete
import androidx.room.Insert
import androidx.room.Query
import androidx.room.Update
import kotlinx.coroutines.flow.Flow

/** Id tetap untuk kategori pengeluaran yang dibuat sistem. */
object ReservedExpenseCategory {
    const val INGREDIENTS = 1L
    const val LOSS = 2L
    fun isReserved(id: Long) = id == INGREDIENTS || id == LOSS
}

// ---------- Hasil kueri berbentuk POJO ----------

data class SalesSummaryRow(val txCount: Int, val subtotal: Long, val discount: Long, val total: Long)
data class TrendRow(val bucket: Long, val total: Long, val cnt: Int)
data class TopMenuRow(val name: String, val qty: Long, val revenue: Long)
data class GroupRow(val label: String, val amount: Long, val cnt: Long)
data class ExpenseRow(
    val id: Long,
    val tanggal: Long,
    val kategoriId: Long,
    val kategoriNama: String,
    val nominal: Long,
    val catatan: String,
    val sumber: String,
    val referensiId: Long?,
)
data class MenuUsage(val menuId: Long, val nama: String)

// ---------- Kategori ----------

@Dao
interface CategoryDao {
    @Query("SELECT * FROM kategori_menu ORDER BY urutan, id")
    fun observeAll(): Flow<List<CategoryEntity>>

    @Query("SELECT * FROM kategori_menu ORDER BY urutan, id")
    suspend fun getAll(): List<CategoryEntity>

    @Query("SELECT * FROM kategori_menu WHERE id = :id")
    suspend fun get(id: Long): CategoryEntity?

    @Query("SELECT * FROM kategori_menu WHERE nama = :nama COLLATE NOCASE LIMIT 1")
    suspend fun findByName(nama: String): CategoryEntity?

    @Query("SELECT COALESCE(MAX(urutan), 0) FROM kategori_menu")
    suspend fun maxOrder(): Int

    @Insert suspend fun insert(c: CategoryEntity): Long
    @Update suspend fun update(c: CategoryEntity)
    @Update suspend fun updateAll(list: List<CategoryEntity>)
    @Query("DELETE FROM kategori_menu WHERE id = :id") suspend fun delete(id: Long)

    @Query("SELECT COUNT(*) FROM menu WHERE kategori_id = :id AND dihapus_pada IS NULL")
    suspend fun countMenus(id: Long): Int

    @Query("UPDATE menu SET kategori_id = :to WHERE kategori_id = :from")
    suspend fun moveMenus(from: Long, to: Long?)
}

// ---------- Menu dan resep ----------

@Dao
interface MenuDao {
    @Query("SELECT * FROM menu WHERE dihapus_pada IS NULL ORDER BY nama COLLATE NOCASE")
    fun observeAll(): Flow<List<MenuEntity>>

    @Query("SELECT * FROM menu WHERE id = :id")
    suspend fun get(id: Long): MenuEntity?

    @Insert suspend fun insert(m: MenuEntity): Long
    @Update suspend fun update(m: MenuEntity)

    @Query("UPDATE menu SET dihapus_pada = :time WHERE id = :id")
    suspend fun softDelete(id: Long, time: Long)

    @Query("DELETE FROM menu WHERE id = :id")
    suspend fun hardDelete(id: Long)

    @Query("SELECT COUNT(*) FROM transaksi_item WHERE menu_id = :id")
    suspend fun salesCount(id: Long): Int

    @Query("SELECT * FROM resep_item")
    fun observeAllRecipes(): Flow<List<RecipeItemEntity>>

    @Query("SELECT * FROM resep_item WHERE menu_id = :menuId ORDER BY id")
    suspend fun getRecipe(menuId: Long): List<RecipeItemEntity>

    @Query("DELETE FROM resep_item WHERE menu_id = :menuId")
    suspend fun deleteRecipe(menuId: Long)

    @Insert suspend fun insertRecipe(items: List<RecipeItemEntity>)

    @Query(
        "SELECT m.id AS menuId, m.nama AS nama FROM menu m JOIN resep_item r ON r.menu_id = m.id " +
            "WHERE r.bahan_id = :ingredientId AND m.dihapus_pada IS NULL ORDER BY m.nama COLLATE NOCASE",
    )
    suspend fun menusUsing(ingredientId: Long): List<MenuUsage>
}

// ---------- Bahan baku ----------

@Dao
interface IngredientDao {
    @Query("SELECT * FROM bahan_baku WHERE dihapus_pada IS NULL ORDER BY nama COLLATE NOCASE")
    fun observeActive(): Flow<List<IngredientEntity>>

    @Query("SELECT * FROM bahan_baku WHERE dihapus_pada IS NULL ORDER BY nama COLLATE NOCASE")
    suspend fun getActive(): List<IngredientEntity>

    @Query("SELECT * FROM bahan_baku WHERE id = :id")
    suspend fun get(id: Long): IngredientEntity?

    @Query("SELECT * FROM bahan_baku WHERE id = :id")
    fun observe(id: Long): Flow<IngredientEntity?>

    @Query("SELECT * FROM bahan_baku WHERE id IN (:ids)")
    suspend fun getByIds(ids: List<Long>): List<IngredientEntity>

    @Query("SELECT * FROM bahan_baku WHERE nama = :nama COLLATE NOCASE LIMIT 1")
    suspend fun findByName(nama: String): IngredientEntity?

    @Insert suspend fun insert(i: IngredientEntity): Long
    @Update suspend fun update(i: IngredientEntity)

    @Query("UPDATE bahan_baku SET dihapus_pada = :time WHERE id = :id")
    suspend fun archive(id: Long, time: Long)

    @Query("DELETE FROM bahan_baku WHERE id = :id")
    suspend fun hardDelete(id: Long)

    @Query("SELECT COUNT(*) FROM pergerakan_stok WHERE bahan_id = :id")
    suspend fun movementCount(id: Long): Int

    // Takaran kustom
    @Query("SELECT * FROM takaran_bahan ORDER BY nama_satuan COLLATE NOCASE")
    fun observeAllMeasures(): Flow<List<MeasureEntity>>

    @Query("SELECT * FROM takaran_bahan WHERE bahan_id = :id ORDER BY nama_satuan COLLATE NOCASE")
    suspend fun getMeasures(id: Long): List<MeasureEntity>

    @Query("DELETE FROM takaran_bahan WHERE bahan_id = :id")
    suspend fun deleteMeasures(id: Long)

    @Insert suspend fun insertMeasures(list: List<MeasureEntity>)

    // Pergerakan stok
    @Insert suspend fun insertMovement(m: StockMovementEntity): Long

    @Query(
        "SELECT * FROM pergerakan_stok WHERE bahan_id = :id AND waktu >= :from AND waktu < :to " +
            "ORDER BY waktu DESC, id DESC LIMIT :limit",
    )
    fun observeMovements(id: Long, from: Long, to: Long, limit: Int): Flow<List<StockMovementEntity>>
}

// ---------- Transaksi dan laporan pemasukan ----------

@Dao
interface TransactionDao {
    @Insert suspend fun insert(t: TransactionEntity): Long
    @Insert suspend fun insertItem(i: TransactionItemEntity): Long
    @Insert suspend fun insertIngredientRows(rows: List<TransactionIngredientEntity>)

    @Query("SELECT COUNT(*) FROM transaksi")
    suspend fun countAll(): Int

    @Query("SELECT COALESCE(MAX(nomor), 0) + 1 FROM transaksi WHERE kunci_hari = :key")
    suspend fun nextNumber(key: Int): Int

    @Query("SELECT * FROM transaksi WHERE id = :id")
    suspend fun get(id: Long): TransactionEntity?

    @Query("SELECT * FROM transaksi WHERE id = :id")
    fun observe(id: Long): Flow<TransactionEntity?>

    @Query("SELECT * FROM transaksi_item WHERE transaksi_id = :id ORDER BY id")
    suspend fun getItems(id: Long): List<TransactionItemEntity>

    @Query("SELECT * FROM transaksi_item WHERE transaksi_id = :id ORDER BY id")
    fun observeItems(id: Long): Flow<List<TransactionItemEntity>>

    @Query(
        "SELECT tb.* FROM transaksi_bahan tb JOIN transaksi_item ti ON ti.id = tb.transaksi_item_id " +
            "WHERE ti.transaksi_id = :id",
    )
    suspend fun getIngredientRows(id: Long): List<TransactionIngredientEntity>

    @Query("UPDATE transaksi SET status = :status WHERE id = :id")
    suspend fun updateStatus(id: Long, status: String)

    @Query(
        "SELECT * FROM transaksi WHERE waktu >= :from AND waktu < :to ORDER BY waktu DESC, id DESC LIMIT :limit",
    )
    fun observeList(from: Long, to: Long, limit: Int): Flow<List<TransactionEntity>>

    // Laporan: semua agregasi dilakukan di SQL.
    @Query(
        "SELECT COUNT(*) AS txCount, COALESCE(SUM(subtotal), 0) AS subtotal, " +
            "COALESCE(SUM(diskon), 0) AS discount, COALESCE(SUM(total), 0) AS total " +
            "FROM transaksi WHERE status = 'SELESAI' AND waktu >= :from AND waktu < :to",
    )
    fun observeSummary(from: Long, to: Long): Flow<SalesSummaryRow>

    @Query(
        "SELECT COALESCE(SUM(ti.hpp_total), 0) FROM transaksi_item ti JOIN transaksi t ON t.id = ti.transaksi_id " +
            "WHERE t.status = 'SELESAI' AND t.waktu >= :from AND t.waktu < :to",
    )
    fun observeCogs(from: Long, to: Long): Flow<Long>

    @Query(
        "SELECT ((waktu + :offset) / 86400000) AS bucket, COALESCE(SUM(total), 0) AS total, COUNT(*) AS cnt " +
            "FROM transaksi WHERE status = 'SELESAI' AND waktu >= :from AND waktu < :to " +
            "GROUP BY bucket ORDER BY bucket",
    )
    fun observeTrendByDay(from: Long, to: Long, offset: Long): Flow<List<TrendRow>>

    @Query(
        "SELECT (((waktu + :offset) / 3600000) % 24) AS bucket, COALESCE(SUM(total), 0) AS total, COUNT(*) AS cnt " +
            "FROM transaksi WHERE status = 'SELESAI' AND waktu >= :from AND waktu < :to " +
            "GROUP BY bucket ORDER BY bucket",
    )
    fun observeTrendByHour(from: Long, to: Long, offset: Long): Flow<List<TrendRow>>

    @Query(
        "SELECT ti.nama_menu AS name, SUM(ti.kuantitas) AS qty, SUM(ti.harga_satuan * ti.kuantitas) AS revenue " +
            "FROM transaksi_item ti JOIN transaksi t ON t.id = ti.transaksi_id " +
            "WHERE t.status = 'SELESAI' AND t.waktu >= :from AND t.waktu < :to " +
            "GROUP BY ti.menu_id, ti.nama_menu ORDER BY qty DESC, revenue DESC LIMIT 8",
    )
    fun observeTopMenus(from: Long, to: Long): Flow<List<TopMenuRow>>

    @Query(
        "SELECT COALESCE(k.nama, '') AS label, SUM(ti.harga_satuan * ti.kuantitas) AS amount, SUM(ti.kuantitas) AS cnt " +
            "FROM transaksi_item ti JOIN transaksi t ON t.id = ti.transaksi_id " +
            "LEFT JOIN menu m ON m.id = ti.menu_id LEFT JOIN kategori_menu k ON k.id = m.kategori_id " +
            "WHERE t.status = 'SELESAI' AND t.waktu >= :from AND t.waktu < :to " +
            "GROUP BY k.id ORDER BY amount DESC",
    )
    fun observeByCategory(from: Long, to: Long): Flow<List<GroupRow>>

    @Query(
        "SELECT metode_bayar AS label, COALESCE(SUM(total), 0) AS amount, COUNT(*) AS cnt FROM transaksi " +
            "WHERE status = 'SELESAI' AND waktu >= :from AND waktu < :to GROUP BY metode_bayar ORDER BY amount DESC",
    )
    fun observeByPayment(from: Long, to: Long): Flow<List<GroupRow>>
}

// ---------- Pengeluaran ----------

@Dao
interface ExpenseDao {
    @Query("SELECT * FROM kategori_pengeluaran ORDER BY id")
    fun observeCategories(): Flow<List<ExpenseCategoryEntity>>

    @Query("SELECT * FROM kategori_pengeluaran WHERE nama = :nama COLLATE NOCASE LIMIT 1")
    suspend fun findCategoryByName(nama: String): ExpenseCategoryEntity?

    @Insert suspend fun insertCategory(c: ExpenseCategoryEntity): Long
    @Update suspend fun updateCategory(c: ExpenseCategoryEntity)
    @Query("DELETE FROM kategori_pengeluaran WHERE id = :id") suspend fun deleteCategory(id: Long)

    @Query("SELECT COUNT(*) FROM pengeluaran WHERE kategori_id = :id")
    suspend fun countInCategory(id: Long): Int

    @Query("UPDATE pengeluaran SET kategori_id = :to WHERE kategori_id = :from")
    suspend fun moveExpenses(from: Long, to: Long)

    @Insert suspend fun insert(e: ExpenseEntity): Long
    @Update suspend fun update(e: ExpenseEntity)
    @Query("DELETE FROM pengeluaran WHERE id = :id") suspend fun delete(id: Long)
    @Query("SELECT * FROM pengeluaran WHERE id = :id") suspend fun get(id: Long): ExpenseEntity?

    @Query(
        "SELECT e.id AS id, e.tanggal AS tanggal, e.kategori_id AS kategoriId, k.nama AS kategoriNama, " +
            "e.nominal AS nominal, e.catatan AS catatan, e.sumber AS sumber, e.referensi_id AS referensiId " +
            "FROM pengeluaran e JOIN kategori_pengeluaran k ON k.id = e.kategori_id " +
            "WHERE e.tanggal >= :from AND e.tanggal < :to ORDER BY e.tanggal DESC, e.id DESC LIMIT :limit",
    )
    fun observeList(from: Long, to: Long, limit: Int): Flow<List<ExpenseRow>>

    @Query(
        "SELECT k.nama AS label, SUM(e.nominal) AS amount, COUNT(*) AS cnt FROM pengeluaran e " +
            "JOIN kategori_pengeluaran k ON k.id = e.kategori_id " +
            "WHERE e.tanggal >= :from AND e.tanggal < :to GROUP BY k.id ORDER BY amount DESC",
    )
    fun observeByCategory(from: Long, to: Long): Flow<List<GroupRow>>

    @Query(
        "SELECT sumber AS label, SUM(nominal) AS amount, COUNT(*) AS cnt FROM pengeluaran " +
            "WHERE tanggal >= :from AND tanggal < :to GROUP BY sumber",
    )
    fun observeBySource(from: Long, to: Long): Flow<List<GroupRow>>

    @Query(
        "SELECT ((tanggal + :offset) / 86400000) AS bucket, COALESCE(SUM(nominal), 0) AS total, COUNT(*) AS cnt " +
            "FROM pengeluaran WHERE tanggal >= :from AND tanggal < :to GROUP BY bucket ORDER BY bucket",
    )
    fun observeTrendByDay(from: Long, to: Long, offset: Long): Flow<List<TrendRow>>
}

// ---------- Notifikasi dan profil ----------

@Dao
interface NotificationDao {
    @Insert suspend fun insert(n: NotificationEntity): Long

    @Query("SELECT * FROM notifikasi ORDER BY waktu DESC, id DESC LIMIT 200")
    fun observeAll(): Flow<List<NotificationEntity>>

    @Query("SELECT COUNT(*) FROM notifikasi WHERE dibaca = 0")
    fun observeUnreadCount(): Flow<Int>

    @Query("UPDATE notifikasi SET dibaca = 1 WHERE id = :id")
    suspend fun markRead(id: Long)

    @Query("UPDATE notifikasi SET dibaca = 1 WHERE dibaca = 0")
    suspend fun markAllRead()

    @Query("DELETE FROM notifikasi")
    suspend fun clear()

    @Query("SELECT COUNT(*) FROM notifikasi WHERE jenis = :type AND waktu >= :since")
    suspend fun countSince(type: String, since: Long): Int
}

@Dao
interface ProfileDao {
    @Query("SELECT * FROM profil_usaha WHERE id = 1")
    fun observe(): Flow<BusinessProfileEntity?>

    @Query("SELECT * FROM profil_usaha WHERE id = 1")
    suspend fun get(): BusinessProfileEntity?

    @Insert(onConflict = androidx.room.OnConflictStrategy.REPLACE)
    suspend fun upsert(p: BusinessProfileEntity)
}
