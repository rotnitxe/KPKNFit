package com.example.kpkn.data.db

import android.content.Context
import android.database.Cursor
import android.database.sqlite.SQLiteDatabase
import com.example.kpkn.data.backup.BoundedInput
import com.example.kpkn.data.backup.MaintenanceCheckpoint
import com.example.kpkn.data.backup.MaintenancePhase
import java.io.File
import java.nio.file.Files
import java.nio.file.StandardCopyOption
import java.util.UUID

/** Transactions preserve a consistent snapshot and keep the live Room connection usable. */
object DatabaseBackupHelper {
    private val maintenanceLock = Any()
    private const val RETENTION_MS = 30L * 24 * 60 * 60 * 1000

    fun createSnapshot(context: Context): String = createSnapshot(context, MaintenanceCheckpoint.None)
    internal fun createSnapshot(context: Context, checkpoint: MaintenanceCheckpoint): String = synchronized(maintenanceLock) {
        val db = KpknDatabase.getInstance(context)
        val directory = File(context.filesDir, "snapshots").apply { check(isDirectory || mkdirs()) }
        val temporary = File.createTempFile("kpkn_snapshot_", ".db.partial", directory)
        val name = temporary.name.removeSuffix(".partial")
        try {
            SQLiteDatabase.openOrCreateDatabase(temporary, null).use { destination ->
                db.runInTransaction {
                    val source = db.openHelper.readableDatabase
                    destination.beginTransaction()
                    try {
                        val definitions = source.query("SELECT type, name, sql FROM sqlite_master WHERE sql IS NOT NULL AND name NOT LIKE 'sqlite_%' AND name != 'android_metadata'").use { cursor ->
                            buildList { while (cursor.moveToNext()) add(Triple(cursor.getString(0), cursor.getString(1), cursor.getString(2))) }
                        }
                        definitions.filter { it.first == "table" && !isFtsShadow(it.second) }.forEach { destination.execSQL(it.third) }
                        definitions.filter { it.first == "table" && !isFtsShadow(it.second) && it.second != "global_foods_fts" }.forEach { (_, table, _) ->
                            copyTable(source, table) { sql, values ->
                                destination.execSQL(sql, values)
                                checkpoint.reached(MaintenancePhase.SNAPSHOT_ROW_COPIED)
                            }
                        }
                        if (definitions.any { it.second == "global_foods_fts" }) {
                            destination.execSQL("INSERT INTO global_foods_fts(global_foods_fts) VALUES('rebuild')")
                        }
                        definitions.filter { it.first in setOf("index", "trigger", "view") }.forEach { destination.execSQL(it.third) }
                        destination.version = source.version
                        destination.setTransactionSuccessful()
                    } finally { destination.endTransaction() }
                    validate(destination)
                }
            }
            java.io.FileInputStream(temporary).use { it.fd.sync() }
            Files.move(temporary.toPath(), File(directory, name).toPath(), StandardCopyOption.ATOMIC_MOVE)
            listSnapshots(context).forEachIndexed { index, file ->
                if (index >= 10 || System.currentTimeMillis() - file.lastModified() > RETENTION_MS) check(file.delete())
            }
            name
        } finally { temporary.delete() }
    }

    fun listSnapshots(context: Context): List<File> = File(context.filesDir, "snapshots")
        .listFiles { _, name -> name.startsWith("kpkn_snapshot_") && name.endsWith(".db") }
        ?.sortedByDescending { it.lastModified() } ?: emptyList()

