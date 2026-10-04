package com.example.kpkn.data.protocols

import com.example.kpkn.data.models.BlockGoal
import com.example.kpkn.data.models.CardioDetails
import com.example.kpkn.data.models.LoadQuantityConvention
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

/** Tipo de referencia de carga explícita (§14.1/§14.2). */
@Serializable
enum class PlanLoadReferenceKind {
    /** 1RM del mismo ejercicio/configuración. `reference1RM` sigue significando 1RM: aquí NUNCA se guarda una carga de trabajo de 3–5 reps. */
    EXERCISE_1RM,
    /** Training max (p. ej. 90 % de 1RM) del mismo ejercicio/configuración. */
    EXERCISE_TM,
    /** Última serie de trabajo completada en un rango de reps (p. ej. 3–5) del MISMO slot/configuración. */
    OBSERVED_WORKING_SET,
    /** Lastre/asistencia externa declarada; convención distinta de la carga externa total. */
    BODYWEIGHT_EXTERNAL,
}

/** Estado de resolución de una [PlanLoadReference]: pendiente de resolver o ya capturada. */
@Serializable
enum class PlanLoadReferenceState {
    /** Sin registro todavía: el peso se elige en entrenamiento (null ≠ 0 kg). */
    PENDING,
    /** Snapshot de la carga elegida conservado en [PlanLoadReference.capturedLoadKg]. */
    CAPTURED,
}

/**
 * Referencia de carga explícita de una receta/programa (§14.1/§14.2).
 *
 * Reglas semánticas (§14.2):
 * - Solo aplica a la MISMA [configurationId] y la MISMA [quantityConvention];
 *   nunca se transfiere barra → mancuernas ni por LiftSlot de otra variante.
 * - Una carga de trabajo de 3–5 reps se guarda aquí como [PlanLoadReferenceKind.OBSERVED_WORKING_SET]
 *   con [repMin]/[repMax]; JAMÁS en `Exercise.reference1RM`, que sigue significando 1RM.
 * - `SlotRecipe.targetGroupOverride` no es un canal oculto de base de carga.
 * - Sin referencia → peso pendiente, no cero ficticio ni NaN.
 */
@Serializable
data class PlanLoadReference(
    val kind: PlanLoadReferenceKind,
    val configurationId: String,
    val quantityConvention: LoadQuantityConvention = LoadQuantityConvention.UNSPECIFIED,
    /** Slot de origen para trabajo observado (día pesado enlazado, p. ej. PHAT §14.2). */
    val sourceSlotId: String? = null,
    /** Reps mínimas del trabajo observado (p. ej. 3); null en 1RM/TM. */
    val repMin: Int? = null,
    /** Reps máximas del trabajo observado (p. ej. 5); null en 1RM/TM. */
    val repMax: Int? = null,
    /** Side whose manual first load is pending/captured; null for bilateral or legacy references. */
    val side: String? = null,
    /** Procedencia cuando la referencia viene de otro snapshot. */
    val sourceProgramId: String? = null,
    val sourceRunId: String? = null,
    val sourceWeekOccurrence: Int? = null,
    val state: PlanLoadReferenceState = PlanLoadReferenceState.PENDING,
    /** Snapshot de la carga elegida cuando [state] es [PlanLoadReferenceState.CAPTURED]. */
    val capturedLoadKg: Double? = null,
    val capturedAtMs: Long? = null,
)

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
    /**
     * Referencia de carga explícita del set (§14.1/§14.2). Cuando existe,
     * prevalece sobre la resolución legacy del porcentaje: [percent] conserva
     * la magnitud, pero su base visible la determina esta referencia, no
     * `PERCENT_TM` por default accidental. Solo aplica a la MISMA configuración
     * y convención; una carga 3–5 reps va aquí (OBSERVED_WORKING_SET), nunca en
     * `reference1RM`. null = resolución legacy sin cambios.
     */
    val reference: PlanLoadReference? = null,
)

/**
 * Intención editorial de un slot según el vocabulario de §11.2.
 * El JSON canónico usa la notación de la tabla: `F`, `Fv`, `H`, `I`, `C`, `P`.
 */
@Serializable
enum class SlotIntent {
    /** F: principal de fuerza. */
    F,
    /** Fv: práctica/volumen del principal. */
    @SerialName("Fv")
    FV,
    /** H: compuesto muscular. */
    H,
    /** I: aislamiento. */
    I,
    /** C: core. */
    C,
    /** P: potencia/SPEED. */
    P,
}

/**
 * Rango de series publicado por el autor (p. ej. `3–4×8–12`). Los sets
 * concretos de [SlotRecipe.sets] siguen siendo la prescripción ejecutable;
 * este rango documenta lo publicado para elegir dentro de él.
 */
@Serializable
data class AuthoredSetRange(
    val min: Int,
    val max: Int,
) {
    init {
        require(min > 0) { "AuthoredSetRange.min must be positive" }
        require(max >= min) { "AuthoredSetRange.max must be >= min" }
    }
}

