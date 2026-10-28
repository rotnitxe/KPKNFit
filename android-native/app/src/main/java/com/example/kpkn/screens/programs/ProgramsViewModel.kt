package com.example.kpkn.screens.programs

import android.app.Application
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.example.kpkn.data.models.ActiveProgramState
import com.example.kpkn.data.models.Block
import com.example.kpkn.data.models.KeyDateType
import com.example.kpkn.data.models.Macrocycle
import com.example.kpkn.data.models.Mesocycle
import com.example.kpkn.data.models.OngoingWorkoutState
import com.example.kpkn.data.models.PowerliftingProfile
import com.example.kpkn.data.models.Program
import com.example.kpkn.data.models.ProgramMode
import com.example.kpkn.data.models.ProgramRunStatus
import com.example.kpkn.data.models.ProgramStatus
import com.example.kpkn.data.models.ProgramStructure
import com.example.kpkn.data.models.ProgramWeek
import com.example.kpkn.data.models.WorkoutLog
import com.example.kpkn.data.models.isSimpleProgram
import com.example.kpkn.data.programs.PersonalizedPlanCatalog
import com.example.kpkn.data.programs.programModeFor
import com.example.kpkn.data.programs.programNameFor
import com.example.kpkn.data.programs.resolveProgramTemplate
import com.example.kpkn.data.protocols.PROTOCOL_LIBRARY
import com.example.kpkn.data.repository.ProgramRepository
import com.example.kpkn.data.repository.SessionTemplateRepository
import com.example.kpkn.data.sessions.SessionTemplate
import com.example.kpkn.domain.training.ProgramAutoregulationEngine
import com.example.kpkn.domain.training.ProgramProtocolEngine
import com.example.kpkn.domain.training.HomeSessionResolver
import com.example.kpkn.domain.training.HomeWeekResolver
import com.example.kpkn.domain.training.ProgramDetailHelpers
import com.example.kpkn.domain.training.ProgramHierarchyIndex
import com.example.kpkn.domain.training.ProgramTemplateEngine
import com.example.kpkn.domain.training.SystemAppClock
import com.example.kpkn.screens.programdetail.design.ProgramRibbonWeek
import com.example.kpkn.screens.programdetail.design.programPhaseColor
import com.example.kpkn.screens.programdetail.design.programPhaseOf
import com.example.kpkn.screens.programdetail.design.programProgressLabel
import java.time.Instant
import java.time.LocalDate
import java.time.ZoneId
import java.util.UUID
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.flowOn
import kotlinx.coroutines.flow.map
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

    /** Todos los registros de entrenamiento: la hoja «Compartir en historias» de la lista filtra los de cada programa. */
    val history: StateFlow<List<WorkoutLog>> = repository.history

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
     * Programas que NO son el de en curso ni están en cola: «Tus programas». Un programa encolado sale solo en «En cola».
     */
    val inactivePrograms: StateFlow<List<Program>> = combine(programs, featuredProgramId, repository.programQueue) { all, activeId, queue ->
        inactiveProgramsOf(all = all, featuredId = activeId, queuedIds = queue)
    }.stateIn(viewModelScope, SharingStarted.Lazily, emptyList())

    // Si se activa un programa que estaba en cola, sale en «En curso» y no en «En cola» a la vez.
    val programQueue: StateFlow<List<Program>> = combine(repository.programQueue, programs, featuredProgramId) { queue, all, activeId ->
        queue.filter { it != activeId }.mapNotNull { id -> all.find { it.id == id } }
    }.stateIn(viewModelScope, SharingStarted.Lazily, emptyList())

    /**
     * Lo que la tarjeta «En curso» enseña del programa activo: sesión de hoy, semana y cinta de fases. Null sin programa en curso o pausado.
     */
    internal val activeSummary: StateFlow<ActiveProgramSummary?> = combine(
        activeProgram,
        activeProgramState,
        repository.history,
        repository.ongoingWorkout,
    ) { program, active, history, ongoing ->
        program?.let { summarizeActiveProgram(it, active, history, ongoing, SystemAppClock.today(ZoneId.systemDefault())) }
    }
        .distinctUntilChanged()
        .flowOn(Dispatchers.Default)
        .stateIn(viewModelScope, SharingStarted.Lazily, null)

    /**
     * Último día con un registro de cada programa: la fecha de «Terminado · d MMM». El programa no guarda fecha de cierre.
     */
    val lastTrainedOn: StateFlow<Map<String, LocalDate>> = repository.history
        .map { logs -> lastTrainedDatesOf(logs.map { it.programId to it.date }, ZoneId.systemDefault()) }
        .distinctUntilChanged()
        .flowOn(Dispatchers.Default)
        .stateIn(viewModelScope, SharingStarted.Lazily, emptyMap())

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

    /**
     * Crea un programa en blanco. [name] en blanco o null → «Nuevo programa N»; [mode] y [coverImage] (si no es null) son los que
     * eligió la hoja «Desde cero», y sustituyen a los de siempre.
     */
    fun createBlankProgram(
        name: String? = null,
        mode: ProgramMode = ProgramMode.HYPERTROPHY,
        coverImage: String? = null,
    ): String {
        val programId = UUID.randomUUID().toString()
        // El número sale del mayor «Nuevo programa N» que ya existe (contar nombres repetía números tras borrar uno).
        val nextNumber = nextBlankProgramNumber(repository.programs.value.map { it.name })
        val base = buildNewProgram(
            id = programId,
            name = name?.trim()?.takeIf { it.isNotEmpty() } ?: "Nuevo programa $nextNumber",
            mode = mode,
        )
        repository.addProgram(coverImage?.let { base.copy(coverImage = it) } ?: base)
        return programId
    }

    /** El nombre que propone la hoja «Desde cero»: el mismo que [createBlankProgram] pone si no se escribe ninguno. */
    fun suggestedBlankProgramName(): String =
        "Nuevo programa ${nextBlankProgramNumber(repository.programs.value.map { it.name })}"

    sealed interface TemplateApplyOutcome {
        data class Created(val programId: String) : TemplateApplyOutcome
    }

    suspend fun createProgramFromTemplate(
        templateId: String,
        skipCalibration: Boolean = false,
        calibration: com.example.kpkn.screens.programdetail.components.VolumeCalibrationResult? = null,
    ): Result<String> = withContext(Dispatchers.Default) {
        runCatching {
            when (val outcome = createProgramFromTemplateGated(templateId, skipCalibration, calibration)) {
                is TemplateApplyOutcome.Created -> outcome.programId
            }
        }
    }

    /**
     * Crea con la dosis editorial. Con respuestas aportadas expresamente ([calibration]) escala con ellas; sin ellas, con la
     * calibración sugerida por KPKN (IMP-03, [suggestedCalibrationFor]). [skipCalibration] se conserva por compatibilidad;
     * el cuestionario ya no bloquea crear.
     */
    suspend fun createProgramFromTemplateGated(
        templateId: String,
        skipCalibration: Boolean = false,
        calibration: com.example.kpkn.screens.programdetail.components.VolumeCalibrationResult? = null,
    ): TemplateApplyOutcome =
        withContext(Dispatchers.Default) {
            val template = resolveProgramTemplate(templateId)
            val programId = UUID.randomUUID().toString()
            val pending = calibration
            // C.P6: la plantilla se llama como su ficha de la biblioteca (nunca el nombre crudo de la plantilla) y,
            // si no declara una pista, toma el modo de la disciplina de la entrada.
            val entry = PersonalizedPlanCatalog.find("template:${template.id}")
            val templateMode = pending?.mode ?: when (template.trackLabel) {
                "Powerlifting" -> ProgramMode.POWERLIFTING
                "Powerbuilding" -> ProgramMode.POWERBUILDING
                else -> entry?.let(::programModeFor) ?: ProgramMode.HYPERTROPHY
            }
            // IMP-03: sin respuestas propias el programa nace con la calibración sugerida, así que `scaleToCalibratedVolume` escala.
            val suggested = if (pending == null) suggestedCalibrationFor(templateMode) else null
            val base = Program(
                id = programId,
                name = entry?.let(::programNameFor) ?: template.name,
                coverImage = "gradient://ember",
                structure = template.type,
                mode = templateMode,
                volumeRecommendations = pending?.recommendations ?: suggested?.recommendations.orEmpty(),
                athleteProfileScore = pending?.score ?: suggested?.score,
                volumeSystem = if (pending != null || suggested != null) com.example.kpkn.data.models.VolumeSystem.KPNK else null,
                volumeCalibrationNotes = pending?.notes.orEmpty(),
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
                exerciseList = com.example.kpkn.data.exercises.exerciseCatalogSnapshot(),
            )
            repository.addProgram(result.program)
            repository.startProgram(result.program.id)
            TemplateApplyOutcome.Created(result.program.id)
        }

    /**
     * Materializa el protocolo con sus referencias declaradas. Sin [calibration] propia usa la sugerida por KPKN
     * (IMP-03, [suggestedCalibrationFor]). [skipCalibration] se conserva por compatibilidad; la ausencia de
     * autovaloraciones no bloquea la creación.
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
        // C.P6: el nombre que la persona escribió sigue mandando; si no, el de la ficha de la biblioteca. El modo
        // sale de la disciplina de la entrada (antes TODOS los métodos salían como powerlifting, también PHUL).
        val entry = PersonalizedPlanCatalog.find("protocol:${protocol.id}")
        val protocolMode = entry?.let(::programModeFor) ?: ProgramMode.POWERLIFTING
        // IMP-03: sin respuestas propias, la calibración sugerida (el estilo sale del modo del método).
        val suggested = if (calibration == null) suggestedCalibrationFor(protocolMode) else null
        val base = Program(
            id = programId,
            name = preferredName?.trim()?.takeIf { it.isNotEmpty() } ?: entry?.let(::programNameFor) ?: protocol.name,
            coverImage = "gradient://ember",
            structure = ProgramStructure.SIMPLE,
            mode = protocolMode,
            powerliftingProfile = profile,
            selectedSplitId = protocol.defaultSplit,
            volumeRecommendations = calibration?.recommendations ?: suggested?.recommendations.orEmpty(),
            athleteProfileScore = calibration?.score ?: suggested?.score,
            volumeSystem = if (calibration != null || suggested != null) com.example.kpkn.data.models.VolumeSystem.KPNK else null,
            volumeCalibrationNotes = calibration?.notes.orEmpty(),
        )
        // La falta de cuestionario no bloquea una receta de autor ni modifica su dosis.
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
     * IMP-03: la calibración que recibe un programa creado sin respuestas propias: (2, 2, 2, 2) con el estilo del modo
     * ([VolumeCalibrationEngine.suggested]). Si la persona aún no tiene un perfil global, se estrena con esta sugerencia en
     * estado SUGGESTED para que la tarjeta «Tu volumen» diga de dónde sale; un perfil ya declarado nunca se pisa.
     */
    private fun suggestedCalibrationFor(mode: ProgramMode): com.example.kpkn.domain.training.VolumeCalibrationOutput {
        val suggested = com.example.kpkn.domain.training.VolumeCalibrationEngine.suggested(mode)
        repository.updateSettings { settings ->
            if (settings.volumeCalibrationProfile != null) settings else settings.copy(
                volumeCalibrationProfile = com.example.kpkn.data.models.VolumeCalibrationProfile(
                    trainingStyle = suggested.score.trainingStyle,
                    athleteProfileScore = suggested.score,
                    responses = com.example.kpkn.data.models.VolumeCalibrationResponses(
                        suggested.score.technicalScore, suggested.score.consistencyScore,
                        suggested.score.strengthScore, suggested.score.mobilityScore,
                        com.example.kpkn.data.models.CalibrationResponseState.SUGGESTED,
                    ),
                    recommendations = suggested.recommendations,
                    calibratedAtMs = System.currentTimeMillis(),
                    calculatorRevision = com.example.kpkn.domain.training.VolumeCalibrationEngine.REVISION,
                ),
            )
        }
        return suggested
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

    /** Activa el programa: pasa a ser el de en curso, igual que «Activar programa» de su detalle. */
    fun startProgram(programId: String) {
        repository.startProgram(programId)
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

/** Lo que la tarjeta «En curso» enseña del programa activo. */
internal data class ActiveProgramSummary(
    val programId: String,
    /** Sesión de hoy que queda por hacer; null si hoy no toca ninguna. */
    val todaySessionName: String?,
    /** «Semana 4 de 6 · Intensificación»; null si no se sabe en qué semana va. */
    val progressLabel: String?,
    /** Una celda por semana de la cinta de fases; vacía si el programa tiene una sola semana. */
    val ribbon: List<ProgramRibbonWeek>,
)

private val BLANK_PROGRAM_NAME = Regex("""Nuevo programa (\d+)""")

/** El número del próximo «Nuevo programa N»: el mayor que ya usa algún nombre, más uno. Un hueco no se reutiliza. */
internal fun nextBlankProgramNumber(existingNames: Collection<String>): Int {
    val highest = existingNames
        .mapNotNull { name -> BLANK_PROGRAM_NAME.matchEntire(name.trim())?.groupValues?.get(1)?.toIntOrNull() }
        .maxOrNull() ?: 0
    return highest + 1
}

/** «Tus programas»: los que no son el de en curso ni están en cola. */
internal fun inactiveProgramsOf(all: List<Program>, featuredId: String?, queuedIds: List<String>): List<Program> =
    all.filter { it.id != featuredId && it.id !in queuedIds }

/** Terminado si su ciclo guardado quedó completado. El resto son borradores, igual que en la cabecera del detalle. */
internal fun programIsFinished(program: Program): Boolean = program.runState?.status == ProgramRunStatus.COMPLETED

private val SHORT_MONTHS = listOf("ene", "feb", "mar", "abr", "may", "jun", "jul", "ago", "sep", "oct", "nov", "dic")

/** Estado de una tarjeta de «Tus programas»: «Borrador», «Terminado · 12 sep» o «Terminado» si no se sabe la fecha. */
internal fun programListStatusLabel(completed: Boolean, completedOn: LocalDate?): String = when {
    !completed -> "Borrador"
    completedOn == null -> "Terminado"
    else -> "Terminado · ${completedOn.dayOfMonth} ${SHORT_MONTHS[completedOn.monthValue - 1]}"
}

/** El último día con un registro de cada programa, a partir de pares (programa, fecha ISO). Las fechas que no se leen se ignoran. */
internal fun lastTrainedDatesOf(entries: List<Pair<String, String>>, zone: ZoneId): Map<String, LocalDate> {
    val days = HashMap<String, LocalDate>()
    entries.forEach { (programId, raw) ->
        val day = parseLogDay(raw, zone) ?: return@forEach
        val known = days[programId]
        if (known == null || day.isAfter(known)) days[programId] = day
    }
    return days
}

private fun parseLogDay(raw: String, zone: ZoneId): LocalDate? =
    runCatching { Instant.parse(raw).atZone(zone).toLocalDate() }
        .recoverCatching { LocalDate.parse(raw.take(10)) }
        .getOrNull()

/** La cinta de fases: una celda por semana, del color de la fase de su bloque. Hecha si va antes de la semana actual. */
internal fun programRibbonOf(program: Program, currentWeekIndex: Int?): List<ProgramRibbonWeek> {
    val roadmap = ProgramDetailHelpers.buildRoadmapBlocks(program)
    val phaseByWeek = roadmap.flatMap { block ->
        val weeks = ProgramDetailHelpers.getWeeksForBlock(block.id, roadmap, program)
        val phase = programPhaseOf(goal = block.goal, hasCompetitionWeek = weeks.any { it.keyDateType == KeyDateType.COMPETITION })
        weeks.map { week -> week.id to phase }
    }.toMap()
    return ProgramDetailHelpers.getAllWeeks(roadmap, program).mapIndexed { index, week ->
        ProgramRibbonWeek(
            color = programPhaseColor(phaseByWeek[week.id]),
            done = currentWeekIndex != null && index < currentWeekIndex,
            current = index == currentWeekIndex,
        )
    }
}

/**
 * Lo que la tarjeta «En curso» enseña, con el mismo cálculo que la cabecera del detalle: la primera sesión de hoy por hacer, la
 * línea «Semana N de M · Fase» y la cinta. Si [active] no es de [program], no hay sesiones de hoy.
 */
internal fun summarizeActiveProgram(
    program: Program,
    active: ActiveProgramState?,
    history: List<WorkoutLog>,
    ongoing: OngoingWorkoutState?,
    today: LocalDate,
): ActiveProgramSummary {
    val sessions = if (active?.programId == program.id) {
        HomeSessionResolver.resolveTodaySessions(
            program = program,
            active = active,
            currentDayOfWeek = today.dayOfWeek.value,
            history = history,
            ongoing = ongoing,
            today = today,
        )
    } else {
        emptyList()
    }
    val line = HomeWeekResolver.programLine(program, sessions, today)
    val weekCount = ProgramHierarchyIndex(program).orderedWeeks().size
    val repeatingWeek = program.isSimpleProgram && weekCount <= 1
    return ActiveProgramSummary(
        programId = program.id,
        todaySessionName = HomeSessionResolver.selectPrimarySession(sessions)?.takeIf { !it.isCompleted }?.session?.name,
        progressLabel = programProgressLabel(
            weekIndex = line?.weekIndex,
            weekCount = line?.weekCount,
            phase = line?.phase,
            repeatingWeek = repeatingWeek,
            cycle = program.runState?.cycleNumber,
        ),
        ribbon = if (weekCount > 1) programRibbonOf(program, line?.weekIndex?.minus(1)) else emptyList(),
    )
}
