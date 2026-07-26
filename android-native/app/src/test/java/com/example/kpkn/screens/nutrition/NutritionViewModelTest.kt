package com.example.kpkn.screens.nutrition

import android.content.Context
import androidx.test.core.app.ApplicationProvider
import com.example.kpkn.data.models.*
import com.example.kpkn.data.repository.NutritionRepository
import com.example.kpkn.data.repository.ProgramRepository
import com.example.kpkn.domain.nutrition.*
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.Job
import kotlinx.coroutines.launch
import kotlinx.coroutines.test.*
import org.junit.After
import org.junit.Assert.*
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import java.util.UUID

@OptIn(ExperimentalCoroutinesApi::class)
@RunWith(RobolectricTestRunner::class)
@Config(manifest = Config.NONE, sdk = [34])
class NutritionViewModelTest {

    private val testDispatcher = UnconfinedTestDispatcher()
    private val testScope = TestScope(testDispatcher)
    private lateinit var nutritionRepo: NutritionRepository
    private lateinit var programRepo: ProgramRepository
    private lateinit var vm: NutritionViewModel
    private val collectors = mutableListOf<Job>()

    @Before
    fun setup() {
        Dispatchers.setMain(testDispatcher)
        val context = ApplicationProvider.getApplicationContext<Context>()
        NutritionRepository.initForTests(context)
        ProgramRepository.initForTests(context)
        nutritionRepo = NutritionRepository.getInstance()
        programRepo = ProgramRepository.getInstance()

        // Clear shared state
        nutritionRepo.clearNutritionLogs()
        nutritionRepo.setActiveNutritionPlanId(null)
        programRepo.clearPrograms()
        programRepo.clearActiveProgram()
        programRepo.clearOngoingWorkout()

        vm = NutritionViewModel()

        collectors += testScope.launch { vm.todayLogs.collect { } }
        collectors += testScope.launch { vm.dailyTotals.collect { } }
        collectors += testScope.launch { vm.mealGroups.collect { } }
        collectors += testScope.launch { vm.activePlan.collect { } }
        collectors += testScope.launch { vm.goals.collect { } }
    }

    @After
    fun tearDown() {
        collectors.forEach { it.cancel() }
        collectors.clear()
        NutritionRepository.closeInstance()
        ProgramRepository.closeInstance()
        Dispatchers.resetMain()
    }

    // ─── Log Management ────────────────────────────────────────────────────

    @Test
    fun `add log increases today logs`() {
        val log = NutritionLog(
            id = UUID.randomUUID().toString(),
            date = java.time.LocalDate.now().toString() + "T12:00:00.000Z",
            mealType = MealType.LUNCH,
            foods = listOf(LoggedFood(id = "f1", foodName = "Arroz", calories = 200.0, protein = 5.0, carbs = 40.0, fats = 1.0)),
        )
        vm.addLog(log)

        val todayLogs = vm.todayLogs.value
        assertTrue(todayLogs.isNotEmpty())
        assertEquals(log.id, todayLogs.first().id)
    }

    @Test
    fun `delete log removes it`() {
        val id = UUID.randomUUID().toString()
        val log = NutritionLog(
            id = id,
            date = java.time.LocalDate.now().toString() + "T12:00:00.000Z",
            mealType = MealType.LUNCH,
            foods = listOf(LoggedFood(id = "f1", foodName = "Arroz", calories = 200.0)),
        )
        vm.addLog(log)
        assertEquals(1, vm.todayLogs.value.size)

        vm.deleteLog(id)
        assertEquals(0, vm.todayLogs.value.size)
    }

    @Test
    fun `duplicate log creates new log`() {
        val original = NutritionLog(
            id = "original",
            date = java.time.LocalDate.now().toString() + "T12:00:00.000Z",
            mealType = MealType.BREAKFAST,
            foods = listOf(LoggedFood(id = "f1", foodName = "Avena", calories = 300.0)),
            notes = "Nota",
        )
        vm.addLog(original)

        vm.duplicateLog(original)
        val logs = vm.todayLogs.value
        assertTrue(logs.size >= 2)
        assertTrue(logs.any { it.notes?.contains("duplicado") == true })
    }

    // ─── Daily Totals ──────────────────────────────────────────────────────

