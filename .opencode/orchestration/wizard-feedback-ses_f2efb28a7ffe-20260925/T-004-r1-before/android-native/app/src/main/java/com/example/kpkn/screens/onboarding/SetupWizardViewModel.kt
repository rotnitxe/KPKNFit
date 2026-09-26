package com.example.kpkn.screens.onboarding

import android.app.Application
import android.util.Log
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.SavedStateHandle
import androidx.lifecycle.viewModelScope
import com.example.kpkn.data.exercises.catalogv2.ApprovedAssetExerciseCatalogRepositoryV2
import com.example.kpkn.data.exercises.catalogv2.CatalogCompositionMetadataProvider
import com.example.kpkn.data.exercises.catalogv2.toLegacyConfigurationLookup
import com.example.kpkn.data.models.*
import com.example.kpkn.data.models.DISCOMFORT_CATALOG_BY_ID
import com.example.kpkn.data.onboarding.*
import com.example.kpkn.data.programs.CatalogLevel
import com.example.kpkn.data.programs.CatalogSource
import com.example.kpkn.data.programs.PersonalizedPlanCatalog
import com.example.kpkn.data.programs.TrainingFocus
import com.example.kpkn.data.protocols.PROTOCOL_LIBRARY
import com.example.kpkn.domain.exercises.catalogv2.ExerciseCatalogStateV2
import com.example.kpkn.domain.nutrition.NutritionConfigurationMode
import com.example.kpkn.domain.nutrition.kilogramsFromInput
import com.example.kpkn.domain.nutrition.parseLocalizedNumber
import com.example.kpkn.domain.onboarding.*
import com.example.kpkn.domain.training.*
import com.example.kpkn.screens.nutrition.NutritionWizardDraft
import java.time.LocalDate
import java.util.UUID
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.NonCancellable
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withContext
import kotlinx.coroutines.currentCoroutineContext
import kotlinx.coroutines.ensureActive
import kotlinx.serialization.decodeFromString
import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.Json

