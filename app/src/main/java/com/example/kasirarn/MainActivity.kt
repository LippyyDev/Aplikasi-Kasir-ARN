package com.example.kasirarn

import android.content.Context
import android.content.ContextWrapper
import android.content.res.Resources
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.SystemBarStyle
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalResources
import androidx.core.splashscreen.SplashScreen.Companion.installSplashScreen
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.example.kasirarn.data.settings.AppLanguage
import com.example.kasirarn.data.settings.ThemeMode
import com.example.kasirarn.ui.components.LocalImageStore
import com.example.kasirarn.ui.format.ProvideFmt
import com.example.kasirarn.ui.nav.ArnNavHost
import com.example.kasirarn.ui.theme.ArnTheme
import kotlinx.coroutines.flow.map

class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        val splash = installSplashScreen()
        super.onCreate(savedInstanceState)
        val container = (application as ArnApp).container
        // Splash ditahan hanya selama database dan pengaturan disiapkan, tanpa timer tetap.
        splash.setKeepOnScreenCondition { !container.ready.value }
        setContent {
            val ready by container.ready.collectAsStateWithLifecycle()
            if (ready) ArnRoot(container)
        }
    }
}

@Composable
private fun ArnRoot(container: AppContainer) {
    val settings by container.settings.settings.collectAsStateWithLifecycle(initialValue = container.initialSettings)
    val currencyFlow = remember { container.profile.observe().map { it?.mataUang ?: "Rp" } }
    val profile by currencyFlow.collectAsStateWithLifecycle(initialValue = "Rp")

    val base = LocalContext.current
    val localized = remember(settings.language, base) { LocalizedContext(base, settings.language) }
    val darkTheme = when (settings.theme) {
        ThemeMode.SYSTEM -> isSystemInDarkTheme()
        ThemeMode.LIGHT -> false
        ThemeMode.DARK -> true
    }
    val activity = base as ComponentActivity
    DisposableEffect(darkTheme) {
        val style = if (darkTheme) {
            SystemBarStyle.dark(android.graphics.Color.TRANSPARENT)
        } else {
            SystemBarStyle.light(android.graphics.Color.TRANSPARENT, android.graphics.Color.TRANSPARENT)
        }
        activity.enableEdgeToEdge(statusBarStyle = style, navigationBarStyle = style)
        onDispose { }
    }

    CompositionLocalProvider(
        LocalContext provides localized,
        LocalResources provides localized.resources,
        LocalConfiguration provides localized.resources.configuration,
        LocalImageStore provides container.images,
    ) {
        ArnTheme(mode = settings.theme, dynamicColor = settings.dynamicColor) {
            ProvideFmt(language = settings.language, currency = profile) {
                Surface(color = MaterialTheme.colorScheme.background) {
                    ArnNavHost()
                }
            }
        }
    }
}

/**
 * Pembungkus Activity yang hanya mengganti Resources ke bahasa pilihan. Tetap berupa ContextWrapper
 * dari Activity asli agar pencarian pemilik hasil aktivitas (pemilih berkas, izin) tetap berfungsi.
 */
private class LocalizedContext(base: Context, language: AppLanguage) : ContextWrapper(base) {
    private val localizedResources: Resources =
        AppContainer.localizedContext(base, language).resources

    override fun getResources(): Resources = localizedResources
}
