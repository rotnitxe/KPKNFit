package com.example.kpkn.services.workout

import com.example.kpkn.domain.workout.WarmupEffort
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class WorkoutVoicePreparationParserTest {

    @Test
    fun parsesSkipPreparationWithoutBecomingSkipExercise() {
        listOf(
            "saltar aproximaciones",
            "omitir aproximacion",
            "pasar movilidad",
        ).forEach { phrase ->
            val command = WorkoutVoiceCommandParser.parseCommand(
                transcript = phrase,
                isTimeMode = false,
                isUnilateral = false,
                hasPendingConfirmation = false,
                isRestTimerActive = false,
            )
            assertEquals("Failed for: $phrase", VoiceSessionCommand.SkipPreparation, command)
        }
    }

    @Test
    fun parsesMobilityTimerCommands() {
        assertEquals(
            VoiceSessionCommand.StartMobilityTimer,
            WorkoutVoiceCommandParser.parseCommand("iniciar movilidad", false, false, false, false),
        )
        assertEquals(
            VoiceSessionCommand.PauseMobilityTimer,
            WorkoutVoiceCommandParser.parseCommand("pausar movilidad", false, false, false, false),
        )
        assertEquals(
            VoiceSessionCommand.AdjustMobilityTimer(30),
            WorkoutVoiceCommandParser.parseCommand("+30 segundos movilidad", false, false, false, false),
        )
        assertEquals(
            VoiceSessionCommand.ResetMobilityTimer,
            WorkoutVoiceCommandParser.parseCommand("reiniciar timer movilidad", false, false, false, false),
        )
        assertEquals(
            VoiceSessionCommand.AddComplementaryMobilityVoice,
            WorkoutVoiceCommandParser.parseCommand("agregar movilidad sugerida", false, false, false, false),
        )
    }

    @Test
    fun parsesWarmupCommandsAndReports() {
        assertEquals(
            VoiceSessionCommand.AddWarmupSetVoice,
            WorkoutVoiceCommandParser.parseCommand("agregar serie de aproximacion", false, false, false, false),
        )
        assertEquals(
            VoiceSessionCommand.QueryWarmupSuggestedWeight,
            WorkoutVoiceCommandParser.parseCommand("cuanto peso aproximo", false, false, false, false),
        )

        val reportLight = WorkoutVoiceCommandParser.parseCommand(
            "aproximacion con 60 kilos por 5 reps se sintio muy liviano",
            false, false, false, false,
        )
        assertTrue(reportLight is VoiceSessionCommand.RecordWarmupEffortAndLoad)
        val r1 = reportLight as VoiceSessionCommand.RecordWarmupEffortAndLoad
        assertEquals(60.0, r1.weightKg)
        assertEquals(5, r1.reps)
        assertEquals(WarmupEffort.LIGHT, r1.effort)

        val reportHeavy = WorkoutVoiceCommandParser.parseCommand(
            "calentamiento 80 kg duro",
            false, false, false, false,
        )
        assertTrue(reportHeavy is VoiceSessionCommand.RecordWarmupEffortAndLoad)
        val r2 = reportHeavy as VoiceSessionCommand.RecordWarmupEffortAndLoad
        assertEquals(80.0, r2.weightKg)
        assertEquals(WarmupEffort.HEAVY, r2.effort)

        val targetWorking = WorkoutVoiceCommandParser.parseCommand(
            "mi primera serie es con 100 kilos",
            false, false, false, false,
        )
        assertTrue(targetWorking is VoiceSessionCommand.SetTargetWorkingWeightVoice)
        assertEquals(100.0, (targetWorking as VoiceSessionCommand.SetTargetWorkingWeightVoice).weightKg, 0.001)
    }
}

