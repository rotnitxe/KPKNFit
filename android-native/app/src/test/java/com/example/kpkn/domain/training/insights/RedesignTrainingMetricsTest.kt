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

    @Test fun uncapturedDefaultsAreMissingAndZeroEnergyIsPreserved() {
        val wellbeing = DailyWellbeingLog("a", today.toString(), energy = 0, capturedFields = setOf("energy"))
        val arrival = HomeTrainingMetricsBuilder.build(emptyList(), listOf(wellbeing), emptyList(), today, now).readiness!!
        assertNull(arrival.sleep)
        assertNull(arrival.stress)
        assertEquals(0, arrival.energy)
    }

    @Test fun effortUsesOriginalSecondsWhenAvailable() {
        val set = CompletedSet("effort", reps = 5, actualIntensityMode = IntensityMode.RPE, actualIntensityValue = 8.0)
        val recorded = log(today, listOf(set), 4.0).copy(actualDurationSeconds = 90)
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

    @Test fun homeProgressAlignsWithExerciseProgressAcrossZeroOneManyAndAllModalities() {
        assertTrue(HomeTrainingMetricsBuilder.build(emptyList(), emptyList(), emptyList(), today, now).progress.isEmpty())

        val day1 = today.minusDays(7)
        val day2 = today
        fun sessionLog(day: LocalDate, exercises: List<CompletedExercise>) = WorkoutLog(
            id = "log-${day}",
            programId = "p",
            sessionId = "s",
            sessionName = "Mixta",
            date = day.toString(),
            durationMinutes = 45,
            completedExercises = exercises,
        )

        val oneLog = sessionLog(day2, listOf(CompletedExercise("sq", "Sentadilla", catalogConfigurationId = "sq", sets = listOf(CompletedSet("s1", weight = 100.0, reps = 5, loadMode = LoadModeV2.LOAD, originalLoad = 100.0)))))
        val singleProgress = HomeTrainingMetricsBuilder.build(listOf(oneLog), emptyList(), emptyList(), today, now).progress
        assertEquals(1, singleProgress.size)
        assertNull(singleProgress.single().deltaKg)

        val logs = listOf(
            sessionLog(
                day1,
                listOf(
                    CompletedExercise("sq", "Sentadilla", catalogConfigurationId = "sq", sets = listOf(CompletedSet("1", weight = 100.0, reps = 5, loadMode = LoadModeV2.LOAD, originalLoad = 100.0))),
                    CompletedExercise("pu", "Flexiones", catalogConfigurationId = "pu", sets = listOf(CompletedSet("2", weight = 0.0, reps = 12, loadMode = LoadModeV2.BODYWEIGHT))),
                    CompletedExercise("as", "Dominadas asistidas", catalogConfigurationId = "as", sets = listOf(CompletedSet("3", weight = 0.0, reps = 8, loadMode = LoadModeV2.ASSISTED, originalLoad = 30.0))),
                    CompletedExercise("ls", "Fondos lastre", catalogConfigurationId = "ls", sets = listOf(CompletedSet("4", weight = 10.0, reps = 8, loadMode = LoadModeV2.LASTRE, originalLoad = 10.0))),
                    CompletedExercise("rn", "Correr", catalogConfigurationId = "rn", sets = listOf(CompletedSet("5", reps = 0, distanceKm = 2.0, recordedPayloadV3 = RecordedSetPayload(actualValue = 2000.0, exerciseId = "rn", unitMode = UnitModeV2.DISTANCE)))),
                    CompletedExercise("pl", "Plancha", catalogConfigurationId = "pl", sets = listOf(CompletedSet("6", reps = 0, timeSeconds = 40, recordedPayloadV3 = RecordedSetPayload(actualValue = 40.0, exerciseId = "pl", unitMode = UnitModeV2.TIME)))),
                    CompletedExercise("bp", "Press banca", catalogConfigurationId = "bp", sets = listOf(CompletedSet("7", weight = 80.0, reps = 5, loadMode = LoadModeV2.LOAD, originalLoad = 80.0))),
                ),
            ),
            sessionLog(
                day2,
                listOf(
                    CompletedExercise("sq", "Sentadilla", catalogConfigurationId = "sq", sets = listOf(CompletedSet("1b", weight = 110.0, reps = 5, loadMode = LoadModeV2.LOAD, originalLoad = 110.0))),
                    CompletedExercise("pu", "Flexiones", catalogConfigurationId = "pu", sets = listOf(CompletedSet("2b", weight = 0.0, reps = 16, loadMode = LoadModeV2.BODYWEIGHT))),
                    CompletedExercise("as", "Dominadas asistidas", catalogConfigurationId = "as", sets = listOf(CompletedSet("3b", weight = 0.0, reps = 8, loadMode = LoadModeV2.ASSISTED, originalLoad = 20.0))),
                    CompletedExercise("ls", "Fondos lastre", catalogConfigurationId = "ls", sets = listOf(CompletedSet("4b", weight = 15.0, reps = 8, loadMode = LoadModeV2.LASTRE, originalLoad = 15.0))),
                    CompletedExercise("rn", "Correr", catalogConfigurationId = "rn", sets = listOf(CompletedSet("5b", reps = 0, distanceKm = 3.0, recordedPayloadV3 = RecordedSetPayload(actualValue = 3000.0, exerciseId = "rn", unitMode = UnitModeV2.DISTANCE)))),
                    CompletedExercise("pl", "Plancha", catalogConfigurationId = "pl", sets = listOf(CompletedSet("6b", reps = 0, timeSeconds = 60, recordedPayloadV3 = RecordedSetPayload(actualValue = 60.0, exerciseId = "pl", unitMode = UnitModeV2.TIME)))),
                    CompletedExercise("bp", "Press banca", catalogConfigurationId = "bp", sets = listOf(CompletedSet("7b", weight = 85.0, reps = 5, loadMode = LoadModeV2.LOAD, originalLoad = 85.0))),
                ),
            ),
        )
        val many = HomeTrainingMetricsBuilder.build(logs, emptyList(), emptyList(), today, now).progress
        val expectedChoices = com.example.kpkn.domain.training.ExerciseProgress.choices(logs).map { it.id }
        assertEquals(7, many.size)
        assertEquals(expectedChoices, many.map { it.progressId })

        val assisted = many.first { it.progressId == "configuration:as" }
        assertFalse(assisted.higherIsBetter)
        assertEquals(-10.0, assisted.deltaKg!!, 0.001)

        val pushup = many.first { it.progressId == "configuration:pu" }
        assertEquals("reps", pushup.unitLabel)
        assertEquals(4.0, pushup.deltaKg!!, 0.001)

        val run = many.first { it.progressId == "configuration:rn" }
        assertEquals("m", run.unitLabel)
        assertEquals(1000.0, run.deltaKg!!, 0.001)

        val plank = many.first { it.progressId == "configuration:pl" }
        assertEquals("s", plank.unitLabel)
        assertEquals(20.0, plank.deltaKg!!, 0.001)
    }

}
