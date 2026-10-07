package com.example.kpkn.screens.onboarding

import android.app.Application
import androidx.lifecycle.SavedStateHandle
import androidx.lifecycle.ViewModelStore
import androidx.test.core.app.ApplicationProvider
import com.example.kpkn.data.db.KpknDatabase
import com.example.kpkn.data.db.toEntity
import com.example.kpkn.data.db.toSettings
import com.example.kpkn.data.models.EquipmentAvailability
import com.example.kpkn.data.models.EquipmentCategory
import com.example.kpkn.data.models.EquipmentInventory
import com.example.kpkn.data.models.NutritionPlan
import com.example.kpkn.data.models.PlateStock
import com.example.kpkn.data.models.Program
import com.example.kpkn.data.models.Settings
import com.example.kpkn.data.onboarding.SetupCommitCoordinator
import com.example.kpkn.data.onboarding.SetupCommitRequest
import com.example.kpkn.data.onboarding.SetupDraft
import com.example.kpkn.data.onboarding.SetupDraftCandidate
import com.example.kpkn.data.onboarding.SetupDraftScope
import com.example.kpkn.data.onboarding.SetupPatchField
import com.example.kpkn.data.onboarding.SetupSettingsPatch
import com.example.kpkn.domain.onboarding.SetupAnswerProvenance
import com.example.kpkn.domain.onboarding.SetupChangeDetector
import com.example.kpkn.domain.onboarding.SetupChangeSource
import com.example.kpkn.domain.onboarding.SetupPreviewKind
import com.example.kpkn.domain.onboarding.EquipmentSymbolId
import com.example.kpkn.domain.onboarding.EquipmentSymbols
import com.example.kpkn.domain.onboarding.RingsCompletion
import com.example.kpkn.domain.onboarding.SetupRingsMapping
import com.example.kpkn.domain.onboarding.SetupStepId
import com.example.kpkn.domain.onboarding.SetupStepProgress
import com.example.kpkn.domain.onboarding.SetupTrainingOptions
import com.example.kpkn.domain.onboarding.SetupValueState
import com.example.kpkn.domain.onboarding.TrainingPlace
import com.example.kpkn.domain.onboarding.WizChatQuestionId
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.test.StandardTestDispatcher
import kotlinx.coroutines.test.advanceUntilIdle
import kotlinx.coroutines.test.resetMain
import kotlinx.coroutines.test.runTest
import kotlinx.coroutines.test.setMain
import kotlinx.serialization.decodeFromString
import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.Json
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

@OptIn(ExperimentalCoroutinesApi::class)
@RunWith(RobolectricTestRunner::class)
@Config(manifest = Config.NONE, sdk = [34])
class SetupAvailabilitySeedTest {
    private val dispatcher = StandardTestDispatcher()
    private val viewModels = ViewModelStore()
    private lateinit var app: Application
    private lateinit var persistence: MemoryPersistence

    @Before
    fun setUp() {
        Dispatchers.setMain(dispatcher)
        app = ApplicationProvider.getApplicationContext()
        persistence = MemoryPersistence()
    }

    @After
    fun tearDown() {
        viewModels.clear()
        Dispatchers.resetMain()
    }

    /** El material tal como lo recibe el motor para estos símbolos en estos lugares (lo único que escribe el paso). */
    private fun material(places: Set<TrainingPlace>, vararg symbols: EquipmentSymbolId): EquipmentAvailability =
        EquipmentSymbols.availabilityOf(symbols.toSet(), places)

    private val gym = setOf(TrainingPlace.GYM)
    private val home = setOf(TrainingPlace.HOME)

    @Test
    fun fresh_draft_seeds_stored_categories_as_an_undeclared_suggestion() = runTest {
        val suggested = EquipmentAvailability(setOf(EquipmentCategory.BARBELL, EquipmentCategory.MACHINES))
        val vm = viewModel(Settings(equipmentAvailability = suggested))

        vm.initialize(SetupWizardMode.FULL, draftId = "fresh-availability")
        advanceUntilIdle()

        val draft = vm.state.value.draft
        assertEquals(suggested, draft.trainingOptions.availability)
        assertFalse(SetupStepId.HOME_EQUIPMENT in draft.declaredSteps)
        assertFalse(draft.isStepDeclared(SetupStepId.HOME_EQUIPMENT))
        // El paso que decide la disponibilidad ES AVAILABILITY: un prefill
        // sembrado desde Settings no es ni una declaración ni una respuesta.
        assertFalse(SetupStepId.AVAILABILITY in draft.declaredSteps)
        assertFalse(draft.isStepDeclared(SetupStepId.AVAILABILITY))
        assertNull(draft.stepProgress.answers[SetupStepId.AVAILABILITY])
        assertNull(draft.stepProgress.answers[SetupStepId.HOME_EQUIPMENT])
        assertNull(draft.stepSelections[SetupStepId.AVAILABILITY])
        assertEquals(
            SetupPatchField.Unchanged,
            buildSettingsPatch(vm, draft, Settings(equipmentAvailability = suggested)).equipmentAvailability,
        )
    }

