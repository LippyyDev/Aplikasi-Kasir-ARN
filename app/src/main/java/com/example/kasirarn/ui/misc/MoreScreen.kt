package com.example.kasirarn.ui.misc

import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.selection.toggleable
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.outlined.KeyboardArrowRight
import androidx.compose.material.icons.outlined.Info
import androidx.compose.material.icons.outlined.MenuBook
import androidx.compose.material.icons.outlined.Language
import androidx.compose.material.icons.outlined.Notifications
import androidx.compose.material.icons.outlined.Palette
import androidx.compose.material.icons.outlined.SettingsBackupRestore
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Badge
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.lifecycle.ViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewModelScope
import com.example.kasirarn.AppContainer
import com.example.kasirarn.BuildConfig
import com.example.kasirarn.R
import com.example.kasirarn.data.settings.AppLanguage
import com.example.kasirarn.data.settings.AppSettings
import com.example.kasirarn.data.settings.ThemeMode
import com.example.kasirarn.ui.components.ArnCard
import com.example.kasirarn.ui.components.ArnTopBar
import com.example.kasirarn.ui.components.MenuImage
import com.example.kasirarn.ui.format.LocalFmt
import com.example.kasirarn.ui.home.RadioRow
import com.example.kasirarn.ui.nav.ConstrainedScreen
import com.example.kasirarn.ui.nav.Routes
import com.example.kasirarn.ui.nav.containerViewModel
import com.example.kasirarn.ui.onboarding.CoachId
import com.example.kasirarn.ui.onboarding.coachTarget
import com.example.kasirarn.ui.theme.Spacing
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch

data class MoreUi(
    val settings: AppSettings = AppSettings(),
    val businessName: String = "",
    val logo: String? = null,
    val unread: Int = 0,
)

class MoreViewModel(private val c: AppContainer) : ViewModel() {
    val ui: StateFlow<MoreUi> = combine(
        c.settings.settings,
        c.profile.observe(),
        c.notifications.observeUnreadCount(),
    ) { settings, profile, unread -> MoreUi(settings, profile?.nama.orEmpty(), profile?.logoPath, unread) }
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), MoreUi())

    fun setTheme(mode: ThemeMode) { viewModelScope.launch { c.settings.setTheme(mode) } }
    fun setDynamicColor(on: Boolean) { viewModelScope.launch { c.settings.setDynamicColor(on) } }
    fun setLanguage(lang: AppLanguage) { viewModelScope.launch { c.settings.setLanguage(lang) } }
}

