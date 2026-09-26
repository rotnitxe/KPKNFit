package com.example.kpkn.domain.onboarding

import kotlinx.serialization.Serializable

/**
 * Pure step graph for the traditional setup wizard that replaces the WizChat
 * conversational orchestration.
 *
 * The flow is organised as four mandatory blocks plus a closing review:
 * Datos básicos → Entreno → Nutrición → Rings → Revisión y activación.
 *
 * Step identity is always the stable [SetupStepId]; [SetupStepProgress.stepIndex]
 * is a derived position inside the current route and must never be used as the
 * only identity, because conditional branches insert or drop steps.
 *
 * Legacy rules kept for read/migration compatibility only:
 * - [SetupStepId.GENDER], [SetupStepId.HOME_EQUIPMENT], [SetupStepId.NUTRITION_SEX]
 *   and [SetupStepId.RINGS_START] survive in the enum and in [blocks] so persisted
 *   drafts keep deserializing, but none of them appears in a productive route.
 * - Gender identity is NEVER converted into equation sex. A legacy N_SEX answer
 *   migrates into [SetupStepId.EQUATION_SEX] only when it is explicit
 *   (Femenino/Masculino); "Prefiero no responder" stays pending.
 * - Unknown steps after a migration stay PENDING: finishing a legacy block never
 *   completes the new milestones by ordinal or progress.
 */

@Serializable
enum class SetupWizardBlock { BASICS, TRAINING, NUTRITION, RINGS, REVIEW }

@Serializable
enum class SetupStepKind { QUESTION, MILESTONE, REVIEW }

@Serializable
enum class SetupStepId {
    // Datos básicos (bloque siempre presente cuando hay entreno o nutrición)
    NAME, AGE, HEIGHT, WEIGHT, EQUATION_SEX, BODY_FAT, MILESTONE_BASICS,
    // Entreno
    EXPERIENCE, ROUTE, GOAL, STYLE, VOLUME_TECHNIQUE, VOLUME_CONSISTENCY,
    VOLUME_STRENGTH, VOLUME_MOBILITY, EQUIPMENT, INVENTORY_BARBELL, INVENTORY_PLATES,
    INVENTORY_DUMBBELLS, INVENTORY_KETTLEBELLS, INVENTORY_MACHINES, DAYS, WEEKDAYS,
    SESSION_TIME, CARDIO_TYPE, CARDIO_TIME, PRIORITIES, SPLIT, PLAN, TRAINING_MAX,
    TRAINING_MARKS, AUTOREGULATION, AUTOREGULATION_CONFIRM, WARMUPS, TRAINING_REVIEW,
    MILESTONE_TRAINING,
    // Nutrición
    NUTRITION_START, NUTRITION_ELIGIBILITY, NUTRITION_DIRECTION, NUTRITION_RHYTHM,
    NUTRITION_TARGET, NUTRITION_HISTORY_CONTEXT, NUTRITION_ACTIVITY,
    NUTRITION_MANUAL_CALORIES, NUTRITION_MANUAL_CARBS_FAT,
    NUTRITION_DISTRIBUTION, NUTRITION_WEIGH_INS, NUTRITION_RESULT, MILESTONE_NUTRITION,
    // Rings
    RINGS_RECENT, RINGS_SESSIONS, RINGS_RECENCY, RINGS_ACTIVITY, RINGS_INTENSITY,
    RINGS_AXIAL, RINGS_MUSCLE_FEELING, RINGS_ENERGY_FEELING, RINGS_STRUCTURE_FEELING,
    RINGS_DISCOMFORT, RINGS_RESULT, MILESTONE_RINGS,
    // Revisión y activación
    REVIEW_ACTIVATE,

    // -------------------------------------------------------------
    // Legacy-only (fuera de la ruta productiva; solo deserialización)
    // -------------------------------------------------------------
    @Deprecated("Identidad de género de flujos legacy: nunca se convierte en sexo de cálculo")
    GENDER,
    @Deprecated("Material en casa de flujos legacy: reemplazado por los pasos INVENTORY_*")
    HOME_EQUIPMENT,
    @Deprecated("Sexo de nutrición legacy: los valores explícitos migran a EQUATION_SEX")
    NUTRITION_SEX,
    @Deprecated("Acción de inicio de Rings legacy: el bloque RINGS es obligatorio")
    RINGS_START,
    /** Material disponible por categorías, sin inventario de kilos ni cantidades. */
    AVAILABILITY,
}

data class SetupStepNode(
    val id: SetupStepId,
    val block: SetupWizardBlock,
    val kind: SetupStepKind,
)

/**
 * Minimal routing context; it deliberately contains no UI state. New routes are
 * derived from this context with compatible defaults, so a default context
 * produces the full normal sign-up (nothing is skipped).
 */
