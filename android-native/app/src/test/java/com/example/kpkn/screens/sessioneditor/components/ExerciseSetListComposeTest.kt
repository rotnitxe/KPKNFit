package com.example.kpkn.screens.sessioneditor.components

import androidx.compose.ui.graphics.Color
import androidx.compose.ui.test.hasSetTextAction
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
import com.example.kpkn.data.protocols.LoadBasis
import com.example.kpkn.screens.sessioneditor.toggledBilateralUnilateral
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

/** ER1c1, R1 §4.3: la lista de series, la fila que se despliega, sus controles en los 7 modos y sus acciones. */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [34], application = android.app.Application::class, qualifiers = "w360dp-h2000dp-xxhdpi")
class ExerciseSetListComposeTest {

    @get:Rule
    val rule = createComposeRule()

    private val accent = Color(0xFFF49A6E)

    private fun exerciseWith(
        vararg sets: ExerciseSet,
        mode: TrainingMode = TrainingMode.REPS,
        customUnit: String? = null,
    ) = Exercise(id = "press", name = "Press", trainingMode = mode, customUnit = customUnit, sets = sets.toList())

    private fun renderList(
        exercise: Exercise,
        predictedMetrics: Map<String, Double?> = emptyMap(),
        onUpdateSet: (String, (ExerciseSet) -> ExerciseSet) -> Unit = { _, _ -> },
        onMoveSet: (String, Int) -> Unit = { _, _ -> },
        onRemoveSet: (String) -> Unit = {},
        onTransformExercise: (((Exercise) -> Exercise) -> Unit)? = null,
    ) {
        rule.setContent {
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
    fun unaFilaSeAbreYSoloUnaEstaAbiertaALaVez() {
        renderList(exerciseWith(ExerciseSet(id = "s1", targetReps = 8), ExerciseSet(id = "s2", targetReps = 10)))
        openRow("s1")
        rule.onAllNodesWithText("Externa").fetchSemanticsNodes().let { assertEquals(1, it.size) }
        openRow("s2")
        rule.onAllNodesWithText("Externa").fetchSemanticsNodes().let { assertEquals(1, it.size) }
        openRow("s2")
        rule.onAllNodesWithText("Externa").fetchSemanticsNodes().let { assertEquals(0, it.size) }
    }

    @Test
    fun laFilaDeRepsTieneSusControles() {
        openedRowShows(TrainingMode.REPS, "Externa", "Reps / rango", "RPE", "RIR", "Fallo", "Drop-set", "Rest-pause")
    }

    @Test
    fun laFilaDeAmrapTieneSusControles() {
        openedRowShows(TrainingMode.AMRAP, "AMRAP / mín.", "AMRAP", "Externa", "Drop-set", "Rest-pause")
    }

    /** Una serie RM sin rango ni RIR (pura) enseña su %RM y sus «Reps est.» de siempre (ER2a). */
    @Test
    fun laFilaDeRmPuraTieneSusControles() {
        renderList(exerciseWith(rmSet(id = "s1", range = null, rir = null), mode = TrainingMode.RM))
        openRow("s1")
        listOf("%RM", "Reps est.", "Externa", "Drop-set", "Rest-pause").forEach { label ->
            assertTrue("falta el control «$label»", rule.onAllNodesWithText(label).fetchSemanticsNodes().isNotEmpty())
        }
    }

    /** Una serie RM con rango y RIR abre «Reps» y «RIR» junto al %RM, y «Reps est.» pasa a ser una línea de ayuda (ER2a). */
    @Test
    fun laFilaDeRmConRangoYRirTieneRepsRirYLaAyudaDeReps() {
        renderList(
            exerciseWith(rmSet(id = "s1", range = RepRange(4, 6), rir = 2), mode = TrainingMode.RM),
            predictedMetrics = mapOf("s1" to 7.0),
        )
        openRow("s1")
        listOf("%RM", "Reps", "RIR").forEach { label ->
            assertTrue("falta el control «$label»", rule.onAllNodesWithText(label).fetchSemanticsNodes().isNotEmpty())
        }
        assertTrue("sigue el campo «Reps est.»", rule.onAllNodesWithText("Reps est.").fetchSemanticsNodes().isEmpty())
        rule.onNodeWithText("≈ 7 reps máx. al 80 %").assertExists()
    }

    /** Editar el RIR de una serie RM con rango lo guarda en esa serie (ER2a). */
    @Test
    fun editarElRirDeUnaSerieRmLoGuarda() {
        val set = rmSet(id = "s1", range = RepRange(4, 6), rir = 2)
        var saved: ExerciseSet? = null
        rule.setContent {
            ExerciseSetList(
                exercise = exerciseWith(set, mode = TrainingMode.RM),
                reference1RM = 120.0,
                trainingMode = TrainingMode.RM,
                customUnit = null,
                predictedMetrics = mapOf("s1" to 7.0),
                accentColor = accent,
                backgroundAccentColor = accent,
                onAddSet = {},
                onUpdateSet = { _, updater -> saved = updater(set) },
                onRemoveSet = {},
                onMoveSet = { _, _ -> },
            )
        }
        openRow("s1")
        rule.onNodeWithContentDescription("RIR").performClick()
        rule.onNode(hasSetTextAction()).performTextReplacement("3")
        rule.onNode(hasSetTextAction()).performImeAction()
        assertEquals(3, saved?.targetRIR)
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
        openedRowShows(TrainingMode.SOLO_RPE, "RPE obj.", "Externa", "Drop-set", "Rest-pause")
    }

    @Test
    fun laFilaDeTiempoTieneSuTiempo() {
        openedRowShows(TrainingMode.TIME, "Tiempo", "Externa", "Drop-set", "Rest-pause")
    }

    @Test
    fun laFilaDeDistanciaTieneSuDistancia() {
        openedRowShows(TrainingMode.DISTANCE, "Dist.", "Externa", "Drop-set", "Rest-pause")
    }

    @Test
    fun laFilaDePersonalizadoTieneSuUnidad() {
        openedRowShows(TrainingMode.CUSTOM, "vatios", "Externa", "Drop-set", "Rest-pause", customUnit = "vatios")
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
        rule.onNodeWithText("Aplicar").performClick()
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
        rule.onNodeWithText("Subir").performClick()
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
        rule.onNodeWithText("Eliminar").performClick()
        assertEquals(listOf("s1"), removed)
    }

    @Test
    fun conUnaSolaSerieNoSeOfreceEliminar() {
        renderList(exerciseWith(ExerciseSet(id = "s1")))
        openRow("s1")
        rule.onNodeWithText("Eliminar").assertDoesNotExist()
    }

    @Test
    fun enUnilateralSinLadoDerechoSeOfreceAnadirD() {
        val sets = listOf(ExerciseSet(id = "s1", targetReps = 8))
        val uni = exerciseWith(*sets.toTypedArray())
            .toggledBilateralUnilateral()
            .let { unilateral -> unilateral.copy(sets = unilateral.sets.map { set -> set.copy(rightTarget = null) }) }
        val updated = mutableMapOf<String, ExerciseSet>()
        renderList(uni, onUpdateSet = { id, transform -> updated[id] = transform(sets.first { it.id == id }) })
        rule.onNodeWithText("I1").assertExists()
        rule.onNodeWithText("D1").assertDoesNotExist()
        rule.onNodeWithText("+ Añadir D").performClick()
        assertTrue(updated.getValue("s1").rightTarget != null)
    }

    @Test
    fun elSelectorDeUnilateralCambiaDeModoComoToggledBilateralUnilateral() {
        val bilateral = exerciseWith(ExerciseSet(id = "s1", targetReps = 8))
        val unilateral = bilateral.toggledBilateralUnilateral()
        assertTrue(unilateral.sets.first().leftTarget != null && unilateral.sets.first().rightTarget != null)
        assertEquals(15, unilateral.restBetweenSidesSeconds)
    }
}
