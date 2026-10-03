package com.example.kpkn.data.db

import android.app.Application
import android.content.ContentValues
import android.content.Context
import android.content.ContextWrapper
import android.database.Cursor
import android.database.DatabaseErrorHandler
import android.database.SQLException
import android.database.sqlite.SQLiteDatabase
import androidx.sqlite.db.SupportSQLiteDatabase
import androidx.test.core.app.ApplicationProvider
import kotlinx.coroutines.runBlocking
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertThrows
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Rule
import org.junit.Test
import org.junit.rules.TemporaryFolder
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import org.robolectric.shadows.ShadowLog
import java.io.ByteArrayOutputStream
import java.io.File
import java.io.PrintStream

/**
 * B11 / WP-S5: a database migrated from v<=5 keeps the `global_foods_ai/ad/au` triggers that MIGRATION_5_6 created
 * next to Room's own `room_fts_content_sync_global_foods_fts_*`. [GlobalFoodsFtsHygiene] drops the legacy set and
 * rebuilds the FTS index when the database is opened, with no migration. Every file database here is opened through
 * [KpknDatabase.getInstance] (the production builder), so that registration is covered as well; the in-memory
 * builder only gets a smoke check, because a fresh in-memory database can never carry legacy triggers.
 */
@RunWith(RobolectricTestRunner::class)
@Config(manifest = Config.NONE, sdk = [34], application = Application::class)
class GlobalFoodsFtsTriggerHygieneTest {
    @Rule
    @JvmField
    val temporaryFolder = TemporaryFolder()

    private val context: Context get() = ApplicationProvider.getApplicationContext()
    private lateinit var databaseDirectory: File
    private lateinit var shortPathContext: Context
    private lateinit var logOutput: ByteArrayOutputStream
    private var previousLogStream: PrintStream? = null

    @Before
    fun setUp() {
        KpknDatabase.closeInstance()
        logOutput = ByteArrayOutputStream()
        previousLogStream = ShadowLog.stream
        ShadowLog.stream = PrintStream(logOutput, true, "UTF-8")
        databaseDirectory = temporaryFolder.newFolder("room-fts-hygiene")
        check(databaseDirectory.absolutePath.length < 200) { "Room test database path must stay short on Windows." }
        shortPathContext = object : ContextWrapper(context) {
            override fun getApplicationContext(): Context = this

            override fun getDatabasePath(name: String): File = File(databaseDirectory, name)

            override fun openOrCreateDatabase(
                name: String,
                mode: Int,
                factory: SQLiteDatabase.CursorFactory?,
            ): SQLiteDatabase = SQLiteDatabase.openDatabase(
                getDatabasePath(name).absolutePath,
                factory,
                mode or SQLiteDatabase.CREATE_IF_NECESSARY,
            )

            override fun openOrCreateDatabase(
                name: String,
                mode: Int,
                factory: SQLiteDatabase.CursorFactory?,
                errorHandler: DatabaseErrorHandler?,
            ): SQLiteDatabase = SQLiteDatabase.openDatabase(
                getDatabasePath(name).absolutePath,
                factory,
                mode or SQLiteDatabase.CREATE_IF_NECESSARY,
                errorHandler,
            )
        }
    }

    @After
    fun tearDown() {
        ShadowLog.stream = previousLogStream
        KpknDatabase.closeInstance()
    }

