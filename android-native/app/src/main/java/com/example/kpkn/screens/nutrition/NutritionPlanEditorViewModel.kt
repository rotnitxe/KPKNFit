package com.example.kpkn.screens.nutrition

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.example.kpkn.data.models.Gender
import com.example.kpkn.data.models.GoalMetric
import com.example.kpkn.data.models.NutritionPlan
import com.example.kpkn.data.models.PlanDirection
import com.example.kpkn.data.onboarding.SetupDraftRepository
import com.example.kpkn.data.repository.NutritionPlanCommitCoordinator
import com.example.kpkn.data.repository.NutritionPlanCommitRequest
import com.example.kpkn.data.repository.NutritionPlanCommitResult
import com.example.kpkn.data.repository.NutritionRepository
import com.example.kpkn.data.repository.ProgramRepository
import com.example.kpkn.data.repository.SettingsGoalMirror
import com.example.kpkn.data.repository.derivedBodyGoalsFor
import com.example.kpkn.domain.nutrition.DayExpenditure
import com.example.kpkn.domain.nutrition.EerInput
import com.example.kpkn.domain.nutrition.EerSex
import com.example.kpkn.domain.nutrition.NutritionEditorBase
import com.example.kpkn.domain.nutrition.NutritionEditorProvenance
import com.example.kpkn.domain.nutrition.NutritionEditCommitIds
import com.example.kpkn.domain.nutrition.NutritionPlanEditorDraft
import com.example.kpkn.domain.nutrition.NutritionPlanEditorMode
import com.example.kpkn.domain.nutrition.NutritionPlanPreparation
import com.example.kpkn.domain.nutrition.NutritionPlanRecommendation
import com.example.kpkn.domain.nutrition.NutritionWeeklyDistributionMode
import com.example.kpkn.domain.nutrition.NutritionEnergyEngine
import com.example.kpkn.domain.nutrition.editorErrorsOf
import com.example.kpkn.domain.nutrition.matchesReviewedBase
import com.example.kpkn.domain.nutrition.parseLocalizedNumber
import com.example.kpkn.domain.nutrition.preparationInputOf
import com.example.kpkn.domain.nutrition.resolvedTargetSiOf
import com.example.kpkn.domain.nutrition.reviewedBaseOf
import com.example.kpkn.domain.nutrition.reviewedPlanOf
import com.example.kpkn.domain.nutrition.withCalories
import com.example.kpkn.domain.nutrition.withMacro
import com.example.kpkn.domain.nutrition.withProvenance
import com.example.kpkn.domain.nutrition.NutritionEditorDayTarget
import com.example.kpkn.domain.nutrition.NutritionDistributionStatus
import com.example.kpkn.domain.nutrition.WEEKLY_FORECAST_KEY
import com.example.kpkn.domain.nutrition.NutritionTrainingCalendarAdapter
import com.example.kpkn.domain.nutrition.NutritionTrainingCalendarInput
import com.example.kpkn.domain.nutrition.WeeklyForecastDay
import com.example.kpkn.domain.nutrition.WeeklyForecastDocument
import com.example.kpkn.domain.nutrition.calorieBoundsFor
import com.example.kpkn.domain.nutrition.effectiveOptionalConfirmations
import com.example.kpkn.domain.nutrition.decodeWeeklyForecast
import com.example.kpkn.domain.nutrition.decodeWeeklyForecastDocument
import com.example.kpkn.domain.nutrition.fixedWeeklyEvidenceFor
import com.example.kpkn.domain.nutrition.forecastDaysFor
import com.example.kpkn.domain.nutrition.previousTargetsFor
import com.example.kpkn.domain.nutrition.weeklyDistributionResultFor
import com.example.kpkn.domain.nutrition.weeklyForecastPeriodFor
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import kotlinx.serialization.decodeFromString
import kotlinx.serialization.json.Json
import java.time.LocalDate
import java.util.UUID

