package com.example.kpkn.data.protocols

import com.example.kpkn.data.models.BlockGoal
import com.example.kpkn.data.models.WeekExecutionKind
import com.example.kpkn.data.programs.DaySlotTemplate
import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable

@Serializable
enum class LiftSlot { SQUAT, BENCH, DEADLIFT, OVERHEAD }

@Serializable
enum class SlotRole { T1_MAIN, T2_SUPPLEMENTAL, T3_ACCESSORY, SPEED, TECHNIQUE }

@Serializable
enum class SlotPriority { NORMAL, SPEED }

@Serializable
enum class SlotSource { AUTHOR, KPKN_DEFAULT }

@Serializable
enum class LoadBasis {
    PERCENT_TM,
    PERCENT_1RM,
    PERCENT_DESIRED_MAX,
    PERCENT_OF_TOP_SET,
    RPE,
    REP_MAX,
}

@Serializable
enum class TechniqueModifier {
    PAUSE_2S,
    TEMPO_3_0_3,
    CLOSE_GRIP,
    GRIP_SUPINATED,
    GRIP_NEUTRAL,
    GRIP_WIDE,
    UNILATERAL_EXECUTION,
    BOX,
    PIN,
    DEFICIT,
    SPEED,
    CHAINS_BANDS,
    TOUCH_AND_GO,
    DEAD_STOP,
    TO_KNEES,
    BLOCK_PULL,
    PRE_EXHAUST,
}

@Serializable
enum class CompositionSeverity { HARD, SOFT }

@Serializable
data class RecipeCompositionExemption(
    val rule: String,
    val scope: String,
    val justification: String,
    val sourceUrl: String? = null,
)

@Serializable
data class LiftRef(
    val configurationId: String,
    val liftSlot: LiftSlot? = null,
)

@Serializable
data class SetRecipe(
    val reps: Int? = null,
    val repsMin: Int? = null,
    val repsMax: Int? = null,
    val percent: Double? = null,
    val rpe: Double? = null,
    val rir: Int? = null,
    val amrap: Boolean = false,
    val isTopSet: Boolean = false,
    val loadBasis: LoadBasis = LoadBasis.PERCENT_TM,
    /** No cuenta como serie efectiva en H5b/H6/H8/W2. */
    val isWarmup: Boolean = false,
)

@Serializable
data class SlotRecipe(
    val id: String,
    val role: SlotRole,
    val lift: LiftRef,
    val sets: List<SetRecipe>,
    val restSeconds: Int,
    val technique: TechniqueModifier? = null,
    val supplementalOf: String? = null,
    val isUnilateral: Boolean = false,
    val priority: SlotPriority = SlotPriority.NORMAL,
    val source: SlotSource = SlotSource.AUTHOR,
    val isCompetitionLift: Boolean = false,
    val targetGroupOverride: String? = null,
)

@Serializable
data class DayRecipe(
    val label: String,
    val slots: List<SlotRecipe>,
    val archetype: DaySlotTemplate? = null,
    val priority: SlotPriority = SlotPriority.NORMAL,
    /** 1-7 ISO weekday; si es null, W3 asume un descanso entre días listados. */
    val weekday: Int? = null,
)

@Serializable
data class WeekRecipe(
    val weekNumber: Int,
    val blockIndex: Int,
    val blockName: String = "",
    val blockGoal: BlockGoal = BlockGoal.ACCUMULATION,
    val kind: WeekExecutionKind = WeekExecutionKind.TRAINING,
    val days: List<DayRecipe>,
)

@Serializable
sealed class ProgressionRule {
    @Serializable
    @SerialName("none")
    data object None : ProgressionRule()

    @Serializable
    @SerialName("cycle_increment")
    data class CycleIncrement(val upperKg: Double, val lowerKg: Double) : ProgressionRule()

    @Serializable
    @SerialName("amrap_driven_tm")
    data class AmrapDrivenTm(
        val zeroToOneKg: Double = 0.0,
        val twoToThreeKg: Double = 2.5,
        val fourToFiveKg: Double = 5.0,
        val sixPlusKg: Double = 7.5,
    ) : ProgressionRule()

    @Serializable
    @SerialName("weekly_percent")
    data class WeeklyPercent(val increment: Double) : ProgressionRule()

    @Serializable
    @SerialName("weekly_kg")
    data class WeeklyKg(val weekToKg: Map<Int, Double> = emptyMap()) : ProgressionRule()

    @Serializable
    @SerialName("rep_target_driven_tm")
    data class RepTargetDrivenTm(
        val extraRepPercent: Double = 0.5,
        val missedRepPercent: Double = 1.0,
        val missThreshold: Int = 2,
    ) : ProgressionRule()

