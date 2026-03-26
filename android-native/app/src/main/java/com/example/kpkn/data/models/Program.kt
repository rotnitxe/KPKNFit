package com.example.kpkn.data.models

import kotlinx.serialization.Serializable

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
    val splitTrialSeen: Boolean = false,
    val isDraft: Boolean = false,
)

enum class ProgramMode { POWERLIFTING, HYPERTROPHY, POWERBUILDING }
enum class ProgramStructure { SIMPLE, COMPLEX }
enum class TrainingPhase { ACCUMULATION, TRANSFORMATION, REALIZATION }
enum class VolumeSystem { ISRAETEL, KPNK, MANUAL }

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
    val sessions: List<Session> = emptyList(),
    val variant: WeekVariant? = null,
    val isLoopWeek: Boolean = false,
    val loopId: String? = null,
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
