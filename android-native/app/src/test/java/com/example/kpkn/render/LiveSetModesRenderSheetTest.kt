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
import androidx.compose.ui.semantics.SemanticsActions
import androidx.compose.ui.semantics.SemanticsProperties
import androidx.compose.ui.test.*
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.unit.dp
import com.example.kpkn.data.models.TrainingMode
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

@RunWith(RobolectricTestRunner::class)
@GraphicsMode(GraphicsMode.Mode.NATIVE)
@Config(sdk = [34], application = Application::class, qualifiers = "w360dp-h880dp-xxhdpi")
class LiveSetModesRenderSheetTest {
    @get:Rule val rule = createComposeRule()
    private var view: View? = null
    private var density = 1f
    @Test fun reps880() = scene(TrainingMode.REPS, 880)
    @Test fun reps740() = scene(TrainingMode.REPS, 740)
    @Test fun rm880() = scene(TrainingMode.RM, 880)
    @Test fun rm740() = scene(TrainingMode.RM, 740)
    @Test fun soloRpe880() = scene(TrainingMode.SOLO_RPE, 880)
    @Test fun soloRpe740() = scene(TrainingMode.SOLO_RPE, 740)
    @Test fun time880() = scene(TrainingMode.TIME, 880)
    @Test fun time740() = scene(TrainingMode.TIME, 740)
    @Test fun distance880() = scene(TrainingMode.DISTANCE, 880)
    @Test fun distance740() = scene(TrainingMode.DISTANCE, 740)
    @Test fun custom880() = scene(TrainingMode.CUSTOM, 880)
    @Test fun custom740() = scene(TrainingMode.CUSTOM, 740)
    @Test fun rom880() = scene(TrainingMode.REPS, 880, rom = true)
    @Test fun rom740() = scene(TrainingMode.REPS, 740, rom = true)
    @Test fun failureFire880() = scene(TrainingMode.REPS, 880, failure = true)
    @Test fun rmLinkedEditor740() = scene(TrainingMode.RM, 740, editRm = true)

