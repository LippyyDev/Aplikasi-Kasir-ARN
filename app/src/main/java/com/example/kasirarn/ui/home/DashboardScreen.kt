package com.example.kasirarn.ui.home

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.outlined.TrendingDown
import androidx.compose.material.icons.automirrored.outlined.TrendingUp
import androidx.compose.material.icons.outlined.Add
import androidx.compose.material.icons.outlined.BarChart
import androidx.compose.material.icons.outlined.Inventory2
import androidx.compose.material.icons.outlined.Notifications
import androidx.compose.material.icons.outlined.Person
import androidx.compose.material.icons.outlined.ShoppingBag
import androidx.compose.material.icons.outlined.WarningAmber
import androidx.compose.material3.Badge
import androidx.compose.material3.BadgedBox
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.StrokeJoin
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.lifecycle.ViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewModelScope
import com.example.kasirarn.AppContainer
import com.example.kasirarn.R
import com.example.kasirarn.data.db.IngredientEntity
import com.example.kasirarn.data.db.TopMenuRow
import com.example.kasirarn.data.repo.DayMath
import com.example.kasirarn.data.repo.TimeRange
import com.example.kasirarn.ui.components.ArnCard
import com.example.kasirarn.ui.components.HeroCard
import com.example.kasirarn.ui.components.MenuImage
import com.example.kasirarn.ui.components.MoneyText
import com.example.kasirarn.ui.components.TonalIconAction
import com.example.kasirarn.ui.components.initials
import com.example.kasirarn.ui.format.LocalFmt
import com.example.kasirarn.ui.ingredient.StockLevel
import com.example.kasirarn.ui.ingredient.dim
import com.example.kasirarn.ui.ingredient.level
import com.example.kasirarn.ui.nav.ManageTab
import com.example.kasirarn.ui.nav.Routes
import com.example.kasirarn.ui.nav.TopTab
import com.example.kasirarn.ui.nav.containerViewModel
import com.example.kasirarn.ui.theme.Spacing
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.flow
import kotlinx.coroutines.flow.flatMapLatest
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.stateIn
import java.text.SimpleDateFormat
import java.util.Calendar
import java.util.Date

data class DashboardUi(
    val loaded: Boolean = false,
    val businessName: String = "",
    val logo: String? = null,
    val unread: Int = 0,
    val sales: Long = 0,
    val yesterday: Long = 0,
    val txCount: Int = 0,
    val average: Long = 0,
    val netProfit: Long = 0,
    /** Penjualan per jam hari ini (indeks 0 sampai 23). */
    val hourly: List<Long> = List(24) { 0L },
    val lowStock: List<IngredientEntity> = emptyList(),
    val topMenus: List<TopMenuRow> = emptyList(),
)

/** Ringkasan hari ini untuk layar Beranda. Rentang hari dihitung ulang tiap menit supaya ikut berganti tengah malam. */
@OptIn(ExperimentalCoroutinesApi::class)
class DashboardViewModel(c: AppContainer) : ViewModel() {
    private val today = flow {
        while (true) {
            emit(DayMath.startOfDay(System.currentTimeMillis()))
            delay(60_000)
        }
    }.distinctUntilChanged()

    private val summary = today.flatMapLatest { start ->
        val range = TimeRange(start, DayMath.addDays(start, 1))
        val prev = TimeRange(DayMath.addDays(start, -1), start)
        combine(c.reports.observeIncome(range), c.reports.observeIncome(prev), c.reports.observeFinancials(range)) { now, before, fin ->
            Triple(now, before.summary.total, fin.netProfit)
        }
    }

    val ui: StateFlow<DashboardUi> = combine(
        summary,
        c.ingredients.observeIngredients(),
        c.profile.observe(),
        c.notifications.observeUnreadCount(),
    ) { (income, yesterday, profit), ingredients, profile, unread ->
        val hourly = MutableList(24) { 0L }
        income.trend.forEach { if (it.bucket in 0..23) hourly[it.bucket.toInt()] = it.total }
        DashboardUi(
            loaded = true,
            businessName = profile?.nama.orEmpty(),
            logo = profile?.logoPath,
            unread = unread,
            sales = income.summary.total,
            yesterday = yesterday,
            txCount = income.summary.txCount,
            average = income.average,
            netProfit = profit,
            hourly = hourly,
            lowStock = ingredients.filter { it.level() != StockLevel.OK }.sortedBy { it.stokDasar },
            topMenus = income.topMenus.take(4),
        )
    }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), DashboardUi())
}

