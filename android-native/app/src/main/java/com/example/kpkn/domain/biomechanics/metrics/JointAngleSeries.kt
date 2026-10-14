package com.example.kpkn.domain.biomechanics.metrics

import com.example.kpkn.domain.biomechanics.kinematics.Angles
import com.example.kpkn.domain.biomechanics.kinematics.FrameGeometry
import com.example.kpkn.domain.biomechanics.kinematics.Side
import com.example.kpkn.domain.biomechanics.kinematics.ViewKind
import com.example.kpkn.domain.biomechanics.kinematics.ankle
import com.example.kpkn.domain.biomechanics.kinematics.elbow
import com.example.kpkn.domain.biomechanics.kinematics.hip
import com.example.kpkn.domain.biomechanics.kinematics.knee
import com.example.kpkn.domain.biomechanics.kinematics.shoulder
import com.example.kpkn.domain.biomechanics.kinematics.wrist
import com.example.kpkn.domain.biomechanics.pose.PoseLandmark
import com.example.kpkn.domain.biomechanics.profiles.AnalysisProfile
import com.example.kpkn.domain.biomechanics.profiles.RomAngle
import com.example.kpkn.domain.biomechanics.profiles.StartPhase
import com.example.kpkn.domain.biomechanics.reps.CountSeries
import com.example.kpkn.domain.biomechanics.reps.CountSeriesBuilder
import com.example.kpkn.domain.biomechanics.reps.PhaseSummary
import com.example.kpkn.domain.biomechanics.reps.PreparedTrack
import com.example.kpkn.domain.biomechanics.reps.RepCycle
import com.example.kpkn.domain.biomechanics.reps.RepPhaseResult
import com.example.kpkn.domain.biomechanics.reps.SegmentationResult
import com.example.kpkn.domain.biomechanics.signal.GapFiller
import com.example.kpkn.domain.biomechanics.signal.Hampel
import com.example.kpkn.domain.biomechanics.signal.Resampler
import com.example.kpkn.domain.biomechanics.signal.RobustStats
import com.example.kpkn.domain.biomechanics.signal.ZeroPhaseLowPass
import kotlin.math.acos
import kotlin.math.ceil
import kotlin.math.floor
import kotlin.math.max
import kotlin.math.min
import kotlin.math.roundToInt

/**
 * Una magnitud de las métricas sobre la rejilla uniforme de la serie de conteo (t_k = [t0Us] + k/[fs]): filtrada de atípicos,
 * con los huecos de hasta 0,25 s rellenos ([filled] los marca), con paso bajo de fase cero a 5 Hz (§4.9) y NaN en los cortes.
 * Comparte rejilla con `CountSeries`, así que un índice del segmentador vale aquí.
 */
class MetricSeries(
    val t0Us: Long,
    val fs: Double,
    val v: DoubleArray,
    val filled: BooleanArray,
) {
    /** Número de muestras de la rejilla. */
    val size: Int get() = v.size

    /** Índice fraccionario de la rejilla del instante [tUs]. */
    fun indexOf(tUs: Long): Double = (tUs - t0Us) * fs / SeriesMath.MICROS

    /** Valor interpolado en [tUs]; NaN fuera de la serie o junto a un corte. */
    fun valueAt(tUs: Long): Double = SeriesMath.interpolate(v, indexOf(tUs))

    /** Muestras con valor. */
    fun validCount(): Int {
        var count = 0
        for (x in v) if (!x.isNaN()) count++
        return count
    }
}

/** Utilidades sobre arreglos de la rejilla. Todas ignoran los NaN y no asignan nada, salvo las que devuelven un arreglo. */
internal object SeriesMath {
    const val MICROS = 1_000_000.0

    /** Valor de [x] en el índice fraccionario [index] (interpolación lineal); NaN fuera de [x] o junto a un NaN. */
    fun interpolate(x: DoubleArray, index: Double): Double {
        if (index.isNaN() || x.isEmpty() || index < -0.5 || index > x.size - 0.5) return Double.NaN
        val idx = index.coerceIn(0.0, (x.size - 1).toDouble())
        val i = floor(idx).toInt()
        val a = x[i]
        val f = idx - i
        if (f == 0.0 || i + 1 >= x.size) return a
        val b = x[i + 1]
        return if (a.isNaN() || b.isNaN()) Double.NaN else a + (b - a) * f
    }

