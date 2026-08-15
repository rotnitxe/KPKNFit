package com.example.kpkn.data.exercises

import android.content.Context
import com.example.kpkn.data.db.KpknDatabase
import com.example.kpkn.data.db.toEntity
import com.example.kpkn.data.db.toExerciseMuscleInfo
import com.example.kpkn.data.models.ExerciseMuscleInfo
import com.example.kpkn.data.exercises.catalogv2.toLegacyDefaultCatalog
import com.example.kpkn.data.exercises.catalogv2.toLegacyConfigurationLookup
import com.example.kpkn.domain.exercises.catalogv2.ExerciseCatalogV2Loader
import com.example.kpkn.domain.exercises.VariantGroupIndex
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.withContext

private const val EXERCISE_DATABASE_V2_ASSET = "exercise_catalog_v2.json"

private val exerciseCatalogLock = Any()

private val _exerciseCatalogReady = MutableStateFlow(false)
val exerciseCatalogReady: StateFlow<Boolean> = _exerciseCatalogReady.asStateFlow()

@Volatile
private var exerciseDatabaseCache: List<ExerciseMuscleInfo> = emptyList()

@Volatile
private var staticExerciseCache: List<ExerciseMuscleInfo> = emptyList()

@Volatile
private var customExerciseOverlayCache: List<ExerciseMuscleInfo> = emptyList()

@Volatile
private var exerciseDatabaseByIdCache: Map<String, ExerciseMuscleInfo> = emptyMap()

@Volatile
private var v2ConfigurationLookupCache: Map<String, ExerciseMuscleInfo> = emptyMap()

@Volatile
private var exerciseCatalogInitialized = false

fun initializeExerciseDatabase(context: Context) {
    if (exerciseCatalogInitialized) return

    synchronized(exerciseCatalogLock) {
        if (exerciseCatalogInitialized) return

        val assets = context.assets
        val v2Catalog = runCatching {
            val payload = assets.open(EXERCISE_DATABASE_V2_ASSET).bufferedReader().use { it.readText() }
            ExerciseCatalogV2Loader.decodeApproved(payload)
        }.getOrElse { failure ->
            throw IllegalStateException("Approved exercise catalog v2 failed to load", failure)
        }
        staticExerciseCache = v2Catalog.toLegacyDefaultCatalog().map(::normalizeExerciseLabels)
        val exercises = buildMergedExerciseCatalog()
        exerciseDatabaseCache = exercises
        v2ConfigurationLookupCache = v2Catalog.toLegacyConfigurationLookup()
            .mapValues { (_, value) -> normalizeExerciseLabels(value) }
        exerciseDatabaseByIdCache = (exercises.associateBy { it.id.lowercase() } + v2ConfigurationLookupCache)
        VariantGroupIndex.rebuild(exercises)
        exerciseCatalogInitialized = true
        _exerciseCatalogReady.value = true
    }
}

suspend fun loadCustomExercisesAsync(context: Context) {
    val customExercises = withContext(Dispatchers.IO) {
        runCatching {
            KpknDatabase.getInstance(context)
                .customExerciseDao()
                .getAll()
                .map { it.toExerciseMuscleInfo().copy(isCustom = true) }
        }.getOrDefault(emptyList())
    }
    synchronized(exerciseCatalogLock) {
        customExerciseOverlayCache = customExercises
        val merged = buildMergedExerciseCatalog()
        exerciseDatabaseCache = merged
        exerciseDatabaseByIdCache = merged.associateBy { it.id.lowercase() } + v2ConfigurationLookupCache
        VariantGroupIndex.rebuild(merged)
        _exerciseCatalogReady.value = true
    }
}

private fun buildMergedExerciseCatalog(): List<ExerciseMuscleInfo> =
    (staticExerciseCache + customExerciseOverlayCache)
        .associateBy { it.id.lowercase() }
        .values
        .toList()

/**
 * Repository-owned snapshot for presentation/analytics adapters.
 *
 * The catalog is intentionally exposed as a function rather than a global
 * mutable-map/list constant. Callers receive the current v2-backed snapshot
 * (including the custom overlay) and cannot retain a reference to the
 * repository's internal index.
 */
fun exerciseCatalogSnapshot(): List<ExerciseMuscleInfo> = exerciseDatabaseCache.toList()

/** Explicit v2 index; callers cannot construct identities from visible names. */
fun catalogExerciseIndex(): Map<String, ExerciseMuscleInfo> = exerciseDatabaseByIdCache

/** Search terms live on v2 definitions; no redirect table is used at runtime. */
fun catalogSearchRedirects(): Map<String, String> = emptyMap()

/** True only after an approved v2 asset has loaded successfully. */
fun isExerciseCatalogV2RuntimeReady(): Boolean =
    exerciseCatalogInitialized && v2ConfigurationLookupCache.isNotEmpty()

/**
 * Lookup used by analytics and workout history. Only exact v2 ids are accepted.
 */
fun buildExerciseCatalogLookup(
    catalog: List<ExerciseMuscleInfo> = exerciseCatalogSnapshot(),
): Map<String, ExerciseMuscleInfo> {
    return catalog.associateBy { it.id.lowercase() }
}

fun resolveExerciseId(rawId: String?): String? {
    val normalized = rawId?.trim()?.lowercase().orEmpty()
    if (normalized.isBlank()) return null
    return normalized.takeIf { exerciseDatabaseByIdCache.containsKey(it) }
}

fun resolveExercise(rawId: String?): ExerciseMuscleInfo? =
    resolveExerciseId(rawId)?.let { exerciseDatabaseByIdCache[it] }

