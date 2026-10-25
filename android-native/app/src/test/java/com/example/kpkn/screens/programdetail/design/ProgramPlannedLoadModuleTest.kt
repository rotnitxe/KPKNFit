package com.example.kpkn.screens.programdetail.design

import androidx.compose.runtime.*
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.unit.Density
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performScrollTo
import com.example.kpkn.domain.training.planning.*
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import org.robolectric.annotation.GraphicsMode

@RunWith(RobolectricTestRunner::class)
@GraphicsMode(GraphicsMode.Mode.NATIVE)
@Config(sdk = [34],qualifiers = "w360dp-h900dp-xxhdpi",application = android.app.Application::class)
class ProgramPlannedLoadModuleTest {
    @get:Rule val rule = createComposeRule()
    @Test fun the_plan_summary_opens_its_definition_at_130_percent_without_a_recovery_score() {
        val projection = PlannedLoadProjection("w",listOf(PlannedMuscleLoad("Pectorales",8.0,6.0)),
            listOf("Un porcentaje de TM no equivale a un porcentaje de 1RM."),weekName = "Semana 2",cycleNumber = 1)
        rule.setContent {
            var show by remember { mutableStateOf(false) }
            val density = LocalDensity.current
            CompositionLocalProvider(LocalDensity provides Density(density.density,1.3f)) {
                if (show) ProgramPlannedLoadSheet(projection) { show = false }
                else ProgramPlannedLoadModule(projection) { show = true }
            }
        }
        rule.onNodeWithText("Ver semana prevista").performClick()
        rule.mainClock.advanceTimeBy(1_000L)
        rule.onNodeWithText("Un porcentaje de TM no equivale a un porcentaje de 1RM.",useUnmergedTree = true).performScrollTo()
        rule.onNodeWithText("Cerrar").performScrollTo().performClick()
        rule.onNodeWithText("Ver semana prevista").assertExists()
    }
}
