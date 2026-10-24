package com.example.kpkn.screens.progress

import android.app.Application
import androidx.compose.foundation.layout.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.test.*
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.unit.dp
import com.example.kpkn.domain.training.*
import org.junit.*
import org.junit.Assert.*
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class)
@Config(sdk=[34],application=Application::class,qualifiers="w360dp-h2600dp-mdpi")
class ExerciseProgressUiTest {
    @get:Rule val rule=createComposeRule()
    @Test fun controlesAccesiblesSolicitanMetricaYRango() {
        var metric: ExerciseProgressMetric?=null;var window: ExerciseProgressWindow?=null
        rule.setContent { ExerciseProgressScreen(ExerciseProgressSamples.state("strength"),{},{metric=it},{window=it},{},{},Modifier.width(360.dp).height(2400.dp)) }
        rule.onNodeWithTag("progress-metric-SETS").performScrollTo().assertHeightIsAtLeast(48.dp).performClick()
        assertEquals(ExerciseProgressMetric.SETS,metric)
        rule.onNodeWithText("1 mes").performScrollTo().performClick();assertEquals(ExerciseProgressWindow.MONTH,window)
        rule.onNodeWithContentDescription("e1RM: 5 puntos",substring=true).performScrollTo().assertExists()
    }
    @Test fun modoAsistidoExplicaDireccionSinFalsoE1rm() {
        rule.setContent { ExerciseProgressScreen(ExerciseProgressSamples.state("assisted"),{},{},{},{},{},Modifier.width(360.dp).height(2400.dp)) }
        rule.onNodeWithTag("progress-metric-E1RM").assertDoesNotExist()
        rule.onAllNodesWithText("kg asistencia ↓",substring=true).onFirst().performScrollTo().assertExists()
    }
    @Test fun sinDatosLoDiceSinInventarCifras() {
        rule.setContent { ExerciseProgressScreen(ExerciseProgressSamples.state("empty"),{},{},{},{},{},Modifier.width(360.dp).height(2400.dp)) }
        rule.onNodeWithText("Todavía no hay sesiones de este ejercicio.",substring=true).assertIsDisplayed()
        rule.onNodeWithText("Récords históricos").assertDoesNotExist()
    }
    @Test fun tocarDentroDelCuartoPuntoSeleccionaEsePuntoYNoElAnterior() {
        var selected: Int?=null
        rule.setContent { ExerciseProgressScreen(ExerciseProgressSamples.state("strength"),{},{},{},{selected=it},{},Modifier.width(360.dp).height(2400.dp)) }
        rule.onNodeWithTag("exercise-progress-chart").performScrollTo().performTouchInput {
            // Fixture: cinco sesiones, extremos 96 y 123 kg; cuarta sesión 110 kg. Tocar 2 px a la izquierda aún cae dentro del punto PR.
            click(Offset(12f+.75f*(width-24f)-2f,12f+(123f-110f)/(123f-96f)*(height-24f)))
        }
        rule.runOnIdle { assertEquals(3,selected) }
    }

}
