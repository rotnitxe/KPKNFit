package com.example.kpkn.screens.programdetail

import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.viewModelScope
import com.example.kpkn.data.exercises.EXERCISE_DATABASE_BY_ID
import com.example.kpkn.data.models.ActiveProgramState
import com.example.kpkn.data.models.Block
import com.example.kpkn.data.models.Exercise
import com.example.kpkn.data.models.HYPERTROPHY_ROLE_MULTIPLIERS
import com.example.kpkn.data.models.Mesocycle
import com.example.kpkn.data.models.MesocycleGoal
import com.example.kpkn.data.models.Program
import com.example.kpkn.data.models.ProgramCalendarizationMode
import com.example.kpkn.data.models.ProgramStructure
import com.example.kpkn.data.models.ProgramWeek
import com.example.kpkn.data.models.ProgramStatus
import com.example.kpkn.data.models.Session
import com.example.kpkn.data.models.SimpleProgramKind
import com.example.kpkn.data.models.SimpleProgramSnapshot
import com.example.kpkn.data.models.WorkoutLog
import com.example.kpkn.data.models.isSimpleTemporalProgram
import com.example.kpkn.data.models.nextSimpleCalendarStart
import com.example.kpkn.data.models.normalizedTemporalStructure
import com.example.kpkn.data.models.resolveMuscleVolumeContribution
import com.example.kpkn.data.models.restorePausedCyclicProgram
import com.example.kpkn.data.models.startFreshSimpleCycle
import com.example.kpkn.data.models.startSimpleCalendarizedBreak
import com.example.kpkn.data.models.suggestCalendarTrainingDays
import com.example.kpkn.data.repository.ProgramRepository
import com.example.kpkn.domain.training.ProgramDetailHelpers
import com.example.kpkn.domain.training.ProgramCalendarEngine
import com.example.kpkn.domain.training.RoadmapBlock
import com.example.kpkn.domain.training.RoadmapLoopMarker
import com.example.kpkn.domain.training.SplitApplicationEngine
import com.example.kpkn.domain.training.StartDaySessionMode
import com.example.kpkn.domain.training.StartDayTemporalScope
import com.example.kpkn.domain.training.VolumeCalculator
import com.example.kpkn.domain.training.WeekAdherence
import com.example.kpkn.domain.training.WeekWithMeta
import android.content.Context
import kotlin.math.roundToInt
import com.example.kpkn.data.repository.AugeRepository
import com.example.kpkn.data.models.PostSessionFeedback
import com.example.kpkn.data.models.MuscleRole
import com.example.kpkn.data.models.VolumeRecommendation
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import java.util.UUID
import java.time.LocalDate
import java.time.format.DateTimeFormatter
import java.time.format.DateTimeParseException
import java.util.Locale
import kotlin.math.ceil
import kotlin.math.floor

enum class MainTab { TRAINING, ANALYTICS }

enum class StructureSubTab { SEMANA, SPLIT, MACROCICLO, LOOPS, PROTOCOLOS }

enum class AnalyticsSubTab { VOLUMEN, PROGRESO, HISTORIALES }

enum class VolumeAdjustmentResult { SUCCESS, REQUIRES_CALIBRATION, NO_WEEK_SELECTED, NO_ADJUSTABLE_VOLUME }

