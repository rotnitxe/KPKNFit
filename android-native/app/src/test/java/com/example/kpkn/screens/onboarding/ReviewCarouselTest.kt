package com.example.kpkn.screens.onboarding

import androidx.compose.material3.Text
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.ui.test.assertIsOff
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNodeWithContentDescription
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import com.example.kpkn.domain.onboarding.CompetitionAnswer
import com.example.kpkn.domain.onboarding.SetupStepId
import com.example.kpkn.domain.onboarding.SetupWizardBlock
import com.example.kpkn.domain.onboarding.TrainingGoalProfile
import com.example.kpkn.screens.onboarding.design.LocalWizardReducedMotion
import com.example.kpkn.screens.onboarding.design.WizardDock
import com.example.kpkn.screens.onboarding.design.WizardGenderMark
import com.example.kpkn.screens.onboarding.design.WizardMassUnit
import com.example.kpkn.screens.onboarding.design.WizardWeightScale
import com.example.kpkn.screens.onboarding.rings.ringsTowerValues
import com.example.kpkn.ui.components.rings.TowerRingValues
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

/**
 * La revisión en carrusel: los destinos de «Editar», los textos que salen de los datos (solo los que cuentan), las casillas que
 * se marcan y el botón final con su etiqueta de prueba. Las lecturas puras no montan el ViewModel.
 */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [34], application = android.app.Application::class, qualifiers = "w360dp-h800dp-xxhdpi")
class ReviewCarouselTest {

    @get:Rule
    val rule = createComposeRule()

    // ── Destinos de «Editar» ───────────────────────────────────────────────

    @Test
    fun editTargetOfEachBlockIsItsFirstStep() {
        val route = listOf(
            SetupStepId.NAME,
            SetupStepId.EQUATION_SEX,
            SetupStepId.GOAL,
            SetupStepId.EQUIPMENT,
            SetupStepId.WEEKDAYS,
            SetupStepId.NUTRITION_START,
            SetupStepId.RINGS_INTRO,
            SetupStepId.REVIEW_ACTIVATE,
        )
        val targets = reviewBlockTargets(route)
        assertEquals(SetupStepId.NAME, targets[SetupWizardBlock.BASICS])
        assertEquals(SetupStepId.GOAL, targets[SetupWizardBlock.TRAINING])
        assertEquals(SetupStepId.NUTRITION_START, targets[SetupWizardBlock.NUTRITION])
        assertEquals(SetupStepId.RINGS_INTRO, targets[SetupWizardBlock.RINGS])
    }

    @Test
    fun blockWithoutStepsInTheRouteHasNoEditTarget() {
        val targets = reviewBlockTargets(listOf(SetupStepId.NAME, SetupStepId.GOAL))
        assertNull(targets[SetupWizardBlock.NUTRITION])
        assertNull(targets[SetupWizardBlock.RINGS])
    }

    // ── Textos que salen de los datos ──────────────────────────────────────

    @Test
    fun trainingLineNamesDaysMinutesAndMainDay() {
        assertEquals("4 días × 60 min · tu sesión principal el jueves", reviewTrainingLine(days = 4, minutes = 60, mainDay = 4))
        assertEquals("3 días", reviewTrainingLine(days = 3, minutes = null, mainDay = null))
        assertNull(reviewTrainingLine(days = 0, minutes = 60, mainDay = 1))
    }

    @Test
    fun competitionLineSaysOnlyWhatCounts() {
        val base = SetupWizardDraft(goalProfile = TrainingGoalProfile.POWERLIFTING, experience = SetupExperience.INTERMEDIATE)
        val withDate = base.copy(competition = CompetitionAnswer.WITH_DATE, meetDateIso = "2027-03-14", meetName = "Nacional Open")
        assertEquals("Compites el 14 de marzo de 2027 · Nacional Open", reviewCompetitionLine(withDate))
        assertEquals("Compites el 14 de marzo de 2027", reviewCompetitionLine(withDate.copy(meetName = "")))
        assertEquals("Compites, aún sin fecha", reviewCompetitionLine(base.copy(competition = CompetitionAnswer.NO_DATE)))
        assertNull(reviewCompetitionLine(base.copy(competition = CompetitionAnswer.NO)))
        // Powerbuilding no tiene competición: su respuesta guardada no cuenta para el programa.
        assertNull(reviewCompetitionLine(withDate.copy(goalProfile = TrainingGoalProfile.POWERBUILDING)))
    }

    @Test
    fun bodyLineSkipsWhatIsMissing() {
        val draft = SetupWizardDraft(
            heightCm = 176.0,
            weightKg = 78.0,
            weightUnit = "kg",
            bodyFatPercent = 18.0,
            bodyFatSource = SetupBodyFatSource.MEASURED,
        )
        assertEquals(
            "176 cm · ${WizardWeightScale.formatWithUnit(78.0, WizardMassUnit.KG)} · 18 % de grasa",
            reviewBodyLine(draft),
        )
        assertEquals("176 cm", reviewBodyLine(SetupWizardDraft(heightCm = 176.0)))
        assertNull(reviewBodyLine(SetupWizardDraft()))
    }

