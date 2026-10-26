package com.example.kasirarn.ui.onboarding

import android.app.Activity
import androidx.activity.compose.BackHandler
import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.slideInHorizontally
import androidx.compose.animation.slideOutHorizontally
import androidx.compose.animation.togetherWith
import androidx.compose.foundation.BorderStroke
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
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.pager.HorizontalPager
import androidx.compose.foundation.pager.rememberPagerState
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.BarChart
import androidx.compose.material.icons.outlined.Inventory2
import androidx.compose.material.icons.outlined.MenuBook
import androidx.compose.material.icons.outlined.PointOfSale
import androidx.compose.material.icons.outlined.SettingsBackupRestore
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.platform.LocalView
import androidx.compose.ui.res.stringArrayResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.heading
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.core.view.WindowCompat
import androidx.lifecycle.ViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewModelScope
import com.example.kasirarn.AppContainer
import com.example.kasirarn.R
import com.example.kasirarn.data.settings.AppLanguage
import com.example.kasirarn.ui.components.PrimaryButton
import com.example.kasirarn.ui.misc.ProfileFields
import com.example.kasirarn.ui.misc.ProfileViewModel
import com.example.kasirarn.ui.nav.containerViewModel
import com.example.kasirarn.ui.theme.MoneyStyle
import com.example.kasirarn.ui.theme.Spacing
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

private const val STEPS = 3
private val ContentWidth = 560.dp

/** Alur pertama kali: sambutan, profil toko, lalu penjelasan singkat fitur. Disusul tutorial sorot di aplikasi. */
@Composable
fun OnboardingFlow() {
    val vm = containerViewModel { OnboardingViewModel(it) }
    var step by rememberSaveable { mutableIntStateOf(0) }
    BackHandler(enabled = step > 0) { step -= 1 }

    Surface(color = MaterialTheme.colorScheme.background, modifier = Modifier.fillMaxSize()) {
        AnimatedContent(
            targetState = step,
            transitionSpec = {
                val forward = targetState > initialState
                (slideInHorizontally { if (forward) it / 4 else -it / 4 } + fadeIn()) togetherWith
                    (slideOutHorizontally { if (forward) -it / 4 else it / 4 } + fadeOut())
            },
            label = "onboarding",
        ) { s ->
            when (s) {
                0 -> WelcomePage(
                    language = vm.language.collectAsStateWithLifecycle().value,
                    onLanguage = vm::setLanguage,
                    onStart = { step = 1 },
                    onSkip = { vm.setStage(OnboardingStage.DONE) },
                )
                1 -> ProfilePage(onNext = { step = 2 }, onLater = { step = 2 })
                else -> Box(Modifier.fillMaxSize(), contentAlignment = Alignment.TopCenter) {
                    Column(Modifier.widthIn(max = ContentWidth).fillMaxSize().statusBarsPadding()) {
                        StepBar(current = 2, modifier = Modifier.padding(horizontal = Spacing.xl, vertical = Spacing.lg))
                        FeaturePager(
                            lastLabel = stringResource(R.string.ob_start_tour),
                            onFinish = { vm.setStage(OnboardingStage.TOUR) },
                            onSkip = { vm.setStage(OnboardingStage.DONE) },
                        )
                    }
                }
            }
        }
    }
}

