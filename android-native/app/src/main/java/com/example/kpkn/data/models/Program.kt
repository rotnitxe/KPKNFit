package com.example.kpkn.data.models

import kotlinx.serialization.Serializable
import java.time.LocalDate
import com.example.kpkn.domain.training.ProgramCalendarEngine
import com.example.kpkn.domain.training.AppClock
import com.example.kpkn.domain.training.IdProvider
import com.example.kpkn.domain.training.LoopEngine
import com.example.kpkn.domain.training.SystemAppClock
import com.example.kpkn.domain.training.UuidIdProvider

@Serializable
data class Program(
    val id: String,
    val name: String,
    val description: String? = null,
    val coverImage: String? = null,
    val mode: ProgramMode = ProgramMode.HYPERTROPHY,
    val structure: ProgramStructure = ProgramStructure.SIMPLE,
    val blockLabel: String? = null,
    val macrocycles: List<Macrocycle> = emptyList(),
    val author: String? = null,
    val isPublic: Boolean = false,
    val tags: List<String> = emptyList(),
    val events: List<ProgramEvent> = emptyList(),
    val loops: List<Loop> = emptyList(),
    val loopState: LoopState? = null,
    val exerciseGoals: Map<String, Double> = emptyMap(),
    val goals: ProgramGoals? = null,
    val trainingPhase: TrainingPhase? = null,
    val volumeSystem: VolumeSystem? = null,
    val autoVolumeEnabled: Boolean = false,
    val startDay: Int? = null,
    val weekDays: Int? = null,
    val selectedSplitId: String? = null,
    val customSplitPattern: List<String> = emptyList(),
    val customSplitName: String? = null,
    val customSplitDescription: String? = null,
    val blockSplitSelections: Map<String, String> = emptyMap(),
    val weekSplitSelections: Map<String, String> = emptyMap(),
    val structureTemplateId: String? = null,
    val timelineStartDate: String? = null,
    val calendarization: ProgramCalendarization? = null,
    val simpleProgramKind: SimpleProgramKind = SimpleProgramKind.CYCLIC,
    val pausedCyclicSnapshot: SimpleProgramSnapshot? = null,
    val keyDates: List<ProgramKeyDate> = emptyList(),
    val volumeRecommendations: List<VolumeRecommendation> = emptyList(),
    val athleteProfileScore: AthleteProfileScore? = null,
    val volumeAlertsEnabled: Boolean = true,
    val volumeSetupPromptSeen: Boolean = false,
    val splitTrialSeen: Boolean = false,
    val isDraft: Boolean = false,
    val schedulePlan: ProgramSchedulePlan? = null,
    val calendarBreaks: List<CalendarBreak> = emptyList(),
    val runState: ProgramRunState? = null,
    val loopOccurrences: List<LoopOccurrence> = emptyList(),
    val powerliftingProfile: PowerliftingProfile? = null,
)

enum class ProgramMode { POWERLIFTING, HYPERTROPHY, POWERBUILDING }
enum class ProgramStructure { SIMPLE, COMPLEX }
enum class TrainingPhase { ACCUMULATION, TRANSFORMATION, REALIZATION }
enum class VolumeSystem { ISRAETEL, KPNK, MANUAL }
enum class ProgramCalendarizationMode { ADVANCED_COMPETITION, SIMPLE_DATED }
enum class SimpleProgramKind { CYCLIC, CALENDARIZED }

@Serializable
data class ProgramCalendarization(
    val mode: ProgramCalendarizationMode,
    val manualEndDate: String? = null,
    val strictStart: Boolean = false,
    val activatedByCompetition: Boolean = false,
)

