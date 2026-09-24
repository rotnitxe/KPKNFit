package com.example.kpkn.screens.onboarding

import com.example.kpkn.data.models.Exercise
import com.example.kpkn.data.models.ExerciseMuscleInfo
import com.example.kpkn.data.models.InitialRecoveryActivityType
import com.example.kpkn.data.models.InitialRecoveryIntensity
import com.example.kpkn.data.models.InitialRecoverySensations
import com.example.kpkn.data.models.Program
import com.example.kpkn.data.models.CardioType
import com.example.kpkn.data.models.Gender
import com.example.kpkn.data.models.GlobalBatteries
import com.example.kpkn.data.models.Session
import com.example.kpkn.data.models.NutritionPlan
import com.example.kpkn.data.models.AthleteProfileScore
import com.example.kpkn.data.models.PowerliftingProfile
import com.example.kpkn.data.models.VolumeRecommendation
import com.example.kpkn.data.models.CalibrationResponseState
import com.example.kpkn.data.models.InitialRecoveryAxialExposure
import com.example.kpkn.data.models.InitialRecoveryMuscleScope
import com.example.kpkn.data.models.VolumeCalibrationProfile
import com.example.kpkn.data.models.TrainingStyle
import com.example.kpkn.data.programs.toTrainingReference
import com.example.kpkn.domain.training.PersonalizationReport
import com.example.kpkn.domain.onboarding.SetupAnswerProvenance
import com.example.kpkn.domain.onboarding.SetupChangeDetector
import com.example.kpkn.domain.onboarding.SetupChangeSource
import com.example.kpkn.domain.onboarding.SetupDependencyRules
import com.example.kpkn.domain.onboarding.SetupFieldCheck
import com.example.kpkn.domain.onboarding.SetupInputFootprint
import com.example.kpkn.domain.onboarding.SetupStepContext
import com.example.kpkn.domain.onboarding.SetupStepGraph
import com.example.kpkn.domain.onboarding.SetupStepId
import com.example.kpkn.domain.onboarding.SetupStepProgress
import com.example.kpkn.domain.onboarding.SetupValueState
import com.example.kpkn.domain.onboarding.WizChatGraph
import com.example.kpkn.domain.onboarding.WizChatMachineState
import com.example.kpkn.domain.onboarding.WizChatProgress
import com.example.kpkn.domain.onboarding.WizChatQuestionId
import com.example.kpkn.domain.onboarding.WizChatValidation
import com.example.kpkn.screens.nutrition.NutritionWizardDraft
import kotlinx.serialization.Serializable
import java.time.LocalDate

@Serializable
enum class SetupWizardMode { FULL, TRAINING_ONLY, NUTRITION_ONLY, RINGS_ONLY, RESUME }

@Serializable
enum class SetupWizardChapter { PROFILE, VOLUME, TRAINING, WEEK, NUTRITION, RINGS, REVIEW }

@Serializable
enum class SetupModuleChoice { TRAINING, TRAINING_AND_NUTRITION }

@Serializable
enum class SetupProgramRoute { CUSTOMIZABLE, PROTOCOL, LATER }

@Serializable
enum class SetupRecentTrainingState { NOT_ANSWERED, NO, YES, UNKNOWN }

@Serializable
enum class SetupDiscomfortState { NOT_ANSWERED, NONE, DECLARED, OMITTED }

@Serializable
data class SetupVolumeAnswers(
    val style: TrainingStyle? = null,
    val technique: Int? = null,
    val consistency: Int? = null,
    val strength: Int? = null,
    val mobility: Int? = null,
    val responseState: CalibrationResponseState = CalibrationResponseState.UNKNOWN,
)

@Serializable
enum class SetupExperience(val label: String) { NEW("Estoy empezando"), RETURNING("Estoy volviendo"), INTERMEDIATE("Ya entreno con constancia"), ADVANCED("Tengo experiencia") }

@Serializable
enum class SetupTrainingPath(val label: String) { PERSONALIZE("Personaliza un plan"), FROM_SCRATCH("Crea desde cero") }

@Serializable
enum class SetupGoal(val label: String) {
    STRENGTH("Fuerza"),
    MUSCLE("Músculo"),
    STRENGTH_MUSCLE("Fuerza y músculo"),
    HEALTH("Salud y condición"),
    MIXED("Fuerza + cardio"),
}

/** Fuerza → powerlifting, Músculo → hipertrofia, Fuerza y músculo → powerbuilding. */
val SetupGoal.inferredTrainingStyle: com.example.kpkn.data.models.TrainingStyle?
    get() = when (this) {
        SetupGoal.STRENGTH -> com.example.kpkn.data.models.TrainingStyle.POWERLIFTER
        SetupGoal.MUSCLE -> com.example.kpkn.data.models.TrainingStyle.BODYBUILDER
        SetupGoal.STRENGTH_MUSCLE -> com.example.kpkn.data.models.TrainingStyle.POWERBUILDER
        SetupGoal.HEALTH, SetupGoal.MIXED -> null
    }

