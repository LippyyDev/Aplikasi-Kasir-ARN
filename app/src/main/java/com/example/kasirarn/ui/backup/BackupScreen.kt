package com.example.kasirarn.ui.backup

import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
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
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.Backup
import androidx.compose.material.icons.outlined.DeleteForever
import androidx.compose.material.icons.outlined.RestoreFromTrash
import androidx.compose.material.icons.outlined.Science
import androidx.compose.material.icons.outlined.Shield
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.foundation.background
import androidx.compose.ui.draw.clip
import androidx.compose.foundation.layout.Box
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import androidx.lifecycle.ViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewModelScope
import com.example.kasirarn.AppContainer
import com.example.kasirarn.R
import com.example.kasirarn.data.backup.BackupError
import com.example.kasirarn.data.backup.BackupException
import com.example.kasirarn.data.backup.BackupManager
import com.example.kasirarn.data.backup.RestorePreview
import com.example.kasirarn.data.notify.get
import com.example.kasirarn.data.settings.AppSettings
import com.example.kasirarn.ui.components.ArnCard
import com.example.kasirarn.ui.components.ArnTopBar
import com.example.kasirarn.ui.components.ChoiceChips
import com.example.kasirarn.ui.components.PrimaryButton
import com.example.kasirarn.ui.components.SecondaryButton
import com.example.kasirarn.ui.components.SectionLabel
import com.example.kasirarn.ui.format.LocalFmt
import com.example.kasirarn.ui.nav.containerViewModel
import com.example.kasirarn.ui.onboarding.CoachId
import com.example.kasirarn.ui.onboarding.coachTarget
import com.example.kasirarn.ui.theme.Spacing
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch

sealed interface BackupMessage {
    data object BackupDone : BackupMessage
    data class Failed(val error: BackupError) : BackupMessage
    data object RestoreRolledBack : BackupMessage
    data class DummyDone(val summary: com.example.kasirarn.data.dummy.DummySummary) : BackupMessage
    data object DummyFailed : BackupMessage
}

class BackupViewModel(private val c: AppContainer) : ViewModel() {
    val settings: StateFlow<AppSettings> =
        c.settings.settings.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), AppSettings())

    var busy by mutableStateOf(false)
        private set
    var preview by mutableStateOf<RestorePreview?>(null)
        private set
    var message by mutableStateOf<BackupMessage?>(null)
        private set

    var dummyAvailable by mutableStateOf(false)
        private set

    init { refreshDummy() }

    private fun refreshDummy() {
        viewModelScope.launch { dummyAvailable = c.dummy.isEmpty() }
    }

    fun loadDummy(scenario: com.example.kasirarn.data.dummy.DummyScenario) {
        busy = true
        viewModelScope.launch {
            message = try {
                BackupMessage.DummyDone(c.dummy.loadBundled(scenario))
            } catch (e: Exception) {
                BackupMessage.DummyFailed
            }
            busy = false
            refreshDummy()
        }
    }

    /** Hapus semua data lalu mulai ulang; salinan pengaman dibuat otomatis oleh BackupManager.wipeAll. */
    fun wipeAll() {
        busy = true
        viewModelScope.launch {
            val r = c.backup.wipeAll()
            if (r.restartRequired) c.backup.restartApp() else busy = false
        }
    }

    fun suggestedName() = c.backup.suggestedFileName()
    fun consumeMessage() { message = null }

    fun setReminderDays(days: Int) { viewModelScope.launch { c.settings.setReminderDays(days) } }

    fun createBackup(uri: android.net.Uri) {
        busy = true
        viewModelScope.launch {
            val result = c.backup.createBackup(uri)
            busy = false
            result.onSuccess {
                message = BackupMessage.BackupDone
                c.notifications.raiseBackupResult(true, c.texts.get(R.string.backup_done_notification))
            }.onFailure {
                val err = (it as? BackupException)?.error ?: BackupError.IO
                message = BackupMessage.Failed(err)
                c.notifications.raiseBackupResult(false, c.texts.get(R.string.backup_failed_notification))
            }
        }
    }

    fun prepareRestore(uri: android.net.Uri) {
        busy = true
        viewModelScope.launch {
            val result = c.backup.prepareRestore(uri)
            busy = false
            result.onSuccess { preview = it }.onFailure {
                message = BackupMessage.Failed((it as? BackupException)?.error ?: BackupError.IO)
            }
        }
    }

    fun cancelRestore() {
        preview = null
        c.backup.discardPendingRestore()
    }

    fun confirmRestore() {
        preview = null
        busy = true
        viewModelScope.launch {
            val r = c.backup.applyRestore()
            if (r.success) {
                c.backup.restartApp()
            } else {
                busy = false
                message = if (r.restartRequired) BackupMessage.RestoreRolledBack else BackupMessage.Failed(r.error ?: BackupError.IO)
            }
        }
    }

    fun restart() = c.backup.restartApp()
}

