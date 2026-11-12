package com.example.kpkn.screens.workout

import com.example.kpkn.data.models.CompletedExercise
import com.example.kpkn.data.models.CompletedSet
import com.example.kpkn.data.models.DropSetData
import com.example.kpkn.data.models.LoadModeV2
import com.example.kpkn.data.models.RecordedSetPayload
import com.example.kpkn.data.models.RestPauseData
import com.example.kpkn.data.models.UnitModeV2
import org.junit.Assert.assertEquals
import org.junit.Test

class SessionTonnageLoadModeTest {
    private fun set(
        id: String,
        weight: Double,
        reps: Int,
        mode: LoadModeV2,
        external: Double? = null,
        assisted: Double? = null,
        drops: List<DropSetData> = emptyList(),
        pauses: List<RestPauseData> = emptyList(),
    ) = CompletedSet(
        id = id,
        weight = weight,
        reps = reps,
        loadMode = mode,
        originalLoad = when (mode) {
            LoadModeV2.BODYWEIGHT -> 0.0
            LoadModeV2.ASSISTED -> assisted
            else -> external ?: weight
        },
        dropSets = drops,
        restPauses = pauses,
        recordedPayloadV3 = RecordedSetPayload(
            actualValue = reps.toDouble(),
            exerciseId = "exercise",
            loadInputMode = mode,
            unitMode = UnitModeV2.REPS,
            externalLoad = external,
            assistedLoad = assisted,
            bodyWeightSnapshot = 106.0,
            completedReps = reps,
        ),
    )

    private fun exercise(vararg sets: CompletedSet) = CompletedExercise(
        exerciseId = "exercise",
        exerciseName = "Ejercicio",
        sets = sets.toList(),
    )

    @Test
    fun bodyweightAndAssistanceDoNotAddExternalTonnage() {
        val bodyweight = set("bw", weight = 0.0, reps = 5, mode = LoadModeV2.BODYWEIGHT)
        val assisted = set("as", weight = 0.0, reps = 5, mode = LoadModeV2.ASSISTED, assisted = 20.0)
        assertEquals(0.0, sessionTonnage(listOf(exercise(bodyweight, assisted))), 0.0)
    }

    @Test
    fun lastreCountsOnlyAddedKgAndDropSetsDoNotDoubleBodyMass() {
        val lastre = set(
            id = "lastre",
            weight = 40.0,
            reps = 5,
            mode = LoadModeV2.LASTRE,
            external = 40.0,
            drops = listOf(DropSetData(weight = 30.0, reps = 3)),
            pauses = listOf(RestPauseData(restTime = 20, reps = 2)),
        )
        assertEquals(40.0 * 5 + 30.0 * 3 + 40.0 * 2, sessionTonnage(listOf(exercise(lastre))), 0.0)
    }

    @Test
    fun conventionalLoadAndLegacyKeepHistoricalTonnage() {
        val load = set("load", weight = 100.0, reps = 5, mode = LoadModeV2.LOAD, external = 100.0)
        val legacy = CompletedSet(id = "legacy", weight = 80.0, reps = 5)
        assertEquals(500.0 + 400.0, sessionTonnage(listOf(exercise(load, legacy))), 0.0)
    }

    @Test
    fun liveHistoryBestSetAndFormattingRespectBodyweightLastreAndAssisted() {
        val bw = set("bw", weight = 0.0, reps = 10, mode = LoadModeV2.BODYWEIGHT)
        assertEquals("Corporal × 10", historySetValue(historyBestSet(listOf(bw))!!))

        val unrecoverableLastre = CompletedSet(id = "ul", weight = 146.0, reps = 6, loadMode = LoadModeV2.LASTRE, originalLoad = null)
        val validLastre = set("vl", weight = 40.0, reps = 5, mode = LoadModeV2.LASTRE, external = 40.0)
        assertEquals("+40 kg × 5", historySetValue(historyBestSet(listOf(unrecoverableLastre, validLastre))!!))
        assertEquals("Lastre · sin detalle × 6", historySetValue(unrecoverableLastre))

        val highAssist = set("ha", weight = 0.0, reps = 8, mode = LoadModeV2.ASSISTED, assisted = 35.0)
        val lowAssist = set("la", weight = 0.0, reps = 6, mode = LoadModeV2.ASSISTED, assisted = 15.0)
        assertEquals("15 asist. × 6", historySetValue(historyBestSet(listOf(highAssist, lowAssist))!!))
    }
}
