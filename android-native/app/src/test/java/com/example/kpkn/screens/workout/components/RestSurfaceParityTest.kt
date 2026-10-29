package com.example.kpkn.screens.workout.components

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.size
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.test.assertTextEquals
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNodeWithContentDescription
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.unit.dp
import com.example.kpkn.data.models.CompletedSet
import com.example.kpkn.screens.workout.PendingRestSuggestion
import com.example.kpkn.screens.workout.RestClockShape
import com.example.kpkn.screens.workout.RestNextSetSummary
import com.example.kpkn.screens.workout.RestOverrun
import com.example.kpkn.screens.workout.RestTimerKind
import com.example.kpkn.screens.workout.WorkoutRestModalState
import com.example.kpkn.ui.theme.KPKNTheme
import dev.chrisbanes.haze.HazeState
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.RuntimeEnvironment
import org.robolectric.annotation.Config
import org.robolectric.annotation.GraphicsMode

/**
 * El descanso comparte estado entre la pantalla completa, la tarjeta y el minijuego. La tarjeta tiene composición fija (sin scroll):
 * −15 s, +15 s, pausa, descanso sugerido y expandir. Terminar el descanso y saltar el resto del ejercicio viven en la pantalla completa.
 * Las superficies no tienen lógica propia: solo reciben lambdas (las del ViewModel, las mismas de la notificación y de la voz).
 */
@RunWith(RobolectricTestRunner::class)
@GraphicsMode(GraphicsMode.Mode.NATIVE)
@Config(sdk = [34], application = android.app.Application::class, qualifiers = "w360dp-h880dp-xxhdpi")
class RestSurfaceParityTest {

    @get:Rule
    val rule = createComposeRule()

    private class Taps {
        var minus = 0
        var plus = 0
        var skip = 0
        var adaptive = 0
        var skipExercise = 0
        var minimize = 0
        var expand = 0
        var pauses = 0
        var shapes = mutableListOf<RestClockShape>()
    }

    private val rest = WorkoutRestModalState(
        exerciseName = "Sentadilla trasera",
        kind = RestTimerKind.STANDARD,
        plannedSeconds = 195,
        suggestedSeconds = 249,
        activeSeconds = 249,
    )
    private val suggestion = PendingRestSuggestion(
        plannedSeconds = 195,
        adaptiveSeconds = 249,
        exerciseName = "Sentadilla trasera",
        exerciseId = "sq",
        lastSet = CompletedSet(id = "last", weight = 95.0, reps = 6),
        advancedFeedback = null,
    )
    private val next = RestNextSetSummary(
        setNumber = 2,
        setTotal = 4,
        loadKg = 95.0,
        repsText = "4–6",
        intensityText = "RIR 2",
    )
    private val nbsp = "\u00A0"

    /** El reloj congelado: así no hay bucles que impidan que la prueba quede en reposo. */
    private fun content(body: @androidx.compose.runtime.Composable () -> Unit) {
        rule.setContent {
            KPKNTheme {
                MaterialTheme {
                    CompositionLocalProvider(LocalRestClockFrozen provides 1.7f) {
                        body()
                    }
                }
            }
        }
    }

    private fun overlay(taps: Taps, playing: Boolean = false, paused: Boolean = false, remaining: Int = 161) = content {
        Box(Modifier.size(360.dp, 880.dp).background(Color.Black)) {
            RestTimerOverlay(
                state = if (paused) rest.copy(pausedRemainingSeconds = 161) else rest,
                remainingSeconds = remaining,
                hazeState = HazeState(),
                pendingRestSuggestion = suggestion,
                lastCompletedSet = CompletedSet(id = "last", weight = 95.0, reps = 6),
                onDecrease = { taps.minus++ },
                onIncrease = { taps.plus++ },
                onSkip = { taps.skip++ },
                skipExerciseLabel = "Saltar series restantes de Sentadilla trasera",
                onSkipExercise = { taps.skipExercise++ },
                onUseAdaptive = { taps.adaptive++ },
                onMinimize = { taps.minimize++ },
                onShapeChange = { taps.shapes += it },
                nextSet = next,
                initiallyPlaying = playing,
                isPaused = paused,
                onTogglePause = { taps.pauses++ },
            )
        }
    }

