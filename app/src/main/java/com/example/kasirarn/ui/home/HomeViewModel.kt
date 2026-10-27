package com.example.kasirarn.ui.home

import androidx.compose.runtime.getValue
import androidx.compose.runtime.setValue
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.example.kasirarn.AppContainer
import com.example.kasirarn.data.db.CategoryEntity
import com.example.kasirarn.data.repo.CatalogItem
import com.example.kasirarn.data.settings.AppLanguage
import com.example.kasirarn.data.settings.AppSettings
import com.example.kasirarn.data.settings.StockPolicy
import com.example.kasirarn.data.settings.ThemeMode
import com.example.kasirarn.domain.CartLine
import com.example.kasirarn.domain.CartMath
import com.example.kasirarn.domain.Discount
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.SharedFlow
import kotlinx.coroutines.flow.asSharedFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch

data class GuideState(val hasIngredients: Boolean, val hasCategories: Boolean, val hasMenus: Boolean)

data class CartLineUi(
    val menuId: Long,
    val name: String,
    val photo: String?,
    val price: Long,
    val quantity: Int,
    val note: String,
    val category: String? = null,
) {
    val lineTotal: Long get() = price * quantity
}

data class CartUi(
    val lines: List<CartLineUi> = emptyList(),
    val discount: Discount = Discount.NONE,
) {
    val count: Int get() = lines.sumOf { it.quantity }
    val subtotal: Long get() = lines.sumOf { it.lineTotal }
    val discountAmount: Long get() = CartMath.discountAmount(subtotal, discount)
    val total: Long get() = subtotal - discountAmount
}

data class HomeUi(
    val loaded: Boolean = false,
    val categories: List<CategoryEntity> = emptyList(),
    val selectedCategory: Long? = null,
    val query: String = "",
    val items: List<CatalogItem> = emptyList(),
    val sellableMenuCount: Int = 0,
    val cartQty: Map<Long, Int> = emptyMap(),
    val cartNotes: Map<Long, String> = emptyMap(),
    val cart: CartUi = CartUi(),
    val policy: StockPolicy = StockPolicy.BLOCK,
    val unread: Int = 0,
    val businessName: String = "",
    val logo: String? = null,
    val guide: GuideState? = null,
    val lastBackupAt: Long = 0,
    val settings: AppSettings = AppSettings(),
)

sealed interface HomeEvent {
    data class DummyDone(val summary: com.example.kasirarn.data.dummy.DummySummary) : HomeEvent
    data object DummyFailed : HomeEvent
    data class StockLimit(val menuName: String, val available: Long) : HomeEvent
}

class HomeViewModel(private val c: AppContainer) : ViewModel() {
    private val query = MutableStateFlow("")
    private val selectedCategory = MutableStateFlow<Long?>(null)
    private val _events = MutableSharedFlow<HomeEvent>(extraBufferCapacity = 4)
    val events: SharedFlow<HomeEvent> = _events.asSharedFlow()

    @Volatile
    private var latestCatalog: List<CatalogItem> = emptyList()

    @Volatile
    private var latestPolicy: StockPolicy = StockPolicy.BLOCK

    private val catalogFlow = c.menus.observeCatalog()

    private val data = combine(
        catalogFlow,
        c.menus.observeCategories(),
        c.ingredients.observeIngredients(),
        c.settings.settings,
        c.profile.observe(),
    ) { catalog, categories, ingredients, settings, profile ->
        latestCatalog = catalog
        latestPolicy = settings.stockPolicy
        Data(catalog, categories, ingredients.size, settings, profile?.nama.orEmpty(), profile?.logoPath)
    }

    private data class Data(
        val catalog: List<CatalogItem>,
        val categories: List<CategoryEntity>,
        val ingredientCount: Int,
        val settings: AppSettings,
        val businessName: String,
        val logo: String?,
    )

