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
    /** Append-only trail of weekly AUGE proposals (applied or pending). */
    val autoregulationAudit: List<AutoregulationAuditEntry> = emptyList(),
)

/** Progression decision attached to one exact native recipe exercise identity (§12.4). */
@Serializable
data class NativeProgressionIdentity(
    val recipeId: String,
    val recipeContentVersion: Int,
    /** H-IDENT: ya no forman parte de la identidad (vacíos): el mismo ejercicio sube junto en todos los días. */
    val recipeDayId: String = "",
    val recipeSlotId: String = "",
    val configurationId: String,
    val loadMode: LoadModeV2,
    val unitMode: UnitModeV2,
    /** "bilateral", "left", or "right"; never merges unilateral sides. */
    val side: String,
    val execution: String,
    val slotPurpose: String,
    val quantityConvention: LoadQuantityConvention = LoadQuantityConvention.UNSPECIFIED,
)

@Serializable
enum class NativeProgressionProposalKind {
    INCREASE_LOAD,
    REDUCE_LOAD,
    HARDER_BODYWEIGHT_VARIANT,
    EASIER_BODYWEIGHT_VARIANT,
}

/** Persisted proposal; the plan is unchanged until this is explicitly accepted. */
@Serializable
data class NativeProgressionProposal(
    val proposalId: String,
    val kind: NativeProgressionProposalKind,
    val identity: NativeProgressionIdentity,
    val sourceSessionId: String,
    val sourceLogIds: List<String>,
    val targetConfigurationId: String? = null,
    /** Null means select a load in the workout; it is never interpreted as 0 kg. */
    val targetLoadKg: Double? = null,
    val explanation: String,
    val createdAtMs: Long,
)

@Serializable
enum class NativeProgressionResolutionStatus {
    APPLIED,
    REJECTED,
    EXPIRED,

    /** Aviso informativo sin propuesta detrás (p. ej. «nuevo bloque» al continuar de ciclo, H-CICLO). */
    NOTICE,
}

/** Append-only terminal resolution, including idempotent rejection/expiry. */
@Serializable
data class NativeProgressionResolution(
    val proposalId: String,
    val status: NativeProgressionResolutionStatus,
    val kind: NativeProgressionProposalKind,
    val resolvedAtMs: Long,
    val reason: String,
    val userFacingNotice: Boolean = false,
    /** Proposal boundary prevents already-resolved exposures from triggering another prompt. */
    val identity: NativeProgressionIdentity? = null,
    val sourceLogIds: List<String> = emptyList(),
    /** Variante a la que pasó el ejercicio cuando se aceptó un cambio de variante corporal (H-IDENT). */
    val targetConfigurationId: String? = null,
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
    val proposals: List<AutoregulationProposal> = emptyList(),
    val targetWeekId: String? = null,
)

@Serializable
enum class PendingProgramActionType {
    /** Athlete must explicitly record or skip the realization/peak 1RM test. */
    CONFIRM_1RM_TEST,
    /** AUGE proposed a generated deload; it is not active until accepted. */
    CONFIRM_DELOAD,
    /** AUGE proposed weekly autoregulation; not applied until accepted. */
    CONFIRM_AUTOREGULATION,
}

@Serializable
enum class AutoregulationProposalKind {
    ADJUST_TM,
    SCALE_WEEK_INTENSITY,
    SCALE_WEEK_VOLUME,
    INSERT_DELOAD,
    DELAY_PEAK,
    PROMOTE_TM,
    SWAP_TO_TECHNIQUE_VARIANT,
}

@Serializable
data class AutoregulationProposal(
    val kind: AutoregulationProposalKind,
    val liftSlot: String? = null,
    val percentDelta: Double? = null,
    val volumeFactor: Double? = null,
    val explanation: String,
)

@Serializable
data class AutoregulationAuditEntry(
    val atMs: Long,
    val mode: AutoregulationMode,
    val kinds: List<AutoregulationProposalKind>,
    val weekId: String? = null,
    /**
     * Estado terminal de ESTA entrada (§14.5/AC-G4): null = propuestas
     * generadas y dejadas pendientes (entradas legacy). Toda acción pendiente
     * debe terminar aquí como aplicada/rechazada/expirada con motivo; una
     * propuesta sin entrada terminal sería una caída silenciosa.
     */
    val resolution: PendingActionResolutionStatus? = null,
    /** Motivo del estado terminal; vacío en las entradas de generación. */
    val resolutionReason: String = "",
)

/** Estado terminal de una acción/propuesta pendiente (§14.5, AC-G4). */
@Serializable
enum class PendingActionResolutionStatus {
    /** La propuesta produjo un efecto observable (receta/carga/bloque). */
    APPLIED,
    /** El atleta la rechazó explícitamente. */
    REJECTED,
    /** Sin efecto posible: semana entrenada, sin receta o caducada al avanzar/cerrar ciclo. */
    EXPIRED,
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
