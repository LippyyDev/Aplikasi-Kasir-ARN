package com.example.kasirarn.ui.onboarding

import androidx.activity.compose.BackHandler
import androidx.annotation.StringRes
import androidx.compose.animation.core.animateRectAsState
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyListState
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.relocation.BringIntoViewRequester
import androidx.compose.foundation.relocation.bringIntoViewRequester
import androidx.compose.material3.MaterialTheme
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.size
import androidx.compose.material.icons.automirrored.outlined.ArrowForward
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateMapOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.runtime.snapshots.SnapshotStateMap
import androidx.compose.runtime.staticCompositionLocalOf
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.composed
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Rect
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.BlendMode
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.CompositingStrategy
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.boundsInWindow
import androidx.compose.ui.layout.onGloballyPositioned
import androidx.compose.ui.layout.onSizeChanged
import androidx.compose.ui.layout.positionInWindow
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.IntSize
import androidx.compose.ui.unit.dp
import com.example.kasirarn.R
import com.example.kasirarn.ui.components.PrimaryButton
import com.example.kasirarn.ui.nav.Routes
import com.example.kasirarn.ui.theme.Spacing
import kotlinx.coroutines.delay
import kotlin.math.roundToInt

/** Bagian aplikasi yang bisa disorot oleh tutorial. */
enum class CoachId {
    // Bilah navigasi
    TAB_DASHBOARD, TAB_CASHIER, TAB_MANAGE, TAB_REPORTS, TAB_MORE,
    // Kasir
    HOME_SEARCH, HOME_CATEGORIES, HOME_MENU, HOME_CART, HOME_BELL, HOME_SAMPLE,
    // Kelola
    MANAGE_FAB,
    // Editor menu
    MENU_PHOTO, MENU_NAME, MENU_PRICE, MENU_CATEGORY, MENU_RECIPE, MENU_SAVE,
    // Editor bahan
    ING_NAME, ING_TYPE, ING_UNIT, ING_MIN, ING_INITIAL, ING_MEASURE, ING_SAVE,
    // Laporan
    REPORT_TABS, REPORT_PERIOD, REPORT_SUMMARY, EXPENSE_FAB, EXPENSE_CATEGORIES,
    // Notifikasi dan cadangan
    NOTIF_SETTINGS, BACKUP_CREATE, BACKUP_RESTORE, BACKUP_REMINDER, BACKUP_SAMPLE, BACKUP_WIPE,
    // Lainnya dan profil
    MORE_GUIDE, MORE_PROFILE, MORE_NOTIF, MORE_BACKUP, MORE_THEME, MORE_LANGUAGE, PROFILE_LOGO, PROFILE_NAME, PROFILE_SAVE,
}

/** Menyimpan posisi (di jendela) tiap bagian yang bisa disorot, dan bagian mana yang sedang dituju. */
class CoachRegistry {
    val bounds: SnapshotStateMap<CoachId, Rect> = mutableStateMapOf()
    var focus by mutableStateOf<CoachId?>(null)
}

val LocalCoachRegistry = staticCompositionLocalOf<CoachRegistry?> { null }

/**
 * Tandai sebuah bagian sebagai sasaran tutorial. Tanpa registri (di luar tutorial) tidak melakukan apa-apa.
 * Bila sedang dituju dan berada di daftar yang bisa digulir, daftar digulir sampai bagian itu terlihat.
 */
@OptIn(ExperimentalFoundationApi::class)
fun Modifier.coachTarget(id: CoachId): Modifier = composed {
    val registry = LocalCoachRegistry.current
    if (registry == null) {
        this
    } else {
        val requester = remember { BringIntoViewRequester() }
        DisposableEffect(registry, id) { onDispose { registry.bounds.remove(id) } }
        LaunchedEffect(registry.focus) {
            if (registry.focus == id) {
                delay(350)
                runCatching { requester.bringIntoView() }
            }
        }
        this.bringIntoViewRequester(requester).onGloballyPositioned { if (it.isAttached) registry.bounds[id] = it.boundsInWindow() }
    }
}

/** Pembungkus untuk sasaran yang komponennya tidak menerima Modifier. */
@Composable
fun CoachBox(id: CoachId, modifier: Modifier = Modifier, content: @Composable () -> Unit) {
    Box(modifier.coachTarget(id)) { content() }
}

/**
 * Daftar yang digulir malas hanya menyusun bagian yang terlihat, jadi bagian di bawah layar tidak punya posisi.
 * Fungsi ini menggulir daftar ke butir yang berisi sasaran yang sedang dituju tutorial.
 */
@Composable
fun rememberCoachListState(itemOf: Map<CoachId, Int>): LazyListState {
    val state = rememberLazyListState()
    val registry = LocalCoachRegistry.current
    LaunchedEffect(registry?.focus) {
        val index = registry?.focus?.let { itemOf[it] }
        if (index != null) state.animateScrollToItem(index)
    }
    return state
}

