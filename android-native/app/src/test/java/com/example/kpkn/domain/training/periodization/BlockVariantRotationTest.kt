package com.example.kpkn.domain.training.periodization

import com.example.kpkn.data.protocols.CatalogIds
import org.junit.Assert.assertEquals
import org.junit.Test

class BlockVariantRotationTest {
    @Test fun accumulation_and_intensification_have_distinct_literal_variants() {
        assertEquals(CatalogIds.SQ_HIGH,BlockVariantRotation.variantFor(CatalogIds.SQ_LOW,PhaseKind.ACCUMULATION,true))
        assertEquals(CatalogIds.SQ_PAUSED,BlockVariantRotation.variantFor(CatalogIds.SQ_LOW,PhaseKind.INTENSIFICATION,true))
        assertEquals(CatalogIds.BP_SPOTO,BlockVariantRotation.variantFor(CatalogIds.BP,PhaseKind.ACCUMULATION,true))
        assertEquals(CatalogIds.BP_PAUSE,BlockVariantRotation.variantFor(CatalogIds.BP,PhaseKind.INTENSIFICATION,true))
        assertEquals(CatalogIds.DL_DEF,BlockVariantRotation.variantFor(CatalogIds.DL,PhaseKind.ACCUMULATION,true))
    }
    @Test fun specific_and_light_phases_preserve_the_persons_selected_competition_variant() {
        for (phase in listOf(PhaseKind.REALIZATION,PhaseKind.PEAK,PhaseKind.TEST,PhaseKind.TAPER,PhaseKind.DELOAD)) {
            assertEquals(CatalogIds.SQ_LOW,BlockVariantRotation.variantFor(CatalogIds.SQ_LOW,phase,true))
            assertEquals(CatalogIds.DL,BlockVariantRotation.variantFor(CatalogIds.DL,phase,true))
        }
    }
    @Test fun other_modes_and_unlisted_configurations_are_unchanged() {
        assertEquals(CatalogIds.SQ_LOW,BlockVariantRotation.variantFor(CatalogIds.SQ_LOW,PhaseKind.ACCUMULATION,false))
        assertEquals("leg_press__machine",BlockVariantRotation.variantFor("leg_press__machine",PhaseKind.ACCUMULATION,true))
        assertEquals(CatalogIds.DL_SUMO,BlockVariantRotation.variantFor(CatalogIds.DL_SUMO,PhaseKind.ACCUMULATION,true))
    }
}
