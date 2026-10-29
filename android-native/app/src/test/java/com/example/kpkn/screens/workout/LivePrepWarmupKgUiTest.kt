package com.example.kpkn.screens.workout

import android.app.Application
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.requiredSize
import androidx.compose.foundation.layout.width
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateListOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.runtime.snapshots.SnapshotStateList
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.assertIsNotEnabled
import androidx.compose.ui.test.assertTextEquals
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onAllNodesWithText
import androidx.compose.ui.test.onNodeWithContentDescription
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performTextReplacement
import androidx.compose.ui.unit.dp
import com.example.kpkn.data.models.MobilitySeries
import com.example.kpkn.data.models.WarmupSetDefinition
import com.example.kpkn.screens.onboarding.design.LocalWizardReducedMotion
import com.example.kpkn.screens.workout.components.MobilityPhaseLiveCard
import com.example.kpkn.screens.workout.components.WarmupPhaseLiveCard
import com.example.kpkn.screens.workout.components.WarmupPhaseRow
import com.example.kpkn.screens.workout.components.WorkoutMobilityChecklistItem
import com.example.kpkn.ui.components.LocalKpknOverlayHost
import com.example.kpkn.ui.theme.KPKNTheme
import org.junit.Assert.assertEquals
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import org.robolectric.annotation.GraphicsMode

/**
 * Tarjeta de aproximación con kg (modo TARJETAS): cada aproximación enseña su carga en kg y sus reps, se corrigen antes de registrar
 * (cifra editable y − / + con el paso del ejercicio) y el botón del roadmap registra lo que hay en la tarjeta. El descanso de aproximación
 * y el de movilidad se expanden y minimizan como el de las series y tienen −15 s / pausa / +15 s.
 */
@RunWith(RobolectricTestRunner::class)
@GraphicsMode(GraphicsMode.Mode.NATIVE)
@Config(sdk = [34], application = Application::class, qualifiers = "w360dp-h880dp-xxhdpi")
class LivePrepWarmupKgUiTest {
    @get:Rule val rule = createComposeRule()

    private fun row(index: Int, percent: Double, reps: Int, increment: Double = 2.5, done: Boolean = false, kg: Double? = null, doneReps: Int? = null) =
        WarmupPhaseRow(
            exerciseId = "sent", exerciseBadge = null, index = index,
            warmup = WarmupSetDefinition(id = "w${index + 1}", percentageOfWorkingWeight = percent, targetReps = reps, restBetween = 60),
            suggestedWeightKg = 100.0 * percent, actualWeightKg = kg, isCompleted = done, actualReps = doneReps, loadIncrementKg = increment,
        )

    private class Harness(initial: List<WarmupPhaseRow>) {
        val rows: SnapshotStateList<WarmupPhaseRow> = mutableStateListOf<WarmupPhaseRow>().apply { addAll(initial) }
        var recorded: Triple<Int, Double, Int>? = null
        val holder = RecordActionHolder()
        val fab = RecordFabHolder()
        val scope = Any()
        var restSeconds by mutableIntStateOf(-1)
        var expanded by mutableStateOf(false)
        var paused by mutableStateOf(false)
        var expandToggles = 0
        var pauseToggles = 0
        val adjusts = mutableListOf<Int>()
        var restEnds = 0
        var withRestControls = true
    }

    @Composable
    private fun frame(content: @Composable () -> Unit) {
        CompositionLocalProvider(LocalWizardReducedMotion provides true, LocalKpknOverlayHost provides null, LocalLiveSessionCompactLayout provides false) {
            KPKNTheme { MaterialTheme {
                Box(Modifier.requiredSize(360.dp, 880.dp).background(Color.Black)) {
                    Box(Modifier.align(Alignment.Center).width(312.dp)) {
                        LiveSessionCardFrame { Box(Modifier.fillMaxSize()) { content() } }
                    }
                }
            } }
        }
    }

    private fun show(h: Harness) {
        rule.setContent {
            frame {
                WarmupPhaseLiveCard(
                    rows = h.rows.toList(),
                    sessionAccentColor = Color(0xFFF49A6E),
                    onToggleComplete = { r, completed, _ ->
                        val at = h.rows.indexOfFirst { it.warmup.id == r.warmup.id }
                        if (!completed) h.rows[at] = r.copy(isCompleted = false, actualWeightKg = null, actualReps = null)
                    },
                    onCompleteWithReps = { r, kg, reps ->
                        h.recorded = Triple(r.index, kg, reps)
                        val at = h.rows.indexOfFirst { it.warmup.id == r.warmup.id }
                        h.rows[at] = r.copy(isCompleted = true, actualWeightKg = kg, actualReps = reps)
                    },
                    onSkip = {},
                    onContinue = {},
                    modifier = Modifier.fillMaxSize(),
                    inlineRestRemainingSeconds = h.restSeconds.takeIf { it >= 0 },
                    inlineRestTotalSeconds = if (h.restSeconds >= 0) 60 else null,
                    onSkipInlineRest = { h.restEnds++; h.restSeconds = -1 },
                    recordActionHolder = h.holder,
                    recordActionScopeOwner = h.scope,
                    recordActionPageKey = "warmup-page",
                    recordFabHolder = h.fab,
                    isActivePage = true,
                    onWeightDraft = { _, _ -> },
                    workingWeightKg = 100.0,
                    exerciseName = "Sentadilla trasera",
                    inlineRestPaused = h.paused,
                    inlineRestExpanded = h.expanded,
                    onToggleInlineRestExpanded = if (h.withRestControls) ({ h.expandToggles++; h.expanded = !h.expanded }) else null,
                    onAdjustInlineRest = if (h.withRestControls) ({ h.adjusts += it }) else null,
                    onToggleInlineRestPause = if (h.withRestControls) ({ h.pauseToggles++; h.paused = !h.paused }) else null,
                )
            }
        }
        rule.waitForIdle()
    }

