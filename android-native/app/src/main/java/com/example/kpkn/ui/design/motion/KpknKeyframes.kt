package com.example.kpkn.ui.design.motion

import androidx.compose.animation.core.Easing
import androidx.compose.ui.graphics.Color
import kotlin.math.floor

/**
 * Propiedades que animan los `@keyframes` del lienzo. Cada canal es un número: [identity] es el valor «sin efecto» (el de
 * `transform: none`, `opacity: 1`…), que usan los fotogramas implícitos cuando la regla no escribe el 0 % o el 100 %.
 */
enum class KpknChannel(val identity: Float) {
    /** `opacity`. */
    Alpha(1f),

    /** `translateX(<dp>)`. */
    TranslateXDp(0f),

    /** `translateY(<dp>)`. */
    TranslateYDp(0f),

    /** `translateX(<%>)`, como fracción del ancho propio (1 = 100 %). */
    TranslateXFraction(0f),

    /** `translateY(<%>)`, como fracción del alto propio (1 = 100 %). */
    TranslateYFraction(0f),

    /** `scale(x)` o `scaleX(x)`. */
    ScaleX(1f),

    /** `scale(x)` o `scaleY(x)`. */
    ScaleY(1f),

    /** `rotate(<grados>)`. */
    RotationDeg(0f),

    /** `stroke-dashoffset` como fracción del largo del trazo (con `pathLength` = 100: 100 = 1). */
    DashOffset(0f),

    /** `width` como fracción del ancho del contenedor (0 % … 100 %). */
    WidthFraction(1f),

    /** `background-position` horizontal, en %. */
    BackgroundShiftPct(0f),

    /** `max-height` en dp (`none` = 4096). */
    MaxHeightDp(4096f),

    /** `filter: brightness(x)`. */
    Brightness(1f),

    /** `filter: blur(<dp>)` (desviación típica, como en CSS). */
    BlurDp(0f),

    /** Mezcla entre el color de partida (0) y el de llegada (1) de una propiedad de color (`color`, `background`). */
    ColorMix(1f),

    /** Opacidad del color de una `box-shadow` (el color lo pone quien dibuja; interpola como alfa premultiplicado). */
    ShadowAlpha(0f),

    /** Extensión (`spread`) de una `box-shadow`, en dp. */
    ShadowSpreadDp(0f),

    /** Desenfoque (`blur`) de una `box-shadow`, en dp. */
    ShadowBlurDp(0f),
    ;

    companion object {
        val count: Int = entries.size

        /** Canales que forman `transform`: `none` los pone todos en su identidad. */
        val transformGroup: List<KpknChannel> = listOf(
            TranslateXDp, TranslateYDp, TranslateXFraction, TranslateYFraction, ScaleX, ScaleY, RotationDeg,
        )
    }
}

/** Valor de todos los canales en un instante. [active] = la animación está aplicándose (con relleno incluido). */
class KpknPose {
    private val values = FloatArray(KpknChannel.count) { KpknChannel.entries[it].identity }

    /** `true` si algún fotograma de la animación está aplicándose; `false` = el elemento está en su estado base. */
    var active: Boolean = false
        internal set

    operator fun get(channel: KpknChannel): Float = values[channel.ordinal]

    internal operator fun set(channel: KpknChannel, value: Float) {
        values[channel.ordinal] = value
    }

    val alpha: Float get() = values[KpknChannel.Alpha.ordinal]
    val translateXDp: Float get() = values[KpknChannel.TranslateXDp.ordinal]
    val translateYDp: Float get() = values[KpknChannel.TranslateYDp.ordinal]
    val translateXFraction: Float get() = values[KpknChannel.TranslateXFraction.ordinal]
    val translateYFraction: Float get() = values[KpknChannel.TranslateYFraction.ordinal]
    val scaleX: Float get() = values[KpknChannel.ScaleX.ordinal]
    val scaleY: Float get() = values[KpknChannel.ScaleY.ordinal]
    val rotationDeg: Float get() = values[KpknChannel.RotationDeg.ordinal]

    internal fun copyFrom(base: FloatArray) {
        System.arraycopy(base, 0, values, 0, values.size)
    }
}

