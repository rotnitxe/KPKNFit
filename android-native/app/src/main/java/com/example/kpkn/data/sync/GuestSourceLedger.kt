package com.example.kpkn.data.sync

import android.content.Context
import android.util.AtomicFile
import com.example.kpkn.data.account.LocalProfile
import com.example.kpkn.data.account.ProfileContext
import com.example.kpkn.data.account.ProfileStorage
import com.example.kpkn.data.backup.BoundedInput
import com.example.kpkn.data.backup.PersonalStorageManifest
import kotlinx.serialization.Serializable
import kotlinx.serialization.builtins.ListSerializer
import kotlinx.serialization.json.Json
import java.io.File

@Serializable private data class OwnedGuest(val uid: String, val importId: String, val source: LocalProfile)

/** Keeps ownership proof after import finishes, so deleting an account also erases its retained guest source. */
object GuestSourceLedger {
    private val json = Json { ignoreUnknownKeys = true }
    private val serializer = ListSerializer(OwnedGuest.serializer())
    private fun file(context: Context) = AtomicFile(File(context.noBackupFilesDir, "owned-guest-sources.json"))
    private fun read(context: Context): List<OwnedGuest> {
        val atomic = file(context)
        if (!atomic.baseFile.exists() && !File(atomic.baseFile.path + ".bak").exists()) return emptyList()
        return atomic.openRead().use { json.decodeFromString(serializer, BoundedInput.bytes(it, 1024 * 1024).toString(Charsets.UTF_8)) }
    }
    fun record(context: Context, uid: String, importId: String, source: LocalProfile) {
        require((context as ProfileContext).profile.uid == uid && source.uid == null)
        val entries = (read(context) + OwnedGuest(uid, importId, source)).distinctBy { it.importId }
        require(entries.size <= 1024)
        val atomic = file(context); val stream = atomic.startWrite()
        try { stream.write(json.encodeToString(serializer, entries).toByteArray()); atomic.finishWrite(stream) }
        catch (error: Throwable) { atomic.failWrite(stream); throw error }
    }
    fun eraseOwnedSources(context: ProfileContext) {
        val uid = context.profile.uid ?: return
        for (entry in read(context)) {
            require(entry.uid == uid && entry.source.uid == null && entry.source.key != context.profile.key)
            val source = ProfileStorage.context(context, entry.source) as ProfileContext
            val planFile = File(source.noBackupFilesDir, "guest-import/plan.json")
            if (!planFile.exists()) continue // A previous interrupted erase already removed this source.
            val plan = planFile.inputStream().use { json.decodeFromString(GuestImportPlan.serializer(),
                BoundedInput.bytes(it, 16L * 1024 * 1024).toString(Charsets.UTF_8)) }
            require(plan.ownerUid == uid && plan.id == entry.importId && plan.source.key == source.profile.key)
            if (source.getDatabasePath("kpkn.db").exists()) check(source.deleteDatabase("kpkn.db"))
            PersonalStorageManifest.clearFilesAndPreferences(source)
        }
    }

    /** Capture ownership before deleting any plan, so an interrupted erase retains that proof. */
    internal fun verifiedSources(context: ProfileContext): List<LocalProfile> {
        val uid = context.profile.uid ?: return emptyList()
        return read(context).map { entry ->
            require(entry.uid == uid && entry.source.uid == null && entry.source.key != context.profile.key)
            val source = ProfileStorage.context(context, entry.source)
            val planFile = File(source.noBackupFilesDir, "guest-import/plan.json")
            if (planFile.exists()) {
                val plan = planFile.inputStream().use { json.decodeFromString(GuestImportPlan.serializer(),
                    BoundedInput.bytes(it, 16L * 1024 * 1024).toString(Charsets.UTF_8)) }
                require(plan.ownerUid == uid && plan.id == entry.importId && plan.source.key == entry.source.key)
            }
            entry.source
        }.distinctBy { it.key }
    }
}
