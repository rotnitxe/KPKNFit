package com.example.kpkn.screens.workout

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
import androidx.compose.ui.test.junit4.ComposeContentTestRule
import androidx.compose.ui.unit.Density
import androidx.compose.ui.unit.dp
import com.example.kpkn.data.models.*
import com.example.kpkn.screens.onboarding.design.LocalWizardReducedMotion
import com.example.kpkn.screens.workout.components.*
import com.example.kpkn.ui.components.LocalKpknOverlayHost
import com.example.kpkn.ui.theme.KPKNTheme
import org.junit.Assert.*
import java.io.File

/** The same V7 interactions and frame assertions run on JVM and in the orchestrator APK. */
internal class Cal3CardContract(private val rule: ComposeContentTestRule,
    private val captureImage: ((File, String) -> Unit)? = null) {
    private val holder = RecordActionHolder()
    private lateinit var state: MutableState<WorkoutUiState>
    private lateinit var exercise: Exercise
    private var host: View? = null
    private var density = 1f
    private var recordCount = 0
    private var continued = false

    fun show(fixture: Cal3Fixture = cal3Fixture(), height: Int = 880, before: Boolean = false, font: Float = 1f,
        normalAmrap: Boolean = false) {
        exercise = fixture.exercise
        if (normalAmrap) exercise = exercise.copy(sets = exercise.sets.map { it.copy(purpose = if(it.purpose == SetPurpose.CALIBRATOR) SetPurpose.AMRAP else it.purpose) })
        val initial = if (before) fixture.state.copy(completedSets = emptyMap(), calibrationDecisions = emptyList()) else fixture.state
        state = mutableStateOf(if (normalAmrap) initial.copy(calibrationDecisions = emptyList(), completedSets = initial.completedSets.mapValues {
            it.value.copy(purpose = SetPurpose.AMRAP) }) else initial)
        rule.setContent {
            host = LocalView.current
            density = LocalDensity.current.density
            CompositionLocalProvider(LocalDensity provides Density(density, font), LocalWizardReducedMotion provides true,
                LocalKpknOverlayHost provides null, LocalLiveSessionCompactLayout provides (height < 800)) {
                KPKNTheme { MaterialTheme {
                    val ui = state.value
                    val done = ui.completedSets[workoutSetKey(exercise.id, 0)]
                    val record = ui.calibrationDecisions.lastOrNull { it.status != CalibrationStatus.SUPERSEDED }
                    val snapshot = LiveSetStepperSnapshot(List(4) { i -> TimelineElement.BilateralSet(pageIndex = i, label = "S${i + 1}",
                        state = if(i == 0 && done != null) WorkoutSetCardVisualState.COMPLETED else if(i == 0) WorkoutSetCardVisualState.ACTIVE else WorkoutSetCardVisualState.FUTURE) },
                        0, if(done == null) 0 else 1, 4, null, true, calibratedPageLoads = (1..3).mapNotNull { i ->
                            calibrationLoadForSet(ui, exercise, i, null)?.takeIf { it.calibrated }?.let { i to it.weight }
                        }.toMap())
                    Box(Modifier.requiredSize(360.dp, height.dp).background(Color.Black).testTag("cal3-scene")) {
                        var headerPx by remember { mutableIntStateOf(0) }
                        val localDensity = LocalDensity.current
                        val header = with(localDensity) { headerPx.toDp() }
                        val band = liveSessionRelatorBandHeight(height.toFloat(), header.value, 394f, 144f).dp
                        Box(Modifier.align(Alignment.TopCenter).padding(top = 45.dp).onSizeChanged { headerPx = it.height + with(localDensity) { 45.dp.roundToPx() } }) {
                            WorkoutHeaderBar(exercise.name, sessionName = "Fuerza A", groupName = "Principal", startTimeMs = 0,
                                isComplete = false, background = null, sessionTimeRemainingSeconds = null, onAdjustTimeLimit = {},
                                onToggleVoice = {}, onStoryClick = {}, onMoreClick = {}, compactLayout = height < 800,
                                statusBarInsets = WindowInsets(0,0,0,0))
                        }
                        WorkoutLiveRelatorLine(if(done == null) "Haz al menos 6. Cuenta todas las que completes." else "Resultado registrado. Revisa lo que sigue.",
                            "cal3", modifier = Modifier.align(Alignment.TopCenter).offset(y = header).height(band))
                        Box(Modifier.align(Alignment.BottomCenter).padding(bottom = LiveSessionLineHeight + 44.dp).width(312.dp)) {
                            LiveSessionCardFrame {
                                SetInputCardV2(exercise, 0, exercise.sets.first(), null, sessionCompletedSet = done,
                                    onShowHistory = {}, onSetBodyWeight = {}, initialBodyWeight = 75.0,
                                    recordActionHolder = holder, recordActionPageKey = "cal3",
                                    initialDraft = if(done == null) WorkoutSetDraft(weightText = "69", valueText = "10", isDirty = true) else null,
                                    amrapResultPanel = if(done != null) ({ edit ->
                                        LiveCalibrationResult(ui, exercise, done, record,
                                            LiveAmrapProgramPreview(AutoregulationMode.OFF, false),
                                            onResolve = { id, status -> state.value = resolveCalibration(state.value, id, status, System.currentTimeMillis()) },
                                            onContinue = { continued = true }, onEdit = edit)
                                    }) else null,
                                    onRecordV2 = { mode, _, weight, value, _, advanced, _, _, side, result ->
                                        recordCount++
                                        val completed = (done ?: fixture.completed).copy(weight = weight, originalLoad = weight, reps = value.toInt(),
                                            rir = advanced.rir, rpe = null, isFailure = advanced.reachedFailure,
                                            loadMode = mode, side = side, purpose = if(normalAmrap) SetPurpose.AMRAP else SetPurpose.CALIBRATOR)
                                        val captured = state.value.copy(completedSets = state.value.completedSets + (workoutSetKey(exercise.id,0) to completed))
                                        val next = if(normalAmrap) null else buildRecordedCalibration(captured, exercise, 0, completed, emptyList(), emptyList(), System.currentTimeMillis())
                                        state.value = captured.copy(calibrationDecisions = replaceRecordedCalibration(captured.calibrationDecisions, completed.id, next, System.currentTimeMillis()))
                                        result(RecordSetResult.Created(workoutSetKey(exercise.id, 0)))
                                    }, effortFireSecondsOverride = 1f)
                            }
                        }
                        LiveSessionLine(listOf(exercise), exercise.id, snapshot, {}, {}, {}, {},
                            onRecord = { holder.actionForPage("cal3")?.invoke() }, recordVisible = true, recordEnabled = true,
                            modifier = Modifier.align(Alignment.BottomCenter).padding(start = 16.dp, end = 16.dp, bottom = 32.dp))
                    }
                } }
            }
        }
        rule.waitForIdle()
    }

    fun assertFrame() {
        val frame = rule.onNodeWithTag("live-card-frame").assertIsDisplayed().fetchSemanticsNode().boundsInRoot
        val line = rule.onNodeWithTag("live-session-line").assertIsDisplayed().fetchSemanticsNode().boundsInRoot
        assertEquals(312f, frame.width / density, .5f); assertEquals(394f, frame.height / density, .5f)
        assertEquals(LiveSessionLineHeight.value, line.height / density, .5f); assertEquals(12f, (line.top - frame.bottom) / density, .5f)
        listOf("calibration-apply", "calibration-keep", "calibration-revert", "calibration-continue", "calibration-edit").forEach { tag ->
            rule.onAllNodesWithTag(tag).fetchSemanticsNodes().forEach { node ->
                val b = node.boundsInRoot
                assertTrue("$tag dentro de la tarjeta", b.left >= frame.left && b.right <= frame.right + 1 && b.top >= frame.top && b.bottom <= frame.bottom + 1)
                assertTrue("$tag objetivo de 44 dp", b.height / density >= 44f - .5f)
            }
        }
    }

    fun walkthrough(height: Int, output: File? = null) {
        show(height = height, before = true)
        rule.onNodeWithTag("amrap-purpose-badge").assertTextEquals("Mínimo 6 · Calibrador")
        assertFrame(); output?.let { save(it, "v7-$height-before") }
        rule.onNodeWithTag(EffortSliderTag).assertIsDisplayed()
        val range = rule.onNodeWithTag(EffortSliderTag).fetchSemanticsNode().config[SemanticsProperties.ProgressBarRangeInfo]
        rule.onNodeWithTag(EffortSliderTag).performSemanticsAction(SemanticsActions.SetProgress) { it(range.range.endInclusive) }
        rule.onNodeWithTag("live-session-record").performClick()
        rule.onNodeWithTag("calibration-result").assertIsDisplayed()
        rule.onNodeWithText("Aplicar a S2–S4").assertIsDisplayed()
        rule.onAllNodesWithText("60 → 62,5 kg").assertCountEquals(3)
        assertEquals(1, recordCount)
        assertFrame(); output?.let { save(it, "v7-$height-proposed") }
        rule.onNodeWithTag("calibration-apply").performClick()
        assertEquals(CalibrationStatus.APPLIED, state.value.calibrationDecisions.last().status)
        rule.onAllNodesWithContentDescription("calibrada", substring = true).assertCountEquals(3)
        assertFrame(); output?.let { save(it, "v7-$height-applied") }
        rule.onNodeWithTag("calibration-revert").performClick()
        assertEquals(CalibrationStatus.REVERTED, state.value.calibrationDecisions.last().status)
        assertTrue(state.value.calibrationDecisions.last().decision.affectedSets.all { it.beforeKg == 60.0 })
        rule.onNodeWithTag("calibration-edit").performClick()
        rule.onNodeWithTag(EffortSliderTag).performSemanticsAction(SemanticsActions.SetProgress) { it(range.range.endInclusive - 2) }
        rule.onNodeWithTag("live-session-record").performClick()
        assertEquals(2, recordCount)
        assertEquals(CalibrationStatus.SUPERSEDED, state.value.calibrationDecisions.first().status)
        rule.onNodeWithTag("calibration-keep").performClick()
        assertEquals(CalibrationStatus.KEPT, state.value.calibrationDecisions.last().status)
        rule.onNodeWithTag("calibration-continue").performClick()
        assertTrue(continued)
        assertFrame()
    }

    fun save(dir: File, name: String) {
        dir.mkdirs()
        val bounds = rule.onNodeWithTag("cal3-scene").fetchSemanticsNode().boundsInRoot
        if (captureImage != null) captureImage.invoke(dir, name) else {
            val view = checkNotNull(host)
            val full = Bitmap.createBitmap(view.width, view.height, Bitmap.Config.ARGB_8888)
            rule.runOnUiThread { view.draw(android.graphics.Canvas(full)) }
            val left = bounds.left.toInt().coerceAtLeast(0); val top = bounds.top.toInt().coerceAtLeast(0)
            val crop = Bitmap.createBitmap(full, left, top, bounds.width.toInt().coerceAtMost(full.width-left), bounds.height.toInt().coerceAtMost(full.height-top))
            File(dir, "$name.png").outputStream().use { assertTrue(crop.compress(Bitmap.CompressFormat.PNG, 100, it)) }
            crop.recycle(); full.recycle()
        }
        val tags = listOf("live-card-frame", "live-session-line", "calibration-result", "calibration-apply", "calibration-keep", "calibration-revert")
        File(dir, "$name-medidas.txt").writeText(tags.flatMap { tag -> rule.onAllNodesWithTag(tag).fetchSemanticsNodes().map {
            val b = it.boundsInRoot; "$tag: x=${b.left/density}, y=${b.top/density}, width=${b.width/density}, height=${b.height/density} dp"
        } }.joinToString("\n"))
    }
}
