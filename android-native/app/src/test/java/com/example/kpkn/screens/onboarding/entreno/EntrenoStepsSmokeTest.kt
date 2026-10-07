package com.example.kpkn.screens.onboarding.entreno

import android.app.Application
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.test.assertIsEnabled
import androidx.compose.ui.test.assertIsNotEnabled
import androidx.compose.ui.test.assertIsNotSelected
import androidx.compose.ui.test.assertIsOff
import androidx.compose.ui.test.assertIsOn
import androidx.compose.ui.test.assertIsSelected
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.onNodeWithText
import androidx.lifecycle.SavedStateHandle
import androidx.lifecycle.ViewModelStore
import androidx.test.core.app.ApplicationProvider
import com.example.kpkn.data.models.NutritionPlan
import com.example.kpkn.data.models.Settings
import com.example.kpkn.data.onboarding.SetupDraft
import com.example.kpkn.data.onboarding.SetupDraftCandidate
import com.example.kpkn.domain.onboarding.CapabilityLevel
import com.example.kpkn.domain.onboarding.CapabilitySkill
import com.example.kpkn.domain.onboarding.EquipmentSymbolId
import com.example.kpkn.domain.onboarding.LiftMark
import com.example.kpkn.domain.onboarding.MuscleSymbol
import com.example.kpkn.domain.onboarding.TrainingGoalProfile
import com.example.kpkn.domain.onboarding.TrainingPlace
import com.example.kpkn.screens.onboarding.SetupExperience
import com.example.kpkn.screens.onboarding.SetupPreview
import com.example.kpkn.screens.onboarding.SetupWizardDraft
import com.example.kpkn.screens.onboarding.SetupWizardEnvironment
import com.example.kpkn.screens.onboarding.SetupWizardMaterializer
import com.example.kpkn.screens.onboarding.SetupWizardPersistence
import com.example.kpkn.screens.onboarding.SetupWizardState
import com.example.kpkn.screens.onboarding.SetupWizardViewModel
import com.example.kpkn.screens.onboarding.withCapability
import com.example.kpkn.screens.onboarding.withFreshestDay
import com.example.kpkn.screens.onboarding.withGoalProfile
import com.example.kpkn.screens.onboarding.withLiftMark
import com.example.kpkn.screens.onboarding.withMaterialToggled
import com.example.kpkn.screens.onboarding.withMuscleToggled
import com.example.kpkn.screens.onboarding.withPlaces
import com.example.kpkn.screens.onboarding.withSessionMinutes
import com.example.kpkn.screens.onboarding.withWeekdays
import org.junit.After
import org.junit.Before
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

/**
 * Los once controles provisionales de Entreno v2 componen sin romperse (dentro de un contenedor con scroll, como en la
 * página larga del wizard, es decir, con alto sin límite) y reflejan lo que el borrador trae: lo elegido sale
 * marcado, lo que el material no permite sale apagado y los textos de apoyo salen de los datos. No ejecuta el
 * ViewModel (no se inicializa): las escrituras las cubren las pruebas de los reductores y del recorrido.
 */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [34], application = android.app.Application::class, qualifiers = "w411dp-h891dp-xxhdpi")
class EntrenoStepsSmokeTest {

    @get:Rule
    val rule = createComposeRule()

    private val store = ViewModelStore()
    private lateinit var vm: SetupWizardViewModel

    @Before
    fun setUp() {
        val app = ApplicationProvider.getApplicationContext<Application>()
        vm = SetupWizardViewModel(
            application = app,
            savedStateHandle = SavedStateHandle(),
            persistence = NoPersistence,
            environment = StubEnvironment,
            materializeOverride = SetupWizardMaterializer { SetupPreview(null, null) },
        ).also { store.put("entreno-smoke-vm", it) }
    }

    @After
    fun tearDown() {
        store.clear()
    }

    private fun show(draft: SetupWizardDraft, step: @Composable (SetupWizardState, SetupWizardViewModel) -> Unit) {
        val state = SetupWizardState(draft = draft)
        rule.setContent {
            Column(Modifier.verticalScroll(rememberScrollState())) { step(state, vm) }
        }
        rule.waitForIdle()
    }

    private val gym = setOf(TrainingPlace.GYM)

