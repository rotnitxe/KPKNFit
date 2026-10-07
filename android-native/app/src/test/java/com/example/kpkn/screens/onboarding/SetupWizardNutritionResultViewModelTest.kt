package com.example.kpkn.screens.onboarding

import android.app.Application
import androidx.lifecycle.SavedStateHandle
import androidx.test.core.app.ApplicationProvider
import com.example.kpkn.data.models.CalculationOrigin
import com.example.kpkn.data.models.NutritionPlan
import com.example.kpkn.data.models.PlanDirection
import com.example.kpkn.data.models.Settings
import com.example.kpkn.data.onboarding.SetupDraft
import com.example.kpkn.data.onboarding.SetupDraftCandidate
import com.example.kpkn.data.onboarding.SetupDraftScope
import com.example.kpkn.domain.nutrition.EerActivity
import com.example.kpkn.domain.nutrition.EerSex
import com.example.kpkn.domain.nutrition.NutritionConfigurationMode
import com.example.kpkn.domain.nutrition.NutritionPlanPreparationStatus
import com.example.kpkn.domain.nutrition.NutritionWizardDraft
import com.example.kpkn.domain.nutrition.PlanValues
import com.example.kpkn.domain.nutrition.automaticPlanValues
import com.example.kpkn.domain.onboarding.SetupStepId
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.test.StandardTestDispatcher
import kotlinx.coroutines.test.TestScope
import kotlinx.coroutines.test.advanceUntilIdle
import kotlinx.coroutines.test.resetMain
import kotlinx.coroutines.test.runTest
import kotlinx.coroutines.test.setMain
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

/**
 * El resultado de nutrición con el [SetupWizardViewModel] REAL (persistencia y entorno herméticos): lo que el
 * panel escribe con `updateStep` es lo que la preparación publica y lo que se activa; el `hardStop` cierra
 * «Continuar» sin mover el cursor, y los modos objetivos propios, solo registro y ecuación bloqueada no se
 * rompen.
 */
@OptIn(ExperimentalCoroutinesApi::class)
@RunWith(RobolectricTestRunner::class)
@Config(manifest = Config.NONE, sdk = [34])
class SetupWizardNutritionResultViewModelTest {

    private val dispatcher = StandardTestDispatcher()
    private lateinit var persistence: InMemoryPersistence
    private lateinit var app: Application

    @Before
    fun setUp() {
        Dispatchers.setMain(dispatcher)
        app = ApplicationProvider.getApplicationContext()
        persistence = InMemoryPersistence()
    }

    @After
    fun tearDown() {
        Dispatchers.resetMain()
    }

    private fun vm() = SetupWizardViewModel(app, SavedStateHandle(), persistence, FixedSettingsEnvironment(Settings()))

    private fun automatic(sex: EerSex? = EerSex.MALE) = NutritionWizardDraft(
        direction = PlanDirection.DEFICIT,
        equationSex = sex,
        activity = EerActivity.LOW_ACTIVE,
        configurationMode = NutritionConfigurationMode.AUTOMATIC,
    )

    /** Alta solo de nutrición con los datos del perfil y el cursor en el resultado. */
    private fun TestScope.openResult(vm: SetupWizardViewModel, nutrition: NutritionWizardDraft = automatic()) {
        vm.initialize(SetupWizardMode.NUTRITION_ONLY)
        advanceUntilIdle()
        vm.update { draft ->
            draft.copy(ageYears = 30, heightCm = 178.0, weightKg = 80.0, nutritionDraft = nutrition)
        }
        advanceUntilIdle()
        vm.editStep(SetupStepId.NUTRITION_RESULT)
        advanceUntilIdle()
        assertEquals(SetupStepId.NUTRITION_RESULT, vm.state.value.currentStep)
    }

    private fun TestScope.write(vm: SetupWizardViewModel, values: PlanValues, clear: Boolean = false) {
        vm.updateStep(SetupStepId.NUTRITION_RESULT) { draft ->
            draft.copy(nutritionDraft = checkNotNull(draft.nutritionDraft).withPlanValues(values, clear))
        }
        advanceUntilIdle()
    }

    private fun planOf(vm: SetupWizardViewModel): NutritionPlan =
        checkNotNull(vm.state.value.nutritionPreparation?.plan) {
            "sin plan: errores=${vm.state.value.nutritionErrors} estado=${vm.state.value.nutritionPreparation?.status}"
        }

    private fun valuesOf(plan: NutritionPlan) =
        PlanValues(plan.calorieTarget, plan.proteinGoal, plan.carbGoal, plan.fatGoal)

    // ─── Escritura → preparación → activación ────────────────────────────────

    @Test
    fun theOpenedResultIsTheAutomaticRecommendationAndNotEdited() = runTest {
        val vm = vm()
        openResult(vm)

        val tuning = checkNotNull(nutritionPlanTuningOf(vm.state.value))
        assertEquals(valuesOf(planOf(vm)), tuning.values)
        assertEquals(automaticPlanValues(tuning.context), tuning.values)
        assertFalse(tuning.isEdited)
        assertEquals(CalculationOrigin.PLAN, planOf(vm).calculationOrigin)
    }

