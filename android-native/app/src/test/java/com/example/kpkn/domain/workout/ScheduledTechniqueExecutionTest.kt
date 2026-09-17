package com.example.kpkn.domain.workout

import com.example.kpkn.data.models.ExerciseSet
import com.example.kpkn.data.models.PlannedTechnique
import com.example.kpkn.data.models.TechniqueType
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class ScheduledTechniqueExecutionTest {

    @Test
    fun dropsetUsesZeroRestAndFiveKgPerTransition() {
        val set = ExerciseSet(
            id = "drop",
            targetReps = 8,
            weight = 40.0,
            isDropSet = true,
            plannedIntensityTechniques = listOf(
                PlannedTechnique(
                    id = "t",
                    type = TechniqueType.DROP_SET,
                    params = mapOf("count" to "2", "betweenMarked" to "true"),
                ),
            ),
        )
        val first = set.resolveScheduledTechniqueExecution(
            phaseIndex = 0,
            currentLoad = 40.0,
            normalRestSeconds = 90,
        )
        val second = set.resolveScheduledTechniqueExecution(
            phaseIndex = 1,
            currentLoad = 35.0,
            normalRestSeconds = 90,
        )
        val last = set.resolveScheduledTechniqueExecution(
            phaseIndex = 2,
            currentLoad = 30.0,
            normalRestSeconds = 90,
        )
        assertEquals(0, first?.restAfterSeconds)
        assertEquals(35.0, first?.nextLoad)
        assertEquals(0, second?.restAfterSeconds)
        assertEquals(30.0, second?.nextLoad)
        assertEquals(90, last?.restAfterSeconds)
        assertNull(last?.nextLoad)
        assertEquals(3, last?.phaseCount)
    }

    @Test
    fun restPauseKeepsLoadAndUsesFifteenSecondsUntilFinalPhase() {
        val set = ExerciseSet(
            id = "rp",
            targetReps = 8,
            weight = 30.0,
            isRestPause = true,
            plannedIntensityTechniques = listOf(
                PlannedTechnique(
                    id = "t",
                    type = TechniqueType.REST_PAUSE,
                    params = mapOf("count" to "2", "pauseSeconds" to "15", "reps" to "3", "betweenMarked" to "true"),
                ),
            ),
        )
        val first = set.resolveScheduledTechniqueExecution(phaseIndex = 0, currentLoad = 30.0, normalRestSeconds = 75)
        val last = set.resolveScheduledTechniqueExecution(phaseIndex = 2, currentLoad = 30.0, normalRestSeconds = 75)
        assertEquals(15, first?.restAfterSeconds)
        assertEquals(30.0, first?.nextLoad)
        assertEquals(8, first?.targetReps)
        assertEquals(75, last?.restAfterSeconds)
        assertNull(last?.nextLoad)
    }

    @Test
    fun legacyEditorTechniqueIsNormalizedOnlyAtEditorBoundary() {
        val set = ExerciseSet(
            id = "legacy",
            targetReps = 8,
            isDropSet = true,
            plannedIntensityTechniques = listOf(
                PlannedTechnique(id = "legacy-drop", type = TechniqueType.DROP_SET, params = mapOf("count" to "1")),
            ),
        )
        val normalized = set.normalizeEditorScheduledTechnique()
        assertTrue(normalized.isVolumeReplacedTechnique())
        assertEquals("true", normalized.plannedIntensityTechniques.single().params["betweenMarked"])
    }
}
