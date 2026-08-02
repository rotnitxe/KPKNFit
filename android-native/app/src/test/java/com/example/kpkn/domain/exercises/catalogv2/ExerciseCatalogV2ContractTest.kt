package com.example.kpkn.domain.exercises.catalogv2

import kotlinx.serialization.json.Json
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class ExerciseCatalogV2ContractTest {
    private val json = Json { ignoreUnknownKeys = false }

    @Test
    fun selection_round_trips_without_reconstructing_options() {
        val selection = ExerciseSelectionV2(
            definitionId = "biceps_curl",
            configurationId = "biceps_curl__bayesian__cable__supinated",
            catalogRevision = "v2-draft-2026-08-01",
        )

        val encoded = json.encodeToString(ExerciseSelectionV2.serializer(), selection)
        val decoded = json.decodeFromString(ExerciseSelectionV2.serializer(), encoded)

        assertEquals(selection, decoded)
        assertTrue(decoded.configurationId.contains("bayesian"))
    }

    @Test
    fun invalid_selection_is_represented_as_error_not_default() {
        val result: ExerciseSelectionValidationV2 = ExerciseSelectionValidationV2.Invalid(
            "configuration_id_not_in_definition",
        )

        assertTrue(result is ExerciseSelectionValidationV2.Invalid)
        assertEquals(
            "configuration_id_not_in_definition",
            (result as ExerciseSelectionValidationV2.Invalid).reason,
        )
    }
}