/** Fotogramas de un solo canal: `offsets` crecientes en 0..1 y, por intervalo, una curva propia (la de `animation-timing-function` del fotograma) o `null`. */
internal class KpknTrack(
    val offsets: FloatArray,
    val values: FloatArray,
    val easings: Array<Easing?>,
) {
    /**
     * Valor en [progress] (0..1). Semántica de CSS: la curva rige CADA intervalo entre fotogramas del canal, con el progreso local del
     * intervalo; un fotograma con `animation-timing-function` propio manda en el intervalo que EMPIEZA en él.
     */
    fun sample(progress: Float, fallback: Easing): Float {
        val n = offsets.size
        if (progress <= offsets[0]) return values[0]
        if (progress >= offsets[n - 1]) return values[n - 1]
        var i = 0
        while (i < n - 2 && progress >= offsets[i + 1]) i++
        val span = offsets[i + 1] - offsets[i]
        val local = (progress - offsets[i]) / span
        val eased = (easings[i] ?: fallback).transform(local)
        return values[i] + (values[i + 1] - values[i]) * eased
    }
}

/**
 * Un `@keyframes` del lienzo, con nombre. Se evalúa con [sample] (progreso 0..1 de la iteración) o, entero y con la temporización
 * de CSS, con [KpknAnimation.poseAt]. [css] guarda el texto original para poder compararlo con el lienzo.
 */
class KpknKeyframes internal constructor(
    val name: String,
    val css: String,
    private val tracks: Array<KpknTrack?>,
    internal val base: FloatArray,
) {
    /** Los canales que este `@keyframes` anima. */
    val channels: List<KpknChannel> = KpknChannel.entries.filter { tracks[it.ordinal] != null }

    /** Escribe en [out] el valor de cada canal para el progreso [progress] de una iteración; los canales no animados quedan en su base. */
    fun sample(progress: Float, easing: Easing, out: KpknPose) {
        out.copyFrom(base)
        for (i in tracks.indices) {
            val track = tracks[i] ?: continue
            out[KpknChannel.entries[i]] = track.sample(progress, easing)
        }
    }
}

/** Constructor de los valores de un fotograma: se escribe como el CSS (`opacity: 0; transform: translateY(14px)`). */
class KpknFrameBuilder internal constructor() {
    internal val values = LinkedHashMap<KpknChannel, Float>()
    internal var transformNone = false

    fun alpha(value: Float) { values[KpknChannel.Alpha] = value }
    fun translateX(dp: Float) { values[KpknChannel.TranslateXDp] = dp }
    fun translateY(dp: Float) { values[KpknChannel.TranslateYDp] = dp }
    fun translateXFraction(fraction: Float) { values[KpknChannel.TranslateXFraction] = fraction }
    fun translateYFraction(fraction: Float) { values[KpknChannel.TranslateYFraction] = fraction }
    fun scale(value: Float) { values[KpknChannel.ScaleX] = value; values[KpknChannel.ScaleY] = value }
    fun scaleX(value: Float) { values[KpknChannel.ScaleX] = value }
    fun scaleY(value: Float) { values[KpknChannel.ScaleY] = value }
    fun rotate(degrees: Float) { values[KpknChannel.RotationDeg] = degrees }
    fun dashOffset(fraction: Float) { values[KpknChannel.DashOffset] = fraction }
    fun width(fraction: Float) { values[KpknChannel.WidthFraction] = fraction }
    fun backgroundShift(percent: Float) { values[KpknChannel.BackgroundShiftPct] = percent }
    fun maxHeight(dp: Float) { values[KpknChannel.MaxHeightDp] = dp }
    fun brightness(value: Float) { values[KpknChannel.Brightness] = value }
    fun blur(dp: Float) { values[KpknChannel.BlurDp] = dp }
    fun colorMix(value: Float) { values[KpknChannel.ColorMix] = value }

    /** `box-shadow: 0 0 <blur> <spread> <color con alfa>`: el color lo elige quien dibuja. */
    fun shadow(alpha: Float, spreadDp: Float, blurDp: Float) {
        values[KpknChannel.ShadowAlpha] = alpha
        values[KpknChannel.ShadowSpreadDp] = spreadDp
        values[KpknChannel.ShadowBlurDp] = blurDp
    }

    /** `transform: none`: todos los canales de `transform` que la regla anima vuelven a su identidad en este fotograma. */
    fun transformNone() { transformNone = true }
}

