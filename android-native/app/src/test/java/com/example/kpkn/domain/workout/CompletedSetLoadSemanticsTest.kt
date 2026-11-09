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
        val value = set(weight = 0.0, mode = LoadModeV2.LASTRE, external = null, originalLoad = null)
        assertEquals(
            "Peso corporal + lastre · Carga sin detalle · 5 reps",
            CompletedSetLoadSemantics.displaySet(value),
        )
        assertNull(CompletedSetLoadSemantics.externalLoadKg(value)?.takeIf { it > 0.0 })
    }

    @Test
    fun lastreNeverEstimatesOneRmOrPrescribesFromSyntheticBodyMass() {
        val value = set(weight = 40.0, mode = LoadModeV2.LASTRE, external = 40.0)
        assertNull(LoadSuggestionEngine.estimatedCapacity(value))
    }
}
