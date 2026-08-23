package com.example.kpkn.data

import android.content.Context
import com.example.kpkn.data.db.*
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import kotlinx.serialization.json.Json
import kotlinx.serialization.Serializable

private val prepopulateJson = Json { ignoreUnknownKeys = true; encodeDefaults = true }

/** Static Concepts content revision; bump when the compact canonical rows change. */
const val APRENDE_CONTENT_REVISION = "conceptos-clave-v2-2026-08-23"
private const val APRENDE_CONTENT_PREFS = "aprende_content"
private const val APRENDE_CONTENT_PREF_KEY = "revision"

private fun logWikiLabError(category: String, throwable: Throwable) {
    if (com.example.kpkn.BuildConfig.DEBUG) {
        android.util.Log.e("WikiLabPrepopulate", "Error prepopulating $category", throwable)
    }
}

/**
 * Prepopulates WikiLab Room tables from JSON assets on first run.
 * Call from coroutine scope after database creation.
 */
suspend fun prepopulateWikiLabAssets(context: Context, db: KpknDatabase) = withContext(Dispatchers.IO) {
    val dao = db.wikiLabDao()
    val preferences = context.getSharedPreferences(APRENDE_CONTENT_PREFS, Context.MODE_PRIVATE)
    val currentRevision = preferences.getString(APRENDE_CONTENT_PREF_KEY, null)
    val counts = listOf(dao.getMuscleCount(), dao.getJointCount(), dao.getPatternCount())
    val tendonCount = dao.getTendonCount()
    val chainCount = dao.getChainCount()
    val hasData = counts.any { it > 0 } || tendonCount > 0 || chainCount > 0
    val populated = counts.all { it > 0 }
    if (populated && currentRevision == APRENDE_CONTENT_REVISION && tendonCount == 0 && chainCount == 0) return@withContext
    if (hasData && (!populated || currentRevision != APRENDE_CONTENT_REVISION)) {
        dao.clearMuscles()
        dao.clearJoints()
        dao.clearTendons()
        dao.clearPatterns()
        dao.clearChains()
    }
    var failures = 0

    val assets = context.assets

    // ─── Muscles ──────────────────────────────────────────────────────
    try {
        val muscleJson = assets.open("wikilab/muscles.json").bufferedReader().use { it.readText() }
        val muscleData: List<MuscleGroupDto> = prepopulateJson.decodeFromString(muscleJson)
        val muscleEntities = muscleData.map { dto ->
            MuscleGroupEntity(
                id = dto.id,
                name = dto.name,
                description = dto.description,
                bodyPart = dto.bodyPart,
                // The Concepts surface intentionally stores only the compact
                // canonical projection. Room columns remain for v23 schema
                // compatibility, but static rich anatomy is no longer loaded.
                coverImage = null,
                origin = null,
                insertion = null,
                mechanicalFunctions = null,
                mev = null,
                mav = null,
                mrv = null,
                recommendedExercises = null,
                relatedJoints = null,
                relatedTendons = null,
                importanceMovement = null,
                importanceHealth = null,
                aestheticImportance = null,
            )
        }
        dao.insertMuscles(muscleEntities)
    } catch (e: Exception) {
        failures++
        logWikiLabError("muscles", e)
    }

    // ─── Joints ──────────────────────────────────────────────────────
    try {
        val jointJson = assets.open("wikilab/joints.json").bufferedReader().use { it.readText() }
        val jointData: List<JointDto> = prepopulateJson.decodeFromString(jointJson)
        val jointEntities = jointData.map { dto ->
            JointEntity(
                id = dto.id,
                name = dto.name,
                type = dto.type,
                description = dto.description,
                bodyPart = dto.bodyPart,
                musclesCrossing = null,
                tendonsRelated = null,
                movementPatterns = null,
                commonInjuries = null,
                protectiveExercises = null,
            )
        }
        dao.insertJoints(jointEntities)
    } catch (e: Exception) {
        failures++
        logWikiLabError("joints", e)
    }

    // ─── Movement Patterns ────────────────────────────────────────────
    try {
        val patternJson = assets.open("wikilab/movement_patterns.json").bufferedReader().use { it.readText() }
        val patternData: List<MovementPatternDto> = prepopulateJson.decodeFromString(patternJson)
        val patternEntities = patternData.map { dto ->
            MovementPatternEntity(
                id = dto.id,
                name = dto.name,
                description = dto.description,
                forceTypes = null,
                chainTypes = null,
                primaryMuscles = null,
                primaryJoints = null,
                exampleExercises = null,
            )
        }
        dao.insertPatterns(patternEntities)
    } catch (e: Exception) {
        failures++
        logWikiLabError("patterns", e)
    }

    if (failures == 0) {
        preferences.edit().putString(APRENDE_CONTENT_PREF_KEY, APRENDE_CONTENT_REVISION).apply()
    }
}

// ─── DTOs for JSON parsing ──────────────────────────────────────────────────

@Serializable
private data class MuscleGroupDto(
    val id: String,
    val name: String,
    val description: String,
    val bodyPart: String? = null,
)

@Serializable
private data class JointDto(
    val id: String,
    val name: String,
    val type: String,
    val description: String,
    val bodyPart: String? = null,
)

@Serializable
private data class MovementPatternDto(
    val id: String,
    val name: String,
    val description: String,
)
