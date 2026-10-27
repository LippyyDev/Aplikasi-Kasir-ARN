package com.example.kasirarn.ui.components

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.example.kasirarn.R
import com.example.kasirarn.data.db.TopMenuRow
import com.example.kasirarn.ui.format.LocalFmt
import com.example.kasirarn.ui.theme.Spacing

/** Peringkat menu terlaris: nomor bulat, batang proporsi, pendapatan, dan jumlah terjual. */
@Composable
fun TopMenuList(rows: List<TopMenuRow>, short: Boolean, modifier: Modifier = Modifier, emptyText: String? = null) {
    val s = MaterialTheme.colorScheme
    val fmt = LocalFmt.current
    ArnCard(modifier.fillMaxWidth(), contentPadding = PaddingValues(Spacing.sm)) {
        if (rows.isEmpty()) {
            Text(
                emptyText.orEmpty(),
                style = MaterialTheme.typography.bodyMedium,
                color = s.onSurfaceVariant,
                modifier = Modifier.padding(Spacing.md),
            )
            return@ArnCard
        }
        val max = rows.maxOf { it.revenue }.coerceAtLeast(1L)
        Column(verticalArrangement = Arrangement.spacedBy(Spacing.xs)) {
            rows.forEachIndexed { i, row ->
                Row(
                    Modifier.fillMaxWidth().padding(Spacing.sm),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(Spacing.md),
                ) {
                    Box(
                        Modifier.size(32.dp).clip(CircleShape).background(if (i == 0) s.primary else s.surfaceContainerHigh),
                        contentAlignment = Alignment.Center,
                    ) {
                        Text(
                            "${i + 1}",
                            style = MaterialTheme.typography.labelLarge.copy(fontWeight = FontWeight.ExtraBold),
                            color = if (i == 0) s.onPrimary else s.onSurfaceVariant,
                        )
                    }
                    Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(6.dp)) {
                        Text(row.name, style = MaterialTheme.typography.titleSmall, maxLines = 1, overflow = TextOverflow.Ellipsis)
                        Box(Modifier.fillMaxWidth().height(6.dp).clip(RoundedCornerShape(3.dp)).background(s.surfaceContainerHigh)) {
                            Box(
                                Modifier
                                    .fillMaxHeight()
                                    .fillMaxWidth((row.revenue.toFloat() / max).coerceIn(0.04f, 1f))
                                    .clip(RoundedCornerShape(3.dp))
                                    .background(if (i == 0) s.primary else s.primary.copy(alpha = 0.45f)),
                            )
                        }
                    }
                    Column(horizontalAlignment = Alignment.End) {
                        Text(
                            if (short) fmt.moneyShort(row.revenue) else fmt.money(row.revenue),
                            style = MaterialTheme.typography.titleSmall.copy(fontWeight = FontWeight.ExtraBold),
                            maxLines = 1,
                        )
                        Text(
                            stringResource(R.string.income_sold_qty, row.qty),
                            style = MaterialTheme.typography.labelSmall,
                            color = s.onSurfaceVariant,
                            maxLines = 1,
                        )
                    }
                }
            }
        }
    }
}