    /**
     * Media móvil centrada de ±[half] muestras sobre las válidas. Donde [x] es NaN sigue siendo NaN, y se exige que al menos
     * la mitad de la ventana (recortada en los extremos) tenga valor.
     */
    fun movingAverage(x: DoubleArray, half: Int): DoubleArray {
        val n = x.size
        val out = DoubleArray(n)
        val sum = DoubleArray(n + 1)
        val cnt = IntArray(n + 1)
        for (i in 0 until n) {
            val valid = !x[i].isNaN()
            sum[i + 1] = sum[i] + (if (valid) x[i] else 0.0)
            cnt[i + 1] = cnt[i] + (if (valid) 1 else 0)
        }
        for (i in 0 until n) {
            if (x[i].isNaN()) {
                out[i] = Double.NaN
                continue
            }
            val lo = max(0, i - half)
            val hi = min(n - 1, i + half)
            val c = cnt[hi + 1] - cnt[lo]
            out[i] = if (c * 2 < hi - lo + 1) Double.NaN else (sum[hi + 1] - sum[lo]) / c
        }
        return out
    }

    fun maxIn(x: DoubleArray, from: Int, to: Int): Double {
        var best = Double.NaN
        for (k in max(0, from)..min(x.size - 1, to)) {
            val v = x[k]
            if (!v.isNaN() && (best.isNaN() || v > best)) best = v
        }
        return best
    }

    fun minIn(x: DoubleArray, from: Int, to: Int): Double {
        var best = Double.NaN
        for (k in max(0, from)..min(x.size - 1, to)) {
            val v = x[k]
            if (!v.isNaN() && (best.isNaN() || v < best)) best = v
        }
        return best
    }

    /** Índice del máximo de [x] en [from, to]; −1 si no hay ningún valor. */
    fun argMax(x: DoubleArray, from: Int, to: Int): Int {
        var best = -1
        for (k in max(0, from)..min(x.size - 1, to)) {
            val v = x[k]
            if (!v.isNaN() && (best < 0 || v > x[best])) best = k
        }
        return best
    }

    /** Fracción de muestras con valor en [from, to]. */
    fun validFraction(x: DoubleArray, from: Int, to: Int): Double {
        val lo = max(0, from)
        val hi = min(x.size - 1, to)
        if (hi < lo) return 0.0
        var count = 0
        for (k in lo..hi) if (!x[k].isNaN()) count++
        return count.toDouble() / (hi - lo + 1)
    }

    /** Media de [x] en [from, to] sin los NaN; NaN si falta más de la parte [1 − minFraction] de las muestras. */
    fun meanIn(x: DoubleArray, from: Int, to: Int, minFraction: Double): Double {
        val lo = max(0, from)
        val hi = min(x.size - 1, to)
        if (hi < lo) return Double.NaN
        var sum = 0.0
        var count = 0
        for (k in lo..hi) {
            if (x[k].isNaN()) continue
            sum += x[k]
            count++
        }
        return if (count == 0 || count < minFraction * (hi - lo + 1)) Double.NaN else sum / count
    }

    /** Mediana de los valores finitos de [values]; NaN si no hay ninguno. */
    fun median(values: List<Double>): Double {
        val v = DoubleArray(values.size) { values[it] }
        return RobustStats.median(v)
    }

    /** Desviación típica muestral (n − 1) de [values]; NaN con menos de dos. */
    fun sampleSd(values: List<Double>): Double {
        if (values.size < 2) return Double.NaN
        val mean = values.average()
        var s = 0.0
        for (x in values) s += (x - mean) * (x - mean)
        return kotlin.math.sqrt(s / (values.size - 1))
    }

    /** Pendiente de la recta de mínimos cuadrados de [values] frente a 0, 1, 2…; NaN con menos de dos. */
    fun olsSlope(values: List<Double>): Double {
        val n = values.size
        if (n < 2) return Double.NaN
        val meanX = (n - 1) / 2.0
        val meanY = values.average()
        var sxy = 0.0
        var sxx = 0.0
        for (i in 0 until n) {
            sxy += (i - meanX) * (values[i] - meanY)
            sxx += (i - meanX) * (i - meanX)
        }
        return sxy / sxx
    }

    /** `null` si [x] no es finito: lo que no se pudo medir no se guarda como número. */
    fun finiteOrNull(x: Double): Double? = if (x.isNaN() || x.isInfinite()) null else x

    /** [x] redondeado a [decimals] decimales; `null` si no es finito. */
    fun roundedOrNull(x: Double, decimals: Int): Double? {
        if (x.isNaN() || x.isInfinite()) return null
        val scale = Math.pow(10.0, decimals.toDouble())
        return Math.round(x * scale) / scale
    }
}

