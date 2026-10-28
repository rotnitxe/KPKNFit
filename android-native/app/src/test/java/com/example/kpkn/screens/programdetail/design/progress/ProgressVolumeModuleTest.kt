package com.example.kpkn.screens.programdetail.design.progress

import android.app.Application
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.test.assertCountEquals
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onAllNodesWithText
import androidx.compose.ui.test.onNodeWithContentDescription
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performScrollTo
import com.example.kpkn.screens.programdetail.ProgramDetailSamples
import com.example.kpkn.screens.programdetail.design.ProgramVolumeMuscle
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

/**
 * «Series por músculo» (g-volume) en Robolectric: lo hecho frente al plan, la nota del mínimo útil, la fila «Rangos», el desglose al tocar
 * un músculo y los dos vacíos con su salida.
 */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [34], application = Application::class)
class ProgressVolumeModuleTest {

    @get:Rule
    val composeRule = createComposeRule()

    private fun setModule(
        muscles: List<ProgramVolumeMuscle> = ProgramDetailSamples.volumeWeek,
        onOpenRanges: () -> Unit = {},
        onDoneView: (Boolean) -> Unit = {},
    ) {
        composeRule.setContent {
            var expanded by remember { mutableStateOf<String?>(null) }
            Box(Modifier.verticalScroll(rememberScrollState())) {
                ProgressVolumeModule(
                    muscles = muscles,
                    doneView = true,
                    onDoneViewChange = onDoneView,
                    rangesLabel = "perfil del asistente",
                    onOpenRanges = onOpenRanges,
                    expandedMuscle = expanded,
                    onToggleMuscle = { expanded = if (expanded == it) null else it },
                    breakdown = if (expanded == "Pectorales") ProgramDetailSamples.volumeBreakdownPectorales else null,
                )
            }
        }
    }

    /** Con registros, cada fila dice lo hecho sobre lo planificado, sin unidad: «22 / 26». */
    @Test
    fun cadaFilaDiceLoHechoSobreElPlan() {
        setModule()

        composeRule.onNodeWithText("22 / 26").assertExists()
        composeRule.onNodeWithContentDescription("Cuádriceps: 22 hechas de 26 planificadas").assertExists()
        composeRule.onNodeWithText("Hecho").assertIsDisplayed()
        // «Plan» sale dos veces: en el conmutador y en la leyenda.
        composeRule.onAllNodesWithText("Plan").assertCountEquals(2)
        composeRule.onNodeWithText("Indirectas: ½").performScrollTo().assertIsDisplayed()
    }

    /** Lo hecho por debajo del mínimo útil lleva su nota; sin trabajo en el plan, la otra. */
    @Test
    fun porDebajoDelMinimoUtilLoDice() {
        val zones = ProgramDetailSamples.volumeWeek.first().zones
        setModule(
            muscles = listOf(
                ProgramVolumeMuscle("Bíceps", 8.0, zones, real = 6.0, scaleMax = 0.0),
                ProgramVolumeMuscle("Pantorrillas", 0.0, zones, real = 0.0, scaleMax = 0.0),
            ),
        )

        composeRule.onNodeWithText("Por debajo del mínimo útil (8)").assertExists()
        composeRule.onNodeWithText("No hay trabajo directo en el plan").assertExists()
    }

    /** «Rangos: perfil del asistente» abre su hoja, y «Plan» cambia de vista. */
    @Test
    fun rangosYPlanAvisan() {
        var opened = false
        var doneView: Boolean? = null
        setModule(onOpenRanges = { opened = true }, onDoneView = { doneView = it })

        composeRule.onNodeWithText("Rangos: perfil del asistente").performClick()
        composeRule.onAllNodesWithText("Plan")[0].performClick()

        composeRule.runOnIdle {
            assertTrue(opened)
            assertEquals(false, doneView)
        }
    }

    /** Tocar un músculo despliega sus ejercicios; tocarlo otra vez lo pliega. */
    @Test
    fun tocarUnMusculoDespliegaSuDesglose() {
        setModule()

        composeRule.onNodeWithContentDescription("Pectorales: 16 hechas de 16 planificadas").performScrollTo().performClick()
        composeRule.onNodeWithText("Press banca").assertExists()
        composeRule.onNodeWithContentDescription("Pectorales: 16 hechas de 16 planificadas").performClick()
        composeRule.onAllNodesWithText("Press banca").assertCountEquals(0)
    }

    /** Sin programa activo, el módulo lo dice en una línea y «Activar» lo activa. */
    @Test
    fun sinProgramaActivoOfreceActivar() {
        var activated = false
        composeRule.setContent { ProgressVolumeEmpty(inactive = true, onAction = { activated = true }) }

        composeRule.onNodeWithText("Activa tu programa para ver el volumen.").assertIsDisplayed()
        composeRule.onNodeWithText("Activar").performClick()
        composeRule.runOnIdle { assertTrue(activated) }
    }

    /** Sin sesiones, «Añadir sesión». */
    @Test
    fun sinSesionesOfreceAnadirSesion() {
        var added = false
        composeRule.setContent { ProgressVolumeEmpty(inactive = false, onAction = { added = true }) }

        composeRule.onNodeWithTag("volumen_sin_sesiones").assertExists()
        composeRule.onNodeWithText("Aún no hay sesiones en esta semana.").assertIsDisplayed()
        composeRule.onNodeWithText("Añadir sesión").performClick()
        composeRule.runOnIdle { assertTrue(added) }
    }
}
