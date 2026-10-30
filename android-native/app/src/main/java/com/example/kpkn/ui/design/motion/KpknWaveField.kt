package com.example.kpkn.ui.design.motion

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.mutableLongStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.withFrameNanos
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.drawWithContent
import androidx.compose.ui.graphics.BlendMode
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.CompositingStrategy
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.StrokeJoin
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.drawscope.scale
import androidx.compose.ui.graphics.graphicsLayer
import com.example.kpkn.screens.onboarding.design.wizardReducedMotion
import kotlin.math.exp
import kotlin.math.min
import kotlin.math.pow
import kotlin.math.sin

/**
 * Parámetros de una onda (`CVW` del lienzo): [amp] altura de las crestas, [spd] velocidad, [freq] frecuencia, [jag] aspereza, [dim]
 * intensidad del trazo y [acc] intensidad de la línea de acento.
 */
data class KpknWaveParams(
    val amp: Double,
    val spd: Double,
    val freq: Double,
    val jag: Double,
    val dim: Double,
    val acc: Double,
)

/** Los diez estados de onda del cardio (`CVW`). */
enum class KpknWaveState(val params: KpknWaveParams) {
    /** Antes de empezar: quietas y apagadas. */
    Idle(KpknWaveParams(0.2, 0.0, 1.0, 0.0, 0.8, 0.7)),

    /** Calentamiento (Z1-Z2): suaves y lentas. */
    Warm(KpknWaveParams(0.5, 0.55, 1.0, 0.0, 1.0, 1.0)),

    /** Continuo. */
    Cont(KpknWaveParams(0.72, 0.85, 1.05, 0.03, 1.0, 1.0)),

    /** Trabajo: amplias y rápidas. */
    Work(KpknWaveParams(0.98, 1.15, 1.15, 0.07, 1.0, 1.0)),

    /** Recuperación: bajan y se alargan. */
    Recover(KpknWaveParams(0.52, 0.58, 0.9, 0.0, 1.0, 1.0)),

    /** Vuelta a la calma: casi planas. */
    Cool(KpknWaveParams(0.3, 0.36, 0.85, 0.0, 0.95, 1.0)),

    /** Por encima de la zona: ásperas, rápidas y en coral. */
    Above(KpknWaveParams(1.2, 1.65, 1.4, 0.5, 1.0, 1.0)),

    /** Por debajo de la zona: flojas para el objetivo. */
    Below(KpknWaveParams(0.5, 0.65, 0.95, 0.0, 1.0, 1.0)),

    /** En pausa: congeladas y apagadas. */
    Pause(KpknWaveParams(0.32, 0.0, 1.0, 0.0, 0.42, 0.0)),

    /** Cardio terminado: se aquietan. */
    Done(KpknWaveParams(0.26, 0.22, 0.8, 0.0, 0.9, 1.0)),
}

/** Máscara vertical del contenedor de la onda (`.cv-wave` del lienzo). */
enum class KpknWaveEdgeFade {
    /** Sin máscara (`.cv-wave.full`, `.cv-wt .cv-wave`). */
    None,

    /** `linear-gradient(#0000, #000 14%, #000 82%, #0000)`: la de las ondas de la cabecera. */
    Hero,

    /** `linear-gradient(#000, #000 60%, #0000)`: la del final del cardio (`.cv-finwave`). */
    Finish,
}

/** Ancho del sistema de coordenadas del SVG de las ondas (`W = 360`); la altura es `data-h`. */
const val KPKN_WAVE_VIEWBOX_WIDTH: Float = 360f

/**
 * Las líneas de la onda, calculadas con EXACTAMENTE el algoritmo de `cvWaveD` del lienzo. Devuelve, por línea, las `y` de cada punto
 * (x = 0, 5, …, 360) en unidades del viewBox de [height] de alto. [tAcc] = segundos acumulados de fase; [heartRate] = pulso en lpm
 * (0 = sin pulso) y la cresta late a ese ritmo.
 *
 * ```
 * val ys = kpknWaveLines(height = 112f, params = KpknWaveState.Work.params, lines = 12, tAcc = 2.0)   // ys[k][i] = y de la línea k en x = 5 i
 * ```
 */
