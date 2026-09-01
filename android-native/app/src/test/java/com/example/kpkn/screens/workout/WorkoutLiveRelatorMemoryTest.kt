package com.example.kpkn.screens.workout

import com.example.kpkn.data.models.CompletedSet
import com.example.kpkn.data.models.ExerciseDiscomfortReport
import com.example.kpkn.data.models.WorkoutLog
import com.example.kpkn.domain.calculations.calculateHybrid1RM
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class WorkoutLiveRelatorMemoryTest {

    @Test
    fun identityKeysMatchAcrossProgramRebuild() {
        val oldKeys = identityKeysFor(
            canonicalId = null,
            exerciseDbId = null,
            catalogDefinitionId = null,
            exerciseId = "session-uuid-old",
            name = "Press banca",
        )
        val newKeys = identityKeysFor(
            canonicalId = "bench_press",
            exerciseDbId = "bench_press",
            catalogDefinitionId = "def_bench",
            exerciseId = "session-uuid-new",
            name = "Press banca",
        )
        assertTrue(identityKeysOverlap(oldKeys, newKeys))
        assertTrue(oldKeys.any { it.startsWith("custom:") })
        assertTrue("bench_press" in newKeys)
    }

    @Test
    fun discomfortReportMatchesByNameWhenIdsChanged() {
        val keys = identityKeysFor(
            canonicalId = "bench_press",
            exerciseDbId = "bench_press",
            catalogDefinitionId = "def_bench",
            exerciseId = "new-id",
            name = "Press banca",
        )
        val report = ExerciseDiscomfortReport(
            exerciseId = "old-id",
            exerciseName = "Press banca",
            technicalQuality = 7,
            discomfortIds = listOf("shoulder_anterior"),
        )
        assertTrue(discomfortReportMatches(report, keys, "Press banca"))
    }

    @Test
    fun latestDiscomfortReadsNewestMatchingLog() {
        val keys = identityKeysFor(
            canonicalId = "bench_press",
            exerciseDbId = "bench_press",
            catalogDefinitionId = null,
            exerciseId = "new-id",
            name = "Press banca",
        )
        val older = log(
            id = "old",
            date = "2026-01-01T10:00:00.000Z",
            reports = listOf(
                ExerciseDiscomfortReport(
                    exerciseId = "old-id",
                    exerciseName = "Press banca",
                    technicalQuality = 6,
                    discomfortIds = listOf("elbow_lateral"),
                ),
            ),
        )
        val newer = log(
            id = "new",
            date = "2026-08-01T10:00:00.000Z",
            reports = listOf(
                ExerciseDiscomfortReport(
                    exerciseId = "another-id",
                    canonicalExerciseId = "bench_press",
                    exerciseName = "Press banca",
                    technicalQuality = 7,
                    discomfortIds = listOf("shoulder_anterior"),
                ),
            ),
        )
        val ids = latestDiscomfortIdsFromLogs(listOf(newer, older), keys, "Press banca")
        assertEquals(listOf("shoulder_anterior"), ids)
    }

    @Test
    fun prRequiresBeatingPriorBest() {
        val live = calculateHybrid1RM(110.0, 3)
        val hit = resolveRelatorPrHint(
            liveWeightKg = 110.0,
            liveReps = 3,
            historyBestE1rm = live - 5.0,
            sessionBestPreviousE1rm = 0.0,
            isStar = true,
            goal1RmKg = 140.0,
        )
        assertNotNull(hit)
        assertEquals(live, hit!!.estimatedRmKg, 0.05)
        assertTrue(hit.isStar)
        assertTrue((hit.goalPct ?: 0) in 1..99)

        val noHit = resolveRelatorPrHint(
            liveWeightKg = 110.0,
            liveReps = 3,
            historyBestE1rm = live,
            sessionBestPreviousE1rm = 0.0,
            isStar = false,
            goal1RmKg = null,
        )
        assertNull(noHit)
    }

    @Test
    fun firstWorkingSetMemorySkipsWarmup() {
        val memory = firstWorkingSetMemory(
            listOf(
                CompletedSet(id = "w", weight = 40.0, reps = 8, isWarmup = true),
                CompletedSet(id = "a", weight = 80.0, reps = 8),
            ),
        )
        assertNotNull(memory)
        assertEquals(80.0, memory!!.weightKg, 0.0)
        assertEquals(8, memory.reps)
    }

    @Test
    fun pickPrefersThisSessionDiscomfort() {
        val hint = pickRelatorDiscomfortHint(
            sameExerciseThisSessionLabels = listOf("Hombro anterior"),
            otherThisSession = listOf("Extensiones" to "Codo (cara externa)"),
            previousSessionLabels = listOf("Lumbar"),
        )
        assertNotNull(hint)
        assertTrue(hint!!.fromThisSession)
        assertEquals("Hombro anterior", hint.label)
        assertNull(hint.sourceExerciseName)
    }

    private fun log(
        id: String,
        date: String,
        reports: List<ExerciseDiscomfortReport>,
    ) = WorkoutLog(
        id = id,
        programId = "p",
        sessionId = "s",
        sessionName = "S",
        date = date,
        durationMinutes = 40,
        postExerciseReports = reports,
    )
}
