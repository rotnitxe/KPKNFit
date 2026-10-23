package com.example.kpkn.screens.workout

import android.app.Application
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.remember
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.semantics.SemanticsActions
import androidx.compose.ui.test.assertCountEquals
import androidx.compose.ui.test.performSemanticsAction
import androidx.compose.ui.test.assertHeightIsAtLeast
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.assertIsEnabled
import androidx.compose.ui.test.assertIsNotEnabled
import androidx.compose.ui.test.assertIsSelected
import androidx.compose.ui.test.assertIsNotSelected
import androidx.compose.ui.test.assertTextContains
import androidx.compose.ui.test.assertWidthIsAtLeast
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onAllNodesWithText
import androidx.compose.ui.test.onNodeWithContentDescription
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performScrollTo
import androidx.compose.ui.unit.dp
import com.example.kpkn.data.models.CardioDetails
import com.example.kpkn.data.models.CardioType
import com.example.kpkn.data.models.Exercise
import com.example.kpkn.data.models.ExerciseSet
import com.example.kpkn.data.models.IntensityMode
import com.example.kpkn.data.models.Session
import com.example.kpkn.data.models.TrainingMode
import com.example.kpkn.data.models.UnilateralMode
import com.example.kpkn.ui.components.KpknOverlayHostContent
import com.example.kpkn.ui.components.KpknOverlayHostController
import com.example.kpkn.ui.components.LocalKpknOverlayHost
import com.example.kpkn.ui.tour.TourSeenStore
import dev.chrisbanes.haze.HazeState
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import org.robolectric.annotation.GraphicsMode

/**
 * G1 y G2 de screens-v2: la hoja «Editar series» y la hoja «Editar cardio» de la sesión en vivo, con los valores reales de la sesión.
 * Cada control del tablero está y funciona; lo que cambia de sitio (descanso entre lados, «Carga inteligente», las acciones de serie)
 * se prueba en su sitio nuevo.
 */
@RunWith(RobolectricTestRunner::class)
@GraphicsMode(GraphicsMode.Mode.NATIVE)
@Config(sdk = [34], qualifiers = "w400dp-h1800dp-xxhdpi", application = Application::class)
class WorkoutEditSeriesSheetBoardTest {
    @get:Rule val rule = createComposeRule()

    private var saved: Exercise? = null
    private lateinit var sheetState: WorkoutStructureSheetsState

    /** Peso muerto rumano, el del tablero: 4 series, la cuarta más pesada, con RIR; 1RM de 140 kg y descanso de 2:30. */
    private fun romanianDeadlift(): Exercise = Exercise(
        id = "rdl",
        name = "Peso muerto rumano",
        trainingMode = TrainingMode.REPS,
        restTime = 150,
        reference1RM = 140.0,
        sets = listOf(
            set("s1", reps = 8, kg = 100.0, rir = 2),
            set("s2", reps = 8, kg = 100.0, rir = 2),
            set("s3", reps = 8, kg = 100.0, rir = 2),
            set("s4", reps = 6, kg = 105.0, rir = 1),
        ),
    )

    private fun set(id: String, reps: Int, kg: Double, rir: Int) =
        ExerciseSet(id = id, targetReps = reps, weight = kg, targetRIR = rir, intensityMode = IntensityMode.RIR)

    private fun mount(exercise: Exercise, initialSetIndex: Int = 1) {
        val session = Session(id = "pierna-a", name = "Pierna A", exercises = listOf(exercise))
        val overlays = KpknOverlayHostController(tourSeen = object : TourSeenStore {
            override fun seenIds(): Set<String> = emptySet()
            override fun markSeen(tourId: String) = Unit
        })
        rule.setContent {
            val state = rememberWorkoutStructureSheetsState().also { sheetState = it }
            CompositionLocalProvider(LocalKpknOverlayHost provides overlays) {
                WorkoutEditSeriesSheet(
                    state = state,
                    modeSession = session,
                    sessionAccentColor = Color(0xFFF49A6E),
                    bottomHazeState = remember { HazeState() },
                    initialSetIndex = initialSetIndex,
                ) { _, draft -> saved = draft }
                KpknOverlayHostContent(overlays)
            }
        }
        rule.runOnIdle { sheetState.editSheetExerciseId = exercise.id }
        rule.waitUntil(15_000) { rule.onAllNodesWithText("Confirmar cambios").fetchSemanticsNodes().isNotEmpty() }
    }

