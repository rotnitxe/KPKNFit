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
 * Replaces ad-hoc [WorkoutVoiceAutoConfirmGate.shouldAutoConfirm] boolean checks.
 */
object WorkoutVoiceConfirmationPolicy {

    fun decide(
        interpretation: WorkoutVoiceInterpretation,
        asrConfidence: Float,
        draftHasWeightAndReps: Boolean = false,
        isEditPatch: Boolean = false,
    ): ConfirmationDecision {
        val hasWeight = WorkoutVoiceField.WEIGHT in interpretation.fields &&
            interpretation.weightKg != null && interpretation.weightKg > 0.0
        val hasReps = WorkoutVoiceField.VALUE in interpretation.fields &&
            interpretation.metricValue != null && interpretation.metricValue > 0
        val hasIntensity = WorkoutVoiceField.INTENSITY in interpretation.fields &&
            interpretation.intensityValue != null

        if (isEditPatch) {
            // Edits are lower risk when at least one field is explicit.
            if (!hasWeight && !hasReps && !hasIntensity && interpretation.weightKg == null) {
                return ConfirmationDecision.REJECT
            }
            return if (confidenceOk(asrConfidence)) ConfirmationDecision.AUTO else ConfirmationDecision.ASK
        }

        val completeSet = hasWeight && hasReps
        val intensityOnDraft = draftHasWeightAndReps && hasIntensity

        if (!completeSet && !intensityOnDraft) {
            // Partial dictation — still ask if anything useful was heard.
            if (hasWeight || hasReps || hasIntensity) return ConfirmationDecision.ASK
            return ConfirmationDecision.REJECT
        }

        return if (confidenceOk(asrConfidence)) ConfirmationDecision.AUTO else ConfirmationDecision.ASK
    }

    /** Back-compat helper used by existing call sites. */
    fun shouldAutoConfirm(
        interpretation: WorkoutVoiceInterpretation,
        asrConfidence: Float,
        draftHasWeightAndReps: Boolean = false,
    ): Boolean = decide(interpretation, asrConfidence, draftHasWeightAndReps) == ConfirmationDecision.AUTO

    private fun confidenceOk(asrConfidence: Float): Boolean {
        if (asrConfidence <= 0f) return true // engine did not provide scores
        return asrConfidence >= WorkoutVoiceHypothesisScorer.AUTO_CONFIRM_MIN_CONFIDENCE
    }
}