@Composable
fun BackupScreen(onBack: () -> Unit) {
    val vm = containerViewModel { BackupViewModel(it) }
    val settings by vm.settings.collectAsStateWithLifecycle()
    val fmt = LocalFmt.current
    val snackbar = remember { SnackbarHostState() }
    val createLauncher = rememberLauncherForActivityResult(ActivityResultContracts.CreateDocument(BackupManager.MIME)) { uri ->
        if (uri != null) vm.createBackup(uri)
    }
    val openLauncher = rememberLauncherForActivityResult(ActivityResultContracts.OpenDocument()) { uri ->
        if (uri != null) vm.prepareRestore(uri)
    }
    val message = vm.message
    var confirmDummy by remember { mutableStateOf(false) }
    var confirmWipe by remember { mutableStateOf(false) }
    val messageText = when (message) {
        BackupMessage.BackupDone -> stringResource(R.string.backup_done)
        is BackupMessage.Failed -> backupErrorText(message.error)
        BackupMessage.RestoreRolledBack -> null
        is BackupMessage.DummyDone -> stringResource(
            R.string.dummy_done,
            message.summary.transactions - message.summary.voided,
            message.summary.menus,
            message.summary.ingredients,
        )
        BackupMessage.DummyFailed -> stringResource(R.string.dummy_failed)
        null -> null
    }
    LaunchedEffect(message) {
        if (messageText != null) {
            snackbar.showSnackbar(messageText)
            vm.consumeMessage()
        }
    }

    val stale = settings.lastBackupAt == 0L ||
        System.currentTimeMillis() - settings.lastBackupAt > settings.backupReminderDays * 86_400_000L

    Scaffold(
        containerColor = MaterialTheme.colorScheme.background,
        snackbarHost = { SnackbarHost(snackbar) },
        topBar = { ArnTopBar(stringResource(R.string.backup_title), onBack) },
    ) { padding ->
        Column(
            Modifier.fillMaxSize().padding(padding).verticalScroll(rememberScrollState()).padding(horizontal = Spacing.lg),
            verticalArrangement = Arrangement.spacedBy(Spacing.md),
        ) {
            val scheme = MaterialTheme.colorScheme
            val statusBg = if (stale) scheme.tertiaryContainer else scheme.secondaryContainer
            val statusFg = if (stale) scheme.onTertiaryContainer else scheme.onSecondaryContainer
            ArnCard(Modifier.fillMaxWidth(), container = statusBg, contentPadding = androidx.compose.foundation.layout.PaddingValues(Spacing.lg)) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Box(
                        Modifier.size(52.dp).clip(androidx.compose.foundation.shape.CircleShape).background(if (stale) scheme.tertiary else scheme.secondary),
                        contentAlignment = Alignment.Center,
                    ) {
                        Icon(
                            Icons.Outlined.Backup,
                            contentDescription = null,
                            tint = if (stale) scheme.onTertiary else scheme.onSecondary,
                            modifier = Modifier.size(26.dp),
                        )
                    }
                    Spacer(Modifier.width(Spacing.md))
                    Column {
                        Text(stringResource(R.string.backup_last_title), style = MaterialTheme.typography.labelMedium, color = statusFg)
                        Text(
                            if (settings.lastBackupAt == 0L) stringResource(R.string.drawer_backup_never) else fmt.dateTime(settings.lastBackupAt),
                            style = MaterialTheme.typography.titleLarge.copy(fontWeight = androidx.compose.ui.text.font.FontWeight.ExtraBold),
                            color = statusFg,
                        )
                        if (stale) {
                            Text(
                                if (settings.lastBackupAt == 0L) stringResource(R.string.notif_reminder_never)
                                else stringResource(R.string.backup_stale, settings.backupReminderDays),
                                style = MaterialTheme.typography.bodySmall,
                                color = statusFg,
                            )
                        }
                    }
                }
            }

            BackupSection(stringResource(R.string.backup_create_title), stringResource(R.string.backup_create_body)) {
                PrimaryButton(
                    text = stringResource(R.string.backup_create),
                    onClick = { createLauncher.launch(vm.suggestedName()) },
                    enabled = !vm.busy,
                    modifier = Modifier.fillMaxWidth().coachTarget(CoachId.BACKUP_CREATE),
                    icon = Icons.Outlined.Backup,
                )
            }

            BackupSection(stringResource(R.string.backup_restore_title), stringResource(R.string.backup_restore_body)) {
                SecondaryButton(
                    text = stringResource(R.string.backup_restore),
                    onClick = { openLauncher.launch(arrayOf("*/*")) },
                    enabled = !vm.busy,
                    modifier = Modifier.fillMaxWidth().coachTarget(CoachId.BACKUP_RESTORE),
                    icon = Icons.Outlined.RestoreFromTrash,
                )
            }

            BackupSection(stringResource(R.string.backup_reminder_title), null) {
                ChoiceChips(
                    modifier = Modifier.coachTarget(CoachId.BACKUP_REMINDER),
                    options = listOf(3, 7, 14, 30).map { it to stringResource(R.string.backup_every_days, it) },
                    selected = settings.backupReminderDays,
                    onSelect = vm::setReminderDays,
                )
            }

            BackupSection(stringResource(R.string.dummy_title), stringResource(R.string.dummy_body)) {
                SecondaryButton(
                    text = stringResource(R.string.dummy_load),
                    onClick = { confirmDummy = true },
                    enabled = !vm.busy && vm.dummyAvailable,
                    modifier = Modifier.fillMaxWidth().coachTarget(CoachId.BACKUP_SAMPLE),
                    icon = Icons.Outlined.Science,
                )
                if (!vm.dummyAvailable) {
                    Text(
                        stringResource(R.string.dummy_not_empty),
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
            }

            BackupSection(stringResource(R.string.wipe_title), stringResource(R.string.wipe_body)) {
                androidx.compose.material3.OutlinedButton(
                    onClick = { confirmWipe = true },
                    enabled = !vm.busy,
                    modifier = Modifier.fillMaxWidth().heightIn(min = 56.dp).coachTarget(CoachId.BACKUP_WIPE),
                    shape = androidx.compose.foundation.shape.RoundedCornerShape(50),
                    border = androidx.compose.foundation.BorderStroke(1.5.dp, MaterialTheme.colorScheme.error),
                ) {
                    Icon(Icons.Outlined.DeleteForever, contentDescription = null, tint = MaterialTheme.colorScheme.error)
                    Spacer(Modifier.width(Spacing.sm))
                    Text(stringResource(R.string.wipe_button), color = MaterialTheme.colorScheme.error, style = MaterialTheme.typography.labelLarge.copy(fontWeight = androidx.compose.ui.text.font.FontWeight.ExtraBold))
                }
            }

            ArnCard(Modifier.fillMaxWidth(), container = scheme.tertiaryContainer.copy(alpha = 0.5f)) {
                Row {
                    Icon(Icons.Outlined.Shield, contentDescription = null, tint = MaterialTheme.colorScheme.tertiary)
                    Spacer(Modifier.width(Spacing.md))
                    Text(
                        stringResource(R.string.backup_security_note),
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
            }
            Spacer(Modifier.height(Spacing.xl))
        }
    }

    if (confirmDummy) {
        DummyScenarioDialog(
            onDismiss = { confirmDummy = false },
            onConfirm = { scenario ->
                confirmDummy = false
                vm.loadDummy(scenario)
            },
        )
    }
    if (confirmWipe) {
        com.example.kasirarn.ui.components.ConfirmDialog(
            title = stringResource(R.string.wipe_confirm_title),
            text = stringResource(R.string.wipe_confirm_body),
            confirmLabel = stringResource(R.string.wipe_confirm),
            dismissLabel = stringResource(R.string.action_cancel),
            destructive = true,
            onDismiss = { confirmWipe = false },
            onConfirm = {
                confirmWipe = false
                vm.wipeAll()
            },
        )
    }
    vm.preview?.let { p ->
        AlertDialog(
            onDismissRequest = vm::cancelRestore,
            title = { Text(stringResource(R.string.restore_confirm_title)) },
            text = {
                Column(verticalArrangement = Arrangement.spacedBy(Spacing.xs)) {
                    Text(stringResource(R.string.restore_summary_date, fmt.dateTime(p.createdAt)), style = MaterialTheme.typography.bodyMedium)
                    Text(stringResource(R.string.restore_summary_menus, fmt.number(p.menuCount)), style = MaterialTheme.typography.bodyMedium)
                    Text(stringResource(R.string.restore_summary_ingredients, fmt.number(p.ingredientCount)), style = MaterialTheme.typography.bodyMedium)
                    Text(stringResource(R.string.restore_summary_transactions, fmt.number(p.transactionCount)), style = MaterialTheme.typography.bodyMedium)
                    Spacer(Modifier.height(Spacing.sm))
                    Text(
                        stringResource(R.string.restore_warning),
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.error,
                    )
                }
            },
            confirmButton = {
                TextButton(onClick = vm::confirmRestore) {
                    Text(stringResource(R.string.restore_confirm), color = MaterialTheme.colorScheme.error)
                }
            },
            dismissButton = { TextButton(onClick = vm::cancelRestore) { Text(stringResource(R.string.action_cancel)) } },
            containerColor = MaterialTheme.colorScheme.surfaceContainerHigh,
        )
    }
    if (message == BackupMessage.RestoreRolledBack) {
        AlertDialog(
            onDismissRequest = {},
            title = { Text(stringResource(R.string.restore_failed_title)) },
            text = { Text(stringResource(R.string.restore_rolled_back)) },
            confirmButton = { TextButton(onClick = vm::restart) { Text(stringResource(R.string.restore_restart)) } },
            containerColor = MaterialTheme.colorScheme.surfaceContainerHigh,
        )
    }
    if (vm.busy) {
        AlertDialog(
            onDismissRequest = {},
            confirmButton = {},
            text = {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    CircularProgressIndicator(Modifier.size(28.dp), strokeWidth = 3.dp)
                    Spacer(Modifier.width(Spacing.lg))
                    Text(stringResource(R.string.backup_working))
                }
            },
            containerColor = MaterialTheme.colorScheme.surfaceContainerHigh,
        )
    }
}

@Composable
private fun backupErrorText(error: BackupError): String = stringResource(
    when (error) {
        BackupError.NOT_ARCHIVE -> R.string.backup_err_not_archive
        BackupError.MANIFEST_INVALID -> R.string.backup_err_manifest
        BackupError.CHECKSUM_MISMATCH -> R.string.backup_err_checksum
        BackupError.NEWER_SCHEMA -> R.string.backup_err_newer
        BackupError.DB_INVALID -> R.string.backup_err_db
        BackupError.UNSAFE_ENTRY -> R.string.backup_err_unsafe
        BackupError.IO -> R.string.backup_err_io
    },
)

/** Satu bagian halaman cadangan dalam kartu putih: judul, uraian singkat, lalu isinya. */
@Composable
private fun BackupSection(title: String, body: String?, content: @Composable () -> Unit) {
    ArnCard(Modifier.fillMaxWidth(), contentPadding = androidx.compose.foundation.layout.PaddingValues(Spacing.lg)) {
        Column(verticalArrangement = Arrangement.spacedBy(Spacing.md)) {
            Text(title, style = MaterialTheme.typography.titleMedium.copy(fontWeight = androidx.compose.ui.text.font.FontWeight.ExtraBold))
            if (body != null) {
                Text(body, style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
            }
            content()
        }
    }
}
