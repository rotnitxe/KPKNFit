package com.example.kpkn.screens.onboarding

import com.example.kpkn.data.models.InitialRecoveryActivityType
import com.example.kpkn.data.models.InitialRecoveryIntensity
import com.example.kpkn.data.models.InitialRecoveryMuscleScope
import com.example.kpkn.data.models.InitialRecoveryResponseState
import com.example.kpkn.data.models.RingsLiftPattern
import com.example.kpkn.data.models.RingsMuscleGroup
import com.example.kpkn.domain.onboarding.RingsCalibration
import com.example.kpkn.domain.onboarding.RingsCompletion
import com.example.kpkn.domain.onboarding.RingsDiscomfortResponse
import com.example.kpkn.domain.onboarding.RingsHistoryState
import com.example.kpkn.domain.onboarding.RingsMainLiftsState
import com.example.kpkn.domain.onboarding.RingsSessionDaysState
import com.example.kpkn.domain.onboarding.SetupRingsInput
import com.example.kpkn.domain.onboarding.SetupRingsMapper
import com.example.kpkn.domain.onboarding.SetupRingsMapping
import com.example.kpkn.domain.onboarding.SetupStepGraph
import com.example.kpkn.domain.onboarding.SetupStepId
import java.time.ZoneId
import kotlinx.serialization.decodeFromString
import kotlinx.serialization.json.Json
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

/** RINGS v2 del alta: días, ejercicios principales, series y sensaciones (reductores, derivados, mapeo y validación). */
class SetupRingsAnswersV2Test {

    private val now = 1_770_000_000_000L
    private val zone: ZoneId = ZoneId.of("Europe/Madrid")

    /** Mismo JSON que el ViewModel usa para guardar el borrador. */
    private val json = Json { ignoreUnknownKeys = true; encodeDefaults = true }

    private fun SetupRingsAnswers.mapping(): SetupRingsMapping = SetupRingsMapper.map(toMapperInput(), now, zone)

    @Test
    fun noTrainingAndNoIdeaAreExclusiveWithTheDays() {
        val trained = SetupWizardDraft().withTrainedDayToggled(0, now).withTrainedDayToggled(2, now)
        assertEquals(setOf(0, 2), trained.ringsAnswers?.trainedDayOffsets)

        val none = trained.withNoTraining(now)
        assertEquals(RingsSessionDaysState.NONE, none.ringsAnswers?.sessionDaysState)
        assertTrue(none.ringsAnswers?.trainedDayOffsets.orEmpty().isEmpty())

        // Tocar un día apaga «No entrené».
        val back = none.withTrainedDayToggled(1, now)
        assertEquals(RingsSessionDaysState.DECLARED, back.ringsAnswers?.sessionDaysState)
        assertEquals(setOf(1), back.ringsAnswers?.trainedDayOffsets)

        val unknown = back.withTrainingUnknown(now)
        assertEquals(RingsSessionDaysState.UNKNOWN, unknown.ringsAnswers?.sessionDaysState)
        assertTrue(unknown.ringsAnswers?.trainedDayOffsets.orEmpty().isEmpty())

        // Quitar el último día deja la pregunta sin responder.
        val cleared = unknown.withTrainedDayToggled(0, now).withTrainedDayToggled(0, now)
        assertEquals(RingsSessionDaysState.NOT_ANSWERED, cleared.ringsAnswers?.sessionDaysState)
    }

    @Test
    fun mainLiftsNoneAndUnknownAreExclusiveWithTheLifts() {
        val lifts = SetupWizardDraft()
            .withMainLiftToggled(RingsLiftPattern.SQUAT, now)
            .withMainLiftToggled(RingsLiftPattern.CARDIO_LONG, now)
        assertEquals(setOf(RingsLiftPattern.SQUAT, RingsLiftPattern.CARDIO_LONG), lifts.ringsAnswers?.mainLifts)

        val none = lifts.withNoMainLifts(now)
        assertEquals(RingsMainLiftsState.NONE, none.ringsAnswers?.mainLiftsState)
        assertTrue(none.ringsAnswers?.mainLifts.orEmpty().isEmpty())

        val bench = none.withMainLiftToggled(RingsLiftPattern.BENCH, now)
        assertEquals(RingsMainLiftsState.DECLARED, bench.ringsAnswers?.mainLiftsState)
        assertEquals(setOf(RingsLiftPattern.BENCH), bench.ringsAnswers?.mainLifts)

        val unknown = bench.withMainLiftsUnknown(now)
        assertEquals(RingsMainLiftsState.UNKNOWN, unknown.ringsAnswers?.mainLiftsState)
        assertTrue(unknown.ringsAnswers?.mainLifts.orEmpty().isEmpty())

        // Quitar el último ejercicio deja la pregunta sin responder.
        val cleared = bench.withMainLiftToggled(RingsLiftPattern.BENCH, now)
        assertEquals(RingsMainLiftsState.NOT_ANSWERED, cleared.ringsAnswers?.mainLiftsState)
    }

