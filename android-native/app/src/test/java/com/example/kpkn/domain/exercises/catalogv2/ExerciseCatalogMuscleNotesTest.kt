package com.example.kpkn.domain.exercises.catalogv2

import com.example.kpkn.data.exercises.catalogv2.toLegacySelection
import com.example.kpkn.data.models.MuscleRole
import kotlinx.serialization.json.Json
import org.junit.Assert.assertEquals
import org.junit.Assert.assertThrows
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * Covers the editorial muscleNotes contract end to end: the loader rejects
 * orphan, duplicate or missing notes, and the legacy adapter propagates the
 * curated biomechanical reason to the volume pipeline.
 */
class ExerciseCatalogMuscleNotesTest {
    private val codec = Json { ignoreUnknownKeys = false; encodeDefaults = true }

    @Test
    fun loader_accepts_profile_with_exact_muscle_notes() {
        val catalog = catalogWithProfile(
            profile = profile(
                primary = listOf("hamstrings", "gluteus_maximus"),
                secondary = emptyList(),
                stabilizer = listOf("erector_spinae", "core"),
                notes = listOf(
                    MuscleNoteV2("hamstrings", "Principal: los isquiosurales controlan el descenso y extienden la cadera en la subida; por eso suman la serie completa."),
                    MuscleNoteV2("gluteus_maximus", "Principal: el glúteo mayor extiende la cadera al enderezar el tronco y completa la potencia del patrón."),
                    MuscleNoteV2("erector_spinae", "Estabilizador: trabaja isométricamente para mantener la columna neutra durante toda la bisagra."),
                    MuscleNoteV2("core", "Estabilizador: protege la columna y transmite la carga entre el tronco y la pelvis durante la bisagra."),
                ),
            ),
        )
        ExerciseCatalogV2Loader.decodeApproved(catalog)
    }

    @Test
    fun loader_rejects_orphan_muscle_note() {
        val catalog = catalogWithProfile(
            profile = profile(
                primary = listOf("hamstrings"),
                secondary = emptyList(),
                stabilizer = emptyList(),
                notes = listOf(
                    MuscleNoteV2("hamstrings", "Principal: los isquiosurales controlan el descenso y extienden la cadera en la subida; por eso suman la serie completa."),
                    MuscleNoteV2("biceps", "Principal: el bíceps flexiona el codo y concentra el trabajo del movimiento."),
                ),
            ),
        )
        assertThrows(IllegalArgumentException::class.java) {
            ExerciseCatalogV2Loader.decodeApproved(catalog)
        }
    }

    @Test
    fun loader_rejects_missing_muscle_note() {
        val catalog = catalogWithProfile(
            profile = profile(
                primary = listOf("hamstrings"),
                secondary = emptyList(),
                stabilizer = listOf("core"),
                notes = listOf(
                    MuscleNoteV2("hamstrings", "Principal: los isquiosurales controlan el descenso y extienden la cadera en la subida; por eso suman la serie completa."),
                ),
            ),
        )
        assertThrows(IllegalArgumentException::class.java) {
            ExerciseCatalogV2Loader.decodeApproved(catalog)
        }
    }

    @Test
    fun loader_rejects_short_muscle_note() {
        val catalog = catalogWithProfile(
            profile = profile(
                primary = listOf("hamstrings"),
                secondary = emptyList(),
                stabilizer = emptyList(),
                notes = listOf(MuscleNoteV2("hamstrings", "Nota corta.")),
            ),
        )
        assertThrows(IllegalArgumentException::class.java) {
            ExerciseCatalogV2Loader.decodeApproved(catalog)
        }
    }

    @Test
    fun adapter_propagates_biomechanical_reason_from_muscle_notes() {
        val catalog = catalogWithProfile(
            profile = profile(
                primary = listOf("hamstrings"),
                secondary = emptyList(),
                stabilizer = emptyList(),
                notes = listOf(
                    MuscleNoteV2("hamstrings", "Principal: los isquiosurales controlan el descenso y extienden la cadera en la subida; por eso suman la serie completa."),
                ),
            ),
        )
        val catalogV2 = ExerciseCatalogV2Loader.decodeApproved(catalog)
        val definition = catalogV2.families[0].definitions[0]
        val configuration = definition.configurations[0]
        val legacy = catalogV2.toLegacySelection(
            ExerciseSelectionV2(
                definitionId = definition.id,
                configurationId = configuration.id,
                catalogRevision = catalogV2.catalogRevision,
            ),
        )
        assertTrue(legacy != null)
        val involved = legacy!!.involvedMuscles.single { it.role == MuscleRole.PRIMARY }
        assertTrue(involved.biomechanicalReason!!.contains("isquiosurales"))
        assertEquals(null, involved.volumeContribution)
    }

