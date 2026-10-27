package com.example.kasirarn.ui.home

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
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
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.Check
import androidx.compose.material.icons.outlined.Image
import androidx.compose.material.icons.outlined.Share
import androidx.compose.material.icons.outlined.ShoppingBag
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.lifecycle.ViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewModelScope
import com.example.kasirarn.AppContainer
import com.example.kasirarn.R
import com.example.kasirarn.data.db.PayMethod
import com.example.kasirarn.data.repo.Op
import com.example.kasirarn.data.repo.CheckoutRequest
import com.example.kasirarn.data.repo.Receipt
import com.example.kasirarn.data.repo.ReceiptNumber
import com.example.kasirarn.domain.CartLine
import com.example.kasirarn.ui.components.ArnCard
import com.example.kasirarn.ui.components.ArnTopBar
import com.example.kasirarn.ui.components.ChoiceChips
import com.example.kasirarn.ui.components.EmptyState
import com.example.kasirarn.ui.components.MoneyField
import com.example.kasirarn.ui.components.MoneyText
import com.example.kasirarn.ui.components.PrimaryButton
import com.example.kasirarn.ui.components.SecondaryButton
import com.example.kasirarn.ui.components.SegmentedChoice
import com.example.kasirarn.ui.components.errorMessage
import com.example.kasirarn.ui.components.parseMoney
import com.example.kasirarn.ui.format.LocalFmt
import com.example.kasirarn.ui.nav.containerViewModel
import com.example.kasirarn.ui.nav.rememberWindowInfo
import com.example.kasirarn.ui.theme.MoneyStyle
import com.example.kasirarn.ui.theme.Spacing
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch

class PaymentViewModel(private val c: AppContainer) : ViewModel() {
    val cart: StateFlow<CartUi> = combine(c.cart.lines, c.cart.discount, c.menus.observeCatalog()) { lines, discount, catalog ->
        val byId = catalog.associateBy { it.menu.id }
        CartUi(
            lines = lines.mapNotNull { l ->
                byId[l.menuId]?.let { CartLineUi(l.menuId, it.menu.nama, it.menu.fotoPath, it.menu.hargaJual, l.quantity, l.note) }
            },
            discount = discount,
        )
    }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), CartUi())

    var method by mutableStateOf(PayMethod.CASH)
    var cashText by mutableStateOf("")
    var submitting by mutableStateOf(false)
        private set
    var error by mutableStateOf<Op.Fail?>(null)
        private set

    fun clearError() { error = null }

    fun pay(onDone: (Long) -> Unit) {
        if (submitting) return
        val cartUi = cart.value
        val lines = c.cart.lines.value
        submitting = true
        viewModelScope.launch {
            val result = c.sales.checkout(
                CheckoutRequest(
                    lines = lines.map { CartLine(it.menuId, it.quantity, it.note) },
                    discount = c.cart.discount.value,
                    method = method,
                    paid = if (method == PayMethod.CASH) parseMoney(cashText) ?: 0 else cartUi.total,
                ),
            )
            submitting = false
            when (result) {
                is Op.Ok -> {
                    c.cart.clear()
                    onDone(result.value)
                }
                is Op.Fail -> error = result
            }
        }
    }
}

/** Pecahan uang yang wajar di atas [total]: dibulatkan naik ke kelipatan 5 ribu sampai 100 ribu. */
fun suggestCash(total: Long): List<Long> {
    val steps = listOf(5_000L, 10_000L, 20_000L, 50_000L, 100_000L)
    return steps.map { step -> ((total + step - 1) / step) * step }
        .filter { it > total }
        .distinct()
        .sorted()
        .take(3)
}

