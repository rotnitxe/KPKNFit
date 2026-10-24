package com.example.kpkn.screens.settings.equipment

import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.test.assertIsEnabled
import androidx.compose.ui.test.assertIsNotEnabled
import androidx.compose.ui.test.hasAnyAncestor
import androidx.compose.ui.test.hasSetTextAction
import androidx.compose.ui.test.hasTestTag
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performScrollTo
import androidx.compose.ui.test.performTextReplacement
import androidx.compose.ui.unit.Density
import com.example.kpkn.domain.equipment.EquipmentInventoryTemplate
import com.example.kpkn.screens.onboarding.design.LocalWizardReducedMotion
import com.example.kpkn.ui.theme.KPKNTheme
import org.junit.Assert.assertEquals
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [34], qualifiers = "w360dp-h800dp-mdpi", application = android.app.Application::class)
class EquipmentInventoryScreenTest {
    @get:Rule val rule = createComposeRule()

    @Test fun guardarSeDesactivaConUnPesoInvalidoYElCampoExplicaElError() {
        rule.setContent { KPKNTheme { CompositionLocalProvider(LocalWizardReducedMotion provides true) {
            EquipmentInventoryScreen(EquipmentInventorySamples.state("error"))
        } } }
        rule.onNodeWithTag("equipment-save").assertIsNotEnabled()
        rule.onNodeWithTag("equipment-barbell").performScrollTo()
        rule.onNodeWithText("Introduce un peso mayor que 0 y hasta 100 kg.").assertExists()
    }

    @Test fun laPlantillaYElCampoDeBarraEnvianLaEdicionAlBorrador() {
        var selected: EquipmentInventoryTemplate? = null
        var bar: String? = null
        rule.setContent { KPKNTheme { CompositionLocalProvider(LocalWizardReducedMotion provides true) {
            EquipmentInventoryScreen(EquipmentInventorySamples.state("empty"), EquipmentInventoryActions(
                template = { selected = it }, edit = { bar = it.barbellWeightKg },
            ))
        } } }
        rule.onNodeWithText("Gimnasio en casa").performScrollTo().performClick()
        assertEquals(EquipmentInventoryTemplate.HOME, selected)
        rule.onNodeWithTag("equipment-barbell").performScrollTo()
        rule.onNode(hasSetTextAction() and hasAnyAncestor(hasTestTag("equipment-barbell")), useUnmergedTree = true).performTextReplacement("15")
        assertEquals("15", bar)
    }

    @Test fun elResumenYElBotonSiguenLegiblesAl130PorCiento() {
        var saves = 0
        rule.setContent { KPKNTheme {
            val base = LocalDensity.current
            CompositionLocalProvider(LocalDensity provides Density(base.density, 1.3f), LocalWizardReducedMotion provides true) {
                EquipmentInventoryScreen(EquipmentInventorySamples.state("commercial"), EquipmentInventoryActions(save = { saves++ }))
            }
        } }
        rule.onNodeWithText("20 – 335 kg").assertExists()
        rule.onNodeWithTag("equipment-save").assertIsEnabled().performClick()
        assertEquals(1, saves)
    }
}