    @Test
    fun loader_rejects_muscle_in_multiple_roles() {
        val catalog = catalogWithProfile(
            profile = profile(
                primary = listOf("abdominals"),
                secondary = listOf("abdominals"),
                stabilizer = emptyList(),
                notes = listOf(
                    MuscleNoteV2("abdominals", "Principal: el abdomen flexiona o fija el tronco y concentra la fuerza del movimiento."),
                ),
            ),
        )
        assertThrows(IllegalArgumentException::class.java) {
            ExerciseCatalogV2Loader.decodeApproved(catalog)
        }
    }

    private fun catalogWithProfile(profile: ResolvedExerciseProfileV2): String {
        val profileB = profile.copy(
            equipmentId = "dumbbells",
            performanceProfileId = "parent_a__dumbbells",
            richMetadata = profile.richMetadata?.copy(
                identity = profile.richMetadata!!.identity.copy(
                    configurationId = "parent_a__dumbbells",
                    performanceProfileId = "parent_a__dumbbells",
                ),
                biomechanics = profile.richMetadata!!.biomechanics.copy(equipmentId = "dumbbells"),
                programming = profile.richMetadata!!.programming.copy(requiredEquipment = listOf("dumbbells")),
                replacement = profile.richMetadata!!.replacement.copy(compatibleEquipmentIds = listOf("dumbbells")),
                display = profile.richMetadata!!.display.copy(
                    displaySummary = "dumbbells",
                    selectedOptions = mapOf("implement" to "dumbbells"),
                ),
            ),
        )
        val catalog = ExerciseCatalogV2(
            schemaVersion = 2,
            catalogRevision = "muscle-notes-test",
            ontologyRevision = "test",
            families = listOf(
                ExerciseFamilyV2(
                    id = "family_a",
                    canonicalName = "Familia A",
                    description = "Familia de prueba con muscleNotes para el contrato editorial.",
                    definitions = listOf(
                        ExerciseDefinitionV2(
                            id = "parent_a",
                            familyId = "family_a",
                            kind = ExerciseDefinitionKindV2.PARENT,
                            canonicalName = "Padre A",
                            description = "Padre de prueba con configuraciones explícitas y muscleNotes completas.",
                            optionAxes = listOf("implement"),
                            configurations = listOf(
                                ExerciseConfigurationV2(
                                    id = "parent_a__barbell",
                                    selectedOptions = mapOf("implement" to "barbell"),
                                    displaySummary = "barbell",
                                    profile = profile,
                                    evidence = CatalogEvidenceV2(
                                        reviewStatus = CatalogReviewStatusV2.APPROVED,
                                        confidence = CatalogConfidenceV2.HIGH,
                                        evidenceRefs = listOf("test"),
                                    ),
                                ),
                                ExerciseConfigurationV2(
                                    id = "parent_a__dumbbells",
                                    selectedOptions = mapOf("implement" to "dumbbells"),
                                    displaySummary = "dumbbells",
                                    profile = profileB,
                                    evidence = CatalogEvidenceV2(
                                        reviewStatus = CatalogReviewStatusV2.APPROVED,
                                        confidence = CatalogConfidenceV2.HIGH,
                                        evidenceRefs = listOf("test"),
                                    ),
                                ),
                            ),
                            defaultConfigurationId = "parent_a__barbell",
                            evidence = CatalogEvidenceV2(
                                reviewStatus = CatalogReviewStatusV2.APPROVED,
                                confidence = CatalogConfidenceV2.HIGH,
                                evidenceRefs = listOf("test"),
                            ),
                        ),
                    ),
                    evidence = CatalogEvidenceV2(
                        reviewStatus = CatalogReviewStatusV2.APPROVED,
                        confidence = CatalogConfidenceV2.HIGH,
                        evidenceRefs = listOf("test"),
                    ),
                ),
            ),
        )
        return codec.encodeToString(ExerciseCatalogV2.serializer(), catalog)
    }

