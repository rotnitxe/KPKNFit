package com.example.kpkn.domain.exercises

import com.example.kpkn.data.models.ExerciseMuscleInfo
import com.example.kpkn.data.models.InvolvedMuscle
import com.example.kpkn.data.models.MuscleRole

data class ExerciseMatchResult(
    val exercise: ExerciseMuscleInfo,
    val score: Double,
)

data class InferredSuggestions(
    /** null when no DB match has efc declared — UI should show "—" or omit the field. */
    val efc: Double?,
    /** null when no DB match has cnc declared — UI should show "—" or omit the field. */
    val cnc: Double?,
    /** null when no DB match has ssc declared — UI should show "—" or omit the field. */
    val ssc: Double?,
    val suggestedMuscles: List<InvolvedMuscle>,
    val suggestedBodyPart: String,
    val suggestedChain: String,
    val suggestedTier: String,
    val suggestedRestSeconds: Int,
    val matchCount: Int,
    val topMatches: List<ExerciseMatchResult>,
    val anatomicalConsiderations: List<Pair<String, String>>,
    val commonMistakes: List<Pair<String, String>>,
    val setupCues: List<String>,
    val executionCues: List<String>,
)

private val EQUIPMENT_GROUPS = mapOf(
    "barra" to setOf("barra", "barra olímpica", "barra ez", "barra corta"),
    "mancuerna" to setOf("mancuerna"),
    "maquina" to setOf("máquina", "maquina", "hack"),
    "polea" to setOf("polea", "cable"),
    "peso corporal" to setOf("peso corporal", "bandas", "ninguno"),
    "kettlebell" to setOf("kettlebell"),
)

private val FORCE_GROUPS = mapOf(
    "empuje" to setOf("empuje", "press", "extensión", "extension"),
    "tirón" to setOf("tirón", "tiron", "remo", "pull", "curl", "dominada", "face pull"),
    "sentadilla" to setOf("sentadilla", "squat", "step-up", "zancada", "lunge"),
    "bisagra" to setOf("bisagra", "deadlift", "peso muerto", "rumano", "hip thrust", "extensión cadera"),
    "anti-extension" to setOf("anti-extensión", "anti-extension", "plancha", "ab wheel", "rollout", "pallof"),
    "anti-flexion" to setOf("anti-flexión", "anti-flexion", "farmer", "granjero"),
    "anti-rotacion" to setOf("anti-rotación", "anti-rotacion", "pallof"),
    "flexion" to setOf("flexión", "flexion", "crunch", "elevación", "elevacion"),
)

private val CATEGORY_WEIGHTS = mapOf(
    "fuerza" to 1.0,
    "potencia" to 0.9,
    "hipertrofia" to 0.8,
    "isometría" to 0.7,
    "isometria" to 0.7,
)

private val TYPE_HIERARCHY = mapOf(
    "básico" to 1.0,
    "basico" to 1.0,
    "variante" to 0.8,
    "accesorio" to 0.6,
    "aislamiento" to 0.5,
)

private val NAME_TOKENS_TO_IGNORE = setOf(
    "con", "de", "del", "en", "para", "el", "la", "los", "las", "un", "una",
    "y", "o", "a", "al", "por", "sin",
)

private fun getEquipmentGroup(equip: String?): String? {
    val e = equip?.lowercase()?.trim() ?: return null
    return EQUIPMENT_GROUPS.entries.firstOrNull { e in it.value }?.key
}

private fun getForceGroup(force: String?): String? {
    val f = force?.lowercase()?.trim() ?: return null
    return FORCE_GROUPS.entries.firstOrNull { f in it.value }?.key
}

private fun tokenize(name: String): Set<String> =
    name.lowercase()
        .replace(Regex("[^a-záéíóúñü\\s]"), "")
        .split(Regex("\\s+"))
        .filter { it.isNotBlank() && it !in NAME_TOKENS_TO_IGNORE }
        .toSet()

private fun nameSimilarity(queryTokens: Set<String>, candidate: String): Double {
    if (queryTokens.isEmpty()) return 0.0
    val candidateTokens = tokenize(candidate)
    if (candidateTokens.isEmpty()) return 0.0
    val intersection = queryTokens.intersect(candidateTokens)
    val jaccard = intersection.size.toDouble() / (queryTokens.size + candidateTokens.size - intersection.size)
    val overlap = intersection.size.toDouble() / queryTokens.size
    return (jaccard * 0.6 + overlap * 0.4).coerceIn(0.0, 1.0)
}

