package com.example.kpkn.domain.nutrition

import com.example.kpkn.data.models.*
import org.junit.Assert.*
import org.junit.Test
import java.time.LocalDate
import java.time.ZoneId

class CompletedTrainingDayAccountingTest {
    private val day = LocalDate.of(2026, 10, 18)
    private val zone = ZoneId.of("America/Santiago")

    @Test fun `mixed activity and exact repeats contribute one final session total`() {
        val mixed = log("mixed", 300).copy(completedExercises = listOf(
            CompletedExercise("strength", "Strength", sets = listOf(CompletedSet("set-1", calories = 200.0))),
            CompletedExercise("cardio", "Cardio", sets = listOf(CompletedSet("set-2", calories = 100.0))),
        ))
        val result = CompletedTrainingDayAccountant.summarize(listOf(mixed, mixed), day, zone)
        assertEquals(300, result.balanceMidpointKcal)
        assertEquals(1, result.activities.size)
        assertFalse(result.hasPartialEnergy)
    }

    @Test fun `conflict across days stays excluded on both days`() {
        val first = log("same", 100)
        val second = first.copy(actualDate = day.plusDays(1).toString(), durationMinutes = 90)
        for (date in listOf(day, day.plusDays(1))) {
            val result = CompletedTrainingDayAccountant.summarize(listOf(first, second), date, zone)
            assertNull(result.knownFinalEnergyKcal)
            assertTrue(result.hasPartialEnergy)
            assertTrue(result.activities.single().hasIdConflict)
        }
    }

    @Test fun `planned and live estimates do not enter actual burn and missing is not known zero`() {
        val planned = log("plan", 400).copy(energySummary = SessionEnergySummary(methodVersion = "independent-recorded-fixture-v1", totalKcal = CalorieRange(400, 400, 400)))
        val live = log("live", 200).copy(energySummary = SessionEnergySummary(methodVersion = "independent-recorded-fixture-v1",
            totalKcal = CalorieRange(200, 200, 200), source = EnergyEstimateSource.LIVE,
        ))
        val missing = log("missing", 0).copy(energySummary = null)
        val result = CompletedTrainingDayAccountant.summarize(listOf(planned, live, missing), day, zone)
        assertNull(result.balanceMidpointKcal)
        assertTrue(result.hasPartialEnergy)
        val zero = CompletedTrainingDayAccountant.summarize(listOf(log("zero", 0)), day, zone)
        assertEquals(0, zero.balanceMidpointKcal)
        assertFalse(zero.hasPartialEnergy)
    }

    @Test fun `actual date wins and instant fallback uses injected local zone without crashing`() {
        val explicit = log("explicit", 40).copy(date = "bad", actualDate = day.toString())
        val instant = log("instant", 60).copy(actualDate = null, date = "2026-10-19T01:00:00Z")
        val undated = log("undated", 90).copy(actualDate = null, date = "bad")
        val result = CompletedTrainingDayAccountant.summarize(listOf(explicit, instant, undated), day, zone)
        assertEquals(100, result.balanceMidpointKcal)
        assertEquals(1, result.undatedActivityCount)
    }

    @Test fun `aggregate outside Int range remains Long and cannot wrap into nutrition balance`() {
        val result = CompletedTrainingDayAccountant.summarize(
            listOf(log("a", Int.MAX_VALUE), log("b", 1)), day, zone,
        )
        assertEquals(Int.MAX_VALUE.toLong() + 1, result.knownFinalEnergyKcal!!.mid)
        assertNull(result.balanceMidpointKcal)
        assertTrue(result.isOutsideBalanceRange)
    }

    private fun log(id: String, kcal: Int) = WorkoutLog(
        id = id, programId = "p", sessionId = id, sessionName = id,
        date = day.toString(), actualDate = day.toString(), durationMinutes = 30,
        energySummary = SessionEnergySummary(methodVersion = "independent-recorded-fixture-v1", totalKcal = CalorieRange(kcal, kcal, kcal), source = EnergyEstimateSource.FINAL),
    )
}
