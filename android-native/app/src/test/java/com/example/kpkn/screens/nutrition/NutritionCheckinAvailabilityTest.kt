package com.example.kpkn.screens.nutrition

import android.app.Application
import android.content.Context
import androidx.lifecycle.viewModelScope
import androidx.test.core.app.ApplicationProvider
import com.example.kpkn.data.models.*
import com.example.kpkn.data.repository.*
import com.example.kpkn.domain.nutrition.checkin.*
import com.example.kpkn.domain.training.AppClock
import com.example.kpkn.testsupport.RoomTestRule
import java.time.*
import kotlinx.coroutines.*
import kotlinx.coroutines.flow.*
import kotlinx.coroutines.test.*
import org.junit.*
import org.junit.Assert.*
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

@OptIn(ExperimentalCoroutinesApi::class)
@RunWith(RobolectricTestRunner::class)
@Config(manifest = Config.NONE, sdk = [34], application = Application::class)
class NutritionCheckinAvailabilityTest {
    @get:Rule val roomRule = RoomTestRule()
    private val dispatcher = UnconfinedTestDispatcher()
    private val today = LocalDate.of(2026, 10, 19)
    private lateinit var vm: NutritionViewModel

    @Before fun setup() {
        Dispatchers.setMain(dispatcher)
        val context = ApplicationProvider.getApplicationContext<Context>()
        NutritionRepository.initForTests(context)
        ProgramRepository.initForTests(context)
        vm = NutritionViewModel(
            clock = object : AppClock {
                override fun now() = today.atStartOfDay(ZoneId.of("UTC")).toInstant()
                override fun today(zoneId: ZoneId) = today
            },
            computeDispatcher = dispatcher,
        )
    }

    @After fun close() {
        vm.viewModelScope.cancel()
        NutritionRepository.closeInstance()
        ProgramRepository.closeInstance()
        Dispatchers.resetMain()
    }

    @Test fun `el check-in semanal retirado preserva historial y permite ajustar objetivos y porciones`() = runBlocking {
        val context = ApplicationProvider.getApplicationContext<Context>()
        val store = com.example.kpkn.screens.nutrition.checkin.NutritionCheckinRuntime.store(context)
        val weekKey = CheckinPolicy.weekKey(today, DayOfWeek.MONDAY, today.dayOfWeek)
        store.decline(weekKey, null)

        val programs = ProgramRepository.getInstance()
        programs.updateSettings { it.copy(nutritionCheckin = CheckinPreferences(dayOfWeek = today.dayOfWeek.value)) }
        val nutrition = NutritionRepository.getInstance()
        val initialPlan = NutritionPlan(
            id = "plan-checkin",
            calorieTarget = 2200,
            proteinGoal = 160,
            carbGoal = 230,
            fatGoal = 70,
            direction = PlanDirection.MAINTENANCE,
        )
        nutrition.addNutritionPlan(initialPlan)
        vm.activatePlan("plan-checkin")
        vm.activePlan.first { it?.id == "plan-checkin" }
        nutrition.addNutritionPlan(initialPlan.copy(calorieTarget = 2450, proteinGoal = 175, isActive = true))
        val active = vm.activePlan.first { it?.calorieTarget == 2450 }
        vm.syncActivePlanGoalsToSettings()

        assertEquals(2450, active?.calorieTarget)
        assertEquals(2450, programs.settings.first { it.dailyCalorieGoal == 2450 }.dailyCalorieGoal)
        assertEquals(175, programs.settings.value.dailyProteinGoal)
        assertEquals(1, store.records.first { it.isNotEmpty() }.size)
    }
}
