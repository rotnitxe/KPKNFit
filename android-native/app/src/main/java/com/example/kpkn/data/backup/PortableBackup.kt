package com.example.kpkn.data.backup

import android.content.Context
import android.util.AtomicFile
import com.example.kpkn.data.account.ProfileContext
import com.example.kpkn.data.db.*
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.*
import java.io.File
import java.io.InputStream
import java.io.OutputStream
import java.nio.file.Files
import java.nio.file.StandardCopyOption
import java.security.MessageDigest
import java.util.UUID
import java.util.zip.ZipEntry
import java.util.zip.ZipInputStream
import java.util.zip.ZipOutputStream

@Serializable data class PortableFile(val path: String, val bytes: Long, val sha256: String)
@Serializable data class PortableManifest(val schemaVersion: Int = 2, val databaseVersion: Int = KpknDatabase.DATABASE_VERSION,
    val createdAtEpochMs: Long, val files: List<PortableFile>, val preferences: JsonObject)

/** Durable rollback spans Room, private files and preferences. Recovery runs before readers start. */
object PortableBackup {
    private val json = Json { encodeDefaults = true; ignoreUnknownKeys = true }
    private const val MAX_TOTAL = 2L * 1024 * 1024 * 1024
    private const val MAX_ENTRY = 512L * 1024 * 1024
    private const val MAX_FILES = 20_000
    val portableRoots = setOf("profile", "workout_media", "workout_media_pending", "workout_photos",
        "exercise_user_media", "competition_media", "cardio-gps", "legacy-body-recovery.json")
    internal fun portableFile(context: Context, root: String): File =
        if (root == "legacy-body-recovery.json") File(context.noBackupFilesDir, "legacy-body-recovery.json") else File(context.filesDir, root)
    private val excludedPreferences = setOf("kpkn_secure_deepseek", "workout_rest_alerts", "kpkn_diagnostic_storage",
        "kpkn_voice_exit_info", "kpkn_entitlement", "kpkn_sync_scheduler", "workout_voice_diagnostic_storage",
        "diagnostic_consent", "sync_media_transfer", "sync_capabilities")
    private val secretKey = Regex("(?i)(api.?key|password|credential|purchase.?token|auth.?token|grant|tree.?uri)")

    suspend fun export(context: Context, output: OutputStream) = withContext(Dispatchers.IO) {
        val workspace = File(context.cacheDir, "portable-export/${UUID.randomUUID()}").also { check(it.mkdirs()) }
        val snapshot = File(context.filesDir, "snapshots/${DatabaseBackupHelper.createSnapshot(context)}")
        val name = "restore_${UUID.randomUUID()}.db"
        val databaseFile = context.getDatabasePath(name)
        try {
            snapshot.copyTo(databaseFile, overwrite = true)
            rewriteFiles(context, name, portable = true)
            val entries = mutableListOf("database.db" to databaseFile)
            portableRoots.sorted().forEach { root ->
                val directory = portableFile(context, root)
                if (directory.isDirectory) directory.walkTopDown().filter { it.isFile }.forEach { file ->
                    require(file.canonicalFile.toPath().startsWith(directory.canonicalFile.toPath())) { "Unsupported linked file" }
                    entries += "files/${file.relativeTo(context.filesDir).invariantSeparatorsPath}" to file
                }
                else if (directory.isFile) entries += "files/$root" to directory
            }
            require(entries.size <= MAX_FILES)
            var total = 0L
            val files = entries.map { (path, file) ->
                require(file.length() in 0..MAX_ENTRY); total += file.length(); require(total <= MAX_TOTAL)
                PortableFile(path, file.length(), hash(file))
            }
            val manifest = PortableManifest(createdAtEpochMs = System.currentTimeMillis(), files = files, preferences = preferences(context))
            ZipOutputStream(output).use { zip ->
                zip.putNextEntry(ZipEntry("manifest.json")); zip.write(json.encodeToString(PortableManifest.serializer(), manifest).toByteArray()); zip.closeEntry()
                entries.forEach { (path, file) -> zip.putNextEntry(ZipEntry(path)); file.inputStream().use { BoundedInput.copy(it, zip, MAX_ENTRY) }; zip.closeEntry() }
            }
        } finally { snapshot.delete(); context.deleteDatabase(name); workspace.deleteRecursively() }
    }

    suspend fun restore(context: Context, input: InputStream) = restore(context, input, MaintenanceCheckpoint.None)

