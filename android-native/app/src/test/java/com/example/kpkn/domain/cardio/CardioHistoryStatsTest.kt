package com.example.kpkn.domain.cardio

import com.example.kpkn.data.models.CardioDetails
import com.example.kpkn.data.models.CardioType
import com.example.kpkn.data.models.CompletedExercise
import com.example.kpkn.data.models.CompletedSet
import com.example.kpkn.data.models.WorkoutLog
import org.junit.Assert.assertEquals
import org.junit.Test

class CardioHistoryStatsTest {

    @Test
    fun lastAndPrPaceUseDistanceThreshold() {
        val logs = listOf(
            log("2026-01-02", CardioType.RUN_OUTDOOR, duration = 1500, km = 5.0, rpe = 7.0),
            log("2026-01-01", CardioType.RUN_OUTDOOR, duration = 600, km = 2.0, rpe = 6.0),
            log("2026-01-03", CardioType.TREADMILL, duration = 1200, km = 4.0, rpe = 5.0),
        )
        val history = CardioHistoryStats.forType(logs, CardioType.RUN_OUTDOOR)
        assertEquals(1500, history.lastDurationSeconds)
        assertEquals(5.0, history.lastDistanceKm)
        assertEquals(300, history.lastPaceSecondsPerKm)
        assertEquals(300, history.bestPaceSecondsPerKm)
        assertEquals(5.0, history.longestDistanceKm)
        assertEquals(1500, history.longestTimeSeconds)
    }

    @Test
    fun subKilometreDoesNotCountForPacePr() {
        val logs = listOf(
            log("2026-01-02", CardioType.WALK, duration = 200, km = 0.4, rpe = 4.0),
        )
        val history = CardioHistoryStats.forType(logs, CardioType.WALK)
        assertEquals(null, history.bestPaceSecondsPerKm)
        assertEquals(500, history.lastPaceSecondsPerKm)
    }

    private fun log(
        date: String,
        type: CardioType,
        duration: Int,
        km: Double,
        rpe: Double,
    ): WorkoutLog = WorkoutLog(
        id = date,
        programId = "p",
        sessionId = "s",
        sessionName = "s",
        date = date,
        durationMinutes = duration / 60,
        completedExercises = listOf(
            CompletedExercise(
                exerciseId = "e",
                exerciseName = "Cardio",
                cardioDetails = CardioDetails(type = type),
                sets = listOf(
                    CompletedSet(id = "set", timeSeconds = duration, distanceKm = km, rpe = rpe),
                ),
            ),
        ),
    )
}
