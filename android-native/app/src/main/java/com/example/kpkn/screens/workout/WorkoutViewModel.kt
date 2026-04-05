package com.example.kpkn.screens.workout

import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.viewModelScope
import com.example.kpkn.data.exercises.EXERCISE_DATABASE_BY_ID
import com.example.kpkn.data.models.*
import com.example.kpkn.data.repository.ProgramRepository
import com.example.kpkn.domain.auge.AugeFatigueEngine
import com.example.kpkn.domain.auge.getAugeMuscleDisplayId
import com.example.kpkn.domain.calculations.calculateHybrid1RM
import com.example.kpkn.domain.training.VolumeCalculator
import com.example.kpkn.services.workout.WorkoutRestAlertManager
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
    val activeMode: WeekVariant = WeekVariant.A,
    val programId: String = "",
    val weekId: String = "",
    val macroIndex: Int = 0,
    val mesoIndex: Int = 0,
    val currentExerciseIdx: Int = 0,
    val currentSetIdx: Int = 0,
    // key = "${exerciseId}_${setIdx}" for bilateral, "${exerciseId}_${setIdx}_L/R" for unilateral
    val completedSets: Map<String, CompletedSet> = emptyMap(),
    val restTimerTotal: Int = 0,
    val restTimerRemaining: Int = 0,
    val isRestTimerRunning: Boolean = false,
    val showFinishSheet: Boolean = false,
    val showReadinessSheet: Boolean = true,   // shown at workout start
    val showPostExerciseSheet: Boolean = false,
    val postExerciseTargetIdx: Int = -1,      // exercise index for feedback
    val pendingPostExerciseIdx: Int = -1,
    val setAdvancedFeedback: Map<String, SetAdvancedFeedback> = emptyMap(),
    val postExerciseFeedbackByExerciseId: Map<String, PostExerciseFeedback> = emptyMap(),
    val sessionStressScore: Double = 0.0,
    val readinessMuscularOverride: Int? = null,
    val readinessNeuralOverride: Int? = null,
    val readinessSpinalOverride: Int? = null,
    val readinessMuscleOverrides: Map<String, Int> = emptyMap(),
    val skippedExerciseIds: Set<String> = emptySet(),
    val warmupCompletedExerciseIds: Set<String> = emptySet(),
    val startTimeMs: Long = System.currentTimeMillis(),
    val isComplete: Boolean = false,
    // Tanda 1: tags, history
    val exerciseTags: Map<String, String> = emptyMap(),   // exerciseId → active tag
    val showHistorySheet: Boolean = false,
    val historySheetExerciseDbId: String? = null,
    // Tanda 2: deviations
    val planDeviations: List<PlanDeviation> = emptyList(),
)

