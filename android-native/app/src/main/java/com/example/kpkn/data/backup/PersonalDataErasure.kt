package com.example.kpkn.data.backup

import android.content.Context
import android.util.AtomicFile
import com.example.kpkn.data.account.LocalProfile
import com.example.kpkn.data.account.ProfileContext
import com.example.kpkn.data.account.ProfileStorage
import com.example.kpkn.data.db.KpknDatabase
import com.example.kpkn.data.sync.GuestSourceLedger
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.Json

@Serializable
internal data class PendingProfileErasure(val version: Int = 1, val owner: LocalProfile,
    val ownedGuestSources: List<LocalProfile> = emptyList())

/** The journal lives outside erased profile roots and is removed only after all owned stores are gone. */
object PersonalDataErasure {
    private val json = Json { encodeDefaults = true; ignoreUnknownKeys = true }
    private fun file() = AtomicFile(ProfileStorage.erasureJournal())
    fun pending(): Boolean = ProfileStorage.initialized() && (file().baseFile.exists() || java.io.File(file().baseFile.path + ".bak").exists())

    internal fun read(): PendingProfileErasure = file().openRead().use { input ->
        json.decodeFromString(PendingProfileErasure.serializer(), BoundedInput.bytes(input, 256 * 1024).toString(Charsets.UTF_8))
    }.also { record ->
        require(record.version == 1 && record.ownedGuestSources.size <= 1024)
        (record.ownedGuestSources + record.owner).forEach { profile ->
            require(profile.epoch > 0 && (profile.uid == null || profile.uid.isNotBlank() && profile.uid.length <= 128))
            require(profile.guestId.matches(Regex("[A-Za-z0-9_-]{1,128}")))
        }
        require(record.ownedGuestSources.all { it.uid == null && it.key != record.owner.key })
        require(record.owner.uid != null || record.ownedGuestSources.isEmpty())
    }

    internal fun begin(context: Context) {
        val captured = context.applicationContext as? ProfileContext
            ?: ProfileStorage.context(context) as ProfileContext
        if (pending()) { check(read().owner.key == captured.profile.key) { "Another profile erasure must finish first" }; return }
        val record = PendingProfileErasure(owner = captured.profile, ownedGuestSources = GuestSourceLedger.verifiedSources(captured))
        val atomic = file()
        val output = atomic.startWrite()
        try { output.write(json.encodeToString(PendingProfileErasure.serializer(), record).toByteArray()); atomic.finishWrite(output) }
        catch (error: Throwable) { atomic.failWrite(output); throw error }
    }

    /** Caller must first stop every writer; bootstrap recovery uses the same barrier. */
    suspend fun erase(context: Context) { begin(context); finishStores(context) }

    internal suspend fun finishStores(context: Context, afterProfile: (Int) -> Unit = {}) {
        val record = read()
        KpknDatabase.closeInstance()
        (record.ownedGuestSources + record.owner).distinctBy { it.key }.forEachIndexed { index, profile ->
            val captured = ProfileStorage.context(context, profile) as ProfileContext
            if (captured.getDatabasePath("kpkn.db").exists()) check(captured.deleteDatabase("kpkn.db")) { "Unable to erase personal database" }
            PersonalStorageManifest.clearFilesAndPreferences(captured, includeOwnedSources = false)
            check(PersonalStorageManifest.personalFileRoots.none { java.io.File(captured.filesDir, it).exists() })
            check(captured.personalPreferenceNames().all { captured.getSharedPreferences(it, 0).all.isEmpty() })
            afterProfile(index)
        }
        file().delete()
        check(!pending()) { "Unable to finish profile erasure" }
        com.example.kpkn.data.account.AccountDeletionNotices.clearLocalPending()
    }
}