    private fun roadmapPress(h: Harness) {
        rule.runOnIdle { h.holder.action?.invoke() }
        rule.waitForIdle()
    }

    private fun threeRows(increment: Double = 2.5) = listOf(row(0, .5, 8, increment), row(1, .6, 6, increment), row(2, .7, 4, increment))

    @Test fun eachWarmupShowsItsKgWithTheUnitAndTheEditorShowsTheFirstPending() {
        show(Harness(threeRows()))
        rule.onNodeWithText("50 kg × 8", useUnmergedTree = true).assertIsDisplayed()
        rule.onNodeWithText("60 kg × 6", useUnmergedTree = true).assertIsDisplayed()
        rule.onNodeWithTag("warmup-editor-kg").assertTextEquals("50")
        rule.onNodeWithTag("warmup-editor-reps").assertTextEquals("8")
        rule.onNodeWithContentDescription("Carga de la aproximación A1 en kg").assertIsDisplayed()
        rule.onNodeWithText("kg", useUnmergedTree = true).assertIsDisplayed()
        rule.onNodeWithText("reps", useUnmergedTree = true).assertIsDisplayed()
    }

    @Test fun plusAndMinusUseTheExerciseIncrementAndTheRoadmapRecordsWhatTheCardShows() {
        val h = Harness(threeRows())
        show(h)
        rule.onNodeWithTag("warmup-editor-plus").performClick()
        rule.onNodeWithTag("warmup-editor-kg").assertTextEquals("52,5")
        rule.onNodeWithText("52,5 kg × 8", useUnmergedTree = true).assertIsDisplayed()
        rule.onNodeWithTag("warmup-editor-reps").performTextReplacement("6")
        assertEquals("Registrar aproximación A1", h.fab.label)
        roadmapPress(h)
        assertEquals(Triple(0, 52.5, 6), h.recorded)
        rule.onNodeWithText("52,5 kg × 6", useUnmergedTree = true).assertIsDisplayed()
        assertEquals("Registrar aproximación A2", h.fab.label)
    }

    @Test fun aDumbbellStepsOneKgAndAnEmptyLoadStartsFromZero() {
        val h = Harness(threeRows(increment = 1.0))
        show(h)
        rule.onNodeWithTag("warmup-editor-minus").performClick()
        rule.onNodeWithTag("warmup-editor-kg").assertTextEquals("49")
        roadmapPress(h)
        assertEquals(Triple(0, 49.0, 8), h.recorded)
    }

    @Test fun aTypedKgWithACommaIsWhatTheCheckRecords() {
        val h = Harness(threeRows())
        show(h)
        rule.onNodeWithTag("warmup-editor-kg").performTextReplacement("47,5")
        rule.onNodeWithContentDescription("Completar aproximación A1").performClick()
        rule.waitForIdle()
        assertEquals(Triple(0, 47.5, 8), h.recorded)
    }

    @Test fun goingBackToARecordedWarmupShowsItsRegisteredKgAndReps() {
        val h = Harness(listOf(row(0, .5, 8, done = true, kg = 55.0, doneReps = 7), row(1, .6, 6), row(2, .7, 4)))
        show(h)
        // Por defecto el editor sigue a la primera pendiente.
        rule.onNodeWithTag("warmup-editor-kg").assertTextEquals("60")
        rule.onNodeWithText("55 kg × 7", useUnmergedTree = true).assertIsDisplayed()
        rule.onNodeWithTag("warmup-row-A1").performClick()
        rule.onNodeWithTag("warmup-editor-kg").assertTextEquals("55")
        rule.onNodeWithTag("warmup-editor-reps").assertTextEquals("7")
        rule.onNodeWithTag("warmup-editor-minus").assertIsNotEnabled()
        rule.onNodeWithTag("warmup-editor-plus").assertIsNotEnabled()
        // Lo registrado no cambia: el roadmap sigue registrando la primera pendiente.
        assertEquals("Registrar aproximación A2", h.fab.label)
    }

