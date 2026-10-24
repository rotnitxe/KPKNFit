package com.example.kpkn.screens.workout

import android.graphics.Bitmap
import android.graphics.Canvas
import android.view.View
import androidx.activity.ComponentActivity
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.toArgb
import androidx.compose.ui.platform.LocalView
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.test.click
import androidx.compose.ui.test.hasSetTextAction
import androidx.compose.ui.test.junit4.createAndroidComposeRule
import androidx.compose.ui.test.onAllNodesWithContentDescription
import androidx.compose.ui.test.onAllNodesWithText
import androidx.compose.ui.test.onLast
import androidx.compose.ui.semantics.SemanticsActions
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.onNodeWithContentDescription
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.onRoot
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performTextReplacement
import androidx.compose.ui.test.performTouchInput
import androidx.compose.ui.unit.dp
import androidx.compose.ui.zIndex
import com.example.kpkn.data.models.Exercise
import com.example.kpkn.data.models.ExerciseReadiness
import com.example.kpkn.data.models.LoadModeV2
import com.example.kpkn.data.models.MuscleAdvance
import com.example.kpkn.data.models.PostSessionPreview
import com.example.kpkn.data.models.Session
import com.example.kpkn.data.models.SessionPart
import com.example.kpkn.domain.auge.SessionIntensityResult
import com.example.kpkn.screens.workout.components.RoadmapPendingAction
import com.example.kpkn.screens.workout.components.SeriesOptionsSheet
import com.example.kpkn.screens.workout.components.SetAdjustmentOverlay
import com.example.kpkn.screens.workout.components.UltraFastPreviewSheet
import com.example.kpkn.screens.workout.components.VolumeAdvanceModal
import com.example.kpkn.screens.workout.components.WorkoutReadinessSheet
import com.example.kpkn.screens.workout.components.WorkoutRoadmapBar
import com.example.kpkn.ui.components.KpknGlassDialog
import com.example.kpkn.ui.components.KpknOverlayHostContent
import com.example.kpkn.ui.components.KpknOverlayHostController
import com.example.kpkn.ui.components.LocalKpknOverlayHost
import com.example.kpkn.ui.theme.KPKNTheme
import com.example.kpkn.ui.tour.TourSeenStore
import dev.chrisbanes.haze.HazeState
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import org.robolectric.annotation.GraphicsMode
import org.robolectric.shadows.ShadowToast

private const val SESSION_TAG = "sesion"
private val SESSION_COLOR = Color(0xFF3A7BD5)

/** Sin recorridos: el anfitrión de capas de la prueba no los necesita. */
private object NoTours : TourSeenStore {
    override fun seenIds(): Set<String> = emptySet()
    override fun markSeen(tourId: String) = Unit
}

/**
 * Cerrar una hoja o una capa por accidente (LV4, 15): con Atrás o tocando fuera, la sesión tiene que verse tal cual, sin velo ni
 * recuadro negro. Cada capa real de la sesión en vivo se monta en el mismo anfitrión de superposiciones que usa la app (`KpknPortal`)
 * sobre una «sesión» de un color liso; al cerrarla, todo el plano vuelve a ser ese color.
 */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [34], qualifiers = "w360dp-h880dp-xxhdpi", application = android.app.Application::class)
@GraphicsMode(GraphicsMode.Mode.NATIVE)
class LiveLayerDismissTest {

    @get:Rule
    val rule = createAndroidComposeRule<ComponentActivity>()

    private var open by mutableStateOf(true)
    private var hostView: View? = null

    private fun show(content: @Composable () -> Unit) {
        rule.setContent {
            hostView = LocalView.current
            KPKNTheme {
                val host = remember { KpknOverlayHostController(tourSeen = NoTours) }
                CompositionLocalProvider(LocalKpknOverlayHost provides host) {
                    Box(Modifier.fillMaxSize()) {
                        Box(Modifier.fillMaxSize().background(SESSION_COLOR).testTag(SESSION_TAG))
                        content()
                        // Como MainActivity: el anfitrión solo se monta con capas dentro.
                        if (host.entries.isNotEmpty()) {
                            Box(Modifier.fillMaxSize().zIndex(400f)) { KpknOverlayHostContent(host) }
                        }
                    }
                }
            }
        }
        rule.waitForIdle()
    }

    private fun pressBack() {
        rule.runOnIdle { rule.activity.onBackPressedDispatcher.onBackPressed() }
        rule.waitForIdle()
    }

