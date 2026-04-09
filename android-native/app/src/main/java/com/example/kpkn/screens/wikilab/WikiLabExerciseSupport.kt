package com.example.kpkn.screens.wikilab

import androidx.compose.ui.graphics.Color
import com.example.kpkn.data.exercises.resolveExercise
import com.example.kpkn.data.models.InvolvedMuscle
import com.example.kpkn.data.models.MuscleRole
import com.example.kpkn.data.repository.WikiLabRepository
import com.example.kpkn.domain.training.VolumeCalculator

internal data class WikiLabExerciseLink(
    val id: String,
    val name: String,
    val subtitle: String = "",
)

private val CANONICAL_MUSCLE_COLORS = mapOf(
    "Pectorales" to Color(0xFFE53935),
    "Dorsales" to Color(0xFF1E88E5),
    "Trapecio" to Color(0xFF1976D2),
    "Deltoides" to Color(0xFFFF8F00),
    "Tríceps" to Color(0xFF7B1FA2),
    "Bíceps" to Color(0xFF8E24AA),
    "Antebrazo" to Color(0xFF795548),
    "Abdomen" to Color(0xFF00897B),
    "Cuádriceps" to Color(0xFF43A047),
    "Isquiosurales" to Color(0xFF2E7D32),
    "Glúteos" to Color(0xFF558B2F),
    "Aductores" to Color(0xFF7CB342),
    "Pantorrillas" to Color(0xFF33691E),
    "Core" to Color(0xFF00695C),
    "Erectores Espinales" to Color(0xFF1565C0),
    "Cuello" to Color(0xFF6D4C41),
)

internal fun wikilabMuscleColor(name: String): Color =
    CANONICAL_MUSCLE_COLORS[name] ?: Color(0xFF757575)

internal fun resolveWikiLabExerciseLinks(
    ids: List<String>,
    subtitle: String = "",
): List<WikiLabExerciseLink> {
    val seen = linkedSetOf<String>()
    return ids.mapNotNull { requestedId ->
        val exercise = resolveExercise(requestedId) ?: resolveExerciseFromNaturalLanguage(requestedId) ?: return@mapNotNull null
        if (!seen.add(exercise.id)) return@mapNotNull null
        WikiLabExerciseLink(
            id = exercise.id,
            name = exercise.name,
            subtitle = subtitle,
        )
    }
}

private fun resolveExerciseFromNaturalLanguage(raw: String): com.example.kpkn.data.models.ExerciseMuscleInfo? {
    val query = normalizeForLookup(raw)
    if (query.isBlank()) return null

    val normalizedTokens = query.split(" ").filter { it.isNotBlank() }
    if (normalizedTokens.isEmpty()) return null

    return com.example.kpkn.data.exercises.EXERCISE_DATABASE
        .asSequence()
        .map { exercise ->
            val name = normalizeForLookup(exercise.name)
            val alias = normalizeForLookup(exercise.alias.orEmpty())
            val haystack = "$name $alias"
            val tokenHits = normalizedTokens.count { token -> haystack.contains(token) }
            val score = when {
                name == query || alias == query -> 200
                name.contains(query) || alias.contains(query) -> 150
                else -> tokenHits * 22
            }
            exercise to score
        }
        .filter { (_, score) -> score > 0 }
        .sortedWith(compareByDescending<Pair<com.example.kpkn.data.models.ExerciseMuscleInfo, Int>> { it.second }.thenBy { it.first.name.length })
        .map { it.first }
        .firstOrNull()
}

private fun normalizeForLookup(raw: String): String =
    raw.lowercase()
        .replace("á", "a")
        .replace("é", "e")
        .replace("í", "i")
        .replace("ó", "o")
        .replace("ú", "u")
        .replace("ü", "u")
        .replace("ñ", "n")
        .replace("-", " ")
        .replace("_", " ")
        .trim()

internal fun resolveWikiLabMuscleId(muscleName: String): String? = when (muscleName) {
    "Pectorales" -> "pectoral"
    "Dorsales" -> "espalda"
    "Trapecio" -> "trapecio"
    "Deltoides" -> "deltoides"
    "Tríceps" -> "tríceps"
    "Bíceps" -> "bíceps"
    "Antebrazo" -> "antebrazo"
    "Abdomen" -> "abdomen"
    "Cuádriceps" -> "cuádriceps"
    "Isquiosurales" -> "isquiosurales"
    "Glúteos" -> "glúteos"
    "Aductores" -> "aductores"
    "Pantorrillas" -> "pantorrillas"
    "Core" -> "core"
    "Erectores Espinales" -> "erectores-espinales"
    "Cuello" -> "cuello"
    else -> null
}

internal fun canonicalMuscleDisplayName(raw: String, emphasis: String? = null): String =
    VolumeCalculator.normalizeCanonicalMuscleGroup(raw.replace('-', ' '), emphasis)

internal fun canonicalWikiLabMuscleId(raw: String, emphasis: String? = null): String? =
    resolveWikiLabMuscleId(canonicalMuscleDisplayName(raw, emphasis))

internal fun canonicalWikiLabMuscleIdFromEntityId(muscleId: String): String? {
    val entity = WikiLabRepository.getMuscleById(muscleId)
    val canonicalFromName = entity?.name?.let { canonicalWikiLabMuscleId(it) }
    if (canonicalFromName != null) return canonicalFromName
    val canonicalFromId = canonicalWikiLabMuscleId(muscleId)
    if (canonicalFromId != null) return canonicalFromId
    return entity?.let {
        if (
            it.id in setOf(
                "pectoral",
                "espalda",
                "hombros",
                "brazos",
                "piernas",
                "abdomen",
                "core",
                "deltoides",
                "bíceps",
                "tríceps",
                "antebrazo",
                "cuádriceps",
                "isquiosurales",
                "glúteos",
                "aductores",
                "pantorrillas",
                "erectores-espinales",
                "cuello",
                "trapecio",
            )
        ) it.id else null
    }
}

internal fun collapseInvolvedMusclesToCanonical(muscles: List<InvolvedMuscle>): List<InvolvedMuscle> {
    if (muscles.isEmpty()) return emptyList()

    val rolePriority = mapOf(
        MuscleRole.PRIMARY to 0,
        MuscleRole.SECONDARY to 1,
        MuscleRole.STABILIZER to 2,
        MuscleRole.NEUTRALIZER to 3,
    )

    val grouped = linkedMapOf<String, InvolvedMuscle>()
    muscles.forEach { item ->
        val canonical = canonicalMuscleDisplayName(item.muscle, item.emphasis)
        val existing = grouped[canonical]
        if (existing == null) {
            grouped[canonical] = item.copy(muscle = canonical)
        } else {
            val existingPriority = rolePriority[existing.role] ?: 99
            val incomingPriority = rolePriority[item.role] ?: 99
            val role = if (incomingPriority < existingPriority) item.role else existing.role
            val volumeContribution = maxOf(existing.volumeContribution ?: 0.0, item.volumeContribution ?: 0.0).takeIf { it > 0.0 }
            grouped[canonical] = existing.copy(muscle = canonical, role = role, volumeContribution = volumeContribution)
        }
    }
    return grouped.values.toList()
}
