package com.example.kpkn.screens.sessioneditor

import com.example.kpkn.data.models.*
import com.example.kpkn.data.exercises.catalogv2.catalogV2SelectionIssues
import org.junit.Assert.*
import org.junit.Test

/** AUD2: imported/old sessions, superset holes and repeatable transfers. */
class SessionEditorAuditCopiesTest {
    private val recorded = ExerciseSet(
        id = "set", targetReps = 8, targetRIR = 2, intensityMode = IntensityMode.RIR,
        completedReps = 7, completedRPE = 9.0, completedDuration = 35,
        plannedIntensityTechniques = listOf(PlannedTechnique(id = "technique", type = TechniqueType.DROP_SET)),
    )
    private val exercise = Exercise(
        id = "exercise", name = "Press", exerciseDbId = "bench_press__barbell", occurrenceId = "occurrence",
        catalogRevision = "test", catalogDefinitionId = "bench_press", catalogConfigurationId = "bench_press__barbell",
        performanceProfileId = "profile-bench",
        sets = listOf(recorded),
        warmupSets = listOf(WarmupSetDefinition(id = "warmup", percentageOfWorkingWeight = 0.5, targetReps = 5)),
    )

    @Test fun addingASetDoesNotReuseRecordedResultsOrTechniqueIdentity() {
        val next = createNextSetTemplate(exercise, recorded)
        assertNull(next.completedReps)
        assertNull(next.completedRPE)
        assertNull(next.completedDuration)
        assertEquals(8, next.targetReps)
        assertNotEquals(recorded.id, next.id)
        assertNotEquals(recorded.plannedIntensityTechniques.single().id, next.plannedIntensityTechniques.single().id)
    }

    @Test fun aNewSetFromASupersetHoleIsExecutable() {
        val hole = recorded.copy(isEmptySlot = true)
        assertFalse(createNextSetTemplate(exercise, hole).isEmptySlot)
    }

    @Test fun duplicatingASetDoesNotCopyItsExecution() {
        val copy = exercise.withSetDuplicated(recorded.id).sets[1]
        assertNull(copy.completedReps)
        assertNull(copy.completedRPE)
        assertEquals(recorded.targetRIR, copy.targetRIR)
    }

    @Test fun duplicatingAnExerciseRegeneratesPreparationAndTechniqueIds() {
        val session = Session(id = "session", name = "Día", exercises = listOf(exercise))
        val copy = duplicateExerciseInSession(session, null, exercise.id).exercises[1]
        assertNotEquals(exercise.warmupSets.single().id, copy.warmupSets.single().id)
        assertNotEquals(recorded.plannedIntensityTechniques.single().id, copy.sets.single().plannedIntensityTechniques.single().id)
        assertNull(copy.sets.single().completedReps)
    }

    @Test fun repeatedTransfersNeverDuplicateCatalogOccurrences() {
        val source = Session(id = "source", name = "Origen", exercises = listOf(exercise))
        val once = mergeSessions(Session(id = "target", name = "Destino"), source, null, SessionCloneApplyMode.APPEND)
        val twice = mergeSessions(once, source, null, SessionCloneApplyMode.APPEND)
        assertFalse(twice.normalizedForEditorSave().catalogV2SelectionIssues().any { it.code == "duplicate_occurrence_id" })
        val occurrences = twice.allExercises().map { it.occurrenceId }
        assertEquals(2, occurrences.size)
        assertEquals(2, occurrences.toSet().size)
        assertTrue(occurrences.none { it == "occurrence" })
    }

    @Test fun transferToANewDayDoesNotShareSourceVariants() {
        val variant = Session(id = "variant", name = "B", exercises = listOf(exercise))
        val source = Session(id = "source", name = "Origen", exercises = listOf(exercise), sessionB = variant)
        val copied = createSessionFromPayload(source, 2, "Destino", buildClonePayload(source, null), null)
        assertNull(copied.sessionB)
        assertNull(copied.sessionC)
        assertNull(copied.sessionD)
    }

    @Test fun transferDoesNotCarryExecutedSets() {
        val source = Session(id = "source", name = "Origen", exercises = listOf(exercise))
        val copied = buildClonePayload(source, null).parts.single().exercises.single()
        assertNull(copied.sets.single().completedReps)
        assertNotEquals(recorded.plannedIntensityTechniques.single().id, copied.sets.single().plannedIntensityTechniques.single().id)
    }
}
