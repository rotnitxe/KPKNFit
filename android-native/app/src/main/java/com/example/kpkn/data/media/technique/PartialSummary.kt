package com.example.kpkn.data.media.technique

import com.example.kpkn.domain.biomechanics.metrics.CountConfidence
import com.example.kpkn.domain.biomechanics.metrics.PartialZone
import com.example.kpkn.domain.biomechanics.metrics.RepKind
import com.example.kpkn.domain.biomechanics.metrics.SetTechniqueReport

/**
 * Lo que la sesión en vivo necesita saber de las parciales de un informe y que `video_analysis` no guarda (sin migraciones; la
 * fuente de verdad es `<id>.analysis.json`): la confianza propia del número de parciales (M4) y su zona.
 *
 * @property confidence la confianza del número de parciales; `null` si el informe no la trae (motor anterior al 9).
 * @property zone la zona común de todas las parciales, o `null` si hay zonas distintas, alguna sin zona o ninguna parcial.
 */
data class PartialSummary(
    val confidence: CountConfidence?,
    val zone: PartialZone?,
) {
    companion object {
        fun of(report: SetTechniqueReport): PartialSummary {
            val partials = report.reps.filter { it.kind == RepKind.PARTIAL }
            val zone = if (partials.isEmpty() || partials.any { it.zone == null }) null else partials.mapNotNull { it.zone }.toSet().singleOrNull()
            return PartialSummary(report.partialConfidence, zone)
        }
    }
}
