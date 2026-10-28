package com.example.kpkn.screens.workout

import android.app.Application
import androidx.compose.foundation.layout.*
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.semantics.SemanticsProperties
import androidx.compose.ui.test.*
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.unit.Density
import androidx.compose.ui.unit.dp
import com.example.kpkn.data.models.*
import com.example.kpkn.screens.onboarding.design.LocalWizardReducedMotion
import com.example.kpkn.screens.workout.components.*
import com.example.kpkn.ui.components.LocalKpknOverlayHost
import com.example.kpkn.ui.theme.KPKNTheme
import org.junit.Assert.*
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import org.robolectric.annotation.GraphicsMode

internal fun liveLineTestSnapshot(count: Int, active: Int = 0, preparation: Boolean = false,
    mobility: () -> Unit = {}, warmup: () -> Unit = {}): LiveSetStepperSnapshot {
    val elements = buildList {
        if (preparation) {
            add(TimelineElement.MobilityPill(false, false, .5f, mobility))
            add(TimelineElement.WarmupPill(false, true, 1f, warmup))
        }
        repeat(count) { index -> add(TimelineElement.BilateralSet(pageIndex = 40 + index, label = "S${index + 1}",
            state = if (index == active) WorkoutSetCardVisualState.ACTIVE else WorkoutSetCardVisualState.FUTURE)) }
    }
    return LiveSetStepperSnapshot(elements, active + if (preparation) 2 else 0, 0, count, null, true)
}

@RunWith(RobolectricTestRunner::class)
@GraphicsMode(GraphicsMode.Mode.NATIVE)
@Config(sdk = [34], application = Application::class, qualifiers = "w360dp-h880dp-xxhdpi")
class LiveSessionLineTest {
    @get:Rule val rule = createComposeRule()
    private val exercise = Exercise("line-lift", "Sentadilla", sets = List(14) { ExerciseSet("set-$it", targetReps = 6, weight = 95.0) })
    private val row = Exercise("line-row", "Remo", sets = listOf(ExerciseSet("row-set")))
    private var density = 1f
    private fun content(fontScale: Float = 1f, body: @Composable () -> Unit) {
        rule.setContent {
            density = LocalDensity.current.density
            CompositionLocalProvider(LocalDensity provides Density(density, fontScale), LocalWizardReducedMotion provides true,
                LocalKpknOverlayHost provides null) { KPKNTheme { MaterialTheme { Box(Modifier.fillMaxSize()) { body() } } } }
        }
    }
    @Composable private fun Line(snapshot: LiveSetStepperSnapshot, onPage: (Int) -> Unit = {}, onRecord: () -> Unit = {},
        onRoute: () -> Unit = {}, onAdjacent: ((Boolean) -> Unit)? = null, enabled: Boolean = true,
        restActive: Boolean = false, onEndRest: (() -> Unit)? = null) {
        LiveSessionLine(listOf(exercise, row), exercise.id, snapshot, onRoute, onPage, {}, {}, onRecord,
            recordVisible = true, recordEnabled = enabled, onNavigateAdjacentExercise = onAdjacent,
            sessionCompletedCount = 2, sessionTotalCount = 15, restActive = restActive, onEndRest = onEndRest,
            modifier = Modifier.width(340.dp))
    }

