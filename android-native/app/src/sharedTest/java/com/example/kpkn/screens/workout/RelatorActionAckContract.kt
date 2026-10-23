package com.example.kpkn.screens.workout

import androidx.activity.ComponentActivity
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.width
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.semantics.SemanticsActions
import androidx.compose.ui.test.junit4.createAndroidComposeRule
import androidx.compose.ui.test.onAllNodesWithText
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performSemanticsAction
import androidx.compose.ui.test.performTouchInput
import androidx.compose.ui.test.click
import androidx.compose.ui.text.TextLayoutResult
import androidx.compose.ui.unit.dp
import com.example.kpkn.data.models.Exercise
import com.example.kpkn.data.models.WeekVariant
import com.example.kpkn.domain.workout.LoadSuggestionEngine
import com.example.kpkn.ui.theme.KPKNTheme
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test

/** Clicks reales del módulo y contrato Ack; fixture UI aislado, sin escribir datos de la app. */
abstract class RelatorActionAckContract {
    @get:Rule val rule = createAndroidComposeRule<ComponentActivity>()
    private val idle = "Hoy conviene ajustar. Ajusta carga."
    private val action = RelatorAssistAction(RelatorAssistActionKind.ADJUST_LOAD, "Ajusta carga",
        exerciseId = "press", setIndex = 0, span = "Ajusta carga", loadDeltaPercent = -5.0)
    private var phase by mutableStateOf("working")
    private var copy by mutableStateOf(idle)
    private val state = WorkoutUiState(startTimeMs = 1L, activeMode = WeekVariant.B, currentExerciseIdx = 0,
        currentSetIdx = 0, activeStepKey = "press_0")
    private var currentExercise by mutableStateOf(Exercise(id = "press", name = "Press A",
        canonicalExerciseId = "press-a", catalogConfigurationId = "cfg-a", occurrenceId = "same-slot"))
    private var draft: Double? = 41.0
    private var calls = 0
    private var fallbackCalls = 0
    private var pops = 0
    private var undo: RelatorAssistAction? = null

    private fun show(applied: Boolean = true, knownWeights: Boolean = true) {
        val previous = draft
        rule.setContent {
            KPKNTheme {
                Box(Modifier.width(320.dp).height(112.dp)) {
                    WorkoutLiveRelatorLine(
                        text = copy, phaseKey = phase, actions = listOf(action), reduceMotionOverride = true,
                        onAction = { fallbackCalls += 1; calls += 1 },
                        onLoadPop = { pops += 1 },
                        actionContext = RelatorActionUiContext(relatorActionScopeKey(state, currentExercise)) { clicked ->
                            calls += 1
                            phase = "assist_confirm"
                            if (clicked.undoPreviousLoad) {
                                undo = clicked
                                draft = previous
                                copy = "Borrador restaurado."
                                RelatorAssistAck(clicked.kind, applied = true)
                            } else if (!applied) {
                                copy = "No se pudo ajustar la carga."
                                RelatorAssistAck(clicked.kind, applied = false)
                            } else {
                                // Same rounding contract as the real VM; no kg exists in action label/payload.
                                draft = LoadSuggestionEngine.roundLoad((draft ?: 41.0) * 0.95)
                                copy = "Confirmación del motor."
                                RelatorAssistAck(clicked.kind, applied = true,
                                    appliedWeightKg = draft.takeIf { knownWeights },
                                    previousWeightKg = previous.takeIf { knownWeights }, canUndoLoad = true)
                            }
                        },
                    )
                }
            }
        }
        rule.waitForIdle()
    }

    private fun tapSpan(phrase: String, span: String) {
        val node = rule.onNodeWithText(phrase, useUnmergedTree = true)
        val results = mutableListOf<TextLayoutResult>()
        node.performSemanticsAction(SemanticsActions.GetTextLayoutResult) { it(results) }
        assertTrue("El tramo debe pertenecer a la frase visible", phrase.contains(span))
        val offset = phrase.indexOf(span) + span.length / 2
        val center = results.single().getBoundingBox(offset).center
        node.performTouchInput { click(center) }
    }

