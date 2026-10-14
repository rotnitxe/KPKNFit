package com.example.kpkn.domain.biomechanics.reps

import com.example.kpkn.domain.biomechanics.bar.BarSampleSource
import com.example.kpkn.domain.biomechanics.bar.BarTrack
import com.example.kpkn.domain.biomechanics.kinematics.Angles
import com.example.kpkn.domain.biomechanics.kinematics.FrameGeometry
import com.example.kpkn.domain.biomechanics.kinematics.Side
import com.example.kpkn.domain.biomechanics.kinematics.Vec2
import com.example.kpkn.domain.biomechanics.kinematics.ankle
import com.example.kpkn.domain.biomechanics.kinematics.elbow
import com.example.kpkn.domain.biomechanics.kinematics.hip
import com.example.kpkn.domain.biomechanics.kinematics.knee
import com.example.kpkn.domain.biomechanics.kinematics.shoulder
import com.example.kpkn.domain.biomechanics.kinematics.wrist
import com.example.kpkn.domain.biomechanics.metrics.AnalysisTuning
import com.example.kpkn.domain.biomechanics.pose.PoseLandmark
import com.example.kpkn.domain.biomechanics.profiles.AnalysisProfile
import com.example.kpkn.domain.biomechanics.profiles.AnalysisProfileId
import com.example.kpkn.domain.biomechanics.profiles.BarPointRule
import com.example.kpkn.domain.biomechanics.profiles.CountSignal
import com.example.kpkn.domain.biomechanics.signal.Derivative
import com.example.kpkn.domain.biomechanics.signal.GapFiller
import com.example.kpkn.domain.biomechanics.signal.Hampel
import com.example.kpkn.domain.biomechanics.signal.Resampler
import com.example.kpkn.domain.biomechanics.signal.RobustStats
import com.example.kpkn.domain.biomechanics.signal.Timebase
import com.example.kpkn.domain.biomechanics.signal.ZeroPhaseLowPass
import kotlin.math.abs
import kotlin.math.acos
import kotlin.math.cos
import kotlin.math.max
import kotlin.math.roundToInt
import kotlin.math.sin
import kotlin.math.sqrt

/** Unidad de la señal de conteo: alturas corporales (BH = FH/S), grados o razón sin unidad. */
enum class SignalUnit { BODY_HEIGHTS, DEGREES, RATIO }

/**
 * La señal con la que se cuenta (§5): r(t) orientada para que el reposo sea el MÍNIMO y el giro lejano el máximo, sobre la
 * rejilla uniforme de la traza (t_k = [t0Us] + k/[fs]).
 *
 * @property r la señal; filtrada de atípicos (Hampel), con los huecos de hasta 0,6 s rellenos y NaN en los cortes.
 * @property filled `true` donde la muestra está rellena por interpolación (no es una medida).
 * @property weak `true` donde la muestra se calculó con algún punto de visibilidad débil (0,3 a 0,5): vale para contar.
 * @property walk `true` mientras el atleta camina (|dX tobillos| > 0,12 BH/s durante ≥ 0,3 s, dilatado ±0,3 s); todo
 *   `false` en LUNGE y STEP_UP y cuando no hay escala.
 * @property barX punto de barra (disco, o sustituto de pose según `BarPointRule`) en BH, para descartar movimientos de barra
 *   horizontales (sacar la barra, caminar con ella, recolocarla); `null` si el perfil no tiene punto de barra o no hay escala.
 * @property barY ídem en vertical.
 * @property perSide `rL` y `rR` si el perfil cuenta por lado y los dos lados son válidos (≥ 70 %, con puntos fiables).
 * @property source `"plate"` si cuenta el disco, `"pose"` si cuenta la pose.
 * @property unit unidad de [r].
 * @property validFraction fracción de la señal OBSERVADA (sin rellenos ni muestras débiles) entre su primera y su última
 *   muestra válida.
 * @property workSide en un perfil por lado con un solo lado fiable, el lado cuya señal es [r] (el cercano, o el lejano si es
 *   el que trabaja: FAR_SIDE_WORKING); `null` si [r] mezcla los dos lados o el perfil no cuenta por lado.
 */
class CountSeries(
    val t0Us: Long,
    val fs: Double,
    val r: DoubleArray,
    val filled: BooleanArray,
    val weak: BooleanArray,
    val walk: BooleanArray,
    val barX: DoubleArray?,
    val barY: DoubleArray?,
    val perSide: Map<Side, DoubleArray>?,
    val source: String,
    val unit: SignalUnit,
    val validFraction: Double,
    val workSide: Side? = null,
) {
    /** Número de muestras de la rejilla. */
    val size: Int get() = r.size

    /** Segundos desde el primer instante de la serie hasta la muestra [k]. */
    fun timeS(k: Int): Double = k / fs
}

