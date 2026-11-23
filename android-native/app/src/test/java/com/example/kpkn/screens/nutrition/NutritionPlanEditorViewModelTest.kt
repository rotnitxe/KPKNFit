package com.example.kpkn.screens.nutrition

import android.app.Application
import android.content.Context
import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.ViewModelStore
import androidx.test.core.app.ApplicationProvider
import com.example.kpkn.data.db.KpknDatabase
import com.example.kpkn.data.db.toEntity
import com.example.kpkn.data.db.toNutritionPlan
import com.example.kpkn.data.food.FoodImporter
import com.example.kpkn.data.repository.FoodCatalogImporter
import com.example.kpkn.data.models.CalculationOrigin
import com.example.kpkn.data.models.Gender
import com.example.kpkn.data.models.NutritionPlan
import com.example.kpkn.data.models.PlanDirection
import com.example.kpkn.data.models.UserVitals
import com.example.kpkn.data.models.WeightUnit
import com.example.kpkn.data.onboarding.SetupDraftRepository
import com.example.kpkn.data.repository.BodyProgressRepository
import com.example.kpkn.data.repository.NutritionPlanCommitCoordinator
import com.example.kpkn.data.repository.NutritionPlanCommitRequest
import com.example.kpkn.data.repository.NutritionRepository
import com.example.kpkn.data.repository.ProgramRepository
import com.example.kpkn.data.repository.SettingsGoalMirror
import com.example.kpkn.domain.nutrition.EerActivity
import com.example.kpkn.domain.nutrition.EerSex
import com.example.kpkn.domain.nutrition.NutritionConfigurationMode
import com.example.kpkn.domain.nutrition.NutritionEditorBase
import com.example.kpkn.domain.nutrition.NutritionEditorProvenance
import com.example.kpkn.domain.nutrition.NutritionPlanEditorDraft
import com.example.kpkn.domain.nutrition.NutritionWizardDraft
import com.example.kpkn.domain.nutrition.editorDraftOf
import com.example.kpkn.domain.nutrition.withCalories
import com.example.kpkn.domain.nutrition.withMacro
import com.example.kpkn.testsupport.SyncRoomExecutor
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.delay
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.test.UnconfinedTestDispatcher
import kotlinx.coroutines.test.resetMain
import kotlinx.coroutines.test.setMain
import kotlinx.coroutines.withTimeout
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import java.time.LocalDate

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [34], application = Application::class)
@OptIn(ExperimentalCoroutinesApi::class)
class NutritionPlanEditorViewModelTest {

    private val mainDispatcher = UnconfinedTestDispatcher()
    private lateinit var context: Context
    private lateinit var programRepo: ProgramRepository
    private lateinit var nutritionRepo: NutritionRepository
    private lateinit var coordinator: NutritionPlanCommitCoordinator
    private lateinit var store: ViewModelStore

    private val noImport = object : FoodCatalogImporter {
        override suspend fun importIfNeeded(
            db: KpknDatabase,
            context: Context,
            alreadyImported: Boolean,
            existingMeta: FoodImporter.ImportMetadata?,
            onMetaUpdated: (FoodImporter.ImportMetadata) -> Unit,
        ): Boolean = false
    }

    @Before
    fun setUp() {
        SyncRoomExecutor.install()
        Dispatchers.setMain(mainDispatcher)
        context = ApplicationProvider.getApplicationContext()
        NutritionRepository.closeInstance()
        BodyProgressRepository.closeInstance()
        ProgramRepository.closeInstance()
        KpknDatabase.closeInstance()
        programRepo = ProgramRepository.initForTests(context)
        nutritionRepo = NutritionRepository.initForTests(context, noImport)
        coordinator = NutritionPlanCommitCoordinator(
            db = nutritionRepo.databaseForTests(),
            nutritionRepository = nutritionRepo,
            programRepository = programRepo,
        )
        store = ViewModelStore()
    }