class WorkoutViewModel(
    private val programId: String,
    private val sessionId: String,
    private val restAlertManager: WorkoutRestAlertManager,
) : ViewModel() {

    private val repository = ProgramRepository.getInstance()

    private val _uiState = MutableStateFlow(WorkoutUiState(programId = programId))
    val uiState: StateFlow<WorkoutUiState> = _uiState.asStateFlow()

    private var timerJob: Job? = null
    private var activeRestTimerId: String? = null

    // Last log for ghost performance (by sessionId)
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
            var mesoOffset = 0
            for (block in macro.blocks) {
                for ((mesoIdx, meso) in block.mesocycles.withIndex()) {
                    val flattenedMesoIdx = mesoOffset + mesoIdx
                    for (week in meso.weeks) {
                        val s = week.sessions.find { it.id == sessionId }
                        if (s != null) {
                            foundSession = s
                            foundWeekId = week.id
                            foundMacroIdx = macroIdx
                            foundMesoIdx = flattenedMesoIdx
                            break@outer
                        }
                    }
                }
                mesoOffset += block.mesocycles.size
            }
        }

        val session = foundSession ?: return
        val resumedState = repository.ongoingWorkout.value
            ?.takeIf { it.programId == programId && it.session.id == sessionId }

        val restoredSession = resumedState?.session ?: session
        val restoredMode = resumedState?.activeMode ?: WeekVariant.A
        val restoredCompletedSets = resumedState?.completedSets ?: emptyMap()
        val restoredTags = resumedState?.exerciseTags ?: emptyMap()
        val exercisesForMode = sessionForActiveMode(restoredSession, restoredMode).allExercises()
        val (restoredExerciseIdx, restoredSetIdx) = resolveResumePosition(
            exercises = exercisesForMode,
            completedSets = restoredCompletedSets,
            preferredExerciseId = resumedState?.activeExerciseId,
            preferredSetId = resumedState?.activeSetId,
        )
        val restoredStartTime = resumedState?.startTime ?: System.currentTimeMillis()

        _uiState.update {
            it.copy(
                session = restoredSession,
                activeMode = restoredMode,
                weekId = foundWeekId,
                macroIndex = foundMacroIdx,
                mesoIndex = foundMesoIdx,
                currentExerciseIdx = restoredExerciseIdx,
                currentSetIdx = restoredSetIdx,
                completedSets = restoredCompletedSets,
                exerciseTags = restoredTags,
                showReadinessSheet = resumedState == null,
                startTimeMs = restoredStartTime,
            )
        }

        if (resumedState == null) {
            val initialExercise = exercisesForMode.firstOrNull()
            repository.startWorkout(
                OngoingWorkoutState(
                    programId = programId,
                    session = restoredSession,
                    startTime = restoredStartTime,
                    activeExerciseId = initialExercise?.id,
                    activeSetId = initialExercise?.sets?.firstOrNull()?.id,
                    macroIndex = foundMacroIdx,
                    mesoIndex = foundMesoIdx,
                    weekId = foundWeekId,
                    activeMode = WeekVariant.A,
                )
            )
        }
    }

    // ─── Navigation ───────────────────────────────────────────────────────────

    private fun sessionForActiveMode(base: Session, mode: WeekVariant): Session = when (mode) {
        WeekVariant.A -> base
        WeekVariant.B -> base.sessionB ?: base
        WeekVariant.C -> base.sessionC ?: base
        WeekVariant.D -> base.sessionD ?: base
    }

    /**
     * Returns true when a set slot is filled, accounting for bilateral (single key)
     * and unilateral (paired _L / _R keys).
     */
    fun isSetDone(completedSets: Map<String, CompletedSet>, exerciseId: String, setIdx: Int, isUnilateral: Boolean): Boolean =
        completedSets.containsKey("${exerciseId}_${setIdx}") ||
            (isUnilateral && (
                completedSets.containsKey("${exerciseId}_${setIdx}_L") ||
                completedSets.containsKey("${exerciseId}_${setIdx}_R")
            ))

    private fun resolveResumePosition(
        exercises: List<Exercise>,
        completedSets: Map<String, CompletedSet>,
        preferredExerciseId: String?,
        preferredSetId: String?,
    ): Pair<Int, Int> {
        if (exercises.isEmpty()) return 0 to 0

        if (!preferredExerciseId.isNullOrBlank()) {
            val preferredExerciseIdx = exercises.indexOfFirst { it.id == preferredExerciseId }
            if (preferredExerciseIdx >= 0) {
                val preferredExercise = exercises[preferredExerciseIdx]
                val preferredSetIdx = preferredSetId
                    ?.let { setId -> preferredExercise.sets.indexOfFirst { it.id == setId } }
                    ?.takeIf { it >= 0 }
                if (preferredSetIdx != null) {
                    if (!isSetDone(completedSets, preferredExercise.id, preferredSetIdx, preferredExercise.isUnilateral)) {
                        return preferredExerciseIdx to preferredSetIdx
                    }
                }

                val fallbackSetIdx = preferredExercise.sets.indices.firstOrNull { setIdx ->
                    !isSetDone(completedSets, preferredExercise.id, setIdx, preferredExercise.isUnilateral)
                }
                if (fallbackSetIdx != null) {
                    return preferredExerciseIdx to fallbackSetIdx
                }
            }
        }

        for ((exerciseIdx, exercise) in exercises.withIndex()) {
            val pendingSetIdx = exercise.sets.indices.firstOrNull { setIdx ->
                !isSetDone(completedSets, exercise.id, setIdx, exercise.isUnilateral)
            }
            if (pendingSetIdx != null) {
                return exerciseIdx to pendingSetIdx
            }
        }

        return exercises.size to 0
    }

    private fun visibleExercises(state: WorkoutUiState): List<Exercise> {
        val base = state.session ?: return emptyList()
        val byMode = sessionForActiveMode(base, state.activeMode).allExercises()
        if (state.skippedExerciseIds.isEmpty()) return byMode
        return byMode.filterNot { it.id in state.skippedExerciseIds }
    }

    private fun persistOngoingState(state: WorkoutUiState = _uiState.value) {
        val session = state.session ?: return
        val visible = visibleExercises(state)
        val activeExercise = visible.getOrNull(state.currentExerciseIdx)
        val activeSetId = activeExercise
            ?.sets
            ?.getOrNull(state.currentSetIdx)
            ?.id

        repository.updateOngoingWorkout { ongoing ->
            if (ongoing.programId != programId || ongoing.session.id != sessionId) {
                ongoing
            } else {
                ongoing.copy(
                    session = session,
                    activeMode = state.activeMode,
                    activeExerciseId = activeExercise?.id,
                    activeSetId = activeSetId,
                    completedSets = state.completedSets,
                    exerciseTags = state.exerciseTags,
                )
            }
        }
    }

    private fun withModeSession(base: Session, mode: WeekVariant, update: (Session) -> Session): Session = when (mode) {
        WeekVariant.A -> update(base)
        WeekVariant.B -> base.copy(sessionB = update(base.sessionB ?: base))
        WeekVariant.C -> base.copy(sessionC = update(base.sessionC ?: base))
        WeekVariant.D -> base.copy(sessionD = update(base.sessionD ?: base))
    }

    private fun Session.replaceExerciseById(exerciseId: String, update: (Exercise) -> Exercise): Session {
        if (parts.isNotEmpty()) {
            var changed = false
            val newParts = parts.map { part ->
                val idx = part.exercises.indexOfFirst { it.id == exerciseId }
                if (idx < 0) return@map part
                changed = true
                val mutable = part.exercises.toMutableList()
                mutable[idx] = update(mutable[idx])
                part.copy(exercises = mutable)
            }
            return if (changed) copy(parts = newParts) else this
        }

        val idx = exercises.indexOfFirst { it.id == exerciseId }
        if (idx < 0) return this
        val mutable = exercises.toMutableList()
        mutable[idx] = update(mutable[idx])
        return copy(exercises = mutable)
    }

    private fun Session.moveExerciseById(exerciseId: String, direction: Int): Session {
        if (parts.isNotEmpty()) {
            var changed = false
            val newParts = parts.map { part ->
                val idx = part.exercises.indexOfFirst { it.id == exerciseId }
                if (idx < 0 || part.exercises.size < 2) return@map part
                val target = (idx + direction).coerceIn(0, part.exercises.lastIndex)
                if (target == idx) return@map part
                changed = true
                val mutable = part.exercises.toMutableList()
                val moved = mutable.removeAt(idx)
                mutable.add(target, moved)
                part.copy(exercises = mutable)
            }
            return if (changed) copy(parts = newParts) else this
        }

        if (exercises.size < 2) return this
        val idx = exercises.indexOfFirst { it.id == exerciseId }
        if (idx < 0) return this
        val target = (idx + direction).coerceIn(0, exercises.lastIndex)
        if (target == idx) return this
        val mutable = exercises.toMutableList()
        val moved = mutable.removeAt(idx)
        mutable.add(target, moved)
        return copy(exercises = mutable)
    }

    private fun supersetGroupIndices(exercises: List<Exercise>, currentIdx: Int): List<Int> {
        val groupId = exercises.getOrNull(currentIdx)?.supersetId?.takeIf { it.isNotBlank() } ?: return emptyList()
        return exercises.indices.filter { exercises[it].supersetId == groupId }
    }

    private fun nextSupersetTarget(exercises: List<Exercise>, currentIdx: Int, currentSetIdx: Int): Pair<Int, Int>? {
        val group = supersetGroupIndices(exercises, currentIdx)
        if (group.size <= 1) return null

        val pos = group.indexOf(currentIdx)
        if (pos < 0) return null

        val hasSetAt: (Int, Int) -> Boolean = { exerciseIdx, setIdx ->
            setIdx >= 0 && setIdx <= exercises[exerciseIdx].sets.lastIndex
        }

        for (groupPos in (pos + 1) until group.size) {
            val candidateExerciseIdx = group[groupPos]
            if (hasSetAt(candidateExerciseIdx, currentSetIdx)) {
                return candidateExerciseIdx to currentSetIdx
            }
        }

        val nextRoundSetIdx = currentSetIdx + 1
        for (candidateExerciseIdx in group) {
            if (hasSetAt(candidateExerciseIdx, nextRoundSetIdx)) {
                return candidateExerciseIdx to nextRoundSetIdx
            }
        }

        return null
    }

    private fun shouldAutoRestAfterSet(exercises: List<Exercise>, currentIdx: Int, currentSetIdx: Int): Boolean {
        val next = nextSupersetTarget(exercises, currentIdx, currentSetIdx) ?: return true
        // If we are just switching to a superset partner in the same round, skip rest.
        return !(next.first != currentIdx && next.second == currentSetIdx)
    }

    private fun skipExerciseAndAdvance(state: WorkoutUiState, exerciseId: String) {
        val currentExerciseId = visibleExercises(state)
            .getOrNull(state.currentExerciseIdx)
            ?.id
        val updatedSkips = state.skippedExerciseIds + exerciseId
        val visible = visibleExercises(state.copy(skippedExerciseIds = updatedSkips))

        val keepCurrentExercise = currentExerciseId != null && currentExerciseId != exerciseId

        val resolvedNextIdx = when {
            visible.isEmpty() -> 0
            keepCurrentExercise -> {
                visible.indexOfFirst { it.id == currentExerciseId }
                    .takeIf { it >= 0 }
                    ?: state.currentExerciseIdx.coerceIn(0, visible.lastIndex)
            }
            else -> state.currentExerciseIdx.coerceIn(0, visible.lastIndex)
        }

        val resolvedNextSetIdx = if (!keepCurrentExercise) {
            0
        } else {
            val currentExercise = visible.getOrNull(resolvedNextIdx)
            state.currentSetIdx.coerceIn(0, (currentExercise?.sets?.lastIndex ?: 0).coerceAtLeast(0))
        }

        _uiState.update {
            it.copy(
                skippedExerciseIds = updatedSkips,
                currentExerciseIdx = resolvedNextIdx,
                currentSetIdx = resolvedNextSetIdx,
            )
        }
        persistOngoingState()
    }

    fun setActiveMode(mode: WeekVariant) {
        val state = _uiState.value
        if (state.activeMode == mode) return

        val preview = state.copy(activeMode = mode)
        val modeExercises = visibleExercises(preview)
        val (resolvedExerciseIdx, resolvedSetIdx) = resolveResumePosition(
            exercises = modeExercises,
            completedSets = state.completedSets,
            preferredExerciseId = null,
            preferredSetId = null,
        )

        _uiState.update {
            it.copy(
                activeMode = mode,
                currentExerciseIdx = resolvedExerciseIdx,
                currentSetIdx = resolvedSetIdx,
            )
        }
        persistOngoingState()
    }

    fun moveExercise(exerciseId: String, direction: Int) {
        val state = _uiState.value
        val base = state.session ?: return
        val updatedSession = withModeSession(base, state.activeMode) { modeSession ->
            modeSession.moveExerciseById(exerciseId, direction)
        }
        if (updatedSession == base) return

        val updatedState = state.copy(session = updatedSession)
        val newIdx = visibleExercises(updatedState).indexOfFirst { it.id == exerciseId }

        _uiState.update {
            it.copy(
                session = updatedSession,
                currentExerciseIdx = if (newIdx >= 0) newIdx else it.currentExerciseIdx,
            )
        }
        persistOngoingState()
    }

    fun replaceExercise(exerciseId: String, replacement: ExerciseMuscleInfo) {
        val state = _uiState.value
        val base = state.session ?: return

        val updatedSession = withModeSession(base, state.activeMode) { modeSession ->
            modeSession.replaceExerciseById(exerciseId) { old ->
                old.copy(
                    name = replacement.name,
                    exerciseDbId = replacement.id,
                    exerciseId = replacement.id,
                    setupDetails = old.setupDetails ?: replacement.setupDetails?.let {
                        ExerciseSetupDetails(
                            seatPosition = it.seatPosition,
                            pinPosition = it.pinPosition,
                            equipmentNotes = it.equipmentNotes,
                        )
                    },
                    setupCues = if (!old.setupCues.isNullOrEmpty()) old.setupCues else (replacement.setupCues ?: emptyList()),
                    executionCues = if (!old.executionCues.isNullOrEmpty()) old.executionCues else (replacement.executionCues ?: emptyList()),
                )
            }
        }

        val cleanedCompleted = state.completedSets.filterKeys { !it.startsWith("${exerciseId}_") }
        val cleanedAdvanced = state.setAdvancedFeedback.filterKeys { !it.startsWith("${exerciseId}_") }
        val cleanedFeedback = state.postExerciseFeedbackByExerciseId - exerciseId

        _uiState.update {
            it.copy(
                session = updatedSession,
                completedSets = cleanedCompleted,
                setAdvancedFeedback = cleanedAdvanced,
                postExerciseFeedbackByExerciseId = cleanedFeedback,
            )
        }
        persistOngoingState()
    }

    fun skipExercise(exerciseId: String) {
        val state = _uiState.value
        skipExerciseAndAdvance(state, exerciseId)
    }

    fun markWarmupComplete(exerciseId: String) {
        _uiState.update { it.copy(warmupCompletedExerciseIds = it.warmupCompletedExerciseIds + exerciseId) }
    }

    fun selectExercise(idx: Int) {
        stopRestTimer()
        _uiState.update { it.copy(currentExerciseIdx = idx, currentSetIdx = 0) }
        persistOngoingState()
    }

    fun nextSet() {
        stopRestTimer()
        val state = _uiState.value
        val allExercises = visibleExercises(state)
        val currentEx = allExercises.getOrNull(state.currentExerciseIdx) ?: return
        val totalSets = currentEx.sets.size

        nextSupersetTarget(allExercises, state.currentExerciseIdx, state.currentSetIdx)?.let { (nextExIdx, nextSetIdx) ->
            _uiState.update {
                it.copy(
                    currentExerciseIdx = nextExIdx,
                    currentSetIdx = nextSetIdx,
                )
            }
            persistOngoingState()
            return
        }

        if (state.currentSetIdx < totalSets - 1) {
            _uiState.update { it.copy(currentSetIdx = it.currentSetIdx + 1) }
        } else if (state.currentExerciseIdx < allExercises.size - 1) {
            _uiState.update {
                it.copy(
                    showPostExerciseSheet = true,
                    postExerciseTargetIdx = it.currentExerciseIdx,
                    pendingPostExerciseIdx = it.currentExerciseIdx + 1,
                )
            }
        } else {
            _uiState.update {
                it.copy(
                    showPostExerciseSheet = true,
                    postExerciseTargetIdx = it.currentExerciseIdx,
                    pendingPostExerciseIdx = -2,
                )
            }
        }
        persistOngoingState()
    }

    // ─── Log set (bilateral) ──────────────────────────────────────────────────

    fun logSet(weight: Double, reps: Int, rpe: Double?, advanced: SetAdvancedFeedback = SetAdvancedFeedback()) {
        val state = _uiState.value
        val allExercises = visibleExercises(state)
        val exercise = allExercises.getOrNull(state.currentExerciseIdx) ?: return
        val key = "${exercise.id}_${state.currentSetIdx}"

        val completedSet = applyAdvancedFeedback(CompletedSet(
            id = java.util.UUID.randomUUID().toString(),
            weight = weight,
            reps = reps,
            rpe = rpe,
        ), advanced)

        val suggestion = getWeightSuggestion(exercise, state.currentSetIdx)
        val currentPlannedSet = exercise.sets.getOrNull(state.currentSetIdx)
        val newDeviations = if (currentPlannedSet != null) {
            WorkoutPlanDeviationSupport.detect(
                exerciseId = exercise.id,
                exerciseName = exercise.name,
                setIdx = state.currentSetIdx,
                plannedSet = currentPlannedSet,
                actualWeight = weight,
                actualReps = reps,
                advanced = advanced,
                suggestedWeight = suggestion?.suggestedWeight,
            )
        } else emptyList()

        _uiState.update {
            it.copy(
                completedSets = it.completedSets + (key to completedSet),
                setAdvancedFeedback = it.setAdvancedFeedback + (key to advanced),
                planDeviations = it.planDeviations + newDeviations,
            )
        }
        persistOngoingState()

        val baseRest = exercise.restTime?.takeIf { it > 0 } ?: 90
        val adaptiveRest = WorkoutAdaptiveRest.compute(baseRest, advanced)
        if (shouldAutoRestAfterSet(allExercises, state.currentExerciseIdx, state.currentSetIdx)) {
            startRestTimer(adaptiveRest)
        }
    }

    // ─── Log unilateral set (left + right) ───────────────────────────────────

    fun logUnilateralSet(
        leftWeight: Double, leftReps: Int, leftRpe: Double?,
        rightWeight: Double, rightReps: Int, rightRpe: Double?,
        advanced: SetAdvancedFeedback,
    ) {
        val state = _uiState.value
        val allExercises = visibleExercises(state)
        val exercise = allExercises.getOrNull(state.currentExerciseIdx) ?: return
        val keyBase = "${exercise.id}_${state.currentSetIdx}"

        val newSets = mutableMapOf<String, CompletedSet>()
        val newFeedback = mutableMapOf<String, SetAdvancedFeedback>()

        if (leftWeight > 0 || leftReps > 0) {
            newSets["${keyBase}_L"] = applyAdvancedFeedback(
                CompletedSet(id = java.util.UUID.randomUUID().toString(), weight = leftWeight, reps = leftReps, rpe = leftRpe, side = "left"),
                advanced,
            )
            newFeedback["${keyBase}_L"] = advanced
        }
        if (rightWeight > 0 || rightReps > 0) {
            // Drop dropsets / rest-pauses from right side to avoid double-counting
            val rightAdvanced = advanced.copy(dropSets = emptyList(), restPauseReps = emptyList())
            newSets["${keyBase}_R"] = applyAdvancedFeedback(
                CompletedSet(id = java.util.UUID.randomUUID().toString(), weight = rightWeight, reps = rightReps, rpe = rightRpe, side = "right"),
                rightAdvanced,
            )
            newFeedback["${keyBase}_R"] = rightAdvanced
        }

        val suggestion = getWeightSuggestion(exercise, state.currentSetIdx)
        val currentPlannedSet = exercise.sets.getOrNull(state.currentSetIdx)
        val dominantWeight = maxOf(leftWeight, rightWeight)
        val dominantReps = maxOf(leftReps, rightReps)
        val newDeviations = if (currentPlannedSet != null && (dominantWeight > 0 || dominantReps > 0)) {
            WorkoutPlanDeviationSupport.detect(
                exerciseId = exercise.id,
                exerciseName = exercise.name,
                setIdx = state.currentSetIdx,
                plannedSet = currentPlannedSet,
                actualWeight = dominantWeight,
                actualReps = dominantReps,
                advanced = advanced,
                suggestedWeight = suggestion?.suggestedWeight,
            )
        } else emptyList()

        _uiState.update {
            it.copy(
                completedSets = it.completedSets + newSets,
                setAdvancedFeedback = it.setAdvancedFeedback + newFeedback,
                planDeviations = it.planDeviations + newDeviations,
            )
        }
        persistOngoingState()

        val baseRest = exercise.restTime?.takeIf { it > 0 } ?: 90
        val adaptiveRest = WorkoutAdaptiveRest.compute(baseRest, advanced)
        if (shouldAutoRestAfterSet(allExercises, state.currentExerciseIdx, state.currentSetIdx)) {
            startRestTimer(adaptiveRest)
        }
    }

    // ─── Rest Timer ───────────────────────────────────────────────────────────

    fun startRestTimer(seconds: Int) {
        timerJob?.cancel()
        val state = _uiState.value
        val sessionName = state.session?.name ?: "Entrenamiento"
        val exerciseName = visibleExercises(state)
            ?.getOrNull(state.currentExerciseIdx)
            ?.name
            ?: "Siguiente serie"

        activeRestTimerId = restAlertManager.scheduleRestEnd(
            durationSeconds = seconds,
            sessionName = sessionName,
            exerciseName = exerciseName,
        )

        _uiState.update { it.copy(restTimerTotal = seconds, restTimerRemaining = seconds, isRestTimerRunning = true) }
        timerJob = viewModelScope.launch {
            var remaining = seconds
            while (remaining > 0) {
                delay(1000L)
                remaining--
                _uiState.update { it.copy(restTimerRemaining = remaining) }
            }
            restAlertManager.onTimerFinishedInApp(activeRestTimerId)
            activeRestTimerId = null
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
        restAlertManager.cancelRestAlerts()
        activeRestTimerId = null
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

    fun savePostExerciseFeedback(feedback: PostExerciseFeedback) {
        _uiState.update {
            it.copy(
                postExerciseFeedbackByExerciseId = it.postExerciseFeedbackByExerciseId + (feedback.exerciseId to feedback),
                showPostExerciseSheet = false,
                postExerciseTargetIdx = -1,
            )
        }
        advanceAfterPostExerciseFeedback()
    }

    private fun advanceAfterPostExerciseFeedback() {
        val pending = _uiState.value.pendingPostExerciseIdx
        when {
            pending >= 0 -> {
                _uiState.update {
                    it.copy(
                        currentExerciseIdx = pending,
                        currentSetIdx = 0,
                        pendingPostExerciseIdx = -1,
                    )
                }
            }
            pending == -2 -> {
                _uiState.update { it.copy(showFinishSheet = true, pendingPostExerciseIdx = -1) }
            }
        }
        persistOngoingState()
    }

    fun dismissPostExerciseSheet() {
        _uiState.update { it.copy(showPostExerciseSheet = false, postExerciseTargetIdx = -1) }
        advanceAfterPostExerciseFeedback()
    }

    fun saveReadinessAdjustments(
        neural: Int?,
        muscular: Int?,
        spinal: Int?,
        perMuscle: Map<String, Int>,
    ) {
        _uiState.update {
            it.copy(
                readinessNeuralOverride = neural,
                readinessMuscularOverride = muscular,
                readinessSpinalOverride = spinal,
                readinessMuscleOverrides = perMuscle,
            )
        }
    }

    // ─── Tags / Setup ─────────────────────────────────────────────────────────

    fun setExerciseTag(exerciseId: String, tag: String) {
        _uiState.update { it.copy(exerciseTags = it.exerciseTags + (exerciseId to tag)) }
        persistOngoingState()
    }

    fun clearExerciseTag(exerciseId: String) {
        _uiState.update { it.copy(exerciseTags = it.exerciseTags - exerciseId) }
        persistOngoingState()
    }

    // ─── History sheet ────────────────────────────────────────────────────────

    fun showHistoryFor(exerciseDbId: String) {
        _uiState.update { it.copy(showHistorySheet = true, historySheetExerciseDbId = exerciseDbId) }
    }

    fun hideHistorySheet() {
        _uiState.update { it.copy(showHistorySheet = false, historySheetExerciseDbId = null) }
    }

    // ─── Finish ───────────────────────────────────────────────────────────────

    fun showFinish() { _uiState.update { it.copy(showFinishSheet = true) } }
    fun hideFinish() { _uiState.update { it.copy(showFinishSheet = false) } }

    fun finishWorkout(
        notes: String,
        fatigueLevel: Int,
        closingFeedback: SessionClosingFeedback,
        onPendingQuestionnaire: ((PendingQuestionnaire) -> Unit)? = null,
    ) {
        val state = _uiState.value
        val session = state.session ?: return
        val durationMs = System.currentTimeMillis() - state.startTimeMs
        val durationMinutes = (durationMs / 60000).toInt().coerceAtLeast(1)

        val completedExercises = visibleExercises(state).map { exercise ->
            // Collect bilateral + unilateral L/R sets in order
            val sets = exercise.sets.indices.flatMap { setIdx ->
                val bilateral = state.completedSets["${exercise.id}_$setIdx"]
                val left = state.completedSets["${exercise.id}_${setIdx}_L"]
                val right = state.completedSets["${exercise.id}_${setIdx}_R"]
                listOfNotNull(bilateral, left, right)
            }
            CompletedExercise(
                exerciseId = exercise.id,
                exerciseName = exercise.name,
                exerciseDbId = exercise.exerciseDbId ?: exercise.exerciseId,
                restTime = exercise.restTime ?: 90,
                sets = sets,
            )
        }.filter { it.sets.isNotEmpty() }

        val totalVolume = completedExercises.sumOf { ex ->
            ex.sets.sumOf { it.weight * it.reps }
        }

        val logId = java.util.UUID.randomUUID().toString()

        viewModelScope.launch {
            val stressScore = withContext(Dispatchers.Default) {
                val base = AugeFatigueEngine.calculateCompletedSessionStress(completedExercises, EXERCISE_DATABASE_BY_ID)
                val neuralFactor = closingFeedback.neuralDrain.toDouble() / 100.0
                val qualityPenalty = if (state.postExerciseFeedbackByExerciseId.isEmpty()) 1.0 else {
                    val avgTech = state.postExerciseFeedbackByExerciseId.values.map { it.technicalQuality }.average()
                    (1.1 - (avgTech / 20.0)).coerceIn(0.75, 1.2)
                }
                (base * neuralFactor * qualityPenalty).coerceAtLeast(1.0)
            }

            val muscleGroups = completedExercises
                .mapNotNull { ex ->
                    val info = EXERCISE_DATABASE_BY_ID[ex.exerciseDbId ?: ex.exerciseId]
                    val primary = info?.involvedMuscles?.firstOrNull { m -> m.role == MuscleRole.PRIMARY }
                    if (primary != null) {
                        val canonical = VolumeCalculator.normalizeCanonicalMuscleGroup(primary.muscle, primary.emphasis)
                        getAugeMuscleDisplayId(canonical, primary.emphasis)
                    } else ex.exerciseName
                }
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
                discomforts = (closingFeedback.discomforts + state.postExerciseFeedbackByExerciseId.values.flatMap { it.discomforts }).distinct(),
                notes = notes.ifBlank { null },
                totalVolume = totalVolume,
                sessionStressScore = stressScore,
                weekId = state.weekId,
                macroIndex = state.macroIndex,
                mesoIndex = state.mesoIndex,
                clarityRating = closingFeedback.clarityRating,
                environmentTags = closingFeedback.environmentTags,
                planDeviations = state.planDeviations,
                exerciseTags = state.exerciseTags,
            )

            repository.addWorkoutLog(log)
            repository.clearOngoingWorkout()
            restAlertManager.cancelRestAlerts()
            activeRestTimerId = null

            val postFeedback = mapWorkoutToPostSessionFeedback(
                log = log,
                postExerciseFeedback = state.postExerciseFeedbackByExerciseId.values.toList(),
                exerciseDbById = EXERCISE_DATABASE_BY_ID,
            )

            val pending = PendingQuestionnaire(
                logId = logId,
                sessionName = session.name,
                muscleGroups = muscleGroups,
                scheduledTimeMs = System.currentTimeMillis() + (2 * 60 * 60 * 1000L),
            )
            if (postFeedback.muscleFeedback.isNotEmpty()) {
                onPendingQuestionnaire?.invoke(
                    pending.copy(muscleGroups = postFeedback.muscleFeedback.keys.toList().take(6)),
                )
            } else {
                onPendingQuestionnaire?.invoke(pending)
            }

            _uiState.update { it.copy(isComplete = true, showFinishSheet = false, sessionStressScore = stressScore) }
        }
    }

    // ─── Ghost performance ────────────────────────────────────────────────────

    /**
     * Returns the last completed set for this exercise/set position.
     * If [activeTag] is set, prefers the most recent session where this exercise
     * was performed with that same tag — giving contextually accurate ghost data.
     */
    fun getGhostForSet(
        exerciseId: String,
        setIdx: Int,
        exerciseDbId: String? = null,
        activeTag: String? = null,
    ): CompletedSet? {
        val dbId = exerciseDbId?.takeIf { it.isNotBlank() }
        if (dbId != null) {
            val candidates = repository.history.value
                .filter { log -> log.completedExercises.any { it.exerciseDbId == dbId || it.exerciseId == dbId } }
                .sortedByDescending { it.date }

            // Prefer tag-matching log when tag is active
            val preferred = if (activeTag != null) {
                candidates.firstOrNull { log ->
                    val ex = log.completedExercises.firstOrNull { it.exerciseDbId == dbId || it.exerciseId == dbId }
                    ex != null && log.exerciseTags[ex.exerciseId] == activeTag
                }
            } else null

            val ghost = (preferred ?: candidates.firstOrNull())
                ?.completedExercises
                ?.firstOrNull { it.exerciseDbId == dbId || it.exerciseId == dbId }
                ?.sets?.getOrNull(setIdx)
            if (ghost != null) return ghost
        }
        // Fallback: last session with same exerciseId
        return lastLog?.completedExercises
            ?.find { it.exerciseId == exerciseId }
            ?.sets?.getOrNull(setIdx)
    }

    // ─── Exercise history ─────────────────────────────────────────────────────

    /**
     * Returns up to [limit] history entries for [exerciseDbId].
     * If [preferredTag] is provided, tag-matching sessions appear first.
     */
    fun getExerciseHistory(
        exerciseDbId: String,
        limit: Int = 5,
        preferredTag: String? = null,
    ): List<ExerciseHistoryEntry> {
        val all = repository.history.value
            .filter { log -> log.completedExercises.any { it.exerciseDbId == exerciseDbId || it.exerciseId == exerciseDbId } }
            .sortedByDescending { it.date }

        val tagged = if (preferredTag != null) {
            all.filter { log ->
                val ex = log.completedExercises.firstOrNull { it.exerciseDbId == exerciseDbId || it.exerciseId == exerciseDbId }
                ex != null && log.exerciseTags[ex.exerciseId] == preferredTag
            }
        } else emptyList()

        // Tag-matched first, then fill with the rest (no duplicates)
        val ordered = (tagged + all.filter { it !in tagged }).take(limit)

        return ordered.mapNotNull { log ->
            val ex = log.completedExercises.firstOrNull {
                it.exerciseDbId == exerciseDbId || it.exerciseId == exerciseDbId
            } ?: return@mapNotNull null
            val best1rm = ex.sets
                .filter { s -> !s.isWarmup && s.weight > 0 && s.reps > 0 }
                .maxOfOrNull { s -> calculateHybrid1RM(s.weight, s.reps) }
            ExerciseHistoryEntry(
                date = log.date,
                sets = ex.sets,
                e1rm = best1rm,
                tag = log.exerciseTags[ex.exerciseId],
            )
        }
    }

    // ─── Weight suggestion ────────────────────────────────────────────────────

    /**
     * Suggests a working weight for [setIdx].
     * When [activeTag] is set, prioritizes history from sessions with that same tag,
     * so "Press Smith" and "Press libre" don't contaminate each other.
     */
    fun getWeightSuggestion(exercise: Exercise, setIdx: Int, activeTag: String? = null): WeightSuggestion? {
        val dbId = (exercise.exerciseDbId ?: exercise.exerciseId)?.takeIf { it.isNotBlank() }
            ?: return null

        val history = getExerciseHistory(dbId, limit = 5, preferredTag = activeTag)
        if (history.isEmpty()) {
            val refWeight = exercise.consolidatedWeight?.weightKg
                ?: exercise.sets.getOrNull(setIdx)?.weight
                ?: exercise.sets.getOrNull(setIdx)?.consolidatedWeight
            return if (refWeight != null && refWeight > 0)
                WeightSuggestion(suggestedWeight = refWeight, reason = "Del programa")
            else null
        }

        // Prefer tag-matched entry for the suggestion base
        val baseEntry = history.firstOrNull { it.tag == activeTag } ?: history.first()
        val lastSet = baseEntry.sets.filter { !it.isWarmup }
            .getOrNull(setIdx) ?: baseEntry.sets.filter { !it.isWarmup }.lastOrNull()

        if (lastSet != null && lastSet.weight > 0) {
            val targetReps = exercise.sets.getOrNull(setIdx)?.targetReps ?: lastSet.reps
            val suggestedWeight = when {
                lastSet.isFailure -> lastSet.weight * 0.95
                lastSet.reps > 0 && targetReps <= lastSet.reps -> lastSet.weight * 1.025
                else -> lastSet.weight
            }
            val rounded = (suggestedWeight * 2).toLong() / 2.0
            val reason = if (activeTag != null && baseEntry.tag == activeTag)
                "Última sesión · $activeTag" else "Última sesión"
            return WeightSuggestion(suggestedWeight = rounded, reason = reason)
        }

        return null
    }

    // ─── 1RM estimate ─────────────────────────────────────────────────────────

    fun estimateBrzycki1RM(weight: Double, reps: Int): Double? {
        if (reps <= 0 || reps >= 37 || weight <= 0) return null
        return weight * (36.0 / (37.0 - reps))
    }

    override fun onCleared() {
        super.onCleared()
        timerJob?.cancel()
        restAlertManager.cancelRestAlerts()
    }

    companion object {
        fun factory(
            programId: String,
            sessionId: String,
            restAlertManager: WorkoutRestAlertManager,
        ): ViewModelProvider.Factory =
            object : ViewModelProvider.Factory {
                @Suppress("UNCHECKED_CAST")
                override fun <T : ViewModel> create(modelClass: Class<T>): T =
                    WorkoutViewModel(programId, sessionId, restAlertManager) as T
            }
    }
}

// Extension to flatten exercises from session parts or direct list
fun Session.allExercises(): List<Exercise> =
    if (parts.isNotEmpty()) parts.flatMap { it.exercises } else exercises