    private fun tapOutside() {
        rule.onRoot().performTouchInput { click(Offset(8f, 8f)) }
        rule.waitForIdle()
    }

    private fun assertSessionIsUntouched(layer: String) {
        val view = hostView!!
        val bitmap = Bitmap.createBitmap(view.width, view.height, Bitmap.Config.ARGB_8888)
        rule.runOnUiThread {
            val canvas = Canvas(bitmap)
            canvas.drawColor(android.graphics.Color.BLACK)
            view.draw(canvas)
        }
        val expected = SESSION_COLOR.toArgb()
        val points = listOf(
            bitmap.width / 2 to bitmap.height / 2,
            10 to 10,
            bitmap.width - 10 to bitmap.height - 10,
            bitmap.width / 2 to bitmap.height - 120,
            bitmap.width / 2 to 120,
        )
        points.forEach { (x, y) ->
            val actual = bitmap.getPixel(x, y)
            fun channel(color: Int, shift: Int) = (color shr shift) and 0xFF
            val close = listOf(16, 8, 0).all { shift -> kotlin.math.abs(channel(actual, shift) - channel(expected, shift)) <= 2 }
            assertTrue("$layer: el punto ($x, $y) debería ser la sesión (#${Integer.toHexString(expected)}) y es #${Integer.toHexString(actual)}", close)
        }
    }

    /** Atrás y tocar fuera cierran la capa y dejan la sesión intacta. */
    private fun assertClosesWithBackAndWithATapOutside(layer: String, title: String, content: @Composable () -> Unit) {
        show { if (open) content() }
        rule.onNodeWithText(title).assertExists()
        pressBack()
        rule.onNodeWithText(title).assertDoesNotExist()
        assertSessionIsUntouched("$layer · Atrás")

        rule.runOnUiThread { open = true }
        rule.waitForIdle()
        rule.onNodeWithText(title).assertExists()
        tapOutside()
        rule.onNodeWithText(title).assertDoesNotExist()
        assertSessionIsUntouched("$layer · tocar fuera")
    }

    private val sampleExercise: Exercise = liveExercise("a", sets = 3)

    private val sampleReadiness = ExerciseReadiness(
        exerciseId = "a",
        exerciseName = "Ejercicio A",
        overallScore = 62,
        muscularComponent = 70,
        cnsComponent = 55,
        spinalComponent = 48,
        articularComponent = 80,
        structuralComponent = 75,
        relatedArticular = emptyList(),
        muscularWeight = 0.5,
        cnsWeight = 0.3,
        spinalWeight = 0.2,
        articularWeight = 0.0,
        setsPenaltyFactor = 1.0,
        intensityPenaltyFactor = 1.0,
        ermProximityFactor = 1.0,
        patternId = null,
        involvedMuscleIds = emptyList(),
    )

    // ── Hojas que se cierran con Atrás o tocando fuera ─────────────────────────────────────────────────────────────────────────

    @Test
    fun theUltraFastSheetClosesAndLeavesTheSessionUntouched() {
        open = true
        assertClosesWithBackAndWithATapOutside("Ultrarrápido", "Ultrarrápido") {
            UltraFastPreviewSheet(
                preview = null,
                savedSeconds = 0,
                visibleExercises = emptyList(),
                ultraFastManualOverrides = emptyMap(),
                onToggleOverride = {},
                onConfirm = {},
                onDismiss = { open = false },
            )
        }
    }

    @Test
    fun theSeriesOptionsSheetClosesAndLeavesTheSessionUntouched() {
        open = true
        // La hoja se abre con la serie en la que se pulsó («Serie 2»).
        assertClosesWithBackAndWithATapOutside("Opciones de la serie", "Serie 2") {
            SeriesOptionsSheet(
                exercise = sampleExercise,
                target = SeriesTypeTarget(exerciseId = "a", fromSetIdx = 1, toSetIdx = 2),
                completedSets = emptyMap(),
                omittedSetKeys = emptySet(),
                onDismiss = { open = false },
                onApply = { _, _ -> open = false },
                onEdit = {},
                onAddSet = {},
                onSkipSet = {},
                onDeleteSet = {},
            )
        }
    }

    @Test
    fun aWorkoutDrawerClosesAndLeavesTheSessionUntouched() {
        open = true
        assertClosesWithBackAndWithATapOutside("Cajón", "Historial de prueba") {
            WorkoutDrawer(title = "Historial de prueba", onDismiss = { open = false }, hazeState = null) {
                Text("Sin series anteriores", modifier = Modifier.padding(4.dp))
            }
        }
    }

