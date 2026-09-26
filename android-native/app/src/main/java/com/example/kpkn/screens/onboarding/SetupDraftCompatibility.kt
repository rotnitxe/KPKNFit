package com.example.kpkn.screens.onboarding

import com.example.kpkn.data.onboarding.SetupDraftResolver
import com.example.kpkn.data.onboarding.SetupDraftScope
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
        repairStepProgress(repairGoalStyleConflict(restoreMandatoryVitals(normalizeOrigin(draft))))

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
     * NATIVE/MIGRATED drafts keep their answers and origin untouched unless
     * their cursor is stranded on a step that is no longer in the productive
     * route (GENDER / HOME_EQUIPMENT / RINGS_START). In that case the cursor is
     * re-pointed to a resume step derived from the legacy mirror, preserving
     * every answer and never converting gender identity into equation sex; the
     * draft is only left marked NOT_CONVERTIBLE when the mirror truly cannot be
     * placed in this scope.
     */
    fun repairStepProgress(draft: SetupWizardDraft): SetupWizardDraft {
        // Defensa también para llamadas directas: un progreso nativo con el
        // origen por defecto nunca se trata como migración legacy.
        val normalized = normalizeOrigin(draft)
        if (normalized !== draft) return repairStepProgress(normalized)
        val progress = draft.stepProgress
        if (progress.origin != SetupProgressOrigin.NOT_CONVERTIBLE) {
            val route = SetupStepGraph.stepIds(draft.stepContext())
            if (progress.currentStepId in route) return draft
            val resumable = SetupStepGraph.migrateFromLegacy(
                draft.wizChat.currentQuestionId, draft.stepContext(), progress.answers)
            if (resumable.origin == SetupProgressOrigin.NOT_CONVERTIBLE) return draft
            val resume = resumable.currentStepId
            val repaired = progress.copy(
                block = SetupStepGraph.blockOf(resume),
                stepIndex = route.indexOf(resume),
                currentStepId = resume,
                visited = if (resume in progress.visited) progress.visited else progress.visited + resume,
                revision = progress.revision + 1,
                terminal = resume == SetupStepId.REVIEW_ACTIVATE,
            )
            return if (repaired == progress) draft else draft.copy(stepProgress = repaired)
        }
        val migrated = migrate(draft)
        return if (migrated == draft.stepProgress) draft
        else draft.copy(stepProgress = migrated.copy(revision = draft.stepProgress.revision + 1))
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
        if (persistedRevision == null || persistedRevision == currentRevision) return draft
        val selected = draft.selectedCatalogId ?: return draft
        return draft.copy(
            catalogRevision = currentRevision,
            selectedCatalogId = selected.takeIf(planExists),
            stepProgress = draft.stepProgress
                .withPendingReview(setOf(SetupStepId.PLAN))
                .withStalePreviews(setOf(
                    SetupPreviewKind.PLAN_CANDIDATES, SetupPreviewKind.EXERCISES,
                    SetupPreviewKind.LOADS, SetupPreviewKind.WARMUPS, SetupPreviewKind.SPLIT,
                    SetupPreviewKind.RECIPE, SetupPreviewKind.MARKS,
                )),
            wizChat = draft.wizChat.copy(terminal = false, revision = draft.wizChat.revision + 1),
        )
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
                answers.responseState.takeIf { it != com.example.kpkn.data.models.CalibrationResponseState.UNKNOWN }
                    ?: com.example.kpkn.data.models.CalibrationResponseState.DECLARED,
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