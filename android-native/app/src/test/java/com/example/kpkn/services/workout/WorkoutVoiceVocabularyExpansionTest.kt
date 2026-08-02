package com.example.kpkn.services.workout

import com.example.kpkn.screens.workout.extractFirstVoiceNumber
import com.example.kpkn.screens.workout.parseWorkoutVoiceTranscript
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

/** Tests del vocabulario flexible (Fase 2), sugerido (Fase 0) y estructura (Fase 6). */
class WorkoutVoiceVocabularyExpansionTest {

    // ── Fase 0: "sugerido" ──────────────────────────────────────────────────

    @Test
    fun bareSugerido_withRestMeansUseAdaptiveRest() {
        val cmd = WorkoutVoiceCommandParser.parseCommand(
            transcript = "sugerido",
            isTimeMode = false,
            isUnilateral = false,
            hasPendingConfirmation = false,
            isRestTimerActive = true,
        )
        assertEquals(VoiceSessionCommand.UseAdaptiveRest, cmd)
    }

    @Test
    fun bareSugerido_withoutRestMeansSuggestWeight() {
        val cmd = WorkoutVoiceCommandParser.parseCommand(
            transcript = "sugerido",
            isTimeMode = false,
            isUnilateral = false,
            hasPendingConfirmation = false,
            isRestTimerActive = false,
        )
        assertEquals(VoiceSessionCommand.SuggestWeight, cmd)
    }

    // ── Fase 2: conector "equis" ────────────────────────────────────────────

    @Test
    fun equisConnectorParsesCompactSet() {
        val interpretation = parseWorkoutVoiceTranscript(
            transcript = "cincuenta y cuatro equis cinco rir dos",
            isTimeMode = false,
            isUnilateral = false,
        )
        assertTrue(interpretation != null)
        assertEquals(54.0, interpretation!!.weightKg!!, 0.001)
        assertEquals(5, interpretation.metricValue)
        assertEquals(2.0, interpretation.intensityValue!!, 0.001)
        assertEquals(com.example.kpkn.screens.workout.WorkoutVoiceIntensityKind.RIR, interpretation.intensityKind)
    }

    @Test
    fun porConnectorStillWorks() {
        val interpretation = parseWorkoutVoiceTranscript(
            transcript = "cincuenta y cuatro por cinco",
            isTimeMode = false,
            isUnilateral = false,
        )
        assertTrue(interpretation != null)
        assertEquals(54.0, interpretation!!.weightKg!!, 0.001)
        assertEquals(5, interpretation.metricValue)
    }

    // ── Fase 2: RIR verbal ─────────────────────────────────────────────────

    @Test
    fun rirVerbalMeQuedaronEnReserva() {
        val interpretation = parseWorkoutVoiceTranscript(
            transcript = "me quedaron dos en reserva",
            isTimeMode = false,
            isUnilateral = false,
        )
        assertTrue(interpretation != null)
        assertEquals(2.0, interpretation!!.intensityValue!!, 0.001)
        assertEquals(com.example.kpkn.screens.workout.WorkoutVoiceIntensityKind.RIR, interpretation.intensityKind)
    }

    @Test
    fun rirVerbalQuedabanTres() {
        val interpretation = parseWorkoutVoiceTranscript(
            transcript = "quedaban tres",
            isTimeMode = false,
            isUnilateral = false,
        )
        assertTrue(interpretation != null)
        assertEquals(3.0, interpretation!!.intensityValue!!, 0.001)
    }

    @Test
    fun rirVerbalWithinSetRegistration() {
        val interpretation = parseWorkoutVoiceTranscript(
            transcript = "cincuenta y cuatro por cinco me quedaron dos en reserva",
            isTimeMode = false,
            isUnilateral = false,
        )
        assertTrue(interpretation != null)
        assertEquals(54.0, interpretation!!.weightKg!!, 0.001)
        assertEquals(5, interpretation.metricValue)
        assertEquals(2.0, interpretation.intensityValue!!, 0.001)
    }

    // ── Fase 2: fallo verbal (D2a) ─────────────────────────────────────────

    @Test
    fun dandoLoTodoMarksFailure() {
        val interpretation = parseWorkoutVoiceTranscript(
            transcript = "cincuenta por cinco dandolo todo",
            isTimeMode = false,
            isUnilateral = false,
        )
        assertTrue(interpretation != null)
        assertTrue(interpretation!!.reachedFailure)
        assertTrue(com.example.kpkn.screens.workout.WorkoutVoiceField.FAILURE in interpretation.fields)
    }

    @Test
    fun loDiTodoMarksFailure() {
        val interpretation = parseWorkoutVoiceTranscript(
            transcript = "cincuenta por cinco lo di todo",
            isTimeMode = false,
            isUnilateral = false,
        )
        assertTrue(interpretation != null)
        assertTrue(interpretation!!.reachedFailure)
    }

    // ── Fase 2: cansancio alto → RPE 9 sin fallo (D2b) ─────────────────────

    @Test
    fun quedeMuyCansadoIsRpe9NotFailure() {
        val interpretation = parseWorkoutVoiceTranscript(
            transcript = "cincuenta por cinco quede muy cansado",
            isTimeMode = false,
            isUnilateral = false,
        )
        assertTrue(interpretation != null)
        assertEquals(9.0, interpretation!!.intensityValue!!, 0.001)
        assertEquals(com.example.kpkn.screens.workout.WorkoutVoiceIntensityKind.RPE, interpretation.intensityKind)
        assertFalse(interpretation.reachedFailure)
    }

