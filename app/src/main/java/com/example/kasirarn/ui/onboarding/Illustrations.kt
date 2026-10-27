package com.example.kasirarn.ui.onboarding

import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.BoxScope
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.State
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.PathEffect
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.StrokeJoin
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.drawscope.withTransform
import androidx.compose.ui.graphics.lerp
import androidx.compose.ui.semantics.clearAndSetSemantics
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import kotlin.math.PI
import kotlin.math.abs
import kotlin.math.cos
import kotlin.math.sin

/**
 * Adegan bertokoh yang bergerak mengulang, satu untuk tiap halaman pengenalan. Digambar dengan Canvas dari bentuk
 * sederhana dan warna tema (kulit dan rambut tetap), tanpa berkas gambar.
 */
enum class Illustration { WELCOME, NAME, LOGO, CONTACT, SELL, RECIPE, STOCK, REPORT, BACKUP }

private const val VW = 240f
private const val VH = 170f
private val TAU = 2f * PI.toFloat()

private fun seg(t: Float, a: Float, b: Float): Float = ((t - a) / (b - a)).coerceIn(0f, 1f)
private fun ease(x: Float): Float = x * x * (3f - 2f * x)
private fun Color.a(f: Float): Color = copy(alpha = (alpha * f).coerceIn(0f, 1f))
private fun mix(a: Float, b: Float, f: Float): Float = a + (b - a) * f
private fun mix(a: Offset, b: Offset, f: Float): Offset = Offset(mix(a.x, b.x, f), mix(a.y, b.y, f))
private fun arc(from: Offset, to: Offset, f: Float, lift: Float): Offset {
    val p = mix(from, to, f)
    return Offset(p.x, p.y - lift * sin(f * PI.toFloat()))
}

private val Skin = Color(0xFFF0BE96)
private val SkinDeep = Color(0xFFC98F66)
private val SkinShade = Color(0xFFE2A67A)
private val Hair = Color(0xFF3A2A24)
private val HairLight = Color(0xFF7A4B2E)
private val Cream = Color(0xFFF6EBDD)
private val AwningCream = Color(0xFFE8D2BB)
private val Wall = Color(0xFFF1E3D1)
private val Ink = Color(0xFF2B2420)
private val Coin = Color(0xFFE9B949)
private val Paper = Color(0xFFFFFBF4)
private val Wood = Color(0xFF8A5A3C)

private class Palette(
    val primary: Color,
    val dark: Color,
    val soft: Color,
    val tint: Color,
    val line: Color,
    val ink: Color,
    val good: Color,
    val warn: Color,
    val surface: Color,
)

private enum class Hat { NONE, CHEF }

private class Look(
    val skin: Color,
    val hair: Color,
    val shirt: Color,
    val apron: Color? = null,
    val hat: Hat = Hat.NONE,
    val bun: Boolean = false,
)

@Composable
private fun rememberLoop(millis: Int): State<Float> {
    val transition = rememberInfiniteTransition(label = "illustration")
    return transition.animateFloat(
        initialValue = 0f,
        targetValue = 1f,
        animationSpec = infiniteRepeatable(tween(millis, easing = LinearEasing), RepeatMode.Restart),
        label = "t",
    )
}

/**
 * Kartu berlatar lembut berisi satu adegan. Dekoratif, jadi disembunyikan dari pembaca layar.
 * [overlay] menumpuk komposabel biasa (misalnya teks nama toko) di atas adegan; satuannya dp per satuan kanvas maya.
 */
@Composable
fun IllustrationCard(
    kind: Illustration,
    modifier: Modifier = Modifier,
    height: Dp = 180.dp,
    framed: Boolean = true,
    overlay: (@Composable BoxScope.(unit: Float) -> Unit)? = null,
) {
    val scheme = MaterialTheme.colorScheme
    val palette = Palette(
        primary = scheme.primary,
        dark = lerp(scheme.primary, Color.Black, 0.3f),
        soft = scheme.primary.copy(alpha = 0.32f),
        tint = scheme.primary.copy(alpha = 0.14f),
        line = scheme.outlineVariant,
        ink = scheme.onSurfaceVariant,
        good = scheme.secondary,
        warn = scheme.tertiary,
        surface = scheme.surfaceContainerLow,
    )
    val t = rememberLoop(
        when (kind) {
            Illustration.WELCOME -> 4400
            Illustration.NAME -> 4000
            Illustration.LOGO -> 4200
            Illustration.CONTACT -> 3600
            Illustration.SELL -> 4200
            Illustration.RECIPE -> 4600
            Illustration.STOCK -> 4800
            Illustration.REPORT -> 4400
            Illustration.BACKUP -> 4200
        },
    )
    val base = modifier.fillMaxWidth().height(height).clearAndSetSemantics { }
    BoxWithConstraints(
        if (framed) base.clip(RoundedCornerShape(24.dp)).background(scheme.surfaceContainerLow) else base,
        contentAlignment = Alignment.Center,
    ) {
        val unit = minOf(maxWidth.value / VW, maxHeight.value / VH)
        Canvas(Modifier.fillMaxSize()) {
            val s = minOf(size.width / VW, size.height / VH)
            withTransform({
                translate((size.width - VW * s) / 2f, (size.height - VH * s) / 2f)
                scale(s, s, Offset.Zero)
            }) {
                val p = t.value
                when (kind) {
                    Illustration.WELCOME -> drawStall(p, palette)
                    Illustration.NAME -> drawShopfront(p, palette)
                    Illustration.LOGO -> drawPhoto(p, palette)
                    Illustration.CONTACT -> drawCall(p, palette)
                    Illustration.SELL -> drawSale(p, palette)
                    Illustration.RECIPE -> drawKitchen(p, palette)
                    Illustration.STOCK -> drawStockroom(p, palette)
                    Illustration.REPORT -> drawBoard(p, palette)
                    Illustration.BACKUP -> drawArchive(p, palette)
                }
            }
        }
        overlay?.invoke(this, unit)
    }
}

// ------------------------------------------------------------------------------------------ bagian bersama

