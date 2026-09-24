package com.example.kpkn.domain.onboarding

import kotlinx.serialization.Serializable

/**
 * Pure step graph for the traditional setup wizard that replaces the WizChat
 * conversational orchestration.
 *
 * The flow is organised as four blocks plus milestones and a closing review:
 * Datos básicos → Entreno → Nutrición → Rings → Revisión y activación.
 *
 * Step identity is always the stable [SetupStepId]; [SetupStepProgress.stepIndex]
 * is a derived position inside the current route and must never be used as the
 * only identity, because conditional branches insert or drop steps.
 */

@Serializable
enum class SetupWizardBlock { BASICS, TRAINING, NUTRITION, RINGS, REVIEW }

@Serializable
enum class SetupStepKind { QUESTION, MILESTONE, REVIEW }

@Serializable
enum class SetupStepId {
    // Datos básicos
    NAME, GENDER, AGE, HEIGHT, WEIGHT, EXPERIENCE, MILESTONE_BASICS,
    // Entreno
    ROUTE, GOAL, STYLE, VOLUME_TECHNIQUE, VOLUME_CONSISTENCY, VOLUME_STRENGTH, VOLUME_MOBILITY,
    EQUIPMENT, HOME_EQUIPMENT, DAYS, WEEKDAYS, SESSION_TIME, CARDIO_TYPE, CARDIO_TIME,
    TRAINING_MAX, TRAINING_MARKS, PLAN, TRAINING_REVIEW, MILESTONE_TRAINING,
    // Nutrición
    NUTRITION_START, NUTRITION_SEX, NUTRITION_ELIGIBILITY, NUTRITION_DIRECTION,
    NUTRITION_ACTIVITY, NUTRITION_RESULT, MILESTONE_NUTRITION,
    // Rings
    RINGS_START, RINGS_RECENT, RINGS_SESSIONS, RINGS_RECENCY, RINGS_ACTIVITY, RINGS_INTENSITY,
    RINGS_AXIAL, RINGS_MUSCLE_FEELING, RINGS_ENERGY_FEELING, RINGS_STRUCTURE_FEELING,
    RINGS_DISCOMFORT, RINGS_RESULT, MILESTONE_RINGS,
    // Revisión y activación
    REVIEW_ACTIVATE,
}

data class SetupStepNode(
    val id: SetupStepId,
    val block: SetupWizardBlock,
    val kind: SetupStepKind,
)

/** Minimal routing context; it deliberately contains no UI state. */
data class SetupStepContext(
    val includeTraining: Boolean = true,
    val includeNutrition: Boolean = true,
    val includeRings: Boolean = true,
    val programRouteLater: Boolean = false,
    val homeEquipmentSelected: Boolean = false,
    val mixedTraining: Boolean = false,
    val hasTrainingMarks: Boolean = false,
    val goalStyleInferred: Boolean = false,
    val nutritionProfessional: Boolean = false,
    val nutritionStarted: Boolean = false,
    val ringsAction: String? = null,
    val recentTraining: Boolean? = null,
) {
    /** Same skip rules the WizChat graph applied to the RINGS start action. */
    val ringsSkipsCalibration: Boolean
        get() {
            val action = ringsAction?.trim()?.lowercase().orEmpty()
            return action == "omit" || action == "keep" || action == "preserve" || action == "remove" ||
                action.contains("dejar") || action.contains("conservar") ||
                action.contains("quitar") || action.contains("uncalibrated")
        }
}

