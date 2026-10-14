package com.example.kpkn.screens.workout.components

import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.test.click
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNodeWithContentDescription
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performTouchInput
import com.example.kpkn.data.models.VoiceCaptureMode
import com.example.kpkn.domain.auge.DiscomfortSuggestionEngine
import org.junit.Assert.assertEquals
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

/**
 * Pruebas del CUERPO de la preparación («¿Cómo llegas a entrenar hoy?») montado suelto: la hoja de [KpknSheet] no entrega toques en
 * Robolectric. Cubren los cuatro gestos que llevan datos a la pantalla de la sesión: la regla de Energía, una molestia, el interruptor
 * de voz y «Empezar».
 */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [34], qualifiers = "w360dp-h2400dp-xxhdpi", application = android.app.Application::class)
class WorkoutReadinessContentTest {

    @get:Rule
    val rule = createComposeRule()

    private var energy: Int? = null
    private var discomforts: List<String>? = null
    private var voiceToggles = 0
    private var starts = 0

    private fun show(selected: List<String> = emptyList(), voiceOn: Boolean = false) {
        rule.setContent {
            WorkoutReadinessBody(
                neural = 60,
                muscular = WorkoutReadinessSamples.muscles.values.average().toInt(),
                spinal = 72,
                muscleAdjustments = WorkoutReadinessSamples.muscles,
                selectedDiscomforts = selected,
                discomfortPillarIds = WorkoutReadinessSamples.pillars,
                advice = ReadinessAdvice.None,
                voiceSessionEnabled = voiceOn,
                voiceCaptureMode = VoiceCaptureMode.HANDS_FREE,
                onNeuralChange = { energy = it },
                onSpinalChange = {},
                onMuscleChange = { _, _ -> },
                onDiscomfortsChanged = { discomforts = it },
                onVoiceToggle = { voiceToggles += 1 },
                onVoiceCaptureModeChange = {},
                onStart = { starts += 1 },
            )
        }
        rule.waitForIdle()
    }

    @Test
    fun ajustarEnergiaLlamaConElValor() {
        show()
        // Un toque al 60 % de la regla de Energía: el valor llega tal cual.
        rule.onNodeWithContentDescription("Energía")
            .performTouchInput { click(Offset(width * 0.6f, height / 2f)) }
        rule.waitForIdle()
        assertEquals(60, energy)
    }

    @Test
    fun marcarUnaMolestiaLlamaConElla() {
        show()
        val first = DiscomfortSuggestionEngine.suggestForMuscles(WorkoutReadinessSamples.pillars).first()
        // El bloque de molestias está plegado: primero se despliega y luego se marca la primera sugerida.
        rule.onNodeWithText("MOLESTIAS PREVIAS").performClick()
        rule.waitForIdle()
        rule.onNodeWithText(first.label).performClick()
        rule.waitForIdle()
        assertEquals(listOf(first.id), discomforts)
    }

    @Test
    fun elInterruptorDeVozLlamaAlCambiar() {
        show()
        rule.onNodeWithContentDescription("Voz durante la sesión").performClick()
        rule.waitForIdle()
        assertEquals(1, voiceToggles)
    }

    @Test
    fun empezarSeDescribeComoConfirmarYLlamaAEmpezar() {
        show(voiceOn = true)
        rule.onNodeWithText("Empezar").assertExists()
        rule.onNodeWithContentDescription("Confirmar y entrenar").performClick()
        rule.waitForIdle()
        assertEquals(1, starts)
    }
}
