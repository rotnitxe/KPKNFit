package com.example.kpkn.data.importer

import android.util.AtomicFile
import android.os.Build
import com.example.kpkn.data.models.Program
import com.example.kpkn.domain.training.importer.ImportedProgramSourceMap
import com.example.kpkn.domain.training.importer.ImportedProgramDraft
import com.example.kpkn.domain.training.importer.ImportSourceEvidence
import com.example.kpkn.domain.training.importer.ImportWizardPage
import com.example.kpkn.domain.training.importer.ImportWizardSheet
import java.io.File
import java.io.FileOutputStream
import java.io.IOException
import java.util.Locale
import java.util.UUID
import java.util.concurrent.ConcurrentHashMap
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withContext
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.Json

/** Draft del importador. `program` solo se conserva en documentos antiguos que ya usaban el editor de sesiones. */
@Serializable
internal data class ImportedEditorDocument(
    val id: String,
    val revision: Long,
    val source: ImportedProgramDraft,
    val resolutions: Map<String, String?>,
    val title: String,
    val weekdays: Set<Int>,
    val program: Program? = null,
    val sourceMap: ImportedProgramSourceMap = ImportedProgramSourceMap(),
    val editingStarted: Boolean = false,
    /** Stable id of the library commit, so a restore after process death can complete cleanup idempotently. */
    val savedProgramId: String? = null,
    val wizardPage: ImportWizardPage = ImportWizardPage.EXERCISES,
    val wizardSheet: ImportWizardSheet? = null,
    val sourceEvidence: ImportSourceEvidence = ImportSourceEvidence(),
    /** Canonical session ids the user opened and saved through the shared session editor. */
    val reviewedSessionIds: Set<String> = emptySet(),
    val schemaVersion: Int = 3,
)