    @Test
    fun settings_patch_keeps_unanswered_null_and_persists_explicit_none() {
        val vm = viewModel(Settings())
        val unanswered = SetupWizardDraft()
        val selectedNone = unanswered.copy(
            trainingOptions = SetupTrainingOptions(availability = EquipmentAvailability()),
        ).touchStep(SetupStepId.HOME_EQUIPMENT)

        assertEquals(
            SetupPatchField.Unchanged,
            buildSettingsPatch(vm, unanswered, Settings()).equipmentAvailability,
        )
        assertEquals(
            SetupPatchField.Set(EquipmentAvailability()),
            buildSettingsPatch(vm, selectedNone, Settings()).equipmentAvailability,
        )
    }

    @Test
    fun confirming_an_untouched_availability_suggestion_persists_it_without_declaring_it() = runTest {
        val suggested = EquipmentAvailability(setOf(EquipmentCategory.BARBELL, EquipmentCategory.MACHINES))
        val vm = viewModel(Settings(equipmentAvailability = suggested))

        vm.initialize(SetupWizardMode.FULL, draftId = "confirm-availability-suggestion")
        advanceUntilIdle()

        val seeded = vm.state.value.draft
        assertEquals(suggested, seeded.trainingOptions.availability)
        assertFalse(seeded.isStepDeclared(SetupStepId.HOME_EQUIPMENT))
        assertEquals(
            SetupPatchField.Unchanged,
            buildSettingsPatch(vm, seeded, Settings(equipmentAvailability = suggested)).equipmentAvailability,
        )

        val confirmed = seeded.confirmCurrentStep(SetupStepId.HOME_EQUIPMENT)

        assertEquals(
            SetupAnswerProvenance.SUGGESTED,
            confirmed.stepProgress.answers[SetupStepId.HOME_EQUIPMENT],
        )
        assertFalse("Accepting a suggestion must not turn it into a declaration", confirmed.isStepDeclared(SetupStepId.HOME_EQUIPMENT))
        assertEquals(
            SetupPatchField.Set(suggested),
            buildSettingsPatch(vm, confirmed, Settings(equipmentAvailability = suggested)).equipmentAvailability,
        )
    }

    @Test
    fun confirming_an_empty_availability_suggestion_persists_explicit_empty() = runTest {
        val suggestedNone = EquipmentAvailability(emptySet())
        val vm = viewModel(Settings(equipmentAvailability = suggestedNone))

        vm.initialize(SetupWizardMode.FULL, draftId = "confirm-empty-availability-suggestion")
        advanceUntilIdle()

        val seeded = vm.state.value.draft
        assertFalse(seeded.isStepDeclared(SetupStepId.HOME_EQUIPMENT))
        val confirmed = seeded.confirmCurrentStep(SetupStepId.HOME_EQUIPMENT)

        assertEquals(SetupAnswerProvenance.SUGGESTED, confirmed.stepProgress.answers[SetupStepId.HOME_EQUIPMENT])
        assertFalse(confirmed.isStepDeclared(SetupStepId.HOME_EQUIPMENT))
        assertEquals(
            SetupPatchField.Set(EquipmentAvailability(emptySet())),
            buildSettingsPatch(vm, confirmed, Settings(equipmentAvailability = suggestedNone)).equipmentAvailability,
        )
    }

    @Test
    fun restoring_a_null_availability_draft_does_not_merge_the_settings_suggestion() = runTest {
        val id = "restored-availability"
        val restored = SetupWizardDraft(
            draftId = id,
            commitId = "restored-commit",
            draftScope = "full",
            trainingOptions = SetupTrainingOptions(availability = null),
        ).let { it.copy(stepProgress = SetupStepProgress.initial(it.stepContext())) }
        persistence.seed(restored)
        val vm = viewModel(Settings(equipmentAvailability = EquipmentAvailability(setOf(EquipmentCategory.CARDIO))))

        vm.initialize(SetupWizardMode.RESUME, draftId = id)
        advanceUntilIdle()

        assertNull(vm.state.value.draft.trainingOptions.availability)
    }

    @Test
    fun draft_json_and_equipment_footprint_keep_null_empty_and_change_impact_distinct() {
        val json = Json { ignoreUnknownKeys = true; encodeDefaults = true }
        val legacyDraft = json.decodeFromString<SetupWizardDraft>("""{"trainingOptions":{}}""")
        val explicitNone = SetupWizardDraft(
            trainingOptions = SetupTrainingOptions(availability = EquipmentAvailability()),
        )
        val roundTrip = json.decodeFromString<SetupWizardDraft>(json.encodeToString(explicitNone))
        assertNull(legacyDraft.trainingOptions.availability)
        assertEquals(EquipmentAvailability(emptySet()), roundTrip.trainingOptions.availability)
        assertFalse(legacyDraft.inputFootprint().equipmentAvailability == roundTrip.inputFootprint().equipmentAvailability)

        val withBarbell = explicitNone.copy(
            trainingOptions = SetupTrainingOptions(
                availability = EquipmentAvailability(setOf(EquipmentCategory.BARBELL)),
            ),
        )
        assertEquals(
            setOf(SetupChangeSource.EQUIPMENT),
            SetupChangeDetector.sourcesFor(explicitNone.inputFootprint(), withBarbell.inputFootprint()),
        )
        val impacted = withBarbell.withChangeImpacts(explicitNone)
        assertTrue(impacted.stepProgress.pendingReview.contains(SetupStepId.PLAN))
        assertTrue(
            impacted.stepProgress.stalePreviews.containsAll(
                setOf(SetupPreviewKind.EXERCISES, SetupPreviewKind.LOADS, SetupPreviewKind.WARMUPS),
            ),
        )
    }

