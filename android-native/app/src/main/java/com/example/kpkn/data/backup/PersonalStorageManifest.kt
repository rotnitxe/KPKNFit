package com.example.kpkn.data.backup

import android.content.Context
import java.io.File
import com.example.kpkn.data.account.ProfileContext

/** Every listed path is relative to this application's sandbox, never a user SAF tree. */
object PersonalStorageManifest {
    val personalFileRoots = setOf("profile", "workout_media", "workout_media_pending", "workout_photos",
        "exercise_user_media", "competition_media", "cardio-gps", "snapshots", "kpkn_logs",
        "kpkn_diagnostics", "voice_diagnostics", "nutrition_telemetry", "legacy-body-recovery.json")
    val personalDatabases = setOf("health-connect.db", "sync-outbox.db")
    val regenerableRoots = setOf("voice-models")

    fun clearFilesAndPreferences(context: Context, includeOwnedSources: Boolean = true) {
        val app = context.applicationContext
        if (includeOwnedSources && app is ProfileContext) com.example.kpkn.data.sync.GuestSourceLedger.eraseOwnedSources(app)
        val root = app.filesDir.canonicalFile
        personalFileRoots.forEach { name ->
            val target = File(root, name).canonicalFile
            check(target.toPath().startsWith(root.toPath()) && target != root)
            check(!target.exists() || target.deleteRecursively()) { "Unable to remove personal files" }
        }
        app.cacheDir.listFiles()?.forEach { check(it.deleteRecursively()) { "Unable to remove temporary files" } }
        app.noBackupFilesDir.listFiles()?.forEach { check(it.deleteRecursively()) { "Unable to remove pending profile work" } }
        // A local erase never clears another profile or the platform SDK's global authentication store.
        if (app is ProfileContext) app.clearPersonalPreferences()
        else if (com.example.kpkn.data.account.ProfileStorage.initialized())
            (com.example.kpkn.data.account.ProfileStorage.context(app) as ProfileContext).clearPersonalPreferences()
        personalDatabases.forEach { name ->
            if (app.getDatabasePath(name).exists()) check(app.deleteDatabase(name)) { "Unable to remove personal database" }
        }
    }
}