object SetupStepGraph {
    private val blocks: Map<SetupStepId, SetupWizardBlock> = mapOf(
        SetupStepId.NAME to SetupWizardBlock.BASICS,
        SetupStepId.GENDER to SetupWizardBlock.BASICS,
        SetupStepId.AGE to SetupWizardBlock.BASICS,
        SetupStepId.HEIGHT to SetupWizardBlock.BASICS,
        SetupStepId.WEIGHT to SetupWizardBlock.BASICS,
        SetupStepId.EXPERIENCE to SetupWizardBlock.BASICS,
        SetupStepId.MILESTONE_BASICS to SetupWizardBlock.BASICS,
        SetupStepId.ROUTE to SetupWizardBlock.TRAINING,
        SetupStepId.GOAL to SetupWizardBlock.TRAINING,
        SetupStepId.STYLE to SetupWizardBlock.TRAINING,
        SetupStepId.VOLUME_TECHNIQUE to SetupWizardBlock.TRAINING,
        SetupStepId.VOLUME_CONSISTENCY to SetupWizardBlock.TRAINING,
        SetupStepId.VOLUME_STRENGTH to SetupWizardBlock.TRAINING,
        SetupStepId.VOLUME_MOBILITY to SetupWizardBlock.TRAINING,
        SetupStepId.EQUIPMENT to SetupWizardBlock.TRAINING,
        SetupStepId.HOME_EQUIPMENT to SetupWizardBlock.TRAINING,
        SetupStepId.DAYS to SetupWizardBlock.TRAINING,
        SetupStepId.WEEKDAYS to SetupWizardBlock.TRAINING,
        SetupStepId.SESSION_TIME to SetupWizardBlock.TRAINING,
        SetupStepId.CARDIO_TYPE to SetupWizardBlock.TRAINING,
        SetupStepId.CARDIO_TIME to SetupWizardBlock.TRAINING,
        SetupStepId.TRAINING_MAX to SetupWizardBlock.TRAINING,
        SetupStepId.TRAINING_MARKS to SetupWizardBlock.TRAINING,
        SetupStepId.PLAN to SetupWizardBlock.TRAINING,
        SetupStepId.TRAINING_REVIEW to SetupWizardBlock.TRAINING,
        SetupStepId.MILESTONE_TRAINING to SetupWizardBlock.TRAINING,
        SetupStepId.NUTRITION_START to SetupWizardBlock.NUTRITION,
        SetupStepId.NUTRITION_SEX to SetupWizardBlock.NUTRITION,
        SetupStepId.NUTRITION_ELIGIBILITY to SetupWizardBlock.NUTRITION,
        SetupStepId.NUTRITION_DIRECTION to SetupWizardBlock.NUTRITION,
        SetupStepId.NUTRITION_ACTIVITY to SetupWizardBlock.NUTRITION,
        SetupStepId.NUTRITION_RESULT to SetupWizardBlock.NUTRITION,
        SetupStepId.MILESTONE_NUTRITION to SetupWizardBlock.NUTRITION,
        SetupStepId.RINGS_START to SetupWizardBlock.RINGS,
        SetupStepId.RINGS_RECENT to SetupWizardBlock.RINGS,
        SetupStepId.RINGS_SESSIONS to SetupWizardBlock.RINGS,
        SetupStepId.RINGS_RECENCY to SetupWizardBlock.RINGS,
        SetupStepId.RINGS_ACTIVITY to SetupWizardBlock.RINGS,
        SetupStepId.RINGS_INTENSITY to SetupWizardBlock.RINGS,
        SetupStepId.RINGS_AXIAL to SetupWizardBlock.RINGS,
        SetupStepId.RINGS_MUSCLE_FEELING to SetupWizardBlock.RINGS,
        SetupStepId.RINGS_ENERGY_FEELING to SetupWizardBlock.RINGS,
        SetupStepId.RINGS_STRUCTURE_FEELING to SetupWizardBlock.RINGS,
        SetupStepId.RINGS_DISCOMFORT to SetupWizardBlock.RINGS,
        SetupStepId.RINGS_RESULT to SetupWizardBlock.RINGS,
        SetupStepId.MILESTONE_RINGS to SetupWizardBlock.RINGS,
        SetupStepId.REVIEW_ACTIVATE to SetupWizardBlock.REVIEW,
    )

    private val milestones: Map<SetupWizardBlock, SetupStepId> = mapOf(
        SetupWizardBlock.BASICS to SetupStepId.MILESTONE_BASICS,
        SetupWizardBlock.TRAINING to SetupStepId.MILESTONE_TRAINING,
        SetupWizardBlock.NUTRITION to SetupStepId.MILESTONE_NUTRITION,
        SetupWizardBlock.RINGS to SetupStepId.MILESTONE_RINGS,
    )

