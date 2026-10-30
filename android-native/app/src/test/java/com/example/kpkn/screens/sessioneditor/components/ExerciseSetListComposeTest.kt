package com.example.kpkn.screens.sessioneditor.components

import androidx.compose.ui.graphics.Color
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.assertIsNotEnabled
import androidx.compose.ui.test.hasAnyAncestor
import androidx.compose.ui.test.hasSetTextAction
import androidx.compose.ui.test.hasTestTag
import androidx.compose.ui.test.hasText
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onAllNodesWithText
import androidx.compose.ui.test.onNodeWithContentDescription
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performImeAction
import androidx.compose.ui.test.performTextReplacement
import com.example.kpkn.data.models.Exercise
import com.example.kpkn.data.models.ExerciseSet
import com.example.kpkn.data.models.IntensityMode
import com.example.kpkn.data.models.RepRange
import com.example.kpkn.data.models.TrainingMode
import com.example.kpkn.data.models.UnilateralTarget
import com.example.kpkn.data.models.UnilateralIntensityMode
import com.example.kpkn.data.models.UnilateralMode
import com.example.kpkn.data.protocols.LoadBasis
import com.example.kpkn.screens.sessioneditor.toggledBilateralUnilateral
import com.example.kpkn.screens.sessioneditor.withTrainingMode
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import org.robolectric.annotation.GraphicsMode

/** ER1c1, R1 §4.3: la lista de series, la fila que se despliega, sus controles en los 7 modos y sus acciones. */
@RunWith(RobolectricTestRunner::class)
@GraphicsMode(GraphicsMode.Mode.NATIVE)
@Config(sdk = [34], application = android.app.Application::class, qualifiers = "w360dp-h880dp-mdpi")
class ExerciseSetListComposeTest {

    @get:Rule
    val rule = createComposeRule()

    private fun setEditorContent(content: @androidx.compose.runtime.Composable () -> Unit) {
        rule.setContent { EditorOverlayTestHost(content) }
    }

    private val accent = Color(0xFFF49A6E)

    private fun exerciseWith(
        vararg sets: ExerciseSet,
        mode: TrainingMode = TrainingMode.REPS,
        customUnit: String? = null,
    ) = Exercise(id = "press", name = "Press", trainingMode = mode, customUnit = customUnit, sets = sets.toList())
        .let { if (mode == TrainingMode.AMRAP) it.withTrainingMode(mode) else it }

    private fun renderList(
        exercise: Exercise,
        predictedMetrics: Map<String, Double?> = emptyMap(),
        onUpdateSet: (String, (ExerciseSet) -> ExerciseSet) -> Unit = { _, _ -> },
        onMoveSet: (String, Int) -> Unit = { _, _ -> },
        onRemoveSet: (String) -> Unit = {},
        onTransformExercise: (((Exercise) -> Exercise) -> Unit)? = null,
    ) {
        setEditorContent {
            ExerciseSetList(
                exercise = exercise,
                reference1RM = 100.0,
                trainingMode = exercise.trainingMode,
                customUnit = exercise.customUnit,
                predictedMetrics = predictedMetrics,
                accentColor = accent,
                backgroundAccentColor = accent,
                onAddSet = {},
                onUpdateSet = onUpdateSet,
                onRemoveSet = onRemoveSet,
                onMoveSet = onMoveSet,
                onTransformExercise = onTransformExercise,
            )
        }
    }

    private fun openRow(setId: String) = rule.onNodeWithTag("set-row-$setId").performClick()

    /** Abre la fila de una serie bilateral y comprueba que aparecen sus controles del modo indicado. */
    private fun openedRowShows(mode: TrainingMode, vararg labels: String, customUnit: String? = null) {
        val set = ExerciseSet(id = "s1", targetReps = 8, targetRIR = 2, intensityMode = IntensityMode.RIR, targetPercentageRM = 80.0)
        renderList(exerciseWith(set, mode = mode, customUnit = customUnit))
        openRow("s1")
        // «RIR» aparece como segmento del selector y como etiqueta del valor: basta con que el control esté presente.
        labels.forEach { label ->
            assertTrue("falta el control «$label»", rule.onAllNodesWithText(label).fetchSemanticsNodes().isNotEmpty())
        }
    }

    @Test
    fun lasTecnicasSoloAparecenAFallo() {
        renderList(exerciseWith(ExerciseSet(id = "s1", targetReps = 8),
            ExerciseSet(id = "s2", targetReps = 8, isFailure = true, intensityMode = IntensityMode.FAILURE)))
        openRow("s1")
        rule.onNodeWithText("¿Vas más allá del fallo?").assertDoesNotExist()
        rule.onNodeWithText("Drop-set").assertDoesNotExist()
        rule.onNodeWithText("Rest-pause").assertDoesNotExist()
        openRow("s2")
        rule.onNodeWithText("¿Vas más allá del fallo?").assertExists()
        rule.onNodeWithText("Drop-set").assertExists()
        rule.onNodeWithText("Rest-pause").assertExists()
    }

