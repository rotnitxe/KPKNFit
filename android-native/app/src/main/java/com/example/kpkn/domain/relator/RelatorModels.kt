package com.example.kpkn.domain.relator

import com.example.kpkn.data.models.AutoregulationMode
import com.example.kpkn.data.models.BlockGoal
import com.example.kpkn.data.models.BlockProgressionScheme
import com.example.kpkn.data.models.ProgramGoals
import com.example.kpkn.data.models.ProgramMode
import com.example.kpkn.data.models.TrainingPhase
import com.example.kpkn.data.protocols.AutoregulationHook
import com.example.kpkn.data.protocols.LoadBasis
import com.example.kpkn.data.protocols.ProgressionRule
import com.example.kpkn.data.protocols.SlotRole
import com.example.kpkn.data.protocols.TechniqueModifier

enum class RelatorSessionPhase {
    HIDDEN,
    MOBILITY,
    WARMUP,
    WORKING,
    REST,
}

enum class RelatorTopic {
    REACTION,
    STRUCTURE_ASSIST,
    PLAN,
    HISTORY,
    SESSION_PROGRESS,
    REST,
    READINESS,
    TECHNIQUE,
    MILESTONE,
    WARMUP,
    COACH,
    MEDIA,
    SITUATE,
}

data class RelatorActionSpec(
    val kind: String,
    val label: String,
    val exerciseId: String = "",
    val setIndex: Int = -1,
    val side: String = "",
    val mobilityId: String = "",
    val span: String = "",
    val weightKg: Double? = null,
    val restSeconds: Int? = null,
    val loadDeltaPercent: Double? = null,
)

data class RelatorCandidate(
    val topic: RelatorTopic,
    val priority: Int,
    val relevance: Double,
    val lines: List<String>,
    val fingerprintBase: String,
    val actions: List<RelatorActionSpec> = emptyList(),
    val openers: List<String> = emptyList(),
    val cooldownSets: Int = 0,
    val ttlSets: Int = 1,
    val isReaction: Boolean = false,
    val spokenConceptId: String? = null,
    val holdPrevious: Boolean = false,
    val phaseKey: String = topic.name.lowercase(),
)

data class RelatorPlanSlice(
    val sourceProtocolId: String? = null,
    val sourceProtocolName: String? = null,
    val mode: ProgramMode? = null,
    val trainingPhase: TrainingPhase? = null,
    val goals: ProgramGoals? = null,
    val autoregulationMode: AutoregulationMode = AutoregulationMode.OFF,
    val blockGoal: BlockGoal? = null,
    val blockProgressionScheme: BlockProgressionScheme? = null,
    val blockName: String? = null,
    val weekIndexInBlock: Int? = null,
    val weeksInBlock: Int? = null,
    val progression: ProgressionRule? = null,
    val autoregulationHooks: List<AutoregulationHook> = emptyList(),
)

data class RelatorHistorySet(
    val weightKg: Double,
    val reps: Int,
)

data class RelatorHistorySlice(
    val lastSessionSets: List<RelatorHistorySet> = emptyList(),
    val trendThreeSessionsKg: List<Double> = emptyList(),
    val bestEstimatedRmKg: Double = 0.0,
    val distanceToBestKg: Double? = null,
)

data class RelatorProgressSlice(
    val completedWorkingSets: Int = 0,
    val totalWorkingSets: Int = 0,
    val elapsedMinutes: Int = 0,
    val targetDurationMinutes: Int? = null,
    val remainingSeconds: Int? = null,
    val minutesAhead: Int? = null,
    val nextExerciseName: String? = null,
    val nextSuggestedKg: Double? = null,
)

data class RelatorRestSlice(
    val active: Boolean = false,
    val remainingSeconds: Int = 0,
    val plannedSeconds: Int = 0,
    val adaptiveSeconds: Int = 0,
    val justHitPr: Boolean = false,
    /** The relator ignores overlay vs card; this is never a gate. */
    val overlayMinimized: Boolean = false,
)

