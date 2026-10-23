package com.example.kpkn.screens.sessioneditor.components.sheets

import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.ui.test.assertCountEquals
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.unit.Density
import androidx.compose.ui.unit.dp
import androidx.compose.ui.test.assertHeightIsAtLeast
import androidx.compose.ui.test.assertIsNotEnabled
import androidx.compose.ui.test.onNodeWithContentDescription
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onAllNodesWithText
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performScrollTo
import com.example.kpkn.data.models.Exercise
import com.example.kpkn.data.models.ExerciseSet
import com.example.kpkn.data.models.RepRange
import com.example.kpkn.data.models.SessionPart
import com.example.kpkn.data.models.TrainingMode
import com.example.kpkn.screens.sessioneditor.SupersetDraft
import com.example.kpkn.ui.design.KpknSetFormat
import org.junit.Assert.assertEquals
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

/**
 * ED-D · C2: el creador único de superseries. La lista sale por partes, con la letra del orden en la insignia de lo marcado, el módulo
 * de rondas y descansos y el atajo al catálogo.
 */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [34], application = android.app.Application::class, qualifiers = "w360dp-h880dp-xxhdpi")
class SupersetCreatorC2ComposeTest {

    @get:Rule
    val rule = createComposeRule()

    private fun member(id: String, name: String, sets: Int = 3) = Exercise(
        id = id,
        name = name,
        trainingMode = TrainingMode.REPS,
        sets = (1..sets).map {
            ExerciseSet(id = "$id-$it", targetReps = 10, targetRepsRange = RepRange(8, 10), targetRIR = 2)
        },
    )

    private val sentadilla = member("sent", "Sentadilla trasera con barra alta", 4)
    private val peso = member("peso", "Peso muerto rumano", 4)
    private val dominadas = member("dom", "Dominadas", 3)
    private val cara = member("cara", "Face pull con cuerda", 3)
    private val quinto = member("q", "Remo con mancuernas", 3)

    private fun sheet(
        draft: () -> SupersetDraft,
        fontScale: Float = 1f,
        exercises: List<Exercise> = listOf(sentadilla, peso, dominadas, cara, quinto),
        onDraft: (SupersetDraft) -> Unit,
    ) {
        rule.setContent {
            val density = LocalDensity.current
            CompositionLocalProvider(LocalDensity provides Density(density.density, fontScale)) {
                SupersetMemberPickerSheet(
                    draft = draft(),
                    sessionExercises = exercises,
                    sessionParts = listOf(
                        SessionPart(id = "p1", name = "Principal", exercises = listOf(sentadilla, peso)),
                        SessionPart(id = "p2", name = "Accesorios", exercises = listOf(dominadas, cara)),
                    ),
                    onUpdateDraft = onDraft,
                    onConfirm = {},
                    onOpenCatalog = {},
                    onDismiss = {},
                )
            }
        }
    }

    @Test
    fun elCreadorMuestraLasPartesLasLetrasYElModuloDeRondasYDescansos() {
        var draft by mutableStateOf(SupersetDraft(exerciseIds = listOf("sent")))
        sheet({ draft }) { draft = it }

        rule.onNodeWithText("SUPERSERIE").assertIsDisplayed()
        rule.onNodeWithText("1 de 4").assertIsDisplayed()
        rule.onNodeWithText("PRINCIPAL").assertExists()
        rule.onNodeWithText("ACCESORIOS").assertExists()
        // La insignia marcada lleva su letra del orden.
        rule.onNodeWithText("A").assertIsDisplayed()
        rule.onAllNodesWithText("B").assertCountEquals(0)
        // El módulo de configuración del tablero C2.
        rule.onNodeWithText("Rondas").performScrollTo().assertIsDisplayed()
        rule.onNodeWithText("Descanso entre ejercicios").performScrollTo().assertIsDisplayed()
        rule.onNodeWithText("Descanso tras la ronda").performScrollTo().assertIsDisplayed()
        // El atajo al catálogo.
        rule.onNodeWithText("Desde el catálogo").performScrollTo().assertIsDisplayed()
        rule.onNodeWithTag("superset-picker-confirm").assertIsDisplayed()
    }

