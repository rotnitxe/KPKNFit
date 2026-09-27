package com.example.kpkn.screens.onboarding

import com.example.kpkn.data.models.CalibrationResponseState
import com.example.kpkn.data.models.TrainingStyle
import com.example.kpkn.domain.onboarding.SetupStepId
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Test

/**
 * Contrato de procedencia del perfil de volumen (E-017 / T-004 / AC-T004-03):
 * una respuesta COMPLETA con `responseState=UNKNOWN` conserva UNKNOWN (o
 * perfil null sin presentarse como calibrado), SUGGESTED se conserva, y las
 * cuatro respuestas declaradas desde la UI permanecen DECLARED. Jamás se
 * recodifica una sugerencia/restauración como declaración del usuario.
 */
class VolumeCalibrationProvenanceTest {

    private fun completeAnswers(state: CalibrationResponseState) = SetupVolumeAnswers(
        style = TrainingStyle.BODYBUILDER,
        technique = 2,
        consistency = 2,
        strength = 2,
        mobility = 2,
        responseState = state,
    )

    /** Recalcula vía la proyección real de estilo ([SetupStepAnswers.withVolumeStyle]). */
    private fun recalculated(state: CalibrationResponseState, mobility: Int? = 2): SetupWizardDraft =
        SetupWizardDraft(volumeAnswers = completeAnswers(state).copy(mobility = mobility))
            .withStepChoice(SetupStepId.STYLE, "powerlifter")

    @Test
    fun `UNKNOWN completo se conserva como UNKNOWN al recalcular`() {
        val draft = recalculated(CalibrationResponseState.UNKNOWN)
        val profile = draft.volumeCalibrationProfile
        assertNotNull("Con estilo y cuatro valores el perfil existe", profile)
        assertEquals(
            "UNKNOWN nunca se convierte en DECLARED",
            CalibrationResponseState.UNKNOWN,
            profile?.responses?.state,
        )
    }

    @Test
    fun `SUGGESTED se conserva como SUGGESTED al recalcular`() {
        val draft = recalculated(CalibrationResponseState.SUGGESTED)
        assertEquals(
            CalibrationResponseState.SUGGESTED,
            draft.volumeCalibrationProfile?.responses?.state,
        )
    }

    @Test
    fun `DECLARED de la UI permanece DECLARED al recalcular`() {
        val draft = recalculated(CalibrationResponseState.DECLARED)
        assertEquals(
            CalibrationResponseState.DECLARED,
            draft.volumeCalibrationProfile?.responses?.state,
        )
    }

    @Test
    fun `respuesta incompleta deja perfil null sin fingir calibracion`() {
        val draft = recalculated(CalibrationResponseState.UNKNOWN, mobility = null)
        assertNull("Sin las cuatro respuestas no hay perfil de calibración", draft.volumeCalibrationProfile)
    }

    @Test
    fun `repair conserva UNKNOWN del borrador restaurado`() {
        val repaired = SetupDraftCompatibility.repair(
            SetupWizardDraft(
                goal = SetupGoal.MUSCLE,
                volumeAnswers = completeAnswers(CalibrationResponseState.UNKNOWN).copy(style = TrainingStyle.POWERLIFTER),
            ),
        )
        assertNotNull("La reparación recalibra con el estilo del objetivo", repaired.volumeCalibrationProfile)
        assertEquals(
            "El borrador restaurado con UNKNOWN no se recalibra como DECLARED",
            CalibrationResponseState.UNKNOWN,
            repaired.volumeCalibrationProfile?.responses?.state,
        )
    }

    @Test
    fun `repair conserva SUGGESTED del borrador restaurado`() {
        val repaired = SetupDraftCompatibility.repair(
            SetupWizardDraft(
                goal = SetupGoal.MUSCLE,
                volumeAnswers = completeAnswers(CalibrationResponseState.SUGGESTED).copy(style = TrainingStyle.POWERLIFTER),
            ),
        )
        assertNotNull(repaired.volumeCalibrationProfile)
        assertEquals(
            CalibrationResponseState.SUGGESTED,
            repaired.volumeCalibrationProfile?.responses?.state,
        )
    }
}


