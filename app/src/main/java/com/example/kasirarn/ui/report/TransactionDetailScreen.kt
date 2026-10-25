package com.example.kasirarn.ui.report

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.Share
import androidx.compose.material.icons.outlined.Undo
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Text
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
import androidx.lifecycle.ViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewModelScope
import com.example.kasirarn.AppContainer
import com.example.kasirarn.R
import com.example.kasirarn.data.db.PayMethod
import com.example.kasirarn.data.db.TransactionEntity
import com.example.kasirarn.data.db.TransactionItemEntity
import com.example.kasirarn.data.db.TxStatus
import com.example.kasirarn.data.repo.Op
import com.example.kasirarn.data.repo.ReceiptNumber
import com.example.kasirarn.ui.components.ArnCard
import com.example.kasirarn.ui.components.ArnTopBar
import com.example.kasirarn.ui.components.ConfirmDialog
import com.example.kasirarn.ui.components.MoneyText
import com.example.kasirarn.ui.components.SecondaryButton
import com.example.kasirarn.ui.components.StatusPill
import com.example.kasirarn.ui.components.Tone
import com.example.kasirarn.ui.components.errorMessage
import com.example.kasirarn.ui.format.LocalFmt
import com.example.kasirarn.ui.home.ReceiptFormat
import com.example.kasirarn.ui.nav.containerViewModel
import com.example.kasirarn.ui.theme.MoneyStyle
import com.example.kasirarn.ui.theme.Spacing
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch

class TransactionDetailViewModel(private val c: AppContainer, private val id: Long) : ViewModel() {
    val transaction: StateFlow<TransactionEntity?> =
        c.sales.observeTransaction(id).stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), null)
    val items: StateFlow<List<TransactionItemEntity>> =
        c.sales.observeItems(id).stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), emptyList())

    fun void(done: (Op<Unit>) -> Unit) {
        viewModelScope.launch { done(c.sales.voidTransaction(id)) }
    }

    suspend fun receipt() = c.sales.receipt(id)
}

