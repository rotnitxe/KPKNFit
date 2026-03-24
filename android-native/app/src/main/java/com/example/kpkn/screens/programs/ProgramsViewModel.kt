package com.example.kpkn.screens.programs

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.example.kpkn.data.models.Program
import com.example.kpkn.data.repository.ProgramRepository
import com.example.kpkn.domain.calculations.getTotalWeeks
import com.example.kpkn.domain.calculations.getSessionExerciseCount
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.stateIn

/**
 * ProgramsViewModel — State management for Programs Screen.
 * Equivalent to PWA: ProgramsView.tsx (lines 68-81)
 *
 * Provides reactive access to:
 * - All programs (filtered into active/inactive)
 * - Program statistics (weeks, sessions)
 * - Navigation callbacks
 *
 * No Hilt — uses ProgramRepository singleton for state
 */
class ProgramsViewModel : ViewModel() {

    private val repository = ProgramRepository.getInstance()

    // ─── Reactive State (StateFlow) ────────────────────────────────────────

    /**
     * All programs from repository.
     * Reactive updates when repository state changes.
     */
    val programs: StateFlow<List<Program>> = repository.programs

    /**
     * Currently active program state (with programId, status, currentWeekId, etc).
     * Null if no program is active.
     */
    val activeProgramState: StateFlow<com.example.kpkn.data.models.ActiveProgramState?> =
        repository.activeProgramState

    /**
     * Currently active Program object (derived from activeProgramState + programs).
     * Null if no active program found or activeProgramState is null.
     *
     * Combines: activeProgramState + programs.
     * Updates whenever either changes.
     */
    val activeProgram: StateFlow<Program?> = combine(activeProgramState, programs) { active, all ->
        if (active != null) all.find { it.id == active.programId } else null
    }.stateIn(viewModelScope, SharingStarted.Lazily, null)

    /**
     * All programs that are NOT currently active (for display in list).
     * Derived: filters programs where id != activeProgramState.programId.
     *
     * Combines: programs + activeProgramState.
     * Updates whenever either changes.
     */
    val inactivePrograms: StateFlow<List<Program>> = combine(programs, activeProgramState) { all, active ->
        all.filter { it.id != active?.programId }
    }.stateIn(viewModelScope, SharingStarted.Lazily, emptyList())

    // ─── Business Logic ────────────────────────────────────────────────────

    /**
     * Compute statistics for a program.
     * Port of PWA calculateProgramStats() from ProgramsView.tsx.
     *
     * @param program The program to analyze.
     * @return ProgramStats with total weeks and session count.
     */
    fun getProgramStats(program: Program): ProgramStats {
        val weeks = getTotalWeeks(program)
        var sessions = 0

        // Count all sessions across all macrocycles → blocks → mesocycles → weeks
        for (macro in program.macrocycles) {
            for (block in macro.blocks) {
                for (meso in block.mesocycles) {
                    for (week in meso.weeks) {
                        sessions += week.sessions.size
                    }
                }
            }
        }

        return ProgramStats(weeks = weeks, sessions = sessions)
    }

    /**
     * Delete a program by ID.
     * Calls repository.deleteProgram().
     *
     * @param programId ID of program to delete.
     */
    fun deleteProgram(programId: String) {
        repository.deleteProgram(programId)
    }

    /**
     * Navigate to program detail screen.
     * Invokes the callback provided by the UI layer (composable).
     * Used for screen navigation (Navigation Compose NavController handled by UI).
     *
     * @param programId ID of program to view.
     * @param onNavigate Callback: (programId: String) -> Unit. Called by composable to trigger navigation.
     */
    fun navigateToProgram(programId: String, onNavigate: (String) -> Unit) {
        onNavigate(programId)
    }
}

// ─── Data Classes ──────────────────────────────────────────────────────────

/**
 * Statistics computed for a program.
 * Used by UI to display program summary.
 */
data class ProgramStats(
    val weeks: Int,
    val sessions: Int,
)