/** Resultado de [CountSeriesBuilder.build]: la serie con la traza preparada, o el motivo de que no haya señal. */
sealed interface CountSeriesResult {
    data class Ok(val series: CountSeries, val prepared: PreparedTrack) : CountSeriesResult

    /** [reason]: `no_timebase`, `no_pose`, `no_scale`, `no_near_side` o `no_valid_signal`. */
    data class NoSignal(val reason: String) : CountSeriesResult
}

/**
 * Construye la serie de conteo de un perfil (§5, tabla de perfiles) sobre la geometría de [PreparedTrack]: la fórmula de
 * cada [CountSignal], el disco como señal principal si hay cobertura, el desfase pose↔disco, los huecos, caminar y el punto
 * de barra. Las señales de desplazamiento van en BH, así que sin escala no hay señal (`no_scale`).
 */
object CountSeriesBuilder {
    const val SOURCE_PLATE = "plate"
    const val SOURCE_POSE = "pose"

    private const val DEG_STRAIGHT = 180.0

    /** Perfiles sin caminar: la zancada y la subida al cajón mueven los pies a propósito. */
    private val NO_WALK = setOf(AnalysisProfileId.LUNGE, AnalysisProfileId.STEP_UP)

    fun build(
        prepared: PreparedTrack,
        bar: BarTrack?,
        profile: AnalysisProfile,
        tuning: AnalysisTuning = AnalysisTuning.DEFAULT,
    ): CountSeriesResult {
        val tb = prepared.timebase
        val frames = prepared.track.frames
        val nFrames = frames.size
        if (nFrames < 2 || !(tb.fs > 0.0) || tb.n < 2) return CountSeriesResult.NoSignal("no_timebase")
        var withPose = 0
        for (f in frames) if (f.hasPose) withPose++
        if (withPose == 0) return CountSeriesResult.NoSignal("no_pose")

        val times = LongArray(nFrames) { frames[it].tUs }
        val scale = prepared.scale?.bodyHeightFh
        val geo = prepared.geo

        // ---- fuente: el disco si el perfil lo usa y se ve en ≥ 85 % de los fotogramas con pose ----
        val plate = if (profile.usesPlate && profile.plateSignal != null && bar != null) {
            PlateFrames.of(bar, geo, times, tuning)
        } else {
            null
        }
        val usePlate = plate != null && plate.coverage >= tuning.plateMinCoverage
        val signal = if (usePlate) profile.plateSignal!! else profile.poseSignal
        val unit = unitOf(signal)
        if (unit == SignalUnit.BODY_HEIGHTS && scale == null) return CountSeriesResult.NoSignal("no_scale")
        val s = scale ?: 1.0

        val reader = PoseReader(geo)
        val finisher = Finisher(times, tb.fs, tb.t0Us, tb.n, tuning, unit == SignalUnit.BODY_HEIGHTS)

        // Desfase pose↔disco: la mano cercana frente al disco, ±3 fotogramas.
        val lag = if (usePlate) plateLag(plate!!, reader, prepared, nFrames, tuning) else 0

        var workSide: Side? = null
        var perSide: Map<Side, DoubleArray>? = null
        val main: Finished

        if (usePlate) {
            val sign = if (signal == CountSignal.PLATE_RISE) -1.0 else 1.0
            val raw = RawSignal(nFrames)
            for (i in 0 until nFrames) {
                val k = i + lag
                if (k < 0 || k >= nFrames) continue
                val y = plate!!.y[k]
                if (y.isNaN()) continue
                raw.v[i] = sign * y / s
                raw.weak[i] = plate.weak[k]
            }
            main = finisher.finish(raw)
        } else {
            val near = prepared.near?.near
            val needsNear = needsNearSide(signal)
            if (needsNear && near == null) return CountSeriesResult.NoSignal("no_near_side")
            val computer = SignalComputer(reader, geo, nFrames, s)
            if (profile.perSide && near != null) {
                val rawNear = computer.raw(signal, near)
                val rawFar = computer.raw(signal, near.opposite)
                val fin = finisher.finish(rawNear)
                val finFar = finisher.finish(rawFar)
                val strongNear = strongFraction(rawNear, withPose)
                val strongFar = strongFraction(rawFar, withPose)
                if (strongNear >= tuning.perSideMinValid && strongFar >= tuning.perSideMinValid) {
                    val left = if (near == Side.LEFT) fin else finFar
                    val right = if (near == Side.LEFT) finFar else fin
                    perSide = mapOf(Side.LEFT to left.r, Side.RIGHT to right.r)
                    main = Finished.maxOf(left, right)
                } else {
                    val ampNear = amplitude(fin.r)
                    val ampFar = amplitude(finFar.r)
                    if (ampFar > 0.0 && ampNear < tuning.singleSideAmplitudeRatio * ampFar &&
                        finFar.validCount >= tuning.minObservedS * tb.fs
                    ) {
                        main = Finished.summarize(finFar.r, finFar.filled, finFar.weak, weakCounts = true)
                        workSide = near.opposite
                    } else {
                        main = fin
                        workSide = near
                    }
                }
            } else {
                main = finisher.finish(computer.raw(signal, near ?: Side.LEFT))
            }
        }

        if (main.validCount < tuning.minObservedS * tb.fs) return CountSeriesResult.NoSignal("no_valid_signal")

        // ---- caminar y punto de barra (en BH: necesitan escala) ----
        val walk = if (scale != null && profile.id !in NO_WALK) {
            walkMask(reader, nFrames, times, finisher.timebase, scale, tuning)
        } else {
            BooleanArray(tb.n)
        }
        val barPoint = if (scale != null && profile.barPoint != BarPointRule.NONE) {
            barPointSeries(profile, usePlate, plate, lag, reader, prepared, nFrames, scale, finisher, tuning)
        } else {
            null
        }

        val series = CountSeries(
            t0Us = tb.t0Us,
            fs = tb.fs,
            r = main.r,
            filled = main.filled,
            weak = main.weak,
            walk = walk,
            barX = barPoint?.first,
            barY = barPoint?.second,
            perSide = perSide,
            source = if (usePlate) SOURCE_PLATE else SOURCE_POSE,
            unit = unit,
            validFraction = main.validFraction,
            workSide = workSide,
        )
        return CountSeriesResult.Ok(series, prepared)
    }

