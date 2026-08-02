package com.example.kpkn.domain.exercises.catalogv2

/** Namespace and capability policy for user-created exercises. */
object CustomExerciseCatalogV2Policy {
    private const val PREFIX = "custom:"

    fun normalizeId(rawId: String): String {
        val normalized = rawId.trim().lowercase()
        require(normalized.startsWith(PREFIX)) {
            "custom_exercise_id_must_start_with_custom_namespace"
        }
        require(normalized.removePrefix(PREFIX).matches(Regex("[a-z0-9][a-z0-9_-]{1,63}"))) {
            "custom_exercise_id_invalid"
        }
        return normalized
    }

    fun canUseForAutomation(hasMinimumMetadata: Boolean): Boolean = hasMinimumMetadata

    fun validateCollision(id: String, staticIds: Set<String>) {
        val normalized = normalizeId(id)
        require(normalized !in staticIds.map(String::lowercase).toSet()) {
            "custom_exercise_id_collides_with_static_catalog"
        }
    }
}
