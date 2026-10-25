package com.example.kpkn.domain.training.periodization

import com.example.kpkn.data.protocols.CatalogIds

/** Tabla editorial por fase; realización, pico, test, descarga y taper conservan la variante elegida. */
object BlockVariantRotation {
    private val squats = setOf(CatalogIds.SQ_LOW,CatalogIds.SQ_HIGH,CatalogIds.SQ_PAUSED,CatalogIds.SQ_BOX)
    private val benches = setOf(CatalogIds.BP,CatalogIds.BP_PAUSE,CatalogIds.BP_SPOTO)
    private val deadlifts = setOf(CatalogIds.DL,CatalogIds.DL_DEF)

    fun variantFor(baseId: String, phase: PhaseKind, heavyMode: Boolean): String {
        if (!heavyMode) return baseId
        return when (phase) {
            PhaseKind.ACCUMULATION -> when (baseId) {
                in squats -> CatalogIds.SQ_HIGH
                in benches -> CatalogIds.BP_SPOTO
                in deadlifts -> CatalogIds.DL_DEF
                else -> baseId
            }
            PhaseKind.INTENSIFICATION -> when (baseId) {
                in squats -> CatalogIds.SQ_PAUSED
                in benches -> CatalogIds.BP_PAUSE
                in deadlifts -> CatalogIds.DL
                else -> baseId
            }
            else -> baseId
        }
    }
}