    private val questions: Map<SetupStepId, WizChatQuestionId> = mapOf(
        SetupStepId.NAME to WizChatQuestionId.P_NAME,
        SetupStepId.GENDER to WizChatQuestionId.P_GENDER,
        SetupStepId.AGE to WizChatQuestionId.P_AGE,
        SetupStepId.HEIGHT to WizChatQuestionId.P_HEIGHT,
        SetupStepId.WEIGHT to WizChatQuestionId.P_WEIGHT,
        SetupStepId.EXPERIENCE to WizChatQuestionId.P_EXPERIENCE,
        SetupStepId.ROUTE to WizChatQuestionId.T_ROUTE,
        SetupStepId.GOAL to WizChatQuestionId.T_GOAL,
        SetupStepId.STYLE to WizChatQuestionId.T_STYLE,
        SetupStepId.VOLUME_TECHNIQUE to WizChatQuestionId.T_VOLUME_TECHNIQUE,
        SetupStepId.VOLUME_CONSISTENCY to WizChatQuestionId.T_VOLUME_CONSISTENCY,
        SetupStepId.VOLUME_STRENGTH to WizChatQuestionId.T_VOLUME_STRENGTH,
        SetupStepId.VOLUME_MOBILITY to WizChatQuestionId.T_VOLUME_MOBILITY,
        SetupStepId.EQUIPMENT to WizChatQuestionId.T_EQUIPMENT,
        SetupStepId.HOME_EQUIPMENT to WizChatQuestionId.T_HOME_EQUIPMENT,
        SetupStepId.DAYS to WizChatQuestionId.T_DAYS,
        SetupStepId.WEEKDAYS to WizChatQuestionId.T_WEEKDAYS,
        SetupStepId.SESSION_TIME to WizChatQuestionId.T_TIME,
        SetupStepId.CARDIO_TYPE to WizChatQuestionId.T_CARDIO_TYPE,
        SetupStepId.CARDIO_TIME to WizChatQuestionId.T_CARDIO_TIME,
        SetupStepId.TRAINING_MAX to WizChatQuestionId.T_TRAINING_MAX,
        SetupStepId.TRAINING_MARKS to WizChatQuestionId.T_MARKS,
        SetupStepId.PLAN to WizChatQuestionId.T_PLAN,
        SetupStepId.TRAINING_REVIEW to WizChatQuestionId.T_REVIEW,
        SetupStepId.NUTRITION_START to WizChatQuestionId.N_START,
        SetupStepId.NUTRITION_SEX to WizChatQuestionId.N_SEX,
        SetupStepId.NUTRITION_ELIGIBILITY to WizChatQuestionId.N_ELIGIBILITY,
        SetupStepId.NUTRITION_DIRECTION to WizChatQuestionId.N_DIRECTION,
        SetupStepId.NUTRITION_ACTIVITY to WizChatQuestionId.N_ACTIVITY,
        SetupStepId.NUTRITION_RESULT to WizChatQuestionId.N_RESULT,
        SetupStepId.RINGS_START to WizChatQuestionId.R_START,
        SetupStepId.RINGS_RECENT to WizChatQuestionId.R_RECENT,
        SetupStepId.RINGS_SESSIONS to WizChatQuestionId.R_SESSIONS,
        SetupStepId.RINGS_RECENCY to WizChatQuestionId.R_RECENCY,
        SetupStepId.RINGS_ACTIVITY to WizChatQuestionId.R_ACTIVITY,
        SetupStepId.RINGS_INTENSITY to WizChatQuestionId.R_INTENSITY,
        SetupStepId.RINGS_AXIAL to WizChatQuestionId.R_AXIAL,
        SetupStepId.RINGS_MUSCLE_FEELING to WizChatQuestionId.R_FEELINGS_MUSCLE,
        SetupStepId.RINGS_ENERGY_FEELING to WizChatQuestionId.R_FEELINGS_ENERGY,
        SetupStepId.RINGS_STRUCTURE_FEELING to WizChatQuestionId.R_FEELINGS_STRUCTURE,
        SetupStepId.RINGS_DISCOMFORT to WizChatQuestionId.R_DISCOMFORT,
        SetupStepId.RINGS_RESULT to WizChatQuestionId.R_RESULT,
        SetupStepId.REVIEW_ACTIVATE to WizChatQuestionId.REVIEW,
    )

