package com.example.kpkn.screens.sessioneditor.components

import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Text
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.test.assertCountEquals
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.assertIsNotEnabled
import androidx.compose.ui.test.hasAnyAncestor
import androidx.compose.ui.test.hasContentDescription
import androidx.compose.ui.test.hasTestTag
import androidx.compose.ui.test.hasText
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNodeWithContentDescription
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.onRoot
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performScrollTo
import androidx.compose.ui.test.performSemanticsAction
import androidx.compose.ui.test.printToString
import com.example.kpkn.data.models.Exercise
import com.example.kpkn.data.models.ExerciseSet
import com.example.kpkn.data.models.SupersetGroup
import com.example.kpkn.data.models.TrainingMode
import com.example.kpkn.data.models.UnilateralIntensityMode
import com.example.kpkn.data.models.UnilateralMode
import com.example.kpkn.data.models.UnilateralSideOrder
import com.example.kpkn.data.models.UnilateralTarget
import com.example.kpkn.screens.sessioneditor.withSetDuplicated
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import org.robolectric.annotation.GraphicsMode

/**
 * Remediación 11: cobertura real del menú de series en ambos editores (normal y rondas de superserie), incluyendo REPS/RM/unilateral,
 * primera/última/única serie, eliminar+deshacer, duplicar, aplicar a todas y reordenación de miembros.
 */
@RunWith(RobolectricTestRunner::class)
@GraphicsMode(GraphicsMode.Mode.NATIVE)
@Config(sdk = [34], application = android.app.Application::class, qualifiers = "w360dp-h900dp-xxhdpi")
class SupersetSetActionsComposeTest {

    @get:Rule
    val rule = createComposeRule()

    private fun bilateralExercise(
        id: String = "press",
        name: String = "Press inclinado",
        mode: TrainingMode = TrainingMode.REPS,
        sets: List<ExerciseSet> = listOf(
            ExerciseSet(id = "s1", weight = 80.0, targetReps = 6),
            ExerciseSet(id = "s2", weight = 60.0, targetReps = 10),
        ),
    ) = Exercise(
        id = id,
        name = name,
        trainingMode = mode,
        supersetGroupRef = "ss",
        sets = sets,
    )

    private fun mountSuperset(
        initialPrimary: Exercise,
        companion: Exercise = Exercise(
            id = "remo",
            name = "Remo con barra",
            supersetGroupRef = "ss",
            trainingMode = TrainingMode.REPS,
            sets = listOf(ExerciseSet(id = "r1", targetReps = 10), ExerciseSet(id = "r2", targetReps = 10)),
        ),
        onReordered: (List<String>) -> Unit = {},
    ): MutableExerciseState {
        val state = MutableExerciseState(listOf(initialPrimary, companion))
        rule.setContent {
            EditorOverlayTestHost {
                Column(Modifier.fillMaxWidth().verticalScroll(rememberScrollState())) {
                    val exercises = state.exercises
                    SupersetGroupEditorCard(
                        group = SupersetGroup(
                            id = "ss",
                            exerciseOrder = exercises.map { it.id },
                            rounds = exercises.maxOfOrNull { it.sets.size } ?: 1,
                            restBetweenExercises = 60,
                            restAfterSuperset = 120,
                        ),
                        exercises = exercises,
                        accentHex = null,
                        partId = "main",
                        onOpenSupersetCreator = { _, _ -> },
                        onUpdateSupersetRest = { _, _, _, _ -> },
                        onUpdateExercise = { exId, transform ->
                            state.exercises = state.exercises.map { if (it.id == exId) transform(it) else it }
                        },
                        onUpdateSet = { exId, setId, transform ->
                            state.exercises = state.exercises.map { ex ->
                                if (ex.id != exId) ex
                                else ex.copy(sets = ex.sets.map { s -> if (s.id == setId) transform(s) else s })
                            }
                        },
                        onRemoveSet = { exId, setId ->
                            state.exercises = state.exercises.map { ex ->
                                if (ex.id != exId) ex
                                else {
                                    val activeCount = ex.sets.count { !it.isEmptySlot }
                                    if (activeCount <= 1) ex
                                    else ex.copy(sets = ex.sets.map { s -> if (s.id == setId) ExerciseSet(id = s.id, isEmptySlot = true) else s })
                                }
                            }
                        },
                        onRemoveFromSuperset = { _, _ -> },
                        onDissolve = {},
                        onAddRound = {},
                        onReorderMembers = { ids ->
                            onReordered(ids)
                            state.exercises = ids.mapNotNull { id -> state.exercises.firstOrNull { it.id == id } }
                        },
                    ) {
                        exercises.forEach { member -> Text(member.name) }
                    }
                }
            }
        }
        return state
    }