    // ------------------------------------------------------------------------------------------------------------
    // Unidades y requisitos de cada señal
    // ------------------------------------------------------------------------------------------------------------

    private fun unitOf(signal: CountSignal): SignalUnit = when (signal) {
        CountSignal.KNEE_FLEXION, CountSignal.KNEE_EXTENSION_ANGLE, CountSignal.HIP_FLEXION, CountSignal.HIP_ANGLE,
        CountSignal.ELBOW_FLEXION, CountSignal.ELBOW_ANGLE, CountSignal.ARM_ELEVATION,
        -> SignalUnit.DEGREES
        CountSignal.SHOULDER_TO_WRIST_CLOSE -> SignalUnit.RATIO
        else -> SignalUnit.BODY_HEIGHTS
    }

    /** Las señales de los puntos de un lado concreto: sin lado cercano no hay de dónde sacarlas. */
    private fun needsNearSide(signal: CountSignal): Boolean = when (signal) {
        CountSignal.DROP_HIP_MID, CountSignal.RISE_HIP_MID, CountSignal.RISE_TRUNK_MID, CountSignal.SHRUG_SIGNAL,
        CountSignal.PLATE_DROP, CountSignal.PLATE_RISE,
        -> false
        else -> true
    }

    private fun strongFraction(raw: RawSignal, withPose: Int): Double {
        var strong = 0
        for (i in raw.v.indices) if (!raw.v[i].isNaN() && !raw.weak[i]) strong++
        return if (withPose > 0) strong.toDouble() / withPose else 0.0
    }

    /** Amplitud robusta de una señal: P95 − P5 de las muestras válidas (0 si no hay). */
    private fun amplitude(r: DoubleArray): Double {
        val hi = RobustStats.percentile(r, 95.0)
        val lo = RobustStats.percentile(r, 5.0)
        return if (hi.isNaN() || lo.isNaN()) 0.0 else hi - lo
    }

    // ------------------------------------------------------------------------------------------------------------
    // Señal cruda por fotograma
    // ------------------------------------------------------------------------------------------------------------

    /** Valor por fotograma (NaN = falta) y si salió de algún punto débil. */
    private class RawSignal(n: Int) {
        val v = DoubleArray(n) { Double.NaN }
        val weak = BooleanArray(n)
    }

    /** Lee puntos de la geometría: fiables si puede y débiles si no, y recuerda si usó alguno débil. */
    private class PoseReader(val geo: FrameGeometry) {
        var weakUsed = false

        fun point(i: Int, lm: PoseLandmark): Vec2? {
            val strong = geo.point(i, lm, weak = false)
            if (strong != null) return strong
            val weak = geo.point(i, lm, weak = true)
            if (weak != null) weakUsed = true
            return weak
        }

        /** Solo puntos fiables (sin marcar nada). */
        fun strongPoint(i: Int, lm: PoseLandmark): Vec2? = geo.point(i, lm, weak = false)

        /**
         * Punto medio de un par izquierda/derecha. Si solo uno es fiable manda él (de lado, el lejano casi nunca llega a 0,5:
         * no puede marcar toda la serie como débil); sin ninguno fiable se usan los débiles.
         */
        fun mid(i: Int, left: PoseLandmark, right: PoseLandmark): Vec2? {
            val a = geo.point(i, left, weak = false)
            val b = geo.point(i, right, weak = false)
            if (a != null && b != null) return (a + b) * 0.5
            if (a != null) return a
            if (b != null) return b
            val aw = geo.point(i, left, weak = true)
            val bw = geo.point(i, right, weak = true)
            if (aw == null && bw == null) return null
            weakUsed = true
            return when {
                aw != null && bw != null -> (aw + bw) * 0.5
                aw != null -> aw
                else -> bw
            }
        }

