package com.example.kpkn.screens.workout

import com.example.kpkn.data.models.CompletedExercise
import com.example.kpkn.data.models.CompletedSet
import com.example.kpkn.data.models.LoadModeV2
import com.example.kpkn.data.models.RecordedSetPayload
import com.example.kpkn.data.models.UnitModeV2
import com.example.kpkn.data.models.WorkoutLog
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Test
import java.time.LocalDate

class FinishWeeklyComparisonTest {
    private fun set(id: String, weight: Double) = CompletedSet(
        id = id,
        weight = weight,
        reps = 5,
        loadMode = LoadModeV2.LOAD,
        originalLoad = weight,
        contextKey = "ctx",
        setIndex = 0,
        recordedPayloadV3 = RecordedSetPayload(
            actualValue = 5.0,
            exerciseId = "bench",
            loadInputMode = LoadModeV2.LOAD,
            unitMode = UnitModeV2.REPS,
            externalLoad = weight,
            completedReps = 5,
        ),
    )

    private fun exercise(id: String, weight: Double) = CompletedExercise(
        exerciseId = "bench",
        exerciseName = "Press banca",
        exerciseDbId = "bench",
        canonicalExerciseId = "bench",
        sets = listOf(set(id, weight)),
    )

    @Test
    fun previousCalendarWeekProvidesTheComparableMark() {
        val today = LocalDate.of(2026, 11, 9)
        val history = WorkoutLog(
            id = "old",
            programId = "p",
            sessionId = "s",
            sessionName = "Empuje",
            date = "2026-11-04T10:00:00.000Z",
            durationMinutes = 40,
            completedExercises = listOf(exercise("old-set", 95.0)),
        )
        val result = buildFinishLiftProgress(listOf(exercise("today", 100.0)), listOf(history), today).single()
        assertNotNull(result.previousWeek)
        assertEquals(95.0, result.previousWeek?.weight ?: -1.0, 0.0)
        assertEquals(5, result.previousWeek?.reps)
    }

    @Test
    fun noComparablePreviousWeekIsReportedAsMissing() {
        val today = LocalDate.of(2026, 11, 9)
        val old = WorkoutLog(
            id = "older",
            programId = "p",
            sessionId = "s",
            sessionName = "Empuje",
            date = "2026-10-20T10:00:00.000Z",
            durationMinutes = 40,
            completedExercises = listOf(exercise("older-set", 90.0)),
        )
        val result = buildFinishLiftProgress(listOf(exercise("today", 100.0)), listOf(old), today).single()
        assertNull(result.previousWeek)
    }

    @Test
    fun unrecoverableLastreAndMixedRepsSuppressBogusKgDelta() {
        val today = LocalDate.of(2026, 11, 9)
        val todayUnrecoverable = CompletedExercise(
            exerciseId = "dip",
            exerciseName = "Fondos lastrados",
            sets = listOf(
                CompletedSet(
                    id = "today-dip",
                    weight = 78.0,
                    reps = 6,
                    loadMode = LoadModeV2.LASTRE,
                ),
            ),
        )
        val prevLog = WorkoutLog(
            id = "prev",
            programId = "p",
            sessionId = "s",
            sessionName = "Tirón",
            date = "2026-11-04T10:00:00.000Z",
            durationMinutes = 40,
            completedExercises = listOf(
                CompletedExercise(
                    exerciseId = "dip",
                    exerciseName = "Fondos lastrados",
                    sets = listOf(
                        CompletedSet(
                            id = "prev-dip",
                            weight = 75.0,
                            reps = 6,
                            loadMode = LoadModeV2.LASTRE,
                        ),
                    ),
                ),
                CompletedExercise(
                    exerciseId = "bench",
                    exerciseName = "Press banca",
                    exerciseDbId = "bench",
                    canonicalExerciseId = "bench",
                    sets = listOf(
                        CompletedSet(
                            id = "prev-bench",
                            weight = 100.0,
                            reps = 8,
                            loadMode = LoadModeV2.LOAD,
                            originalLoad = 100.0,
                        ),
                    ),
                ),
            ),
        )
        val benchToday = exercise("today-bench", 105.0)
        val progress = buildFinishLiftProgress(listOf(todayUnrecoverable, benchToday), listOf(prevLog), today)
        assertNull(progress[0].deltaKg)
        assertEquals(false, progress[0].previousWeek?.loadKnown)
        // Mixed reps (5 today vs 8 previous): no bogus kg delta even on LOAD.
        assertNull(progress[1].deltaKg)
    }

    @Test
    fun assistedRanksLowerAssistanceFirstAndDoesNotEmitBogusKgGain() {
        val today = LocalDate.of(2026, 11, 9)
        fun assistedSet(id: String, assistance: Double) = CompletedSet(
            id = id,
            weight = 0.0,
            reps = 8,
            loadMode = LoadModeV2.ASSISTED,
            originalLoad = assistance,
            recordedPayloadV3 = RecordedSetPayload(
                actualValue = 8.0,
                exerciseId = "pullup",
                loadInputMode = LoadModeV2.ASSISTED,
                unitMode = UnitModeV2.REPS,
                assistedLoad = assistance,
                completedReps = 8,
            ),
        )
        val prevLog = WorkoutLog(
            id = "prev",
            programId = "p",
            sessionId = "s",
            sessionName = "Tirón",
            date = "2026-11-04T10:00:00.000Z",
            durationMinutes = 40,
            completedExercises = listOf(
                CompletedExercise("pullup", "Dominadas asistidas", sets = listOf(assistedSet("p1", 30.0), assistedSet("p2", 20.0))),
            ),
        )
        val todayEx = CompletedExercise("pullup", "Dominadas asistidas", sets = listOf(assistedSet("t1", 15.0)))
        val lift = buildFinishLiftProgress(listOf(todayEx), listOf(prevLog), today).single()
        assertNull(lift.deltaKg)
        assertEquals(20.0, lift.previousWeek?.weight ?: -1.0, 0.0)
    }
}
