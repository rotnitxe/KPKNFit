package com.example.kpkn.screens.workout

import android.app.Application
import androidx.activity.ComponentActivity
import androidx.compose.foundation.layout.*
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.LocalView
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.test.*
import androidx.compose.ui.test.junit4.createAndroidComposeRule
import androidx.compose.ui.unit.Density
import androidx.compose.ui.unit.dp
import androidx.compose.ui.zIndex
import com.example.kpkn.data.models.*
import com.example.kpkn.domain.auge.SessionIntensityResult
import com.example.kpkn.screens.onboarding.design.LocalWizardReducedMotion
import com.example.kpkn.ui.components.*
import com.example.kpkn.ui.theme.KPKNTheme
import com.example.kpkn.ui.tour.TourSeenStore
import dev.chrisbanes.haze.HazeState
import org.junit.Assert.*
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import org.robolectric.annotation.GraphicsMode

/** Reproduces the header entry with the same portal and initial story as the activity. */
@RunWith(RobolectricTestRunner::class)
@GraphicsMode(GraphicsMode.Mode.NATIVE)
@Config(sdk = [34], application = Application::class, qualifiers = "w360dp-h880dp-xxhdpi")
class LiveStoryEntryTest {
    @get:Rule val rule = createAndroidComposeRule<ComponentActivity>()

    @Test fun headerStoryIsVisibleAndCompleteWithoutConfirmingTheSession() = story(880, 1f)
    @Test fun headerStoryFitsAt740WithLargeText() = story(740, 1.3f)

    private fun story(height: Int, font: Float) {
        var open by mutableStateOf(false)
        var confirmed = 0
        lateinit var view: android.view.View
        rule.setContent {
            view = LocalView.current
            val density = LocalDensity.current.density
            val host = remember { KpknOverlayHostController(object : TourSeenStore {
                override fun seenIds() = emptySet<String>()
                override fun markSeen(tourId: String) = Unit
            }) }
            CompositionLocalProvider(LocalDensity provides Density(density, font),
                LocalWizardReducedMotion provides true, LocalKpknOverlayHost provides host) {
                KPKNTheme {
                    Box(Modifier.requiredSize(360.dp, height.dp).padding(top = 45.dp, bottom = 24.dp).testTag("story-viewport")) {
                        WorkoutHeaderBar("Sentadilla", sessionName = "Pierna A", groupName = null, startTimeMs = 0L,
                            isComplete = false, background = null, sessionTimeRemainingSeconds = null,
                            onAdjustTimeLimit = {}, onStoryClick = { open = true },
                            statusBarInsets = WindowInsets(0, 0, 0, 0))
                        if (open) FinishWorkoutSheet(
                            session = Session("story-session", "Pierna A", exercises = listOf(liveExercise("squat", 4))),
                            completedSets = emptyMap(), completedExercises = emptyList(), durationMinutes = 1,
                            sessionIntensityResult = SessionIntensityResult(7.0, 7.0, "Moderada", false, false, 0.25, 1.0),
                            postSessionPreview = PostSessionPreview(80, 80, 80, emptyMap(), 5, 5, 5),
                            hazeState = remember { HazeState() }, initialShowStory = true,
                            onConfirm = { _, _, _, _ -> confirmed++ }, onDismiss = { open = false },
                            onShare = {}, onSummaryReady = {},
                        )
                        if (host.entries.isNotEmpty()) Box(Modifier.fillMaxSize().zIndex(400f)) { KpknOverlayHostContent(host) }
                    }
                }
            }
        }
        rule.onNodeWithTag("workout-story-header").performClick()
        rule.onNodeWithTag("story-sheet", useUnmergedTree = true).assertIsDisplayed()
        val viewport = rule.onNodeWithTag("story-viewport").fetchSemanticsNode().boundsInRoot
        listOf("story-templates", "story-share-after-save", "story-save-image", "story-share").forEach { tag ->
            val bounds = rule.onNodeWithTag(tag).assertIsDisplayed().fetchSemanticsNode().boundsInRoot
            assertTrue("$tag tiene alto", bounds.height > 0)
            assertTrue("$tag dentro del viewport: $bounds / $viewport", bounds.top >= viewport.top && bounds.bottom <= viewport.bottom)
        }
        rule.waitUntil(15_000) { rule.onNodeWithTag("story-preview", true).isDisplayed() }
        val preview = rule.onNodeWithTag("story-preview", true).fetchSemanticsNode().boundsInRoot
        assertEquals(9f / 16f, preview.width / preview.height, .01f)
        for (label in listOf("Guardar imagen", "Compartir en historias")) {
            val layouts = mutableListOf<androidx.compose.ui.text.TextLayoutResult>()
            val text = rule.onNodeWithText(label, useUnmergedTree = true)
            text.performSemanticsAction(androidx.compose.ui.semantics.SemanticsActions.GetTextLayoutResult) { it(layouts) }
            val layout = layouts.single()
            val textBounds = text.fetchSemanticsNode().boundsInRoot
            val button = rule.onNodeWithTag(if (label == "Guardar imagen") "story-save-image" else "story-share")
                .fetchSemanticsNode().boundsInRoot
            // Tight native text boxes can report hasVisualOverflow from fractional rounding.
            // Check the actual visible characters and every laid-out line instead.
            assertTrue("$label usa como máximo dos líneas", layout.lineCount in 1..2)
            assertEquals("$label conserva todas sus letras", label.length,
                layout.getLineEnd(layout.lineCount - 1, visibleEnd = true))
            for (line in 0 until layout.lineCount) {
                assertFalse("$label no tiene elipsis", layout.isLineEllipsized(line))
                // The button is the clipping boundary; glyph bearings may exceed the tight text box.
                val left = textBounds.left + layout.getLineLeft(line)
                val right = textBounds.left + layout.getLineRight(line)
                val bottom = textBounds.top + layout.getLineBottom(line)
                assertTrue("$label cabe en el botón: $left..$right / $button", left >= button.left - 1f && right <= button.right + 1f)
                assertTrue("$label cabe de alto: $bottom / $button", bottom <= button.bottom + 1f)
            }
        }
        println("Historia $height dp / fuente $font: hoja=$viewport; vista previa=$preview")
        rule.runOnIdle { assertEquals(0, confirmed) }
        saveLiveQaRender(rule, view, "lv-af-story-$height-${(font * 100).toInt()}")
        rule.onNodeWithContentDescription("Volver al resumen").performClick()
        rule.onNodeWithTag("finish-tabs", useUnmergedTree = true).assertIsDisplayed()
        rule.runOnIdle { assertEquals(0, confirmed) }
    }
}
