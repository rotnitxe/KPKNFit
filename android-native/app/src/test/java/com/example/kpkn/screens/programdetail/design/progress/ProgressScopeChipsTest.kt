package com.example.kpkn.screens.programdetail.design.progress

import android.app.Application
import androidx.compose.material3.MaterialTheme
import androidx.compose.ui.test.assertCountEquals
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onAllNodesWithText
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import org.junit.Assert.assertEquals
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

/**
 * El selector de alcance de Progreso en Robolectric: «Programa» siempre; «Bloque 2» sale con su etiqueta y, sin semana, ninguna píldora
 * dice «Semana»; tocar un alcance lo entrega a la acción.
 */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [34], application = Application::class)
class ProgressScopeChipsTest {

    @get:Rule
    val composeRule = createComposeRule()

    /** Con «Bloque 2» y sin semana salen «Programa» y «Bloque 2», y ninguna píldora dice «Semana». */
    @Test
    fun saleProgramaYBloqueYNingunaSemanaCuandoNoHaySemana() {
        composeRule.setContent {
            MaterialTheme {
                ProgressScopeChips(
                    selected = ProgressScope.PROGRAM,
                    blockLabel = "Bloque 2",
                    weekLabel = null,
                    onSelect = {},
                )
            }
        }

        composeRule.onNodeWithText("Programa").assertIsDisplayed()
        composeRule.onNodeWithText("Bloque 2").assertIsDisplayed()
        composeRule.onAllNodesWithText("Semana", substring = true).assertCountEquals(0)
    }

    /** Tocar «Bloque 2» entrega el alcance de bloque. */
    @Test
    fun tocarBloqueEntregaElAlcanceBloque() {
        var picked: ProgressScope? = null
        composeRule.setContent {
            MaterialTheme {
                ProgressScopeChips(
                    selected = ProgressScope.PROGRAM,
                    blockLabel = "Bloque 2",
                    weekLabel = null,
                    onSelect = { picked = it },
                )
            }
        }

        composeRule.onNodeWithText("Bloque 2").performClick()

        assertEquals(ProgressScope.BLOCK, picked)
    }

    /** Con «Semana 4» la píldora sale, y tocarla entrega el alcance de semana. */
    @Test
    fun conSemanaSaleLaPildoraYTocarlaEntregaElAlcanceSemana() {
        var picked: ProgressScope? = null
        composeRule.setContent {
            MaterialTheme {
                ProgressScopeChips(
                    selected = ProgressScope.PROGRAM,
                    blockLabel = null,
                    weekLabel = "Semana 4",
                    onSelect = { picked = it },
                )
            }
        }

        composeRule.onNodeWithText("Semana 4").assertIsDisplayed().performClick()

        assertEquals(ProgressScope.WEEK, picked)
    }
}