    @Test
    fun derivedFieldsFollowTheDaysAndTheLiftsSoTheRestOfTheAppStaysCoherent() {
        val draft = SetupWizardDraft()
            .withTrainedDayToggled(3, now)
            .withTrainedDayToggled(0, now)
            .withTrainedDayToggled(1, now)
            .withMainLiftToggled(RingsLiftPattern.SQUAT, now)
        val answers = requireNotNull(draft.ringsAnswers)
        assertEquals(true, answers.recentTraining)
        assertEquals(SetupRecentTrainingState.YES, answers.recentTrainingState)
        assertEquals(3, answers.sessionsLastSevenDays)
        assertEquals(0, answers.lastSessionRecencyDays)
        assertEquals(InitialRecoveryActivityType.STRENGTH, answers.activityType)
        assertEquals(InitialRecoveryResponseState.DECLARED, answers.axialExposure.state)
        assertEquals(1, answers.axialExposure.sessions)

        val mixed = draft.withMainLiftToggled(RingsLiftPattern.INTERVALS, now).ringsAnswers
        assertEquals(InitialRecoveryActivityType.MIXED, mixed?.activityType)

        val cardio = draft.withNoMainLifts(now).withMainLiftToggled(RingsLiftPattern.INTERVALS, now).ringsAnswers
        assertEquals(InitialRecoveryActivityType.CARDIO, cardio?.activityType)

        // Sin ejercicios conocidos no hay tipo de sesión ni exposición axial inventada.
        val unknownLifts = draft.withMainLiftsUnknown(now).ringsAnswers
        assertNull(unknownLifts?.activityType)
        assertEquals(InitialRecoveryResponseState.UNKNOWN, unknownLifts?.axialExposure?.state)

        val none = SetupWizardDraft().withNoTraining(now).ringsAnswers
        assertEquals(false, none?.recentTraining)
        assertEquals(SetupRecentTrainingState.NO, none?.recentTrainingState)
        assertEquals(0, none?.sessionsLastSevenDays)
        assertNull(none?.lastSessionRecencyDays)
        assertNull(none?.activityType)
    }

    @Test
    fun changingTheDaysRestampsTheCaptureButOtherAnswersKeepIt() {
        val first = SetupWizardDraft().withTrainedDayToggled(0, 1_000L)
        assertEquals(1_000L, first.ringsAnswers?.capturedAtMs)

        val lifts = first.withMainLiftToggled(RingsLiftPattern.BENCH, 2_000L)
        assertEquals(1_000L, lifts.ringsAnswers?.capturedAtMs)

        val moved = lifts.withTrainedDayToggled(1, 3_000L)
        assertEquals(3_000L, moved.ringsAnswers?.capturedAtMs)
    }

    @Test
    fun groupSetsAreBoundedAndNoIdeaLeavesTheGroupWithoutANumber() {
        val legs = SetupWizardDraft()
            .withGroupSets(RingsMuscleGroup.LEGS, 14, now)
            .withGroupSets(RingsMuscleGroup.BACK, 99, now)
        assertEquals(mapOf(RingsMuscleGroup.LEGS to 14, RingsMuscleGroup.BACK to 20), legs.ringsAnswers?.groupSets)

        val unknown = legs.withGroupSets(RingsMuscleGroup.LEGS, null, now)
        assertEquals(mapOf(RingsMuscleGroup.BACK to 20), unknown.ringsAnswers?.groupSets)
        assertEquals(setOf(RingsMuscleGroup.LEGS), unknown.ringsAnswers?.groupSetsUnknown)

        val answered = unknown.withGroupSets(RingsMuscleGroup.LEGS, 6, now)
        assertTrue(answered.ringsAnswers?.groupSetsUnknown.orEmpty().isEmpty())
    }

    @Test
    fun feelingsStayUnansweredWhenTheyAreOutsideTheScale() {
        val draft = SetupWizardDraft().withFeelings(muscular = 3, energy = null, structure = 9, nowEpochMs = now)
        val answers = requireNotNull(draft.ringsAnswers)
        assertEquals(3, answers.muscleFeeling)
        assertNull(answers.energy)
        assertNull(answers.structureFeeling)
    }

