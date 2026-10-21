package com.example.kpkn.domain.biomechanics.golden

import com.example.kpkn.domain.biomechanics.kinematics.Side
import com.example.kpkn.domain.biomechanics.kinematics.ViewKind
import com.example.kpkn.domain.biomechanics.kinematics.elbow
import com.example.kpkn.domain.biomechanics.kinematics.ankle
import com.example.kpkn.domain.biomechanics.kinematics.hip
import com.example.kpkn.domain.biomechanics.kinematics.knee
import com.example.kpkn.domain.biomechanics.kinematics.shoulder
import com.example.kpkn.domain.biomechanics.kinematics.wrist
import com.example.kpkn.domain.biomechanics.metrics.AnalysisTuning
import com.example.kpkn.domain.biomechanics.metrics.CountReliabilityTuning
import com.example.kpkn.domain.biomechanics.pose.PoseLandmark
import com.example.kpkn.domain.biomechanics.profiles.AnalysisProfile
import com.example.kpkn.domain.biomechanics.profiles.AnalysisProfileId
import com.example.kpkn.domain.biomechanics.profiles.CountSignal
import com.example.kpkn.domain.biomechanics.reps.CountSeries
import com.example.kpkn.domain.biomechanics.reps.CountSeriesBuilder
import com.example.kpkn.domain.biomechanics.reps.CountSeriesResult
import com.example.kpkn.domain.biomechanics.reps.PreparedTrack
import com.example.kpkn.domain.biomechanics.reps.RepConfidence
import com.example.kpkn.domain.biomechanics.reps.RepFlags
import com.example.kpkn.domain.biomechanics.reps.RepSegmenter
import com.example.kpkn.domain.biomechanics.reps.SegmentationResult
import com.example.kpkn.domain.biomechanics.reps.SignalUnit
import com.example.kpkn.domain.biomechanics.signal.GapFiller
import com.example.kpkn.domain.biomechanics.signal.Hampel
import com.example.kpkn.domain.biomechanics.signal.Resampler
import com.example.kpkn.domain.biomechanics.signal.RobustStats
import com.example.kpkn.domain.biomechanics.signal.Timebase
import kotlin.math.abs
import kotlin.math.acos
import kotlin.math.max
import kotlin.math.roundToInt
import kotlin.math.sqrt
import kotlinx.serialization.json.*

/**
 * M3, solo diagnóstico (no cambia el recorte, la verdad, las predicciones ni las métricas del evaluador):
 * - la traza del segmentador y los ciclos dudosos uno a uno, con su tipo y su altura;
 * - la confianza con la regla de las dudas que conservan el número (altura, hueco, descartes admitidos y penalización);
 * - el reposo en los dos extremos de la serie con varias ventanas y bandas (series de 1–2 repeticiones);
 * - D42.1: series de conteo con los puntos «world» (ángulos 3D y altura muñeca–hombro), con controles de visibilidad, de
 *   estabilidad de los segmentos y de coherencia con la imagen, segmentadas y puntuadas con el motor.
 */
internal fun fullBankM3Diagnostics(prepared: PreparedTrack, built: CountSeriesResult, profile: AnalysisProfile): JsonObject =
    buildJsonObject {
        val tuning = AnalysisTuning.DEFAULT
        if (built is CountSeriesResult.Ok) {
            val series = built.series
            val lines = ArrayList<String>()
            val previous = RepSegmenter.trace
            RepSegmenter.trace = { lines.add(it) }
            try {
                RepSegmenter.segment(series, profile, emptySet(), tuning)
            } finally {
                RepSegmenter.trace = previous
            }
            put("trace", JsonArray(lines.map(::JsonPrimitive)))
            val seg = RepSegmenter.segment(series, profile, emptySet(), tuning, prepared)
            put("ambiguity", JsonArray(seg.ambiguity.map {
                buildJsonObject { put("kind", it.kind.name); put("height", it.height); put("gapS", it.gapS) }
            }))
            val flags = RepFlags.of(prepared, series, seg)
            fun scored(reliability: CountReliabilityTuning): JsonObject {
                val c = RepConfidence.score(series, seg, prepared.view, profile, flags,
                    tuning.copy(metrics = tuning.metrics.copy(countReliability = reliability)))
                return buildJsonObject {
                    put("level", c.level.name); put("score", c.score)
                    put("forced", c.forcedLowReason?.let(::JsonPrimitive) ?: JsonNull)
                }
            }
            // Dudas que conservan el número: altura, hueco, descartes admitidos y penalización (M3).
            val discardSets = linkedMapOf<String, Set<String>?>(
                "none" to setOf("noise"), "edge" to setOf("noise", "setdown", "rerack"), "all" to null)
            put("preservingAmbiguity", buildJsonObject {
                put("off", scored(tuning.metrics.countReliability.copy(preservingAmbiguityHeight = null)))
                for (height in listOf(0.3, 0.5)) for (gap in listOf(0.1, 0.15, 0.2, 0.25, 0.3))
                    for ((name, discards) in discardSets) for (penalized in listOf(true, false)) {
                        put("$height/$gap/$name/$penalized", scored(tuning.metrics.countReliability.copy(
                            preservingAmbiguityHeight = height, preservingAmbiguityGapS = gap,
                            preservingAmbiguityDiscards = discards, preservingAmbiguityPenalized = penalized)))
                    }
            })
            put("restEdges", restEdges(series, seg))
        }
        val angle = angleOf(profile.poseSignal)
        if (profile.id == AnalysisProfileId.BENCH ||
            (angle != null && prepared.view.kind in setOf(ViewKind.OBLIQUE, ViewKind.LYING))
        ) {
            put("world", worldCandidates(prepared, profile, tuning))
        }
    }

