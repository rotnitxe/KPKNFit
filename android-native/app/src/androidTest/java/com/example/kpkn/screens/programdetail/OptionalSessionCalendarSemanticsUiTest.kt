package com.example.kpkn.screens.programdetail

import androidx.activity.ComponentActivity
import androidx.compose.material3.MaterialTheme
import androidx.compose.ui.test.ExperimentalTestApi
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.junit4.createAndroidComposeRule
import androidx.compose.ui.test.onAllNodesWithContentDescription
import androidx.compose.ui.test.onNodeWithContentDescription
import androidx.compose.ui.test.performClick
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.example.kpkn.data.models.Block
import com.example.kpkn.data.models.Macrocycle
import com.example.kpkn.data.models.Mesocycle
import com.example.kpkn.data.models.MesocycleGoal
import com.example.kpkn.data.models.OptionalSessionConfirmation
import com.example.kpkn.data.models.Program
import com.example.kpkn.data.models.ProgramCalendarization
import com.example.kpkn.data.models.ProgramCalendarizationMode
import com.example.kpkn.data.models.ProgramSchedulePlan
import com.example.kpkn.data.models.ProgramStructure
import com.example.kpkn.data.models.ProgramWeek
import com.example.kpkn.data.models.ScheduleMode
import com.example.kpkn.data.models.Session
import com.example.kpkn.data.models.SessionRequirement
import com.example.kpkn.domain.training.ProgramCalendarEngine
import com.example.kpkn.domain.training.WeekWithMeta
import com.example.kpkn.screens.programdetail.components.DayView
import org.junit.Assert.assertEquals
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import java.time.LocalDate

/**
 * Semántica del calendario (UI, SIN persistencia): la acción contextual de
 * confirmación de una sesión OPCAIONAL futura se dibuja SOBRE la sesión+fecha
 * correctas, con estado `checked` (`IconToggleButton`) y con descripción
 * distinta para confirmar/retirar. Sólo callbacks de UI: las afirmaciones de
 * post-alta y de fila Room viven en otros tests (M4).
 *
 * Cubre también el fallback de fecha: si el meta de la semana viene con el mapa
 * de fechas VACÍO, DayView resuelve el día con la MISMA proyección de
 * [ProgramCalendarEngine] (nunca un weekday aislado inventado) y la acción
 * sigue apareciendo.
 */
@RunWith(AndroidJUnit4::class)
@OptIn(ExperimentalTestApi::class)
class OptionalSessionCalendarSemanticsUiTest {

    @get:Rule
    val composeRule = createAndroidComposeRule<ComponentActivity>()

    private val today: LocalDate = LocalDate.now()
    private val anchor: LocalDate = today.plusDays(7)
    private val todayWeekday: Int = today.dayOfWeek.value

    private var captured: Pair<String, String>? = null

    private fun session(id: String, optional: Boolean): Session =
        Session(
            id = id,
            name = if (optional) "Opcional $id" else "Obligatoria $id",
            dayOfWeek = todayWeekday,
            requirement = if (optional) SessionRequirement.OPTIONAL else SessionRequirement.REQUIRED,
        )

    /** Programa calendarizado con ancla FUTURA (hoy + 7) y dos sesiones en el día de hoy. */
    private fun program(confirmations: List<OptionalSessionConfirmation> = emptyList()): Program =
        Program(
            id = "post-setup-calendar",
            name = "Post setup calendario",
            structure = ProgramStructure.COMPLEX,
            startDay = todayWeekday,
            calendarization = ProgramCalendarization(
                mode = ProgramCalendarizationMode.ADVANCED_COMPETITION,
                strictStart = true,
            ),
            schedulePlan = ProgramSchedulePlan(
                anchorDate = anchor.toString(),
                weekStartDay = 1,
                mode = ScheduleMode.DATED,
            ),
            optionalSessionConfirmations = confirmations,
            macrocycles = listOf(
                Macrocycle(
                    id = "mc",
                    name = "Macro",
                    blocks = listOf(
                        Block(
                            id = "b",
                            name = "Bloque",
                            mesocycles = listOf(
                                Mesocycle(
                                    id = "m",
                                    name = "Meso",
                                    goal = MesocycleGoal.ACCUMULATION,
                                    weeks = listOf(
                                        ProgramWeek(
                                            id = "w1",
                                            name = "Semana 1",
                                            sessions = listOf(session("opt-1", true), session("req-1", false)),
                                        ),
                                    ),
                                ),
                            ),
                        ),
                    ),
                ),
            ),
        )

    private fun sessionsOf(program: Program): List<Session> =
        program.macrocycles.first().blocks.first().mesocycles.first().weeks.first().sessions

    private fun weekMeta(program: Program, withDates: Boolean): WeekWithMeta {
        val projected = ProgramCalendarEngine.project(program).weeks.first()
        return WeekWithMeta(
            id = projected.weekId,
            name = "Semana 1",
            sessions = sessionsOf(program),
            mesoGoal = MesocycleGoal.ACCUMULATION,
            mesoIndex = 0,
            trainingDayDates = if (withDates) {
                projected.trainingDayDates.mapValues { it.value.toString() }
            } else {
                emptyMap()
            },
        )
    }

    private fun setDayView(program: Program, withDates: Boolean) {
        captured = null
        composeRule.setContent {
            MaterialTheme {
                DayView(
                    program = program,
                    isSimpleProgram = false,
                    isCalendarized = true,
                    selectedWeek = weekMeta(program, withDates),
                    sessions = sessionsOf(program),
                    onEditSession = {},
                    onAddSession = {},
                    onDeleteSession = {},
                    onStartWorkout = {},
                    onApplySessionsLayout = {},
                    onUpdateStartDay = { _, _, _ -> },
                    onUpdateWeekMetadata = { _, _, _ -> },
                    onToggleOptionalConfirmation = { dayIso, sessionId ->
                        captured = dayIso to sessionId
                    },
                )
            }
        }
    }

    @Test
    fun futureOptionalSessionShowsCheckedActionForItsExactInstance() {
        setDayView(program(), withDates = true)

        // SÓLO la opcional del día tiene acción (la obligatoria no)…
        val toggle = composeRule.onNodeWithContentDescription("Confirmar sesión opcional")
        toggle.assertIsDisplayed()
        assertEquals(
            1,
            composeRule.onAllNodesWithContentDescription("Confirmar sesión opcional")
                .fetchSemanticsNodes()
                .size,
        )
        // …y reporta la instancia EXACTA (día ISO real de la proyección + sesión).
        toggle.performClick()
        assertEquals(anchor.toString() to "opt-1", captured)
    }

    @Test
    fun confirmedInstanceShowsTheRemoveDescription() {
        setDayView(
            program(listOf(OptionalSessionConfirmation(dayIso = anchor.toString(), sessionId = "opt-1"))),
            withDates = true,
        )
        composeRule
            .onNodeWithContentDescription("Retirar confirmación de sesión opcional")
            .assertIsDisplayed()
    }

    @Test
    fun emptyWeekMetaStillResolvesTheProjectedRealDate() {
        // Meta sin mapa de fechas: el fallback de DayView usa la proyección del
        // motor ⇒ la acción sigue apareciendo sobre la fecha real futura.
        setDayView(program(), withDates = false)
        val toggle = composeRule.onNodeWithContentDescription("Confirmar sesión opcional")
        toggle.assertIsDisplayed()
        toggle.performClick()
        assertEquals(anchor.toString() to "opt-1", captured)
    }
}
