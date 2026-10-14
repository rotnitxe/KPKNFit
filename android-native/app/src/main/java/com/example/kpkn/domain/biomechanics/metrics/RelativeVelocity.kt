package com.example.kpkn.domain.biomechanics.metrics

import com.example.kpkn.domain.biomechanics.reps.CountSeriesBuilder
import com.example.kpkn.domain.biomechanics.reps.MovementPieces
import com.example.kpkn.domain.biomechanics.reps.RepCycle
import com.example.kpkn.domain.biomechanics.reps.SignalUnit
import com.example.kpkn.domain.biomechanics.signal.RobustStats
import kotlin.math.abs
import kotlin.math.ceil
import kotlin.math.floor
import kotlin.math.max
import kotlin.math.min
import kotlin.math.roundToInt
import kotlin.math.sqrt

/** Velocidad de la concéntrica de una repetición, en la unidad de [VelocitySummary.unit]; NaN donde no hay dato. */
class RepVelocity(val mcv: Double, val peak: Double) {
    companion object {
        val NONE: RepVelocity = RepVelocity(Double.NaN, Double.NaN)
    }
}

/**
 * Velocidad de una serie: [reps] va por posición de ciclo; el resto sale de las repeticiones FULL.
 * [lossPct] = (mejor − última)/mejor·100, con al menos 3 FULL, medida en la ventana del 10 % al 90 % de la concéntrica y solo
 * si el ruido de la señal lo permite. Todo en [unit]: sin calibrar nunca en m/s; con el disco calibrado, `CalibratedVelocity`
 * da otra [VelocitySummary] en [VelocityUnit.MPS].
 *
 * @property durationError error relativo esperado de la duración de una concéntrica por el ruido de la señal (0,025 = ±2,5 %), la
 *   mediana de las FULL; `null` si no se pudo estimar. Es la cota de Cramér–Rao de ajustar un tramo de jerk mínimo a la señal con
 *   el ruido que el propio ajuste deja sin explicar.
 * @property lossReliable `false` si la medida no vale ni siquiera para las velocidades relativas (un ruido enorme o un arranque
 *   irregular): ni pérdida ni velocidad (la serie queda sin `VELOCITY_RELATIVE`). Con un ruido menor pero que no deja dar la pérdida,
 *   vale `true`, la pérdida es `null` y las velocidades relativas se dan.
 */
class VelocitySummary(
    val available: Boolean,
    val peakAvailable: Boolean,
    val unit: VelocityUnit?,
    val reps: List<RepVelocity>,
    val first: Double?,
    val best: Double?,
    val last: Double?,
    val peakBest: Double?,
    val lossPct: Double?,
    val durationError: Double? = null,
    val lossReliable: Boolean = true,
)

/**
 * La ventana de velocidad (§7, «Velocidad»): la velocidad de una repetición se mide entre el 10 % y el 90 % del desplazamiento de
 * su concéntrica, 0,8·ΔL / (t90 − t10), donde la salida y la llegada son las de un tramo de jerk mínimo s(τ) = 10τ³ − 15τ⁴ + 6τ⁵.
 * Se mide igual en todas las repeticiones, así que el porcentaje de pérdida no se sesga.
 */
internal object VelocityWindow {
    /** Instante normalizado τ en el que s(τ) = [level]. */
    fun tauAt(level: Double): Double {
        var lo = 0.0
        var hi = 1.0
        repeat(60) {
            val mid = 0.5 * (lo + hi)
            val s = mid * mid * mid * (10.0 - 15.0 * mid + 6.0 * mid * mid)
            if (s < level) lo = mid else hi = mid
        }
        return 0.5 * (lo + hi)
    }

    /** s(τ) = 10τ³ − 15τ⁴ + 6τ⁵ con τ recortado a [0, 1]. */
    fun profile(tau: Double): Double {
        val t = if (tau < 0.0) 0.0 else if (tau > 1.0) 1.0 else tau
        return t * t * t * (10.0 - 15.0 * t + 6.0 * t * t)
    }

