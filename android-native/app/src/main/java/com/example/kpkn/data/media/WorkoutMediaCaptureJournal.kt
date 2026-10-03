package com.example.kpkn.data.media

import android.os.Build
import android.system.Os
import android.annotation.TargetApi
import com.example.kpkn.data.models.WorkoutMedia
import kotlinx.serialization.Serializable
import kotlinx.serialization.decodeFromString
import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.Json
import java.io.File
import java.io.FileNotFoundException
import java.io.FileOutputStream
import java.io.IOException
import java.nio.file.Files
import java.nio.file.StandardCopyOption
import java.security.MessageDigest
import java.util.UUID

/** Durable capture metadata written before CameraX starts or a URI is copied. */
@Serializable
data class WorkoutMediaCaptureJournalEntry(
    val media: WorkoutMedia,
    val sourceFilePath: String,
    val sourceUri: String? = null,
    val deleteSourceAfterCommit: Boolean = false,
    val readyToIngest: Boolean = false,
    val discardedByRetention: Boolean = false,
    /** MIME-resolved extension for URI captures; null on older journals and camera captures. */
    val resolvedExtension: String? = null,
)

data class WorkoutMediaCaptureRecoveryResult(
    val failedSessionKeys: Set<String> = emptySet(),
    val unreadableJournal: Boolean = false,
)

/**
 * Private, atomic journal for media which has not yet committed to Room.
 *
 * [readEntryText] is the seam through which [readAll] reads each marker file; the
 * default is a plain UTF-8 read. It exists so tests can simulate a marker being
 * removed (by the ingest cleanup) between the directory listing and the read.
 */
