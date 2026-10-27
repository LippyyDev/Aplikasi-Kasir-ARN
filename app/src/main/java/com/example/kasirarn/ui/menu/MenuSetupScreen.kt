package com.example.kasirarn.ui.menu

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.GridItemSpan
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.Add
import androidx.compose.material.icons.outlined.Category
import androidx.compose.material.icons.outlined.KeyboardArrowDown
import androidx.compose.material.icons.outlined.KeyboardArrowUp
import androidx.compose.material.icons.outlined.MoreVert
import androidx.compose.material.icons.outlined.RestaurantMenu
import androidx.compose.material.icons.outlined.Search
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.ExtendedFloatingActionButton
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.PrimaryTabRow
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Tab
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.pluralStringResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.lifecycle.ViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewModelScope
import com.example.kasirarn.AppContainer
import com.example.kasirarn.R
import com.example.kasirarn.data.db.CategoryEntity
import com.example.kasirarn.data.repo.CatalogItem
import com.example.kasirarn.data.repo.MoveDirection
import com.example.kasirarn.data.repo.Op
import com.example.kasirarn.ui.components.ArnCard
import com.example.kasirarn.ui.components.ArnField
import com.example.kasirarn.ui.components.ArnTopBar
import com.example.kasirarn.ui.components.ScreenHeader
import com.example.kasirarn.ui.components.ConfirmDialog
import com.example.kasirarn.ui.components.EmptyState
import com.example.kasirarn.ui.components.MenuImage
import com.example.kasirarn.ui.components.MoneyText
import com.example.kasirarn.ui.components.StatusPill
import com.example.kasirarn.ui.components.Tone
import com.example.kasirarn.ui.components.errorMessage
import com.example.kasirarn.ui.format.LocalFmt
import com.example.kasirarn.ui.home.RadioRow
import com.example.kasirarn.ui.nav.ManageTab
import com.example.kasirarn.ui.nav.containerViewModel
import com.example.kasirarn.ui.onboarding.CoachId
import com.example.kasirarn.ui.onboarding.coachTarget
import com.example.kasirarn.ui.theme.Spacing
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch

data class MenuSetupUi(
    val loaded: Boolean = false,
    val categories: List<CategoryEntity> = emptyList(),
    val menuCounts: Map<Long, Int> = emptyMap(),
    val uncategorizedCount: Int = 0,
    val menus: List<CatalogItem> = emptyList(),
    val totalMenus: Int = 0,
    val query: String = "",
)

class MenuSetupViewModel(private val c: AppContainer) : ViewModel() {
    private val query = MutableStateFlow("")

    val ui: StateFlow<MenuSetupUi> = combine(
        c.menus.observeCatalog(),
        c.menus.observeCategories(),
        query,
    ) { catalog, categories, q ->
        val needle = q.trim().lowercase()
        MenuSetupUi(
            loaded = true,
            categories = categories,
            menuCounts = catalog.groupBy { it.menu.kategoriId ?: -1L }.mapValues { it.value.size },
            uncategorizedCount = catalog.count { it.menu.kategoriId == null },
            menus = catalog.filter { needle.isEmpty() || it.menu.nama.lowercase().contains(needle) },
            totalMenus = catalog.size,
            query = q,
        )
    }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), MenuSetupUi())

    fun setQuery(q: String) { query.value = q }

    suspend fun addCategory(name: String) = c.menus.addCategory(name)
    suspend fun renameCategory(id: Long, name: String) = c.menus.renameCategory(id, name)
    fun moveCategory(id: Long, direction: MoveDirection) { viewModelScope.launch { c.menus.moveCategory(id, direction) } }

    fun deleteCategory(id: Long, moveTo: Long?, done: (Op<Unit>) -> Unit) {
        viewModelScope.launch { done(c.menus.deleteCategory(id, moveTo)) }
    }

    fun duplicate(id: Long, suffix: String, done: (Op<Long>) -> Unit) {
        viewModelScope.launch { done(c.menus.duplicate(id, suffix)) }
    }

    fun setActive(id: Long, active: Boolean) { viewModelScope.launch { c.menus.setActive(id, active) } }

    fun deleteMenu(id: Long, done: (Op<String?>) -> Unit) {
        viewModelScope.launch {
            val r = c.menus.delete(id)
            if (r is Op.Ok) c.images.delete(r.value)
            done(r)
        }
    }
}

/**
 * Mode Kelola: semua yang membentuk katalog dalam satu tempat, dipisah tab:
 * Menu, Bahan baku, dan Kategori. Tombol tambah mengikuti tab yang aktif.
 */
