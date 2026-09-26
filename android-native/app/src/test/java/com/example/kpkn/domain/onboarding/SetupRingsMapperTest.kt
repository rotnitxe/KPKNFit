package com.example.kpkn.domain.onboarding

import com.example.kpkn.data.models.InitialRecoveryActivityType
import com.example.kpkn.data.models.InitialRecoveryEvidenceFactory
import com.example.kpkn.data.models.InitialRecoveryIntensity
import com.example.kpkn.data.models.InitialRecoveryResponseState
import com.example.kpkn.data.models.InitialRecoverySensations
import com.example.kpkn.data.models.Settings
import com.example.kpkn.data.onboarding.SetupPatchField
import com.example.kpkn.data.onboarding.SetupSettingsPatch
import com.example.kpkn.data.onboarding.previewCheckIn
import com.example.kpkn.data.onboarding.toEvidencePatchField
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class SetupRingsMapperTest {
    @Test
    fun feelingsAreOptionalAndTimestampIsAssignedOnlyAtMapping() {
        // Historia declarada + sensaciones parciales: sigue siendo válido porque la
        // fábrica de evidencia admite sensaciones anulables. La sensación que no se
        // informó viaja `null` (nunca un valor por defecto).
        val partial = SetupRingsMapper.map(
            SetupRingsInput(startAction = "Preparar mi punto de partida", recentTraining = false, muscleFeeling = 2, energy = 2),
            nowMs = 1000L,
        )
        assertEquals(RingsCompletion.VALID, partial.completion)
        assertEquals(RingsCalibration.FULL_EVIDENCE, partial.calibration)
        assertEquals(RingsEvidenceHandling.SET, partial.evidenceHandling)
        assertEquals(InitialRecoverySensations(muscular = 2, energy = 2, structure = null), partial.evidence?.sensations)
        assertEquals(SetupRingsCheckIn(muscular = 2, energy = 2), partial.checkIn)
        assertEquals(1000L, partial.capturedAtMs)

        val complete = SetupRingsMapper.map(
            SetupRingsInput(startAction = "Preparar mi punto de partida", recentTraining = false, muscleFeeling = 2, energy = 2, structureFeeling = 1, activityType = InitialRecoveryActivityType.MIXED, intensity = InitialRecoveryIntensity.MODERATE),
            nowMs = 1000L,
        )
        assertEquals(RingsCompletion.VALID, complete.completion)
        assertEquals(1000L, complete.evidence?.capturedAtMs)
        assertNotNull(complete.evidence)
    }

    @Test
    fun knownRecentTrainingWithSessionsAndOneFeelingBuildsEvidenceWithoutFabricatingSensations() {
        val mapping = SetupRingsMapper.map(
            SetupRingsInput(
                recentTraining = true,
                sessions = 3,
                recencyDays = 1,
                activityType = InitialRecoveryActivityType.MIXED,
                intensity = InitialRecoveryIntensity.MODERATE,
                muscleFeeling = 2,
                discomfortIds = listOf("knee"),
            ),
            nowMs = 1_000L,
        )
        assertEquals(RingsCompletion.VALID, mapping.completion)
        assertEquals(RingsCalibration.FULL_EVIDENCE, mapping.calibration)
        val evidence = requireNotNull(mapping.evidence)
        assertEquals(3, evidence.sessions)
        assertEquals(InitialRecoverySensations(muscular = 2, energy = null, structure = null), evidence.sensations)
        assertEquals(1_000L, evidence.capturedAtMs)
        // El check-in declarado solo contiene lo informado.
        assertEquals(SetupRingsCheckIn(muscular = 2), mapping.checkIn)
        assertEquals(RingsDiscomfortResponse.DECLARED, mapping.discomfortResponse)
    }

    @Test
    fun missingHistoryDataKeepsDeclaredCheckInAsValidPartialWithoutFabricatingSessions() {
        // Sesiones/recencia declaradas pero ausentes: jamás se sintetizan → INCOMPLETE,
        // pero el check-in real no se pierde (calibración parcial).
        val mapping = SetupRingsMapper.map(
            SetupRingsInput(recentTraining = true, muscleFeeling = 2),
            nowMs = 1_000L,
        )
        assertEquals(RingsCompletion.INCOMPLETE, mapping.completion)
        assertEquals(RingsCalibration.PARTIAL_CHECK_IN, mapping.calibration)
        assertEquals(RingsEvidenceHandling.UNCHANGED, mapping.evidenceHandling)
        assertNull(mapping.evidence)
        assertTrue(mapping.savesRealCheckIn)
        assertEquals(SetupRingsCheckIn(muscular = 2), mapping.previewCheckIn())
        assertEquals(1_000L, mapping.capturedAtMs)
    }

    @Test
    fun discomfortOnlyWithoutCalibrationStillPersistsItsPayload() {
        // Sin historial ni sensaciones, solo molestias: la calibración sigue siendo
        // NONE (no hay sensación que calibrar) pero el check-in sí se guarda.
        val none = SetupRingsMapper.map(
            SetupRingsInput(recentTrainingUnknown = true, discomfortResponse = RingsDiscomfortResponse.NONE),
            nowMs = 1_000L,
        )
        assertEquals(RingsCompletion.UNKNOWN, none.completion)
        assertEquals(RingsCalibration.NONE, none.calibration)
        assertTrue(none.savesRealCheckIn)
        // El staging usa el check-in SIN fabricar sensaciones: solo el campo de molestias.
        val staged = SetupRingsResponseMapping.stageCheckIn(
            checkIn = none.previewCheckIn(),
            discomforts = SetupRingsResponseMapping.discomfortField(none.discomfortResponse, none.discomfortIds),
        )
        assertEquals(setOf("discomforts"), staged.capturedFields)
        assertEquals(emptyList<String>(), staged.preWorkoutDiscomforts)
        assertEquals(SetupRingsCheckIn(), none.previewCheckIn())

        // Sin respuesta ni molestias no se guarda nada (desconocimiento = sin efectos).
        val unanswered = SetupRingsMapper.map(SetupRingsInput(recentTrainingUnknown = true), nowMs = 1_000L)
        assertEquals(RingsCalibration.NONE, unanswered.calibration)
        assertFalse(unanswered.savesRealCheckIn)
        assertTrue(SetupRingsResponseMapping.stageCheckIn(checkIn = unanswered.previewCheckIn()).isEmpty)
    }

    @Test
    fun removeAndPreserveAreDifferentCommitIntents() {
        assertEquals(RingsCompletion.OMITTED, SetupRingsMapper.map(SetupRingsInput(startAction = "Quitar estimación inicial"), 10L).completion)
        assertEquals(RingsCompletion.PRESERVE, SetupRingsMapper.map(SetupRingsInput(startAction = "Conservar estimación actual"), 10L).completion)
    }

    @Test
    fun unknownRecentTrainingNeverFabricatesEvidenceOrZeroSessions() {
        listOf(
            SetupRingsInput(recentTrainingUnknown = true),
            SetupRingsInput(recentTraining = null),
            SetupRingsInput(recentTraining = null, recentTrainingUnknown = true),
        ).forEach { input ->
            val mapping = SetupRingsMapper.map(input, 1_000L)
            assertEquals(RingsCompletion.UNKNOWN, mapping.completion)
            // El desconocimiento no es una ausencia declarada: no hay sessions = 0.
            assertEquals(RingsHistoryState.UNKNOWN, mapping.historyState)
            assertNull(mapping.evidence)
            assertEquals(RingsCalibration.NONE, mapping.calibration)
            assertEquals(RingsEvidenceHandling.UNCHANGED, mapping.evidenceHandling)
            assertEquals(SetupRingsCheckIn(), mapping.checkIn)
            assertTrue(SetupRingsResponseMapping.stageCheckIn(checkIn = mapping.previewCheckIn()).isEmpty)
        }
    }

    @Test
    fun unknownRecentTrainingWithDeclaredSensationsIsPartialCalibrationWithRealCheckIn() {
        val mapping = SetupRingsMapper.map(
            SetupRingsInput(recentTrainingUnknown = true, muscleFeeling = 2, energy = 3, structureFeeling = 4),
            nowMs = 1_000L,
        )
        // Calibración parcial: nunca VALID ni evidencia fabricada.
        assertEquals(RingsCalibration.PARTIAL_CHECK_IN, mapping.calibration)
        assertEquals(RingsCompletion.UNKNOWN, mapping.completion)
        assertNull(mapping.evidence)
        assertEquals(RingsHistoryState.UNKNOWN, mapping.historyState)
        assertTrue(mapping.savesRealCheckIn)
        // El check-in declarado es real y solo toca lo informado.
        assertEquals(SetupRingsCheckIn(muscular = 2, energy = 3, structure = 4), mapping.checkIn)
        val staged = SetupRingsResponseMapping.stageCheckIn(checkIn = mapping.previewCheckIn())
        assertEquals(75, staged.manualMuscularBattery)
        assertEquals(50, staged.manualNeuralBattery)
        assertEquals(25, staged.manualSpinalBattery)
        assertEquals(setOf("muscular", "energy", "structure"), staged.capturedFields)
    }

    @Test
    fun partialCalibrationLeavesInitialRecoveryEvidenceUnchanged() {
        val mapping = SetupRingsMapper.map(
            SetupRingsInput(recentTrainingUnknown = true, muscleFeeling = 2, energy = 2, structureFeeling = 2),
            nowMs = 1_000L,
        )
        assertEquals(RingsEvidenceHandling.UNCHANGED, mapping.evidenceHandling)
        assertEquals(SetupPatchField.Unchanged, mapping.toEvidencePatchField())
        val prior = InitialRecoveryEvidenceFactory.fromInputs(
            capturedAtMs = 5_000L,
            recencyDays = 0,
            sessions = 2,
            type = InitialRecoveryActivityType.MIXED,
            intensity = InitialRecoveryIntensity.MODERATE,
            sensations = InitialRecoverySensations(3, 3, 3),
        )
        val kept = SetupSettingsPatch(initialRecoveryEvidence = mapping.toEvidencePatchField())
            .applyTo(Settings(initialRecoveryEvidence = prior))
        assertEquals(prior, kept.initialRecoveryEvidence)
    }

    @Test
    fun explicitNoneAndUnreportedDiscomfortsAreDifferentStates() {
        val none = SetupRingsMapper.map(
            SetupRingsInput(recentTrainingUnknown = true, discomfortResponse = RingsDiscomfortResponse.NONE), 10L)
        val unreported = SetupRingsMapper.map(SetupRingsInput(recentTrainingUnknown = true), 10L)
        val omitted = SetupRingsMapper.map(
            SetupRingsInput(recentTrainingUnknown = true, discomfortResponse = RingsDiscomfortResponse.OMITTED), 10L)
        val declared = SetupRingsMapper.map(
            SetupRingsInput(recentTrainingUnknown = true, discomfortIds = listOf("knee", "knee")), 10L)

        assertEquals(RingsDiscomfortResponse.NONE, none.discomfortResponse)
        assertEquals(emptyList<String>(), SetupRingsResponseMapping.discomfortField(none.discomfortResponse, none.discomfortIds))
        // El desconocimiento nunca se convierte en «sin molestias».
        assertEquals(RingsDiscomfortResponse.NOT_ANSWERED, unreported.discomfortResponse)
        assertNull(SetupRingsResponseMapping.discomfortField(unreported.discomfortResponse, unreported.discomfortIds))
        assertNull(SetupRingsResponseMapping.discomfortField(omitted.discomfortResponse, omitted.discomfortIds))
        assertEquals(RingsDiscomfortResponse.DECLARED, declared.discomfortResponse)
        assertEquals(listOf("knee"), SetupRingsResponseMapping.discomfortField(declared.discomfortResponse, declared.discomfortIds))
    }

    @Test
    fun declaredAbsenceOfRecentTrainingStillProducesDerivedEvidence() {
        val mapping = SetupRingsMapper.map(
            SetupRingsInput(recentTraining = false, muscleFeeling = 2, energy = 2, structureFeeling = 1),
            nowMs = 1_000L,
        )
        assertEquals(RingsCompletion.VALID, mapping.completion)
        assertEquals(RingsCalibration.FULL_EVIDENCE, mapping.calibration)
        assertEquals(RingsHistoryState.DECLARED_ABSENT, mapping.historyState)
        assertEquals(RingsEvidenceHandling.SET, mapping.evidenceHandling)
        val evidence = requireNotNull(mapping.evidence)
        // Ausencia declarada válida, no un desconocimiento.
        assertEquals(0, evidence.sessions)
        assertEquals(InitialRecoveryResponseState.DERIVED, evidence.activityTypeState)
        assertEquals(evidence.capturedAtMs, evidence.coveredToMs)
        assertEquals(evidence.capturedAtMs, evidence.coveredFromMs)
    }

    @Test
    fun resumingDraftKeepsPreviousEvidenceTimestampAndExpiry() {
        val mapping = SetupRingsMapper.map(
            SetupRingsInput(recentTraining = false, muscleFeeling = 2, energy = 2, structureFeeling = 1, capturedAtMs = 5_000L),
            nowMs = 999_999L,
        )
        val evidence = requireNotNull(mapping.evidence)
        assertEquals(5_000L, evidence.capturedAtMs)
        assertEquals(5_000L, evidence.coveredToMs)
        // Reanudar el alta no renueva la fecha ni prolonga la caducidad de 14 días.
        assertEquals(5_000L + 14L * 24L * 60L * 60L * 1_000L, evidence.expiresAtMs)
        assertEquals(5_000L, mapping.capturedAtMs)
    }
}