    /** Toque por la acción semántica: determinista, sin depender de qué haya encima del nodo en la ventana de la prueba. */
    private fun click(node: androidx.compose.ui.test.SemanticsNodeInteraction) {
        node.performSemanticsAction(SemanticsActions.OnClick) { it() }
        rule.waitForIdle()
    }

    private fun tap(tag: String) = click(rule.onNodeWithTag(tag))

    private fun tapText(text: String) = click(rule.onNodeWithText(text))

    private fun confirm() = tap(LIVE_EDIT_CONFIRM_TAG)

    // ───────────────────────────── G1 · Editar series ─────────────────────────────

    @Test
    fun theSheetShowsTitleExerciseNameAndEveryControlOfTheBoard() {
        mount(romanianDeadlift())

        rule.onNodeWithText("Editar series").assertExists()
        rule.onAllNodesWithText("Peso muerto rumano").assertCountEquals(1)
        rule.onNodeWithText("EJERCICIO").assertExists()
        rule.onNodeWithText("Vale para todas las series").assertExists()
        rule.onNodeWithTag(LIVE_EDIT_MODE_TAG).assertExists()
        rule.onNodeWithTag(LIVE_EDIT_LATERALITY_TAG, useUnmergedTree = true).assertExists()
        rule.onNodeWithText("Bilateral").assertDoesNotExist()
        rule.onNodeWithText("Unilateral").assertDoesNotExist()
        rule.onNodeWithTag(LIVE_EDIT_REST_TAG).assertExists()
        rule.onNodeWithTag(LIVE_EDIT_SMART_LOAD_TAG).assertExists()
        rule.onNodeWithText("SERIES").assertExists()
        rule.onNodeWithText("Toca una para editarla").assertExists()
        // El panel de la hoja es un nodo que fusiona descendientes (su toque vacío evita cerrar al tocar dentro);
        // el foco es un contenedor sin acción propia, así que se busca en el árbol sin fusionar.
        rule.onNodeWithTag(LIVE_EDIT_FOCUS_TAG, useUnmergedTree = true).assertExists()
        rule.onNodeWithTag(LIVE_EDIT_CONFIRM_TAG).assertIsDisplayed()
    }

    @Test
    fun theExerciseZoneTakesItsValuesFromTheRealExercise() {
        mount(romanianDeadlift())

        // Descanso del ejercicio (150 s) y 1RM de referencia (140 kg).
        rule.onNodeWithTag(LIVE_EDIT_REST_TAG).assertTextContains("2:30", substring = true)
        rule.onNodeWithTag(LIVE_EDIT_SMART_LOAD_TAG).assertTextContains("1RM: 140", substring = true)
        rule.onNodeWithTag(LIVE_EDIT_MODE_TAG).assertTextContains("Reps")
    }

    @Test
    fun withoutAReferenceTheSmartLoadTileInvitesToConfigureIt() {
        mount(romanianDeadlift().copy(reference1RM = null, sets = romanianDeadlift().sets.map { it.copy(weight = null) }))

        rule.onNodeWithTag(LIVE_EDIT_SMART_LOAD_TAG).assertTextContains("Configurar", substring = true)
    }

    @Test
    fun theSeriesListShowsOneChipPerSetPlusTheAddChip() {
        mount(romanianDeadlift())

        (0..3).forEach { rule.onNodeWithTag(liveEditSetChipTag(it)).assertExists() }
        rule.onNodeWithTag(liveEditSetChipTag(0)).assertTextContains("8 × 100", substring = true)
        rule.onNodeWithTag(liveEditSetChipTag(3)).assertTextContains("6 × 105", substring = true)
        rule.onNodeWithTag(LIVE_EDIT_ADD_SET_TAG).assertExists()
        // Objetivos táctiles de 48 dp como mínimo.
        rule.onNodeWithTag(liveEditSetChipTag(0)).assertHeightIsAtLeast(48.dp).assertWidthIsAtLeast(48.dp)
        rule.onNodeWithTag(LIVE_EDIT_ADD_SET_TAG).assertHeightIsAtLeast(48.dp).assertWidthIsAtLeast(48.dp)
    }

