package com.example.kpkn.domain.auge

import com.example.kpkn.data.models.ExerciseReadiness
import com.example.kpkn.data.models.LoadModeV2
import com.example.kpkn.screens.workout.WorkoutAutoRegulation
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * Sugerencia de carga honesta (paquete 1.12c, F-41): pasos reales, porcentaje real y nunca sube sola.
 *
 * Con severidad 0,5 el recorte pedido es (70 − readiness) / 100 × 0,5:
 * readiness 50 ⇒ 10 %, readiness 45 ⇒ 12,5 %.
 */
class SetAdjustmentSnapTest {

    private fun readiness(score: Int): ExerciseReadiness = ExerciseReadiness(
        exerciseId = "ejercicio-prueba",
        exerciseName = "Ejercicio de prueba",
        overallScore = score,
        muscularComponent = score,
        cnsComponent = score,
        spinalComponent = 90,
        articularComponent = 90,
        structuralComponent = 90,
        relatedArticular = emptyList(),
        muscularWeight = 0.4,
        cnsWeight = 0.3,
        spinalWeight = 0.3,
        articularWeight = 0.0,
        setsPenaltyFactor = 1.0,
        intensityPenaltyFactor = 1.0,
        ermProximityFactor = 1.0,
        patternId = "empuje",
        involvedMuscleIds = listOf("Pectorales"),
    )

    @Test
    fun a_cinco_kg_con_diez_por_ciento_nunca_se_sugiere_igual() {
        val s = ExerciseReadinessEngine.calculateSetAdjustment(
            plannedWeight = 5.0,
            exerciseReadiness = readiness(50),
            severitySlider = 0.5,
            loadMode = LoadModeV2.LOAD,
        )
        assertTrue(s.reason, s.reason == "No requiere ajuste" || s.suggestedWeight < 5.0)
    }

    @Test
    fun b_cien_kilos_con_doce_coma_cinco_por_ciento_dan_87_5_y_porcentaje_real() {
        val s = ExerciseReadinessEngine.calculateSetAdjustment(
            plannedWeight = 100.0,
            exerciseReadiness = readiness(45),
            severitySlider = 0.5,
            loadMode = LoadModeV2.LOAD,
        )
        assertEquals(87.5, s.suggestedWeight, 1e-9)
        assertEquals(1 - 87.5 / 100.0, s.reductionPercent, 1e-9)
        assertTrue(s.reason, s.reason.contains("12.5%"))
    }

    @Test
    fun c_lastre_de_doce_coma_cinco_kilos_se_muestra_con_decimal() {
        val s = ExerciseReadinessEngine.calculateSetAdjustment(
            plannedWeight = 15.0,
            exerciseReadiness = readiness(50),
            severitySlider = 0.5,
            loadMode = LoadModeV2.LASTRE,
        )
        assertEquals(12.5, s.suggestedWeight, 1e-9)
        assertTrue(s.reason, s.reason.contains("12.5"))
        assertFalse(s.reason, s.reason.contains("12kg"))
    }

    @Test
    fun d_asistido_sube_al_menos_un_paso() {
        val s = ExerciseReadinessEngine.calculateSetAdjustment(
            plannedWeight = 20.0,
            exerciseReadiness = readiness(50),
            severitySlider = 0.5,
            loadMode = LoadModeV2.ASSISTED,
        )
        assertEquals(LoadModeV2.ASSISTED, s.suggestedLoadMode)
        assertTrue(s.suggestedWeight >= 20.0 + 2.5 - 1e-9)
    }

    @Test
    fun f_readiness_alta_no_sube_la_carga() {
        val factor = WorkoutAutoRegulation.computeReadinessAdjustmentFactor(
            readinessNeural = 95,
            readinessSpinal = 95,
            readinessMuscular = 95,
            readinessPerMuscle = null,
            involvedMuscleIds = emptyList(),
        )
        assertEquals(1.0, factor, 1e-9)
    }

    @Test
    fun f_readiness_baja_sigue_reduciendo() {
        val factor = WorkoutAutoRegulation.computeReadinessAdjustmentFactor(
            readinessNeural = 40,
            readinessSpinal = 40,
            readinessMuscular = 40,
            readinessPerMuscle = null,
            involvedMuscleIds = emptyList(),
        )
        assertTrue(factor < 1.0)
    }
}
