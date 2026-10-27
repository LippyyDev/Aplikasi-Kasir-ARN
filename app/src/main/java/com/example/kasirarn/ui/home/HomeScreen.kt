package com.example.kasirarn.ui.home

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.slideInVertically
import androidx.compose.animation.slideOutVertically
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.background
import androidx.compose.foundation.basicMarquee
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.BoxScope
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.GridItemSpan
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.items
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.Check
import androidx.compose.material.icons.outlined.Close
import androidx.compose.material.icons.outlined.EditNote
import androidx.compose.material.icons.outlined.Inventory2
import androidx.compose.material.icons.outlined.Notifications
import androidx.compose.material.icons.outlined.Person
import androidx.compose.material.icons.outlined.RestaurantMenu
import androidx.compose.material.icons.outlined.Search
import androidx.compose.material.icons.outlined.ShoppingBag
import androidx.compose.material.icons.outlined.ExpandLess
import androidx.compose.material3.BadgedBox
import androidx.compose.material.icons.outlined.Add
import androidx.compose.material3.FilledTonalIconButton
import androidx.compose.material3.IconButtonDefaults
import androidx.compose.ui.graphics.Color
import com.example.kasirarn.ui.components.ArnCard
import com.example.kasirarn.ui.components.ArnChip
import com.example.kasirarn.ui.components.ArnTopBar
import com.example.kasirarn.ui.components.MenuTile
import com.example.kasirarn.ui.components.SearchPill
import androidx.compose.material.icons.automirrored.outlined.ArrowForward
import androidx.compose.ui.text.font.FontWeight
import com.example.kasirarn.ui.components.QuantityStepper
import com.example.kasirarn.ui.components.TonalIconAction
import com.example.kasirarn.ui.components.initials
import com.example.kasirarn.ui.theme.isDarkScheme
import androidx.compose.material3.Badge
import androidx.compose.material3.VerticalDivider
import androidx.compose.material3.FilterChip
import androidx.compose.material3.FilterChipDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.pluralStringResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.role
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.example.kasirarn.R
import com.example.kasirarn.data.repo.CatalogItem
import com.example.kasirarn.data.settings.StockPolicy
import com.example.kasirarn.ui.components.EmptyState
import com.example.kasirarn.ui.components.MenuImage
import com.example.kasirarn.ui.components.MoneyText
import com.example.kasirarn.ui.components.StatusPill
import com.example.kasirarn.ui.components.Tone
import com.example.kasirarn.ui.format.LocalFmt
import com.example.kasirarn.ui.nav.ManageTab
import com.example.kasirarn.ui.nav.Routes
import com.example.kasirarn.ui.nav.rememberWindowInfo
import com.example.kasirarn.ui.nav.containerViewModel
import com.example.kasirarn.ui.theme.MoneyStyle
import com.example.kasirarn.ui.onboarding.CoachId
import com.example.kasirarn.ui.onboarding.coachTarget
import com.example.kasirarn.ui.theme.Spacing
import kotlinx.coroutines.launch

/** Lebar panel keranjang pada tata letak dua panel: sepertiga jendela, dibatasi supaya tetap nyaman. */
private fun cartPanelWidth(windowWidthDp: Int) = (windowWidthDp * 0.34f).toInt().coerceIn(300, 420).dp