    private val stepsByQuestion: Map<WizChatQuestionId, SetupStepId> =
        questions.entries.associate { (step, question) -> question to step }

    fun blockOf(step: SetupStepId): SetupWizardBlock =
        blocks[step] ?: SetupWizardBlock.REVIEW

    fun isMilestone(step: SetupStepId): Boolean =
        milestones.values.contains(step)

    fun milestoneOf(block: SetupWizardBlock): SetupStepId? = milestones[block]

    /** Legacy bridge: the WizChat question rendered for a step, null for milestones. */
    fun questionForStep(step: SetupStepId): WizChatQuestionId? = questions[step]

    /** Migration bridge: the stable step behind a legacy question id. */
    fun stepForQuestion(question: WizChatQuestionId): SetupStepId? = stepsByQuestion[question]

    /** Full conditional route: block steps, milestone between blocks and final review. */
    fun nodes(context: SetupStepContext): List<SetupStepNode> = buildList {
        if (context.includeTraining) {
            addAll(listOf(SetupStepId.NAME, SetupStepId.GENDER, SetupStepId.AGE, SetupStepId.HEIGHT, SetupStepId.WEIGHT, SetupStepId.EXPERIENCE))
            add(SetupStepId.MILESTONE_BASICS)
            add(SetupStepId.ROUTE)
            if (!context.programRouteLater) add(SetupStepId.GOAL)
            if (context.programRouteLater || !context.goalStyleInferred) add(SetupStepId.STYLE)
            addAll(listOf(SetupStepId.VOLUME_TECHNIQUE, SetupStepId.VOLUME_CONSISTENCY, SetupStepId.VOLUME_STRENGTH, SetupStepId.VOLUME_MOBILITY))
            if (!context.programRouteLater) {
                add(SetupStepId.EQUIPMENT)
                if (context.homeEquipmentSelected) add(SetupStepId.HOME_EQUIPMENT)
                addAll(listOf(SetupStepId.DAYS, SetupStepId.WEEKDAYS, SetupStepId.SESSION_TIME))
                if (context.mixedTraining) addAll(listOf(SetupStepId.CARDIO_TYPE, SetupStepId.CARDIO_TIME))
                add(SetupStepId.TRAINING_MAX)
                if (context.hasTrainingMarks) add(SetupStepId.TRAINING_MARKS)
                addAll(listOf(SetupStepId.PLAN, SetupStepId.TRAINING_REVIEW))
            }
            add(SetupStepId.MILESTONE_TRAINING)
        }
        if (context.includeNutrition) {
            add(SetupStepId.NUTRITION_START)
            when {
                context.nutritionProfessional -> add(SetupStepId.NUTRITION_RESULT)
                context.nutritionStarted -> addAll(listOf(
                    SetupStepId.NUTRITION_SEX, SetupStepId.NUTRITION_ELIGIBILITY,
                    SetupStepId.NUTRITION_DIRECTION, SetupStepId.NUTRITION_ACTIVITY, SetupStepId.NUTRITION_RESULT,
                ))
            }
            add(SetupStepId.MILESTONE_NUTRITION)
        }
        if (context.includeRings) {
            add(SetupStepId.RINGS_START)
            if (!context.ringsSkipsCalibration) {
                add(SetupStepId.RINGS_RECENT)
                if (context.recentTraining == true) {
                    addAll(listOf(
                        SetupStepId.RINGS_SESSIONS, SetupStepId.RINGS_RECENCY, SetupStepId.RINGS_ACTIVITY,
                        SetupStepId.RINGS_INTENSITY, SetupStepId.RINGS_AXIAL,
                    ))
                }
                // Unknown recency is not a lack of training: sensations and discomfort
                // are always asked so partial calibration keeps a real check-in.
                addAll(listOf(
                    SetupStepId.RINGS_MUSCLE_FEELING, SetupStepId.RINGS_ENERGY_FEELING,
                    SetupStepId.RINGS_STRUCTURE_FEELING, SetupStepId.RINGS_DISCOMFORT, SetupStepId.RINGS_RESULT,
                ))
            }
            add(SetupStepId.MILESTONE_RINGS)
        }
        add(SetupStepId.REVIEW_ACTIVATE)
    }.map { node -> SetupStepNode(node, blockOf(node), kindOf(node)) }

