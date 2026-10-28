package com.example.kpkn.domain.training.progress

import com.example.kpkn.data.models.CompletedExercise
import com.example.kpkn.data.models.CompletedSet
import com.example.kpkn.data.models.WorkoutLog
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class WeeklyWorkEngineTest {
    private fun set(weight: Double = 0.0, reps: Int = 0, rir: Int? = null, skipped: Boolean = false, isWarmup: Boolean = false) =
        CompletedSet("s", weight = weight, reps = reps, rir = rir, skipped = skipped, isWarmup = isWarmup)

    private fun log(id: String, weekId: String?, minutes: Int, vararg sets: CompletedSet) = WorkoutLog(
        id = id,
        programId = "p",
        sessionId = "s",
        sessionName = "Sesión $id",
        date = "2026-01-05T10:00:00.000Z",
        durationMinutes = minutes,
        completedExercises = listOf(CompletedExercise("press", "Press banca", sets = sets.toList())),
        weekId = weekId,
    )

    @Test
    fun semanaSinRegistrosSaleConCerosYRespetaElOrdenSinDuplicados() {
        val rows = WeeklyWorkEngine.byWeek(listOf(log("a", "w1", 60, set(60.0, 10, rir = 2))), weekOrder = listOf("w2", "w1", "w2"))
        assertEquals(listOf("w2", "w1"), rows.map { it.weekId })
        val empty = rows.first()
        assertEquals(0, empty.hardSets)
        assertEquals(0, empty.sessions)
        assertEquals(0.0, empty.tonnageKg, 0.0)
        assertEquals(0, empty.reps)
        assertEquals(0, empty.minutes)
        assertNull(empty.rirMean)
    }

    @Test
    fun sumaSoloSeriesDeTrabajoParaTonelajeRepsSeriesYMinutos() {
        val first = log("a", "w1", 60, set(60.0, 10, rir = 2), set(80.0, 8, rir = 1), set(40.0, 12, isWarmup = true), set(100.0, 5, skipped = true), set(0.0, 0))
        val second = log("b", "w1", 45, set(50.0, 6))
        val row = WeeklyWorkEngine.byWeek(listOf(first, second), weekOrder = listOf("w1")).single()
        assertEquals(3, row.hardSets)
        assertEquals(1540.0, row.tonnageKg, 1e-9)
        assertEquals(24, row.reps)
        assertEquals(105, row.minutes)
        assertEquals(2, row.sessions)
    }

    @Test
    fun rirMedioConUnDecimalSoloConSeriesDeTrabajoRegistradas() {
        val rows = WeeklyWorkEngine.byWeek(
            logs = listOf(
                log("a", "w1", 30, set(60.0, 8, rir = 2), set(60.0, 8, rir = 1), set(60.0, 8, rir = 2), set(60.0, 8), set(60.0, 8, rir = 0, skipped = true)),
                log("b", "w2", 30, set(60.0, 8)),
            ),
            weekOrder = listOf("w1", "w2"),
        )
        assertEquals(1.7, rows[0].rirMean!!, 1e-9)
        assertNull(rows[1].rirMean)
    }

    @Test
    fun seriesYRirPlanificadosVienenDeLosMapas() {
        val rows = WeeklyWorkEngine.byWeek(
            logs = listOf(log("a", "w1", 30, set(60.0, 8, rir = 2))),
            weekOrder = listOf("w1", "w2"),
            plannedSetsByWeek = mapOf("w1" to 12),
            plannedRirByWeek = mapOf("w1" to 2.0),
        )
        assertEquals(12, rows[0].plannedSets)
        assertEquals(2.0, rows[0].plannedRirMean!!, 1e-9)
        assertNull(rows[1].plannedSets)
        assertNull(rows[1].plannedRirMean)
    }

    @Test
    fun logSinSemanaOFueraDelOrdenSeIgnora() {
        val row = WeeklyWorkEngine.byWeek(
            logs = listOf(log("a", null, 60, set(100.0, 5)), log("b", "w9", 60, set(100.0, 5)), log("c", "w1", 50, set(100.0, 5))),
            weekOrder = listOf("w1"),
        ).single()
        assertEquals(1, row.sessions)
        assertEquals(1, row.hardSets)
        assertEquals(50, row.minutes)
    }
}
