package com.example.kpkn.domain.training

import com.example.kpkn.data.protocols.CatalogIds
import kotlinx.serialization.Serializable

/** Estimación visible de la carga de una variante a partir de la marca de su básico. */
@Serializable
data class VariantLoadEstimate(
    val sourceConfigurationId: String,
    val sourceOneRmKg: Double,
    val factor: Double,
    val estimatedOneRmKg: Double,
)

/**
 * Factores fijos de KPKN (no de otra app) relativos al básico de competición de cada familia. Son un punto de partida
 * prudente: la carga se marca «estimada» y la persona la corrige declarando la marca de la variante.
 */
object VariantLoadEstimates {
    private val SQUAT = mapOf(
        CatalogIds.SQ_LOW to 1.0,
        CatalogIds.SQ_HIGH to 0.95,
        CatalogIds.SQ_PAUSED to 0.85,
        CatalogIds.SQ_BOX to 0.9,
        CatalogIds.SQ_FRONT to 0.8,
        CatalogIds.SQ_SSB to 0.9,
        CatalogIds.SQ_PIN to 0.85,
    )
    private val BENCH = mapOf(
        CatalogIds.BP to 1.0,
        CatalogIds.BP_PAUSE to 0.95,
        CatalogIds.BP_SPOTO to 0.92,
        CatalogIds.BP_INC to 0.8,
        CatalogIds.BP_CLOSE_GRIP to 0.9,
        CatalogIds.BP_FLOOR to 0.9,
    )
    private val DEADLIFT = mapOf(
        CatalogIds.DL to 1.0,
        CatalogIds.DL_DEF to 0.9,
        CatalogIds.DL_TO_KNEES to 0.85,
        CatalogIds.RDL to 0.7,
        CatalogIds.SLDL to 0.65,
    )
    private val FAMILIES = listOf(SQUAT, BENCH, DEADLIFT)

    /** Null si alguna de las dos configuraciones no está en la misma familia de la tabla o la marca no es positiva. */
    fun estimate(sourceConfigurationId: String, sourceOneRmKg: Double, targetConfigurationId: String): VariantLoadEstimate? {
        if (sourceOneRmKg <= 0.0) return null
        val family = FAMILIES.firstOrNull { sourceConfigurationId in it && targetConfigurationId in it } ?: return null
        val factor = family.getValue(targetConfigurationId) / family.getValue(sourceConfigurationId)
        return VariantLoadEstimate(sourceConfigurationId, sourceOneRmKg, factor, sourceOneRmKg * factor)
    }
}
