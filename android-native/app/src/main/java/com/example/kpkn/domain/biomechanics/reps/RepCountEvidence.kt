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
    // M4: neither can a cycle the video cut while it still moved (discarded as `censored`) at either edge.
    val censoredBefore = seg.discarded.any { it.reason == "censored" && it.peak < seg.cycles.first().peak }
    val censoredAfter = seg.discarded.any { it.reason == "censored" && it.peak > seg.cycles.last().peak }
    val censoredStart = censoredBefore || initialFarEndReturn(series, seg, fulls, window, cutoff, tuning) ||
        (startLevel > seg.lRest + tuning.partialFraction * seg.aFull &&
            seg.discarded.any { it.reason == "setup" && it.peak < seg.cycles.first().peak })
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
        seg.ambiguousCycles == 0 && !censoredStart && returned && !censoredAfter &&
        series.r.indices.all { series.r[it].isFinite() && !series.filled[it] && !series.weak[it] } &&
        resting(0, window) && resting(series.size - window, series.size)
    return RepCountEvidence(censoredStart, !returned || censoredAfter, closedShort)
}

/**
 * La señal ya puede estar en el giro lejano al empezar el vídeo y volver al reposo antes de la primera repetición cerrada.
 * Esa vuelta observada puede ser la segunda mitad de una repetición, pero no demuestra que su ida ocurriera dentro del vídeo.
 * No se añade una FULL ni se inventan fases: se impide publicar como seguro el número de ciclos interiores.
 * Se exige una vuelta de amplitud completa, datos válidos, al menos dos ciclos interiores y una transición sin la pausa larga
 * de una preparación/dejada. Dos ciclos permiten medir un intervalo interior de la primera mini-serie; la adyacencia
 * de un periodo compara esa espera con la cadencia observada. El límite provisional está en [AnalysisTuning] y requiere
 * selección con los folds de ajuste antes de fijar el valor de producto.
 */
private fun initialFarEndReturn(
    series: CountSeries,
    seg: SegmentationResult,
    fulls: List<RepCycle>,
    window: Int,
    cutoff: Double,
    tuning: AnalysisTuning,
): Boolean {
    if (fulls.size < 2) return false
    val maxPeriods = tuning.metrics.countReliability.initialBoundaryMaxPeriods
    if (!maxPeriods.isFinite() || maxPeriods <= 0.0) return false
    // SINGLE/BOTH_SYNC pueden elegir un lado distinto de series.r; ALTERNATING mezcla ambos lados.
    val signal = when {
        seg.sideMode == SideMode.ALTERNATING -> return false
        seg.selectedSignalSide != null -> series.perSide?.get(seg.selectedSignalSide) ?: return false
        seg.sideMode == SideMode.SINGLE ||
            (seg.sideMode == SideMode.BOTH_SYNC && seg.perSideCounts.isNotEmpty()) -> return false
        else -> series.r
    }
    if (signal.size != series.size) return false
    val raw = if (seg.polarityFlipped) DoubleArray(signal.size) { -signal[it] } else signal
    val rd = ZeroPhaseLowPass.filter(raw, series.fs, cutoff)
    val startLevel = RobustStats.median(raw.copyOfRange(0, window))
    if (!startLevel.isFinite()) return false
    val first = fulls.first()
    val valley = first.leftValley
    if (valley <= window || valley >= first.peak || first.peak >= rd.size) return false
    if ((0..valley).any { !raw[it].isFinite() || series.filled[it] || series.weak[it] }) return false
    if (!(raw[window - 1] < raw[0])) return false
    if (!(startLevel - rd[valley] >= tuning.fullFraction * seg.aFull)) return false
    val restLevel = rd[valley] + tuning.partialFraction * seg.aFull
    val firstRest = (0..valley).firstOrNull { rd[it].isFinite() && rd[it] <= restLevel } ?: return false
    val firstMiniSet = fulls.takeWhile { it.miniSet == first.miniSet }
    if (firstMiniSet.size < 2) return false
    val gaps = DoubleArray(firstMiniSet.size - 1) { (firstMiniSet[it + 1].peak - firstMiniSet[it].peak).toDouble() }
    val period = RobustStats.median(gaps)
    return period.isFinite() && period > 0.0 && first.peak - firstRest <= maxPeriods * period
}
