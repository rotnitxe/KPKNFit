package com.example.kpkn.domain.training

import com.example.kpkn.data.models.*
import com.example.kpkn.data.protocols.*
import org.junit.Assert.*
import org.junit.Test

class ExerciseTrainingMaxTest {
    private fun exercise(id: String = "press", config: String = CatalogIds.OHP, tm: Double? = 72.0,
        oneRm: Double? = 80.0) = Exercise(id, "Press militar", catalogConfigurationId = config,
        loadQuantityConvention = LoadQuantityConvention.TOTAL_EXTERNAL, referenceTrainingMax = tm, reference1RM = oneRm,
        trainingMode = TrainingMode.RM, sets = listOf(ExerciseSet("set-$id", targetPercentageRM = 85.0,
            loadBasis = LoadBasis.PERCENT_TM, weight = tm?.times(0.85))))
    private fun program(exercises: List<Exercise>, profile: PowerliftingProfile? = null): Program = Program("p", "Plan",
        powerliftingProfile = profile, macrocycles = listOf(Macrocycle("m", "Macro", blocks = listOf(Block("b", "Bloque",
            mesocycles = listOf(Mesocycle("meso", "Meso", weeks = listOf(ProgramWeek("w", "Semana",
                sessions = exercises.map { Session("session-${it.id}", "Fuerza", exercises = listOf(it)) })))))))))
    private fun all(program: Program) = ExerciseTrainingMax.exercises(program)

    @Test fun migratesFourStoredLegacyTmsIncludingOverheadWithoutInventingDates() {
        val inputs = listOf(exercise("squat", CatalogIds.SQ_LOW, null, null), exercise("bench", CatalogIds.BP, null, null),
            exercise("deadlift", CatalogIds.DL, null, null), exercise("press", CatalogIds.OHP, null, null))
        val migrated = ExerciseTrainingMax.migrate(program(inputs, Cal5TestSupport.fullProfile()))
        assertEquals(listOf(180.0, 108.0, 198.0, 72.0), all(migrated).map {
            ExerciseTrainingMax.capturedKg(migrated, it, PlanLoadReferenceKind.EXERCISE_TM) })
        assertTrue(migrated.exerciseLoadReferences.flatMap { it.references }.all { it.capturedAtMs == null })
        assertEquals(migrated, ExerciseTrainingMax.migrate(migrated))
    }

    @Test fun aProfileOneRmWithoutStoredTmDoesNotMigrateAnInferredTm() {
        val source = program(listOf(exercise(tm = null, oneRm = null)), PowerliftingProfile(overhead1RM = 80.0))
        val migrated = ExerciseTrainingMax.migrate(source)
        assertNull(ExerciseTrainingMax.capturedKg(migrated, all(migrated).single(), PlanLoadReferenceKind.EXERCISE_TM))
    }

    @Test fun manualTmOutsideTheFourLiftsDoesNotNeedAProfileOrCreateOneRm() {
        val row = exercise(config = CatalogIds.ROW, tm = null, oneRm = null)
        val captured = ExerciseTrainingMax.capture(program(listOf(row)), row, PlanLoadReferenceKind.EXERCISE_TM,
            100.0, 20, "manual", "Confirmado")
        assertEquals(100.0, ExerciseTrainingMax.capturedKg(captured, row, PlanLoadReferenceKind.EXERCISE_TM)!!, 0.0)
        assertNull(ExerciseTrainingMax.capturedKg(captured, row, PlanLoadReferenceKind.EXERCISE_1RM))
        assertNull(captured.powerliftingProfile)
    }

