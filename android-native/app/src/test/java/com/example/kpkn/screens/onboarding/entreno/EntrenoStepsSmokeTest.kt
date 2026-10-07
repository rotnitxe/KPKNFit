package com.example.kpkn.screens.onboarding.entreno

import android.app.Application
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.semantics.SemanticsActions
import androidx.compose.ui.semantics.SemanticsProperties
import androidx.compose.ui.semantics.getOrNull
import androidx.compose.ui.test.assertContentDescriptionEquals
import androidx.compose.ui.test.assertCountEquals
import androidx.compose.ui.test.assertHasClickAction
import androidx.compose.ui.test.assertIsNotSelected
import androidx.compose.ui.test.assertIsOff
import androidx.compose.ui.test.assertIsOn
import androidx.compose.ui.test.assertIsSelected
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onAllNodesWithText
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
import com.example.kpkn.domain.onboarding.SetupStepId
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
import com.example.kpkn.screens.onboarding.design.entreno.SESSION_DIAL_TAG
import com.example.kpkn.screens.onboarding.touchStep
import com.example.kpkn.screens.onboarding.withCapability
import com.example.kpkn.screens.onboarding.withDayPlace
import com.example.kpkn.screens.onboarding.withFreshestDay
import com.example.kpkn.screens.onboarding.withGoalProfile
import com.example.kpkn.screens.onboarding.withLiftMark
import com.example.kpkn.screens.onboarding.withMaterial
import com.example.kpkn.screens.onboarding.withMaterialToggled
import com.example.kpkn.screens.onboarding.withMuscleToggled
import com.example.kpkn.screens.onboarding.withPlaces
import com.example.kpkn.screens.onboarding.withSessionMinutes
import com.example.kpkn.screens.onboarding.withWeekdays
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

