package com.example.kpkn.screens.home

import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.performClick
import com.example.kpkn.data.models.KeyDateType
import com.example.kpkn.data.models.Program
import com.example.kpkn.data.models.ProgramKeyDate
import com.example.kpkn.data.models.ProgramStructure
import com.example.kpkn.domain.training.HomeCompetitionResolver
import org.junit.Assert.assertEquals
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

/** C4c: Inicio ofrece «Añadir la fecha de tu competición» solo a quien compite sin fecha clave en su programa activo. */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [34], application = android.app.Application::class, qualifiers = "w411dp-h891dp-xxhdpi")
class CompetitionDatePromptTest {

    @get:Rule
    val rule = createComposeRule()

    private val plan = Program(id = "plan-1", name = "Plan", structure = ProgramStructure.COMPLEX)

    private val planWithDate = plan.copy(
        keyDates = listOf(
            ProgramKeyDate(
                id = "kd-1",
                title = "Open nacional",
                type = KeyDateType.COMPETITION,
                startDate = "2026-11-20",
                eventDate = "2026-11-20",
            ),
        ),
    )

    /** Pinta la sección de Inicio sin sesiones (día de descanso), donde va la cuenta atrás. */
    private fun setHome(program: Program, competesWithoutDate: Boolean, onOpen: (String) -> Unit = {}) {
        val promptId = HomeCompetitionResolver.competitionDatePromptProgramId(program, competesWithoutDate)
        rule.setContent {
            HomeSessionSection(
                sessions = emptyList(),
                currentDayOfWeek = 1,
                onStartWorkout = { _, _ -> },
                onResumeWorkout = {},
                competitionDatePromptProgramId = promptId,
                onOpenProgramForCompetitionDate = onOpen,
            )
        }
    }

    @Test
    fun compite_sin_fecha_clave_muestra_la_accion_y_abre_el_programa() {
        var opened: String? = null
        setHome(plan, competesWithoutDate = true) { opened = it }

        rule.onNodeWithTag(COMPETITION_DATE_PROMPT_TAG).assertExists().performClick()

        assertEquals("plan-1", opened)
    }

    @Test
    fun con_fecha_clave_la_accion_desaparece() {
        setHome(planWithDate, competesWithoutDate = true)

        rule.onNodeWithTag(COMPETITION_DATE_PROMPT_TAG).assertDoesNotExist()
    }

    @Test
    fun quien_no_compite_no_muestra_la_accion() {
        setHome(plan, competesWithoutDate = false)

        rule.onNodeWithTag(COMPETITION_DATE_PROMPT_TAG).assertDoesNotExist()
    }
}