/**
 * Resolves a persisted exercise reference against the live catalog overlay.
 * Custom exercises can be added after a screen/ViewModel was created, so this
 * intentionally reads the current index instead of accepting a captured map.
 */
fun resolveCatalogExerciseInfo(
    catalogConfigurationId: String?,
    exerciseDbId: String?,
    exerciseId: String?,
    exerciseName: String?,
): ExerciseMuscleInfo? = resolveCatalogExerciseInfoInIndex(
    index = exerciseDatabaseByIdCache,
    catalogConfigurationId = catalogConfigurationId,
    exerciseDbId = exerciseDbId,
    exerciseId = exerciseId,
    exerciseName = exerciseName,
)

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

    val normalizedName = normalizeCatalogSearchText(exerciseName.orEmpty())
    if (normalizedName.isBlank()) return null
    index[normalizedName]?.let { return it }
    return index.values.firstOrNull { info ->
        normalizeCatalogSearchText(info.name) == normalizedName ||
            info.alias.orEmpty()
                .split(',')
                .any { normalizeCatalogSearchText(it) == normalizedName }
    }
}

/**
 * Adds normalized exercise names and aliases as lookup keys without changing
 * the canonical id entries. Template faceting resolves many exercises in a
 * row; keeping this index beside the id map avoids scanning the whole catalog
 * for every name-based fallback.
 */
internal fun buildCatalogExerciseNameLookup(
    index: Map<String, ExerciseMuscleInfo>,
): Map<String, ExerciseMuscleInfo> {
    val lookup = index
        .mapKeys { (key, _) -> key.trim().lowercase() }
        .toMutableMap()

    fun addName(raw: String?, info: ExerciseMuscleInfo) {
        val normalized = normalizeCatalogSearchText(raw.orEmpty())
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

/**
 * Resolves a voice/search phrase only through curated v2 definition
 * searchTerms.  It deliberately does not compare against the visible
 * canonical name and never consults the removed alias redirect table.
 */
fun catalogSearchExerciseId(query: String?): String? {
    val normalized = normalizeCatalogSearchText(query.orEmpty())
    if (normalized.isBlank()) return null
    val candidates = staticExerciseCache.asSequence()
        .flatMap { exercise ->
            exercise.alias.orEmpty()
                .split(',')
                .asSequence()
                .map { term -> normalizeCatalogSearchText(term) to exercise.id }
        }
        .filter { (term, _) -> term.isNotBlank() }
        .toList()
    return candidates.firstOrNull { (term, _) -> term == normalized }?.second
        ?: candidates.firstOrNull { (term, _) -> term.contains(normalized) || normalized.contains(term) }?.second
}

fun setCustomExerciseOverlay(exercises: List<ExerciseMuscleInfo>) {
    synchronized(exerciseCatalogLock) {
        customExerciseOverlayCache = exercises
            .map { normalizeExerciseLabels(it.copy(isCustom = true)) }
        val merged = buildMergedExerciseCatalog()
        exerciseDatabaseCache = merged
        exerciseDatabaseByIdCache = merged.associateBy { it.id.lowercase() } + v2ConfigurationLookupCache
    }
}

fun upsertCustomExerciseOverlay(exercise: ExerciseMuscleInfo) {
    synchronized(exerciseCatalogLock) {
        val normalized = normalizeExerciseLabels(exercise.copy(isCustom = true))
        customExerciseOverlayCache = customExerciseOverlayCache
            .filterNot { it.id.equals(normalized.id, ignoreCase = true) } + normalized
        val merged = buildMergedExerciseCatalog()
        exerciseDatabaseCache = merged
        exerciseDatabaseByIdCache = merged.associateBy { it.id.lowercase() } + v2ConfigurationLookupCache
    }
}

fun removeCustomExerciseOverlay(exerciseId: String) {
    synchronized(exerciseCatalogLock) {
        customExerciseOverlayCache = customExerciseOverlayCache.filterNot { it.id.equals(exerciseId, ignoreCase = true) }
        val merged = buildMergedExerciseCatalog()
        exerciseDatabaseCache = merged
        exerciseDatabaseByIdCache = merged.associateBy { it.id.lowercase() } + v2ConfigurationLookupCache
    }
}

suspend fun addOrUpdateCustomExercise(context: Context, exercise: ExerciseMuscleInfo) {
    val normalized = normalizeExerciseLabels(exercise.copy(isCustom = true))
    upsertCustomExerciseOverlay(normalized)
    withContext(Dispatchers.IO) {
        runCatching {
            KpknDatabase.getInstance(context)
                .customExerciseDao()
                .upsert(normalized.toEntity())
        }
    }
}

private fun normalizeExerciseLabels(exercise: ExerciseMuscleInfo): ExerciseMuscleInfo =
    exercise.copy(
        name = normalizeInlineUppercaseP(exercise.name),
        alias = exercise.alias?.let(::normalizeInlineUppercaseP),
    )

private fun normalizeInlineUppercaseP(value: String): String {
    val chars = value.toCharArray()
    for (index in 1 until chars.lastIndex) {
        if (
            chars[index] == 'P' &&
            chars[index - 1].isLetter() &&
            chars[index + 1].isLowerCase()
        ) {
            chars[index] = 'p'
        }
    }
    return String(chars)
}

private fun normalizeCatalogSearchText(value: String): String {
    return java.text.Normalizer.normalize(value.lowercase(java.util.Locale.ROOT), java.text.Normalizer.Form.NFD)
        .replace(Regex("\\p{Mn}+"), "")
        .trim()
}
