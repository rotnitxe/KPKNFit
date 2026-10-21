package com.example.kpkn.domain.sessionassistant

import androidx.test.ext.junit.runners.AndroidJUnit4
import com.example.kpkn.data.models.Exercise
import com.example.kpkn.data.models.ExerciseMuscleInfo
import com.example.kpkn.data.models.ExerciseSet
import com.example.kpkn.data.models.Session
import org.junit.Assert.*
import org.junit.Test
import org.junit.runner.RunWith

/** Pure engine regression executed by the physical-device instrumentation runner. */
@RunWith(AndroidJUnit4::class)
class UltraFastSeriesPreservationDeviceTest {
    private fun exercise(name: String) = Exercise(
        id = "exercise", name = name,
        sets = (0 until 4).map { index -> ExerciseSet(
            id = "actual-uuid-" + index, targetReps = 8 + index, weight = 20.0 + index * 2.5,
            tagId = "tag-" + index, contextKeyV2 = "context-" + index,
        ) },
    )
    private fun session(exercise: Exercise) = Session(id = "test", name = "Test", exercises = listOf(exercise))

    @Test fun dropsetsKeepEverySeriesIdAndItsOwnLoad() {
        val source = exercise("Curl con mancuernas")
        val result = UltraFastEngine.apply(session(source), emptyMap()).transformedExercises.single()
        assertEquals(source.sets.map { it.id }, result.sets.map { it.id })
        source.sets.zip(result.sets).forEach { (before, after) ->
            assertEquals(before.weight, after.weight)
            assertEquals(before.targetReps, after.targetReps)
            assertEquals(before.tagId, after.tagId)
            assertEquals(before.contextKeyV2, after.contextKeyV2)
            assertTrue(after.isDropSet)
            assertEquals(before.weight!! * .85, after.dropSets[0].weight, .0001)
        }
    }

    @Test fun cableRestPauseKeepsEverySeriesAndPreviewCountAgrees() {
        val source = exercise("Curl polea baja")
        val preview = UltraFastEngine.preview(session(source), emptyMap()).perExercise.single()
        val result = UltraFastEngine.apply(session(source), emptyMap()).transformedExercises.single()
        assertEquals(4, preview.afterSets)
        assertFalse(preview.wasReduced)
        assertTrue(preview.wasDensified)
        assertEquals(source.sets.map { it.id }, result.sets.map { it.id })
        assertTrue(result.sets.all { it.isRestPause && !it.isDropSet && it.restPauses.size == 2 })
    }

    @Test fun staleCustomCountCannotCollapseAnIntensiveTechnique() {
        val source = exercise("Curl polea baja")
        val result = UltraFastEngine.apply(session(source), emptyMap(), customSetCounts = mapOf(source.id to 1))
        assertEquals(4, result.transformedExercises.single().sets.size)
        assertEquals(4, result.preview.perExercise.single().afterSets)
    }

    @Test fun existingIntensiveSeriesStayIntactAfterSomeWereRecorded() {
        val original = exercise("Sentadilla barra alta")
        val source = original.copy(sets = original.sets.map { it.copy(isRestPause = true) })
        val result = UltraFastEngine.apply(session(source), emptyMap(),
            completedSetCountByExercise = mapOf(source.id to 2), customSetCounts = mapOf(source.id to 1))
        assertEquals(source.sets, result.transformedExercises.single().sets)
        assertFalse(result.preview.perExercise.single().wasReduced)
    }

    @Test fun explicitProtectedOverrideAddsTechniqueToAllSeries() {
        val source = exercise("Sentadilla barra alta")
        val result = UltraFastEngine.apply(session(source), emptyMap(), manualOverrides = mapOf(source.id to true))
        assertEquals(4, result.transformedExercises.single().sets.size)
        assertTrue(result.transformedExercises.single().sets.all { it.isDropSet })
    }

    @Test fun normalProtectedReductionRetainsExistingContract() {
        val source = exercise("Sentadilla barra alta")
        val result = UltraFastEngine.apply(session(source), emptyMap())
        assertEquals(2, result.transformedExercises.single().sets.size)
        assertTrue(result.preview.perExercise.single().wasReduced)
        assertTrue(result.transformedExercises.single().sets.none { it.isDropSet || it.isRestPause })
    }
}
