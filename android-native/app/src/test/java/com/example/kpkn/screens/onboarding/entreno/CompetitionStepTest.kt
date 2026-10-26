package com.example.kpkn.screens.onboarding.entreno

import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.test.assertCountEquals
import androidx.compose.ui.test.assertIsEnabled
import androidx.compose.ui.test.assertIsNotEnabled
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onAllNodesWithText
import androidx.compose.ui.test.onNodeWithContentDescription
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import com.example.kpkn.domain.onboarding.CompetitionAnswer
import com.example.kpkn.domain.onboarding.CompetitionRules
import com.example.kpkn.domain.onboarding.SetupChangeDetector
import com.example.kpkn.domain.onboarding.SetupChangeSource
import com.example.kpkn.domain.onboarding.SetupStepGraph
import com.example.kpkn.domain.onboarding.SetupStepId
import com.example.kpkn.domain.onboarding.TrainingGoalProfile
import com.example.kpkn.domain.training.periodization.PhaseKind
import com.example.kpkn.screens.onboarding.SetupExperience
import com.example.kpkn.screens.onboarding.SetupWizardDraft
import com.example.kpkn.screens.onboarding.SetupWizardValidation
import com.example.kpkn.screens.onboarding.inputFootprint
import com.example.kpkn.screens.onboarding.withChangeImpacts
import com.example.kpkn.screens.onboarding.stepContext
import com.example.kpkn.screens.onboarding.wizardPageCopy
import com.example.kpkn.screens.onboarding.withCompetition
import com.example.kpkn.screens.onboarding.withMeetDate
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import java.time.LocalDate

/**
 * «¿Compites?»: quién lo pregunta, qué dice el título, qué fechas valen, cómo cambia la huella y el calendario y la línea
 * de bloques de la pantalla. Hoy fijo: 9 de octubre de 2026 (viernes), semanas que empiezan en lunes.
 */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [34], application = android.app.Application::class, qualifiers = "w411dp-h891dp-xxhdpi")
class CompetitionStepTest {

    @get:Rule
    val rule = createComposeRule()

    private val today: LocalDate = LocalDate.of(2026, 10, 9)
    private val competitive = setOf(
        TrainingGoalProfile.POWERLIFTING, TrainingGoalProfile.WEIGHTLIFTING, TrainingGoalProfile.STRONGMAN,
        TrainingGoalProfile.BODYBUILDING, TrainingGoalProfile.ARMWRESTLING, TrainingGoalProfile.CALISTHENICS,
    )

    @Test
    fun askedOnlyForCompetitiveDisciplinesWithIntermediateOrAdvancedExperience() {
        for (profile in TrainingGoalProfile.entries) {
            for (experience in SetupExperience.entries) {
                val draft = SetupWizardDraft(goalProfile = profile, experience = experience)
                val expected = profile in competitive &&
                    (experience == SetupExperience.INTERMEDIATE || experience == SetupExperience.ADVANCED)
                assertEquals("$profile/$experience ruta", expected, SetupStepId.COMPETITION in SetupStepGraph.stepIds(draft.stepContext()))
                assertEquals("$profile/$experience contexto", expected, draft.stepContext().asksCompetition)
            }
        }
    }

    @Test
    fun titleNamesTheDisciplineAndFallsBackToTheCatalogTitle() {
        assertEquals("¿Compites en powerlifting?", wizardPageCopy(SetupStepId.COMPETITION, TrainingGoalProfile.POWERLIFTING).title)
        assertEquals("¿Compites en halterofilia?", wizardPageCopy(SetupStepId.COMPETITION, TrainingGoalProfile.WEIGHTLIFTING).title)
        assertEquals("¿Compites en calistenia?", wizardPageCopy(SetupStepId.COMPETITION, TrainingGoalProfile.CALISTHENICS).title)
        assertEquals("¿Compites en tu disciplina?", wizardPageCopy(SetupStepId.COMPETITION, null).title)
    }

    @Test
    fun withDateNeedsADateInsideTheRangeAndAShortName() {
        val base = competitionDraft(CompetitionAnswer.WITH_DATE)
        assertTrue(blocking(base))
        assertTrue(blocking(base.withMeetDate(LocalDate.now().plusDays(3).toString())))
        assertTrue(blocking(base.withMeetDate(LocalDate.now().plusMonths(19).toString())))
        val valid = base.withMeetDate(LocalDate.now().plusDays(60).toString())
        assertFalse(blocking(valid))
        assertTrue(blocking(valid.copy(meetName = "x".repeat(41))))
        assertFalse(blocking(valid.copy(meetName = "x".repeat(40))))
        // Sin fecha, la respuesta «sin fecha» y «no compito» valen por sí solas.
        assertFalse(blocking(competitionDraft(CompetitionAnswer.NO_DATE)))
        assertFalse(blocking(competitionDraft(CompetitionAnswer.NO)))
        assertTrue(blocking(competitionDraft(null)))
    }