fun kpknWaveLines(height: Float, params: KpknWaveParams, lines: Int, tAcc: Double, heartRate: Double = 0.0): Array<FloatArray> {
    val width = KPKN_WAVE_VIEWBOX_WIDTH.toDouble()
    val h = height.toDouble()
    val n = lines
    val beat = if (heartRate > 0.0) exp(-(((tAcc * heartRate / 60.0) % 1.0)) * 6.0) else 0.0
    val ph = tAcc * params.spd * 2.2
    val top = h * 0.36
    val span = h * 0.6
    val points = (KPKN_WAVE_VIEWBOX_WIDTH / 5f).toInt() + 1
    return Array(n) { k ->
        val base = top + k * span / (n - 1)
        val depth = (k.toDouble() / (n - 1)).pow(1.1)
        val bump = min(h, 330.0) * 0.5 * params.amp * (0.22 + 0.78 * depth) * (1 + 0.14 * beat)
        val sig = width * (0.2 + 0.1 * depth) * (0.85 + 0.25 * min(1.4, params.amp))
        val cx = width / 2 + (k - n / 2.0) * 5
        FloatArray(points) { i ->
            val x = i * 5.0
            val env = exp(-((x - cx) / sig).pow(2))
            val w1 = sin(x * 0.05 * params.freq + k * 0.42 - ph)
            val w2 = sin(x * 0.021 * params.freq - k * 0.31 + ph * 0.7 + 1.3)
            val w3 = sin(x * 0.19 * params.freq + k * 1.1 - ph * 2.6)
            val shape = 0.5 + 0.5 * (0.62 * w1 + 0.38 * w2)
            val ripple = sin(x * 0.03 + k * 0.7 - ph * 0.5) * 1.4 * (0.4 + params.amp)
            (base - env * bump * (shape + params.jag * 0.55 * w3) - ripple).toFloat()
        }
    }
}

/**
 * Estado dinámico de una onda (lo que hace `frame()` en el lienzo): los parámetros actuales se acercan a los del estado con
 * `k = 1 − e^(−3,2 dt)`, la fase [tAcc] solo avanza si la velocidad pasa de 0,01 y `dt` se recorta a 50 ms; con [intro] la amplitud
 * crece con una salida cúbica durante esos segundos.
 */
class KpknWaveDynamics(initial: KpknWaveParams, introSeconds: Double = 0.0) {
    private var amp = if (introSeconds > 0.0) 0.0 else initial.amp
    private var spd = initial.spd
    private var freq = initial.freq
    private var jag = initial.jag
    private var dim = initial.dim
    private var acc = initial.acc
    private var elapsed = 0.0

    /** Segundos de fase acumulados. */
    var tAcc: Double = 0.0
        private set

    private val intro = introSeconds

    /** Avanza [dtSeconds] hacia [target]. */
    fun step(dtSeconds: Double, target: KpknWaveParams) {
        val dt = min(0.05, dtSeconds)
        val k = 1 - exp(-dt * 3.2)
        amp += (target.amp - amp) * k
        spd += (target.spd - spd) * k
        freq += (target.freq - freq) * k
        jag += (target.jag - jag) * k
        dim += (target.dim - dim) * k
        acc += (target.acc - acc) * k
        elapsed += dt
        tAcc += dt * (if (spd > 0.01) 1 else 0)
    }

    /** Parámetros que se dibujan ahora (con la rampa de entrada aplicada a la amplitud). */
    fun current(): KpknWaveParams {
        val ramp = if (intro > 0.0) min(1.0, elapsed / intro) else 1.0
        val eased = 1 - (1 - ramp).pow(3)
        return KpknWaveParams(amp * eased, spd, freq, jag, dim, acc)
    }
}

/**
 * Campo de ondas del cardio y de las portadas (técnica «Ondas» de las portadas de programa, en movimiento): crestas apiladas que se tapan
 * entre sí, trazo crema y UNA línea con el color de la zona ([zoneColor]) con su resplandor. Mismo algoritmo y mismas constantes que el
 * lienzo; [heartRate] > 0 hace latir la cresta a ese ritmo. Con movimiento reducido (o con [frozenSeconds]) dibuja una sola pose fija:
 * la del estado, sin animar. [viewBoxHeight] = `data-h` del lienzo (alto del SVG en unidades; por defecto, el alto del contenedor).
 *
 * ```
 * KpknWaveField(KpknWaveState.Work, Modifier.fillMaxWidth().height(112.dp), zoneColor = Color(0xFFF49A6E), lines = 12)
 * KpknWaveField(KpknWaveState.Pause, Modifier.fillMaxSize(), frozenSeconds = 2.4f)          // pose fija
 * KpknWaveField(KpknWaveState.Warm, Modifier.height(292.dp), heartRate = 142, edgeFade = KpknWaveEdgeFade.Hero)
 * ```
 */
