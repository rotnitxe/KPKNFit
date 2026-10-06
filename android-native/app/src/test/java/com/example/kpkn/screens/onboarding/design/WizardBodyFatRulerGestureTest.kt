package com.example.kpkn.screens.onboarding.design

import androidx.compose.foundation.layout.height
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
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
import androidx.compose.ui.unit.dp
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

/**
 * Gestos y semántica reales de la regla de grasa corporal, con Compose sobre Robolectric (la tubería de punteros
 * de verdad, sin dispositivo): tocar un punto, arrastrar de punta a punta, la acción `setProgress` de TalkBack y
 * qué se anuncia (`contentDescription`, `stateDescription`, rango y pasos).
 *
 * Reglas que fija: el primer contacto SIEMPRE se emite (tocar el valor de arranque también lo declara), un entero
 * que no cambia no se vuelve a emitir, cada entero del arrastre se emite una sola vez y en orden, y el valor final
 * se entrega una vez al soltar.
 */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [34], application = android.app.Application::class)
class WizardBodyFatRulerGestureTest {

    @get:Rule
    val rule = createComposeRule()

    private val changes = mutableListOf<Int>()
    private val finished = mutableListOf<Int>()
    private var shown by mutableStateOf(25.0)
    private var declared by mutableStateOf(false)

    private fun show(initial: Double = 25.0, isDeclared: Boolean = false) {
        shown = initial
        declared = isDeclared
        rule.setContent {
            WizardBodyFatRuler(
                percent = shown,
                declared = declared,
                onPercentChange = { value ->
                    changes += value
                    shown = value.toDouble()
                    declared = true
                },
                onPercentChangeFinished = { value -> finished += value },
                modifier = Modifier.height(380.dp),
            )
        }
    }

    @Test
    fun semanticsDescribeASliderForTalkBack() {
        show(25.0, isDeclared = true)
        val node = rule.onNodeWithTag(BODY_FAT_RULER_TAG)
        node.assertContentDescriptionEquals("Porcentaje de grasa corporal")
        val config = node.fetchSemanticsNode().config
        assertEquals("25 por ciento", config[SemanticsProperties.StateDescription])
        val range = config[SemanticsProperties.ProgressBarRangeInfo]
        assertEquals(25f, range.current, 0f)
        assertEquals(5f, range.range.start, 0f)
        assertEquals(50f, range.range.endInclusive, 0f)
        assertEquals(44, range.steps)
        assertTrue(config.contains(SemanticsActions.SetProgress))
        // Declarado: no ofrece «usar el valor mostrado».
        assertFalse(config.contains(SemanticsActions.OnClick))
    }

    @Test
    fun anUndeclaredRulerOffersToUseTheShownValueForTalkBack() {
        show(25.0, isDeclared = false)
        val config = rule.onNodeWithTag(BODY_FAT_RULER_TAG).fetchSemanticsNode().config
        assertTrue(config.contains(SemanticsActions.OnClick))
        rule.onNodeWithTag(BODY_FAT_RULER_TAG).performSemanticsAction(SemanticsActions.OnClick)
        rule.waitForIdle()
        assertEquals(listOf(25), changes)
        assertEquals(listOf(25), finished)
    }

    @Test
    fun setProgressMovesToTheRoundedValueAndFinishes() {
        show()
        rule.onNodeWithTag(BODY_FAT_RULER_TAG).performSemanticsAction(SemanticsActions.SetProgress) { it(30.4f) }
        rule.waitForIdle()
        assertEquals(listOf(30), changes)
        assertEquals(listOf(30), finished)
    }

    @Test
    fun tappingATickEmitsItAndFinishesOnce() {
        show()
        rule.onNodeWithTag(BODY_FAT_RULER_TAG).performTouchInput { click(Offset(width / 2f, 0f)) }
        rule.waitForIdle()
        // El extremo de arriba es 5 %.
        assertEquals(listOf(5), changes)
        assertEquals(listOf(5), finished)
    }

    @Test
    fun tappingTheBottomEndGivesFifty() {
        show()
        rule.onNodeWithTag(BODY_FAT_RULER_TAG).performTouchInput { click(Offset(width / 2f, height - 1f)) }
        rule.waitForIdle()
        assertEquals(listOf(50), changes)
        assertEquals(listOf(50), finished)
    }

    @Test
    fun theFirstTouchOnTheStartingValueStillDeclaresIt() {
        // 27,5 es el centro exacto de la pista: el toque en el centro cae en 28 (se redondea hacia arriba).
        show(28.0, isDeclared = false)
        rule.onNodeWithTag(BODY_FAT_RULER_TAG).performTouchInput { click(Offset(width / 2f, height / 2f)) }
        rule.waitForIdle()
        assertEquals("el valor no cambia pero se emite igual", listOf(28), changes)
        assertEquals(listOf(28), finished)
    }

    @Test
    fun aDeclaredRulerDoesNotReEmitTheSameValueButStillFinishes() {
        show(28.0, isDeclared = true)
        rule.onNodeWithTag(BODY_FAT_RULER_TAG).performTouchInput { click(Offset(width / 2f, height / 2f)) }
        rule.waitForIdle()
        assertTrue("sin cambio de entero no hay emisión: $changes", changes.isEmpty())
        assertEquals(listOf(28), finished)
    }

    @Test
    fun draggingDownEmitsEachIntegerOnceInOrderAndFinishesWithTheLast() {
        show(5.0, isDeclared = true)
        rule.onNodeWithTag(BODY_FAT_RULER_TAG).performTouchInput {
            swipe(Offset(width / 2f, 0f), Offset(width / 2f, height - 1f), durationMillis = 600)
        }
        rule.waitForIdle()
        assertTrue("al menos varios enteros: $changes", changes.size > 10)
        assertEquals("sin repetidos", changes.distinct(), changes)
        assertEquals("en orden creciente", changes.sorted(), changes)
        assertEquals(50, changes.last())
        assertEquals(listOf(50), finished)
    }
}
