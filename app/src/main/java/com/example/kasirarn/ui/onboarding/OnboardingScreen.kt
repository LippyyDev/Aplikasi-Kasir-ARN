package com.example.kasirarn.ui.onboarding

import androidx.activity.compose.BackHandler
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.PickVisualMediaRequest
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.animateContentSize
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.slideInHorizontally
import androidx.compose.animation.slideInVertically
import androidx.compose.animation.slideOutHorizontally
import androidx.compose.animation.slideOutVertically
import androidx.compose.animation.togetherWith
import androidx.compose.foundation.Image
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
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.pager.HorizontalPager
import androidx.compose.foundation.pager.rememberPagerState
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.outlined.ArrowBack
import androidx.compose.material.icons.outlined.AddPhotoAlternate
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material.icons.outlined.WifiOff
import androidx.compose.material.icons.outlined.Inventory2
import androidx.compose.material.icons.outlined.Payments
import androidx.compose.material.icons.automirrored.outlined.ArrowForward
import androidx.compose.ui.graphics.Color
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringArrayResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.heading
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.ViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewModelScope
import com.example.kasirarn.AppContainer
import com.example.kasirarn.R
import com.example.kasirarn.data.settings.AppLanguage
import com.example.kasirarn.ui.components.ArnField
import com.example.kasirarn.ui.components.MenuImage
import com.example.kasirarn.ui.components.PrimaryButton
import com.example.kasirarn.ui.components.SecondaryButton
import com.example.kasirarn.ui.misc.ProfileViewModel
import com.example.kasirarn.ui.nav.containerViewModel
import com.example.kasirarn.ui.theme.Spacing
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch

/** Tahap pengenalan: 0 belum mulai (tampilkan sambutan), 1 tinggal tutorial sorot, 2 selesai atau dilewati. */
object OnboardingStage {
    const val WELCOME = 0
    const val TOUR = 1
    const val DONE = 2
}

class OnboardingViewModel(private val c: AppContainer) : ViewModel() {
    val stage: StateFlow<Int> = c.settings.settings.map { it.onboardingStage }
        .stateIn(viewModelScope, SharingStarted.Eagerly, c.initialSettings.onboardingStage)
    val language: StateFlow<AppLanguage> = c.settings.settings.map { it.language }
        .stateIn(viewModelScope, SharingStarted.Eagerly, c.initialSettings.language)

    fun setStage(stage: Int) { viewModelScope.launch { c.settings.setOnboardingStage(stage) } }
    fun setLanguage(language: AppLanguage) { viewModelScope.launch { c.settings.setLanguage(language) } }
}

private val ContentWidth = 520.dp
private val SidePadding = 28.dp

/** Halaman: 0 sambutan, 1 nama, 2 logo, 3 alamat dan telepon, 4 sampai 8 penjelasan fitur. */
private const val FIRST_FEATURE = 4
private const val LAST_PAGE = 8

/** Alur pertama kali, satu hal per layar. Disusul tutorial sorot di aplikasi. */
@Composable
fun OnboardingFlow() {
    val vm = containerViewModel { OnboardingViewModel(it) }
    val profile = containerViewModel { ProfileViewModel(it) }
    var page by rememberSaveable { mutableIntStateOf(0) }
    BackHandler(enabled = page > 0) { page -= 1 }

    Surface(color = MaterialTheme.colorScheme.background, modifier = Modifier.fillMaxSize()) {
        Box(Modifier.fillMaxSize(), contentAlignment = Alignment.TopCenter) {
            Column(Modifier.widthIn(max = ContentWidth).fillMaxSize().statusBarsPadding()) {
                if (page > 0) {
                    TopBar(page = page, onBack = { page -= 1 })
                }
                AnimatedContent(
                    targetState = page,
                    modifier = Modifier.weight(1f),
                    transitionSpec = {
                        val forward = targetState > initialState
                        (slideInHorizontally(tween(320)) { if (forward) it / 5 else -it / 5 } + fadeIn(tween(320))) togetherWith
                            (slideOutHorizontally(tween(220)) { if (forward) -it / 5 else it / 5 } + fadeOut(tween(160)))
                    },
                    label = "onboarding",
                ) { p ->
                    when (p) {
                        0 -> WelcomePage(
                            language = vm.language.collectAsStateWithLifecycle().value,
                            onLanguage = vm::setLanguage,
                            onStart = { page = 1 },
                            onSkip = { vm.setStage(OnboardingStage.DONE) },
                        )
                        1 -> NamePage(profile, onNext = { page = 2 }, onLater = { page = FIRST_FEATURE })
                        2 -> LogoPage(profile, onNext = { page = 3 })
                        3 -> ContactPage(profile, onDone = { page = FIRST_FEATURE })
                        else -> FeaturePage(
                            index = p - FIRST_FEATURE,
                            onNext = { page = p + 1 },
                            onFinish = { vm.setStage(OnboardingStage.TOUR) },
                            onSkip = { vm.setStage(OnboardingStage.DONE) },
                        )
                    }
                }
            }
        }
    }
}

