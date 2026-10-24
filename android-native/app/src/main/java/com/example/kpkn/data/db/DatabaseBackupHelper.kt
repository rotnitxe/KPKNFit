package com.example.kpkn.data.db

import android.content.Context
import java.io.File
import java.io.FileInputStream
import java.io.FileOutputStream
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

object DatabaseBackupHelper {

    fun createSnapshot(context: Context): String {
        val db = KpknDatabase.getInstance(context)
        // Checkpoint completo para asegurar que todos los logs WAL se consoliden en el archivo .db principal
        db.openHelper.writableDatabase.query("PRAGMA wal_checkpoint(FULL)").close()

        val dbFile = context.getDatabasePath("kpkn.db")
        if (!dbFile.exists()) {
            throw Exception("Base de datos no encontrada para respaldar")
        }

        val snapshotDir = File(context.filesDir, "snapshots").apply { mkdirs() }
        val timestamp = SimpleDateFormat("yyyyMMdd_HHmmss", Locale.getDefault()).format(Date())
        val snapshotFile = File(snapshotDir, "kpkn_snapshot_$timestamp.db")

        copyFile(dbFile, snapshotFile)
        return snapshotFile.name
    }

    fun listSnapshots(context: Context): List<File> {
        val snapshotDir = File(context.filesDir, "snapshots")
        if (!snapshotDir.exists()) return emptyList()
        return snapshotDir.listFiles { _, name -> name.startsWith("kpkn_snapshot_") && name.endsWith(".db") }
            ?.sortedByDescending { it.lastModified() }
            ?: emptyList()
    }

    fun restoreSnapshot(context: Context, snapshotFile: File) {
        if (!snapshotFile.exists()) {
            throw Exception("Archivo de snapshot no encontrado")
        }

        val stagingName = "restore-${java.util.UUID.randomUUID()}.db"
        val staging = context.getDatabasePath(stagingName)
        try {
            // Room imports into a separate file, migrates and checks the full schema before any replacement.
            val candidate = KpknDatabase.buildPersistent(context, stagingName, snapshotFile)
            try {
                val sqlite = candidate.openHelper.writableDatabase
                sqlite.query("PRAGMA quick_check").use { result ->
                    require(result.moveToFirst() && result.getString(0) == "ok") { "Respaldo no íntegro; se conserva la base activa" }
                }
                sqlite.query("PRAGMA wal_checkpoint(TRUNCATE)").close()
            } finally { candidate.close() }
            createSnapshot(context) // Recovery point of the active DB; external backup remains untouched.
            KpknDatabase.closeInstance()
            val destDbFile = context.getDatabasePath("kpkn.db")
            val atomic = android.util.AtomicFile(destDbFile)
            val stream = atomic.startWrite()
            try {
                staging.inputStream().use { it.copyTo(stream) }
                atomic.finishWrite(stream)
                context.getDatabasePath("kpkn.db-wal").delete()
                context.getDatabasePath("kpkn.db-shm").delete()
            } catch (failure: Throwable) {
                atomic.failWrite(stream)
                throw failure
            }
        } finally { context.deleteDatabase(stagingName) }

    }

    fun deleteSnapshot(snapshotFile: File): Boolean {
        return if (snapshotFile.exists()) snapshotFile.delete() else false
    }

    private fun copyFile(source: File, dest: File) {
        FileInputStream(source).use { inStream ->
            FileOutputStream(dest).use { outStream ->
                val buffer = ByteArray(1024)
                var bytesRead: Int
                while (inStream.read(buffer).also { bytesRead = it } > 0) {
                    outStream.write(buffer, 0, bytesRead)
                }
            }
        }
    }
}