        /** Centro de la mano; si la muñeca no es fiable, la muñeca débil. */
        fun hand(i: Int, side: Side): Vec2? {
            val hc = geo.handCenter(i, side)
            if (hc != null) return hc
            val weak = geo.point(i, side.wrist(), weak = true)
            if (weak != null) weakUsed = true
            return weak
        }
    }

    /** Las fórmulas de §5 sobre la geometría; las longitudes de referencia (L̂) se calculan una vez por lado. */
    private class SignalComputer(
        val reader: PoseReader,
        val geo: FrameGeometry,
        val nFrames: Int,
        val s: Double,
    ) {
        private val shoulderToWristRef = HashMap<Side, Double>()
        private val upperArmRef = HashMap<Side, Double>()

        fun raw(signal: CountSignal, side: Side): RawSignal {
            val out = RawSignal(nFrames)
            val lSw = if (signal == CountSignal.SHOULDER_TO_WRIST_CLOSE) segmentRef(side, shoulderToWristRef, side.shoulder(), side.wrist()) else 0.0
            val lArm = if (signal == CountSignal.ARM_ELEVATION) segmentRef(side, upperArmRef, side.shoulder(), side.elbow()) else 0.0
            for (i in 0 until nFrames) {
                if (!geo.hasPose(i)) continue
                reader.weakUsed = false
                val v = value(signal, side, i, lSw, lArm)
                if (v.isNaN()) continue
                out.v[i] = v
                out.weak[i] = reader.weakUsed
            }
            return out
        }

        /** Longitud de referencia L̂ = P90 de la distancia 2D entre [a] y [b] de [side]. NaN si no hay datos. */
        private fun segmentRef(side: Side, cache: HashMap<Side, Double>, a: PoseLandmark, b: PoseLandmark): Double =
            cache.getOrPut(side) {
                val strong = DoubleArray(nFrames) { geo.distance(it, a, b) }
                var l = RobustStats.percentile(strong, 90.0)
                if (l.isNaN()) {
                    val weak = DoubleArray(nFrames) { geo.distance(it, a, b, weak = true) }
                    l = RobustStats.percentile(weak, 90.0)
                }
                l
            }

        private fun angle(i: Int, a: PoseLandmark, b: PoseLandmark, c: PoseLandmark): Double {
            val pa = reader.point(i, a) ?: return Double.NaN
            val pb = reader.point(i, b) ?: return Double.NaN
            val pc = reader.point(i, c) ?: return Double.NaN
            return Angles.included(pa, pb, pc)
        }

        private fun value(signal: CountSignal, side: Side, i: Int, lSw: Double, lArm: Double): Double = when (signal) {
            CountSignal.DROP_HIP_MID -> (reader.mid(i, PoseLandmark.LEFT_HIP, PoseLandmark.RIGHT_HIP)?.y ?: Double.NaN) / s
            CountSignal.RISE_HIP_MID -> -(reader.mid(i, PoseLandmark.LEFT_HIP, PoseLandmark.RIGHT_HIP)?.y ?: Double.NaN) / s
            CountSignal.KNEE_FLEXION -> DEG_STRAIGHT - angle(i, side.hip(), side.knee(), side.ankle())
            CountSignal.KNEE_EXTENSION_ANGLE -> angle(i, side.hip(), side.knee(), side.ankle())
            CountSignal.HIP_FLEXION -> DEG_STRAIGHT - angle(i, side.shoulder(), side.hip(), side.knee())
            CountSignal.HIP_ANGLE -> angle(i, side.shoulder(), side.hip(), side.knee())
            CountSignal.RISE_SHOULDER_NEAR -> -(reader.point(i, side.shoulder())?.y ?: Double.NaN) / s
            CountSignal.DROP_SHOULDER_NEAR -> (reader.point(i, side.shoulder())?.y ?: Double.NaN) / s
            CountSignal.DROP_HAND_NEAR -> (reader.hand(i, side)?.y ?: Double.NaN) / s
            CountSignal.RISE_HAND -> -(reader.hand(i, side)?.y ?: Double.NaN) / s
            CountSignal.ELBOW_FLEXION -> DEG_STRAIGHT - angle(i, side.shoulder(), side.elbow(), side.wrist())
            CountSignal.ELBOW_ANGLE -> angle(i, side.shoulder(), side.elbow(), side.wrist())
            CountSignal.SHOULDER_TO_WRIST_CLOSE -> shoulderToWrist(i, side, lSw)
            CountSignal.ARM_ELEVATION -> armElevation(i, side, lArm)
            CountSignal.RISE_TRUNK_MID -> trunkMid(i)
            CountSignal.RISE_KNEE_NEAR -> -(reader.point(i, side.knee())?.y ?: Double.NaN) / s
            CountSignal.SHRUG_SIGNAL -> shrug(i)
            CountSignal.DROP_WRIST_NEAR -> (reader.point(i, side.wrist())?.y ?: Double.NaN) / s
            // El disco no sale de la pose: lo calcula el constructor con la traza del disco.
            CountSignal.PLATE_DROP, CountSignal.PLATE_RISE -> Double.NaN
        }

        /** 1 − d(S,W)/L̂ con L̂ = P90(d_SW) del propio lado. */
        private fun shoulderToWrist(i: Int, side: Side, lHat: Double): Double {
            if (!(lHat > 0.0)) return Double.NaN
            val ps = reader.point(i, side.shoulder()) ?: return Double.NaN
            val pw = reader.point(i, side.wrist()) ?: return Double.NaN
            return 1.0 - (ps - pw).norm() / lHat
        }

        /** β = acos(clamp((Y_E − Y_S)/L̂_brazo, −1, 1)) en grados: la componente vertical del brazo, no depende del giro. */
        private fun armElevation(i: Int, side: Side, lHat: Double): Double {
            if (!(lHat > 0.0)) return Double.NaN
            val ps = reader.point(i, side.shoulder()) ?: return Double.NaN
            val pe = reader.point(i, side.elbow()) ?: return Double.NaN
            return Math.toDegrees(acos(((pe.y - ps.y) / lHat).coerceIn(-1.0, 1.0)))
        }

        /** −Y(½(S_mid + H_mid))/S: sube con los gemelos (de pie). */
        private fun trunkMid(i: Int): Double {
            val sh = reader.mid(i, PoseLandmark.LEFT_SHOULDER, PoseLandmark.RIGHT_SHOULDER) ?: return Double.NaN
            val hp = reader.mid(i, PoseLandmark.LEFT_HIP, PoseLandmark.RIGHT_HIP) ?: return Double.NaN
            return -(0.5 * (sh.y + hp.y)) / s
        }

        /** (Y_oreja_media − Y_hombro_medio)/S: las orejas están por encima de los hombros, así que sube con el encogimiento. */
        private fun shrug(i: Int): Double {
            val ear = reader.mid(i, PoseLandmark.LEFT_EAR, PoseLandmark.RIGHT_EAR) ?: return Double.NaN
            val sh = reader.mid(i, PoseLandmark.LEFT_SHOULDER, PoseLandmark.RIGHT_SHOULDER) ?: return Double.NaN
            return (ear.y - sh.y) / s
        }
    }

