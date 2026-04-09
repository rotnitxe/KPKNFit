package com.example.kpkn.domain.exercises

import com.example.kpkn.data.models.ExerciseMuscleInfo
import com.example.kpkn.data.models.MuscleRole
import com.example.kpkn.domain.auge.ExerciseFatigueIndex
import com.example.kpkn.domain.training.VolumeCalculator
import kotlin.math.abs
import kotlin.math.max
import kotlin.math.min

enum class ExerciseCatalogRegion(val label: String) {
    ALL("Todo"),
    UPPER("Tren superior"),
    LOWER("Tren inferior"),
    CORE("Core"),
    FULL("Full body"),
}

enum class ExerciseCatalogTrait(val label: String) {
    BASIC("Básicos"),
    FREE("Libres"),
    MACHINE("En máquina"),
    UNILATERAL("Unilaterales"),
}

enum class ExerciseCatalogSort(val label: String) {
    RELEVANCE("Relevancia"),
    FATIGUE_HIGH("Fatiga alta"),
    FATIGUE_LOW("Fatiga baja"),
    NAME("Nombre"),
    MUSCLE("Músculo"),
}

data class FriendlyFatigueBreakdown(
    val muscle: Int,
    val snc: Int,
    val spinal: Int,
    val overall: Int,
)

enum class ExerciseKinshipBand(val label: String) {
    VERY_CLOSE("Parentesco alto"),
    CLOSE_VARIANT("Variante cercana"),
    TRANSFER_USEFUL("Transferencia util"),
}

data class ExerciseKinship(
    val exercise: ExerciseMuscleInfo,
    val band: ExerciseKinshipBand,
    val similarityScore: Int,
    val transferScore: Int,
    val rationale: String,
)

data class ExerciseKinshipResult(
    val similar: List<ExerciseKinship>,
    val transfer: List<ExerciseKinship>,
)

private val coreKeywords = listOf("abdomen", "core", "oblicuo", "transverso", "lumbar", "espalda baja", "recto abdominal")
private val upperKeywords = listOf("pectoral", "pecho", "dorsal", "espalda", "trapecio", "romboide", "deltoide", "hombro", "bíceps", "biceps", "tríceps", "triceps", "antebrazo", "braquial")
private val lowerKeywords = listOf("cuádriceps", "cuadriceps", "glúteo", "gluteo", "glúteos", "gluteos", "femoral", "isquio", "pantorrilla", "gemelo", "sóleo", "soleo", "aductor", "abductor", "pierna", "cadera")

private fun broadMuscleLabel(raw: String): String {
    val canonical = VolumeCalculator.normalizeCanonicalMuscleGroup(raw)
    return when (canonical) {
        "Pectorales" -> "Pecho"
        "Deltoides" -> "Hombros"
        "Dorsales" -> "Espalda"
        "Trapecio" -> "Trapecio"
        "Bíceps" -> "Bíceps"
        "Tríceps" -> "Tríceps"
        "Antebrazo" -> "Antebrazos"
        "Cuádriceps" -> "Cuádriceps"
        "Isquiosurales" -> "Isquios"
        "Glúteos" -> "Glúteos"
        "Aductores" -> "Aductores"
        "Pantorrillas" -> "Pantorrillas"
        "Abdomen", "Core" -> "Core"
        "Erectores Espinales" -> "Espalda baja"
        else -> canonical
    }
}

fun resolvePrimaryMuscleLabel(info: ExerciseMuscleInfo): String {
    val primary = info.involvedMuscles.firstOrNull { it.role.name.equals("PRIMARY", ignoreCase = true) }?.muscle
        ?: info.involvedMuscles.firstOrNull()?.muscle
        ?: return "General"
    return broadMuscleLabel(primary)
}

fun resolveExerciseRegion(info: ExerciseMuscleInfo): ExerciseCatalogRegion {
    val bodyPart = info.bodyPart?.lowercase().orEmpty()
    val muscles = info.involvedMuscles.joinToString(" ") { it.muscle.lowercase() }
    return when {
        bodyPart == "full" -> ExerciseCatalogRegion.FULL
        bodyPart == "lower" -> ExerciseCatalogRegion.LOWER
        bodyPart == "upper" -> {
            if (coreKeywords.any { muscles.contains(it) } && !upperKeywords.any { muscles.contains(it) }) ExerciseCatalogRegion.CORE
            else ExerciseCatalogRegion.UPPER
        }
        coreKeywords.any { muscles.contains(it) } -> ExerciseCatalogRegion.CORE
        lowerKeywords.any { muscles.contains(it) } && !upperKeywords.any { muscles.contains(it) } -> ExerciseCatalogRegion.LOWER
        upperKeywords.any { muscles.contains(it) } -> ExerciseCatalogRegion.UPPER
        else -> ExerciseCatalogRegion.FULL
    }
}

