package com.example.kasirarn.ui.nav

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.foundation.Image
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.WindowInsetsSides
import androidx.compose.foundation.layout.consumeWindowInsets
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.only
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.safeDrawing
import androidx.compose.foundation.layout.size
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.BarChart
import androidx.compose.material.icons.filled.Inventory2
import androidx.compose.material.icons.filled.PointOfSale
import androidx.compose.material.icons.outlined.BarChart
import androidx.compose.material.icons.outlined.Inventory2
import androidx.compose.material.icons.outlined.MoreHoriz
import androidx.compose.material.icons.outlined.PointOfSale
import androidx.compose.material3.Badge
import androidx.compose.material3.BadgedBox
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.NavigationBar
import androidx.compose.material3.NavigationBarItem
import androidx.compose.material3.NavigationBarItemDefaults
import androidx.compose.material3.NavigationRail
import androidx.compose.material3.NavigationRailItem
import androidx.compose.material3.NavigationRailItemDefaults
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import androidx.lifecycle.ViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewModelScope
import androidx.navigation.NavController
import androidx.navigation.NavGraph.Companion.findStartDestination
import androidx.navigation.NavType
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.currentBackStackEntryAsState
import androidx.navigation.compose.rememberNavController
import androidx.navigation.navArgument
import com.example.kasirarn.AppContainer
import com.example.kasirarn.R
import com.example.kasirarn.ui.backup.BackupScreen
import com.example.kasirarn.ui.home.HomeScreen
import com.example.kasirarn.ui.home.PaymentScreen
import com.example.kasirarn.ui.home.SuccessScreen
import com.example.kasirarn.ui.ingredient.IngredientDetailScreen
import com.example.kasirarn.ui.ingredient.IngredientEditorScreen
import com.example.kasirarn.ui.ingredient.StockLevel
import com.example.kasirarn.ui.ingredient.level
import com.example.kasirarn.ui.menu.ManageScreen
import com.example.kasirarn.ui.menu.MenuEditorScreen
import com.example.kasirarn.ui.misc.AboutScreen
import com.example.kasirarn.ui.misc.MoreScreen
import com.example.kasirarn.ui.misc.NotificationsScreen
import com.example.kasirarn.ui.misc.ProfileScreen
import com.example.kasirarn.ui.onboarding.CoachId
import com.example.kasirarn.ui.onboarding.CoachOverlay
import com.example.kasirarn.ui.onboarding.CoachRegistry
import com.example.kasirarn.ui.onboarding.GuideListScreen
import com.example.kasirarn.ui.onboarding.GuideTopicScreen
import com.example.kasirarn.ui.onboarding.LocalCoachRegistry
import com.example.kasirarn.ui.onboarding.OnboardingStage
import com.example.kasirarn.ui.onboarding.OnboardingViewModel
import com.example.kasirarn.ui.onboarding.coachTarget
import com.example.kasirarn.ui.report.ExpenseCategoriesScreen
import com.example.kasirarn.ui.report.ReportsScreen
import com.example.kasirarn.ui.report.TransactionDetailScreen
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.stateIn

/** Empat mode kerja aplikasi. Urutannya sama di bar bawah (ponsel) dan rail (tablet, lanskap). */
enum class TopTab(
    val route: String,
    val pattern: String,
    val labelRes: Int,
    val icon: ImageVector,
    val iconSelected: ImageVector,
) {
    CASHIER(Routes.HOME, Routes.HOME, R.string.nav_cashier, Icons.Outlined.PointOfSale, Icons.Filled.PointOfSale),
    MANAGE(Routes.manage(), Routes.MANAGE, R.string.nav_manage, Icons.Outlined.Inventory2, Icons.Filled.Inventory2),
    REPORTS(Routes.reports(), Routes.REPORTS, R.string.nav_reports, Icons.Outlined.BarChart, Icons.Filled.BarChart),
    MORE(Routes.MORE, Routes.MORE, R.string.nav_more, Icons.Outlined.MoreHoriz, Icons.Outlined.MoreHoriz),
}

