package com.example.kasirarn.ui.components

import android.graphics.BitmapFactory
import android.util.LruCache
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.statusBars
import androidx.compose.foundation.layout.windowInsetsPadding
import androidx.compose.foundation.layout.RowScope
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.outlined.ArrowBack
import androidx.compose.material.icons.outlined.Add
import androidx.compose.material.icons.outlined.Delete
import androidx.compose.material.icons.outlined.Remove
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilledTonalButton
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.FilledTonalIconButton
import androidx.compose.material3.IconButtonDefaults
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.SegmentedButton
import androidx.compose.material3.Surface
import androidx.compose.material3.SegmentedButtonDefaults
import androidx.compose.material3.SingleChoiceSegmentedButtonRow
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.compositionLocalOf
import androidx.compose.runtime.getValue
import androidx.compose.runtime.produceState
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.kasirarn.R
import com.example.kasirarn.data.repo.AppError
import com.example.kasirarn.data.repo.ImageStore
import com.example.kasirarn.data.repo.Op
import com.example.kasirarn.domain.DecimalParser
import com.example.kasirarn.domain.MoneyParser
import com.example.kasirarn.ui.format.LocalFmt
import com.example.kasirarn.ui.nav.rememberWindowInfo
import com.example.kasirarn.ui.theme.MoneyStyle
import com.example.kasirarn.ui.theme.Spacing
import com.example.kasirarn.ui.theme.isDarkScheme
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

// ---------- Bilah atas ----------

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ArnTopBar(
    title: String,
    onBack: (() -> Unit)?,
    modifier: Modifier = Modifier,
    actions: @Composable RowScope.() -> Unit = {},
) {
    TopAppBar(
        title = { Text(title, maxLines = 1, overflow = TextOverflow.Ellipsis, style = MaterialTheme.typography.titleLarge) },
        modifier = modifier,
        navigationIcon = {
            if (onBack != null) {
                IconButton(onClick = onBack) {
                    Icon(Icons.AutoMirrored.Outlined.ArrowBack, contentDescription = stringResource(R.string.action_back))
                }
            }
        },
        actions = actions,
        colors = TopAppBarDefaults.topAppBarColors(
            containerColor = MaterialTheme.colorScheme.background,
            scrolledContainerColor = MaterialTheme.colorScheme.background,
        ),
    )
}

/**
 * Kepala layar mode utama: judul, aksi, dan baris tab. Pada layar pendek (ponsel lanskap) judul dan tab
 * digabung dalam satu baris supaya ruang vertikal tidak habis untuk kepala layar.
 */
@Composable
fun ScreenHeader(
    title: String,
    actions: @Composable RowScope.() -> Unit = {},
    tabs: @Composable (Modifier) -> Unit,
) {
    if (rememberWindowInfo().isShort) {
        Row(
            Modifier
                .fillMaxWidth()
                .background(MaterialTheme.colorScheme.background)
                .windowInsetsPadding(WindowInsets.statusBars),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            tabs(Modifier.weight(1f))
            actions()
        }
    } else {
        Column {
            ArnTopBar(title = title, onBack = null, actions = actions)
            tabs(Modifier)
        }
    }
}

// ---------- Kartu dan bagian ----------

@Composable
fun ArnCard(
    modifier: Modifier = Modifier,
    onClick: (() -> Unit)? = null,
    contentPadding: PaddingValues = PaddingValues(Spacing.lg),
    container: Color = Color.Unspecified,
    content: @Composable () -> Unit,
) {
    val scheme = MaterialTheme.colorScheme
    val dark = isDarkScheme()
    // Terang: putih di atas krem dengan garis rambut. Gelap: lapisan tonal tanpa garis.
    val fill = if (container != Color.Unspecified) container else if (dark) scheme.surfaceContainerLow else scheme.surfaceContainerLowest
    val colors = CardDefaults.cardColors(containerColor = fill)
    val border = if (dark || container != Color.Unspecified) null else BorderStroke(1.dp, scheme.outlineVariant.copy(alpha = 0.7f))
    if (onClick != null) {
        Card(onClick = onClick, modifier = modifier, colors = colors, border = border, shape = MaterialTheme.shapes.medium) {
            Box(Modifier.padding(contentPadding)) { content() }
        }
    } else {
        Card(modifier = modifier, colors = colors, border = border, shape = MaterialTheme.shapes.medium) {
            Box(Modifier.padding(contentPadding)) { content() }
        }
    }
}

