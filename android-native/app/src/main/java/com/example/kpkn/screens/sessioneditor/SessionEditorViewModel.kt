package com.example.kpkn.screens.sessioneditor

import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import com.example.kpkn.data.models.*
import com.example.kpkn.data.repository.ProgramRepository
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update

data class SessionEditorUiState(
    val session: Session? = null,
    val programId: String = "",
    val weekId: String = "",
    val macroIndex: Int = 0,
    val mesoIndex: Int = 0,
    val isPickerOpen: Boolean = false,
    val searchQuery: String = "",
    val pickerTargetPartIdx: Int = 0,
    val hasUnsavedChanges: Boolean = false,
)

// Part colors palette (10 colors matching PWA)
val PART_COLORS = listOf(
    "#6366F1", "#10B981", "#F59E0B", "#EF4444", "#3B82F6",
    "#8B5CF6", "#EC4899", "#14B8A6", "#F97316", "#84CC16",
)

class SessionEditorViewModel(
    private val programId: String,
    private val sessionId: String,
) : ViewModel() {

    private val repository = ProgramRepository.getInstance()

    private val _uiState = MutableStateFlow(SessionEditorUiState(programId = programId))
    val uiState: StateFlow<SessionEditorUiState> = _uiState.asStateFlow()

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

        // If not found by ID, create a new session draft
        val session = foundSession ?: Session(
            id = sessionId,
            name = "Nueva Sesión",
            parts = listOf(
                SessionPart(
                    id = java.util.UUID.randomUUID().toString(),
                    name = "Parte Principal",
                    color = PART_COLORS[0],
                )
            ),
        )

        _uiState.update {
            it.copy(
                session = session,
                weekId = foundWeekId,
                macroIndex = foundMacroIdx,
                mesoIndex = foundMesoIdx,
            )
        }
    }

    // ─── Session field updates ────────────────────────────────────────────────

    fun updateSessionName(name: String) = updateSession { it.copy(name = name) }
    fun updateSessionDescription(desc: String) = updateSession { it.copy(description = desc) }

    private fun updateSession(transform: (Session) -> Session) {
        _uiState.update { state ->
            state.copy(
                session = state.session?.let(transform),
                hasUnsavedChanges = true,
            )
        }
    }

    // ─── Parts ───────────────────────────────────────────────────────────────

    fun addPart() {
        val colorIdx = (_uiState.value.session?.parts?.size ?: 0) % PART_COLORS.size
        val newPart = SessionPart(
            id = java.util.UUID.randomUUID().toString(),
            name = "Parte ${(_uiState.value.session?.parts?.size ?: 0) + 1}",
            color = PART_COLORS[colorIdx],
        )
        updateSession { it.copy(parts = it.parts + newPart) }
    }

    fun updatePartName(partIdx: Int, name: String) {
        updateSession { session ->
            session.copy(parts = session.parts.mapIndexed { i, p ->
                if (i == partIdx) p.copy(name = name) else p
            })
        }
    }

    fun updatePartColor(partIdx: Int, color: String) {
        updateSession { session ->
            session.copy(parts = session.parts.mapIndexed { i, p ->
                if (i == partIdx) p.copy(color = color) else p
            })
        }
    }

    fun removePart(partIdx: Int) {
        updateSession { session ->
            session.copy(parts = session.parts.filterIndexed { i, _ -> i != partIdx })
        }
    }

    // ─── Exercises ───────────────────────────────────────────────────────────

    fun openPicker(partIdx: Int) {
        _uiState.update { it.copy(isPickerOpen = true, pickerTargetPartIdx = partIdx, searchQuery = "") }
    }

    fun closePicker() {
        _uiState.update { it.copy(isPickerOpen = false) }
    }

    fun setSearchQuery(q: String) {
        _uiState.update { it.copy(searchQuery = q) }
    }

    fun addExerciseToPart(partIdx: Int, name: String, exerciseDbId: String?) {
        val newExercise = Exercise(
            id = java.util.UUID.randomUUID().toString(),
            name = name,
            exerciseDbId = exerciseDbId,
            sets = listOf(
                ExerciseSet(
                    id = java.util.UUID.randomUUID().toString(),
                    targetReps = 8,
                )
            ),
            restTime = 90,
        )
        updateSession { session ->
            session.copy(parts = session.parts.mapIndexed { i, p ->
                if (i == partIdx) p.copy(exercises = p.exercises + newExercise) else p
            })
        }
        closePicker()
    }

    fun removeExercise(partIdx: Int, exIdx: Int) {
        updateSession { session ->
            session.copy(parts = session.parts.mapIndexed { i, p ->
                if (i != partIdx) p
                else p.copy(exercises = p.exercises.filterIndexed { ei, _ -> ei != exIdx })
            })
        }
    }

    fun updateExerciseName(partIdx: Int, exIdx: Int, name: String) {
        updateExerciseField(partIdx, exIdx) { it.copy(name = name) }
    }

    fun updateExerciseRestTime(partIdx: Int, exIdx: Int, seconds: Int) {
        updateExerciseField(partIdx, exIdx) { it.copy(restTime = seconds) }
    }

    private fun updateExerciseField(partIdx: Int, exIdx: Int, transform: (Exercise) -> Exercise) {
        updateSession { session ->
            session.copy(parts = session.parts.mapIndexed { i, p ->
                if (i != partIdx) p
                else p.copy(exercises = p.exercises.mapIndexed { ei, ex ->
                    if (ei == exIdx) transform(ex) else ex
                })
            })
        }
    }

    // ─── Sets ────────────────────────────────────────────────────────────────

    fun addSet(partIdx: Int, exIdx: Int) {
        updateSession { session ->
            session.copy(parts = session.parts.mapIndexed { i, p ->
                if (i != partIdx) p
                else p.copy(exercises = p.exercises.mapIndexed { ei, ex ->
                    if (ei != exIdx) ex
                    else {
                        val lastSet = ex.sets.lastOrNull()
                        val newSet = ExerciseSet(
                            id = java.util.UUID.randomUUID().toString(),
                            targetReps = lastSet?.targetReps ?: 8,
                            targetRPE = lastSet?.targetRPE,
                            weight = lastSet?.weight,
                        )
                        ex.copy(sets = ex.sets + newSet)
                    }
                })
            })
        }
    }

    fun removeSet(partIdx: Int, exIdx: Int, setIdx: Int) {
        updateSession { session ->
            session.copy(parts = session.parts.mapIndexed { i, p ->
                if (i != partIdx) p
                else p.copy(exercises = p.exercises.mapIndexed { ei, ex ->
                    if (ei != exIdx) ex
                    else ex.copy(sets = ex.sets.filterIndexed { si, _ -> si != setIdx })
                })
            })
        }
    }

    fun updateSet(partIdx: Int, exIdx: Int, setIdx: Int, targetReps: Int?, weight: Double?, targetRPE: Double?) {
        updateSession { session ->
            session.copy(parts = session.parts.mapIndexed { i, p ->
                if (i != partIdx) p
                else p.copy(exercises = p.exercises.mapIndexed { ei, ex ->
                    if (ei != exIdx) ex
                    else ex.copy(sets = ex.sets.mapIndexed { si, s ->
                        if (si != setIdx) s
                        else s.copy(
                            targetReps = targetReps ?: s.targetReps,
                            weight = weight ?: s.weight,
                            targetRPE = targetRPE ?: s.targetRPE,
                        )
                    })
                })
            })
        }
    }

    // ─── Save ────────────────────────────────────────────────────────────────

    fun saveSession(): Boolean {
        val state = _uiState.value
        val session = state.session ?: return false
        val program = repository.getProgramById(programId) ?: return false

        val updated = program.copy(
            macrocycles = program.macrocycles.mapIndexed { macroIdx, macro ->
                if (macroIdx != state.macroIndex) macro
                else macro.copy(blocks = macro.blocks.map { block ->
                    block.copy(mesocycles = block.mesocycles.mapIndexed { mesoIdx, meso ->
                        if (mesoIdx != state.mesoIndex) meso
                        else meso.copy(weeks = meso.weeks.map { week ->
                            if (week.id != state.weekId) week
                            else week.copy(sessions = week.sessions.map { s ->
                                if (s.id == session.id) session else s
                            }.let { list ->
                                // If session not found (new), add it
                                if (list.none { it.id == session.id }) list + session else list
                            })
                        })
                    })
                })
            }
        )
        repository.updateProgram(updated)
        _uiState.update { it.copy(hasUnsavedChanges = false) }
        return true
    }

    companion object {
        fun factory(programId: String, sessionId: String): ViewModelProvider.Factory =
            object : ViewModelProvider.Factory {
                @Suppress("UNCHECKED_CAST")
                override fun <T : ViewModel> create(modelClass: Class<T>): T =
                    SessionEditorViewModel(programId, sessionId) as T
            }
    }
}