private fun TopTab.coachId() = when (this) {
    TopTab.CASHIER -> CoachId.TAB_CASHIER
    TopTab.MANAGE -> CoachId.TAB_MANAGE
    TopTab.REPORTS -> CoachId.TAB_REPORTS
    TopTab.MORE -> CoachId.TAB_MORE
}

/** Penanda kecil di tab: bahan yang perlu perhatian di Kelola, notifikasi belum dibaca di Lainnya. */
data class TabBadges(val manage: Boolean = false, val more: Boolean = false) {
    fun of(tab: TopTab) = when (tab) {
        TopTab.MANAGE -> manage
        TopTab.MORE -> more
        else -> false
    }
}

class ShellViewModel(c: AppContainer) : ViewModel() {
    val badges: StateFlow<TabBadges> = combine(
        c.ingredients.observeIngredients().map { list -> list.any { it.level() != StockLevel.OK } },
        c.notifications.observeUnreadCount(),
    ) { stockAlert, unread -> TabBadges(manage = stockAlert, more = unread > 0) }
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), TabBadges())
}

private val optionalId = listOf(
    navArgument("id") {
        type = NavType.LongType
        defaultValue = -1L
    },
)
private val requiredId = listOf(navArgument("id") { type = NavType.LongType })
private val tabArg = listOf(
    navArgument("tab") {
        type = NavType.IntType
        defaultValue = 0
    },
)

/** [fresh] membuka tab dari layar utamanya, tanpa memulihkan layar turunan yang terakhir dibuka di tab itu. */
private fun NavController.goToTab(tab: TopTab, fresh: Boolean = false) {
    navigate(tab.route) {
        popUpTo(graph.findStartDestination().id) { saveState = true }
        launchSingleTop = true
        restoreState = !fresh
    }
}

@Composable
fun ArnNavHost() {
    val nav = rememberNavController()
    val entry by nav.currentBackStackEntryAsState()
    val route = entry?.destination?.route
    val current = TopTab.entries.firstOrNull { it.pattern == route }
    val window = rememberWindowInfo()
    val shell = containerViewModel { ShellViewModel(it) }
    val badges by shell.badges.collectAsStateWithLifecycle()
    val onTab: (TopTab) -> Unit = { nav.goToTab(it) }

    val onboarding = containerViewModel { OnboardingViewModel(it) }
    val stage by onboarding.stage.collectAsStateWithLifecycle()
    val coach = remember { CoachRegistry() }
    val touring = stage == OnboardingStage.TOUR
    // Tutorial selalu mulai dari mode Kasir, apa pun layar yang sedang terbuka.
    LaunchedEffect(touring) { if (touring && current != TopTab.CASHIER) nav.goToTab(TopTab.CASHIER) }

    CompositionLocalProvider(LocalCoachRegistry provides coach) {
        Box(Modifier.fillMaxSize()) {
            if (window.isWide) {
                Row(Modifier.padding(0.dp)) {
                    AnimatedVisibility(visible = current != null) {
                        ArnRail(current, badges, onTab)
                    }
                    Box(
                        Modifier
                            .weight(1f)
                            .consumeWindowInsets(WindowInsets.safeDrawing.only(WindowInsetsSides.Start)),
                    ) { ArnGraph(nav) }
                }
            } else {
                Scaffold(
                    containerColor = MaterialTheme.colorScheme.background,
                    contentWindowInsets = WindowInsets(0),
                    bottomBar = {
                        AnimatedVisibility(visible = current != null) { ArnBottomBar(current, badges, onTab) }
                    },
                ) { padding ->
                    Box(Modifier.padding(padding).consumeWindowInsets(padding)) { ArnGraph(nav) }
                }
            }
            if (touring && (current == TopTab.CASHIER || current == TopTab.MORE)) {
                CoachOverlay(coach, current, onNavigate = { nav.goToTab(it, fresh = true) }, onFinish = { onboarding.setStage(OnboardingStage.DONE) })
            }
        }
    }
}