    @Test
    fun anOldDraftWithoutTheNewFieldsMapsExactlyLikeToday() {
        // Borrador guardado por la versión anterior: no trae ninguno de los campos de RINGS v2.
        val stored = """
            {"recentTraining":true,"recentTrainingState":"YES","sessionsLastSevenDays":3,
             "lastSessionRecencyDays":1,"activityType":"STRENGTH","activityTypeState":"DECLARED",
             "intensityLevel":"MODERATE","muscleFeeling":2,"energy":3,
             "axialExposure":{"state":"DECLARED","sessions":1,"intensity":"HARD","recencyDays":1},
             "muscleScope":"UNKNOWN","discomfortIds":[],"discomfortState":"NONE",
             "capturedAtMs":1700000000000}
        """.trimIndent()
        val answers = json.decodeFromString<SetupRingsAnswers>(stored)
        assertEquals(RingsSessionDaysState.NOT_ANSWERED, answers.sessionDaysState)
        assertEquals(RingsMainLiftsState.NOT_ANSWERED, answers.mainLiftsState)

        // Lo que el mapeo de siempre recibía de este borrador (ley v1).
        val todayInput = SetupRingsInput(
            recentTraining = true,
            recentTrainingUnknown = false,
            sessions = 3,
            recencyDays = 1,
            activityType = InitialRecoveryActivityType.STRENGTH,
            intensity = InitialRecoveryIntensity.MODERATE,
            muscleFeeling = 2,
            energy = 3,
            structureFeeling = null,
            axialState = InitialRecoveryResponseState.DECLARED,
            axialSessions = 1,
            axialIntensity = InitialRecoveryIntensity.MODERATE,
            muscleScope = InitialRecoveryMuscleScope.UNKNOWN,
            selectedMuscles = emptyList(),
            discomfortIds = emptyList(),
            capturedAtMs = 1_700_000_000_000L,
            discomfortResponse = RingsDiscomfortResponse.NONE,
        )
        val mapping = answers.mapping()
        assertEquals(SetupRingsMapper.map(todayInput, now, zone), mapping)
        assertEquals(RingsCompletion.VALID, mapping.completion)
        assertEquals(RingsCalibration.FULL_EVIDENCE, mapping.calibration)
        assertEquals(1, mapping.evidence?.estimatorVersion)
    }

    @Test
    fun aNewDraftWithDaysProducesEstimatorVersionTwoEvidence() {
        val draft = SetupWizardDraft()
            .withTrainedDayToggled(0, now)
            .withTrainedDayToggled(2, now)
            .withMainLiftToggled(RingsLiftPattern.SQUAT, now)
            .withMainLiftToggled(RingsLiftPattern.BENCH, now)
            .withGroupSets(RingsMuscleGroup.LEGS, 14, now)
            .withGroupSets(RingsMuscleGroup.BACK, 10, now)
            .withFeelings(muscular = 2, energy = 3, structure = null, nowEpochMs = now)
        val answers = requireNotNull(draft.ringsAnswers).copy(intensityLevel = InitialRecoveryIntensity.HARD)

        val mapping = answers.mapping()
        assertEquals(RingsCompletion.VALID, mapping.completion)
        assertEquals(RingsCalibration.FULL_EVIDENCE, mapping.calibration)
        val evidence = requireNotNull(mapping.evidence)
        assertEquals(2, evidence.estimatorVersion)
        assertEquals("initial-recovery-v3", evidence.contractRevision)
        assertEquals(2, evidence.dayLoads.size)
        assertFalse(evidence.muscleGeneric)
        // Con patrones axiales la columna sí está calibrada aunque no haya sensación de columna.
        assertTrue(evidence.structureKnown)
        assertEquals(now, evidence.capturedAtMs)
    }

    @Test
    fun noTrainingIsEvidenceWithNoLoadsAndFullScoresWithoutSensations() {
        val mapping = requireNotNull(SetupWizardDraft().withNoTraining(now).ringsAnswers).mapping()
        assertEquals(RingsCompletion.VALID, mapping.completion)
        assertEquals(RingsHistoryState.DECLARED_ABSENT, mapping.historyState)
        val evidence = requireNotNull(mapping.evidence)
        assertEquals(2, evidence.estimatorVersion)
        assertTrue(evidence.dayLoads.isEmpty())
        assertEquals(100, evidence.muscularScore)
        assertEquals(100, evidence.systemScore)
        assertEquals(100, evidence.structureScore)
        assertTrue(evidence.structureKnown)
    }

