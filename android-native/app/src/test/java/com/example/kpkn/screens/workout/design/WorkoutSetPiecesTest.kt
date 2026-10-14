package com.example.kpkn.screens.workout.design

import android.app.Application
import androidx.compose.ui.test.assertIsOff
import androidx.compose.ui.test.assertIsOn
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNodeWithContentDescription
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performScrollTo
import org.junit.Assert.assertEquals
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

/** Comportamiento de las piezas de la serie en vivo (LS1): intensidad, sugerencia de carga y tira de series. */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [34], application = Application::class, qualifiers = "w360dp-h800dp-xxhdpi")
class WorkoutSetPiecesTest {

    @get:Rule
    val rule = createComposeRule()

    @Test
    fun laFilaRirLlamaAOnSelectCon4ComoValorDeMasDeCuatroYAOnToggleFailure() {
        var chosen: Double? = null
        var failureToggles = 0
        rule.setContent {
            WorkoutIntensityRow(
                scale = WorkoutIntensityScale.RIR,
                selected = 2.0,
                failure = false,
                onSelect = { chosen = it },
                onToggleFailure = { failureToggles++ },
            )
        }
        rule.onNodeWithText("2").assertIsOn()
        rule.onNodeWithText("4+").performScrollTo().performClick()
        rule.waitForIdle()
        assertEquals(4.0, chosen ?: -1.0, 0.0)
        rule.onNodeWithText("Fallo").performScrollTo().performClick()
        rule.waitForIdle()
        assertEquals(1, failureToggles)
    }

    @Test
    fun laEscalaRpeMarcaSoloElValorElegido() {
        rule.setContent {
            WorkoutIntensityRow(
                scale = WorkoutIntensityScale.RPE,
                selected = 8.5,
                failure = false,
                onSelect = {},
                onToggleFailure = {},
            )
        }
        rule.onNodeWithText("8,5").assertIsOn()
        rule.onNodeWithText("8").assertIsOff()
    }

    @Test
    fun laSugerenciaSinValorNoPintaNada() {
        rule.setContent {
            WorkoutLoadSuggestion(suggestion = null, applied = false, onUse = {}, reason = "Por tu última sesión")
        }
        rule.onNodeWithText("Usar").assertDoesNotExist()
        rule.onNodeWithText("Por tu última sesión").assertDoesNotExist()
    }

    @Test
    fun usarLlamaAOnUseYSinAplicarMuestraElMotivo() {
        var used = 0
        rule.setContent {
            WorkoutLoadSuggestion(
                suggestion = "82,5 kg",
                applied = false,
                onUse = { used++ },
                reason = "Por tu última sesión",
            )
        }
        rule.onNodeWithText("Sugerida 82,5 kg").assertExists()
        rule.onNodeWithText("Por tu última sesión").assertExists()
        rule.onNodeWithText("Usar").performClick()
        rule.waitForIdle()
        assertEquals(1, used)
    }

    @Test
    fun aplicadaSeLeeEnUsoYNoOfreceUsar() {
        rule.setContent {
            WorkoutLoadSuggestion(suggestion = "82,5 kg", applied = true, onUse = {})
        }
        rule.onNodeWithText("Sugerida 82,5 kg · en uso").assertExists()
        rule.onNodeWithText("Usar").assertDoesNotExist()
    }

    @Test
    fun laTiraLlamaAOnOpenConElIdYAOnAdd() {
        val opened = mutableListOf<String>()
        var added = 0
        val dots = listOf(
            WorkoutSetDot(id = "s1", label = "1", state = WorkoutSetDotState.DONE, caption = "80 × 8"),
            WorkoutSetDot(id = "s2", label = "2", state = WorkoutSetDotState.CURRENT, caption = null),
            WorkoutSetDot(id = "s3", label = "3", state = WorkoutSetDotState.FUTURE, caption = null),
        )
        rule.setContent {
            WorkoutSetStrip(dots = dots, onOpen = { opened += it }, onAdd = { added++ })
        }
        rule.onNodeWithContentDescription("Serie 1, hecha, 80 kilos por 8").performClick()
        rule.onNodeWithContentDescription("Serie 2, la actual").performClick()
        rule.onNodeWithContentDescription("Serie 3, pendiente").performClick()
        rule.onNodeWithContentDescription("Añadir serie").performClick()
        rule.waitForIdle()
        assertEquals(listOf("s1", "s2", "s3"), opened)
        assertEquals(1, added)
    }
}
