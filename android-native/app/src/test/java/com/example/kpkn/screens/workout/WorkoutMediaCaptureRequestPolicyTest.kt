package com.example.kpkn.screens.workout

import com.example.kpkn.data.media.WorkoutMediaSides
import com.example.kpkn.data.models.Exercise
import com.example.kpkn.data.models.UnilateralMode
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class WorkoutMediaCaptureRequestPolicyTest {
    private val bilateral = Exercise(id = "exercise", name = "Ejercicio de prueba",
        catalogDefinitionId = "definition", catalogConfigurationId = "configuration")
    private val raw = WorkoutMediaCaptureRequest(
        exerciseId = "exercise", canonicalExerciseId = "canonical", exerciseName = "Ejercicio de prueba",
        setIndex = 2, side = "left", weightKg = 95.0, reps = 8, isPr = true,
        catalogDefinitionId = "old-definition", catalogConfigurationId = "old-configuration",
    )

    @Test fun bilateral_seed_drops_raw_left_and_uses_exercise_catalog_without_changing_set_snapshot() {
        val seed = raw.withExerciseCaptureContext(bilateral)
        assertEquals(raw.copy(side = null, catalogDefinitionId = "definition", catalogConfigurationId = "configuration"), seed)
    }

    @Test fun unilateral_left_and_right_keep_canonical_sides_and_existing_key_tokens() {
        val exercise = bilateral.copy(unilateralMode = UnilateralMode.UNILATERAL_PAIRED)
        for ((input, canonical, token) in listOf(Triple(" LEFT ", "left", "L"), Triple("r", "right", "R"))) {
            val request = raw.copy(side = input).withExerciseCaptureContext(exercise)
            assertEquals(canonical, request.side)
            assertEquals(token, WorkoutMediaSides.token(request.side))
            assertEquals("configuration", request.catalogConfigurationId)
        }
    }

    @Test fun absent_exercise_catalog_fields_preserve_each_request_fallback_independently() {
        val noCatalog = bilateral.copy(catalogDefinitionId = null, catalogConfigurationId = null)
        assertEquals(raw.copy(side = null), raw.withExerciseCaptureContext(noCatalog))
        assertEquals("old-configuration", raw.withExerciseCaptureContext(
            bilateral.copy(catalogConfigurationId = null)).catalogConfigurationId)
        assertEquals("old-definition", raw.withExerciseCaptureContext(
            bilateral.copy(catalogDefinitionId = null)).catalogDefinitionId)
    }

    @Test fun optional_exercise_preserves_request_catalog_and_existing_unknown_side_policy() {
        val request = raw.withExerciseCaptureContext(null)
        assertEquals("old-definition", request.catalogDefinitionId)
        assertEquals("old-configuration", request.catalogConfigurationId)
        assertNull(request.side)
    }

    @Test fun initial_seed_and_finalize_share_identity_policy_while_finalize_reads_latest_numbers() {
        // Estas son las dos instantáneas del provider: antes del diario y después de Finalize.
        val seed = raw.withExerciseCaptureContext(bilateral)
        val finalRequest = raw.copy(weightKg = 97.5, reps = 7).withExerciseCaptureContext(bilateral)
        assertEquals(seed.copy(weightKg = 97.5, reps = 7), finalRequest)
        assertEquals(seed, seed.withExerciseCaptureContext(bilateral))
        assertEquals("configuration", seed.catalogConfigurationId)
        assertNull(seed.side)
    }
}
