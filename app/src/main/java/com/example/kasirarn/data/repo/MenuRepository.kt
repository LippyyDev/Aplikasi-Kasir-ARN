package com.example.kasirarn.data.repo

import androidx.room.withTransaction
import com.example.kasirarn.data.db.ArnDatabase
import com.example.kasirarn.data.db.CategoryEntity
import com.example.kasirarn.data.db.MenuEntity
import com.example.kasirarn.data.db.RecipeItemEntity
import com.example.kasirarn.domain.Availability
import com.example.kasirarn.domain.CostCalculator
import com.example.kasirarn.domain.RecipeLine
import com.example.kasirarn.domain.StockState
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.combine

data class RecipeDraftLine(val ingredientId: Long, val qty: Long)

data class MenuDraft(
    val id: Long?,
    val name: String,
    val categoryId: Long?,
    val price: Long,
    val photo: String?,
    val active: Boolean,
    val notes: String,
    val recipe: List<RecipeDraftLine>,
)

/** Menu beserta data turunan yang dibutuhkan Halaman Utama dan Penyetelan Menu. */
data class CatalogItem(
    val menu: MenuEntity,
    val categoryName: String?,
    val recipe: List<RecipeLine>,
    /** Porsi yang masih bisa dibuat; null berarti tak terbatas (resep kosong). */
    val portions: Long?,
    val hpp: Long,
    val hppComplete: Boolean,
) {
    val margin: Long get() = menu.hargaJual - hpp
    val soldOut: Boolean get() = portions != null && portions <= 0
}

enum class MoveDirection { UP, DOWN }

