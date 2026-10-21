package com.example.kpkn.domain.biomechanics.metrics

import com.example.kpkn.domain.biomechanics.kinematics.Angles
import com.example.kpkn.domain.biomechanics.kinematics.FrameGeometry
import com.example.kpkn.domain.biomechanics.kinematics.Side
import com.example.kpkn.domain.biomechanics.kinematics.ViewKind
import com.example.kpkn.domain.biomechanics.kinematics.ankle
import com.example.kpkn.domain.biomechanics.kinematics.elbow
import com.example.kpkn.domain.biomechanics.kinematics.heel
import com.example.kpkn.domain.biomechanics.kinematics.hip
import com.example.kpkn.domain.biomechanics.kinematics.knee
import com.example.kpkn.domain.biomechanics.kinematics.shoulder
import com.example.kpkn.domain.biomechanics.kinematics.toe
import com.example.kpkn.domain.biomechanics.pose.PoseLandmark
import com.example.kpkn.domain.biomechanics.profiles.AnalysisProfile
import com.example.kpkn.domain.biomechanics.profiles.AnalysisProfileId
import com.example.kpkn.domain.biomechanics.profiles.RomAngle
import com.example.kpkn.domain.biomechanics.profiles.StartPhase
import com.example.kpkn.domain.biomechanics.reps.CountSeriesBuilder
import com.example.kpkn.domain.biomechanics.reps.PreparedTrack
import com.example.kpkn.domain.biomechanics.signal.RobustStats
import kotlin.math.abs
import kotlin.math.ceil
import kotlin.math.floor
import kotlin.math.max
import kotlin.math.min
import kotlin.math.roundToInt

/**
 * Una observación que una regla ha encontrado, antes de ordenar y recortar. [magnitude] es su tamaño en las unidades de la regla
 * (grados, cm, repeticiones…); [strength] ≥ 1 dice cuántas veces el umbral (o la escala de la regla) se pasa; [weight] < 1 baja
 * las reglas que solo describen.
 */
internal class Candidate(
    val code: String,
    val text: String,
    val repIndices: List<Int>,
    val atUs: Long?,
    val magnitude: Double,
    val strength: Double,
    val weight: Double = 1.0,
) {
    /** Magnitud × número de repeticiones × confianza (§7): [strength] ya es la magnitud en unidades del umbral. */
    fun rank(confidence: Double): Double = strength * repIndices.size * confidence * weight
}

/**
 * Las observaciones de técnica de una serie (§7, «Observaciones»): hechos con su cifra y su repetición, como mucho 3, ordenados por
 * magnitud × número de repeticiones × confianza. Cada regla comprueba sus requisitos (la vista, la visibilidad, el disco, los
 * fotogramas por segundo) y **calla** si le falta alguno: sin dato no hay observación.
 *
 * - Las reglas de la tabla de §7: sentadilla, peso muerto, rumano/buenos días, hip thrust, banca, flexiones, press militar,
 *   dominada/jalón, remo, curl/tríceps y elevaciones; más dos comunes: la tendencia del ROM y el cambio de inclinación del tronco
 *   entre la primera y la última repetición.
 * - **No hay observaciones con la confianza del conteo BAJA** («Vídeo no concluyente»): los números de repetición no valen.
 * - **No entra en v1** (callan): la convergencia de rodillas de la sentadilla y la asimetría de las elevaciones (SYMMETRY, de
 *   frente, nivel C: no se calcula); y «flexión de rodilla antes de subir» del press militar, que exige saber que el press es
 *   estricto (llega por `hints` con `STRICT_PRESS`, que aún no manda nadie).
 * - **Nunca se emite** (§7): rodillas por delante de la punta, flexión lumbar o espalda, cabeza, cuello o mirada, valgo, agarre,
 *   codos abiertos, arco, barra alta o baja, ancho o ángulo de pies como errores, «válido/nulo», m/s o 1RM sin calibrar, RIR o RPE
 *   por pérdida de velocidad, ni diagnósticos de movilidad. Los textos son de [ObservationText].
 *
 * @param ctx el contexto de las métricas (serie de conteo, barra y fases) que arma el motor; sin él callan las reglas que miden
 *   la barra: cadera antes que la barra, rebote, y dominada frente a jalón.
 * @param path la trayectoria de la barra (en cm con el disco calibrado); sin ella callan las reglas de trayectoria que la usan.
 */
object Observations {
    fun evaluate(
        report: SetTechniqueReport,
        prepared: PreparedTrack,
        profile: AnalysisProfile,
        scaleInfo: ScaleInfo,
        tuning: AnalysisTuning = AnalysisTuning.DEFAULT,
        ctx: MetricContext? = null,
        path: PathSummary? = null,
        hints: Set<String> = emptySet(),
    ): List<TechniqueObservation> {
        if (report.reps.isEmpty() || report.confidence == CountConfidence.BAJA) return emptyList()
        val t = tuning.observations
        val confidence = if (report.confidence == CountConfidence.ALTA) t.confidenceAlta else t.confidenceMedia
        val found = candidates(report, prepared, profile, scaleInfo, tuning, ctx, path, hints)
        if (found.isEmpty()) return emptyList()
        val ranked = found.map { it to it.rank(confidence) }
            .sortedWith(compareByDescending<Pair<Candidate, Double>> { it.second }.thenBy { it.first.code })
        val seen = HashSet<String>()
        val out = ArrayList<TechniqueObservation>()
        for ((c, rank) in ranked) {
            if (!seen.add(c.code)) continue
            out.add(TechniqueObservation(c.code, c.text, c.repIndices, c.atUs, rounded(c.magnitude, 2), rounded(rank, 2)))
            if (out.size >= t.maxObservations) break
        }
        return out
    }

    /** Todas las observaciones que disparan, sin ordenar ni recortar (lo usan [evaluate] y las pruebas). */
    internal fun candidates(
        report: SetTechniqueReport,
        prepared: PreparedTrack,
        profile: AnalysisProfile,
        scaleInfo: ScaleInfo,
        tuning: AnalysisTuning = AnalysisTuning.DEFAULT,
        ctx: MetricContext? = null,
        path: PathSummary? = null,
        hints: Set<String> = emptySet(),
    ): List<Candidate> {
        val env = ObsEnv(report, prepared, profile, scaleInfo, tuning, ctx, path, hints)
        val out = ArrayList<Candidate>()
        env.romTrend(out)
        when (profile.id) {
            AnalysisProfileId.SQUAT -> {
                env.trunkChange(out)
                env.squat(out)
            }
            AnalysisProfileId.DEADLIFT -> {
                env.trunkChange(out)
                env.deadlift(out)
            }
            AnalysisProfileId.HINGE -> {
                env.trunkChange(out)
                env.hinge(out)
            }
            AnalysisProfileId.HIP_THRUST -> env.hipThrust(out)
            AnalysisProfileId.BENCH -> env.bench(out)
            AnalysisProfileId.PUSH_UP -> env.pushUp(out)
            AnalysisProfileId.OHP -> env.ohp(out)
            AnalysisProfileId.PULL_VERTICAL -> env.pullVertical(out)
            AnalysisProfileId.ROW -> env.row(out)
            AnalysisProfileId.CURL, AnalysisProfileId.TRICEPS_EXTENDED_REST, AnalysisProfileId.TRICEPS_FLEXED_REST -> env.arms(out)
            AnalysisProfileId.RAISE -> env.raise(out)
            else -> Unit
        }
        return out
    }

    private fun rounded(x: Double, decimals: Int): Double {
        val scale = Math.pow(10.0, decimals.toDouble())
        return Math.round(x * scale) / scale
    }
}

/**
 * Todo lo que las reglas necesitan: el informe, la traza preparada y, perezosos y con caché, los ángulos y las posiciones de los
 * puntos como series sobre la rejilla de la traza (a 5 Hz de corte, igual que las métricas).
 */