    @Test
    fun legacy_triggers_are_dropped_on_open_and_the_fts_index_is_rebuilt_without_duplicates() {
        createCurrentDatabase()
        val firstRowId = withRawDatabase { db ->
            plantLegacyTriggers(db)
            insertOrphanFtsEntry(db)
            insertRawGlobalFood(db, "hallulla-1", "Hallulla amasada")
        }
        withRawDatabase { assertEquals(ALL_TRIGGERS, triggerNames(it)) }

        // Opening through Room runs GlobalFoodsFtsHygiene.onOpen.
        val room = openRoom()
        assertEquals(ROOM_TRIGGERS, triggerNames(room))
        assertEquals(listOf(firstRowId), matchDocids(room, "hallulla"))
        // A rebuild discards index entries that have no content row.
        assertEquals(emptyList<Long>(), matchDocids(room, "fantasma"))
        room.execSQL("INSERT INTO global_foods_fts(global_foods_fts) VALUES('integrity-check')")
        assertEquals(1, hygieneLogLines('I').size)

        runBlocking { nutritionDao().insertGlobalFoods(listOf(food("hallulla-2", "Hallulla integral"))) }
        val docids = matchDocids(room, "hallulla")
        assertEquals("no docid may appear twice", docids.toSet().size, docids.size)
        assertEquals(setOf(firstRowId, rowId(room, "hallulla-2")), docids.toSet())

        // A clean database is not touched (or logged) again: an orphan entry, which only a rebuild removes, survives.
        KpknDatabase.closeInstance()
        withRawDatabase { insertOrphanFtsEntry(it) }
        val reopened = openRoom()
        assertEquals(ROOM_TRIGGERS, triggerNames(reopened))
        assertEquals(listOf(ORPHAN_DOCID), matchDocids(reopened, "fantasma"))
        assertEquals(1, hygieneLogLines('I').size)
    }

    @Test
    fun upgrade_path_recreates_room_triggers_before_the_cleanup_runs() {
        createCurrentDatabase()
        val rowId = withRawDatabase { db ->
            assertEquals("This test rewinds a v28 database to 27: revisit it when the schema version changes", 28, db.version)
            // A database that reached v27 from v<=5: legacy triggers and, until Room's onPostMigrate, no Room ones.
            ROOM_TRIGGERS.forEach { db.execSQL("DROP TRIGGER `$it`") }
            plantLegacyTriggers(db)
            insertRawGlobalFood(db, "hallulla-1", "Hallulla amasada").also {
                db.version = 27 // MIGRATION_27_28 is idempotent, so a v28 file can be rewound to exercise onUpgrade.
            }
        }

        val room = openRoom() // onPreMigrate -> MIGRATION_27_28 -> onPostMigrate -> onOpen (hygiene)
        assertEquals(ROOM_TRIGGERS, triggerNames(room))
        assertEquals(listOf(rowId), matchDocids(room, "hallulla"))
        assertEquals(1, hygieneLogLines('I').size)
    }

    @Test
    fun after_the_cleanup_updates_and_deletes_work_and_keep_the_index_in_sync() {
        createCurrentDatabase()
        val rowId = withRawDatabase { db ->
            plantLegacyTriggers(db)
            insertRawGlobalFood(db, "hallulla-1", "Hallulla amasada").also {
                // The legacy _au/_ad triggers run an FTS5-only command on an FTS4 table, so SQLite rejects the write.
                assertThrows(SQLException::class.java) { db.execSQL("UPDATE global_foods SET usageCount = usageCount + 1") }
                assertThrows(SQLException::class.java) { db.execSQL("DELETE FROM global_foods") }
            }
        }

        val room = openRoom()
        runBlocking {
            val dao = nutritionDao()
            dao.incrementGlobalFoodUsage("hallulla-1", "2026-10-03T00:00:00Z")
            assertEquals(1, dao.getGlobalFoodById("hallulla-1")!!.usageCount)
            assertEquals(listOf(rowId), matchDocids(room, "hallulla"))
            dao.clearGlobalFoods()
            assertEquals(0, dao.getGlobalFoodCount())
            assertEquals(emptyList<Long>(), matchDocids(room, "hallulla"))
        }
    }