    // ------------------------------------------------------------------------------------------------------------
    // Pasos comunes: atípicos, remuestreo, huecos
    // ------------------------------------------------------------------------------------------------------------

    /**
     * Una señal terminada sobre la rejilla. [observedCount] son las muestras medidas (ni rellenas ni débiles), [validCount]
     * las que tienen valor (medidas, débiles o rellenas) y [validFraction] la parte observada entre la primera y la última.
     */
    private class Finished(
        val r: DoubleArray,
        val filled: BooleanArray,
        val weak: BooleanArray,
        val validFraction: Double,
        val observedCount: Int,
        val validCount: Int,
    ) {
        companion object {
            /** Máximo muestra a muestra; si falta uno manda el otro. */
            fun maxOf(a: Finished, b: Finished): Finished {
                val n = a.r.size
                val r = DoubleArray(n) { Double.NaN }
                val filled = BooleanArray(n)
                val weak = BooleanArray(n)
                for (k in 0 until n) {
                    val x = a.r[k]
                    val y = b.r[k]
                    val fromA = when {
                        x.isNaN() && y.isNaN() -> continue
                        x.isNaN() -> false
                        y.isNaN() -> true
                        else -> x >= y
                    }
                    val src = if (fromA) a else b
                    r[k] = src.r[k]
                    filled[k] = src.filled[k]
                    weak[k] = src.weak[k]
                }
                return summarize(r, filled, weak)
            }

            /**
             * Fracción observada entre la primera y la última muestra válida. Con [weakCounts] las débiles cuentan como
             * observadas: la señal del lado lejano que trabaja es débil por definición y su cautela ya va en el tope de
             * confianza (FAR_SIDE_WORKING), no en la fracción válida.
             */
            fun summarize(r: DoubleArray, filled: BooleanArray, weak: BooleanArray, weakCounts: Boolean = false): Finished {
                var first = -1
                var last = -1
                var observed = 0
                var valid = 0
                for (k in r.indices) {
                    if (r[k].isNaN()) continue
                    valid++
                    if (first < 0) first = k
                    last = k
                    if (!filled[k] && (weakCounts || !weak[k])) observed++
                }
                val span = if (first < 0) 0 else last - first + 1
                return Finished(r, filled, weak, if (span > 0) observed.toDouble() / span else 0.0, observed, valid)
            }
        }
    }