    @Test
    fun theFocusOpensOnTheCurrentSetOfTheSessionAndSaysWhatComesNext() {
        mount(romanianDeadlift(), initialSetIndex = 1)

        rule.onNodeWithTag(liveEditSetChipTag(1)).assertIsSelected()
        rule.onNodeWithText("Serie 2 de 4").assertExists()
        rule.onNodeWithText("Siguiente: 8 × 100 kg · RIR 2", substring = true).assertExists()
    }

    @Test
    fun theLastSetHasNoNextOne() {
        mount(romanianDeadlift(), initialSetIndex = 3)

        rule.onNodeWithText("Serie 4 de 4").assertExists()
        rule.onNodeWithText("Última serie").assertExists()
        rule.onNodeWithTag(LIVE_EDIT_NEXT_TAG).assertIsNotEnabled()
        rule.onNodeWithTag(LIVE_EDIT_PREV_TAG).assertIsEnabled()
    }

    @Test
    fun theArrowsMoveTheFocusBetweenSets() {
        mount(romanianDeadlift(), initialSetIndex = 1)

        tap(LIVE_EDIT_NEXT_TAG)
        rule.onNodeWithText("Serie 3 de 4").assertExists()
        rule.onNodeWithTag(liveEditSetChipTag(2)).assertIsSelected()
        tap(LIVE_EDIT_PREV_TAG)
        tap(LIVE_EDIT_PREV_TAG)
        rule.onNodeWithText("Serie 1 de 4").assertExists()
        rule.onNodeWithTag(LIVE_EDIT_PREV_TAG).assertIsNotEnabled()
    }

    @Test
    fun tappingAChipEditsThatSet() {
        mount(romanianDeadlift(), initialSetIndex = 1)

        tap(liveEditSetChipTag(3))

        rule.onNodeWithText("Serie 4 de 4").assertExists()
        rule.onNodeWithTag(liveEditSetChipTag(3)).assertIsSelected()
        rule.onNodeWithTag(liveEditSetChipTag(1)).assertIsNotSelected()
    }

    @Test
    fun addingASetAppendsItAndFocusesIt() {
        mount(romanianDeadlift())

        tap(LIVE_EDIT_ADD_SET_TAG)
        rule.onNodeWithText("Serie 5 de 5").assertExists()
        confirm()

        assertEquals(5, saved!!.sets.size)
        // La serie nueva parte de la última: 6 reps con 105 kg.
        assertEquals(6, saved!!.sets.last().targetReps)
        assertEquals(105.0, saved!!.sets.last().weight!!, 0.0)
    }

    @Test
    fun duplicatingTheFocusedSetInsertsACopyAfterItAndFocusesTheCopy() {
        mount(romanianDeadlift(), initialSetIndex = 3)

        tap(LIVE_EDIT_DUPLICATE_TAG)
        rule.onNodeWithText("Serie 5 de 5").assertExists()
        confirm()

        val sets = saved!!.sets
        assertEquals(5, sets.size)
        assertEquals(sets[3].targetReps, sets[4].targetReps)
        assertEquals(sets[3].weight, sets[4].weight)
        assertTrue(sets[3].id != sets[4].id)
    }

    @Test
    fun deletingTheFocusedSetRemovesItAndMovesTheFocusToItsNeighbour() {
        mount(romanianDeadlift(), initialSetIndex = 3)

        tap(LIVE_EDIT_DELETE_TAG)
        rule.onNodeWithText("Serie 3 de 3").assertExists()
        confirm()

        assertEquals(listOf("s1", "s2", "s3"), saved!!.sets.map { it.id })
    }

    @Test
    fun theOnlyRemainingSetCannotBeDeleted() {
        mount(romanianDeadlift().copy(sets = listOf(set("s1", 8, 100.0, 2))), initialSetIndex = 0)

        rule.onNodeWithTag(LIVE_EDIT_DELETE_TAG).assertIsNotEnabled()
        rule.onNodeWithTag(LIVE_EDIT_APPLY_ALL_TAG).assertIsNotEnabled()
        rule.onNodeWithTag(LIVE_EDIT_UP_TAG).assertIsNotEnabled()
        rule.onNodeWithTag(LIVE_EDIT_DOWN_TAG).assertIsNotEnabled()
    }

