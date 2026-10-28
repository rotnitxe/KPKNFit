package com.example.kpkn.screens.nutrition.nutrients

import androidx.compose.runtime.*
import androidx.compose.ui.test.*
import androidx.compose.ui.test.junit4.createComposeRule
import com.example.kpkn.data.models.*
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
    @Test fun losEncabezadosDeGrupoAparecen() {
        show("complete")
        NutrientGroup.entries.forEach {
            rule.onNodeWithTag("nutrients-list").performScrollToNode(hasTestTag("nutrient-group-${it.name}"))
            rule.onNodeWithTag("nutrient-group-${it.name}").assertExists()
        }
    }
    @Test fun unGrupoSinFilasNoDibujaEncabezado() {
        rule.setContent { KPKNTheme { NutrientsContent(NutrientsSheetState("2026-10-24", NutrientsSamples.state("complete").rows.filter { it.nutrient.group == NutrientGroup.CARBS }), emptySet(), {}, {}) } }
        rule.onNodeWithTag("nutrient-group-CARBS").assertExists()
        rule.onNodeWithTag("nutrient-group-FATS").assertDoesNotExist()
    }
    @Test fun elTopeDeLaGuiaSeMuestraYAvisaDelExceso() {
        val food = LoggedFood(foodName = "Mucha grasa", calories = 2000.0, fatBreakdown = FatBreakdown(saturated = 30.0))
        val state = buildNutrientsSheetState("2026-10-24", listOf(NutritionLog(date = "2026-10-24T12:00:00-03:00", foods = listOf(food))), NutrientsSamples.profile)
        assertEquals(2000.0, state.energyKcal!!, 1e-9)
        rule.setContent { KPKNTheme { NutrientsContent(state, emptySet(), {}, {}) } }
        rule.onNodeWithTag("nutrients-list").performScrollToNode(hasTestTag("nutrient-SAT_FAT"))
        rule.onNodeWithText("Tope de la guía: ${nutrientNumber(200.0 / 9.0)} g (10 % de 2000 kcal registradas) · OMS").assertExists()
        rule.onNodeWithText("El total conocido supera el tope de la guía.").assertExists()
    }
    @Test fun sinKcalElTopeSeDiceEnPorcentajeDeLaEnergia() {
        val state = NutrientsSheetState("2026-10-24", NutrientCoverage.summarize(emptyList()), NutrientsSamples.profile, null)
        rule.setContent { KPKNTheme { NutrientsContent(state, emptySet(), {}, {}) } }
        rule.onNodeWithTag("nutrients-list").performScrollToNode(hasTestTag("nutrient-SAT_FAT"))
        rule.onAllNodesWithText("Tope de la guía: 10 % de la energía del día · OMS").onFirst().assertExists()
    }
    @Test fun laNiacinaTieneSuNotaYElPieCitaLasFuentes() {
        show("complete")
        rule.onNodeWithTag("nutrients-list").performScrollToNode(hasTestTag("nutrient-NIACIN"))
        rule.onNodeWithText("Las fuentes dan niacina preformada; la referencia está en equivalentes (incluye la que el cuerpo forma del triptófano), así que tu total real suele ser mayor.").assertExists()
        rule.onNodeWithTag("nutrients-list").performScrollToNode(hasTestTag("nutrients-sources"))
        rule.onNodeWithTag("nutrients-sources").assertExists()
    }
}