    private fun kindOf(step: SetupStepId): SetupStepKind = when {
        isMilestone(step) -> SetupStepKind.MILESTONE
        step == SetupStepId.REVIEW_ACTIVATE -> SetupStepKind.REVIEW
        else -> SetupStepKind.QUESTION
    }

    fun stepIds(context: SetupStepContext): List<SetupStepId> = nodes(context).map { it.id }

    fun first(context: SetupStepContext): SetupStepId = stepIds(context).first()

    fun last(context: SetupStepContext): SetupStepId = stepIds(context).last()

    fun indexOf(step: SetupStepId, context: SetupStepContext): Int = stepIds(context).indexOf(step)

    fun next(step: SetupStepId, context: SetupStepContext): SetupStepId? {
        val route = stepIds(context)
        val index = route.indexOf(step)
        return if (index < 0 || index + 1 >= route.size) null else route[index + 1]
    }

    /**
     * Previous step in the route. When the wizard already knows the steps the
     * user really visited, that order wins so back never lands on a branch the
     * user never saw. Back navigation never removes answers.
     */
    fun previous(step: SetupStepId, context: SetupStepContext, visited: List<SetupStepId> = emptyList()): SetupStepId? {
        val visitedIndex = visited.indexOfLast { it == step }
        if (visitedIndex > 0) return visited[visitedIndex - 1]
        if (visitedIndex == 0) return null
        val route = stepIds(context)
        val index = route.indexOf(step)
        return if (index <= 0) null else route[index - 1]
    }

    /** First step still without answer after [step]; used to skip answered steps on resume. */
    fun nextPending(step: SetupStepId, context: SetupStepContext, answered: Set<SetupStepId>): SetupStepId {
        var next = next(step, context) ?: return SetupStepId.REVIEW_ACTIVATE
        val visited = mutableSetOf<SetupStepId>()
        while (next != SetupStepId.REVIEW_ACTIVATE && next in answered && visited.add(next)) {
            next = next(next, context) ?: SetupStepId.REVIEW_ACTIVATE
        }
        return next
    }

    /** Blocks whose milestone already sits before [step] in the current route. */
    fun blocksCompletedBefore(step: SetupStepId, context: SetupStepContext): Set<SetupWizardBlock> {
        val route = stepIds(context)
        val index = route.indexOf(step)
        if (index < 0) return emptySet()
        return milestones.entries
            .filter { (block, milestone) -> route.indexOf(milestone) in 0 until index }
            .mapTo(mutableSetOf()) { (block, _) -> block }
    }

    /**
     * Migration from a legacy WizChat draft: maps [currentQuestionId] to the
     * stable step. Returns a progress marked [SetupProgressOrigin.NOT_CONVERTIBLE]
     * when the question cannot be placed inside the route for this context; the
     * caller must then keep the legacy data untouched.
     */
    fun migrateFromLegacy(
        currentQuestionId: WizChatQuestionId,
        context: SetupStepContext,
        answered: Map<SetupStepId, SetupAnswerProvenance> = emptyMap(),
    ): SetupStepProgress {
        val step = stepForQuestion(currentQuestionId)
            ?: return SetupStepProgress(origin = SetupProgressOrigin.NOT_CONVERTIBLE)
        val route = stepIds(context)
        if (step !in route) {
            return SetupStepProgress(
                block = blockOf(step),
                currentStepId = step,
                origin = SetupProgressOrigin.NOT_CONVERTIBLE,
            )
        }
        return SetupStepProgress(
            block = blockOf(step),
            stepIndex = route.indexOf(step),
            completedBlocks = blocksCompletedBefore(step, context),
            currentStepId = step,
            visited = route.subList(0, route.indexOf(step) + 1),
            answers = answered,
            origin = SetupProgressOrigin.MIGRATED_FROM_WIZCHAT,
            terminal = step == SetupStepId.REVIEW_ACTIVATE,
        )
    }
}