private fun DrawScope.floor(c: Palette) {
    drawRoundRect(c.line.a(0.8f), Offset(14f, 152f), Size(212f, 5f), CornerRadius(2.5f))
}

private fun DrawScope.checkMark(center: Offset, size: Float, color: Color, width: Float) {
    drawPath(
        Path().apply {
            moveTo(center.x - size, center.y + size * 0.1f)
            lineTo(center.x - size * 0.3f, center.y + size * 0.8f)
            lineTo(center.x + size, center.y - size * 0.7f)
        },
        color, style = Stroke(width, cap = StrokeCap.Round, join = StrokeJoin.Round),
    )
}

private fun DrawScope.pin(cx: Float, topY: Float, color: Color, hole: Color) {
    drawPath(
        Path().apply {
            addOval(androidx.compose.ui.geometry.Rect(cx - 15f, topY, cx + 15f, topY + 30f))
            moveTo(cx - 12f, topY + 22f); lineTo(cx, topY + 46f); lineTo(cx + 12f, topY + 22f); close()
        },
        color,
    )
    drawCircle(hole, 6f, Offset(cx, topY + 15f))
}

private fun DrawScope.star(center: Offset, r: Float, color: Color) {
    val path = Path()
    for (i in 0 until 10) {
        val rad = if (i % 2 == 0) r else r * 0.45f
        val ang = (i * 36f - 90f) * PI.toFloat() / 180f
        val p = Offset(center.x + cos(ang) * rad, center.y + sin(ang) * rad)
        if (i == 0) path.moveTo(p.x, p.y) else path.lineTo(p.x, p.y)
    }
    path.close()
    drawPath(path, color)
}

/**
 * Tokoh sederhana. [left] dan [right] adalah posisi tangan di kanvas (sisi kiri dan kanan layar);
 * bila null, tangan menggantung. Badan digambar sampai [bottom], biasanya tertutup meja di depannya.
 */
private fun DrawScope.person(
    cx: Float,
    headY: Float,
    look: Look,
    bottom: Float,
    left: Offset? = null,
    right: Offset? = null,
    blink: Boolean = false,
    smile: Float = 1f,
) {
    val top = headY + 19f
    drawRoundRect(look.shirt, Offset(cx - 22f, top), Size(44f, bottom - top), CornerRadius(14f))
    look.apron?.let {
        drawRoundRect(it, Offset(cx - 13f, top + 7f), Size(26f, bottom - top), CornerRadius(6f))
        drawLine(it, Offset(cx - 11f, top + 8f), Offset(cx - 15f, top - 1f), 3f, StrokeCap.Round)
        drawLine(it, Offset(cx + 11f, top + 8f), Offset(cx + 15f, top - 1f), 3f, StrokeCap.Round)
    }
    drawRect(lerp(look.skin, Color.Black, 0.1f), Offset(cx - 5f, headY + 13f), Size(10f, 9f))
    val lh = left ?: Offset(cx - 25f, top + 34f)
    val rh = right ?: Offset(cx + 25f, top + 34f)
    drawLine(look.shirt, Offset(cx - 19f, top + 7f), lh, 10f, StrokeCap.Round)
    drawCircle(look.skin, 5.5f, lh)
    drawLine(look.shirt, Offset(cx + 19f, top + 7f), rh, 10f, StrokeCap.Round)
    drawCircle(look.skin, 5.5f, rh)

    val head = Offset(cx, headY)
    drawCircle(look.skin, 17f, head)
    drawArc(look.hair, 180f, 180f, true, Offset(cx - 18f, headY - 18f), Size(36f, 36f))
    if (look.bun) drawCircle(look.hair, 7f, Offset(cx, headY - 20f))
    if (look.hat == Hat.CHEF) {
        val hat = Color.White
        drawRoundRect(hat, Offset(cx - 13f, headY - 30f), Size(26f, 15f), CornerRadius(4f))
        drawCircle(hat, 8f, Offset(cx - 9f, headY - 33f))
        drawCircle(hat, 9f, Offset(cx, headY - 38f))
        drawCircle(hat, 8f, Offset(cx + 9f, headY - 33f))
        drawLine(Color(0xFFE6DCCF), Offset(cx - 13f, headY - 20f), Offset(cx + 13f, headY - 20f), 2f, StrokeCap.Round)
    }
    if (blink) {
        drawLine(Ink, Offset(cx - 9f, headY + 2f), Offset(cx - 4f, headY + 2f), 1.8f, StrokeCap.Round)
        drawLine(Ink, Offset(cx + 4f, headY + 2f), Offset(cx + 9f, headY + 2f), 1.8f, StrokeCap.Round)
    } else {
        drawCircle(Ink, 2f, Offset(cx - 6.5f, headY + 2f))
        drawCircle(Ink, 2f, Offset(cx + 6.5f, headY + 2f))
    }
    val blush = Color(0xFFE88E7F).copy(alpha = 0.35f)
    drawCircle(blush, 3.4f, Offset(cx - 11f, headY + 8f))
    drawCircle(blush, 3.4f, Offset(cx + 11f, headY + 8f))
    drawArc(
        Ink, 25f, 130f * smile, false, Offset(cx - 5.5f, headY + 3f), Size(11f, 9f),
        style = Stroke(1.8f, cap = StrokeCap.Round),
    )
}

private fun blinkAt(t: Float, at: Float = 0.72f): Boolean = t > at && t < at + 0.045f

// ------------------------------------------------------------------------------------------ sambutan

/**
 * Sambutan: pemilik kedai bercelemek di balik meja. Ia melambai, berkedip, kopi mengepul,
 * dan koin muncul dari mesin kasir.
 */
