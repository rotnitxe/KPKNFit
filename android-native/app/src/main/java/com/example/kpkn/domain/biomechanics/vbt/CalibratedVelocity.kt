package com.example.kpkn.domain.biomechanics.vbt

import com.example.kpkn.domain.biomechanics.bar.BarSample
import com.example.kpkn.domain.biomechanics.bar.BarSampleSource
import com.example.kpkn.domain.biomechanics.bar.BarTrack
import com.example.kpkn.domain.biomechanics.metrics.AnalysisTuning
import com.example.kpkn.domain.biomechanics.metrics.MetricContext
import com.example.kpkn.domain.biomechanics.metrics.RepKind
import com.example.kpkn.domain.biomechanics.metrics.RepVelocity
import com.example.kpkn.domain.biomechanics.metrics.ScaleInfo
import com.example.kpkn.domain.biomechanics.metrics.ScaleSource
import com.example.kpkn.domain.biomechanics.metrics.SeriesMath
import com.example.kpkn.domain.biomechanics.metrics.VelocityLoss
import com.example.kpkn.domain.biomechanics.metrics.VelocitySummary
import com.example.kpkn.domain.biomechanics.metrics.VelocityUnit
import com.example.kpkn.domain.biomechanics.metrics.VelocityWindow
import com.example.kpkn.domain.biomechanics.reps.CountSeriesBuilder
import com.example.kpkn.domain.biomechanics.signal.GapFiller
import com.example.kpkn.domain.biomechanics.signal.Resampler
import com.example.kpkn.domain.biomechanics.signal.ZeroPhaseLowPass
import kotlin.math.abs
import kotlin.math.ceil
import kotlin.math.cos
import kotlin.math.floor
import kotlin.math.max
import kotlin.math.min
import kotlin.math.sin

/**
 * La posición vertical del disco, en píxeles y con la vertical de la gravedad (se deshace el roll del móvil), sobre una rejilla
 * uniforme PROPIA del disco (t_k = [t0Us] + k/[fs]), solo con las muestras de detector (HOUGH y NCC: las de respaldo, POSE_PRIOR y
 * PREDICTED, valen para seguir el disco pero no para medir su velocidad, §8 punto 5).
 *
 * Los huecos de hasta 0,1 s se interpolan; con más, la serie queda en NaN y la repetición que los cruce no tiene velocidad. La señal
 * va con paso bajo de fase cero: 8 Hz a ≥ 50 fps y 6 Hz a 30 fps (§4.9).
 */
