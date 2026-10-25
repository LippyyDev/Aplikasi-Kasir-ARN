package com.example.kasirarn.ui.misc

import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.PickVisualMediaRequest
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.AddPhotoAlternate
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.Icon
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.unit.dp
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.example.kasirarn.AppContainer
import com.example.kasirarn.R
import com.example.kasirarn.data.db.BusinessProfileEntity
import com.example.kasirarn.ui.components.ArnField
import com.example.kasirarn.ui.components.ArnTopBar
import com.example.kasirarn.ui.components.MenuImage
import com.example.kasirarn.ui.components.PrimaryButton
import com.example.kasirarn.ui.nav.containerViewModel
import com.example.kasirarn.ui.theme.Spacing
import kotlinx.coroutines.launch

class ProfileViewModel(private val c: AppContainer) : ViewModel() {
    var loaded by mutableStateOf(false)
        private set
    var name by mutableStateOf("")
    var address by mutableStateOf("")
    var phone by mutableStateOf("")
    var currency by mutableStateOf("Rp")
    var logo by mutableStateOf<String?>(null)
        private set
    private var originalLogo: String? = null
    private val sessionLogos = mutableSetOf<String>()
    private var saved = false

    init {
        viewModelScope.launch {
            val p = c.profile.get()
            name = p.nama
            address = p.alamat
            phone = p.telepon
            currency = p.mataUang
            logo = p.logoPath
            originalLogo = p.logoPath
            loaded = true
        }
    }

    fun pickLogo(uri: android.net.Uri) {
        viewModelScope.launch {
            val file = c.images.saveFromUri(uri, "logo", maxSide = 512) ?: return@launch
            logo?.takeIf { it in sessionLogos }?.let { c.images.delete(it); sessionLogos -= it }
            sessionLogos += file
            logo = file
        }
    }

    fun removeLogo() {
        logo?.takeIf { it in sessionLogos }?.let { c.images.delete(it); sessionLogos -= it }
        logo = null
    }

    fun save(onDone: () -> Unit) {
        viewModelScope.launch {
            c.profile.save(BusinessProfileEntity(nama = name, logoPath = logo, alamat = address, telepon = phone, mataUang = currency))
            saved = true
            if (originalLogo != null && originalLogo != logo) c.images.delete(originalLogo)
            sessionLogos.filter { it != logo }.forEach { c.images.delete(it) }
            onDone()
        }
    }

    override fun onCleared() {
        if (!saved) sessionLogos.forEach { c.images.delete(it) }
    }
}

@Composable
fun ProfileScreen(onBack: () -> Unit) {
    val vm = containerViewModel { ProfileViewModel(it) }
    val picker = rememberLauncherForActivityResult(ActivityResultContracts.PickVisualMedia()) { uri ->
        if (uri != null) vm.pickLogo(uri)
    }
    Scaffold(
        containerColor = MaterialTheme.colorScheme.background,
        topBar = { ArnTopBar(stringResource(R.string.profile_title), onBack) },
        bottomBar = {
            Surface(color = MaterialTheme.colorScheme.background) {
                PrimaryButton(
                    text = stringResource(R.string.action_save),
                    onClick = { vm.save(onBack) },
                    enabled = vm.loaded,
                    modifier = Modifier.fillMaxWidth().navigationBarsPadding().imePadding().padding(Spacing.lg),
                )
            }
        },
    ) { padding ->
        if (!vm.loaded) return@Scaffold
        Column(
            Modifier.fillMaxSize().padding(padding).verticalScroll(rememberScrollState()).padding(horizontal = Spacing.lg),
            verticalArrangement = Arrangement.spacedBy(Spacing.md),
        ) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Box(
                    Modifier
                        .size(88.dp)
                        .clip(CircleShape)
                        .background(MaterialTheme.colorScheme.primaryContainer)
                        .clickable(role = Role.Button) {
                            picker.launch(PickVisualMediaRequest(ActivityResultContracts.PickVisualMedia.ImageOnly))
                        },
                    contentAlignment = Alignment.Center,
                ) {
                    if (vm.logo != null) {
                        MenuImage(photo = vm.logo, name = vm.name, modifier = Modifier.fillMaxSize(), shape = CircleShape)
                    } else {
                        Icon(
                            Icons.Outlined.AddPhotoAlternate,
                            contentDescription = stringResource(R.string.profile_logo_add),
                            tint = MaterialTheme.colorScheme.onPrimaryContainer,
                            modifier = Modifier.size(32.dp),
                        )
                    }
                }
                Spacer(Modifier.width(Spacing.lg))
                Column {
                    Text(stringResource(R.string.profile_logo), style = MaterialTheme.typography.titleSmall)
                    Text(
                        stringResource(R.string.profile_logo_hint),
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                    if (vm.logo != null) {
                        TextButton(onClick = vm::removeLogo) { Text(stringResource(R.string.profile_logo_remove)) }
                    }
                }
            }
            Text(
                stringResource(R.string.profile_hint),
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
            ArnField(vm.name, { vm.name = it.take(60) }, stringResource(R.string.profile_name))
            ArnField(vm.address, { vm.address = it.take(120) }, stringResource(R.string.profile_address), singleLine = false, minLines = 2)
            ArnField(
                vm.phone,
                { vm.phone = it.take(20) },
                stringResource(R.string.profile_phone),
                keyboardType = androidx.compose.ui.text.input.KeyboardType.Phone,
            )
            ArnField(
                vm.currency,
                { vm.currency = it.take(4) },
                stringResource(R.string.profile_currency),
                supporting = stringResource(R.string.profile_currency_hint),
                imeAction = androidx.compose.ui.text.input.ImeAction.Done,
            )
            Spacer(Modifier.height(Spacing.xl))
        }
    }
}