private fun DrawScope.drawStall(t: Float, c: Palette) {
    val tau = 2f * PI.toFloat()
    val bob = 1.2f * sin(t * tau * 2f)
    val cx = 112f

    // Matahari lembut dan tiang tenda.
    drawCircle(c.tint, 34f, Offset(198f, 70f))
    drawCircle(c.primary.a(0.10f), 22f, Offset(198f, 70f))
    drawLine(c.primary.a(0.5f), Offset(30f, 40f), Offset(30f, 130f), 4f, StrokeCap.Round)
    drawLine(c.primary.a(0.5f), Offset(210f, 40f), Offset(210f, 130f), 4f, StrokeCap.Round)

    // Badan.
    drawRoundRect(Cream, Offset(cx - 24f, 102f + bob), Size(48f, 44f), CornerRadius(14f))
    drawRoundRect(c.primary, Offset(cx - 14f, 108f + bob), Size(28f, 44f), CornerRadius(6f))
    drawLine(c.primary, Offset(cx - 11f, 109f + bob), Offset(cx - 15f, 101f + bob), 3f, StrokeCap.Round)
    drawLine(c.primary, Offset(cx + 11f, 109f + bob), Offset(cx + 15f, 101f + bob), 3f, StrokeCap.Round)
    drawRect(SkinShade, Offset(cx - 5f, 95f + bob), Size(10f, 9f))

    // Lengan kanan (sisi kanan layar) bertumpu di meja.
    drawLine(Cream, Offset(cx + 21f, 108f + bob), Offset(cx + 29f, 124f + bob), 10f, StrokeCap.Round)
    drawCircle(Skin, 5.5f, Offset(cx + 30f, 127f + bob))

    // Lengan kiri melambai: naik, bergoyang, lalu turun.
    val raise = ease(seg(t, 0f, 0.1f)) * (1f - ease(seg(t, 0.52f, 0.64f)))
    val shake = sin(t * tau * 6f) * 16f * seg(t, 0.08f, 0.14f) * (1f - seg(t, 0.48f, 0.54f))
    val theta = Math.toRadians((mix(190f, 325f, raise) + shake).toDouble()).toFloat()
    val pivot = Offset(cx - 20f, 108f + bob)
    val hand = Offset(pivot.x + sin(theta) * 25f, pivot.y - kotlin.math.cos(theta) * 25f)
    drawLine(Cream, pivot, hand, 10f, StrokeCap.Round)
    drawCircle(Skin, 5.5f, hand)

    // Kepala.
    val head = Offset(cx, 82f + bob)
    drawCircle(Skin, 17f, head)
    drawArc(Hair, 180f, 180f, true, Offset(head.x - 18f, head.y - 18f), Size(36f, 36f))
    drawCircle(Hair, 7f, Offset(cx, head.y - 20f))
    val blink = t > 0.72f && t < 0.765f
    if (blink) {
        drawLine(Ink, Offset(cx - 9f, head.y + 2f), Offset(cx - 4f, head.y + 2f), 1.8f, StrokeCap.Round)
        drawLine(Ink, Offset(cx + 4f, head.y + 2f), Offset(cx + 9f, head.y + 2f), 1.8f, StrokeCap.Round)
    } else {
        drawCircle(Ink, 2f, Offset(cx - 6.5f, head.y + 2f))
        drawCircle(Ink, 2f, Offset(cx + 6.5f, head.y + 2f))
    }
    drawCircle(Color(0xFFE88E7F).copy(alpha = 0.35f), 3.4f, Offset(cx - 11f, head.y + 8f))
    drawCircle(Color(0xFFE88E7F).copy(alpha = 0.35f), 3.4f, Offset(cx + 11f, head.y + 8f))
    drawArc(Ink, 25f, 130f, false, Offset(cx - 5.5f, head.y + 3f), Size(11f, 9f), style = Stroke(1.8f, cap = StrokeCap.Round))

    // Meja kayu.
    drawRoundRect(lerp(c.primary, Color.Black, 0.28f), Offset(20f, 128f), Size(200f, 42f), CornerRadius(8f))
    drawRoundRect(c.primary, Offset(14f, 121f), Size(212f, 11f), CornerRadius(5.5f))
    for (k in 1..5) drawLine(Color.Black.a(0.14f), Offset(20f + k * 33.3f, 138f), Offset(20f + k * 33.3f, 166f), 2f, StrokeCap.Round)

    // Mesin kasir dan koin yang muncul.
    drawRoundRect(Cream, Offset(38f, 101f), Size(38f, 21f), CornerRadius(5f))
    drawRoundRect(c.soft, Offset(43f, 104f), Size(22f, 7f), CornerRadius(2f))
    repeat(3) { i -> drawCircle(c.primary.a(0.6f), 1.8f, Offset(46f + i * 6f, 116f)) }
    val pop = seg(t, 0.6f, 0.92f)
    if (pop > 0f && pop < 1f) {
        val e = ease(pop)
        val y = 96f - 28f * e
        val alpha = sin(pop * PI.toFloat())
        drawCircle(Coin.a(alpha), 7f, Offset(57f, y))
        drawCircle(Color.White.a(alpha * 0.55f), 3.2f, Offset(57f, y), style = Stroke(1.4f))
        if (pop < 0.6f) {
            repeat(4) { i ->
                val ang = (i * 90f + 45f) * PI.toFloat() / 180f
                val r1 = 11f + 4f * pop
                drawLine(
                    Coin.a(alpha), Offset(57f + sin(ang) * r1, y - kotlin.math.cos(ang) * r1),
                    Offset(57f + sin(ang) * (r1 + 4f), y - kotlin.math.cos(ang) * (r1 + 4f)), 1.6f, StrokeCap.Round,
                )
            }
        }
    }

    // Cangkir kopi dan uapnya.
    drawPath(
        Path().apply { moveTo(160f, 102f); lineTo(178f, 102f); lineTo(175.5f, 120f); lineTo(162.5f, 120f); close() },
        Cream,
    )
    drawRect(c.primary, Offset(160.8f, 107f), Size(16.4f, 4f))
    drawArc(Cream, -80f, 160f, false, Offset(172f, 104f), Size(11f, 11f), style = Stroke(3f, cap = StrokeCap.Round))
    drawRoundRect(Cream, Offset(154f, 119f), Size(30f, 4f), CornerRadius(2f))
    repeat(3) { k ->
        val s = (t * 1.4f + k / 3f) % 1f
        val x = 163f + k * 7f + sin((s * 2f + k) * PI.toFloat()) * 2.5f
        val y = 96f - 26f * s
        drawPath(
            Path().apply { moveTo(x, y); quadraticBezierTo(x + 4f, y - 6f, x, y - 12f) },
            Color.White.a(sin(s * PI.toFloat()) * 0.7f).let { if (c.surface.luminanceApprox() > 0.5f) c.ink.a(sin(s * PI.toFloat()) * 0.45f) else it },
            style = Stroke(2.4f, cap = StrokeCap.Round),
        )
    }

    // Tenda bergaris dengan tepi bergelombang.
    val x0 = 18f
    val w = 25.5f
    for (i in 0 until 8) {
        val x = x0 + i * w
        val color = if (i % 2 == 0) c.primary else AwningCream
        val sway = sin(t * tau + i * 0.7f) * 0.9f
        drawRect(color, Offset(x, 12f), Size(w + 0.5f, 24f))
        drawArc(color, 0f, 180f, true, Offset(x, 24f + sway), Size(w, 25f))
    }
}

