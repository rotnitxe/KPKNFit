package com.example.kpkn.screens.workout

import com.example.kpkn.data.models.CardioDetails
import com.example.kpkn.data.models.CardioType
import com.example.kpkn.data.models.CompletedSet
import com.example.kpkn.data.models.Exercise
import com.example.kpkn.data.models.ExerciseSet
import com.example.kpkn.screens.sessioneditor.sessionExerciseListDose
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * Lo que dice la cabecera de las opciones de un ejercicio (LV11, A2): su dosis con lo hecho («1/4 hecha»), el resumen del cardio con la
 * unidad pegada a su cifra y las rondas de una superserie.
 */
class ExerciseOptionsModelTest {

    private fun strength(id: String = "sq", sets: Int = 4) = Exercise(
        id = id,
        name = "Sentadilla",
        sets = (0 until sets).map { ExerciseSet(id = "$id-s$it", targetReps = 6, weight = 100.0) },
    )

    private fun done(id: String, vararg indices: Int): Map<String, CompletedSet> =
        indices.associate { "${id}_$it" to CompletedSet(id = "c-$id-$it", weight = 100.0, reps = 6) }

    @Test
    fun theSubtitleIsThePlannedDoseWithHowManySetsAreDone() {
        val exercise = strength()
        val dose = sessionExerciseListDose(exercise, includeRest = false)

        assertEquals("$dose · 0/4 hechas", exerciseOptionsSubtitle(exercise, emptyMap(), emptySet()))
        assertEquals("$dose · 1/4 hecha", exerciseOptionsSubtitle(exercise, done("sq", 0), emptySet()))
        assertEquals("$dose · 3/4 hechas", exerciseOptionsSubtitle(exercise, done("sq", 0, 1, 2), emptySet()))
    }

    @Test
    fun anOmittedSetDoesNotCountAsOneToDo() {
        val exercise = strength(sets = 3)
        val dose = sessionExerciseListDose(exercise, includeRest = false)

        assertEquals("$dose · 1/2 hecha", exerciseOptionsSubtitle(exercise, done("sq", 0), setOf("sq_2")))
    }

    @Test
    fun anExerciseWithNoSetsToDoShowsOnlyItsDose() {
        val exercise = strength(sets = 1)
        val dose = sessionExerciseListDose(exercise, includeRest = false)

        assertEquals(dose, exerciseOptionsSubtitle(exercise, emptyMap(), setOf("sq_0")))
    }

    @Test
    fun aCardioSubtitleKeepsTheUnitGluedToTheNumber() {
        val run = Exercise(
            id = "run",
            name = "Cinta",
            cardioDetails = CardioDetails(type = CardioType.TREADMILL, targetDurationSeconds = 1200),
            sets = listOf(ExerciseSet(id = "run-s0")),
        )

        val subtitle = exerciseOptionsSubtitle(run, emptyMap(), emptySet())

        assertEquals("Espacio cardio · Estático · 20 min", subtitle)
        assertFalse("la unidad no se separa de la cifra", subtitle.contains("20 min"))
    }

    @Test
    fun aCardioWithOnlySecondsKeepsTheUnitGluedToo() {
        val short = Exercise(
            id = "run",
            name = "Cinta",
            cardioDetails = CardioDetails(type = CardioType.TREADMILL, targetDurationSeconds = 45),
            sets = listOf(ExerciseSet(id = "run-s0")),
        )

        assertTrue(cardioContextSummary(short).endsWith("45 s"))
    }

    @Test
    fun aSupersetSaysItsRoundsAndItsExercisesWithTheirSingular() {
        assertEquals("3 rondas · 2 ejercicios", supersetOptionsSubtitle(rounds = 3, members = 2))
        assertEquals("1 ronda · 3 ejercicios", supersetOptionsSubtitle(rounds = 1, members = 3))
        assertEquals("2 rondas · 1 ejercicio", supersetOptionsSubtitle(rounds = 2, members = 1))
    }
}
