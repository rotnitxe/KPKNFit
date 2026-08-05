package com.example.kpkn.services.workout

import com.example.kpkn.data.models.VoiceVerbosity
import com.example.kpkn.screens.workout.WorkoutVoiceField
import com.example.kpkn.screens.workout.WorkoutVoiceInterpretation
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class WorkoutVoiceHandsFreeTest {

    @Test
    fun restAwareSaltarMeansSkipRestNotExercise() {
        val cmd = WorkoutVoiceCommandParser.parseCommand(
            transcript = "saltar",
            isTimeMode = false,
            isUnilateral = false,
            hasPendingConfirmation = false,
            isRestTimerActive = true,
        )
        assertEquals(VoiceSessionCommand.SkipRest, cmd)
    }

    @Test
    fun withoutRestSaltarMeansSkipExercise() {
        val cmd = WorkoutVoiceCommandParser.parseCommand(
            transcript = "saltar",
            isTimeMode = false,
            isUnilateral = false,
            hasPendingConfirmation = false,
            isRestTimerActive = false,
        )
        assertEquals(VoiceSessionCommand.SkipExercise, cmd)
    }

    @Test
    fun parseUseAdaptiveRestKeywords() {
        val cmd = WorkoutVoiceCommandParser.parseCommand(
            transcript = "usar sugerido",
            isTimeMode = false,
            isUnilateral = false,
            hasPendingConfirmation = false,
            isRestTimerActive = true,
        )
        assertEquals(VoiceSessionCommand.UseAdaptiveRest, cmd)
    }

    @Test
    fun parseAdjustRestAddThirty() {
        val cmd = WorkoutVoiceCommandParser.parseAdjustRestTime("añade 30 segundos")
        assertEquals(VoiceSessionCommand.AdjustRestTime(30), cmd)
    }

    @Test
    fun parseAdjustRestRemoveFifteenSpoken() {
        val cmd = WorkoutVoiceCommandParser.parseAdjustRestTime("quita quince")
        assertEquals(VoiceSessionCommand.AdjustRestTime(-15), cmd)
    }

    @Test
    fun parseUndoDuringRest() {
        val cmd = WorkoutVoiceCommandParser.parseCommand(
            transcript = "corregir",
            isTimeMode = false,
            isUnilateral = false,
            hasPendingConfirmation = false,
            isRestTimerActive = true,
        )
        assertEquals(VoiceSessionCommand.UndoLastSet, cmd)
    }

    @Test
    fun hypothesisScorerPrefersGymSignalWords() {
        val best = WorkoutVoiceHypothesisScorer.pickBest(
            listOf(
                VoiceHypothesis("quiero comer pan", 0.9f),
                VoiceHypothesis("ochenta kilos por ocho", 0.4f),
            ),
        )
        assertEquals("ochenta kilos por ocho", best?.text)
    }

    @Test
    fun autoConfirmRequiresWeightAndReps() {
        val incomplete = WorkoutVoiceInterpretation(
            transcript = "ochenta kilos",
            weightKg = 80.0,
            fields = setOf(WorkoutVoiceField.WEIGHT),
        )
        assertFalse(WorkoutVoiceConfirmationPolicy.shouldAutoConfirm(incomplete, 0.9f))

        val complete = WorkoutVoiceInterpretation(
            transcript = "ochenta por ocho",
            weightKg = 80.0,
            metricValue = 8,
            fields = setOf(WorkoutVoiceField.WEIGHT, WorkoutVoiceField.VALUE),
        )
        assertTrue(WorkoutVoiceConfirmationPolicy.shouldAutoConfirm(complete, 0.9f))
        assertFalse(WorkoutVoiceConfirmationPolicy.shouldAutoConfirm(complete, 0.2f))
        assertTrue(WorkoutVoiceConfirmationPolicy.shouldAutoConfirm(complete, 0f))
    }

    @Test
    fun verbosityGateSilentBlocksCompleteCues() {
        assertTrue(
            WorkoutVoiceVerbosityGate.allows(VoiceVerbosity.SILENT, VoiceAnnouncementKind.CRITICAL),
        )
        assertFalse(
            WorkoutVoiceVerbosityGate.allows(VoiceVerbosity.SILENT, VoiceAnnouncementKind.ESSENTIAL),
        )
        assertFalse(
            WorkoutVoiceVerbosityGate.allows(VoiceVerbosity.SILENT, VoiceAnnouncementKind.COMPLETE),
        )
        assertTrue(
            WorkoutVoiceVerbosityGate.allows(VoiceVerbosity.ESSENTIAL, VoiceAnnouncementKind.ESSENTIAL),
        )
        assertFalse(
            WorkoutVoiceVerbosityGate.allows(VoiceVerbosity.ESSENTIAL, VoiceAnnouncementKind.COMPLETE),
        )
    }

    @Test
    fun instantPartialConfirmAndSkipRest() {
        assertEquals(
            VoiceSessionCommand.Confirm,
            WorkoutVoiceInstantCommands.match("sí", confirmWait = true, restActive = false),
        )
        assertEquals(
            VoiceSessionCommand.SkipRest,
            WorkoutVoiceInstantCommands.match("listo", confirmWait = false, restActive = true),
        )
        assertNull(
            WorkoutVoiceInstantCommands.match("ochenta por ocho", confirmWait = false, restActive = false),
        )
    }

    @Test
    fun engineErrorBackoffIsExponential() {
        assertEquals(400L, WorkoutVoiceSessionGate.engineErrorBackoffMs(1))
        assertEquals(800L, WorkoutVoiceSessionGate.engineErrorBackoffMs(2))
        assertEquals(1600L, WorkoutVoiceSessionGate.engineErrorBackoffMs(3))
        assertEquals(1600L, WorkoutVoiceSessionGate.engineErrorBackoffMs(5))
    }

    @Test
    fun listoDuringRestIsSkipRestNotUnknown() {
        val cmd = WorkoutVoiceCommandParser.parseCommand(
            transcript = "listo",
            isTimeMode = false,
            isUnilateral = false,
            hasPendingConfirmation = false,
            isRestTimerActive = true,
        )
        assertEquals(VoiceSessionCommand.SkipRest, cmd)
    }

    @Test
    fun saltarDescansoPhrase() {
        assertNotNull(
            WorkoutVoiceCommandParser.parseRestAwareCommand("saltar descanso"),
        )
        assertEquals(
            VoiceSessionCommand.SkipRest,
            WorkoutVoiceCommandParser.parseRestAwareCommand("saltar descanso"),
        )
    }

    @Test
    fun parseEditLastSetAbsoluteWeight() {
        val cmd = WorkoutVoiceCommandParser.parseEditLastSet("cámbialo a 82 kilos")
        assertNotNull(cmd)
        assertEquals(82.0, cmd!!.patch.weightKg!!, 0.01)
    }

    @Test
    fun parseEditLastSetReps() {
        val cmd = WorkoutVoiceCommandParser.parseEditLastSet("eran nueve reps")
        assertNotNull(cmd)
        assertEquals(9, cmd!!.patch.metricValue)
    }

    @Test
    fun parseEditLastSetMultiFieldNaturalPhrase() {
        val cmd = WorkoutVoiceCommandParser.parseEditLastSet("cámbialo a 82 por 9 rpe 8")
        assertNotNull(cmd)
        assertEquals(82.0, cmd!!.patch.weightKg!!, 0.01)
        assertEquals(9, cmd.patch.metricValue)
        assertEquals(8.0, cmd.patch.intensityValue!!, 0.01)
        assertEquals(
            com.example.kpkn.screens.workout.WorkoutVoiceIntensityKind.RPE,
            cmd.patch.intensityKind,
        )
    }

    @Test
    fun speechBusHigherPriorityInterruptsLower() {
        val bus = WorkoutSpeechBus()
        var interrupted = false
        assertTrue(bus.tryAcquire(WorkoutSpeechPriority.LOW) {})
        assertTrue(bus.tryAcquire(WorkoutSpeechPriority.HIGH) { interrupted = true })
        assertTrue(interrupted)
        bus.clear()
        assertTrue(bus.tryAcquire(WorkoutSpeechPriority.HIGH) {})
        assertFalse(bus.tryAcquire(WorkoutSpeechPriority.LOW) {})
    }

    @Test
    fun enteringCueRespectsShowPrsFlag() {
        assertNull(
            WorkoutVoiceEnteringCue.rangeHint(100.0, 120.0, sampleCount = 5, showPRsInWorkout = false),
        )
        assertEquals(
            "Rango eRM 100 kilos a 120 kilos.",
            WorkoutVoiceEnteringCue.rangeHint(100.0, 120.0, sampleCount = 5, showPRsInWorkout = true),
        )
    }

    @Test
    fun sessionGateRecoveringIsActiveButDoesNotProcessCommands() {
        assertEquals(
            WorkoutVoiceSessionGate.EnableAction.NOOP_ALREADY_ACTIVE,
            WorkoutVoiceSessionGate.enableAction(VoicePipelineStage.RECOVERING),
        )
        assertFalse(WorkoutVoiceSessionGate.shouldProcessCommand(VoicePipelineStage.RECOVERING))
        assertFalse(WorkoutVoiceSessionGate.shouldAcceptFinalResult(VoicePipelineStage.RECOVERING))
    }

    @Test
    fun confirmationPolicyAutoVsAsk() {
        val complete = WorkoutVoiceInterpretation(
            transcript = "80 por 8",
            weightKg = 80.0,
            metricValue = 8,
            fields = setOf(WorkoutVoiceField.WEIGHT, WorkoutVoiceField.VALUE),
        )
        assertEquals(
            ConfirmationDecision.AUTO,
            WorkoutVoiceConfirmationPolicy.decide(complete, 0.9f),
        )
        assertEquals(
            ConfirmationDecision.ASK,
            WorkoutVoiceConfirmationPolicy.decide(complete, 0.2f),
        )
    }

    @Test
    fun intentMatcherConfirmWaitRejectsSkipExercise() {
        val cmd = WorkoutVoiceIntentMatcher.match(
            transcript = "saltar",
            stage = VoicePipelineStage.CONFIRM_WAIT,
            isTimeMode = false,
            isUnilateral = false,
            isRestTimerActive = false,
            showPostExerciseSheet = false,
            showFinishSheet = false,
        )
        assertTrue(cmd is VoiceSessionCommand.Unknown)
    }

    @Test
    fun intentMatcherRestMapsSaltarToSkipRest() {
        val cmd = WorkoutVoiceIntentMatcher.match(
            transcript = "saltar",
            stage = VoicePipelineStage.LISTENING,
            isTimeMode = false,
            isUnilateral = false,
            isRestTimerActive = true,
            showPostExerciseSheet = false,
            showFinishSheet = false,
        )
        assertEquals(VoiceSessionCommand.SkipRest, cmd)
    }

    @Test
    fun stopSpeakingKeyword() {
        val cmd = WorkoutVoiceCommandParser.parseCommand(
            transcript = "para",
            isTimeMode = false,
            isUnilateral = false,
            hasPendingConfirmation = false,
            isRestTimerActive = false,
        )
        assertEquals(VoiceSessionCommand.StopSpeaking, cmd)
    }

    @Test
    fun restAwareSigueAndParaMapToSkipRest() {
        val sigue = WorkoutVoiceCommandParser.parseCommand(
            transcript = "sigue",
            isTimeMode = false,
            isUnilateral = false,
            hasPendingConfirmation = false,
            isRestTimerActive = true,
        )
        val para = WorkoutVoiceCommandParser.parseCommand(
            transcript = "para",
            isTimeMode = false,
            isUnilateral = false,
            hasPendingConfirmation = false,
            isRestTimerActive = true,
        )
        assertEquals(VoiceSessionCommand.SkipRest, sigue)
        assertEquals(VoiceSessionCommand.SkipRest, para)
    }

    @Test
    fun bareSuggestedSynonymsMapToSuggestWeight() {
        for (word in listOf("sugerido", "sugerida")) {
            val cmd = WorkoutVoiceCommandParser.parseCommand(
                transcript = word,
                isTimeMode = false,
                isUnilateral = false,
                hasPendingConfirmation = false,
                isRestTimerActive = false,
            )
            assertEquals(word, VoiceSessionCommand.SuggestWeight, cmd)
        }
    }

    @Test
    fun rangeEditMapsToEditLastSetAbsoluteWeight() {
        val cmd = WorkoutVoiceCommandParser.parseEditLastSet("de 101.3 a 123.8 kilos")
        assertNotNull(cmd)
        assertEquals(123.8, cmd?.patch?.weightKg ?: 0.0, 0.0)
    }

    @Test
    fun confirmationPolicyAsksWhenWeightDeviatesFromContext() {
        val outOfContext = WorkoutVoiceInterpretation(
            transcript = "sesenta kilos",
            weightKg = 60.0,
            metricValue = 8,
            fields = setOf(WorkoutVoiceField.WEIGHT, WorkoutVoiceField.VALUE),
        )
        val plausible = WorkoutVoiceInterpretation(
            transcript = "80 por 8",
            weightKg = 82.0,
            metricValue = 8,
            fields = setOf(WorkoutVoiceField.WEIGHT, WorkoutVoiceField.VALUE),
        )
        assertEquals(
            ConfirmationDecision.ASK,
            WorkoutVoiceConfirmationPolicy.decide(outOfContext, 0.9f, suggestedWeight = 20.0),
        )
        assertEquals(
            ConfirmationDecision.AUTO,
            WorkoutVoiceConfirmationPolicy.decide(plausible, 0.9f, suggestedWeight = 80.0),
        )
    }

    @Test
    fun confirmationPolicyAcceptsBodyweightMetricAndFeedbackOnDraft() {
        val bodyweight = WorkoutVoiceInterpretation(
            transcript = "12 repeticiones",
            metricValue = 12,
            fields = setOf(WorkoutVoiceField.VALUE),
        )
        val failure = WorkoutVoiceInterpretation(
            transcript = "fallo",
            reachedFailure = true,
            fields = setOf(WorkoutVoiceField.FAILURE),
        )
        assertEquals(
            ConfirmationDecision.AUTO,
            WorkoutVoiceConfirmationPolicy.decide(bodyweight, 0.9f, requiresWeight = false),
        )
        assertEquals(
            ConfirmationDecision.AUTO,
            WorkoutVoiceConfirmationPolicy.decide(failure, 0.9f, draftHasWeightAndReps = true),
        )
    }

    @Test
    fun timedSetCommandsAreContextual() {
        val start = WorkoutVoiceCommandParser.parseCommand("iniciar", true, false, false, false)
        val stopTimed = WorkoutVoiceCommandParser.parseCommand("para", true, false, false, false)
        val stopSpeech = WorkoutVoiceCommandParser.parseCommand("para", false, false, false, false)

        assertTrue(start is VoiceSessionCommand.StartTimedSet)
        assertTrue(stopTimed is VoiceSessionCommand.StopTimedSet)
        assertTrue(stopSpeech is VoiceSessionCommand.StopSpeaking)
    }

    @Test
    fun parsesSessionTimeLimitAsSessionOnlyByDefault() {
        val temporary = WorkoutVoiceCommandParser.parseCommand("maximo 90 minutos", false, false, false, false)
        val permanent = WorkoutVoiceCommandParser.parseCommand(
            "maximo 90 minutos guardar este limite en el programa", false, false, false, false,
        )

        assertEquals(VoiceSessionCommand.SetSessionTimeLimit(90, false), temporary)
        assertEquals(VoiceSessionCommand.SetSessionTimeLimit(90, true), permanent)
    }

    @Test
    fun explicitTagCommandIsParsedWithoutGuessingNormalSpeech() {
        val tag = WorkoutVoiceCommandParser.parseCommand(
            transcript = "etiqueta agarre neutro", isTimeMode = false, isUnilateral = false,
            hasPendingConfirmation = false, isRestTimerActive = false, tagNames = setOf("Agarre neutro"),
        )
        val ordinary = WorkoutVoiceCommandParser.parseCommand(
            transcript = "agarre neutro", isTimeMode = false, isUnilateral = false,
            hasPendingConfirmation = false, isRestTimerActive = false, tagNames = setOf("Agarre neutro"),
        )
        assertEquals(VoiceSessionCommand.ApplyTag("Agarre neutro"), tag)
        assertTrue(ordinary is VoiceSessionCommand.Unknown)
    }
}