@Composable
fun SectionLabel(text: String, modifier: Modifier = Modifier, trailing: @Composable () -> Unit = {}) {
    Row(
        modifier = modifier.fillMaxWidth().padding(top = Spacing.lg, bottom = Spacing.sm),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.SpaceBetween,
    ) {
        Text(
            text,
            style = MaterialTheme.typography.titleSmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            modifier = Modifier.weight(1f),
        )
        trailing()
    }
}

@Composable
fun EmptyState(
    icon: ImageVector,
    title: String,
    body: String,
    modifier: Modifier = Modifier,
    actionLabel: String? = null,
    onAction: (() -> Unit)? = null,
) {
    Column(
        modifier = modifier.fillMaxWidth().padding(horizontal = Spacing.xl, vertical = Spacing.xxl),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.Center,
    ) {
        Box(
            Modifier.size(64.dp).clip(CircleShape).background(MaterialTheme.colorScheme.primaryContainer),
            contentAlignment = Alignment.Center,
        ) {
            Icon(icon, contentDescription = null, tint = MaterialTheme.colorScheme.onPrimaryContainer, modifier = Modifier.size(28.dp))
        }
        Spacer(Modifier.height(Spacing.lg))
        Text(title, style = MaterialTheme.typography.titleMedium, textAlign = TextAlign.Center)
        Spacer(Modifier.height(Spacing.xs))
        Text(
            body,
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            textAlign = TextAlign.Center,
        )
        if (actionLabel != null && onAction != null) {
            Spacer(Modifier.height(Spacing.lg))
            FilledTonalButton(onClick = onAction, modifier = Modifier.height(Spacing.minTouch)) { Text(actionLabel) }
        }
    }
}

enum class Tone { GOOD, WARN, BAD, NEUTRAL }

@Composable
fun StatusPill(text: String, tone: Tone, modifier: Modifier = Modifier) {
    val scheme = MaterialTheme.colorScheme
    val (bg, fg) = when (tone) {
        Tone.GOOD -> scheme.secondaryContainer to scheme.onSecondaryContainer
        Tone.WARN -> scheme.tertiaryContainer to scheme.onTertiaryContainer
        Tone.BAD -> scheme.errorContainer to scheme.onErrorContainer
        Tone.NEUTRAL -> scheme.surfaceContainerHigh to scheme.onSurfaceVariant
    }
    Box(
        modifier = modifier.clip(RoundedCornerShape(50)).background(bg).padding(horizontal = Spacing.md, vertical = Spacing.xs),
    ) {
        Text(text, style = MaterialTheme.typography.labelMedium, color = fg, maxLines = 1)
    }
}

@Composable
fun ConfirmDialog(
    title: String,
    text: String,
    confirmLabel: String,
    dismissLabel: String,
    onConfirm: () -> Unit,
    onDismiss: () -> Unit,
    destructive: Boolean = false,
) {
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(title) },
        text = { Text(text) },
        confirmButton = {
            TextButton(onClick = onConfirm) {
                Text(
                    confirmLabel,
                    color = if (destructive) MaterialTheme.colorScheme.error else MaterialTheme.colorScheme.primary,
                )
            }
        },
        dismissButton = { TextButton(onClick = onDismiss) { Text(dismissLabel) } },
        containerColor = MaterialTheme.colorScheme.surfaceContainerHigh,
    )
}

// ---------- Tombol ----------

@Composable
fun PrimaryButton(
    text: String,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    enabled: Boolean = true,
    icon: ImageVector? = null,
) {
    Button(
        onClick = onClick,
        enabled = enabled,
        modifier = modifier.heightIn(min = 52.dp),
        shape = MaterialTheme.shapes.medium,
        colors = ButtonDefaults.buttonColors(),
    ) {
        if (icon != null) {
            Icon(icon, contentDescription = null, modifier = Modifier.size(20.dp))
            Spacer(Modifier.width(Spacing.sm))
        }
        Text(text, style = MaterialTheme.typography.labelLarge)
    }
}

@Composable
fun SecondaryButton(
    text: String,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    enabled: Boolean = true,
    icon: ImageVector? = null,
) {
    FilledTonalButton(
        onClick = onClick,
        enabled = enabled,
        modifier = modifier.heightIn(min = 52.dp),
        shape = MaterialTheme.shapes.medium,
        colors = ButtonDefaults.filledTonalButtonColors(
            containerColor = MaterialTheme.colorScheme.secondaryContainer,
            contentColor = MaterialTheme.colorScheme.onSecondaryContainer,
        ),
    ) {
        if (icon != null) {
            Icon(icon, contentDescription = null, modifier = Modifier.size(20.dp))
            Spacer(Modifier.width(Spacing.sm))
        }
        Text(text, style = MaterialTheme.typography.labelLarge)
    }
}

