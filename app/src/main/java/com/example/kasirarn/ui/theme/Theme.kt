package com.example.kasirarn.ui.theme

import android.os.Build
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Shapes
import androidx.compose.material3.dynamicDarkColorScheme
import androidx.compose.material3.dynamicLightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import com.example.kasirarn.data.settings.ThemeMode

/** Skala jarak tunggal (kelipatan 4 dp). Semua layar memakai nilai ini, bukan angka lepas. */
object Spacing {
    val xxs = 2.dp
    val xs = 4.dp
    val sm = 8.dp
    val md = 12.dp
    val lg = 16.dp
    val xl = 24.dp
    val xxl = 32.dp
    val minTouch = 48.dp
}

val ArnShapes = Shapes(
    extraSmall = RoundedCornerShape(8.dp),
    small = RoundedCornerShape(12.dp),
    medium = RoundedCornerShape(18.dp),
    large = RoundedCornerShape(24.dp),
    extraLarge = RoundedCornerShape(32.dp),
)

@Composable
fun ArnTheme(
    mode: ThemeMode,
    dynamicColor: Boolean,
    content: @Composable () -> Unit,
) {
    val dark = when (mode) {
        ThemeMode.SYSTEM -> isSystemInDarkTheme()
        ThemeMode.LIGHT -> false
        ThemeMode.DARK -> true
    }
    val context = LocalContext.current
    val scheme = when {
        dynamicColor && Build.VERSION.SDK_INT >= Build.VERSION_CODES.S ->
            if (dark) dynamicDarkColorScheme(context) else dynamicLightColorScheme(context)
        dark -> DarkScheme
        else -> LightScheme
    }
    MaterialTheme(
        colorScheme = scheme,
        typography = ArnTypography,
        shapes = ArnShapes,
        content = content,
    )
}

@Composable
fun isDarkScheme(): Boolean = MaterialTheme.colorScheme.background.let {
    // Luminans sederhana: latar gelap bila rata-rata kanal rendah.
    (it.red + it.green + it.blue) / 3f < 0.5f
}
