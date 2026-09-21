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
import kotlinx.serialization.decodeFromString
import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.Json

/** Single serialized WIZCHAT orchestrator. Room owns the full draft; SavedStateHandle owns IDs only. */
class SetupWizardViewModel(
    application: Application,
    private val savedStateHandle: SavedStateHandle,
) : AndroidViewModel(application) {
    private val json = Json { ignoreUnknownKeys = true; encodeDefaults = true; coerceInputValues = true }
    private val commandMutex = Mutex()
    private val catalogRepository = ApprovedAssetExerciseCatalogRepositoryV2(application.applicationContext)
    private var catalogLoaded = false
    private var initialized = false
    private var currentDraftId: String? = null
    private var previewJob: kotlinx.coroutines.Job? = null

    private val _state = MutableStateFlow(SetupWizardState(SetupWizardDraft(commitId = UUID.randomUUID().toString()), isLoading = true))
    val state: StateFlow<SetupWizardState> = _state.asStateFlow()

    fun initialize(mode: SetupWizardMode, nutritionMode: String = "create", nutritionPlanId: String? = null, draftId: String? = null) {
        if (initialized && _state.value.mode == mode && (draftId == null || draftId == currentDraftId)) return
        initialized = false
        _state.value = _state.value.copy(mode = mode, isLoading = true, machineState = WizChatMachineState.Loading)
        viewModelScope.launch {
            try {
                ProgramRepository.getInstance().isReady.first { it }
                if (!catalogLoaded) { catalogRepository.load(); catalogLoaded = true }
                val factory = persistenceFactory(getApplication<Application>())
                val storedCandidate = savedStateHandle.get<String>(DRAFT_ID_KEY)?.takeIf(String::isNotBlank)
                val storedId = storedCandidate?.takeIf { mode == SetupWizardMode.RESUME || it == SetupDraftResolver.canonicalDraftId(scopeFor(mode)) }
                val resumeId = if (mode == SetupWizardMode.RESUME) factory.database.setupDraftDao().getAllDrafts().firstOrNull()?.draftId else null
                val id: String = draftId ?: storedId ?: resumeId ?: SetupDraftResolver.canonicalDraftId(scopeFor(mode))
                val persisted = factory.drafts.load(id)
                val restored = persisted?.let { runCatching { json.decodeFromString<SetupWizardDraft>(it.payloadJson) }.getOrNull() }
                if (persisted != null && restored == null) {
                    currentDraftId = id
                    savedStateHandle[DRAFT_ID_KEY] = id
                    _state.value = _state.value.copy(isLoading = false, machineState = WizChatMachineState.UnsupportedDraft, errors = mapOf("draft" to "Este borrador no es compatible con la versión actual. Puedes conservarlo y comenzar uno nuevo."))
                    return@launch
                }
                val draft = (restored ?: newDraft(mode, nutritionMode, nutritionPlanId, id)).let { value ->
                    value.copy(
                        draftId = value.draftId.ifBlank { id },
                        draftScope = value.draftScope.ifBlank { scopeFor(mode).name.lowercase() },
                        nutritionMode = nutritionMode.takeIf { restored == null } ?: value.nutritionMode,
                        nutritionPlanId = nutritionPlanId ?: value.nutritionPlanId,
                        wizChat = normalizeProgress(value.wizChat, value.draftScope.ifBlank { scopeFor(mode).name.lowercase() }, mode),
                    )
                }
                currentDraftId = draft.draftId
                savedStateHandle[DRAFT_ID_KEY] = draft.draftId
                initialized = true
                publishDraft(draft, restored != null, WizChatMachineState.AwaitingAnswer)
                updateCandidates(draft)
                preparePreview(draft)
            } catch (cancel: CancellationException) { throw cancel }
            catch (error: Throwable) {
                _state.value = _state.value.copy(isLoading = false, machineState = WizChatMachineState.RecoverableError, errors = mapOf("initialize" to (error.message ?: "No se pudo cargar el asistente")))
            }
        }
    }

    fun answerText(text: String) = enqueueAnswer(WizChatAnswerRecord(WizChatQuestionId.P_NAME, WizChatAnswerKind.TEXT, textValue = text))
    fun answerNumber(id: WizChatQuestionId, value: Double) = enqueueAnswer(WizChatAnswerRecord(id, WizChatAnswerKind.NUMBER, numberValue = value))
    fun answerChoice(id: WizChatQuestionId, value: String) = enqueueAnswer(WizChatAnswerRecord(id, WizChatAnswerKind.CHOICE, textValue = value))
    fun answerMulti(id: WizChatQuestionId, values: List<String>) = enqueueAnswer(WizChatAnswerRecord(id, WizChatAnswerKind.MULTI_CHOICE, values = values.distinct()))
    fun answerAction(id: WizChatQuestionId = _state.value.draft.wizChat.currentQuestionId) = enqueueAnswer(WizChatAnswerRecord(id, WizChatAnswerKind.ACTION))
    fun skip(id: WizChatQuestionId = _state.value.draft.wizChat.currentQuestionId) = enqueueAnswer(WizChatAnswerRecord(id, WizChatGraph.question(id)?.kind ?: WizChatAnswerKind.CHOICE, source = WizChatAnswerSource.OMITTED))
    fun selectPlan(id: String) = answerChoice(WizChatQuestionId.T_PLAN, id)

    fun updateNameDraft(text: String) = mutateDraft { it.copy(name = WizChatValidation.cleanText(text)) }
    fun updateNutritionInput(update: (NutritionWizardDraft) -> NutritionWizardDraft) = mutateDraft {
        val current = it.nutritionDraft ?: NutritionWizardDraft(mode = it.nutritionMode, planId = it.nutritionPlanId)
        it.copy(nutritionDraft = update(current))
    }
    fun toggleSound() = mutateDraft { it.copy(wizChat = it.wizChat.copy(soundEnabled = !it.wizChat.soundEnabled)) }
    fun openAdvanced() = mutateDraft { it.copy(wizChat = it.wizChat.copy(advancedBranches = it.wizChat.advancedBranches + "training")) }
    fun openRingsAdvanced() = mutateDraft { it.copy(wizChat = it.wizChat.copy(advancedBranches = it.wizChat.advancedBranches + "rings")) }
    fun setRingsMuscleScope(scope: InitialRecoveryMuscleScope) = mutateDraft { draft ->
        val answers = draft.ringsAnswers ?: SetupRingsAnswers()
        draft.copy(ringsAnswers = answers.copy(muscleScope = scope, recentMuscles = if (scope == InitialRecoveryMuscleScope.FULL_BODY) emptySet() else answers.recentMuscles))
    }
    fun toggleRingsMuscle(muscle: String) = mutateDraft { draft ->
        val answers = draft.ringsAnswers ?: SetupRingsAnswers(muscleScope = InitialRecoveryMuscleScope.SELECTED)
        val selected = if (muscle in answers.recentMuscles) answers.recentMuscles - muscle else answers.recentMuscles + muscle
        draft.copy(ringsAnswers = answers.copy(muscleScope = InitialRecoveryMuscleScope.SELECTED, recentMuscles = selected))
    }
    fun setManualMuscleOverride(muscle: String, level: Int) = mutateDraft { it.copy(manualMuscleOverrides = it.manualMuscleOverrides + (muscle to level.coerceIn(1, 5))) }
    fun setManualEnergyOverride(level: Int) = mutateDraft { it.copy(manualEnergyOverride = level.coerceIn(1, 5)) }
    fun setManualStructureOverride(level: Int) = mutateDraft { it.copy(manualStructureOverride = level.coerceIn(1, 5)) }
    fun candidatePlans(): List<SetupPlanCandidate> = _state.value.planCandidates
    fun activeQuestion() = WizChatGraph.question(_state.value.draft.wizChat.currentQuestionId)
    fun ringsPreview(): SetupRingsMapping = ringsMapping(_state.value.draft)

    fun edit(id: WizChatQuestionId) {
        viewModelScope.launch { commandMutex.withLock {
            val draft = _state.value.draft
            val remove = WizChatReducer.invalidatedAnswersFor(id) + id
            val next = draft.copy(wizChat = draft.wizChat.copy(currentQuestionId = id, stage = WizChatGraph.stageFor(id), terminal = false, acceptedAnswers = draft.wizChat.acceptedAnswers.filterNot { it.questionId in remove }), confirmActivation = false, revision = draft.revision + 1)
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
    fun setPriorityMuscles(value: Set<String>) = mutateDraft { it.copy(priorityMuscles = value.map(String::trim).filter(String::isNotBlank).toSet()) }
    fun setLowerEmphasisMuscles(value: Set<String>) = mutateDraft { it.copy(lowerEmphasisMuscles = value.map(String::trim).filter(String::isNotBlank).toSet()) }
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
            runCatching { persistenceFactory(getApplication<Application>()).drafts.discard(id) }
            savedStateHandle[DRAFT_ID_KEY] = null
            initialized = false
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
            val bodyGoals = typed?.targetValueSi?.let { target -> listOf(BodyGoal("${draft.commitId}-body-goal", typed.metric.toBodyMetric(), target, typed.unitSi, typed.origin, nutrition.id, System.currentTimeMillis(), System.currentTimeMillis())) }.orEmpty()
            val result = persistenceFactory(context).commits.commit(SetupCommitRequest(
                commitId = draft.commitId,
                draftId = draft.draftId,
                settings = base,
                program = program,
                nutritionPlan = nutrition,
                activateProgram = draft.includeTraining && draft.programRoute != SetupProgramRoute.LATER && draft.activateProgram,
                activateNutrition = draft.includeNutrition && draft.activateNutrition,
                derivedBodyGoals = bodyGoals,
                initialWellbeing = initialWellbeing(draft, rings),
                settingsPatch = buildSettingsPatch(base, draft, program, nutrition, rings),
                dailyGoalSnapshot = nutrition?.takeIf { draft.activateNutrition }?.let { plan -> DailyGoalSnapshot(LocalDate.now().toString(), plan.id, plan.calorieTarget, plan.proteinGoal, plan.carbGoal, plan.fatGoal, plan.direction, plan.calculationOrigin, System.currentTimeMillis()) },
            ))
            _state.value = _state.value.copy(draft = draft.copy(wizChat = draft.wizChat.copy(terminal = true, currentQuestionId = WizChatQuestionId.REVIEW, stage = WizChatStage.REVIEW)), dirty = false, receiptId = result.commitId, isCommitting = false, machineState = WizChatMachineState.Committed, errors = emptyMap())
            savedStateHandle[DRAFT_ID_KEY] = null
            result.commitId
        } catch (cancel: CancellationException) { throw cancel }
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
        if (answer.questionId != currentId) return
        val question = WizChatGraph.question(currentId) ?: return
        validateAnswer(currentId, answer, state.draft)?.let { error -> _state.value = state.copy(machineState = WizChatMachineState.RecoverableError, errors = mapOf(currentId.name to error)); return }
        val applied = applyAnswer(state.draft, answer)
        val next = nextQuestion(applied, currentId)
        val reduced = WizChatReducer.accept(state.draft.wizChat, currentId, answer.copy(acceptedAtMs = System.currentTimeMillis()), next) ?: return
        val invalidated = reduced.invalidated
        val progress = reduced.progress.copy(
            acceptedAnswers = reduced.progress.acceptedAnswers.filterNot { it.questionId in invalidated },
            currentQuestionId = if (currentId == WizChatQuestionId.REVIEW) WizChatQuestionId.REVIEW else next,
            terminal = currentId == WizChatQuestionId.REVIEW,
        )
        val persisted = applied.copy(wizChat = progress, revision = state.draft.revision + 1)
        _state.value = state.copy(machineState = WizChatMachineState.PersistingAnswer, errors = emptyMap())
        if (!persistDraft(persisted)) return
        publishDraft(persisted, true, if (currentId == WizChatQuestionId.REVIEW) WizChatMachineState.Reviewing else WizChatMachineState.AwaitingAnswer)
        updateCandidates(persisted)
        preparePreview(persisted)
    }

    private fun validateAnswer(id: WizChatQuestionId, answer: WizChatAnswerRecord, draft: SetupWizardDraft): String? {
        val question = WizChatGraph.question(id) ?: return "Pregunta no disponible"
        if (answer.source == WizChatAnswerSource.OMITTED) return if (question.allowSkip || id in setOf(WizChatQuestionId.N_START, WizChatQuestionId.N_RESULT, WizChatQuestionId.R_START)) null else "Esta respuesta es necesaria para continuar"
        WizChatValidation.validate(question, answer.textValue, answer.numberValue, answer.values)?.let { return it }
        return when (id) {
            WizChatQuestionId.P_NAME -> if (WizChatValidation.cleanText(answer.textValue.orEmpty()).isBlank() && !question.allowSkip) "Escribe un nombre o usa omitir" else null
            WizChatQuestionId.T_DAYS -> if (answer.numberValue?.toInt() !in 1..6) "Elige entre 1 y 6 días" else null
            WizChatQuestionId.T_WEEKDAYS -> if (answer.values.size != (draft.daysPerWeek ?: 0)) "Selecciona exactamente ${draft.daysPerWeek ?: 0} días" else null
            WizChatQuestionId.T_EQUIPMENT -> WizChatValidation.exclusiveMultiChoice(answer.values, "Sin material", "El equipo")
            WizChatQuestionId.N_ELIGIBILITY -> when {
                "No lo sé / prefiero no responder" in answer.values && answer.values.size > 1 -> "No lo sé / prefiero no responder es una opción exclusiva"
                else -> WizChatValidation.exclusiveMultiChoice(answer.values, "Ninguna de estas", "La selección")
            }
            WizChatQuestionId.R_DISCOMFORT -> WizChatValidation.exclusiveMultiChoice(answer.values, "Sin molestias", "La selección")
            WizChatQuestionId.T_PLAN -> if (_state.value.planCandidates.none { it.id == answer.textValue }) "Elige una opción compatible" else null
            WizChatQuestionId.N_RESULT -> prepareNutrition(draft)?.errors?.values?.firstOrNull()
            WizChatQuestionId.R_RESULT -> if (ringsMapping(draft).completion == RingsCompletion.INCOMPLETE) "Completa las tres sensaciones requeridas" else null
            else -> null
        }
    }

    private fun nextQuestion(draft: SetupWizardDraft, current: WizChatQuestionId): WizChatQuestionId = WizChatGraph.next(current, WizChatGraphContext(
        includeTraining = draft.includeTraining,
        includeNutrition = draft.includeNutrition,
        programRouteLater = draft.programRoute == SetupProgramRoute.LATER,
        trainingPlanSelected = draft.selectedCatalogId != null || draft.trainingPath == SetupTrainingPath.FROM_SCRATCH,
        recentTraining = draft.ringsAnswers?.recentTraining,
        recentTrainingUnknown = draft.ringsAnswers?.recentTrainingState == SetupRecentTrainingState.UNKNOWN,
        nutritionStarted = draft.nutritionDraft?.direction != null,
        nutritionProfessional = draft.nutritionDraft?.mode == "professional",
        ringsAction = draft.ringsAnswers?.startAction,
    )) ?: WizChatQuestionId.REVIEW

    private fun applyAnswer(draft: SetupWizardDraft, answer: WizChatAnswerRecord): SetupWizardDraft {
        val text = WizChatValidation.cleanText(answer.textValue.orEmpty())
        val values = answer.values
        return when (answer.questionId) {
            WizChatQuestionId.P_NAME -> draft.copy(name = text)
            WizChatQuestionId.P_AGE -> draft.copy(ageYears = answer.numberValue?.toInt())
            WizChatQuestionId.P_HEIGHT -> draft.copy(heightCm = answer.numberValue)
            WizChatQuestionId.P_WEIGHT -> draft.copy(weightKg = answer.numberValue)
            WizChatQuestionId.P_EXPERIENCE -> draft.copy(experience = when { text.contains("empez", true) -> SetupExperience.NEW; text.contains("volviendo", true) -> SetupExperience.RETURNING; text.contains("constancia", true) -> SetupExperience.INTERMEDIATE; else -> SetupExperience.ADVANCED })
            WizChatQuestionId.T_ROUTE -> when { text.contains("protocolo", true) -> draft.copy(programRoute = SetupProgramRoute.PROTOCOL, trainingPath = SetupTrainingPath.PERSONALIZE); text.contains("cero", true) -> draft.copy(programRoute = SetupProgramRoute.CUSTOMIZABLE, trainingPath = SetupTrainingPath.FROM_SCRATCH); text.contains("despu", true) -> draft.copy(programRoute = SetupProgramRoute.LATER, includeTraining = false, selectedCatalogId = null); else -> draft.copy(programRoute = SetupProgramRoute.CUSTOMIZABLE, trainingPath = SetupTrainingPath.PERSONALIZE, includeTraining = true) }
            WizChatQuestionId.T_GOAL -> draft.copy(goal = when { text.equals("Fuerza", true) -> SetupGoal.STRENGTH; text.contains("Músculo", true) -> SetupGoal.MUSCLE; text.contains("cardio", true) -> SetupGoal.MIXED; else -> SetupGoal.HEALTH })
            WizChatQuestionId.T_STYLE -> draft.withVolumeStyle(when { text.contains("powerlifting", true) -> TrainingStyle.POWERLIFTER; text.contains("powerbuilding", true) -> TrainingStyle.POWERBUILDER; else -> TrainingStyle.BODYBUILDER })
            WizChatQuestionId.T_VOLUME_TECHNIQUE -> withVolume(draft, answer, 0)
            WizChatQuestionId.T_VOLUME_CONSISTENCY -> withVolume(draft, answer, 1)
            WizChatQuestionId.T_VOLUME_STRENGTH -> withVolume(draft, answer, 2)
            WizChatQuestionId.T_VOLUME_MOBILITY -> withVolume(draft, answer, 3)
            WizChatQuestionId.T_EQUIPMENT -> draft.copy(equipment = values.mapNotNull(::equipmentFor).toSet())
            WizChatQuestionId.T_DAYS -> draft.copy(daysPerWeek = answer.numberValue?.toInt())
            WizChatQuestionId.T_WEEKDAYS -> draft.copy(selectedWeekdays = values.mapNotNull { weekdayLabels.indexOf(it).takeIf { index -> index >= 0 }?.plus(1) }.toSet())
            WizChatQuestionId.T_TIME -> draft.copy(minutesPerSession = answer.numberValue?.toInt())
            WizChatQuestionId.T_PLAN -> draft.copy(selectedCatalogId = text)
            WizChatQuestionId.N_START -> when { text.contains("despu", true) -> draft.copy(includeNutrition = false); text.contains("profesional", true) -> draft.withNutrition { it.copy(mode = "professional", direction = PlanDirection.PROFESSIONAL) }.copy(includeNutrition = true, nutritionMode = "professional"); else -> draft.copy(includeNutrition = true, nutritionMode = "create", nutritionDraft = draft.nutritionDraft ?: NutritionWizardDraft(mode = "create", planId = draft.nutritionPlanId)) }
            WizChatQuestionId.N_SEX -> draft.withNutrition { it.copy(equationSex = when { text.contains("femen", true) -> EerSex.FEMALE; text.contains("mascul", true) -> EerSex.MALE; else -> null }) }
            WizChatQuestionId.N_ELIGIBILITY -> draft.withNutrition { it.copy(eligibilityUnknown = values.any { it.contains("no lo sé", true) || it.contains("no lo se", true) }, pregnant = values.any { it.contains("embarazo", true) }, lactating = values.any { it.contains("lactancia", true) }, medicalRestriction = values.any { it.contains("médica", true) || it.contains("medica", true) }) }
            WizChatQuestionId.N_DIRECTION -> draft.withNutrition { it.copy(direction = when { text.contains("definir", true) -> PlanDirection.DEFICIT; text.contains("volumen", true) -> PlanDirection.SURPLUS; else -> PlanDirection.MAINTENANCE }) }
            WizChatQuestionId.N_ACTIVITY -> draft.withNutrition { it.copy(activity = when { text.contains("muy", true) -> EerActivity.VERY_ACTIVE; text.equals("Activo", true) -> EerActivity.ACTIVE; text.contains("algo", true) -> EerActivity.LOW_ACTIVE; else -> EerActivity.INACTIVE }) }
            WizChatQuestionId.R_START -> draft.copy(ringsAnswers = (draft.ringsAnswers ?: SetupRingsAnswers()).copy(startAction = text))
            WizChatQuestionId.R_RECENT -> draft.copy(ringsAnswers = (draft.ringsAnswers ?: SetupRingsAnswers()).copy(recentTraining = text == "Sí", recentTrainingState = when { text == "Sí" -> SetupRecentTrainingState.YES; text == "No" -> SetupRecentTrainingState.NO; else -> SetupRecentTrainingState.UNKNOWN }))
            WizChatQuestionId.R_SESSIONS -> draft.copy(ringsAnswers = (draft.ringsAnswers ?: SetupRingsAnswers()).copy(sessionsLastSevenDays = answer.numberValue?.toInt() ?: text.toIntOrNull()))
            WizChatQuestionId.R_RECENCY -> draft.copy(ringsAnswers = (draft.ringsAnswers ?: SetupRingsAnswers()).copy(lastSessionRecencyDays = recencyFor(text), recencyDays = recencyFor(text)))
            WizChatQuestionId.R_ACTIVITY -> draft.copy(ringsAnswers = (draft.ringsAnswers ?: SetupRingsAnswers()).copy(activityType = when { text.contains("cardio", true) -> InitialRecoveryActivityType.CARDIO; text.contains("mixta", true) -> InitialRecoveryActivityType.MIXED; else -> InitialRecoveryActivityType.STRENGTH }, activityTypeState = InitialRecoveryResponseState.DECLARED))
            WizChatQuestionId.R_INTENSITY -> draft.copy(ringsAnswers = (draft.ringsAnswers ?: SetupRingsAnswers()).copy(intensityLevel = intensityFor(text)))
            WizChatQuestionId.R_AXIAL -> draft.copy(ringsAnswers = (draft.ringsAnswers ?: SetupRingsAnswers()).copy(axialExposure = InitialRecoveryAxialExposure(if (text == "Sí" || text == "No") InitialRecoveryResponseState.DECLARED else InitialRecoveryResponseState.UNKNOWN, if (text == "Sí") 1 else 0, draft.ringsAnswers?.intensityLevel, draft.ringsAnswers?.lastSessionRecencyDays)))
            WizChatQuestionId.R_FEELINGS_MUSCLE -> draft.copy(ringsAnswers = (draft.ringsAnswers ?: SetupRingsAnswers()).copy(muscleFeeling = feelingFor(text)))
            WizChatQuestionId.R_FEELINGS_ENERGY -> draft.copy(ringsAnswers = (draft.ringsAnswers ?: SetupRingsAnswers()).copy(energy = feelingFor(text)))
            WizChatQuestionId.R_FEELINGS_STRUCTURE -> draft.copy(ringsAnswers = (draft.ringsAnswers ?: SetupRingsAnswers()).copy(structureFeeling = feelingFor(text)))
            WizChatQuestionId.R_DISCOMFORT -> draft.copy(ringsAnswers = (draft.ringsAnswers ?: SetupRingsAnswers()).copy(discomfortIds = values.filterNot { it == "Sin molestias" || it == "Prefiero omitirlo" }.mapNotNull { value -> DISCOMFORT_CATALOG_BY_ID[value]?.id ?: DISCOMFORT_CATALOG_BY_ID.values.firstOrNull { entry -> entry.label == value }?.id }.distinct()))
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
        _state.value = _state.value.copy(machineState = WizChatMachineState.PersistingAnswer, errors = emptyMap())
        if (persistDraft(draft)) { publishDraft(draft, true, WizChatMachineState.AwaitingAnswer); updateCandidates(draft); preparePreview(draft) }
    }
    private fun mutateDraft(change: (SetupWizardDraft) -> SetupWizardDraft) = viewModelScope.launch { commandMutex.withLock {
        if (_state.value.isCommitting) return@withLock
        val old = _state.value.draft
        val changed = change(old)
        if (changed != old) persistAndPublish(changed.copy(revision = old.revision + 1))
    } }
    private suspend fun persistDraft(draft: SetupWizardDraft): Boolean = runCatching {
        persistenceFactory(getApplication<Application>()).drafts.save(draft.draftId, json.encodeToString(draft), draft.revision.toLong(), PersonalizedPlanCatalog.REVISION)
        savedStateHandle[DRAFT_ID_KEY] = draft.draftId
    }.onFailure { _state.value = _state.value.copy(machineState = WizChatMachineState.RecoverableError, errors = mapOf("draft" to (it.message ?: "No se pudo guardar el borrador"))) }.isSuccess
    private fun publishDraft(draft: SetupWizardDraft, dirty: Boolean, machine: WizChatMachineState) { _state.value = _state.value.copy(draft = draft, dirty = dirty, isLoading = false, machineState = machine, messages = messagesFor(draft), errors = emptyMap(), previewError = null) }

    private fun preparePreview(draft: SetupWizardDraft) {
        previewJob?.cancel()
        previewJob = viewModelScope.launch {
            if (!initialized) return@launch
            if (!draft.includeTraining || draft.programRoute == SetupProgramRoute.LATER) { _state.value = _state.value.copy(programPreview = null, previewReport = null, isPreviewLoading = false); updateNutritionPreview(draft); return@launch }
            if (previewInputsIncomplete(draft)) { _state.value = _state.value.copy(programPreview = null, previewReport = null, isPreviewLoading = false); updateNutritionPreview(draft); return@launch }
            _state.value = _state.value.copy(machineState = WizChatMachineState.PreparingPreview, isPreviewLoading = true, previewError = null)
            try {
                val result = withContext(Dispatchers.Default) { materializeProgram(draft) }
                if (_state.value.draft.revision == draft.revision) { _state.value = _state.value.copy(programPreview = result.program, previewReport = result.report, isPreviewLoading = false, machineState = if (draft.wizChat.currentQuestionId == WizChatQuestionId.REVIEW) WizChatMachineState.Reviewing else WizChatMachineState.AwaitingAnswer, requiresActivationConfirmation = activationConfirmation(draft, result.program)); updateNutritionPreview(draft) }
            } catch (cancel: CancellationException) { throw cancel }
            catch (error: Throwable) { if (_state.value.draft.revision == draft.revision) _state.value = _state.value.copy(isPreviewLoading = false, machineState = WizChatMachineState.RecoverableError, previewError = error.message ?: "No se pudo preparar la vista previa") }
        }
    }
    private fun updateNutritionPreview(draft: SetupWizardDraft) { if (!draft.includeNutrition) _state.value = _state.value.copy(nutritionPlanPreview = null, nutritionErrors = emptyMap()) else prepareNutrition(draft).also { _state.value = _state.value.copy(nutritionPlanPreview = it?.plan, nutritionErrors = it?.errors.orEmpty()) } }

    private fun messagesFor(draft: SetupWizardDraft): List<WizChatMessage> = buildList {
        draft.wizChat.acceptedAnswers.forEach { answer ->
            val question = WizChatGraph.question(answer.questionId) ?: return@forEach
            add(WizChatMessage("question:${answer.questionId.name}:${answer.revision}", question.stage, false, question.prompt, question.id))
            add(WizChatMessage("answer:${answer.questionId.name}:${answer.revision}", question.stage, true, answerPresentation(answer), question.id, answer.variantId))
            add(WizChatMessage("ack:${answer.questionId.name}:${answer.revision}", question.stage, false, WizChatCopyCatalog.acknowledgement(question.stage, answer.variantId), question.id, answer.variantId))
        }
        if (!draft.wizChat.terminal) WizChatGraph.question(draft.wizChat.currentQuestionId)?.let { add(WizChatMessage("current:${it.id.name}:${draft.wizChat.revision}", it.stage, false, it.prompt, it.id)) }
    }.takeLast(80)
    private fun answerPresentation(answer: WizChatAnswerRecord) = when { answer.source == WizChatAnswerSource.OMITTED -> "Omitir por ahora"; answer.values.isNotEmpty() -> answer.values.joinToString(" · "); answer.numberValue != null -> answer.numberValue.toString().removeSuffix(".0"); !answer.textValue.isNullOrBlank() -> answer.textValue.orEmpty(); else -> "Confirmado" }

    private fun updateCandidates(draft: SetupWizardDraft) {
        val entries = if (!draft.includeTraining || draft.programRoute == SetupProgramRoute.LATER) emptyList() else SetupTrainingPlanner.candidates(SetupTrainingPlannerInput(draft.goal?.label, draft.daysPerWeek, draft.equipment.map { it.catalogId }.toSet(), draft.experience.toCatalogLevel(), draft.focus.toTrainingFocus()))
        _state.value = _state.value.copy(planCandidates = entries.map { SetupPlanCandidate(it.id, it.title, it.technicalSubtitle, it.description, it.source.name) })
    }
    private fun previewInputsIncomplete(draft: SetupWizardDraft): Boolean { if (!draft.includeTraining || draft.programRoute == SetupProgramRoute.LATER) return false; val days = draft.daysPerWeek ?: return true; if (draft.minutesPerSession == null || draft.selectedWeekdays.size != days) return true; return if (draft.trainingPath == SetupTrainingPath.FROM_SCRATCH) { val selected = draft.sessions.filter { it.weekday in draft.selectedWeekdays }; selected.size != draft.selectedWeekdays.size || selected.any { it.exercises.isEmpty() } } else draft.selectedCatalogId == null }

    private suspend fun materializeProgram(draft: SetupWizardDraft): SetupPreview {
        if (!draft.includeTraining || draft.programRoute == SetupProgramRoute.LATER) return SetupPreview(null, null)
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
                    calibration = if (draft.volumeRecommendations.isNotEmpty()) Calibration.CALIBRATED else Calibration.CONSERVATIVE,
                    volumeRecommendations = draft.volumeRecommendations,
                    priorityMuscles = draft.priorityMuscles,
                    lowerEmphasisMuscles = draft.lowerEmphasisMuscles,
                    splitId = draft.selectedSplitId,
                    splitPattern = draft.customSplitPattern,
                    splitName = draft.customSplitName,
                ),
            )
            return SetupPreview(result.program?.copy(id = draft.commitId) ?: error(result.report.limitations.joinToString(" ")), result.report)
        }
        val catalog = (catalogRepository.state.value as? ExerciseCatalogStateV2.Ready)?.catalog ?: error("El catálogo de ejercicios todavía no está disponible")
        val base = Program(id = draft.commitId, name = "Plan de ${draft.name.ifBlank { "entrenamiento" }}")
        val program = when (entry.source) {
            CatalogSource.PROTOCOL -> {
                val protocol = PROTOCOL_LIBRARY.first { it.id == entry.sourceId }
                ProgramProtocolEngine.applyProtocol(
                    program = base.copy(selectedSplitId = protocol.defaultSplit),
                    protocol = protocol,
                    metadata = CatalogCompositionMetadataProvider.fromCatalog(catalog),
                    exerciseList = catalog.toLegacyConfigurationLookup().values.toList(),
                ).copy(powerliftingProfile = draft.powerliftingProfile)
            }
            CatalogSource.TEMPLATE -> ProgramTemplateEngine.applyTemplate(base, requireNotNull(entry.template), forceReplace = true).program
            CatalogSource.NATIVE -> error("Ruta nativa no válida")
        }.copy(id = draft.commitId)
        val frequency = program.macrocycles.flatMap { it.blocks }.flatMap { it.mesocycles }.flatMap { it.weeks }.flatMap { it.sessions }.mapNotNull { it.dayOfWeek }.distinct().size
        if (draft.daysPerWeek != null && frequency != draft.daysPerWeek) error("La receta fija produce $frequency días, no ${draft.daysPerWeek}")
        return SetupPreview(program, null)
    }

    private fun prepareNutrition(draft: SetupWizardDraft): com.example.kpkn.domain.nutrition.NutritionPlanPreparationResult? {
        if (!draft.includeNutrition) return null
        val n = draft.nutritionDraft ?: return null
        val weight = draft.weightKg ?: parseLocalizedNumber(n.weightText)?.let { kilogramsFromInput(it, n.weightUnit) }
        val target = when (n.goalMetric) { GoalMetric.WEIGHT -> parseLocalizedNumber(n.targetWeightText.ifBlank { n.targetValueText })?.let { kilogramsFromInput(it, n.weightUnit) }; GoalMetric.BODY_FAT -> parseLocalizedNumber(n.targetBodyFatText.ifBlank { n.targetValueText }); GoalMetric.MUSCLE_MASS -> parseLocalizedNumber(n.targetMuscleText.ifBlank { n.targetValueText }) }
        return NutritionPlanPreparation.prepare(NutritionPlanPreparationInput(draft.nutritionPlanId ?: draft.commitId, NutritionRepository.getInstance().nutritionPlans.value.firstOrNull { it.id == (draft.nutritionPlanId ?: draft.commitId) }, draft.ageYears ?: parseLocalizedNumber(n.ageText)?.toInt(), draft.heightCm ?: parseLocalizedNumber(n.heightText), weight, n.equationSex, n.activity, n.eligibilityUnknown, n.pregnant, n.lactating, n.medicalRestriction, n.direction, n.goalMetric, target, parseLocalizedNumber(n.manualCalorieTargetText)?.toInt(), parseLocalizedNumber(n.manualProteinText), parseLocalizedNumber(n.manualCarbsText), parseLocalizedNumber(n.manualFatText), n.higherProteinInDeficit, parseLocalizedNumber(n.bodyFatText), parseLocalizedNumber(n.muscleText)))
    }
    private fun reviewErrors(): Map<String, String> = buildMap {
        val s = _state.value; val d = s.draft
        if (d.wizChat.currentQuestionId != WizChatQuestionId.REVIEW && !d.wizChat.terminal) put("flow", "Completa la revisión antes de activar")
        if (d.includeTraining && d.programRoute != SetupProgramRoute.LATER && (s.programPreview == null || s.previewError != null)) put("program", "Prepara una vista previa ejecutable")
        if (d.includeNutrition && (s.nutritionPlanPreview == null || s.nutritionErrors.isNotEmpty())) put("nutrition", s.nutritionErrors.values.firstOrNull() ?: "Completa la nutrición")
        if (activationConfirmation(d, s.programPreview) && !d.confirmActivation) put("activation", "Confirma la activación del plan")
    }
    private fun activationConfirmation(draft: SetupWizardDraft, preview: Program?): Boolean = (draft.activateProgram && preview != null && ProgramRepository.getInstance().activeProgramState.value?.programId?.let { it != preview.id } == true) || (draft.includeNutrition && draft.activateNutrition && NutritionRepository.getInstance().activeNutritionPlanId.value?.let { it != (draft.nutritionPlanId ?: draft.commitId) } == true)

    private fun buildSettingsPatch(base: Settings, draft: SetupWizardDraft, program: Program?, nutrition: NutritionPlan?, rings: SetupRingsMapping): SetupSettingsPatch {
        val accepted = draft.wizChat.acceptedAnswers.map { it.questionId }.toSet()
        val age = draft.ageYears ?: parseLocalizedNumber(draft.nutritionDraft?.ageText.orEmpty())?.toInt()
        val vitals = base.userVitals.copy(age = age ?: base.userVitals.age, height = draft.heightCm ?: base.userVitals.height, weight = draft.weightKg ?: base.userVitals.weight)
        val evidence = when (rings.completion) { RingsCompletion.VALID -> SetupPatchField.Set(rings.evidence); RingsCompletion.OMITTED -> SetupPatchField.Clear; else -> SetupPatchField.Unchanged }
        return SetupSettingsPatch(
            username = if (WizChatQuestionId.P_NAME in accepted && draft.name.isNotBlank()) SetupPatchField.Set(draft.name) else SetupPatchField.Unchanged,
            age = if (WizChatQuestionId.P_AGE in accepted && age != null) SetupPatchField.Set(age) else SetupPatchField.Unchanged,
            userVitals = if (accepted.any { it in setOf(WizChatQuestionId.P_AGE, WizChatQuestionId.P_HEIGHT, WizChatQuestionId.P_WEIGHT) }) SetupPatchField.Set(vitals) else SetupPatchField.Unchanged,
            dailyCalorieGoal = nutrition?.let { SetupPatchField.Set(it.calorieTarget) } ?: SetupPatchField.Unchanged,
            dailyProteinGoal = nutrition?.let { SetupPatchField.Set(it.proteinGoal) } ?: SetupPatchField.Unchanged,
            dailyCarbGoal = nutrition?.let { SetupPatchField.Set(it.carbGoal) } ?: SetupPatchField.Unchanged,
            dailyFatGoal = nutrition?.let { SetupPatchField.Set(it.fatGoal) } ?: SetupPatchField.Unchanged,
            onboardingCompleted = if (_state.value.mode == SetupWizardMode.FULL || _state.value.mode == SetupWizardMode.RESUME) SetupPatchField.Set(true) else SetupPatchField.Unchanged,
            onboardingNameDone = if (WizChatQuestionId.P_NAME in accepted && draft.name.isNotBlank()) SetupPatchField.Set(true) else SetupPatchField.Unchanged,
            onboardingProgramDone = if (!draft.includeTraining || draft.programRoute == SetupProgramRoute.LATER || program != null) SetupPatchField.Set(true) else SetupPatchField.Unchanged,
            onboardingNutritionDone = if (!draft.includeNutrition || nutrition != null) SetupPatchField.Set(true) else SetupPatchField.Unchanged,
            nutritionTrackingChoice = when { nutrition != null -> SetupPatchField.Set(NutritionTrackingChoice.ENABLED); !draft.includeNutrition && WizChatQuestionId.N_START in accepted -> SetupPatchField.Set(NutritionTrackingChoice.SKIPPED); else -> SetupPatchField.Unchanged },
            initialRecoveryEvidence = evidence,
            volumeCalibrationProfile = draft.volumeCalibrationProfile?.let { SetupPatchField.Set(it) } ?: SetupPatchField.Unchanged,
        )
    }

    private fun ringsMapping(draft: SetupWizardDraft): SetupRingsMapping {
        val a = draft.ringsAnswers ?: return SetupRingsMapping(RingsCompletion.UNKNOWN)
        return SetupRingsMapper.map(SetupRingsInput(a.startAction, a.recentTraining, a.recentTrainingState == SetupRecentTrainingState.UNKNOWN, a.sessionsLastSevenDays, a.lastSessionRecencyDays ?: a.recencyDays, a.activityType, a.intensityLevel, a.muscleFeeling, a.energy, a.structureFeeling, a.axialExposure.state, a.axialExposure.sessions, a.axialExposure.intensity, a.muscleScope, a.recentMuscles.toList(), a.discomfortIds, a.capturedAtMs), System.currentTimeMillis())
    }
    private fun initialWellbeing(draft: SetupWizardDraft, mapping: SetupRingsMapping): DailyWellbeingLog? {
        val a = draft.ringsAnswers ?: return null
        val fields = buildSet { if (draft.manualMuscleOverrides.isNotEmpty()) add("muscle_batteries"); if (draft.manualEnergyOverride != null) add("energy"); if (draft.manualStructureOverride != null) add("structure"); if (a.discomfortIds.isNotEmpty()) add("discomforts") }
        if (fields.isEmpty()) return null
        val anchor = mapping.evidence?.capturedAtMs ?: System.currentTimeMillis()
        fun battery(value: Int?) = value?.let { (100 - (it.coerceIn(1, 5) - 1) * 25).coerceIn(0, 100) }
        val manual = draft.manualMuscleOverrides.filterKeys { mapping.evidence?.perMuscleScores?.containsKey(it) == true }.mapValues { (key, value) -> ManualMuscleBatteryOverride(battery(value) ?: 0, anchor, null, mapping.evidence?.perMuscleScores?.get(key) ?: 0) }
        return DailyWellbeingLog("${draft.commitId}-onboarding-wellbeing", LocalDate.now().toString(), manualMuscleBatteries = manual.mapValues { it.value.battery }, manualBatteryAnchorMs = anchor, manualMuscleOverridesV2 = manual, manualNeuralBattery = battery(draft.manualEnergyOverride), manualSpinalBattery = battery(draft.manualStructureOverride), preWorkoutDiscomforts = a.discomfortIds.distinct(), source = WellbeingSource.ONBOARDING_INITIAL, capturedFields = fields)
    }
    private fun buildVolumeProfile(draft: SetupWizardDraft): VolumeCalibrationProfile? {
        val a = draft.volumeAnswers; val style = a.style ?: return null; val t = a.technique ?: return null; val c = a.consistency ?: return null; val s = a.strength ?: return null; val m = a.mobility ?: return null; val output = VolumeCalibrationEngine.calculate(style, t, c, s, m)
        return VolumeCalibrationProfile(style, output.score, VolumeCalibrationResponses(t, c, s, m, a.responseState.takeIf { it != CalibrationResponseState.UNKNOWN } ?: CalibrationResponseState.DECLARED), output.recommendations, System.currentTimeMillis(), VolumeCalibrationEngine.REVISION)
    }

    private fun newDraft(mode: SetupWizardMode, nutritionMode: String, nutritionPlanId: String?, id: String): SetupWizardDraft {
        val settings = ProgramRepository.getInstance().settings.value
        val training = mode != SetupWizardMode.NUTRITION_ONLY && mode != SetupWizardMode.RINGS_ONLY
        val nutrition = mode != SetupWizardMode.TRAINING_ONLY && mode != SetupWizardMode.RINGS_ONLY
        val progress = WizChatProgress(scriptVersion = WizChatCopyCatalog.SCRIPT_VERSION, draftScope = scopeFor(mode).name.lowercase(), currentQuestionId = WizChatGraph.firstFor(WizChatGraphContext(training, nutrition)))
        return SetupWizardDraft(draftId = id, commitId = UUID.randomUUID().toString(), draftScope = scopeFor(mode).name.lowercase(), name = settings.username.takeIf { it != "Usuario" }.orEmpty(), moduleChoice = if (nutrition) SetupModuleChoice.TRAINING_AND_NUTRITION else SetupModuleChoice.TRAINING, ageYears = settings.userVitals.age ?: settings.age, includeTraining = training, includeNutrition = nutrition, programRoute = if (training) SetupProgramRoute.CUSTOMIZABLE else SetupProgramRoute.LATER, trainingPath = if (training) SetupTrainingPath.PERSONALIZE else null, nutritionMode = nutritionMode, nutritionPlanId = nutritionPlanId, nutritionDraft = if (nutrition) NutritionWizardDraft(mode = nutritionMode, planId = nutritionPlanId) else null, catalogRevision = PersonalizedPlanCatalog.REVISION, wizChat = progress)
    }
    private fun normalizeProgress(progress: WizChatProgress, scope: String, mode: SetupWizardMode) = progress.copy(scriptVersion = WizChatCopyCatalog.SCRIPT_VERSION, draftScope = scope, currentQuestionId = if (WizChatGraph.question(progress.currentQuestionId) != null) progress.currentQuestionId else WizChatQuestionId.P_NAME)
    private fun scopeFor(mode: SetupWizardMode): SetupDraftScope = when (mode) { SetupWizardMode.TRAINING_ONLY -> SetupDraftScope.TRAINING_ONLY; SetupWizardMode.NUTRITION_ONLY -> SetupDraftScope.NUTRITION_ONLY; SetupWizardMode.RINGS_ONLY -> SetupDraftScope.RINGS_ONLY; else -> SetupDraftScope.FULL }
    private fun SetupFocus.toTrainingFocus() = TrainingFocus.valueOf(name)
    private fun SetupExperience?.toCatalogLevel() = when (this) { SetupExperience.ADVANCED -> CatalogLevel.ADVANCED; SetupExperience.INTERMEDIATE -> CatalogLevel.INTERMEDIATE; else -> CatalogLevel.BEGINNER }
    private val SetupEquipment.catalogId: String get() = when (this) { SetupEquipment.NONE -> "bodyweight"; SetupEquipment.BANDS -> "band"; SetupEquipment.DUMBBELLS -> "dumbbells"; SetupEquipment.MACHINE -> "machine"; SetupEquipment.CABLE -> "cable"; SetupEquipment.BARBELL -> "barbell"; SetupEquipment.PULL_UP -> "pull_up_bar"; SetupEquipment.GYM -> "general_gym"; SetupEquipment.SUPPORT -> "support"; SetupEquipment.BALL -> "ball"; SetupEquipment.SMITH -> "smith_machine" }
    private fun SetupWizardDraft.withNutrition(update: (NutritionWizardDraft) -> NutritionWizardDraft): SetupWizardDraft { val value = nutritionDraft ?: NutritionWizardDraft(mode = nutritionMode, planId = nutritionPlanId); return copy(nutritionDraft = update(value)) }
    private fun answerIndex(value: String?) = when { value?.contains("Inicial", true) == true || value?.contains("Aprendiendo", true) == true || value?.contains("Irregular", true) == true || value?.contains("Limitada", true) == true -> 1; value?.contains("Intermedia", true) == true || value?.contains("estable", true) == true || value?.contains("constante", true) == true || value?.contains("Suficiente", true) == true -> 2; else -> 3 }
    private fun feelingFor(value: String) = when { value.contains("Descans", true) || value.contains("energía", true) || value.equals("Con energía", true) -> 1; value.equals("Bien", true) -> 2; value.contains("Intermedia", true) -> 3; value.contains("Algo", true) || value.contains("Cargada", true) -> 4; else -> 5 }
    private fun intensityFor(value: String) = when { value.contains("fácil", true) -> InitialRecoveryIntensity.EASY; value.contains("moderada", true) -> InitialRecoveryIntensity.MODERATE; value.contains("exigente", true) && !value.contains("muy", true) -> InitialRecoveryIntensity.HARD; else -> InitialRecoveryIntensity.VERY_HARD }
    private fun recencyFor(value: String) = when { value.equals("Hoy", true) -> 0; value.equals("Ayer", true) -> 1; else -> Regex("\\d+").find(value)?.value?.toIntOrNull()?.coerceIn(0, 6) ?: 6 }
    private fun equipmentFor(value: String): SetupEquipment? = when { value.contains("Sin material", true) -> SetupEquipment.NONE; value.contains("máquina", true) -> SetupEquipment.MACHINE; value.contains("casa", true) -> SetupEquipment.DUMBBELLS; value.contains("gimnasio", true) -> SetupEquipment.GYM; else -> null }
    private fun com.example.kpkn.data.models.GoalMetric.toBodyMetric() = when (this) { GoalMetric.WEIGHT -> BodyMetric.WEIGHT; GoalMetric.BODY_FAT -> BodyMetric.BODY_FAT_PERCENT; GoalMetric.MUSCLE_MASS -> BodyMetric.MUSCLE_MASS_PERCENT }

    companion object { private const val DRAFT_ID_KEY = "setup_wizard_draft_id"; private val weekdayLabels = listOf("Lunes", "Martes", "Miércoles", "Jueves", "Viernes", "Sábado", "Domingo") }
}

private fun List<SetupSessionDraft>.ensureSession(weekday: Int): List<SetupSessionDraft> = if (any { it.weekday == weekday }) this else this + SetupSessionDraft(weekday, "Sesión del día $weekday")
