package com.example.kasirarn.ui.misc

import android.Manifest
import android.content.Intent
import android.os.Build
import android.provider.Settings
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
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
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.foundation.selection.toggleable
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.Backup
import androidx.compose.material.icons.outlined.DoneAll
import androidx.compose.material.icons.outlined.Inventory2
import androidx.compose.material.icons.outlined.Notifications
import androidx.compose.material.icons.outlined.Tune
import androidx.compose.material.icons.outlined.WarningAmber
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.unit.dp
import androidx.lifecycle.ViewModel
import androidx.lifecycle.compose.LifecycleResumeEffect
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewModelScope
import com.example.kasirarn.AppContainer
import com.example.kasirarn.R
import com.example.kasirarn.data.db.NotifType
import com.example.kasirarn.data.db.NotificationEntity
import com.example.kasirarn.data.settings.AppSettings
import com.example.kasirarn.ui.components.ArnCard
import com.example.kasirarn.ui.components.ArnTopBar
import com.example.kasirarn.ui.components.EmptyState
import com.example.kasirarn.ui.components.PrimaryButton
import com.example.kasirarn.ui.components.SecondaryButton
import com.example.kasirarn.ui.format.LocalFmt
import com.example.kasirarn.ui.nav.containerViewModel
import com.example.kasirarn.ui.theme.Spacing
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch

class NotificationsViewModel(private val c: AppContainer) : ViewModel() {
    val items: StateFlow<List<NotificationEntity>> =
        c.notifications.observeAll().stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), emptyList())
    val settings: StateFlow<AppSettings> =
        c.settings.settings.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), AppSettings())

    fun canPost() = c.notifier.canPost()
    fun needsRuntimePermission() = c.notifier.needsRuntimePermission()

    fun markRead(id: Long) { viewModelScope.launch { c.notifications.markRead(id) } }
    fun markAllRead() { viewModelScope.launch { c.notifications.markAllRead() } }
    fun setLow(on: Boolean) { viewModelScope.launch { c.settings.setNotifyLowStock(on) } }
    fun setOut(on: Boolean) { viewModelScope.launch { c.settings.setNotifyOutOfStock(on) } }
    fun setReminder(on: Boolean) { viewModelScope.launch { c.settings.setNotifyBackupReminder(on) } }
    fun setResult(on: Boolean) { viewModelScope.launch { c.settings.setNotifyBackupResult(on) } }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun NotificationsScreen(onBack: () -> Unit) {
    val vm = containerViewModel { NotificationsViewModel(it) }
    val items by vm.items.collectAsStateWithLifecycle()
    val settings by vm.settings.collectAsStateWithLifecycle()
    val fmt = LocalFmt.current
    var showSettings by remember { mutableStateOf(false) }
    val hasUnread = items.any { !it.dibaca }

    Scaffold(
        containerColor = MaterialTheme.colorScheme.background,
        topBar = {
            ArnTopBar(
                stringResource(R.string.notif_title),
                onBack,
                actions = {
                    IconButton(onClick = vm::markAllRead, enabled = hasUnread) {
                        Icon(Icons.Outlined.DoneAll, contentDescription = stringResource(R.string.notif_mark_all))
                    }
                    IconButton(onClick = { showSettings = true }) {
                        Icon(Icons.Outlined.Tune, contentDescription = stringResource(R.string.notif_settings))
                    }
                },
            )
        },
    ) { padding ->
        if (items.isEmpty()) {
            Column(Modifier.fillMaxSize().padding(padding), verticalArrangement = Arrangement.Center) {
                EmptyState(
                    icon = Icons.Outlined.Notifications,
                    title = stringResource(R.string.notif_empty_title),
                    body = stringResource(R.string.notif_empty_body),
                )
            }
        } else {
            LazyColumn(
                Modifier.fillMaxSize().padding(padding),
                contentPadding = PaddingValues(bottom = Spacing.xl),
            ) {
                items(items, key = { it.id }) { n ->
                    NotificationRow(n, fmt.dateTime(n.waktu)) { vm.markRead(n.id) }
                    HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant)
                }
            }
        }
    }

    if (showSettings) {
        NotificationSettingsSheet(vm, settings) { showSettings = false }
    }
}