/** Penanda langkah: tiga garis tipis dan teks "Langkah n dari 3". */
@Composable
private fun StepBar(current: Int, modifier: Modifier = Modifier) {
    Column(modifier, verticalArrangement = Arrangement.spacedBy(Spacing.sm)) {
        Row(horizontalArrangement = Arrangement.spacedBy(6.dp), modifier = Modifier.fillMaxWidth()) {
            repeat(STEPS) { i ->
                Box(
                    Modifier
                        .weight(1f)
                        .height(4.dp)
                        .clip(CircleShape)
                        .background(if (i <= current) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.outlineVariant),
                )
            }
        }
        Text(
            stringResource(R.string.ob_step_of, current + 1, STEPS),
            style = MaterialTheme.typography.labelMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
    }
}

// ------------------------------------------------------------------------------------------ sambutan

@Composable
private fun WelcomePage(language: AppLanguage, onLanguage: (AppLanguage) -> Unit, onStart: () -> Unit, onSkip: () -> Unit) {
    val scheme = MaterialTheme.colorScheme
    // Ikon status bar harus terang di atas blok warna merek; dikembalikan saat halaman ditinggalkan.
    val view = LocalView.current
    DisposableEffect(view) {
        val window = (view.context as? Activity)?.window
        val controller = window?.let { WindowCompat.getInsetsController(it, view) }
        val previous = controller?.isAppearanceLightStatusBars
        controller?.isAppearanceLightStatusBars = false
        onDispose { if (previous != null) controller.isAppearanceLightStatusBars = previous }
    }
    // Tinggi blok warna ikut tinggi layar agar tidak ada ruang kosong besar di tengah.
    val gap = (LocalConfiguration.current.screenHeightDp * 0.07f).dp.coerceIn(36.dp, 96.dp)
    Column(Modifier.fillMaxSize()) {
        Column(Modifier.weight(1f).verticalScroll(rememberScrollState())) {
            // Blok warna merek di atas: nama aplikasi, pilihan bahasa, judul besar.
            Box(
                Modifier
                    .fillMaxWidth()
                    .background(scheme.primary, RoundedCornerShape(bottomStart = 36.dp, bottomEnd = 36.dp)),
                contentAlignment = Alignment.TopCenter,
            ) {
                Column(
                    Modifier
                        .widthIn(max = ContentWidth)
                        .fillMaxWidth()
                        .statusBarsPadding()
                        .padding(start = 28.dp, end = 28.dp, top = 20.dp, bottom = gap + 32.dp),
                ) {
                    Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
                        Text(
                            stringResource(R.string.ob_brand_a),
                            style = MaterialTheme.typography.titleLarge.copy(fontWeight = FontWeight.Black, letterSpacing = 2.sp),
                            color = scheme.onPrimary,
                        )
                        Spacer(Modifier.width(6.dp))
                        Text(
                            stringResource(R.string.ob_brand_b),
                            style = MaterialTheme.typography.titleLarge.copy(fontWeight = FontWeight.Light),
                            color = scheme.onPrimary,
                        )
                        Spacer(Modifier.weight(1f))
                        LanguageSwitch(language, onLanguage)
                    }
                    Spacer(Modifier.height(gap))
                    Text(
                        stringResource(R.string.ob_head_1),
                        style = MaterialTheme.typography.displaySmall.copy(fontWeight = FontWeight.ExtraBold, lineHeight = 44.sp),
                        color = scheme.onPrimary,
                        modifier = Modifier.semantics { heading() },
                    )
                    Text(
                        stringResource(R.string.ob_head_2),
                        style = MaterialTheme.typography.displaySmall.copy(fontWeight = FontWeight.ExtraBold, lineHeight = 44.sp),
                        color = scheme.primaryContainer,
                    )
                    Spacer(Modifier.height(Spacing.lg))
                    Text(
                        stringResource(R.string.ob_welcome_body),
                        style = MaterialTheme.typography.bodyLarge,
                        color = scheme.onPrimary.copy(alpha = 0.9f),
                    )
                }
            }
            // Contoh satu penjualan, menumpuk di tepi blok warna.
            Box(Modifier.fillMaxWidth().offset(y = (-52).dp), contentAlignment = Alignment.TopCenter) {
                SaleDemoCard(Modifier.widthIn(max = ContentWidth).fillMaxWidth().padding(horizontal = Spacing.xl))
            }
        }
        Box(Modifier.fillMaxWidth(), contentAlignment = Alignment.TopCenter) {
            Column(
                Modifier
                    .widthIn(max = ContentWidth)
                    .fillMaxWidth()
                    .padding(horizontal = Spacing.xl)
                    .padding(top = Spacing.sm, bottom = Spacing.md)
                    .navigationBarsPadding(),
                horizontalAlignment = Alignment.CenterHorizontally,
            ) {
                PrimaryButton(stringResource(R.string.ob_start), onStart, Modifier.fillMaxWidth())
                TextButton(onClick = onSkip) { Text(stringResource(R.string.ob_skip_all)) }
            }
        }
    }
}

