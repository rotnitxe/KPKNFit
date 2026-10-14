package com.example.kpkn.screens.sessioneditor

import android.app.Application
import androidx.compose.material3.MaterialTheme
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.SemanticsProperties
import androidx.compose.ui.test.SemanticsMatcher
import androidx.compose.ui.test.assertIsNotEnabled
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNodeWithContentDescription
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performScrollTo
import com.example.kpkn.screens.sessioneditor.components.sheets.CoverSheetBody
import com.example.kpkn.screens.sessioneditor.components.sheets.RulesSheet
import com.example.kpkn.screens.sessioneditor.components.sheets.SessionClonerSheet
import com.example.kpkn.screens.sessioneditor.components.sheets.SupersetManagerBody
import org.junit.Assert.assertEquals
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

/**
 * Las hojas del editor (SE7) en Robolectric, montadas por sus cuerpos: los toques dentro de una `KpknSheet` no llegan en las pruebas,
 * así que se prueba lo que la hoja pinta. Cada prueba comprueba una acción y que su efecto llega al callback con el dato correcto.
 */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [34], qualifiers = "w360dp-h1800dp-xxhdpi", application = Application::class)
class SessionEditorSheetsTest {

    @get:Rule
    val composeRule = createComposeRule()

    @Test
    fun cambiar_un_interruptor_llama_con_el_valor() {
        val recorded = mutableListOf<Boolean?>()
        composeRule.setContent {
            MaterialTheme {
                RulesSheet(
                    uiState = SessionEditorSheetSamples.rulesState(),
                    onApplyRules = { ApplyRulesOutcome.NoChanges },
                    onRuleDefaultsChange = { _, _, _, _, _, _, _, _, applyToNew, _ -> recorded += applyToNew },
                    onRuleLimitsChange = { _, _ -> },
                    onAdvancedRuleLimitsChange = { _, _, _, _ -> },
                    onApplyGlobalIntensityAdjustment = { _, _, _ -> },
                    setTargetDuration = {},
                    setPartTargetDuration = { _, _ -> },
                    setExerciseTargetDuration = { _, _ -> },
                )
            }
        }
        // El único interruptor de la pestaña «Reglas» es «Aplicar a nuevos elementos», apagado en la muestra: al tocarlo llega «encendido».
        composeRule.onNode(SemanticsMatcher.expectValue(SemanticsProperties.Role, Role.Switch)).performScrollTo().performClick()
        assertEquals(listOf<Boolean?>(true), recorded)
    }

    @Test
    fun elegir_una_muestra_de_portada_llama_con_su_id() {
        val chosen = mutableListOf<String>()
        composeRule.setContent {
            MaterialTheme {
                CoverSheetBody(
                    session = SessionEditorSheetSamples.session,
                    onSelectGradient = { chosen += it },
                )
            }
        }
        composeRule.onNodeWithContentDescription("Degradado Lagoon").performClick()
        assertEquals(listOf("gradient://lagoon"), chosen)
    }

    @Test
    fun copiar_sin_elegidas_deja_la_principal_apagada() {
        composeRule.setContent {
            MaterialTheme {
                SessionClonerSheet(
                    uiState = SessionEditorSheetSamples.cloneState,
                    onCloneCurrentToTargets = { _, _, _ -> },
                    onImportFromSource = { _, _, _ -> },
                )
            }
        }
        composeRule.onNodeWithText("Copiar a sesiones").assertIsNotEnabled()
    }

    @Test
    fun deshacer_superserie_pide_confirmacion_antes_de_quitar_nada() {
        val removed = mutableListOf<String>()
        composeRule.setContent {
            MaterialTheme {
                SupersetManagerBody(
                    exercises = SessionEditorSheetSamples.supersetMembers,
                    partId = "p-empuje",
                    supersetId = "g1",
                    restBetweenSeconds = 60,
                    restAfterSeconds = 120,
                    onUpdateRestBetween = { _, _, _ -> },
                    onUpdateRestAfter = { _, _, _ -> },
                    onRemove = { _, id -> removed += id },
                    onDismiss = {},
                )
            }
        }
        composeRule.onNodeWithText("Deshacer superserie").performScrollTo().performClick()
        composeRule.onNodeWithText("¿Deshacer la superserie?").assertExists()
        assertEquals(emptyList<String>(), removed)

        // Confirmar quita a todos los miembros, del último al primero: el último que queda disuelve el grupo.
        composeRule.onNodeWithText("Deshacer").performClick()
        assertEquals(listOf("ex-incline", "ex-bench"), removed)
    }
}
