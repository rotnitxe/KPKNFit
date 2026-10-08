package com.example.kpkn.data.sync

import android.content.Context
import android.util.AtomicFile
import com.example.kpkn.data.account.*
import com.example.kpkn.data.backup.BoundedInput
import com.example.kpkn.data.db.*
import com.example.kpkn.domain.sync.PersonalOperation
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.*
import java.io.File
import java.time.ZoneId
import java.util.UUID

@Serializable data class GuestImportPlan(val id: String, val guestProfileId: String, val source: LocalProfile,
    val deviceId: String, val files: List<String>, val manifestHash: String, val ownerUid: String? = null, val completed: Boolean = false,
    val snapshotVersion: Int = 1, val frozenFiles: List<com.example.kpkn.data.backup.PortableFile> = emptyList(),
    val preferences: JsonObject = JsonObject(emptyMap()), val operationHashes: Map<String, String> = emptyMap(),
    val localMediaPaths: Map<String, Map<String, String>> = emptyMap(), val avatar: String? = null,
    val stagedMedia: List<com.example.kpkn.data.backup.PortableFile> = emptyList())

/** The immutable guest snapshot is retained until verification; retries use the same operation identities. */
class GuestImportCoordinator(private val platform: Context, private val session: FirebaseSession?, private val transport: AccountTransport?) {
    internal constructor(platform: Context) : this(platform, null, null)
    private val json = Json { encodeDefaults = true; ignoreUnknownKeys = true }
    suspend fun prepare(source: Context, database: KpknDatabase): File = withContext(Dispatchers.IO) {
        require(ProfileStorage.current().uid == null)
        val root = File(source.noBackupFilesDir, "guest-import").also { it.mkdirs() }
        val saved = File(root, "plan.json")
        if (saved.isFile || File(saved.path + ".bak").isFile) {
            require(read(root).snapshotVersion == 2) { "Guest snapshot needs explicit recovery before importing" }
            return@withContext root
        }
        val id = UUID.randomUUID().toString(); val deviceId = UUID.randomUUID().toString()
        val unitSystem = database.settingsDao().get()?.let { com.example.kpkn.data.db.dbJson.decodeFromString(com.example.kpkn.data.models.Settings.serializer(), it.data) }
            ?.weightUnit?.let { if (it == com.example.kpkn.data.models.WeightUnit.LBS) "IMPERIAL" else "METRIC" } ?: "METRIC"
        val leaves = mutableListOf<JsonObject>(); val files = mutableListOf<String>()
        val codec = PersonalRecordCodec(source)
        val mediaPaths = mutableMapOf<String, Map<String, String>>()
        var avatar: String? = null
        val stagedMedia = linkedMapOf<String, com.example.kpkn.data.backup.PortableFile>()
        database.runInTransaction {
            val sql = database.openHelper.readableDatabase
            SyncJournalSchema.keys.forEach { (table, key) ->
                val keys = sql.query("SELECT `$key` FROM `$table` ORDER BY `$key`").use { cursor -> buildList { while (cursor.moveToNext()) add(cursor.getString(0)) } }
                keys.forEach { raw ->
                    require(files.size < 100_000)
                    val payload = requireNotNull(codec.snapshot(sql, table, raw))
                    val row = requireNotNull(BoundedSqlRows.read(sql, table, key, raw))
                    if (table == "workout_media") mediaPaths[raw] = listOf("filePath", "thumbPath", "poseTrackPath")
                        .mapNotNull { column -> (row[column] as? String)?.let { column to it } }.toMap()
                    if (table == "settings") avatar = (row["data"] as? String)?.let { Json.parseToJsonElement(it).jsonObject["profilePicture"]?.jsonPrimitive?.contentOrNull }
                    MediaFiles.references(payload).forEach { reference ->
                        val file = File(source.noBackupFilesDir, "sync-media/${reference.sha256}")
                        MediaFiles.verify(file, reference)
                        val entry = com.example.kpkn.data.backup.PortableFile(reference.sha256, reference.bytes, reference.sha256)
                        GuestSnapshotFiles.copyVerified(file, File(root, "staged-media/${reference.sha256}"), entry)
                        stagedMedia[reference.sha256] = entry
                    }
                    val operation = PersonalOperation(operationId = UUID.randomUUID().toString(), entityId = PersonalRecordCodec.wireId(raw), sourceId = raw,
                        deviceId = deviceId, accountGeneration = 1, baseRevision = 0, kind = PersonalRecordCodec.kinds.getValue(table),
                        changedAtEpochMs = System.currentTimeMillis(), zoneId = ZoneId.systemDefault().id, unitSystem = unitSystem, deleted = false,
                        payload = payload, guestImportId = id)
                    val filename = "${files.size.toString().padStart(8, '0')}.json"
                    val file = File(root, filename)
                    java.io.FileOutputStream(file).use { it.write(json.encodeToString(PersonalOperation.serializer(), operation).toByteArray()); it.fd.sync() }
                    files += filename
                    leaves += buildJsonObject { put("kind", operation.kind); put("entityId", operation.entityId); put("payloadHash", PersonalRecordCodec.sha256(CanonicalJson.encode(payload).toByteArray())) }
                }
            }
        }
        val manifest = JsonArray(leaves.sortedBy { it.getValue("kind").jsonPrimitive.content + ":" + it.getValue("entityId").jsonPrimitive.content })
        val frozenFiles = GuestSnapshotFiles.freeze(source, root)
        write(root, GuestImportPlan(id, UUID.randomUUID().toString(), ProfileStorage.current(), deviceId, files,
            PersonalRecordCodec.sha256(CanonicalJson.encode(manifest).toByteArray()), snapshotVersion = 2,
            frozenFiles = frozenFiles, preferences = com.example.kpkn.data.backup.PortableBackup.preferences(source),
            operationHashes = files.associateWith { MediaFiles.hash(File(root, it)) }, localMediaPaths = mediaPaths,
            avatar = avatar, stagedMedia = stagedMedia.values.toList()))
        root
    }
    suspend fun bindAndCopy(root: File, uid: String) = withContext(Dispatchers.IO) {
        val plan = read(root)
        require(plan.snapshotVersion == 2)
        require(plan.ownerUid == null || plan.ownerUid == uid) { "Guest snapshot belongs to another account" }
        write(root, plan.copy(ownerUid = uid))
        val target = ProfileStorage.context(platform, LocalProfile(uid = uid))
        val source = ProfileStorage.context(platform, plan.source)
        GuestSourceLedger.record(target, uid, plan.id, plan.source)
        val published = GuestSnapshotFiles.publish(root, target, plan.id, plan.frozenFiles)
        val paths = published.mapKeys { (relative, _) ->
            com.example.kpkn.data.backup.PortableBackup.portableFile(source, relative.substringBefore('/')).let { origin ->
                if ('/' in relative) File(origin, relative.substringAfter('/')).canonicalPath else origin.canonicalPath
            }
        }
        plan.stagedMedia.forEach { entry -> GuestSnapshotFiles.copyVerified(File(root, "staged-media/${entry.path}"),
            File(target.noBackupFilesDir, "sync-media/${entry.path}"), entry) }
        val operations = plan.files.map { file ->
            require(plan.operationHashes[file] == MediaFiles.hash(File(root, file))) { "Guest row snapshot is corrupt" }
            operation(root, file)
        }
        val database = KpknDatabase.openIsolated(target, "kpkn.db")
        try {
            database.runInTransaction {
                val sql = database.openHelper.writableDatabase
                sql.execSQL("UPDATE sync_configuration SET applyingRemote=1")
                try {
                    val codec = PersonalRecordCodec(target)
                    operations.forEach { operation ->
                        val table = PersonalRecordCodec.kinds.entries.first { it.value == operation.kind }.key
                        val key = requireNotNull(operation.sourceId)
                        if (BoundedSqlRows.read(sql, table, SyncJournalSchema.keys.getValue(table), key) == null) {
                            val payload = requireNotNull(operation.payload)
                            var localPayload = GuestSnapshotFiles.remap(payload, paths).jsonObject
                            if (table == "settings" && plan.avatar != null) localPayload = JsonObject(localPayload +
                                ("profilePicture" to GuestSnapshotFiles.remap(JsonPrimitive(plan.avatar), paths)))
                            codec.apply(sql, operation.kind, key, localPayload, false)
                            if (table == "workout_media") {
                                listOf("filePath", "thumbPath", "poseTrackPath").forEach { column ->
                                    val path = plan.localMediaPaths[key]?.get(column) ?: return@forEach
                                    val replacement = paths[File(path).canonicalPath]
                                    if (replacement != null) sql.execSQL("UPDATE workout_media SET `$column`=? WHERE id=?", arrayOf(replacement, key))
                                }
                            }
                        }
                        // Existing target records remain intact; incoming versions stay in the immutable snapshot and server conflicts.
                    }
                } finally { sql.execSQL("UPDATE sync_configuration SET applyingRemote=0") }
            }
            com.example.kpkn.data.backup.PortableBackup.copyMissingPreferences(GuestSnapshotFiles.remap(plan.preferences, paths).jsonObject, target)
            val atomic = AtomicFile(File(target.noBackupFilesDir, "pending-guest-import.json")); val stream = atomic.startWrite()
            try { stream.write(root.canonicalPath.toByteArray()); atomic.finishWrite(stream) } catch (error: Throwable) { atomic.failWrite(stream); throw error }
        } finally { database.close() }
    }
    suspend fun resume(target: Context, uid: String) = withContext(Dispatchers.IO) {
        val pointer = File(target.noBackupFilesDir, "pending-guest-import.json")
        if (!pointer.isFile) return@withContext
        val root = File(pointer.readText()).canonicalFile
        val plan = read(root); require(plan.ownerUid == uid)
        val expectedRoot = File(ProfileStorage.context(platform, plan.source).noBackupFilesDir, "guest-import").canonicalFile
        require(root == expectedRoot)
        if (plan.completed) { pointer.delete(); return@withContext }
        val server = requireNotNull(transport).call(uid, "beginGuestImport", buildJsonObject {
            put("guestProfileId", plan.guestProfileId); put("deviceId", plan.deviceId); put("importId", plan.id)
            put("manifestHash", plan.manifestHash); put("expectedRecords", plan.files.size)
        })
        val generation = server.getValue("generation").jsonPrimitive.long
        require(plan.snapshotVersion == 2)
        val privateData = PrivateDataTransport(target, uid, requireNotNull(session))
        val media = MediaTransfer(target, uid, requireNotNull(session))
        for (file in plan.files) {
            var operation = operation(root, file).copy(accountGeneration = generation)
            require(plan.operationHashes[file] == MediaFiles.hash(File(root, file)))
            media.upload(generation, operation.payload)
            if (json.encodeToString(PersonalOperation.serializer(), operation).toByteArray().size > 512 * 1024) {
                val reference = privateData.stage(requireNotNull(operation.payload)); privateData.upload(generation, reference)
                operation = operation.copy(payload = null, payloadRef = reference)
            }
            requireNotNull(transport).call(uid, "pushChanges", buildJsonObject { put("operations", JsonArray(listOf(json.encodeToJsonElement(PersonalOperation.serializer(), operation)))) })
        }
        val receipt = requireNotNull(transport).call(uid, "finishGuestImport", buildJsonObject { put("importId", plan.id) })
        check(receipt.getValue("status").jsonPrimitive.content == "COMPLETE" && receipt.getValue("verifiedRecords").jsonPrimitive.int == plan.files.size)
        write(root, plan.copy(completed = true)); pointer.delete()
    }
    private fun operation(root: File, name: String): PersonalOperation {
        require(name.matches(Regex("[0-9]{8}\\.json")))
        return json.decodeFromString(PersonalOperation.serializer(), File(root, name).inputStream().use { BoundedInput.bytes(it, 65L * 1024 * 1024).toString(Charsets.UTF_8) })
    }
    private fun read(root: File): GuestImportPlan = json.decodeFromString(GuestImportPlan.serializer(), AtomicFile(File(root, "plan.json")).openRead().use { BoundedInput.bytes(it, 16L * 1024 * 1024).toString(Charsets.UTF_8) })
    private fun write(root: File, plan: GuestImportPlan) {
        val encoded = json.encodeToString(GuestImportPlan.serializer(), plan).toByteArray()
        require(encoded.size <= 16 * 1024 * 1024 - 2048) { "Guest manifest exceeds the permitted size" }
        val atomic = AtomicFile(File(root, "plan.json")); val stream = atomic.startWrite()
        try { stream.write(encoded); atomic.finishWrite(stream) }
        catch (error: Throwable) { atomic.failWrite(stream); throw error }
    }
}