/** Single serialized WIZCHAT orchestrator. Room owns the full draft; SavedStateHandle owns IDs only. */
class SetupWizardViewModel @JvmOverloads constructor(
    application: Application,
    private val savedStateHandle: SavedStateHandle,
    private val persistence: SetupWizardPersistence = realSetupWizardPersistence(application),
    private val environment: SetupWizardEnvironment = RealSetupWizardEnvironment(application.applicationContext),
    private val commits: SetupWizardCommits? = null,
    /**
     * Puerto de materialización opcional: producción usa el motor real del VM
     * ([SetupWizardViewModel.materializeProgram]); las tests inyectan el suyo
     * para controlar orden y tiempo de los previews (carrera A→B→A).
     */
    private val materializeOverride: SetupWizardMaterializer? = null,
) : AndroidViewModel(application) {
    private val json = Json { ignoreUnknownKeys = true; encodeDefaults = true }
    private val commandMutex = Mutex()
    private val catalogRepository = ApprovedAssetExerciseCatalogRepositoryV2(application.applicationContext)
    private var catalogLoaded = false
    private var initialized = false
    private var initializeJob: kotlinx.coroutines.Job? = null
    private var currentDraftId: String? = null
    private var previewJob: kotlinx.coroutines.Job? = null
    private var candidateJob: kotlinx.coroutines.Job? = null
    private var exerciseSearchJob: kotlinx.coroutines.Job? = null
    @Volatile private var exerciseLookup: List<ExerciseMuscleInfo>? = null
    @Volatile private var navigationInFlight = false
    private var ringsPreviewJob: kotlinx.coroutines.Job? = null
    private var lastRingsPreviewKey: List<Any?>? = null
    /** Clock injected into the last rings preview so preview and stored check-in share one date. */
    @Volatile private var ringsPreviewNow: Long? = null
    private var preparingTrainingKey: List<Any?>? = null
    private var lastSuccessfulTrainingKey: List<Any?>? = null
    /**
     * Generación del cálculo de preview. Cada lanzamiento (o liberación de
     * caché) la incrementa y se hace DUEÑO de `isPreviewLoading` y
     * `preparingTrainingKey`: sólo el job dueño publica o limpia, un job viejo
     * cancelado o tardío no escribe nada y una cancelación nunca publica error.
     */
    @Volatile private var previewGeneration = 0L

    private val _state = MutableStateFlow(SetupWizardState(SetupWizardDraft(commitId = UUID.randomUUID().toString()), isLoading = true))
    val state: StateFlow<SetupWizardState> = _state.asStateFlow()

    fun initialize(mode: SetupWizardMode, nutritionMode: String = "create", nutritionPlanId: String? = null, draftId: String? = null) {
        if (initialized && _state.value.mode == mode && (draftId == null || draftId == currentDraftId)) return
        initializeJob?.cancel()
        initialized = false
        // Invalida la generación del preview, cancela el job en vuelo y apaga
        // su loading: un cancelado de aquí jamás deja `isPreviewLoading` eterno.
        releasePreviewGeneration(cancelInFlight = true)
        candidateJob?.cancel()
        exerciseSearchJob?.cancel()
        ringsPreviewJob?.cancel()
        lastSuccessfulTrainingKey = null
        lastRingsPreviewKey = null
        ringsPreviewNow = null
        // La intención de volver a la revisión vive en el BORRADOR
        // (`reviewReturnStep`): aquí NO se reinicia para que sobreviva a
        // guardar/salir y a la recreación del ViewModel.
        _state.value = _state.value.copy(mode = mode, isLoading = true, machineState = WizChatMachineState.Loading,
            programPreview = null, planCandidates = emptyList(), availablePlanCandidates = emptyList(),
            isPreviewLoading = false,
            nutritionPlanPreview = null, nutritionPreparation = null, ringsBatteriesPreview = null, ringsCoveragePreview = null,
            errors = emptyMap(), lastFailure = null)
        initializeJob = viewModelScope.launch {
            try {
                environment.awaitReady()
                val storedCandidate = savedStateHandle.get<String>(DRAFT_ID_KEY)?.takeIf(String::isNotBlank)
                val storedId = storedCandidate?.takeIf { mode == SetupWizardMode.RESUME || it.startsWith(SetupDraftResolver.canonicalDraftId(scopeFor(mode))) }
                val resumeId = if (mode == SetupWizardMode.RESUME) persistence.listRecoverable().firstOrNull()?.draftId else null
                val id: String = draftId ?: storedId ?: resumeId ?: SetupDraftResolver.canonicalDraftId(scopeFor(mode))
                val persisted = persistence.load(id)
                val restored = persisted?.let { runCatching { json.decodeFromString<SetupWizardDraft>(it.payloadJson) }.getOrNull() }
                if (persisted != null && (restored == null || restored.wizChat.schemaVersion > 2 || restored.wizChat.scriptVersion > WizChatCopyCatalog.SCRIPT_VERSION)) {
                    currentDraftId = id
                    savedStateHandle[DRAFT_ID_KEY] = id
                    _state.value = _state.value.copy(isLoading = false, machineState = WizChatMachineState.UnsupportedDraft, errors = mapOf("draft" to "Este borrador no es compatible con la versión actual. Puedes conservarlo y comenzar uno nuevo."))
                    return@launch
                }
                val normalized = (restored ?: newDraft(mode, nutritionMode, nutritionPlanId, id)).let { value ->
                    val realScope = value.draftScope.ifBlank { scopeFor(mode).name.lowercase() }
                        .let { if (it == "resume") "full" else it }
                    value.copy(
                        draftId = id,
                        draftScope = realScope,
                        nutritionMode = nutritionMode.takeIf { restored == null } ?: value.nutritionMode,
                        nutritionPlanId = nutritionPlanId ?: value.nutritionPlanId,
                        wizChat = normalizeProgress(value.wizChat, realScope, mode),
                    )
                }.let { SetupDraftCompatibility.repair(it) }
                if (restored != null && normalized.stepProgress.origin == SetupProgressOrigin.NOT_CONVERTIBLE) {
                    // Borrador no convertible: se conserva íntegro (sin perder
                    // datos de desarrollo) hasta que el usuario confirme el descarte.
                    currentDraftId = id
                    savedStateHandle[DRAFT_ID_KEY] = id
                    _state.value = _state.value.copy(isLoading = false, machineState = WizChatMachineState.UnsupportedDraft,
                        errors = mapOf("draft" to "Este borrador no es compatible con la versión actual. Se conserva tal cual; puedes descartarlo cuando quieras."))
                    return@launch
                }
                // Catálogo cambiado: se conservan todas las respuestas; solo se
                // señala qué selección necesita revisión (regla pura testeable).
                val draft = SetupDraftCompatibility.applyCatalogRevision(
                    normalized,
                    persisted?.catalogRevision,
                    PersonalizedPlanCatalog.REVISION,
                ) { planId -> PersonalizedPlanCatalog.find(planId) != null }
                if (mode == SetupWizardMode.RESUME) {
                    _state.value = _state.value.copy(mode = when (SetupDraftResolver.scopeOf(draft.draftScope)) {
                        SetupDraftScope.TRAINING_ONLY -> SetupWizardMode.TRAINING_ONLY
                        SetupDraftScope.NUTRITION_ONLY -> SetupWizardMode.NUTRITION_ONLY
                        SetupDraftScope.RINGS_ONLY -> SetupWizardMode.RINGS_ONLY
                        else -> SetupWizardMode.FULL
                    })
                }
                currentDraftId = draft.draftId
                savedStateHandle[DRAFT_ID_KEY] = draft.draftId
                val migrated = if (restored != null && draft != restored) draft.copy(
                    revision = maxOf(draft.revision, persisted?.revision?.toInt() ?: draft.revision) + 1,
                ) else draft
                if (migrated !== draft && !persistDraft(migrated)) {
                    _state.value = _state.value.copy(isLoading = false, machineState = WizChatMachineState.UnsupportedDraft,
                        errors = mapOf("draft" to "No pude actualizar este borrador. Lo conservé para que puedas recuperarlo."))
                    return@launch
                }
                initialized = true
                publishDraft(migrated, restored != null, WizChatMachineState.AwaitingAnswer)
                updateCandidates(migrated)
                preparePreview(migrated)
            } catch (cancel: CancellationException) { throw cancel }
            catch (error: Throwable) {
                _state.value = _state.value.copy(isLoading = false, machineState = WizChatMachineState.RecoverableError, errors = mapOf("initialize" to (error.message ?: "No se pudo cargar el asistente")), lastFailure = error.message)
            }
        }
    }

    // ── Paso → datos: única vía productiva de la UI ─────────────────────────
    //
    // Cada setter escribe el dato del paso, lo marca como declarado por el
    // usuario y persiste con revisión monótona. Ninguno mueve el cursor: solo
    // [submitCurrentStep] confirma y avanza exactamente un paso.

    /** Selección única del paso; guarda el valor estable de sus opciones. */
    fun setStepChoice(step: SetupStepId, value: String) =
        mutateDraft(step) { draft -> draft.withStepChoice(step, value, System.currentTimeMillis()) }

    /** Selección múltiple (días, equipo, molestias…); nunca inventa valores. */
    fun setStepChoices(step: SetupStepId, values: Set<String>) =
        mutateDraft(step) { draft -> draft.withStepChoices(step, values, System.currentTimeMillis()) }

    /**
     * Alterna una opción de un paso multi a partir del **evento** de la tarjeta
     * (solo el valor estable, sin Set calculado en la UI).
     *
     * El conjunto resultante se deriva SIEMPRE del último borrador dentro del
     * mutex ([setupToggleExclusive] sobre `latest.selectedValues`), de modo que
     * dos toques seguidos sin esperar el primer persisto no se pisan: el segundo
     * parte del resultado del primero y un valor excluyente (`none`/`unknown`/
     * `omit`) desplaza al resto. No avanza ni confirma: el cursor lo mueve solo
     * [submitCurrentStep].
     */
    fun toggleStepChoice(step: SetupStepId, value: String) =
        mutateDraft(step) { latest ->
            latest.withStepChoices(
                step,
                setupToggleExclusive(
                    latest.selectedValues(step),
                    value,
                    SetupStepDefinitions.of(step)?.exclusiveValues.orEmpty(),
                ),
                System.currentTimeMillis(),
            )
        }

    /** Texto libre del paso (nombre, filas editoriales). */
    fun setStepText(step: SetupStepId, value: String) =
        mutateDraft(step) { draft -> draft.withStepText(step, value, System.currentTimeMillis()) }

    /** Número del paso; [value] null retira el dato (nunca deja un default). */
    fun setStepNumber(step: SetupStepId, value: Double?) =
        mutateDraft(step) { draft -> draft.withStepNumber(step, value, System.currentTimeMillis()) }

    /**
     * Escritura tipada arbitraria sobre el paso (p. ej. el plan elegido o las
     * filas de marcas). Solo persiste el borrador: no toca `acceptedAnswers`
     * del espejo legacy y no confirma el paso.
     */
    fun updateStep(step: SetupStepId, change: (SetupWizardDraft) -> SetupWizardDraft) =
        mutateDraft(step, change)

    /**
     * Omite el paso SOLO si su definición lo permite. Registra procedencia y
     * estado ausente sin fabricar ningún dato, y no avanza: la confirmación
     * sigue siendo [submitCurrentStep].
     */
    fun skipStep(step: SetupStepId) {
        val definition = SetupStepDefinitions.of(step)
        if (definition == null || definition.legacyOnly || !definition.allowSkip) {
            _state.value = _state.value.copy(errors = _state.value.errors + (step.name to "Este paso no se puede omitir"))
            return
        }
        mutateDraft(step) { draft ->
            draft.recordStepAnswer(step, SetupAnswerProvenance.USER_DECLARED, SetupValueState.ABSENT)
        }
    }

    /**
     * Vuelve a un paso anterior sin borrar nada. Si se edita desde la
     * revisión final y al confirmar la ruta sigue siendo válida, el cursor
     * regresa a la revisión: no se vuelve a contestar todo el formulario.
     */
    fun editStep(step: SetupStepId) {
        viewModelScope.launch { commandMutex.withLock {
            val state = _state.value
            if (!initialized || state.isCommitting || state.isSubmittingAnswer || state.isSavingAndExiting ||
                state.machineState == WizChatMachineState.Committed ||
                state.machineState == WizChatMachineState.UnsupportedDraft
            ) return@withLock
            val draft = state.draft
            if (step == draft.stepProgress.currentStepId) return@withLock
            if (step !in SetupStepGraph.stepIds(draft.stepContext())) return@withLock
            // La intención de volver a la revisión vive en el BORRADOR
            // (`reviewReturnStep`), no en un campo volatile: así sobrevive a
            // guardar/salir y a la recreación del ViewModel. Solo una edición
            // empezada desde la revisión final la deja puesta.
            val fromReview = draft.stepProgress.currentStepId == SetupStepId.REVIEW_ACTIVATE
            persistAndPublish(
                draft.editStep(step).copy(
                    reviewReturnStep = if (fromReview) SetupStepId.REVIEW_ACTIVATE else null,
                ),
            )
        } }
    }

    /**
     * Solo selecciona el plan: escribe el id elegido en el paso PLAN sin
     * validar candidatos, sin tocar el espejo conversacional y sin avanzar el
     * paso (la confirmación sigue siendo [submitCurrentStep]).
     */
    fun selectPlan(id: String) = updateStep(SetupStepId.PLAN) { draft ->
        if (draft.trainingPath == SetupTrainingPath.FROM_SCRATCH) draft
        else draft.copy(selectedCatalogId = id, acceptFixedRecipeDifference = false)
    }

    fun setWeightUnit(unit: String) = mutateDraft(step = SetupStepId.WEIGHT) { draft ->
        if (unit !in setOf("kg", "lb") || unit == draft.weightUnit) draft else {
            val nutrition = draft.nutritionDraft?.let { n ->
                val existing = parseLocalizedNumber(n.targetWeightText)
                val kg = existing?.let { kilogramsFromInput(it, n.weightUnit) }
                val targetText = kg?.let { WizChatWeightScale.format(WizChatWeightScale.snap(WizChatWeightScale.toDisplay(it, unit))) }
                n.copy(weightUnit = unit, targetWeightText = targetText ?: n.targetWeightText,
                    targetValueText = if (n.goalMetric == GoalMetric.WEIGHT) targetText ?: n.targetValueText else n.targetValueText)
            }
            draft.copy(weightUnit = unit, weightUnitChanged = true, nutritionDraft = nutrition,
                wizChat = draft.wizChat.copy(revision = draft.wizChat.revision + 1))
        }
    }
    fun updateNutritionInput(update: (NutritionWizardDraft) -> NutritionWizardDraft) = mutateDraft {
        val current = it.nutritionDraft ?: NutritionWizardDraft(mode = it.nutritionMode, planId = it.nutritionPlanId)
        it.copy(nutritionDraft = update(current))
    }
    fun toggleSound() = mutateDraft { it.copy(wizChat = it.wizChat.copy(soundEnabled = !it.wizChat.soundEnabled)) }
    fun confirmActivation(confirmed: Boolean) = mutateDraft { it.copy(confirmActivation = confirmed) }
    fun acceptFixedRecipeDifference(accepted: Boolean) = mutateDraft { it.copy(acceptFixedRecipeDifference = accepted) }
    private fun toggleBranch(branch: String) = mutateDraft { draft ->
        val branches = draft.wizChat.advancedBranches
        draft.copy(wizChat = draft.wizChat.copy(advancedBranches = if (branch in branches) branches - branch else branches + branch))
    }
    fun openAdvanced() = toggleBranch("training")
    fun openNutritionAdvanced() = toggleBranch("nutrition")
    fun openRingsAdvanced() = toggleBranch("rings")
    fun setRingsMuscleScope(scope: InitialRecoveryMuscleScope) = mutateDraft { draft ->
        val answers = draft.ringsAnswers ?: SetupRingsAnswers()
        val selected = if (scope == InitialRecoveryMuscleScope.FULL_BODY) emptySet() else answers.recentMuscles
        val allowed = when (scope) {
            InitialRecoveryMuscleScope.FULL_BODY -> InitialRecoveryEvidenceFactory.INITIAL_RECOVERY_MUSCLE_PILLARS.toSet()
            InitialRecoveryMuscleScope.SELECTED -> selected
            InitialRecoveryMuscleScope.UNKNOWN -> emptySet()
        }
        draft.copy(ringsAnswers = answers.copy(muscleScope = scope, recentMuscles = selected),
            manualMuscleOverrides = draft.manualMuscleOverrides.filterKeys { it in allowed })
    }
    fun toggleRingsMuscle(muscle: String) = mutateDraft { draft ->
        val answers = draft.ringsAnswers ?: SetupRingsAnswers(muscleScope = InitialRecoveryMuscleScope.SELECTED)
        val selected = if (muscle in answers.recentMuscles) answers.recentMuscles - muscle else answers.recentMuscles + muscle
        draft.copy(ringsAnswers = answers.copy(muscleScope = InitialRecoveryMuscleScope.SELECTED, recentMuscles = selected),
            manualMuscleOverrides = draft.manualMuscleOverrides.filterKeys { it in selected })
    }
    fun setManualMuscleOverride(muscle: String, level: Int) = mutateDraft { draft ->
        val a = draft.ringsAnswers
        val allowed = when (a?.muscleScope) {
            InitialRecoveryMuscleScope.FULL_BODY -> muscle in InitialRecoveryEvidenceFactory.INITIAL_RECOVERY_MUSCLE_PILLARS
            InitialRecoveryMuscleScope.SELECTED -> muscle in a.recentMuscles
            else -> false
        }
        if (allowed) draft.copy(manualMuscleOverrides = draft.manualMuscleOverrides + (muscle to level.coerceIn(1, 5))) else draft
    }
    fun setManualEnergyOverride(level: Int) = mutateDraft { it.copy(manualEnergyOverride = level.coerceIn(1, 5)) }
    fun setManualStructureOverride(level: Int) = mutateDraft { it.copy(manualStructureOverride = level.coerceIn(1, 5)) }
    fun candidatePlans(): List<SetupPlanCandidate> = _state.value.planCandidates
    fun showMoreCandidates() {
        val current = _state.value
        _state.value = current.copy(planCandidates = current.availablePlanCandidates.take(current.planCandidates.size + 3))
    }
    fun activeNutritionPlan(): NutritionPlan? = environment.activeNutritionPlan()
    fun hasInitialRecoveryEvidence(): Boolean = environment.hasInitialRecoveryEvidence()
    fun searchExercises(query: String) {
        exerciseSearchJob?.cancel()
        val term = query.trim()
        if (term.isBlank()) {
            _state.value = _state.value.copy(exerciseSuggestions = emptyList(), isExerciseSearching = false, exerciseSearchError = null)
            return
        }
        _state.value = _state.value.copy(exerciseSuggestions = emptyList(), isExerciseSearching = true, exerciseSearchError = null)
        exerciseSearchJob = viewModelScope.launch {
            try {
                ensureCatalogLoaded()
                val matches = withContext(Dispatchers.IO) {
                    val all = exerciseLookup ?: (catalogRepository.state.value as? ExerciseCatalogStateV2.Ready)
                        ?.catalog?.toLegacyConfigurationLookup()?.values?.distinctBy { it.id }
                        .orEmpty().also { exerciseLookup = it }
                    all.asSequence().filter { it.name.contains(term, ignoreCase = true) }.take(14).toList()
                }
                currentCoroutineContext().ensureActive()
                if (_state.value.draft.stepProgress.currentStepId == SetupStepId.PLAN) _state.value = _state.value.copy(
                    exerciseSuggestions = matches, isExerciseSearching = false)
            } catch (cancel: CancellationException) { throw cancel }
            catch (error: Exception) {
                if (_state.value.draft.stepProgress.currentStepId == SetupStepId.PLAN) _state.value = _state.value.copy(
                    exerciseSuggestions = emptyList(), isExerciseSearching = false,
                    exerciseSearchError = "No pude consultar los ejercicios. Inténtalo otra vez.")
            }
        }
    }
    fun ringsPreview(): SetupRingsMapping = ringsMapping(_state.value.draft)
    fun retryRingsPreview() {
        if (_state.value.machineState == WizChatMachineState.Committed || _state.value.isCommitting) return
        lastRingsPreviewKey = null
        ringsPreviewNow = null
        _state.value = _state.value.copy(ringsPreviewError = null, errors = _state.value.errors - "rings_preview")
        prepareRingsPreview(_state.value.draft)
    }

    /**
     * Specific retry for one inline error: every failure keeps the key of the
     * operation that produced it, so the UI never offers a generic reload for
     * a save/preview/commit problem.
     */
    fun retryOperationForError(key: String): SetupRetryOperation? = when (key) {
        "initialize" -> SetupRetryOperation.LOAD
        "save" -> SetupRetryOperation.SAVE
        "preview" -> SetupRetryOperation.PREVIEW
        "candidates" -> SetupRetryOperation.CANDIDATES
        "rings_preview" -> SetupRetryOperation.RINGS_PREVIEW
        "commit" -> SetupRetryOperation.COMMIT
        else -> null
    }

    /**
     * Retries the operation that failed. LOAD re-runs initialization (modal
     * RecoverableError only) or, when the draft is already loaded, persists the
     * in-memory draft instead of silently doing nothing; SAVE repersists without
     * losing unpersisted answers; the rest recompute the corresponding preview.
     */
    fun retryFailedOperation(operation: SetupRetryOperation, expectedRevision: Int? = null) {
        when (operation) {
            SetupRetryOperation.LOAD -> {
                _state.value = _state.value.copy(errors = emptyMap(), lastFailure = null)
                if (initialized) retryPersist() else initialize(_state.value.mode, draftId = currentDraftId)
            }
            SetupRetryOperation.SAVE -> retryPersist()
            SetupRetryOperation.PREVIEW -> retryPreview()
            SetupRetryOperation.CANDIDATES -> retryCandidates()
            SetupRetryOperation.RINGS_PREVIEW -> retryRingsPreview()
            SetupRetryOperation.COMMIT -> retryCommit(expectedRevision)
        }
    }

    /** Dismisses one inline error (e.g. closing the banner). */
    fun clearError(key: String) {
        if (key !in _state.value.errors) return
        _state.value = _state.value.copy(errors = _state.value.errors - key)
    }

    /** Dismisses every inline error. */
    fun clearErrors() {
        if (_state.value.errors.isEmpty()) return
        _state.value = _state.value.copy(errors = emptyMap())
    }

    private fun retryPersist() {
        viewModelScope.launch { commandMutex.withLock {
            val current = _state.value
            if (!initialized || current.isCommitting || current.machineState == WizChatMachineState.Committed) return@withLock
            // Reintenta el guardado del borrador en memoria sin perder respuestas:
            // la revisión avanza de forma monótona frente a la fila guardada.
            val next = current.draft.withNextDraftRevision(current.draft)
            _state.value = current.copy(machineState = WizChatMachineState.PersistingAnswer, errors = emptyMap(), isSubmittingAnswer = true)
            if (persistDraft(next)) {
                publishDraft(next, true, WizChatMachineState.AwaitingAnswer)
            } else {
                _state.value = _state.value.copy(draft = next, machineState = WizChatMachineState.AwaitingAnswer, dirty = true,
                    isSubmittingAnswer = false)
            }
        } }
    }

    private fun retryPreview() {
        lastSuccessfulTrainingKey = null
        _state.value = _state.value.copy(previewError = null, errors = _state.value.errors - "preview")
        preparePreview(_state.value.draft)
    }

    private fun retryCandidates() {
        _state.value = _state.value.copy(previewError = null, errors = _state.value.errors - "candidates")
        updateCandidates(_state.value.draft)
    }

    private fun retryCommit(expectedRevision: Int?) {
        viewModelScope.launch {
            if (expectedRevision != null && _state.value.draft.revision != expectedRevision) return@launch
            commit()
        }
    }

    /** Back never deletes answers; it only moves the step cursor. */
    fun back(): Boolean = goBack()

    /** Whether the step cursor can move one step back. */
    fun canGoBack(): Boolean {
        if (!initialized || _state.value.isCommitting || _state.value.machineState == WizChatMachineState.Committed) return false
        return SetupStepGraph.previous(_state.value.draft.stepProgress.currentStepId,
            _state.value.draft.stepContext(), _state.value.draft.stepProgress.visited) != null
    }

    fun goBack(): Boolean {
        if (!initialized || _state.value.isCommitting || _state.value.machineState == WizChatMachineState.Committed) return false
        val expectedStep = _state.value.draft.stepProgress.currentStepId
        if (SetupStepGraph.previous(expectedStep, _state.value.draft.stepContext(), _state.value.draft.stepProgress.visited) == null) return false
        if (navigationInFlight) return false
        navigationInFlight = true
        viewModelScope.launch {
            try { commandMutex.withLock {
                if (_state.value.draft.stepProgress.currentStepId != expectedStep) return@withLock
                // Navegación manual: se retira la intención de volver a la
                // revisión en el BORRADOR persistido (nunca en memoria volátil).
                persistAndPublish(_state.value.draft.copy(reviewReturnStep = null).goBack())
            } } finally { navigationInFlight = false }
        }
        return true
    }

    /**
     * Safe delegation to [submitCurrentStep]: the same gate, revisions and
     * exactly-once advance that the Continuar CTA uses.
     */
    fun goNext(): Boolean = submitCurrentStep(_state.value.draft.stepProgress.currentStepId).accepted

    /**
     * Confirms and advances exactly one step. Repeated callbacks are dropped:
     * the synchronous gate rejects while a confirmation is in flight, and the
     * cursor/revision are re-checked inside the serialized queue, so a stale
     * callback can never double-advance.
     */
    fun submitCurrentStep(
        step: SetupStepId = _state.value.draft.stepProgress.currentStepId,
        expectedRevision: Int? = null,
    ): SetupSubmitResult {
        val current = _state.value
        if (!initialized || current.isCommitting || current.machineState == WizChatMachineState.Committed ||
            current.machineState == WizChatMachineState.UnsupportedDraft) {
            Log.w(DIAG_TAG, "submit $step → DROP initialized=$initialized committing=${current.isCommitting} machine=${current.machineState}")
            return SetupSubmitResult(SetupSubmitOutcome.DROPPED)
        }
        if (navigationInFlight || current.isSubmittingAnswer || current.isSavingAndExiting ||
            step != current.draft.stepProgress.currentStepId) {
            Log.w(DIAG_TAG, "submit $step → DROP navigation=$navigationInFlight submitting=${current.isSubmittingAnswer} saving=${current.isSavingAndExiting} cursor=${current.draft.stepProgress.currentStepId}")
            return SetupSubmitResult(SetupSubmitOutcome.DROPPED)
        }
        if (expectedRevision != null && expectedRevision != current.draft.revision) {
            Log.w(DIAG_TAG, "submit $step → DROP revision expected=$expectedRevision actual=${current.draft.revision}")
            return SetupSubmitResult(SetupSubmitOutcome.DROPPED)
        }
        val validation = SetupWizardValidation.validateStep(current.draft, step)
        if (validation.any { it.isBlocking }) {
            // Solo claves de validación: nunca el valor del usuario.
            Log.w(DIAG_TAG, "submit $step → REJECT ${validation.filter { it.isBlocking }.map { it.key }}")
            _state.value = current.copy(errors = validation.mapNotNull { check -> check.message?.let { check.key to it } }.toMap())
            return SetupSubmitResult(SetupSubmitOutcome.REJECTED)
        }
        navigationInFlight = true
        Log.d(DIAG_TAG, "submit $step → ACCEPTED (encolado, cursor=${current.draft.stepProgress.currentStepId} rev=${current.draft.revision})")
        viewModelScope.launch {
            try { commandMutex.withLock { submitCurrentStepLocked(current.draft, step, expectedRevision) } }
            finally { navigationInFlight = false }
        }
        return SetupSubmitResult(SetupSubmitOutcome.ACCEPTED)
    }

    /** Runs fully under the command mutex: re-validates, records, advances, persists. */
    private suspend fun submitCurrentStepLocked(snapshot: SetupWizardDraft, expectedStep: SetupStepId, expectedRevision: Int?) {
        val current = _state.value
        if (!initialized || current.isCommitting || current.machineState == WizChatMachineState.Committed) {
            Log.w(DIAG_TAG, "submitLocked $expectedStep → DROP initialized=$initialized committing=${current.isCommitting} machine=${current.machineState}")
            return
        }
        if (expectedStep != current.draft.stepProgress.currentStepId) {
            Log.w(DIAG_TAG, "submitLocked $expectedStep → DROP cursor=${current.draft.stepProgress.currentStepId}")
            return
        }
        if (expectedRevision != null && expectedRevision != current.draft.revision) {
            Log.w(DIAG_TAG, "submitLocked $expectedStep → DROP revision expected=$expectedRevision actual=${current.draft.revision}")
            return
        }
        if (snapshot.draftId != current.draft.draftId) {
            Log.w(DIAG_TAG, "submitLocked $expectedStep → DROP draftId ${snapshot.draftId} ≠ ${current.draft.draftId}")
            return
        }
        val validation = SetupWizardValidation.validateStep(current.draft, expectedStep)
        if (validation.any { it.isBlocking }) {
            Log.w(DIAG_TAG, "submitLocked $expectedStep → REJECT ${validation.filter { it.isBlocking }.map { it.key }}")
            _state.value = _state.value.copy(errors = validation.mapNotNull { check -> check.message?.let { check.key to it } }.toMap())
            return
        }
        val previous = current.draft
        // Una sola confirmación por llamada: el avance se calcula una vez y la
        // revisión siempre supera la fila guardada.
        val confirmed = resumeReviewAfterEdit(previous.confirmCurrentStep(expectedStep), expectedStep)
            .withNextDraftRevision(previous)
        val previousKey = trainingKey(previous)
        _state.value = _state.value.copy(machineState = WizChatMachineState.PersistingAnswer, errors = emptyMap(), isSubmittingAnswer = true)
        val persisted = persistDraft(confirmed)
        if (persisted) {
            publishDraft(confirmed, true, WizChatMachineState.AwaitingAnswer)
        } else {
            _state.value = _state.value.copy(draft = confirmed, dirty = true, machineState = WizChatMachineState.AwaitingAnswer,
                isSubmittingAnswer = false)
        }
        Log.d(DIAG_TAG, "submitLocked $expectedStep → ${confirmed.stepProgress.currentStepId} rev=${confirmed.revision} persisted=$persisted")
        if (previousKey != trainingKey(confirmed)) updateCandidates(confirmed)
        preparePreview(confirmed)
    }

    /**
     * Editing from the final review and confirming the edited step lands back
     * on the review when the route still contains it and the step still
     * validates, so the user never re-answers the whole form.
     *
     * La intención ([SetupWizardDraft.reviewReturnStep]) vive en el borrador y
     * se consume AQUÍ, dentro del borrador confirmado que se persiste: sobrevive
     * a guardar/salir y a la recreación del ViewModel, y se consume exactamente
     * una vez.
     */
    private fun resumeReviewAfterEdit(confirmed: SetupWizardDraft, editedStep: SetupStepId): SetupWizardDraft {
        val target = confirmed.reviewReturnStep
        val consumed = confirmed.copy(reviewReturnStep = null)
        if (target != SetupStepId.REVIEW_ACTIVATE) return consumed
        if (target !in SetupStepGraph.stepIds(consumed.stepContext())) return consumed
        if (SetupWizardValidation.validateStep(consumed, editedStep).any { it.isBlocking }) return consumed
        return consumed.copy(
            stepProgress = consumed.stepProgress.at(target, consumed.stepContext()),
            wizChat = consumed.wizChat.copy(
                currentQuestionId = WizChatQuestionId.REVIEW,
                stage = WizChatStage.REVIEW,
                terminal = true,
                revision = consumed.wizChat.revision + 1,
            ),
        )
    }

    /** Compatibility entry point for advanced editor callers; same queue, same Room draft. */
    fun update(change: (SetupWizardDraft) -> SetupWizardDraft) = mutateDraft(change = change)
    fun setName(value: String) = setStepText(SetupStepId.NAME, value)
    fun setAge(value: Int?) = setStepNumber(SetupStepId.AGE, value?.toDouble())
    fun setWeightKg(value: Double?) = setStepNumber(SetupStepId.WEIGHT, value)
    fun setHeightCm(value: Double?) = setStepNumber(SetupStepId.HEIGHT, value)
    fun setChapter(chapter: SetupWizardChapter) = Unit
    fun setProgramRoute(route: SetupProgramRoute) = mutateDraft { it.copy(programRoute = route) }
    fun setModuleChoice(choice: SetupModuleChoice) = mutateDraft { it.copy(includeNutrition = choice == SetupModuleChoice.TRAINING_AND_NUTRITION) }
    fun setVolumeAnswer(change: (SetupVolumeAnswers) -> SetupVolumeAnswers) = mutateDraft { draft ->
        val answers = change(draft.volumeAnswers)
        val profile = buildVolumeProfile(draft.copy(volumeAnswers = answers))
        draft.copy(volumeAnswers = answers, volumeCalibrationProfile = profile, volumeRecommendations = profile?.recommendations.orEmpty(), athleteProfileScore = profile?.athleteProfileScore)
    }
    fun setPriorityMuscles(value: Set<String>) = mutateDraft {
        val priorities = value.map(String::trim).filter(String::isNotBlank).take(3).toSet()
        it.copy(priorityMuscles = priorities, lowerEmphasisMuscles = it.lowerEmphasisMuscles - priorities)
    }
    fun setLowerEmphasisMuscles(value: Set<String>) = mutateDraft {
        it.copy(lowerEmphasisMuscles = value.map(String::trim).filter(String::isNotBlank).toSet() - it.priorityMuscles)
    }
    fun resetManualRecoveryAdjustments() = mutateDraft { it.copy(manualMuscleOverrides = emptyMap(), manualEnergyOverride = null, manualStructureOverride = null) }

    fun addExercise(weekday: Int, info: ExerciseMuscleInfo) {
        val id = "${_state.value.draft.commitId}-exercise-$weekday-${info.id}"
        val exercise = Exercise(id = id, name = info.name, exerciseDbId = info.id, exerciseId = info.id, canonicalExerciseId = info.id, catalogConfigurationId = info.catalogConfigurationId, catalogDefinitionId = info.catalogDefinitionId, catalogRevision = info.catalogRevision, performanceProfileId = info.performanceProfileId, occurrenceId = id, effectiveMuscles = info.involvedMuscles, sets = (1..3).map { ExerciseSet("$id-set-$it", targetReps = 10) })
        mutateDraft { draft ->
            val sessions = draft.sessions.ensureSession(weekday).map { session -> if (session.weekday == weekday && session.exercises.none { it.exercise.exerciseDbId == info.id }) session.copy(exercises = session.exercises + SetupExerciseDraft(id, exercise, info)) else session }
            draft.copy(sessions = sessions)
        }
    }
    fun removeExercise(weekday: Int, id: String) = mutateDraft { draft -> draft.copy(sessions = draft.sessions.map { session -> if (session.weekday == weekday) session.copy(exercises = session.exercises.filterNot { it.id == id || it.exercise.exerciseDbId == id }) else session }) }
    fun renameSession(weekday: Int, title: String) = mutateDraft { draft ->
        val clean = WizChatValidation.cleanText(title).take(40)
        if (clean.isBlank()) draft else draft.copy(sessions = draft.sessions.ensureSession(weekday).map { session ->
            if (session.weekday == weekday) session.copy(title = clean) else session
        })
    }
    fun moveExercise(weekday: Int, id: String, delta: Int) = mutateDraft { draft -> draft.copy(sessions = draft.sessions.map { session ->
        if (session.weekday != weekday) session else {
            val from = session.exercises.indexOfFirst { it.id == id || it.exercise.exerciseDbId == id }
            if (from < 0) session else session.copy(exercises = session.exercises.toMutableList().also { list -> val to = (from + delta).coerceIn(0, list.lastIndex); list.add(to, list.removeAt(from)) })
        }
    }) }
    fun changeSets(weekday: Int, id: String, count: Int) = mutateDraft { draft -> draft.copy(sessions = draft.sessions.map { session -> if (session.weekday != weekday) session else session.copy(exercises = session.exercises.map { item -> if (item.id != id && item.exercise.exerciseDbId != id) item else item.copy(exercise = item.exercise.copy(sets = (1..count.coerceIn(1, 30)).map { n -> item.exercise.sets.getOrNull(n - 1)?.copy(id = "${item.exercise.id}-set-$n") ?: ExerciseSet("${item.exercise.id}-set-$n", targetReps = 10) })) }) }) }
    fun changeReps(weekday: Int, id: String, reps: String) = mutateDraft { draft -> draft.copy(sessions = draft.sessions.map { session -> if (session.weekday != weekday) session else session.copy(exercises = session.exercises.map { item -> if (item.id != id && item.exercise.exerciseDbId != id) item else item.copy(exercise = item.exercise.copy(sets = item.exercise.sets.map { it.copy(targetReps = reps.toIntOrNull()) })) }) }) }

    fun canContinue(): Boolean = _state.value.machineState == WizChatMachineState.AwaitingAnswer

    /**
     * Deprecated behaviour: it used to discard the draft without confirmation.
     * Now it only opens the explicit discard confirmation; the data survives
     * until [confirmDiscard] runs.
     */
    fun clear() = requestDiscard()

    /** Exit intention: opens the "Guardar y salir / Seguir configurando" dialog. */
    fun requestExit() {
        _state.value = _state.value.copy(dialog = session().requestExit().dialog)
    }

    fun keepConfiguring() {
        _state.value = _state.value.copy(dialog = session().keepConfiguring().dialog)
    }

    /** Separate discard intention; never reachable from the back button. */
    fun requestDiscard() {
        _state.value = _state.value.copy(dialog = session().requestDiscard().dialog)
    }

    /**
     * Saves and leaves. The save must finish before the caller abandons the
     * wizard: the persistence call runs non-cancellable and the result is only
     * published after it completes.
     */
    suspend fun saveAndExit(): Boolean = commandMutex.withLock {
        val plan = session().saveAndExit() as? SetupWizardExitPlan.SaveAndExit ?: return@withLock false
        // La revisión siempre avanza: el guardado no puede perder contra la fila
        // persistida aunque el borrador en memoria lleve cambios sin escribir.
        val previous = _state.value.draft
        val toSave = plan.draft.withNextDraftRevision(previous)
        val previousState = _state.value.machineState
        _state.value = _state.value.copy(isSavingAndExiting = true, machineState = WizChatMachineState.PersistingAnswer, errors = emptyMap())
        val saved = withContext(NonCancellable) { persistDraft(toSave) }
        if (saved) {
            val finished = session().onSavedAndExited()
            _state.value = _state.value.copy(draft = toSave, machineState = previousState,
                isSavingAndExiting = finished.isSavingAndExiting,
                exitCompleted = finished.exitCompleted, dialog = finished.dialog, dirty = false, errors = emptyMap(), lastFailure = null)
        } else {
            // No se navega cuando el guardado falla: el diálogo se mantiene y el
            // error queda inline para poder reintentar sin perder nada.
            _state.value = _state.value.copy(isSavingAndExiting = false, machineState = previousState)
        }
        saved
    }

    /** Discarding requires the explicit confirmation dialog; back never calls this. */
    suspend fun confirmDiscard(): Boolean = commandMutex.withLock {
        if (_state.value.isCommitting) return@withLock false
        val plan = session().confirmDiscard() as? SetupWizardExitPlan.Discard ?: return@withLock false
        // Un borrador no convertible nunca se publica en el estado: su id real
        // vive en currentDraftId. Sin id conocido todavía no se cargó nada y no
        // se borra nada (nunca se destruye datos que el usuario no ha visto).
        val draftId = currentDraftId?.takeIf(String::isNotBlank) ?: plan.draftId.takeIf(String::isNotBlank)
        if (draftId == null) {
            val nothingToDiscard = session().onDiscarded()
            _state.value = _state.value.copy(dialog = nothingToDiscard.dialog, exitCompleted = nothingToDiscard.exitCompleted)
            return@withLock true
        }
        previewJob?.cancel()
        candidateJob?.cancel()
        exerciseSearchJob?.cancel()
        ringsPreviewJob?.cancel()
        try {
            withContext(NonCancellable) { persistence.discard(draftId) }
        } catch (cancel: CancellationException) { throw cancel }
        catch (error: Exception) {
            // El fallo de descarte se reporta: nunca se navega mintiendo.
            _state.value = _state.value.copy(isSavingAndExiting = false, machineState = stateForCurrentStep(),
                errors = mapOf("draft" to "No pude descartar el borrador. Inténtalo de nuevo."), lastFailure = error.message)
            return@withLock false
        }
        savedStateHandle[DRAFT_ID_KEY] = null
        currentDraftId = null
        initialized = true
        val fresh = newDraft(_state.value.mode, "create", null, draftId)
        publishDraft(fresh, false, WizChatMachineState.AwaitingAnswer)
        val finished = session().onDiscarded()
        _state.value = _state.value.copy(dialog = finished.dialog, exitCompleted = finished.exitCompleted)
        true
    }

    private fun session(): SetupWizardSession = SetupWizardSession(
        draft = _state.value.draft,
        dialog = _state.value.dialog,
        isSavingAndExiting = _state.value.isSavingAndExiting,
        exitCompleted = _state.value.exitCompleted,
    )

    suspend fun commit(): String? = commandMutex.withLock {
        val current = _state.value
        if (current.machineState == WizChatMachineState.Committed) return@withLock current.receiptId
        _state.value = current.copy(isCommitting = true, machineState = WizChatMachineState.Committing, errors = emptyMap())
        try {
            // Puerta real: revisión de pasos + validación completa (sin
            // `wizChat.terminal` ni respuestas legacy aceptadas).
            val errors = reviewErrors()
            if (errors.isNotEmpty()) {
                _state.value = _state.value.copy(isCommitting = false, machineState = stateForCurrentStep(), errors = errors)
                return@withLock null
            }
            val draft = _state.value.draft
            val program = if (draft.includeTraining && draft.programRoute != SetupProgramRoute.LATER) _state.value.programPreview ?: error("La vista previa del programa no es ejecutable") else null
            val trackingOnly = isTrackingOnly(draft)
            // Se reutiliza la preparación revisada del preview (planId estable
            // derivado del commitId); nunca se fabrica un EER ni un objetivo.
            val preparation = if (draft.includeNutrition) prepareNutrition(draft) else null
            if (draft.includeNutrition && !trackingOnly && preparation?.plan == null) {
                error(preparation?.errors?.values?.firstOrNull() ?: "Completa el plan de nutrición")
            }
            val nutrition = if (trackingOnly) null else preparation?.plan
            // Un solo reloj para el check-in: el previewado y el persistido en
            // esta misma operación comparten exactamente la fecha.
            val ringsNow = ringsPreviewNow ?: System.currentTimeMillis()
            val rings = ringsMapping(draft, ringsNow)
            val base = environment.settings
            val typed = nutrition?.typedBodyGoal
            val bodyGoals = typed?.targetValueSi?.let { target -> listOf(BodyGoal("plan:${nutrition.id}:${typed.metric.name}", typed.metric.toBodyMetric(), target, typed.unitSi, typed.origin, nutrition.id, System.currentTimeMillis(), System.currentTimeMillis())) }.orEmpty()
            // Check-in real: evidencia completa, calibración parcial o solo
            // molestias declaradas. Nunca se fabrican sesiones sintéticas.
            val wellbeing = if (rings.savesRealCheckIn) {
                calculateRingsPreview(draft, rings, ringsNow).stagedWellbeing
            } else null
            val pendingNutritionDraft = if (SetupPendingNutrition.shouldPreserve(draft)) {
                val pending = SetupPendingNutrition.build(draft)
                SetupPendingNutrition.toCommitField(pending, json.encodeToString(pending))
            } else null
            // Snapshot del día REAL: el objetivo de HOY calculado por el reparto
            // de la ventana; si hoy no tiene objetivo no se graba ninguno.
            val today = LocalDate.now()
            val snapshot = if (!trackingOnly && draft.activateNutrition && nutrition != null) {
                preparation?.days?.firstOrNull { it.date == today }?.let { day ->
                    DailyGoalSnapshot(today.toString(), nutrition.id, day.calorieTargetKcal,
                        day.proteinG, day.carbsG, day.fatG, nutrition.direction,
                        nutrition.calculationOrigin, System.currentTimeMillis())
                }
            } else null
            // El adaptador real se resuelve solo aquí: las tests inyectan el
            // suyo y la construcción del VM nunca toca Room.
            val port = commits ?: realSetupWizardCommits(getApplication())
            val result = port.commit(SetupCommitRequest(
                commitId = draft.commitId,
                draftId = draft.draftId,
                settings = base,
                program = program,
                nutritionPlan = nutrition,
                activateProgram = draft.includeTraining && draft.programRoute != SetupProgramRoute.LATER && draft.activateProgram,
                activateNutrition = draft.includeNutrition && draft.activateNutrition && !trackingOnly,
                derivedBodyGoals = bodyGoals,
                initialWellbeing = wellbeing,
                settingsPatch = buildSettingsPatch(base, draft, program, nutrition, rings, trackingOnly),
                dailyGoalSnapshot = snapshot,
                pendingNutritionDraft = pendingNutritionDraft,
                nutritionTrackingOnly = trackingOnly,
                bodyObservations = SetupActivationPayload.bodyObservations(draft),
            ))
            _state.value = _state.value.copy(draft = draft.copy(
                wizChat = draft.wizChat.copy(terminal = true, currentQuestionId = WizChatQuestionId.REVIEW, stage = WizChatStage.REVIEW),
                stepProgress = draft.stepProgress.at(SetupStepId.REVIEW_ACTIVATE, draft.stepContext()),
            ), dirty = false, receiptId = result.commitId, isCommitting = false, machineState = WizChatMachineState.Committed, errors = emptyMap())
            savedStateHandle[DRAFT_ID_KEY] = null
            // Post-éxito: Body Progress relee su almacenamiento para mostrar
            // las observaciones recién escritas sin reiniciar la app.
            environment.refreshBodyProgress()
            result.commitId
        } catch (cancel: CancellationException) {
            // Un commit interrumpido no se maquilla: vuelve a la pantalla real
            // con el error inline para poder reintentar la operación exacta.
            _state.value = _state.value.copy(isCommitting = false, machineState = stateForCurrentStep(), errors = mapOf("commit" to "La configuración se interrumpió. Inténtalo de nuevo."))
            throw cancel
        }
        catch (error: Throwable) {
            _state.value = _state.value.copy(isCommitting = false, machineState = stateForCurrentStep(), errors = mapOf("commit" to (error.message ?: "No se pudo guardar la configuración")))
            null
        }
    }

    /**
     * Machine state that renders the REAL step screen for the current cursor.
     * Failures (commit, preview) keep the steps visible with an inline error
     * instead of hiding them behind a status dialog.
     */
    private fun stateForCurrentStep(): WizChatMachineState =
        if (_state.value.draft.stepProgress.currentStepId == SetupStepId.REVIEW_ACTIVATE) {
            WizChatMachineState.Reviewing
        } else {
            WizChatMachineState.AwaitingAnswer
        }

    private suspend fun persistAndPublish(draft: SetupWizardDraft) {
        val previous = _state.value.draft
        val previousKey = trainingKey(previous)
        // Cambios fisiológicos reales: marcan pendientes y previews obsoletos.
        // La navegación pura no cambia la huella y por eso no dispara nada.
        // Revisión monótona: models nunca toca draft.revision, así que la frontera
        // de persistencia siempre supera la fila guardada (guard de Room en 126).
        val next = draft.withNextDraftRevision(previous).withChangeImpacts(previous)
        _state.value = _state.value.copy(machineState = WizChatMachineState.PersistingAnswer, errors = emptyMap())
        if (persistDraft(next)) {
            publishDraft(next, true, WizChatMachineState.AwaitingAnswer)
            if (previousKey != trainingKey(next)) updateCandidates(next)
            preparePreview(next)
        } else {
            // Guardar falló: el borrador (respuestas incluidas) sigue visible en
            // memoria con un error inline y sin perder nada, para reintentar.
            _state.value = _state.value.copy(draft = next, dirty = true, machineState = WizChatMachineState.AwaitingAnswer,
                isSubmittingAnswer = false, isSavingAndExiting = false)
        }
    }
    /**
     * Serialized write: applies [change] to the current draft and persists with
     * a strictly monotonic revision. It NEVER moves the cursor: confirmation
     * and advance belong to [submitCurrentStep] alone.
     *
     * Declaración explícita: una escritura de usuario con `step != null`
     * declara el paso aunque el valor no cambie (p. ej. confirmar el peso por
     * defecto 70 kg con la regla); si ya estaba declarado y el valor no varía,
     * no se persiste nada (sin revisión inútil).
     */
    private fun mutateDraft(step: SetupStepId? = null, change: (SetupWizardDraft) -> SetupWizardDraft) = viewModelScope.launch { commandMutex.withLock {
        if (!initialized || _state.value.isCommitting || _state.value.isSubmittingAnswer || _state.value.isSavingAndExiting ||
            _state.value.machineState == WizChatMachineState.Committed ||
            _state.value.machineState == WizChatMachineState.UnsupportedDraft
        ) return@withLock
        val old = _state.value.draft
        val changed = change(old)
        val alreadyDeclared = step != null && step in old.declaredSteps
        val marked = if (step != null && (changed != old || !alreadyDeclared)) changed.touchStep(step) else changed
        if (marked != old) persistAndPublish(marked.copy(revision = old.revision + 1))
    } }
    private suspend fun persistDraft(draft: SetupWizardDraft): Boolean = try {
        persistence.save(draft.draftId, json.encodeToString(draft), draft.revision.toLong(), PersonalizedPlanCatalog.REVISION)
        savedStateHandle[DRAFT_ID_KEY] = draft.draftId
        true
    } catch (cancel: CancellationException) { throw cancel }
    catch (error: Exception) {
        // Fracaso de guardado: error inline (dismissible), nunca un modal; el
        // estado de máquina lo decide quien llama (AwaitingAnswer + inicializado).
        _state.value = _state.value.copy(errors = mapOf("save" to "No pude guardar esta respuesta. Tu información sigue aquí; inténtalo de nuevo."), lastFailure = error.message)
        false
    }
    private fun publishDraft(draft: SetupWizardDraft, dirty: Boolean, machine: WizChatMachineState) {
        // Éxito de escritura: se limpia el último fallo, salvo el diagnóstico
        // de RINGS, que solo lo retira su propio cálculo cuando sale bien (el
        // gate de RINGS debe poder seguir mostrando la causa real).
        val ringsDiagnosis = _state.value.lastFailure
            ?.takeIf { it.startsWith(RINGS_FAILURE_PREFIX) }
        _state.value = _state.value.copy(draft = draft, dirty = dirty, isLoading = false, machineState = machine,
            errors = emptyMap(), previewError = null, lastFailure = ringsDiagnosis, isSubmittingAnswer = false)
    }

    private val trainingPreviewKinds = setOf(
        SetupPreviewKind.EXERCISES, SetupPreviewKind.LOADS, SetupPreviewKind.WARMUPS,
        SetupPreviewKind.PLAN_CANDIDATES, SetupPreviewKind.SPLIT, SetupPreviewKind.RECIPE, SetupPreviewKind.MARKS,
    )
    private val nutritionPreviewKinds = setOf(
        SetupPreviewKind.EER, SetupPreviewKind.MACROS, SetupPreviewKind.EXPENDITURE,
        SetupPreviewKind.NUTRITION_REFERENCES, SetupPreviewKind.NUTRITION_DISTRIBUTION,
    )
    private val ringsPreviewKinds = setOf(SetupPreviewKind.RINGS_BATTERIES)

    /** Once a preview is recomputed its stale flag is dropped; results whose footprint no longer matches are discarded. */
    private fun clearStalePreviews(kinds: Set<SetupPreviewKind>) {
        val current = _state.value.draft.stepProgress.stalePreviews
        if (kinds.none { it in current }) return
        _state.value = _state.value.copy(draft = _state.value.draft.let { draft ->
            draft.copy(stepProgress = draft.stepProgress.previewsComputed(kinds))
        })
    }

    /**
     * Fingerprint of everything the training engines consume: options, split,
     * marks and VITALS. Any change makes the cached preview stale so the next
     * computation runs against the real inputs.
     */
    private fun trainingKey(draft: SetupWizardDraft): List<Any?> = listOf(
        draft.commitId, draft.includeTraining, draft.programRoute, draft.trainingPath, draft.goal, draft.focus,
        draft.experience, draft.daysPerWeek, draft.selectedWeekdays, draft.minutesPerSession, draft.equipment,
        draft.cardioType, draft.cardioMinutes,
        draft.ageYears, draft.heightCm, draft.weightKg, draft.profileGender,
        draft.trainingOptions,
        // Inventario declarado (P0): su cambio invalida candidatos y preview de
        // programa, aunque el resto de opciones no varíen.
        draft.trainingOptions.inventory,
        draft.trainingEnvironment, draft.knowsTrainingMarks,
        draft.volumeAnswers, draft.volumeRecommendations, draft.priorityMuscles, draft.lowerEmphasisMuscles,
        draft.selectedSplitId, draft.customSplitPattern, draft.customSplitName,
        draft.selectedCatalogId, draft.sessions, draft.powerliftingProfile, draft.catalogRevision,
    )

    /** Catalog is loaded lazily, only when a preview or candidate computation requires it. */
    private suspend fun ensureCatalogLoaded() {
        if (catalogLoaded) return
        catalogRepository.load()
        catalogLoaded = true
    }

    /**
     * Propiedad de generación/job del preview: cada lanzamiento (o liberación
     * de caché) incrementa [previewGeneration] y toma la PROPIEDAD de
     * `isPreviewLoading`/`preparingTrainingKey`.
     *
     * Reglas:
     *  - el dedup sólo vale con un job **activo** calculando la misma clave;
     *  - un cache-hit **libera** el cálculo intermedio (cancela y apaga el
     *    loading): es la carrera A→B→A que dejaba `isPreviewLoading=true` eterno;
     *  - sólo el job **dueño** publica o limpia — un job viejo (cancelado o
     *    tardío) no escribe nada —;
     *  - una cancelación nunca publica error.
     */
    private fun preparePreview(draft: SetupWizardDraft) {
        prepareRingsPreview(draft)
        val key = trainingKey(draft)
        val jobActive = previewJob?.isActive == true
        if (jobActive && preparingTrainingKey == key && _state.value.isPreviewLoading) {
            updateNutritionPreview(draft)
            return
        }
        if (lastSuccessfulTrainingKey == key && _state.value.programPreview != null && _state.value.previewError == null) {
            // Caché válida: se recupera el resultado Y se retira la rama
            // intermedia que aún estuviera calculando.
            releasePreviewGeneration(cancelInFlight = true)
            clearStalePreviews(trainingPreviewKinds)
            updateNutritionPreview(draft)
            return
        }
        val generation = previewGeneration + 1
        previewGeneration = generation
        previewJob?.cancel()
        preparingTrainingKey = key
        _state.value = _state.value.copy(isPreviewLoading = true)
        previewJob = viewModelScope.launch { runPreview(generation, key, draft) }
    }

    /**
     * Retira la propiedad del cálculo en vuelo: invalida la generación, cancela
     * si hace falta y apaga el loading. Sólo la invoca el NUEVO dueño
     * (cache-hit / nueva generación) o [initialize].
     */
    private fun releasePreviewGeneration(cancelInFlight: Boolean) {
        previewGeneration += 1
        if (cancelInFlight) previewJob?.cancel()
        previewJob = null
        preparingTrainingKey = null
        retirePreviewLoading()
    }

    private fun ownsPreview(generation: Long): Boolean = generation == previewGeneration

    /** Sólo el dueño retira su señal de carga, sin publicar ningún resultado. */
    private fun retirePreviewLoading() {
        preparingTrainingKey = null
        val current = _state.value
        if (current.isPreviewLoading || current.machineState == WizChatMachineState.PreparingPreview) {
            _state.value = current.copy(
                isPreviewLoading = false,
                machineState = if (current.machineState == WizChatMachineState.PreparingPreview) {
                    stateForCurrentStep()
                } else {
                    current.machineState
                },
            )
        }
    }

    /**
     * Materialización real, salvo el puerto inyectado por las tests. El catálogo
     * es un PREREQUISITO del motor real: con override las tests controlan el
     * materializado completo sin cargar assets.
     */
    private suspend fun materialize(draft: SetupWizardDraft): SetupPreview {
        val override = materializeOverride
        if (override != null) return override.materialize(draft)
        ensureCatalogLoaded()
        return materializeProgram(draft)
    }

    private suspend fun runPreview(generation: Long, key: List<Any?>, draft: SetupWizardDraft) {
        if (!initialized) {
            if (ownsPreview(generation)) {
                preparingTrainingKey = null
                _state.value = _state.value.copy(isPreviewLoading = false)
            }
            return
        }
        val withoutPreview = !draft.includeTraining || draft.programRoute == SetupProgramRoute.LATER ||
            previewInputsIncomplete(draft)
        if (withoutPreview) {
            if (ownsPreview(generation)) {
                lastSuccessfulTrainingKey = null
                preparingTrainingKey = null
                _state.value = _state.value.copy(
                    programPreview = null, previewReport = null,
                    fixedSessionEstimateMinutes = null, fixedTrainingDays = null,
                    isPreviewLoading = false,
                )
                updateNutritionPreview(draft)
            }
            return
        }
        if (ownsPreview(generation)) {
            _state.value = _state.value.copy(
                machineState = WizChatMachineState.PreparingPreview,
                isPreviewLoading = true, previewError = null,
            )
        }
        try {
            val result = withContext(Dispatchers.IO) { materialize(draft) }
            when {
                // Dueño + clave vigente: publica y se retira.
                ownsPreview(generation) && trainingKey(_state.value.draft) == key -> {
                    lastSuccessfulTrainingKey = key
                    preparingTrainingKey = null
                    clearStalePreviews(trainingPreviewKinds)
                    val isFixed = draft.selectedCatalogId?.let(PersonalizedPlanCatalog::find)?.source?.let { it != CatalogSource.NATIVE } == true
                    val minutes = if (isFixed) result.program?.let(::estimateFixedSessionMinutes) else null
                    _state.value = _state.value.copy(programPreview = result.program, previewReport = result.report, fixedSessionEstimateMinutes = minutes,
                        fixedTrainingDays = if (isFixed) result.program?.let(::fixedTrainingDays) else null,
                        isPreviewLoading = false, machineState = if (_state.value.draft.wizChat.currentQuestionId == WizChatQuestionId.REVIEW) WizChatMachineState.Reviewing else WizChatMachineState.AwaitingAnswer,
                        requiresActivationConfirmation = activationConfirmation(draft, result.program))
                    updateNutritionPreview(_state.value.draft)
                }
                // Dueño con clave ya vieja: sólo retira su carga; el resultado
                // se descarta (nunca se publica un preview que ya no corresponde).
                ownsPreview(generation) -> retirePreviewLoading()
            }
            // No dueño: manda el job actual; este job no escribe nada.
        } catch (cancel: CancellationException) {
            // Cancelación: sin error y sin tocar el estado del job dueño.
            throw cancel
        } catch (error: Throwable) {
            if (ownsPreview(generation)) {
                preparingTrainingKey = null
                if (trainingKey(_state.value.draft) == key) {
                    _state.value = _state.value.copy(isPreviewLoading = false, machineState = stateForCurrentStep(), previewError = error.message ?: "No se pudo preparar la vista previa", errors = _state.value.errors + ("preview" to (error.message ?: "No se pudo preparar la vista previa")), lastFailure = error.message)
                } else {
                    retirePreviewLoading()
                }
            }
        }
    }
    private fun firstWeekSessions(program: Program): List<Session> = program.macrocycles.firstOrNull()?.blocks?.firstOrNull()
        ?.mesocycles?.firstOrNull()?.weeks?.firstOrNull()?.sessions.orEmpty()

    private fun fixedTrainingDays(program: Program): Set<Int> = program.resolvedSchedulePlan().trainingDays
        .ifEmpty { firstWeekSessions(program).mapNotNull { it.dayOfWeek }.toSet() }

    private fun estimateFixedSessionMinutes(program: Program): Int? {
        val sessions = program.macrocycles.flatMap { it.blocks }.flatMap { it.mesocycles }
            .flatMap { it.weeks }.flatMap { it.sessions }.filter { it.exercises.isNotEmpty() }
        return sessions.maxOfOrNull { session ->
            (session.exercises.sumOf { exercise -> exercise.sets.size * (45 + (exercise.restTime ?: 90).coerceIn(30, 300)) } + 59) / 60
        }
    }

    private fun fixedRecipeDifference(draft: SetupWizardDraft, program: Program?, minutes: Int?): Boolean {
        if (program == null || draft.selectedCatalogId?.let(PersonalizedPlanCatalog::find)?.source == CatalogSource.NATIVE) return false
        val realDays = fixedTrainingDays(program)
        return realDays != draft.selectedWeekdays || minutes != null && minutes > (draft.minutesPerSession ?: 100)
    }
    private fun ringsKey(draft: SetupWizardDraft): List<Any?> = listOf(draft.ringsAnswers,
        draft.manualMuscleOverrides, draft.manualEnergyOverride, draft.manualStructureOverride)

    private fun prepareRingsPreview(draft: SetupWizardDraft) {
        // Un solo reloj para el mapeo y el cálculo: el check-in previewado y
        // el que se guardaría en el commit comparten fecha.
        val now = System.currentTimeMillis()
        val mapping = ringsMapping(draft, now)
        val key = ringsKey(draft)
        if (!mapping.savesRealCheckIn) {
            ringsPreviewJob?.cancel()
            // Sin check-in real no hay baterías NI cobertura: nunca se muestra
            // una cobertura calculada sobre datos que no existen.
            lastRingsPreviewKey = null
            ringsPreviewNow = null
            _state.value = _state.value.copy(ringsBatteriesPreview = null, ringsCoveragePreview = null,
                ringsPreviewLoading = false, ringsPreviewError = null,
                errors = _state.value.errors - "rings_preview",
                // Tampoco queda un diagnóstico de RINGS que ya no aplica.
                lastFailure = _state.value.lastFailure
                    ?.takeUnless { it.startsWith(RINGS_FAILURE_PREFIX) })
            // Baterías, cobertura y marcas obsoletas se limpian juntas.
            clearStalePreviews(ringsPreviewKinds)
            return
        }
        if (lastRingsPreviewKey == key && _state.value.ringsBatteriesPreview != null) return
        ringsPreviewJob?.cancel()
        _state.value = _state.value.copy(ringsPreviewLoading = true, ringsPreviewError = null,
            errors = _state.value.errors - "rings_preview")
        ringsPreviewJob = viewModelScope.launch {
            try {
                val result = calculateRingsPreview(draft, mapping, now)
                if (ringsKey(_state.value.draft) == key) {
                    lastRingsPreviewKey = key
                    // El reloj inyectado se conserva: el commit reutiliza esta
                    // MISMA fecha para el check-in que se guardará.
                    ringsPreviewNow = now
                    // Éxito: se retira SOLO el diagnóstico de RINGS; los fallos
                    // de guardado/carga que sigan abiertos no se enmascaran.
                    val stillFailed = _state.value.lastFailure
                        ?.takeUnless { it.startsWith(RINGS_FAILURE_PREFIX) }
                    // Baterías y cobertura se publican JUNTAS: la cobertura sale
                    // del mismo cálculo, nunca de una etiqueta global derivada
                    // de las baterías (un canal desconocido no es cobertura).
                    _state.value = _state.value.copy(
                        ringsBatteriesPreview = result.batteries,
                        ringsCoveragePreview = result.coverage,
                        ringsPreviewLoading = false,
                        lastFailure = stillFailed,
                    )
                    clearStalePreviews(ringsPreviewKinds)
                }
            } catch (cancel: CancellationException) { throw cancel }
            catch (error: Throwable) {
                // Diagnóstico honesto: la CAUSA REAL (clase + mensaje) queda en
                // `lastFailure` —nunca un genérico— y se registra en logcat con
                // la traza. Solo se manda la excepción: ni borrador, ni
                // respuestas, ni ajustes (sin datos personales). Sin cancelación
                // encima, y sin inventar ningún preview en su lugar.
                val cause = "${error::class.java.simpleName}: ${error.message ?: "sin detalle"}"
                Log.e(DIAG_TAG, "Rings preview falló → $cause", error)
                if (ringsKey(_state.value.draft) == key) _state.value = _state.value.copy(
                    ringsPreviewLoading = false,
                    ringsPreviewError = "No pude preparar tus RINGS. Reintenta desde esta pregunta.",
                    errors = _state.value.errors + ("rings_preview" to "No pude preparar tus RINGS. Reintenta desde esta pregunta."),
                    lastFailure = "$RINGS_FAILURE_PREFIX · $cause",
                )
            }
        }
    }

    private suspend fun calculateRingsPreview(
        draft: SetupWizardDraft,
        mapping: SetupRingsMapping,
        nowEpochMs: Long = System.currentTimeMillis(),
    ): SetupRingsPreview {
        val a = requireNotNull(draft.ringsAnswers)
        // Mismo alcance que usaba la evidencia (perMuscleScores), sin depender de ella.
        val allowed = when (a.muscleScope) {
            InitialRecoveryMuscleScope.FULL_BODY -> InitialRecoveryEvidenceFactory.INITIAL_RECOVERY_MUSCLE_PILLARS.toSet()
            InitialRecoveryMuscleScope.SELECTED -> a.recentMuscles
            InitialRecoveryMuscleScope.UNKNOWN -> mapping.evidence?.perMuscleScores?.keys ?: emptySet()
        }
        val selected = draft.manualMuscleOverrides.filterKeys { it in allowed }
        val discomforts = SetupRingsResponseMapping.discomfortField(mapping.discomfortResponse, mapping.discomfortIds)
        val settings = environment.settings
        val evidenceInput = mapping.evidence?.let { SetupRingsEvidenceInput.Available(it) }
            ?: if (settings.initialRecoveryEvidence != null) SetupRingsEvidenceInput.Preserved
            else SetupRingsEvidenceInput.Absent
        // El preview y el registro guardado comparten el MISMO `now` inyectado
        // (mapping.previewCheckIn() lleva la fecha del mapeo): nunca se mezclan
        // dos relojes para el mismo check-in.
        return SetupRingsPreviewCalculator(getApplication()).calculate(
            settings, evidenceInput, draft.commitId,
            selected, draft.manualEnergyOverride, draft.manualStructureOverride, discomforts,
            nowEpochMs, checkIn = mapping.previewCheckIn(),
        )
    }
    private fun updateNutritionPreview(draft: SetupWizardDraft) {
        val result = if (draft.includeNutrition) prepareNutrition(draft) else null
        _state.value = _state.value.copy(nutritionPreparation = result,
            nutritionPlanPreview = result?.plan,
            nutritionErrors = result?.errors.orEmpty(),
            nutritionPacePercentPerWeek = result?.recommendation?.suggestedRatePercentBodyWeightPerWeek?.times(100.0),
            requiresActivationConfirmation = activationConfirmation(draft, _state.value.programPreview))
        if (result != null) clearStalePreviews(nutritionPreviewKinds)
    }

    /**
     * Equipo Efectivo para los motores (M7: `TrainingOptions.effectiveEquipment`):
     * con inventario declarado manda él —en casa los 5 grupos finitos del wizard,
     * sin asumir `general_gym`— y sin inventario se conserva el perfil legacy.
     * Incluye siempre BODYWEIGHT.
     */
    private fun effectiveEquipmentIds(draft: SetupWizardDraft): Set<String> =
        draft.trainingOptions.effectiveEquipment(draft.equipment.map { it.catalogId }.toSet())

    private fun updateCandidates(draft: SetupWizardDraft) {
        candidateJob?.cancel()
        val equipmentIds = effectiveEquipmentIds(draft)
        val canPrepare = draft.includeTraining && draft.programRoute != SetupProgramRoute.LATER &&
            draft.trainingPath != SetupTrainingPath.FROM_SCRATCH && draft.daysPerWeek != null &&
            draft.minutesPerSession != null && draft.selectedWeekdays.size == draft.daysPerWeek && equipmentIds.isNotEmpty() &&
            (draft.goal != SetupGoal.MIXED || draft.cardioType != null && draft.cardioMinutes != null)
        if (!canPrepare) {
            // Entradas incompletas: se retira SOLO la marca de candidatos
            // (`errors["candidates"]` + su `previewError`); los errores de otras
            // operaciones nunca se tocan ni se filtran aquí.
            _state.value = withoutStaleCandidatesError(
                _state.value.copy(planCandidates = emptyList(), availablePlanCandidates = emptyList(), isCandidateLoading = false),
            )
            return
        }
        // Nueva carga: la marca vieja de candidatos sale YA, para que un error
        // anterior no tape la lista que está a punto de llegar.
        _state.value = withoutStaleCandidatesError(
            _state.value.copy(planCandidates = emptyList(), availablePlanCandidates = emptyList(), isCandidateLoading = true),
        )
        candidateJob = viewModelScope.launch {
            try {
                ensureCatalogLoaded()
                val (published, viable) = withContext(Dispatchers.IO) {
                    val publishedEntries = SetupTrainingPlanner.candidates(SetupTrainingPlannerInput(draft.trainingReference(), draft.daysPerWeek,
                        equipmentIds, draft.experience.toCatalogLevel(),
                        draft.focus.toTrainingFocus(), protocolOnly = draft.programRoute == SetupProgramRoute.PROTOCOL,
                        mixedTraining = draft.goal == SetupGoal.MIXED))
                    val viableEntries = mutableListOf<com.example.kpkn.data.programs.CatalogEntry>()
                    for (entry in publishedEntries) {
                        currentCoroutineContext().ensureActive()
                        val executable = try {
                            val program = materialize(draft.copy(selectedCatalogId = entry.id)).program
                            program != null && (entry.source == CatalogSource.NATIVE ||
                                (estimateFixedSessionMinutes(program) ?: 0) <= (draft.minutesPerSession ?: 100))
                        } catch (cancel: CancellationException) { throw cancel }
                        catch (_: Exception) { false }
                        if (executable) viableEntries += entry
                        if (viableEntries.size == 6) break
                    }
                    publishedEntries to viableEntries
                }
                if (trainingKey(_state.value.draft) == trainingKey(draft)) {
                    val options = viable.map { entry ->
                        SetupPlanCandidate(
                            id = entry.id,
                            title = entry.title,
                            subtitle = entry.technicalSubtitle,
                            description = entry.description,
                            source = entry.source.name,
                            reasons = buildList {
                                draft.daysPerWeek?.let { days ->
                                    if (entry.supportedFrequencies.contains(days)) add("Encaja con tus $days días por semana")
                                }
                                add("Se ejecuta con el equipo que has elegido")
                                if (entry.level == draft.experience.toCatalogLevel()) add("Su nivel coincide con tu experiencia")
                                if (draft.goal == SetupGoal.MIXED && entry.schedulesCardio) add("Programa el cardio que has pedido")
                            },
                            details = listOfNotNull(
                                entry.sourceAuthor?.let { "Método de $it" },
                                entry.sourceRevision?.let { "Revisión del método: $it" },
                                entry.disclaimer,
                            ).joinToString("\n").ifBlank { null },
                        )
                    }
                    val current = _state.value
                    if (options.isEmpty()) {
                        // Estado explícito «no compatible» con motivo REAL (UDF):
                        // una lista vacía nunca se publica en silencio. El motivo
                        // sale de los datos del propio borrador, sin fabricar nada.
                        val material = equipmentIds.sorted().joinToString(", ")
                            .ifBlank { "solo peso corporal" }
                        val frequency = draft.daysPerWeek?.let { "$it días por semana" } ?: "esta frecuencia"
                        val reason = if (published.isEmpty()) {
                            "No hay planes publicados compatibles con tu material ($material) y $frequency."
                        } else {
                            "${published.size} planes publicados; ninguno es ejecutable con tu material ($material) y $frequency."
                        }
                        _state.value = current.copy(
                            planCandidates = emptyList(), availablePlanCandidates = emptyList(),
                            isCandidateLoading = false,
                            // Se publica en `errors["candidates"]` (banner con
                            // reintento CANDIDATES) sin tocar `previewError`,
                            // que sigue siendo el canal del preview de programa
                            // (así un estado «no compatible» no tapa un preview
                            // de programa ya listo ni rompe sus gates).
                            errors = current.errors + ("candidates" to reason),
                        )
                    } else {
                        // Éxito: sólo se retira la marca propia de candidatos;
                        // `previewError` ajeno (p. ej. del programa) se conserva.
                        _state.value = current.copy(
                            planCandidates = options.take(3), availablePlanCandidates = options,
                            isCandidateLoading = false,
                            errors = current.errors - "candidates",
                        )
                    }
                }
            } catch (cancel: CancellationException) { throw cancel }
            catch (error: Exception) {
                if (trainingKey(_state.value.draft) == trainingKey(draft)) _state.value = _state.value.copy(
                    planCandidates = emptyList(), availablePlanCandidates = emptyList(),
                    isCandidateLoading = false, previewError = "No pude comprobar los planes. Prueba de nuevo.",
                    errors = _state.value.errors + ("candidates" to "No pude comprobar los planes. Prueba de nuevo."))
            }
        }
    }

    /**
     * Retira SOLO la marca de candidatos (`errors["candidates"]` y, si era suya,
     * su `previewError`): así un error viejo nunca se queda ocultando una lista
     * nueva y los fallos de otras operaciones siguen visibles.
     */
    private fun withoutStaleCandidatesError(state: SetupWizardState): SetupWizardState {
        val hadCandidatesError = "candidates" in state.errors
        return state.copy(
            errors = state.errors - "candidates",
            previewError = if (hadCandidatesError) null else state.previewError,
        )
    }
    private fun previewInputsIncomplete(draft: SetupWizardDraft): Boolean { if (!draft.includeTraining || draft.programRoute == SetupProgramRoute.LATER) return false; val days = draft.daysPerWeek ?: return true; if (draft.minutesPerSession == null || draft.selectedWeekdays.size != days || draft.goal == SetupGoal.MIXED && (draft.cardioType == null || draft.cardioMinutes == null)) return true; return if (draft.trainingPath == SetupTrainingPath.FROM_SCRATCH) { val selected = draft.sessions.filter { it.weekday in draft.selectedWeekdays }; selected.size != draft.selectedWeekdays.size || selected.any { it.exercises.isEmpty() } } else draft.selectedCatalogId == null }

    private suspend fun materializeProgram(draft: SetupWizardDraft): SetupPreview {
        if (!draft.includeTraining || draft.programRoute == SetupProgramRoute.LATER) return SetupPreview(null, null)
        if (draft.trainingPath == SetupTrainingPath.FROM_SCRATCH) {
            check(!previewInputsIncomplete(draft)) { "Completa todas las sesiones antes de crear el programa" }
            val id = draft.commitId
            val sessions = draft.sessions.filter { it.weekday in draft.selectedWeekdays }
                .sortedBy { it.weekday }.map { it.toSession(id) }
            val week = ProgramWeek("$id-week", "Semana de entrenamiento", sessions = sessions)
            val mesocycle = Mesocycle("$id-meso", "Mi entrenamiento", weeks = listOf(week))
            val block = Block("$id-block", "Bloque inicial", mesocycles = listOf(mesocycle))
            val macrocycle = Macrocycle("$id-macro", "Mi programa", blocks = listOf(block))
            val program = Program(id = id, name = "Mi plan de entrenamiento",
                startDay = draft.selectedWeekdays.minOrNull(), weekDays = draft.daysPerWeek,
                macrocycles = listOf(macrocycle), volumeRecommendations = draft.volumeRecommendations,
                athleteProfileScore = draft.athleteProfileScore,
                schedulePlan = ProgramSchedulePlan(
                    weekStartDay = draft.selectedWeekdays.minOrNull(),
                    trainingDays = draft.selectedWeekdays.toSet(),
                ))
            ProgramExecutionContract.requireExecutable(program)
            return SetupPreview(program, null)
        }
        val entry = draft.selectedCatalogId?.let(PersonalizedPlanCatalog::find) ?: error("Selecciona un plan")
        if (entry.source == CatalogSource.NATIVE) {
            val frequency = draft.daysPerWeek ?: error("Selecciona los días de entrenamiento")
            val result = OnboardingPlanGenerator(SimpleCyclePersonalizer(catalogRepository)).generate(
                draft.commitId,
                PersonalizerInput(
                    catalogEntryId = entry.id,
                    focus = draft.focus.toTrainingFocus(),
                    frequency = frequency,
                    weekdays = draft.selectedWeekdays.sorted(),
                    equipment = effectiveEquipmentIds(draft),
                    level = draft.experience.toCatalogLevel(),
                    availableMinutes = draft.minutesPerSession ?: error("Indica el tiempo disponible"),
                    cardio = if (draft.goal == SetupGoal.MIXED) CardioPreference(requireNotNull(draft.cardioType), requireNotNull(draft.cardioMinutes)) else null,
                    calibration = if (draft.volumeRecommendations.isNotEmpty()) Calibration.CALIBRATED else Calibration.CONSERVATIVE,
                    volumeRecommendations = draft.volumeRecommendations,
                    priorityMuscles = draft.priorityMuscles,
                    lowerEmphasisMuscles = draft.lowerEmphasisMuscles,
                    splitId = draft.selectedSplitId,
                    splitPattern = draft.customSplitPattern,
                    splitName = draft.customSplitName,
                ),
                // Cadena real de entrenamiento: el motor recibe las opciones del
                // usuario (inventario, prioridades, calentamientos, autorreg.)
                options = draft.trainingOptions,
            )
            return SetupPreview(result.program?.copy(id = draft.commitId) ?: error(result.report.limitations.joinToString(" ")), result.report)
        }
        val catalog = (catalogRepository.state.value as? ExerciseCatalogStateV2.Ready)?.catalog
            ?: error("El catálogo de ejercicios todavía no está disponible")
        // Mismo equipo efectivo que candidatos y preview (helper de M7): nunca
        // se reinyecta `general_gym` ni se retira el modelo finito de inventario.
        val effectiveEquipment = effectiveEquipmentIds(draft)
        // Opciones del usuario ANTES de materializar: la autoregulación entra en
        // la base (PlanMaterializer la conserva sin mezclar) y los calentamientos
        // viajan como `defaultOptions` hasta el materializador, que da
        // precedencia a la elección/autor ya guardada sobre `options`.
        val options = draft.trainingOptions
        val base = options.applyTo(
            Program(id = draft.commitId, name = "Plan de ${draft.name.ifBlank { "entrenamiento" }}", startDay = draft.selectedWeekdays.minOrNull(), powerliftingProfile = draft.powerliftingProfile),
        )
        val program = when (entry.source) {
            CatalogSource.PROTOCOL -> {
                val protocol = PROTOCOL_LIBRARY.first { it.id == entry.sourceId }
                ProgramProtocolEngine.applyProtocol(
                    program = base.copy(selectedSplitId = protocol.defaultSplit),
                    protocol = protocol,
                    metadata = CatalogCompositionMetadataProvider.fromCatalog(catalog),
                    exerciseList = catalog.toLegacyConfigurationLookup().values.toList(),
                    defaultOptions = options,
                )
            }
            CatalogSource.TEMPLATE -> ProgramTemplateEngine.applyTemplate(
                base,
                requireNotNull(entry.template),
                forceReplace = true,
                defaultOptions = options,
            ).program
            CatalogSource.NATIVE -> error("Ruta nativa no válida")
        }.copy(id = draft.commitId)
        // Guardia de material real (helper de M7) DESPUÉS de aplicar la receta
        // fija y ANTES del preview: si la receta exige material no declarado, el
        // error es honesto en lugar de presentarla como compatible. Así los
        // candidatos viables se podan solos en `updateCandidates` (su `catch`
        // marca la entrada como no viable) sin afirmar compatibilidad falsa.
        val missingMaterial = missingFixedRecipeEquipment(program, effectiveEquipment, catalog)
        if (missingMaterial.isNotEmpty()) {
            error(
                "Esta receta necesita material que no has declarado: " +
                    missingMaterial.sorted().joinToString(", "),
            )
        }
        val sessionDays = program.macrocycles.flatMap { it.blocks }.flatMap { it.mesocycles }
            .flatMap { it.weeks }.flatMap { it.sessions }.mapNotNull { it.dayOfWeek }.toSet()
        val frequency = sessionDays.size
        if (draft.daysPerWeek != null && frequency != draft.daysPerWeek) error("La receta fija produce $frequency días, no ${draft.daysPerWeek}")
        val scheduled = program.copy(
            schedulePlan = ProgramSchedulePlan(
                weekStartDay = program.startDay,
                trainingDays = sessionDays,
            ),
        )
        // Sin copia posterior de `planWarmupConfig`: una vez materializada la
        // receta, reasignar la config no vuelve a aplicar los warmups (bug
        // confirmado). La resolución correcta ocurre EN la primera
        // materialización, dentro del materializador (elección/autor guardada >
        // `defaultOptions`), que es el que asigna los pasos por ejercicio.
        return SetupPreview(scheduled, null)
    }

    /**
     * Preparación nutricional REAL del alta: motor `SetupNutritionPreparation`
     * con el borrador enriquecido (vitales del wizard y grasa corporal ACTUAL
     * medida o estimada, nunca usada como meta), el programa previewado como
     * calendario y los ajustes del entorno con las vitales del borrador. El
     * planId se deriva del commitId, así que cada preview reutiliza el mismo
     * id estable en lugar de generar uno nuevo.
     */
    private fun prepareNutrition(draft: SetupWizardDraft): SetupNutritionPreparationResult? {
        if (!draft.includeNutrition) return null
        val wizard = draft.nutritionDraft ?: return null
        // Solo una fuente EXPLÍCITA (medida o estimación visual) enriquece el
        // borrador; «No lo sé» o fuente sin declarar no aportan grasa alguna.
        // Mismo rango que la validación del commit (0–100 %) para que la
        // nutrición y las observaciones corporales no diverjan.
        val currentBodyFat = draft.bodyFatPercent
            ?.takeIf { it.isFinite() && it in 0.0..100.0 }
            ?.takeIf {
                draft.bodyFatSource == SetupBodyFatSource.MEASURED ||
                    draft.bodyFatSource == SetupBodyFatSource.VISUAL_ESTIMATE
            }
        val enriched = wizard.copy(
            planId = draft.nutritionPlanId ?: draft.commitId,
            ageText = wizard.ageText.ifBlank { draft.ageYears?.toString().orEmpty() },
            heightText = wizard.heightText.ifBlank { draft.heightCm?.toString().orEmpty() },
            weightText = wizard.weightText.ifBlank { draft.weightKg?.toString().orEmpty() },
            bodyFatText = wizard.bodyFatText.ifBlank {
                currentBodyFat?.let { String.format(java.util.Locale.ROOT, "%.1f", it) }.orEmpty()
            },
        )
        return SetupNutritionPreparation.prepare(SetupNutritionPreparationInput(
            draft = enriched,
            program = _state.value.programPreview,
            settings = settingsWithVitals(draft),
            today = LocalDate.now(),
        ))
    }

    /** Entorno con las vitales del borrador: las ecuaciones leen el alta, no el viejo Settings. */
    private fun settingsWithVitals(draft: SetupWizardDraft): Settings {
        val base = environment.settings
        val vitals = base.userVitals.copy(
            age = draft.ageYears ?: base.userVitals.age,
            height = draft.heightCm ?: base.userVitals.height,
            weight = draft.weightKg ?: base.userVitals.weight,
            gender = draft.profileGender ?: base.userVitals.gender,
            bodyFatPercentage = draft.bodyFatPercent ?: base.userVitals.bodyFatPercentage,
        )
        return base.copy(userVitals = vitals, age = draft.ageYears ?: base.age)
    }

    /** «Solo registro»: el modo explícito del borrador, nunca inferido. */
    private fun isTrackingOnly(draft: SetupWizardDraft): Boolean =
        draft.includeNutrition &&
            draft.nutritionDraft?.configurationMode == NutritionConfigurationMode.TRACKING_ONLY

    /**
     * Puerta de activación REAL: revisión de pasos + validación completa. No
     * depende del espejo conversacional (`wizChat.terminal` ni
     * `acceptedAnswers`): el estado de la ruta sale de `stepProgress` y de
     * [SetupWizardValidation.validateAll].
     */
    private fun reviewErrors(): Map<String, String> = buildMap {
        val s = _state.value; val d = s.draft
        val route = SetupStepGraph.stepIds(d.stepContext())
        if (SetupStepId.REVIEW_ACTIVATE !in route || d.stepProgress.currentStepId != SetupStepId.REVIEW_ACTIVATE) {
            put("flow", "Completa la revisión antes de activar")
        }
        // Revisión REAL del camino: cada paso se confirma (o llega confirmado
        // desde un borrador migrado). «Obligatorio» significa revisado, no
        // fabricar sensaciones ni historia: omitir con la opción explícita
        // también cuenta como revisado.
        val unreviewed = route.filterNot { step ->
            step == SetupStepId.REVIEW_ACTIVATE ||
                step in d.stepProgress.answers ||
                (SetupStepGraph.questionForStep(step)?.let { question ->
                    d.wizChat.acceptedAnswers.any { it.questionId == question }
                } == true)
        }
        unreviewed.firstOrNull()?.let { step ->
            val title = SetupStepDefinitions.of(step)?.title ?: "la revisión"
            put("review", "Falta revisar «$title» antes de activar")
        }
        if (SetupDraftCompatibility.pendingMandatoryVitals(d).isNotEmpty()) put("profile", "Completa tu edad, estatura y peso para continuar")
        val blocking = SetupWizardValidation.validateAll(d).firstOrNull { it.isBlocking }
        if (blocking != null) put(blocking.key, blocking.message ?: "Revisa tus respuestas antes de activar")
        if (d.includeTraining && d.programRoute != SetupProgramRoute.LATER && (s.programPreview == null || s.previewError != null || s.isPreviewLoading || lastSuccessfulTrainingKey != trainingKey(d))) put("program", "Prepara una vista previa ejecutable")
        if (s.fixedSessionEstimateMinutes != null && s.fixedSessionEstimateMinutes > (d.minutesPerSession ?: 100)) put("time", "Esta receta supera los ${d.minutesPerSession ?: 100} minutos por sesión; elige otra o ajusta el tiempo")
        if (fixedRecipeDifference(d, s.programPreview, s.fixedSessionEstimateMinutes) && !d.acceptFixedRecipeDifference) put("schedule", "Confirma la rotación y la duración reales de la receta")
        // Solo registro = sin plan y sin metas; no se exige una preparación que
        // el modo rechaza explícitamente.
        if (d.includeNutrition && !isTrackingOnly(d) && (s.nutritionPlanPreview == null || s.nutritionErrors.isNotEmpty())) {
            put("nutrition", s.nutritionErrors.values.firstOrNull() ?: "Completa la nutrición")
        }
        if (ringsMapping(d).savesRealCheckIn && (s.ringsBatteriesPreview == null || s.ringsPreviewLoading || s.ringsPreviewError != null || lastRingsPreviewKey != ringsKey(d))) {
            // Causa real de RINGS primero (clase+mensaje del último fallo de
            // este cálculo): nunca un genérico cuando existe diagnóstico, ni el
            // fallo de otra operación colado bajo esta clave.
            val ringsCause = s.lastFailure?.takeIf { it.startsWith(RINGS_FAILURE_PREFIX) }
            put("rings", ringsCause ?: s.ringsPreviewError ?: "Espera a que la vista previa de RINGS esté lista")
        }
        if (activationConfirmation(d, s.programPreview) && !d.confirmActivation) put("activation", "Confirma la activación del plan")
    }
    private fun activationConfirmation(draft: SetupWizardDraft, preview: Program?): Boolean = (draft.activateProgram && preview != null && environment.activeProgramId()?.let { it != preview.id } == true) || (draft.includeNutrition && draft.activateNutrition && environment.activeNutritionPlanId()?.let { it != (draft.nutritionPlanId ?: draft.commitId) } == true)

    private fun buildSettingsPatch(
        base: Settings,
        draft: SetupWizardDraft,
        program: Program?,
        nutrition: NutritionPlan?,
        rings: SetupRingsMapping,
        trackingOnly: Boolean,
    ): SetupSettingsPatch {
        // Evidencia REAL de que el usuario escribió el dato: el paso declarado.
        // Los borradores migrados conservan el espejo legacy como alternativa;
        // un valor nunca se declara solo por existir en el borrador.
        val declared = draft.declaredSteps
        val legacyProvided = draft.wizChat.acceptedAnswers
            .filter { it.source != WizChatAnswerSource.OMITTED }
            .map { it.questionId }.toSet()
        val legacyAccepted = draft.wizChat.acceptedAnswers.map { it.questionId }.toSet()
        fun provided(step: SetupStepId, question: WizChatQuestionId): Boolean =
            step in declared || question in legacyProvided
        val age = draft.ageYears ?: parseLocalizedNumber(draft.nutritionDraft?.ageText.orEmpty())?.toInt()
        val declaredName = draft.name.takeIf {
            it.isNotBlank() && provided(SetupStepId.NAME, WizChatQuestionId.P_NAME)
        }
        val goals = nutrition?.takeIf { draft.activateNutrition }
        // SET/CLEAR/UNCHANGED decididos por el mapper: una calibración parcial deja
        // initialRecoveryEvidence sin tocar (Unchanged) y PRESERVE no rejuvenece nada.
        val evidence = rings.toEvidencePatchField()
        val trackingChoice: NutritionTrackingChoice? = when {
            goals != null -> NutritionTrackingChoice.ENABLED
            trackingOnly -> null
            !draft.includeNutrition && !SetupPendingNutrition.shouldPreserve(draft) &&
                (provided(SetupStepId.NUTRITION_START, WizChatQuestionId.N_START) ||
                    WizChatQuestionId.N_START in legacyAccepted) &&
                environment.activeNutritionPlanId() == null -> NutritionTrackingChoice.SKIPPED
            else -> null
        }
        return SetupSettingsPatch(
            username = setOrKeep(declaredName),
            age = setIf(age, provided(SetupStepId.AGE, WizChatQuestionId.P_AGE) && age != null),
            vitalsPatch = SetupUserVitalsPatch(
                age = setIf(age, provided(SetupStepId.AGE, WizChatQuestionId.P_AGE) && age != null),
                height = setIf(draft.heightCm, provided(SetupStepId.HEIGHT, WizChatQuestionId.P_HEIGHT) && draft.heightCm != null),
                weight = setIf(draft.weightKg, provided(SetupStepId.WEIGHT, WizChatQuestionId.P_WEIGHT) && draft.weightKg != null),
                gender = setIf(draft.profileGender, provided(SetupStepId.GENDER, WizChatQuestionId.P_GENDER) && draft.profileGender != null),
            ),
            weightUnit = setIf(
                if (draft.weightUnit == "lb") WeightUnit.LBS else WeightUnit.KG,
                draft.weightUnitChanged,
            ),
            dailyCalorieGoal = setIf(goals?.calorieTarget, goals != null),
            dailyProteinGoal = setIf(goals?.proteinGoal, goals != null),
            dailyCarbGoal = setIf(goals?.carbGoal, goals != null),
            dailyFatGoal = setIf(goals?.fatGoal, goals != null),
            onboardingCompleted = setIf(
                true,
                draft.draftScope == "full" &&
                    _state.value.mode in setOf(SetupWizardMode.FULL, SetupWizardMode.RESUME),
            ),
            onboardingNameDone = setIf(true, declaredName != null),
            onboardingProgramDone = setIf(
                true,
                _state.value.mode in setOf(SetupWizardMode.FULL, SetupWizardMode.RESUME, SetupWizardMode.TRAINING_ONLY) &&
                    (!draft.includeTraining || draft.programRoute == SetupProgramRoute.LATER || program != null),
            ),
            onboardingNutritionDone = setIf(
                true,
                _state.value.mode in setOf(SetupWizardMode.FULL, SetupWizardMode.RESUME, SetupWizardMode.NUTRITION_ONLY) &&
                    (!draft.includeNutrition || nutrition != null || trackingOnly),
            ),
            nutritionTrackingChoice = setOrKeep(trackingChoice),
            initialRecoveryEvidence = evidence,
            volumeCalibrationProfile = setIf(draft.volumeCalibrationProfile, draft.volumeCalibrationProfile != null),
            // Inventario declarado en el wizard (`trainingOptions.inventory`);
            // sin datos se conserva el actual, nunca se limpia por accidente.
            equipmentInventory = setIf(draft.trainingOptions.inventory, draft.trainingOptions.inventory != null),
            nutritionTrackingOnly = setIf(true, trackingOnly),
            // Un valor sugerido desde Settings no cuenta como respuesta. Un
            // none explícito sí persiste como EquipmentAvailability(emptySet()).
            equipmentAvailability = if (draft.isStepDeclared(SetupStepId.HOME_EQUIPMENT)) {
                setOrKeep(draft.trainingOptions.availability)
            } else {
                SetupPatchField.Unchanged
            },
        )
    }

    /**
     * `Set` cuando hay valor; `Unchanged` conserva lo actual. El tipo de
     * retorno es explícito a propósito: construir `SetupSettingsPatch` con
     * `if (…) Set(…) else Unchanged` degrada la inferencia en cascada.
     */
    private fun <T> setOrKeep(value: T?): SetupPatchField<T> =
        if (value == null) SetupPatchField.Unchanged else SetupPatchField.Set(value)

    /**
     * `Set` solo cuando [present]; si no, `Unchanged`. Pensado para campos
     * cuyo tipo ya es anulable (`SetupPatchField<Int?>`,
     * `SetupPatchField<EquipmentInventory?>`): un dato sin declarar nunca se
     * escribe como `Set(null)`.
     */
    private fun <T> setIf(value: T, present: Boolean): SetupPatchField<T> =
        if (present) SetupPatchField.Set(value) else SetupPatchField.Unchanged

    private fun ringsMapping(draft: SetupWizardDraft, nowEpochMs: Long = System.currentTimeMillis()): SetupRingsMapping {
        val a = draft.ringsAnswers ?: return SetupRingsMapping(RingsCompletion.UNKNOWN)
        return SetupRingsMapper.map(SetupRingsInput(
            startAction = a.startAction,
            recentTraining = a.recentTraining,
            recentTrainingUnknown = a.recentTrainingState == SetupRecentTrainingState.UNKNOWN,
            sessions = a.sessionsLastSevenDays,
            recencyDays = a.lastSessionRecencyDays ?: a.recencyDays,
            activityType = a.activityType,
            intensity = a.intensityLevel,
            muscleFeeling = a.muscleFeeling,
            energy = a.energy,
            structureFeeling = a.structureFeeling,
            axialState = a.axialExposure.state,
            axialSessions = a.axialExposure.sessions,
            axialIntensity = a.intensityLevel,
            muscleScope = a.muscleScope,
            selectedMuscles = a.recentMuscles.toList(),
            discomfortIds = a.discomfortIds,
            capturedAtMs = a.capturedAtMs,
            // NONE (lista vacía explícita) es distinto de no informado/omitido.
            discomfortResponse = when (a.discomfortState) {
                SetupDiscomfortState.NONE -> RingsDiscomfortResponse.NONE
                SetupDiscomfortState.DECLARED -> RingsDiscomfortResponse.DECLARED
                SetupDiscomfortState.OMITTED -> RingsDiscomfortResponse.OMITTED
                SetupDiscomfortState.NOT_ANSWERED -> RingsDiscomfortResponse.NOT_ANSWERED
            },
        ), nowEpochMs)
    }
    private fun buildVolumeProfile(draft: SetupWizardDraft): VolumeCalibrationProfile? {
        val a = draft.volumeAnswers; val style = a.style ?: return null; val t = a.technique ?: return null; val c = a.consistency ?: return null; val s = a.strength ?: return null; val m = a.mobility ?: return null; val output = VolumeCalibrationEngine.calculate(style, t, c, s, m)
        return VolumeCalibrationProfile(style, output.score, VolumeCalibrationResponses(t, c, s, m, a.responseState.takeIf { it != CalibrationResponseState.UNKNOWN } ?: CalibrationResponseState.DECLARED), output.recommendations, System.currentTimeMillis(), VolumeCalibrationEngine.REVISION)
    }

    private fun newDraft(mode: SetupWizardMode, nutritionMode: String, nutritionPlanId: String?, id: String): SetupWizardDraft {
        val settings = environment.settings
        val training = mode != SetupWizardMode.NUTRITION_ONLY && mode != SetupWizardMode.RINGS_ONLY
        val nutrition = mode != SetupWizardMode.TRAINING_ONLY && mode != SetupWizardMode.RINGS_ONLY
        val first = WizChatGraph.firstFor(WizChatGraphContext(training, nutrition))
        val progress = WizChatProgress(scriptVersion = WizChatCopyCatalog.SCRIPT_VERSION,
            draftScope = scopeFor(mode).name.lowercase(), currentQuestionId = first, stage = WizChatGraph.stageFor(first))
        val unit = if (settings.weightUnit == WeightUnit.LBS) "lb" else "kg"
        return SetupWizardDraft(draftId = id, commitId = UUID.randomUUID().toString(), draftScope = scopeFor(mode).name.lowercase(), name = settings.username.takeIf { it != "Usuario" }.orEmpty(), moduleChoice = if (nutrition) SetupModuleChoice.TRAINING_AND_NUTRITION else SetupModuleChoice.TRAINING, ageYears = settings.userVitals.age ?: settings.age, heightCm = settings.userVitals.height, weightKg = settings.userVitals.weight, importedWeightKg = settings.userVitals.weight, weightUnit = unit, includeTraining = training, includeNutrition = nutrition, programRoute = if (training) SetupProgramRoute.CUSTOMIZABLE else SetupProgramRoute.LATER, trainingPath = if (training) SetupTrainingPath.PERSONALIZE else null, nutritionMode = nutritionMode, nutritionPlanId = nutritionPlanId, nutritionDraft = if (nutrition) NutritionWizardDraft(mode = nutritionMode, planId = nutritionPlanId, weightUnit = unit) else null, catalogRevision = PersonalizedPlanCatalog.REVISION, wizChat = progress,
            trainingOptions = SetupTrainingOptions(availability = settings.equipmentAvailability))
            .let { draft -> draft.copy(stepProgress = SetupStepProgress.initial(draft.stepContext())) }
    }
    private fun normalizeProgress(progress: WizChatProgress, scope: String, mode: SetupWizardMode): WizChatProgress {
        val missingGender = SetupDraftResolver.scopeOf(scope) == SetupDraftScope.FULL &&
            progress.schemaVersion == 1 && progress.acceptedAnswers.none { it.questionId == WizChatQuestionId.P_GENDER } &&
            progress.currentQuestionId != WizChatQuestionId.P_NAME
        val next = if (missingGender) WizChatQuestionId.P_GENDER else progress.currentQuestionId
        val safeQuestion = next.takeIf { WizChatGraph.question(it) != null } ?: WizChatQuestionId.P_NAME
        return progress.copy(schemaVersion = 2, scriptVersion = WizChatCopyCatalog.SCRIPT_VERSION,
            draftScope = scope, currentQuestionId = safeQuestion, stage = WizChatGraph.stageFor(safeQuestion))
    }
    private fun scopeFor(mode: SetupWizardMode): SetupDraftScope = when (mode) { SetupWizardMode.TRAINING_ONLY -> SetupDraftScope.TRAINING_ONLY; SetupWizardMode.NUTRITION_ONLY -> SetupDraftScope.NUTRITION_ONLY; SetupWizardMode.RINGS_ONLY -> SetupDraftScope.RINGS_ONLY; else -> SetupDraftScope.FULL }
    private fun SetupFocus.toTrainingFocus() = TrainingFocus.valueOf(name)
    private fun SetupExperience?.toCatalogLevel() = when (this) { SetupExperience.ADVANCED -> CatalogLevel.ADVANCED; SetupExperience.INTERMEDIATE -> CatalogLevel.INTERMEDIATE; else -> CatalogLevel.BEGINNER }
    private val SetupEquipment.catalogId: String get() = when (this) { SetupEquipment.NONE, SetupEquipment.BODYWEIGHT -> "bodyweight"; SetupEquipment.BANDS -> "band"; SetupEquipment.DUMBBELLS -> "dumbbells"; SetupEquipment.MACHINE -> "machine"; SetupEquipment.CABLE -> "cable"; SetupEquipment.BARBELL -> "barbell"; SetupEquipment.PULL_UP -> "pull_up_bar"; SetupEquipment.GYM -> "general_gym"; SetupEquipment.SUPPORT -> "support"; SetupEquipment.BALL -> "ball"; SetupEquipment.SMITH -> "smith_machine" }
    private fun com.example.kpkn.data.models.GoalMetric.toBodyMetric() = when (this) { GoalMetric.WEIGHT -> BodyMetric.WEIGHT; GoalMetric.BODY_FAT -> BodyMetric.BODY_FAT_PERCENT; GoalMetric.MUSCLE_MASS -> BodyMetric.MUSCLE_MASS_PERCENT }

    companion object {
        private const val DRAFT_ID_KEY = "setup_wizard_draft_id"
        /** Etiqueta de logcat de diagnóstico: solo clase+mensaje, nunca datos del usuario. */
        private const val DIAG_TAG = "SetupWizard"
        /** Marca en `lastFailure` del fallo de RINGS; permite limpiarlo sin pisar otros fallos. */
        private const val RINGS_FAILURE_PREFIX = "Rings preview"
    }
}

private fun List<SetupSessionDraft>.ensureSession(weekday: Int): List<SetupSessionDraft> = if (any { it.weekday == weekday }) this else this + SetupSessionDraft(weekday, "Sesión del día $weekday")
