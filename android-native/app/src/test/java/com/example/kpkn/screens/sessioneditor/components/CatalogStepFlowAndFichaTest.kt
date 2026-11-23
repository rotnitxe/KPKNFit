package com.example.kpkn.screens.sessioneditor.components

import android.app.Application
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.onNodeWithText
import com.example.kpkn.data.models.ExerciseMuscleInfo
import com.example.kpkn.data.models.InvolvedMuscle
import com.example.kpkn.data.models.MuscleRole
import com.example.kpkn.domain.exercises.catalogv2.JointInvolvementV2
import com.example.kpkn.domain.exercises.catalogv2.JointRoleV2
import com.example.kpkn.screens.sessioneditor.design.CatalogInlineAxis
import com.example.kpkn.screens.sessioneditor.design.CatalogInlineVariants
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [34], application = Application::class, qualifiers = "w411dp-h891dp-xxhdpi")
class CatalogStepFlowAndFichaTest {

    @get:Rule
    val rule = createComposeRule()

    @Test
    fun guidedVariantsShowHeaderStepsAndMissingPrompt() {
        rule.setContent {
            CatalogInlineVariants(
                definitionId = "chest_supported_row",
                axes = listOf(
                    CatalogInlineAxis("implement", listOf("cable", "dumbbells"), "cable"),
                    CatalogInlineAxis("grip_width", listOf("close", "wide"), null),
                ),
                onSelect = { _, _ -> },
                onOpenFicha = {},
                missingAxesPrompt = "Elige Agarre para añadir este ejercicio",
            )
        }
        rule.onNodeWithText("¿Cómo lo vas a hacer?").assertIsDisplayed()
        rule.onNodeWithText("Elige Agarre para añadir este ejercicio").assertIsDisplayed()
        rule.onNodeWithText("Paso 1 de 2 · Implemento").assertIsDisplayed()
        rule.onNodeWithText("Paso 2 de 2 · Amplitud de agarre").assertIsDisplayed()
    }

    @Test
    fun exerciseFichaShowsOnlyPhotoDescriptionMusclesAndJoints() {
        val model = CatalogDetailModel(
            name = "Press banca",
            caption = "Pectorales · Barra",
            description = "Empuje horizontal clásico.",
            imageVariants = emptyList(),
            selectedImplementation = null,
            axes = emptyList(),
            configurationLine = "Barra",
            configurationIncomplete = false,
            muscleInfo = ExerciseMuscleInfo(
                id = "bench_press",
                name = "Press banca",
                involvedMuscles = listOf(
                    InvolvedMuscle("Pectorales", MuscleRole.PRIMARY, volumeContribution = 1.0),
                ),
            ),
            joints = listOf(JointInvolvementV2("shoulder", JointRoleV2.PRIMARY, emptyList())),
            patternId = "horizontal_push",
            isSelected = false,
            canSelect = true,
            setupCues = listOf("Retrae escápulas"),
            executionCues = listOf("Baja controlado"),
        )
        rule.setContent {
            ExerciseFichaContent(
                model = model,
                onDismiss = {},
                onToggleSelection = {},
                onSelectOption = { _, _ -> },
                onImplementationSettled = {},
            )
        }
        rule.onNodeWithTag("exercise-ficha-large-title").assertIsDisplayed()
        rule.onNodeWithTag("exercise-ficha-description").assertIsDisplayed()
        rule.onNodeWithTag("exercise-ficha-muscles").assertIsDisplayed()
        rule.onNodeWithTag("exercise-ficha-joints").assertIsDisplayed()
        rule.onNodeWithText("Preparación").assertDoesNotExist()
        rule.onNodeWithText("Ejecución").assertDoesNotExist()
        rule.onNodeWithText("Ver progreso").assertDoesNotExist()
        rule.onNodeWithText("Configuración").assertDoesNotExist()
    }
}
