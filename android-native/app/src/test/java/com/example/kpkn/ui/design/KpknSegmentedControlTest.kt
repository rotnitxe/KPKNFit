package com.example.kpkn.ui.design

import android.app.Application
import androidx.compose.ui.test.assertIsNotEnabled
import androidx.compose.ui.test.assertIsNotSelected
import androidx.compose.ui.test.assertIsSelected
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import org.junit.Assert.assertEquals
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

/** El control segmentado compartido: sin ninguno elegido (índice negativo), con el índice válido de siempre, y apagado (enabled = false). */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [34], application = Application::class)
class KpknSegmentedControlTest {

    @get:Rule
    val rule = createComposeRule()

    @Test
    fun conIndiceNegativoNingunSegmentoEstaElegido() {
        rule.setContent {
            KpknSegmentedControl(options = listOf("Uno", "Dos"), selectedIndex = -1, onSelect = {})
        }
        rule.onNodeWithText("Uno").assertIsNotSelected()
        rule.onNodeWithText("Dos").assertIsNotSelected()
    }

    @Test
    fun elIndiceValidoSigueEligiendoSuSegmento() {
        rule.setContent {
            KpknSegmentedControl(options = listOf("Uno", "Dos"), selectedIndex = 1, onSelect = {})
        }
        rule.onNodeWithText("Dos").assertIsSelected()
        rule.onNodeWithText("Uno").assertIsNotSelected()
    }

    @Test
    fun apagadoNoRespondeAlToque() {
        var picked = -1
        rule.setContent {
            KpknSegmentedControl(
                options = listOf("Uno", "Dos"),
                selectedIndex = 0,
                onSelect = { picked = it },
                enabled = false,
            )
        }
        rule.onNodeWithText("Dos").assertIsNotEnabled().performClick()
        assertEquals(-1, picked)
    }
}