    val ui: StateFlow<HomeUi> = combine(
        data,
        query,
        selectedCategory,
        combine(c.cart.lines, c.cart.discount) { l, d -> l to d },
        c.notifications.observeUnreadCount(),
    ) { d, q, cat, cart, unread ->
        val byId = d.catalog.associateBy { it.menu.id }
        val sellable = d.catalog.filter { it.menu.aktif && it.menu.kategoriId != null }
        val needle = q.trim().lowercase()
        val items = sellable.filter {
            (cat == null || it.menu.kategoriId == cat) && (needle.isEmpty() || it.menu.nama.lowercase().contains(needle))
        }
        val lines = cart.first.mapNotNull { l ->
            byId[l.menuId]?.let { CartLineUi(l.menuId, it.menu.nama, it.menu.fotoPath, it.menu.hargaJual, l.quantity, l.note, it.categoryName) }
        }
        val guide = GuideState(d.ingredientCount > 0, d.categories.isNotEmpty(), d.catalog.isNotEmpty())
        HomeUi(
            loaded = true,
            categories = d.categories,
            selectedCategory = cat,
            query = q,
            items = items,
            sellableMenuCount = sellable.size,
            cartQty = cart.first.associate { it.menuId to it.quantity },
            cartNotes = cart.first.associate { it.menuId to it.note },
            cart = CartUi(lines, cart.second),
            policy = d.settings.stockPolicy,
            unread = unread,
            businessName = d.businessName,
            logo = d.logo,
            guide = if (!d.settings.guideDismissed && !(guide.hasIngredients && guide.hasCategories && guide.hasMenus)) guide else null,
            lastBackupAt = d.settings.lastBackupAt,
            settings = d.settings,
        )
    }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), HomeUi())

    fun setQuery(text: String) { query.value = text }
    fun selectCategory(id: Long?) { selectedCategory.value = id }

    /** Tambah satu porsi. Pada mode blokir, tidak boleh melebihi porsi yang tersedia. */
    fun add(item: CatalogItem) {
        val inCart = c.cart.quantityOf(item.menu.id)
        val limit = item.portions
        if (latestPolicy == StockPolicy.BLOCK && limit != null && inCart + 1 > limit) {
            _events.tryEmit(HomeEvent.StockLimit(item.menu.nama, limit.coerceAtLeast(0)))
            return
        }
        c.cart.add(item.menu.id)
    }

    fun setQuantity(menuId: Long, quantity: Int) {
        val item = latestCatalog.firstOrNull { it.menu.id == menuId }
        val limit = item?.portions
        if (item != null && latestPolicy == StockPolicy.BLOCK && limit != null && quantity > limit) {
            _events.tryEmit(HomeEvent.StockLimit(item.menu.nama, limit.coerceAtLeast(0)))
            return
        }
        c.cart.setQuantity(menuId, quantity)
    }

    fun remove(menuId: Long) = c.cart.remove(menuId)
    fun setNote(menuId: Long, note: String) = c.cart.setNote(menuId, note)
    fun setDiscount(d: Discount) = c.cart.setDiscount(d)
    fun clearCart() = c.cart.clear()

    fun setTheme(mode: ThemeMode) { viewModelScope.launch { c.settings.setTheme(mode) } }
    fun setDynamicColor(on: Boolean) { viewModelScope.launch { c.settings.setDynamicColor(on) } }
    fun setLanguage(lang: AppLanguage) { viewModelScope.launch { c.settings.setLanguage(lang) } }
    var loadingDummy by androidx.compose.runtime.mutableStateOf(false)
        private set

    fun loadDummy(scenario: com.example.kasirarn.data.dummy.DummyScenario) {
        loadingDummy = true
        viewModelScope.launch {
            val event = try {
                HomeEvent.DummyDone(c.dummy.loadBundled(scenario))
            } catch (e: Exception) {
                HomeEvent.DummyFailed
            }
            loadingDummy = false
            _events.tryEmit(event)
        }
    }

    fun dismissGuide() { viewModelScope.launch { c.settings.setGuideDismissed(true) } }
}
