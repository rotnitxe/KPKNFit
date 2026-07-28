package com.example.kpkn.services.workout

import android.content.Context
import android.content.res.AssetManager
import java.io.File
import java.security.MessageDigest
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
    private val modelMutex = Mutex()
    private var cachedModel: Model? = null

    suspend fun prepare(context: Context): Model = modelMutex.withLock {
        cachedModel?.let { return it }
        withContext(Dispatchers.IO) {
            val targetRoot = File(context.noBackupFilesDir, "voice-models")
            val targetDir = File(targetRoot, VERSION)
            val assetHash = assetDigest(context.assets, ASSET_DIR)
            val assetUuid = readAssetText(
                context.assets,
                "$ASSET_DIR/$UUID_FILE",
            ).ifBlank { assetHash }
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
            Model(targetDir.absolutePath).also { cachedModel = it }
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
        val stagingDir = File(targetRoot, "$VERSION.tmp")
        val previousDir = File(targetRoot, "$VERSION.previous")
        stagingDir.deleteRecursively()
        previousDir.deleteRecursively()
        check(stagingDir.mkdirs() || stagingDir.exists()) {
            "No se pudo crear staging para el modelo Vosk"
        }
        copyAssetTree(assetManager, ASSET_DIR, stagingDir)
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

    private fun assetDigest(assetManager: AssetManager, root: String): String {
        val digest = MessageDigest.getInstance("SHA-256")
        updateDigest(assetManager, root, digest)
        return digest.digest().joinToString("") { "%02x".format(it) }
    }

    private fun updateDigest(
        assetManager: AssetManager,
        assetPath: String,
        digest: MessageDigest,
    ) {
        val children = assetManager.list(assetPath).orEmpty().sorted()
        if (children.isEmpty()) {
            assetManager.open(assetPath).use { input ->
                val buffer = ByteArray(DEFAULT_BUFFER_SIZE)
                while (true) {
                    val read = input.read(buffer)
                    if (read <= 0) break
                    digest.update(buffer, 0, read)
                }
            }
            return
        }
        children.forEach { child ->
            digest.update("$assetPath/$child".toByteArray())
            updateDigest(assetManager, "$assetPath/$child", digest)
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