@Composable
fun DashboardScreen(onNavigate: (String) -> Unit, onTab: (TopTab) -> Unit) {
    val vm = containerViewModel { DashboardViewModel(it) }
    val ui by vm.ui.collectAsStateWithLifecycle()
    val scheme = MaterialTheme.colorScheme
    LazyColumn(
        Modifier.fillMaxSize().background(scheme.background).statusBarsPadding(),
        contentPadding = PaddingValues(start = Spacing.lg, end = Spacing.lg, top = Spacing.sm, bottom = Spacing.xl),
        verticalArrangement = Arrangement.spacedBy(Spacing.lg),
    ) {
        item { DashboardHeader(ui, onProfile = { onNavigate(Routes.PROFILE) }, onNotifications = { onNavigate(Routes.NOTIFICATIONS) }) }
        item { SalesHero(ui) }
        item {
            QuickActions(
                onSell = { onTab(TopTab.CASHIER) },
                onAddMenu = { onNavigate(Routes.menuEdit()) },
                onStock = { onNavigate(Routes.manage(ManageTab.INGREDIENT)) },
                onReports = { onNavigate(Routes.reports()) },
            )
        }
        item {
            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(Spacing.md)) {
                LowStockCard(ui.lowStock, Modifier.weight(1f)) { onNavigate(Routes.manage(ManageTab.INGREDIENT)) }
                PeakHoursCard(ui.hourly, Modifier.weight(1f))
            }
        }
        item {
            Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
                Text(
                    stringResource(R.string.dash_top_today),
                    style = MaterialTheme.typography.titleMedium.copy(fontWeight = FontWeight.Bold),
                    modifier = Modifier.weight(1f),
                )
                Text(
                    stringResource(R.string.dash_see_all),
                    style = MaterialTheme.typography.labelMedium.copy(fontWeight = FontWeight.Bold),
                    color = scheme.primary,
                    modifier = Modifier.clip(RoundedCornerShape(8.dp)).clickable { onNavigate(Routes.reports()) }.padding(vertical = 6.dp, horizontal = 4.dp),
                )
            }
        }
        item { com.example.kasirarn.ui.components.TopMenuList(ui.topMenus, short = true, emptyText = stringResource(R.string.dash_top_empty)) }
    }
}

private fun greetingRes(): Int = when (Calendar.getInstance().get(Calendar.HOUR_OF_DAY)) {
    in 4..10 -> R.string.dash_greeting_morning
    in 11..14 -> R.string.dash_greeting_noon
    in 15..17 -> R.string.dash_greeting_afternoon
    else -> R.string.dash_greeting_evening
}

@Composable
private fun DashboardHeader(ui: DashboardUi, onProfile: () -> Unit, onNotifications: () -> Unit) {
    val scheme = MaterialTheme.colorScheme
    Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(Spacing.md)) {
        Surface(onClick = onProfile, modifier = Modifier.size(46.dp), shape = CircleShape, color = scheme.primaryContainer, contentColor = scheme.onPrimaryContainer) {
            Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                when {
                    ui.logo != null -> MenuImage(photo = ui.logo, name = ui.businessName, modifier = Modifier.fillMaxSize(), shape = CircleShape)
                    ui.businessName.isNotBlank() -> Text(
                        initials(ui.businessName),
                        style = MaterialTheme.typography.titleSmall.copy(fontWeight = FontWeight.ExtraBold),
                        modifier = Modifier.semantics { contentDescription = ui.businessName },
                    )
                    else -> Icon(Icons.Outlined.Person, contentDescription = stringResource(R.string.home_profile), modifier = Modifier.size(22.dp))
                }
            }
        }
        Column(Modifier.weight(1f)) {
            Text(stringResource(greetingRes()), style = MaterialTheme.typography.bodySmall, color = scheme.onSurfaceVariant)
            Text(
                ui.businessName.ifBlank { stringResource(R.string.more_business_unnamed) },
                style = MaterialTheme.typography.titleLarge.copy(fontWeight = FontWeight.ExtraBold),
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
        }
        TonalIconAction(onClick = onNotifications) {
            BadgedBox(badge = { if (ui.unread > 0) Badge() }) {
                Icon(Icons.Outlined.Notifications, contentDescription = stringResource(R.string.drawer_notifications))
            }
        }
    }
}

