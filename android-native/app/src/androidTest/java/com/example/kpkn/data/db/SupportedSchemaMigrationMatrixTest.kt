package com.example.kpkn.data.db

import android.content.ContentValues
import android.database.Cursor
import android.os.Bundle
import androidx.room.testing.MigrationTestHelper
import androidx.sqlite.db.SupportSQLiteDatabase
import androidx.sqlite.db.framework.FrameworkSQLiteOpenHelperFactory
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import org.json.JSONArray
import org.json.JSONObject
import org.junit.After
import org.junit.Assert.*
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import java.security.MessageDigest

/** Real exported schemas20..30, actual SQLite migrations; raw-column fidelity, not domain decoding. */
@RunWith(AndroidJUnit4::class)
class SupportedSchemaMigrationMatrixTest {
    private val instrumentation get() = InstrumentationRegistry.getInstrumentation()
    private val names = mutableListOf<String>()
    @get:Rule val helper = MigrationTestHelper(instrumentation, KpknDatabase::class.java,
        emptyList(), FrameworkSQLiteOpenHelperFactory())

    private fun migrations() = arrayOf(KpknDatabase.MIGRATION_20_21, KpknDatabase.MIGRATION_21_22,
        KpknDatabase.MIGRATION_22_23, KpknDatabase.MIGRATION_23_24, KpknDatabase.MIGRATION_24_25,
        KpknDatabase.MIGRATION_25_26, KpknDatabase.MIGRATION_26_27, KpknDatabase.MIGRATION_27_28,
        KpknDatabase.MIGRATION_28_29, KpknDatabase.MIGRATION_29_30)

    @After fun removeOnlyFixtureDatabases() {
        check(instrumentation.targetContext.packageName == "com.example.kpkn.stability")
        names.forEach { name ->
            check(name.matches(Regex("qa-migration-matrix-(20|21|22|23|24|25|26|27|28|29|30)\\.db")))
            instrumentation.targetContext.deleteDatabase(name)
        }
    }

    private fun quoted(value: String): String {
        check(value.matches(Regex("[A-Za-z_][A-Za-z0-9_]*")))
        return "`$value`"
    }
    private fun readRows(db: SupportSQLiteDatabase, table: String, columns: List<String>): List<List<String?>> =
        db.query("SELECT ${columns.joinToString(",") { quoted(it) }} FROM ${quoted(table)}").use { cursor ->
            buildList {
                while (cursor.moveToNext()) add(columns.indices.map { index ->
                    when (cursor.getType(index)) {
                        Cursor.FIELD_TYPE_NULL -> null
                        Cursor.FIELD_TYPE_BLOB -> cursor.getBlob(index).joinToString("") { "%02x".format(it) }
                        else -> cursor.getString(index)
                    }
                })
            }
        }

    @Test fun everyExportedVersionPreservesAllOriginalPersonalColumns() {
        check(instrumentation.targetContext.packageName == "com.example.kpkn.stability")
        assertEquals("Review this matrix when schema advances", 30, KpknDatabase.DATABASE_VERSION)
        val receipts = JSONArray()
        for (version in 20..30) {
            val schemaBytes = instrumentation.context.assets
                .open("com.example.kpkn.data.db.KpknDatabase/$version.json").use { it.readBytes() }
            val schema = JSONObject(schemaBytes.toString(Charsets.UTF_8)).getJSONObject("database")
            assertEquals(version, schema.getInt("version"))
            val name = "qa-migration-matrix-$version.db"
            names += name
            val columns = linkedMapOf<String,List<String>>()
            val original = linkedMapOf<String,List<List<String?>>>()
            helper.createDatabase(name, version).use { old ->
                val entities = schema.getJSONArray("entities")
                for (index in 0 until entities.length()) {
                    val entity = entities.getJSONObject(index)
                    val table = entity.getString("tableName")
                    val category = requireNotNull(BackupTableManifest.tableCategory[table])
                    if (category != BackupTableManifest.Category.USER_DATA) continue
                    val fields = entity.getJSONArray("fields")
                    val values = ContentValues()
                    val fieldNames = mutableListOf<String>()
                    for (fieldIndex in 0 until fields.length()) {
                        val field = fields.getJSONObject(fieldIndex)
                        val column = field.getString("columnName")
                        fieldNames += column
                        when (field.getString("affinity")) {
                            "INTEGER" -> values.put(column, if (column == "rowId") 1L else 42L)
                            "REAL" -> values.put(column, 17.5)
                            "TEXT" -> values.put(column, when {
                                column == "workoutLogId" -> "qa-workout_logs-id"
                                column == "id" -> "qa-$table-id"
                                column == "data" || column.endsWith("Json") -> "{\"qaMarker\":\"original-$version-α\"}"
                                else -> "qa-$column-$version-α"
                            })
                            "BLOB" -> values.put(column, byteArrayOf(0,1,2,127))
                            else -> error("Unsupported fixture affinity")
                        }
                    }
                    old.insert(table, android.database.sqlite.SQLiteDatabase.CONFLICT_ABORT, values)
                    columns[table] = fieldNames
                    original[table] = readRows(old, table, fieldNames)
                    assertEquals("Exactly one original marker row per table", 1, original[table]!!.size)
                }
                assertTrue("A schema must contain personal data", columns.isNotEmpty())
            }
            helper.runMigrationsAndValidate(name, KpknDatabase.DATABASE_VERSION, true, *migrations()).use { upgraded ->
                upgraded.query("PRAGMA integrity_check").use { cursor ->
                    assertTrue(cursor.moveToFirst()); assertEquals("ok", cursor.getString(0))
                }
                upgraded.query("PRAGMA foreign_key_check").use { assertFalse(it.moveToFirst()) }
                for ((table, fields) in columns) {
                    assertEquals("Version $version, original columns in $table", original[table], readRows(upgraded, table, fields))
                }
            }
            val hash = MessageDigest.getInstance("SHA-256").digest(schemaBytes).joinToString("") { "%02x".format(it) }
            receipts.put(JSONObject().put("fromVersion",version).put("toVersion",30)
                .put("personalTables",columns.size).put("originalColumns",columns.values.sumOf { it.size })
                .put("schemaSha256",hash).put("rawColumnsPreserved",true))
        }
        assertEquals(11, receipts.length())
        instrumentation.sendStatus(2, Bundle().apply { putString("kpkn_migration_matrix",receipts.toString()) })
    }
}