/** Label navigasi tidak boleh melebar melewati lebar tab; ukuran huruf sistem dibatasi 1,3x khusus di bilah ini. */
@Composable
private fun CappedFontScale(content: @Composable () -> Unit) {
    val density = androidx.compose.ui.platform.LocalDensity.current
    val capped = androidx.compose.ui.unit.Density(density.density, density.fontScale.coerceAtMost(1.3f))
    androidx.compose.runtime.CompositionLocalProvider(androidx.compose.ui.platform.LocalDensity provides capped, content = content)
}

@Composable
private fun ArnBottomBar(current: TopTab?, badges: TabBadges, onTab: (TopTab) -> Unit) = CappedFontScale {
    NavigationBar(containerColor = MaterialTheme.colorScheme.surfaceContainer) {
        TopTab.entries.forEach { tab ->
            val selected = tab == current
            NavigationBarItem(
                modifier = Modifier.coachTarget(tab.coachId()),
                selected = selected,
                onClick = { onTab(tab) },
                icon = { TabIcon(tab, selected, badges.of(tab)) },
                label = { Text(stringResource(tab.labelRes), style = MaterialTheme.typography.labelMedium) },
                colors = NavigationBarItemDefaults.colors(
                    indicatorColor = MaterialTheme.colorScheme.primaryContainer,
                    selectedIconColor = MaterialTheme.colorScheme.onPrimaryContainer,
                    selectedTextColor = MaterialTheme.colorScheme.onSurface,
                ),
            )
        }
    }
}

@Composable
private fun ArnRail(current: TopTab?, badges: TabBadges, onTab: (TopTab) -> Unit) = CappedFontScale {
    NavigationRail(
        containerColor = MaterialTheme.colorScheme.surfaceContainer,
        header = {
            Spacer(Modifier.height(8.dp))
            Image(painterResource(R.drawable.ic_logo_mark), contentDescription = null, modifier = Modifier.size(40.dp))
            Spacer(Modifier.height(8.dp))
        },
    ) {
        Column(verticalArrangement = androidx.compose.foundation.layout.Arrangement.spacedBy(4.dp)) {
            TopTab.entries.forEach { tab ->
                val selected = tab == current
                NavigationRailItem(
                    modifier = Modifier.coachTarget(tab.coachId()),
                    selected = selected,
                    onClick = { onTab(tab) },
                    icon = { TabIcon(tab, selected, badges.of(tab)) },
                    label = { Text(stringResource(tab.labelRes), style = MaterialTheme.typography.labelMedium) },
                    colors = NavigationRailItemDefaults.colors(
                        indicatorColor = MaterialTheme.colorScheme.primaryContainer,
                        selectedIconColor = MaterialTheme.colorScheme.onPrimaryContainer,
                        selectedTextColor = MaterialTheme.colorScheme.onSurface,
                    ),
                )
            }
        }
    }
}

@Composable
private fun TabIcon(tab: TopTab, selected: Boolean, badge: Boolean) {
    BadgedBox(badge = { if (badge) Badge() }) {
        Icon(if (selected) tab.iconSelected else tab.icon, contentDescription = null)
    }
}

