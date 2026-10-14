package com.example.kpkn.screens.workout

import android.app.Application
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.SemanticsProperties
import androidx.compose.ui.test.SemanticsMatcher
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import com.example.kpkn.data.models.TrainingMode
import com.example.kpkn.data.models.UnilateralSideOrder
import com.example.kpkn.screens.workout.design.WorkoutSetSettingsBody
import com.example.kpkn.screens.workout.design.WorkoutSupersetCandidate
import com.example.kpkn.screens.workout.design.WorkoutSupersetCreatorBody
import com.example.kpkn.screens.workout.design.WorkoutTagSheetBody
import org.junit.Assert.assertEquals
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

/**
 * Cuerpos de las hojas de estructura de la sesión en vivo (LS7a) montados sueltos: sin la hoja, porque Robolectric no entrega toques
 * dentro de `KpknSheet`. Cada prueba toca un control y comprueba lo que sale por su lambda.
 */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [34], qualifiers = "w360dp-h1800dp-xxhdpi", application = Application::class)
class WorkoutStructureSheetsTest {

    @get:Rule
    val composeRule = createComposeRule()

    @Test
    fun marcar_una_etiqueta_llama_con_su_id() {
        val recorded = mutableListOf<String>()
        composeRule.setContent {
            MaterialTheme {
                WorkoutTagSheetBody(
                    currentTag = null,
                    userTags = listOf("Empuje", "Tirón"),
                    suggestedTag = null,
                    profiles = emptyList(),
                    onTagSet = { recorded += it },
                    onDismiss = {},
                )
            }
        }

        composeRule.onNodeWithText("Tirón").performClick()

        assertEquals(listOf("Tirón"), recorded)
    }

    @Test
    fun el_interruptor_de_unilateral_llama_con_el_valor() {
        val recorded = mutableListOf<Boolean>()
        composeRule.setContent {
            MaterialTheme {
                WorkoutSetSettingsBody(
                    trainingMode = TrainingMode.REPS,
                    onTrainingModeChange = {},
                    unilateral = false,
                    onUnilateralChange = { recorded += it },
                    sideOrder = UnilateralSideOrder.LEFT_RIGHT,
                    onSideOrderChange = {},
                    restBetweenSidesSeconds = 0,
                    onRestBetweenSidesChange = {},
                    smartLoadSummary = null,
                    onOpenSmartLoad = {},
                )
            }
        }

        composeRule.onNode(SemanticsMatcher.expectValue(SemanticsProperties.Role, Role.Switch)).performClick()

        assertEquals(listOf(true), recorded)
    }

    @Test
    fun crear_una_superserie_con_dos_miembros_llama_con_ellos() {
        var selected by mutableStateOf(listOf("a"))
        val recorded = mutableListOf<Pair<List<String>, Int>>()
        composeRule.setContent {
            MaterialTheme {
                WorkoutSupersetCreatorBody(
                    candidates = listOf(
                        WorkoutSupersetCandidate(id = "a", name = "Remo"),
                        WorkoutSupersetCandidate(id = "b", name = "Curl"),
                        WorkoutSupersetCandidate(id = "c", name = "Press"),
                    ),
                    anchorId = "a",
                    selectedIds = selected,
                    onSelectedIdsChange = { selected = it },
                    defaultRounds = 3,
                    defaultRestBetween = 60,
                    defaultRestAfter = 120,
                    confirmLabel = "Crear superserie",
                    canOpenCatalog = false,
                    onConfirm = { ids, rounds, _, _ -> recorded += ids to rounds },
                    onOpenCatalog = {},
                    onDismiss = {},
                )
            }
        }

        composeRule.onNodeWithText("Curl").performClick()
        composeRule.onNodeWithText("Crear superserie").performClick()

        assertEquals(listOf(listOf("a", "b") to 3), recorded)
    }
}