    private fun card(taps: Taps, playing: Boolean = false, paused: Boolean = false) = content {
        Box(Modifier.size(272.dp, 388.dp).background(Color.Black)) {
            RestLiveCard(
                remainingSeconds = 161,
                totalSeconds = 249,
                sessionAccentColor = Color(0xFFF49A6E),
                restState = if (paused) rest.copy(pausedRemainingSeconds = 161) else rest,
                pendingRestSuggestion = suggestion,
                lastSetOutcome = null,
                lastCompletedSet = CompletedSet(id = "last", weight = 95.0, reps = 6),
                onDecrease = { taps.minus++ },
                onIncrease = { taps.plus++ },
                onSkip = { taps.skip++ },
                onUseAdaptive = { taps.adaptive++ },
                onExpand = { taps.expand++ },
                skipExerciseLabel = "Saltar series restantes de Sentadilla trasera",
                onSkipExercise = { taps.skipExercise++ },
                nextSet = next,
                initiallyPlaying = playing,
                isPaused = paused,
                onTogglePause = { taps.pauses++ },
            )
        }
    }

    @Test
    fun theFullScreenAdjustsSkipsAndSkipsTheRestOfTheExerciseWithTheCallbacksItIsGiven() {
        val taps = Taps()
        overlay(taps)
        rule.onNodeWithContentDescription("Restar 15 segundos").performClick()
        rule.onNodeWithContentDescription("Sumar 15 segundos").performClick()
        rule.onNodeWithContentDescription("Pausar descanso").performClick()
        rule.onNodeWithTag("rest-end").performClick()
        rule.onNodeWithTag("rest-suggestion-row").performClick()
        rule.onNodeWithTag("rest-suggestion-apply").performClick()
        rule.onNodeWithContentDescription("Minimizar a tarjeta").performClick()
        assertEquals(1, taps.minus)
        assertEquals(1, taps.plus)
        assertEquals(1, taps.pauses)
        assertEquals(1, taps.skip)
        assertEquals(1, taps.adaptive)
        assertEquals(1, taps.minimize)
    }

    @Test
    fun theCardAdjustsTheClockAndSuggestionWithoutInternalScroll() {
        val taps = Taps()
        card(taps)
        rule.onNodeWithContentDescription("Restar 15 segundos").performClick()
        rule.onNodeWithContentDescription("Sumar 15 segundos").performClick()
        rule.onNodeWithContentDescription("Pausar descanso").performClick()
        rule.onNodeWithTag("rest-suggestion-row").performClick()
        rule.onNodeWithTag("rest-suggestion-apply").performClick()
        rule.onNodeWithContentDescription("Expandir descanso").performClick()
        assertEquals(1, taps.minus)
        assertEquals(1, taps.plus)
        assertEquals(1, taps.pauses)
        assertEquals(1, taps.adaptive)
        assertEquals(1, taps.expand)
        // La tarjeta no desplaza contenido: saltar el descanso o el resto del ejercicio vive en la pantalla completa.
        rule.onNodeWithText("Saltar descanso").assertDoesNotExist()
        rule.onNodeWithText("Saltar series restantes de Sentadilla trasera").assertDoesNotExist()
    }

    @Test
    fun theFullScreenGameKeepsTheRestClockAndGoesBackToTheRest() {
        val taps = Taps()
        overlay(taps, playing = true)
        // Refinamiento 02: se abre el selector de juegos; elegir uno mantiene arriba el mismo reloj del descanso.
        rule.onNodeWithText("Salta las mancuernas").performClick()
        rule.onNodeWithTag("arcade-rest-clock", useUnmergedTree = true).assertTextEquals("2:41")
        // Desde el juego, «atrás» vuelve al selector; desde el selector, vuelve al reloj del descanso.
        rule.onNodeWithTag("arcade-back").performClick()
        rule.onNodeWithTag("arcade-back").performClick()
        rule.onNodeWithText("Una pausa antes de la siguiente.").assertExists()
        rule.onNodeWithContentDescription("Sumar 15 segundos").performClick()
        assertEquals(1, taps.plus)
    }

    @Test
    fun theFullScreenGameReturnsToTheSetWhenTheRestEnds() {
        val taps = Taps()
        overlay(taps, playing = true, remaining = 0)
        // Con el descanso cumplido la partida se congela y «Volver a la serie» llama al mismo «saltar» del descanso.
        rule.onNodeWithText("Salta las mancuernas").performClick()
        rule.onNodeWithTag("rest-game-rest-over", useUnmergedTree = true).assertExists()
        rule.onNodeWithTag("rest-game-rest-over-primary", useUnmergedTree = true).performClick()
        assertEquals(1, taps.skip)
    }