fun matchesCatalogTrait(info: ExerciseMuscleInfo, trait: ExerciseCatalogTrait): Boolean {
    val lowerName = info.name.lowercase()
    return when (trait) {
        ExerciseCatalogTrait.BASIC -> info.type.equals("Básico", ignoreCase = true)
        ExerciseCatalogTrait.FREE -> info.equipment in setOf("Barra", "Mancuerna", "Kettlebell", "Peso Corporal", "Disco", "Eje", "Saco de arena", "Balón Medicinal", "Piedra", "Neumático", "TRX", "Banda")
        ExerciseCatalogTrait.MACHINE -> info.equipment in setOf("Máquina", "Polea")
        ExerciseCatalogTrait.UNILATERAL -> listOf(
            "unilateral", "una mano", "un brazo", "a un brazo", "a una mano",
            "una pierna", "1 pierna", "1 mano", "single", "split squat", "búlgara", "bulgara"
        ).any { lowerName.contains(it) }
    }
}

fun calculateFriendlyFatigue(info: ExerciseMuscleInfo): FriendlyFatigueBreakdown {
    val index = ExerciseFatigueIndex.fromIntrinsic(
        efc = info.efc,
        cnc = info.cnc,
        ssc = info.ssc,
    )
    return FriendlyFatigueBreakdown(
        muscle = index.muscle,
        snc = index.snc,
        spinal = index.spinal,
        overall = index.overall,
    )
}

fun inferSetupTimeLabel(info: ExerciseMuscleInfo): String {
    info.setupTime?.takeIf { it > 0 }?.let { seconds ->
        return when {
            seconds < 45 -> "Rápido"
            seconds < 90 -> "1 min aprox."
            seconds < 150 -> "2 min aprox."
            else -> "3+ min"
        }
    }
    return when (info.equipment) {
        "Barra" -> if (info.type.equals("Básico", true)) "2-3 min" else "1-2 min"
        "Máquina" -> "30-60 seg"
        "Polea" -> "45-75 seg"
        "Mancuerna", "Kettlebell" -> "45-90 seg"
        "Peso Corporal", "TRX", "Banda" -> "Muy rápido"
        else -> "1-2 min"
    }
}

fun inferLearningCurveLabel(info: ExerciseMuscleInfo): String {
    val technical = info.technicalDifficulty
    if (technical != null) {
        return when {
            technical < 2.2 -> "Baja"
            technical < 3.6 -> "Media"
            else -> "Alta"
        }
    }
    val fatigue = calculateFriendlyFatigue(info).overall
    return when {
        info.type.equals("Aislamiento", true) && fatigue <= 4 -> "Baja"
        info.type.equals("Básico", true) || info.equipment == "Barra" || resolveExerciseRegion(info) == ExerciseCatalogRegion.FULL -> "Alta"
        else -> "Media"
    }
}

fun inferTransferLabel(info: ExerciseMuscleInfo): String {
    info.functionalTransfer?.takeIf { it.isNotBlank() }?.let { return it }
    info.sportsRelevance?.takeIf { it.isNotEmpty() }?.let { return "Útil para ${it.take(3).joinToString(", ")}." }
    return when (resolveExerciseRegion(info)) {
        ExerciseCatalogRegion.FULL -> "Muy útil para fuerza general, coordinación y producción total de fuerza."
        ExerciseCatalogRegion.LOWER -> "Buena transferencia a salto, sprint, cambios de dirección y potencia del tren inferior."
        ExerciseCatalogRegion.UPPER -> "Buena transferencia a contacto, empuje, tracción y rendimiento del tren superior."
        ExerciseCatalogRegion.CORE -> "Ayuda a estabilizar y transmitir fuerza entre tren superior e inferior."
        ExerciseCatalogRegion.ALL -> "Aporta transferencia general según el objetivo del programa."
    }
}

private data class KinshipScoredCandidate(
    val exercise: ExerciseMuscleInfo,
    val similarity: Double,
    val transfer: Double,
    val rationale: String,
)