    @Test
    fun `daily totals compute correctly`() {
        val log1 = NutritionLog(
            id = "l1",
            date = java.time.LocalDate.now().toString() + "T08:00:00.000Z",
            mealType = MealType.BREAKFAST,
            foods = listOf(
                LoggedFood(id = "f1", foodName = "Avena", calories = 300.0, protein = 10.0, carbs = 50.0, fats = 5.0),
            ),
        )
        val log2 = NutritionLog(
            id = "l2",
            date = java.time.LocalDate.now().toString() + "T12:00:00.000Z",
            mealType = MealType.LUNCH,
            foods = listOf(
                LoggedFood(id = "f2", foodName = "Pollo", calories = 400.0, protein = 40.0, carbs = 0.0, fats = 10.0),
            ),
        )
        vm.addLog(log1)
        vm.addLog(log2)

        val totals = vm.dailyTotals.value
        assertEquals(700.0, totals.calories, 0.01)
        assertEquals(50.0, totals.protein, 0.01)
    }

    // ─── Meal Groups ───────────────────────────────────────────────────────

    @Test
    fun `meal groups filter by meal type`() {
        val breakfast = NutritionLog(
            id = "b1",
            date = java.time.LocalDate.now().toString() + "T08:00:00.000Z",
            mealType = MealType.BREAKFAST,
            foods = listOf(LoggedFood(id = "f1", foodName = "Avena", calories = 300.0)),
        )
        val lunch = NutritionLog(
            id = "l1",
            date = java.time.LocalDate.now().toString() + "T12:00:00.000Z",
            mealType = MealType.LUNCH,
            foods = listOf(LoggedFood(id = "f2", foodName = "Pollo", calories = 400.0)),
        )
        vm.addLog(breakfast)
        vm.addLog(lunch)

        val groups = vm.mealGroups.value
        assertEquals(4, groups.size) // BREAKFAST, LUNCH, DINNER, SNACK
        val breakfastGroup = groups.find { it.mealType == MealType.BREAKFAST }
        assertEquals(1, breakfastGroup?.logs?.size)
        assertEquals(300.0, breakfastGroup?.totals?.calories ?: 0.0, 0.01)
    }

    // ─── Goals ─────────────────────────────────────────────────────────────

    @Test
    fun `goals default values`() {
        val goals = vm.goals.value
        assertTrue(goals.calorieGoal > 0)
        assertTrue(goals.proteinGoal > 0)
        assertTrue(goals.carbGoal > 0)
        assertTrue(goals.fatGoal > 0)
    }

    // ─── Plan Management ───────────────────────────────────────────────────

    @Test
    fun `create plan sets active`() {
        val plan = NutritionPlan(
            id = "plan1",
            name = "Test Plan",
            calorieTarget = 2000,
            proteinGoal = 150,
            carbGoal = 200,
            fatGoal = 60,
            isActive = true,
            createdAt = java.time.Instant.now().toString(),
        )
        vm.createPlan(plan)

        val active = vm.activePlan.value
        assertNotNull(active)
        assertEquals("plan1", active?.id)
        assertEquals(2000, active?.calorieTarget)
    }

    @Test
    fun `activate plan changes active`() {
        val plan1 = NutritionPlan(id = "p1", name = "Plan 1", isActive = true)
        val plan2 = NutritionPlan(id = "p2", name = "Plan 2")
        vm.createPlan(plan1)
        nutritionRepo.addNutritionPlan(plan2)

        vm.activatePlan("p2")
        val active = vm.activePlan.value
        assertEquals("p2", active?.id)
    }

    // ─── Date Selection ────────────────────────────────────────────────────

    @Test
    fun `set selected date updates state`() {
        val newDate = "2025-06-15"
        vm.setSelectedDate(newDate)
        assertEquals(newDate, vm.selectedDate.value)
    }

    // ─── Wizard State ──────────────────────────────────────────────────────

    // TODO: Re-enable when showWizard is added to NutritionViewModel
    /*
    @Test
    fun `show wizard toggles`() {
        vm.setShowWizard(true)
        assertTrue(vm.showWizard.value)

        vm.setShowWizard(false)
        assertFalse(vm.showWizard.value)
    }
    */
}