/**
 * Metadatos de referencia explícita del slot (§14.1/§14.2): la carga de este
 * slot se resuelve contra [configurationId] y [quantityConvention], nunca por
 * LiftSlot de otra variante ni por `targetGroupOverride`.
 */
@Serializable
data class SlotLoadReferenceMetadata(
    val configurationId: String? = null,
    val quantityConvention: LoadQuantityConvention = LoadQuantityConvention.UNSPECIFIED,
    /** Reps del trabajo observado al que aplica (p. ej. 3..5 para SPEED de PHAT). */
    val repMin: Int? = null,
    val repMax: Int? = null,
    val note: String = "",
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
    /**
     * Override de grupo muscular para orden/prioridad. NO es un canal oculto
     * de base de carga (§14.2): la base de carga va en [SetRecipe.reference].
     */
    val targetGroupOverride: String? = null,
    /** Intención F/Fv/H/I/C/P (§11.2); null = receta legacy sin declarar. */
    val intent: SlotIntent? = null,
    /** Rango de series publicado por el autor; null = no declarado. */
    val authoredSetRange: AuthoredSetRange? = null,
    /** Referencia explícita del slot; null = resolución legacy sin cambios. */
    val explicitReference: SlotLoadReferenceMetadata? = null,
)

/** Tipo de sesión que declara un [DayRecipe] (dispatch de MIXED_CARDIO, §14.3). */
@Serializable
enum class RecipeSessionKind {
    /** Solo trabajo de resistencia. */
    STRENGTH,
    /** Resistencia + bloque(s) de cardio en la misma sesión. */
    STRENGTH_CARDIO,
    /** Cardio real + accesorios esenciales declarados (p. ej. día dedicado con U/C de §11.4). */
    CARDIO_ACCESSORY,
    /** Día solo cardio: cero slots de resistencia y cardio real. */
    CARDIO,
}

/**
 * Mínimos de composición declarados por un día (§14.3 NATIVE_COMPACT).
 * Defaults = suelo de un día STRENGTH: 2 configuraciones distintas y 4 series
 * de resistencia ordinaria. `essentialSlotIds` lista slots que el fitter no
 * puede eliminar. SPEED y calentamientos no rellenan artificialmente este mínimo.
 */
@Serializable
data class DayMinimumDose(
    val minDistinctConfigurations: Int = 2,
    val minResistanceSets: Int = 4,
    val essentialSlotIds: List<String> = emptyList(),
)

/** Posición del bloque de cardio respecto del trabajo de fuerza del día (§14.1). */
@Serializable
enum class RecipeCardioPosition {
    /** Cardio primero; cubre días dedicados con accesorios posteriores (§11.4). */
    BEFORE_STRENGTH,
    /** Cardio después de la resistencia (default). */
    AFTER_STRENGTH,
    /** Día solo cardio. */
    ONLY,
}

/**
 * Progresión propia de un bloque de cardio (§12.4): escalones ofertados en
 * minutos (p. ej. 10→15→20→30); nunca se inventan valores fuera de la UI.
 */
@Serializable
data class RecipeCardioProgression(
    /** Duraciones ofertadas en minutos; vacío = sin escalones automáticos. */
    val offeredDurationsMinutes: List<Int> = emptyList(),
    /** Al alcanzar esta duración no se proponen más incrementos automáticos. */
    val stopAtMinutes: Int? = null,
    val note: String = "",
)

/**
 * Bloque de cardio dentro de un [DayRecipe] (§14.1). Reutiliza [CardioDetails]
 * (duración/intensidad/modalidad reales); NO crea un segundo catálogo de
 * CardioType. La posición se proyecta al orden real de `Session.parts`/`cardioFirst`.
 */
@Serializable
data class RecipeCardioBlock(
    /** ID estable dentro de la receta (identidad §14.4). */
    val id: String,
    val details: CardioDetails,
    val position: RecipeCardioPosition = RecipeCardioPosition.AFTER_STRENGTH,
    /** Propósito editorial (p. ej. «cardio continuo», «día dedicado»). */
    val purpose: String = "",
    /** Progresión propia del bloque; null = sin escalón automático. */
    val progression: RecipeCardioProgression? = null,
)

@Serializable
data class DayRecipe(
    val label: String,
    val slots: List<SlotRecipe>,
    val archetype: DaySlotTemplate? = null,
    val priority: SlotPriority = SlotPriority.NORMAL,
    /** 1-7 ISO weekday; si es null, W3 asume un descanso entre días listados. */
    val weekday: Int? = null,
    /** Identidad estable del día en recetas nuevas (§14.4); null = receta legacy. */
    val id: String? = null,
    /** Bloques de cardio del día; vacío = solo resistencia (payloads viejos sin cambios). */
    val cardioBlocks: List<RecipeCardioBlock> = emptyList(),
    val sessionKind: RecipeSessionKind = RecipeSessionKind.STRENGTH,
    /** Mínimos declarados; null = perfil legacy (H6 estándar, §14.3). */
    val minimumDose: DayMinimumDose? = null,
)

