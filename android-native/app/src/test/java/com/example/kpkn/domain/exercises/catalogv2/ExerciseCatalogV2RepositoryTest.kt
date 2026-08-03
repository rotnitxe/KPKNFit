package com.example.kpkn.domain.exercises.catalogv2

import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class ExerciseCatalogV2RepositoryTest {
    private val catalog = ExerciseCatalogV2(
        schemaVersion = 2,
        catalogRevision = "test-revision",
        ontologyRevision = "test-ontology",
        families = listOf(
            ExerciseFamilyV2(
                id = "family",
                canonicalName = "Familia",
                description = "Familia de prueba con compatibilidades explícitas y sin combinaciones libres.",
                definitions = listOf(
                    ExerciseDefinitionV2(
                        id = "parent",
                        familyId = "family",
                        kind = ExerciseDefinitionKindV2.PARENT,
                        canonicalName = "Ejercicio padre",
                        description = "Ejercicio de prueba para validar la selección contextual de configuraciones.",
                        optionAxes = listOf("setup", "implement"),
                        configurations = listOf(
                            config("standing", "barbell"),
                            config("standing", "cable"),
                            config("seated", "dumbbells"),
                        ),
                        defaultConfigurationId = "parent__standing__barbell",
                        evidence = evidence(),
                    ),
                ),
                evidence = evidence(),
            ),
        ),
    )

    private fun config(setup: String, implement: String) = ExerciseConfigurationV2(
        id = "parent__${setup}__${implement}",
        selectedOptions = mapOf("setup" to setup, "implement" to implement),
        displaySummary = "$setup · $implement",
        profile = ResolvedExerciseProfileV2(
            movementPatternId = "test_pattern",
            bodyRegion = ExerciseBodyRegionV2.UPPER,
            kineticChain = ExerciseKineticChainV2.ANTERIOR,
            laterality = ExerciseLateralityV2.BILATERAL,
            equipmentId = implement,
            loadMode = "external",
            primaryMuscles = listOf("test_muscle"),
            secondaryMuscles = emptyList(),
            stabilizerMuscles = emptyList(),
            efc = 1.0,
            cnc = 1.0,
            ssc = 0.0,
            ttc = 1.0,
            axialLoadFactor = 0.0,
            technicalDifficulty = 2.0,
            resistanceProfile = "test",
            setupCues = listOf("Setup."),
            executionCues = listOf("Execute."),
            commonMistakes = listOf("Error."),
            performanceProfileId = "parent__${setup}__${implement}",
        ),
        evidence = evidence(),
    )

    private fun evidence() = CatalogEvidenceV2(
        reviewStatus = CatalogReviewStatusV2.APPROVED,
        confidence = CatalogConfidenceV2.HIGH,
        evidenceRefs = listOf("test"),
    )

    @Test
    fun compatibility_never_creates_an_unlisted_cartesian_combination() = runTest {
        val repository = InMemoryExerciseCatalogRepositoryV2(catalog)
        repository.load()

        val firstLevel = repository.compatibility("parent")
        assertEquals(listOf("setup"), firstLevel.axes.map { it.axis })

        val compatibility = repository.compatibility(
            definitionId = "parent",
            selectedOptions = mapOf("setup" to "standing"),
        )

        assertEquals(
            setOf("parent__standing__barbell", "parent__standing__cable"),
            compatibility.matchingConfigurationIds,
        )
        val implementOptions = compatibility.axes.single { it.axis == "implement" }.options
        assertTrue(implementOptions.single { it.value == "barbell" }.enabled)
        assertTrue(implementOptions.single { it.value == "cable" }.enabled)
        assertTrue(!implementOptions.single { it.value == "dumbbells" }.enabled)
    }

    @Test
    fun changing_the_first_level_reveals_only_the_next_contextual_level() = runTest {
        val repository = InMemoryExerciseCatalogRepositoryV2(catalog)
        repository.load()

        val compatibility = repository.compatibility(
            definitionId = "parent",
            selectedOptions = mapOf("setup" to "standing"),
        )

        assertEquals(listOf("setup", "implement"), compatibility.axes.map { it.axis })
        assertEquals(
            setOf("parent__standing__barbell", "parent__standing__cable"),
            compatibility.matchingConfigurationIds,
        )
        assertEquals(null, compatibility.exactConfigurationId)

        val fixed = repository.compatibility(
            definitionId = "parent",
            selectedOptions = mapOf("setup" to "seated"),
        )
        assertEquals(listOf("setup"), fixed.axes.map { it.axis })
        assertEquals("parent__seated__dumbbells", fixed.exactConfigurationId)
    }

    @Test
    fun validation_requires_an_exact_configuration() = runTest {
        val repository = InMemoryExerciseCatalogRepositoryV2(catalog)
        repository.load()

        val ambiguous = repository.validate(
            ExerciseSelectionDraftV2(
                definitionId = "parent",
                selectedOptions = mapOf("setup" to "standing"),
                catalogRevision = "test-revision",
            ),
        )
        assertTrue(ambiguous is ExerciseSelectionValidationV2.Invalid)

        val valid = repository.validate(
            ExerciseSelectionDraftV2(
                definitionId = "parent",
                selectedOptions = mapOf("setup" to "standing", "implement" to "cable"),
                catalogRevision = "test-revision",
            ),
        )
        assertEquals(
            ExerciseSelectionV2("parent", "parent__standing__cable", "test-revision"),
            (valid as ExerciseSelectionValidationV2.Valid).selection,
        )
    }

    @Test
    fun resolve_rejects_profiles_without_rich_metadata_instead_of_synthesizing_defaults() = runTest {
        val repository = InMemoryExerciseCatalogRepositoryV2(catalog)
        repository.load()

        val result = repository.resolve(
            ExerciseSelectionV2(
                definitionId = "parent",
                configurationId = "parent__standing__cable",
                catalogRevision = "test-revision",
            ),
        )

        assertTrue(result is ExerciseCatalogResolveResultV2.Invalid)
        assertEquals(
            "rich_metadata_missing:parent__standing__cable",
            (result as ExerciseCatalogResolveResultV2.Invalid).reason,
        )
    }}
