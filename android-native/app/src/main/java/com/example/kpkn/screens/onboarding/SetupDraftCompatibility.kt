package com.example.kpkn.screens.onboarding

import com.example.kpkn.data.onboarding.SetupDraftResolver
import com.example.kpkn.data.onboarding.SetupDraftScope
import com.example.kpkn.data.programs.PersonalizedPlanCatalog
import com.example.kpkn.domain.onboarding.SetupAnswerProvenance
import com.example.kpkn.domain.onboarding.SetupPreviewKind
import com.example.kpkn.domain.onboarding.SetupProgressOrigin
import com.example.kpkn.domain.onboarding.SetupStepGraph
import com.example.kpkn.domain.onboarding.SetupStepId
import com.example.kpkn.domain.onboarding.SetupStepProgress
import com.example.kpkn.domain.onboarding.WizChatAnswerSource
import com.example.kpkn.domain.onboarding.WizChatGraph
import com.example.kpkn.domain.onboarding.WizChatQuestionId

/**
 * Pure compatibility rules for persisted drafts: age, height and weight must be
 * explicitly declared in flows that collect profile vitals, so old drafts that
 * omitted them are rewound to ask only the pending data.
 *
 * Legacy WizChat drafts are bridged into the traditional step graph:
 * - Provenance is recorded ONLY for explicit legacy answers. OMITTED,
 *   IMPORTED and UNKNOWN payloads and neutral options (e.g. N_SEX "Prefiero
 *   no responder") never count as answered in the new progress; the legacy
 *   payload itself is always preserved (authority excludes, it never deletes).
 * - Native step progress is authoritative for age/height/weight: typed
 *   records win over the stale mirror and a deleted value is never
 *   resurrected from old JSON.
 * - [SetupStepGraph.migrateFromLegacy] decides the resume step; a block is
 *   never completed by position, so a migrated draft starts with no completed
 *   block and confirms each milestone in the new flow.
 */
object SetupDraftCompatibility {
    /** Orden de los vitales obligatorios y su paso canónico en el progreso. */
    private val vitalSteps = listOf(
        WizChatQuestionId.P_AGE to SetupStepId.AGE,
        WizChatQuestionId.P_HEIGHT to SetupStepId.HEIGHT,
        WizChatQuestionId.P_WEIGHT to SetupStepId.WEIGHT,
    )

    private val mandatoryOrder = vitalSteps.map { it.first }

    fun collectsProfileVitals(scope: String): Boolean =
        SetupDraftResolver.scopeOf(scope) in setOf(SetupDraftScope.FULL, SetupDraftScope.TRAINING_ONLY)

    /**
     * Vitales ya declarados. One rule per step, mirroring the value authority
     * of [restoreMandatoryVitals] (a real record, never a bare default value):
     * - if the native step progress OWNS the vital (typed answer record), only
     *   that record counts — and only when the value is actually present;
     * - otherwise an explicit finite legacy mirror record counts;
     * - OMITTED, IMPORTED and UNKNOWN payloads exist in the JSON but never
     *   count as declared, exactly like [SetupStepGraph.isExplicitLegacy]
     *   treats them. Imported values may still be shown as a suggestion; M1
     *   decides on confirm.
     */
    fun declaredVitals(draft: SetupWizardDraft): Set<WizChatQuestionId> = buildSet {
        val mirror = explicitMirrorVitals(draft)
        for ((question, step) in vitalSteps) {
            val owned = draft.stepProgress.answers.containsKey(step)
            val nativeDeclared = when (question) {
                WizChatQuestionId.P_AGE -> draft.ageYears != null
                WizChatQuestionId.P_HEIGHT -> draft.heightCm != null
                else -> draft.weightKg != null
            } && draft.stepProgress.answers[step]?.canPersistAsDeclared() == true
            when {
                nativeDeclared -> add(question)
                // El espejo solo aporta cuando el campo no es autoridad nativa.
                !owned && mirror.containsKey(question) -> add(question)
            }
        }
    }