/**
 * Los pasos de Entreno v2 componen sin romperse (dentro de un contenedor con scroll, como en la página larga del wizard,
 * es decir, con alto sin límite) y reflejan lo que el borrador trae: lo elegido sale marcado, lo que el material no
 * permite sale bloqueado con su razón y los textos de apoyo salen de los datos. Los seis primeros (lugares, material,
 * objetivo, día con más energía, días y tiempo) llevan ya los símbolos dibujados y se buscan por sus marcas de prueba
 * (`setup-place-*`, `setup-equipment-*`, `setup-goal-*`, `setup-freshday-*`, `setup-weekday-*`, `setup-sessiontime-*`);
 * el resto sigue con controles provisionales. No ejecuta el ViewModel (no se inicializa): las escrituras las cubren las
 * pruebas de los reductores y del recorrido, y la comprobación con toques de verdad va en el arnés de depuración.
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
    fun placesShowTheThreeScenesAndWhatWasChosen() {
        show(SetupWizardDraft().withPlaces(setOf(TrainingPlace.GYM, TrainingPlace.HOME))) { s, v -> EntrenoPlacesStep(s, v) }
        rule.onNodeWithTag("setup-place-GYM").assertIsOn()
        rule.onNodeWithTag("setup-place-HOME").assertIsOn()
        rule.onNodeWithTag("setup-place-PUBLIC").assertIsOff()
        rule.onNodeWithText("Después podrás elegir dónde entrenas cada día.").assertExists()
    }

    @Test
    fun placesWithNothingChosenInviteToChooseOne() {
        show(SetupWizardDraft()) { s, v -> EntrenoPlacesStep(s, v) }
        rule.onNodeWithText("Elige al menos un lugar.").assertExists()
        rule.onNodeWithTag("setup-place-GYM").assertIsOff()
    }

    @Test
    fun aSinglePlaceNeedsNoNote() {
        show(SetupWizardDraft().withPlaces(setOf(TrainingPlace.HOME))) { s, v -> EntrenoPlacesStep(s, v) }
        rule.onNodeWithTag("setup-place-HOME").assertIsOn()
        rule.onNodeWithText("Elige al menos un lugar.").assertDoesNotExist()
        rule.onNodeWithText("Después podrás elegir dónde entrenas cada día.").assertDoesNotExist()
    }

    // ── AVAILABILITY ───────────────────────────────────────────────────────────

    @Test
    fun theGymComesWithItsUsualMaterialMarkedAndTheRestOff() {
        show(SetupWizardDraft().withPlaces(gym)) { s, v -> EntrenoMaterialStep(s, v) }
        rule.onNodeWithTag("setup-equipment-BARBELL").assertIsOn()
        rule.onNodeWithTag("setup-equipment-RACK").assertIsOn()
        rule.onNodeWithTag("setup-equipment-RINGS").assertIsOff()
        rule.onNodeWithTag("setup-equipment-BODYWEIGHT_ONLY").assertIsOff()
        rule.onNodeWithText("En el gimnasio ya contamos con lo habitual. Desmarca lo que no quieras usar.").assertExists()
    }

    @Test
    fun aParkOnlyOffersWhatAParkCanHaveAndSaysToMarkWhatYouHave() {
        show(SetupWizardDraft().withPlaces(setOf(TrainingPlace.PUBLIC)).withMaterialToggled(EquipmentSymbolId.BANDS)) { s, v ->
            EntrenoMaterialStep(s, v)
        }
        rule.onNodeWithTag("setup-equipment-PULL_UP_BAR").assertIsOn()
        rule.onNodeWithTag("setup-equipment-BANDS").assertIsOn()
        rule.onNodeWithTag("setup-equipment-MACHINES").assertDoesNotExist()
        rule.onNodeWithText("Marca solo lo que tienes a mano.").assertExists()
    }

    @Test
    fun bodyweightOnlyIsTheOnlyOneMarkedAndSaysYouTrainWithYourBody() {
        show(SetupWizardDraft().withPlaces(setOf(TrainingPlace.HOME)).withMaterial(setOf(EquipmentSymbolId.BODYWEIGHT_ONLY))) { s, v ->
            EntrenoMaterialStep(s, v)
        }
        rule.onNodeWithTag("setup-equipment-BODYWEIGHT_ONLY").assertIsOn()
        rule.onNodeWithTag("setup-equipment-DUMBBELLS").assertIsOff()
        rule.onNodeWithText("Entrenas con tu cuerpo.").assertExists()
        rule.onNodeWithText("Marca solo lo que tienes a mano.").assertDoesNotExist()
    }

    @Test
    fun withoutPlacesTheMaterialAsksForThemFirst() {
        show(SetupWizardDraft()) { s, v -> EntrenoMaterialStep(s, v) }
        rule.onNodeWithText("Elige primero dónde entrenas.").assertExists()
    }

    // ── GOAL ───────────────────────────────────────────────────────────────────

    @Test
    fun disciplinesThatTheMaterialDoesNotAllowAreBlockedAndSayWhatIsMissing() {
        show(SetupWizardDraft().withPlaces(setOf(TrainingPlace.HOME)).withGoalProfile(TrainingGoalProfile.FUNCTIONAL_HEALTH)) { s, v ->
            EntrenoGoalStep(s, v)
        }
        rule.onNodeWithTag("setup-goal-FUNCTIONAL_HEALTH").assertIsSelected()
        rule.onNodeWithTag("setup-goal-STRENGTH_MUSCLE").assertIsNotSelected()
        // Bloqueado: el estado y la acción llegan a TalkBack y la razón se ve en la propia fila.
        val config = rule.onNodeWithTag("setup-goal-POWERLIFTING").fetchSemanticsNode().config
        assertEquals("No disponible. Necesita barra, rack y banco.", config[SemanticsProperties.StateDescription])
        assertEquals("Cambiar mi material", config[SemanticsActions.OnClick].label)
        rule.onNodeWithText("Necesita barra, rack y banco.", useUnmergedTree = true).assertExists()
        rule.onNodeWithText("Dependen de tu material.").assertExists()
    }

    @Test
    fun withAGymEveryDisciplineIsAvailable() {
        show(SetupWizardDraft().withPlaces(gym).withGoalProfile(TrainingGoalProfile.POWERLIFTING)) { s, v ->
            EntrenoGoalStep(s, v)
        }
        rule.onNodeWithTag("setup-goal-POWERLIFTING").assertIsSelected()
        for (profile in TrainingGoalProfile.entries) {
            val state = rule.onNodeWithTag("setup-goal-${profile.name}").fetchSemanticsNode().config
                .getOrNull(SemanticsProperties.StateDescription).orEmpty()
            assertFalse("${profile.name} no debería estar bloqueado con gimnasio", state.startsWith("No disponible"))
        }
    }

    @Test
    fun theBlockedReasonsComeFromTheMaterialAndNeverBlockTheGeneralProfiles() {
        val gymAvailability = SetupWizardDraft().withPlaces(gym).trainingOptions.availability
        assertTrue(goalBlockedReasons(gymAvailability).isEmpty())
        val bare = SetupWizardDraft().withPlaces(setOf(TrainingPlace.HOME)).trainingOptions.availability
        val blocked = goalBlockedReasons(bare)
        assertEquals("Necesita barra y rack.", blocked[TrainingGoalProfile.WEIGHTLIFTING])
        TrainingGoalProfile.general.forEach { assertNull(blocked[it]) }
    }

    // ── FRESH_DAY / WEEKDAYS ───────────────────────────────────────────────────

    @Test
    fun theFreshDayMarksTheChosenDayAndExplainsItIsTheStartOfTheWeek() {
        show(SetupWizardDraft().withFreshestDay(4)) { s, v -> EntrenoFreshDayStep(s, v) }
        rule.onNodeWithTag("setup-freshday-4").assertIsSelected()
        rule.onNodeWithTag("setup-freshday-1").assertIsNotSelected()
        rule.onNodeWithText("También será el primer día de tu semana.").assertExists()
    }

    @Test
    fun withoutAFreshDayNothingIsSelectedAndThereIsNoNote() {
        show(SetupWizardDraft()) { s, v -> EntrenoFreshDayStep(s, v) }
        for (day in 1..7) rule.onNodeWithTag("setup-freshday-$day").assertIsNotSelected()
        rule.onNodeWithText("También será el primer día de tu semana.").assertDoesNotExist()
    }

    @Test
    fun theWeekShowsTheChosenDaysTheStartAndPlacesPerDayOnlyWithSeveralPlaces() {
        val oneGym = SetupWizardDraft().withPlaces(gym).withFreshestDay(2).withWeekdays(setOf(2, 4, 6))
        show(oneGym) { s, v -> EntrenoWeekdaysStep(s, v) }
        rule.onNodeWithTag("setup-weekday-2").assertIsOn()
        rule.onNodeWithTag("setup-weekday-3").assertIsOff()
        rule.onNodeWithTag("setup-week-count").assertContentDescriptionEquals("3 días por semana")
        // La semana empieza el día con más energía mientras no se mueva.
        rule.onNodeWithTag("setup-weekstart").assertContentDescriptionEquals("La semana empieza el martes")
        // El martes es el día más fuerte (lleva su marca) y con un solo lugar no hay lugar por día.
        rule.onNodeWithTag("setup-weekday-2").assertContentDescriptionEquals("Martes, elegido, tu sesión más fuerte")
        rule.onNodeWithTag("setup-weekplace-2").assertDoesNotExist()
    }

    @Test
    fun withTwoPlacesEachTrainingDayCanChooseWhereItIs() {
        val two = SetupWizardDraft().withPlaces(setOf(TrainingPlace.GYM, TrainingPlace.HOME))
            .withFreshestDay(1).withWeekdays(setOf(1, 3)).withDayPlace(3, TrainingPlace.HOME)
        show(two) { s, v -> EntrenoWeekdaysStep(s, v) }
        rule.onNodeWithText("¿Dónde entrenas ese día?").assertExists()
        // Por defecto, el primero de la lista (gimnasio); el miércoles se cambió a casa.
        rule.onNodeWithTag("setup-weekplace-1").assertContentDescriptionEquals("Lugar del lunes: Gimnasio")
        rule.onNodeWithTag("setup-weekplace-3").assertContentDescriptionEquals("Lugar del miércoles: En casa")
    }

    // ── SESSION_TIME ───────────────────────────────────────────────────────────

    @Test
    fun theSessionDialReadsTheDeclaredMinutesAndSelectsNoShortcutThatDoesNotMatch() {
        show(SetupWizardDraft().withSessionMinutes(75)) { s, v -> EntrenoSessionTimeStep(s, v) }
        val config = rule.onNodeWithTag(SESSION_DIAL_TAG).fetchSemanticsNode().config
        assertEquals("1 hora y 15 minutos", config[SemanticsProperties.StateDescription])
        rule.onNodeWithTag("setup-sessiontime-60").assertIsNotSelected()
        rule.onNodeWithTag("setup-sessiontime-90").assertIsNotSelected()
    }

    @Test
    fun withoutATimeTheDialStartsAtSixtyAndGivesNoHint() {
        show(SetupWizardDraft()) { s, v -> EntrenoSessionTimeStep(s, v) }
        val config = rule.onNodeWithTag(SESSION_DIAL_TAG).fetchSemanticsNode().config
        assertEquals("1 hora", config[SemanticsProperties.StateDescription])
        rule.onNodeWithText("Con poco tiempo vamos a lo esencial.").assertDoesNotExist()
    }

    @Test
    fun aShortSessionExplainsItGoesToTheEssential() {
        show(SetupWizardDraft().withSessionMinutes(30)) { s, v -> EntrenoSessionTimeStep(s, v) }
        rule.onNodeWithTag("setup-sessiontime-30").assertIsSelected()
        rule.onNodeWithText("Con poco tiempo vamos a lo esencial.").assertExists()
    }

    @Test
    fun aLongSessionExplainsItAddsWarmupsAndLongerRests() {
        show(SetupWizardDraft().withSessionMinutes(120)) { s, v -> EntrenoSessionTimeStep(s, v) }
        rule.onNodeWithTag("setup-sessiontime-120").assertIsSelected()
        rule.onNodeWithText("Con más tiempo sumamos aproximaciones, movilidad y descansos más largos.").assertExists()
    }

    // ── CAPABILITIES ───────────────────────────────────────────────────────────

    @Test
    fun capabilitiesOnlyOfferTheExercisesTheMaterialAllowsAndMarkTheLevel() {
        val draft = SetupWizardDraft().withPlaces(setOf(TrainingPlace.PUBLIC))
            .withGoalProfile(TrainingGoalProfile.CALISTHENICS)
            .withCapability(CapabilitySkill.PULL_UP, CapabilityLevel.SOME)
        show(draft) { s, v -> EntrenoCapabilitiesStep(s, v) }
        rule.onNodeWithTag("setup-capability-PULL_UP-SOME").assertIsSelected()
        rule.onNodeWithTag("setup-capability-PULL_UP-NONE").assertIsNotSelected()
        // Cada ejercicio que se ofrece lleva su símbolo y sus tres niveles; sin respuesta no hay segmento elegido.
        rule.onNodeWithTag("setup-capability-PUSH_UP").assertExists()
        rule.onNodeWithTag("setup-capability-PUSH_UP-MANY").assertIsNotSelected()
        rule.onNodeWithText("Sin presión: siempre podrás cambiarlo.").assertExists()
    }

    @Test
    fun theCapabilitiesTheMaterialDoesNotAllowAreNotOffered() {
        // Sin material alguno no hay barra de dominadas ni paralelas: quedan las flexiones y la sentadilla a una pierna.
        val bare = SetupWizardDraft().withPlaces(setOf(TrainingPlace.HOME))
            .withMaterial(setOf(EquipmentSymbolId.BODYWEIGHT_ONLY))
            .withGoalProfile(TrainingGoalProfile.CALISTHENICS)
        show(bare) { s, v -> EntrenoCapabilitiesStep(s, v) }
        rule.onNodeWithTag("setup-capability-PUSH_UP").assertExists()
        rule.onNodeWithTag("setup-capability-PISTOL_SQUAT").assertExists()
        rule.onNodeWithTag("setup-capability-PULL_UP").assertDoesNotExist()
        rule.onNodeWithTag("setup-capability-DIP").assertDoesNotExist()
    }

    // ── PRIORITIES ─────────────────────────────────────────────────────────────

    @Test
    fun theMusclesMarkTheChosenOnesAndSkipIsAlwaysThere() {
        show(SetupWizardDraft().withMuscleToggled(MuscleSymbol.CHEST).withMuscleToggled(MuscleSymbol.FOREARMS)) { s, v ->
            EntrenoMusclesStep(s, v)
        }
        rule.onNodeWithTag("setup-muscle-CHEST").assertIsOn()
        rule.onNodeWithTag("setup-muscle-FOREARMS").assertIsOn()
        rule.onNodeWithTag("setup-muscle-BACK").assertIsOff()
        rule.onNodeWithTag(MUSCLES_SKIP_TAG).assertExists().assertHasClickAction()
        rule.onNodeWithText("Omitir").assertExists()
    }

    @Test
    fun theSuggestionOfTheProfileIsLabelledAsSuggested() {
        show(SetupWizardDraft().withPlaces(gym).withGoalProfile(TrainingGoalProfile.ARMWRESTLING)) { s, v ->
            EntrenoMusclesStep(s, v)
        }
        rule.onNodeWithTag("setup-muscle-FOREARMS").assertIsOn()
        rule.onNodeWithTag("setup-muscle-BICEPS").assertIsOn()
        rule.onAllNodesWithText("Sugerido").assertCountEquals(2)
    }

    @Test
    fun whatThePersonAlreadyDeclaredIsNeverLabelledAsSuggested() {
        // Con el paso ya declarado al entrar (se vuelve a editar) lo elegido es suyo: ningún músculo lleva «Sugerido».
        val declared = SetupWizardDraft().withPlaces(gym).withGoalProfile(TrainingGoalProfile.ARMWRESTLING)
            .touchStep(SetupStepId.PRIORITIES)
        show(declared) { s, v -> EntrenoMusclesStep(s, v) }
        rule.onAllNodesWithText("Sugerido").assertCountEquals(0)
    }

    @Test
    fun withFiveMusclesTheGridSaysTheCapAndTheTapOnAnotherOneChangesNothing() {
        val five = setOf(MuscleSymbol.CHEST, MuscleSymbol.BACK, MuscleSymbol.SHOULDERS, MuscleSymbol.BICEPS, MuscleSymbol.TRICEPS)
        val draft = five.fold(SetupWizardDraft()) { d, muscle -> d.withMuscleToggled(muscle) }
        show(draft) { s, v -> EntrenoMusclesStep(s, v) }
        rule.onNodeWithText("Máximo 5 músculos.").assertExists()
        assertEquals("Máximo de músculos alcanzado", rule.onNodeWithTag("setup-muscle-ABS").fetchSemanticsNode().config[SemanticsProperties.StateDescription])
    }

    // ── TRAINING_MAX ───────────────────────────────────────────────────────────

    @Test
    fun marksAskForTheLiftsOfTheGoalWithAUnitSwitchAndTheDeclaredValue() {
        val draft = SetupWizardDraft().withPlaces(gym)
            .withGoalProfile(TrainingGoalProfile.POWERLIFTING)
            .copy(experience = SetupExperience.ADVANCED)
            .withLiftMark(LiftMark.SQUAT, 140.0)
        show(draft) { s, v -> EntrenoMarksStep(s, v) }
        // El conmutador de unidad parte en kilos y la sentadilla, ya declarada, dice su marca.
        assertEquals("Kilogramos", rule.onNodeWithTag("setup-mark-unit").fetchSemanticsNode().config[SemanticsProperties.StateDescription])
        rule.onNodeWithTag("setup-mark-SQUAT").assertExists()
        rule.onNodeWithTag("setup-mark-BENCH").assertExists()
        rule.onNodeWithTag("setup-mark-DEADLIFT").assertExists()
        rule.onNodeWithText("140 kg").assertExists()
    }

    @Test
    fun theMarksUnitFollowsTheDraft() {
        val draft = SetupWizardDraft().withPlaces(gym)
            .withGoalProfile(TrainingGoalProfile.POWERLIFTING)
            .copy(experience = SetupExperience.ADVANCED, marksUnit = "lb")
            .withLiftMark(LiftMark.SQUAT, 100.0)
        show(draft) { s, v -> EntrenoMarksStep(s, v) }
        assertEquals("Libras", rule.onNodeWithTag("setup-mark-unit").fetchSemanticsNode().config[SemanticsProperties.StateDescription])
        rule.onNodeWithText("220,5 lb").assertExists()
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