@Composable
fun KpknWaveField(
    state: KpknWaveState,
    modifier: Modifier = Modifier,
    zoneColor: Color = Color(0xFFF49A6E),
    lines: Int = 18,
    heartRate: Int = 0,
    introSeconds: Float = 0f,
    edgeFade: KpknWaveEdgeFade = KpknWaveEdgeFade.None,
    viewBoxHeight: Float? = null,
    frozenSeconds: Float? = null,
) {
    val reduced = wizardReducedMotion()
    val target = rememberUpdatedState(state.params)
    val dynamics = remember { KpknWaveDynamics(state.params, introSeconds.toDouble()) }
    val tick = remember { mutableLongStateOf(0L) }
    val frozen = frozenSeconds != null || reduced
    LaunchedEffect(frozen) {
        if (frozen) return@LaunchedEffect
        var last = withFrameNanos { it }
        while (true) {
            withFrameNanos { now ->
                dynamics.step((now - last) / 1e9, target.value)
                last = now
                tick.longValue++
            }
        }
    }
    val faded = when (edgeFade) {
        KpknWaveEdgeFade.None -> Modifier
        KpknWaveEdgeFade.Hero -> Modifier.waveMask(listOf(0f to 0f, 0.14f to 1f, 0.82f to 1f, 1f to 0f))
        KpknWaveEdgeFade.Finish -> Modifier.waveMask(listOf(0f to 1f, 0.6f to 1f, 1f to 0f))
    }
    Canvas(modifier.then(faded).fillMaxSize()) {
        tick.longValue // suscripción: cada cuadro de la onda redibuja, sin recomponer
        val params: KpknWaveParams
        val t: Double
        if (frozen) {
            params = state.params
            t = (frozenSeconds ?: 2.4f).toDouble()
        } else {
            params = dynamics.current()
            t = dynamics.tAcc
        }
        val vbH = viewBoxHeight ?: (size.height / density)
        val sx = size.width / KPKN_WAVE_VIEWBOX_WIDTH
        val sy = size.height / vbH
        val ys = kpknWaveLines(vbH, params, lines, t, heartRate.toDouble())
        val n = lines
        val accK = Math.round(n * 0.64).toInt()
        val cream = Color(0xFFF2EEE6)
        scale(sx, sy, pivot = androidx.compose.ui.geometry.Offset.Zero) {
            fun line(k: Int): Path = Path().apply {
                ys[k].forEachIndexed { i, y ->
                    val r = Math.round(y * 10f) / 10f // el lienzo escribe la y con `toFixed(1)`
                    if (i == 0) moveTo(0f, r) else lineTo(i * 5f, r)
                }
            }
            fun closed(k: Int): Path = line(k).apply {
                lineTo(KPKN_WAVE_VIEWBOX_WIDTH, vbH + 4f)
                lineTo(0f, vbH + 4f)
                close()
            }
            for (k in 0 until n) {
                drawPath(closed(k), Color.Black)
                val op = ((0.08 + 0.42 * (k.toDouble() / (n - 1)).pow(1.15)) * params.dim).toFloat()
                drawPath(
                    line(k),
                    cream,
                    alpha = op,
                    style = Stroke(width = (0.8f + 0.5f * k / (n - 1)).toFloat(), cap = StrokeCap.Round, join = StrokeJoin.Round),
                )
            }
            val accent = line(accK)
            drawPath(accent, zoneColor, alpha = (0.26 * params.acc).toFloat(), style = Stroke(width = 7f, cap = StrokeCap.Round))
            drawPath(
                accent,
                zoneColor,
                alpha = params.acc.toFloat(),
                style = Stroke(width = 2.2f, cap = StrokeCap.Round, join = StrokeJoin.Round),
            )
        }
    }
}

/** Máscara de opacidad vertical: [stops] = (posición 0..1, opacidad 0..1). */
private fun Modifier.waveMask(stops: List<Pair<Float, Float>>): Modifier =
    graphicsLayer { compositingStrategy = CompositingStrategy.Offscreen }.drawWithContent {
        drawContent()
        drawRect(
            brush = Brush.verticalGradient(*stops.map { (p, a) -> p to Color.Black.copy(alpha = a) }.toTypedArray()),
            blendMode = BlendMode.DstIn,
        )
    }
