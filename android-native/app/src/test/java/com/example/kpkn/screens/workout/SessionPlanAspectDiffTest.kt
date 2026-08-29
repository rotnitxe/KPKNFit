package com.example.kpkn.screens.workout

import com.example.kpkn.data.models.Exercise
import com.example.kpkn.data.models.ExerciseSet
import com.example.kpkn.data.models.PlannedTechnique
import com.example.kpkn.data.models.Session
import com.example.kpkn.data.models.TechniqueType
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class SessionPlanAspectDiffTest {

    @Test
    fun skipRevert_doesNotRestoreOtherTechniqueChange() {
        val press = Exercise(id = "press", name = "Press", sets = listOf(ExerciseSet(id = "p1")))
        val row = Exercise(
            id = "row",
            name = "Remo",
            sets = listOf(
                ExerciseSet(
                    id = "r1",
                    plannedIntensityTechniques = listOf(
                        PlannedTechnique(type = TechniqueType.DROP_SET, params = mapOf("count" to "2")),
                    ),
                    isDropSet = true,
                ),
            ),
        )
        val baseline = Session(id = "s", name = "Live", exercises = listOf(press, row))
        val current = baseline.copy(
            exercises = listOf(
                press,
                row.copy(sets = listOf(ExerciseSet(id = "r1"))),
            ),
        )
        val aspects = diffSessionPlan(
            baseline = baseline,
            current = current,
            skippedExerciseIds = setOf("press"),
            omittedSetKeys = emptySet(),
        )
        assertTrue(aspects.any { it.kind == SessionPlanAspectKind.SKIPPED_EXERCISE && it.exerciseId == "press" })
        assertTrue(aspects.any { it.kind == SessionPlanAspectKind.TECHNIQUE_CHANGED && it.exerciseId == "row" })

        val skip = aspects.first { it.kind == SessionPlanAspectKind.SKIPPED_EXERCISE }
        val reverted = applySessionPlanAspectRevert(
            aspect = skip,
            baseline = baseline,
            current = current,
            skippedExerciseIds = setOf("press"),
            omittedSetKeys = emptySet(),
        )
        assertEquals(emptySet<String>(), reverted.skippedExerciseIds)
        val rowAfter = reverted.session.allExercises().first { it.id == "row" }
        assertEquals(null, rowAfter.sets.first().plannedIntensityTechniques.firstOrNull())
        assertEquals(false, rowAfter.sets.first().isDropSet)
    }

    @Test
    fun removedExercise_restoresOnlyThatExercise() {
        val a = Exercise(id = "a", name = "A", sets = listOf(ExerciseSet(id = "a1")))
        val b = Exercise(id = "b", name = "B", sets = listOf(ExerciseSet(id = "b1")))
        val c = Exercise(id = "c", name = "C", sets = listOf(ExerciseSet(id = "c1")))
        val baseline = Session(id = "s", name = "Live", exercises = listOf(a, b, c))
        val current = baseline.copy(exercises = listOf(a, c))
        val aspects = diffSessionPlan(baseline, current, emptySet(), emptySet())
        val removed = aspects.first { it.kind == SessionPlanAspectKind.REMOVED_EXERCISE }
        val reverted = applySessionPlanAspectRevert(removed, baseline, current, emptySet(), emptySet())
        assertEquals(listOf("a", "b", "c"), reverted.session.allExercises().map { it.id })
    }
}
