package com.example.kpkn.domain.onboarding

import com.example.kpkn.data.models.NutritionPlan
import com.example.kpkn.data.models.Program
import com.example.kpkn.data.models.Settings
import com.example.kpkn.domain.nutrition.DayExpenditure
import com.example.kpkn.domain.nutrition.NutritionDayDistributionResult
import com.example.kpkn.domain.nutrition.NutritionDistributionStatus
import com.example.kpkn.domain.nutrition.NutritionEditorBase
import com.example.kpkn.domain.nutrition.NutritionEditorDayTarget
import com.example.kpkn.domain.nutrition.NutritionFixedDayTarget
import com.example.kpkn.domain.nutrition.NutritionPlanEditorDraft
import com.example.kpkn.domain.nutrition.NutritionPlanPreparation
import com.example.kpkn.domain.nutrition.NutritionPlanPreparationStatus
import com.example.kpkn.domain.nutrition.NutritionPlanRecommendation
import com.example.kpkn.domain.nutrition.NutritionSessionInstance
import com.example.kpkn.domain.nutrition.NutritionTrainingCalendarAdapter
import com.example.kpkn.domain.nutrition.NutritionTrainingCalendarInput
import com.example.kpkn.domain.nutrition.NutritionTrainingCalendarResult
import com.example.kpkn.domain.nutrition.NutritionWizardDraft
import com.example.kpkn.domain.nutrition.SessionVariant
import com.example.kpkn.domain.nutrition.WEEKLY_FORECAST_KEY
import com.example.kpkn.domain.nutrition.WeeklyForecastDay
import com.example.kpkn.domain.nutrition.WeeklyForecastDocument
import com.example.kpkn.domain.nutrition.calorieBoundsFor
import com.example.kpkn.domain.nutrition.effectiveOptionalConfirmations
import com.example.kpkn.domain.nutrition.decodeWeeklyForecast
import com.example.kpkn.domain.nutrition.decodeWeeklyForecastDocument
import com.example.kpkn.domain.nutrition.fixedWeeklyEvidenceFor
import com.example.kpkn.domain.nutrition.forecastDaysFor
import com.example.kpkn.domain.nutrition.parseLocalizedNumber
import com.example.kpkn.domain.nutrition.preparationInputOf
import com.example.kpkn.domain.nutrition.previousTargetsFor
import com.example.kpkn.domain.nutrition.reviewedPlanOf
import com.example.kpkn.domain.nutrition.weeklyDistributionResultFor
import com.example.kpkn.domain.nutrition.weeklyForecastPeriodFor
import com.example.kpkn.domain.nutrition.editorDraftOf as wizardDraftToEditorDraft
import java.time.Instant
import java.time.LocalDate
import java.util.UUID

/**
 * Preparación del plan nutricional durante el ALTA (onboarding), reutilizando
 * exactamente los motores del editor directo: [NutritionPlanPreparation],
 * [NutritionTrainingCalendarAdapter] y el reparto semanal único de
 * [com.example.kpkn.domain.nutrition.NutritionDayDistribution].
 *
 * El DTO del borrador vive en `domain/nutrition` (el onboarding no importa de
 * `screens/`); la traducción compartida con el editor es
 * [editorDraftOf], un único algoritmo.
 *
 * Honestidad (misma del editor directo):
 * - El resultado SIEMPRE pasa por la preparación canónica; nunca se fabrica un
 *   EER, un objetivo o unos macros con valores por defecto.
 * - «Borrador vacío» ≠ plan: `TRACKING_ONLY` devuelve sin plan, sin metas y sin
 *   defaults (solo registro).
 * - El gasto del calendario se adapta con [NutritionTrainingCalendarAdapter]
 *   (opcionales confirmadas por sesión+fecha, una variante por sesión+fecha,
 *   no estimable ≠ 0, sesiones sin scheduling ≠ descanso).
 * - La composición corporal ACTUAL del borrador viaja como `NutritionPlan.startValue`
 *   (nunca como meta), y las metas salen de los campos de objetivo del borrador.
 * - La previsión semanal del reparto queda escrita en el plan con
 *   [reviewedPlanOf] (REVISIÓN y fecha de efectividad incluidas) para poder
 *   resolver el objetivo por fecha después del alta.
 */
object SetupNutritionPreparation {

    /**
     * Espejo del `draftFromWizard` del editor: UNA sola traducción, en dominio.
     * [NutritionPlanEditorDraft.pendingDraftId] queda null porque el DTO del alta
     * ES la fuente directa; la limpieza del borrador guardado la hace el
     * consumidor (fuera de este dominio).
     */
    fun editorDraftOf(wizard: NutritionWizardDraft): NutritionPlanEditorDraft =
        wizardDraftToEditorDraft(wizard, pendingDraftId = null)

