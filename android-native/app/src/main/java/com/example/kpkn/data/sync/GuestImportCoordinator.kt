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
    val deviceId: String, val files: List<String>, val manifestHash: String, val ownerUid: String? = null, val completed: Boolean = false)

/** The immutable guest snapshot is retained until verification; retries use the same operation identities. */
class GuestImportCoordinator(private val platform: Context, private val session: FirebaseSession, private val transport: AccountTransport) {
    private val json = Json { encodeDefaults = true; ignoreUnknownKeys = true }
    suspend fun prepare(source: Context, database: KpknDatabase): File = withContext(Dispatchers.IO) {
        require(ProfileStorage.current().uid == null)
        val root = File(source.noBackupFilesDir, "guest-import").also { it.mkdirs() }
        val saved = File(root, "plan.json")
        if (saved.isFile) return@withContext root
        val id = UUID.randomUUID().toString(); val deviceId = UUID.randomUUID().toString()
        val unitSystem = database.settingsDao().get()?.let { com.example.kpkn.data.db.dbJson.decodeFromString(com.example.kpkn.data.models.Settings.serializer(), it.data) }
            ?.weightUnit?.let { if (it == com.example.kpkn.data.models.WeightUnit.LBS) "IMPERIAL" else "METRIC" } ?: "METRIC"
        val leaves = mutableListOf<JsonObject>(); val files = mutableListOf<String>()
        val codec = PersonalRecordCodec(source)
        database.runInTransaction {
            val sql = database.openHelper.readableDatabase
            SyncJournalSchema.keys.forEach { (table, key) ->
                val keys = sql.query("SELECT `$key` FROM `$table` ORDER BY `$key`").use { cursor -> buildList { while (cursor.moveToNext()) add(cursor.getString(0)) } }
                keys.forEach { raw ->
                    require(files.size < 100_000)
                    val payload = requireNotNull(codec.snapshot(sql, table, raw))
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
        write(root, GuestImportPlan(id, UUID.randomUUID().toString(), ProfileStorage.current(), deviceId, files,
            PersonalRecordCodec.sha256(CanonicalJson.encode(manifest).toByteArray())))
        root
    }
    suspend fun bindAndCopy(root: File, uid: String) = withContext(Dispatchers.IO) {
        val plan = read(root)
        require(plan.ownerUid == null || plan.ownerUid == uid) { "Guest snapshot belongs to another account" }
        write(root, plan.copy(ownerUid = uid))
        val target = ProfileStorage.context(platform, LocalProfile(uid = uid))
        val source = ProfileStorage.context(platform, plan.source)
        GuestSourceLedger.record(target, uid, plan.id, plan.source)
        val sourceSql = KpknDatabase.getInstance(source).openHelper.readableDatabase
        val database = KpknDatabase.openIsolated(target, "kpkn.db")
        try {
            database.runInTransaction {
                val sql = database.openHelper.writableDatabase
                sql.execSQL("UPDATE sync_configuration SET applyingRemote=1")
                try {
                    val codec = PersonalRecordCodec(target)
                    plan.files.forEach { file ->
                        val operation = operation(root, file)
                        val table = PersonalRecordCodec.kinds.entries.first { it.value == operation.kind }.key
                        val key = requireNotNull(operation.sourceId)
                        if (BoundedSqlRows.read(sql, table, SyncJournalSchema.keys.getValue(table), key) == null) {
                            codec.apply(sql, operation.kind, key, operation.payload, false)
                            if (table == "workout_media") {
                                val original = requireNotNull(BoundedSqlRows.read(sourceSql, table, "id", key))
                                listOf("filePath", "thumbPath", "poseTrackPath").forEach { column ->
                                    val path = original[column] as? String ?: return@forEach
                                    val file = File(path).canonicalFile
                                    val origin = source.filesDir.canonicalFile
                                    if (file.toPath().startsWith(origin.toPath()) && file.relativeTo(origin).invariantSeparatorsPath.substringBefore('/') in com.example.kpkn.data.backup.PortableBackup.portableRoots)
                                        sql.execSQL("UPDATE workout_media SET `$column`=? WHERE id=?", arrayOf(File(target.filesDir, file.relativeTo(origin).path).absolutePath, key))
                                }
                            }
                        }
                        // Existing target records remain intact; incoming versions stay in the immutable snapshot and server conflicts.
                    }
                } finally { sql.execSQL("UPDATE sync_configuration SET applyingRemote=0") }
            }
            com.example.kpkn.data.backup.PortableBackup.portableRoots.forEach { name ->
                val original = File(source.filesDir, name)
                if (original.isDirectory) original.walkTopDown().filter { it.isFile }.forEach { file ->
                    require(file.canonicalFile.toPath().startsWith(original.canonicalFile.toPath()))
                    val destination = File(target.filesDir, file.relativeTo(source.filesDir).path)
                    if (!destination.exists()) { destination.parentFile!!.mkdirs(); file.copyTo(destination); java.io.FileInputStream(destination).use { it.fd.sync() } }
                }
            }
            com.example.kpkn.data.backup.PortableBackup.copyMissingPreferences(source, target)
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
        val server = transport.call(uid, "beginGuestImport", buildJsonObject {
            put("guestProfileId", plan.guestProfileId); put("deviceId", plan.deviceId); put("importId", plan.id)
            put("manifestHash", plan.manifestHash); put("expectedRecords", plan.files.size)
        })
        val generation = server.getValue("generation").jsonPrimitive.long
        val privateData = PrivateDataTransport(ProfileStorage.context(platform, plan.source), uid, session)
        for (file in plan.files) {
            var operation = operation(root, file).copy(accountGeneration = generation)
            if (json.encodeToString(PersonalOperation.serializer(), operation).toByteArray().size > 512 * 1024) {
                val reference = privateData.stage(requireNotNull(operation.payload)); privateData.upload(generation, reference)
                operation = operation.copy(payload = null, payloadRef = reference)
            }
            transport.call(uid, "pushChanges", buildJsonObject { put("operations", JsonArray(listOf(json.encodeToJsonElement(PersonalOperation.serializer(), operation)))) })
        }
        val receipt = transport.call(uid, "finishGuestImport", buildJsonObject { put("importId", plan.id) })
        check(receipt.getValue("status").jsonPrimitive.content == "COMPLETE" && receipt.getValue("verifiedRecords").jsonPrimitive.int == plan.files.size)
        write(root, plan.copy(completed = true)); pointer.delete()
    }
    private fun operation(root: File, name: String): PersonalOperation {
        require(name.matches(Regex("[0-9]{8}\\.json")))
        return json.decodeFromString(PersonalOperation.serializer(), File(root, name).inputStream().use { BoundedInput.bytes(it, 65L * 1024 * 1024).toString(Charsets.UTF_8) })
    }
    private fun read(root: File): GuestImportPlan = json.decodeFromString(GuestImportPlan.serializer(), File(root, "plan.json").inputStream().use { BoundedInput.bytes(it, 4L * 1024 * 1024).toString(Charsets.UTF_8) })
    private fun write(root: File, plan: GuestImportPlan) {
        val atomic = AtomicFile(File(root, "plan.json")); val stream = atomic.startWrite()
        try { stream.write(json.encodeToString(GuestImportPlan.serializer(), plan).toByteArray()); atomic.finishWrite(stream) }
        catch (error: Throwable) { atomic.failWrite(stream); throw error }
    }
}
