package com.example.kpkn.domain.exercises

import com.example.kpkn.data.models.AnatomicalConsideration
import com.example.kpkn.data.models.CommonMistake
import com.example.kpkn.data.models.ExerciseMuscleInfo
import com.example.kpkn.data.models.InvolvedMuscle
import java.util.UUID

/**
 * Smart creation integrated into the catalog search: when a search yields zero
 * results the user can create the exercise inline, and its technical data is
 * derived by coincidence with the closest existing exercise.
 *
 * Name-first matching (the user typed a variant such as "Peso muerto rumano
 * con mancuernas"), filtered/boosted by the chosen implemento, then AUGE/RINGS
 * and muscle involvement are inferred from the matched exercises through
 * [ExerciseMatchEngine.inferFromMatches]. ttc and axialLoadFactor come from the
 * best match; the per-muscle emphasis is propagated from the same match.
 */
object SmartExerciseCreator {

    private val IMPLEMENTO_LABELS = mapOf(
        "barbell" to "Barra",
        "dumbbells" to "Mancuerna",
        "ez_bar" to "Barra EZ",
        "cable" to "Polea",
        "machine" to "Máquina",
        "smith_machine" to "Máquina Smith",
        "kettlebell" to "Kettlebell",
        "band" to "Banda elástica",
        "plate" to "Disco",
        "bodyweight" to "Peso Corporal",
        "safety_bar" to "Barra de Seguridad",
        "hex_bar" to "Barra hexagonal",
        "h_bar" to "Barra H",
        "t_bar" to "Barra T",
        "trx" to "TRX",
        "ghd" to "Máquina GHD",
    )

    private val AXIAL_EQUIPMENT = setOf(
        "Barra", "Máquina Smith", "Disco", "Barra hexagonal",
        "Barra de Seguridad", "Barra H", "Barra T",
    )

    private val EQUIPMENT_GROUPS = mapOf(
        "Barra" to "barbell", "Barra EZ" to "barbell", "Máquina Smith" to "barbell",
        "Barra hexagonal" to "barbell", "Barra de Seguridad" to "barbell",
        "Barra H" to "barbell", "Barra T" to "barbell",
        "Mancuerna" to "dumbbells", "Kettlebell" to "dumbbells",
        "Polea" to "cable", "Banda elástica" to "cable",
        "Máquina" to "machine", "Máquina GHD" to "machine", "TRX" to "machine",
        "Peso Corporal" to "bodyweight", "Disco" to "bodyweight",
    )

    fun create(request: SmartCreateRequest, catalog: List<ExerciseMuscleInfo>): ExerciseMuscleInfo {
        val name = request.name.trim()
        val equipment = IMPLEMENTO_LABELS[request.implementoId] ?: "Mancuerna"
        val matches = findBestMatches(catalog, name, equipment)
        val suggestions = inferFromMatches(
            matches = matches,
            name = name,
            equipment = equipment,
            force = matches.firstOrNull()?.exercise?.force ?: "",
            category = "Fuerza",
            isAxialLoaded = equipment in AXIAL_EQUIPMENT,
        )
        val top = matches.firstOrNull()?.exercise
        val emphasisByMuscle = top?.involvedMuscles.orEmpty()
            .associate { it.muscle.lowercase() to it.emphasis }
        val involved = suggestions.suggestedMuscles.map { muscle ->
            muscle.copy(emphasis = emphasisByMuscle[muscle.muscle.lowercase()] ?: muscle.emphasis)
        }

        val chosenOptions = buildList {
            request.implementoId.takeIf { it.isNotBlank() }?.let { add(IMPLEMENTO_LABELS[it] ?: it) }
            request.estacionId?.let { add(STATION_LABELS[it] ?: it) }
            request.lateralidadId?.let { add(LATERALITY_LABELS[it] ?: it) }
        }

        return ExerciseMuscleInfo(
            id = "custom:${UUID.randomUUID()}",
            name = name.ifBlank { "Ejercicio nuevo" },
            alias = name,
            involvedMuscles = involved,
            equipment = equipment,
            category = top?.category ?: "Fuerza",
            type = top?.type ?: "Básico",
            force = top?.force,
            chain = suggestions.suggestedChain,
            bodyPart = suggestions.suggestedBodyPart,
            tier = top?.tier ?: suggestions.suggestedTier,
            isCustom = true,
            efc = suggestions.efc ?: 2.5,
            cnc = suggestions.cnc ?: 2.5,
            ssc = suggestions.ssc ?: 0.3,
            ttc = top?.ttc ?: 1.0,
            axialLoadFactor = top?.axialLoadFactor ?: 0.0,
            averageRestSeconds = suggestions.suggestedRestSeconds,
            setupCues = suggestions.setupCues,
            executionCues = suggestions.executionCues,
            commonMistakes = suggestions.commonMistakes.map { CommonMistake(it.first, it.second) },
            anatomicalConsiderations = suggestions.anatomicalConsiderations
                .map { AnatomicalConsideration(it.first, it.second) },
            // Chips user-facing de la variante (Implemento · Estación · Lateralidad).
            catalogVariantChips = chosenOptions,
        )
    }

