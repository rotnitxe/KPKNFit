package com.example.kpkn.screens.programdetail.design

import android.app.Application
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.ui.semantics.SemanticsActions
import androidx.compose.ui.test.assertHasClickAction
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performSemanticsAction
import com.example.kpkn.screens.onboarding.design.LocalWizardReducedMotion
import com.example.kpkn.screens.programdetail.ProgramDetailSamples
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

/**
 * Módulo «Volumen de la semana» y sus vacíos (P4), en Robolectric: desplegar una fila, calibrar y los textos honestos.
 * Se pintan con movimiento reducido: la entrada animada del módulo no debe mover los nodos mientras se prueban.
 */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [34], application = Application::class)
class ProgramVolumeModuleTest {

    @get:Rule
    val composeRule = createComposeRule()

    /** Pinta [content] en reposo, sin la entrada animada de los módulos. */
    private fun setAtRest(content: @Composable () -> Unit) {
        composeRule.setContent {
            CompositionLocalProvider(LocalWizardReducedMotion provides true) {
                content()
            }
        }
    }

    @Test
    fun filaDesplegadaMuestraElDesglose() {
        setAtRest {
            ProgramVolumeModule(
                muscles = ProgramDetailSamples.volumeWeek,
                scope = ProgramDetailSamples.volumeScopeWeek,
                onSelectKind = {},
                onPreviousScope = {},
                onNextScope = {},
                expandedMuscle = "Pectorales",
                onToggleMuscle = {},
                breakdown = ProgramDetailSamples.volumeBreakdownPectorales,
                onCalibrate = {},
            )
        }
        composeRule.onNodeWithText("Press banca").assertExists()
    }

    @Test
    fun tocarUnaFilaPideDesplegarla() {
        var requested: String? = null
        setAtRest {
            ProgramVolumeModule(
                muscles = ProgramDetailSamples.volumeWeek,
                scope = ProgramDetailSamples.volumeScopeWeek,
                onSelectKind = {},
                onPreviousScope = {},
                onNextScope = {},
                expandedMuscle = null,
                onToggleMuscle = { requested = it },
                breakdown = null,
                onCalibrate = {},
            )
        }
        // La fila es un botón con acción de clic: se activa por su acción semántica, la misma que usa TalkBack.
        composeRule.onNodeWithTag("volumen_fila_Pectorales")
            .assertHasClickAction()
            .performSemanticsAction(SemanticsActions.OnClick)
        assertEquals("Pectorales", requested)
    }

    @Test
    fun calibrarLlamaALaAccion() {
        var calibrated = false
        setAtRest {
            ProgramVolumeModule(
                muscles = ProgramDetailSamples.volumeWeek,
                scope = ProgramDetailSamples.volumeScopeWeek,
                onSelectKind = {},
                onPreviousScope = {},
                onNextScope = {},
                expandedMuscle = null,
                onToggleMuscle = {},
                breakdown = null,
                onCalibrate = { calibrated = true },
            )
        }
        composeRule.onNodeWithText("Calibrar").performClick()
        assertTrue(calibrated)
    }

    @Test
    fun sinActivarElProgramaElVacioOfreceActivar() {
        var activated = false
        setAtRest {
            ProgramVolumeInactive(onActivate = { activated = true })
        }
        composeRule.onNodeWithText("Activa tu programa para ver el volumen").assertExists()
        composeRule.onNodeWithText("Activar").performClick()
        assertTrue(activated)
    }

    @Test
    fun sinSesionesElVacioOfreceAnadirSesion() {
        var added = false
        setAtRest {
            ProgramVolumeNoSessions(onAddSession = { added = true })
        }
        composeRule.onNodeWithText("Aún no hay sesiones en esta semana").assertExists()
        composeRule.onNodeWithText("Añadir sesión").performClick()
        assertTrue(added)
    }
}
