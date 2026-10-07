package com.example.kpkn.screens.onboarding.design.entreno.plan

import androidx.compose.foundation.Canvas
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clipToBounds
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.drawscope.withTransform
import androidx.compose.ui.graphics.lerp
import androidx.compose.ui.graphics.luminance
import androidx.compose.ui.unit.dp
import com.example.kpkn.screens.onboarding.design.WizardColors
import com.example.kpkn.screens.onboarding.design.entreno.DEG
import com.example.kpkn.screens.onboarding.design.entreno.SymbolClock
import com.example.kpkn.screens.onboarding.design.entreno.SymbolPen
import com.example.kpkn.screens.onboarding.design.entreno.SymbolRun
import com.example.kpkn.screens.onboarding.design.entreno.loopTime
import kotlin.math.abs
import kotlin.math.cos
import kotlin.math.min
import kotlin.math.sin

/*
 * La parte «imagen» de una portada de programa: el campo oscuro con un degradado muy suave hacia el acento de la
 * disciplina, las marcas de fondo y la ilustración de línea grande (el mismo dibujo que el símbolo de la lista, con la
 * línea más fina). La variación por semilla vive aquí como funciones puras para poder probarla.
 */

/** Cuánto se desplaza la ilustración por cada unidad de parallax (fracción del ancho de su zona). */
private const val ART_PARALLAX = 0.10f

/** Cuánto se desplazan las marcas de fondo (menos que la ilustración: da profundidad). */
private const val DECOR_PARALLAX = 0.04f

/** Cuántas veces más lento que en la lista corre la ilustración animada de la portada. */
private const val COVER_SPEED = 0.8f

// ---------------------------------------------------------------- variación por semilla

/**
 * Cómo se distingue una portada de otra de la misma disciplina: el ángulo del degradado, dónde y cómo cae la
 * ilustración (desplazamiento como fracción de su zona, escala y una ligera inclinación) y qué marcas lleva detrás.
 */
internal data class CoverVariant(
    val angleDeg: Float,
    val artDx: Float,
    val artDy: Float,
    val artScale: Float,
    val tiltDeg: Float,
    val decor: Int,
    val ringDx: Float,
)

/** Las tres familias de marcas de fondo: anillos, esfera graduada y diagonales. */
internal const val COVER_DECOR_STYLES = 3

/** Mezcla de 64 bits (splitmix64): semillas contiguas dan variantes bien distintas. */
private fun splitMix(seed: Int): Long {
    var z = seed.toLong() * (-0x61c8864680b583ebL) + 0x3c6ef372fe94f82aL
    z = (z xor (z ushr 30)) * (-0x40a7b892e31b1a47L)
    z = (z xor (z ushr 27)) * (-0x6b2fb644ecceee15L)
    return z xor (z ushr 31)
}

/** La variante de una semilla. Determinista: la misma semilla da siempre la misma portada. */
internal fun coverVariantOf(seed: Int): CoverVariant {
    val h = splitMix(seed)
    fun unit(shift: Int): Float = ((h ushr shift) and 0xFFL).toInt() / 255f
    return CoverVariant(
        angleDeg = 28f + unit(0) * 124f,
        artDx = (unit(8) - 0.5f) * 0.18f,
        artDy = (unit(16) - 0.5f) * 0.10f,
        artScale = 0.90f + unit(24) * 0.14f,
        tiltDeg = (unit(32) - 0.5f) * 10f,
        decor = (((h ushr 40) and 0x7FL).toInt()) % COVER_DECOR_STYLES,
        ringDx = (unit(48) - 0.5f) * 0.30f,
    )
}

// ---------------------------------------------------------------- campo y degradado

/**
 * Colores del campo de la portada. El degradado va del negro casi puro a un ~21 % del acento: siempre lo bastante
 * oscuro para que el texto en tinta cálida (y el gris cálido secundario) se lea con contraste de sobra.
 */
internal object CoverPalette {
    val base = Color(0xFF0A0A0B)
    private const val MID_MIX = 0.07f

    /** Cuánto del acento llega al extremo más claro del degradado. */
    const val TINT_MIX = 0.21f

    fun mid(accent: Color): Color = lerp(base, accent, MID_MIX)

    /** El color más claro de todo el campo (el extremo teñido del degradado): el peor caso para el contraste del texto. */
    fun tint(accent: Color): Color = lerp(base, accent, TINT_MIX)
}

