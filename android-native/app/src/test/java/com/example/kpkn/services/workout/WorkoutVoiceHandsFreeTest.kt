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
        assertFalse(WorkoutVoiceAutoConfirmGate.shouldAutoConfirm(incomplete, 0.9f))

        val complete = WorkoutVoiceInterpretation(
            transcript = "ochenta por ocho",
            weightKg = 80.0,
            metricValue = 8,
            fields = setOf(WorkoutVoiceField.WEIGHT, WorkoutVoiceField.VALUE),
        )
        assertTrue(WorkoutVoiceAutoConfirmGate.shouldAutoConfirm(complete, 0.9f))
        assertFalse(WorkoutVoiceAutoConfirmGate.shouldAutoConfirm(complete, 0.2f))
        assertTrue(WorkoutVoiceAutoConfirmGate.shouldAutoConfirm(complete, 0f))
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
    fun sessionGateArmedIsActiveButDoesNotProcessCommands() {
        assertEquals(
            WorkoutVoiceSessionGate.EnableAction.NOOP_ALREADY_ACTIVE,
            WorkoutVoiceSessionGate.enableAction(VoicePipelineStage.ARMED),
        )
        assertFalse(WorkoutVoiceSessionGate.shouldProcessCommand(VoicePipelineStage.ARMED))
        assertFalse(WorkoutVoiceSessionGate.shouldAcceptFinalResult(VoicePipelineStage.ARMED))
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
}
