package com.example.kpkn.screens.onboarding.design.entreno

import androidx.compose.foundation.Canvas
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.unit.dp
import com.example.kpkn.screens.onboarding.design.WizardColors
import com.example.kpkn.screens.onboarding.design.seg
import kotlin.math.abs
import kotlin.math.floor
import kotlin.math.roundToInt
import kotlin.math.sin

/*
 * Llama de la escala de intensidad de RINGS («¿Cómo sentiste la intensidad?»): una ilustración que se transforma con el deslizador.
 * Tres capas de lenguas en forma de gota (exterior, media y núcleo) sobre negro, sin halo ni resplandor de color. Entre niveles la
 * forma interpola de manera continua; en «Muy dura» saltan chispas. Sin respuesta es solo una brasa apagada.
 */

/** Brasa apagada (sin respuesta): tamaño como fracción del alto útil y tinta. */
private const val EMBER_RADIUS = 0.09f
private const val EMBER_ALPHA = 0.25f

/** Radio de la base de la llama central frente a su alto: la forma se mantiene igual en todos los niveles. */
private const val RADIUS_PER_HEIGHT = 0.205f

/** Parpadeo: amplitud base y lo que añade la intensidad (la llama tiembla más en «Muy dura»). */
private const val FLICKER_BASE = 0.02f
private const val FLICKER_GAIN = 0.06f

/** Por debajo de esta amplitud, la lengua todavía no existe. */
private const val MIN_AMPLITUDE = 0.02f

/** Chispas de «Muy dura»: cuántas, y cuánto suben (fracción del alto útil) a lo largo de su vida. */
private const val SPARK_COUNT = 8
private const val SPARK_RISE = 0.24f

/** Escalas de las capas interiores frente a la exterior. */
private const val MIDDLE_SCALE = 0.70f
private const val CORE_SCALE = 0.42f

/** Lengua de la llama: nace `dx` radios desde el centro, con `width` y `height` relativas a la central, y crece entre `from` y `to`. */
private class Tongue(val dx: Float, val width: Float, val height: Float, val from: Float, val to: Float) {
    /** Cuánto existe la lengua en la posición [t]: 0 = todavía no, 1 = completa. */
    fun amplitude(t: Float): Float = if (to <= from) 1f else seg(t, from, to)
}

/** La central siempre; las laterales salen con la intensidad: 1, 2, 3 y 5 puntas en las paradas de la escala. */
private val TONGUES = arrayOf(
    Tongue(dx = 0f, width = 1f, height = 1f, from = 0f, to = 0f),
    Tongue(dx = 0.95f, width = 0.52f, height = 0.70f, from = 0f, to = 0.33f),
    Tongue(dx = -0.92f, width = 0.50f, height = 0.62f, from = 0.33f, to = 0.66f),
    Tongue(dx = 1.60f, width = 0.40f, height = 0.50f, from = 0.66f, to = 1f),
    Tongue(dx = -1.55f, width = 0.38f, height = 0.44f, from = 0.80f, to = 1f),
)

/**
 * Geometría de la llama en la posición t de la escala (0 = Suave … 1 = Muy dura). Función pura, sin Compose: se prueba en JVM.
 */
internal object IntensityFlameShape {
    /** Paradas de la escala: Suave, Moderada, Dura y Muy dura. */
    const val STOP_COUNT = 4

    /** Alto de la brasa apagada, como fracción del alto útil. */
    const val EMBER_HEIGHT = 0.13f

    /** Alto de la llama en cada parada, como fracción del alto útil: 32 %, 55 %, 78 % y 100 %. */
    private val HEIGHTS = floatArrayOf(0.32f, 0.55f, 0.78f, 1f)

    /** Posición t de la parada [index] (se acota a las paradas que existen). */
    fun stopT(index: Int): Float = index.coerceIn(0, STOP_COUNT - 1) / (STOP_COUNT - 1).toFloat()

    /** Índice de la parada más cercana a [t]. */
    fun nearestStop(t: Float): Int = (t.coerceIn(0f, 1f) * (STOP_COUNT - 1)).roundToInt()