    @Test
    fun theReorderDialogClosesAndLeavesTheSessionUntouched() {
        val state = WorkoutStructureSheetsState().apply {
            showReorderSheet = true
            reorderSheetExerciseIds = listOf("a", "b")
        }
        val exercises = listOf(liveExercise("a"), liveExercise("b"))
        open = true
        show {
            WorkoutReorderSheets(
                state = state,
                visibleExercises = exercises,
                originalExercisePartMap = mapOf("a" to "Grupo 1", "b" to "Grupo 1"),
                onApplyReorder = { _, _, _ -> },
            )
        }
        rule.onNodeWithText("Orden de la sesión").assertExists()
        pressBack()
        rule.onNodeWithText("Orden de la sesión").assertDoesNotExist()
        assertEquals(false, state.showReorderSheet)
        assertSessionIsUntouched("Reordenar · Atrás")

        rule.runOnUiThread {
            state.showReorderSheet = true
            state.reorderSheetExerciseIds = listOf("a", "b")
        }
        rule.waitForIdle()
        rule.onNodeWithText("Orden de la sesión").assertExists()
        tapOutside()
        rule.onNodeWithText("Orden de la sesión").assertDoesNotExist()
        assertEquals(false, state.showReorderSheet)
        assertSessionIsUntouched("Reordenar · tocar fuera")
    }

    @Test
    fun theTagListClosesAndLeavesTheSessionUntouched() {
        open = true
        assertClosesWithBackAndWithATapOutside("Etiquetas", "Etiquetas") {
            WorkoutTagListOverlay(
                rows = emptyList(),
                onSelectTag = {},
                onCreateTag = {},
                onDismiss = { open = false },
                hasSeenEducation = true,
            )
        }
    }

    @Test
    fun aGlassDialogClosesAndLeavesTheSessionUntouched() {
        open = true
        assertClosesWithBackAndWithATapOutside("Diálogo de vidrio", "¿Qué deseas hacer?") {
            KpknGlassDialog(onDismissRequest = { open = false }) { Text("¿Qué deseas hacer?") }
        }
    }

    @Test
    fun theFinishSummaryClosesAndLeavesTheSessionUntouched() {
        open = true
        assertClosesWithBackAndWithATapOutside("Resumen final", "SESIÓN TERMINADA") { SummarySheet() }
    }

    @Composable
    private fun SummarySheet(onNotesDraft: (String) -> Unit = {}) {
        RecordedSessionFinishSheet(name="SESIÓN TERMINADA",completedExercises=emptyList(),durationMinutes=45,
            initialNotes="",saving=false,onNotes=onNotesDraft,onAlbum={},onConfirm={_,_->},onDismiss={open=false})
    }

    @Test
    fun theNoteTypedInTheSummaryIsKeptWhenTheSummaryIsClosed() {
        open = true
        var kept: String? = null
        show { if (open) SummarySheet(onNotesDraft = { kept = it }) }

        rule.waitForIdle()
        // La nota rápida pertenece a la sección Notas y conserva el borrador.
        rule.onNodeWithTag("finish-notes", useUnmergedTree = true).performTextReplacement("Me sentí fuerte")
        pressBack()

        assertEquals("la nota pasa a la sesión en vez de perderse", "Me sentí fuerte", kept)
        assertFalse("y el resumen se cierra", hasText("SESIÓN TERMINADA"))
    }

    @Test
    fun closingTheSummaryWithoutTypingAnythingDoesNotTouchTheNote() {
        open = true
        var kept: String? = null
        show { if (open) SummarySheet(onNotesDraft = { kept = it }) }

        pressBack()

        assertEquals("sin nada escrito no hay nada que guardar", null, kept)
    }

    @Test
    fun theReadinessSheetClosesWithoutAnsweringAndLeavesTheSessionUntouched() {
        open = true
        var saved = false
        assertClosesWithBackAndWithATapOutside("Estado previo", "¿Cómo llegas a entrenar hoy?") {
            WorkoutReadinessSheet(
                showReadinessSheet = true,
                gender = null,
                sessionMuscleStartingBatteries = emptyMap(),
                readinessNeuralStart = 80,
                readinessMuscularStart = 80,
                readinessSpinalStart = 80,
                hazeState = HazeState(),
                onSave = { _, _, _, _, _, _, _, _, _ -> saved = true },
                onDismiss = { open = false },
            )
        }
        assertFalse("cerrarlo no responde nada: el estado previo queda pendiente", saved)
    }