/** Reference used to pick candidates: inferred from the goal or asked in the brief focus question. */
fun SetupWizardDraft.trainingReference(): com.example.kpkn.data.programs.TrainingReference? {
    val style = goal?.inferredTrainingStyle ?: volumeAnswers.style
    return style?.toTrainingReference()
}

@Serializable
enum class SetupFocus(val label: String) { FULL_BODY("Todo el cuerpo"), GLUTES("Glúteos"), LEGS("Piernas"), BACK("Espalda"), CHEST("Pecho"), SHOULDERS("Hombros"), ARMS("Brazos") }

@Serializable
enum class SetupEquipment(val label: String) { NONE("Sin material"), BODYWEIGHT("Peso corporal"), BANDS("Bandas"), DUMBBELLS("Mancuernas"), MACHINE("Máquinas"), CABLE("Polea"), BARBELL("Barra"), PULL_UP("Barra de dominadas"), GYM("Gimnasio completo"), SUPPORT("Apoyo estable"), BALL("Balón"), SMITH("Máquina Smith") }

@Serializable
data class SetupExerciseDraft(
    val id: String,
    val exercise: Exercise,
    val info: ExerciseMuscleInfo? = null,
) {
    val name: String get() = exercise.name
    val sets: Int? get() = exercise.sets.size.takeIf { it > 0 }
    val reps: Int? get() = exercise.sets.firstOrNull()?.targetReps
}

@Serializable
data class SetupSessionDraft(
    val weekday: Int,
    val title: String,
    val exercises: List<SetupExerciseDraft> = emptyList(),
) {
    fun toSession(commitId: String): Session = Session(
        id = "$commitId-session-$weekday",
        name = title,
        exercises = exercises.map { it.exercise },
        dayOfWeek = weekday,
        assignedDays = listOf(weekday),
    )
}

@Serializable
data class SetupRingsAnswers(
    val startAction: String? = null,
    val recentTraining: Boolean? = null,
    val recencyDays: Int? = null,
    val sessionsLastSevenDays: Int? = null,
    val activityType: InitialRecoveryActivityType? = null,
    val intensity: Int? = null,
    val zones: List<String> = emptyList(),
    val muscleFeeling: Int? = null,
    val energy: Int? = null,
    val structureFeeling: Int? = null,
    val capturedAtMs: Long? = null,
    val recentTrainingState: SetupRecentTrainingState = SetupRecentTrainingState.NOT_ANSWERED,
    val lastSessionRecencyDays: Int? = null,
    val intensityLevel: InitialRecoveryIntensity? = null,
    val muscleScope: InitialRecoveryMuscleScope = InitialRecoveryMuscleScope.UNKNOWN,
    val recentMuscles: Set<String> = emptySet(),
    val axialExposure: InitialRecoveryAxialExposure = InitialRecoveryAxialExposure(),
    val discomfortIds: List<String> = emptyList(),
    val discomfortState: SetupDiscomfortState = SetupDiscomfortState.NOT_ANSWERED,
    val activityTypeState: com.example.kpkn.data.models.InitialRecoveryResponseState = com.example.kpkn.data.models.InitialRecoveryResponseState.UNKNOWN,
)

typealias InitialRingsAnswers = SetupRingsAnswers