@Composable
fun ManageScreen(
    initialTab: Int,
    onEditMenu: (Long?) -> Unit,
    onOpenIngredient: (Long) -> Unit,
    onAddIngredient: () -> Unit,
) {
    val vm = containerViewModel { MenuSetupViewModel(it) }
    val ingredientsVm = containerViewModel { com.example.kasirarn.ui.ingredient.IngredientsViewModel(it) }
    val ui by vm.ui.collectAsStateWithLifecycle()
    val ingredientsUi by ingredientsVm.ui.collectAsStateWithLifecycle()
    var tab by rememberSaveable(initialTab) { mutableIntStateOf(initialTab.coerceIn(0, 2)) }
    val snackbar = remember { SnackbarHostState() }
    val scope = rememberCoroutineScope()
    val context = LocalContext.current
    val resources = androidx.compose.ui.platform.LocalResources.current
    var nameDialog by remember { mutableStateOf<CategoryDialog?>(null) }
    var deleteCategory by remember { mutableStateOf<CategoryEntity?>(null) }
    var deleteMenu by remember { mutableStateOf<CatalogItem?>(null) }
    var menuOpen by remember { mutableStateOf(false) }
    var showPolicy by remember { mutableStateOf(false) }

    Scaffold(
        containerColor = MaterialTheme.colorScheme.background,
        snackbarHost = { SnackbarHost(snackbar) },
        topBar = {
            ScreenHeader(
                title = stringResource(R.string.nav_manage),
                actions = {
                    if (tab == ManageTab.INGREDIENT) {
                        IconButton(onClick = { menuOpen = true }) {
                            Icon(Icons.Outlined.MoreVert, contentDescription = stringResource(R.string.action_more))
                        }
                        DropdownMenu(expanded = menuOpen, onDismissRequest = { menuOpen = false }) {
                            DropdownMenuItem(
                                text = { Text(stringResource(R.string.stock_policy_title)) },
                                onClick = {
                                    menuOpen = false
                                    showPolicy = true
                                },
                            )
                        }
                    }
                },
            ) { tabModifier ->
                PrimaryTabRow(
                    selectedTabIndex = tab,
                    modifier = tabModifier,
                    containerColor = MaterialTheme.colorScheme.background,
                    contentColor = MaterialTheme.colorScheme.primary,
                ) {
                    listOf(
                        ManageTab.MENU to R.string.menu_tab_menus,
                        ManageTab.INGREDIENT to R.string.tab_ingredients,
                        ManageTab.CATEGORY to R.string.menu_tab_categories,
                    ).forEach { (index, label) ->
                        Tab(
                            selected = tab == index,
                            onClick = { tab = index },
                            unselectedContentColor = MaterialTheme.colorScheme.onSurfaceVariant,
                            text = { Text(stringResource(label)) },
                        )
                    }
                }
            }
        },
        floatingActionButton = {
            val (label, action) = when (tab) {
                ManageTab.MENU -> R.string.menu_add to { onEditMenu(null) }
                ManageTab.INGREDIENT -> R.string.ingredient_add to onAddIngredient
                else -> R.string.category_add to { nameDialog = CategoryDialog(null) }
            }
            ExtendedFloatingActionButton(
                onClick = action,
                modifier = Modifier.coachTarget(CoachId.MANAGE_FAB),
                icon = { Icon(Icons.Outlined.Add, contentDescription = null) },
                text = { Text(stringResource(label)) },
                containerColor = MaterialTheme.colorScheme.primary,
                contentColor = MaterialTheme.colorScheme.onPrimary,
            )
        },
    ) { padding ->
        Column(Modifier.fillMaxSize().padding(padding)) {
            when (tab) {
                ManageTab.MENU -> MenusTab(
                    ui = ui,
                    onQuery = vm::setQuery,
                    onEdit = { onEditMenu(it.menu.id) },
                    onDuplicate = { item ->
                        vm.duplicate(item.menu.id, " " + resources.getString(R.string.menu_copy_suffix)) { r ->
                            if (r is Op.Fail) scope.launch { snackbar.showSnackbar(resources.getString(R.string.err_storage)) }
                        }
                    },
                    onToggle = { vm.setActive(it.menu.id, !it.menu.aktif) },
                    onDelete = { deleteMenu = it },
                )
                ManageTab.INGREDIENT -> com.example.kasirarn.ui.ingredient.IngredientsTab(
                    ui = ingredientsUi,
                    onQuery = ingredientsVm::setQuery,
                    onOpen = onOpenIngredient,
                    onAdd = onAddIngredient,
                )
                else -> CategoriesTab(
                    ui = ui,
                    onRename = { nameDialog = CategoryDialog(it) },
                    onMove = vm::moveCategory,
                    onDelete = { deleteCategory = it },
                )
            }
        }
    }

    if (showPolicy) {
        com.example.kasirarn.ui.ingredient.StockPolicyDialog(
            current = ingredientsUi.policy,
            onSelect = ingredientsVm::setPolicy,
            onDismiss = { showPolicy = false },
        )
    }
    nameDialog?.let { dialog ->
        NameDialog(
            title = stringResource(if (dialog.category == null) R.string.category_add else R.string.category_rename),
            label = stringResource(R.string.category_name),
            initial = dialog.category?.nama.orEmpty(),
            onDismiss = { nameDialog = null },
            onSubmit = { text ->
                val r = if (dialog.category == null) vm.addCategory(text) else vm.renameCategory(dialog.category.id, text)
                if (r is Op.Fail) r else {
                    nameDialog = null
                    null
                }
            },
        )
    }
    deleteCategory?.let { category ->
        DeleteCategoryDialog(
            category = category,
            menuCount = ui.menuCounts[category.id] ?: 0,
            others = ui.categories.filter { it.id != category.id },
            onDismiss = { deleteCategory = null },
            onConfirm = { moveTo ->
                deleteCategory = null
                vm.deleteCategory(category.id, moveTo) { r ->
                    if (r is Op.Fail) scope.launch { snackbar.showSnackbar(resources.getString(R.string.err_storage)) }
                }
            },
        )
    }
    deleteMenu?.let { item ->
        ConfirmDialog(
            title = stringResource(R.string.menu_delete_title, item.menu.nama),
            text = stringResource(R.string.menu_delete_body),
            confirmLabel = stringResource(R.string.action_delete),
            dismissLabel = stringResource(R.string.action_cancel),
            destructive = true,
            onDismiss = { deleteMenu = null },
            onConfirm = {
                deleteMenu = null
                vm.deleteMenu(item.menu.id) { r ->
                    if (r is Op.Fail) scope.launch { snackbar.showSnackbar(resources.getString(R.string.err_storage)) }
                }
            },
        )
    }
}

