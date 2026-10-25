package com.example.kpkn.screens.nutrition.customfood

import android.app.Application
import androidx.compose.foundation.layout.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.test.*
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.unit.dp
import org.junit.*
import org.junit.Assert.*
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class)
@Config(sdk=[34],application=Application::class,qualifiers="w360dp-h2600dp-mdpi")
class DuplicateBarcodeWarningUiTest {
    @get:Rule val rule=createComposeRule()
    @Test fun avisoEsVisibleYGuardarSigueSiendoUnaDecisionExplicita() {
        val form=CustomFoodForm(name="Mi alimento",servingAmount="100",calories="200",protein="10",carbs="20",fats="8")
        var saves=0
        rule.setContent { CustomFoodFormContent(form,CustomFoodValidator.validate(form,"test"),false,false,false,null,false,
            {},{saves++},{},{},{},{},Modifier.width(360.dp).height(2400.dp),duplicateWarning="Este código ya está en otro alimento propio.") }
        rule.onNodeWithTag("custom-food-duplicate-warning").performScrollTo().assertIsDisplayed()
        assertEquals(0,saves)
        rule.onNodeWithTag("custom-food-save").assertIsEnabled().performClick()
        assertEquals(1,saves)
    }
}
