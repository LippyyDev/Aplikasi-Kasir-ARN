package com.example.kasirarn.ui.report

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.GridItemSpan
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.Add
import androidx.compose.material.icons.outlined.Category
import androidx.compose.material.icons.outlined.ReceiptLong
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.ExtendedFloatingActionButton
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import kotlinx.coroutines.CoroutineScope
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewModelScope
import com.example.kasirarn.AppContainer
import com.example.kasirarn.R
import com.example.kasirarn.data.db.ExpenseCategoryEntity
import com.example.kasirarn.data.db.ExpenseRow
import com.example.kasirarn.data.db.ExpenseSource
import com.example.kasirarn.data.db.ReservedExpenseCategory
import com.example.kasirarn.data.repo.ExpenseDraft
import com.example.kasirarn.data.repo.ExpenseReport
import com.example.kasirarn.data.repo.Financials
import com.example.kasirarn.data.repo.Op
import com.example.kasirarn.ui.components.ArnCard
import com.example.kasirarn.ui.components.ArnField
import com.example.kasirarn.ui.components.ArnTopBar
import com.example.kasirarn.ui.components.ChoiceChips
import com.example.kasirarn.ui.components.ConfirmDialog
import com.example.kasirarn.ui.components.DatePickerField
import com.example.kasirarn.ui.components.DonutWithLegend
import com.example.kasirarn.ui.components.EmptyState
import com.example.kasirarn.ui.components.MoneyField
import com.example.kasirarn.ui.components.MoneyText
import com.example.kasirarn.ui.components.PeriodSelector
import com.example.kasirarn.ui.components.PrimaryButton
import com.example.kasirarn.ui.components.SectionLabel
import com.example.kasirarn.ui.components.Slice
import com.example.kasirarn.ui.components.StatusPill
import com.example.kasirarn.ui.components.Tone
import com.example.kasirarn.ui.components.errorMessage
import com.example.kasirarn.ui.components.parseMoney
import com.example.kasirarn.ui.format.LocalFmt
import com.example.kasirarn.ui.nav.containerViewModel
import com.example.kasirarn.ui.theme.Spacing
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.flatMapLatest
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch

private const val PAGE = 30

data class ExpensesUi(
    val period: PeriodUi? = null,
    val financials: Financials? = null,
    val report: ExpenseReport? = null,
    val rows: List<ExpenseRow> = emptyList(),
    val categories: List<ExpenseCategoryEntity> = emptyList(),
    val canLoadMore: Boolean = false,
)

/** Keadaan laporan pengeluaran. Periode dipakai bersama dengan tab Pemasukan lewat [PeriodState]. */
@OptIn(ExperimentalCoroutinesApi::class)
class ExpensesState(private val c: AppContainer, private val scope: CoroutineScope, periodState: PeriodState) {
    private val limit = MutableStateFlow(PAGE)

    val ui: StateFlow<ExpensesUi> = periodState.state.flatMapLatest { p ->
        limit.value = PAGE
        combine(
            c.reports.observeFinancials(p.range),
            c.reports.observeExpenseReport(p.range),
            limit.flatMapLatest { c.reports.observeExpenses(p.range, it) },
            c.expenses.observeCategories(),
            limit,
        ) { fin, rep, rows, cats, lim -> ExpensesUi(p, fin, rep, rows, cats, rows.size >= lim) }
    }.stateIn(scope, SharingStarted.WhileSubscribed(5_000), ExpensesUi())

    fun loadMore() { limit.value += PAGE }

    fun save(draft: ExpenseDraft, done: (Op<Long>) -> Unit) {
        scope.launch { done(c.expenses.save(draft)) }
    }

    fun delete(id: Long, done: (Op<Unit>) -> Unit) {
        scope.launch { done(c.expenses.delete(id)) }
    }
}