    @Test
    fun fresh_databases_without_legacy_triggers_are_left_untouched() {
        val room = openRoom()
        assertEquals(ROOM_TRIGGERS, triggerNames(room))
        runBlocking { nutritionDao().insertGlobalFoods(listOf(food("hallulla-1", "Hallulla amasada"))) }
        KpknDatabase.closeInstance()
        withRawDatabase { insertOrphanFtsEntry(it) } // only a rebuild would remove it

        val reopened = openRoom()
        assertEquals(ROOM_TRIGGERS, triggerNames(reopened))
        assertEquals(listOf(ORPHAN_DOCID), matchDocids(reopened, "fantasma"))
        assertEquals(1, matchDocids(reopened, "hallulla").size)
        assertTrue(hygieneLogLines('I').isEmpty() && hygieneLogLines('W').isEmpty())

        val inMemory = KpknDatabase.createInMemory(context)
        try {
            assertEquals(ROOM_TRIGGERS, triggerNames(inMemory.openHelper.writableDatabase))
        } finally {
            inMemory.close()
        }
    }

    @Test
    fun legacy_triggers_are_kept_when_room_sync_triggers_are_missing() {
        createCurrentDatabase()
        withRawDatabase { db ->
            ROOM_TRIGGERS.forEach { db.execSQL("DROP TRIGGER `$it`") }
            plantLegacyTriggers(db)
            insertOrphanFtsEntry(db)
        }

        // Dropping the legacy triggers now would leave the index with no sync trigger at all.
        val room = openRoom()
        assertEquals(LEGACY_TRIGGERS, triggerNames(room))
        assertEquals(listOf(ORPHAN_DOCID), matchDocids(room, "fantasma"))
        assertTrue(hygieneLogLines('I').isEmpty())
        assertEquals(1, hygieneLogLines('W').size)
    }

    @Test
    fun a_failed_rebuild_rolls_back_the_trigger_drops_and_does_not_block_the_open() {
        createCurrentDatabase()
        withRawDatabase { db ->
            plantLegacyTriggers(db)
            db.execSQL("DROP TABLE global_foods_fts") // makes the rebuild fail after the triggers were dropped
        }

        val room = openRoom()
        assertEquals(ALL_TRIGGERS, triggerNames(room))
        assertEquals(0L, room.query("SELECT COUNT(*) FROM global_foods").longs().single())
        assertTrue(hygieneLogLines('I').isEmpty())
        assertEquals(1, hygieneLogLines('W').size)
    }

    /** Creates the current-version database file through the production builder, then closes it. */
    private fun createCurrentDatabase() {
        assertEquals(ROOM_TRIGGERS, triggerNames(openRoom()))
        KpknDatabase.closeInstance()
    }

    private fun openRoom(): SupportSQLiteDatabase = KpknDatabase.getInstance(shortPathContext).openHelper.writableDatabase

    private fun nutritionDao() = KpknDatabase.getInstance(shortPathContext).nutritionDao()

    private fun <T> withRawDatabase(block: (SQLiteDatabase) -> T): T {
        val file = databaseDirectory.listFiles { candidate: File -> candidate.extension == "db" }!!.single()
        return SQLiteDatabase.openDatabase(file.absolutePath, null, SQLiteDatabase.OPEN_READWRITE).use(block)
    }

    private fun plantLegacyTriggers(db: SQLiteDatabase) = LEGACY_TRIGGER_SQL.forEach { db.execSQL(it) }

    private fun insertOrphanFtsEntry(db: SQLiteDatabase) =
        db.execSQL("INSERT INTO global_foods_fts(docid, name, brand) VALUES ($ORPHAN_DOCID, 'fantasma', 'huerfano')")

    /**
     * Inserts a row with plain SQL. Only the identifying columns are set; every other NOT NULL column gets a
     * neutral value read from the live schema, so adding a column to GlobalFoodEntity does not break this test.
     */
    private fun insertRawGlobalFood(db: SQLiteDatabase, foodId: String, name: String): Long {
        val values = ContentValues()
        db.rawQuery("PRAGMA table_info(`global_foods`)", null).use { cursor ->
            while (cursor.moveToNext()) {
                if (cursor.getInt(cursor.getColumnIndexOrThrow("notnull")) != 1) continue
                val column = cursor.getString(cursor.getColumnIndexOrThrow("name"))
                val isText = cursor.getString(cursor.getColumnIndexOrThrow("type")) == "TEXT"
                if (isText) values.put(column, "") else values.put(column, 0)
            }
        }
        values.put("foodId", foodId)
        values.put("name", name)
        values.put("brand", "Panaderia Test")
        values.put("normalizedName", name.lowercase())
        return db.insertOrThrow("global_foods", null, values)
    }