class WorkoutMediaCaptureJournal(
    filesDir: File,
    private val readEntryText: (File) -> String = { it.readText(Charsets.UTF_8) },
) {
    private val root = File(filesDir, ROOT_DIR)
    private val journalDir = File(root, JOURNAL_DIR)
    private val sourceDir = File(root, SOURCE_DIR)
    private val json = Json {
        encodeDefaults = true
        ignoreUnknownKeys = true
    }

    fun pendingSourceFile(id: String, extension: String): File {
        val safeExtension = extension.lowercase().filter { it.isLetterOrDigit() }.take(8)
            .ifBlank { "bin" }
        if (!sourceDir.isDirectory && !sourceDir.mkdirs() && !sourceDir.isDirectory) {
            throw IOException("No se pudo preparar el almacenamiento privado de la captura.")
        }
        return File(sourceDir, "${fileToken(id)}.$safeExtension")
    }

    fun ownsSource(file: File): Boolean =
        runCatching { file.canonicalFile.parentFile == sourceDir.canonicalFile }.getOrDefault(false)

    fun writeCapturing(entry: WorkoutMediaCaptureJournalEntry) {
        require(entry.media.id.isNotBlank()) { "Workout media id must not be blank" }
        writeIfAbsent(entry.copy(readyToIngest = false), CAPTURING_SUFFIX)
    }

    fun writeReady(entry: WorkoutMediaCaptureJournalEntry) {
        require(entry.media.id.isNotBlank()) { "Workout media id must not be blank" }
        if (entry.discardedByRetention) throw IOException("La captura fue rechazada por la política de retención.")
        writeIfAbsent(entry.copy(readyToIngest = true), READY_SUFFIX)
    }

    fun writeDiscarded(entry: WorkoutMediaCaptureJournalEntry) {
        require(entry.media.id.isNotBlank()) { "Workout media id must not be blank" }
        writeIfAbsent(entry.copy(readyToIngest = false, discardedByRetention = true), DISCARDED_SUFFIX)
    }

    fun read(id: String): WorkoutMediaCaptureJournalEntry? =
        readAll().firstOrNull { it.media.id == id }

    fun readAll(): List<WorkoutMediaCaptureJournalEntry> {
        val files = journalDir.listFiles()
            ?.filter {
                it.isFile && (
                    it.name.endsWith(CAPTURING_SUFFIX) ||
                        it.name.endsWith(READY_SUFFIX) ||
                        it.name.endsWith(DISCARDED_SUFFIX)
                    )
            }
            .orEmpty()
        val entries = files.mapNotNull { file ->
            val raw = try {
                readEntryText(file)
            } catch (error: FileNotFoundException) {
                // The ingest cleanup may legitimately remove a marker between the
                // directory listing and this read. Skip only a file that is really
                // gone; one that still exists but cannot be opened keeps failing.
                if (!file.exists()) return@mapNotNull null
                throw IOException(PENDING_READ_FAILURE, error)
            } catch (error: Exception) {
                throw IOException(PENDING_READ_FAILURE, error)
            }
            try {
                json.decodeFromString<WorkoutMediaCaptureJournalEntry>(raw)
            } catch (error: Exception) {
                throw IOException(PENDING_READ_FAILURE, error)
            }
        }
        return entries.groupBy { it.media.id }.values.map { copies ->
            val first = copies.first()
            if (copies.any { !hasSameCaptureIdentity(first, it) }) {
                throw IOException("La captura pendiente tiene registros incompatibles.")
            }
            copies.firstOrNull { it.discardedByRetention }
                ?: copies.firstOrNull { it.readyToIngest }
                ?: first
        }.sortedBy { it.media.createdAtMs }
    }

    fun remove(id: String) {
        val token = fileToken(id)
        listOf(
            File(journalDir, "$token$CAPTURING_SUFFIX"),
            File(journalDir, "$token$READY_SUFFIX"),
            File(journalDir, "$token$DISCARDED_SUFFIX"),
        ).forEach { file ->
            if (file.exists() && !file.delete()) throw IOException("No se pudo cerrar la captura pendiente.")
        }
    }

    fun deleteOwnedSource(entry: WorkoutMediaCaptureJournalEntry) {
        if (!entry.deleteSourceAfterCommit) return
        val source = File(entry.sourceFilePath).canonicalFile
        val allowedRoot = sourceDir.canonicalFile
        if (source.parentFile != allowedRoot) {
            throw IOException("La ruta temporal de la captura no pertenece al directorio privado.")
        }
        if (source.exists() && !source.delete()) {
            throw IOException("No se pudo limpiar el archivo temporal de la captura.")
        }
    }

    private fun writeIfAbsent(entry: WorkoutMediaCaptureJournalEntry, suffix: String) {
        synchronized(JOURNAL_WRITE_LOCK) {
            if (!journalDir.exists() && !journalDir.mkdirs()) {
                throw IOException("No se pudo preparar el registro de capturas pendientes.")
            }
            val token = fileToken(entry.media.id)
            val target = File(journalDir, "$token$suffix")
            val discarded = File(journalDir, "$token$DISCARDED_SUFFIX")
            if (suffix != DISCARDED_SUFFIX && discarded.exists()) {
                validateExisting(discarded, entry)
                throw IOException("La captura fue rechazada por la política de retención.")
            }
            if (target.exists()) {
                validateExisting(target, entry)
                cleanupTemporaryFiles(token)
                return
            }
            val siblingSuffix = if (suffix == READY_SUFFIX) CAPTURING_SUFFIX else READY_SUFFIX
            val sibling = File(journalDir, "$token$siblingSuffix")
            if (sibling.exists()) validateExisting(sibling, entry)

            val temporary = File(journalDir, "$token.${UUID.randomUUID()}.tmp")
            try {
                FileOutputStream(temporary).use { output ->
                    output.write(json.encodeToString(entry).toByteArray(Charsets.UTF_8))
                    output.fd.sync()
                }
            } catch (error: Exception) {
                temporary.delete()
                throw IOException("No se pudo guardar la captura pendiente.", error)
            }

            // Keep the previous durable marker until the complete sibling file is
            // atomically installed. Robolectric leaves Os.rename unshadowed, where
            // its native call can return without moving either file.
            var renameError: Exception? = try {
                Os.rename(temporary.absolutePath, target.absolutePath)
                null
            } catch (error: Exception) {
                error
            }
            if (target.isFile && !temporary.exists()) {
                validateExisting(target, entry)
                cleanupTemporaryFiles(token)
                return
            }
            if (target.isFile) {
                validateExisting(target, entry)
                temporary.delete()
                cleanupTemporaryFiles(token)
                return
            }

            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
                try {
                    AtomicJournalFileMove.replace(temporary, target)
                } catch (error: Exception) {
                    if (target.isFile) {
                        validateExisting(target, entry)
                        temporary.delete()
                        cleanupTemporaryFiles(token)
                        return
                    }
                    if (renameError == null) renameError = error else renameError?.addSuppressed(error)
                }
                if (target.isFile && !temporary.exists()) {
                    validateExisting(target, entry)
                    cleanupTemporaryFiles(token)
                    return
                }
            }

            // Do not delete the complete temp file or replace the old marker when
            // atomic installation is unavailable; a later retry can finish it.
            throw IOException("No se pudo completar el guardado de la captura pendiente.", renameError)
        }
    }

    private fun validateExisting(target: File, entry: WorkoutMediaCaptureJournalEntry) {
        val existing = try {
            json.decodeFromString<WorkoutMediaCaptureJournalEntry>(target.readText(Charsets.UTF_8))
        } catch (error: Exception) {
            throw IOException("No se pudo validar una captura pendiente.", error)
        }
        if (
            !hasSameCaptureIdentity(existing, entry)
        ) {
            throw IOException("La captura pendiente ya existe con otros metadatos.")
        }
    }

    /** Remove orphaned atomic-write siblings only after a valid marker exists. */
    private fun cleanupTemporaryFiles(token: String) {
        journalDir.listFiles()
            ?.filter { it.isFile && it.name.startsWith("$token.") && it.name.endsWith(".tmp") }
            .orEmpty()
            .forEach { temporary ->
                runCatching { temporary.delete() }
            }
    }

    private fun hasSameCaptureIdentity(
        first: WorkoutMediaCaptureJournalEntry,
        second: WorkoutMediaCaptureJournalEntry,
    ): Boolean {
        // A URI's kind and extension are provisional until ContentResolver.getType
        // succeeds. Allow only those fields to change from capturing -> ready;
        // every frozen session/set field and private source path stays exact.
        val sameMedia = if (first.sourceUri != null && second.sourceUri != null) {
            first.media.copy(kind = com.example.kpkn.data.models.WorkoutMediaKind.PHOTO) ==
                second.media.copy(kind = com.example.kpkn.data.models.WorkoutMediaKind.PHOTO)
        } else {
            first.media == second.media
        }
        val sameResolvedExtension = if (first.sourceUri != null && second.sourceUri != null) {
            first.resolvedExtension == null || second.resolvedExtension == null ||
                first.resolvedExtension == second.resolvedExtension
        } else {
            first.resolvedExtension == second.resolvedExtension
        }
        return sameMedia &&
            sameResolvedExtension &&
            first.sourceFilePath == second.sourceFilePath &&
            first.sourceUri == second.sourceUri &&
            first.deleteSourceAfterCommit == second.deleteSourceAfterCommit
    }

    private fun fileToken(id: String): String = MessageDigest.getInstance("SHA-256")
        .digest(id.toByteArray(Charsets.UTF_8))
        .joinToString("") { byte -> "%02x".format(byte) }

    companion object {
        const val ROOT_DIR = "workout_media_pending"
        private const val JOURNAL_DIR = "journal"
        private const val SOURCE_DIR = "sources"
        private const val CAPTURING_SUFFIX = ".capturing.json"
        private const val READY_SUFFIX = ".ready.json"
        private const val DISCARDED_SUFFIX = ".discarded.json"
        private const val PENDING_READ_FAILURE = "No se pudo leer una captura pendiente."
        private val JOURNAL_WRITE_LOCK = Any()
    }
}

@TargetApi(Build.VERSION_CODES.O)
private object AtomicJournalFileMove {
    fun replace(source: File, target: File) {
        Files.move(
            source.toPath(),
            target.toPath(),
            StandardCopyOption.ATOMIC_MOVE,
            StandardCopyOption.REPLACE_EXISTING,
        )
    }
}
