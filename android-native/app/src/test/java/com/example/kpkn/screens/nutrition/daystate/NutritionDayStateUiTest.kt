package com.example.kpkn.screens.nutrition.daystate

import android.app.Application
import androidx.compose.foundation.layout.Column
import androidx.compose.ui.test.*
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.unit.dp
import com.example.kpkn.domain.nutrition.expenditure.DayMark
import org.junit.Assert.*
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [34], application = Application::class, qualifiers = "w360dp-h1000dp-mdpi")
class NutritionDayStateUiTest {
    @get:Rule val rule = createComposeRule()

    @Test fun `la linea sin marca abre la hoja y tiene un objetivo tactil accesible`() {
        var opened = false
        rule.setContent { NutritionDayStateLine(NutritionDayStateSamples.state("unmarked"), { opened = true }, {}, {}) }
        rule.onNodeWithText("Día sin marcar").assertExists()
        rule.onNodeWithTag("nutrition-day-state").assertHasClickAction().assertHeightIsAtLeast(48.dp).performClick()
        assertTrue(opened)
    }

    @Test fun `la sugerencia ofrece aceptar y rechazar con acciones distintas`() {
        var accepted = 0
        var rejected = 0
        rule.setContent { NutritionDayStateLine(NutritionDayStateSamples.state("suggested"), {}, { accepted++ }, { rejected++ }) }
        rule.onNodeWithText("Marcar como parcial").performClick()
        rule.onNodeWithText("Así es mi día").performClick()
        assertEquals(1, accepted)
        assertEquals(1, rejected)
    }

    @Test fun `con comidas no ofrece ayuno y quitar tiene su propia accion`() {
        var chosen: DayMark? = null
        rule.setContent { NutritionDayStateOptions(NutritionDayStateSamples.state("complete"), { chosen = it }) }
        rule.onNodeWithTag("nutrition-day-option-FASTING").assertDoesNotExist()
        rule.onNodeWithTag("nutrition-day-option-NONE").performClick()
        assertEquals(DayMark.NONE, chosen)
        rule.onNodeWithTag("nutrition-day-option-PARTIAL").assertHeightIsAtLeast(48.dp).performClick()
        assertEquals(DayMark.PARTIAL, chosen)
    }

    @Test fun `sin comidas permite ayuno y deshabilita completo y parcial`() {
        var chosen: DayMark? = null
        rule.setContent { NutritionDayStateOptions(NutritionDayStateSamples.state("fasting").copy(mark = DayMark.NONE), { chosen = it }) }
        rule.onNodeWithTag("nutrition-day-option-COMPLETE_CONFIRMED").assertIsNotEnabled()
        rule.onNodeWithTag("nutrition-day-option-PARTIAL").assertIsNotEnabled()
        rule.onNodeWithTag("nutrition-day-option-FASTING").performClick()
        assertEquals(DayMark.FASTING, chosen)
    }

    @Test fun `un futuro y un guardado pendiente deshabilitan las acciones`() {
        rule.setContent { Column {
            NutritionDayStateLine(NutritionDayStateSamples.state("unmarked").copy(editable = false), {}, {}, {})
            NutritionDayStateOptions(NutritionDayStateSamples.state("partial").copy(saving = true), {})
        } }
        rule.onNodeWithTag("nutrition-day-state").assertIsNotEnabled()
        rule.onNodeWithTag("nutrition-day-option-PARTIAL").assertIsNotEnabled()
        rule.onNodeWithText("Día futuro").assertExists()
    }
}
