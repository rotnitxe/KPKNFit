package com.example.kpkn.screens.onboarding

import android.app.Application
import android.content.Context
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.SavedStateHandle
import androidx.lifecycle.viewModelScope
import com.example.kpkn.data.models.BodyGoal
import com.example.kpkn.data.models.CalculationOrigin
import com.example.kpkn.data.models.Exercise
import com.example.kpkn.data.models.ExerciseMuscleInfo
import com.example.kpkn.data.models.ExerciseSet
import com.example.kpkn.data.models.InitialRecoveryActivityType
import com.example.kpkn.data.models.InitialRecoveryEvidenceFactory
import com.example.kpkn.data.models.InitialRecoveryIntensity
import com.example.kpkn.data.models.InitialRecoverySensations
import com.example.kpkn.data.models.Macrocycle
import com.example.kpkn.data.models.Mesocycle
import com.example.kpkn.data.models.MesocycleGoal
import com.example.kpkn.data.models.Program
import com.example.kpkn.data.models.ProgramStructure
import com.example.kpkn.data.models.ProgramWeek
import com.example.kpkn.data.models.Block
import com.example.kpkn.data.models.Settings
import com.example.kpkn.data.models.Session
import com.example.kpkn.data.models.SimpleProgramKind
import com.example.kpkn.data.models.UserVitals
import com.example.kpkn.data.exercises.catalogv2.ApprovedAssetExerciseCatalogRepositoryV2
import com.example.kpkn.data.exercises.catalogv2.CatalogCompositionMetadataProvider
import com.example.kpkn.data.exercises.catalogv2.toLegacyConfigurationLookup
import com.example.kpkn.data.protocols.PROTOCOL_LIBRARY
import com.example.kpkn.data.programs.resolveProgramTemplate
import com.example.kpkn.domain.training.ProgramProtocolEngine
import com.example.kpkn.domain.training.ProgramTemplateEngine
import com.example.kpkn.data.onboarding.SetupCommitRequest
import com.example.kpkn.data.onboarding.persistenceFactory
import com.example.kpkn.data.programs.CatalogLevel
import com.example.kpkn.data.programs.PersonalizedPlanCatalog
import com.example.kpkn.data.programs.TrainingFocus
import com.example.kpkn.data.repository.NutritionRepository
import com.example.kpkn.data.repository.ProgramRepository
import com.example.kpkn.domain.nutrition.kilogramsFromInput
import com.example.kpkn.domain.nutrition.parseLocalizedNumber
import com.example.kpkn.domain.training.Calibration
import com.example.kpkn.domain.training.PersonalizerInput
import com.example.kpkn.domain.training.SimpleCyclePersonalizer
import com.example.kpkn.screens.nutrition.NutritionWizardDraft
import com.example.kpkn.screens.nutrition.NutritionWizardStep
import com.example.kpkn.screens.nutrition.NutritionWizardViewModel
import java.time.LocalDate
import java.util.UUID
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.withContext
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.collectLatest
import kotlinx.coroutines.launch
import kotlinx.serialization.decodeFromString
import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.Json