    /** Invalid imports never replace the main file. A failed transaction rolls back every table. */
    fun restoreSnapshot(context: Context, snapshotFile: File, recovering: Boolean = false): Unit =
        restoreSnapshot(context, snapshotFile, recovering, MaintenanceCheckpoint.None)
    internal fun restoreSnapshot(context: Context, snapshotFile: File, recovering: Boolean, checkpoint: MaintenanceCheckpoint): Unit = synchronized(maintenanceLock) {
        require(snapshotFile.isFile && snapshotFile.length() in 1..BoundedInput.MAX_DATABASE_BYTES) { "Invalid database snapshot size" }
        val live = KpknDatabase.getInstance(context)
        check(recovering || live.openHelper.readableDatabase.query("SELECT COUNT(*) FROM ongoing_workout").use { it.moveToFirst(); it.getLong(0) == 0L }) {
            "Finish or explicitly cancel the active workout before restoring"
        }
        val stageName = "restore_${UUID.randomUUID()}.db"
        val stage = context.getDatabasePath(stageName)
        try {
            snapshotFile.inputStream().use { input -> java.io.FileOutputStream(stage).use { output ->
                BoundedInput.copy(input, output, BoundedInput.MAX_DATABASE_BYTES); output.fd.sync()
            } }
            // Native SQLite integrity_check validates FTS4 using internal writes.
            // Permit those writes only on our isolated copy, never on the import source.
            SQLiteDatabase.openDatabase(stage.absolutePath, null, SQLiteDatabase.OPEN_READWRITE).use(::validate)
            val imported = KpknDatabase.openIsolated(context, stageName)
            try {
                val source = imported.openHelper.readableDatabase // migrates and validates Room identity
                check(source.version == live.openHelper.readableDatabase.version)
                // Device binding and pending network receipts must never be imported from another profile.
                val tables = BackupTableManifest.tableCategory.filterValues { recovering || it != BackupTableManifest.Category.CACHE }
                    .keys.filterNot { it == "global_foods_fts" }
                val present = source.query("SELECT name FROM sqlite_master WHERE type='table'").use { cursor ->
                    buildSet { while (cursor.moveToNext()) add(cursor.getString(0)) }
                }
                check(present.containsAll(tables)) { "Snapshot does not contain all classified tables" }
                live.runInTransaction {
                    val destination = live.openHelper.writableDatabase
                    if (recovering) destination.execSQL("DELETE FROM sync_configuration")
                    destination.execSQL("DELETE FROM workout_media_session_associations")
                    tables.filterNot { it == "workout_media_session_associations" }.forEach { destination.execSQL("DELETE FROM `${safeIdentifier(it)}`") }
                    tables.sortedBy { when (it) { "sync_configuration" -> 3; "sync_mutations", "sync_shadows" -> 2; "workout_media_session_associations" -> 1; else -> 0 } }.forEach { table ->
                        copyTable(source, table) { sql, values ->
                            destination.execSQL(sql, values)
                            checkpoint.reached(MaintenancePhase.DATABASE_ROW_COPIED)
                        }
                    }
                    check(destination.query("PRAGMA foreign_key_check").use { !it.moveToFirst() }) { "Snapshot contains broken relationships" }
                    destination.query("SELECT COUNT(*) FROM settings").use { check(it.moveToFirst()) }
                }
            } finally { imported.close() }
        } finally { context.deleteDatabase(stageName) }
    }

    fun deleteSnapshot(snapshotFile: File): Boolean = snapshotFile.isFile && snapshotFile.delete()

    private fun validate(database: SQLiteDatabase) {
        require(database.version in 1..KpknDatabase.DATABASE_VERSION) { "Unsupported snapshot version" }
        check(database.rawQuery("PRAGMA integrity_check", null).use { it.moveToFirst() && it.getString(0) == "ok" }) { "Snapshot integrity check failed" }
        check(database.rawQuery("PRAGMA foreign_key_check", null).use { !it.moveToFirst() }) { "Snapshot contains broken relationships" }
    }
    private fun safeIdentifier(value: String): String = value.also { require(it.matches(Regex("[A-Za-z_][A-Za-z0-9_]*"))) }
    private fun isFtsShadow(table: String) = table.startsWith("global_foods_fts_")

    private fun copyTable(source: androidx.sqlite.db.SupportSQLiteDatabase, table: String, write: (String, Array<Any?>) -> Unit) {
        if (BackupTableManifest.tableCategory[table] == BackupTableManifest.Category.USER_DATA || table in setOf("sync_mutations", "sync_shadows"))
            BoundedSqlRows.copyTable(source, table, write)
        else source.query("SELECT * FROM `${safeIdentifier(table)}`").use { copyRows(it, table, write) }
    }

    private fun copyRows(cursor: Cursor, table: String, write: (String, Array<Any?>) -> Unit) {
        val columns = cursor.columnNames.joinToString(",") { "`${safeIdentifier(it)}`" }
        val sql = "INSERT INTO `${safeIdentifier(table)}` ($columns) VALUES (${cursor.columnNames.joinToString(",") { "?" }})"
        while (cursor.moveToNext()) {
            val values = Array<Any?>(cursor.columnCount) { index ->
                when (cursor.getType(index)) {
                    Cursor.FIELD_TYPE_NULL -> null
                    Cursor.FIELD_TYPE_INTEGER -> cursor.getLong(index)
                    Cursor.FIELD_TYPE_FLOAT -> cursor.getDouble(index)
                    Cursor.FIELD_TYPE_BLOB -> cursor.getBlob(index)
                    else -> cursor.getString(index)
                }
            }
            write(sql, values)
        }
    }
}
