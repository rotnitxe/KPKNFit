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
    fun retired_cross_definition_mapping_requires_exact_source_pair_and_existing_target() {
        val template = catalog.families[1].definitions.single()
        val sumo = template.copy(
            id = "romanian_sumo_deadlift",
            configurations = listOf(template.configurations.single().copy(id = "romanian_sumo_deadlift__bilateral__barbell")),
            defaultConfigurationId = "romanian_sumo_deadlift__bilateral__barbell",
        )
        val target = template.copy(
            id = "romanian_deadlift",
            configurations = listOf(template.configurations.single().copy(id = "romanian_deadlift__unilateral__barbell")),
            defaultConfigurationId = "romanian_deadlift__unilateral__barbell",
        )
        val family = catalog.families[1].copy(definitions = listOf(sumo, target))
        val resolver = ExerciseCatalogV2Resolver(catalog.copy(families = listOf(family)))
        val saved = ExerciseSelectionV2("romanian_sumo_deadlift", "romanian_sumo_deadlift__unilateral__barbell", catalog.catalogRevision)
        val migrated = resolver.validate(saved) as ExerciseSelectionValidationV2.Valid
        assertEquals("romanian_deadlift", migrated.selection.definitionId)
        assertEquals("romanian_deadlift__unilateral__barbell", migrated.selection.configurationId)
        assertTrue(resolver.validate(saved.copy(definitionId = "romanian_deadlift")) is ExerciseSelectionValidationV2.Invalid)
        assertTrue(resolver.validate(saved.copy(catalogRevision = "different")) is ExerciseSelectionValidationV2.Invalid)
        val missingTarget = ExerciseCatalogV2Resolver(catalog.copy(families = listOf(family.copy(definitions = listOf(sumo)))))
        assertTrue(missingTarget.validate(saved) is ExerciseSelectionValidationV2.Invalid)
        assertNull(missingTarget.resolve(saved))
    }

    /** A complete lunge fixture; the shared squat fixture remains unchanged. */
    private fun lungeRetirementCatalog(implement: String): ExerciseCatalogV2 {
        fun family(definitionId: String, equipmentId: String): ExerciseFamilyV2 {
            val configurationId = "${definitionId}__$equipmentId"
            val name = when (definitionId) {
                "walking_lunge" -> "Zancada Caminando"
                "forward_lunge" -> "Zancada Frontal"
                "reverse_lunge" -> "Zancada Inversa"
                else -> error("Unexpected lunge definition: $definitionId")
            }
            val profile = ResolvedExerciseProfileV2(
                movementPatternId = "unilateral_knee_dominant",
                bodyRegion = ExerciseBodyRegionV2.LOWER,
                kineticChain = ExerciseKineticChainV2.ANTERIOR,
                laterality = ExerciseLateralityV2.UNILATERAL,
                equipmentId = equipmentId,
                loadMode = when (equipmentId) {
                    "smith_machine" -> "guided_external_load"
                    "cable" -> "continuous_cable"
                    else -> "free_external_load"
                },
                primaryMuscles = listOf("quadriceps", "gluteus_maximus"),
                secondaryMuscles = listOf("hamstrings"),
                stabilizerMuscles = listOf("core"),
                jointInvolvement = listOf(
                    JointInvolvementV2("rodilla", JointRoleV2.PRIMARY, listOf("Flexión Y Extensión Unilateral")),
                    JointInvolvementV2("cadera", JointRoleV2.PRIMARY, listOf("Extensión Y Control Frontal")),
                    JointInvolvementV2("tobillo", JointRoleV2.SECONDARY, listOf("Equilibrio Y Dorsiflexión")),
                    JointInvolvementV2("sacroiliaca", JointRoleV2.STABILIZER, listOf("Transferencia Unilateral")),
                ),
                efc = 2.8,
                cnc = 2.2,
                ssc = 0.5,
                ttc = 2.0,
                axialLoadFactor = 0.0,
                technicalDifficulty = 4.2,
                resistanceProfile = when (equipmentId) {
                    "smith_machine" -> "guided_constant"
                    "cable" -> "continuous_cable"
                    else -> "gravity_constant"
                },
                setupCues = listOf("Alinea el apoyo y mantén la pelvis estable."),
                executionCues = listOf("Controla el descenso y empuja con la pierna de trabajo."),
                performanceProfileId = "${configurationId}__zancada",
                description = "$name con configuración explícita $equipmentId.",
                articulationType = ExerciseArticulationTypeV2.MULTIARTICULAR,
                setupTimeSeconds = if (equipmentId == "smith_machine") 50 else 35,
                fatigueTier = ExerciseFatigueTierV2.MEDIA,
            )
            val definition = ExerciseDefinitionV2(
                id = definitionId,
                familyId = "lower_$definitionId",
                kind = ExerciseDefinitionKindV2.PARENT,
                canonicalName = name,
                description = "$name con implementación materializada para probar identidades.",
                searchTerms = listOf(name),
                optionAxes = listOf("implement"),
                configurations = listOf(
                    ExerciseConfigurationV2(
                        id = configurationId,
                        selectedOptions = mapOf("implement" to equipmentId),
                        displaySummary = equipmentId,
                        profile = profile,
                        evidence = evidence(),
                    ),
                ),
                defaultConfigurationId = configurationId,
                evidence = evidence(),
            )
            return ExerciseFamilyV2(
                id = definition.familyId,
                canonicalName = name,
                definitions = listOf(definition),
                evidence = evidence(),
            )
        }
        return catalog.copy(
            families = listOf(
                family("walking_lunge", "barbell"),
                family("forward_lunge", implement),
                family("reverse_lunge", implement),
            ),
        )
    }

    @Test
    fun retired_walking_setups_require_exact_saved_pair_revision_and_existing_target() {
        listOf("smith_machine", "cable").forEach { implement ->
            val testCatalog = lungeRetirementCatalog(implement)
            val oldId = "walking_lunge__$implement"
            val newId = "forward_lunge__$implement"
            val resolver = ExerciseCatalogV2Resolver(testCatalog)
            val saved = ExerciseSelectionV2("walking_lunge", oldId, testCatalog.catalogRevision)
            val migrated = resolver.validate(saved) as ExerciseSelectionValidationV2.Valid
            assertEquals("forward_lunge", migrated.selection.definitionId)
            assertEquals(newId, migrated.selection.configurationId)
            assertEquals(testCatalog.catalogRevision, migrated.selection.catalogRevision)
            val expectedProfile = testCatalog.families.single { it.id == "lower_forward_lunge" }
                .definitions.single().configurations.single().profile
            val resolved = resolver.resolve(saved) ?: error("Retired walking selection did not resolve")
            assertEquals(expectedProfile, resolved)
            assertEquals("unilateral_knee_dominant", resolved.movementPatternId)
            assertEquals(ExerciseBodyRegionV2.LOWER, resolved.bodyRegion)
            assertEquals(ExerciseKineticChainV2.ANTERIOR, resolved.kineticChain)
            assertEquals(ExerciseLateralityV2.UNILATERAL, resolved.laterality)
            assertEquals(implement, resolved.equipmentId)
            assertEquals("${newId}__zancada", resolved.performanceProfileId)
            assertEquals(
                if (implement == "smith_machine") "guided_external_load" else "continuous_cable",
                resolved.loadMode,
            )
            assertTrue(resolver.validate(saved.copy(definitionId = "forward_lunge")) is ExerciseSelectionValidationV2.Invalid)
            assertTrue(resolver.validate(saved.copy(definitionId = "reverse_lunge")) is ExerciseSelectionValidationV2.Invalid)
            assertTrue(resolver.validate(saved.copy(catalogRevision = "different")) is ExerciseSelectionValidationV2.Invalid)
            val missing = ExerciseCatalogV2Resolver(
                testCatalog.copy(families = testCatalog.families.filterNot { it.id == "lower_forward_lunge" }),
            )
            assertTrue(missing.validate(saved) is ExerciseSelectionValidationV2.Invalid)
            assertNull(missing.resolve(saved))
        }
    }

    @Test
    fun ordinary_forward_and_reverse_smith_and_cable_keep_their_exact_profiles() {
        listOf("smith_machine", "cable").forEach { implement ->
            val testCatalog = lungeRetirementCatalog(implement)
            val resolver = ExerciseCatalogV2Resolver(testCatalog)
            listOf("forward_lunge", "reverse_lunge").forEach { definitionId ->
                val selection = ExerciseSelectionV2(definitionId, "${definitionId}__$implement", testCatalog.catalogRevision)
                val validation = resolver.validate(selection) as ExerciseSelectionValidationV2.Valid
                assertEquals(selection, validation.selection)
                val expectedProfile = testCatalog.families.single { it.id == "lower_$definitionId" }
                    .definitions.single().configurations.single().profile
                val resolved = resolver.resolve(selection) ?: error("Ordinary lunge selection did not resolve")
                assertEquals(expectedProfile, resolved)
                assertEquals(implement, resolved.equipmentId)
                assertEquals(ExerciseLateralityV2.UNILATERAL, resolved.laterality)
                assertEquals("${definitionId}__${implement}__zancada", resolved.performanceProfileId)
            }
        }
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