    /**
     * Legacy mirror vitals usable as authority: explicit source + finite value.
     * The records themselves are never deleted (legacy JSON stays intact);
     * exclusion from authority is the mechanism, not data loss.
     */
    private fun explicitMirrorVitals(draft: SetupWizardDraft): Map<WizChatQuestionId, Double> =
        draft.wizChat.acceptedAnswers
            .filter {
                it.questionId in mandatoryOrder &&
                    (it.source == WizChatAnswerSource.DECLARED ||
                        it.source == WizChatAnswerSource.SUGGESTED_ACCEPTED) &&
                    it.numberValue?.isFinite() == true
            }
            .associate { it.questionId to it.numberValue!! }

    fun pendingMandatoryVitals(draft: SetupWizardDraft): List<WizChatQuestionId> =
        if (!collectsProfileVitals(draft.draftScope)) emptyList()
        else mandatoryOrder.filterNot { it in declaredVitals(draft) }

    fun repair(draft: SetupWizardDraft): SetupWizardDraft =
        repairStepProgress(
            repairMissingApparatus(
                repairTrainingPath(
                    repairLegacyGoalReview(
                        normalizeLegacyRouteAtPlan(
                            repairGoalStyleConflict(restoreMandatoryVitals(normalizeOrigin(draft))),
                        ),
                    ),
                ),
            ),
        )

    /**
     * A draft already resting on PLAN has crossed the old ROUTE question. Keep
     * its answer in the legacy mirror/selections, but enter the unified catalog
     * instead of retaining a hidden protocol-only filter.
     */
    fun normalizeLegacyRouteAtPlan(draft: SetupWizardDraft): SetupWizardDraft {
        if (draft.stepProgress.currentStepId != SetupStepId.PLAN ||
            draft.programRoute != SetupProgramRoute.PROTOCOL ||
            draft.trainingPath == SetupTrainingPath.FROM_SCRATCH
        ) return draft
        return draft.copy(
            programRoute = SetupProgramRoute.CUSTOMIZABLE,
            trainingPath = SetupTrainingPath.PERSONALIZE,
        )
    }

    /**
     * T-005 / §15.4 — HEALTH/MIXED: el valor y sus datos se CONSERVAN; GOAL
     * queda marcado para revisar y se sugiere «Atleta completo» en la UI sin
     * seleccionarlo nunca. Idempotente: mientras el objetivo siga siendo
     * legacy, la marca es el mismo conjunto; una elección real retira la
     * regla (el nuevo objetivo ya no es legacy).
     */
    private fun repairLegacyGoalReview(draft: SetupWizardDraft): SetupWizardDraft {
        val goal = draft.goal ?: return draft
        if (!goal.isLegacyOnly) return draft
        return draft.copy(stepProgress = draft.stepProgress.withPendingReview(setOf(SetupStepId.GOAL)))
    }

    /**
     * T-005 / §15.4 — CUSTOMIZABLE con `trainingPath` null: se rellena
     * PERSONALIZE solo cuando NO hay sesiones montadas a mano; si las hay, se
     * conserva manual y PLAN queda por revisión (nunca se rellena un camino que
     * convertiría sesiones manuales en un plan catálogo). Idempotente.
     */
    private fun repairTrainingPath(draft: SetupWizardDraft): SetupWizardDraft {
        if (draft.programRoute != SetupProgramRoute.CUSTOMIZABLE || draft.trainingPath != null) return draft
        return if (draft.sessions.any { it.exercises.isNotEmpty() }) {
            draft.copy(stepProgress = draft.stepProgress.withPendingReview(setOf(SetupStepId.PLAN)))
        } else {
            draft.copy(trainingPath = SetupTrainingPath.PERSONALIZE)
        }
    }

    /**
     * T-005 / §15.4 — categorías sin aparatos: se MANTIENEN las categorías y
     * los mapas vacíos (vacío = UNKNOWN, nunca PRESENT) y NO se borra ningún
     * inventario guardado; EQUIPMENT queda por revisión solo cuando el plan
     * elegido exige precisión de aparato/soporte. Idempotente.
     */
    private fun repairMissingApparatus(draft: SetupWizardDraft): SetupWizardDraft {
        val availability = draft.trainingOptions.availability ?: return draft
        if (availability.categories.isEmpty()) return draft
        if (availability.apparatus.isNotEmpty() || availability.supports.isNotEmpty()) return draft
        val planId = draft.selectedCatalogId ?: return draft
        val entry = PersonalizedPlanCatalog.find(planId) ?: return draft
        val requiresPrecision = entry.requiredEquipment.any { token ->
            token.startsWith("machine_config:") || token in PRECISE_EQUIPMENT_TOKENS
        }
        if (!requiresPrecision) return draft
        return draft.copy(stepProgress = draft.stepProgress.withPendingReview(setOf(SetupStepId.EQUIPMENT)))
    }

