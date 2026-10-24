package com.example.kpkn.screens.habits

import android.content.Context
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.example.kpkn.data.models.BodyObservation
import com.example.kpkn.data.models.NutritionLog
import com.example.kpkn.data.models.WorkoutLog
import com.example.kpkn.data.repository.BodyProgressRepository
import com.example.kpkn.data.repository.NutritionRepository
import com.example.kpkn.data.repository.ProgramRepository
import com.example.kpkn.domain.habits.HabitKind
import com.example.kpkn.domain.habits.HabitsSnapshot
import com.example.kpkn.domain.habits.buildHabitsSnapshot
import com.example.kpkn.domain.habits.mealRecordDays
import com.example.kpkn.domain.habits.trainingSetsByDay
import com.example.kpkn.domain.habits.weighInDays
import com.example.kpkn.domain.nutrition.expenditure.DayMarkEntry
import java.time.LocalDate
import java.time.ZoneId
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.flow
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.flowOn
import kotlinx.coroutines.flow.stateIn

/** Lo que Hábitos lee de la app: flujos de solo lectura (en producción, los de los repositorios; en pruebas, falsos). */
interface HabitsDataSource {
    val nutritionLogs: StateFlow<List<NutritionLog>>
    val dayMarks: StateFlow<Map<LocalDate, DayMarkEntry>>
    val bodyObservations: StateFlow<List<BodyObservation>>
    val workoutLogs: StateFlow<List<WorkoutLog>>
}

/** Fuente de producción: lee los flujos de los repositorios existentes, sin cambiarlos. */
class RepositoryHabitsDataSource(
    nutrition: NutritionRepository,
    body: BodyProgressRepository,
    program: ProgramRepository,
) : HabitsDataSource {
    override val nutritionLogs: StateFlow<List<NutritionLog>> = nutrition.nutritionLogs
    override val dayMarks: StateFlow<Map<LocalDate, DayMarkEntry>> = nutrition.dayMarks
    override val bodyObservations: StateFlow<List<BodyObservation>> = body.observations
    override val workoutLogs: StateFlow<List<WorkoutLog>> = program.history
}

/**
 * Estado de la pantalla. [snapshot] es null hasta el primer cálculo: entonces la pantalla muestra «—», nunca un cero
 * inventado.
 */
data class HabitsUiState(
    val selected: HabitKind = HabitKind.MEALS,
    val snapshot: HabitsSnapshot? = null,
)

/**
 * Hábitos de la app. Recalcula las rachas y los calendarios en [Dispatchers.IO] cuando cambia cualquier fuente y expone un
 * único [uiState] de solo lectura. No escribe en ningún repositorio.
 */
class HabitsViewModel(
    source: HabitsDataSource,
    private val zone: ZoneId = ZoneId.systemDefault(),
    private val today: () -> LocalDate = { LocalDate.now(zone) },
    dayChanges: Flow<LocalDate>? = null,
) : ViewModel() {

    private val selected = MutableStateFlow(HabitKind.MEALS)

    private val snapshots: Flow<HabitsSnapshot> = combine(
        source.nutritionLogs,
        source.dayMarks,
        source.bodyObservations,
        source.workoutLogs,
        dayChanges ?: flow { while (true) { emit(today()); delay(60_000L) } }.distinctUntilChanged(),
    ) { logs, marks, observations, workouts, day ->
        buildHabitsSnapshot(
            mealDays = mealRecordDays(logs, marks.values),
            weighInDays = weighInDays(observations, zone),
            trainingSets = trainingSetsByDay(workouts, zone),
            today = day,
        )
    }.flowOn(Dispatchers.IO)

    val uiState: StateFlow<HabitsUiState> = combine(snapshots, selected) { snapshot, kind ->
        HabitsUiState(selected = kind, snapshot = snapshot)
    }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(STOP_TIMEOUT_MS), HabitsUiState())

    /** Cambia el hábito que se muestra. No recalcula nada: los tres ya están listos en [uiState]. */
    fun select(kind: HabitKind) {
        selected.value = kind
    }

    private companion object {
        const val STOP_TIMEOUT_MS = 5_000L
    }
}

/** Crea el ViewModel con los repositorios de la app (ya inicializados al arrancar MainActivity). */
fun habitsViewModelFor(context: Context): HabitsViewModel = HabitsViewModel(
    RepositoryHabitsDataSource(
        nutrition = NutritionRepository.getInstance(),
        body = BodyProgressRepository.getInstance(context.applicationContext),
        program = ProgramRepository.getInstance(),
    ),
)