@Composable
fun HomeScreen(onNavigate: (String) -> Unit) {
    val vm = containerViewModel { HomeViewModel(it) }
    val ui by vm.ui.collectAsStateWithLifecycle()
    val snackbar = remember { SnackbarHostState() }
    var showCart by remember { mutableStateOf(false) }
    var searching by androidx.compose.runtime.saveable.rememberSaveable { mutableStateOf(false) }
    var noteFor by remember { mutableStateOf<CatalogItem?>(null) }
    var confirmDummy by remember { mutableStateOf(false) }
    val context = LocalContext.current
    val resources = androidx.compose.ui.platform.LocalResources.current

    LaunchedEffect(Unit) {
        vm.events.collect { event ->
            when (event) {
                is HomeEvent.StockLimit -> {
                    snackbar.currentSnackbarData?.dismiss()
                    snackbar.showSnackbar(
                        resources.getString(R.string.home_stock_limit, event.menuName, event.available),
                    )
                }
                is HomeEvent.DummyDone -> snackbar.showSnackbar(
                    resources.getString(
                        R.string.dummy_done,
                        event.summary.transactions - event.summary.voided,
                        event.summary.menus,
                        event.summary.ingredients,
                    ),
                )
                HomeEvent.DummyFailed -> snackbar.showSnackbar(resources.getString(R.string.dummy_failed))
            }
        }
    }

    val window = rememberWindowInfo()
    val twoPane = window.isWide
    val showCartBar = !twoPane && ui.cart.count > 0

    Scaffold(
        containerColor = MaterialTheme.colorScheme.background,
        snackbarHost = {
            SnackbarHost(snackbar, modifier = Modifier.padding(bottom = if (showCartBar) 96.dp else 0.dp))
        },
        topBar = {
            HomeTopBar(
                query = ui.query,
                onQuery = vm::setQuery,
                searching = searching || ui.query.isNotEmpty(),
                onToggleSearch = {
                    val open = searching || ui.query.isNotEmpty()
                    if (open) vm.setQuery("")
                    searching = !open
                },
                unread = ui.unread,
                onNotifications = { onNavigate(Routes.NOTIFICATIONS) },
            )
        },
    ) { padding ->
        Row(Modifier.fillMaxSize().padding(top = padding.calculateTopPadding())) {
            Box(Modifier.weight(1f).fillMaxHeight()) {
                Column(Modifier.fillMaxSize()) {
                    if (ui.categories.isNotEmpty()) {
                        CategoryRow(ui, onSelect = vm::selectCategory)
                    }
                    HomeContent(
                        ui = ui,
                        showCartBar = showCartBar,
                        onAdd = vm::add,
                        onDecrease = { vm.setQuantity(it.menu.id, (ui.cartQty[it.menu.id] ?: 1) - 1) },
                        onNote = { noteFor = it },
                        onNavigate = onNavigate,
                        onDismissGuide = vm::dismissGuide,
                        onSample = { confirmDummy = true },
                        modifier = Modifier.weight(1f),
                    )
                }
                CartBarHost(visible = showCartBar, cart = ui.cart, onClick = { showCart = true }, onPay = { onNavigate(Routes.PAYMENT) })
            }
            if (twoPane) {
                // Tablet dan lanskap: keranjang selalu terlihat di sisi kanan, tanpa lembar bawah.
                VerticalDivider(color = MaterialTheme.colorScheme.outlineVariant)
                Surface(
                    color = MaterialTheme.colorScheme.surfaceContainerLowest,
                    modifier = Modifier.width(cartPanelWidth(window.widthDp)).fillMaxHeight(),
                ) {
                    CartPanel(
                        cart = ui.cart,
                        onQuantity = vm::setQuantity,
                        onRemove = vm::remove,
                        onNote = vm::setNote,
                        onDiscount = vm::setDiscount,
                        onClear = vm::clearCart,
                        onPay = { onNavigate(Routes.PAYMENT) },
                        modifier = Modifier.fillMaxSize().padding(top = Spacing.md).navigationBarsPadding(),
                        fillHeight = true,
                    )
                }
            }
        }
    }

    if (showCart && !twoPane) {
        CartSheet(
            cart = ui.cart,
            onDismiss = { showCart = false },
            onQuantity = vm::setQuantity,
            onRemove = vm::remove,
            onNote = vm::setNote,
            onDiscount = vm::setDiscount,
            onClear = {
                vm.clearCart()
                showCart = false
            },
            onPay = {
                showCart = false
                onNavigate(Routes.PAYMENT)
            },
        )
    }

    if (confirmDummy) {
        com.example.kasirarn.ui.backup.DummyScenarioDialog(
            onDismiss = { confirmDummy = false },
            onConfirm = { scenario ->
                confirmDummy = false
                vm.loadDummy(scenario)
            },
        )
    }
    if (vm.loadingDummy) {
        androidx.compose.material3.AlertDialog(
            onDismissRequest = {},
            confirmButton = {},
            text = {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    androidx.compose.material3.CircularProgressIndicator(Modifier.size(28.dp), strokeWidth = 3.dp)
                    Spacer(Modifier.width(Spacing.lg))
                    Text(stringResource(R.string.dummy_loading))
                }
            },
            containerColor = MaterialTheme.colorScheme.surfaceContainerHigh,
        )
    }

    noteFor?.let { item ->
        NoteDialog(
            title = item.menu.nama,
            initial = ui.cartNotes[item.menu.id].orEmpty(),
            onDismiss = { noteFor = null },
            onSave = {
                vm.setNote(item.menu.id, it)
                noteFor = null
            },
        )
    }
}

