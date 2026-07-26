package com.example.kpkn.screens.programs

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.example.kpkn.data.models.Program
import com.example.kpkn.data.models.ProgramMode
import com.example.kpkn.data.models.ProgramStatus
import com.example.kpkn.data.models.ProgramStructure
import com.example.kpkn.data.programs.resolveProgramTemplate
import com.example.kpkn.data.repository.ProgramRepository
import com.example.kpkn.domain.calculations.getTotalWeeks
import com.example.kpkn.domain.calculations.getSessionExerciseCount
import com.example.kpkn.domain.training.ProgramTemplateEngine
import java.util.UUID
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
    val programs: StateFlow<List<Program>> = combine(
        repository.programs,
        repository.settings,
    ) { all, settings ->
        all.filter { it.id !in settings.archivedProgramIds }
    }.stateIn(viewModelScope, SharingStarted.Lazily, emptyList())

    val archivedPrograms: StateFlow<List<Program>> = combine(
        repository.programs,
        repository.settings,
    ) { all, settings ->
        settings.archivedProgramIds.mapNotNull { id -> all.find { it.id == id } }
    }.stateIn(viewModelScope, SharingStarted.Lazily, emptyList())

    /**
     * Currently active program state (with programId, status, currentWeekId, etc).
     * Null if no program is active.
     */
    val activeProgramState: StateFlow<com.example.kpkn.data.models.ActiveProgramState?> =
        repository.activeProgramState

    /**
     * ID of the currently active program, only when the stored state is truly ACTIVE.
     * Paused/completed programs stay in the regular list.
     */
    private val activeProgramId: StateFlow<String?> = combine(activeProgramState, programs) { active, _ ->
        if (active?.status == ProgramStatus.ACTIVE) active.programId else null
    }.stateIn(viewModelScope, SharingStarted.Lazily, null)

    /**
     * Currently active Program object (derived from activeProgramState + programs).
     * Null if no active program found or activeProgramState is null.
     *
     * Combines: activeProgramState + programs.
     * Updates whenever either changes.
     */
    val activeProgram: StateFlow<Program?> = combine(programs, activeProgramId) { all, activeId ->
        activeId?.let { id -> all.find { it.id == id } }
    }.stateIn(viewModelScope, SharingStarted.Lazily, null)

    /**
     * All programs that are NOT currently active (for display in list).
     * Derived: filters programs where id != activeProgramId.
     *
     * Combines: programs + activeProgramId.
     * Updates whenever either changes.
     */
    val inactivePrograms: StateFlow<List<Program>> = combine(programs, activeProgramId) { all, activeId ->
        all.filter { it.id != activeId }
    }.stateIn(viewModelScope, SharingStarted.Lazily, emptyList())

    val programQueue: StateFlow<List<Program>> = combine(repository.programQueue, programs) { queue, all ->
        queue.mapNotNull { id -> all.find { it.id == id } }
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

    fun archiveProgram(programId: String) {
        repository.archiveProgram(programId)
    }

    fun restoreArchivedProgram(programId: String) {
        repository.restoreArchivedProgram(programId)
    }

    fun permanentlyDeleteProgram(programId: String) {
        repository.permanentlyDeleteProgram(programId)
    }

    fun createBlankProgram(): String {
        val programId = UUID.randomUUID().toString()
        val nextNumber = repository.programs.value.count { it.name.startsWith("Nuevo programa") } + 1
        repository.addProgram(
            Program(
                id = programId,
                name = "Nuevo programa $nextNumber",
                coverImage = "gradient://ember",
                structure = ProgramStructure.SIMPLE,
                macrocycles = listOf(
                    com.example.kpkn.data.models.Macrocycle(
                        id = UUID.randomUUID().toString(),
                        name = "Macrociclo base",
                        blocks = listOf(
                            com.example.kpkn.data.models.Block(
                                id = UUID.randomUUID().toString(),
                                name = "Ciclo base",
                                mesocycles = listOf(
                                    com.example.kpkn.data.models.Mesocycle(
                                        id = UUID.randomUUID().toString(),
                                        name = "Mesociclo 1",
                                        weeks = listOf(
                                            com.example.kpkn.data.models.ProgramWeek(
                                                id = UUID.randomUUID().toString(),
                                                name = "Semana 1",
                                            ),
                                        ),
                                    ),
                                ),
                            ),
                        ),
                    ),
                ),
            ),
        )
        return programId
    }

    fun createProgramFromTemplate(templateId: String): String {
        val template = resolveProgramTemplate(templateId)
        val programId = UUID.randomUUID().toString()
        val base = Program(
            id = programId,
            name = template.name,
            coverImage = "gradient://ember",
            structure = template.type,
            mode = when (template.trackLabel) {
                "Powerlifting" -> ProgramMode.POWERLIFTING
                "Powerbuilding" -> ProgramMode.POWERBUILDING
                else -> ProgramMode.HYPERTROPHY
            },
        )
        val result = ProgramTemplateEngine.applyTemplate(base, template)
        repository.addProgram(result.program)
        return programId
    }

    fun addToQueue(programId: String) {
        repository.addProgramToQueue(programId)
    }

    fun removeFromQueue(programId: String) {
        repository.removeProgramFromQueue(programId)
    }

    fun moveQueuedProgram(programId: String, direction: Int) {
        repository.moveQueuedProgram(programId, direction)
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
