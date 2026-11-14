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
        val chart = finishWeeklyMetric(result)
        assertEquals(FinishWeeklyMetricKind.ESTIMATED_ONE_RM, chart?.kind)
        assertEquals(result.previousWeek?.estimated1Rm ?: -1.0, chart?.previousValue ?: -2.0, 0.0)
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
        assertNull(progress[0].previousWeek)
        // Unrecoverable lastre is excluded from the comparable weekly marks altogether.
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
        val chart = finishWeeklyMetric(lift)
        assertEquals(FinishWeeklyMetricKind.ASSISTANCE, chart?.kind)
        assertEquals(15.0, chart?.currentValue ?: -1.0, 0.0)
        assertEquals(20.0, chart?.previousValue ?: -1.0, 0.0)
        assertEquals(true, chart?.lowerIsBetter)
    }

    @Test
    fun contextAndSideMustMatchExactlyIncludingMissingLegacyValues() {
        val today = LocalDate.of(2026, 11, 9)
        val current = exercise("today", 100.0).copy(sets = listOf(set("today", 100.0).copy(side = "left")))
        val mismatched = exercise("old", 110.0).copy(sets = listOf(set("old", 110.0).copy(side = null)))
        val log = WorkoutLog("old", "p", "s", "Empuje", "2026-11-04T10:00:00.000Z", 40, completedExercises = listOf(mismatched))
        val result = buildFinishLiftProgress(listOf(current), listOf(log), today).single()
        assertNull(result.previousWeek)
    }

    @Test
    fun lastreAtDifferentRepetitionsIsNotAComparableWeeklyMark() {
        val today = LocalDate.of(2026, 11, 9)
        fun lastre(id: String, kg: Double, reps: Int) = CompletedSet(id, weight = 0.0, reps = reps,
            loadMode = LoadModeV2.LASTRE, originalLoad = kg,
            recordedPayloadV3 = RecordedSetPayload(actualValue = reps.toDouble(), exerciseId = "dip",
                loadInputMode = LoadModeV2.LASTRE, unitMode = UnitModeV2.REPS, externalLoad = kg, completedReps = reps))
        val current = CompletedExercise("dip", "Fondos", sets = listOf(lastre("t", 40.0, 5)))
        val previous = WorkoutLog("old", "p", "s", "Tirón", "2026-11-04T10:00:00.000Z", 40,
            completedExercises = listOf(CompletedExercise("dip", "Fondos", sets = listOf(lastre("p", 60.0, 8)))))
        assertNull(buildFinishLiftProgress(listOf(current), listOf(previous), today).single().previousWeek)
    }

    @Test
    fun distanceDoesNotCompareAgainstDurationOnlyCardio() {
        val today = LocalDate.of(2026, 11, 9)
        val current = CompletedExercise("run", "Cinta", cardioDetails = com.example.kpkn.data.models.CardioDetails(type = com.example.kpkn.data.models.CardioType.TREADMILL),
            sets = listOf(CompletedSet("t", distanceKm = 3.0)))
        val prior = CompletedExercise("run", "Cinta", cardioDetails = com.example.kpkn.data.models.CardioDetails(type = com.example.kpkn.data.models.CardioType.TREADMILL),
            sets = listOf(CompletedSet("p", timeSeconds = 1200)))
        val log = WorkoutLog("old", "p", "s", "Cardio", "2026-11-04T10:00:00.000Z", 40, completedExercises = listOf(prior))
        assertNull(buildFinishLiftProgress(listOf(current), listOf(log), today).single().previousWeek)
    }

    @Test
    fun distanceChartUsesKilometersForBothWeeks() {
        val today = LocalDate.of(2026, 11, 9)
        fun cardio(id: String, distanceKm: Double) = CompletedExercise(
            exerciseId = "run",
            exerciseName = "Cinta",
            cardioDetails = com.example.kpkn.data.models.CardioDetails(type = com.example.kpkn.data.models.CardioType.TREADMILL),
            sets = listOf(CompletedSet(id, distanceKm = distanceKm)),
        )
        val previous = WorkoutLog(
            "old", "p", "s", "Cardio", "2026-11-04T10:00:00.000Z", 40,
            completedExercises = listOf(cardio("prior", 4.2)),
        )
        val lift = buildFinishLiftProgress(listOf(cardio("today", 5.0)), listOf(previous), today).single()
        val chart = finishWeeklyMetric(lift)
        assertEquals(FinishWeeklyMetricKind.DISTANCE_KM, chart?.kind)
        assertEquals(5.0, chart?.currentValue ?: -1.0, 0.0)
        assertEquals(4.2, chart?.previousValue ?: -1.0, 1e-9)
    }

    @Test
    fun timeMetricDrivesBothCardioComparisonAndLabelsWithExactSeconds() {
        val today = LocalDate.of(2026, 11, 9)
        fun timedSet(id: String, distance: Double, seconds: Int) = CompletedSet(
            id = id,
            reps = 0,
            distanceKm = distance,
            timeSeconds = seconds,
            recordedPayloadV3 = RecordedSetPayload(
                exerciseId = "run",
                unitMode = UnitModeV2.TIME,
                durationSeconds = seconds,
                actualValue = seconds.toDouble(),
            ),
        )
        fun cardio(vararg sets: CompletedSet) = CompletedExercise(
            exerciseId = "run",
            exerciseName = "Cinta",
            cardioDetails = com.example.kpkn.data.models.CardioDetails(
                type = com.example.kpkn.data.models.CardioType.TREADMILL,
            ),
            sets = sets.toList(),
        )
        val current = cardio(timedSet("short", 4.0, 90), timedSet("today", 2.0, 125))
        val previous = WorkoutLog(
            "old", "p", "s", "Cardio", "2026-11-04T10:00:00.000Z", 40,
            completedExercises = listOf(cardio(timedSet("prior", 8.0, 95))),
        )

        val result = buildFinishLiftProgress(listOf(current), listOf(previous), today).single()

        assertEquals("2 min 5 s", finishSetLabel(result.best))
        assertEquals("1 min 35 s", result.previousWeek?.displayLabel)
        val chart = finishWeeklyMetric(result)
        assertEquals(FinishWeeklyMetricKind.TIME_SECONDS, chart?.kind)
        assertEquals(125.0, chart?.currentValue ?: -1.0, 0.0)
        assertEquals(95.0, chart?.previousValue ?: -1.0, 0.0)
    }

    @Test
    fun previousWeekUsesItsBestComparableMarkInsteadOfTheMostRecent() {
        val today = LocalDate.of(2026, 11, 9)
        fun log(id: String, day: String, load: Double) = WorkoutLog(id, "p", "s", "Empuje", day, 40,
            completedExercises = listOf(exercise(id, load)))
        val history = listOf(log("best", "2026-11-02T10:00:00.000Z", 110.0), log("recent", "2026-11-08T10:00:00.000Z", 100.0))
        val progress = buildFinishLiftProgress(listOf(exercise("today", 120.0)), history, today).single()
        assertEquals(110.0, progress.previousWeek?.weight ?: -1.0, 0.0)
    }

    @Test
    fun resumedSessionKeepsItsLocalStartDateAcrossMidnight() {
        val zone = java.time.ZoneId.of("America/Santiago")
        val started = java.time.ZonedDateTime.of(2026, 11, 11, 23, 40, 0, 0, zone).toInstant().toEpochMilli()
        assertEquals(LocalDate.of(2026, 11, 11), finishSessionLocalDate(started, LocalDate.of(2026, 11, 12), zone))
        assertEquals(LocalDate.of(2026, 11, 12), finishSessionLocalDate(0L, LocalDate.of(2026, 11, 12), zone))
    }
}
