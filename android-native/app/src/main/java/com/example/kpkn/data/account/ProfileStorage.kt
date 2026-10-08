package com.example.kpkn.data.account

import android.content.Context
import android.content.ContextWrapper
import android.content.SharedPreferences
import android.content.res.Configuration
import android.database.DatabaseErrorHandler
import android.database.sqlite.SQLiteDatabase
import android.util.AtomicFile
import java.io.File
import java.security.MessageDigest
import java.util.UUID
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.Json
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow

@Serializable
data class LocalProfile(val uid: String? = null, val guestId: String = LEGACY_GUEST, val epoch: Long = 1) {
    val key: String get() = if (uid != null) "user_" + sha256(uid).take(24) else if (guestId == LEGACY_GUEST) LEGACY_GUEST else "guest_" + sha256(guestId).take(24)
    companion object { const val LEGACY_GUEST = "legacy-guest" }
}

private fun sha256(value: String) = MessageDigest.getInstance("SHA-256")
    .digest(value.toByteArray(Charsets.UTF_8)).joinToString("") { "%02x".format(it) }

/** Only opaque profile identity is global. Personal stores remain in distinct directories. */
object ProfileStorage {
    private val lock = Any()
    private val json = Json { ignoreUnknownKeys = true }
    private lateinit var systemContext: Context
    private lateinit var registry: AtomicFile
    @Volatile private var selected = LocalProfile()
    private val _recoveryNeeded = MutableStateFlow(false)
    val recoveryNeeded = _recoveryNeeded.asStateFlow()

    fun initialize(base: Context) = synchronized(lock) {
        if (::systemContext.isInitialized && systemContext === base) return@synchronized
        val root = base.filesDir.canonicalFile
        systemContext = base
        registry = AtomicFile(File(root, "profile-registry.json"))
        _recoveryNeeded.value = false
        // AtomicFile on API 26 can leave ONLY .bak after a process dies between
        // rename and opening the replacement. openRead() must perform recovery.
        val hadRegistry = registry.baseFile.exists() ||
            File(registry.baseFile.path + ".bak").exists() || File(registry.baseFile.path + ".new").exists()
        selected = try {
            registry.openRead().use {
                val bytes = com.example.kpkn.data.backup.BoundedInput.bytes(it, 2048)
                json.decodeFromString<LocalProfile>(bytes.toString(Charsets.UTF_8)).also { profile ->
                    require(profile.epoch > 0 && (profile.uid == null || profile.uid.isNotBlank() && profile.uid.length <= 128))
                    require(profile.guestId.matches(Regex("[A-Za-z0-9_-]{1,128}")))
                }
            }
        } catch (error: Exception) {
            if (!hadRegistry && error is java.io.FileNotFoundException) {
                LocalProfile()
            } else {
                // A corrupt identity registry must never expose the legacy guest or another user's files.
                _recoveryNeeded.value = true
                LocalProfile(guestId = UUID.randomUUID().toString())
            }
        }
    }
    fun current(): LocalProfile = selected
    fun initialized(): Boolean = ::systemContext.isInitialized
    fun platformContext(): Context = systemContext.applicationContext
    internal fun erasureJournal(): File = File(systemContext.noBackupFilesDir, "pending-profile-erasure.json")
    fun context(base: Context, profile: LocalProfile = selected): Context {
        if (base is ProfileContext && base.profile == profile) return base
        check(::systemContext.isInitialized) { "Profile storage must initialize before personal repositories" }
        return ProfileContext(systemContext, profile)
    }
    internal fun select(uid: String?, newGuest: Boolean = false): LocalProfile = synchronized(lock) {
        require(uid == null || (uid.isNotBlank() && uid.length <= 128))
        val next = LocalProfile(uid, if (newGuest) UUID.randomUUID().toString() else selected.guestId, selected.epoch + 1)
        val output = registry.startWrite()
        try { output.write(json.encodeToString(LocalProfile.serializer(), next).toByteArray()); registry.finishWrite(output) }
        catch (error: Throwable) { registry.failWrite(output); throw error }
        selected = next
        _recoveryNeeded.value = false
        next
    }
    internal fun isPlatformPreference(name: String): Boolean = name == AccountDeletionNotices.PREFERENCES || listOf("com.google.", "com.android.vending.", "google.", "firebase", "Firebase", "androidx.")
        .any(name::startsWith)
    internal fun isPersonalDatabase(name: String): Boolean = name in setOf("kpkn.db", "sync-outbox.db", "health-connect.db") ||
        name.matches(Regex("restore_[a-f0-9-]+\\.db"))
}