internal class ObsEnv(
    val report: SetTechniqueReport,
    val prepared: PreparedTrack,
    val profile: AnalysisProfile,
    val scale: ScaleInfo,
    val tuning: AnalysisTuning,
    val ctx: MetricContext?,
    val path: PathSummary?,
    val hints: Set<String>,
) {
    val t: ObservationsTuning = tuning.observations
    val m: MetricsTuning = tuning.metrics
    val reps: List<RepReport> = report.reps
    val full: List<RepReport> = reps.filter { it.kind == RepKind.FULL }
    val near: Side? = prepared.near?.near
    val geo: FrameGeometry = prepared.geo
    val view: ViewKind = report.view
    val eccentricFirst: Boolean = profile.startPhase == StartPhase.ECCENTRIC_FIRST
    val bodyHeightFh: Double? = prepared.scale?.bodyHeightFh

    /** Cifras de trayectoria en cm: disco calibrado y trayectoria en la unidad de centímetros. */
    val centimetres: Boolean = scale.source == ScaleSource.PLATE && report.barDeviationUnit == TechniqueAnalysisEngine.PATH_CM

    private val frames = prepared.track.frames
    private val cache = HashMap<String, MetricSeries?>()

    // ------------------------------------------------------------------------------------------------------------
    // Tiempo y repeticiones
    // ------------------------------------------------------------------------------------------------------------

    /** La concéntrica de [rep] en µs `[desde, hasta]`, según las fases del informe; `null` sin fases o sin tramo. */
    fun conSpan(rep: RepReport): LongArray? {
        if (rep.concentricMs == null) return null
        val p = rep.phases
        val span = if (eccentricFirst) longArrayOf(p.turnEndUs, p.arriveUs) else longArrayOf(p.departUs, p.turnStartUs)
        return if (span[1] > span[0]) span else null
    }

    /** La excéntrica de [rep]: lo contrario de [conSpan]. */
    fun eccSpan(rep: RepReport): LongArray? {
        if (rep.eccentricMs == null) return null
        val p = rep.phases
        val span = if (eccentricFirst) longArrayOf(p.departUs, p.turnStartUs) else longArrayOf(p.turnEndUs, p.arriveUs)
        return if (span[1] > span[0]) span else null
    }

    /** El instante medio de la concéntrica de [rep], o su giro si no tiene fases. */
    fun midCon(rep: RepReport): Long {
        val c = conSpan(rep) ?: return rep.phases.turnStartUs
        return (c[0] + c[1]) / 2
    }

    /** La posición de [rep] en `report.reps` (la de `PathSummary.reps`). */
    fun repPosition(rep: RepReport): Int = reps.indexOf(rep)

    // ------------------------------------------------------------------------------------------------------------
    // Series
    // ------------------------------------------------------------------------------------------------------------

    private fun cached(key: String, build: () -> MetricSeries?): MetricSeries? {
        if (cache.containsKey(key)) return cache[key]
        val s = build()
        cache[key] = s
        return s
    }

    /** El ángulo del perfil del lado cercano (flexión en °; 0 = extendido; τ para TRUNK). */
    fun angle(angle: RomAngle): MetricSeries? {
        val side = near ?: return null
        return cached("angle:$angle") { JointAngleSeries.build(prepared, angle, side, tuning) }
    }

    /** X o Y (FH) del punto [lm], con el aspecto y el giro del móvil corregidos; `null` si falta en casi toda la traza. */
    fun position(lm: PoseLandmark, yAxis: Boolean): MetricSeries? = cached("pos:${lm.name}:$yAxis") {
        val raw = DoubleArray(frames.size) { Double.NaN }
        var valid = 0
        for (i in raw.indices) {
            val p = geo.point(i, lm) ?: continue
            raw[i] = if (yAxis) p.y else p.x
            valid++
        }
        seriesIfEnough(raw, valid)
    }

    /** Ángulo incluido θ(a, b, c) en grados del lado cercano; `null` si falta en casi toda la traza. */
    fun included(key: String, a: PoseLandmark, b: PoseLandmark, c: PoseLandmark): MetricSeries? = cached("angle3:$key") {
        val raw = DoubleArray(frames.size) { Double.NaN }
        var valid = 0
        for (i in raw.indices) {
            val pa = geo.point(i, a) ?: continue
            val pb = geo.point(i, b) ?: continue
            val pc = geo.point(i, c) ?: continue
            val theta = Angles.included(pa, pb, pc)
            if (theta.isNaN()) continue
            raw[i] = theta
            valid++
        }
        seriesIfEnough(raw, valid)
    }

    /**
     * Desviación de la línea del cuerpo en las flexiones: 180° − θ(S,H,A) en la cadera, positiva si la cadera queda por encima de la
     * línea hombro-tobillo y negativa si cae por debajo (el producto vectorial, con el sentido en que mira el atleta).
     */
    fun bodyLine(): MetricSeries? = cached("bodyLine") {
        val side = near ?: return@cached null
        val raw = DoubleArray(frames.size) { Double.NaN }
        var valid = 0
        val f = prepared.facing.toDouble()
        for (i in raw.indices) {
            val s = geo.point(i, side.shoulder()) ?: continue
            val h = geo.point(i, side.hip()) ?: continue
            val a = geo.point(i, side.ankle()) ?: continue
            val theta = Angles.included(s, h, a)
            if (theta.isNaN()) continue
            val above = f * (a - s).cross(h - s) > 0.0
            raw[i] = (STRAIGHT - theta) * (if (above) 1.0 else -1.0)
            valid++
        }
        seriesIfEnough(raw, valid)
    }

    private fun seriesIfEnough(raw: DoubleArray, valid: Int): MetricSeries? {
        if (valid < tuning.minObservedS * prepared.timebase.fs) return null
        return MetricSeriesBuilder.fromFrames(prepared, raw, tuning)
    }

    /** Media de [s] en [tUs] ± [halfS]; NaN si falta casi todo. */
    fun meanAt(s: MetricSeries, tUs: Long, halfS: Double = m.instantWindowHalfS): Double {
        val c = s.indexOf(tUs)
        val h = halfS * s.fs
        val lo = ceil(c - h).toInt()
        val hi = floor(c + h).toInt()
        return if (hi < lo) SeriesMath.interpolate(s.v, c) else SeriesMath.meanIn(s.v, lo, hi, m.minWindowValidFraction)
    }

    /** Los índices de [s] entre [fromUs] y [toUs] (con [marginS] a cada lado); `null` si queda vacía. */
    fun window(s: MetricSeries, fromUs: Long, toUs: Long, marginS: Double = 0.0): IntRange? {
        val lo = max(0, floor(s.indexOf(fromUs) - marginS * s.fs).toInt())
        val hi = min(s.size - 1, ceil(s.indexOf(toUs) + marginS * s.fs).toInt())
        return if (hi > lo) lo..hi else null
    }

    /** [s] con una media móvil de 0,3 s: los extremos de una repetición no los empuja el ruido. */
    fun smoothed(s: MetricSeries): DoubleArray = SeriesMath.movingAverage(s.v, max(1, (m.extremumWindowS * s.fs / 2.0).roundToInt()))

    /** Mediana de la visibilidad de [lm] entre [fromUs] y [toUs]; NaN si no hay ningún fotograma. */
    fun medianVisibility(lm: PoseLandmark, fromUs: Long, toUs: Long): Double {
        var lo = 0
        var hi = frames.size
        while (lo < hi) {
            val mid = (lo + hi) ushr 1
            if (frames[mid].tUs < fromUs) lo = mid + 1 else hi = mid
        }
        val values = ArrayList<Double>()
        var i = lo
        while (i < frames.size && frames[i].tUs <= toUs) {
            val v = frames[i].vis(lm)
            if (!v.isNaN()) values.add(v.toDouble())
            i++
        }
        return if (values.isEmpty()) Double.NaN else RobustStats.median(values.toDoubleArray())
    }

    /** Los puntos [landmarks] se ven (visibilidad mediana ≥ 0,7) en el fondo de [rep]: alrededor de su giro. */
    fun visibleAtTurn(rep: RepReport, vararg landmarks: PoseLandmark): Boolean {
        val margin = (m.turnWindowHalfS * MICROS).toLong()
        val from = rep.phases.turnStartUs - margin
        val to = rep.phases.turnEndUs + margin
        return landmarks.all { medianVisibility(it, from, to) >= t.minLandmarkVisibility }
    }

    // ------------------------------------------------------------------------------------------------------------
    // Candidatas
    // ------------------------------------------------------------------------------------------------------------

    /**
     * Añade una observación. [excess] es cuánto pasa de lo normal, en las unidades de la regla, y [unit] la escala con la que se
     * compara (normalmente el umbral): la fuerza es excess/unit, al menos 1.
     */
    fun add(
        out: MutableList<Candidate>,
        code: String,
        text: String,
        involved: List<RepReport>,
        atUs: Long?,
        magnitude: Double,
        excess: Double,
        unit: Double,
        weight: Double = 1.0,
    ) {
        if (involved.isEmpty() || !magnitude.isFinite()) return
        val strength = if (unit > 0.0 && excess.isFinite()) max(1.0, excess / unit) else 1.0
        out.add(Candidate(code, text, involved.map { it.index }, atUs, magnitude, strength, if (weight > 0.0) weight else 1.0))
    }

    // ------------------------------------------------------------------------------------------------------------
    // Comunes
    // ------------------------------------------------------------------------------------------------------------

    /** Tendencia del ROM: |Δ| ≥ 6° y |Δ|/media ≥ 10 %, con al menos 4 FULL (§7, «ROM»). */
    fun romTrend(out: MutableList<Candidate>) {
        val joint = profile.romAngle ?: return
        if (MetricKey.ROM !in report.available || full.size < m.romTrendMinFull) return
        val trend = report.romTrendDeg ?: return
        val mean = report.romMeanDeg ?: return
        if (!(mean > 0.0) || abs(trend) < t.romTrendMinDeg || abs(trend) / mean < t.romTrendMinFraction) return
        val first = full.take(TREND_REPS)
        val last = full.takeLast(TREND_REPS)
        add(
            out, ObservationCodes.ROM_TREND, ObservationText.romTrend(joint, trend, abs(trend) / mean * 100.0),
            (first + last).distinct(), last.last().phases.turnStartUs, abs(trend), abs(trend), t.romTrendMinDeg,
        )
    }

    /** El tronco en el giro de la última FULL frente a la primera (§7, «Tronco»), con al menos 3 FULL. */
    fun trunkChange(out: MutableList<Candidate>) {
        if (MetricKey.TRUNK !in report.available) return
        val withTurn = full.filter { it.trunkAtTurnDeg != null }
        if (withTurn.size < t.trunkChangeMinFull) return
        val first = withTurn.first()
        val last = withTurn.last()
        val delta = (last.trunkAtTurnDeg ?: return) - (first.trunkAtTurnDeg ?: return)
        if (abs(delta) < t.trunkChangeDeg) return
        add(
            out, ObservationCodes.TRUNK_CHANGE, ObservationText.trunkChange(delta),
            listOf(first, last), last.phases.turnStartUs, abs(delta), abs(delta), t.trunkChangeDeg,
        )
    }

    /** Variación del ROM entre repeticiones: CV > 10 % (rumano, buenos días, hip thrust). */
    fun romVaries(out: MutableList<Candidate>) {
        val joint = profile.romAngle ?: return
        val cv = report.romCvPct ?: return
        if (MetricKey.ROM !in report.available || full.size < m.romTrendMinFull - 1 || cv <= t.romCvPct) return
        add(out, ObservationCodes.ROM_VARIES, ObservationText.romVaries(joint, cv), full, full.last().phases.turnStartUs, cv, cv, t.romCvPct)
    }

    // ------------------------------------------------------------------------------------------------------------
    // Sentadilla
    // ------------------------------------------------------------------------------------------------------------

    fun squat(out: MutableList<Candidate>) {
        val side = near
        if (view == ViewKind.SIDE && side != null) {
            squatDepth(out, side)
            squatHipsFirst(out, side)
            squatHeelLift(out, side)
        }
        squatBarDrift(out)
    }

    /** Clase de profundidad por repetición y profundidad irregular (cadera y rodilla con visibilidad ≥ 0,7 en el fondo). */
    private fun squatDepth(out: MutableList<Candidate>, side: Side) {
        if (MetricKey.DEPTH !in report.available) return
        val seen = full.filter { it.depthDeg != null && visibleAtTurn(it, side.hip(), side.knee()) }
        val above = seen.filter { it.depthClass == DepthClass.ABOVE }
        if (above.isNotEmpty()) {
            val deg = above.map { -(it.depthDeg ?: 0.0) }.average()
            val worst = above.minByOrNull { it.depthDeg ?: 0.0 } ?: above.first()
            add(
                out, ObservationCodes.SQUAT_DEPTH_ABOVE, ObservationText.squatDepthAbove(above.map { it.index }, deg),
                above, worst.phases.turnStartUs, deg, deg, m.depthParallelDeg,
            )
        }
        if (seen.size < IRREGULAR_MIN_REPS) return
        val alphas = seen.map { it.depthDeg ?: 0.0 }
        val sd = SeriesMath.sampleSd(alphas)
        if (sd > t.squatDepthSdDeg) {
            add(
                out, ObservationCodes.SQUAT_DEPTH_IRREGULAR, ObservationText.squatDepthIrregular(sd),
                seen, seen.first().phases.turnStartUs, sd, sd, t.squatDepthSdDeg,
            )
        }
        val median = SeriesMath.median(alphas)
        val higher = seen.filter { (it.depthDeg ?: 0.0) <= median - t.squatShallowFromMedianDeg }
        if (higher.isNotEmpty()) {
            val by = higher.map { median - (it.depthDeg ?: median) }.average()
            val top = higher.minByOrNull { it.depthDeg ?: 0.0 } ?: higher.first()
            add(
                out, ObservationCodes.SQUAT_DEPTH_SHALLOW_REP, ObservationText.squatDepthShallowRep(higher.map { it.index }, by),
                higher, top.phases.turnStartUs, by, by, t.squatShallowFromMedianDeg,
            )
        }
    }

    /** Cadera adelantada: Δτ temprano ≥ 8° y subida de cadera / subida de hombro ≥ 1,5 en el primer 30 % de la CON. */
    private fun squatHipsFirst(out: MutableList<Candidate>, side: Side) {
        if (MetricKey.TRUNK !in report.available) return
        val hipY = position(side.hip(), true) ?: return
        val shoulderY = position(side.shoulder(), true) ?: return
        val minRise = (bodyHeightFh ?: return) * MIN_RISE_BODY
        val flagged = ArrayList<RepReport>()
        val ratios = ArrayList<Double>()
        val deltas = ArrayList<Double>()
        for (rep in full) {
            val delta = rep.trunkEarlyDeltaDeg ?: continue
            if (delta < t.squatHipsEarlyDeltaDeg) continue
            val con = conSpan(rep) ?: continue
            val later = con[0] + (t.earlyConTimeFraction * (con[1] - con[0])).toLong()
            val hipRise = meanAt(hipY, con[0]) - meanAt(hipY, later)
            val shoulderRise = meanAt(shoulderY, con[0]) - meanAt(shoulderY, later)
            if (hipRise.isNaN() || shoulderRise.isNaN() || !(shoulderRise > minRise)) continue
            val ratio = hipRise / shoulderRise
            if (ratio < t.squatHipsRiseRatio) continue
            flagged.add(rep)
            ratios.add(ratio)
            deltas.add(delta)
        }
        if (flagged.isEmpty()) return
        val ratio = ratios.average()
        add(
            out, ObservationCodes.SQUAT_HIPS_FIRST, ObservationText.squatHipsFirst(flagged.map { it.index }, ratio, deltas.average()),
            flagged, conSpan(flagged.first())?.get(0), ratio, ratio, t.squatHipsRiseRatio,
        )
    }

    /** Talón que se levanta: ≥ 6 % del largo del pie con la punta quieta (SIDE, pie con visibilidad ≥ 0,7). */
    private fun squatHeelLift(out: MutableList<Candidate>, side: Side) {
        val heelY = position(side.heel(), true) ?: return
        val toeY = position(side.toe(), true) ?: return
        val footLength = MetricSeriesBuilder.segmentRef(geo, side.heel(), side.toe())
        if (!(footLength > 0.0) || full.isEmpty()) return
        // El suelo: el percentil 90 de la Y del talón y de la punta (la Y crece hacia abajo) en la serie.
        val from = full.first().phases.departUs
        val to = full.last().phases.arriveUs
        val active = window(heelY, from, to) ?: return
        val heelFloor = RobustStats.percentile(heelY.v.copyOfRange(active.first, active.last + 1), 90.0)
        val toeFloor = RobustStats.percentile(toeY.v.copyOfRange(active.first, active.last + 1), 90.0)
        if (heelFloor.isNaN() || toeFloor.isNaN()) return
        val flagged = ArrayList<RepReport>()
        val lifts = ArrayList<Double>()
        val margin = (m.turnWindowHalfS * 2.0 * MICROS).toLong()
        for (rep in full) {
            if (!visibleAtTurn(rep, side.heel(), side.toe())) continue
            val w = window(heelY, rep.phases.turnStartUs - margin, rep.phases.turnEndUs + margin) ?: continue
            val lift = ArrayList<Double>()
            val toeMove = ArrayList<Double>()
            for (k in w) {
                val h = heelY.v[k]
                val tv = toeY.v[k]
                if (h.isNaN() || tv.isNaN()) continue
                lift.add(heelFloor - h)
                toeMove.add(abs(tv - toeFloor))
            }
            if (lift.size < MIN_WINDOW_SAMPLES) continue
            val liftMedian = SeriesMath.median(lift)
            val toeMedian = SeriesMath.median(toeMove)
            if (liftMedian >= t.heelLiftFootFraction * footLength && toeMedian <= t.toeStillFootFraction * footLength) {
                flagged.add(rep)
                lifts.add(liftMedian / footLength * 100.0)
            }
        }
        if (flagged.isEmpty()) return
        val percent = lifts.average()
        add(
            out, ObservationCodes.SQUAT_HEEL_LIFT, ObservationText.squatHeelLift(flagged.map { it.index }, percent),
            flagged, flagged.first().phases.turnStartUs, percent, percent, t.heelLiftFootFraction * 100.0,
        )
    }

    /** Deriva de la barra en la concéntrica: |drift| ≥ 5 cm (calibrado) o ≥ 10 % de ROM_v (≈). */
    private fun squatBarDrift(out: MutableList<Candidate>) {
        if (MetricKey.PATH !in report.available || report.barDeviationUnit == null) return
        val limit = if (centimetres) t.squatDriftCm else t.squatDriftPctRom
        val drifting = full.filter { (it.pathDrift ?: 0.0).let { d -> abs(d) >= limit } }
        if (drifting.isEmpty()) return
        val top = drifting.maxByOrNull { abs(it.pathDrift ?: 0.0) } ?: return
        val forward = (top.pathDrift ?: 0.0) > 0.0
        val same = drifting.filter { ((it.pathDrift ?: 0.0) > 0.0) == forward }
        val amount = abs(top.pathDrift ?: 0.0)
        add(
            out, ObservationCodes.SQUAT_BAR_DRIFT, ObservationText.barDrift(same.map { it.index }, amount, centimetres, forward),
            same, midCon(top), amount, amount, limit,
        )
    }

    // ------------------------------------------------------------------------------------------------------------
    // Peso muerto
    // ------------------------------------------------------------------------------------------------------------

    fun deadlift(out: MutableList<Candidate>) {
        val side = near
        if (view == ViewKind.SIDE && side != null) {
            deadliftHipsFirst(out, side)
            deadliftLockout(out)
            deadliftLeanBack(out)
        }
        deadliftStyle(out)
        deadliftBounce(out)
    }

    /** Cadera antes que la barra: ΔY_cadera/ΔY_barra ≥ 2 en el primer 15 % del recorrido de la barra y Δτ ≥ 5°. */
    private fun deadliftHipsFirst(out: MutableList<Candidate>, side: Side) {
        val c = ctx ?: return
        val barY = c.barYSmooth ?: return
        val s = bodyHeightFh ?: return
        val hipY = position(side.hip(), true) ?: return
        val flagged = ArrayList<RepReport>()
        val ratios = ArrayList<Double>()
        val deltas = ArrayList<Double>()
        for (rep in full) {
            val delta = rep.trunkEarlyDeltaDeg ?: continue
            if (delta < t.deadliftHipsDeltaTauDeg) continue
            val con = conSpan(rep) ?: continue
            val i0 = c.indexOf(con[0])
            val i1 = c.indexOf(con[1])
            val lo = ceil(i0).toInt()
            val hi = floor(i1).toInt()
            val start = SeriesMath.interpolate(barY, i0)
            val top = SeriesMath.minIn(barY, lo, hi)
            val total = start - top
            if (start.isNaN() || top.isNaN() || !(total > MIN_BAR_RISE_BODY)) continue
            // El primer instante en que la barra ha subido el 15 % de su recorrido.
            val target = start - t.deadliftBarRomFraction * total
            var reached = Double.NaN
            for (k in max(1, lo)..min(barY.size - 1, hi)) {
                if (!barY[k].isNaN() && barY[k] <= target) {
                    val prev = barY[k - 1]
                    reached = if (prev.isNaN() || prev <= barY[k]) k.toDouble() else k - 1 + (prev - target) / (prev - barY[k])
                    break
                }
            }
            if (reached.isNaN()) continue
            val hipStart = SeriesMath.interpolate(hipY.v, hipY.indexOf(con[0]))
            val hipLater = SeriesMath.interpolate(hipY.v, reached)
            if (hipStart.isNaN() || hipLater.isNaN()) continue
            val hipRise = (hipStart - hipLater) / s
            val ratio = hipRise / (t.deadliftBarRomFraction * total)
            if (ratio < t.deadliftHipsToBarRatio) continue
            flagged.add(rep)
            ratios.add(ratio)
            deltas.add(delta)
        }
        if (flagged.isEmpty()) return
        val ratio = ratios.average()
        add(
            out, ObservationCodes.DEADLIFT_HIPS_FIRST,
            ObservationText.deadliftHipsFirst(flagged.map { it.index }, ratio, deltas.average()),
            flagged, conSpan(flagged.first())?.get(0), ratio, ratio, t.deadliftHipsToBarRatio,
        )
    }

    /** Bloqueo: θH < 165° o θK < 160° arriba (con el ángulo que se midió). */
    private fun deadliftLockout(out: MutableList<Candidate>) {
        if (MetricKey.LOCKOUT !in report.available) return
        val hip = angle(RomAngle.HIP)
        val knee = angle(RomAngle.KNEE)
        val flagged = ArrayList<RepReport>()
        val hipValues = ArrayList<Double>()
        val kneeValues = ArrayList<Double>()
        var shortfall = 0.0
        for (rep in full) {
            if (rep.criteria[DepthCriteria.LOCKOUT] != false) continue
            val end = conSpan(rep)?.get(1) ?: continue
            val h = if (hip != null) STRAIGHT - meanAt(hip, end) else Double.NaN
            val k = if (knee != null) STRAIGHT - meanAt(knee, end) else Double.NaN
            val hipFails = !h.isNaN() && h < m.lockoutDeadliftHipDeg
            val kneeFails = !k.isNaN() && k < m.lockoutDeadliftKneeDeg
            if (!hipFails && !kneeFails) continue
            flagged.add(rep)
            if (hipFails) {
                hipValues.add(h)
                shortfall = max(shortfall, m.lockoutDeadliftHipDeg - h)
            }
            if (kneeFails) {
                kneeValues.add(k)
                shortfall = max(shortfall, m.lockoutDeadliftKneeDeg - k)
            }
        }
        if (flagged.isEmpty()) return
        add(
            out, ObservationCodes.DEADLIFT_LOCKOUT,
            ObservationText.deadliftLockout(
                flagged.map { it.index }, hipValues.takeIf { it.isNotEmpty() }?.average(), kneeValues.takeIf { it.isNotEmpty() }?.average(),
                m.lockoutDeadliftHipDeg, m.lockoutDeadliftKneeDeg,
            ),
            flagged, flagged.first().phases.turnStartUs, shortfall, shortfall, LOCKOUT_SCALE_DEG,
        )
    }

    /** Inclinación hacia atrás arriba: τ ≤ −10° al terminar la concéntrica. */
    private fun deadliftLeanBack(out: MutableList<Candidate>) {
        val trunk = angle(RomAngle.TRUNK) ?: return
        val flagged = ArrayList<RepReport>()
        val backs = ArrayList<Double>()
        for (rep in full) {
            val end = conSpan(rep)?.get(1) ?: continue
            val tau = meanAt(trunk, end)
            if (tau.isNaN() || -tau < t.deadliftLeanBackDeg) continue
            flagged.add(rep)
            backs.add(-tau)
        }
        if (flagged.isEmpty()) return
        val back = backs.maxOrNull() ?: return
        add(
            out, ObservationCodes.DEADLIFT_LEAN_BACK, ObservationText.leanBackAtTop(flagged.map { it.index }, back),
            flagged, flagged.first().phases.turnStartUs, back, back, t.deadliftLeanBackDeg,
        )
    }

    /** Estilo por repetición: con parada en el suelo (≥ 0,25 s) o continuo; solo describe si hay de las dos. */
    private fun deadliftStyle(out: MutableList<Candidate>) {
        val withPause = full.filter { it.restPauseMs != null }
        if (withPause.size < STYLE_MIN_REPS) return
        val paused = withPause.filter { (it.restPauseMs ?: 0) > 0 }
        val continuous = withPause.filter { (it.restPauseMs ?: 0) <= 0 }
        if (paused.isEmpty() || continuous.isEmpty()) return
        val minority = if (paused.size <= continuous.size) paused else continuous
        add(
            out, ObservationCodes.DEADLIFT_STYLE, ObservationText.deadliftStyle(paused.size, continuous.size),
            minority, minority.first().phases.departUs, minority.size.toDouble(), 1.0, 1.0, t.descriptiveWeight,
        )
    }

    /**
     * Rebote: menos de 0,1 s de contacto con el suelo y la velocidad de la barra se invierte en el suelo sin pararse (a ±0,05 s del
     * instante de contacto la barra va al menos al 25 % de su velocidad máxima). Con el disco como señal y a ≥ 50 fps.
     */
    private fun deadliftBounce(out: MutableList<Candidate>) {
        val c = ctx ?: return
        if (c.series.source != CountSeriesBuilder.SOURCE_PLATE || report.analyzedFps < t.deadliftBounceMinFps) return
        val barY = c.barYSmooth ?: return
        val fs = c.series.fs
        val flagged = ArrayList<RepReport>()
        for (k in 1 until full.size) {
            val prev = full[k - 1]
            val rep = full[k]
            val gapUs = rep.phases.departUs - prev.phases.arriveUs
            // Un hueco negativo (los ajustes de las dos repeticiones se solapan) es un contacto sin pausa.
            if (gapUs / MICROS >= t.deadliftBounceMaxS) continue
            val mid = (rep.phases.departUs + prev.phases.arriveUs) / 2
            val conPeak = peakSpeed(barY, c.indexOf(rep.phases.departUs), c.indexOf(rep.phases.turnStartUs), fs)
            val before = speedAt(barY, c.indexOf(mid) - BOUNCE_PROBE_S * fs, fs)
            val after = speedAt(barY, c.indexOf(mid) + BOUNCE_PROBE_S * fs, fs)
            if (conPeak.isNaN() || before.isNaN() || after.isNaN() || !(conPeak > 0.0)) continue
            if (min(before, after) >= BOUNCE_SPEED_FRACTION * conPeak) flagged.add(rep)
        }
        if (flagged.isEmpty()) return
        add(
            out, ObservationCodes.DEADLIFT_BOUNCE, ObservationText.deadliftBounce(flagged.map { it.index }),
            flagged, flagged.first().phases.departUs, flagged.size.toDouble(), 1.0, 1.0,
        )
    }

    // ------------------------------------------------------------------------------------------------------------
    // Rumano y buenos días
    // ------------------------------------------------------------------------------------------------------------

    fun hinge(out: MutableList<Candidate>) {
        val side = near
        if (view == ViewKind.SIDE && side != null) hingeKneeFlexion(out)
        romVaries(out)
        hingeBarAhead(out)
    }

    /** La flexión de rodilla crece ≥ 25° durante la excéntrica. */
    private fun hingeKneeFlexion(out: MutableList<Candidate>) {
        val knee = angle(RomAngle.KNEE) ?: return
        val smooth = smoothed(knee)
        val flagged = ArrayList<RepReport>()
        val growths = ArrayList<Double>()
        for (rep in full) {
            val ecc = eccSpan(rep) ?: continue
            val w = window(knee, ecc[0], ecc[1]) ?: continue
            val start = meanAt(knee, ecc[0])
            val peak = SeriesMath.maxIn(smooth, w.first, w.last)
            if (start.isNaN() || peak.isNaN()) continue
            val growth = peak - start
            if (growth < t.hingeKneeGrowthDeg) continue
            flagged.add(rep)
            growths.add(growth)
        }
        if (flagged.isEmpty()) return
        val growth = growths.maxOrNull() ?: return
        add(
            out, ObservationCodes.HINGE_KNEE_FLEXION, ObservationText.hingeKneeFlexion(flagged.map { it.index }, growth),
            flagged, flagged.first().phases.turnStartUs, growth, growth, t.hingeKneeGrowthDeg,
        )
    }

    /** Barra ≥ 8 cm por delante del medio pie en el fondo: solo con el disco calibrado y sin paralaje oculto. */
    private fun hingeBarAhead(out: MutableList<Candidate>) {
        val p = path ?: return
        if (!centimetres || !p.calibrated || p.offsetsHidden) return
        val flagged = ArrayList<RepReport>()
        var top = 0.0
        for (rep in full) {
            val offset = p.reps.getOrNull(repPosition(rep))?.offsetMaxCm ?: continue
            if (offset.isNaN() || offset < t.hingeBarAheadCm) continue
            flagged.add(rep)
            top = max(top, offset)
        }
        if (flagged.isEmpty()) return
        add(
            out, ObservationCodes.HINGE_BAR_AHEAD, ObservationText.hingeBarAhead(flagged.map { it.index }, top),
            flagged, flagged.first().phases.turnStartUs, top, top, t.hingeBarAheadCm,
        )
    }

    // ------------------------------------------------------------------------------------------------------------
    // Hip thrust
    // ------------------------------------------------------------------------------------------------------------

    fun hipThrust(out: MutableList<Candidate>) {
        val side = near
        if (side != null && view in profile.metricViews) {
            thrustLockout(out)
            thrustPause(out)
        }
        romVaries(out)
    }

    /** θH arriba < 165° (con el ángulo medido). */
    private fun thrustLockout(out: MutableList<Candidate>) {
        if (MetricKey.LOCKOUT !in report.available) return
        val hip = angle(RomAngle.HIP) ?: return
        val flagged = ArrayList<RepReport>()
        val values = ArrayList<Double>()
        for (rep in full) {
            if (rep.criteria[DepthCriteria.LOCKOUT] != false) continue
            val end = conSpan(rep)?.get(1) ?: continue
            val theta = STRAIGHT - meanAt(hip, end)
            if (theta.isNaN() || theta >= m.lockoutHipThrustHipDeg) continue
            flagged.add(rep)
            values.add(theta)
        }
        if (flagged.isEmpty()) return
        val worst = values.minOrNull() ?: return
        val shortfall = m.lockoutHipThrustHipDeg - worst
        add(
            out, ObservationCodes.THRUST_LOCKOUT, ObservationText.thrustLockout(flagged.map { it.index }, values.average(), m.lockoutHipThrustHipDeg),
            flagged, flagged.first().phases.turnStartUs, shortfall, shortfall, LOCKOUT_SCALE_DEG,
        )
    }

    /** Pausa media arriba (la pausa de giro de las FULL): solo describe, y solo si hay pausa (≥ 0,25 s). */
    private fun thrustPause(out: MutableList<Candidate>) {
        val pauses = full.mapNotNull { it.turnPauseMs }.map { it.toDouble() }
        if (pauses.size < PAUSE_MIN_REPS) return
        val median = SeriesMath.median(pauses)
        if (!(median >= tuning.turnPauseMinS * 1000.0)) return
        add(
            out, ObservationCodes.THRUST_PAUSE, ObservationText.thrustPause(median),
            full, full.first().phases.turnStartUs, median / 1000.0, 1.0, 1.0, t.descriptiveWeight,
        )
    }

    // ------------------------------------------------------------------------------------------------------------
    // Banca
    // ------------------------------------------------------------------------------------------------------------

    fun bench(out: MutableList<Candidate>) {
        if (view !in profile.metricViews) return
        benchAtypical(out)
        benchContact(out)
        benchShorter(out)
        benchPause(out)
    }

    /** Repetición atípica: PD ≥ max(2× la mediana, 3 cm o 6 % de ROM_v). */
    private fun benchAtypical(out: MutableList<Candidate>) {
        if (MetricKey.PATH !in report.available) return
        val withPd = full.filter { it.pathDeviationRms != null }
        if (withPd.size < m.pathMinReps) return
        val median = SeriesMath.median(withPd.map { it.pathDeviationRms ?: 0.0 })
        val minimum = if (centimetres) t.benchDeviationMinCm else t.benchDeviationMinPctRom
        val limit = max(t.benchAtypicalFactor * median, minimum)
        val atypical = withPd.filter { (it.pathDeviationRms ?: 0.0) >= limit }
        if (atypical.isEmpty()) return
        val top = atypical.maxByOrNull { it.pathDeviationRms ?: 0.0 } ?: return
        val amount = top.pathDeviationRms ?: return
        add(
            out, ObservationCodes.BENCH_ATYPICAL, ObservationText.benchAtypical(atypical.map { it.index }, amount, centimetres),
            atypical, midCon(top), amount, amount, limit,
        )
    }

    /** Desviación típica del punto de contacto ≥ 3 cm o 6 % de ROM_v. */
    private fun benchContact(out: MutableList<Candidate>) {
        val p = path ?: return
        if (!p.available || full.size < m.pathMinReps) return
        val amount: Double
        val limit: Double
        if (centimetres && p.calibrated) {
            amount = p.contactSdCm ?: return
            limit = t.benchDeviationMinCm
        } else {
            val sd = p.contactSdBh ?: return
            val rom = p.medianRomVBh ?: return
            if (!(rom > 0.0)) return
            amount = sd / rom * 100.0
            limit = t.benchDeviationMinPctRom
        }
        if (amount < limit) return
        add(
            out, ObservationCodes.BENCH_CONTACT, ObservationText.benchContact(amount, centimetres && p.calibrated),
            full, full.first().phases.turnStartUs, amount, amount, limit,
        )
    }

    /** Repetición más corta: el fondo queda más de un 10 % de ROM_v por encima de la mediana. */
    private fun benchShorter(out: MutableList<Candidate>) {
        val p = path ?: return
        val shorter = full.filter { it.criteria[DepthCriteria.SHALLOWER] == true }
        if (shorter.isEmpty()) return
        val medianRom = if (centimetres && p.calibrated) p.medianRomVCm else p.medianRomVBh
        if (medianRom == null || !(medianRom > 0.0)) return
        val gaps = shorter.mapNotNull { rep ->
            val r = p.reps.getOrNull(repPosition(rep)) ?: return@mapNotNull null
            val own = if (centimetres && p.calibrated) r.romVCm else r.romVBh
            if (own.isNaN()) null else medianRom - own
        }
        if (gaps.isEmpty()) return
        val amount = if (centimetres && p.calibrated) gaps.max() else gaps.max() / medianRom * 100.0
        if (!(amount > 0.0)) return
        val top = shorter.first()
        add(
            out, ObservationCodes.BENCH_SHORTER, ObservationText.benchShorter(shorter.map { it.index }, amount, centimetres && p.calibrated),
            shorter, top.phases.turnStartUs, amount, amount,
            if (centimetres && p.calibrated) t.benchDeviationMinCm else t.benchDeviationMinPctRom,
        )
    }

    /** En banca con pausa, la pausa en el pecho debe durar ≥ 0,5 s. Hay «pausa» si la mayoría de las FULL tienen ≥ 0,25 s. */
    private fun benchPause(out: MutableList<Candidate>) {
        val pauses = full.mapNotNull { rep -> rep.turnPauseMs?.let { rep to it } }
        if (pauses.size < PAUSE_MIN_REPS) return
        val minPause = tuning.turnPauseMinS * 1000.0
        val paused = pauses.count { it.second >= minPause }
        if (paused * 2 < pauses.size) return
        val median = SeriesMath.median(pauses.map { it.second.toDouble() })
        val short = pauses.filter { it.second < t.benchPauseMinS * 1000.0 }
        if (short.isEmpty()) return
        val shortest = short.minOf { it.second }.toDouble()
        add(
            out, ObservationCodes.BENCH_PAUSE, ObservationText.benchPause(short.map { it.first.index }, shortest, median),
            short.map { it.first }, short.first().first.phases.turnStartUs, t.benchPauseMinS * 1000.0 - shortest, t.benchPauseMinS * 1000.0 - shortest,
            t.benchPauseMinS * 1000.0,
        )
    }

    // ------------------------------------------------------------------------------------------------------------
    // Flexiones
    // ------------------------------------------------------------------------------------------------------------

    fun pushUp(out: MutableList<Candidate>) {
        if (view !in profile.metricViews) return
        // θE,min > 110° es una flexión corta.
        val short = full.filter { (it.depthDeg ?: 0.0) > m.pushUpShortElbowDeg }
        if (short.isNotEmpty() && MetricKey.DEPTH in report.available) {
            val worst = short.maxOf { it.depthDeg ?: 0.0 }
            add(
                out, ObservationCodes.PUSHUP_SHORT, ObservationText.pushUpShort(short.map { it.index }, worst),
                short, short.first().phases.turnStartUs, worst - m.pushUpShortElbowDeg, worst - m.pushUpShortElbowDeg, PUSHUP_SCALE_DEG,
            )
        }
        pushUpBodyLine(out)
    }

    /** Línea del cuerpo θ(S,H,A) < 165°: cadera que cae o que sube, según el signo del producto vectorial. */
    private fun pushUpBodyLine(out: MutableList<Candidate>) {
        val line = bodyLine() ?: return
        val smooth = smoothed(line)
        val limit = STRAIGHT - t.pushUpBodyLineDeg
        val sagging = ArrayList<RepReport>()
        val piking = ArrayList<RepReport>()
        val sagDeg = ArrayList<Double>()
        val pikeDeg = ArrayList<Double>()
        for (rep in full) {
            val w = window(line, rep.phases.departUs, rep.phases.arriveUs, t.repMarginS) ?: continue
            val high = SeriesMath.maxIn(smooth, w.first, w.last)
            val low = SeriesMath.minIn(smooth, w.first, w.last)
            val dev = if (abs(low) >= abs(high)) low else high
            if (dev.isNaN() || abs(dev) < limit) continue
            if (dev < 0.0) {
                sagging.add(rep)
                sagDeg.add(-dev)
            } else {
                piking.add(rep)
                pikeDeg.add(dev)
            }
        }
        val sags = sagging.size >= piking.size
        val flagged = if (sags) sagging else piking
        val degrees = if (sags) sagDeg else pikeDeg
        if (flagged.isEmpty()) return
        val deg = degrees.average()
        add(
            out, ObservationCodes.PUSHUP_BODY_LINE, ObservationText.pushUpBodyLine(flagged.map { it.index }, deg, sags),
            flagged, flagged.first().phases.turnStartUs, deg, deg, limit,
        )
    }

    // ------------------------------------------------------------------------------------------------------------
    // Press militar
    // ------------------------------------------------------------------------------------------------------------

    fun ohp(out: MutableList<Candidate>) {
        val side = near
        if (view != ViewKind.SIDE || side == null) return
        ohpLeanBack(out)
        ohpLockout(out)
        ohpKneeDrive(out)
    }

    /** Inclinación hacia atrás en el pico de velocidad (la mitad de la subida): τ ≤ −12°. */
    private fun ohpLeanBack(out: MutableList<Candidate>) {
        val trunk = angle(RomAngle.TRUNK) ?: return
        val flagged = ArrayList<RepReport>()
        val backs = ArrayList<Double>()
        for (rep in full) {
            val con = conSpan(rep) ?: continue
            val tau = meanAt(trunk, (con[0] + con[1]) / 2)
            if (tau.isNaN() || -tau < t.ohpLeanBackDeg) continue
            flagged.add(rep)
            backs.add(-tau)
        }
        if (flagged.isEmpty()) return
        val back = backs.maxOrNull() ?: return
        add(
            out, ObservationCodes.OHP_LEAN_BACK, ObservationText.ohpLeanBack(flagged.map { it.index }, back),
            flagged, midCon(flagged.first()), back, back, t.ohpLeanBackDeg,
        )
    }

    /** Bloqueo: θE < 160° (o la muñeca por debajo de la nariz) al terminar la concéntrica. */
    private fun ohpLockout(out: MutableList<Candidate>) {
        if (MetricKey.LOCKOUT !in report.available) return
        val elbow = angle(RomAngle.ELBOW)
        val bent = ArrayList<RepReport>()
        val bentDeg = ArrayList<Double>()
        val lowWrist = ArrayList<RepReport>()
        for (rep in full) {
            if (rep.criteria[DepthCriteria.LOCKOUT] != false) continue
            val end = conSpan(rep)?.get(1) ?: continue
            val theta = if (elbow != null) STRAIGHT - meanAt(elbow, end) else Double.NaN
            if (!theta.isNaN() && theta < m.lockoutOhpElbowDeg) {
                bent.add(rep)
                bentDeg.add(theta)
            } else {
                lowWrist.add(rep)
            }
        }
        if (bent.isNotEmpty()) {
            val shortfall = m.lockoutOhpElbowDeg - (bentDeg.minOrNull() ?: m.lockoutOhpElbowDeg)
            add(
                out, ObservationCodes.OHP_LOCKOUT, ObservationText.ohpLockoutElbow(bent.map { it.index }, bentDeg.average(), m.lockoutOhpElbowDeg),
                bent, bent.first().phases.turnStartUs, shortfall, shortfall, LOCKOUT_SCALE_DEG,
            )
        } else if (lowWrist.isNotEmpty()) {
            add(
                out, ObservationCodes.OHP_LOCKOUT, ObservationText.ohpLockoutWrist(lowWrist.map { it.index }),
                lowWrist, lowWrist.first().phases.turnStartUs, lowWrist.size.toDouble(), 1.0, 1.0,
            )
        }
    }

    /** En press estricto, flexión de rodilla ≥ 15° justo antes de la CON. Solo si llega el aviso `STRICT_PRESS`. */
    private fun ohpKneeDrive(out: MutableList<Candidate>) {
        if (STRICT_PRESS !in hints) return
        val knee = angle(RomAngle.KNEE) ?: return
        val flagged = ArrayList<RepReport>()
        val dips = ArrayList<Double>()
        for (rep in full) {
            val start = conSpan(rep)?.get(0) ?: continue
            val w = window(knee, start - (KNEE_DRIVE_WINDOW_S * MICROS).toLong(), start) ?: continue
            val high = SeriesMath.maxIn(knee.v, w.first, w.last)
            val low = SeriesMath.minIn(knee.v, w.first, w.last)
            if (high.isNaN() || low.isNaN() || high - low < t.ohpKneeDriveDeg) continue
            flagged.add(rep)
            dips.add(high - low)
        }
        if (flagged.isEmpty()) return
        val dip = dips.maxOrNull() ?: return
        add(
            out, ObservationCodes.OHP_KNEE_DRIVE, ObservationText.ohpKneeDrive(flagged.map { it.index }, dip),
            flagged, conSpan(flagged.first())?.get(0), dip, dip, t.ohpKneeDriveDeg,
        )
    }

    // ------------------------------------------------------------------------------------------------------------
    // Dominada y jalón
    // ------------------------------------------------------------------------------------------------------------

    /** Cuánto suben los hombros (BH) en la concéntrica de [rep], o `null` sin la barra del contexto. */
    private fun shoulderRise(rep: RepReport): Double? {
        val c = ctx ?: return null
        val y = c.barYSmooth ?: return null
        val con = conSpan(rep) ?: return null
        val lo = ceil(c.indexOf(con[0])).toInt()
        val hi = floor(c.indexOf(con[1])).toInt()
        val high = SeriesMath.maxIn(y, lo, hi)
        val low = SeriesMath.minIn(y, lo, hi)
        return if (high.isNaN() || low.isNaN()) null else high - low
    }

    /** `true` si es una dominada (los hombros suben), `false` si es un jalón, `null` si no se sabe. */
    private fun isPullUp(): Boolean? {
        val rises = full.mapNotNull { shoulderRise(it) }
        if (rises.isEmpty()) return null
        return SeriesMath.median(rises) >= t.pullUpShoulderRiseBh
    }

    fun pullVertical(out: MutableList<Candidate>) {
        if (view !in profile.metricViews) return
        val pullUp = isPullUp()
        if (pullUp == true) pullChin(out)
        pullHang(out)
        if (pullUp == true) pullSwing(out)
        if (pullUp == false) pulldownLeanBack(out)
    }

    /** Mentón sobre la barra por repetición (≈): las que no llegaron, con «n de N». */
    private fun pullChin(out: MutableList<Candidate>) {
        val judged = full.filter { it.criteria.containsKey(DepthCriteria.CHIN_OVER_BAR) }
        if (judged.size < PULL_MIN_REPS) return
        val missed = judged.filter { it.criteria[DepthCriteria.CHIN_OVER_BAR] == false }
        if (missed.isEmpty()) return
        add(
            out, ObservationCodes.PULL_CHIN, ObservationText.pullChin(judged.size - missed.size, judged.size),
            missed, missed.first().phases.turnStartUs, missed.size.toDouble(), missed.size.toDouble(), 1.0,
        )
    }

    /** Colgado abajo: θE < 155° (con el ángulo medido). */
    private fun pullHang(out: MutableList<Candidate>) {
        val elbow = angle(RomAngle.ELBOW) ?: return
        val smooth = smoothed(elbow)
        val flagged = ArrayList<RepReport>()
        val values = ArrayList<Double>()
        for (rep in full) {
            if (rep.criteria[DepthCriteria.FULL_HANG] != false) continue
            val w = window(elbow, rep.phases.departUs, rep.phases.arriveUs, t.repMarginS) ?: continue
            val flexion = SeriesMath.minIn(smooth, w.first, w.last)
            if (flexion.isNaN()) continue
            val theta = STRAIGHT - flexion
            if (theta >= m.fullHangElbowDeg) continue
            flagged.add(rep)
            values.add(theta)
        }
        if (flagged.isEmpty()) return
        val shortfall = m.fullHangElbowDeg - (values.minOrNull() ?: m.fullHangElbowDeg)
        add(
            out, ObservationCodes.PULL_HANG, ObservationText.pullHang(flagged.map { it.index }, values.average(), m.fullHangElbowDeg),
            flagged, flagged.first().phases.departUs, shortfall, shortfall, LOCKOUT_SCALE_DEG,
        )
    }

    /** Balanceo: X de la cadera ≥ 0,10 BH o flexión de rodilla ≥ 30° dentro de una repetición. */
    private fun pullSwing(out: MutableList<Candidate>) {
        val side = near ?: return
        val s = bodyHeightFh ?: return
        val hipX = position(side.hip(), false)
        val knee = angle(RomAngle.KNEE)
        val flagged = ArrayList<RepReport>()
        val hipSwing = ArrayList<Double>()
        val kneeSwing = ArrayList<Double>()
        for (rep in full) {
            var hipRange = Double.NaN
            var kneeRange = Double.NaN
            if (hipX != null) {
                val w = window(hipX, rep.phases.departUs, rep.phases.arriveUs, 0.0)
                if (w != null) {
                    val high = SeriesMath.maxIn(hipX.v, w.first, w.last)
                    val low = SeriesMath.minIn(hipX.v, w.first, w.last)
                    if (!high.isNaN() && !low.isNaN()) hipRange = (high - low) / s
                }
            }
            if (knee != null) {
                val w = window(knee, rep.phases.departUs, rep.phases.arriveUs, 0.0)
                if (w != null) {
                    val high = SeriesMath.maxIn(knee.v, w.first, w.last)
                    val low = SeriesMath.minIn(knee.v, w.first, w.last)
                    if (!high.isNaN() && !low.isNaN()) kneeRange = high - low
                }
            }
            val hipOver = !hipRange.isNaN() && hipRange >= t.pullSwingHipBh
            val kneeOver = !kneeRange.isNaN() && kneeRange >= t.pullSwingKneeDeg
            if (!hipOver && !kneeOver) continue
            flagged.add(rep)
            if (hipOver) hipSwing.add(hipRange * 100.0)
            if (kneeOver) kneeSwing.add(kneeRange)
        }
        if (flagged.isEmpty()) return
        val hipText = hipSwing.takeIf { it.isNotEmpty() }?.maxOrNull()
        val kneeText = kneeSwing.takeIf { it.isNotEmpty() }?.maxOrNull()
        val strength = max(
            if (hipText != null) hipText / (t.pullSwingHipBh * 100.0) else 0.0,
            if (kneeText != null) kneeText / t.pullSwingKneeDeg else 0.0,
        )
        add(
            out, ObservationCodes.PULL_SWING, ObservationText.pullSwing(flagged.map { it.index }, hipText, kneeText),
            flagged, flagged.first().phases.departUs, strength, strength, 1.0,
        )
    }

    /** Jalón: Δτ hacia atrás ≥ 15° durante la concéntrica. */
    private fun pulldownLeanBack(out: MutableList<Candidate>) {
        val trunk = angle(RomAngle.TRUNK) ?: return
        val flagged = ArrayList<RepReport>()
        val backs = ArrayList<Double>()
        for (rep in full) {
            val con = conSpan(rep) ?: continue
            val delta = meanAt(trunk, con[1]) - meanAt(trunk, con[0])
            if (delta.isNaN() || -delta < t.pulldownLeanBackDeg) continue
            flagged.add(rep)
            backs.add(-delta)
        }
        if (flagged.isEmpty()) return
        val back = backs.maxOrNull() ?: return
        add(
            out, ObservationCodes.PULLDOWN_LEAN_BACK, ObservationText.pulldownLeanBack(flagged.map { it.index }, back),
            flagged, midCon(flagged.first()), back, back, t.pulldownLeanBackDeg,
        )
    }

    // ------------------------------------------------------------------------------------------------------------
    // Remo
    // ------------------------------------------------------------------------------------------------------------

    fun row(out: MutableList<Candidate>) {
        if (view != ViewKind.SIDE) return
        // El torso sube ≥ 10° durante la concéntrica.
        val trunk = angle(RomAngle.TRUNK)
        if (trunk != null) {
            val flagged = ArrayList<RepReport>()
            val rises = ArrayList<Double>()
            for (rep in full) {
                val con = conSpan(rep) ?: continue
                val delta = meanAt(trunk, con[1]) - meanAt(trunk, con[0])
                if (delta.isNaN() || -delta < t.rowTorsoRiseDeg) continue
                flagged.add(rep)
                rises.add(-delta)
            }
            if (flagged.isNotEmpty()) {
                val rise = rises.maxOrNull() ?: 0.0
                add(
                    out, ObservationCodes.ROW_TORSO_RISE, ObservationText.rowTorsoRise(flagged.map { it.index }, rise),
                    flagged, midCon(flagged.first()), rise, rise, t.rowTorsoRiseDeg,
                )
            }
        }
        // Balanceo del tronco ≥ 15° en una repetición (remo en polea).
        val swinging = full.filter { (it.trunkRangeDeg ?: 0.0) >= t.rowCableSwingDeg }
        if (swinging.isNotEmpty() && MetricKey.TRUNK in report.available) {
            val range = swinging.maxOf { it.trunkRangeDeg ?: 0.0 }
            add(
                out, ObservationCodes.ROW_SWING, ObservationText.trunkSwing(swinging.map { it.index }, range),
                swinging, swinging.first().phases.departUs, range, range, t.rowCableSwingDeg,
            )
        }
    }

    // ------------------------------------------------------------------------------------------------------------
    // Curl y tríceps
    // ------------------------------------------------------------------------------------------------------------

    /** Las repeticiones del brazo que mide el lado cercano: las del lado cercano o las que no distinguen lado. */
    private fun nearArmReps(): List<RepReport> {
        val nearName = report.nearSide
        return full.filter { it.side == null || it.side == nearName }
    }

    fun arms(out: MutableList<Candidate>) {
        if (view != ViewKind.SIDE) return
        val mine = nearArmReps()
        // Balanceo del tronco ≥ 10° en una repetición.
        val swinging = mine.filter { (it.trunkRangeDeg ?: 0.0) >= t.armTrunkSwingDeg }
        if (swinging.isNotEmpty() && MetricKey.TRUNK in report.available) {
            val range = swinging.maxOf { it.trunkRangeDeg ?: 0.0 }
            add(
                out, ObservationCodes.ARM_TRUNK_SWING, ObservationText.trunkSwing(swinging.map { it.index }, range),
                swinging, swinging.first().phases.departUs, range, range, t.armTrunkSwingDeg,
            )
        }
        armElbowDrift(out, mine)
        if (profile.id == AnalysisProfileId.CURL) curlExtension(out, mine)
    }

    /** Deriva del codo: θ(cadera, hombro, codo) cambia ≥ 25° (curl) o ≥ 20° (tríceps) dentro de una repetición. */
    private fun armElbowDrift(out: MutableList<Candidate>, mine: List<RepReport>) {
        val side = near ?: return
        val drift = included("hse", side.hip(), side.shoulder(), side.elbow()) ?: return
        val limit = if (profile.id == AnalysisProfileId.CURL) t.curlElbowDriftDeg else t.tricepsElbowDriftDeg
        val flagged = ArrayList<RepReport>()
        val ranges = ArrayList<Double>()
        for (rep in mine) {
            val w = window(drift, rep.phases.departUs, rep.phases.arriveUs, 0.0) ?: continue
            val high = SeriesMath.maxIn(drift.v, w.first, w.last)
            val low = SeriesMath.minIn(drift.v, w.first, w.last)
            if (high.isNaN() || low.isNaN() || high - low < limit) continue
            flagged.add(rep)
            ranges.add(high - low)
        }
        if (flagged.isEmpty()) return
        val range = ranges.maxOrNull() ?: return
        add(
            out, ObservationCodes.ARM_ELBOW_DRIFT, ObservationText.elbowDrift(flagged.map { it.index }, range),
            flagged, flagged.first().phases.departUs, range, range, limit,
        )
    }

    /** Curl: θE máximo abajo < 150° (el brazo no llega a extenderse). */
    private fun curlExtension(out: MutableList<Candidate>, mine: List<RepReport>) {
        val elbow = angle(RomAngle.ELBOW) ?: return
        val smooth = smoothed(elbow)
        val flagged = ArrayList<RepReport>()
        val values = ArrayList<Double>()
        for (rep in mine) {
            val w = window(elbow, rep.phases.departUs, rep.phases.arriveUs, t.repMarginS) ?: continue
            val flexion = SeriesMath.minIn(smooth, w.first, w.last)
            if (flexion.isNaN()) continue
            val theta = STRAIGHT - flexion
            if (theta >= t.curlMinExtensionDeg) continue
            flagged.add(rep)
            values.add(theta)
        }
        if (flagged.isEmpty()) return
        val shortfall = t.curlMinExtensionDeg - (values.minOrNull() ?: t.curlMinExtensionDeg)
        add(
            out, ObservationCodes.CURL_EXTENSION, ObservationText.curlExtension(flagged.map { it.index }, values.average(), t.curlMinExtensionDeg),
            flagged, flagged.first().phases.departUs, shortfall, shortfall, LOCKOUT_SCALE_DEG,
        )
    }

    // ------------------------------------------------------------------------------------------------------------
    // Elevaciones
    // ------------------------------------------------------------------------------------------------------------

    /** Impulso: rango de τ ≥ 8° o flexión de rodilla ≥ 15° dentro de una repetición. (La asimetría izquierda-derecha no entra en v1.) */
    fun raise(out: MutableList<Candidate>) {
        if (view != ViewKind.SIDE) return
        val knee = angle(RomAngle.KNEE)
        val flagged = ArrayList<RepReport>()
        val trunkValues = ArrayList<Double>()
        val kneeValues = ArrayList<Double>()
        for (rep in full) {
            val trunkRange = rep.trunkRangeDeg
            var kneeRange = Double.NaN
            if (knee != null) {
                val w = window(knee, rep.phases.departUs, rep.phases.arriveUs, 0.0)
                if (w != null) {
                    val high = SeriesMath.maxIn(knee.v, w.first, w.last)
                    val low = SeriesMath.minIn(knee.v, w.first, w.last)
                    if (!high.isNaN() && !low.isNaN()) kneeRange = high - low
                }
            }
            val trunkOver = trunkRange != null && trunkRange >= t.raiseMomentumTrunkDeg && MetricKey.TRUNK in report.available
            val kneeOver = !kneeRange.isNaN() && kneeRange >= t.raiseMomentumKneeDeg
            if (!trunkOver && !kneeOver) continue
            flagged.add(rep)
            if (trunkRange != null && trunkOver) trunkValues.add(trunkRange)
            if (kneeOver) kneeValues.add(kneeRange)
        }
        if (flagged.isEmpty()) return
        val trunkText = trunkValues.takeIf { it.isNotEmpty() }?.maxOrNull()
        val kneeText = kneeValues.takeIf { it.isNotEmpty() }?.maxOrNull()
        val strength = max(
            if (trunkText != null) trunkText / t.raiseMomentumTrunkDeg else 0.0,
            if (kneeText != null) kneeText / t.raiseMomentumKneeDeg else 0.0,
        )
        add(
            out, ObservationCodes.RAISE_MOMENTUM, ObservationText.raiseMomentum(flagged.map { it.index }, trunkText, kneeText),
            flagged, flagged.first().phases.departUs, strength, strength, 1.0,
        )
    }

    // ------------------------------------------------------------------------------------------------------------
    // Velocidad de la barra para el rebote
    // ------------------------------------------------------------------------------------------------------------

    /** |v| de [x] en el índice fraccionario [index] (derivada central), en unidades por segundo; NaN si falta. */
    private fun speedAt(x: DoubleArray, index: Double, fs: Double): Double {
        val k = index.roundToInt()
        if (k < 1 || k > x.size - 2) return Double.NaN
        val a = x[k + 1]
        val b = x[k - 1]
        return if (a.isNaN() || b.isNaN()) Double.NaN else abs(a - b) * fs / 2.0
    }

    /** El máximo de |v| de [x] entre los índices fraccionarios [from] y [to]. */
    private fun peakSpeed(x: DoubleArray, from: Double, to: Double, fs: Double): Double {
        var best = Double.NaN
        for (k in max(1, ceil(from).toInt())..min(x.size - 2, floor(to).toInt())) {
            val v = speedAt(x, k.toDouble(), fs)
            if (!v.isNaN() && (best.isNaN() || v > best)) best = v
        }
        return best
    }

    private companion object {
        const val STRAIGHT = 180.0
        const val MICROS = 1_000_000.0
        const val TREND_REPS = 2
        const val IRREGULAR_MIN_REPS = 3
        const val MIN_WINDOW_SAMPLES = 4
        const val STYLE_MIN_REPS = 3
        const val PAUSE_MIN_REPS = 3
        const val PULL_MIN_REPS = 3

        /** La subida del hombro en el primer 30 % de la CON debe pasar de esta fracción de la altura corporal para medir una razón. */
        const val MIN_RISE_BODY = 0.01

        /** El recorrido de la barra en el peso muerto debe pasar de esto (BH) para medir su primer 15 %. */
        const val MIN_BAR_RISE_BODY = 0.05

        /** Escala (°) con la que se compara un bloqueo que no llega: a 5° de distancia, la fuerza vale 1. */
        const val LOCKOUT_SCALE_DEG = 5.0

        /** Escala (°) de una flexión corta. */
        const val PUSHUP_SCALE_DEG = 10.0

        const val BOUNCE_PROBE_S = 0.05
        const val BOUNCE_SPEED_FRACTION = 0.25
        const val KNEE_DRIVE_WINDOW_S = 0.6

        /** Aviso de `setHints` que dice que el press es estricto (aún no lo manda nadie). */
        const val STRICT_PRESS = "STRICT_PRESS"
    }
}