private fun roleWeight(role: MuscleRole): Double = when (role) {
    MuscleRole.PRIMARY -> 1.0
    MuscleRole.SECONDARY -> 0.55
    MuscleRole.STABILIZER -> 0.25
    MuscleRole.NEUTRALIZER -> 0.15
}

private fun muscleProfile(info: ExerciseMuscleInfo): Map<String, Double> {
    if (info.involvedMuscles.isEmpty()) return emptyMap()
    val aggregated = linkedMapOf<String, Double>()
    info.involvedMuscles.forEach { muscle ->
        val canonical = broadMuscleLabel(muscle.muscle)
        val contribution = max(muscle.volumeContribution ?: 0.0, 0.0)
        val weighted = roleWeight(muscle.role) * if (contribution > 0.0) contribution else 1.0
        aggregated[canonical] = (aggregated[canonical] ?: 0.0) + weighted
    }
    val total = aggregated.values.sum().takeIf { it > 0.0 } ?: return emptyMap()
    return aggregated.mapValues { (_, value) -> value / total }
}

private fun weightedJaccard(a: Map<String, Double>, b: Map<String, Double>): Double {
    if (a.isEmpty() || b.isEmpty()) return 0.0
    val keys = a.keys + b.keys
    val intersection = keys.sumOf { key -> min(a[key] ?: 0.0, b[key] ?: 0.0) }
    val union = keys.sumOf { key -> max(a[key] ?: 0.0, b[key] ?: 0.0) }
    return if (union <= 0.0) 0.0 else (intersection / union).coerceIn(0.0, 1.0)
}

private fun equipmentFamily(raw: String?): String {
    return when (raw?.lowercase()) {
        null, "" -> "unknown"
        "barra", "mancuerna", "kettlebell", "disco", "eje", "saco de arena", "piedra", "balón medicinal" -> "free_weights"
        "máquina", "polea" -> "machine"
        "peso corporal", "trx", "banda" -> "bodyweight"
        else -> raw.lowercase()
    }
}

private fun movementFamily(info: ExerciseMuscleInfo): String {
    val text = normalizeCatalogSearchValue(
        listOfNotNull(info.name, info.force, info.type, info.category, info.alias).joinToString(" ")
    )
    return when {
        listOf("sentadilla", "squat", "zancada", "lunge", "bulgara", "split squat", "step up", "prensa", "hack").any { text.contains(it) } -> "squat_knee"
        listOf("peso muerto", "deadlift", "rumano", "hip thrust", "bisagra", "good morning", "pull through", "rack pull").any { text.contains(it) } -> "hinge_hip"
        listOf("press banca", "bench", "flexiones", "fondos", "aperturas", "cruce").any { text.contains(it) } -> "horizontal_push"
        listOf("press militar", "overhead", "hombros", "arnold", "push press", "landmine").any { text.contains(it) } -> "vertical_push"
        listOf("dominada", "chin", "pull up", "jalon").any { text.contains(it) } -> "vertical_pull"
        listOf("remo", "row", "face pull").any { text.contains(it) } -> "horizontal_pull"
        listOf("curl", "biceps").any { text.contains(it) } -> "elbow_flexion"
        listOf("triceps", "extension").any { text.contains(it) } -> "elbow_extension"
        listOf("plancha", "pallof", "core", "ab wheel", "rodillo", "anti").any { text.contains(it) } -> "core_stability"
        else -> listOfNotNull(info.force?.lowercase(), resolveExerciseRegion(info).name.lowercase()).joinToString("_")
    }
}

private fun fatigueSimilarity(target: ExerciseMuscleInfo, candidate: ExerciseMuscleInfo): Double {
    val delta = abs(calculateFriendlyFatigue(target).overall - calculateFriendlyFatigue(candidate).overall)
    return (1.0 - (delta / 10.0)).coerceIn(0.0, 1.0)
}

private val COMPETITION_BASIC_PATTERNS = listOf(
    "peso muerto", "deadlift", "sentadilla", "squat", "press banca", "bench press",
)

private fun isCompetitionBasic(info: ExerciseMuscleInfo): Boolean {
    val lower = info.name.lowercase()
    return COMPETITION_BASIC_PATTERNS.any { lower.contains(it) } &&
        (info.type.equals("Básico", ignoreCase = true) || info.tier?.equals("T1", ignoreCase = true) == true)
}