    @Test
    fun confirmed_current_availability_persists_the_exact_declared_material() = runTest {
        val vm = viewModel(Settings())

        vm.initialize(SetupWizardMode.FULL, draftId = "declared-availability")
        advanceUntilIdle()

        val chosen = material(gym, EquipmentSymbolId.BARBELL, EquipmentSymbolId.MACHINES)
        val confirmed = vm.state.value.draft
            .withStepChoice(SetupStepId.EQUIPMENT, "gym")
            .withStepChoices(SetupStepId.AVAILABILITY, setOf("BARBELL", "MACHINES"))
            .touchStep(SetupStepId.AVAILABILITY)
            .confirmCurrentStep(SetupStepId.AVAILABILITY)

        assertEquals(chosen, confirmed.trainingOptions.availability)
        assertEquals(
            setOf(EquipmentCategory.BARBELL, EquipmentCategory.MACHINES),
            confirmed.trainingOptions.availability?.categories,
        )
        assertEquals(
            SetupAnswerProvenance.USER_DECLARED,
            confirmed.stepProgress.answers[SetupStepId.AVAILABILITY],
        )
        assertEquals(
            SetupPatchField.Set(chosen),
            buildSettingsPatch(vm, confirmed, Settings()).equipmentAvailability,
        )
    }

    @Test
    fun accepted_untouched_gym_seed_persists_the_usual_gym_material_without_declaring() = runTest {
        val vm = viewModel(Settings())
        val seeded = vm.state.value.draft.withStepChoice(SetupStepId.EQUIPMENT, "gym")
        val gymSeed = EquipmentSymbols.availabilityOf(EquipmentSymbols.seedFor(gym), gym)

        // La semilla del gimnasio no es una respuesta: sigue sin selección guardada y sin procedencia hasta que la
        // persona continúa.
        assertEquals(gymSeed, seeded.trainingOptions.availability)
        assertNull(seeded.stepSelections[SetupStepId.AVAILABILITY])
        assertNull(seeded.stepProgress.answers[SetupStepId.AVAILABILITY])
        assertEquals(
            SetupPatchField.Unchanged,
            buildSettingsPatch(vm, seeded, Settings()).equipmentAvailability,
        )

        val accepted = seeded.confirmCurrentStep(SetupStepId.AVAILABILITY)

        assertEquals(SetupAnswerProvenance.SUGGESTED, accepted.stepProgress.answers[SetupStepId.AVAILABILITY])
        assertFalse("Aceptar una sugerencia no la convierte en declaración", accepted.isStepDeclared(SetupStepId.AVAILABILITY))
        assertEquals(
            SetupPatchField.Set(gymSeed),
            buildSettingsPatch(vm, accepted, Settings()).equipmentAvailability,
        )
    }

    @Test
    fun confirmed_bodyweight_only_persists_explicit_empty_and_not_null() = runTest {
        val vm = viewModel(Settings())

        vm.initialize(SetupWizardMode.FULL, draftId = "bodyweight-only")
        advanceUntilIdle()

        val confirmed = vm.state.value.draft
            .withStepChoice(SetupStepId.EQUIPMENT, "gym")
            .withStepChoices(SetupStepId.AVAILABILITY, setOf("bodyweight_only"))
            .touchStep(SetupStepId.AVAILABILITY)
            .confirmCurrentStep(SetupStepId.AVAILABILITY)

        assertEquals(EquipmentAvailability(emptySet()), confirmed.trainingOptions.availability)
        assertEquals(
            SetupPatchField.Set(EquipmentAvailability(emptySet())),
            buildSettingsPatch(vm, confirmed, Settings()).equipmentAvailability,
        )
    }

    @Test
    fun a_home_without_material_persists_the_explicit_empty_only_once_the_material_step_is_confirmed() = runTest {
        val vm = viewModel(Settings())

        // Los entornos antiguos «sin material» y «en casa» se leen como el lugar «casa»; casa no asume nada.
        for (label in listOf("none", "Sin material", "home")) {
            val atHome = vm.state.value.draft
                .withStepChoice(SetupStepId.EQUIPMENT, label)
                .confirmCurrentStep(SetupStepId.EQUIPMENT)

            assertEquals("env=$label", home, atHome.trainingPlaces)
            assertEquals("env=$label", EquipmentAvailability(emptySet()), atHome.trainingOptions.availability)
            assertEquals(
                "env=$label",
                SetupAnswerProvenance.SUGGESTED,
                atHome.stepProgress.answers[SetupStepId.EQUIPMENT],
            )
            // Confirmar solo el lugar NO autoriza el material: el paso siguiente (material) es el que lo declara.
            assertEquals(
                "env=$label",
                SetupPatchField.Unchanged,
                buildSettingsPatch(vm, atHome, Settings()).equipmentAvailability,
            )
            // Con el material confirmado (vacío = solo peso corporal, una respuesta válida) se persiste el vacío explícito.
            val confirmed = atHome.confirmCurrentStep(SetupStepId.AVAILABILITY)
            assertEquals(
                "env=$label must persist the explicit no-material choice",
                SetupPatchField.Set(EquipmentAvailability(emptySet())),
                buildSettingsPatch(vm, confirmed, Settings()).equipmentAvailability,
            )
            // La ruta no es legacy: NUNCA se fabrica una respuesta retirada.
            assertNull(confirmed.stepProgress.answers[SetupStepId.HOME_EQUIPMENT])
            assertFalse(SetupStepId.HOME_EQUIPMENT in confirmed.declaredSteps)
            assertFalse(confirmed.wizChat.acceptedAnswers.any { it.questionId == WizChatQuestionId.T_HOME_EQUIPMENT })
        }
    }