/** Dipisah dari tata letak Row supaya AnimatedVisibility tidak bentrok dengan penerima RowScope. */
@Composable
private fun BoxScope.CartBarHost(visible: Boolean, cart: CartUi, onClick: () -> Unit, onPay: () -> Unit) {
    AnimatedVisibility(
        visible = visible,
        modifier = Modifier.align(Alignment.BottomCenter),
        enter = slideInVertically { it } + fadeIn(),
        exit = slideOutVertically { it } + fadeOut(),
    ) {
        CartBar(cart, onClick = onClick, onPay = onPay)
    }
}

@Composable
private fun HomeTopBar(
    query: String,
    onQuery: (String) -> Unit,
    searching: Boolean,
    onToggleSearch: () -> Unit,
    unread: Int,
    onNotifications: () -> Unit,
) {
    Column(Modifier.background(MaterialTheme.colorScheme.background)) {
        ArnTopBar(
            title = stringResource(R.string.nav_cashier),
            onBack = null,
            subtitle = stringResource(R.string.home_subtitle),
            actions = {
                TonalIconAction(onClick = onToggleSearch, modifier = Modifier.coachTarget(CoachId.HOME_SEARCH)) {
                    Icon(
                        if (searching) Icons.Outlined.Close else Icons.Outlined.Search,
                        contentDescription = stringResource(R.string.home_search_hint),
                    )
                }
                TonalIconAction(onClick = onNotifications, modifier = Modifier.coachTarget(CoachId.HOME_BELL)) {
                    BadgedBox(badge = { if (unread > 0) Badge() }) {
                        Icon(Icons.Outlined.Notifications, contentDescription = stringResource(R.string.drawer_notifications))
                    }
                }
            },
        )
        AnimatedVisibility(visible = searching) {
            SearchPill(
                value = query,
                onValueChange = onQuery,
                placeholder = stringResource(R.string.home_search_hint),
                modifier = Modifier.padding(start = Spacing.lg, end = Spacing.lg, bottom = Spacing.sm),
            )
        }
    }
}

@Composable
private fun CategoryRow(ui: HomeUi, onSelect: (Long?) -> Unit) {
    LazyRow(
        contentPadding = PaddingValues(horizontal = Spacing.lg),
        horizontalArrangement = Arrangement.spacedBy(Spacing.sm),
        modifier = Modifier.fillMaxWidth().coachTarget(CoachId.HOME_CATEGORIES).padding(vertical = Spacing.sm),
    ) {
        item(key = "all") {
            ArnChip(stringResource(R.string.home_all), ui.selectedCategory == null, onClick = { onSelect(null) })
        }
        items(ui.categories, key = { it.id }) { c ->
            ArnChip(c.nama, ui.selectedCategory == c.id, onClick = { onSelect(c.id) })
        }
    }
}

