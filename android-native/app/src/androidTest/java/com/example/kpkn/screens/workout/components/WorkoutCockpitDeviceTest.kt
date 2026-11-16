package com.example.kpkn.screens.workout.components

import androidx.activity.ComponentActivity
import androidx.activity.compose.BackHandler
import androidx.compose.foundation.layout.*
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.test.*
import androidx.compose.ui.test.junit4.createAndroidComposeRule
import androidx.compose.ui.unit.dp
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.example.kpkn.data.models.*
import com.example.kpkn.screens.workout.*
import com.example.kpkn.testutil.configureLiveSheetTestWindow
import com.example.kpkn.ui.components.KpknOverlayHostContent
import com.example.kpkn.ui.components.LocalKpknOverlayHost
import com.example.kpkn.ui.components.rememberKpknOverlayHostController
import com.example.kpkn.ui.theme.KPKNTheme
import org.junit.Assert.*
import org.junit.Before
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith

/** Historical suite name retained for the orchestrator; checks V1 and the migrated sheets. */
@RunWith(AndroidJUnit4::class)
class WorkoutCockpitDeviceTest {
    @get:Rule val rule = createAndroidComposeRule<ComponentActivity>()
    @Before fun configureWindow() { rule.activityRule.scenario.onActivity { it.configureLiveSheetTestWindow() } }
    private val lift = Exercise("lv11-lift", "Levantamiento LV11", sets = List(3) { ExerciseSet("s$it") })
    private val row = Exercise("lv11-row", "Remo LV11", sets = listOf(ExerciseSet("r")))
    private val sets = listOf(CompletedSet("s0", weight = 100.0, reps = 5, rpe = 8.0),
        CompletedSet("s1", weight = 80.0, reps = 10, rir = 1), CompletedSet("s2", weight = 50.0, reps = 4),
        CompletedSet("warmup", weight = 20.0, reps = 10, rpe = 3.0, isWarmup = true))
    private fun content(body: @Composable () -> Unit) {
        rule.setContent { KPKNTheme { MaterialTheme {
            val overlayHost = rememberKpknOverlayHostController()
            CompositionLocalProvider(LocalKpknOverlayHost provides overlayHost) {
                Box(Modifier.fillMaxSize()) { body(); KpknOverlayHostContent(overlayHost) }
            }
        } } }
    }
    @Test fun metricsKeepRealTonnageAndOnlyAverageReportedEffort() {
        val metrics = cockpitSessionMetrics(listOf(lift), sets.take(3).mapIndexed { index, set -> "${lift.id}_$index" to set }.toMap(),
            listOf(CompletedExercise(lift.id, lift.name, sets = sets)), emptySet(), SessionEnergySummary(), 80.0)
        assertEquals(3, metrics.completed); assertEquals(3, metrics.total)
        assertEquals(1500.0, metrics.tonnage, .0001); assertEquals(1.5, metrics.averageRir!!, .0001)
        assertNull("sin estimación real no se inventan kcal", metrics.calories.kcal)
    }
    @Test fun routeOpensFromTheIntegratedLineAndClosesWithoutEditingChrome() {
        var destination by mutableStateOf<LiveSessionDestination?>(null)
        val snapshot = LiveSetStepperSnapshot(listOf(TimelineElement.BilateralSet(pageIndex = 0, label = "S1", state = WorkoutSetCardVisualState.ACTIVE)), 0, 0, 1, null, true)
        content {
            LiveSessionLine(listOf(lift, row), lift.id, snapshot, { destination = LiveSessionDestination.ROUTE }, {}, {}, {}, {}, true, true,
                modifier = Modifier.width(328.dp))
            LiveSessionSheetsHost(destination, { destination = it }, exercises = listOf(lift, row))
        }
        rule.onNodeWithTag("live-session-route").performClick()
        rule.waitForIdle()
        rule.onNodeWithTag("live-session-sheet-route", useUnmergedTree = true).assertIsDisplayed()
        rule.onNodeWithText("Añadir ejercicio").assertIsDisplayed()
        rule.onAllNodesWithContentDescription("Eliminar ejercicio").assertCountEquals(0)
        rule.onAllNodesWithContentDescription("Agregar ejercicio").assertCountEquals(0)
        rule.onNodeWithContentDescription("Cerrar panel").performClick()
        rule.waitUntil(5_000) { destination == null }
        rule.onNodeWithTag("live-session-route").assertIsDisplayed()
    }
    @Test fun sessionKeepsItsMetricsAndRouteSelectsTheRealExercise() {
        var selected: String? = null
        content { LiveSessionSheetsHost(LiveSessionDestination.ROUTE, {}, exercises = listOf(lift, row),
            currentExerciseId = lift.id, onSelectExercise = { selected = it }) }
        rule.onNodeWithText("TONELAJE", useUnmergedTree = true).performScrollTo().assertIsDisplayed()
        rule.onNodeWithText("RIR MEDIO", useUnmergedTree = true).performScrollTo().assertIsDisplayed()
        rule.onAllNodesWithText("RINGS EN VIVO").assertCountEquals(0)
        rule.onNodeWithText("Remo LV11").performScrollTo().performClick()
        rule.runOnIdle { assertEquals(row.id, selected) }
    }
    @Test fun everyCurrentCockpitMenuDestinationIsReachable() {
        var destination by mutableStateOf<LiveSessionDestination?>(LiveSessionDestination.MENU)
        content { LiveSessionSheetsHost(destination, { destination = it }) }
        listOf("view", "route", "voice", "notes", "photos", "timer", "milestones", "rm", "assistance").forEach { destination ->
            rule.onNodeWithTag("live-session-menu-$destination").performScrollTo().assertIsDisplayed()
        }
        rule.onNodeWithTag("live-session-menu-assistance").performScrollTo().performClick()
        rule.waitUntil(5_000) { destination == LiveSessionDestination.ASSISTANCE }
        rule.onNodeWithTag("live-assistance-analysis", useUnmergedTree = true).performScrollTo().assertIsDisplayed()
    }
    @Test fun anEmptySessionStillHasAVisibleAddActionInTheRoute() {
        var adds = 0
        content { LiveSessionSheetsHost(LiveSessionDestination.ROUTE, {}, onAddExercise = { adds++ }) }
        rule.onNodeWithText("Añadir ejercicio").performScrollTo().performClick()
        rule.runOnIdle { assertEquals(1, adds) }
    }
    @Test fun backClosesTheHigherSheetThenTheRouteBeforeExiting() {
        var destination by mutableStateOf<LiveSessionDestination?>(LiveSessionDestination.ROUTE)
        var child by mutableStateOf(true)
        var exits = 0
        content {
            BackHandler { exits++ }
            LiveSessionSheetsHost(destination, { destination = it }, exercises = listOf(lift))
            if (child) HeaderSheet({ child = false }) { Text("Hoja superior") }
        }
        androidx.test.espresso.Espresso.pressBack()
        rule.waitUntil(5_000) { !child }
        rule.runOnIdle { assertNotNull(destination); assertEquals(0, exits) }
        androidx.test.espresso.Espresso.pressBack()
        rule.waitUntil(5_000) { destination == null }
        rule.runOnIdle { assertEquals(0, exits) }
        androidx.test.espresso.Espresso.pressBack()
        rule.runOnIdle { assertEquals(1, exits) }
    }
}
