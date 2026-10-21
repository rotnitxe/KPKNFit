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
import com.example.kpkn.ui.theme.KPKNTheme
import org.junit.Assert.*
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith

/** Historical suite name retained for the orchestrator; checks V1 and the migrated sheets. */
@RunWith(AndroidJUnit4::class)
class WorkoutCockpitDeviceTest {
    @get:Rule val rule = createAndroidComposeRule<ComponentActivity>()
    private val lift = Exercise("lv11-lift", "Levantamiento LV11", sets = List(3) { ExerciseSet("s$it") })
    private val row = Exercise("lv11-row", "Remo LV11", sets = listOf(ExerciseSet("r")))
    private val sets = listOf(CompletedSet("s0", weight = 100.0, reps = 5, rpe = 8.0),
        CompletedSet("s1", weight = 80.0, reps = 10, rir = 1), CompletedSet("s2", weight = 50.0, reps = 4),
        CompletedSet("warmup", weight = 20.0, reps = 10, rpe = 3.0, isWarmup = true))
    private fun content(body: @Composable () -> Unit) {
        rule.setContent { KPKNTheme { MaterialTheme { Box(Modifier.fillMaxSize()) { body() } } } }
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
        rule.onNodeWithContentDescription("Abrir ruta de la sesión").performClick()
        rule.onNodeWithText("Ruta de la sesión").assertIsDisplayed()
        rule.onAllNodesWithContentDescription("Eliminar ejercicio").assertCountEquals(0)
        rule.onAllNodesWithContentDescription("Agregar ejercicio").assertCountEquals(0)
        rule.onNodeWithContentDescription("Cerrar Ruta de la sesión").performClick()
        rule.waitUntil(5_000) { destination == null }
        rule.onNodeWithContentDescription("Abrir ruta de la sesión").assertIsDisplayed()
    }
    @Test fun sessionKeepsItsMetricsAndRouteSelectsTheRealExercise() {
        var selected: String? = null
        content { LiveSessionSheetsHost(LiveSessionDestination.ROUTE, {}, exercises = listOf(lift, row),
            currentExerciseId = lift.id, onSelectExercise = { selected = it }) }
        rule.onNodeWithText("Tonelaje").assertExists(); rule.onNodeWithText("RIR medio").assertExists()
        rule.onAllNodesWithText("RINGS EN VIVO").assertCountEquals(0)
        rule.onNodeWithText("Remo LV11").performScrollTo().performClick()
        rule.runOnIdle { assertEquals(row.id, selected) }
    }
    @Test fun everyOldCockpitFunctionHasAnExplicitMenuDestination() {
        content { LiveSessionSheetsHost(LiveSessionDestination.MENU, {}) }
        for (item in LiveSessionDestination.entries.filter { it != LiveSessionDestination.MENU })
            rule.onNodeWithTag("live-session-menu-${item.name.lowercase()}").performScrollTo().assertIsDisplayed()
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
        rule.runOnUiThread { rule.activity.onBackPressedDispatcher.onBackPressed() }
        rule.waitUntil(5_000) { !child }
        rule.runOnIdle { assertNotNull(destination); assertEquals(0, exits) }
        rule.runOnUiThread { rule.activity.onBackPressedDispatcher.onBackPressed() }
        rule.waitUntil(5_000) { destination == null }
        rule.runOnIdle { assertEquals(0, exits) }
        rule.runOnUiThread { rule.activity.onBackPressedDispatcher.onBackPressed() }
        rule.runOnIdle { assertEquals(1, exits) }
    }
}
