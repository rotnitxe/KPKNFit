package com.example.kpkn.screens.programdetail.design

import android.app.Application
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onAllNodesWithContentDescription
import androidx.compose.ui.test.onAllNodesWithText
import androidx.compose.ui.test.onNodeWithContentDescription
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import com.example.kpkn.data.models.Block
import com.example.kpkn.data.models.Macrocycle
import com.example.kpkn.data.models.ManualOverrideScope
import com.example.kpkn.data.models.ManualSessionOverride
import com.example.kpkn.data.models.Mesocycle
import com.example.kpkn.data.models.MesocycleGoal
import com.example.kpkn.data.models.Program
import com.example.kpkn.data.models.ProgramStructure
import com.example.kpkn.data.models.ProgramWeek
import com.example.kpkn.data.models.Session
import com.example.kpkn.data.protocols.TrainingPlanRecipe
import com.example.kpkn.domain.training.SessionDurationEstimator
import com.example.kpkn.domain.training.WeekWithMeta
import com.example.kpkn.screens.programdetail.ProgramDetailSamples
import com.example.kpkn.screens.programdetail.components.DayView
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotEquals
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import java.time.LocalDate

/**
 * El día de la agenda de la pestaña Semana (P2), en Robolectric: cada sesión se despliega en su sitio y muestra sus ejercicios; «Empezar»
 * llama a la acción; los minutos salen del estimador y no de series × 3; el menú de una sesión editada ofrece «Restaurar…»; el índice
 * «1 DE 2» sale cuando el día tiene varias sesiones; y la sesión de hoy que no está hecha lleva su botón «Empezar».
 */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [34], application = Application::class)
class ProgramDayModuleTest {

    @get:Rule
    val composeRule = createComposeRule()

    private val todayWeekday: Int = LocalDate.now().dayOfWeek.value

    /** El módulo de un día con sus acciones de prueba; [expanded] son las sesiones desplegadas. */
    private fun setDay(day: ProgramDayUi, expanded: Set<String> = emptySet(), onStart: (Session) -> Unit = {}) {
        composeRule.setContent {
            ProgramDayModule(
                day = day,
                expandedSessionIds = expanded,
                onToggleSession = {},
                onLongPressDay = {},
                onEditDate = {},
                onAddSession = {},
                onStartSession = onStart,
                onEditSession = {},
                onMoreSession = { _, _ -> },
                onRestoreSession = {},
                onToggleOptional = { _, _ -> },
                draggingSessionId = null,
                insertIndex = null,
                anchorFirstSession = false,
                onDayBounds = {},
                onSessionBounds = { _, _ -> },
                onSessionDragStart = {},
                onSessionDrag = { _, _ -> },
                onSessionDragEnd = {},
                onSessionDragCancel = {},
            )
        }
    }

    /** El día de Torso A, con su sesión plegada o desplegada según [expanded], y sus acciones de prueba. */
    private fun setTorsoDay(expanded: Boolean, onStart: (Session) -> Unit = {}) {
        val day = ProgramDetailSamples.day(
            id = 1,
            name = "Lunes",
            short = "Lun",
            sessions = listOf(ProgramDetailSamples.sessionUi(ProgramDetailSamples.torsoA)),
        )
        setDay(day, expanded = if (expanded) setOf(ProgramDetailSamples.torsoA.id) else emptySet(), onStart = onStart)
    }

    @Test
    fun desplegarMuestraLosEjercicios() {
        var expandedIds by mutableStateOf(emptySet<String>())
        val day = ProgramDetailSamples.day(
            id = 1,
            name = "Lunes",
            short = "Lun",
            sessions = listOf(ProgramDetailSamples.sessionUi(ProgramDetailSamples.torsoA)),
        )
        composeRule.setContent {
            ProgramDayModule(
                day = day,
                expandedSessionIds = expandedIds,
                onToggleSession = { id -> expandedIds = if (id in expandedIds) expandedIds - id else expandedIds + id },
                onLongPressDay = {},
                onEditDate = {},
                onAddSession = {},
                onStartSession = {},
                onEditSession = {},
                onMoreSession = { _, _ -> },
                onRestoreSession = {},
                onToggleOptional = { _, _ -> },
                draggingSessionId = null,
                insertIndex = null,
                anchorFirstSession = false,
                onDayBounds = {},
                onSessionBounds = { _, _ -> },
                onSessionDragStart = {},
                onSessionDrag = { _, _ -> },
                onSessionDragEnd = {},
                onSessionDragCancel = {},
            )
        }
        composeRule.onNodeWithText("Press banca").assertDoesNotExist()
        composeRule.onNodeWithText("Torso A").performClick()
        composeRule.onNodeWithText("Press banca").assertIsDisplayed()
    }

    @Test
    fun empezarLlamaALaAccion() {
        var started: Session? = null
        setTorsoDay(expanded = true, onStart = { started = it })
        composeRule.onNodeWithText("Empezar").performClick()
        assertEquals("Torso A", started?.name)
    }

    @Test
    fun losMinutosSaleNDelEstimadorYNoDeSeriesPorTres() {
        val session = ProgramDetailSamples.twentySets
        val estimated = SessionDurationEstimator.estimate(session).totalMinutes
        assertNotEquals("20 series no dan 60 min: el estimador tiene su propio total", 60, estimated)
        val day = ProgramDetailSamples.day(
            id = 3,
            name = "Miércoles",
            short = "Mié",
            sessions = listOf(ProgramDetailSamples.sessionUi(session)),
        )
        setDay(day)
        composeRule.onNodeWithText("≈ $estimated min", substring = true).assertIsDisplayed()
    }