    @Test
    fun unknownDaysDoNotFabricateEvidence() {
        val mapping = requireNotNull(SetupWizardDraft().withTrainingUnknown(now).ringsAnswers).mapping()
        assertEquals(RingsCompletion.UNKNOWN, mapping.completion)
        assertEquals(RingsHistoryState.UNKNOWN, mapping.historyState)
        assertNull(mapping.evidence)
    }

    @Test
    fun trainedDaysWithoutIntensityStayIncomplete() {
        // La intensidad es obligatoria con días: sin ella no se fabrica evidencia.
        val mapping = requireNotNull(SetupWizardDraft().withTrainedDayToggled(0, now).ringsAnswers).mapping()
        assertEquals(RingsCompletion.INCOMPLETE, mapping.completion)
        assertNull(mapping.evidence)
    }

    @Test
    fun theRouteAsksTheDetailOnlyWhenThereAreTrainedDays() {
        val recent = SetupWizardDraft().withTrainedDayToggled(2, now).stepContext()
        assertTrue(recent.ringsTrainedDays)
        assertTrue(recent.ringsTrainedLast72h)

        val older = SetupWizardDraft().withTrainedDayToggled(3, now).stepContext()
        assertTrue(older.ringsTrainedDays)
        assertFalse(older.ringsTrainedLast72h)
        assertTrue(SetupStepId.RINGS_MAIN_LIFTS in SetupStepGraph.stepIds(older))
        assertFalse(SetupStepId.RINGS_GROUP_SETS in SetupStepGraph.stepIds(older))

        val none = SetupWizardDraft().withNoTraining(now).stepContext()
        assertFalse(none.ringsTrainedDays)
        assertFalse(SetupStepId.RINGS_MAIN_LIFTS in SetupStepGraph.stepIds(none))
        assertFalse(SetupWizardDraft().withTrainingUnknown(now).stepContext().ringsTrainedDays)
    }

    @Test
    fun theFingerprintOfThePreviewChangesWithEveryNewField() {
        val base = SetupWizardDraft()
        val before = base.inputFootprint().ringsSignature
        val changes = listOf(
            base.withTrainedDayToggled(0, now),
            base.withNoTraining(now),
            base.withTrainingUnknown(now),
            base.withMainLiftToggled(RingsLiftPattern.SQUAT, now),
            base.withNoMainLifts(now),
            base.withMainLiftsUnknown(now),
            base.withGroupSets(RingsMuscleGroup.LEGS, 10, now),
            base.withGroupSets(RingsMuscleGroup.LEGS, null, now),
            base.withFeelings(muscular = 2, energy = null, structure = null, nowEpochMs = now),
        )
        for (draft in changes) {
            assertTrue(draft.inputFootprint().ringsSignature != before)
        }
    }

    @Test
    fun theDaysAndTheLiftsBlockUntilAnsweredButTheDetailStepsAreOptional() {
        fun blocking(draft: SetupWizardDraft, step: SetupStepId): Boolean =
            SetupWizardValidation.validateStep(draft, step).any { it.isBlocking }

        assertTrue(blocking(SetupWizardDraft(), SetupStepId.RINGS_SESSION_DAYS))
        assertFalse(blocking(SetupWizardDraft().withNoTraining(now), SetupStepId.RINGS_SESSION_DAYS))
        assertFalse(blocking(SetupWizardDraft().withTrainingUnknown(now), SetupStepId.RINGS_SESSION_DAYS))
        assertFalse(blocking(SetupWizardDraft().withTrainedDayToggled(0, now), SetupStepId.RINGS_SESSION_DAYS))

        assertTrue(blocking(SetupWizardDraft(), SetupStepId.RINGS_MAIN_LIFTS))
        assertFalse(blocking(SetupWizardDraft().withNoMainLifts(now), SetupStepId.RINGS_MAIN_LIFTS))
        assertFalse(blocking(SetupWizardDraft().withMainLiftsUnknown(now), SetupStepId.RINGS_MAIN_LIFTS))

        assertFalse(blocking(SetupWizardDraft(), SetupStepId.RINGS_GROUP_SETS))
        assertFalse(blocking(SetupWizardDraft(), SetupStepId.RINGS_FEELINGS))
        assertFalse(blocking(SetupWizardDraft(), SetupStepId.RINGS_INTRO))
    }
}