/** Mode Lainnya: profil usaha, pemberitahuan, cadangan, tampilan, dan informasi aplikasi. */
@Composable
fun MoreScreen(onNavigate: (String) -> Unit) {
    val vm = containerViewModel { MoreViewModel(it) }
    val ui by vm.ui.collectAsStateWithLifecycle()
    val fmt = LocalFmt.current
    var showTheme by remember { mutableStateOf(false) }
    var showLanguage by remember { mutableStateOf(false) }
    val backupStale = ui.settings.lastBackupAt == 0L ||
        System.currentTimeMillis() - ui.settings.lastBackupAt > ui.settings.backupReminderDays * 86_400_000L

    Scaffold(
        containerColor = MaterialTheme.colorScheme.background,
        topBar = { ArnTopBar(stringResource(R.string.nav_more), onBack = null) },
    ) { padding ->
        ConstrainedScreen {
            Column(
                Modifier.fillMaxSize().padding(padding).verticalScroll(rememberScrollState()).padding(horizontal = Spacing.lg),
                verticalArrangement = Arrangement.spacedBy(Spacing.sm),
            ) {
                BusinessHeader(ui) { onNavigate(Routes.PROFILE) }

                GroupLabel(stringResource(R.string.more_section_alerts))
                SettingsGroup {
                    SettingRow(
                        Modifier.coachTarget(CoachId.MORE_NOTIF),
                        Icons.Outlined.Notifications,
                        stringResource(R.string.drawer_notifications),
                        badge = ui.unread,
                    ) { onNavigate(Routes.NOTIFICATIONS) }
                    HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant)
                    SettingRow(
                        Modifier.coachTarget(CoachId.MORE_BACKUP),
                        Icons.Outlined.SettingsBackupRestore,
                        stringResource(R.string.drawer_backup),
                        supporting = if (ui.settings.lastBackupAt == 0L) {
                            stringResource(R.string.drawer_backup_never)
                        } else {
                            stringResource(R.string.drawer_backup_last, fmt.dateTime(ui.settings.lastBackupAt))
                        },
                        supportingWarning = backupStale,
                    ) { onNavigate(Routes.BACKUP) }
                }

                GroupLabel(stringResource(R.string.more_section_display))
                SettingsGroup {
                    SettingRow(Modifier.coachTarget(CoachId.MORE_THEME), Icons.Outlined.Palette, stringResource(R.string.drawer_theme), supporting = themeLabel(ui.settings.theme)) {
                        showTheme = true
                    }
                    HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant)
                    SettingRow(Modifier.coachTarget(CoachId.MORE_LANGUAGE), Icons.Outlined.Language, stringResource(R.string.drawer_language), supporting = languageLabel(ui.settings.language)) {
                        showLanguage = true
                    }
                }

                GroupLabel(stringResource(R.string.more_section_info))
                SettingsGroup {
                    Box(Modifier.coachTarget(CoachId.MORE_GUIDE)) {
                        SettingRow(
                            Modifier,
                            Icons.Outlined.MenuBook,
                            stringResource(R.string.userguide_title),
                            supporting = stringResource(R.string.guide_summary),
                        ) { onNavigate(Routes.GUIDE) }
                    }
                    HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant)
                    SettingRow(
                        Modifier,
                        Icons.Outlined.Info,
                        stringResource(R.string.drawer_about),
                        supporting = stringResource(R.string.about_version, BuildConfig.VERSION_NAME, BuildConfig.VERSION_CODE),
                    ) { onNavigate(Routes.ABOUT) }
                }
                Spacer(Modifier.height(Spacing.xl))
            }
        }
    }

    if (showTheme) {
        ThemeDialog(
            current = ui.settings.theme,
            dynamic = ui.settings.dynamicColor,
            onSelect = vm::setTheme,
            onDynamic = vm::setDynamicColor,
            onDismiss = { showTheme = false },
        )
    }
    if (showLanguage) {
        LanguageDialog(
            current = ui.settings.language,
            onSelect = {
                vm.setLanguage(it)
                showLanguage = false
            },
            onDismiss = { showLanguage = false },
        )
    }
}

@Composable
private fun BusinessHeader(ui: MoreUi, onClick: () -> Unit) {
    ArnCard(Modifier.fillMaxWidth().coachTarget(CoachId.MORE_PROFILE), onClick = onClick, contentPadding = PaddingValues(Spacing.lg)) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Box(
                Modifier.size(56.dp).clip(CircleShape).background(MaterialTheme.colorScheme.primaryContainer),
                contentAlignment = Alignment.Center,
            ) {
                if (ui.logo != null) {
                    MenuImage(photo = ui.logo, name = ui.businessName, modifier = Modifier.fillMaxSize(), shape = CircleShape)
                } else {
                    Image(painterResource(R.drawable.ic_logo_mark), contentDescription = null, modifier = Modifier.fillMaxSize())
                }
            }
            Spacer(Modifier.width(Spacing.lg))
            Column(Modifier.weight(1f)) {
                Text(
                    ui.businessName.ifBlank { stringResource(R.string.more_business_unnamed) },
                    style = MaterialTheme.typography.titleMedium,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                )
                Text(
                    stringResource(R.string.more_business_edit),
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
            Icon(Icons.AutoMirrored.Outlined.KeyboardArrowRight, contentDescription = null, tint = MaterialTheme.colorScheme.onSurfaceVariant)
        }
    }
}

@Composable
private fun GroupLabel(text: String) {
    Text(
        text,
        style = MaterialTheme.typography.titleSmall,
        color = MaterialTheme.colorScheme.onSurfaceVariant,
        modifier = Modifier.padding(top = Spacing.md, start = Spacing.xs),
    )
}

@Composable
private fun SettingsGroup(content: @Composable () -> Unit) {
    ArnCard(Modifier.fillMaxWidth(), contentPadding = PaddingValues(0.dp)) { Column { content() } }
}

