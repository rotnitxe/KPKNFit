package com.example.kpkn.screens.onboarding.design

import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.semantics.SemanticsActions
import androidx.compose.ui.semantics.SemanticsProperties
import androidx.compose.ui.semantics.getOrNull
import androidx.compose.ui.test.assertContentDescriptionEquals
import androidx.compose.ui.test.assertHeightIsAtLeast
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.performClick
import androidx.compose.ui.unit.dp
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

/**
 * La fila-resumen de la página larga con Compose sobre Robolectric: se oye entera aunque el texto se corte para verse, sigue siendo un
 * botón que edita el paso y tiene sitio para la etiqueta y dos líneas de valor.
 *
 * Los cortes por palabras con las tipografías reales se prueban aparte, con sus métricas (`SetupStepSummaryFitTest`): Robolectric
 * no mide los textos con las fuentes de la app.
 */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [34], qualifiers = "w360dp-h900dp", application = android.app.Application::class)
class WizardSummaryRowTest {

    @get:Rule
    val rule = createComposeRule()

    @Test
    fun theSummaryRowIsReadWholeAndCanBeTapped() {
        var edits = 0
        rule.setContent {
            WizardSummaryRow(
                label = "Marcas",
                value = "Sentadilla 120 kg, Press banca 80 kg",
                onClick = { edits++ },
                modifier = Modifier.testTag("row"),
            )
        }
        rule.onNodeWithTag("row").assertContentDescriptionEquals("Marcas: Sentadilla 120 kg, Press banca 80 kg")
        rule.onNodeWithTag("row").performClick()
        assertEquals(1, edits)
        val config = rule.onNodeWithTag("row").fetchSemanticsNode().config
        assertNotNull("sigue siendo un botón", config.getOrNull(SemanticsProperties.Role))
        assertEquals("Editar Marcas", config[SemanticsActions.OnClick].label)
    }

    @Test
    fun aSummaryRowThatCannotBeEditedIsNotClickable() {
        rule.setContent { WizardSummaryRow(label = "Paso", value = "Listo", onClick = null, modifier = Modifier.testTag("row")) }
        assertNull(rule.onNodeWithTag("row").fetchSemanticsNode().config.getOrNull(SemanticsActions.OnClick))
    }

    @Test
    fun theSummaryRowHasRoomForTheLabelAndTwoLinesOfValue() {
        rule.setContent { WizardSummaryRow(label = "Marcas", value = "Sentadilla", onClick = null, modifier = Modifier.testTag("row")) }
        // Etiqueta (18 dp) + dos líneas de valor (2 × 21 dp) + aire (12 dp) = 72 dp con letra normal.
        rule.onNodeWithTag("row").assertHeightIsAtLeast(72.dp)
        val height = rule.onNodeWithTag("row").fetchSemanticsNode().boundsInRoot.height
        assertTrue("alto $height", height >= 72f)
    }
}