fun findBestMatches(
    database: List<ExerciseMuscleInfo>,
    name: String,
    equipment: String,
    force: String,
    category: String,
    type: String,
    bodyPart: String,
    chain: String,
    maxResults: Int = 8,
): List<ExerciseMatchResult> {
    val queryTokens = tokenize(name)
    val queryEquipGroup = getEquipmentGroup(equipment)
    val queryForceGroup = getForceGroup(force)
    val queryCategory = category.lowercase().trim()
    val queryType = type.lowercase().trim()
    val queryBodyPart = bodyPart.lowercase().trim()
    val queryChain = chain.lowercase().trim()

    return database
        .filter { it.efc != null && it.cnc != null }
        .map { candidate ->
            var score = 0.0

            // Equipment match (30% weight)
            val candidateEquipGroup = getEquipmentGroup(candidate.equipment)
            score += when {
                candidate.equipment?.equals(equipment, ignoreCase = true) == true -> 0.30
                queryEquipGroup != null && candidateEquipGroup == queryEquipGroup -> 0.25
                queryEquipGroup != null && candidateEquipGroup != null -> 0.05
                else -> 0.0
            }

            // Force/pattern match (25% weight)
            val candidateForceGroup = getForceGroup(candidate.force)
            score += when {
                candidate.force?.equals(force, ignoreCase = true) == true -> 0.25
                queryForceGroup != null && candidateForceGroup == queryForceGroup -> 0.22
                queryForceGroup != null && candidateForceGroup != null -> 0.05
                else -> 0.0
            }

            // Category match (20% weight)
            score += when {
                candidate.category?.equals(category, ignoreCase = true) == true -> 0.20
                queryCategory.isNotBlank() && candidate.category?.lowercase() == queryCategory -> 0.18
                queryCategory.isNotBlank() -> 0.02
                else -> 0.0
            }

            // Type match (10% weight)
            val typeDiff = TYPE_HIERARCHY[queryType] to TYPE_HIERARCHY[candidate.type?.lowercase()?.trim()]
            score += when {
                candidate.type?.equals(type, ignoreCase = true) == true -> 0.10
                typeDiff.first != null && typeDiff.second != null -> 0.10 * (1.0 - kotlin.math.abs(typeDiff.first!! - typeDiff.second!!))
                else -> 0.0
            }

            // Body part match (5% weight)
            score += when {
                candidate.bodyPart?.equals(bodyPart, ignoreCase = true) == true -> 0.05
                queryBodyPart.isNotBlank() && candidate.bodyPart?.lowercase() == queryBodyPart -> 0.04
                else -> 0.0
            }

            // Chain match (5% weight)
            score += when {
                candidate.chain?.equals(chain, ignoreCase = true) == true -> 0.05
                queryChain.isNotBlank() && candidate.chain?.lowercase() == queryChain -> 0.04
                else -> 0.0
            }

            // Name similarity (5% weight)
            score += nameSimilarity(queryTokens, candidate.name) * 0.05

            ExerciseMatchResult(exercise = candidate, score = score)
        }
        .filter { it.score > 0.15 }
        .sortedByDescending { it.score }
        .take(maxResults)
}

