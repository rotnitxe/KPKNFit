package com.example.kpkn.domain.biomechanics.reps

import com.example.kpkn.domain.biomechanics.kinematics.Side
import java.util.Random
import kotlin.math.roundToInt

/**
 * Señales de conteo hechas a mano para probar las reglas del segmentador una a una, sin pasar por la pose: tramos de
 * jerk mínimo entre niveles (reposo = 0, giro = amplitud) y pausas, a [fs] muestras por segundo. Cada `go` acaba en su
 * nivel exactamente en la última muestra de su duración.
 */
internal class SignalBuilder(val fs: Double = 30.0, start: Double = 0.0) {
    private val values = ArrayList<Double>()
    private var level = start

    /** Muestras escritas hasta ahora. */
    val size: Int get() = values.size

    /** Instante (s) de la siguiente muestra. */
    val nowS: Double get() = values.size / fs

    fun hold(seconds: Double): SignalBuilder {
        repeat(count(seconds)) { values.add(level) }
        return this
    }

    fun go(seconds: Double, to: Double): SignalBuilder {
        val n = count(seconds)
        val from = level
        for (i in 1..n) {
            val tau = i.toDouble() / n
            values.add(from + (to - from) * (10.0 * tau * tau * tau - 15.0 * tau * tau * tau * tau + 6.0 * tau * tau * tau * tau * tau))
        }
        level = to
        return this
    }

    /** Una repetición: sube a [amplitude], aguanta, vuelve al reposo (0) y descansa. */
    fun rep(amplitude: Double = 1.0, up: Double = 1.5, top: Double = 0.5, down: Double = 1.5, rest: Double = 0.5): SignalBuilder =
        go(up, amplitude).hold(top).go(down, 0.0).hold(rest)

    private fun count(seconds: Double): Int = (seconds * fs).roundToInt().coerceAtLeast(0)

    fun values(): DoubleArray = values.toDoubleArray()

    /** La serie, con ruido gaussiano de desviación [sigma] si se pide y los campos auxiliares que se indiquen. */
    fun series(
        sigma: Double = 0.0,
        seed: Long = 7L,
        unit: SignalUnit = SignalUnit.BODY_HEIGHTS,
        source: String = CountSeriesBuilder.SOURCE_POSE,
        walk: BooleanArray? = null,
        filled: BooleanArray? = null,
        barX: DoubleArray? = null,
        barY: DoubleArray? = null,
        perSide: Map<Side, DoubleArray>? = null,
        raw: DoubleArray = values(),
        validFraction: Double = 1.0,
    ): CountSeries = seriesOf(raw, fs, sigma, seed, unit, source, walk, filled, barX, barY, perSide, validFraction)

    companion object {
        fun seriesOf(
            raw: DoubleArray,
            fs: Double = 30.0,
            sigma: Double = 0.0,
            seed: Long = 7L,
            unit: SignalUnit = SignalUnit.BODY_HEIGHTS,
            source: String = CountSeriesBuilder.SOURCE_POSE,
            walk: BooleanArray? = null,
            filled: BooleanArray? = null,
            barX: DoubleArray? = null,
            barY: DoubleArray? = null,
            perSide: Map<Side, DoubleArray>? = null,
            validFraction: Double = 1.0,
        ): CountSeries {
            val rnd = Random(seed)
            val r = DoubleArray(raw.size) { raw[it] + if (sigma > 0.0) sigma * rnd.nextGaussian() else 0.0 }
            return CountSeries(
                t0Us = 0L,
                fs = fs,
                r = r,
                filled = filled ?: BooleanArray(raw.size),
                weak = BooleanArray(raw.size),
                walk = walk ?: BooleanArray(raw.size),
                barX = barX,
                barY = barY,
                perSide = perSide,
                source = source,
                unit = unit,
                validFraction = validFraction,
            )
        }
    }
}
