package com.example.kpkn.domain.multisport

sealed interface TrainingIntentValidationError {
    data class UnsupportedSchemaVersion(val actual: Int) : TrainingIntentValidationError
    data class EmptyGoalId(val goalIndex: Int) : TrainingIntentValidationError
    data class DuplicateGoalId(val id: String) : TrainingIntentValidationError
    data class EmptyObjective(val goalIndex: Int) : TrainingIntentValidationError
    data class EmptyTargetUnit(val goalIndex: Int) : TrainingIntentValidationError
    data class NonFiniteTargetValue(val goalIndex: Int) : TrainingIntentValidationError
    data object EmptyPrimaryGoalId : TrainingIntentValidationError
    data class PrimaryGoalNotFound(val id: String) : TrainingIntentValidationError
}

/** Pure, deterministic validation. It never normalizes or rewrites the supplied intent. */
object TrainingIntentPolicy {
    fun validate(intent: TrainingIntent): List<TrainingIntentValidationError> {
        val errors = mutableListOf<TrainingIntentValidationError>()
        if (intent.schemaVersion != TrainingIntent.CURRENT_SCHEMA_VERSION) {
            return listOf(TrainingIntentValidationError.UnsupportedSchemaVersion(intent.schemaVersion))
        }

        val goals = intent.goals
        val seenIds = mutableSetOf<String>()
        goals.forEachIndexed { index, goal ->
            if (goal.id.isBlank()) {
                errors += TrainingIntentValidationError.EmptyGoalId(index)
            } else if (!seenIds.add(goal.id)) {
                errors += TrainingIntentValidationError.DuplicateGoalId(goal.id)
            }

            if (goal.objective.isBlank()) {
                errors += TrainingIntentValidationError.EmptyObjective(index)
            }

            goal.target?.let { target ->
                if (target.unit.isBlank()) {
                    errors += TrainingIntentValidationError.EmptyTargetUnit(index)
                }
                if (!target.value.isFinite()) {
                    errors += TrainingIntentValidationError.NonFiniteTargetValue(index)
                }
            }
        }

        if (intent.primaryGoalId.isBlank()) {
            errors += TrainingIntentValidationError.EmptyPrimaryGoalId
        }
        if (goals.none { it.id == intent.primaryGoalId }) {
            errors += TrainingIntentValidationError.PrimaryGoalNotFound(intent.primaryGoalId)
        }
        return errors.toList()
    }
}