/** How a persisted progress was produced; legacy drafts are migrated in repair(). */
@Serializable
enum class SetupProgressOrigin { NOT_CONVERTIBLE, MIGRATED_FROM_WIZCHAT, NATIVE }

/**
 * Provenance of a value. Engine/preview results can never be stored as declared
 * answers; only user declarations and accepted suggestions carry DECLARED state.
 */
@Serializable
enum class SetupAnswerProvenance {
    USER_DECLARED,
    SUGGESTED,
    DERIVED,
    ENGINE_RESULT;

    fun canPersistAsDeclared(): Boolean = this == USER_DECLARED || this == SUGGESTED

    companion object {
        fun fromLegacy(source: WizChatAnswerSource): SetupAnswerProvenance = when (source) {
            WizChatAnswerSource.DECLARED -> USER_DECLARED
            WizChatAnswerSource.SUGGESTED_ACCEPTED -> SUGGESTED
            WizChatAnswerSource.IMPORTED -> DERIVED
            WizChatAnswerSource.UNKNOWN -> DERIVED
            WizChatAnswerSource.OMITTED -> USER_DECLARED
        }
    }
}

/** Value status for step and global validation; no silent defaults anywhere. */
@Serializable
enum class SetupValueState { ABSENT, INVALID, DECLARED, ESTIMATED, MISSING_EQUATION_INPUT }

/** One validation finding for one stable step. */
data class SetupFieldCheck(
    val stepId: SetupStepId,
    val key: String,
    val state: SetupValueState,
    val message: String? = null,
) {
    val isBlocking: Boolean
        get() = state == SetupValueState.ABSENT ||
            state == SetupValueState.INVALID ||
            state == SetupValueState.MISSING_EQUATION_INPUT
}

/** Derived previews the wizard may show; they are never answers. */
@Serializable
enum class SetupPreviewKind {
    EER, MACROS, EXPENDITURE, NUTRITION_REFERENCES, NUTRITION_DISTRIBUTION,
    EXERCISES, LOADS, WARMUPS, PLAN_CANDIDATES, SPLIT, RECIPE, MARKS, RINGS_BATTERIES,
}

/** Physiological sources of change; navigation is intentionally not one of them. */
enum class SetupChangeSource { WEIGHT, EQUIPMENT, FREQUENCY, PROTOCOL, PRIORITIES, CALENDAR, SENSATIONS }

/** What has to be revalidated after one [SetupChangeSource]. */
data class SetupDependencyImpact(
    val stalePreviews: Set<SetupPreviewKind> = emptySet(),
    val pendingSteps: Set<SetupStepId> = emptySet(),
    val reorderOnly: Boolean = false,
)

/**
 * Concrete dependency rules: what an earlier change invalidates. Answers that
 * are still valid stay untouched; incompatible selections are only marked
 * pending and dependent previews are invalidated.
 */
