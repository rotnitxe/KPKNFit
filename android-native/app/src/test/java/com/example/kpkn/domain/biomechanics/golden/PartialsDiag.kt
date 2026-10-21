package com.example.kpkn.domain.biomechanics.golden

import com.example.kpkn.domain.biomechanics.metrics.AnalysisTuning
import com.example.kpkn.domain.biomechanics.metrics.RangeReference
import com.example.kpkn.domain.biomechanics.pose.PoseTrackV2
import com.example.kpkn.domain.biomechanics.profiles.AnalysisProfile
import com.example.kpkn.domain.biomechanics.reps.CountSeriesBuilder
import com.example.kpkn.domain.biomechanics.reps.CountSeriesResult
import com.example.kpkn.domain.biomechanics.reps.RepConfidence
import com.example.kpkn.domain.biomechanics.reps.RepFlags
import com.example.kpkn.domain.biomechanics.reps.RepSegmenter
import com.example.kpkn.domain.biomechanics.reps.TrackPreparation
import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.JsonNull
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.put

/**
 * M4, solo diagnóstico de los bancos de parciales (no cambia la verdad, el emparejamiento ni las cifras): lo que decide la
 * confianza propia de las parciales en cada serie (descartes, dudas y la evidencia del segmentador), con el mismo camino que el
 * motor (preparación, señal de conteo, segmentación y confianza) y la misma referencia.
 */
internal object PartialsDiag {
    fun of(track: PoseTrackV2, profile: AnalysisProfile, reference: RangeReference?): JsonObject = try {
        val tuning = AnalysisTuning.DEFAULT
        val prepared = TrackPreparation.prepare(track, tuning)
        val built = CountSeriesBuilder.build(prepared, null, profile, tuning)
        if (built !is CountSeriesResult.Ok) {
            buildJsonObject { put("sinSenal", true) }
        } else {
            val series = built.series
            val seg = RepSegmenter.segment(series, profile, emptySet(), tuning, prepared, reference)
            val conf = RepConfidence.score(series, seg, prepared.view, profile, RepFlags.of(prepared, series, seg), tuning)
            val evidence = seg.partialEvidence
            fun num(x: Double) = if (x.isFinite()) JsonPrimitive(Math.round(x * 1e4) / 1e4) else JsonNull
            buildJsonObject {
                put("nivel", conf.level.name)
                put("nivelParciales", RepConfidence.partialLevel(seg, conf, tuning).name)
                put("forzada", conf.forcedLowReason?.let(::JsonPrimitive) ?: JsonNull)
                put("descartes", JsonArray(seg.discarded.map { JsonPrimitive(it.reason) }))
                put("dudas", JsonArray(seg.ambiguity.map { JsonPrimitive(it.kind.name) }))
                put("estable", evidence.stableReference)
                put("limite", evidence.borderline)
                put("cortas", evidence.shortFull)
                put("completasVivas", evidence.fullCycles)
                put("referenciaAplicada", evidence.referenceApplied)
                put("aFull", num(seg.aFull))
                put("aClase", num(seg.classificationAmplitude))
                put("ciclos", JsonArray(seg.cycles.map { c ->
                    buildJsonObject { put("k", c.kind.name); put("a", num(c.amplitudeFraction)) }
                }))
            }
        }
    } catch (e: Throwable) {
        buildJsonObject { put("error", e.javaClass.simpleName + ": " + e.message) }
    }
}