@Serializable
data class SimpleProgramSnapshot(
    val macrocycles: List<Macrocycle> = emptyList(),
    val loops: List<Loop> = emptyList(),
    val loopState: LoopState? = null,
    val events: List<ProgramEvent> = emptyList(),
    val selectedSplitId: String? = null,
    val customSplitPattern: List<String> = emptyList(),
    val customSplitName: String? = null,
    val customSplitDescription: String? = null,
    val blockSplitSelections: Map<String, String> = emptyMap(),
    val weekSplitSelections: Map<String, String> = emptyMap(),
    val savedAtMs: Long = 0L,
    /** Congela el cursor cíclico para restaurarlo al salir del break. */
    val runState: ProgramRunState? = null,
    val schedulePlan: ProgramSchedulePlan? = null,
    val loopOccurrences: List<LoopOccurrence> = emptyList(),
    val activeWeekId: String? = null,
    val activeWeekInstanceId: String? = null,
    val activeCycleNumber: Int? = null,
    val programRunId: String? = null,
)

@Serializable
data class VolumeRecommendation(
    val muscleGroup: String,
    val minEffectiveVolume: Int,
    val maxAdaptiveVolume: Int,
    val maxRecoverableVolume: Int,
    val frequencyCap: Int = 4,
)

@Serializable
data class AthleteProfileScore(
    val technicalScore: Int,
    val consistencyScore: Int,
    val strengthScore: Int,
    val mobilityScore: Int,
    val trainingStyle: TrainingStyle,
    val totalScore: Int,
    val profileLevel: AthleteProfileLevel,
)

enum class TrainingStyle { BODYBUILDER, POWERBUILDER, POWERLIFTER }
enum class AthleteProfileLevel { BEGINNER, ADVANCED }

@Serializable
data class ProgramGoals(
    val squat1RM: Double? = null,
    val bench1RM: Double? = null,
    val deadlift1RM: Double? = null,
)

@Serializable
data class LoopState(
    val currentCycle: Int = 0,
    val postponed: List<PostponedLoop> = emptyList(),
    val cancelled: List<String> = emptyList(),
    /** Claves `loopId:scheduledCycle` de ocurrencias canceladas sin anular la regla. */
    val cancelledOccurrences: List<String> = emptyList(),
)

@Serializable
data class PostponedLoop(
    val loopId: String,
    val fromCycle: Int,
    val toCycle: Int,
)

@Serializable
data class Macrocycle(
    val id: String,
    val name: String,
    val blocks: List<Block> = emptyList(),
)

@Serializable
enum class BlockMaterializationStatus { GENERATED, OUTDATED, USER_MODIFIED }

@Serializable
data class Block(
    val id: String,
    val name: String,
    val description: String? = null,
    val mesocycles: List<Mesocycle> = emptyList(),
    /** Objetivo de bloque (enum NUEVO; opcional para no romper JSON legacy). */
    val goal: BlockGoal? = null,
    /** Esquema de progresión semanal dentro del bloque (enum NUEVO). */
    val progressionScheme: BlockProgressionScheme? = null,
    /** Metadata changed without silently rewriting user prescriptions. */
    val materializationPending: Boolean = false,
    val materializationStatus: BlockMaterializationStatus = BlockMaterializationStatus.GENERATED,
    val sourceDefinitionId: String? = null,
    val sourceRevision: String? = null,
    val prescriptionOrigin: String? = null,
)

@Serializable
data class Mesocycle(
    val id: String,
    val name: String,
    val goal: MesocycleGoal = MesocycleGoal.ACCUMULATION,
    val customGoal: String? = null,
    val weeks: List<ProgramWeek> = emptyList(),
)

enum class MesocycleGoal(val label: String) {
    ACCUMULATION("Acumulación"),
    INTENSIFICATION("Intensificación"),
    REALIZATION("Realización"),
    DELOAD("Descarga"),
    CUSTOM("Custom"),
}

/** Objetivo a nivel de bloque (separado de [MesocycleGoal] para no mutar enums legacy en dbJson). */
enum class BlockGoal(val label: String) {
    ACCUMULATION("Acumulación"),
    INTENSIFICATION("Intensificación"),
    /** Trabajo específico de competición antes del pico. */
    SPECIFICITY("Especificidad"),
    REALIZATION("Realización"),
    DELOAD("Descarga"),
    DENSITY("Densidad / Metabolitos"),
    PEAK("Pico"),
    TAPER("Taper"),
    CUSTOM("Custom"),
}

