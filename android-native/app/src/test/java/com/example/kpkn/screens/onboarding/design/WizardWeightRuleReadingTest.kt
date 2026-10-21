package com.example.kpkn.screens.onboarding.design

import android.app.Application
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNodeWithTag
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

/** La cifra central de la regla de peso: se pinta por defecto y desaparece con `showReading = false`. */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [34], application = Application::class)
class WizardWeightRuleReadingTest {

    @get:Rule
    val rule = createComposeRule()

    @Test
    fun porDefectoLaCifraCentralSePinta() {
        rule.setContent {
            WizardWeightRule(unit = WizardMassUnit.KG, valueKg = 68.5, onValueChange = {})
        }
        rule.onNodeWithTag("setup-weight-value").assertExists()
    }

    @Test
    fun conShowReadingFalsoLaCifraCentralNoSePinta() {
        rule.setContent {
            WizardWeightRule(unit = WizardMassUnit.KG, valueKg = 68.5, onValueChange = {}, showReading = false)
        }
        rule.onNodeWithTag("setup-weight-value").assertDoesNotExist()
    }
}