/**
 * Reposo al principio y al final de la serie: para cada ventana (s) y banda (fracción de Afull), [reposo al principio,
 * reposo al final, nivel al principio, nivel al final]. «Reposo» es como en `countEvidence`: todas las muestras medidas
 * (ni rellenas ni débiles), mediana ≤ Lrest + banda y variación ≤ banda; «nivel» solo pide la mediana.
 */
private fun restEdges(series: CountSeries, seg: SegmentationResult): JsonObject = buildJsonObject {
    if (!(seg.aFull > 0.0) || !(series.fs > 0.0)) return@buildJsonObject
    val raw = if (seg.polarityFlipped) DoubleArray(series.size) { -series.r[it] } else series.r
    fun window(from: Int, to: Int, band: Double): Pair<Boolean, Boolean> {
        if (to <= from) return false to false
        val values = raw.copyOfRange(from, to)
        val finite = values.filter { it.isFinite() }
        if (finite.isEmpty()) return false to false
        val median = RobustStats.median(finite.toDoubleArray())
        val levelOk = median <= seg.lRest + band * seg.aFull
        var observed = true
        for (i in from until to) if (!raw[i].isFinite() || series.filled[i] || series.weak[i]) observed = false
        val flat = (finite.max() - finite.min()) <= band * seg.aFull
        return (observed && levelOk && flat) to levelOk
    }
    for (seconds in listOf(0.12, 0.25, 0.40)) for (band in listOf(0.05, 0.10, 0.20, 0.30)) {
        val w = max(1, (seconds * series.fs).roundToInt()).coerceAtMost(series.size)
        val start = window(0, w, band)
        val end = window(series.size - w, series.size, band)
        put("$seconds/$band", JsonArray(listOf(start.first, end.first, start.second, end.second).map(::JsonPrimitive)))
    }
}

/** Ángulo de la señal de conteo del perfil: (punto a, vértice, punto c, en flexión 180 − θ). `null` si no es un ángulo. */
private data class AngleSpec(val a: (Side) -> PoseLandmark, val b: (Side) -> PoseLandmark, val c: (Side) -> PoseLandmark, val flexion: Boolean)

private fun angleOf(signal: CountSignal): AngleSpec? = when (signal) {
    CountSignal.KNEE_FLEXION -> AngleSpec({ it.hip() }, { it.knee() }, { it.ankle() }, true)
    CountSignal.KNEE_EXTENSION_ANGLE -> AngleSpec({ it.hip() }, { it.knee() }, { it.ankle() }, false)
    CountSignal.HIP_FLEXION -> AngleSpec({ it.shoulder() }, { it.hip() }, { it.knee() }, true)
    CountSignal.HIP_ANGLE -> AngleSpec({ it.shoulder() }, { it.hip() }, { it.knee() }, false)
    CountSignal.ELBOW_FLEXION -> AngleSpec({ it.shoulder() }, { it.elbow() }, { it.wrist() }, true)
    CountSignal.ELBOW_ANGLE -> AngleSpec({ it.shoulder() }, { it.elbow() }, { it.wrist() }, false)
    else -> null
}

private val ELBOW_FLEXION = AngleSpec({ it.shoulder() }, { it.elbow() }, { it.wrist() }, true)

/** Talla supuesta para pasar metros «world» a alturas corporales (solo diagnóstico). */
private const val ASSUMED_HEIGHT_M = 1.70