    /** Tokens que exigen presencia concreta (§13.2), no solo una categoría. */
    private val PRECISE_EQUIPMENT_TOKENS: Set<String> = setOf(
        "machine", "cable", "smith_machine",
        "bench", "bench_incline", "rack", "pull_up_bar", "dip_bars",
        "low_bar_support", "support", "ball", "nordic_anchor",
    )

    /**
     * Native progress persisted with the DEFAULT origin (NOT_CONVERTIBLE @ NAME)
     * is a fake "old" draft: a real legacy bridge never carries typed records,
     * a visit trail or a confirmed block. It is relabelled NATIVE without
     * touching the cursor, the answers or the legacy mirror, so repair neither
     * migrates nor rewinds it.
     */
    private fun normalizeOrigin(draft: SetupWizardDraft): SetupWizardDraft {
        val progress = draft.stepProgress
        if (progress.origin != SetupProgressOrigin.NOT_CONVERTIBLE) return draft
        val nativeSignature = progress.answers.isNotEmpty() ||
            progress.visited.isNotEmpty() ||
            progress.completedBlocks.isNotEmpty()
        if (!nativeSignature) return draft
        return draft.copy(stepProgress = progress.copy(
            origin = SetupProgressOrigin.NATIVE,
            revision = progress.revision + 1,
        ))
    }

    /**
     * Repairs the stable step progress of a persisted draft.
     *
     * Legacy (NOT_CONVERTIBLE) drafts are bridged from the WizChat payload:
     * provenance comes from [SetupStepGraph.isExplicitLegacy] (OMITTED,
     * UNKNOWN and IMPORTED sources, unmapped or partially mapped labels and
     * non-finite numbers never count), and [SetupStepGraph.migrateFromLegacy]
     * decides the resume step without completing any block by position.
     *
     * NATIVE/MIGRATED drafts keep their answers and origin untouched. Their
     * cursor/index is repaired by stable ID; a removed cursor or newly
     * out-of-order/pending prerequisite resumes at the first valid pending step.
     * Every answer is preserved, and gender identity is never converted into
     * equation sex. A legacy draft is left NOT_CONVERTIBLE only when its mirror
     * truly cannot be placed in this scope.
     */
    fun repairStepProgress(draft: SetupWizardDraft): SetupWizardDraft {
        // Defensa también para llamadas directas: un progreso nativo con el
        // origen por defecto nunca se trata como migración legacy.
        val normalized = normalizeOrigin(draft)
        if (normalized !== draft) return repairStepProgress(normalized)
        val progress = draft.stepProgress
        if (progress.origin != SetupProgressOrigin.NOT_CONVERTIBLE) {
            val route = SetupStepGraph.stepIds(draft.stepContext())
            val currentIndex = route.indexOf(progress.currentStepId)
            // A cursor can still name a valid step after the graph changes while
            // answers are now out of order. Resume at the first unanswered or
            // explicitly pending step before that cursor; never trust its old
            // numeric index or jump over a newly inserted prerequisite.
            val earlierPending = if (currentIndex >= 0) {
                firstPendingStep(draft, route.take(currentIndex))
            } else {
                null
            }
            val resume = earlierPending ?: if (currentIndex >= 0) {
                progress.currentStepId
            } else {
                firstPendingStep(draft, route) ?: SetupStepId.REVIEW_ACTIVATE
            }
            return reindexProgress(draft, resume, route)
        }
        val migrated = migrate(draft)
        if (migrated.origin == SetupProgressOrigin.NOT_CONVERTIBLE) {
            // Even when the old question belongs to a different scope, retain
            // its stable step projection for diagnostics/recovery. The payload
            // remains NOT_CONVERTIBLE and untouched; only a mapped legacy
            // cursor is carried forward.
            if (migrated.currentStepId == progress.currentStepId &&
                migrated.block == progress.block &&
                migrated.stepIndex == progress.stepIndex &&
                migrated.terminal == progress.terminal
            ) return draft
            return draft.copy(stepProgress = progress.copy(
                block = migrated.block,
                stepIndex = migrated.stepIndex,
                currentStepId = migrated.currentStepId,
                terminal = migrated.terminal,
                revision = progress.revision + 1,
            ))
        }
        val withRepairs = migrated.copy(
            pendingReview = migrated.pendingReview + draft.stepProgress.pendingReview,
            stalePreviews = migrated.stalePreviews + draft.stepProgress.stalePreviews,
        )
        return repairStepProgress(draft.copy(stepProgress = withRepairs))
    }