    private fun awaitCopy(phrase: String) {
        rule.waitUntil(5_000) {
            rule.onAllNodesWithText(phrase, useUnmergedTree = true).fetchSemanticsNodes().isNotEmpty()
        }
    }

    @Test fun ajustePorcentualSobreviveAckYDeshace41A39SinCargaInventada() {
        show()
        tapSpan(idle, "Ajusta carga")
        val working = "El equipo está cargando los discos de 39\u00A0kg…"
        rule.onNodeWithText(working, useUnmergedTree = true).assertExists()
        rule.runOnIdle { assertEquals("assist_confirm", phase); assertEquals(39.0, draft!!, 0.0); assertEquals(1, calls) }
        rule.onNodeWithText(working, useUnmergedTree = true).performTouchInput { click() }
        rule.runOnIdle { assertEquals(1, calls) }
        val done = "Listo: la serie 1 va con 39\u00A0kg. ↶ vuelve a 41."
        awaitCopy(done)
        rule.runOnIdle { assertEquals(1, pops); assertEquals(0, fallbackCalls) }
        tapSpan(done, "vuelve a 41")
        rule.runOnIdle {
            assertEquals(41.0, draft!!, 0.0)
            assertTrue(undo!!.undoPreviousLoad)
            assertNull(undo!!.weightKg)
            assertNull(undo!!.loadDeltaPercent)
            assertEquals(2, calls)
            assertEquals(0, fallbackCalls)
        }
    }

    @Test fun ackAppliedFalseNoAnunciaHechoNiCambiaBorrador() {
        show(applied = false)
        tapSpan(idle, "Ajusta carga")
        rule.onNodeWithText("No se pudo ajustar la carga.", useUnmergedTree = true).assertExists()
        rule.runOnIdle {
            assertEquals(41.0, draft!!, 0.0); assertEquals(1, calls)
            assertEquals(0, fallbackCalls); assertEquals(0, pops)
        }
        rule.onAllNodesWithText("Listo:", substring = true).fetchSemanticsNodes().let { assertTrue(it.isEmpty()) }
        rule.onAllNodesWithText("cargando los discos", substring = true).fetchSemanticsNodes().let { assertTrue(it.isEmpty()) }
    }

    @Test fun undoSinCargaConocidaDiceDeshacerYRestauraAusenciaDeDraft() {
        draft = null
        show(knownWeights = false)
        tapSpan(idle, "Ajusta carga")
        rule.onNodeWithText("Ajustando la carga…", useUnmergedTree = true).assertExists()
        awaitCopy("Carga ajustada. ↶ Deshacer.")
        tapSpan("Carga ajustada. ↶ Deshacer.", "Deshacer")
        rule.runOnIdle { assertNull(draft); assertTrue(undo!!.undoPreviousLoad); assertNull(undo!!.weightKg) }
    }

    @Test fun reemplazarEjercicioConMismosIdsIndicesYFaseCancelaTrabajoYUndo() {
        show()
        tapSpan(idle, "Ajusta carga")
        val before = relatorActionScopeKey(state, currentExercise)
        rule.runOnIdle {
            currentExercise = currentExercise.copy(name = "Press B", canonicalExerciseId = "press-b", catalogConfigurationId = "cfg-b")
            copy = "Nuevo ejercicio."
            // Same slot, occurrence, index, step and ASSIST_CONFIRM: only real exercise identity changed.
            assertEquals("press", currentExercise.id)
            assertEquals("same-slot", currentExercise.occurrenceId)
            assertEquals(0, state.currentExerciseIdx)
            assertEquals(0, state.currentSetIdx)
            assertEquals("press_0", state.activeStepKey)
            assertEquals("assist_confirm", phase)
            assertFalse(before == relatorActionScopeKey(state, currentExercise))
        }
        rule.onNodeWithText("Nuevo ejercicio.", useUnmergedTree = true).assertExists()
        rule.onAllNodesWithText("vuelve a", substring = true).fetchSemanticsNodes().let { assertTrue(it.isEmpty()) }
        rule.onAllNodesWithText("cargando los discos", substring = true).fetchSemanticsNodes().let { assertTrue(it.isEmpty()) }
    }
}
