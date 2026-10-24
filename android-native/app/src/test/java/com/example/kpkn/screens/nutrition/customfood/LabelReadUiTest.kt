package com.example.kpkn.screens.nutrition.customfood

import android.app.Application
import androidx.compose.foundation.layout.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.test.*
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.unit.dp
import com.example.kpkn.domain.nutrition.label.*
import org.junit.*
import org.junit.Assert.*
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [34], application = Application::class, qualifiers = "w360dp-h2600dp-mdpi")
class LabelReadUiTest {
    @get:Rule val rule = createComposeRule()
    @Test fun lecturaSeRevisaYSoloGuardarInvocaElCallback() {
        val form = CustomFoodForm(name = "Propio", servingAmount = "100", calories = "200", protein = "10", carbs = "20", fats = "8")
        val label = LabelParse(LabelBasis.PER_100G)
        var saves = 0; var undos = 0
        rule.setContent { CustomFoodFormContent(form, CustomFoodValidator.validate(form, "test"), false, false, false, null, false,
            {}, { saves++ }, {}, {}, {}, {}, Modifier.width(360.dp).height(2400.dp),
            LabelFormState(form, label = label, markers = mapOf(CustomFoodField.PROTEIN to LabelConfidence.UNSURE), canUndo = true), onUndoLabel = { undos++ }) }
        rule.onNodeWithText("Dudoso · leído de la foto; revisa").performScrollTo().assertExists()
        assertEquals(0, saves)
        rule.onNodeWithText("Deshacer lectura").performScrollTo().performClick(); assertEquals(1, undos)
        rule.onNodeWithTag("custom-food-save").performClick(); assertEquals(1, saves)
    }
    @Test fun lecturaEnCursoBloqueaGuardar() {
        val form = CustomFoodForm()
        rule.setContent { CustomFoodFormContent(form, CustomFoodValidator.validate(form, "test"), false, false, false, null, false,
            {}, {}, {}, {}, {}, {}, Modifier.width(360.dp).height(2400.dp), LabelFormState(form, reading = true)) }
        rule.onNodeWithTag("custom-food-save").assertIsNotEnabled()
    }
    @Test fun sodioCeroDeclaradoYSaturadasSeConservan() {
        val form = CustomFoodForm(name = "Propio", servingAmount = "100", calories = "200", protein = "10", carbs = "20", fats = "8", sodiumMg = "0", saturatedFat = "2")
        val food = CustomFoodValidator.validate(form, "test").food!!
        assertTrue(food.micronutrients.any { it.name.equals("sodio", true) && it.amount == 0.0 })
        assertEquals("2", CustomFoodValidator.fromFood(food).saturatedFat)
        assertFalse(CustomFoodValidator.validate(form.copy(sodiumMg = ""), "test").food!!.micronutrients.any { it.name.equals("sodio", true) })
    }
}
