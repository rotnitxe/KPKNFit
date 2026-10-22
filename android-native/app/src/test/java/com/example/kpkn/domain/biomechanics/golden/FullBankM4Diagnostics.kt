package com.example.kpkn.domain.biomechanics.golden

import com.example.kpkn.domain.biomechanics.metrics.AnalysisTuning
import com.example.kpkn.domain.biomechanics.metrics.PartialsTuning
import com.example.kpkn.domain.biomechanics.profiles.AnalysisProfile
import com.example.kpkn.domain.biomechanics.reps.CountSeriesResult
import com.example.kpkn.domain.biomechanics.reps.PreparedTrack
import com.example.kpkn.domain.biomechanics.reps.RepConfidence
import com.example.kpkn.domain.biomechanics.reps.RepFlags
import com.example.kpkn.domain.biomechanics.reps.RepSegmenter
import kotlinx.serialization.json.JsonNull
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.put

/**
 * M4, solo diagnóstico (no cambia el recorte, la verdad, las predicciones ni las métricas del evaluador): el conteo y las dos
 * confianzas (la del conteo y la propia de las parciales) con cada variante de las reglas de parciales, para la validación
 * cruzada dejando un fold fuera con la confianza exacta del motor.
 */
internal object FullBankM4Variants {
    private val product = PartialsTuning()

    /** Las variantes: el producto, cada regla apagada o movida a sus candidatos, y todo apagado (como el motor 8). */
    val variants: Map<String, PartialsTuning> = linkedMapOf(
        "product" to product,
        "sandwich=0" to product.copy(sandwichMargin = 0.0),
        "sandwich=0.08" to product.copy(sandwichMargin = 0.08),
        "sandwich=0.15" to product.copy(sandwichMargin = 0.15),
        "width=0" to product.copy(minPartialWidth = 0.0),
        "width=0.2" to product.copy(minPartialWidth = 0.2),
        "width=0.4" to product.copy(minPartialWidth = 0.4),
        "censor=0.3" to product.copy(censorSpeedRatio = 0.3),
        "censor=0.5" to product.copy(censorSpeedRatio = 0.5),
        "censor=0.6" to product.copy(censorSpeedRatio = 0.6),
        "censorOff" to product.copy(censorEdges = false),
        "stable=1" to product.copy(minStableFull = 1),
        "stable=3" to product.copy(minStableFull = 3),
        "lastReturnOff" to product.copy(lastReturnByAmplitude = false),
        "failedSetdownOff" to product.copy(failedNeedsSetdown = false),
        "shortFullOneLevel" to product.copy(shortFullCostsOneLevel = true),
        "m4Off" to product.copy(censorEdges = false, failedNeedsSetdown = false, lastReturnByAmplitude = false, minStableFull = 0,
            sandwichMargin = 0.0, minPartialWidth = 0.0),
    )
}

internal fun fullBankM4Diagnostics(
    prepared: PreparedTrack,
    built: CountSeriesResult,
    profile: AnalysisProfile,
    tuning: AnalysisTuning = AnalysisTuning.DEFAULT,
): JsonObject =
    buildJsonObject {
        if (built !is CountSeriesResult.Ok) return@buildJsonObject
        val series = built.series
        for ((name, partials) in FullBankM4Variants.variants) {
            val variant = tuning.copy(metrics = tuning.metrics.copy(partials = partials))
            val seg = RepSegmenter.segment(series, profile, emptySet(), variant, prepared)
            val confidence = RepConfidence.score(series, seg, prepared.view, profile, RepFlags.of(prepared, series, seg), variant)
            val hasReps = seg.cycles.isNotEmpty()
            put(name, buildJsonObject {
                put("full", seg.fullCount); put("partial", seg.partialCount); put("failed", seg.failedCount)
                put("level", if (hasReps) confidence.level.name else "SIN_SALIDA")
                put("partialLevel", if (hasReps) RepConfidence.partialLevel(seg, confidence, variant).name else "SIN_SALIDA")
                put("score", confidence.score)
                put("forced", confidence.forcedLowReason?.let(::JsonPrimitive) ?: JsonNull)
                put("zones", seg.cycles.mapNotNull { it.zone?.name }.joinToString(","))
            })
        }
    }
