package com.example.kpkn.screens.workout

import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.viewModelScope
import com.example.kpkn.data.models.*
import com.example.kpkn.data.repository.ProgramRepository
import com.example.kpkn.domain.auge.AugeFatigueEngine
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

data class WorkoutUiState(
    val session: Session? = null,
    val programId: String = "",
    val weekId: String = "",
    val macroIndex: Int = 0,
    val mesoIndex: Int = 0,
    val currentExerciseIdx: Int = 0,
    val currentSetIdx: Int = 0,
    // key = "${exerciseId}_${setIdx}"
    val completedSets: Map<String, CompletedSet> = emptyMap(),
    val restTimerTotal: Int = 0,
    val restTimerRemaining: Int = 0,
    val isRestTimerRunning: Boolean = false,
    val showFinishSheet: Boolean = false,
    val showReadinessSheet: Boolean = true,   // shown at workout start
    val showPostExerciseSheet: Boolean = false,
    val postExerciseTargetIdx: Int = -1,      // exercise index for feedback
    val sessionStressScore: Double = 0.0,     // AUGE CNC drain accumulated
    val startTimeMs: Long = System.currentTimeMillis(),
    val isComplete: Boolean = false,
)

class WorkoutViewModel(
    private val programId: String,
    private val sessionId: String,
) : ViewModel() {

    private val repository = ProgramRepository.getInstance()

    private val _uiState = MutableStateFlow(WorkoutUiState(programId = programId))
    val uiState: StateFlow<WorkoutUiState> = _uiState.asStateFlow()

    private var timerJob: Job? = null

    // Last log for ghost performance
    val lastLog: WorkoutLog? get() = repository.getLogsForSession(sessionId).firstOrNull()

    init {
        loadSession()
    }

    private fun loadSession() {
        val program = repository.getProgramById(programId) ?: return
        var foundSession: Session? = null
        var foundWeekId = ""
        var foundMacroIdx = 0
        var foundMesoIdx = 0

        outer@ for ((macroIdx, macro) in program.macrocycles.withIndex()) {
            for (block in macro.blocks) {
                for ((mesoIdx, meso) in block.mesocycles.withIndex()) {
                    for (week in meso.weeks) {
                        val s = week.sessions.find { it.id == sessionId }
                        if (s != null) {
                            foundSession = s
                            foundWeekId = week.id
                            foundMacroIdx = macroIdx
                            foundMesoIdx = mesoIdx
                            break@outer
                        }
                    }
                }
            }
        }

        val session = foundSession ?: return
        _uiState.update {
            it.copy(
                session = session,
                weekId = foundWeekId,
                macroIndex = foundMacroIdx,
                mesoIndex = foundMesoIdx,
                startTimeMs = System.currentTimeMillis(),
            )
        }

        // Start ongoing workout in repository
        repository.startWorkout(
            OngoingWorkoutState(
                programId = programId,
                session = session,
                startTime = System.currentTimeMillis(),
                macroIndex = foundMacroIdx,
                mesoIndex = foundMesoIdx,
                weekId = foundWeekId,
            )
        )
    }

    // ─── Navigation ───────────────────────────────────────────────────────────

    fun selectExercise(idx: Int) {
        stopRestTimer()
        _uiState.update { it.copy(currentExerciseIdx = idx, currentSetIdx = 0) }
    }

    fun nextSet() {
        stopRestTimer()
        val state = _uiState.value
        val session = state.session ?: return
        val allExercises = session.allExercises()
        val currentEx = allExercises.getOrNull(state.currentExerciseIdx) ?: return
        val totalSets = currentEx.sets.size

        if (state.currentSetIdx < totalSets - 1) {
            _uiState.update { it.copy(currentSetIdx = it.currentSetIdx + 1) }
        } else if (state.currentExerciseIdx < allExercises.size - 1) {
            _uiState.update { it.copy(currentExerciseIdx = it.currentExerciseIdx + 1, currentSetIdx = 0) }
        } else {
            _uiState.update { it.copy(showFinishSheet = true) }
        }
    }

    // ─── Log set ──────────────────────────────────────────────────────────────

    fun logSet(weight: Double, reps: Int, rpe: Double?) {
        val state = _uiState.value
        val session = state.session ?: return
        val allExercises = session.allExercises()
        val exercise = allExercises.getOrNull(state.currentExerciseIdx) ?: return
        val key = "${exercise.id}_${state.currentSetIdx}"

        val completedSet = CompletedSet(
            id = java.util.UUID.randomUUID().toString(),
            weight = weight,
            reps = reps,
            rpe = rpe,
        )

        _uiState.update {
            it.copy(completedSets = it.completedSets + (key to completedSet))
        }

        // Update repository ongoing workout
        repository.updateOngoingWorkout { ongoing ->
            ongoing.copy(completedSets = ongoing.completedSets + (key to completedSet))
        }

        // Start rest timer
        val restTime = exercise.restTime?.takeIf { it > 0 } ?: 90
        startRestTimer(restTime)
    }

    // ─── Rest Timer ───────────────────────────────────────────────────────────

    fun startRestTimer(seconds: Int) {
        timerJob?.cancel()
        _uiState.update { it.copy(restTimerTotal = seconds, restTimerRemaining = seconds, isRestTimerRunning = true) }
        timerJob = viewModelScope.launch {
            var remaining = seconds
            while (remaining > 0) {
                delay(1000L)
                remaining--
                _uiState.update { it.copy(restTimerRemaining = remaining) }
            }
            _uiState.update { it.copy(isRestTimerRunning = false) }
        }
    }

    fun addRestTime(seconds: Int) {
        timerJob?.cancel()
        val newTotal = _uiState.value.restTimerRemaining + seconds
        startRestTimer(newTotal)
    }

    fun stopRestTimer() {
        timerJob?.cancel()
        _uiState.update { it.copy(isRestTimerRunning = false, restTimerRemaining = 0) }
    }

    // ─── Readiness sheet ──────────────────────────────────────────────────────

    fun dismissReadinessSheet() {
        _uiState.update { it.copy(showReadinessSheet = false) }
    }

    // ─── Post-exercise sheet ──────────────────────────────────────────────────

    fun requestPostExerciseFeedback(exerciseIdx: Int) {
        _uiState.update { it.copy(showPostExerciseSheet = true, postExerciseTargetIdx = exerciseIdx) }
    }

    fun dismissPostExerciseSheet() {
        _uiState.update { it.copy(showPostExerciseSheet = false, postExerciseTargetIdx = -1) }
    }

    // ─── Finish ───────────────────────────────────────────────────────────────

    fun showFinish() { _uiState.update { it.copy(showFinishSheet = true) } }
    fun hideFinish() { _uiState.update { it.copy(showFinishSheet = false) } }

    fun finishWorkout(
        notes: String,
        fatigueLevel: Int,
        onPendingQuestionnaire: ((PendingQuestionnaire) -> Unit)? = null,
    ) {
        val state = _uiState.value
        val session = state.session ?: return
        val durationMs = System.currentTimeMillis() - state.startTimeMs
        val durationMinutes = (durationMs / 60000).toInt().coerceAtLeast(1)

        val completedExercises = session.allExercises().map { exercise ->
            val sets = exercise.sets.indices.mapNotNull { setIdx ->
                state.completedSets["${exercise.id}_$setIdx"]
            }
            CompletedExercise(
                exerciseId = exercise.id,
                exerciseName = exercise.name,
                restTime = exercise.restTime ?: 90,
                sets = sets,
            )
        }.filter { it.sets.isNotEmpty() }

        val totalVolume = completedExercises.sumOf { ex ->
            ex.sets.sumOf { it.weight * it.reps }
        }

        val logId = java.util.UUID.randomUUID().toString()

        viewModelScope.launch {
            // Compute AUGE session stress score on background thread
            val stressScore = withContext(Dispatchers.Default) {
                AugeFatigueEngine.calculateCompletedSessionStress(completedExercises)
            }

            // Collect primary muscle groups for the questionnaire
            val muscleGroups = completedExercises
                .map { it.exerciseName }
                .distinct()
                .take(6)

            val log = WorkoutLog(
                id = logId,
                programId = programId,
                sessionId = sessionId,
                sessionName = session.name,
                date = java.time.Instant.now().toString(),
                durationMinutes = durationMinutes,
                completedExercises = completedExercises,
                fatigueLevel = fatigueLevel,
                notes = notes.ifBlank { null },
                totalVolume = totalVolume,
                sessionStressScore = stressScore,
                weekId = state.weekId,
                macroIndex = state.macroIndex,
                mesoIndex = state.mesoIndex,
            )

            repository.addWorkoutLog(log)
            repository.clearOngoingWorkout()

            // Schedule post-session questionnaire for ~2h later
            val pending = PendingQuestionnaire(
                logId = logId,
                sessionName = session.name,
                muscleGroups = muscleGroups,
                scheduledTimeMs = System.currentTimeMillis() + (2 * 60 * 60 * 1000L),
            )
            onPendingQuestionnaire?.invoke(pending)

            _uiState.update { it.copy(isComplete = true, showFinishSheet = false, sessionStressScore = stressScore) }
        }
    }

    // ─── Ghost performance ────────────────────────────────────────────────────

    fun getGhostForSet(exerciseId: String, setIdx: Int): CompletedSet? {
        return lastLog?.completedExercises
            ?.find { it.exerciseId == exerciseId }
            ?.sets?.getOrNull(setIdx)
    }

    // ─── 1RM estimate ─────────────────────────────────────────────────────────

    fun estimateBrzycki1RM(weight: Double, reps: Int): Double? {
        if (reps <= 0 || reps >= 37 || weight <= 0) return null
        return weight * (36.0 / (37.0 - reps))
    }

    override fun onCleared() {
        super.onCleared()
        timerJob?.cancel()
    }

    companion object {
        fun factory(programId: String, sessionId: String): ViewModelProvider.Factory =
            object : ViewModelProvider.Factory {
                @Suppress("UNCHECKED_CAST")
                override fun <T : ViewModel> create(modelClass: Class<T>): T =
                    WorkoutViewModel(programId, sessionId) as T
            }
    }
}

// Extension to flatten exercises from session parts or direct list
fun Session.allExercises(): List<Exercise> =
    if (parts.isNotEmpty()) parts.flatMap { it.exercises } else exercises