// ---------- Isian ----------

@Composable
fun ArnField(
    value: String,
    onValueChange: (String) -> Unit,
    label: String,
    modifier: Modifier = Modifier,
    error: String? = null,
    supporting: String? = null,
    prefix: String? = null,
    suffix: String? = null,
    singleLine: Boolean = true,
    minLines: Int = 1,
    enabled: Boolean = true,
    keyboardType: KeyboardType = KeyboardType.Text,
    capitalization: androidx.compose.ui.text.input.KeyboardCapitalization = androidx.compose.ui.text.input.KeyboardCapitalization.Sentences,
    imeAction: androidx.compose.ui.text.input.ImeAction = androidx.compose.ui.text.input.ImeAction.Next,
    trailing: @Composable (() -> Unit)? = null,
) {
    OutlinedTextField(
        value = value,
        onValueChange = onValueChange,
        label = { Text(label) },
        modifier = modifier.fillMaxWidth(),
        enabled = enabled,
        singleLine = singleLine,
        minLines = minLines,
        isError = error != null,
        prefix = prefix?.let { { Text(it) } },
        suffix = suffix?.let { { Text(it) } },
        trailingIcon = trailing,
        supportingText = (error ?: supporting)?.let { { Text(it) } },
        keyboardOptions = KeyboardOptions(keyboardType = keyboardType, capitalization = capitalization, imeAction = imeAction),
        shape = MaterialTheme.shapes.medium,
    )
}

/** Isian uang bulat: pemisah ribuan ditambahkan otomatis. Nilai yang dikelola pemanggil adalah teks berformat. */
@Composable
fun MoneyField(
    value: String,
    onValueChange: (String) -> Unit,
    label: String,
    modifier: Modifier = Modifier,
    error: String? = null,
    supporting: String? = null,
    imeAction: androidx.compose.ui.text.input.ImeAction = androidx.compose.ui.text.input.ImeAction.Next,
) {
    val fmt = LocalFmt.current
    ArnField(
        value = value,
        onValueChange = { raw ->
            val digits = raw.filter { it.isDigit() }.take(12)
            onValueChange(if (digits.isEmpty()) "" else fmt.number(digits.toLong()))
        },
        label = label,
        modifier = modifier,
        error = error,
        supporting = supporting,
        prefix = fmt.currency + " ",
        keyboardType = KeyboardType.Number,
        imeAction = imeAction,
    )
}

/** Isian angka desimal (koma atau titik). Karakter selain angka dan satu pemisah ditolak. */
@Composable
fun DecimalField(
    value: String,
    onValueChange: (String) -> Unit,
    label: String,
    modifier: Modifier = Modifier,
    error: String? = null,
    supporting: String? = null,
    suffix: String? = null,
    imeAction: androidx.compose.ui.text.input.ImeAction = androidx.compose.ui.text.input.ImeAction.Next,
) {
    ArnField(
        value = value,
        onValueChange = { raw ->
            val cleaned = StringBuilder()
            var sep = false
            for (c in raw) {
                when {
                    c.isDigit() -> cleaned.append(c)
                    (c == ',' || c == '.') && !sep -> {
                        sep = true
                        cleaned.append(c)
                    }
                }
            }
            onValueChange(cleaned.toString().take(14))
        },
        label = label,
        modifier = modifier,
        error = error,
        supporting = supporting,
        suffix = suffix,
        keyboardType = KeyboardType.Decimal,
        imeAction = imeAction,
    )
}

fun parseMoney(text: String): Long? = MoneyParser.parse(text)
fun parseDecimal(text: String) = DecimalParser.parse(text)

// ---------- Pilihan ----------

@Composable
fun <T> SegmentedChoice(
    options: List<Pair<T, String>>,
    selected: T,
    onSelect: (T) -> Unit,
    modifier: Modifier = Modifier,
    enabled: Boolean = true,
) {
    SingleChoiceSegmentedButtonRow(modifier = modifier.fillMaxWidth()) {
        options.forEachIndexed { index, (value, label) ->
            SegmentedButton(
                selected = value == selected,
                onClick = { onSelect(value) },
                shape = SegmentedButtonDefaults.itemShape(index, options.size),
                modifier = Modifier.heightIn(min = 48.dp),
                enabled = enabled,
                label = { Text(label, maxLines = 2, overflow = TextOverflow.Ellipsis, textAlign = TextAlign.Center) },
            )
        }
    }
}

