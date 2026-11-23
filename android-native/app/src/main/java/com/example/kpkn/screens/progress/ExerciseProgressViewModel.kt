package com.example.kpkn.screens.progress

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.example.kpkn.data.models.Program
import com.example.kpkn.data.models.WorkoutLog
import com.example.kpkn.data.repository.ProgramRepository
import com.example.kpkn.domain.training.*
import kotlinx.coroutines.*
import kotlinx.coroutines.flow.*
import java.time.LocalDate
import java.time.ZoneId

data class ExerciseProgressUiState(
    val loading: Boolean = true,
    val choices: List<ExerciseProgressChoice> = emptyList(),
    val report: ExerciseProgressReport? = null,
    val window: ExerciseProgressWindow = ExerciseProgressWindow.QUARTER,
    val selectedPoint: Int? = null,
    val searchQuery: String = "",
    val showDirectory: Boolean = false,
    val programId: String? = null,
) {
    val filteredChoices: List<ExerciseProgressChoice>
        get() {
            val q = searchQuery.trim().lowercase()
            if (q.isBlank()) return choices
            return choices.filter {
                it.name.lowercase().contains(q) || it.configuration.lowercase().contains(q)
            }
        }
}

class ExerciseProgressViewModel(
    exerciseId: String? = null,
    history: Flow<List<WorkoutLog>> = runCatching { ProgramRepository.getInstance().history }.getOrElse { flowOf(emptyList()) },
    private val today: () -> LocalDate = { LocalDate.now() },
    private val zone: () -> ZoneId = { ZoneId.systemDefault() },
    private val compute: CoroutineDispatcher = Dispatchers.Default,
    programs: Flow<List<Program>> = runCatching { ProgramRepository.getInstance().programs }.getOrElse { flowOf(emptyList()) },
    private val programId: String? = null,
) : ViewModel() {
    private data class Request(
        val exercise: String?,
        val metric: ExerciseProgressMetric = ExerciseProgressMetric.E1RM,
        val window: ExerciseProgressWindow = ExerciseProgressWindow.QUARTER,
        val showDirectory: Boolean = false,
        val searchQuery: String = "",
    )

    private val request = MutableStateFlow(
        Request(
            exercise = exerciseId,
            showDirectory = exerciseId == null,
        ),
    )
    private val _state = MutableStateFlow(
        ExerciseProgressUiState(
            showDirectory = exerciseId == null,
            programId = programId,
        ),
    )
    val state: StateFlow<ExerciseProgressUiState> = _state.asStateFlow()

    init {
        viewModelScope.launch {
            combine(history, programs, request) { logs, progs, req ->
                Triple(logs, progs, req)
            }.collectLatest { (logs, progs, req) ->
                val result = withContext(compute) {
                    val now = today()
                    val tz = zone()
                    val allChoices = ExerciseProgress.choices(
                        logs = logs,
                        today = now,
                        zone = tz,
                        programs = progs,
                        programId = programId,
                    )
                    val builtReport = ExerciseProgress.build(
                        logs = logs,
                        exerciseId = req.exercise,
                        metric = req.metric,
                        window = req.window,
                        today = now,
                        zone = tz,
                        programs = progs,
                        programId = programId,
                    )
                    allChoices to builtReport
                }
                _state.update { prev ->
                    ExerciseProgressUiState(
                        loading = false,
                        choices = result.first,
                        report = result.second,
                        window = req.window,
                        selectedPoint = prev.selectedPoint?.takeIf { it in result.second.points.indices },
                        searchQuery = req.searchQuery,
                        showDirectory = req.showDirectory && result.first.isNotEmpty(),
                        programId = programId,
                    )
                }
            }
        }
    }

    fun selectExercise(id: String) {
        request.update { it.copy(exercise = id, showDirectory = false) }
    }

    fun selectMetric(metric: ExerciseProgressMetric) {
        request.update { it.copy(metric = metric) }
    }

    fun selectWindow(window: ExerciseProgressWindow) {
        request.update { it.copy(window = window) }
    }

    fun selectPoint(index: Int) {
        _state.update { state ->
            state.copy(selectedPoint = index.takeIf { it in state.report?.points.orEmpty().indices })
        }
    }

    fun setSearchQuery(query: String) {
        request.update { it.copy(searchQuery = query) }
    }

    fun toggleDirectory(show: Boolean) {
        request.update { it.copy(showDirectory = show) }
    }
}