    @Test
    fun movingTheFocusedSetUpAndDownReordersTheSets() {
        mount(romanianDeadlift(), initialSetIndex = 1)

        tap(LIVE_EDIT_UP_TAG)
        rule.onNodeWithText("Serie 1 de 4").assertExists()
        tap(LIVE_EDIT_DOWN_TAG)
        tap(LIVE_EDIT_DOWN_TAG)
        confirm()

        assertEquals(listOf("s1", "s3", "s2", "s4"), saved!!.sets.map { it.id })
    }

    @Test
    fun applyToAllAsksForConfirmationAndCopiesTheFocusedSetToTheOthers() {
        mount(romanianDeadlift(), initialSetIndex = 3)

        tap(LIVE_EDIT_APPLY_ALL_TAG)
        rule.onNodeWithText("3 series cambiarán para quedar iguales que esta. ¿Aplicar?").assertExists()
        tapText("Aplicar")
        rule.waitForIdle()
        confirm()

        assertTrue(saved!!.sets.all { it.targetReps == 6 && it.weight == 105.0 && it.targetRIR == 1 })
    }

    @Test
    fun cancellingApplyToAllChangesNothing() {
        mount(romanianDeadlift(), initialSetIndex = 3)

        tap(LIVE_EDIT_APPLY_ALL_TAG)
        tapText("Cancelar")
        rule.waitForIdle()
        confirm()

        assertEquals(listOf(8, 8, 8, 6), saved!!.sets.map { it.targetReps })
    }

    @Test
    fun changingTheModeAppliesToTheWholeExercise() {
        mount(romanianDeadlift())

        tap(LIVE_EDIT_MODE_TAG)
        tapText("Tiempo")
        rule.waitForIdle()
        confirm()

        assertEquals(TrainingMode.TIME, saved!!.trainingMode)
    }

    @Test
    fun theLateralitySwitchMakesTheExerciseUnilateralAndShowsItsSideControls() {
        mount(romanianDeadlift())

        rule.onNodeWithTag(LIVE_EDIT_LATERALITY_TAG, useUnmergedTree = true).performClick()
        rule.mainClock.advanceTimeBy(3_000)
        rule.waitForIdle()
        rule.onNodeWithText("Lateralidad: Bilateral").assertIsDisplayed().performClick()
        rule.mainClock.advanceTimeBy(3_000)
        rule.waitForIdle()
        rule.onNodeWithText("Bilateral").assertIsSelected()
        rule.onNodeWithText("Unilateral").assertIsDisplayed().performClick()
        rule.waitForIdle()

        rule.onNodeWithText("Unilateral").assertExists()
        // El orden de lados, el descanso entre lados (en la loseta de descanso) y los lados de la serie.
        rule.onNodeWithText("Orden L/R").assertExists()
        rule.onNodeWithTag(LIVE_EDIT_REST_TAG).assertTextContains("Lados", substring = true)
        rule.onNodeWithTag(LIVE_EDIT_FOCUS_TAG, useUnmergedTree = true).assertExists()
        confirm()

        assertEquals(UnilateralMode.UNILATERAL_PAIRED, saved!!.unilateralMode)
        assertNotNull(saved!!.restBetweenSidesSeconds)
        assertNotNull(saved!!.sets.first().leftTarget)
        assertNotNull(saved!!.sets.first().rightTarget)
    }

    @Test
    fun theSideOrderChipFlipsTheOrderOfTheSides() {
        mount(romanianDeadlift().copy(isUnilateral = true, unilateralMode = UnilateralMode.UNILATERAL_PAIRED, restBetweenSidesSeconds = 15))

        tapText("Orden L/R")
        rule.waitForIdle()
        rule.onNodeWithText("Orden R/L").assertExists()
        confirm()

        assertEquals(com.example.kpkn.data.models.UnilateralSideOrder.RIGHT_LEFT, saved!!.unilateralSideOrder)
    }

