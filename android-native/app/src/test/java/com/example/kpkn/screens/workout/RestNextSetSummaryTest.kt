package com.example.kpkn.screens.workout

import com.example.kpkn.data.models.CompletedSet
import com.example.kpkn.data.models.Exercise
import com.example.kpkn.data.models.ExerciseSet
import com.example.kpkn.data.models.RepRange
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

/**
 * «Siguiente: Serie 2 · 95 kg × 4–6 · RIR 2» (LV6, F3 y F6): sale de la serie REAL que toca, la que marca el cursor durante el
 * descanso, y no de un ejemplo. También la cabecera del descanso («Serie 1 de 4 hecha») y el orden que rota la forma del reloj.
 */
class RestNextSetSummaryTest {

    private val nbsp = "\u00A0"

    private val squat = Exercise(
        id = "sq",
        name = "Sentadilla trasera",
        sets = listOf(
            ExerciseSet(id = "sq0", targetRepsRange = RepRange(4, 6), weight = 92.5, targetRIR = 2),
            ExerciseSet(id = "sq1", targetRepsRange = RepRange(4, 6), weight = 95.0, targetRIR = 2),
            ExerciseSet(id = "sq2", targetReps = 8, weight = 0.0, targetRPE = 8.5),
            ExerciseSet(id = "sq3", targetDuration = 45),
        ),
    )
    private val press = Exercise(
        id = "pr",
        name = "Press militar",
        sets = listOf(ExerciseSet(id = "pr0", targetReps = 8, weight = 40.0, targetRIR = 1)),
    )

    private fun summary(
        exercises: List<Exercise> = listOf(squat, press),
        exerciseIdx: Int = 0,
        setIdx: Int = 1,
        justLogged: String? = "sq",
        suggested: (Exercise, Int) -> Double? = { _, _ -> null },
    ) = restNextSetSummary(
        exercises = exercises,
        currentExerciseIdx = exerciseIdx,
        currentSetIdx = setIdx,
        justLoggedExerciseId = justLogged,
        nameOf = { it.name },
        suggestedKg = suggested,
    )

    @Test
    fun theNextSetComesFromThePlannedSetTheCursorPointsAt() {
        val next = summary()!!
        assertEquals(2, next.setNumber)
        assertEquals(4, next.setTotal)
        assertNull("misma sesión de ejercicio: no se repite el nombre", next.exerciseName)
        assertEquals("Serie 2 · 95${nbsp}kg${nbsp}×${nbsp}4–6", next.headline())
        assertEquals("RIR 2", next.intensityText)
        assertEquals("Serie 2 de 4: 95${nbsp}kg${nbsp}×${nbsp}4–6.", next.sentence())
        assertEquals("Siguiente: Serie 2 · 95${nbsp}kg${nbsp}×${nbsp}4–6 · RIR 2", next.spoken())
    }

    @Test
    fun theLoadHasADecimalCommaAndTheRepsAreAFixedNumberWhenThePlanHasNoRange() {
        val next = summary(setIdx = 0)!!
        assertEquals("Serie 1 · 92,5${nbsp}kg${nbsp}×${nbsp}4–6", next.headline())
        val fixedReps = summary(exercises = listOf(press), exerciseIdx = 0, setIdx = 0, justLogged = "pr")!!
        assertEquals("Serie 1 · 40${nbsp}kg${nbsp}×${nbsp}8", fixedReps.headline())
        assertEquals("RIR 1", fixedReps.intensityText)
    }

    @Test
    fun withoutAPlannedLoadItUsesTheOneTheAppSuggests() {
        val next = summary(setIdx = 2, suggested = { _, _ -> 87.5 })!!
        assertEquals("Serie 3 · 87,5${nbsp}kg${nbsp}×${nbsp}8", next.headline())
        // Y con RPE: coma decimal.
        assertEquals("RPE 8,5", next.intensityText)
        // Sin sugerencia ni carga solo quedan las repeticiones.
        val bare = summary(setIdx = 2)!!
        assertEquals("Serie 3 · 8 reps", bare.headline())
    }

    @Test
    fun aTimedSetSaysItsDuration() {
        val next = summary(setIdx = 3)!!
        assertEquals("Serie 4 · 45${nbsp}s", next.headline())
        assertNull(next.intensityText)
    }

    @Test
    fun whenTheNextSetIsFromAnotherExerciseItSaysWhichOne() {
        val next = summary(exerciseIdx = 1, setIdx = 0, justLogged = "sq")!!
        assertEquals("Press militar", next.exerciseName)
        assertEquals("Serie 1 · 40${nbsp}kg${nbsp}×${nbsp}8", next.headline())
        assertEquals("Press militar. Serie 1 de 1: 40${nbsp}kg${nbsp}×${nbsp}8.", next.sentence())
        // Sin saber qué serie acaba de registrarse (restauración) no se inventa un nombre.
        assertNull(summary(exerciseIdx = 1, setIdx = 0, justLogged = null)!!.exerciseName)
    }

    @Test
    fun noNextSetWhenNothingIsLeft() {
        assertNull(summary(exerciseIdx = 5))
        assertNull(summary(setIdx = 9))
        assertNull(summary(exercises = emptyList()))
        val emptySlot = squat.copy(sets = listOf(ExerciseSet(id = "x", isEmptySlot = true)))
        assertNull(summary(exercises = listOf(emptySlot), setIdx = 0))
    }

    @Test
    fun theHeadlineSaysWhichSetWasJustDone() {
        val headline = restHeadline(listOf(squat, press), "sq_0", fallbackName = "otro", nameOf = { it.name })
        assertEquals("Sentadilla trasera", headline.title)
        assertEquals("Serie 1 de 4 hecha", headline.subtitle)
        // Un lado de una serie unilateral es la misma serie.
        assertEquals("Serie 3 de 4 hecha", restHeadline(listOf(squat), "sq_2_L", "x", { it.name }).subtitle)
    }

    @Test
    fun theHeadlineFallsBackToTheRestNameWhenTheSetIsUnknown() {
        val unknown = restHeadline(listOf(squat), "zz_0", fallbackName = "Press banca", nameOf = { it.name })
        assertEquals("Press banca", unknown.title)
        assertNull(unknown.subtitle)
        assertNull(restHeadline(listOf(squat), null, "Press banca", { it.name }).subtitle)
    }

    @Test
    fun theRestOrdinalCountsWorkingSetsOnly() {
        val sets = listOf(
            CompletedSet(id = "w", isWarmup = true),
            CompletedSet(id = "a"),
            CompletedSet(id = "b"),
            CompletedSet(id = "s", skipped = true),
        )
        assertEquals(2, restOrdinal(sets))
        assertEquals(0, restOrdinal(emptyList()))
    }
}