    @Test
    fun unknownBodyFatIsNotShownAsPercent() {
        val draft = SetupWizardDraft(bodyFatPercent = 18.0, bodyFatSource = SetupBodyFatSource.UNKNOWN)
        assertNull(reviewBodyFatPercentOf(draft))
    }

    @Test
    fun genderSymbolAndLabelFollowTheChosenAnswer() {
        val female = SetupWizardDraft().withStepChoice(SetupStepId.EQUATION_SEX, "female")
        assertEquals(WizardGenderMark.FEMALE, reviewGenderMarkOf(female))
        assertEquals("Mujer", reviewSexLabelOf(female))
        val transFemale = SetupWizardDraft().withStepChoice(SetupStepId.EQUATION_SEX, "trans_female")
        assertEquals(WizardGenderMark.TRANS_FEMALE, reviewGenderMarkOf(transFemale))
        // «No lo sé» no tiene símbolo: el glifo se omite y la palabra dice lo que hay.
        val unknown = SetupWizardDraft().withStepChoice(SetupStepId.EQUATION_SEX, "unknown")
        assertNull(reviewGenderMarkOf(unknown))
        assertEquals("No lo sé", reviewSexLabelOf(unknown))
    }

    @Test
    fun ringLinesNameTheValueOrSayUncalibrated() {
        assertEquals("Músculos 64 %", ringLineText("Músculos", 64))
        assertEquals("Columna sin calibrar", ringLineText("Columna", null))
    }

    @Test
    fun towerValuesAreEmptyWithoutCoverage() {
        assertEquals(TowerRingValues(columna = null, musculos = null, energia = null), ringsTowerValues(null))
    }

    // ── Carrusel (Compose) ─────────────────────────────────────────────────

    /** Una página de prueba: su nombre, su «Editar» y un texto para reconocerla. */
    private fun page(title: String, label: String, onEdit: (() -> Unit)? = null): ReviewPage =
        ReviewPage(title = title, onEdit = onEdit, content = { _ -> Text(label) })

    @Test
    fun carouselHasOneDotPerPage() {
        rule.setContent {
            CompositionLocalProvider(LocalWizardReducedMotion provides true) {
                ReviewCarousel(
                    pages = listOf(
                        page("Tú", "alias"),
                        page("Entreno", "programa"),
                        page("Nutrición", "kcal"),
                        page("RINGS", "torre"),
                    ),
                )
            }
        }
        rule.onNodeWithContentDescription("Página 4 de 4").assertExists()
    }

    @Test
    fun carouselWithoutNutritionHasThreePages() {
        rule.setContent {
            CompositionLocalProvider(LocalWizardReducedMotion provides true) {
                ReviewCarousel(
                    pages = listOf(page("Tú", "alias"), page("Entreno", "programa"), page("RINGS", "torre")),
                )
            }
        }
        rule.onNodeWithContentDescription("Página 3 de 3").assertExists()
        rule.onNodeWithContentDescription("Página 4 de 4").assertDoesNotExist()
    }

    @Test
    fun editButtonOfThePageRunsItsTarget() {
        var edits = 0
        rule.setContent {
            CompositionLocalProvider(LocalWizardReducedMotion provides true) {
                ReviewCarousel(
                    pages = listOf(
                        page("Tú", "alias", onEdit = { }),
                        page("Entreno", "programa", onEdit = { edits++ }),
                    ),
                    initialPage = 1,
                )
            }
        }
        rule.onNodeWithContentDescription("Editar Entreno").performClick()
        rule.waitForIdle()
        assertEquals(1, edits)
    }

    @Test
    fun activationPillShowsTheLabelAndKeepsTheTag() {
        var clicks = 0
        rule.setContent {
            CompositionLocalProvider(LocalWizardReducedMotion provides true) {
                WizardDock(
                    enabled = true,
                    label = "Activar y entrar a KPKN",
                    textLabel = "Activar y entrar a KPKN",
                    onClick = { clicks++ },
                )
            }
        }
        rule.onNodeWithText("Activar y entrar a KPKN").assertExists()
        rule.onNodeWithTag("setup-continue").performClick()
        rule.waitForIdle()
        assertEquals(1, clicks)
    }

    @Test
    fun confirmationIsACheckboxThatToggles() {
        var toggles = 0
        rule.setContent {
            CompositionLocalProvider(LocalWizardReducedMotion provides true) {
                ReviewConfirmation(
                    title = "Confirmo la activación",
                    body = "El programa se activa al confirmar.",
                    confirmed = false,
                    onToggle = { toggles++ },
                )
            }
        }
        rule.onNodeWithText("Confirmo la activación").assertIsOff()
        rule.onNodeWithText("Confirmo la activación").performClick()
        rule.waitForIdle()
        assertEquals(1, toggles)
    }
}