@Composable
private fun SalesHero(ui: DashboardUi) {
    val fmt = LocalFmt.current
    val onHero = com.example.kasirarn.ui.components.heroColors().second
    HeroCard(Modifier.fillMaxWidth()) {
        Column(verticalArrangement = Arrangement.spacedBy(Spacing.md)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text(
                    stringResource(R.string.dash_sales_today),
                    style = MaterialTheme.typography.labelLarge,
                    color = onHero.copy(alpha = 0.85f),
                    modifier = Modifier.weight(1f),
                )
                Text(
                    SimpleDateFormat("EEE, d MMM", fmt.locale).format(Date()),
                    style = MaterialTheme.typography.labelMedium,
                    modifier = Modifier.clip(RoundedCornerShape(50)).background(onHero.copy(alpha = 0.16f)).padding(horizontal = 10.dp, vertical = 4.dp),
                )
            }
            MoneyText(ui.sales, style = MaterialTheme.typography.displayMedium, color = onHero, fontWeight = FontWeight.ExtraBold, shrinkToFit = true)
            if (ui.yesterday > 0 && ui.sales > 0) {
                val pct = ((ui.sales - ui.yesterday) * 100 / ui.yesterday).toInt()
                Row(
                    Modifier.clip(RoundedCornerShape(50)).background(onHero.copy(alpha = 0.18f)).padding(horizontal = 10.dp, vertical = 5.dp),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(5.dp),
                ) {
                    Icon(if (pct >= 0) Icons.AutoMirrored.Outlined.TrendingUp else Icons.AutoMirrored.Outlined.TrendingDown, contentDescription = null, modifier = Modifier.size(14.dp))
                    Text(stringResource(R.string.dash_vs_yesterday, pct), style = MaterialTheme.typography.labelMedium)
                }
            }
            Sparkline(ui.hourly, onHero, Modifier.fillMaxWidth().height(64.dp))
            Box(Modifier.fillMaxWidth().height(1.dp).background(onHero.copy(alpha = 0.2f)))
            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(Spacing.sm)) {
                HeroStat(ui.txCount.toString(), stringResource(R.string.dash_transactions), Modifier.weight(1f))
                HeroStat(fmt.moneyShort(ui.average), stringResource(R.string.dash_average), Modifier.weight(1f))
                HeroStat(fmt.moneyShort(ui.netProfit), stringResource(R.string.dash_net_profit), Modifier.weight(1f))
            }
        }
    }
}

@Composable
private fun HeroStat(value: String, label: String, modifier: Modifier) {
    Column(modifier) {
        Text(value, style = MaterialTheme.typography.titleMedium.copy(fontWeight = FontWeight.ExtraBold), maxLines = 1)
        Text(label, style = MaterialTheme.typography.labelSmall, color = com.example.kasirarn.ui.components.heroColors().second.copy(alpha = 0.78f), maxLines = 1)
    }
}

/** Garis dan area halus: penjualan kumulatif hari ini per jam sampai jam sekarang. */
@Composable
private fun Sparkline(hourly: List<Long>, color: Color, modifier: Modifier) {
    val nowHour = Calendar.getInstance().get(Calendar.HOUR_OF_DAY)
    val first = hourly.indexOfFirst { it > 0 }.let { if (it < 0) 0 else it }
    val last = maxOf(nowHour, first + 1).coerceAtMost(23)
    val points = hourly.subList(first, last + 1).runningFold(0L) { acc, v -> acc + v }
    Canvas(modifier.semantics { contentDescription = "" }) {
        val max = (points.maxOrNull() ?: 0L).coerceAtLeast(1L).toFloat()
        val w = size.width
        val h = size.height - 6.dp.toPx()
        val n = points.size.coerceAtLeast(2)
        fun x(i: Int) = w * i / (n - 1)
        fun y(i: Int) = 3.dp.toPx() + h - h * (points.getOrElse(i) { points.lastOrNull() ?: 0L } / max) * 0.92f
        val line = Path().apply {
            moveTo(x(0), y(0))
            for (i in 1 until n) {
                val mx = (x(i - 1) + x(i)) / 2
                cubicTo(mx, y(i - 1), mx, y(i), x(i), y(i))
            }
        }
        val area = Path().apply {
            addPath(line)
            lineTo(w, size.height)
            lineTo(0f, size.height)
            close()
        }
        drawPath(area, Brush.verticalGradient(listOf(color.copy(alpha = 0.28f), color.copy(alpha = 0.04f))))
        drawPath(line, color, style = Stroke(3.dp.toPx(), cap = StrokeCap.Round, join = StrokeJoin.Round))
    }
}

@Composable
private fun QuickActions(onSell: () -> Unit, onAddMenu: () -> Unit, onStock: () -> Unit, onReports: () -> Unit) {
    val s = MaterialTheme.colorScheme
    Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(Spacing.sm)) {
        QuickAction(Icons.Outlined.ShoppingBag, R.string.dash_quick_sell, s.primaryContainer, s.onPrimaryContainer, onSell, Modifier.weight(1f))
        QuickAction(Icons.Outlined.Add, R.string.dash_quick_menu, s.secondaryContainer, s.onSecondaryContainer, onAddMenu, Modifier.weight(1f))
        QuickAction(Icons.Outlined.Inventory2, R.string.dash_quick_stock, s.tertiaryContainer, s.onTertiaryContainer, onStock, Modifier.weight(1f))
        QuickAction(Icons.Outlined.BarChart, R.string.dash_quick_reports, s.surfaceContainerHigh, s.onSurface, onReports, Modifier.weight(1f))
    }
}

