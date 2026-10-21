package com.example.kpkn.domain.biomechanics.reps

import com.example.kpkn.domain.biomechanics.metrics.AnalysisTuning
import com.example.kpkn.domain.biomechanics.metrics.RepKind
import com.example.kpkn.domain.biomechanics.signal.RobustStats
import com.example.kpkn.domain.biomechanics.signal.ZeroPhaseLowPass
import kotlin.math.max
import kotlin.math.roundToInt

/** Evidence missing at a crop boundary is different from variation between observed repetitions. */
internal data class RepCountEvidence(val censoredStart: Boolean, val censoredReturn: Boolean, val closedShortSet: Boolean)

internal fun countEvidence(series: CountSeries, seg: SegmentationResult, tuning: AnalysisTuning): RepCountEvidence {
    val fulls = seg.cycles.filter { it.kind == RepKind.FULL }
    if (fulls.isEmpty() || !(seg.aFull > 0.0) || !(series.fs > 0.0)) return RepCountEvidence(false, false, false)
    val raw = if (seg.polarityFlipped) DoubleArray(series.size) { -series.r[it] } else series.r
    val cutoff = seg.periodS?.let { (tuning.countLowPassCycles / it).coerceIn(tuning.countLowPassMinHz, tuning.countLowPassMaxHz) }
        ?: tuning.acfLowPassHz
    val rd = ZeroPhaseLowPass.filter(raw, series.fs, cutoff)
    val last = fulls.last()
    val right = last.rightValley
    val returned = right != null && last.peak in rd.indices && right in rd.indices &&
        (rd[last.peak] - rd[right]) / seg.aFull >= tuning.fullFraction
    val settings = tuning.metrics.countReliability
    val window = max(1, (settings.boundaryWindowS * series.fs).roundToInt()).coerceAtMost(series.size)
    fun edge(from: Int, to: Int): Double = RobustStats.median(raw.copyOfRange(from, to))
    val startLevel = edge(0, window)
    // A discarded first rise can be an incomplete repetition; it must not silently disappear from confidence.
    val censoredStart = startLevel > seg.lRest + tuning.partialFraction * seg.aFull &&
        seg.discarded.any { it.reason == "setup" && it.peak < seg.cycles.first().peak }
    fun resting(from: Int, to: Int): Boolean {
        if (to <= from) return false
        var lo = Double.POSITIVE_INFINITY
        var hi = Double.NEGATIVE_INFINITY
        for (i in from until to) {
            val v = raw[i]
            if (!v.isFinite() || series.filled[i] || series.weak[i]) return false
            lo = minOf(lo, v); hi = maxOf(hi, v)
        }
        val band = settings.shortRestFraction * seg.aFull
        return edge(from, to) <= seg.lRest + band && hi - lo <= band
    }
    val closedShort = fulls.size < tuning.minCvReps && seg.cycles.all { it.kind == RepKind.FULL && !it.turnInGap } &&
        seg.ambiguousCycles == 0 && !censoredStart && returned &&
        series.r.indices.all { series.r[it].isFinite() && !series.filled[it] && !series.weak[it] } &&
        resting(0, window) && resting(series.size - window, series.size)
    return RepCountEvidence(censoredStart, !returned, closedShort)
}
