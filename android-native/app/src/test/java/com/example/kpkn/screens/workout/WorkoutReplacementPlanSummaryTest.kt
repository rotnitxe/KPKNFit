package com.example.kpkn.screens.workout

import com.example.kpkn.data.models.Exercise
import com.example.kpkn.data.models.ExerciseSet
import com.example.kpkn.data.models.RepRange
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

/**
 * LV5, bug 1 (H1): la nota del catálogo de reemplazo dice lo que se mantiene del plan, «4 × 4–6 · RIR 2», que es la regla del bug 3 en
 * palabras: cuántas series, su objetivo y la intensidad. Sin el descanso.
 */
class WorkoutReplacementPlanSummaryTest {

    private fun planned(count: Int, range: RepRange = RepRange(4, 6), rir: Int = 2) =
        (0 until count).map { index -> ExerciseSet(id = "s$index", targetRepsRange = range, targetRIR = rir) }

    @Test
    fun laDosisDelPlanSeDiceComoEnElDisenoYSinElDescanso() {
        val exercise = Exercise(id = "slot-1", name = "Sentadilla trasera", sets = planned(4), restTime = 150)

        assertEquals("4 × 4–6 · RIR 2", replacementPlanSummary(exercise))
    }

    @Test
    fun conSeriesDistintasDiceElRangoQueAbarcanTodas() {
        val exercise = Exercise(
            id = "slot-1",
            name = "Press",
            sets = listOf(
                ExerciseSet(id = "a", targetRepsRange = RepRange(6, 8), targetRIR = 2),
                ExerciseSet(id = "b", targetRepsRange = RepRange(10, 12), targetRIR = 2),
            ),
        )

        assertEquals("2 × 6–12 · RIR 2", replacementPlanSummary(exercise))
    }

    @Test
    fun sinSeriesNoHayDosisQueDecir() {
        assertNull(replacementPlanSummary(Exercise(id = "slot-1", name = "Sentadilla trasera", sets = emptyList())))
    }
}