/** Pilihan bahasa kecil di pojok blok warna: "ID | EN". */
@Composable
private fun LanguageSwitch(language: AppLanguage, onLanguage: (AppLanguage) -> Unit) {
    val scheme = MaterialTheme.colorScheme
    val idLabel = stringResource(R.string.language_id)
    val enLabel = stringResource(R.string.language_en)
    Row(
        Modifier
            .clip(CircleShape)
            .background(scheme.onPrimary.copy(alpha = 0.16f))
            .padding(3.dp),
    ) {
        listOf(AppLanguage.ID to "ID", AppLanguage.EN to "EN").forEach { (lang, label) ->
            val selected = language == lang
            Box(
                Modifier
                    .heightIn(min = 40.dp)
                    .widthIn(min = 48.dp)
                    .clip(CircleShape)
                    .background(if (selected) scheme.onPrimary else Color.Transparent)
                    .clickable(role = Role.RadioButton) { onLanguage(lang) }
                    .semantics { contentDescription = if (lang == AppLanguage.ID) idLabel else enLabel },
                contentAlignment = Alignment.Center,
            ) {
                Text(
                    label,
                    style = MaterialTheme.typography.labelLarge,
                    color = if (selected) scheme.primary else scheme.onPrimary,
                )
            }
        }
    }
}

/** Satu contoh penjualan: pendapatan, stok yang berkurang otomatis, modal, dan laba. Menjelaskan inti aplikasi tanpa kata-kata. */
@Composable
private fun SaleDemoCard(modifier: Modifier = Modifier) {
    val scheme = MaterialTheme.colorScheme
    Surface(
        modifier = modifier,
        shape = RoundedCornerShape(24.dp),
        color = scheme.surfaceContainerLowest,
        border = BorderStroke(1.dp, scheme.outlineVariant.copy(alpha = 0.6f)),
        shadowElevation = 10.dp,
    ) {
        Column(Modifier.padding(horizontal = 20.dp, vertical = 18.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text(
                    stringResource(R.string.ob_demo_title).uppercase(),
                    style = MaterialTheme.typography.labelSmall.copy(letterSpacing = 1.2.sp),
                    color = scheme.onSurfaceVariant,
                    modifier = Modifier.weight(1f),
                )
                Text(
                    stringResource(R.string.ob_demo_pay),
                    style = MaterialTheme.typography.labelMedium,
                    color = scheme.onSecondaryContainer,
                    modifier = Modifier.clip(CircleShape).background(scheme.secondaryContainer).padding(horizontal = 10.dp, vertical = 3.dp),
                )
            }
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text(stringResource(R.string.ob_demo_line), style = MaterialTheme.typography.titleSmall, modifier = Modifier.weight(1f))
                Text(stringResource(R.string.ob_demo_total), style = MaterialTheme.typography.titleSmall.merge(MoneyStyle))
            }
            HorizontalDivider(color = scheme.outlineVariant)
            Text(
                stringResource(R.string.ob_demo_stock),
                style = MaterialTheme.typography.labelMedium,
                color = scheme.primary,
            )
            DemoStockRow(stringResource(R.string.ob_demo_s1), stringResource(R.string.ob_demo_s1v))
            DemoStockRow(stringResource(R.string.ob_demo_s2), stringResource(R.string.ob_demo_s2v))
            DemoStockRow(stringResource(R.string.ob_demo_s3), stringResource(R.string.ob_demo_s3v))
            HorizontalDivider(color = scheme.outlineVariant)
            Row {
                Text(
                    stringResource(R.string.ob_demo_cost),
                    style = MaterialTheme.typography.bodyMedium.merge(MoneyStyle),
                    color = scheme.onSurfaceVariant,
                    modifier = Modifier.weight(1f),
                )
                Text(
                    stringResource(R.string.ob_demo_profit),
                    style = MaterialTheme.typography.bodyMedium.merge(MoneyStyle).copy(fontWeight = FontWeight.Bold),
                    color = scheme.secondary,
                )
            }
        }
    }
}

