package com.example.kpkn.data.sync

import android.content.Context
import androidx.room.withTransaction
import com.example.kpkn.data.account.AccountServiceException
import com.example.kpkn.data.account.AccountCalls
import com.example.kpkn.data.db.*
import com.example.kpkn.domain.sync.*
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withContext
import kotlinx.serialization.json.*
import java.time.ZoneId
import java.util.UUID

/** Room remains the UI's store. Network completion changes only persisted receipts and owned rows. */
class RoomSyncRepository(private val context: Context, private val uid: String,
    private val database: KpknDatabase, private val transport: AccountCalls,
    private val privateData: PrivatePayloadTransfer, private val media: SyncMediaTransfer) : SyncRepository {
    private val mutex = syncLocks.getOrPut(uid) { Mutex() }
    private val journal = database.syncJournalDao()
    private val codec = PersonalRecordCodec(context)
    private val json = Json { encodeDefaults = true; ignoreUnknownKeys = true }
    private val _status = MutableStateFlow<SyncStatus>(SyncStatus.Disabled)
    override val status = _status.asStateFlow()

    override suspend fun enable() = withContext(Dispatchers.IO) {
        val server = transport.call(uid, "openAccount")
        val configuration = journal.configuration() ?: SyncConfigurationEntity()
        val device = configuration.deviceId.ifBlank { UUID.randomUUID().toString() }
        database.withTransaction {
            journal.configure(configuration.copy(enabled = true, accountGeneration = server.getValue("generation").jsonPrimitive.long, deviceId = device))
            val sql = database.openHelper.writableDatabase
            SyncJournalSchema.keys.forEach { (table, key) ->
                sql.execSQL("""INSERT OR IGNORE INTO sync_mutations(entityTable,rawKey,dirtyVersion,deleted,baseRevision,operationId,operationJson,sentVersion,status)
                    SELECT '$table',CAST(`$key` AS TEXT),1,0,0,NULL,NULL,0,'WAITING' FROM `$table`
                    WHERE NOT EXISTS(SELECT 1 FROM sync_shadows WHERE entityTable='$table' AND rawKey=CAST(`$key` AS TEXT))""".trimIndent())
            }
        }
        publish(SyncStatus.Ready(journal.pendingCount(), configuration.confirmedAtEpochMs))
    }
    override suspend fun disable() = withContext(Dispatchers.IO) {
        mutex.withLock {
            journal.configuration()?.let { journal.configure(it.copy(enabled = false)) }
            publish(SyncStatus.Disabled)
        }
    }
    override suspend fun synchronize() = withContext(Dispatchers.IO) {
        mutex.withLock {
            val configuration = journal.configuration() ?: return@withLock
            if (!configuration.enabled) { publish(SyncStatus.Disabled); return@withLock }
            try {
                publish(SyncStatus.Working(journal.pendingCount()))
                // Send existing dirty versions BEFORE pulling, so a remote change cannot silently rebase a local edit.
                val pro = com.example.kpkn.data.account.AccountGraph.entitlement.value.effectiveLevel == com.example.kpkn.domain.entitlements.PlanLevel.PRO
                if (pro) journal.releaseCapabilityWait()
                val prepared = mutableListOf<Pair<SyncMutationEntity, PersonalOperation>>()
                for (pending in journal.pending()) {
                    var marker = pending
                    val cached = marker.operationJson?.let { json.decodeFromString(PersonalOperation.serializer(), it) }
                    if (!pro && cached != null && SyncCapabilities.requiresPro(cached.kind,
                            cached.payloadRef?.let(privateData::readStaged) ?: cached.payload, cached.deleted)) {
                        val result = transport.call(uid, "retirePendingOperation", buildJsonObject {
                            put("operation", json.encodeToJsonElement(PersonalOperation.serializer(), cached))
                        })
                        confirmReceipt(marker, cached, result)
                        marker = journal.mutation(marker.entityTable, marker.rawKey) ?: continue
                        if (marker.status == "CONFLICT") continue
                    }
                    // A paid edit must not hold unrelated Free data behind the same batch.
                    val payload = if (marker.deleted) null else codec.snapshot(database.openHelper.readableDatabase, marker.entityTable, marker.rawKey)
                    val kind = PersonalRecordCodec.kinds.getValue(marker.entityTable)
                    if (!pro && marker.operationJson == null && SyncCapabilities.requiresPro(kind, payload, marker.deleted || payload == null)) {
                        deferPaid(marker)
                        continue
                    }
                    val sending = prepare(marker, configuration)
                    if (!pro && SyncCapabilities.requiresPro(sending.second.kind,
                            sending.second.payloadRef?.let(privateData::readStaged) ?: sending.second.payload, sending.second.deleted)) {
                        deferPaid(sending.first)
                    } else prepared += sending
                }
                if (prepared.isNotEmpty()) {
                    prepared.forEach { (_, operation) ->
                        operation.payloadRef?.let { privateData.upload(configuration.accountGeneration, it) }
                        if (operation.kind == "workout_media") media.upload(configuration.accountGeneration,
                            operation.payloadRef?.let(privateData::readStaged) ?: operation.payload)
                    }
                    val response = transport.call(uid, "pushChanges", buildJsonObject {
                        put("operations", JsonArray(prepared.map { json.encodeToJsonElement(PersonalOperation.serializer(), it.second) }))
                    })
                    val receipts = response.getValue("receipts").jsonArray.map { it.jsonObject }.associateBy { it.getValue("operationId").jsonPrimitive.content }
                    prepared.forEach { (marker, operation) ->
                        val receipt = receipts[operation.operationId] ?: throw AccountServiceException("INCOMPLETE_RECEIPTS")
                        confirmReceipt(marker, operation, receipt)
                    }
                }
                pull(configuration.accountGeneration)
                val current = journal.configuration()!!
                val conflicts = conflicts().size
                publish(if (conflicts > 0) SyncStatus.Conflict(conflicts) else SyncStatus.Ready(journal.pendingCount(), current.confirmedAtEpochMs, journal.waitingForProCount()))
            } catch (error: Exception) {
                publish(SyncStatus.Error((error as? AccountServiceException)?.code ?: "UNAVAILABLE", journal.pendingCount()))
                throw error
            }
        }
    }
    private suspend fun deferPaid(marker: SyncMutationEntity) = database.withTransaction {
        val live = journal.mutation(marker.entityTable, marker.rawKey) ?: return@withTransaction
        if (!live.deleted && live.dirtyVersion == marker.dirtyVersion && live.operationId == marker.operationId)
            journal.upsert(live.copy(status = "CAPABILITY_WAIT"))
    }
    private suspend fun confirmReceipt(marker: SyncMutationEntity, operation: PersonalOperation, receipt: JsonObject) = database.withTransaction {
        val live = journal.mutation(marker.entityTable, marker.rawKey) ?: return@withTransaction
        if (live.operationId != marker.operationId) return@withTransaction
        when (receipt.getValue("status").jsonPrimitive.content) {
            "CONFLICT" -> journal.upsert(live.copy(status = "CONFLICT"))
            "CANCELED" -> journal.upsert(live.copy(operationId = null, operationJson = null, sentVersion = 0, status = "WAITING"))
            "APPLIED" -> {
                val revision = receipt.getValue("revision").jsonPrimitive.long
                journal.confirm(SyncShadowEntity(marker.entityTable, marker.rawKey, revision, shadowJson(operation.payload, operation.payloadRef), operation.deleted))
                if (live.dirtyVersion == live.sentVersion) journal.remove(marker.entityTable, marker.rawKey)
                else journal.upsert(live.copy(operationId = null, operationJson = null, sentVersion = 0, baseRevision = revision, status = "WAITING"))
            }
            else -> throw AccountServiceException("INVALID_RECEIPT")
        }
    }
    private suspend fun prepare(marker: SyncMutationEntity, configuration: SyncConfigurationEntity): Pair<SyncMutationEntity, PersonalOperation> = database.withTransaction {
        val latest = journal.mutation(marker.entityTable, marker.rawKey) ?: error("Pending record disappeared")
        if (latest.operationJson != null) return@withTransaction latest to json.decodeFromString(PersonalOperation.serializer(), latest.operationJson)
        val payload = if (latest.deleted) null else codec.snapshot(database.openHelper.readableDatabase, latest.entityTable, latest.rawKey)
        var operation = PersonalOperation(operationId = UUID.randomUUID().toString(), entityId = PersonalRecordCodec.wireId(latest.rawKey),
            sourceId = latest.rawKey, deviceId = configuration.deviceId, accountGeneration = configuration.accountGeneration,
            baseRevision = latest.baseRevision, kind = PersonalRecordCodec.kinds.getValue(latest.entityTable),
            changedAtEpochMs = System.currentTimeMillis(), zoneId = ZoneId.systemDefault().id,
            unitSystem = if (codec.snapshot(database.openHelper.readableDatabase, "settings", "1")?.get("weightUnit")?.jsonPrimitive?.content == "LBS") "IMPERIAL" else "METRIC",
            deleted = latest.deleted || payload == null, payload = payload)
        var encoded = json.encodeToString(PersonalOperation.serializer(), operation)
        if (encoded.toByteArray().size > 512 * 1024) {
            operation = operation.copy(payload = null, payloadRef = privateData.stage(requireNotNull(payload)))
            encoded = json.encodeToString(PersonalOperation.serializer(), operation)
        }
        val sending = latest.copy(operationId = operation.operationId, operationJson = encoded, sentVersion = latest.dirtyVersion, status = "SENDING")
        journal.upsert(sending)
        sending to operation
    }
    private suspend fun pull(generation: Long) {
        var more: Boolean
        do {
            val configuration = journal.configuration()!!
            val response = transport.call(uid, "pullChanges", buildJsonObject { put("generation", generation); put("after", configuration.pullCursor); put("limit", 50) })
            val records = response.getValue("records").jsonArray.map { it.jsonObject }
            val hydrated = records.map { record ->
                val reference = record["payloadRef"]?.takeIf { it != JsonNull }?.let { json.decodeFromJsonElement(PrivateDataReference.serializer(), it) }
                Triple(record, reference, reference?.let { privateData.download(generation, it) }
                    ?: record["payload"]?.takeIf { it != JsonNull }?.jsonObject)
            }
            hydrated.forEach { (record, _, payload) -> if (record["kind"]?.jsonPrimitive?.content == "workout_media") media.download(generation, payload) }
            database.withTransaction {
                journal.configure(configuration.copy(applyingRemote = true))
                try {
                    hydrated.forEach { (record, reference, payload) ->
                        val kind = record.getValue("kind").jsonPrimitive.content
                        val table = PersonalRecordCodec.kinds.entries.firstOrNull { it.value == kind }?.key ?: error("Unsupported sync schema")
                        val key = record["sourceId"]?.jsonPrimitive?.content ?: record.getValue("entityId").jsonPrimitive.content
                        val revision = record.getValue("revision").jsonPrimitive.long
                        val deleted = record.getValue("deleted").jsonPrimitive.boolean
                        val previous = journal.shadow(table, key)
                        if (previous == null || revision > previous.serverRevision) {
                            // Both versions are preserved when a local edit exists; the server will issue its conflict receipt.
                            val ownedWorkout = kind != "ongoing_workout" || deleted || record.getValue("deviceId").jsonPrimitive.content == configuration.deviceId
                            if (ownedWorkout && journal.mutation(table, key) == null) codec.apply(database.openHelper.writableDatabase, kind, key, payload, deleted)
                            journal.confirm(SyncShadowEntity(table, key, revision, shadowJson(payload, reference), deleted))
                        }
                    }
                    journal.configure(configuration.copy(applyingRemote = false, pullCursor = maxOf(journal.configuration()!!.pullCursor, response.getValue("cursor").jsonPrimitive.long),
                        confirmedAtEpochMs = System.currentTimeMillis()))
                } finally {
                    journal.configuration()?.let { journal.configure(it.copy(applyingRemote = false)) }
                }
            }
            more = response.getValue("hasMore").jsonPrimitive.boolean
        } while (more)
    }
    override suspend fun resolveConflict(id: String, resolution: String) {
        require(resolution in setOf("KEEP_CURRENT", "USE_INCOMING", "KEEP_BOTH"))
        withContext(Dispatchers.IO) { mutex.withLock {
            val configuration = journal.configuration() ?: error("Sync is unavailable")
            val marker = journal.conflicted(id) ?: run {
                val remote = conflicts().firstOrNull { it.getValue("id").jsonPrimitive.content == id } ?: error("Conflict is unavailable")
                val incoming = json.decodeFromJsonElement(PersonalOperation.serializer(), remote.getValue("incoming"))
                val table = PersonalRecordCodec.kinds.entries.first { it.value == incoming.kind }.key
                val key = incoming.sourceId ?: incoming.entityId
                val cached = SyncMutationEntity(table, key, dirtyVersion = 0, deleted = incoming.deleted,
                    operationId = id, operationJson = json.encodeToString(PersonalOperation.serializer(), incoming), status = "CONFLICT")
                if (journal.mutation(table, key) == null) journal.upsert(cached)
                cached
            }
            val original = json.decodeFromString(PersonalOperation.serializer(), requireNotNull(marker.operationJson))
            val shadow = journal.shadow(marker.entityTable, marker.rawKey) ?: error("Current remote version is unavailable")
            val candidate = if (resolution == "KEEP_BOTH") {
                val preserved = original.payloadRef?.let { privateData.download(configuration.accountGeneration, it) }
                    ?: requireNotNull(original.payload)
                ConflictCopies.candidate(uid, id, original, preserved).let { copy ->
                    if (json.encodeToString(PersonalOperation.serializer(), copy).toByteArray().size > 400 * 1024)
                        copy.copy(payload = null, payloadRef = privateData.stage(requireNotNull(copy.payload))) else copy
                }
            } else original.copy(operationId = UUID.randomUUID().toString(), baseRevision = shadow.serverRevision,
                guestImportId = null, changedAtEpochMs = System.currentTimeMillis())
            if (resolution != "KEEP_CURRENT") candidate.payloadRef?.let { privateData.upload(configuration.accountGeneration, it) }
            val response = transport.call(uid, "resolveConflict", buildJsonObject {
                put("generation", configuration.accountGeneration); put("conflictId", id); put("resolution", resolution)
                put("expectedRevision", shadow.serverRevision)
                if (resolution != "KEEP_CURRENT") put("operation", json.encodeToJsonElement(PersonalOperation.serializer(), candidate))
            })
            if (response["status"]?.jsonPrimitive?.content == "CONFLICT") throw AccountServiceException("CONFLICT_CHANGED")
            val payload = if (resolution == "USE_INCOMING") candidate.payloadRef?.let { privateData.download(configuration.accountGeneration, it) } ?: candidate.payload
                else shadowPayload(shadow.payloadJson, configuration.accountGeneration)
            val duplicate = if (resolution == "KEEP_BOTH") candidate.payloadRef?.let { privateData.readStaged(it) } ?: candidate.payload else null
            database.withTransaction {
                val latest = journal.mutation(marker.entityTable, marker.rawKey)
                val deleted = if (resolution == "USE_INCOMING") candidate.deleted else shadow.deleted
                val revision = if (resolution == "KEEP_BOTH") shadow.serverRevision else response["revision"]?.jsonPrimitive?.long ?: shadow.serverRevision
                journal.configure(configuration.copy(applyingRemote = true))
                try {
                    if (resolution == "KEEP_BOTH") {
                        val copyKey = requireNotNull(candidate.sourceId)
                        // A later edit to an already-recovered copy remains pending instead of being overwritten on retry.
                        if (journal.mutation(marker.entityTable, copyKey) == null)
                            codec.apply(database.openHelper.writableDatabase, candidate.kind, copyKey, duplicate, false)
                        journal.confirm(SyncShadowEntity(marker.entityTable, copyKey, response.getValue("revision").jsonPrimitive.long,
                            shadowJson(duplicate, candidate.payloadRef), false))
                    }
                    if (latest == null || latest.operationId != marker.operationId) { /* A newer local edit keeps its original base revision for an explicit subsequent conflict. */ }
                    else if (latest.dirtyVersion == marker.dirtyVersion) {
                        codec.apply(database.openHelper.writableDatabase, candidate.kind, marker.rawKey, payload, deleted)
                        journal.remove(marker.entityTable, marker.rawKey)
                    } else journal.upsert(latest.copy(operationId = null, operationJson = null, sentVersion = 0, baseRevision = revision, status = "WAITING"))
                    journal.confirm(SyncShadowEntity(marker.entityTable, marker.rawKey, revision,
                        if (resolution == "USE_INCOMING") shadowJson(payload, candidate.payloadRef) else shadow.payloadJson, deleted))
                } finally { journal.configure(configuration.copy(applyingRemote = false)) }
            }
        } }
        synchronize()
    }
    private suspend fun publish(value: SyncStatus) = withContext(Dispatchers.Main) { _status.value = value }
    private fun shadowJson(payload: JsonObject?, reference: PrivateDataReference?): String? = if (reference == null) payload?.toString()
        else buildJsonObject { put("kpknPrivateDataReference", json.encodeToJsonElement(PrivateDataReference.serializer(), reference)) }.toString()
    private suspend fun shadowPayload(value: String?, generation: Long): JsonObject? {
        val payload = value?.let { Json.parseToJsonElement(it).jsonObject } ?: return null
        return payload["kpknPrivateDataReference"]?.let { privateData.download(generation, json.decodeFromJsonElement(PrivateDataReference.serializer(), it)) } ?: payload
    }
    suspend fun conflicts(): List<JsonObject> = withContext(Dispatchers.IO) {
        val configuration = journal.configuration() ?: return@withContext emptyList()
        transport.call(uid, "listConflicts", buildJsonObject { put("generation", configuration.accountGeneration) })
            .getValue("conflicts").jsonArray.map { it.jsonObject }
    }
    suspend fun conflictVersions(conflict: JsonObject): Pair<JsonObject?, JsonObject?> = withContext(Dispatchers.IO) {
        val incoming = json.decodeFromJsonElement(PersonalOperation.serializer(), conflict.getValue("incoming"))
        val table = PersonalRecordCodec.kinds.entries.first { it.value == incoming.kind }.key
        val key = incoming.sourceId ?: incoming.entityId
        val generation = requireNotNull(journal.configuration()).accountGeneration
        val current = journal.shadow(table, key)
        shadowPayload(current?.payloadJson, generation) to (incoming.payloadRef?.let { privateData.download(generation, it) } ?: incoming.payload)
    }
    fun canKeepBoth(conflict: JsonObject, incomingPayload: JsonObject?): Boolean =
        ConflictCopies.supported(json.decodeFromJsonElement(PersonalOperation.serializer(), conflict.getValue("incoming")), incomingPayload)
    suspend fun cancelRemoteWorkout(conflict: JsonObject) = withContext(Dispatchers.IO) { mutex.withLock {
        val incoming = json.decodeFromJsonElement(PersonalOperation.serializer(), conflict.getValue("incoming"))
        require(incoming.kind == "ongoing_workout")
        val configuration = requireNotNull(journal.configuration())
        val key = incoming.sourceId ?: incoming.entityId
        val current = requireNotNull(journal.shadow("ongoing_workout", key))
        transport.call(uid, "cancelRemoteWorkout", buildJsonObject {
            put("generation", configuration.accountGeneration); put("entityId", incoming.entityId)
            put("expectedRevision", current.serverRevision); put("deviceId", configuration.deviceId)
            put("operationId", UUID.randomUUID().toString()); put("confirmation", "CANCEL_REMOTE_WORKOUT")
        })
        pull(configuration.accountGeneration)
    } }
    companion object { private val syncLocks = java.util.concurrent.ConcurrentHashMap<String, Mutex>() }
}
