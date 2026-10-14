package com.example.kpkn.screens.sessioneditor.components

import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.ui.Modifier
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNodeWithContentDescription
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performScrollTo
import androidx.compose.ui.test.performTextReplacement
import com.example.kpkn.data.models.ExerciseMuscleInfo
import com.example.kpkn.data.models.InvolvedMuscle
import com.example.kpkn.data.models.MuscleRole
import com.example.kpkn.domain.exercises.VariantGroup
import com.example.kpkn.domain.exercises.catalogv2.JointInvolvementV2
import com.example.kpkn.domain.exercises.catalogv2.JointRoleV2
import com.example.kpkn.screens.sessioneditor.CatalogSelectionWizardContent
import com.example.kpkn.screens.sessioneditor.CatalogSupersetConfig
import com.example.kpkn.screens.sessioneditor.design.CatalogSupersetConfiguratorContent
import com.example.kpkn.screens.sessioneditor.design.SmartExerciseForm
import org.junit.Assert.assertEquals
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

/** Los cuerpos de la ficha, la superserie, el formulario y el asistente, montados sueltos dentro de su hoja (SE6b). */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [34], application = android.app.Application::class)
class CatalogDetailContentTest {

    @get:Rule
    val rule = createComposeRule()

    private fun pressInfo(
        id: String = "press-detalle",
        name: String = "Press banca",
        variantGroupId: String? = null,
    ): ExerciseMuscleInfo = ExerciseMuscleInfo(
        id = id,
        name = name,
        description = "Empuje horizontal del tren superior.",
        involvedMuscles = listOf(
            InvolvedMuscle(muscle = "Pectorales", role = MuscleRole.PRIMARY, volumeContribution = 1.0),
            InvolvedMuscle(muscle = "Tríceps", role = MuscleRole.SECONDARY, volumeContribution = 0.5),
        ),
        variantGroupId = variantGroupId,
    )

    private fun model(
        joints: List<JointInvolvementV2> = emptyList(),
    ) = CatalogDetailModel(
        name = "Press banca",
        caption = "Pectorales · Barra · Empuje horizontal",
        description = "Empuje horizontal del tren superior.",
        imageVariants = emptyList(),
        selectedImplementation = null,
        axes = emptyList(),
        configurationLine = null,
        configurationIncomplete = false,
        muscleInfo = pressInfo(),
        joints = joints,
        patternId = null,
        isSelected = false,
        canSelect = true,
    )

    @Test
    fun laFichaMuestraNombreMusculoYMaterialYAnadirSeleccionaSinCerrarla() {
        var toggled = 0
        var dismissed = 0
        rule.setContent {
            CatalogDetailContent(
                model = model(),
                onDismiss = { dismissed++ },
                onToggleSelection = { toggled++ },
                onSelectOption = { _, _ -> },
                onImplementationSettled = {},
            )
        }

        rule.onNodeWithText("Press banca").assertExists()
        rule.onNodeWithText("Pectorales · Barra · Empuje horizontal").assertExists()
        rule.onNodeWithText("Añadir").performClick()
        rule.waitForIdle()

        assertEquals(1, toggled)
        assertEquals(0, dismissed)
    }

    @Test
    fun losModulosDeMusculosYArticulacionesSePliegaYDespliegan() {
        rule.setContent {
            CatalogDetailContent(
                model = model(
                    joints = listOf(
                        JointInvolvementV2(jointId = "glenohumeral", role = JointRoleV2.PRIMARY, actions = listOf("flexion")),
                    ),
                ),
                onDismiss = {},
                onToggleSelection = {},
                onSelectOption = { _, _ -> },
                onImplementationSettled = {},
            )
        }

        rule.onNodeWithText("Pectorales").assertDoesNotExist()
        rule.onNodeWithText("Músculos · aporte por 1 serie").performClick()
        rule.waitForIdle()
        rule.onNodeWithText("Pectorales").assertExists()
        rule.onNodeWithText("Músculos · aporte por 1 serie").performClick()
        rule.waitForIdle()
        rule.onNodeWithText("Pectorales").assertDoesNotExist()

        rule.onNodeWithText("Hombro").assertDoesNotExist()
        rule.onNodeWithText("Articulaciones").performClick()
        rule.waitForIdle()
        rule.onNodeWithText("Hombro").assertExists()
    }

    @Test
    fun laCabeceraDeLaFichaCierraConSuDescripcion() {
        var dismissed = 0
        rule.setContent {
            CatalogDetailContent(
                model = model(),
                onDismiss = { dismissed++ },
                onToggleSelection = {},
                onSelectOption = { _, _ -> },
                onImplementationSettled = {},
            )
        }

        rule.onNodeWithContentDescription("Cerrar definición").performClick()
        rule.waitForIdle()

        assertEquals(1, dismissed)
    }

    @Test
    fun crearEjercicioConNombreVacioNoGuarda() {
        val saved = mutableListOf<ExerciseMuscleInfo>()
        rule.setContent {
            Column(Modifier.verticalScroll(rememberScrollState())) {
                SmartExerciseForm(
                    initial = pressInfo(),
                    onSave = { saved += it },
                    onDismiss = {},
                )
            }
        }

        rule.onNodeWithText("Press banca").performTextReplacement("")
        rule.onNodeWithText("Guardar ejercicio").performScrollTo().performClick()
        rule.waitForIdle()

        assertEquals(0, saved.size)
    }

    @Test
    fun laSuperserieLlamaConSusRondas() {
        var confirmed: CatalogSupersetConfig? = null
        rule.setContent {
            CatalogSupersetConfiguratorContent(
                selectedExercises = listOf(pressInfo(), pressInfo(id = "remo", name = "Remo con barra")),
                initialConfig = CatalogSupersetConfig(),
                onDismiss = {},
                onConfirm = { confirmed = it },
            )
        }

        rule.onNodeWithContentDescription("Añadir una ronda").performClick()
        rule.onNodeWithText("Crear superserie").performScrollTo().performClick()
        rule.waitForIdle()

        assertEquals(4, confirmed?.rounds)
    }

    @Test
    fun elAsistenteDeVariantesAvanzaYVuelve() {
        val primera = pressInfo(id = "press-a", variantGroupId = "press")
        val segunda = pressInfo(id = "press-b", name = "Press con mancuernas", variantGroupId = "press")
        val group = VariantGroup(
            id = "press",
            name = "Press",
            variants = listOf(primera, segunda),
            sharedAspects = emptyList(),
        )
        rule.setContent {
            CatalogSelectionWizardContent(
                group = group,
                initialExercise = primera,
                onConfirm = { _, _ -> },
                onDismiss = {},
            )
        }

        rule.onNodeWithText("Paso 1 de 3").assertExists()
        rule.onNodeWithText("Siguiente").performClick()
        rule.waitForIdle()
        rule.onNodeWithText("Paso 2 de 3").assertExists()
        rule.onNodeWithText("Atrás").performClick()
        rule.waitForIdle()
        rule.onNodeWithText("Paso 1 de 3").assertExists()
    }
}