    @Test
    fun movingAMacroLeavesThosePublishedGramsAndTheAtwaterKcalInThePlan() = runTest {
        val vm = vm()
        openResult(vm)
        val tuning = checkNotNull(nutritionPlanTuningOf(vm.state.value))
        val moved = tuning.withProtein(tuning.proteinG + 25)

        write(vm, moved.values)

        val plan = planOf(vm)
        assertEquals(moved.values, valuesOf(plan))
        assertEquals(4 * plan.proteinGoal + 4 * plan.carbGoal + 9 * plan.fatGoal, plan.calorieTarget)
        assertEquals(CalculationOrigin.MANUAL, plan.calculationOrigin)
        // Y es lo que el panel vuelve a leer: nada se corrige en silencio.
        val reread = checkNotNull(nutritionPlanTuningOf(vm.state.value))
        assertEquals(moved.values, reread.values)
        assertTrue(reread.isEdited)
        // El borrador guarda los cuatro números.
        val saved = checkNotNull(vm.state.value.draft.nutritionDraft)
        assertEquals(moved.kcal.toString(), saved.manualCalorieTargetText)
        assertEquals(moved.proteinG.toString(), saved.manualProteinText)
        assertEquals(moved.carbsG.toString(), saved.manualCarbsText)
        assertEquals(moved.fatG.toString(), saved.manualFatText)
        assertEquals(SetupStepId.NUTRITION_RESULT, vm.state.value.currentStep)
    }

    @Test
    fun movingThePaceLeavesTheTuningKcalAndMacrosInThePlan() = runTest {
        val vm = vm()
        openResult(vm)
        val tuning = checkNotNull(nutritionPlanTuningOf(vm.state.value))
        val moved = tuning.withPaceRate(0.62)

        write(vm, moved.values)

        assertEquals(moved.values, valuesOf(planOf(vm)))
        val reread = checkNotNull(nutritionPlanTuningOf(vm.state.value))
        assertEquals(-0.62, reread.weeklyChangeKg!!, 0.001)
    }

    @Test
    fun resettingGoesBackToTheRecommendationWithAutomaticProvenance() = runTest {
        val vm = vm()
        openResult(vm)
        val original = checkNotNull(nutritionPlanTuningOf(vm.state.value))
        write(vm, original.withCarbs(original.carbsG - 70).values)
        assertEquals(CalculationOrigin.MANUAL, planOf(vm).calculationOrigin)

        val edited = checkNotNull(nutritionPlanTuningOf(vm.state.value))
        assertTrue(edited.isEdited)
        val reset = edited.reset()
        write(vm, reset.values, clear = reset.values == automaticPlanValues(reset.context))

        assertEquals(original.values, valuesOf(planOf(vm)))
        assertEquals(CalculationOrigin.PLAN, planOf(vm).calculationOrigin)
        assertEquals("", checkNotNull(vm.state.value.draft.nutritionDraft).manualCalorieTargetText)
        assertFalse(checkNotNull(nutritionPlanTuningOf(vm.state.value)).isEdited)
    }

    @Test
    fun theSelectedPaceOfThePreviousStepIsAppliedBySeedingTheDraft() = runTest {
        val vm = vm()
        openResult(vm, automatic().copy(pacePreset = com.example.kpkn.domain.nutrition.WizardPacePreset.FAST))
        val tuning = checkNotNull(nutritionPlanTuningOf(vm.state.value))
        val seed = checkNotNull(nutritionPresetSeed(vm.state.value.draft.nutritionDraft, tuning))

        write(vm, seed)

        assertEquals(seed, valuesOf(planOf(vm)))
        val seeded = checkNotNull(nutritionPlanTuningOf(vm.state.value))
        assertFalse(seeded.isEdited)
        assertNull(nutritionPresetSeed(vm.state.value.draft.nutritionDraft, seeded))
    }

    // ─── hardStop ────────────────────────────────────────────────────────────

    @Test
    fun aPlanWithDangerouslyLowCaloriesRejectsContinuarWithoutMovingTheCursor() = runTest {
        val vm = vm()
        openResult(vm)
        write(vm, PlanValues(1100, 130, 120, 40))
        assertTrue(checkNotNull(nutritionPlanTuningOf(vm.state.value)).hardStop)

        val rejected = vm.submitCurrentStep(SetupStepId.NUTRITION_RESULT)
        advanceUntilIdle()

        assertEquals(SetupSubmitOutcome.REJECTED, rejected.outcome)
        assertEquals(NUTRITION_LOW_CALORIES_MESSAGE, vm.state.value.errors["nutritionPlan"])
        assertEquals(SetupStepId.NUTRITION_RESULT, vm.state.value.currentStep)
        assertNull("nada se confirmó", vm.state.value.draft.stepProgress.answers[SetupStepId.NUTRITION_RESULT])

        // Subiendo las calorías el paso vuelve a poder continuar.
        val tuning = checkNotNull(nutritionPlanTuningOf(vm.state.value))
        write(vm, tuning.reset().values, clear = true)
        val accepted = vm.submitCurrentStep(SetupStepId.NUTRITION_RESULT)
        advanceUntilIdle()
        assertEquals(SetupSubmitOutcome.ACCEPTED, accepted.outcome)
        assertEquals(SetupStepId.MILESTONE_NUTRITION, vm.state.value.currentStep)
    }

