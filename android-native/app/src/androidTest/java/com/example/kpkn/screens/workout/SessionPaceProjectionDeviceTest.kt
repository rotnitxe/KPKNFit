package com.example.kpkn.screens.workout

import androidx.test.ext.junit.runners.AndroidJUnit4
import com.example.kpkn.data.models.*
import org.junit.Assert.*
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class SessionPaceProjectionDeviceTest {
    @Test fun projectsOnlyObservedPaceAndReportsDifferenceFromRealGoal() {
        val pace = projectSessionPace(1_600, 8, 20, 60)!!
        assertEquals(4_000L, pace.totalSeconds)
        assertEquals(2_400L, pace.remainingSeconds)
        assertEquals(400L, pace.overTargetSeconds)
        assertNull(projectSessionPace(1_600, 8, 20)?.overTargetSeconds)
    }
    @Test fun insufficientOrInconsistentDataCannotProduceAnEstimate() {
        assertNull(projectSessionPace(600, 1, 20, 60))
        assertNull(projectSessionPace(0, 2, 20, 60))
        assertNull(projectSessionPace(-20, 2, 20, 60))
        assertNull(projectSessionPace(600, 3, 2, 60))
        assertNull(projectSessionPace(Long.MAX_VALUE, 2, 20, 60))
    }
    @Test fun completedPlanHasNoRemainingTimeAndNoInventedTarget() {
        val pace = projectSessionPace(600, 2, 2, 0)!!
        assertEquals(600L, pace.totalSeconds)
        assertEquals(0L, pace.remainingSeconds)
        assertNull(pace.overTargetSeconds)
    }
    @Test fun workingSetCountsExcludeWarmupsOmissionsCardioAndHalfUnilateralSets() {
        val lift = Exercise("lift", "Unilateral", unilateralMode = UnilateralMode.UNILATERAL_PAIRED, sets = List(5) { ExerciseSet("s$it") })
        val cardio = Exercise("cardio", "Bici", sets = listOf(ExerciseSet("c")), cardioDetails = CardioDetails(CardioType.BIKE_STATIONARY))
        fun result(id: String, warmup: Boolean = false) = CompletedSet(id, weight = 20.0, reps = 8, isWarmup = warmup)
        val keys = (0..4).associateWith { WorkoutStepRules.completionKeysForRoadmapSet(lift, it) }
        val completed = buildMap {
            keys.getValue(0).forEach { put(it, result(it)) }
            put(keys.getValue(1).first(), result("half"))
            keys.getValue(2).forEach { put(it, result(it, true)) }
            keys.getValue(3).forEach { put(it, result(it)) }
            keys.getValue(4).forEach { put(it, result(it).copy(skipped = true)) }
            put("cardio_0", result("c"))
        }
        val work = sessionPaceWork(listOf(lift, cardio), completed, setOf(WorkoutStepRules.omittedSetKey(lift.id, 3)))
        assertEquals(SessionPaceWork(1, 4), work)
        assertNull(projectSessionPace(600, work.completed, work.total))
    }
    @Test fun deletedRoundSlotsDoNotInflateTheProjectionEvenWithAStaleCompletion() {
        val lift = Exercise("lift", "Press", sets = listOf(
            ExerciseSet("a"), ExerciseSet("deleted-a", isEmptySlot = true),
            ExerciseSet("b"), ExerciseSet("deleted-b", isEmptySlot = true), ExerciseSet("c"),
        ))
        fun completedAt(index: Int) = WorkoutStepRules.completionKeysForRoadmapSet(lift, index).single() to
            CompletedSet("record-$index", weight = 40.0, reps = 8)
        val realCompletions = mapOf(completedAt(0), completedAt(2))
        val work = sessionPaceWork(listOf(lift), realCompletions, emptySet())
        assertEquals(SessionPaceWork(2, 3), work)
        assertEquals(900L, projectSessionPace(600, work.completed, work.total)!!.totalSeconds)
        val withStaleCompletion = realCompletions + ("${lift.id}_1" to CompletedSet("stale", weight = 40.0, reps = 8))
        assertEquals(work, sessionPaceWork(listOf(lift), withStaleCompletion, emptySet()))
    }

}
