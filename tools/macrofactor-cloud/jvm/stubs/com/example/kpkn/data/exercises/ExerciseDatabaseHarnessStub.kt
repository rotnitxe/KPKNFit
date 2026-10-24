package com.example.kpkn.data.exercises

import com.example.kpkn.data.models.ExerciseMuscleInfo
import com.example.kpkn.domain.exercises.VariantGroupIndex

/*
 * STUBS DEL ARNÉS JVM de la API de ExerciseDatabase.kt (excluido: usa Context, KpknDatabase y el asset del catálogo v2).
 * Solo está lo que el código puro llama. En el arnés no hay catálogo estático (asset v2): el único contenido es el
 * overlay de setCustomExerciseOverlay, que indexa igual que en la app. El catálogo v2 queda vacío, así que los tests
 * que dependan de él fallan por entorno (se listan en excluded-test.txt).
 * resolveCatalogExerciseInfoInIndex, buildCatalogExerciseNameLookup y catalogSearchRedirects son copias de su cuerpo
 * puro, para que las pruebas que pasan su propio índice se comporten igual.
 */

// Overlay de ejercicios personalizados (en la app: customExerciseOverlayCache + índice por id).
private var harnessOverlay: List<ExerciseMuscleInfo> = emptyList()
private var harnessIndex: Map<String, ExerciseMuscleInfo> = emptyMap()

/** Mismo efecto que en la app sobre el índice y las variantes, sin catálogo estático. */
fun setCustomExerciseOverlay(exercises: List<ExerciseMuscleInfo>) {
    harnessOverlay = exercises.toList()
    harnessIndex = harnessOverlay.associateBy { it.id.lowercase() }
    VariantGroupIndex.rebuild(harnessOverlay)
}

fun exerciseCatalogSnapshot(): List<ExerciseMuscleInfo> = harnessOverlay.toList()

fun catalogExerciseIndex(): Map<String, ExerciseMuscleInfo> = harnessIndex

fun catalogConfigurationDisplayName(configurationId: String?): String? = null

fun catalogSearchRedirects(): Map<String, String> = emptyMap()

fun isExerciseCatalogV2RuntimeReady(): Boolean = false

// Copia del cuerpo puro de ExerciseDatabase.kt (resolveExerciseId): el id se normaliza y debe existir en el índice.
fun resolveExerciseId(rawId: String?): String? {
    val normalized = rawId?.trim()?.lowercase().orEmpty()
    if (normalized.isBlank()) return null
    return normalized.takeIf { harnessIndex.containsKey(it) }
}

fun resolveExercise(rawId: String?): ExerciseMuscleInfo? =
    resolveExerciseId(rawId)?.let { harnessIndex[it] }

fun resolveCatalogExerciseInfo(
    catalogConfigurationId: String?,
    exerciseDbId: String?,
    exerciseId: String?,
    exerciseName: String?,
): ExerciseMuscleInfo? = resolveCatalogExerciseInfoInIndex(
    index = catalogExerciseIndex(),
    catalogConfigurationId = catalogConfigurationId,
    exerciseDbId = exerciseDbId,
    exerciseId = exerciseId,
    exerciseName = exerciseName,
)

// Copia del cuerpo puro de ExerciseDatabase.kt.
internal fun resolveCatalogExerciseInfoInIndex(
    index: Map<String, ExerciseMuscleInfo>,
    catalogConfigurationId: String?,
    exerciseDbId: String?,
    exerciseId: String?,
    exerciseName: String?,
): ExerciseMuscleInfo? {
    listOfNotNull(catalogConfigurationId, exerciseDbId, exerciseId)
        .map { it.trim().lowercase() }
        .filter { it.isNotBlank() }
        .distinct()
        .forEach { id ->
            val canonicalId = catalogSearchRedirects()[id] ?: id
            index[canonicalId]?.let { return it }
        }
    return null
}

// Copia del cuerpo puro de ExerciseDatabase.kt (con su normalizador privado, copiado abajo).
internal fun buildCatalogExerciseNameLookup(
    index: Map<String, ExerciseMuscleInfo>,
): Map<String, ExerciseMuscleInfo> {
    val lookup = index
        .mapKeys { (key, _) -> key.trim().lowercase() }
        .toMutableMap()

    fun addName(raw: String?, info: ExerciseMuscleInfo) {
        val normalized = harnessNormalizeCatalogSearchText(raw.orEmpty())
        if (normalized.isNotBlank()) lookup.putIfAbsent(normalized, info)
    }

    index.values.forEach { info ->
        addName(info.name, info)
        info.alias.orEmpty()
            .split(',')
            .forEach { alias -> addName(alias, info) }
    }
    return lookup
}

// Copia de normalizeCatalogSearchText (ExerciseDatabase.kt, privada allí).
private fun harnessNormalizeCatalogSearchText(value: String): String {
    return java.text.Normalizer.normalize(value.lowercase(java.util.Locale.ROOT), java.text.Normalizer.Form.NFD)
        .replace(Regex("\\p{Mn}+"), "")
        .trim()
}