data class SetupStepContext(
    val includeTraining: Boolean = true,
    val includeNutrition: Boolean = true,
    val includeRings: Boolean = true,
    // Legacy read compatibility (still produced by old scopes/contexts).
    val programRouteLater: Boolean = false,
    val homeEquipmentSelected: Boolean = false,
    val mixedTraining: Boolean = false,
    val hasTrainingMarks: Boolean = false,
    val goalStyleInferred: Boolean = false,
    val nutritionProfessional: Boolean = false,
    /**
     * false only hides the nutrition CHAIN until an explicit choice exists;
     * it never removes the block (NUTRITION_START stays in the route).
     */
    val nutritionStarted: Boolean = true,
    val ringsAction: String? = null,
    val recentTraining: Boolean? = null,
    // New minimal route data (defaults keep the normal FULL route intact).
    /** Inventory groups whose steps are included, derived from the environment. */
    val inventoryGroups: Set<SetupInventoryGroup> = emptySet(),
    /** Categorías de material (gimnasio, casa o máquinas), sin cuestionario de stock. */
    val asksAvailability: Boolean = false,
    /** Cardio branch (goal mixto o elección explícita de incluir cardio). */
    val wantsCardio: Boolean = false,
    /** NUTRITION_START: automatic | self_defined | tracking_only. */
    val nutritionStartChoice: String? = null,
    /** NUTRITION_DIRECTION: deficit | maintenance | surplus; drives NUTRITION_RHYTHM. */
    val nutritionDirection: String? = null,
    /** AUTOREGULATION on → the explicit confirmation step is inserted. */
    val autoregulationOn: Boolean = false,
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
        SetupStepId.AGE to SetupWizardBlock.BASICS,
        SetupStepId.HEIGHT to SetupWizardBlock.BASICS,
        SetupStepId.WEIGHT to SetupWizardBlock.BASICS,
        SetupStepId.EQUATION_SEX to SetupWizardBlock.BASICS,
        SetupStepId.BODY_FAT to SetupWizardBlock.BASICS,
        SetupStepId.MILESTONE_BASICS to SetupWizardBlock.BASICS,
        SetupStepId.EXPERIENCE to SetupWizardBlock.TRAINING,
        SetupStepId.ROUTE to SetupWizardBlock.TRAINING,
        SetupStepId.GOAL to SetupWizardBlock.TRAINING,
        SetupStepId.STYLE to SetupWizardBlock.TRAINING,
        SetupStepId.VOLUME_TECHNIQUE to SetupWizardBlock.TRAINING,
        SetupStepId.VOLUME_CONSISTENCY to SetupWizardBlock.TRAINING,
        SetupStepId.VOLUME_STRENGTH to SetupWizardBlock.TRAINING,
        SetupStepId.VOLUME_MOBILITY to SetupWizardBlock.TRAINING,
        SetupStepId.EQUIPMENT to SetupWizardBlock.TRAINING,
        SetupStepId.AVAILABILITY to SetupWizardBlock.TRAINING,
        SetupStepId.INVENTORY_BARBELL to SetupWizardBlock.TRAINING,
        SetupStepId.INVENTORY_PLATES to SetupWizardBlock.TRAINING,
        SetupStepId.INVENTORY_DUMBBELLS to SetupWizardBlock.TRAINING,
        SetupStepId.INVENTORY_KETTLEBELLS to SetupWizardBlock.TRAINING,
        SetupStepId.INVENTORY_MACHINES to SetupWizardBlock.TRAINING,
        SetupStepId.DAYS to SetupWizardBlock.TRAINING,
        SetupStepId.WEEKDAYS to SetupWizardBlock.TRAINING,
        SetupStepId.SESSION_TIME to SetupWizardBlock.TRAINING,
        SetupStepId.CARDIO_TYPE to SetupWizardBlock.TRAINING,
        SetupStepId.CARDIO_TIME to SetupWizardBlock.TRAINING,
        SetupStepId.PRIORITIES to SetupWizardBlock.TRAINING,
        SetupStepId.SPLIT to SetupWizardBlock.TRAINING,
        SetupStepId.PLAN to SetupWizardBlock.TRAINING,
        SetupStepId.TRAINING_MAX to SetupWizardBlock.TRAINING,
        SetupStepId.TRAINING_MARKS to SetupWizardBlock.TRAINING,
        SetupStepId.AUTOREGULATION to SetupWizardBlock.TRAINING,
        SetupStepId.AUTOREGULATION_CONFIRM to SetupWizardBlock.TRAINING,
        SetupStepId.WARMUPS to SetupWizardBlock.TRAINING,
        SetupStepId.TRAINING_REVIEW to SetupWizardBlock.TRAINING,
        SetupStepId.MILESTONE_TRAINING to SetupWizardBlock.TRAINING,
        SetupStepId.NUTRITION_START to SetupWizardBlock.NUTRITION,
        SetupStepId.NUTRITION_ELIGIBILITY to SetupWizardBlock.NUTRITION,
        SetupStepId.NUTRITION_DIRECTION to SetupWizardBlock.NUTRITION,
        SetupStepId.NUTRITION_RHYTHM to SetupWizardBlock.NUTRITION,
        SetupStepId.NUTRITION_TARGET to SetupWizardBlock.NUTRITION,
        SetupStepId.NUTRITION_HISTORY_CONTEXT to SetupWizardBlock.NUTRITION,
        SetupStepId.NUTRITION_ACTIVITY to SetupWizardBlock.NUTRITION,
        SetupStepId.NUTRITION_MANUAL_CALORIES to SetupWizardBlock.NUTRITION,
        SetupStepId.NUTRITION_MANUAL_CARBS_FAT to SetupWizardBlock.NUTRITION,
        SetupStepId.NUTRITION_DISTRIBUTION to SetupWizardBlock.NUTRITION,
        SetupStepId.NUTRITION_WEIGH_INS to SetupWizardBlock.NUTRITION,
        SetupStepId.NUTRITION_RESULT to SetupWizardBlock.NUTRITION,
        SetupStepId.MILESTONE_NUTRITION to SetupWizardBlock.NUTRITION,
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
        // Legacy read-only steps keep their block so deserialized progress stays coherent.
        SetupStepId.GENDER to SetupWizardBlock.BASICS,
        SetupStepId.HOME_EQUIPMENT to SetupWizardBlock.TRAINING,
        SetupStepId.NUTRITION_SEX to SetupWizardBlock.NUTRITION,
        SetupStepId.RINGS_START to SetupWizardBlock.RINGS,
    )

    private val milestones: Map<SetupWizardBlock, SetupStepId> = mapOf(
        SetupWizardBlock.BASICS to SetupStepId.MILESTONE_BASICS,
        SetupWizardBlock.TRAINING to SetupStepId.MILESTONE_TRAINING,
        SetupWizardBlock.NUTRITION to SetupStepId.MILESTONE_NUTRITION,
        SetupWizardBlock.RINGS to SetupStepId.MILESTONE_RINGS,
    )

    /** Legacy question rendered as a 1:1 copy of a step (read/migration only). */
    private val renderQuestions: Map<SetupStepId, WizChatQuestionId> = SetupStepDefinitions.legacyRenderable

    /** Legacy question -> stable step for migration; includes N_SEX -> EQUATION_SEX. */
    private val migrateTargets: Map<WizChatQuestionId, SetupStepId> = SetupStepDefinitions.legacyMigrationTargets

    /** Anchor that mimics the legacy WizChat order; used only to resume unmapped steps. */
    private const val ANCHOR_SCALE = 100

    private val extraAnchors: Map<SetupStepId, Int> = mapOf(
        SetupStepId.EQUATION_SEX to 410,
        SetupStepId.BODY_FAT to 420,
        SetupStepId.INVENTORY_BARBELL to 1310,
        SetupStepId.INVENTORY_PLATES to 1320,
        SetupStepId.INVENTORY_DUMBBELLS to 1330,
        SetupStepId.INVENTORY_KETTLEBELLS to 1340,
        SetupStepId.INVENTORY_MACHINES to 1350,
        SetupStepId.AVAILABILITY to 1360,
        SetupStepId.PRIORITIES to 1910,
        SetupStepId.SPLIT to 1920,
        SetupStepId.AUTOREGULATION to 2150,
        SetupStepId.AUTOREGULATION_CONFIRM to 2160,
        SetupStepId.WARMUPS to 2170,
        SetupStepId.NUTRITION_RHYTHM to 2750,
        SetupStepId.NUTRITION_TARGET to 2760,
        SetupStepId.NUTRITION_HISTORY_CONTEXT to 2770,
        SetupStepId.NUTRITION_MANUAL_CALORIES to 2810,
        SetupStepId.NUTRITION_MANUAL_CARBS_FAT to 2820,
        SetupStepId.NUTRITION_DISTRIBUTION to 2830,
        SetupStepId.NUTRITION_WEIGH_INS to 2850,
    )

    /**
     * Anchor that mimics the legacy WizChat order of each step. Steps that carry
     * a legacy question keep its ordinal even when the question does not render
     * as this step's copy (e.g. NUTRITION_RESULT), so a legacy draft past that
     * position resumes inside the block instead of jumping to the terminal
     * steps; new steps use [extraAnchors].
     */
    private fun legacyAnchor(step: SetupStepId): Int =
        SetupStepDefinitions.of(step)?.legacyQuestion?.ordinal?.times(ANCHOR_SCALE)
            ?: extraAnchors[step]
            ?: Int.MAX_VALUE

    fun blockOf(step: SetupStepId): SetupWizardBlock = blocks[step] ?: SetupWizardBlock.REVIEW

    fun isMilestone(step: SetupStepId): Boolean = milestones.values.contains(step)

    fun milestoneOf(block: SetupWizardBlock): SetupStepId? = milestones[block]

    /** Legacy question this step still renders as a 1:1 copy; null for new steps. */
    fun questionForStep(step: SetupStepId): WizChatQuestionId? = renderQuestions[step]

    /** Migration bridge: the stable step behind a legacy question id. */
    fun stepForQuestion(question: WizChatQuestionId): SetupStepId? = migrateTargets[question]

    /**
     * True when a legacy answer is explicit enough to migrate into [step].
     *
     * Contract:
     * - OMITTED, UNKNOWN and IMPORTED sources are never explicit: they are not
     *   user declarations of the new flow and must never look "declared".
     * - Only mapped labels count for CHOICE/TOGGLE answers; any other non-blank
     *   label stays PENDING ("Lo decidiré después", "Hipertrofia"…).
     * - MULTI answers are explicit only when EVERY selected value maps: a
     *   partially valid selection stays PENDING instead of silently keeping
     *   half of it (the raw payload is preserved in the legacy mirror).
     * - PLAN-style steps declare [SetupStepDefinition.legacyAcceptsIdValue] and
     *   their answer value is the generated candidate id itself.
     * - Free TEXT is its own value; a NUMBER must be finite (never NaN/∞).
     * - ACTION only counts where the action IS the confirmation (result
     *   previews and the closing review) or when it carries its payload
     *   (marks rows); a bare action on any other step stays PENDING.
     */
    fun isExplicitLegacy(
        questionId: WizChatQuestionId,
        record: com.example.kpkn.domain.onboarding.WizChatAnswerRecord,
    ): Boolean {
        if (record.source == WizChatAnswerSource.OMITTED ||
            record.source == WizChatAnswerSource.UNKNOWN ||
            record.source == WizChatAnswerSource.IMPORTED
        ) return false
        val step = migrateTargets[questionId] ?: return false
        val definition = SetupStepDefinitions.of(step) ?: return false
        // N_SEX -> EQUATION_SEX: only explicit values migrate; the neutral option stays pending.
        if (questionId == WizChatQuestionId.N_SEX) {
            return record.textValue in setOf("Femenino", "Masculino")
        }
        return when (record.kind) {
            WizChatAnswerKind.NUMBER -> record.numberValue?.isFinite() == true
            WizChatAnswerKind.MULTI_CHOICE ->
                when {
                    record.values.isEmpty() -> false
                    definition.legacyAcceptsIdValue -> record.values.all { it.isNotBlank() }
                    // Todos los valores deben estar mapeados: nada de "cualquiera válida".
                    else -> record.values.all { definition.migratedValue(it) != null }
                }
            WizChatAnswerKind.ACTION -> when (definition.control) {
                // La acción ES la confirmación de estos pasos.
                SetupControlKind.RESULT_PREVIEW, SetupControlKind.REVIEW -> true
                // El resto solo si la acción trae su payload (p. ej. las marcas).
                else -> !record.textValue.isNullOrBlank() || record.values.isNotEmpty()
            }
            WizChatAnswerKind.TEXT -> !record.textValue.isNullOrBlank()
            WizChatAnswerKind.CHOICE, WizChatAnswerKind.TOGGLE ->
                definition.migratedValue(record.textValue) != null ||
                    (definition.legacyAcceptsIdValue && !record.textValue.isNullOrBlank())
        }
    }

    /** Full conditional route: block steps, milestones between blocks and final review. */
    fun nodes(context: SetupStepContext): List<SetupStepNode> = buildList {
        // Bloque 1: Datos básicos. Obligatorio siempre que haya entreno o nutrición;
        // los scopes antiguos (solo rings) conservan su arranque sin vitales.
        if (context.includeTraining || context.includeNutrition) {
            addAll(listOf(
                SetupStepId.NAME, SetupStepId.AGE, SetupStepId.HEIGHT, SetupStepId.WEIGHT,
                SetupStepId.EQUATION_SEX, SetupStepId.BODY_FAT,
            ))
            add(SetupStepId.MILESTONE_BASICS)
        }
        // Bloque 2: Entreno
        if (context.includeTraining) {
            add(SetupStepId.EXPERIENCE)
            add(SetupStepId.ROUTE)
            add(SetupStepId.GOAL)
            // El estilo solo se pregunta si el objetivo no lo infiere.
            if (!context.goalStyleInferred) add(SetupStepId.STYLE)
            addAll(listOf(
                SetupStepId.VOLUME_TECHNIQUE, SetupStepId.VOLUME_CONSISTENCY,
                SetupStepId.VOLUME_STRENGTH, SetupStepId.VOLUME_MOBILITY,
            ))
            add(SetupStepId.EQUIPMENT)
            if (context.asksAvailability) add(SetupStepId.AVAILABILITY)
            context.inventoryGroups.sortedBy(SetupStepDefinitions::stepOf).forEach { group ->
                add(SetupStepDefinitions.stepOf(group))
            }
            addAll(listOf(SetupStepId.DAYS, SetupStepId.WEEKDAYS, SetupStepId.SESSION_TIME))
            if (context.wantsCardio || context.mixedTraining) {
                addAll(listOf(SetupStepId.CARDIO_TYPE, SetupStepId.CARDIO_TIME))
            }
            add(SetupStepId.PRIORITIES)
            add(SetupStepId.SPLIT)
            // El plan aparece antes de marcas: un candidato es válido sin marcas completas.
            add(SetupStepId.PLAN)
            add(SetupStepId.TRAINING_MAX)
            if (context.hasTrainingMarks) add(SetupStepId.TRAINING_MARKS)
            add(SetupStepId.AUTOREGULATION)
            if (context.autoregulationOn) add(SetupStepId.AUTOREGULATION_CONFIRM)
            add(SetupStepId.WARMUPS)
            add(SetupStepId.TRAINING_REVIEW)
            add(SetupStepId.MILESTONE_TRAINING)
        }
        // Bloque 3: Nutrición. El arranque es siempre explícito: un estado
        // "sin iniciar" (nutritionStarted=false) NUNCA desmonta el bloque
        // entero; solo oculta la cadena hasta que la elección exista. La única
        // rama que salta referencias es la elección explícita "solo registrar".
        if (context.includeNutrition) {
            add(SetupStepId.NUTRITION_START)
            val chainVisible = context.nutritionStarted || context.nutritionProfessional ||
                context.nutritionStartChoice != null
            if (chainVisible) {
                when {
                    context.nutritionProfessional -> add(SetupStepId.NUTRITION_RESULT)
                    context.nutritionStartChoice == "tracking_only" -> add(SetupStepId.NUTRITION_RESULT)
                    context.nutritionStartChoice == "self_defined" -> {
                        // Modo manual: NO se pregunta la cadena EER (elegibilidad ni
                        // actividad); solo dirección + números explícitos del usuario.
                        add(SetupStepId.NUTRITION_DIRECTION)
                        if (context.nutritionDirection != null && context.nutritionDirection != "maintenance") {
                            add(SetupStepId.NUTRITION_RHYTHM)
                        }
                        add(SetupStepId.NUTRITION_TARGET)
                        add(SetupStepId.NUTRITION_HISTORY_CONTEXT)
                        add(SetupStepId.NUTRITION_MANUAL_CALORIES)
                        add(SetupStepId.NUTRITION_MANUAL_CARBS_FAT)
                    }
                    else -> {
                        // Modo automático: cadena EER completa más contexto de objetivo.
                        add(SetupStepId.NUTRITION_ELIGIBILITY)
                        add(SetupStepId.NUTRITION_DIRECTION)
                        if (context.nutritionDirection != null && context.nutritionDirection != "maintenance") {
                            add(SetupStepId.NUTRITION_RHYTHM)
                        }
                        add(SetupStepId.NUTRITION_TARGET)
                        add(SetupStepId.NUTRITION_HISTORY_CONTEXT)
                        add(SetupStepId.NUTRITION_ACTIVITY)
                    }
                }
                // El reparto, los pesajes y el resultado siguen en ambos modos no-tracking.
                if (context.nutritionStartChoice != "tracking_only" && !context.nutritionProfessional) {
                    add(SetupStepId.NUTRITION_DISTRIBUTION)
                    add(SetupStepId.NUTRITION_WEIGH_INS)
                    add(SetupStepId.NUTRITION_RESULT)
                }
                add(SetupStepId.MILESTONE_NUTRITION)
            }
        }
        // Bloque 4: Rings (obligatorio; se pregunta siempre, sin salto inicial)
        if (context.includeRings) {
            add(SetupStepId.RINGS_RECENT)
            // Evidencia de historial SOLO si el entrenamiento reciente es conocido("sí").
            if (context.recentTraining == true) {
                addAll(listOf(
                    SetupStepId.RINGS_SESSIONS, SetupStepId.RINGS_RECENCY, SetupStepId.RINGS_ACTIVITY,
                    SetupStepId.RINGS_INTENSITY, SetupStepId.RINGS_AXIAL,
                ))
            }
            // Sensaciones y molestias SIEMPRE: desconocer el historial no es no entrenar.
            addAll(listOf(
                SetupStepId.RINGS_MUSCLE_FEELING, SetupStepId.RINGS_ENERGY_FEELING,
                SetupStepId.RINGS_STRUCTURE_FEELING, SetupStepId.RINGS_DISCOMFORT,
            ))
            add(SetupStepId.RINGS_RESULT)
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
     * user never saw — but only steps that still belong to the current route
     * are valid targets: after a branch change (marks off, cardio dropped…)
     * back never returns a removed step. The visited trail is an identity trail
     * (unique steps), not a full back-stack: coming back from a review edit is
     * owned by the caller (editStep returns to the review). Back never removes
     * answers.
     */
    fun previous(step: SetupStepId, context: SetupStepContext, visited: List<SetupStepId> = emptyList()): SetupStepId? {
        val route = stepIds(context)
        // Un paso fuera de la ruta nunca genera navegación.
        if (step !in route) return null
        val visitedIndex = visited.indexOfLast { it == step }
        if (visitedIndex >= 0) {
            for (index in visitedIndex - 1 downTo 0) {
                val candidate = visited[index]
                if (candidate in route) return candidate
            }
            // Todo lo visitado anterior quedó fuera de la ruta: cae al orden real.
        }
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

    /**
     * Blocks truly completed: a block is complete only when its milestone has
     * a USER-CONFIRMED answer (USER_DECLARED or accepted SUGGESTED) inside
     * [answered], the milestone still belongs to the current route AND the
     * block is not flagged for review. Engine results, derived previews and
     * other non-confirmations never complete anything, merely visiting the
     * milestone never completes anything, and a block whose steps are marked
     * in [pendingReview] stops being "done" until the milestone is confirmed
     * again — without ever deleting answer data.
     *
     * Completion is DERIVED on every call, so it can shrink when inputs
     * invalidate the block milestone; callers never keep a stale union.
     */
    fun confirmedBlocks(
        answered: Map<SetupStepId, SetupAnswerProvenance>,
        context: SetupStepContext,
        pendingReview: Set<SetupStepId> = emptySet(),
    ): Set<SetupWizardBlock> {
        val route = stepIds(context).toSet()
        val underReview = pendingReview.mapTo(mutableSetOf()) { blockOf(it) }
        return milestones.entries
            .filter { (block, milestone) ->
                block !in underReview &&
                    answered[milestone]?.canPersistAsDeclared() == true &&
                    milestone in route
            }
            .mapTo(mutableSetOf()) { (block, _) -> block }
    }

    /** Legacy question accepted by the scope; unmatching scopes are NOT_CONVERTIBLE. */
    private fun scopeAccepts(questionId: WizChatQuestionId, context: SetupStepContext): Boolean =
        when (WizChatGraph.stageFor(questionId)) {
            WizChatStage.PROFILE -> context.includeTraining || context.includeNutrition
            WizChatStage.TRAINING -> context.includeTraining
            WizChatStage.NUTRITION -> context.includeNutrition
            WizChatStage.RINGS -> context.includeRings
            WizChatStage.REVIEW -> true
        }

    /**
     * First route step still pending after the legacy position of [current].
     * New steps behave as pending even when their legacy sibling was answered;
     * milestones and the terminal review are never chosen as resume points
     * (falling back to REVIEW only when everything else is answered).
     */
    private fun firstPendingAfter(
        current: WizChatQuestionId,
        route: List<SetupStepId>,
        answered: Map<SetupStepId, SetupAnswerProvenance>,
    ): SetupStepId? {
        val anchor = current.ordinal * ANCHOR_SCALE
        val startIndex = route.indexOfFirst {
            !isMilestone(it) && it != SetupStepId.REVIEW_ACTIVATE && legacyAnchor(it) > anchor
        }
        val after = if (startIndex < 0) null else route.drop(startIndex).firstOrNull { it !in answered }
        if (after != null) return after
        // Nada pendiente después: nunca se salta un paso pendiente anterior al ancla.
        return route.firstOrNull { it !in answered && !isMilestone(it) && it != SetupStepId.REVIEW_ACTIVATE }
            ?: route.firstOrNull { it !in answered }
            ?: SetupStepId.REVIEW_ACTIVATE
    }

    /**
     * Migration from a legacy WizChat draft: maps [currentQuestionId] to the
     * stable step. When the legacy question cannot be placed inside the route
     * for this context (wrong scope), returns a progress marked
     * [SetupProgressOrigin.NOT_CONVERTIBLE]; the caller must keep the legacy
     * data untouched.
     *
     * Resume placement:
     * - Exact resume when the mapped step is still part of the route.
     * - Otherwise the first pending route step after the legacy position
     *   (e.g. a legacy draft at T_MARKS whose route does not include
     *   TRAINING_MARKS resumes at the next training step, and the marks
     *   stay unmigrated).
     *
     * Completion is never derived from ordinal or progress: the migrated
     * progress starts with no completed block; the user confirms each milestone
     * in the new flow.
     */
    fun migrateFromLegacy(
        currentQuestionId: WizChatQuestionId,
        context: SetupStepContext,
        answered: Map<SetupStepId, SetupAnswerProvenance> = emptyMap(),
    ): SetupStepProgress {
        if (!scopeAccepts(currentQuestionId, context)) {
            val mapped = migrateTargets[currentQuestionId]
            return SetupStepProgress(
                block = mapped?.let(::blockOf) ?: SetupWizardBlock.BASICS,
                currentStepId = mapped ?: SetupStepId.NAME,
                origin = SetupProgressOrigin.NOT_CONVERTIBLE,
            )
        }
        val route = stepIds(context)
        val target = migrateTargets[currentQuestionId]
            ?.takeIf { it in route }
            ?: firstPendingAfter(currentQuestionId, route, answered)
            ?: return SetupStepProgress(origin = SetupProgressOrigin.NOT_CONVERTIBLE)
        return SetupStepProgress(
            block = blockOf(target),
            stepIndex = route.indexOf(target),
            completedBlocks = emptySet(),
            currentStepId = target,
            visited = emptyList(),
            answers = answered,
            origin = SetupProgressOrigin.MIGRATED_FROM_WIZCHAT,
            terminal = target == SetupStepId.REVIEW_ACTIVATE,
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
        /**
         * Only a DECLARED or accepted suggestion can become a declared answer.
         * IMPORTED and UNKNOWN payloads are derived data and OMITTED answers
         * are no answer at all: none of them is ever "declared" here.
         */
        fun fromLegacy(source: WizChatAnswerSource): SetupAnswerProvenance = when (source) {
            WizChatAnswerSource.DECLARED -> USER_DECLARED
            WizChatAnswerSource.SUGGESTED_ACCEPTED -> SUGGESTED
            WizChatAnswerSource.IMPORTED -> DERIVED
            WizChatAnswerSource.UNKNOWN -> DERIVED
            WizChatAnswerSource.OMITTED -> DERIVED
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
enum class SetupChangeSource { WEIGHT, BODY_COMPOSITION, EQUIPMENT, FREQUENCY, PROTOCOL, PRIORITIES, CALENDAR, SENSATIONS }

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
        // Vitales de las ecuaciones (peso, estatura, edad, sexo de cálculo,
        // modo/dirección nutricional) → recalcula EER, macros, gasto,
        // referencias y reparto.
        SetupChangeSource.WEIGHT -> SetupDependencyImpact(
            stalePreviews = setOf(
                SetupPreviewKind.EER, SetupPreviewKind.MACROS,
                SetupPreviewKind.EXPENDITURE, SetupPreviewKind.NUTRITION_REFERENCES,
                SetupPreviewKind.NUTRITION_DISTRIBUTION,
            ),
        )
        // Grasa corporal → solo las referencias que la usan.
        SetupChangeSource.BODY_COMPOSITION -> SetupDependencyImpact(
            stalePreviews = setOf(
                SetupPreviewKind.EER, SetupPreviewKind.MACROS,
                SetupPreviewKind.EXPENDITURE, SetupPreviewKind.NUTRITION_REFERENCES,
            ),
        )
        // Equipo/inventario → revalida ejercicios, cargas y calentamientos.
        SetupChangeSource.EQUIPMENT -> SetupDependencyImpact(
            stalePreviews = setOf(SetupPreviewKind.EXERCISES, SetupPreviewKind.LOADS, SetupPreviewKind.WARMUPS),
            pendingSteps = setOf(SetupStepId.PLAN),
        )
        // Frecuencia → revalida días, candidato y split.
        SetupChangeSource.FREQUENCY -> SetupDependencyImpact(
            stalePreviews = setOf(SetupPreviewKind.PLAN_CANDIDATES, SetupPreviewKind.SPLIT),
            pendingSteps = setOf(SetupStepId.WEEKDAYS, SetupStepId.PLAN),
        )
        // Protocolo (objetivo, experiencia, marcas, plan, autoregulación…) →
        // revalida marcas, split, receta y selección.
        SetupChangeSource.PROTOCOL -> SetupDependencyImpact(
            stalePreviews = setOf(SetupPreviewKind.MARKS, SetupPreviewKind.SPLIT, SetupPreviewKind.RECIPE),
            pendingSteps = setOf(SetupStepId.TRAINING_MAX, SetupStepId.TRAINING_MARKS, SetupStepId.PLAN),
        )
        // Prioridades → solo reordena ejercicios, nunca los cambia.
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
    val equationSex: String? = null,
    val bodyFatPercent: Double? = null,
    val equipment: Set<String> = emptySet(),
    val trainingEnvironment: String? = null,
    val inventory: Set<String> = emptySet(),
    /** Null = unanswered/legacy; empty = explicitly no declared categories. */
    val equipmentAvailability: Set<String>? = null,
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
    val priorityPoints: Map<String, Int> = emptyMap(),
    val selectedSplitId: String? = null,
    val customSplitPattern: List<String> = emptyList(),
    val autoregulationMode: String? = null,
    val warmupsPreference: String? = null,
    val sessionsSignature: List<String> = emptyList(),
    val ringsSignature: List<String> = emptyList(),
    val nutritionSignature: List<String> = emptyList(),
    val nutritionStartChoice: String? = null,
    val nutritionDirection: String? = null,
    val nutritionDistribution: String? = null,
    /** NUTRITION_TARGET: peso meta opcional (kg). */
    val nutritionTargetKg: Double? = null,
    /** NUTRITION_HISTORY_CONTEXT: tendencia + máximo anterior (no registros). */
    val nutritionHistorySignature: String? = null,
)

/** Detects which physiological changes really happened between two footprints. */
object SetupChangeDetector {
    fun sourcesFor(old: SetupInputFootprint, new: SetupInputFootprint): Set<SetupChangeSource> = buildSet {
        if (old.weightKg != new.weightKg || old.heightCm != new.heightCm || old.ageYears != new.ageYears ||
            old.gender != new.gender || old.equationSex != new.equationSex ||
            old.nutritionSignature != new.nutritionSignature ||
            old.nutritionStartChoice != new.nutritionStartChoice ||
            old.nutritionDirection != new.nutritionDirection ||
            old.nutritionDistribution != new.nutritionDistribution ||
            old.nutritionTargetKg != new.nutritionTargetKg ||
            old.nutritionHistorySignature != new.nutritionHistorySignature
        ) add(SetupChangeSource.WEIGHT)
        if (old.bodyFatPercent != new.bodyFatPercent) add(SetupChangeSource.BODY_COMPOSITION)
        if (old.equipment != new.equipment || old.trainingEnvironment != new.trainingEnvironment ||
            old.inventory != new.inventory || old.equipmentAvailability != new.equipmentAvailability
        ) add(SetupChangeSource.EQUIPMENT)
        if (old.daysPerWeek != new.daysPerWeek) add(SetupChangeSource.FREQUENCY)
        if (old.programRoute != new.programRoute || old.trainingPath != new.trainingPath ||
            old.knowsTrainingMarks != new.knowsTrainingMarks || old.marks != new.marks ||
            old.selectedCatalogId != new.selectedCatalogId || old.minutesPerSession != new.minutesPerSession ||
            old.goal != new.goal || old.focus != new.focus || old.experience != new.experience ||
            old.volumeStyle != new.volumeStyle || old.volumeResponses != new.volumeResponses ||
            old.cardioType != new.cardioType || old.cardioMinutes != new.cardioMinutes ||
            old.autoregulationMode != new.autoregulationMode ||
            old.warmupsPreference != new.warmupsPreference
        ) add(SetupChangeSource.PROTOCOL)
        if (old.priorityMuscles != new.priorityMuscles || old.lowerEmphasisMuscles != new.lowerEmphasisMuscles ||
            old.priorityPoints != new.priorityPoints
        ) add(SetupChangeSource.PRIORITIES)
        if (old.selectedWeekdays != new.selectedWeekdays) add(SetupChangeSource.CALENDAR)
        if (old.ringsSignature != new.ringsSignature) add(SetupChangeSource.SENSATIONS)
    }
}

/**
 * Wizard session progress. [currentStepId] is the stable identity; [stepIndex]
 * is only a derived position inside the current conditional route.
 *
 * [completedBlocks] only contains blocks whose milestone answer was confirmed
 * (answer record), never blocks inferred from position or navigation, and it
 * drops again when inputs flag the block for review (see [withPendingReview]).
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

    /**
     * Moves the cursor to [step]; answers are never removed. Completion is
     * derived, never accumulated: a block counts only while its milestone has
     * an answer record (see [SetupStepGraph.confirmedBlocks]), is inside the
     * current route and is not flagged for review, so visiting or passing a
     * milestone never completes anything and an invalidated block never stays
     * "done".
     *
     * [visited] is a unique identity trail (not a full back-stack): review
     * edits rely on the caller returning to the review.
     */
    fun at(step: SetupStepId, context: SetupStepContext): SetupStepProgress {
        val route = SetupStepGraph.stepIds(context)
        if (step !in route) return this
        val nextVisited = if (step in visited) visited else visited + step
        return copy(
            block = SetupStepGraph.blockOf(step),
            stepIndex = route.indexOf(step),
            completedBlocks = SetupStepGraph.confirmedBlocks(answers, context, pendingReview),
            currentStepId = step,
            visited = nextVisited,
            revision = revision + 1,
            terminal = step == SetupStepId.REVIEW_ACTIVATE,
        )
    }

    /**
     * Records provenance for one step; engine results can never be declared
     * answers. Confirming a milestone (recording its answer) is the explicit
     * act that completes its block and closes any review flag of that block —
     * and only a confirmation the user could have made (USER_DECLARED or
     * accepted SUGGESTED) may be recorded for a milestone: a DERIVED or
     * ENGINE_RESULT record (preview material) can never mark a block done.
     * Completion never derives from visiting steps or navigating past a
     * milestone; M1's validateAll keeps gating the block's real data.
     */
    fun recordAnswer(step: SetupStepId, provenance: SetupAnswerProvenance, valueState: SetupValueState): SetupStepProgress {
        require(valueState != SetupValueState.DECLARED || provenance.canPersistAsDeclared()) {
            "Un preview o resultado de motor nunca se guarda como valor declarado"
        }
        if (SetupStepGraph.isMilestone(step)) {
            require(provenance.canPersistAsDeclared()) {
                "Solo una confirmación declarada o sugerida puede registrar un hito de bloque"
            }
        }
        val updated = copy(answers = answers + (step to provenance), revision = revision + 1)
        return if (SetupStepGraph.isMilestone(step)) {
            val block = SetupStepGraph.blockOf(step)
            updated.copy(
                completedBlocks = updated.completedBlocks + block,
                // Reconfirmar el hito cierra la revisión del bloque: sus
                // respuestas nunca se borraron.
                pendingReview = updated.pendingReview
                    .filterNot { SetupStepGraph.blockOf(it) == block }
                    .toSet(),
            )
        } else updated
    }

    /**
     * Marks steps (and the blocks that own them) for review. Marking never
     * deletes answer data: only the block's "done" flag drops, and the block
     * completes again as soon as its milestone is confirmed once more.
     */
    fun withPendingReview(steps: Set<SetupStepId>): SetupStepProgress {
        if (steps.isEmpty()) return this
        val affectedBlocks = steps.mapTo(mutableSetOf()) { SetupStepGraph.blockOf(it) }
        return copy(
            pendingReview = pendingReview + steps,
            completedBlocks = completedBlocks - affectedBlocks,
        )
    }

    fun reviewDone(step: SetupStepId): SetupStepProgress =
        copy(pendingReview = pendingReview - step)

    fun withStalePreviews(kinds: Set<SetupPreviewKind>): SetupStepProgress =
        if (kinds.isEmpty()) this else copy(stalePreviews = stalePreviews + kinds)

    fun previewsComputed(kinds: Set<SetupPreviewKind>): SetupStepProgress =
        copy(stalePreviews = stalePreviews - kinds)
}
