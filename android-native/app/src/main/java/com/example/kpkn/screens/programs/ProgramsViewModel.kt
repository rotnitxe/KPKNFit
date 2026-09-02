package com.example.kpkn.screens.programs

import android.app.Application
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.example.kpkn.data.models.Program
import com.example.kpkn.data.models.ProgramMode
import com.example.kpkn.data.models.ProgramStatus
import com.example.kpkn.data.models.ProgramStructure
import com.example.kpkn.data.programs.resolveProgramTemplate
import com.example.kpkn.data.repository.ProgramRepository
import com.example.kpkn.data.repository.SessionTemplateRepository
import com.example.kpkn.data.sessions.SessionTemplate
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
class ProgramsViewModel(application: Application) : AndroidViewModel(application) {

    private val repository = ProgramRepository.getInstance()
    private val sessionTemplateRepository = SessionTemplateRepository.getInstance(application)

    /**
     * Opt-in USER templates are hydrated off-main by the repository.  Program
     * creation consumes this same list as split preview/application once it is
     * ready; before hydration it intentionally falls back to the published
     * system catalog rather than pretending USER generation is available.
     */
    val generationTemplates: StateFlow<List<SessionTemplate>> =
        sessionTemplateRepository.generationTemplates

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
     * ID of the currently featured program (ACTIVE or PAUSED).
     * Completed programs stay in the regular list.
     */
    private val featuredProgramId: StateFlow<String?> = combine(activeProgramState, programs) { active, _ ->
        if (active?.status == ProgramStatus.ACTIVE || active?.status == ProgramStatus.PAUSED) {
            active.programId
        } else {
            null
        }
    }.stateIn(viewModelScope, SharingStarted.Lazily, null)

    val isFeaturedPaused: StateFlow<Boolean> = activeProgramState
        .combine(featuredProgramId) { active, featuredId ->
            active?.programId == featuredId && active?.status == ProgramStatus.PAUSED
        }
        .stateIn(viewModelScope, SharingStarted.Lazily, false)

    /**
     * Currently featured Program object (derived from activeProgramState + programs).
     */
    val activeProgram: StateFlow<Program?> = combine(programs, featuredProgramId) { all, activeId ->
        activeId?.let { id -> all.find { it.id == id } }
    }.stateIn(viewModelScope, SharingStarted.Lazily, null)

    /**
     * All programs that are NOT currently featured (for display in list).
     */
    val inactivePrograms: StateFlow<List<Program>> = combine(programs, featuredProgramId) { all, activeId ->
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
        var weeks = 0
        var sessions = 0

        for (macro in program.macrocycles) {
            for (block in macro.blocks) {
                for (meso in block.mesocycles) {
                    for (week in meso.weeks) {
                        if (!week.isLoopWeek) weeks++
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
                        name = "Macrociclo 1",
                        blocks = listOf(
                            com.example.kpkn.data.models.Block(
                                id = UUID.randomUUID().toString(),
                                name = "Bloque 1",
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
        val result = ProgramTemplateEngine.applyTemplate(
            current = base,
            template = template,
            // During Room hydration the repository can briefly report ready
            // before the derived generation StateFlow has emitted its first
            // catalog.  An explicit empty list disables the engine's safe
            // system-catalog fallback, so only pass USER-aware candidates once
            // there is an actual list to use.
            generationTemplates = generationTemplates.value.takeIf { it.isNotEmpty() },
        )
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

    fun resumeProgram() {
        repository.resumeProgram()
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
