package com.example.kpkn.domain.biomechanics.reps

import com.example.kpkn.domain.biomechanics.bar.BarTrack
import com.example.kpkn.domain.biomechanics.metrics.AnalysisTuning
import com.example.kpkn.domain.biomechanics.profiles.AnalysisProfile
import com.example.kpkn.domain.biomechanics.profiles.CountSignal
import com.example.kpkn.domain.biomechanics.signal.Resampler
import com.example.kpkn.domain.biomechanics.signal.ZeroPhaseLowPass
import com.example.kpkn.domain.biomechanics.vbt.PlateVerticalSeries
import kotlin.math.ceil
import kotlin.math.floor
import kotlin.math.max
import kotlin.math.roundToInt

/**
 * Refines ONLY the events of existing cycles on the plate clock. CountSeries, RepCycle,
 * PreparedTrack and every geometry array retain their pose indices. The temporary native
 * series and mapped cycles never escape this object; only cycle identity + absolute PTS do.
 *
 * Conservative first implementation: if any cycle cannot be fitted on a continuous measured
 * plate interval, the entire phase summary abstains. It never returns partial set TUT/tempo
 * as if they described the whole set. Predicted/pose-prior positions are not measurements.
 */
object NativePlatePhases {
    fun compute(
        series: CountSeries,
        seg: SegmentationResult,
        profile: AnalysisProfile,
        bar: BarTrack?,
        rollDeg: Double,
        tuning: AnalysisTuning = AnalysisTuning.DEFAULT,
    ): PhaseSummary {
        if (series.source != CountSeriesBuilder.SOURCE_PLATE) {
            return RepPhases.compute(series, seg, profile, tuning)
        }
        val empty = PhaseSummary(emptyList(), null, null, null, null)
        if (bar == null || bar.frameHeight <= 0 || bar.frameWidth <= 0 || !rollDeg.isFinite() || seg.cycles.isEmpty()) return empty
        val sign = when (profile.plateSignal) {
            CountSignal.PLATE_DROP -> 1.0
            CountSignal.PLATE_RISE -> -1.0
            else -> return empty
        } * if (seg.polarityFlipped) -1.0 else 1.0
        val samples = bar.samples
        if (samples.size < 2 || samples.zipWithNext().any { (a, b) -> b.tUs <= a.tUs }) return empty
        val times = LongArray(samples.size) { samples[it].tUs }
        val tb = Resampler.timebase(times)
        if (!tb.fs.isFinite() || tb.fs < tuning.lowFpsBelow || tb.n < 2) return empty

        // Share the exact native clock/support mask with both velocity estimators. Its raw
        // array already corrects gravity and preserves long absent-PTS/detector gaps as NaN.
        // Normalize to dimensionless Y/FH; never infer a metric scale or write a BarSample.
        val vertical = PlateVerticalSeries.of(bar, rollDeg, tuning) ?: return empty
        val nativeValues = DoubleArray(vertical.raw.size) { sign * vertical.raw[it] / bar.frameHeight }
        val fc = if (tb.fs >= tuning.plateFastMinFps) tuning.plateLowPassHzFast else tuning.phaseLowPassHz
        val smooth = ZeroPhaseLowPass.filter(nativeValues, tb.fs, fc)
        val half = max(1, (tuning.pieceLevelWindowS * tb.fs).roundToInt())

        fun nativeIndex(poseIndex: Int): Int? {
            if (poseIndex !in series.r.indices) return null
            val absoluteUs = series.t0Us + poseIndex * MICROS / series.fs
            val index = (absoluteUs - tb.t0Us) * tb.fs / MICROS
            if (!index.isFinite() || index < 0.0 || index > smooth.lastIndex) return null
            return index.roundToInt()
        }
        fun continuous(a: Int, b: Int): Boolean = a >= 0 && b < smooth.size && b >= a &&
            (a..b).all { nativeValues[it].isFinite() && smooth[it].isFinite() }

        val mapped = ArrayList<RepCycle>(seg.cycles.size)
        for (cycle in seg.cycles) {
            // A missing return or a turn supported only by a count interpolation is not a timed cycle.
            if (cycle.turnInGap) return empty
            val left = nativeIndex(cycle.leftValley) ?: return empty
            val right = nativeIndex(cycle.rightValley ?: return empty) ?: return empty
            if (right <= left + 1) return empty
            if (!continuous(max(0, left - 2 * half), minOf(smooth.lastIndex, right + 2 * half))) return empty
            // The coarse plate series may have been aligned to the hand by plateLag. Refine the
            // turn inside its original valley bracket on NATIVE PTS; never shift the native clock.
            var peak = left + 1
            for (i in left + 2 until right) if (smooth[i] > smooth[peak]) peak = i
            val pieces = MovementPieces.cycle(smooth, left, peak, right, half)
            val rise = pieces.rise ?: return empty
            val fall = pieces.fall ?: return empty
            if (!rise.start.isFinite() || !rise.end.isFinite() ||
                !fall.start.isFinite() || !fall.end.isFinite() ||
                !continuous(floor(rise.start).toInt(), ceil(fall.end).toInt())) return empty
            mapped.add(cycle.copy(leftValley = left, peak = peak, rightValley = right))
        }

        // RepPhases is the existing phase estimator with exactly the same physical thresholds.
        // The carrier is private and never passed to MetricContext or RepConfidence.
        val native = CountSeries(
            t0Us = tb.t0Us, fs = tb.fs, r = nativeValues,
            // Conservative private flags: this resampled carrier is never scored as observed tracking.
            filled = BooleanArray(tb.n) { true }, weak = BooleanArray(tb.n) { true }, walk = BooleanArray(tb.n),
            barX = null, barY = null, perSide = null, source = CountSeriesBuilder.SOURCE_PLATE,
            unit = SignalUnit.RATIO, validFraction = series.validFraction,
        )
        val nativeSeg = seg.copy(cycles = mapped, discarded = emptyList(), activeWindow = null)
        val result = RepPhases.compute(native, nativeSeg, profile, tuning)
        if (result.reps.size != seg.cycles.size) return empty
        for (event in result.reps) {
            val a = (event.departUs - tb.t0Us) * tb.fs / MICROS
            val b = (event.arriveUs - tb.t0Us) * tb.fs / MICROS
            if (!continuous(floor(a).toInt(), ceil(b).toInt())) return empty
        }
        return result
    }

    private const val MICROS = 1_000_000.0
}