    @Test
    fun theSetAdjustmentSheetClosesLikeCancelAndAppliesNothing() {
        open = true
        var applied = false
        assertClosesWithBackAndWithATapOutside("Ajuste por estado actual", "Ajuste por estado actual") {
            SetAdjustmentOverlay(
                exercise = sampleExercise,
                currentSet = sampleExercise.sets.first(),
                setIndex = 0,
                exerciseReadiness = sampleReadiness,
                weightSuggestion = null,
                averageErm = 118.0,
                bodyWeight = 80.0,
                loadMode = LoadModeV2.LOAD,
                onDismiss = { open = false },
                onApply = { applied = true },
            )
        }
        assertFalse("cerrar el ajuste no aplica ninguna carga", applied)
    }

    // ── Hojas protegidas: Atrás y tocar fuera no las cierran, pero nada se rompe y sus botones sí ─────────────────────────────

    @Test
    fun theVolumeAdvanceSheetKeepsItsContentAndClosesWithItsButton() {
        open = true
        show {
            if (open) {
                VolumeAdvanceModal(
                    advances = listOf(
                        MuscleAdvance(
                            muscleId = "pecho",
                            muscleName = "Pecho",
                            currentSets = 14.0,
                            targetSets = 12.0,
                            deficitSets = 2.0,
                            targetSessionId = "s2",
                            targetSessionName = "Torso B",
                        ),
                    ),
                    onAccept = { open = false },
                    onDismiss = { open = false },
                )
            }
        }
        rule.onNodeWithText("Adelanto de volumen").assertExists()
        pressBack()
        rule.onNodeWithText("Adelanto de volumen").assertExists()
        assertEquals("Atrás no se queda muda: dice por qué no cierra", BLOCKED_BACK_HINT, ShadowToast.getTextOfLatestToast())
        tapOutside()
        rule.onNodeWithText("Adelanto de volumen").assertExists()

        rule.onNodeWithText("Omitir por ahora").performClick()
        rule.waitForIdle()
        rule.onNodeWithText("Adelanto de volumen").assertDoesNotExist()
        assertSessionIsUntouched("Adelanto de volumen · botón")
    }

    @Test
    fun theQuickDiscomfortSheetKeepsItsContentAndClosesWithItsButton() {
        open = true
        var saved: List<String>? = null
        show {
            if (open) {
                QuickExecutionErrorDiscomfortSheet(
                    exerciseName = "Press banca",
                    onSave = { ids ->
                        saved = ids
                        open = false
                    },
                    onDismiss = { open = false },
                )
            }
        }
        rule.onNodeWithText("Reportar molestias").assertExists()
        pressBack()
        rule.onNodeWithText("Reportar molestias").assertExists()
        assertEquals("Atrás no se queda muda: dice por qué no cierra", BLOCKED_BACK_HINT, ShadowToast.getTextOfLatestToast())
        tapOutside()
        rule.onNodeWithText("Reportar molestias").assertExists()

        rule.onNodeWithText("Sin molestias").performClick()
        rule.waitForIdle()
        rule.onNodeWithText("Reportar molestias").assertDoesNotExist()
        assertEquals(emptyList<String>(), saved)
        assertSessionIsUntouched("Molestias · botón")
    }

    // ── El carrusel enseña el orden de la sesión ───────────────────────────────────────────────────────────────────────────────

    @Test
    fun theCarouselShowsTheExercisesLeftToRightInTheOrderOfTheSession() {
        // Dos tarjetas caben a la vista del carrusel en una pantalla de 360 dp; el orden no es el alfabético, para que no se confunda.
        val names = listOf("Cc", "Aa")
        val exercises = names.map { liveExercise(it.lowercase(), name = it) }
        rule.setContent {
            KPKNTheme {
                Box(Modifier.fillMaxSize().background(Color.Black)) {
                    LiveSessionLine(exercises, null, null, {}, {}, {}, {}, {}, recordVisible = false, recordEnabled = false)
                }
            }
        }
        rule.waitForIdle()
        val lefts = names.map { name -> rule.onNodeWithContentDescription("Ir a $name").fetchSemanticsNode().boundsInRoot.left }
        assertEquals("de izquierda a derecha, como la lista", lefts.sorted(), lefts)
    }