private fun Color.luminanceApprox(): Float = 0.2126f * red + 0.7152f * green + 0.0722f * blue


// ------------------------------------------------------------------------------------------ nama toko

/** Nama toko: papan nama dengan lampu hias berkedip bergantian; pemilik menunjuk ke papan. Teks papan ditumpuk dari luar. */
private fun DrawScope.drawShopfront(t: Float, c: Palette) {
    floor(c)
    val bob = 1.2f * sin(t * TAU * 2f)
    // Dinding, tenda kecil, pintu, jendela.
    drawRoundRect(Wall, Offset(84f, 58f), Size(112f, 94f), CornerRadius(6f))
    for (i in 0 until 8) {
        val x = 80f + i * 15f
        val col = if (i % 2 == 0) c.primary else AwningCream
        drawRect(col, Offset(x, 56f), Size(15.5f, 14f))
        drawArc(col, 0f, 180f, true, Offset(x, 62f), Size(15f, 16f))
    }
    drawRoundRect(c.dark, Offset(126f, 100f), Size(28f, 52f), CornerRadius(5f))
    drawCircle(Coin, 2f, Offset(149f, 128f))
    for (wx in floatArrayOf(92f, 160f)) {
        drawRoundRect(c.tint, Offset(wx, 100f), Size(28f, 30f), CornerRadius(4f))
        drawRoundRect(c.primary.a(0.5f), Offset(wx, 100f), Size(28f, 30f), CornerRadius(4f), style = Stroke(2f))
        drawLine(c.primary.a(0.4f), Offset(wx + 14f, 100f), Offset(wx + 14f, 130f), 1.6f)
    }
    // Papan nama dan lampu hias.
    drawRoundRect(c.primary, Offset(78f, 28f), Size(124f, 26f), CornerRadius(7f))
    drawRoundRect(Color.White.a(0.25f), Offset(78f, 28f), Size(124f, 26f), CornerRadius(7f), style = Stroke(1.6f))
    for (i in 0 until 11) {
        val x = 86f + i * 11.4f
        val glow = (sin(t * TAU * 2f - i * 0.7f) * 0.5f + 0.5f)
        drawCircle(Coin.a(0.35f + 0.65f * glow), 2.4f, Offset(x, 24f))
        drawLine(c.ink.a(0.25f), Offset(x, 24f), Offset(x, 28f), 1f)
    }
    val spark = seg(t, 0.6f, 0.85f)
    if (spark > 0f && spark < 1f) {
        val al = sin(spark * PI.toFloat())
        val sp = Offset(206f, 34f)
        drawLine(Coin.a(al), Offset(sp.x - 6f, sp.y), Offset(sp.x + 6f, sp.y), 2f, StrokeCap.Round)
        drawLine(Coin.a(al), Offset(sp.x, sp.y - 6f), Offset(sp.x, sp.y + 6f), 2f, StrokeCap.Round)
    }
    // Pemilik menunjuk ke papan.
    val look = Look(Skin, Hair, Cream, c.primary, bun = true)
    val point = ease(seg(t, 0.05f, 0.2f)) * (1f - ease(seg(t, 0.8f, 0.95f)))
    val rest = Offset(63f, 128f + bob)
    val up = Offset(80f, 78f + bob + 4f * sin(t * TAU * 3f))
    person(40f, 92f + bob, look, 152f, right = mix(rest, up, point), blink = blinkAt(t, 0.5f))
    // Tanaman di sisi kanan.
    val sway = sin(t * TAU) * 2f
    drawRoundRect(c.dark, Offset(210f, 134f), Size(18f, 18f), CornerRadius(3f))
    drawLine(c.good, Offset(219f, 134f), Offset(219f + sway, 112f), 3f, StrokeCap.Round)
    drawOval(c.good, Offset(206f + sway, 108f), Size(14f, 8f))
    drawOval(c.good, Offset(218f + sway, 114f), Size(14f, 8f))
}

// ------------------------------------------------------------------------------------------ logo / foto

