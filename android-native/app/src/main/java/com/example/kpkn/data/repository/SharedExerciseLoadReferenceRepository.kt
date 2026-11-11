package com.example.kpkn.data.repository

import android.content.Context
import com.example.kpkn.data.db.KpknDatabase
import com.example.kpkn.data.db.SharedExerciseLoadReferenceEntity
import com.example.kpkn.data.models.ComparableCapacityObservation
import com.example.kpkn.data.models.SharedExerciseLoadReference
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import kotlinx.serialization.decodeFromString
import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.Json

/** Cross-program load references. Every lookup uses the complete stable identity. */
class SharedExerciseLoadReferenceRepository private constructor(context: Context) {
    private val db = KpknDatabase.getInstance(context.applicationContext)
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.IO)
    private val json = Json { ignoreUnknownKeys = true; encodeDefaults = true }
    private val _references = MutableStateFlow<List<SharedExerciseLoadReference>>(emptyList())
    val references: StateFlow<List<SharedExerciseLoadReference>> = _references.asStateFlow()

    init { scope.launch { refresh() } }

    suspend fun refresh() {
        val persisted = db.sharedExerciseLoadReferenceDao().getAll().mapNotNull(::decode)
        synchronized(referenceWriteLock) {
            val localWrites = _references.value.filter { writeVersions.containsKey(it.id) }
            _references.value = (persisted + localWrites).distinctBy { it.id }
        }
    }

    fun getCompatible(request: SharedExerciseLoadReference): SharedExerciseLoadReference? =
        _references.value.firstOrNull { it.sameIdentity(request) }

    fun save(reference: SharedExerciseLoadReference) {
        if (reference.exerciseKey.isBlank() || reference.configurationId.isBlank() ||
            !reference.baseValueKg.isFinite() || reference.baseValueKg <= 0.0
        ) return
        synchronized(referenceWriteLock) {
            val previous = _references.value.firstOrNull { it.sameIdentity(reference) }
            val unchangedSource = previous != null && previous.sourceId == reference.sourceId &&
                previous.baseValueKg == reference.baseValueKg && previous.prWeightKg == reference.prWeightKg &&
                previous.prReps == reference.prReps && previous.effortMode == reference.effortMode &&
                previous.effortValue == reference.effortValue
            val next = if (unchangedSource) reference.copy(
                sourceAtMs = previous!!.sourceAtMs,
                comparableObservations = previous.comparableObservations,
            ) else reference.copy(comparableObservations = emptyList())
            _references.update { current -> current.filterNot { it.sameIdentity(next) } + next }
            persistInOrder(next)
        }
    }

    /** Appends one validated conventional session; duplicate session IDs are idempotent. */
    fun recordComparableObservation(
        exerciseKey: String,
        configurationId: String,
        tagId: String?,
        unitMode: String,
        loadMode: String,
        quantityConvention: String,
        sessionId: String,
        observedAtMs: Long,
        capacityKg: Double,
    ) {
        synchronized(referenceWriteLock) {
            val matching = _references.value.filter {
                it.exerciseKey == exerciseKey && it.configurationId == configurationId && it.tagId == tagId &&
                    it.unitMode.name == unitMode && it.loadMode.name == loadMode && it.quantityConvention.name == quantityConvention &&
                    it.kind in setOf(
                        com.example.kpkn.data.models.SharedLoadReferenceKind.ESTIMATED_PR,
                        com.example.kpkn.data.models.SharedLoadReferenceKind.OBSERVED_SESSION,
                    )
            }
            val enums = runCatching {
                Triple(
                    com.example.kpkn.data.models.UnitModeV2.valueOf(unitMode),
                    com.example.kpkn.data.models.LoadModeV2.valueOf(loadMode),
                    com.example.kpkn.data.models.LoadQuantityConvention.valueOf(quantityConvention),
                )
            }.getOrNull() ?: return
            val observation = ComparableCapacityObservation(sessionId, observedAtMs, capacityKg)
            val updates = matching.mapNotNull { current ->
                current.withComparableObservation(observation).takeIf { it != current }
            }.toMutableList()
            if (matching.none { it.kind == com.example.kpkn.data.models.SharedLoadReferenceKind.OBSERVED_SESSION } &&
                sessionId.isNotBlank() && capacityKg.isFinite() && capacityKg > 0.0
            ) {
                updates += SharedExerciseLoadReference(
                    id = com.example.kpkn.data.models.sharedLoadReferenceId(
                        exerciseKey, configurationId, tagId, enums.first, enums.second, enums.third,
                        com.example.kpkn.data.models.SharedLoadReferenceKind.OBSERVED_SESSION,
                    ),
                    exerciseKey = exerciseKey,
                    configurationId = configurationId,
                    tagId = tagId,
                    unitMode = enums.first,
                    loadMode = enums.second,
                    quantityConvention = enums.third,
                    kind = com.example.kpkn.data.models.SharedLoadReferenceKind.OBSERVED_SESSION,
                    baseValueKg = capacityKg,
                    sourceAtMs = observedAtMs,
                    sourceId = "workout-history",
                    comparableObservations = listOf(observation),
                )
            }
            if (updates.isEmpty()) return
            _references.update { current -> updates.fold(current) { rows, updated ->
                rows.filterNot { it.id == updated.id } + updated
            } }
            updates.forEach(::persistInOrder)
        }
    }

    private val referenceWriteLock = Any()
    private val writeVersions = java.util.concurrent.ConcurrentHashMap<String, Long>()
    private val daoWriteMutex = kotlinx.coroutines.sync.Mutex()

    private fun persistInOrder(reference: SharedExerciseLoadReference) {
        val version = synchronized(referenceWriteLock) {
            val next = (writeVersions[reference.id] ?: 0L) + 1L
            writeVersions[reference.id] = next
            next
        }
        scope.launch {
            daoWriteMutex.lock()
            try {
                if (writeVersions[reference.id] == version) {
                    db.sharedExerciseLoadReferenceDao().upsert(reference.toEntity())
                }
            } finally {
                daoWriteMutex.unlock()
            }
        }
    }

    private fun SharedExerciseLoadReference.toEntity() = SharedExerciseLoadReferenceEntity(
        id = id,
        exerciseKey = exerciseKey,
        configurationId = configurationId,
        tagId = tagId,
        unitMode = unitMode.name,
        loadMode = loadMode.name,
        quantityConvention = quantityConvention.name,
        kind = kind.name,
        valueKg = baseValueKg,
        sourceAtMs = sourceAtMs,
        sourceId = sourceId,
        repCount = prReps,
        effortMode = effortMode?.name,
        effortValue = effortValue,
        payloadJson = json.encodeToString(this),
    )

    private fun decode(row: SharedExerciseLoadReferenceEntity): SharedExerciseLoadReference? = runCatching {
        json.decodeFromString<SharedExerciseLoadReference>(row.payloadJson)
    }.getOrNull()

    companion object {
        @Volatile private var instance: SharedExerciseLoadReferenceRepository? = null
        fun getInstance(context: Context): SharedExerciseLoadReferenceRepository = instance
            ?: synchronized(this) {
                instance ?: SharedExerciseLoadReferenceRepository(context).also { instance = it }
            }
    }
}
