package com.example.kpkn.screens.workout

import android.app.Application
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.requiredSize
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.semantics.SemanticsActions
import androidx.compose.ui.test.SemanticsMatcher
import androidx.compose.ui.test.assert
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.assertTextContains
import androidx.compose.ui.test.getBoundsInRoot
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performTextReplacement
import androidx.compose.ui.text.TextLayoutResult
import androidx.compose.ui.unit.Density
import androidx.compose.ui.unit.dp
import com.example.kpkn.data.models.CardioBlockType
import com.example.kpkn.data.models.CardioDetails
import com.example.kpkn.data.models.CardioExecutionStatus
import com.example.kpkn.data.models.CardioIntervalBlock
import com.example.kpkn.data.models.CardioTimerState
import com.example.kpkn.data.models.CardioType
import com.example.kpkn.screens.onboarding.design.LocalWizardReducedMotion
import com.example.kpkn.screens.workout.components.CardioFigureKind
import com.example.kpkn.screens.workout.components.cardioFigureCadence
import com.example.kpkn.screens.workout.components.cardioFigureSpec
import com.example.kpkn.ui.components.LocalKpknOverlayHost
import com.example.kpkn.ui.theme.KPKNTheme
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import org.robolectric.annotation.GraphicsMode

/**
 * El centro del cardio en marcha: el recuadro del reloj (sin anillo) a todo el ancho con el tiempo en una línea y la figura de la
 * actividad; «Añadir pulso» sin reloj abre la hoja y lo guardado se enseña como «Manual · hace…»; con reloj, «Reloj» no es un botón.
 */
@RunWith(RobolectricTestRunner::class)
@GraphicsMode(GraphicsMode.Mode.NATIVE)
@Config(sdk = [34], application = Application::class, qualifiers = "w360dp-h880dp-xxhdpi")
class CardioLiveCenterUiTest {
    @get:Rule val rule = createComposeRule()

    private val intervals = CardioDetails(
        type = CardioType.TREADMILL,
        intervalBlocks = listOf(
            CardioIntervalBlock(id = "w", type = CardioBlockType.WORK, durationSeconds = 120, speedKmh = 12.0),
            CardioIntervalBlock(id = "r", type = CardioBlockType.RECOVER, durationSeconds = 60, speedKmh = 5.0),
        ),
        intervalRounds = 4,
    )
    private val continuous = CardioDetails(type = CardioType.ROW_MACHINE, targetDurationSeconds = 20 * 60)

    private fun running(details: CardioDetails, elapsed: Int = 30) = CardioTimerState(
        exerciseId = "cardio",
        totalSeconds = details.effectiveDurationSeconds(),
        remainingSeconds = details.effectiveDurationSeconds() - elapsed,
        elapsedSeconds = elapsed,
        status = CardioExecutionStatus.RUNNING,
    )

    private fun scene(details: CardioDetails, heightDp: Int = 880, font: Float = 1f, watchHr: Int? = null) {
        rule.setContent {
            val density = LocalDensity.current.density
            CompositionLocalProvider(
                LocalDensity provides Density(density, font),
                LocalWizardReducedMotion provides true,
                LocalKpknOverlayHost provides null,
            ) {
                KPKNTheme {
                    MaterialTheme {
                        // Lo que queda para el espacio de cardio bajo la cabecera de la sesión.
                        Box(Modifier.requiredSize(360.dp, (heightDp - 160).dp).background(Color.Black)) {
                            CardioLiveCard(
                                details = details,
                                completedSet = null,
                                accentColor = Color.White,
                                modifier = Modifier.fillMaxSize(),
                                executionState = running(details),
                                liveHeartRateBpm = watchHr,
                                exerciseName = "Cinta",
                                maxHeartRateBpm = 190,
                                onRecord = { _, _, _ -> true },
                            )
                        }
                    }
                }
            }
        }
        rule.waitForIdle()
    }