    // ── Hojas de estructura de la sesión, con la bandera de su estado ──────────────────────────────────────────────────────────

    private fun hasText(text: String): Boolean = rule.onAllNodesWithText(text).fetchSemanticsNodes().isNotEmpty()

    /** Atrás y tocar fuera cierran una hoja gobernada por una bandera de [WorkoutStructureSheetsState] y dejan la sesión intacta. */
    private fun assertStateSheetCloses(
        layer: String,
        title: String,
        state: WorkoutStructureSheetsState,
        reopen: () -> Unit,
        isOpen: () -> Boolean,
    ) {
        assertTrue("$layer: parte abierta", hasText(title))
        pressBack()
        assertFalse("$layer · Atrás: la hoja se cierra", hasText(title))
        assertFalse("$layer · Atrás: la bandera se limpia", isOpen())
        assertFalse("$layer · Atrás: ya no cuenta como hoja abierta", state.hasOpenDrawer())
        assertSessionIsUntouched("$layer · Atrás")

        rule.runOnUiThread { reopen() }
        rule.waitForIdle()
        assertTrue("$layer: se vuelve a abrir", hasText(title))
        tapOutside()
        assertFalse("$layer · tocar fuera: la hoja se cierra", hasText(title))
        assertFalse("$layer · tocar fuera: la bandera se limpia", isOpen())
        assertSessionIsUntouched("$layer · tocar fuera")
    }

    @Test
    fun theEditSeriesDrawerClosesAndLeavesTheSessionUntouched() {
        val session = Session(id = "s", name = "Sesión", exercises = listOf(sampleExercise))
        val state = WorkoutStructureSheetsState().apply { editSheetExerciseId = sampleExercise.id }
        show {
            WorkoutEditSeriesSheet(
                state = state,
                modeSession = session,
                sessionAccentColor = SESSION_COLOR,
                bottomHazeState = HazeState(),
                onSaveDefinition = { _, _ -> },
            )
        }

        assertStateSheetCloses(
            layer = "Editar series",
            title = "Editar series",
            state = state,
            reopen = { state.editSheetExerciseId = sampleExercise.id },
            isOpen = { state.editSheetExerciseId != null },
        )
    }

    @Test
    fun theSupersetCreatorClosesAndLeavesTheSessionUntouched() {
        val exercises = listOf("a", "b", "c").map { liveExercise(it) }
        val session = Session(id = "s", name = "Sesión", exercises = exercises)
        val state = WorkoutStructureSheetsState().apply {
            showWorkoutSupersetCreator = true
            workoutSupersetSelectedExerciseId = "a"
        }
        show {
            WorkoutSupersetCreatorSheet(
                state = state,
                modeSession = session,
                renderedParts = listOf(SessionPart(id = "default", name = "Sesión principal", exercises = exercises)),
                visibleExercises = exercises,
                completedSets = emptyMap(),
                hasCatalogAction = false,
                onCreateSuperset = { _, _, _, _, _ -> },
            )
        }

        assertStateSheetCloses(
            layer = "Crear superserie",
            title = "Crear superserie",
            state = state,
            reopen = {
                state.showWorkoutSupersetCreator = true
                state.workoutSupersetSelectedExerciseId = "a"
            },
            isOpen = { state.showWorkoutSupersetCreator },
        )
    }

    // ── Lo que queda pendiente se ve en el cockpit ─────────────────────────────────────────────────────────────────────────────

    private fun showCockpit(pending: List<RoadmapPendingAction>) {
        rule.setContent {
            KPKNTheme {
                Box(Modifier.fillMaxSize().background(Color.Black)) {
                    LiveSessionLine(listOf(liveExercise("a"), liveExercise("b")), "a", null,
                        {}, {}, {}, {}, {}, recordVisible = false, recordEnabled = false, pendingActions = pending)
                }
            }
        }
        rule.waitForIdle()
    }

    @Test
    fun whatIsPendingShowsAsPillsInTheCockpitAndEachOneRunsItsAction() {
        var readiness = 0
        var finish = 0
        showCockpit(
            listOf(
                RoadmapPendingAction("Estado previo") { readiness++ },
                RoadmapPendingAction("Terminar") { finish++ },
            ),
        )

        rule.onNodeWithText("Estado previo").performClick()
        rule.onNodeWithText("Terminar").performClick()

        assertEquals("«Estado previo» vuelve a abrir el estado previo", 1, readiness)
        assertEquals("«Terminar» vuelve al resumen final", 1, finish)
    }

