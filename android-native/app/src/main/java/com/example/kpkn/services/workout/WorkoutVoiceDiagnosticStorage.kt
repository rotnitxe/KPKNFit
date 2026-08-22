package com.example.kpkn.services.workout

import android.content.Context
import android.net.Uri
import com.example.kpkn.services.diagnostics.KpknDiagnosticStorage
import java.io.File

/** Compatibility adapter over the single diagnostics SAF configuration. */
object WorkoutVoiceDiagnosticStorage {
    fun configure(context: Context, treeUri: Uri): Result<String> =
        KpknDiagnosticStorage.configure(context.applicationContext, treeUri)

    fun clear(context: Context) = KpknDiagnosticStorage.clear(context.applicationContext)

    fun isConfigured(context: Context): Boolean = KpknDiagnosticStorage.isConfigured(context.applicationContext)

    fun configuredLabel(context: Context): String? = KpknDiagnosticStorage.configuredLabel(context.applicationContext)

    fun createJsonl(context: Context, displayName: String): Uri? =
        KpknDiagnosticStorage.createJsonl(context.applicationContext, "voice", displayName)

    /** Legacy call site retained; the central recovery walker owns the migration. */
    fun mirrorRecoveryFiles(context: Context, @Suppress("UNUSED_PARAMETER") files: List<File>) {
        KpknDiagnosticStorage.mirrorRecoveryFiles(context.applicationContext)
    }

    internal fun sanitizeDisplayName(name: String): String {
        val safe = name.replace(Regex("""[\\/:*?"<>|]"""), "_").trim()
        return safe.ifBlank { "kpkn-voice-diagnostic.jsonl" }.take(120)
    }
}
