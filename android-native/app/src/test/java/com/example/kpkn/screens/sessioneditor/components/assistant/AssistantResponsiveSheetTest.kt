package com.example.kpkn.screens.sessioneditor.components.assistant

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.getUnclippedBoundsInRoot
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNodeWithContentDescription
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performScrollTo
import androidx.compose.ui.unit.Density
import androidx.compose.ui.unit.dp
import com.example.kpkn.data.models.Exercise
import com.example.kpkn.data.models.ExerciseSet
import com.example.kpkn.data.models.Session
import com.example.kpkn.data.models.SessionPart
import com.example.kpkn.screens.sessioneditor.ApplyRulesOutcome
import com.example.kpkn.screens.sessioneditor.AssistantEntry
import com.example.kpkn.screens.sessioneditor.AssistantTab
import com.example.kpkn.screens.sessioneditor.RulesSection
import com.example.kpkn.screens.sessioneditor.SessionEditorUiState
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

/** Req 10: altura 0.72/0.93, asa, entradas reales, letra grande y pantalla baja sin solapes ni recortes. */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [34], application = android.app.Application::class, qualifiers = "w320dp-h580dp-mdpi")
class AssistantResponsiveSheetTest {

    @get:Rule
    val rule = createComposeRule()

    private fun sampleUiState(entry: AssistantEntry? = null): SessionEditorUiState {
        val ex = Exercise(id = "e1", name = "Press banca", sets = listOf(ExerciseSet(id = "s1", targetReps = 8)))
        val session = Session(id = "s1", name = "Torso intenso", parts = listOf(SessionPart(id = "p1", name = "Empujes", exercises = listOf(ex))))
        return SessionEditorUiState(session = session, assistantEntry = entry)
    }

    @Test
    fun fraccionesDeAlturaAsistenteSon072Y093() {
        assertEquals(0.72f, ASSISTANT_MEDIUM_HEIGHT, 0.001f)
        assertEquals(0.93f, ASSISTANT_FULL_HEIGHT, 0.001f)
    }

    @Test
    fun pantallaBajaYLetraGrandeConservaPestanasYAplicarEnReglas() {
        var full by mutableStateOf(false)
        var applied = 0
        val sheetHeightDp = 580f * ASSISTANT_MEDIUM_HEIGHT
        rule.setContent {
            val base = LocalDensity.current
            CompositionLocalProvider(LocalDensity provides Density(base.density, fontScale = 1.4f)) {
                Box(Modifier.fillMaxWidth().height(sheetHeightDp.dp).testTag("as-container")) {
                    AssistantSheetContent(
                        uiState = sampleUiState(AssistantEntry(tab = AssistantTab.METRICS)),
                        templates = emptyList(),
                        archivedUserTemplates = emptyList(),
                        templatesReady = true,
                        corruptTemplateIds = emptyList(),
                        exerciseIndex = emptyMap(),
                        facetsById = emptyMap(),
                        callbacks = AssistantCallbacks(
                            rules = AsRulesActions(
                                onApplyRules = {
                                    applied++
                                    ApplyRulesOutcome.Applied(1)
                                },
                                onRuleDefaultsChange = AsRulesActions.None.onRuleDefaultsChange,
                                onPatchRuleDefaults = AsRulesActions.None.onPatchRuleDefaults,
                                onRuleLimitsChange = AsRulesActions.None.onRuleLimitsChange,
                                onAdvancedRuleLimitsChange = AsRulesActions.None.onAdvancedRuleLimitsChange,
                                onApplyGlobalIntensityAdjustment = AsRulesActions.None.onApplyGlobalIntensityAdjustment,
                                setTargetDuration = AsRulesActions.None.setTargetDuration,
                                setPartTargetDuration = AsRulesActions.None.setPartTargetDuration,
                                setExerciseTargetDuration = AsRulesActions.None.setExerciseTargetDuration,
                                onDistributeTargetAcrossParts = AsRulesActions.None.onDistributeTargetAcrossParts,
                                onApplyRuleTemplate = AsRulesActions.None.onApplyRuleTemplate,
                                onSaveRuleTemplate = AsRulesActions.None.onSaveRuleTemplate,
                                onRenameRuleTemplate = AsRulesActions.None.onRenameRuleTemplate,
                                onDeleteRuleTemplate = AsRulesActions.None.onDeleteRuleTemplate,
                                onApplyTimeCoachSuggestion = AsRulesActions.None.onApplyTimeCoachSuggestion,
                                onDismissTimeCoachSuggestion = AsRulesActions.None.onDismissTimeCoachSuggestion,
                            ),
                        ),
                        fullHeight = full,
                        onFullHeight = { full = it },
                    )
                }
            }
        }
        rule.waitForIdle()
        rule.onNodeWithText("Torso intenso").assertIsDisplayed()
        listOf("Métricas", "Sugerencias", "Plantillas", "Reglas").forEach { label ->
            rule.onNodeWithText(label).performScrollTo().assertExists()
        }
        rule.onNodeWithContentDescription("Cambiar la altura de la hoja").performClick()
        assertTrue(full)

        rule.onNodeWithText("Reglas").performScrollTo().performClick()
        rule.waitForIdle()
        // Al cambiar de pestaña conserva la altura elegida.
        assertTrue(full)

        val applyNode = rule.onNodeWithText("Aplicar a la sesión")
        applyNode.assertIsDisplayed()
        val containerBounds = rule.onNodeWithTag("as-container").getUnclippedBoundsInRoot()
        val applyBounds = applyNode.getUnclippedBoundsInRoot()
        assertTrue("El botón Aplicar no debe sobresalir por abajo", applyBounds.bottom <= containerBounds.bottom + 1.dp)
        applyNode.performClick()
        assertEquals(1, applied)

        rule.onNodeWithContentDescription("Cambiar la altura de la hoja").performClick()
        assertFalse(full)
    }

    @Test
    fun entradasDeReglasYTiempoAbrenLaSeccionEsperada() {
        rule.setContent {
            AssistantSheetContent(
                uiState = sampleUiState(AssistantEntry(tab = AssistantTab.RULES, rules = RulesSection.TIME)),
                templates = emptyList(),
                archivedUserTemplates = emptyList(),
                templatesReady = true,
                corruptTemplateIds = emptyList(),
                exerciseIndex = emptyMap(),
                facetsById = emptyMap(),
                callbacks = AssistantCallbacks(),
                fullHeight = true,
                onFullHeight = {},
            )
        }
        rule.waitForIdle()
        rule.onNodeWithText("Valores").assertIsDisplayed()
        rule.onNodeWithText("Tiempo").assertIsDisplayed()
        rule.onNodeWithText("Aplicar a la sesión").assertDoesNotExist()
    }
}
