package com.example.kasirarn.ui.report

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.Add
import androidx.compose.material.icons.outlined.MoreVert
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.ExtendedFloatingActionButton
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import androidx.lifecycle.ViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewModelScope
import com.example.kasirarn.AppContainer
import com.example.kasirarn.R
import com.example.kasirarn.data.db.ExpenseCategoryEntity
import com.example.kasirarn.data.db.ReservedExpenseCategory
import com.example.kasirarn.data.repo.Op
import com.example.kasirarn.ui.components.ArnCard
import com.example.kasirarn.ui.components.ArnTopBar
import com.example.kasirarn.ui.components.ConfirmDialog
import com.example.kasirarn.ui.components.StatusPill
import com.example.kasirarn.ui.components.Tone
import com.example.kasirarn.ui.home.RadioRow
import com.example.kasirarn.ui.menu.NameDialog
import com.example.kasirarn.ui.nav.containerViewModel
import com.example.kasirarn.ui.theme.Spacing
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch

class ExpenseCategoriesViewModel(private val c: AppContainer) : ViewModel() {
    val categories: StateFlow<List<ExpenseCategoryEntity>> =
        c.expenses.observeCategories().stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), emptyList())

    suspend fun add(name: String) = c.expenses.addCategory(name)
    suspend fun rename(id: Long, name: String) = c.expenses.renameCategory(id, name)
    suspend fun count(id: Long) = c.expenses.countIn(id)

    fun delete(id: Long, moveTo: Long, done: (Op<Unit>) -> Unit) {
        viewModelScope.launch { done(c.expenses.deleteCategory(id, moveTo)) }
    }
}