/** Construye una [MetricSeries] desde un valor por fotograma (NaN = falta). */
internal object MetricSeriesBuilder {
    /**
     * Hampel, remuestreo a la rejilla de la traza, relleno de huecos de hasta 0,25 s (marcados) y paso bajo de fase cero de
     * 5 Hz (4 Hz con fs < 25) (§4.6 a §4.9). [raw] tiene un valor por fotograma de `prepared.track.frames`.
     */
    fun fromFrames(prepared: PreparedTrack, raw: DoubleArray, tuning: AnalysisTuning): MetricSeries {
        val tb = prepared.timebase
        val frames = prepared.track.frames
        val times = LongArray(frames.size) { frames[it].tUs }
        val fs = tb.fs
        val half = max(tuning.hampelMinHalfWindow, (tuning.hampelWindowFraction * fs).roundToInt())
        val clean = Hampel.filter(raw, half, tuning.hampelSigmas)
        val grid = Resampler.toUniform(times, clean, tb, tuning.fillCountGapS)
        val fill = GapFiller.fill(grid, fs, tuning.fillShortGapS)
        val fc = if (fs < tuning.metrics.metricSlowFpsBelow) tuning.metrics.metricLowPassSlowHz else tuning.metrics.metricLowPassHz
        return MetricSeries(tb.t0Us, fs, ZeroPhaseLowPass.filter(fill.values, fs, fc), fill.filled)
    }

    /** Longitud de referencia L̂ = P90 de la distancia 2D (FH) entre [a] y [b] sobre todos los fotogramas; NaN si no hay datos. */
    fun segmentRef(geo: FrameGeometry, a: PoseLandmark, b: PoseLandmark): Double {
        val lengths = DoubleArray(geo.frameCount) { geo.distance(it, a, b) }
        return RobustStats.percentile(lengths, 90.0)
    }
}

/**
 * Todo lo que las métricas necesitan del conteo: la traza preparada, la serie de conteo (con el signo de la segmentación), la
 * segmentación y las fases. Los instantes de inicio y fin de cada fase salen SIEMPRE de [phases] (`RepPhaseResult`): el MCV,
 * el Δτ temprano y el bloqueo usan los mismos instantes que el tempo.
 */
