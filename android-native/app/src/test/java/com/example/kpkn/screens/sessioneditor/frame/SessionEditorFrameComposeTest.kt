package com.example.kpkn.screens.sessioneditor.frame

import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNodeWithContentDescription
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.unit.dp
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import dev.chrisbanes.haze.HazeState
import org.junit.Assert.assertEquals
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import org.robolectric.annotation.GraphicsMode

/** Las acciones de la fila superior y de la barra inferior del editor, sin mirar su aspecto (ER1a §2.1 y §2.3). */
@RunWith(RobolectricTestRunner::class)
@GraphicsMode(GraphicsMode.Mode.NATIVE)
@Config(sdk = [34], qualifiers = "w360dp-h640dp-xxhdpi", application = android.app.Application::class)
class SessionEditorFrameComposeTest {

    @get:Rule
    val rule = createComposeRule()

    @Composable
    private fun TopBar(canPrevious: Boolean, canNext: Boolean, calls: MutableList<String>) {
        SessionEditorFrameTopBar(
            dayLabel = "Jue · Pierna A",
            forward = true,
            canGoPrevious = canPrevious,
            canGoNext = canNext,
            onBack = { calls += "back" },
            onPrevious = { calls += "previous" },
            onNext = { calls += "next" },
            onOpenWeek = { calls += "week" },
            onTransfer = { calls += "transfer" },
            onHistory = { calls += "history" },
        )
    }

    @Test
    fun topBarArrowsAndTitleCallTheirActions() {
        val calls = mutableListOf<String>()
        rule.setContent { TopBar(canPrevious = true, canNext = true, calls = calls) }

        rule.onNodeWithContentDescription("Día siguiente").performClick()
        rule.onNodeWithContentDescription("Día anterior").performClick()
        rule.onNodeWithText("Jue · Pierna A").performClick()
        rule.onNodeWithContentDescription("Atrás").performClick()

        assertEquals(listOf("next", "previous", "week", "back"), calls)
    }

    @Test
    fun topBarArrowsDoNothingWhereThereIsNoSessionToGoTo() {
        val calls = mutableListOf<String>()
        rule.setContent { TopBar(canPrevious = false, canNext = false, calls = calls) }

        rule.onNodeWithContentDescription("Día siguiente").performClick()
        rule.onNodeWithContentDescription("Día anterior").performClick()

        assertEquals(emptyList<String>(), calls)
    }

    @Test
    fun bottomBarButtonsOpenCatalogAssistantAndTime() {
        val calls = mutableListOf<String>()
        rule.setContent {
            Box(Modifier.fillMaxSize()) {
                SessionEditorFrameBottomBar(
                    hazeState = HazeState(),
                    suggestionCount = 3,
                    estimatedMinutes = 54,
                    limitMinutes = 60,
                    onAdd = { calls += "add" },
                    onAssistant = { calls += "assistant" },
                    onTime = { calls += "time" },
                    modifier = Modifier.padding(12.dp),
                )
            }
        }

        rule.onNodeWithText("Añadir").performClick()
        rule.onNodeWithContentDescription("Asistente, 3 sugerencias").performClick()
        // Pulido v1 · EDITOR · A2: «Reglas» sale de la barra (es la 4.ª pestaña del Asistente); los minutos abren Asistente › Reglas › Tiempo.
        rule.onNodeWithContentDescription("Tiempo estimado 54 de 60 minutos").performClick()

        assertEquals(listOf("add", "assistant", "time"), calls)
        rule.onNodeWithText("Reglas").assertDoesNotExist()
    }

    @Test
    fun rulesShowsTheEstimatedMinutesOnlyWhenThereIsAnEstimate() {
        rule.setContent {
            Box(Modifier.fillMaxSize()) {
                SessionEditorFrameBottomBar(
                    hazeState = HazeState(),
                    suggestionCount = 0,
                    estimatedMinutes = 54,
                    limitMinutes = 60,
                    onAdd = {},
                    onAssistant = {},
                    onTime = {},
                    modifier = Modifier.padding(12.dp),
                )
            }
        }
        // Los minutos van dentro del anillo; su descripción dice el estimado frente al límite.
        rule.onNodeWithContentDescription("Tiempo estimado 54 de 60 minutos").assertExists()
        rule.onNodeWithText("54 min").assertExists()
        rule.onNodeWithText("de 60").assertExists()
    }

    @Test
    fun rulesHasNoMinutesWithoutExercises() {
        rule.setContent {
            Box(Modifier.fillMaxSize()) {
                SessionEditorFrameBottomBar(
                    hazeState = HazeState(),
                    suggestionCount = 0,
                    estimatedMinutes = null,
                    limitMinutes = 60,
                    onAdd = {},
                    onAssistant = {},
                    onTime = {},
                    modifier = Modifier.padding(12.dp),
                )
            }
        }
        rule.onNodeWithText("0 min").assertExists()
        rule.onNodeWithContentDescription("Tiempo estimado: sin ejercicios").assertExists()
        rule.onNodeWithContentDescription("Tiempo estimado 54 de 60 minutos").assertDoesNotExist()
    }
}