private fun buildKinshipRationale(
    target: ExerciseMuscleInfo,
    candidate: ExerciseMuscleInfo,
    muscleOverlap: Double,
): String {
    val reasons = mutableListOf<String>()
    if (resolvePrimaryMuscleLabel(target) == resolvePrimaryMuscleLabel(candidate)) {
        reasons += "mismo musculo primario"
    }
    if (movementFamily(target) == movementFamily(candidate)) {
        reasons += "patron muy parecido"
    }
    if (target.force.equals(candidate.force, true)) {
        reasons += "misma direccion de fuerza"
    }
    if (equipmentFamily(target.equipment) == equipmentFamily(candidate.equipment)) {
        reasons += "equipamiento similar"
    }
    if (reasons.isEmpty()) {
        reasons += if (muscleOverlap >= 0.35) "transferencia por musculatura compartida" else "transferencia general de patron"
    }
    return reasons.take(2).joinToString(" + ")
}

fun buildExerciseKinships(
    target: ExerciseMuscleInfo,
    catalog: List<ExerciseMuscleInfo>,
    similarLimit: Int = 3,
    transferLimit: Int = 3,
): ExerciseKinshipResult {
    val targetProfile = muscleProfile(target)
    val targetPrimary = resolvePrimaryMuscleLabel(target)
    val targetRegion = resolveExerciseRegion(target)
    val targetFamily = movementFamily(target)

    val scored = catalog
        .asSequence()
        .filter { it.id != target.id }
        .map { candidate ->
            val candidateProfile = muscleProfile(candidate)
            val muscleOverlap = weightedJaccard(targetProfile, candidateProfile)
            val samePrimary = resolvePrimaryMuscleLabel(candidate) == targetPrimary
            val sameRegion = resolveExerciseRegion(candidate) == targetRegion
            val sameFamily = movementFamily(candidate) == targetFamily
            val sameForce = target.force.equals(candidate.force, true)
            val sameChain = target.chain.equals(candidate.chain, true)
            val sameEquipmentFamily = equipmentFamily(target.equipment) == equipmentFamily(candidate.equipment)
            val fatigueMatch = fatigueSimilarity(target, candidate)

            val similarity = (
                muscleOverlap * 0.56 +
                    (if (samePrimary) 0.14 else 0.0) +
                    (if (sameForce) 0.10 else 0.0) +
                    (if (sameFamily) 0.10 else 0.0) +
                    (if (sameEquipmentFamily) 0.05 else 0.0) +
                    (if (sameRegion) 0.03 else 0.0) +
                    fatigueMatch * 0.02
                ).coerceIn(0.0, 1.0)

            val transfer = (
                muscleOverlap * 0.30 +
                    (if (sameForce) 0.24 else 0.0) +
                    (if (sameChain) 0.16 else 0.0) +
                    (if (sameRegion) 0.14 else 0.0) +
                    (if (sameFamily) 0.10 else 0.0) +
                    fatigueMatch * 0.06
                ).coerceIn(0.0, 1.0)

            KinshipScoredCandidate(
                exercise = candidate,
                similarity = similarity,
                transfer = transfer,
                rationale = buildKinshipRationale(target, candidate, muscleOverlap),
            )
        }
        .toList()

    // "Otras opciones": similares ordenados por menor fatiga primero para mostrar alternativas más llevaderas
    val targetFatigue = calculateFriendlyFatigue(target).overall
    val similar = scored
        .filter { it.similarity >= 0.50 }
        .sortedWith(
            compareByDescending<KinshipScoredCandidate> { it.similarity }
                .thenBy { calculateFriendlyFatigue(it.exercise).overall } // menor fatiga primero
                .thenBy { it.exercise.name }
        )
        .take(similarLimit)
        .map { candidate ->
            val candidateFatigue = calculateFriendlyFatigue(candidate.exercise).overall
            val fatigueNote = when {
                candidateFatigue < targetFatigue - 1 -> " · menos fatigante"
                candidateFatigue > targetFatigue + 1 -> " · más fatigante"
                else -> ""
            }
            val band = if (candidate.similarity >= 0.72) ExerciseKinshipBand.VERY_CLOSE else ExerciseKinshipBand.CLOSE_VARIANT
            ExerciseKinship(
                exercise = candidate.exercise,
                band = band,
                similarityScore = (candidate.similarity * 100).toInt().coerceIn(0, 100),
                transferScore = (candidate.transfer * 100).toInt().coerceIn(0, 100),
                rationale = candidate.rationale + fatigueNote,
            )
        }

    val similarIds = similar.map { it.exercise.id }.toSet()
    // Transferencia cruzada: priorizar básicos de competencia (peso muerto, sentadilla, press banca)
    val transfer = scored
        .filter { it.exercise.id !in similarIds && it.transfer >= 0.45 }
        .sortedWith(
            compareByDescending<KinshipScoredCandidate> { isCompetitionBasic(it.exercise) }
                .thenByDescending { it.transfer }
                .thenByDescending { it.similarity }
                .thenBy { it.exercise.name }
        )
        .take(transferLimit)
        .map { candidate ->
            ExerciseKinship(
                exercise = candidate.exercise,
                band = ExerciseKinshipBand.TRANSFER_USEFUL,
                similarityScore = (candidate.similarity * 100).toInt().coerceIn(0, 100),
                transferScore = (candidate.transfer * 100).toInt().coerceIn(0, 100),
                rationale = if (isCompetitionBasic(candidate.exercise)) "básico de competencia · ${candidate.rationale}" else candidate.rationale,
            )
        }

    return ExerciseKinshipResult(similar = similar, transfer = transfer)
}