class MetricContext(
    val prepared: PreparedTrack,
    val series: CountSeries,
    val seg: SegmentationResult,
    val phases: PhaseSummary,
    val profile: AnalysisProfile,
    val tuning: AnalysisTuning = AnalysisTuning.DEFAULT,
) {
    /** Un tramo de una repetición, en µs. */
    class Span(val fromUs: Long, val toUs: Long)

    val fs: Double get() = series.fs
    val size: Int get() = series.size
    val cycles: List<RepCycle> get() = seg.cycles
    val view: ViewKind get() = prepared.view.kind
    val near: Side? get() = prepared.near?.near
    val facing: Int get() = prepared.facing
    val eccentricFirst: Boolean get() = profile.startPhase == StartPhase.ECCENTRIC_FIRST

    /** Altura corporal S en unidades FH, o `null` si no se midió. */
    val bodyHeightFh: Double? get() = prepared.scale?.bodyHeightFh

    /**
     * Las métricas de geometría (ángulos, distancias, velocidades) exigen un fotograma de aspecto conocido: una traza v1
     * (`aspectKnown = false`) solo sirve para contar.
     */
    val geometryOk: Boolean get() = prepared.track.aspectKnown

    /** Las métricas absolutas del perfil (ROM, profundidad, tronco, trayectoria) solo se dan en las vistas del perfil. */
    val metricViewOk: Boolean get() = geometryOk && view in profile.metricViews

    private val phaseByCycle: Map<Int, RepPhaseResult> = phases.reps.associateBy { it.cycleIndex }
    private val smoothedCache = HashMap<Side?, DoubleArray>()

    /** Paso bajo de las fases: 8 Hz con el disco a ≥ 50 fps; 5 Hz en los demás casos (igual que `RepPhases`). */
    private val countFilterHz: Double
        get() = if (series.source == CountSeriesBuilder.SOURCE_PLATE && series.fs >= tuning.plateFastMinFps) {
            tuning.plateLowPassHzFast
        } else {
            tuning.phaseLowPassHz
        }

    /** Fases de la repetición [cycle]; `null` con fs < 20 (sin fases) o si el ciclo no las tiene. */
    fun phasesOf(cycle: RepCycle): RepPhaseResult? = phaseByCycle[cycle.index]

    /** Lado que trabaja en [cycle]: el del ciclo (conteo por lado), el único lado fiable, o el cercano. */
    fun workSideOf(cycle: RepCycle): Side? = cycle.side ?: series.workSide ?: near

    /** Señal de conteo con paso bajo, del lado [side] en los perfiles por lado (como la que usan las fases). */
    fun smoothedCount(side: Side?): DoubleArray = smoothedCache.getOrPut(side) {
        val raw = if (side != null) series.perSide?.get(side) ?: series.r else series.r
        ZeroPhaseLowPass.filter(raw, series.fs, countFilterHz)
    }

    /** Altura (Y, BH, hacia abajo) del punto de barra con paso bajo; `null` si el perfil no tiene punto de barra. */
    val barYSmooth: DoubleArray? by lazy { series.barY?.let { ZeroPhaseLowPass.filter(it, series.fs, countFilterHz) } }

    /** Posición horizontal (X, BH) del punto de barra con paso bajo; `null` si el perfil no tiene punto de barra. */
    val barXSmooth: DoubleArray? by lazy { series.barX?.let { ZeroPhaseLowPass.filter(it, series.fs, countFilterHz) } }

    fun indexOf(tUs: Long): Double = (tUs - series.t0Us) * series.fs / SeriesMath.MICROS

    /** Semiancho, en muestras, de la media móvil con la que se buscan los extremos ([MetricsTuning.extremumWindowS]). */
    val extremumHalf: Int get() = max(1, (tuning.metrics.extremumWindowS * series.fs / 2.0).roundToInt())

    /** Ventana [desde, hasta] (índices, ambos incluidos) de la repetición, con [marginS] a cada lado; `null` si queda vacía. */
    fun repWindow(cycle: RepCycle, marginS: Double): IntRange? {
        val ph = phasesOf(cycle)
        val a: Double
        val b: Double
        if (ph != null) {
            a = indexOf(ph.departUs)
            b = indexOf(ph.arriveUs)
        } else {
            a = cycle.leftValley.toDouble()
            b = (cycle.rightValley ?: cycle.peak).toDouble()
        }
        val m = marginS * series.fs
        val lo = max(0, floor(a - m).toInt())
        val hi = min(size - 1, ceil(b + m).toInt())
        return if (hi > lo) lo..hi else null
    }

    /** Ventana alrededor del giro: de la llegada a la salida del giro, con [MetricsTuning.turnWindowHalfS] a cada lado. */
    fun turnWindow(cycle: RepCycle): IntRange? {
        val ph = phasesOf(cycle)
        val centre: Double
        val half: Double
        if (ph != null) {
            val s = indexOf(ph.turnStartUs)
            val e = indexOf(ph.turnEndUs)
            centre = 0.5 * (s + e)
            half = 0.5 * (e - s)
        } else {
            centre = cycle.peak.toDouble()
            half = 0.0
        }
        val m = tuning.metrics.turnWindowHalfS * series.fs
        val lo = max(0, floor(centre - half - m).toInt())
        val hi = min(size - 1, ceil(centre + half + m).toInt())
        return if (hi >= lo) lo..hi else null
    }

    /** Concéntrica de [cycle] según las fases; `null` si la repetición no tiene fases o no completa la concéntrica. */
    fun concentric(cycle: RepCycle): Span? {
        val ph = phasesOf(cycle) ?: return null
        val span = if (eccentricFirst) {
            if (ph.concentricMs == null) return null
            Span(ph.turnEndUs, ph.arriveUs)
        } else {
            Span(ph.departUs, ph.turnStartUs)
        }
        return if (span.toUs > span.fromUs) span else null
    }

    /** Media de [x] (sobre la rejilla) en [tUs] ± [halfS]; NaN si falta más de la parte [1 − minWindowValidFraction] de las muestras. */
    fun meanAround(x: DoubleArray, tUs: Long, halfS: Double): Double {
        val c = indexOf(tUs)
        val h = halfS * series.fs
        val lo = ceil(c - h).toInt()
        val hi = floor(c + h).toInt()
        if (hi < lo) return SeriesMath.interpolate(x, c)
        return SeriesMath.meanIn(x, lo, hi, tuning.metrics.minWindowValidFraction)
    }
}

