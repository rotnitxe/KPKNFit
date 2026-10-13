package com.example.kpkn.screens.home.design

import androidx.compose.foundation.layout.Column
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import org.junit.Assert.assertEquals
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

/** Aviso de Home: su acción llama a la lambda y los dos tonos se pintan con su título. */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [34], application = android.app.Application::class, qualifiers = "w360dp-h800dp-xxhdpi")
class HomeNoticeTest {

    @get:Rule
    val rule = createComposeRule()

    @Test
    fun `la accion llama a la lambda`() {
        var taps = 0
        rule.setContent {
            HomeNotice(
                title = "Posible exceso de volumen",
                body = "Hay señales de carga alta en: pecho. Considera reducir series semanales.",
                actionLabel = "Ver recomendaciones",
                onAction = { taps++ },
                tone = HomeNoticeTone.DANGER,
            )
        }

        rule.onNodeWithText("Ver recomendaciones").performClick()

        assertEquals(1, taps)
    }

    @Test
    fun `DANGER y WARN se pintan con su titulo`() {
        rule.setContent {
            Column {
                HomeNotice(
                    title = "Exceso de carga",
                    body = "Baja un poco el volumen.",
                    actionLabel = "Entendido",
                    onAction = {},
                    tone = HomeNoticeTone.DANGER,
                )
                HomeNotice(
                    title = "Ajuste sugerido",
                    body = "Hoy, algo menos de carga.",
                    actionLabel = "Entendido",
                    onAction = {},
                    tone = HomeNoticeTone.WARN,
                )
            }
        }

        rule.onNodeWithTag("home-notice-danger").assertIsDisplayed()
        rule.onNodeWithTag("home-notice-warn").assertIsDisplayed()
        rule.onNodeWithText("Exceso de carga").assertIsDisplayed()
        rule.onNodeWithText("Ajuste sugerido").assertIsDisplayed()
    }
}