/** Tolerancia de la longitud de cada segmento «world» respecto de su mediana en la serie. */
private const val SEGMENT_TOLERANCE = 0.25

private fun worldCandidates(prepared: PreparedTrack, profile: AnalysisProfile, tuning: AnalysisTuning): JsonObject = buildJsonObject {
    val near = prepared.near?.near ?: Side.LEFT
    val bench = profile.id == AnalysisProfileId.BENCH
    val spec = if (bench) ELBOW_FLEXION else angleOf(profile.poseSignal) ?: return@buildJsonObject
    for (side in listOf(near, near.opposite)) {
        val tag = if (side == near) "near" else "far"
        put("$tag/angle3d", worldSeries(prepared, profile, tuning, side, spec, wristDrop = false, bench = bench))
        if (bench) put("$tag/wristDrop", worldSeries(prepared, profile, tuning, side, spec, wristDrop = true, bench = true))
    }
}

private fun angle3(ax: Double, ay: Double, az: Double, bx: Double, by: Double, bz: Double, cx: Double, cy: Double, cz: Double): Double {
    val ux = ax - bx; val uy = ay - by; val uz = az - bz
    val vx = cx - bx; val vy = cy - by; val vz = cz - bz
    val nu = sqrt(ux * ux + uy * uy + uz * uz); val nv = sqrt(vx * vx + vy * vy + vz * vz)
    if (!(nu > 0.0) || !(nv > 0.0)) return Double.NaN
    return Math.toDegrees(acos(((ux * vx + uy * vy + uz * vz) / (nu * nv)).coerceIn(-1.0, 1.0)))
}