@Serializable
data class WeekRecipe(
    val weekNumber: Int,
    val blockIndex: Int,
    val blockName: String = "",
    val blockGoal: BlockGoal = BlockGoal.ACCUMULATION,
    val kind: WeekExecutionKind = WeekExecutionKind.TRAINING,
    val days: List<DayRecipe>,
    /** Etiqueta del autor para el microciclo (p. ej. 5s / 3s / 1s). Vacío → "Semana N". */
    val weekName: String = "",
)

/**
 * Cuándo sube el TM una [ProgressionRule.CycleIncrement]: al cerrar el ciclo del programa
 * ([CYCLE], p. ej. 5/3/1 cada cuatro semanas) o al entrar en el bloque siguiente ([BLOCK],
 * p. ej. Juggernaut tras cada ola).
 */
@Serializable
enum class IncrementScope { CYCLE, BLOCK }

@Serializable
sealed class ProgressionRule {
    @Serializable
    @SerialName("none")
    data object None : ProgressionRule()

    /**
     * Subida de TM del método: [upperKg] para banca y press militar, [lowerKg] para sentadilla y
     * peso muerto, aplicada al cerrar el ciclo o el bloque según [scope]. El JSON anterior sin
     * `scope` decodifica con [IncrementScope.CYCLE].
     */
    @Serializable
    @SerialName("cycle_increment")
    data class CycleIncrement(
        val upperKg: Double,
        val lowerKg: Double,
        val scope: IncrementScope = IncrementScope.CYCLE,
    ) : ProgressionRule()

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

    /**
     * Récord del top set: sube [upperKg] (banca, press militar) o [lowerKg] (sentadilla, peso
     * muerto) cuando el top set supera su objetivo. El JSON anterior `{"type":"top_set_pr"}`
     * decodifica con los valores por defecto. Su consumidor llega con B.S4.
     */
    @Serializable
    @SerialName("top_set_pr")
    data class TopSetPr(
        val upperKg: Double = 1.25,
        val lowerKg: Double = 2.5,
    ) : ProgressionRule()
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

/**
 * Perfil de composición de una receta (§14.3). `LEGACY_STANDARD` conserva las
 * reglas H6 actuales para todas las recetas no migradas.
 */
@Serializable
enum class RecipeCompositionProfile {
    LEGACY_STANDARD,
    NATIVE_COMPACT,
    MIXED_CARDIO,
    AUTHORED_EXACT,
}

/** Estrategia de progresión propia de los planes KPKN (§12.4). */
@Serializable
enum class NativeProgressionStrategy {
    /** Sin progresión nativa propia (receta legacy/autoral). */
    NONE,
    /** Mantener carga y subir reps dentro del rango; después el menor incremento conocido del equipo. */
    REP_RANGE_THEN_LOAD,
    /** Corporal: al tope de dos exposiciones, siguiente variante curada más difícil limpiando referencias. */
    BODYWEIGHT_VARIANT_ESCALATION,
}

/**
 * Progresión propia de un plan KPKN (§12.4). null en recetas legacy: esas
 * recetas usan [TrainingPlanRecipe.progression] (regla del autor intacta).
 * Identidad de progresión: configurationId + loadMode + unitMode + lado +
 * slotPurpose con referencia de receta.
 */
@Serializable
data class NativeProgressionSpec(
    val strategy: NativeProgressionStrategy = NativeProgressionStrategy.REP_RANGE_THEN_LOAD,
    /** Exposiciones completas en tope antes de proponer el primer incremento (§12.4: 2). */
    val exposuresBeforeProposal: Int = 2,
    val note: String = "",
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
    /** True si el microciclo se repite (METHOD / WEEKLY_SPLIT). False = especialización finita. */
    val repeats: Boolean = false,
    /** Versión de contenido de la receta; parte de la identidad (recipeId, contentVersion, ...) de §14.4. */
    val contentVersion: Int = 1,
    /** Procedencia editorial (§10/§14.1); null = receta legacy sin procedencia declarada. */
    val provenance: PlanProvenance? = null,
    /** Progresión propia KPKN (§12.4); null = usar la regla legacy [progression]. */
    val nativeProgression: NativeProgressionSpec? = null,
    /** Perfil de composición (§14.3); LEGACY_STANDARD = reglas H6 actuales. */
    val compositionProfile: RecipeCompositionProfile = RecipeCompositionProfile.LEGACY_STANDARD,
) {
    val daysPerWeek: Int get() = claimedDaysPerWeek ?: weeks.maxOfOrNull { it.days.size } ?: 0
    val distinctBlockCount: Int get() = weeks.map { it.blockIndex }.distinct().size
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
