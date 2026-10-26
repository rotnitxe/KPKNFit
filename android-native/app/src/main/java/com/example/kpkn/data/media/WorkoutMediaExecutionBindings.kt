package com.example.kpkn.data.media

import android.util.AtomicFile
import com.example.kpkn.data.models.WorkoutSetExecutionRef
import kotlinx.serialization.encodeToString
import kotlinx.serialization.decodeFromString
import kotlinx.serialization.json.Json
import java.io.File
import java.io.IOException
import java.security.MessageDigest

/** Immutable capture provenance. Sidecars preserve it without changing the published Room schema. */
internal class WorkoutMediaExecutionBindings(filesDir: File) {
    private val root = File(filesDir, "workout_media/execution_refs")
    private val json = Json { ignoreUnknownKeys = true; encodeDefaults = true }
    private fun file(id: String): File = File(root, MessageDigest.getInstance("SHA-256")
        .digest(id.toByteArray(Charsets.UTF_8)).joinToString("") { "%02x".format(it) } + ".json")

    @Synchronized fun write(id: String, ref: WorkoutSetExecutionRef) {
        val target = file(id)
        if (target.exists()) {
            check(read(id) == ref) { "A capture cannot change its execution identity" }
            return
        }
        if (!root.isDirectory && !root.mkdirs()) throw IOException("Cannot persist capture identity")
        val atomic = AtomicFile(target)
        val stream = atomic.startWrite()
        try { stream.write(json.encodeToString(ref).toByteArray(Charsets.UTF_8)); atomic.finishWrite(stream) }
        catch (failure: Exception) { atomic.failWrite(stream); throw failure }
    }

    @Synchronized fun read(id: String): WorkoutSetExecutionRef? {
        val target = file(id)
        if (!target.exists()) return null
        return runCatching { AtomicFile(target).openRead().use { json.decodeFromString<WorkoutSetExecutionRef>(it.readBytes().toString(Charsets.UTF_8)) } }.getOrNull()
    }
}
