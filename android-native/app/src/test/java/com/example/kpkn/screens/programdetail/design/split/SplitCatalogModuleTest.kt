package com.example.kpkn.screens.programdetail.design.split

import android.app.Application
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.test.assertIsEnabled
import androidx.compose.ui.test.assertIsNotEnabled
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNodeWithContentDescription
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import com.example.kpkn.data.splits.SPLIT_TEMPLATES
import com.example.kpkn.data.splits.SplitTag
import com.example.kpkn.data.splits.SplitTemplate
import com.example.kpkn.screens.onboarding.design.LocalWizardReducedMotion
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

/**
 * Módulo del catálogo y página «Cambiar reparto» (P5), en Robolectric: «Aplicar» y el icono de información llaman a su acción; en modo
 * comparar, tocar dos repartos habilita «Comparar 2»; y la etiqueta deja solo los repartos que la llevan. Se pintan con movimiento
 * reducido para que la entrada animada no mueva los nodos.
 */
// Ventana alta: performClick no desplaza, así que todos los módulos de la prueba tienen que caber en pantalla.
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [34], qualifiers = "w360dp-h2400dp", application = Application::class)
class SplitCatalogModuleTest {

    @get:Rule
    val composeRule = createComposeRule()

    private val ulX4: SplitTemplate = SPLIT_TEMPLATES.first { it.id == "ul_x4" }
    private val pplUl: SplitTemplate = SPLIT_TEMPLATES.first { it.id == "ppl_ul" }
    private val pplX6: SplitTemplate = SPLIT_TEMPLATES.first { it.id == "ppl_x6" }
    private val broSplit: SplitTemplate = SPLIT_TEMPLATES.first { it.id == "bro_split" }

    /** Pinta [content] en reposo, sin la entrada animada de los módulos. */
    private fun setAtRest(content: @Composable () -> Unit) {
        composeRule.setContent {
            CompositionLocalProvider(LocalWizardReducedMotion provides true) {
                content()
            }
        }
    }

    @Test
    fun aplicarLlamaAlAccionDelModulo() {
        var applied = false
        setAtRest {
            SplitCatalogModule(
                split = ulX4,
                startDay = 1,
                isCurrent = false,
                unavailableReason = null,
                mode = SplitCatalogMode.BROWSE,
                selected = false,
                onApply = { applied = true },
                onInfo = {},
                onToggle = {},
            )
        }
        composeRule.onNodeWithTag("split_aplicar_ul_x4").performClick()
        assertTrue(applied)
    }

    @Test
    fun infoAbreLaExplicacionDelReparto() {
        var infoRequested = false
        setAtRest {
            SplitCatalogModule(
                split = ulX4,
                startDay = 1,
                isCurrent = false,
                unavailableReason = null,
                mode = SplitCatalogMode.BROWSE,
                selected = false,
                onApply = {},
                onInfo = { infoRequested = true },
                onToggle = {},
            )
        }
        composeRule.onNodeWithContentDescription("Qué es ${ulX4.name}").performClick()
        assertTrue(infoRequested)
    }

    @Test
    fun enCompararDosRepartosHabilitanCompararDos() {
        setAtRest { CompareHost(catalog = listOf(ulX4, pplUl, pplX6)) }
        composeRule.onNodeWithText("Elige dos repartos").assertIsNotEnabled()
        composeRule.onNodeWithText(ulX4.name).performClick()
        composeRule.onNodeWithText("Elige dos repartos").assertIsNotEnabled()
        composeRule.onNodeWithText(pplUl.name).performClick()
        composeRule.onNodeWithText("Comparar 2").assertIsEnabled()
    }

    @Test
    fun filtrarPorEtiquetaDejaSoloLosSuyos() {
        setAtRest { TagHost(catalog = listOf(pplX6, broSplit)) }
        composeRule.onNodeWithText(broSplit.name).assertExists()
        composeRule.onNodeWithText("Alta frecuencia").performClick()
        composeRule.onNodeWithText(pplX6.name).assertExists()
        composeRule.onNodeWithText(broSplit.name).assertDoesNotExist()
    }

    /** Página en modo comparar con el estado real de los elegidos: cada toque añade o quita, y un tercero sustituye al primero. */
    @Composable
    private fun CompareHost(catalog: List<SplitTemplate>) {
        var ids by remember { mutableStateOf(listOf<String>()) }
        SplitCatalogScreen(
            current = null,
            startDay = 1,
            query = "",
            onQueryChange = {},
            selectedTag = null,
            onSelectTag = {},
            splits = catalog,
            customSplit = null,
            unavailableReason = { null },
            isCurrent = { false },
            mode = SplitCatalogMode.COMPARE,
            selectedIds = ids,
            onBack = null,
            onToggleCompareMode = {},
            onToggleMultiMode = {},
            onApply = {},
            onInfo = {},
            onToggleSelect = { split ->
                ids = when {
                    split.id in ids -> ids - split.id
                    ids.size >= 2 -> ids.drop(1) + split.id
                    else -> ids + split.id
                }
            },
            onEditCustom = {},
            onApplyCustom = {},
            onCompare = {},
            onApplySelected = {},
        )
    }

    /** Página con el filtro de etiquetas real: la lista que se pinta es la que deja [filterSplitCatalog] con la etiqueta elegida. */
    @Composable
    private fun TagHost(catalog: List<SplitTemplate>) {
        var tag by remember { mutableStateOf<SplitTag?>(null) }
        SplitCatalogScreen(
            current = null,
            startDay = 1,
            query = "",
            onQueryChange = {},
            selectedTag = tag,
            onSelectTag = { tag = it },
            splits = filterSplitCatalog(catalog, query = "", tag = tag),
            customSplit = null,
            unavailableReason = { null },
            isCurrent = { false },
            mode = SplitCatalogMode.BROWSE,
            selectedIds = emptyList(),
            onBack = null,
            onToggleCompareMode = {},
            onToggleMultiMode = {},
            onApply = {},
            onInfo = {},
            onToggleSelect = {},
            onEditCustom = {},
            onApplyCustom = {},
            onCompare = {},
            onApplySelected = {},
        )
    }
}
