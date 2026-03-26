package com.example.kpkn.data.repository

import android.content.Context
import com.example.kpkn.data.db.*
import com.example.kpkn.data.prepopulateWikiLabAssets
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.flow.*
import kotlinx.coroutines.launch
import kotlinx.serialization.json.Json

/**
 * WikiLab Repository — StateFlow + Room cache pattern.
 *
 * All static WikiLab data (muscles, joints, tendons, patterns, chains)
 * is loaded from Room on initialization and exposed as StateFlows.
 *
 * On first app launch, data is prepopulated from JSON assets.
 */
object WikiLabRepository {

    private lateinit var dao: WikiLabDao
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.IO)

    // ─── StateFlow Caches ───────────────────────────────────────────────

    private val _muscles = MutableStateFlow<List<MuscleGroupEntity>>(emptyList())
    val muscles: StateFlow<List<MuscleGroupEntity>> = _muscles.asStateFlow()

    private val _joints = MutableStateFlow<List<JointEntity>>(emptyList())
    val joints: StateFlow<List<JointEntity>> = _joints.asStateFlow()

    private val _tendons = MutableStateFlow<List<TendonEntity>>(emptyList())
    val tendons: StateFlow<List<TendonEntity>> = _tendons.asStateFlow()

    private val _patterns = MutableStateFlow<List<MovementPatternEntity>>(emptyList())
    val patterns: StateFlow<List<MovementPatternEntity>> = _patterns.asStateFlow()

    private val _chains = MutableStateFlow<List<KineticChainEntity>>(emptyList())
    val chains: StateFlow<List<KineticChainEntity>> = _chains.asStateFlow()

    // ─── JSON Parser ────────────────────────────────────────────────────

    val json = Json { ignoreUnknownKeys = true; encodeDefaults = true }

    // ─── Initialization ─────────────────────────────────────────────────

    fun initialize(context: Context, db: KpknDatabase) {
        dao = db.wikiLabDao()

        scope.launch {
            // Prepopulate on first run
            prepopulateWikiLabAssets(context, db)

            // Collect from Room → StateFlows
            launch { dao.getAllMuscles().collect { _muscles.value = it } }
            launch { dao.getAllJoints().collect { _joints.value = it } }
            launch { dao.getAllTendons().collect { _tendons.value = it } }
            launch { dao.getAllPatterns().collect { _patterns.value = it } }
            launch { dao.getAllChains().collect { _chains.value = it } }
        }
    }

    // ─── Lookup Helpers ─────────────────────────────────────────────────

    fun getMuscleById(id: String): MuscleGroupEntity? =
        _muscles.value.find { it.id == id }

    fun getJointById(id: String): JointEntity? =
        _joints.value.find { it.id == id }

    fun getTendonById(id: String): TendonEntity? =
        _tendons.value.find { it.id == id }

    fun getPatternById(id: String): MovementPatternEntity? =
        _patterns.value.find { it.id == id }

    fun getChainById(id: String): KineticChainEntity? =
        _chains.value.find { it.id == id }

    // ─── JSON Parsers ───────────────────────────────────────────────────

    fun parseStringList(jsonStr: String?): List<String> =
        try { json.decodeFromString<List<String>>(jsonStr ?: "[]") } catch (_: Exception) { emptyList() }

    fun parseInjuries(jsonStr: String?): List<InjuryInfo> =
        try { json.decodeFromString<List<InjuryInfo>>(jsonStr ?: "[]") } catch (_: Exception) { emptyList() }

    // ─── Filtered Queries ───────────────────────────────────────────────

    fun getMusclesByBodyPart(bodyPart: String): List<MuscleGroupEntity> =
        _muscles.value.filter { it.bodyPart == bodyPart }

    fun getJointsByBodyPart(bodyPart: String): List<JointEntity> =
        _joints.value.filter { it.bodyPart == bodyPart }

    fun getTendonsByMuscle(muscleId: String): List<TendonEntity> =
        _tendons.value.filter { it.muscleId == muscleId }

    fun getTendonsByJoint(jointId: String): List<TendonEntity> =
        _tendons.value.filter { it.jointId == jointId }

    // ─── Body Part Categories ──────────────────────────────────────────

    val bodyPartCategories: List<String> =
        listOf("upper", "lower", "core", "spine")

    fun getBodyPartLabel(bodyPart: String?): String = when (bodyPart) {
        "upper" -> "Tren Superior"
        "lower" -> "Tren Inferior"
        "core" -> "Core"
        "spine" -> "Columna"
        else -> "Otro"
    }

    fun getJointTypeLabel(type: String): String = when (type) {
        "hinge" -> "Bisagra"
        "ball-socket" -> "Esferoidea"
        "pivot" -> "Pivote"
        "gliding" -> "Deslizante"
        "saddle" -> "Silla de montar"
        "condyloid" -> "Condílea"
        else -> type
    }
}

// ─── Data Classes for Parsed JSON ──────────────────────────────────────────

@kotlinx.serialization.Serializable
data class InjuryInfo(
    val name: String,
    val description: String? = null,
    val riskExercises: List<String>? = null,
    val contraindications: List<String>? = null,
    val returnProgressions: List<String>? = null,
)