@Composable
private fun DemoStockRow(name: String, delta: String) {
    Row(verticalAlignment = Alignment.CenterVertically) {
        Text(name, style = MaterialTheme.typography.bodyMedium, modifier = Modifier.weight(1f))
        Text(delta, style = MaterialTheme.typography.bodyMedium.merge(MoneyStyle).copy(fontWeight = FontWeight.SemiBold))
    }
}

// ------------------------------------------------------------------------------------------ profil

@Composable
private fun ProfilePage(onNext: () -> Unit, onLater: () -> Unit) {
    val vm = containerViewModel { ProfileViewModel(it) }
    Box(Modifier.fillMaxSize(), contentAlignment = Alignment.TopCenter) {
        Column(Modifier.widthIn(max = ContentWidth).fillMaxSize().statusBarsPadding()) {
            StepBar(current = 1, modifier = Modifier.padding(horizontal = Spacing.xl, vertical = Spacing.lg))
            Column(
                Modifier.weight(1f).verticalScroll(rememberScrollState()).padding(horizontal = Spacing.xl),
                verticalArrangement = Arrangement.spacedBy(Spacing.md),
            ) {
                Text(
                    stringResource(R.string.ob_profile_title),
                    style = MaterialTheme.typography.headlineMedium.copy(fontWeight = FontWeight.ExtraBold),
                    modifier = Modifier.semantics { heading() },
                )
                Text(
                    stringResource(R.string.ob_profile_body),
                    style = MaterialTheme.typography.bodyLarge,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
                Spacer(Modifier.height(Spacing.xs))
                if (vm.loaded) ProfileFields(vm, showCurrency = false)
                Spacer(Modifier.height(Spacing.lg))
            }
            Column(
                Modifier
                    .fillMaxWidth()
                    .padding(horizontal = Spacing.xl)
                    .padding(top = Spacing.sm, bottom = Spacing.md)
                    .navigationBarsPadding()
                    .imePadding(),
                horizontalAlignment = Alignment.CenterHorizontally,
            ) {
                PrimaryButton(
                    text = stringResource(R.string.ob_profile_save),
                    onClick = { vm.save(onNext) },
                    enabled = vm.loaded && vm.name.isNotBlank(),
                    modifier = Modifier.fillMaxWidth(),
                )
                if (vm.name.isBlank()) {
                    Text(
                        stringResource(R.string.ob_profile_need_name),
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        modifier = Modifier.padding(top = Spacing.xs),
                    )
                }
                TextButton(onClick = onLater) { Text(stringResource(R.string.ob_profile_later)) }
            }
        }
    }
}

// ------------------------------------------------------------------------------------------ penjelasan fitur

private class FeaturePage(val icon: ImageVector, val title: Int, val summary: Int, val steps: Int)

private val FeaturePages = listOf(
    FeaturePage(Icons.Outlined.PointOfSale, R.string.gp1_title, R.string.gp1_sum, R.array.gp1_steps),
    FeaturePage(Icons.Outlined.MenuBook, R.string.gp2_title, R.string.gp2_sum, R.array.gp2_steps),
    FeaturePage(Icons.Outlined.Inventory2, R.string.gp3_title, R.string.gp3_sum, R.array.gp3_steps),
    FeaturePage(Icons.Outlined.BarChart, R.string.gp4_title, R.string.gp4_sum, R.array.gp4_steps),
    FeaturePage(Icons.Outlined.SettingsBackupRestore, R.string.gp5_title, R.string.gp5_sum, R.array.gp5_steps),
)

/** Penjelasan singkat lima fitur utama beserta cara pakainya; penjelasan rinci ada di Panduan penggunaan. */
@Composable
private fun FeaturePager(lastLabel: String, onFinish: () -> Unit, onSkip: () -> Unit) {
    val scheme = MaterialTheme.colorScheme
    val pager = rememberPagerState { FeaturePages.size }
    val scope = rememberCoroutineScope()
    val last = pager.currentPage == FeaturePages.lastIndex
    Column(Modifier.fillMaxSize()) {
        HorizontalPager(pager, Modifier.weight(1f)) { index ->
            val page = FeaturePages[index]
            val steps = stringArrayResource(page.steps)
            Column(
                Modifier.fillMaxSize().verticalScroll(rememberScrollState()).padding(horizontal = Spacing.xl),
                verticalArrangement = Arrangement.spacedBy(Spacing.md),
            ) {
                Row(verticalAlignment = Alignment.Top) {
                    Column(Modifier.weight(1f)) {
                        Text(
                            stringResource(R.string.ob_features_kicker, index + 1, FeaturePages.size),
                            style = MaterialTheme.typography.labelMedium.copy(letterSpacing = 1.sp),
                            color = scheme.primary,
                        )
                        Spacer(Modifier.height(Spacing.xs))
                        Text(
                            stringResource(page.title),
                            style = MaterialTheme.typography.headlineMedium.copy(fontWeight = FontWeight.ExtraBold),
                            modifier = Modifier.semantics { heading() },
                        )
                    }
                    Spacer(Modifier.width(Spacing.md))
                    Box(
                        Modifier.size(56.dp).background(scheme.primaryContainer, RoundedCornerShape(18.dp)),
                        contentAlignment = Alignment.Center,
                    ) {
                        Icon(page.icon, contentDescription = null, tint = scheme.onPrimaryContainer, modifier = Modifier.size(28.dp))
                    }
                }
                Text(stringResource(page.summary), style = MaterialTheme.typography.bodyLarge, color = scheme.onSurfaceVariant)
                Surface(
                    shape = MaterialTheme.shapes.large,
                    color = scheme.surfaceContainerLow,
                    modifier = Modifier.fillMaxWidth().padding(top = Spacing.xs),
                ) {
                    Column(Modifier.padding(Spacing.lg), verticalArrangement = Arrangement.spacedBy(Spacing.lg)) {
                        steps.forEachIndexed { i, text ->
                            Row(verticalAlignment = Alignment.Top) {
                                Text(
                                    "${i + 1}",
                                    style = MaterialTheme.typography.titleMedium.copy(fontWeight = FontWeight.ExtraBold),
                                    color = scheme.primary,
                                    modifier = Modifier.width(28.dp),
                                )
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
                    )
                }
                Spacer(Modifier.height(Spacing.lg))
            }
        }
        Column(
            Modifier.padding(horizontal = Spacing.xl).padding(top = Spacing.sm, bottom = Spacing.md).navigationBarsPadding(),
            horizontalAlignment = Alignment.CenterHorizontally,
        ) {
            PrimaryButton(
                text = if (last) lastLabel else stringResource(R.string.ob_next),
                onClick = { if (last) onFinish() else scope.launch { pager.animateScrollToPage(pager.currentPage + 1) } },
                modifier = Modifier.fillMaxWidth(),
            )
            if (last) {
                Spacer(Modifier.height(48.dp))
            } else {
                TextButton(onClick = onSkip) { Text(stringResource(R.string.ob_skip_all)) }
            }
        }
    }
}