    private fun profile(
        primary: List<String>,
        secondary: List<String>,
        stabilizer: List<String>,
        notes: List<MuscleNoteV2>,
    ) = ResolvedExerciseProfileV2(
        movementPatternId = "hip_hinge",
        bodyRegion = ExerciseBodyRegionV2.LOWER,
        kineticChain = ExerciseKineticChainV2.POSTERIOR,
        laterality = ExerciseLateralityV2.BILATERAL,
        equipmentId = "barbell",
        loadMode = "external",
        primaryMuscles = primary,
        secondaryMuscles = secondary,
        stabilizerMuscles = stabilizer,
        muscleNotes = notes,
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
        performanceProfileId = "parent_a__barbell",
        replacementGroup = "test",
        replacementPriority = 1,
        automationEligible = true,
        description = "Bisagra de cadera de prueba con descripción suficientemente larga para el gate de aprobación del catálogo.",
        richMetadata = ResolvedExerciseMetadataV2(
            identity = ExerciseIdentityMetadataV2(
                catalogRevision = "muscle-notes-test",
                familyId = "family_a",
                definitionId = "parent_a",
                configurationId = "parent_a__barbell",
                canonicalName = "Padre A",
                searchTerms = emptyList(),
                kind = ExerciseDefinitionKindV2.PARENT,
                performanceProfileId = "parent_a__barbell",
            ),
            anatomy = ExerciseAnatomyMetadataV2(
                primaryMuscles = primary,
                secondaryMuscles = secondary,
                stabilizerMuscles = stabilizer,
                targetRegions = listOf(primary.firstOrNull() ?: "muscle"),
                jointActions = listOf("hip_hinge"),
                muscleLengthBias = "mixed_controlled",
                volumeContribution = "direct",
                stabilizationDemand = "moderate",
            ),
            biomechanics = ExerciseBiomechanicsMetadataV2(
                movementPatternId = "hip_hinge",
                bodyRegion = ExerciseBodyRegionV2.LOWER,
                kineticChain = ExerciseKineticChainV2.POSTERIOR,
                laterality = ExerciseLateralityV2.BILATERAL,
                equipmentId = "barbell",
                loadMode = "external",
                resistanceProfile = "test",
                rangeOfMotion = "controlled_full_available",
                stability = "self_stabilized",
                relevantJoints = listOf("hip", "knee"),
                relevantTendons = listOf("hip_tendon"),
            ),
            programming = ExerciseProgrammingMetadataV2(
                role = "primary_compound",
                objectives = listOf("Desarrollar fuerza."),
                suitableRepRanges = listOf("4-8"),
                indicativeRestSeconds = IntRangeV2(min = 60, max = 120),
                fatigueCost = "moderate",
                recoveryCost = "moderate",
                requiredEquipment = listOf("barbell"),
                setupTransitionCost = "low",
                splitSuitability = listOf("full_body"),
            ),
            fatigue = ExerciseFatigueMetadataV2(
                efc = 1.0, cnc = 1.0, ssc = 0.0, ttc = 1.0,
                axialLoadFactor = 0.0, technicalDifficulty = 2.0,
            ),
            replacement = ExerciseReplacementMetadataV2(
                replacementGroup = "test",
                replacementPriority = 1,
                compatibleEquipmentIds = listOf("barbell"),
                preservesIntent = listOf("Conserva el patrón."),
            ),
            coaching = ExerciseCoachingMetadataV2(
                setup = listOf("setup"),
                execution = listOf("execute"),
                cues = listOf("cue"),
                commonMistakes = listOf("mistake"),
                progressions = listOf("progresión"),
                regressions = listOf("regresión"),
                relevantMobility = listOf("movilidad"),
            ),
            safety = ExerciseSafetyMetadataV2(
                risks = emptyList(),
                precautions = listOf("Precaución."),
                medicalDisclaimerRequired = false,
            ),
            display = ExerciseDisplayMetadataV2(
                displayName = "Padre A",
                displaySummary = "barbell",
                selectedOptions = mapOf("implement" to "barbell"),
            ),
            evidenceConfidence = CatalogConfidenceV2.HIGH,
        ),
    )
}
