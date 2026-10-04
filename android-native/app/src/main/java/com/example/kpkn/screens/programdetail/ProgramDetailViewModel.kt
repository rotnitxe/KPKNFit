package com.example.kpkn.screens.programdetail

import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.viewModelScope
import com.example.kpkn.data.exercises.catalogConfigurationDisplayName
import com.example.kpkn.data.exercises.catalogExerciseIndex
import com.example.kpkn.data.exercises.catalogSearchRedirects
import com.example.kpkn.data.exercises.resolveCatalogExerciseInfo
import com.example.kpkn.data.models.ActiveProgramState
import com.example.kpkn.data.models.AutoregulationProposal
import com.example.kpkn.data.models.Block
import com.example.kpkn.data.models.BlockGoal
import com.example.kpkn.data.models.BlockProgressionScheme
import com.example.kpkn.data.models.EquipmentInventory
import com.example.kpkn.data.models.Exercise
import com.example.kpkn.data.models.HYPERTROPHY_ROLE_MULTIPLIERS
import com.example.kpkn.data.models.Mesocycle
import com.example.kpkn.data.models.MesocycleGoal
import com.example.kpkn.data.models.OptionalSessionConfirmation
import com.example.kpkn.data.models.Program
import com.example.kpkn.data.models.ProgramCalendarizationMode
import com.example.kpkn.data.models.ProgramStructure
import com.example.kpkn.data.models.ProgramWeek
import com.example.kpkn.data.models.ProgramStatus
import com.example.kpkn.data.models.PendingProgramActionType
import com.example.kpkn.data.models.Session
import com.example.kpkn.data.models.SimpleProgramKind
import com.example.kpkn.data.models.WorkoutLog
import com.example.kpkn.data.models.isSimpleTemporalProgram
import com.example.kpkn.data.models.nextSimpleCalendarStart
import com.example.kpkn.data.models.normalizedTemporalStructure
import com.example.kpkn.data.models.resolvedSchedulePlan
import com.example.kpkn.data.models.resolveMuscleVolumeContribution
import com.example.kpkn.data.models.restorePausedCyclicProgram
import com.example.kpkn.data.models.startFreshSimpleCycle
import com.example.kpkn.data.models.startSimpleCalendarizedBreak
import com.example.kpkn.data.models.calendarizeSimpleCycle
import com.example.kpkn.data.models.suggestCalendarTrainingDays
import com.example.kpkn.data.models.toSimpleProgramSnapshot
import com.example.kpkn.data.repository.CompetitionRepository
import com.example.kpkn.data.protocols.TrainingPlanRecipe
import com.example.kpkn.data.repository.ProgramRepository
import com.example.kpkn.domain.nutrition.NutritionTrainingCalendarAdapter
import com.example.kpkn.domain.text.SpanishPlurals
import com.example.kpkn.domain.training.BlockProgressionEngine
import com.example.kpkn.domain.training.BlockTransitionEngine
import com.example.kpkn.domain.training.ProgramAutoregulationEngine
import com.example.kpkn.domain.training.ProgramDetailHelpers
import com.example.kpkn.domain.training.AppClock
import com.example.kpkn.domain.training.IdProvider
import com.example.kpkn.domain.training.SystemAppClock
import com.example.kpkn.domain.training.UuidIdProvider
import com.example.kpkn.domain.training.ProgramCalendarEngine
import com.example.kpkn.domain.training.ProgramKeyDateEngine
import com.example.kpkn.domain.training.ProgramProgressEngine
import com.example.kpkn.domain.training.RoadmapBlock
import com.example.kpkn.domain.training.RoadmapLoopMarker
import com.example.kpkn.domain.exercises.normalizedSessionStructures
import com.example.kpkn.domain.training.SplitApplicationEngine
import com.example.kpkn.domain.training.StartDaySessionMode
import com.example.kpkn.domain.training.StartDayTemporalScope
import com.example.kpkn.domain.training.TrainingMaxMerge
import com.example.kpkn.domain.training.VolumeCalculator
import com.example.kpkn.domain.training.WeekAdherence
import com.example.kpkn.domain.training.WeekWithMeta
import android.content.Context
import android.util.Log
import kotlin.math.roundToInt
import com.example.kpkn.data.repository.AugeRepository
import com.example.kpkn.data.models.PostSessionFeedback
import com.example.kpkn.data.models.MuscleRole
import com.example.kpkn.data.models.VolumeRecommendation
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withContext
import java.util.UUID
import java.time.LocalDate
import java.time.format.DateTimeFormatter
import java.time.format.DateTimeParseException
import java.util.Locale
import kotlin.math.ceil
import kotlin.math.floor

enum class StructureSubTab { SEMANA, MACROCICLO, VOLUMEN }

enum class VolumeAdjustmentResult { SUCCESS, REQUIRES_CALIBRATION, NO_WEEK_SELECTED, NO_ADJUSTABLE_VOLUME }

data class ProgramDetailUiState(
    val structureSubTab: StructureSubTab = StructureSubTab.SEMANA,
    val selectedBlockId: String? = null,
    val selectedWeekId: String? = null,
    val macrocycleRoadmapExpanded: Boolean? = null,
    val macrocycleKeyDatesSheetOpen: Boolean? = null,
    val macrocycleLibrarySheetOpen: Boolean? = null,
    val macrocycleLoopsSheetOpen: Boolean? = null,
    val macrocycleTimelineStartDate: String? = null,
    val macrocycleManualEndDate: String? = null,
    val macrocycleCompetitionDate: String? = null,
    val snackbarMessage: String? = null,
    val pendingOpenProgramId: String? = null,
)

/** Banner de transición de bloque (evento → StateFlow; sin lógica inline en Compose). */
data class BlockTransitionBanner(
    val kind: BlockTransitionEngine.DecisionKind,
    val message: String,
    val nextBlockId: String? = null,
    val requiresExplicitConfirmation: Boolean = false,
    val pendingType: PendingProgramActionType? = null,
)

data class WeekCopyConflict(
    val weekId: String,
    val weekName: String,
    val dayLabels: List<String>,
)

data class MuscleOvertrainingStatus(
    val muscleName: String,
    val isOvertrained: Boolean,
    val isOverreaching: Boolean,
    val activeFactorsCount: Int,
    val explanation: String,
)

/**
 * Resultado de reconstruir semanas del plan con la evidencia real de entrenamiento: el programa
 * resultante, cuántas semanas se recalcularon y cuántas se dejaron intactas por estar entrenadas.
 * [failed]: no se pudo reconstruir (p. ej. sin metadatos del catálogo); [program] es el de entrada.
 */
internal data class Rematerialization(
    val program: Program,
    val recalculatedWeeks: Int,
    val preservedWeeks: Int,
    val failed: Boolean = false,
)

