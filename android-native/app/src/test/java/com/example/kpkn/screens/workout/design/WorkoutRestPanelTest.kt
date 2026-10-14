package com.example.kpkn.screens.workout.design

import androidx.compose.material3.MaterialTheme
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import com.example.kpkn.screens.workout.RestTimerKind
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

/** El descanso único (LS3): sus botones llaman a lo que deben, y la píldora vuelve al descanso al tocarla. */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [34], application = android.app.Application::class)
class WorkoutRestPanelTest {

    @get:Rule
    val composeRule = createComposeRule()

    private fun setPanel(
        remaining: Int = 105,
        total: Int = 150,
        suggested: Int? = null,
        onAdjust: (Int) -> Unit = {},
        onSkip: () -> Unit = {},
        onUse: () -> Unit = {},
    ) {
        composeRule.setContent {
            MaterialTheme {
                WorkoutRestPanel(
                    remainingSeconds = remaining,
                    totalSeconds = total,
                    nextLine = "Siguiente: Serie 3 · 80 kg × 8",
                    suggestedSeconds = suggested,
                    onAdjustSeconds = onAdjust,
                    onSkip = onSkip,
                    onUseSuggestion = onUse,
                )
            }
        }
    }

    @Test
    fun menos15YMas15LlamanConMenos15YMas15() {
        val adjusts = mutableListOf<Int>()
        setPanel(onAdjust = { adjusts += it })

        composeRule.onNodeWithText("−15 s").performClick()
        composeRule.onNodeWithText("+15 s").performClick()

        assertEquals(listOf(-15, 15), adjusts)
    }

    @Test
    fun saltarDescansoLlamaASaltar() {
        var skipped = 0
        setPanel(onSkip = { skipped++ })

        composeRule.onNodeWithText("Saltar descanso").performClick()

        assertEquals(1, skipped)
    }

    @Test
    fun usarAplicaElDescansoSugerido() {
        var used = 0
        setPanel(suggested = 150, onUse = { used++ })

        composeRule.onNodeWithText("Descanso sugerido 2:30").assertExists()
        composeRule.onNodeWithText("Usar").performClick()

        assertEquals(1, used)
    }

    @Test
    fun enCeroSeVeListo() {
        setPanel(remaining = 0)

        composeRule.onNodeWithText("Listo").assertExists()
    }

    @Test
    fun laPildoraVuelveAlDescansoAlTocarla() {
        var opened = 0
        composeRule.setContent {
            MaterialTheme {
                WorkoutRestPill(
                    remainingSeconds = 105,
                    totalSeconds = 150,
                    onOpen = { opened++ },
                )
            }
        }

        composeRule.onNodeWithText("1:45 · Descanso").assertExists()
        composeRule.onNodeWithText("1:45 · Descanso").performClick()

        assertEquals(1, opened)
    }

    @Test
    fun lineaSiguienteSegunElTipoDeDescanso() {
        assertEquals("Siguiente ronda", workoutRestNextLine(RestTimerKind.SUPERSET_ROUND, null, null, null))
        assertEquals("Entre lados", workoutRestNextLine(RestTimerKind.BETWEEN_SIDES, null, null, null))
        assertEquals(
            "Siguiente: Serie 3 · 80 kg × 8",
            workoutRestNextLine(RestTimerKind.STANDARD, nextSetNumber = 3, plannedWeightKg = 80.0, plannedReps = 8),
        )
        assertEquals(
            "Siguiente: Serie 4 · 82,5 kg × 6",
            workoutRestNextLine(RestTimerKind.STANDARD, nextSetNumber = 4, plannedWeightKg = 82.5, plannedReps = 6),
        )
        assertEquals(
            "Siguiente: Serie 2 · 8 repeticiones",
            workoutRestNextLine(RestTimerKind.STANDARD, nextSetNumber = 2, plannedWeightKg = null, plannedReps = 8),
        )
        assertNull(workoutRestNextLine(RestTimerKind.STANDARD, nextSetNumber = null, plannedWeightKg = 80.0, plannedReps = 8))
    }

    @Test
    fun relojDelDescansoEnMinutosYSegundos() {
        assertEquals("1:45", formatWorkoutRestClock(105))
        assertEquals("0:05", formatWorkoutRestClock(5))
        assertEquals("10:00", formatWorkoutRestClock(600))
        assertEquals("0:00", formatWorkoutRestClock(-3))
    }

    @Test
    fun laReglaMapeaDeUnoADiez() {
        val range = 1..10
        assertEquals(1, workoutScoreValueAt(x = 14f, widthPx = 300f, padPx = 14f, range = range))
        assertEquals(10, workoutScoreValueAt(x = 286f, widthPx = 300f, padPx = 14f, range = range))
        assertEquals(6, workoutScoreValueAt(x = 150f, widthPx = 300f, padPx = 14f, range = range))
    }
}
