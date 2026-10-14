package com.example.kpkn.screens.workout

import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNodeWithContentDescription
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import com.example.kpkn.data.models.SessionMilestone
import com.example.kpkn.screens.workout.design.FinishEmptySeriesState
import com.example.kpkn.screens.workout.design.FinishFiguresModule
import com.example.kpkn.screens.workout.design.FinishFooter
import com.example.kpkn.screens.workout.design.FinishPendingSeriesModule
import com.example.kpkn.screens.workout.design.FinishRecoveryModule
import com.example.kpkn.screens.workout.design.finishDrainLine
import com.example.kpkn.screens.workout.design.finishFigures
import com.example.kpkn.screens.workout.design.finishFormatThousands
import com.example.kpkn.screens.workout.design.finishOneDecimal
import com.example.kpkn.screens.workout.design.finishTomorrowLine
import com.example.kpkn.ui.components.rings.TowerRingValues
import com.example.kpkn.ui.theme.KPKNTheme
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

/**
 * Resumen al terminar (LS6): las cifras con su unidad, la torre solo con datos del motor, las series pendientes con sus etiquetas,
 * «Guardar entrenamiento» una vez y el estado vacío. Los cuerpos se montan sueltos: Robolectric no entrega toques dentro de la hoja.
 */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [34], application = android.app.Application::class, qualifiers = "w360dp-h1800dp-xxhdpi")
class WorkoutFinishSummaryTest {

    @get:Rule
    val rule = createComposeRule()

    @Test
    fun cuatroCifrasConSuUnidad() {
        rule.setContent {
            KPKNTheme {
                FinishFiguresModule(figures = finishFigures(58, 22, 8450.0, recordCount = 2, exerciseCount = 6))
            }
        }
        rule.onNodeWithContentDescription("58 min").assertExists()
        rule.onNodeWithContentDescription("22").assertExists()
        rule.onNodeWithContentDescription("8.450 kg").assertExists()
        rule.onNodeWithContentDescription("2").assertExists()
        rule.onNodeWithText("Récords").assertExists()
    }

    @Test
    fun sinRecordsLaCuartaEsEjercicios() {
        rule.setContent {
            KPKNTheme {
                FinishFiguresModule(figures = finishFigures(58, 22, 8450.0, recordCount = 0, exerciseCount = 6))
            }
        }
        rule.onNodeWithText("Ejercicios").assertExists()
        rule.onNodeWithText("Récords").assertDoesNotExist()
        rule.onNodeWithContentDescription("6").assertExists()
    }

    @Test
    fun sinDatosDelMotorNoSePintaLaTorre() {
        rule.setContent {
            KPKNTheme {
                FinishRecoveryModule(values = null, from = null, drainLine = null, tomorrowLine = null)
            }
        }
        rule.onNodeWithText("RECUPERACIÓN").assertDoesNotExist()
    }

    @Test
    fun conDatosDelMotorSeVeElGastoPorCanal() {
        rule.setContent {
            KPKNTheme {
                FinishRecoveryModule(
                    values = TowerRingValues(columna = 70, musculos = 60, energia = 80),
                    from = TowerRingValues(columna = 82, musculos = 92, energia = 98),
                    drainLine = finishDrainLine(32, 18, 12),
                    tomorrowLine = null,
                )
            }
        }
        rule.onNodeWithText("RECUPERACIÓN").assertExists()
        rule.onNodeWithText(finishDrainLine(32, 18, 12)!!).assertExists()
    }

    @Test
    fun calculandoSinAvisoHastaQueSePida() {
        rule.setContent {
            KPKNTheme {
                FinishRecoveryModule(
                    values = null,
                    from = null,
                    drainLine = null,
                    tomorrowLine = null,
                    loading = true,
                    loadingCaptionVisible = false,
                )
            }
        }
        rule.onNodeWithText("Calculando la recuperación…").assertDoesNotExist()
    }