    @Serializable
    @SerialName("rep_max_autoregulated")
    data object RepMaxAutoregulated : ProgressionRule()

    @Serializable
    @SerialName("top_set_pr")
    data object TopSetPr : ProgressionRule()
}

@Serializable
enum class AutoregulationHookKind { AMRAP_TM, RPE_CAP, WEEKLY_REVIEW }

@Serializable
data class AutoregulationHook(
    val kind: AutoregulationHookKind,
    val note: String = "",
)

@Serializable
data class ProtocolFidelitySpec(
    val expectedWeeks: Int,
    val expectedDaysPerWeek: Int,
    val requiresAmrap: Boolean = false,
    val requiresRpe: Boolean = false,
    val requiresPercent: Boolean = false,
    val claimedLevel: String? = null,
    val percentAnchors: Map<String, List<Double>> = emptyMap(),
)

@Serializable
data class TrainingPlanRecipe(
    val id: String,
    val weeks: List<WeekRecipe>,
    val trainingMaxPercent: Double = 0.90,
    val liftSlots: Map<LiftSlot, String> = emptyMap(),
    val progression: ProgressionRule = ProgressionRule.None,
    val exemptions: List<RecipeCompositionExemption> = emptyList(),
    val autoregulationHooks: List<AutoregulationHook> = emptyList(),
    val claimedDaysPerWeek: Int? = null,
    val claimedLevel: String? = null,
) {
    val daysPerWeek: Int get() = claimedDaysPerWeek ?: weeks.maxOfOrNull { it.days.size } ?: 0
}

fun TechniqueModifier.displayName(): String = when (this) {
    TechniqueModifier.PAUSE_2S -> "Pausa 2s"
    TechniqueModifier.TEMPO_3_0_3 -> "Tempo 3-0-3"
    TechniqueModifier.CLOSE_GRIP -> "Agarre cerrado"
    TechniqueModifier.GRIP_SUPINATED -> "Agarre supino"
    TechniqueModifier.GRIP_NEUTRAL -> "Agarre neutro"
    TechniqueModifier.GRIP_WIDE -> "Agarre ancho"
    TechniqueModifier.UNILATERAL_EXECUTION -> "Unilateral"
    TechniqueModifier.BOX -> "Cajón"
    TechniqueModifier.PIN -> "Pines"
    TechniqueModifier.DEFICIT -> "Déficit"
    TechniqueModifier.SPEED -> "Velocidad"
    TechniqueModifier.CHAINS_BANDS -> "Cadenas/bandas"
    TechniqueModifier.TOUCH_AND_GO -> "Touch and go"
    TechniqueModifier.DEAD_STOP -> "Parada muerta"
    TechniqueModifier.TO_KNEES -> "Hasta rodillas"
    TechniqueModifier.BLOCK_PULL -> "Desde tacos"
    TechniqueModifier.PRE_EXHAUST -> "Pre-agotamiento"
}

fun TechniqueModifier.executionCue(): String = when (this) {
    TechniqueModifier.PAUSE_2S -> "Pausa de dos segundos en la posición más baja antes de subir."
    TechniqueModifier.TEMPO_3_0_3 -> "Tres segundos de bajada, sin rebote, tres segundos de subida."
    TechniqueModifier.CLOSE_GRIP -> "Agarre cerrado, antebrazos verticales sobre la barra."
    TechniqueModifier.GRIP_SUPINATED -> "Agarre supino."
    TechniqueModifier.GRIP_NEUTRAL -> "Agarre neutro."
    TechniqueModifier.GRIP_WIDE -> "Agarre más ancho que el de competición."
    TechniqueModifier.UNILATERAL_EXECUTION -> "Ejecutar un lado cada vez."
    TechniqueModifier.BOX -> "Sentarse controlado al cajón y arrancar sin rebote."
    TechniqueModifier.PIN -> "Arranque muerto desde los pines."
    TechniqueModifier.DEFICIT -> "Pies elevados; el recorrido empieza más bajo."
    TechniqueModifier.SPEED -> "Barras rápidas, acelerar al máximo sin perder posiciones."
    TechniqueModifier.CHAINS_BANDS -> "La resistencia aumenta hacia el bloqueo."
    TechniqueModifier.TOUCH_AND_GO -> "Contacto breve, sin pausa ni rebote elástico."
    TechniqueModifier.DEAD_STOP -> "Cada repetición arranca desde parado."
    TechniqueModifier.TO_KNEES -> "Tirar solo hasta la rodilla y bajar controlado."
    TechniqueModifier.BLOCK_PULL -> "La barra arranca desde tacos/bloques."
    TechniqueModifier.PRE_EXHAUST -> "Aislamiento antes del compuesto del mismo músculo."
}
