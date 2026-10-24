package com.example.kpkn.data.db

import android.content.Context
import androidx.sqlite.db.SupportSQLiteDatabase
import java.io.File
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale
import java.util.UUID
import java.util.concurrent.CancellationException

/** Private-copy validation followed by transactional replacement; live repositories keep their Room connection. */
object DatabaseBackupHelper {
    // Virtual FTS contents follow global_foods through the existing Room triggers, never copied as shadow tables.
    private val storedTables: List<String> get() = BackupTableManifest.tableCategory.keys.filter { it != "global_foods_fts" }

    private fun copyTables(db: SupportSQLiteDatabase, from: String, to: String, isCancelled: () -> Boolean = { false }) {
        fun ensureActive() { if (isCancelled()) throw CancellationException("Restauración cancelada; se conservan los datos activos") }
        db.execSQL("PRAGMA defer_foreign_keys=ON")
        storedTables.forEach { table -> ensureActive(); db.execSQL("DELETE FROM `$to`.`$table`") }
        storedTables.forEach { table ->
            ensureActive()
            // Named columns preserve values even when historical ALTER TABLE changed physical column order.
            val columns = db.query("PRAGMA `$to`.table_info(`$table`)").use { cursor ->
                buildList { while (cursor.moveToNext()) add("`${cursor.getString(1)}`") }
            }.joinToString(",")
            require(columns.isNotEmpty()) { "Tabla de respaldo no compatible: $table" }
            db.execSQL("INSERT INTO `$to`.`$table` ($columns) SELECT $columns FROM `$from`.`$table`")
        }
        ensureActive()
        db.query("PRAGMA `$to`.foreign_key_check").use { require(it.count == 0) { "Relaciones de respaldo no válidas; se conserva la base activa" } }
    }

    private fun checkpoint(context: Context, file: File) {
        val db = KpknDatabase.buildPersistent(context, file.absolutePath)
        try { db.openHelper.writableDatabase.query("PRAGMA wal_checkpoint(TRUNCATE)").close() }
        finally { db.close() }
    }

    @Synchronized
    fun createSnapshot(context: Context): String {
        val active = KpknDatabase.getInstance(context)
        val directory = File(context.filesDir, "snapshots").apply { mkdirs() }
        val timestamp = SimpleDateFormat("yyyyMMdd_HHmmss", Locale.ROOT).format(Date())
        val file = File(directory, "kpkn_snapshot_${timestamp}_${UUID.randomUUID()}.db")
        var complete = false
        try {
            // Materialize the current schema, then copy a coherent set of rows under Room's writer transaction.
            checkpoint(context, file)
            val sqlite = active.openHelper.writableDatabase
            sqlite.execSQL("ATTACH DATABASE ? AS snapshot_copy", arrayOf(file.absolutePath))
            try { active.runInTransaction(Runnable { copyTables(sqlite, "main", "snapshot_copy") }) }
            finally { sqlite.execSQL("DETACH DATABASE snapshot_copy") }
            checkpoint(context, file)
            complete = true
            return file.name
        } finally {
            if (!complete) listOf("", "-wal", "-shm").forEach { File(file.path + it).delete() }
        }
    }

    fun listSnapshots(context: Context): List<File> = File(context.filesDir, "snapshots")
        .listFiles { _, name -> name.startsWith("kpkn_snapshot_") && name.endsWith(".db") }
        ?.sortedByDescending { it.lastModified() }.orEmpty()

    @Synchronized
    fun restoreSnapshot(context: Context, snapshotFile: File, isCancelled: () -> Boolean = { false }) {
        require(snapshotFile.isFile) { "Archivo de snapshot no encontrado" }
        if (isCancelled()) throw CancellationException("Restauración cancelada")
        require(snapshotFile.length() >= 100L) { "Archivo de snapshot SQLite incompleto" }
        java.io.DataInputStream(snapshotFile.inputStream()).use { input ->
            val header=ByteArray(16)
            input.readFully(header)
            require(header.contentEquals("SQLite format 3\u0000".toByteArray(Charsets.US_ASCII))) { "El archivo no es un snapshot SQLite" }
        }
        val stagingName = "restore-${UUID.randomUUID()}.db"
        val staging = context.getDatabasePath(stagingName)
        try {
            // Validate the copied SQLite file before Room can invoke any corruption recovery callback.
            staging.parentFile?.mkdirs()
            snapshotFile.inputStream().use { input -> staging.outputStream().use { output -> input.copyTo(output) } }
            val checked=android.database.sqlite.SQLiteDatabase.openDatabase(staging.absolutePath,null,
                android.database.sqlite.SQLiteDatabase.OPEN_READONLY,
                android.database.DatabaseErrorHandler { throw IllegalStateException("Snapshot corrupto; se conservan los datos activos y el archivo externo") })
            try {
                require(checked.version in 1..33) { "Versión de snapshot no compatible" }
                checked.rawQuery("PRAGMA quick_check",null).use { check ->
                    require(check.count==1 && check.moveToFirst() && check.getString(0)=="ok") { "Snapshot SQLite corrupto" }
                }
            } finally { checked.close() }
            val candidate = KpknDatabase.buildPersistent(context, stagingName)
            try {
                val sqlite = candidate.openHelper.writableDatabase
                sqlite.query("PRAGMA quick_check").use { require(it.moveToFirst() && it.getString(0) == "ok") { "Respaldo no íntegro" } }
                sqlite.query("PRAGMA wal_checkpoint(TRUNCATE)").close()
            } finally { candidate.close() }
            if (isCancelled()) throw CancellationException("Restauración cancelada")
            createSnapshot(context)
            val active = KpknDatabase.getInstance(context)
            val sqlite = active.openHelper.writableDatabase
            sqlite.execSQL("ATTACH DATABASE ? AS restore_copy", arrayOf(staging.absolutePath))
            try { active.runInTransaction(Runnable { copyTables(sqlite, "restore_copy", "main", isCancelled) }) }
            finally { sqlite.execSQL("DETACH DATABASE restore_copy") }
            // Room's transaction/invalidation tracker publishes the restored data to existing repositories.
        } finally { context.deleteDatabase(stagingName) }
    }

    fun deleteSnapshot(snapshotFile: File): Boolean = snapshotFile.isFile && snapshotFile.delete()
}
