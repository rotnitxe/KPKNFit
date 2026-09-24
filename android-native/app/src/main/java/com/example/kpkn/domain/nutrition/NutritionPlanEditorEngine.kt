package com.example.kpkn.domain.nutrition

import com.example.kpkn.data.models.GoalMetric
import com.example.kpkn.data.models.NutritionPlan
import com.example.kpkn.data.models.NutritionTrackingChoice
import com.example.kpkn.data.models.PlanDirection
import com.example.kpkn.data.models.Settings
import java.time.Instant
import java.time.LocalDate
import kotlin.math.roundToInt

/**
 * Motor puro del editor nutricional DIRECTO (sin wizard ni pasos guiados).
 *
 * Toda la edición se apoya sobre una única base coherente de calorías/macros
 * ([NutritionEditorBase]): es lo que se muestra, lo que se edita y lo que se
 * guarda. El reparto semanal lo calcula el dueño único
 * [NutritionDayDistribution]; la preparación/validación canónica la hace
 * [NutritionPlanPreparation]. Este motor no introduce defaults, no fórmulas
 * nuevas y no corrige nada en silencio.
 */

// ─── Modos del editor ────────────────────────────────────────────────────────

/** Estado del plan elegido en el editor: activo o solo registro. */
enum class NutritionPlanEditorMode {
    /** Plan activo con metas numéricas. */
    ACTIVE_PLAN,

    /**
     * Solo registro (durable): el registro de alimentos sigue disponible, no
     * hay metas que mostrar ni alertar y NO se borran los planes anteriores.
     */
    TRACKING_ONLY,
}

/** Reparto semanal de las metas por fecha. */
enum class NutritionWeeklyDistributionMode {
    /** Reparto variable T_i = B + α·(E_i − Ē) por gasto previsto. */
    VARIABLE,

    /** Objetivos uniformes elegidos por el usuario. */
    UNIFORM,
}

/** Procedencia elegida para la definición de los objetivos. */
enum class NutritionEditorProvenance {
    /** Recomendación automática (ecuación EER). */
    AUTOMATIC,

    /** Objetivos propios introducidos por el usuario. */
    SELF_DEFINED,

    /** Pauta definida por un tercero. */
    PROFESSIONAL,
}

// ─── Base coherente calorías/macros ──────────────────────────────────────────

/**
 * Única base coherente de calorías/macros del editor. Un macro en 0 es un cero
 * manual válido (se preserva al escalar); null = ausencia y solo existe fuera
 * de la base, en [NutritionPlanPreparationInput].
 */
data class NutritionEditorBase(
    val caloriesKcal: Int,
    val proteinG: Int,
    val carbsG: Int,
    val fatG: Int,
) {
    /** Total energético Atwater (4/4/9) de los gramos revisados. */
    val atwaterEnergyKcal: Int
        get() = atwaterKcal(proteinG.toDouble(), carbsG.toDouble(), fatG.toDouble())

    /** true si queda alguna meta numérica; los ceros cuentan como valor. */
    val hasAnyValue: Boolean
        get() = caloriesKcal != 0 || proteinG != 0 || carbsG != 0 || fatG != 0
}

/**
 * Edición de calorías: escalan los TRES macros con [scaleMacrosToCalories]
 * (preserveZeros = true) para no destruir un cero manual válido. El total
 * mostrado queda en el valor introducido; la tolerancia Atwater residual del
 * redondeo de gramos (±2 kcal cuando hay un cero manual) es la documentada en
 * [scaleMacrosToCalories].
 */
fun NutritionEditorBase.withCalories(newKcal: Int): NutritionEditorBase {
    val (protein, carbs, fat) = scaleMacrosToCalories(
        proteinG.toDouble(),
        carbsG.toDouble(),
        fatG.toDouble(),
        newKcal,
        preserveZeros = true,
    )
    return NutritionEditorBase(
        caloriesKcal = newKcal,
        proteinG = protein,
        carbsG = carbs,
        fatG = fat,
    )
}

/**
 * Edición de UN macro: los otros dos quedan intactos y el total energético
 * mostrado se actualiza con Atwater de los gramos resultantes.
 */
