package com.example.kpkn.screens.sessioneditor.components

import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNodeWithContentDescription
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import com.example.kpkn.data.models.Exercise
import com.example.kpkn.data.models.ExerciseSet
import com.example.kpkn.data.models.TrainingMode
import com.example.kpkn.data.models.isEffectivelyUnilateral
import com.example.kpkn.screens.sessioneditor.toggledBilateralUnilateral
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import org.robolectric.annotation.GraphicsMode

/** ER1c1, R1 §4.2 más los tableros B1/B5/B8: la barra de opciones, la lateralidad en ⋯ con su hoja, la hoja de los 7 modos y el módulo «Lados». */
@RunWith(RobolectricTestRunner::class)
@GraphicsMode(GraphicsMode.Mode.NATIVE)
@Config(sdk = [34], application = android.app.Application::class, qualifiers = "w360dp-h880dp-mdpi")
class ExerciseOptionsBarComposeTest {

    @get:Rule
    val rule = createComposeRule()

    private fun setEditorContent(content: @androidx.compose.runtime.Composable () -> Unit) {
        rule.setContent { EditorOverlayTestHost(content) }
    }

    private val press = Exercise(
        id = "press",
        name = "Press",
        restTime = 90,
        sets = listOf(ExerciseSet(id = "s1", targetReps = 8)),
    )

    @Test
    fun laHojaDeModosTieneLosSieteModosYElegirUnoLoDevuelve() {
        var picked: TrainingMode? = null
        setEditorContent {
            ExerciseModeSheetBody(current = TrainingMode.REPS, onSelect = { picked = it })
        }
        listOf("Reps", "AMRAP", "RM", "Solo RPE", "Tiempo", "Distancia", "Personalizado").forEach { label ->
            rule.onNodeWithText(label).assertExists()
        }
        rule.onNodeWithText("Tiempo").performClick()
        assertEquals(TrainingMode.TIME, picked)
    }

    /** B1/B8: la barra lleva Modo y Descanso; la lateralidad ya no es un alternador fijo, vive en el menú ⋯ y abre su hoja. */
    @Test
    fun laBarraMuestraModoYDescansoYLaLateralidadViveEnElMenu() {
        setEditorContent {
            val local = rememberExerciseEditorLocalState(press)
            ExerciseOptionsBar(
                exercise = press,
                local = local,
                accentColor = Color(0xFFF49A6E),
                isSupersetExercise = false,
                suppressIndividualRest = false,
                relationshipAnchorName = null,
                onUpdateExercise = {},
                onOpenSuperset = {},
                onOpenRelationshipPicker = {},
                onClearRelationship = {},
                onOpenCustomUnit = {},
                onOpenSmartLoad = {},
                onOpenGoal = {},
                onOpenRest = {},
            )
        }
        rule.onNodeWithText("Modo: Reps ▾").assertExists()
        rule.onNodeWithText("Descanso 1:30").assertExists()
        rule.onNodeWithContentDescription("Más opciones del ejercicio").performClick()
        rule.mainClock.advanceTimeBy(3_000)
        rule.waitForIdle()
        rule.onNodeWithText("Lateralidad: Bilateral").assertExists().performClick()
        rule.mainClock.advanceTimeBy(3_000)
        rule.waitForIdle()
        rule.onNodeWithText("Bilateral").assertExists()
        rule.onNodeWithText("Unilateral").assertExists()
    }

    /** B1/B8: cambiar a unilateral desde la hoja hace lo mismo que `toggledBilateralUnilateral`. */
    @Test
    fun laHojaDeLateralidadHaceLoMismoQueToggledBilateralUnilateral() {
        var result: Exercise? = null
        setEditorContent {
            val local = rememberExerciseEditorLocalState(press)
            ExerciseOptionsBar(
                exercise = press,
                local = local,
                accentColor = Color(0xFFF49A6E),
                isSupersetExercise = false,
                suppressIndividualRest = false,
                relationshipAnchorName = null,
                onUpdateExercise = { transform -> result = transform(press) },
                onOpenSuperset = {},
                onOpenRelationshipPicker = {},
                onClearRelationship = {},
                onOpenCustomUnit = {},
                onOpenSmartLoad = {},
                onOpenGoal = {},
                onOpenRest = {},
            )
        }
        rule.onNodeWithContentDescription("Más opciones del ejercicio").performClick()
        rule.mainClock.advanceTimeBy(3_000)
        rule.waitForIdle()
        rule.onNodeWithText("Lateralidad: Bilateral").performClick()
        rule.waitForIdle()
        rule.mainClock.advanceTimeBy(3_000)
        rule.waitForIdle()
        rule.onNodeWithText("Unilateral").assertIsDisplayed().performClick()
        rule.waitForIdle()
        val expected = press.toggledBilateralUnilateral()
        assertEquals(expected, result)
        assertTrue(result!!.isEffectivelyUnilateral())
    }

    @Test
    fun elMenuDeOpcionesReuneRomRelacionSuperserieUnidadCargaYMeta() {
        setEditorContent {
            val local = rememberExerciseEditorLocalState(press)
            ExerciseOptionsBar(
                exercise = press,
                local = local,
                accentColor = Color(0xFFF49A6E),
                isSupersetExercise = false,
                suppressIndividualRest = false,
                relationshipAnchorName = null,
                onUpdateExercise = {},
                onOpenSuperset = {},
                onOpenRelationshipPicker = {},
                onClearRelationship = {},
                onOpenCustomUnit = {},
                onOpenSmartLoad = {},
                onOpenGoal = {},
                onOpenRest = {},
            )
        }
        rule.onNodeWithContentDescription("Más opciones del ejercicio").performClick()
        listOf("Medir ROM", "Relacionar ejercicio", "Superserie", "Unidad personalizada", "Carga inteligente", "Meta").forEach { label ->
            rule.onNodeWithText(label).assertExists()
        }
    }

    @Test
    fun elModuloLadosTieneOrdenLadosYDescansoEntreLados() {
        val unilateral = press.toggledBilateralUnilateral()
        var orderToggled: Exercise? = null
        setEditorContent {
            SideOptionsModule(
                exercise = unilateral,
                onUpdateExercise = { transform -> orderToggled = transform(unilateral) },
                onOpenSideRest = {},
            )
        }
        // B5: el orden de lados se dice en L/R, nunca I/D.
        rule.onNodeWithText("Orden L/R").assertExists()
        rule.onNodeWithText("Lados iguales").assertExists()
        rule.onNodeWithText("Entre lados 0:15").assertExists()
        rule.onNodeWithText("Orden L/R").performClick()
        assertEquals(
            com.example.kpkn.data.models.UnilateralSideOrder.RIGHT_LEFT,
            orderToggled!!.unilateralSideOrder,
        )
    }
}