    private class Finisher(
        val times: LongArray,
        val fs: Double,
        val t0Us: Long,
        val n: Int,
        val tuning: AnalysisTuning,
        val limitJumps: Boolean,
    ) {
        val timebase = Timebase(t0Us, fs, n, false, 1.0)

        /** Hampel y saltos sobre los fotogramas, remuestreo a la rejilla y relleno de huecos de hasta 0,6 s. */
        fun finish(raw: RawSignal): Finished {
            val half = max(tuning.hampelMinHalfWindow, (tuning.hampelWindowFraction * fs).roundToInt())
            val clean = Hampel.filter(raw.v, half, tuning.hampelSigmas)
            if (limitJumps) dropJumps(clean)
            val weakNum = DoubleArray(clean.size) { if (clean[it].isNaN()) Double.NaN else if (raw.weak[it]) 1.0 else 0.0 }
            val grid = Resampler.toUniform(times, clean, timebase, tuning.fillCountGapS)
            val weakGrid = Resampler.toUniform(times, weakNum, timebase, tuning.fillCountGapS)
            val fill = GapFiller.fill(grid, fs, tuning.fillCountGapS)
            val weak = BooleanArray(grid.size)
            for (k in grid.indices) weak[k] = !grid[k].isNaN() && !weakGrid[k].isNaN() && weakGrid[k] > 0.0
            return Finished.summarize(fill.values, fill.filled, weak)
        }

        /** Remuestrea, filtra atípicos y rellena huecos de una coordenada auxiliar (punto de barra). */
        fun axis(raw: DoubleArray): DoubleArray {
            val half = max(tuning.hampelMinHalfWindow, (tuning.hampelWindowFraction * fs).roundToInt())
            val clean = Hampel.filter(raw, half, tuning.hampelSigmas)
            val grid = Resampler.toUniform(times, clean, timebase, tuning.fillCountGapS)
            return GapFiller.fill(grid, fs, tuning.fillCountGapS).values
        }

        /** Salto: |Δr| > 4,5 BH/s · Δt entre dos fotogramas con dato deja la muestra como falta. */
        private fun dropJumps(v: DoubleArray) {
            var prev = Double.NaN
            var prevT = 0L
            for (i in v.indices) {
                val x = v[i]
                if (x.isNaN()) continue
                if (!prev.isNaN()) {
                    val dt = (times[i] - prevT) / MICROS
                    if (dt > 0.0 && abs(x - prev) > tuning.jumpBodyHeightsPerS * dt) {
                        v[i] = Double.NaN
                        continue
                    }
                }
                prev = x
                prevT = times[i]
            }
        }
    }

    private const val MICROS = 1_000_000.0

    // ------------------------------------------------------------------------------------------------------------
    // Disco
    // ------------------------------------------------------------------------------------------------------------

    /**
     * La traza del disco llevada a los fotogramas de la pose: Y (FH, con el roll corregido) y X por fotograma, si la muestra
     * sale de un detector (HOUGH o NCC) o de un respaldo (POSE_PRIOR o PREDICTED: débil), y la cobertura HOUGH + NCC.
     */
    private class PlateFrames(val x: DoubleArray, val y: DoubleArray, val weak: BooleanArray, val coverage: Double) {
        companion object {
            fun of(bar: BarTrack, geo: FrameGeometry, times: LongArray, tuning: AnalysisTuning): PlateFrames {
                val n = times.size
                val x = DoubleArray(n) { Double.NaN }
                val y = DoubleArray(n) { Double.NaN }
                val weak = BooleanArray(n)
                val h = bar.frameHeight.toDouble()
                val samples = bar.samples.sortedBy { it.tUs }
                if (h <= 0.0 || samples.isEmpty()) return PlateFrames(x, y, weak, 0.0)
                val fs = if (n > 1) (n - 1) * MICROS / max(1L, times[n - 1] - times[0]) else 30.0
                val tolUs = 0.6 * MICROS / fs
                val rot = Rotation(geo)
                var withPose = 0
                var detected = 0
                var j = 0
                for (i in 0 until n) {
                    val hasPose = geo.hasPose(i)
                    if (hasPose) withPose++
                    val t = times[i]
                    while (j + 1 < samples.size && abs(samples[j + 1].tUs - t) <= abs(samples[j].tUs - t)) j++
                    val smp = samples[j]
                    if (abs(smp.tUs - t) > tolUs) continue
                    val strong = smp.source == BarSampleSource.HOUGH || smp.source == BarSampleSource.NCC
                    if (hasPose && strong) detected++
                    x[i] = rot.x(smp.cx / h, smp.cy / h)
                    y[i] = rot.y(smp.cx / h, smp.cy / h)
                    weak[i] = !strong
                }
                val coverage = if (withPose > 0) detected.toDouble() / withPose else 0.0
                return PlateFrames(x, y, weak, coverage)
            }
        }
    }

