package com.example.kasirarn.ui.components

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.defaultMinSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.ArrowDropDown
import androidx.compose.material.icons.outlined.CalendarMonth
import androidx.compose.material3.DatePicker
import androidx.compose.material3.DatePickerDialog
import androidx.compose.material3.DateRangePicker
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilterChip
import androidx.compose.material3.FilterChipDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.rememberDatePickerState
import androidx.compose.material3.rememberDateRangePickerState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.unit.dp
import com.example.kasirarn.R
import com.example.kasirarn.data.repo.Period
import com.example.kasirarn.data.repo.TimeRange
import com.example.kasirarn.domain.Fraction
import com.example.kasirarn.domain.UnitConverter
import com.example.kasirarn.domain.UnitDef
import com.example.kasirarn.ui.format.LocalFmt
import com.example.kasirarn.ui.theme.Spacing
import java.util.Calendar
import java.util.TimeZone

/** Jumlah ke satuan dasar dari teks isian dan satuan terpilih; null bila teks bukan angka sah. */
fun quantityToBase(text: String, unit: UnitDef): Long? {
    val f: Fraction = parseDecimal(text) ?: return null
    return try {
        UnitConverter.toBase(f, unit)
    } catch (e: ArithmeticException) {
        null
    }
}

/** Isian jumlah dengan pemilih satuan; menampilkan hasil konversi ke satuan dasar. */
@Composable
fun QuantityInput(
    text: String,
    onText: (String) -> Unit,
    units: List<UnitDef>,
    unit: UnitDef,
    onUnit: (UnitDef) -> Unit,
    baseUnitName: String,
    label: String,
    modifier: Modifier = Modifier,
    error: String? = null,
    imeAction: ImeAction = ImeAction.Next,
) {
    val fmt = LocalFmt.current
    val base = quantityToBase(text, unit)
    val convertedHint = if (base != null && unit.factor != Fraction.ONE && text.isNotBlank()) {
        "= ${fmt.number(base)} $baseUnitName"
    } else {
        null
    }
    Row(modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(Spacing.sm), verticalAlignment = Alignment.Top) {
        DecimalField(
            value = text,
            onValueChange = onText,
            label = label,
            modifier = Modifier.weight(1f),
            error = error,
            supporting = convertedHint,
            imeAction = imeAction,
        )
        UnitPicker(units, unit, onUnit, Modifier.padding(top = 8.dp))
    }
}

@Composable
fun UnitPicker(units: List<UnitDef>, selected: UnitDef, onSelect: (UnitDef) -> Unit, modifier: Modifier = Modifier) {
    var open by remember { mutableStateOf(false) }
    Box(modifier) {
        OutlinedButton(
            onClick = { open = true },
            enabled = units.size > 1,
            modifier = Modifier.heightIn(min = 56.dp).defaultMinSize(minWidth = 96.dp),
            shape = MaterialTheme.shapes.medium,
            border = BorderStroke(1.dp, MaterialTheme.colorScheme.outline),
            contentPadding = androidx.compose.foundation.layout.PaddingValues(start = Spacing.md, end = Spacing.xs),
        ) {
            Text(selected.name, maxLines = 1, style = MaterialTheme.typography.bodyLarge)
            if (units.size > 1) Icon(Icons.Outlined.ArrowDropDown, contentDescription = null)
        }
        DropdownMenu(expanded = open, onDismissRequest = { open = false }) {
            units.forEach { u ->
                DropdownMenuItem(
                    text = { Text(u.name) },
                    onClick = {
                        onSelect(u)
                        open = false
                    },
                )
            }
        }
    }
}

/** Baris pilihan bergulir ke bawah bila tidak muat; setiap pilihan tampil sebagai chip. */
@OptIn(ExperimentalLayoutApi::class)
@Composable
fun <T> ChoiceChips(
    options: List<Pair<T, String>>,
    selected: T?,
    onSelect: (T) -> Unit,
    modifier: Modifier = Modifier,
) {
    FlowRow(
        modifier = modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.spacedBy(Spacing.sm),
        verticalArrangement = Arrangement.spacedBy(Spacing.sm),
    ) {
        options.forEach { (value, label) ->
            val isSelected = value == selected
            FilterChip(
                selected = isSelected,
                onClick = { onSelect(value) },
                label = { Text(label, style = MaterialTheme.typography.labelLarge) },
                shape = RoundedCornerShape(50),
                modifier = Modifier.heightIn(min = 40.dp),
                colors = FilterChipDefaults.filterChipColors(
                    containerColor = MaterialTheme.colorScheme.surfaceContainerHigh,
                    labelColor = MaterialTheme.colorScheme.onSurface,
                    selectedContainerColor = MaterialTheme.colorScheme.primary,
                    selectedLabelColor = MaterialTheme.colorScheme.onPrimary,
                ),
                border = FilterChipDefaults.filterChipBorder(
                    enabled = true,
                    selected = isSelected,
                    borderColor = Color.Transparent,
                    selectedBorderColor = Color.Transparent,
                ),
            )
        }
    }
}

