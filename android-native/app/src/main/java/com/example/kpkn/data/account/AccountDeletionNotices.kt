package com.example.kpkn.data.account

import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.Json

/** One device notice survives Activity/process recreation. It contains no account or health identity. */
object AccountDeletionNotices {
    const val PREFERENCES = "kpkn_device_notices"
    @Serializable data class Notice(val remotePending: Boolean, val localPending: Boolean, val createdAtEpochMs: Long)
    private val _notice = MutableStateFlow<Notice?>(null)
    val notice = _notice.asStateFlow()
    private fun preferences() = ProfileStorage.platformContext().getSharedPreferences(PREFERENCES, 0)
    suspend fun load() = withContext(Dispatchers.IO) {
        val stored = preferences().getString("account_deletion", null)
        val value = stored?.takeIf { it.length <= 512 }?.let { runCatching { Json.decodeFromString(Notice.serializer(), it) }.getOrNull() }
            ?.takeIf { System.currentTimeMillis() - it.createdAtEpochMs in 0..7L * 24 * 60 * 60 * 1000 }
        withContext(Dispatchers.Main) { _notice.value = value }
    }
    suspend fun save(remotePending: Boolean, localPending: Boolean) = withContext(Dispatchers.IO) {
        val value = if (remotePending || localPending) Notice(remotePending, localPending, System.currentTimeMillis()) else null
        check(preferences().edit().let { editor ->
            if (value == null) editor.remove("account_deletion") else editor.putString("account_deletion", Json.encodeToString(Notice.serializer(), value))
        }.commit())
        withContext(Dispatchers.Main) { _notice.value = value }
    }
    suspend fun dismiss() = withContext(Dispatchers.IO) {
        check(preferences().edit().remove("account_deletion").commit())
        withContext(Dispatchers.Main) { _notice.value = null }
    }
    suspend fun clearLocalPending() {
        load()
        val value = _notice.value ?: return
        save(value.remotePending, localPending = false)
    }
}
