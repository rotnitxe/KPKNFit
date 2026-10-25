package com.example.kpkn.screens.nutrition.nutrients

import androidx.compose.runtime.*
import androidx.compose.ui.test.*
import androidx.compose.ui.test.junit4.createComposeRule
import com.example.kpkn.domain.nutrition.*
import com.example.kpkn.ui.theme.KPKNTheme
import org.junit.Assert.*
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [34], application = android.app.Application::class, qualifiers = "w360dp-h1000dp-mdpi")
class NutrientsSheetTest {
    @get:Rule val rule = createComposeRule()
    private fun show(name: String) {
        rule.setContent { KPKNTheme {
            var expanded by remember { mutableStateOf(emptySet<NutrientKey>()) }
            NutrientsContent(NutrientsSamples.state(name), expanded, { key -> expanded = expanded + key }, {})
        } }
    }
    @Test fun diaCompletoMuestraDatoYReferencia() {
        show("complete")
        rule.onNodeWithText("12,5 g conocidos").assertExists()
        rule.onNodeWithText("Referencia AI: 25 g").assertExists()
        assertTrue(NutrientsSamples.state("complete").rows.all { it.state == NutrientCoverageState.COMPLETE })
    }
    @Test fun diaVacioNoMuestraCerosInventados() {
        show("no_data")
        rule.onAllNodesWithText("Sin datos comparables").onFirst().assertExists()
        rule.onAllNodesWithText("0 g conocidos").assertCountEquals(0)
        assertTrue(NutrientsSamples.state("no_data").rows.all { it.totalKnown == null })
    }
    @Test fun entradaRapidaReduceCoberturaSinInventarMicronutrientes() {
        show("with_quick_entry")
        rule.onAllNodesWithText("Cobertura: 50 % de las kcal").onFirst().assertExists()
        assertTrue(NutrientsSamples.state("with_quick_entry").rows.all { it.missingCount == 1 && it.state == NutrientCoverageState.PARTIAL })
    }
    @Test fun elDetalleExplicaElTotalParcial() {
        show("partial")
        rule.onNodeWithText("Fibra").performClick()
        rule.onNodeWithTag("contributors-FIBER").assertExists()
        rule.onNodeWithText("Alimento con ficha: 12,5 g").assertExists()
    }
}
