package com.example.kpkn.screens.progress

import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.ui.test.assertCountEquals
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.hasTestTag
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onAllNodesWithText
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.unit.Density
import androidx.compose.ui.text.TextLayoutResult
import androidx.compose.ui.semantics.SemanticsActions
import androidx.compose.ui.test.performSemanticsAction
import org.junit.Assert.assertFalse
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performScrollTo
import androidx.compose.ui.test.onFirst
import com.example.kpkn.domain.training.MuscleVolumeWindow
import com.example.kpkn.domain.training.VolumeCalculator
import com.example.kpkn.screens.onboarding.design.LocalWizardReducedMotion
import com.example.kpkn.ui.theme.KPKNTheme
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [34], qualifiers = "w360dp-h800dp-mdpi", application = android.app.Application::class)
class MuscleVolumePanelScreenTest {
    @get:Rule val rule = createComposeRule()

    private fun show(name: String, actions: MuscleVolumePanelActions = MuscleVolumePanelActions(), fontScale: Float = 1f) {
        rule.setContent {
            KPKNTheme {
                val density = LocalDensity.current
                CompositionLocalProvider(LocalWizardReducedMotion provides true, LocalDensity provides Density(density.density, fontScale)) {
                    MuscleVolumePanelScreen(MuscleVolumePanelSamples.byName(name), actions)
                }
            }
        }
    }

    @Test
    fun `media semanal se ve entera al ciento treinta por ciento`() {
        show("data", fontScale = 1.3f)
        val layouts = mutableListOf<TextLayoutResult>()
        rule.onNodeWithText("Media semanal", useUnmergedTree = true)
            .performSemanticsAction(SemanticsActions.GetTextLayoutResult) { it(layouts) }
        assertEquals(1, layouts.size)
        val layout = layouts.single()
        assertEquals(1, layout.lineCount)
        assertFalse("La opción debe verse completa", layout.isLineEllipsized(0))
    }

    @Test
    fun `la lista trae una fila por grupo con datos`() {
        show("data")
        VolumeCalculator.standardVolumeMuscles.forEach { muscle ->
            rule.onNodeWithTag("muscle-volume-row-$muscle").assertExists()
        }
        rule.onNodeWithText("Volumen por músculo").assertExists()
        rule.onNodeWithText("Series registradas, no recuperación ni carga.").assertExists()
    }

    @Test
    fun `la fila elegida muestra su desglose con el total del musculo`() {
        show("data")
        rule.onNodeWithTag("muscle-volume-breakdown-Pectorales").assertExists()
        rule.onNodeWithTag("muscle-volume-breakdown-Pectorales").performScrollTo()
        rule.onAllNodes(hasTestTag("muscle-volume-breakdown-Dorsales")).assertCountEquals(0)
        val chest = MuscleVolumePanelSamples.byName("data").panel!!.row("Pectorales")!!
        assertTrue(chest.contributors.isNotEmpty())
        assertEquals(chest.sets!!, chest.contributors.sumOf { it.sets }, 1e-9)
        rule.onNodeWithText("Total del músculo").assertExists()
    }

    @Test
    fun `tocar una fila avisa al modelo`() {
        var toggled: String? = null
        show("data", MuscleVolumePanelActions(onToggleMuscle = { toggled = it }))
        rule.onNodeWithTag("muscle-volume-row-Dorsales").performScrollTo().performClick()
        assertEquals("Dorsales", toggled)
    }

    @Test
    fun `sin registros dice sin datos y no pinta filas`() {
        show("empty")
        rule.onNodeWithText("Sin registros en esta ventana").assertExists()
        rule.onAllNodes(hasTestTag("muscle-volume-row-Pectorales")).assertCountEquals(0)
    }

    @Test
    fun `los registros sin asignar se avisan con su recuento`() {
        show("unassigned")
        rule.onNodeWithTag("muscle-volume-unassigned").assertExists()
        rule.onNodeWithText("2 registros sin fecha asignable no se cuentan.").assertExists()
    }

    @Test
    fun `un musculo sin series en una ventana con registros se muestra a cero y no sin datos`() {
        show("sparse")
        rule.onNodeWithTag("muscle-volume-row-Bíceps").performScrollTo().assertIsDisplayed()
        rule.onAllNodesWithText("Sin series registradas en esta ventana").onFirst().assertExists()
        rule.onAllNodesWithText("Sin datos en esta ventana").assertCountEquals(0)
    }

    @Test
    fun `cambiar la vista y la ventana llega a las acciones`() {
        var view: MuscleVolumeView? = null
        var window: MuscleVolumeWindow? = null
        show("data", MuscleVolumePanelActions(onView = { view = it }, onWindow = { window = it }))
        rule.onNodeWithText("Cuerpo").performClick()
        assertEquals(MuscleVolumeView.BODY, view)
        rule.onNodeWithText("12 semanas").performScrollTo().performClick()
        assertEquals(MuscleVolumeWindow.WEEKS_12, window)
    }

    @Test
    fun `el cuerpo expone una descripcion resumen y una region por grupo con zona`() {
        show("body")
        rule.onNodeWithTag("muscle-volume-body").assertExists()
        listOf("Pectorales", "Deltoides", "Bíceps", "Abdomen", "Core", "Cuádriceps", "Pantorrillas")
            .forEach { rule.onNodeWithTag("muscle-volume-region-$it").assertExists() }
        rule.onNodeWithTag("muscle-volume-legend").assertExists()
    }

    @Test
    fun `tocar una region del cuerpo avisa al modelo`() {
        var region: String? = null
        show("body", MuscleVolumePanelActions(onSelectRegion = { region = it }))
        rule.onNodeWithTag("muscle-volume-region-Cuádriceps").performScrollTo().performClick()
        assertEquals("Cuádriceps", region)
    }

    @Test
    fun `el cuerpo conserva los ajustes de series y no ofrece recuperacion retirada`() {
        show("body")
        rule.onNodeWithTag("muscle-volume-controls").assertExists()
        rule.onAllNodes(hasTestTag("muscle-volume-metric")).assertCountEquals(0)
        rule.onNodeWithTag("muscle-volume-body-detail").assertExists()
    }

    @Test
    fun `el cuerpo sin datos nombra la region como sin datos`() {
        show("body_empty")
        rule.onNodeWithTag("muscle-volume-body").assertExists()
        rule.onNodeWithText("Sin registros en esta ventana: no hay series que repartir.").assertExists()
    }

    @Test
    fun `cargando muestra un esqueleto y ninguna cifra`() {
        show("loading")
        rule.onAllNodes(hasTestTag("muscle-volume-row-Pectorales")).assertCountEquals(0)
        rule.onNodeWithText("Volumen por músculo").assertExists()
    }
}