@Serializable
data class SetupWizardDraft(
    val draftId: String = "",
    val commitId: String = "",
    /** Canonical persisted scope; RESUME is only a navigation intent. */
    val draftScope: String = "full",
    val revision: Int = 1,
    val chapter: SetupWizardChapter = SetupWizardChapter.PROFILE,
    val name: String = "",
    val profileGender: Gender? = null,
    val moduleChoice: SetupModuleChoice = SetupModuleChoice.TRAINING_AND_NUTRITION,
    val weightKg: Double? = null,
    val importedWeightKg: Double? = null,
    val weightUnit: String = "kg",
    val weightUnitChanged: Boolean = false,
    val heightCm: Double? = null,
    val ageYears: Int? = null,
    val birthDateIso: String? = null,
    val experience: SetupExperience? = null,
    val trainingPath: SetupTrainingPath? = null,
    val programRoute: SetupProgramRoute = SetupProgramRoute.CUSTOMIZABLE,
    val goal: SetupGoal? = null,
    val focus: SetupFocus = SetupFocus.FULL_BODY,
    val daysPerWeek: Int? = null,
    val selectedWeekdays: Set<Int> = emptySet(),
    val minutesPerSession: Int? = null,
    val cardioType: CardioType? = null,
    val cardioMinutes: Int? = null,
    val equipment: Set<SetupEquipment> = emptySet(),
    val trainingEnvironment: String? = null,
    val priorityMuscles: Set<String> = emptySet(),
    val lowerEmphasisMuscles: Set<String> = emptySet(),
    val selectedSplitId: String? = null,
    val customSplitPattern: List<String> = emptyList(),
    val customSplitName: String? = null,
    val selectedCatalogId: String? = null,
    val sessions: List<SetupSessionDraft> = emptyList(),
    val includeTraining: Boolean = true,
    val includeNutrition: Boolean = true,
    val activateProgram: Boolean = true,
    val activateNutrition: Boolean = true,
    val confirmActivation: Boolean = false,
    val acceptFixedRecipeDifference: Boolean = false,
    val ringsAnswers: SetupRingsAnswers? = null,
    val nutritionMode: String = "create",
    val nutritionPlanId: String? = null,
    val nutritionDraft: NutritionWizardDraft? = null,
    val nutritionStepIndex: Int = 0,
    val nutritionDraftJson: String? = null,
    val catalogRevision: String? = null,
    val volumeRecommendations: List<VolumeRecommendation> = emptyList(),
    val athleteProfileScore: AthleteProfileScore? = null,
    val powerliftingProfile: PowerliftingProfile? = null,
    val knowsTrainingMarks: Boolean = false,
    val volumeAnswers: SetupVolumeAnswers = SetupVolumeAnswers(),
    val volumeCalibrationProfile: VolumeCalibrationProfile? = null,
    val manualMuscleOverrides: Map<String, Int> = emptyMap(),
    val manualEnergyOverride: Int? = null,
    val manualStructureOverride: Int? = null,
    /**
     * Canonical wizard progress for the traditional step flow: stable step id,
     * block, step index and completed blocks. Legacy WizChat drafts are migrated
     * into this field by [SetupDraftCompatibility.repair].
     */
    val stepProgress: SetupStepProgress = SetupStepProgress(),
    /**
     * Legacy conversational progress. Since the step migration it is only a
     * compatibility mirror (question cursor, answer log and sound/branch flags)
     * kept so older drafts and screens keep loading; it never drives navigation.
     */
    val wizChat: WizChatProgress = WizChatProgress(),
)

/** Routing context derived from the draft; mirrors the legacy WizChat context. */
fun SetupWizardDraft.stepContext(): SetupStepContext = SetupStepContext(
    includeTraining = includeTraining,
    includeNutrition = includeNutrition,
    includeRings = draftScope in setOf("full", "resume", "rings_only"),
    programRouteLater = programRoute == SetupProgramRoute.LATER,
    homeEquipmentSelected = trainingEnvironment == "Entreno en casa",
    mixedTraining = goal == SetupGoal.MIXED,
    hasTrainingMarks = knowsTrainingMarks,
    goalStyleInferred = goal?.inferredTrainingStyle != null,
    nutritionProfessional = nutritionDraft?.mode == "professional",
    nutritionStarted = includeNutrition && nutritionMode == "create",
    ringsAction = ringsAnswers?.startAction,
    recentTraining = ringsAnswers?.recentTraining,
)

/** Fingerprint of the inputs that feed previews; pure navigation never changes it. */
fun SetupWizardDraft.inputFootprint(): SetupInputFootprint = SetupInputFootprint(
    weightKg = weightKg,
    heightCm = heightCm,
    ageYears = ageYears,
    gender = profileGender?.name,
    equipment = equipment.map { it.name }.toSet(),
    trainingEnvironment = trainingEnvironment,
    daysPerWeek = daysPerWeek,
    selectedWeekdays = selectedWeekdays,
    minutesPerSession = minutesPerSession,
    programRoute = programRoute.name,
    trainingPath = trainingPath?.name,
    goal = goal?.name,
    focus = focus.name,
    experience = experience?.name,
    volumeStyle = volumeAnswers.style?.name,
    volumeResponses = listOf(volumeAnswers.technique, volumeAnswers.consistency, volumeAnswers.strength, volumeAnswers.mobility),
    cardioType = cardioType?.name,
    cardioMinutes = cardioMinutes,
    knowsTrainingMarks = knowsTrainingMarks,
    marks = listOf(powerliftingProfile?.squat1RM, powerliftingProfile?.bench1RM, powerliftingProfile?.deadlift1RM),
    selectedCatalogId = selectedCatalogId,
    priorityMuscles = priorityMuscles,
    lowerEmphasisMuscles = lowerEmphasisMuscles,
    selectedSplitId = selectedSplitId,
    customSplitPattern = customSplitPattern,
    sessionsSignature = sessions.map { session ->
        "${session.weekday}:${session.title}:" + session.exercises.joinToString("|") { item ->
            "${item.id}/${item.sets ?: 0}/${item.reps}"
        }
    },
    ringsSignature = listOf(
        ringsAnswers?.toString().orEmpty(),
        manualMuscleOverrides.toString(),
        manualEnergyOverride?.toString().orEmpty(),
        manualStructureOverride?.toString().orEmpty(),
    ),
    nutritionSignature = listOf(
        nutritionDraft?.toString().orEmpty(),
        nutritionMode,
        nutritionPlanId.orEmpty(),
    ),
)

