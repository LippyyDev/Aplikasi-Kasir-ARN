package com.example.kasirarn.ui.report

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.RowScope
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.dp
import com.example.kasirarn.data.db.TrendRow
import com.example.kasirarn.data.repo.DayMath
import com.example.kasirarn.data.repo.Period
import com.example.kasirarn.data.repo.TimeRange
import com.example.kasirarn.ui.components.ArnCard
import com.example.kasirarn.ui.components.ChartBar
import com.example.kasirarn.ui.components.MoneyText
import com.example.kasirarn.ui.theme.Spacing
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.combine
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

data class PeriodUi(
    val period: Period,
    val range: TimeRange,
    val customFrom: Long,
    val customTo: Long,
)

/** Keadaan pilihan periode laporan, dibagi oleh layar Pemasukan dan Pengeluaran. */
class PeriodState(now: Long = System.currentTimeMillis()) {
    private val today = DayMath.startOfDay(now)
    private val period = MutableStateFlow(Period.TODAY)
    private val custom = MutableStateFlow(today to today)

    val state = combine(period, custom) { p, c ->
        PeriodUi(p, TimeRange.of(p, System.currentTimeMillis(), c.first, c.second), c.first, c.second)
    }

    fun select(p: Period) { period.value = p }

    fun setCustom(from: Long, toInclusive: Long) {
        custom.value = from to toInclusive
        period.value = Period.CUSTOM
    }
}

/**
 * Menyusun batang grafik dari baris agregat SQL. Hari tanpa penjualan tetap muncul sebagai batang nol
 * supaya sumbu waktu utuh. Rentang panjang dikelompokkan per minggu.
 */
fun buildBars(rows: List<TrendRow>, range: TimeRange, hourly: Boolean, locale: Locale): List<ChartBar> {
    val byBucket = rows.associate { it.bucket to it.total }
    if (hourly) {
        return (0L..23L).map { h -> ChartBar("%02d".format(h), byBucket[h] ?: 0L, showLabel = h % 6 == 0L) }
    }
    val offset = DayMath.offsetMs(range.from)
    val startDay = (range.from + offset) / 86_400_000L
    val endDay = (range.to - 1 + offset) / 86_400_000L
    val days = (startDay..endDay).toList()
    val dayFormat = SimpleDateFormat("d", locale)
    val weekFormat = SimpleDateFormat("d/M", locale)
    fun dayMillis(day: Long) = day * 86_400_000L - offset
    if (days.size <= 45) {
        val step = when {
            days.size <= 10 -> 1
            days.size <= 20 -> 2
            else -> 5
        }
        return days.mapIndexed { i, d ->
            ChartBar(dayFormat.format(Date(dayMillis(d))), byBucket[d] ?: 0L, showLabel = i % step == 0)
        }
    }
    return days.chunked(7).mapIndexed { i, week ->
        ChartBar(weekFormat.format(Date(dayMillis(week.first()))), week.sumOf { byBucket[it] ?: 0L }, showLabel = i % 2 == 0)
    }
}

@Composable
fun StatTile(label: String, value: Long, modifier: Modifier = Modifier, color: Color = Color.Unspecified, negativeAllowed: Boolean = false) {
    ArnCard(modifier) {
        Column {
            Text(label, style = MaterialTheme.typography.labelMedium, color = MaterialTheme.colorScheme.onSurfaceVariant, maxLines = 2)
            Spacer(Modifier.height(Spacing.xs))
            MoneyText(
                value,
                style = MaterialTheme.typography.titleMedium,
                color = if (negativeAllowed && value < 0) MaterialTheme.colorScheme.error else color,
                shrinkToFit = true,
            )
        }
    }
}

@Composable
fun StatRow(content: @Composable RowScope.() -> Unit) {
    Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(Spacing.md), content = content)
}

@Composable
fun ReportCard(title: String, modifier: Modifier = Modifier, content: @Composable () -> Unit) {
    ArnCard(modifier.fillMaxWidth()) {
        Column {
            Text(title, style = MaterialTheme.typography.titleSmall)
            Spacer(Modifier.height(Spacing.md))
            content()
        }
    }
}

@Composable
fun LineRow(
    label: String,
    value: Long,
    bold: Boolean = false,
    color: Color = Color.Unspecified,
    negativeIsError: Boolean = false,
    labelColor: Color = Color.Unspecified,
) {
    Row(
        Modifier.fillMaxWidth().padding(vertical = 3.dp),
        horizontalArrangement = Arrangement.SpaceBetween,
    ) {
        Text(
            label,
            style = if (bold) MaterialTheme.typography.titleSmall else MaterialTheme.typography.bodyMedium,
            color = if (labelColor != Color.Unspecified) labelColor else if (bold) MaterialTheme.colorScheme.onSurface else MaterialTheme.colorScheme.onSurfaceVariant,
            modifier = Modifier.weight(1f),
        )
        MoneyText(
            value,
            style = if (bold) MaterialTheme.typography.titleSmall else MaterialTheme.typography.bodyMedium,
            color = if (negativeIsError && value < 0) MaterialTheme.colorScheme.error else color,
        )
    }
}
