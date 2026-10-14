package com.example.kpkn.screens.workout.design

import android.app.Application
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNodeWithContentDescription
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import org.junit.Assert.assertEquals
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

/** El panel de la serie (KPKN LS2b): cifras con su paso, sugerencia, «Opciones avanzadas» y «Registrar serie». */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [34], application = Application::class, qualifiers = "w360dp-h800dp-xxhdpi")
class WorkoutSetPanelTest {

    @get:Rule
    val rule = createComposeRule()

    /** Anfitrión con estado para «Opciones avanzadas»: el panel no guarda nada. */
    @Composable
    private fun PanelHost(
        timeMode: Boolean = false,
        valueText: String = "8",
        onLoadStep: (Double) -> Unit = {},
        onValueStep: (Double) -> Unit = {},
        onUseSuggestion: () -> Unit = {},
    ) {
        var advancedOpen by remember { mutableStateOf(false) }
        WorkoutSetPanel(
            eyebrow = "Serie 2 de 4",
            summary = "Serie 2 de 4, 80 kilos, 8 repeticiones, RIR 2",
            loadValue = "80",
            loadStep = 2.5,
            onLoadStep = onLoadStep,
            onLoadCommit = {},
            valueLabel = if (timeMode) "TIEMPO" else "REPS",
            valueText = valueText,
            valueStep = if (timeMode) 5.0 else 1.0,
            onValueStep = onValueStep,
            onValueCommit = {},
            timeMode = timeMode,
            intensityScale = WorkoutIntensityScale.RIR,
            intensitySelected = 2.0,
            intensityFailure = false,
            onIntensitySelect = {},
            onToggleFailure = {},
            suggestion = "82,5 kg",
            suggestionApplied = false,
            suggestionReason = "Por tu última sesión",
            onUseSuggestion = onUseSuggestion,
            modeLabels = null,
            modeSelected = 0,
            onModeSelected = {},
            advancedOpen = advancedOpen,
            onToggleAdvanced = { advancedOpen = !advancedOpen },
            advanced = { Text("Contenido avanzado") },
        )
    }

    @Test
    fun elPasoDeKgSaleDelMaterialAlSumar() {
        var step: Double? = null
        rule.setContent { PanelHost(onLoadStep = { step = it }) }

        rule.onNodeWithContentDescription("Sumar kilos").performClick()

        assertEquals(2.5, step ?: Double.NaN, 0.001)
    }

    @Test
    fun usarAplicaLaSugerencia() {
        var used = false
        rule.setContent { PanelHost(onUseSuggestion = { used = true }) }

        rule.onNodeWithContentDescription("Usar 82,5 kg").performClick()

        assertEquals(true, used)
    }

    @Test
    fun enTiempoSeVeCeroCuarentaYCincoYMueveCincoSegundos() {
        var step: Double? = null
        rule.setContent { PanelHost(timeMode = true, valueText = "0:45", onValueStep = { step = it }) }

        rule.onNodeWithText("0:45").assertExists()
        rule.onNodeWithContentDescription("Sumar tiempo").performClick()

        assertEquals(5.0, step ?: Double.NaN, 0.001)
    }

    @Test
    fun opcionesAvanzadasDespliegaYPliega() {
        rule.setContent { PanelHost() }

        rule.onNodeWithText("Contenido avanzado").assertDoesNotExist()
        rule.onNodeWithText("Opciones avanzadas").performClick()
        rule.waitForIdle()
        rule.onNodeWithText("Contenido avanzado").assertExists()
        rule.onNodeWithText("Opciones avanzadas").performClick()
        rule.waitForIdle()
        rule.onNodeWithText("Contenido avanzado").assertDoesNotExist()
    }

    @Test
    fun laPildoraLlamaALaAccionUnaSolaVezPorToqueYDiceActualizarEnCorreccion() {
        var calls = 0
        rule.setContent {
            WorkoutRecordPill(label = "Actualizar serie", enabled = true, onRecord = { calls++ })
        }

        rule.onNodeWithContentDescription("Actualizar serie").performClick()
        rule.waitForIdle()

        assertEquals(1, calls)
    }
}