    @Test
    fun unknown_blank_or_unconfirmed_environment_never_persists_empty() = runTest {
        val vm = viewModel(Settings())
        val empty = EquipmentAvailability(emptySet())
        val cases = listOf(
            "null availability under no-material" to SetupWizardDraft(trainingEnvironment = "none"),
            "blank environment" to SetupWizardDraft(trainingEnvironment = "   "),
            "unknown environment, declared equipment" to SetupWizardDraft(
                trainingEnvironment = "en el parque",
                trainingOptions = SetupTrainingOptions(availability = empty),
            ).touchStep(SetupStepId.EQUIPMENT),
            "unconfirmed no-material seed" to SetupWizardDraft(
                trainingEnvironment = "none",
                trainingOptions = SetupTrainingOptions(availability = empty),
                stepSelections = mapOf(SetupStepId.EQUIPMENT to listOf("none")),
            ),
        )

        cases.forEach { (label, draft) ->
            assertEquals(
                label,
                SetupPatchField.Unchanged,
                buildSettingsPatch(vm, draft, Settings()).equipmentAvailability,
            )
        }
    }

    @Test
    fun a_place_change_invalidates_only_the_stale_availability_confirmation() = runTest {
        val vm = viewModel(Settings())
        val stock = EquipmentInventory(
            barbellWeightKg = 20.0,
            plates = listOf(PlateStock(weightKg = 10.0, countPerSide = 2)),
        )
        val underGym = SetupWizardDraft(
            trainingOptions = SetupTrainingOptions(availability = null, inventory = stock),
        )
            .withStepChoice(SetupStepId.EQUIPMENT, "gym")
            .withStepChoices(SetupStepId.AVAILABILITY, setOf("BARBELL"))
            .touchStep(SetupStepId.AVAILABILITY)
            .confirmCurrentStep(SetupStepId.AVAILABILITY)
            .touchStep(SetupStepId.WEEKDAYS)
            .withStepChoices(SetupStepId.WEEKDAYS, setOf("1", "2"))
            .recordStepAnswer(SetupStepId.GOAL, SetupAnswerProvenance.USER_DECLARED, SetupValueState.DECLARED)

        val underGymMaterial = material(gym, EquipmentSymbolId.BARBELL)
        assertEquals(SetupAnswerProvenance.USER_DECLARED, underGym.stepProgress.answers[SetupStepId.AVAILABILITY])
        assertEquals(
            SetupPatchField.Set(underGymMaterial),
            buildSettingsPatch(vm, underGym, Settings()).equipmentAvailability,
        )

        val reseeded = underGym.withStepChoice(SetupStepId.EQUIPMENT, "home")

        // El material que se ve cambia con el lugar (las llaves de gimnasio dejan de contar), así que la confirmación
        // del gimnasio ya no describe este valor y se retira; la selección de símbolos se conserva.
        assertEquals(material(home, EquipmentSymbolId.BARBELL), reseeded.trainingOptions.availability)
        assertEquals(setOf(EquipmentSymbolId.BARBELL), reseeded.selectedEquipmentSymbols())
        assertNull(reseeded.stepProgress.answers[SetupStepId.AVAILABILITY])
        assertFalse(SetupStepId.AVAILABILITY in reseeded.declaredSteps)
        assertNull(reseeded.stepSelections[SetupStepId.AVAILABILITY])
        assertEquals(
            SetupPatchField.Unchanged,
            buildSettingsPatch(vm, reseeded, Settings()).equipmentAvailability,
        )

        // Reafirmar bajo el lugar nuevo vuelve a autorizar, y solo AVAILABILITY.
        val reconfirmed = reseeded.confirmCurrentStep(SetupStepId.AVAILABILITY)
        assertEquals(SetupAnswerProvenance.SUGGESTED, reconfirmed.stepProgress.answers[SetupStepId.AVAILABILITY])
        assertEquals(
            SetupPatchField.Set(material(home, EquipmentSymbolId.BARBELL)),
            buildSettingsPatch(vm, reconfirmed, Settings()).equipmentAvailability,
        )

        // Respuestas, declaraciones, días, stock y cursor ajenos: intactos.
        assertEquals(SetupAnswerProvenance.USER_DECLARED, reconfirmed.stepProgress.answers[SetupStepId.GOAL])
        assertTrue(SetupStepId.WEEKDAYS in reconfirmed.declaredSteps)
        assertEquals(setOf(1, 2), reconfirmed.selectedWeekdays)
        assertEquals(stock, reconfirmed.trainingOptions.inventory)
        assertEquals("home", reconfirmed.trainingEnvironment)
        assertEquals(underGym.stepProgress.currentStepId, reseeded.stepProgress.currentStepId)
        assertEquals(underGym.stepProgress.visited, reseeded.stepProgress.visited)
    }

