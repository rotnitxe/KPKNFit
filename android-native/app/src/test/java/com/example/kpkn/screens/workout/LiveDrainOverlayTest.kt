package com.example.kpkn.screens.workout

import android.app.Application
import androidx.compose.foundation.layout.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.LocalView
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.test.*
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.unit.Density
import androidx.compose.ui.unit.dp
import com.example.kpkn.screens.onboarding.design.LocalWizardReducedMotion
import com.example.kpkn.ui.theme.KPKNTheme
import org.junit.Assert.*
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import org.robolectric.annotation.GraphicsMode

@RunWith(RobolectricTestRunner::class)
@GraphicsMode(GraphicsMode.Mode.NATIVE)
@Config(sdk = [34], application = Application::class, qualifiers = "w360dp-h880dp-xxhdpi")
class LiveDrainOverlayTest {
    @get:Rule val rule = createComposeRule()
    private lateinit var view: android.view.View
    private var density = 1f
    private var records = 0
    private var state by mutableStateOf<ExerciseDrainOverlayState?>(drain(1))

    private fun drain(key: Long) = ExerciseDrainOverlayState(key, "Sentadilla QA", listOf(
        ExerciseDrainOverlayItem("Energía", 10, ExerciseDrainOverlayChannel.ENERGY),
        ExerciseDrainOverlayItem("Columna", 1, ExerciseDrainOverlayChannel.BACK),
        ExerciseDrainOverlayItem("Muscular", 10, ExerciseDrainOverlayChannel.MUSCLE),
    ))

    private fun show(height: Int = 880, font: Float = 1f, insetDp: Int = 24) {
        rule.mainClock.autoAdvance = false
        rule.setContent {
            view = LocalView.current
            density = LocalDensity.current.density
            CompositionLocalProvider(LocalDensity provides Density(density, font), LocalWizardReducedMotion provides true) {
                KPKNTheme {
                    Box(Modifier.requiredSize(360.dp, height.dp).testTag("drain-viewport")) {
                        val exercise = liveExercise("squat", 4, name = "Sentadilla QA")
                        val line = liveLineTestSnapshot(4, 1, false).let { snapshot ->
                            snapshot.copy(completedCount = 1, elements = snapshot.elements.map { element ->
                                if (element is TimelineElement.BilateralSet && element.label == "S1")
                                    element.copy(state = WorkoutSetCardVisualState.COMPLETED) else element
                            })
                        }
                        LiveSessionLine(listOf(exercise), exercise.id, line,
                            {}, {}, {}, {}, { records++ }, true, true,
                            modifier = Modifier.align(Alignment.BottomCenter)
                                .padding(start = 16.dp, end = 16.dp, bottom = (insetDp + 8).dp))
                        ExerciseDrainOverlay(state, LiveSessionLineHeight + 8.dp,
                            Modifier.align(Alignment.BottomCenter),
                            insets = WindowInsets(0, 0, 0, with(LocalDensity.current) { insetDp.dp.roundToPx() }))
                    }
                }
            }
        }
        rule.mainClock.advanceTimeBy(200)
    }

    @Test fun feedbackStaysAboveTheWholeIslandAndRegisterAt880() = geometry(880, 1f)
    @Test fun feedbackStaysAboveTheWholeIslandAndRegisterAt740WithLargeText() = geometry(740, 1.3f)

    private fun geometry(height: Int, font: Float) {
        show(height, font)
        val panel = rule.onNodeWithTag("exercise-drain-overlay").assertIsDisplayed().fetchSemanticsNode().boundsInRoot
        val line = rule.onNodeWithTag("live-session-line").assertIsDisplayed().fetchSemanticsNode().boundsInRoot
        assertTrue("Drenaje deja 8dp sobre la isla: $panel / $line", panel.bottom + 7 * density <= line.top)
        rule.onNodeWithContentDescription("Registrar serie").assertIsDisplayed().performClick()
        rule.runOnIdle { assertEquals(1, records) }
        println("Drenaje $height dp / fuente $font: panel=$panel; isla=$line")
        saveLiveQaRender(rule, view, "lv-af-drain-$height-${(font * 100).toInt()}")
    }

    @Test fun feedbackExpiresAfter1650MillisPlusItsExitAnimation() {
        show()
        rule.mainClock.advanceTimeBy(1_300)
        rule.onNodeWithTag("exercise-drain-overlay").assertIsDisplayed()
        rule.mainClock.advanceTimeBy(600)
        rule.onNodeWithTag("exercise-drain-overlay").assertDoesNotExist()
    }

    @Test fun tapDismissesFeedbackAndTheNextRecordCanShowItsOwnFeedback() {
        show()
        rule.onNodeWithTag("exercise-drain-overlay").performClick()
        rule.mainClock.advanceTimeBy(300)
        rule.onNodeWithTag("exercise-drain-overlay").assertDoesNotExist()
        // A new record needs Android's layout/draw pass as well as Compose frames.
        // The paused clock above remains responsible for the dismissal-duration assertion.
        rule.mainClock.autoAdvance = true
        rule.runOnIdle { state = drain(2) }
        rule.onNodeWithTag("exercise-drain-overlay").assertIsDisplayed()
    }
}
