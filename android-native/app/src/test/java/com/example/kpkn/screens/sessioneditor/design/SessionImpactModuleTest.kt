package com.example.kpkn.screens.sessioneditor.design

import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import com.example.kpkn.screens.onboarding.design.LocalWizardReducedMotion
import org.junit.Assert.assertEquals
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

/** Módulo «Impacto de la sesión» del editor (SE4): sin datos no se pinta, con datos lee sus cifras y tocarlo abre el asistente. */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [34], application = android.app.Application::class)
class SessionImpactModuleTest {

    @get:Rule
    val rule = createComposeRule()

    private val impact = SessionImpact(
        channels = SessionImpactChannels(energia = 18, musculos = 32, columna = 12),
        muscles = emptyList(),
    )

    private fun setModule(data: SessionImpact?, onOpen: () -> Unit = {}) {
        rule.setContent {
            CompositionLocalProvider(LocalWizardReducedMotion provides true) {
                SessionImpactModule(impact = data, onOpenAssistant = onOpen)
            }
        }
    }

    @Test
    fun sinDatosDelMotorElModuloNoSePinta() {
        setModule(data = null)
        rule.onNodeWithTag("sesion_impacto").assertDoesNotExist()
    }

    @Test
    fun conDatosSeLeenLasTresCifrasConSignoMenos() {
        setModule(data = impact)
        rule.onNodeWithTag("sesion_impacto").assertExists()
        // U+2212 delante de cada cifra; el texto va dentro del módulo fusionado, así que se busca sin fusionar.
        rule.onNodeWithText("−32", useUnmergedTree = true).assertExists()
        rule.onNodeWithText("−18", useUnmergedTree = true).assertExists()
        rule.onNodeWithText("−12", useUnmergedTree = true).assertExists()
    }

    @Test
    fun tocarElModuloAbreElAsistente() {
        var opened = 0
        setModule(data = impact, onOpen = { opened++ })
        rule.onNodeWithTag("sesion_impacto").performClick()
        assertEquals(1, opened)
    }
}