    /** Velocidad media en la ventana de un tramo de desplazamiento [displacement] y duración [durationS]. */
    fun speed(displacement: Double, durationS: Double, low: Double, high: Double): Double =
        (high - low) * displacement / ((tauAt(high) - tauAt(low)) * durationS)
}

/**
 * La pérdida de velocidad de una serie a partir de la velocidad en la ventana de cada FULL (en orden).
 *
 * VL% = (mejor − última)/mejor·100 con al menos 3 FULL. [reliable] dice si valen las velocidades relativas de cada repetición (las
 *  barras); la pérdida, que resta dos velocidades con ruido y se queda con la mejor de varias, exige más. Hay tres motivos para no darla:
 *
 * 1. **Ruido de la señal.** La duración de una sola concéntrica no se conoce mejor que lo que dejan el ruido del punto de barra, su
 *    recorrido y el número de fotogramas. Para un tramo de jerk mínimo con ruido blanco de desviación σ, recorrido ΔL y n = fs·T
 *    muestras, la cota de Cramér–Rao de la duración (con la salida, los dos niveles y la duración libres) es
 *    `error ≈ κ·(σ/ΔL)/√n`, con κ = 5,55 (calculada numéricamente) y 6,6 para el ajuste real (300 simulaciones). Con el atleta al
 *    23 % del cuadro y 0,004 FH de ruido por punto sale entre el 3 y el 4 %, y la «mejor» de varias repeticiones sube sola con ese
 *    ruido (en la sentadilla sin disco daba hasta un 20 % de pérdida con una pérdida real de 0). σ se mide en la propia concéntrica
 *    ([fitNoise]): lo que el ajuste de jerk mínimo, con los dos niveles libres, deja sin explicar en la concéntrica y ±0,2 s.
 *    Medirlo en las frecuencias altas de la señal (lo que quita un paso bajo) lo subestimaba: el ajuste de la duración ve el ruido
 *    de baja frecuencia, y el filtro de atípicos ya había quitado parte del de alta. Por encima de `VbtTuning.vlMaxDurationError` (2,5 %) la pérdida no se da, pero cada velocidad
 *    relativa sigue valiendo (una velocidad con ±5 % sirve para ver cuánto más lenta es una repetición, aunque la pérdida no).
 * 2. **Ruido enorme.** Por encima de `VbtTuning.vlMaxBarDurationError` (10 %) tampoco valen las velocidades relativas.
 * 3. **Arranque irregular.** Si las tres primeras repeticiones difieren más de `VbtTuning.vlMaxFirstCv` (CV), algo falló en la
 *    medida de alguna de ellas (un hueco, una oclusión): las primeras no deberían tener una fatiga que lo explique. Ni pérdida ni
 *    velocidades relativas.
 */
