package com.example.kpkn.screens.workout

import android.app.Application
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNodeWithContentDescription
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import com.example.kpkn.data.models.ExerciseSet
import com.example.kpkn.data.models.RepRange
import com.example.kpkn.screens.workout.design.WorkoutBottomIsland
import com.example.kpkn.screens.workout.design.WorkoutClockLabel
import com.example.kpkn.screens.workout.design.WorkoutExerciseHeader
import com.example.kpkn.screens.workout.design.WorkoutProgressBar
import com.example.kpkn.screens.workout.design.WorkoutTopRow
import com.example.kpkn.ui.theme.KPKNTheme
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

/**
 * Marco de la sesión en vivo (LS2a): fila superior, reloj con ritmo, barra de progreso, cabecera del ejercicio e isla inferior, con
 * sus textos honestos (sin ritmo sin límite, sin «Última vez» sin historial) y su cableado de acciones.
 */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [34], application = Application::class)
class WorkoutFrameTest {

    @get:Rule
    val rule = createComposeRule()

    @Test
    fun topRowMinimizesAndOpensTheMenu() {
        var minimized = 0
        var opened = 0
        rule.setContent {
            KPKNTheme {
                WorkoutTopRow(
                    onMinimize = { minimized++ },
                    onMore = { opened++ },
                    voiceActive = true,
                    center = {
                        WorkoutClockLabel(clock = "24:31", pace = null, paceColor = Color.Unspecified, onClick = {})
                    },
                )
            }
        }
        rule.onNodeWithContentDescription("Minimizar").performClick()
        rule.onNodeWithContentDescription("Más opciones de la sesión").performClick()
        rule.onNodeWithContentDescription("Control por voz activo").assertExists()
        assertEquals(1, minimized)
        assertEquals(1, opened)
    }

    @Test
    fun clockHasNoPaceLineWithoutATimeLimit() {
        rule.setContent {
            KPKNTheme {
                WorkoutClockLabel(clock = "24:31", pace = null, paceColor = Color.Unspecified, onClick = {})
            }
        }
        rule.onNodeWithContentDescription("Tiempo de sesión 24:31").assertExists()
        rule.onNodeWithText("Vas a tiempo").assertDoesNotExist()
    }

    @Test
    fun clockShowsThePaceLineWhenThereIsALimit() {
        rule.setContent {
            KPKNTheme {
                WorkoutClockLabel(
                    clock = "24:31",
                    pace = "Vas por delante",
                    paceColor = Color.Unspecified,
                    onClick = {},
                )
            }
        }
        rule.onNodeWithText("Vas por delante").assertExists()
    }

    @Test
    fun exerciseHeaderWithoutHistoryDoesNotPaintLastTime() {
        rule.setContent {
            KPKNTheme {
                WorkoutExerciseHeader(
                    eyebrow = "Ejercicio 3 de 6 · Torso A",
                    name = "Press inclinado",
                    detailLine = null,
                    targetLine = "Objetivo 4 × 6–8 · RIR 2",
                    lastLine = null,
                    protocolLabel = null,
                    onOptions = {},
                )
            }
        }
        rule.onNodeWithText("Objetivo 4 × 6–8 · RIR 2").assertExists()
        rule.onNodeWithText("Última vez", substring = true).assertDoesNotExist()
    }

    @Test
    fun islandCallsPreviousNextAndRoute() {
        var previous = 0
        var next = 0
        var route = 0
        rule.setContent {
            KPKNTheme {
                WorkoutBottomIsland(
                    previousEnabled = true,
                    nextLabel = "Press inclinado",
                    onPrevious = { previous++ },
                    onNext = { next++ },
                    onOpenRoute = { route++ },
                )
            }
        }
        rule.onNodeWithContentDescription("Ejercicio anterior").performClick()
        rule.onNodeWithContentDescription("Ejercicio siguiente").performClick()
        rule.onNodeWithText("Sigue: Press inclinado").performClick()
        assertEquals(1, previous)
        assertEquals(1, next)
        assertEquals(1, route)
    }

    @Test
    fun progressBarNamesThePositionAndOpensTheRoute() {
        var route = 0
        rule.setContent {
            KPKNTheme {
                WorkoutProgressBar(
                    exerciseCount = 6,
                    currentIndex = 2,
                    doneFlags = listOf(true, true, false, false, false, false),
                    onOpenRoute = { route++ },
                )
            }
        }
        rule.onNodeWithContentDescription("Ejercicio 3 de 6. Abrir la ruta de la sesión").performClick()
        assertEquals(1, route)
    }

    @Test
    fun targetLineReadsSetsRepsAndRir() {
        val sets = (1..4).map { index -> ExerciseSet(id = "s$index", targetRepsRange = RepRange(6, 8), targetRIR = 2) }
        assertEquals("Objetivo 4 × 6–8 · RIR 2", workoutLiveTargetLine(sets))
        assertNull(workoutLiveTargetLine(emptyList()))
    }

    @Test
    fun lastLineAppearsOnlyWithHistory() {
        assertNull(workoutLiveLastLine(null))
        assertEquals("Última vez 80 kg × 8", workoutLiveLastLine("80 kg × 8"))
    }

    @Test
    fun clockAndPaceTextsAreHonest() {
        assertEquals("24:31", formatElapsedClock(1471))
        assertEquals("1:02:05", formatElapsedClock(3725))
        assertEquals("Vas a tiempo", workoutPaceStatusText(isExceeded = false, coachPaceAlert = null))
        assertEquals("Vas por detrás", workoutPaceStatusText(isExceeded = false, coachPaceAlert = "retrasado"))
        assertEquals("Vas por delante", workoutPaceStatusText(isExceeded = false, coachPaceAlert = "adelantado"))
    }

    @Test
    fun eyebrowNamesTheSupersetOrTheExercise() {
        assertEquals("Ejercicio 3 de 6 · Torso A", workoutLiveEyebrow(2, 6, "Torso A", null, 1, 1))
        assertEquals("Superserie A · ronda 2 de 3", workoutLiveEyebrow(2, 6, "Torso A", "A", 2, 3))
    }
}
