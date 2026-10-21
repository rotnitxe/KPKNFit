package com.example.kpkn.screens.onboarding

import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.material3.Text
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.ui.Modifier
import androidx.compose.ui.test.assertIsNotEnabled
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
import com.example.kpkn.screens.onboarding.design.ActivationButton
import com.example.kpkn.screens.onboarding.design.ActivationFooter
import com.example.kpkn.screens.onboarding.design.ActivationNotice
import com.example.kpkn.screens.onboarding.design.activationNoticesOf
import com.example.kpkn.screens.onboarding.design.ActivationMoment
import com.example.kpkn.screens.onboarding.design.ActivationOverlayBody
import com.example.kpkn.screens.onboarding.design.ActivationPager
import com.example.kpkn.screens.onboarding.design.LocalWizardReducedMotion
import com.example.kpkn.screens.onboarding.design.ReviewConfirmation
import com.example.kpkn.screens.onboarding.design.WizardDock
import com.example.kpkn.screens.onboarding.design.WizardGenderMark
import com.example.kpkn.screens.onboarding.design.WizardMassUnit
import com.example.kpkn.screens.onboarding.design.WizardWeightScale
import com.example.kpkn.screens.onboarding.rings.ringsTowerValues
import com.example.kpkn.ui.components.rings.TowerRingValues
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

/**
 * La revisión y activación: las lecturas puras que dan los textos (solo los que cuentan), los destinos de «Editar», y el overlay
 * (pager de momentos, botón de activar con su etiqueta de prueba, casillas y ausencia de «Ver cómo funciona»). Las lecturas
 * puras no montan el ViewModel.
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

    // ── Overlay (Compose) ──────────────────────────────────────────────────

    /** Un momento de prueba: su bloque, su rótulo, su «Editar» y un texto para reconocerlo. */
    private fun moment(block: SetupWizardBlock, label: String, onEdit: (() -> Unit)? = null): ActivationMoment =
        ActivationMoment(block, label, onEdit) { _ -> Text(label) }

    @Test
    fun overlayHasOneMomentPerRouteBlockAndTheActivationTag() {
        rule.setContent {
            CompositionLocalProvider(LocalWizardReducedMotion provides true) {
                ActivationOverlayBody(
                    title = "Tu KPKN está listo",
                    rise = { 1f },
                    moments = listOf(
                        moment(SetupWizardBlock.BASICS, "Tú"),
                        moment(SetupWizardBlock.TRAINING, "Tu programa"),
                        moment(SetupWizardBlock.RINGS, "RINGS"),
                    ),
                    state = SetupWizardState(draft = SetupWizardDraft()),
                    notices = listOf(ActivationNotice("Tus RINGS se ajustarán con tus primeros entrenos.")),
                    activateEnabled = false,
                    onConfirmActivation = { },
                    onConfirmFixedRecipe = { },
                    onActivate = { },
                    modifier = Modifier.fillMaxSize(),
                )
            }
        }
        rule.onNodeWithTag("setup-activation-overlay").assertExists()
        rule.onNodeWithContentDescription("Página 1 de 3").assertExists()
        rule.onNodeWithContentDescription("Página 4 de 4").assertDoesNotExist()
        rule.onNodeWithTag("setup-continue").assertIsNotEnabled()
    }

    @Test
    fun pagerHasOneDotPerMoment() {
        rule.setContent {
            CompositionLocalProvider(LocalWizardReducedMotion provides true) {
                ActivationPager(
                    moments = listOf(
                        moment(SetupWizardBlock.BASICS, "Tú"),
                        moment(SetupWizardBlock.TRAINING, "Tu programa"),
                        moment(SetupWizardBlock.NUTRITION, "Nutrición"),
                        moment(SetupWizardBlock.RINGS, "RINGS"),
                    ),
                    modifier = Modifier.fillMaxSize(),
                )
            }
        }
        rule.onNodeWithContentDescription("Página 1 de 4").assertExists()
        rule.onNodeWithTag("setup-activation-page-0").assertExists()
    }

    @Test
    fun editButtonOfTheMomentRunsItsBlockTarget() {
        var edits = 0
        rule.setContent {
            CompositionLocalProvider(LocalWizardReducedMotion provides true) {
                ActivationPager(
                    moments = listOf(
                        moment(SetupWizardBlock.BASICS, "Tú", onEdit = { }),
                        moment(SetupWizardBlock.TRAINING, "Tu programa", onEdit = { edits++ }),
                    ),
                    modifier = Modifier.fillMaxSize(),
                    initialPage = 1,
                )
            }
        }
        rule.onNodeWithTag("setup-activation-edit-TRAINING").performClick()
        rule.waitForIdle()
        assertEquals(1, edits)
    }

    @Test
    fun activationButtonKeepsItsTagAndRunsOnce() {
        var clicks = 0
        rule.setContent {
            CompositionLocalProvider(LocalWizardReducedMotion provides true) {
                ActivationButton(enabled = true, busy = false, onClick = { clicks++ })
            }
        }
        rule.onNodeWithTag("setup-continue").performClick()
        rule.waitForIdle()
        assertEquals(1, clicks)
    }

    @Test
    fun activationWithTheConfirmationUncheckedDoesNotPass() {
        var activations = 0
        rule.setContent {
            CompositionLocalProvider(LocalWizardReducedMotion provides true) {
                ActivationFooter(
                    state = SetupWizardState(draft = SetupWizardDraft(), requiresActivationConfirmation = true),
                    notices = emptyList(),
                    // Sin la casilla marcada, el anfitrión (canConfirmStep) no deja el botón encendido.
                    activateEnabled = false,
                    onConfirmActivation = { },
                    onConfirmFixedRecipe = { },
                    onActivate = { activations++ },
                )
            }
        }
        rule.onNodeWithText("Confirmo la activación").assertIsOff()
        rule.onNodeWithTag("setup-continue").assertIsNotEnabled().performClick()
        rule.waitForIdle()
        assertEquals(0, activations)
    }

    @Test
    fun activationNoticesSpeakPlainlyAndNeverLinkToHowItWorks() {
        val texts = activationNoticesOf(
            state = SetupWizardState(draft = SetupWizardDraft()),
            nutritionPage = true,
            ringsPage = true,
        ).map { it.text }
        // Sin líneas de estado internas ni el enlace «Ver cómo funciona».
        assertTrue(texts.none { it.contains("Ver cómo funciona") || it.startsWith("Estado") || it.contains("Sin datos") })
        assertTrue("Aún falta tu plan de comidas." in texts)
        assertTrue("Tus RINGS se ajustarán con tus primeros entrenos." in texts)
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
