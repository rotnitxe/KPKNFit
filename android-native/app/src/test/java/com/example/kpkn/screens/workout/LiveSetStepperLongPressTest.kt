package com.example.kpkn.screens.workout

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.longClick
import androidx.compose.ui.test.onAllNodesWithText
import androidx.compose.ui.test.onNodeWithContentDescription
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performTouchInput
import com.example.kpkn.ui.theme.KPKNTheme
import org.junit.Assert.assertEquals
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import org.robolectric.annotation.GraphicsMode

/**
 * La pulsación larga en el paso de la serie (LV11, A1): abre las opciones de ESA serie, tanto en un paso normal como en el de un ejercicio
 * unilateral (la etiqueta y cada lado), y tocar sigue seleccionando la serie sin abrir nada.
 */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [34], qualifiers = "w360dp-h880dp-xxhdpi", application = android.app.Application::class)
@GraphicsMode(GraphicsMode.Mode.NATIVE)
class LiveSetStepperLongPressTest {

    @get:Rule
    val rule = createComposeRule()

    private val longPressed = mutableListOf<Int>()
    private val selected = mutableListOf<Int>()

    private fun show(elements: List<TimelineElement>, active: Int = 0, withLongPress: Boolean = true) {
        rule.setContent {
            KPKNTheme {
                Box(Modifier.fillMaxSize().background(Color.Black)) {
                    WorkoutSetPager(
                        elements = elements,
                        activeElementIndex = active,
                        completedCount = 0,
                        totalCount = elements.size,
                        onSelectPage = { selected += it },
                        onLongPressPage = if (withLongPress) { page -> longPressed += page } else null,
                    )
                }
            }
        }
        rule.waitForIdle()
    }

    private val bilateral = listOf(
        TimelineElement.BilateralSet(pageIndex = 0, label = "S1", state = WorkoutSetCardVisualState.COMPLETED),
        TimelineElement.BilateralSet(pageIndex = 1, label = "S2", state = WorkoutSetCardVisualState.ACTIVE),
        TimelineElement.BilateralSet(pageIndex = 2, label = "S3", state = WorkoutSetCardVisualState.FUTURE),
    )

    private val unilateral = listOf(
        TimelineElement.UnilateralSet(
            setLabel = "S1",
            leftPageIndex = 0,
            leftState = WorkoutSetCardVisualState.COMPLETED,
            rightPageIndex = 0,
            rightState = WorkoutSetCardVisualState.ACTIVE,
        ),
        TimelineElement.UnilateralSet(
            setLabel = "S2",
            leftPageIndex = 1,
            leftState = WorkoutSetCardVisualState.FUTURE,
            rightPageIndex = 1,
            rightState = WorkoutSetCardVisualState.FUTURE,
        ),
    )

    @Test
    fun aLongPressOnAStepOpensTheOptionsOfThatSet() {
        show(bilateral, active = 1)

        rule.onNodeWithText("S3").performTouchInput { longClick() }
        // La serie hecha enseña su marca (con su nombre como descripción), no el texto.
        rule.onNodeWithContentDescription("S1").performTouchInput { longClick() }

        assertEquals(listOf(2, 0), longPressed)
        assertEquals("pulsar largo no selecciona la serie", emptyList<Int>(), selected)
    }

    @Test
    fun aTapStillSelectsTheSetAndDoesNotOpenTheOptions() {
        show(bilateral, active = 1)

        rule.onNodeWithText("S3").performClick()

        assertEquals(listOf(2), selected)
        assertEquals(emptyList<Int>(), longPressed)
    }

    @Test
    fun aLongPressOnTheLabelAndOnEachSideOfAUnilateralStepOpensTheOptionsOfThatSet() {
        show(unilateral, active = 0)

        // La etiqueta de la segunda serie y, en la primera, sus dos lados (el izquierdo ya hecho, con su marca; el derecho en curso).
        rule.onNodeWithText("S2").performTouchInput { longClick() }
        rule.onNodeWithContentDescription("L").performTouchInput { longClick() }
        rule.onAllNodesWithText("R")[0].performTouchInput { longClick() }

        assertEquals("la etiqueta y los dos lados abren la serie correcta", listOf(1, 0, 0), longPressed)
        assertEquals(emptyList<Int>(), selected)
    }

    @Test
    fun aTapOnASideOfAUnilateralStepStillSelectsThatSet() {
        show(unilateral, active = 0)

        rule.onAllNodesWithText("R")[0].performClick()

        assertEquals(listOf(0), selected)
        assertEquals(emptyList<Int>(), longPressed)
    }

    @Test
    fun withoutAHandlerALongPressDoesNothing() {
        show(bilateral, active = 1, withLongPress = false)

        rule.onNodeWithText("S3").performTouchInput { longClick() }

        assertEquals(emptyList<Int>(), longPressed)
    }
}
