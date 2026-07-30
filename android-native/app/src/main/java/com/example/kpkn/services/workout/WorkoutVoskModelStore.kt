package com.example.kpkn.services.workout

import android.content.Context
import android.content.res.AssetManager
import java.io.File
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withContext
import org.vosk.Model

/**
 * Extrae el modelo Vosk de forma versionada a storage privado.
 *
 * Preparación y cierre comparten el mismo mutex para impedir que una sesión vieja
 * cierre el Model mientras una sesión nueva termina de prepararlo.
 */
object WorkoutVoskModelStore {
    private const val ASSET_DIR = "vosk/vosk-model-small-es-0.42"
    private const val VERSION = "vosk-model-small-es-0.42"
    private const val UUID_FILE = "uuid"
    private const val MIN_FREE_BYTES = 180L * 1024L * 1024L
    private val modelMutex = Mutex()
    private var cachedModel: Model? = null

    suspend fun prepare(context: Context): Model = modelMutex.withLock {
        cachedModel?.let { return it }
        withContext(Dispatchers.IO) {
            WorkoutVoiceDiagnosticLogger.event("voice_phase", mapOf("phase" to "MODEL_INSTALL", "state" to "CHECK"))
            val targetRoot = File(context.noBackupFilesDir, "voice-models")
            val targetDir = File(targetRoot, VERSION)
            val assetUuid = readAssetText(
                context.assets,
                "$ASSET_DIR/$UUID_FILE",
            ).ifBlank { VERSION }
            val diskUuid = File(targetDir, UUID_FILE)
                .takeIf(File::exists)
                ?.readText()
                ?.trim()
            if (!targetDir.exists() || diskUuid != assetUuid) {
                installAssetsAtomically(
                    assetManager = context.assets,
                    targetRoot = targetRoot,
                    targetDir = targetDir,
                    assetUuid = assetUuid,
                )
            }
            WorkoutVoiceDiagnosticLogger.event("voice_phase", mapOf("phase" to "MODEL_LOAD", "state" to "START"))
            Model(targetDir.absolutePath).also { model ->
                cachedModel = model
                WorkoutVoiceDiagnosticLogger.event("voice_phase", mapOf("phase" to "MODEL_LOAD", "state" to "READY"))
            }
        }
    }

    suspend fun close() = modelMutex.withLock {
        withContext(Dispatchers.IO) {
            runCatching { cachedModel?.close() }
            cachedModel = null
        }
    }

    private fun installAssetsAtomically(
        assetManager: AssetManager,
        targetRoot: File,
        targetDir: File,
        assetUuid: String,
    ) {
        targetRoot.mkdirs()
        check(targetRoot.usableSpace >= MIN_FREE_BYTES) {
            "Espacio insuficiente para instalar el modelo Vosk"
        }
        val stagingDir = File(targetRoot, "$VERSION.tmp")
        val previousDir = File(targetRoot, "$VERSION.previous")
        stagingDir.deleteRecursively()
        previousDir.deleteRecursively()
        check(stagingDir.mkdirs() || stagingDir.exists()) {
            "No se pudo crear staging para el modelo Vosk"
        }
        WorkoutVoiceDiagnosticLogger.event("voice_phase", mapOf("phase" to "MODEL_INSTALL", "state" to "COPY"))
        copyAssetTree(assetManager, ASSET_DIR, stagingDir)
        validateInstalledTree(stagingDir)
        File(stagingDir, UUID_FILE).writeText(assetUuid)

        if (targetDir.exists() && !targetDir.renameTo(previousDir)) {
            targetDir.copyRecursively(previousDir, overwrite = true)
            targetDir.deleteRecursively()
        }
        val installed = stagingDir.renameTo(targetDir) || runCatching {
            stagingDir.copyRecursively(targetDir, overwrite = true)
            stagingDir.deleteRecursively()
            true
        }.getOrDefault(false)
        if (!installed) {
            targetDir.deleteRecursively()
            if (previousDir.exists()) previousDir.renameTo(targetDir)
            error("No se pudo instalar el modelo Vosk")
        }
        previousDir.deleteRecursively()
    }

    private fun validateInstalledTree(root: File) {
        val required = listOf(
            "am/final.mdl",
            "conf/model.conf",
            "graph/HCLr.fst",
        )
        check(required.all { relative -> File(root, relative).let { it.isFile && it.length() > 0L } }) {
            "La copia del modelo Vosk quedó incompleta"
        }
    }

    private fun readAssetText(assetManager: AssetManager, path: String): String =
        runCatching {
            assetManager.open(path).bufferedReader().use { it.readText().trim() }
        }.getOrDefault("")

    private fun copyAssetTree(
        assetManager: AssetManager,
        assetPath: String,
        outputRoot: File,
    ) {
        val children = assetManager.list(assetPath).orEmpty()
        if (children.isEmpty()) {
            val outputFile = File(outputRoot, assetPath.removePrefix("$ASSET_DIR/"))
            outputFile.parentFile?.mkdirs()
            assetManager.open(assetPath).use { input ->
                outputFile.outputStream().use(input::copyTo)
            }
            return
        }
        val currentDir = if (assetPath == ASSET_DIR) {
            outputRoot
        } else {
            File(outputRoot, assetPath.removePrefix("$ASSET_DIR/"))
        }
        currentDir.mkdirs()
        children.forEach { child ->
            copyAssetTree(assetManager, "$assetPath/$child", outputRoot)
        }
    }
}