    @Test
    fun elDescansoEsDeLaSerieYSePuedeVolverAlDelEjercicio() {
        var saved: ExerciseSet? = null
        val set = ExerciseSet(id = "s1", restAfterSeconds = 60)
        renderList(exerciseWith(set), onUpdateSet = { _, transform -> saved = transform(set) })
        openRow("s1")
        rule.onNodeWithText("DESCANSO").assertExists()
        rule.onNodeWithText("Propio").assertExists()
        rule.onNodeWithContentDescription("Sumar 15 segundos").performClick()
        rule.waitForIdle()
        assertEquals(75, saved?.restAfterSeconds)
        rule.onNodeWithContentDescription("Usar el descanso del ejercicio").performClick()
        rule.waitForIdle()
        assertEquals(null, saved?.restAfterSeconds)
    }

    @Test
    fun lasFlechasCambianElFocoSinMoverLasSeries() {
        renderList(exerciseWith(ExerciseSet(id = "s1"), ExerciseSet(id = "s2")))
        openRow("s1")
        rule.onNodeWithText("Serie 1 de 2").assertExists()
        rule.onNodeWithContentDescription("Serie siguiente").performClick()
        rule.onNodeWithText("Serie 2 de 2").assertExists()
        rule.onNodeWithContentDescription("Serie anterior").performClick()
        rule.onNodeWithText("Serie 1 de 2").assertExists()
    }

    @Test
    fun siempreHayUnaSerieElegidaYSoloUnaConFoco() {
        renderList(exerciseWith(ExerciseSet(id = "s1", targetReps = 8), ExerciseSet(id = "s2", targetReps = 10)))
        // La primera serie ya está elegida al abrir el ejercicio (como en el lienzo) y su foco es el único.
        rule.onAllNodesWithText("Externa").fetchSemanticsNodes().let { assertEquals(1, it.size) }
        rule.onNodeWithTag("set-focus-s1").assertExists()
        openRow("s2")
        rule.onAllNodesWithText("Externa").fetchSemanticsNodes().let { assertEquals(1, it.size) }
        rule.onNodeWithTag("set-focus-s1").assertDoesNotExist()
        rule.onNodeWithTag("set-focus-s2").assertExists()
    }

    @Test
    fun laFilaDeRepsTieneSusControles() {
        openedRowShows(TrainingMode.REPS, "Externa", "REPETICIONES", "INTENSIDAD", "RIR")
    }

    @Test
    fun laFilaDeAmrapTieneSusControles() {
        openedRowShows(TrainingMode.AMRAP, "REPS MÍN.", "AMRAP", "Externa")
    }

    /** Una serie RM sin rango ni RIR (pura) enseña su «% del RM» con su deslizador (ed-serie-rm). */
    @Test
    fun laFilaDeRmPuraTieneSusControles() {
        renderList(exerciseWith(rmSet(id = "s1", range = null, rir = null), mode = TrainingMode.RM))
        openRow("s1")
        listOf("% DEL RM", "Externa").forEach { label ->
            assertTrue("falta el control «$label»", rule.onAllNodesWithText(label).fetchSemanticsNodes().isNotEmpty())
        }
    }

    /** B3: el RM tiene un solo campo (% del RM) y la receta (rango y RIR) no se borra. */
    @Test
    fun laFilaRmMuestraPorcentajeYCargaSinRir() {
        renderList(exerciseWith(rmSet(id = "s1", range = RepRange(4, 6), rir = 2), mode = TrainingMode.RM))
        openRow("s1")
        rule.onNodeWithText("% DEL RM").assertExists()
        rule.onNodeWithText("RIR").assertDoesNotExist()
    }

    @Test
    fun editarElPorcentajeConservaElRangoYRirDeLaReceta() {
        val set = rmSet(id = "s1", range = RepRange(4, 6), rir = 2)
        var saved: ExerciseSet? = null
        renderList(exerciseWith(set, mode = TrainingMode.RM), onUpdateSet = { _, update -> saved = update(set) })
        openRow("s1")
        rule.onNodeWithContentDescription("% del RM").performClick()
        rule.onNode(hasSetTextAction()).performTextReplacement("85")
        rule.onNode(hasSetTextAction()).performImeAction()
        assertEquals(85.0, saved?.targetPercentageRM)
        assertEquals(2, saved?.targetRIR)
        assertEquals(RepRange(4, 6), saved?.targetRepsRange)
    }

