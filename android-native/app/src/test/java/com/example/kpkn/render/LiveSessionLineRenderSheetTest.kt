package com.example.kpkn.render

import android.app.Application
import android.graphics.Bitmap
import android.view.View
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.onSizeChanged
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.LocalView
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.test.*
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.unit.Density
import androidx.compose.ui.unit.dp
import com.example.kpkn.data.models.*
import com.example.kpkn.screens.onboarding.design.LocalWizardReducedMotion
import com.example.kpkn.screens.workout.*
import com.example.kpkn.screens.workout.components.*
import com.example.kpkn.ui.components.LocalKpknOverlayHost
import com.example.kpkn.ui.theme.KPKNTheme
import org.junit.Assert.*
import org.junit.Assume.assumeTrue
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import org.robolectric.annotation.GraphicsMode
import java.io.File

/** Uses production components and the production height policy; no drawn imitation of the approved boards. */
@RunWith(RobolectricTestRunner::class)
@GraphicsMode(GraphicsMode.Mode.NATIVE)
@Config(sdk = [34], application = Application::class, qualifiers = "w360dp-h880dp-xxhdpi")
class LiveSessionLineRenderSheetTest {
    @get:Rule val rule = createComposeRule()
    private var view: View? = null
    private var density = 1f
    private val set = ExerciseSet("s1", weight = 95.0, targetReps = 6, targetRIR = 2, intensityMode = IntensityMode.RIR)
    private val exercise = Exercise("squat", "Sentadilla", sets = List(4) { set.copy(id = "s$it") })
    private val row = Exercise("row", "Peso muerto rumano", sets = List(4) { set.copy(id = "r$it") })
    private fun content(fontScale: Float = 1f, compact: Boolean = false, body: @Composable () -> Unit) {
        assumeTrue(!System.getenv("KPKN_RENDER_DIR").isNullOrBlank())
        rule.setContent {
            view = LocalView.current; density = LocalDensity.current.density
            CompositionLocalProvider(LocalDensity provides Density(density, fontScale), LocalWizardReducedMotion provides true,
                LocalKpknOverlayHost provides null, LocalRestClockFrozen provides 1.7f) {
                CompositionLocalProvider(LocalLiveSessionCompactLayout provides compact) { KPKNTheme { MaterialTheme { body() } } }
            }
        }
        rule.waitForIdle()
    }
    @Test fun v1At880With45DpStatusBar() = scene(880, 1f, "lv-a-v1-880")
    @Test @Config(qualifiers = "w360dp-h740dp-xxhdpi") fun v1At740With45DpStatusBar() = scene(740, 1f, "lv-a-v1-740")
    @Test @Config(qualifiers = "w360dp-h760dp-xxhdpi") fun v1At760With45DpStatusBarAnd130PercentText() = scene(760, 1.3f, "lv-a-v1-760-130")
    private fun scene(height: Int, fontScale: Float, name: String) {
        content(fontScale, compact = height < 800) {
            Box(Modifier.requiredSize(360.dp, height.dp).background(Color.Black).testTag("lv-a-scene")) {
                Box(Modifier.align(Alignment.TopCenter).padding(top = 45.dp)) {
                    WorkoutHeaderBar("Sentadilla", sessionName = "Pierna A", groupName = "Principal", startTimeMs = 0L,
                        isComplete = false, background = null, sessionTimeRemainingSeconds = null, onAdjustTimeLimit = {},
                        onToggleVoice = {}, onStoryClick = {}, onMoreClick = {}, compactLayout = height < 800,
                        statusBarInsets = WindowInsets(0, 0, 0, 0))
                }
                Box(Modifier.align(Alignment.BottomCenter).padding(bottom = 32.dp + com.example.kpkn.screens.workout.LiveSessionLineHeight + 12.dp).width(312.dp)) {
                    LiveSessionCardFrame {
                        SetInputCardV2(exercise, 1, set, null, null,
                            onShowHistory = {}, onRecordV2 = { _, _, _, _, _, _, _, _, _, _ -> },
                            onSetBodyWeight = {}, initialBodyWeight = 80.0, recordActionHolder = remember { RecordActionHolder() })
                    }
                }
                LiveSessionLine(listOf(exercise, row), exercise.id, liveLineTestSnapshot(4, 1, true), {}, {}, {}, {}, {}, true, true,
                    onAddSet = {},
                    modifier = Modifier.align(Alignment.BottomCenter).padding(start = 16.dp, end = 16.dp, bottom = 32.dp))
            }
        }
        val frame = rule.onNodeWithTag("live-card-frame").assertIsDisplayed().fetchSemanticsNode().boundsInRoot
        val island = rule.onNodeWithTag("live-session-line").assertIsDisplayed().fetchSemanticsNode().boundsInRoot
        val rail = rule.onNodeWithTag("live-session-steps").assertIsDisplayed().fetchSemanticsNode().boundsInRoot
        assertEquals(312f * density, frame.width, 1f); assertEquals(liveSessionCardHeightDp(360, height).value * density, frame.height, 1f)
        assertEquals(com.example.kpkn.screens.workout.LiveSessionLineHeight.value * density, island.height, 1f); assertTrue(rail.height > 0f)
        val heading = rule.onNodeWithTag("live-session-header").fetchSemanticsNode().boundsInRoot
        assertTrue("La cabecera no invade la tarjeta", heading.bottom <= frame.top)
        assertTrue("La isla no invade la tarjeta", frame.bottom + 11f * density <= island.top)
        assertTrue("La isla queda sobre la barra inferior de 24dp", island.bottom <= (height - 24) * density)
        rule.onNodeWithTag("secondary_pill_historial").assertIsDisplayed()
        val actions = rule.onNodeWithTag("live-card-actions").fetchSemanticsNode().boundsInRoot
        assertEquals("La fila de acciones queda completa dentro de la tarjeta", 44f * density, actions.height, 1f)
        assertTrue("Las acciones no se salen por abajo", actions.bottom <= frame.bottom + 1f)
        val measurements = "viewport=360x$height dp; status=45 dp; navigation=24 dp; fontScale=$fontScale\n" +
            listOf("card" to frame, "line" to island, "steps" to rail, "actions" to actions).joinToString("\n") { (label, rect) ->
                "$label: x=${rect.left / density}, y=${rect.top / density}, width=${rect.width / density}, height=${rect.height / density} dp"
            }
        val folder = File(checkNotNull(System.getenv("KPKN_RENDER_DIR"))).apply { mkdirs() }
        File(folder, "$name-medidas.txt").writeText(measurements)
        println(measurements)
        save("lv-a-scene", name)
    }
    @Test fun route() = sheet(LiveSessionDestination.ROUTE)
    @Test fun milestones() = sheet(LiveSessionDestination.MILESTONES)
    @Test fun timer() = sheet(LiveSessionDestination.TIMER)
    @Test fun rm() = sheet(LiveSessionDestination.RM)
    @Test fun notes() = sheet(LiveSessionDestination.NOTES)
    @Test fun photos() {
        content { WorkoutSessionAlbumSheet(emptyList(), {}, captureActions = {
            SessionPhotoCaptureActions({}, { null }, { _, _ -> }, {}, "Captura pendiente", {})
        }) }
        save("live-session-album", "lv-a-photos")
    }
    private fun sheet(destination: LiveSessionDestination) {
        content {
            LiveSessionSheetsHost(destination, {}, exercises = listOf(exercise.copy(isStarTarget = true, goal1RM = 150.0), row), currentExerciseId = exercise.id,
                completedSets = mapOf("squat_0" to CompletedSet("s0", weight = 95.0, reps = 6, rir = 2)),
                completedExercises = listOf(CompletedExercise(exercise.id, exercise.name,
                    sets = listOf(CompletedSet("s0", weight = 95.0, reps = 6, rir = 2)))),
                startTimeMs = System.currentTimeMillis() - 150_000L, targetMinutes = 55,
                 milestones = listOf(SessionMilestone("pr", "squat", "Sentadilla", "pr_e1rm", "Nuevo récord", 114.0)),
                sessionNotes = "Mantener el abdomen firme.", sessionSavedNotes = listOf(SessionSavedNote("note", "Primera serie estable.")),
                sessionChecklist = listOf(SessionChecklistItem("water", "Beber agua")), bodyWeight = 80.0)
        }
        if (destination == LiveSessionDestination.RM) {
            rule.onAllNodes(hasSetTextAction())[0].performTextInput("100")
            rule.onAllNodes(hasSetTextAction())[1].performTextInput("5")
        }
        save("live-session-sheet-${destination.name.lowercase()}", "lv-a-${destination.name.lowercase()}")
    }
    private fun save(tag: String, name: String) {
        val bounds = rule.onNodeWithTag(tag, useUnmergedTree = true).fetchSemanticsNode().boundsInRoot
        val host = checkNotNull(view)
        val full = Bitmap.createBitmap(host.width, host.height, Bitmap.Config.ARGB_8888)
        rule.runOnUiThread { host.draw(android.graphics.Canvas(full)) }
        val left = bounds.left.toInt().coerceAtLeast(0); val top = bounds.top.toInt().coerceAtLeast(0)
        val cropped = Bitmap.createBitmap(full, left, top, bounds.width.toInt().coerceAtMost(full.width - left), bounds.height.toInt().coerceAtMost(full.height - top))
        val folder = File(checkNotNull(System.getenv("KPKN_RENDER_DIR"))).apply { mkdirs() }
        File(folder, "$name.png").outputStream().use { assertTrue(cropped.compress(Bitmap.CompressFormat.PNG, 100, it)) }
        cropped.recycle(); full.recycle()
    }
}
