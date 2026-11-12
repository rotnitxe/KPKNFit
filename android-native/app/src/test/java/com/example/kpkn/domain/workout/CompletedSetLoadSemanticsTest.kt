package com.example.kpkn.domain.workout

import com.example.kpkn.data.models.CompletedSet
import com.example.kpkn.data.models.LoadModeV2
import com.example.kpkn.data.models.RecordedSetPayload
import com.example.kpkn.data.models.UnitModeV2
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class CompletedSetLoadSemanticsTest {
    private fun set(
        weight: Double,
        reps: Int = 5,
        mode: LoadModeV2?,
        external: Double? = null,
        assisted: Double? = null,
        body: Double? = 106.0,
        originalLoad: Double? = weight.takeIf { it > 0.0 },
    ) = CompletedSet(
        id = "set",
        weight = weight,
        reps = reps,
        loadMode = mode,
        originalLoad = originalLoad,
        recordedPayloadV3 = RecordedSetPayload(
            actualValue = reps.toDouble(),
            exerciseId = "exercise",
            loadInputMode = mode ?: LoadModeV2.LOAD,
            unitMode = UnitModeV2.REPS,
            externalLoad = external,
            assistedLoad = assisted,
            bodyWeightSnapshot = body,
            completedReps = reps,
        ),
    )

    @Test
    fun bodyweightDoesNotBecomeLiftedLoad() {
        val value = set(weight = 0.0, mode = LoadModeV2.BODYWEIGHT)
        assertEquals("Peso corporal · 5 reps", CompletedSetLoadSemantics.displaySet(value))
        assertEquals(0.0, CompletedSetLoadSemantics.externalLoadKg(value) ?: -1.0, 0.0)
        assertFalse(CompletedSetLoadSemantics.supportsEstimatedOneRm(value))
        assertNull(CompletedSetLoadSemantics.assistanceKg(value))
    }

    @Test
    fun lastreKeepsOnlyAddedKg() {
        val value = set(weight = 40.0, mode = LoadModeV2.LASTRE, external = 40.0)
        assertEquals("Peso corporal + 40 kg de lastre · 5 reps", CompletedSetLoadSemantics.displaySet(value))
        assertEquals(40.0, CompletedSetLoadSemantics.externalLoadKg(value) ?: -1.0, 0.0)
        assertFalse(CompletedSetLoadSemantics.supportsEstimatedOneRm(value))
    }

    @Test
    fun assistedKeepsAssistanceSeparate() {
        val value = set(weight = 0.0, mode = LoadModeV2.ASSISTED, assisted = 20.0, originalLoad = 20.0)
        assertEquals("20 kg de asistencia · 5 reps", CompletedSetLoadSemantics.displaySet(value))
        assertEquals(0.0, CompletedSetLoadSemantics.externalLoadKg(value) ?: -1.0, 0.0)
        assertEquals(20.0, CompletedSetLoadSemantics.assistanceKg(value) ?: -1.0, 0.0)
        assertFalse(CompletedSetLoadSemantics.supportsEstimatedOneRm(value))
    }

    @Test
    fun externalLoadAndLegacyRemainComparable() {
        val load = set(weight = 100.0, mode = LoadModeV2.LOAD)
        assertEquals("100 kg · 5 reps", CompletedSetLoadSemantics.displaySet(load))
        assertTrue(CompletedSetLoadSemantics.supportsEstimatedOneRm(load))

        val legacy = CompletedSet(id = "legacy", weight = 80.0, reps = 5)
        assertEquals("80 kg · 5 reps", CompletedSetLoadSemantics.displaySet(legacy))
        assertTrue(CompletedSetLoadSemantics.supportsEstimatedOneRm(legacy))
    }

    @Test
    fun missingInputKeepsModalityAndSaysNoDetail() {
        val lastre = set(weight = 0.0, mode = LoadModeV2.LASTRE, external = null, originalLoad = null)
        assertEquals(
            "Peso corporal + lastre · Carga sin detalle · 5 reps",
            CompletedSetLoadSemantics.displaySet(lastre),
        )
        assertNull(CompletedSetLoadSemantics.externalLoadKg(lastre))

        val assisted = set(weight = 0.0, mode = LoadModeV2.ASSISTED, assisted = null, originalLoad = null)
        assertEquals(
            "Asistencia · Carga sin detalle · 5 reps",
            CompletedSetLoadSemantics.displaySet(assisted),
        )
        assertNull(CompletedSetLoadSemantics.assistanceKg(assisted))
    }

    @Test
    fun athlete106KgAbWheelAndLastreTonnage() {
        val abWheel = set(weight = 0.0, reps = 5, mode = LoadModeV2.BODYWEIGHT, body = 106.0)
        assertFalse(CompletedSetLoadSemantics.supportsEstimatedOneRm(abWheel))
        assertEquals(0.0, CompletedSetLoadSemantics.externalTonnageKg(abWheel, 5.0), 0.001)
        assertEquals(0.0, com.example.kpkn.domain.performance.StrengthModel.estimate1RM(abWheel), 0.001)

        val lastre = set(weight = 40.0, reps = 5, mode = LoadModeV2.LASTRE, external = 40.0, body = 106.0)
        assertFalse(CompletedSetLoadSemantics.supportsEstimatedOneRm(lastre))
        assertEquals(200.0, CompletedSetLoadSemantics.externalTonnageKg(lastre, 5.0), 0.001)
        assertEquals(0.0, com.example.kpkn.domain.performance.StrengthModel.estimate1RM(lastre), 0.001)
    }

    @Test
    fun legacyLastreWithOnlySyntheticWeightHasNoExternalLoadDetail() {
        val legacyLastre = CompletedSet(
            id = "legacy-lastre",
            weight = 146.0, // 106 kg bodyweight + 40 kg lastre synthetic total
            reps = 5,
            loadMode = LoadModeV2.LASTRE,
            originalLoad = null,
            recordedPayloadV3 = null,
        )
        assertNull(CompletedSetLoadSemantics.externalLoadKg(legacyLastre))
        assertEquals(0.0, CompletedSetLoadSemantics.externalTonnageKg(legacyLastre, 5.0), 0.001)
        assertEquals("Peso corporal + lastre · Carga sin detalle · 5 reps", CompletedSetLoadSemantics.displaySet(legacyLastre))
    }

    @Test
    fun legacyDropSetsAndRestPausesPreserveEnteredLoadWithoutAddingBodyMass() {
        val bwDrop = CompletedSet(
            id = "legacy-bw-drop",
            weight = 90.0,
            reps = 8,
            loadMode = LoadModeV2.BODYWEIGHT,
            originalLoad = null,
            dropSets = listOf(com.example.kpkn.data.models.DropSetData(weight = 90.0, reps = 5)),
            restPauses = listOf(com.example.kpkn.data.models.RestPauseData(restTime = 15, reps = 4)),
        )
        assertEquals(0.0, CompletedSetLoadSemantics.dropTonnageKg(bwDrop, 90.0, 5), 0.001)
        assertEquals(0.0, CompletedSetLoadSemantics.restPauseTonnageKg(bwDrop, 4), 0.001)

        val legacyLastreWithDrop = CompletedSet(
            id = "legacy-lastre-drop",
            weight = 146.0,
            reps = 6,
            loadMode = LoadModeV2.LASTRE,
            originalLoad = null,
            dropSets = listOf(com.example.kpkn.data.models.DropSetData(weight = 25.0, reps = 4)),
            restPauses = listOf(com.example.kpkn.data.models.RestPauseData(restTime = 20, reps = 3)),
        )
        assertEquals(100.0, CompletedSetLoadSemantics.dropTonnageKg(legacyLastreWithDrop, 25.0, 4), 0.001)
        assertEquals(0.0, CompletedSetLoadSemantics.restPauseTonnageKg(legacyLastreWithDrop, 3), 0.001)

        val legacyLoad = CompletedSet(
            id = "legacy-load-drop",
            weight = 80.0,
            reps = 6,
            dropSets = listOf(com.example.kpkn.data.models.DropSetData(weight = 60.0, reps = 5)),
            restPauses = listOf(com.example.kpkn.data.models.RestPauseData(restTime = 20, reps = 2)),
        )
        assertEquals(300.0, CompletedSetLoadSemantics.dropTonnageKg(legacyLoad, 60.0, 5), 0.001)
        assertEquals(160.0, CompletedSetLoadSemantics.restPauseTonnageKg(legacyLoad, 2), 0.001)
    }

    @Test
    fun lastreNeverEstimatesOneRmOrPrescribesFromSyntheticBodyMass() {
        val value = set(weight = 40.0, mode = LoadModeV2.LASTRE, external = 40.0)
        assertNull(LoadSuggestionEngine.estimatedCapacity(value))
    }
}