/**
 * Serie del ángulo de la articulación del perfil (`RomAngle`), a 5 Hz de corte, sobre la geometría corregida de
 * `PreparedTrack` (aspecto y giro del móvil):
 * - KNEE = 180 − θ(H,K,A), HIP = 180 − θ(S,H,K), ELBOW = 180 − θ(S,E,W): flexión en grados (0 = extendido);
 * - ARM_ELEVATION = β = acos((Y_E − Y_S)/L̂_brazo), el ángulo del brazo con la vertical hacia abajo;
 * - TRUNK = τ, inclinación del tronco (+ hacia delante).
 */
object JointAngleSeries {
    /**
     * Serie del lado [side] (el cercano a la cámara, o cada lado en los perfiles por lado); `null` si falta el dato en casi
     * toda la traza. Con [boneGate] (por defecto, solo en SIDE) un segmento 2D fuera de [0,65; 1,25]·L̂ deja el ángulo como
     * falta (§4.6): un hueso que se alarga o se acorta de golpe es un punto mal puesto, no un movimiento.
     */
    fun build(
        prepared: PreparedTrack,
        angle: RomAngle,
        side: Side,
        tuning: AnalysisTuning = AnalysisTuning.DEFAULT,
        boneGate: Boolean = prepared.view.kind == ViewKind.SIDE,
    ): MetricSeries? {
        val geo = prepared.geo
        val raw = DoubleArray(prepared.track.frames.size) { Double.NaN }
        when (angle) {
            RomAngle.KNEE -> flexion(geo, raw, side.hip(), side.knee(), side.ankle(), boneGate, tuning)
            RomAngle.HIP -> flexion(geo, raw, side.shoulder(), side.hip(), side.knee(), boneGate, tuning)
            RomAngle.ELBOW -> flexion(geo, raw, side.shoulder(), side.elbow(), side.wrist(), boneGate, tuning)
            RomAngle.ARM_ELEVATION -> armElevation(geo, raw, side)
            RomAngle.TRUNK -> trunk(geo, raw, side, prepared.facing)
        }
        val series = MetricSeriesBuilder.fromFrames(prepared, raw, tuning)
        return if (series.validCount() >= tuning.minObservedS * series.fs) series else null
    }

    /** 180 − θ(a, b, c) por fotograma; con [gate], los dos segmentos dentro de [boneMinRatio, boneMaxRatio]·L̂. */
    private fun flexion(
        geo: FrameGeometry,
        raw: DoubleArray,
        a: PoseLandmark,
        b: PoseLandmark,
        c: PoseLandmark,
        gate: Boolean,
        tuning: AnalysisTuning,
    ) {
        val refAb = if (gate) MetricSeriesBuilder.segmentRef(geo, a, b) else Double.NaN
        val refBc = if (gate) MetricSeriesBuilder.segmentRef(geo, b, c) else Double.NaN
        val gated = gate && refAb > 0.0 && refBc > 0.0
        for (i in raw.indices) {
            val pa = geo.point(i, a) ?: continue
            val pb = geo.point(i, b) ?: continue
            val pc = geo.point(i, c) ?: continue
            if (gated) {
                val ab = geo.distance(i, a, b)
                val bc = geo.distance(i, b, c)
                if (ab < tuning.boneMinRatio * refAb || ab > tuning.boneMaxRatio * refAb) continue
                if (bc < tuning.boneMinRatio * refBc || bc > tuning.boneMaxRatio * refBc) continue
            }
            val theta = Angles.included(pa, pb, pc)
            if (!theta.isNaN()) raw[i] = STRAIGHT - theta
        }
    }

    private fun armElevation(geo: FrameGeometry, raw: DoubleArray, side: Side) {
        val lHat = MetricSeriesBuilder.segmentRef(geo, side.shoulder(), side.elbow())
        if (!(lHat > 0.0)) return
        for (i in raw.indices) {
            val ps = geo.point(i, side.shoulder()) ?: continue
            val pe = geo.point(i, side.elbow()) ?: continue
            raw[i] = Math.toDegrees(acos(((pe.y - ps.y) / lHat).coerceIn(-1.0, 1.0)))
        }
    }

    private fun trunk(geo: FrameGeometry, raw: DoubleArray, side: Side, facing: Int) {
        for (i in raw.indices) {
            val ps = geo.point(i, side.shoulder()) ?: continue
            val ph = geo.point(i, side.hip()) ?: continue
            raw[i] = Angles.trunkLean(ps, ph, facing)
        }
    }

    private const val STRAIGHT = 180.0
}