/** Tombol kembali bulat dan garis kemajuan. */
@Composable
private fun TopBar(page: Int, onBack: () -> Unit) {
    val progress by animateFloatAsState(page.toFloat() / LAST_PAGE, tween(400), label = "progress")
    Row(
        Modifier.fillMaxWidth().padding(start = 16.dp, end = SidePadding, top = 8.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(14.dp),
    ) {
        com.example.kasirarn.ui.components.TonalIconAction(onClick = onBack) {
            Icon(Icons.AutoMirrored.Outlined.ArrowBack, contentDescription = stringResource(R.string.action_back))
        }
        LinearProgressIndicator(
            progress = { progress },
            modifier = Modifier.weight(1f).height(6.dp).clip(CircleShape),
            color = MaterialTheme.colorScheme.primary,
            trackColor = MaterialTheme.colorScheme.surfaceContainerHigh,
            gapSize = 0.dp,
            drawStopIndicator = {},
        )
    }
}

// ------------------------------------------------------------------------------------------ sambutan

@Composable
private fun WelcomePage(language: AppLanguage, onLanguage: (AppLanguage) -> Unit, onStart: () -> Unit, onSkip: () -> Unit) {
    val scheme = MaterialTheme.colorScheme
    var shown by remember { mutableStateOf(false) }
    LaunchedEffect(Unit) { shown = true }
    Column(Modifier.fillMaxSize().padding(horizontal = SidePadding)) {
        Row(Modifier.fillMaxWidth().padding(top = 20.dp), verticalAlignment = Alignment.CenterVertically) {
            Image(painterResource(R.drawable.ic_logo_mark), contentDescription = null, modifier = Modifier.size(32.dp))
            Spacer(Modifier.width(10.dp))
            Text(
                stringResource(R.string.ob_brand_a) + " " + stringResource(R.string.ob_brand_b),
                style = MaterialTheme.typography.titleMedium.copy(fontWeight = FontWeight.Bold),
            )
            Spacer(Modifier.weight(1f))
            LanguageToggle(language, onLanguage)
        }
        Column(Modifier.weight(1f).verticalScroll(rememberScrollState()), verticalArrangement = Arrangement.Center) {
            Reveal(shown, 0) {
                IllustrationCard(Illustration.WELCOME, Modifier.padding(top = Spacing.lg), height = 220.dp)
            }
            Reveal(shown, 1) {
                Text(
                    stringResource(R.string.ob_head_1),
                    style = MaterialTheme.typography.displaySmall.copy(fontWeight = FontWeight.ExtraBold, fontSize = 38.sp, lineHeight = 44.sp, letterSpacing = (-1).sp),
                    modifier = Modifier.padding(top = Spacing.xl).semantics { heading() },
                )
            }
            Reveal(shown, 2) {
                Text(
                    stringResource(R.string.ob_head_2) + " " + stringResource(R.string.ob_head_3),
                    style = MaterialTheme.typography.displaySmall.copy(fontWeight = FontWeight.ExtraBold, fontSize = 38.sp, lineHeight = 44.sp, letterSpacing = (-1).sp),
                    color = scheme.primary,
                )
            }
            Reveal(shown, 3) {
                Text(
                    stringResource(R.string.ob_welcome_body),
                    style = MaterialTheme.typography.bodyLarge,
                    color = scheme.onSurfaceVariant,
                    modifier = Modifier.padding(top = Spacing.lg).widthIn(max = 380.dp),
                )
            }
            Reveal(shown, 4) {
                Row(Modifier.padding(top = Spacing.lg), horizontalArrangement = Arrangement.spacedBy(Spacing.sm)) {
                    BenefitChip(Icons.Outlined.WifiOff, stringResource(R.string.ob_chip_offline))
                    BenefitChip(Icons.Outlined.Inventory2, stringResource(R.string.ob_chip_stock))
                    BenefitChip(Icons.Outlined.Payments, stringResource(R.string.ob_chip_cost))
                }
            }
        }
        Column(Modifier.fillMaxWidth().padding(bottom = Spacing.md).navigationBarsPadding(), horizontalAlignment = Alignment.CenterHorizontally) {
            PrimaryButton(stringResource(R.string.ob_start), onStart, Modifier.fillMaxWidth(), trailingIcon = Icons.AutoMirrored.Outlined.ArrowForward)
            TextButton(onClick = onSkip) { Text(stringResource(R.string.ob_skip_all)) }
        }
    }
}

@Composable
private fun BenefitChip(icon: androidx.compose.ui.graphics.vector.ImageVector, label: String) {
    val scheme = MaterialTheme.colorScheme
    Row(
        Modifier.clip(CircleShape).background(scheme.surfaceContainerHigh).padding(horizontal = 10.dp, vertical = 7.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(5.dp),
    ) {
        Icon(icon, contentDescription = null, tint = scheme.primary, modifier = Modifier.size(14.dp))
        Text(label, style = MaterialTheme.typography.labelSmall.copy(fontWeight = FontWeight.Bold), maxLines = 1)
    }
}

/** Muncul bertahap: memudar dan naik sedikit, tiap baris tertunda sedikit dari sebelumnya. */
@Composable
private fun Reveal(visible: Boolean, order: Int, content: @Composable () -> Unit) {
    AnimatedVisibility(
        visible = visible,
        enter = fadeIn(tween(600, delayMillis = 150 + order * 180)) +
            slideInVertically(tween(600, delayMillis = 150 + order * 180)) { it / 4 },
    ) { content() }
}

/** Pilihan bahasa berupa pil dua segmen: "ID | EN". */
@Composable
private fun LanguageToggle(language: AppLanguage, onLanguage: (AppLanguage) -> Unit) {
    val scheme = MaterialTheme.colorScheme
    val idLabel = stringResource(R.string.language_id)
    val enLabel = stringResource(R.string.language_en)
    Row(Modifier.clip(CircleShape).background(scheme.surfaceContainerHigh).padding(3.dp), verticalAlignment = Alignment.CenterVertically) {
        listOf(AppLanguage.ID to "ID", AppLanguage.EN to "EN").forEach { (lang, label) ->
            val selected = language == lang
            Box(
                Modifier
                    .heightIn(min = 36.dp)
                    .widthIn(min = 48.dp)
                    .clip(CircleShape)
                    .background(if (selected) scheme.primaryContainer else Color.Transparent)
                    .clickable(role = Role.RadioButton) { onLanguage(lang) }
                    .semantics { contentDescription = if (lang == AppLanguage.ID) idLabel else enLabel },
                contentAlignment = Alignment.Center,
            ) {
                Text(
                    label,
                    style = MaterialTheme.typography.labelLarge.copy(fontWeight = if (selected) FontWeight.ExtraBold else FontWeight.SemiBold),
                    color = if (selected) scheme.primary else scheme.onSurfaceVariant,
                )
            }
        }
    }
}

// ------------------------------------------------------------------------------------------ profil, satu per satu

/** Kerangka satu pertanyaan: hero di atas, judul, isi, lalu tombol di bawah. */
@Composable
private fun StepScaffold(
    hero: @Composable () -> Unit,
    title: String,
    body: String,
    actions: @Composable () -> Unit,
    content: @Composable () -> Unit,
) {
    Column(Modifier.fillMaxSize().padding(horizontal = SidePadding)) {
        Column(Modifier.weight(1f).verticalScroll(rememberScrollState()), verticalArrangement = Arrangement.spacedBy(Spacing.md)) {
            Spacer(Modifier.height(Spacing.xl))
            hero()
            Spacer(Modifier.height(Spacing.sm))
            Text(
                title,
                style = MaterialTheme.typography.headlineMedium.copy(fontWeight = FontWeight.ExtraBold, letterSpacing = (-0.5).sp),
                modifier = Modifier.semantics { heading() },
            )
            Text(body, style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
            Spacer(Modifier.height(Spacing.sm))
            content()
            Spacer(Modifier.height(Spacing.lg))
        }
        Column(
            Modifier.fillMaxWidth().padding(bottom = Spacing.md).navigationBarsPadding().imePadding(),
            horizontalAlignment = Alignment.CenterHorizontally,
        ) { actions() }
    }
}

@Composable
private fun NamePage(vm: ProfileViewModel, onNext: () -> Unit, onLater: () -> Unit) {
    val focus = remember { FocusRequester() }
    LaunchedEffect(Unit) {
        delay(350)
        runCatching { focus.requestFocus() }
    }
    val ghost = stringResource(R.string.profile_name).uppercase()
    StepScaffold(
        hero = {
            IllustrationCard(Illustration.NAME, height = 190.dp) { u ->
                // Teks papan nama mengikuti yang diketik. Posisi dan ukurannya dihitung dari satuan kanvas ilustrasi.
                val name = vm.name.trim()
                Text(
                    text = if (name.isEmpty()) ghost else name.uppercase(),
                    style = MaterialTheme.typography.labelLarge.copy(fontWeight = FontWeight.ExtraBold, letterSpacing = 1.sp, fontSize = (9.5f * u).sp),
                    color = MaterialTheme.colorScheme.onPrimary.copy(alpha = if (name.isEmpty()) 0.55f else 1f),
                    maxLines = 1,
                    overflow = androidx.compose.ui.text.style.TextOverflow.Ellipsis,
                    textAlign = androidx.compose.ui.text.style.TextAlign.Center,
                    modifier = Modifier.offset(x = ((140f - 120f) * u).dp, y = ((41f - 85f) * u).dp).width((112f * u).dp),
                )
            }
        },
        title = stringResource(R.string.ob_name_title),
        body = stringResource(R.string.ob_name_hint),
        actions = {
            PrimaryButton(
                text = stringResource(R.string.ob_next),
                onClick = onNext,
                enabled = vm.loaded && vm.name.isNotBlank(),
                modifier = Modifier.fillMaxWidth(),
            )
            TextButton(onClick = onLater) { Text(stringResource(R.string.ob_profile_later)) }
        },
    ) {
        ArnField(
            value = vm.name,
            onValueChange = { vm.name = it.take(60) },
            label = stringResource(R.string.profile_name),
            modifier = Modifier.focusRequester(focus),
            imeAction = ImeAction.Done,
        )
    }
}

@Composable
private fun LogoPage(vm: ProfileViewModel, onNext: () -> Unit) {
    val picker = rememberLauncherForActivityResult(ActivityResultContracts.PickVisualMedia()) { uri ->
        if (uri != null) vm.pickLogo(uri)
    }
    val pick = { picker.launch(PickVisualMediaRequest(ActivityResultContracts.PickVisualMedia.ImageOnly)) }
    StepScaffold(
        hero = {
            IllustrationCard(Illustration.LOGO, Modifier.clickable(role = Role.Button, onClick = pick), height = 190.dp) { u ->
                // Foto pilihan tampil di dalam bingkai bidikan kamera.
                if (vm.logo != null) {
                    MenuImage(
                        photo = vm.logo,
                        name = vm.name,
                        modifier = Modifier.offset(x = ((168f - 120f) * u).dp, y = ((80f - 85f) * u).dp).size((80f * u).dp, (68f * u).dp),
                        shape = RoundedCornerShape((6f * u).dp),
                    )
                }
            }
        },
        title = stringResource(R.string.ob_logo_title),
        body = stringResource(R.string.ob_logo_body),
        actions = {
            PrimaryButton(stringResource(R.string.ob_next), onNext, Modifier.fillMaxWidth())
            if (vm.logo == null) {
                TextButton(onClick = onNext) { Text(stringResource(R.string.ob_skip)) }
            } else {
                TextButton(onClick = vm::removeLogo) { Text(stringResource(R.string.profile_logo_remove)) }
            }
        },
    ) {
        SecondaryButton(
            text = stringResource(if (vm.logo == null) R.string.ob_logo_pick else R.string.ob_logo_change),
            onClick = pick,
            icon = Icons.Outlined.AddPhotoAlternate,
        )
    }
}

@Composable
private fun ContactPage(vm: ProfileViewModel, onDone: () -> Unit) {
    StepScaffold(
        hero = { IllustrationCard(Illustration.CONTACT, height = 180.dp) },
        title = stringResource(R.string.ob_contact_title),
        body = stringResource(R.string.ob_contact_body),
        actions = {
            PrimaryButton(
                text = stringResource(R.string.ob_next),
                onClick = { vm.save(onDone) },
                enabled = vm.loaded,
                modifier = Modifier.fillMaxWidth(),
            )
            TextButton(onClick = { vm.save(onDone) }) { Text(stringResource(R.string.ob_skip)) }
        },
    ) {
        Column(verticalArrangement = Arrangement.spacedBy(Spacing.md)) {
            ArnField(
                value = vm.address,
                onValueChange = { vm.address = it.take(120) },
                label = stringResource(R.string.profile_address),
                singleLine = false,
                minLines = 2,
            )
            ArnField(
                value = vm.phone,
                onValueChange = { vm.phone = it.take(20) },
                label = stringResource(R.string.profile_phone),
                keyboardType = androidx.compose.ui.text.input.KeyboardType.Phone,
                imeAction = ImeAction.Done,
            )
        }
    }
}

// ------------------------------------------------------------------------------------------ penjelasan fitur

private class FeatureText(val title: Int, val summary: Int, val steps: Int, val art: Illustration)

private val Features = listOf(
    FeatureText(R.string.gp1_title, R.string.gp1_sum, R.array.gp1_steps, Illustration.SELL),
    FeatureText(R.string.gp2_title, R.string.gp2_sum, R.array.gp2_steps, Illustration.RECIPE),
    FeatureText(R.string.gp3_title, R.string.gp3_sum, R.array.gp3_steps, Illustration.STOCK),
    FeatureText(R.string.gp4_title, R.string.gp4_sum, R.array.gp4_steps, Illustration.REPORT),
    FeatureText(R.string.gp5_title, R.string.gp5_sum, R.array.gp5_steps, Illustration.BACKUP),
)

/** Satu fitur per layar: judul, satu kalimat, tiga langkah. Penjelasan rinci ada di Panduan penggunaan. */
@Composable
private fun FeaturePage(index: Int, onNext: () -> Unit, onFinish: () -> Unit, onSkip: () -> Unit) {
    val scheme = MaterialTheme.colorScheme
    val feature = Features[index]
    val steps = stringArrayResource(feature.steps)
    val last = index == Features.lastIndex
    Column(Modifier.fillMaxSize().padding(horizontal = SidePadding)) {
        Column(Modifier.weight(1f).verticalScroll(rememberScrollState())) {
            Spacer(Modifier.height(Spacing.xl))
            IllustrationCard(feature.art, height = 180.dp)
            Spacer(Modifier.height(Spacing.xl))
            Text(
                stringResource(feature.title),
                style = MaterialTheme.typography.headlineLarge.copy(fontWeight = FontWeight.ExtraBold, letterSpacing = (-0.5).sp),
                modifier = Modifier.semantics { heading() },
            )
            Text(
                stringResource(feature.summary),
                style = MaterialTheme.typography.bodyLarge,
                color = scheme.onSurfaceVariant,
                modifier = Modifier.padding(top = Spacing.sm),
            )
            Spacer(Modifier.height(Spacing.xl))
            Column(verticalArrangement = Arrangement.spacedBy(Spacing.md)) {
                steps.forEachIndexed { i, text ->
                    val (numBg, numFg) = when (i % 3) {
                        0 -> scheme.primaryContainer to scheme.onPrimaryContainer
                        1 -> scheme.secondaryContainer to scheme.onSecondaryContainer
                        else -> scheme.tertiaryContainer to scheme.onTertiaryContainer
                    }
                    com.example.kasirarn.ui.components.ArnCard(Modifier.fillMaxWidth(), contentPadding = androidx.compose.foundation.layout.PaddingValues(Spacing.lg)) {
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Box(Modifier.size(34.dp).clip(CircleShape).background(numBg), contentAlignment = Alignment.Center) {
                                Text("${i + 1}", style = MaterialTheme.typography.titleSmall.copy(fontWeight = FontWeight.ExtraBold), color = numFg)
                            }
                            Spacer(Modifier.width(Spacing.md))
                            Text(text, style = MaterialTheme.typography.bodyMedium, modifier = Modifier.weight(1f))
                        }
                    }
                }
            }
            if (last) {
                Text(
                    stringResource(R.string.ob_features_hint),
                    style = MaterialTheme.typography.bodySmall,
                    color = scheme.onSurfaceVariant,
                    modifier = Modifier.padding(top = Spacing.md),
                )
            }
            Spacer(Modifier.height(Spacing.lg))
        }
        Column(Modifier.fillMaxWidth().padding(bottom = Spacing.md).navigationBarsPadding(), horizontalAlignment = Alignment.CenterHorizontally) {
            PrimaryButton(
                text = if (last) stringResource(R.string.ob_start_tour) else stringResource(R.string.ob_next),
                onClick = { if (last) onFinish() else onNext() },
                modifier = Modifier.fillMaxWidth(),
                trailingIcon = if (last) null else Icons.AutoMirrored.Outlined.ArrowForward,
            )
            if (last) {
                Spacer(Modifier.height(48.dp))
            } else {
                TextButton(onClick = onSkip) { Text(stringResource(R.string.ob_skip_all)) }
            }
        }
    }
}
