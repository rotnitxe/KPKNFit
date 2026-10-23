package com.example.kpkn.screens.sessioneditor.components

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.semantics.SemanticsProperties
import androidx.compose.ui.test.assertCountEquals
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onAllNodesWithText
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.unit.dp
import com.example.kpkn.data.models.Exercise
import com.example.kpkn.data.models.ExerciseSet
import com.example.kpkn.data.models.RepRange
import com.example.kpkn.data.models.SupersetGroup
import com.example.kpkn.data.models.TrainingMode
import com.example.kpkn.screens.onboarding.design.LocalWizardReducedMotion
import com.example.kpkn.screens.sessioneditor.SessionEditorDropKind
import com.example.kpkn.screens.sessioneditor.SessionEditorDropPlan
import com.example.kpkn.screens.sessioneditor.design.SessionEditorDashedAddButton
import com.example.kpkn.screens.sessioneditor.design.SessionEditorDropHintBanner
import com.example.kpkn.screens.sessioneditor.design.SessionEditorOutsideDropZone
import com.example.kpkn.screens.sessioneditor.design.SessionExerciseUndoNotice
import com.example.kpkn.screens.sessioneditor.design.SessionModuleEdge
import com.example.kpkn.screens.sessioneditor.design.SessionModuleSlot
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

/**
 * ED-A · A3: las señales del arrastre (la frase de arriba, «Sin grupo», el borde del destino) y que no mueven la lista.
 */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [34], application = android.app.Application::class, qualifiers = "w360dp-h900dp-xxhdpi")
class SessionEditorDropSignalsComposeTest {

    @get:Rule
    val rule = createComposeRule()

    private val noop: () -> Unit = {}

    // ───────────────────────────── A3 · las señales del arrastre ─────────────────────────────

    @Test
    fun elAvisoDeArribaDiceCadaDestino() {
        val plans = listOf(
            SessionEditorDropPlan(SessionEditorDropKind.BETWEEN_GROUPS, "p2", null, 0, "Al soltar pasará de «Principal» a «Accesorios»."),
            SessionEditorDropPlan(SessionEditorDropKind.OUT_OF_GROUP, null, null, 1, "Al soltar saldrá de «Principal» y quedará sin grupo."),
            SessionEditorDropPlan(SessionEditorDropKind.INTO_GROUP, "p1", null, 0, "Al soltar entrará en «Principal»."),
            SessionEditorDropPlan(SessionEditorDropKind.INTO_SUPERSET, "p2", "ss", 2, "Al soltar entrará como A3 en la superserie.", "A3"),
            SessionEditorDropPlan(SessionEditorDropKind.INVALID, "p2", "ss", 2, "La superserie ya tiene 4 ejercicios."),
        )
        var plan by mutableStateOf<SessionEditorDropPlan?>(null)
        rule.setContent { SessionEditorDropHintBanner(plan = plan) }

        rule.onNodeWithTag("session_editor_drop_hint").assertDoesNotExist()
        plans.forEach { next ->
            rule.runOnIdle { plan = next }
            rule.waitForIdle()
            rule.onNodeWithText(next.hint).assertIsDisplayed()
            // Un solo aviso a la vez.
            rule.onAllNodesWithText("Al soltar", substring = true).assertCountEquals(if (next.hint.startsWith("Al soltar")) 1 else 0)
        }
        rule.runOnIdle { plan = SessionEditorDropPlan(SessionEditorDropKind.NONE, null, null, null, "") }
        rule.waitForIdle()
        rule.onNodeWithTag("session_editor_drop_hint").assertDoesNotExist()
    }

    @Test
    fun laZonaSinGrupoSoloSeVeCuandoEsElDestino() {
        var visible by mutableStateOf(false)
        rule.setContent {
            Column(Modifier.padding(16.dp)) {
                // Sitio de sobra por encima: la señal se dibuja sobre el borde del primer grupo, hacia arriba.
                Box(Modifier.height(100.dp)) { Text("Cabecera") }
                SessionEditorOutsideDropZone(visible = visible)
                Text("Primer grupo")
            }
        }

        rule.onNodeWithTag("session_editor_drop_outside").assertDoesNotExist()
        rule.runOnIdle { visible = true }
        rule.waitForIdle()
        rule.onNodeWithTag("session_editor_drop_outside").assertExists()
        rule.onNodeWithText("Sin grupo").assertIsDisplayed()
        rule.onNodeWithText("Soltar aquí").assertIsDisplayed()
    }

    @Test
    fun laSenalDeSinGrupoYElBordeDelDestinoNoMuevenLaLista() {
        var visible by mutableStateOf(false)
        var highlighted by mutableStateOf(false)
        rule.setContent {
            Column(Modifier.fillMaxWidth()) {
                SessionEditorOutsideDropZone(visible = visible)
                SessionModuleSlot(edge = SessionModuleEdge.ONLY, highlighted = highlighted, modifier = Modifier.testTag("modulo")) {
                    Text("Principal", modifier = Modifier.height(48.dp))
                }
                Box(Modifier.testTag("debajo").height(10.dp))
            }
        }
        val before = rule.onNodeWithTag("modulo").fetchSemanticsNode().boundsInRoot
        val belowBefore = rule.onNodeWithTag("debajo").fetchSemanticsNode().boundsInRoot

        rule.runOnIdle {
            visible = true
            highlighted = true
        }
        rule.waitForIdle()

        // ED-A · A3: lista estable. Al cambiar de destino nada de lo que hay debajo cambia de sitio ni de tamaño.
        assertEquals(before, rule.onNodeWithTag("modulo").fetchSemanticsNode().boundsInRoot)
        assertEquals(belowBefore, rule.onNodeWithTag("debajo").fetchSemanticsNode().boundsInRoot)
    }

    @Test
    fun elBotonDeAnadirDelGrupoEsUnRecuadroConElNombreDelGrupo() {
        var added = 0
        rule.setContent { SessionEditorDashedAddButton(text = "Añadir a Principal", onClick = { added++ }) }

        rule.onNodeWithText("Añadir a Principal").assertIsDisplayed()
        rule.onNodeWithText("Añadir a Principal").performClick()
        assertEquals(1, added)
        val size = rule.onNodeWithText("Añadir a Principal").fetchSemanticsNode().size
        assertTrue(size.width > 0)
    }

}
