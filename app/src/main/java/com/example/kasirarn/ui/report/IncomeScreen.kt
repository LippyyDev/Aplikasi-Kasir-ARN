package com.example.kasirarn.ui.report

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.GridItemSpan
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.Payments
import androidx.compose.foundation.background
import androidx.compose.ui.draw.clip
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.pluralStringResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import kotlinx.coroutines.CoroutineScope
import com.example.kasirarn.ui.nav.rememberWindowInfo
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewModelScope
import com.example.kasirarn.AppContainer
import com.example.kasirarn.R
import com.example.kasirarn.data.db.PayMethod
import com.example.kasirarn.data.db.TransactionEntity
import com.example.kasirarn.data.db.TxStatus
import com.example.kasirarn.data.repo.IncomeReport
import com.example.kasirarn.data.repo.ReceiptNumber
import com.example.kasirarn.ui.components.ArnCard
import com.example.kasirarn.ui.components.ArnTopBar
import com.example.kasirarn.ui.components.DonutWithLegend
import com.example.kasirarn.ui.components.EmptyState
import com.example.kasirarn.ui.components.MoneyText
import com.example.kasirarn.ui.components.PeriodSelector
import com.example.kasirarn.ui.components.RankRow
import com.example.kasirarn.ui.components.RankedBars
import com.example.kasirarn.ui.components.SectionLabel
import com.example.kasirarn.ui.components.Slice
import com.example.kasirarn.ui.components.StatusPill
import com.example.kasirarn.ui.components.Tone
import com.example.kasirarn.ui.components.TrendBarChart
import com.example.kasirarn.ui.format.LocalFmt
import com.example.kasirarn.ui.nav.containerViewModel
import com.example.kasirarn.ui.onboarding.CoachId
import com.example.kasirarn.ui.onboarding.coachTarget
import com.example.kasirarn.ui.theme.Spacing
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.flatMapLatest
import kotlinx.coroutines.flow.stateIn

private const val PAGE = 30

data class IncomeUi(
    val period: PeriodUi? = null,
    val report: IncomeReport? = null,
    val transactions: List<TransactionEntity> = emptyList(),
    val canLoadMore: Boolean = false,
)

/** Keadaan laporan pemasukan. Periode dipakai bersama dengan tab Pengeluaran lewat [PeriodState]. */
@OptIn(ExperimentalCoroutinesApi::class)
class IncomeState(c: AppContainer, scope: CoroutineScope, periodState: PeriodState) {
    private val limit = MutableStateFlow(PAGE)

    val ui: StateFlow<IncomeUi> = periodState.state.flatMapLatest { p ->
        limit.value = PAGE
        combine(
            c.reports.observeIncome(p.range),
            limit.flatMapLatest { c.reports.observeTransactions(p.range, it) },
            limit,
        ) { report, txs, lim -> IncomeUi(p, report, txs, canLoadMore = txs.size >= lim) }
    }.stateIn(scope, SharingStarted.WhileSubscribed(5_000), IncomeUi())

    fun loadMore() { limit.value += PAGE }
}