private fun worldSeries(
    prepared: PreparedTrack,
    profile: AnalysisProfile,
    tuning: AnalysisTuning,
    side: Side,
    spec: AngleSpec,
    wristDrop: Boolean,
    bench: Boolean,
): JsonObject = buildJsonObject {
    val frames = prepared.track.frames
    val n = frames.size
    val a = spec.a(side); val b = spec.b(side); val c = spec.c(side)
    val lenAb = DoubleArray(n) { Double.NaN }
    val lenBc = DoubleArray(n) { Double.NaN }
    val usable = BooleanArray(n)
    val weakFrame = BooleanArray(n)
    for (i in 0 until n) {
        val f = frames[i]
        if (!f.hasPose) continue
        val pts = listOf(a, b, c)
        if (pts.any { !(f.vis(it) >= 0.3f) || f.worldX(it).isNaN() }) continue
        usable[i] = true
        weakFrame[i] = pts.any { !(f.vis(it) >= tuning.minVisibility && f.pres(it) >= tuning.minPresence) }
        fun len(p: PoseLandmark, q: PoseLandmark): Double {
            val dx = (f.worldX(p) - f.worldX(q)).toDouble(); val dy = (f.worldY(p) - f.worldY(q)).toDouble()
            val dz = (f.worldZ(p) - f.worldZ(q)).toDouble()
            return sqrt(dx * dx + dy * dy + dz * dz)
        }
        lenAb[i] = len(a, b); lenBc[i] = len(b, c)
    }
    val medAb = RobustStats.median(lenAb)
    val medBc = RobustStats.median(lenBc)
    put("lengthCvA", RobustStats.cv(lenAb)); put("lengthCvB", RobustStats.cv(lenBc))
    val raw = DoubleArray(n) { Double.NaN }
    val image = DoubleArray(n) { Double.NaN }
    var stable = 0
    for (i in 0 until n) {
        if (!usable[i]) continue
        if (!(medAb > 0.0) || !(medBc > 0.0)) continue
        if (abs(lenAb[i] / medAb - 1.0) > SEGMENT_TOLERANCE || abs(lenBc[i] / medBc - 1.0) > SEGMENT_TOLERANCE) continue
        stable++
        val f = frames[i]
        raw[i] = if (wristDrop) {
            (f.worldY(side.wrist()) - f.worldY(side.shoulder())).toDouble() / ASSUMED_HEIGHT_M
        } else {
            val th = angle3(f.worldX(a).toDouble(), f.worldY(a).toDouble(), f.worldZ(a).toDouble(),
                f.worldX(b).toDouble(), f.worldY(b).toDouble(), f.worldZ(b).toDouble(),
                f.worldX(c).toDouble(), f.worldY(c).toDouble(), f.worldZ(c).toDouble())
            if (spec.flexion) 180.0 - th else th
        }
        // La misma magnitud en la imagen (2D), para la coherencia.
        image[i] = if (wristDrop) {
            prepared.geo.point(i, side.wrist(), weak = true)?.let { w -> prepared.geo.point(i, side.shoulder(), weak = true)?.let { s -> w.y - s.y } } ?: Double.NaN
        } else {
            val pa = prepared.geo.point(i, a, weak = true); val pb = prepared.geo.point(i, b, weak = true); val pc = prepared.geo.point(i, c, weak = true)
            if (pa == null || pb == null || pc == null) Double.NaN else {
                val th = com.example.kpkn.domain.biomechanics.kinematics.Angles.included(pa, pb, pc)
                if (spec.flexion) 180.0 - th else th
            }
        }
    }
    put("usableFrames", usable.count { it }); put("stableFrames", stable)
    put("posedFrames", frames.count { it.hasPose })
    put("coherence", pearson(raw, image))
    val tb = prepared.timebase
    if (tb.n < 2 || !(tb.fs > 0.0)) return@buildJsonObject
    val times = LongArray(n) { frames[it].tUs }
    val timebase = Timebase(tb.t0Us, tb.fs, tb.n, false, 1.0)
    val half = max(tuning.hampelMinHalfWindow, (tuning.hampelWindowFraction * tb.fs).roundToInt())
    val clean = Hampel.filter(raw, half, tuning.hampelSigmas)
    val weakNum = DoubleArray(n) { if (clean[it].isNaN()) Double.NaN else if (weakFrame[it]) 1.0 else 0.0 }
    val grid = Resampler.toUniform(times, clean, timebase, tuning.fillCountGapS)
    val weakGrid = Resampler.toUniform(times, weakNum, timebase, tuning.fillCountGapS)
    val fill = GapFiller.fill(grid, tb.fs, tuning.fillCountGapS)
    val weak = BooleanArray(grid.size) { !grid[it].isNaN() && !weakGrid[it].isNaN() && weakGrid[it] > 0.0 }
    var first = -1; var last = -1; var observed = 0; var valid = 0
    for (k in fill.values.indices) {
        if (fill.values[k].isNaN()) continue
        valid++
        if (first < 0) first = k
        last = k
        if (!fill.filled[k] && !weak[k]) observed++
    }
    val span = if (first < 0) 0 else last - first + 1
    val validFraction = if (span > 0) observed.toDouble() / span else 0.0
    put("validFraction", validFraction)
    if (valid < tuning.minObservedS * tb.fs) {
        put("level", "SIN_SALIDA"); put("reason", "no_valid_signal")
        return@buildJsonObject
    }
    val unit = if (wristDrop) SignalUnit.BODY_HEIGHTS else SignalUnit.DEGREES
    val series = CountSeries(tb.t0Us, tb.fs, fill.values, fill.filled, weak, BooleanArray(grid.size), null, null, null,
        CountSeriesBuilder.SOURCE_POSE, unit, validFraction, null)
    val variant = when {
        bench && wristDrop -> profile.copy(poseSignal = CountSignal.DROP_HAND_NEAR, minAmplitude = profile.minAmplitude)
        bench -> profile.copy(poseSignal = CountSignal.ELBOW_FLEXION, minAmplitude = 35.0)
        else -> profile
    }
    val seg = RepSegmenter.segment(series, variant, emptySet(), tuning, prepared)
    val conf = RepConfidence.score(series, seg, prepared.view, variant, RepFlags.of(prepared, series, seg), tuning)
    val hasReps = seg.cycles.isNotEmpty()
    put("full", seg.fullCount); put("partial", seg.partialCount); put("failed", seg.failedCount)
    put("level", if (hasReps) conf.level.name else "SIN_SALIDA")
    put("score", conf.score); put("forced", conf.forcedLowReason?.let(::JsonPrimitive) ?: JsonNull)
    put("reason", seg.noRepsReason?.let(::JsonPrimitive) ?: JsonNull)
    put("nAmb", seg.ambiguousCycles); put("aFull", seg.aFull)
}

private fun pearson(x: DoubleArray, y: DoubleArray): Double {
    var n = 0; var sx = 0.0; var sy = 0.0
    for (i in x.indices) if (x[i].isFinite() && y[i].isFinite()) { n++; sx += x[i]; sy += y[i] }
    if (n < 5) return Double.NaN
    val mx = sx / n; val my = sy / n
    var sxx = 0.0; var syy = 0.0; var sxy = 0.0
    for (i in x.indices) if (x[i].isFinite() && y[i].isFinite()) {
        sxx += (x[i] - mx) * (x[i] - mx); syy += (y[i] - my) * (y[i] - my); sxy += (x[i] - mx) * (y[i] - my)
    }
    val d = sqrt(sxx * syy)
    return if (d > 0.0) sxy / d else Double.NaN
}
