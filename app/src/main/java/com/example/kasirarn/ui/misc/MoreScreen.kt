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
import androidx.compose.ui.text.font.FontWeight
import com.example.kasirarn.ui.components.HeroCard
import com.example.kasirarn.ui.components.IconTile
import com.example.kasirarn.ui.components.initials
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
                    RowDivider()
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
                        tone = 1,
                    ) { onNavigate(Routes.BACKUP) }
                }

                GroupLabel(stringResource(R.string.more_section_display))
                SettingsGroup {
                    SettingRow(Modifier.coachTarget(CoachId.MORE_THEME), Icons.Outlined.Palette, stringResource(R.string.drawer_theme), supporting = themeLabel(ui.settings.theme), tone = 2) {
                        showTheme = true
                    }
                    RowDivider()
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
                            tone = 1,
                        ) { onNavigate(Routes.GUIDE) }
                    }
                    RowDivider()
                    SettingRow(
                        Modifier,
                        Icons.Outlined.Info,
                        stringResource(R.string.drawer_about),
                        supporting = stringResource(R.string.about_version, BuildConfig.VERSION_NAME, BuildConfig.VERSION_CODE),
                        tone = 3,
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
    val scheme = MaterialTheme.colorScheme
    val onHero = com.example.kasirarn.ui.components.heroColors().second
    HeroCard(
        Modifier.fillMaxWidth().coachTarget(CoachId.MORE_PROFILE),
        onClick = onClick,
        contentPadding = PaddingValues(18.dp),
    ) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Box(
                Modifier.size(58.dp).clip(CircleShape).background(onHero.copy(alpha = 0.18f)),
                contentAlignment = Alignment.Center,
            ) {
                if (ui.logo != null) {
                    MenuImage(photo = ui.logo, name = ui.businessName, modifier = Modifier.fillMaxSize(), shape = CircleShape)
                } else {
                    Text(
                        initials(ui.businessName.ifBlank { "ARN" }),
                        style = MaterialTheme.typography.titleMedium.copy(fontWeight = FontWeight.ExtraBold),
                        color = onHero,
                    )
                }
            }
            Spacer(Modifier.width(14.dp))
            Column(Modifier.weight(1f)) {
                Text(
                    ui.businessName.ifBlank { stringResource(R.string.more_business_unnamed) },
                    style = MaterialTheme.typography.titleLarge.copy(fontWeight = FontWeight.ExtraBold),
                    color = onHero,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                )
                Text(
                    stringResource(R.string.more_business_edit),
                    style = MaterialTheme.typography.bodySmall,
                    color = onHero.copy(alpha = 0.85f),
                )
            }
            Box(
                Modifier.size(36.dp).clip(CircleShape).background(onHero.copy(alpha = 0.18f)),
                contentAlignment = Alignment.Center,
            ) {
                Icon(Icons.AutoMirrored.Outlined.KeyboardArrowRight, contentDescription = null, tint = onHero)
            }
        }
    }
}

@Composable
private fun GroupLabel(text: String) {
    Text(
        text,
        style = MaterialTheme.typography.labelMedium.copy(fontWeight = FontWeight.Bold),
        color = MaterialTheme.colorScheme.primary,
        modifier = Modifier.padding(top = Spacing.md, start = Spacing.xs),
    )
}

@Composable
private fun SettingsGroup(content: @Composable () -> Unit) {
    ArnCard(Modifier.fillMaxWidth(), contentPadding = PaddingValues(vertical = 6.dp)) { Column { content() } }
}

@Composable
private fun RowDivider() {
    HorizontalDivider(Modifier.padding(horizontal = Spacing.lg), color = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.6f))
}

/** [tone] memilih pasangan warna kotak ikon: 0 jingga, 1 hijau, 2 kuning, 3 netral. */
@Composable
private fun SettingRow(
    modifier: Modifier = Modifier,
    icon: ImageVector,
    title: String,
    supporting: String? = null,
    supportingWarning: Boolean = false,
    badge: Int = 0,
    tone: Int = 0,
    onClick: () -> Unit,
) {
    val s = MaterialTheme.colorScheme
    val (tileBg, tileFg) = when (tone) {
        0 -> s.primaryContainer to s.onPrimaryContainer
        1 -> s.secondaryContainer to s.onSecondaryContainer
        2 -> s.tertiaryContainer to s.onTertiaryContainer
        else -> s.surfaceContainerHigh to s.onSurface
    }
    Row(
        modifier
            .fillMaxWidth()
            .heightIn(min = 64.dp)
            .clickable(role = Role.Button, onClick = onClick)
            .padding(horizontal = Spacing.lg, vertical = 10.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        IconTile(icon, tileBg, tileFg, size = 44.dp, corner = 14.dp)
        Spacer(Modifier.width(14.dp))
        Column(Modifier.weight(1f)) {
            Text(title, style = MaterialTheme.typography.titleSmall.copy(fontWeight = FontWeight.ExtraBold))
            if (supporting != null) {
                Text(
                    supporting,
                    style = MaterialTheme.typography.bodySmall,
                    color = if (supportingWarning) s.tertiary else s.onSurfaceVariant,
                    maxLines = 2,
                    overflow = TextOverflow.Ellipsis,
                )
            }
        }
        if (badge > 0) {
            Box(Modifier.size(24.dp).clip(CircleShape).background(s.primary), contentAlignment = Alignment.Center) {
                Text(
                    if (badge > 99) "99+" else badge.toString(),
                    style = MaterialTheme.typography.labelSmall.copy(fontWeight = FontWeight.ExtraBold),
                    color = s.onPrimary,
                )
            }
            Spacer(Modifier.width(Spacing.sm))
        }
        Icon(Icons.AutoMirrored.Outlined.KeyboardArrowRight, contentDescription = null, tint = s.onSurface)
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