    @Test
    fun calculandoMuestraElAvisoCuandoSePide() {
        rule.setContent {
            KPKNTheme {
                FinishRecoveryModule(
                    values = null,
                    from = null,
                    drainLine = null,
                    tomorrowLine = null,
                    loading = true,
                    loadingCaptionVisible = true,
                )
            }
        }
        rule.onNodeWithText("Calculando la recuperación…").assertExists()
    }

    @Test
    fun guardarEntrenamientoLlamaUnaVez() {
        var saves = 0
        rule.setContent {
            KPKNTheme {
                FinishFooter(
                    primaryLabel = "Guardar entrenamiento",
                    primaryEnabled = true,
                    onPrimary = { saves++ },
                    onShare = {},
                    onBack = {},
                )
            }
        }
        rule.onNodeWithText("Guardar entrenamiento").performClick()
        rule.waitForIdle()
        assertEquals(1, saves)
    }

    @Test
    fun seriesPendientesConSusEtiquetas() {
        rule.setContent {
            KPKNTheme {
                FinishPendingSeriesModule(
                    message = "Quedan 2 series por hacer. Si guardas ya, no se contarán.",
                    actionLabel = "Seguir con ellas",
                    onContinue = {},
                )
            }
        }
        rule.onNodeWithTag("finish_pending_series_notice").assertExists()
        rule.onNodeWithTag("finish_pending_series_continue").assertExists()
    }

    @Test
    fun sinSeriesMuestraElEstadoVacio() {
        rule.setContent {
            KPKNTheme {
                FinishEmptySeriesState(guidance = FINISH_EMPTY_SESSION_GUIDANCE)
            }
        }
        rule.onNodeWithText("Sin series registradas").assertExists()
    }

    @Test
    fun cifrasConRecordsYSinRecords() {
        val con = finishFigures(58, 22, 8450.0, recordCount = 2, exerciseCount = 6)
        assertEquals(listOf("Duración", "Series", "Volumen", "Récords"), con.map { it.label })
        assertEquals(listOf("58", "22", "8.450", "2"), con.map { it.value })
        assertEquals(listOf("min", null, "kg", null), con.map { it.unit })
        val sin = finishFigures(58, 22, 8450.0, recordCount = 0, exerciseCount = 6)
        assertEquals("Ejercicios", sin.last().label)
        assertEquals("6", sin.last().value)
    }

    @Test
    fun milesConPuntoYDecimalesConComa() {
        assertEquals("8.450", finishFormatThousands(8450))
        assertEquals("999", finishFormatThousands(999))
        assertEquals("1.000.000", finishFormatThousands(1_000_000))
        assertEquals("7,5", finishOneDecimal(7.5))
    }

    @Test
    fun gastoYMananaSinDatosNoSePintan() {
        assertNull(finishDrainLine(0, 0, 0))
        assertEquals("Energía −5", finishDrainLine(0, 5, 0))
        assertEquals("Mañana: Energía ≈ 84 %, Columna ≈ 90 %", finishTomorrowLine(84, 90))
        assertEquals("Mañana: Columna ≈ 90 %", finishTomorrowLine(null, 90))
        assertNull(finishTomorrowLine(null, null))
    }

    @Test
    fun losRecordsSonSoloLosPrDeE1rm() {
        val milestones = listOf(
            SessionMilestone(id = "1", exerciseId = "a", exerciseName = "Press", kind = "pr_e1rm", label = "Nuevo PR e1RM", value = 100.0),
            SessionMilestone(id = "2", exerciseId = "b", exerciseName = "Remo", kind = "pr_e1rm", label = "Nuevo PR e1RM", value = 80.0),
            SessionMilestone(
                id = "3",
                exerciseId = "c",
                exerciseName = "Sentadilla",
                kind = "star_goal_reached",
                label = "Meta estrella alcanzada",
                value = 120.0,
            ),
        )
        assertEquals(2, finishSessionRecordCount(milestones))
        assertEquals(0, finishSessionRecordCount(emptyList()))
    }
}