    @Test
    fun theCardGameKeepsTheRestClockAndGoesBackToTheCard() {
        val taps = Taps()
        card(taps, playing = true)
        rule.onNodeWithText("Salta las mancuernas").performClick()
        rule.onNodeWithTag("arcade-rest-clock", useUnmergedTree = true).assertTextEquals("2:41")
        // Desde el juego, «atrás» vuelve al selector; «Volver al descanso» devuelve a la tarjeta con el mismo reloj.
        rule.onNodeWithTag("arcade-back").performClick()
        rule.onNodeWithTag("arcade-return-rest").performClick()
        rule.onNodeWithText("2:41").assertExists()
        rule.onNodeWithText("Jugar mientras descansas").assertExists()
        rule.onNodeWithContentDescription("Restar 15 segundos").performClick()
        assertEquals(1, taps.minus)
    }

    @Test
    fun thePlayButtonsOpenTheGameInsideTheSameSurface() {
        overlay(Taps())
        rule.onNodeWithText("Jugar mientras descansas").performClick()
        rule.onNodeWithText("Salta las mancuernas").performClick()
        rule.onNodeWithTag("rest-game-canvas-runner", useUnmergedTree = true).assertExists()
    }

    @Test
    fun theCardPlayButtonOpensTheGameInsideTheCard() {
        card(Taps())
        rule.onNodeWithText("Jugar mientras descansas").performClick()
        rule.onNodeWithText("Salta las mancuernas").performClick()
        rule.onNodeWithTag("rest-game-canvas-runner", useUnmergedTree = true).assertExists()
    }

    @Test
    fun theHostControlsClockShapeWithoutShowingSelectionTabs() {
        val taps = Taps()
        overlay(taps)
        rule.onNodeWithText("Pista").assertDoesNotExist()
        rule.onNodeWithText("Espiral").assertDoesNotExist()
        rule.onNodeWithText("Torre").assertDoesNotExist()
        assertEquals(emptyList<RestClockShape>(), taps.shapes)
    }

    @Test
    fun theFullScreenShowsWhatEverythingElseKeepsShowing() {
        overlay(Taps())
        rule.onNodeWithTag("rest-full-eyebrow").assertTextEquals("PAUSA ENTRE SERIES")
        rule.onNodeWithText("2:41").assertExists()
        rule.onNodeWithText("Una pausa antes de la siguiente.").assertExists()
        rule.onNodeWithTag("rest-full-context").assertExists()
        rule.onNodeWithText("ÚLTIMA").assertExists()
        rule.onNodeWithText("SIGUIENTE").assertExists()
        rule.onNodeWithContentDescription("Descansando, 2:41").assertExists()
    }

    @Test
    fun theCardShowsTheNextSetFromTheRealPlan() {
        card(Taps())
        rule.onNodeWithText("2:41").assertExists()
        rule.onNodeWithText("SIGUIENTE").assertExists()
        rule.onNodeWithText("Sentadilla trasera · serie 2").assertExists()
        rule.onNodeWithText("95${nbsp}kg${nbsp}×${nbsp}4–6").assertExists()
    }

    @Test
    fun bothRestSurfacesPauseAndResumeTheSameClock() {
        val taps = Taps()
        overlay(taps, paused = true)
        rule.onNodeWithContentDescription("En pausa, 2:41").assertExists()
        rule.onNodeWithContentDescription("Continuar descanso").performClick()
        rule.onNodeWithText("En pausa").assertExists()
        assertEquals(1, taps.pauses)
    }

    @Test
    fun theCardShowsThePausedClockWithoutLosingTheRemainingTime() {
        card(Taps(), paused = true)
        rule.onNodeWithText("2:41").assertExists()
        rule.onNodeWithContentDescription("En pausa, 2:41").assertExists()
        rule.onNodeWithContentDescription("Continuar descanso").assertExists()
    }