// ---------- Uang ----------

@Composable
fun MoneyText(
    value: Long,
    modifier: Modifier = Modifier,
    style: androidx.compose.ui.text.TextStyle = MaterialTheme.typography.bodyMedium,
    color: Color = Color.Unspecified,
    fontWeight: FontWeight? = null,
    /** Kecilkan huruf otomatis (sampai 60%) bila angka tidak muat, supaya tidak terpotong. */
    shrinkToFit: Boolean = false,
) {
    val merged = style.merge(MoneyStyle).let { if (fontWeight != null) it.copy(fontWeight = fontWeight) else it }
    if (shrinkToFit && merged.fontSize.isSp) {
        androidx.compose.material3.Text(
            LocalFmt.current.money(value),
            modifier = modifier,
            style = merged,
            color = color,
            maxLines = 1,
            autoSize = androidx.compose.foundation.text.TextAutoSize.StepBased(
                minFontSize = (merged.fontSize.value * 0.6f).sp,
                maxFontSize = merged.fontSize,
            ),
        )
    } else {
        Text(LocalFmt.current.money(value), modifier = modifier, style = merged, color = color, maxLines = 1)
    }
}

// ---------- Gambar menu ----------

val LocalImageStore = compositionLocalOf<ImageStore?> { null }

private object ImageCache {
    private val cache = object : LruCache<String, ImageBitmap>(24 * 1024) {
        override fun sizeOf(key: String, value: ImageBitmap): Int = value.width * value.height * 4 / 1024
    }
    fun get(key: String): ImageBitmap? = cache.get(key)
    fun put(key: String, value: ImageBitmap) { cache.put(key, value) }
}

@Composable
fun rememberLocalImage(fileName: String?, maxPx: Int = 512): ImageBitmap? {
    val store = LocalImageStore.current
    val state by produceState<ImageBitmap?>(initialValue = fileName?.let { ImageCache.get(it) }, fileName, store) {
        if (fileName == null || store == null) {
            value = null
            return@produceState
        }
        ImageCache.get(fileName)?.let {
            value = it
            return@produceState
        }
        value = withContext(Dispatchers.IO) {
            val file = store.file(fileName) ?: return@withContext null
            val bounds = BitmapFactory.Options().apply { inJustDecodeBounds = true }
            BitmapFactory.decodeFile(file.absolutePath, bounds)
            var sample = 1
            while (bounds.outWidth / (sample * 2) >= maxPx && bounds.outHeight / (sample * 2) >= maxPx) sample *= 2
            val bmp = BitmapFactory.decodeFile(file.absolutePath, BitmapFactory.Options().apply { inSampleSize = sample })
            bmp?.asImageBitmap()?.also { ImageCache.put(fileName, it) }
        }
    }
    return state
}

/** Foto bila ada, selain itu tile huruf awal dengan warna stabil dari nama. */
@Composable
fun MenuImage(
    photo: String?,
    name: String,
    modifier: Modifier = Modifier,
    shape: androidx.compose.ui.graphics.Shape = RoundedCornerShape(0.dp),
    textSize: androidx.compose.ui.unit.TextUnit = androidx.compose.ui.unit.TextUnit.Unspecified,
) {
    val image = rememberLocalImage(photo)
    Box(modifier = modifier.clip(shape), contentAlignment = Alignment.Center) {
        if (image != null) {
            Image(
                bitmap = image,
                contentDescription = name,
                contentScale = ContentScale.Crop,
                modifier = Modifier.fillMaxSize(),
            )
        } else {
            val (bg, fg) = monogramColors(Math.floorMod(name.hashCode(), 5))
            Box(Modifier.fillMaxSize().background(bg), contentAlignment = Alignment.Center) {
                Text(
                    initials(name),
                    color = fg,
                    style = MaterialTheme.typography.headlineSmall,
                    fontSize = textSize,
                    modifier = Modifier.semantics { contentDescription = name },
                )
            }
        }
    }
}