data class RelatorReadinessSlice(
    val dailyScore: Int? = null,
    val dailyLabel: String? = null,
    val dailyDetails: List<String> = emptyList(),
    val exerciseScore: Int? = null,
    val limitingFactor: String? = null,
    val sleepQuality: Int? = null,
    val stressLevel: Int? = null,
    val doms: Int? = null,
    val motivation: Int? = null,
    val preWorkoutDiscomforts: List<String> = emptyList(),
    val muscleDrainLabel: String? = null,
)

data class RelatorWarmupSlice(
    val incompleteIndex: Int? = null,
    val count: Int = 0,
    val isLastIncomplete: Boolean = false,
    val suggestedKg: Double? = null,
    val remainingCount: Int = 0,
)

data class RelatorCoachSlice(
    val key: String? = null,
    val title: String? = null,
    val body: String? = null,
    val action: String? = null,
)

data class RelatorMediaSlice(
    val previousCount: Int = 0,
    val previousExerciseName: String? = null,
)

data class RelatorMilestoneSlice(
    val prJustNow: Boolean = false,
    val isStar: Boolean = false,
    val estimatedRmKg: Double? = null,
    val goal1RmKg: Double? = null,
    val sessionVolumeRecord: Boolean = false,
    val bestTagName: String? = null,
)

data class RelatorContext(
    val sessionId: String,
    val setKey: String,
    val idleCycle: Int = 0,
    val feminine: Boolean = false,
    val visible: Boolean = true,
    val phase: RelatorSessionPhase = RelatorSessionPhase.WORKING,
    val exerciseId: String = "",
    val exerciseName: String = "",
    val setIndex: Int = 0,
    val setCount: Int = 1,
    val slotRole: SlotRole? = null,
    val isTopSet: Boolean = false,
    val loadBasis: LoadBasis? = null,
    val techniqueModifier: TechniqueModifier? = null,
    val isAmrap: Boolean = false,
    val isCompetitionLift: Boolean = false,
    val targetPercentageRm: Double? = null,
    val prescribedWeightKg: Double? = null,
    val suggestedWeightKg: Double? = null,
    val enteredWeightKg: Double? = null,
    val targetReps: Int? = null,
    val targetRpe: Double? = null,
    val targetRir: Int? = null,
    val executionCues: List<String> = emptyList(),
    val restAfterSeconds: Int? = null,
    val isTyping: Boolean = false,
    val userReacted: Boolean = false,
    val shownConceptIds: Set<String> = emptySet(),
    val conceptId: String? = null,
    val conceptLines: List<String> = emptyList(),
    val plan: RelatorPlanSlice = RelatorPlanSlice(),
    val history: RelatorHistorySlice = RelatorHistorySlice(),
    val progress: RelatorProgressSlice = RelatorProgressSlice(),
    val rest: RelatorRestSlice = RelatorRestSlice(),
    val readiness: RelatorReadinessSlice = RelatorReadinessSlice(),
    val warmup: RelatorWarmupSlice = RelatorWarmupSlice(),
    val coach: RelatorCoachSlice = RelatorCoachSlice(),
    val media: RelatorMediaSlice = RelatorMediaSlice(),
    val milestone: RelatorMilestoneSlice = RelatorMilestoneSlice(),
)

fun interface RelatorObserver {
    fun observe(context: RelatorContext): List<RelatorCandidate>
}

data class RelatorLine(
    val text: String?,
    val holdPrevious: Boolean = false,
    val phaseKey: String,
    val topic: RelatorTopic? = null,
    val fingerprint: String? = null,
    val spokenConceptId: String? = null,
    val actions: List<RelatorActionSpec> = emptyList(),
)

data class RelatorEngineResult(
    val line: RelatorLine,
    val selectorState: RelatorSelectorState,
    val longTermMemory: RelatorLongTermMemory,
)

internal const val RELATOR_LINE_MAX_CHARS = 140

internal fun formatRelatorKg(value: Double): String {
    val scaled = kotlin.math.round(value * 10.0) / 10.0
    val asLong = scaled.toLong()
    return if (kotlin.math.abs(scaled - asLong) < 1e-6) asLong.toString() else scaled.toString()
}

internal fun RelatorContext.shortExerciseName(): String {
    val raw = exerciseName.trim()
    if (raw.isEmpty()) return "ejercicio"
    return raw.split(" · ").first().trim().ifBlank { "ejercicio" }
}
