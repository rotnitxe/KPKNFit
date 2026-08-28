package com.example.kpkn.domain.exercises

import com.example.kpkn.data.models.Exercise

/**
 * Stable key for a user-facing nickname overlay.
 * Prefer catalog definition, then db id, then canonical identity.
 */
fun exerciseNicknameKey(
    catalogDefinitionId: String? = null,
    exerciseDbId: String? = null,
    exerciseId: String? = null,
    canonicalExerciseId: String? = null,
): String? = catalogDefinitionId?.trim()?.takeIf { it.isNotBlank() }
    ?: exerciseDbId?.trim()?.takeIf { it.isNotBlank() }
    ?: exerciseId?.trim()?.takeIf { it.isNotBlank() }
    ?: canonicalExerciseId?.trim()?.takeIf { it.isNotBlank() }

fun Exercise.nicknameKey(): String? = exerciseNicknameKey(
    catalogDefinitionId = catalogDefinitionId,
    exerciseDbId = exerciseDbId ?: this.exerciseId,
    exerciseId = id,
    canonicalExerciseId = resolvedCanonicalExerciseId(),
)

fun overlayExerciseParentName(
    fallbackName: String,
    nicknameKey: String?,
    nicknames: Map<String, String>,
): String {
    val nickname = nicknameKey?.let { nicknames[it] }?.trim()?.takeIf { it.isNotBlank() }
    return nickname ?: fallbackName
}

enum class GodModeTechniqueScope {
    THIS,
    REMAINING,
    ALL,
}

fun godModeTechniqueRange(
    setIndex: Int,
    setCount: Int,
    scope: GodModeTechniqueScope,
): Pair<Int, Int> {
    if (setCount <= 0) return 0 to 0
    val idx = setIndex.coerceIn(0, setCount - 1)
    val last = setCount - 1
    return when (scope) {
        GodModeTechniqueScope.THIS -> idx to idx
        GodModeTechniqueScope.REMAINING -> idx to last
        GodModeTechniqueScope.ALL -> 0 to last
    }
}

fun godModeTechniqueApplyIndices(
    setIndex: Int,
    setCount: Int,
    scope: GodModeTechniqueScope,
    completedIndices: Set<Int>,
): List<Int> {
    val (from, to) = godModeTechniqueRange(setIndex, setCount, scope)
    if (setCount <= 0) return emptyList()
    return (from..to).filter { it !in completedIndices }
}
