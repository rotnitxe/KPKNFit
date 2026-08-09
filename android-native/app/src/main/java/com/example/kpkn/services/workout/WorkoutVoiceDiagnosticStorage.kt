package com.example.kpkn.services.workout

import android.content.Context
import android.net.Uri
import android.util.Log
import com.example.kpkn.services.diagnostics.KpknDiagnosticStorage
import java.io.File
import java.util.concurrent.LinkedBlockingQueue
import java.util.concurrent.ThreadPoolExecutor
import java.util.concurrent.TimeUnit

/** Compatibility adapter over the single diagnostics SAF configuration. */
object WorkoutVoiceDiagnosticStorage {
    private const val TAG = "VoiceDiagnosticStore"
    private val writer = ThreadPoolExecutor(
        1,
        1,
        30L,
        TimeUnit.SECONDS,
        LinkedBlockingQueue(),
        { runnable -> Thread(runnable, "kpkn-voice-jsonl-mirror").apply { isDaemon = true } },
        ThreadPoolExecutor.CallerRunsPolicy(),
    )

    fun configure(context: Context, treeUri: Uri): Result<String> =
        KpknDiagnosticStorage.configure(context.applicationContext, treeUri)

    fun clear(context: Context) = KpknDiagnosticStorage.clear(context.applicationContext)

    fun isConfigured(context: Context): Boolean = KpknDiagnosticStorage.isConfigured(context.applicationContext)

    fun configuredLabel(context: Context): String? = KpknDiagnosticStorage.configuredLabel(context.applicationContext)

    fun createJsonl(context: Context, displayName: String): Uri? =
        KpknDiagnosticStorage.createJsonl(context.applicationContext, "voice", displayName)

    fun appendLine(context: Context, documentUri: Uri, line: String) {
        val appContext = context.applicationContext
        writer.execute {
            runCatching {
                appContext.contentResolver.openOutputStream(documentUri, "wa")?.use { output ->
                    output.write(line.toByteArray(Charsets.UTF_8))
                    output.write('\n'.code)
                    output.flush()
                } ?: error("El proveedor no permitió abrir el JSONL")
            }.onFailure { error -> Log.e(TAG, "Unable to mirror voice diagnostic line", error) }
        }
    }

    /** Legacy call site retained; the central recovery walker owns the migration. */
    fun mirrorRecoveryFiles(context: Context, @Suppress("UNUSED_PARAMETER") files: List<File>) {
        KpknDiagnosticStorage.mirrorRecoveryFiles(context.applicationContext)
    }

    internal fun sanitizeDisplayName(name: String): String {
        val safe = name.replace(Regex("""[\\/:*?"<>|]"""), "_").trim()
        return safe.ifBlank { "kpkn-voice-diagnostic.jsonl" }.take(120)
    }
}
