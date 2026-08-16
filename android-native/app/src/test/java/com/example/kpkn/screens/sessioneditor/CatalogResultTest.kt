package com.example.kpkn.screens.sessioneditor

import com.example.kpkn.data.models.ExerciseMuscleInfo
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class CatalogResultTest {

    @Test
    fun result_keeps_configuration_alignment_when_a_selection_has_no_configuration() {
        val custom = ExerciseMuscleInfo(
            id = "custom-row",
            name = "Remo personalizado",
        )
        val definition = ExerciseMuscleInfo(
            id = "chest_supported_row",
            name = "Remo con apoyo",
            catalogConfigurationId = "chest_supported_row__dumbbells__wide",
        )
        val request = CatalogLaunchRequest(requestId = "catalog-test")

        val result = CatalogResult.from(request, listOf(custom, definition))

        assertEquals(
            listOf("", "chest_supported_row__dumbbells__wide"),
            result.selectedConfigurationIds,
        )
        assertEquals(
            listOf(custom, definition),
            result.resolveSelectedInfos(
                mapOf(
                    custom.id to custom,
                    definition.catalogConfigurationId!! to definition,
                ),
            ),
        )
    }

    @Test
    fun result_prefers_exact_configuration_before_definition_fallback() {
        val default = ExerciseMuscleInfo(
            id = "press",
            name = "Press por defecto",
            catalogConfigurationId = "press__barbell",
        )
        val selected = ExerciseMuscleInfo(
            id = "press",
            name = "Press con mancuernas",
            catalogConfigurationId = "press__dumbbells",
        )

        val result = CatalogResult(
            requestId = "catalog-test",
            selectedExerciseIds = listOf("press"),
            selectedConfigurationIds = listOf("press__dumbbells"),
        )

        assertEquals(
            listOf(selected),
            result.resolveSelectedInfos(
                mapOf(
                    "press" to default,
                    "press__dumbbells" to selected,
                ),
            ),
        )
    }

    @Test
    fun create_superset_action_requires_a_multiple_selection_request() {
        val request = CatalogLaunchRequest(
            requestId = "live-superset",
            origin = CatalogLaunchOrigin.LIVE_SESSION,
            selectionMode = CatalogSelectionMode.MULTIPLE,
        )
        val selected = listOf(
            ExerciseMuscleInfo(id = "a", name = "A"),
            ExerciseMuscleInfo(id = "b", name = "B"),
        )

        val result = CatalogResult.success(
            request = request,
            selected = selected,
            commitAction = CatalogCommitAction.CREATE_SUPERSET,
            supersetConfig = CatalogSupersetConfig(),
        )

        assertTrue(result.isValidFor(request))
        val replacementRequest = request.copy(
            requestId = "replacement",
            origin = CatalogLaunchOrigin.REPLACEMENT,
            selectionMode = CatalogSelectionMode.REPLACEMENT,
            targetExerciseId = "target",
        )
        assertFalse(
            CatalogResult.success(
                request = replacementRequest,
                selected = selected,
                commitAction = CatalogCommitAction.CREATE_SUPERSET,
                supersetConfig = CatalogSupersetConfig(),
            ).isValidFor(replacementRequest)
        )
    }

    @Test
    fun replacement_rejects_structural_superset_action_atomically() {
        val request = CatalogLaunchRequest(
            requestId = "replacement",
            origin = CatalogLaunchOrigin.REPLACEMENT,
            selectionMode = CatalogSelectionMode.REPLACEMENT,
            targetExerciseId = "old",
        )
        val result = CatalogResult.success(
            request = request,
            selected = listOf(ExerciseMuscleInfo(id = "new", name = "Nuevo")),
            commitAction = CatalogCommitAction.CREATE_SUPERSET,
            supersetConfig = CatalogSupersetConfig(),
        )

        assertFalse(result.isValidFor(request))
    }
}