/** Cómo se actualiza la prescripción semana a semana dentro de un bloque. */
enum class BlockProgressionScheme {
    NONE,
    LINEAR_LOAD,
    UNDULATING,
    PERCENT_RM,
    RPE_CAP,
}

@Serializable
data class ProgramWeek(
    val id: String,
    val name: String,
    val description: String? = null,
    val sessions: List<Session> = emptyList(),
    val variant: WeekVariant? = null,
    val isLoopWeek: Boolean = false,
    val loopId: String? = null,
    val startDate: String? = null,
    val endDate: String? = null,
    val trainingDayDates: Map<Int, String> = emptyMap(),
    /** Índice 1-based de progresión dentro del bloque (opcional; default null = legacy). */
    val progressionIndex: Int? = null,
    /** A rest week completes by design; an empty training week never does. */
    val executionKind: WeekExecutionKind = WeekExecutionKind.TRAINING,
)

enum class WeekVariant { A, B, C, D }

@Serializable
enum class WeekExecutionKind { TRAINING, DELOAD, REST }

@Serializable
data class Loop(
    val id: String,
    val title: String,
    val type: LoopType = LoopType.CUSTOM,
    val repeatEveryXLoops: Int = 1,
    val durationType: DurationType = DurationType.WEEK,
    val dayOfWeek: Int? = null,
    val durationWeeks: Int? = null,
    val priority: Int? = null,
    val sessions: List<Session> = emptyList(),
    val color: String? = null,
)

enum class LoopType { ONE_RM_TEST, DELOAD, COMPETITION, CUSTOM }
enum class DurationType { DAY, WEEK }

@Serializable
data class LoopActivation(
    val loopId: String,
    val cycle: Int,
    val status: LoopStatus = LoopStatus.SCHEDULED,
    val postponedTo: Int? = null,
)

enum class LoopStatus { SCHEDULED, ACTIVE, COMPLETED, POSTPONED, CANCELLED }

@Serializable
data class ProgramKeyDate(
    val id: String,
    val title: String,
    val type: KeyDateType = KeyDateType.CUSTOM,
    val startDate: String,
    val endDate: String? = null,
    val eventDate: String? = null,
    val notes: String? = null,
)

enum class KeyDateType {
    COMPETITION,
    EXAMS,
    VACATION,
    TRAVEL,
    CUSTOM,
}

@Serializable
data class ProgramEvent(
    val id: String? = null,
    val title: String,
    val type: String,
    val date: String,
    val endDate: String? = null,
    val calculatedWeek: Int,
    val createMacrocycle: Boolean = false,
    @Deprecated("Use loops[] instead")
    val repeatEveryXCycles: Int? = null,
    val sessions: List<Session> = emptyList(),
)

val Program.totalBlockCount: Int
    get() = macrocycles.sumOf { it.blocks.size }

val Program.totalMesocycleCount: Int
    get() = macrocycles.sumOf { macro -> macro.blocks.sumOf { it.mesocycles.size } }

val Program.totalProgramWeeks: Int
    get() = macrocycles.sumOf { macro -> macro.blocks.sumOf { block -> block.mesocycles.sumOf { it.weeks.size } } }

/** Simple explícito por contrato serializado; no infiere por cantidad de bloques. */
val Program.isSimpleProgram: Boolean
    get() = structure == ProgramStructure.SIMPLE

/** Alias histórico — usa structure, no el conteo de bloques. */
val Program.isSimpleTemporalProgram: Boolean
    get() = isSimpleProgram

val Program.isSimpleCalendarizedProgram: Boolean
    get() = isSimpleProgram && simpleProgramKind == SimpleProgramKind.CALENDARIZED

/** Identidad del break calendarizado activo; aisla logs del run cíclico pausado. */
val Program.activeCalendarBreakId: String?
    get() {
        if (!isSimpleCalendarizedProgram) return null
        return calendarBreaks.lastOrNull()?.id
            ?: resolvedSchedulePlan().anchorDate?.let { "cal_${id}_$it" }
            ?: "cal_$id"
    }