    // ── EQUIPMENT ──────────────────────────────────────────────────────────────

    @Test
    fun placesShowTheThreePlacesAndWhatWasChosen() {
        show(SetupWizardDraft().withPlaces(setOf(TrainingPlace.GYM, TrainingPlace.HOME))) { s, v -> EntrenoPlacesStep(s, v) }
        rule.onNodeWithTag("entreno-place-gym").assertIsOn()
        rule.onNodeWithTag("entreno-place-home").assertIsOn()
        rule.onNodeWithTag("entreno-place-public").assertIsOff()
        rule.onNodeWithText("En espacios públicos").assertExists()
        rule.onNodeWithText("Después podrás elegir dónde entrenas cada día.").assertExists()
    }

    @Test
    fun placesWithNothingChosenInviteToChooseOne() {
        show(SetupWizardDraft()) { s, v -> EntrenoPlacesStep(s, v) }
        rule.onNodeWithText("Elige al menos un lugar.").assertExists()
        rule.onNodeWithTag("entreno-place-gym").assertIsOff()
    }

    // ── AVAILABILITY ───────────────────────────────────────────────────────────

    @Test
    fun theGymComesWithItsUsualMaterialMarkedAndTheRestOff() {
        show(SetupWizardDraft().withPlaces(gym)) { s, v -> EntrenoMaterialStep(s, v) }
        rule.onNodeWithTag("entreno-symbol-BARBELL").assertIsOn()
        rule.onNodeWithTag("entreno-symbol-RACK").assertIsOn()
        rule.onNodeWithTag("entreno-symbol-RINGS").assertIsOff()
        rule.onNodeWithTag("entreno-symbol-BODYWEIGHT_ONLY").assertIsNotSelected()
        rule.onNodeWithText("En el gimnasio ya contamos con lo habitual. Desmarca lo que no quieras usar.").assertExists()
    }

    @Test
    fun aParkOnlyOffersWhatAParkCanHaveAndSaysToMarkWhatYouHave() {
        show(SetupWizardDraft().withPlaces(setOf(TrainingPlace.PUBLIC)).withMaterialToggled(EquipmentSymbolId.BANDS)) { s, v ->
            EntrenoMaterialStep(s, v)
        }
        rule.onNodeWithTag("entreno-symbol-PULL_UP_BAR").assertIsOn()
        rule.onNodeWithTag("entreno-symbol-BANDS").assertIsOn()
        rule.onNodeWithTag("entreno-symbol-MACHINES").assertDoesNotExist()
        rule.onNodeWithText("Marca solo lo que tienes a mano.").assertExists()
    }

    @Test
    fun withoutPlacesTheMaterialAsksForThemFirst() {
        show(SetupWizardDraft()) { s, v -> EntrenoMaterialStep(s, v) }
        rule.onNodeWithText("Elige primero dónde entrenas.").assertExists()
    }

    // ── GOAL ───────────────────────────────────────────────────────────────────

    @Test
    fun disciplinesThatTheMaterialDoesNotAllowAreOffAndSayWhatIsMissing() {
        show(SetupWizardDraft().withPlaces(setOf(TrainingPlace.HOME)).withGoalProfile(TrainingGoalProfile.FUNCTIONAL_HEALTH)) { s, v ->
            EntrenoGoalStep(s, v)
        }
        rule.onNodeWithTag("entreno-goal-functional_health").assertIsSelected()
        rule.onNodeWithTag("entreno-goal-strength_muscle").assertIsEnabled()
        rule.onNodeWithTag("entreno-goal-powerlifting").assertIsNotEnabled()
        rule.onNodeWithText("Necesita barra, rack y banco.").assertExists()
        rule.onNodeWithText("Dependen de tu material").assertExists()
        rule.onNodeWithTag("entreno-goal-change-material").assertExists()
    }

    @Test
    fun withAGymEveryDisciplineIsAvailableAndThereIsNoWayOutToTheMaterial() {
        show(SetupWizardDraft().withPlaces(gym).withGoalProfile(TrainingGoalProfile.POWERLIFTING)) { s, v ->
            EntrenoGoalStep(s, v)
        }
        rule.onNodeWithTag("entreno-goal-powerlifting").assertIsSelected().assertIsEnabled()
        rule.onNodeWithTag("entreno-goal-calisthenics").assertIsEnabled()
        rule.onNodeWithTag("entreno-goal-change-material").assertDoesNotExist()
    }