    @Test
    fun theRestBetweenSidesLivesInTheRestTileNowAndKeepsItsValue() {
        mount(romanianDeadlift().copy(isUnilateral = true, unilateralMode = UnilateralMode.UNILATERAL_PAIRED, restBetweenSidesSeconds = 45))

        rule.onNodeWithTag(LIVE_EDIT_REST_TAG).assertTextContains("Lados 0:45", substring = true)
        tap(LIVE_EDIT_REST_TAG)
        // Dos relojes, uno detrás de otro: las series y después los lados.
        rule.onNodeWithText("Series").assertExists()
        tapText("Aceptar")
        rule.waitForIdle()
        rule.onNodeWithText("Descanso entre lados").assertExists()
        tapText("Aceptar")
        rule.waitForIdle()
        confirm()

        assertEquals(150, saved!!.restTime)
        assertEquals(45, saved!!.restBetweenSidesSeconds)
    }

    @Test
    fun theRestTileOpensTheRestPickerAndConfirmingKeepsTheExerciseRest() {
        mount(romanianDeadlift())

        tap(LIVE_EDIT_REST_TAG)
        rule.onNodeWithText("Minutos : segundos").assertExists()
        tapText("Aceptar")
        rule.waitForIdle()
        confirm()

        assertEquals(150, saved!!.restTime)
    }

    @Test
    fun theSmartLoadTileOpensItsDialog() {
        mount(romanianDeadlift())

        tap(LIVE_EDIT_SMART_LOAD_TAG)
        rule.waitForIdle()

        rule.onNodeWithText("Configura la referencia que alimenta las sugerencias de carga y %RM.").assertExists()
    }

    @Test
    fun theSetFieldsOfTheFocusAreTheOnesOfTheSetEditor() {
        mount(romanianDeadlift(), initialSetIndex = 3)

        // Reps / rango, tipo de carga y las técnicas de la serie siguen en el foco (InlineSetRow en lista).
        rule.onNodeWithText("Reps / rango").assertExists()
        rule.onNodeWithText("Drop-set", substring = true).assertExists()
        rule.onNodeWithText("Rest-pause", substring = true).assertExists()
    }

    @Test
    fun confirmingSavesTheDraftOnceAndClosesTheSheet() {
        mount(romanianDeadlift())

        tap(LIVE_EDIT_ADD_SET_TAG)
        confirm()

        assertNotNull(saved)
        assertNull(sheetState.editSheetExerciseId)
    }

    // ───────────────────────────── G2 · Editar cardio ─────────────────────────────

    private fun stationaryBike(): Exercise = Exercise(
        id = "bike",
        name = "Bici estática",
        cardioDetails = CardioDetails(type = CardioType.BIKE_STATIONARY, intensityLevel = 8, targetDurationSeconds = 20 * 60),
    )

    @Test
    fun theCardioSheetShowsThePrescriptionTheTemplatesAndTheConfirmButton() {
        mount(stationaryBike(), initialSetIndex = 0)

        rule.onNodeWithText("Editar cardio").assertExists()
        rule.onNodeWithText("PLANTILLAS").assertExists()
        rule.onNodeWithText("FORMA").assertExists()
        rule.onNodeWithText("Continuo").assertExists()
        rule.onNodeWithText("Series").assertExists()
        rule.onNodeWithText("Intervalos").assertExists()
        rule.onNodeWithText("RPE").assertExists()
        rule.onNodeWithText("Avanzado").assertExists()
        rule.onNodeWithTag(LIVE_EDIT_CONFIRM_TAG).assertIsDisplayed()
        // Sin los controles de fuerza.
        rule.onNodeWithTag(LIVE_EDIT_FOCUS_TAG).assertDoesNotExist()
    }

    @Test
    fun aCardioTemplateIsAppliedToTheRealCardioAndConfirmedWithTheFooterButton() {
        mount(stationaryBike(), initialSetIndex = 0)
        val before = stationaryBike().cardioDetails

        tapText("Tabata")
        rule.waitForIdle()
        confirm()

        val after = saved!!.cardioDetails!!
        assertTrue("la plantilla cambia la prescripción", after != before)
        assertEquals(CardioType.BIKE_STATIONARY, after.type)
        assertTrue(after.hasIntervals())
        assertEquals("Bici estática", saved!!.name)
    }

    @Test
    fun theCardioFormSwitchChangesTheShapeOfTheRealCardio() {
        mount(stationaryBike(), initialSetIndex = 0)

        tapText("Series")
        rule.waitForIdle()
        confirm()

        assertTrue(saved!!.cardioDetails!!.hasIntervals())
    }
}
