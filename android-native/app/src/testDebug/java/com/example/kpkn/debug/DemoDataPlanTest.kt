package com.example.kpkn.debug

import com.example.kpkn.data.models.ActiveProgramState
import com.example.kpkn.data.models.Exercise
import com.example.kpkn.data.models.ExerciseSet
import com.example.kpkn.data.models.Session
import com.example.kpkn.data.models.WellbeingSource
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import java.time.LocalDate

/** Parte pura de la siembra de depuración (sin Android): cuántos entrenos, qué fechas y ids estables. */
class DemoDataPlanTest {

    private val today = LocalDate.of(2026, 10, 9)
    private val sessions = listOf(
        Session(
            id = "gen-x-s0",
            name = "Empuje",
            exercises = listOf(
                Exercise(
                    id = "ex-banca",
                    name = "Press banca",
                    sets = listOf(
                        ExerciseSet(id = "a1", targetReps = 6, weight = 80.0, targetRPE = 8.0),
                        ExerciseSet(id = "a2", targetReps = 6, weight = 80.0),
                    ),
                ),
            ),
        ),
        Session(
            id = "gen-x-s1",
            name = "Tirón",
            exercises = listOf(
                Exercise(id = "ex-remo", name = "Remo con barra", sets = listOf(ExerciseSet(id = "b1", targetReps = 8, weight = 60.0))),
            ),
        ),
    )
    private val active = ActiveProgramState(
        programId = "gen-x",
        currentWeekId = "gen-x-week",
        programRunId = "run_1",
        currentCycleNumber = 1,
        currentWeekInstanceId = "inst_c1_gen-x-week",
    )

    @Test
    fun `ocho entrenos dentro de los ultimos catorce dias y antes de hoy`() {
        val workouts = DemoDataPlan.workouts(today, "gen-x-week", sessions, active)
        assertEquals(8, workouts.size)
        workouts.forEach { workout ->
            val day = LocalDate.parse(workout.date.take(10))
            assertTrue(day.isBefore(today))
            assertFalse(day.isBefore(today.minusDays(14)))
        }
        assertEquals(8, workouts.map { it.date.take(10) }.toSet().size)
    }

    @Test
    fun `los entrenos rotan las sesiones del programa activo y llevan sus series`() {
        val workouts = DemoDataPlan.workouts(today, "gen-x-week", sessions, active)
        assertEquals(listOf("gen-x-s0", "gen-x-s1"), workouts.take(2).map { it.sessionId })
        assertTrue(workouts.all { it.programId == "gen-x" && it.programRunId == "run_1" })
        assertTrue(workouts.all { log -> log.completedExercises.all { exercise -> exercise.sets.all { it.reps > 0 && it.weight >= 0.0 } } })
    }

    @Test
    fun `los ids son estables y relanzar la siembra no cambia el conjunto`() {
        val first = DemoDataPlan.workouts(today, "gen-x-week", sessions, active).map { it.id }
        val second = DemoDataPlan.workouts(today, "gen-x-week", sessions, active).map { it.id }
        assertEquals(first, second)
        assertEquals(DemoDataPlan.workoutIds(), first.toSet())
        assertTrue(first.all { it.startsWith("demo-") })
        assertTrue(DemoDataPlan.meals(today).all { it.id.startsWith("demo-") })
        assertEquals(DemoDataPlan.CHECK_IN_ID, DemoDataPlan.ringsCheckIn(today).id)
    }

    @Test
    fun `las comidas de hoy son desayuno y comida, sin el texto que escribe el video`() {
        val meals = DemoDataPlan.meals(today)
        assertEquals(2, meals.size)
        assertTrue(meals.all { it.date == "2026-10-09T12:00:00.000Z" })
        val names = meals.flatMap { it.foods }.map { it.foodName.lowercase() }
        assertTrue(names.none { "huevo" in it || "café" in it })
        assertTrue(meals.flatMap { it.foods }.all { it.amount > 0.0 && it.calories >= 0.0 })
    }

    @Test
    fun `el check-in de hoy usa escalas de 1 a 5 y la fuente diaria`() {
        val checkIn = DemoDataPlan.ringsCheckIn(today)
        assertEquals("2026-10-09", checkIn.date)
        assertEquals(WellbeingSource.DAILY_CHECK_IN, checkIn.source)
        listOf(checkIn.sleepQuality, checkIn.stressLevel, checkIn.doms, checkIn.motivation).forEach { assertTrue(it in 1..5) }
    }
}
