package com.example.kasirarn.ui.components

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.example.kasirarn.ui.theme.MoneyStyle
import com.example.kasirarn.ui.theme.Spacing
import com.example.kasirarn.ui.theme.isDarkScheme

/** Warna seri grafik: dipilih agar tetap terbedakan di mode terang dan gelap. */
@Composable
fun chartColors(): List<Color> {
    val scheme = MaterialTheme.colorScheme
    val extra = if (isDarkScheme()) {
        listOf(Color(0xFF8FC1B5), Color(0xFFC9A0B8), Color(0xFFB5ACA0))
    } else {
        listOf(Color(0xFF2F7A6C), Color(0xFF8C4A6B), Color(0xFF7D7367))
    }
    // Tiga warna pertama mengikuti skema aktif (termasuk warna dinamis); sisanya tetap agar seri tetap terbedakan.
    return listOf(scheme.primary, scheme.secondary, scheme.tertiary) + extra
}

data class ChartBar(val label: String, val value: Long, val showLabel: Boolean = true)

/** Grafik batang tren. Ketuk batang untuk melihat nilainya; ringkasan teks tersedia untuk pembaca layar. */
@Composable
fun TrendBarChart(
    bars: List<ChartBar>,
    formatValue: (Long) -> String,
    modifier: Modifier = Modifier,
    description: String,
) {
    var selected by remember(bars) { mutableStateOf<Int?>(null) }
    val primary = MaterialTheme.colorScheme.primary
    val faint = MaterialTheme.colorScheme.surfaceContainerHighest
    val grid = MaterialTheme.colorScheme.outlineVariant
    val max = (bars.maxOfOrNull { it.value } ?: 0L).coerceAtLeast(1L)

    Column(modifier = modifier.semantics { contentDescription = description }) {
        val sel = selected?.let { bars.getOrNull(it) }
        Text(
            text = if (sel != null) "${sel.label} · ${formatValue(sel.value)}" else " ",
            style = MaterialTheme.typography.labelLarge,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
        Spacer(Modifier.height(Spacing.sm))
        Canvas(
            Modifier
                .fillMaxWidth()
                .height(140.dp)
                .pointerInput(bars) {
                    detectTapGestures { pos ->
                        if (bars.isEmpty()) return@detectTapGestures
                        val slot = size.width.toFloat() / bars.size
                        selected = (pos.x / slot).toInt().coerceIn(0, bars.size - 1)
                    }
                },
        ) {
            val slot = size.width / bars.size
            val barWidth = (slot * 0.62f).coerceAtMost(28.dp.toPx())
            // Garis dasar dan satu garis bantu di tengah.
            drawLine(grid, Offset(0f, size.height), Offset(size.width, size.height), strokeWidth = 1.dp.toPx())
            drawLine(grid.copy(alpha = 0.5f), Offset(0f, size.height / 2), Offset(size.width, size.height / 2), strokeWidth = 1.dp.toPx())
            bars.forEachIndexed { i, bar ->
                val h = if (bar.value <= 0) 2.dp.toPx() else (size.height * bar.value / max).coerceAtLeast(3.dp.toPx())
                val left = slot * i + (slot - barWidth) / 2
                val isSel = selected == i
                drawRoundRect(
                    color = if (bar.value <= 0) faint else if (selected == null || isSel) primary else primary.copy(alpha = 0.45f),
                    topLeft = Offset(left, size.height - h),
                    size = Size(barWidth, h),
                    cornerRadius = CornerRadius(4.dp.toPx(), 4.dp.toPx()),
                )
            }
        }
        Spacer(Modifier.height(Spacing.xs))
        Row(Modifier.fillMaxWidth()) {
            bars.forEach { bar ->
                Box(Modifier.weight(1f), contentAlignment = Alignment.Center) {
                    if (bar.showLabel) {
                        Text(
                            bar.label,
                            style = MaterialTheme.typography.labelSmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                            maxLines = 1,
                            softWrap = false,
                            overflow = TextOverflow.Clip,
                        )
                    }
                }
            }
        }
    }
}

data class RankRow(val label: String, val value: Long, val valueText: String, val caption: String? = null)

/** Peringkat dengan batang horizontal; nilai selalu ditulis sebagai teks. */
@Composable
fun RankedBars(rows: List<RankRow>, modifier: Modifier = Modifier) {
    val max = (rows.maxOfOrNull { it.value } ?: 0L).coerceAtLeast(1L)
    Column(modifier, verticalArrangement = Arrangement.spacedBy(Spacing.md)) {
        rows.forEachIndexed { i, row ->
            Column {
                Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween, verticalAlignment = Alignment.CenterVertically) {
                    Text(
                        "${i + 1}. ${row.label}",
                        style = MaterialTheme.typography.bodyMedium,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                        modifier = Modifier.weight(1f),
                    )
                    Spacer(Modifier.width(Spacing.sm))
                    Text(row.valueText, style = MaterialTheme.typography.labelLarge.merge(MoneyStyle), maxLines = 1)
                }
                Spacer(Modifier.height(6.dp))
                Box(
                    Modifier.fillMaxWidth().height(8.dp).clip(RoundedCornerShape(4.dp)).background(MaterialTheme.colorScheme.surfaceContainerHighest),
                ) {
                    Box(
                        Modifier.fillMaxHeight().fillMaxWidth((row.value.toFloat() / max).coerceIn(0.02f, 1f))
                            .clip(RoundedCornerShape(4.dp)).background(MaterialTheme.colorScheme.primary),
                    )
                }
                if (row.caption != null) {
                    Spacer(Modifier.height(2.dp))
                    Text(row.caption, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                }
            }
        }
    }
}

data class Slice(val label: String, val value: Long, val valueText: String)

/** Donat proporsi dengan legenda: label, nilai, dan persentase ditulis sehingga tidak bergantung warna. */
@Composable
fun DonutWithLegend(slices: List<Slice>, modifier: Modifier = Modifier) {
    val colors = chartColors()
    val total = slices.sumOf { it.value }.coerceAtLeast(1L)
    val track = MaterialTheme.colorScheme.surfaceContainerHighest
    Row(modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(Spacing.lg)) {
        Canvas(Modifier.size(112.dp)) {
            val stroke = 18.dp.toPx()
            val diameter = size.minDimension - stroke
            val topLeft = Offset(stroke / 2, stroke / 2)
            drawCircle(track, radius = diameter / 2, center = center, style = Stroke(stroke))
            var start = -90f
            slices.forEachIndexed { i, s ->
                val sweep = 360f * s.value / total
                if (sweep > 0f) {
                    drawArc(
                        color = colors[i % colors.size],
                        startAngle = start,
                        sweepAngle = if (slices.size == 1) sweep else (sweep - 1.5f).coerceAtLeast(0.5f),
                        useCenter = false,
                        topLeft = topLeft,
                        size = Size(diameter, diameter),
                        style = Stroke(stroke, cap = StrokeCap.Butt),
                    )
                }
                start += sweep
            }
        }
        Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(Spacing.sm)) {
            slices.forEachIndexed { i, s ->
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Box(Modifier.size(10.dp).clip(CircleShape).background(colors[i % colors.size]))
                    Spacer(Modifier.width(Spacing.sm))
                    Text(s.label, style = MaterialTheme.typography.bodyMedium, maxLines = 1, overflow = TextOverflow.Ellipsis, modifier = Modifier.weight(1f))
                    Spacer(Modifier.width(Spacing.sm))
                    Column(horizontalAlignment = Alignment.End) {
                        Text(s.valueText, style = MaterialTheme.typography.labelMedium.merge(MoneyStyle), maxLines = 1)
                        Text(
                            "${(s.value * 100 + total / 2) / total}%",
                            style = MaterialTheme.typography.labelSmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                    }
                }
            }
        }
    }
}
