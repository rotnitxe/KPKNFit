package com.example.kpkn.domain.biomechanics.reps

import com.example.kpkn.domain.biomechanics.kinematics.Side
import com.example.kpkn.domain.biomechanics.metrics.AnalysisTuning
import com.example.kpkn.domain.biomechanics.metrics.RepKind
import com.example.kpkn.domain.biomechanics.metrics.TempoSummary
import com.example.kpkn.domain.biomechanics.profiles.AnalysisProfile
import com.example.kpkn.domain.biomechanics.profiles.StartPhase
import com.example.kpkn.domain.biomechanics.signal.RobustStats
import com.example.kpkn.domain.biomechanics.signal.ZeroPhaseLowPass
import kotlin.math.max
import kotlin.math.roundToInt
import kotlin.math.roundToLong

/**
 * Tiempos de una repetición (§6, «Fases, TUT y tempo»). Los instantes son µs sobre la misma base que `PoseFrameV2.tUs`.
 *
 * - [departUs]: sale del reposo; [turnStartUs]: llega al giro (extremo lejano); [turnEndUs]: sale del giro; [arriveUs]:
 *   vuelve al reposo. Sin pausa de giro (< 0,25 s) [turnStartUs] = [turnEndUs].
 * - Con arranque E la subida de r es la excéntrica y la bajada la concéntrica; con arranque C, al revés.
 * - [turnPauseMs]: pausa en el giro; [restPauseMs]: pausa en la posición de reposo que acompaña a esta repetición (la de
 *   DESPUÉS con arranque E, la de ANTES con arranque C). Las pausas de menos de 0,25 s valen 0. `null` = no hay dato: la
 *   última repetición con arranque E y la primera con arranque C no tienen pausa de reposo propia, ni hay entre mini-series.
 */
data class RepPhaseResult(
    val cycleIndex: Int,
    val departUs: Long,
    val turnStartUs: Long,
    val turnEndUs: Long,
    val arriveUs: Long,
    val eccentricMs: Int?,
    val turnPauseMs: Int?,
    val concentricMs: Int?,
    val restPauseMs: Int?,
)

/**
 * Las fases de la serie. [tutMs] suma EXC + CON + pausa de giro de las FULL, PARTIAL y FAILED, sin el reposo en la posición
 * inicial, sin los descansos entre mini-series y sin la preparación, el unrack ni el re-rack. [setDurationMs] va de la
 * salida de la primera repetición a la llegada de la última; [restPositionMs] es el reposo entre repeticiones de una misma
 * mini-serie. [tempo] es la mediana de las FULL. Con fs < 20 no hay fases (todo vacío).
 */
data class PhaseSummary(
    val reps: List<RepPhaseResult>,
    val tutMs: Long?,
    val setDurationMs: Long?,
    val restPositionMs: Long?,
    val tempo: TempoSummary?,
)

/**
 * Fases, TUT y tempo de las repeticiones de una serie segmentada (§6).
 *
 * La señal es r_m = LP(r, 5 Hz), o la del disco a 8 Hz si fs ≥ 50. Cada repetición tiene un tramo de subida (del reposo
 * al giro) y uno de bajada (del giro al reposo), ajustados con [MovementPieces]; sus extremos dan la salida, la llegada al
 * giro, la salida del giro y la llegada. Una pausa de giro de menos de 0,25 s no existe: el giro es un solo instante.
 */
