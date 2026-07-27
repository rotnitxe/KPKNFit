package com.example.kpkn.data.exercises

import android.content.Context
import com.example.kpkn.data.db.KpknDatabase
import com.example.kpkn.data.db.toEntity
import com.example.kpkn.data.db.toExerciseMuscleInfo
import com.example.kpkn.data.models.ExerciseMuscleInfo
import com.example.kpkn.domain.exercises.VariantGroupIndex
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import kotlinx.serialization.json.Json

private const val EXERCISE_DATABASE_ASSET = "exercise_database.json"
private const val EXERCISE_ALIASES_ASSET = "exercise_id_aliases.json"

private val exerciseCatalogJson = Json { ignoreUnknownKeys = true }
private val exerciseCatalogLock = Any()
private val extraWikiLabExerciseAliases = mapOf(
    "db_exp_face_pull" to "deltoides_face_pull_polea",
    "db_plank" to "core_plancha_frontal_iso",
    "db_exp_hammer_curl" to "biceps_curl_de_pie_martillo_mancuernas",
    "db_ab_wheel" to "core_rueda_abdominal_rodillas",
    "db_hanging_leg_raises" to "core_elevacion_piernas_paralelas",
)

@Volatile
private var exerciseDatabaseCache: List<ExerciseMuscleInfo> = emptyList()

@Volatile
private var staticExerciseCache: List<ExerciseMuscleInfo> = emptyList()

@Volatile
private var customExerciseOverlayCache: List<ExerciseMuscleInfo> = emptyList()

@Volatile
private var exerciseDatabaseByIdCache: Map<String, ExerciseMuscleInfo> = emptyMap()

@Volatile
private var exerciseAliasCache: Map<String, String> = emptyMap()

@Volatile
private var exerciseCatalogInitialized = false

fun initializeExerciseDatabase(context: Context) {
    if (exerciseCatalogInitialized) return

    synchronized(exerciseCatalogLock) {
        if (exerciseCatalogInitialized) return

        val assets = context.assets
        val exercisesJson = assets.open(EXERCISE_DATABASE_ASSET).bufferedReader().use { it.readText() }
        val aliasesJson = assets.open(EXERCISE_ALIASES_ASSET).bufferedReader().use { it.readText() }

        val baseExercises = exerciseCatalogJson.decodeFromString<List<ExerciseMuscleInfo>>(exercisesJson)
            .map(::normalizeExerciseLabels)
        staticExerciseCache = baseExercises
        val exercises = buildMergedExerciseCatalog()
        val aliases = exerciseCatalogJson.decodeFromString<Map<String, String>>(aliasesJson)

        exerciseDatabaseCache = exercises
        exerciseDatabaseByIdCache = exercises.associateBy { it.id.lowercase() }
        exerciseAliasCache = (
            aliases.mapKeys { it.key.lowercase() }.mapValues { it.value.lowercase() } +
                extraWikiLabExerciseAliases.mapKeys { it.key.lowercase() }.mapValues { it.value.lowercase() }
            )
        VariantGroupIndex.rebuild(exercises)
        exerciseCatalogInitialized = true
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
        exerciseDatabaseByIdCache = merged.associateBy { it.id.lowercase() }
        VariantGroupIndex.rebuild(merged)
    }
}

private fun buildMergedExerciseCatalog(): List<ExerciseMuscleInfo> =
    (staticExerciseCache + customExerciseOverlayCache)
        .associateBy { it.id.lowercase() }
        .values
        .toList()

val EXERCISE_DATABASE: List<ExerciseMuscleInfo>
    get() = exerciseDatabaseCache

val EXERCISE_DATABASE_BY_ID: Map<String, ExerciseMuscleInfo>
    get() = exerciseDatabaseByIdCache

val EXERCISE_ID_ALIASES: Map<String, String>
    get() = exerciseAliasCache

fun resolveExerciseId(rawId: String?): String? {
    val normalized = rawId?.trim()?.lowercase().orEmpty()
    if (normalized.isBlank()) return null
    if (exerciseDatabaseByIdCache.containsKey(normalized)) return normalized
    val canonical = exerciseAliasCache[normalized] ?: return null
    return canonical.takeIf { exerciseDatabaseByIdCache.containsKey(it) }
}

fun resolveExercise(rawId: String?): ExerciseMuscleInfo? =
    resolveExerciseId(rawId)?.let { exerciseDatabaseByIdCache[it] }

fun setCustomExerciseOverlay(exercises: List<ExerciseMuscleInfo>) {
    synchronized(exerciseCatalogLock) {
        customExerciseOverlayCache = exercises
            .map { normalizeExerciseLabels(it.copy(isCustom = true)) }
        val merged = buildMergedExerciseCatalog()
        exerciseDatabaseCache = merged
        exerciseDatabaseByIdCache = merged.associateBy { it.id.lowercase() }
    }
}

fun upsertCustomExerciseOverlay(exercise: ExerciseMuscleInfo) {
    synchronized(exerciseCatalogLock) {
        val normalized = normalizeExerciseLabels(exercise.copy(isCustom = true))
        customExerciseOverlayCache = customExerciseOverlayCache
            .filterNot { it.id.equals(normalized.id, ignoreCase = true) } + normalized
        val merged = buildMergedExerciseCatalog()
        exerciseDatabaseCache = merged
        exerciseDatabaseByIdCache = merged.associateBy { it.id.lowercase() }
    }
}

fun removeCustomExerciseOverlay(exerciseId: String) {
    synchronized(exerciseCatalogLock) {
        customExerciseOverlayCache = customExerciseOverlayCache.filterNot { it.id.equals(exerciseId, ignoreCase = true) }
        val merged = buildMergedExerciseCatalog()
        exerciseDatabaseCache = merged
        exerciseDatabaseByIdCache = merged.associateBy { it.id.lowercase() }
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
