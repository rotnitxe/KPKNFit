package com.example.kpkn.screens.body

import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNodeWithContentDescription
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import com.example.kpkn.data.models.MeasurementSchedule
import com.example.kpkn.screens.body.design.BodyReminderModule
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [34], application = android.app.Application::class, qualifiers = "w360dp-h1800dp-xxhdpi")
class BodyReminderModuleTest {

    @get:Rule
    val rule = createComposeRule()

    /** Pinta el recordatorio; [onUpdate], [onOtra] y [onHora] recogen lo que el usuario pide. */
    private fun montar(
        schedule: MeasurementSchedule = bodySampleSchedule(),
        customInterval: Boolean = false,
        onUpdate: (MeasurementSchedule) -> Unit = {},
        onOtra: (Boolean) -> Unit = {},
        onHora: () -> Unit = {},
    ) {
        rule.setContent {
            BodyReminderModule(
                schedule = schedule,
                customInterval = customInterval,
                onCustomIntervalChange = onOtra,
                onUpdate = onUpdate,
                onEditTime = onHora,
            )
        }
    }

    @Test
    fun elInterruptorLlamaAUpdateScheduleConElRecordatorioActivado() {
        var cambio: MeasurementSchedule? = null
        montar(schedule = bodySampleSchedule().copy(enabled = false), onUpdate = { cambio = it })
        rule.onNodeWithContentDescription("Recordatorio de pesaje").performClick()
        val nuevo = cambio!!
        assertTrue(nuevo.enabled)
        assertEquals(7, nuevo.intervalDays)
    }

    @Test
    fun laFrecuenciaPreestablecidaCambiaElIntervaloSinMostrarOtra() {
        var cambio: MeasurementSchedule? = null
        var otra: Boolean? = null
        montar(onUpdate = { cambio = it }, onOtra = { otra = it })
        rule.onNodeWithText("14 días").performClick()
        assertEquals(14, cambio!!.intervalDays)
        assertEquals(false, otra)
    }

    @Test
    fun otraMuestraElSelectorDeDiasYLoCambiaPasoAPaso() {
        var otra: Boolean? = null
        montar(onOtra = { otra = it })
        rule.onNodeWithText("Otra").performClick()
        assertEquals(true, otra)
    }

    @Test
    fun conOtraElSelectorDeDiasSeVeYSuMasSumaUnDia() {
        var cambio: MeasurementSchedule? = null
        montar(schedule = bodySampleSchedule(intervalDays = 21), customInterval = true, onUpdate = { cambio = it })
        rule.onNodeWithContentDescription("Añadir un día").assertIsDisplayed()
        rule.onNodeWithContentDescription("Quitar un día").assertIsDisplayed()
        rule.onNodeWithContentDescription("Añadir un día").performClick()
        assertEquals(22, cambio!!.intervalDays)
    }

    @Test
    fun sinOtraNoHaySelectorDeDias() {
        // Con una frecuencia preestablecida no hay selector: sus botones no existen.
        montar(customInterval = false)
        rule.onNodeWithText("Cada día").assertIsDisplayed()
        rule.onNodeWithContentDescription("Añadir un día").assertDoesNotExist()
    }

    @Test
    fun laHoraAbreElSelectorDeHora() {
        var abierto = false
        montar(onHora = { abierto = true })
        rule.onNodeWithContentDescription("Hora del recordatorio, 09:00").performClick()
        assertTrue(abierto)
    }

    @Test
    fun laProximaMedicionSeVeEnSuFechaYSuHora() {
        montar()
        // «a las» y la hora van con espacios de no separación: la hora nunca queda sola en una línea.
        rule.onNodeWithText("Próxima medición: martes 20 de octubre a las 09:00").assertIsDisplayed()
    }
}