/**
 * Estado del editor nutricional DIRECTO: todas las secciones editables a la
 * vez, sin pasos ni botones «Siguiente». [base] es la única base coherente de
 * calorías/macros que se muestra, se edita y se guarda.
 */
data class NutritionPlanEditorUiState(
    val isLoading: Boolean = true,
    val draft: NutritionPlanEditorDraft = NutritionPlanEditorDraft(),
    val recommendation: NutritionPlanRecommendation? = null,
    val base: NutritionEditorBase? = null,
    val weeklyTargets: List<NutritionEditorDayTarget> = emptyList(),
    val errors: Map<String, String> = emptyMap(),
    val isDirty: Boolean = false,
    val isSaving: Boolean = false,
    val isSaved: Boolean = false,
    val savedPlanId: String? = null,
    /** Estado explícito del reparto semanal vigente ([NutritionDayDistribution]). */
    val distributionStatus: NutritionDistributionStatus? = null,
    /** Identificador de idempotencia de la operación de edición en curso. */
    val activeCommitId: String? = null,
)

/**
 * ViewModel del editor nutricional directo (post-onboarding). Cada operación de
 * edición tiene su propio identificador de idempotencia: un doble tap o un
 * reintento repiten el MISMO commit y no duplican plan, metas de cuerpo ni
 * snapshots históricos. El guardado es una operación transaccional coordinada
 * ([NutritionPlanCommitCoordinator]), nunca `addNutritionPlan` + `activatePlan`
 * como dos efectos sueltos.
 */