    @Test
    fun choosingNoDateOrNoDropsTheDateAndTheName() {
        val dated = competitionDraft(CompetitionAnswer.WITH_DATE)
            .withMeetDate("2027-03-14").copy(meetName = "Nacional Open")
        val noDate = dated.withCompetition(CompetitionAnswer.NO_DATE)
        assertEquals(null, noDate.meetDateIso)
        assertEquals("", noDate.meetName)
    }

    @Test
    fun theDateChangesTheFootprintAndMarksPlanForReviewButTheNameDoesNot() {
        val first = competitionDraft(CompetitionAnswer.WITH_DATE).withMeetDate("2027-03-14")
        // La fecha cambia el programa: huella distinta, cambio de CALENDAR, y PLAN y la semana quedan por revisar.
        val moved = first.withMeetDate("2027-03-21")
        assertTrue(first.inputFootprint() != moved.inputFootprint())
        assertEquals("2027-03-21", moved.inputFootprint().meetDateIso)
        assertTrue(SetupChangeSource.CALENDAR in SetupChangeDetector.sourcesFor(first.inputFootprint(), moved.inputFootprint()))
        val reviewed = moved.withChangeImpacts(first).stepProgress.pendingReview
        assertTrue(SetupStepId.PLAN in reviewed)
        assertTrue(SetupStepId.WEEK_LAYOUT in reviewed)
        // El nombre del evento no cambia el programa: misma huella, ningún CALENDAR y nada por revisar.
        val named = first.copy(meetName = "Nacional Open")
        assertEquals(first.inputFootprint(), named.inputFootprint())
        assertFalse(SetupChangeSource.CALENDAR in SetupChangeDetector.sourcesFor(first.inputFootprint(), named.inputFootprint()))
        assertFalse(SetupStepId.PLAN in named.withChangeImpacts(first).stepProgress.pendingReview)
    }

    @Test
    fun aStaleAnswerOutsideTheRouteDoesNotReachTheFootprint() {
        val intermediate = competitionDraft(CompetitionAnswer.WITH_DATE).withMeetDate("2027-03-14")
        val beginner = intermediate.copy(experience = SetupExperience.NEW)
        assertEquals(null, beginner.inputFootprint().competition)
        assertEquals(null, beginner.inputFootprint().meetDateIso)
    }

    @Test
    fun phasesForFourteenWeeksComeFromThePeriodizerAndAddUpToThem() {
        val phases = competitionPhases(TrainingGoalProfile.POWERLIFTING, today, LocalDate.of(2027, 1, 4), weekStartDay = 1)
        assertEquals(14, phases.sumOf { it.weeks })
        assertEquals(
            listOf(
                PhaseKind.ACCUMULATION, PhaseKind.DELOAD, PhaseKind.ACCUMULATION,
                PhaseKind.INTENSIFICATION, PhaseKind.PEAK, PhaseKind.TAPER,
            ),
            phases.map { it.kind },
        )
        assertEquals(
            "Puesta a punto",
            competitionPhaseLabel(phases.last().kind),
        )
        // Con una semana o menos no hay tramos: la pantalla lo dice en lugar de dibujar una barra.
        assertTrue(competitionPhases(TrainingGoalProfile.POWERLIFTING, today, today.plusDays(1), weekStartDay = 1).isEmpty())
    }

    @Test
    fun theDayNameReadsInSpanish() {
        assertEquals("14 de marzo de 2027", CompetitionRules.dateLabel(LocalDate.of(2027, 3, 14)))
    }

    @Test
    fun pickingWithDateShowsTheCalendarAndDaysOutOfRangeCannotBeTapped() {
        val recorded = mutableListOf<String?>()
        setStep(record = { recorded += it })
        rule.onNodeWithTag(competitionTag(CompetitionAnswer.WITH_DATE)).performClick()
        rule.waitForIdle()
        rule.onNodeWithTag(competitionDayTag(LocalDate.of(2026, 10, 10))).assertIsNotEnabled()
        rule.onNodeWithTag(competitionDayTag(LocalDate.of(2026, 10, 23))).assertIsEnabled()
        rule.onNodeWithTag(competitionDayTag(LocalDate.of(2026, 10, 10))).performClick()
        assertTrue(recorded.isEmpty())
    }

