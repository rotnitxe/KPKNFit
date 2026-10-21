package com.example.kpkn.screens.workout

import com.example.kpkn.data.models.MuscleRecoveryStatus
import com.example.kpkn.data.models.RecoveryBands
import com.example.kpkn.data.models.RecoveryStatus
import com.example.kpkn.domain.relator.*
import com.example.kpkn.screens.home.homeBodyValues
import com.example.kpkn.screens.workout.components.ReadinessSheetState
import org.junit.Assert.*
import org.junit.Test

/**
 * Pruebas de integración de superficies de interfaz y relator de 1.10v2 (screens/):
 * - RingEditStates: derivedMuscular redondea y no trunca.
 * - RingDetailSheet: homeBodyValues no descarta músculos con hasEvidence=true o recoveryScore < 100.
 * - WorkoutCoachMessages: no afirma 'batería baja' desde gasto ni sugiere 'probar RMs'.
 * - WarmupObserver: dailyScore=null con exerciseScore=100 -> sin «Salta aprox».
 * - WarmupObserver: ambos null con 300 s restantes -> sin «Salta aprox».
 * - WarmupObserver: dailyScore=85 -> ofrece «Salta aprox».
 * - ReadinessObserver: dailyScore=null -> sin candidato de disponibilidad inventado.
 * - ReadinessObserver: dailyScore=65 -> ofrece ajuste de carga.
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

    @Test
    fun `warmup observer no ofrece salta aprox con dailyScore null y exerciseScore 100`() {
        val context = dummyRelatorContext(
            phase = RelatorSessionPhase.WARMUP,
            dailyScore = null,
            exerciseScore = 100,
            remainingSeconds = 600,
            suggestedKg = 60.0,
        )
        val candidates = WarmupObserver.observe(context)
        val actions = candidates.flatMap { it.actions }
        val skipAction = actions.find { it.kind == RelatorActionKind.SKIP_REMAINING_WARMUPS }
        assertNull("No debe ofrecer 'Salta aprox' cuando dailyScore es null", skipAction)
    }

    @Test
    fun `warmup observer no ofrece salta aprox cuando ambos scores son null con 300 s restantes`() {
        val context = dummyRelatorContext(
            phase = RelatorSessionPhase.WARMUP,
            dailyScore = null,
            exerciseScore = null,
            remainingSeconds = 300,
            suggestedKg = 60.0,
        )
        val candidates = WarmupObserver.observe(context)
        val actions = candidates.flatMap { it.actions }
        val skipAction = actions.find { it.kind == RelatorActionKind.SKIP_REMAINING_WARMUPS }
        assertNull("No debe ofrecer 'Salta aprox' por el solo tiempo cuando no hay disponibilidad diaria", skipAction)
    }

    @Test
    fun `warmup observer si ofrece salta aprox con dailyScore alto`() {
        val context = dummyRelatorContext(
            phase = RelatorSessionPhase.WARMUP,
            dailyScore = 85,
            exerciseScore = null,
            remainingSeconds = 600,
            suggestedKg = 60.0,
        )
        val candidates = WarmupObserver.observe(context)
        val actions = candidates.flatMap { it.actions }
        val skipAction = actions.find { it.kind == RelatorActionKind.SKIP_REMAINING_WARMUPS }
        assertNotNull("Debe ofrecer 'Salta aprox' con dailyScore >= 85", skipAction)
    }

    @Test
    fun `readiness observer no emite candidatos de disponibilidad sin dailyScore`() {
        val context = dummyRelatorContext(
            phase = RelatorSessionPhase.WORKING,
            dailyScore = null,
            exerciseScore = 100,
            preWorkoutDiscomforts = listOf("Rodilla izquierda"),
            doms = 4,
        )
        val candidates = ReadinessObserver.observe(context)
        assertTrue("No debe emitir candidatos de disponibilidad sin dailyScore observado", candidates.isEmpty())
    }

    @Test
    fun `readiness observer emite candidato cuando dailyScore es bajo`() {
        val context = dummyRelatorContext(
            phase = RelatorSessionPhase.WORKING,
            dailyScore = 65,
            exerciseScore = null,
        )
        val candidates = ReadinessObserver.observe(context)
        assertFalse("Debe emitir candidato con dailyScore conocido < 70", candidates.isEmpty())
        val adjust = candidates.flatMap { it.actions }.find { it.kind == RelatorActionKind.ADJUST_LOAD }
        assertNotNull("Debe ofrecer ajuste de carga con score 65", adjust)
    }

    private fun dummyRelatorContext(
        phase: RelatorSessionPhase,
        dailyScore: Int?,
        exerciseScore: Int?,
        remainingSeconds: Int? = null,
        suggestedKg: Double? = null,
        preWorkoutDiscomforts: List<String> = emptyList(),
        doms: Int? = null,
    ): RelatorContext = RelatorContext(
        sessionId = "s1",
        setKey = "e1_0",
        exerciseId = "e1",
        exerciseName = "Press banca",
        setIndex = 1,
        phase = phase,
        progress = RelatorProgressSlice(
            completedWorkingSets = 0,
            totalWorkingSets = 3,
            remainingSeconds = remainingSeconds,
        ),
        readiness = RelatorReadinessSlice(
            dailyScore = dailyScore,
            dailyLabel = dailyScore?.let { RecoveryBands.labelForScore(it) },
            dailyDetails = emptyList<String>(),
            exerciseScore = exerciseScore,
            doms = doms,
            preWorkoutDiscomforts = preWorkoutDiscomforts,
        ),
        warmup = RelatorWarmupSlice(
            suggestedKg = suggestedKg,
            remainingCount = 1,
        ),
    )
}
