package com.example.kasirarn.ui.ingredient

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
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
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.AddShoppingCart
import androidx.compose.material.icons.outlined.Edit
import androidx.compose.material.icons.outlined.FactCheck
import androidx.compose.material.icons.outlined.History
import androidx.compose.material.icons.outlined.MoreVert
import androidx.compose.material.icons.outlined.PointOfSale
import androidx.compose.material.icons.outlined.Undo
import androidx.compose.material3.Checkbox
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.AlertDialog
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
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import androidx.lifecycle.ViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewModelScope
import com.example.kasirarn.AppContainer
import com.example.kasirarn.R
import com.example.kasirarn.data.db.IngredientEntity
import com.example.kasirarn.data.db.MeasureEntity
import com.example.kasirarn.data.db.StockMoveType
import com.example.kasirarn.data.db.StockMovementEntity
import com.example.kasirarn.data.repo.AppError
import com.example.kasirarn.data.repo.DayMath
import com.example.kasirarn.data.repo.DeleteOutcome
import com.example.kasirarn.data.repo.Op
import com.example.kasirarn.data.repo.Reason
import com.example.kasirarn.data.repo.toState
import com.example.kasirarn.data.repo.unitsFor
import com.example.kasirarn.domain.Fraction
import com.example.kasirarn.domain.UnitDef
import com.example.kasirarn.ui.components.ArnCard
import com.example.kasirarn.ui.components.ChoiceChips
import com.example.kasirarn.ui.components.ConfirmDialog
import com.example.kasirarn.ui.components.EmptyState
import com.example.kasirarn.ui.components.MoneyField
import com.example.kasirarn.ui.components.MoneyText
import com.example.kasirarn.ui.components.PrimaryButton
import com.example.kasirarn.ui.components.QuantityInput
import com.example.kasirarn.ui.components.SecondaryButton
import com.example.kasirarn.ui.components.SectionLabel
import com.example.kasirarn.ui.components.parseDecimal
import com.example.kasirarn.ui.components.parseMoney
import com.example.kasirarn.ui.components.quantityToBase
import com.example.kasirarn.ui.format.LocalFmt
import com.example.kasirarn.ui.nav.containerViewModel
import com.example.kasirarn.ui.theme.MoneyStyle
import com.example.kasirarn.ui.theme.Spacing
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.flatMapLatest
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch

enum class HistoryRange { WEEK, MONTH, ALL }

@OptIn(ExperimentalCoroutinesApi::class)
class IngredientDetailViewModel(private val c: AppContainer, val id: Long) : ViewModel() {
    val ingredient: StateFlow<IngredientEntity?> =
        c.ingredients.observeIngredient(id).stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), null)

    val measures: StateFlow<List<MeasureEntity>> =
        c.ingredients.observeMeasuresByIngredient().map { it[id].orEmpty() }
            .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), emptyList())

    private val range = MutableStateFlow(HistoryRange.MONTH)
    val historyRange: StateFlow<HistoryRange> = range

    val movements: StateFlow<List<StockMovementEntity>> = range.flatMapLatest { r ->
        val now = System.currentTimeMillis()
        val from = when (r) {
            HistoryRange.WEEK -> DayMath.addDays(DayMath.startOfDay(now), -6)
            HistoryRange.MONTH -> DayMath.addDays(DayMath.startOfDay(now), -29)
            HistoryRange.ALL -> 0L
        }
        c.ingredients.observeMovements(id, from, Long.MAX_VALUE)
    }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), emptyList())

    fun setRange(r: HistoryRange) { range.value = r }

    fun restock(qty: Long, cost: Long, onResult: (Op<Unit>) -> Unit) {
        viewModelScope.launch { onResult(c.ingredients.restock(id, qty, cost)) }
    }

    fun adjust(physical: Long, reason: String, loss: Boolean, onResult: (Op<*>) -> Unit) {
        viewModelScope.launch { onResult(c.ingredients.adjust(id, physical, reason, loss)) }
    }

    fun delete(onResult: (Op<DeleteOutcome>) -> Unit) {
        viewModelScope.launch { onResult(c.ingredients.delete(id)) }
    }
}

