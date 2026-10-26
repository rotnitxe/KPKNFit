package com.example.kpkn.services.workout

import com.example.kpkn.screens.workout.WorkoutVoiceIntensityKind
import org.junit.Assert.*
import org.junit.Test

class WorkoutVoiceSpokenCorrectionRegressionTest {
    @Test fun spokenDecimalCorrectionDoesNotSilentlyTruncateTheLoad() {
        val command = WorkoutVoiceCommandParser.parseEditLastSet(
            "cámbialo a veintidós coma cinco kilos nueve repeticiones rpe siete",
        )!!
        assertEquals(22.5, command.patch.weightKg!!, 0.0)
        assertEquals(9, command.patch.metricValue)
        assertEquals(7.0, command.patch.intensityValue!!, 0.0)
        assertEquals(WorkoutVoiceIntensityKind.RPE, command.patch.intensityKind)
    }

    @Test fun spokenEffortInACorrectionIsAnActualChangedField() {
        val command = WorkoutVoiceCommandParser.parseEditLastSet("cámbialo a veinte por nueve rir tres")!!
        assertEquals(3.0, command.patch.intensityValue!!, 0.0)
        assertEquals(WorkoutVoiceIntensityKind.RIR, command.patch.intensityKind)
    }

    @Test fun compoundTensHalfKilogramsAndDecimalZerosArePreserved() {
        listOf("treinta y dos y medio" to 32.5, "ciento veintidós coma cinco" to 122.5,
            "veinte coma cero cinco" to 20.05, "22.5" to 22.5).forEach { (spoken, expected) ->
            val command = WorkoutVoiceCommandParser.parseEditLastSet("cámbialo a $spoken kilos")!!
            assertEquals(spoken, expected, command.patch.weightKg!!, 0.0)
        }
    }

    @Test fun exactNativeRecognizerTranscriptRetainsTheEffortCorrection() {
        val command = WorkoutVoiceCommandParser.parseCommand(
            "cambia a veintidos coma cinco kilos nueve repeticiones erre pe e siete",
            isTimeMode = false, isUnilateral = false, hasPendingConfirmation = false, isRestTimerActive = false,
        ) as VoiceSessionCommand.EditLastSet
        assertEquals(22.5, command.patch.weightKg!!, 0.0)
        assertEquals(7.0, command.patch.intensityValue!!, 0.0)
        assertEquals(WorkoutVoiceIntensityKind.RPE, command.patch.intensityKind)
    }

    @Test fun vocabularyEffortAliasesAlsoWorkInsideCorrections() {
        listOf("esfuerzo siete", "intensidad siete").forEach { effort ->
            val command = WorkoutVoiceCommandParser.parseEditLastSet("cambia a veinte por nueve $effort")!!
            assertEquals(effort, 7.0, command.patch.intensityValue!!, 0.0)
        }
        val reserve = WorkoutVoiceCommandParser.parseEditLastSet("cambia a veinte reservas ocho")!!
        assertEquals(8.0, reserve.patch.intensityValue!!, 0.0)
        assertEquals(WorkoutVoiceIntensityKind.RIR, reserve.patch.intensityKind)
    }

    @Test fun actualNumericReservesMustNeverBeRewrittenAsTwo() {
        listOf("ocho", "diez", "doce").forEach { actual ->
            assertEquals("rir $actual", WorkoutVoiceMishearingCorrections.correctDeterministic("rir $actual"))
        }
    }

    @Test fun spelledScalesAreCanonicalizedWithoutChangingTheirNumbers() {
        listOf("erre pe e siete", "erre pe siete", "r p e siete").forEach {
            assertEquals("rpe siete", WorkoutVoiceMishearingCorrections.correctDeterministic(it))
        }
        assertEquals("rir ocho", WorkoutVoiceMishearingCorrections.correctDeterministic("erre i erre ocho"))
    }

    @Test fun voiceReservePolicyMatchesTheRecordersAcceptedActualReports() {
        listOf(0.0, 5.0, 8.0, 10.0, 12.0, 30.0).forEach { value ->
            assertTrue(com.example.kpkn.screens.workout.isValidReportedReserve(value))
            assertNull(com.example.kpkn.screens.workout.recordingEffortError(value,
                com.example.kpkn.screens.workout.SetAdvancedFeedback(
                    actualIntensityMode = com.example.kpkn.data.models.IntensityMode.RIR,
                    actualIntensityValue = value, intensityAdjusted = true)))
        }
        listOf(null, -1.0, Double.NaN, Double.POSITIVE_INFINITY).forEach {
            assertFalse(com.example.kpkn.screens.workout.isValidReportedReserve(it))
        }
    }
}
