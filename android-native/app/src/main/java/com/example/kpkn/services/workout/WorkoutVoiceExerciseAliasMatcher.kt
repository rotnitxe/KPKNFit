package com.example.kpkn.services.workout

import com.example.kpkn.data.exercises.EXERCISE_ID_ALIASES
import java.text.Normalizer
import java.util.Locale

/**
 * Resolves spoken exercise nicknames against catalog aliases and user settings.
 */
object WorkoutVoiceExerciseAliasMatcher {

    fun matchesSpokenName(
        spoken: String,
        exerciseId: String,
        exerciseName: String,
        userAliases: Map<String, String>,
    ): Boolean {
        val spokenNorm = normalize(spoken)
        if (spokenNorm.isBlank()) return false
        val nameNorm = normalize(exerciseName)
        if (nameNorm.isNotBlank() && spokenNorm.contains(nameNorm)) return true

        userAliases.forEach { (nickname, id) ->
            if (id == exerciseId && spokenNorm.contains(normalize(nickname))) return true
        }

        // Catalog map is aliasId → canonicalId; also accept reverse contains on ids.
        EXERCISE_ID_ALIASES.forEach { (aliasId, canonical) ->
            if (canonical == exerciseId || aliasId == exerciseId) {
                val aliasNorm = normalize(aliasId.replace('_', ' '))
                if (aliasNorm.isNotBlank() && spokenNorm.contains(aliasNorm)) return true
            }
        }
        return false
    }

    private fun normalize(text: String): String {
        val decomposed = Normalizer.normalize(text.lowercase(Locale.ROOT), Normalizer.Form.NFD)
        return decomposed.replace(Regex("\\p{Mn}+"), "").trim()
    }
}