    /** Roadmap del Refinamiento 02: alto fijo, resumen de la sesión, pista de fases y la fila ‹ registrar › dentro del vidrio. */
    private fun assertDock(count: Int, active: Int, prep: Boolean) {
        content { Line(liveLineTestSnapshot(count, active, prep)) }
        val bounds = rule.onNodeWithTag("live-session-line").assertIsDisplayed().fetchSemanticsNode().boundsInRoot
        assertEquals(LiveSessionLineHeight.value * density, bounds.height, 1f)
        rule.onNodeWithText("SERIES · 2 DE 15").assertIsDisplayed()
        rule.onNodeWithText(" ${active + 1} / 14").assertIsDisplayed()
        // Iteración 04: raíl de series; el punto en curso está seleccionado y el descanso solo aparece mientras dura.
        assertTrue(rule.onAllNodes(isSelected()).fetchSemanticsNodes().isNotEmpty())
        rule.onNodeWithTag("live-session-phase-rest").assertDoesNotExist()
        if (prep) {
            rule.onNodeWithTag("live-session-phase-mov").assertIsDisplayed()
            rule.onNodeWithTag("live-session-phase-apr").assertIsDisplayed()
        } else {
            rule.onNodeWithTag("live-session-phase-mov").assertDoesNotExist()
        }
        val record = rule.onNodeWithTag("live-session-record").assertIsDisplayed().fetchSemanticsNode().boundsInRoot
        assertTrue(bounds.contains(record.topLeft) && bounds.contains(record.bottomRight - androidx.compose.ui.geometry.Offset(1f, 1f)))
        val phases = rule.onNodeWithTag("live-session-steps").fetchSemanticsNode().boundsInRoot
        assertFalse(phases.overlaps(record))
    }
    @Test fun oneSeriesShowsTheDockWithRecordInside() = assertDock(1, 0, false)
    @Test fun preparationAddsMovAndAprPhases() = assertDock(4, 0, true)
    @Test fun fourteenSeriesKeepTheSameDock() = assertDock(14, 7, true)
    @Test fun lastOfFourteenSeriesShowsItsPosition() = assertDock(14, 13, false)