    @After
    fun tearDown() {
        store.clear()
        NutritionRepository.closeInstance()
        BodyProgressRepository.closeInstance()
        ProgramRepository.closeInstance()
        KpknDatabase.closeInstance()
        Dispatchers.resetMain()
        SyncRoomExecutor.uninstall()
    }

    private fun createViewModel(): NutritionPlanEditorViewModel {
        val factory = object : ViewModelProvider.Factory {
            @Suppress("UNCHECKED_CAST")
            override fun <T : ViewModel> create(modelClass: Class<T>): T = NutritionPlanEditorViewModel(
                nutritionRepository = nutritionRepo,
                programRepository = programRepo,
                commits = coordinator,
                drafts = SetupDraftRepository(nutritionRepo.databaseForTests()),
            ) as T
        }
        return ViewModelProvider(store, factory)[NutritionPlanEditorViewModel::class.java]
    }

    private suspend fun awaitLoaded(vm: NutritionPlanEditorViewModel) {
        withTimeout(5_000L) {
            while (vm.uiState.value.isLoading) delay(10)
        }
    }

    private suspend fun awaitSaved(vm: NutritionPlanEditorViewModel) {
        withTimeout(5_000L) {
            while (vm.uiState.value.isSaving) delay(10)
        }
    }

    @Test
    fun automaticWizardDraftPreservesAutomaticProvenanceAndActivity() {
        val wizard = NutritionWizardDraft(
            ageText = "28",
            heightText = "175",
            weightText = "80",
            equationSex = EerSex.MALE,
            activity = EerActivity.VERY_ACTIVE,
            configurationMode = NutritionConfigurationMode.AUTOMATIC,
        )
        val editorDraft = editorDraftOf(wizard)
        assertEquals(NutritionEditorProvenance.AUTOMATIC, editorDraft.provenance)
        assertEquals(EerActivity.VERY_ACTIVE, editorDraft.activity)
        assertEquals(false, editorDraft.baseEdited)
    }

    @Test
    fun typingCaloriesFromAnchorNeverCollapsesCarbsToZero() {
        val anchor = NutritionEditorBase(caloriesKcal = 2500, proteinG = 180, carbsG = 280, fatG = 73)
        val at2 = anchor.withCalories(2, anchor = anchor)
        val backTo2000 = at2.withCalories(2000, anchor = anchor)
        assertTrue("Carbs must remain positive when restoring from anchor", backTo2000.carbsG > 100)
        assertTrue("Protein must remain positive", backTo2000.proteinG > 100)
        assertTrue("Fat must remain positive", backTo2000.fatG > 30)
    }

    @Test
    fun editingMacroUpdatesAtwaterCaloriesAndUsesFallbackBaseWhenBaseIsNull() {
        val draft = NutritionPlanEditorDraft(base = null)
        val fallback = NutritionEditorBase(caloriesKcal = 2200, proteinG = 160, carbsG = 240, fatG = 67)
        val updated = draft.withMacro(protein = 180.0, fallbackBase = fallback)
        assertEquals(180, updated.base?.proteinG)
        assertEquals(240, updated.base?.carbsG)
        assertEquals(67, updated.base?.fatG)
        assertEquals(2283, updated.base?.caloriesKcal)
    }

    @Test
    fun manualWizardDraftWithExplicitBaseMarksBaseEditedTrue() {
        val manualWizard = NutritionWizardDraft(
            ageText = "28",
            heightText = "175",
            weightText = "80",
            equationSex = EerSex.MALE,
            activity = EerActivity.ACTIVE,
            manualCalorieTargetText = "2400",
            manualProteinText = "170",
            manualCarbsText = "260",
            manualFatText = "75",
        )
        val editorDraft = editorDraftOf(manualWizard)
        assertEquals(true, editorDraft.baseEdited)
        assertEquals(2400, editorDraft.base?.caloriesKcal)
    }

