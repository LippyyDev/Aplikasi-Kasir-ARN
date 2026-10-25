package com.example.kasirarn.data.db

import android.content.Context
import androidx.room.Database
import androidx.room.Room
import androidx.room.RoomDatabase
import androidx.sqlite.db.SupportSQLiteDatabase

@Database(
    entities = [
        CategoryEntity::class,
        MenuEntity::class,
        IngredientEntity::class,
        MeasureEntity::class,
        RecipeItemEntity::class,
        TransactionEntity::class,
        TransactionItemEntity::class,
        TransactionIngredientEntity::class,
        StockMovementEntity::class,
        ExpenseEntity::class,
        ExpenseCategoryEntity::class,
        NotificationEntity::class,
        BusinessProfileEntity::class,
    ],
    version = ArnDatabase.VERSION,
    exportSchema = true,
)
abstract class ArnDatabase : RoomDatabase() {
    abstract fun categoryDao(): CategoryDao
    abstract fun menuDao(): MenuDao
    abstract fun ingredientDao(): IngredientDao
    abstract fun transactionDao(): TransactionDao
    abstract fun expenseDao(): ExpenseDao
    abstract fun notificationDao(): NotificationDao
    abstract fun profileDao(): ProfileDao

    companion object {
        const val VERSION = 1
        const val FILE_NAME = "arn.db"

        /** Daftar migrasi; kosong selama skema masih versi 1. */
        val MIGRATIONS = emptyArray<androidx.room.migration.Migration>()

        /** Semua tabel data, dipakai untuk menghitung jumlah baris pada manifest cadangan. */
        val TABLES = listOf(
            "kategori_menu", "menu", "bahan_baku", "takaran_bahan", "resep_item", "transaksi",
            "transaksi_item", "transaksi_bahan", "pergerakan_stok", "pengeluaran",
            "kategori_pengeluaran", "notifikasi", "profil_usaha",
        )

        fun build(context: Context): ArnDatabase =
            Room.databaseBuilder(context.applicationContext, ArnDatabase::class.java, FILE_NAME)
                .addMigrations(*MIGRATIONS)
                .addCallback(SeedCallback)
                .build()
    }

    /** Menyiapkan data awal tepat sekali, ketika berkas database baru dibuat. */
    private object SeedCallback : Callback() {
        override fun onCreate(db: SupportSQLiteDatabase) {
            db.execSQL(
                "INSERT INTO profil_usaha (id, nama, logo_path, alamat, telepon, mata_uang) " +
                    "VALUES (1, '', NULL, '', '', 'Rp')",
            )
            val names = listOf("Bahan baku", "Susut", "Sewa", "Listrik dan air", "Gaji", "Kemasan", "Lain-lain")
            names.forEachIndexed { index, name ->
                db.execSQL("INSERT INTO kategori_pengeluaran (id, nama) VALUES (${index + 1}, '$name')")
            }
        }
    }
}