/** Fotograma de un `@keyframes`: posición (0..1) y valores. */
private class KpknFrame(val offset: Float, val easing: Easing?, val builder: KpknFrameBuilder)

/** Constructor de un [KpknKeyframes] (ver [kpknKeyframes]). */
class KpknKeyframesBuilder internal constructor(private val name: String, private val css: String) {
    private val frames = ArrayList<KpknFrame>()
    private val baseOverrides = LinkedHashMap<KpknChannel, Float>()

    /**
     * Valor del elemento fuera de la animación para un canal (el que usan los fotogramas implícitos): p. ej. `stroke-dashoffset: 100`
     * en el trazo o `opacity: 0` en el estilo de un elemento que solo escribe el 100 %.
     */
    fun base(channel: KpknChannel, value: Float) { baseOverrides[channel] = value }

    /**
     * Fotograma en [offset] (0 = 0 %, 1 = 100 %). [easing] = `animation-timing-function` escrito DENTRO del fotograma: rige el intervalo
     * que empieza aquí. Sin él, manda la curva de la animación.
     */
    fun at(offset: Float, easing: Easing? = null, block: KpknFrameBuilder.() -> Unit) {
        frames += KpknFrame(offset, easing, KpknFrameBuilder().apply(block))
    }

    internal fun build(): KpknKeyframes {
        val base = FloatArray(KpknChannel.count) { KpknChannel.entries[it].identity }
        for ((channel, value) in baseOverrides) base[channel.ordinal] = value
        val sorted = frames.sortedBy { it.offset }
        // Canales de transform que la regla anima de verdad: `transform: none` solo los toca a ellos.
        val usedTransform = KpknChannel.transformGroup.filter { c ->
            baseOverrides.containsKey(c) || sorted.any { it.builder.values.containsKey(c) }
        }
        val tracks = arrayOfNulls<KpknTrack>(KpknChannel.count)
        for (channel in KpknChannel.entries) {
            val offs = ArrayList<Float>()
            val vals = ArrayList<Float>()
            val eases = ArrayList<Easing?>()
            for (frame in sorted) {
                val own = frame.builder.values[channel]
                val value = when {
                    own != null -> own
                    frame.builder.transformNone && channel in usedTransform -> channel.identity
                    else -> null
                } ?: continue
                offs += frame.offset
                vals += value
                eases += frame.easing
            }
            if (offs.isEmpty()) continue
            if (offs.first() > 0f) { offs.add(0, 0f); vals.add(0, base[channel.ordinal]); eases.add(0, null) }
            if (offs.last() < 1f) { offs += 1f; vals += base[channel.ordinal]; eases += null }
            tracks[channel.ordinal] = KpknTrack(offs.toFloatArray(), vals.toFloatArray(), eases.toTypedArray())
        }
        return KpknKeyframes(name, css, tracks, base)
    }
}

/**
 * Define un `@keyframes` con el mismo orden y los mismos valores que el CSS del lienzo.
 *
 * ```
 * val rise = kpknKeyframes("cv-rise", "@keyframes cv-rise { 0% { opacity: 0; transform: translateY(14px); } 100% { opacity: 1; transform: none; } }") {
 *     at(0f) { alpha(0f); translateY(14f) }
 *     at(1f) { alpha(1f); transformNone() }
 * }
 * ```
 */
fun kpknKeyframes(name: String, css: String, block: KpknKeyframesBuilder.() -> Unit): KpknKeyframes =
    KpknKeyframesBuilder(name, css).apply(block).build()

/** `animation-direction`. */
enum class KpknDirection { Normal, Reverse, Alternate, AlternateReverse }

/** `animation-fill-mode`. */
enum class KpknFill {
    None, Forwards, Backwards, Both;

    val backwards: Boolean get() = this == Backwards || this == Both
    val forwards: Boolean get() = this == Forwards || this == Both
}

/**
 * Una `animation` de CSS completa: [keyframes] + duración, curva (aplicada a CADA intervalo entre fotogramas), retardo, repeticiones
 * ([INFINITE] o un número), dirección y relleno. [poseAt] reproduce la línea de tiempo de CSS en cualquier instante, sin reloj.
 *
 * ```
 * val anim = KpknAnimation(KpknFamilies.EntradaSube.keyframes, KpknDuration(420), KpknMotion.Estandar, fill = KpknFill.Both)
 * val pose = KpknPose().also { anim.poseAt(210f, it) }      // a mitad de la entrada
 * ```
 */