@Composable
fun IngredientDetailScreen(id: Long, onBack: () -> Unit, onEdit: (Long) -> Unit) {
    val vm = containerViewModel(key = "ingredient-$id") { IngredientDetailViewModel(it, id) }
    val ing by vm.ingredient.collectAsStateWithLifecycle()
    val measures by vm.measures.collectAsStateWithLifecycle()
    val movements by vm.movements.collectAsStateWithLifecycle()
    val range by vm.historyRange.collectAsStateWithLifecycle()
    val snackbar = remember { SnackbarHostState() }
    val scope = rememberCoroutineScope()
    val context = LocalContext.current
    val resources = androidx.compose.ui.platform.LocalResources.current
    var menuOpen by remember { mutableStateOf(false) }
    var showRestock by remember { mutableStateOf(false) }
    var showAdjust by remember { mutableStateOf(false) }
    var confirmDelete by remember { mutableStateOf(false) }
    var blockedBy by remember { mutableStateOf<String?>(null) }
    val fmt = LocalFmt.current
    val current = ing

    Scaffold(
        containerColor = MaterialTheme.colorScheme.background,
        snackbarHost = { SnackbarHost(snackbar) },
        topBar = {
            com.example.kasirarn.ui.components.ArnTopBar(
                title = current?.nama.orEmpty(),
                onBack = onBack,
                actions = {
                    IconButton(onClick = { onEdit(id) }) {
                        Icon(Icons.Outlined.Edit, contentDescription = stringResource(R.string.action_edit))
                    }
                    IconButton(onClick = { menuOpen = true }) {
                        Icon(Icons.Outlined.MoreVert, contentDescription = stringResource(R.string.action_more))
                    }
                    DropdownMenu(expanded = menuOpen, onDismissRequest = { menuOpen = false }) {
                        DropdownMenuItem(
                            text = { Text(stringResource(R.string.action_delete), color = MaterialTheme.colorScheme.error) },
                            onClick = {
                                menuOpen = false
                                confirmDelete = true
                            },
                        )
                    }
                },
            )
        },
    ) { padding ->
        if (current == null) return@Scaffold
        val dim = current.dim()
        val units = unitsFor(dim, measures)
        LazyColumn(
            Modifier.fillMaxSize().padding(padding),
            contentPadding = PaddingValues(start = Spacing.lg, end = Spacing.lg, bottom = Spacing.xxl),
        ) {
            item {
                ArnCard(Modifier.fillMaxWidth()) {
                    Column {
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Column(Modifier.weight(1f)) {
                                Text(
                                    stringResource(R.string.detail_stock_now),
                                    style = MaterialTheme.typography.labelLarge,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                                )
                                Text(fmt.quantity(current.stokDasar, dim), style = MaterialTheme.typography.headlineMedium.merge(MoneyStyle))
                            }
                            StockLevelPill(current.level())
                        }
                        Spacer(Modifier.height(Spacing.md))
                        HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant)
                        Spacer(Modifier.height(Spacing.md))
                        InfoRow(stringResource(R.string.detail_stock_value)) { MoneyText(current.nilaiStok.coerceAtLeast(0), style = MaterialTheme.typography.bodyMedium) }
                        val displayUnit = dim.standardUnits.last().takeIf { dim != com.example.kasirarn.domain.Dimension.COUNT }
                            ?: dim.standardUnits.first()
                        InfoRow(stringResource(R.string.detail_avg_cost, displayUnit.name)) {
                            val unitCost = averageCost(current, displayUnit)
                            if (unitCost == null) {
                                Text(stringResource(R.string.detail_no_cost), style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
                            } else {
                                MoneyText(unitCost, style = MaterialTheme.typography.bodyMedium)
                            }
                        }
                        InfoRow(stringResource(R.string.detail_min_stock)) {
                            Text(
                                if (current.stokMinimum == 0L) stringResource(R.string.detail_not_set) else fmt.quantity(current.stokMinimum, dim),
                                style = MaterialTheme.typography.bodyMedium.merge(MoneyStyle),
                            )
                        }
                    }
                }
                Spacer(Modifier.height(Spacing.md))
                Row(horizontalArrangement = Arrangement.spacedBy(Spacing.md)) {
                    PrimaryButton(
                        stringResource(R.string.detail_restock),
                        onClick = { showRestock = true },
                        modifier = Modifier.weight(1f),
                        icon = Icons.Outlined.AddShoppingCart,
                    )
                    SecondaryButton(
                        stringResource(R.string.detail_adjust),
                        onClick = { showAdjust = true },
                        modifier = Modifier.weight(1f),
                        icon = Icons.Outlined.FactCheck,
                    )
                }
                SectionLabel(stringResource(R.string.detail_history))
                ChoiceChips(
                    options = listOf(
                        HistoryRange.WEEK to stringResource(R.string.history_week),
                        HistoryRange.MONTH to stringResource(R.string.history_month),
                        HistoryRange.ALL to stringResource(R.string.history_all),
                    ),
                    selected = range,
                    onSelect = vm::setRange,
                )
                Spacer(Modifier.height(Spacing.sm))
            }
            if (movements.isEmpty()) {
                item {
                    EmptyState(
                        icon = Icons.Outlined.History,
                        title = stringResource(R.string.history_empty_title),
                        body = stringResource(R.string.history_empty_body),
                    )
                }
            }
            items(movements, key = { it.id }) { m ->
                MovementRow(m, dim)
                HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant)
            }
        }

        if (showRestock) {
            RestockSheet(
                ingredient = current,
                units = units,
                onDismiss = { showRestock = false },
                onSave = { qty, cost ->
                    vm.restock(qty, cost) { result ->
                        showRestock = false
                        scope.launch {
                            when (result) {
                                is Op.Ok -> snackbar.showSnackbar(resources.getString(R.string.restock_done))
                                is Op.Fail -> snackbar.showSnackbar(resources.getString(R.string.err_storage))
                            }
                        }
                    }
                },
            )
        }
        if (showAdjust) {
            AdjustSheet(
                ingredient = current,
                units = units,
                onDismiss = { showAdjust = false },
                onSave = { physical, reason, loss ->
                    vm.adjust(physical, reason, loss) { result ->
                        showAdjust = false
                        scope.launch {
                            if (result is Op.Ok<*>) snackbar.showSnackbar(resources.getString(R.string.adjust_done))
                            else snackbar.showSnackbar(resources.getString(R.string.err_storage))
                        }
                    }
                },
            )
        }
    }

    if (confirmDelete) {
        ConfirmDialog(
            title = stringResource(R.string.ingredient_delete_title),
            text = stringResource(R.string.ingredient_delete_body),
            confirmLabel = stringResource(R.string.action_delete),
            dismissLabel = stringResource(R.string.action_cancel),
            destructive = true,
            onDismiss = { confirmDelete = false },
            onConfirm = {
                confirmDelete = false
                vm.delete { result ->
                    when (result) {
                        is Op.Ok -> {
                            scope.launch {
                                if (result.value == DeleteOutcome.Archived) {
                                    snackbar.showSnackbar(resources.getString(R.string.ingredient_archived))
                                }
                                onBack()
                            }
                        }
                        is Op.Fail -> if (result.error == AppError.IN_USE) {
                            blockedBy = result.detail.orEmpty()
                        } else {
                            scope.launch { snackbar.showSnackbar(resources.getString(R.string.err_storage)) }
                        }
                    }
                }
            },
        )
    }
    blockedBy?.let { menus ->
        AlertDialog(
            onDismissRequest = { blockedBy = null },
            title = { Text(stringResource(R.string.ingredient_in_use_title)) },
            text = {
                Column(Modifier.verticalScroll(rememberScrollState())) {
                    Text(stringResource(R.string.ingredient_in_use_body), style = MaterialTheme.typography.bodyMedium)
                    Spacer(Modifier.height(Spacing.sm))
                    Text(menus, style = MaterialTheme.typography.bodyMedium.copy(fontWeight = androidx.compose.ui.text.font.FontWeight.SemiBold))
                }
            },
            confirmButton = { TextButton(onClick = { blockedBy = null }) { Text(stringResource(R.string.action_ok)) } },
            containerColor = MaterialTheme.colorScheme.surfaceContainerHigh,
        )
    }
}