fun buildExerciseComparisons(
    target: ExerciseMuscleInfo,
    catalog: List<ExerciseMuscleInfo>,
    limit: Int = 3,
): List<ExerciseMuscleInfo> {
    return buildExerciseKinships(target, catalog, similarLimit = limit, transferLimit = 0)
        .similar
        .map { it.exercise }
}

fun normalizeCatalogSearchValue(value: String): String =
    value.lowercase()
        .replace("á", "a")
        .replace("é", "e")
        .replace("í", "i")
        .replace("ó", "o")
        .replace("ú", "u")
        .replace("ü", "u")
        .replace("ñ", "n")

fun calculateSearchScore(info: ExerciseMuscleInfo, query: String): Int {
    val normalizedQuery = normalizeCatalogSearchValue(query.trim())
    if (normalizedQuery.isBlank()) return 0
    val terms = normalizedQuery.split(" ").filter { it.isNotBlank() }
    val normalizedName = normalizeCatalogSearchValue(info.name)
    val nameTokens = normalizedName.split(" ").filter { it.isNotBlank() }
    val aliasNormalized = normalizeCatalogSearchValue(info.alias ?: "")
    val equipmentNormalized = normalizeCatalogSearchValue(info.equipment ?: "")
    val primaryMuscleNormalized = normalizeCatalogSearchValue(resolvePrimaryMuscleLabel(info))
    val regionNormalized = normalizeCatalogSearchValue(resolveExerciseRegion(info).label)
    val descriptionNormalized = normalizeCatalogSearchValue(info.description ?: "")

    val searchBlob = normalizeCatalogSearchValue(
        listOfNotNull(
            info.name,
            info.alias,
            info.description,
            info.equipment,
            info.category,
            info.type,
            resolvePrimaryMuscleLabel(info),
            info.involvedMuscles.joinToString(" ") { it.muscle }
        ).joinToString(" ")
    )

    var score = 0
    if (normalizedName == normalizedQuery) score += 220
    if (aliasNormalized == normalizedQuery) score += 180
    if (nameTokens.any { it == normalizedQuery }) score += 160
    if (normalizedName.startsWith(normalizedQuery)) score += 110
    if (normalizedName.contains(normalizedQuery)) score += 90
    if (aliasNormalized.contains(normalizedQuery) && aliasNormalized.isNotBlank()) score += 70
    if (primaryMuscleNormalized.contains(normalizedQuery)) score += 55
    if (equipmentNormalized.contains(normalizedQuery) && equipmentNormalized.isNotBlank()) score += 40
    if (regionNormalized.contains(normalizedQuery) && regionNormalized.isNotBlank()) score += 25

    if (searchBlob.contains(normalizedQuery)) {
        score += 80
    }

    val allTermsMatch = terms.isNotEmpty() && terms.all { term ->
        normalizedName.contains(term) ||
            aliasNormalized.contains(term) ||
            primaryMuscleNormalized.contains(term) ||
            equipmentNormalized.contains(term) ||
            descriptionNormalized.contains(term)
    }
    if (allTermsMatch) score += 120

    terms.forEach { term ->
        if (normalizedName.split(" ").any { it == term }) score += 30
        if (normalizedName.split(" ").any { it.startsWith(term) }) score += 20
        if (aliasNormalized.split(" ").any { it == term }) score += 18
        if (primaryMuscleNormalized.contains(term)) score += 16
        if (equipmentNormalized.contains(term)) score += 10
        if (descriptionNormalized.contains(term)) score += 6
        if (searchBlob.contains(term)) score += 10
    }

    return score
}