    @Test
    fun reselecting_the_same_place_preserves_the_confirmed_material_and_persists_it() = runTest {
        val vm = viewModel(Settings())
        // Valor esperado INDEPENDIENTE: el subconjunto que el usuario declaró.
        val chosen = material(gym, EquipmentSymbolId.BARBELL, EquipmentSymbolId.CABLE)
        val underGym = vm.state.value.draft
            .withStepChoice(SetupStepId.EQUIPMENT, "gym")
            .withStepChoices(SetupStepId.AVAILABILITY, setOf("BARBELL", "CABLE"))
            .touchStep(SetupStepId.AVAILABILITY)
            .confirmCurrentStep(SetupStepId.AVAILABILITY)
        assertEquals(chosen, underGym.trainingOptions.availability)
        assertEquals(
            SetupPatchField.Set(chosen),
            buildSettingsPatch(vm, underGym, Settings()).equipmentAvailability,
        )

        // Re-pulsar el MISMO lugar no puede re-sembrar: manda el material declarado y lo que se persiste es ESE
        // subconjunto, no la semilla completa del gimnasio.
        val sameEnvironment = underGym.withStepChoice(SetupStepId.EQUIPMENT, "gym")

        assertEquals("gym", sameEnvironment.trainingEnvironment)
        assertEquals(chosen, sameEnvironment.trainingOptions.availability)
        assertFalse(
            "Re-pulsar «gym» no debe ampliar el material declarado al de la semilla del gimnasio",
            sameEnvironment.trainingOptions.availability ==
                EquipmentSymbols.availabilityOf(EquipmentSymbols.seedFor(gym), gym),
        )
        assertEquals(SetupAnswerProvenance.USER_DECLARED, sameEnvironment.stepProgress.answers[SetupStepId.AVAILABILITY])
        assertTrue(SetupStepId.AVAILABILITY in sameEnvironment.declaredSteps)
        assertEquals(
            SetupPatchField.Set(chosen),
            buildSettingsPatch(vm, sameEnvironment, Settings()).equipmentAvailability,
        )
    }

    @Test
    fun reselecting_the_same_environment_keeps_explicit_bodyweight_only_empty() = runTest {
        val vm = viewModel(Settings())
        val bodyweightOnly = EquipmentAvailability(emptySet())
        val underGym = vm.state.value.draft
            .withStepChoice(SetupStepId.EQUIPMENT, "gym")
            .withStepChoices(SetupStepId.AVAILABILITY, setOf("bodyweight_only"))
            .touchStep(SetupStepId.AVAILABILITY)
            .confirmCurrentStep(SetupStepId.AVAILABILITY)
        assertEquals(bodyweightOnly, underGym.trainingOptions.availability)

        val sameEnvironment = underGym.withStepChoice(SetupStepId.EQUIPMENT, "gym")

        assertEquals(
            "«Solo peso corporal» es un vacío explícito y no un preset de gimnasio",
            bodyweightOnly,
            sameEnvironment.trainingOptions.availability,
        )
        assertEquals(SetupAnswerProvenance.USER_DECLARED, sameEnvironment.stepProgress.answers[SetupStepId.AVAILABILITY])
        assertEquals(
            SetupPatchField.Set(bodyweightOnly),
            buildSettingsPatch(vm, sameEnvironment, Settings()).equipmentAvailability,
        )
    }

    @Test
    fun reselecting_the_same_place_keeps_the_no_material_empty_and_confirmed() = runTest {
        val vm = viewModel(Settings())
        val noMaterial = EquipmentAvailability(emptySet())
        val confirmed = vm.state.value.draft
            .withStepChoice(SetupStepId.EQUIPMENT, "none")
            .confirmCurrentStep(SetupStepId.EQUIPMENT)
            .confirmCurrentStep(SetupStepId.AVAILABILITY)
        assertEquals(noMaterial, confirmed.trainingOptions.availability)

        val sameEnvironment = confirmed.withStepChoice(SetupStepId.EQUIPMENT, "none")

        assertEquals("home", sameEnvironment.trainingEnvironment)
        assertEquals(noMaterial, sameEnvironment.trainingOptions.availability)
        assertEquals(SetupAnswerProvenance.SUGGESTED, sameEnvironment.stepProgress.answers[SetupStepId.EQUIPMENT])
        assertEquals(SetupAnswerProvenance.SUGGESTED, sameEnvironment.stepProgress.answers[SetupStepId.AVAILABILITY])
        assertEquals(
            SetupPatchField.Set(noMaterial),
            buildSettingsPatch(vm, sameEnvironment, Settings()).equipmentAvailability,
        )
    }

    @Test
    fun reselecting_the_same_place_keeps_an_accepted_suggestion_still_suggested() = runTest {
        val vm = viewModel(Settings())
        val custom = material(gym, EquipmentSymbolId.KETTLEBELL)
        val accepted = vm.state.value.draft
            .withStepChoice(SetupStepId.EQUIPMENT, "machines")
            .withStepChoices(SetupStepId.AVAILABILITY, setOf("KETTLEBELL"))
            .confirmCurrentStep(SetupStepId.AVAILABILITY)
        assertEquals(SetupAnswerProvenance.SUGGESTED, accepted.stepProgress.answers[SetupStepId.AVAILABILITY])
        assertFalse(accepted.isStepDeclared(SetupStepId.AVAILABILITY))
        assertEquals(custom, accepted.trainingOptions.availability)

        // «Principalmente máquinas» es el lugar gimnasio: re-pulsarlo no cambia nada.
        val sameEnvironment = accepted.withStepChoice(SetupStepId.EQUIPMENT, "machines")

        assertEquals(custom, sameEnvironment.trainingOptions.availability)
        assertEquals(SetupAnswerProvenance.SUGGESTED, sameEnvironment.stepProgress.answers[SetupStepId.AVAILABILITY])
        assertFalse(
            "Aceptar una sugerencia no la convierte en declaración",
            sameEnvironment.isStepDeclared(SetupStepId.AVAILABILITY),
        )
        assertEquals(
            SetupPatchField.Set(custom),
            buildSettingsPatch(vm, sameEnvironment, Settings()).equipmentAvailability,
        )
    }

