package com.example.kpkn.data.db

import android.content.Context
import android.util.Log
import androidx.sqlite.db.SupportSQLiteDatabase
import java.io.File
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale
import java.util.UUID
import java.util.concurrent.CancellationException

/** Private-copy validation followed by transactional replacement; live repositories keep their Room connection. */
object DatabaseBackupHelper {
    private const val TAG = "DatabaseBackupHelper"
    internal const val MAX_RETAINED_SNAPSHOTS = 5

    // Virtual FTS contents follow global_foods through the existing Room triggers, never copied as shadow tables.
    private val storedTables: List<String> get() = BackupTableManifest.tableCategory.keys.filter { it != "global_foods_fts" }

    private fun nextAttachAlias(prefix: String): String =
        "${prefix}_${UUID.randomUUID().toString().replace("-", "").take(16)}"

    private fun detachSafely(sqlite: SupportSQLiteDatabase, alias: String, primaryError: Throwable?) {
        try {
            sqlite.execSQL("DETACH DATABASE `$alias`")
        } catch (detachError: Throwable) {
            if (primaryError != null) {
                primaryError.addSuppressed(detachError)
            } else {
                Log.w(TAG, "Could not detach database alias $alias", detachError)
            }
        }
    }

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

    private fun canonicalPathKey(file: File): String =
        runCatching { file.canonicalPath }.getOrDefault(file.absolutePath)

    private fun pruneOldSnapshots(directory: File, keep: Int = MAX_RETAINED_SNAPSHOTS, newest: File? = null, preserve: File? = null) {
        val keptKeys = buildSet {
            newest?.let { add(canonicalPathKey(it)) }
            preserve?.let { add(canonicalPathKey(it)) }
        }
        val snapshots = directory
            .listFiles { _, name -> name.startsWith("kpkn_snapshot_") && name.endsWith(".db") }
            ?.sortedWith(compareByDescending<File> { it.lastModified() }.thenByDescending { it.name })
            .orEmpty()
        val remainingSlots = (keep - keptKeys.size).coerceAtLeast(0)
        var slotsUsed = 0
        for (candidate in snapshots) {
            if (canonicalPathKey(candidate) in keptKeys) continue
            if (slotsUsed < remainingSlots) {
                slotsUsed++
            } else {
                deleteSnapshot(candidate)
            }
        }
    }

    @Synchronized
    fun createSnapshot(context: Context): String = createSnapshot(context, preserveSnapshot = null)

    @Synchronized
    internal fun createSnapshot(context: Context, preserveSnapshot: File? = null): String {
        val active = KpknDatabase.getInstance(context)
        val directory = File(context.filesDir, "snapshots").apply { mkdirs() }
        val timestamp = SimpleDateFormat("yyyyMMdd_HHmmss", Locale.ROOT).format(Date())
        val file = File(directory, "kpkn_snapshot_${timestamp}_${UUID.randomUUID()}.db")
        var complete = false
        try {
            // Materialize the current schema, then copy a coherent set of rows under Room's writer transaction.
            checkpoint(context, file)
            val sqlite = active.openHelper.writableDatabase
            val alias = nextAttachAlias("snapshot_copy")
            sqlite.execSQL("ATTACH DATABASE ? AS `$alias`", arrayOf(file.absolutePath))
            var txError: Throwable? = null
            try {
                active.runInTransaction(Runnable { copyTables(sqlite, "main", alias) })
            } catch (t: Throwable) {
                txError = t
                throw t
            } finally {
                detachSafely(sqlite, alias, txError)
            }
            checkpoint(context, file)
            complete = true
            pruneOldSnapshots(directory, keep = MAX_RETAINED_SNAPSHOTS, newest = file, preserve = preserveSnapshot)
            return file.name
        } finally {
            if (!complete) deleteSnapshot(file)
        }
    }

    fun listSnapshots(context: Context): List<File> = File(context.filesDir, "snapshots")
        .listFiles { _, name -> name.startsWith("kpkn_snapshot_") && name.endsWith(".db") }
        ?.sortedByDescending { it.lastModified() }.orEmpty()

    @Synchronized
    fun restoreSnapshot(context: Context, snapshotFile: File, isCancelled: () -> Boolean = { false }) {
        restoreSnapshot(context, snapshotFile, createSafetySnapshot = true, isCancelled = isCancelled)
    }

    @Synchronized
    internal fun restoreSnapshot(
        context: Context,
        snapshotFile: File,
        createSafetySnapshot: Boolean,
        isCancelled: () -> Boolean = { false },
    ) {
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
            // Read-write on purpose: since SQLite 3.44 quick_check also checks FTS4 tables (global_foods_fts) by running an
            // INSERT, which fails on a read-only connection on recent Android (seen on API 36). The staging copy is disposable.
            staging.parentFile?.mkdirs()
            snapshotFile.inputStream().use { input -> staging.outputStream().use { output -> input.copyTo(output) } }
            val checked=android.database.sqlite.SQLiteDatabase.openDatabase(staging.absolutePath,null,
                android.database.sqlite.SQLiteDatabase.OPEN_READWRITE,
                android.database.DatabaseErrorHandler { throw IllegalStateException("Snapshot corrupto; se conservan los datos activos y el archivo externo") })
            try {
                // Upper bound = @Database(version) of KpknDatabase; NutritionDayStateBackupTest fails when it falls behind.
                require(checked.version in 1..38) { "Versión de snapshot no compatible" }
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
            if (createSafetySnapshot) {
                createSnapshot(context, preserveSnapshot = snapshotFile)
            }
            val active = KpknDatabase.getInstance(context)
            val sqlite = active.openHelper.writableDatabase
            val alias = nextAttachAlias("restore_copy")
            sqlite.execSQL("ATTACH DATABASE ? AS `$alias`", arrayOf(staging.absolutePath))
            var txError: Throwable? = null
            try {
                active.runInTransaction(Runnable { copyTables(sqlite, alias, "main", isCancelled) })
            } catch (t: Throwable) {
                txError = t
                throw t
            } finally {
                detachSafely(sqlite, alias, txError)
            }
            // Room's transaction/invalidation tracker publishes the restored data to existing repositories.
        } finally { context.deleteDatabase(stagingName) }
    }

    fun deleteSnapshot(snapshotFile: File): Boolean {
        val deleted = snapshotFile.isFile && snapshotFile.delete()
        listOf("-wal", "-shm").forEach { suffix ->
            val sidecar = File(snapshotFile.path + suffix)
            if (sidecar.isFile) sidecar.delete()
        }
        return deleted
    }
}