    internal suspend fun restore(context: Context, input: InputStream, checkpoint: MaintenanceCheckpoint) = withContext(Dispatchers.IO) {
        val stage = File(context.noBackupFilesDir, "portable-stage/${UUID.randomUUID()}").also { check(it.mkdirs()) }
        try {
            val manifest = extract(input, stage, checkpoint)
            transaction(context, checkpoint) {
                portableRoots.forEach { root ->
                    val destination = portableFile(context, root)
                    if (destination.exists()) check(destination.deleteRecursively())
                    File(stage, "files/$root").takeIf { it.exists() }?.let { move(it, destination) }
                    checkpoint.reached(MaintenancePhase.PORTABLE_ROOT_REPLACED)
                }
                val name = "restore_${UUID.randomUUID()}.db"
                val imported = context.getDatabasePath(name)
                try {
                    File(stage, "database.db").copyTo(imported, overwrite = true)
                    rewriteFiles(context, name, portable = false)
                    DatabaseBackupHelper.restoreSnapshot(context, imported, recovering = false, checkpoint = checkpoint)
                    checkpoint.reached(MaintenancePhase.DATABASE_APPLIED)
                    restorePreferences(context, manifest.preferences)
                    checkpoint.reached(MaintenancePhase.PREFERENCES_APPLIED)
                } finally { context.deleteDatabase(name) }
            }
        } finally { stage.deleteRecursively() }
    }

    /** Call only while the DataSessionCoordinator has stopped all personal writers. */
    suspend fun <T> transaction(context: Context, action: suspend () -> T): T =
        transaction(context, MaintenanceCheckpoint.None, action)

    internal suspend fun <T> transaction(context: Context, checkpoint: MaintenanceCheckpoint, action: suspend () -> T): T {
        recover(context)
        val root = File(context.noBackupFilesDir, "maintenance")
        check(!root.exists() || root.deleteRecursively()); check(root.mkdirs())
        val oldDatabase = File(context.filesDir, "snapshots/${DatabaseBackupHelper.createSnapshot(context, checkpoint)}")
        val oldPreferences = preferences(context, includeDeviceState = true)
        File(root, "rollback.db").also { oldDatabase.copyTo(it); sync(it) }; oldDatabase.delete()
        checkpoint.reached(MaintenancePhase.ROLLBACK_DATABASE_COPIED)
        File(root, "preferences.json").also { it.writeText(oldPreferences.toString()); sync(it) }
        portableRoots.forEach { name ->
            val source = portableFile(context, name)
            if (source.exists()) {
                val destination = File(root, "rollback-files/$name")
                copyRollbackFiles(source, destination, checkpoint)
            }
        }
        writeState(root, "PREPARED")
        checkpoint.reached(MaintenancePhase.PREPARED_SYNCED)
        try {
            val result = action()
            writeState(root, "COMMITTED", checkpoint)
            checkpoint.reached(MaintenancePhase.COMMITTED_SYNCED)
            // COMMITTED is the durable outcome. Cleanup can be retried at the next startup;
            // lack of space or a temporary file lock must not turn a completed restore into failure.
            runCatching { root.deleteRecursively() }
            return result
        } catch (error: Throwable) {
            try { recover(context) } catch (rollback: Throwable) { error.addSuppressed(rollback) }
            throw error
        }
    }

    fun recover(context: Context) = recover(context, MaintenanceCheckpoint.None)

    internal fun recover(context: Context, checkpoint: MaintenanceCheckpoint) {
        val root = File(context.noBackupFilesDir, "maintenance")
        if (!root.exists()) return
        val atomicState = AtomicFile(File(root, "state"))
        val hasCommittedState = atomicState.baseFile.exists() || File(atomicState.baseFile.path + ".bak").exists()
        // AtomicFile must recover an API26 backup before any rollback input can be deleted.
        // No base/backup means preparation never durably admitted the destructive action.
        val state = if (hasCommittedState) atomicState.openRead().use {
            BoundedInput.bytes(it, 32).toString(Charsets.UTF_8)
        }.also {
            check(it in setOf("PREPARED", "COMMITTED", "RECOVERED")) { "The maintenance journal requires recovery" }
        } else null
        if (state == "PREPARED") {
            // Copies, rather than moves, keep the preimage intact if recovery itself is interrupted.
            portableRoots.forEach { name ->
                val target = portableFile(context, name)
                if (target.exists()) check(target.deleteRecursively())
                File(root, "rollback-files/$name").takeIf { it.exists() }?.let { check(it.copyRecursively(target, overwrite = true)) }
                checkpoint.reached(MaintenancePhase.RECOVERY_ROOT_RESTORED)
            }
            DatabaseBackupHelper.restoreSnapshot(context, File(root, "rollback.db"), recovering = true, checkpoint = checkpoint)
            checkpoint.reached(MaintenancePhase.RECOVERY_DATABASE_APPLIED)
            restorePreferences(context, Json.parseToJsonElement(File(root, "preferences.json").readText()).jsonObject, includeDeviceState = true)
            checkpoint.reached(MaintenancePhase.RECOVERY_PREFERENCES_APPLIED)
            writeState(root, "RECOVERED")
            checkpoint.reached(MaintenancePhase.RECOVERY_SYNCED)
        }
        check(root.deleteRecursively())
    }