    @Test
    fun choosing_a_place_from_missing_material_seeds_it_without_confirming() = runTest {
        val vm = viewModel(Settings())
        val gymSeed = EquipmentSymbols.availabilityOf(EquipmentSymbols.seedFor(gym), gym)
        // Registro obsoleto: AVAILABILITY confirmó en su día, pero el borrador ya no tiene material. Sin invalidarlo,
        // la semilla nueva quedaría confirmada por un registro que ya no describe este valor.
        val stale = SetupWizardDraft(trainingEnvironment = "gym").copy(
            stepProgress = SetupStepProgress(
                answers = mapOf(SetupStepId.AVAILABILITY to SetupAnswerProvenance.USER_DECLARED),
            ),
            declaredSteps = setOf(SetupStepId.AVAILABILITY),
        )
        assertNull(stale.trainingOptions.availability)

        val seeded = stale.withStepChoice(SetupStepId.EQUIPMENT, "gym")

        assertEquals("Sembrar desde «sin material» sigue siendo posible", gymSeed, seeded.trainingOptions.availability)
        assertNull(seeded.stepProgress.answers[SetupStepId.AVAILABILITY])
        assertFalse(SetupStepId.AVAILABILITY in seeded.declaredSteps)
        assertEquals(
            SetupPatchField.Unchanged,
            buildSettingsPatch(vm, seeded, Settings()).equipmentAvailability,
        )
    }

    @Test
    fun confirmed_gym_then_home_retires_the_gym_confirmation_and_leaves_the_patch_unchanged() = runTest {
        val vm = viewModel(Settings())
        val underGym = vm.state.value.draft
            .withStepChoice(SetupStepId.EQUIPMENT, "gym")
            .withStepChoices(SetupStepId.AVAILABILITY, setOf("BARBELL"))
            .touchStep(SetupStepId.AVAILABILITY)
            .confirmCurrentStep(SetupStepId.AVAILABILITY)

        val atHome = underGym.withStepChoice(SetupStepId.EQUIPMENT, "home")

        // Lo marcado se conserva (casa ofrece lo mismo), pero la confirmación del gimnasio ya no vale y no se persiste.
        assertEquals(material(home, EquipmentSymbolId.BARBELL), atHome.trainingOptions.availability)
        assertNull(atHome.stepProgress.answers[SetupStepId.AVAILABILITY])
        assertEquals(
            SetupPatchField.Unchanged,
            buildSettingsPatch(vm, atHome, Settings()).equipmentAvailability,
        )
    }

    @Test
    fun stale_legacy_home_equipment_cannot_authorize_an_unconfirmed_current_seed() = runTest {
        val vm = viewModel(Settings())
        val staleLegacy = vm.state.value.draft
            .withStepChoice(SetupStepId.EQUIPMENT, "machines")
            .recordStepAnswer(SetupStepId.HOME_EQUIPMENT, SetupAnswerProvenance.USER_DECLARED, SetupValueState.DECLARED)

        assertEquals("gym", staleLegacy.trainingEnvironment)
        assertNull(staleLegacy.stepProgress.answers[SetupStepId.AVAILABILITY])
        assertEquals(
            SetupPatchField.Unchanged,
            buildSettingsPatch(vm, staleLegacy, Settings()).equipmentAvailability,
        )
    }

    @Test
    fun each_current_equipment_marker_disables_legacy_authorization() = runTest {
        val vm = viewModel(Settings())
        val live = EquipmentAvailability(setOf(EquipmentCategory.BARBELL, EquipmentCategory.CABLE))
        val legacyOnly = SetupWizardDraft(trainingOptions = SetupTrainingOptions(availability = live))
            .recordStepAnswer(SetupStepId.HOME_EQUIPMENT, SetupAnswerProvenance.USER_DECLARED, SetupValueState.DECLARED)
        val legacyAnswer = legacyOnly.stepProgress.answers[SetupStepId.HOME_EQUIPMENT]
        assertEquals(SetupAnswerProvenance.USER_DECLARED, legacyAnswer)

        val disabled = listOf(
            "declaredSteps + EQUIPMENT" to legacyOnly.copy(declaredSteps = setOf(SetupStepId.EQUIPMENT)),
            "answers[EQUIPMENT]" to legacyOnly.copy(
                stepProgress = legacyOnly.stepProgress.copy(
                    answers = legacyOnly.stepProgress.answers + (SetupStepId.EQUIPMENT to SetupAnswerProvenance.SUGGESTED),
                ),
            ),
            "stepSelections[EQUIPMENT]" to legacyOnly.copy(
                stepSelections = mapOf(SetupStepId.EQUIPMENT to listOf("gym")),
            ),
            "stepSelections[AVAILABILITY] sin confirmar" to legacyOnly.copy(
                stepSelections = mapOf(SetupStepId.AVAILABILITY to listOf("BARBELL")),
            ),
        )
        disabled.forEach { (label, draft) ->
            assertEquals(
                "$label must not authorize the current seed",
                SetupPatchField.Unchanged,
                buildSettingsPatch(vm, draft, Settings()).equipmentAvailability,
            )
        }

        // Precedencia: una confirmación actual gana; la autorización legacy
        // solo sobrevive cuando no hay ninguna señal del flujo actual.
        val currentWins = listOf(
            "declaredSteps + AVAILABILITY" to legacyOnly.copy(declaredSteps = setOf(SetupStepId.AVAILABILITY)),
            "answers[AVAILABILITY] = SUGGESTED" to legacyOnly.copy(
                stepProgress = legacyOnly.stepProgress.copy(
                    answers = legacyOnly.stepProgress.answers + (SetupStepId.AVAILABILITY to SetupAnswerProvenance.SUGGESTED),
                ),
            ),
        )
        currentWins.forEach { (label, draft) ->
            assertEquals(
                label,
                SetupPatchField.Set(live),
                buildSettingsPatch(vm, draft, Settings()).equipmentAvailability,
            )
        }
    }