@Composable
private fun HomeContent(
    ui: HomeUi,
    onAdd: (CatalogItem) -> Unit,
    onDecrease: (CatalogItem) -> Unit,
    onNote: (CatalogItem) -> Unit,
    onNavigate: (String) -> Unit,
    onDismissGuide: () -> Unit,
    onSample: () -> Unit,
    showCartBar: Boolean,
    modifier: Modifier = Modifier,
) {
    val bottomPad = if (showCartBar) 112.dp else Spacing.xl
    LazyVerticalGrid(
        columns = GridCells.Adaptive(minSize = 140.dp),
        modifier = modifier.fillMaxSize(),
        contentPadding = PaddingValues(start = Spacing.lg, end = Spacing.lg, top = Spacing.xs, bottom = bottomPad),
        horizontalArrangement = Arrangement.spacedBy(Spacing.md),
        verticalArrangement = Arrangement.spacedBy(Spacing.md),
    ) {
        val guide = ui.guide
        if (guide != null) {
            item(span = { GridItemSpan(maxLineSpan) }, key = "guide") {
                GuideCard(guide, onNavigate, onDismissGuide, onSample)
            }
        }
        if (ui.items.isEmpty() && ui.loaded) {
            item(span = { GridItemSpan(maxLineSpan) }, key = "empty") {
                when {
                    ui.query.isNotBlank() -> EmptyState(
                        icon = Icons.Outlined.Search,
                        title = stringResource(R.string.home_no_result_title),
                        body = stringResource(R.string.home_no_result_body, ui.query.trim()),
                    )
                    ui.categories.isEmpty() -> EmptyState(
                        icon = Icons.Outlined.RestaurantMenu,
                        title = stringResource(R.string.home_empty_category_title),
                        body = stringResource(R.string.home_empty_category_body),
                        actionLabel = stringResource(R.string.home_open_menu_setup),
                        onAction = { onNavigate(Routes.manage(ManageTab.CATEGORY)) },
                    )
                    ui.sellableMenuCount == 0 -> EmptyState(
                        icon = Icons.Outlined.RestaurantMenu,
                        title = stringResource(R.string.home_empty_menu_title),
                        body = stringResource(R.string.home_empty_menu_body),
                        actionLabel = stringResource(R.string.home_open_menu_setup),
                        onAction = { onNavigate(Routes.manage(ManageTab.MENU)) },
                    )
                    else -> EmptyState(
                        icon = Icons.Outlined.RestaurantMenu,
                        title = stringResource(R.string.home_empty_in_category_title),
                        body = stringResource(R.string.home_empty_in_category_body),
                    )
                }
            }
        }
        items(ui.items, key = { it.menu.id }) { item ->
            MenuCard(
                modifier = if (item.menu.id == ui.items.first().menu.id) Modifier.coachTarget(CoachId.HOME_MENU) else Modifier,
                item = item,
                quantity = ui.cartQty[item.menu.id] ?: 0,
                policy = ui.policy,
                onAdd = { onAdd(item) },
                onDecrease = { onDecrease(item) },
                onNote = { onNote(item) },
            )
        }
    }
}