class PlateVerticalSeries private constructor(
    val t0Us: Long,
    val fs: Double,
    /** Y del disco en píxeles (hacia abajo), filtrada; NaN donde falta. */
    val y: DoubleArray,
    /** La posición medida, en píxeles, sin filtrar (con los huecos cortos interpolados): de ella sale el ruido de cada concéntrica. */
    val raw: DoubleArray,
) {
    /** Índice fraccionario de la rejilla del instante [tUs]. */
    fun indexOf(tUs: Long): Double = (tUs - t0Us) * fs / MICROS

    /** Valor interpolado en [tUs]; NaN fuera de la serie o junto a un hueco. */
    fun valueAt(tUs: Long): Double = SeriesMath.interpolate(y, indexOf(tUs))

    /**
     * Velocidad media del disco entre [fromUs] y [toUs], en m/s: |Y(fin) − Y(inicio)|·[metersPerPx]/duración. NaN si falta un
     * dato en el tramo (un hueco de más de 0,1 s) o dura menos de [minSeconds].
     */
    fun meanSpeed(fromUs: Long, toUs: Long, metersPerPx: Double, minSeconds: Double): Double {
        val seconds = (toUs - fromUs) / MICROS
        if (!(seconds >= minSeconds) || !(metersPerPx > 0.0)) return Double.NaN
        if (!complete(fromUs, toUs)) return Double.NaN
        val y0 = valueAt(fromUs)
        val y1 = valueAt(toUs)
        if (y0.isNaN() || y1.isNaN()) return Double.NaN
        return abs(y1 - y0) * metersPerPx / seconds
    }

    /** Velocidad máxima del disco entre [fromUs] y [toUs], en m/s (derivada central de la señal filtrada). NaN si no hay dato. */
    fun peakSpeed(fromUs: Long, toUs: Long, metersPerPx: Double): Double {
        if (!(metersPerPx > 0.0) || !complete(fromUs, toUs)) return Double.NaN
        val from = max(1, ceil(indexOf(fromUs)).toInt())
        val to = min(y.size - 2, floor(indexOf(toUs)).toInt())
        var best = 0.0
        var found = false
        for (k in from..to) {
            val a = y[k + 1]
            val b = y[k - 1]
            if (a.isNaN() || b.isNaN()) continue
            best = max(best, abs(a - b) * fs / 2.0)
            found = true
        }
        return if (found) best * metersPerPx else Double.NaN
    }

    /** `true` si todas las muestras entre [fromUs] y [toUs] (y las dos de sus extremos) tienen valor. */
    private fun complete(fromUs: Long, toUs: Long): Boolean {
        val a = indexOf(fromUs)
        val b = indexOf(toUs)
        if (a.isNaN() || b.isNaN() || a < -0.5 || b > y.size - 0.5) return false
        for (k in max(0, floor(a).toInt())..min(y.size - 1, ceil(b).toInt())) if (y[k].isNaN()) return false
        return true
    }

    companion object {
        private const val MICROS = 1_000_000.0

        /**
         * La serie vertical del disco de [bar]; [rollDeg] es la inclinación del móvil (`FrameGeometry.rollDeg`, 0 si no se corrigió).
         * `null` si hay menos de dos muestras, la cadencia no se puede medir o no hay ninguna muestra de detector.
         */
        fun of(bar: BarTrack, rollDeg: Double, tuning: AnalysisTuning = AnalysisTuning.DEFAULT): PlateVerticalSeries? {
            val samples = if (isSorted(bar.samples)) bar.samples else bar.samples.sortedBy { it.tUs }
            val n = samples.size
            if (n < 2) return null
            val times = LongArray(n) { samples[it].tUs }
            val tb = Resampler.timebase(times)
            if (!(tb.fs > 0.0) || tb.n < 2) return null

            val cosR = cos(Math.toRadians(-rollDeg))
            val sinR = sin(Math.toRadians(-rollDeg))
            val cx0 = bar.frameWidth / 2.0
            val cy0 = bar.frameHeight / 2.0
            var detected = 0
            val values = DoubleArray(n)
            for (i in 0 until n) {
                val s = samples[i]
                if (s.source == BarSampleSource.HOUGH || s.source == BarSampleSource.NCC) {
                    // Y con la vertical de la gravedad: el giro −roll alrededor del centro del fotograma.
                    values[i] = (s.cx - cx0) * sinR + (s.cy - cy0) * cosR + cy0
                    detected++
                } else {
                    values[i] = Double.NaN
                }
            }
            if (detected < 2) return null

            val grid = Resampler.toUniform(times, values, tb, MAX_TIME_GAP_S)
            val filled = GapFiller.fill(grid, tb.fs, tuning.vbt.plateMaxGapS).values
            val fc = if (tb.fs >= tuning.plateFastMinFps) tuning.plateLowPassHzFast else tuning.vbt.plateLowPassSlowHz
            val smooth = ZeroPhaseLowPass.filter(filled, tb.fs, fc)
            return PlateVerticalSeries(tb.t0Us, tb.fs, smooth, filled)
        }

        /** Un salto de tiempo entre dos muestras del disco mayor que esto deja la rejilla en NaN (no es un hueco de valores). */
        private const val MAX_TIME_GAP_S = 0.6

        private fun isSorted(samples: List<BarSample>): Boolean {
            for (i in 1 until samples.size) if (samples[i].tUs < samples[i - 1].tUs) return false
            return true
        }
    }
}

