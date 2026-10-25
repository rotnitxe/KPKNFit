package com.example.kpkn.data.db

import android.app.Application
import android.content.ContentValues
import android.content.Context
import android.content.ContextWrapper
import android.database.Cursor
import android.database.DatabaseErrorHandler
import android.database.sqlite.SQLiteDatabase
import androidx.room.Room
import androidx.test.core.app.ApplicationProvider
import com.example.kpkn.testsupport.RoomTestRule
import kotlinx.coroutines.runBlocking
import org.json.JSONObject
import org.junit.Assert.*
import org.junit.Rule
import org.junit.Test
import org.junit.rules.TemporaryFolder
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import java.io.File

@RunWith(RobolectricTestRunner::class)
@Config(manifest = Config.NONE, sdk = [34], application = Application::class)
class NutritionDayStateMigrationTest {
    @get:Rule val roomRule = RoomTestRule()
    @get:Rule val folder = TemporaryFolder()
    private val context get() = ApplicationProvider.getApplicationContext<Context>()

    @Test fun `Room migra la base v29 completa conservando cada fila e indexa los codigos`() = verifyUpgrade(29)
    @Test fun `Room migra calibracion v30 conservando decisiones y cada fila anterior`() = verifyUpgrade(30)
    private fun verifyUpgrade(fromVersion: Int) {
        val directory = folder.newFolder("v$fromVersion-31")
        val file = File(directory, "day.db")
        val legacySchema = schema(fromVersion)
        val entities = legacySchema.getJSONArray("entities").let { list -> (0 until list.length()).map { list.getJSONObject(it) } }
        val old = SQLiteDatabase.openOrCreateDatabase(file, null)
        val before = try {
            entities.forEach { old.execSQL(it.getString("createSql").replace("\${TABLE_NAME}", it.getString("tableName"))) }
            entities.forEach { entity ->
                val indices = entity.optJSONArray("indices") ?: return@forEach
                for (i in 0 until indices.length()) old.execSQL(indices.getJSONObject(i).getString("createSql").replace("\${TABLE_NAME}", entity.getString("tableName")))
            }
            val setup = legacySchema.getJSONArray("setupQueries")
            for (i in 0 until setup.length()) old.execSQL(setup.getString(i))
            entities.filterNot { it.getString("tableName") == "global_foods_fts" }.forEach { entity ->
                val fields = entity.getJSONArray("fields")
                val values = ContentValues()
                for (i in 0 until fields.length()) {
                    val field = fields.getJSONObject(i)
                    val name = field.getString("columnName")
                    when (field.getString("affinity")) {
                        "INTEGER" -> values.put(name, 7)
                        "REAL" -> values.put(name, 12.5)
                        else -> values.put(name, "dato-preservado")
                    }
                }
                assertTrue(old.insertOrThrow(entity.getString("tableName"), null, values) != -1L)
                if (entity.getString("tableName") == "global_foods") {
                    values.put("foodId", "segundo-alimento")
                    old.insertOrThrow("global_foods", null, values)
                }
            }
            old.execSQL("INSERT INTO global_foods_fts(global_foods_fts) VALUES('rebuild')")
            old.version = fromVersion
            entities.associate { entity ->
                val table = entity.getString("tableName")
                table to old.rawQuery("SELECT * FROM `$table` ORDER BY rowid", null).rows()
            }
        } finally { old.close() }
        assertTrue(before.values.all { it.isNotEmpty() })
        assertEquals(2, before.getValue("global_foods").size)

        val shortContext = object : ContextWrapper(context) {
            override fun getApplicationContext(): Context = this
            override fun getDatabasePath(name: String) = File(directory, name)
            override fun openOrCreateDatabase(name: String, mode: Int, factory: SQLiteDatabase.CursorFactory?): SQLiteDatabase =
                SQLiteDatabase.openDatabase(getDatabasePath(name).absolutePath, factory, mode or SQLiteDatabase.CREATE_IF_NECESSARY)
            override fun openOrCreateDatabase(name: String, mode: Int, factory: SQLiteDatabase.CursorFactory?, errorHandler: DatabaseErrorHandler?): SQLiteDatabase =
                SQLiteDatabase.openDatabase(getDatabasePath(name).absolutePath, factory, mode or SQLiteDatabase.CREATE_IF_NECESSARY, errorHandler)
        }
        val upgraded = Room.databaseBuilder(shortContext, PublishedMfp31FixtureDatabase::class.java, file.name)
            .addMigrations(CALIBRATION_MIGRATION_29_30, nutritionDayStateMigration(30)).allowMainThreadQueries().build()
        try {
            val db = upgraded.openHelper.writableDatabase
            db.query("PRAGMA user_version").use { assertTrue(it.moveToFirst()); assertEquals(31, it.getInt(0)) }
            before.forEach { (table, rows) -> assertEquals("filas de $table", rows, db.query("SELECT * FROM `$table` ORDER BY rowid").rows()) }
            db.query("PRAGMA foreign_key_check").use { assertFalse(it.moveToFirst()) }
            db.query("PRAGMA integrity_check").use { assertTrue(it.moveToFirst()); assertEquals("ok", it.getString(0)) }
            db.query("SELECT name FROM sqlite_master WHERE type='index' AND name='index_global_foods_sourceRecordId'").use { assertTrue(it.moveToFirst()) }
            db.query("EXPLAIN QUERY PLAN SELECT * FROM global_foods WHERE sourceRecordId='dato-preservado'").use { cursor ->
                assertTrue(cursor.moveToFirst()); assertTrue(cursor.getString(3).contains("index_global_foods_sourceRecordId"))
            }
            runBlocking {
                val mark = NutritionDayMarkEntity("2026-10-10", "FASTING", "USER", "2026-10-10T12:00:00Z")
                val checkin = NutritionCheckInEntity("2026-10-05", "PENDING", null, "{}", null, mark.updatedAt)
                upgraded.nutritionDao().upsertDayMark(mark)
                upgraded.nutritionDao().upsertCheckIn(checkin)
                nutritionDayStateMigration(30).migrate(db)
                assertEquals(listOf(mark), upgraded.nutritionDao().getAllDayMarks())
                assertEquals(checkin, upgraded.nutritionDao().getCheckIn(checkin.weekKey))
            }
            val current = schema(31).getJSONArray("entities")
            for (table in listOf("nutrition_day_marks", "nutrition_checkins")) {
                val entity = (0 until current.length()).map { current.getJSONObject(it) }.single { it.getString("tableName") == table }
                db.query("SELECT sql FROM sqlite_master WHERE type='table' AND name=?", arrayOf(table)).use {
                    assertTrue(it.moveToFirst())
                    assertEquals(normalize(entity.getString("createSql").replace("\${TABLE_NAME}", table)), normalize(it.getString(0)))
                }
            }
        } finally { upgraded.close() }
    }

    private fun schema(version: Int): JSONObject {
        val relative = "schemas/com.example.kpkn.data.db.KpknDatabase/$version.json"
        val root = File(System.getProperty("user.dir")!!)
        val file = listOf(File(root, relative), File(root, "app/$relative"), File(root, "android-native/app/$relative"))
            .first { it.isFile }
        return JSONObject(file.readText()).getJSONObject("database")
    }

    private fun normalize(sql: String) = sql.replace(Regex("\\s+"), "").replace("IFNOTEXISTS", "")
    private fun Cursor.rows(): List<List<String?>> = use { cursor -> buildList {
        while (cursor.moveToNext()) add(List(cursor.columnCount) { if (cursor.isNull(it)) null else cursor.getString(it) })
    } }
}
