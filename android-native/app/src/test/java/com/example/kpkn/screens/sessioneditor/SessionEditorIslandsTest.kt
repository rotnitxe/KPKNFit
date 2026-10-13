package com.example.kpkn.screens.sessioneditor

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNodeWithContentDescription
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import com.example.kpkn.screens.sessioneditor.design.SessionEditorBottomIsland
import com.example.kpkn.ui.components.KpknModalTracker
import dev.chrisbanes.haze.HazeState
import org.junit.Assert.assertEquals
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

/** Isla inferior del editor (SE2): sus tres acciones y su ocultación mientras hay una hoja o un diálogo abierto. */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [34], application = android.app.Application::class)
class SessionEditorIslandsTest {

    @get:Rule
    val rule = createComposeRule()

    private fun setIsland(
        visible: Boolean = true,
        onAdd: () -> Unit = {},
        onAssistant: () -> Unit = {},
        onTime: () -> Unit = {},
    ) {
        rule.setContent {
            val haze = remember { HazeState() }
            Box(Modifier.fillMaxSize()) {
                SessionEditorBottomIsland(
                    visible = visible,
                    timeLabel = "≈ 58 min",
                    hazeState = haze,
                    onAddExercise = onAdd,
                    onAssistant = onAssistant,
                    onTime = onTime,
                    modifier = Modifier.align(Alignment.BottomCenter),
                )
            }
        }
    }

    @Test
    fun laIslaLlamaAAnadirAlAsistenteYAlTiempo() {
        var added = 0
        var assistant = 0
        var time = 0
        setIsland(onAdd = { added++ }, onAssistant = { assistant++ }, onTime = { time++ })

        rule.onNodeWithText("Añadir ejercicio").assertIsDisplayed().performClick()
        rule.onNodeWithContentDescription("Asistente de la sesión").performClick()
        rule.onNodeWithText("≈ 58 min").performClick()
        rule.waitForIdle()

        assertEquals(1, added)
        assertEquals(1, assistant)
        assertEquals(1, time)
    }

    @Test
    fun conUnModalAbiertoNoEstaLaIsla() {
        KpknModalTracker.onOpened()
        try {
            setIsland()
            rule.onNodeWithText("Añadir ejercicio").assertDoesNotExist()
        } finally {
            KpknModalTracker.onClosed()
        }
    }
}
