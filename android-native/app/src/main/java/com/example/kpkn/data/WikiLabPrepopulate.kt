package com.example.kpkn.data

import android.content.Context
import com.example.kpkn.data.db.*
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import kotlinx.serialization.json.Json
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.JsonElement
import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.jsonArray
import kotlinx.serialization.json.jsonPrimitive

private val prepopulateJson = Json { ignoreUnknownKeys = true; encodeDefaults = true }

/** Static Aprende content revision; bump when bundled anatomy or links change. */
const val APRENDE_CONTENT_REVISION = "aprende-v2-2026-08-22"
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
    val counts = listOf(
        dao.getMuscleCount(),
        dao.getJointCount(),
        dao.getTendonCount(),
        dao.getPatternCount(),
        dao.getChainCount(),
    )
    val hasData = counts.any { it > 0 }
    val populated = counts.all { it > 0 }
    if (populated && currentRevision == APRENDE_CONTENT_REVISION) return@withContext
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
                coverImage = dto.coverImage,
                origin = dto.origin,
                insertion = dto.insertion,
                mechanicalFunctions = dto.mechanicalFunctions?.let { prepopulateJson.encodeToString(it) },
                mev = dto.mev,
                mav = dto.mav,
                mrv = dto.mrv,
                recommendedExercises = dto.recommendedExercises?.let { prepopulateJson.encodeToString(it) },
                relatedJoints = dto.relatedJoints?.let { prepopulateJson.encodeToString(it) },
                relatedTendons = dto.relatedTendons?.let { prepopulateJson.encodeToString(it) },
                importanceMovement = dto.importanceMovement,
                importanceHealth = dto.importanceHealth,
                aestheticImportance = dto.aestheticImportance,
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
                musclesCrossing = dto.musclesCrossing?.let { prepopulateJson.encodeToString(it) },
                tendonsRelated = dto.tendonsRelated?.let { prepopulateJson.encodeToString(it) },
                movementPatterns = dto.movementPatterns?.let { prepopulateJson.encodeToString(it) },
                commonInjuries = dto.commonInjuries?.let { prepopulateJson.encodeToString(it) },
                protectiveExercises = dto.protectiveExercises?.let { prepopulateJson.encodeToString(it) },
            )
        }
        dao.insertJoints(jointEntities)
    } catch (e: Exception) {
        failures++
        logWikiLabError("joints", e)
    }

    // ─── Tendons ──────────────────────────────────────────────────────
    try {
        val tendonJson = assets.open("wikilab/tendons.json").bufferedReader().use { it.readText() }
        val tendonData: List<TendonDto> = prepopulateJson.decodeFromString(tendonJson)
        val tendonEntities = tendonData.map { dto ->
            TendonEntity(
                id = dto.id,
                name = dto.name,
                description = dto.description,
                muscleId = dto.muscleId,
                jointId = dto.jointId,
                commonInjuries = dto.commonInjuries?.let { prepopulateJson.encodeToString(it) },
                protectiveExercises = dto.protectiveExercises?.let { prepopulateJson.encodeToString(it) },
            )
        }
        dao.insertTendons(tendonEntities)
    } catch (e: Exception) {
        failures++
        logWikiLabError("tendons", e)
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
                forceTypes = dto.forceTypes?.let { prepopulateJson.encodeToString(it) },
                chainTypes = dto.chainTypes?.let { prepopulateJson.encodeToString(it) },
                primaryMuscles = dto.primaryMuscles?.let { prepopulateJson.encodeToString(it) },
                primaryJoints = dto.primaryJoints?.let { prepopulateJson.encodeToString(it) },
                exampleExercises = dto.exampleExercises?.let { prepopulateJson.encodeToString(it) },
            )
        }
        dao.insertPatterns(patternEntities)
    } catch (e: Exception) {
        failures++
        logWikiLabError("patterns", e)
    }

    // ─── Kinetic Chains ────────────────────────────────────────────────
    try {
        val chainJson = assets.open("wikilab/kinetic_chains.json").bufferedReader().use { it.readText() }
        val chainData: List<KineticChainDto> = prepopulateJson.decodeFromString(chainJson)
        val chainEntities = chainData.map { dto ->
            KineticChainEntity(
                id = dto.id,
                name = dto.name,
                description = dto.description,
                importance = dto.importance,
                muscles = dto.muscles?.let { prepopulateJson.encodeToString(it) },
            )
        }
        dao.insertChains(chainEntities)
    } catch (e: Exception) {
        failures++
        logWikiLabError("chains", e)
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
    val coverImage: String? = null,
    val origin: String? = null,
    val insertion: String? = null,
    val mechanicalFunctions: List<String>? = null,
    val mev: String? = null,
    val mav: String? = null,
    val mrv: String? = null,
    val recommendedExercises: List<String>? = null,
    val relatedJoints: List<String>? = null,
    val relatedTendons: List<String>? = null,
    val importanceMovement: String? = null,
    val importanceHealth: String? = null,
    val aestheticImportance: String? = null,
)

@Serializable
private data class JointDto(
    val id: String,
    val name: String,
    val type: String,
    val description: String,
    val bodyPart: String? = null,
    val musclesCrossing: List<String>? = null,
    val tendonsRelated: List<String>? = null,
    val movementPatterns: List<String>? = null,
    val commonInjuries: List<InjuryDto>? = null,
    val protectiveExercises: List<String>? = null,
)

@Serializable
private data class TendonDto(
    val id: String,
    val name: String,
    val description: String? = null,
    val muscleId: String? = null,
    val jointId: String? = null,
    val commonInjuries: List<InjuryDto>? = null,
    val protectiveExercises: List<String>? = null,
)

@Serializable
private data class MovementPatternDto(
    val id: String,
    val name: String,
    val description: String,
    val forceTypes: List<String>? = null,
    val chainTypes: List<String>? = null,
    val primaryMuscles: List<String>? = null,
    val primaryJoints: List<String>? = null,
    val exampleExercises: List<String>? = null,
)

@Serializable
private data class KineticChainDto(
    val id: String,
    val name: String,
    val description: String,
    val importance: String,
    val muscles: List<String>? = null,
)

@Serializable
private data class InjuryDto(
    val name: String,
    val description: String? = null,
    val riskExercises: List<String>? = null,
    val contraindications: List<String>? = null,
    val returnProgressions: List<String>? = null,
)