@Composable
fun TransactionDetailScreen(id: Long, onBack: () -> Unit) {
    val vm = containerViewModel(key = "tx-$id") { TransactionDetailViewModel(it, id) }
    val tx by vm.transaction.collectAsStateWithLifecycle()
    val items by vm.items.collectAsStateWithLifecycle()
    val fmt = LocalFmt.current
    val context = LocalContext.current
    val resources = androidx.compose.ui.platform.LocalResources.current
    val scope = rememberCoroutineScope()
    val snackbar = remember { SnackbarHostState() }
    var confirmVoid by remember { mutableStateOf(false) }
    val shareTitle = stringResource(R.string.success_share_title)
    val current = tx

    Scaffold(
        containerColor = MaterialTheme.colorScheme.background,
        snackbarHost = { SnackbarHost(snackbar) },
        topBar = { ArnTopBar(current?.let { ReceiptNumber.format(it) }.orEmpty(), onBack) },
    ) { padding ->
        if (current == null) return@Scaffold
        val void = current.status == TxStatus.VOID
        val cogs = items.sumOf { it.hppTotal }
        Column(
            Modifier.fillMaxSize().padding(padding).verticalScroll(rememberScrollState()).padding(horizontal = Spacing.lg),
            verticalArrangement = Arrangement.spacedBy(Spacing.md),
        ) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text(fmt.dateTime(current.waktu), style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant, modifier = Modifier.weight(1f))
                if (void) StatusPill(stringResource(R.string.tx_void), Tone.BAD) else StatusPill(stringResource(R.string.tx_done), Tone.GOOD)
            }
            if (void) {
                Text(
                    stringResource(R.string.tx_void_note),
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
            ArnCard(Modifier.fillMaxWidth()) {
                Column {
                    items.forEach { item ->
                        Row(Modifier.fillMaxWidth().padding(vertical = 4.dp), verticalAlignment = Alignment.Top) {
                            Text("${item.kuantitas}×", style = MaterialTheme.typography.bodyMedium.merge(MoneyStyle), modifier = Modifier.width(32.dp))
                            Column(Modifier.weight(1f)) {
                                Text(item.namaMenu, style = MaterialTheme.typography.bodyMedium)
                                Text(
                                    "@ " + fmt.money(item.hargaSatuan),
                                    style = MaterialTheme.typography.bodySmall,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                                )
                                if (item.catatan.isNotBlank()) {
                                    Text(item.catatan, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                                }
                            }
                            MoneyText(item.hargaSatuan * item.kuantitas, style = MaterialTheme.typography.bodyMedium)
                        }
                    }
                    HorizontalDivider(Modifier.padding(vertical = Spacing.sm), color = MaterialTheme.colorScheme.outlineVariant)
                    LineRow(stringResource(R.string.cart_subtotal), current.subtotal)
                    if (current.diskon > 0) LineRow(stringResource(R.string.cart_discount), -current.diskon)
                    LineRow(stringResource(R.string.cart_total), current.total, bold = true)
                    HorizontalDivider(Modifier.padding(vertical = Spacing.sm), color = MaterialTheme.colorScheme.outlineVariant)
                    if (current.metodeBayar == PayMethod.CASH) {
                        LineRow(stringResource(R.string.pay_received), current.dibayar)
                        LineRow(stringResource(R.string.pay_change), current.kembalian)
                    } else {
                        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                            Text(stringResource(R.string.receipt_method), style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
                            Text(stringResource(R.string.pay_non_cash), style = MaterialTheme.typography.bodyMedium)
                        }
                    }
                }
            }
            if (!void) ArnCard(Modifier.fillMaxWidth()) {
                Column {
                    Text(stringResource(R.string.tx_profit_title), style = MaterialTheme.typography.titleSmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                    Spacer(Modifier.height(Spacing.sm))
                    LineRow(stringResource(R.string.income_cogs), cogs)
                    LineRow(stringResource(R.string.income_gross_profit), current.total - cogs, bold = true, negativeIsError = true)
                }
            }
            SecondaryButton(
                text = stringResource(R.string.success_share_text),
                onClick = {
                    scope.launch {
                        vm.receipt()?.let { ReceiptFormat.shareText(context, ReceiptFormat.text(context, fmt, it), shareTitle) }
                    }
                },
                modifier = Modifier.fillMaxWidth(),
                icon = Icons.Outlined.Share,
            )
            if (!void) {
                OutlinedButton(
                    onClick = { confirmVoid = true },
                    modifier = Modifier.fillMaxWidth().heightIn(min = 52.dp),
                    shape = MaterialTheme.shapes.medium,
                    border = androidx.compose.foundation.BorderStroke(1.dp, MaterialTheme.colorScheme.error),
                ) {
                    androidx.compose.material3.Icon(Icons.Outlined.Undo, contentDescription = null, tint = MaterialTheme.colorScheme.error)
                    Spacer(Modifier.width(Spacing.sm))
                    Text(stringResource(R.string.tx_void_action), color = MaterialTheme.colorScheme.error, style = MaterialTheme.typography.labelLarge)
                }
            }
            Spacer(Modifier.height(Spacing.xl).navigationBarsPadding())
        }
    }

    if (confirmVoid) {
        ConfirmDialog(
            title = stringResource(R.string.tx_void_title),
            text = stringResource(R.string.tx_void_body),
            confirmLabel = stringResource(R.string.tx_void_confirm),
            dismissLabel = stringResource(R.string.action_cancel),
            destructive = true,
            onDismiss = { confirmVoid = false },
            onConfirm = {
                confirmVoid = false
                vm.void { r ->
                    scope.launch {
                        when (r) {
                            is Op.Ok -> snackbar.showSnackbar(resources.getString(R.string.tx_void_done))
                            is Op.Fail -> snackbar.showSnackbar(
                                if (r.error == com.example.kasirarn.data.repo.AppError.ALREADY_VOID) resources.getString(R.string.err_already_void)
                                else resources.getString(R.string.err_storage),
                            )
                        }
                    }
                }
            },
        )
    }
}