    /** Serie RM de prueba: 95 kg al 80 % de un 1RM de 120 kg, con el rango y el RIR que se indiquen. */
    private fun rmSet(id: String, range: RepRange?, rir: Int?) = ExerciseSet(
        id = id,
        weight = 95.0,
        targetPercentageRM = 80.0,
        targetReps = range?.max,
        targetRepsRange = range,
        targetRIR = rir,
        intensityMode = IntensityMode.SOLO_RM,
        loadBasis = LoadBasis.PERCENT_1RM,
    )

    @Test
    fun laFilaDeSoloRpeTieneSuRpeObjetivo() {
        openedRowShows(TrainingMode.SOLO_RPE, "RPE OBJETIVO")
    }

    @Test
    fun laFilaDeTiempoTieneSuTiempo() {
        openedRowShows(TrainingMode.TIME, "DURACIÓN OBJETIVO", "Externa")
    }

    @Test
    fun laFilaDeDistanciaTieneSuDistancia() {
        openedRowShows(TrainingMode.DISTANCE, "DISTANCIA (M)", "Externa")
    }

    @Test
    fun laFilaDePersonalizadoTieneSuUnidad() {
        openedRowShows(TrainingMode.CUSTOM, "VATIOS", "Externa", customUnit = "vatios")
    }

    @Test
    fun aplicarATodasCopiaLaConfiguracionTrasConfirmar() {
        val sets = listOf(
            ExerciseSet(id = "s1", targetReps = 8, targetRIR = 2, intensityMode = IntensityMode.RIR),
            ExerciseSet(id = "s2", targetReps = 10, targetRIR = 1, intensityMode = IntensityMode.RIR),
            ExerciseSet(id = "s3", targetReps = 12, targetRIR = 0, intensityMode = IntensityMode.RIR),
        )
        val updated = mutableMapOf<String, ExerciseSet>()
        renderList(
            exerciseWith(*sets.toTypedArray()),
            onUpdateSet = { id, transform ->
                updated[id] = transform(updated[id] ?: sets.first { it.id == id })
            },
        )
        openRow("s1")
        rule.onNodeWithText("Aplicar a todas").performClick()
        rule.mainClock.advanceTimeBy(3_000)
        rule.waitForIdle()
        rule.onNodeWithText("Aplicar").assertIsDisplayed().performClick()
        // La hoja baja (230 ms) y solo entonces se aplica (`closeLayer(cb)` del lienzo).
        rule.mainClock.advanceTimeBy(1_000)
        rule.waitForIdle()
        assertEquals(setOf("s2", "s3"), updated.keys)
        assertEquals(8, updated.getValue("s2").targetReps)
        assertEquals(2, updated.getValue("s3").targetRIR)
    }

    @Test
    fun duplicarInsertaUnaCopiaJustoDespues() {
        val exercise = exerciseWith(ExerciseSet(id = "s1", targetReps = 8), ExerciseSet(id = "s2", targetReps = 10))
        var result: Exercise? = null
        renderList(exercise, onTransformExercise = { transform -> result = transform(exercise) })
        openRow("s1")
        rule.onNodeWithText("Duplicar").performClick()
        val sets = result!!.sets
        assertEquals(3, sets.size)
        assertEquals(8, sets[1].targetReps)
        assertTrue(sets[1].id != "s1")
    }

    @Test
    fun subirYBajarMuevenLaSerieAbierta() {
        val moves = mutableListOf<Pair<String, Int>>()
        renderList(
            exerciseWith(ExerciseSet(id = "s1"), ExerciseSet(id = "s2"), ExerciseSet(id = "s3")),
            onMoveSet = { id, direction -> moves += id to direction },
        )
        openRow("s2")
        // Subir y Bajar viven en el menú «⋯» de la serie.
        rule.onNodeWithContentDescription("Más acciones de la serie").performClick()
        rule.onNodeWithText("Subir").performClick()
        rule.onNodeWithContentDescription("Más acciones de la serie").performClick()
        rule.onNodeWithText("Bajar").performClick()
        assertEquals(listOf("s2" to -1, "s2" to 1), moves)
    }

    @Test
    fun eliminarQuitaLaSerieSinDejarLaListaVacia() {
        val removed = mutableListOf<String>()
        renderList(
            exerciseWith(ExerciseSet(id = "s1"), ExerciseSet(id = "s2")),
            onRemoveSet = { id -> removed += id },
        )
        openRow("s1")
        rule.onNodeWithContentDescription("Más acciones de la serie").performClick()
        rule.onNodeWithText("Eliminar serie").performClick()
        assertEquals(listOf("s1"), removed)
    }

