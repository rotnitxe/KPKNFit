package com.example.kpkn.screens.habits

import com.example.kpkn.data.models.BodyMetric
import com.example.kpkn.data.models.BodyObservation
import com.example.kpkn.data.models.CompletedExercise
import com.example.kpkn.data.models.CompletedSet
import com.example.kpkn.data.models.NutritionLog
import com.example.kpkn.data.models.NutritionStatus
import com.example.kpkn.data.models.WorkoutLog
import com.example.kpkn.domain.habits.HabitKind
import com.example.kpkn.domain.habits.HabitStreak
import com.example.kpkn.domain.habits.HabitUnit
import com.example.kpkn.domain.nutrition.expenditure.DayMarkEntry
import java.time.Instant
import java.time.LocalDate
import java.time.ZoneId
import androidx.lifecycle.viewModelScope
import kotlinx.coroutines.cancel
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.test.UnconfinedTestDispatcher
import kotlinx.coroutines.test.resetMain
import kotlinx.coroutines.test.setMain
import kotlinx.coroutines.withTimeout
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Before
import org.junit.Test

/** El ViewModel de Hábitos con fuentes falsas: calcula el estado, reacciona a cambios y no escribe en nada. */
@OptIn(ExperimentalCoroutinesApi::class)
class HabitsViewModelTest {

    private class FakeSource : HabitsDataSource {
        override val nutritionLogs = MutableStateFlow<List<NutritionLog>>(emptyList())
        override val dayMarks = MutableStateFlow<Map<LocalDate, DayMarkEntry>>(emptyMap())
        override val bodyObservations = MutableStateFlow<List<BodyObservation>>(emptyList())
        override val workoutLogs = MutableStateFlow<List<WorkoutLog>>(emptyList())
    }

    private val today = LocalDate.of(2026, 10, 21)

    @Before
    fun setUp() {
        Dispatchers.setMain(UnconfinedTestDispatcher())
    }

    @After
    fun tearDown() {
        Dispatchers.resetMain()
    }

    private fun viewModel(source: HabitsDataSource): HabitsViewModel =
        HabitsViewModel(source, zone = ZoneId.of("UTC"), today = { today }, dayChanges = kotlinx.coroutines.flow.flowOf(today))

    private fun meal(id: String, date: String): NutritionLog =
        NutritionLog(id = id, date = date, status = NutritionStatus.CONSUMED)

    @Test
    fun `sin datos los habitos estan a cero y el calculo ya existe`() = runBlocking {
        val state = withTimeout(5_000) { viewModel(FakeSource()).uiState.first { it.snapshot != null } }
        assertEquals(HabitKind.MEALS, state.selected)
        assertEquals(HabitStreak(0, 0, HabitUnit.DAYS), state.snapshot!!.summary(HabitKind.MEALS).streak)
        assertEquals(HabitStreak(0, 0, HabitUnit.WEEKS), state.snapshot!!.summary(HabitKind.TRAINING).streak)
    }

    @Test
    fun `las comidas de la fuente alimentan la racha de comidas`() = runBlocking {
        val source = FakeSource()
        source.nutritionLogs.value = listOf(meal("a", "2026-10-19T13:00"), meal("b", "2026-10-20T20:00"))
        val state = withTimeout(5_000) { viewModel(source).uiState.first { it.snapshot != null } }
        assertEquals(HabitStreak(2, 2, HabitUnit.DAYS), state.snapshot!!.summary(HabitKind.MEALS).streak)
    }

    @Test
    fun `pesajes y entrenos de la fuente alimentan sus habitos`() = runBlocking {
        val source = FakeSource()
        source.bodyObservations.value = listOf(
            BodyObservation(
                id = "w1",
                metric = BodyMetric.WEIGHT,
                valueSi = 78.4,
                unitSi = "kg",
                timestampEpochMs = Instant.parse("2026-10-21T07:00:00Z").toEpochMilli(),
            ),
        )
        source.workoutLogs.value = listOf(
            WorkoutLog(
                id = "s1",
                programId = "p1",
                sessionId = "sesion-1",
                sessionName = "Pierna",
                date = "2026-10-19T10:00:00Z",
                durationMinutes = 50,
                completedExercises = listOf(
                    CompletedExercise(
                        exerciseId = "e1",
                        exerciseName = "Sentadilla",
                        sets = listOf(CompletedSet(id = "x1", reps = 8)),
                    ),
                ),
            ),
        )
        val snapshot = withTimeout(5_000) { viewModel(source).uiState.first { it.snapshot != null } }.snapshot!!
        assertEquals(HabitStreak(1, 1, HabitUnit.DAYS), snapshot.summary(HabitKind.WEIGHING).streak)
        assertEquals(HabitStreak(1, 1, HabitUnit.WEEKS), snapshot.summary(HabitKind.TRAINING).streak)
    }

    @Test
    fun `seleccionar un habito cambia la seleccion y conserva el calculo`() = runBlocking {
        val vm = viewModel(FakeSource())
        withTimeout(5_000) { vm.uiState.first { it.snapshot != null } }
        vm.select(HabitKind.TRAINING)
        val state = withTimeout(5_000) { vm.uiState.first { it.selected == HabitKind.TRAINING } }
        assertEquals(HabitKind.TRAINING, state.selected)
        assertEquals(true, state.snapshot != null)
    }

    @Test
    fun `un registro nuevo llega a la racha cuando cambia la fuente`() = runBlocking {
        val source = FakeSource()
        val vm = viewModel(source)
        withTimeout(5_000) { vm.uiState.first { it.snapshot != null } }
        source.nutritionLogs.value = listOf(meal("a", "2026-10-20T13:00"))
        val state = withTimeout(5_000) {
            vm.uiState.first { it.snapshot?.summary(HabitKind.MEALS)?.streak?.current == 1 }
        }
        assertEquals(1, state.snapshot!!.summary(HabitKind.MEALS).streak.current)
    }
    @Test
    fun `pasar medianoche actualiza calendario y racha sin cambiar repositorios`() = runBlocking {
        val source=FakeSource();source.nutritionLogs.value=listOf(meal("a","2026-10-20T13:00"))
        val days=MutableStateFlow(today)
        val vm=HabitsViewModel(source,zone=ZoneId.of("UTC"),today={days.value},dayChanges=days)
        try {
            val initial=withTimeout(5000) { vm.uiState.first { it.snapshot?.today==today } }
            assertEquals(1,initial.snapshot!!.summary(HabitKind.MEALS).streak.current)
            days.value=today.plusDays(1)
            val next=withTimeout(5000) { vm.uiState.first { it.snapshot?.today==today.plusDays(1) } }
            assertEquals(0,next.snapshot!!.summary(HabitKind.MEALS).streak.current)
            assertEquals(1,source.nutritionLogs.value.size)
        } finally { vm.viewModelScope.cancel() }
    }
}