/** Harga rata-rata per satuan tampil; jatuh ke harga pembelian terakhir bila stok kosong. */
private fun averageCost(i: IngredientEntity, unit: UnitDef): Long? {
    val factor = unit.factor
    val s = i.toState()
    return when {
        s.hasAverage -> (Fraction.of(s.value) * factor / Fraction.of(s.qty)).roundHalfUp()
        s.lastCostQty > 0 -> (Fraction.of(s.lastCostValue) * factor / Fraction.of(s.lastCostQty)).roundHalfUp()
        else -> null
    }
}

@Composable
private fun InfoRow(label: String, value: @Composable () -> Unit) {
    Row(Modifier.fillMaxWidth().padding(vertical = 3.dp), horizontalArrangement = Arrangement.SpaceBetween, verticalAlignment = Alignment.CenterVertically) {
        Text(label, style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
        value()
    }
}

@Composable
private fun MovementRow(m: StockMovementEntity, dim: com.example.kasirarn.domain.Dimension) {
    val fmt = LocalFmt.current
    val scheme = MaterialTheme.colorScheme
    val (icon, tint, bg) = when (m.jenis) {
        StockMoveType.RESTOCK -> Triple(Icons.Outlined.AddShoppingCart, scheme.onSecondaryContainer, scheme.secondaryContainer)
        StockMoveType.SALE -> Triple(Icons.Outlined.PointOfSale, scheme.onPrimaryContainer, scheme.primaryContainer)
        StockMoveType.VOID -> Triple(Icons.Outlined.Undo, scheme.onSurfaceVariant, scheme.surfaceContainerHigh)
        else -> Triple(Icons.Outlined.FactCheck, scheme.onTertiaryContainer, scheme.tertiaryContainer)
    }
    Row(Modifier.fillMaxWidth().padding(vertical = Spacing.md), verticalAlignment = Alignment.CenterVertically) {
        MovementIcon(icon, tint, bg)
        Spacer(Modifier.width(Spacing.md))
        Column(Modifier.weight(1f)) {
            val label = moveTypeLabel(m.jenis)
            val reason = if (m.catatan.isNotBlank()) " · " + reasonLabel(m.catatan) else ""
            Text(label + reason, style = MaterialTheme.typography.titleSmall)
            Text(fmt.dateTime(m.waktu), style = MaterialTheme.typography.bodySmall, color = scheme.onSurfaceVariant)
        }
        Column(horizontalAlignment = Alignment.End) {
            val sign = if (m.jumlahDasar > 0) "+" else if (m.jumlahDasar < 0) "-" else ""
            Text(
                sign + fmt.quantity(Math.abs(m.jumlahDasar), dim),
                style = MaterialTheme.typography.titleSmall.merge(MoneyStyle),
                color = if (m.jumlahDasar > 0) scheme.secondary else scheme.onSurface,
            )
            if (m.nilai != 0L) {
                Text(
                    (if (m.nilai < 0) "-" else "") + fmt.money(Math.abs(m.nilai)),
                    style = MaterialTheme.typography.bodySmall.merge(MoneyStyle),
                    color = scheme.onSurfaceVariant,
                )
            }
        }
    }
}

@Composable
private fun MovementIcon(icon: ImageVector, tint: androidx.compose.ui.graphics.Color, bg: androidx.compose.ui.graphics.Color) {
    Box(Modifier.size(40.dp).clip(CircleShape).background(bg), contentAlignment = Alignment.Center) {
        Icon(icon, contentDescription = null, tint = tint, modifier = Modifier.size(20.dp))
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun RestockSheet(
    ingredient: IngredientEntity,
    units: List<UnitDef>,
    onDismiss: () -> Unit,
    onSave: (qty: Long, cost: Long) -> Unit,
) {
    val fmt = LocalFmt.current
    var qtyText by remember { mutableStateOf("") }
    var unit by remember { mutableStateOf(units.firstOrNull { it.name.equals(ingredient.satuanBeli, true) } ?: units.first()) }
    var priceText by remember { mutableStateOf("") }
    var submitted by remember { mutableStateOf(false) }
    val qty = quantityToBase(qtyText, unit)
    val price = parseMoney(priceText)
    val qtyError = submitted && (qty == null || qty <= 0)
    val priceError = submitted && priceText.isNotBlank() && price == null

    ModalBottomSheet(onDismissRequest = onDismiss, sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true)) {
        Column(
            Modifier.navigationBarsPadding().imePadding().verticalScroll(rememberScrollState()).padding(horizontal = Spacing.lg),
            verticalArrangement = Arrangement.spacedBy(Spacing.md),
        ) {
            Text(stringResource(R.string.restock_title, ingredient.nama), style = MaterialTheme.typography.titleLarge)
            QuantityInput(
                text = qtyText,
                onText = { qtyText = it },
                units = units,
                unit = unit,
                onUnit = { unit = it },
                baseUnitName = ingredient.satuanDasar,
                label = stringResource(R.string.restock_qty),
                error = if (qtyError) stringResource(R.string.err_invalid_number) else null,
            )
            MoneyField(
                value = priceText,
                onValueChange = { priceText = it },
                label = stringResource(R.string.restock_price),
                error = if (priceError) stringResource(R.string.err_invalid_number) else null,
                supporting = run {
                    val f = parseDecimal(qtyText)
                    if (f != null && !f.isZero && price != null && price > 0) {
                        stringResource(R.string.restock_per_unit, fmt.money((Fraction.of(price) / f).roundHalfUp()), unit.name)
                    } else {
                        stringResource(R.string.restock_price_hint)
                    }
                },
                imeAction = androidx.compose.ui.text.input.ImeAction.Done,
            )
            PrimaryButton(
                text = stringResource(R.string.restock_save),
                onClick = {
                    submitted = true
                    if (qty != null && qty > 0 && (priceText.isBlank() || price != null)) onSave(qty, price ?: 0)
                },
                modifier = Modifier.fillMaxWidth(),
            )
            Spacer(Modifier.height(Spacing.md))
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun AdjustSheet(
    ingredient: IngredientEntity,
    units: List<UnitDef>,
    onDismiss: () -> Unit,
    onSave: (physical: Long, reason: String, recordLoss: Boolean) -> Unit,
) {
    val fmt = LocalFmt.current
    val dim = ingredient.dim()
    var text by remember { mutableStateOf("") }
    var unit by remember { mutableStateOf(bestUnit(ingredient.stokDasar, dim, units)) }
    var reason by remember { mutableStateOf(Reason.MISCOUNT) }
    var loss by remember { mutableStateOf(true) }
    var submitted by remember { mutableStateOf(false) }
    val physical = quantityToBase(text, unit)
    val diff = physical?.let { it - ingredient.stokDasar }

    ModalBottomSheet(onDismissRequest = onDismiss, sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true)) {
        Column(
            Modifier.navigationBarsPadding().imePadding().verticalScroll(rememberScrollState()).padding(horizontal = Spacing.lg),
            verticalArrangement = Arrangement.spacedBy(Spacing.md),
        ) {
            Text(stringResource(R.string.adjust_title, ingredient.nama), style = MaterialTheme.typography.titleLarge)
            Text(
                stringResource(R.string.adjust_system_stock, fmt.quantity(ingredient.stokDasar, dim)),
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
            QuantityInput(
                text = text,
                onText = { text = it },
                units = units,
                unit = unit,
                onUnit = { unit = it },
                baseUnitName = ingredient.satuanDasar,
                label = stringResource(R.string.adjust_physical),
                error = if (submitted && physical == null) stringResource(R.string.err_invalid_number) else null,
            )
            if (diff != null) {
                val sign = if (diff > 0) "+" else if (diff < 0) "-" else ""
                Text(
                    stringResource(R.string.adjust_diff, sign + fmt.quantity(Math.abs(diff), dim)),
                    style = MaterialTheme.typography.titleSmall,
                    color = if (diff < 0) MaterialTheme.colorScheme.error else MaterialTheme.colorScheme.onSurface,
                )
            }
            Column {
                Text(stringResource(R.string.adjust_reason), style = MaterialTheme.typography.titleSmall)
                Spacer(Modifier.height(Spacing.sm))
                ChoiceChips(
                    options = listOf(Reason.DAMAGED, Reason.SPILLED, Reason.MISCOUNT, Reason.OTHER).map { it to reasonLabel(it) },
                    selected = reason,
                    onSelect = { reason = it },
                )
            }
            if (diff != null && diff < 0) {
                Row(
                    Modifier.fillMaxWidth().height(Spacing.minTouch),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    Checkbox(checked = loss, onCheckedChange = { loss = it })
                    Spacer(Modifier.width(Spacing.xs))
                    Column {
                        Text(stringResource(R.string.adjust_record_loss), style = MaterialTheme.typography.bodyLarge)
                        Text(
                            stringResource(R.string.adjust_record_loss_hint),
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                    }
                }
            }
            PrimaryButton(
                text = stringResource(R.string.adjust_save),
                onClick = {
                    submitted = true
                    if (physical != null && physical >= 0) onSave(physical, reason, loss)
                },
                modifier = Modifier.fillMaxWidth(),
            )
            Spacer(Modifier.height(Spacing.md))
        }
    }
}
