package com.example.kpkn.screens.workout

import com.example.kpkn.data.models.*
import com.example.kpkn.data.protocols.LoadBasis
import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.Json
import org.junit.Assert.*
import org.junit.Test

class LiveSetModeTest {
    private val set = ExerciseSet("s", targetPercentageRM = 80.0, targetReps = 7)
    private val exercise = Exercise("e", "Press banca", trainingMode = TrainingMode.RM, reference1RM = 125.0, sets = listOf(set))

    @Test fun eightyPercentOf125Is100WithSevenEstimatedReps() {
        val values = liveRmValues(exercise, set, 80.0, liveRmReference(exercise, null))
        assertEquals(100.0, values.weight!!, 0.0)
        assertEquals(7, values.estimatedReps)
    }
    @Test fun prReferenceUsesTheSharedHybridWithoutAnAmrapBonus() {
        val pr = exercise.copy(reference1RM = null, prFor1RM = PrReference(100.0, 5))
        assertEquals(112.5, liveRmReference(pr, null)!!, 0.0)
        assertEquals(90.0, liveRmValues(pr, set, 80.0, liveRmReference(pr, null)).weight!!, 0.0)
    }
    @Test fun trainingMaxRemainsASeparateLoadReference() {
        val plan = exercise.copy(reference1RM = 150.0, referenceTrainingMax = 120.0)
        val values = liveRmValues(plan, set.copy(loadBasis = LoadBasis.PERCENT_TM), 80.0, liveRmReference(plan, null))
        assertEquals(96.0, values.weight!!, 0.0)
        assertEquals(150.0, values.oneRm!!, 0.0)
        assertEquals(120.0, values.trainingMax!!, 0.0)
        assertTrue(values.usesTrainingMax)
    }
    @Test fun aTrainingMaxAloneDoesNotInventAOneRmOrEstimatedReps() {
        val plan = exercise.copy(reference1RM = null, referenceTrainingMax = 125.0)
        val values = liveRmValues(plan, set.copy(loadBasis = LoadBasis.PERCENT_TM), 80.0, liveRmReference(plan, null))
        assertEquals(100.0, values.weight!!, 0.0)
        assertNull(values.oneRm)
        assertNull(values.estimatedReps)
    }
    @Test fun soloRpeNeverTurnsIntoAFixedLoadModeBecauseOfAnOldPercentage() {
        assertEquals(LiveSetCardMode.SOLO_RPE, liveSetCardMode(exercise.copy(trainingMode = TrainingMode.SOLO_RPE), set, UnitModeV2.REPS))
    }
    @Test fun anActualLightWeightKeepsItsRealPercentageInsteadOfBeingClampedToThePrescription() {
        val values = liveRmValues(exercise, set, 20.0, 125.0, actualWeight = 25.0)
        assertEquals(20.0, values.percent, 0.0); assertEquals(25.0, values.weight!!, 0.0)
        assertEquals(30, values.estimatedReps)
    }
    @Test fun customAndDistanceUnitsAreInferredWithoutAnExplicitSetUnit() {
        assertEquals(UnitModeV2.CUSTOM, liveSetUnitMode(exercise.copy(trainingMode = TrainingMode.CUSTOM), set))
        assertEquals(UnitModeV2.DISTANCE, liveSetUnitMode(exercise.copy(trainingMode = TrainingMode.DISTANCE), set))
        assertEquals(UnitModeV2.TIME, liveSetUnitMode(exercise.copy(trainingMode = TrainingMode.TIME), set))
        assertEquals(UnitModeV2.REPS, liveSetUnitMode(exercise.copy(trainingMode = TrainingMode.TIME), set.copy(unitModeV2 = UnitModeV2.REPS)))
    }
    @Test fun minuteSecondEntryPreservesItsMeaningAndLegacySeconds() {
        assertEquals(65, parseLiveDurationSeconds("1:05"))
        assertEquals(65, parseLiveDurationSeconds("1:5"))
        assertEquals(65, parseLiveDurationSeconds("65"))
        assertEquals("1:05", formatLiveDuration(65))
        assertEquals(0, parseLiveDurationSeconds("0:00"))
    }
    @Test fun invalidDurationNeverBecomesAPlannedOrStrippedNumber() {
        listOf("1:60", "1:99", "-1:05", "1:-5", "1.5", "1:05:00", ":05", "1:", "", "999999999999999:00").forEach {
            assertNull(it, parseLiveDurationSeconds(it))
        }
    }
    @Test fun decimalQuantitiesRemainFiniteAndKeepTheirPrecision() {
        assertEquals(12.5, liveQuantityNumber("12,5", UnitModeV2.DISTANCE)!!, 0.0)
        assertEquals(12.5, liveQuantityNumber("12.5", UnitModeV2.CUSTOM)!!, 0.0)
        listOf(93.75, 12.25, .125).forEach { value ->
            assertEquals(value, liveQuantityNumber(value.toLiveNumberString(), UnitModeV2.CUSTOM)!!, 0.0)
        }
        assertEquals("93,75", 93.75.toLiveNumberString())
        listOf("NaN", "Infinity", "-Infinity").forEach { assertNull(liveQuantityNumber(it, UnitModeV2.CUSTOM)) }
    }
    @Test fun reopeningLegacyDistanceUsesMetersWithoutIntegerTruncation() {
        assertEquals(12.5, completedLiveQuantity(CompletedSet("c", weight = 24.0, reps = 12, distanceKm = .0125), UnitModeV2.DISTANCE)!!, 0.0)
    }
    @Test fun pausingAndResumingKeepsSubsecondTime() {
        val first = LiveSetClockState().start(1_000).stop(1_450, 60)
        val resumed = first.start(5_000)
        assertEquals(1, resumed.seconds(5_550, 60))
        assertEquals(0, first.seconds(10_000, 60))
    }
    @Test fun clockCatchesUpAfterAnInactivePageAndStopsAtTheGoal() {
        val clock = LiveSetClockState().start(10_000)
        assertEquals(60, clock.seconds(75_000, 60))
        assertTrue(clock.complete(75_000, 60))
        val stopped = clock.stop(75_000, 60)
        assertFalse(stopped.running)
        assertEquals(60, stopped.seconds(100_000, 60))
    }
    @Test fun freeTimeHasNoInventedGoal() {
        val clock = LiveSetClockState().start(1_000)
        assertEquals(125, clock.seconds(126_000, null))
        assertFalse(clock.complete(126_000, null))
    }
    @Test fun rmDraftFieldsRemainCompatibleWithOldSerializedDrafts() {
        val old = Json.decodeFromString<WorkoutSetDraft>("{\"weightText\":\"100\",\"valueText\":\"7\"}")
        assertNull(old.rmReference1RM)
        assertNull(old.rmPercent)
        val current = old.copy(rmReference1RM = 125.0, rmPercent = 80.0, updatedAtMs = 1234L)
        assertEquals(current, Json.decodeFromString<WorkoutSetDraft>(Json.encodeToString(current)))
    }
}
