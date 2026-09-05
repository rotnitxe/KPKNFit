package com.example.kpkn.domain.competitions

import com.example.kpkn.data.models.ExerciseMuscleInfo
import com.example.kpkn.domain.exercises.catalogv2.ExerciseCatalogV2
import com.example.kpkn.domain.exercises.catalogv2.ExerciseCatalogV2Resolver

data class CompetitionExerciseSuggestion(
    val exercise: ExerciseMuscleInfo,
    val fromCatalogV2: Boolean,
)

object CompetitionExerciseTypeahead {
    const val CUSTOM_PREFIX = "custom:"

    @Volatile
    private var cachedResolver: Pair<String, ExerciseCatalogV2Resolver>? = null

    fun suggest(
        query: String,
        catalogIndex: Map<String, ExerciseMuscleInfo>,
        catalogV2: ExerciseCatalogV2? = null,
        limit: Int = 8,
    ): List<CompetitionExerciseSuggestion> {
        val needle = query.trim()
        if (needle.isEmpty()) return emptyList()
        val byId = linkedMapOf<String, CompetitionExerciseSuggestion>()

        if (catalogV2 != null) {
            resolverFor(catalogV2).search(needle).forEach { hit ->
                val resolved = resolveHit(hit.suggestedConfigurationId, hit.definitionId, catalogIndex)
                    ?: return@forEach
                byId.putIfAbsent(
                    resolved.id.lowercase(),
                    CompetitionExerciseSuggestion(exercise = resolved, fromCatalogV2 = true),
                )
            }
        }

        catalogIndex.values
            .asSequence()
            .filter { matchesName(it, needle) }
            .sortedByDescending { rankName(it, needle) }
            .forEach { exercise ->
                byId.putIfAbsent(
                    exercise.id.lowercase(),
                    CompetitionExerciseSuggestion(exercise = exercise, fromCatalogV2 = false),
                )
            }

        return byId.values.take(limit)
    }

    fun hasExactCatalogMatch(
        query: String,
        suggestions: List<CompetitionExerciseSuggestion>,
    ): Boolean {
        val needle = query.trim().lowercase()
        if (needle.isEmpty()) return false
        return suggestions.any { suggestion ->
            !isCustomId(suggestion.exercise.id) &&
                (suggestion.exercise.name.equals(query.trim(), ignoreCase = true) ||
                    suggestion.exercise.alias.equals(query.trim(), ignoreCase = true))
        }
    }

    fun isCustomId(id: String?): Boolean =
        id.orEmpty().startsWith(CUSTOM_PREFIX, ignoreCase = true)

    private fun resolverFor(catalog: ExerciseCatalogV2): ExerciseCatalogV2Resolver {
        val cached = cachedResolver
        if (cached != null && cached.first == catalog.catalogRevision) return cached.second
        return ExerciseCatalogV2Resolver(catalog).also { cachedResolver = catalog.catalogRevision to it }
    }

    private fun resolveHit(
        configurationId: String?,
        definitionId: String,
        catalogIndex: Map<String, ExerciseMuscleInfo>,
    ): ExerciseMuscleInfo? {
        val keys = listOfNotNull(configurationId, definitionId)
        return keys.firstNotNullOfOrNull { key ->
            catalogIndex[key.lowercase()] ?: catalogIndex[key]
        }
    }

    private fun matchesName(exercise: ExerciseMuscleInfo, query: String): Boolean {
        val needle = query.lowercase()
        return exercise.name.lowercase().contains(needle) ||
            exercise.alias.orEmpty().lowercase().contains(needle)
    }

    private fun rankName(exercise: ExerciseMuscleInfo, query: String): Int {
        val needle = query.lowercase()
        val name = exercise.name.lowercase()
        val alias = exercise.alias.orEmpty().lowercase()
        return when {
            name == needle || alias == needle -> 100
            name.startsWith(needle) || alias.startsWith(needle) -> 80
            else -> 40
        }
    }
}