internal class VelocityLoss(val lossPct: Double?, val durationError: Double?, val reliable: Boolean) {
    companion object {
        /**
         * Error relativo esperado de la duración de una concéntrica: [sigma] es el ruido blanco de la señal, [displacement] el
         * recorrido de la concéntrica (en las mismas unidades), [fs] los fotogramas por segundo y [seconds] su duración. `null` sin datos.
         */
        fun durationError(sigma: Double, displacement: Double, fs: Double, seconds: Double, tuning: AnalysisTuning): Double? {
            if (!(sigma >= 0.0) || !(displacement > 0.0) || !(fs * seconds > 0.0)) return null
            return tuning.vbt.vlDurationErrorFactor * (sigma / displacement) / sqrt(fs * seconds)
        }

        /**
         * Ruido equivalente de [x] alrededor de un tramo de jerk mínimo que va del índice fraccionario [from] al [to]: la desviación
         * robusta (MAD) de lo que el modelo `x(i) = v0·(1 − s) + v1·s`, con s el perfil de jerk mínimo del tramo y los dos niveles
         * (v0 antes, v1 después) ajustados por mínimos cuadrados, deja sin explicar entre [marginS] s antes y después del tramo. Se
         * corrige por los cuatro parámetros del ajuste (los dos niveles y la salida y la duración del tramo). `NaN` con menos de 10
         * muestras. Es lo que ve el ajuste de la duración: el ruido de baja frecuencia, no solo el de alta.
         */
        fun fitNoise(x: DoubleArray, from: Double, to: Double, fs: Double, marginS: Double): Double {
            val length = to - from
            if (!(length > 2.0) || !(fs > 0.0)) return Double.NaN
            val lo = max(0, floor(from - marginS * fs).toInt())
            val hi = min(x.size - 1, ceil(to + marginS * fs).toInt())
            if (hi - lo < 9) return Double.NaN
            var saa = 0.0
            var sab = 0.0
            var sbb = 0.0
            var sax = 0.0
            var sbx = 0.0
            var n = 0
            for (i in lo..hi) {
                val xi = x[i]
                if (xi.isNaN()) continue
                val s = VelocityWindow.profile((i - from) / length)
                val a = 1.0 - s
                saa += a * a
                sab += a * s
                sbb += s * s
                sax += a * xi
                sbx += s * xi
                n++
            }
            if (n < 10) return Double.NaN
            val det = saa * sbb - sab * sab
            if (!(abs(det) > 1e-9)) return Double.NaN
            val v0 = (sbb * sax - sab * sbx) / det
            val v1 = (saa * sbx - sab * sax) / det
            val residual = DoubleArray(n)
            var m = 0
            for (i in lo..hi) {
                val xi = x[i]
                if (xi.isNaN()) continue
                val s = VelocityWindow.profile((i - from) / length)
                residual[m++] = xi - (v0 * (1.0 - s) + v1 * s)
            }
            val mad = RobustStats.mad(residual)
            return if (mad.isNaN()) Double.NaN else mad * sqrt(n / (n - 4.0))
        }

        fun of(windowSpeeds: List<Double>, durationError: Double?, tuning: AnalysisTuning): VelocityLoss {
            // Un ruido enorme: ni las velocidades relativas valen.
            if (durationError != null && durationError > tuning.vbt.vlMaxBarDurationError) return VelocityLoss(null, durationError, false)
            if (windowSpeeds.size < tuning.metrics.vlMinFull) return VelocityLoss(null, durationError, true)
            // Una medida rota en las primeras repeticiones: tampoco.
            val firstCv = coefficientOfVariation(windowSpeeds.take(tuning.vbt.vlFirstReps))
            if (firstCv != null && firstCv > tuning.vbt.vlMaxFirstCv) return VelocityLoss(null, durationError, false)
            // Un ruido que no deja medir la pérdida: las velocidades relativas valen, la pérdida no.
            if (durationError != null && durationError > tuning.vbt.vlMaxDurationError) return VelocityLoss(null, durationError, true)
            val best = windowSpeeds.max()
            val last = windowSpeeds.last()
            val loss = if (best > 0.0) (best - last) / best * 100.0 else null
            return VelocityLoss(loss, durationError, true)
        }

        /** Desviación típica muestral entre la media; `null` con menos de 2 valores o con media nula. */
        fun coefficientOfVariation(v: List<Double>): Double? {
            if (v.size < 2) return null
            val mean = v.average()
            if (!(mean > 0.0)) return null
            var sum = 0.0
            for (x in v) sum += (x - mean) * (x - mean)
            return sqrt(sum / (v.size - 1)) / mean
        }
    }
}