class MenuRepository(
    private val db: ArnDatabase,
    private val clock: Clock = Clock.System,
) {
    private val menuDao = db.menuDao()
    private val categoryDao = db.categoryDao()
    private val ingredientDao = db.ingredientDao()

    fun observeCategories(): Flow<List<CategoryEntity>> = categoryDao.observeAll()

    fun observeCatalog(): Flow<List<CatalogItem>> = combine(
        menuDao.observeAll(),
        menuDao.observeAllRecipes(),
        ingredientDao.observeActive(),
        categoryDao.observeAll(),
    ) { menus, recipes, ingredients, categories ->
        val stockQty = ingredients.associate { it.id to it.stokDasar }
        val states = ingredients.associate {
            it.id to StockState(it.stokDasar, it.nilaiStok, it.biayaTerakhirNilai, it.biayaTerakhirQty)
        }
        val recipeByMenu = recipes.groupBy { it.menuId }
        val categoryNames = categories.associate { it.id to it.nama }
        menus.map { menu ->
            val lines = recipeByMenu[menu.id].orEmpty().map { RecipeLine(it.bahanId, it.jumlahDasar) }
            val cost = CostCalculator.perPortion(lines, states)
            CatalogItem(
                menu = menu,
                categoryName = menu.kategoriId?.let { categoryNames[it] },
                recipe = lines,
                portions = Availability.portions(lines, stockQty),
                hpp = cost.total,
                hppComplete = cost.complete,
            )
        }
    }

    suspend fun getMenu(id: Long) = menuDao.get(id)
    suspend fun getRecipe(id: Long) = menuDao.getRecipe(id)

    suspend fun save(draft: MenuDraft): Op<Long> {
        val name = draft.name.trim()
        if (name.isEmpty()) return Op.Fail(AppError.NAME_EMPTY)
        if (draft.price <= 0) return Op.Fail(AppError.PRICE_INVALID)
        if (draft.recipe.any { it.qty <= 0 } || draft.recipe.map { it.ingredientId }.toSet().size != draft.recipe.size) {
            return Op.Fail(AppError.INVALID_NUMBER, "recipe")
        }
        return try {
            db.withTransaction {
                val now = clock.now()
                val id: Long
                if (draft.id == null) {
                    id = menuDao.insert(
                        MenuEntity(
                            nama = name,
                            kategoriId = draft.categoryId,
                            hargaJual = draft.price,
                            fotoPath = draft.photo,
                            aktif = draft.active,
                            catatan = draft.notes.trim(),
                            dibuat = now,
                            diubah = now,
                        ),
                    )
                } else {
                    val current = menuDao.get(draft.id) ?: return@withTransaction Op.Fail(AppError.NOT_FOUND)
                    id = current.id
                    menuDao.update(
                        current.copy(
                            nama = name,
                            kategoriId = draft.categoryId,
                            hargaJual = draft.price,
                            fotoPath = draft.photo,
                            aktif = draft.active,
                            catatan = draft.notes.trim(),
                            diubah = now,
                        ),
                    )
                    menuDao.deleteRecipe(id)
                }
                menuDao.insertRecipe(draft.recipe.map { RecipeItemEntity(menuId = id, bahanId = it.ingredientId, jumlahDasar = it.qty) })
                Op.Ok(id)
            }
        } catch (e: Exception) {
            Op.Fail(AppError.STORAGE, e.message)
        }
    }

    suspend fun setActive(id: Long, active: Boolean) {
        val m = menuDao.get(id) ?: return
        menuDao.update(m.copy(aktif = active, diubah = clock.now()))
    }

    /** Salin menu beserta resepnya; foto tidak ikut disalin agar berkas tidak dimiliki dua menu. */
    suspend fun duplicate(id: Long, nameSuffix: String): Op<Long> = try {
        db.withTransaction {
            val src = menuDao.get(id) ?: return@withTransaction Op.Fail(AppError.NOT_FOUND)
            val now = clock.now()
            val newId = menuDao.insert(
                src.copy(id = 0, nama = src.nama + nameSuffix, fotoPath = null, dibuat = now, diubah = now, dihapusPada = null),
            )
            menuDao.insertRecipe(menuDao.getRecipe(id).map { it.copy(id = 0, menuId = newId) })
            Op.Ok(newId)
        }
    } catch (e: Exception) {
        Op.Fail(AppError.STORAGE, e.message)
    }

    /** Menu yang pernah terjual dihapus lunak; selain itu dihapus penuh. Mengembalikan nama berkas foto yang boleh dibuang. */
    suspend fun delete(id: Long): Op<String?> = try {
        db.withTransaction {
            val m = menuDao.get(id) ?: return@withTransaction Op.Fail(AppError.NOT_FOUND)
            if (menuDao.salesCount(id) > 0) {
                menuDao.softDelete(id, clock.now())
                Op.Ok(null)
            } else {
                menuDao.hardDelete(id)
                Op.Ok(m.fotoPath)
            }
        }
    } catch (e: Exception) {
        Op.Fail(AppError.STORAGE, e.message)
    }

    // ---------- Kategori ----------

    suspend fun addCategory(name: String): Op<Long> {
        val n = name.trim()
        if (n.isEmpty()) return Op.Fail(AppError.NAME_EMPTY)
        return try {
            db.withTransaction {
                if (categoryDao.findByName(n) != null) return@withTransaction Op.Fail(AppError.NAME_DUPLICATE)
                Op.Ok(categoryDao.insert(CategoryEntity(nama = n, urutan = categoryDao.maxOrder() + 1)))
            }
        } catch (e: Exception) {
            Op.Fail(AppError.STORAGE, e.message)
        }
    }

    suspend fun renameCategory(id: Long, name: String): Op<Unit> {
        val n = name.trim()
        if (n.isEmpty()) return Op.Fail(AppError.NAME_EMPTY)
        return try {
            db.withTransaction {
                val c = categoryDao.get(id) ?: return@withTransaction Op.Fail(AppError.NOT_FOUND)
                val dup = categoryDao.findByName(n)
                if (dup != null && dup.id != id) return@withTransaction Op.Fail(AppError.NAME_DUPLICATE)
                categoryDao.update(c.copy(nama = n))
                Op.Ok(Unit)
            }
        } catch (e: Exception) {
            Op.Fail(AppError.STORAGE, e.message)
        }
    }

    suspend fun moveCategory(id: Long, direction: MoveDirection) {
        db.withTransaction {
            val list = categoryDao.getAll().toMutableList()
            val i = list.indexOfFirst { it.id == id }
            val j = if (direction == MoveDirection.UP) i - 1 else i + 1
            if (i < 0 || j < 0 || j >= list.size) return@withTransaction
            val tmp = list[i]
            list[i] = list[j]
            list[j] = tmp
            categoryDao.updateAll(list.mapIndexed { index, c -> c.copy(urutan = index + 1) })
        }
    }

    suspend fun countMenusIn(id: Long) = categoryDao.countMenus(id)

    /** Hapus kategori; menu di dalamnya dipindah ke [moveTo] (null = tanpa kategori). */
    suspend fun deleteCategory(id: Long, moveTo: Long?): Op<Unit> = try {
        db.withTransaction {
            categoryDao.moveMenus(id, moveTo)
            categoryDao.delete(id)
            Op.Ok(Unit)
        }
    } catch (e: Exception) {
        Op.Fail(AppError.STORAGE, e.message)
    }
}