    @Test
    fun aPlanWithOnlyWarningsStillContinues() = runTest {
        val vm = vm()
        openResult(vm)
        write(vm, PlanValues(2200, 70, 360, 40)) // proteína y grasas bajas: avisan, no detienen
        assertTrue(checkNotNull(nutritionPlanTuningOf(vm.state.value)).warnings.isNotEmpty())

        val result = vm.submitCurrentStep(SetupStepId.NUTRITION_RESULT)
        advanceUntilIdle()

        assertEquals(SetupSubmitOutcome.ACCEPTED, result.outcome)
        assertEquals(SetupStepId.MILESTONE_NUTRITION, vm.state.value.currentStep)
    }

    // ─── Otros modos ─────────────────────────────────────────────────────────

    @Test
    fun selfDefinedKeepsTheOwnNumbersAndTheSlidersWriteOnTopOfThem() = runTest {
        val vm = vm()
        val own = NutritionWizardDraft(
            direction = PlanDirection.DEFICIT,
            equationSex = EerSex.MALE,
            configurationMode = NutritionConfigurationMode.SELF_DEFINED,
            manualCalorieTargetText = "2100",
            manualProteinText = "165",
            manualCarbsText = "180",
            manualFatText = "65",
        )
        openResult(vm, own)
        assertEquals(PlanValues(2100, 165, 180, 65), valuesOf(planOf(vm)))
        assertEquals(CalculationOrigin.MANUAL, planOf(vm).calculationOrigin)

        val tuning = checkNotNull(nutritionPlanTuningOf(vm.state.value, arrival = PlanValues(2100, 165, 180, 65)))
        assertFalse(tuning.isEdited)
        val moved = tuning.withFat(80)
        write(vm, moved.values)
        assertEquals(moved.values, valuesOf(planOf(vm)))
        assertEquals(NutritionConfigurationMode.SELF_DEFINED, vm.state.value.draft.nutritionDraft?.configurationMode)
        assertEquals(moved.values, checkNotNull(nutritionPlanTuningOf(vm.state.value, moved.baseline)).values)
    }

    @Test
    fun trackingOnlyHasNoPlanToTuneAndContinuarIsNotGated() = runTest {
        val vm = vm()
        openResult(vm, NutritionWizardDraft(configurationMode = NutritionConfigurationMode.TRACKING_ONLY))

        assertEquals(NutritionPlanPreparationStatus.TRACKING_ONLY, vm.state.value.nutritionPreparation?.status)
        assertNull(nutritionPlanTuningOf(vm.state.value))
        val result = vm.submitCurrentStep(SetupStepId.NUTRITION_RESULT)
        advanceUntilIdle()
        assertEquals(SetupSubmitOutcome.ACCEPTED, result.outcome)
    }

    @Test
    fun aBlockedEquationShowsItsErrorsAndHasNothingToTune() = runTest {
        val vm = vm()
        openResult(vm, automatic(sex = null))

        val preparation = checkNotNull(vm.state.value.nutritionPreparation)
        assertEquals(NutritionPlanPreparationStatus.BLOCKED_EQUATION, preparation.status)
        assertTrue(vm.state.value.nutritionErrors.isNotEmpty())
        assertNull(preparation.plan)
        assertNull(nutritionPlanTuningOf(vm.state.value))
        assertNotNull(vm.state.value.nutritionErrors["equationSex"])
        assertTrue(nutritionResultGate(vm.state.value, SetupStepId.NUTRITION_RESULT).isEmpty())
    }

    // ─── Fakes herméticos ────────────────────────────────────────────────────

    private class InMemoryPersistence : SetupWizardPersistence {
        val rows = LinkedHashMap<String, SetupDraft>()

        override suspend fun load(draftId: String): SetupDraft? = rows[draftId]

        override suspend fun save(draftId: String, payloadJson: String, revision: Long, catalogRevision: String?): SetupDraft =
            SetupDraft(draftId, payloadJson, revision, catalogRevision, 0L).also { rows[draftId] = it }

        override suspend fun discard(draftId: String) {
            rows.remove(draftId)
        }

        override suspend fun listRecoverable(): List<SetupDraftCandidate> =
            rows.map { (id, row) -> SetupDraftCandidate(id, SetupDraftScope.NUTRITION_ONLY, row.revision, 0L, row.catalogRevision) }
    }

    private class FixedSettingsEnvironment(override val settings: Settings) : SetupWizardEnvironment {
        override suspend fun awaitReady() = Unit
        override fun activeProgramId(): String? = null
        override fun activeNutritionPlanId(): String? = null
        override fun activeNutritionPlan(): NutritionPlan? = null
        override fun nutritionPlan(id: String): NutritionPlan? = null
        override fun hasInitialRecoveryEvidence(): Boolean = false
        override suspend fun refreshBodyProgress() = Unit
    }
}
