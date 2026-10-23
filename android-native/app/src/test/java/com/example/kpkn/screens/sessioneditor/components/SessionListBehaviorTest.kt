package com.example.kpkn.screens.sessioneditor.components

import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.semantics.SemanticsProperties
import androidx.compose.ui.test.SemanticsMatcher
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.assertTextEquals
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNodeWithContentDescription
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performTextReplacement
import androidx.compose.ui.test.performTouchInput
import androidx.compose.ui.test.swipeLeft
import com.example.kpkn.data.models.Exercise
import com.example.kpkn.data.models.ExerciseSet
import com.example.kpkn.data.models.RepRange
import com.example.kpkn.data.models.SessionPart
import com.example.kpkn.data.models.SupersetGroup
import com.example.kpkn.data.models.TrainingMode
import com.example.kpkn.screens.sessioneditor.components.sheets.SupersetPickRow
import com.example.kpkn.screens.sessioneditor.design.SessionExerciseUndoNotice
import kotlin.math.abs
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

/**
 * La lista del editor (ER1b): el grupo se ve con su nombre y su resumen, se renombra en sitio, se pliega, abre su diálogo al deslizarlo;
 * la superserie muestra a sus miembros plegada; la fila tiene su dosis y el aviso «Deshacer» devuelve el ejercicio.
 */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [34], application = android.app.Application::class, qualifiers = "w360dp-h900dp-xxhdpi")
class SessionListBehaviorTest {

    @get:Rule
    val rule = createComposeRule()

    private val noop: () -> Unit = {}

    private fun sets(exercise: String, count: Int, reps: Int = 8): List<ExerciseSet> = (1..count).map { index ->
        ExerciseSet(id = "$exercise-$index", targetReps = reps, targetRepsRange = RepRange(reps - 2, reps))
    }

    private fun exercise(id: String, name: String, count: Int = 2): Exercise =
        Exercise(id = id, name = name, trainingMode = TrainingMode.REPS, sets = sets(id, count))

    @Composable
    private fun Header(part: SessionPart, onRename: (String) -> Unit = {}, onToggle: () -> Unit = {}) {
        GroupEditorCard(
            part = part,
            collapsed = false,
            onToggleCollapse = onToggle,
            onRename = onRename,
            onRemove = {},
            isDragging = false,
            isDropTarget = false,
            onBoundsChange = {},
            onDrag = {},
            onDragEnd = noop,
        )
    }

    @Test
    fun elGrupoPintaSuNombreYSuResumen() {
        val part = SessionPart(
            id = "g",
            name = "Pierna",
            color = "#EAB308",
            exercises = listOf(exercise("sent", "Sentadilla", 2), exercise("peso", "Peso muerto", 2)),
        )
        rule.setContent { Header(part) }

        rule.onNodeWithTag("session_group_name_field").assertTextEquals("Pierna")
        rule.onNodeWithText("2 ejercicios · 4 series").assertIsDisplayed()
    }

    @Test
    fun elNombreSeRenombraEnSitio() {
        var renamed: String? = null
        val part = SessionPart(id = "g", name = "Pierna", exercises = listOf(exercise("sent", "Sentadilla")))
        rule.setContent { Header(part, onRename = { renamed = it }) }

        rule.onNodeWithTag("session_group_name_field").performTextReplacement("Empuje")

        assertEquals("Empuje", renamed)
    }

    @Test
    fun elGrupoSePliegaConSuFlecha() {
        var toggled = 0
        val part = SessionPart(id = "g", name = "Pierna", exercises = listOf(exercise("sent", "Sentadilla")))
        rule.setContent { Header(part, onToggle = { toggled++ }) }

        rule.onNodeWithContentDescription("Plegar grupo").performClick()

        assertEquals(1, toggled)
    }

    /** ER2b: el umbral abre el diálogo directamente al soltar; por eso no se pulsa la papelera, que queda bajo el velo del diálogo. */
    @Test
    fun borrarDesdeLaCabeceraAbreSuDialogo() {
        val part = SessionPart(id = "g", name = "Pierna", exercises = listOf(exercise("sent", "Sentadilla")))
        rule.setContent { Header(part) }

        rule.onNode(
            SemanticsMatcher.expectValue(SemanticsProperties.StateDescription, "Desliza a la izquierda para eliminar"),
        ).performTouchInput { swipeLeft() }
        rule.waitForIdle()

        rule.onNodeWithText("¿Qué hacemos con los ejercicios?").assertExists()
        rule.onNodeWithText("Borrar grupo y ejercicios").assertExists()
    }