/**
 * Velocidad sin calibrar (§7, «Velocidad»).
 *
 * - **Unidad:** con señal de desplazamiento, la velocidad vertical del punto de barra en BH/s ([VelocityUnit.BODY_HEIGHTS_PER_S]; si
 *   el perfil no tiene punto de barra, la de la propia señal de conteo); con señal de ángulo, la angular ([VelocityUnit.DEG_PER_S]).
 * - **MCV por repetición:** desplazamiento de la concéntrica entre su duración. Las repeticiones fallidas no tienen MCV.
 *   La concéntrica sale de `RepPhaseResult` cuando la señal de velocidad es la propia señal de conteo (ángulos, disco).
 *   Cuando el punto de barra es un sustituto de pose (el hombro en la sentadilla, la mano en el peso muerto) NO se mide con
 *   las fases de la señal de conteo, que es otra (la cadera, el codo): en la sentadilla sin disco esas fases caen a ±0,5 s
 *   de donde el hombro empieza y acaba de subir. Se ajusta el tramo de jerk mínimo de la propia señal de barra
 *   (`MovementPieces.cycle`, el mismo ajuste de las fases) y se miden en ella la duración y el desplazamiento (entre los niveles
 *   robustos de reposo y de giro).
 * - **Pico:** el máximo de |v| de la concéntrica (derivada central de la señal con paso bajo); solo con fs ≥ 50.
 * - **Por serie:** `first`, `best` y `last` son la MCV de la primera, la más rápida y la última FULL. La pérdida de velocidad
 *   se mide en la ventana del 10 % al 90 % del desplazamiento ([VelocityWindow]) y se da solo con al menos 3 FULL y si el ruido
 *   de la señal lo permite ([VelocityLoss]); si no es fiable, la serie queda sin velocidad relativa. Se excluyen las parciales y
 *   las fallidas.
 * - **Disponibilidad:** en las vistas de conteo del perfil, y con fs ≥ 20 (hay fases).
 *
 * Sin calibrar, las velocidades solo se muestran en % (la pérdida y las barras relativas a la mejor).
 */
