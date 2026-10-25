package com.example.kasirarn.ui.home

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.Add
import androidx.compose.material.icons.outlined.Delete
import androidx.compose.material.icons.outlined.EditNote
import androidx.compose.material.icons.outlined.Remove
import androidx.compose.material.icons.outlined.Sell
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import com.example.kasirarn.R
import com.example.kasirarn.domain.DecimalParser
import com.example.kasirarn.domain.Discount
import com.example.kasirarn.domain.DiscountType
import com.example.kasirarn.domain.Fraction
import com.example.kasirarn.domain.MoneyParser
import com.example.kasirarn.ui.components.ArnField
import com.example.kasirarn.ui.components.DecimalField
import com.example.kasirarn.ui.components.MenuImage
import com.example.kasirarn.ui.components.MoneyField
import com.example.kasirarn.ui.components.MoneyText
import com.example.kasirarn.ui.components.PrimaryButton
import com.example.kasirarn.ui.components.QuantityStepper
import com.example.kasirarn.ui.components.SegmentedChoice
import com.example.kasirarn.ui.format.LocalFmt
import com.example.kasirarn.ui.theme.MoneyStyle
import com.example.kasirarn.ui.theme.Spacing

/** Keranjang sebagai lembar bawah (ponsel potret). */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun CartSheet(
    cart: CartUi,
    onDismiss: () -> Unit,
    onQuantity: (Long, Int) -> Unit,
    onRemove: (Long) -> Unit,
    onNote: (Long, String) -> Unit,
    onDiscount: (Discount) -> Unit,
    onClear: () -> Unit,
    onPay: () -> Unit,
) {
    val sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true)
    ModalBottomSheet(
        onDismissRequest = onDismiss,
        sheetState = sheetState,
        containerColor = MaterialTheme.colorScheme.surface,
    ) {
        CartPanel(
            cart = cart,
            onQuantity = onQuantity,
            onRemove = onRemove,
            onNote = onNote,
            onDiscount = onDiscount,
            onClear = onClear,
            onPay = onPay,
            modifier = Modifier.navigationBarsPadding(),
        )
    }
}

/**
 * Isi keranjang: daftar item, diskon, total, dan tombol bayar. Dipakai di lembar bawah (ponsel potret)
 * dan sebagai panel tetap di sisi kanan layar kasir (tablet, lanskap) dengan [fillHeight] true.
 */