    /** El mismo giro −roll del centro del fotograma que aplica `FrameGeometry`, sobre puntos que ya están en FH. */
    private class Rotation(geo: FrameGeometry) {
        private val rolled = abs(geo.rollDeg) > 1e-9
        private val cosR = cos(Math.toRadians(-geo.rollDeg))
        private val sinR = sin(Math.toRadians(-geo.rollDeg))
        private val cx = geo.aspect / 2.0

        fun x(xFh: Double, yFh: Double): Double = if (!rolled) xFh else cx + (xFh - cx) * cosR - (yFh - 0.5) * sinR

        fun y(xFh: Double, yFh: Double): Double = if (!rolled) yFh else 0.5 + (xFh - cx) * sinR + (yFh - 0.5) * cosR
    }

    /**
     * Desfase del disco respecto de la pose, en fotogramas (−3 a +3): el que maximiza la correlación de la Y de la mano
     * cercana con la Y del disco desplazada. 0 si no hay mano, hay pocos pares o el desfase no mejora claramente.
     */
    private fun plateLag(plate: PlateFrames, reader: PoseReader, prepared: PreparedTrack, nFrames: Int, tuning: AnalysisTuning): Int {
        val near = prepared.near?.near ?: return 0
        val hand = DoubleArray(nFrames) { Double.NaN }
        var count = 0
        for (i in 0 until nFrames) {
            val hc = reader.geo.handCenter(i, near) ?: continue
            hand[i] = hc.y
            count++
        }
        if (count < tuning.plateLagMinPairs) return 0
        val maxLag = tuning.plateMaxLagFrames
        var best = 0
        var bestCorr = correlation(hand, plate.y, 0, tuning.plateLagMinPairs)
        val zeroCorr = bestCorr
        for (lag in -maxLag..maxLag) {
            if (lag == 0) continue
            val c = correlation(hand, plate.y, lag, tuning.plateLagMinPairs)
            if (!c.isNaN() && (bestCorr.isNaN() || c > bestCorr)) {
                bestCorr = c
                best = lag
            }
        }
        // El desfase solo se corrige si la decorrelación (1 − ρ) baja de verdad: con una señal lenta, ρ casi no se mueve.
        if (best != 0 && (zeroCorr.isNaN() || (1.0 - bestCorr) <= (1.0 - tuning.plateLagMinGain) * (1.0 - zeroCorr))) return best
        return 0
    }

    /** Correlación de Pearson de a[k] con b[k + lag] sobre los pares válidos; NaN con menos de [minPairs]. */
    private fun correlation(a: DoubleArray, b: DoubleArray, lag: Int, minPairs: Int): Double {
        var n = 0
        var sa = 0.0
        var sb = 0.0
        for (k in a.indices) {
            val j = k + lag
            if (j < 0 || j >= b.size) continue
            val x = a[k]
            val y = b[j]
            if (x.isNaN() || y.isNaN()) continue
            n++
            sa += x
            sb += y
        }
        if (n < minPairs) return Double.NaN
        val ma = sa / n
        val mb = sb / n
        var saa = 0.0
        var sbb = 0.0
        var sab = 0.0
        for (k in a.indices) {
            val j = k + lag
            if (j < 0 || j >= b.size) continue
            val x = a[k]
            val y = b[j]
            if (x.isNaN() || y.isNaN()) continue
            saa += (x - ma) * (x - ma)
            sbb += (y - mb) * (y - mb)
            sab += (x - ma) * (y - mb)
        }
        val d = sqrt(saa * sbb)
        return if (d > 0.0) sab / d else Double.NaN
    }

    // ------------------------------------------------------------------------------------------------------------
    // Caminar y punto de barra
    // ------------------------------------------------------------------------------------------------------------