object SetupDependencyRules {
    fun impactOf(source: SetupChangeSource): SetupDependencyImpact = when (source) {
        // Peso (y demás vitales de las ecuaciones) → recalcula EER, macros,
        // gasto previsto y referencias.
        SetupChangeSource.WEIGHT -> SetupDependencyImpact(
            stalePreviews = setOf(
                SetupPreviewKind.EER, SetupPreviewKind.MACROS,
                SetupPreviewKind.EXPENDITURE, SetupPreviewKind.NUTRITION_REFERENCES,
            ),
        )
        // Equipo → revalida ejercicios, cargas y calentamientos.
        SetupChangeSource.EQUIPMENT -> SetupDependencyImpact(
            stalePreviews = setOf(SetupPreviewKind.EXERCISES, SetupPreviewKind.LOADS, SetupPreviewKind.WARMUPS),
            pendingSteps = setOf(SetupStepId.PLAN),
        )
        // Frecuencia → revalida días, candidato y split.
        SetupChangeSource.FREQUENCY -> SetupDependencyImpact(
            stalePreviews = setOf(SetupPreviewKind.PLAN_CANDIDATES, SetupPreviewKind.SPLIT),
            pendingSteps = setOf(SetupStepId.WEEKDAYS, SetupStepId.PLAN),
        )
        // Protocolo → revalida marcas, split y receta.
        SetupChangeSource.PROTOCOL -> SetupDependencyImpact(
            stalePreviews = setOf(SetupPreviewKind.MARKS, SetupPreviewKind.SPLIT, SetupPreviewKind.RECIPE),
            pendingSteps = setOf(SetupStepId.TRAINING_MARKS, SetupStepId.PLAN),
        )
        // Prioridades → solo reordena.
        SetupChangeSource.PRIORITIES -> SetupDependencyImpact(reorderOnly = true)
        // Calendario → recalcula el reparto nutricional previsto.
        SetupChangeSource.CALENDAR -> SetupDependencyImpact(
            stalePreviews = setOf(SetupPreviewKind.NUTRITION_DISTRIBUTION),
        )
        // Sensaciones → solo Rings.
        SetupChangeSource.SENSATIONS -> SetupDependencyImpact(
            stalePreviews = setOf(SetupPreviewKind.RINGS_BATTERIES),
        )
    }
}

/**
 * Fingerprint of the inputs that feed previews. Preview invalidation compares
 * footprints, so pure navigation never looks like a physiological change.
 */
data class SetupInputFootprint(
    val weightKg: Double? = null,
    val heightCm: Double? = null,
    val ageYears: Int? = null,
    val gender: String? = null,
    val equipment: Set<String> = emptySet(),
    val trainingEnvironment: String? = null,
    val daysPerWeek: Int? = null,
    val selectedWeekdays: Set<Int> = emptySet(),
    val minutesPerSession: Int? = null,
    val programRoute: String = "",
    val trainingPath: String? = null,
    val goal: String? = null,
    val focus: String? = null,
    val experience: String? = null,
    val volumeStyle: String? = null,
    val volumeResponses: List<Int?> = emptyList(),
    val cardioType: String? = null,
    val cardioMinutes: Int? = null,
    val knowsTrainingMarks: Boolean = false,
    val marks: List<Double?> = emptyList(),
    val selectedCatalogId: String? = null,
    val priorityMuscles: Set<String> = emptySet(),
    val lowerEmphasisMuscles: Set<String> = emptySet(),
    val selectedSplitId: String? = null,
    val customSplitPattern: List<String> = emptyList(),
    val sessionsSignature: List<String> = emptyList(),
    val ringsSignature: List<String> = emptyList(),
    val nutritionSignature: List<String> = emptyList(),
)

