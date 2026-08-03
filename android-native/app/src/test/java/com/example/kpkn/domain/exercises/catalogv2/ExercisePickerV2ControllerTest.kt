package com.example.kpkn.domain.exercises.catalogv2

import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class ExercisePickerV2ControllerTest {
    @Test
    fun each_parent_keeps_an_independent_draft() = runTest {
        val repository = InMemoryExerciseCatalogRepositoryV2(testCatalog())
        repository.load()
        val controller = ExercisePickerV2Controller(repository)

        controller.updateOption("parent_a", "setup", "standing")
        controller.updateOption("parent_b", "setup", "seated")

        assertEquals("standing", controller.draftFor("parent_a").selectedOptions["setup"])
        assertEquals("seated", controller.draftFor("parent_b").selectedOptions["setup"])
    }

    @Test
    fun confirmation_is_blocked_until_the_selection_is_exact() = runTest {
        val repository = InMemoryExerciseCatalogRepositoryV2(testCatalog())
        repository.load()
        val controller = ExercisePickerV2Controller(repository)

        controller.updateOption("parent_a", "setup", "standing")
        assertTrue(controller.confirm("parent_a") is ExercisePickerV2ConfirmResult.Blocked)
        controller.updateOption("parent_a", "implement", "cable")
        val result = controller.confirm("parent_a") as ExercisePickerV2ConfirmResult.Confirmed
        assertEquals("parent_a__standing__cable", result.selection.configurationId)
    }

    @Test
    fun changing_a_broad_choice_clears_an_incompatible_downstream_choice() = runTest {
        val repository = InMemoryExerciseCatalogRepositoryV2(testCatalog())
        repository.load()
        val controller = ExercisePickerV2Controller(repository)

        controller.updateOption("parent_a", "setup", "standing")
        controller.updateOption("parent_a", "implement", "cable")
        controller.updateOption("parent_a", "setup", "seated")

        assertEquals(
            mapOf("setup" to "seated"),
            controller.draftFor("parent_a").selectedOptions,
        )
        val result = controller.confirm("parent_a") as ExercisePickerV2ConfirmResult.Confirmed
        assertEquals("parent_a__seated__dumbbells", result.selection.configurationId)
    }

    private fun testCatalog() = ExerciseCatalogV2(
        schemaVersion = 2,
        catalogRevision = "picker-test",
        ontologyRevision = "test",
        families = listOf(
            family("family_a", "parent_a"),
            family("family_b", "parent_b"),
        ),
    )

    private fun family(familyId: String, definitionId: String) = ExerciseFamilyV2(
        id = familyId,
        canonicalName = definitionId,
        description = "Familia de prueba para validar drafts independientes en el picker.",
        definitions = listOf(
            ExerciseDefinitionV2(
                id = definitionId,
                familyId = familyId,
                kind = ExerciseDefinitionKindV2.PARENT,
                canonicalName = definitionId,
                description = "Padre de prueba con configuraciones explícitas para el picker.",
                optionAxes = listOf("setup", "implement"),
                configurations = listOf(
                    config(definitionId, "standing", "barbell"),
                    config(definitionId, "standing", "cable"),
                    config(definitionId, "seated", "dumbbells"),
                ),
                defaultConfigurationId = "${definitionId}__standing__barbell",
                evidence = evidence(),
            ),
        ),
        evidence = evidence(),
    )

    private fun config(definitionId: String, setup: String, implement: String) = ExerciseConfigurationV2(
        id = "${definitionId}__${setup}__${implement}",
        selectedOptions = mapOf("setup" to setup, "implement" to implement),
        displaySummary = "$setup · $implement",
        profile = ResolvedExerciseProfileV2(
            movementPatternId = "test",
            bodyRegion = ExerciseBodyRegionV2.UPPER,
            kineticChain = ExerciseKineticChainV2.ANTERIOR,
            laterality = ExerciseLateralityV2.BILATERAL,
            equipmentId = implement,
            loadMode = "external",
            primaryMuscles = listOf("muscle"),
            secondaryMuscles = emptyList(),
            stabilizerMuscles = emptyList(),
            efc = 1.0,
            cnc = 1.0,
            ssc = 0.0,
            ttc = 1.0,
            axialLoadFactor = 0.0,
            technicalDifficulty = 2.0,
            resistanceProfile = "test",
            setupCues = listOf("setup"),
            executionCues = listOf("execute"),
            commonMistakes = listOf("mistake"),
            performanceProfileId = "${definitionId}__${setup}__${implement}",
        ),
        evidence = evidence(),
    )

    private fun evidence() = CatalogEvidenceV2(
        reviewStatus = CatalogReviewStatusV2.APPROVED,
        confidence = CatalogConfidenceV2.HIGH,
        evidenceRefs = listOf("test"),
    )
}
