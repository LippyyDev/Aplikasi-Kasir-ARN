package com.example.kasirarn.ui.nav

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.widthIn
import androidx.compose.runtime.Composable
import androidx.compose.runtime.Immutable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.LocalWindowInfo
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp

/**
 * Ukuran jendela saat ini dalam dp. Dipakai untuk memilih tata letak:
 * ponsel potret = sempit; tablet dan ponsel lanskap = lebar (rail navigasi, kasir dua panel).
 */
@Immutable
data class WindowInfo(val widthDp: Int, val heightDp: Int) {
    val isLandscape: Boolean get() = widthDp > heightDp

    /** Cukup lebar untuk menu dan keranjang berdampingan serta rail navigasi di sisi kiri. */
    val isWide: Boolean get() = widthDp >= WIDE_BREAKPOINT_DP

    /** Layar pendek (ponsel lanskap): hindari elemen tinggi dan kurangi jarak vertikal. */
    val isShort: Boolean get() = heightDp < 480

    companion object {
        const val WIDE_BREAKPOINT_DP = 700
    }
}

@Composable
fun rememberWindowInfo(): WindowInfo {
    val size = LocalWindowInfo.current.containerSize
    val density = LocalDensity.current.density
    return WindowInfo((size.width / density).toInt(), (size.height / density).toInt())
}

/** Lebar nyaman untuk formulir dan layar detail di tablet; di ponsel tidak berpengaruh. */
val FormMaxWidth: Dp = 640.dp

/** Menengahkan isi layar dengan lebar maksimum [maxWidth]. */
@Composable
fun ConstrainedScreen(maxWidth: Dp = FormMaxWidth, content: @Composable () -> Unit) {
    Box(Modifier.fillMaxSize(), contentAlignment = Alignment.TopCenter) {
        Box(Modifier.widthIn(max = maxWidth).fillMaxHeight()) { content() }
    }
}
