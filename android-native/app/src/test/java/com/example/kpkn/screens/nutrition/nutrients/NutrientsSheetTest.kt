package com.example.kpkn.screens.nutrition.nutrients

import androidx.compose.ui.test.*
import androidx.compose.ui.test.junit4.createComposeRule
import com.example.kpkn.data.models.*
import com.example.kpkn.domain.nutrition.*
import com.example.kpkn.screens.onboarding.design.LocalWizardReducedMotion
import com.example.kpkn.ui.theme.KPKNTheme
import androidx.compose.runtime.CompositionLocalProvider
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
    private val opened = mutableListOf<NutrientKey>()
    private fun show(state: NutrientsSheetState) {
        rule.setContent { KPKNTheme { CompositionLocalProvider(LocalWizardReducedMotion provides true) {
            NutrientsContent(state, { opened += it })
        } } }
    }

    @Test fun laHojaDelDiaTieneTituloFechaYReferencia() {
        show(NutrientsSamples.state("complete"))
        rule.onNodeWithText("Vitaminas y minerales").assertExists()
        rule.onNodeWithText("Sábado 24 · % de la referencia de tu perfil").assertExists()
    }

    @Test fun unaFilaPorNutrienteYLosGruposVanUnoTrasOtro() {
        show(NutrientsSamples.state("complete"))
        rule.onNodeWithTag("nutrient-VITAMIN_A").assertExists()
        rule.onNodeWithTag("nutrient-FIBER").assertExists()
        rule.onNodeWithText("13 g").assertExists()
        rule.onAllNodesWithText("50 %").onFirst().assertExists()
        NutrientKey.entries.forEach { rule.onNodeWithTag("nutrient-${it.name}").assertExists() }
    }

    @Test fun lasEtiquetasDeGrupoVanEnElOrdenDelLienzo() {
        show(NutrientsSamples.state("complete"))
        assertEquals(listOf("Vitaminas", "Minerales", "Grasas", "Carbos", "Otros"), NutrientGroup.entries.map { it.label })
        NutrientGroup.entries.forEach { rule.onNodeWithText(it.label.uppercase()).assertExists() }
        rule.onNodeWithTag("nutrient-group-VITAMINS").assertDoesNotExist()
    }

    @Test fun diaVacioNoMuestraCerosInventados() {
        show(NutrientsSamples.state("no_data"))
        rule.onAllNodesWithText("sin dato").onFirst().assertExists()
        rule.onAllNodesWithText("0 µg").assertCountEquals(0)
        rule.onAllNodesWithText("0 %").assertCountEquals(0)
    }

    @Test fun entradaRapidaMarcaLoParcialConMayorOIgual() {
        show(NutrientsSamples.state("with_quick_entry"))
        rule.onAllNodesWithText("≥", substring = true).onFirst().assertExists()
    }

    @Test fun tocarUnaFilaAbreSuHoja() {
        show(NutrientsSamples.state("partial"))
        rule.onNodeWithTag("nutrient-THIAMIN").performClick()
        assertEquals(listOf(NutrientKey.THIAMIN), opened)
    }

    @Test fun elTopeSeQuedaEnSuGrupoConMaxEnLaCantidad() {
        val food = LoggedFood(foodName = "Mucha grasa", calories = 2000.0, fatBreakdown = FatBreakdown(saturated = 30.0))
        show(buildNutrientsSheetState("2026-10-24", listOf(NutritionLog(date = "2026-10-24T12:00:00-03:00", foods = listOf(food))), NutrientsSamples.profile))
        rule.onNodeWithTag("nutrients-watch").assertDoesNotExist()
        rule.onNodeWithTag("nutrient-SAT_FAT").assertExists()
        rule.onAllNodesWithText("· máx.", substring = true).onFirst().assertExists()
    }

    @Test fun elPieCitaLasFuentes() {
        show(NutrientsSamples.state("complete"))
        rule.onNodeWithTag("nutrients-list").performScrollToNode(hasTestTag("nutrients-sources"))
        rule.onNodeWithTag("nutrients-sources").assertTextContains("No sustituye el consejo de un profesional.", substring = true)
    }

    @Test fun elModuloDelDiaTieneHuellaLeyendaYCobertura() {
        var opened = 0
        rule.setContent { KPKNTheme { CompositionLocalProvider(LocalWizardReducedMotion provides true) {
            NutrientsDayModule(NutrientsSamples.state("partial"), { opened++ })
        } } }
        rule.onNodeWithText("VITAMINAS Y MINERALES", useUnmergedTree = true).assertExists()
        rule.onNodeWithText("sábado 24", useUnmergedTree = true).assertExists()
        rule.onNodeWithText("Ver todos", useUnmergedTree = true).assertDoesNotExist()
        rule.onNodeWithTag("nutrient-footprint", useUnmergedTree = true).assertExists()
        listOf("cubiertos", "cerca", "por debajo").forEach { rule.onNodeWithText(it, useUnmergedTree = true).assertExists() }
        rule.onNodeWithText("Cobertura: 50 % de las kcal con dato · 1 alimento sin ficha", useUnmergedTree = true).assertExists()
        rule.onNodeWithTag("nutrients-day-module").performClick()
        assertEquals(1, opened)
    }

    @Test fun laHojaDelNutrienteDiceDeDondeSale() {
        val state = NutrientsSamples.state("partial")
        val detail = NutrientFootprints.detail(state.rows.first { it.nutrient == NutrientKey.FIBER }, state.profile, state.energyKcal, "hoy")
        rule.setContent { KPKNTheme { NutrientDetailContent(detail) } }
        rule.onNodeWithText("DE DÓNDE SALE").assertExists()
        rule.onNodeWithText("Alimento con ficha").assertExists()
        rule.onNodeWithText("Alimento sin detalle no trae dato de fibra: el total puede ser mayor.").assertExists()
        rule.onNodeWithText("AI").assertExists()
    }
}
