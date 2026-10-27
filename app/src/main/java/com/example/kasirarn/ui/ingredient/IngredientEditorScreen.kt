package com.example.kasirarn.ui.ingredient

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
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.Add
import androidx.compose.material.icons.outlined.Delete
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
import androidx.compose.runtime.mutableStateListOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.example.kasirarn.AppContainer
import com.example.kasirarn.R
import com.example.kasirarn.data.repo.AppError
import com.example.kasirarn.data.repo.IngredientDraft
import com.example.kasirarn.data.repo.MeasureDraft
import com.example.kasirarn.data.repo.Op
import com.example.kasirarn.data.repo.dimension
import com.example.kasirarn.data.repo.toUnit
import com.example.kasirarn.domain.DecimalParser
import com.example.kasirarn.domain.Dimension
import com.example.kasirarn.domain.Fraction
import com.example.kasirarn.domain.UnitConverter
import com.example.kasirarn.domain.UnitDef
import com.example.kasirarn.ui.components.ArnCard
import com.example.kasirarn.ui.components.ArnField
import com.example.kasirarn.ui.components.ArnTopBar
import com.example.kasirarn.ui.components.ChoiceChips
import com.example.kasirarn.ui.components.DecimalField
import com.example.kasirarn.ui.components.MoneyField
import com.example.kasirarn.ui.components.PrimaryButton
import com.example.kasirarn.ui.components.QuantityInput
import com.example.kasirarn.ui.components.SectionLabel
import com.example.kasirarn.ui.components.SegmentedChoice
import com.example.kasirarn.ui.components.parseMoney
import com.example.kasirarn.ui.components.quantityToBase
import com.example.kasirarn.ui.format.LocalFmt
import com.example.kasirarn.ui.nav.containerViewModel
import com.example.kasirarn.ui.onboarding.CoachId
import com.example.kasirarn.ui.onboarding.coachTarget
import com.example.kasirarn.ui.onboarding.rememberCoachListState
import com.example.kasirarn.ui.theme.Spacing
import kotlinx.coroutines.launch

enum class EditorError { NAME_EMPTY, NAME_DUPLICATE, MIN_INVALID, INITIAL_INVALID, VALUE_INVALID, MEASURE_NAME, MEASURE_FACTOR, MEASURE_DUPLICATE, STORAGE }

data class MeasureField(val key: Long, val name: String = "", val factor: String = "")

class IngredientEditorViewModel(private val c: AppContainer, private val id: Long?) : ViewModel() {
    var loaded by mutableStateOf(id == null)
        private set
    var name by mutableStateOf("")
    var dimension by mutableStateOf(Dimension.MASS)
    var purchaseUnit by mutableStateOf("")
    var minText by mutableStateOf("")
    var minUnit by mutableStateOf("")
    var initialText by mutableStateOf("")
    var initialUnit by mutableStateOf("")
    var initialValue by mutableStateOf("")
    val measures = mutableStateListOf<MeasureField>()
    var errors by mutableStateOf<Set<EditorError>>(emptySet())
        private set
    var saving by mutableStateOf(false)
        private set
    private var nextKey = 1L

    init {
        if (id != null) {
            viewModelScope.launch {
                val ing = c.ingredients.get(id)
                if (ing != null) {
                    name = ing.nama
                    dimension = ing.dimension()
                    c.ingredients.measures(id).forEach {
                        val unit = it.toUnit()
                        measures += MeasureField(nextKey++, it.namaSatuan, unit.factor.toDecimalString(6, ','))
                    }
                    purchaseUnit = ing.satuanBeli
                    val unitList = units
                    val u = bestUnit(ing.stokMinimum, dimension, unitList)
                    minUnit = u.name
                    minText = if (ing.stokMinimum == 0L) "" else UnitConverter.fromBase(ing.stokMinimum, u).toDecimalString(3, ',')
                }
                loaded = true
            }
        } else {
            purchaseUnit = dimension.baseUnit
            minUnit = dimension.baseUnit
            initialUnit = dimension.baseUnit
        }
    }

    /** Satuan yang tersedia: standar dimensi + takaran kustom yang sudah lengkap isiannya. */
    val units: List<UnitDef>
        get() = dimension.standardUnits + measures.mapNotNull { m ->
            val f = DecimalParser.parse(m.factor)
            if (m.name.isNotBlank() && f != null && !f.isZero) UnitDef(m.name.trim(), f) else null
        }

    fun unitByName(n: String): UnitDef = units.firstOrNull { it.name == n } ?: units.first()

    fun changeDimension(d: Dimension) {
        if (id != null) return
        dimension = d
        purchaseUnit = d.baseUnit
        minUnit = d.baseUnit
        initialUnit = d.baseUnit
        measures.clear()
    }

    fun addMeasure() { measures += MeasureField(nextKey++) }
    fun updateMeasure(index: Int, m: MeasureField) { measures[index] = m }
    fun removeMeasure(index: Int) { measures.removeAt(index) }

