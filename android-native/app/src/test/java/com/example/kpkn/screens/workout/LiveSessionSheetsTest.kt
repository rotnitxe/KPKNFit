package com.example.kpkn.screens.workout

import android.app.Application
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.test.*
import androidx.compose.ui.test.junit4.createComposeRule
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

@RunWith(RobolectricTestRunner::class)
@GraphicsMode(GraphicsMode.Mode.NATIVE)
@Config(sdk = [34], application = Application::class, qualifiers = "w360dp-h880dp-xxhdpi")
class LiveSessionSheetsTest {
    @get:Rule val rule = createComposeRule()
    private fun content(body: @Composable () -> Unit) {
        rule.setContent { CompositionLocalProvider(LocalWizardReducedMotion provides true, LocalKpknOverlayHost provides null) {
            KPKNTheme { MaterialTheme { Box(Modifier.fillMaxSize()) { body() } } }
        } }
    }
    private fun tap(text: String) {
        val node = rule.onNodeWithText(text, useUnmergedTree = true)
        if (hasAnyAncestor(hasScrollAction()).matches(node.fetchSemanticsNode())) node.performScrollTo()
        node.assertIsDisplayed().performClick()
    }
    @Test fun menuKeepsEveryMigratedDestinationAndPhotosUsesTheExistingAlbum() {
        var selected by mutableStateOf<LiveSessionDestination?>(LiveSessionDestination.MENU)
        var photos = 0
        var voice = 0
        content { LiveSessionSheetsHost(selected, { selected = it }, onOpenPhotos = { photos++ }, onOpenVoice = { voice++ }) }
        // Refinamiento 02: «Entender las métricas del vídeo» (ANALYSIS_HELP) ya no es una fila del menú principal: se abre desde la hoja
        // «Ayuda y explicaciones» (ASSISTANCE), que sí cuelga del menú. Se comprueba aparte, justo debajo.
        LiveSessionDestination.entries.filter { it != LiveSessionDestination.MENU && it != LiveSessionDestination.ANALYSIS_HELP }.forEach { item ->
            rule.onNodeWithTag("live-session-menu-${item.name.lowercase()}").performScrollTo().assertIsDisplayed().performClick()
            when (item) {
                LiveSessionDestination.PHOTOS -> rule.runOnIdle { assertEquals(1, photos); assertNull(selected) }
                // La conversación de voz es una superficie completa, no una hoja del host.
                LiveSessionDestination.VOICE -> rule.runOnIdle { assertEquals(1, voice); assertNull(selected) }
                else -> rule.onNodeWithTag("live-session-sheet-${item.name.lowercase()}", true).assertIsDisplayed()
            }
            rule.runOnIdle { selected = LiveSessionDestination.MENU }
        }
        rule.onNodeWithTag("live-session-menu-assistance").performScrollTo().performClick()
        rule.onNodeWithTag("live-assistance-analysis", true).assertIsDisplayed().performClick()
        rule.onNodeWithTag("live-session-sheet-analysis_help", true).assertIsDisplayed()
        rule.runOnIdle { assertEquals(LiveSessionDestination.ANALYSIS_HELP, selected) }
    }
    @Test fun viewSelectorSwitchesCardsAndListWithoutLeavingTheSession() {
        var selected by mutableStateOf<LiveSessionDestination?>(LiveSessionDestination.VIEW)
        var view by mutableStateOf(LiveSessionView.CARDS)
        var chosen: LiveSessionView? = null
        content { LiveSessionSheetsHost(selected, { selected = it }, currentView = view,
            onSelectView = { view = it; chosen = it }) }
        rule.onNodeWithText("Tarjetas y Lista comparten el mismo borrador. Cambiar de vista no registra ni borra nada.")
            .assertIsDisplayed()
        rule.onNodeWithTag("live-session-view-list").performClick()
        rule.runOnIdle {
            assertEquals(LiveSessionView.LIST, chosen)
            assertEquals(LiveSessionView.LIST, view)
            assertNull(selected)
        }
    }
    @Test fun timerStartsPausesAndResetsAndSurvivesClosingTheSheet() {
        var selected by mutableStateOf<LiveSessionDestination?>(LiveSessionDestination.TIMER)
        val timer = LiveSessionTimerState()
        content { LiveSessionSheetsHost(selected, { selected = it }, timer = timer) }
        tap("Iniciar")
        rule.onNodeWithText("Pausar").assertIsDisplayed()
        rule.runOnIdle { assertNotNull(timer.startedAtMillis); selected = null }
        rule.runOnIdle { selected = LiveSessionDestination.TIMER }
        tap("Pausar")
        rule.runOnIdle { assertNull(timer.startedAtMillis) }
        tap("Reiniciar")
        rule.onNodeWithTag("live-session-timer-value", true).assertTextEquals("0:00")
    }
    @Test fun rmSheetUsesTheCommonHybridAndKeepsAllTenRmRows() {
        content { LiveSessionSheetsHost(LiveSessionDestination.RM, {}) }
        rule.onAllNodes(hasSetTextAction())[0].performTextInput("100")
        rule.onAllNodes(hasSetTextAction())[1].performTextInput("5")
        // These are the existing calculator's real rows, not a replacement table.
        for (reps in 1..10) rule.onNodeWithText("${reps}RM", useUnmergedTree = true).performScrollTo().assertIsDisplayed()
        assertEquals(112.5, com.example.kpkn.domain.calculations.calculateHybrid1RM(100.0, 5), .001)
    }
    @Test fun rmCalculatorKeepsSpanishDecimalsInTheEstimateAndEveryRowWithEnglishLocale() {
        val original = java.util.Locale.getDefault()
        java.util.Locale.setDefault(java.util.Locale.US)
        try {
            content { LiveSessionSheetsHost(LiveSessionDestination.RM, {}) }
            rule.onAllNodes(hasSetTextAction())[0].performTextReplacement("100")
            rule.onAllNodes(hasSetTextAction())[1].performTextReplacement("5")
            rule.onNodeWithText("e1RM: 112,5 kg", true).assertIsDisplayed()
            for ((reps, weight) in rmTableLoads(112.5)) {
                val expected = String.format(java.util.Locale.forLanguageTag("es"), "%.1f", weight)
                rule.onNode(hasText(expected) and hasAnySibling(hasText("${reps}RM")), true)
                    .performScrollTo().assertIsDisplayed()
            }
        } finally { java.util.Locale.setDefault(original) }
    }

