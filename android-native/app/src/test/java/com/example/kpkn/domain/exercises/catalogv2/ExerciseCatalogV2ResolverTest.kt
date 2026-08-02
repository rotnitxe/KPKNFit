package com.example.kpkn.domain.exercises.catalogv2

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class ExerciseCatalogV2ResolverTest {
    private val catalog = ExerciseCatalogV2(
        schemaVersion = 2,
        catalogRevision = "test-revision",
        ontologyRevision = "test-ontology",
        families = listOf(
            ExerciseFamilyV2(
                id = "elbow_flexion",
                canonicalName = "Curl de bíceps",
                description = "Familia de flexión de codo para una prueba determinista.",
                definitions = listOf(
                    ExerciseDefinitionV2(
                        id = "biceps_curl",
                        familyId = "elbow_flexion",
                        kind = ExerciseDefinitionKindV2.PARENT,
                        canonicalName = "Curl de bíceps",
                        description = "Flexión de codo con configuración explícita para probar resolución.",
                        searchTerms = listOf("curl bayesiano"),
                        optionAxes = listOf("setup"),
                        configurations = listOf(
                            config("standing", "De pie"),
                            config("bayesian", "Bayesiano"),
                        ),
                        defaultConfigurationId = "biceps_curl__standing",
                        evidence = evidence(),
                    ),
                ),
                evidence = evidence(),
            ),
        ),
    )

    private fun config(id: String, label: String) = ExerciseConfigurationV2(
        id = "biceps_curl__$id",
        selectedOptions = mapOf("setup" to id),
        displaySummary = label,
        profile = ResolvedExerciseProfileV2(
            movementPatternId = "elbow_flexion",
            bodyRegion = ExerciseBodyRegionV2.UPPER,
            kineticChain = ExerciseKineticChainV2.ANTERIOR,
            laterality = ExerciseLateralityV2.BILATERAL,
            equipmentId = "dumbbells",
            loadMode = "free_external_load",
            primaryMuscles = listOf("biceps_brachii"),
            secondaryMuscles = listOf("brachialis"),
            stabilizerMuscles = emptyList(),
            efc = 2.0,
            cnc = 1.5,
            ssc = 0.0,
            ttc = 1.0,
            axialLoadFactor = 0.0,
            technicalDifficulty = 3.0,
            resistanceProfile = "gravity_arc",
            setupCues = listOf("Torso estable."),
            executionCues = listOf("Flexiona el codo con control."),
            commonMistakes = listOf("Balancear el tronco."),
            performanceProfileId = "biceps_curl__$id",
        ),
        evidence = evidence(),
    )

    private fun evidence() = CatalogEvidenceV2(
        reviewStatus = CatalogReviewStatusV2.APPROVED,
        confidence = CatalogConfidenceV2.HIGH,
        evidenceRefs = listOf("test"),
    )

    @Test
    fun invalid_configuration_does_not_fall_back_to_default() {
        val resolver = ExerciseCatalogV2Resolver(catalog)
        val selection = ExerciseSelectionV2("biceps_curl", "does_not_exist", "test-revision")

        assertTrue(resolver.validate(selection) is ExerciseSelectionValidationV2.Invalid)
        assertNull(resolver.resolve(selection))
    }

    @Test
    fun specific_search_returns_one_parent_with_suggested_configuration() {
        val result = ExerciseCatalogV2Resolver(catalog).search("curl bayesiano")

        assertEquals(1, result.size)
        assertEquals("biceps_curl", result.single().definitionId)
        assertEquals("biceps_curl__bayesian", result.single().suggestedConfigurationId)
    }
    @Test
    fun search_filters_are_explicit_and_do_not_synthesize_hits() {
        val resolver = ExerciseCatalogV2Resolver(catalog)

        val upper = resolver.search(
            query = "curl",
            filters = ExerciseSearchFiltersV2(
                bodyRegions = setOf(ExerciseBodyRegionV2.UPPER),
                equipmentIds = setOf("dumbbells"),
            ),
        )
        assertEquals(listOf("biceps_curl"), upper.map { it.definitionId })
        assertTrue(
            resolver.search(
                query = "curl",
                filters = ExerciseSearchFiltersV2(
                    bodyRegions = setOf(ExerciseBodyRegionV2.LOWER),
                ),
            ).isEmpty(),
        )
        assertTrue(
            resolver.search(
                query = "curl",
                filters = ExerciseSearchFiltersV2(equipmentIds = setOf("cable")),
            ).isEmpty(),
        )
    }
}