    /** Earliest route step that is unanswered or explicitly marked for review. */
    private fun firstPendingStep(
        draft: SetupWizardDraft,
        route: List<SetupStepId>,
    ): SetupStepId? {
        val confirmed = buildSet {
            addAll(draft.stepProgress.answers.keys)
            draft.wizChat.acceptedAnswers.forEach { answer ->
                if (SetupStepGraph.isExplicitLegacy(answer.questionId, answer)) {
                    SetupStepGraph.stepForQuestion(answer.questionId)?.let(::add)
                }
            }
        }
        return route.firstOrNull { step ->
            step != SetupStepId.REVIEW_ACTIVATE &&
                (step in draft.stepProgress.pendingReview || step !in confirmed)
        }
    }

    /** Recomputes stable identity/index/completion without dropping any answer or visit. */
    private fun reindexProgress(
        draft: SetupWizardDraft,
        resume: SetupStepId,
        route: List<SetupStepId>,
    ): SetupWizardDraft {
        if (resume !in route) return draft
        val progress = draft.stepProgress
        val repaired = progress.copy(
            block = SetupStepGraph.blockOf(resume),
            stepIndex = route.indexOf(resume),
            completedBlocks = SetupStepGraph.confirmedBlocks(
                progress.answers,
                draft.stepContext(),
                progress.pendingReview,
            ),
            currentStepId = resume,
            visited = if (resume in progress.visited) progress.visited else progress.visited + resume,
            graphRevision = SetupStepGraph.REVISION,
            terminal = resume == SetupStepId.REVIEW_ACTIVATE,
        )
        return if (repaired == progress) draft
        else draft.copy(stepProgress = repaired.copy(revision = progress.revision + 1))
    }

    private fun migrate(draft: SetupWizardDraft): SetupStepProgress {
        val provenance = draft.wizChat.acceptedAnswers.mapNotNull { record ->
            if (!SetupStepGraph.isExplicitLegacy(record.questionId, record)) return@mapNotNull null
            SetupStepGraph.stepForQuestion(record.questionId)?.let { step ->
                step to SetupAnswerProvenance.fromLegacy(record.source)
            }
        }.toMap()
        return SetupStepGraph.migrateFromLegacy(draft.wizChat.currentQuestionId, draft.stepContext(), provenance)
    }

    /**
     * Resume rule when the plan catalog changed while the draft was stored:
     * every answer is kept, the selection that may no longer exist is cleared
     * only when its plan is really gone, and the step is marked for review with
     * the dependent previews stale. Returns the draft untouched when the
     * catalog did not change or the draft never selected a plan.
     */
    fun applyCatalogRevision(
        draft: SetupWizardDraft,
        persistedRevision: String?,
        currentRevision: String,
        planExists: (String) -> Boolean,
    ): SetupWizardDraft {
        val previousRevision = persistedRevision ?: draft.catalogRevision
        if (previousRevision == currentRevision) return draft
        val selected = draft.selectedCatalogId
        val updated = draft.copy(catalogRevision = currentRevision)
        if (selected == null) return updated
        return repairStepProgress(updated.copy(
            selectedCatalogId = selected.takeIf(planExists),
            stepProgress = draft.stepProgress
                .withPendingReview(setOf(SetupStepId.PLAN))
                .withStalePreviews(setOf(
                    SetupPreviewKind.PLAN_CANDIDATES, SetupPreviewKind.EXERCISES,
                    SetupPreviewKind.LOADS, SetupPreviewKind.WARMUPS, SetupPreviewKind.SPLIT,
                    SetupPreviewKind.RECIPE, SetupPreviewKind.MARKS,
                )),
            wizChat = draft.wizChat.copy(terminal = false, revision = draft.wizChat.revision + 1),
        ))
    }