    @Test fun choosingAPendingWarmupMakesTheRoadmapRecordIt() {
        val h = Harness(threeRows())
        show(h)
        rule.onNodeWithTag("warmup-row-A3").performClick()
        rule.waitForIdle()
        rule.onNodeWithTag("warmup-editor-kg").assertTextEquals("70")
        assertEquals("Registrar aproximación A3", h.fab.label)
        roadmapPress(h)
        assertEquals(Triple(2, 70.0, 4), h.recorded)
        assertEquals("Registrar aproximación A1", h.fab.label)
    }

    @Test fun theWarmupRestHasMinusPausePlusAndExpands() {
        val h = Harness(threeRows()).apply { restSeconds = 45 }
        show(h)
        rule.onNodeWithTag("rest-minus").performClick()
        rule.onNodeWithTag("rest-plus").performClick()
        assertEquals(listOf(-15, 15), h.adjusts)
        rule.onNodeWithTag("rest-pause").performClick()
        assertEquals(1, h.pauseToggles)
        rule.onNodeWithContentDescription("Continuar descanso").assertIsDisplayed()
        rule.onNodeWithTag("rest-expand").performClick()
        rule.waitForIdle()
        assertEquals(1, h.expandToggles)
        // Expandido: la pantalla completa de la pausa entre aproximaciones, con minimizar a la tarjeta.
        rule.onNodeWithTag("prep-rest-fullscreen").assertIsDisplayed()
        rule.onNodeWithTag("rest-full-eyebrow", useUnmergedTree = true).assertTextEquals("PAUSA ENTRE APROXIMACIONES")
        rule.onNodeWithTag("rest-full-title", useUnmergedTree = true).assertTextEquals("Sentadilla trasera")
        rule.onNodeWithTag("rest-minimize").performClick()
        rule.waitForIdle()
        assertEquals(2, h.expandToggles)
        rule.onNodeWithTag("prep-rest-fullscreen").assertDoesNotExist()
    }

    @Test fun theExpandedWarmupRestEndsFromItsFooter() {
        val h = Harness(threeRows()).apply { restSeconds = 45; expanded = true }
        show(h)
        rule.onNodeWithTag("prep-rest-fullscreen").assertIsDisplayed()
        // SIGUIENTE: la aproximación que toca, con sus kg y reps (en la pantalla completa y en la tarjeta de debajo).
        assertEquals(true, rule.onAllNodesWithText("Aproximación A1", useUnmergedTree = true).fetchSemanticsNodes().isNotEmpty())
        rule.onNodeWithTag("prep-rest-full-end").performClick()
        rule.waitForIdle()
        assertEquals(1, h.restEnds)
        rule.onNodeWithTag("prep-rest-fullscreen").assertDoesNotExist()
    }

    @Test fun withoutTimerActionsTheRestStaysAMinimizedCardWithoutControls() {
        val h = Harness(threeRows()).apply { restSeconds = 45; withRestControls = false; expanded = true }
        show(h)
        rule.onNodeWithTag("rest-minus").assertDoesNotExist()
        rule.onNodeWithTag("rest-pause").assertDoesNotExist()
        rule.onNodeWithTag("rest-expand").assertDoesNotExist()
        rule.onNodeWithTag("prep-rest-fullscreen").assertDoesNotExist()
    }

    @Test fun theMobilityRestExpandsAndHasTheSameControls() {
        val item = WorkoutMobilityChecklistItem(
            exerciseId = "sent", exerciseName = "Sentadilla trasera",
            mobility = MobilitySeries(id = "m1", name = "Gato-vaca", reps = "10"), stepKey = "m1",
        )
        var expanded by mutableStateOf(false)
        val adjusts = mutableListOf<Int>()
        var pauses = 0
        rule.setContent {
            frame {
                MobilityPhaseLiveCard(
                    items = listOf(item), completedStepKeys = emptySet(), remainingSeconds = 300, totalMinutes = 5, isTimerRunning = false,
                    sessionAccentColor = Color(0xFFF49A6E), onToggleComplete = { _, _ -> }, onStartTimer = {}, onPauseTimer = {},
                    onAddTimerSeconds = {}, onResetTimer = {}, onSkip = {}, onContinue = {}, modifier = Modifier.fillMaxSize(),
                    inlineRestRemainingSeconds = 20, inlineRestTotalSeconds = 30, onSkipInlineRest = {}, isActivePage = true,
                    inlineRestExpanded = expanded, onToggleInlineRestExpanded = { expanded = !expanded },
                    onAdjustInlineRest = { adjusts += it }, onToggleInlineRestPause = { pauses++ }, exerciseName = "Sentadilla trasera",
                )
            }
        }
        rule.onNodeWithTag("rest-plus").performClick()
        rule.onNodeWithTag("rest-pause").performClick()
        assertEquals(listOf(15), adjusts)
        assertEquals(1, pauses)
        rule.onNodeWithTag("rest-expand").performClick()
        rule.waitForIdle()
        rule.onNodeWithTag("rest-full-eyebrow", useUnmergedTree = true).assertTextEquals("PAUSA DE MOVILIDAD")
    }
}
