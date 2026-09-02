package com.example.kpkn.domain.workout

import com.example.kpkn.data.models.DropSetData
import com.example.kpkn.data.models.ExerciseSet
import com.example.kpkn.data.models.PlannedTechnique
import com.example.kpkn.data.models.RestPauseData
import com.example.kpkn.data.models.TechniqueType
import com.example.kpkn.domain.sessionassistant.SeriesTechnique
import com.example.kpkn.domain.sessionassistant.applyMarkedSeriesTechnique
import com.example.kpkn.domain.sessionassistant.withTechnique
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class SetTechniqueScopeTest {

    @Test
    fun unmarkedSetIsNone() {
        val set = ExerciseSet(id = "s1", targetReps = 8, weight = 80.0)
        assertEquals(SetTechniqueScope.NONE, set.techniqueScope())
        assertFalse(set.isStackedIntensityTechnique())
        assertFalse(set.isVolumeReplacedTechnique())
        assertNull(set.volumeReplacedLabel())
    }

    @Test
    fun editorChipDropsetIsStacked() {
        val set = ExerciseSet(id = "s1", targetReps = 8, weight = 80.0).withTechnique(SeriesTechnique.DROPSET)
        assertEquals(SetTechniqueScope.STACKED_ON_SET, set.techniqueScope())
        assertTrue(set.isStackedIntensityTechnique())
        assertFalse(set.isVolumeReplacedTechnique())
        assertNull(set.volumeReplacedLabel())
    }

    @Test
    fun markedDropsetChainIsVolumeReplaced() {
        val sets = listOf(
            ExerciseSet(id = "s1", targetReps = 8, weight = 80.0),
            ExerciseSet(id = "s2", targetReps = 8, weight = 80.0),
        )
        val out = applyMarkedSeriesTechnique(sets, setOf(0, 1), SeriesTechnique.DROPSET)
        assertEquals(SetTechniqueScope.VOLUME_REPLACED, out[0].techniqueScope())
        assertEquals(SetTechniqueScope.VOLUME_REPLACED, out[1].techniqueScope())
        assertEquals("DROPSET", out[0].volumeReplacedLabel())
        assertEquals(0, out[0].restAfterSeconds)
        assertTrue(out[0].isDropSet)
        assertEquals("true", out[0].plannedIntensityTechniques.first { it.type == TechniqueType.DROP_SET }.params["betweenMarked"])
    }

    @Test
    fun markedRestPauseIsVolumeReplaced() {
        val sets = listOf(
            ExerciseSet(id = "s1", targetReps = 8, weight = 80.0),
            ExerciseSet(id = "s2", targetReps = 8, weight = 80.0),
        )
        val out = applyMarkedSeriesTechnique(sets, setOf(0, 1), SeriesTechnique.REST_PAUSE)
        assertEquals(SetTechniqueScope.VOLUME_REPLACED, out[0].techniqueScope())
        assertEquals("REST PAUSE", out[0].volumeReplacedLabel())
        assertEquals(15, out[0].restAfterSeconds)
    }

    @Test
    fun densifiedPlanMiniSeriesIsVolumeReplaced() {
        val set = ExerciseSet(
            id = "s1",
            targetReps = 8,
            weight = 80.0,
            isDropSet = true,
            dropSets = listOf(DropSetData(weight = 68.0, reps = 4)),
        )
        assertEquals(SetTechniqueScope.VOLUME_REPLACED, set.techniqueScope())
        assertEquals("DROPSET", set.volumeReplacedLabel())
    }

    @Test
    fun densifiedRestPauseMiniSeriesIsVolumeReplaced() {
        val set = ExerciseSet(
            id = "s1",
            targetReps = 8,
            weight = 40.0,
            isRestPause = true,
            restPauses = listOf(RestPauseData(restTime = 15, reps = 4)),
        )
        assertEquals(SetTechniqueScope.VOLUME_REPLACED, set.techniqueScope())
        assertEquals("REST PAUSE", set.volumeReplacedLabel())
    }

    @Test
    fun stackedRestPauseWithoutBetweenMarked() {
        val set = ExerciseSet(
            id = "s1",
            targetReps = 8,
            weight = 80.0,
            isRestPause = true,
            plannedIntensityTechniques = listOf(
                PlannedTechnique(
                    id = "t1",
                    type = TechniqueType.REST_PAUSE,
                    params = mapOf("count" to "2", "pauseSeconds" to "15"),
                ),
            ),
        )
        assertEquals(SetTechniqueScope.STACKED_ON_SET, set.techniqueScope())
    }
}
