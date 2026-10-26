package com.example.kpkn.domain.training.insights

import com.example.kpkn.data.models.*
import com.example.kpkn.screens.home.HomeTrainingMetricsBuilder
import org.junit.Assert.*
import org.junit.Test
import java.time.LocalDate
import java.time.ZoneId

class RedesignTrainingMetricsTest {
    private val today = LocalDate.of(2026, 10, 25)
    private val now = today.atTime(12, 0).atZone(ZoneId.systemDefault()).toInstant().toEpochMilli()
    private fun exercise(sets: List<CompletedSet>) = CompletedExercise("e", "Press",
        effectiveMuscles = listOf(InvolvedMuscle("Pectorales", MuscleRole.PRIMARY)), sets = sets)
    private fun log(day: LocalDate, sets: List<CompletedSet>, rpe: Double? = null) =
        WorkoutLog(day.toString(), "p", "s", "Session", day.toString(), 10,
            completedExercises = listOf(exercise(sets)), sessionEffortRpe = rpe)
    private fun cues(logs: List<WorkoutLog>) = SessionCueEngine.cues(logs, setOf("e"),
        mapOf("e" to "Press"), emptySet(), emptyMap(), emptyMap(), emptySet(), now)

    @Test fun unilateralSidesCountOnceAcrossHomeAndMuscleVolume() {
        val left = CompletedSet("l", weight = 10.0, reps = 10, setIndex = 0, side = "left")
        val right = left.copy(id = "r", side = "right")
        val warmup = left.copy(id = "w", setIndex = 1, isWarmup = true)
        val skipped = left.copy(id = "s", setIndex = 2, skipped = true)
        val sets = listOf(left, right, warmup, skipped)
        assertEquals(1, RecordedSetAccounting.count(listOf(exercise(sets))))
        assertEquals(1.0, MuscleVolumeAccounting.sessionSets(listOf(exercise(sets))).getValue("Pectorales"), 0.0)
        val metrics = HomeTrainingMetricsBuilder.build(listOf(log(today, sets)), emptyList(), emptyList(), today, now)
        assertEquals(1.0, metrics.weeks.last().series, 0.0)
        assertEquals(200.0, metrics.weeks.last().tonnage, 0.0)
    }

    @Test fun unknownSessionEffortIsNotInferredFromSets() {
        val exercises = listOf(exercise(listOf(CompletedSet("a", rir = 0, rpe = 10.0))))
        assertNull(RpeConversion.sessionRpe(null, exercises))
        assertEquals(8.0, RpeConversion.sessionRpe(8.0, exercises)!!, 0.0)
        assertEquals(8.0, RpeConversion.fromRir(2), 0.0)
    }

    @Test fun unknownSessionBreaksHighEffortStreak() {
        val logs = (0..3).map { log(today.minusDays(it.toLong()), emptyList(), if (it == 1) null else 9.0) }
        assertFalse(cues(logs).any { it.kind == SessionCueKind.HIGH_EFFORT })
        assertTrue(cues(logs.map { it.copy(sessionEffortRpe = 9.0) }).any { it.kind == SessionCueKind.HIGH_EFFORT })
    }

    @Test fun oneRirIsNotLessThanOneAndFutureRecordsAreExcluded() {
        assertFalse(cues(listOf(log(today, listOf(CompletedSet("a", rir = 1)))))
            .any { it.kind == SessionCueKind.NEAR_FAILURE })
        assertTrue(cues(listOf(log(today, listOf(CompletedSet("a", rir = 0)))))
            .any { it.kind == SessionCueKind.NEAR_FAILURE })
        assertFalse(cues(listOf(log(today.plusDays(1), listOf(CompletedSet("a", rir = 0)))))
            .any { it.kind == SessionCueKind.NEAR_FAILURE })
    }

    @Test fun uncapturedDefaultsAreMissingAndZeroEnergyIsPreserved() {
        val wellbeing = DailyWellbeingLog("a", today.toString(), energy = 0, capturedFields = setOf("energy"))
        val arrival = HomeTrainingMetricsBuilder.build(emptyList(), listOf(wellbeing), emptyList(), today, now).readiness!!
        assertNull(arrival.sleep)
        assertNull(arrival.stress)
        assertEquals(0, arrival.energy)
    }

    @Test fun effortUsesOriginalSecondsWhenAvailable() {
        val recorded = log(today, emptyList(), 8.0).copy(actualDurationSeconds = 90)
        val week = HomeTrainingMetricsBuilder.build(listOf(recorded), emptyList(), emptyList(), today, now).weeks.last()
        assertEquals(12.0, week.effort, 0.0)
    }
    @Test fun weeklyBucketsUseTheLocalRecordedDate() {
        val monday = LocalDate.of(2026, 10, 26)
        val sundayInstant = monday.atStartOfDay(ZoneId.systemDefault()).minusMinutes(1).toInstant()
        val original = log(monday, listOf(CompletedSet("a", reps = 5))).copy(date = sundayInstant.toString())
        val metrics = HomeTrainingMetricsBuilder.build(listOf(original), emptyList(), emptyList(), monday,
            monday.atTime(12, 0).atZone(ZoneId.systemDefault()).toInstant().toEpochMilli())
        assertEquals(0.0, metrics.weeks.last().series, 0.0)
        assertEquals(1.0, metrics.weeks[2].series, 0.0)
    }

}
