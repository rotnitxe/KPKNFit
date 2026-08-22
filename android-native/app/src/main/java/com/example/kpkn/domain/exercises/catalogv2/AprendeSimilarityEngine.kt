package com.example.kpkn.domain.exercises.catalogv2

/** The explicit metadata needed to compare two materialized v2 exercises. */
enum class AprendeMuscleRole {
    PRIMARY,
    SECONDARY,
    STABILIZER,
}

data class AprendeSimilarityInput(
    val id: String,
    val displayName: String,
    val definitionId: String,
    val replacementGroup: String?,
    val preservesIntent: Set<String> = emptySet(),
    val movementPatternId: String,
    val muscles: Set<String>,
    /** Role-aware anatomy from the exact configuration. Older callers may
     * leave this empty; the aggregate [muscles] set remains the safe fallback
     * for those fixtures. */
    val musclesByRole: Map<AprendeMuscleRole, Set<String>> = emptyMap(),
    val joints: Set<String>,
    val bodyRegion: ExerciseBodyRegionV2,
    val kineticChain: ExerciseKineticChainV2,
    val laterality: ExerciseLateralityV2,
    val equipmentId: String,
    /** Exact v2 configuration identity; null keeps older non-catalog callers compatible. */
    val configurationId: String? = null,
)

enum class AprendeSimilarityBand {
    EQUIVALENT,
    PATTERN_VARIANT,
    ANATOMICAL_TRANSFER,
}

data class AprendeSimilarityMatch(
    val candidate: AprendeSimilarityInput,
    val band: AprendeSimilarityBand,
    val score: Int,
    val sharedMuscles: Int,
    val sharedJoints: Int,
    val sameDefinition: Boolean,
    val samePattern: Boolean,
    val sameIntent: Boolean,
)

/**
 * Deterministic, metadata-only relation ranking for Aprende. It deliberately
 * has no display-name, fatigue, RPE, or random ordering fallback.
 */
object AprendeSimilarityEngine {
    fun rank(
        current: AprendeSimilarityInput,
        candidates: Iterable<AprendeSimilarityInput>,
        limit: Int = 4,
    ): List<AprendeSimilarityMatch> {
        if (limit <= 0) return emptyList()
        return candidates.asSequence()
            .filter { it.id != current.id }
            .mapNotNull { candidate -> score(current, candidate) }
            .sortedWith(compareByDescending<AprendeSimilarityMatch> { it.score }.thenBy { it.candidate.displayName })
            .take(limit)
            .toList()
    }

    private fun score(
        current: AprendeSimilarityInput,
        candidate: AprendeSimilarityInput,
    ): AprendeSimilarityMatch? {
        val sharedMuscles = current.muscles.intersect(candidate.muscles).size
        val sharedJoints = current.joints.intersect(candidate.joints).size
        val roleAwareMuscles = current.musclesByRole.isNotEmpty() || candidate.musclesByRole.isNotEmpty()
        val sharedPrimaryMuscles = sharedRoleMuscles(current, candidate, AprendeMuscleRole.PRIMARY)
        val sharedSecondaryMuscles = sharedRoleMuscles(current, candidate, AprendeMuscleRole.SECONDARY)
        val sharedStabilizerMuscles = sharedRoleMuscles(current, candidate, AprendeMuscleRole.STABILIZER)
        val sameDefinition = current.definitionId == candidate.definitionId
        val samePattern = current.movementPatternId == candidate.movementPatternId
        val sameReplacementGroup = current.replacementGroup != null &&
            current.replacementGroup == candidate.replacementGroup
        val preservesIntent = current.preservesIntent.any { intent ->
            candidate.preservesIntent.any { candidateIntent ->
                intent.equals(candidateIntent, ignoreCase = true)
            }
        }
        val sameIntent = sameReplacementGroup || preservesIntent
        val band = when {
            sameIntent -> AprendeSimilarityBand.EQUIVALENT
            sameDefinition || samePattern -> AprendeSimilarityBand.PATTERN_VARIANT
            sharedMuscles > 0 || sharedJoints > 0 -> AprendeSimilarityBand.ANATOMICAL_TRANSFER
            else -> return null
        }
        val roleScore = if (roleAwareMuscles) {
            sharedPrimaryMuscles * 12 +
                sharedSecondaryMuscles * 8 +
                sharedStabilizerMuscles * 5
        } else {
            sharedMuscles * 7
        }
        val score = (if (sameReplacementGroup) 100 else 0) +
            (if (preservesIntent) 28 else 0) +
            (if (sameDefinition) 22 else 0) +
            (if (samePattern) 18 else 0) +
            roleScore +
            sharedJoints * 6 +
            (if (current.bodyRegion == candidate.bodyRegion) 4 else 0) +
            (if (current.kineticChain == candidate.kineticChain) 3 else 0) +
            (if (current.laterality == candidate.laterality) 2 else 0) +
            (if (current.equipmentId == candidate.equipmentId) 1 else 0)
        return AprendeSimilarityMatch(
            candidate = candidate,
            band = band,
            score = score,
            sharedMuscles = sharedMuscles,
            sharedJoints = sharedJoints,
            sameDefinition = sameDefinition,
            samePattern = samePattern,
            sameIntent = sameIntent,
        )
    }

    private fun sharedRoleMuscles(
        current: AprendeSimilarityInput,
        candidate: AprendeSimilarityInput,
        role: AprendeMuscleRole,
    ): Int {
        val currentRole = current.musclesByRole[role].orEmpty()
        val candidateRole = candidate.musclesByRole[role].orEmpty()
        if (currentRole.isEmpty() || candidateRole.isEmpty()) return 0
        return currentRole.intersect(candidateRole).size
    }
}
