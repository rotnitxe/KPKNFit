package com.example.kpkn.data.exercises

import android.content.Context
import com.example.kpkn.data.models.ExerciseMuscleInfo
import kotlinx.serialization.json.Json

private const val EXERCISE_DATABASE_ASSET = "exercise_database.json"
private const val EXERCISE_ALIASES_ASSET = "exercise_id_aliases.json"

private val exerciseCatalogJson = Json { ignoreUnknownKeys = true }
private val exerciseCatalogLock = Any()
private val extraWikiLabExerciseAliases = mapOf(
    "db_exp_face_pull" to "tren_superior_face_pull_polea",
    "db_plank" to "ultimo_plancha_frontal",
    "db_exp_hammer_curl" to "tren_superior_curl_martillo_mancuernas",
    "db_ab_wheel" to "ultimo_plancha_rodillo",
    "db_hanging_leg_raises" to "ultimo_elevacion_piernas_paralelas",
)

@Volatile
private var exerciseDatabaseCache: List<ExerciseMuscleInfo> = emptyList()

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

        val exercises = exerciseCatalogJson.decodeFromString<List<ExerciseMuscleInfo>>(exercisesJson)
        val aliases = exerciseCatalogJson.decodeFromString<Map<String, String>>(aliasesJson)

        exerciseDatabaseCache = exercises
        exerciseDatabaseByIdCache = exercises.associateBy { it.id.lowercase() }
        exerciseAliasCache = (
            aliases.mapKeys { it.key.lowercase() }.mapValues { it.value.lowercase() } +
                extraWikiLabExerciseAliases.mapKeys { it.key.lowercase() }.mapValues { it.value.lowercase() }
            )
        exerciseCatalogInitialized = true
    }
}

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
