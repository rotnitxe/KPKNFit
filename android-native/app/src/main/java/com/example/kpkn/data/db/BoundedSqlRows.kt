package com.example.kpkn.data.db

import android.database.Cursor
import androidx.sqlite.db.SupportSQLiteDatabase
import java.io.ByteArrayOutputStream

/** Reads text/blob chunks independently, so a valid program need not fit in CursorWindow. */
object BoundedSqlRows {
    private const val CHUNK = 4096
    private const val MAX_FIELD_BYTES = 64 * 1024 * 1024
    data class Column(val name: String, val type: String, val notNull: Boolean = false, val defaultSql: String? = null)
    fun columns(database: SupportSQLiteDatabase, table: String): List<Column> = database.query("PRAGMA table_info(`${identifier(table)}`)").use { cursor ->
        buildList { while (cursor.moveToNext()) add(Column(cursor.getString(1), cursor.getString(2).uppercase(), cursor.getInt(3) != 0, if (cursor.isNull(4)) null else cursor.getString(4))) }
    }
    fun read(database: SupportSQLiteDatabase, table: String, primaryColumn: String, key: Any,
        columns: List<Column> = columns(database, table)): Map<String, Any?>? {
        require(columns.isNotEmpty())
        val selections = columns.joinToString(",") { column ->
            if (column.type in setOf("TEXT", "BLOB")) "CASE WHEN length(`${column.name}`)>$CHUNK THEN NULL ELSE `${column.name}` END AS `${column.name}`"
            else "`${column.name}`"
        }
        val row = database.query("SELECT $selections FROM `${identifier(table)}` WHERE `${identifier(primaryColumn)}`=? LIMIT 1", arrayOf(key)).use { cursor ->
            if (!cursor.moveToFirst()) return null
            columns.mapIndexed { index, column -> column.name to value(cursor, index) }.toMap().toMutableMap()
        }
        columns.filter { it.type in setOf("TEXT", "BLOB") && row[it.name] == null }.forEach { column ->
            val size = database.query("SELECT length(`${column.name}`) FROM `${identifier(table)}` WHERE `${identifier(primaryColumn)}`=?", arrayOf(key)).use {
                check(it.moveToFirst()); if (it.isNull(0)) null else it.getLong(0)
            } ?: return@forEach
            require(size in 0..MAX_FIELD_BYTES.toLong()) { "Database field exceeds the supported size" }
            if (column.type == "TEXT") {
                var bytes = 0L
                row[column.name] = buildString {
                    var start = 1L
                    while (start <= size) {
                        val part = database.query("SELECT substr(`${column.name}`,?,$CHUNK) FROM `${identifier(table)}` WHERE `${identifier(primaryColumn)}`=?", arrayOf(start, key)).use {
                            check(it.moveToFirst()); it.getString(0)
                        }
                        bytes += part.toByteArray(Charsets.UTF_8).size
                        require(bytes <= MAX_FIELD_BYTES) { "Database field exceeds the supported size" }
                        append(part); start += CHUNK
                    }
                }
            } else {
                val output = ByteArrayOutputStream(size.toInt())
                var start = 1L
                while (start <= size) {
                    val part = database.query("SELECT substr(`${column.name}`,?,$CHUNK) FROM `${identifier(table)}` WHERE `${identifier(primaryColumn)}`=?", arrayOf(start, key)).use {
                        check(it.moveToFirst()); it.getBlob(0)
                    }
                    output.write(part); start += CHUNK
                }
                row[column.name] = output.toByteArray()
            }
        }
        return row
    }
    fun copyTable(database: SupportSQLiteDatabase, table: String, write: (String, Array<Any?>) -> Unit) {
        val columns = columns(database, table)
        val sql = "INSERT INTO `${identifier(table)}` (${columns.joinToString(",") { "`${identifier(it.name)}`" }}) VALUES (${columns.joinToString(",") { "?" }})"
        var last: Long? = null
        do {
            val keys = database.query("SELECT rowid FROM `${identifier(table)}` ${if (last == null) "" else "WHERE rowid>?"} ORDER BY rowid LIMIT 256",
                if (last == null) emptyArray() else arrayOf(last)).use { cursor ->
                buildList { while (cursor.moveToNext()) add(cursor.getLong(0)) }
            }
            for (key in keys) {
                val row = requireNotNull(read(database, table, "rowid", key, columns))
                write(sql, columns.map { row[it.name] }.toTypedArray())
            }
            last = keys.lastOrNull()
        } while (last != null)
    }
    private fun value(cursor: Cursor, index: Int): Any? = when (cursor.getType(index)) {
        Cursor.FIELD_TYPE_NULL -> null
        Cursor.FIELD_TYPE_INTEGER -> cursor.getLong(index)
        Cursor.FIELD_TYPE_FLOAT -> cursor.getDouble(index)
        Cursor.FIELD_TYPE_BLOB -> cursor.getBlob(index)
        else -> cursor.getString(index)
    }
    private fun identifier(value: String) = value.also { require(it.matches(Regex("[A-Za-z_][A-Za-z0-9_]*"))) }
}