@Composable
fun CartPanel(
    cart: CartUi,
    onQuantity: (Long, Int) -> Unit,
    onRemove: (Long) -> Unit,
    onNote: (Long, String) -> Unit,
    onDiscount: (Discount) -> Unit,
    onClear: () -> Unit,
    onPay: () -> Unit,
    modifier: Modifier = Modifier,
    fillHeight: Boolean = false,
) {
    var noteFor by remember { mutableStateOf<CartLineUi?>(null) }
    var showDiscount by remember { mutableStateOf(false) }
    var confirmClear by remember { mutableStateOf(false) }
    val fmt = LocalFmt.current

    Column(modifier) {
        Row(
            Modifier.fillMaxWidth().padding(start = Spacing.lg, end = Spacing.sm),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Text(stringResource(R.string.cart_title), style = MaterialTheme.typography.titleLarge, modifier = Modifier.weight(1f))
            if (cart.lines.isNotEmpty()) {
                TextButton(onClick = { confirmClear = true }) {
                    Text(stringResource(R.string.cart_clear), color = MaterialTheme.colorScheme.error)
                }
            }
        }
        if (cart.lines.isEmpty()) {
            Box(Modifier.weight(1f, fill = fillHeight).fillMaxWidth(), contentAlignment = Alignment.Center) {
                Text(
                    stringResource(R.string.cart_empty_hint),
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.padding(Spacing.xl),
                    textAlign = androidx.compose.ui.text.style.TextAlign.Center,
                )
            }
        } else {
            // Item, diskon, dan subtotal menggulir bersama; total dan tombol bayar selalu terlihat di bawah.
            LazyColumn(
                Modifier.weight(1f, fill = fillHeight),
                contentPadding = androidx.compose.foundation.layout.PaddingValues(horizontal = Spacing.lg),
            ) {
                items(cart.lines, key = { it.menuId }) { line ->
                    CartLineRow(
                        line = line,
                        onMinus = { onQuantity(line.menuId, line.quantity - 1) },
                        onPlus = { onQuantity(line.menuId, line.quantity + 1) },
                        onRemove = { onRemove(line.menuId) },
                        onNote = { noteFor = line },
                    )
                    HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant)
                }
                item(key = "adjustments") {
                    Column(Modifier.padding(top = Spacing.md)) {
                        Surface(
                            onClick = { showDiscount = true },
                            shape = MaterialTheme.shapes.small,
                            color = MaterialTheme.colorScheme.surfaceContainer,
                            modifier = Modifier.fillMaxWidth().heightIn(min = Spacing.minTouch),
                        ) {
                            Row(Modifier.padding(horizontal = Spacing.md), verticalAlignment = Alignment.CenterVertically) {
                                Icon(Icons.Outlined.Sell, contentDescription = null, modifier = Modifier.size(20.dp), tint = MaterialTheme.colorScheme.primary)
                                Spacer(Modifier.width(Spacing.sm))
                                Text(
                                    if (cart.discountAmount > 0) stringResource(R.string.cart_discount_applied) else stringResource(R.string.cart_add_discount),
                                    style = MaterialTheme.typography.labelLarge,
                                    modifier = Modifier.weight(1f),
                                )
                                if (cart.discountAmount > 0) {
                                    Text("-" + fmt.money(cart.discountAmount), style = MaterialTheme.typography.labelLarge.merge(MoneyStyle))
                                }
                            }
                        }
                        Spacer(Modifier.height(Spacing.sm))
                        SummaryRow(stringResource(R.string.cart_subtotal), cart.subtotal)
                        if (cart.discountAmount > 0) {
                            SummaryRow(stringResource(R.string.cart_discount), -cart.discountAmount)
                        }
                        Spacer(Modifier.height(Spacing.sm))
                    }
                }
            }
        }
        HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant)
        Column(Modifier.padding(horizontal = Spacing.lg, vertical = Spacing.md)) {
            Row(
                Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Text(stringResource(R.string.cart_total), style = MaterialTheme.typography.titleMedium)
                MoneyText(cart.total, style = MaterialTheme.typography.headlineSmall)
            }
            Spacer(Modifier.height(Spacing.md))
            PrimaryButton(
                text = stringResource(R.string.cart_pay),
                onClick = onPay,
                modifier = Modifier.fillMaxWidth(),
                enabled = cart.lines.isNotEmpty(),
            )
        }
    }

    noteFor?.let { line ->
        NoteDialog(
            title = line.name,
            initial = line.note,
            onDismiss = { noteFor = null },
            onSave = {
                onNote(line.menuId, it)
                noteFor = null
            },
        )
    }
    if (showDiscount) {
        DiscountDialog(
            subtotal = cart.subtotal,
            current = cart.discount,
            onDismiss = { showDiscount = false },
            onApply = {
                onDiscount(it)
                showDiscount = false
            },
        )
    }
    if (confirmClear) {
        com.example.kasirarn.ui.components.ConfirmDialog(
            title = stringResource(R.string.cart_clear_title),
            text = stringResource(R.string.cart_clear_body),
            confirmLabel = stringResource(R.string.cart_clear),
            dismissLabel = stringResource(R.string.action_cancel),
            destructive = true,
            onConfirm = {
                confirmClear = false
                onClear()
            },
            onDismiss = { confirmClear = false },
        )
    }
}

