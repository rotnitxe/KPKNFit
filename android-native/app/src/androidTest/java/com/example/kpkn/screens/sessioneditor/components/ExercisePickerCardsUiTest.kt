package com.example.kpkn.screens.sessioneditor.components

import androidx.activity.ComponentActivity
import androidx.compose.foundation.layout.Column
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.ui.test.assertCountEquals
import androidx.compose.ui.test.junit4.createAndroidComposeRule
import androidx.compose.ui.test.onAllNodesWithText
import androidx.compose.ui.test.onNodeWithContentDescription
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import com.example.kpkn.data.models.AspectOption
import com.example.kpkn.data.models.ExerciseMuscleInfo
import com.example.kpkn.data.models.InvolvedMuscle
import com.example.kpkn.data.models.MuscleRole
import com.example.kpkn.data.models.TechnicalAspect
import org.junit.Assert.assertEquals
import org.junit.Rule
import org.junit.Test

class ExercisePickerCardsUiTest {

    @get:Rule
    val composeRule = createAndroidComposeRule<ComponentActivity>()

    private fun pressInfo(
        id: String = "press-ui",
        name: String = "Press de banca",
        description: String = "Empuje horizontal del tren superior.",
    ): ExerciseMuscleInfo = ExerciseMuscleInfo(
        id = id,
        name = name,
        description = description,
        movementPattern = "Empuje Horizontal",
        force = "Empuje",
        involvedMuscles = listOf(
            InvolvedMuscle(
                muscle = "Pectorales",
                role = MuscleRole.PRIMARY,
                volumeContribution = 1.0,
            ),
            InvolvedMuscle(
                muscle = "Deltoides anterior",
                role = MuscleRole.SECONDARY,
                volumeContribution = 0.5,
                emphasis = "anterior",
            ),
            InvolvedMuscle(
                muscle = "Tríceps",
                role = MuscleRole.SECONDARY,
                volumeContribution = 0.5,
            ),
        ),
        catalogOptionAxes = listOf(
            TechnicalAspect(
                id = "grip",
                name = "Agarre",
                options = listOf(
                    AspectOption(id = "libre", name = "Libre"),
                    AspectOption(id = "cerrado", name = "Cerrado"),
                ),
                defaultOptionId = "libre",
            ),
        ),
    )

    @Test
    fun info_expands_inline_and_technical_or_muscle_chips_do_not_select() {
        var selectionCount = 0
        var aspectChangeCount = 0

        composeRule.setContent {
            MaterialTheme {
                ExercisePickerDetailedCard(
                    info = pressInfo(),
                    isSelected = true,
                    onSelect = { selectionCount++ },
                    onToggleInfo = {},
                    isInfoExpanded = true,
                    selectedAspects = mapOf("grip" to "libre"),
                    onAspectsChange = { aspectChangeCount++ },
                    showAspects = true,
                )
            }
        }

        composeRule.onNodeWithText("Información del ejercicio").assertExists()
        composeRule.onNodeWithText("Volumen equivalente por serie").assertExists()

        composeRule.onNodeWithText("Cerrado").performClick()
        assertEquals(1, aspectChangeCount)
        assertEquals(0, selectionCount)

        composeRule.onNodeWithText("Deltoides · anterior").performClick()
        composeRule.onNodeWithText("hombro", substring = true).assertDoesNotExist()
        composeRule.onNodeWithText("biomechanicalReason", substring = true).assertDoesNotExist()
        assertEquals(0, selectionCount)

        composeRule.onNodeWithText("Press de banca").performClick()
        assertEquals(1, selectionCount)
    }

    @Test
    fun opening_a_second_card_closes_the_first_inline_info() {
        val first = pressInfo(id = "press-a", name = "Press A", description = "Descripción única A.")
        val second = pressInfo(id = "press-b", name = "Press B", description = "Descripción única B.")
        var expandedId by mutableStateOf<String?>(null)

        composeRule.setContent {
            MaterialTheme {
                Column {
                    listOf(first, second).forEach { info ->
                        ExercisePickerDetailedCard(
                            info = info,
                            isSelected = false,
                            onSelect = {},
                            isInfoExpanded = expandedId == info.id,
                            onToggleInfo = {
                                expandedId = if (expandedId == info.id) null else info.id
                            },
                        )
                    }
                }
            }
        }

        composeRule.onNodeWithContentDescription("Mostrar información de Press A").performClick()
        composeRule.onAllNodesWithText("Información del ejercicio").assertCountEquals(1)
        composeRule.onNodeWithContentDescription("Mostrar información de Press B").performClick()
        composeRule.onAllNodesWithText("Información del ejercicio").assertCountEquals(1)
        composeRule.onNodeWithContentDescription("Ocultar información de Press A").assertDoesNotExist()
        composeRule.onNodeWithContentDescription("Ocultar información de Press B").assertExists()
    }
}