val Program.simpleCycleWeeks: Int?
    get() = if (isSimpleProgram) {
        macrocycles.firstOrNull()?.blocks?.firstOrNull()
            ?.mesocycles?.sumOf { meso -> meso.weeks.count { !it.isLoopWeek } }
            ?.takeIf { it > 0 }
    } else null

val Program.primaryLoopCadenceCycles: Int?
    get() = when {
        loops.isNotEmpty() -> loops.minOf { it.repeatEveryXLoops.coerceAtLeast(1) }
        events.isNotEmpty() -> events.mapNotNull { it.repeatEveryXCycles }.minOrNull()?.coerceAtLeast(1)
        else -> null
    }

val Program.primaryLoopLengthWeeks: Int?
    get() = simpleCycleWeeks?.let { cycleWeeks ->
        primaryLoopCadenceCycles?.let { cadence -> cycleWeeks * cadence }
    }

fun Program.resolvedSchedulePlan(): ProgramSchedulePlan {
    schedulePlan?.let { existing ->
        // schedulePlan is the SSoT. Legacy fields only fill nulls while a record
        // is in memory; ProgramMigrationEngine persists the same reconciliation.
        return existing.copy(
            anchorDate = existing.anchorDate ?: timelineStartDate,
            weekStartDay = existing.weekStartDay ?: startDay,
            targetEndDate = existing.targetEndDate ?: calendarization?.manualEndDate,
        )
    }
    return ProgramSchedulePlan(
        anchorDate = timelineStartDate,
        weekStartDay = startDay,
        trainingDays = emptySet(),
        targetEndDate = calendarization?.manualEndDate,
        mode = when {
            calendarization != null && !timelineStartDate.isNullOrBlank() -> ScheduleMode.DATED
            else -> ScheduleMode.FLOATING
        },
    )
}

fun Program.validateTemporalStructure(): List<TemporalStructureIssue> {
    val issues = mutableListOf<TemporalStructureIssue>()
    if (isSimpleProgram) {
        if (macrocycles.size > 1) {
            issues += TemporalStructureIssue(
                TemporalStructureIssueType.SIMPLE_MULTIPLE_MACROCYCLES,
                "Programa Simple con ${macrocycles.size} macrociclos; se requiere un solo macrociclo.",
            )
        }
        if (totalBlockCount > 1) {
            issues += TemporalStructureIssue(
                TemporalStructureIssueType.SIMPLE_MULTIPLE_BLOCKS,
                "Programa Simple con $totalBlockCount bloques; agregar bloques requiere conversión explícita a Avanzado.",
            )
        }
        if (simpleProgramKind == SimpleProgramKind.CALENDARIZED && loops.isNotEmpty()) {
            issues += TemporalStructureIssue(
                TemporalStructureIssueType.CALENDARIZED_WITH_LOOPS,
                "Programa calendarizado con loops activos; los loops deben pausarse durante el break.",
            )
        }
    }
    LoopEngine.validate(this).forEach { loopIssue ->
        issues += TemporalStructureIssue(
            TemporalStructureIssueType.LOOP_INCONSISTENCY,
            "${loopIssue.type}: ${loopIssue.message}",
        )
    }
    if (structure == ProgramStructure.COMPLEX && macrocycles.isEmpty()) {
        issues += TemporalStructureIssue(
            TemporalStructureIssueType.COMPLEX_MISSING_STRUCTURE,
            "Programa Avanzado sin macrociclos.",
        )
    }
    val invalidDays = resolvedSchedulePlan().trainingDays.filterNot { it in 1..7 }
    if (invalidDays.isNotEmpty()) {
        issues += TemporalStructureIssue(
            TemporalStructureIssueType.INVALID_TRAINING_DAYS,
            "Días de entrenamiento inválidos: $invalidDays",
        )
    }
    return issues
}