    private fun scene(mode: TrainingMode, height: Int, rom: Boolean = false, failure: Boolean = false, editRm: Boolean = false) {
        assumeTrue(!System.getenv("KPKN_RENDER_DIR").isNullOrBlank())
        val exercise = liveModeExercise(mode, rom)
        val name = "lv-b-${if (rom) "rom" else if (failure) "failure" else if (editRm) "rm-editor" else mode.name.lowercase()}-$height"
        rule.setContent {
            view = LocalView.current; density = LocalDensity.current.density
            CompositionLocalProvider(LocalWizardReducedMotion provides !failure, LocalKpknOverlayHost provides null,
                LocalLiveSessionCompactLayout provides (height < 800)) {
                KPKNTheme { MaterialTheme {
                    Box(Modifier.requiredSize(360.dp, height.dp).background(Color.Black).testTag("lv-b-scene")) {
                        var headerPx by remember { mutableIntStateOf(0) }
                        val localDensity = LocalDensity.current
                        val header = with(localDensity) { headerPx.toDp() }
                        val band = liveSessionRelatorBandHeight(height.toFloat(), header.value, 425f, 144f).dp
                        Box(Modifier.align(Alignment.TopCenter).padding(top = 45.dp).onSizeChanged { headerPx = it.height + with(localDensity) { 45.dp.roundToPx() } }) {
                            WorkoutHeaderBar(exercise.name, sessionName = "Fuerza A", groupName = "Principal", startTimeMs = 0L,
                                isComplete = false, background = null, sessionTimeRemainingSeconds = null, onAdjustTimeLimit = {},
                                onToggleVoice = {}, onStoryClick = {}, onMoreClick = {}, compactLayout = height < 800,
                                statusBarInsets = WindowInsets(0, 0, 0, 0))
                        }
                        WorkoutLiveRelatorLine(when (mode) {
                            TrainingMode.RM -> "Tu referencia: 125 kg de 1RM. Plan: 80 %."
                            TrainingMode.SOLO_RPE -> "Elige la carga que te permita alcanzar RPE 8."
                            TrainingMode.TIME -> "Mantén la posición durante un minuto."
                            TrainingMode.DISTANCE -> "Acumula metros con la carga del plan."
                            TrainingMode.CUSTOM -> "Cuenta cada brazada completa."
                            else -> "Registra cómo se sintió tu serie."
                        }, "lv-b", modifier = Modifier.align(Alignment.TopCenter).offset(y = header).height(band))
                        Box(Modifier.align(Alignment.BottomCenter).padding(bottom = 156.dp).width(312.dp)) {
                            LiveSessionCardFrame {
                                SetInputCardV2(exercise, 0, exercise.sets.single(), null,
                                    onShowHistory = {}, onSetBodyWeight = {}, initialBodyWeight = 80.0, recordActionHolder = remember { RecordActionHolder() },
                                    onRecordV2 = { _, _, _, _, _, _, _, _, _, _ -> }, effortFireSecondsOverride = 1.7f)
                            }
                        }
                        LiveSessionLine(listOf(exercise), exercise.id, liveLineTestSnapshot(4), {}, {}, {}, {}, {}, true, true,
                            onAddSet = {}, modifier = Modifier.align(Alignment.BottomCenter).padding(start = 16.dp, end = 16.dp, bottom = 32.dp))
                    }
                } }
            }
        }
        rule.waitForIdle()
        if (failure) {
            val info = rule.onNodeWithTag(EffortSliderTag).fetchSemanticsNode().config[SemanticsProperties.ProgressBarRangeInfo]
            rule.onNodeWithTag(EffortSliderTag).performSemanticsAction(SemanticsActions.SetProgress) { it(info.range.endInclusive) }
        }
        if (editRm) rule.onNodeWithTag("live-rm-edit").performClick()
        val frame = rule.onNodeWithTag("live-card-frame").assertIsDisplayed().fetchSemanticsNode().boundsInRoot
        val actions = rule.onNodeWithTag("live-card-actions").assertIsDisplayed().fetchSemanticsNode().boundsInRoot
        val island = rule.onNodeWithTag("live-session-line").assertIsDisplayed().fetchSemanticsNode().boundsInRoot
        if (editRm) rule.onNodeWithTag(EffortSliderTag).performScrollTo()
        val touch = rule.onNodeWithTag(EffortSliderTag).assertIsDisplayed().fetchSemanticsNode().boundsInRoot
        val track = rule.onNodeWithTag("effort-fire-track", useUnmergedTree = true).fetchSemanticsNode().boundsInRoot
        assertEquals(312f, frame.width / density, .5f); assertEquals(425f, frame.height / density, .5f)
        assertEquals(44f, actions.height / density, .5f); assertEquals(44f, touch.height / density, .5f)
        assertEquals(18f, track.height / density, .5f); assertEquals(112f, island.height / density, .5f)
        assertTrue(actions.bottom <= frame.bottom + 1f)
        assertEquals(12f, (island.top - frame.bottom) / density, .5f)
        if (rom) rule.onNodeWithTag("live-rom-slider").assertIsDisplayed()
        if (editRm) rule.onNodeWithTag("live-rm-percent").performScrollTo()
        val folder = File(checkNotNull(System.getenv("KPKN_RENDER_DIR"))).apply { mkdirs() }
        File(folder, "$name-medidas.txt").writeText("viewport=360x$height dp; status=45 dp; navigation=24 dp\n" +
            listOf("card" to frame, "actions" to actions, "line" to island, "effort-touch" to touch, "effort-track" to track).joinToString("\n") { (label, rect) ->
                "$label: x=${rect.left / density}, y=${rect.top / density}, width=${rect.width / density}, height=${rect.height / density} dp"
            })
        val bounds = rule.onNodeWithTag("lv-b-scene").fetchSemanticsNode().boundsInRoot
        val host = checkNotNull(view)
        val full = Bitmap.createBitmap(host.width, host.height, Bitmap.Config.ARGB_8888)
        rule.runOnUiThread { host.draw(android.graphics.Canvas(full)) }
        val left = bounds.left.toInt().coerceAtLeast(0); val top = bounds.top.toInt().coerceAtLeast(0)
        val cropped = Bitmap.createBitmap(full, left, top, bounds.width.toInt().coerceAtMost(full.width - left), bounds.height.toInt().coerceAtMost(full.height - top))
        File(folder, "$name.png").outputStream().use { assertTrue(cropped.compress(Bitmap.CompressFormat.PNG, 100, it)) }
        cropped.recycle(); full.recycle()
    }
}
