package com.example.kpkn.screens.onboarding.design.entreno

import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.semantics.SemanticsActions
import androidx.compose.ui.semantics.SemanticsProperties
import androidx.compose.ui.test.assertContentDescriptionEquals
import androidx.compose.ui.test.click
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.performSemanticsAction
import androidx.compose.ui.test.performTouchInput
import androidx.compose.ui.test.swipe
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

/**
 * La regla de conteo compartida (repeticiones de 0 a 30 en Calistenia): cada valor que declara es un entero dentro del rango,
 * tanto con el dedo como con las acciones de accesibilidad, y sin valor declarado el arranque no es una respuesta.
 */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [34], application = android.app.Application::class, qualifiers = "w420dp-h1600dp")
class CountRulerTest {

    @get:Rule
    val rule = createComposeRule()

    private val emitted = mutableListOf<Int>()
    private var value by mutableStateOf<Int?>(null)

    private fun show(initial: Int?, range: IntRange = 0..30) {
        value = initial
        rule.setContent {
            CountRuler(
                range = range,
                value = value,
                onValue = { emitted += it; value = it },
                unitLabel = "Repeticiones",
                tag = TAG,
            )
        }
    }

    private fun ruler() = rule.onNodeWithTag(TAG)

    private fun action(label: String) {
        val actions = ruler().fetchSemanticsNode().config[SemanticsActions.CustomActions]
        rule.runOnUiThread { assertTrue(actions.first { it.label == label }.action()) }
        rule.waitForIdle()
    }

    @Test
    fun setProgressDeclaresWholeCountsInsideTheRange() {
        show(null)
        ruler().performSemanticsAction(SemanticsActions.SetProgress) { it(12.4f) }
        rule.waitForIdle()
        assertEquals(12, value)
        ruler().performSemanticsAction(SemanticsActions.SetProgress) { it(99f) }
        rule.waitForIdle()
        assertEquals(30, value)
        ruler().performSemanticsAction(SemanticsActions.SetProgress) { it(-4f) }
        rule.waitForIdle()
        assertEquals(0, value)
        assertTrue("valor fuera de rango: $emitted", emitted.all { it in 0..30 })
    }

    @Test
    fun accessibilityActionsStepOneWholeCountAtATime() {
        show(5)
        action("Subir uno")
        assertEquals(6, value)
        action("Bajar uno")
        action("Bajar uno")
        assertEquals(4, value)
        assertTrue("valor fuera de rango: $emitted", emitted.all { it in 0..30 })
    }

    @Test
    fun theActionsStopAtTheEdgesOfTheRange() {
        show(0)
        action("Bajar uno")
        assertEquals(0, value)
        assertTrue("sale del rango: $emitted", emitted.all { it in 0..30 })
    }

    @Test
    fun aDragLandsOnWholeCountsInsideTheRange() {
        show(null)
        // Arrastrar hacia la izquierda sube el valor, como en la regla de marcas.
        ruler().performTouchInput {
            swipe(Offset(width * 0.8f, height / 2f), Offset(width * 0.2f, height / 2f), durationMillis = 400)
        }
        rule.waitForIdle()
        assertTrue("sin emisiones", emitted.isNotEmpty())
        assertTrue("valor fuera de rango: $emitted", emitted.all { it in 0..30 })
        assertTrue("el arrastre no sube el valor: $emitted", emitted.max() > 15)
        // Escalón a escalón y en orden: sin saltos hacia atrás.
        val ordered = emitted.distinct()
        assertEquals(ordered.sorted(), ordered)
    }

    @Test
    fun theStartingPositionIsNotAnAnswerUntilTheFirstContact() {
        show(null)
        rule.waitForIdle()
        assertTrue("sin contacto no hay respuesta: $emitted", emitted.isEmpty())
        ruler().performTouchInput { click(Offset(width / 2f, height / 2f)) }
        rule.waitForIdle()
        // El centro de 0 a 30 es 15: tocarlo declara ese entero.
        assertEquals(15, value)
    }

    @Test
    fun theRulerAnnouncesItsCountAndItsRange() {
        show(7)
        ruler().assertContentDescriptionEquals("Repeticiones: 7")
        val range = ruler().fetchSemanticsNode().config[SemanticsProperties.ProgressBarRangeInfo]
        assertEquals(7f, range.current, 0f)
        assertEquals(0f, range.range.start, 0f)
        assertEquals(30f, range.range.endInclusive, 0f)
    }

    private companion object {
        const val TAG = "count-ruler-test"
    }
}
