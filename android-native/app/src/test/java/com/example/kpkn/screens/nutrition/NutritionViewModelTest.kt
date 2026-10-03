package com.example.kpkn.screens.nutrition

import android.content.Context
import androidx.test.core.app.ApplicationProvider
import com.example.kpkn.data.models.*
import com.example.kpkn.data.repository.NutritionRepository
import com.example.kpkn.data.repository.ProgramRepository
import com.example.kpkn.domain.nutrition.*
import com.example.kpkn.domain.training.AppClock
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
import java.time.Instant
import java.time.LocalDate
import java.time.ZoneId
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

    /** deleteLog/duplicateLog finish on Dispatchers.IO; wait until StateFlow reflects them. */
    private fun awaitTodayLogs(timeoutMs: Long = 5_000, condition: (List<NutritionLog>) -> Boolean) {
        val deadline = System.currentTimeMillis() + timeoutMs
        while (System.currentTimeMillis() < deadline) {
            if (condition(vm.todayLogs.value)) return
            Thread.sleep(10)
        }
        assertTrue(
            "todayLogs did not satisfy condition within ${timeoutMs}ms; size=${vm.todayLogs.value.size}",
            condition(vm.todayLogs.value),
        )
    }

    // ─── Log Management ────────────────────────────────────────────────────

    @Test
    fun `add log increases today logs`() {
        val log = NutritionLog(
            id = UUID.randomUUID().toString(),
            date = java.time.LocalDate.now().toString() + "T12:00:00.000Z",
            mealType = MealType.LUNCH,
            foods = listOf(LoggedFood(id = "f1", foodName = "Arroz", amount = 100.0, calories = 200.0, protein = 5.0, carbs = 40.0, fats = 1.0)),
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
            foods = listOf(LoggedFood(id = "f1", foodName = "Arroz", amount = 100.0, calories = 200.0)),
        )
        vm.addLog(log)
        assertEquals(1, vm.todayLogs.value.size)

        vm.deleteLog(id)
        awaitTodayLogs { it.isEmpty() }
    }

    @Test
    fun `duplicate log creates new log`() {
        val original = NutritionLog(
            id = "original",
            date = java.time.LocalDate.now().toString() + "T12:00:00.000Z",
            mealType = MealType.BREAKFAST,
            foods = listOf(LoggedFood(id = "f1", foodName = "Avena", amount = 100.0, calories = 300.0)),
            notes = "Nota",
        )
        vm.addLog(original)

        vm.duplicateLog(original)
        awaitTodayLogs { logs -> logs.size >= 2 && logs.any { it.notes?.contains("duplicado") == true } }
    }

    // ─── Daily Totals ──────────────────────────────────────────────────────

    @Test
    fun `daily totals compute correctly`() {
        val log1 = NutritionLog(
            id = "l1",
            date = java.time.LocalDate.now().toString() + "T08:00:00.000Z",
            mealType = MealType.BREAKFAST,
            foods = listOf(
                LoggedFood(id = "f1", foodName = "Avena", amount = 100.0, calories = 300.0, protein = 10.0, carbs = 50.0, fats = 5.0),
            ),
        )
        val log2 = NutritionLog(
            id = "l2",
            date = java.time.LocalDate.now().toString() + "T12:00:00.000Z",
            mealType = MealType.LUNCH,
            foods = listOf(
                LoggedFood(id = "f2", foodName = "Pollo", amount = 100.0, calories = 400.0, protein = 40.0, carbs = 0.0, fats = 10.0),
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
            foods = listOf(LoggedFood(id = "f1", foodName = "Avena", amount = 100.0, calories = 300.0)),
        )
        val lunch = NutritionLog(
            id = "l1",
            date = java.time.LocalDate.now().toString() + "T12:00:00.000Z",
            mealType = MealType.LUNCH,
            foods = listOf(LoggedFood(id = "f2", foodName = "Pollo", amount = 100.0, calories = 400.0)),
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
    fun `goals without plan or settings are absent instead of default values`() {
        val goals = vm.goals.value
        // Sin plan ni objetivos en ajustes: ausencia explícita y ningún
        // default fabricado de 2500/150/250/70.
        assertTrue(goals is DayGoalsResult.Absent)
    }

    @Test
    fun `goals show explicit zeros from the plan as zero not as absence`() {
        programRepo.updateSettings {
            it.copy(
                dailyCalorieGoal = null,
                dailyProteinGoal = null,
                dailyCarbGoal = null,
                dailyFatGoal = null,
            )
        }
        vm.createPlan(
            NutritionPlan(
                id = "zero-plan",
                name = "Zero",
                calorieTarget = 0,
                proteinGoal = 0,
                carbGoal = 0,
                fatGoal = 0,
                isActive = true,
                createdAt = java.time.Instant.now().toString(),
            ),
        )
        val goals = vm.goals.value as DayGoalsResult.Present
        assertEquals(0, goals.goals.calorieGoal)
        assertEquals(0, goals.goals.proteinGoal)
        assertEquals(0, goals.goals.carbGoal)
        assertEquals(0, goals.goals.fatGoal)
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

    @Test
    fun `activating a different plan the same day replaces todays stored goal`() {
        fun plan(id: String, kcal: Int) = NutritionPlan(
            id = id,
            name = "Plan $id",
            calorieTarget = kcal,
            proteinGoal = 150,
            carbGoal = 200,
            fatGoal = 60,
            isActive = true,
            createdAt = java.time.Instant.now().toString(),
        )

        vm.createPlan(plan("goal-a", 2000))
        awaitStoredTodayGoal { goal -> goal != null && goal.planId == "goal-a" && goal.calorieTargetKcal == 2000 }

        // Un plan DISTINTO el mismo día: la meta de hoy en Room pasa a ser la suya.
        vm.createPlan(plan("goal-b", 2600))
        awaitStoredTodayGoal { goal -> goal != null && goal.planId == "goal-b" && goal.calorieTargetKcal == 2600 }
    }

    /** activatePlan fija la meta en Dispatchers.IO; espera a que Room la refleje. */
    private fun awaitStoredTodayGoal(timeoutMs: Long = 5_000, condition: (DailyGoalSnapshot?) -> Boolean) {
        val today = java.time.LocalDate.now().toString()
        val deadline = System.currentTimeMillis() + timeoutMs
        var last: DailyGoalSnapshot? = null
        while (System.currentTimeMillis() < deadline) {
            last = kotlinx.coroutines.runBlocking { nutritionRepo.getDailyGoalSnapshot(today) }
            if (condition(last)) return
            Thread.sleep(10)
        }
        fail("La meta de hoy guardada no cumplió la condición en ${timeoutMs}ms; última=$last")
    }

    // ─── Date Selection ────────────────────────────────────────────────────

    @Test
    fun `set selected date updates state`() {
        val newDate = "2025-06-15"
        vm.setSelectedDate(newDate)
        assertEquals(newDate, vm.selectedDate.value)
    }

    // ─── Date rollover: «hoy» avanza con el calendario (C1) ────────────────

    private val santiago: ZoneId = ZoneId.of("America/Santiago")

    // 2026-07-10 23:59:30 en Santiago (UTC-4, invierno): faltan 30 s para la medianoche.
    private val justBeforeMidnight: Instant = Instant.parse("2026-07-11T03:59:30Z")

    // 2026-07-11 00:00:05 en Santiago: ya es otro día.
    private val justAfterMidnight: Instant = Instant.parse("2026-07-11T04:00:05Z")

    /**
     * Reloj manual: el test fija «ahora»; el VM solo lo relee al refrescar o cuando salta su ticker.
     * Ojo: cada VM arma un ticker de medianoche que se re-programa solo (nunca queda ocioso), así que
     * en estos tests el tiempo virtual se avanza con `advanceTimeBy` + `runCurrent`, nunca con
     * `advanceUntilIdle()` (no terminaría).
     */
    private class FakeAppClock(var current: Instant) : AppClock {
        override fun now(): Instant = current
        override fun today(zoneId: ZoneId): LocalDate = current.atZone(zoneId).toLocalDate()
    }

    private fun dayViewModel(clock: FakeAppClock): NutritionViewModel =
        NutritionViewModel(clock = clock, zoneProvider = { santiago })

    @Test
    fun `selected date follows today across a simulated midnight`() {
        val clock = FakeAppClock(justBeforeMidnight)
        val dayVm = dayViewModel(clock)
        assertEquals(LocalDate.of(2026, 7, 10), dayVm.today.value)
        assertEquals("2026-07-10", dayVm.selectedDate.value)
        assertTrue(dayVm.followsToday)

        clock.current = justAfterMidnight
        dayVm.refreshToday()

        assertEquals(LocalDate.of(2026, 7, 11), dayVm.today.value)
        assertEquals("2026-07-11", dayVm.selectedDate.value)
        assertTrue(dayVm.followsToday)
    }

    @Test
    fun `refreshing within the same day changes nothing`() {
        val clock = FakeAppClock(justBeforeMidnight)
        val dayVm = dayViewModel(clock)

        clock.current = Instant.parse("2026-07-11T03:59:59Z") // sigue siendo el día 10
        dayVm.refreshToday()

        assertEquals(LocalDate.of(2026, 7, 10), dayVm.today.value)
        assertEquals("2026-07-10", dayVm.selectedDate.value)
    }

    @Test
    fun `an explicitly selected past date is kept when midnight passes`() {
        val clock = FakeAppClock(justBeforeMidnight)
        val dayVm = dayViewModel(clock)

        dayVm.setSelectedDate("2026-07-08")
        assertFalse(dayVm.followsToday)

        clock.current = justAfterMidnight
        dayVm.refreshToday()

        assertEquals("2026-07-08", dayVm.selectedDate.value) // la elección explícita se respeta
        assertEquals(LocalDate.of(2026, 7, 11), dayVm.today.value) // pero hoy sí avanzó
        assertFalse(dayVm.followsToday)
    }

    @Test
    fun `reselecting today re-enables following`() {
        val clock = FakeAppClock(justBeforeMidnight)
        val dayVm = dayViewModel(clock)
        dayVm.setSelectedDate("2026-07-08")
        assertFalse(dayVm.followsToday)

        dayVm.setSelectedDate(dayVm.today.value.toString())
        assertTrue(dayVm.followsToday)

        clock.current = justAfterMidnight
        dayVm.refreshToday()
        assertEquals("2026-07-11", dayVm.selectedDate.value)
    }

    @Test
    fun `midnight ticker moves today without any screen call`() {
        val clock = FakeAppClock(justBeforeMidnight)
        val dayVm = dayViewModel(clock) // el ticker espera 30 s hasta medianoche + 1 s de margen

        // El reloj ya cruzó la medianoche, pero el VM solo se entera cuando salta su ticker.
        clock.current = justAfterMidnight
        testDispatcher.scheduler.advanceTimeBy(30_999)
        assertEquals("2026-07-10", dayVm.selectedDate.value)

        testDispatcher.scheduler.advanceTimeBy(1)
        testDispatcher.scheduler.runCurrent()
        assertEquals(LocalDate.of(2026, 7, 11), dayVm.today.value)
        assertEquals("2026-07-11", dayVm.selectedDate.value)
    }

    @Test
    fun `history series is anchored on the view model today and rolls with it`() {
        val clock = FakeAppClock(justBeforeMidnight)
        val dayVm = dayViewModel(clock)
        collectors += testScope.launch { dayVm.historySeries.collect { } }
        assertEquals(LocalDate.of(2026, 7, 10), dayVm.historySeries.value.points.last().date)

        clock.current = justAfterMidnight
        dayVm.refreshToday()

        val series = dayVm.historySeries.value
        assertEquals(LocalDate.of(2026, 7, 11), series.points.last().date)
        assertEquals(LocalDate.of(2026, 6, 12), series.points.first().date) // ventana de 30 días
        assertEquals(30, series.points.size)
    }

    // ─── Balance energético: una fecha rota no tumba el flujo (C14) ────────

    /** ProgramRepository carga Room en segundo plano y publica el historial al terminar. */
    private fun awaitProgramRepositoryReady(timeoutMs: Long = 10_000) {
        val deadline = System.currentTimeMillis() + timeoutMs
        while (!programRepo.isReady.value && System.currentTimeMillis() < deadline) Thread.sleep(10)
        assertTrue("ProgramRepository no terminó de cargar en ${timeoutMs}ms", programRepo.isReady.value)
    }

    private fun workoutLog(id: String, date: String, burnKcal: Int, actualDate: String? = null) = WorkoutLog(
        id = id,
        programId = "program-$id",
        sessionId = "session-$id",
        sessionName = "Sesión $id",
        date = date,
        durationMinutes = 45,
        actualDate = actualDate,
        energySummary = SessionEnergySummary(
            totalKcal = CalorieRange(low = burnKcal, mid = burnKcal, high = burnKcal),
        ),
    )

    @Test
    fun `energy balance ignores workouts with unparseable dates instead of crashing`() {
        awaitProgramRepositoryReady()
        collectors += testScope.launch { vm.dailyEnergyBalance.collect { } }
        val today = LocalDate.now()

        // Fecha importada/legacy no ISO: antes LocalDate.parse lanzaba dentro del flow y lo mataba.
        programRepo.addWorkoutLog(workoutLog(id = "broken", date = "not-a-date", burnKcal = 999))
        assertEquals(0, vm.dailyEnergyBalance.value.trainingBurnKcal)

        // Con actualDate válida cuenta; con solo el prefijo de fecha también; la rota sigue fuera.
        programRepo.addWorkoutLog(
            workoutLog(id = "valid", date = "${today}T10:00:00.000Z", burnKcal = 300, actualDate = today.toString()),
        )
        programRepo.addWorkoutLog(workoutLog(id = "date-only", date = today.toString(), burnKcal = 50))

        assertEquals(350, vm.dailyEnergyBalance.value.trainingBurnKcal)
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