    fun save(onDone: () -> Unit) {
        val found = mutableSetOf<EditorError>()
        if (name.isBlank()) found += EditorError.NAME_EMPTY
        val minBase = if (minText.isBlank()) 0L else quantityToBase(minText, unitByName(minUnit))
        if (minBase == null) found += EditorError.MIN_INVALID
        val initBase = if (id != null || initialText.isBlank()) 0L else quantityToBase(initialText, unitByName(initialUnit))
        if (initBase == null) found += EditorError.INITIAL_INVALID
        val initValue = if (id != null || initialValue.isBlank()) 0L else parseMoney(initialValue)
        if (initValue == null) found += EditorError.VALUE_INVALID

        val drafts = mutableListOf<MeasureDraft>()
        for (m in measures) {
            val f: Fraction? = DecimalParser.parse(m.factor)
            if (m.name.isBlank()) found += EditorError.MEASURE_NAME
            if (f == null || f.isZero) found += EditorError.MEASURE_FACTOR
            if (m.name.isNotBlank() && f != null && !f.isZero) drafts += MeasureDraft(m.name.trim(), f.num, f.den)
        }
        errors = found
        if (found.isNotEmpty()) return

        saving = true
        viewModelScope.launch {
            val result = c.ingredients.save(
                IngredientDraft(
                    id = id,
                    name = name,
                    dimension = dimension,
                    purchaseUnit = purchaseUnit,
                    minStock = minBase ?: 0,
                    initialStock = initBase ?: 0,
                    initialValue = initValue ?: 0,
                    measures = drafts,
                ),
            )
            saving = false
            when (result) {
                is Op.Ok -> onDone()
                is Op.Fail -> errors = setOf(
                    when {
                        result.error == AppError.NAME_DUPLICATE && result.detail == "measure" -> EditorError.MEASURE_DUPLICATE
                        result.error == AppError.NAME_DUPLICATE -> EditorError.NAME_DUPLICATE
                        result.error == AppError.NAME_EMPTY && result.detail == "measure" -> EditorError.MEASURE_NAME
                        result.error == AppError.NAME_EMPTY -> EditorError.NAME_EMPTY
                        else -> EditorError.STORAGE
                    },
                )
            }
        }
    }
}