    private fun extract(input: InputStream, stage: File, checkpoint: MaintenanceCheckpoint): PortableManifest = ZipInputStream(input).use { zip ->
        require(zip.nextEntry?.name == "manifest.json") { "Invalid portable backup" }
        val manifest = json.decodeFromString(PortableManifest.serializer(), BoundedInput.bytes(zip, 8L * 1024 * 1024).toString(Charsets.UTF_8))
        require(manifest.schemaVersion in 1..2 && manifest.databaseVersion in 1..KpknDatabase.DATABASE_VERSION)
        require(manifest.files.size in 1..MAX_FILES && manifest.files.map { it.path }.toSet().size == manifest.files.size)
        require(manifest.files.count { it.path == "database.db" } == 1)
        var total = 0L
        val expected = manifest.files.associateBy { entry ->
            safeEntry(entry.path); require(entry.bytes in 0..MAX_ENTRY && entry.sha256.matches(Regex("[a-f0-9]{64}")))
            total += entry.bytes; require(total <= MAX_TOTAL); entry.path
        }.toMutableMap()
        while (true) {
            val entry = zip.nextEntry ?: break
            safeEntry(entry.name); require(!entry.isDirectory)
            val description = expected.remove(entry.name) ?: error("Unexpected or duplicate backup file")
            val file = File(stage, entry.name).also { check(it.parentFile!!.isDirectory || it.parentFile!!.mkdirs()) }
            java.io.FileOutputStream(file).use { output ->
                BoundedInput.copy(zip, observedOutput(output, checkpoint, MaintenancePhase.IMPORT_COPYING), description.bytes)
                output.fd.sync()
            }
            require(file.length() == description.bytes && hash(file) == description.sha256) { "Backup verification failed" }
        }
        require(expected.isEmpty()) { "Backup is incomplete" }
        manifest
    }
    private fun safeEntry(name: String) {
        require(name.length <= 1024 && !name.contains('\\') && name.split('/').none { it.isBlank() || it in setOf(".", "..") })
        require(name == "database.db" || name.startsWith("files/") && name.split('/').getOrNull(1) in portableRoots)
    }
    private fun rewriteFiles(context: Context, name: String, portable: Boolean) {
        val database = KpknDatabase.openIsolated(context, name)
        try {
            database.runInTransaction {
                val sql = database.openHelper.writableDatabase
                sql.execSQL("UPDATE sync_configuration SET enabled=0,applyingRemote=1")
                SyncJournalSchema.keys.forEach { (table, primary) ->
                    val columns = BoundedSqlRows.columns(sql, table)
                    val keys = sql.query("SELECT `$primary` FROM `$table`").use { cursor -> buildList { while (cursor.moveToNext()) add(cursor.getString(0)) } }
                    keys.forEach { key ->
                        val row = requireNotNull(BoundedSqlRows.read(sql, table, primary, key, columns))
                        columns.filter { it.type == "TEXT" }.forEach { column ->
                            val value = row[column.name] as? String ?: return@forEach
                            val mediaColumn = table == "workout_media" && column.name in setOf("filePath", "thumbPath", "poseTrackPath")
                            val updated = if (mediaColumn && portable && value.isNotBlank() && !File(value).isFile) {
                                val missing = File(value).canonicalFile
                                require(missing.toPath().startsWith(context.filesDir.canonicalFile.toPath())) { "Unowned media path" }
                                "kpkn-pending-file:${column.name}:${com.example.kpkn.data.sync.PersonalRecordCodec.sha256(key.toByteArray())}"
                            } else if (mediaColumn && !portable && value.startsWith("kpkn-pending-file:")) {
                                require(value.matches(Regex("kpkn-pending-file:(filePath|thumbPath|poseTrackPath):[a-f0-9]{64}")))
                                File(context.filesDir, "workout_media/cloud/${value.substringAfterLast(':')}-${column.name}.pending").absolutePath
                            } else if (value.startsWith("{") || value.startsWith("[")) {
                                var element = runCatching { Json.parseToJsonElement(value) }.getOrElse {
                                    if (column.name == "data" || column.name.endsWith("Json")) throw it else JsonPrimitive(value)
                                }
                                if (table == "settings" && column.name == "data" && portable) element = JsonObject(element.jsonObject + mapOf(
                                    "apiKeys" to buildJsonObject {}, "isPro" to JsonPrimitive(false), "crashlyticsEnabled" to JsonPrimitive(false), "analyticsEnabled" to JsonPrimitive(false)))
                                rewriteElement(context, element, portable, 0).toString()
                            } else rewritePath(context, value, portable)
                            if (mediaColumn && !portable && updated.isNotBlank()) {
                                val root = context.filesDir.canonicalFile
                                val referenced = File(updated).canonicalFile
                                require(referenced.toPath().startsWith(root.toPath()) &&
                                    referenced.relativeTo(root).invariantSeparatorsPath.substringBefore('/') in portableRoots &&
                                    (referenced.isFile || value.startsWith("kpkn-pending-file:"))) {
                                    "Backup media refers to a missing or unowned file"
                                }
                            }
                            if (updated != value) sql.execSQL("UPDATE `$table` SET `${column.name}`=? WHERE `$primary`=?", arrayOf(updated, key))
                        }
                    }
                }
                sql.execSQL("DELETE FROM sync_mutations"); sql.execSQL("DELETE FROM sync_shadows")
                sql.execSQL("UPDATE sync_configuration SET enabled=0,applyingRemote=0,deviceId='',accountGeneration=0,pullCursor=0,confirmedAtEpochMs=0")
            }
        } finally { database.close() }
    }
    private fun rewriteElement(context: Context, value: JsonElement, portable: Boolean, depth: Int): JsonElement {
        require(depth <= 48) { "Personal data is nested too deeply" }
        return when (value) {
        is JsonObject -> JsonObject(value.mapValues { rewriteElement(context, it.value, portable, depth + 1) })
        is JsonArray -> JsonArray(value.map { rewriteElement(context, it, portable, depth + 1) })
        is JsonPrimitive -> if (value.isString) JsonPrimitive(rewritePath(context, value.content, portable)) else value
        else -> value
        }
    }
    private fun rewritePath(context: Context, value: String, portable: Boolean): String {
        if (portable) {
            val root = context.filesDir.canonicalFile
            if (!value.startsWith(root.path + File.separator)) return value
            val target = File(value).canonicalFile
            require(target.toPath().startsWith(root.toPath()))
            val relative = target.relativeTo(root).invariantSeparatorsPath
            return if (relative.substringBefore('/') in portableRoots) "kpkn-file:$relative" else value
        }
        if (!value.startsWith("kpkn-file:")) return value
        val relative = value.removePrefix("kpkn-file:"); safeEntry("files/$relative")
        val destination = File(context.filesDir, relative).canonicalFile
        require(destination.toPath().startsWith(context.filesDir.canonicalFile.toPath()) && destination.isFile) {
            "Backup refers to a missing personal file"
        }
        return destination.absolutePath
    }
    internal fun preferences(context: Context, includeDeviceState: Boolean = false): JsonObject {
        val profile = context as? ProfileContext ?: com.example.kpkn.data.account.ProfileStorage.context(context) as ProfileContext
        return buildJsonObject {
            profile.personalPreferenceNames().filter { includeDeviceState || it !in excludedPreferences }.forEach { name ->
                val store = context.getSharedPreferences(name, 0)
                check(store.edit().commit()) { "Could not flush pending personal preferences" }
                put(name, buildJsonObject { store.all.forEach { (key, value) ->
                    if (includeDeviceState || !secretKey.containsMatchIn(key)) put(key, when (value) {
                        null -> JsonNull; is String -> JsonPrimitive(value); is Boolean -> JsonPrimitive(value)
                        is Number -> buildJsonObject { put("numberType", value.javaClass.simpleName); put("value", value.toString()) }
                        is Set<*> -> JsonArray(value.filterIsInstance<String>().map(::JsonPrimitive))
                        else -> error("Unsupported personal preference")
                    })
                } })
            }
        }
    }
    private fun restorePreferences(context: Context, values: JsonObject, includeDeviceState: Boolean = false) {
        val profile = context as? ProfileContext ?: com.example.kpkn.data.account.ProfileStorage.context(context) as ProfileContext
        profile.clearPersonalPreferences()
        values.forEach { (name, fields) ->
            require(!com.example.kpkn.data.account.ProfileStorage.isPlatformPreference(name))
            require(includeDeviceState || name !in excludedPreferences)
            val editor = context.getSharedPreferences(name, 0).edit()
            fields.jsonObject.forEach { (key, value) ->
                require(includeDeviceState || !secretKey.containsMatchIn(key))
                when (value) {
                JsonNull -> editor.remove(key)
                is JsonArray -> editor.putStringSet(key, value.map { it.jsonPrimitive.content }.toSet())
                is JsonObject -> when (value.getValue("numberType").jsonPrimitive.content) {
                    "Integer" -> editor.putInt(key, value.getValue("value").jsonPrimitive.int)
                    "Long" -> editor.putLong(key, value.getValue("value").jsonPrimitive.long)
                    "Float" -> editor.putFloat(key, value.getValue("value").jsonPrimitive.float)
                    else -> error("Invalid preference number type")
                }
                is JsonPrimitive -> if (value.isString) editor.putString(key, value.content) else editor.putBoolean(key, value.boolean)
            } }
            check(editor.commit()) { "Could not persist personal preferences" }
        }
    }
    fun copyMissingPreferences(source: Context, target: Context) {
        copyMissingPreferences(preferences(source), target)
    }
    internal fun copyMissingPreferences(selected: JsonObject, target: Context) {
        selected.forEach { (name, fields) ->
            val existing = target.getSharedPreferences(name, 0).all.keys
            val missing = JsonObject(fields.jsonObject.filterKeys { it !in existing })
            if (missing.isNotEmpty()) {
                val original = preferences(target, includeDeviceState = true)
                val merged = JsonObject(original + mapOf(name to JsonObject(original[name]?.jsonObject.orEmpty() + missing)))
                restorePreferences(target, merged, includeDeviceState = true)
            }
        }
    }
    private fun observedOutput(output: OutputStream, checkpoint: MaintenanceCheckpoint, phase: MaintenancePhase): OutputStream =
        object : OutputStream() {
            override fun write(value: Int) { output.write(value); checkpoint.reached(phase) }
            override fun write(bytes: ByteArray, offset: Int, count: Int) {
                output.write(bytes, offset, count)
                if (count > 0) checkpoint.reached(phase)
            }
            override fun flush() = output.flush()
        }