/**
 * Alinea metadatos temporales sin reclasificar structure Simple/Avanzado.
 * Reemplaza normalizedTemporalStructure() que cambiaba el tipo silenciosamente.
 */
fun Program.alignTemporalMetadata(): Program {
    val isSimple = isSimpleProgram
    val normalizedSimpleKind = when {
        !isSimple -> SimpleProgramKind.CYCLIC
        calendarization?.mode == ProgramCalendarizationMode.SIMPLE_DATED && !resolvedSchedulePlan().anchorDate.isNullOrBlank() ->
            SimpleProgramKind.CALENDARIZED
        else -> simpleProgramKind
    }
    val cleanMacrocycles = macrocycles.map { macro ->
        macro.copy(
            blocks = macro.blocks.map { block ->
                block.copy(
                    mesocycles = block.mesocycles.map { meso ->
                        meso.copy(
                            weeks = meso.weeks.map { week ->
                                if (week.isLoopWeek && !isSimple) week.copy(isLoopWeek = false, loopId = null)
                                else week
                            }
                        )
                    }
                )
            }
        )
    }
    return copy(
        simpleProgramKind = normalizedSimpleKind,
        loops = if (isSimple && normalizedSimpleKind == SimpleProgramKind.CYCLIC) loops else emptyList(),
        loopState = if (isSimple && normalizedSimpleKind == SimpleProgramKind.CYCLIC) loopState else null,
        events = if (isSimple && normalizedSimpleKind == SimpleProgramKind.CYCLIC) events else emptyList(),
        pausedCyclicSnapshot = if (isSimple) pausedCyclicSnapshot else null,
        macrocycles = cleanMacrocycles,
    )
}

/** @deprecated Usar alignTemporalMetadata() — nunca cambia structure automáticamente. */
fun Program.normalizedTemporalStructure(): Program = alignTemporalMetadata()

fun Program.toSimpleProgramSnapshot(
    clock: AppClock = SystemAppClock,
    activeState: ActiveProgramState? = null,
): SimpleProgramSnapshot =
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
        weekSplitSelections = weekSplitSelections,
        savedAtMs = clock.now().toEpochMilli(),
        runState = runState,
        schedulePlan = schedulePlan ?: resolvedSchedulePlan().copy(mode = ScheduleMode.FLOATING),
        loopOccurrences = loopOccurrences,
        activeWeekId = activeState?.currentWeekId ?: runState?.weekId,
        activeWeekInstanceId = activeState?.currentWeekInstanceId ?: runState?.weekInstanceId,
        activeCycleNumber = activeState?.currentCycleNumber ?: runState?.cycleNumber,
        programRunId = activeState?.programRunId ?: runState?.runId,
    )

