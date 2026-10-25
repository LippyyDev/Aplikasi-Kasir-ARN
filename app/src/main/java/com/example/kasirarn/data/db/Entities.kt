package com.example.kasirarn.data.db

import androidx.room.ColumnInfo
import androidx.room.Entity
import androidx.room.ForeignKey
import androidx.room.Index
import androidx.room.PrimaryKey

/*
 * Semua uang: Long Rupiah. Semua waktu: Long epoch milidetik UTC.
 * Semua jumlah bahan: Long dalam satuan dasar. Tidak ada Double.
 */

object StockMoveType {
    const val RESTOCK = "RESTOK"
    const val SALE = "PENJUALAN"
    const val VOID = "BATAL"
    const val ADJUST = "PENYESUAIAN"
}

object TxStatus {
    const val DONE = "SELESAI"
    const val VOID = "BATAL"
}

object PayMethod {
    const val CASH = "TUNAI"
    const val NON_CASH = "NON_TUNAI"
}

object ExpenseSource {
    const val MANUAL = "MANUAL"
    const val RESTOCK = "RESTOK"
    const val LOSS = "SUSUT"
}

object NotifType {
    const val LOW_STOCK = "STOK_MENIPIS"
    const val OUT_OF_STOCK = "STOK_HABIS"
    const val BACKUP_REMINDER = "PENGINGAT_CADANGAN"
    const val BACKUP_RESULT = "HASIL_CADANGAN"
}

@Entity(tableName = "kategori_menu", indices = [Index(value = ["nama"], unique = true)])
data class CategoryEntity(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val nama: String,
    val urutan: Int,
)

@Entity(
    tableName = "menu",
    foreignKeys = [
        ForeignKey(
            entity = CategoryEntity::class,
            parentColumns = ["id"],
            childColumns = ["kategori_id"],
            onDelete = ForeignKey.SET_NULL,
        ),
    ],
    indices = [Index("kategori_id")],
)
data class MenuEntity(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val nama: String,
    @ColumnInfo(name = "kategori_id") val kategoriId: Long?,
    @ColumnInfo(name = "harga_jual") val hargaJual: Long,
    @ColumnInfo(name = "foto_path") val fotoPath: String?,
    val aktif: Boolean,
    val catatan: String,
    val dibuat: Long,
    val diubah: Long,
    @ColumnInfo(name = "dihapus_pada") val dihapusPada: Long? = null,
)

@Entity(tableName = "bahan_baku", indices = [Index(value = ["nama"], unique = true)])
data class IngredientEntity(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val nama: String,
    /** Nama enum [com.example.kasirarn.domain.Dimension]. */
    val dimensi: String,
    @ColumnInfo(name = "satuan_dasar") val satuanDasar: String,
    @ColumnInfo(name = "satuan_beli") val satuanBeli: String,
    @ColumnInfo(name = "stok_dasar") val stokDasar: Long,
    @ColumnInfo(name = "nilai_stok") val nilaiStok: Long,
    @ColumnInfo(name = "stok_minimum") val stokMinimum: Long,
    /** Total harga pembelian terakhir; harga cadangan saat stok nol atau negatif. */
    @ColumnInfo(name = "biaya_terakhir_nilai") val biayaTerakhirNilai: Long = 0,
    @ColumnInfo(name = "biaya_terakhir_qty") val biayaTerakhirQty: Long = 0,
    @ColumnInfo(name = "dihapus_pada") val dihapusPada: Long? = null,
)

@Entity(
    tableName = "takaran_bahan",
    foreignKeys = [
        ForeignKey(
            entity = IngredientEntity::class,
            parentColumns = ["id"],
            childColumns = ["bahan_id"],
            onDelete = ForeignKey.CASCADE,
        ),
    ],
    indices = [Index(value = ["bahan_id", "nama_satuan"], unique = true)],
)
data class MeasureEntity(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    @ColumnInfo(name = "bahan_id") val bahanId: Long,
    @ColumnInfo(name = "nama_satuan") val namaSatuan: String,
    @ColumnInfo(name = "faktor_pembilang") val faktorPembilang: Long,
    @ColumnInfo(name = "faktor_penyebut") val faktorPenyebut: Long,
)

@Entity(
    tableName = "resep_item",
    foreignKeys = [
        ForeignKey(
            entity = MenuEntity::class,
            parentColumns = ["id"],
            childColumns = ["menu_id"],
            onDelete = ForeignKey.CASCADE,
        ),
        ForeignKey(
            entity = IngredientEntity::class,
            parentColumns = ["id"],
            childColumns = ["bahan_id"],
            onDelete = ForeignKey.RESTRICT,
        ),
    ],
    indices = [Index(value = ["menu_id", "bahan_id"], unique = true), Index("bahan_id")],
)
data class RecipeItemEntity(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    @ColumnInfo(name = "menu_id") val menuId: Long,
    @ColumnInfo(name = "bahan_id") val bahanId: Long,
    @ColumnInfo(name = "jumlah_dasar") val jumlahDasar: Long,
)

