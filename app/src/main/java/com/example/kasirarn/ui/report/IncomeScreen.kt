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
            item(span = full) { HeroCard(report) }
            item(span = full) {
                if (wide) {
                    StatRow {
                        StatTile(stringResource(R.string.income_gross), report.summary.subtotal, Modifier.weight(1f))
                        StatTile(stringResource(R.string.income_discount), report.summary.discount, Modifier.weight(1f))
                        StatTile(stringResource(R.string.income_cogs), report.cogs, Modifier.weight(1f))
                        StatTile(
                            stringResource(R.string.income_gross_profit), report.grossProfit, Modifier.weight(1f),
                            color = MaterialTheme.colorScheme.secondary, negativeAllowed = true,
                        )
                    }
                } else {
                    Column(verticalArrangement = Arrangement.spacedBy(Spacing.md)) {
                        StatRow {
                            StatTile(stringResource(R.string.income_gross), report.summary.subtotal, Modifier.weight(1f))
                            StatTile(stringResource(R.string.income_discount), report.summary.discount, Modifier.weight(1f))
                        }
                        StatRow {
                            StatTile(stringResource(R.string.income_cogs), report.cogs, Modifier.weight(1f))
                            StatTile(
                                stringResource(R.string.income_gross_profit), report.grossProfit, Modifier.weight(1f),
                                color = MaterialTheme.colorScheme.secondary, negativeAllowed = true,
                            )
                        }
                    }
                }
            }
            item(span = full) {
                Text(
                    stringResource(R.string.income_gross_profit_note),
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
            item(span = full) {
                val bars = buildBars(report.trend, period?.range ?: com.example.kasirarn.data.repo.TimeRange(0, 1), report.hourly, fmt.locale)
                ReportCard(stringResource(if (report.hourly) R.string.income_trend_hour else R.string.income_trend_day)) {
                    TrendBarChart(
                        bars = bars,
                        formatValue = { fmt.money(it) },
                        description = stringResource(R.string.income_trend_desc, fmt.money(report.summary.total)),
                    )
                }
            }
            if (report.topMenus.isNotEmpty()) {
                item {
                    ReportCard(stringResource(R.string.income_top_menus), Modifier.fillMaxHeight()) {
                        RankedBars(
                            report.topMenus.map {
                                RankRow(it.name, it.qty, stringResource(R.string.income_sold_qty, it.qty), fmt.money(it.revenue))
                            },
                        )
                    }
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

@Composable
private fun HeroCard(report: IncomeReport) {
    val onHero = MaterialTheme.colorScheme.onPrimaryContainer
    ArnCard(Modifier.fillMaxWidth().coachTarget(CoachId.REPORT_SUMMARY), container = MaterialTheme.colorScheme.primaryContainer, contentPadding = PaddingValues(Spacing.xl)) {
        Column {
            Text(stringResource(R.string.income_net), style = MaterialTheme.typography.labelLarge, color = onHero.copy(alpha = 0.85f))
            MoneyText(report.summary.total, style = MaterialTheme.typography.displaySmall, color = onHero, shrinkToFit = true)
            Spacer(Modifier.height(Spacing.xs))
            val fmt = LocalFmt.current
            Text(
                pluralStringResource(R.plurals.transaction_count, report.summary.txCount, report.summary.txCount) +
                    " · " + stringResource(R.string.income_average, fmt.money(report.average)),
                style = MaterialTheme.typography.bodyMedium,
                color = onHero.copy(alpha = 0.85f),
            )
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