object RelativeVelocity {
    fun compute(ctx: MetricContext): VelocitySummary {
        val n = ctx.cycles.size
        val none = VelocitySummary(false, false, null, List(n) { RepVelocity.NONE }, null, null, null, null, null)
        if (n == 0 || !ctx.geometryOk || ctx.phases.reps.isEmpty() || ctx.view !in ctx.profile.countViews) return none

        val tuning = ctx.tuning
        val angular = ctx.series.unit == SignalUnit.DEGREES
        val barY = ctx.barYSmooth
        // Una razón (dominadas, jalones) no es un desplazamiento: sin punto de barra no hay velocidad.
        if (!angular && barY == null && ctx.series.unit != SignalUnit.BODY_HEIGHTS) return none
        val unit = if (angular) VelocityUnit.DEG_PER_S else VelocityUnit.BODY_HEIGHTS_PER_S
        val fs = ctx.series.fs
        val wantsPeak = fs >= tuning.metrics.peakMinFps

        fun signalOf(cycle: RepCycle): DoubleArray = when {
            angular -> ctx.smoothedCount(cycle.side)
            barY != null -> barY
            else -> ctx.smoothedCount(cycle.side)
        }

        // La misma señal sin filtrar: ahí se mide el ruido.
        fun rawOf(cycle: RepCycle): DoubleArray? {
            val side = cycle.side
            val counted = if (side != null) ctx.series.perSide?.get(side) ?: ctx.series.r else ctx.series.r
            return if (!angular && barY != null) ctx.series.barY else counted
        }

        // Punto de barra de pose: su propia concéntrica, ajustada en su propia señal.
        val own = if (!angular && barY != null && ctx.series.source != CountSeriesBuilder.SOURCE_PLATE) ownConcentrics(ctx, barY) else null

        val reps = ArrayList<RepVelocity>(n)
        val windowSpeeds = ArrayList<Double>()
        val fullErrors = ArrayList<Double>()
        for ((k, cycle) in ctx.cycles.withIndex()) {
            if (cycle.kind == RepKind.FAILED) {
                reps.add(RepVelocity.NONE)
                continue
            }
            val x = signalOf(cycle)
            val fitted = if (cycle.side == null) own?.get(k) else null
            val i0: Double
            val i1: Double
            val displacement: Double
            if (fitted != null) {
                i0 = fitted.from
                i1 = fitted.to
                displacement = fitted.displacement
            } else {
                val span = ctx.concentric(cycle)
                if (span == null) {
                    reps.add(RepVelocity.NONE)
                    continue
                }
                i0 = ctx.indexOf(span.fromUs)
                i1 = ctx.indexOf(span.toUs)
                val y0 = SeriesMath.interpolate(x, i0)
                val y1 = SeriesMath.interpolate(x, i1)
                if (y0.isNaN() || y1.isNaN()) {
                    reps.add(RepVelocity.NONE)
                    continue
                }
                displacement = abs(y1 - y0)
            }
            val seconds = (i1 - i0) / fs
            if (seconds < tuning.metrics.minConcentricS || displacement.isNaN()) {
                reps.add(RepVelocity.NONE)
                continue
            }
            var peak = Double.NaN
            if (wantsPeak) {
                var best = 0.0
                var found = false
                for (j in max(1, ceil(i0).toInt())..min(x.size - 2, floor(i1).toInt())) {
                    val a = x[j + 1]
                    val b = x[j - 1]
                    if (a.isNaN() || b.isNaN()) continue
                    best = max(best, abs(a - b) * fs / 2.0)
                    found = true
                }
                if (found) peak = best
            }
            val mcv = displacement / seconds
            reps.add(RepVelocity(mcv, peak))
            if (cycle.kind == RepKind.FULL) {
                windowSpeeds.add(VelocityWindow.speed(displacement, seconds, tuning.vbt.vlWindowLow, tuning.vbt.vlWindowHigh))
                val raw = rawOf(cycle)
                if (raw != null) {
                    val sigma = VelocityLoss.fitNoise(raw, i0, i1, fs, tuning.vbt.vlNoiseMarginS)
                    val error = VelocityLoss.durationError(sigma, displacement, fs, seconds, tuning)
                    if (error != null) fullErrors.add(error)
                }
            }
        }

        val fullMcv = ArrayList<Double>()
        var peakBest = Double.NaN
        var any = false
        var anyPeak = false
        for ((k, cycle) in ctx.cycles.withIndex()) {
            val rep = reps[k]
            if (!rep.mcv.isNaN()) any = true
            if (!rep.peak.isNaN()) anyPeak = true
            if (cycle.kind != RepKind.FULL) continue
            if (!rep.mcv.isNaN()) fullMcv.add(rep.mcv)
            if (!rep.peak.isNaN() && (peakBest.isNaN() || rep.peak > peakBest)) peakBest = rep.peak
        }
        if (!any) return none

        val loss = VelocityLoss.of(windowSpeeds, if (fullErrors.isEmpty()) null else SeriesMath.median(fullErrors), tuning)
        if (!loss.reliable) {
            // El ruido de la señal es del tamaño de lo que se quiere medir: ni pérdida ni barras relativas.
            return VelocitySummary(false, false, null, List(n) { RepVelocity.NONE }, null, null, null, null, null, loss.durationError, false)
        }
        val best = if (fullMcv.isEmpty()) Double.NaN else fullMcv.max()
        return VelocitySummary(
            available = true,
            peakAvailable = anyPeak,
            unit = unit,
            reps = reps,
            first = if (fullMcv.isEmpty()) null else SeriesMath.finiteOrNull(fullMcv.first()),
            best = SeriesMath.finiteOrNull(best),
            last = if (fullMcv.isEmpty()) null else SeriesMath.finiteOrNull(fullMcv.last()),
            peakBest = SeriesMath.finiteOrNull(peakBest),
            lossPct = loss.lossPct?.let { SeriesMath.finiteOrNull(it) },
            durationError = loss.durationError,
            lossReliable = true,
        )
    }

    // ------------------------------------------------------------------------------------------------------------
    // Concéntrica de un punto de barra de pose, ajustada en su propia señal
    // ------------------------------------------------------------------------------------------------------------

    /** Concéntrica de la señal de barra de un ciclo: índices FRACCIONARIOS de la rejilla y desplazamiento entre sus niveles (BH). */
    private class OwnConcentric(val from: Double, val to: Double, val displacement: Double)