/** Razón de contraste de WCAG entre un texto [fg] y un fondo [bg] (≥ 4,5 es legible; ≥ 7 es holgado). */
internal fun contrastRatio(fg: Color, bg: Color): Float {
    val a = fg.luminance()
    val b = bg.luminance()
    val hi = if (a > b) a else b
    val lo = if (a > b) b else a
    return (hi + 0.05f) / (lo + 0.05f)
}

/** El pincel del campo: un degradado lineal de [angleDeg] que cubre toda la portada de [w] × [h] píxeles. */
internal fun coverBrush(w: Float, h: Float, angleDeg: Float, accent: Color): Brush {
    val a = angleDeg * DEG
    val dx = cos(a)
    val dy = sin(a)
    // Mitad de la proyección de la caja sobre la dirección: el degradado va de esquina a esquina sin dejar zonas planas.
    val half = (abs(w * dx) + abs(h * dy)) / 2f
    val c = Offset(w / 2f, h / 2f)
    return Brush.linearGradient(
        0f to CoverPalette.base,
        0.55f to CoverPalette.mid(accent),
        1f to CoverPalette.tint(accent),
        start = Offset(c.x - dx * half, c.y - dy * half),
        end = Offset(c.x + dx * half, c.y + dy * half),
    )
}

// ---------------------------------------------------------------- lienzo de la ilustración

/**
 * La zona de la ilustración de una portada: las marcas de fondo y el dibujo de la disciplina. [running] hace correr el
 * bucle (más lento que en la lista); si no, el dibujo queda en su cuadro representativo. [parallax] (−1 a 1, se lee al
 * dibujar, sin recomponer) desplaza la ilustración un poco más que las marcas de fondo.
 */
@Composable
internal fun CoverArtCanvas(
    art: GoalArt,
    variant: CoverVariant,
    pen: SymbolPen,
    run: SymbolRun,
    clock: SymbolClock,
    running: Boolean,
    parallax: () -> Float,
    modifier: Modifier = Modifier,
) {
    Canvas(modifier.clipToBounds()) {
        val s = min(size.width, size.height)
        val par = parallax()
        val side = s * variant.artScale
        val cx = size.width / 2f + variant.artDx * size.width + par * size.width * ART_PARALLAX
        val cy = size.height / 2f + variant.artDy * size.height
        drawCoverDecor(variant, cx - par * size.width * (ART_PARALLAX - DECOR_PARALLAX), cy, s)
        pen.begin(this, 1f, art.accent)
        withTransform({
            translate(cx - side / 2f, cy - side / 2f)
            rotate(variant.tiltDeg, Offset(side / 2f, side / 2f))
            scale(side / art.width, side / art.height, Offset.Zero)
        }) {
            art.drawStatic(pen)
            val t = if (running) loopTime(art.restT, (clock.seconds - run.startAt) * COVER_SPEED, art.period) else art.restT
            art.drawDynamic(pen, t)
        }
    }
}

/** Las marcas de fondo (tinta muy tenue) detrás de la ilustración: anillos, una esfera graduada o unas diagonales. */
private fun DrawScope.drawCoverDecor(v: CoverVariant, cx: Float, cy: Float, s: Float) {
    val ink = WizardColors.text
    val hair = 1.dp.toPx()
    val r = s * 0.60f
    val center = Offset(cx + v.ringDx * s, cy)
    when (v.decor) {
        0 -> {
            drawCircle(ink.copy(alpha = 0.07f), r, center, style = Stroke(hair))
            drawCircle(ink.copy(alpha = 0.05f), r * 0.74f, center, style = Stroke(hair))
        }
        1 -> {
            drawCircle(ink.copy(alpha = 0.07f), r, center, style = Stroke(hair))
            val ticks = 48
            for (i in 0 until ticks) {
                val a = i * (360f / ticks) * DEG
                val long = i % 6 == 0
                val inner = r + 3.dp.toPx()
                val outer = inner + (if (long) 9.dp.toPx() else 4.dp.toPx())
                drawLine(
                    ink.copy(alpha = if (long) 0.14f else 0.08f),
                    Offset(center.x + cos(a) * inner, center.y + sin(a) * inner),
                    Offset(center.x + cos(a) * outer, center.y + sin(a) * outer),
                    strokeWidth = hair,
                )
            }
        }
        else -> {
            val gap = s * 0.16f
            for (i in -3..3) {
                val off = i * gap
                drawLine(
                    ink.copy(alpha = 0.06f),
                    Offset(center.x + off - s * 0.5f, center.y + s * 0.62f),
                    Offset(center.x + off + s * 0.5f, center.y - s * 0.62f),
                    strokeWidth = hair,
                )
            }
        }
    }
}