private data class CategoryDialog(val category: CategoryEntity?)

@Composable
private fun CategoriesTab(
    ui: MenuSetupUi,
    onRename: (CategoryEntity) -> Unit,
    onMove: (Long, MoveDirection) -> Unit,
    onDelete: (CategoryEntity) -> Unit,
) {
    if (ui.loaded && ui.categories.isEmpty()) {
        Column(Modifier.fillMaxSize(), verticalArrangement = Arrangement.Center) {
            EmptyState(
                icon = Icons.Outlined.Category,
                title = stringResource(R.string.category_empty_title),
                body = stringResource(R.string.category_empty_body),
            )
        }
        return
    }
    LazyVerticalGrid(
        columns = GridCells.Adaptive(minSize = 320.dp),
        modifier = Modifier.fillMaxSize(),
        contentPadding = PaddingValues(start = Spacing.lg, end = Spacing.lg, top = Spacing.sm, bottom = 96.dp),
        horizontalArrangement = Arrangement.spacedBy(Spacing.sm),
        verticalArrangement = Arrangement.spacedBy(Spacing.sm),
    ) {
        item(span = { GridItemSpan(maxLineSpan) }) {
            Text(
                stringResource(R.string.category_hint),
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.padding(bottom = Spacing.xs),
            )
        }
        items(ui.categories.size, key = { ui.categories[it].id }) { index ->
            val c = ui.categories[index]
            var menuOpen by remember { mutableStateOf(false) }
            ArnCard(contentPadding = PaddingValues(start = Spacing.lg, top = Spacing.xs, bottom = Spacing.xs, end = Spacing.xs)) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Column(Modifier.weight(1f)) {
                        Text(c.nama, style = MaterialTheme.typography.titleSmall, maxLines = 1, overflow = TextOverflow.Ellipsis)
                        val n = ui.menuCounts[c.id] ?: 0
                        Text(
                            pluralStringResource(R.plurals.menu_count, n, n),
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                    }
                    IconButton(onClick = { onMove(c.id, MoveDirection.UP) }, enabled = index > 0) {
                        Icon(Icons.Outlined.KeyboardArrowUp, contentDescription = stringResource(R.string.category_move_up, c.nama))
                    }
                    IconButton(onClick = { onMove(c.id, MoveDirection.DOWN) }, enabled = index < ui.categories.lastIndex) {
                        Icon(Icons.Outlined.KeyboardArrowDown, contentDescription = stringResource(R.string.category_move_down, c.nama))
                    }
                    IconButton(onClick = { menuOpen = true }) {
                        Icon(Icons.Outlined.MoreVert, contentDescription = stringResource(R.string.action_more))
                    }
                    DropdownMenu(expanded = menuOpen, onDismissRequest = { menuOpen = false }) {
                        DropdownMenuItem(
                            text = { Text(stringResource(R.string.category_rename)) },
                            onClick = {
                                menuOpen = false
                                onRename(c)
                            },
                        )
                        DropdownMenuItem(
                            text = { Text(stringResource(R.string.action_delete), color = MaterialTheme.colorScheme.error) },
                            onClick = {
                                menuOpen = false
                                onDelete(c)
                            },
                        )
                    }
                }
            }
        }
        if (ui.uncategorizedCount > 0) {
            item(span = { GridItemSpan(maxLineSpan) }) {
                Text(
                    pluralStringResource(R.plurals.menu_uncategorized, ui.uncategorizedCount, ui.uncategorizedCount),
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.tertiary,
                    modifier = Modifier.padding(top = Spacing.sm),
                )
            }
        }
    }
}