    @Test
    fun derived_or_engine_result_availability_record_is_not_a_confirmation() = runTest {
        val vm = viewModel(Settings())
        val empty = EquipmentAvailability(emptySet())
        val gymSeed = EquipmentAvailability(setOf(EquipmentCategory.MACHINES))
        val cases = listOf(
            "DERIVED under no-material" to SetupWizardDraft(
                trainingEnvironment = "none",
                trainingOptions = SetupTrainingOptions(availability = empty),
                stepProgress = SetupStepProgress(
                    answers = mapOf(SetupStepId.AVAILABILITY to SetupAnswerProvenance.DERIVED),
                ),
            ),
            "DERIVED with a gym seed" to SetupWizardDraft(
                trainingEnvironment = "gym",
                trainingOptions = SetupTrainingOptions(availability = gymSeed),
                stepProgress = SetupStepProgress(
                    answers = mapOf(SetupStepId.AVAILABILITY to SetupAnswerProvenance.DERIVED),
                ),
            ),
            // Un resultado de motor en AVAILABILITY tampoco devuelve la
            // autorización al espejo legacy: es señal del flujo actual.
            "ENGINE_RESULT + legacy HOME_EQUIPMENT, sin entorno" to SetupWizardDraft(
                trainingOptions = SetupTrainingOptions(availability = gymSeed),
                stepProgress = SetupStepProgress(
                    answers = mapOf(
                        SetupStepId.HOME_EQUIPMENT to SetupAnswerProvenance.USER_DECLARED,
                        SetupStepId.AVAILABILITY to SetupAnswerProvenance.ENGINE_RESULT,
                    ),
                ),
            ),
        )

        cases.forEach { (label, draft) ->
            assertEquals(
                label,
                SetupPatchField.Unchanged,
                buildSettingsPatch(vm, draft, Settings()).equipmentAvailability,
            )
        }
    }

    @Test
    fun valid_legacy_only_home_equipment_still_persists_categories_after_resume() = runTest {
        val vm = viewModel(Settings())
        val live = EquipmentAvailability(setOf(EquipmentCategory.BARBELL, EquipmentCategory.CABLE))
        val legacyOnly = SetupWizardDraft(trainingOptions = SetupTrainingOptions(availability = live))
            .recordStepAnswer(SetupStepId.HOME_EQUIPMENT, SetupAnswerProvenance.SUGGESTED, SetupValueState.ESTIMATED)

        // Compatibilidad legacy: sin entorno actual y sin ninguna señal del
        // flujo nuevo, la respuesta legacy sigue autorizando las categorías.
        assertNull(legacyOnly.trainingEnvironment)
        assertTrue(legacyOnly.declaredSteps.isEmpty())
        assertNull(legacyOnly.stepSelections[SetupStepId.EQUIPMENT])
        assertNull(legacyOnly.stepSelections[SetupStepId.AVAILABILITY])
        assertEquals(
            SetupPatchField.Set(live),
            buildSettingsPatch(vm, legacyOnly, Settings()).equipmentAvailability,
        )

        // Y sobrevive al viaje real por el serializador del borrador (reanudar).
        val json = Json { ignoreUnknownKeys = true; encodeDefaults = true }
        val resumed = json.decodeFromString<SetupWizardDraft>(json.encodeToString(legacyOnly))
        assertEquals(live, resumed.trainingOptions.availability)
        assertEquals(SetupAnswerProvenance.SUGGESTED, resumed.stepProgress.answers[SetupStepId.HOME_EQUIPMENT])
        assertEquals(
            SetupPatchField.Set(live),
            buildSettingsPatch(vm, resumed, Settings()).equipmentAvailability,
        )
    }

    @Test
    fun confirmed_current_availability_reaches_real_settings_without_touching_numeric_stock() = runTest {
        assertAvailabilityReachesRealSettings(
            commitId = "commit-availability-owner",
            environment = "home",
            selected = setOf("KETTLEBELL", "BAND"),
            expected = material(home, EquipmentSymbolId.KETTLEBELL, EquipmentSymbolId.BANDS),
        )
    }