@Composable
private fun SummaryRow(label: String, value: Long) {
    Row(Modifier.fillMaxWidth().padding(vertical = 2.dp), horizontalArrangement = Arrangement.SpaceBetween) {
        Text(label, style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
        MoneyText(value, style = MaterialTheme.typography.bodyMedium)
    }
}

@Composable
private fun CartLineRow(
    line: CartLineUi,
    onMinus: () -> Unit,
    onPlus: () -> Unit,
    onRemove: () -> Unit,
    onNote: () -> Unit,
) {
    Row(Modifier.fillMaxWidth().padding(vertical = Spacing.md), verticalAlignment = Alignment.Top) {
        MenuImage(
            photo = line.photo,
            name = line.name,
            modifier = Modifier.size(56.dp),
            shape = MaterialTheme.shapes.small,
        )
        Spacer(Modifier.width(Spacing.md))
        Column(Modifier.weight(1f)) {
            Text(line.name, style = MaterialTheme.typography.titleSmall, maxLines = 2)
            MoneyText(line.price, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
            Row(
                Modifier.clickable(onClick = onNote).padding(vertical = Spacing.xs),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Icon(Icons.Outlined.EditNote, contentDescription = null, modifier = Modifier.size(16.dp), tint = MaterialTheme.colorScheme.primary)
                Spacer(Modifier.width(Spacing.xs))
                Text(
                    line.note.ifBlank { stringResource(R.string.home_add_note) },
                    style = MaterialTheme.typography.bodySmall,
                    color = if (line.note.isBlank()) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurface,
                    maxLines = 1,
                )
            }
        }
        Spacer(Modifier.width(Spacing.sm))
        Column(horizontalAlignment = Alignment.End) {
            QuantityStepper(
                quantity = line.quantity,
                onMinus = if (line.quantity > 1) onMinus else onRemove,
                onPlus = onPlus,
                minusIsDelete = line.quantity == 1,
            )
            Spacer(Modifier.height(Spacing.xs))
            MoneyText(line.lineTotal, style = MaterialTheme.typography.titleSmall)
        }
    }
}

@Composable
fun NoteDialog(title: String, initial: String, onDismiss: () -> Unit, onSave: (String) -> Unit) {
    var text by remember { mutableStateOf(initial) }
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(title, maxLines = 2) },
        text = {
            ArnField(
                value = text,
                onValueChange = { text = it.take(80) },
                label = stringResource(R.string.note_label),
                supporting = stringResource(R.string.note_hint),
                singleLine = false,
                minLines = 2,
            )
        },
        confirmButton = { TextButton(onClick = { onSave(text.trim()) }) { Text(stringResource(R.string.action_save)) } },
        dismissButton = { TextButton(onClick = onDismiss) { Text(stringResource(R.string.action_cancel)) } },
        containerColor = MaterialTheme.colorScheme.surfaceContainerHigh,
    )
}

@Composable
fun DiscountDialog(subtotal: Long, current: Discount, onDismiss: () -> Unit, onApply: (Discount) -> Unit) {
    val fmt = LocalFmt.current
    var type by remember { mutableStateOf(current.type) }
    var text by remember {
        mutableStateOf(
            when {
                current.value.isZero -> ""
                current.type == DiscountType.NOMINAL -> fmt.number(current.value.roundHalfUp())
                else -> current.value.toDecimalString(2, fmt.decimalSeparator)
            },
        )
    }
    val parsed: Fraction? = if (type == DiscountType.NOMINAL) MoneyParser.parse(text)?.let { Fraction.of(it) } else DecimalParser.parse(text)
    val tooHigh = parsed != null && when (type) {
        DiscountType.NOMINAL -> parsed > Fraction.of(subtotal)
        DiscountType.PERCENT -> parsed > Fraction.of(100)
    }
    val valid = parsed != null && !parsed.isZero && !tooHigh
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(stringResource(R.string.discount_title)) },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(Spacing.md)) {
                SegmentedChoice(
                    options = listOf(
                        DiscountType.NOMINAL to fmt.currency,
                        DiscountType.PERCENT to "%",
                    ),
                    selected = type,
                    onSelect = {
                        type = it
                        text = ""
                    },
                )
                if (type == DiscountType.NOMINAL) {
                    MoneyField(
                        value = text,
                        onValueChange = { text = it },
                        label = stringResource(R.string.discount_amount),
                        error = if (tooHigh) stringResource(R.string.discount_too_high) else null,
                    )
                } else {
                    DecimalField(
                        value = text,
                        onValueChange = { text = it },
                        label = stringResource(R.string.discount_percent),
                        suffix = "%",
                        error = if (tooHigh) stringResource(R.string.discount_too_high) else null,
                    )
                }
            }
        },
        confirmButton = {
            TextButton(
                enabled = valid,
                onClick = { onApply(Discount(type, parsed ?: Fraction.ZERO)) },
            ) { Text(stringResource(R.string.action_apply)) }
        },
        dismissButton = {
            Row {
                if (!current.value.isZero) {
                    TextButton(onClick = { onApply(Discount.NONE) }) {
                        Text(stringResource(R.string.discount_remove), color = MaterialTheme.colorScheme.error)
                    }
                }
                TextButton(onClick = onDismiss) { Text(stringResource(R.string.action_cancel)) }
            }
        },
        containerColor = MaterialTheme.colorScheme.surfaceContainerHigh,
    )
}