    private fun repairGoalStyleConflict(draft: SetupWizardDraft): SetupWizardDraft {
        val goal = draft.goal ?: return draft
        val inferred = goal.inferredTrainingStyle ?: return draft
        val answers = draft.volumeAnswers
        if (answers.style == inferred) return draft
        // Old flows could keep a style that contradicts the goal; the goal wins
        // and the volume reference is recalibrated with the same engine.
        val fixed = answers.copy(style = inferred)
        val profile = rebuiltVolumeProfile(fixed)
        return draft.copy(
            volumeAnswers = fixed,
            volumeCalibrationProfile = profile,
            volumeRecommendations = profile?.recommendations.orEmpty(),
            athleteProfileScore = profile?.athleteProfileScore,
            wizChat = draft.wizChat.copy(
                acceptedAnswers = draft.wizChat.acceptedAnswers.filterNot { it.questionId == WizChatQuestionId.T_STYLE },
            ),
        )
    }

    private fun rebuiltVolumeProfile(answers: SetupVolumeAnswers): com.example.kpkn.data.models.VolumeCalibrationProfile? {
        val style = answers.style ?: return null
        val technique = answers.technique ?: return null
        val consistency = answers.consistency ?: return null
        val strength = answers.strength ?: return null
        val mobility = answers.mobility ?: return null
        val output = com.example.kpkn.domain.training.VolumeCalibrationEngine.calculate(style, technique, consistency, strength, mobility)
        return com.example.kpkn.data.models.VolumeCalibrationProfile(
            style,
            output.score,
            com.example.kpkn.data.models.VolumeCalibrationResponses(
                technique, consistency, strength, mobility,
                answers.responseState,
            ),
            output.recommendations,
            System.currentTimeMillis(),
            com.example.kpkn.domain.training.VolumeCalibrationEngine.REVISION,
        )
    }

    /**
     * Mandatory-vitals authority without destroying the legacy log.
     *
     * Value merge (age/height/weight):
     * - Genuinely legacy progress (NOT_CONVERTIBLE): the explicit mirror value
     *   is the authority — that is the flow that used to write these fields.
     * - Native progress: the step progress wins. A typed record owns its field,
     *   so a value the user deleted stays deleted (no silent resurrection from
     *   an old mirror), a value already present is never overwritten, and only
     *   with NO native datum at all an explicit finite legacy value is carried
     *   over.
     *
     * The mirror JSON is preserved verbatim (OMITTED / non-finite records are
     * EXCLUDED from authority, never deleted: cleaning the log would be the
     * only irreversible act here). Only the legacy mirror cursor rewinds, and
     * only to the first pending vital.
     */
    private fun restoreMandatoryVitals(draft: SetupWizardDraft): SetupWizardDraft {
        if (!collectsProfileVitals(draft.draftScope)) return draft
        val legacyProgress = draft.stepProgress.origin == SetupProgressOrigin.NOT_CONVERTIBLE
        val mirror = explicitMirrorVitals(draft)
        fun merge(question: WizChatQuestionId, step: SetupStepId, native: Double?): Double? = when {
            legacyProgress -> mirror[question] ?: native
            draft.stepProgress.answers.containsKey(step) -> native
            native != null -> native
            else -> mirror[question]
        }
        val merged = draft.copy(
            ageYears = merge(WizChatQuestionId.P_AGE, SetupStepId.AGE, draft.ageYears?.toDouble())?.toInt(),
            heightCm = merge(WizChatQuestionId.P_HEIGHT, SetupStepId.HEIGHT, draft.heightCm),
            weightKg = merge(WizChatQuestionId.P_WEIGHT, SetupStepId.WEIGHT, draft.weightKg),
        )
        val pending = pendingMandatoryVitals(merged)
        if (pending.isEmpty()) return merged
        // Solo el progreso legacy se rebobina por aquí; el cursor nativo no se
        // altera si sigue dentro de la ruta (sus vitales se reportan como
        // pendientes sin mover nada).
        if (!legacyProgress) return merged
        val progress = draft.wizChat
        val firstPending = pending.first()
        val progressedPast = progress.terminal || progress.currentQuestionId.ordinal > firstPending.ordinal
        if (!progressedPast) return merged
        return merged.copy(
            wizChat = progress.copy(
                currentQuestionId = firstPending,
                stage = WizChatGraph.stageFor(firstPending),
                terminal = false,
                revision = progress.revision + 1,
            ),
        )
    }
}
