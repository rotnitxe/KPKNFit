package com.example.kpkn.screens.workout.components

import android.graphics.Bitmap
import androidx.activity.ComponentActivity
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.asAndroidBitmap
import androidx.compose.ui.layout.onSizeChanged
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.semantics.SemanticsActions
import androidx.compose.ui.semantics.SemanticsProperties
import androidx.compose.ui.test.*
import androidx.compose.ui.test.junit4.createAndroidComposeRule
import androidx.compose.ui.text.TextLayoutResult
import androidx.compose.ui.unit.Density
import androidx.compose.ui.unit.dp
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import com.example.kpkn.data.models.*
import com.example.kpkn.screens.onboarding.design.LocalWizardReducedMotion
import com.example.kpkn.screens.workout.*
import com.example.kpkn.testutil.configureLiveSheetTestWindow
import com.example.kpkn.ui.components.LocalKpknOverlayHost
import com.example.kpkn.ui.theme.KPKNTheme
import org.junit.Assert.*
import org.junit.Before
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import java.io.File

/** Compile here; the orchestrator runs this suite on its 360x880 emulator. 740dp cases restrict the real content. */
@RunWith(AndroidJUnit4::class)
class LiveSetModesDeviceTest {
    @get:Rule val rule = createAndroidComposeRule<ComponentActivity>()
    private val holder = RecordActionHolder()
    private var density = 1f
    private var now = 1_000L
    private val records = mutableListOf<Triple<UnitModeV2, Double, SetAdvancedFeedback>>()
    @Before fun configureWindow() { rule.activityRule.scenario.onActivity { it.configureLiveSheetTestWindow() } }

    @Test fun reps880() = render(TrainingMode.REPS, 880)
    @Test fun reps740() = render(TrainingMode.REPS, 740)
    @Test fun rm880() = render(TrainingMode.RM, 880)
    @Test fun rm740() = render(TrainingMode.RM, 740)
    @Test fun soloRpe880() = render(TrainingMode.SOLO_RPE, 880)
    @Test fun soloRpe740() = render(TrainingMode.SOLO_RPE, 740)
    @Test fun time880() = render(TrainingMode.TIME, 880)
    @Test fun time740() = render(TrainingMode.TIME, 740)
    @Test fun distance880() = render(TrainingMode.DISTANCE, 880)
    @Test fun distance740() = render(TrainingMode.DISTANCE, 740)
    @Test fun custom880() = render(TrainingMode.CUSTOM, 880)
    @Test fun custom740() = render(TrainingMode.CUSTOM, 740)
    @Test fun rom880() = render(TrainingMode.REPS, 880, true)
    @Test fun rom740() = render(TrainingMode.REPS, 740, true)
    @Test fun rmLinkedEditor740() = render(TrainingMode.RM, 740, editRm = true)
    @Test fun rmLinkedEditor740WithLargeText() = render(TrainingMode.RM, 740, font = 1.3f, editRm = true)