@OptIn(ExperimentalFoundationApi::class)
@Composable
private fun MenuCard(
    modifier: Modifier = Modifier,
    item: CatalogItem,
    quantity: Int,
    policy: StockPolicy,
    onAdd: () -> Unit,
    onDecrease: () -> Unit,
    onNote: () -> Unit,
) {
    val blocked = item.soldOut && policy == StockPolicy.BLOCK
    val inCart = quantity > 0
    val scheme = MaterialTheme.colorScheme
    val dark = isDarkScheme()
    val description = buildString {
        append(item.menu.nama)
        append(", ")
        append(LocalFmt.current.money(item.menu.hargaJual))
        if (blocked) append(", ").append(stringResource(R.string.home_sold_out))
        if (inCart) append(", ").append(stringResource(R.string.home_in_cart, quantity))
    }
    Surface(
        modifier = modifier
            .fillMaxWidth()
            .semantics {
                contentDescription = description
                role = Role.Button
            }
            .combinedClickable(enabled = !blocked, onClick = onAdd, onLongClick = onNote),
        shape = MaterialTheme.shapes.large,
        color = if (dark) scheme.surfaceContainerLow else scheme.surfaceContainerLowest,
        border = if (inCart) BorderStroke(2.dp, scheme.primary) else null,
    ) {
        Column(Modifier.padding(Spacing.sm).alpha(if (blocked) 0.55f else 1f)) {
            Box(
                Modifier
                    .fillMaxWidth()
                    .aspectRatio(1.4f)
                    .clip(RoundedCornerShape(18.dp)),
            ) {
                MenuTile(photo = item.menu.fotoPath, name = item.menu.nama, category = item.categoryName, modifier = Modifier.fillMaxSize())
                Row(Modifier.align(Alignment.TopStart).padding(Spacing.sm)) {
                    when {
                        blocked -> StatusPill(stringResource(R.string.home_sold_out), Tone.BAD)
                        item.soldOut -> StatusPill(stringResource(R.string.home_stock_short), Tone.WARN)
                        item.portions != null && item.portions <= 3 ->
                            StatusPill(stringResource(R.string.home_portions_left, item.portions), Tone.WARN)
                    }
                }
                if (inCart) {
                    Box(
                        Modifier.align(Alignment.TopEnd).padding(Spacing.sm).size(26.dp).clip(CircleShape).background(scheme.primary),
                        contentAlignment = Alignment.Center,
                    ) {
                        Text(quantity.toString(), style = MaterialTheme.typography.labelMedium.copy(fontWeight = FontWeight.ExtraBold), color = scheme.onPrimary)
                    }
                }
                Surface(
                    modifier = Modifier.align(Alignment.BottomStart).padding(Spacing.sm),
                    shape = RoundedCornerShape(50),
                    color = scheme.surfaceContainerLowest,
                ) {
                    MoneyText(
                        item.menu.hargaJual,
                        style = MaterialTheme.typography.labelMedium,
                        color = scheme.primary,
                        fontWeight = FontWeight.ExtraBold,
                        modifier = Modifier.padding(horizontal = 10.dp, vertical = 4.dp),
                    )
                }
            }
            Row(
                Modifier.fillMaxWidth().padding(start = Spacing.xs, top = Spacing.sm, bottom = Spacing.xxs),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(Spacing.xs),
            ) {
                Column(Modifier.weight(1f)) {
                    Text(
                        item.menu.nama,
                        style = MaterialTheme.typography.titleSmall.copy(fontWeight = FontWeight.Bold),
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                    )
                    Text(
                        item.categoryName.orEmpty(),
                        style = MaterialTheme.typography.labelSmall.copy(fontWeight = FontWeight.Medium),
                        color = scheme.onSurfaceVariant,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                    )
                }
                if (!blocked) {
                    Surface(
                        onClick = onAdd,
                        modifier = Modifier.size(36.dp),
                        shape = CircleShape,
                        color = if (inCart) scheme.primary else scheme.primaryContainer,
                        contentColor = if (inCart) scheme.onPrimary else scheme.onPrimaryContainer,
                    ) {
                        Box(contentAlignment = Alignment.Center) {
                            Icon(Icons.Outlined.Add, contentDescription = stringResource(R.string.cart_increase), modifier = Modifier.size(20.dp))
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun CartBar(cart: CartUi, onClick: () -> Unit, onPay: () -> Unit) {
    val scheme = MaterialTheme.colorScheme
    val fmt = LocalFmt.current
    val description = pluralStringResource(R.plurals.cart_items, cart.count, cart.count) + ", " + fmt.money(cart.total)
    Surface(
        onClick = onClick,
        modifier = Modifier
            .padding(horizontal = Spacing.lg, vertical = Spacing.sm)
            .fillMaxWidth()
            .heightIn(min = 72.dp)
            .coachTarget(CoachId.HOME_CART)
            .semantics { contentDescription = description },
        shape = RoundedCornerShape(28.dp),
        color = scheme.primary,
        contentColor = scheme.onPrimary,
        shadowElevation = 8.dp,
    ) {
        Row(
            Modifier.padding(start = Spacing.md, end = Spacing.sm),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Box(
                Modifier.size(44.dp).clip(RoundedCornerShape(16.dp)).background(scheme.onPrimary.copy(alpha = 0.16f)),
                contentAlignment = Alignment.Center,
            ) {
                Icon(Icons.Outlined.ShoppingBag, contentDescription = null, modifier = Modifier.size(22.dp))
            }
            Spacer(Modifier.width(Spacing.md))
            Column(Modifier.weight(1f)) {
                Text(
                    pluralStringResource(R.plurals.cart_items, cart.count, cart.count),
                    style = MaterialTheme.typography.labelMedium,
                    color = scheme.onPrimary.copy(alpha = 0.85f),
                )
                Text(fmt.money(cart.total), style = MaterialTheme.typography.titleLarge.copy(fontWeight = FontWeight.ExtraBold).merge(MoneyStyle), maxLines = 1)
            }
            Surface(onClick = onPay, shape = RoundedCornerShape(50), color = scheme.onPrimary, contentColor = scheme.primary) {
                Row(Modifier.padding(horizontal = 18.dp, vertical = 14.dp), verticalAlignment = Alignment.CenterVertically) {
                    Text(stringResource(R.string.cart_pay), style = MaterialTheme.typography.labelLarge.copy(fontWeight = FontWeight.ExtraBold))
                    Spacer(Modifier.width(Spacing.sm))
                    Icon(Icons.AutoMirrored.Outlined.ArrowForward, contentDescription = null, modifier = Modifier.size(18.dp))
                }
            }
        }
    }
}

@Composable
private fun GuideCard(guide: GuideState, onNavigate: (String) -> Unit, onDismiss: () -> Unit, onSample: () -> Unit) {
    val steps = listOf(
        Triple(R.string.guide_step_ingredient, guide.hasIngredients, Routes.manage(ManageTab.INGREDIENT)),
        Triple(R.string.guide_step_category, guide.hasCategories, Routes.manage(ManageTab.CATEGORY)),
        Triple(R.string.guide_step_menu, guide.hasMenus, Routes.manage(ManageTab.MENU)),
    )
    ArnCard(Modifier.fillMaxWidth()) {
        Column {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Icon(Icons.Outlined.Inventory2, contentDescription = null, tint = MaterialTheme.colorScheme.primary)
                Spacer(Modifier.width(Spacing.sm))
                Text(stringResource(R.string.guide_title), style = MaterialTheme.typography.titleMedium, modifier = Modifier.weight(1f))
                TextButton(onClick = onDismiss) { Text(stringResource(R.string.guide_hide)) }
            }
            Text(
                stringResource(R.string.guide_body),
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
            Spacer(Modifier.height(Spacing.md))
            steps.forEachIndexed { index, (label, done, route) ->
                Row(
                    Modifier
                        .fillMaxWidth()
                        .heightIn(min = Spacing.minTouch)
                        .clip(MaterialTheme.shapes.small)
                        .combinedClickable(onClick = { onNavigate(route) }),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    Box(
                        Modifier
                            .size(28.dp)
                            .clip(CircleShape)
                            .background(if (done) MaterialTheme.colorScheme.secondary else MaterialTheme.colorScheme.surfaceContainerHigh),
                        contentAlignment = Alignment.Center,
                    ) {
                        if (done) {
                            Icon(Icons.Outlined.Check, contentDescription = null, tint = MaterialTheme.colorScheme.onSecondary, modifier = Modifier.size(16.dp))
                        } else {
                            Text("${index + 1}", style = MaterialTheme.typography.labelLarge)
                        }
                    }
                    Spacer(Modifier.width(Spacing.md))
                    Text(
                        stringResource(label),
                        style = MaterialTheme.typography.bodyLarge,
                        color = if (done) MaterialTheme.colorScheme.onSurfaceVariant else MaterialTheme.colorScheme.onSurface,
                    )
                }
            }
            Row(Modifier.fillMaxWidth().heightIn(min = Spacing.minTouch), verticalAlignment = Alignment.CenterVertically) {
                Box(
                    Modifier.size(28.dp).clip(CircleShape).background(MaterialTheme.colorScheme.surfaceContainerHigh),
                    contentAlignment = Alignment.Center,
                ) { Text("4", style = MaterialTheme.typography.labelLarge) }
                Spacer(Modifier.width(Spacing.md))
                Text(stringResource(R.string.guide_step_sell), style = MaterialTheme.typography.bodyLarge)
            }
            if (!guide.hasIngredients && !guide.hasCategories && !guide.hasMenus) {
                Spacer(Modifier.height(Spacing.md))
                androidx.compose.material3.OutlinedButton(
                    onClick = onSample,
                    modifier = Modifier.fillMaxWidth().heightIn(min = Spacing.minTouch).coachTarget(CoachId.HOME_SAMPLE),
                    shape = MaterialTheme.shapes.medium,
                ) { Text(stringResource(R.string.dummy_guide_button), style = MaterialTheme.typography.labelLarge) }
            }
        }
    }
}