object RepPhases {
    fun compute(
        series: CountSeries,
        seg: SegmentationResult,
        profile: AnalysisProfile,
        tuning: AnalysisTuning = AnalysisTuning.DEFAULT,
    ): PhaseSummary {
        val empty = PhaseSummary(emptyList(), null, null, null, null)
        if (!series.fs.isFinite() || series.fs < tuning.lowFpsBelow || series.size < 2 ||
            series.t0Us < 0L || seg.cycles.isEmpty()) return empty

        val fs = series.fs
        fun us(index: Double): Long = series.t0Us + (index / fs * MICROS).roundToLong()
        val fc = if (series.source == CountSeriesBuilder.SOURCE_PLATE && fs >= tuning.plateFastMinFps) {
            tuning.plateLowPassHzFast
        } else {
            tuning.phaseLowPassHz
        }
        val smoothed = HashMap<Side?, DoubleArray>()
        fun signalOf(side: Side?): DoubleArray = smoothed.getOrPut(side) {
            val raw = if (side != null) series.perSide?.get(side) ?: series.r else series.r
            ZeroPhaseLowPass.filter(raw, fs, fc)
        }

        val eccentricFirst = profile.startPhase == StartPhase.ECCENTRIC_FIRST
        val halfWin = max(1, (tuning.pieceLevelWindowS * fs).roundToInt())
        val pauseMin = tuning.turnPauseMinS * fs

        // Eventos de cada repetición, en índices fraccionarios.
        class Events(
            val cycle: RepCycle,
            val depart: Double,
            val turnStart: Double,
            val turnEnd: Double,
            val arrive: Double,
            val hasReturn: Boolean,
        )

        val events = ArrayList<Events>(seg.cycles.size)
        for (c in seg.cycles) {
            val x = signalOf(c.side)
            // The sampled signal may begin after the clip origin or end before its duration.
            // Check its observed envelope in the same published microsecond clock, not at zero.
            val firstObserved = x.indexOfFirst { it.isFinite() }
            val lastObserved = x.indexOfLast { it.isFinite() }
            if (firstObserved < 0 || lastObserved <= firstObserved) return empty
            val firstUs = us(firstObserved.toDouble())
            val lastUs = us(lastObserved.toDouble())
            fun supported(index: Double): Boolean = index.isFinite() &&
                us(index) in firstUs..lastUs
            val peak = c.peak
            val vl = c.leftValley
            val vr = c.rightValley ?: -1
            val pieces = MovementPieces.cycle(x, if (vl < peak) vl else -1, peak, vr, halfWin)
            val rise = pieces.rise
            val fall = pieces.fall
            // Both the refined fit and the original crossing fallback extrapolate. Neither
            // may publish unseen motion. Check before merging the turn can hide an endpoint.
            if (listOfNotNull(rise, fall).any { !supported(it.start) || !supported(it.end) }) return empty
            val depart = rise?.start ?: (if (vl in 0 until peak) vl.toDouble() else peak.toDouble())
            val riseEnd = rise?.end ?: peak.toDouble()
            val fallStart = fall?.start ?: peak.toDouble()
            val arrive = fall?.end ?: (if (vr > peak) vr.toDouble() else max(riseEnd, fallStart))
            val turnStart: Double
            val turnEnd: Double
            if (fallStart - riseEnd >= pauseMin) {
                turnStart = riseEnd
                turnEnd = fallStart
            } else {
                val t = 0.5 * (riseEnd + fallStart)
                turnStart = t
                turnEnd = t
            }
            // Do not turn incompatible movement fits into a measured return by clamping arrive
            // to the merged turn. A partial set summary would also misstate whole-set TUT/tempo.
            if (!supported(depart) || !supported(turnStart) || !supported(turnEnd) || !supported(arrive) ||
                turnStart < depart || turnEnd < turnStart || arrive < turnEnd ||
                (rise != null && fall != null && (turnStart <= depart || arrive <= turnEnd))) return empty
            events.add(Events(c, depart, turnStart, turnEnd, arrive, fall != null))
        }

        fun ms(samples: Double): Int = max(0, (samples / fs * 1000.0).roundToInt())
        fun pauseMs(samples: Double): Int = if (samples >= pauseMin) ms(samples) else 0

        // Independent fits must describe a compatible chronology for the same body/limb.
        // Compare the published microsecond clock, avoiding arbitrary sub-microsecond tolerances.
        // A null side is a whole-body interval and conflicts with either named limb. Distinct
        // limbs may move concurrently. Mini-set labels do not make overlapping time support valid.
        val previousBySide = HashMap<Side?, Events>()
        for (event in events) {
            val side = event.cycle.side
            val previous = if (side == null) previousBySide.values else
                listOfNotNull(previousBySide[side], previousBySide[null])
            if (previous.any { us(event.depart) < us(it.arrive) }) return empty
            previousBySide[side] = event
        }

        val reps = ArrayList<RepPhaseResult>(events.size)
        var tut = 0.0
        val restPauses = ArrayList<Double>()
        for ((k, e) in events.withIndex()) {
            val rise = e.turnStart - e.depart
            val fallT = e.arrive - e.turnEnd
            val turnPause = e.turnEnd - e.turnStart
            // Pausa de reposo propia: la siguiente con arranque E (tras la llegada), la anterior con arranque C (antes de salir).
            val rest: Double? = if (eccentricFirst) {
                events.getOrNull(k + 1)?.takeIf { it.cycle.miniSet == e.cycle.miniSet }?.let { it.depart - e.arrive }
            } else {
                events.getOrNull(k - 1)?.takeIf { it.cycle.miniSet == e.cycle.miniSet }?.let { e.depart - it.arrive }
            }
            val eccentricSamples = if (eccentricFirst) rise else fallT
            val concentricSamples = if (eccentricFirst) fallT else rise
            val hasFall = e.hasReturn
            val ecc = if (eccentricFirst || hasFall) ms(eccentricSamples) else null
            val con = if (!eccentricFirst || hasFall) ms(concentricSamples) else null
            val turnPauseMs = pauseMs(turnPause)
            val restMs = rest?.let { pauseMs(it) }
            if (rest != null && rest >= pauseMin) restPauses.add(rest)
            tut += (ecc ?: 0) + (con ?: 0) + turnPauseMs
            reps.add(
                RepPhaseResult(
                    cycleIndex = e.cycle.index,
                    departUs = us(e.depart),
                    turnStartUs = us(e.turnStart),
                    turnEndUs = us(e.turnEnd),
                    arriveUs = us(e.arrive),
                    eccentricMs = ecc,
                    turnPauseMs = turnPauseMs,
                    concentricMs = con,
                    restPauseMs = restMs,
                ),
            )
        }

        val firstDepart = events.first().depart
        val lastArrive = events.last().arrive
        val setDuration = ms(lastArrive - firstDepart).toLong()
        val restPosition = restPauses.sum().let { ms(it).toLong() }

        // Tempo: mediana de las FULL. P2 de la última con arranque E y P1 de la primera con arranque C son `null` y quedan fuera.
        val fullReps = reps.filter { r -> seg.cycles.first { it.index == r.cycleIndex }.kind == RepKind.FULL }
        val tempo = if (fullReps.isEmpty()) {
            null
        } else {
            val ecc = medianInt(fullReps.map { it.eccentricMs })
            val con = medianInt(fullReps.map { it.concentricMs })
            val turn = medianInt(fullReps.map { it.turnPauseMs })
            val rest = medianInt(fullReps.map { it.restPauseMs })
            if (eccentricFirst) TempoSummary(ecc, turn, con, rest) else TempoSummary(ecc, rest, con, turn)
        }
        return PhaseSummary(reps, tut.roundToLong(), setDuration, restPosition, tempo)
    }

    /** Mediana de los valores presentes; `null` si no hay ninguno. */
    private fun medianInt(values: List<Int?>): Int? {
        val present = values.filterNotNull()
        if (present.isEmpty()) return null
        val m = RobustStats.median(DoubleArray(present.size) { present[it].toDouble() })
        return m.roundToInt()
    }

    private const val MICROS = 1_000_000.0
}
