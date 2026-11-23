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
import com.example.kpkn.domain.nutrition.equationSexOfPlan
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
import com.example.kpkn.domain.nutrition.WEEKLY_FORECAST_KEY
import com.example.kpkn.domain.nutrition.NutritionTrainingCalendarAdapter
import com.example.kpkn.domain.nutrition.NutritionTrainingCalendarInput
import com.example.kpkn.domain.nutrition.NutritionDistributionStatus
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
    /**
     * Calorías que ya tiene FIJADAS hoy el plan que se edita; null si hoy aún no
     * está fijado. Con valor, lo que se guarde en este plan vale desde mañana (la
     * pantalla lo avisa); el editor nunca reescribe la meta de hoy del mismo plan.
     */
    val todayGoalFixedKcal: Int? = null,
    /** Identificador de idempotencia de la operación de edición en curso. */
    val activeCommitId: String? = null,
    /** Base del plan tal como se cargó (null si es un plan nuevo): el «antes» del cambio que muestra «Guardar». */
    val originalBase: NutritionEditorBase? = null,
    val distributionStatus: NutritionDistributionStatus? = null,
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
    private var calorieEditAnchor: com.example.kpkn.domain.nutrition.NutritionEditorBase? = null
    private var userExplicitlyZeroedCarbs: Boolean = false

    /**
     * Consumidor REAL del calendario de entrenamiento (no un simple setter): el
     * gasto previsto por fecha llega del adaptador [NutritionTrainingCalendarAdapter]
     * sobre el programa real y las estimaciones de cardio compatibles del calendario
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
                existingPlan != null -> draftFromPlan(existingPlan, settings)
                else -> NutritionPlanEditorDraft(
                    mode = if (settings.nutritionTrackingOnly) {
                        NutritionPlanEditorMode.TRACKING_ONLY
                    } else {
                        NutritionPlanEditorMode.ACTIVE_PLAN
                    },
                    activity = eerActivityFromSettingsLevel(settings.nutritionActivityLevel),
                    weightUnit = weightUnitCode(settings.weightUnit),
                )
            }.let { base ->
                val vitals = settings.userVitals
                base.copy(
                    ageText = base.ageText.ifBlank { vitals.age?.toString().orEmpty() },
                    heightText = base.heightText.ifBlank { formatCleanNumber(vitals.height) },
                    weightText = base.weightText.ifBlank { formatWeightForUnit(vitals.weight, base.weightUnit) },
                    equationSex = base.equationSex ?: when (vitals.gender) {
                        Gender.MALE -> EerSex.MALE
                        Gender.FEMALE -> EerSex.FEMALE
                        else -> null
                    },
                )
            }
            calorieEditAnchor = null
            userExplicitlyZeroedCarbs = false
            _uiState.update {
                it.copy(
                    isLoading = false,
                    draft = draft,
                    originalBase = existingPlan
                        ?.takeIf { p -> p.calorieTarget > 0 || p.proteinGoal > 0 || p.carbGoal > 0 || p.fatGoal > 0 }
                        ?.let { p -> NutritionEditorBase(p.calorieTarget, p.proteinGoal, p.carbGoal, p.fatGoal) }
                        ?: draft.base.takeIf { draft.planId != null },
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

    fun setDirection(direction: PlanDirection) {
        calorieEditAnchor = null
        mutate {
            val next = it.copy(direction = direction)
            if (next.provenance == NutritionEditorProvenance.AUTOMATIC) next.copy(base = null, baseEdited = false) else next
        }
    }
    fun setGoalMetric(metric: GoalMetric) = mutate { it.copy(goalMetric = metric) }
    fun setTargetValue(text: String) = mutate { it.copy(targetValueText = text) }
    fun setAge(text: String) {
        calorieEditAnchor = null
        mutate {
            val next = it.copy(ageText = text)
            if (next.provenance == NutritionEditorProvenance.AUTOMATIC) next.copy(base = null, baseEdited = false) else next
        }
    }
    fun setHeight(text: String) {
        calorieEditAnchor = null
        mutate {
            val next = it.copy(heightText = text)
            if (next.provenance == NutritionEditorProvenance.AUTOMATIC) next.copy(base = null, baseEdited = false) else next
        }
    }
    fun setWeight(text: String) {
        calorieEditAnchor = null
        mutate {
            val next = it.copy(weightText = text)
            if (next.provenance == NutritionEditorProvenance.AUTOMATIC) next.copy(base = null, baseEdited = false) else next
        }
    }
    fun setEquationSex(sex: EerSex?) {
        calorieEditAnchor = null
        mutate {
            val next = it.copy(equationSex = sex)
            if (next.provenance == NutritionEditorProvenance.AUTOMATIC) next.copy(base = null, baseEdited = false) else next
        }
    }
    fun setActivity(activity: com.example.kpkn.domain.nutrition.EerActivity) {
        calorieEditAnchor = null
        mutate {
            val next = it.copy(activity = activity)
            if (next.provenance == NutritionEditorProvenance.AUTOMATIC) next.copy(base = null, baseEdited = false) else next
        }
    }
    fun setEligibilityUnknown(value: Boolean) = mutate { it.copy(eligibilityUnknown = value) }
    fun setMedicalRestriction(value: Boolean) = mutate { it.copy(medicalRestriction = value) }
    fun setPregnant(value: Boolean) = mutate { it.copy(pregnant = value) }
    fun setLactating(value: Boolean) = mutate { it.copy(lactating = value) }

    /** Edición de calorías: escala proporcionalmente desde el ancla sin colapsar macros a 0. */
    fun setCalories(kcal: Int) {
        val currentState = _uiState.value
        val fallback = currentState.base ?: reviewedBaseOf(currentState.draft, currentState.recommendation)
        val candidate = currentState.draft.base ?: fallback
        val anchor = calorieEditAnchor ?: if (
            candidate != null &&
            (candidate.carbsG > 0 || userExplicitlyZeroedCarbs) &&
            (candidate.proteinG > 0 || candidate.fatG > 0 || candidate.caloriesKcal > 0)
        ) {
            candidate
        } else {
            NutritionEditorBase(caloriesKcal = 2000, proteinG = 150, carbsG = 225, fatG = 56)
        }.also { calorieEditAnchor = it }
        mutate { it.withCalories(kcal, fallbackBase = fallback, anchor = anchor) }
    }

    /** Edición de un macro: el total energético mostrado se actualiza con Atwater. */
    fun setMacro(protein: Double? = null, carbs: Double? = null, fat: Double? = null) {
        calorieEditAnchor = null
        if (carbs != null) {
            userExplicitlyZeroedCarbs = carbs <= 0.0
        }
        val currentState = _uiState.value
        val fallback = currentState.base ?: reviewedBaseOf(currentState.draft, currentState.recommendation)
        mutate { it.withMacro(protein = protein, carbs = carbs, fat = fat, fallbackBase = fallback) }
    }

    fun resetToRecommendation() {
        calorieEditAnchor = null
        userExplicitlyZeroedCarbs = false
        mutate {
            it.copy(
                provenance = if (it.provenance == NutritionEditorProvenance.PROFESSIONAL) it.provenance else NutritionEditorProvenance.AUTOMATIC,
                base = null,
                baseEdited = false,
            )
        }
    }

    fun setWeeklyDistribution(mode: NutritionWeeklyDistributionMode) =
        mutate { it.copy(weeklyDistribution = mode) }

    /** Estado del plan: activo o modo durable de solo registro. */
    fun setMode(mode: NutritionPlanEditorMode) = mutate { it.copy(mode = mode) }

    /** Procedencia: recomendación automática, objetivos propios o pauta de tercero. */
    fun setProvenance(provenance: NutritionEditorProvenance) {
        calorieEditAnchor = null
        userExplicitlyZeroedCarbs = false
        mutate {
            val next = it.withProvenance(provenance)
            if (provenance == NutritionEditorProvenance.AUTOMATIC) next.copy(base = null, baseEdited = false) else next
        }
    }

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
                onSuccess = { _ ->
                    _uiState.update {
                        it.copy(
                            isSaving = false,
                            isSaved = true,
                            errors = emptyMap(),
                        )
                    }
                },
                onFailure = { _ ->
                    _uiState.update {
                        it.copy(
                            isSaving = false,
                            errors = it.errors + ("commit" to "No se pudo guardar el plan. Inténtalo de nuevo."),
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
        val result = commits.commit(
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
                updateTodaySnapshot = true,
            ),
        )
        // Persistir también los vitales y actividad editados en Settings (#9)
        val editedAge = parseLocalizedNumber(draft.ageText)?.toInt()?.takeIf { it > 0 }
        val editedHeight = parseLocalizedNumber(draft.heightText)?.takeIf { it > 0 }
        val editedWeight = parseLocalizedNumber(draft.weightText)
            ?.let { com.example.kpkn.domain.nutrition.kilogramsFromInput(it, draft.weightUnit) }
            ?.takeIf { it > 0 }
        val editedGender = when (draft.equationSex) {
            EerSex.MALE -> Gender.MALE
            EerSex.FEMALE -> Gender.FEMALE
            EerSex.AVERAGE, null -> null
        }
        val editedActivityLevel = settingsLevelFromEerActivity(draft.activity)
        programRepository.updateSettings { current ->
            current.copy(
                age = editedAge ?: current.age,
                nutritionActivityLevel = editedActivityLevel,
                userVitals = current.userVitals.copy(
                    age = editedAge ?: current.userVitals.age,
                    height = editedHeight ?: current.userVitals.height,
                    weight = editedWeight ?: current.userVitals.weight,
                    gender = editedGender ?: current.userVitals.gender,
                ),
            )
        }
        return result
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
        ).filterKeys { it.isBefore(today) }
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
        val ownErrors = editorErrorsOf(draft, base)
        val prepErrors = if (draft.mode == NutritionPlanEditorMode.ACTIVE_PLAN && ownErrors.isEmpty()) {
            NutritionPlanPreparation.prepare(preparationInputOf(draft, draft.planId ?: "preview", plan)).errors
        } else {
            emptyMap()
        }
        _uiState.update {
            it.copy(
                recommendation = recommendation,
                base = base,
                weeklyTargets = targets,
                todayGoalFixedKcal = null,
                distributionStatus = distribution?.status,
                errors = if (it.errors.containsKey("commit")) it.errors else ownErrors + prepErrors,
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

    private fun draftFromPlan(plan: NutritionPlan, settings: com.example.kpkn.data.models.Settings): NutritionPlanEditorDraft {
        val goalMetric = plan.typedBodyGoal?.metric ?: plan.goalType
        val target = plan.typedBodyGoal?.targetValueSi
            ?: plan.primaryGoal?.value?.takeIf { it > 0 }
        val inputs = plan.calculationSnapshot?.inputs.orEmpty()
        val activity = inputs["activity"]
            ?.let { raw -> runCatching { com.example.kpkn.domain.nutrition.EerActivity.valueOf(raw) }.getOrNull() }
            ?: eerActivityFromSettingsLevel(settings.nutritionActivityLevel)
        val isAuto = plan.calculationOrigin != com.example.kpkn.data.models.CalculationOrigin.MANUAL &&
            plan.calculationOrigin != com.example.kpkn.data.models.CalculationOrigin.PROFESSIONAL
        val unitName = weightUnitCode(settings.weightUnit)
        val snapAge = inputs["ageYears"]?.let { formatCleanNumber(it.toDoubleOrNull()) }.orEmpty()
        val snapHeight = inputs["heightCm"]?.let { formatCleanNumber(it.toDoubleOrNull()) }.orEmpty()
        val snapWeightKg = inputs["weightKg"]?.toDoubleOrNull() ?: settings.userVitals.weight
        val resolvedTargetText = if (goalMetric == GoalMetric.WEIGHT) {
            formatWeightForUnit(target, unitName)
        } else {
            formatCleanNumber(target)
        }
        val initialDraft = NutritionPlanEditorDraft(
            planId = plan.id,
            direction = plan.direction,
            goalMetric = goalMetric,
            targetValueText = resolvedTargetText,
            ageText = snapAge.ifBlank { settings.userVitals.age?.toString().orEmpty() },
            heightText = snapHeight.ifBlank { formatCleanNumber(settings.userVitals.height) },
            weightText = formatWeightForUnit(snapWeightKg, unitName),
            weightUnit = unitName,
            equationSex = equationSexOfPlan(plan),
            activity = activity,
            bodyFatText = formatCleanNumber(plan.startValue?.takeIf { goalMetric == GoalMetric.BODY_FAT }),
            muscleText = formatCleanNumber(plan.startValue?.takeIf { goalMetric == GoalMetric.MUSCLE_MASS }),
            base = if (isAuto) null else NutritionEditorBase(
                caloriesKcal = plan.calorieTarget,
                proteinG = plan.proteinGoal,
                carbsG = plan.carbGoal,
                fatG = plan.fatGoal,
            ),
            baseEdited = !isAuto,
            provenance = when (plan.calculationOrigin) {
                com.example.kpkn.data.models.CalculationOrigin.PROFESSIONAL -> NutritionEditorProvenance.PROFESSIONAL
                com.example.kpkn.data.models.CalculationOrigin.MANUAL -> NutritionEditorProvenance.SELF_DEFINED
                else -> NutritionEditorProvenance.AUTOMATIC
            },
            weeklyDistribution = inputs["weeklyDistribution"]
                ?.let { value -> runCatching { NutritionWeeklyDistributionMode.valueOf(value) }.getOrNull() }
                ?: NutritionWeeklyDistributionMode.VARIABLE,
        )
        if (!isAuto) return initialDraft
        val savedBase = NutritionEditorBase(
            caloriesKcal = plan.calorieTarget,
            proteinG = plan.proteinGoal,
            carbsG = plan.carbGoal,
            fatG = plan.fatGoal,
        )
        val defaultAutoBase = reviewedBaseOf(initialDraft, recommendationFor(initialDraft))
        return if (savedBase.hasAnyValue && savedBase != defaultAutoBase) {
            initialDraft.copy(base = savedBase, baseEdited = true)
        } else {
            initialDraft
        }
    }

    private fun weightUnitCode(unit: com.example.kpkn.data.models.WeightUnit): String =
        if (unit == com.example.kpkn.data.models.WeightUnit.LBS) "lb" else "kg"

    private fun formatWeightForUnit(weightKg: Double?, unit: String): String {
        val kg = weightKg?.takeIf { it.isFinite() && it > 0.0 } ?: return ""
        val value = if (unit.lowercase().startsWith("lb")) kg * 2.20462262185 else kg
        return formatCleanNumber(value)
    }

    private fun formatCleanNumber(value: Double?): String {
        if (value == null || !value.isFinite() || value <= 0.0) return ""
        val rounded = kotlin.math.round(value)
        return if (kotlin.math.abs(value - rounded) < 0.05) {
            rounded.toInt().toString()
        } else {
            String.format(java.util.Locale.US, "%.1f", value)
        }
    }

    private fun eerActivityFromSettingsLevel(level: Int): com.example.kpkn.domain.nutrition.EerActivity = when (level) {
        1 -> com.example.kpkn.domain.nutrition.EerActivity.INACTIVE
        2 -> com.example.kpkn.domain.nutrition.EerActivity.LOW_ACTIVE
        3 -> com.example.kpkn.domain.nutrition.EerActivity.ACTIVE
        4, 5 -> com.example.kpkn.domain.nutrition.EerActivity.VERY_ACTIVE
        else -> com.example.kpkn.domain.nutrition.EerActivity.ACTIVE
    }

    private fun settingsLevelFromEerActivity(activity: com.example.kpkn.domain.nutrition.EerActivity): Int = when (activity) {
        com.example.kpkn.domain.nutrition.EerActivity.INACTIVE -> 1
        com.example.kpkn.domain.nutrition.EerActivity.LOW_ACTIVE -> 2
        com.example.kpkn.domain.nutrition.EerActivity.ACTIVE -> 3
        com.example.kpkn.domain.nutrition.EerActivity.VERY_ACTIVE -> 4
    }

    private fun draftFromWizard(wizard: NutritionWizardDraft, pendingDraftId: String?): NutritionPlanEditorDraft =
        // Misma traducción que el alta: UN solo algoritmo en domain/nutrition.
        com.example.kpkn.domain.nutrition.editorDraftOf(wizard, pendingDraftId)

    private companion object {
        const val WEEK_DAYS = 7L
    }
}