/** Keeps valid answers, marks incompatible selections pending and stales previews. */
fun SetupWizardDraft.applyChangeImpact(source: SetupChangeSource): SetupWizardDraft {
    val impact = SetupDependencyRules.impactOf(source)
    return copy(
        stepProgress = stepProgress
            .withPendingReview(impact.pendingSteps)
            .withStalePreviews(impact.stalePreviews),
    )
}

/** Applies every physiological change detected between [previous] and this draft. */
fun SetupWizardDraft.withChangeImpacts(previous: SetupWizardDraft): SetupWizardDraft {
    val sources = SetupChangeDetector.sourcesFor(previous.inputFootprint(), inputFootprint())
    return sources.fold(this) { draft, source -> draft.applyChangeImpact(source) }
}

/** Selections that must be reviewed again once earlier data changed. */
fun SetupWizardDraft.markPendingQuestions(questions: Collection<WizChatQuestionId>): SetupWizardDraft {
    val steps = questions.mapNotNull(SetupStepGraph::stepForQuestion).toSet()
    return copy(stepProgress = stepProgress.withPendingReview(steps))
}

/**
 * Rewinds to one step to change an earlier answer. Every answer is kept as data
 * and only the selections that may no longer be compatible are marked pending.
 */
fun SetupWizardDraft.editStep(
    step: SetupStepId,
    pendingQuestions: Collection<WizChatQuestionId> = emptyList(),
): SetupWizardDraft {
    val question = SetupStepGraph.questionForStep(step)
    return markPendingQuestions(pendingQuestions).copy(
        stepProgress = stepProgress.at(step, stepContext()),
        wizChat = wizChat.copy(
            currentQuestionId = question ?: wizChat.currentQuestionId,
            stage = question?.let(WizChatGraph::stageFor) ?: wizChat.stage,
            terminal = false,
            revision = wizChat.revision + 1,
        ),
    )
}

/** Advances or rewinds one step without deleting any answer. */
fun SetupWizardDraft.goNext(): SetupWizardDraft =
    moveCursorTo(SetupStepGraph.next(stepProgress.currentStepId, stepContext()))

fun SetupWizardDraft.goBack(): SetupWizardDraft =
    moveCursorTo(SetupStepGraph.previous(stepProgress.currentStepId, stepContext(), stepProgress.visited))

private fun SetupWizardDraft.moveCursorTo(step: SetupStepId?): SetupWizardDraft {
    if (step == null) return this
    val question = SetupStepGraph.questionForStep(step)
    return copy(
        stepProgress = stepProgress.at(step, stepContext()),
        wizChat = wizChat.copy(
            currentQuestionId = question ?: wizChat.currentQuestionId,
            stage = question?.let(WizChatGraph::stageFor) ?: wizChat.stage,
            terminal = step == SetupStepId.REVIEW_ACTIVATE,
            revision = wizChat.revision + 1,
        ),
    )
}

/** Records where a value came from; engine results can never be declared answers. */
fun SetupWizardDraft.recordStepAnswer(
    step: SetupStepId,
    provenance: SetupAnswerProvenance,
    valueState: SetupValueState,
): SetupWizardDraft = copy(stepProgress = stepProgress.recordAnswer(step, provenance, valueState))

/** Pending modal decisions; discard is never triggered by the back button. */
@Serializable
enum class SetupWizardDialog { NONE, EXIT, DISCARD }

/** Terminal plans produced only by an explicit user intention. */
sealed interface SetupWizardExitPlan {
    data class SaveAndExit(val draft: SetupWizardDraft) : SetupWizardExitPlan
    data class Discard(val draftId: String) : SetupWizardExitPlan
}

/**
 * Pure wizard session: step navigation, change impacts and the exit/discard
 * intentions. [saveAndExit] and [confirmDiscard] only return a plan after the
 * matching dialog was requested, so discarding always needs explicit
 * confirmation and is never tied to the back button.
 */