    private class MutableExerciseState(initial: List<Exercise>) {
        var exercises by mutableStateOf(initial)
    }

    @Test
    fun superserieModoRepsExponeAplicarDuplicarYEliminarConDeshacerYReglaUltimaSerie() {
        val state = mountSuperset(bilateralExercise())

        rule.onNodeWithText("2 rondas").performClick()
        rule.onNodeWithText("Ronda 1").performScrollTo().performClick()
        rule.onAllNodes(hasContentDescription("Ampliar serie"))[0].performScrollTo().performClick()
        rule.waitForIdle()

        val focus1 = hasAnyAncestor(hasTestTag("round-set-focus-s1"))
        rule.onNode(hasText("Subir") and focus1).assertDoesNotExist()
        rule.onNode(hasText("Bajar") and focus1).assertDoesNotExist()
        rule.onNode(hasText("Aplicar a todas") and focus1).assertExists()
        rule.onNode(hasText("Duplicar") and focus1).assertExists()

        // Aplicar a todas copia la dosis/peso a S2 previa confirmación.
        rule.onNode(hasText("Aplicar a todas") and focus1).performScrollTo().performClick()
        rule.mainClock.advanceTimeBy(3_000)
        rule.waitForIdle()
        rule.onNodeWithText("Aplicar").assertIsDisplayed().performClick()
        // La hoja baja (240 ms) y solo entonces se aplica (`closeLayer(cb)` del lienzo).
        rule.mainClock.advanceTimeBy(1_000)
        rule.waitForIdle()
        assertEquals(6, state.exercises.first().sets[1].targetReps)
        assertEquals(80.0, state.exercises.first().sets[1].weight!!, 0.001)

        // Duplicar añade una tercera ronda sin desplazar S1 ni S2.
        rule.onNode(hasText("Duplicar") and focus1).performScrollTo().performClick()
        rule.waitForIdle()
        assertEquals(3, state.exercises.first().sets.size)

        // Eliminar S1 deja hueco vacío + aviso Deshacer y restaura al pulsar Deshacer.
        rule.onNode(hasContentDescription("Más acciones de la serie") and focus1).performScrollTo().performClick()
        rule.onNodeWithText("Eliminar serie").performClick()
        rule.waitForIdle()
        assertTrue(state.exercises.first().sets[0].isEmptySlot)
        rule.onNodeWithText("Serie eliminada").performScrollTo().assertExists()
        rule.onNodeWithContentDescription("Deshacer").performScrollTo().performClick()
        rule.waitForIdle()
        assertFalse(state.exercises.first().sets[0].isEmptySlot)
    }

    @Test
    fun superserieConUnaSolaSerieActivaYSinHuecoBloqueaEliminarSerie() {
        val singleActiveWithHole = bilateralExercise(
            sets = listOf(
                ExerciseSet(id = "s1", isEmptySlot = true),
                ExerciseSet(id = "s2", weight = 70.0, targetReps = 8),
            ),
        )
        mountSuperset(singleActiveWithHole)

        rule.onNodeWithText("2 rondas").performClick()
        rule.onNodeWithText("Ronda 2").performScrollTo().performClick()
        rule.onAllNodes(hasContentDescription("Ampliar serie"))[0].performScrollTo().performClick()
        rule.waitForIdle()

        val focus2 = hasAnyAncestor(hasTestTag("round-set-focus-s2"))
        // Con una sola serie activa no se muestra Aplicar a todas y Eliminar serie está desactivado.
        rule.onNode(hasText("Aplicar a todas") and focus2).assertDoesNotExist()
        rule.onNode(hasText("Duplicar") and focus2).assertExists()
        rule.onNode(hasContentDescription("Más acciones de la serie") and focus2).performScrollTo().performClick()
        rule.onNodeWithText("Eliminar serie").assertIsNotEnabled()
        rule.onNodeWithText("Siempre queda una serie").assertExists()
    }