@Composable
private fun NotificationRow(n: NotificationEntity, time: String, onClick: () -> Unit) {
    val scheme = MaterialTheme.colorScheme
    val (icon, bg, fg) = when (n.jenis) {
        NotifType.LOW_STOCK -> Triple(Icons.Outlined.WarningAmber, scheme.tertiaryContainer, scheme.onTertiaryContainer)
        NotifType.OUT_OF_STOCK -> Triple(Icons.Outlined.Inventory2, scheme.errorContainer, scheme.onErrorContainer)
        else -> Triple(Icons.Outlined.Backup, scheme.secondaryContainer, scheme.onSecondaryContainer)
    }
    Row(
        Modifier
            .fillMaxWidth()
            .background(if (n.dibaca) scheme.background else scheme.surfaceContainerLow)
            .clickable(onClick = onClick)
            .padding(horizontal = Spacing.lg, vertical = Spacing.md),
        verticalAlignment = Alignment.Top,
    ) {
        Box(Modifier.size(40.dp).clip(CircleShape).background(bg), contentAlignment = Alignment.Center) {
            Icon(icon, contentDescription = null, tint = fg, modifier = Modifier.size(20.dp))
        }
        Spacer(Modifier.width(Spacing.md))
        Column(Modifier.weight(1f)) {
            Text(
                n.judul,
                style = if (n.dibaca) MaterialTheme.typography.titleSmall else MaterialTheme.typography.titleSmall.copy(fontWeight = androidx.compose.ui.text.font.FontWeight.Bold),
            )
            Text(n.isi, style = MaterialTheme.typography.bodyMedium, color = scheme.onSurfaceVariant)
            Spacer(Modifier.height(Spacing.xs))
            Text(time, style = MaterialTheme.typography.bodySmall, color = scheme.onSurfaceVariant)
        }
        if (!n.dibaca) {
            Box(Modifier.padding(top = 6.dp, start = Spacing.sm).size(8.dp).clip(CircleShape).background(scheme.primary))
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun NotificationSettingsSheet(vm: NotificationsViewModel, settings: AppSettings, onDismiss: () -> Unit) {
    val context = LocalContext.current
    var canPost by remember { mutableStateOf(vm.canPost()) }
    var denied by remember { mutableStateOf(false) }
    LifecycleResumeEffect(Unit) {
        canPost = vm.canPost()
        onPauseOrDispose { }
    }
    val permission = rememberLauncherForActivityResult(ActivityResultContracts.RequestPermission()) { granted ->
        canPost = vm.canPost()
        denied = !granted
    }
    fun ensurePermission() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU && vm.needsRuntimePermission()) {
            permission.launch(Manifest.permission.POST_NOTIFICATIONS)
        }
    }

    ModalBottomSheet(onDismissRequest = onDismiss, sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true)) {
        Column(
            Modifier.navigationBarsPadding().verticalScroll(rememberScrollState()).padding(horizontal = Spacing.lg),
            verticalArrangement = Arrangement.spacedBy(Spacing.xs),
        ) {
            Text(stringResource(R.string.notif_settings), style = MaterialTheme.typography.titleLarge)
            Spacer(Modifier.height(Spacing.sm))
            if (!canPost) {
                ArnCard(Modifier.fillMaxWidth()) {
                    Column {
                        Text(stringResource(R.string.notif_permission_title), style = MaterialTheme.typography.titleSmall)
                        Text(
                            stringResource(if (denied) R.string.notif_permission_denied else R.string.notif_permission_body),
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                        Spacer(Modifier.height(Spacing.md))
                        if (vm.needsRuntimePermission() && !denied) {
                            PrimaryButton(stringResource(R.string.notif_permission_allow), onClick = ::ensurePermission, modifier = Modifier.fillMaxWidth())
                        } else {
                            SecondaryButton(
                                stringResource(R.string.notif_permission_open_settings),
                                onClick = {
                                    context.startActivity(
                                        Intent(Settings.ACTION_APP_NOTIFICATION_SETTINGS)
                                            .putExtra(Settings.EXTRA_APP_PACKAGE, context.packageName)
                                            .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK),
                                    )
                                },
                                modifier = Modifier.fillMaxWidth(),
                            )
                        }
                    }
                }
                Spacer(Modifier.height(Spacing.sm))
            }
            ToggleRow(stringResource(R.string.notif_type_low), stringResource(R.string.notif_type_low_hint), settings.notifyLowStock) {
                vm.setLow(it)
                if (it) ensurePermission()
            }
            ToggleRow(stringResource(R.string.notif_type_out), stringResource(R.string.notif_type_out_hint), settings.notifyOutOfStock) {
                vm.setOut(it)
                if (it) ensurePermission()
            }
            ToggleRow(stringResource(R.string.notif_type_reminder), stringResource(R.string.notif_type_reminder_hint, settings.backupReminderDays), settings.notifyBackupReminder) {
                vm.setReminder(it)
                if (it) ensurePermission()
            }
            ToggleRow(stringResource(R.string.notif_type_result), stringResource(R.string.notif_type_result_hint), settings.notifyBackupResult) {
                vm.setResult(it)
                if (it) ensurePermission()
            }
            Spacer(Modifier.height(Spacing.xl))
        }
    }
}

@Composable
private fun ToggleRow(title: String, hint: String, checked: Boolean, onChange: (Boolean) -> Unit) {
    Row(
        Modifier
            .fillMaxWidth()
            .toggleable(value = checked, role = Role.Switch, onValueChange = onChange)
            .padding(vertical = Spacing.md),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Column(Modifier.weight(1f)) {
            Text(title, style = MaterialTheme.typography.bodyLarge)
            Text(hint, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
        }
        Spacer(Modifier.width(Spacing.md))
        Switch(checked = checked, onCheckedChange = null)
    }
}
