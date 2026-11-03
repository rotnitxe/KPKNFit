package com.example.kpkn.screens.settings

import android.app.Application
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.onAllNodesWithText
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performTouchInput
import androidx.compose.ui.test.center
import androidx.compose.foundation.layout.Column
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.ui.unit.dp
import com.example.kpkn.data.models.WeightUnit
import com.example.kpkn.ui.theme.KPKNTheme
import org.junit.Assert.assertEquals
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [34], qualifiers = "w360dp-h880dp-mdpi", application = Application::class)
class AjustesScreensTest {
    @get:Rule val rule = createComposeRule()

    @Test fun lasHorasSeLeenConLosLimitesDelReloj() {
        assertEquals(18 to 5, ajParseTime("18:05"))
        assertEquals(23 to 59, ajParseTime("99:99"))
        assertEquals(0 to 0, ajParseTime("raro"))
        assertEquals("07:05", ajFormatTime(7, 5))
        assertEquals("23:55", ajFormatTime(-1, 55))
        assertEquals("00:00", ajFormatTime(24, 60))
    }

    @Test fun comidasNoCuentaSiSeSaltoNutricion() {
        assertEquals(1, ajActiveReminders(workout = true, meals = true, sleep = false, nutrition = false))
        assertEquals(3, ajActiveReminders(workout = true, meals = true, sleep = true, nutrition = true))
        assertEquals(0, ajActiveReminders(workout = false, meals = false, sleep = false, nutrition = true))
    }

    @Test fun laPantallaPrincipalSoloTieneLoGlobal() {
        rule.setContent {
            KPKNTheme {
                AjMainScreen(WeightUnit.KG, 0, 2, true, {}, {}, {}, {}, {}, {}, {}, {})
            }
        }
        rule.onNodeWithText("Unidades").assertIsDisplayed()
        rule.onNodeWithText("kg").assertIsDisplayed()
        rule.onNodeWithText("Sin activar").assertIsDisplayed()
        rule.onNodeWithText("2 por activar").assertIsDisplayed()
        rule.onNodeWithText("Health Connect").assertIsDisplayed()
        rule.onNodeWithText("Borrar todos los datos").assertIsDisplayed()
        assertEquals(0, rule.onAllNodesWithTextCount("Mi material"))
        assertEquals(0, rule.onAllNodesWithTextCount("Descanso por defecto"))
    }

    @Test fun sinPermisoDeNotificacionesElAvisoPideActivarlo() {
        var asked = 0
        rule.setContent {
            KPKNTheme {
                AjRemindersScreen(
                    AjPermState.DENIED, true, true, "18:00", true, false, "08:00", "13:00", "20:00", false, "22:00",
                    {}, { _, _ -> }, {}, { asked++ }, {},
                )
            }
        }
        rule.onNodeWithText("Las notificaciones están desactivadas.", substring = true).assertIsDisplayed()
        rule.onNodeWithTag("aj-warn-button").performClick()
        assertEquals(1, asked)
        rule.onNodeWithText("Activar notificaciones").assertIsDisplayed()
    }

    @Test fun elInterruptorAvisaYMuestraLaHoraSoloEncendido() {
        var toggled: Pair<AjReminder, Boolean>? = null
        rule.setContent {
            KPKNTheme {
                AjRemindersScreen(
                    AjPermState.GRANTED, true, true, "18:00", false, false, "08:00", "13:00", "20:00", false, "22:00",
                    {}, { which, on -> toggled = which to on }, {}, {}, {},
                )
            }
        }
        rule.onNodeWithText("18:00").assertIsDisplayed()
        rule.onNodeWithTag("aj-rem-sleep").performClick()
        assertEquals(AjReminder.Sleep to true, toggled)
        assertEquals(0, rule.onAllNodesWithTextCount("Comidas"))
    }

    @Test fun unPermisoBloqueadoExplicaComoRecuperarlo() {
        rule.setContent {
            KPKNTheme {
                AjPermissionsScreen(AjPerm.entries.associateWith { if (it == AjPerm.Camera) AjPermState.BLOCKED else AjPermState.GRANTED }, {}, {})
            }
        }
        rule.onNodeWithText("Bloqueado: el sistema ya no pregunta.").assertIsDisplayed()
        rule.onNodeWithText("Si el sistema deja de preguntar", substring = true).assertIsDisplayed()
    }

    @Test fun mantenerPulsadoSoloBorraAlCumplirElTiempo() {
        var confirmed = 0
        rule.mainClock.autoAdvance = false
        rule.setContent { KPKNTheme { Column { AjHoldButton("Mantén pulsado para borrar", "Sigue pulsando…", { confirmed++ }) } } }
        rule.onNodeWithTag("aj-hold").performTouchInput { down(center) }
        rule.mainClock.advanceTimeBy(600)
        rule.onNodeWithTag("aj-hold").performTouchInput { up() }
        rule.mainClock.advanceTimeBy(1500)
        assertEquals(0, confirmed)
        rule.onNodeWithTag("aj-hold").performTouchInput { down(center) }
        rule.mainClock.advanceTimeBy(1400)
        rule.waitForIdle()
        assertEquals(1, confirmed)
    }

    private fun androidx.compose.ui.test.junit4.ComposeContentTestRule.onAllNodesWithTextCount(text: String): Int =
        onAllNodesWithText(text, substring = true).fetchSemanticsNodes().size
}