    /**
     * Prepara el plan del alta: adapta el calendario sobre el periodo de la
     * previsión, prepara el plan, reparte el periodo sobre el gasto real y
     * escribe la previsión en el plan.
     *
     * El borrador vacío (TRACKING_ONLY) y los valores rotos NUNCA producen
     * metas fabricadas: el resultado vuelve sin plan y con sus errores, pero
     * con el gasto del calendario ya resuelto (independiente del plan).
     */
    fun prepare(input: SetupNutritionPreparationInput): SetupNutritionPreparationResult {
        val editor = editorDraftOf(input.draft)
        // Id ESTABLE del plan: el del borrador si viene suministrado; nunca un
        // UUID nuevo por preview (rompería la identidad entre vistas previas).
        val planId = input.draft.planId?.takeIf { it.isNotBlank() }
            ?: input.planId
            ?: stablePreviewPlanId(input.draft)
        val prior = priorForecastOf(input.existingPlan)
        val period = weeklyForecastPeriodFor(prior, input.today, input.windowDays)
        val calendar = NutritionTrainingCalendarAdapter.adapt(
            NutritionTrainingCalendarInput(
                program = input.program,
                settings = input.settings,
                today = period.first(),
                windowDays = period.size.toLong(),
                plannedVariants = input.plannedVariants,
                // Evidencia real (registro de entrenamiento) + confirmación
                // manual del calendario; nada inventado aquí.
                confirmedOptionalSessions = effectiveOptionalConfirmations(
                    logs = input.workoutLogs,
                    program = input.program,
                    extra = input.confirmedOptionalSessions,
                ),
            ),
        )
        val prepared = NutritionPlanPreparation.prepare(
            preparationInputOf(
                editor,
                planId = planId,
                existingPlan = input.existingPlan,
                now = input.now,
            ).copy(
                // Composición actual como startValue, NUNCA como meta: la meta
                // viaja por los campos de objetivo del borrador (vía
                // `resolvedTargetSiOf` dentro de `preparationInputOf`).
                currentBodyFatPercent = parseLocalizedNumber(input.draft.bodyFatText),
                currentMusclePercent = parseLocalizedNumber(input.draft.muscleText),
            ),
        )
        val plan = prepared.plan
            ?: return SetupNutritionPreparationResult(
                plan = null,
                status = prepared.status,
                errors = prepared.errors,
                expendituresByDate = calendar.expendituresByDate,
                distributionStatus = null,
                recommendation = prepared.recommendation,
            )
        val fixedEvidence = fixedWeeklyEvidenceFor(
            priorDays = prior,
            today = input.today,
            period = period,
            snapshots = emptyList(),
            planId = input.existingPlan?.id,
        )
        val distribution = distributionFor(
            plan = plan,
            editor = editor,
            calendar = calendar,
            input = input,
            period = period,
            fixedEvidence = fixedEvidence,
            recommendation = prepared.recommendation,
        )
        // Los días fijados (hoy/pasado con previsión previa) conservan SUS
        // macros; los repartidos escalan desde la base del plan final.
        val days = distribution?.let { result ->
            forecastDaysFor(
                fixedEvidence = fixedEvidence,
                distributed = result.targetsByDate,
                base = NutritionEditorBase(
                    caloriesKcal = plan.calorieTarget,
                    proteinG = plan.proteinGoal,
                    carbsG = plan.carbGoal,
                    fatG = plan.fatGoal,
                ),
            )
        }.orEmpty()
        // La previsión semanal (objetivos por fecha) viaja en el snapshot de
        // cálculo para resolver el objetivo de un día después del alta.
        return SetupNutritionPreparationResult(
            plan = reviewedPlanOf(
                draft = editor,
                prepared = plan,
                forecastTargets = days,
                priorForecast = input.existingPlan?.let { priorDocumentOf(it) },
            ),
            status = prepared.status,
            errors = prepared.errors,
            days = days,
            expendituresByDate = calendar.expendituresByDate,
            distributionStatus = distribution?.status,
            recommendation = prepared.recommendation,
        )
    }

    /**
     * Reparto semanal sobre el PERIODO de la previsión (no una ventana rolling
     * «hoy..+6»). La base del reparto es la DEL PLAN FINAL (jamás una
     * re-derivación): lo que se persiste define los T_i y la previsión que se
     * guarda. El alta no tiene evidencia histórica (aún no hay snapshots del
     * plan), así que solo se fija lo que la previsión previa del plan aporta; si
     * no hay solución válida, se devuelve intacta con KEPT_PREVIOUS.
     */
    private fun distributionFor(
        plan: NutritionPlan,
        editor: NutritionPlanEditorDraft,
        calendar: NutritionTrainingCalendarResult,
        input: SetupNutritionPreparationInput,
        period: List<LocalDate>,
        fixedEvidence: Map<LocalDate, NutritionFixedDayTarget>,
        recommendation: NutritionPlanRecommendation?,
    ): NutritionDayDistributionResult? {
        // Sin DailyGoalSnapshot en el alta: la evidencia son los días fijados
        // provenientes de la previsión previa del plan que se re-prepara.
        val fixed = fixedEvidence.mapValues { it.value.calorieTargetKcal }
        val future = period.filterNot { it in fixed }
        val previous = input.existingPlan?.let { previousTargetsFor(it, future) }.orEmpty()
        val bounds = plan.direction?.let { calorieBoundsFor(it, recommendation?.eerKcal) }
        return weeklyDistributionResultFor(
            base = NutritionEditorBase(
                caloriesKcal = plan.calorieTarget,
                proteinG = plan.proteinGoal,
                carbsG = plan.carbGoal,
                fatG = plan.fatGoal,
            ),
            mode = editor.weeklyDistribution,
            dates = future,
            expenditures = calendar.expendituresByDate,
            bounds = bounds,
            fixedTargets = fixed,
            previousTargets = previous,
            periodBudgetKcal = period.size * plan.calorieTarget,
        )
    }

