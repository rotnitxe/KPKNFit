package com.example.kpkn.screens.programs

import android.app.Application
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.example.kpkn.data.models.Block
import com.example.kpkn.data.models.Macrocycle
import com.example.kpkn.data.models.Mesocycle
import com.example.kpkn.data.models.PowerliftingProfile
import com.example.kpkn.data.models.Program
import com.example.kpkn.data.models.ProgramMode
import com.example.kpkn.data.models.ProgramStatus
import com.example.kpkn.data.models.ProgramStructure
import com.example.kpkn.data.models.ProgramWeek
import com.example.kpkn.data.programs.resolveProgramTemplate
import com.example.kpkn.data.protocols.PROTOCOL_LIBRARY
import com.example.kpkn.data.repository.ProgramRepository
import com.example.kpkn.data.repository.SessionTemplateRepository
import com.example.kpkn.data.sessions.SessionTemplate
import com.example.kpkn.domain.training.ProgramAutoregulationEngine
import com.example.kpkn.domain.training.ProgramProtocolEngine
import com.example.kpkn.domain.training.ProgramTemplateEngine
import java.util.UUID
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.withContext

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
            buildNewProgram(
                id = programId,
                name = "Nuevo programa $nextNumber",
            ),
        )
        return programId
    }

    sealed interface TemplateApplyOutcome {
        data class Created(val programId: String) : TemplateApplyOutcome
        data object RequiresCalibration : TemplateApplyOutcome
    }

    suspend fun createProgramFromTemplate(
        templateId: String,
        skipCalibration: Boolean = false,
        calibration: com.example.kpkn.screens.programdetail.components.VolumeCalibrationResult? = null,
    ): Result<String> = withContext(Dispatchers.Default) {
        runCatching {
            when (val outcome = createProgramFromTemplateGated(templateId, skipCalibration, calibration)) {
                is TemplateApplyOutcome.Created -> outcome.programId
                TemplateApplyOutcome.RequiresCalibration ->
                    error("REQUIRES_CALIBRATION")
            }
        }
    }

    /**
     * Crea el programa pero exige calibración antes de materializar cuando la
     * plantilla trae rutina pre-seleccionada. Sin calibrar no se escala ni se
     * persiste nada a menos que [skipCalibration] sea true (omisión del usuario).
     */
    suspend fun createProgramFromTemplateGated(
        templateId: String,
        skipCalibration: Boolean = false,
        calibration: com.example.kpkn.screens.programdetail.components.VolumeCalibrationResult? = null,
    ): TemplateApplyOutcome =
        withContext(Dispatchers.Default) {
            val template = resolveProgramTemplate(templateId)
            val programId = UUID.randomUUID().toString()
            val pending = calibration ?: pendingCalibrationForCreate
            val base = Program(
                id = programId,
                name = template.name,
                coverImage = "gradient://ember",
                structure = template.type,
                mode = pending?.mode ?: when (template.trackLabel) {
                    "Powerlifting" -> ProgramMode.POWERLIFTING
                    "Powerbuilding" -> ProgramMode.POWERBUILDING
                    else -> ProgramMode.HYPERTROPHY
                },
                volumeRecommendations = pending?.recommendations.orEmpty(),
                athleteProfileScore = pending?.score,
                volumeSystem = if (pending != null) com.example.kpkn.data.models.VolumeSystem.KPNK else null,
            )
            if (!skipCalibration && !com.example.kpkn.domain.training.VolumeCalibrationGate.isVolumeCalibrated(base)) {
                return@withContext TemplateApplyOutcome.RequiresCalibration
            }
            pendingCalibrationForCreate = null
            val result = ProgramTemplateEngine.applyTemplate(
                current = base,
                template = template,
                // During Room hydration the repository can briefly report ready
                // before the derived generation StateFlow has emitted its first
                // catalog.  An explicit empty list disables the engine's safe
                // system-catalog fallback, so only pass USER-aware candidates once
                // there is an actual list to use.
                generationTemplates = generationTemplates.value.takeIf { it.isNotEmpty() },
                exerciseList = com.example.kpkn.data.exercises.exerciseCatalogSnapshot(),
            )
            repository.addProgram(result.program)
            repository.startProgram(result.program.id)
            TemplateApplyOutcome.Created(result.program.id)
        }

    /**
     * Misma puerta para protocolos: exige calibración antes de materializar la
     * rutina pre-seleccionada. Devuelve null si falta calibrar a menos que
     * [skipCalibration] sea true (omisión del usuario).
     */
    fun createProgramFromProtocolGated(
        protocolId: String,
        profile: PowerliftingProfile? = null,
        preferredName: String? = null,
        calibration: com.example.kpkn.screens.programdetail.components.VolumeCalibrationResult? = null,
        skipCalibration: Boolean = false,
    ): String? {
        val protocol = PROTOCOL_LIBRARY.first { it.id == protocolId }
        val programId = UUID.randomUUID().toString()
        val base = Program(
            id = programId,
            name = preferredName?.trim()?.takeIf { it.isNotEmpty() } ?: protocol.name,
            coverImage = "gradient://ember",
            structure = ProgramStructure.SIMPLE,
            mode = ProgramMode.POWERLIFTING,
            powerliftingProfile = profile,
            selectedSplitId = protocol.defaultSplit,
            volumeRecommendations = calibration?.recommendations.orEmpty(),
            athleteProfileScore = calibration?.score,
            volumeSystem = if (calibration != null) com.example.kpkn.data.models.VolumeSystem.KPNK else null,
        )
        if (!skipCalibration && !com.example.kpkn.domain.training.VolumeCalibrationGate.isVolumeCalibrated(base)) {
            return null
        }
        val applied = ProgramProtocolEngine.applyProtocol(
            base,
            protocol,
            exerciseList = com.example.kpkn.data.exercises.exerciseCatalogSnapshot(),
        )
        repository.addProgram(applied)
        return programId
    }

    fun estimatedProfileFromHistory(): PowerliftingProfile? {
        val fromLogs = ProgramAutoregulationEngine.collectE1rmFromHistory(repository.history.value)
        val fromPrograms = programs.value.mapNotNull { it.powerliftingProfile }
        fun best(slot: com.example.kpkn.data.protocols.LiftSlot, fromProfile: (PowerliftingProfile) -> Double?): Double? {
            val logged = fromLogs[slot]
            val profileMax = fromPrograms.mapNotNull(fromProfile).maxOrNull()
            return listOfNotNull(logged, profileMax).maxOrNull()?.takeIf { it > 0.0 }
        }
        val squat = best(com.example.kpkn.data.protocols.LiftSlot.SQUAT) { it.squat1RM ?: it.squatE1RM }
        val bench = best(com.example.kpkn.data.protocols.LiftSlot.BENCH) { it.bench1RM ?: it.benchE1RM }
        val deadlift = best(com.example.kpkn.data.protocols.LiftSlot.DEADLIFT) { it.deadlift1RM ?: it.deadliftE1RM }
        val overhead = best(com.example.kpkn.data.protocols.LiftSlot.OVERHEAD) { it.overhead1RM ?: it.overheadE1RM }
        if (squat == null && bench == null && deadlift == null && overhead == null) return null
        return PowerliftingProfile(
            squat1RM = squat,
            squatE1RM = squat,
            bench1RM = bench,
            benchE1RM = bench,
            deadlift1RM = deadlift,
            deadliftE1RM = deadlift,
            overhead1RM = overhead,
            overheadE1RM = overhead,
        )
    }

    fun createProgramFromProtocol(
        protocolId: String,
        profile: PowerliftingProfile? = null,
        preferredName: String? = null,
        calibration: com.example.kpkn.screens.programdetail.components.VolumeCalibrationResult? = null,
        skipCalibration: Boolean = false,
    ): String? = createProgramFromProtocolGated(protocolId, profile, preferredName, calibration, skipCalibration)

    /**
     * La calibración del sheet previo a crear programa no tiene programa aún:
     * se guarda como borrador pendiente que el siguiente create consume. Como
     * el base de create* parte de Program() vacío, aquí se registra en
     * memoria para que el reintento la aplique al materializar+escalar.
     */
    private var pendingCalibrationForCreate: com.example.kpkn.screens.programdetail.components.VolumeCalibrationResult? = null

    fun applyCalibrationToLatestDraft(
        calibration: com.example.kpkn.screens.programdetail.components.VolumeCalibrationResult,
    ) {
        pendingCalibrationForCreate = calibration
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
 * Construye un programa base NUEVO con estructura Simple mínima (un
 * macrociclo, un bloque, un mesociclo y una semana). Función pura: no
 * persiste ni activa nada. Compartida entre [ProgramsViewModel.createBlankProgram]
 * y el editor directo post-alta ([ProgramEditorViewModel]).
 */
fun buildNewProgram(
    id: String,
    name: String,
    description: String? = null,
    coverImage: String = "gradient://ember",
    mode: ProgramMode = ProgramMode.HYPERTROPHY,
): Program = Program(
    id = id,
    name = name,
    description = description,
    coverImage = coverImage,
    structure = ProgramStructure.SIMPLE,
    mode = mode,
    macrocycles = listOf(
        Macrocycle(
            id = UUID.randomUUID().toString(),
            name = "Macrociclo 1",
            blocks = listOf(
                Block(
                    id = UUID.randomUUID().toString(),
                    name = "Bloque 1",
                    mesocycles = listOf(
                        Mesocycle(
                            id = UUID.randomUUID().toString(),
                            name = "Mesociclo 1",
                            weeks = listOf(
                                ProgramWeek(
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
)

/**
 * Statistics computed for a program.
 * Used by UI to display program summary.
 */
data class ProgramStats(
    val weeks: Int,
    val sessions: Int,
)