    private fun textLayout(tag: String): TextLayoutResult {
        val results = mutableListOf<TextLayoutResult>()
        rule.onNodeWithTag(tag).fetchSemanticsNode().config[SemanticsActions.GetTextLayoutResult].action!!.invoke(results)
        return results.first()
    }

    private fun assertClockFits() {
        rule.onNodeWithTag("cardio-clock").assertIsDisplayed()
        val panel = rule.onNodeWithTag("cardio-clock").getBoundsInRoot()
        val label = rule.onNodeWithTag("cardio-clock-label").getBoundsInRoot()
        assertTrue("el recuadro mide al menos 196 dp de alto: ${panel.bottom - panel.top}", (panel.bottom - panel.top).value >= 195.5f)
        assertTrue("el recuadro va a todo el ancho: ${panel.right - panel.left}", (panel.right - panel.left).value >= 320f)
        assertTrue("el tiempo cabe dentro del recuadro", label.left >= panel.left && label.right <= panel.right &&
            label.top >= panel.top && label.bottom <= panel.bottom)
        val layout = textLayout("cardio-clock-label")
        assertEquals("el tiempo va en una sola línea", 1, layout.lineCount)
        val px = rule.density.density
        val glyphs = layout.getLineRight(0) - layout.getLineLeft(0)
        val diag = "línea ${glyphs}px de ${layout.size.width}px, alto ${layout.getLineBottom(0) - layout.getLineTop(0)}px de ${layout.size.height}px, " +
            "desborde ancho=${layout.didOverflowWidth} alto=${layout.didOverflowHeight}"
        assertTrue("el tiempo no se recorta a lo ancho: $diag", glyphs <= (panel.right - panel.left).value * px - 32f * px + 1f)
        assertTrue("el tiempo no se recorta a lo alto: $diag", layout.getLineBottom(0) - layout.getLineTop(0) <= layout.size.height + 1f)
        assertTrue("el tiempo es grande: ${label.bottom - label.top}", (label.bottom - label.top).value >= 40f)
    }

    @Test
    fun clockPanelFitsAt880() {
        scene(intervals, heightDp = 880, font = 1f)
        assertClockFits()
        rule.onNodeWithTag("cardio-figure-run").assertIsDisplayed()
    }

    @Test
    fun clockPanelFitsAt740WithFont130() {
        scene(intervals, heightDp = 740, font = 1.3f)
        assertClockFits()
    }

    @Test
    fun rowingShowsRowerAndNoSkipWithoutSteps() {
        scene(continuous)
        rule.onNodeWithTag("cardio-figure-row").assertIsDisplayed()
        // Cardio continuo: no hay pasos que saltar, así que el mando no se pinta apagado.
        assertTrue(rule.onAllNodes(SemanticsMatcher.expectValue(androidx.compose.ui.semantics.SemanticsProperties.TestTag, "cardio-skip"))
            .fetchSemanticsNodes().isEmpty())
    }

    @Test
    fun manualHeartRateIsAddedValidatedAndShownWithItsZone() {
        scene(intervals)
        rule.onNodeWithTag("cardio-hr").assertTextContains("—")
        rule.onNodeWithTag("cardio-hr-add").performClick()
        rule.waitForIdle()
        rule.onNodeWithTag("cardio-hr-sheet-title", useUnmergedTree = true).assertTextContains("Añadir pulso")
        rule.onNodeWithTag("cardio-hr-input", useUnmergedTree = true).performTextReplacement("300")
        rule.onNodeWithTag("cardio-hr-save", useUnmergedTree = true).performClick()
        rule.waitForIdle()
        rule.onNodeWithTag("cardio-hr-error", useUnmergedTree = true).assertTextContains("entre 40 y 230", substring = true)
        rule.onNodeWithTag("cardio-hr-input", useUnmergedTree = true).performTextReplacement("150")
        rule.onNodeWithText("Con 150 lpm estás en zona 3 · Tempo.", useUnmergedTree = true).assertIsDisplayed()
        rule.onNodeWithTag("cardio-hr-save", useUnmergedTree = true).performClick()
        rule.waitForIdle()
        rule.mainClock.advanceTimeBy(1_000)
        rule.waitForIdle()
        rule.onNodeWithTag("cardio-hr").assertTextContains("150")
        rule.onNodeWithText("lpm · manual", useUnmergedTree = true).assertIsDisplayed()
        rule.onNodeWithTag("cardio-hr-manual").assertTextContains("Manual · hace", substring = true)
        // Se puede actualizar: la ficha vuelve a abrir la hoja con el valor guardado.
        rule.onNodeWithTag("cardio-hr-manual").performClick()
        rule.waitForIdle()
        rule.onNodeWithTag("cardio-hr-sheet-title", useUnmergedTree = true).assertTextContains("Actualizar pulso")
    }