    @Test
    fun marcarUnEjercicioLePoneLaLetraSiguienteYElBorradorGuardaElOrden() {
        var draft by mutableStateOf(SupersetDraft(exerciseIds = listOf("sent")))
        sheet({ draft }) { draft = it }

        rule.onNodeWithText("Peso muerto rumano").performClick()
        rule.waitForIdle()

        assertEquals(listOf("sent", "peso"), draft.exerciseIds)
        rule.onNodeWithText("A").assertIsDisplayed()
        // La segunda fila queda al final de la lista con scroll: se baja hasta ella antes de comprobar la insignia.
        rule.onNodeWithText("B").performScrollTo().assertIsDisplayed()
        rule.onNodeWithText("2 de 4").assertIsDisplayed()
        rule.onNodeWithText("Crear superserie con 2").assertIsDisplayed()
    }

    @Test
    fun conCuatroMarcadosElQuintoNoEntraYSeAvisaDelMaximo() {
        var draft by mutableStateOf(SupersetDraft(exerciseIds = listOf("sent", "peso", "dom", "cara")))
        sheet({ draft }) { draft = it }

        rule.onNodeWithText("Máximo 4").assertIsDisplayed()
        rule.onNodeWithText("Remo con mancuernas").performScrollTo().performClick()
        rule.waitForIdle()

        assertEquals(listOf("sent", "peso", "dom", "cara"), draft.exerciseIds)
        rule.onNodeWithText("4 de 4").assertIsDisplayed()
    }

    @Test
    fun losAtajosDeDescansoEscribenEnElBorrador() {
        var draft by mutableStateOf(SupersetDraft(exerciseIds = listOf("sent", "peso")))
        sheet({ draft }) { draft = it }

        // El atajo de «Descanso entre ejercicios» con 30 s. La etiqueta usa el espacio duro de `doseTime` («30 s»).
        rule.onNodeWithText(KpknSetFormat.doseTime(30)).performClick()
        rule.waitForIdle()

        assertEquals(30, draft.restBetweenExercises)
    }

    @Test
    fun conFuenteAlCientoTreintaLaListaYLaConfiguracionSonAccesiblesYElMinimoEsReal() {
        var draft by mutableStateOf(SupersetDraft(exerciseIds = listOf("sent", "peso"), rounds = 4))
        sheet({ draft }, fontScale = 1.3f) { draft = it }
        rule.onNodeWithTag("superset-picker-content").assertHeightIsAtLeast(96.dp)
        rule.onNodeWithText("Peso muerto rumano").performScrollTo().assertIsDisplayed()
        rule.onNodeWithText("B").assertIsDisplayed()
        rule.onNodeWithTag("superset-picker-confirm").assertIsDisplayed()
        rule.onNodeWithText("Rondas").performScrollTo().assertIsDisplayed()
        rule.onNodeWithContentDescription("Quitar una ronda").assertIsNotEnabled()
        rule.onNodeWithContentDescription("Añadir una ronda").performClick()
        rule.waitForIdle()
        assertEquals(5, draft.rounds)
        rule.onNodeWithText("Descanso tras la ronda").performScrollTo().assertIsDisplayed()
        rule.onNodeWithTag("superset-picker-confirm").assertIsDisplayed()
        rule.onNodeWithText("Cancelar").assertIsDisplayed()
    }


    @Test
    fun elGrupoExistenteMuestraSusMiembrosYPermiteDesmarcarlosYVolverAMarcarlos() {
        var draft by mutableStateOf(SupersetDraft(sourceGroupId = "own", exerciseIds = listOf("sent", "peso")))
        sheet({ draft }, exercises = listOf(
            sentadilla.copy(supersetGroupRef = "own"), peso.copy(supersetGroupRef = "own"),
            dominadas.copy(supersetGroupRef = "other"), cara, quinto,
        )) { draft = it }
        rule.onNodeWithText("A").assertIsDisplayed()
        rule.onNodeWithText("B").assertIsDisplayed()
        rule.onNodeWithText("Dominadas").assertDoesNotExist()
        rule.onNodeWithText(sentadilla.name).performScrollTo().performClick()
        rule.waitForIdle()
        assertEquals(listOf("peso"), draft.exerciseIds)
        rule.onNodeWithText(peso.name).performScrollTo().performClick()
        rule.waitForIdle()
        assertEquals(emptyList<String>(), draft.exerciseIds)
        rule.onNodeWithText(sentadilla.name).assertExists()
        rule.onNodeWithText(peso.name).performClick()
        rule.waitForIdle()
        rule.onNodeWithText(sentadilla.name).performScrollTo().performClick()
        rule.waitForIdle()
        assertEquals(listOf("peso", "sent"), draft.exerciseIds)
        assertEquals("own", draft.sourceGroupId)
        rule.onNodeWithText("B").assertIsDisplayed()
    }

}