    @Test
    fun elMenuDeLaSesionOfreceRestaurarSoloSiEstaEditada() {
        setDayView(manualProgram())
        // Las sesiones empiezan plegadas: la editada se despliega tocándola y muestra su restauración en línea, una sola vez.
        composeRule.onNodeWithText("Sesión editada").performClick()
        assertEquals(1, composeRule.onAllNodesWithText(RESTORE_LABEL).fetchSemanticsNodes().size)
        // Su menú ofrece editar, mover, restaurar y eliminar; la restauración en línea no se repite en el menú.
        composeRule.onAllNodesWithContentDescription("Más opciones de la sesión")[0].performClick()
        composeRule.onNodeWithText("Editar sesión").assertIsDisplayed()
        composeRule.onNodeWithText("Mover a otro día").assertIsDisplayed()
        composeRule.onNodeWithText("Restaurar desde el plan").assertIsDisplayed()
        composeRule.onNodeWithText("Eliminar sesión").assertIsDisplayed()
        assertEquals(1, composeRule.onAllNodesWithText(RESTORE_LABEL).fetchSemanticsNodes().size)
    }

    @Test
    fun laRestauracionEnLineaAbreLaConfirmacion() {
        setDayView(manualProgram())
        composeRule.onNodeWithText("Sesión editada").performClick()
        composeRule.onNodeWithText(RESTORE_LABEL).performClick()
        composeRule.onNodeWithText("Restaurar sesión").assertIsDisplayed()
    }

    @Test
    fun diaConDosSesionesMuestraElIndice() {
        val day = ProgramDetailSamples.day(
            id = 1,
            name = "Lunes",
            short = "Lun",
            sessions = listOf(
                ProgramDetailSamples.sessionUi(ProgramDetailSamples.torsoA),
                ProgramDetailSamples.sessionUi(ProgramDetailSamples.piernaA),
            ),
        )
        setDay(day)
        composeRule.onNodeWithText("1 DE 2").assertIsDisplayed()
        composeRule.onNodeWithText("2 DE 2").assertIsDisplayed()
    }

    @Test
    fun laSesionDeHoyNoHechaLlevaElBotonEmpezar() {
        var started: Session? = null
        val day = ProgramDetailSamples.day(
            id = 1,
            name = "Lunes",
            short = "Lun",
            sessions = listOf(ProgramDetailSamples.sessionUi(ProgramDetailSamples.torsoA)),
            today = true,
        )
        setDay(day, onStart = { started = it })
        composeRule.onNodeWithContentDescription("Empezar Torso A").performClick()
        assertEquals("Torso A", started?.name)
    }

    /** La vista de días con un programa de dos sesiones de hoy: la editada (con receta fuente) y una sin marca. */
    private fun setDayView(program: Program) {
        val sessions = program.macrocycles.first().blocks.first().mesocycles.first().weeks.first().sessions
        composeRule.setContent {
            MaterialTheme {
                DayView(
                    program = program,
                    isSimpleProgram = false,
                    isCalendarized = false,
                    selectedWeek = WeekWithMeta(
                        id = WEEK_ID,
                        name = "Semana 1",
                        sessions = sessions,
                        mesoGoal = MesocycleGoal.ACCUMULATION,
                        mesoIndex = 0,
                        trainingDayDates = emptyMap(),
                    ),
                    sessions = sessions,
                    onEditSession = {},
                    onAddSession = {},
                    onDeleteSession = {},
                    onStartWorkout = {},
                    onApplySessionsLayout = {},
                    onUpdateStartDay = { _, _, _ -> },
                    onUpdateWeekMetadata = { _, _, _ -> },
                )
            }
        }
    }

    private fun manualProgram(): Program {
        val sessions = listOf(
            Session(id = EDITED, name = "Sesión editada", dayOfWeek = todayWeekday, assignedDays = listOf(todayWeekday)),
            Session(id = PLAIN, name = "Sesión del plan", dayOfWeek = todayWeekday, assignedDays = listOf(todayWeekday)),
        )
        return Program(
            id = "p2-day-menu",
            name = "Semana P2",
            structure = ProgramStructure.COMPLEX,
            startDay = todayWeekday,
            sourceRecipe = TrainingPlanRecipe(id = "p2-recipe", weeks = emptyList()),
            manualSessionOverrides = listOf(
                ManualSessionOverride(
                    sessionId = EDITED,
                    weekId = WEEK_ID,
                    weekOccurrence = 1,
                    scope = ManualOverrideScope.SESSION,
                    reason = "Sesión guardada desde el editor",
                    createdAtMs = 1L,
                ),
            ),
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
                                    weeks = listOf(ProgramWeek(id = WEEK_ID, name = "Semana 1", sessions = sessions)),
                                ),
                            ),
                        ),
                    ),
                ),
            ),
        )
    }

    private companion object {
        const val WEEK_ID = "p2-day-week"
        const val EDITED = "p2-edited"
        const val PLAIN = "p2-plain"
        const val RESTORE_LABEL = "Restaurar esta sesión desde el plan"
    }
}