@Entity(
    tableName = "transaksi",
    indices = [Index("waktu"), Index("kunci_hari")],
)
data class TransactionEntity(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    /** Nomor urut dalam satu hari (1, 2, 3, ...). */
    val nomor: Int,
    /** Hari lokal dalam bentuk yyyyMMdd, dasar nomor urut harian. */
    @ColumnInfo(name = "kunci_hari") val kunciHari: Int,
    val waktu: Long,
    val subtotal: Long,
    val diskon: Long,
    val total: Long,
    @ColumnInfo(name = "metode_bayar") val metodeBayar: String,
    val dibayar: Long,
    val kembalian: Long,
    val status: String,
    val catatan: String,
)

@Entity(
    tableName = "transaksi_item",
    foreignKeys = [
        ForeignKey(
            entity = TransactionEntity::class,
            parentColumns = ["id"],
            childColumns = ["transaksi_id"],
            onDelete = ForeignKey.CASCADE,
        ),
    ],
    indices = [Index("transaksi_id")],
)
data class TransactionItemEntity(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    @ColumnInfo(name = "transaksi_id") val transaksiId: Long,
    @ColumnInfo(name = "menu_id") val menuId: Long?,
    @ColumnInfo(name = "nama_menu") val namaMenu: String,
    @ColumnInfo(name = "harga_satuan") val hargaSatuan: Long,
    val kuantitas: Int,
    @ColumnInfo(name = "hpp_satuan") val hppSatuan: Long,
    /** Total HPP baris ini (jumlah biaya bahan yang dipakai), tanpa galat pembulatan per porsi. */
    @ColumnInfo(name = "hpp_total") val hppTotal: Long,
    val catatan: String,
)

@Entity(
    tableName = "transaksi_bahan",
    foreignKeys = [
        ForeignKey(
            entity = TransactionItemEntity::class,
            parentColumns = ["id"],
            childColumns = ["transaksi_item_id"],
            onDelete = ForeignKey.CASCADE,
        ),
    ],
    indices = [Index("transaksi_item_id"), Index("bahan_id")],
)
data class TransactionIngredientEntity(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    @ColumnInfo(name = "transaksi_item_id") val transaksiItemId: Long,
    @ColumnInfo(name = "bahan_id") val bahanId: Long,
    @ColumnInfo(name = "jumlah_dasar") val jumlahDasar: Long,
    val biaya: Long,
)

@Entity(
    tableName = "pergerakan_stok",
    indices = [Index(value = ["bahan_id", "waktu"])],
)
data class StockMovementEntity(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    @ColumnInfo(name = "bahan_id") val bahanId: Long,
    val waktu: Long,
    val jenis: String,
    /** Bertanda: positif masuk, negatif keluar. */
    @ColumnInfo(name = "jumlah_dasar") val jumlahDasar: Long,
    val nilai: Long,
    @ColumnInfo(name = "referensi_id") val referensiId: Long? = null,
    val catatan: String = "",
)

@Entity(tableName = "kategori_pengeluaran", indices = [Index(value = ["nama"], unique = true)])
data class ExpenseCategoryEntity(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val nama: String,
)

@Entity(
    tableName = "pengeluaran",
    foreignKeys = [
        ForeignKey(
            entity = ExpenseCategoryEntity::class,
            parentColumns = ["id"],
            childColumns = ["kategori_id"],
            onDelete = ForeignKey.RESTRICT,
        ),
    ],
    indices = [Index("tanggal"), Index("kategori_id")],
)
data class ExpenseEntity(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val tanggal: Long,
    @ColumnInfo(name = "kategori_id") val kategoriId: Long,
    val nominal: Long,
    val catatan: String,
    val sumber: String,
    @ColumnInfo(name = "referensi_id") val referensiId: Long? = null,
)

@Entity(tableName = "notifikasi", indices = [Index("waktu")])
data class NotificationEntity(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val jenis: String,
    val judul: String,
    val isi: String,
    val waktu: Long,
    val dibaca: Boolean = false,
    @ColumnInfo(name = "referensi_id") val referensiId: Long? = null,
)

@Entity(tableName = "profil_usaha")
data class BusinessProfileEntity(
    @PrimaryKey val id: Int = 1,
    val nama: String = "",
    @ColumnInfo(name = "logo_path") val logoPath: String? = null,
    val alamat: String = "",
    val telepon: String = "",
    @ColumnInfo(name = "mata_uang") val mataUang: String = "Rp",
)
