package com.example.kasirarn.ui.theme

import androidx.compose.material3.ColorScheme
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.ui.graphics.Color

/*
 * Palet ARN: krem hangat sebagai latar, tanah liat sebagai aksen utama, zaitun untuk status aman,
 * kuning tua untuk peringatan. Ganti nilai di berkas ini untuk memasang warna merek resmi.
 */

private val Clay = Color(0xFFA8431F)
private val ClayContainer = Color(0xFFFBE3D8)
private val OnClayContainer = Color(0xFF4A1A08)
private val Olive = Color(0xFF55652B)
private val OliveContainer = Color(0xFFE4EBC9)
private val OnOliveContainer = Color(0xFF1E2608)
private val Ochre = Color(0xFF7D5700)
private val OchreContainer = Color(0xFFFAE8B8)
private val OnOchreContainer = Color(0xFF2B1D00)

val LightScheme: ColorScheme = lightColorScheme(
    primary = Clay,
    onPrimary = Color.White,
    primaryContainer = ClayContainer,
    onPrimaryContainer = OnClayContainer,
    secondary = Olive,
    onSecondary = Color.White,
    secondaryContainer = OliveContainer,
    onSecondaryContainer = OnOliveContainer,
    tertiary = Ochre,
    onTertiary = Color.White,
    tertiaryContainer = OchreContainer,
    onTertiaryContainer = OnOchreContainer,
    error = Color(0xFFB3261E),
    onError = Color.White,
    errorContainer = Color(0xFFF9DEDC),
    onErrorContainer = Color(0xFF410E0B),
    background = Color(0xFFFBF7F1),
    onBackground = Color(0xFF231E19),
    surface = Color(0xFFFBF7F1),
    onSurface = Color(0xFF231E19),
    surfaceVariant = Color(0xFFEFE6DA),
    onSurfaceVariant = Color(0xFF5B5249),
    outline = Color(0xFF857B6F),
    outlineVariant = Color(0xFFDDD3C6),
    surfaceContainerLowest = Color(0xFFFFFFFF),
    surfaceContainerLow = Color(0xFFF6F0E7),
    surfaceContainer = Color(0xFFF1EADF),
    surfaceContainerHigh = Color(0xFFECE4D8),
    surfaceContainerHighest = Color(0xFFE6DDD0),
    inverseSurface = Color(0xFF37312B),
    inverseOnSurface = Color(0xFFF6EFE6),
    inversePrimary = Color(0xFFF2A583),
    scrim = Color(0xFF000000),
)

val DarkScheme: ColorScheme = darkColorScheme(
    primary = Color(0xFFF2A583),
    onPrimary = Color(0xFF4A1A08),
    primaryContainer = Color(0xFF7A2E12),
    onPrimaryContainer = ClayContainer,
    secondary = Color(0xFFC3D087),
    onSecondary = Color(0xFF2A3306),
    secondaryContainer = Color(0xFF404B1C),
    onSecondaryContainer = OliveContainer,
    tertiary = Color(0xFFE8C16A),
    onTertiary = Color(0xFF3F2D00),
    tertiaryContainer = Color(0xFF5C4300),
    onTertiaryContainer = OchreContainer,
    error = Color(0xFFF2B8B5),
    onError = Color(0xFF601410),
    errorContainer = Color(0xFF8C1D18),
    onErrorContainer = Color(0xFFF9DEDC),
    background = Color(0xFF17130F),
    onBackground = Color(0xFFEDE5DA),
    surface = Color(0xFF17130F),
    onSurface = Color(0xFFEDE5DA),
    surfaceVariant = Color(0xFF3A332B),
    onSurfaceVariant = Color(0xFFC2B8AB),
    outline = Color(0xFF978D80),
    outlineVariant = Color(0xFF3F372F),
    surfaceContainerLowest = Color(0xFF120F0B),
    surfaceContainerLow = Color(0xFF1D1813),
    surfaceContainer = Color(0xFF221C17),
    surfaceContainerHigh = Color(0xFF2C251F),
    surfaceContainerHighest = Color(0xFF37302A),
    inverseSurface = Color(0xFFEDE5DA),
    inverseOnSurface = Color(0xFF332D27),
    inversePrimary = Clay,
    scrim = Color(0xFF000000),
)

/** Warna tile bawaan untuk menu tanpa foto; dipilih dari nama supaya stabil antar sesi. */
val MonogramTints = listOf(
    Color(0xFFE9C9B6) to Color(0xFF5A2410),
    Color(0xFFD5DDB3) to Color(0xFF2F3A10),
    Color(0xFFEBD9A4) to Color(0xFF4A3300),
    Color(0xFFD8CDBF) to Color(0xFF3E352B),
    Color(0xFFC9D6D0) to Color(0xFF1F3A33),
)

val MonogramTintsDark = listOf(
    Color(0xFF5C3322) to Color(0xFFF2D2C0),
    Color(0xFF3C4624) to Color(0xFFDCE6B6),
    Color(0xFF5A4513) to Color(0xFFF2DDA3),
    Color(0xFF4A4137) to Color(0xFFE6DCCF),
    Color(0xFF2F4640) to Color(0xFFCFE0DA),
)