data class SetupWizardSession(
    val draft: SetupWizardDraft,
    val dialog: SetupWizardDialog = SetupWizardDialog.NONE,
    val isSavingAndExiting: Boolean = false,
    val exitCompleted: Boolean = false,
) {
    fun requestExit(): SetupWizardSession = copy(dialog = SetupWizardDialog.EXIT)

    fun requestDiscard(): SetupWizardSession = copy(dialog = SetupWizardDialog.DISCARD)

    fun keepConfiguring(): SetupWizardSession = copy(dialog = SetupWizardDialog.NONE)

    fun saveAndExit(): SetupWizardExitPlan? =
        if (dialog != SetupWizardDialog.EXIT || isSavingAndExiting) null
        else SetupWizardExitPlan.SaveAndExit(draft)

    fun onSavedAndExited(): SetupWizardSession =
        copy(dialog = SetupWizardDialog.NONE, isSavingAndExiting = false, exitCompleted = true)

    fun onDiscarded(): SetupWizardSession =
        copy(dialog = SetupWizardDialog.NONE, isSavingAndExiting = false, exitCompleted = true)

    /** Discarding needs the explicit discard dialog; anything else keeps the draft. */
    fun confirmDiscard(): SetupWizardExitPlan? =
        if (dialog != SetupWizardDialog.DISCARD) null
        else SetupWizardExitPlan.Discard(draft.draftId)

    fun goBack(): SetupWizardSession = copy(draft = draft.goBack())

    fun goNext(): SetupWizardSession = copy(draft = draft.goNext())

    fun change(source: SetupChangeSource): SetupWizardSession = copy(draft = draft.applyChangeImpact(source))
}

data class SetupWizardState(
    val draft: SetupWizardDraft,
    val mode: SetupWizardMode = SetupWizardMode.FULL,
    val dirty: Boolean = false,
    val errors: Map<String, String> = emptyMap(),
    val isLoading: Boolean = false,
    val isCommitting: Boolean = false,
    val receiptId: String? = null,
    val programPreview: Program? = null,
    val previewReport: PersonalizationReport? = null,
    val isPreviewLoading: Boolean = false,
    val previewError: String? = null,
    val fixedSessionEstimateMinutes: Int? = null,
    val fixedTrainingDays: Set<Int>? = null,
    val requiresActivationConfirmation: Boolean = false,
    val machineState: WizChatMachineState = WizChatMachineState.Loading,
    val isSubmittingAnswer: Boolean = false,
    val messages: List<com.example.kpkn.domain.onboarding.WizChatMessage> = emptyList(),
    val planCandidates: List<SetupPlanCandidate> = emptyList(),
    val availablePlanCandidates: List<SetupPlanCandidate> = emptyList(),
    val exerciseSuggestions: List<ExerciseMuscleInfo> = emptyList(),
    val isExerciseSearching: Boolean = false,
    val exerciseSearchError: String? = null,
    val isCandidateLoading: Boolean = false,
    val nutritionPlanPreview: NutritionPlan? = null,
    val nutritionErrors: Map<String, String> = emptyMap(),
    val nutritionPacePercentPerWeek: Double? = null,
    val ringsBatteriesPreview: GlobalBatteries? = null,
    val ringsPreviewLoading: Boolean = false,
    val ringsPreviewError: String? = null,
    val dialog: SetupWizardDialog = SetupWizardDialog.NONE,
    val isSavingAndExiting: Boolean = false,
    val exitCompleted: Boolean = false,
) {
    val showNutritionPreview: Boolean get() = nutritionDraft != null
    val nutritionDraft: NutritionWizardDraft? get() = draft.nutritionDraft
    val currentStep: SetupStepId get() = draft.stepProgress.currentStepId
    val currentBlock get() = draft.stepProgress.block
    val completedBlocks get() = draft.stepProgress.completedBlocks
    val stepValidation: List<SetupFieldCheck>
        get() = SetupWizardValidation.validateStep(draft, draft.stepProgress.currentStepId)
    val globalValidation: List<SetupFieldCheck>
        get() = SetupWizardValidation.validateAll(draft)
}

data class SetupPlanCandidate(
    val id: String,
    val title: String,
    val subtitle: String,
    val description: String,
    val source: String,
    val reasons: List<String> = emptyList(),
    val details: String? = null,
)

typealias SetupWizardUiState = SetupWizardState

data class SetupPreview(val program: Program?, val report: PersonalizationReport?)