/** Isi tab Pemasukan. Pemilih periode ada di layar Laporan, di atas tab. */
@Composable
fun IncomeContent(ui: IncomeUi, onLoadMore: () -> Unit, onOpenTransaction: (Long) -> Unit) {
    val fmt = LocalFmt.current
    val wide = rememberWindowInfo().isWide
    val period = ui.period
    val report = ui.report
    val full: androidx.compose.foundation.lazy.grid.LazyGridItemSpanScope.() -> GridItemSpan = { GridItemSpan(maxLineSpan) }

    LazyVerticalGrid(
        columns = GridCells.Adaptive(minSize = 320.dp),
        modifier = Modifier.fillMaxSize(),
        contentPadding = PaddingValues(start = Spacing.lg, end = Spacing.lg, top = Spacing.sm, bottom = Spacing.xxl),
        horizontalArrangement = Arrangement.spacedBy(Spacing.md),
        verticalArrangement = Arrangement.spacedBy(Spacing.md),
    ) {
        if (report == null) return@LazyVerticalGrid
        if (report.summary.txCount == 0) {
            item(span = full) {
                EmptyState(
                    icon = Icons.Outlined.Payments,
                    title = stringResource(R.string.income_empty_title),
                    body = stringResource(R.string.income_empty_body),
                )
            }
        } else {
            item(span = full) {
                val bars = buildBars(report.trend, period?.range ?: com.example.kasirarn.data.repo.TimeRange(0, 1), report.hourly, fmt.locale)
                HeroCard(report, bars, fmt.money(report.summary.total))
            }
            item(span = full) { IncomeMetrics(report, period?.range) }
            item(span = full) {
                ReportCard(stringResource(R.string.income_detail)) {
                    LineRow(stringResource(R.string.income_gross), report.summary.subtotal)
                    LineRow(stringResource(R.string.income_discount), -report.summary.discount)
                    LineRow(stringResource(R.string.income_cogs), -report.cogs)
                    LineRow(stringResource(R.string.income_gross_profit), report.grossProfit, bold = true, negativeIsError = true)
                    Spacer(Modifier.height(Spacing.xs))
                    Text(
                        stringResource(R.string.income_gross_profit_note),
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
            }
            if (report.topMenus.isNotEmpty()) {
                item(span = full) { SectionLabel(stringResource(R.string.income_top_menus)) }
                item(span = full) {
                    com.example.kasirarn.ui.components.TopMenuList(report.topMenus, short = false)
                }
            }
            if (report.byCategory.isNotEmpty()) {
                item {
                    ReportCard(stringResource(R.string.income_by_category), Modifier.fillMaxHeight()) {
                        DonutWithLegend(
                            report.byCategory.map {
                                Slice(it.label.ifBlank { stringResource(R.string.menu_no_category) }, it.amount, fmt.money(it.amount))
                            },
                        )
                    }
                }
            }
            if (report.byPayment.isNotEmpty()) {
                item {
                    ReportCard(stringResource(R.string.income_by_payment), Modifier.fillMaxHeight()) {
                        DonutWithLegend(
                            report.byPayment.map {
                                val label = if (it.label == PayMethod.CASH) stringResource(R.string.pay_cash) else stringResource(R.string.pay_non_cash)
                                Slice(label, it.amount, fmt.money(it.amount))
                            },
                        )
                    }
                }
            }
        }
        if (ui.transactions.isNotEmpty()) {
            item(span = full) { SectionLabel(stringResource(R.string.income_transactions)) }
            items(ui.transactions, key = { it.id }) { tx ->
                TransactionRow(tx) { onOpenTransaction(tx.id) }
            }
            if (ui.canLoadMore) {
                item(span = full) { TextButton(onClick = onLoadMore, modifier = Modifier.fillMaxWidth()) { Text(stringResource(R.string.action_load_more)) } }
            }
        }
    }
}

/** Warna kartu sorotan laporan: cokelat tua di tema terang, jingga tua di tema gelap. */
@Composable
fun reportHeroColors(): Triple<androidx.compose.ui.graphics.Color, androidx.compose.ui.graphics.Color, androidx.compose.ui.graphics.Color> {
    val scheme = MaterialTheme.colorScheme
    return if (com.example.kasirarn.ui.theme.isDarkScheme()) {
        Triple(scheme.primaryContainer, scheme.onPrimaryContainer, scheme.primary)
    } else {
        Triple(scheme.inverseSurface, scheme.inverseOnSurface, scheme.inversePrimary)
    }
}

@Composable
private fun HeroCard(report: IncomeReport, bars: List<com.example.kasirarn.ui.components.ChartBar>, totalText: String) {
    val (bg, fg, accent) = reportHeroColors()
    val fmt = LocalFmt.current
    com.example.kasirarn.ui.components.HeroCard(
        Modifier.fillMaxWidth().coachTarget(CoachId.REPORT_SUMMARY),
        container = bg,
        contentColor = fg,
    ) {
        Column(verticalArrangement = Arrangement.spacedBy(Spacing.md)) {
            Text(stringResource(R.string.income_net), style = MaterialTheme.typography.labelLarge, color = fg.copy(alpha = 0.7f))
            MoneyText(report.summary.total, style = MaterialTheme.typography.displaySmall, color = fg, fontWeight = androidx.compose.ui.text.font.FontWeight.ExtraBold, shrinkToFit = true)
            Text(
                pluralStringResource(R.plurals.transaction_count, report.summary.txCount, report.summary.txCount) +
                    " \u00b7 " + stringResource(R.string.income_average, fmt.money(report.average)),
                style = MaterialTheme.typography.labelMedium,
                color = MaterialTheme.colorScheme.onSecondaryContainer,
                modifier = Modifier
                    .clip(androidx.compose.foundation.shape.RoundedCornerShape(50))
                    .background(MaterialTheme.colorScheme.secondaryContainer)
                    .padding(horizontal = 10.dp, vertical = 5.dp),
            )
            com.example.kasirarn.ui.components.TrendAreaChart(
                bars = bars,
                lineColor = accent,
                labelColor = fg.copy(alpha = 0.7f),
                description = stringResource(R.string.income_trend_desc, totalText),
            )
        }
    }
}

@Composable
private fun IncomeMetrics(report: IncomeReport, range: com.example.kasirarn.data.repo.TimeRange?) {
    val s = MaterialTheme.colorScheme
    val fmt = LocalFmt.current
    val days = range?.let { ((it.to - it.from) / 86_400_000L).coerceAtLeast(1L) } ?: 1L
    val margin = if (report.summary.total > 0) (report.grossProfit * 100 / report.summary.total) else 0L
    Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(Spacing.sm)) {
        MetricTile(stringResource(R.string.income_gross_profit), fmt.moneyShort(report.grossProfit), stringResource(R.string.menu_margin, margin.toString()), s.secondaryContainer, s.onSecondaryContainer, Modifier.weight(1f))
        MetricTile(stringResource(R.string.dash_transactions), report.summary.txCount.toString(), stringResource(R.string.income_per_day, (report.summary.txCount / days).toString()), s.tertiaryContainer, s.onTertiaryContainer, Modifier.weight(1f))
        MetricTile(stringResource(R.string.dash_average), fmt.moneyShort(report.average), stringResource(R.string.income_per_receipt), s.primaryContainer, s.onPrimaryContainer, Modifier.weight(1f))
    }
}

@Composable
private fun MetricTile(label: String, value: String, sub: String, container: androidx.compose.ui.graphics.Color, content: androidx.compose.ui.graphics.Color, modifier: Modifier) {
    androidx.compose.material3.Surface(modifier, shape = androidx.compose.foundation.shape.RoundedCornerShape(22.dp), color = container, contentColor = content) {
        Column(Modifier.padding(14.dp), verticalArrangement = Arrangement.spacedBy(4.dp)) {
            Text(label, style = MaterialTheme.typography.labelMedium, maxLines = 1)
            Text(value, style = MaterialTheme.typography.titleMedium.copy(fontWeight = androidx.compose.ui.text.font.FontWeight.ExtraBold), maxLines = 1)
            Text(sub, style = MaterialTheme.typography.labelSmall, maxLines = 1)
        }
    }
}

@Composable
fun TransactionRow(tx: TransactionEntity, onClick: () -> Unit) {
    val fmt = LocalFmt.current
    val void = tx.status == TxStatus.VOID
    ArnCard(onClick = onClick, contentPadding = PaddingValues(Spacing.lg)) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Column(Modifier.weight(1f)) {
                Text(ReceiptNumber.format(tx), style = MaterialTheme.typography.titleSmall)
                Text(fmt.dateTime(tx.waktu), style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
            }
            Column(horizontalAlignment = Alignment.End) {
                MoneyText(
                    tx.total,
                    style = MaterialTheme.typography.titleSmall,
                    color = if (void) MaterialTheme.colorScheme.onSurfaceVariant else MaterialTheme.colorScheme.onSurface,
                )
                Spacer(Modifier.height(2.dp))
                if (void) {
                    StatusPill(stringResource(R.string.tx_void), Tone.BAD)
                } else {
                    Text(
                        stringResource(if (tx.metodeBayar == PayMethod.CASH) R.string.pay_cash else R.string.pay_non_cash),
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
            }
        }
    }
}
