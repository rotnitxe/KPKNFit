package com.example.kpkn.screens.sessioneditor.components

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.semantics.SemanticsProperties
import androidx.compose.ui.test.assertCountEquals
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onAllNodesWithText
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.unit.dp
import com.example.kpkn.data.models.Exercise
import com.example.kpkn.data.models.ExerciseSet
import com.example.kpkn.data.models.RepRange
import com.example.kpkn.data.models.SupersetGroup
import com.example.kpkn.data.models.TrainingMode
import com.example.kpkn.screens.onboarding.design.LocalWizardReducedMotion
import com.example.kpkn.screens.sessioneditor.SessionEditorDropKind
import com.example.kpkn.screens.sessioneditor.SessionEditorDropPlan
import com.example.kpkn.screens.sessioneditor.design.SessionEditorDashedAddButton
import com.example.kpkn.screens.sessioneditor.design.SessionEditorDropHintBanner
import com.example.kpkn.screens.sessioneditor.design.SessionEditorOutsideDropZone
import com.example.kpkn.screens.sessioneditor.design.SessionExerciseUndoNotice
import com.example.kpkn.screens.sessioneditor.design.SessionModuleEdge
import com.example.kpkn.screens.sessioneditor.design.SessionModuleSlot
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

/**
 * ED-A · C1: la superserie enmarcada DENTRO del módulo de su grupo, con sus miembros A1, A2 y su resumen.
 */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [34], application = android.app.Application::class, qualifiers = "w360dp-h900dp-xxhdpi")
class SessionEditorSupersetFrameComposeTest {

    @get:Rule
    val rule = createComposeRule()

    private val noop: () -> Unit = {}

    // ───────────────────────────── C1 · la superserie dentro de su grupo ─────────────────────────────

    private fun member(id: String, name: String, sets: Int = 3) = Exercise(
        id = id,
        name = name,
        trainingMode = TrainingMode.REPS,
        supersetGroupRef = "ss",
        sets = (1..sets).map { ExerciseSet(id = "$id-$it", targetReps = 10, targetRepsRange = RepRange(8, 10), targetRIR = 2) },
    )

    @Composable
    private fun SupersetInGroup(members: List<Exercise>) {
        SessionModuleSlot(edge = SessionModuleEdge.MIDDLE) {
            SupersetGroupEditorCard(
                group = SupersetGroup(id = "ss", exerciseOrder = members.map { it.id }, rounds = 3, restBetweenExercises = 60, restAfterSuperset = 90),
                exercises = members,
                accentHex = null,
                partId = "acc",
                onOpenSupersetCreator = { _, _ -> },
                onUpdateSupersetRest = { _, _, _, _ -> },
                onRemoveFromSuperset = { _, _ -> },
                onDissolve = {},
                onAddRound = noop,
            ) {
                members.forEachIndexed { index, exercise ->
                    ExerciseEditorCard(
                        exercise = exercise,
                        exerciseInfo = null,
                        accentHex = null,
                        partId = "acc",
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
                        suppressIndividualRest = true,
                        memberBadge = "A${index + 1}",
                    )
                }
            }
        }
    }

    @Test
    fun laSuperserieSeMuestraEnmarcadaDentroDeSuGrupoConSusMiembrosA1YA2() {
        rule.setContent { SupersetInGroup(listOf(member("zan", "Zancada con mancuernas"), member("curl", "Curl femoral tumbado"))) }

        rule.onNodeWithTag("session_superset_frame").assertIsDisplayed()
        rule.onNodeWithText("SUPERSERIE").assertIsDisplayed()
        rule.onNodeWithText("2 ejercicios").assertIsDisplayed()
        rule.onNodeWithText("3 rondas").assertIsDisplayed()
        rule.onNodeWithText("1:00 entre ejercicios").assertIsDisplayed()
        rule.onNodeWithText("1:30 tras la ronda").assertIsDisplayed()
        rule.onNodeWithText("A1").assertIsDisplayed()
        rule.onNodeWithText("A2").assertIsDisplayed()
        rule.onNodeWithText("Zancada con mancuernas").assertIsDisplayed()
        rule.onNodeWithText("Curl femoral tumbado").assertIsDisplayed()
        rule.onAllNodesWithText("A3").assertCountEquals(0)
    }

    @Test
    fun elMarcoDeLaSuperserieQuedaDentroDelMargenDelModulo() {
        var density = 1f
        rule.setContent {
            density = LocalDensity.current.density
            Box(Modifier.testTag("modulo")) {
                SupersetInGroup(listOf(member("zan", "Zancada"), member("curl", "Curl")))
            }
        }

        val module = rule.onNodeWithTag("modulo").fetchSemanticsNode().boundsInRoot
        val frame = rule.onNodeWithTag("session_superset_frame").fetchSemanticsNode().boundsInRoot
        // El módulo del grupo deja 16 dp de margen a la pantalla y el marco otros 8 dp dentro: la superserie está DENTRO del grupo, no aparte.
        assertEquals(24f * density, frame.left - module.left, 1f)
        assertEquals(24f * density, module.right - frame.right, 1f)
        assertTrue(frame.top >= module.top && frame.bottom <= module.bottom)
    }

    @Test
    fun laSuperserieDeDestinoDelArrastreCambiaSoloElBorde() {
        var target by mutableStateOf(false)
        rule.setContent {
            val members = listOf(member("zan", "Zancada"), member("curl", "Curl"))
            SessionModuleSlot(edge = SessionModuleEdge.MIDDLE) {
                SupersetGroupEditorCard(
                    group = SupersetGroup(id = "ss", exerciseOrder = members.map { it.id }, rounds = 3),
                    exercises = members,
                    accentHex = null,
                    partId = "acc",
                    isDropTarget = target,
                    onOpenSupersetCreator = { _, _ -> },
                    onUpdateSupersetRest = { _, _, _, _ -> },
                    onRemoveFromSuperset = { _, _ -> },
                    onDissolve = {},
                    onAddRound = noop,
                ) {
                    Text("Zancada")
                    Text("Curl")
                }
            }
        }

        val before = rule.onNodeWithTag("session_superset_frame").fetchSemanticsNode().boundsInRoot
        rule.runOnIdle { target = true }
        rule.waitForIdle()
        assertEquals(before, rule.onNodeWithTag("session_superset_frame").fetchSemanticsNode().boundsInRoot)
    }
}