    @Test
    fun theCockpitShowsNoPillsWhenNothingIsPending() {
        showCockpit(emptyList())

        rule.onNodeWithText("Estado previo").assertDoesNotExist()
        rule.onNodeWithText("Terminar").assertDoesNotExist()
    }

    // ── Reordenar: la hoja aplica lo que la persona fija (aquí, con las acciones «Subir» y «Bajar» del asa) ───────────────────

    /** Lo que hace un lector de pantalla con el asa de una fila: su acción de accesibilidad [label] («Subir» o «Bajar»). */
    private fun moveRowWithAccessibilityAction(handleIndex: Int, label: String) {
        val handle = rule.onAllNodesWithContentDescription("Arrastrar para reordenar")[handleIndex]
        rule.runOnUiThread {
            handle.fetchSemanticsNode().config[SemanticsActions.CustomActions].first { it.label == label }.action()
        }
        rule.waitForIdle()
    }

    @Test
    fun theReorderDialogAppliesWhatThePersonSetsWithTheArrows() {
        val exercises = listOf("a", "b", "c").map { liveExercise(it) }
        val state = WorkoutStructureSheetsState().apply {
            showReorderSheet = true
            reorderSheetExerciseIds = exercises.map { it.id }
        }
        var applied: List<String>? = null
        var appliedGlobally: Boolean? = null
        show {
            WorkoutReorderSheets(
                state = state,
                visibleExercises = exercises,
                originalExercisePartMap = exercises.associate { it.id to "Sesión principal" },
                onApplyReorder = { ids, _, global ->
                    applied = ids
                    appliedGlobally = global
                },
                session = Session(id = "s", name = "Sesión", exercises = exercises),
            )
        }

        moveRowWithAccessibilityAction(handleIndex = 0, label = "Bajar")
        assertEquals(listOf("b", "a", "c"), state.reorderSheetExerciseIds)
        rule.onNodeWithText("Listo").performClick()
        rule.waitForIdle()

        assertEquals(listOf("b", "a", "c"), applied)
        assertEquals("ningún ejercicio cambia de grupo: no hace falta confirmar", false, appliedGlobally)
        assertFalse(state.showReorderSheet)
    }

    @Test
    fun theReorderDialogWarnsWhenAnExerciseChangesGroupAndAppliesTheOrderOnceConfirmed() {
        val session = Session(
            id = "s",
            name = "Sesión",
            parts = listOf(listOf("a", "b"), listOf("c", "d")).mapIndexed { index, ids ->
                SessionPart(id = "p$index", name = "Grupo ${index + 1}", exercises = ids.map { liveExercise(it) })
            },
        )
        val state = WorkoutStructureSheetsState().apply {
            showReorderSheet = true
            reorderSheetExerciseIds = listOf("a", "b", "c", "d")
        }
        var applied: List<String>? = null
        var appliedGlobally: Boolean? = null
        show {
            WorkoutReorderSheets(
                state = state,
                visibleExercises = session.allExercises(),
                originalExercisePartMap = mapOf("a" to "Grupo 1", "b" to "Grupo 1", "c" to "Grupo 2", "d" to "Grupo 2"),
                onApplyReorder = { ids, _, global ->
                    applied = ids
                    appliedGlobally = global
                },
                session = session,
            )
        }

        // «Ejercicio C» sube dos filas, hasta ser el primero: pasa del grupo 2 al grupo 1.
        moveRowWithAccessibilityAction(handleIndex = 2, label = "Subir")
        moveRowWithAccessibilityAction(handleIndex = 1, label = "Subir")
        assertEquals(listOf("c", "a", "b", "d"), state.reorderSheetExerciseIds)

        rule.onNodeWithText("Listo").performClick()
        rule.waitForIdle()
        rule.onNodeWithText("Cambio de grupo").assertExists()
        rule.onNodeWithText("Ejercicio C (Grupo 2 → Grupo 1)", substring = true).assertExists()
        assertEquals("hasta confirmar no se aplica nada", null, applied)

        rule.onNodeWithText("Guardar de todas formas").performClick()
        rule.waitForIdle()
        assertEquals(listOf("c", "a", "b", "d"), applied)
        assertEquals(true, appliedGlobally)
        assertFalse(state.showReorderSheet)
    }
}
