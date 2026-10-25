package com.example.kpkn.domain.training

import com.example.kpkn.data.models.*
import com.example.kpkn.data.protocols.*
import org.junit.Assert.*
import org.junit.BeforeClass
import org.junit.Test

class Cal5bMaterializerReferenceTest {
    companion object { @JvmStatic @BeforeClass fun install() { CatalogCompositionTestSupport.install() } }

    @Test fun pendingDeclarationsUseLatestPoolCaptureOfTheirOwnKind() {
        val original = Cal5TestSupport.recipeOf("wendler-531-bbb")
        val firstSlot = original.weeks.first().days.first().slots.first()
        val requestTm = PlanLoadReference(PlanLoadReferenceKind.EXERCISE_TM, firstSlot.lift.configurationId)
        val requestOne = requestTm.copy(kind = PlanLoadReferenceKind.EXERCISE_1RM)
        var workIndex = 0
        val changedSlot = firstSlot.copy(sets = firstSlot.sets.map { set ->
            if (set.isWarmup) set else when (workIndex++) {
                0 -> set.copy(percent = 80.0, loadBasis = LoadBasis.PERCENT_TM, reference = requestTm)
                1 -> set.copy(percent = 50.0, loadBasis = LoadBasis.PERCENT_1RM, reference = requestOne)
                else -> set
            }
        })
        val recipe = original.copy(weeks = original.weeks.mapIndexed { wi, week -> if (wi != 0) week else
            week.copy(days = week.days.mapIndexed { di, day -> if (di != 0) day else
                day.copy(slots = listOf(changedSlot) + day.slots.drop(1)) }) })
        val initial = Cal5TestSupport.materialize(recipe)
        val week = Cal5TestSupport.weeksOf(initial).first()
        val exercise = week.sessions.first().allExercises().first { it.catalogConfigurationId == firstSlot.lift.configurationId }
        var captured = ExerciseTrainingMax.capture(initial, exercise, PlanLoadReferenceKind.EXERCISE_TM,
            160.0, 20, "tm", "TM confirmado")
        captured = ExerciseTrainingMax.capture(captured, exercise, PlanLoadReferenceKind.EXERCISE_1RM,
            200.0, 30, "one", "Test de 1RM")
        val rebuilt = PlanMaterializer.rematerializeWeek(captured, week.id, recipe,
            metadata = CatalogCompositionTestSupport.metadata)
        val result = Cal5TestSupport.weeksOf(rebuilt).first().sessions.first().allExercises()
            .first { it.id == exercise.id }
        assertEquals(160.0, result.referenceTrainingMax!!, 0.0001)
        assertEquals(200.0, result.reference1RM!!, 0.0001)
        assertEquals(128.0, result.sets[0].weight!!, 0.0001)
        assertEquals(100.0, result.sets[1].weight!!, 0.0001)
    }

    @Test fun promoteTmComparesAgainstOneRmRatherThanTmTimesOnePointZeroFive() {
        val recipe = Cal5TestSupport.recipeOf("wendler-531-bbb")
        val program = Cal5TestSupport.materialize(recipe)
        val week = Cal5TestSupport.weeksOf(program).first()
        fun promoted(e1rm: Double) = ProgramAutoregulationEngine.evaluate(program, week, emptyList(), recipe,
            WeeklyAutoregulationSignals(e1rmByLift = mapOf(LiftSlot.SQUAT to e1rm), consecutiveHighE1rmWeeks = 2))
            .filter { it.kind == AutoregulationProposalKind.PROMOTE_TM }
        assertTrue(promoted(195.0).isEmpty()) // TM180×1.05=189, but the real 1RM is200.
        assertTrue("No inferred TM promotion", promoted(205.0).isEmpty())
        val noOneRm = program.copy(powerliftingProfile = PowerliftingProfile(squatTM = 180.0))
        val withoutReference = ProgramAutoregulationEngine.evaluate(noOneRm, week, emptyList(), recipe,
            WeeklyAutoregulationSignals(e1rmByLift = mapOf(LiftSlot.SQUAT to 205.0), consecutiveHighE1rmWeeks = 2))
        assertTrue(withoutReference.none { it.kind == AutoregulationProposalKind.PROMOTE_TM })
    }
}
