package com.example.kasirarn.ui.backup

import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.selection.selectable
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.RadioButton
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.Role
import com.example.kasirarn.R
import com.example.kasirarn.data.dummy.DummyScenario
import com.example.kasirarn.ui.theme.Spacing

/** Pilih ukuran data contoh sebelum dimuat. Dipakai di Halaman Utama dan di Cadangan dan pulihkan. */
@Composable
fun DummyScenarioDialog(onDismiss: () -> Unit, onConfirm: (DummyScenario) -> Unit) {
    var selected by rememberSaveable { mutableStateOf(DummyScenario.MEDIUM) }
    AlertDialog(
        onDismissRequest = onDismiss,
        containerColor = MaterialTheme.colorScheme.surfaceContainerHigh,
        title = { Text(stringResource(R.string.dummy_confirm_title)) },
        text = {
            Column {
                Text(
                    stringResource(R.string.dummy_confirm_body),
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.padding(bottom = Spacing.sm),
                )
                DummyScenario.entries.forEach { scenario ->
                    val (title, detail) = when (scenario) {
                        DummyScenario.FEW -> R.string.dummy_few to R.string.dummy_few_detail
                        DummyScenario.MEDIUM -> R.string.dummy_medium to R.string.dummy_medium_detail
                        DummyScenario.MANY -> R.string.dummy_many to R.string.dummy_many_detail
                    }
                    Row(
                        Modifier
                            .fillMaxWidth()
                            .heightIn(min = Spacing.minTouch)
                            .selectable(selected = selected == scenario, role = Role.RadioButton) { selected = scenario }
                            .padding(vertical = Spacing.xs),
                        verticalAlignment = Alignment.CenterVertically,
                    ) {
                        RadioButton(selected = selected == scenario, onClick = null)
                        Spacer(Modifier.width(Spacing.md))
                        Column {
                            Text(stringResource(title), style = MaterialTheme.typography.titleSmall)
                            Text(
                                stringResource(detail),
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                            )
                        }
                    }
                }
            }
        },
        confirmButton = { TextButton(onClick = { onConfirm(selected) }) { Text(stringResource(R.string.dummy_load)) } },
        dismissButton = { TextButton(onClick = onDismiss) { Text(stringResource(R.string.action_cancel)) } },
    )
}
