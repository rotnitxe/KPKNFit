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
        onRoute: () -> Unit = {}, onAdd: () -> Unit = {}, onLong: ((Int) -> Unit)? = null, enabled: Boolean = true) {
        LiveSessionLine(listOf(exercise, row), exercise.id, snapshot, onRoute, onPage, {}, {}, onRecord,
            recordVisible = true, recordEnabled = enabled, onAddSet = onAdd, onLongPressPage = onLong,
            modifier = Modifier.width(328.dp))
    }
    private fun assertLine(count: Int, active: Int, prep: Boolean) {
        content { Line(liveLineTestSnapshot(count, active, prep)) }
        val bounds = rule.onNodeWithTag("live-session-line").assertIsDisplayed().fetchSemanticsNode().boundsInRoot
        assertEquals(112f * density, bounds.height, 1f)
        val rail = rule.onNodeWithTag("live-session-steps").fetchSemanticsNode().boundsInRoot
        val selected = rule.onNode(isSelected() and hasAnyAncestor(hasTestTag("live-session-steps")))
            .assertIsDisplayed().fetchSemanticsNode().boundsInRoot
        assertEquals("El paso activo queda centrado, también en los extremos", rail.center.x, selected.center.x, 2f)
        val record = rule.onNodeWithTag("live-session-record").assertIsDisplayed().fetchSemanticsNode().boundsInRoot
        assertTrue(bounds.contains(record.topLeft) && bounds.contains(record.bottomRight - androidx.compose.ui.geometry.Offset(1f, 1f)))
        assertFalse(rail.overlaps(record))
    }
    @Test fun oneSeriesIsCenteredAndRecordLivesInsideTheIsland() = assertLine(1, 0, false)
    @Test fun fourSeriesWithPreparationStayInTheSameIsland() = assertLine(4, 0, true)
    @Test fun fourteenSeriesCompressDistantStepsAndCenterTheActiveOne() = assertLine(14, 7, true)
    @Test fun lastOfFourteenSeriesIsCenteredToo() = assertLine(14, 13, false)

    @Test fun compressionKeepsAllOriginalIndicesExactlyOnce() {
        for (count in listOf(1, 4, 14, 30)) for (active in 0 until count) {
            val ranges = liveSessionLineRanges(count, active, setOf(0, 1))
            assertEquals((0 until count).toList(), ranges.flatMap { it.indices })
            assertFalse(ranges.single { active in it.indices }.compressed)
            assertTrue(ranges.filter { it.compressed }.all { it.indices.count() > 0 })
        }
    }
    @Test fun selectionFollowsTheSettledCardWhileAnotherPageIsRequested() {
        content { Line(liveLineTestSnapshot(4, active = 3).copy(activePageIndex = 40)) }
        rule.onNodeWithContentDescription("S1").assertIsSelected()
        rule.onNodeWithTag("live-session-steps").performScrollToNode(hasContentDescription("S4"))
        rule.onNodeWithContentDescription("S4").assertIsNotSelected()
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
            Column(Modifier.width(328.dp)) {
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
    @Test fun addingAndLongPressKeepTheirOriginalPageDestinations() {
        var adds = 0; var page: Int? = null
        content { Line(liveLineTestSnapshot(4), onAdd = { adds++ }, onLong = { page = it }) }
        rule.onNodeWithTag("live-session-steps").performScrollToNode(hasContentDescription("S2"))
        rule.onNodeWithContentDescription("S2").performTouchInput { longClick() }
        rule.onNodeWithContentDescription("Añadir serie").performClick()
        rule.runOnIdle { assertEquals(41, page); assertEquals(1, adds) }
    }
    @Test fun preparationIconsKeepTheirNamesAndCallbacks() {
        var mobility = 0; var warmup = 0
        content { Line(liveLineTestSnapshot(4, preparation = true, mobility = { mobility++ }, warmup = { warmup++ })) }
        rule.onNodeWithTag("live-session-steps").performScrollToNode(hasContentDescription("Movilidad"))
        rule.onNodeWithContentDescription("Movilidad").performClick()
        rule.onNodeWithContentDescription("Aproximación").performClick()
        rule.runOnIdle { assertEquals(1, mobility); assertEquals(1, warmup) }
    }
    @Test fun horizontalGestureStillChangesExercisesWhenThereIsOnlyOneSeries() {
        val moves = mutableListOf<Boolean>()
        content { LiveSessionLine(listOf(exercise), exercise.id, liveLineTestSnapshot(1), {}, {}, {}, {}, {}, true, true,
            onNavigateAdjacentExercise = { moves += it }, modifier = Modifier.width(328.dp)) }
        rule.onNodeWithTag("live-session-steps").performTouchInput { swipeLeft() }
        rule.onNodeWithTag("live-session-steps").performTouchInput { swipeRight() }
        rule.runOnIdle { assertEquals(listOf(true, false), moves) }
    }
    @Test fun reversingAnEdgeDragBackIntoTheRailDoesNotChangeExercises() {
        val moves = mutableListOf<Boolean>()
        content { LiveSessionLine(listOf(exercise), exercise.id, liveLineTestSnapshot(4), {}, {}, {}, {}, {}, true, true,
            onNavigateAdjacentExercise = { moves += it }, modifier = Modifier.width(328.dp)) }
        val rail = rule.onNodeWithTag("live-session-steps")
        rail.performTouchInput {
            down(center)
            advanceEventTime(300)
            moveBy(androidx.compose.ui.geometry.Offset(120f * density, 0f))
            advanceEventTime(300)
            moveBy(androidx.compose.ui.geometry.Offset(-60f * density, 0f))
            advanceEventTime(300)
            up()
        }
        val range = rail.fetchSemanticsNode().config[SemanticsProperties.HorizontalScrollAxisRange]
        rule.runOnIdle {
            assertTrue("El gesto vuelve al interior de la línea", range.value() > 0f && range.value() < range.maxValue())
            assertTrue("El exceso anterior del borde no navega al soltar dentro", moves.isEmpty())
        }
    }
    @Test fun transientUndoAndBothPendingActionsFitInsideTheIslandWithoutCoveringTheCard() {
        var stack by mutableStateOf(emptyList<GodModeUndoSnapshot>())
        var undone: Int? = null
        content(1.3f) { LiveSessionLine(listOf(exercise), exercise.id, liveLineTestSnapshot(1), {}, {}, {}, {}, {}, true, true,
            onAddSet = {},
            pendingActions = listOf(RoadmapPendingAction("Estado previo") {}, RoadmapPendingAction("Terminar") {}),
            undoStack = stack, onUndo = { undone = it; stack = emptyList() }, modifier = Modifier.width(328.dp)) }
        rule.runOnIdle { stack = listOf(GodModeUndoSnapshot(label = "Añadir serie")) }
        val island = rule.onNodeWithTag("live-session-line").fetchSemanticsNode().boundsInRoot
        val undo = rule.onNodeWithContentDescription("Deshacer añadir serie").assertIsDisplayed()
        val bounds = undo.fetchSemanticsNode().boundsInRoot
        assertTrue(bounds.top >= island.top && bounds.bottom <= island.bottom)
        assertFalse(bounds.overlaps(rule.onNodeWithTag("live-session-record").fetchSemanticsNode().boundsInRoot))
        rule.onNodeWithText("Estado previo").assertIsDisplayed()
        rule.onNodeWithText("Terminar").assertIsDisplayed()
        for (label in listOf("Estado previo", "Terminar")) {
            val layouts = mutableListOf<androidx.compose.ui.text.TextLayoutResult>()
            rule.onNodeWithText(label, useUnmergedTree = true)
                .performSemanticsAction(androidx.compose.ui.semantics.SemanticsActions.GetTextLayoutResult) { it(layouts) }
            val layout = layouts.single()
            assertFalse("$label no se recorta al 130 %: size=${layout.size}, lines=${layout.lineCount}, font=${layout.layoutInput.style.fontSize}", layout.hasVisualOverflow)
        }
        rule.onNodeWithTag("live-session-add-set").assertIsDisplayed()
        undo.performClick()
        rule.runOnIdle { assertEquals(0, undone) }
    }
    @Test fun compressedGapOpensEveryStepIncludingTheDistantDestination() {
        var page: Int? = null
        content { Line(liveLineTestSnapshot(14, 7, true), onPage = { page = it }) }
        val gap = hasContentDescription("Ver 3 pasos lejanos")
        rule.onNodeWithTag("live-session-steps").performScrollToNode(gap)
        rule.onAllNodes(gap)[0].performClick()
        rule.onNodeWithText("Todos los pasos").assertIsDisplayed()
        rule.onNodeWithText("S12", useUnmergedTree = true).performScrollTo().performClick()
        rule.runOnIdle { assertEquals(51, page) }
    }
    @Test fun sidesAndRoundsKeepSeparateDestinationsAndTechniqueState() {
        var selected: Int? = null
        val snapshot = LiveSetStepperSnapshot(listOf(
            TimelineElement.RoundBadge(1, true, false, 17),
            TimelineElement.UnilateralSet(1, "S2", 17, WorkoutSetCardVisualState.ACTIVE,
                21, WorkoutSetCardVisualState.FUTURE, volumeReplaced = true)), 1, 0, 2, null, false)
        content { Line(snapshot, onPage = { selected = it }) }
        rule.onNodeWithContentDescription("Ronda 2, S2, lado derecho").performClick()
        rule.runOnIdle { assertEquals(21, selected) }
        rule.onNodeWithContentDescription("Ronda 2, S2, lado izquierdo")
            .assert(SemanticsMatcher.expectValue(SemanticsProperties.StateDescription, "Seleccionada, Técnica de volumen reemplazado"))
        rule.onNodeWithContentDescription("Ronda 2").performClick()
        rule.runOnIdle { assertEquals(17, selected) }
    }
    @Test fun restKeepsItsOwnDestinationAndActivePositionInsideTheLine() {
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
    @Test fun routeFromTheLineKeepsMetricsAndSelectsTheRealExercise() {
        var destination by mutableStateOf<LiveSessionDestination?>(null)
        var selected: String? = null
        content {
            Line(liveLineTestSnapshot(4), onRoute = { destination = LiveSessionDestination.ROUTE })
            LiveSessionSheetsHost(destination, { destination = it }, exercises = listOf(exercise, row),
                onSelectExercise = { selected = it })
        }
        rule.onNodeWithContentDescription("Abrir ruta de la sesión").performClick()
        rule.onNodeWithText("Tonelaje").assertIsDisplayed()
        rule.onNodeWithText("RIR medio").assertIsDisplayed()
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
    @Test fun voiceStoryAndMoreAreHeaderActions() {
        val clicks = mutableListOf<String>()
        content { WorkoutHeaderBar("Sentadilla", sessionName = "Pierna", groupName = null, startTimeMs = 0L,
            isComplete = false, background = null, sessionTimeRemainingSeconds = null, onAdjustTimeLimit = {},
            onToggleVoice = { clicks += "voice" }, onStoryClick = { clicks += "story" }, onMoreClick = { clicks += "more" }) }
        rule.onNodeWithTag("workout-voice-header").assertIsDisplayed().performClick()
        rule.onNodeWithContentDescription("Historia de la sesión").performClick()
        rule.onNodeWithContentDescription("Más opciones de sesión").performClick()
        rule.onNodeWithTag("workout-voice-fab").assertDoesNotExist()
        rule.runOnIdle { assertEquals(listOf("voice", "story", "more"), clicks) }
    }
}
