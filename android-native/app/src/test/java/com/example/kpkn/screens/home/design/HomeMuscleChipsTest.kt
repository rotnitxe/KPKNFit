package com.example.kpkn.screens.home.design

import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNodeWithContentDescription
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import com.example.kpkn.screens.onboarding.design.LocalWizardReducedMotion
import org.junit.Assert.assertEquals
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

/** La fila de músculos de la sesión (I10a): «—» sin dato, la descripción con el %, y que el toque abre el detalle. */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [34], application = android.app.Application::class, qualifiers = "w360dp-h800dp-xxhdpi")
class HomeMuscleChipsTest {

    @get:Rule
    val rule = createComposeRule()

    private val muscles = listOf(HomeMuscleGauge("Pantorrilla", null), HomeMuscleGauge("Isquios", 78))

    private fun setChips(onOpen: () -> Unit = {}) {
        rule.setContent {
            CompositionLocalProvider(LocalWizardReducedMotion provides true) {
                HomeMuscleChips(muscles = muscles, onOpen = onOpen)
            }
        }
    }

    @Test
    fun sin_dato_sale_con_guion_y_nunca_con_100() {
        setChips()

        rule.onNodeWithText("—", useUnmergedTree = true).assertExists()
        rule.onNodeWithText("78 %", useUnmergedTree = true).assertExists()
        rule.onNodeWithText("100 %", useUnmergedTree = true).assertDoesNotExist()
        rule.onNodeWithContentDescription("Pantorrilla, sin dato").assertExists()
    }

    @Test
    fun cada_chip_describe_su_musculo_y_su_porcentaje() {
        setChips()

        rule.onNodeWithContentDescription("Isquios, 78 %").assertExists()
    }

    @Test
    fun el_toque_llama_a_onOpen() {
        var opened = 0
        setChips(onOpen = { opened++ })

        rule.onNodeWithContentDescription("Isquios, 78 %").performClick()
        assertEquals(1, opened)
    }
}
