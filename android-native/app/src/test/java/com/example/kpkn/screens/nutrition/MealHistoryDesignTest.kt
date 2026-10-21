package com.example.kpkn.screens.nutrition

import android.app.Application
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import com.example.kpkn.screens.nutrition.design.history.MealHistoryMonthModule
import org.junit.Assert.assertEquals
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

/**
 * Interacción del historial (N5): tocar una fila entrega su día y «Ir a este día» entrega la fecha. Las piezas se montan sueltas: en
 * Robolectric, los toques de dentro de una `KpknSheet` no llegan porque la hoja vive en un host de overlay.
 */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [34], application = Application::class, qualifiers = "w360dp-h800dp-xxhdpi")
class MealHistoryDesignTest {

    @get:Rule
    val rule = createComposeRule()

    private val octubre: MealHistoryMonth = NutritionHistorySamples.history.first()
    private val hoy: MealHistoryDay = octubre.days.first()

    @Test
    fun tocarUnaFilaEntregaSuDia() {
        var abierto: MealHistoryDay? = null
        rule.setContent {
            MealHistoryMonthModule(month = octubre, index = 0, onOpen = { abierto = it })
        }
        rule.onNodeWithText(hoy.dateLabel).performClick()
        assertEquals(hoy.dateKey, abierto?.dateKey)
    }

    @Test
    fun irAEsteDiaLlamaConLaFecha() {
        var fecha: String? = null
        rule.setContent {
            MealHistoryDayContent(day = NutritionHistorySamples.diaConRango, onGoToDay = { fecha = it })
        }
        rule.onNodeWithText("Ir a este día").performClick()
        assertEquals("2026-10-12", fecha)
    }
}