data class ProgramDetailUiState(
    val activeTab: MainTab = MainTab.TRAINING,
    val structureSubTab: StructureSubTab = StructureSubTab.SEMANA,
    val analyticsSubTab: AnalyticsSubTab = AnalyticsSubTab.VOLUMEN,
    val selectedBlockId: String? = null,
    val selectedWeekId: String? = null,
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

class ProgramDetailViewModel(private val programId: String) : ViewModel() {

    private val repository = ProgramRepository.getInstance()

    val feedbacks = MutableStateFlow<List<PostSessionFeedback>>(emptyList())

    // ─── UI State ─────────────────────────────────────────────────────────

    private val _uiState = MutableStateFlow(ProgramDetailUiState())

    val uiState: StateFlow<ProgramDetailUiState> = _uiState

    // ─── Raw Data from Repository ─────────────────────────────────────────

    val program: StateFlow<Program?> = combine(
        repository.programs.map { programs -> programs.find { it.id == programId } },
        feedbacks
    ) { p, fbs ->
        if (p == null) return@combine null
        if (fbs.isEmpty()) return@combine p

        val scaledRecommendations = p.volumeRecommendations.map { rec ->
            val adj = VolumeCalculator.calculateVolumeAdjustment(rec.muscleGroup, fbs)
            if (adj == 1.0) rec
            else rec.copy(
                minEffectiveVolume = (rec.minEffectiveVolume * adj).roundToInt(),
                maxAdaptiveVolume = (rec.maxAdaptiveVolume * adj).roundToInt(),
                maxRecoverableVolume = (rec.maxRecoverableVolume * adj).roundToInt()
            )
        }
        p.copy(volumeRecommendations = scaledRecommendations)
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

    fun loadFeedbacks(context: Context) {
        viewModelScope.launch {
            try {
                val list = AugeRepository.getInstance(context).getPostSessionFeedbacks()
                feedbacks.value = list
            } catch (_: Exception) {}
        }
    }

    val muscleCdbsStatus: StateFlow<Map<String, MuscleOvertrainingStatus>> = combine(
        program,
        programLogs,
        feedbacks
    ) { p, logs, fbs ->
        if (p == null) return@combine emptyMap()

        val statusMap = mutableMapOf<String, MuscleOvertrainingStatus>()
        val exerciseList = EXERCISE_DATABASE_BY_ID.values.toList()

        val completedVolumes = VolumeCalculator.calculateCompletedWeeklyMuscleVolume(
            logs = logs,
            exerciseList = exerciseList,
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
                isOvertrained -> "Sobreentrenamiento Crónico: Detectado por ${factorsList.joinToString(", ")}."
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
                if (active != null && active.programId == programId && active.status == ProgramStatus.ACTIVE) {
                    val activeBlock = ProgramDetailHelpers.findActiveBlockId(active, programId, blocks)
                    if (activeBlock != null && _uiState.value.selectedBlockId != activeBlock) {
                        _uiState.update { it.copy(selectedBlockId = activeBlock) }
                    }
                } else if (_uiState.value.selectedBlockId == null) {
                    _uiState.update { it.copy(selectedBlockId = blocks.first().id) }
                }
            }.collect {}
        }

        // Auto-select active/first week when activeProgramState, program, or currentWeeks changes
        viewModelScope.launch {
            combine(activeProgramState, program, currentWeeks) { active, p, weeks ->
                if (p == null || weeks.isEmpty()) return@combine
                if (active != null && active.programId == programId && active.status == ProgramStatus.ACTIVE) {
                    val activeWeek = active.currentWeekId
                    if (activeWeek != null && weeks.any { it.id == activeWeek }) {
                        if (_uiState.value.selectedWeekId != activeWeek) {
                            _uiState.update { it.copy(selectedWeekId = activeWeek) }
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
    }

    // ─── Actions ──────────────────────────────────────────────────────────

    fun setActiveTab(tab: MainTab) {
        _uiState.update { it.copy(activeTab = tab) }
    }

    fun setStructureSubTab(tab: StructureSubTab) {
        _uiState.update { it.copy(structureSubTab = tab) }
    }

    fun setAnalyticsSubTab(tab: AnalyticsSubTab) {
        _uiState.update { it.copy(analyticsSubTab = tab) }
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

    fun updateProgram(updated: Program) {
        repository.updateProgram(ProgramCalendarEngine.materializeWeekDates(updated))
    }

    fun setSimpleDatedCalendarization(enabled: Boolean) {
        val current = program.value ?: return
        if (!current.isSimpleTemporalProgram && current.structure != ProgramStructure.SIMPLE) return
        val updated = if (enabled) {
            current.copy(
                timelineStartDate = current.timelineStartDate ?: LocalDate.now().toString(),
                calendarization = current.calendarization ?: ProgramCalendarEngine.defaultSimpleDatedCalendarization(),
                simpleProgramKind = SimpleProgramKind.CALENDARIZED,
                pausedCyclicSnapshot = current.pausedCyclicSnapshot ?: current.toSimpleProgramSnapshot(),
                loops = emptyList(),
                loopState = null,
                events = emptyList(),
            )
        } else {
            current.pausedCyclicSnapshot?.let { snapshot ->
                current.copy(
                    timelineStartDate = current.timelineStartDate,
                    calendarization = null,
                    simpleProgramKind = SimpleProgramKind.CYCLIC,
                    macrocycles = snapshot.macrocycles,
                    loops = snapshot.loops,
                    loopState = snapshot.loopState,
                    events = snapshot.events,
                    selectedSplitId = snapshot.selectedSplitId,
                    customSplitPattern = snapshot.customSplitPattern,
                    customSplitName = snapshot.customSplitName,
                    customSplitDescription = snapshot.customSplitDescription,
                    blockSplitSelections = snapshot.blockSplitSelections,
                    pausedCyclicSnapshot = null,
                )
            } ?: current.copy(
                calendarization = null,
                simpleProgramKind = SimpleProgramKind.CYCLIC,
                pausedCyclicSnapshot = null,
            )
        }
        repository.updateProgram(ProgramCalendarEngine.materializeWeekDates(updated))
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
                id = UUID.randomUUID().toString(),
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
                id = UUID.randomUUID().toString(),
                name = name?.trim()?.takeIf { it.isNotEmpty() } ?: "Semana ${ProgramDetailHelpers.getTotalWeeks(current) + 1}",
                description = description?.trim()?.takeIf { it.isNotEmpty() },
                sessions = copiedSessions,
            )
        }

        if (current.macrocycles.isEmpty() || current.macrocycles.firstOrNull()?.blocks.isNullOrEmpty()) {
            val fallbackMeso = defaultRoadmapMesocycle(newWeek)
            val fallbackBlock = Block(
                id = "block_simple_${System.nanoTime()}",
                name = "Ciclo base",
                mesocycles = listOf(fallbackMeso),
            )
            val fallbackMacro = com.example.kpkn.data.models.Macrocycle(
                id = "macro_simple_${System.nanoTime()}",
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

            repository.updateProgram(updated)
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

        repository.updateProgram(updated)
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

        repository.updateProgram(updated)
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

        repository.updateProgram(updated)
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
            id = UUID.randomUUID().toString(),
            name = name,
            description = description?.trim()?.takeIf { it.isNotEmpty() },
        )
    }

    private fun defaultRoadmapMesocycle(firstWeek: ProgramWeek): Mesocycle {
        return Mesocycle(
            id = UUID.randomUUID().toString(),
            name = "Mesociclo 1",
            goal = MesocycleGoal.ACCUMULATION,
            weeks = listOf(firstWeek),
        )
    }

    private fun defaultRoadmapBlock(name: String, description: String?, firstWeek: ProgramWeek): Block {
        return Block(
            id = UUID.randomUUID().toString(),
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

    fun updateBlockMetadata(blockId: String, name: String, description: String?) {
        val current = program.value ?: return
        val normalizedDescription = description?.trim()?.takeIf { it.isNotEmpty() }
        val updated = current.copy(
            macrocycles = current.macrocycles.map { macro ->
                macro.copy(
                    blocks = macro.blocks.map { block ->
                        if (block.id == blockId) {
                            block.copy(
                                name = name.trim().ifBlank { block.name },
                                description = normalizedDescription,
                            )
                        } else block
                    }
                )
            }
        )
        repository.updateProgram(updated)
    }

    fun deleteWeekFromRoadmap(weekId: String) {
        val current = program.value ?: return
        if (ProgramDetailHelpers.getTotalWeeks(current) <= 1) return

        val updated = current.copy(
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

        repository.updateProgram(updated)
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

        val updated = current.copy(
            macrocycles = current.macrocycles.map { macro ->
                macro.copy(blocks = macro.blocks.filterNot { it.id == blockId })
            }.filter { it.blocks.isNotEmpty() }
        ).normalizedTemporalStructure()

        repository.updateProgram(updated)
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

    fun deleteSession(sessionId: String, macroIndex: Int, mesoIndex: Int, weekId: String) {
        val current = program.value ?: return
        val updated = current.copy(
            macrocycles = current.macrocycles.mapIndexed { mi, macro ->
                if (mi != macroIndex) macro
                else macro.copy(
                    blocks = macro.blocks.map { block ->
                        block.copy(
                            mesocycles = block.mesocycles.mapIndexed { mesoI, meso ->
                                if (mesoI != mesoIndex) meso
                                else meso.copy(
                                    weeks = meso.weeks.map { week ->
                                        if (week.id != weekId) week
                                        else week.copy(
                                            sessions = normalizeMainSessions(
                                                week.sessions.filter { it.id != sessionId }
                                            )
                                        )
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

    fun replaceWeekSessions(weekId: String, sessions: List<Session>) {
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

        repository.updateProgram(updated)
        return true
    }

    fun createCalendarWeeks(startDateIso: String, weekCount: Int, trainingDays: Set<Int>) {
        val current = program.value ?: return
        val startDate = parseIsoDate(startDateIso) ?: return
        val safeCount = weekCount.coerceIn(1, 52)
        val safeDays = trainingDays.filter { it in 1..7 }.toSet()
        if (current.isSimpleTemporalProgram) {
            appendCalendarWeeksToSimple(current, startDate, safeCount, safeDays)
        } else {
            appendCalendarWeeksToSelectedBlock(current, startDate, safeCount, safeDays)
        }
    }

    private fun appendCalendarWeeksToSimple(current: Program, startDate: LocalDate, weekCount: Int, trainingDays: Set<Int>) {
        val macroIndex = current.macrocycles.indexOfFirst { it.blocks.isNotEmpty() }.takeIf { it >= 0 } ?: return
        val block = current.macrocycles[macroIndex].blocks.firstOrNull() ?: return
        val mesoIndex = block.mesocycles.indexOfLast { true }.takeIf { it >= 0 } ?: return
        val offset = ProgramDetailHelpers.getTotalWeeks(current)
        val newWeeks = buildCalendarWeeks(startDate, weekCount, trainingDays, offset, current.startDay ?: 1)
        val updated = current.copy(
            timelineStartDate = current.timelineStartDate ?: startDate.toString(),
            calendarization = current.calendarization ?: ProgramCalendarEngine.defaultSimpleDatedCalendarization(),
            simpleProgramKind = SimpleProgramKind.CALENDARIZED,
            pausedCyclicSnapshot = current.pausedCyclicSnapshot ?: current.toSimpleProgramSnapshot(),
            loops = emptyList(),
            loopState = null,
            events = emptyList(),
            macrocycles = current.macrocycles.mapIndexed { currentMacroIndex, macro ->
                if (currentMacroIndex != macroIndex) macro else macro.copy(
                    blocks = macro.blocks.mapIndexed { blockIndex, currentBlock ->
                        if (blockIndex != 0) currentBlock else currentBlock.copy(
                            mesocycles = currentBlock.mesocycles.mapIndexed { currentMesoIndex, meso ->
                                if (currentMesoIndex != mesoIndex) meso else meso.copy(weeks = meso.weeks + newWeeks)
                            }
                        )
                    }
                )
            }
        ).normalizedTemporalStructure()
        repository.updateProgram(ProgramCalendarEngine.materializeWeekDates(updated))
        _uiState.update { it.copy(selectedBlockId = block.id, selectedWeekId = newWeeks.lastOrNull()?.id) }
    }

    private fun appendCalendarWeeksToSelectedBlock(current: Program, startDate: LocalDate, weekCount: Int, trainingDays: Set<Int>) {
        val blocks = ProgramDetailHelpers.buildRoadmapBlocks(current)
        val target = blocks.find { it.id == _uiState.value.selectedBlockId } ?: blocks.firstOrNull() ?: return
        val macro = current.macrocycles.getOrNull(target.macroIndex) ?: return
        val block = macro.blocks.getOrNull(target.blockIndex) ?: return
        val offset = countWeeksBeforeAppendingToBlock(current, target.macroIndex, target.blockIndex) + block.mesocycles.sumOf { it.weeks.size }
        val newWeeks = buildCalendarWeeks(startDate, weekCount, trainingDays, offset, current.startDay ?: 1)
        val lastMesoIndex = block.mesocycles.lastIndex
        val updated = current.copy(
            timelineStartDate = current.timelineStartDate ?: startDate.toString(),
            macrocycles = current.macrocycles.mapIndexed { macroIndex, currentMacro ->
                if (macroIndex != target.macroIndex) currentMacro else currentMacro.copy(
                    blocks = currentMacro.blocks.mapIndexed { blockIndex, currentBlock ->
                        if (blockIndex != target.blockIndex) currentBlock
                        else if (lastMesoIndex < 0) currentBlock.copy(mesocycles = listOf(defaultRoadmapMesocycle(newWeeks.first()).copy(weeks = newWeeks)))
                        else currentBlock.copy(
                            mesocycles = currentBlock.mesocycles.mapIndexed { mesoIndex, meso ->
                                if (mesoIndex == lastMesoIndex) meso.copy(weeks = meso.weeks + newWeeks) else meso
                            }
                        )
                    }
                )
            }
        ).normalizedTemporalStructure()
        repository.updateProgram(ProgramCalendarEngine.materializeWeekDates(updated))
        _uiState.update { it.copy(selectedBlockId = target.id, selectedWeekId = newWeeks.lastOrNull()?.id, structureSubTab = StructureSubTab.SEMANA) }
    }

    private fun Program.toSimpleProgramSnapshot(): SimpleProgramSnapshot =
        SimpleProgramSnapshot(
            macrocycles = macrocycles,
            loops = loops,
            loopState = loopState,
            events = events,
            selectedSplitId = selectedSplitId,
            customSplitPattern = customSplitPattern,
            customSplitName = customSplitName,
            customSplitDescription = customSplitDescription,
            blockSplitSelections = blockSplitSelections,
            savedAtMs = System.currentTimeMillis(),
        )

    private fun buildCalendarWeeks(startDate: LocalDate, weekCount: Int, trainingDays: Set<Int>, weekOffset: Int, startDayOfWeek: Int = 1): List<ProgramWeek> {
        val startDayIsoValue = startDayOfWeek.coerceIn(1, 7)
        return (0 until weekCount).map { index ->
            val weekStart = startDate.plusWeeks(index.toLong())
            val weekEnd = weekStart.plusDays(6)
            ProgramWeek(
                id = UUID.randomUUID().toString(),
            name = calendarWeekTitle(weekStart),
            startDate = weekStart.toString(),
                endDate = weekEnd.toString(),
                trainingDayDates = trainingDays.associate { dayOfWeek ->
                    val targetDayIsoValue = dayOfWeekToJava(dayOfWeek).value
                    val offset = ((targetDayIsoValue - startDayIsoValue + 7) % 7).toLong()
                    val actualDate = weekStart.plusDays(offset)
                    dayOfWeek to actualDate.toString()
                },
            )
        }
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
            ?: LocalDate.now()
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
        val exerciseDbId = exercise.exerciseDbId?.lowercase() ?: return emptySet()
        val info = EXERCISE_DATABASE_BY_ID[exerciseDbId] ?: return emptySet()

        return info.involvedMuscles
            .filter { resolveMuscleVolumeContribution(it) > 0.0 }
            .map { involved ->
                canonicalizeMuscleName(
                    VolumeCalculator.normalizeMuscleGroup(
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
                template.copy(id = UUID.randomUUID().toString())
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
        val updated = current.startFreshSimpleCycle()
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
            id = "week_simple_${System.nanoTime()}",
            name = "Semana 1",
        )
        val fallbackMeso = Mesocycle(
            id = "meso_simple_${System.nanoTime()}",
            name = "Mesociclo 1",
            goal = MesocycleGoal.ACCUMULATION,
            weeks = listOf(fallbackWeek),
        )
        val fallbackBlock = Block(
            id = "block_simple_${System.nanoTime()}",
            name = "Ciclo base",
            mesocycles = listOf(fallbackMeso),
        )
        val fallbackMacro = com.example.kpkn.data.models.Macrocycle(
            id = "macro_simple_${System.nanoTime()}",
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
        fun factory(programId: String): ViewModelProvider.Factory = object : ViewModelProvider.Factory {
            @Suppress("UNCHECKED_CAST")
            override fun <T : ViewModel> create(modelClass: Class<T>): T {
                return ProgramDetailViewModel(programId) as T
            }
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