    // ── FRESH_DAY / WEEKDAYS ───────────────────────────────────────────────────

    @Test
    fun theFreshDayMarksTheChosenDayAndExplainsItIsTheStartOfTheWeek() {
        show(SetupWizardDraft().withFreshestDay(4)) { s, v -> EntrenoFreshDayStep(s, v) }
        rule.onNodeWithTag("entreno-fresh-day-4").assertIsSelected()
        rule.onNodeWithTag("entreno-fresh-day-1").assertIsNotSelected()
        rule.onNodeWithText("También será el primer día de tu semana.").assertExists()
    }

    @Test
    fun theWeekShowsTheChosenDaysTheStartAndPlacesPerDayOnlyWithSeveralPlaces() {
        val oneGym = SetupWizardDraft().withPlaces(gym).withFreshestDay(2).withWeekdays(setOf(2, 4, 6))
        show(oneGym) { s, v -> EntrenoWeekdaysStep(s, v) }
        rule.onNodeWithTag("entreno-weekday-2").assertIsOn()
        rule.onNodeWithTag("entreno-weekday-3").assertIsOff()
        rule.onNodeWithText("3 días por semana").assertExists()
        rule.onNodeWithText("La semana empieza el martes").assertExists()
        rule.onNodeWithTag("entreno-week-start-2").assertIsSelected()
        rule.onNodeWithTag("entreno-day-place-2-gym").assertDoesNotExist()
    }

    @Test
    fun withTwoPlacesEachTrainingDayCanChooseWhereItIs() {
        val two = SetupWizardDraft().withPlaces(setOf(TrainingPlace.GYM, TrainingPlace.HOME))
            .withFreshestDay(1).withWeekdays(setOf(1, 3))
        show(two) { s, v -> EntrenoWeekdaysStep(s, v) }
        rule.onNodeWithText("¿Dónde entrenas ese día?").assertExists()
        // Por defecto, el primero de la lista (gimnasio).
        rule.onNodeWithTag("entreno-day-place-1-gym").assertIsSelected()
        rule.onNodeWithTag("entreno-day-place-1-home").assertIsNotSelected()
        rule.onNodeWithTag("entreno-day-place-3-home").assertExists()
    }

    // ── SESSION_TIME ───────────────────────────────────────────────────────────

    @Test
    fun theSessionTimeReadsInMinutesHoursAndGivesItsHint() {
        show(SetupWizardDraft().withSessionMinutes(75)) { s, v -> EntrenoSessionTimeStep(s, v) }
        rule.onNodeWithTag("entreno-session-minutes").assertExists()
        rule.onNodeWithText("75 min").assertExists()
        rule.onNodeWithText("1 h 15 min").assertExists()
        rule.onNodeWithTag("entreno-session-shortcut-60").assertIsNotSelected()
    }

    @Test
    fun withoutATimeTheDialInvitesToChooseAndSelectsNoShortcut() {
        show(SetupWizardDraft()) { s, v -> EntrenoSessionTimeStep(s, v) }
        rule.onNodeWithText("Elige un tiempo").assertExists()
        rule.onNodeWithTag("entreno-session-shortcut-30").assertIsNotSelected()
    }

    @Test
    fun aShortSessionExplainsItGoesToTheEssential() {
        show(SetupWizardDraft().withSessionMinutes(30)) { s, v -> EntrenoSessionTimeStep(s, v) }
        rule.onNodeWithTag("entreno-session-shortcut-30").assertIsSelected()
        rule.onNodeWithText("Con poco tiempo vamos a lo esencial.").assertExists()
    }

    // ── CAPABILITIES ───────────────────────────────────────────────────────────

    @Test
    fun capabilitiesOnlyOfferTheExercisesTheMaterialAllowsAndMarkTheLevel() {
        val draft = SetupWizardDraft().withPlaces(setOf(TrainingPlace.PUBLIC))
            .withGoalProfile(TrainingGoalProfile.CALISTHENICS)
            .withCapability(CapabilitySkill.PULL_UP, CapabilityLevel.SOME)
        show(draft) { s, v -> EntrenoCapabilitiesStep(s, v) }
        rule.onNodeWithTag("entreno-capability-PULL_UP-SOME").assertIsSelected()
        rule.onNodeWithTag("entreno-capability-PULL_UP-NONE").assertIsNotSelected()
        rule.onNodeWithTag("entreno-capability-PUSH_UP-MANY").assertExists()
        rule.onNodeWithText("Sin presión: siempre podrás cambiarlo.").assertExists()
    }