object SetupWizardValidation {
    fun validate(draft: SetupWizardDraft, chapter: SetupWizardChapter): Map<String, String> = buildMap {
        when (chapter) {
            SetupWizardChapter.PROFILE -> {
                if (draft.ageYears == null && draft.birthDateIso == null) put("age", "Añade tu edad o fecha de nacimiento")
                draft.ageYears?.let { if (it !in 13..100) put("age", "La edad debe estar entre 13 y 100 años") }
                draft.birthDateIso?.let { value ->
                    val date = runCatching { LocalDate.parse(value) }.getOrNull()
                    if (date == null || date.isAfter(LocalDate.now())) put("birthDate", "Usa una fecha válida")
                    else if (date.plusYears(13).isAfter(LocalDate.now())) put("birthDate", "Debes tener al menos 13 años")
                }
                if (draft.experience == null) put("experience", "Elige tu experiencia")
            }
            SetupWizardChapter.TRAINING -> {
                if (!draft.includeTraining) return@buildMap
                if (draft.programRoute == SetupProgramRoute.LATER) return@buildMap
                if (draft.trainingPath == null && draft.programRoute == SetupProgramRoute.CUSTOMIZABLE) put("path", "Elige cómo quieres empezar")
                if (draft.goal == null) put("goal", "Elige un objetivo")
            }
            SetupWizardChapter.VOLUME -> {
                val answers = draft.volumeAnswers
                if (answers.style == null) put("volumeStyle", "Elige el estilo de referencia")
                if (answers.technique == null) put("volumeTechnique", "Indica tu técnica actual")
                if (answers.consistency == null) put("volumeConsistency", "Indica tu consistencia actual")
                if (answers.strength == null) put("volumeStrength", "Indica tu fuerza actual")
                if (answers.mobility == null) put("volumeMobility", "Indica tu movilidad actual")
            }
            SetupWizardChapter.WEEK -> {
                if (!draft.includeTraining) return@buildMap
                if (draft.programRoute == SetupProgramRoute.LATER) return@buildMap
                if (draft.daysPerWeek == null) put("days", "Elige los días que quieres entrenar")
                if (draft.minutesPerSession == null) put("minutes", "Indica el tiempo disponible")
                if (draft.equipment.isEmpty()) put("equipment", "Elige al menos un perfil de equipo")
                if (draft.selectedWeekdays.size != draft.daysPerWeek || draft.selectedWeekdays.any { it !in 1..7 }) put("week", "Selecciona ${draft.daysPerWeek ?: 0} días en tu semana")
                if (draft.trainingPath == SetupTrainingPath.FROM_SCRATCH) {
                    val selected = draft.sessions.filter { it.weekday in draft.selectedWeekdays }
                    if (selected.size != draft.selectedWeekdays.size || selected.any { it.exercises.isEmpty() }) put("sessions", "Completa todas las sesiones que programaste")
                }
            }
            SetupWizardChapter.NUTRITION -> Unit
            SetupWizardChapter.RINGS, SetupWizardChapter.REVIEW -> Unit
        }
    }

    fun validateDate(value: String?, today: LocalDate = LocalDate.now()): Boolean {
        val date = runCatching { value?.let(LocalDate::parse) }.getOrNull() ?: return false
        return !date.isAfter(today) && !date.plusYears(13).isAfter(today)
    }