    private fun priorForecastOf(plan: NutritionPlan?): List<WeeklyForecastDay> =
        plan?.calculationSnapshot?.inputs
            ?.get(WEEKLY_FORECAST_KEY)
            ?.let(::decodeWeeklyForecast)
            .orEmpty()

    private fun priorDocumentOf(plan: NutritionPlan): WeeklyForecastDocument? =
        plan.calculationSnapshot?.inputs
            ?.get(WEEKLY_FORECAST_KEY)
            ?.let(::decodeWeeklyForecastDocument)

    /**
     * Id determinista de la vista previa cuando NI el borrador NI el consumidor
     * suministran uno: la misma entrada produce siempre el MISMO id (jamás un
     * UUID aleatorio por preview). El consumidor debe suministrar
     * [SetupNutritionPreparationInput.planId] o `draft.planId` para que el id
     * sobreviva a ediciones del borrador.
     */
    private fun stablePreviewPlanId(draft: NutritionWizardDraft): String =
        UUID.nameUUIDFromBytes("kpkn-setup-plan|$draft".toByteArray(Charsets.UTF_8)).toString()
}

/** Entrada de la preparación nutricional del alta. */
data class SetupNutritionPreparationInput(
    /** Borrador del wizard de nutrición (DTO serializado compartido del alta). */
    val draft: NutritionWizardDraft,
    /** Programa activo; null = calendario desactivado (sin entrenamiento). */
    val program: Program?,
    val settings: Settings,
    /** Ancla del día de hoy; la ventana arranca hoy. */
    val today: LocalDate = LocalDate.now(),
    /** Días de la ventana a proyectar cuando NO hay previsión previa. */
    val windowDays: Long = NutritionTrainingCalendarAdapter.DEFAULT_WINDOW_DAYS,
    /**
     * Id ESTABLE del plan que se prepara (lo genera UNA vez el consumidor).
     * Tiene prioridad sobre el id derivado del borrador cuando `draft.planId`
     * está vacío; nunca se genera un UUID por preview.
     */
    val planId: String? = null,
    /**
     * Variante elegida por (sesión, fecha); ausente → la declarada en la semana
     * del programa, y si no, A.
     */
    val plannedVariants: Map<NutritionSessionInstance, SessionVariant> = emptyMap(),
    /** Sesiones opcionales confirmadas por (sesión, fecha); fuera de este set se omiten.
     *  Origen productivo: [com.example.kpkn.domain.nutrition.confirmedOptionalSessionsOf]
     *  sobre el registro de entrenamiento; aquí sólo se reenvía. */
    val confirmedOptionalSessions: Set<NutritionSessionInstance> = emptySet(),
    /** Registro de entrenamiento real: de él se derivan las opcionales confirmadas. */
    val workoutLogs: List<com.example.kpkn.data.models.WorkoutLog> = emptyList(),
    /** Plan existente que se re-prepara (si el alta edita un plan previo). */
    val existingPlan: NutritionPlan? = null,
    val now: Instant = Instant.now(),
)

/** Resultado de la preparación nutricional del alta. */
data class SetupNutritionPreparationResult(
    /** Plan ya revisado con su previsión semanal; null si no hay preparación completa. */
    val plan: NutritionPlan? = null,
    /** Estado explícito de la preparación ([NutritionPlanPreparationStatus]). */
    val status: NutritionPlanPreparationStatus,
    /** Errores de validación; vacío = lista para activar. */
    val errors: Map<String, String> = emptyMap(),
    /** Objetivos por fecha (macros del reparto) de TODA la ventana planificada. */
    val days: List<NutritionEditorDayTarget> = emptyList(),
    /** Gasto de entrenamiento previsto por fecha de la ventana (Rest si no hay sesión). */
    val expendituresByDate: Map<LocalDate, DayExpenditure> = emptyMap(),
    /** Estado del reparto semanal ([NutritionDistributionStatus]); null sin plan. */
    val distributionStatus: NutritionDistributionStatus? = null,
    /** Recomendación EER calculada (puede existir aunque haya errores de ecuación). */
    val recommendation: NutritionPlanRecommendation? = null,
)
