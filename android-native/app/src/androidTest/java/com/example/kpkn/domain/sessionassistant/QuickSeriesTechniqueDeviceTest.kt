package com.example.kpkn.domain.sessionassistant

import androidx.test.ext.junit.runners.AndroidJUnit4
import com.example.kpkn.data.models.CompletedSet
import com.example.kpkn.data.models.Exercise
import com.example.kpkn.data.models.ExerciseSet
import com.example.kpkn.data.models.TechniqueType
import com.example.kpkn.domain.workout.isSetDone
import org.junit.Assert.*
import org.junit.Test
import org.junit.runner.RunWith

/** The B4 helper operates on real plan rows, never on a duplicated row per side. */
@RunWith(AndroidJUnit4::class)
class QuickSeriesTechniqueDeviceTest {
    private fun exercise() = Exercise(id = "exercise", name = "Curl con mancuerna", sets = (0 until 4).map { index ->
        ExerciseSet(id = "uuid-" + index, weight = 20.0 + index * 2.5, targetReps = 8 + index,
            tagId = "tag-" + index, contextKeyV2 = "context-" + index)
    })

    private fun assertPlanDataPreserved(before: Exercise, after: Exercise) {
        assertEquals(4, after.sets.size)
        assertEquals(before.sets.map { it.id }, after.sets.map { it.id })
        assertEquals(4, after.sets.map { it.id }.toSet().size)
        before.sets.zip(after.sets).forEach { (original, result) ->
            assertEquals(original.weight, result.weight)
            assertEquals(original.targetReps, result.targetReps)
            assertEquals(original.targetRepsRange, result.targetRepsRange)
            assertEquals(original.plannedTargetV2, result.plannedTargetV2)
            assertEquals(original.tagId, result.tagId)
            assertEquals(original.contextKeyV2, result.contextKeyV2)
        }
    }

    @Test fun firstSeriesReceivesDropSetWithoutReducingOrRewritingThePlan() {
        val source = exercise()
        val snapshot = source.sets.toList()
        val result = applyQuickSeriesTechnique(source, emptyMap(), 0, SeriesTechnique.DROPSET)
        assertPlanDataPreserved(source, result)
        assertTrue(result.sets.all { it.isDropSet && !it.isRestPause })
        assertTrue(result.sets.all { set -> set.plannedIntensityTechniques.any { it.type == TechniqueType.DROP_SET } })
        assertEquals(snapshot, source.sets)
    }

    @Test fun firstSeriesReceivesRestPauseWithoutReducingOrRewritingThePlan() {
        val source = exercise()
        val result = applyQuickSeriesTechnique(source, emptyMap(), 0, SeriesTechnique.REST_PAUSE)
        assertPlanDataPreserved(source, result)
        assertTrue(result.sets.all { it.isRestPause && !it.isDropSet })
        assertTrue(result.sets.all { set -> set.plannedIntensityTechniques.any { it.type == TechniqueType.REST_PAUSE } })
    }

    @Test fun priorAndCompletedSeriesRemainExactlyUnchanged() {
        val source = exercise()
        val completed = mapOf("exercise_1" to CompletedSet(id = "record-uuid", weight = 22.5, reps = 9))
        val snapshot = completed.toMap()
        val result = applyQuickSeriesTechnique(source, completed, 1, SeriesTechnique.DROPSET)
        assertPlanDataPreserved(source, result)
        assertEquals(source.sets[0], result.sets[0])
        assertEquals(source.sets[1], result.sets[1])
        assertTrue(result.sets.drop(2).all { it.isDropSet })
        assertEquals(snapshot, completed)
    }

    @Test fun normalClearsBothTechniquesWhileKeepingAllPlanData() {
        val original = exercise()
        val source = original.copy(sets = original.sets.mapIndexed { index, set ->
            set.withTechnique(if (index % 2 == 0) SeriesTechnique.DROPSET else SeriesTechnique.REST_PAUSE)
        })
        val result = applyQuickSeriesTechnique(source, emptyMap(), 0, SeriesTechnique.NORMAL)
        assertPlanDataPreserved(source, result)
        assertEquals(original, result)
        assertTrue(result.sets.all { !it.isDropSet && !it.isRestPause && it.dropSets.isEmpty() && it.restPauses.isEmpty() })
        assertTrue(result.sets.all { set -> set.plannedIntensityTechniques.none { it.type == TechniqueType.DROP_SET || it.type == TechniqueType.REST_PAUSE } })
    }

    @Test fun unilateralPartialSeriesRemainsOnePendingRowAndKeepsRecordedSide() {
        val source = exercise().copy(isUnilateral = true)
        val completed = mapOf(
            "exercise_0_L" to CompletedSet(id = "actual-left-uuid", side = "left", weight = 20.0, reps = 8),
            "exercise_1_L" to CompletedSet(id = "done-left-uuid", side = "left", weight = 22.5, reps = 9),
            "exercise_1_R" to CompletedSet(id = "done-right-uuid", side = "right", weight = 22.5, reps = 9),
            "exercise_2_L" to CompletedSet(id = "skipped-left-uuid", skipped = true, side = "left"),
            "exercise_2_R" to CompletedSet(id = "skipped-right-uuid", skipped = true, side = "right"),
        )
        val snapshot = completed.toMap()
        assertFalse(source.isSetDone(completed, 0))
        assertTrue(source.isSetDone(completed, 1))
        assertTrue(source.isSetDone(completed, 2))
        val result = applyQuickSeriesTechnique(source, completed, 0, SeriesTechnique.REST_PAUSE)
        assertPlanDataPreserved(source, result)
        assertTrue(result.sets[0].isRestPause)
        assertTrue(result.sets[3].isRestPause)
        assertEquals(source.sets[1], result.sets[1])
        assertEquals(source.sets[2], result.sets[2])
        assertEquals(snapshot, completed)
        assertFalse(result.isSetDone(completed, 0))
    }

    @Test fun invalidCursorReturnsTheOriginalExercise() {
        val source = exercise()
        assertSame(source, applyQuickSeriesTechnique(source, emptyMap(), -1, SeriesTechnique.DROPSET))
        assertSame(source, applyQuickSeriesTechnique(source, emptyMap(), source.sets.size, SeriesTechnique.REST_PAUSE))
    }
}
