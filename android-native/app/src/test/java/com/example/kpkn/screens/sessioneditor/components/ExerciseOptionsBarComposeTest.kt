package com.example.kpkn.screens.sessioneditor.components

import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.ui.graphics.Color
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

/** ER1c1, R1 §4.2: la barra de opciones del ejercicio, la hoja de los 7 modos y el módulo «Lados». */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [34], application = android.app.Application::class, qualifiers = "w360dp-h2000dp-xxhdpi")
class ExerciseOptionsBarComposeTest {

    @get:Rule
    val rule = createComposeRule()

    private val press = Exercise(
        id = "press",
        name = "Press",
        restTime = 90,
        sets = listOf(ExerciseSet(id = "s1", targetReps = 8)),
    )

    @Test
    fun laHojaDeModosTieneLosSieteModosYElegirUnoLoDevuelve() {
        var picked: TrainingMode? = null
        rule.setContent {
            ExerciseModeSheetBody(current = TrainingMode.REPS, onSelect = { picked = it })
        }
        listOf("Reps", "AMRAP", "RM", "Solo RPE", "Tiempo", "Distancia", "Personalizado").forEach { label ->
            rule.onNodeWithText(label).assertExists()
        }
        rule.onNodeWithText("Tiempo").performClick()
        assertEquals(TrainingMode.TIME, picked)
    }

    @Test
    fun laBarraMuestraModoDescansoYElAlternadorBilateralUnilateral() {
        rule.setContent {
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
        rule.onNodeWithText("Bilateral").assertExists()
        rule.onNodeWithText("Unilateral").assertExists()
    }

    @Test
    fun elAlternadorUnilateralHaceLoMismoQueToggledBilateralUnilateral() {
        var result: Exercise? = null
        rule.setContent {
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
        rule.onNodeWithText("Unilateral").performClick()
        val expected = press.toggledBilateralUnilateral()
        assertEquals(expected, result)
        assertTrue(result!!.isEffectivelyUnilateral())
    }

    @Test
    fun elMenuDeOpcionesReuneRomRelacionSuperserieUnidadCargaYMeta() {
        rule.setContent {
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
        rule.setContent {
            SideOptionsModule(
                exercise = unilateral,
                onUpdateExercise = { transform -> orderToggled = transform(unilateral) },
                onOpenSideRest = {},
            )
        }
        rule.onNodeWithText("Orden I/D").assertExists()
        rule.onNodeWithText("Lados iguales").assertExists()
        rule.onNodeWithText("Entre lados 0:15").assertExists()
        rule.onNodeWithText("Orden I/D").performClick()
        assertEquals(
            com.example.kpkn.data.models.UnilateralSideOrder.RIGHT_LEFT,
            orderToggled!!.unilateralSideOrder,
        )
    }
}