    /**
     * Per-step validation distinguishing absent, invalid, declared and estimated
     * values. Ranges come from [WizChatValidation.validate]; nothing is defaulted
     * silently.
     */
    fun validateStep(draft: SetupWizardDraft, step: SetupStepId): List<SetupFieldCheck> {
        fun absent(key: String, message: String): List<SetupFieldCheck> =
            listOf(SetupFieldCheck(step, key, SetupValueState.ABSENT, message))
        fun invalid(key: String, message: String): List<SetupFieldCheck> =
            listOf(SetupFieldCheck(step, key, SetupValueState.INVALID, message))
        fun ok(key: String): List<SetupFieldCheck> =
            listOf(SetupFieldCheck(step, key, valueStateOf(draft, step)))
        fun choice(key: String, value: String?, absentMessage: String): List<SetupFieldCheck> {
            if (value.isNullOrBlank()) return if (draft.isAnswered(step)) ok(key) else absent(key, absentMessage)
            val question = SetupStepGraph.questionForStep(step)?.let(WizChatGraph::question)
            val error = question?.let { WizChatValidation.validate(it, text = value) }
            return if (error != null) invalid(key, error) else ok(key)
        }
        fun number(key: String, value: Double?, absentMessage: String): List<SetupFieldCheck> {
            if (value == null) return absent(key, absentMessage)
            val question = SetupStepGraph.questionForStep(step)?.let(WizChatGraph::question)
            val error = question?.let { WizChatValidation.validate(it, number = value) }
            return if (error != null) invalid(key, error) else ok(key)
        }
        return when (step) {
            SetupStepId.NAME -> when {
                draft.name.isBlank() && !draft.isAnswered(step) -> absent("name", "Escribe un nombre o usa omitir")
                draft.name.length > 32 -> invalid("name", "Usa hasta 32 caracteres")
                else -> ok("name")
            }
            SetupStepId.GENDER -> if (draft.isAnswered(step) || draft.profileGender != null) ok("profileGender")
                else absent("profileGender", "Elige con qué género te identificas o usa omitir")
            SetupStepId.AGE -> number("age", draft.ageYears?.toDouble(), "Añade tu edad o fecha de nacimiento")
            SetupStepId.HEIGHT -> number("height", draft.heightCm, "Indica tu estatura")
            SetupStepId.WEIGHT -> number("weight", draft.weightKg, "Indica tu peso")
            SetupStepId.EXPERIENCE -> choice("experience", draft.experience?.label, "Elige tu experiencia")
            SetupStepId.MILESTONE_BASICS, SetupStepId.MILESTONE_TRAINING,
            SetupStepId.MILESTONE_NUTRITION, SetupStepId.MILESTONE_RINGS -> emptyList()
            SetupStepId.ROUTE -> if (draft.isAnswered(step)) ok("programRoute") else absent("programRoute", "Elige cómo quieres empezar")
            SetupStepId.GOAL -> if (draft.goal != null) ok("goal") else absent("goal", "Elige un objetivo")
            SetupStepId.STYLE -> if (draft.volumeAnswers.style != null) ok("volumeStyle")
                else absent("volumeStyle", "Elige el estilo de referencia")
            SetupStepId.VOLUME_TECHNIQUE -> if (draft.volumeAnswers.technique != null) ok("volumeTechnique")
                else absent("volumeTechnique", "Indica tu técnica actual")
            SetupStepId.VOLUME_CONSISTENCY -> if (draft.volumeAnswers.consistency != null) ok("volumeConsistency")
                else absent("volumeConsistency", "Indica tu consistencia actual")
            SetupStepId.VOLUME_STRENGTH -> if (draft.volumeAnswers.strength != null) ok("volumeStrength")
                else absent("volumeStrength", "Indica tu fuerza actual")
            SetupStepId.VOLUME_MOBILITY -> if (draft.volumeAnswers.mobility != null) ok("volumeMobility")
                else absent("volumeMobility", "Indica tu movilidad actual")
            SetupStepId.EQUIPMENT -> if (draft.trainingEnvironment.isNullOrBlank()) absent("equipment", "Elige dónde sueles entrenar")
                else ok("equipment")
            SetupStepId.HOME_EQUIPMENT -> if (draft.equipment.isEmpty()) absent("equipment", "Elige al menos un perfil de equipo")
                else ok("equipment")
            SetupStepId.DAYS -> when {
                draft.daysPerWeek == null -> absent("days", "Elige los días que quieres entrenar")
                draft.daysPerWeek !in 1..6 -> invalid("days", "Elige entre 1 y 6 días")
                else -> ok("days")
            }
            SetupStepId.WEEKDAYS -> when {
                draft.selectedWeekdays.isEmpty() -> absent("week", "Selecciona tus días de entrenamiento")
                draft.daysPerWeek == null || draft.selectedWeekdays.size != draft.daysPerWeek ||
                    draft.selectedWeekdays.any { it !in 1..7 } ->
                    invalid("week", "Selecciona ${draft.daysPerWeek ?: 0} días en tu semana")
                else -> ok("week")
            }
            SetupStepId.SESSION_TIME -> number("minutes", draft.minutesPerSession?.toDouble(), "Indica el tiempo disponible")
            SetupStepId.CARDIO_TYPE -> if (draft.cardioType != null) ok("cardioType") else absent("cardioType", "Elige el tipo de cardio")
            SetupStepId.CARDIO_TIME -> if (draft.cardioMinutes != null) ok("cardioMinutes") else absent("cardioMinutes", "Indica los minutos de cardio")
            SetupStepId.TRAINING_MAX -> if (draft.isAnswered(step)) ok("trainingMax") else absent("trainingMax", "Indica si conoces tus marcas")
            SetupStepId.TRAINING_MARKS -> {
                val marks = listOf(draft.powerliftingProfile?.squat1RM, draft.powerliftingProfile?.bench1RM, draft.powerliftingProfile?.deadlift1RM)
                when {
                    marks.all { it == null } -> absent("marks", "Añade al menos una marca, o vuelve y elige Todavía no")
                    marks.any { it != null && it !in 1.0..1000.0 } -> invalid("marks", "Usa valores válidos entre 1 y 1000 kg")
                    else -> ok("marks")
                }
            }
            SetupStepId.PLAN -> if (draft.selectedCatalogId != null || draft.trainingPath == SetupTrainingPath.FROM_SCRATCH) ok("plan")
                else absent("plan", "Elige un plan")
            SetupStepId.TRAINING_REVIEW, SetupStepId.NUTRITION_RESULT,
            SetupStepId.RINGS_RESULT, SetupStepId.REVIEW_ACTIVATE -> emptyList()
            SetupStepId.NUTRITION_START -> if (draft.isAnswered(step)) ok("nutritionStart") else absent("nutritionStart", "Elige una opción de nutrición")
            SetupStepId.NUTRITION_SEX -> if (draft.nutritionDraft?.equationSex != null) ok("equationSex")
                else absent("equationSex", "Elige el sexo que usamos solo para calcular tu energía")
            SetupStepId.NUTRITION_ELIGIBILITY -> if (draft.isAnswered(step)) ok("eligibility") else absent("eligibility", "Elige al menos una opción")
            SetupStepId.NUTRITION_DIRECTION -> if (draft.nutritionDraft?.direction != null) ok("direction")
                else absent("direction", "Elige hacia dónde quieres llevar tu alimentación")
            SetupStepId.NUTRITION_ACTIVITY -> if (draft.isAnswered(step)) ok("activity") else absent("activity", "Indica qué tan activo eres")
            SetupStepId.RINGS_START -> if (draft.isAnswered(step)) ok("startAction") else absent("startAction", "Elige cómo situar tus RINGS")
            SetupStepId.RINGS_RECENT -> when (draft.ringsAnswers?.recentTrainingState) {
                null, SetupRecentTrainingState.NOT_ANSWERED ->
                    absent("recentTraining", "Indica si entrenaste en los últimos siete días")
                else -> ok("recentTraining")
            }
            SetupStepId.RINGS_SESSIONS -> if (draft.ringsAnswers?.sessionsLastSevenDays != null) ok("sessions")
                else absent("sessions", "Indica cuántas sesiones hiciste")
            SetupStepId.RINGS_RECENCY -> if (draft.ringsAnswers?.lastSessionRecencyDays != null) ok("recency")
                else absent("recency", "Indica cuándo fue tu última sesión")
            SetupStepId.RINGS_ACTIVITY -> if (draft.ringsAnswers?.activityType != null) ok("activityType")
                else absent("activityType", "Indica qué predominó en tus sesiones")
            SetupStepId.RINGS_INTENSITY -> if (draft.ringsAnswers?.intensityLevel != null) ok("intensity")
                else absent("intensity", "Indica cómo sentiste la intensidad")
            SetupStepId.RINGS_AXIAL -> if (draft.isAnswered(step)) ok("axial") else absent("axial", "Indica si hubo cargas pesadas para la espalda")
            SetupStepId.RINGS_MUSCLE_FEELING -> if (draft.ringsAnswers?.muscleFeeling != null) ok("muscleFeeling")
                else absent("muscleFeeling", "Indica cómo se sienten tus músculos")
            SetupStepId.RINGS_ENERGY_FEELING -> if (draft.ringsAnswers?.energy != null) ok("energy")
                else absent("energy", "Indica cómo está tu energía")
            SetupStepId.RINGS_STRUCTURE_FEELING -> if (draft.ringsAnswers?.structureFeeling != null) ok("structureFeeling")
                else absent("structureFeeling", "Indica cómo está tu columna")
            SetupStepId.RINGS_DISCOMFORT -> when (draft.ringsAnswers?.discomfortState) {
                null, SetupDiscomfortState.NOT_ANSWERED -> absent("discomfort", "Indica si hay alguna molestia o si prefieres omitirlo")
                else -> ok("discomfort")
            }
        }
    }