/** Archivo privado y atómico para la ruta de Programas; el alta usa su persistencia propia. */
internal class ImportedEditorDraftPersistence(
    file: File,
    private val atomicFile: AtomicFile = AtomicFile(file),
    private val sourceMediaStore: ImportSourceMediaStore? = null,
) {
    private val mutex = locks.computeIfAbsent(file.absoluteFile.canonicalPath.replace('\\', '/').lowercase(Locale.ROOT)) { Mutex() }
    private val json = Json { ignoreUnknownKeys = true; encodeDefaults = true }

    companion object {
        private val locks = ConcurrentHashMap<String, Mutex>()
    }

    private fun decodeDocument(bytes: ByteArray): ImportedEditorDocument =
        json.decodeFromString(ImportedEditorDocument.serializer(), String(bytes, Charsets.UTF_8)).also { decoded ->
            check(decoded.schemaVersion in 1..3) { "Versión de borrador no compatible" }
            check(decoded.id.isNotBlank() && decoded.revision >= 0) { "Borrador inválido" }
            check(!decoded.editingStarted || decoded.program?.id?.isNotBlank() == true) { "Borrador editado inválido" }
        }.copy(schemaVersion = 3)

    private fun readLocked(): ImportedEditorDocument? {
        val base = atomicFile.baseFile
        check(!(File(base.path + ".rollback").exists() && File(base.path + ".rollback-empty").exists())) {
            "Hay respaldos incompatibles de borrador; se preservaron para recuperación"
        }
        restoreInterruptedWriteIfPresent(base)
        restoreInterruptedFirstWriteIfPresent(base)
        quarantinePendingWriteIfPresent(base)
        if (!base.exists() && !File(base.path + ".bak").exists()) return null
        return decodeDocument(atomicFile.openRead().use { it.readBytes() })
    }

    suspend fun read(): Result<ImportedEditorDocument?> = withContext(Dispatchers.IO) {
        mutex.withLock {
            runCatching { readLocked() }.onSuccess { document ->
                runCatching { sourceMediaStore?.retainOnly(document?.sourceEvidence?.sourceMediaId) }
            }
        }
    }

    private fun writeLocked(document: ImportedEditorDocument) {
        val bytes = json.encodeToString(ImportedEditorDocument.serializer(), document).toByteArray(Charsets.UTF_8)
        val base = atomicFile.baseFile
        base.parentFile?.mkdirs()
        val hadPreviousDraft = base.exists()
        val rollback = snapshotPreviousDraft(base)
        val emptyRollback = if (hadPreviousDraft) null else markAbsentDraft(base)
        var output: FileOutputStream? = null
        var finishing = false
        try {
            // Android 24/25 writes directly to the base file and may only log a failed backup rename.
            // A separate verified rollback copy stays in place until the new bytes are verified.
            output = atomicFile.startWrite()
            output.write(bytes)
            output.flush()
            output.fd.sync()
            finishing = true
            atomicFile.finishWrite(output)
        } catch (error: Throwable) {
            if (!finishing && output != null) {
                try {
                    atomicFile.failWrite(output)
                } catch (cleanup: Throwable) {
                    error.addSuppressed(cleanup)
                }
            } else if (output != null) {
                try {
                    output.close()
                } catch (cleanup: Throwable) {
                    error.addSuppressed(cleanup)
                }
            }
            try {
                restoreInterruptedWriteIfPresent(base)
                restoreInterruptedFirstWriteIfPresent(base)
                quarantineFirstWriteBaseIfNeeded(base, hadPreviousDraft)
                quarantinePendingWriteIfPresent(base)
            } catch (cleanup: Throwable) {
                error.addSuppressed(cleanup)
            }
            throw error
        }

        // AtomicFile does not report a failed rename on some platform versions (including the
        // Windows/Robolectric host). A remaining .new is still uncommitted; move it with a
        // checked replacement while the previous revision remains recoverable.
        try {
            val pending = File(base.path + ".new")
            if (pending.exists()) replaceFileSafely(pending, base)

            check(base.isFile && base.readBytes().contentEquals(bytes)) {
                "No se pudo verificar el borrador guardado"
            }
            val backup = File(base.path + ".bak")
            if (backup.exists()) {
                val previous = rollback?.readBytes()
                    ?: throw IOException("No se pudo verificar el respaldo del borrador")
                check(backup.readBytes().contentEquals(previous)) {
                    "El respaldo del borrador cambió durante la escritura"
                }
                check(backup.delete()) { "No se pudo cerrar el reemplazo del borrador" }
            }
            rollback?.let { check(it.delete()) { "No se pudo cerrar el respaldo recuperable del borrador" } }
            emptyRollback?.let { check(it.delete()) { "No se pudo cerrar el marcador de primera escritura" } }
        } catch (error: Throwable) {
            try {
                restoreInterruptedWriteIfPresent(base)
                restoreInterruptedFirstWriteIfPresent(base)
                quarantineFirstWriteBaseIfNeeded(base, hadPreviousDraft)
                quarantinePendingWriteIfPresent(base)
            } catch (cleanup: Throwable) {
                error.addSuppressed(cleanup)
            }
            throw error
        }
    }

    /** Keeps the last committed bytes until the replacement has been read back and verified. */
    private fun snapshotPreviousDraft(base: File): File? {
        if (!base.exists()) return null
        val rollback = File(base.path + ".rollback")
        check(!rollback.exists()) { "Hay una recuperación de borrador pendiente" }
        val bytes = base.readBytes()
        decodeDocument(bytes)
        val temporary = File(base.path + ".rollback-${UUID.randomUUID()}.tmp")
        try {
            writeSynced(temporary, bytes)
            check(temporary.readBytes().contentEquals(bytes) && base.readBytes().contentEquals(bytes)) {
                "No se pudo verificar el respaldo del borrador"
            }
            if (!temporary.renameTo(rollback)) throw IOException("No se pudo preparar el respaldo recuperable")
            return rollback
        } finally {
            if (temporary.exists()) temporary.delete()
        }
    }

    /** Marks that a first write had no committed revision to restore if Android stops mid-write. */
    private fun markAbsentDraft(base: File): File {
        val marker = File(base.path + ".rollback-empty")
        check(!marker.exists()) { "Hay una recuperación de primera escritura pendiente" }
        FileOutputStream(marker).use { it.fd.sync() }
        return marker
    }

    /** A crash before rollback removal means the new revision was never acknowledged as committed. */
    private fun restoreInterruptedWriteIfPresent(base: File) {
        val rollback = File(base.path + ".rollback")
        if (!rollback.exists()) return
        val bytes = rollback.readBytes()
        decodeDocument(bytes)
        val temporary = File(base.path + ".restore-${UUID.randomUUID()}.tmp")
        writeSynced(temporary, bytes)
        check(temporary.readBytes().contentEquals(bytes)) { "No se pudo verificar la recuperación del borrador" }
        replaceFileSafely(temporary, base)
        check(base.isFile && base.readBytes().contentEquals(bytes)) { "No se pudo recuperar el borrador anterior" }
        val backup = File(base.path + ".bak")
        if (backup.exists()) {
            check(backup.readBytes().contentEquals(bytes)) { "El respaldo atómico no coincide con el borrador recuperable" }
            check(backup.delete()) { "No se pudo retirar el respaldo atómico recuperado" }
        }
        check(rollback.delete()) { "No se pudo cerrar la recuperación del borrador" }
    }

    /** A first-write marker restores the previous state (absence) and preserves interrupted bytes. */
    private fun restoreInterruptedFirstWriteIfPresent(base: File) {
        val marker = File(base.path + ".rollback-empty")
        if (!marker.exists()) return
        check(!File(base.path + ".rollback").exists()) { "Hay respaldos incompatibles de borrador" }
        quarantinePendingWriteIfPresent(base)
        quarantineFirstWriteBaseIfNeeded(base, hadPreviousDraft = false)
        val backup = File(base.path + ".bak")
        if (backup.exists()) {
            val preserved = File(backup.path + ".uncommitted-${UUID.randomUUID()}")
            if (!backup.renameTo(preserved)) throw IOException("No se pudo preservar el respaldo de primera escritura")
        }
        check(marker.delete()) { "No se pudo cerrar la recuperación de primera escritura" }
    }

    /** Keeps an uncommitted .new file out of AtomicFile's next write while retaining its bytes. */
    private fun quarantinePendingWriteIfPresent(base: File) {
        val pending = File(base.path + ".new")
        if (!pending.exists()) return
        val preserved = File(pending.path + ".uncommitted-${UUID.randomUUID()}")
        if (!pending.renameTo(preserved)) throw IOException("No se pudo preservar un reemplazo pendiente")
    }

    /** A failed initial write has no prior revision to restore; keep partial bytes out of the draft path. */
    private fun quarantineFirstWriteBaseIfNeeded(base: File, hadPreviousDraft: Boolean) {
        if (hadPreviousDraft || !base.exists()) return
        val preserved = File(base.path + ".uncommitted-${UUID.randomUUID()}")
        if (!base.renameTo(preserved)) throw IOException("No se pudo preservar la primera escritura fallida")
    }

    private fun writeSynced(file: File, bytes: ByteArray) {
        file.parentFile?.mkdirs()
        FileOutputStream(file).use { output ->
            output.write(bytes)
            output.flush()
            output.fd.sync()
        }
    }

    /** Replaces a file without deleting the old destination first; rollback remains available on failure. */
    private fun replaceFileSafely(source: File, destination: File) {
        try {
            if (Build.VERSION.SDK_INT >= 26) {
                if (replacePendingAtomicallyIfSupported(source, destination)) return
            } else {
                // Android 24/25 has no java.nio.file. Exercise the same rename-based fallback under Robolectric.
                throw IOException("Reemplazo atómico no disponible en Android ${Build.VERSION.SDK_INT}")
            }
        } catch (_: IOException) {
            // On API 24/25 Android, same-directory rename is the platform primitive. On Windows
            // Robolectric, move the destination aside first because renameTo won't replace it.
        }
        val displaced = File(destination.path + ".replace-displaced-${UUID.randomUUID()}")
        val hadDestination = destination.exists()
        if (hadDestination && !destination.renameTo(displaced)) {
            throw IOException("No se pudo preservar el destino anterior")
        }
        if (!source.renameTo(destination)) {
            if (hadDestination && !displaced.renameTo(destination)) {
                throw IOException("No se pudo restaurar el destino anterior")
            }
            throw IOException("No se pudo confirmar el reemplazo del borrador")
        }
        if (hadDestination && displaced.exists() && !displaced.delete()) {
            throw IOException("No se pudo cerrar el reemplazo del borrador")
        }
    }

    /** Avoids linking API 26 classes into the API 24/25 verifier while using ATOMIC_MOVE when it exists. */
    private fun replacePendingAtomicallyIfSupported(pending: File, base: File): Boolean {
        val filesClass = try {
            Class.forName("java.nio.file.Files")
        } catch (_: ClassNotFoundException) {
            return false
        }
        try {
            val pathClass = Class.forName("java.nio.file.Path")
            val copyOptionClass = Class.forName("java.nio.file.CopyOption")
            val standardOptionClass = Class.forName("java.nio.file.StandardCopyOption")
            val options = java.lang.reflect.Array.newInstance(copyOptionClass, 2)
            val constants = requireNotNull(standardOptionClass.enumConstants)
            val atomicMove = constants.first { (it as Enum<*>).name == "ATOMIC_MOVE" }
            val replaceExisting = constants.first { (it as Enum<*>).name == "REPLACE_EXISTING" }
            java.lang.reflect.Array.set(options, 0, atomicMove)
            java.lang.reflect.Array.set(options, 1, replaceExisting)
            val toPath = File::class.java.getMethod("toPath")
            val move = filesClass.getMethod("move", pathClass, pathClass, options.javaClass)
            move.invoke(null, toPath.invoke(pending), toPath.invoke(base), options)
            return true
        } catch (error: java.lang.reflect.InvocationTargetException) {
            val cause = error.targetException
            if (cause is IOException) throw cause
            throw IOException("No se pudo reemplazar atómicamente el borrador en el host", cause)
        } catch (error: ReflectiveOperationException) {
            throw IOException("No se pudo preparar el reemplazo atómico del borrador en el host", error)
        }
    }

    /** Crea con revisión 0 o actualiza solo si el archivo sigue en la revisión esperada. */
    suspend fun write(document: ImportedEditorDocument, expectedRevision: Long?): Result<Unit> = withContext(Dispatchers.IO) {
        mutex.withLock {
            runCatching {
                require(document.schemaVersion == 3 && document.id.isNotBlank() && document.revision >= 0)
                require(!document.editingStarted || document.program?.id?.isNotBlank() == true)
                val previous = readLocked()
                if (previous == null) {
                    require(expectedRevision == null && document.revision == 0L)
                } else {
                    require(previous.id == document.id && previous.revision == expectedRevision)
                    require(document.revision == previous.revision + 1)
                }
                writeLocked(document)
                if (previous?.sourceEvidence?.sourceMediaId != document.sourceEvidence.sourceMediaId) {
                    runCatching { sourceMediaStore?.delete(previous?.sourceEvidence?.sourceMediaId) }
                }
            }
        }
    }

    /** Sustituye una lectura solo cuando el borrador anterior sigue intacto. */
    suspend fun replace(document: ImportedEditorDocument, expectedId: String, expectedRevision: Long): Result<Unit> =
        withContext(Dispatchers.IO) {
            mutex.withLock {
                runCatching {
                    val previous = requireNotNull(readLocked())
                    require(previous.id == expectedId && previous.revision == expectedRevision)
                    require(document.id.isNotBlank() && document.id != previous.id && document.revision == 0L)
                    require(document.schemaVersion == 3)
                    require(!document.editingStarted || document.program?.id?.isNotBlank() == true)
                    writeLocked(document)
                    runCatching { sourceMediaStore?.delete(previous.sourceEvidence.sourceMediaId) }
                    Unit
                }
            }
        }

    /** No borra un borrador que haya sido reemplazado o editado desde que se abrió. */
    suspend fun delete(id: String, expectedRevision: Long): Result<Boolean> = withContext(Dispatchers.IO) {
        mutex.withLock {
            runCatching {
                val current = readLocked() ?: return@runCatching false
                if (current.id != id || current.revision != expectedRevision) return@runCatching false
                val base = atomicFile.baseFile
                val rollback = snapshotPreviousDraft(base)
                try {
                    atomicFile.delete()
                    check(!base.exists() && !File(base.path + ".bak").exists())
                    rollback?.let { check(it.delete()) { "No se pudo cerrar el respaldo recuperable del borrador" } }
                    runCatching { sourceMediaStore?.delete(current.sourceEvidence.sourceMediaId) }
                    true
                } catch (error: Throwable) {
                    try {
                        restoreInterruptedWriteIfPresent(base)
                    } catch (cleanup: Throwable) {
                        error.addSuppressed(cleanup)
                    }
                    throw error
                }
            }
        }
    }
}
