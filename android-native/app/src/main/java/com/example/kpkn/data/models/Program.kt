package com.example.kpkn.data.models

import kotlinx.serialization.Serializable
import java.time.LocalDate
import com.example.kpkn.domain.training.ProgramCalendarEngine

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
    val savedAtMs: Long = 0L,
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
data class Block(
    val id: String,
    val name: String,
    val description: String? = null,
    val mesocycles: List<Mesocycle> = emptyList(),
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
)

enum class WeekVariant { A, B, C, D }

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

val Program.isSimpleTemporalProgram: Boolean
    get() = macrocycles.size == 1 && totalBlockCount == 1

val Program.isSimpleCalendarizedProgram: Boolean
    get() = isSimpleTemporalProgram && simpleProgramKind == SimpleProgramKind.CALENDARIZED

val Program.simpleCycleWeeks: Int?
    get() = if (isSimpleTemporalProgram) totalProgramWeeks else null

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

fun Program.normalizedTemporalStructure(): Program {
    val shouldBeSimple = isSimpleTemporalProgram
    val normalizedSimpleKind = when {
        !shouldBeSimple -> SimpleProgramKind.CYCLIC
        calendarization?.mode == ProgramCalendarizationMode.SIMPLE_DATED && !timelineStartDate.isNullOrBlank() ->
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
                                if (week.isLoopWeek && !shouldBeSimple) week.copy(isLoopWeek = false, loopId = null)
                                else week
                            }
                        )
                    }
                )
            }
        )
    }
    return copy(
        structure = if (shouldBeSimple) ProgramStructure.SIMPLE else ProgramStructure.COMPLEX,
        simpleProgramKind = normalizedSimpleKind,
        loops = if (shouldBeSimple && normalizedSimpleKind == SimpleProgramKind.CYCLIC) loops else emptyList(),
        loopState = if (shouldBeSimple && normalizedSimpleKind == SimpleProgramKind.CYCLIC) loopState else null,
        events = if (shouldBeSimple && normalizedSimpleKind == SimpleProgramKind.CYCLIC) events else emptyList(),
        pausedCyclicSnapshot = if (shouldBeSimple) pausedCyclicSnapshot else null,
        macrocycles = cleanMacrocycles,
    )
}

fun Program.toSimpleProgramSnapshot(): SimpleProgramSnapshot =
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

fun Program.startSimpleCalendarizedBreak(
    startDate: LocalDate,
    endDate: LocalDate?,
    startDayOfWeek: Int,
    trainingDays: Set<Int>,
): Program {
    val safeDays = trainingDays.filter { it in 1..7 }.toSet().ifEmpty { suggestCalendarTrainingDays() }
    val snapshot = pausedCyclicSnapshot ?: toSimpleProgramSnapshot()

    val calculatedEndDate = endDate ?: startDate.plusWeeks(3).plusDays(6)
    val weekCount = java.time.temporal.ChronoUnit.WEEKS.between(startDate, calculatedEndDate).toInt().coerceIn(1, 52)

    val weeks = buildSimpleCalendarWeeks(startDate, weekCount, startDayOfWeek, safeDays)

    return copy(
        structure = ProgramStructure.SIMPLE,
        timelineStartDate = startDate.toString(),
        calendarization = ProgramCalendarEngine.defaultSimpleDatedCalendarization(),
        simpleProgramKind = SimpleProgramKind.CALENDARIZED,
        pausedCyclicSnapshot = snapshot,
        loops = emptyList(),
        loopState = null,
        events = emptyList(),
        startDay = startDayOfWeek,
        macrocycles = listOf(
            Macrocycle(
                id = "macro_calendarized_${System.nanoTime()}",
                name = "Break calendarizado",
                blocks = listOf(
                    Block(
                        id = "block_calendarized_${System.nanoTime()}",
                        name = "Semanas calendarizadas",
                        mesocycles = listOf(
                            Mesocycle(
                                id = "meso_calendarized_${System.nanoTime()}",
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

fun Program.restorePausedCyclicProgram(): Program {
    val snapshot = pausedCyclicSnapshot ?: return copy(
        calendarization = null,
        simpleProgramKind = SimpleProgramKind.CYCLIC,
        pausedCyclicSnapshot = null,
    )
    return copy(
        structure = ProgramStructure.SIMPLE,
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
}

fun Program.startFreshSimpleCycle(): Program {
    return copy(
        structure = ProgramStructure.SIMPLE,
        calendarization = null,
        simpleProgramKind = SimpleProgramKind.CYCLIC,
        pausedCyclicSnapshot = null,
        loops = emptyList(),
        loopState = null,
        events = emptyList(),
        macrocycles = listOf(
            Macrocycle(
                id = "macro_simple_${System.nanoTime()}",
                name = "Macrociclo base",
                blocks = listOf(
                    Block(
                        id = "block_simple_${System.nanoTime()}",
                        name = "Ciclo base",
                        mesocycles = listOf(
                            Mesocycle(
                                id = "meso_simple_${System.nanoTime()}",
                                name = "Mesociclo 1",
                                goal = MesocycleGoal.ACCUMULATION,
                                weeks = listOf(
                                    ProgramWeek(
                                        id = "week_simple_${System.nanoTime()}",
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

fun Program.nextSimpleCalendarStart(): LocalDate {
    val lastEnd = macrocycles
        .flatMap { it.blocks }
        .flatMap { it.mesocycles }
        .flatMap { it.weeks }
        .mapNotNull { week -> week.endDate?.let { java.time.LocalDate.parse(it) } }
        .maxOrNull()
    return lastEnd?.plusDays(1) ?: timelineStartDate?.let { java.time.LocalDate.parse(it) } ?: LocalDate.now()
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
): List<ProgramWeek> {
    val startDayIsoValue = when (startDayOfWeek) {
        1 -> 1
        2 -> 2
        3 -> 3
        4 -> 4
        5 -> 5
        6 -> 6
        7 -> 7
        else -> 1
    }
    return (0 until weekCount).map { index ->
        val weekStart = startDate.plusWeeks(index.toLong())
        val weekEnd = weekStart.plusDays(6)
        val trainingDayDates = trainingDays.associate { dayOfWeek ->
            val targetDayIsoValue = dayOfWeek
            val offset = ((targetDayIsoValue - startDayIsoValue + 7) % 7).toLong()
            val actualDate = weekStart.plusDays(offset)
            dayOfWeek to actualDate.toString()
        }
        ProgramWeek(
            id = java.util.UUID.randomUUID().toString(),
            name = "Semana: ${weekStart.format(java.time.format.DateTimeFormatter.ofPattern("MM/dd", java.util.Locale.US))}",
            startDate = weekStart.toString(),
            endDate = weekEnd.toString(),
            trainingDayDates = trainingDayDates,
        )
    }
}