    /** Global validation: every step of the current route plus equation inputs. */
    fun validateAll(draft: SetupWizardDraft): List<SetupFieldCheck> =
        SetupStepGraph.stepIds(draft.stepContext()).flatMap { validateStep(draft, it) } + missingEquationInputs(draft)

    /**
     * Data an equation needs but the draft does not provide. These are never
     * replaced by defaults: the wizard must ask again or keep the value pending.
     */
    fun missingEquationInputs(draft: SetupWizardDraft): List<SetupFieldCheck> {
        if (!draft.includeNutrition || draft.nutritionMode != "create") return emptyList()
        val nutrition = draft.nutritionDraft
        return buildList {
            if (draft.weightKg == null && nutrition?.weightText.isNullOrBlank()) add(
                SetupFieldCheck(SetupStepId.WEIGHT, "equation.weight", SetupValueState.MISSING_EQUATION_INPUT,
                    "El peso es necesario para calcular el gasto energético"))
            if (draft.heightCm == null && nutrition?.heightText.isNullOrBlank()) add(
                SetupFieldCheck(SetupStepId.HEIGHT, "equation.height", SetupValueState.MISSING_EQUATION_INPUT,
                    "La estatura es necesaria para calcular el gasto energético"))
            if (draft.ageYears == null && nutrition?.ageText.isNullOrBlank()) add(
                SetupFieldCheck(SetupStepId.AGE, "equation.age", SetupValueState.MISSING_EQUATION_INPUT,
                    "La edad es necesaria para calcular el gasto energético"))
            if (nutrition?.equationSex == null) add(
                SetupFieldCheck(SetupStepId.NUTRITION_SEX, "equation.sex", SetupValueState.MISSING_EQUATION_INPUT,
                    "El sexo de cálculo es necesario para la ecuación de energía"))
        }
    }

    private fun SetupWizardDraft.isAnswered(step: SetupStepId): Boolean =
        step in stepProgress.answers ||
            (SetupStepGraph.questionForStep(step)?.let { question ->
                wizChat.acceptedAnswers.any { it.questionId == question }
            } == true)

    private fun valueStateOf(draft: SetupWizardDraft, step: SetupStepId): SetupValueState =
        when (draft.stepProgress.answers[step]) {
            SetupAnswerProvenance.USER_DECLARED -> SetupValueState.DECLARED
            SetupAnswerProvenance.SUGGESTED, SetupAnswerProvenance.DERIVED -> SetupValueState.ESTIMATED
            else -> if (draft.isAnswered(step)) SetupValueState.DECLARED else SetupValueState.ESTIMATED
        }
}
