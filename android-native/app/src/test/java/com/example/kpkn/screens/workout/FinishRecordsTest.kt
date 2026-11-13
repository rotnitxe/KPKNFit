package com.example.kpkn.screens.workout

import com.example.kpkn.data.models.CompletedExercise
import com.example.kpkn.data.models.CompletedSet
import com.example.kpkn.data.models.LoadModeV2
import com.example.kpkn.data.models.RecordedSetPayload
import com.example.kpkn.data.models.UnitModeV2
import com.example.kpkn.data.models.WorkoutLog
import com.example.kpkn.domain.performance.StrengthModel
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class FinishRecordsTest {
    private fun exercise(
        id: String,
        set: CompletedSet,
        config: String? = null,
        name: String = "Ejercicio",
    ) = CompletedExercise(
        exerciseId = id,
        exerciseName = name,
        exerciseDbId = "definition-$id",
        canonicalExerciseId = "definition-$id",
        catalogDefinitionId = "definition-$id",
        catalogConfigurationId = config,
        sets = listOf(set),
    )

    private fun log(id: String, set: CompletedSet, config: String? = null) = WorkoutLog(
        id = id,
        programId = "p",
        sessionId = "s",
        sessionName = "Sesión",
        date = "2026-11-04T10:00:00.000Z",
        durationMinutes = 40,
        completedExercises = listOf(exercise("movement", set, config)),
    )

    private fun external(id: String, kg: Double, reps: Int) = CompletedSet(
        id = id, weight = kg, reps = reps, loadMode = LoadModeV2.LOAD, originalLoad = kg,
    )

    private fun weighted(id: String, mode: LoadModeV2, kg: Double, reps: Int) = CompletedSet(
        id = id,
        weight = 0.0,
        reps = reps,
        loadMode = mode,
        originalLoad = kg,
        recordedPayloadV3 = RecordedSetPayload(
            actualValue = reps.toDouble(),
            exerciseId = "movement",
            loadInputMode = mode,
            unitMode = UnitModeV2.REPS,
            externalLoad = kg.takeIf { mode == LoadModeV2.LASTRE },
            assistedLoad = kg.takeIf { mode == LoadModeV2.ASSISTED },
            completedReps = reps,
        ),
    )

    @Test
    fun bodyweightRecordsUseRepsAndNeverCreateAnE1rmFromBodyMass() {
        val bodyweight = CompletedSet(
            id = "t",
            weight = 106.0,
            reps = 5,
            loadMode = LoadModeV2.BODYWEIGHT,
            recordedPayloadV3 = RecordedSetPayload(
                actualValue = 5.0,
                exerciseId = "movement",
                loadInputMode = LoadModeV2.BODYWEIGHT,
                unitMode = UnitModeV2.REPS,
                bodyWeightSnapshot = 106.0,
                completedReps = 5,
            ),
        )
        val records = buildFinishRecords(listOf(exercise("movement", bodyweight)), emptyList())
        assertEquals(1, records.size)
        assertEquals("Repeticiones", records.single().metric)
        assertTrue(records.single().isInitialReference)
        assertTrue(records.single().currentLabel.contains("5 reps · peso corporal"))
        assertFalse(records.single().summaryLine.contains("e1RM"))
        assertFalse(records.single().summaryLine.contains("106 kg"))
    }

    @Test
    fun lastreShowsCurrentPriorAndPositiveDifferenceAtEqualRepetitions() {
        val records = buildFinishRecords(
            listOf(exercise("movement", weighted("today", LoadModeV2.LASTRE, 40.0, 5))),
            listOf(log("prior", weighted("prior-set", LoadModeV2.LASTRE, 35.0, 5))),
        )
        val record = records.single()
        assertEquals("Lastre", record.metric)
        assertEquals(40.0, record.value, 0.0)
        assertEquals(35.0, record.previousValue ?: -1.0, 0.0)
        assertEquals(5.0, record.improvement ?: -1.0, 0.0)
        assertTrue(record.currentLabel.contains("40 kg lastre × 5 reps"))
        assertTrue(record.previousLabel.orEmpty().contains("35 kg lastre × 5 reps"))
    }

    @Test
    fun assistanceImprovementMeansLessAssistanceAtTheSameRepetitions() {
        val record = buildFinishRecords(
            listOf(exercise("movement", weighted("today", LoadModeV2.ASSISTED, 20.0, 5))),
            listOf(log("prior", weighted("prior-set", LoadModeV2.ASSISTED, 25.0, 5))),
        ).single()
        assertEquals("Asistencia", record.metric)
        assertEquals(5.0, record.improvement ?: -1.0, 0.0)
        assertTrue(record.summaryLine.contains("+5 kg"))
        assertFalse(record.summaryLine.contains("e1RM"))
    }

    @Test
    fun bestPriorRecordIsUsedInsteadOfTheMostRecentSession() {
        val history = listOf(
            log("best", external("best-set", 110.0, 5)),
            log("recent", external("recent-set", 105.0, 5)),
        )
        val record = buildFinishRecords(listOf(exercise("movement", external("today", 112.0, 5))), history).single()
        val bestHistoricalE1rm = StrengthModel.estimate1RM(external("expected-best", 110.0, 5))
        val recentHistoricalE1rm = StrengthModel.estimate1RM(external("expected-recent", 105.0, 5))
        val currentE1rm = StrengthModel.estimate1RM(external("expected-current", 112.0, 5))
        assertEquals(bestHistoricalE1rm, record.previousValue ?: -1.0, 0.0)
        assertTrue((record.previousValue ?: 0.0) > recentHistoricalE1rm)
        assertEquals(currentE1rm - bestHistoricalE1rm, record.improvement ?: -1.0, 0.0)
    }

    @Test
    fun correctedSetBelowThePriorBestRemovesTheRecord() {
        val history = listOf(log("prior", external("prior-set", 120.0, 5)))
        assertTrue(buildFinishRecords(listOf(exercise("movement", external("today", 125.0, 5))), history).isNotEmpty())
        assertTrue(buildFinishRecords(listOf(exercise("movement", external("today-corrected", 115.0, 5))), history).isEmpty())
    }

    @Test
    fun differentConfigurationBecomesItsOwnInitialReference() {
        val records = buildFinishRecords(
            listOf(exercise("movement", external("today", 100.0, 5), config = "config-b")),
            listOf(log("prior", external("prior-set", 120.0, 5), config = "config-a")),
        )
        assertEquals(1, records.size)
        assertTrue(records.single().isInitialReference)
        assertNull(records.single().previousValue)
    }
}
