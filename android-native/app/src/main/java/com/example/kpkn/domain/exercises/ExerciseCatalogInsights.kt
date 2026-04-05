package com.example.kpkn.domain.exercises

import com.example.kpkn.data.models.ExerciseMuscleInfo
import com.example.kpkn.domain.auge.ExerciseFatigueIndex
import com.example.kpkn.domain.training.VolumeCalculator

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

fun buildExerciseComparisons(
    target: ExerciseMuscleInfo,
    catalog: List<ExerciseMuscleInfo>,
    limit: Int = 3,
): List<ExerciseMuscleInfo> {
    val targetMuscle = resolvePrimaryMuscleLabel(target)
    val targetRegion = resolveExerciseRegion(target)
    val targetFatigue = calculateFriendlyFatigue(target).overall
    return catalog
        .asSequence()
        .filter { it.id != target.id }
        .filter { resolvePrimaryMuscleLabel(it) == targetMuscle || resolveExerciseRegion(it) == targetRegion }
        .sortedWith(
            compareBy<ExerciseMuscleInfo>(
                { if (it.equipment == target.equipment) 0 else 1 },
                { kotlin.math.abs(calculateFriendlyFatigue(it).overall - targetFatigue) },
                { it.name }
            )
        )
        .take(limit)
        .toList()
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