/** Isi tab Pengeluaran: laba bersih, arus kas, rincian per kategori, dan daftar pengeluaran. */
@Composable
fun ExpensesContent(ui: ExpensesUi, onRowClick: (ExpenseRow) -> Unit, onLoadMore: () -> Unit) {
    val fmt = LocalFmt.current
    val fin = ui.financials
    val report = ui.report
    val full: androidx.compose.foundation.lazy.grid.LazyGridItemSpanScope.() -> GridItemSpan = { GridItemSpan(maxLineSpan) }

    LazyVerticalGrid(
        columns = GridCells.Adaptive(minSize = 320.dp),
        modifier = Modifier.fillMaxSize(),
        contentPadding = PaddingValues(start = Spacing.lg, end = Spacing.lg, top = Spacing.sm, bottom = 96.dp),
        horizontalArrangement = Arrangement.spacedBy(Spacing.md),
        verticalArrangement = Arrangement.spacedBy(Spacing.md),
    ) {
        if (fin != null) {
            item { ProfitCard(fin) }
            item { CashFlowCard(fin) }
        }
        if (report != null && report.byCategory.isNotEmpty()) {
            item(span = full) {
                ReportCard(stringResource(R.string.expenses_by_category)) {
                    DonutWithLegend(report.byCategory.map { Slice(it.label, it.amount, fmt.money(it.amount)) })
                }
            }
        }
        item(span = full) { SectionLabel(stringResource(R.string.expenses_list)) }
        if (ui.rows.isEmpty()) {
            item(span = full) {
                EmptyState(
                    icon = Icons.Outlined.ReceiptLong,
                    title = stringResource(R.string.expenses_empty_title),
                    body = stringResource(R.string.expenses_empty_body),
                )
            }
        }
        items(ui.rows, key = { it.id }) { row ->
            val manual = row.sumber == ExpenseSource.MANUAL
            ArnCard(
                onClick = if (manual) ({ onRowClick(row) }) else null,
                contentPadding = PaddingValues(Spacing.lg),
            ) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Column(Modifier.weight(1f)) {
                        Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(Spacing.sm)) {
                            Text(row.kategoriNama, style = MaterialTheme.typography.titleSmall)
                            when (row.sumber) {
                                ExpenseSource.RESTOCK -> StatusPill(stringResource(R.string.expenses_source_restock), Tone.NEUTRAL)
                                ExpenseSource.LOSS -> StatusPill(stringResource(R.string.expenses_source_loss), Tone.WARN)
                            }
                        }
                        Text(
                            fmt.date(row.tanggal) + if (row.catatan.isNotBlank()) " \u00b7 ${row.catatan}" else "",
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                            maxLines = 2,
                        )
                    }
                    MoneyText(row.nominal, style = MaterialTheme.typography.titleSmall)
                }
            }
        }
        if (ui.canLoadMore) {
            item(span = full) { TextButton(onClick = onLoadMore, modifier = Modifier.fillMaxWidth()) { Text(stringResource(R.string.action_load_more)) } }
        }
    }
}

@Composable
private fun ProfitCard(fin: Financials) {
    val loss = fin.netProfit < 0
    val scheme = MaterialTheme.colorScheme
    val container = if (loss) scheme.errorContainer else scheme.secondaryContainer
    val onContainer = if (loss) scheme.onErrorContainer else scheme.onSecondaryContainer
    ArnCard(Modifier.fillMaxWidth(), container = container, contentPadding = PaddingValues(Spacing.xl)) {
        Column {
            Text(stringResource(R.string.profit_net), style = MaterialTheme.typography.labelLarge, color = onContainer.copy(alpha = 0.85f))
            MoneyText(fin.netProfit, style = MaterialTheme.typography.displaySmall, color = onContainer, shrinkToFit = true)
            Spacer(Modifier.height(Spacing.md))
            HorizontalDivider(color = onContainer.copy(alpha = 0.2f))
            Spacer(Modifier.height(Spacing.sm))
            LineRow(stringResource(R.string.income_net), fin.netSales, color = onContainer, labelColor = onContainer.copy(alpha = 0.85f))
            LineRow(stringResource(R.string.profit_cogs), -fin.cogs, color = onContainer, labelColor = onContainer.copy(alpha = 0.85f))
            LineRow(stringResource(R.string.income_gross_profit), fin.grossProfit, bold = true, color = onContainer, labelColor = onContainer)
            LineRow(stringResource(R.string.profit_operating), -fin.operating, color = onContainer, labelColor = onContainer.copy(alpha = 0.85f))
            Spacer(Modifier.height(Spacing.sm))
            Text(
                stringResource(R.string.profit_note),
                style = MaterialTheme.typography.bodySmall,
                color = onContainer.copy(alpha = 0.8f),
            )
        }
    }
}