/** Logo: tokoh memotret dengan ponsel; kilat menyala dan gambar muncul di bingkai. Foto pilihan ditumpuk dari luar. */
private fun DrawScope.drawPhoto(t: Float, c: Palette) {
    floor(c)
    val bob = 1.2f * sin(t * TAU * 2f)
    val look = Look(SkinDeep, Hair, c.good, null, bun = false)
    val cx = 58f
    val phone = Offset(cx + 36f, 96f + bob)
    person(cx, 92f + bob, look, 152f, right = Offset(phone.x, phone.y + 10f), blink = blinkAt(t, 0.7f))
    drawRoundRect(Ink, Offset(phone.x - 5f, phone.y - 9f), Size(11f, 19f), CornerRadius(2.5f))
    drawRoundRect(c.tint.a(2f), Offset(phone.x - 3.5f, phone.y - 7f), Size(8f, 13f), CornerRadius(1.5f))
    drawCircle(Color(0xFF6C7A89), 1.6f, Offset(phone.x + 0.5f, phone.y - 7.8f))

    // Bingkai bidikan dengan sudut menebal.
    val frame = Offset(122f, 40f)
    val fw = 92f
    val fh = 80f
    val pulse = 0.7f + 0.3f * sin(t * TAU * 2f)
    val bracket = c.primary.a(pulse)
    for (sx in 0..1) for (sy in 0..1) {
        val x = frame.x + sx * fw
        val y = frame.y + sy * fh
        val dx = if (sx == 0) 1f else -1f
        val dy = if (sy == 0) 1f else -1f
        drawLine(bracket, Offset(x, y), Offset(x + 14f * dx, y), 4f, StrokeCap.Round)
        drawLine(bracket, Offset(x, y), Offset(x, y + 14f * dy), 4f, StrokeCap.Round)
    }
    val flash = seg(t, 0.5f, 0.56f) * (1f - seg(t, 0.56f, 0.7f))
    val develop = ease(seg(t, 0.58f, 0.78f))
    drawRoundRect(c.tint.a(0.6f + 0.4f * develop), Offset(frame.x + 6f, frame.y + 6f), Size(fw - 12f, fh - 12f), CornerRadius(6f))
    // Gambar contoh: bukit dan matahari, muncul setelah kilat.
    drawCircle(Coin.a(develop), 7f, Offset(frame.x + 66f, frame.y + 26f))
    drawPath(
        Path().apply {
            moveTo(frame.x + 10f, frame.y + fh - 8f); lineTo(frame.x + 38f, frame.y + 34f)
            lineTo(frame.x + 56f, frame.y + 56f); lineTo(frame.x + 68f, frame.y + 44f)
            lineTo(frame.x + fw - 10f, frame.y + fh - 8f); close()
        },
        c.primary.a(0.55f * develop),
    )
    // Kilat kamera.
    if (flash > 0f) {
        drawCircle(Color.White.a(0.9f * flash), 18f * (0.5f + flash), phone)
        repeat(8) { i ->
            val ang = i * TAU / 8f
            drawLine(
                Coin.a(flash), Offset(phone.x + cos(ang) * 14f, phone.y + sin(ang) * 14f),
                Offset(phone.x + cos(ang) * 24f, phone.y + sin(ang) * 24f), 2.4f, StrokeCap.Round,
            )
        }
        drawRoundRect(Color.White.a(0.55f * flash), Offset(frame.x, frame.y), Size(fw, fh), CornerRadius(6f))
    }
    if (develop > 0.95f) checkMark(Offset(frame.x + fw - 4f, frame.y - 2f), 5f, c.good, 3f)
}

// ------------------------------------------------------------------------------------------ alamat dan telepon

/** Kontak: tokoh menelepon, gelembung titik-titik, dan peta dengan penanda yang memantul. */
private fun DrawScope.drawCall(t: Float, c: Palette) {
    floor(c)
    val bob = 1.2f * sin(t * TAU * 2f)
    val look = Look(Skin, HairLight, c.primary, Color(0xFFF6EBDD), bun = true)
    val cx = 54f
    val ear = Offset(cx - 25f, 94f + bob)
    person(cx, 96f + bob, look, 152f, left = Offset(cx - 22f, 104f + bob), blink = blinkAt(t, 0.8f))
    drawRoundRect(Ink, Offset(ear.x - 5f, ear.y - 11f), Size(9f, 21f), CornerRadius(3f))
    repeat(3) { k ->
        val s = (t * 1.5f + k / 3f) % 1f
        drawArc(
            c.primary.a((1f - s) * 0.8f), 140f, 80f, false,
            Offset(ear.x - 8f - 10f * s - 4f * k, ear.y - 10f - 4f * s), Size(14f + 8f * s + 4f * k, 24f),
            style = Stroke(2.4f, cap = StrokeCap.Round),
        )
    }
    // Gelembung ucapan dengan tiga titik yang melompat bergantian.
    drawRoundRect(c.tint, Offset(86f, 30f), Size(66f, 32f), CornerRadius(14f))
    drawPath(Path().apply { moveTo(96f, 60f); lineTo(88f, 74f); lineTo(108f, 61f); close() }, c.tint)
    repeat(3) { i ->
        val hop = (sin(t * TAU * 2f - i * 0.9f).coerceAtLeast(0f)) * 4f
        drawCircle(c.primary, 3.6f, Offset(105f + i * 14f, 47f - hop))
    }
    // Peta dengan jalan dan penanda memantul.
    val map = Offset(134f, 78f)
    drawRoundRect(c.tint, map, Size(92f, 70f), CornerRadius(12f))
    drawLine(Color.White.a(0.8f), Offset(map.x + 4f, map.y + 44f), Offset(map.x + 88f, map.y + 26f), 7f, StrokeCap.Round)
    drawLine(Color.White.a(0.8f), Offset(map.x + 34f, map.y + 4f), Offset(map.x + 52f, map.y + 66f), 7f, StrokeCap.Round)
    drawRoundRect(c.good.a(0.3f), Offset(map.x + 60f, map.y + 8f), Size(22f, 14f), CornerRadius(4f))
    val bounce = abs(sin(t * TAU)).toFloat()
    val px = map.x + 50f
    repeat(2) { i ->
        val s = (t + i * 0.5f) % 1f
        drawOval(c.primary.a((1f - s) * 0.4f), Offset(px - 8f - 18f * s, map.y + 54f - 3f * s), Size(16f + 36f * s, 6f + 8f * s), style = Stroke(1.8f))
    }
    drawOval(c.ink.a(0.2f), Offset(px - 8f + 2f * bounce, map.y + 55f), Size(16f - 4f * bounce, 5f))
    pin(px, map.y + 6f - 12f * bounce, c.primary, c.surface)
}

// ------------------------------------------------------------------------------------------ berjualan

