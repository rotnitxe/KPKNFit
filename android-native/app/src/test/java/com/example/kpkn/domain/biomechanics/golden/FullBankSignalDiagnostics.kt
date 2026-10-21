package com.example.kpkn.domain.biomechanics.golden

import com.example.kpkn.domain.biomechanics.kinematics.Side
import com.example.kpkn.domain.biomechanics.kinematics.SideEstimate
import com.example.kpkn.domain.biomechanics.metrics.AnalysisFlag
import com.example.kpkn.domain.biomechanics.metrics.AnalysisTuning
import com.example.kpkn.domain.biomechanics.metrics.RepKind
import com.example.kpkn.domain.biomechanics.bar.BarTrack
import com.example.kpkn.domain.biomechanics.pose.PoseLandmark
import com.example.kpkn.domain.biomechanics.profiles.AnalysisProfile
import com.example.kpkn.domain.biomechanics.profiles.AnalysisProfileId
import com.example.kpkn.domain.biomechanics.profiles.CountSignal
import com.example.kpkn.domain.biomechanics.reps.*
import com.example.kpkn.domain.biomechanics.signal.ZeroPhaseLowPass
import kotlin.math.pow
import kotlin.math.sqrt
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
    // M2 iteración 2: fracción de fotogramas con pose con algún punto de cada región fuera de la imagen (0..1).
    put("clippedRegions", clippedRegions(prepared))
    put("landmarks", buildJsonObject {
        for (lm in PoseLandmark.KEPT_IMAGE) {
            put(lm.name, buildJsonObject {
                put("strong", prepared.track.frames.indices.count { prepared.geo.point(it, lm) != null })
                put("weak", prepared.track.frames.indices.count { prepared.geo.point(it, lm, weak = true) != null })
            })
        }
    })
    /** Cada puerta de publicación por separado (no solo la primera), para estudiar subgrupos de BAJA. */
    fun gates(series: CountSeries, seg: SegmentationResult, variantProfile: AnalysisProfile, t: AnalysisTuning): JsonObject = buildJsonObject {
        val flags = RepFlags.of(prepared, series, seg)
        val c = RepConfidence.score(series, seg, prepared.view, variantProfile, flags, t)
        val evidence = countEvidence(series, seg, t)
        put("level", c.level.name); put("score", c.score)
        put("forcedLowReason", c.forcedLowReason?.let(::JsonPrimitive) ?: JsonNull)
        put("cTrack", c.cTrack); put("cSnr", c.cSnr); put("cAmp", c.cAmp); put("cRhy", c.cRhy); put("cView", c.cView)
        put("nAmb", c.nAmbiguous)
        put("repeatedScore", sqrt(c.cTrack) * c.cView * (t.snrWeight * c.cSnr + t.ampWeight * c.cAmp + t.rhythmWeight * c.cRhy) *
            t.ambiguityPenalty.pow(c.nAmbiguous))
        put("shortScore", sqrt(c.cTrack) * c.cView * c.cSnr)
        put("closedShort", evidence.closedShortSet)
        put("sidesDisagree", seg.sideMode == SideMode.BOTH_SYNC && seg.perSideCounts.values.toSet().size > 1)
        put("censoredStart", evidence.censoredStart); put("censoredReturn", evidence.censoredReturn)
        put("multi", AnalysisFlag.MULTI_PERSON in flags); put("legacy", AnalysisFlag.LEGACY_TRACK in flags)
        put("polarity", seg.polarityFlipped || AnalysisFlag.POLARITY_FLIPPED in flags)
        put("sideCap", seg.sideMode == SideMode.NEAR_ONLY || AnalysisFlag.SIDE_UNKNOWN in flags ||
            AnalysisFlag.FAR_SIDE_WORKING in flags)
        put("view", prepared.view.kind.name); put("viewSupported", prepared.view.kind in variantProfile.countViews)
        put("validFraction", series.validFraction); put("fs", series.fs); put("lowFps", series.fs < t.lowFpsBelow)
        put("fulls", seg.cycles.count { it.kind == RepKind.FULL })
        put("partials", seg.partialCount); put("failedCycles", seg.failedCount)
        put("sideMode", seg.sideMode.name); put("reason", seg.noRepsReason?.let(::JsonPrimitive) ?: JsonNull)
        put("hasWindow", seg.activeWindow != null)
        put("cycles", JsonArray(seg.cycles.map { cy -> buildJsonObject {
            put("kind", cy.kind.name); put("a", cy.amplitudeFraction); put("turnInGap", cy.turnInGap)
            put("left", cy.leftValley); put("peak", cy.peak); put("right", cy.rightValley?.let(::JsonPrimitive) ?: JsonNull)
        } }))
        put("discarded", JsonArray(seg.discarded.map { JsonPrimitive(it.reason) }))
        // Evidencia de bordes con otras ventanas/bandas de reposo: [censoredStart, censoredReturn, closedShortSet].
        put("evidence", buildJsonObject {
            for (w in listOf(0.12, 0.25, 0.40)) for (r in listOf(0.05, 0.10, 0.20)) {
                val variant = t.copy(metrics = t.metrics.copy(countReliability = t.metrics.countReliability.copy(
                    boundaryWindowS = w, shortRestFraction = r)))
                val e = countEvidence(series, seg, variant)
                put("$w/$r", JsonArray(listOf(JsonPrimitive(e.censoredStart), JsonPrimitive(e.censoredReturn), JsonPrimitive(e.closedShortSet))))
            }
        })
    }
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
        put("gates", gates(series, seg, profile, tuning))
        put("rawGates", gates(series, requireNotNull(rawSeg), profile, tuning))
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
            if (candidate is CountSeriesResult.Ok) {
                val raw = RepSegmenter.segment(candidate.series, profile, emptySet(), variant)
                val evidence = captureCountEvidence(prepared, profile, variant, captureWindowUs(candidate.series, raw, variant))
                put(seconds.toString(), buildJsonObject {
                    summary(candidate.series, RepSegmenter.segment(candidate.series, profile, emptySet(), variant, prepared), profile)
                        .forEach { (k, v) -> put(k, v) }
                    // Segmentación sin la puerta de captura y su evidencia, para reproducir la publicación con otras reglas.
                    put("rawGates", gates(candidate.series, raw, profile, variant))
                    put("captureClipped", evidence.clippedFraction); put("captureFootTravelBh", evidence.commonFootTravelBh)
                })
            } else put(seconds.toString(), buildJsonObject {
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

/** Por región del cuerpo: fracción de fotogramas con pose con algún punto de la región fuera de [0, 1]. */
private fun clippedRegions(prepared: PreparedTrack): JsonObject = buildJsonObject {
    val frames = prepared.track.frames
    val posed = frames.indices.filter { frames[it].hasPose }
    fun fraction(points: List<PoseLandmark>): Double = if (posed.isEmpty()) 0.0 else posed.count { i ->
        points.any { lm ->
            val x = frames[i].x(lm); val y = frames[i].y(lm)
            x.isFinite() && y.isFinite() && (x < 0f || x > 1f || y < 0f || y > 1f)
        }
    }.toDouble() / posed.size
    val regions = linkedMapOf(
        "head" to listOf(PoseLandmark.NOSE, PoseLandmark.LEFT_EAR, PoseLandmark.RIGHT_EAR),
        "shoulders" to listOf(PoseLandmark.LEFT_SHOULDER, PoseLandmark.RIGHT_SHOULDER),
        "hips" to listOf(PoseLandmark.LEFT_HIP, PoseLandmark.RIGHT_HIP),
        "elbows" to listOf(PoseLandmark.LEFT_ELBOW, PoseLandmark.RIGHT_ELBOW),
        "wrists" to listOf(PoseLandmark.LEFT_WRIST, PoseLandmark.RIGHT_WRIST),
        "hands" to listOf(PoseLandmark.LEFT_INDEX, PoseLandmark.RIGHT_INDEX, PoseLandmark.LEFT_PINKY, PoseLandmark.RIGHT_PINKY,
            PoseLandmark.LEFT_THUMB, PoseLandmark.RIGHT_THUMB),
        "knees" to listOf(PoseLandmark.LEFT_KNEE, PoseLandmark.RIGHT_KNEE),
        "ankles" to listOf(PoseLandmark.LEFT_ANKLE, PoseLandmark.RIGHT_ANKLE),
        "feet" to listOf(PoseLandmark.LEFT_HEEL, PoseLandmark.RIGHT_HEEL, PoseLandmark.LEFT_FOOT_INDEX, PoseLandmark.RIGHT_FOOT_INDEX),
    )
    for ((name, points) in regions) put(name, fraction(points))
    // Lado cercano (si se conoce): el brazo y la pierna que suelen dar la señal.
    val near = prepared.near?.near
    if (near != null) {
        val l = near == Side.LEFT
        put("nearArm", fraction(if (l) listOf(PoseLandmark.LEFT_SHOULDER, PoseLandmark.LEFT_ELBOW, PoseLandmark.LEFT_WRIST)
            else listOf(PoseLandmark.RIGHT_SHOULDER, PoseLandmark.RIGHT_ELBOW, PoseLandmark.RIGHT_WRIST)))
        put("nearLeg", fraction(if (l) listOf(PoseLandmark.LEFT_HIP, PoseLandmark.LEFT_KNEE, PoseLandmark.LEFT_ANKLE)
            else listOf(PoseLandmark.RIGHT_HIP, PoseLandmark.RIGHT_KNEE, PoseLandmark.RIGHT_ANKLE)))
    }
}
