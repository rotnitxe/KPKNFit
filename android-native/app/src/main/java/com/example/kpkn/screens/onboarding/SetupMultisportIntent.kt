package com.example.kpkn.screens.onboarding

import com.example.kpkn.domain.multisport.SportId
import com.example.kpkn.domain.multisport.TrainingIntent
import com.example.kpkn.domain.multisport.TrainingIntentPolicy
import com.example.kpkn.domain.multisport.TrainingIntentValidationError
import com.example.kpkn.domain.onboarding.TrainingGoalProfile

/** An activation rejection retains all domain errors for callers and diagnostics. */
class InvalidSetupTrainingIntentException(
    val errors: List<TrainingIntentValidationError>,
) : IllegalArgumentException("Los objetivos deportivos no son válidos. Revisa tus objetivos antes de activar: $errors")

/** Persistence bridge only: this snapshot does not schedule or generate sessions. */
object SetupMultisportIntent {
    fun validate(intent: TrainingIntent?): List<TrainingIntentValidationError> =
        intent?.let(TrainingIntentPolicy::validate).orEmpty()

    /** Null is valid legacy data. Invalid explicit intent is rejected without rewriting it. */
    fun requireValid(intent: TrainingIntent?): TrainingIntent? {
        val errors = validate(intent)
        if (errors.isNotEmpty()) throw InvalidSetupTrainingIntentException(errors)
        return intent
    }

    /**
     * Optional projection for consumers that support these existing profiles.
     * It never changes the draft, marks GOAL answered, or drops secondary goals.
     * General strength and cardio/combat sports have no exact legacy profile.
     */
    fun primaryLegacyProfile(intent: TrainingIntent?): TrainingGoalProfile? {
        val valid = requireValid(intent) ?: return null
        return when (valid.goals.single { it.id == valid.primaryGoalId }.sport) {
            SportId.HYPERTROPHY -> TrainingGoalProfile.BODYBUILDING
            SportId.POWERLIFTING -> TrainingGoalProfile.POWERLIFTING
            SportId.POWERBUILDING -> TrainingGoalProfile.POWERBUILDING
            SportId.WEIGHTLIFTING -> TrainingGoalProfile.WEIGHTLIFTING
            SportId.CALISTHENICS -> TrainingGoalProfile.CALISTHENICS
            SportId.STRONGMAN -> TrainingGoalProfile.STRONGMAN
            SportId.ARMWRESTLING -> TrainingGoalProfile.ARMWRESTLING
            else -> null
        }
    }
}