fun Program.startSimpleCalendarizedBreak(
    startDate: LocalDate,
    endDate: LocalDate?,
    startDayOfWeek: Int,
    trainingDays: Set<Int>,
    idProvider: IdProvider = UuidIdProvider,
): Program {
    val safeDays = trainingDays.filter { it in 1..7 }.toSet().ifEmpty { suggestCalendarTrainingDays() }
    val snapshot = pausedCyclicSnapshot ?: toSimpleProgramSnapshot()

    val calculatedEndDate = endDate ?: startDate.plusWeeks(3).plusDays(6)
    val alignedStart = ProgramCalendarEngine.alignToWeekStart(startDate, startDayOfWeek)
    val weekCount = inclusiveCalendarWeekCount(alignedStart, calculatedEndDate)

    val weeks = buildSimpleCalendarWeeks(alignedStart, weekCount, startDayOfWeek, safeDays, idProvider)
    val breakId = "break_${id}_${alignedStart}"
    val breakRunId = "run_cal_${idProvider.newId()}"
    val datedPlan = (schedulePlan ?: resolvedSchedulePlan()).copy(
        anchorDate = alignedStart.toString(),
        weekStartDay = startDayOfWeek,
        trainingDays = safeDays,
        mode = ScheduleMode.DATED,
        targetEndDate = calculatedEndDate.toString(),
    )

    return copy(
        structure = ProgramStructure.SIMPLE,
        timelineStartDate = alignedStart.toString(),
        calendarization = ProgramCalendarEngine.defaultSimpleDatedCalendarization().copy(
            manualEndDate = calculatedEndDate.toString(),
        ),
        simpleProgramKind = SimpleProgramKind.CALENDARIZED,
        pausedCyclicSnapshot = snapshot,
        loops = emptyList(),
        loopState = null,
        events = emptyList(),
        loopOccurrences = emptyList(),
        // Dedicated break run — never reuse the paused cyclic runId/cycle on workout logs.
        runState = ProgramRunState(
            runId = breakRunId,
            cycleNumber = 1,
            status = ProgramRunStatus.BREAK,
        ),
        startDay = startDayOfWeek,
        schedulePlan = datedPlan,
        calendarBreaks = calendarBreaks + CalendarBreak(
            id = breakId,
            title = "Break calendarizado",
            startDate = alignedStart.toString(),
            endDate = calculatedEndDate.toString(),
            weeks = weeks,
            pausedRunState = snapshot.runState,
            pausedCyclicSnapshot = snapshot,
        ),
        macrocycles = listOf(
            Macrocycle(
                id = idProvider.newId(),
                name = "Break calendarizado",
                blocks = listOf(
                    Block(
                        id = idProvider.newId(),
                        name = "Semanas calendarizadas",
                        mesocycles = listOf(
                            Mesocycle(
                                id = idProvider.newId(),
                                name = "Calendarizado",
                                goal = MesocycleGoal.ACCUMULATION,
                                weeks = weeks,
                            )
                        ),
                    )
                ),
            )
        ),
    )
}

/**
 * Calendariza el ciclo simple existente (mismas semanas/sesiones) sin crear un break.
 */
fun Program.calendarizeSimpleCycle(
    startDate: LocalDate,
    startDayOfWeek: Int,
    trainingDays: Set<Int>,
    idProvider: IdProvider = UuidIdProvider,
): Program {
    val safeDays = trainingDays.filter { it in 1..7 }.toSet().ifEmpty { suggestCalendarTrainingDays() }
    val weeks = macrocycles
        .flatMap { it.blocks }
        .flatMap { it.mesocycles }
        .flatMap { it.weeks }
        .filter { !it.isLoopWeek }
    if (weeks.isEmpty()) {
        return startSimpleCalendarizedBreak(startDate, null, startDayOfWeek, safeDays, idProvider)
    }

    val alignedStart = ProgramCalendarEngine.alignToWeekStart(startDate, startDayOfWeek)
    var cursor = alignedStart
    val datedMacrocycles = macrocycles.map { macro ->
        macro.copy(
            blocks = macro.blocks.map { block ->
                block.copy(
                    mesocycles = block.mesocycles.map { meso ->
                        meso.copy(
                            weeks = meso.weeks.map { week ->
                                if (week.isLoopWeek) week
                                else {
                                    val weekStart = cursor
                                    val weekEnd = weekStart.plusDays(6)
                                    val trainingDayDates = safeDays.associateWith { day ->
                                        val delta = ((day - startDayOfWeek) + 7) % 7
                                        weekStart.plusDays(delta.toLong()).toString()
                                    }
                                    cursor = weekEnd.plusDays(1)
                                    week.copy(
                                        startDate = weekStart.toString(),
                                        endDate = weekEnd.toString(),
                                        trainingDayDates = trainingDayDates,
                                    )
                                }
                            },
                        )
                    },
                )
            },
        )
    }

    val snapshot = pausedCyclicSnapshot ?: toSimpleProgramSnapshot()
    val projectedEnd = datedMacrocycles
        .flatMap { it.blocks }
        .flatMap { it.mesocycles }
        .flatMap { it.weeks }
        .mapNotNull { it.endDate }
        .maxOrNull()
    val breakId = "cal_${id}_$alignedStart"
    val breakRunId = "run_cal_${idProvider.newId()}"
    return copy(
        structure = ProgramStructure.SIMPLE,
        timelineStartDate = alignedStart.toString(),
        calendarization = ProgramCalendarEngine.defaultSimpleDatedCalendarization().copy(
            manualEndDate = projectedEnd,
        ),
        simpleProgramKind = SimpleProgramKind.CALENDARIZED,
        pausedCyclicSnapshot = snapshot,
        loops = emptyList(),
        loopState = null,
        events = emptyList(),
        loopOccurrences = emptyList(),
        runState = ProgramRunState(
            runId = breakRunId,
            cycleNumber = 1,
            status = ProgramRunStatus.BREAK,
        ),
        startDay = startDayOfWeek,
        schedulePlan = (schedulePlan ?: resolvedSchedulePlan()).copy(
            anchorDate = alignedStart.toString(),
            weekStartDay = startDayOfWeek,
            trainingDays = safeDays,
            mode = ScheduleMode.DATED,
            targetEndDate = projectedEnd,
        ),
        calendarBreaks = if (calendarBreaks.any { it.id == breakId }) {
            calendarBreaks
        } else {
            calendarBreaks + CalendarBreak(
                id = breakId,
                title = "Ciclo calendarizado",
                startDate = alignedStart.toString(),
                endDate = projectedEnd ?: alignedStart.toString(),
                weeks = datedMacrocycles.flatMap { it.blocks }.flatMap { it.mesocycles }.flatMap { it.weeks },
                pausedRunState = snapshot.runState,
                pausedCyclicSnapshot = snapshot,
            )
        },
        macrocycles = datedMacrocycles,
    )
}

