package com.example.kasirarn.ui.onboarding

import androidx.activity.compose.BackHandler
import androidx.annotation.StringRes
import androidx.compose.animation.core.animateRectAsState
import androidx.compose.foundation.Canvas
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
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.runtime.snapshots.SnapshotStateMap
import androidx.compose.runtime.staticCompositionLocalOf
import androidx.compose.runtime.mutableStateMapOf
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
import androidx.compose.ui.layout.onGloballyPositioned
import androidx.compose.ui.layout.onSizeChanged
import androidx.compose.ui.layout.boundsInWindow
import androidx.compose.ui.layout.positionInWindow
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.IntSize
import androidx.compose.ui.unit.dp
import com.example.kasirarn.R
import com.example.kasirarn.ui.components.PrimaryButton
import com.example.kasirarn.ui.nav.TopTab
import com.example.kasirarn.ui.theme.Spacing
import kotlinx.coroutines.delay
import kotlin.math.roundToInt

/** Elemen asli di layar yang bisa disorot oleh tutorial. */
enum class CoachId { TAB_CASHIER, TAB_MANAGE, TAB_REPORTS, TAB_MORE, HOME_SEARCH, HOME_CATEGORIES, HOME_MENU, MORE_GUIDE }

/** Menyimpan posisi (di jendela) tiap elemen yang bisa disorot. */
class CoachRegistry {
    val bounds: SnapshotStateMap<CoachId, Rect> = mutableStateMapOf()
}

val LocalCoachRegistry = staticCompositionLocalOf<CoachRegistry?> { null }

/** Tandai sebuah elemen sebagai target tutorial. Tanpa registri (di luar tutorial) tidak melakukan apa-apa. */
fun Modifier.coachTarget(id: CoachId): Modifier = composed {
    val registry = LocalCoachRegistry.current
    if (registry == null) {
        this
    } else {
        DisposableEffect(registry, id) { onDispose { registry.bounds.remove(id) } }
        this.onGloballyPositioned { if (it.isAttached) registry.bounds[id] = it.boundsInWindow() }
    }
}

/** [screen] adalah tab tempat target berada; tutorial berpindah tab sendiri bila langkah berikutnya ada di tab lain. */
data class CoachStep(val target: CoachId?, val screen: TopTab, @StringRes val title: Int, @StringRes val text: Int)

val TourSteps = listOf(
    CoachStep(CoachId.TAB_CASHIER, TopTab.CASHIER, R.string.tour_cashier_title, R.string.tour_cashier_text),
    CoachStep(CoachId.HOME_SEARCH, TopTab.CASHIER, R.string.tour_search_title, R.string.tour_search_text),
    CoachStep(CoachId.HOME_CATEGORIES, TopTab.CASHIER, R.string.tour_categories_title, R.string.tour_categories_text),
    CoachStep(CoachId.HOME_MENU, TopTab.CASHIER, R.string.tour_menu_title, R.string.tour_menu_text),
    CoachStep(CoachId.TAB_MANAGE, TopTab.CASHIER, R.string.tour_manage_title, R.string.tour_manage_text),
    CoachStep(CoachId.TAB_REPORTS, TopTab.CASHIER, R.string.tour_reports_title, R.string.tour_reports_text),
    CoachStep(CoachId.TAB_MORE, TopTab.CASHIER, R.string.tour_more_title, R.string.tour_more_text),
    CoachStep(CoachId.MORE_GUIDE, TopTab.MORE, R.string.tour_guide_title, R.string.tour_guide_text),
)

/**
 * Tutorial sorot: layar digelapkan, satu elemen asli diberi "lubang" dan penjelasan. Langkah yang elemennya
 * tidak sedang tampil (misalnya menu belum dibuat) dilewati. Tombol Lewati selalu ada.
 */
@Composable
fun CoachOverlay(registry: CoachRegistry, current: TopTab?, onNavigate: (TopTab) -> Unit, onFinish: () -> Unit) {
    // Daftar langkah ditetapkan sekali, setelah layar Kasir terukur, supaya nomor langkah tidak berubah di tengah jalan.
    val ready = current == TopTab.CASHIER && CoachId.TAB_CASHIER in registry.bounds && CoachId.HOME_SEARCH in registry.bounds
    var fixed by remember { mutableStateOf<List<CoachStep>?>(null) }
    LaunchedEffect(ready) {
        if (ready && fixed == null) {
            delay(450)
            fixed = TourSteps.filter { it.screen != TopTab.CASHIER || it.target == null || it.target in registry.bounds }
        }
    }
    val steps = fixed ?: return
    var index by rememberSaveable { mutableIntStateOf(0) }
    val safeIndex = index.coerceIn(0, steps.lastIndex)
    val step = steps[safeIndex]
    val last = safeIndex == steps.lastIndex
    fun goTo(to: Int) {
        index = to
        if (steps[to].screen != current) onNavigate(steps[to].screen)
    }

    BackHandler { if (safeIndex > 0) goTo(safeIndex - 1) else onFinish() }

    val density = LocalDensity.current
    var origin by remember { mutableStateOf(Offset.Zero) }
    var area by remember { mutableStateOf(IntSize.Zero) }
    var cardSize by remember { mutableStateOf(IntSize.Zero) }
    val pad = with(density) { 8.dp.toPx() }
    val corner = with(density) { 16.dp.toPx() }
    val margin = with(density) { 16.dp.toPx() }
    val target: Rect? = step.target?.let { registry.bounds[it] }?.translate(-origin.x, -origin.y)?.inflate(pad)
    val animated by animateRectAsState(target ?: Rect.Zero, label = "coach")
    val shown = if (target != null) animated else null
    val ring = MaterialTheme.colorScheme.primaryContainer

    Box(
        Modifier
            .fillMaxSize()
            .onGloballyPositioned {
                origin = it.positionInWindow()
                area = it.size
            }
            // Layar di bawah tidak boleh ikut tersentuh selama tutorial.
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
            color = MaterialTheme.colorScheme.surfaceContainerHigh,
            tonalElevation = 6.dp,
            shadowElevation = 8.dp,
        ) {
            Column(Modifier.padding(Spacing.lg), verticalArrangement = Arrangement.spacedBy(Spacing.sm)) {
                Text(
                    stringResource(R.string.tour_step, safeIndex + 1, steps.size),
                    style = MaterialTheme.typography.labelMedium,
                    color = MaterialTheme.colorScheme.primary,
                )
                Text(stringResource(step.title), style = MaterialTheme.typography.titleMedium)
                Text(
                    stringResource(step.text),
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
                Spacer(Modifier.height(Spacing.xs))
                Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
                    if (!last) {
                        TextButton(onClick = onFinish) { Text(stringResource(R.string.ob_skip)) }
                    }
                    Spacer(Modifier.weight(1f))
                    PrimaryButton(
                        text = stringResource(if (last) R.string.ob_done else R.string.ob_next),
                        onClick = { if (last) onFinish() else goTo(safeIndex + 1) },
                    )
                }
            }
        }
    }
}