    /**
     * Máscara de caminar: la X de los tobillos (media de los fiables) y la de las caderas, en BH, sin atípicos, suavizadas a
     * 1 Hz y derivadas; camina cuando LAS DOS se mueven a más de 0,12 BH/s durante ≥ 0,3 s (dilatado ±0,3 s). Con solo los
     * tobillos, la extensión de cuádriceps o el curl femoral (el pie se mueve, el cuerpo no) contarían como caminar.
     */
    private fun walkMask(
        reader: PoseReader,
        nFrames: Int,
        times: LongArray,
        timebase: Timebase,
        s: Double,
        tuning: AnalysisTuning,
    ): BooleanArray {
        val n = timebase.n
        val fs = timebase.fs
        val half = max(tuning.hampelMinHalfWindow, (tuning.hampelWindowFraction * fs).roundToInt())
        val ax = DoubleArray(nFrames) { Double.NaN }
        val hx = DoubleArray(nFrames) { Double.NaN }
        for (i in 0 until nFrames) {
            val l = reader.strongPoint(i, PoseLandmark.LEFT_ANKLE)
            val r = reader.strongPoint(i, PoseLandmark.RIGHT_ANKLE)
            ax[i] = when {
                l != null && r != null -> (l.x + r.x) * 0.5 / s
                l != null -> l.x / s
                r != null -> r.x / s
                else -> Double.NaN
            }
            hx[i] = (reader.mid(i, PoseLandmark.LEFT_HIP, PoseLandmark.RIGHT_HIP)?.x ?: Double.NaN) / s
        }
        val ankleSpeed = horizontalSpeed(ax, times, timebase, half, tuning)
        val hipSpeed = horizontalSpeed(hx, times, timebase, half, tuning)
        val moving = BooleanArray(n) { abs(ankleSpeed[it]) > tuning.walkSpeedBhPerS && abs(hipSpeed[it]) > tuning.walkSpeedBhPerS }
        val mask = BooleanArray(n)
        val minRun = max(1, (tuning.walkMinS * fs).roundToInt())
        val dilate = (tuning.walkDilateS * fs).roundToInt()
        var k = 0
        while (k < n) {
            if (!moving[k]) {
                k++
                continue
            }
            var end = k
            while (end < n && moving[end]) end++
            if (end - k >= minRun) {
                for (m in max(0, k - dilate) until minOf(n, end + dilate)) mask[m] = true
            }
            k = end
        }
        return mask
    }

    /** Velocidad horizontal (BH/s) de una coordenada por fotograma: sin atípicos, en la rejilla, suavizada y derivada. */
    private fun horizontalSpeed(x: DoubleArray, times: LongArray, timebase: Timebase, half: Int, tuning: AnalysisTuning): DoubleArray {
        val clean = Hampel.filter(x, half, tuning.hampelSigmas)
        val grid = GapFiller.fill(Resampler.toUniform(times, clean, timebase, tuning.fillCountGapS), timebase.fs, tuning.fillCountGapS).values
        return Derivative.central(ZeroPhaseLowPass.filter(grid, timebase.fs, tuning.walkLowPassHz), timebase.fs)
    }

    /** El punto de barra del perfil en BH: el disco si cuenta, o el sustituto de pose de la regla. */
    private fun barPointSeries(
        profile: AnalysisProfile,
        usePlate: Boolean,
        plate: PlateFrames?,
        lag: Int,
        reader: PoseReader,
        prepared: PreparedTrack,
        nFrames: Int,
        s: Double,
        finisher: Finisher,
        tuning: AnalysisTuning,
    ): Pair<DoubleArray, DoubleArray>? {
        val xs = DoubleArray(nFrames) { Double.NaN }
        val ys = DoubleArray(nFrames) { Double.NaN }
        if (usePlate && plate != null) {
            for (i in 0 until nFrames) {
                val k = i + lag
                if (k < 0 || k >= nFrames) continue
                xs[i] = plate.x[k] / s
                ys[i] = plate.y[k] / s
            }
        } else {
            val near = prepared.near?.near ?: return null
            for (i in 0 until nFrames) {
                if (!reader.geo.hasPose(i)) continue
                val p = poseBarPoint(profile, near, reader, i, tuning) ?: continue
                xs[i] = p.x / s
                ys[i] = p.y / s
            }
        }
        return Pair(finisher.axis(xs), finisher.axis(ys))
    }

    /** Sustituto de pose del punto de barra: `BarPointRule` sin el disco. */
    private fun poseBarPoint(profile: AnalysisProfile, near: Side, reader: PoseReader, i: Int, tuning: AnalysisTuning): Vec2? =
        when (profile.barPoint) {
            BarPointRule.PLATE_THEN_SHOULDER -> reader.point(i, near.shoulder())
            BarPointRule.PLATE_THEN_HAND ->
                if (profile.id == AnalysisProfileId.DEADLIFT) {
                    // Peso muerto: la mano solo si la muñeca se ve bien (el disco la tapa); si no, el hombro.
                    val vis = reader.geo.track.frames[i].vis(near.wrist())
                    if (vis >= tuning.deadliftHandVisibility) reader.hand(i, near) else reader.point(i, near.shoulder())
                } else {
                    reader.hand(i, near) ?: reader.point(i, near.elbow())
                }
            BarPointRule.PLATE_THEN_HIP -> reader.point(i, near.hip())
            BarPointRule.HAND -> reader.hand(i, near)
            BarPointRule.SHOULDER_MID -> reader.mid(i, PoseLandmark.LEFT_SHOULDER, PoseLandmark.RIGHT_SHOULDER)
            BarPointRule.NONE -> null
        }
}
