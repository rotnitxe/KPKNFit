package com.example.kpkn.domain.exercises

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class MuscleHeadResolutionTest {

    @Test
    fun deltoid_legacy_emphasis_resolves_to_correct_head() {
        assertEquals(
            "Deltoides Posterior",
            MuscleHeadResolution.resolveDisplayHead("Deltoides", "Deltoides Posterior"),
        )
        assertEquals(
            "Deltoides Lateral",
            MuscleHeadResolution.resolveDisplayHead("Deltoides", "Deltoides Lateral"),
        )
        assertEquals(
            "Deltoides Anterior",
            MuscleHeadResolution.resolveDisplayHead("Deltoides", "Deltoides Anterior"),
        )
        assertEquals(
            "Deltoides Posterior",
            MuscleHeadResolution.resolveDisplayHead("Deltoides", "posterior"),
        )
    }

    @Test
    fun glute_legacy_emphasis_resolves_to_correct_head() {
        assertEquals(
            "Glúteo Medio",
            MuscleHeadResolution.resolveDisplayHead("Glúteos", "Glúteo Medio"),
        )
        assertEquals(
            "Glúteo Menor",
            MuscleHeadResolution.resolveDisplayHead("Glúteos", "menor"),
        )
        assertEquals(
            "Glúteo Mayor",
            MuscleHeadResolution.resolveDisplayHead("Glúteos", "Glúteo Mayor / Isquiosurales"),
        )
    }

    @Test
    fun emphasis_keywords_normalize_legacy_strings() {
        assertEquals("posterior", MuscleHeadResolution.resolveEmphasisKeyword("Deltoides", "Deltoides Posterior"))
        assertEquals("medio", MuscleHeadResolution.resolveEmphasisKeyword("Deltoides", "lateral"))
        assertEquals("mayor", MuscleHeadResolution.resolveEmphasisKeyword("Glúteos", "Glúteo Mayor"))
        assertEquals("menor", MuscleHeadResolution.resolveEmphasisKeyword("Glúteos", "menor"))
    }

    @Test
    fun matches_head_uses_normalized_keyword() {
        val anatomy = MUSCLE_BY_CANONICAL.getValue("Deltoides")
        val posterior = anatomy.heads.first { it.emphasisKeyword == "posterior" }
        assertTrue(MuscleHeadResolution.matchesHead("Deltoides", "Deltoides Posterior", posterior))
        assertTrue(MuscleHeadResolution.matchesHead("Deltoides", "posterior", posterior))
    }
}