@Composable
private fun CashFlowCard(fin: Financials) {
    ArnCard(Modifier.fillMaxWidth()) {
        Column {
            Text(stringResource(R.string.cash_title), style = MaterialTheme.typography.titleSmall)
            Text(
                stringResource(R.string.cash_subtitle),
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
            Spacer(Modifier.height(Spacing.sm))
            LineRow(stringResource(R.string.cash_in), fin.netSales)
            LineRow(stringResource(R.string.cash_out_ingredients), -fin.ingredientPurchases)
            LineRow(stringResource(R.string.cash_out_manual), -fin.manualExpenses)
            HorizontalDivider(Modifier.padding(vertical = Spacing.sm), color = MaterialTheme.colorScheme.outlineVariant)
            LineRow(stringResource(R.string.cash_balance), fin.cashFlow, bold = true, negativeIsError = true)
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ExpenseSheet(
    row: ExpenseRow?,
    categories: List<ExpenseCategoryEntity>,
    onDismiss: () -> Unit,
    onSave: (ExpenseDraft) -> Unit,
    onDelete: (Long) -> Unit,
) {
    val fmt = LocalFmt.current
    var date by remember { mutableStateOf(row?.tanggal ?: com.example.kasirarn.data.repo.DayMath.startOfDay(System.currentTimeMillis())) }
    var categoryId by remember { mutableStateOf(row?.kategoriId ?: categories.lastOrNull()?.id) }
    var amount by remember { mutableStateOf(row?.nominal?.let { fmt.number(it) }.orEmpty()) }
    var note by remember { mutableStateOf(row?.catatan.orEmpty()) }
    var submitted by remember { mutableStateOf(false) }
    var confirmDelete by remember { mutableStateOf(false) }
    val parsed = parseMoney(amount)

    ModalBottomSheet(onDismissRequest = onDismiss, sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true)) {
        Column(
            Modifier.navigationBarsPadding().imePadding().verticalScroll(rememberScrollState()).padding(horizontal = Spacing.lg),
            verticalArrangement = Arrangement.spacedBy(Spacing.md),
        ) {
            Text(stringResource(if (row == null) R.string.expenses_add else R.string.expenses_edit), style = MaterialTheme.typography.titleLarge)
            DatePickerField(label = stringResource(R.string.expenses_date), value = date, onValue = { date = it })
            Column {
                Text(stringResource(R.string.expenses_category), style = MaterialTheme.typography.titleSmall)
                Spacer(Modifier.height(Spacing.sm))
                ChoiceChips(options = categories.map { it.id to it.nama }, selected = categoryId, onSelect = { categoryId = it })
            }
            MoneyField(
                value = amount,
                onValueChange = { amount = it },
                label = stringResource(R.string.expenses_amount),
                error = if (submitted && (parsed == null || parsed <= 0)) stringResource(R.string.err_invalid_number) else null,
            )
            ArnField(
                value = note,
                onValueChange = { note = it.take(120) },
                label = stringResource(R.string.expenses_note),
                imeAction = androidx.compose.ui.text.input.ImeAction.Done,
            )
            PrimaryButton(
                text = stringResource(R.string.action_save),
                onClick = {
                    submitted = true
                    val cat = categoryId
                    if (parsed != null && parsed > 0 && cat != null) onSave(ExpenseDraft(row?.id, date, cat, parsed, note))
                },
                modifier = Modifier.fillMaxWidth(),
            )
            if (row != null) {
                TextButton(onClick = { confirmDelete = true }, modifier = Modifier.fillMaxWidth()) {
                    Text(stringResource(R.string.expenses_delete), color = MaterialTheme.colorScheme.error)
                }
            }
            Spacer(Modifier.height(Spacing.md))
        }
    }
    if (confirmDelete && row != null) {
        ConfirmDialog(
            title = stringResource(R.string.expenses_delete_title),
            text = stringResource(R.string.expenses_delete_body),
            confirmLabel = stringResource(R.string.action_delete),
            dismissLabel = stringResource(R.string.action_cancel),
            destructive = true,
            onDismiss = { confirmDelete = false },
            onConfirm = {
                confirmDelete = false
                onDelete(row.id)
            },
        )
    }
}