    @Test
    fun watchSourceIsNotAButton() {
        scene(intervals, watchHr = 140)
        rule.onNodeWithTag("cardio-hr").assertTextContains("140")
        rule.onNodeWithText("Reloj").assertIsDisplayed()
        rule.onNodeWithTag("cardio-hr-source").assert(SemanticsMatcher.keyNotDefined(SemanticsActions.OnClick))
        assertTrue(rule.onAllNodes(SemanticsMatcher.expectValue(androidx.compose.ui.semantics.SemanticsProperties.TestTag, "cardio-hr-add"))
            .fetchSemanticsNodes().isEmpty())
    }

    @Test
    fun manualHeartRateValidation() {
        assertEquals("Escribe tu pulso en lpm.", cardioManualHeartRateError(" "))
        assertTrue(cardioManualHeartRateError("39")!!.contains("entre 40 y 230"))
        assertTrue(cardioManualHeartRateError("231")!!.contains("entre 40 y 230"))
        assertNull(cardioManualHeartRateError("40"))
        assertNull(cardioManualHeartRateError("230"))
        assertEquals(150, cardioManualHeartRateOrNull("150"))
        assertNull(cardioManualHeartRateOrNull("12a"))
        assertEquals("Manual · hace 0:30", cardioManualHeartRateLabel(30))
    }

    @Test
    fun figureFollowsModalityAndIntensity() {
        assertEquals(CardioFigureKind.RUN, cardioFigureSpec(CardioType.TREADMILL, 12.0).kind)
        assertEquals(CardioFigureKind.WALK, cardioFigureSpec(CardioType.TREADMILL, 5.0).kind)
        assertTrue(cardioFigureSpec(CardioType.TREADMILL).indoor)
        assertFalse(cardioFigureSpec(CardioType.RUN_OUTDOOR).indoor)
        assertEquals(CardioFigureKind.BIKE, cardioFigureSpec(CardioType.BIKE_STATIONARY).kind)
        assertEquals(CardioFigureKind.BIKE, cardioFigureSpec(CardioType.AIR_BIKE).kind)
        assertEquals(CardioFigureKind.ELLIPTICAL, cardioFigureSpec(CardioType.ELLIPTICAL).kind)
        assertEquals(CardioFigureKind.ROW, cardioFigureSpec(CardioType.ROW_MACHINE).kind)
        assertEquals(CardioFigureKind.STAIRS, cardioFigureSpec(CardioType.STAIR_CLIMBER).kind)
        assertEquals(CardioFigureKind.WALK, cardioFigureSpec(CardioType.WALK).kind)
        val work = cardioFigureCadence(CardioFigureKind.RUN, zone = 4, moving = true)
        val recover = cardioFigureCadence(CardioFigureKind.RUN, zone = 2, moving = true)
        assertTrue("más rápido en trabajo que en recuperación", work > recover)
        assertEquals(0f, cardioFigureCadence(CardioFigureKind.RUN, zone = 4, moving = false))
        assertEquals(1.5f, cardioFigureCadence(CardioFigureKind.BIKE, zone = 3, moving = true, rpm = 90), 1e-4f)
    }
}