    private fun findBestMatches(
        catalog: List<ExerciseMuscleInfo>,
        name: String,
        equipment: String,
        maxResults: Int = 8,
    ): List<ExerciseMatchResult> {
        val tokens = tokenize(name)
        val queryGroup = EQUIPMENT_GROUPS[equipment]
        return catalog
            .filter { it.efc != null && it.cnc != null }
            .map { candidate ->
                val sim = nameSimilarity(tokens, candidate.alias ?: candidate.name)
                var score = sim * 0.7
                val candidateGroup = candidate.equipment?.let { EQUIPMENT_GROUPS[it] }
                score += when {
                    candidate.equipment?.equals(equipment, ignoreCase = true) == true -> 0.30
                    queryGroup != null && candidateGroup == queryGroup -> 0.20
                    else -> 0.0
                }
                ExerciseMatchResult(exercise = candidate, score = score) to sim
            }
            // Solo coincidencias con solapamiento real de tokens: un ejercicio que
            // comparte equipo pero ningún token de nombre (ej. "Curl" al buscar
            // "peso muerto rumano") contaminaría la inferencia AUGE.
            .filter { (_, sim) -> tokens.isEmpty() || sim > 0.0 }
            .filter { (result, _) -> result.score > 0.15 }
            .sortedByDescending { it.first.score }
            .take(maxResults)
            .map { it.first }
    }

    private val NAME_TOKENS_TO_IGNORE = setOf(
        "el", "la", "los", "las", "de", "del", "con", "en", "para", "y", "a", "al",
        "variante", "estilo", "barra", "mancuerna", "mancuernas",
        "maquina", "polea", "kettlebell", "banda", "peso", "corporal", "sentado",
        "de pie", "bilateral", "unilateral", "alternado",
    )

    private fun tokenize(name: String): Set<String> =
        name.lowercase()
            .replace(Regex("[^a-záéíóúñü\\s]"), " ")
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

    private val STATION_LABELS = mapOf("seated" to "Sentado", "standing" to "De pie")
    private val LATERALITY_LABELS = mapOf(
        "bilateral" to "Bilateral",
        "unilateral" to "Unilateral",
        "alternating" to "Alternada",
    )

    val IMPLEMENTO_IDS: List<String> = IMPLEMENTO_LABELS.keys.sorted()
    val ESTACION_IDS: List<String> = STATION_LABELS.keys.sorted()
    val LATERALIDAD_IDS: List<String> = LATERALITY_LABELS.keys.sorted()

    fun implementoLabel(id: String): String = IMPLEMENTO_LABELS[id] ?: id
    fun estacionLabel(id: String): String = STATION_LABELS[id] ?: id
    fun lateralidadLabel(id: String): String = LATERALITY_LABELS[id] ?: id
}

data class SmartCreateRequest(
    val name: String,
    val implementoId: String,
    val estacionId: String? = null,
    val lateralidadId: String? = null,
)