    @Test
    fun confirmed_bodyweight_only_reaches_real_settings_as_explicit_empty() = runTest {
        // Mismo camino real completo para el vacío EXPLÍCITO: reductor ->
        // confirmación -> parche privado real -> coordinador -> Settings.
        assertAvailabilityReachesRealSettings(
            commitId = "commit-availability-explicit-empty",
            environment = "gym",
            selected = setOf("bodyweight_only"),
            expected = EquipmentAvailability(emptySet()),
        )
    }

    private suspend fun assertAvailabilityReachesRealSettings(
        commitId: String,
        environment: String,
        selected: Set<String>,
        expected: EquipmentAvailability,
    ) {
        val db = KpknDatabase.createInMemory(app)
        try {
            val stock = EquipmentInventory(
                barbellWeightKg = 20.0,
                plates = listOf(PlateStock(weightKg = 10.0, countPerSide = 2)),
            )
            val existing = Settings(
                username = "Perfil previo",
                barbellWeight = 17.5,
                availablePlates = listOf(5.0, 2.5),
                equipmentInventory = stock,
                equipmentAvailability = EquipmentAvailability(setOf(EquipmentCategory.BARBELL)),
            )
            db.settingsDao().upsert(existing.toEntity())

            // Reductor real -> confirmación real -> parche real -> Room real.
            val vm = viewModel(Settings())
            val patch = buildSettingsPatch(
                vm,
                SetupWizardDraft()
                    .withStepChoice(SetupStepId.EQUIPMENT, environment)
                    .withStepChoices(SetupStepId.AVAILABILITY, selected)
                    .touchStep(SetupStepId.AVAILABILITY)
                    .confirmCurrentStep(SetupStepId.AVAILABILITY),
                existing,
            )
            assertEquals(SetupPatchField.Set(expected), patch.equipmentAvailability)

            SetupCommitCoordinator(db).commit(
                SetupCommitRequest(
                    commitId = commitId,
                    draftId = null,
                    settings = Settings(username = "stale request snapshot"),
                    program = null,
                    nutritionPlan = null,
                    activateProgram = false,
                    activateNutrition = false,
                    settingsPatch = patch,
                ),
            )

            val saved = checkNotNull(db.settingsDao().get()?.toSettings())
            assertEquals(expected, saved.equipmentAvailability)
            assertEquals("Perfil previo", saved.username)
            assertEquals(stock, saved.equipmentInventory)
            assertEquals(17.5, saved.barbellWeight, 0.0)
            assertEquals(listOf(5.0, 2.5), saved.availablePlates)
        } finally {
            db.close()
        }
    }

    private fun viewModel(settings: Settings): SetupWizardViewModel =
        SetupWizardViewModel(
            application = app,
            savedStateHandle = SavedStateHandle(),
            persistence = persistence,
            environment = TestEnvironment(settings),
            materializeOverride = SetupWizardMaterializer { SetupPreview(null, null) },
        ).also { viewModels.put("availability-${viewModels.hashCode()}-${System.identityHashCode(it)}", it) }

    private fun buildSettingsPatch(
        viewModel: SetupWizardViewModel,
        draft: SetupWizardDraft,
        base: Settings,
    ): SetupSettingsPatch {
        val builder = SetupWizardViewModel::class.java.getDeclaredMethod(
            "buildSettingsPatch",
            Settings::class.java,
            SetupWizardDraft::class.java,
            Program::class.java,
            NutritionPlan::class.java,
            SetupRingsMapping::class.java,
            Boolean::class.javaPrimitiveType!!,
        ).apply { isAccessible = true }
        return builder.invoke(
            viewModel,
            base,
            draft,
            null,
            null,
            SetupRingsMapping(RingsCompletion.UNKNOWN),
            false,
        ) as SetupSettingsPatch
    }

    private class MemoryPersistence : SetupWizardPersistence {
        private val rows = linkedMapOf<String, SetupDraft>()
        private val json = Json { encodeDefaults = true }

        fun seed(draft: SetupWizardDraft) {
            rows[draft.draftId] = SetupDraft(
                draftId = draft.draftId,
                payloadJson = json.encodeToString(draft),
                revision = draft.revision.toLong(),
                catalogRevision = draft.catalogRevision,
                updatedAtEpochMs = 0L,
            )
        }

        override suspend fun load(draftId: String): SetupDraft? = rows[draftId]

        override suspend fun save(
            draftId: String,
            payloadJson: String,
            revision: Long,
            catalogRevision: String?,
        ): SetupDraft = SetupDraft(draftId, payloadJson, revision, catalogRevision, 0L)
            .also { rows[draftId] = it }

        override suspend fun discard(draftId: String) {
            rows.remove(draftId)
        }

        override suspend fun listRecoverable(): List<SetupDraftCandidate> = rows.values.map {
            SetupDraftCandidate(it.draftId, SetupDraftScope.FULL, it.revision, it.updatedAtEpochMs, it.catalogRevision)
        }
    }

    private class TestEnvironment(
        override val settings: Settings,
    ) : SetupWizardEnvironment {
        override suspend fun awaitReady() = Unit
        override fun activeProgramId(): String? = null
        override fun activeNutritionPlanId(): String? = null
        override fun activeNutritionPlan(): NutritionPlan? = null
        override fun nutritionPlan(id: String): NutritionPlan? = null
        override fun hasInitialRecoveryEvidence(): Boolean = false
        override suspend fun refreshBodyProgress() = Unit
    }
}
