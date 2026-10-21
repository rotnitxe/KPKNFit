package com.example.kpkn.ui.design

import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.semantics.SemanticsActions
import androidx.compose.ui.semantics.SemanticsProperties
import androidx.compose.ui.semantics.getOrNull
import androidx.compose.ui.test.SemanticsMatcher
import androidx.compose.ui.test.assert
import androidx.compose.ui.test.hasSetTextAction
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNodeWithContentDescription
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performImeAction
import androidx.compose.ui.test.performTextReplacement
import androidx.compose.ui.test.performTouchInput
import org.junit.Assert.assertEquals
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

/** Celda numérica (SE1): toca para editar, arrastra para sumar o restar y expone sus acciones de TalkBack. */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [34], application = android.app.Application::class)
class KpknNumberCellTest {

    @get:Rule
    val rule = createComposeRule()

    @Test
    fun tocarUnaCeldaDeTextoEntraEnEdicionYHechoLlamaACommit() {
        val committed = mutableListOf<String>()
        rule.setContent {
            KpknNumberCell(
                value = "80",
                input = KpknCellInput.Text(onCommit = { committed += it }),
                contentDescription = "Serie 2, kilos, 80",
            )
        }
        rule.onNodeWithContentDescription("Serie 2, kilos, 80").performClick()
        rule.onNode(hasSetTextAction()).performTextReplacement("82,5")
        rule.onNode(hasSetTextAction()).performImeAction()
        assertEquals(listOf("82,5"), committed)
    }

    @Test
    fun unaCeldaDeSelectorLlamaAOnOpen() {
        var opened = 0
        rule.setContent {
            KpknNumberCell(
                value = "2:30",
                input = KpknCellInput.Picker(onOpen = { opened++ }),
                contentDescription = "Descanso, 2:30",
            )
        }
        rule.onNodeWithContentDescription("Descanso, 2:30").performClick()
        assertEquals(1, opened)
    }

    @Test
    fun arrastrarSetentaYDosDpALaDerechaLlamaDosVecesAOnStepMasUno() {
        val steps = mutableListOf<Int>()
        rule.setContent {
            KpknNumberCell(
                value = "80",
                input = KpknCellInput.ReadOnly,
                contentDescription = "Serie 1, kilos, 80",
                modifier = Modifier.testTag("celda"),
                onStep = { steps += it },
            )
        }
        val size = rule.onNodeWithTag("celda").fetchSemanticsNode().size
        val start = Offset(size.width / 2f, size.height / 2f)
        // 72 dp: el umbral de arrastre de la prueba (18 dp) se descuenta antes de contar pasos; quedan 54 dp, dos pasos.
        val setentaYDosDp = 72f * rule.density.density
        rule.onNodeWithTag("celda").performTouchInput {
            down(start)
            moveTo(start + Offset(setentaYDosDp / 2f, 0f))
            moveTo(start + Offset(setentaYDosDp, 0f))
            up()
        }
        assertEquals(listOf(1, 1), steps)
    }

    @Test
    fun lasAccionesDeTalkBackAumentarYDisminuirLlamanAOnStep() {
        val steps = mutableListOf<Int>()
        rule.setContent {
            KpknNumberCell(
                value = "80",
                input = KpknCellInput.ReadOnly,
                contentDescription = "Serie 1, kilos, 80",
                onStep = { steps += it },
            )
        }
        val acciones = rule.onNodeWithContentDescription("Serie 1, kilos, 80")
            .fetchSemanticsNode().config.getOrNull(SemanticsActions.CustomActions).orEmpty()
        assertEquals(listOf("Aumentar", "Disminuir"), acciones.map { it.label })
        acciones.first { it.label == "Aumentar" }.action()
        acciones.first { it.label == "Disminuir" }.action()
        assertEquals(listOf(1, -1), steps)
    }

    @Test
    fun conErrorLoExponeLaSemantica() {
        rule.setContent {
            KpknNumberCell(
                value = "300",
                input = KpknCellInput.ReadOnly,
                contentDescription = "Serie 3, kilos, 300",
                error = true,
            )
        }
        rule.onNodeWithContentDescription("Serie 3, kilos, 300")
            .assert(SemanticsMatcher.expectValue(SemanticsProperties.StateDescription, "No válido"))
    }
}