/** Captured contexts keep a delayed operation bound to its ORIGINAL profile. */
class ProfileContext internal constructor(base: Context, val profile: LocalProfile) : ContextWrapper(base) {
    private val legacy = profile.key == LocalProfile.LEGACY_GUEST
    private val prefix = "kpkn_profile_${profile.key}__"
    override fun getApplicationContext(): Context = this
    // On Android 8 Context's default callback methods call getApplicationContext().
    // Returning this for captured storage would recurse forever without delegation.
    override fun registerComponentCallbacks(callback: android.content.ComponentCallbacks) =
        baseContext.registerComponentCallbacks(callback)
    override fun unregisterComponentCallbacks(callback: android.content.ComponentCallbacks) =
        baseContext.unregisterComponentCallbacks(callback)
    override fun createConfigurationContext(overrideConfiguration: Configuration): Context =
        ProfileContext(baseContext.createConfigurationContext(overrideConfiguration), profile)
    override fun getFilesDir(): File = if (legacy) baseContext.filesDir else directory(File(baseContext.filesDir, "profiles/${profile.key}/files"))
    override fun getCacheDir(): File = directory(File(baseContext.cacheDir, "profiles/${profile.key}"))
    override fun getNoBackupFilesDir(): File = directory(File(baseContext.noBackupFilesDir, "profiles/${profile.key}"))
    override fun getDatabasePath(name: String): File = if (!ProfileStorage.isPersonalDatabase(name) || legacy) baseContext.getDatabasePath(name)
        else File(directory(File(baseContext.getDatabasePath("kpkn.db").parentFile, "profiles/${profile.key}")), name)
    override fun openOrCreateDatabase(name: String, mode: Int, factory: SQLiteDatabase.CursorFactory?): SQLiteDatabase =
        openOrCreateDatabase(name, mode, factory, null)
    override fun openOrCreateDatabase(name: String, mode: Int, factory: SQLiteDatabase.CursorFactory?, handler: DatabaseErrorHandler?): SQLiteDatabase {
        if (!ProfileStorage.isPersonalDatabase(name)) return baseContext.openOrCreateDatabase(name, mode, factory, handler)
        val flags = SQLiteDatabase.CREATE_IF_NECESSARY or
            (if (mode and Context.MODE_ENABLE_WRITE_AHEAD_LOGGING != 0) SQLiteDatabase.ENABLE_WRITE_AHEAD_LOGGING else 0)
        return SQLiteDatabase.openDatabase(getDatabasePath(name).absolutePath, factory, flags, handler)
    }
    override fun deleteDatabase(name: String): Boolean = if (ProfileStorage.isPersonalDatabase(name)) SQLiteDatabase.deleteDatabase(getDatabasePath(name))
        else baseContext.deleteDatabase(name)
    override fun getSharedPreferences(name: String, mode: Int): SharedPreferences {
        if (ProfileStorage.isPlatformPreference(name)) return baseContext.getSharedPreferences(name, mode)
        require(name.matches(Regex("[A-Za-z0-9_.-]{1,160}")) && !name.startsWith("kpkn_profile_"))
        openedPreferences.computeIfAbsent(profile.key) { java.util.concurrent.ConcurrentHashMap.newKeySet() }.add(name)
        return baseContext.getSharedPreferences(if (legacy) name else prefix + name, mode)
    }
    fun clearPersonalPreferences() {
        personalPreferenceNames().forEach { name -> check(getSharedPreferences(name, MODE_PRIVATE).edit().clear().commit()) }
    }
    fun personalPreferenceNames(): Set<String> = File(baseContext.applicationInfo.dataDir, "shared_prefs")
        .listFiles { _, name -> name.endsWith(".xml") }.orEmpty().map { it.name.removeSuffix(".xml") }
        .filter { if (legacy) !it.startsWith("kpkn_profile_") && !ProfileStorage.isPlatformPreference(it) else it.startsWith(prefix) }
        .map { if (legacy) it else it.removePrefix(prefix) }.toSet() + openedPreferences[profile.key].orEmpty()
    companion object {
        private val openedPreferences = java.util.concurrent.ConcurrentHashMap<String, MutableSet<String>>()
    }
    private fun directory(path: File): File = path.also {
        // Another reader/SDK can create the same captured directory between these
        // checks. mkdirs() returns false when it already exists; that is not failure.
        check(it.isDirectory || it.mkdirs() || it.isDirectory) { "Unable to open profile storage" }
    }
}