fun Program.restorePausedCyclicProgram(): Program {
    val snapshot = pausedCyclicSnapshot ?: return LoopEngine.syncOccurrences(copy(
        calendarization = null,
        simpleProgramKind = SimpleProgramKind.CYCLIC,
        pausedCyclicSnapshot = null,
        timelineStartDate = null,
        runState = runState?.copy(status = ProgramRunStatus.ACTIVE),
        schedulePlan = schedulePlan?.copy(
            mode = ScheduleMode.FLOATING,
            anchorDate = null,
            targetEndDate = null,
        ),
    ))
    val restoredRun = (snapshot.runState ?: runState)?.copy(status = ProgramRunStatus.ACTIVE)
    return LoopEngine.syncOccurrences(copy(
        structure = ProgramStructure.SIMPLE,
        calendarization = null,
        simpleProgramKind = SimpleProgramKind.CYCLIC,
        timelineStartDate = null,
        macrocycles = snapshot.macrocycles,
        loops = snapshot.loops,
        loopState = snapshot.loopState,
        events = snapshot.events,
        selectedSplitId = snapshot.selectedSplitId,
        customSplitPattern = snapshot.customSplitPattern,
        customSplitName = snapshot.customSplitName,
        customSplitDescription = snapshot.customSplitDescription,
        blockSplitSelections = snapshot.blockSplitSelections,
        weekSplitSelections = snapshot.weekSplitSelections,
        runState = restoredRun,
        schedulePlan = snapshot.schedulePlan?.copy(mode = ScheduleMode.FLOATING, anchorDate = null, targetEndDate = null)
            ?: schedulePlan?.copy(mode = ScheduleMode.FLOATING, anchorDate = null, targetEndDate = null),
        loopOccurrences = snapshot.loopOccurrences,
        pausedCyclicSnapshot = null,
    ))
}