// ---------- Tanggal ----------

/** Pemilih tanggal mengembalikan tengah malam UTC; ubah menjadi awal hari lokal pada tanggal yang sama. */
fun pickerUtcToLocalStart(utcMillis: Long): Long {
    val utc = Calendar.getInstance(TimeZone.getTimeZone("UTC")).apply { timeInMillis = utcMillis }
    return Calendar.getInstance().apply {
        clear()
        set(utc.get(Calendar.YEAR), utc.get(Calendar.MONTH), utc.get(Calendar.DAY_OF_MONTH), 0, 0, 0)
    }.timeInMillis
}

fun localToPickerUtc(localMillis: Long): Long {
    val local = Calendar.getInstance().apply { timeInMillis = localMillis }
    return Calendar.getInstance(TimeZone.getTimeZone("UTC")).apply {
        clear()
        set(local.get(Calendar.YEAR), local.get(Calendar.MONTH), local.get(Calendar.DAY_OF_MONTH), 0, 0, 0)
    }.timeInMillis
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun DatePickerField(
    label: String,
    value: Long,
    onValue: (Long) -> Unit,
    modifier: Modifier = Modifier,
) {
    var open by remember { mutableStateOf(false) }
    val fmt = LocalFmt.current
    OutlinedButton(
        onClick = { open = true },
        modifier = modifier.fillMaxWidth().heightIn(min = 56.dp),
        shape = MaterialTheme.shapes.small,
        border = BorderStroke(1.dp, MaterialTheme.colorScheme.outline),
    ) {
        Icon(Icons.Outlined.CalendarMonth, contentDescription = null)
        Spacer(Modifier.width(Spacing.sm))
        Column(Modifier.weight(1f)) {
            Text(label, style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
            Text(fmt.date(value), style = MaterialTheme.typography.bodyLarge, color = MaterialTheme.colorScheme.onSurface)
        }
    }
    if (open) {
        val state = rememberDatePickerState(initialSelectedDateMillis = localToPickerUtc(value))
        DatePickerDialog(
            onDismissRequest = { open = false },
            confirmButton = {
                TextButton(onClick = {
                    state.selectedDateMillis?.let { onValue(pickerUtcToLocalStart(it)) }
                    open = false
                }) { Text(stringResource(R.string.action_ok)) }
            },
            dismissButton = { TextButton(onClick = { open = false }) { Text(stringResource(R.string.action_cancel)) } },
        ) { DatePicker(state = state) }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun DateRangeDialog(
    initialFrom: Long,
    initialTo: Long,
    onDismiss: () -> Unit,
    onConfirm: (from: Long, toInclusive: Long) -> Unit,
) {
    val state = rememberDateRangePickerState(
        initialSelectedStartDateMillis = localToPickerUtc(initialFrom),
        initialSelectedEndDateMillis = localToPickerUtc(initialTo),
    )
    DatePickerDialog(
        onDismissRequest = onDismiss,
        confirmButton = {
            TextButton(
                enabled = state.selectedStartDateMillis != null,
                onClick = {
                    val a = state.selectedStartDateMillis ?: return@TextButton
                    val b = state.selectedEndDateMillis ?: a
                    onConfirm(pickerUtcToLocalStart(a), pickerUtcToLocalStart(b))
                },
            ) { Text(stringResource(R.string.action_apply)) }
        },
        dismissButton = { TextButton(onClick = onDismiss) { Text(stringResource(R.string.action_cancel)) } },
    ) {
        DateRangePicker(state = state, modifier = Modifier.height(500.dp))
    }
}

/** Pilihan periode laporan: Hari ini, 7 hari, Bulan ini, atau rentang kustom. */
@Composable
fun PeriodSelector(
    period: Period,
    range: TimeRange,
    customFrom: Long,
    customTo: Long,
    onPeriod: (Period) -> Unit,
    onCustom: (Long, Long) -> Unit,
    modifier: Modifier = Modifier,
) {
    var showRange by remember { mutableStateOf(false) }
    val fmt = LocalFmt.current
    Column(modifier) {
        ChoiceChips(
            options = listOf(
                Period.TODAY to stringResource(R.string.period_today),
                Period.WEEK to stringResource(R.string.period_week),
                Period.MONTH to stringResource(R.string.period_month),
                Period.CUSTOM to stringResource(R.string.period_custom),
            ),
            selected = period,
            onSelect = {
                if (it == Period.CUSTOM) showRange = true else onPeriod(it)
            },
        )
        Spacer(Modifier.height(Spacing.sm))
        val last = range.to - 1
        val text = if (range.isSingleDay) fmt.dateLong(range.from) else "${fmt.date(range.from)} – ${fmt.date(last)}"
        Text(text, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
    }
    if (showRange) {
        DateRangeDialog(
            initialFrom = customFrom,
            initialTo = customTo,
            onDismiss = { showRange = false },
            onConfirm = { a, b ->
                showRange = false
                onCustom(a, b)
            },
        )
    }
}