class KpknAnimation(
    val keyframes: KpknKeyframes,
    val duration: KpknDuration,
    val easing: Easing,
    val delay: KpknDuration = KpknDuration(0),
    val iterations: Float = 1f,
    val direction: KpknDirection = KpknDirection.Normal,
    val fill: KpknFill = KpknFill.None,
) {
    val isInfinite: Boolean get() = iterations.isInfinite()

    /** Duración de una vuelta en ms: el periodo exacto de un bucle. */
    val periodMs: Float get() = duration.ms.toFloat()

    /** Instante (ms desde el principio, retardo incluido) en que termina la última vuelta; infinito en los bucles. */
    val endMs: Float get() = if (isInfinite) Float.POSITIVE_INFINITY else delay.ms + duration.ms * iterations

    /** Copia con otros parámetros (p. ej. el valor exacto de una fila del lienzo en lugar del canónico). */
    fun copy(
        duration: KpknDuration = this.duration,
        easing: Easing = this.easing,
        delay: KpknDuration = this.delay,
        iterations: Float = this.iterations,
        direction: KpknDirection = this.direction,
        fill: KpknFill = this.fill,
    ): KpknAnimation = KpknAnimation(keyframes, duration, easing, delay, iterations, direction, fill)

    /**
     * Escribe en [out] el estado de la animación [timeMs] milisegundos después de arrancar (el retardo cuenta dentro).
     * Antes del retardo y después del final solo se aplica según [fill]; fuera de eso, [KpknPose.active] es `false` y los valores son
     * los del elemento sin animar. Con [reducedMotion] no hay animación: el estado final (y, en un bucle, el estado base).
     */
    fun poseAt(timeMs: Float, out: KpknPose, reducedMotion: Boolean = false) {
        out.copyFrom(keyframes.base)
        out.active = false
        val dur = duration.ms.toFloat()
        if (dur <= 0f) return
        if (reducedMotion) {
            if (!isInfinite) applyProgress(finalIterationIndex(), 1f, out)
            return
        }
        val local = timeMs - delay.ms
        if (local < 0f) {
            if (fill.backwards) applyProgress(0, 0f, out)
            return
        }
        val overall = local / dur
        if (!isInfinite && overall >= iterations) {
            if (fill.forwards) {
                val index = finalIterationIndex()
                val frac = iterations - floor(iterations)
                applyProgress(index, if (frac == 0f) 1f else frac, out)
            }
            return
        }
        val index = floor(overall).toInt()
        applyProgress(index, overall - index, out)
    }

    private fun finalIterationIndex(): Int =
        if (iterations - floor(iterations) == 0f) iterations.toInt() - 1 else floor(iterations).toInt()

    private fun applyProgress(iterationIndex: Int, simple: Float, out: KpknPose) {
        val reversed = when (direction) {
            KpknDirection.Normal -> false
            KpknDirection.Reverse -> true
            KpknDirection.Alternate -> iterationIndex % 2 == 1
            KpknDirection.AlternateReverse -> iterationIndex % 2 == 0
        }
        keyframes.sample(if (reversed) 1f - simple else simple, easing, out)
        out.active = true
    }

    companion object {
        /** `animation-iteration-count: infinite`. */
        const val INFINITE: Float = Float.POSITIVE_INFINITY
    }
}

/** Mezcla de dos colores como la hace CSS: en sRGB y con alfa premultiplicado (no en Oklab, que es lo que usa `lerp` de Compose). */
fun kpknCssColorLerp(from: Color, to: Color, fraction: Float): Color {
    val f = fraction.coerceIn(0f, 1f)
    val a = from.alpha + (to.alpha - from.alpha) * f
    if (a <= 0f) return Color.Transparent
    val r = (from.red * from.alpha + (to.red * to.alpha - from.red * from.alpha) * f) / a
    val g = (from.green * from.alpha + (to.green * to.alpha - from.green * from.alpha) * f) / a
    val b = (from.blue * from.alpha + (to.blue * to.alpha - from.blue * from.alpha) * f) / a
    return Color(r.coerceIn(0f, 1f), g.coerceIn(0f, 1f), b.coerceIn(0f, 1f), a.coerceIn(0f, 1f))
}
