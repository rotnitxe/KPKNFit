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
    private val scope = TestScope(dispatcher)
    private val today = LocalDate.of(2026, 10, 19)
    private val records = MutableStateFlow<List<CheckinRecord>>(emptyList())
    private lateinit var vm: NutritionViewModel
    private lateinit var collector: Job
    @Before fun setup() {
        Dispatchers.setMain(dispatcher)
        val context = ApplicationProvider.getApplicationContext<Context>()
        NutritionRepository.initForTests(context)
        ProgramRepository.initForTests(context)
        vm = NutritionViewModel(clock = object : AppClock {
            override fun now() = today.atStartOfDay(ZoneId.of("UTC")).toInstant()
            override fun today(zoneId: ZoneId) = today
        }, computeDispatcher = dispatcher, checkinRecords = records)
        collector = scope.launch { vm.checkinAvailability.collect {} }
    }
    @After fun close() {
        collector.cancel(); vm.viewModelScope.cancel()
        NutritionRepository.closeInstance(); ProgramRepository.closeInstance(); Dispatchers.resetMain()
    }
    @Test fun `la tarjeta pasa de NONE a NOW y desaparece al guardar la decision`() = runBlocking {
        assertEquals(CheckinAvailability.NONE, vm.checkinAvailability.value)
        val programs = ProgramRepository.getInstance()
        programs.updateSettings { it.copy(nutritionCheckin = CheckinPreferences(dayOfWeek = today.dayOfWeek.value)) }
        NutritionRepository.getInstance().addNutritionPlan(NutritionPlan(id = "plan-checkin", calorieTarget = 2200, direction = PlanDirection.MAINTENANCE))
        NutritionRepository.getInstance().activatePlan("plan-checkin")
        withTimeout(5000) { vm.checkinAvailability.first { it == CheckinAvailability.NOW } }
        val key = CheckinPolicy.weekKey(today, com.example.kpkn.screens.nutrition.checkin.checkinWeekStart(null), today.dayOfWeek)
        records.value = listOf(CheckinRecord(key, CheckinDecision.APPLIED))
        withTimeout(5000) { vm.checkinAvailability.first { it == CheckinAvailability.DISMISSED } }
        assertEquals(CheckinAvailability.DISMISSED, vm.checkinAvailability.value)
    }
}
