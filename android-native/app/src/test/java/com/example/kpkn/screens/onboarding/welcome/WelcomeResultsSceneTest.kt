package com.example.kpkn.screens.onboarding.welcome

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.LocalView
import androidx.compose.ui.test.*
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.unit.Density
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
class WelcomeResultsSceneTest {
    @get:Rule val compose = createComposeRule()
    @Test fun exampleHasUnitsAndDoesNotPresentPersonalResults() {
        var host: android.view.View? = null
        compose.setContent {
            host = LocalView.current
            CompositionLocalProvider(LocalDensity provides Density(LocalDensity.current.density,1.3f)) {
                MaterialTheme { WelcomeResultsScene(Modifier.fillMaxSize().background(Color.Black)) }
            }
        }
        compose.onNodeWithText("Ejemplo").assertIsDisplayed()
        compose.onNodeWithText("Carrera · 5 km").assertIsDisplayed()
        compose.onNodeWithText("Sesión anterior · 30 min").assertIsDisplayed()
        compose.onNodeWithText("Sesión reciente · 29 min").assertIsDisplayed()
        compose.onNodeWithText("Resultados ilustrativos, no tus registros").assertIsDisplayed()
        compose.capturePrivateTrainingEvidence("welcome-example-large-text", dialog = false, host = host)
    }
}