    /** Alto de la llama (fracción del alto útil), interpolado de forma lineal entre paradas. */
    fun heightAt(t: Float): Float {
        val x = t.coerceIn(0f, 1f) * (STOP_COUNT - 1)
        val i = minOf(x.toInt(), STOP_COUNT - 2)
        return HEIGHTS[i] + (HEIGHTS[i + 1] - HEIGHTS[i]) * (x - i)
    }

    /** Puntas que se ven en [t]: lenguas con más de la mitad de su tamaño. */
    fun tipCountAt(t: Float): Int = TONGUES.count { it.amplitude(t.coerceIn(0f, 1f)) > 0.5f }

    /** Fuerza de las chispas: nada hasta casi «Muy dura», y plena en ella. */
    fun sparkStrengthAt(t: Float): Float = seg(t.coerceIn(0f, 1f), 0.72f, 0.98f)

    /** Ensanche de la capa exterior: máximo en «Dura», donde la llama se abre. */
    fun outerWidenAt(t: Float): Float = 0.12f * (1f - abs(t.coerceIn(0f, 1f) - 2f / 3f) * 2.5f).coerceIn(0f, 1f)
}

/**
 * Traza una lengua de llama en esta ruta (la vacía antes): base redondeada de radio [r] centrada en [cx], con la punta a [h] sobre
 * [base] y desplazada [sway] en horizontal. Es `internal` porque también lo usa el pulgar del deslizador.
 */
internal fun Path.flameTongue(cx: Float, base: Float, r: Float, h: Float, sway: Float) {
    reset()
    val by = base - r
    val ax = cx + sway
    val ay = base - h
    val bulge = (h - r) * 0.34f
    moveTo(cx, base)
    cubicTo(cx + r * 0.55f, base, cx + r, base - r * 0.45f, cx + r, by)
    cubicTo(cx + r * 1.02f, by - bulge, ax + r * 0.2f, ay + h * 0.4f, ax, ay)
    cubicTo(ax - r * 0.2f, ay + h * 0.4f, cx - r * 1.02f, by - bulge, cx - r, by)
    cubicTo(cx - r, base - r * 0.45f, cx - r * 0.55f, base, cx, base)
    close()
}

/**
 * La llama de la escala. [level] es la posición t (0..1) y [lit] si está encendida (0 = brasa apagada, 1 = llama). Ambas, igual que el
 * reloj [clock], se leen al dibujar, así que el paso no recompone con cada cuadro. Con [reducedMotion] es el cuadro estático de su
 * nivel, sin parpadeo ni chispas.
 */
@Composable
internal fun IntensityFlameArt(
    level: () -> Float,
    lit: () -> Float,
    clock: SymbolClock,
    reducedMotion: Boolean,
    modifier: Modifier = Modifier,
) {
    // Una sola ruta y un único búfer de puntas para todos los cuadros: no se reserva memoria al dibujar.
    val path = remember { Path() }
    val tips = remember { FloatArray(TONGUES.size * 2) }
    Canvas(modifier) {
        drawIntensityFlame(
            level = level(),
            lit = lit(),
            time = if (reducedMotion) 0f else clock.seconds,
            reducedMotion = reducedMotion,
            path = path,
            tips = tips,
        )
    }
}

