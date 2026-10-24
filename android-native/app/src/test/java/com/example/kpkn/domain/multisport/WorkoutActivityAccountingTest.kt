package com.example.kpkn.domain.multisport

import com.example.kpkn.data.models.CalorieRange
import com.example.kpkn.data.models.CompletedExercise
import com.example.kpkn.data.models.CompletedSet
import com.example.kpkn.data.models.EnergyEstimateSource
import com.example.kpkn.data.models.ExerciseEnergyContribution
import com.example.kpkn.data.models.SessionEnergySummary
import com.example.kpkn.data.models.WorkoutLog
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class WorkoutActivityAccountingTest {
    @Test fun retiredOrUnspecifiedMethodsNeverContributeAnEnergyTotal() {
        for (method in listOf("auge-energy-v1", "auge-energy-v2", "unknown", "")) {
            val old = WorkoutLog(id="legacy",programId="p",sessionId="s",sessionName="Original",date="2026-10-18",durationMinutes=17,
                energySummary=SessionEnergySummary(methodVersion=method,source=EnergyEstimateSource.FINAL,totalKcal=CalorieRange(100,120,140)))
            val result = WorkoutActivityAccounting.summarize(listOf(old))
            assertNull(result.totalFinalEnergyKcal)
            assertNull(result.activities.single().finalEnergyKcal)
            assertTrue(result.hasPartialEnergyTotal)
        }
    }

    @Test
    fun mixedStrengthAndCardioCountsOnlyTheExistingSessionEnergySummary() {
        val mixedLog = log(
            id = "mixed",
            duration = 52,
            completedExercises = listOf(
                CompletedExercise(
                    exerciseId = "barbell-row",
                    exerciseName = "Row",
                    sets = listOf(CompletedSet(id = "strength-set", calories = 300.0)),
                ),
                CompletedExercise(
                    exerciseId = "run",
                    exerciseName = "Run",
                    sets = listOf(CompletedSet(id = "cardio-set", calories = 500.0)),
                ),
            ),
            energy = SessionEnergySummary(methodVersion = "independent-recorded-fixture-v1",
                activeKcal = CalorieRange(20, 30, 40),
                epocKcal = CalorieRange(1, 2, 3),
                totalKcal = CalorieRange(21, 32, 43),
                source = EnergyEstimateSource.FINAL,
                exerciseContributions = listOf(
                    ExerciseEnergyContribution(
                        exerciseId = "run",
                        activeKcal = 800,
                        epocKcal = 100,
                        totalKcal = 900,
                    ),
                ),
            ),
        )

        val result = WorkoutActivityAccounting.summarize(listOf(mixedLog))

        assertEquals(52L, result.totalDurationMinutes)
        assertEquals(range(21, 32, 43), result.totalFinalEnergyKcal)
        assertFalse(result.hasPartialDurationTotal)
        assertFalse(result.hasPartialEnergyTotal)
    }

    @Test
    fun nonFiniteSetMetadataDoesNotBlockOrContributeToFinalAccounting() {
        val logWithUnrelatedMetadata = log(
            id = "non-finite-metadata",
            duration = 14,
            completedExercises = listOf(
                CompletedExercise(
                    exerciseId = "exercise",
                    exerciseName = "Exercise",
                    sets = listOf(
                        CompletedSet(
                            id = "set",
                            weight = Double.NaN,
                            calories = Double.POSITIVE_INFINITY,
                        ),
                    ),
                ),
            ),
            energy = finalEnergy(10, 20, 30),
        )

        val result = WorkoutActivityAccounting.summarize(listOf(logWithUnrelatedMetadata))

        assertEquals(14L, result.totalDurationMinutes)
        assertEquals(range(10, 20, 30), result.totalFinalEnergyKcal)
        assertFalse(result.hasPartialDurationTotal)
        assertFalse(result.hasPartialEnergyTotal)
    }

    @Test
    fun exactDuplicateWithSameIdCountsOnceAndRecordsItsOccurrenceCount() {
        val sameLog = log("same", duration = 12, energy = finalEnergy(4, 5, 6))

        val result = WorkoutActivityAccounting.summarize(listOf(sameLog, sameLog.copy()))

        assertEquals(1, result.activities.size)
        assertEquals(2, result.activities.single().sourceOccurrences)
        assertEquals(12L, result.totalDurationMinutes)
        assertEquals(range(4, 5, 6), result.totalFinalEnergyKcal)
        assertTrue(result.idConflicts.isEmpty())
    }

    @Test
    fun conflictingContentsForSameIdAreExplicitAndExcludedFromBothTotals() {
        val firstVariant = log("conflict", duration = 10, energy = finalEnergy(10, 12, 14))
        val secondVariant = firstVariant.copy(sessionName = "Changed content")
        val independent = log("independent", duration = 7, energy = finalEnergy(2, 3, 4))

        val result = WorkoutActivityAccounting.summarize(
            listOf(firstVariant, secondVariant, independent),
        )

        assertEquals("conflict", result.idConflicts.single().id)
        assertEquals(2, result.idConflicts.single().distinctLogs.size)
        assertEquals(2, result.activities.count { it.hasIdConflict })
        assertEquals(7L, result.totalDurationMinutes)
        assertEquals(range(2, 3, 4), result.totalFinalEnergyKcal)
        assertTrue(result.hasPartialDurationTotal)
        assertTrue(result.hasPartialEnergyTotal)
        assertTrue(
            result.missingData.filter { it.logId == "conflict" }
                .all { WorkoutActivityMissingField.CONFLICT_RESOLUTION in it.fields },
        )
    }

    @Test
    fun missingEnergyIsNotKnownZeroAndFinalZeroRemainsKnown() {
        val missing = WorkoutActivityAccounting.summarize(
            listOf(log("missing", energy = null)),
        )
        val knownZero = WorkoutActivityAccounting.summarize(
            listOf(log("zero", energy = finalEnergy(0, 0, 0))),
        )
        val emptyHistory = WorkoutActivityAccounting.summarize(emptyList())

        assertNull(missing.totalFinalEnergyKcal)
        assertTrue(missing.hasPartialEnergyTotal)
        assertTrue(
            WorkoutActivityMissingField.FINAL_ENERGY in missing.missingData.single().fields,
        )
        assertEquals(range(0, 0, 0), knownZero.totalFinalEnergyKcal)
        assertFalse(knownZero.hasPartialEnergyTotal)
        assertEquals(0L, emptyHistory.totalDurationMinutes)
        assertNull(emptyHistory.totalFinalEnergyKcal)
        assertFalse(emptyHistory.hasPartialDurationTotal)
        assertFalse(emptyHistory.hasPartialEnergyTotal)
    }

    @Test
    fun plannedAndLiveSummariesStayLabeledAsEstimatesAndDoNotEnterFinalTotal() {
        val plannedLog = log("planned", energy = energy(10, 12, 14, EnergyEstimateSource.PLANNED))
        val liveLog = log("live", energy = energy(20, 22, 24, EnergyEstimateSource.LIVE))
        val finalLog = log("final", energy = finalEnergy(1, 2, 3))

        val result = WorkoutActivityAccounting.summarize(listOf(plannedLog, liveLog, finalLog))
        val byId = result.activities.associateBy { it.log.id }

        assertEquals(EnergyEstimateSource.PLANNED, byId.getValue("planned").estimatedEnergy?.source)
        assertEquals(range(10, 12, 14), byId.getValue("planned").estimatedEnergy?.totalKcal)
        assertEquals(EnergyEstimateSource.LIVE, byId.getValue("live").estimatedEnergy?.source)
        assertEquals(range(1, 2, 3), result.totalFinalEnergyKcal)
        assertTrue(result.hasPartialEnergyTotal)
        assertEquals(
            2,
            result.warnings.count { it.code == WorkoutActivityWarningCode.NON_FINAL_ENERGY },
        )
    }

    @Test
    fun malformedRangesAndNegativeDurationBecomeMissingWithWarnings() {
        val badOrder = log("bad-order", duration = -3, energy = finalEnergy(40, 20, 60))
        val negativeEnergy = log("negative-energy", duration = 2, energy = finalEnergy(-1, 2, 3))
        val valid = log("valid", duration = 4, energy = finalEnergy(1, 2, 3))

        val result = WorkoutActivityAccounting.summarize(listOf(badOrder, negativeEnergy, valid))

        assertEquals(6L, result.totalDurationMinutes)
        assertEquals(range(1, 2, 3), result.totalFinalEnergyKcal)
        assertTrue(result.hasPartialDurationTotal)
        assertTrue(result.hasPartialEnergyTotal)
        assertEquals(
            2,
            result.warnings.count { it.code == WorkoutActivityWarningCode.INVALID_ENERGY_RANGE },
        )
        assertTrue(result.warnings.any { it.code == WorkoutActivityWarningCode.NEGATIVE_DURATION })
        assertTrue(
            result.missingData.first { it.logId == "bad-order" }
                .fields.containsAll(
                    setOf(
                        WorkoutActivityMissingField.DURATION_MINUTES,
                        WorkoutActivityMissingField.FINAL_ENERGY,
                    ),
                ),
        )
    }

    @Test
    fun blankIdsAreNotMergedAndDoNotEnterAggregates() {
        val blankIdLog = log(" \t", duration = 8, energy = finalEnergy(1, 2, 3))

        val result = WorkoutActivityAccounting.summarize(listOf(blankIdLog, blankIdLog.copy()))

        assertEquals(2, result.activities.size)
        assertTrue(result.activities.all { !it.hasValidId && it.sourceOccurrences == 1 })
        assertNull(result.totalDurationMinutes)
        assertNull(result.totalFinalEnergyKcal)
        assertTrue(result.hasPartialDurationTotal)
        assertTrue(result.hasPartialEnergyTotal)
        assertEquals(2, result.warnings.count { it.code == WorkoutActivityWarningCode.BLANK_ID })
    }

    @Test
    fun delimiterLikeStringsDoNotCollideInCanonicalLogOrdering() {
        val first = log("same-id", sessionName = "x, date=y", date = "z")
        val second = log("same-id", sessionName = "x", date = "y, date=z")

        assertTrue(first != second)
        assertEquals(first.toString(), second.toString())

        val forward = WorkoutActivityAccounting.summarize(listOf(first, second))
        val reversed = WorkoutActivityAccounting.summarize(listOf(second, first))

        assertEquals(forward, reversed)
        assertEquals(2, forward.idConflicts.single().distinctLogs.size)
    }

    @Test
    fun mapEntryOrderDoesNotChangeCanonicalConflictOrdering() {
        val alphaZ = log(
            "map-conflict",
            sessionName = "Maps",
            exerciseTags = linkedMapOf("alpha" to "z", "beta" to "a"),
        )
        val alphaA = log(
            "map-conflict",
            sessionName = "Maps",
            exerciseTags = linkedMapOf("alpha" to "a", "beta" to "z"),
        )
        val reordered = listOf(
            alphaZ.copy(exerciseTags = linkedMapOf("beta" to "a", "alpha" to "z")),
            alphaA.copy(exerciseTags = linkedMapOf("beta" to "z", "alpha" to "a")),
        )

        val originalOrder = WorkoutActivityAccounting.summarize(listOf(alphaZ, alphaA))
        val reorderedMaps = WorkoutActivityAccounting.summarize(reordered)

        assertEquals(originalOrder, reorderedMaps)
        assertEquals(
            listOf("a", "z"),
            originalOrder.idConflicts.single().distinctLogs.map { it.exerciseTags.getValue("alpha") },
        )
    }

    @Test
    fun durationWithoutAnyEligibleValueIsMissingWhileRealZeroIsKnown() {
        val unavailable = WorkoutActivityAccounting.summarize(
            listOf(log("negative-duration", duration = -1, energy = null)),
        )
        val knownZero = WorkoutActivityAccounting.summarize(
            listOf(log("zero-duration", duration = 0, energy = null)),
        )

        assertNull(unavailable.totalDurationMinutes)
        assertTrue(unavailable.hasPartialDurationTotal)
        assertEquals(0L, knownZero.totalDurationMinutes)
        assertFalse(knownZero.hasPartialDurationTotal)
    }

    @Test
    fun resultsHaveStableOrderingRegardlessOfInputOrder() {
        val inputs = listOf(
            log("z", duration = 1, energy = finalEnergy(1, 1, 1)),
            log("a", duration = 2, energy = finalEnergy(2, 2, 2)),
            log("a", duration = 3, energy = finalEnergy(3, 3, 3), sessionName = "Variant"),
            log("", duration = 4, energy = null),
            log(" ", duration = 5, energy = null),
        )

        val forward = WorkoutActivityAccounting.summarize(inputs)
        val reversed = WorkoutActivityAccounting.summarize(inputs.reversed())

        assertEquals(forward, reversed)
        assertEquals(listOf("", " ", "a", "a", "z"), forward.activities.map { it.log.id })
    }

    @Test
    fun durationAndEnergyAccumulationUseLongBeyondIntMaximum() {
        val maxInt = Int.MAX_VALUE
        val logs = listOf(
            log("one", duration = maxInt, energy = finalEnergy(maxInt, maxInt, maxInt)),
            log("two", duration = maxInt, energy = finalEnergy(maxInt, maxInt, maxInt)),
        )

        val result = WorkoutActivityAccounting.summarize(logs)
        val expected = 2L * maxInt.toLong()

        assertEquals(expected, result.totalDurationMinutes)
        assertEquals(
            WorkoutActivityEnergyRange(expected, expected, expected),
            result.totalFinalEnergyKcal,
        )
        assertFalse(result.hasPartialDurationTotal)
        assertFalse(result.hasPartialEnergyTotal)
    }

    private fun log(
        id: String,
        duration: Int = 30,
        energy: SessionEnergySummary? = finalEnergy(1, 2, 3),
        completedExercises: List<CompletedExercise> = emptyList(),
        sessionName: String = "Session",
        date: String = "2026-10-18T10:00:00Z",
        exerciseTags: Map<String, String> = emptyMap(),
    ) = WorkoutLog(
        id = id,
        programId = "program",
        sessionId = "session",
        sessionName = sessionName,
        date = date,
        durationMinutes = duration,
        completedExercises = completedExercises,
        energySummary = energy,
        exerciseTags = exerciseTags,
    )

    private fun finalEnergy(low: Int, mid: Int, high: Int) =
        energy(low, mid, high, EnergyEstimateSource.FINAL)

    private fun energy(
        low: Int,
        mid: Int,
        high: Int,
        source: EnergyEstimateSource,
    ) = SessionEnergySummary(methodVersion = "independent-recorded-fixture-v1",
        totalKcal = CalorieRange(low, mid, high),
        source = source,
    )

    private fun range(low: Int, mid: Int, high: Int) =
        WorkoutActivityEnergyRange(low.toLong(), mid.toLong(), high.toLong())
}