@Composable
private fun ArnGraph(nav: androidx.navigation.NavHostController) {
    val back: () -> Unit = { nav.popBackStack() }
    val navigate: (String) -> Unit = { nav.navigate(it) }

    NavHost(navController = nav, startDestination = Routes.HOME) {
        // ---- empat mode utama ----
        composable(Routes.HOME) { HomeScreen(onNavigate = navigate) }
        composable(Routes.MANAGE, tabArg) { entry ->
            ManageScreen(
                initialTab = entry.arguments?.getInt("tab") ?: 0,
                onEditMenu = { nav.navigate(Routes.menuEdit(it)) },
                onOpenIngredient = { nav.navigate(Routes.ingredient(it)) },
                onAddIngredient = { nav.navigate(Routes.ingredientEdit()) },
            )
        }
        composable(Routes.REPORTS, tabArg) { entry ->
            ReportsScreen(
                initialTab = entry.arguments?.getInt("tab") ?: 0,
                onOpenTransaction = { nav.navigate(Routes.transaction(it)) },
                onManageCategories = { nav.navigate(Routes.EXPENSE_CATEGORIES) },
            )
        }
        composable(Routes.MORE) { MoreScreen(onNavigate = navigate) }

        // ---- kasir: pembayaran ----
        composable(Routes.PAYMENT) {
            ConstrainedScreen(maxWidth = 960.dp) {
                PaymentScreen(
                    onBack = back,
                    onPaid = { id -> nav.navigate(Routes.success(id)) { popUpTo(Routes.HOME) } },
                )
            }
        }
        composable(Routes.SUCCESS, requiredId) { entry ->
            ConstrainedScreen {
                SuccessScreen(
                    id = entry.arguments?.getLong("id") ?: -1L,
                    onNewTransaction = { nav.popBackStack(Routes.HOME, inclusive = false) },
                )
            }
        }

        // ---- layar turunan ----
        composable(Routes.PROFILE) { ConstrainedScreen { ProfileScreen(onBack = back) } }
        composable(Routes.NOTIFICATIONS) { ConstrainedScreen { NotificationsScreen(onBack = back) } }
        composable(Routes.ABOUT) { ConstrainedScreen { AboutScreen(onBack = back) } }
        composable(Routes.GUIDE) {
            ConstrainedScreen(maxWidth = 640.dp) {
                GuideListScreen(
                    onBack = back,
                    onOpenTopic = { nav.navigate(Routes.guideTopic(it)) },
                    onStartTour = { nav.goToTab(TopTab.CASHIER) },
                )
            }
        }
        composable(Routes.GUIDE_TOPIC, listOf(navArgument("topic") { type = NavType.StringType })) { entry ->
            ConstrainedScreen(maxWidth = 640.dp) {
                GuideTopicScreen(
                    topicId = entry.arguments?.getString("topic").orEmpty(),
                    onBack = back,
                    onTry = { route ->
                        val tab = TopTab.entries.firstOrNull { it.route == route }
                        if (tab != null) nav.goToTab(tab) else nav.navigate(route)
                    },
                )
            }
        }
        composable(Routes.BACKUP) { ConstrainedScreen { BackupScreen(onBack = back) } }
        composable(Routes.EXPENSE_CATEGORIES) { ConstrainedScreen { ExpenseCategoriesScreen(onBack = back) } }

        composable(Routes.MENU_EDIT, optionalId) { entry ->
            val id = entry.arguments?.getLong("id")?.takeIf { it >= 0 }
            ConstrainedScreen {
                MenuEditorScreen(id = id, onBack = back, onAddIngredient = { nav.navigate(Routes.ingredientEdit()) })
            }
        }
        composable(Routes.INGREDIENT_EDIT, optionalId) { entry ->
            val id = entry.arguments?.getLong("id")?.takeIf { it >= 0 }
            ConstrainedScreen { IngredientEditorScreen(id = id, onBack = back) }
        }
        composable(Routes.INGREDIENT_DETAIL, requiredId) { entry ->
            val id = entry.arguments?.getLong("id") ?: -1L
            ConstrainedScreen {
                IngredientDetailScreen(id = id, onBack = back, onEdit = { nav.navigate(Routes.ingredientEdit(it)) })
            }
        }
        composable(Routes.TX_DETAIL, requiredId) { entry ->
            ConstrainedScreen { TransactionDetailScreen(id = entry.arguments?.getLong("id") ?: -1L, onBack = back) }
        }
    }
}
