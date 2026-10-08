package com.example.kpkn.domain.sync

import kotlinx.coroutines.flow.StateFlow
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.JsonObject

@Serializable
data class PrivateDataReference(val sha256: String, val bytes: Long)

@Serializable
data class PersonalOperation(
    val schemaVersion: Int = 1,
    val operationId: String,
    val entityId: String,
    val deviceId: String,
    val accountGeneration: Long,
    val baseRevision: Long,
    val kind: String,
    val changedAtEpochMs: Long,
    val zoneId: String,
    val unitSystem: String,
    val deleted: Boolean,
    val payload: JsonObject?,
    val sourceId: String? = null,
    val guestImportId: String? = null,
    val payloadRef: PrivateDataReference? = null,
)

sealed interface SyncStatus {
    data object Disabled : SyncStatus
    data class Ready(val pending: Int, val lastConfirmedAtEpochMs: Long, val waitingForPro: Int = 0) : SyncStatus
    data class Working(val pending: Int) : SyncStatus
    data class Conflict(val count: Int) : SyncStatus
    data class Error(val code: String, val pending: Int) : SyncStatus
}
interface SyncRepository {
    val status: StateFlow<SyncStatus>
    suspend fun enable()
    suspend fun disable()
    suspend fun synchronize()
    suspend fun resolveConflict(id: String, resolution: String)
}