    private fun copyRollbackFiles(source: File, target: File, checkpoint: MaintenanceCheckpoint) {
        if (source.isDirectory) {
            check(target.isDirectory || target.mkdirs())
            source.listFiles()?.forEach { copyRollbackFiles(it, File(target, it.name), checkpoint) }
                ?: error("Unable to enumerate rollback files")
        } else {
            check(target.parentFile!!.isDirectory || target.parentFile!!.mkdirs())
            source.inputStream().use { input -> java.io.FileOutputStream(target).use { output ->
                BoundedInput.copy(input, observedOutput(output, checkpoint, MaintenancePhase.ROLLBACK_FILE_COPYING), Long.MAX_VALUE)
                output.fd.sync()
            } }
        }
    }

    private fun hash(file: File): String {
        val digest = MessageDigest.getInstance("SHA-256")
        file.inputStream().use { input -> val bytes = ByteArray(64 * 1024); while (true) { val count = input.read(bytes); if (count < 0) break; digest.update(bytes, 0, count) } }
        return digest.digest().joinToString("") { "%02x".format(it) }
    }
    private fun sync(file: File) { java.io.FileInputStream(file).use { it.fd.sync() } }
    private fun move(source: File, target: File) { target.parentFile!!.mkdirs(); Files.move(source.toPath(), target.toPath(), StandardCopyOption.ATOMIC_MOVE) }
    private fun writeState(root: File, state: String, checkpoint: MaintenanceCheckpoint = MaintenanceCheckpoint.None) {
        val atomic = AtomicFile(File(root, "state")); val output = atomic.startWrite()
        try {
            if (state == "COMMITTED") checkpoint.reached(MaintenancePhase.COMMIT_JOURNAL_OPENED)
            output.write(state.toByteArray()); atomic.finishWrite(output)
        } catch (error: Throwable) { atomic.failWrite(output); throw error }
    }
}
