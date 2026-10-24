package com.example.kpkn.domain.relator

import com.example.kpkn.data.models.AutoregulationMode
import com.example.kpkn.data.models.BlockGoal
import com.example.kpkn.data.models.BlockProgressionScheme
import com.example.kpkn.data.models.PowerliftingProfile
import com.example.kpkn.data.models.ProgramGoals
import com.example.kpkn.data.models.ProgramMode
import com.example.kpkn.data.models.SetPurpose
import com.example.kpkn.data.models.TrainingPhase
import com.example.kpkn.data.protocols.AutoregulationHook
import com.example.kpkn.data.protocols.LoadBasis
import com.example.kpkn.data.protocols.LiftSlot
import com.example.kpkn.data.protocols.ProgressionRule
import com.example.kpkn.data.protocols.SlotRole
import com.example.kpkn.data.protocols.TechniqueModifier
import com.example.kpkn.data.protocols.TrainingPlanRecipe
import com.example.kpkn.domain.training.AmrapHit
import com.example.kpkn.domain.training.ProgramAutoregulationEngine

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
    val progressionContext: RelatorProgressionContext? = null,
)

/** Datos reales de la receta y del registro usados para describir su progresión en vivo. */
data class RelatorProgressionContext(
    val recipe: TrainingPlanRecipe? = null,
    val sourceProtocolId: String? = null,
    val profile: PowerliftingProfile? = null,
    val hit: AmrapHit? = null,
    /** Evidencia agregada de cierre; null mientras no se conoce el cierre del ciclo. */
    val tierCheck: ProgramAutoregulationEngine.GzclpTierCheck? = null,
    /** Racha anterior verificada de ciclos congelados; null si no se cargó esa evidencia. */
    val frozenCyclesBefore: Int? = null,
    val liftSlot: LiftSlot? = null,
    val slotRole: SlotRole? = null,
    val plannedVolumeReps: Int? = null,
    val plannedMinimumReps: Int? = null,
    val purpose: SetPurpose? = null,
    val isApreBlock: Boolean = false,
    val isLastAmrapOfExercise: Boolean? = null,
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
    /** Número (1-based) de la serie que toca del mismo ejercicio; null si ya no quedan. */
    val nextSetNumber: Int? = null,
    val nextSetTotal: Int? = null,
    val nextSetWeightKg: Double? = null,
    val nextSetReps: Int? = null,
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
    val sleepQuality: Int? = null,
    val stressLevel: Int? = null,
    val doms: Int? = null,
    val motivation: Int? = null,
    val preWorkoutDiscomforts: List<String> = emptyList(),
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

/**
 * Serie que acaba de registrarse (LR1h): lo que hizo la persona frente a lo previsto, y la serie que sigue del mismo
 * ejercicio. No sale de los borradores de la pantalla: durante el descanso el cursor ya está en la serie siguiente.
 */
data class RelatorLoggedSetSlice(
    val setNumber: Int? = null,
    val setTotal: Int? = null,
    val weightKg: Double? = null,
    val reps: Int? = null,
    val rir: Int? = null,
    val plannedWeightKg: Double? = null,
    val plannedReps: Int? = null,
    /** Máximo del rango de reps de la receta; sin rango, las reps previstas. */
    val plannedRepsMax: Int? = null,
    val plannedRir: Int? = null,
    /** Serie siguiente del mismo ejercicio (1-based); null si la registrada era la última. */
    val nextSetNumber: Int? = null,
    val nextSetTotal: Int? = null,
    val nextSetWeightKg: Double? = null,
    val nextSetReps: Int? = null,
)

data class RelatorContext(
    val sessionId: String,
    val setKey: String,
    val idleCycle: Int = 0,
    val feminine: Boolean = false,
    val visible: Boolean = true,
    /** Momento que el reloj ha decidido (LR1g). Null fuera del reloj: el motor usa todos los temas. */
    val moment: RelatorMoment? = null,
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
    /** Serie que acaba de registrarse (LR1h): lo que el relator dice al empezar el descanso. */
    val loggedSet: RelatorLoggedSetSlice = RelatorLoggedSetSlice(),
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
    /** Índice de la frase elegida dentro del candidato (LR1h): el selector lo recuerda para rotar la siguiente vez. */
    val variant: Int? = null,
)

data class RelatorEngineResult(
    val line: RelatorLine,
    val selectorState: RelatorSelectorState,
    val longTermMemory: RelatorLongTermMemory,
)

internal const val RELATOR_LINE_MAX_CHARS = 90

/** Kilos en una frase: «97,5 kg», con espacio inseparable antes de «kg» (LR1g). */
internal fun relatorKg(value: Double): String = "${formatRelatorKg(value)} kg"

/** Serie en una frase: «95 kg × 6» (LR1g). Sin reps, solo el peso. */
internal fun relatorSetMark(weightKg: Double, reps: Int): String =
    if (reps > 0) "${relatorKg(weightKg)} × $reps" else relatorKg(weightKg)

/** Porcentaje en una frase: «82 %», con espacio inseparable (LR1g). */
internal fun relatorPercent(value: Double): String = "${formatRelatorKg(value)} %"

/** Tiempo en una frase, como KpknSetFormat.doseTime: «3:15» o «45 s» (LR1g). */
internal fun relatorDoseTime(seconds: Int): String {
    val safe = seconds.coerceAtLeast(0)
    return if (safe < 60) {
        "$safe s"
    } else {
        "${safe / 60}:${(safe % 60).toString().padStart(2, '0')}"
    }
}

internal fun formatRelatorKg(value: Double): String {
    val scaled = kotlin.math.round(value * 10.0) / 10.0
    val asLong = scaled.toLong()
    return if (kotlin.math.abs(scaled - asLong) < 1e-6) asLong.toString() else scaled.toString().replace('.', ',')
}

internal fun RelatorContext.shortExerciseName(): String {
    val raw = exerciseName.trim()
    if (raw.isEmpty()) return "ejercicio"
    return raw.split(" · ").first().trim().ifBlank { "ejercicio" }
}
