package com.example.kpkn.services.workout

import com.example.kpkn.screens.workout.WorkoutVoiceField
import com.example.kpkn.screens.workout.WorkoutVoiceInterpretation

enum class ConfirmationDecision {
    /** Register immediately with undo window. */
    AUTO,
    /** Ask sí/no. */
    ASK,
    /** Do not treat as a set registration. */
    REJECT,
}

/**
 * Unified confirmation policy for new sets and intensity-only completions.
 * Replaces the removed ad-hoc auto-confirm boolean gate.
 */
object WorkoutVoiceConfirmationPolicy {

    fun decide(
        interpretation: WorkoutVoiceInterpretation,
        asrConfidence: Float,
        draftHasWeightAndReps: Boolean = false,
        requiresWeight: Boolean = true,
        isEditPatch: Boolean = false,
        confidenceKnown: Boolean = true,
    ): ConfirmationDecision {
        val hasWeight = WorkoutVoiceField.WEIGHT in interpretation.fields &&
            interpretation.weightKg != null && interpretation.weightKg > 0.0
        val hasReps = WorkoutVoiceField.VALUE in interpretation.fields &&
            (interpretation.resolvedMetricValue ?: 0.0) > 0.0
        val hasIntensity = WorkoutVoiceField.INTENSITY in interpretation.fields &&
            interpretation.intensityValue != null
        val hasSupplementalFeedback = hasIntensity || interpretation.reachedFailure || interpretation.romPercent != null

        if (isEditPatch) {
            // Edits are lower risk when at least one field is explicit.
            if (!hasWeight && !hasReps && !hasSupplementalFeedback && interpretation.weightKg == null) {
                return ConfirmationDecision.REJECT
            }
            return if (confidenceOk(asrConfidence, confidenceKnown)) {
                ConfirmationDecision.AUTO
            } else {
                ConfirmationDecision.ASK
            }
        }

        val completeSet = (!requiresWeight || hasWeight) && hasReps
        val intensityOnDraft = draftHasWeightAndReps && hasSupplementalFeedback

        if (!completeSet && !intensityOnDraft) {
            // Partial dictation — still ask if anything useful was heard.
            if (hasWeight || hasReps || hasSupplementalFeedback) return ConfirmationDecision.ASK
            return ConfirmationDecision.REJECT
        }

        return if (confidenceOk(asrConfidence, confidenceKnown)) {
            ConfirmationDecision.AUTO
        } else {
            ConfirmationDecision.ASK
        }
    }

    /** Back-compat helper used by existing call sites. */
    fun shouldAutoConfirm(
        interpretation: WorkoutVoiceInterpretation,
        asrConfidence: Float,
        draftHasWeightAndReps: Boolean = false,
        requiresWeight: Boolean = true,
        confidenceKnown: Boolean = true,
    ): Boolean = decide(
        interpretation,
        asrConfidence,
        draftHasWeightAndReps,
        requiresWeight = requiresWeight,
        confidenceKnown = confidenceKnown,
    ) == ConfirmationDecision.AUTO

    private fun confidenceOk(asrConfidence: Float, confidenceKnown: Boolean): Boolean {
        // Vosk (y cualquier motor sin score real) no puede auto-confirmar por confianza inventada.
        if (!confidenceKnown) return false
        if (asrConfidence <= 0f) return true // nativo sin CONFIDENCE_SCORES (legacy)
        return asrConfidence >= WorkoutVoiceHypothesisScorer.AUTO_CONFIRM_MIN_CONFIDENCE
    }
}
