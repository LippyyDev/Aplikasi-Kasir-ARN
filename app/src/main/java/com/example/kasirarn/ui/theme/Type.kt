package com.example.kasirarn.ui.theme

import androidx.compose.material3.Typography
import androidx.compose.ui.text.ExperimentalTextApi
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.Font
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontVariation
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.sp
import com.example.kasirarn.R

/** Plus Jakarta Sans (SIL OFL 1.1), satu berkas variabel untuk semua bobot. */
@OptIn(ExperimentalTextApi::class)
val ArnFontFamily = FontFamily(
    listOf(400, 500, 600, 700, 800).map { w ->
        Font(
            resId = R.font.plus_jakarta_sans,
            weight = FontWeight(w),
            variationSettings = FontVariation.Settings(FontVariation.weight(w)),
        )
    },
)

private fun style(size: Int, line: Int, weight: Int, tracking: Double = 0.0) = TextStyle(
    fontFamily = ArnFontFamily,
    fontSize = size.sp,
    lineHeight = line.sp,
    fontWeight = FontWeight(weight),
    letterSpacing = tracking.sp,
)

val ArnTypography = Typography(
    displayLarge = style(44, 52, 800, -0.5),
    displayMedium = style(36, 44, 800, -0.3),
    displaySmall = style(32, 40, 800, -0.6),
    headlineLarge = style(28, 36, 700),
    headlineMedium = style(24, 32, 700),
    headlineSmall = style(22, 30, 700, -0.2),
    titleLarge = style(20, 28, 700),
    titleMedium = style(16, 24, 650, 0.0),
    titleSmall = style(14, 20, 650, 0.1),
    bodyLarge = style(16, 24, 400, 0.1),
    bodyMedium = style(14, 20, 400, 0.15),
    bodySmall = style(12, 16, 400, 0.2),
    labelLarge = style(14, 20, 650, 0.1),
    labelMedium = style(12, 16, 600, 0.3),
    labelSmall = style(11, 16, 600, 0.4),
)

/** Gaya angka uang: tebal sedang, lebar angka sama supaya kolom rata. */
val MoneyStyle = TextStyle(fontFamily = ArnFontFamily, fontWeight = FontWeight.SemiBold, fontFeatureSettings = "tnum")