    @Test fun compressionKeepsAllOriginalIndicesExactlyOnce() {
        for (count in listOf(1, 4, 14, 30)) for (active in 0 until count) {
            val ranges = liveSessionLineRanges(count, active, setOf(0, 1))
            assertEquals((0 until count).toList(), ranges.flatMap { it.indices })
            assertFalse(ranges.single { active in it.indices }.compressed)
            assertTrue(ranges.filter { it.compressed }.all { it.indices.count() > 0 })
        }
    }
    @Test fun positionFollowsTheSettledCardWhileAnotherPageIsRequested() {
        content { Line(liveLineTestSnapshot(4, active = 3).copy(activePageIndex = 40)) }
        rule.onNodeWithText(" 1 / 14").assertIsDisplayed()
    }
    @Test fun integratedCheckInvokesOnlyTheActiveRecordingAction() {
        var records = 0; var routes = 0
        content { Line(liveLineTestSnapshot(4), onRecord = { records++ }, onRoute = { routes++ }) }
        rule.onNodeWithContentDescription("Registrar serie").performClick()
        rule.runOnIdle { assertEquals(1, records); assertEquals(0, routes) }
    }
    @Test fun integratedCheckUsesTheRealCardRecorderWithReportedEffort() {
        val holder = RecordActionHolder()
        var recorded: Triple<Double, Int, Double?>? = null
        val set = ExerciseSet("recorded-set", weight = 95.0, targetReps = 6, targetRIR = 2, intensityMode = IntensityMode.RIR)
        content {
            Column(Modifier.width(340.dp)) {
                Box(Modifier.width(312.dp).height(425.dp)) {
                    SetInputCardV2(exercise.copy(sets = listOf(set)), 0, set, null,
                        onShowHistory = {}, onSetBodyWeight = {}, initialBodyWeight = 80.0,
                        recordActionHolder = holder, recordActionPageKey = "integrated-check", hapticFeedbackEnabled = false,
                        initialDraft = WorkoutSetDraft(weightText = "95", valueText = "6", intensityText = "2"),
                        onRecordV2 = { _, _, weight, value, effort, _, _, _, _, result ->
                            recorded = Triple(weight, value.toInt(), effort)
                            result(RecordSetResult.Created("line-lift_0"))
                        })
                }
                Line(liveLineTestSnapshot(1), onRecord = { holder.actionForPage("integrated-check")?.invoke() })
            }
        }
        rule.onNodeWithTag("live-session-record").assertIsEnabled().performClick()
        rule.runOnIdle {
            assertEquals(95.0, recorded?.first ?: -1.0, .001)
            assertEquals(6, recorded?.second)
            assertEquals(2.0, recorded?.third ?: -1.0, .001)
        }
    }
    @Test fun recordingGateAlsoProtectsTheIntegratedCheck() {
        var records = 0
        content { Line(liveLineTestSnapshot(1), onRecord = { records++ }, enabled = false) }
        rule.onNodeWithTag("live-session-record").assertIsNotEnabled().performClick()
        rule.runOnIdle { assertEquals(0, records) }
    }
    @Test fun previousAndNextKeepTheOriginalPageDestinations() {
        val pages = mutableListOf<Int>()
        content { Line(liveLineTestSnapshot(4, active = 1), onPage = { pages += it }) }
        rule.onNodeWithContentDescription("Paso siguiente").performClick()
        rule.onNodeWithContentDescription("Paso anterior").performClick()
        rule.runOnIdle { assertEquals(listOf(42, 40), pages) }
    }
    @Test fun preparationPhasesKeepTheirCallbacks() {
        var mobility = 0; var warmup = 0
        content { Line(liveLineTestSnapshot(4, preparation = true, mobility = { mobility++ }, warmup = { warmup++ })) }
        rule.onNodeWithContentDescription("Movilidad").performClick()
        rule.onNodeWithContentDescription("Aproximaciones").performClick()
        rule.runOnIdle { assertEquals(1, mobility); assertEquals(1, warmup) }
    }
    @Test fun edgesOfTheExerciseChangeToTheAdjacentExercise() {
        val moves = mutableListOf<Boolean>()
        content { Line(liveLineTestSnapshot(1), onAdjacent = { moves += it }) }
        rule.onNodeWithContentDescription("Paso siguiente").performClick()
        rule.onNodeWithContentDescription("Paso anterior").performClick()
        rule.runOnIdle { assertEquals(listOf(true, false), moves) }
    }
    @Test fun transientUndoAndBothPendingActionsStayVisibleWithoutCoveringTheRecord() {
        var stack by mutableStateOf(emptyList<GodModeUndoSnapshot>())
        var undone: Int? = null
        content(1.3f) { LiveSessionLine(listOf(exercise), exercise.id, liveLineTestSnapshot(1), {}, {}, {}, {}, {}, true, true,
            pendingActions = listOf(RoadmapPendingAction("Estado previo") {}, RoadmapPendingAction("Terminar") {}),
            undoStack = stack, onUndo = { undone = it; stack = emptyList() },
            modifier = Modifier.padding(top = 80.dp).width(340.dp)) }
        rule.runOnIdle { stack = listOf(GodModeUndoSnapshot(label = "Añadir serie")) }
        val undo = rule.onNodeWithContentDescription("Deshacer añadir serie").assertIsDisplayed()
        assertFalse(undo.fetchSemanticsNode().boundsInRoot.overlaps(rule.onNodeWithTag("live-session-record").fetchSemanticsNode().boundsInRoot))
        for (label in listOf("Estado previo", "Terminar")) {
            val layouts = mutableListOf<androidx.compose.ui.text.TextLayoutResult>()
            rule.onNodeWithText(label, useUnmergedTree = true).assertIsDisplayed()
                .performSemanticsAction(androidx.compose.ui.semantics.SemanticsActions.GetTextLayoutResult) { it(layouts) }
            assertFalse("$label no se recorta al 130 %", layouts.single().hasVisualOverflow)
        }
        undo.performClick()
        rule.runOnIdle { assertEquals(0, undone) }
    }
    @Test fun sidesAndRoundsKeepSeparateDestinations() {
        val pages = mutableListOf<Int>()
        val snapshot = LiveSetStepperSnapshot(listOf(
            TimelineElement.RoundBadge(1, true, false, 17),
            TimelineElement.UnilateralSet(1, "S2", 17, WorkoutSetCardVisualState.ACTIVE,
                21, WorkoutSetCardVisualState.FUTURE, volumeReplaced = true)), 1, 0, 2, null, false)
        content { Line(snapshot, onPage = { pages += it }) }
        rule.onNodeWithContentDescription("Paso siguiente").performClick()
        rule.onNodeWithContentDescription("Paso anterior").performClick()
        rule.runOnIdle { assertEquals(listOf(21, 17), pages) }
    }
    @Test fun restPhaseIsActiveAndKeepsItsOwnDestination() {
        var selected: Int? = null
        val snapshot = LiveSetStepperSnapshot(listOf(
            TimelineElement.BilateralSet(pageIndex = 7, label = "S1", state = WorkoutSetCardVisualState.COMPLETED),
            TimelineElement.RestPill(88, .4f, "2:41"),
            TimelineElement.BilateralSet(pageIndex = 11, label = "S2", state = WorkoutSetCardVisualState.FUTURE)),
            1, 1, 2, null, false, activePageIndex = 88)
        content { Line(snapshot, onPage = { selected = it }) }
        rule.onNodeWithContentDescription("Descanso").assertIsSelected().performClick()
        rule.runOnIdle { assertEquals(88, selected) }
    }
    @Test fun runningRestTurnsThePrimaryIntoEndRest() {
        var ended = 0; var records = 0
        content { Line(liveLineTestSnapshot(4), onRecord = { records++ }, restActive = true, onEndRest = { ended++ }) }
        rule.onNodeWithContentDescription("Terminar descanso").performClick()
        rule.runOnIdle { assertEquals(1, ended); assertEquals(0, records) }
    }
    @Test fun routeFromTheLineKeepsMetricsAndSelectsTheRealExercise() {
        var destination by mutableStateOf<LiveSessionDestination?>(null)
        var selected: String? = null
        content {
            Line(liveLineTestSnapshot(4), onRoute = { destination = LiveSessionDestination.ROUTE })
            LiveSessionSheetsHost(destination, { destination = it }, exercises = listOf(exercise, row),
                onSelectExercise = { selected = it })
        }
        rule.onNodeWithContentDescription("Abrir ruta de la sesión").performClick()
        // Refinamiento 02: las métricas viven en «Resumen», al final del recorrido.
        rule.onNodeWithText("TONELAJE").assertExists()
        rule.onNodeWithText("RIR MEDIO").assertExists()
        rule.onNodeWithText("Remo").performScrollTo().performClick()
        rule.runOnIdle { assertEquals(row.id, selected); assertNull(destination) }
    }
    @Test fun compactActionRowKeepsAllThreeTargetsInsideTheCardAt130Percent() {
        val clicks = mutableListOf<String>()
        content(1.3f) { LiveSetCardActions({ clicks += "advanced" }, { clicks += "visual" }, { clicks += "history" }, Modifier.width(284.dp)) }
        val parent = rule.onNodeWithTag("live-card-actions").fetchSemanticsNode().boundsInRoot
        val children = listOf("secondary_pill_avanzado", "secondary_pill_modo_visual", "secondary_pill_historial").map { tag ->
            val control = rule.onNodeWithTag(tag).assertIsDisplayed()
            val rect = control.fetchSemanticsNode().boundsInRoot
            assertEquals(44f * density, rect.height, 1f)
            assertTrue(rect.left >= parent.left && rect.right <= parent.right)
            control.performClick(); rect
        }
        assertFalse(children[0].overlaps(children[1])); assertFalse(children[1].overlaps(children[2]))
        rule.runOnIdle { assertEquals(listOf("advanced", "visual", "history"), clicks) }
    }
    @Test fun voiceAndMoreAreHeaderActions() {
        val clicks = mutableListOf<String>()
        content { WorkoutHeaderBar("Sentadilla", sessionName = "Pierna", groupName = null, startTimeMs = 0L,
            isComplete = false, background = null, sessionTimeRemainingSeconds = null, onAdjustTimeLimit = {},
            onToggleVoice = { clicks += "voice" }, onStoryClick = { clicks += "story" }, onMoreClick = { clicks += "more" }) }
        rule.onNodeWithTag("workout-voice-header").assertIsDisplayed().performClick()
        rule.onNodeWithContentDescription("Más opciones de sesión").performClick()
        rule.onNodeWithTag("workout-voice-fab").assertDoesNotExist()
        rule.runOnIdle { assertEquals(listOf("voice", "more"), clicks) }
    }
}
