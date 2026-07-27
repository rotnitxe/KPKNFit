package com.example.kpkn.domain.auge

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class AugeMuscleNormalizationTest {

    @Test
    fun deltoidHeadsResolveToSamePillar() {
        assertEquals("Deltoides", getAugeMusclePillarId("Deltoides Anterior"))
        assertEquals("Deltoides", getAugeMusclePillarId("Deltoides Lateral"))
        assertEquals("Deltoides", getAugeMusclePillarId("Deltoides Posterior"))
        assertEquals("Deltoides", getAugeMusclePillarId("hombro", "lateral"))
        assertEquals("Deltoides Lateral", getAugeMuscleDisplayId("Deltoides Lateral"))
        assertEquals("Deltoides Anterior", getAugeMuscleDisplayId("shoulder", "front"))
        assertEquals("Deltoides Posterior", getAugeMuscleDisplayId("Deltoides", "Deltoides Posterior"))
        assertEquals("Deltoides Posterior", getAugeMuscleDisplayId("Deltoides", "posterior"))
        assertEquals("Glúteos", getAugeMusclePillarId("Glúteo Medio"))
        assertEquals("Glúteos", getAugeMusclePillarId("Tensor Fascia Lata"))
    }

    @Test
    fun lookupPrefersPillarThenDisplayThenLegacy() {
        val pillarOnly = mapOf("Deltoides" to 72)
        assertEquals(72, lookupMuscleScore(pillarOnly, "Deltoides Lateral"))

        val displayOnly = mapOf("Deltoides Lateral" to 55)
        assertEquals(55, lookupMuscleScore(displayOnly, "Deltoides"))

        val mixed = mapOf("Deltoides Lateral" to 40, "Deltoides" to 80)
        assertEquals(80, lookupMuscleScore(mixed, "Deltoides Lateral"))
    }

    @Test
    fun remapIntMapCollapsesHeadsToPillarPreferringPillarValue() {
        val remapped = remapMuscleIntMapToPillars(
            mapOf(
                "Deltoides Lateral" to 40,
                "Deltoides" to 75,
                "Deltoides Anterior" to 30,
                "Pectorales" to 90,
            ),
        )
        assertEquals(75, remapped["Deltoides"])
        assertEquals(90, remapped["Pectorales"])
        assertEquals(2, remapped.size)
    }

    @Test
    fun remapMultipliersAverageLegacyHeadsUnderPillarKey() {
        val remapped = remapMuscleMultiplierMapToPillars(
            mapOf(
                "deltoides lateral" to 1.2,
                "deltoides anterior" to 0.8,
                "pectorales" to 1.1,
            ),
        )
        assertEquals(1.0, remapped["deltoides"]!!, 0.001)
        assertEquals(1.1, remapped["pectorales"]!!, 0.001)
    }

    @Test
    fun lookupMuscleDrainMultiplierMigratesLegacyDisplayKeys() {
        val legacy = mapOf("deltoides lateral" to 1.35)
        assertEquals(1.35, lookupMuscleDrainMultiplier(legacy, "Deltoides"), 0.001)
        assertEquals(1.0, lookupMuscleDrainMultiplier(emptyMap(), "Deltoides"), 0.001)
    }

    @Test
    fun matchesTargetAcceptsHeadAgainstPillar() {
        assertTrue(matchesAugeMuscleTarget("Deltoides Lateral", "Deltoides"))
        assertNull(lookupMuscleScore(emptyMap(), "Core"))
    }
}