@Composable
fun ExpenseCategoriesScreen(onBack: () -> Unit) {
    val vm = containerViewModel { ExpenseCategoriesViewModel(it) }
    val categories by vm.categories.collectAsStateWithLifecycle()
    val snackbar = remember { SnackbarHostState() }
    val scope = rememberCoroutineScope()
    val context = LocalContext.current
    val resources = androidx.compose.ui.platform.LocalResources.current
    var dialog by remember { mutableStateOf<ExpenseCategoryEntity?>(null) }
    var adding by remember { mutableStateOf(false) }
    var deleting by remember { mutableStateOf<Pair<ExpenseCategoryEntity, Int>?>(null) }

    Scaffold(
        containerColor = MaterialTheme.colorScheme.background,
        snackbarHost = { SnackbarHost(snackbar) },
        topBar = { ArnTopBar(stringResource(R.string.expcat_title), onBack) },
        floatingActionButton = {
            ExtendedFloatingActionButton(
                onClick = { adding = true },
                icon = { Icon(Icons.Outlined.Add, contentDescription = null) },
                text = { Text(stringResource(R.string.expcat_add)) },
                containerColor = MaterialTheme.colorScheme.primary,
                contentColor = MaterialTheme.colorScheme.onPrimary,
            )
        },
    ) { padding ->
        LazyColumn(
            Modifier.fillMaxSize().padding(padding),
            contentPadding = PaddingValues(start = Spacing.lg, end = Spacing.lg, bottom = 96.dp, top = Spacing.sm),
            verticalArrangement = Arrangement.spacedBy(Spacing.sm),
        ) {
            item {
                Text(
                    stringResource(R.string.expcat_hint),
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
            items(categories, key = { it.id }) { cat ->
                val reserved = ReservedExpenseCategory.isReserved(cat.id)
                var menuOpen by remember { mutableStateOf(false) }
                ArnCard(contentPadding = PaddingValues(start = Spacing.lg, top = Spacing.xs, bottom = Spacing.xs, end = Spacing.xs)) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Text(cat.nama, style = MaterialTheme.typography.titleSmall, modifier = Modifier.weight(1f))
                        if (reserved) {
                            StatusPill(stringResource(R.string.expcat_system), Tone.NEUTRAL, Modifier.padding(end = Spacing.md, top = Spacing.md, bottom = Spacing.md))
                        } else {
                            IconButton(onClick = { menuOpen = true }) {
                                Icon(Icons.Outlined.MoreVert, contentDescription = stringResource(R.string.action_more))
                            }
                            DropdownMenu(expanded = menuOpen, onDismissRequest = { menuOpen = false }) {
                                DropdownMenuItem(
                                    text = { Text(stringResource(R.string.category_rename)) },
                                    onClick = {
                                        menuOpen = false
                                        dialog = cat
                                    },
                                )
                                DropdownMenuItem(
                                    text = { Text(stringResource(R.string.action_delete), color = MaterialTheme.colorScheme.error) },
                                    onClick = {
                                        menuOpen = false
                                        scope.launch { deleting = cat to vm.count(cat.id) }
                                    },
                                )
                            }
                        }
                    }
                }
            }
        }
    }

    if (adding) {
        NameDialog(
            title = stringResource(R.string.expcat_add),
            label = stringResource(R.string.category_name),
            initial = "",
            onDismiss = { adding = false },
            onSubmit = { text ->
                val r = vm.add(text)
                if (r is Op.Fail) r else {
                    adding = false
                    null
                }
            },
        )
    }
    dialog?.let { cat ->
        NameDialog(
            title = stringResource(R.string.category_rename),
            label = stringResource(R.string.category_name),
            initial = cat.nama,
            onDismiss = { dialog = null },
            onSubmit = { text ->
                val r = vm.rename(cat.id, text)
                if (r is Op.Fail) r else {
                    dialog = null
                    null
                }
            },
        )
    }
    deleting?.let { (cat, used) ->
        val targets = categories.filter { it.id != cat.id && !ReservedExpenseCategory.isReserved(it.id) }
        if (used == 0) {
            ConfirmDialog(
                title = stringResource(R.string.category_delete_title, cat.nama),
                text = stringResource(R.string.category_delete_empty_expense),
                confirmLabel = stringResource(R.string.action_delete),
                dismissLabel = stringResource(R.string.action_cancel),
                destructive = true,
                onDismiss = { deleting = null },
                onConfirm = {
                    deleting = null
                    vm.delete(cat.id, targets.firstOrNull()?.id ?: ReservedExpenseCategory.INGREDIENTS) { }
                },
            )
        } else if (targets.isEmpty()) {
            AlertDialog(
                onDismissRequest = { deleting = null },
                title = { Text(stringResource(R.string.category_delete_title, cat.nama)) },
                text = { Text(stringResource(R.string.expcat_cannot_delete)) },
                confirmButton = { TextButton(onClick = { deleting = null }) { Text(stringResource(R.string.action_ok)) } },
                containerColor = MaterialTheme.colorScheme.surfaceContainerHigh,
            )
        } else {
            var target by remember { mutableStateOf(targets.first().id) }
            AlertDialog(
                onDismissRequest = { deleting = null },
                title = { Text(stringResource(R.string.category_delete_title, cat.nama)) },
                text = {
                    Column(Modifier.verticalScroll(rememberScrollState())) {
                        Text(stringResource(R.string.expcat_move_prompt, used), style = MaterialTheme.typography.bodyMedium)
                        Spacer(Modifier.height(Spacing.sm))
                        targets.forEach { t -> RadioRow(t.nama, target == t.id) { target = t.id } }
                    }
                },
                confirmButton = {
                    TextButton(onClick = {
                        deleting = null
                        vm.delete(cat.id, target) { r ->
                            if (r is Op.Fail) scope.launch { snackbar.showSnackbar(resources.getString(R.string.err_storage)) }
                        }
                    }) { Text(stringResource(R.string.category_delete_confirm), color = MaterialTheme.colorScheme.error) }
                },
                dismissButton = { TextButton(onClick = { deleting = null }) { Text(stringResource(R.string.action_cancel)) } },
                containerColor = MaterialTheme.colorScheme.surfaceContainerHigh,
            )
        }
    }
}
