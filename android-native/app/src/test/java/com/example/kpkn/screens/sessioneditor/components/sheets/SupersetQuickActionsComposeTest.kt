package com.example.kpkn.screens.sessioneditor.components.sheets

import androidx.compose.foundation.layout.Column
import androidx.compose.runtime.mutableStateOf
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import com.example.kpkn.data.models.Exercise
import com.example.kpkn.data.models.ExerciseSet
import com.example.kpkn.data.models.RepRange
import com.example.kpkn.data.models.SupersetGroup
import com.example.kpkn.screens.sessioneditor.SupersetDraft
import com.example.kpkn.screens.sessioneditor.components.SupersetRoundsCarousel
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

/**
 * ER1c2: la lista de acciones rápidas, la selección por casillas de la superserie y el editor de rondas en lista.
 * Las filas de acciones van dentro de una columna, como en la hoja: en la raíz de la composición se apilarían unas sobre otras.
 */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [34], application = android.app.Application::class, qualifiers = "w360dp-h800dp-xxhdpi")
class SupersetQuickActionsComposeTest {

    @get:Rule
    val rule = createComposeRule()

    private fun exercise(id: String, name: String, sets: Int = 2) = Exercise(
        id = id,
        name = name,
        sets = List(sets) { index -> ExerciseSet(id = "$id-s$index", targetRepsRange = RepRange(8, 10)) },
    )

    private val members = listOf(
        exercise("press", "Press banca"),
        exercise("remo", "Remo con barra"),
        exercise("curl", "Curl bíceps"),
        exercise("fondos", "Fondos"),
        exercise("sentadilla", "Sentadilla"),
    )

    @Test
    fun laListaDeAccionesPintaSieteYCadaUnaLlamaALoSuyo() {
        val calls = mutableListOf<String>()
        rule.setContent {
            Column {
                ExerciseQuickActionRows(
                    isInSuperset = false,
                    onOpenExerciseDetail = { calls += "ficha" },
                    onOpenPicker = { calls += "cambiar" },
                    onOpenWarmup = { calls += "aproximacion" },
                    onOpenMobility = { calls += "movilidad" },
                    onManageSuperset = { calls += "superserie" },
                    onDuplicate = { calls += "duplicar" },
                    onRequestDelete = { calls += "eliminar" },
                )
            }
        }
        rule.onNodeWithText("Crear superserie").assertExists()
        rule.onNodeWithText("Duplicar ejercicio").assertExists()
        rule.onNodeWithText("Eliminar").assertExists()

        val tags = listOf(
            "quick-action-ficha",
            "quick-action-cambiar",
            "quick-action-aproximacion",
            "quick-action-movilidad",
            "quick-action-superserie",
            "quick-action-duplicar",
            "quick-action-eliminar",
        )
        val expected = listOf("ficha", "cambiar", "aproximacion", "movilidad", "superserie", "duplicar", "eliminar")
        tags.zip(expected).forEach { (tag, name) ->
            rule.onNodeWithTag(tag).performClick()
            assertEquals(name, calls.last())
        }
        assertEquals(7, calls.size)
    }

    @Test
    fun siElEjercicioYaEsMiembroLaAccionEsGestionarNoCrear() {
        rule.setContent {
            Column {
                ExerciseQuickActionRows(
                    isInSuperset = true,
                    onOpenExerciseDetail = {},
                    onOpenPicker = {},
                    onOpenWarmup = {},
                    onOpenMobility = {},
                    onManageSuperset = {},
                    onDuplicate = {},
                    onRequestDelete = {},
                )
            }
        }
        rule.onNodeWithText("Gestionar superserie").assertExists()
        rule.onNodeWithText("Crear superserie").assertDoesNotExist()
    }

    @Test
    fun conTresMarcadosLaSuperserieSeCreaConTres() {
        val draft = mutableStateOf(SupersetDraft(exerciseIds = listOf("press")))
        var confirmed = 0
        rule.setContent {
            SupersetMemberPickerSheet(
                draft = draft.value,
                sessionExercises = members,
                onUpdateDraft = { draft.value = it },
                onConfirm = { confirmed += 1 },
                onOpenCatalog = {},
                onDismiss = {},
            )
        }
        rule.onNodeWithText("Remo con barra").performClick()
        rule.onNodeWithText("Curl bíceps").performClick()
        assertEquals(listOf("press", "remo", "curl"), draft.value.exerciseIds)
        rule.onNodeWithText("Crear superserie con 3").assertExists()
        rule.onNodeWithTag("superset-picker-confirm").performClick()
        assertEquals(1, confirmed)
    }

    @Test
    fun conCuatroMarcadosElQuintoNoSeMarca() {
        val draft = mutableStateOf(SupersetDraft(exerciseIds = listOf("press")))
        rule.setContent {
            SupersetMemberPickerSheet(
                draft = draft.value,
                sessionExercises = members,
                onUpdateDraft = { draft.value = it },
                onConfirm = {},
                onOpenCatalog = {},
                onDismiss = {},
            )
        }
        rule.onNodeWithText("Remo con barra").performClick()
        rule.onNodeWithText("Curl bíceps").performClick()
        rule.onNodeWithText("Fondos").performClick()
        rule.onNodeWithText("Máximo 4").assertExists()
        rule.onNodeWithText("Sentadilla").performClick()
        assertEquals(4, draft.value.exerciseIds.size)
        assertTrue("sentadilla" !in draft.value.exerciseIds)
        rule.onNodeWithText("Crear superserie con 4").assertExists()
    }

    @Test
    fun conUnoSoloElBotonDeCrearNoHaceNada() {
        val draft = mutableStateOf(SupersetDraft(exerciseIds = listOf("press")))
        var confirmed = 0
        rule.setContent {
            SupersetMemberPickerSheet(
                draft = draft.value,
                sessionExercises = members,
                onUpdateDraft = { draft.value = it },
                onConfirm = { confirmed += 1 },
                onOpenCatalog = {},
                onDismiss = {},
            )
        }
        rule.onNodeWithTag("superset-picker-confirm").performClick()
        assertEquals(0, confirmed)
        assertEquals(listOf("press"), draft.value.exerciseIds)
    }

    @Test
    fun lasRondasEnListaSeAbrenYCierranYAnadenRonda() {
        val group = SupersetGroup(
            id = "grupo",
            exerciseOrder = listOf("press", "remo"),
            restBetweenExercises = 60,
            restAfterSuperset = 120,
            rounds = 2,
        )
        val pair = listOf(exercise("press", "Press banca"), exercise("remo", "Remo con barra"))
        var added = 0
        rule.setContent {
            SupersetRoundsCarousel(
                group = group,
                exercises = pair,
                rounds = 2,
                accentColor = Color.White,
                onUpdateRoundRest = { _, _, _ -> },
                onUpdateSet = { _, _, _ -> },
                onRemoveSet = { _, _ -> },
                onMoveSet = { _, _, _ -> },
                onRestoreSet = { _, _ -> },
                onAddRound = { added += 1 },
                onRemoveRound = {},
            )
        }
        rule.onNodeWithText("Entre 1:00 · Ronda 2:00").assertDoesNotExist()
        rule.onNodeWithText("Ronda 1").performClick()
        rule.waitForIdle()
        rule.onNodeWithText("Entre 1:00 · Ronda 2:00").assertExists()
        rule.onNodeWithText("Ronda 1").performClick()
        rule.waitForIdle()
        rule.onNodeWithText("Entre 1:00 · Ronda 2:00").assertDoesNotExist()
        rule.onNodeWithText("Añadir ronda").performClick()
        assertEquals(1, added)
    }
}