@Composable
private fun MenusTab(
    ui: MenuSetupUi,
    onQuery: (String) -> Unit,
    onEdit: (CatalogItem) -> Unit,
    onDuplicate: (CatalogItem) -> Unit,
    onToggle: (CatalogItem) -> Unit,
    onDelete: (CatalogItem) -> Unit,
) {
    if (ui.loaded && ui.totalMenus == 0) {
        Column(Modifier.fillMaxSize(), verticalArrangement = Arrangement.Center) {
            EmptyState(
                icon = Icons.Outlined.RestaurantMenu,
                title = stringResource(R.string.menu_empty_title),
                body = stringResource(R.string.menu_empty_body),
            )
        }
        return
    }
    val fmt = LocalFmt.current
    LazyVerticalGrid(
        columns = GridCells.Adaptive(minSize = 320.dp),
        modifier = Modifier.fillMaxSize(),
        contentPadding = PaddingValues(start = Spacing.lg, end = Spacing.lg, top = Spacing.sm, bottom = 96.dp),
        horizontalArrangement = Arrangement.spacedBy(Spacing.sm),
        verticalArrangement = Arrangement.spacedBy(Spacing.sm),
    ) {
        item(span = { GridItemSpan(maxLineSpan) }) {
            ArnField(
                value = ui.query,
                onValueChange = onQuery,
                label = stringResource(R.string.home_search_hint),
                trailing = { Icon(Icons.Outlined.Search, contentDescription = null) },
                imeAction = androidx.compose.ui.text.input.ImeAction.Search,
            )
        }
        if (ui.menus.isEmpty()) {
            item(span = { GridItemSpan(maxLineSpan) }) {
                EmptyState(
                    icon = Icons.Outlined.Search,
                    title = stringResource(R.string.home_no_result_title),
                    body = stringResource(R.string.home_no_result_body, ui.query.trim()),
                )
            }
        }
        items(ui.menus, key = { it.menu.id }) { item ->
            var menuOpen by remember { mutableStateOf(false) }
            ArnCard(onClick = { onEdit(item) }, contentPadding = PaddingValues(Spacing.md)) {
                Row(verticalAlignment = Alignment.Top) {
                    MenuImage(
                        photo = item.menu.fotoPath,
                        name = item.menu.nama,
                        modifier = Modifier.size(64.dp),
                        shape = MaterialTheme.shapes.small,
                    )
                    Spacer(Modifier.width(Spacing.md))
                    Column(Modifier.weight(1f)) {
                        Text(item.menu.nama, style = MaterialTheme.typography.titleSmall, maxLines = 2, overflow = TextOverflow.Ellipsis)
                        Text(
                            item.categoryName ?: stringResource(R.string.menu_no_category),
                            style = MaterialTheme.typography.bodySmall,
                            color = if (item.categoryName == null) MaterialTheme.colorScheme.tertiary else MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                        Spacer(Modifier.height(Spacing.xs))
                        MoneyText(item.menu.hargaJual, style = MaterialTheme.typography.titleSmall, color = MaterialTheme.colorScheme.primary)
                        val price = item.menu.hargaJual
                        val pct = if (price > 0) (item.margin * 100 + price / 2) / price else 0
                        Text(
                            stringResource(R.string.menu_cost_margin, fmt.money(item.hpp), pct.toString()),
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                        if (!item.menu.aktif || (!item.hppComplete && item.recipe.isNotEmpty())) {
                            Spacer(Modifier.height(Spacing.xs))
                            Row(horizontalArrangement = Arrangement.spacedBy(Spacing.xs)) {
                                if (!item.menu.aktif) StatusPill(stringResource(R.string.menu_inactive), Tone.NEUTRAL)
                                if (!item.hppComplete && item.recipe.isNotEmpty()) StatusPill(stringResource(R.string.menu_cost_incomplete), Tone.WARN)
                            }
                        }
                    }
                    Column {
                        IconButton(onClick = { menuOpen = true }) {
                            Icon(Icons.Outlined.MoreVert, contentDescription = stringResource(R.string.action_more))
                        }
                        DropdownMenu(expanded = menuOpen, onDismissRequest = { menuOpen = false }) {
                            DropdownMenuItem(text = { Text(stringResource(R.string.action_edit)) }, onClick = { menuOpen = false; onEdit(item) })
                            DropdownMenuItem(text = { Text(stringResource(R.string.menu_duplicate)) }, onClick = { menuOpen = false; onDuplicate(item) })
                            DropdownMenuItem(
                                text = { Text(stringResource(if (item.menu.aktif) R.string.menu_deactivate else R.string.menu_activate)) },
                                onClick = { menuOpen = false; onToggle(item) },
                            )
                            DropdownMenuItem(
                                text = { Text(stringResource(R.string.action_delete), color = MaterialTheme.colorScheme.error) },
                                onClick = { menuOpen = false; onDelete(item) },
                            )
                        }
                    }
                }
            }
        }
    }
}

/** Dialog satu isian nama. [onSubmit] mengembalikan galat untuk ditampilkan atau null bila berhasil. */
@Composable
fun NameDialog(
    title: String,
    label: String,
    initial: String,
    onDismiss: () -> Unit,
    onSubmit: suspend (String) -> Op.Fail?,
) {
    var text by remember { mutableStateOf(initial) }
    var error by remember { mutableStateOf<Op.Fail?>(null) }
    val scope = rememberCoroutineScope()
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(title) },
        text = {
            ArnField(
                value = text,
                onValueChange = {
                    text = it.take(40)
                    error = null
                },
                label = label,
                error = error?.let { errorMessage(it) },
                imeAction = androidx.compose.ui.text.input.ImeAction.Done,
            )
        },
        confirmButton = {
            TextButton(onClick = { scope.launch { error = onSubmit(text) } }) { Text(stringResource(R.string.action_save)) }
        },
        dismissButton = { TextButton(onClick = onDismiss) { Text(stringResource(R.string.action_cancel)) } },
        containerColor = MaterialTheme.colorScheme.surfaceContainerHigh,
    )
}