/**
 * Satu langkah tutorial. [route] adalah layar tempat langkah berlangsung; tutorial berpindah ke sana sendiri.
 * [target] kosong berarti penjelasan tanpa sorotan. [optional] dilewati bila bagiannya tidak ada (misalnya belum ada menu).
 */
data class CoachStep(
    val target: CoachId?,
    val route: String,
    @StringRes val title: Int,
    @StringRes val text: Int,
    val optional: Boolean = false,
)

/** Tutorial interaktif singkat yang dimulai setelah pengenalan pertama. */
val TourSteps = listOf(
    CoachStep(CoachId.TAB_CASHIER, Routes.HOME, R.string.tour_cashier_title, R.string.tour_cashier_text),
    CoachStep(CoachId.HOME_SEARCH, Routes.HOME, R.string.tour_search_title, R.string.tour_search_text),
    CoachStep(CoachId.HOME_CATEGORIES, Routes.HOME, R.string.tour_categories_title, R.string.tour_categories_text, optional = true),
    CoachStep(CoachId.HOME_MENU, Routes.HOME, R.string.tour_menu_title, R.string.tour_menu_text, optional = true),
    CoachStep(CoachId.TAB_MANAGE, Routes.HOME, R.string.tour_manage_title, R.string.tour_manage_text),
    CoachStep(CoachId.TAB_REPORTS, Routes.HOME, R.string.tour_reports_title, R.string.tour_reports_text),
    CoachStep(CoachId.TAB_MORE, Routes.HOME, R.string.tour_more_title, R.string.tour_more_text),
    CoachStep(CoachId.MORE_GUIDE, Routes.MORE, R.string.tour_guide_title, R.string.tour_guide_text),
)

/**
 * Tutorial sorot: layar digelapkan, satu bagian asli diberi "lubang" dan penjelasan. Berpindah layar sendiri bila langkah
 * berikutnya ada di layar lain. Langkah yang bagiannya tidak ditemukan tampil sebagai kartu di tengah, atau dilewati
 * bila ditandai opsional. Tombol Lewati selalu ada.
 */