    @Test
    fun choosingMarchFourteenSendsTheIsoDate() {
        val recorded = mutableListOf<String?>()
        setStep(record = { recorded += it })
        rule.onNodeWithTag(competitionTag(CompetitionAnswer.WITH_DATE)).performClick()
        rule.waitForIdle()
        repeat(5) {
            rule.onNodeWithContentDescription("Mes siguiente").performClick()
        }
        rule.waitForIdle()
        rule.onNodeWithContentDescription("14 de marzo de 2027").assertIsEnabled()
        rule.onNodeWithTag(competitionDayTag(LocalDate.of(2027, 3, 14))).performClick()
        rule.waitForIdle()
        assertEquals(listOf("2027-03-14"), recorded)
    }

    @Test
    fun theBlockLineShowsTheFourteenWeekPeriodizerPhases() {
        setStep(initialAnswer = CompetitionAnswer.WITH_DATE, initialDate = "2027-01-04")
        rule.waitForIdle()
        rule.onNodeWithText("14 semanas").assertExists()
        rule.onNodeWithText("hasta tu competición").assertExists()
        rule.onAllNodesWithText("Acumulación · 3 semanas").assertCountEquals(2)
        rule.onNodeWithText("Descarga · 1 semana").assertExists()
        rule.onNodeWithText("Intensificación · 3 semanas").assertExists()
        rule.onNodeWithText("Pico · 2 semanas").assertExists()
        rule.onNodeWithText("Puesta a punto · 2 semanas").assertExists()
        rule.onNodeWithContentDescription("Tramos", substring = true).assertExists()
    }

    @Test
    fun exactProtocolSwitchOnlyAppearsWithAValidDateAndReportsTheExplicitChoice() {
        var selected by mutableStateOf(false)
        var answer by mutableStateOf<CompetitionAnswer?>(CompetitionAnswer.NO_DATE)
        rule.setContent {
            CompetitionStepContent(TrainingGoalProfile.POWERLIFTING, answer, "2027-01-04", "Meet", 1,
                onAnswer = { answer = it }, onMeetDate = {}, onMeetName = {}, today = today,
                keepProtocolExact = selected, onKeepProtocolExact = { selected = it })
        }
        rule.onNodeWithTag("competition-keep-exact").assertDoesNotExist()
        rule.runOnIdle { answer = CompetitionAnswer.WITH_DATE }
        rule.waitForIdle()
        rule.onNodeWithTag("competition-keep-exact").performClick()
        rule.runOnIdle { assertTrue(selected) }
        rule.runOnIdle { answer = CompetitionAnswer.NO }
        rule.waitForIdle()
        rule.onNodeWithTag("competition-keep-exact").assertDoesNotExist()
    }

    // ---------------------------------------------------------------- ayudas

    private fun competitionDraft(answer: CompetitionAnswer?): SetupWizardDraft = SetupWizardDraft(
        goalProfile = TrainingGoalProfile.POWERLIFTING,
        experience = SetupExperience.INTERMEDIATE,
    ).withCompetition(answer)

    private fun blocking(draft: SetupWizardDraft): Boolean =
        SetupWizardValidation.validateStep(draft, SetupStepId.COMPETITION).any { it.isBlocking }

    private fun setStep(
        initialAnswer: CompetitionAnswer? = null,
        initialDate: String? = null,
        record: (String?) -> Unit = {},
    ) {
        rule.setContent {
            CompetitionHost(initialAnswer, initialDate, record)
        }
    }

    @Composable
    private fun CompetitionHost(initialAnswer: CompetitionAnswer?, initialDate: String?, record: (String?) -> Unit) {
        var answer by remember { mutableStateOf(initialAnswer) }
        var date by remember { mutableStateOf(initialDate) }
        var name by remember { mutableStateOf("") }
        CompetitionStepContent(
            profile = TrainingGoalProfile.POWERLIFTING,
            answer = answer,
            meetDateIso = date,
            meetName = name,
            weekStartDay = 1,
            onAnswer = { chosen -> answer = chosen },
            onMeetDate = { iso ->
                date = iso
                record(iso)
            },
            onMeetName = { typed -> name = typed },
            today = today,
        )
    }
}