@Composable
private fun DeleteCategoryDialog(
    category: CategoryEntity,
    menuCount: Int,
    others: List<CategoryEntity>,
    onDismiss: () -> Unit,
    onConfirm: (moveTo: Long?) -> Unit,
) {
    if (menuCount == 0) {
        ConfirmDialog(
            title = stringResource(R.string.category_delete_title, category.nama),
            text = stringResource(R.string.category_delete_empty),
            confirmLabel = stringResource(R.string.action_delete),
            dismissLabel = stringResource(R.string.action_cancel),
            destructive = true,
            onConfirm = { onConfirm(null) },
            onDismiss = onDismiss,
        )
        return
    }
    var target by remember { mutableStateOf<Long?>(null) }
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(stringResource(R.string.category_delete_title, category.nama)) },
        text = {
            Column(Modifier.verticalScroll(rememberScrollState())) {
                Text(
                    pluralStringResource(R.plurals.category_delete_has_menus, menuCount, menuCount),
                    style = MaterialTheme.typography.bodyMedium,
                )
                Spacer(Modifier.height(Spacing.sm))
                RadioRow(stringResource(R.string.category_move_none), target == null) { target = null }
                others.forEach { other ->
                    RadioRow(stringResource(R.string.category_move_to, other.nama), target == other.id) { target = other.id }
                }
            }
        },
        confirmButton = {
            TextButton(onClick = { onConfirm(target) }) {
                Text(stringResource(R.string.category_delete_confirm), color = MaterialTheme.colorScheme.error)
            }
        },
        dismissButton = { TextButton(onClick = onDismiss) { Text(stringResource(R.string.action_cancel)) } },
        containerColor = MaterialTheme.colorScheme.surfaceContainerHigh,
    )
}