    /**
     * El ajuste de jerk mínimo de la subida y la bajada de cada ciclo sobre la señal de barra orientada como la de conteo (el
     * reposo es el mínimo y el giro el máximo), con los mismos valles y pico del segmentador. La concéntrica es la bajada (arranque E)
     * o la subida (arranque C). El desplazamiento es la diferencia entre el nivel del giro y el del reposo vecino, con los
     * niveles robustos de `MovementPieces` (media móvil de ±0,3 s). Los ciclos por lado y los que no se ajustan quedan `null`
     * y se miden con las fases de la señal de conteo.
     */
    private fun ownConcentrics(ctx: MetricContext, barY: DoubleArray): Array<OwnConcentric?> {
        val out = arrayOfNulls<OwnConcentric>(ctx.cycles.size)
        val sign = orientation(ctx.smoothedCount(null), barY)
        val x = DoubleArray(barY.size) { sign * barY[it] }
        val half = max(1, (ctx.tuning.pieceLevelWindowS * ctx.series.fs).roundToInt())
        for ((k, cycle) in ctx.cycles.withIndex()) {
            if (cycle.side != null) continue
            val valleyLeft = if (cycle.leftValley < cycle.peak) cycle.leftValley else -1
            val valleyRight = cycle.rightValley ?: -1
            val pieces = MovementPieces.cycle(x, valleyLeft, cycle.peak, valleyRight, half)
            val con = (if (ctx.eccentricFirst) pieces.fall else pieces.rise) ?: continue
            val lowerBound = if (valleyLeft >= 0) valleyLeft else 0
            val upperBound = if (valleyRight > cycle.peak && valleyRight < x.size) valleyRight else x.size - 1
            val top = highLevel(x, cycle.peak, half, lowerBound, upperBound)
            val restIndex = if (ctx.eccentricFirst) valleyRight else valleyLeft
            if (restIndex < 0 || restIndex >= x.size) continue
            val rest = lowLevel(x, restIndex, half)
            val displacement = top - rest
            if (top.isNaN() || rest.isNaN() || !(displacement > 0.0)) continue
            out[k] = OwnConcentric(con.start, con.end, displacement)
        }
        return out
    }

    /** +1 si la señal de barra sube y baja con la de conteo, −1 si va al revés (el disco o la mano en una subida con arranque C). */
    private fun orientation(count: DoubleArray, bar: DoubleArray): Double {
        var n = 0
        var meanCount = 0.0
        var meanBar = 0.0
        for (i in count.indices) {
            if (count[i].isNaN() || bar[i].isNaN()) continue
            meanCount += count[i]
            meanBar += bar[i]
            n++
        }
        if (n < MIN_ORIENTATION_SAMPLES) return 1.0
        meanCount /= n
        meanBar /= n
        var covariance = 0.0
        for (i in count.indices) {
            if (count[i].isNaN() || bar[i].isNaN()) continue
            covariance += (count[i] - meanCount) * (bar[i] - meanBar)
        }
        return if (covariance >= 0.0) 1.0 else -1.0
    }

    // Copias de los niveles robustos de MovementPieces (privados allí): media móvil de ±half y su mínimo o máximo cerca de un extremo.

    private fun meanAround(x: DoubleArray, centre: Int, half: Int): Double {
        var sum = 0.0
        var count = 0
        for (i in max(0, centre - half)..min(x.size - 1, centre + half)) {
            val v = x[i]
            if (v.isNaN()) continue
            sum += v
            count++
        }
        return if (count == 0) Double.NaN else sum / count
    }

    /** Nivel de reposo junto al valle: el mínimo de la media móvil de ±[half] en [valley] ± [half]. */
    private fun lowLevel(x: DoubleArray, valley: Int, half: Int): Double {
        var best = Double.NaN
        for (c in max(0, valley - half)..min(x.size - 1, valley + half)) {
            val m = meanAround(x, c, half)
            if (!m.isNaN() && (best.isNaN() || m < best)) best = m
        }
        return best
    }

    /** Nivel del giro: el máximo de la media móvil de ±[half] en [peak] ± 2·[half], dentro de [lowest, highest]. */
    private fun highLevel(x: DoubleArray, peak: Int, half: Int, lowest: Int, highest: Int): Double {
        var best = Double.NaN
        for (c in max(lowest, peak - 2 * half)..min(highest, peak + 2 * half)) {
            val m = meanAround(x, c, half)
            if (!m.isNaN() && (best.isNaN() || m > best)) best = m
        }
        return best
    }

    private const val MIN_ORIENTATION_SAMPLES = 3
}
