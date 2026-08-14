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
            ExerciseFamilyV2(
                id = "knee_dominant",
                canonicalName = "Sentadillas",
                description = "Familia dominante de rodilla.",
                definitions = listOf(
                    ExerciseDefinitionV2(
                        id = "back_squat",
                        familyId = "knee_dominant",
                        kind = ExerciseDefinitionKindV2.PARENT,
                        canonicalName = "Sentadilla con Barra",
                        description = "Sentadilla clásica con barra.",
                        searchTerms = listOf("sentadilla trasera", "back squat"),
                        optionAxes = emptyList(),
                        configurations = listOf(
                            ExerciseConfigurationV2(
                                id = "back_squat__barbell",
                                selectedOptions = emptyMap(),
                                displaySummary = "Barra",
                                profile = profile(
                                    movementPatternId = "knee_dominant",
                                    equipmentId = "barbell",
                                    primary = listOf("quadriceps"),
                                    secondary = listOf("gluteus_maximus"),
                                ),
                                evidence = evidence(),
                            ),
                        ),
                        defaultConfigurationId = "back_squat__barbell",
                        evidence = evidence(),
                    ),
                ),
                evidence = evidence(),
            ),
            ExerciseFamilyV2(
                id = "chest_fly",
                canonicalName = "Aperturas de Pecho",
                description = "Familia de aperturas.",
                definitions = listOf(
                    ExerciseDefinitionV2(
                        id = "cable_chest_fly",
                        familyId = "chest_fly",
                        kind = ExerciseDefinitionKindV2.PARENT,
                        canonicalName = "Aperturas en Polea",
                        description = "Aperturas de pecho en polea.",
                        searchTerms = listOf("cable fly", "aperturas polea"),
                        optionAxes = emptyList(),
                        configurations = listOf(
                            ExerciseConfigurationV2(
                                id = "cable_chest_fly__cable",
                                selectedOptions = emptyMap(),
                                displaySummary = "Polea",
                                profile = profile(
                                    movementPatternId = "horizontal_abduction",
                                    equipmentId = "cable",
                                    primary = listOf("pectoralis"),
                                    secondary = listOf("deltoid"),
                                ),
                                evidence = evidence(),
                            ),
                        ),
                        defaultConfigurationId = "cable_chest_fly__cable",
                        evidence = evidence(),
                    ),
                ),
                evidence = evidence(),
            ),
            ExerciseFamilyV2(
                id = "chest_crossover",
                canonicalName = "Cruces de Poleas",
                description = "Familia de cruces en polea.",
                definitions = listOf(
                    ExerciseDefinitionV2(
                        id = "cable_crossover",
                        familyId = "chest_crossover",
                        kind = ExerciseDefinitionKindV2.PARENT,
                        canonicalName = "Cruces en Polea",
                        description = "Cruce de poleas con altura ajustable.",
                        searchTerms = listOf("cruces", "crossover", "cruce de poleas"),
                        optionAxes = listOf("implement", "pulley_height"),
                        configurations = listOf(
                            ExerciseConfigurationV2(
                                id = "cable_crossover__high",
                                selectedOptions = mapOf("implement" to "cable", "pulley_height" to "high"),
                                displaySummary = "cable · high",
                                profile = profile(
                                    movementPatternId = "horizontal_abduction",
                                    equipmentId = "cable",
                                    primary = listOf("pectoralis"),
                                    secondary = listOf("deltoid"),
                                ),
                                evidence = evidence(),
                            ),
                            ExerciseConfigurationV2(
                                id = "cable_crossover__mid",
                                selectedOptions = mapOf("implement" to "cable", "pulley_height" to "mid"),
                                displaySummary = "cable · mid",
                                profile = profile(
                                    movementPatternId = "horizontal_abduction",
                                    equipmentId = "cable",
                                    primary = listOf("pectoralis"),
                                    secondary = listOf("deltoid"),
                                ),
                                evidence = evidence(),
                            ),
                            ExerciseConfigurationV2(
                                id = "cable_crossover__low",
                                selectedOptions = mapOf("implement" to "cable", "pulley_height" to "low"),
                                displaySummary = "cable · low",
                                profile = profile(
                                    movementPatternId = "horizontal_abduction",
                                    equipmentId = "cable",
                                    primary = listOf("pectoralis"),
                                    secondary = listOf("deltoid"),
                                ),
                                evidence = evidence(),
                            ),
                        ),
                        defaultConfigurationId = "cable_crossover__mid",
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

    private fun profile(
        movementPatternId: String,
        equipmentId: String,
        primary: List<String>,
        secondary: List<String>,
    ) = ResolvedExerciseProfileV2(
        movementPatternId = movementPatternId,
        bodyRegion = ExerciseBodyRegionV2.UPPER,
        kineticChain = ExerciseKineticChainV2.ANTERIOR,
        laterality = ExerciseLateralityV2.BILATERAL,
        equipmentId = equipmentId,
        loadMode = "free_external_load",
        primaryMuscles = primary,
        secondaryMuscles = secondary,
        stabilizerMuscles = emptyList(),
        efc = 2.0,
        cnc = 1.5,
        ssc = 0.0,
        ttc = 1.0,
        axialLoadFactor = 0.0,
        technicalDifficulty = 3.0,
        resistanceProfile = "gravity_arc",
        setupCues = listOf("Posición estable."),
        executionCues = listOf("Ejecuta con control."),
        commonMistakes = listOf("Perder la posición."),
        performanceProfileId = "${movementPatternId}__${equipmentId}",
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
    fun localized_equipment_search_returns_parent_and_suggests_matching_configuration() {
        val result = ExerciseCatalogV2Resolver(catalog).search("mancuernas")

        assertEquals(listOf("biceps_curl"), result.map { it.definitionId })
        assertEquals("biceps_curl__standing", result.single().suggestedConfigurationId)
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

    @Test
    fun search_synonym_squat_finds_sentadilla() {
        val result = ExerciseCatalogV2Resolver(catalog).search("squat")
        assertEquals(listOf("back_squat"), result.map { it.definitionId })
    }

    @Test
    fun search_tolerates_typos_in_long_terms() {
        val result = ExerciseCatalogV2Resolver(catalog).search("sentadila")
        assertEquals(listOf("back_squat"), result.map { it.definitionId })
    }

    @Test
    fun search_muscle_alias_pecho_finds_chest_exercises() {
        val result = ExerciseCatalogV2Resolver(catalog).search("pecho")
        assertEquals(
            setOf("cable_chest_fly", "cable_crossover"),
            result.map { it.definitionId }.toSet(),
        )
    }

    @Test
    fun cable_crossover_low_pulley_suggests_low_configuration() {
        val result = ExerciseCatalogV2Resolver(catalog).search("Cruces en Polea Baja")

        assertEquals("cable_crossover", result.single().definitionId)
        assertEquals("cable_crossover__low", result.single().suggestedConfigurationId)
    }

    @Test
    fun cable_crossover_high_pulley_suggests_high_configuration() {
        val result = ExerciseCatalogV2Resolver(catalog).search("cruces polea alta")

        assertEquals("cable_crossover", result.single().definitionId)
        assertEquals("cable_crossover__high", result.single().suggestedConfigurationId)
    }
}