    @Test
    fun superserieUnilateralModoRepsMuestraUnaSolaBarraYAplicaAmbosLados() {
        val unilateral = bilateralExercise(
            sets = listOf(
                ExerciseSet(
                    id = "s1",
                    leftTarget = UnilateralTarget(weight = 22.5, targetReps = 10),
                    rightTarget = UnilateralTarget(weight = 25.0, targetReps = 10),
                ),
                ExerciseSet(
                    id = "s2",
                    leftTarget = UnilateralTarget(weight = 15.0, targetReps = 12),
                    rightTarget = UnilateralTarget(weight = 17.5, targetReps = 12),
                ),
            ),
        ).copy(
            isUnilateral = true,
            unilateralMode = UnilateralMode.UNILATERAL_PAIRED,
            unilateralIntensityMode = UnilateralIntensityMode.INDEPENDENT,
            unilateralSideOrder = UnilateralSideOrder.RIGHT_LEFT,
        )
        val state = mountSuperset(unilateral)

        rule.onNodeWithText("2 rondas").performClick()
        rule.onNodeWithText("Ronda 1").performScrollTo().performClick()
        rule.onAllNodes(hasContentDescription("Ampliar serie"))[0].performScrollTo().performClick()
        rule.waitForIdle()

        val eitherSide = hasAnyAncestor(hasTestTag("round-set-focus-s1:R")) or hasAnyAncestor(hasTestTag("round-set-focus-s1:L"))
        rule.onAllNodes(hasContentDescription("Más acciones de la serie") and eitherSide).assertCountEquals(1)
        rule.onNode(hasText("Aplicar a todas") and hasAnyAncestor(hasTestTag("round-set-focus-s1:R"))).performScrollTo().performClick()
        rule.mainClock.advanceTimeBy(3_000)
        rule.waitForIdle()
        rule.onNodeWithText("Aplicar").assertIsDisplayed().performClick()
        // La hoja baja (240 ms) y solo entonces se aplica (`closeLayer(cb)` del lienzo).
        rule.mainClock.advanceTimeBy(1_000)
        rule.waitForIdle()

        val s2 = state.exercises.first().sets[1]
        assertEquals(22.5, s2.leftTarget!!.weight!!, 0.001)
        assertEquals(25.0, s2.rightTarget!!.weight!!, 0.001)
    }

    @Test
    fun reordenacionDeMiembrosEnSuperserieFunciona() {
        val reorderedCalls = mutableListOf<List<String>>()
        mountSuperset(bilateralExercise(), onReordered = { reorderedCalls += it })

        rule.onNodeWithText("2 rondas").performClick()
        rule.onNodeWithContentDescription("Bajar en el orden").performScrollTo().performClick()
        rule.waitForIdle()
        assertEquals(listOf(listOf("remo", "press")), reorderedCalls)
    }

    @Test
    fun navegacionEntreSeriesYGuardaDeHuecoEnEditorNormalFuncionan() {
        val normal = Exercise(
            id = "e1",
            name = "Sentadilla",
            sets = listOf(
                ExerciseSet(id = "s1", targetReps = 5),
                ExerciseSet(id = "s2", targetReps = 8),
                ExerciseSet(id = "s3", isEmptySlot = true),
            ),
        )
        var current by mutableStateOf(normal)
        rule.setContent {
            ExerciseSetList(
                exercise = current,
                reference1RM = null,
                trainingMode = TrainingMode.REPS,
                customUnit = null,
                predictedMetrics = emptyMap(),
                accentColor = androidx.compose.ui.graphics.Color.White,
                backgroundAccentColor = androidx.compose.ui.graphics.Color.White,
                onAddSet = {},
                onUpdateSet = { _, _ -> },
                onRemoveSet = {},
                onMoveSet = { _, _ -> },
                onTransformExercise = { transform -> current = transform(current) },
            )
        }
        rule.onNodeWithText("Serie 1 de 3").assertExists()
        rule.onNodeWithContentDescription("Serie siguiente").performClick()
        rule.onNodeWithText("Serie 2 de 3").assertExists()
        rule.onNodeWithContentDescription("Serie anterior").performClick()
        rule.onNodeWithText("Serie 1 de 3").assertExists()

        // El duplicado conserva el helper puro en normal y superserie.
        assertEquals(4, normal.withSetDuplicated("s1") { "s4" }.sets.size)
    }
}
