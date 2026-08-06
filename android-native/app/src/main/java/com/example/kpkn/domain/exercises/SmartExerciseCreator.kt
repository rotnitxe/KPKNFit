package com.example.kpkn.domain.exercises

import com.example.kpkn.data.models.AnatomicalConsideration
import com.example.kpkn.data.models.CommonMistake
import com.example.kpkn.data.models.ExerciseMuscleInfo
import com.example.kpkn.data.models.InvolvedMuscle
import com.example.kpkn.data.models.resolveMuscleVolumeContribution
import java.util.UUID

/**
 * Smart creation integrated into the catalog search: when a search yields zero
 * results the user can create the exercise inline, and its technical data is
 * derived by coincidence with the closest existing exercise.
 *
 * Name-first matching (the user typed a variant such as "Peso muerto rumano
 * con mancuernas"), filtered/boosted by the chosen implemento and by the
 * detected movement pattern, then AUGE/RINGS and muscle involvement are
 * inferred from the matched exercises through
 * [ExerciseMatchEngine.inferFromMatches]. ttc and axialLoadFactor come from the
 * best match; the per-muscle volume/role/emphasis is propagated from the same
 * match (explicit catalog values or role fallback 1.0/0.5/0.4), never from the
 * match score.
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

    /**
     * Crea (o reutiliza, si `existingId` viene en el request) un ejercicio
     * personalizado a partir de la detección automática.
     */
    fun create(request: SmartCreateRequest, catalog: List<ExerciseMuscleInfo>): ExerciseMuscleInfo =
        preview(request, catalog).exercise

    /**
     * Creación automática sin formulario: toma el nombre buscado y deriva
     * implemento/estación/lateralidad (chips) del mejor match de referencia.
     * El usuario ajusta esos datos después, al editar el ejercicio.
     */
    fun createAutomatic(name: String, catalog: List<ExerciseMuscleInfo>): ExerciseMuscleInfo {
        val trimmed = name.trim()
        val initial = preview(SmartCreateRequest(name = trimmed, implementoId = ""), catalog)
        val ref = initial.reference
        val request = SmartCreateRequest(
            name = trimmed,
            implementoId = ref?.equipment?.let { implementoIdFromLabel(it) } ?: "",
            estacionId = ref?.catalogVariantChips?.getOrNull(1)?.let { estacionIdFromLabel(it) },
            lateralidadId = ref?.catalogVariantChips?.getOrNull(2)?.let { lateralidadIdFromLabel(it) },
        )
        return preview(request, catalog).exercise
    }

    /**
     * Vista previa del creador inteligente: expone el ejercicio derivado, el
     * patrón detectado, cuántos matches hubo y si conviene ofrecer edición
     * manual del involucramiento muscular.
     */
    fun preview(request: SmartCreateRequest, catalog: List<ExerciseMuscleInfo>): SmartCreatePreview {
        val name = request.name.trim()
        val equipment = IMPLEMENTO_LABELS[request.implementoId] ?: "Mancuerna"
        val detected = ExercisePatternDetector.detect(name)
        val matches = findBestMatches(catalog, name, equipment, detected)
        val suggestions = inferFromMatches(
            matches = matches,
            name = name,
            equipment = equipment,
            force = matches.firstOrNull()?.exercise?.force ?: "",
            category = "Fuerza",
            isAxialLoaded = equipment in AXIAL_EQUIPMENT,
        )
        val top = matches.firstOrNull()?.exercise
        val involved = buildInvolvedMuscles(request, suggestions, top)

        val chosenOptions = buildList {
            request.implementoId.takeIf { it.isNotBlank() }?.let { add(IMPLEMENTO_LABELS[it] ?: it) }
            request.estacionId?.let { add(STATION_LABELS[it] ?: it) }
            request.lateralidadId?.let { add(LATERALITY_LABELS[it] ?: it) }
        }

        val displayName = name.ifBlank { "Ejercicio nuevo" }
        val description = request.description?.trim()?.takeIf { it.isNotBlank() }
            ?: if (matches.isNotEmpty()) {
                autoGenerateCustomExerciseDescription(
                    ExerciseMuscleInfo(
                        id = "auto",
                        name = displayName,
                        involvedMuscles = involved,
                        equipment = equipment,
                    ),
                    detected,
                )
            } else null

        val exercise = ExerciseMuscleInfo(
            id = request.existingId ?: "custom:${UUID.randomUUID()}",
            name = displayName,
            alias = name,
            description = description,
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

        val manualRecommended = matches.isEmpty() ||
            detected == null ||
            detected.confidence != ExercisePatternDetector.PatternConfidence.HIGH
        return SmartCreatePreview(
            exercise = exercise,
            detectedPattern = detected,
            matchCount = matches.size,
            manualRecommended = manualRecommended,
            reference = top,
        )
    }

    private fun buildInvolvedMuscles(
        request: SmartCreateRequest,
        suggestions: InferredSuggestions,
        top: ExerciseMuscleInfo?,
    ): List<InvolvedMuscle> {
        if (!request.musclesOverride.isNullOrEmpty()) {
            return request.musclesOverride.map { muscle ->
                muscle.copy(volumeContribution = resolveMuscleVolumeContribution(muscle))
            }
        }
        val byName = top?.involvedMuscles?.associateBy { it.muscle.lowercase() } ?: emptyMap()
        return suggestions.suggestedMuscles.map { muscle ->
            val source = byName[muscle.muscle.lowercase()]
            muscle.copy(
                role = source?.role ?: muscle.role,
                volumeContribution = source?.let { resolveMuscleVolumeContribution(it) }
                    ?: resolveMuscleVolumeContribution(muscle),
                emphasis = source?.emphasis ?: muscle.emphasis,
                biomechanicalReason = source?.biomechanicalReason ?: muscle.biomechanicalReason,
            )
        }
    }

    private fun findBestMatches(
        catalog: List<ExerciseMuscleInfo>,
        name: String,
        equipment: String,
        pattern: ExercisePatternDetector.DetectedMovementPattern? = null,
        maxResults: Int = 8,
    ): List<ExerciseMatchResult> {
        val tokens = tokenize(name)
        val queryGroup = EQUIPMENT_GROUPS[equipment]
        val mentionedMuscles = ExerciseMatchLexicon.mentionedMuscleGroups(name)
        return catalog
            .filter { it.efc != null && it.cnc != null }
            .map { candidate ->
                val candidateText = candidate.alias ?: candidate.name
                val sim = ExerciseMatchLexicon.tokenSimilarity(name, candidateText)
                var score = sim * 0.7
                val candidateGroup = candidate.equipment?.let { EQUIPMENT_GROUPS[it] }
                score += when {
                    candidate.equipment?.equals(equipment, ignoreCase = true) == true -> 0.30
                    queryGroup != null && candidateGroup == queryGroup -> 0.20
                    else -> 0.0
                }
                if (pattern != null) {
                    score += when {
                        candidate.movementPattern?.equals(pattern.patternId, ignoreCase = true) == true -> 0.18
                        candidate.force?.equals(pattern.label, ignoreCase = true) == true -> 0.15
                        else -> 0.0
                    }
                }
                if (ExerciseMatchLexicon.containsKnownPhrase(name, candidateText)) {
                    score += 0.22
                }
                if (mentionedMuscles.isNotEmpty() && candidate.involvedMuscles.isNotEmpty()) {
                    val candidateMuscles = candidate.involvedMuscles
                        .map { ExerciseMatchLexicon.normalize(it.muscle) }
                        .toSet()
                    val muscleHit = mentionedMuscles.any { m ->
                        val normalized = ExerciseMatchLexicon.normalize(m)
                        candidateMuscles.any { it.contains(normalized) }
                    }
                    if (muscleHit) score += 0.12
                }
                ExerciseMatchResult(exercise = candidate, score = score) to sim
            }
            // Solo coincidencias con solapamiento real: un ejercicio que comparte
            // equipo pero ningún token/sinónimo de nombre (ej. "Curl" al buscar
            // "peso muerto rumano") contaminaría la inferencia AUGE.
            .filter { (_, sim) -> tokens.isEmpty() || sim > 0.0 }
            .filter { (result, _) -> result.score > 0.15 }
            .sortedByDescending { it.first.score }
            .take(maxResults)
            .map { it.first }
    }
    private val NAME_TOKENS_TO_IGNORE = setOf(
        "el", "la", "los", "las", "de", "del", "con", "en", "para", "y", "a", "al",
        "un", "una", "variante", "estilo", "bilateral", "unilateral", "alternado",
        "ejercicio", "ejercicios",
    )

    private fun tokenize(name: String): Set<String> =
        ExerciseMatchLexicon.tokenKeys(name).filter { it !in NAME_TOKENS_TO_IGNORE }.toSet()
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

    /** Devuelve el id de implemento a partir de la etiqueta guardada en chips. */
    fun implementoIdFromLabel(label: String): String? =
        IMPLEMENTO_LABELS.entries.firstOrNull { it.value.equals(label, ignoreCase = true) }?.key

    fun estacionIdFromLabel(label: String): String? =
        STATION_LABELS.entries.firstOrNull { it.value.equals(label, ignoreCase = true) }?.key

    fun lateralidadIdFromLabel(label: String): String? =
        LATERALITY_LABELS.entries.firstOrNull { it.value.equals(label, ignoreCase = true) }?.key
}

data class SmartCreateRequest(
    val name: String,
    val implementoId: String,
    val estacionId: String? = null,
    val lateralidadId: String? = null,
    /** Descripción escrita por el usuario; null/blanco dispara autogeneración. */
    val description: String? = null,
    /** Override manual del involucramiento muscular (rol + aporte). */
    val musclesOverride: List<InvolvedMuscle>? = null,
    /** Id existente para editar un ejercicio personalizado; null crea uno nuevo. */
    val existingId: String? = null,
)

data class SmartCreatePreview(
    val exercise: ExerciseMuscleInfo,
    val detectedPattern: ExercisePatternDetector.DetectedMovementPattern?,
    val matchCount: Int,
    val manualRecommended: Boolean,
    /** Mejor match del catálogo usado como referencia (puede ser null). */
    val reference: ExerciseMuscleInfo? = null,
)