class NutritionPlanEditorViewModel(
    private val nutritionRepository: NutritionRepository,
    private val programRepository: ProgramRepository,
    private val commits: NutritionPlanCommitCoordinator,
    private val drafts: SetupDraftRepository,
) : ViewModel() {

    private val json = Json { ignoreUnknownKeys = true }

    private val _uiState = MutableStateFlow(NutritionPlanEditorUiState())
    val uiState: StateFlow<NutritionPlanEditorUiState> = _uiState.asStateFlow()

    /** Gasto previsto por fecha para el reparto semanal; inyectable a futuro. */
    private val expenditures = MutableStateFlow<Map<LocalDate, DayExpenditure>>(emptyMap())

    private var revision: Int = 0
    /** Idempotencia por operación de edición (la 0 es el alta). */
    private val commitIds = NutritionEditCommitIds()

    /**
     * Consumidor REAL del calendario de entrenamiento (no un simple setter): el
     * gasto previsto por fecha llega del adaptador [NutritionTrainingCalendarAdapter]
     * sobre el programa real vía [ProgramCalendarEngine] + [TrainingEnergyEngine]
     * + [CardioCalorieEngine]. Un cambio de programa activo, de la lista de
     * programas o de los ajustes re-proyecta el gasto y re-reparte la semana
     * (hoy/pasado quedan fijados; el futuro se redistribuye).
     */
    init {
        viewModelScope.launch {
            combine(
                programRepository.activeProgramState,
                programRepository.programs,
                programRepository.settings,
                // ÚNICA fuente de opcionales confirmadas (evidencia real).
                programRepository.history,
            ) { _, _, _, _ -> }
                .collect { refreshCalendarExpenditures() }
        }
    }

    /**
     * Carga el estado inicial: plan existente, borrador pendiente (pauta
     * profesional a medias) o alta nueva. Idempotente: no pisar ediciones.
     */
    fun initialize(planId: String?, pendingDraftId: String?) {
        if (!_uiState.value.isLoading) return
        viewModelScope.launch {
            val settings = programRepository.settings.value
            val existingPlan = planId?.let { id ->
                nutritionRepository.nutritionPlans.value.firstOrNull { it.id == id }
            }
            val wizardDraft = pendingDraftId?.let { id ->
                withContext(Dispatchers.IO) {
                    drafts.load(id)?.payloadJson
                        ?.let { runCatching { json.decodeFromString<NutritionWizardDraft>(it) }.getOrNull() }
                }
            }
            val draft = when {
                wizardDraft != null -> draftFromWizard(wizardDraft, pendingDraftId)
                existingPlan != null -> draftFromPlan(existingPlan, settings.userVitals.age?.toString().orEmpty())
                else -> NutritionPlanEditorDraft(
                    mode = if (settings.nutritionTrackingOnly) {
                        NutritionPlanEditorMode.TRACKING_ONLY
                    } else {
                        NutritionPlanEditorMode.ACTIVE_PLAN
                    },
                )
            }.let { base ->
                val vitals = settings.userVitals
                base.copy(
                    ageText = base.ageText.ifBlank { vitals.age?.toString().orEmpty() },
                    heightText = base.heightText.ifBlank { vitals.height?.toString().orEmpty() },
                    weightText = base.weightText.ifBlank { vitals.weight?.toString().orEmpty() },
                    equationSex = base.equationSex ?: when (vitals.gender) {
                        Gender.MALE -> EerSex.MALE
                        Gender.FEMALE -> EerSex.FEMALE
                        else -> null
                    },
                )
            }
            _uiState.update {
                it.copy(
                    isLoading = false,
                    draft = draft,
                )
            }
            refreshCalendarExpenditures()
        }
    }

    /**
     * Re-proyecta el gasto previsto del programa/calendario activo sobre el
     * PERIODO de la previsión vigente (no una ventana rolling «hoy..+6», que
     * rompería la semana) y re-reparte los objetivos cuando ya hay estado
     * visible (durante la carga inicial el reparto queda para el final de
     * [initialize]).
     */
    fun refreshCalendarExpenditures() {
        val program = programRepository.activeProgramState.value?.programId
            ?.let { id -> programRepository.programs.value.firstOrNull { it.id == id } }
        val plan = _uiState.value.draft.planId
            ?.let { id -> nutritionRepository.nutritionPlans.value.firstOrNull { it.id == id } }
        val window = weeklyForecastPeriodFor(priorForecastOf(plan), LocalDate.now(), WEEK_DAYS)
        val result = NutritionTrainingCalendarAdapter.adapt(
            NutritionTrainingCalendarInput(
                program = program,
                settings = programRepository.settings.value,
                today = window.first(),
                windowDays = window.size.toLong(),
                // Origen productivo: registro real + confirmaciones manuales
                // del calendario, vía la MISMA función pura que alta y coordinador.
                confirmedOptionalSessions = effectiveOptionalConfirmations(
                    logs = programRepository.history.value,
                    program = program,
                ),
            ),
        )
        expenditures.value = result.expendituresByDate
        if (!_uiState.value.isLoading) refresh()
    }

    // ─── Ediciones de sección (sin pasos; todas editables en cualquier orden) ─

    fun setDirection(direction: PlanDirection) = mutate { it.copy(direction = direction) }
    fun setGoalMetric(metric: GoalMetric) = mutate { it.copy(goalMetric = metric) }
    fun setTargetValue(text: String) = mutate { it.copy(targetValueText = text) }
    fun setAge(text: String) = mutate { it.copy(ageText = text) }
    fun setHeight(text: String) = mutate { it.copy(heightText = text) }
    fun setWeight(text: String) = mutate { it.copy(weightText = text) }
    fun setEquationSex(sex: EerSex?) = mutate { it.copy(equationSex = sex) }
    fun setActivity(activity: com.example.kpkn.domain.nutrition.EerActivity) = mutate { it.copy(activity = activity) }
    fun setEligibilityUnknown(value: Boolean) = mutate { it.copy(eligibilityUnknown = value) }
    fun setMedicalRestriction(value: Boolean) = mutate { it.copy(medicalRestriction = value) }
    fun setPregnant(value: Boolean) = mutate { it.copy(pregnant = value) }
    fun setLactating(value: Boolean) = mutate { it.copy(lactating = value) }

    /** Edición de calorías: escalan los TRES macros preservando ceros manuales. */
    fun setCalories(kcal: Int) = mutate { it.withCalories(kcal) }

    /** Edición de un macro: el total energético mostrado se actualiza con Atwater. */
    fun setMacro(protein: Double? = null, carbs: Double? = null, fat: Double? = null) =
        mutate { it.withMacro(protein = protein, carbs = carbs, fat = fat) }

    fun setWeeklyDistribution(mode: NutritionWeeklyDistributionMode) =
        mutate { it.copy(weeklyDistribution = mode) }

    /** Estado del plan: activo o modo durable de solo registro. */
    fun setMode(mode: NutritionPlanEditorMode) = mutate { it.copy(mode = mode) }

    /** Procedencia: recomendación automática, objetivos propios o pauta de tercero. */
    fun setProvenance(provenance: NutritionEditorProvenance) = mutate { it.withProvenance(provenance) }

    // ─── Guardado ────────────────────────────────────────────────────────────

    /**
     * Guarda EXACTAMENTE la base revisada, sin correcciones silenciosas. La
     * operación completa (plan, activación/solo registro, metas derivadas,
     * snapshot histórico y borrador pendiente) es una sola transacción con
     * idempotencia por operación de edición.
     */
    fun save() {
        val snapshot = _uiState.value
        if (snapshot.isLoading || snapshot.isSaving) return
        val draft = snapshot.draft
        val commitId = commitIdFor(revision)
        _uiState.update { it.copy(isSaving = true, activeCommitId = commitId) }
        viewModelScope.launch {
            val outcome = withContext(Dispatchers.IO) { runCatching { commit(draft, commitId) } }
            outcome.fold(
                onSuccess = { result ->
                    _uiState.update {
                        it.copy(
                            isSaving = false,
                            isSaved = true,
                            savedPlanId = result.planId,
                            errors = emptyMap(),
                        )
                    }
                },
                onFailure = { error ->
                    _uiState.update {
                        it.copy(
                            isSaving = false,
                            errors = it.errors + ("commit" to (error.message ?: "No se pudo guardar el plan")),
                        )
                    }
                },
            )
        }
    }

    private suspend fun commit(draft: NutritionPlanEditorDraft, commitId: String): NutritionPlanCommitResult {
        if (draft.mode == NutritionPlanEditorMode.TRACKING_ONLY) {
            // Solo registro: sin metas fabricadas, sin plan nuevo y sin borrar
            // los planes anteriores. El borrador profesional pendiente NO se
            // consume: sus indicaciones siguen disponibles.
            return commits.commit(
                NutritionPlanCommitRequest(
                    commitId = commitId,
                    plan = null,
                    activatePlan = false,
                    trackingOnly = true,
                    pendingDraftId = null,
                    captureTodaySnapshot = false,
                ),
            )
        }
        val planId = draft.planId ?: UUID.randomUUID().toString()
        val existing = nutritionRepository.nutritionPlans.value.firstOrNull { it.id == planId }
        val base = reviewedBaseOf(draft, _uiState.value.recommendation)
        val ownErrors = editorErrorsOf(draft, base)
        if (ownErrors.isNotEmpty()) {
            throw IllegalArgumentException(ownErrors.values.first())
        }
        val prepared = NutritionPlanPreparation.prepare(preparationInputOf(draft, planId, existing))
        if (prepared.errors.isNotEmpty()) {
            throw IllegalArgumentException(prepared.errors.values.first())
        }
        val plan = prepared.plan
            ?: throw IllegalArgumentException("No hay una preparación completa para guardar")
        // La previsión semanal del reparto viaja en el snapshot de cálculo para
        // resolver el objetivo por fecha después del guardado; la REVISIÓN sigue
        // a la previsión que el plan ya traía (monotónica, sin resets).
        val reviewed = reviewedPlanOf(
            draft = draft,
            prepared = plan,
            forecastTargets = _uiState.value.weeklyTargets,
            priorForecast = priorForecastDocumentOf(existing),
        )
        // Una sola base coherente: si la preparación no devuelve exactamente la
        // base revisada, NO se guarda nada (nunca una corrección en silencio).
        if (base != null && !matchesReviewedBase(reviewed, base)) {
            throw IllegalArgumentException("La preparación no conserva la base revisada; no se guardó nada")
        }
        return commits.commit(
            NutritionPlanCommitRequest(
                commitId = commitId,
                plan = reviewed,
                activatePlan = true,
                trackingOnly = false,
                derivedBodyGoals = derivedBodyGoalsFor(reviewed),
                settingsGoalMirror = SettingsGoalMirror(
                    calorieGoal = reviewed.calorieTarget,
                    proteinGoal = reviewed.proteinGoal,
                    carbGoal = reviewed.carbGoal,
                    fatGoal = reviewed.fatGoal,
                ),
                pendingDraftId = draft.pendingDraftId,
                captureTodaySnapshot = true,
            ),
        )
    }

    /**
     * Identificador de idempotencia POR operación de edición: se crea al
     * guardar una revisión concreta y solo se reutiliza para reintentar esa
     * misma operación. Una edición posterior genera un id nuevo.
     */
    private fun commitIdFor(revision: Int): String = commitIds.idFor(revision)

    // ─── Derivados ───────────────────────────────────────────────────────────

    private fun mutate(transform: (NutritionPlanEditorDraft) -> NutritionPlanEditorDraft) {
        revision += 1
        _uiState.update {
            it.copy(
                draft = transform(it.draft),
                isDirty = true,
                isSaved = false,
                errors = emptyMap(),
            )
        }
        refresh()
    }

    private fun refresh() {
        val current = _uiState.value
        val draft = current.draft
        val recommendation = recommendationFor(draft)
        val base = reviewedBaseOf(draft, recommendation)
        val today = LocalDate.now()
        // Objetivos YA fijados (hoy/pasado): evidencia del mismo plan (snapshot
        // histórico o su propia previsión previa, aunque no haya snapshot).
        val plan = draft.planId
            ?.let { id -> nutritionRepository.nutritionPlans.value.firstOrNull { it.id == id } }
        val prior = priorForecastOf(plan)
        // El periodo NO rueda con «hoy»: se conserva el horizonte de la previsión
        // vigente (una ventana hoy..+6 partiría la semana y rompería el presupuesto).
        val window = weeklyForecastPeriodFor(prior, today, WEEK_DAYS)
        val fixedEvidence = fixedWeeklyEvidenceFor(
            priorDays = prior,
            today = today,
            period = window,
            snapshots = nutritionRepository.dailyGoalSnapshots.value,
            planId = plan?.id,
        )
        val fixed = fixedEvidence.mapValues { it.value.calorieTargetKcal }
        // Nunca se mueven y consumen presupuesto; el futuro se redistribuye.
        val future = window.filterNot { it in fixed }
        val previous = plan?.let { previousTargetsFor(it, future) }.orEmpty()
        val bounds = draft.direction?.let { calorieBoundsFor(it, recommendation?.eerKcal) }
        val distribution = base?.let {
            weeklyDistributionResultFor(
                base = it,
                mode = draft.weeklyDistribution,
                dates = future,
                expenditures = expenditures.value,
                bounds = bounds,
                fixedTargets = fixed,
                previousTargets = previous,
                // Presupuesto del periodo FIJO: Σ T_i = |periodo| · B aunque
                // hoy/pasado estén fijados (sin ventana rolling que rompa la semana).
                periodBudgetKcal = window.size * it.caloriesKcal,
            )
        }
        // Los días fijados conservan SUS macros; solo los repartidos escalan
        // los tres macros desde la base revisada.
        val targets = if (base == null) {
            emptyList()
        } else {
            forecastDaysFor(
                fixedEvidence = fixedEvidence,
                distributed = distribution?.targetsByDate.orEmpty(),
                base = base,
            )
        }
        _uiState.update {
            it.copy(
                recommendation = recommendation,
                base = base,
                weeklyTargets = targets,
                distributionStatus = distribution?.status,
                errors = if (it.errors.containsKey("commit")) it.errors else editorErrorsOf(draft, base),
            )
        }
    }

    /** Previsión semanal versionada del plan (días), vacía si no la tiene. */
    private fun priorForecastOf(plan: NutritionPlan?): List<WeeklyForecastDay> =
        plan?.calculationSnapshot?.inputs?.get(WEEKLY_FORECAST_KEY)
            ?.let(::decodeWeeklyForecast)
            .orEmpty()

    /** Cabecera de la previsión vigente (revisión + efectividad) del plan. */
    private fun priorForecastDocumentOf(plan: NutritionPlan?): WeeklyForecastDocument? =
        plan?.calculationSnapshot?.inputs?.get(WEEKLY_FORECAST_KEY)
            ?.let(::decodeWeeklyForecastDocument)

    private fun recommendationFor(draft: NutritionPlanEditorDraft): NutritionPlanRecommendation? {
        val direction = draft.direction ?: return null
        val age = parseLocalizedNumber(draft.ageText)?.toInt() ?: 0
        val height = parseLocalizedNumber(draft.heightText) ?: 0.0
        val weight = parseLocalizedNumber(draft.weightText)
            ?.let { com.example.kpkn.domain.nutrition.kilogramsFromInput(it, draft.weightUnit) } ?: 0.0
        return NutritionEnergyEngine.recommendPlan(
            input = EerInput(
                ageYears = age,
                heightCm = height,
                weightKg = weight,
                sex = draft.equationSex,
                activity = draft.activity,
                pregnant = draft.pregnant,
                lactating = draft.lactating,
                medicalRestriction = draft.medicalRestriction,
            ),
            direction = direction,
            targetValueSi = resolvedTargetSiOf(draft),
            manualCalorieTargetKcal = draft.base?.caloriesKcal?.takeIf { it > 0 },
        )
    }

    private fun draftFromPlan(plan: NutritionPlan, fallbackAge: String): NutritionPlanEditorDraft {
        val goalMetric = plan.typedBodyGoal?.metric ?: plan.goalType
        val target = plan.typedBodyGoal?.targetValueSi
            ?: plan.primaryGoal?.value?.takeIf { it > 0 }
        return NutritionPlanEditorDraft(
            planId = plan.id,
            direction = plan.direction,
            goalMetric = goalMetric,
            targetValueText = target?.toString().orEmpty(),
            ageText = fallbackAge,
            bodyFatText = plan.startValue?.takeIf { goalMetric == GoalMetric.BODY_FAT }?.toString().orEmpty(),
            muscleText = plan.startValue?.takeIf { goalMetric == GoalMetric.MUSCLE_MASS }?.toString().orEmpty(),
            base = NutritionEditorBase(
                caloriesKcal = plan.calorieTarget,
                proteinG = plan.proteinGoal,
                carbsG = plan.carbGoal,
                fatG = plan.fatGoal,
            ),
            baseEdited = true,
            provenance = when (plan.calculationOrigin) {
                com.example.kpkn.data.models.CalculationOrigin.PROFESSIONAL -> NutritionEditorProvenance.PROFESSIONAL
                com.example.kpkn.data.models.CalculationOrigin.MANUAL -> NutritionEditorProvenance.SELF_DEFINED
                else -> NutritionEditorProvenance.AUTOMATIC
            },
            weeklyDistribution = plan.calculationSnapshot?.inputs
                ?.get("weeklyDistribution")
                ?.let { value -> runCatching { NutritionWeeklyDistributionMode.valueOf(value) }.getOrNull() }
                ?: NutritionWeeklyDistributionMode.VARIABLE,
        )
    }

    private fun draftFromWizard(wizard: NutritionWizardDraft, pendingDraftId: String?): NutritionPlanEditorDraft =
        // Misma traducción que el alta: UN solo algoritmo en domain/nutrition.
        com.example.kpkn.domain.nutrition.editorDraftOf(wizard, pendingDraftId)

    private companion object {
        const val WEEK_DAYS = 7L
    }
}