    @Test fun editingCanonicalTmSynchronizesProfileWithoutChangingAnotherConfiguration() {
        val canonical = exercise()
        val variant = exercise("seated", CatalogIds.OHP_SEATED, 60.0, 70.0)
        val source = program(listOf(canonical, variant), PowerliftingProfile(overheadTM = 72.0, overhead1RM = 80.0))
        val captured = ExerciseTrainingMax.capture(source, canonical, PlanLoadReferenceKind.EXERCISE_TM, 75.0, 20, "manual", "Confirmado")
        assertEquals(75.0, captured.powerliftingProfile!!.overheadTM!!, 0.0)
        assertEquals(80.0, captured.powerliftingProfile!!.overhead1RM!!, 0.0)
        assertEquals(60.0, ExerciseTrainingMax.capturedKg(captured, variant, PlanLoadReferenceKind.EXERCISE_TM)!!, 0.0)
    }

    @Test fun cycleProfileAdvanceIsNotHiddenByAnOlderPoolSnapshot() {
        val row = exercise()
        val before = ExerciseTrainingMax.migrate(program(listOf(row), PowerliftingProfile(overheadTM = 72.0, overhead1RM = 80.0)))
        val after = ExerciseTrainingMax.syncProfile(before, before.copy(powerliftingProfile = before.powerliftingProfile!!.copy(overheadTM = 74.5)),
            100, "cycle-2", "Ciclo")
        assertEquals(74.5, ExerciseTrainingMax.capturedKg(after, row, PlanLoadReferenceKind.EXERCISE_TM)!!, 0.0)
        assertEquals(2, ExerciseLoadReferenceStore.history(after, row.id, ExerciseTrainingMax.request(row, PlanLoadReferenceKind.EXERCISE_TM)!!).size)
    }

    @Test fun refreshProtectsExecutedAndManualSessionsAndRecalculatesOnlyPending() {
        val first = exercise("done")
        val manual = exercise("manual")
        val pending = exercise("pending")
        val source = program(listOf(first, manual, pending)).copy(manualSessionOverrides = listOf(ManualSessionOverride("session-manual")))
        val captured = ExerciseTrainingMax.capture(source, pending, PlanLoadReferenceKind.EXERCISE_TM, 100.0, 20, "manual", "Confirmado")
        val refreshed = ExerciseTrainingMax.refreshPending(captured, emptySet(), setOf("session-done"))
        assertEquals(first, all(refreshed)[0])
        assertEquals(manual, all(refreshed)[1])
        assertEquals(85.0, all(refreshed)[2].sets.single().weight!!, 0.0)
    }

    @Test fun preparationRequestsOnlyMissingTmAndDoesNotApplyItsSuggestion() {
        val ready = exercise()
        val missing = exercise("missing", tm = null)
        val oneRmOnly = exercise("one-rm", tm = null).let { it.copy(sets = it.sets.map { set -> set.copy(loadBasis = LoadBasis.PERCENT_1RM) }) }
        val source = program(listOf(ready, missing, oneRmOnly))
        val requests = ExerciseTrainingMax.missing(source, Session("s", "Fuerza", exercises = listOf(ready, missing, oneRmOnly)))
        assertEquals(listOf("missing"), requests.map { it.exercise.id })
        assertNull(requests.single().suggestedKg) // No recipe/fraction was declared.
        assertNull(missing.referenceTrainingMax)
    }

    @Test fun unilateralObservationIsNeverResolvedAsGlobalTmOrOneRm() {
        val row = exercise(tm = null, oneRm = null)
        val observed = PlanLoadReference(PlanLoadReferenceKind.OBSERVED_WORKING_SET, CatalogIds.OHP,
            LoadQuantityConvention.TOTAL_EXTERNAL, side = "left", repMin = 6, repMax = 6,
            state = PlanLoadReferenceState.CAPTURED, capturedLoadKg = 50.0)
        val source = ExerciseLoadReferenceStore.appendCapture(program(listOf(row)), setOf(row.id), observed)
        assertNull(ExerciseTrainingMax.capturedKg(source, row, PlanLoadReferenceKind.EXERCISE_TM))
        assertNull(ExerciseTrainingMax.capturedKg(source, row, PlanLoadReferenceKind.EXERCISE_1RM))
    }
}