fun NutritionEditorBase.withMacro(
    protein: Double? = null,
    carbs: Double? = null,
    fat: Double? = null,
): NutritionEditorBase {
    val (p, c, f) = editSingleMacro(
        proteinG.toDouble(),
        carbsG.toDouble(),
        fatG.toDouble(),
        protein = protein,
        carbs = carbs,
        fat = fat,
    )
    return NutritionEditorBase(
        caloriesKcal = atwaterKcal(p.toDouble(), c.toDouble(), f.toDouble()),
        proteinG = p,
        carbsG = c,
        fatG = f,
    )
}

/**
 * true si el plan persistido es EXACTAMENTE la base revisada: sin correcciones
 * silenciosas ni rederivaciones en el commit.
 */
fun matchesReviewedBase(plan: NutritionPlan, base: NutritionEditorBase): Boolean =
    plan.calorieTarget == base.caloriesKcal &&
        plan.proteinGoal == base.proteinG &&
        plan.carbGoal == base.carbsG &&
        plan.fatGoal == base.fatG

// ─── Borrador del editor (todas las secciones editables) ─────────────────────

/**
 * Estado editable del editor directo. No hay pasos ni orden obligatorio: todas
 * las secciones son editables en cualquier momento.
 */
data class NutritionPlanEditorDraft(
    /** Plan existente que se edita; null = alta de un plan nuevo. */
    val planId: String? = null,
    /** Borrador pendiente (pauta profesional a medias) consumido por el guardado. */
    val pendingDraftId: String? = null,
    val direction: PlanDirection? = null,
    val goalMetric: GoalMetric = GoalMetric.WEIGHT,
    val targetValueText: String = "",
    val ageText: String = "",
    val heightText: String = "",
    val weightText: String = "",
    val weightUnit: String = "kg",
    val equationSex: EerSex? = null,
    val activity: EerActivity = EerActivity.INACTIVE,
    val eligibilityUnknown: Boolean = false,
    val medicalRestriction: Boolean = false,
    val pregnant: Boolean = false,
    val lactating: Boolean = false,
    val bodyFatText: String = "",
    val muscleText: String = "",
    /** Base revisada por el usuario; null = aún no editada (se muestra la recomendación). */
    val base: NutritionEditorBase? = null,
    /** true cuando el usuario editó calorías o macros a mano. */
    val baseEdited: Boolean = false,
    /** Procedencia elegida explícitamente; [PlanDirection.PROFESSIONAL] la fija. */
    val provenance: NutritionEditorProvenance = NutritionEditorProvenance.AUTOMATIC,
    val mode: NutritionPlanEditorMode = NutritionPlanEditorMode.ACTIVE_PLAN,
    val weeklyDistribution: NutritionWeeklyDistributionMode = NutritionWeeklyDistributionMode.VARIABLE,
)

/** Procedencia efectiva del borrador. */
fun provenanceOf(draft: NutritionPlanEditorDraft): NutritionEditorProvenance = when {
    draft.direction == PlanDirection.PROFESSIONAL -> NutritionEditorProvenance.PROFESSIONAL
    else -> draft.provenance
}

/**
 * Cambia la procedencia. `PROFESSIONAL` fija la dirección de tercero; las
 * otras dos opciones conservan la dirección del usuario.
 */
fun NutritionPlanEditorDraft.withProvenance(provenance: NutritionEditorProvenance): NutritionPlanEditorDraft =
    when (provenance) {
        NutritionEditorProvenance.PROFESSIONAL ->
            copy(direction = PlanDirection.PROFESSIONAL, provenance = provenance)
        NutritionEditorProvenance.SELF_DEFINED ->
            copy(
                direction = direction?.takeIf { it != PlanDirection.PROFESSIONAL } ?: PlanDirection.MAINTENANCE,
                provenance = provenance,
            )
        NutritionEditorProvenance.AUTOMATIC ->
            copy(
                direction = direction?.takeIf { it != PlanDirection.PROFESSIONAL } ?: PlanDirection.MAINTENANCE,
                provenance = provenance,
            )
    }

/** Edición de calorías sobre el borrador (base única coherente). */
fun NutritionPlanEditorDraft.withCalories(newKcal: Int): NutritionPlanEditorDraft {
    val current = base ?: NutritionEditorBase(0, 0, 0, 0)
    return copy(base = current.withCalories(newKcal), baseEdited = true)
}

/** Edición de un macro sobre el borrador (el total energético se actualiza). */
fun NutritionPlanEditorDraft.withMacro(
    protein: Double? = null,
    carbs: Double? = null,
    fat: Double? = null,
): NutritionPlanEditorDraft {
    val current = base ?: NutritionEditorBase(0, 0, 0, 0)
    return copy(base = current.withMacro(protein = protein, carbs = carbs, fat = fat), baseEdited = true)
}