fun inferFromMatches(
    matches: List<ExerciseMatchResult>,
    name: String,
    equipment: String,
    force: String,
    category: String,
    isAxialLoaded: Boolean,
): InferredSuggestions {
    if (matches.isEmpty()) {
        // No DB matches found — use structural inference as creation-time suggestion only.
        // These values are NOT used for runtime drain calculations; they are prefill suggestions
        // that the user can edit. Mark them null if not available in DB.
        val fallback = inferExerciseMetrics(
            type = "Accesorio",
            force = force,
            equipment = equipment,
            category = category,
            isAxialLoaded = isAxialLoaded,
            exerciseName = name,
        )
        return InferredSuggestions(
            efc = fallback.efc,
            cnc = fallback.cnc,
            ssc = fallback.ssc,
            suggestedMuscles = fallback.suggestedMuscles,
            suggestedBodyPart = fallback.suggestedBodyPart,
            suggestedChain = fallback.suggestedChain,
            suggestedTier = "T2",
            suggestedRestSeconds = 90,
            matchCount = 0,
            topMatches = emptyList(),
            anatomicalConsiderations = emptyList(),
            commonMistakes = emptyList(),
            setupCues = emptyList(),
            executionCues = emptyList(),
        )
    }

    // Weighted average of AUGE metrics from matches — only use exercises that have complete DB data.
    // Fallback ?: defaults are intentionally removed: if DB doesn't have the value, exclude that
    // match from the average rather than polluting it with invented numbers.
    val matchesWithMetrics = matches.filter { it.exercise.efc != null && it.exercise.cnc != null && it.exercise.ssc != null }
    val totalWeight = if (matchesWithMetrics.isNotEmpty()) matchesWithMetrics.sumOf { it.score } else matches.sumOf { it.score }
    val avgEfc = if (matchesWithMetrics.isNotEmpty())
        matchesWithMetrics.sumOf { it.exercise.efc!! * it.score } / totalWeight
    else null
    val avgCnc = if (matchesWithMetrics.isNotEmpty())
        matchesWithMetrics.sumOf { it.exercise.cnc!! * it.score } / totalWeight
    else null
    val avgSsc = if (matchesWithMetrics.isNotEmpty())
        matchesWithMetrics.sumOf { it.exercise.ssc!! * it.score } / totalWeight
    else null

    // Adjust for axial load
    val sscMultiplier = if (isAxialLoaded) 1.0 else 0.4

    // Equipment adjustments (same as original inference)
    val (eqEfcMult, eqCncMult) = when {
        equipment.equals("Barra", ignoreCase = true) -> 1.0 to 1.2
        equipment.equals("Mancuerna", ignoreCase = true) -> 0.9 to 1.1
        equipment.equals("Máquina", ignoreCase = true) || equipment.equals("Polea", ignoreCase = true) -> 0.8 to 0.6
        equipment.equals("Peso Corporal", ignoreCase = true) -> 0.8 to 0.8
        else -> 1.0 to 1.0
    }

    val efc = avgEfc?.let { (it * eqEfcMult).coerceIn(0.5, 5.0) }
    val cnc = avgCnc?.let { (it * eqCncMult).coerceIn(0.5, 5.0) }
    val ssc = avgSsc?.let { (it * sscMultiplier).coerceIn(0.0, 2.0) }

    // Merge muscles from top matches (weighted by score)
    val muscleScores = mutableMapOf<String, Pair<MuscleRole, Double>>()
    for (match in matches) {
        for (muscle in match.exercise.involvedMuscles) {
            val existing = muscleScores[muscle.muscle]
            val newScore = (muscle.volumeContribution ?: 1.0) * match.score
            if (existing == null || newScore > existing.second) {
                muscleScores[muscle.muscle] = muscle.role to newScore
            }
        }
    }

    val sortedMuscles = muscleScores.entries
        .sortedByDescending { it.value.second }
        .take(6)
        .map { InvolvedMuscle(it.key, it.value.first, it.value.second.coerceIn(0.3, 1.0)) }

    // Most common body part and chain from matches
    val bodyPart = matches
        .groupBy { it.exercise.bodyPart ?: "" }
        .maxByOrNull { it.value.sumOf { m -> m.score } }?.key ?: "upper"

    val chain = matches
        .groupBy { it.exercise.chain ?: "" }
        .maxByOrNull { it.value.sumOf { m -> m.score } }?.key ?: "full"

    // Tier from matches
    val tier = matches
        .groupBy { it.exercise.tier ?: "" }
        .filter { it.key.isNotBlank() }
        .maxByOrNull { it.value.sumOf { m -> m.score } }?.key ?: "T2"

    // Rest seconds from matches
    val restSeconds = matches
        .mapNotNull { it.exercise.averageRestSeconds }
        .ifEmpty { listOf(90) }
        .average()
        .toInt()

    // Anatomical considerations from top matches
    val anatomical = matches
        .flatMap { it.exercise.anatomicalConsiderations ?: emptyList() }
        .distinctBy { it.trait }
        .take(4)
        .map { it.trait to it.advice }

    // Common mistakes from top matches
    val mistakes = matches
        .flatMap { it.exercise.commonMistakes ?: emptyList() }
        .distinctBy { it.mistake }
        .take(4)
        .map { it.mistake to it.correction }

    // Setup cues from top matches
    val setupCues = matches
        .flatMap { it.exercise.setupCues ?: emptyList() }
        .distinct()
        .take(3)

    // Execution cues from top matches
    val executionCues = matches
        .flatMap { it.exercise.executionCues ?: emptyList() }
        .distinct()
        .take(3)

    return InferredSuggestions(
        efc = efc,
        cnc = cnc,
        ssc = ssc,
        suggestedMuscles = sortedMuscles,
        suggestedBodyPart = bodyPart,
        suggestedChain = chain,
        suggestedTier = tier,
        suggestedRestSeconds = restSeconds,
        matchCount = matches.size,
        topMatches = matches,
        anatomicalConsiderations = anatomical,
        commonMistakes = mistakes,
        setupCues = setupCues,
        executionCues = executionCues,
    )
}