    @Test
    fun theCardKeepsEveryFixedPieceInsideTheViewportFrameAtLargeText() {
        RuntimeEnvironment.setFontScale(1.3f)
        try {
            content {
                Box(Modifier.size(312.dp, 394.dp).background(Color.Black).testTag("fixed-card")) {
                    RestLiveCard(
                        remainingSeconds = 161,
                        totalSeconds = 249,
                        sessionAccentColor = Color(0xFFF49A6E),
                        restState = rest.copy(notificationsEnabled = false),
                        pendingRestSuggestion = suggestion,
                        lastSetOutcome = null,
                        lastCompletedSet = CompletedSet(id = "last", weight = 95.0, reps = 6),
                        onDecrease = {}, onIncrease = {}, onSkip = {}, onUseAdaptive = {},
                        onExpand = {}, nextSet = next, clockActive = false,
                        videoSlot = { Box(Modifier.fillMaxWidth().height(48.dp)) },
                    )
                }
            }
            val card = rule.onNodeWithTag("fixed-card", useUnmergedTree = true).fetchSemanticsNode().boundsInRoot
            listOf("Descanso sugerido", "Sumar 15 segundos").forEach { label ->
                val bounds = (if (label == "Sumar 15 segundos") rule.onNodeWithContentDescription(label)
                    else rule.onNodeWithText(label)).fetchSemanticsNode().boundsInRoot
                assertTrue("$label fuera de la tarjeta: $bounds vs $card", bounds.top >= card.top && bounds.bottom <= card.bottom)
            }
        } finally {
            RuntimeEnvironment.setFontScale(1f)
        }
    }

    private val overrun = RestOverrun(endedAtMs = 1_000L, totalSeconds = 249, kind = RestTimerKind.STANDARD)

    @Test
    fun theFullScreenOverrunStartsTheSetAndAsksForThirtyMoreSecondsWithoutTouchingTheRest() {
        var started = 0
        var extended = 0
        content {
            Box(Modifier.size(360.dp, 880.dp).background(Color.Black)) {
                RestOverrunOverlay(
                    overrun = overrun,
                    hazeState = HazeState(),
                    shape = RestClockShape.TOWER,
                    headline = null,
                    nextSet = next,
                    onStart = { started++ },
                    onExtend = { extended++ },
                    onMinimize = null,
                    extraSecondsOverride = 24,
                )
            }
        }
        rule.onNodeWithText("Tiempo extra").assertExists()
        rule.onNodeWithText("+0:24").assertExists()
        rule.onNodeWithText("Tu descanso se ha cumplido.").assertExists()
        rule.onNodeWithText("SIGUIENTE").assertExists()
        rule.onNodeWithText("95${nbsp}kg${nbsp}×${nbsp}4–6").assertExists()
        rule.onNodeWithText("Continuar entrenando").performClick()
        rule.onNodeWithText("Necesito +30 s").performClick()
        assertEquals(1, started)
        assertEquals(1, extended)
    }

    @Test
    fun theCardOverrunHasTheSameActions() {
        var started = 0
        var extended = 0
        var expanded = 0
        content {
            Box(Modifier.size(272.dp, 388.dp).background(Color.Black)) {
                RestOverrunCard(
                    overrun = overrun,
                    shape = RestClockShape.SPIRAL,
                    nextSet = next,
                    onStart = { started++ },
                    onExtend = { extended++ },
                    onExpand = { expanded++ },
                    extraSecondsOverride = 24,
                )
            }
        }
        rule.onNodeWithText("DESCANSO · TIEMPO EXTRA").assertExists()
        rule.onNodeWithText("+0:24").assertExists()
        rule.onNodeWithText("Continuar").performClick()
        rule.onNodeWithText("Necesito +30 s").performClick()
        rule.onNodeWithContentDescription("Expandir descanso").performClick()
        assertEquals(1, started)
        assertEquals(1, extended)
        assertEquals(1, expanded)
    }

    @Test
    fun theMiniChipOpensTheRestAndAddsFifteenSeconds() {
        var opened = 0
        var added = 0
        content {
            Box(Modifier.size(360.dp, 120.dp).background(Color.Black)) {
                RestMiniChip(
                    remainingSeconds = 161,
                    totalSeconds = 249,
                    onOpen = { opened++ },
                    onAddTime = { added++ },
                )
            }
        }
        rule.onNodeWithText("2:41").assertExists()
        rule.onNodeWithText("Descanso").performClick()
        rule.onNodeWithContentDescription("Sumar 15 segundos").performClick()
        assertEquals(1, opened)
        assertEquals(1, added)
    }
}
