package com.example.kpkn.screens.body

import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.click
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNodeWithContentDescription
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performTouchInput
import com.example.kpkn.screens.body.design.BodyTrendChart
import com.example.kpkn.screens.body.design.bodyWeekdayDayEs
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [34], application = android.app.Application::class, qualifiers = "w360dp-h1800dp-xxhdpi")
class BodyTrendChartTest {

    @get:Rule
    val rule = createComposeRule()

    @Test
    fun laDescripcionResumeLaTendencia() {
        rule.setContent {
            BodyTrendChart(points = bodySampleWeightPoints(), goal = 60.0, unit = "kg", name = "peso")
        }
        rule.onNodeWithContentDescription("Tendencia de peso: de 66,9 a 64,5 kg", substring = true).assertIsDisplayed()
    }

    @Test
    fun alPulsarLaBurbujaMuestraElDiaDelPunto() {
        val points = bodySampleWeightPoints()
        rule.mainClock.autoAdvance = false
        rule.setContent {
            BodyTrendChart(points = points, goal = 60.0, unit = "kg", name = "peso")
        }
        asentar()
        // Pulsar el borde izquierdo del área de datos: el día más cercano es el primero.
        rule.onNodeWithContentDescription("Tendencia de peso", substring = true)
            .performTouchInput { click(Offset(1f, 20f)) }
        asentar()
        rule.onNodeWithText(bodyWeekdayDayEs(points.first().date), useUnmergedTree = true).assertExists()
    }

    /** Con el reloj parado, cada cambio de estado se compone en un frame: se deja llegar el toque y pasar frames. */
    private fun asentar() {
        repeat(3) {
            rule.waitForIdle()
            rule.mainClock.advanceTimeByFrame()
        }
        rule.waitForIdle()
    }

    @Test
    fun sinDatosLoDice() {
        rule.setContent {
            BodyTrendChart(points = emptyList(), goal = null, unit = "kg", name = "peso")
        }
        rule.onNodeWithText("Sin datos en este rango").assertIsDisplayed()
    }
}
