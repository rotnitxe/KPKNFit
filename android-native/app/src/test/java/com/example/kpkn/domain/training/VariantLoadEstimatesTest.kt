package com.example.kpkn.domain.training

import com.example.kpkn.data.protocols.CatalogIds
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Test

class VariantLoadEstimatesTest {
    @Test fun lowBarToPausedSquatScalesDown() {
        val e = VariantLoadEstimates.estimate(CatalogIds.SQ_LOW, 200.0, CatalogIds.SQ_PAUSED)
        assertNotNull(e)
        assertEquals(170.0, e!!.estimatedOneRmKg, 1e-9)
        assertEquals(0.85, e.factor, 1e-9)
        assertEquals(CatalogIds.SQ_LOW, e.sourceConfigurationId)
        assertEquals(200.0, e.sourceOneRmKg, 1e-9)
    }

    @Test fun highBarToLowBarScalesUpByInverseFactor() {
        val e = VariantLoadEstimates.estimate(CatalogIds.SQ_HIGH, 190.0, CatalogIds.SQ_LOW)!!
        assertEquals(200.0, e.estimatedOneRmKg, 1e-9)
        assertEquals(1.0 / 0.95, e.factor, 1e-9)
    }

    @Test fun accessibilityLabelNamesTheBasicAndUsesADecimalComma() {
        val e = VariantLoadEstimates.estimate(CatalogIds.SQ_LOW, 200.0, CatalogIds.SQ_PAUSED)!!
        assertEquals(
            "Carga estimada desde tu 1RM de Sentadilla barra baja (×0,85). Declara la marca de esta variante para ajustarla.",
            VariantLoadEstimates.accessibilityLabel(e, "Sentadilla barra baja"),
        )
        assertEquals(
            "Carga estimada desde tu 1RM del básico (×0,85). Declara la marca de esta variante para ajustarla.",
            VariantLoadEstimates.accessibilityLabel(e, null),
        )
        assertEquals("carga estimada", VariantLoadEstimates.PRESCRIPTION_LABEL)
    }

    @Test fun benchToSpotoIs92Percent() {
        assertEquals(92.0, VariantLoadEstimates.estimate(CatalogIds.BP, 100.0, CatalogIds.BP_SPOTO)!!.estimatedOneRmKg, 1e-9)
    }

    @Test fun deadliftFamilyEstimates() {
        assertEquals(198.0, VariantLoadEstimates.estimate(CatalogIds.DL, 220.0, CatalogIds.DL_DEF)!!.estimatedOneRmKg, 1e-9)
    }

    @Test fun unknownConfigurationOrDifferentFamilyIsNull() {
        assertNull(VariantLoadEstimates.estimate(CatalogIds.DL, 220.0, "sumo_deadlift__barbell"))
        assertNull(VariantLoadEstimates.estimate(CatalogIds.SQ_LOW, 200.0, CatalogIds.BP))
    }

    @Test fun nonPositiveMarkIsNull() {
        assertNull(VariantLoadEstimates.estimate(CatalogIds.SQ_LOW, 0.0, CatalogIds.SQ_HIGH))
        assertNull(VariantLoadEstimates.estimate(CatalogIds.SQ_LOW, -5.0, CatalogIds.SQ_HIGH))
    }
}