    // ── PRIORITIES ─────────────────────────────────────────────────────────────

    @Test
    fun theMusclesMarkTheChosenOnesAndSkipIsAlwaysThere() {
        show(SetupWizardDraft().withMuscleToggled(MuscleSymbol.CHEST).withMuscleToggled(MuscleSymbol.FOREARMS)) { s, v ->
            EntrenoMusclesStep(s, v)
        }
        rule.onNodeWithTag("entreno-muscle-CHEST").assertIsOn()
        rule.onNodeWithTag("entreno-muscle-FOREARMS").assertIsOn()
        rule.onNodeWithTag("entreno-muscle-BACK").assertIsOff()
        rule.onNodeWithTag("entreno-muscles-skip").assertExists()
        // Lo elegido sin tocar el paso viene rotulado como sugerencia (nada se confirma solo).
        rule.onNodeWithText("Sugerido").assertExists()
    }

    @Test
    fun theSuggestionOfTheProfileIsLabelledAsSuggested() {
        show(SetupWizardDraft().withPlaces(gym).withGoalProfile(TrainingGoalProfile.ARMWRESTLING)) { s, v ->
            EntrenoMusclesStep(s, v)
        }
        rule.onNodeWithTag("entreno-muscle-FOREARMS").assertIsOn()
        rule.onNodeWithTag("entreno-muscle-BICEPS").assertIsOn()
        rule.onNodeWithText("Sugerido").assertExists()
    }

    // ── TRAINING_MAX ───────────────────────────────────────────────────────────

    @Test
    fun marksAskForTheLiftsOfTheGoalWithAUnitSelector() {
        val draft = SetupWizardDraft().withPlaces(gym)
            .withGoalProfile(TrainingGoalProfile.POWERLIFTING)
            .copy(experience = SetupExperience.ADVANCED)
            .withLiftMark(LiftMark.SQUAT, 140.0)
        show(draft) { s, v -> EntrenoMarksStep(s, v) }
        rule.onNodeWithTag("entreno-marks-unit-kg").assertIsSelected()
        rule.onNodeWithTag("entreno-marks-unit-lb").assertIsNotSelected()
        rule.onNodeWithTag("entreno-mark-SQUAT").assertExists()
        rule.onNodeWithTag("entreno-mark-BENCH").assertExists()
        rule.onNodeWithTag("entreno-mark-unknown-DEADLIFT").assertExists()
        rule.onNodeWithText("Tu mejor levantamiento de una repetición, o una estimación.").assertExists()
    }

    // ── PLAN / WEEK_LAYOUT ─────────────────────────────────────────────────────

    @Test
    fun theWeekLayoutAndThePlanComposeWithoutAPreparedProgram() {
        show(SetupWizardDraft().withPlaces(gym)) { s, v ->
            EntrenoWeekLayoutStep(s, v)
        }
    }

    private object NoPersistence : SetupWizardPersistence {
        override suspend fun load(draftId: String): SetupDraft? = null
        override suspend fun save(
            draftId: String,
            payloadJson: String,
            revision: Long,
            catalogRevision: String?,
        ): SetupDraft = SetupDraft(draftId, payloadJson, revision, catalogRevision, 0L)

        override suspend fun discard(draftId: String) = Unit
        override suspend fun listRecoverable(): List<SetupDraftCandidate> = emptyList()
    }

    private object StubEnvironment : SetupWizardEnvironment {
        override val settings: Settings = Settings()
        override suspend fun awaitReady() = Unit
        override fun activeProgramId(): String? = null
        override fun activeNutritionPlanId(): String? = null
        override fun activeNutritionPlan(): NutritionPlan? = null
        override fun nutritionPlan(id: String): NutritionPlan? = null
        override fun hasInitialRecoveryEvidence(): Boolean = false
        override suspend fun refreshBodyProgress() = Unit
    }
}