/** Pasangan warna latar dan huruf untuk tile tanpa foto; diambil dari skema aktif supaya serasi di terang, gelap, dan warna dinamis. */
@Composable
fun monogramColors(index: Int): Pair<Color, Color> {
    val c = MaterialTheme.colorScheme
    return when (index) {
        0 -> c.primaryContainer to c.onPrimaryContainer
        1 -> c.secondaryContainer to c.onSecondaryContainer
        2 -> c.tertiaryContainer to c.onTertiaryContainer
        3 -> c.surfaceContainerHighest to c.onSurface
        else -> c.errorContainer.copy(alpha = 0.7f) to c.onErrorContainer
    }
}

fun initials(name: String): String {
    val words = name.trim().split(Regex("\\s+")).filter { it.isNotEmpty() }
    return when {
        words.isEmpty() -> "?"
        words.size == 1 -> words[0].take(2).uppercase()
        else -> (words[0].take(1) + words[1].take(1)).uppercase()
    }
}

// ---------- Pesan galat ----------

@Composable
fun errorMessage(fail: Op.Fail): String = when (fail.error) {
    AppError.NAME_EMPTY -> stringResource(R.string.err_name_empty)
    AppError.NAME_DUPLICATE -> stringResource(R.string.err_name_duplicate)
    AppError.INVALID_NUMBER -> stringResource(R.string.err_invalid_number)
    AppError.PRICE_INVALID -> stringResource(R.string.err_price_invalid)
    AppError.NOT_FOUND -> stringResource(R.string.err_not_found)
    AppError.IN_USE -> stringResource(R.string.err_in_use)
    AppError.EMPTY_CART -> stringResource(R.string.err_empty_cart)
    AppError.INSUFFICIENT_STOCK -> stringResource(R.string.err_insufficient_stock, fail.detail.orEmpty())
    AppError.PAYMENT_SHORT -> stringResource(R.string.err_payment_short)
    AppError.ALREADY_VOID -> stringResource(R.string.err_already_void)
    AppError.RESERVED -> stringResource(R.string.err_reserved)
    AppError.STORAGE -> stringResource(R.string.err_storage)
    AppError.UNKNOWN -> stringResource(R.string.err_unknown)
}

/** Ukuran kotak ikon bulat yang dipakai di beberapa daftar. */
val IconBoxSize: Dp = 40.dp

/** Pengatur jumlah berbentuk pil: kurangi (atau hapus bila tinggal satu), jumlah, tambah. Target sentuh 40 dp. */
@Composable
fun QuantityStepper(
    quantity: Int,
    onMinus: () -> Unit,
    onPlus: () -> Unit,
    modifier: Modifier = Modifier,
    minusIsDelete: Boolean = false,
    /** Tombol 36 dp untuk ruang sempit (kartu menu); standar 40 dp. */
    compact: Boolean = false,
) {
    val scheme = MaterialTheme.colorScheme
    val button = if (compact) 36.dp else 40.dp
    Surface(modifier = modifier, shape = RoundedCornerShape(50), color = scheme.primaryContainer, contentColor = scheme.onPrimaryContainer) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            IconButton(onClick = onMinus, modifier = Modifier.size(button)) {
                Icon(
                    if (minusIsDelete) Icons.Outlined.Delete else Icons.Outlined.Remove,
                    contentDescription = stringResource(if (minusIsDelete) R.string.cart_remove else R.string.cart_decrease),
                    modifier = Modifier.size(18.dp),
                )
            }
            Text(
                quantity.toString(),
                style = MaterialTheme.typography.titleSmall.merge(MoneyStyle),
                modifier = Modifier.widthIn(min = if (compact) 20.dp else 24.dp),
                textAlign = TextAlign.Center,
                maxLines = 1,
            )
            IconButton(onClick = onPlus, modifier = Modifier.size(button)) {
                Icon(
                    Icons.Outlined.Add,
                    contentDescription = stringResource(R.string.cart_increase),
                    modifier = Modifier.size(18.dp),
                )
            }
        }
    }
}

/** Tombol ikon bulat bernuansa tonal untuk aksi di bilah atas (notifikasi, profil). */
@Composable
fun TonalIconAction(
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    content: @Composable () -> Unit,
) {
    FilledTonalIconButton(
        onClick = onClick,
        modifier = modifier.size(44.dp),
        colors = IconButtonDefaults.filledTonalIconButtonColors(
            containerColor = MaterialTheme.colorScheme.surfaceContainerHigh,
            contentColor = MaterialTheme.colorScheme.onSurface,
        ),
    ) { content() }
}