    /**
     * ER2b: deslizar abre el diálogo directamente, y cancelarlo devuelve la cabecera a su sitio con su nombre (no queda el fondo rojo
     * con la papelera ni el nombre tapado).
     */
    @Test
    fun cancelarElBorradoDevuelveLaCabeceraConSuNombre() {
        val part = SessionPart(id = "g", name = "Pierna", exercises = listOf(exercise("sent", "Sentadilla")))
        rule.setContent { Header(part) }
        val antes = rule.onNodeWithTag("session_group_name_field").fetchSemanticsNode().boundsInRoot.left

        rule.onNode(
            SemanticsMatcher.expectValue(SemanticsProperties.StateDescription, "Desliza a la izquierda para eliminar"),
        ).performTouchInput { swipeLeft() }
        rule.waitForIdle()
        rule.onNodeWithText("¿Qué hacemos con los ejercicios?").assertExists()
        rule.onNodeWithText("Cancelar").performClick()
        rule.waitForIdle()

        rule.onNodeWithText("¿Qué hacemos con los ejercicios?").assertDoesNotExist()
        val despues = rule.onNodeWithTag("session_group_name_field").fetchSemanticsNode().boundsInRoot.left
        assertTrue(abs(antes - despues) < 0.5f)
        rule.onNodeWithTag("session_group_name_field").assertTextEquals("Pierna")
    }

    @Test
    fun laSuperserieMuestraASusMiembrosPlegada() {
        val a = exercise("a", "Press inclinado")
        val b = exercise("b", "Aperturas")
        val c = exercise("c", "Cruces en polea")
        val members = listOf(a, b, c)
        val group = SupersetGroup(id = "ss", exerciseOrder = members.map { it.id }, rounds = 3)
        rule.setContent {
            SupersetGroupEditorCard(
                group = group,
                exercises = members,
                accentHex = null,
                partId = "p",
                onOpenSupersetCreator = { _, _ -> },
                onUpdateSupersetRest = { _, _, _, _ -> },
                onRemoveFromSuperset = { _, _ -> },
                onDissolve = {},
                onAddRound = noop,
            ) {
                Text("Press inclinado")
                Text("Aperturas")
                Text("Cruces en polea")
            }
        }

        rule.onNodeWithText("Press inclinado").assertIsDisplayed()
        rule.onNodeWithText("Aperturas").assertIsDisplayed()
        rule.onNodeWithText("Cruces en polea").assertIsDisplayed()
        // ED-A · C1: la cabecera lleva el rótulo «SUPERSERIE» en `mente` y sus fichas de rondas y descansos.
        rule.onNodeWithText("SUPERSERIE").assertIsDisplayed()
        rule.onNodeWithText("3 rondas").assertIsDisplayed()
    }

    @Test
    fun laFilaPlegadaTieneSuDosis() {
        val press = Exercise(
            id = "press",
            name = "Press banca",
            trainingMode = TrainingMode.RM,
            restTime = 195,
            sets = (1..4).map { ExerciseSet(id = "p$it", targetRepsRange = RepRange(4, 6), targetPercentageRM = 80.0, targetRIR = 2) },
        )
        rule.setContent { FoldedRow(press) }

        rule.onNodeWithText("4 × 4–6 · 80 % 1RM · RIR\u00A02 · 3:15").assertIsDisplayed()
    }

    @Test
    fun unaFilaUnilateralEscribePorLado() {
        val curl = Exercise(
            id = "curl",
            name = "Curl",
            trainingMode = TrainingMode.REPS,
            restTime = 90,
            isUnilateral = true,
            sets = (1..3).map { ExerciseSet(id = "c$it", targetRepsRange = RepRange(8, 10), targetRIR = 2) },
        )
        rule.setContent { FoldedRow(curl) }

        rule.onNodeWithText("3 × 8–10 · RIR\u00A02 · 1:30 · por\u00A0lado").assertIsDisplayed()
    }

    @Test
    fun elAvisoDeshacerDevuelveElEjercicio() {
        var undone = false
        rule.setContent { SessionExerciseUndoNotice(onUndo = { undone = true }) }

        rule.onNodeWithText("Ejercicio eliminado").assertIsDisplayed()
        rule.onNodeWithText("Deshacer").performClick()

        assertTrue(undone)
    }

    /** Ejercicio plegado con todos sus callbacks en no-op; lo que se prueba es lo que pinta. */
    @Composable
    private fun FoldedRow(exercise: Exercise) {
        ExerciseEditorCard(
            exercise = exercise,
            exerciseInfo = null,
            accentHex = null,
            partId = "p",
            isCompetitionMovement = false,
            isDragging = false,
            isDropTarget = false,
            isPartDropTarget = false,
            onBoundsChange = {},
            onDragStart = {},
            onDrag = {},
            onDragEnd = noop,
            onUpdateExercise = {},
            onDeleteExercise = noop,
            onAddSet = {},
            onUpdateSet = { _, _ -> },
            onRemoveSet = {},
            onMoveSet = { _, _ -> },
            onRemoveMobility = {},
            onOpenQuickActions = noop,
            relationshipAnchorName = null,
            onOpenRelationshipPicker = noop,
            onClearRelationship = noop,
            onUpdateRelationshipType = {},
            onUpdateRelationshipNotes = {},
            autoExpand = false,
            onAutoExpandHandled = noop,
        )
    }

    /** ER2b: el selector de superserie pinta la miniatura de la lista (el glifo), no la inicial del nombre. */
    @Test
    fun elSelectorDeSuperserieNoPintaLaInicialComoMiniatura() {
        rule.setContent {
            SupersetPickRow(exercise = exercise("zan", "Zancada"), checked = false, selectable = true, onToggle = noop)
        }

        rule.onNodeWithText("Zancada").assertIsDisplayed()
        rule.onNodeWithText("Z").assertDoesNotExist()
    }
}
