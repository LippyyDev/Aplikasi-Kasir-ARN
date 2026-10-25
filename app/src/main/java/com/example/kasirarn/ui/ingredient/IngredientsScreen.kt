package com.example.kasirarn.ui.ingredient

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.GridItemSpan
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.Add
import androidx.compose.material.icons.outlined.Inventory2
import androidx.compose.material.icons.outlined.MoreVert
import androidx.compose.material.icons.outlined.Search
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.ExtendedFloatingActionButton
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.pluralStringResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.lifecycle.ViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewModelScope
import com.example.kasirarn.AppContainer
import com.example.kasirarn.R
import com.example.kasirarn.data.db.IngredientEntity
import com.example.kasirarn.data.settings.StockPolicy
import com.example.kasirarn.ui.components.ArnCard
import com.example.kasirarn.ui.components.ArnField
import com.example.kasirarn.ui.components.ArnTopBar
import com.example.kasirarn.ui.components.EmptyState
import com.example.kasirarn.ui.components.MoneyText
import com.example.kasirarn.ui.format.LocalFmt
import com.example.kasirarn.ui.home.RadioRow
import com.example.kasirarn.ui.nav.containerViewModel
import com.example.kasirarn.ui.theme.MoneyStyle
import com.example.kasirarn.ui.theme.Spacing
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch

data class IngredientsUi(
    val loaded: Boolean = false,
    val items: List<IngredientEntity> = emptyList(),
    val totalCount: Int = 0,
    val totalValue: Long = 0,
    val lowCount: Int = 0,
    val outCount: Int = 0,
    val policy: StockPolicy = StockPolicy.BLOCK,
    val query: String = "",
)

class IngredientsViewModel(private val c: AppContainer) : ViewModel() {
    private val query = MutableStateFlow("")

    val ui: StateFlow<IngredientsUi> = combine(
        c.ingredients.observeIngredients(),
        c.settings.settings,
        query,
    ) { list, settings, q ->
        val needle = q.trim().lowercase()
        IngredientsUi(
            loaded = true,
            items = list.filter { needle.isEmpty() || it.nama.lowercase().contains(needle) },
            totalCount = list.size,
            totalValue = list.sumOf { it.nilaiStok.coerceAtLeast(0) },
            lowCount = list.count { it.level() == StockLevel.LOW },
            outCount = list.count { it.level() == StockLevel.OUT },
            policy = settings.stockPolicy,
            query = q,
        )
    }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), IngredientsUi())

    fun setQuery(q: String) { query.value = q }
    fun setPolicy(p: StockPolicy) { viewModelScope.launch { c.settings.setStockPolicy(p) } }
}

/** Isi tab Bahan baku di layar Kelola: ringkasan nilai stok, pencarian, dan daftar bahan. */
@Composable
fun IngredientsTab(
    ui: IngredientsUi,
    onQuery: (String) -> Unit,
    onOpen: (Long) -> Unit,
    onAdd: () -> Unit,
) {
    val fmt = LocalFmt.current
    if (ui.loaded && ui.totalCount == 0) {
        Column(Modifier.fillMaxSize(), verticalArrangement = Arrangement.Center) {
            EmptyState(
                icon = Icons.Outlined.Inventory2,
                title = stringResource(R.string.ingredient_empty_title),
                body = stringResource(R.string.ingredient_empty_body),
                actionLabel = stringResource(R.string.ingredient_add_first),
                onAction = onAdd,
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
        item(span = { GridItemSpan(maxLineSpan) }, key = "summary") {
            Column {
                SummaryStrip(ui)
                Spacer(Modifier.height(Spacing.md))
                ArnField(
                    value = ui.query,
                    onValueChange = onQuery,
                    label = stringResource(R.string.ingredient_search),
                    trailing = { Icon(Icons.Outlined.Search, contentDescription = null) },
                    imeAction = androidx.compose.ui.text.input.ImeAction.Search,
                )
            }
        }
        if (ui.items.isEmpty() && ui.query.isNotBlank()) {
            item(span = { GridItemSpan(maxLineSpan) }, key = "noresult") {
                EmptyState(
                    icon = Icons.Outlined.Search,
                    title = stringResource(R.string.ingredient_no_result),
                    body = stringResource(R.string.home_no_result_body, ui.query.trim()),
                )
            }
        }
        items(ui.items, key = { it.id }) { ing ->
            ArnCard(onClick = { onOpen(ing.id) }, contentPadding = PaddingValues(Spacing.lg)) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Column(Modifier.weight(1f)) {
                        Text(ing.nama, style = MaterialTheme.typography.titleSmall, maxLines = 1, overflow = TextOverflow.Ellipsis)
                        Spacer(Modifier.height(2.dp))
                        Text(
                            stringResource(R.string.ingredient_value, fmt.money(ing.nilaiStok.coerceAtLeast(0))),
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                    }
                    Spacer(Modifier.width(Spacing.md))
                    Column(horizontalAlignment = Alignment.End) {
                        Text(
                            fmt.quantity(ing.stokDasar, ing.dim()),
                            style = MaterialTheme.typography.titleSmall.merge(MoneyStyle),
                        )
                        Spacer(Modifier.height(Spacing.xs))
                        StockLevelPill(ing.level())
                    }
                }
            }
        }
    }
}

@Composable
fun StockPolicyDialog(current: StockPolicy, onSelect: (StockPolicy) -> Unit, onDismiss: () -> Unit) {
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(stringResource(R.string.stock_policy_title)) },
        text = {
            Column {
                Text(
                    stringResource(R.string.stock_policy_body),
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
                Spacer(Modifier.height(Spacing.md))
                RadioRow(stringResource(R.string.stock_policy_block), current == StockPolicy.BLOCK) { onSelect(StockPolicy.BLOCK) }
                Text(
                    stringResource(R.string.stock_policy_block_hint),
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.padding(start = 52.dp, bottom = Spacing.sm),
                )
                RadioRow(stringResource(R.string.stock_policy_warn), current == StockPolicy.WARN) { onSelect(StockPolicy.WARN) }
                Text(
                    stringResource(R.string.stock_policy_warn_hint),
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.padding(start = 52.dp),
                )
            }
        },
        confirmButton = { TextButton(onClick = onDismiss) { Text(stringResource(R.string.action_close)) } },
        containerColor = MaterialTheme.colorScheme.surfaceContainerHigh,
    )
}

@Composable
private fun SummaryStrip(ui: IngredientsUi) {
    ArnCard(modifier = Modifier.fillMaxWidth()) {
        Column {
            Text(
                stringResource(R.string.ingredient_stock_value),
                style = MaterialTheme.typography.labelLarge,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
            MoneyText(ui.totalValue, style = MaterialTheme.typography.headlineSmall)
            Spacer(Modifier.height(Spacing.xs))
            val low = ui.lowCount + ui.outCount
            Text(
                if (low == 0) {
                    pluralStringResource(R.plurals.ingredient_count, ui.totalCount, ui.totalCount)
                } else {
                    pluralStringResource(R.plurals.ingredient_count, ui.totalCount, ui.totalCount) + " · " +
                        stringResource(R.string.ingredient_need_attention, low)
                },
                style = MaterialTheme.typography.bodyMedium,
                color = if (low == 0) MaterialTheme.colorScheme.onSurfaceVariant else MaterialTheme.colorScheme.tertiary,
            )
        }
    }
}