    @Test
    fun standaloneCansadoRemainsFatigueAdvice() {
        val cmd = WorkoutVoiceCommandParser.parseCommand(
            transcript = "estoy cansado",
            isTimeMode = false,
            isUnilateral = false,
            hasPendingConfirmation = false,
            isRestTimerActive = false,
        )
        assertEquals(VoiceSessionCommand.FatigueAdvice, cmd)
    }

    @Test
    fun setWithCansadoNotSwallowedByFatigueAdvice() {
        val cmd = WorkoutVoiceCommandParser.parseCommand(
            transcript = "cincuenta por cinco quede muy cansado",
            isTimeMode = false,
            isUnilateral = false,
            hasPendingConfirmation = false,
            isRestTimerActive = false,
        )
        assertTrue(cmd is VoiceSessionCommand.RegisterSet)
        val interpretation = (cmd as VoiceSessionCommand.RegisterSet).interpretation
        assertEquals(9.0, interpretation.intensityValue!!, 0.001)
        assertFalse(interpretation.reachedFailure)
    }

    // ── Fase 3: extracción de número para clarificación ────────────────────

    @Test
    fun extractFirstVoiceNumberFromWord() {
        assertEquals(20.0, extractFirstVoiceNumber("veinte")!!, 0.001)
    }

    @Test
    fun extractFirstVoiceNumberComposed() {
        assertEquals(54.0, extractFirstVoiceNumber("cincuenta y cuatro")!!, 0.001)
    }

    @Test
    fun extractFirstVoiceNumberFromDigitsInSentence() {
        assertEquals(20.0, extractFirstVoiceNumber("hice 20 repeticiones")!!, 0.001)
    }

    @Test
    fun extractFirstVoiceNumberNullWhenNone() {
        assertNull(extractFirstVoiceNumber("no se"))
    }

    // ── Fase 6: estructura ─────────────────────────────────────────────────

    @Test
    fun moveCurrentExerciseUpAndDown() {
        val up = WorkoutVoiceCommandParser.parseCommand(
            transcript = "sube este ejercicio",
            isTimeMode = false,
            isUnilateral = false,
            hasPendingConfirmation = false,
            isRestTimerActive = false,
        )
        assertEquals(VoiceSessionCommand.MoveCurrentExercise(direction = -1), up)
        val down = WorkoutVoiceCommandParser.parseCommand(
            transcript = "baja este ejercicio",
            isTimeMode = false,
            isUnilateral = false,
            hasPendingConfirmation = false,
            isRestTimerActive = false,
        )
        assertEquals(VoiceSessionCommand.MoveCurrentExercise(direction = 1), down)
    }

    @Test
    fun createAndDissolveSupersetKeywords() {
        assertEquals(
            VoiceSessionCommand.CreateSuperset,
            WorkoutVoiceCommandParser.parseCommand(
                transcript = "crea superserie",
                isTimeMode = false,
                isUnilateral = false,
                hasPendingConfirmation = false,
                isRestTimerActive = false,
            ),
        )
        assertEquals(
            VoiceSessionCommand.DissolveSuperset,
            WorkoutVoiceCommandParser.parseCommand(
                transcript = "disuelve la superserie",
                isTimeMode = false,
                isUnilateral = false,
                hasPendingConfirmation = false,
                isRestTimerActive = false,
            ),
        )
    }

    @Test
    fun editLastSetParsesSide() {
        val cmd = WorkoutVoiceCommandParser.parseEditLastSet(
            "en realidad fue con el lado derecho",
        )
        assertTrue(cmd is VoiceSessionCommand.EditLastSet)
        assertEquals("right", (cmd as VoiceSessionCommand.EditLastSet).patch.side)
    }

    @Test
    fun applySuggestedLoadKeywords() {
        val cmd = WorkoutVoiceCommandParser.parseCommand(
            transcript = "sugerencia aplicada",
            isTimeMode = false,
            isUnilateral = false,
            hasPendingConfirmation = false,
            isRestTimerActive = false,
        )
        assertEquals(VoiceSessionCommand.ApplySuggestedLoad, cmd)
    }

    // ── Fase 5: consultas ──────────────────────────────────────────────────

    @Test
    fun queryCommandsKeywords() {
        assertEquals(
            VoiceSessionCommand.QueryDrainage,
            WorkoutVoiceCommandParser.parseCommand(
                transcript = "cuánto drenaje llevo",
                isTimeMode = false,
                isUnilateral = false,
                hasPendingConfirmation = false,
                isRestTimerActive = false,
            ),
        )
        assertEquals(
            VoiceSessionCommand.QueryCurrentSet,
            WorkoutVoiceCommandParser.parseCommand(
                transcript = "qué serie voy",
                isTimeMode = false,
                isUnilateral = false,
                hasPendingConfirmation = false,
                isRestTimerActive = false,
            ),
        )
        assertEquals(
            VoiceSessionCommand.QueryPendingSide,
            WorkoutVoiceCommandParser.parseCommand(
                transcript = "qué lado falta",
                isTimeMode = false,
                isUnilateral = false,
                hasPendingConfirmation = false,
                isRestTimerActive = false,
            ),
        )
    }
}
