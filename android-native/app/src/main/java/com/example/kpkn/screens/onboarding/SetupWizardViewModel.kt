package com.example.kpkn.screens.onboarding

import android.app.Application
import android.content.Context
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
import com.example.kpkn.data.repository.NutritionRepository
import com.example.kpkn.data.repository.ProgramRepository
import com.example.kpkn.domain.exercises.catalogv2.ExerciseCatalogStateV2
import com.example.kpkn.domain.nutrition.EerActivity
import com.example.kpkn.domain.nutrition.EerSex
import com.example.kpkn.domain.nutrition.NutritionPlanPreparation
import com.example.kpkn.domain.nutrition.NutritionPlanPreparationInput
import com.example.kpkn.domain.nutrition.kilogramsFromInput
import com.example.kpkn.domain.nutrition.parseLocalizedNumber
import com.example.kpkn.domain.nutrition.paceRateFor
import com.example.kpkn.domain.onboarding.*
import com.example.kpkn.domain.training.*
import com.example.kpkn.screens.nutrition.NutritionWizardDraft
import java.time.LocalDate
import java.util.UUID
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.first
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
class SetupWizardViewModel(
    application: Application,
    private val savedStateHandle: SavedStateHandle,
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
    private var ringsPreviewJob: kotlinx.coroutines.Job? = null
    private var lastRingsPreviewKey: List<Any?>? = null
    private var preparingTrainingKey: List<Any?>? = null
    private var lastSuccessfulTrainingKey: List<Any?>? = null

    private val _state = MutableStateFlow(SetupWizardState(SetupWizardDraft(commitId = UUID.randomUUID().toString()), isLoading = true))
    val state: StateFlow<SetupWizardState> = _state.asStateFlow()

    fun initialize(mode: SetupWizardMode, nutritionMode: String = "create", nutritionPlanId: String? = null, draftId: String? = null) {
        if (initialized && _state.value.mode == mode && (draftId == null || draftId == currentDraftId)) return
        initializeJob?.cancel()
        initialized = false
        previewJob?.cancel()
        candidateJob?.cancel()
        exerciseSearchJob?.cancel()
        ringsPreviewJob?.cancel()
        lastSuccessfulTrainingKey = null
        lastRingsPreviewKey = null
        _state.value = _state.value.copy(mode = mode, isLoading = true, machineState = WizChatMachineState.Loading,
            programPreview = null, planCandidates = emptyList(), availablePlanCandidates = emptyList(),
            nutritionPlanPreview = null, ringsBatteriesPreview = null)
        initializeJob = viewModelScope.launch {
            try {
                ProgramRepository.getInstance().isReady.first { it }
                if (!catalogLoaded) { catalogRepository.load(); catalogLoaded = true }
                val factory = persistenceFactory(getApplication<Application>())
                val storedCandidate = savedStateHandle.get<String>(DRAFT_ID_KEY)?.takeIf(String::isNotBlank)
                val storedId = storedCandidate?.takeIf { mode == SetupWizardMode.RESUME || it.startsWith(SetupDraftResolver.canonicalDraftId(scopeFor(mode))) }
                val resumeId = if (mode == SetupWizardMode.RESUME) SetupDraftResolver(factory.database).listRecoverable().firstOrNull()?.draftId else null
                val id: String = draftId ?: storedId ?: resumeId ?: SetupDraftResolver.canonicalDraftId(scopeFor(mode))
                val persisted = factory.drafts.load(id)
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
                val catalogueChanged = persisted != null && persisted.catalogRevision != PersonalizedPlanCatalog.REVISION && normalized.selectedCatalogId != null
                val draft = if (catalogueChanged) normalized.copy(
                    selectedCatalogId = null, catalogRevision = PersonalizedPlanCatalog.REVISION,
                    wizChat = normalized.wizChat.copy(currentQuestionId = if (normalized.wizChat.currentQuestionId == WizChatQuestionId.P_GENDER) WizChatQuestionId.P_GENDER else WizChatQuestionId.T_PLAN,
                        stage = if (normalized.wizChat.currentQuestionId == WizChatQuestionId.P_GENDER) WizChatStage.PROFILE else WizChatStage.TRAINING,
                        terminal = false, revision = normalized.wizChat.revision + 1,
                        acceptedAnswers = normalized.wizChat.acceptedAnswers.filterNot { it.questionId in setOf(WizChatQuestionId.T_PLAN, WizChatQuestionId.T_REVIEW) }),
                ) else normalized
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
                _state.value = _state.value.copy(isLoading = false, machineState = WizChatMachineState.RecoverableError, errors = mapOf("initialize" to (error.message ?: "No se pudo cargar el asistente")))
            }
        }
    }

    fun answerText(text: String, expectedRevision: Int? = null) = enqueueAnswer(WizChatAnswerRecord(WizChatQuestionId.P_NAME, WizChatAnswerKind.TEXT, textValue = text, expectedRevision = expectedRevision))
    fun answerNumber(id: WizChatQuestionId, value: Double, expectedRevision: Int? = null) = enqueueAnswer(WizChatAnswerRecord(id, WizChatAnswerKind.NUMBER, numberValue = value, expectedRevision = expectedRevision))
    fun answerWeight(value: Double, unit: String, expectedRevision: Int? = null) = answerNumber(WizChatQuestionId.P_WEIGHT,
        WizChatWeightScale.toKg(value, unit), expectedRevision)
    fun acceptImportedWeight(valueKg: Double, expectedRevision: Int) = enqueueAnswer(
        WizChatAnswerRecord(WizChatQuestionId.P_WEIGHT, WizChatAnswerKind.NUMBER, numberValue = valueKg,
            source = WizChatAnswerSource.IMPORTED, expectedRevision = expectedRevision),
    )
    fun answerChoice(id: WizChatQuestionId, value: String, expectedRevision: Int? = null) = enqueueAnswer(WizChatAnswerRecord(id, WizChatAnswerKind.CHOICE, textValue = value, expectedRevision = expectedRevision))
    fun answerMulti(id: WizChatQuestionId, values: List<String>, expectedRevision: Int? = null) = enqueueAnswer(WizChatAnswerRecord(id, WizChatAnswerKind.MULTI_CHOICE, values = values.distinct(), expectedRevision = expectedRevision))
    fun answerAction(id: WizChatQuestionId = _state.value.draft.wizChat.currentQuestionId, expectedRevision: Int? = null) = enqueueAnswer(WizChatAnswerRecord(id, WizChatAnswerKind.ACTION, expectedRevision = expectedRevision))
    fun skip(id: WizChatQuestionId = _state.value.draft.wizChat.currentQuestionId, expectedRevision: Int? = null) = enqueueAnswer(WizChatAnswerRecord(id, WizChatGraph.question(id)?.kind ?: WizChatAnswerKind.CHOICE, source = WizChatAnswerSource.OMITTED, expectedRevision = expectedRevision))
    fun selectPlan(id: String, expectedRevision: Int? = null) = answerChoice(WizChatQuestionId.T_PLAN, id, expectedRevision)
    fun answerTrainingMarks(squat: String, bench: String, deadlift: String, expectedRevision: Int? = null) = enqueueAnswer(
        WizChatAnswerRecord(WizChatQuestionId.T_MARKS, WizChatAnswerKind.ACTION, values = listOf(squat, bench, deadlift), expectedRevision = expectedRevision),
    )

    fun updateNameDraft(text: String) = mutateDraft {
        if (it.wizChat.currentQuestionId == WizChatQuestionId.P_NAME) it.copy(name = WizChatValidation.stripControls(text)) else it
    }
    fun setWeightUnit(unit: String) = mutateDraft { draft ->
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
    fun activeNutritionPlan(): NutritionPlan? {
        val repository = NutritionRepository.getInstance()
        val id = repository.activeNutritionPlanId.value ?: return null
        return repository.nutritionPlans.value.firstOrNull { it.id == id }
    }
    fun hasInitialRecoveryEvidence(): Boolean = ProgramRepository.getInstance().settings.value.initialRecoveryEvidence != null
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
                val matches = withContext(Dispatchers.Default) {
                    val all = exerciseLookup ?: (catalogRepository.state.value as? ExerciseCatalogStateV2.Ready)
                        ?.catalog?.toLegacyConfigurationLookup()?.values?.distinctBy { it.id }
                        .orEmpty().also { exerciseLookup = it }
                    all.asSequence().filter { it.name.contains(term, ignoreCase = true) }.take(14).toList()
                }
                currentCoroutineContext().ensureActive()
                if (_state.value.draft.wizChat.currentQuestionId == WizChatQuestionId.T_PLAN) _state.value = _state.value.copy(
                    exerciseSuggestions = matches, isExerciseSearching = false)
            } catch (cancel: CancellationException) { throw cancel }
            catch (error: Exception) {
                if (_state.value.draft.wizChat.currentQuestionId == WizChatQuestionId.T_PLAN) _state.value = _state.value.copy(
                    exerciseSuggestions = emptyList(), isExerciseSearching = false,
                    exerciseSearchError = "No pude consultar los ejercicios. Inténtalo otra vez.")
            }
        }
    }
    fun activeQuestion() = WizChatGraph.question(_state.value.draft.wizChat.currentQuestionId)
    fun ringsPreview(): SetupRingsMapping = ringsMapping(_state.value.draft)
    fun retryRingsPreview() {
        if (_state.value.machineState == WizChatMachineState.Committed || _state.value.isCommitting) return
        lastRingsPreviewKey = null
        prepareRingsPreview(_state.value.draft)
    }

    fun edit(id: WizChatQuestionId) {
        viewModelScope.launch { commandMutex.withLock {
            if (!initialized || _state.value.isCommitting || _state.value.machineState == WizChatMachineState.Committed) return@withLock
            val draft = _state.value.draft
            val remove = WizChatReducer.invalidatedAnswersFor(id) + id
            val next = draft.copy(wizChat = draft.wizChat.copy(currentQuestionId = id, stage = WizChatGraph.stageFor(id), terminal = false,
                revision = draft.wizChat.revision + 1,
                acceptedAnswers = draft.wizChat.acceptedAnswers.filterNot { it.questionId in remove }),
                selectedCatalogId = if (WizChatQuestionId.T_PLAN in remove) null else draft.selectedCatalogId,
                confirmActivation = false, acceptFixedRecipeDifference = false, revision = draft.revision + 1)
            persistAndPublish(next)
        } }
    }

    fun back(): Boolean {
        val id = _state.value.draft.wizChat.acceptedAnswers.lastOrNull()?.questionId ?: return false
        edit(id)
        return true
    }

    /** Compatibility entry point for advanced editor callers; it still uses the same queue and Room draft. */
    fun update(change: (SetupWizardDraft) -> SetupWizardDraft) = mutateDraft(change)
    fun setName(value: String) = updateNameDraft(value)
    fun setAge(value: Int?) = mutateDraft { it.copy(ageYears = value?.takeIf { age -> age in 13..100 }) }
    fun setWeightKg(value: Double?) = mutateDraft { it.copy(weightKg = value?.takeIf { kg -> kg.isFinite() && kg in 20.0..500.0 }) }
    fun setHeightCm(value: Double?) = mutateDraft { it.copy(heightCm = value?.takeIf { cm -> cm.isFinite() && cm in 100.0..250.0 }) }
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

    fun clear() {
        val id = _state.value.draft.draftId
        viewModelScope.launch { commandMutex.withLock {
            previewJob?.cancel()
            candidateJob?.cancel()
            exerciseSearchJob?.cancel()
            ringsPreviewJob?.cancel()
            runCatching { persistenceFactory(getApplication<Application>()).drafts.discard(id) }
            savedStateHandle[DRAFT_ID_KEY] = null
            initialized = true
            val fresh = newDraft(_state.value.mode, "create", null, id)
            publishDraft(fresh, false, WizChatMachineState.AwaitingAnswer)
        } }
    }

    suspend fun commit(context: Context): String? = commandMutex.withLock {
        val current = _state.value
        if (current.machineState == WizChatMachineState.Committed) return@withLock current.receiptId
        _state.value = current.copy(isCommitting = true, machineState = WizChatMachineState.Committing, errors = emptyMap())
        try {
            val errors = reviewErrors()
            if (errors.isNotEmpty()) {
                _state.value = _state.value.copy(isCommitting = false, machineState = WizChatMachineState.RecoverableError, errors = errors)
                return@withLock null
            }
            val draft = _state.value.draft
            val program = if (draft.includeTraining && draft.programRoute != SetupProgramRoute.LATER) _state.value.programPreview ?: error("La vista previa del programa no es ejecutable") else null
            val nutritionResult = if (draft.includeNutrition) prepareNutrition(draft) else null
            if (draft.includeNutrition && nutritionResult?.plan == null) error(nutritionResult?.errors?.values?.firstOrNull() ?: "Completa el plan de nutrición")
            val nutrition = nutritionResult?.plan
            val rings = ringsMapping(draft)
            val base = ProgramRepository.getInstance().settings.value
            val typed = nutrition?.typedBodyGoal
            val bodyGoals = typed?.targetValueSi?.let { target -> listOf(BodyGoal("plan:${nutrition.id}:${typed.metric.name}", typed.metric.toBodyMetric(), target, typed.unitSi, typed.origin, nutrition.id, System.currentTimeMillis(), System.currentTimeMillis())) }.orEmpty()
            val wellbeing = if (rings.completion == RingsCompletion.VALID && rings.evidence != null) {
                calculateRingsPreview(draft, rings).stagedWellbeing
            } else null
            val pendingNutritionDraft = if (SetupPendingNutrition.shouldPreserve(draft)) {
                val pending = SetupPendingNutrition.build(draft)
                SetupPendingNutrition.toCommitField(pending, json.encodeToString(pending))
            } else null
            val result = persistenceFactory(context).commits.commit(SetupCommitRequest(
                commitId = draft.commitId,
                draftId = draft.draftId,
                settings = base,
                program = program,
                nutritionPlan = nutrition,
                activateProgram = draft.includeTraining && draft.programRoute != SetupProgramRoute.LATER && draft.activateProgram,
                activateNutrition = draft.includeNutrition && draft.activateNutrition,
                derivedBodyGoals = bodyGoals,
                initialWellbeing = wellbeing,
                settingsPatch = buildSettingsPatch(base, draft, program, nutrition, rings),
                dailyGoalSnapshot = nutrition?.takeIf { draft.activateNutrition }?.let { plan -> DailyGoalSnapshot(LocalDate.now().toString(), plan.id, plan.calorieTarget, plan.proteinGoal, plan.carbGoal, plan.fatGoal, plan.direction, plan.calculationOrigin, System.currentTimeMillis()) },
                pendingNutritionDraft = pendingNutritionDraft,
            ))
            _state.value = _state.value.copy(draft = draft.copy(wizChat = draft.wizChat.copy(terminal = true, currentQuestionId = WizChatQuestionId.REVIEW, stage = WizChatStage.REVIEW)), dirty = false, receiptId = result.commitId, isCommitting = false, machineState = WizChatMachineState.Committed, errors = emptyMap())
            savedStateHandle[DRAFT_ID_KEY] = null
            result.commitId
        } catch (cancel: CancellationException) {
            _state.value = _state.value.copy(isCommitting = false, machineState = WizChatMachineState.RecoverableError, errors = mapOf("commit" to "La configuración se interrumpió. Inténtalo de nuevo."))
            throw cancel
        }
        catch (error: Throwable) {
            _state.value = _state.value.copy(isCommitting = false, machineState = WizChatMachineState.RecoverableError, errors = mapOf("commit" to (error.message ?: "No se pudo guardar la configuración")))
            null
        }
    }
    suspend fun commit(): String? = commit(getApplication())

    private fun enqueueAnswer(answer: WizChatAnswerRecord) = viewModelScope.launch { commandMutex.withLock { acceptAnswer(answer) } }

    private suspend fun acceptAnswer(answer: WizChatAnswerRecord) {
        val state = _state.value
        if (!initialized || state.isCommitting || state.machineState == WizChatMachineState.Committed) return
        val currentId = state.draft.wizChat.currentQuestionId
        if (answer.questionId != currentId || answer.expectedRevision != null && answer.expectedRevision != state.draft.wizChat.revision) return
        val question = WizChatGraph.question(currentId) ?: return
        if (answer.source == WizChatAnswerSource.OMITTED && currentId in WizChatValidation.mandatoryNumericQuestions) {
            _state.value = state.copy(machineState = WizChatMachineState.RecoverableError,
                errors = mapOf(currentId.name to "Este dato es necesario para continuar"))
            return
        }
        validateAnswer(currentId, answer, state.draft)?.let { error -> _state.value = state.copy(machineState = WizChatMachineState.RecoverableError, errors = mapOf(currentId.name to error)); return }
        val applied = applyAnswer(state.draft, answer)
        val next = nextQuestion(applied, currentId)
        val reduced = WizChatReducer.accept(state.draft.wizChat, currentId,
            answer.copy(acceptedAtMs = System.currentTimeMillis(), variantId = WizChatReducer.stableVariantId(
                state.draft.commitId, currentId, state.draft.wizChat.revision + 1, WizChatCopyCatalog.SCRIPT_VERSION)), next) ?: return
        val invalidated = reduced.invalidated
        val progress = reduced.progress.copy(
            acceptedAnswers = reduced.progress.acceptedAnswers.filterNot { it.questionId in invalidated },
            currentQuestionId = if (currentId == WizChatQuestionId.REVIEW) WizChatQuestionId.REVIEW else next,
            terminal = currentId == WizChatQuestionId.REVIEW,
        )
        val persisted = applied.copy(wizChat = progress, revision = state.draft.revision + 1)
        _state.value = state.copy(machineState = WizChatMachineState.PersistingAnswer, errors = emptyMap(), isSubmittingAnswer = true)
        if (!persistDraft(persisted)) return
        publishDraft(persisted, true, if (currentId == WizChatQuestionId.REVIEW) WizChatMachineState.Reviewing else WizChatMachineState.AwaitingAnswer)
        if (currentId != WizChatQuestionId.T_PLAN && trainingKey(state.draft) != trainingKey(persisted)) updateCandidates(persisted)
        preparePreview(persisted)
    }

    private fun validateAnswer(id: WizChatQuestionId, answer: WizChatAnswerRecord, draft: SetupWizardDraft): String? {
        val question = WizChatGraph.question(id) ?: return "Pregunta no disponible"
        if (answer.source == WizChatAnswerSource.OMITTED) return if (question.allowSkip || id in setOf(WizChatQuestionId.N_START, WizChatQuestionId.N_RESULT, WizChatQuestionId.R_START)) null else "Esta respuesta es necesaria para continuar"
        WizChatValidation.validate(question, answer.textValue, answer.numberValue, answer.values)?.let { return it }
        return when (id) {
            WizChatQuestionId.P_NAME -> if (WizChatValidation.cleanText(answer.textValue.orEmpty()).isBlank() && !question.allowSkip) "Escribe un nombre o usa omitir" else null
            WizChatQuestionId.N_START -> if (answer.textValue in question.options ||
                answer.textValue == "Conservar plan actual" && activeNutritionPlan() != null) null else "Elige una opción disponible"
            WizChatQuestionId.T_GOAL -> if (draft.trainingPath == SetupTrainingPath.FROM_SCRATCH && answer.textValue == "Fuerza + cardio") "Para combinar fuerza y cardio, elige un plan que programe ambas modalidades" else null
            WizChatQuestionId.T_DAYS -> if (answer.textValue?.toIntOrNull() !in 1..6) "Elige entre 1 y 6 días" else null
            WizChatQuestionId.T_WEEKDAYS -> if (answer.values.size != (draft.daysPerWeek ?: 0)) "Selecciona exactamente ${draft.daysPerWeek ?: 0} días" else null
            WizChatQuestionId.T_HOME_EQUIPMENT -> WizChatValidation.exclusiveMultiChoice(answer.values, "Sin material", "El equipo")
            WizChatQuestionId.N_ELIGIBILITY -> when {
                "No lo sé / prefiero no responder" in answer.values && answer.values.size > 1 -> "No lo sé / prefiero no responder es una opción exclusiva"
                else -> WizChatValidation.exclusiveMultiChoice(answer.values, "Ninguna de estas", "La selección")
            }
            WizChatQuestionId.R_DISCOMFORT -> WizChatValidation.exclusiveMultiChoice(answer.values, "Sin molestias", "La selección")
                ?: WizChatValidation.exclusiveMultiChoice(answer.values, "Prefiero omitirlo", "La selección")
            WizChatQuestionId.T_PLAN -> if (draft.trainingPath == SetupTrainingPath.FROM_SCRATCH) {
                if (answer.textValue != "from-scratch" || previewInputsIncomplete(draft)) "Completa al menos un ejercicio en cada sesión" else null
            } else if (_state.value.planCandidates.none { it.id == answer.textValue }) "Elige una opción compatible" else null
            WizChatQuestionId.T_MARKS -> {
                val parsed = answer.values.map { parseLocalizedNumber(it) }
                if (parsed.none { it != null }) "Añade al menos una marca, o vuelve y elige Todavía no"
                else if (parsed.any { it != null && it !in 1.0..1000.0 }) "Usa valores válidos entre 1 y 1000 kg"
                else null
            }
            WizChatQuestionId.T_REVIEW -> when {
                draft.includeTraining && draft.programRoute != SetupProgramRoute.LATER && (_state.value.programPreview == null || _state.value.isPreviewLoading || lastSuccessfulTrainingKey != trainingKey(draft)) -> "Espera a que el programa esté preparado"
                _state.value.fixedSessionEstimateMinutes?.let { it > (draft.minutesPerSession ?: 100) } == true -> "Esta receta supera los ${draft.minutesPerSession ?: 100} minutos por sesión; elige otra o ajusta el tiempo"
                fixedRecipeDifference(draft, _state.value.programPreview, _state.value.fixedSessionEstimateMinutes) && !draft.acceptFixedRecipeDifference -> "Confirma la diferencia entre tu disponibilidad y esta receta"
                else -> null
            }
            WizChatQuestionId.N_RESULT -> prepareNutrition(draft)?.errors?.values?.firstOrNull()
            WizChatQuestionId.R_RESULT -> if (ringsMapping(draft).completion == RingsCompletion.INCOMPLETE) "Completa las tres sensaciones requeridas" else null
            else -> null
        }
    }

    private fun nextQuestion(draft: SetupWizardDraft, current: WizChatQuestionId): WizChatQuestionId {
        val context = WizChatGraphContext(
        includeTraining = draft.includeTraining,
        includeNutrition = draft.includeNutrition,
        programRouteLater = draft.programRoute == SetupProgramRoute.LATER,
        trainingPlanSelected = draft.selectedCatalogId != null || draft.trainingPath == SetupTrainingPath.FROM_SCRATCH,
        recentTraining = draft.ringsAnswers?.recentTraining,
        recentTrainingUnknown = draft.ringsAnswers?.recentTrainingState == SetupRecentTrainingState.UNKNOWN,
        nutritionStarted = draft.includeNutrition && draft.nutritionMode == "create",
        nutritionProfessional = draft.nutritionDraft?.mode == "professional",
        ringsAction = draft.ringsAnswers?.startAction,
        homeEquipmentSelected = draft.trainingEnvironment == "Entreno en casa",
        hasTrainingMarks = draft.knowsTrainingMarks,
        includeRings = _state.value.mode in setOf(SetupWizardMode.FULL, SetupWizardMode.RESUME, SetupWizardMode.RINGS_ONLY),
        mixedTraining = draft.goal == SetupGoal.MIXED,
        goalStyleInferred = draft.goal?.inferredTrainingStyle != null,
        )
        var next = WizChatGraph.next(current, context) ?: WizChatQuestionId.REVIEW
        val invalidated = WizChatReducer.invalidatedAnswersFor(current)
        val kept = draft.wizChat.acceptedAnswers.map { it.questionId }.toSet() - invalidated - current
        val visited = mutableSetOf<WizChatQuestionId>()
        while (next != WizChatQuestionId.REVIEW && next in kept && visited.add(next)) {
            next = WizChatGraph.next(next, context) ?: WizChatQuestionId.REVIEW
        }
        return next
    }

    private fun applyAnswer(draft: SetupWizardDraft, answer: WizChatAnswerRecord): SetupWizardDraft {
        val text = WizChatValidation.cleanText(answer.textValue.orEmpty())
        val values = answer.values
        return when (answer.questionId) {
            WizChatQuestionId.P_NAME -> if (answer.source == WizChatAnswerSource.OMITTED) draft else draft.copy(name = text)
            WizChatQuestionId.P_GENDER -> draft.copy(profileGender = when (text) { "Mujer" -> Gender.FEMALE; "Hombre" -> Gender.MALE; "Otro" -> Gender.OTHER; else -> null })
            WizChatQuestionId.P_AGE -> if (answer.source == WizChatAnswerSource.OMITTED) draft else draft.copy(ageYears = answer.numberValue?.toInt())
            WizChatQuestionId.P_HEIGHT -> if (answer.source == WizChatAnswerSource.OMITTED) draft else draft.copy(heightCm = answer.numberValue)
            WizChatQuestionId.P_WEIGHT -> if (answer.source == WizChatAnswerSource.OMITTED) draft else draft.copy(weightKg = answer.numberValue)
            WizChatQuestionId.P_EXPERIENCE -> draft.copy(experience = SetupExperience.entries.first { it.label == text })
            WizChatQuestionId.T_ROUTE -> when (text) {
                "Elegir un protocolo" -> draft.copy(programRoute = SetupProgramRoute.PROTOCOL, trainingPath = SetupTrainingPath.PERSONALIZE)
                "Crear desde cero" -> draft.copy(programRoute = SetupProgramRoute.CUSTOMIZABLE, trainingPath = SetupTrainingPath.FROM_SCRATCH)
                "Lo decidiré después" -> draft.copy(programRoute = SetupProgramRoute.LATER, includeTraining = false, selectedCatalogId = null)
                else -> draft.copy(programRoute = SetupProgramRoute.CUSTOMIZABLE, trainingPath = SetupTrainingPath.PERSONALIZE, includeTraining = true)
            }
            WizChatQuestionId.T_GOAL -> {
                val goal = when (text) {
                    "Fuerza" -> SetupGoal.STRENGTH
                    "Músculo" -> SetupGoal.MUSCLE
                    "Fuerza y músculo" -> SetupGoal.STRENGTH_MUSCLE
                    "Fuerza + cardio" -> SetupGoal.MIXED
                    else -> SetupGoal.HEALTH
                }
                val base = draft.copy(goal = goal, cardioType = null, cardioMinutes = null)
                // The goal recalibrates the volume reference; an asked focus is
                // only valid once it has been answered again.
                val style = goal.inferredTrainingStyle
                if (style != null) base.withVolumeStyle(style)
                else base.copy(volumeAnswers = base.volumeAnswers.copy(style = null),
                    volumeCalibrationProfile = null, volumeRecommendations = emptyList(), athleteProfileScore = null)
            }
            WizChatQuestionId.T_STYLE -> draft.withVolumeStyle(when {
                text == "Powerlifting" || text == "Fuerza" -> TrainingStyle.POWERLIFTER
                text == "Powerbuilding" || text == "Ambos" -> TrainingStyle.POWERBUILDER
                else -> TrainingStyle.BODYBUILDER
            })
            WizChatQuestionId.T_VOLUME_TECHNIQUE -> withVolume(draft, answer, 0)
            WizChatQuestionId.T_VOLUME_CONSISTENCY -> withVolume(draft, answer, 1)
            WizChatQuestionId.T_VOLUME_STRENGTH -> withVolume(draft, answer, 2)
            WizChatQuestionId.T_VOLUME_MOBILITY -> withVolume(draft, answer, 3)
            WizChatQuestionId.T_EQUIPMENT -> draft.copy(trainingEnvironment = text, equipment = equipmentFor(text)?.let(::setOf) ?: emptySet())
            WizChatQuestionId.T_HOME_EQUIPMENT -> draft.copy(equipment = values.mapNotNull(::equipmentFor).toSet())
            WizChatQuestionId.T_DAYS -> draft.copy(daysPerWeek = text.toIntOrNull())
            WizChatQuestionId.T_WEEKDAYS -> draft.copy(selectedWeekdays = values.mapNotNull { weekdayLabels.indexOf(it).takeIf { index -> index >= 0 }?.plus(1) }.toSet())
            WizChatQuestionId.T_TIME -> draft.copy(minutesPerSession = answer.numberValue?.toInt())
            WizChatQuestionId.T_CARDIO_TYPE -> draft.copy(cardioType = when (text) { "Correr al aire libre" -> CardioType.RUN_OUTDOOR; "Bicicleta al aire libre" -> CardioType.BIKE_OUTDOOR; else -> CardioType.WALK })
            WizChatQuestionId.T_CARDIO_TIME -> draft.copy(cardioMinutes = Regex("\\d+").find(text)?.value?.toIntOrNull())
            WizChatQuestionId.T_TRAINING_MAX -> draft.copy(knowsTrainingMarks = text == "Conozco mis marcas", powerliftingProfile = if (text == "Conozco mis marcas") draft.powerliftingProfile else null)
            WizChatQuestionId.T_MARKS -> draft.copy(powerliftingProfile = (draft.powerliftingProfile ?: PowerliftingProfile()).copy(
                squat1RM = parseLocalizedNumber(values.getOrNull(0).orEmpty()),
                bench1RM = parseLocalizedNumber(values.getOrNull(1).orEmpty()),
                deadlift1RM = parseLocalizedNumber(values.getOrNull(2).orEmpty()),
            ))
            WizChatQuestionId.T_PLAN -> if (draft.trainingPath == SetupTrainingPath.FROM_SCRATCH) draft else draft.copy(selectedCatalogId = text, acceptFixedRecipeDifference = false)
            WizChatQuestionId.N_START -> SetupPendingNutrition.applyNStart(draft, text)
            WizChatQuestionId.N_SEX -> draft.withNutrition { it.copy(equationSex = when (text) { "Femenino" -> EerSex.FEMALE; "Masculino" -> EerSex.MALE; else -> null }) }
            WizChatQuestionId.N_ELIGIBILITY -> draft.withNutrition { it.copy(eligibilityUnknown = values.any { it.contains("no lo sé", true) || it.contains("no lo se", true) }, pregnant = values.any { it.contains("embarazo", true) }, lactating = values.any { it.contains("lactancia", true) }, medicalRestriction = values.any { it.contains("médica", true) || it.contains("medica", true) }) }
            WizChatQuestionId.N_DIRECTION -> draft.withNutrition { it.copy(direction = when (text) { "Definir" -> PlanDirection.DEFICIT; "Volumen" -> PlanDirection.SURPLUS; else -> PlanDirection.MAINTENANCE }) }
            WizChatQuestionId.N_ACTIVITY -> draft.withNutrition { it.copy(activity = when (text) { "Muy activo" -> EerActivity.VERY_ACTIVE; "Activo" -> EerActivity.ACTIVE; "Algo activo" -> EerActivity.LOW_ACTIVE; else -> EerActivity.INACTIVE }) }
            WizChatQuestionId.R_START -> draft.copy(ringsAnswers = (draft.ringsAnswers ?: SetupRingsAnswers()).copy(
                startAction = text, capturedAtMs = if (text == "Actualizarla" || text == "Preparar mi punto de partida") null else draft.ringsAnswers?.capturedAtMs))
            WizChatQuestionId.R_RECENT -> draft.copy(ringsAnswers = (draft.ringsAnswers ?: SetupRingsAnswers()).let { old ->
                if (text == "Sí") old.copy(recentTraining = true, recentTrainingState = SetupRecentTrainingState.YES, capturedAtMs = null)
                else old.copy(recentTraining = if (text == "No") false else null,
                    recentTrainingState = if (text == "No") SetupRecentTrainingState.NO else SetupRecentTrainingState.UNKNOWN,
                    sessionsLastSevenDays = null, lastSessionRecencyDays = null, recencyDays = null,
                    activityType = null, intensityLevel = null, axialExposure = InitialRecoveryAxialExposure(), capturedAtMs = null)
            })
            WizChatQuestionId.R_SESSIONS -> draft.copy(ringsAnswers = (draft.ringsAnswers ?: SetupRingsAnswers()).copy(sessionsLastSevenDays = answer.numberValue?.toInt() ?: text.toIntOrNull(), capturedAtMs = null))
            WizChatQuestionId.R_RECENCY -> draft.copy(ringsAnswers = (draft.ringsAnswers ?: SetupRingsAnswers()).copy(lastSessionRecencyDays = recencyFor(text), recencyDays = recencyFor(text), capturedAtMs = null))
            WizChatQuestionId.R_ACTIVITY -> draft.copy(ringsAnswers = (draft.ringsAnswers ?: SetupRingsAnswers()).copy(activityType = when { text.contains("cardio", true) -> InitialRecoveryActivityType.CARDIO; text.contains("mixta", true) -> InitialRecoveryActivityType.MIXED; else -> InitialRecoveryActivityType.STRENGTH }, activityTypeState = InitialRecoveryResponseState.DECLARED, capturedAtMs = null))
            WizChatQuestionId.R_INTENSITY -> draft.copy(ringsAnswers = (draft.ringsAnswers ?: SetupRingsAnswers()).copy(intensityLevel = intensityFor(text), capturedAtMs = null))
            WizChatQuestionId.R_AXIAL -> draft.copy(ringsAnswers = (draft.ringsAnswers ?: SetupRingsAnswers()).copy(axialExposure = InitialRecoveryAxialExposure(if (text == "Sí" || text == "No") InitialRecoveryResponseState.DECLARED else InitialRecoveryResponseState.UNKNOWN, if (text == "Sí") 1 else 0, draft.ringsAnswers?.intensityLevel, draft.ringsAnswers?.lastSessionRecencyDays), capturedAtMs = null))
            WizChatQuestionId.R_FEELINGS_MUSCLE -> draft.copy(ringsAnswers = (draft.ringsAnswers ?: SetupRingsAnswers()).copy(muscleFeeling = feelingFor(answer.questionId, text), capturedAtMs = null))
            WizChatQuestionId.R_FEELINGS_ENERGY -> draft.copy(ringsAnswers = (draft.ringsAnswers ?: SetupRingsAnswers()).copy(energy = feelingFor(answer.questionId, text), capturedAtMs = null))
            WizChatQuestionId.R_FEELINGS_STRUCTURE -> draft.copy(ringsAnswers = (draft.ringsAnswers ?: SetupRingsAnswers()).copy(structureFeeling = feelingFor(answer.questionId, text), capturedAtMs = null))
            WizChatQuestionId.R_DISCOMFORT -> draft.copy(ringsAnswers = (draft.ringsAnswers ?: SetupRingsAnswers()).copy(
                discomfortState = when { "Prefiero omitirlo" in values -> SetupDiscomfortState.OMITTED; "Sin molestias" in values -> SetupDiscomfortState.NONE; values.isNotEmpty() -> SetupDiscomfortState.DECLARED; else -> SetupDiscomfortState.NOT_ANSWERED },
                discomfortIds = values.filterNot { it == "Sin molestias" || it == "Prefiero omitirlo" }.mapNotNull { value -> DISCOMFORT_CATALOG_BY_ID[value]?.id ?: DISCOMFORT_CATALOG_BY_ID.values.firstOrNull { entry -> entry.label == value }?.id }.distinct()))
            WizChatQuestionId.N_RESULT -> if (answer.source == WizChatAnswerSource.OMITTED) draft.copy(includeNutrition = false) else draft
            WizChatQuestionId.R_RESULT -> if (ringsMapping(draft).completion == RingsCompletion.VALID) draft.copy(ringsAnswers = draft.ringsAnswers?.let { it.copy(capturedAtMs = it.capturedAtMs ?: System.currentTimeMillis()) }) else draft
            else -> draft
        }
    }

    private fun SetupWizardDraft.withVolumeStyle(style: TrainingStyle): SetupWizardDraft {
        val answers = volumeAnswers.copy(style = style)
        val profile = buildVolumeProfile(copy(volumeAnswers = answers))
        return copy(volumeAnswers = answers, volumeCalibrationProfile = profile, volumeRecommendations = profile?.recommendations.orEmpty(), athleteProfileScore = profile?.athleteProfileScore)
    }
    private fun withVolume(draft: SetupWizardDraft, answer: WizChatAnswerRecord, field: Int): SetupWizardDraft {
        val old = draft.volumeAnswers
        val value = answerIndex(answer.textValue)
        val answers = when (field) { 0 -> old.copy(technique = value); 1 -> old.copy(consistency = value); 2 -> old.copy(strength = value); else -> old.copy(mobility = value) }
        val profile = buildVolumeProfile(draft.copy(volumeAnswers = answers))
        return draft.copy(volumeAnswers = answers, volumeCalibrationProfile = profile, volumeRecommendations = profile?.recommendations.orEmpty(), athleteProfileScore = profile?.athleteProfileScore)
    }

    private suspend fun persistAndPublish(draft: SetupWizardDraft) {
        val previousKey = trainingKey(_state.value.draft)
        _state.value = _state.value.copy(machineState = WizChatMachineState.PersistingAnswer, errors = emptyMap())
        if (persistDraft(draft)) {
            publishDraft(draft, true, WizChatMachineState.AwaitingAnswer)
            if (previousKey != trainingKey(draft)) updateCandidates(draft)
            preparePreview(draft)
        }
    }
    private fun mutateDraft(change: (SetupWizardDraft) -> SetupWizardDraft) = viewModelScope.launch { commandMutex.withLock {
        if (!initialized || _state.value.isCommitting || _state.value.machineState == WizChatMachineState.Committed || _state.value.machineState == WizChatMachineState.UnsupportedDraft) return@withLock
        val old = _state.value.draft
        val changed = change(old)
        if (changed != old) persistAndPublish(changed.copy(revision = old.revision + 1))
    } }
    private suspend fun persistDraft(draft: SetupWizardDraft): Boolean = try {
        persistenceFactory(getApplication<Application>()).drafts.save(draft.draftId, json.encodeToString(draft), draft.revision.toLong(), PersonalizedPlanCatalog.REVISION)
        savedStateHandle[DRAFT_ID_KEY] = draft.draftId
        true
    } catch (cancel: CancellationException) { throw cancel }
    catch (error: Exception) {
        _state.value = _state.value.copy(machineState = WizChatMachineState.RecoverableError,
            errors = mapOf("draft" to "No pude guardar esta respuesta. Inténtalo de nuevo."), isSubmittingAnswer = false)
        false
    }
    private fun publishDraft(draft: SetupWizardDraft, dirty: Boolean, machine: WizChatMachineState) { _state.value = _state.value.copy(draft = draft, dirty = dirty, isLoading = false, machineState = machine, messages = messagesFor(draft), errors = emptyMap(), previewError = null, isSubmittingAnswer = false) }

    private fun trainingKey(draft: SetupWizardDraft): List<Any?> = listOf(
        draft.commitId, draft.includeTraining, draft.programRoute, draft.trainingPath, draft.goal, draft.focus,
        draft.experience, draft.daysPerWeek, draft.selectedWeekdays, draft.minutesPerSession, draft.equipment,
        draft.cardioType, draft.cardioMinutes,
        draft.volumeRecommendations, draft.priorityMuscles, draft.lowerEmphasisMuscles,
        draft.selectedSplitId, draft.customSplitPattern, draft.customSplitName,
        draft.selectedCatalogId, draft.sessions, draft.powerliftingProfile, draft.catalogRevision,
    )

    private fun preparePreview(draft: SetupWizardDraft) {
        prepareRingsPreview(draft)
        val key = trainingKey(draft)
        if (preparingTrainingKey == key && _state.value.isPreviewLoading) {
            updateNutritionPreview(draft)
            return
        }
        if (lastSuccessfulTrainingKey == key && _state.value.programPreview != null && _state.value.previewError == null) {
            updateNutritionPreview(draft)
            return
        }
        previewJob?.cancel()
        preparingTrainingKey = key
        previewJob = viewModelScope.launch {
            if (!initialized) return@launch
            if (!draft.includeTraining || draft.programRoute == SetupProgramRoute.LATER) { lastSuccessfulTrainingKey = null; _state.value = _state.value.copy(programPreview = null, previewReport = null, fixedSessionEstimateMinutes = null, fixedTrainingDays = null, isPreviewLoading = false); updateNutritionPreview(draft); return@launch }
            if (previewInputsIncomplete(draft)) { lastSuccessfulTrainingKey = null; _state.value = _state.value.copy(programPreview = null, previewReport = null, fixedSessionEstimateMinutes = null, fixedTrainingDays = null, isPreviewLoading = false); updateNutritionPreview(draft); return@launch }
            _state.value = _state.value.copy(machineState = WizChatMachineState.PreparingPreview, isPreviewLoading = true, previewError = null)
            try {
                val result = withContext(Dispatchers.Default) { materializeProgram(draft) }
                if (trainingKey(_state.value.draft) == key) {
                    lastSuccessfulTrainingKey = key
                    val isFixed = draft.selectedCatalogId?.let(PersonalizedPlanCatalog::find)?.source?.let { it != CatalogSource.NATIVE } == true
                    val minutes = if (isFixed) result.program?.let(::estimateFixedSessionMinutes) else null
                    _state.value = _state.value.copy(programPreview = result.program, previewReport = result.report, fixedSessionEstimateMinutes = minutes,
                        fixedTrainingDays = if (isFixed) result.program?.let(::fixedTrainingDays) else null,
                        isPreviewLoading = false, machineState = if (_state.value.draft.wizChat.currentQuestionId == WizChatQuestionId.REVIEW) WizChatMachineState.Reviewing else WizChatMachineState.AwaitingAnswer,
                        requiresActivationConfirmation = activationConfirmation(draft, result.program))
                    updateNutritionPreview(_state.value.draft)
                }
            } catch (cancel: CancellationException) { throw cancel }
            catch (error: Throwable) { if (trainingKey(_state.value.draft) == key) _state.value = _state.value.copy(isPreviewLoading = false, machineState = WizChatMachineState.RecoverableError, previewError = error.message ?: "No se pudo preparar la vista previa") }
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
        val mapping = ringsMapping(draft)
        val key = ringsKey(draft)
        if (mapping.completion != RingsCompletion.VALID || mapping.evidence == null) {
            ringsPreviewJob?.cancel()
            lastRingsPreviewKey = null
            _state.value = _state.value.copy(ringsBatteriesPreview = null, ringsPreviewLoading = false, ringsPreviewError = null)
            return
        }
        if (lastRingsPreviewKey == key && _state.value.ringsBatteriesPreview != null) return
        ringsPreviewJob?.cancel()
        _state.value = _state.value.copy(ringsPreviewLoading = true, ringsPreviewError = null)
        ringsPreviewJob = viewModelScope.launch {
            try {
                val result = calculateRingsPreview(draft, mapping)
                if (ringsKey(_state.value.draft) == key) {
                    lastRingsPreviewKey = key
                    _state.value = _state.value.copy(ringsBatteriesPreview = result.batteries, ringsPreviewLoading = false)
                }
            } catch (cancel: CancellationException) { throw cancel }
            catch (error: Throwable) {
                if (ringsKey(_state.value.draft) == key) _state.value = _state.value.copy(
                    ringsPreviewLoading = false, ringsPreviewError = "No pude preparar tus RINGS. Reintenta desde esta pregunta.")
            }
        }
    }

    private suspend fun calculateRingsPreview(draft: SetupWizardDraft, mapping: SetupRingsMapping): SetupRingsPreview {
        val evidence = requireNotNull(mapping.evidence)
        val a = requireNotNull(draft.ringsAnswers)
        val selected = draft.manualMuscleOverrides.filterKeys { it in evidence.perMuscleScores }
        val discomforts = when (a.discomfortState) {
            SetupDiscomfortState.NONE -> emptyList()
            SetupDiscomfortState.DECLARED -> a.discomfortIds
            else -> null
        }
        return SetupRingsPreviewCalculator(getApplication()).calculate(
            ProgramRepository.getInstance().settings.value, evidence, draft.commitId,
            selected, draft.manualEnergyOverride, draft.manualStructureOverride, discomforts,
            System.currentTimeMillis(),
        )
    }
    private fun updateNutritionPreview(draft: SetupWizardDraft) {
        val result = if (draft.includeNutrition) prepareNutrition(draft) else null
        _state.value = _state.value.copy(nutritionPlanPreview = result?.plan,
            nutritionErrors = result?.errors.orEmpty(),
            nutritionPacePercentPerWeek = result?.recommendation?.suggestedRatePercentBodyWeightPerWeek?.times(100.0),
            requiresActivationConfirmation = activationConfirmation(draft, _state.value.programPreview))
    }

    private fun messagesFor(draft: SetupWizardDraft): List<WizChatMessage> {
        val profileName = ProgramRepository.getInstance().settings.value.username
            ?.takeIf { it.isNotBlank() && it != "Usuario" }
        return WizChatMessageBuilder.build(
            acceptedAnswers = draft.wizChat.acceptedAnswers,
            currentQuestionId = draft.wizChat.currentQuestionId,
            weightUnit = draft.weightUnit,
            profileName = profileName,
        )
    }

    private fun updateCandidates(draft: SetupWizardDraft) {
        candidateJob?.cancel()
        val canPrepare = draft.includeTraining && draft.programRoute != SetupProgramRoute.LATER &&
            draft.trainingPath != SetupTrainingPath.FROM_SCRATCH && draft.daysPerWeek != null &&
            draft.minutesPerSession != null && draft.selectedWeekdays.size == draft.daysPerWeek && draft.equipment.isNotEmpty() &&
            (draft.goal != SetupGoal.MIXED || draft.cardioType != null && draft.cardioMinutes != null)
        if (!canPrepare) {
            _state.value = _state.value.copy(planCandidates = emptyList(), availablePlanCandidates = emptyList(), isCandidateLoading = false)
            return
        }
        _state.value = _state.value.copy(planCandidates = emptyList(), availablePlanCandidates = emptyList(), isCandidateLoading = true)
        candidateJob = viewModelScope.launch {
            try {
                val entries = withContext(Dispatchers.Default) {
                    val published = SetupTrainingPlanner.candidates(SetupTrainingPlannerInput(draft.trainingReference(), draft.daysPerWeek,
                        draft.equipment.map { it.catalogId }.toSet(), draft.experience.toCatalogLevel(),
                        draft.focus.toTrainingFocus(), protocolOnly = draft.programRoute == SetupProgramRoute.PROTOCOL,
                        mixedTraining = draft.goal == SetupGoal.MIXED))
                    val viable = mutableListOf<com.example.kpkn.data.programs.CatalogEntry>()
                    for (entry in published) {
                        currentCoroutineContext().ensureActive()
                        val executable = try {
                            val program = materializeProgram(draft.copy(selectedCatalogId = entry.id)).program
                            program != null && (entry.source == CatalogSource.NATIVE ||
                                (estimateFixedSessionMinutes(program) ?: 0) <= (draft.minutesPerSession ?: 100))
                        } catch (cancel: CancellationException) { throw cancel }
                        catch (_: Exception) { false }
                        if (executable) viable += entry
                        if (viable.size == 6) break
                    }
                    viable
                }
                if (trainingKey(_state.value.draft) == trainingKey(draft)) {
                    val options = entries.map { entry ->
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
                    _state.value = _state.value.copy(planCandidates = options.take(3),
                        availablePlanCandidates = options, isCandidateLoading = false)
                }
            } catch (cancel: CancellationException) { throw cancel }
            catch (error: Exception) {
                if (trainingKey(_state.value.draft) == trainingKey(draft)) _state.value = _state.value.copy(
                    planCandidates = emptyList(), availablePlanCandidates = emptyList(),
                    isCandidateLoading = false, previewError = "No pude comprobar los planes. Prueba de nuevo.")
            }
        }
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
                    equipment = draft.equipment.map { it.catalogId }.toSet(),
                    level = draft.experience.toCatalogLevel(),
                    availableMinutes = draft.minutesPerSession ?: error("Indica el tiempo disponible"),
                    cardio = if (draft.goal == SetupGoal.MIXED) CardioPreference(requireNotNull(draft.cardioType), requireNotNull(draft.cardioMinutes)) else null,
                    calibration = if (draft.volumeRecommendations.isNotEmpty()) Calibration.CALIBRATED else Calibration.CONSERVATIVE,
                    volumeRecommendations = draft.volumeRecommendations,
                    priorityMuscles = draft.priorityMuscles,
                    lowerEmphasisMuscles = draft.lowerEmphasisMuscles,
                    splitId = draft.selectedSplitId?.takeIf { it == "custom" },
                    splitPattern = draft.customSplitPattern,
                    splitName = draft.customSplitName,
                ),
            )
            return SetupPreview(result.program?.copy(id = draft.commitId) ?: error(result.report.limitations.joinToString(" ")), result.report)
        }
        val catalog = (catalogRepository.state.value as? ExerciseCatalogStateV2.Ready)?.catalog ?: error("El catálogo de ejercicios todavía no está disponible")
        val base = Program(id = draft.commitId, name = "Plan de ${draft.name.ifBlank { "entrenamiento" }}", startDay = draft.selectedWeekdays.minOrNull(), powerliftingProfile = draft.powerliftingProfile)
        val program = when (entry.source) {
            CatalogSource.PROTOCOL -> {
                val protocol = PROTOCOL_LIBRARY.first { it.id == entry.sourceId }
                ProgramProtocolEngine.applyProtocol(
                    program = base.copy(selectedSplitId = protocol.defaultSplit),
                    protocol = protocol,
                    metadata = CatalogCompositionMetadataProvider.fromCatalog(catalog),
                    exerciseList = catalog.toLegacyConfigurationLookup().values.toList(),
                )
            }
            CatalogSource.TEMPLATE -> ProgramTemplateEngine.applyTemplate(base, requireNotNull(entry.template), forceReplace = true).program
            CatalogSource.NATIVE -> error("Ruta nativa no válida")
        }.copy(id = draft.commitId)
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
        return SetupPreview(scheduled, null)
    }

    private fun prepareNutrition(draft: SetupWizardDraft): com.example.kpkn.domain.nutrition.NutritionPlanPreparationResult? {
        if (!draft.includeNutrition) return null
        val n = draft.nutritionDraft ?: return null
        val weight = draft.weightKg ?: parseLocalizedNumber(n.weightText)?.let { kilogramsFromInput(it, n.weightUnit) }
        val target = when (n.goalMetric) { GoalMetric.WEIGHT -> parseLocalizedNumber(n.targetWeightText.ifBlank { n.targetValueText })?.let { kilogramsFromInput(it, n.weightUnit) }; GoalMetric.BODY_FAT -> parseLocalizedNumber(n.targetBodyFatText.ifBlank { n.targetValueText }); GoalMetric.MUSCLE_MASS -> parseLocalizedNumber(n.targetMuscleText.ifBlank { n.targetValueText }) }
        return NutritionPlanPreparation.prepare(NutritionPlanPreparationInput(draft.nutritionPlanId ?: draft.commitId, NutritionRepository.getInstance().nutritionPlans.value.firstOrNull { it.id == (draft.nutritionPlanId ?: draft.commitId) }, draft.ageYears ?: parseLocalizedNumber(n.ageText)?.toInt(), draft.heightCm ?: parseLocalizedNumber(n.heightText), weight, n.equationSex, n.activity, n.eligibilityUnknown, n.pregnant, n.lactating, n.medicalRestriction, n.direction, n.goalMetric, target, parseLocalizedNumber(n.manualCalorieTargetText)?.toInt(), parseLocalizedNumber(n.manualProteinText), parseLocalizedNumber(n.manualCarbsText), parseLocalizedNumber(n.manualFatText), n.higherProteinInDeficit, parseLocalizedNumber(n.bodyFatText), parseLocalizedNumber(n.muscleText), n.direction?.let { paceRateFor(it, n.pacePreset) }))
    }
    private fun reviewErrors(): Map<String, String> = buildMap {
        val s = _state.value; val d = s.draft
        if (d.wizChat.currentQuestionId != WizChatQuestionId.REVIEW && !d.wizChat.terminal) put("flow", "Completa la revisión antes de activar")
        if (SetupDraftCompatibility.pendingMandatoryVitals(d).isNotEmpty()) put("profile", "Completa tu edad, estatura y peso para continuar")
        if (d.includeTraining && d.programRoute != SetupProgramRoute.LATER && (s.programPreview == null || s.previewError != null || s.isPreviewLoading || lastSuccessfulTrainingKey != trainingKey(d))) put("program", "Prepara una vista previa ejecutable")
        if (s.fixedSessionEstimateMinutes != null && s.fixedSessionEstimateMinutes > (d.minutesPerSession ?: 100)) put("time", "Esta receta supera los ${d.minutesPerSession ?: 100} minutos por sesión; elige otra o ajusta el tiempo")
        if (fixedRecipeDifference(d, s.programPreview, s.fixedSessionEstimateMinutes) && !d.acceptFixedRecipeDifference) put("schedule", "Confirma la rotación y la duración reales de la receta")
        if (d.includeNutrition && (s.nutritionPlanPreview == null || s.nutritionErrors.isNotEmpty())) put("nutrition", s.nutritionErrors.values.firstOrNull() ?: "Completa la nutrición")
        if (ringsMapping(d).completion == RingsCompletion.VALID && (s.ringsBatteriesPreview == null || s.ringsPreviewLoading || s.ringsPreviewError != null || lastRingsPreviewKey != ringsKey(d))) put("rings", s.ringsPreviewError ?: "Espera a que la vista previa de RINGS esté lista")
        if (activationConfirmation(d, s.programPreview) && !d.confirmActivation) put("activation", "Confirma la activación del plan")
    }
    private fun activationConfirmation(draft: SetupWizardDraft, preview: Program?): Boolean = (draft.activateProgram && preview != null && ProgramRepository.getInstance().activeProgramState.value?.programId?.let { it != preview.id } == true) || (draft.includeNutrition && draft.activateNutrition && NutritionRepository.getInstance().activeNutritionPlanId.value?.let { it != (draft.nutritionPlanId ?: draft.commitId) } == true)

    private fun buildSettingsPatch(base: Settings, draft: SetupWizardDraft, program: Program?, nutrition: NutritionPlan?, rings: SetupRingsMapping): SetupSettingsPatch {
        val accepted = draft.wizChat.acceptedAnswers.map { it.questionId }.toSet()
        val provided = draft.wizChat.acceptedAnswers.filter { it.source != WizChatAnswerSource.OMITTED }.map { it.questionId }.toSet()
        val age = draft.ageYears ?: parseLocalizedNumber(draft.nutritionDraft?.ageText.orEmpty())?.toInt()
        val evidence = when (rings.completion) { RingsCompletion.VALID -> SetupPatchField.Set(rings.evidence); RingsCompletion.OMITTED -> SetupPatchField.Clear; else -> SetupPatchField.Unchanged }
        return SetupSettingsPatch(
            username = if (WizChatQuestionId.P_NAME in provided && draft.name.isNotBlank()) SetupPatchField.Set(draft.name) else SetupPatchField.Unchanged,
            age = if (WizChatQuestionId.P_AGE in provided && age != null) SetupPatchField.Set(age) else SetupPatchField.Unchanged,
            vitalsPatch = SetupUserVitalsPatch(
                age = if (WizChatQuestionId.P_AGE in provided && age != null) SetupPatchField.Set(age) else SetupPatchField.Unchanged,
                height = if (WizChatQuestionId.P_HEIGHT in provided && draft.heightCm != null) SetupPatchField.Set(draft.heightCm) else SetupPatchField.Unchanged,
                weight = if (WizChatQuestionId.P_WEIGHT in provided && draft.weightKg != null) SetupPatchField.Set(draft.weightKg) else SetupPatchField.Unchanged,
                gender = if (WizChatQuestionId.P_GENDER in provided && draft.profileGender != null) SetupPatchField.Set(draft.profileGender) else SetupPatchField.Unchanged,
            ),
            weightUnit = if (draft.weightUnitChanged) SetupPatchField.Set(if (draft.weightUnit == "lb") WeightUnit.LBS else WeightUnit.KG) else SetupPatchField.Unchanged,
            dailyCalorieGoal = nutrition?.takeIf { draft.activateNutrition }?.let { SetupPatchField.Set(it.calorieTarget) } ?: SetupPatchField.Unchanged,
            dailyProteinGoal = nutrition?.takeIf { draft.activateNutrition }?.let { SetupPatchField.Set(it.proteinGoal) } ?: SetupPatchField.Unchanged,
            dailyCarbGoal = nutrition?.takeIf { draft.activateNutrition }?.let { SetupPatchField.Set(it.carbGoal) } ?: SetupPatchField.Unchanged,
            dailyFatGoal = nutrition?.takeIf { draft.activateNutrition }?.let { SetupPatchField.Set(it.fatGoal) } ?: SetupPatchField.Unchanged,
            onboardingCompleted = if (draft.draftScope == "full" && _state.value.mode in setOf(SetupWizardMode.FULL, SetupWizardMode.RESUME)) SetupPatchField.Set(true) else SetupPatchField.Unchanged,
            onboardingNameDone = if (WizChatQuestionId.P_NAME in provided && draft.name.isNotBlank()) SetupPatchField.Set(true) else SetupPatchField.Unchanged,
            onboardingProgramDone = if (_state.value.mode in setOf(SetupWizardMode.FULL, SetupWizardMode.RESUME, SetupWizardMode.TRAINING_ONLY) &&
                (!draft.includeTraining || draft.programRoute == SetupProgramRoute.LATER || program != null)) SetupPatchField.Set(true) else SetupPatchField.Unchanged,
            onboardingNutritionDone = if (_state.value.mode in setOf(SetupWizardMode.FULL, SetupWizardMode.RESUME, SetupWizardMode.NUTRITION_ONLY) &&
                (!draft.includeNutrition || nutrition != null)) SetupPatchField.Set(true) else SetupPatchField.Unchanged,
            nutritionTrackingChoice = when { nutrition != null && draft.activateNutrition -> SetupPatchField.Set(NutritionTrackingChoice.ENABLED); !draft.includeNutrition && !SetupPendingNutrition.shouldPreserve(draft) && WizChatQuestionId.N_START in accepted && NutritionRepository.getInstance().activeNutritionPlanId.value == null -> SetupPatchField.Set(NutritionTrackingChoice.SKIPPED); else -> SetupPatchField.Unchanged },
            initialRecoveryEvidence = evidence,
            volumeCalibrationProfile = draft.volumeCalibrationProfile?.let { SetupPatchField.Set(it) } ?: SetupPatchField.Unchanged,
        )
    }

    private fun ringsMapping(draft: SetupWizardDraft): SetupRingsMapping {
        val a = draft.ringsAnswers ?: return SetupRingsMapping(RingsCompletion.UNKNOWN)
        return SetupRingsMapper.map(SetupRingsInput(a.startAction, a.recentTraining, a.recentTrainingState == SetupRecentTrainingState.UNKNOWN, a.sessionsLastSevenDays, a.lastSessionRecencyDays ?: a.recencyDays, a.activityType, a.intensityLevel, a.muscleFeeling, a.energy, a.structureFeeling, a.axialExposure.state, a.axialExposure.sessions, a.intensityLevel, a.muscleScope, a.recentMuscles.toList(), a.discomfortIds, a.capturedAtMs), System.currentTimeMillis())
    }
    private fun buildVolumeProfile(draft: SetupWizardDraft): VolumeCalibrationProfile? {
        val a = draft.volumeAnswers; val style = a.style ?: return null; val t = a.technique ?: return null; val c = a.consistency ?: return null; val s = a.strength ?: return null; val m = a.mobility ?: return null; val output = VolumeCalibrationEngine.calculate(style, t, c, s, m)
        return VolumeCalibrationProfile(style, output.score, VolumeCalibrationResponses(t, c, s, m, a.responseState.takeIf { it != CalibrationResponseState.UNKNOWN } ?: CalibrationResponseState.DECLARED), output.recommendations, System.currentTimeMillis(), VolumeCalibrationEngine.REVISION)
    }

    private fun newDraft(mode: SetupWizardMode, nutritionMode: String, nutritionPlanId: String?, id: String): SetupWizardDraft {
        val settings = ProgramRepository.getInstance().settings.value
        val training = mode != SetupWizardMode.NUTRITION_ONLY && mode != SetupWizardMode.RINGS_ONLY
        val nutrition = mode != SetupWizardMode.TRAINING_ONLY && mode != SetupWizardMode.RINGS_ONLY
        val first = WizChatGraph.firstFor(WizChatGraphContext(training, nutrition))
        val progress = WizChatProgress(scriptVersion = WizChatCopyCatalog.SCRIPT_VERSION,
            draftScope = scopeFor(mode).name.lowercase(), currentQuestionId = first, stage = WizChatGraph.stageFor(first))
        val unit = if (settings.weightUnit == WeightUnit.LBS) "lb" else "kg"
        return SetupWizardDraft(draftId = id, commitId = UUID.randomUUID().toString(), draftScope = scopeFor(mode).name.lowercase(), name = settings.username.takeIf { it != "Usuario" }.orEmpty(), moduleChoice = if (nutrition) SetupModuleChoice.TRAINING_AND_NUTRITION else SetupModuleChoice.TRAINING, ageYears = settings.userVitals.age ?: settings.age, heightCm = settings.userVitals.height, weightKg = settings.userVitals.weight, importedWeightKg = settings.userVitals.weight, weightUnit = unit, includeTraining = training, includeNutrition = nutrition, programRoute = if (training) SetupProgramRoute.CUSTOMIZABLE else SetupProgramRoute.LATER, trainingPath = if (training) SetupTrainingPath.PERSONALIZE else null, nutritionMode = nutritionMode, nutritionPlanId = nutritionPlanId, nutritionDraft = if (nutrition) NutritionWizardDraft(mode = nutritionMode, planId = nutritionPlanId, weightUnit = unit) else null, catalogRevision = PersonalizedPlanCatalog.REVISION, wizChat = progress)
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
    private fun SetupWizardDraft.withNutrition(update: (NutritionWizardDraft) -> NutritionWizardDraft): SetupWizardDraft { val value = nutritionDraft ?: NutritionWizardDraft(mode = nutritionMode, planId = nutritionPlanId); return copy(nutritionDraft = update(value)) }
    private fun answerIndex(value: String?) = when { value?.contains("Inicial", true) == true || value?.contains("Aprendiendo", true) == true || value?.contains("Irregular", true) == true || value?.contains("Limitada", true) == true -> 1; value?.contains("Intermedia", true) == true || value?.contains("estable", true) == true || value?.contains("constante", true) == true || value?.contains("Suficiente", true) == true -> 2; else -> 3 }
    private fun feelingFor(id: WizChatQuestionId, value: String): Int? = WizChatGraph.question(id)?.options?.indexOf(value)?.takeIf { it >= 0 }?.plus(1)
    private fun intensityFor(value: String) = when { value.contains("fácil", true) -> InitialRecoveryIntensity.EASY; value.contains("moderada", true) -> InitialRecoveryIntensity.MODERATE; value.contains("exigente", true) && !value.contains("muy", true) -> InitialRecoveryIntensity.HARD; else -> InitialRecoveryIntensity.VERY_HARD }
    private fun recencyFor(value: String) = when { value.equals("Hoy", true) -> 0; value.equals("Ayer", true) -> 1; else -> Regex("\\d+").find(value)?.value?.toIntOrNull()?.coerceIn(0, 6) ?: 6 }
    private fun equipmentFor(value: String): SetupEquipment? = when (value) {
        "Sin material" -> SetupEquipment.NONE
        "Peso corporal" -> SetupEquipment.BODYWEIGHT
        "Bandas" -> SetupEquipment.BANDS
        "Mancuernas" -> SetupEquipment.DUMBBELLS
        "Barra de dominadas" -> SetupEquipment.PULL_UP
        "Apoyo estable" -> SetupEquipment.SUPPORT
        "Principalmente máquinas" -> SetupEquipment.MACHINE
        "Gimnasio completo" -> SetupEquipment.GYM
        else -> null
    }
    private fun com.example.kpkn.data.models.GoalMetric.toBodyMetric() = when (this) { GoalMetric.WEIGHT -> BodyMetric.WEIGHT; GoalMetric.BODY_FAT -> BodyMetric.BODY_FAT_PERCENT; GoalMetric.MUSCLE_MASS -> BodyMetric.MUSCLE_MASS_PERCENT }

    companion object { private const val DRAFT_ID_KEY = "setup_wizard_draft_id"; private val weekdayLabels = listOf("Lunes", "Martes", "Miércoles", "Jueves", "Viernes", "Sábado", "Domingo") }
}

private fun List<SetupSessionDraft>.ensureSession(weekday: Int): List<SetupSessionDraft> = if (any { it.weekday == weekday }) this else this + SetupSessionDraft(weekday, "Sesión del día $weekday")
