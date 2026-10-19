package com.example.kpkn.domain.biomechanics.metrics

import com.example.kpkn.domain.biomechanics.bar.BarTrack
import com.example.kpkn.domain.biomechanics.reps.CountSeriesBuilder
import com.example.kpkn.domain.biomechanics.vbt.PlateVerticalSeries

/**
 * Uncalibrated plate velocity on the same physical PTS as native plate phases.
 * It must not use CountSeries.barY, which may have been shifted toward the pose clock.
 * No physical diameter is assumed: units remain BH/s and publication uses existing VL guards.
 */
object NativePlateRelativeVelocity {
    fun compute(ctx: MetricContext, bar: BarTrack): VelocitySummary {
        val n = ctx.cycles.size
        fun none(error: Double? = null) = VelocitySummary(false, false, null,
            List(n) { RepVelocity.NONE }, null, null, null, null, null, error, false)
        if (n == 0 || !ctx.geometryOk || ctx.phases.reps.isEmpty() ||
            ctx.series.source != CountSeriesBuilder.SOURCE_PLATE || ctx.view !in ctx.profile.countViews) return none()
        val bodyHeight = ctx.bodyHeightFh ?: return none()
        if (!bodyHeight.isFinite() || !(bodyHeight > 0.0) || bar.frameHeight <= 0 || bar.frameWidth <= 0 ||
            !ctx.prepared.geo.rollDeg.isFinite()) return none()
        val tuning = ctx.tuning
        val plate = PlateVerticalSeries.of(bar, ctx.prepared.geo.rollDeg, tuning) ?: return none()
        if (plate.fs < tuning.lowFpsBelow) return none()
        val bhPerPx = 1.0 / (bar.frameHeight * bodyHeight)
        if (!bhPerPx.isFinite() || !(bhPerPx > 0.0)) return none()
        val reps = ArrayList<RepVelocity>(n)
        val fullMcv = ArrayList<Double>()
        val windows = ArrayList<Double>()
        val errors = ArrayList<Double>()
        for (cycle in ctx.cycles) {
            val span = if (cycle.kind == RepKind.FAILED) null else ctx.concentric(cycle)
            val mcv = span?.let { plate.meanSpeed(it.fromUs, it.toUs, bhPerPx, tuning.metrics.minConcentricS) }
            if (span == null || mcv == null || !mcv.isFinite()) {
                reps.add(RepVelocity.NONE)
                continue
            }
            reps.add(RepVelocity(mcv, Double.NaN))
            if (cycle.kind == RepKind.FULL) {
                fullMcv.add(mcv)
                val seconds = (span.toUs - span.fromUs) / 1_000_000.0
                windows.add(VelocityWindow.speed(mcv * seconds, seconds, tuning.vbt.vlWindowLow, tuning.vbt.vlWindowHigh))
                val sigma = VelocityLoss.fitNoise(plate.raw, plate.indexOf(span.fromUs), plate.indexOf(span.toUs),
                    plate.fs, tuning.vbt.vlNoiseMarginS)
                val error = VelocityLoss.durationError(sigma, mcv * seconds / bhPerPx, plate.fs, seconds, tuning)
                if (error != null) errors.add(error)
            }
        }
        if (fullMcv.isEmpty()) return none()
        val loss = VelocityLoss.of(windows, errors.takeIf { it.isNotEmpty() }?.let { SeriesMath.median(it) }, tuning)
        if (!loss.reliable) return none(loss.durationError)
        return VelocitySummary(available = true, peakAvailable = false, unit = VelocityUnit.BODY_HEIGHTS_PER_S,
            reps = reps, first = fullMcv.first(), best = fullMcv.max(), last = fullMcv.last(), peakBest = null,
            lossPct = loss.lossPct, durationError = loss.durationError, lossReliable = true)
    }
}
