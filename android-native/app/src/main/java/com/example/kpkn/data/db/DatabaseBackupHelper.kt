package com.example.kpkn.data.db

import android.content.Context
import java.io.File
import java.io.FileInputStream
import java.io.FileOutputStream
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

object DatabaseBackupHelper {

    private const val DEFAULT_DATABASE_NAME = "kpkn.db"
    private const val DEFAULT_SNAPSHOT_PREFIX = "kpkn_snapshot_"
    private const val VT_QA_SNAPSHOT_PREFIX = "kpkn-vt-qa-v29_snapshot_"

    internal fun snapshotFilePrefixForApplicationId(applicationId: String): String =
        if (KpknDatabase.databaseNameForApplicationId(applicationId) == DEFAULT_DATABASE_NAME) {
            DEFAULT_SNAPSHOT_PREFIX
        } else {
            VT_QA_SNAPSHOT_PREFIX
        }

    fun createSnapshot(context: Context): String {
        val appContext = context.applicationContext
        val dbName = KpknDatabase.databaseNameForApplicationId(appContext.packageName)
        val snapshotPrefix = snapshotFilePrefixForApplicationId(appContext.packageName)
        val db = KpknDatabase.getInstance(appContext)
        // Checkpoint completo para asegurar que todos los logs WAL se consoliden en el archivo .db principal
        db.openHelper.writableDatabase.query("PRAGMA wal_checkpoint(FULL)").close()

        val dbFile = appContext.getDatabasePath(dbName)
        if (!dbFile.exists()) {
            throw Exception("Base de datos no encontrada para respaldar")
        }

        val snapshotDir = File(appContext.filesDir, "snapshots").apply { mkdirs() }
        val timestamp = SimpleDateFormat("yyyyMMdd_HHmmss", Locale.getDefault()).format(Date())
        val snapshotFile = File(snapshotDir, "$snapshotPrefix$timestamp.db")

        copyFile(dbFile, snapshotFile)
        return snapshotFile.name
    }

    fun listSnapshots(context: Context): List<File> {
        val appContext = context.applicationContext
        val snapshotDir = File(appContext.filesDir, "snapshots")
        if (!snapshotDir.exists()) return emptyList()
        val snapshotPrefix = snapshotFilePrefixForApplicationId(appContext.packageName)
        return snapshotDir.listFiles { _, name -> name.startsWith(snapshotPrefix) && name.endsWith(".db") }
            ?.sortedByDescending { it.lastModified() }
            ?: emptyList()
    }

    fun restoreSnapshot(context: Context, snapshotFile: File) {
        if (!snapshotFile.exists()) {
            throw Exception("Archivo de snapshot no encontrado")
        }

        val appContext = context.applicationContext
        val dbName = KpknDatabase.databaseNameForApplicationId(appContext.packageName)

        // 1. Cerrar de forma segura la instancia activa de Room
        KpknDatabase.closeInstance()

        val destDbFile = appContext.getDatabasePath(dbName)

        // 2. Eliminar archivos temporales de log WAL/SHM para evitar corrupción por colisión de logs antiguos
        appContext.getDatabasePath("$dbName-wal").delete()
        appContext.getDatabasePath("$dbName-shm").delete()

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
