package com.example.kpkn.services.workout

import com.example.kpkn.data.models.UnitModeV2
import com.example.kpkn.screens.workout.WorkoutVoiceField
import com.example.kpkn.screens.workout.WorkoutVoiceInterpretation
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class WorkoutVoiceConfirmationCorrectionsTest {

    private val draft = WorkoutVoiceInterpretation(
        transcript = "sesenta kilos seis repeticiones rir dos",
        weightKg = 60.0,
        metricValue = 6,
        metricDecimalValue = 6.0,
        intensityValue = 2.0,
        intensityKind = com.example.kpkn.screens.workout.WorkoutVoiceIntensityKind.RIR,
        fields = setOf(
            WorkoutVoiceField.WEIGHT,
            WorkoutVoiceField.VALUE,
            WorkoutVoiceField.INTENSITY,
        ),
    )

    private fun build(text: String) = WorkoutVoiceConfirmationCorrections.buildCorrection(
        draft = draft,
        text = text,
        isTimeMode = false,
        isUnilateral = false,
        unitMode = UnitModeV2.REPS,
        customUnit = null,
        trackRom = false,
    )

    private fun buildAndMerge(text: String) = build(text)?.let {
        WorkoutVoiceConfirmationCorrections.mergeCorrection(draft, it)
    }

    @Test
    fun decimalCorrectionTreatsAsWeightAndKeepsDraftReps() {
        val merged = buildAndMerge("no, era 47.5")!!
        assertEquals(47.5, merged.weightKg ?: -1.0, 0.0)
        assertEquals(6, merged.metricValue)
        assertEquals(2.0, merged.intensityValue ?: -1.0, 0.0)
        assertTrue(WorkoutVoiceField.WEIGHT in merged.fields)
    }

    @Test
    fun kilosCorrectionReplacesWeightAndKeepsDraftReps() {
        val merged = buildAndMerge("no era cuarenta y siete kilos")!!
        assertEquals(47.0, merged.weightKg ?: -1.0, 0.0)
        assertEquals(6, merged.metricValue)
    }

    @Test
    fun eranCorrectionIsRepsAndKeepsDraftWeight() {
        val merged = buildAndMerge("no, eran ocho")!!
        assertEquals(8, merged.metricValue)
        assertEquals(60.0, merged.weightKg ?: -1.0, 0.0)
    }

    @Test
    fun bareIntegerWithoutCueCorrectionsWeight() {
        val merged = buildAndMerge("no, setenta y siete")!!
        assertEquals(77.0, merged.weightKg ?: -1.0, 0.0)
        assertEquals(6, merged.metricValue)
    }

    @Test
    fun fullRedictationReplacesBothFields() {
        val merged = buildAndMerge("no, 82 por 8")!!
        assertEquals(82.0, merged.weightKg ?: -1.0, 0.0)
        assertEquals(8, merged.metricValue)
    }

    @Test
    fun rangeEditProducesAbsoluteWeight() {
        val correction = build("de 101.3 a 123.8 kilos") ?: throw AssertionError("correction null")
        assertEquals(123.8, correction.weightKg ?: -1.0, 0.0)
    }

    @Test
    fun plainNoProducesNullCorrection() {
        assertNull(build("no"))
    }

    @Test
    fun editLastSetDecimalWeightDoesNotStealIntegerPartAsReps() {
        val cmd = WorkoutVoiceCommandParser.parseEditLastSet("era 47.5 kilos")
        assertEquals(47.5, cmd?.patch?.weightKg ?: -1.0, 0.0)
        assertNull(cmd?.patch?.metricValue)
    }

    @Test
    fun editLastSetEranNueveIsReps() {
        val cmd = WorkoutVoiceCommandParser.parseEditLastSet("eran nueve")
        assertEquals(9, cmd?.patch?.metricValue)
    }

    @Test
    fun editLastSetEraCienKilosIsWeight() {
        val cmd = WorkoutVoiceCommandParser.parseEditLastSet("era cien kilos")
        assertEquals(100.0, cmd?.patch?.weightKg ?: -1.0, 0.0)
    }

    @Test
    fun sigueEsSkipRestSoloConTimerYNoRobadoPorSubstringDeSiguiente() {
        val sigue = WorkoutVoiceCommandParser.parseCommand(
            transcript = "sigue",
            isTimeMode = false,
            isUnilateral = false,
            hasPendingConfirmation = false,
            isRestTimerActive = true,
        )
        val siguienteSinTimer = WorkoutVoiceCommandParser.parseCommand(
            transcript = "siguiente",
            isTimeMode = false,
            isUnilateral = false,
            hasPendingConfirmation = false,
            isRestTimerActive = false,
        )
        assertEquals(VoiceSessionCommand.SkipRest, sigue)
        assertEquals(VoiceSessionCommand.SkipExercise, siguienteSinTimer)
    }

    @Test
    fun palabrasInyectadasNoDisparanSkipRest() {
        val cmd = WorkoutVoiceCommandParser.parseCommand(
            transcript = "lado derecho",
            isTimeMode = false,
            isUnilateral = false,
            hasPendingConfirmation = false,
            isRestTimerActive = true,
        )
        assertTrue(cmd !is VoiceSessionCommand.SkipRest)
    }
}