    private fun food(foodId: String, name: String) =
        GlobalFoodEntity(foodId = foodId, name = name, brand = "Panaderia Test", normalizedName = name.lowercase())

    private fun triggerNames(db: SupportSQLiteDatabase): List<String> = db.query(TRIGGERS_SQL).strings()

    private fun triggerNames(db: SQLiteDatabase): List<String> = db.rawQuery(TRIGGERS_SQL, null).strings()

    private fun matchDocids(db: SupportSQLiteDatabase, term: String): List<Long> = db.query(MATCH_SQL, arrayOf(term)).longs()

    private fun rowId(db: SupportSQLiteDatabase, foodId: String): Long =
        db.query("SELECT rowid FROM global_foods WHERE foodId = ?", arrayOf(foodId)).longs().single()

    // ShadowLog.getLogs() returns a Guava list; the log stream avoids needing Guava on the test compile classpath.
    private fun hygieneLogLines(level: Char): List<String> =
        logOutput.toString("UTF-8").lines().filter { it.contains("$level/${GlobalFoodsFtsHygiene.TAG}:") }

    private fun Cursor.strings(): List<String> = use { buildList { while (moveToNext()) add(getString(0)) } }

    private fun Cursor.longs(): List<Long> = use { buildList { while (moveToNext()) add(getLong(0)) } }

    private companion object {
        const val ORPHAN_DOCID = 424242L
        const val TRIGGERS_SQL =
            "SELECT name FROM sqlite_master WHERE type = 'trigger' AND tbl_name = 'global_foods' ORDER BY name"
        const val MATCH_SQL = "SELECT docid FROM global_foods_fts WHERE global_foods_fts MATCH ? ORDER BY docid"

        val ROOM_TRIGGERS = listOf("AFTER_INSERT", "AFTER_UPDATE", "BEFORE_DELETE", "BEFORE_UPDATE")
            .map { "room_fts_content_sync_global_foods_fts_$it" }
        val LEGACY_TRIGGERS = listOf("global_foods_ad", "global_foods_ai", "global_foods_au")
        val ALL_TRIGGERS = (ROOM_TRIGGERS + LEGACY_TRIGGERS).sorted()

        // Verbatim from MIGRATION_5_6, step 4.
        val LEGACY_TRIGGER_SQL = listOf(
            """
            CREATE TRIGGER IF NOT EXISTS `global_foods_ai`
            AFTER INSERT ON `global_foods` BEGIN
              INSERT INTO `global_foods_fts`(`rowid`, `name`, `brand`)
              VALUES (new.`rowid`, new.`name`, new.`brand`);
            END
            """.trimIndent(),
            """
            CREATE TRIGGER IF NOT EXISTS `global_foods_ad`
            AFTER DELETE ON `global_foods` BEGIN
              INSERT INTO `global_foods_fts`(`global_foods_fts`, `rowid`, `name`, `brand`)
              VALUES ('delete', old.`rowid`, old.`name`, old.`brand`);
            END
            """.trimIndent(),
            """
            CREATE TRIGGER IF NOT EXISTS `global_foods_au`
            AFTER UPDATE ON `global_foods` BEGIN
              INSERT INTO `global_foods_fts`(`global_foods_fts`, `rowid`, `name`, `brand`)
              VALUES ('delete', old.`rowid`, old.`name`, old.`brand`);
              INSERT INTO `global_foods_fts`(`rowid`, `name`, `brand`)
              VALUES (new.`rowid`, new.`name`, new.`brand`);
            END
            """.trimIndent(),
        )
    }
}