/** Berjualan: pembeli menyerahkan koin, penjual mengetik di kasir, struk keluar. */
private fun DrawScope.drawSale(t: Float, c: Palette) {
    floor(c)
    val bob = 1.2f * sin(t * TAU * 2f)
    val buyer = Look(SkinDeep, Hair, c.good)
    val seller = Look(Skin, HairLight, Cream, c.primary, bun = true)

    val bx = 50f
    val give = ease(seg(t, 0f, 0.1f)) * (1f - ease(seg(t, 0.4f, 0.5f)))
    val handRest = Offset(bx + 25f, 130f + bob)
    val handOut = Offset(bx + 38f, 112f + bob)
    val buyerHand = mix(handRest, handOut, give)
    person(bx, 90f + bob, buyer, 152f, right = buyerHand, blink = blinkAt(t, 0.76f))

    val sx = 190f
    val tap = sin(seg(t, 0.5f, 0.62f) * PI.toFloat() * 2f).coerceAtLeast(0f)
    person(sx, 84f + bob, seller, 152f, left = Offset(sx - 28f, 108f + 6f * tap + bob), blink = blinkAt(t, 0.3f))

    // Meja.
    drawRoundRect(c.dark, Offset(100f, 126f), Size(128f, 44f), CornerRadius(8f))
    drawRoundRect(c.primary, Offset(94f, 119f), Size(140f, 11f), CornerRadius(5.5f))
    // Mesin kasir dan struk.
    drawRoundRect(Cream, Offset(138f, 100f), Size(40f, 20f), CornerRadius(5f))
    drawRoundRect(c.soft, Offset(143f, 103f), Size(24f, 7f), CornerRadius(2f))
    repeat(4) { i -> drawCircle(c.primary.a(0.6f), 1.8f, Offset(144f + i * 6f, 115f)) }
    val out = ease(seg(t, 0.62f, 0.82f)) * (1f - seg(t, 0.94f, 1f))
    if (out > 0f) {
        val h = 28f * out
        drawRect(Paper, Offset(150f, 101f - h), Size(18f, h))
        drawRect(c.line, Offset(150f, 101f - h), Size(18f, h), style = Stroke(1f))
        for (k in 0 until 3) {
            val y = 101f - h + 5f + k * 7f
            if (y < 98f) drawLine(c.ink.a(0.5f), Offset(153f, y), Offset(165f - k * 2f, y), 1.6f, StrokeCap.Round)
        }
    }
    // Koin berpindah dari tangan pembeli ke meja, lalu hilang ke laci kasir.
    val fly = seg(t, 0.1f, 0.4f)
    if (fly > 0f && fly < 1f) {
        val pos = arc(Offset(bx + 40f, 112f + bob), Offset(128f, 112f), ease(fly), 22f)
        drawCircle(Coin, 6f, pos)
        drawCircle(Color.White.a(0.6f), 2.8f, pos, style = Stroke(1.3f))
    }
    val rest = seg(t, 0.4f, 0.5f)
    if (rest > 0f && rest < 1f) {
        drawCircle(Coin.a(1f - rest), 6f, Offset(128f + 14f * rest, 113f))
    }
}

// ------------------------------------------------------------------------------------------ resep

/** Resep: koki mengaduk panci, bahan jatuh satu per satu, papan resep tercentang. */
private fun DrawScope.drawKitchen(t: Float, c: Palette) {
    floor(c)
    val bob = 1.2f * sin(t * TAU * 2f)
    // Papan resep di dinding.
    drawRoundRect(Paper, Offset(20f, 30f), Size(62f, 66f), CornerRadius(8f))
    drawRoundRect(c.line, Offset(20f, 30f), Size(62f, 66f), CornerRadius(8f), style = Stroke(1.6f))
    drawCircle(c.primary, 3f, Offset(51f, 36f))
    for (i in 0 until 3) {
        val s = ease(seg(t, 0.15f + i * 0.2f, 0.3f + i * 0.2f))
        val y = 52f + i * 15f
        drawCircle(c.tint, 5f, Offset(32f, y))
        if (s > 0f) checkMark(Offset(32f, y), 3f * s, c.good, 2f)
        drawLine(c.soft, Offset(42f, y), Offset(42f + (34f - i * 7f) * s.coerceAtLeast(0.15f), y), 3.5f, StrokeCap.Round)
    }
    val chef = Look(Skin, Hair, Color.White, c.primary, Hat.CHEF)
    val phi = t * TAU * 3f
    val pot = Offset(172f, 108f)
    val hand = Offset(pot.x - 12f + cos(phi) * 7f, 92f + sin(phi) * 3f)
    person(112f, 88f + bob, chef, 152f, right = hand, blink = blinkAt(t, 0.84f))
    // Bahan jatuh ke panci.
    val colors = arrayOf(c.warn, c.good, Coin)
    for (i in 0 until 3) {
        val s = seg(t, 0.05f + i * 0.26f, 0.3f + i * 0.26f)
        if (s > 0f && s < 1f) {
            val x = pot.x - 8f + i * 8f
            val y = mix(20f, 92f, s * s)
            drawCircle(colors[i].a(1f - seg(s, 0.85f, 1f)), 5f, Offset(x, y))
            drawCircle(Color.White.a(0.4f), 1.8f, Offset(x - 1.5f, y - 1.5f))
        }
    }
    // Panci dengan sendok dan uap.
    drawRoundRect(Wood.a(0.9f), Offset(hand.x - 1f, hand.y), Size(3f, 26f), CornerRadius(1.5f))
    drawRoundRect(Color(0xFF5A4A42), Offset(pot.x - 24f, pot.y - 12f), Size(48f, 30f), CornerRadius(7f))
    drawRoundRect(Color(0xFF6F5D54), Offset(pot.x - 28f, pot.y - 14f), Size(56f, 6f), CornerRadius(3f))
    drawLine(Color(0xFF5A4A42), Offset(pot.x - 28f, pot.y), Offset(pot.x - 36f, pot.y), 4f, StrokeCap.Round)
    drawLine(Color(0xFF5A4A42), Offset(pot.x + 28f, pot.y), Offset(pot.x + 36f, pot.y), 4f, StrokeCap.Round)
    repeat(3) { k ->
        val s = (t * 1.5f + k / 3f) % 1f
        val x = pot.x - 10f + k * 10f + sin((s * 2f + k) * PI.toFloat()) * 3f
        val y = pot.y - 18f - 28f * s
        drawPath(
            Path().apply { moveTo(x, y); quadraticBezierTo(x + 4f, y - 6f, x, y - 12f) },
            c.ink.a(sin(s * PI.toFloat()) * 0.5f), style = Stroke(2.4f, cap = StrokeCap.Round),
        )
    }
    // Meja.
    drawRoundRect(c.dark, Offset(70f, 126f), Size(160f, 44f), CornerRadius(8f))
    drawRoundRect(c.primary, Offset(64f, 119f), Size(172f, 11f), CornerRadius(5.5f))
}

