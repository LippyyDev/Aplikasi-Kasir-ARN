package com.example.kasirarn.ui.report

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.Add
import androidx.compose.material.icons.outlined.Category
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
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.lifecycle.ViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewModelScope
import com.example.kasirarn.AppContainer
import com.example.kasirarn.R
import com.example.kasirarn.data.db.ExpenseRow
import com.example.kasirarn.data.db.ReservedExpenseCategory
import com.example.kasirarn.data.repo.Op
import com.example.kasirarn.ui.components.ScreenHeader
import com.example.kasirarn.ui.components.PeriodSelector
import com.example.kasirarn.ui.nav.ReportTab
import com.example.kasirarn.ui.nav.containerViewModel
import com.example.kasirarn.ui.onboarding.CoachId
import com.example.kasirarn.ui.onboarding.coachTarget
import com.example.kasirarn.ui.theme.Spacing
import kotlinx.coroutines.launch

/** Satu periode untuk seluruh laporan: berpindah tab Pemasukan atau Pengeluaran tidak mengubah rentang. */
class ReportsViewModel(c: AppContainer) : ViewModel() {
    val periodState = PeriodState()
    val income = IncomeState(c, viewModelScope, periodState)
    val expenses = ExpensesState(c, viewModelScope, periodState)
}

private data class ExpenseSheetTarget(val row: ExpenseRow?)

/**
 * Mode Laporan: tab Pemasukan dan Pengeluaran berbagi pemilih periode di bagian atas.
 * Tombol catat pengeluaran hanya muncul di tab Pengeluaran.
 */
@Composable
fun ReportsScreen(
    initialTab: Int,
    onOpenTransaction: (Long) -> Unit,
    onManageCategories: () -> Unit,
) {
    val vm = containerViewModel { ReportsViewModel(it) }
    val incomeUi by vm.income.ui.collectAsStateWithLifecycle()
    val expensesUi by vm.expenses.ui.collectAsStateWithLifecycle()
    val period by vm.periodState.state.collectAsStateWithLifecycle(initialValue = null)
    var tab by rememberSaveable(initialTab) { mutableIntStateOf(initialTab.coerceIn(0, 1)) }
    var sheet by remember { mutableStateOf<ExpenseSheetTarget?>(null) }
    val snackbar = remember { SnackbarHostState() }
    val scope = rememberCoroutineScope()
    val context = LocalContext.current
    val resources = androidx.compose.ui.platform.LocalResources.current

    Scaffold(
        containerColor = MaterialTheme.colorScheme.background,
        snackbarHost = { SnackbarHost(snackbar) },
        topBar = {
            Column {
                ScreenHeader(
                    title = stringResource(R.string.nav_reports),
                    actions = {
                        if (tab == ReportTab.EXPENSES) {
                            IconButton(onClick = onManageCategories, modifier = Modifier.coachTarget(CoachId.EXPENSE_CATEGORIES)) {
                                Icon(Icons.Outlined.Category, contentDescription = stringResource(R.string.expenses_manage_categories))
                            }
                        }
                    },
                ) { tabModifier ->
                    PrimaryTabRow(
                        selectedTabIndex = tab,
                        modifier = tabModifier.coachTarget(CoachId.REPORT_TABS),
                        containerColor = MaterialTheme.colorScheme.background,
                        contentColor = MaterialTheme.colorScheme.primary,
                    ) {
                        listOf(ReportTab.INCOME to R.string.tab_income, ReportTab.EXPENSES to R.string.tab_expenses).forEach { (index, label) ->
                            Tab(
                                selected = tab == index,
                                onClick = { tab = index },
                                unselectedContentColor = MaterialTheme.colorScheme.onSurfaceVariant,
                                text = { Text(stringResource(label)) },
                            )
                        }
                    }
                }
                period?.let { p ->
                    Box(Modifier.padding(start = Spacing.lg, end = Spacing.lg, top = Spacing.md, bottom = Spacing.xs).coachTarget(CoachId.REPORT_PERIOD)) {
                        PeriodSelector(
                            period = p.period,
                            range = p.range,
                            customFrom = p.customFrom,
                            customTo = p.customTo,
                            onPeriod = vm.periodState::select,
                            onCustom = vm.periodState::setCustom,
                        )
                    }
                }
            }
        },
        floatingActionButton = {
            if (tab == ReportTab.EXPENSES) {
                ExtendedFloatingActionButton(
                    onClick = { sheet = ExpenseSheetTarget(null) },
                    modifier = Modifier.coachTarget(CoachId.EXPENSE_FAB),
                    icon = { Icon(Icons.Outlined.Add, contentDescription = null) },
                    text = { Text(stringResource(R.string.expenses_add)) },
                    containerColor = MaterialTheme.colorScheme.primary,
                    contentColor = MaterialTheme.colorScheme.onPrimary,
                )
            }
        },
    ) { padding ->
        Box(Modifier.fillMaxSize().padding(padding)) {
            if (tab == ReportTab.INCOME) {
                IncomeContent(incomeUi, onLoadMore = vm.income::loadMore, onOpenTransaction = onOpenTransaction)
            } else {
                ExpensesContent(
                    expensesUi,
                    onRowClick = { sheet = ExpenseSheetTarget(it) },
                    onLoadMore = vm.expenses::loadMore,
                )
            }
        }
    }

    sheet?.let { target ->
        ExpenseSheet(
            row = target.row,
            categories = expensesUi.categories.filter { !ReservedExpenseCategory.isReserved(it.id) },
            onDismiss = { sheet = null },
            onSave = { draft ->
                vm.expenses.save(draft) { r ->
                    sheet = null
                    if (r is Op.Fail) scope.launch { snackbar.showSnackbar(resources.getString(R.string.err_storage)) }
                }
            },
            onDelete = { id ->
                vm.expenses.delete(id) { r ->
                    sheet = null
                    if (r is Op.Fail) scope.launch { snackbar.showSnackbar(resources.getString(R.string.err_storage)) }
                }
            },
        )
    }
}