/**
 * Velocidad media de la concéntrica en m/s con el disco calibrado (§7, «Velocidad», y §8): MCV por repetición, pico (solo con la
 * cadencia del disco ≥ 50 fps) y la pérdida de velocidad.
 *
 * - **Cuándo:** solo con una escala válida del disco (`ScaleSource.PLATE` y metros por píxel vertical) y con el disco como señal de
 *   conteo (`series.source == "plate"`). Si no, valen las velocidades relativas de `RelativeVelocity`.
 * - **MCV:** el desplazamiento vertical del disco (con la escala vertical, corregida por la elipse) entre el inicio y el fin de la
 *   concéntrica de `RepPhaseResult` (la CON completa, convención VBT), entre su duración. La señal es [PlateVerticalSeries].
 * - **Pico:** el máximo de |v| de la concéntrica; solo si el disco se midió a ≥ 50 fps.
 * - **Pérdida:** como en `RelativeVelocity`, en la ventana del 10 % al 90 % del desplazamiento y con la guarda de ruido.
 * - Una repetición cuyo tramo tenga un hueco de más de 0,1 s sin detector no tiene velocidad.
 */
object CalibratedVelocity {
    fun compute(ctx: MetricContext, bar: BarTrack, scale: ScaleInfo): VelocitySummary {
        val n = ctx.cycles.size
        val none = VelocitySummary(false, false, null, List(n) { RepVelocity.NONE }, null, null, null, null, null)
        val metersPerPx = scale.metersPerPxV
        if (n == 0 || scale.source != ScaleSource.PLATE || metersPerPx == null || !(metersPerPx > 0.0)) return none
        if (ctx.series.source != CountSeriesBuilder.SOURCE_PLATE || !ctx.geometryOk || ctx.phases.reps.isEmpty()) return none
        val tuning = ctx.tuning
        val plate = PlateVerticalSeries.of(bar, ctx.prepared.geo.rollDeg, tuning) ?: return none
        val wantsPeak = plate.fs >= tuning.metrics.peakMinFps

        val reps = ArrayList<RepVelocity>(n)
        val fullMcv = ArrayList<Double>()
        val windowSpeeds = ArrayList<Double>()
        val fullErrors = ArrayList<Double>()
        var peakBest = Double.NaN
        var any = false
        var anyPeak = false
        for (cycle in ctx.cycles) {
            val span = if (cycle.kind == RepKind.FAILED) null else ctx.concentric(cycle)
            if (span == null) {
                reps.add(RepVelocity.NONE)
                continue
            }
            val mcv = plate.meanSpeed(span.fromUs, span.toUs, metersPerPx, tuning.metrics.minConcentricS)
            if (mcv.isNaN()) {
                reps.add(RepVelocity.NONE)
                continue
            }
            val peak = if (wantsPeak) plate.peakSpeed(span.fromUs, span.toUs, metersPerPx) else Double.NaN
            reps.add(RepVelocity(mcv, peak))
            any = true
            if (!peak.isNaN()) anyPeak = true
            if (cycle.kind == RepKind.FULL) {
                fullMcv.add(mcv)
                val seconds = (span.toUs - span.fromUs) / 1_000_000.0
                windowSpeeds.add(VelocityWindow.speed(mcv * seconds, seconds, tuning.vbt.vlWindowLow, tuning.vbt.vlWindowHigh))
                val sigma = VelocityLoss.fitNoise(plate.raw, plate.indexOf(span.fromUs), plate.indexOf(span.toUs), plate.fs, tuning.vbt.vlNoiseMarginS)
                val relative = VelocityLoss.durationError(sigma, mcv * seconds / metersPerPx, plate.fs, seconds, tuning)
                if (relative != null) fullErrors.add(relative)
                if (!peak.isNaN() && (peakBest.isNaN() || peak > peakBest)) peakBest = peak
            }
        }
        if (!any) return none

        val error = if (fullErrors.isEmpty()) null else SeriesMath.median(fullErrors)
        val loss = VelocityLoss.of(windowSpeeds, error, tuning)
        val best = if (fullMcv.isEmpty()) Double.NaN else fullMcv.max()
        return VelocitySummary(
            available = true,
            peakAvailable = anyPeak,
            unit = VelocityUnit.MPS,
            reps = reps,
            first = if (fullMcv.isEmpty()) null else SeriesMath.finiteOrNull(fullMcv.first()),
            best = SeriesMath.finiteOrNull(best),
            last = if (fullMcv.isEmpty()) null else SeriesMath.finiteOrNull(fullMcv.last()),
            peakBest = SeriesMath.finiteOrNull(peakBest),
            lossPct = loss.lossPct?.let { SeriesMath.finiteOrNull(it) },
            durationError = loss.durationError,
            lossReliable = loss.reliable,
        )
    }
}