// ------------------------------------------------------------------------------------------ stok

/** Stok: penjaga gudang mencatat di papan; toples yang menipis berkedip, lalu kardus datang dan toples terisi lagi. */
private fun DrawScope.drawStockroom(t: Float, c: Palette) {
    floor(c)
    val bob = 1.2f * sin(t * TAU * 2f)
    // Rak.
    drawRoundRect(c.dark, Offset(112f, 94f), Size(116f, 5f), CornerRadius(2.5f))
    drawRoundRect(c.dark, Offset(112f, 134f), Size(116f, 5f), CornerRadius(2.5f))
    drawLine(c.dark, Offset(114f, 56f), Offset(114f, 152f), 4f, StrokeCap.Round)
    drawLine(c.dark, Offset(226f, 56f), Offset(226f, 152f), 4f, StrokeCap.Round)
    // Toples di rak atas; yang ketiga menipis, berkedip, lalu terisi setelah restok.
    val refill = ease(seg(t, 0.72f, 0.92f))
    val levels = floatArrayOf(0.8f, 0.62f, mix(0.16f, 0.82f, refill))
    val flash = if (t < 0.55f) (0.5f + 0.5f * sin(t * TAU * 5f)) else 0f
    for (i in 0 until 3) {
        val x = 126f + i * 32f
        drawRoundRect(c.tint, Offset(x, 58f), Size(24f, 36f), CornerRadius(7f))
        val h = 32f * levels[i]
        val col = if (i == 2 && levels[i] < 0.4f) lerp(c.warn, c.primary, 1f - flash) else c.primary
        drawRoundRect(col, Offset(x + 2f, 92f - h), Size(20f, h), CornerRadius(5f))
        drawRoundRect(c.primary.a(0.5f), Offset(x, 58f), Size(24f, 36f), CornerRadius(7f), style = Stroke(1.6f))
        drawRoundRect(c.primary.a(0.5f), Offset(x + 3f, 52f), Size(18f, 7f), CornerRadius(3f))
    }
    if (t < 0.55f) {
        val al = 0.4f + 0.6f * flash
        drawPath(
            Path().apply { moveTo(214f, 36f); lineTo(222f, 52f); lineTo(206f, 52f); close() },
            c.warn.a(al),
        )
        drawLine(Color.White.a(al), Offset(214f, 41f), Offset(214f, 46f), 1.8f, StrokeCap.Round)
    }
    // Kardus di rak bawah; satu kardus baru meluncur masuk.
    drawRoundRect(Wood.a(0.85f), Offset(122f, 112f), Size(30f, 22f), CornerRadius(3f))
    drawRoundRect(Wood.a(0.85f), Offset(158f, 112f), Size(30f, 22f), CornerRadius(3f))
    for (bx in floatArrayOf(122f, 158f)) drawLine(Color.Black.a(0.2f), Offset(bx + 15f, 112f), Offset(bx + 15f, 134f), 1.6f)
    val slide = ease(seg(t, 0.52f, 0.72f))
    if (slide > 0f) {
        val x = mix(250f, 194f, slide)
        drawRoundRect(Wood, Offset(x, 112f), Size(30f, 22f), CornerRadius(3f))
        drawLine(Color.Black.a(0.2f), Offset(x + 15f, 112f), Offset(x + 15f, 134f), 1.6f)
        drawRect(Cream, Offset(x + 9f, 120f), Size(12f, 6f))
    }
    // Penjaga gudang dengan papan catatan, pensil mencentang.
    val look = Look(Skin, Hair, c.good, null, bun = false)
    val cx = 62f
    val tick = sin(t * TAU * 6f) * 2.5f
    val pencil = Offset(cx + 26f + tick, 118f + bob + tick)
    person(cx, 90f + bob, look, 152f, left = Offset(cx + 22f, 122f + bob), right = pencil, blink = blinkAt(t, 0.66f))
    drawRoundRect(Paper, Offset(cx + 14f, 96f + bob), Size(24f, 30f), CornerRadius(3f))
    drawRoundRect(c.dark, Offset(cx + 22f, 93f + bob), Size(8f, 5f), CornerRadius(1.5f))
    for (k in 0 until 3) drawLine(c.ink.a(0.45f), Offset(cx + 18f, 106f + bob + k * 6f), Offset(cx + 34f, 106f + bob + k * 6f), 1.5f, StrokeCap.Round)
    drawCircle(Skin, 5.5f, Offset(cx + 22f, 122f + bob))
    drawCircle(Skin, 5.5f, pencil)
    drawLine(Coin, pencil, Offset(pencil.x + 4f, pencil.y - 9f), 2.4f, StrokeCap.Round)
}

// ------------------------------------------------------------------------------------------ laporan

