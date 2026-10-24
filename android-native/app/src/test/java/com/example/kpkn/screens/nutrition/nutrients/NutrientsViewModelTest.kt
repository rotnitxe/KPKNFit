package com.example.kpkn.screens.nutrition.nutrients

import android.app.Application
import android.content.Context
import androidx.test.core.app.ApplicationProvider
import androidx.lifecycle.viewModelScope
import com.example.kpkn.data.models.*
import com.example.kpkn.data.repository.NutritionRepository
import com.example.kpkn.data.repository.ProgramRepository
import com.example.kpkn.domain.nutrition.NutrientKey
import com.example.kpkn.domain.training.AppClock
import com.example.kpkn.screens.nutrition.NutritionViewModel
import com.example.kpkn.testsupport.RoomTestRule
import kotlinx.coroutines.*
import kotlinx.coroutines.test.*
import org.junit.*
import org.junit.Assert.*
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import java.time.*

@OptIn(ExperimentalCoroutinesApi::class)
@RunWith(RobolectricTestRunner::class)
@Config(manifest = Config.NONE, sdk = [34], application = Application::class)
class NutrientsViewModelTest {
    @get:Rule val roomRule = RoomTestRule()
    private val dispatcher = UnconfinedTestDispatcher()
    private val scope = TestScope(dispatcher)
    private lateinit var repo: NutritionRepository
    private lateinit var model: NutritionViewModel
    private lateinit var collector: Job
    @Before fun setup() {
        Dispatchers.setMain(dispatcher)
        val context = ApplicationProvider.getApplicationContext<Context>()
        repo = NutritionRepository.initForTests(context)
        ProgramRepository.initForTests(context)
        val clock = object : AppClock {
            override fun now(): Instant = Instant.parse("2026-10-24T12:00:00Z")
            override fun today(zoneId: ZoneId): LocalDate = LocalDate.of(2026, 10, 24)
        }
        model = NutritionViewModel(clock = clock, computeDispatcher = dispatcher)
        collector = scope.launch { model.nutrients.collect {} }
    }
    @After fun cleanup() {
        collector.cancel(); model.viewModelScope.cancel()
        NutritionRepository.closeInstance(); ProgramRepository.closeInstance(); Dispatchers.resetMain()
    }
    @Test fun cambiarFechaRecalculaYExcluyeComidasPlanificadas() = runBlocking {
        val food = LoggedFood(foodName = "Ficha", amount = 100.0, calories = 100.0, sodiumMg = 10.0)
        repo.saveNutritionLog(NutritionLog(id = "hoy", date = "2026-10-24T23:30:00-03:00", foods = listOf(food)))
        repo.saveNutritionLog(NutritionLog(id = "plan", date = "2026-10-24T12:00:00-03:00", status = NutritionStatus.PLANNED, foods = listOf(food.copy(sodiumMg = 100.0))))
        repo.saveNutritionLog(NutritionLog(id = "manana", date = "2026-10-25T08:00:00-03:00", foods = listOf(food.copy(sodiumMg = 25.0))))
        dispatcher.scheduler.runCurrent()
        assertEquals(10.0, model.nutrients.value.rows.single { it.nutrient == NutrientKey.SODIUM }.totalKnown!!, 0.0)
        model.setSelectedDate("2026-10-25")
        dispatcher.scheduler.runCurrent()
        assertEquals("2026-10-25", model.nutrients.value.date)
        assertEquals(25.0, model.nutrients.value.rows.single { it.nutrient == NutrientKey.SODIUM }.totalKnown!!, 0.0)
        assertNull(model.nutrients.value.profile)
    }
}