@Composable
fun PaymentScreen(onBack: () -> Unit, onPaid: (Long) -> Unit) {
    val vm = containerViewModel { PaymentViewModel(it) }
    val cart by vm.cart.collectAsStateWithLifecycle()
    val fmt = LocalFmt.current
    val snackbar = remember { SnackbarHostState() }
    val failure = vm.error
    val failureText = failure?.let { errorMessage(it) }
    LaunchedEffect(failure) {
        if (failureText != null) {
            snackbar.showSnackbar(failureText)
            vm.clearError()
        }
    }
    val total = cart.total
    val paid = parseMoney(vm.cashText) ?: 0L
    val cash = vm.method == PayMethod.CASH
    val enough = !cash || paid >= total
    val canPay = cart.lines.isNotEmpty() && enough && !vm.submitting

    Scaffold(
        containerColor = MaterialTheme.colorScheme.background,
        snackbarHost = { SnackbarHost(snackbar) },
        topBar = { ArnTopBar(stringResource(R.string.pay_title), onBack) },
        bottomBar = {
            Surface(color = MaterialTheme.colorScheme.background) {
                PrimaryButton(
                    text = stringResource(R.string.pay_confirm),
                    onClick = { vm.pay(onPaid) },
                    enabled = canPay,
                    trailingIcon = Icons.Outlined.Check,
                    modifier = Modifier.fillMaxWidth().navigationBarsPadding().imePadding().padding(Spacing.lg),
                )
            }
        },
    ) { padding ->
        if (cart.lines.isEmpty()) {
            Column(Modifier.fillMaxSize().padding(padding), verticalArrangement = Arrangement.Center) {
                EmptyState(
                    icon = Icons.Outlined.ShoppingBag,
                    title = stringResource(R.string.pay_empty_title),
                    body = stringResource(R.string.pay_empty_body),
                    actionLabel = stringResource(R.string.pay_back_to_menu),
                    onAction = onBack,
                )
            }
            return@Scaffold
        }
        val wide = rememberWindowInfo().isWide
        val summaryBlock: @Composable () -> Unit = {
            ArnCard(Modifier.fillMaxWidth()) {
                Column {
                    cart.lines.forEach { line ->
                        Row(Modifier.fillMaxWidth().padding(vertical = 3.dp), verticalAlignment = Alignment.Top) {
                            Text("${line.quantity}×", style = MaterialTheme.typography.bodyMedium.merge(MoneyStyle), modifier = Modifier.widthIn(min = 32.dp).padding(end = Spacing.xs), softWrap = false)
                            Column(Modifier.weight(1f)) {
                                Text(line.name, style = MaterialTheme.typography.bodyMedium)
                                if (line.note.isNotBlank()) {
                                    Text(line.note, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                                }
                            }
                            MoneyText(line.lineTotal, style = MaterialTheme.typography.bodyMedium)
                        }
                    }
                    HorizontalDivider(Modifier.padding(vertical = Spacing.md), color = MaterialTheme.colorScheme.outlineVariant)
                    if (cart.discountAmount > 0) {
                        TotalsRow(stringResource(R.string.cart_subtotal), fmt.money(cart.subtotal))
                        TotalsRow(stringResource(R.string.cart_discount), "-" + fmt.money(cart.discountAmount))
                        Spacer(Modifier.height(Spacing.xs))
                    }
                    Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween, verticalAlignment = Alignment.CenterVertically) {
                        Text(stringResource(R.string.cart_total), style = MaterialTheme.typography.titleMedium.copy(fontWeight = androidx.compose.ui.text.font.FontWeight.ExtraBold))
                        MoneyText(total, style = MaterialTheme.typography.headlineMedium, color = MaterialTheme.colorScheme.primary, fontWeight = androidx.compose.ui.text.font.FontWeight.ExtraBold)
                    }
                }
            }

        }
        val controlsBlock: @Composable () -> Unit = {
            Column(verticalArrangement = Arrangement.spacedBy(Spacing.lg)) {
            Column(verticalArrangement = Arrangement.spacedBy(Spacing.sm)) {
                Text(stringResource(R.string.pay_method), style = MaterialTheme.typography.titleSmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                SegmentedChoice(
                    options = listOf(
                        PayMethod.CASH to stringResource(R.string.pay_cash),
                        PayMethod.NON_CASH to stringResource(R.string.pay_non_cash),
                    ),
                    selected = vm.method,
                    onSelect = { vm.method = it },
                )
            }

            if (cash) {
                MoneyField(
                    value = vm.cashText,
                    onValueChange = { vm.cashText = it },
                    label = stringResource(R.string.pay_received),
                    imeAction = androidx.compose.ui.text.input.ImeAction.Done,
                )
                val options = buildList {
                    add(total to stringResource(R.string.pay_exact))
                    suggestCash(total).forEach { add(it to fmt.number(it)) }
                }
                ChoiceChips(
                    options = options,
                    selected = paid.takeIf { p -> options.any { it.first == p } },
                    onSelect = { vm.cashText = fmt.number(it) },
                )
                ChangeCard(total = total, paid = paid)
            } else {
                ArnCard(Modifier.fillMaxWidth()) {
                    Text(
                        stringResource(R.string.pay_non_cash_hint),
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
            }
            }
        }
        if (wide) {
            // Tablet dan lanskap: ringkasan di kiri, cara bayar di kanan, tanpa menggulir bersama.
            Row(
                Modifier.fillMaxSize().padding(padding).padding(horizontal = Spacing.lg),
                horizontalArrangement = Arrangement.spacedBy(Spacing.xl),
            ) {
                Column(Modifier.weight(1f).verticalScroll(rememberScrollState())) { summaryBlock() }
                Column(Modifier.weight(1f).verticalScroll(rememberScrollState())) { controlsBlock() }
            }
        } else {
            Column(
                Modifier.fillMaxSize().padding(padding).verticalScroll(rememberScrollState()).padding(horizontal = Spacing.lg),
                verticalArrangement = Arrangement.spacedBy(Spacing.lg),
            ) {
                summaryBlock()
                controlsBlock()
                Spacer(Modifier.height(Spacing.md))
            }
        }
    }
}

@Composable
private fun TotalsRow(label: String, value: String) {
    Row(Modifier.fillMaxWidth().padding(vertical = 2.dp), horizontalArrangement = Arrangement.SpaceBetween) {
        Text(label, style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
        Text(value, style = MaterialTheme.typography.bodyMedium.merge(MoneyStyle))
    }
}

@Composable
private fun ChangeCard(total: Long, paid: Long) {
    val fmt = LocalFmt.current
    val scheme = MaterialTheme.colorScheme
    val enough = paid >= total
    val untouched = paid == 0L
    val bg = when {
        untouched -> scheme.surfaceContainerHigh
        enough -> scheme.secondaryContainer
        else -> scheme.errorContainer
    }
    val fg = when {
        untouched -> scheme.onSurfaceVariant
        enough -> scheme.onSecondaryContainer
        else -> scheme.onErrorContainer
    }
    Surface(shape = MaterialTheme.shapes.medium, color = bg, modifier = Modifier.fillMaxWidth()) {
        Row(Modifier.padding(Spacing.lg), verticalAlignment = Alignment.CenterVertically) {
            Text(
                stringResource(if (enough || untouched) R.string.pay_change else R.string.pay_short),
                style = MaterialTheme.typography.titleSmall,
                color = fg,
                modifier = Modifier.weight(1f),
            )
            Text(
                fmt.money(if (untouched) 0 else Math.abs(paid - total)),
                style = MaterialTheme.typography.headlineMedium.merge(MoneyStyle).copy(fontWeight = androidx.compose.ui.text.font.FontWeight.ExtraBold),
                color = fg,
            )
        }
    }
}

// ---------- Layar sukses ----------

class SuccessViewModel(private val c: AppContainer, private val id: Long) : ViewModel() {
    var receipt by mutableStateOf<Receipt?>(null)
        private set

    init {
        viewModelScope.launch { receipt = c.sales.receipt(id) }
    }
}

@Composable
fun SuccessScreen(id: Long, onNewTransaction: () -> Unit) {
    val vm = containerViewModel(key = "success-$id") { SuccessViewModel(it, id) }
    val receipt = vm.receipt
    val fmt = LocalFmt.current
    val context = LocalContext.current
    val shareTitle = stringResource(R.string.success_share_title)
    val scope = androidx.compose.runtime.rememberCoroutineScope()

    androidx.activity.compose.BackHandler(onBack = onNewTransaction)

    Scaffold(containerColor = MaterialTheme.colorScheme.background) { padding ->
        if (receipt == null) return@Scaffold
        val tx = receipt.transaction
        val cash = tx.metodeBayar == PayMethod.CASH
        Column(
            Modifier.fillMaxSize().padding(padding).verticalScroll(rememberScrollState()).padding(horizontal = Spacing.lg),
            horizontalAlignment = Alignment.CenterHorizontally,
        ) {
            Spacer(Modifier.height(Spacing.xxl))
            Box(
                Modifier.size(72.dp).clip(CircleShape).background(MaterialTheme.colorScheme.secondary),
                contentAlignment = Alignment.Center,
            ) {
                Icon(Icons.Outlined.Check, contentDescription = null, tint = MaterialTheme.colorScheme.onSecondary, modifier = Modifier.size(40.dp))
            }
            Spacer(Modifier.height(Spacing.lg))
            Text(stringResource(R.string.success_title), style = MaterialTheme.typography.headlineSmall)
            Text(
                ReceiptNumber.format(tx) + " · " + fmt.time(tx.waktu),
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
            Spacer(Modifier.height(Spacing.xl))
            if (cash) {
                Text(stringResource(R.string.pay_change), style = MaterialTheme.typography.titleSmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                Text(
                    fmt.money(tx.kembalian),
                    style = MaterialTheme.typography.displaySmall.merge(MoneyStyle),
                    color = MaterialTheme.colorScheme.primary,
                    textAlign = TextAlign.Center,
                )
                Spacer(Modifier.height(Spacing.lg))
            }
            ArnCard(Modifier.fillMaxWidth()) {
                Column {
                    receipt.items.forEach { item ->
                        Row(Modifier.fillMaxWidth().padding(vertical = 2.dp)) {
                            Text("${item.kuantitas}× ${item.namaMenu}", style = MaterialTheme.typography.bodyMedium, modifier = Modifier.weight(1f))
                            MoneyText(item.hargaSatuan * item.kuantitas, style = MaterialTheme.typography.bodyMedium)
                        }
                    }
                    HorizontalDivider(Modifier.padding(vertical = Spacing.sm), color = MaterialTheme.colorScheme.outlineVariant)
                    Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                        Text(stringResource(R.string.cart_total), style = MaterialTheme.typography.titleSmall)
                        MoneyText(tx.total, style = MaterialTheme.typography.titleSmall)
                    }
                    if (cash) {
                        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                            Text(stringResource(R.string.pay_received), style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
                            MoneyText(tx.dibayar, style = MaterialTheme.typography.bodyMedium)
                        }
                    } else {
                        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                            Text(stringResource(R.string.receipt_method), style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
                            Text(stringResource(R.string.pay_non_cash), style = MaterialTheme.typography.bodyMedium)
                        }
                    }
                }
            }
            Spacer(Modifier.height(Spacing.lg))
            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(Spacing.md)) {
                SecondaryButton(
                    stringResource(R.string.success_share_text),
                    onClick = { ReceiptFormat.shareText(context, ReceiptFormat.text(context, fmt, receipt), shareTitle) },
                    modifier = Modifier.weight(1f),
                    icon = Icons.Outlined.Share,
                )
                SecondaryButton(
                    stringResource(R.string.success_share_image),
                    onClick = { scope.launch { ReceiptFormat.shareImage(context, fmt, receipt, shareTitle) } },
                    modifier = Modifier.weight(1f),
                    icon = Icons.Outlined.Image,
                )
            }
            Spacer(Modifier.height(Spacing.md))
            PrimaryButton(
                stringResource(R.string.success_new),
                onClick = onNewTransaction,
                modifier = Modifier.fillMaxWidth().navigationBarsPadding(),
            )
            Spacer(Modifier.height(Spacing.lg))
        }
    }
}
