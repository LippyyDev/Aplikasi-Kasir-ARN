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
            SnackbarHost(snackbar, modifier = Modifier.padding(bottom = if (showCartBar) 88.dp else 0.dp))
        },
        topBar = {
            HomeTopBar(
                query = ui.query,
                onQuery = vm::setQuery,
                unread = ui.unread,
                businessName = ui.businessName,
                logo = ui.logo,
                onNotifications = { onNavigate(Routes.NOTIFICATIONS) },
                onProfile = { onNavigate(Routes.PROFILE) },
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
                CartBarHost(visible = showCartBar, cart = ui.cart, onClick = { showCart = true })
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
private fun BoxScope.CartBarHost(visible: Boolean, cart: CartUi, onClick: () -> Unit) {
    AnimatedVisibility(
        visible = visible,
        modifier = Modifier.align(Alignment.BottomCenter),
        enter = slideInVertically { it } + fadeIn(),
        exit = slideOutVertically { it } + fadeOut(),
    ) {
        CartBar(cart, onClick = onClick)
    }
}

@Composable
private fun HomeTopBar(
    query: String,
    onQuery: (String) -> Unit,
    unread: Int,
    businessName: String,
    logo: String?,
    onNotifications: () -> Unit,
    onProfile: () -> Unit,
) {
    val searchLabel = stringResource(R.string.home_search_hint)
    val scheme = MaterialTheme.colorScheme
    Row(
        Modifier
            .fillMaxWidth()
            .background(scheme.background)
            .statusBarsPadding()
            .padding(horizontal = Spacing.lg, vertical = Spacing.sm),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(Spacing.sm),
    ) {
        Surface(
            modifier = Modifier.weight(1f).heightIn(min = 48.dp).coachTarget(CoachId.HOME_SEARCH),
            shape = RoundedCornerShape(50),
            color = scheme.surfaceContainerHigh,
        ) {
            Row(Modifier.padding(start = Spacing.lg, end = Spacing.xs), verticalAlignment = Alignment.CenterVertically) {
                Icon(Icons.Outlined.Search, contentDescription = null, tint = scheme.onSurfaceVariant, modifier = Modifier.size(20.dp))
                Spacer(Modifier.width(Spacing.sm))
                Box(Modifier.weight(1f).padding(vertical = Spacing.sm), contentAlignment = Alignment.CenterStart) {
                    if (query.isEmpty()) {
                        Text(
                            stringResource(R.string.home_search_hint),
                            style = MaterialTheme.typography.bodyLarge,
                            color = scheme.onSurfaceVariant,
                            maxLines = 1,
                        )
                    }
                    BasicTextField(
                        value = query,
                        onValueChange = onQuery,
                        singleLine = true,
                        textStyle = MaterialTheme.typography.bodyLarge.copy(color = scheme.onSurface),
                        cursorBrush = SolidColor(scheme.primary),
                        modifier = Modifier.fillMaxWidth().semantics { contentDescription = searchLabel },
                    )
                }
                if (query.isNotEmpty()) {
                    IconButton(onClick = { onQuery("") }, modifier = Modifier.size(40.dp)) {
                        Icon(Icons.Outlined.Close, contentDescription = stringResource(R.string.action_clear), modifier = Modifier.size(18.dp))
                    }
                }
            }
        }
        TonalIconAction(onClick = onNotifications) {
            BadgedBox(badge = { if (unread > 0) Badge() }) {
                Icon(Icons.Outlined.Notifications, contentDescription = stringResource(R.string.drawer_notifications))
            }
        }
        Surface(
            onClick = onProfile,
            modifier = Modifier.size(44.dp),
            shape = CircleShape,
            color = scheme.primaryContainer,
            contentColor = scheme.onPrimaryContainer,
        ) {
            Box(contentAlignment = Alignment.Center, modifier = Modifier.fillMaxSize()) {
                when {
                    logo != null -> MenuImage(photo = logo, name = businessName, modifier = Modifier.fillMaxSize(), shape = CircleShape)
                    businessName.isNotBlank() -> Text(
                        initials(businessName),
                        style = MaterialTheme.typography.labelLarge,
                        modifier = Modifier.semantics { contentDescription = businessName },
                    )
                    else -> Icon(
                        Icons.Outlined.Person,
                        contentDescription = stringResource(R.string.home_profile),
                        modifier = Modifier.size(22.dp),
                    )
                }
            }
        }
    }
}

@Composable
private fun CategoryRow(ui: HomeUi, onSelect: (Long?) -> Unit) {
    LazyRow(
        contentPadding = PaddingValues(horizontal = Spacing.lg),
        horizontalArrangement = Arrangement.spacedBy(Spacing.sm),
        modifier = Modifier.fillMaxWidth().coachTarget(CoachId.HOME_CATEGORIES).padding(bottom = Spacing.sm),
    ) {
        item(key = "all") {
            CategoryChip(stringResource(R.string.home_all), ui.selectedCategory == null) { onSelect(null) }
        }
        items(ui.categories, key = { it.id }) { c ->
            CategoryChip(c.nama, ui.selectedCategory == c.id) { onSelect(c.id) }
        }
    }
}

@Composable
private fun CategoryChip(label: String, selected: Boolean, onClick: () -> Unit) {
    val scheme = MaterialTheme.colorScheme
    FilterChip(
        selected = selected,
        onClick = onClick,
        label = { Text(label, style = MaterialTheme.typography.labelLarge, maxLines = 1) },
        shape = RoundedCornerShape(50),
        modifier = Modifier.heightIn(min = 40.dp),
        colors = FilterChipDefaults.filterChipColors(
            containerColor = scheme.surfaceContainerHigh,
            labelColor = scheme.onSurface,
            selectedContainerColor = scheme.primary,
            selectedLabelColor = scheme.onPrimary,
        ),
        border = FilterChipDefaults.filterChipBorder(
            enabled = true,
            selected = selected,
            borderColor = Color.Transparent,
            selectedBorderColor = Color.Transparent,
        ),
    )
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
    val bottomPad = if (showCartBar) 104.dp else Spacing.xl
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
    val container = when {
        inCart -> scheme.primaryContainer.copy(alpha = if (dark) 0.35f else 0.6f)
        dark -> scheme.surfaceContainerLow
        else -> scheme.surfaceContainerLowest
    }
    val border = when {
        inCart -> BorderStroke(2.dp, scheme.primary)
        dark -> null
        else -> BorderStroke(1.dp, scheme.outlineVariant.copy(alpha = 0.7f))
    }
    Surface(
        modifier = modifier
            .fillMaxWidth()
            .semantics {
                contentDescription = description
                role = Role.Button
            }
            .combinedClickable(enabled = !blocked, onClick = onAdd, onLongClick = onNote),
        shape = MaterialTheme.shapes.medium,
        color = container,
        border = border,
    ) {
        Column(Modifier.padding(Spacing.sm).alpha(if (blocked) 0.55f else 1f)) {
            Box(
                Modifier
                    .fillMaxWidth()
                    .aspectRatio(1.25f)
                    .clip(MaterialTheme.shapes.small),
            ) {
                MenuImage(photo = item.menu.fotoPath, name = item.menu.nama, modifier = Modifier.fillMaxSize())
                Row(Modifier.align(Alignment.TopStart).padding(Spacing.sm)) {
                    when {
                        blocked -> StatusPill(stringResource(R.string.home_sold_out), Tone.BAD)
                        item.soldOut -> StatusPill(stringResource(R.string.home_stock_short), Tone.WARN)
                        item.portions != null && item.portions <= 3 ->
                            StatusPill(stringResource(R.string.home_portions_left, item.portions), Tone.WARN)
                    }
                }
            }
            Text(
                item.menu.nama,
                style = MaterialTheme.typography.titleSmall,
                maxLines = 2,
                overflow = TextOverflow.Ellipsis,
                minLines = 2,
                modifier = Modifier.padding(start = Spacing.xs, end = Spacing.xs, top = Spacing.sm),
            )
            BoxWithConstraints(Modifier.fillMaxWidth()) {
                // Harga dan stepper tidak muat sebaris di kartu sempit (lanskap) atau huruf sangat besar: susun ke bawah.
                val stacked = inCart && !blocked && (LocalDensity.current.fontScale > 1.3f || maxWidth < 180.dp)
                val price: @Composable (Modifier) -> Unit = { m ->
                    MoneyText(
                        item.menu.hargaJual,
                        style = MaterialTheme.typography.titleSmall,
                        color = scheme.primary,
                        modifier = m,
                        shrinkToFit = true,
                    )
                }
                val action: @Composable () -> Unit = {
                    if (inCart) {
                        QuantityStepper(quantity = quantity, onMinus = onDecrease, onPlus = onAdd, minusIsDelete = quantity == 1, compact = true)
                    } else {
                        FilledTonalIconButton(
                            onClick = onAdd,
                            modifier = Modifier.size(36.dp),
                            colors = IconButtonDefaults.filledTonalIconButtonColors(
                                containerColor = scheme.primaryContainer,
                                contentColor = scheme.onPrimaryContainer,
                            ),
                        ) {
                            Icon(Icons.Outlined.Add, contentDescription = stringResource(R.string.cart_increase), modifier = Modifier.size(20.dp))
                        }
                    }
                }
                if (stacked) {
                    // Huruf sangat besar: harga dan stepper tidak muat sebaris, susun ke bawah.
                    Column(Modifier.fillMaxWidth().padding(start = Spacing.xs, bottom = Spacing.xs), verticalArrangement = Arrangement.spacedBy(Spacing.xs)) {
                        price(Modifier)
                        Box(Modifier.fillMaxWidth(), contentAlignment = Alignment.CenterEnd) { action() }
                    }
                } else {
                    Row(
                        Modifier.fillMaxWidth().heightIn(min = 44.dp).padding(start = Spacing.xs),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(Spacing.xs),
                    ) {
                        price(Modifier.weight(1f))
                        if (!blocked) action()
                    }
                }
            }
        }
    }
}

@Composable
private fun CartBar(cart: CartUi, onClick: () -> Unit) {
    val scheme = MaterialTheme.colorScheme
    val fmt = LocalFmt.current
    val description = pluralStringResource(R.plurals.cart_items, cart.count, cart.count) + ", " + fmt.money(cart.total)
    Surface(
        onClick = onClick,
        modifier = Modifier
            .navigationBarsPadding()
            .padding(horizontal = Spacing.lg, vertical = Spacing.md)
            .fillMaxWidth()
            .heightIn(min = 64.dp)
            .semantics { contentDescription = description },
        shape = MaterialTheme.shapes.large,
        color = scheme.primary,
        contentColor = scheme.onPrimary,
        shadowElevation = 8.dp,
    ) {
        Row(
            Modifier.padding(horizontal = Spacing.lg),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Box(
                Modifier.size(36.dp).clip(CircleShape).background(scheme.onPrimary.copy(alpha = 0.16f)),
                contentAlignment = Alignment.Center,
            ) {
                Icon(Icons.Outlined.ShoppingBag, contentDescription = null, modifier = Modifier.size(20.dp))
            }
            Spacer(Modifier.width(Spacing.md))
            Column(Modifier.weight(1f)) {
                Text(
                    pluralStringResource(R.plurals.cart_items, cart.count, cart.count),
                    style = MaterialTheme.typography.labelMedium,
                    color = scheme.onPrimary.copy(alpha = 0.85f),
                )
                Text(fmt.money(cart.total), style = MaterialTheme.typography.titleMedium.merge(MoneyStyle))
            }
            Text(stringResource(R.string.cart_view), style = MaterialTheme.typography.labelLarge)
            Icon(Icons.Outlined.ExpandLess, contentDescription = null)
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
                    modifier = Modifier.fillMaxWidth().heightIn(min = Spacing.minTouch),
                    shape = MaterialTheme.shapes.medium,
                ) { Text(stringResource(R.string.dummy_guide_button), style = MaterialTheme.typography.labelLarge) }
            }
        }
    }
}
