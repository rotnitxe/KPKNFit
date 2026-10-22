package com.example.kpkn.screens.technique.analysis

import com.example.kpkn.domain.biomechanics.metrics.CountConfidence
import com.example.kpkn.domain.biomechanics.metrics.PartialKind
import com.example.kpkn.domain.biomechanics.metrics.PartialZone
import com.example.kpkn.domain.biomechanics.metrics.RepKind
import com.example.kpkn.domain.biomechanics.metrics.SetTechniqueReport
import com.example.kpkn.screens.technique.TechniqueSamples

/**
 * La serie de ejemplo de la pantalla de análisis con parciales de M4: la 3.ª repetición (índice 2) y, si hay una segunda zona, la
 * 6.ª (índice 5, que en la serie base es completa) pasan a ser parciales con su zona y su fracción de recorrido.
 */
internal object PartialsSamples {
    private val slots = listOf(2, 5)

    fun report(
        partialConfidence: CountConfidence? = CountConfidence.ALTA,
        zones: List<PartialZone?> = listOf(PartialZone.STRETCHED, PartialZone.STRETCHED),
        fraction: Double = 0.55,
        base: SetTechniqueReport = TechniqueSamples.analysisReport,
    ): SetTechniqueReport {
        require(zones.size in 1..slots.size)
        val reps = base.reps.map { rep ->
            val k = slots.indexOf(rep.index)
            if (k < 0 || k >= zones.size) rep
            else rep.copy(
                kind = RepKind.PARTIAL,
                partialKind = PartialKind.FAR_END,
                zone = zones[k],
                amplitudeFraction = fraction,
                amplitude = fraction * 0.5,
                mcv = null,
                peakVelocity = null,
            )
        }
        return base.copy(
            reps = reps,
            repCount = reps.count { it.kind == RepKind.FULL },
            partialReps = reps.count { it.kind == RepKind.PARTIAL },
            partialConfidence = partialConfidence,
        )
    }
}
