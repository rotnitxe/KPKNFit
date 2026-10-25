package com.example.kpkn.screens.progress

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.example.kpkn.data.models.WorkoutLog
import com.example.kpkn.data.repository.ProgramRepository
import com.example.kpkn.domain.training.*
import kotlinx.coroutines.*
import kotlinx.coroutines.flow.*
import java.time.LocalDate
import java.time.ZoneId

data class ExerciseProgressUiState(val loading: Boolean = true, val choices: List<ExerciseProgressChoice> = emptyList(),
    val report: ExerciseProgressReport? = null, val window: ExerciseProgressWindow = ExerciseProgressWindow.QUARTER,
    val selectedPoint: Int? = null)
class ExerciseProgressViewModel(
    exerciseId: String? = null, history: Flow<List<WorkoutLog>> = ProgramRepository.getInstance().history,
    private val today: () -> LocalDate = { LocalDate.now() }, private val zone: () -> ZoneId = { ZoneId.systemDefault() },
    private val compute: CoroutineDispatcher = Dispatchers.Default,
) : ViewModel() {
    private data class Request(val exercise: String?, val metric: ExerciseProgressMetric = ExerciseProgressMetric.E1RM, val window: ExerciseProgressWindow = ExerciseProgressWindow.QUARTER)
    private val request = MutableStateFlow(Request(exerciseId))
    private val _state = MutableStateFlow(ExerciseProgressUiState())
    val state: StateFlow<ExerciseProgressUiState> = _state.asStateFlow()
    init { viewModelScope.launch {
        combine(history, request) { logs, req -> logs to req }.collectLatest { (logs, req) ->
            val result = withContext(compute) { ExerciseProgress.choices(logs) to ExerciseProgress.build(logs, req.exercise, req.metric, req.window, today(), zone()) }
            _state.value = ExerciseProgressUiState(false, result.first, result.second, req.window)
        }
    } }
    fun selectExercise(id: String) { request.update { it.copy(exercise = id) } }
    fun selectMetric(metric: ExerciseProgressMetric) { request.update { it.copy(metric = metric) } }
    fun selectWindow(window: ExerciseProgressWindow) { request.update { it.copy(window = window) } }
    fun selectPoint(index: Int) { _state.update { state -> state.copy(selectedPoint = index.takeIf { it in state.report?.points.orEmpty().indices }) } }
}