/**
 * Base mostrada/guardada: la revisada por el usuario, o la derivada del
 * recomendador con el MISMO redondeo que usa [NutritionPlanPreparation] (para
 * que lo mostrado y lo persistido sean exactamente la misma base).
 */
fun reviewedBaseOf(
    draft: NutritionPlanEditorDraft,
    recommendation: NutritionPlanRecommendation?,
): NutritionEditorBase? {
    draft.base?.let { return it }
    val macros = recommendation?.macros ?: return null
    return NutritionEditorBase(
        caloriesKcal = recommendation.calorieTargetKcal
            ?: atwaterKcal(macros.proteinG, macros.carbsG, macros.fatG),
        proteinG = macros.proteinG.roundToInt().coerceAtLeast(0),
        carbsG = macros.carbsG.roundToInt().coerceAtLeast(0),
        fatG = macros.fatG.roundToInt().coerceAtLeast(0),
    )
}

/** Modo de configuración efectivo para la preparación canónica. */
fun configurationModeOf(draft: NutritionPlanEditorDraft): NutritionConfigurationMode = when {
    draft.mode == NutritionPlanEditorMode.TRACKING_ONLY -> NutritionConfigurationMode.TRACKING_ONLY
    provenanceOf(draft) != NutritionEditorProvenance.AUTOMATIC -> NutritionConfigurationMode.SELF_DEFINED
    else -> NutritionConfigurationMode.AUTOMATIC
}

/** Meta tipada resuelta en unidades SI; null = sin meta corporal. */
fun resolvedTargetSiOf(draft: NutritionPlanEditorDraft): Double? = when (draft.goalMetric) {
    GoalMetric.WEIGHT ->
        parseLocalizedNumber(draft.targetValueText)?.let { kilogramsFromInput(it, draft.weightUnit) }
    GoalMetric.BODY_FAT, GoalMetric.MUSCLE_MASS -> parseLocalizedNumber(draft.targetValueText)
}

/**
 * Errores propios del editor que la preparación canónica no puede detectar
 * porque la base revisada viaja como valores manuales. Evitan correcciones
 * silenciosas (p. ej. que un 0 calórico se sustituya por la recomendación).
 */
fun editorErrorsOf(draft: NutritionPlanEditorDraft, base: NutritionEditorBase?): Map<String, String> {
    if (base == null) return emptyMap()
    val errors = linkedMapOf<String, String>()
    if (base.caloriesKcal < 1) {
        errors["calories"] = "El total energético debe ser mayor que 0"
    }
    if (base.proteinG == 0 && base.carbsG == 0 && base.fatG == 0) {
        errors["macros"] = "Tres ceros no representan un plan completo"
    }
    return errors
}

/**
 * Entrada canónica de [NutritionPlanPreparation] desde el borrador. La base
 * revisada viaja como valores manuales solo cuando procede (edición manual o
 * objetivos propios/profesionales); en automático sin tocar, la recomendación
 * es la fuente y el resultado redondeado coincide con la base mostrada.
 */
fun preparationInputOf(
    draft: NutritionPlanEditorDraft,
    planId: String,
    existingPlan: NutritionPlan?,
    now: Instant = Instant.now(),
): NutritionPlanPreparationInput {
    val base = reviewedBaseOf(draft, recommendation = null)
    val passBase = draft.baseEdited || provenanceOf(draft) != NutritionEditorProvenance.AUTOMATIC
    val manual = if (passBase) base else null
    return NutritionPlanPreparationInput(
        planId = planId,
        existingPlan = existingPlan,
        ageYears = parseLocalizedNumber(draft.ageText)?.toInt(),
        heightCm = parseLocalizedNumber(draft.heightText),
        weightKg = parseLocalizedNumber(draft.weightText)?.let { kilogramsFromInput(it, draft.weightUnit) },
        equationSex = draft.equationSex,
        activity = draft.activity,
        eligibilityUnknown = draft.eligibilityUnknown,
        pregnant = draft.pregnant,
        lactating = draft.lactating,
        medicalRestriction = draft.medicalRestriction,
        direction = draft.direction,
        goalMetric = draft.goalMetric,
        targetValueSi = resolvedTargetSiOf(draft),
        manualCalories = manual?.caloriesKcal,
        manualProteinG = manual?.proteinG?.toDouble(),
        manualCarbsG = manual?.carbsG?.toDouble(),
        manualFatG = manual?.fatG?.toDouble(),
        configurationMode = configurationModeOf(draft),
        now = now,
    )
}