@Composable
private fun SettingRow(
    modifier: Modifier = Modifier,
    icon: ImageVector,
    title: String,
    supporting: String? = null,
    supportingWarning: Boolean = false,
    badge: Int = 0,
    onClick: () -> Unit,
) {
    Row(
        modifier
            .fillMaxWidth()
            .heightIn(min = 60.dp)
            .clickable(role = Role.Button, onClick = onClick)
            .padding(horizontal = Spacing.lg, vertical = Spacing.md),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Box(
            Modifier.size(40.dp).clip(MaterialTheme.shapes.small).background(MaterialTheme.colorScheme.primaryContainer),
            contentAlignment = Alignment.Center,
        ) {
            Icon(icon, contentDescription = null, tint = MaterialTheme.colorScheme.onPrimaryContainer, modifier = Modifier.size(22.dp))
        }
        Spacer(Modifier.width(Spacing.lg))
        Column(Modifier.weight(1f)) {
            Text(title, style = MaterialTheme.typography.titleSmall)
            if (supporting != null) {
                Text(
                    supporting,
                    style = MaterialTheme.typography.bodySmall,
                    color = if (supportingWarning) MaterialTheme.colorScheme.tertiary else MaterialTheme.colorScheme.onSurfaceVariant,
                    maxLines = 2,
                    overflow = TextOverflow.Ellipsis,
                )
            }
        }
        if (badge > 0) {
            Badge(containerColor = MaterialTheme.colorScheme.primary) { Text(if (badge > 99) "99+" else badge.toString()) }
            Spacer(Modifier.width(Spacing.sm))
        }
        Icon(Icons.AutoMirrored.Outlined.KeyboardArrowRight, contentDescription = null, tint = MaterialTheme.colorScheme.onSurfaceVariant)
    }
}

@Composable
private fun themeLabel(mode: ThemeMode): String = stringResource(
    when (mode) {
        ThemeMode.SYSTEM -> R.string.theme_system
        ThemeMode.LIGHT -> R.string.theme_light
        ThemeMode.DARK -> R.string.theme_dark
    },
)

@Composable
private fun languageLabel(lang: AppLanguage): String = stringResource(
    when (lang) {
        AppLanguage.ID -> R.string.language_id
        AppLanguage.EN -> R.string.language_en
    },
)

@Composable
private fun ThemeDialog(
    current: ThemeMode,
    dynamic: Boolean,
    onSelect: (ThemeMode) -> Unit,
    onDynamic: (Boolean) -> Unit,
    onDismiss: () -> Unit,
) {
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(stringResource(R.string.drawer_theme)) },
        text = {
            Column {
                ThemeMode.entries.forEach { mode -> RadioRow(themeLabel(mode), mode == current) { onSelect(mode) } }
                if (android.os.Build.VERSION.SDK_INT >= android.os.Build.VERSION_CODES.S) {
                    HorizontalDivider(Modifier.padding(vertical = Spacing.sm), color = MaterialTheme.colorScheme.outlineVariant)
                    Row(
                        Modifier
                            .fillMaxWidth()
                            .heightIn(min = Spacing.minTouch + 8.dp)
                            .toggleable(value = dynamic, role = Role.Switch, onValueChange = onDynamic),
                        verticalAlignment = Alignment.CenterVertically,
                    ) {
                        Column(Modifier.weight(1f)) {
                            Text(stringResource(R.string.theme_dynamic), style = MaterialTheme.typography.bodyLarge)
                            Text(
                                stringResource(R.string.theme_dynamic_hint),
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                            )
                        }
                        Switch(checked = dynamic, onCheckedChange = null)
                    }
                }
            }
        },
        confirmButton = { TextButton(onClick = onDismiss) { Text(stringResource(R.string.action_close)) } },
        containerColor = MaterialTheme.colorScheme.surfaceContainerHigh,
    )
}

@Composable
private fun LanguageDialog(current: AppLanguage, onSelect: (AppLanguage) -> Unit, onDismiss: () -> Unit) {
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(stringResource(R.string.drawer_language)) },
        text = { Column { AppLanguage.entries.forEach { lang -> RadioRow(languageLabel(lang), lang == current) { onSelect(lang) } } } },
        confirmButton = { TextButton(onClick = onDismiss) { Text(stringResource(R.string.action_close)) } },
        containerColor = MaterialTheme.colorScheme.surfaceContainerHigh,
    )
}