class ProgramDetailViewModel(
    private val programId: String,
    private val idProvider: IdProvider = UuidIdProvider,
    private val appClock: AppClock = SystemAppClock,
) : ViewModel() {

    private val repository = ProgramRepository.getInstance()

    // D2.9: solo lectura hacia fuera; únicamente este ViewModel escribe `_feedbacks`.
    private val _feedbacks = MutableStateFlow<List<PostSessionFeedback>>(emptyList())
    val feedbacks: StateFlow<List<PostSessionFeedback>> = _feedbacks.asStateFlow()

    // ─── UI State ─────────────────────────────────────────────────────────

    private val _uiState = MutableStateFlow(ProgramDetailUiState())
    val uiState: StateFlow<ProgramDetailUiState> = _uiState

    private val _blockTransitionBanner = MutableStateFlow<BlockTransitionBanner?>(null)
    val blockTransitionBanner: StateFlow<BlockTransitionBanner?> = _blockTransitionBanner

    private val _programSnapshots = MutableStateFlow<List<com.example.kpkn.domain.training.ProgramSnapshot>>(emptyList())
    val programSnapshots: StateFlow<List<com.example.kpkn.domain.training.ProgramSnapshot>> = _programSnapshots

    private var snapshotStore: com.example.kpkn.domain.training.ProgramSnapshotStore? = null

    // Serializa lecturas (`list`) y escrituras (`push`) del almacén de copias, ahora fuera de
    // Main: conserva el orden de las llamadas y evita que una lectura vieja pise a un `push`.
    private val snapshotStoreLock = Mutex()

    fun attachSnapshotStore(store: com.example.kpkn.domain.training.ProgramSnapshotStore) {
        snapshotStore = store
        refreshProgramSnapshots()
    }

    // ─── Raw Data from Repository ─────────────────────────────────────────

    val program: StateFlow<Program?> = combine(
        repository.programs.map { programs -> programs.find { it.id == programId } },
        feedbacks
    ) { p, fbs ->
        if (p == null) return@combine null
        // D3: los programas históricos pueden traer espejo suelto+grupo; se
        // normaliza por-id en lectura para portada/estructura/editor previo.
        val normalized = p.normalizedSessionStructures()
        if (fbs.isEmpty()) return@combine normalized

        val scaledRecommendations = normalized.volumeRecommendations.map { rec ->
            val adj = VolumeCalculator.calculateVolumeAdjustment(rec.muscleGroup, fbs)
            if (adj == 1.0) rec
            else rec.copy(
                minEffectiveVolume = (rec.minEffectiveVolume * adj).roundToInt(),
                maxAdaptiveVolume = (rec.maxAdaptiveVolume * adj).roundToInt(),
                maxRecoverableVolume = (rec.maxRecoverableVolume * adj).roundToInt()
            )
        }
        normalized.copy(volumeRecommendations = scaledRecommendations)
    }
        .distinctUntilChanged()
        .stateIn(viewModelScope, SharingStarted.Lazily, repository.getProgramById(programId))

    val activeProgramState: StateFlow<ActiveProgramState?> = repository.activeProgramState

    val history: StateFlow<List<WorkoutLog>> = repository.history

    // ─── Derived State ────────────────────────────────────────────────────

    val isSimpleProgram: StateFlow<Boolean> = program
        .map { p -> p?.let { ProgramDetailHelpers.isSimpleProgram(it) } ?: true }
        .distinctUntilChanged()
        .stateIn(viewModelScope, SharingStarted.Lazily, true)

    val roadmapBlocks: StateFlow<List<RoadmapBlock>> = program
        .map { p -> p?.let { ProgramDetailHelpers.buildRoadmapBlocks(it) } ?: emptyList() }
        .distinctUntilChanged()
        .stateIn(viewModelScope, SharingStarted.Lazily, emptyList())

    val simpleRoadmapLoopMarkers: StateFlow<List<RoadmapLoopMarker>> = program
        .map { p -> p?.let { ProgramDetailHelpers.buildSimpleRoadmapLoopMarkers(it) } ?: emptyList() }
        .distinctUntilChanged()
        .stateIn(viewModelScope, SharingStarted.Lazily, emptyList())

    val activeBlockId: StateFlow<String?> = combine(activeProgramState, roadmapBlocks) { active, blocks ->
        ProgramDetailHelpers.findActiveBlockId(active, programId, blocks)
    }
        .distinctUntilChanged()
        .stateIn(viewModelScope, SharingStarted.Lazily, null)

    val currentWeeks: StateFlow<List<WeekWithMeta>> = combine(_uiState, roadmapBlocks, program) { state, blocks, p ->
        if (p == null) emptyList()
        else ProgramDetailHelpers.getWeeksForBlock(state.selectedBlockId, blocks, p)
    }
        .distinctUntilChanged()
        .stateIn(viewModelScope, SharingStarted.Lazily, emptyList())

    val displayedSessions: StateFlow<List<Session>> = combine(_uiState, currentWeeks) { state, weeks ->
        ProgramDetailHelpers.getDisplayedSessions(state.selectedWeekId, weeks)
    }
        .distinctUntilChanged()
        .stateIn(viewModelScope, SharingStarted.Lazily, emptyList())

    val selectedWeekMeta: StateFlow<WeekWithMeta?> = combine(_uiState, currentWeeks) { state, weeks ->
        weeks.find { it.id == state.selectedWeekId }
    }
        .distinctUntilChanged()
        .stateIn(viewModelScope, SharingStarted.Lazily, null)

    val programLogs: StateFlow<List<WorkoutLog>> = history
        .map { h -> ProgramDetailHelpers.computeProgramLogs(h, programId) }
        .distinctUntilChanged()
        .stateIn(viewModelScope, SharingStarted.Lazily, emptyList())

    /**
     * Sesiones cuya prescripción NO se puede restaurar desde el plan (§14.5):
     * las ya registradas y la que está en curso, según la MISMA evidencia real
     * que usa [restoreManualSessionFromPlan]. La UI oculta «Restaurar esta sesión
     * desde el plan» para ellas (el badge «Sesión personalizada» se conserva).
     */
    val restoreBlockedSessionIds: StateFlow<Set<String>> = combine(
        program,
        repository.history,
        repository.ongoingWorkout,
    ) { p, _, _ ->
        p?.let { repository.executedTrainingEvidence(it).sessionIds }.orEmpty()
    }
        .distinctUntilChanged()
        .stateIn(
            viewModelScope,
            SharingStarted.Lazily,
            repository.getProgramById(programId)
                ?.let { repository.executedTrainingEvidence(it).sessionIds }
                .orEmpty(),
        )

    /**
     * H-UI: tarjeta de progresión por ejercicio (propuestas por revisar y avisos recientes) ya con
     * el nombre del ejercicio y texto llano. Se recalcula cada vez que cambia el programa; los
     * avisos con más de una semana dejan de mostrarse.
     */
    val nativeProgressionCard: StateFlow<NativeProgressionCardUi> = program
        .map { p -> buildNativeProgressionCard(p) }
        .distinctUntilChanged()
        .stateIn(
            viewModelScope,
            SharingStarted.Lazily,
            buildNativeProgressionCard(repository.getProgramById(programId)),
        )

    private fun buildNativeProgressionCard(p: Program?): NativeProgressionCardUi =
        p?.let {
            NativeProgressionCardModel.build(
                program = it,
                nowMs = appClock.now().toEpochMilli(),
                displayNameOf = { id -> catalogConfigurationDisplayName(id) },
            )
        } ?: NativeProgressionCardUi()

    fun loadFeedbacks(context: Context) {
        viewModelScope.launch {
            try {
                val list = AugeRepository.getInstance(context).getPostSessionFeedbacks()
                _feedbacks.value = list
            } catch (cancelled: CancellationException) {
                throw cancelled
            } catch (error: Exception) {
                // Sin estos comentarios el volumen no se ajusta por recuperación local; no es
                // motivo para interrumpir la pantalla, pero el fallo queda registrado.
                Log.w(LOG_TAG, "No se pudieron cargar los comentarios posteriores a la sesión.", error)
            }
        }
    }

    fun estimatedTmProfile(): com.example.kpkn.data.models.PowerliftingProfile? {
        val fromLogs = ProgramAutoregulationEngine.collectE1rmFromHistory(history.value)
        val fromPrograms = listOfNotNull(program.value?.powerliftingProfile)
        fun best(
            slot: com.example.kpkn.data.protocols.LiftSlot,
            fromProfile: (com.example.kpkn.data.models.PowerliftingProfile) -> Double?,
        ): Double? {
            val logged = fromLogs[slot]
            val profileMax = fromPrograms.mapNotNull(fromProfile).maxOrNull()
            return listOfNotNull(logged, profileMax).maxOrNull()?.takeIf { it > 0.0 }
        }
        val squat = best(com.example.kpkn.data.protocols.LiftSlot.SQUAT) { it.squat1RM ?: it.squatE1RM }
        val bench = best(com.example.kpkn.data.protocols.LiftSlot.BENCH) { it.bench1RM ?: it.benchE1RM }
        val deadlift = best(com.example.kpkn.data.protocols.LiftSlot.DEADLIFT) { it.deadlift1RM ?: it.deadliftE1RM }
        val overhead = best(com.example.kpkn.data.protocols.LiftSlot.OVERHEAD) { it.overhead1RM ?: it.overheadE1RM }
        if (squat == null && bench == null && deadlift == null && overhead == null) return null
        return com.example.kpkn.data.models.PowerliftingProfile(
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

    val muscleCdbsStatus: StateFlow<Map<String, MuscleOvertrainingStatus>> = combine(
        program,
        programLogs,
        feedbacks
    ) { p, logs, fbs ->
        if (p == null) return@combine emptyMap()

        val statusMap = mutableMapOf<String, MuscleOvertrainingStatus>()
        val exerciseList = catalogExerciseIndex().values.toList()

        val completedVolumes = VolumeCalculator.calculateCompletedWeeklyMuscleVolume(
            logs = logs,
            exerciseList = exerciseList,
            aliases = catalogSearchRedirects(),
            weeksCount = p.volumeRecommendations.firstOrNull()?.let {
                (logs.size / 3).coerceAtLeast(1)
            } ?: 1
        )

        p.volumeRecommendations.forEach { rec ->
            val muscle = rec.muscleGroup
            val canonical = VolumeCalculator.normalizeCanonicalMuscleGroup(muscle)
            val mrv = rec.maxRecoverableVolume

            // 1. Factor 1: Volumen Real > MRV
            val completedSets = completedVolumes.find { it.muscleName == canonical }?.weeklySets ?: 0.0
            val factorVol = completedSets > mrv

            // 2. Factor 2: Rendimiento Estancado (progression stagnationRisk)
            var factorProg = false

            // 3. Factor 3: Molestias / Dolor
            val normalizedMuscleLower = canonical.lowercase()
            val factorPain = logs.take(5).any { log ->
                log.discomforts.any { d ->
                    val dl = d.lowercase()
                    dl.contains(normalizedMuscleLower) ||
                    (normalizedMuscleLower.contains("hombro") && dl.contains("deltoid")) ||
                    (normalizedMuscleLower.contains("cuádriceps") && dl.contains("rodilla")) ||
                    (normalizedMuscleLower.contains("espalda baja") && dl.contains("lumbar"))
                }
            }

            // 4. Factor 4: Baterías AUGE sistémicas bajas (< 40%)
            val factorSystemic = logs.firstOrNull()?.fatigueLevel?.let { it >= 8 } ?: false

            // 5. Factor 5: Percepción local post-sesión baja (DOMS >= 3.5 y Fuerza <= 5)
            val muscleLogs = fbs.filter { fb ->
                fb.muscleFeedback.keys.any { key ->
                    VolumeCalculator.normalizeCanonicalMuscleGroup(key).lowercase() == normalizedMuscleLower
                }
            }.take(3)

            var totalDoms = 0.0
            var totalStr = 0.0
            var fbCount = 0
            muscleLogs.forEach { fb ->
                val entryKey = fb.muscleFeedback.keys.find { key ->
                    VolumeCalculator.normalizeCanonicalMuscleGroup(key).lowercase() == normalizedMuscleLower
                } ?: return@forEach
                val entry = fb.muscleFeedback[entryKey] ?: return@forEach
                totalDoms += entry.doms.toDouble()
                totalStr += entry.strengthCapacity.toDouble()
                fbCount++
            }
            val factorLocal = if (fbCount > 0) {
                (totalDoms / fbCount) >= 3.5 || (totalStr / fbCount) <= 5.0
            } else {
                false
            }

            val primaryExercises = exerciseList.filter { db ->
                db.involvedMuscles.any {
                    it.role == MuscleRole.PRIMARY &&
                    VolumeCalculator.normalizeCanonicalMuscleGroup(it.muscle).lowercase() == normalizedMuscleLower
                }
            }.map { it.id.lowercase() }

            // Check if weight is falling for the SAME exercise
            var hasWeightDrop = false
            val exercisesWithLogs = logs.flatMap { it.completedExercises }
                .filter { it.exerciseDbId?.lowercase() in primaryExercises }
                .groupBy { it.exerciseDbId?.lowercase() }

            for ((exId, exLogs) in exercisesWithLogs) {
                if (exLogs.size >= 2) {
                    val recentWeight = exLogs.first().sets.firstOrNull { !it.skipped }?.weight ?: 0.0
                    val olderWeight = exLogs.last().sets.firstOrNull { !it.skipped }?.weight ?: 0.0
                    if (recentWeight < olderWeight && recentWeight > 0.0) {
                        hasWeightDrop = true
                        break
                    }
                }
            }
            if (hasWeightDrop) {
                factorProg = true
            }

            var activeCount = 0
            val factorsList = mutableListOf<String>()
            if (factorVol) { activeCount++; factorsList.add("Volumen real excede MRV") }
            if (factorPain) { activeCount++; factorsList.add("Dolores o molestias") }
            if (factorSystemic) { activeCount++; factorsList.add("Alta fatiga sistémica") }
            if (factorLocal) { activeCount++; factorsList.add("Baja recuperación local") }
            if (factorProg) { activeCount++; factorsList.add("Pérdida de fuerza") }

            val isOvertrained = activeCount >= 3
            val isOverreaching = factorVol && activeCount < 3

            val explanation = when {
                isOvertrained -> "Posible exceso de volumen: ${factorsList.joinToString(", ")}."
                isOverreaching -> "Sobreachance Funcional: Volumen alto pero buena tolerancia sistémica."
                else -> "Óptimo o acumulando volumen."
            }

            statusMap[canonical] = MuscleOvertrainingStatus(
                muscleName = canonical,
                isOvertrained = isOvertrained,
                isOverreaching = isOverreaching,
                activeFactorsCount = activeCount,
                explanation = explanation
            )
        }

        statusMap
    }
        .stateIn(viewModelScope, SharingStarted.Lazily, emptyMap())

    val totalAdherence: StateFlow<Int> = combine(programLogs, program) { logs, p ->
        if (p == null) 0 else ProgramDetailHelpers.computeTotalAdherence(logs, p)
    }
        .distinctUntilChanged()
        .stateIn(viewModelScope, SharingStarted.Lazily, 0)

    val weeklyAdherence: StateFlow<List<WeekAdherence>> = combine(currentWeeks, programLogs) { weeks, logs ->
        ProgramDetailHelpers.computeWeeklyAdherence(weeks, logs)
    }
        .distinctUntilChanged()
        .stateIn(viewModelScope, SharingStarted.Lazily, emptyList())

    val totalWeeks: StateFlow<Int> = program
        .map { p -> p?.let { ProgramDetailHelpers.getTotalWeeks(it) } ?: 0 }
        .distinctUntilChanged()
        .stateIn(viewModelScope, SharingStarted.Lazily, 0)

    val currentWeekIndex: StateFlow<Int> = combine(activeProgramState, program) { state, p ->
        if (p == null) 0 else ProgramDetailHelpers.computeCurrentWeekIndex(state, p)
    }
        .distinctUntilChanged()
        .stateIn(viewModelScope, SharingStarted.Lazily, 0)

    val programDiscomforts: StateFlow<List<com.example.kpkn.domain.training.DiscomfortEntry>> =
        history
            .map { h -> ProgramDetailHelpers.computeProgramDiscomforts(h, programId) }
            .distinctUntilChanged()
            .stateIn(viewModelScope, SharingStarted.Lazily, emptyList())

    val exerciseDiscomfortAssociations: StateFlow<List<com.example.kpkn.domain.training.ExerciseDiscomfortAssociationEntry>> =
        history
            .map { h -> ProgramDetailHelpers.computeExerciseDiscomfortAssociations(h, programId) }
            .distinctUntilChanged()
            .stateIn(viewModelScope, SharingStarted.Lazily, emptyList())

    val isActiveProgram: StateFlow<Boolean> = activeProgramState
        .map { state -> state?.programId == programId && state.status == ProgramStatus.ACTIVE }
        .distinctUntilChanged()
        .stateIn(viewModelScope, SharingStarted.Lazily, false)

    val isPausedProgram: StateFlow<Boolean> = activeProgramState
        .map { state -> state?.programId == programId && state.status == ProgramStatus.PAUSED }
        .distinctUntilChanged()
        .stateIn(viewModelScope, SharingStarted.Lazily, false)

    // ─── Init: Auto-select + Tour ─────────────────────────────────────────

    init {
        // Auto-select active/first block when activeProgramState, program, or roadmapBlocks changes
        viewModelScope.launch {
            combine(activeProgramState, program, roadmapBlocks) { active, p, blocks ->
                if (p == null || blocks.isEmpty()) return@combine
                val currentId = _uiState.value.selectedBlockId
                val currentMissing = currentId == null || blocks.none { it.id == currentId }
                if (active != null && active.programId == programId && active.status == ProgramStatus.ACTIVE) {
                    val activeBlock = ProgramDetailHelpers.findActiveBlockId(active, programId, blocks)
                    when {
                        activeBlock != null && currentId != activeBlock ->
                            _uiState.update { it.copy(selectedBlockId = activeBlock) }
                        activeBlock == null && currentMissing ->
                            _uiState.update { it.copy(selectedBlockId = blocks.first().id) }
                    }
                } else if (currentMissing) {
                    _uiState.update { it.copy(selectedBlockId = blocks.first().id) }
                }
            }.collect {}
        }

        // Auto-select active/first week when activeProgramState, program, or currentWeeks changes
        viewModelScope.launch {
            combine(activeProgramState, program, currentWeeks) { active, p, weeks ->
                if (p == null || weeks.isEmpty()) return@combine
                if (active != null && active.programId == programId && active.status == ProgramStatus.ACTIVE) {
                    val matchedWeekId = resolveActiveWeekSelection(active, weeks.map { it.id })
                    if (matchedWeekId != null) {
                        if (_uiState.value.selectedWeekId != matchedWeekId) {
                            _uiState.update { it.copy(selectedWeekId = matchedWeekId) }
                        }
                        return@combine
                    }
                }
                val current = _uiState.value.selectedWeekId
                if (current == null || weeks.none { it.id == current }) {
                    _uiState.update { it.copy(selectedWeekId = weeks.first().id) }
                }
            }.collect {}
        }

        // A realization block is a hard gate persisted in ProgramRunState.  It
        // survives process death and is rendered again only when a new pending
        // action arrives; dismissing the banner never advances the program.
        viewModelScope.launch {
            program
                .map { it?.runState?.pendingAction }
                .distinctUntilChanged()
                .collect { action ->
                    if (action != null) {
                        _blockTransitionBanner.value = BlockTransitionBanner(
                            kind = when (action.type) {
                                PendingProgramActionType.CONFIRM_DELOAD -> BlockTransitionEngine.DecisionKind.INSERT_DELOAD
                                PendingProgramActionType.CONFIRM_1RM_TEST -> BlockTransitionEngine.DecisionKind.PROPOSE_1RM_TEST
                                PendingProgramActionType.CONFIRM_AUTOREGULATION -> BlockTransitionEngine.DecisionKind.HOLD_INCOMPLETE
                            },
                            message = action.message,
                            nextBlockId = action.nextBlockId,
                            requiresExplicitConfirmation = true,
                            pendingType = action.type,
                        )
                    }
                }
        }
    }

    // ─── Actions ──────────────────────────────────────────────────────────

    fun setStructureSubTab(tab: StructureSubTab) {
        _uiState.update { it.copy(structureSubTab = tab) }
    }

    fun setMacrocycleRoadmapExpanded(expanded: Boolean) {
        _uiState.update { it.copy(macrocycleRoadmapExpanded = expanded) }
    }

    fun setMacrocycleKeyDatesSheetOpen(open: Boolean) {
        _uiState.update { it.copy(macrocycleKeyDatesSheetOpen = open) }
    }

    fun setMacrocycleLibrarySheetOpen(open: Boolean) {
        _uiState.update { it.copy(macrocycleLibrarySheetOpen = open) }
    }

    fun setMacrocycleLoopsSheetOpen(open: Boolean) {
        _uiState.update { it.copy(macrocycleLoopsSheetOpen = open) }
    }

    fun setMacrocycleTimelineStartDate(date: String) {
        _uiState.update { it.copy(macrocycleTimelineStartDate = date) }
    }

    fun setMacrocycleManualEndDate(date: String) {
        _uiState.update { it.copy(macrocycleManualEndDate = date) }
    }

    fun setMacrocycleCompetitionDate(date: String) {
        _uiState.update { it.copy(macrocycleCompetitionDate = date) }
    }

    fun selectBlock(blockId: String) {
        _uiState.update { it.copy(selectedBlockId = blockId, selectedWeekId = null) }
    }

    fun selectWeek(weekId: String) {
        _uiState.update { it.copy(selectedWeekId = weekId) }
    }

    fun startProgram() {
        repository.startProgram(programId)
    }

    fun pauseProgram() {
        repository.pauseProgram()
    }

    fun resumeProgram() {
        repository.resumeProgram()
    }

    fun toggleStartPause() {
        when {
            isActiveProgram.value -> pauseProgram()
            isPausedProgram.value -> resumeProgram()
            else -> startProgram()
        }
    }

    fun activateOrResume() {
        if (isPausedProgram.value) resumeProgram() else if (!isActiveProgram.value) startProgram()
    }

    fun consumeSnackbarMessage() {
        _uiState.update { it.copy(snackbarMessage = null) }
    }

    fun consumePendingOpenProgram() {
        _uiState.update { it.copy(pendingOpenProgramId = null) }
    }

    fun updateProgram(updated: Program) {
        repository.updateProgram(updated)
    }

    /**
     * Confirma/desconfirma UNA sesión opcional en UNA fecha (clave exacta
     * día+sesión; nunca «todas las del día» ni un bloque, y variante A si no se
     * eligió). Sólo la UI del calendario llama aquí: NO escribe WorkoutLog ni
     * crea sesiones/fake logs.
     *
     * Contrato durable: se lanza en `viewModelScope` y la transformación se
     * aplica SOBRE EL ÚLTIMO programa dentro de
     * [ProgramRepository.mutateProgramNow] (así dos toggles rápidos de
     * instancias distintas no se pisan), validando fecha futura y que la
     * sesión OPCIONAL ocurra realmente esa fecha (misma proyección del
     * calendario que el gasto previsto). El mensaje de éxito sólo se anuncia
     * tras `true` (Room ya persistió); si algo falla, el estado se conserva y se
     * comunica el error. Al desconfirmar se retira ÚNICAMENTE esa entrada.
     */
    fun toggleOptionalSessionConfirmation(dateIso: String, sessionId: String) {
        val day = runCatching { LocalDate.parse(dateIso) }.getOrNull()
        if (day == null || !day.isAfter(LocalDate.now())) {
            _uiState.update { it.copy(snackbarMessage = "Sólo se confirman sesiones de fechas futuras.") }
            return
        }
        viewModelScope.launch {
            var toggledToConfirmed: Boolean? = null
            val outcome = runCatching {
                repository.mutateProgramNow(programId) { current ->
                    // Ocurrencia REAL: misma proyección/regla de fecha que el
                    // gasto previsto; si no coincide, se aborta sin escribir.
                    if (!NutritionTrainingCalendarAdapter.optionalOccurrenceOf(current, day, sessionId)) {
                        return@mutateProgramNow null
                    }
                    val confirmed = current.optionalSessionConfirmations.any {
                        it.dayIso == dateIso && it.sessionId == sessionId
                    }
                    toggledToConfirmed = !confirmed
                    // Sólo ESA entrada cambia; el resto de confirmaciones y
                    // campos del programa se conservan intactos.
                    val others = current.optionalSessionConfirmations.filterNot {
                        it.dayIso == dateIso && it.sessionId == sessionId
                    }
                    current.copy(
                        optionalSessionConfirmations = if (confirmed) {
                            others
                        } else {
                            others + OptionalSessionConfirmation(dayIso = dateIso, sessionId = sessionId)
                        },
                    )
                }
            }
            val error = outcome.exceptionOrNull()
            if (error is CancellationException) throw error
            // Éxito anunciado SÓLO tras el `true` del repositorio; nunca un
            // mensaje optimista y nunca sobre una copia rezagada.
            val message = when {
                error != null -> "No se pudo actualizar la sesión opcional."
                outcome.getOrDefault(false) && toggledToConfirmed == true ->
                    "Sesión opcional confirmada · $dateIso"
                outcome.getOrDefault(false) && toggledToConfirmed == false ->
                    "Confirmación retirada · $dateIso"
                toggledToConfirmed != null ->
                    "El programa cambió mientras se actualizaba. Revisa e inténtalo de nuevo."
                repository.getProgramById(programId) == null ->
                    "No se pudo actualizar: el programa ya no existe."
                else -> "No se pudo actualizar: esa sesión no es opcional en esa fecha."
            }
            _uiState.update { it.copy(snackbarMessage = message) }
        }
    }

    private var templateApplyJob: kotlinx.coroutines.Job? = null

    fun applyProgramTemplate(
        template: com.example.kpkn.data.programs.ProgramTemplateOption,
        overwrite: Boolean = false,
    ) {
        // D2.3: guarda de reentrada. Un segundo toque mientras se prepara el reemplazo no duplica
        // copias ni lanza otro reemplazo. Se comparte con el reemplazo por protocolo porque
        // ambos reescriben el mismo programa.
        if (templateApplyJob?.isActive == true || protocolApplyJob?.isActive == true) return
        templateApplyJob = viewModelScope.launch {
            val current = program.value ?: return@launch
            val result = runCatching {
                // D2.8: igual que con protocolo, no se reemplaza un programa con una sesión en curso.
                check(!overwrite || repository.ongoingWorkout.value?.programId != current.id) {
                    SESSION_IN_PROGRESS_MESSAGE
                }
                val applied = withContext(kotlinx.coroutines.Dispatchers.Default) {
                    com.example.kpkn.domain.training.ProgramTemplateEngine.applyTemplate(
                        current = current,
                        template = template,
                        forceReplace = overwrite,
                        exerciseList = com.example.kpkn.data.exercises.exerciseCatalogSnapshot(),
                    )
                }
                if (overwrite) {
                    // D2.3: la copia recuperable se guarda DESPUÉS de calcular la plantilla y
                    // dentro de este runCatching. Sin almacén conectado, o si el guardado falla
                    // (disco lleno), no se reemplaza nada: el programa queda intacto y el aviso
                    // sale por el snackbar de siempre en vez de cerrar la app.
                    pushProgramSnapshot(current, "Antes de \"${template.name}\"", required = true)
                    // La sesión pudo empezar mientras se calculaba la plantilla. Esta revisión y la
                    // escritura de abajo ocurren sin suspender en el hilo principal.
                    check(repository.ongoingWorkout.value?.programId != current.id) {
                        SESSION_IN_PROGRESS_MESSAGE
                    }
                }
                applied
            }
            result.fold(
                onSuccess = { applied ->
                    if (applied.createdCopy) {
                        addProgramCopy(applied.program)
                    } else {
                        updateProgram(applied.program)
                        selectFirstRoadmapPosition(applied.program)
                    }
                    refreshProgramSnapshots(applied.program.id)
                    _uiState.update {
                        it.copy(
                            snackbarMessage = if (applied.createdCopy) {
                                "Se creó una copia con la plantilla \"${template.name}\"."
                            } else {
                                "Plantilla \"${template.name}\" aplicada. Semanas rellenadas."
                            },
                            pendingOpenProgramId = applied.program.id.takeIf { applied.createdCopy },
                        )
                    }
                },
                onFailure = { error ->
                    if (error is CancellationException) throw error
                    _uiState.update {
                        it.copy(
                            snackbarMessage = error.message?.takeIf { msg -> msg.isNotBlank() }
                                ?: "No se pudo aplicar la plantilla. Intenta de nuevo.",
                        )
                    }
                },
            )
        }
    }

    fun clearCompetitionKeyDate() {
        val current = program.value ?: return
        val competition = ProgramKeyDateEngine.competitionKeyDate(current) ?: return
        val updated = ProgramKeyDateEngine.deleteKeyDate(
            program = current,
            keyDateId = competition.id,
            mode = ProgramKeyDateEngine.KeyDateDeleteMode.UNLINK_SESSION,
            competitionRepository = com.example.kpkn.data.repository.CompetitionRepository.getInstance(),
        )
        val cleared = if (updated.calendarization?.activatedByCompetition == true) {
            updated.copy(
                calendarization = updated.calendarization.copy(activatedByCompetition = false),
            )
        } else {
            updated
        }
        updateProgram(cleared)
    }

    fun addProgramCopy(copy: Program) {
        repository.addProgram(ProgramCalendarEngine.materializeWeekDates(copy))
    }

    private var protocolApplyJob: kotlinx.coroutines.Job? = null

    fun applyProtocolOverwrite(
        protocol: com.example.kpkn.data.protocols.Protocol,
        overwrite: Boolean = true,
    ) {
        if (protocolApplyJob?.isActive == true || templateApplyJob?.isActive == true) return
        protocolApplyJob = viewModelScope.launch {
            val current = program.value ?: return@launch
            val result = runCatching {
                check(!overwrite || repository.ongoingWorkout.value?.programId != current.id) {
                    SESSION_IN_PROGRESS_MESSAGE
                }
                val applied = withContext(kotlinx.coroutines.Dispatchers.Default) {
                    val base = if (overwrite) current else current.copy(
                        id = idProvider.newId(),
                        name = "${current.name} · ${protocol.name}",
                        isDraft = true,
                    )
                    com.example.kpkn.domain.training.ProgramProtocolEngine.applyProtocol(
                        base, protocol, exerciseList = com.example.kpkn.data.exercises.exerciseCatalogSnapshot(),
                    )
                }
                if (overwrite) {
                    check(repository.getProgramById(current.id) == current) {
                        "El programa cambió mientras se preparaba el plan. Revisa los cambios y vuelve a aplicar."
                    }
                    withContext(kotlinx.coroutines.Dispatchers.IO) {
                        pushProgramSnapshot(current, "Antes de \"${protocol.name}\"")
                    }
                    repository.replaceProgramSafely(applied)
                } else {
                    addProgramCopy(applied)
                    repository.flushPendingWrites()
                }
                repository.getProgramById(applied.id) ?: applied
            }
            result.fold(
                onSuccess = { applied ->
                    if (overwrite) {
                        selectFirstRoadmapPosition(applied)
                        refreshProgramSnapshots(applied.id)
                    }
                    _uiState.update {
                        it.copy(
                            snackbarMessage = if (overwrite) {
                                "Plan \"${protocol.name}\" aplicado. Se reemplazó el programa sin crear una copia."
                            } else {
                                "Se creó una copia con el plan \"${protocol.name}\"."
                            },
                            pendingOpenProgramId = if (overwrite) null else applied.id,
                        )
                    }
                },
                onFailure = { error ->
                    if (error is kotlinx.coroutines.CancellationException) throw error
                    _uiState.update {
                        it.copy(
                            snackbarMessage = error.message?.takeIf(String::isNotBlank)
                                ?: "No se pudo aplicar el plan. Intenta de nuevo.",
                        )
                    }
                },
            )
        }
    }

    fun refreshProgramSnapshots(targetProgramId: String? = null) {
        val store = snapshotStore ?: return
        val target = targetProgramId ?: programId
        // SharedPreferences (carga del archivo + JSON) fuera de Main. El lock se toma antes
        // de cualquier suspensión, así que las lecturas/escrituras respetan el orden de llamada.
        viewModelScope.launch {
            snapshotStoreLock.withLock {
                _programSnapshots.value = withContext(kotlinx.coroutines.Dispatchers.IO) {
                    store.list(target)
                }
            }
        }
    }

    fun restoreProgramSnapshot(snapshotId: String) {
        viewModelScope.launch {
            val current = program.value ?: return@launch
            val store = snapshotStore ?: return@launch
            val snapshot = withContext(kotlinx.coroutines.Dispatchers.IO) {
                store.restore(current.id, snapshotId)
            } ?: return@launch
            updateProgram(snapshot)
            selectFirstRoadmapPosition(snapshot)
            _uiState.update { it.copy(snackbarMessage = "Copia previa restaurada.") }
        }
    }

    private suspend fun pushProgramSnapshot(current: Program, reason: String, required: Boolean = false) {
        val store = snapshotStore
        if (store == null) {
            // Con `required`, «Reemplazar todo» no quita el plan sin dejar antes una copia para
            // recuperarlo: sin almacén conectado se aborta con el aviso de siempre (D2.3).
            check(!required) { SNAPSHOT_REQUIRED_MESSAGE }
            return
        }
        // `push` lee y luego escribe con `commit()` (disco síncrono): siempre en IO.
        snapshotStoreLock.withLock {
            _programSnapshots.value = withContext(kotlinx.coroutines.Dispatchers.IO) {
                store.push(current, reason)
            }
        }
    }

    fun markVolumeSetupPromptSeen() {
        val current = program.value ?: return
        repository.updateProgram(current.copy(volumeSetupPromptSeen = true))
    }

    fun updateStartDay(day: Int) {
        val current = program.value ?: return
        repository.updateProgram(current.copy(startDay = day))
    }

    fun updateStartDay(
        day: Int,
        temporalScope: StartDayTemporalScope,
        sessionMode: StartDaySessionMode,
    ) {
        val current = program.value ?: return
        repository.updateProgram(
            SplitApplicationEngine.applyStartDayChange(
                program = current,
                selectedWeekId = _uiState.value.selectedWeekId,
                newStartDay = day,
                temporalScope = temporalScope,
                sessionMode = sessionMode,
            )
        )
    }

    fun addWeekToSimpleProgram(sourceWeekId: String? = null, name: String? = null, description: String? = null) {
        val current = program.value ?: return
        if (!current.isSimpleTemporalProgram && current.structure != ProgramStructure.SIMPLE) return
        val copiedSessions = sourceWeekId
            ?.let { id -> findWeek(current, id)?.sessions }
            ?.let { SplitApplicationEngine.copySessionsWithNewIds(it) }
            ?: emptyList()
        val newWeek = if (
            current.simpleProgramKind == SimpleProgramKind.CALENDARIZED &&
            current.calendarization?.mode == ProgramCalendarizationMode.SIMPLE_DATED
        ) {
            val start = nextCalendarWeekStart(current)
            val trainingDays = current.suggestCalendarTrainingDays()
            ProgramWeek(
                id = idProvider.newId(),
                name = calendarWeekTitle(start),
                description = description?.trim()?.takeIf { it.isNotEmpty() },
                sessions = copiedSessions,
                startDate = start.toString(),
                endDate = start.plusDays(6).toString(),
                trainingDayDates = trainingDays.associate { dayOfWeek ->
                    val startDayIsoValue = current.startDay?.coerceIn(1, 7) ?: 1
                    val targetDayIsoValue = dayOfWeekToJava(dayOfWeek).value
                    val offset = ((targetDayIsoValue - startDayIsoValue + 7) % 7).toLong()
                    dayOfWeek to start.plusDays(offset).toString()
                },
            )
        } else {
            ProgramWeek(
                id = idProvider.newId(),
                name = name?.trim()?.takeIf { it.isNotEmpty() } ?: "Semana ${ProgramDetailHelpers.getTotalWeeks(current) + 1}",
                description = description?.trim()?.takeIf { it.isNotEmpty() },
                sessions = copiedSessions,
            )
        }

        if (current.macrocycles.isEmpty() || current.macrocycles.firstOrNull()?.blocks.isNullOrEmpty()) {
            val fallbackMeso = defaultRoadmapMesocycle(newWeek)
            val fallbackBlock = Block(
                id = idProvider.newId(),
                name = "Ciclo base",
                mesocycles = listOf(fallbackMeso),
            )
            val fallbackMacro = com.example.kpkn.data.models.Macrocycle(
                id = idProvider.newId(),
                name = "Macrociclo base",
                blocks = listOf(fallbackBlock),
            )
            val updated = current.copy(
                macrocycles = if (current.macrocycles.isEmpty()) {
                    listOf(fallbackMacro)
                } else {
                    current.macrocycles.mapIndexed { macroIndex, macro ->
                        if (macroIndex == 0) macro.copy(blocks = listOf(fallbackBlock)) else macro
                    }
                },
            ).normalizedTemporalStructure()

            updateProgram(updated)
            _uiState.update { it.copy(selectedBlockId = fallbackBlock.id, selectedWeekId = newWeek.id, structureSubTab = StructureSubTab.SEMANA) }
            return
        }

        val macroIndex = current.macrocycles.indexOfFirst { it.blocks.isNotEmpty() }.takeIf { it >= 0 } ?: return
        val block = current.macrocycles[macroIndex].blocks.firstOrNull() ?: return
        val mesoIndex = block.mesocycles.indexOfLast { true }.takeIf { it >= 0 }

        val updated = current.copy(
            macrocycles = current.macrocycles.mapIndexed { currentMacroIndex, macro ->
                if (currentMacroIndex != macroIndex) macro
                else macro.copy(
                    blocks = macro.blocks.mapIndexed { blockIndex, currentBlock ->
                        if (blockIndex != 0) currentBlock
                        else if (mesoIndex == null) {
                            currentBlock.copy(mesocycles = listOf(defaultRoadmapMesocycle(newWeek)))
                        } else {
                            currentBlock.copy(
                                mesocycles = currentBlock.mesocycles.mapIndexed { currentMesoIndex, meso ->
                                    if (currentMesoIndex != mesoIndex) meso
                                    else meso.copy(weeks = meso.weeks + newWeek)
                                }
                            )
                        }
                    }
                )
            }
        ).normalizedTemporalStructure()

        updateProgram(updated)
        _uiState.update { it.copy(selectedBlockId = block.id, selectedWeekId = newWeek.id) }
    }

    fun addWeekToSelectedAdvancedBlock(name: String? = null, description: String? = null) {
        val current = program.value ?: return
        if (current.isSimpleTemporalProgram) return

        val blocks = ProgramDetailHelpers.buildRoadmapBlocks(current)
        val target = blocks.find { it.id == _uiState.value.selectedBlockId } ?: blocks.firstOrNull() ?: return
        val macro = current.macrocycles.getOrNull(target.macroIndex) ?: return
        val block = macro.blocks.getOrNull(target.blockIndex) ?: return
        val newWeek = defaultRoadmapWeek(
            name = name?.trim()?.takeIf { it.isNotEmpty() }
                ?: "Semana ${countWeeksBeforeAppendingToBlock(current, target.macroIndex, target.blockIndex) + 1}",
            description = description,
        )
        val lastMesoIndex = block.mesocycles.lastIndex

        val updated = current.copy(
            macrocycles = current.macrocycles.mapIndexed { macroIndex, currentMacro ->
                if (macroIndex != target.macroIndex) currentMacro
                else currentMacro.copy(
                    blocks = currentMacro.blocks.mapIndexed { blockIndex, currentBlock ->
                        if (blockIndex != target.blockIndex) currentBlock
                        else if (lastMesoIndex < 0) {
                            currentBlock.copy(mesocycles = listOf(defaultRoadmapMesocycle(newWeek)))
                        } else {
                            currentBlock.copy(
                                mesocycles = currentBlock.mesocycles.mapIndexed { mesoIndex, meso ->
                                    if (mesoIndex == lastMesoIndex) meso.copy(weeks = meso.weeks + newWeek) else meso
                                }
                            )
                        }
                    }
                )
            }
        ).normalizedTemporalStructure()

        updateProgram(updated)
        _uiState.update {
            it.copy(
                selectedBlockId = target.id,
                selectedWeekId = newWeek.id,
                structureSubTab = StructureSubTab.SEMANA,
            )
        }
    }

    fun addAdvancedBlockFromRoadmap(name: String? = null, description: String? = null) {
        val current = program.value ?: return
        if (current.isSimpleTemporalProgram) return

        val blocks = ProgramDetailHelpers.buildRoadmapBlocks(current)
        val targetMacroIndex = blocks.lastOrNull()?.macroIndex ?: current.macrocycles.lastIndex.takeIf { it >= 0 } ?: return
        val newWeek = defaultRoadmapWeek("Semana ${ProgramDetailHelpers.getTotalWeeks(current) + 1}")
        val newBlock = defaultRoadmapBlock(
            name = name?.trim()?.takeIf { it.isNotEmpty() } ?: "Bloque ${blocks.size + 1}",
            description = description,
            firstWeek = newWeek,
        )

        val updated = current.copy(
            macrocycles = current.macrocycles.mapIndexed { macroIndex, macro ->
                if (macroIndex == targetMacroIndex) macro.copy(blocks = macro.blocks + newBlock) else macro
            }
        ).normalizedTemporalStructure()

        updateProgram(updated)
        _uiState.update {
            it.copy(
                selectedBlockId = newBlock.id,
                selectedWeekId = newWeek.id,
                structureSubTab = StructureSubTab.SEMANA,
            )
        }
    }

    private fun defaultRoadmapWeek(name: String, description: String? = null): ProgramWeek {
        return ProgramWeek(
            id = idProvider.newId(),
            name = name,
            description = description?.trim()?.takeIf { it.isNotEmpty() },
        )
    }

    private fun defaultRoadmapMesocycle(firstWeek: ProgramWeek): Mesocycle {
        return Mesocycle(
            id = idProvider.newId(),
            name = "Mesociclo 1",
            goal = MesocycleGoal.ACCUMULATION,
            weeks = listOf(firstWeek),
        )
    }

    private fun defaultRoadmapBlock(name: String, description: String?, firstWeek: ProgramWeek): Block {
        return Block(
            id = idProvider.newId(),
            name = name,
            description = description?.trim()?.takeIf { it.isNotEmpty() },
            mesocycles = listOf(defaultRoadmapMesocycle(firstWeek)),
        )
    }

    private fun countWeeksBeforeAppendingToBlock(program: Program, targetMacroIndex: Int, targetBlockIndex: Int): Int {
        var count = 0
        program.macrocycles.forEachIndexed { macroIndex, macro ->
            if (macroIndex > targetMacroIndex) return count
            macro.blocks.forEachIndexed { blockIndex, block ->
                if (macroIndex == targetMacroIndex && blockIndex > targetBlockIndex) return count
                count += block.mesocycles.sumOf { it.weeks.size }
            }
        }
        return count
    }

    private fun findWeek(program: Program, weekId: String): ProgramWeek? {
        program.macrocycles.forEach { macro ->
            macro.blocks.forEach { block ->
                block.mesocycles.forEach { meso ->
                    meso.weeks.firstOrNull { it.id == weekId }?.let { return it }
                }
            }
        }
        return null
    }

    fun updateWeekMetadata(weekId: String, name: String, description: String?) {
        val current = program.value ?: return
        val normalizedDescription = description?.trim()?.takeIf { it.isNotEmpty() }
        val updated = current.copy(
            macrocycles = current.macrocycles.map { macro ->
                macro.copy(
                    blocks = macro.blocks.map { block ->
                        block.copy(
                            mesocycles = block.mesocycles.map { meso ->
                                meso.copy(
                                    weeks = meso.weeks.map { week ->
                                        if (week.id == weekId) {
                                            week.copy(
                                                name = name.trim().ifBlank { week.name },
                                                description = normalizedDescription,
                                            )
                                        } else week
                                    }
                                )
                            }
                        )
                    }
                )
            }
        )
        repository.updateProgram(updated)
    }

    fun replaceWeekSessions(weekId: String, sessions: List<Session>) {
        val current = program.value ?: return
        if (weekId.isBlank()) return
        val resolvedWeekId = resolvePersistedWeekId(current, weekId) ?: return
        val updated = current.copy(
            macrocycles = current.macrocycles.map { macro ->
                macro.copy(
                    blocks = macro.blocks.map { block ->
                        block.copy(
                            mesocycles = block.mesocycles.map { meso ->
                                meso.copy(
                                    weeks = meso.weeks.map { week ->
                                        if (week.id == resolvedWeekId) {
                                            week.copy(sessions = normalizeMainSessions(sessions))
                                        } else {
                                            week
                                        }
                                    },
                                )
                            },
                        )
                    },
                )
            },
        )
        repository.updateProgram(updated)
    }

    fun updateWeekTrainingDayDate(weekId: String, dayOfWeek: Int, isoDate: String?) {
        val current = program.value ?: return
        if (dayOfWeek !in 1..7) return
        val normalized = isoDate?.trim()?.takeIf { it.isNotBlank() }
        val parsed = normalized?.let { runCatching { java.time.LocalDate.parse(it) }.getOrNull() }
        if (normalized != null && parsed == null) return
        val updated = current.copy(
            macrocycles = current.macrocycles.map { macro ->
                macro.copy(
                    blocks = macro.blocks.map { block ->
                        block.copy(
                            mesocycles = block.mesocycles.map { meso ->
                                meso.copy(
                                    weeks = meso.weeks.map { week ->
                                        if (week.id != weekId) week
                                        else {
                                            val nextDates = week.trainingDayDates.toMutableMap()
                                            if (parsed == null) nextDates.remove(dayOfWeek)
                                            else nextDates[dayOfWeek] = parsed.toString()
                                            week.copy(trainingDayDates = nextDates)
                                        }
                                    },
                                )
                            },
                        )
                    },
                )
            },
        )
        updateProgram(updated)
    }

    fun updateBlockMetadata(
        blockId: String,
        name: String,
        description: String?,
        goal: BlockGoal? = null,
        progressionScheme: BlockProgressionScheme? = null,
    ) {
        val current = program.value ?: return
        val normalizedDescription = description?.trim()?.takeIf { it.isNotEmpty() }
        val updated = current.copy(
            macrocycles = current.macrocycles.map { macro ->
                macro.copy(
                    blocks = macro.blocks.map { block ->
                        if (block.id == blockId) {
                            val nextGoal = goal ?: block.goal
                            val nextScheme = progressionScheme ?: block.progressionScheme
                            block.copy(
                                name = name.trim().ifBlank { block.name },
                                description = normalizedDescription,
                                goal = nextGoal,
                                progressionScheme = nextScheme,
                                // Metadata edits must not pretend that the old
                                // week prescriptions are still atomic.  Keep
                                // the user's draft intact and expose an
                                // explicit materialization-needed state until
                                // the progression engine is run/confirmed.
                                materializationPending = block.materializationPending ||
                                    nextGoal != block.goal || nextScheme != block.progressionScheme,
                            )
                        } else block
                    }
                )
            }
        )
        repository.updateProgram(updated)
    }

    fun dismissBlockTransitionBanner() {
        _blockTransitionBanner.value = null
    }

    /** Accepts the persisted AUGE deload proposal and moves the cursor into it. */
    fun acceptPendingDeload() = resolvePendingDeload(accept = true)

    /** Rejects the persisted AUGE deload proposal and removes its generated block. */
    fun rejectPendingDeload() = resolvePendingDeload(accept = false)

    fun acceptAutoregulation(proposal: AutoregulationProposal? = null) {
        resolveAutoregulation(accept = true, proposal = proposal)
    }

    fun rejectAutoregulation() {
        resolveAutoregulation(accept = false)
    }

    fun acceptNativeProgressionProposal(proposalId: String) = resolveNativeProgression(proposalId, accept = true)

    fun rejectNativeProgressionProposal(proposalId: String) = resolveNativeProgression(proposalId, accept = false)

    // Propuestas que se están resolviendo: un segundo toque no repite la acción ni el aviso.
    private val nativeResolutionsInFlight = mutableSetOf<String>()

    private fun resolveNativeProgression(proposalId: String, accept: Boolean) {
        if (!nativeResolutionsInFlight.add(proposalId)) return
        viewModelScope.launch {
            try {
                val displayNameOf: (String) -> String? = { id -> catalogConfigurationDisplayName(id) }
                // La propuesta y el nombre del ejercicio se leen ANTES de resolver: al aplicarse,
                // la propuesta sale de la lista y una variante cambia el ejercicio del plan.
                val before = repository.getProgramById(programId)
                val proposal = before?.nativeProgressionProposals?.firstOrNull { it.proposalId == proposalId }
                val result = runCatching {
                    repository.resolveNativeProgressionProposalNow(programId, proposalId, accept)
                }
                val error = result.exceptionOrNull()
                if (error is CancellationException) throw error
                if (error != null || result.getOrDefault(false).not()) {
                    _uiState.update {
                        it.copy(snackbarMessage = if (accept) {
                            "No se pudo aplicar la progresión. Las sesiones y cargas registradas se conservaron."
                        } else {
                            "No se pudo rechazar la progresión. Las sesiones y cargas registradas se conservaron."
                        })
                    }
                    return@launch
                }
                // H-UI: toda respuesta se confirma con el nombre del ejercicio y lo que cambió.
                val resolution = repository.getProgramById(programId)
                    ?.nativeProgressionAudit
                    ?.lastOrNull { it.proposalId == proposalId }
                val identity = proposal?.identity ?: resolution?.identity
                val exerciseName = if (before != null && identity != null) {
                    NativeProgressionCardModel.exerciseName(before, identity, displayNameOf)
                } else {
                    "Este ejercicio"
                }
                _uiState.update {
                    it.copy(
                        snackbarMessage = NativeProgressionCardModel.confirmation(
                            accepted = accept,
                            proposal = proposal,
                            resolution = resolution,
                            exerciseName = exerciseName,
                            displayNameOf = displayNameOf,
                        ),
                    )
                }
            } finally {
                nativeResolutionsInFlight.remove(proposalId)
            }
        }
    }

    /**
     * H-UI: oculta un aviso informativo de progresión («Entendido»). El registro de
     * resoluciones se conserva intacto; solo se apaga la bandera que lo muestra en pantalla.
     */
    fun dismissNativeProgressionNotice(proposalId: String) {
        viewModelScope.launch {
            val result = runCatching {
                repository.mutateProgramNow(programId) { current ->
                    if (current.nativeProgressionAudit.none { it.proposalId == proposalId && it.userFacingNotice }) {
                        return@mutateProgramNow null
                    }
                    current.copy(
                        nativeProgressionAudit = current.nativeProgressionAudit.map { entry ->
                            if (entry.proposalId == proposalId) entry.copy(userFacingNotice = false) else entry
                        },
                    )
                }
            }
            val error = result.exceptionOrNull()
            if (error is CancellationException) throw error
            if (error != null) {
                _uiState.update { it.copy(snackbarMessage = "No se pudo ocultar el aviso. Inténtalo de nuevo.") }
            }
        }
    }

    private fun resolveAutoregulation(accept: Boolean, proposal: AutoregulationProposal? = null) {
        viewModelScope.launch {
            val result = runCatching {
                repository.resolvePendingAutoregulationNow(programId, accept = accept, only = proposal)
            }
            val error = result.exceptionOrNull()
            if (error is CancellationException) throw error
            val updated = repository.getProgramById(programId)
            if (error != null || result.getOrDefault(false).not()) {
                _uiState.update {
                    it.copy(snackbarMessage = if (accept) {
                        "No se pudo aplicar la propuesta. El plan y el historial se conservaron."
                    } else {
                        "No se pudo rechazar la propuesta. El plan y el historial se conservaron."
                    })
                }
                return@launch
            }
            if (updated?.runState?.pendingAction?.type != PendingProgramActionType.CONFIRM_AUTOREGULATION) {
                _blockTransitionBanner.value = null
            }
        }
    }

    /**
     * Restaura sólo la sesión seleccionada desde su receta, conservando las
     * sesiones vecinas como snapshots y manteniendo las IDs/historial (§14.5).
     */
    fun restoreManualSessionFromPlan(sessionId: String) {
        viewModelScope.launch {
            var blockedReason: String? = null
            val outcome = runCatching {
                repository.mutateProgramNow(programId) { current ->
                    // La evidencia real (sesiones iniciadas/registradas, del ciclo en
                    // curso en planes nativos) y la reconstrucción selectiva de UNA
                    // sesión viven en el dominio: un resultado rechazado nunca se
                    // reporta como éxito y deja la edición marcada.
                    val evidence = repository.executedTrainingEvidence(current)
                    when (
                        val result = com.example.kpkn.domain.training.PlanMaterializer.restoreSessionFromRecipe(
                            program = current,
                            sessionId = sessionId,
                            executedSessionIds = evidence.sessionIds,
                        )
                    ) {
                        is com.example.kpkn.domain.training.PlanMaterializer.SessionRestoreResult.Restored ->
                            result.program
                        is com.example.kpkn.domain.training.PlanMaterializer.SessionRestoreResult.Rejected -> {
                            blockedReason = restoreRejectionMessage(result.reason)
                            null
                        }
                    }
                }
            }
            val error = outcome.exceptionOrNull()
            if (error is CancellationException) throw error
            val message = when {
                error != null -> error.message?.takeIf(String::isNotBlank)
                    ?: "No se pudo restaurar la sesión. No se modificó el historial."
                outcome.getOrDefault(false) ->
                    "Sesión restaurada desde el plan. Se descartó su edición; las sesiones vecinas y el historial se conservaron."
                // Sin rechazo del dominio, `false` es un conflicto de escritura
                // (otra escritura más nueva ganó): nada se aplicó.
                else -> blockedReason
                    ?: "No se pudo restaurar la sesión: el plan cambió mientras se guardaba. No se modificó nada; reintenta."
            }
            _uiState.update { it.copy(snackbarMessage = message) }
        }
    }

    private fun restoreRejectionMessage(
        reason: com.example.kpkn.domain.training.PlanMaterializer.SessionRestoreRejection,
    ): String = when (reason) {
        com.example.kpkn.domain.training.PlanMaterializer.SessionRestoreRejection.NO_OVERRIDE ->
            "Esta sesión ya no tiene una edición pendiente de restaurar."
        com.example.kpkn.domain.training.PlanMaterializer.SessionRestoreRejection.NO_SOURCE_RECIPE ->
            "No se encontró la receta fuente; no se cambió la sesión."
        com.example.kpkn.domain.training.PlanMaterializer.SessionRestoreRejection.SESSION_NOT_FOUND ->
            "No se encontró la ocurrencia de esta sesión; no se cambió el plan."
        com.example.kpkn.domain.training.PlanMaterializer.SessionRestoreRejection.EXECUTED ->
            "La sesión ya se inició o tiene registros; su prescripción histórica se conserva."
        com.example.kpkn.domain.training.PlanMaterializer.SessionRestoreRejection.WEEK_NOT_IN_RECIPE ->
            "La receta ya no contiene la semana de esta sesión; no se cambió el plan."
        com.example.kpkn.domain.training.PlanMaterializer.SessionRestoreRejection.NO_RECIPE_COUNTERPART ->
            "Esta sesión no tiene una sesión equivalente en la receta (la creaste tú o cambió de día); se conserva tu edición."
    }

    fun setAutoregulationMode(mode: com.example.kpkn.data.models.AutoregulationMode) {
        val current = program.value ?: return
        if (current.autoregulationMode == mode) return
        updateProgram(current.copy(autoregulationMode = mode))
    }

    /**
     * «Guardar TM» (R-04). El asistente solo edita los 1RM y devuelve un perfil nuevo; se fusiona con el
     * que tiene el programa ([TrainingMaxMerge]: conserva las variantes y los TM ajustados de los
     * levantamientos cuyo 1RM no cambió) y, en la MISMA mutación durable, se recalculan las semanas de la
     * receta que no se han entrenado. Las sesiones iniciadas o registradas y las semanas completas se
     * conservan tal cual (evidencia real del repositorio). El aviso dice cuántas semanas se recalcularon
     * y cuántas se dejaron intactas. Sin receta solo se guarda el perfil.
     *
     * Si las semanas no se pueden reconstruir (p. ej. sin metadatos del catálogo) el TM se guarda igual
     * y los bloques de la receta quedan pendientes: el botón RE-MATERIALIZAR aplica las cargas nuevas.
     */
    fun updatePowerliftingProfile(profile: com.example.kpkn.data.models.PowerliftingProfile) {
        viewModelScope.launch {
            var rebuild: Rematerialization? = null
            val outcome = runCatching {
                repository.mutateProgramNow(programId) { current ->
                    rebuild = null
                    val merged = TrainingMaxMerge.merge(
                        old = current.powerliftingProfile,
                        new = profile,
                        trainingMaxPercent = TrainingMaxMerge.trainingMaxPercentOf(current),
                    )
                    val withProfile = current.copy(powerliftingProfile = merged)
                    val recipe = current.sourceRecipe ?: return@mutateProgramNow withProfile
                    val rebuilt = rematerializeWithEvidence(withProfile, recipe, onlyPendingBlocks = false)
                    rebuild = rebuilt
                    if (rebuilt.failed) withPendingRecipeBlocks(withProfile, recipe) else rebuilt.program
                }
            }
            val error = outcome.exceptionOrNull()
            if (error is CancellationException) throw error
            val message = if (error != null || !outcome.getOrDefault(false)) {
                "No se pudo guardar el TM. El plan y el historial se conservaron."
            } else {
                savedMessageFor(rebuild)
            }
            _uiState.update { it.copy(snackbarMessage = message) }
        }
    }

    private fun savedMessageFor(rebuild: Rematerialization?): String = when {
        rebuild == null -> "TM actualizado"
        rebuild.failed -> "TM actualizado. Las cargas nuevas se aplican al pulsar RE-MATERIALIZAR."
        else -> trainingMaxSavedMessage(rebuild.recalculatedWeeks, rebuild.preservedWeeks)
    }

    fun rematerializePending() {
        val current = program.value ?: return
        val recipe = current.sourceRecipe ?: return
        val rebuilt = rematerializeWithEvidence(current, recipe, onlyPendingBlocks = true)
        if (rebuilt.failed) {
            // Empieza por «No se pudo»: así la pantalla lo pinta como fallo (`snackbarTypeFor`).
            _uiState.update {
                it.copy(snackbarMessage = "No se pudo recalcular lo pendiente del plan. El plan y el historial se conservaron.")
            }
            return
        }
        updateProgram(rebuilt.program)
    }

    /**
     * Reconstruye con la receta las semanas de [program] que quedan por entrenar. Con
     * [onlyPendingBlocks] solo las de los bloques `materializationPending` (RE-MATERIALIZAR); sin él, las
     * de todos los bloques que vienen de [recipe] (un TM nuevo cambia las cargas de todo el plan). La
     * «Descarga (auto)» de AUGE y las semanas de loop no vienen de la receta y no se tocan.
     *
     * §14.5/AC-G1: la evidencia REAL de entrenamiento viaja con la reconstrucción (sesiones iniciadas o
     * registradas + semanas completas); jamás se pasa `emptySet()` cuando hay trabajo realizado.
     * `rematerializeWeek` conserva intactas las semanas y sesiones con evidencia y las sesiones editadas
     * a mano. Los bloques reconstruidos dejan de estar pendientes. Si la reconstrucción falla devuelve
     * [Rematerialization.failed] y el programa sin tocar.
     */
    private fun rematerializeWithEvidence(
        program: Program,
        recipe: TrainingPlanRecipe,
        onlyPendingBlocks: Boolean,
    ): Rematerialization {
        val evidence = repository.executedTrainingEvidence(program)
        val targetBlocks = program.macrocycles.flatMap { it.blocks }.filter { block ->
            if (onlyPendingBlocks) block.materializationPending else block.sourceDefinitionId == recipe.id
        }
        val targetBlockIds = targetBlocks.mapTo(mutableSetOf()) { it.id }
        // Las semanas de loop las añade el atleta (competición, descarga…): no vienen de la receta y
        // reconstruirlas por posición las sustituiría por una semana de la receta.
        val weekIds = targetBlocks.flatMap { block -> block.mesocycles.flatMap { it.weeks } }
            .filterNot { it.isLoopWeek }
            .map { it.id }
        val recalculated = weekIds.count { it !in evidence.weekIds }
        return try {
            var working = program
            weekIds.forEach { weekId ->
                working = com.example.kpkn.domain.training.PlanMaterializer.rematerializeWeek(
                    program = working,
                    weekId = weekId,
                    recipe = recipe,
                    executedWeekIds = evidence.weekIds,
                    executedSessionIds = evidence.sessionIds,
                )
            }
            working = working.copy(
                macrocycles = working.macrocycles.map { macro ->
                    macro.copy(
                        blocks = macro.blocks.map { block ->
                            if (block.id in targetBlockIds) block.copy(materializationPending = false) else block
                        },
                    )
                },
            )
            Rematerialization(working, recalculatedWeeks = recalculated, preservedWeeks = weekIds.size - recalculated)
        } catch (failure: RuntimeException) {
            if (failure is CancellationException) throw failure
            Log.w(LOG_TAG, "No se pudieron reconstruir las semanas del plan.", failure)
            Rematerialization(program, recalculatedWeeks = 0, preservedWeeks = 0, failed = true)
        }
    }

    /** Deja pendientes de materializar los bloques de [recipe]: el TM ya está guardado y RE-MATERIALIZAR lo aplica. */
    private fun withPendingRecipeBlocks(program: Program, recipe: TrainingPlanRecipe): Program =
        program.copy(
            macrocycles = program.macrocycles.map { macro ->
                macro.copy(
                    blocks = macro.blocks.map { block ->
                        if (block.sourceDefinitionId == recipe.id) block.copy(materializationPending = true) else block
                    },
                )
            },
        )

    /**
     * Registros de este programa para las puertas de bloque (descarga y test de 1RM). Se leen de la
     * fuente viva [history] y no de [programLogs]: ese flujo es perezoso y, sin nadie que lo observe,
     * su `value` sigue siendo la lista vacía del arranque. Sin registros el motor no puede detectar un
     * AMRAP corto en la ola que se cierra (el levantamiento subiría igual) ni proteger una sesión de la
     * ola nueva que ya se entrenó por adelantado.
     */
    private fun currentProgramLogs(): List<WorkoutLog> =
        ProgramDetailHelpers.computeProgramLogs(history.value, programId)

    /**
     * Barra y discos que el atleta declaró en los ajustes: el TM que sube la progresión del método se
     * redondea a lo que realmente se puede cargar (con discos de 1,25 kg, 108 + 2,5 da 110 y no 110,5).
     * El proveedor de metadatos del catálogo no se pasa: el motor usa el instalado, igual que el resto
     * de reconstrucciones de este ViewModel.
     */
    private fun currentEquipmentInventory(): EquipmentInventory =
        repository.settings.value.resolvedEquipmentInventory()

    private fun resolvePendingDeload(accept: Boolean) {
        val current = program.value ?: return
        val result = ProgramProgressEngine.resolvePendingDeload(
            program = current,
            activeState = activeProgramState.value?.takeIf { it.programId == current.id },
            accept = accept,
            logs = currentProgramLogs(),
            inventory = currentEquipmentInventory(),
        )
        if (result.program == current) return
        updateProgram(result.program)
        result.activeState?.let(repository::updateActiveProgramState)
        _blockTransitionBanner.value = null
    }

    /**
     * Records the three competition 1RMs and then advances the persisted cursor.
     *
     * R-19: el motor también lleva los 1RM al perfil de cargas y deja pendientes los bloques de la
     * receta. Aquí se recalculan con la evidencia real (lo entrenado y las sesiones editadas a mano se
     * conservan) en la misma escritura que avanza el cursor. Si no se puede reconstruir, los bloques
     * siguen pendientes y el botón RE-MATERIALIZAR aplica las cargas nuevas.
     */
    fun recordPendingOneRmTest(squat1RM: Double, bench1RM: Double, deadlift1RM: Double) {
        val current = program.value ?: return
        val result = ProgramProgressEngine.resolvePendingOneRmTest(
            program = current,
            activeState = activeProgramState.value?.takeIf { it.programId == current.id },
            resolution = com.example.kpkn.data.models.OneRmResolution(
                status = com.example.kpkn.data.models.OneRmResolutionStatus.RECORDED,
                squat1RM = squat1RM,
                bench1RM = bench1RM,
                deadlift1RM = deadlift1RM,
            ),
            logs = currentProgramLogs(),
            inventory = currentEquipmentInventory(),
        )
        if (result.program == current) return
        val recipe = result.program.sourceRecipe
        val profileChanged = result.program.powerliftingProfile != current.powerliftingProfile
        val rebuilt = if (recipe != null && profileChanged) {
            rematerializeWithEvidence(result.program, recipe, onlyPendingBlocks = false)
        } else {
            null
        }
        updateProgram(rebuilt?.takeUnless { it.failed }?.program ?: result.program)
        result.activeState?.let(repository::updateActiveProgramState)
        _blockTransitionBanner.value = null
        if (profileChanged) {
            _uiState.update { it.copy(snackbarMessage = "1RM registrado. ${savedMessageFor(rebuilt)}") }
        }
    }

    /** Explicitly declines the test while still persisting the audit decision. */
    fun skipPendingOneRmTest() {
        val current = program.value ?: return
        val result = ProgramProgressEngine.resolvePendingOneRmTest(
            program = current,
            activeState = activeProgramState.value?.takeIf { it.programId == current.id },
            resolution = com.example.kpkn.data.models.OneRmResolution(
                status = com.example.kpkn.data.models.OneRmResolutionStatus.SKIPPED,
                note = "Atleta omitió el registro de 1RM",
            ),
            logs = currentProgramLogs(),
            inventory = currentEquipmentInventory(),
        )
        if (result.program == current) return
        updateProgram(result.program)
        result.activeState?.let(repository::updateActiveProgramState)
        _blockTransitionBanner.value = null
    }

    /** Legacy entrypoint retained for integrations; records SKIPPED explicitly. */
    fun confirmPendingOneRmTestAndContinue() = skipPendingOneRmTest()

    fun blockProgressLabel(): String? {
        val p = program.value ?: return null
        if (p.structure != ProgramStructure.COMPLEX) return null
        val blocks = p.macrocycles.flatMap { it.blocks }
        if (blocks.isEmpty()) return null
        val activeId = activeBlockId.value ?: _uiState.value.selectedBlockId ?: blocks.first().id
        val index = blocks.indexOfFirst { it.id == activeId }.takeIf { it >= 0 } ?: 0
        val block = blocks[index]
        val weeks = block.mesocycles.flatMap { it.weeks }
        val weekId = _uiState.value.selectedWeekId
        val weekIdx = weeks.indexOfFirst { it.id == weekId }.takeIf { it >= 0 } ?: 0
        val goalLabel = block.goal?.label
            ?: block.mesocycles.firstOrNull()?.goal?.label
            ?: "Bloque"
        val remaining = (weeks.size - weekIdx - 1).coerceAtLeast(0)
        return "Bloque ${index + 1}/${blocks.size} · $goalLabel · quedan $remaining sem"
    }

    fun previewBlockProgression(blockId: String): String? {
        val block = program.value?.macrocycles?.flatMap { it.blocks }?.firstOrNull { it.id == blockId }
            ?: return null
        val weeks = block.mesocycles.firstOrNull()?.weeks?.size ?: return null
        if (weeks < 2) return "Bloque de 1 semana: sin cambio semanal."
        return BlockProgressionEngine.previewDiff(block, weeks - 1, weeks)?.summary
    }

    fun deleteWeekFromRoadmap(weekId: String) {
        val current = program.value ?: return
        if (ProgramDetailHelpers.getTotalWeeks(current) <= 1) return

        val updated = current.copy(
            weekSplitSelections = current.weekSplitSelections - weekId,
            macrocycles = current.macrocycles.map { macro ->
                macro.copy(
                    blocks = macro.blocks.map { block ->
                        block.copy(
                            mesocycles = block.mesocycles.map { meso ->
                                meso.copy(weeks = meso.weeks.filterNot { it.id == weekId })
                            }.filter { it.weeks.isNotEmpty() }
                        )
                    }.filter { block -> block.mesocycles.any { it.weeks.isNotEmpty() } }
                )
            }.filter { macro -> macro.blocks.isNotEmpty() }
        ).normalizedTemporalStructure()

        updateProgram(updated)
        val blocks = ProgramDetailHelpers.buildRoadmapBlocks(updated)
        val selectedBlock = blocks.firstOrNull { it.id == _uiState.value.selectedBlockId } ?: blocks.firstOrNull()
        val nextWeek = ProgramDetailHelpers.getWeeksForBlock(selectedBlock?.id, blocks, updated).firstOrNull()
        _uiState.update {
            it.copy(
                selectedBlockId = selectedBlock?.id,
                selectedWeekId = nextWeek?.id,
                structureSubTab = StructureSubTab.SEMANA,
            )
        }
    }

    fun deleteBlockFromRoadmap(blockId: String) {
        val current = program.value ?: return
        val blocksBefore = ProgramDetailHelpers.buildRoadmapBlocks(current)
        if (blocksBefore.size <= 1) return

        val removedWeekIds = SplitApplicationEngine.buildWeekOptions(current)
            .filter { it.blockId == blockId }
            .mapTo(mutableSetOf()) { it.id }

        val updated = current.copy(
            blockSplitSelections = current.blockSplitSelections - blockId,
            weekSplitSelections = current.weekSplitSelections - removedWeekIds,
            macrocycles = current.macrocycles.map { macro ->
                macro.copy(blocks = macro.blocks.filterNot { it.id == blockId })
            }.filter { it.blocks.isNotEmpty() }
        ).normalizedTemporalStructure()

        updateProgram(updated)
        val blocks = ProgramDetailHelpers.buildRoadmapBlocks(updated)
        val selectedBlock = blocks.firstOrNull()
        val nextWeek = ProgramDetailHelpers.getWeeksForBlock(selectedBlock?.id, blocks, updated).firstOrNull()
        _uiState.update {
            it.copy(
                selectedBlockId = selectedBlock?.id,
                selectedWeekId = nextWeek?.id,
                structureSubTab = StructureSubTab.SEMANA,
            )
        }
    }

    fun reduceCurrentWeekVolumeBy20Percent(): VolumeAdjustmentResult {
        return adjustCurrentWeekVolumeByFactor(0.8)
    }

    fun increaseCurrentWeekVolumeBy20Percent(): VolumeAdjustmentResult {
        return adjustCurrentWeekVolumeByFactor(1.2)
    }

    private fun adjustCurrentWeekVolumeByFactor(factor: Double): VolumeAdjustmentResult {
        val current = program.value ?: return VolumeAdjustmentResult.NO_WEEK_SELECTED
        if (current.volumeRecommendations.isEmpty() || current.athleteProfileScore == null) {
            return VolumeAdjustmentResult.REQUIRES_CALIBRATION
        }

        val targetWeekId = when {
            activeProgramState.value?.programId == programId && !activeProgramState.value?.currentWeekId.isNullOrBlank() ->
                activeProgramState.value?.currentWeekId
            !_uiState.value.selectedWeekId.isNullOrBlank() -> _uiState.value.selectedWeekId
            else -> null
        } ?: return VolumeAdjustmentResult.NO_WEEK_SELECTED

        var changed = false
        val updated = current.copy(
            macrocycles = current.macrocycles.map { macro ->
                macro.copy(
                    blocks = macro.blocks.map { block ->
                        block.copy(
                            mesocycles = block.mesocycles.map { meso ->
                                meso.copy(
                                    weeks = meso.weeks.map { week ->
                                        if (week.id != targetWeekId) {
                                            week
                                        } else {
                                            val adjustedSessions = adjustWeekSessionsByCanonicalMuscle(
                                                sessions = week.sessions,
                                                factor = factor,
                                                canonicalMuscles = current.volumeRecommendations.map {
                                                    canonicalizeMuscleName(it.muscleGroup)
                                                }.distinct(),
                                            )
                                            if (adjustedSessions != week.sessions) changed = true
                                            week.copy(sessions = adjustedSessions)
                                        }
                                    }
                                )
                            }
                        )
                    }
                )
            }
        )

        if (!changed) return VolumeAdjustmentResult.NO_ADJUSTABLE_VOLUME
        repository.updateProgram(updated)
        return VolumeAdjustmentResult.SUCCESS
    }

    fun deleteSession(
        sessionId: String,
        macroIndex: Int,
        mesoIndex: Int,
        weekId: String,
        competitionRepository: CompetitionRepository? = runCatching { CompetitionRepository.getInstance() }.getOrNull(),
    ) {
        val current = program.value ?: return
        var changed = false
        var removedSession: Session? = null
        val updated = current.copy(
            macrocycles = current.macrocycles.mapIndexed { mi, macro ->
                if (mi != macroIndex) macro
                else {
                    var globalMesoIndex = 0
                    macro.copy(
                        blocks = macro.blocks.map { block ->
                            block.copy(
                                mesocycles = block.mesocycles.map { meso ->
                                    val currentGlobal = globalMesoIndex
                                    globalMesoIndex++
                                    if (currentGlobal != mesoIndex) meso
                                    else meso.copy(
                                        weeks = meso.weeks.map { week ->
                                            if (week.id != weekId) week
                                            else {
                                                removedSession = week.sessions.firstOrNull { it.id == sessionId }
                                                changed = true
                                                week.copy(
                                                    sessions = normalizeMainSessions(
                                                        week.sessions.filter { it.id != sessionId }
                                                    )
                                                )
                                            }
                                        }
                                    )
                                }
                            )
                        }
                    )
                }
            }
        )
        if (!changed) return
        repository.updateProgram(updated)
        // Un record de competición nunca debe quedar apuntando a una sesión eliminada.
        // Se desvincula (no se borra) para preservar intentos/bitácora ya registrados: el
        // record sigue siendo consultable desde CompetitionScreen como historial standalone.
        val recordId = removedSession?.competitionRecordId
        if (!recordId.isNullOrBlank()) {
            competitionRepository?.getById(recordId)?.let { record ->
                competitionRepository.upsert(record.copy(plannedSessionId = null, plannedWeekId = null))
            }
        }
    }

    fun addSession(macroIndex: Int, mesoIndex: Int, weekId: String, session: Session) {
        repository.upsertSessionInProgram(
            programId = programId,
            weekId = weekId,
            macroIndex = macroIndex,
            mesoIndex = mesoIndex,
            session = session,
        )
    }

    fun reorderSessions(weekId: String, fromIndex: Int, toIndex: Int) {
        val current = program.value ?: return
        val updated = current.copy(
            macrocycles = current.macrocycles.map { macro ->
                macro.copy(
                    blocks = macro.blocks.map { block ->
                        block.copy(
                            mesocycles = block.mesocycles.map { meso ->
                                meso.copy(
                                    weeks = meso.weeks.map { week ->
                                        if (week.id != weekId) week
                                        else {
                                            val sessions = week.sessions.toMutableList()
                                            val item = sessions.removeAt(fromIndex)
                                            sessions.add(toIndex, item)
                                            week.copy(sessions = normalizeMainSessions(sessions))
                                        }
                                    }
                                )
                            }
                        )
                    }
                )
            }
        )
        repository.updateProgram(updated)
    }

    fun reorderSessions(weekId: String, sessions: List<Session>) {
        val current = program.value ?: return
        val normalized = normalizeMainSessions(sessions)
        val updated = current.copy(
            macrocycles = current.macrocycles.map { macro ->
                macro.copy(
                    blocks = macro.blocks.map { block ->
                        block.copy(
                            mesocycles = block.mesocycles.map { meso ->
                                meso.copy(
                                    weeks = meso.weeks.map { week ->
                                        if (week.id == weekId) week.copy(sessions = normalized) else week
                                    }
                                )
                            }
                        )
                    }
                )
            }
        )
        repository.updateProgram(updated)
    }

    fun previewWeekCopyConflicts(sourceWeekId: String, targetWeekIds: Set<String>): List<WeekCopyConflict> {
        val current = program.value ?: return emptyList()
        return SplitApplicationEngine.buildWeekOptions(current)
            .filter { it.id in targetWeekIds && it.id != sourceWeekId && it.sessions.isNotEmpty() }
            .map { week ->
                WeekCopyConflict(
                    weekId = week.id,
                    weekName = week.name,
                    dayLabels = week.sessions
                        .mapNotNull { it.dayOfWeek }
                        .distinct()
                        .sorted()
                        .map(::dayLabelShort),
                )
            }
    }

    fun copyWeekSessions(
        sourceWeekId: String,
        targetWeekIds: Set<String>,
        replaceWeekIds: Set<String>,
    ): Boolean {
        val current = program.value ?: return false
        val source = findWeek(current, sourceWeekId) ?: return false
        val targets = targetWeekIds - sourceWeekId
        if (source.sessions.isEmpty() || targets.isEmpty()) return false

        val updated = current.copy(
            macrocycles = current.macrocycles.map { macro ->
                macro.copy(
                    blocks = macro.blocks.map { block ->
                        block.copy(
                            mesocycles = block.mesocycles.map { meso ->
                                meso.copy(
                                    weeks = meso.weeks.map { week ->
                                        if (week.id !in targets) {
                                            week
                                        } else if (week.sessions.isNotEmpty() && week.id !in replaceWeekIds) {
                                            week
                                        } else {
                                            week.copy(
                                                description = source.description ?: week.description,
                                                variant = source.variant,
                                                sessions = SplitApplicationEngine.copySessionsWithNewIds(source.sessions),
                                            )
                                        }
                                    }
                                )
                            }
                        )
                    }
                )
            }
        ).normalizedTemporalStructure()

        updateProgram(updated)
        return true
    }

    private fun normalizeMainSessions(sessions: List<Session>): List<Session> {
        val distinctSessions = sessions.distinctBy { it.id }
        val mainByDay = mutableMapOf<Int, String>()
        val fallbackByDay = mutableMapOf<Int, String>()

        distinctSessions.forEach { session ->
            val day = session.dayOfWeek ?: 1
            fallbackByDay.putIfAbsent(day, session.id)
            if (session.isMainSession && day !in mainByDay) {
                mainByDay[day] = session.id
            }
        }

        fallbackByDay.forEach { (day, sessionId) ->
            mainByDay.putIfAbsent(day, sessionId)
        }

        return distinctSessions.map { session ->
            val day = session.dayOfWeek ?: 1
            session.copy(isMainSession = mainByDay[day] == session.id)
        }
    }

    private fun parseIsoDate(raw: String): LocalDate? = try {
        LocalDate.parse(raw.trim())
    } catch (_: DateTimeParseException) {
        null
    }

    private fun nextCalendarWeekStart(program: Program): LocalDate {
        val lastEnd = program.macrocycles
            .flatMap { it.blocks }
            .flatMap { it.mesocycles }
            .flatMap { it.weeks }
            .mapNotNull { it.endDate?.let(::parseIsoDate) }
            .maxOrNull()
        return lastEnd?.plusDays(1)
            ?: program.timelineStartDate?.let(::parseIsoDate)
            ?: appClock.today(java.time.ZoneId.systemDefault())
    }

    private fun calendarWeekTitle(startDate: LocalDate): String =
        "Semana: ${startDate.format(DateTimeFormatter.ofPattern("MM/dd", Locale.US))}"

    private fun adjustWeekSessionsByCanonicalMuscle(
        sessions: List<Session>,
        factor: Double,
        canonicalMuscles: List<String>,
    ): List<Session> {
        val exerciseNodes = collectExerciseNodes(sessions)
        if (exerciseNodes.isEmpty()) return sessions

        val mutableSetCounts = exerciseNodes.associate { it.exerciseId to it.setCount }.toMutableMap()
        val muscleToExercises = linkedMapOf<String, MutableSet<String>>()

        exerciseNodes.forEach { node ->
            node.muscles.forEach { muscle ->
                muscleToExercises.getOrPut(muscle) { linkedSetOf() }.add(node.exerciseId)
            }
        }

        val orderedMuscles = (canonicalMuscles + muscleToExercises.keys).distinct()
        var changed = false

        orderedMuscles.forEach { muscle ->
            val candidates = muscleToExercises[muscle].orEmpty().toList()
            if (candidates.isEmpty()) return@forEach

            val currentTotal = candidates.sumOf { mutableSetCounts[it] ?: 0 }
            if (currentTotal == 0) return@forEach

            val targetTotal = computeTargetSetTotal(currentTotal, factor)
            when {
                targetTotal > currentTotal -> {
                    if (increaseMuscleVolume(candidates, mutableSetCounts, targetTotal - currentTotal)) {
                        changed = true
                    }
                }
                targetTotal < currentTotal -> {
                    if (decreaseMuscleVolume(candidates, mutableSetCounts, currentTotal - targetTotal)) {
                        changed = true
                    }
                }
            }
        }

        if (!changed) return sessions
        return sessions.map { applySetCountsToSession(it, mutableSetCounts) }
    }

    private fun computeTargetSetTotal(currentTotal: Int, factor: Double): Int {
        if (currentTotal <= 0) return 0
        return if (factor >= 1.0) {
            ceil(currentTotal * factor).toInt()
        } else {
            floor(currentTotal * factor).toInt().coerceAtLeast(1)
        }
    }

    private fun increaseMuscleVolume(
        candidates: List<String>,
        setCounts: MutableMap<String, Int>,
        delta: Int,
    ): Boolean {
        if (delta <= 0 || candidates.isEmpty()) return false
        var remaining = delta
        var changed = false
        val ordered = candidates.sortedWith(compareBy({ setCounts[it] ?: 0 }, { it }))

        while (remaining > 0) {
            ordered.forEach { exerciseId ->
                setCounts[exerciseId] = (setCounts[exerciseId] ?: 0) + 1
                remaining--
                changed = true
                if (remaining <= 0) return changed
            }
        }

        return changed
    }

    private fun decreaseMuscleVolume(
        candidates: List<String>,
        setCounts: MutableMap<String, Int>,
        delta: Int,
    ): Boolean {
        if (delta <= 0 || candidates.isEmpty()) return false
        var remaining = delta
        var changed = false

        while (remaining > 0) {
            val ordered = candidates.sortedByDescending { setCounts[it] ?: 0 }
            var reducedThisRound = false

            ordered.forEach { exerciseId ->
                val currentCount = setCounts[exerciseId] ?: 0
                if (currentCount > 1 && remaining > 0) {
                    setCounts[exerciseId] = currentCount - 1
                    remaining--
                    changed = true
                    reducedThisRound = true
                }
            }

            if (!reducedThisRound) break
        }

        return changed
    }

    private fun collectExerciseNodes(sessions: List<Session>): List<ExerciseNode> {
        val nodes = mutableListOf<ExerciseNode>()
        sessions.forEach { session -> collectExerciseNodesFromSession(session, nodes) }
        return nodes
    }

    private fun collectExerciseNodesFromSession(session: Session, destination: MutableList<ExerciseNode>) {
        session.exercises.forEach { exercise ->
            buildExerciseNode(exercise)?.let(destination::add)
        }
        session.parts.forEach { part ->
            part.exercises.forEach { exercise ->
                buildExerciseNode(exercise)?.let(destination::add)
            }
        }
        listOfNotNull(session.sessionB, session.sessionC, session.sessionD).forEach { nested ->
            collectExerciseNodesFromSession(nested, destination)
        }
    }

    private fun buildExerciseNode(exercise: Exercise): ExerciseNode? {
        val muscles = resolveCanonicalMuscles(exercise)
        if (muscles.isEmpty() || exercise.sets.isEmpty()) return null
        return ExerciseNode(
            exerciseId = exercise.id,
            muscles = muscles,
            setCount = exercise.sets.size,
        )
    }

    private fun resolveCanonicalMuscles(exercise: Exercise): Set<String> {
        val info = resolveCatalogExerciseInfo(
            catalogConfigurationId = exercise.catalogConfigurationId,
            exerciseDbId = exercise.exerciseDbId,
            exerciseId = exercise.exerciseId,
            exerciseName = exercise.name,
        ) ?: return emptySet()

        return info.involvedMuscles
            .filter { resolveMuscleVolumeContribution(it) > 0.0 }
            .map { involved ->
                canonicalizeMuscleName(
                    VolumeCalculator.normalizeCanonicalMuscleGroup(
                        specificMuscle = involved.muscle,
                        emphasis = involved.emphasis,
                    )
                )
            }
            .filter { it.isNotBlank() }
            .toSet()
    }

    private fun canonicalizeMuscleName(muscle: String): String {
        return when (muscle.trim().lowercase()) {
            "cuadriceps", "cuádriceps" -> "Cuádriceps"
            "gluteos", "glúteos" -> "Glúteos"
            "biceps", "bíceps" -> "Bíceps"
            "triceps", "tríceps" -> "Tríceps"
            "isquiotibiales", "isquiosurales" -> "Isquiosurales"
            else -> muscle.trim().replaceFirstChar { it.uppercase() }
        }
    }

    private fun applySetCountsToSession(session: Session, targetCounts: Map<String, Int>): Session {
        val updatedExercises = session.exercises.map { adjustExerciseSetCount(it, targetCounts[it.id]) }
        val updatedParts = session.parts.map { part ->
            part.copy(exercises = part.exercises.map { adjustExerciseSetCount(it, targetCounts[it.id]) })
        }

        return session.copy(
            exercises = updatedExercises,
            parts = updatedParts,
            sessionB = session.sessionB?.let { applySetCountsToSession(it, targetCounts) },
            sessionC = session.sessionC?.let { applySetCountsToSession(it, targetCounts) },
            sessionD = session.sessionD?.let { applySetCountsToSession(it, targetCounts) },
        )
    }

    private fun adjustExerciseSetCount(exercise: Exercise, targetCount: Int?): Exercise {
        val safeTarget = targetCount ?: return exercise
        val currentSets = exercise.sets
        if (safeTarget == currentSets.size || currentSets.isEmpty()) return exercise

        return if (safeTarget < currentSets.size) {
            exercise.copy(sets = currentSets.take(safeTarget))
        } else {
            val template = currentSets.last()
            val extraSets = List(safeTarget - currentSets.size) {
                template.copy(id = idProvider.newId())
            }
            exercise.copy(sets = currentSets + extraSets)
        }
    }

    private data class ExerciseNode(
        val exerciseId: String,
        val muscles: Set<String>,
        val setCount: Int,
    )

    // ─── Simple Calendarization Sheet State ───────────────────────────────

    private val _showSimpleCalendarizationSheet = MutableStateFlow(false)
    val showSimpleCalendarizationSheet: StateFlow<Boolean> = _showSimpleCalendarizationSheet

    private val _calendarizationStartDate = MutableStateFlow("")
    val calendarizationStartDate: StateFlow<String> = _calendarizationStartDate

    private val _calendarizationEndDate = MutableStateFlow("")
    val calendarizationEndDate: StateFlow<String> = _calendarizationEndDate

    private val _calendarizationStartDayOfWeek = MutableStateFlow(1)
    val calendarizationStartDayOfWeek: StateFlow<Int> = _calendarizationStartDayOfWeek

    private val _calendarizationTrainingDays = MutableStateFlow<Set<Int>>(emptySet())
    val calendarizationTrainingDays: StateFlow<Set<Int>> = _calendarizationTrainingDays

    fun setShowSimpleCalendarizationSheet(show: Boolean) {
        _showSimpleCalendarizationSheet.value = show
        if (show) {
            val current = program.value
            if (current != null) {
                val start = current.nextSimpleCalendarStart()
                _calendarizationStartDate.value = start.toString()
                _calendarizationEndDate.value = start.plusWeeks(3).plusDays(6).toString()
                _calendarizationStartDayOfWeek.value = current.startDay ?: 1
                _calendarizationTrainingDays.value = current.suggestCalendarTrainingDays()
            }
        }
    }

    fun setCalendarizationStartDate(date: String) {
        _calendarizationStartDate.value = date
    }

    fun setCalendarizationEndDate(date: String) {
        _calendarizationEndDate.value = date
    }

    fun setCalendarizationStartDayOfWeek(day: Int) {
        _calendarizationStartDayOfWeek.value = day
    }

    fun toggleCalendarizationTrainingDay(day: Int) {
        val current = _calendarizationTrainingDays.value
        _calendarizationTrainingDays.value = if (day in current) current - day else current + day
    }

    fun setCalendarizationTrainingDays(days: Set<Int>) {
        _calendarizationTrainingDays.value = days
    }

    fun applySimpleCalendarizedBreak() {
        val current = program.value ?: return
        val startDate = parseIsoDate(_calendarizationStartDate.value) ?: return
        val endDate = parseIsoDate(_calendarizationEndDate.value)
        val startDayOfWeek = _calendarizationStartDayOfWeek.value.coerceIn(1, 7)
        val trainingDays = _calendarizationTrainingDays.value
        if (trainingDays.isEmpty()) return

        val updated = ProgramCalendarEngine.materializeWeekDates(
            current.startSimpleCalendarizedBreak(
                startDate = startDate,
                endDate = endDate,
                startDayOfWeek = startDayOfWeek,
                trainingDays = trainingDays,
                idProvider = idProvider,
            )
        ).normalizedTemporalStructure()
        repository.updateProgram(updated)

        val newBlockId = updated.macrocycles.firstOrNull()?.blocks?.firstOrNull()?.id
        val newWeekId = updated.macrocycles
            .firstOrNull()?.blocks?.firstOrNull()
            ?.mesocycles?.firstOrNull()?.weeks?.firstOrNull()?.id
        if (newBlockId != null) {
            _uiState.update { it.copy(selectedBlockId = newBlockId, selectedWeekId = newWeekId) }
        }
        setShowSimpleCalendarizationSheet(false)
    }

    fun calendarizeSimpleCycle() {
        val current = program.value ?: return
        val startDate = parseIsoDate(_calendarizationStartDate.value) ?: return
        val startDayOfWeek = _calendarizationStartDayOfWeek.value.coerceIn(1, 7)
        val trainingDays = _calendarizationTrainingDays.value
        if (trainingDays.isEmpty()) return

        val updated = ProgramCalendarEngine.materializeWeekDates(
            current.calendarizeSimpleCycle(
                startDate = startDate,
                startDayOfWeek = startDayOfWeek,
                trainingDays = trainingDays,
                idProvider = idProvider,
            )
        ).normalizedTemporalStructure()
        repository.updateProgram(updated)
        selectFirstRoadmapPosition(updated)
        setShowSimpleCalendarizationSheet(false)
    }

    fun recoverCyclicProgram() {
        val current = program.value ?: return
        val updated = current.restorePausedCyclicProgram()
            .withFallbackSimpleWeekIfEmpty()
            .normalizedTemporalStructure()
        repository.updateProgram(updated)
        selectFirstRoadmapPosition(updated)
        setShowSimpleCalendarizationSheet(false)
    }

    fun startFreshCyclicProgram() {
        val current = program.value ?: return
        val updated = current.startFreshSimpleCycle(idProvider)
            .withFallbackSimpleWeekIfEmpty()
            .normalizedTemporalStructure()
        repository.updateProgram(updated)
        selectFirstRoadmapPosition(updated)
        setShowSimpleCalendarizationSheet(false)
    }

    private fun Program.withFallbackSimpleWeekIfEmpty(): Program {
        if (ProgramDetailHelpers.getTotalWeeks(this) > 0) return this
        if (!isSimpleTemporalProgram && structure != ProgramStructure.SIMPLE && macrocycles.isNotEmpty()) return this
        val fallbackWeek = ProgramWeek(
            id = idProvider.newId(),
            name = "Semana 1",
        )
        val fallbackMeso = Mesocycle(
            id = idProvider.newId(),
            name = "Mesociclo 1",
            goal = MesocycleGoal.ACCUMULATION,
            weeks = listOf(fallbackWeek),
        )
        val fallbackBlock = Block(
            id = idProvider.newId(),
            name = "Ciclo base",
            mesocycles = listOf(fallbackMeso),
        )
        val fallbackMacro = com.example.kpkn.data.models.Macrocycle(
            id = idProvider.newId(),
            name = "Macrociclo base",
            blocks = listOf(fallbackBlock),
        )

        return copy(
            macrocycles = if (macrocycles.isEmpty()) {
                listOf(fallbackMacro)
            } else {
                macrocycles.mapIndexed { macroIndex, macro ->
                    if (macroIndex != 0) macro
                    else macro.copy(
                        blocks = if (macro.blocks.isEmpty()) {
                            listOf(fallbackBlock)
                        } else {
                            macro.blocks.mapIndexed { blockIndex, block ->
                                if (blockIndex != 0) block
                                else block.copy(
                                    mesocycles = if (block.mesocycles.isEmpty()) {
                                        listOf(fallbackMeso)
                                    } else {
                                        block.mesocycles.mapIndexed { mesoIndex, meso ->
                                            if (mesoIndex == 0) meso.copy(weeks = listOf(fallbackWeek)) else meso
                                        }
                                    }
                                )
                            }
                        }
                    )
                }
            },
        )
    }

    private fun selectFirstRoadmapPosition(program: Program) {
        val firstBlock = ProgramDetailHelpers.buildRoadmapBlocks(program).firstOrNull()
        val firstWeek = firstBlock?.let { block ->
            ProgramDetailHelpers.getWeeksForBlock(block.id, listOf(block), program).firstOrNull()
        }
        _uiState.update {
            it.copy(
                selectedBlockId = firstBlock?.id,
                selectedWeekId = firstWeek?.id,
                structureSubTab = StructureSubTab.SEMANA,
            )
        }
    }

    private fun dayOfWeekToJava(day: Int): java.time.DayOfWeek = when (day) {
        1 -> java.time.DayOfWeek.MONDAY
        2 -> java.time.DayOfWeek.TUESDAY
        3 -> java.time.DayOfWeek.WEDNESDAY
        4 -> java.time.DayOfWeek.THURSDAY
        5 -> java.time.DayOfWeek.FRIDAY
        6 -> java.time.DayOfWeek.SATURDAY
        7 -> java.time.DayOfWeek.SUNDAY
        else -> java.time.DayOfWeek.MONDAY
    }

    // ─── Factory ──────────────────────────────────────────────────────────

    companion object {
        private const val LOG_TAG = "ProgramDetailVM"

        /** Mismo aviso para «Reemplazar todo» con protocolo y con plantilla (D2.8). */
        internal const val SESSION_IN_PROGRESS_MESSAGE =
            "Termina o descarta la sesión en curso antes de reemplazar el plan."

        /** Sin almacén de copias conectado, «Reemplazar todo» no quita el plan sin dejar copia (D2.3). */
        internal const val SNAPSHOT_REQUIRED_MESSAGE =
            "No se pudo guardar la copia recuperable del programa. No se aplicaron cambios."

        /**
         * Aviso tras «Guardar TM» o tras registrar un test de 1RM: «TM actualizado: 3 semanas recalculadas,
         * 1 entrenada intacta». Sin semanas de la receta (ni recalculadas ni entrenadas) solo «TM actualizado».
         */
        internal fun trainingMaxSavedMessage(recalculatedWeeks: Int, preservedWeeks: Int): String {
            if (recalculatedWeeks == 0 && preservedWeeks == 0) return "TM actualizado"
            val recalculated = SpanishPlurals.weeks(recalculatedWeeks) + " " +
                SpanishPlurals.choose(recalculatedWeeks, "recalculada", "recalculadas")
            if (preservedWeeks == 0) return "TM actualizado: $recalculated"
            val preserved = "$preservedWeeks " +
                SpanishPlurals.choose(preservedWeeks, "entrenada intacta", "entrenadas intactas")
            return "TM actualizado: $recalculated, $preserved"
        }

        fun factory(programId: String): ViewModelProvider.Factory = object : ViewModelProvider.Factory {
            @Suppress("UNCHECKED_CAST")
            override fun <T : ViewModel> create(modelClass: Class<T>): T {
                return ProgramDetailViewModel(programId) as T
            }
        }

        internal fun resolvePersistedWeekId(program: Program, weekId: String): String? {
            if (weekId.isBlank()) return null
            val weekIds = program.macrocycles
                .flatMap { it.blocks }
                .flatMap { it.mesocycles }
                .flatMap { it.weeks }
                .map { it.id }
                .toSet()
            if (weekId in weekIds) return weekId
            val fromInstance = ProgramProgressEngine.templateWeekIdFromInstance(weekId)
            return fromInstance.takeIf { it in weekIds }
        }

        internal fun resolveActiveWeekSelection(
            active: ActiveProgramState,
            weekIds: Collection<String>,
        ): String? {
            val idSet = weekIds.toSet()
            val candidates = listOfNotNull(
                active.currentWeekId.takeIf { it.isNotBlank() },
                active.currentWeekInstanceId?.takeIf { it.isNotBlank() },
                active.currentWeekId.takeIf { it.isNotBlank() }
                    ?.let { ProgramProgressEngine.templateWeekIdFromInstance(it) },
                active.currentWeekInstanceId?.takeIf { it.isNotBlank() }
                    ?.let { ProgramProgressEngine.templateWeekIdFromInstance(it) },
            ).distinct()
            return candidates.firstOrNull { it in idSet }
        }
    }
}

private fun dayLabelShort(dayOfWeek: Int): String = when (dayOfWeek) {
    1 -> "Lun"
    2 -> "Mar"
    3 -> "Mié"
    4 -> "Jue"
    5 -> "Vie"
    6 -> "Sáb"
    7 -> "Dom"
    else -> "Día"
}