private fun DrawScope.drawIntensityFlame(
    level: Float,
    lit: Float,
    time: Float,
    reducedMotion: Boolean,
    path: Path,
    tips: FloatArray,
) {
    val t = level.coerceIn(0f, 1f)
    val on = lit.coerceIn(0f, 1f)
    val pad = 6.dp.toPx()
    val usable = (size.height - 2f * pad).coerceAtLeast(1f)
    val cx = size.width / 2f
    val base = size.height - pad

    // Brasa apagada: tinta tenue que se apaga del todo al encenderse la llama. Sin parpadeo.
    if (on < 1f) {
        path.flameTongue(cx, base, EMBER_RADIUS * usable, IntensityFlameShape.EMBER_HEIGHT * usable, 0f)
        drawPath(path, WizardColors.text.copy(alpha = EMBER_ALPHA * (1f - on)))
    }
    if (on <= 0f) return

    val height = IntensityFlameShape.heightAt(t) * usable
    val radius = height * RADIUS_PER_HEIGHT
    val wobble = if (reducedMotion) 0f else FLICKER_BASE + FLICKER_GAIN * t
    val widen = IntensityFlameShape.outerWidenAt(t)
    for (j in TONGUES.indices) {
        tips[2 * j] = cx
        tips[2 * j + 1] = base
    }
    // De fuera adentro: la exterior (la que se ensancha en «Dura» y guarda las puntas), la media y el núcleo.
    drawFlameLayer(
        path, tips, t, cx, base, radius, height, time, reducedMotion, wobble,
        scale = 1f, widen = widen, color = SymbolPalette.musculo.copy(alpha = on),
    )
    drawFlameLayer(
        path, null, t, cx, base, radius, height, time, reducedMotion, wobble,
        scale = MIDDLE_SCALE, widen = 0f, color = SymbolPalette.energia.copy(alpha = on),
    )
    drawFlameLayer(
        path, null, t, cx, base, radius, height, time, reducedMotion, wobble,
        scale = CORE_SCALE, widen = 0f, color = WizardColors.text.copy(alpha = on),
    )
    if (!reducedMotion) drawFlameSparks(tips, t, usable, radius, time, on)
}

/** Una capa de la llama: sus lenguas existentes, con el parpadeo de cada una. Si [tips] no es null, guarda las puntas. */
private fun DrawScope.drawFlameLayer(
    path: Path,
    tips: FloatArray?,
    t: Float,
    cx: Float,
    base: Float,
    radius: Float,
    height: Float,
    time: Float,
    reducedMotion: Boolean,
    wobble: Float,
    scale: Float,
    widen: Float,
    color: Color,
) {
    for (j in TONGUES.indices) {
        val tongue = TONGUES[j]
        val amp = tongue.amplitude(t)
        if (amp < MIN_AMPLITUDE) continue
        val phase = j * 2.1f
        val flicker = 1f + wobble * (0.6f * sin(TAU * 2.6f * time + phase) + 0.4f * sin(TAU * 3.7f * time + phase * 1.9f))
        val r = tongue.width * radius * amp * scale * (1f + widen)
        val h = tongue.height * height * amp * scale * flicker
        val x = cx + tongue.dx * radius * scale
        val sway = if (reducedMotion) 0f else 0.05f * r * sin(TAU * 1.3f * time + phase * 1.7f)
        path.flameTongue(x, base, r, h, sway)
        drawPath(path, color)
        if (tips != null) {
            tips[2 * j] = x + sway
            tips[2 * j + 1] = base - h
        }
    }
}

/** Chispas de «Muy dura»: puntos que suben desde las puntas, se desvían un poco y se apagan en bucle. */
private fun DrawScope.drawFlameSparks(
    tips: FloatArray,
    t: Float,
    usable: Float,
    radius: Float,
    time: Float,
    lit: Float,
) {
    val strength = IntensityFlameShape.sparkStrengthAt(t) * lit
    if (strength <= 0.01f) return
    // Radio de 1,5 dp al nacer (2–3 dp de diámetro), que se encoge al apagarse.
    val dot = 1.5f.dp.toPx()
    for (i in 0 until SPARK_COUNT) {
        val k = i % TONGUES.size
        if (TONGUES[k].amplitude(t) < 0.5f) continue
        // Vida de 0,8 a 1,4 s; cada chispa va con su propio desfase.
        val life = 0.8f + 0.6f * frac(i * 0.618f)
        val p = frac((time + frac(i * 0.377f) * life) / life)
        val x = tips[2 * k] + sin(p * TAU * 0.8f + i) * 0.035f * radius * p
        val y = tips[2 * k + 1] - p * SPARK_RISE * usable
        drawCircle(SymbolPalette.energia.copy(alpha = (1f - p) * strength), dot * (1f - 0.45f * p), Offset(x, y))
    }
}

private fun frac(x: Float): Float = x - floor(x)
