package com.example.kpkn.screens.sessioneditor.components

import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.requiredHeight
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNodeWithContentDescription
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performScrollTo
import androidx.compose.ui.test.performTextReplacement
import com.example.kpkn.data.exercises.ExerciseTechniqueImageLookup
import com.example.kpkn.data.exercises.ExerciseTechniqueImageVariant
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
import org.junit.Assert.assertTrue
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

    /** Siete músculos con aportes distintos: la ficha enseña cuatro y «Ver todos» despliega el resto (CF1). */
    private fun sieteMusculos(): ExerciseMuscleInfo = pressInfo().copy(
        involvedMuscles = listOf(
            InvolvedMuscle(muscle = "Pectorales", role = MuscleRole.PRIMARY, volumeContribution = 1.0),
            InvolvedMuscle(muscle = "Deltoides", role = MuscleRole.SECONDARY, volumeContribution = 0.8),
            InvolvedMuscle(muscle = "Tríceps", role = MuscleRole.SECONDARY, volumeContribution = 0.6),
            InvolvedMuscle(muscle = "Dorsales", role = MuscleRole.STABILIZER, volumeContribution = 0.5),
            InvolvedMuscle(muscle = "Trapecio", role = MuscleRole.STABILIZER, volumeContribution = 0.4),
            InvolvedMuscle(muscle = "Bíceps", role = MuscleRole.NEUTRALIZER, volumeContribution = 0.3),
            InvolvedMuscle(muscle = "Romboides", role = MuscleRole.NEUTRALIZER, volumeContribution = 0.2),
        ),
    )

    private fun model(
        joints: List<JointInvolvementV2> = emptyList(),
        muscles: ExerciseMuscleInfo = pressInfo(),
    ) = CatalogDetailModel(
        name = "Press banca",
        caption = "Pectorales · Barra · Empuje horizontal",
        description = "Empuje horizontal del tren superior.",
        imageVariants = emptyList(),
        selectedImplementation = null,
        axes = emptyList(),
        configurationLine = null,
        configurationIncomplete = false,
        muscleInfo = muscles,
        joints = joints,
        patternId = null,
        isSelected = false,
        canSelect = true,
    )

    /** Los tres implementos del press plano con su foto: barra, mancuernas y máquina. */
    private val implementVariants by lazy {
        ExerciseTechniqueImageLookup.variants(ExerciseTechniqueImageLookup.FLAT_BENCH_PRESS_DEFINITION_ID)
            .filter { it.implementation in setOf("barbell", "dumbbells", "machine") }
    }

    private val implementOptions = listOf(
        CatalogDetailOption(value = "barbell", label = "Barra", selected = false, enabled = true),
        CatalogDetailOption(value = "dumbbells", label = "Mancuerna", selected = false, enabled = true),
        CatalogDetailOption(value = "machine", label = "Máquina", selected = false, enabled = true),
    )

    private fun implementModel(
        selected: String?,
        images: List<ExerciseTechniqueImageVariant>,
        options: List<CatalogDetailOption>,
    ) = model().copy(
        imageVariants = images,
        selectedImplementation = selected,
        axes = listOf(
            CatalogDetailAxis(
                axis = "implement",
                label = "Implemento",
                options = options.map { it.copy(selected = it.value == selected) },
            ),
        ),
    )

    @Test
    fun tocarUnaPildoraDeImplementoLlamaASeleccionarlo() {
        val llamadas = mutableListOf<Pair<String, String>>()
        rule.setContent {
            var elegido by remember { mutableStateOf<String?>("barbell") }
            CatalogDetailContent(
                model = implementModel(elegido, implementVariants, implementOptions),
                onDismiss = {},
                onToggleSelection = {},
                onSelectOption = { axis, value ->
                    llamadas += axis to value
                    elegido = value
                },
                onImplementationSettled = { elegido = it },
            )
        }

        rule.onNodeWithTag(CatalogImplementPillsTag).assertExists()
        rule.onNodeWithText("Mancuerna").performClick()
        rule.waitForIdle()

        assertEquals(listOf("implement" to "dumbbells"), llamadas)
        rule.onNodeWithContentDescription("Ilustración del ejercicio con Mancuernas").assertExists()
    }

    @Test
    fun conUnSoloImplementoNoHayFilaDePildoras() {
        rule.setContent {
            CatalogDetailContent(
                model = implementModel("barbell", implementVariants.take(1), implementOptions.take(1)),
                onDismiss = {},
                onToggleSelection = {},
                onSelectOption = { _, _ -> },
                onImplementationSettled = {},
            )
        }

        rule.onNodeWithTag(CatalogImplementPillsTag).assertDoesNotExist()
        rule.onNodeWithContentDescription("Ilustración del ejercicio con Barra").assertExists()
    }

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
    fun losMusculosSalenAbiertosYLasArticulacionesSePliegan() {
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

        rule.onNodeWithText("Pectorales").assertExists()
        rule.onNodeWithText("Hombro").assertDoesNotExist()
        rule.onNodeWithText("Articulaciones").performScrollTo().performClick()
        rule.waitForIdle()
        rule.onNodeWithText("Hombro").assertExists()
    }

    @Test
    fun losMusculosVanAntesQueLaDescripcion() {
        // Ventana alta: con la de Robolectric (470 dp) la descripción queda fuera del área visible y su posición sale vacía (CF1).
        rule.setContent {
            CatalogDetailContent(
                model = model(),
                onDismiss = {},
                onToggleSelection = {},
                onSelectOption = { _, _ -> },
                onImplementationSettled = {},
                modifier = Modifier.requiredHeight(1600.dp),
            )
        }

        rule.waitForIdle()
        val musculos = rule.onNodeWithText("Músculos · aporte por 1 serie").fetchSemanticsNode().boundsInRoot.top
        val descripcion = rule.onNodeWithText("Empuje horizontal del tren superior.").fetchSemanticsNode().boundsInRoot.top
        assertTrue("musculos=$musculos descripcion=$descripcion", musculos < descripcion)
        rule.onNodeWithText("Pectorales").assertExists()
    }

    @Test
    fun verTodosDespliegaLosMusculosQueFaltan() {
        rule.setContent {
            CatalogDetailContent(
                model = model(muscles = sieteMusculos()),
                onDismiss = {},
                onToggleSelection = {},
                onSelectOption = { _, _ -> },
                onImplementationSettled = {},
            )
        }

        rule.onNodeWithText("Trapecio").assertDoesNotExist()
        rule.onNodeWithText("Ver todos").performScrollTo().performClick()
        rule.waitForIdle()
        rule.onNodeWithText("Trapecio").assertExists()
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