/** Detects which physiological changes really happened between two footprints. */
object SetupChangeDetector {
    fun sourcesFor(old: SetupInputFootprint, new: SetupInputFootprint): Set<SetupChangeSource> = buildSet {
        if (old.weightKg != new.weightKg || old.heightCm != new.heightCm || old.ageYears != new.ageYears ||
            old.gender != new.gender || old.nutritionSignature != new.nutritionSignature
        ) add(SetupChangeSource.WEIGHT)
        if (old.equipment != new.equipment || old.trainingEnvironment != new.trainingEnvironment) add(SetupChangeSource.EQUIPMENT)
        if (old.daysPerWeek != new.daysPerWeek) add(SetupChangeSource.FREQUENCY)
        if (old.programRoute != new.programRoute || old.trainingPath != new.trainingPath ||
            old.knowsTrainingMarks != new.knowsTrainingMarks || old.marks != new.marks ||
            old.selectedCatalogId != new.selectedCatalogId || old.minutesPerSession != new.minutesPerSession ||
            old.goal != new.goal || old.focus != new.focus || old.experience != new.experience ||
            old.volumeStyle != new.volumeStyle || old.volumeResponses != new.volumeResponses ||
            old.cardioType != new.cardioType || old.cardioMinutes != new.cardioMinutes
        ) add(SetupChangeSource.PROTOCOL)
        if (old.priorityMuscles != new.priorityMuscles || old.lowerEmphasisMuscles != new.lowerEmphasisMuscles) add(SetupChangeSource.PRIORITIES)
        if (old.selectedWeekdays != new.selectedWeekdays) add(SetupChangeSource.CALENDAR)
        if (old.ringsSignature != new.ringsSignature) add(SetupChangeSource.SENSATIONS)
    }
}

/**
 * Wizard session progress. [currentStepId] is the stable identity; [stepIndex]
 * is only a derived position inside the current conditional route.
 */
@Serializable
data class SetupStepProgress(
    val block: SetupWizardBlock = SetupWizardBlock.BASICS,
    val stepIndex: Int = 0,
    val completedBlocks: Set<SetupWizardBlock> = emptySet(),
    val currentStepId: SetupStepId = SetupStepId.NAME,
    val visited: List<SetupStepId> = emptyList(),
    val answers: Map<SetupStepId, SetupAnswerProvenance> = emptyMap(),
    val pendingReview: Set<SetupStepId> = emptySet(),
    val stalePreviews: Set<SetupPreviewKind> = emptySet(),
    val origin: SetupProgressOrigin = SetupProgressOrigin.NOT_CONVERTIBLE,
    val revision: Int = 0,
    val terminal: Boolean = false,
) {
    companion object {
        fun initial(context: SetupStepContext, origin: SetupProgressOrigin = SetupProgressOrigin.NATIVE): SetupStepProgress {
            val first = SetupStepGraph.first(context)
            return SetupStepProgress(
                block = SetupStepGraph.blockOf(first),
                stepIndex = 0,
                currentStepId = first,
                visited = listOf(first),
                origin = origin,
            )
        }
    }

    /** Moves the cursor to [step]; answers are never removed and completed blocks never shrink. */
    fun at(step: SetupStepId, context: SetupStepContext): SetupStepProgress {
        val route = SetupStepGraph.stepIds(context)
        if (step !in route) return this
        return copy(
            block = SetupStepGraph.blockOf(step),
            stepIndex = route.indexOf(step),
            completedBlocks = completedBlocks + SetupStepGraph.blocksCompletedBefore(step, context),
            currentStepId = step,
            visited = if (step in visited) visited else visited + step,
            revision = revision + 1,
            terminal = step == SetupStepId.REVIEW_ACTIVATE,
        )
    }

    /** Records provenance for one step; engine results can never be declared answers. */
    fun recordAnswer(step: SetupStepId, provenance: SetupAnswerProvenance, valueState: SetupValueState): SetupStepProgress {
        require(valueState != SetupValueState.DECLARED || provenance.canPersistAsDeclared()) {
            "Un preview o resultado de motor nunca se guarda como valor declarado"
        }
        return copy(answers = answers + (step to provenance), revision = revision + 1)
    }

    fun withPendingReview(steps: Set<SetupStepId>): SetupStepProgress =
        if (steps.isEmpty()) this else copy(pendingReview = pendingReview + steps)

    fun reviewDone(step: SetupStepId): SetupStepProgress =
        copy(pendingReview = pendingReview - step)

    fun withStalePreviews(kinds: Set<SetupPreviewKind>): SetupStepProgress =
        if (kinds.isEmpty()) this else copy(stalePreviews = stalePreviews + kinds)

    fun previewsComputed(kinds: Set<SetupPreviewKind>): SetupStepProgress =
        copy(stalePreviews = stalePreviews - kinds)
}