    /** «Eliminar serie» gana «Deshacer» (decisión del editor): devuelve la serie a su sitio. */
    @Test
    fun eliminarOfreceDeshacerYDevuelveLaSerieASuSitio() {
        val exercise = exerciseWith(ExerciseSet(id = "s1", targetReps = 8), ExerciseSet(id = "s2", targetReps = 10))
        var current = exercise
        renderList(
            exercise,
            onRemoveSet = { id -> current = current.copy(sets = current.sets.filterNot { it.id == id }) },
            onTransformExercise = { transform -> current = transform(current) },
        )
        openRow("s1")
        rule.onNodeWithContentDescription("Más acciones de la serie").performClick()
        rule.onNodeWithText("Eliminar serie").performClick()
        rule.waitForIdle()
        assertEquals(listOf("s2"), current.sets.map { it.id })
        rule.onNodeWithText("Serie eliminada").assertExists()
        rule.onNodeWithContentDescription("Deshacer").performClick()
        rule.waitForIdle()
        assertEquals(listOf("s1", "s2"), current.sets.map { it.id })
    }

    @Test
    fun conUnaSolaSerieEliminarEstaDesactivadoYLoExplica() {
        renderList(exerciseWith(ExerciseSet(id = "s1")))
        openRow("s1")
        rule.onNodeWithContentDescription("Más acciones de la serie").performClick()
        rule.onNodeWithText("Eliminar serie").assertIsNotEnabled()
        rule.onNodeWithText("Siempre queda una serie").assertExists()
    }

    @Test
    fun enUnilateralSinLadoDerechoSeOfreceAnadirR() {
        val sets = listOf(ExerciseSet(id = "s1", targetReps = 8))
        val uni = exerciseWith(*sets.toTypedArray())
            .toggledBilateralUnilateral()
            .let { unilateral -> unilateral.copy(sets = unilateral.sets.map { set -> set.copy(rightTarget = null) }) }
        val updated = mutableMapOf<String, ExerciseSet>()
        renderList(uni, onUpdateSet = { id, transform -> updated[id] = transform(sets.first { it.id == id }) })
        rule.onNodeWithTag("set-row-s1:L").assertExists()
        rule.onNodeWithTag("set-row-s1:R").assertDoesNotExist()
        // Lo que falta es una ficha punteada con su nombre («R1»).
        rule.onNodeWithTag("set-add-side-s1-R").performClick()
        assertTrue(updated.getValue("s1").rightTarget != null)
    }

    @Test
    fun fichasUnilateralesMuestranLasRepsDeCadaLadoSobreElRangoBase() {
        val set = ExerciseSet(
            id = "s1",
            targetRepsRange = RepRange(6, 10),
            leftTarget = UnilateralTarget(targetReps = 10),
            rightTarget = UnilateralTarget(targetReps = 12),
        )
        val exercise = exerciseWith(set).copy(
            isUnilateral = true,
            unilateralMode = UnilateralMode.UNILATERAL_PAIRED,
            unilateralIntensityMode = UnilateralIntensityMode.INDEPENDENT,
        )

        renderList(exercise)

        rule.onNodeWithTag("set-row-s1:L").assertIsDisplayed()
        rule.onNodeWithTag("set-row-s1:R").assertIsDisplayed()
        assertEquals(0, rule.onAllNodesWithText("6–10").fetchSemanticsNodes().size)
        rule.onNode(hasText("10") and hasAnyAncestor(hasTestTag("set-row-s1:L")), useUnmergedTree = true).assertExists()
        rule.onNode(hasText("12") and hasAnyAncestor(hasTestTag("set-row-s1:R")), useUnmergedTree = true).assertExists()

        rule.onNodeWithTag("set-row-s1:L").performClick()
        rule.onNodeWithTag("set-focus-s1:L").assertExists()
        rule.onNode(hasText("10") and hasAnyAncestor(hasTestTag("set-focus-s1:L")), useUnmergedTree = true).assertExists()
        rule.onNodeWithTag("set-row-s1:R").performClick()
        rule.onNodeWithTag("set-focus-s1:L").assertDoesNotExist()
        rule.onNodeWithTag("set-focus-s1:R").assertExists()
        rule.onNode(hasText("12") and hasAnyAncestor(hasTestTag("set-focus-s1:R")), useUnmergedTree = true).assertExists()
    }

    @Test
    fun elSelectorDeUnilateralCambiaDeModoComoToggledBilateralUnilateral() {
        val bilateral = exerciseWith(ExerciseSet(id = "s1", targetReps = 8))
        val unilateral = bilateral.toggledBilateralUnilateral()
        assertTrue(unilateral.sets.first().leftTarget != null && unilateral.sets.first().rightTarget != null)
        assertEquals(15, unilateral.restBetweenSidesSeconds)
    }
}