@Composable
private fun QuickAction(icon: ImageVector, label: Int, container: Color, content: Color, onClick: () -> Unit, modifier: Modifier) {
    Column(modifier, horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.spacedBy(Spacing.sm)) {
        Surface(onClick = onClick, modifier = Modifier.fillMaxWidth().height(64.dp), shape = RoundedCornerShape(22.dp), color = container, contentColor = content) {
            Box(contentAlignment = Alignment.Center) { Icon(icon, contentDescription = null, modifier = Modifier.size(26.dp)) }
        }
        Text(
            stringResource(label),
            style = MaterialTheme.typography.labelMedium.copy(fontWeight = FontWeight.Bold),
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
        )
    }
}

@Composable
private fun LowStockCard(low: List<IngredientEntity>, modifier: Modifier, onClick: () -> Unit) {
    val s = MaterialTheme.colorScheme
    val fmt = LocalFmt.current
    Surface(onClick = onClick, modifier = modifier.height(152.dp), shape = RoundedCornerShape(24.dp), color = s.tertiaryContainer, contentColor = s.onTertiaryContainer) {
        Column(Modifier.padding(Spacing.lg), verticalArrangement = Arrangement.spacedBy(Spacing.sm)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text(stringResource(R.string.dash_low_stock), style = MaterialTheme.typography.labelLarge.copy(fontWeight = FontWeight.Bold), modifier = Modifier.weight(1f))
                Box(Modifier.size(30.dp).clip(CircleShape).background(s.tertiary), contentAlignment = Alignment.Center) {
                    Icon(Icons.Outlined.WarningAmber, contentDescription = null, tint = s.onTertiary, modifier = Modifier.size(18.dp))
                }
            }
            if (low.isEmpty()) {
                Text(stringResource(R.string.dash_stock_ok), style = MaterialTheme.typography.headlineSmall.copy(fontWeight = FontWeight.ExtraBold))
                Text(stringResource(R.string.dash_stock_ok_body), style = MaterialTheme.typography.bodySmall, maxLines = 2)
            } else {
                Text(
                    stringResource(R.string.dash_low_count, low.size),
                    style = MaterialTheme.typography.headlineSmall.copy(fontWeight = FontWeight.ExtraBold),
                )
                low.take(2).forEach {
                    Text(
                        stringResource(R.string.dash_stock_left, it.nama, fmt.quantity(it.stokDasar.coerceAtLeast(0), it.dim())),
                        style = MaterialTheme.typography.labelSmall.copy(fontWeight = FontWeight.Medium),
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                    )
                }
            }
        }
    }
}

@Composable
private fun PeakHoursCard(hourly: List<Long>, modifier: Modifier) {
    val s = MaterialTheme.colorScheme
    val total = hourly.sum()
    // Jendela dua jam dengan penjualan terbesar.
    val peakStart = (0..22).maxByOrNull { hourly[it] + hourly[it + 1] } ?: 0
    val windowStart = (peakStart - 2).coerceIn(0, 17)
    val bars = hourly.subList(windowStart, windowStart + 7)
    val maxBar = (bars.maxOrNull() ?: 0L).coerceAtLeast(1L)
    Surface(modifier = modifier.height(152.dp), shape = RoundedCornerShape(24.dp), color = s.secondaryContainer, contentColor = s.onSecondaryContainer) {
        Column(Modifier.padding(Spacing.lg), verticalArrangement = Arrangement.spacedBy(Spacing.sm)) {
            Text(stringResource(R.string.dash_peak), style = MaterialTheme.typography.labelLarge.copy(fontWeight = FontWeight.Bold))
            Text(
                if (total > 0) "%02d.00–%02d.00".format(peakStart, peakStart + 2) else "–",
                style = MaterialTheme.typography.titleLarge.copy(fontWeight = FontWeight.ExtraBold),
                maxLines = 1,
            )
            Row(Modifier.fillMaxWidth().weight(1f), horizontalArrangement = Arrangement.spacedBy(6.dp), verticalAlignment = Alignment.Bottom) {
                bars.forEachIndexed { i, v ->
                    val hour = windowStart + i
                    val inPeak = total > 0 && (hour == peakStart || hour == peakStart + 1)
                    val fraction = if (total > 0) (v.toFloat() / maxBar).coerceIn(0.12f, 1f) else 0.12f
                    Box(
                        Modifier
                            .weight(1f)
                            .fillMaxHeight(fraction)
                            .clip(RoundedCornerShape(5.dp))
                            .background(if (inPeak) s.secondary else s.secondary.copy(alpha = 0.35f)),
                    )
                }
            }
        }
    }
}
