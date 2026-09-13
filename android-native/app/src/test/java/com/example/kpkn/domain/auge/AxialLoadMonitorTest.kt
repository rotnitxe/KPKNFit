package com.example.kpkn.domain.auge

import com.example.kpkn.data.models.CompletedExercise
import com.example.kpkn.data.models.CompletedSet
import com.example.kpkn.data.models.ExerciseMuscleInfo
import com.example.kpkn.data.models.InvolvedMuscle
import com.example.kpkn.data.models.LoadAdvisoryLevel
import com.example.kpkn.data.models.MuscleRole
import com.example.kpkn.data.models.RingStartSnapshot
import com.example.kpkn.data.models.Settings
import com.example.kpkn.data.models.WorkoutLog
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import java.time.Instant

class AxialLoadMonitorTest {
    private val db = mapOf(
        "squat" to ExerciseMuscleInfo(
            id = "squat", name = "Sentadilla", equipment = "barra",
            efc = 4.0, cnc = 4.0, ssc = 1.0, axialLoadFactor = 1.0,
            involvedMuscles = listOf(InvolvedMuscle("Cuádriceps", MuscleRole.PRIMARY)),
        ),
    )
    private val now = Instant.parse("2026-09-12T18:00:00Z").toEpochMilli()
    private val day = 24L * 3_600_000L

    @Test
    fun threeAxialDaysInFour_reachAdjust_andShortHistoryIsNone() {
        val short = listOf(axial(now - day))
        val shortReport = AxialLoadMonitor.evaluate(short, db, now)
        assertTrue(shortReport.insufficientData)
        val none = LoadAdvisoryEngine.levelFor(
            acwr = shortReport.acwr,
            insufficient = true,
            extraWatch = false,
            extraAdjust = false,
            extraUnload = false,
            previous = LoadAdvisoryLevel.NONE,
            allowDrop = true,
        )
        assertEquals(LoadAdvisoryLevel.NONE, none)

        val fourDays = listOf(
            axial(now - 1 * day),
            axial(now - 2 * day),
            axial(now - 3 * day),
            axial(now - 10 * day),
            axial(now - 17 * day),
            axial(now - 24 * day),
        )
        val report = AxialLoadMonitor.evaluate(fourDays, db, now)
        assertTrue("days4=${report.axialDaysIn4} days6=${report.axialDaysIn6}", report.axialDaysIn4 >= 3 || report.axialDaysIn6 >= 4)
        val level = LoadAdvisoryEngine.levelFor(
            acwr = report.acwr ?: 1.0,
            insufficient = report.insufficientData,
            extraWatch = report.axialDaysIn4 >= 3,
            extraAdjust = report.axialDaysIn6 >= 4,
            extraUnload = false,
            previous = LoadAdvisoryLevel.NONE,
            allowDrop = true,
        )
        assertTrue("level=$level", level == LoadAdvisoryLevel.WATCH || level == LoadAdvisoryLevel.ADJUST)
        val adjust = LoadAdvisoryEngine.levelFor(
            acwr = 1.45,
            insufficient = false,
            extraWatch = true,
            extraAdjust = true,
            extraUnload = false,
            previous = LoadAdvisoryLevel.NONE,
            allowDrop = true,
        )
        assertEquals(LoadAdvisoryLevel.ADJUST, adjust)
    }

    private fun axial(at: Long): WorkoutLog {
        val iso = Instant.ofEpochMilli(at).toString()
        return WorkoutLog(
            id = "a-$at", programId = "p", sessionId = "s", sessionName = "SQ",
            date = iso, durationMinutes = 50,
            ringStartSnapshot = RingStartSnapshot(iso, 80, 70, 55),
            completedExercises = listOf(
                CompletedExercise(
                    exerciseId = "squat", exerciseName = "Sentadilla", exerciseDbId = "squat", restTime = 150,
                    sets = List(5) { i -> CompletedSet(id = "s$i", weight = 120.0, reps = 5, rpe = 8.0) },
                ),
            ),
        )
    }
}

class SystemicLoadMonitorTest {
    @Test
    fun insufficientHistoryIsNullAcwr() {
        val now = Instant.parse("2026-09-12T18:00:00Z").toEpochMilli()
        val report = SystemicLoadMonitor.evaluate(emptyList(), emptyMap(), Settings(), nowMs = now)
        assertTrue(report.insufficientData)
        assertNull(report.acwr)
    }
}
