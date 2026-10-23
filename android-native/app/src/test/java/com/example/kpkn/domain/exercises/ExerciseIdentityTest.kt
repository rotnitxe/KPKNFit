package com.example.kpkn.domain.exercises

import com.example.kpkn.data.models.Block
import com.example.kpkn.data.models.DropSetData
import com.example.kpkn.data.models.Exercise
import com.example.kpkn.data.models.ExerciseMuscleInfo
import com.example.kpkn.data.models.ExerciseSet
import com.example.kpkn.data.models.IntensityMode
import com.example.kpkn.data.models.LoadModeV2
import com.example.kpkn.data.models.Macrocycle
import com.example.kpkn.data.models.Mesocycle
import com.example.kpkn.data.models.Program
import com.example.kpkn.data.models.ProgramWeek
import com.example.kpkn.data.models.RestPauseData
import com.example.kpkn.data.models.Session
import com.example.kpkn.data.models.TrainingMode
import com.example.kpkn.data.models.UnilateralTarget
import com.example.kpkn.data.models.WarmupSetDefinition
import com.example.kpkn.data.protocols.PlanProvenance
import com.example.kpkn.data.protocols.PlanProvenanceClass
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class ExerciseIdentityTest {

    @Test
    fun imported_legacy_occurrences_recover_only_with_complete_unambiguous_identity_per_session() {
        val unique = catalogV2Exercise("unique-slot", name = "Alias importado sin coincidencia de nombre")
        val duplicateA = catalogV2Exercise("duplicate-slot")
        val duplicateB = duplicateA.copy(name = "Otra variante del alias")
        val missingRevision = catalogV2Exercise("missing-revision").copy(catalogRevision = " ")
        val missingDefinition = catalogV2Exercise("missing-definition").copy(catalogDefinitionId = " ")
        val missingConfiguration = catalogV2Exercise("missing-configuration").copy(catalogConfigurationId = " ")
        val missingProfile = catalogV2Exercise("missing-profile").copy(performanceProfileId = " ")
        val incompleteMetadata = listOf(missingRevision, missingDefinition, missingConfiguration, missingProfile)
        val existing = catalogV2Exercise("existing-slot", occurrenceId = "persisted-occurrence")
        val collidingCandidate = catalogV2Exercise("claimed-occurrence")
        val collisionOwner = catalogV2Exercise("occurrence-owner", occurrenceId = "claimed-occurrence")
        val variant = catalogV2Exercise("variant-slot", name = "Alias de variante")
        val sourceSession = Session(
            id = "source-session",
            name = "Día A",
            exercises = listOf(unique, duplicateA, duplicateB) + incompleteMetadata +
                listOf(existing, collidingCandidate, collisionOwner),
            sessionB = Session(id = "source-session-b", name = "Día B", exercises = listOf(variant)),
        )
        val separateSession = Session(
            id = "separate-session",
            name = "Día C",
            exercises = listOf(catalogV2Exercise("duplicate-slot")),
        )

        val normalized = programWithSessions(listOf(sourceSession, separateSession)).normalizedIdentityFields()
        val sessions = normalized.macrocycles.single().blocks.single().mesocycles.single().weeks.single().sessions
        val normalizedSource = sessions[0]
        val byId = normalizedSource.exercises.associateBy { it.id }

        assertEquals("unique-slot", byId.getValue("unique-slot").occurrenceId)
        assertNull(byId.getValue("duplicate-slot").occurrenceId)
        incompleteMetadata.forEach { exercise ->
            assertNull("${exercise.id} must remain incomplete", byId.getValue(exercise.id).occurrenceId)
        }
        assertEquals("persisted-occurrence", byId.getValue("existing-slot").occurrenceId)
        assertNull(byId.getValue("claimed-occurrence").occurrenceId)
        assertEquals("claimed-occurrence", byId.getValue("occurrence-owner").occurrenceId)
        assertEquals("variant-slot", normalizedSource.sessionB?.exercises?.single()?.occurrenceId)
        assertEquals("duplicate-slot", sessions[1].exercises.single().occurrenceId)

        val recovered = byId.getValue("unique-slot")
        assertEquals(unique.sets, recovered.sets)
        assertEquals(unique.reference1RM, recovered.reference1RM)
        assertEquals(unique.referenceTrainingMax, recovered.referenceTrainingMax)
    }

    @Test
    fun non_imported_program_keeps_missing_occurrence_blocked() {
        val legacy = catalogV2Exercise("legacy-slot")
        val program = programWithSessions(
            sessions = listOf(Session(id = "session", name = "Día A", exercises = listOf(legacy))),
            provenance = null,
        )

        val normalized = program.normalizedIdentityFields()
        val recovered = normalized.macrocycles.single().blocks.single().mesocycles.single()
            .weeks.single().sessions.single().exercises.single()

        assertNull(recovered.occurrenceId)
    }

    @Test
    fun replacement_resets_incompatible_load_and_context_data() {
        val old = Exercise(
            id = "slot-1",
            name = "Press banca",
            exerciseDbId = "bench",
            trainingMode = TrainingMode.RM,
            reference1RM = 180.0,
            referenceTrainingMax = 160.0,
            warmupSets = listOf(WarmupSetDefinition(id = "w1", percentageOfWorkingWeight = 50.0, targetReps = 5)),
            sets = listOf(
                ExerciseSet(
                    id = "s1",
                    targetReps = 8,
                    weight = 160.0,
                    targetPercentageRM = 85.0,
                    intensityMode = IntensityMode.SOLO_RM,
                    completedReps = 8,
                    completedRPE = 9.0,
                    loadModeV2 = LoadModeV2.LOAD,
                    contextKeyV2 = "bench-context",
                    setupId = "rack-1",
                    leftTarget = UnilateralTarget(weight = 80.0, targetReps = 8),
                    rightTarget = UnilateralTarget(weight = 82.5, targetReps = 8),
                    dropSets = listOf(DropSetData(weight = 120.0, reps = 6)),
                    restPauses = listOf(RestPauseData(restTime = 20, reps = 3)),
                )
            ),
        )
        val replacement = ExerciseMuscleInfo(
            id = "tren_superior_press_inclinado_maquina_convergente",
            name = "Press inclinado en máquina convergente",
            equipment = "Máquina",
        )

        val updated = old.replacedWithCatalogExercise(replacement)
        val set = updated.sets.single()

        assertEquals(replacement.name, updated.name)
        assertEquals(replacement.id, updated.exerciseDbId)
        assertNull(set.weight)
        assertNull(set.targetPercentageRM)
        assertNull(set.intensityMode)
        assertNull(set.completedReps)
        assertNull(set.completedRPE)
        assertNull(set.contextKeyV2)
        assertNull(set.setupId)
        assertNull(set.leftTarget?.weight)
        assertNull(set.rightTarget?.weight)
        assertTrue(set.dropSets.isEmpty())
        assertTrue(set.restPauses.isEmpty())
        assertTrue(updated.warmupSets.isEmpty())
        assertNull(updated.reference1RM)
        assertNull(updated.referenceTrainingMax)
        assertEquals(TrainingMode.REPS, updated.trainingMode)
    }

    @Test
    fun bodyweight_replacement_defaults_to_bodyweight_load_mode() {
        val old = Exercise(
            id = "slot-1",
            name = "Curl",
            sets = listOf(ExerciseSet(id = "s1", targetReps = 10, weight = 30.0)),
        )
        val replacement = ExerciseMuscleInfo(
            id = "tren_inferior_sentadilla_sissy",
            name = "Sentadilla Sissy",
            equipment = "Peso corporal",
        )

        val updated = old.replacedWithCatalogExercise(replacement)

        assertEquals(LoadModeV2.BODYWEIGHT, updated.sets.single().loadModeV2)
        assertNull(updated.sets.single().weight)
    }

    @Test
    fun assisted_replacement_defaults_to_assisted_without_rm_mode() {
        val old = Exercise(
            id = "slot-1",
            name = "Dominada",
            trainingMode = TrainingMode.RM,
            sets = listOf(ExerciseSet(id = "s1", targetReps = 6, targetPercentageRM = 75.0)),
        )
        val replacement = ExerciseMuscleInfo(
            id = "dominada_asistida_maquina",
            name = "Dominada asistida",
            equipment = "Máquina asistida",
        )

        val updated = old.replacedWithCatalogExercise(replacement)
        val set = updated.sets.single()

        assertEquals(TrainingMode.REPS, updated.trainingMode)
        assertEquals(LoadModeV2.ASSISTED, set.loadModeV2)
        assertNull(set.targetPercentageRM)
        assertNull(set.intensityMode)
    }

    private fun catalogV2Exercise(
        id: String,
        name: String = "Press banca",
        occurrenceId: String? = null,
    ) = Exercise(
        id = id,
        name = name,
        exerciseDbId = "bench__flat__barbell",
        exerciseId = "bench__flat__barbell",
        canonicalExerciseId = "bench",
        exerciseFamilyId = "bench",
        catalogRevision = "catalog-r7",
        catalogDefinitionId = "bench",
        catalogConfigurationId = "bench__flat__barbell",
        performanceProfileId = "bench-profile",
        occurrenceId = occurrenceId,
        reference1RM = 120.0,
        referenceTrainingMax = 100.0,
        sets = listOf(ExerciseSet(id = "$id-set", targetReps = 8, weight = 80.0)),
    )

    private fun programWithSessions(
        sessions: List<Session>,
        provenance: PlanProvenance? = PlanProvenance(
            planId = "imported:legacy-json",
            category = PlanProvenanceClass.IMPORTED,
        ),
    ) = Program(
        id = "legacy-import",
        name = "Importado legacy",
        planProvenance = provenance,
        macrocycles = listOf(
            Macrocycle(
                id = "macro",
                name = "Macro",
                blocks = listOf(
                    Block(
                        id = "block",
                        name = "Bloque",
                        mesocycles = listOf(
                            Mesocycle(
                                id = "meso",
                                name = "Meso",
                                weeks = listOf(ProgramWeek(id = "week", name = "Semana 1", sessions = sessions)),
                            ),
                        ),
                    ),
                ),
            ),
        ),
    )
}