    @Test
    fun realViewModelNormalizesLbsUnitResetsAnchorOnZeroCarbsVsCollapseAndPreservesGenderOther() = runBlocking {
        programRepo.updateSettings {
            it.copy(
                weightUnit = WeightUnit.LBS,
                userVitals = UserVitals(age = 28, height = 178.0, weight = 80.0, gender = Gender.OTHER),
            )
        }
        nutritionRepo.databaseForTests().settingsDao().upsert(programRepo.settings.value.toEntity())
        val vm = createViewModel()
        vm.initialize(planId = null, pendingDraftId = null)
        awaitLoaded(vm)

        val initialDraft = vm.uiState.value.draft
        assertEquals("lb", initialDraft.weightUnit)
        val parsedLbs = com.example.kpkn.domain.nutrition.parseLocalizedNumber(initialDraft.weightText)!!
        assertTrue("Expected ~176.4 lbs for 80 kg, got $parsedLbs", parsedLbs > 170.0 && parsedLbs < 182.0)

        // Zeroed carbs intentionally vs collapsed anchor recovery
        vm.setDirection(PlanDirection.MAINTENANCE)
        vm.setAge("28")
        vm.setHeight("178")
        vm.setWeight("176")
        vm.setEquationSex(EerSex.AVERAGE)

        // Typing low calories without explicitly zeroing carbs keeps balanced anchor (>100g carbs when returning to 2000)
        vm.setCalories(2)
        vm.setCalories(2000)
        val recoveredBase = vm.uiState.value.base!!
        assertTrue("Expected balanced carbs > 100g, got ${recoveredBase.carbsG}", recoveredBase.carbsG > 100)

        // Explicitly zeroing carbs keeps 0 carbs when scaling calories
        vm.setMacro(carbs = 0.0)
        vm.setCalories(2200)
        assertEquals(0, vm.uiState.value.base!!.carbsG)

        // Saving with EerSex.AVERAGE must NOT overwrite Gender.OTHER in Settings
        vm.setMacro(protein = 160.0, carbs = 220.0, fat = 70.0)
        vm.save()
        awaitSaved(vm)
        assertEquals(Gender.OTHER, programRepo.settings.value.userVitals.gender)
        assertTrue(vm.uiState.value.isSaved)
    }

    @Test
    fun editingExistingPlanUpdatesTodayDailyGoalSnapshotImmediatelyOnSave() = runBlocking {
        val original = NutritionPlan(
            id = "plan-edit-1",
            name = "Plan Original",
            calorieTarget = 1900,
            proteinGoal = 150,
            carbGoal = 190,
            fatGoal = 60,
            direction = PlanDirection.DEFICIT,
            calculationOrigin = CalculationOrigin.MANUAL,
            isActive = true,
        )
        coordinator.commit(
            NutritionPlanCommitRequest(
                commitId = "seed-plan-1",
                plan = original,
                activatePlan = true,
                trackingOnly = false,
                derivedBodyGoals = emptyList(),
                settingsGoalMirror = SettingsGoalMirror(1900, 150, 190, 60),
                captureTodaySnapshot = true,
                updateTodaySnapshot = true,
            ),
        )
        val todayStr = LocalDate.now().toString()
        assertEquals(
            1900,
            nutritionRepo.databaseForTests().nutritionDao().getDailyGoalSnapshot(todayStr)?.calorieTargetKcal,
        )

        val vm = createViewModel()
        vm.initialize(planId = original.id, pendingDraftId = null)
        awaitLoaded(vm)

        vm.setMacro(protein = 180.0, carbs = 275.0, fat = 70.0) // 4*180 + 4*275 + 9*70 = 2450 kcal
        vm.save()
        awaitSaved(vm)

        assertTrue(vm.uiState.value.isSaved)
        val todayAfter = nutritionRepo.databaseForTests().nutritionDao().getDailyGoalSnapshot(todayStr)
        assertEquals(2450, todayAfter?.calorieTargetKcal)
        val storedPlan = nutritionRepo.databaseForTests().nutritionDao().getAllPlans()
            .first { it.id == original.id }
            .toNutritionPlan()
        assertEquals(2450, storedPlan.calorieTarget)
    }
}
