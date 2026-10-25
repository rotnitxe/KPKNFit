package com.example.kpkn.screens.training

import androidx.compose.foundation.layout.Column
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.semantics.LiveRegionMode
import androidx.compose.ui.semantics.SemanticsProperties
import androidx.compose.ui.test.*
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.junit4.StateRestorationTester
import androidx.compose.ui.unit.Density
import com.example.kpkn.domain.multisport.SportId
import com.example.kpkn.domain.training.insights.*
import com.example.kpkn.testsupport.capturePrivateTrainingEvidence
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import org.robolectric.annotation.GraphicsMode

@RunWith(RobolectricTestRunner::class)
@GraphicsMode(GraphicsMode.Mode.NATIVE)
@Config(sdk=[34],qualifiers="w360dp-h740dp-xxhdpi",application=android.app.Application::class)
class TrainingFactChartTest {
    @get:Rule val compose = createComposeRule()
    private val now=1_792_843_200_000L

    @Test fun pointDetailAnnouncesOneCompleteBlockAndKeepsCloseAsASeparateAction() {
        val point=TrainingFact("source-1","activity-1",now-86_400_000, SportId.JUMP,
            "JUMP_HEIGHT",31.25,"cm",FactOrigin.OBSERVED,"hands-on-hips","platform-a","jump-v1",mapOf("surface" to "rubber"))
        compose.setContent { MaterialTheme { Column { TrainingFactChart(listOf(point),now,4) } } }
        compose.onNode(hasClickAction() and hasText("31,25 cm",substring=true)).performScrollTo().performClick()
        compose.onNode(hasText("31,25 cm",substring=true) and hasText("Disciplina: Salto") and
            hasText("Fuente: source-1",substring=true) and hasText("Método: platform-a",substring=true))
            .assert(SemanticsMatcher.expectValue(SemanticsProperties.LiveRegion,LiveRegionMode.Polite))
        compose.onNode(hasText("Cerrar") and hasClickAction()).performClick()
        compose.onNodeWithText("Disciplina: Salto").assertDoesNotExist()
    }

    @Test fun equalMetricsFromDifferentSportsHaveDistinctLabelsAndKeepTheirFilterAfterRestore() {
        val running = TrainingFact("run-source","run-activity",now-86_400_000, SportId.RUNNING,
            "distanceMeters",5000.0,"m",FactOrigin.OBSERVED,"flat","manual","distance-v1",mapOf("surface" to "track"))
        val cycling = running.copy(sourceId="cycle-source",activityId="cycle-activity",sport=SportId.CYCLING,value=20000.0)
        val restore=StateRestorationTester(compose)
        restore.setContent { MaterialTheme { Column { TrainingFactChart(listOf(running,cycling),now,4) } } }
        compose.onNodeWithText("Carrera · Distancia (m)",useUnmergedTree=true).assertExists()
        compose.onNodeWithText("Ciclismo · Distancia (m)",useUnmergedTree=true).assertExists()
        compose.onNodeWithText("Ciclismo",useUnmergedTree=true).performScrollTo().performClick()
        compose.onNodeWithContentDescription("Gráfico de Ciclismo · Distancia, 1 puntos. Todos disponibles como botones debajo.").assertExists()
        restore.emulateSavedInstanceStateRestore()
        compose.onNodeWithContentDescription("Gráfico de Ciclismo · Distancia, 1 puntos. Todos disponibles como botones debajo.").assertExists()
        compose.onNode(hasClickAction() and hasText("20000 m",substring=true)).performScrollTo().performClick()
        compose.onNodeWithText("Disciplina: Ciclismo").assertExists()
        compose.onNodeWithText("Cerrar").performClick()
    }

    @Test fun emptyHistoryShowsAbsenceWithoutInventingZeroMeasurements() {
        compose.setContent { MaterialTheme { TrainingHistorySection(emptyList(),now) } }
        compose.onNodeWithText("1 semana",useUnmergedTree=true).performClick()
        compose.onNodeWithText("0 sesiones registradas · datos locales").assertExists()
        compose.onNodeWithText("Sin registros en este periodo. No significa descanso ni esfuerzo cero.").assertExists()
        compose.onNode(hasContentDescription("Gráfico de",substring=true)).assertDoesNotExist()
    }

    @Test fun largeTextPointKeepsItsSourceAfterStateRestoration() {
        val point=TrainingFact("source-1","activity-1",now-86_400_000, SportId.JUMP,
            "JUMP_HEIGHT",31.25,"cm",FactOrigin.OBSERVED,"hands-on-hips","platform-a","jump-v1",mapOf("surface" to "rubber"))
        val restore=StateRestorationTester(compose)
        restore.setContent {
            CompositionLocalProvider(LocalDensity provides Density(LocalDensity.current.density,1.3f)) {
                MaterialTheme { Column { TrainingFactChart(listOf(point),now,4) } }
            }
        }
        compose.onNode(hasClickAction() and hasText("31,25 cm",substring=true)).performScrollTo().performClick()
        compose.onNodeWithText("Actividad: activity-1\nFuente: source-1").assertExists()
        compose.capturePrivateTrainingEvidence("history-point-provenance-large-text")
        restore.emulateSavedInstanceStateRestore()
        compose.onNodeWithText("Actividad: activity-1\nFuente: source-1").assertExists()
        compose.onNodeWithText("Cerrar").performClick()
    }
}
