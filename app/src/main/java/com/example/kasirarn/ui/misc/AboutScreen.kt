package com.example.kasirarn.ui.misc

import androidx.compose.foundation.Image
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.PhoneAndroid
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import com.example.kasirarn.BuildConfig
import com.example.kasirarn.R
import com.example.kasirarn.data.db.ArnDatabase
import com.example.kasirarn.ui.components.ArnCard
import com.example.kasirarn.ui.components.ArnTopBar
import com.example.kasirarn.ui.components.SectionLabel
import com.example.kasirarn.ui.theme.Spacing

private data class Library(val name: String, val license: String)

private val Libraries = listOf(
    Library("AndroidX (Core, Lifecycle, Activity, Navigation, Room, DataStore, WorkManager)", "Apache License 2.0"),
    Library("Jetpack Compose dan Material 3", "Apache License 2.0"),
    Library("Kotlin dan kotlinx.coroutines", "Apache License 2.0"),
    Library("Plus Jakarta Sans (Tokotype)", "SIL Open Font License 1.1"),
)

@Composable
fun AboutScreen(onBack: () -> Unit) {
    Scaffold(
        containerColor = MaterialTheme.colorScheme.background,
        topBar = { ArnTopBar(stringResource(R.string.about_title), onBack) },
    ) { padding ->
        Column(
            Modifier.fillMaxSize().padding(padding).verticalScroll(rememberScrollState()).padding(horizontal = Spacing.lg),
            horizontalAlignment = Alignment.CenterHorizontally,
        ) {
            Spacer(Modifier.height(Spacing.lg))
            Image(painterResource(R.drawable.ic_logo_mark), contentDescription = null, modifier = Modifier.size(96.dp))
            Spacer(Modifier.height(Spacing.md))
            Text(stringResource(R.string.app_name), style = MaterialTheme.typography.headlineSmall)
            Text(
                stringResource(R.string.about_version, BuildConfig.VERSION_NAME, BuildConfig.VERSION_CODE),
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
            Text(
                stringResource(R.string.about_schema, ArnDatabase.VERSION),
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
            Spacer(Modifier.height(Spacing.xl))
            ArnCard(Modifier.fillMaxWidth()) {
                Row(verticalAlignment = Alignment.Top) {
                    Icon(Icons.Outlined.PhoneAndroid, contentDescription = null, tint = MaterialTheme.colorScheme.primary)
                    Spacer(Modifier.size(Spacing.md))
                    Column {
                        Text(stringResource(R.string.about_local_title), style = MaterialTheme.typography.titleSmall)
                        Text(
                            stringResource(R.string.about_local_body),
                            style = MaterialTheme.typography.bodyMedium,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                    }
                }
            }
            Column(Modifier.fillMaxWidth()) {
                SectionLabel(stringResource(R.string.about_developer))
                Text(stringResource(R.string.about_developer_name), style = MaterialTheme.typography.bodyLarge)
                SectionLabel(stringResource(R.string.about_licenses))
                Libraries.forEachIndexed { i, lib ->
                    if (i > 0) HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant)
                    Column(Modifier.padding(vertical = Spacing.md), verticalArrangement = Arrangement.spacedBy(2.dp)) {
                        Text(lib.name, style = MaterialTheme.typography.bodyMedium)
                        Text(lib.license, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                    }
                }
            }
            Spacer(Modifier.height(Spacing.xxl))
        }
    }
}
