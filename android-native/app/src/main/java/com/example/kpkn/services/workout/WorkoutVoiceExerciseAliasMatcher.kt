package com.example.kpkn.services.workout

import com.example.kpkn.data.exercises.catalogSearchRedirects
import java.text.Normalizer
import java.util.Locale

/**
 * Resolves spoken exercise nicknames against catalog aliases and user settings.
 */
object WorkoutVoiceExerciseAliasMatcher {

    data class RankedExercise(val exerciseId: String, val exerciseName: String, val score: Double)

    fun rank(
        spoken: String,
        exercises: List<Pair<String, String>>,
        userAliases: Map<String, String>,
    ): List<RankedExercise> {
        val spokenTokens = normalize(spoken).split(' ').filter(String::isNotBlank).toSet()
        if (spokenTokens.isEmpty()) return emptyList()
        return exercises.map { (id, name) ->
            val candidateTokens = buildSet {
                addAll(normalize(name).split(' ').filter(String::isNotBlank))
                userAliases.filterValues { it == id }.keys.forEach { addAll(normalize(it).split(' ').filter(String::isNotBlank)) }
            }
            val overlap = spokenTokens.intersect(candidateTokens).size.toDouble()
            val union = spokenTokens.union(candidateTokens).size.coerceAtLeast(1)
            val exactBonus = if (normalize(name) == normalize(spoken)) 1.0 else 0.0
            RankedExercise(id, name, exactBonus + overlap / union)
        }.filter { it.score > 0.0 }.sortedByDescending(RankedExercise::score)
    }

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
        catalogSearchRedirects().forEach { (aliasId, canonical) ->
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
