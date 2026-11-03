package com.example.kpkn.screens.programdetail.design.split

import android.app.Application
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.test.assertIsEnabled
import androidx.compose.ui.test.assertIsNotEnabled
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import com.example.kpkn.data.splits.SplitTag
import com.example.kpkn.domain.training.split.SplitCatalog
import com.example.kpkn.domain.training.split.SplitCatalogFilter
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

/**
 * La tarjeta del catálogo y la página «Reparto» (punto 23), en Robolectric: tocar la tarjeta abre la vista previa (no hay botones dentro); la
 * tarjeta trae su descripción, su etiqueta de recomendado y sus etiquetas; en modo comparar, tocar dos repartos habilita «Comparar 2»; y
 * la etiqueta deja solo los repartos que la llevan.
 */
// Ventana alta: performClick no desplaza, así que todos los módulos de la prueba tienen que caber en pantalla.
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [34], qualifiers = "w360dp-h4000dp", application = Application::class)
class SplitCatalogModuleTest {

    @get:Rule
    val composeRule = createComposeRule()

    private val ulX4 = SplitCatalog.entry("ul_x4")!!
    private val pplUl = SplitCatalog.entry("ppl_ul")!!
    private val broSplit = SplitCatalog.entry("bro_split")!!

    @Test
    fun tocarLaTarjetaLlamaALaAccion() {
        var opened = false
        composeRule.setContent {
            SplitCatalogCard(entry = ulX4, startDay = 1, mode = SplitCatalogMode.BROWSE, selected = false, onClick = { opened = true })
        }
        composeRule.onNodeWithTag("split_aplicar_ul_x4").performClick()
        assertTrue(opened)
    }

    @Test
    fun laTarjetaTraeDescripcionRecomendadoYEtiquetas() {
        composeRule.setContent {
            SplitCatalogCard(entry = ulX4, startDay = 1, mode = SplitCatalogMode.BROWSE, selected = false, onClick = {})
        }
        composeRule.onNodeWithText(ulX4.name).assertExists()
        composeRule.onNodeWithText(ulX4.description).assertExists()
        composeRule.onNodeWithText("RECOMENDADO KPKN").assertExists()
        composeRule.onNodeWithText("4 días").assertExists()
        composeRule.onNodeWithText(ulX4.levelLabel).assertExists()
        // Sin radio ni botones dentro: la tarjeta entera es el botón.
        composeRule.onNodeWithText("Aplicar").assertDoesNotExist()
    }

    @Test
    fun unRepartoQuePideOtroNumeroDeDiasAvisaEnLaTarjeta() {
        composeRule.setContent {
            SplitCatalogCard(entry = pplUl, startDay = 1, mode = SplitCatalogMode.BROWSE, selected = false, onClick = {}, needsOtherDays = 4)
        }
        composeRule.onNodeWithText("Necesita 5 días y tu semana tiene 4. Al elegirlo, el reparto propone los días.").assertExists()
    }

    @Test
    fun enCompararDosRepartosHabilitanCompararDos() {
        composeRule.setContent { CatalogHost(mode = SplitCatalogMode.COMPARE) }
        composeRule.onNodeWithText("Elige dos repartos").assertIsNotEnabled()
        composeRule.onNodeWithText(ulX4.name).performClick()
        composeRule.onNodeWithText("Elige dos repartos").assertIsNotEnabled()
        composeRule.onNodeWithText(pplUl.name).performClick()
        composeRule.onNodeWithText("Comparar 2").assertIsEnabled()
    }

    @Test
    fun filtrarPorEtiquetaDejaSoloLosSuyos() {
        composeRule.setContent { CatalogHost(mode = SplitCatalogMode.BROWSE) }
        composeRule.onNodeWithText(broSplit.name).assertExists()
        composeRule.onNodeWithText("Recomendado KPKN").performClick()
        composeRule.onNodeWithText(ulX4.name).assertExists()
        composeRule.onNodeWithText(broSplit.name).assertDoesNotExist()
    }

    @Test
    fun elFiltroPorDiasAgrupaLosQueEncajan() {
        val view = SplitCatalog.view(SplitCatalogFilter(days = 4), haveDays = 4)
        assertEquals(listOf("Encajan con tus 4 días"), view.groups.map { it.title })
        assertTrue(view.groups.single().entries.all { it.trainingDays == 4 })
    }

    /** La página con el estado real del filtro y de lo elegido (cada toque añade o quita; un tercero sustituye al primero en comparar). */
    @Composable
    private fun CatalogHost(mode: SplitCatalogMode) {
        var tag by remember { mutableStateOf<SplitTag?>(null) }
        var ids by remember { mutableStateOf(listOf<String>()) }
        val view = SplitCatalog.view(SplitCatalogFilter(tag = tag), haveDays = null)
        SplitCatalogScreen(
            now = null,
            nowWeekLabel = "semana 5",
            haveDays = null,
            startDay = 1,
            query = "",
            onQueryChange = {},
            selectedTag = tag,
            onSelectTag = { tag = it },
            daysFilter = null,
            onDaysFilter = {},
            groups = view.groups,
            customSplit = null,
            mode = mode,
            selectedIds = ids,
            onBack = null,
            onToggleCompareMode = {},
            onToggleMultiMode = {},
            onOpen = {},
            onToggleSelect = { entry ->
                ids = when {
                    entry.id in ids -> ids - entry.id
                    ids.size >= 2 -> ids.drop(1) + entry.id
                    else -> ids + entry.id
                }
            },
            onEditCustom = {},
            onApplyCustom = {},
            onCompare = {},
            onApplySelected = {},
        )
    }
}