/**
 * Plan final a persistir: el resultado de la preparación canónica + el reparto
 * semanal elegido, viajando en el payload JSON del snapshot de cálculo (sin
 * cambiar el esquema). La base revisada se guarda tal cual, sin correcciones.
 */
fun reviewedPlanOf(
    draft: NutritionPlanEditorDraft,
    prepared: NutritionPlan,
): NutritionPlan =
    prepared.copy(
        calculationSnapshot = prepared.calculationSnapshot?.let { snapshot ->
            snapshot.copy(
                inputs = snapshot.inputs + mapOf(
                    "weeklyDistribution" to draft.weeklyDistribution.name,
                ),
            )
        },
    )

// ─── Reparto semanal (dueño único: NutritionDayDistribution) ─────────────────

/** Objetivo de un día del reparto semanal, con macros escalados desde la base. */
data class NutritionEditorDayTarget(
    val date: LocalDate,
    val calorieTargetKcal: Int,
    val proteinG: Int,
    val carbsG: Int,
    val fatG: Int,
)

/**
 * Reparto semanal de las metas: los T_i salen de [NutritionDayDistribution]
 * (único dueño de T_i = B + α·(E_i − Ē)) y los macros por fecha escalan los
 * TRES desde la MISMA base revisada con [macroTargetsByDate].
 */
fun weeklyTargetsFor(
    base: NutritionEditorBase,
    mode: NutritionWeeklyDistributionMode,
    dates: List<LocalDate>,
    expenditures: Map<LocalDate, DayExpenditure> = emptyMap(),
    bounds: IntRange? = null,
): List<NutritionEditorDayTarget> {
    val result = NutritionDayDistribution.distribute(
        NutritionDayDistributionInput(
            futureDates = dates,
            expenditures = expenditures,
            dailyMeanKcal = base.caloriesKcal.toDouble(),
            bounds = bounds,
            uniformByChoice = mode == NutritionWeeklyDistributionMode.UNIFORM,
        ),
    )
    return macroTargetsByDate(
        targetsByDate = result.allTargets(),
        baseProteinG = base.proteinG.toDouble(),
        baseCarbsG = base.carbsG.toDouble(),
        baseFatG = base.fatG.toDouble(),
    )
        .map { (date, target) ->
            NutritionEditorDayTarget(
                date = date,
                calorieTargetKcal = target.calorieTargetKcal,
                proteinG = target.proteinG,
                carbsG = target.carbsG,
                fatG = target.fatG,
            )
        }
        .sortedBy { it.date }
}

// ─── Disponibilidad de registro / avisos ─────────────────────────────────────

/**
 * Registro de alimentos disponible: Nutrición visible (no SKIPPED) y, además,
 * plan activo o modo durable de solo registro. En solo registro el registro
 * sigue disponible aunque no haya plan activo.
 */
fun isFoodLoggingAvailable(settings: Settings, hasActivePlan: Boolean): Boolean =
    settings.nutritionTrackingChoice != NutritionTrackingChoice.SKIPPED &&
        (settings.nutritionTrackingOnly || hasActivePlan)

/**
 * Nutrición visible en la app. [NutritionTrackingChoice.SKIPPED] conserva su
 * semántica actual (ocultar y silenciar Nutrición); el modo de solo registro
 * nunca oculta Nutrición.
 */
fun isNutritionVisible(settings: Settings): Boolean =
    settings.nutritionTrackingChoice != NutritionTrackingChoice.SKIPPED

/**
 * Los recordatorios de registrar comida siguen activos en solo registro; solo
 * [NutritionTrackingChoice.SKIPPED] los silencia (semántica actual intacta).
 */
fun areMealRemindersAvailable(settings: Settings): Boolean =
    isNutritionVisible(settings) && settings.mealReminderEnabled

/**
 * Los avisos de «te faltan calorías/proteína» exigen metas reales: nunca se
 * envían en modo de solo registro (no hay metas contra las que medir).
 */
fun areGoalDeficitAlertsAvailable(settings: Settings): Boolean =
    !settings.nutritionTrackingOnly && isNutritionVisible(settings)
