package com.example.kpkn.screens.programdetail

import androidx.activity.ComponentActivity
import androidx.compose.material3.MaterialTheme
import androidx.compose.ui.test.ExperimentalTestApi
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.junit4.createAndroidComposeRule
import androidx.compose.ui.test.onAllNodesWithText
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.test.ext.junit.runners.AndroidJUnit4
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
import com.example.kpkn.domain.training.WeekWithMeta
import com.example.kpkn.screens.programdetail.components.DayView
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import java.time.LocalDate

/**
 * «Restaurar esta sesión desde el plan» (§14.5), solo UI y sin persistencia.
 * La acción vive en la tarjeta de la sesión marcada («Sesión personalizada»):
 *
 *  - se ofrece solo si el programa conserva su receta fuente y la sesión no está
 *    registrada ni en curso (`restoreBlockedSessionIds`);
 *  - el badge «Sesión personalizada» se queda aunque el botón se oculte;
 *  - confirmar el diálogo reporta el id exacto de la sesión; cancelar no reporta nada.
 */
@RunWith(AndroidJUnit4::class)
@OptIn(ExperimentalTestApi::class)
class ManualSessionRestoreUiTest {

    @get:Rule
    val composeRule = createAndroidComposeRule<ComponentActivity>()

    private val todayWeekday: Int = LocalDate.now().dayOfWeek.value
    private var restored: String? = null

    private fun session(id: String, name: String) = Session(
        id = id,
        name = name,
        dayOfWeek = todayWeekday,
        assignedDays = listOf(todayWeekday),
    )

    /** Dos sesiones personalizadas en el día de hoy y una sin marca. */
    private fun program(withSourceRecipe: Boolean): Program {
        val sessions = listOf(
            session(EDITED, "Sesión editada"),
            session(TRAINED, "Sesión ya entrenada"),
            session(PLAIN, "Sesión del plan"),
        )
        return Program(
            id = "manual-restore-ui",
            name = "Restaurar sesión UI",
            structure = ProgramStructure.COMPLEX,
            startDay = todayWeekday,
            sourceRecipe = TrainingPlanRecipe(id = "manual-restore-ui-recipe", weeks = emptyList())
                .takeIf { withSourceRecipe },
            manualSessionOverrides = listOf(EDITED, TRAINED).map { id ->
                ManualSessionOverride(
                    sessionId = id,
                    weekId = WEEK_ID,
                    weekOccurrence = 1,
                    scope = ManualOverrideScope.SESSION,
                    reason = "Sesión guardada desde el editor",
                    createdAtMs = 1L,
                )
            },
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

    private fun setDayView(program: Program, blocked: Set<String> = emptySet()) {
        restored = null
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
                    onRestoreManualSessionFromPlan = { restored = it },
                    restoreBlockedSessionIds = blocked,
                )
            }
        }
    }

    private fun restoreButtons() = composeRule.onAllNodesWithText(RESTORE_BUTTON).fetchSemanticsNodes().size
    private fun badges() = composeRule.onAllNodesWithText(BADGE).fetchSemanticsNodes().size

    @Test
    fun badgeAndRestoreButtonShowForEveryCustomizedSessionWithSourceRecipe() {
        setDayView(program(withSourceRecipe = true))

        composeRule.onAllNodesWithText(BADGE)[0].assertIsDisplayed()
        assertEquals("un badge por sesión marcada", 2, badges())
        assertEquals("un botón por sesión marcada", 2, restoreButtons())
    }

    @Test
    fun withoutSourceRecipeTheButtonIsHiddenButTheBadgeStays() {
        setDayView(program(withSourceRecipe = false))

        assertEquals(2, badges())
        assertEquals(0, restoreButtons())
        composeRule.onNodeWithText(RESTORE_BUTTON).assertDoesNotExist()
    }

    @Test
    fun blockedSessionsLoseTheButtonButKeepTheBadge() {
        setDayView(program(withSourceRecipe = true), blocked = setOf(TRAINED))

        assertEquals("el badge se conserva en ambas", 2, badges())
        assertEquals("solo la sesión no entrenada conserva el botón", 1, restoreButtons())
    }

    @Test
    fun confirmingTheDialogReportsTheExactSessionId() {
        setDayView(program(withSourceRecipe = true), blocked = setOf(TRAINED))

        composeRule.onNodeWithText(RESTORE_BUTTON).performClick()
        composeRule.onNodeWithText("Restaurar sesión").assertIsDisplayed()
        assertNull("abrir el diálogo no restaura nada", restored)
        composeRule.onNodeWithText("Restaurar desde el plan").performClick()

        assertEquals(EDITED, restored)
    }

    @Test
    fun cancellingTheDialogDoesNotRestore() {
        setDayView(program(withSourceRecipe = true), blocked = setOf(TRAINED))

        composeRule.onNodeWithText(RESTORE_BUTTON).performClick()
        composeRule.onNodeWithText("Cancelar").performClick()

        assertNull(restored)
        composeRule.onNodeWithText("Restaurar desde el plan").assertDoesNotExist()
    }

    private companion object {
        const val WEEK_ID = "manual-restore-week"
        const val EDITED = "edited-session"
        const val TRAINED = "trained-session"
        const val PLAIN = "plain-session"
        const val BADGE = "Sesión personalizada"
        const val RESTORE_BUTTON = "Restaurar esta sesión desde el plan"
    }
}
