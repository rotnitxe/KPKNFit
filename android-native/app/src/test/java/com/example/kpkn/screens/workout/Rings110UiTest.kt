package com.example.kpkn.screens.workout

import com.example.kpkn.data.models.MuscleRecoveryStatus
import com.example.kpkn.data.models.RecoveryStatus
import com.example.kpkn.screens.home.homeBodyValues
import com.example.kpkn.screens.workout.components.ReadinessSheetState
import org.junit.Assert.*
import org.junit.Test

/**
 * Pruebas de integración de superficies de interfaz de 1.10v2 (screens/):
 * - RingEditStates: derivedMuscular redondea y no trunca.
 * - RingDetailSheet: homeBodyValues no descarta músculos con hasEvidence=true o recoveryScore < 100.
 * - WorkoutCoachMessages: no afirma 'batería baja' desde gasto ni sugiere 'probar RMs'.
 */
class Rings110UiTest {

    @Test
    fun `derivedMuscular en ReadinessSheetState redondea y no trunca`() {
        val state = ReadinessSheetState(startNeural = 80, startMuscular = 80, startSpinal = 80)
        state.muscleAdjustments["Pectorales"] = 61
        state.muscleAdjustments["Dorsales"] = 62
        state.muscleAdjustments["Bíceps"] = 62
        // Promedio: (61 + 62 + 62) / 3 = 185 / 3 = 61.666...
        // Con toInt() daba 61. Con roundToInt() da 62.
        assertEquals(62, state.derivedMuscular(80))
    }

    @Test
    fun `homeBodyValues incluye musculos con evidencia inicial sin historial`() {
        val perMuscle = mapOf(
            "Cuádriceps" to MuscleRecoveryStatus(
                muscleName = "Cuádriceps",
                recoveryScore = 60,
                hoursToRecovery = 12,
                hoursSinceLastSession = -1,
                effectiveSets = 0,
                status = RecoveryStatus.RECOVERING,
                hasEvidence = true,
            ),
            "Pectorales" to MuscleRecoveryStatus(
                muscleName = "Pectorales",
                recoveryScore = 100,
                hoursToRecovery = 0,
                hoursSinceLastSession = -1,
                effectiveSets = 0,
                status = RecoveryStatus.FRESH,
                hasEvidence = false,
            ),
        )
        val values = homeBodyValues(perMuscle)
        assertEquals(60, values["Cuádriceps"])
        assertNull("Pectorales sin evidencia no debe figurar en bodyValues", values["Pectorales"])
    }

    @Test
    fun `workout coach messages no afirma bateria baja desde gasto de serie ni sugiere probar RMs`() {
        // Gasto alto, disponibilidad alta, fase tardía
        val msg1 = WorkoutCoachMessages.getMessage(weightedDrainPct = 9.0, readinessScore = 90, sessionProgress = 0.8)
        assertFalse("No debe afirmar 'batería está baja'", msg1.body.contains("batería está baja"))
        assertTrue("Debe mencionar esfuerzo alto", msg1.body.contains("esfuerzo de la sesión ya es alto"))

        // Gasto bajo, disponibilidad alta, fase temprana
        val msg2 = WorkoutCoachMessages.getMessage(weightedDrainPct = 2.0, readinessScore = 90, sessionProgress = 0.1)
        assertFalse("No debe sugerir 'probar RMs'", msg2.body.contains("probar RMs"))
        assertFalse("No debe titular 'max effort'", msg2.title.contains("max effort"))
    }
}
