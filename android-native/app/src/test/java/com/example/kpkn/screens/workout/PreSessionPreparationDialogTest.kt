package com.example.kpkn.screens.workout

import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.*
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.test.*
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.unit.Density
import org.junit.Assert.*
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
class PreSessionPreparationDialogTest {
    @get:Rule val compose = createComposeRule()
    @Test fun largeTextAllowsContinuingWithoutCreatingAnAnswer() {
        var writes=0;var continued=false
        compose.setContent {
            CompositionLocalProvider(LocalDensity provides Density(LocalDensity.current.density,1.3f)) {
                MaterialTheme { PreSessionPreparationDialog("",emptyList(),emptyList(),{writes++},{continued=true}) }
            }
        }
        compose.onNode(isDialog()).capturePrivateTrainingEvidence("before-empty-large-text")
        compose.onNodeWithText("Continuar").assertIsDisplayed().performClick()
        assertTrue(continued);assertEquals(0,writes)
    }
    @Test fun notesRemainAnExplicitOptionalEdit() {
        var notes by mutableStateOf("");var continued=false
        compose.setContent {
            MaterialTheme { PreSessionPreparationDialog(notes,listOf("Registro manual · 5 km · 28 min"),
                listOf("Molestia registrada: rodilla"),{notes=it},{continued=true}) }
        }
        compose.onNode(hasSetTextAction()).performScrollTo().performTextInput("Cansancio declarado hoy")
        compose.onNode(isDialog()).capturePrivateTrainingEvidence("before-recorded-context")
        compose.onNodeWithText("Continuar").performClick()
        assertEquals("Cansancio declarado hoy",notes);assertTrue(continued)
    }
}