fun Program.startFreshSimpleCycle(
    idProvider: IdProvider = UuidIdProvider,
): Program {
    val freshRunId = "run_${idProvider.newId()}"
    return copy(
        structure = ProgramStructure.SIMPLE,
        calendarization = null,
        simpleProgramKind = SimpleProgramKind.CYCLIC,
        pausedCyclicSnapshot = null,
        timelineStartDate = null,
        loops = emptyList(),
        loopState = null,
        events = emptyList(),
        loopOccurrences = emptyList(),
        calendarBreaks = emptyList(),
        runState = ProgramRunState(
            runId = freshRunId,
            cycleNumber = 1,
            status = ProgramRunStatus.ACTIVE,
        ),
        schedulePlan = ProgramSchedulePlan(
            weekStartDay = startDay,
            mode = ScheduleMode.FLOATING,
        ),
        macrocycles = listOf(
            Macrocycle(
                id = idProvider.newId(),
                name = "Macrociclo base",
                blocks = listOf(
                    Block(
                        id = idProvider.newId(),
                        name = "Ciclo base",
                        mesocycles = listOf(
                            Mesocycle(
                                id = idProvider.newId(),
                                name = "Mesociclo 1",
                                goal = MesocycleGoal.ACCUMULATION,
                                weeks = listOf(
                                    ProgramWeek(
                                        id = idProvider.newId(),
                                        name = "Semana 1",
                                    )
                                ),
                            )
                        ),
                    )
                ),
            )
        ),
    )
}

fun Program.nextSimpleCalendarStart(
    today: LocalDate = SystemAppClock.today(java.time.ZoneId.systemDefault()),
): LocalDate {
    val lastEnd = macrocycles
        .flatMap { it.blocks }
        .flatMap { it.mesocycles }
        .flatMap { it.weeks }
        .mapNotNull { week -> week.endDate?.let(ProgramCalendarEngine::parseIsoDate) }
        .maxOrNull()
    return lastEnd?.plusDays(1) ?: resolvedSchedulePlan().anchorDate?.let(ProgramCalendarEngine::parseIsoDate) ?: today
}

fun Program.suggestCalendarTrainingDays(): Set<Int> {
    val daysFromDates = macrocycles
        .flatMap { it.blocks }
        .flatMap { it.mesocycles }
        .flatMap { it.weeks }
        .flatMap { it.trainingDayDates.keys }
        .filter { it in 1..7 }
        .toSet()
    if (daysFromDates.isNotEmpty()) return daysFromDates

    val daysFromSessions = macrocycles
        .flatMap { it.blocks }
        .flatMap { it.mesocycles }
        .flatMap { it.weeks }
        .flatMap { it.sessions }
        .mapNotNull { it.dayOfWeek?.takeIf { day -> day in 1..7 } }
        .toSet()
    return daysFromSessions.ifEmpty { setOf(1, 3, 5) }
}

internal fun buildSimpleCalendarWeeks(
    startDate: LocalDate,
    weekCount: Int,
    startDayOfWeek: Int,
    trainingDays: Set<Int>,
    idProvider: IdProvider = UuidIdProvider,
): List<ProgramWeek> {
    val startDayIsoValue = startDayOfWeek.coerceIn(1, 7)
    val alignedStart = ProgramCalendarEngine.alignToWeekStart(startDate, startDayIsoValue)
    return (0 until weekCount).map { index ->
        val weekStart = alignedStart.plusWeeks(index.toLong())
        val weekEnd = weekStart.plusDays(6)
        val trainingDayDates = trainingDays.associate { dayOfWeek ->
            val targetDayIsoValue = dayOfWeek
            val offset = ((targetDayIsoValue - startDayIsoValue + 7) % 7).toLong()
            val actualDate = weekStart.plusDays(offset)
            dayOfWeek to actualDate.toString()
        }
        ProgramWeek(
            id = idProvider.newId(),
            name = "Semana: ${weekStart.format(java.time.format.DateTimeFormatter.ofPattern("MM/dd", java.util.Locale.US))}",
            startDate = weekStart.toString(),
            endDate = weekEnd.toString(),
            trainingDayDates = trainingDayDates,
        )
    }
}

private fun inclusiveCalendarWeekCount(startDate: LocalDate, endDate: LocalDate): Int {
    val inclusiveDays = java.time.temporal.ChronoUnit.DAYS.between(startDate, endDate).coerceAtLeast(0) + 1
    return ((inclusiveDays + 6) / 7).toInt().coerceIn(1, 52)
}