    private fun exercise(mode: TrainingMode, rom: Boolean): Exercise {
        val set = ExerciseSet("lv-b-set", targetReps = 7,
            weight = when (mode) { TrainingMode.TIME, TrainingMode.CUSTOM -> 0.0; TrainingMode.DISTANCE -> 24.0; else -> 100.0 },
            targetPercentageRM = 80.0.takeIf { mode == TrainingMode.RM }, targetDuration = 60.takeIf { mode == TrainingMode.TIME },
            plannedTargetV2 = when (mode) { TrainingMode.DISTANCE -> 40.0; TrainingMode.CUSTOM -> 30.0; else -> null },
            targetRIR = 2.takeIf { mode !in listOf(TrainingMode.RM, TrainingMode.SOLO_RPE, TrainingMode.TIME) },
            targetRPE = 8.0.takeIf { mode == TrainingMode.SOLO_RPE || mode == TrainingMode.TIME },
            intensityMode = when (mode) { TrainingMode.RM -> IntensityMode.SOLO_RM; TrainingMode.SOLO_RPE, TrainingMode.TIME -> IntensityMode.RPE; else -> IntensityMode.RIR },
            loadModeV2 = if (mode == TrainingMode.TIME || mode == TrainingMode.CUSTOM) LoadModeV2.BODYWEIGHT else LoadModeV2.LOAD)
        return Exercise("lv-b-exercise", when (mode) { TrainingMode.TIME -> "Plancha"; TrainingMode.DISTANCE -> "Paseo del granjero"; TrainingMode.CUSTOM -> "Remo ergómetro"; TrainingMode.SOLO_RPE -> "Elevaciones laterales"; else -> "Press banca" },
            trainingMode = mode, customUnit = "brazadas".takeIf { mode == TrainingMode.CUSTOM }, reference1RM = 125.0.takeIf { mode == TrainingMode.RM },
            trackRom = rom, sets = listOf(set))
    }
    private fun show(mode: TrainingMode, height: Int, rom: Boolean = false, font: Float = 1f) {
        val exercise = exercise(mode, rom)
        val snapshot = LiveSetStepperSnapshot(List(4) { index -> TimelineElement.BilateralSet(pageIndex = index, label = "S${index + 1}",
            state = if (index == 0) WorkoutSetCardVisualState.ACTIVE else WorkoutSetCardVisualState.FUTURE) }, 0, 0, 4, null, true)
        rule.setContent {
            density = LocalDensity.current.density
            CompositionLocalProvider(LocalDensity provides Density(density, font), LocalWizardReducedMotion provides true, LocalKpknOverlayHost provides null,
                LocalLiveSessionCompactLayout provides (height < 800)) {
                KPKNTheme { MaterialTheme {
                    Box(Modifier.requiredSize(360.dp, height.dp).background(Color.Black).testTag("lv-b-device-scene")) {
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
                        WorkoutLiveRelatorLine("Registra el resultado realizado de tu serie.", "lv-b-device",
                            modifier = Modifier.align(Alignment.TopCenter).offset(y = header).height(band))
                        Box(Modifier.align(Alignment.BottomCenter).padding(bottom = 156.dp).width(312.dp)) {
                            LiveSessionCardFrame {
                                SetInputCardV2(exercise, 0, exercise.sets.single(), null,
                                    onShowHistory = {}, onSetBodyWeight = {}, initialBodyWeight = 80.0,
                                    recordActionHolder = holder, recordActionPageKey = "device",
                                    clockNowMillis = { now }, effortFireSecondsOverride = 1.7f,
                                    onRecordV2 = { _, unit, _, value, _, advanced, _, _, _, result ->
                                        records += Triple(unit, value, advanced); result(RecordSetResult.Created("lv-b-exercise_0"))
                                    })
                            }
                        }
                        LiveSessionLine(listOf(exercise), exercise.id, snapshot, onOpenRoute = {}, onSelectPage = {}, onSelectExercise = {}, onOpenExerciseOptions = {},
                            onRecord = { holder.actionForPage("device")?.invoke() }, recordVisible = true, recordEnabled = true,
                            onAddSet = {}, modifier = Modifier.align(Alignment.BottomCenter).padding(start = 16.dp, end = 16.dp, bottom = 32.dp))
                    }
                } }
            }
        }
        rule.waitForIdle()
    }
    @OptIn(ExperimentalTestApi::class)
    private fun render(mode: TrainingMode, height: Int, rom: Boolean = false, font: Float = 1f, editRm: Boolean = false) {
        show(mode, height, rom, font)
        if (editRm) rule.onNodeWithTag("live-rm-edit").performClick()
        val frame = rule.onNodeWithTag("live-card-frame").assertIsDisplayed().fetchSemanticsNode().boundsInRoot
        val actions = rule.onNodeWithTag("live-card-actions").assertIsDisplayed().fetchSemanticsNode().boundsInRoot
        val line = rule.onNodeWithTag("live-session-line").assertIsDisplayed().fetchSemanticsNode().boundsInRoot
        if (editRm) rule.onNodeWithTag(EffortSliderTag).performScrollTo()
        val effort = rule.onNodeWithTag(EffortSliderTag).assertIsDisplayed().fetchSemanticsNode().boundsInRoot
        assertEquals(312f, frame.width / density, .5f); assertEquals(425f, frame.height / density, .5f)
        assertEquals(44f, actions.height / density, .5f); assertEquals(44f, effort.height / density, .5f)
        assertEquals(18f, rule.onNodeWithTag("effort-fire-track", useUnmergedTree = true).fetchSemanticsNode().boundsInRoot.height / density, .5f)
        assertEquals(112f, line.height / density, .5f)
        assertTrue(actions.left >= frame.left && actions.right <= frame.right && actions.bottom <= frame.bottom + 1)
        assertEquals(12f, (line.top - frame.bottom) / density, .5f)
        val narrator = rule.onNodeWithText("Registra el resultado realizado de tu serie.", useUnmergedTree = true).assertIsDisplayed()
        val layouts = mutableListOf<TextLayoutResult>()
        narrator.performSemanticsAction(SemanticsActions.GetTextLayoutResult) { it(layouts) }
        val layout = layouts.single()
        assertTrue(layout.getLineBottom(layout.lineCount - 1) <= layout.size.height + 1f)
        assertTrue(narrator.fetchSemanticsNode().boundsInRoot.bottom <= frame.top)
        if (rom) rule.onNodeWithTag("live-rom-slider").assertIsDisplayed()
        val context = InstrumentationRegistry.getInstrumentation().targetContext
        val folder = File(context.getExternalFilesDir(null), "lv-b-renders").apply { mkdirs() }
        if (editRm) rule.onNodeWithTag("live-rm-percent").performScrollTo()
        val name = "lv-b-${if (rom) "rom" else if (editRm) "rm-editor" else mode.name.lowercase()}-$height" +
            if (font == 1f) "" else "-${(font * 100).toInt()}"
        val bitmap = rule.onNodeWithTag("lv-b-device-scene").captureToImage().asAndroidBitmap()
        File(folder, "$name.png").outputStream().use { assertTrue(bitmap.compress(Bitmap.CompressFormat.PNG, 100, it)) }
        File(folder, "$name-medidas.txt").writeText("card=${frame.width / density}x${frame.height / density}; actions=${actions.height / density}; line=${line.height / density}; effort=${effort.height / density}")
    }
    @Test fun customQuantityAndUnitAreRegisteredExactlyOnceThroughTheLine() {
        show(TrainingMode.CUSTOM, 880)
        rule.onNodeWithTag("live-quantity-input").performTextReplacement("31.5")
        rule.onNodeWithTag(EffortSliderTag).performScrollTo().performSemanticsAction(SemanticsActions.SetProgress) { it(8f) }
        rule.onNodeWithTag("live-session-record").performClick()
        assertEquals(1, records.size); assertEquals(UnitModeV2.CUSTOM, records.single().first); assertEquals(31.5, records.single().second, 0.0)
    }
    @Test fun completingTheTimeGoalWaitsForTheLineCheck() {
        show(TrainingMode.TIME, 880)
        rule.onNodeWithContentDescription("Iniciar").performClick()
        rule.runOnIdle { now += 60_000 }
        rule.waitUntil(5_000) { rule.onAllNodesWithContentDescription("Detener").fetchSemanticsNodes().isEmpty() }
        assertTrue(records.isEmpty())
        rule.onNodeWithTag("live-quantity-input").assertTextEquals("1:00")
        rule.onNodeWithTag(EffortSliderTag).performScrollTo().performSemanticsAction(SemanticsActions.SetProgress) { it(16f) }
        rule.onNodeWithTag("live-session-record").performClick()
        assertEquals(UnitModeV2.TIME, records.single().first); assertEquals(60.0, records.single().second, 0.0)
    }
}