class SetupWizardViewModel(
    application: Application,
    private val savedStateHandle: SavedStateHandle,
) : AndroidViewModel(application) {
    private val json = Json { ignoreUnknownKeys = true; encodeDefaults = true; coerceInputValues = true }
    private val savedKey = "setup_wizard_state"
    private val modeKey = "setup_wizard_mode"
    private var saveJob: Job? = null
    private var previewJob: Job? = null
    private var initialized = false
    private var syncingNutritionAge = false
    private val catalogRepository = ApprovedAssetExerciseCatalogRepositoryV2(getApplication<Application>().applicationContext)
    private var catalogLoaded = false

    val nutritionEditor = NutritionWizardViewModel(savedStateHandle)
    private val initialDraft = newDraft(SetupWizardMode.FULL, "create", null)
    private val _state = MutableStateFlow(SetupWizardState(draft = initialDraft, isLoading = true))
    val state: StateFlow<SetupWizardState> = _state.asStateFlow()

    init {
        viewModelScope.launch {
            nutritionEditor.uiState.collectLatest { nutritionState ->
                if (!initialized) return@collectLatest
                val current = _state.value.draft
                if (nutritionState.draft != current.nutritionDraft || nutritionState.stepIndex != current.nutritionStepIndex) {
                    updateDraft(current.copy(nutritionDraft = nutritionState.draft, nutritionStepIndex = nutritionState.stepIndex), dirty = current != initialDraft)
                }
            }
        }
    }

    fun initialize(mode: SetupWizardMode, nutritionMode: String = "create", nutritionPlanId: String? = null) {
        if (initialized && _state.value.mode == mode) return
        initialized = false
        _state.value = _state.value.copy(mode = mode, isLoading = true)
        val previousMode = savedStateHandle.get<String>(modeKey)
        savedStateHandle[modeKey] = mode.name
        val savedDraft = loadSavedDraft(mode, previousMode)
        viewModelScope.launch {
            try {
                ProgramRepository.getInstance().isReady.first { it }
                val restored = savedDraft ?: loadRoomDraftNow(mode)
                val draft = restored?.copy(
                    nutritionMode = nutritionMode,
                    nutritionPlanId = nutritionPlanId ?: restored.nutritionPlanId,
                ) ?: newDraft(mode, nutritionMode, nutritionPlanId)
                _state.value = _state.value.copy(draft = draft, mode = mode, dirty = restored != null, isLoading = false)
                nutritionEditor.initialize(draft.nutritionMode, draft.nutritionPlanId)
                draft.nutritionDraft?.let(nutritionEditor::restoreDraft)
                syncNutritionAge(draft.ageYears ?: calculateAge(draft.birthDateIso))
                nutritionEditor.setStep(NutritionWizardStep.entries.getOrElse(draft.nutritionStepIndex) { NutritionWizardStep.GOAL })
                initialized = true
                if (!catalogLoaded) {
                    catalogRepository.load()
                    catalogLoaded = true
                }
                refreshPreview()
            } catch (cancel: CancellationException) { throw cancel }
            catch (error: Throwable) {
                _state.value = _state.value.copy(isLoading = false, errors = mapOf("initialize" to (error.message ?: "No se pudo cargar el asistente")))
            }
        }
    }

    fun update(change: (SetupWizardDraft) -> SetupWizardDraft) {
        if (_state.value.isCommitting) return
        val old = _state.value.draft
        var next = change(old)
        if (next.ringsAnswers != null && next.ringsAnswers.capturedAtMs == null) {
            next = next.copy(ringsAnswers = next.ringsAnswers.copy(capturedAtMs = System.currentTimeMillis()))
        }
        updateDraft(next, dirty = true)
    }

    fun setName(name: String) = update { it.copy(name = name) }
    fun setAge(ageYears: Int?) = update {
        val resolved = ageYears?.takeIf { age -> age in 13..100 }
        syncNutritionAge(resolved)
        it.copy(ageYears = resolved, birthDateIso = null)
    }
    fun setBirthDate(isoDate: String?) = update {
        val birthDate = isoDate?.takeIf(String::isNotBlank)
        val resolved = calculateAge(birthDate)
        syncNutritionAge(resolved)
        it.copy(birthDateIso = birthDate, ageYears = null)
    }
    fun setChapter(chapter: SetupWizardChapter) = updateDraft(_state.value.draft.copy(chapter = chapter), dirty = _state.value.dirty)

    fun next(): Boolean {
        if (_state.value.draft.chapter == SetupWizardChapter.NUTRITION && _state.value.draft.includeNutrition) {
            val editorBefore = nutritionEditor.uiState.value
            if (editorBefore.step != NutritionWizardStep.REVIEW) {
                nutritionEditor.next()
                val editor = nutritionEditor.uiState.value
                update { it.copy(nutritionDraft = editor.draft, nutritionStepIndex = editor.step.ordinal) }
                return true
            }
        }
        val current = _state.value
        val errors = SetupWizardValidation.validate(current.draft, current.draft.chapter)
        if (errors.isNotEmpty()) {
            _state.value = current.copy(errors = errors)
            return false
        }
        val chapters = chapters(current.mode)
        val next = chapters.getOrNull(chapters.indexOf(current.draft.chapter) + 1) ?: return true
        setChapter(next)
        return true
    }

    fun back(): Boolean {
        if (_state.value.draft.chapter == SetupWizardChapter.NUTRITION && _state.value.draft.includeNutrition) {
            val editorBefore = nutritionEditor.uiState.value
            if (editorBefore.step != NutritionWizardStep.GOAL) {
                nutritionEditor.back()
                val editor = nutritionEditor.uiState.value
                update { it.copy(nutritionDraft = editor.draft, nutritionStepIndex = editor.step.ordinal) }
                return true
            }
        }
        val chapters = chapters(_state.value.mode)
        val previous = chapters.getOrNull(chapters.indexOf(_state.value.draft.chapter) - 1) ?: return false
        setChapter(previous)
        return true
    }

    fun canContinue(): Boolean {
        val current = _state.value
        if (current.isLoading || current.isPreviewLoading || current.isCommitting) return false
        val draft = current.draft
        if (draft.chapter == SetupWizardChapter.NUTRITION) {
            if (!draft.includeNutrition) return true
            val editor = nutritionEditor.uiState.value
            return when (editor.step) {
                NutritionWizardStep.GOAL -> editor.draft.direction != null
                NutritionWizardStep.DATA -> editor.canContinue && editor.recommendation != null &&
                    editor.draft.ageText.isNotBlank() && editor.draft.heightText.isNotBlank() &&
                    editor.draft.weightText.isNotBlank() &&
                    (editor.draft.equationSex != null || editor.draft.direction == com.example.kpkn.data.models.PlanDirection.PROFESSIONAL)
                NutritionWizardStep.GOALS -> editor.canContinue
                NutritionWizardStep.REVIEW -> nutritionReviewIsValid(editor)
            }
        }
        if (draft.chapter == SetupWizardChapter.WEEK) {
            if (SetupWizardValidation.validate(draft, draft.chapter).isNotEmpty()) return false
            if (draft.includeTraining) return current.programPreview != null && current.previewError == null
        }
        return SetupWizardValidation.validate(draft, draft.chapter).isEmpty() &&
            (draft.chapter != SetupWizardChapter.REVIEW || reviewErrors().isEmpty())
    }

    private fun nutritionReviewIsValid(editor: com.example.kpkn.screens.nutrition.NutritionWizardUiState): Boolean =
        editor.recommendation != null && editor.errors.isEmpty() && !editor.isContradictoryDeficit

    fun clear() {
        saveJob?.cancel()
        previewJob?.cancel()
        val mode = _state.value.mode
        viewModelScope.launch { runCatching { persistenceFactory(getApplication<Application>()).drafts.discard(storageKey(mode)) } }
        savedStateHandle[savedKey] = null
        _state.value = SetupWizardState(draft = newDraft(mode, "create", null), mode = mode)
    }

    suspend fun commit(context: Context): String? {
        val current = _state.value
        if (current.isCommitting) return current.receiptId
        _state.value = current.copy(isCommitting = true, errors = emptyMap())
        return try {
            val errors = reviewErrors()
            if (errors.isNotEmpty()) {
                _state.value = _state.value.copy(errors = errors)
                return null
            }
            awaitLastSave()
            if (_state.value.errors.containsKey("draft")) return null
            val draft = _state.value.draft
            val preview = _state.value.programPreview
            val program = if (draft.includeTraining) preview ?: throw IllegalStateException("La vista previa del programa no es ejecutable") else null
            val nutritionPlan = if (draft.includeNutrition) prepareNutritionPlan(draft) ?: throw IllegalStateException("Completa el plan de nutrición") else null
            val repository = ProgramRepository.getInstance()
            val base = repository.settings.value
            val settings = base.copy(
                username = draft.name.trim().ifBlank { base.username },
                age = draft.ageYears ?: calculateAge(draft.birthDateIso) ?: base.age,
                userVitals = base.userVitals.copy(
                    age = draft.ageYears ?: calculateAge(draft.birthDateIso) ?: base.userVitals.age,
                    height = if (draft.includeNutrition) nutritionEditor.uiState.value.draft.heightText
                        .let(::parseLocalizedNumber)?.takeIf { it.isFinite() && it in 100.0..250.0 } ?: base.userVitals.height else base.userVitals.height,
                    weight = if (draft.includeNutrition) nutritionEditor.uiState.value.draft.weightText
                        .let(::parseLocalizedNumber)?.let { kilogramsFromInput(it, nutritionEditor.uiState.value.draft.weightUnit) }
                        ?.takeIf { it.isFinite() && it > 0.0 } ?: base.userVitals.weight else base.userVitals.weight,
                ),
                dailyCalorieGoal = nutritionPlan?.calorieTarget?.takeIf { it > 0 } ?: base.dailyCalorieGoal,
                dailyProteinGoal = nutritionPlan?.proteinGoal?.takeIf { it > 0 } ?: base.dailyProteinGoal,
                dailyCarbGoal = nutritionPlan?.carbGoal?.takeIf { it > 0 } ?: base.dailyCarbGoal,
                dailyFatGoal = nutritionPlan?.fatGoal?.takeIf { it > 0 } ?: base.dailyFatGoal,
                onboardingCompleted = base.onboardingCompleted || _state.value.mode == SetupWizardMode.FULL || _state.value.mode == SetupWizardMode.RESUME,
                onboardingNameDone = if (draft.name.isNotBlank()) true else base.onboardingNameDone,
                onboardingProgramDone = if (draft.includeTraining) program != null else base.onboardingProgramDone,
                onboardingNutritionDone = if (draft.includeNutrition) nutritionPlan != null else base.onboardingNutritionDone,
                initialRecoveryEvidence = recoveryEvidence(draft) ?: base.initialRecoveryEvidence,
            )
            val result = persistenceFactory(context).commits.commit(
                SetupCommitRequest(
                    commitId = draft.commitId,
                    draftId = storageKey(_state.value.mode),
                    settings = settings,
                    program = program,
                    nutritionPlan = nutritionPlan,
                    activateProgram = draft.includeTraining && draft.activateProgram,
                    activateNutrition = draft.includeNutrition && draft.activateNutrition,
                    derivedBodyGoals = nutritionPlan?.typedBodyGoal?.let { typed ->
                        typed.targetValueSi?.let { target ->
                            listOf(BodyGoal("${draft.commitId}-body-goal", typed.metric.toBodyMetric(), target, typed.unitSi, typed.origin, nutritionPlan.id, System.currentTimeMillis(), System.currentTimeMillis()))
                        }
                    } ?: emptyList(),
                ),
            )
            savedStateHandle[savedKey] = null
            _state.value = _state.value.copy(dirty = false, receiptId = result.commitId)
            result.commitId
        } catch (cancel: CancellationException) {
            throw cancel
        } catch (error: Throwable) {
            _state.value = _state.value.copy(errors = mapOf("commit" to (error.message ?: "No se pudo guardar la configuración")))
            null
        } finally {
            _state.value = _state.value.copy(isCommitting = false)
        }
    }

    suspend fun commit(): String? = commit(getApplication())

    fun addExercise(weekday: Int, info: ExerciseMuscleInfo) {
        val commitId = _state.value.draft.commitId
        val exerciseId = "$commitId-exercise-$weekday-${info.id}"
        val exercise = Exercise(
            id = exerciseId,
            name = info.name,
            exerciseDbId = info.id,
            exerciseId = info.id,
            canonicalExerciseId = info.id,
            catalogConfigurationId = info.catalogConfigurationId,
            catalogDefinitionId = info.catalogDefinitionId,
            catalogRevision = info.catalogRevision,
            performanceProfileId = info.performanceProfileId,
            occurrenceId = exerciseId,
            effectiveMuscles = info.involvedMuscles,
            sets = (1..3).map { index -> ExerciseSet("$exerciseId-set-$index", targetReps = 10) },
        )
        update { draft ->
            val sessions = draft.sessions.ensureSession(weekday).map { session ->
                if (session.weekday == weekday && session.exercises.none { it.exercise.exerciseDbId == info.id }) session.copy(exercises = session.exercises + SetupExerciseDraft(exercise.id, exercise, info)) else session
            }
            draft.copy(sessions = sessions)
        }
    }

    fun removeExercise(weekday: Int, exerciseId: String) = update { draft ->
        draft.copy(sessions = draft.sessions.map { session -> if (session.weekday == weekday) session.copy(exercises = session.exercises.filterNot { it.id == exerciseId || it.exercise.exerciseDbId == exerciseId }) else session })
    }

    fun moveExercise(weekday: Int, exerciseId: String, delta: Int) = update { draft ->
        draft.copy(sessions = draft.sessions.map { session ->
            if (session.weekday != weekday) session else {
                val from = session.exercises.indexOfFirst { it.id == exerciseId || it.exercise.exerciseDbId == exerciseId }
                if (from < 0) session else session.copy(exercises = session.exercises.toMutableList().also { list ->
                    val to = (from + delta).coerceIn(0, list.lastIndex)
                    val item = list.removeAt(from)
                    list.add(to, item)
                })
            }
        })
    }

    fun changeSets(weekday: Int, exerciseId: String, count: Int) = updateExercises(weekday, exerciseId) { exercise -> exercise.copy(sets = (1..count.coerceIn(1, 30)).map { index -> exercise.sets.getOrNull(index - 1)?.copy(id = "${exercise.id}-set-$index") ?: ExerciseSet("${exercise.id}-set-$index", targetReps = 10) }) }
    fun changeReps(weekday: Int, exerciseId: String, reps: String) = updateExercises(weekday, exerciseId) { exercise -> exercise.copy(sets = exercise.sets.map { it.copy(targetReps = reps.toIntOrNull()) }) }

    fun chapters(mode: SetupWizardMode): List<SetupWizardChapter> = when (mode) {
        SetupWizardMode.TRAINING_ONLY -> listOf(SetupWizardChapter.PROFILE, SetupWizardChapter.TRAINING, SetupWizardChapter.WEEK, SetupWizardChapter.RINGS, SetupWizardChapter.REVIEW)
        SetupWizardMode.NUTRITION_ONLY -> listOf(SetupWizardChapter.PROFILE, SetupWizardChapter.NUTRITION, SetupWizardChapter.REVIEW)
        SetupWizardMode.RINGS_ONLY -> listOf(SetupWizardChapter.PROFILE, SetupWizardChapter.RINGS, SetupWizardChapter.REVIEW)
        SetupWizardMode.RESUME, SetupWizardMode.FULL -> SetupWizardChapter.entries.toList()
    }

    private fun updateExercises(weekday: Int, exerciseId: String, change: (Exercise) -> Exercise) = update { draft ->
        draft.copy(sessions = draft.sessions.map { session -> if (session.weekday == weekday) session.copy(exercises = session.exercises.map { item -> if (item.id == exerciseId || item.exercise.exerciseDbId == exerciseId) item.copy(exercise = change(item.exercise)) else item }) else session })
    }

    private fun updateDraft(draft: SetupWizardDraft, dirty: Boolean) {
        val next = draft.copy(revision = maxOf(draft.revision, _state.value.draft.revision + if (draft == _state.value.draft) 0 else 1))
        _state.value = _state.value.copy(
            draft = next,
            dirty = dirty,
            errors = emptyMap(),
            previewError = null,
            programPreview = null,
            previewReport = null,
            requiresActivationConfirmation = false,
        )
        savedStateHandle[savedKey] = json.encodeToString(next)
        scheduleSave(next)
        refreshPreview()
    }

    private fun scheduleSave(draft: SetupWizardDraft) {
        saveJob?.cancel()
        saveJob = viewModelScope.launch {
            delay(250)
            runCatching { persistenceFactory(getApplication<Application>()).drafts.save(storageKey(_state.value.mode), json.encodeToString(draft), draft.revision.toLong(), PersonalizedPlanCatalog.REVISION) }
                .onFailure { _state.value = _state.value.copy(errors = mapOf("draft" to (it.message ?: "No se pudo guardar el borrador"))) }
        }
    }

    private suspend fun awaitLastSave() { saveJob?.join() }

    private fun refreshPreview() {
        previewJob?.cancel()
        val revision = _state.value.draft.revision
        previewJob = viewModelScope.launch {
            val draft = _state.value.draft
            if (!draft.includeTraining) {
                _state.value = _state.value.copy(programPreview = null, previewReport = null, isPreviewLoading = false)
                return@launch
            }
            if (previewInputsIncomplete(draft)) {
                _state.value = _state.value.copy(programPreview = null, previewReport = null, isPreviewLoading = false, previewError = null)
                return@launch
            }
            _state.value = _state.value.copy(isPreviewLoading = true, previewError = null)
            try {
                val result = withContext(Dispatchers.Default) { materializeProgram(getApplication(), draft) }
                if (_state.value.draft.revision == revision) {
                    _state.value = _state.value.copy(
                        programPreview = result.program,
                        previewReport = result.report,
                        isPreviewLoading = false,
                        requiresActivationConfirmation = activationConfirmation(_state.value.draft, result.program),
                    )
                }
            } catch (cancel: CancellationException) { throw cancel }
            catch (error: Throwable) {
                if (_state.value.draft.revision == revision) _state.value = _state.value.copy(isPreviewLoading = false, previewError = error.message ?: "No se pudo preparar la preview")
            }
        }
    }

    fun requiresActivationConfirmation(): Boolean = activationConfirmation(_state.value.draft, _state.value.programPreview)

    private fun previewInputsIncomplete(draft: SetupWizardDraft): Boolean {
        val days = draft.daysPerWeek
        if (days == null || draft.minutesPerSession == null || draft.selectedWeekdays.size != days || draft.selectedWeekdays.any { it !in 1..7 }) return true
        return when (draft.trainingPath) {
            SetupTrainingPath.PERSONALIZE -> draft.selectedCatalogId == null
            SetupTrainingPath.FROM_SCRATCH -> {
                val selected = draft.sessions.filter { it.weekday in draft.selectedWeekdays }
                selected.size != draft.selectedWeekdays.size || selected.any { it.exercises.isEmpty() }
            }
            null -> true
        }
    }

    private fun activationConfirmation(draft: SetupWizardDraft, preview: Program?): Boolean {
        val activeProgramId = ProgramRepository.getInstance().activeProgramState.value?.programId
        val replacesProgram = draft.includeTraining && draft.activateProgram && preview?.id != null &&
            activeProgramId != null && activeProgramId != preview.id
        val activeNutritionId = NutritionRepository.getInstance().activeNutritionPlanId.value
        val nutritionPlanId = draft.nutritionPlanId ?: draft.commitId
        val replacesNutrition = draft.includeNutrition && draft.activateNutrition && activeNutritionId != null &&
            activeNutritionId != nutritionPlanId
        return replacesProgram || replacesNutrition
    }

    private fun reviewErrors(): Map<String, String> = buildMap {
        val current = _state.value
        val draft = current.draft
        chapters(current.mode).filter { it != SetupWizardChapter.REVIEW }.forEach { putAll(SetupWizardValidation.validate(draft, it)) }
        val replacesDifferent = activationConfirmation(draft, current.programPreview)
        if (replacesDifferent && !draft.confirmActivation) put("activation", "Confirma la activación del plan")
        if (draft.includeTraining && (current.programPreview == null || current.previewError != null)) put("program", "Prepara una vista previa ejecutable")
        if (draft.includeNutrition && !nutritionReviewIsValid(nutritionEditor.uiState.value)) put("nutrition", "Completa el plan de nutrición")
    }

    private fun prepareNutritionPlan(draft: SetupWizardDraft) = if (draft.includeNutrition) nutritionEditor.preparePlan(draft.nutritionPlanId ?: draft.commitId) else null

    private suspend fun materializeProgram(context: Context, draft: SetupWizardDraft): SetupPreview {
        if (!draft.includeTraining) return SetupPreview(null, null)
        val entry = if (draft.trainingPath == SetupTrainingPath.PERSONALIZE) {
            draft.selectedCatalogId?.let(PersonalizedPlanCatalog::find)
        } else null
        if (draft.trainingPath == SetupTrainingPath.PERSONALIZE && entry == null) {
            error("Selecciona un plan")
        }
        if (draft.trainingPath == SetupTrainingPath.PERSONALIZE && entry?.source == com.example.kpkn.data.programs.CatalogSource.NATIVE) {
            val catalogId = draft.selectedCatalogId ?: error("Selecciona un plan")
            val frequency = draft.daysPerWeek ?: error("Selecciona los días de entrenamiento")
            val minutes = draft.minutesPerSession ?: error("Indica el tiempo disponible")
            val calibrated = draft.volumeRecommendations.isNotEmpty() && draft.athleteProfileScore != null
            val result = SimpleCyclePersonalizer(catalogRepository).personalize(
                draft.commitId,
                PersonalizerInput(
                    catalogEntryId = catalogId,
                    focus = draft.focus.toTrainingFocus(),
                    frequency = frequency,
                    weekdays = draft.selectedWeekdays.sorted(),
                    equipment = draft.equipment.map { it.catalogId }.toSet(),
                    level = draft.experience.toCatalogLevel(),
                    availableMinutes = minutes,
                    calibration = if (calibrated) Calibration.CALIBRATED else Calibration.CONSERVATIVE,
                    volumeRecommendations = if (calibrated) draft.volumeRecommendations else emptyList(),
                ),
            )
            val program = result.program ?: error(result.report.limitations.joinToString(" "))
            return SetupPreview(
                program.copy(volumeRecommendations = if (calibrated) draft.volumeRecommendations else emptyList(), athleteProfileScore = draft.athleteProfileScore),
                result.report,
            )
        }
        val catalog = (catalogRepository.state.value as? com.example.kpkn.domain.exercises.catalogv2.ExerciseCatalogStateV2.Ready)?.catalog
            ?: error("El catálogo de ejercicios todavía no está disponible")
        val base = Program(id = draft.commitId, name = "Plan de ${draft.name.ifBlank { "entrenamiento" }}")
        val program = when {
            entry?.source == com.example.kpkn.data.programs.CatalogSource.PROTOCOL -> {
                val protocol = PROTOCOL_LIBRARY.first { it.id == entry.sourceId }
                ProgramProtocolEngine.applyProtocol(
                    base.copy(selectedSplitId = protocol.defaultSplit), protocol,
                    metadata = CatalogCompositionMetadataProvider.fromCatalog(catalog),
                    exerciseList = catalog.toLegacyConfigurationLookup().values.toList(),
                ).copy(powerliftingProfile = draft.powerliftingProfile)
            }
            entry?.source == com.example.kpkn.data.programs.CatalogSource.TEMPLATE ->
                ProgramTemplateEngine.applyTemplate(base, requireNotNull(entry.template), forceReplace = true).program
            else -> {
                val sessions = draft.sessions.filter { it.weekday in draft.selectedWeekdays }.sortedBy { it.weekday }.map { it.toSession(draft.commitId) }
                require(sessions.isNotEmpty()) { "Completa las sesiones del programa" }
                val week = ProgramWeek("${draft.commitId}-week-1", "Semana 1", sessions = sessions)
                base.copy(structure = ProgramStructure.SIMPLE, simpleProgramKind = SimpleProgramKind.CYCLIC,
                    macrocycles = listOf(Macrocycle("${draft.commitId}-macro", "Ciclo", listOf(Block("${draft.commitId}-block", "Semana", mesocycles = listOf(Mesocycle("${draft.commitId}-meso", "Semana", MesocycleGoal.ACCUMULATION, weeks = listOf(week))))))),
                    weekDays = draft.daysPerWeek, isDraft = false)
            }
        }
        val materialized = program.copy(id = draft.commitId)
        val actualFrequency = materialized.macrocycles.flatMap { it.blocks }.flatMap { it.mesocycles }.flatMap { it.weeks }.flatMap { it.sessions }.mapNotNull { it.dayOfWeek }.distinct().size
        if (draft.daysPerWeek != null && actualFrequency != draft.daysPerWeek) {
            error("Este programa produce $actualFrequency días por semana, no ${draft.daysPerWeek}. Elige una frecuencia compatible; la receta fija no se modifica.")
        }
        return SetupPreview(materialized, null)
    }

    private fun recoveryEvidence(draft: SetupWizardDraft) = draft.ringsAnswers?.takeIf { it.capturedAtMs != null }?.let { answers ->
        InitialRecoveryEvidenceFactory.fromInputs(
            capturedAtMs = answers.capturedAtMs ?: return@let null,
            recencyDays = answers.recencyDays ?: 7,
            sessions = answers.sessionsLastSevenDays ?: 0,
            type = answers.activityType ?: InitialRecoveryActivityType.STRENGTH,
            intensity = when (answers.intensity ?: 2) { 1 -> InitialRecoveryIntensity.EASY; 2 -> InitialRecoveryIntensity.MODERATE; 3 -> InitialRecoveryIntensity.HARD; else -> InitialRecoveryIntensity.VERY_HARD },
            zones = answers.zones,
            sensations = InitialRecoverySensations(answers.muscleFeeling, answers.energy, answers.structureFeeling),
        )
    }

    private suspend fun loadRoomDraftNow(mode: SetupWizardMode): SetupWizardDraft? {
        val room = persistenceFactory(getApplication<Application>()).drafts.load(storageKey(mode))
        return room?.let { json.decodeFromString<SetupWizardDraft>(it.payloadJson) }
    }

    private fun loadSavedDraft(mode: SetupWizardMode? = null, storedMode: String? = savedStateHandle.get<String>(modeKey)): SetupWizardDraft? {
        val raw = savedStateHandle.get<String>(savedKey) ?: return null
        val draft = runCatching { json.decodeFromString<SetupWizardDraft>(raw) }.getOrNull() ?: return null
        val compatible = storedMode == mode?.name ||
            ((mode == SetupWizardMode.FULL || mode == SetupWizardMode.RESUME) &&
                storedMode in setOf(SetupWizardMode.FULL.name, SetupWizardMode.RESUME.name))
        return if (mode == null || compatible) draft else null
    }

    private fun newDraft(mode: SetupWizardMode, nutritionMode: String, nutritionPlanId: String?): SetupWizardDraft {
        val storage = storageKey(mode)
        val payload = UUID.randomUUID().toString()
        val settings = ProgramRepository.getInstance().settings.value
        val name = settings.username.trim().takeIf { it.isNotBlank() && !it.equals("Usuario", ignoreCase = true) }.orEmpty()
        val age = settings.userVitals.age?.takeIf { it in 13..100 }
        val includeTraining = mode != SetupWizardMode.NUTRITION_ONLY && mode != SetupWizardMode.RINGS_ONLY
        val includeNutrition = mode != SetupWizardMode.TRAINING_ONLY && mode != SetupWizardMode.RINGS_ONLY
        return SetupWizardDraft(storage, payload, name = name, ageYears = age, includeTraining = includeTraining, includeNutrition = includeNutrition, nutritionMode = nutritionMode, nutritionPlanId = nutritionPlanId, catalogRevision = PersonalizedPlanCatalog.REVISION)
    }

    private fun syncNutritionAge(age: Int?) {
        val text = age?.toString().orEmpty()
        if (syncingNutritionAge || nutritionEditor.uiState.value.draft.ageText == text) return
        syncingNutritionAge = true
        try { nutritionEditor.updateAge(text) } finally { syncingNutritionAge = false }
    }

    private fun storageKey(mode: SetupWizardMode) = "setup-wizard:${mode.name.lowercase()}"
    private fun calculateAge(iso: String?): Int? = runCatching {
        val birthDate = LocalDate.parse(iso)
        birthDate.until(LocalDate.now()).years
    }.getOrNull()?.takeIf { it in 13..100 }
    private fun SetupFocus.toTrainingFocus() = TrainingFocus.valueOf(name)
    private val SetupEquipment.catalogId: String get() = when (this) { SetupEquipment.NONE -> "bodyweight"; SetupEquipment.BANDS -> "band"; SetupEquipment.DUMBBELLS -> "dumbbells"; SetupEquipment.MACHINE -> "machine"; SetupEquipment.CABLE -> "cable"; SetupEquipment.BARBELL -> "barbell"; SetupEquipment.PULL_UP -> "pull_up_bar"; SetupEquipment.GYM -> "general_gym"; SetupEquipment.SUPPORT -> "support"; SetupEquipment.BALL -> "ball"; SetupEquipment.SMITH -> "smith_machine" }
    private fun SetupExperience?.toCatalogLevel() = when (this) { SetupExperience.ADVANCED -> CatalogLevel.ADVANCED; SetupExperience.INTERMEDIATE -> CatalogLevel.INTERMEDIATE; else -> CatalogLevel.BEGINNER }
    private fun com.example.kpkn.data.models.GoalMetric.toBodyMetric() = when (this) {
        com.example.kpkn.data.models.GoalMetric.WEIGHT -> com.example.kpkn.data.models.BodyMetric.WEIGHT
        com.example.kpkn.data.models.GoalMetric.BODY_FAT -> com.example.kpkn.data.models.BodyMetric.BODY_FAT_PERCENT
        com.example.kpkn.data.models.GoalMetric.MUSCLE_MASS -> com.example.kpkn.data.models.BodyMetric.MUSCLE_MASS_PERCENT
    }
}

private fun List<SetupSessionDraft>.ensureSession(weekday: Int): List<SetupSessionDraft> = if (any { it.weekday == weekday }) this else this + SetupSessionDraft(weekday, "Sesión del día $weekday")