@Composable
fun IngredientEditorScreen(id: Long?, onBack: () -> Unit) {
    val vm = containerViewModel(key = "ingredient-edit-$id") { IngredientEditorViewModel(it, id) }
    val errors = vm.errors
    val snackbar = remember { SnackbarHostState() }
    val storageError = stringResource(R.string.err_storage)
    LaunchedEffect(errors) {
        if (EditorError.STORAGE in errors) snackbar.showSnackbar(storageError)
    }
    val isNew = id == null
    val dimName = vm.dimension.baseUnit

    Scaffold(
        containerColor = MaterialTheme.colorScheme.background,
        topBar = {
            ArnTopBar(
                title = stringResource(if (isNew) R.string.ingredient_new else R.string.ingredient_edit),
                onBack = onBack,
            )
        },
        snackbarHost = { SnackbarHost(snackbar) },
        bottomBar = {
            Surface(color = MaterialTheme.colorScheme.background, tonalElevation = 0.dp) {
                PrimaryButton(
                    text = stringResource(R.string.action_save),
                    onClick = { vm.save(onBack) },
                    enabled = vm.loaded && !vm.saving,
                    modifier = Modifier.fillMaxWidth().navigationBarsPadding().imePadding().padding(Spacing.lg).coachTarget(CoachId.ING_SAVE),
                )
            }
        },
    ) { padding ->
        if (!vm.loaded) return@Scaffold
        LazyColumn(
            Modifier.fillMaxSize().padding(padding),
            state = rememberCoachListState(mapOf(CoachId.ING_NAME to 0, CoachId.ING_TYPE to 1, CoachId.ING_UNIT to 2, CoachId.ING_MIN to 3, CoachId.ING_INITIAL to 4, CoachId.ING_MEASURE to 5)),
            contentPadding = PaddingValues(start = Spacing.lg, end = Spacing.lg, bottom = Spacing.xl),
            verticalArrangement = Arrangement.spacedBy(Spacing.md),
        ) {
            item {
                ArnField(
                    value = vm.name,
                    onValueChange = { vm.name = it },
                    label = stringResource(R.string.ingredient_name),
                    modifier = Modifier.coachTarget(CoachId.ING_NAME),
                    error = when {
                        EditorError.NAME_EMPTY in errors -> stringResource(R.string.err_name_empty)
                        EditorError.NAME_DUPLICATE in errors -> stringResource(R.string.err_name_duplicate)
                        else -> null
                    },
                )
            }
            item {
                SectionLabel(stringResource(R.string.ingredient_dimension), Modifier.coachTarget(CoachId.ING_TYPE))
                SegmentedChoice(
                    options = Dimension.entries.map { it to dimensionLabel(it) },
                    selected = vm.dimension,
                    onSelect = vm::changeDimension,
                    enabled = isNew,
                )
                Spacer(Modifier.height(Spacing.xs))
                Text(
                    stringResource(
                        if (isNew) R.string.ingredient_dimension_hint else R.string.ingredient_dimension_locked,
                        dimName,
                    ),
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
            item {
                SectionLabel(stringResource(R.string.ingredient_purchase_unit), Modifier.coachTarget(CoachId.ING_UNIT))
                ChoiceChips(
                    options = vm.units.map { it.name to it.name },
                    selected = vm.purchaseUnit,
                    onSelect = { vm.purchaseUnit = it },
                )
            }
            item {
                SectionLabel(stringResource(R.string.ingredient_min_stock), Modifier.coachTarget(CoachId.ING_MIN))
                QuantityInput(
                    text = vm.minText,
                    onText = { vm.minText = it },
                    units = vm.units,
                    unit = vm.unitByName(vm.minUnit),
                    onUnit = { vm.minUnit = it.name },
                    baseUnitName = dimName,
                    label = stringResource(R.string.ingredient_min_stock_label),
                    error = if (EditorError.MIN_INVALID in errors) stringResource(R.string.err_invalid_number) else null,
                )
                Text(
                    stringResource(R.string.ingredient_min_stock_hint),
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
            if (isNew) {
                item {
                    SectionLabel(stringResource(R.string.ingredient_initial), Modifier.coachTarget(CoachId.ING_INITIAL))
                    Column(verticalArrangement = Arrangement.spacedBy(Spacing.md)) {
                        QuantityInput(
                            text = vm.initialText,
                            onText = { vm.initialText = it },
                            units = vm.units,
                            unit = vm.unitByName(vm.initialUnit),
                            onUnit = { vm.initialUnit = it.name },
                            baseUnitName = dimName,
                            label = stringResource(R.string.ingredient_initial_label),
                            error = if (EditorError.INITIAL_INVALID in errors) stringResource(R.string.err_invalid_number) else null,
                        )
                        MoneyField(
                            value = vm.initialValue,
                            onValueChange = { vm.initialValue = it },
                            label = stringResource(R.string.ingredient_initial_value),
                            supporting = stringResource(R.string.ingredient_initial_value_hint),
                            error = if (EditorError.VALUE_INVALID in errors) stringResource(R.string.err_invalid_number) else null,
                        )
                    }
                }
            }
            item {
                SectionLabel(stringResource(R.string.ingredient_measures), Modifier.coachTarget(CoachId.ING_MEASURE)) {
                    TextButton(onClick = vm::addMeasure) {
                        Icon(Icons.Outlined.Add, contentDescription = null, modifier = Modifier.width(18.dp))
                        Spacer(Modifier.width(Spacing.xs))
                        Text(stringResource(R.string.ingredient_measure_add))
                    }
                }
                Text(
                    stringResource(R.string.ingredient_measures_hint, dimName),
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
            if (EditorError.MEASURE_DUPLICATE in errors) {
                item { Text(stringResource(R.string.ingredient_measure_duplicate), color = MaterialTheme.colorScheme.error, style = MaterialTheme.typography.bodySmall) }
            }
            itemsIndexed(vm.measures, key = { _, m -> m.key }) { index, m ->
                ArnCard {
                    Column(verticalArrangement = Arrangement.spacedBy(Spacing.md)) {
                        Row(verticalAlignment = Alignment.Top, horizontalArrangement = Arrangement.spacedBy(Spacing.sm)) {
                            ArnField(
                                value = m.name,
                                onValueChange = { vm.updateMeasure(index, m.copy(name = it.take(24))) },
                                label = stringResource(R.string.ingredient_measure_name),
                                modifier = Modifier.weight(1f),
                                error = if (EditorError.MEASURE_NAME in errors && m.name.isBlank()) stringResource(R.string.err_name_empty) else null,
                                capitalization = androidx.compose.ui.text.input.KeyboardCapitalization.None,
                            )
                            IconButton(onClick = { vm.removeMeasure(index) }, modifier = Modifier.padding(top = 4.dp)) {
                                Icon(Icons.Outlined.Delete, contentDescription = stringResource(R.string.action_delete))
                            }
                        }
                        DecimalField(
                            value = m.factor,
                            onValueChange = { vm.updateMeasure(index, m.copy(factor = it)) },
                            label = stringResource(R.string.ingredient_measure_equals, m.name.ifBlank { stringResource(R.string.ingredient_measure_one) }),
                            suffix = dimName,
                            error = if (EditorError.MEASURE_FACTOR in errors && (DecimalParser.parse(m.factor)?.isZero != false)) {
                                stringResource(R.string.err_invalid_number)
                            } else {
                                null
                            },
                        )
                    }
                }
            }
        }
    }
}
