package com.example.kpkn.domain.biomechanics.golden

import com.example.kpkn.domain.biomechanics.kinematics.Side
import com.example.kpkn.domain.biomechanics.kinematics.SideEstimate
import com.example.kpkn.domain.biomechanics.metrics.AnalysisTuning
import com.example.kpkn.domain.biomechanics.bar.BarTrack
import com.example.kpkn.domain.biomechanics.pose.PoseLandmark
import com.example.kpkn.domain.biomechanics.profiles.AnalysisProfile
import com.example.kpkn.domain.biomechanics.profiles.AnalysisProfileId
import com.example.kpkn.domain.biomechanics.profiles.CountSignal
import com.example.kpkn.domain.biomechanics.reps.*
import com.example.kpkn.domain.biomechanics.signal.ZeroPhaseLowPass
import kotlinx.serialization.json.*

/** Additive evidence only: never changes FullBank's crop, truth, predictions or metric definitions. */
internal fun fullBankSignalDiagnostics(prepared: PreparedTrack, built: CountSeriesResult, profile: AnalysisProfile,
    bar: BarTrack? = null): JsonObject = buildJsonObject {
    val tuning = AnalysisTuning.DEFAULT
    put("near", prepared.near?.near?.name?.let(::JsonPrimitive) ?: JsonNull)
    put("scaleFh", prepared.scale?.bodyHeightFh?.let(::JsonPrimitive) ?: JsonNull)
    put("viewReliable", prepared.view.reliable)
    val rawSeg = if (built is CountSeriesResult.Ok) RepSegmenter.segment(built.series, profile, emptySet()) else null
    val footWindow = if (built is CountSeriesResult.Ok && rawSeg != null) captureWindowUs(built.series, rawSeg, tuning) else null
    val capture = captureCountEvidence(prepared, profile, tuning, footWindow)
    put("capture", buildJsonObject {
        put("clippedFraction", capture.clippedFraction); put("commonFootTravelBh", capture.commonFootTravelBh)
        put("unsafe", capture.unsafe)
    })
    put("landmarks", buildJsonObject {
        for (lm in PoseLandmark.KEPT_IMAGE) {
            put(lm.name, buildJsonObject {
                put("strong", prepared.track.frames.indices.count { prepared.geo.point(it, lm) != null })
                put("weak", prepared.track.frames.indices.count { prepared.geo.point(it, lm, weak = true) != null })
            })
        }
    })
    fun summary(series: CountSeries, seg: SegmentationResult, variantProfile: AnalysisProfile): JsonObject = buildJsonObject {
        put("full", seg.fullCount); put("partial", seg.partialCount); put("failed", seg.failedCount)
        put("reason", seg.noRepsReason?.let(::JsonPrimitive) ?: JsonNull)
        put("sideMode", seg.sideMode.name); put("nAmb", seg.ambiguousCycles)
        put("aFull", seg.aFull); put("lRest", seg.lRest); put("sigma", seg.noiseSigma)
        put("periodS", seg.periodS?.let(::JsonPrimitive) ?: JsonNull)
        val confidence = RepConfidence.score(series, seg, prepared.view, variantProfile, RepFlags.of(prepared, series, seg))
        put("level", confidence.level.name); put("score", confidence.score)
        put("forcedLowReason", confidence.forcedLowReason?.let(::JsonPrimitive) ?: JsonNull)
        put("validFraction", series.validFraction)
        put("cTrack", confidence.cTrack); put("cSnr", confidence.cSnr); put("cAmp", confidence.cAmp)
        put("cRhy", confidence.cRhy); put("cView", confidence.cView)
    }
    fun array(v: DoubleArray) = JsonArray(v.map { if (it.isFinite()) JsonPrimitive(it) else JsonNull })
    if (built is CountSeriesResult.Ok) {
        val series = built.series
        val seg = RepSegmenter.segment(series, profile, emptySet(), prepared = prepared)
        val cutoff = seg.periodS?.let { (tuning.countLowPassCycles / it).coerceIn(tuning.countLowPassMinHz, tuning.countLowPassMaxHz) } ?: tuning.acfLowPassHz
        put("series", buildJsonObject {
            put("t0Us", series.t0Us); put("fs", series.fs); put("unit", series.unit.name)
            put("r", array(series.r)); put("rd", array(ZeroPhaseLowPass.filter(series.r, series.fs, cutoff)))
            put("filled", JsonArray(series.filled.map(::JsonPrimitive)))
            put("weak", JsonArray(series.weak.map(::JsonPrimitive)))
            put("walk", JsonArray(series.walk.map(::JsonPrimitive)))
            put("summary", summary(series, seg, profile))
            put("unfilteredSummary", summary(series, requireNotNull(rawSeg), profile))
            put("cycles", JsonArray(seg.cycles.map { c -> buildJsonObject {
                put("kind", c.kind.name); put("left", c.leftValley); put("peak", c.peak)
                put("right", c.rightValley?.let(::JsonPrimitive) ?: JsonNull)
                put("a", c.amplitudeFraction); put("turnInGap", c.turnInGap)
                put("side", c.side?.name?.let(::JsonPrimitive) ?: JsonNull)
            } }))
            put("discarded", JsonArray(seg.discarded.map { c -> buildJsonObject { put("peak", c.peak); put("reason", c.reason) } }))
        })
        put("reliabilityCandidates", buildJsonObject {
            for (window in listOf(0.12, 0.25, 0.40)) for (rest in listOf(0.05, 0.10, 0.20, 0.30)) for (amb in 0..1) {
                val variant = tuning.copy(metrics = tuning.metrics.copy(countReliability = tuning.metrics.countReliability.copy(
                    boundaryWindowS = window, shortRestFraction = rest, maxOfferedAmbiguousCycles = amb)))
                val scored = RepConfidence.score(series, requireNotNull(rawSeg), prepared.view, profile,
                    RepFlags.of(prepared, series, rawSeg), variant)
                put("$window/$rest/$amb", buildJsonObject {
                    put("level", scored.level.name); put("score", scored.score)
                    put("forcedLowReason", scored.forcedLowReason?.let(::JsonPrimitive) ?: JsonNull)
                })
            }
        })
    }
    // A duration sweep is diagnostic, not a prediction used by the evaluator.
    put("durationCandidates", buildJsonObject {
        for (seconds in listOf(1.0, 1.5, 2.0, 2.5, 3.0)) {
            val variant = tuning.copy(minObservedS = seconds, minSeriesS = seconds)
            val candidate = CountSeriesBuilder.build(prepared, bar, profile, variant)
            if (candidate is CountSeriesResult.Ok) put(seconds.toString(), summary(candidate.series,
                RepSegmenter.segment(candidate.series, profile, emptySet(), variant, prepared), profile))
            else put(seconds.toString(), buildJsonObject {
                put("full", 0); put("partial", 0); put("failed", 0); put("level", "BAJA")
                put("reason", (candidate as CountSeriesResult.NoSignal).reason)
            })
        }
    })
    if (profile.id == AnalysisProfileId.BENCH) put("benchAngleCandidates", buildJsonObject {
        for (side in Side.entries) {
            val sidePrepared = PreparedTrack(prepared.track, prepared.geo, prepared.timebase, prepared.scale,
                SideEstimate(side, 0.0, "diagnostic"), prepared.facing, prepared.view, prepared.flags)
            for (minimum in listOf(25.0, 35.0, 40.0)) {
                val angleProfile = profile.copy(poseSignal = CountSignal.ELBOW_FLEXION, minAmplitude = minimum)
                val candidate = CountSeriesBuilder.build(sidePrepared, null, angleProfile)
                if (candidate is CountSeriesResult.Ok) put("${side.name}/$minimum", summary(candidate.series,
                    RepSegmenter.segment(candidate.series, angleProfile, emptySet(), prepared = sidePrepared), angleProfile))
            }
        }
    })
}