/** Laporan: tokoh menunjuk grafik di papan; batang tumbuh, garis tren menyusul, bintang muncul di akhir. */
private fun DrawScope.drawBoard(t: Float, c: Palette) {
    floor(c)
    val bob = 1.2f * sin(t * TAU * 2f)
    // Papan dengan kaki.
    drawLine(c.dark, Offset(130f, 118f), Offset(122f, 152f), 4f, StrokeCap.Round)
    drawLine(c.dark, Offset(206f, 118f), Offset(214f, 152f), 4f, StrokeCap.Round)
    drawRoundRect(Paper, Offset(104f, 20f), Size(124f, 100f), CornerRadius(10f))
    drawRoundRect(c.line, Offset(104f, 20f), Size(124f, 100f), CornerRadius(10f), style = Stroke(2f))
    val heights = floatArrayOf(22f, 34f, 28f, 48f, 40f, 62f)
    val base = 106f
    val tops = ArrayList<Offset>()
    heights.forEachIndexed { i, h ->
        val s = ease(seg(t, 0.12f + i * 0.07f, 0.4f + i * 0.07f))
        val x = 122f + i * 17f
        val last = i == heights.lastIndex
        drawRoundRect(if (last) c.primary else c.soft, Offset(x - 6f, base - h * s), Size(12f, h * s), CornerRadius(3f))
        tops.add(Offset(x, base - h * s - 8f))
    }
    drawLine(c.line, Offset(112f, base + 3f), Offset(220f, base + 3f), 2f, StrokeCap.Round)
    val p = seg(t, 0.3f, 0.8f) * (heights.size - 1)
    val whole = p.toInt().coerceAtMost(heights.size - 2)
    val frac = p - whole
    for (i in 0 until whole) drawLine(c.good, tops[i], tops[i + 1], 3f, StrokeCap.Round)
    val tip = mix(tops[whole], tops[whole + 1], frac)
    if (p > 0f) {
        drawLine(c.good, tops[whole], tip, 3f, StrokeCap.Round)
        drawCircle(c.good, 4.5f, tip)
    }
    // Tokoh dengan penunjuk.
    val look = Look(Skin, Hair, Cream, c.primary, bun = true)
    val cx = 52f
    val hand = Offset(cx + 28f, 92f + bob)
    val pointer = if (p > 0f) tip else Offset(122f, 90f)
    person(cx, 86f + bob, look, 152f, right = hand, blink = blinkAt(t, 0.88f))
    drawLine(Wood, Offset(hand.x - 4f, hand.y + 5f), Offset(mix(hand.x, pointer.x, 0.97f), mix(hand.y, pointer.y, 0.97f)), 2.6f, StrokeCap.Round)
    val win = ease(seg(t, 0.82f, 0.92f)) * (1f - seg(t, 0.97f, 1f))
    if (win > 0f) star(Offset(206f, 34f), 10f * win, Coin)
}

// ------------------------------------------------------------------------------------------ cadangan

/** Cadangan: tokoh memasukkan berkas ke laci arsip, laci menutup, lalu gembok dan centang muncul. */
private fun DrawScope.drawArchive(t: Float, c: Palette) {
    floor(c)
    val bob = 1.2f * sin(t * TAU * 2f)
    // Lemari arsip.
    val cab = Offset(150f, 56f)
    drawRoundRect(c.dark, Offset(cab.x, cab.y), Size(70f, 96f), CornerRadius(8f))
    val open = 1f - ease(seg(t, 0.45f, 0.6f))
    for (i in 0 until 3) {
        val y = cab.y + 7f + i * 29f
        val shift = if (i == 0) -16f * open else 0f
        if (i == 0 && open > 0f) drawRoundRect(Color.Black.a(0.45f), Offset(cab.x + 6f, y), Size(58f, 24f), CornerRadius(4f))
        drawRoundRect(c.primary, Offset(cab.x + 6f + shift, y), Size(58f, 24f), CornerRadius(4f))
        drawRoundRect(Cream, Offset(cab.x + 25f + shift, y + 8f), Size(20f, 6f), CornerRadius(3f))
    }
    // Tokoh membawa berkas.
    val look = Look(SkinDeep, Hair, Cream, c.primary, bun = false)
    val cx = 62f
    val doc0 = Offset(cx + 30f, 112f + bob)
    val doc1 = Offset(cab.x - 6f, cab.y + 18f)
    val fly = seg(t, 0.12f, 0.45f)
    val hold = 1f - ease(seg(t, 0.1f, 0.2f)).coerceAtMost(1f)
    val reach = ease(seg(t, 0.12f, 0.3f)) * (1f - ease(seg(t, 0.32f, 0.45f)))
    val hand = mix(Offset(cx + 25f, 128f + bob), Offset(cx + 40f, 108f + bob), reach.coerceAtLeast(1f - hold))
    person(cx, 90f + bob, look, 152f, right = hand, blink = blinkAt(t, 0.7f))
    // Berkas terbang masuk ke laci, mengecil saat mendekat.
    val docPos = if (fly <= 0f) hand else arc(doc0, doc1, ease(fly), 26f)
    val size = if (fly >= 1f) 0f else (1f - 0.55f * ease(fly))
    if (size > 0f) {
        withTransform({ scale(size, size, docPos) }) {
            drawRoundRect(Paper, Offset(docPos.x - 9f, docPos.y - 12f), Size(18f, 24f), CornerRadius(2f))
            drawRoundRect(c.line, Offset(docPos.x - 9f, docPos.y - 12f), Size(18f, 24f), CornerRadius(2f), style = Stroke(1f))
            for (k in 0 until 3) drawLine(c.ink.a(0.5f), Offset(docPos.x - 5f, docPos.y - 6f + k * 6f), Offset(docPos.x + 5f - k, docPos.y - 6f + k * 6f), 1.4f, StrokeCap.Round)
        }
    }
    // Perisai dengan centang di atas lemari.
    val shield = ease(seg(t, 0.62f, 0.78f)) * (1f - seg(t, 0.94f, 1f))
    if (shield > 0f) {
        val sc = Offset(cab.x + 35f, 30f)
        withTransform({ scale(shield, shield, sc) }) {
            drawPath(
                Path().apply {
                    moveTo(sc.x, sc.y - 18f); lineTo(sc.x + 16f, sc.y - 11f); lineTo(sc.x + 14f, sc.y + 6f)
                    quadraticBezierTo(sc.x + 10f, sc.y + 16f, sc.x, sc.y + 21f)
                    quadraticBezierTo(sc.x - 10f, sc.y + 16f, sc.x - 14f, sc.y + 6f)
                    lineTo(sc.x - 16f, sc.y - 11f); close()
                },
                c.good,
            )
            checkMark(Offset(sc.x, sc.y + 1f), 6f, Color.White, 3f)
        }
    }
}
