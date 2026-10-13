package com.example.kpkn.screens.nutrition.design

import android.app.Application
import androidx.compose.runtime.Composable
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNodeWithContentDescription
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import com.example.kpkn.screens.nutrition.NutritionSamples
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [34], application = Application::class, qualifiers = "w360dp-h800dp-xxhdpi")
class NutritionWeekTrendModuleTest {

    @get:Rule
    val rule = createComposeRule()

    @Composable
    private fun Trend(onSelect: (String) -> Unit = {}, onOpen: (() -> Unit)? = null) {
        NutritionWeekTrendModule(
            bars = NutritionSamples.weekBars,
            selectedDate = "2026-10-13",
            onSelectDate = onSelect,
            onOpenHistory = onOpen,
        )
    }

    @Test
    fun tocarUnaBarraElijeSuDia() {
        var picked: String? = null
        rule.setContent { Trend(onSelect = { picked = it }) }
        rule.onNodeWithTag("nutrition-week-bar-2026-10-10").performClick()
        assertEquals("2026-10-10", picked)
    }

    @Test
    fun unDiaSinRegistrosSeDiceAsiYElResumenVaEnElGrafico() {
        rule.setContent { Trend() }
        rule.onNodeWithContentDescription("jueves 8, sin registros").assertExists()
        rule.onNodeWithContentDescription("Últimos 7 días: media 1.918 kcal, 5 días registrados").assertExists()
        rule.onNodeWithText("Media de los días registrados: 1.918 kcal · 5 de 7 días").assertExists()
    }

    @Test
    fun verHistorialSoloAparecesiHayHistorialYLlevaAEl() {
        var opened = false
        rule.setContent { Trend(onOpen = { opened = true }) }
        rule.onNodeWithText("Ver historial").performClick()
        assertTrue(opened)
    }

    @Test
    fun sinHistorialNoHayAccionVerHistorial() {
        rule.setContent { Trend(onOpen = null) }
        rule.onNodeWithText("Ver historial").assertDoesNotExist()
    }
}
