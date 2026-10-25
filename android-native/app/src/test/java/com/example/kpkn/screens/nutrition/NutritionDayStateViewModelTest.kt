package com.example.kpkn.screens.nutrition

import android.app.Application
import android.content.Context
import androidx.test.core.app.ApplicationProvider
import androidx.lifecycle.viewModelScope
import com.example.kpkn.data.models.*
import com.example.kpkn.data.repository.NutritionRepository
import com.example.kpkn.data.repository.ProgramRepository
import com.example.kpkn.domain.nutrition.expenditure.*
import com.example.kpkn.domain.training.AppClock
import com.example.kpkn.testsupport.RoomTestRule
import kotlinx.coroutines.*
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.test.*
import org.junit.*
import org.junit.Assert.*
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import java.time.Instant
import java.time.LocalDate
import java.time.ZoneId

@OptIn(ExperimentalCoroutinesApi::class)
@RunWith(RobolectricTestRunner::class)
@Config(manifest = Config.NONE, sdk = [34], application = Application::class)
class NutritionDayStateViewModelTest {
    @get:Rule val roomRule = RoomTestRule()
    private val dispatcher = UnconfinedTestDispatcher()
    private val scope = TestScope(dispatcher)
    private val day = LocalDate.of(2026, 10, 20)
    private val collectors = mutableListOf<Job>()
    private val models = mutableListOf<NutritionViewModel>()
    private lateinit var repo: NutritionRepository
    private lateinit var vm: NutritionViewModel
    private val clock = object : AppClock {
        override fun now(): Instant = Instant.parse("2026-10-21T12:00:00Z")
        override fun today(zoneId: ZoneId): LocalDate = day.plusDays(1)
    }

    @Before fun setup() {
        Dispatchers.setMain(dispatcher)
        val context = ApplicationProvider.getApplicationContext<Context>()
        repo = NutritionRepository.initForTests(context)
        ProgramRepository.initForTests(context)
        vm = model()
    }
    private fun model(): NutritionViewModel = NutritionViewModel(clock = clock, computeDispatcher = dispatcher).also {
        models += it
        it.setSelectedDate(day.toString())
        collectors += scope.launch { it.dayState.collect {} }
    }
    @After fun teardown() {
        collectors.forEach { it.cancel() }
        models.forEach { it.viewModelScope.cancel() }
        NutritionRepository.closeInstance()
        ProgramRepository.closeInstance()
        Dispatchers.resetMain()
    }

    private suspend fun meal() {
        repo.saveNutritionLog(NutritionLog(id = "colacion", date = day.toString(),
            foods = listOf(LoggedFood(foodName = "Colación", amount = 100.0, calories = 150.0))))
    }

    @Test fun `marcar y quitar actualizan el estado tras el commit`() = runBlocking {
        meal()
        vm.setDayMark(DayMark.PARTIAL).join()
        assertEquals(DayMark.PARTIAL, vm.dayState.value.mark)
        assertEquals(DayMark.PARTIAL, repo.dayMarks.value[day]?.mark)
        vm.clearDayMark().join()
        assertEquals(DayMark.NONE, vm.dayState.value.mark)
        assertFalse(repo.dayMarks.value.containsKey(day))
    }

    @Test fun `aceptar sugerencia persiste la revision y no reaparece al recrear`() = runBlocking {
        meal()
        assertTrue(vm.dayState.value.suggested)
        vm.acceptPartialDaySuggestion().join()
        assertEquals(DayMark.PARTIAL, vm.dayState.value.mark)
        assertEquals(DayMarkSource.REVIEW_ACCEPTED, repo.dayMarks.value[day]?.source)
        assertFalse(vm.dayState.value.suggested)
        assertFalse(model().dayState.value.suggested)
    }

    @Test fun `asi es mi dia confirma la comida baja y no repite la sugerencia`() = runBlocking {
        meal()
        assertTrue(vm.dayState.value.suggested)
        vm.rejectPartialDaySuggestion().join()
        assertEquals(DayMark.COMPLETE_CONFIRMED, vm.dayState.value.mark)
        assertEquals(DayMarkSource.REVIEW_REJECTED, repo.dayMarks.value[day]?.source)
        assertFalse(model().dayState.value.suggested)
        vm.clearDayMark().join()
        assertTrue(vm.dayState.value.suggested)
    }

    @Test fun `ayuno y fallo de Room se reflejan con honestidad`() = runBlocking {
        vm.setDayMark(DayMark.FASTING).join()
        assertEquals(DayMark.FASTING, vm.dayState.value.mark)
        val db = repo.databaseForTests()
        db.openHelper.writableDatabase.execSQL("CREATE TRIGGER fallo_quitar_marca BEFORE DELETE ON nutrition_day_marks BEGIN SELECT RAISE(ABORT, 'fallo simulado'); END")
        vm.clearDayMark().join()
        assertEquals(DayMark.FASTING, vm.dayState.value.mark)
        assertEquals("No se pudo guardar el estado del día. Inténtalo de nuevo.", vm.uiMessage.value)
        withTimeout(5_000) { vm.dayState.first { !it.saving } }
        assertFalse(vm.dayState.value.saving)
    }

    @Test fun `una fecha futura no permite marcar ni crea filas`() = runBlocking {
        vm.setSelectedDate(day.plusDays(2).toString())
        assertFalse(vm.dayState.value.editable)
        vm.setDayMark(DayMark.FASTING).join()
        assertTrue(repo.dayMarks.value.isEmpty())
        assertEquals("No puedes marcar un día futuro.", vm.uiMessage.value)
    }
}
