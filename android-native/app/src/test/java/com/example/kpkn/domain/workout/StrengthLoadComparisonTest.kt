package com.example.kpkn.domain.workout

import com.example.kpkn.data.models.CompletedSet
import com.example.kpkn.data.models.ExerciseSet
import com.example.kpkn.data.models.LoadModeV2
import com.example.kpkn.data.models.RecordedSetPayload
import com.example.kpkn.data.models.UnitModeV2
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class StrengthLoadComparisonTest {
    @Test
    fun `el lastre compara la carga total realizada con la carga total planificada`() {
        val set = CompletedSet("serie", weight = 100.0, reps = 5, rir = 2, recordedPayloadV3 = RecordedSetPayload(
            exerciseId = "dominada", loadInputMode = LoadModeV2.LASTRE, externalLoad = 20.0, bodyWeightSnapshot = 80.0))
        val plan = ExerciseSet("plan", weight = 20.0, targetReps = 5, targetRIR = 2)
        assertEquals(1.0, LoadSuggestionEngine.performanceRatio(set, plan)!!, 0.0)
        assertTrue(LoadSuggestionEngine.performanceRatio(set.copy(reps = 2), plan)!! < 1.0)
    }

    @Test
    fun `la asistencia y las unidades de tiempo no se comparan como fuerza`() {
        val payload = RecordedSetPayload(exerciseId = "ejercicio")
        val set = CompletedSet("serie", weight = 100.0, reps = 5, rir = 2, recordedPayloadV3 = payload)
        val plan = ExerciseSet("plan", weight = 100.0, targetReps = 5, targetRIR = 2)
        assertNull(LoadSuggestionEngine.performanceRatio(set.copy(recordedPayloadV3 = payload.copy(loadInputMode = LoadModeV2.ASSISTED)), plan))
        assertNull(LoadSuggestionEngine.performanceRatio(set.copy(recordedPayloadV3 = payload.copy(unitMode = UnitModeV2.TIME)), plan))
    }

    @Test
    fun `el conteo heredado queda disponible y solo back off lo aplica`() {
        (0..5).forEach { count ->
            assertEquals(1.0, LoadSuggestionEngine.contextualFatigueFactor(count, false), 0.0)
            assertEquals(LoadSuggestionEngine.fatigueFactorForPriorCompletedSets(count), LoadSuggestionEngine.contextualFatigueFactor(count, true), 0.0)
        }
    }
}
