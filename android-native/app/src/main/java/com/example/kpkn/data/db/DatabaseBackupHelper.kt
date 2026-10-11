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

        // 1. Cerrar de forma segura la instancia activa de Room
        KpknDatabase.closeInstance()

        val destDbFile = context.getDatabasePath("kpkn.db")

        // 2. Eliminar archivos temporales de log WAL/SHM para evitar corrupción por colisión de logs antiguos
        context.getDatabasePath("kpkn.db-wal").delete()
        context.getDatabasePath("kpkn.db-shm").delete()

        // 3. Reemplazar el archivo de base de datos principal
        copyFile(snapshotFile, destDbFile)
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
