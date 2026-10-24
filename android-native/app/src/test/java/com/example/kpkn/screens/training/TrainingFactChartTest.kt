package com.example.kpkn.screens.training

import androidx.compose.foundation.layout.Column
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.test.*
import androidx.compose.ui.test.junit4.*
import androidx.compose.ui.unit.Density
import com.example.kpkn.domain.multisport.SportId
import com.example.kpkn.domain.training.insights.*
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class)
@Config(sdk=[34],qualifiers="w360dp-h740dp",application=android.app.Application::class)
class TrainingFactChartTest {
    @get:Rule val compose = createComposeRule()
    private val now=1_792_843_200_000L
    @Test fun largeTextPointKeepsItsSourceAfterStateRestoration() {
        val point=TrainingFact("source-1","activity-1",now-86_400_000, SportId.JUMP,
            "JUMP_HEIGHT",31.25,"cm",FactOrigin.OBSERVED,"hands-on-hips","platform-a","jump-v1",mapOf("surface" to "rubber"))
        val restore=StateRestorationTester(compose)
        restore.setContent {
            CompositionLocalProvider(LocalDensity provides Density(1f,1.3f)) {
                MaterialTheme { Column { TrainingFactChart(listOf(point),now,4) } }
            }
        }
        compose.onNode(hasClickAction() and hasText("31,25 cm",substring=true)).performScrollTo().performClick()
        compose.onNodeWithText("Actividad: activity-1\nFuente: source-1").assertExists()
        restore.emulateSavedInstanceStateRestore()
        compose.onNodeWithText("Actividad: activity-1\nFuente: source-1").assertExists()
        compose.onNodeWithText("Cerrar").performClick()
    }
}
