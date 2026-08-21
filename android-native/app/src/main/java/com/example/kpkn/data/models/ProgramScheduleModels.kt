package com.example.kpkn.data.models

import kotlinx.serialization.Serializable

/** Modo de programación temporal: flotante (cíclico) o anclado a fechas. */
enum class ScheduleMode { FLOATING, DATED }

/**
 * Plan de calendarización separado de la estructura macrociclo/bloque.
 * Consolida ancla, límite semanal, días de entrenamiento y término objetivo.
 */
@Serializable
data class ProgramSchedulePlan(
    val anchorDate: String? = null,
    val weekStartDay: Int? = null,
    val trainingDays: Set<Int> = emptySet(),
    val targetEndDate: String? = null,
    val mode: ScheduleMode = ScheduleMode.FLOATING,
)

/** Semanas excepcionales (break) separadas de la rutina base. */
@Serializable
data class CalendarBreak(
    val id: String,
    val title: String,
    val startDate: String,
    val endDate: String,
    val weeks: List<ProgramWeek> = emptyList(),
    val pausedRunState: ProgramRunState? = null,
    val pausedCyclicSnapshot: SimpleProgramSnapshot? = null,
)

/** Estado de ejecución activa de un programa (run). */
@Serializable
data class ProgramRunState(
    val runId: String,
    val cycleNumber: Int = 1,
    val weekInstanceId: String? = null,
    val weekId: String? = null,
    val macrocycleId: String? = null,
    val blockId: String? = null,
    val mesocycleId: String? = null,
    val completedSessionIds: Set<String> = emptySet(),
    val status: ProgramRunStatus = ProgramRunStatus.ACTIVE,
    /** A mandatory human decision; COMPLEX progression must not skip it. */
    val pendingAction: PendingProgramAction? = null,
    /** Latest explicit 1RM resolution; never inferred from confirmation alone. */
    val oneRmResolution: OneRmResolution? = null,
    /** Append-only audit trail for recorded/skipped gate decisions. */
    val oneRmAuditTrail: List<OneRmResolution> = emptyList(),
)

enum class ProgramRunStatus { ACTIVE, PAUSED, BREAK, COMPLETED }

@Serializable
enum class OneRmResolutionStatus { RECORDED, SKIPPED }

@Serializable
data class OneRmResolution(
    val status: OneRmResolutionStatus,
    val squat1RM: Double? = null,
    val bench1RM: Double? = null,
    val deadlift1RM: Double? = null,
    val resolvedAtMs: Long = System.currentTimeMillis(),
    val note: String? = null,
)

@Serializable
data class PendingProgramAction(
    val type: PendingProgramActionType,
    val message: String,
    val nextBlockId: String? = null,
)

@Serializable
enum class PendingProgramActionType {
    /** Athlete must explicitly record or skip the realization/peak 1RM test. */
    CONFIRM_1RM_TEST,
    /** AUGE proposed a generated deload; it is not active until accepted. */
    CONFIRM_DELOAD,
}

/** Instancia concreta de una regla de loop con estado de ciclo. */
@Serializable
data class LoopOccurrence(
    val id: String,
    val loopId: String,
    val cycleNumber: Int,
    val scheduledCycle: Int,
    val status: LoopStatus = LoopStatus.SCHEDULED,
    val weekInstanceId: String? = null,
    val postponedToCycle: Int? = null,
    /** Ciclo original antes de posponer; null = coincide con [scheduledCycle]. */
    val originalScheduledCycle: Int? = null,
) {
    val originCycle: Int get() = originalScheduledCycle ?: scheduledCycle
}

/** Incompatibilidades de estructura temporal sin reclasificación automática. */
enum class TemporalStructureIssueType {
    SIMPLE_MULTIPLE_BLOCKS,
    SIMPLE_MULTIPLE_MACROCYCLES,
    COMPLEX_MISSING_STRUCTURE,
    CALENDARIZED_WITH_LOOPS,
    LOOP_INCONSISTENCY,
    INVALID_TRAINING_DAYS,
}

@Serializable
data class TemporalStructureIssue(
    val type: TemporalStructureIssueType,
    val message: String,
)