@Composable
fun CoachOverlay(
    registry: CoachRegistry,
    steps: List<CoachStep>,
    scriptKey: String,
    isAt: (String) -> Boolean,
    navigateTo: (String) -> Unit,
    onFinish: () -> Unit,
) {
    var index by rememberSaveable(scriptKey) { mutableIntStateOf(0) }
    var direction by remember(scriptKey) { mutableIntStateOf(1) }
    val safeIndex = index.coerceIn(0, steps.lastIndex)
    val step = steps[safeIndex]
    val last = safeIndex == steps.lastIndex

    fun goTo(to: Int, dir: Int) {
        direction = dir
        index = to
    }

    // Pindah ke layar langkah ini, lalu tentukan sasaran. Beri waktu supaya layar tujuan sempat tersusun.
    LaunchedEffect(scriptKey, safeIndex) {
        registry.focus = step.target
        if (!isAt(step.route)) navigateTo(step.route)
    }
    DisposableEffect(registry) { onDispose { registry.focus = null } }
    var waited by remember(scriptKey, safeIndex) { mutableStateOf(false) }
    LaunchedEffect(scriptKey, safeIndex) {
        waited = false
        delay(1400)
        waited = true
    }

    val density = LocalDensity.current
    var origin by remember { mutableStateOf(Offset.Zero) }
    var area by remember { mutableStateOf(IntSize.Zero) }
    var cardSize by remember { mutableStateOf(IntSize.Zero) }
    val raw = step.target?.let { registry.bounds[it] }
    val onScreen = raw != null && isAt(step.route) &&
        raw.bottom > origin.y + 8f && raw.top < origin.y + area.height - 8f && area != IntSize.Zero
    val missing = step.target != null && !onScreen

    // Bagian opsional yang tidak ada dilewati, ke arah yang sedang dijalani pengguna.
    LaunchedEffect(waited, missing, safeIndex) {
        if (waited && missing && step.optional) {
            val next = safeIndex + direction
            if (next in steps.indices) goTo(next, direction) else if (direction > 0) onFinish()
        }
    }

    BackHandler { if (safeIndex > 0) goTo(safeIndex - 1, -1) else onFinish() }

    val pad = with(density) { 8.dp.toPx() }
    val corner = with(density) { 16.dp.toPx() }
    val margin = with(density) { 16.dp.toPx() }
    val target: Rect? = if (onScreen) raw!!.translate(-origin.x, -origin.y).inflate(pad) else null
    val animated by animateRectAsState(target ?: Rect.Zero, label = "coach")
    val shown = if (target != null) animated else null
    val ring = MaterialTheme.colorScheme.primary
    // Selama sasaran belum ketemu dan belum habis waktu tunggu, hanya layar gelap yang tampil agar tidak berkedip.
    val showCard = !(missing && !waited)

    Box(
        Modifier
            .fillMaxSize()
            .onGloballyPositioned {
                origin = it.positionInWindow()
                area = it.size
            }
            .pointerInput(Unit) { detectTapGestures { } },
    ) {
        Canvas(Modifier.fillMaxSize().graphicsLayer { compositingStrategy = CompositingStrategy.Offscreen }) {
            drawRect(Color.Black.copy(alpha = 0.74f))
            shown?.let {
                drawRoundRect(
                    color = Color.Black,
                    topLeft = it.topLeft,
                    size = Size(it.width, it.height),
                    cornerRadius = CornerRadius(corner),
                    blendMode = BlendMode.Clear,
                )
                drawRoundRect(
                    color = ring,
                    topLeft = it.topLeft,
                    size = Size(it.width, it.height),
                    cornerRadius = CornerRadius(corner),
                    style = Stroke(width = 2.dp.toPx()),
                )
            }
        }
        if (showCard) {
            val cardWidthPx = minOf(area.width - 2 * margin, with(density) { 360.dp.toPx() })
            val cardModifier = if (shown == null || area == IntSize.Zero) {
                Modifier.align(Alignment.Center)
            } else {
                val below = area.height - shown.bottom
                val y = if (below >= cardSize.height + 2 * margin) {
                    shown.bottom + margin
                } else {
                    (shown.top - cardSize.height - margin).coerceAtLeast(margin)
                }
                val x = (shown.center.x - cardWidthPx / 2).coerceIn(margin, (area.width - cardWidthPx - margin).coerceAtLeast(margin))
                Modifier.offset { IntOffset(x.roundToInt(), y.roundToInt()) }
            }
            Surface(
                modifier = cardModifier
                    .width(with(density) { cardWidthPx.toDp() }.coerceAtLeast(200.dp))
                    .onSizeChanged { cardSize = it },
                shape = MaterialTheme.shapes.large,
                color = MaterialTheme.colorScheme.surfaceContainerLowest,
                shadowElevation = 12.dp,
            ) {
                Column(Modifier.padding(20.dp), verticalArrangement = Arrangement.spacedBy(Spacing.sm)) {
                    Text(
                        stringResource(R.string.tour_step, safeIndex + 1, steps.size),
                        style = MaterialTheme.typography.labelMedium.copy(fontWeight = androidx.compose.ui.text.font.FontWeight.Bold),
                        color = MaterialTheme.colorScheme.primary,
                    )
                    Text(
                        stringResource(step.title),
                        style = MaterialTheme.typography.titleLarge.copy(fontWeight = androidx.compose.ui.text.font.FontWeight.ExtraBold),
                    )
                    Text(
                        stringResource(step.text),
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                    Row(horizontalArrangement = Arrangement.spacedBy(5.dp), modifier = Modifier.padding(vertical = Spacing.xs)) {
                        steps.indices.forEach { i ->
                            Box(
                                Modifier
                                    .height(6.dp)
                                    .width(if (i == safeIndex) 22.dp else 6.dp)
                                    .background(
                                        if (i == safeIndex) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.outlineVariant,
                                        androidx.compose.foundation.shape.RoundedCornerShape(3.dp),
                                    ),
                            )
                        }
                    }
                    Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
                        if (!last) {
                            TextButton(onClick = onFinish) {
                                Text(stringResource(R.string.ob_skip), color = MaterialTheme.colorScheme.onSurfaceVariant, fontWeight = androidx.compose.ui.text.font.FontWeight.Bold)
                            }
                        }
                        Spacer(Modifier.weight(1f))
                        if (safeIndex > 0) {
                            TextButton(onClick = { goTo(safeIndex - 1, -1) }) { Text(stringResource(R.string.action_back)) }
                            Spacer(Modifier.width(Spacing.xs))
                        }
                        androidx.compose.material3.Button(
                            onClick = { if (last) onFinish() else goTo(safeIndex + 1, 1) },
                            shape = androidx.compose.foundation.shape.RoundedCornerShape(50),
                            contentPadding = androidx.compose.foundation.layout.PaddingValues(horizontal = 22.dp, vertical = 12.dp),
                        ) {
                            Text(
                                stringResource(if (last) R.string.ob_done else R.string.ob_next),
                                style = MaterialTheme.typography.labelLarge.copy(fontWeight = androidx.compose.ui.text.font.FontWeight.ExtraBold),
                            )
                            if (!last) {
                                Spacer(Modifier.width(Spacing.sm))
                                androidx.compose.material3.Icon(
                                    androidx.compose.material.icons.Icons.AutoMirrored.Outlined.ArrowForward,
                                    contentDescription = null,
                                    modifier = Modifier.size(18.dp),
                                )
                            }
                        }
                    }
                }
            }
        }
    }
}