    @Test fun rmCalculatorAcceptsCommaAndPreservesTheSelectedLoad() {
        var selected: Double? = null
        content { Column(Modifier.fillMaxSize().verticalScroll(androidx.compose.foundation.rememberScrollState())) {
            WorkoutRmCalcContent(softInputs = true, onWeightSelected = { weight, _ -> selected = weight })
        } }
        rule.onAllNodes(hasSetTextAction())[0].performTextReplacement("82,5")
        rule.onAllNodes(hasSetTextAction())[1].performTextReplacement("5")
        val expected = com.example.kpkn.domain.calculations.calculateHybrid1RM(82.5, 5)
        val text = String.format(java.util.Locale.forLanguageTag("es"), "%.1f", expected)
        rule.onNodeWithText("e1RM: $text kg").assertIsDisplayed()
        rule.onNode(hasClickAction() and hasAnyDescendant(hasText("1RM")), useUnmergedTree = true)
            .performScrollTo().performClick()
        rule.runOnIdle { assertEquals(expected, selected!!, .00001) }
    }
    @Test fun notesKeepDraftSaveLastNoteAndAddToggleRemoveTasks() {
        var draft by mutableStateOf("")
        var saved by mutableStateOf(listOf(SessionSavedNote("old", "Última nota guardada")))
        var tasks by mutableStateOf(emptyList<SessionChecklistItem>())
        var selected by mutableStateOf<LiveSessionDestination?>(LiveSessionDestination.NOTES)
        content { LiveSessionSheetsHost(selected, { selected = it }, sessionNotes = draft, sessionSavedNotes = saved,
            sessionChecklist = tasks, onSessionNotesChange = { draft = it },
            onSaveSessionNote = { saved = saved + SessionSavedNote("new", it) },
            onAddChecklistItem = { tasks = tasks + SessionChecklistItem("task", it) },
            onToggleChecklistItem = { id -> tasks = tasks.map { if (it.id == id) it.copy(done = !it.done) else it } },
            onRemoveChecklistItem = { id -> tasks = tasks.filterNot { it.id == id } }) }
        rule.onNodeWithText("Última nota guardada").assertIsDisplayed()
        rule.onNode(hasSetTextAction() and hasText("Anotaciones generales de la sesión…", substring = true)).performTextInput("Técnica estable")
        tap("Guardar nota")
        rule.onNode(hasSetTextAction() and hasText("Nueva tarea")).performScrollTo().performTextInput("Beber agua")
        tap("Añadir")
        rule.onNode(isToggleable(), true).performScrollTo().performClick()
        rule.runOnIdle { assertEquals("Técnica estable", draft); assertEquals(draft, saved.last().text); assertTrue(tasks.single().done) }
        rule.onNodeWithContentDescription("Eliminar").performScrollTo().performClick()
        rule.onNode(hasSetTextAction() and hasText("Nueva tarea")).performScrollTo().performTextInput("Tarea pendiente")
        rule.runOnIdle { assertTrue(tasks.isEmpty()); selected = null }
        rule.runOnIdle { selected = LiveSessionDestination.NOTES }
        rule.onNode(hasSetTextAction() and hasText("Técnica estable")).assertExists()
        rule.onNode(hasSetTextAction() and hasText("Tarea pendiente")).assertExists()
        assertEquals("La misma nota de sesión se entrega al cierre", "Técnica estable", draft)
    }
    @Test fun milestonesKeepRecentRecordsAndStarExerciseProgress() {
        val exercise = Exercise("star", "Sentadilla estrella", sets = listOf(ExerciseSet("set")), isStarTarget = true, goal1RM = 150.0)
        content { LiveSessionSheetsHost(LiveSessionDestination.MILESTONES, {}, exercises = listOf(exercise),
            completedSets = mapOf("star_0" to CompletedSet("set", weight = 100.0, reps = 5)),
            milestones = listOf(SessionMilestone("pr", "star", exercise.name, "pr_e1rm", "Nuevo récord", 112.5))) }
        rule.onNodeWithText("Nuevo récord").assertIsDisplayed()
        rule.onNodeWithText("Ejercicios estrella", useUnmergedTree = true).assertIsDisplayed()
        assertEquals(112.5, com.example.kpkn.domain.calculations.calculateHybrid1RM(100.0, 5), .001)
        rule.onNodeWithText("112,5 / 150 kg e1RM", useUnmergedTree = true).performScrollTo().assertIsDisplayed()
    }
    /** Sin método de energía compatible en vivo (el antiguo está retirado) la fila «Calorías» no se ofrece: sería interfaz vacía. */
    @Test fun menuHasNoCaloriesRowWithoutACompatibleEnergyMethod() {
        content { LiveSessionSheetsHost(LiveSessionDestination.MENU, {}) }
        rule.onNodeWithTag("live-session-menu-calories").assertDoesNotExist()
        rule.onNodeWithText("Calorías").assertDoesNotExist()
        assertNull(sessionCalorieStatus(SessionEnergySummary(), 0, null).kcal)
    }
    @Test fun photoEntryKeepsCameraGalleryPendingRetryAndSelectionInTheExistingAlbum() {
        var retries = 0
        content {
            WorkoutSessionAlbumSheet(emptyList(), {}, captureActions = {
                SessionPhotoCaptureActions({}, { null }, { _, _ -> }, {}, "Captura pendiente", { retries++ })
            })
        }
        rule.onNodeWithText("Cámara").assertIsDisplayed()
        rule.onNodeWithText("Galería").assertIsDisplayed()
        rule.onNodeWithText("Captura pendiente").assertIsDisplayed()
        tap("Reintentar")
        rule.runOnIdle { assertEquals(1, retries) }
        rule.onNodeWithText("Esta sesión", substring = true).assertIsDisplayed()
    }
    @Test fun routeKeepsAuthoredSupersetSelectionAndExerciseOptions() {
        var selected: String? = null; var options: String? = null
        val first = Exercise("first", "Zancada", sets = listOf(ExerciseSet("s1")))
        val second = Exercise("second", "Curl", sets = listOf(ExerciseSet("s2")))
        val group = SupersetGroup(id = "super", exerciseOrder = listOf(first.id, second.id), rounds = 3)
        content { LiveSessionSheetsHost(LiveSessionDestination.ROUTE, {}, exercises = listOf(first, second),
            supersetGroups = listOf(group), onSelectSuperset = { selected = it }, onOpenSupersetOptions = { options = it }) }
        rule.onNodeWithText("Recorrido de la sesión").assertIsDisplayed()
        rule.onNodeWithContentDescription("Opciones de Zancada + Curl").performScrollTo().performClick()
        rule.runOnIdle { assertEquals(first.id, options) }
        rule.onNodeWithText("Zancada + Curl").performScrollTo().performClick()
        rule.runOnIdle { assertEquals("super", selected) }
    }
}

class LiveSessionTimerStateTest {
    @Test fun monotonicClockKeepsElapsedAcrossSheetAbsenceAndPausesExactly() {
        val timer = LiveSessionTimerState()
        timer.toggle(1_000)
        assertEquals(12_000L, timer.elapsedMillis(13_000))
        timer.toggle(13_000)
        assertEquals(12_000L, timer.elapsedMillis(90_000))
        timer.toggle(100_000)
        assertEquals(15_000L, timer.elapsedMillis(103_000))
        val restored = LiveSessionTimerState(timer.pausedMillis, timer.startedAtMillis)
        assertEquals(15_000L, restored.elapsedMillis(103_000))
        restored.reset()
        assertEquals(0L, restored.elapsedMillis(999_000)); assertNull(restored.startedAtMillis)
    }
}
