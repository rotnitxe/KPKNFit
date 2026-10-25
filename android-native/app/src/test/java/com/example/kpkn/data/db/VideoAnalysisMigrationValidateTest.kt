package com.example.kpkn.data.db

import android.app.Application
import android.content.Context
import android.content.ContextWrapper
import android.database.Cursor
import android.database.DatabaseErrorHandler
import android.database.sqlite.SQLiteDatabase
import androidx.room.Room
import androidx.sqlite.db.SupportSQLiteDatabase
import androidx.test.core.app.ApplicationProvider
import org.json.JSONObject
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.rules.TemporaryFolder
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import java.io.File

/**
 * Valida la migración 28→29 contra el esquema que Room espera de verdad, con una base física v28 creada desde el
 * `schemas/…/28.json` exportado (sin `MigrationTestHelper`: en los tests de JVM el `AssetManager` solo trae los assets de
 * la app, no `schemas/`, y el helper no encuentra el JSON).
 *
 * Abrir la base por Room ejecuta la migración REAL (`videoAnalysisMigration(28)`) y después `onValidateSchema` compara las
 * 44 tablas con las del código, columna a columna, índice a índice y clave foránea a clave foránea: si el DDL de la
 * migración se desvía de [VideoAnalysisEntity] o de [WorkoutMediaEntity], la apertura falla. Además se comprueba que ni
 * una sola fila de las tablas que ya existían cambia, y que el DDL coincide con el `29.json` que genera KSP.
 */
@RunWith(RobolectricTestRunner::class)
@Config(manifest = Config.NONE, sdk = [34], application = Application::class)
class VideoAnalysisMigrationValidateTest {
    @Rule
    @JvmField
    val temporaryFolder = TemporaryFolder()

    private val context: Context get() = ApplicationProvider.getApplicationContext()

    /** Un contexto con la carpeta de bases en una ruta corta: en Windows las rutas largas de Robolectric rompen SQLite. */
    private fun shortPathContext(databaseDirectory: File): Context = object : ContextWrapper(context) {
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

    @Test
    fun a_full_room_28_database_upgrades_to_29_validated_by_room_with_every_existing_row_intact() {
        val databaseName = "validate-video-analysis-28-29.db"
        val databaseDirectory = temporaryFolder.newFolder("room-28-29")
        val databaseFile = File(databaseDirectory, databaseName)
        check(databaseFile.absolutePath.length < 200) { "Room migration test database path must stay short on Windows." }

        createPhysicalRoom(28, databaseFile)
        val legacy = SQLiteDatabase.openDatabase(databaseFile.absolutePath, null, SQLiteDatabase.OPEN_READWRITE)
        val before = try {
            seedUserData(legacy)
            snapshotUserTables { sql -> legacy.rawQuery(sql, null).readRows() }
        } finally {
            legacy.close()
        }
        // Los datos de usuario sembrados están de verdad en las tablas que importan.
        assertEquals(3, before.getValue("workout_media").size)
        assertEquals(2, before.getValue("workout_logs").size)
        assertEquals(1, before.getValue("workout_media_session_associations").size)
        assertEquals(1, before.getValue("programs").size)

        val upgraded = Room.databaseBuilder(shortPathContext(databaseDirectory), PublishedMfp31FixtureDatabase::class.java, databaseName)
            .addMigrations(videoAnalysisMigration(28), CALIBRATION_MIGRATION_29_30, nutritionDayStateMigration(30))
            .allowMainThreadQueries()
            .build()
        try {
            // Abrir por Room = migración real + validación completa del esquema integrado v31.
            val db = upgraded.openHelper.writableDatabase
            assertEquals(31L, scalar(db, "PRAGMA user_version"))

            val after = snapshotUserTables { sql -> db.query(sql).readRows() }
            assertEquals("ninguna tabla anterior puede perder ni cambiar filas", before.keys, after.keys)
            for ((table, rows) in before) assertEquals("filas de $table", rows, after.getValue(table))

            // Las dos columnas nuevas llegan vacías en todos los medios.
            assertEquals(
                3L,
                scalar(db, "SELECT COUNT(*) FROM workout_media WHERE catalogDefinitionId IS NULL AND catalogConfigurationId IS NULL"),
            )
            assertEquals(0L, scalar(db, "SELECT COUNT(*) FROM video_analysis"))
            assertEquals(0L, scalar(db, "SELECT COUNT(*) FROM calibration_decisions"))

            db.query("PRAGMA foreign_key_check").use { assertFalse("claves foráneas rotas", it.moveToFirst()) }
            db.query("PRAGMA integrity_check").use { cursor ->
                assertTrue(cursor.moveToFirst())
                assertEquals("ok", cursor.getString(0))
            }

            // La caché nueva funciona de verdad sobre la base migrada: el DAO escribe y la cascada limpia.
            kotlinx.coroutines.runBlocking {
                upgraded.videoAnalysisDao().upsert(
                    VideoAnalysisEntity(
                        mediaId = "media-video",
                        status = "DONE",
                        progressPct = 100,
                        engineVersion = 1,
                        poseVersion = 1,
                        exerciseKey = "lower_squat",
                        recordedAtMs = 1780000000200L,
                        flagsJson = "{}",
                        createdAtMs = 1780000009000L,
                        updatedAtMs = 1780000009000L,
                    ),
                )
                assertEquals(1, upgraded.videoAnalysisDao().count())
                upgraded.workoutMediaDao().delete("media-video")
                assertEquals(0, upgraded.videoAnalysisDao().count())
                assertEquals(2, upgraded.workoutMediaDao().count())
            }
        } finally {
            upgraded.close()
            databaseDirectory.deleteRecursively()
        }
    }

    @Test
    fun the_migrated_ddl_is_the_one_in_the_exported_29_json() {
        val databaseName = "validate-video-analysis-ddl.db"
        val databaseDirectory = temporaryFolder.newFolder("room-28-29-ddl")
        createPhysicalRoom(28, File(databaseDirectory, databaseName))

        val upgraded = Room.databaseBuilder(shortPathContext(databaseDirectory), PublishedMfp31FixtureDatabase::class.java, databaseName)
            .addMigrations(videoAnalysisMigration(28), CALIBRATION_MIGRATION_29_30, nutritionDayStateMigration(30))
            .allowMainThreadQueries()
            .build()
        try {
            val db = upgraded.openHelper.writableDatabase
            val schema = JSONObject(findExportedSchema(29).readText(Charsets.UTF_8)).getJSONObject("database")
            assertEquals(29, schema.getInt("version"))
            val entities = schema.getJSONArray("entities")

            val analysis = (0 until entities.length()).map { entities.getJSONObject(it) }.single { it.getString("tableName") == "video_analysis" }
            // La tabla nueva: el createSql del JSON, tal cual (sqlite_master lo guarda sin «IF NOT EXISTS»).
            assertEquals(
                normalizeSql(analysis.getString("createSql"), "video_analysis"),
                normalizeSql(masterSql(db, "table", "video_analysis"), "video_analysis"),
            )
            // Y la misma lista de columnas, con su afinidad y su NOT NULL.
            assertEquals(columnsOf(analysis), columnsOfTable(db, "video_analysis"))

            // Sus índices: ni uno más ni uno menos, con el mismo DDL.
            val analysisIndices = analysis.getJSONArray("indices")
            assertEquals(2, analysisIndices.length())
            for (i in 0 until analysisIndices.length()) {
                val index = analysisIndices.getJSONObject(i)
                assertEquals(
                    normalizeSql(index.getString("createSql"), "video_analysis"),
                    normalizeSql(masterSql(db, "index", index.getString("name")), "video_analysis"),
                )
            }

            // Y la clave foránea con CASCADE.
            val foreignKeys = analysis.getJSONArray("foreignKeys")
            assertEquals(1, foreignKeys.length())
            val fk = foreignKeys.getJSONObject(0)
            assertEquals("workout_media", fk.getString("table"))
            assertEquals("CASCADE", fk.getString("onDelete"))

            // workout_media: las dos columnas nuevas y el índice nuevo, tal como los exporta KSP.
            val media = (0 until entities.length()).map { entities.getJSONObject(it) }.single { it.getString("tableName") == "workout_media" }
            assertEquals(columnsOf(media), columnsOfTable(db, "workout_media"))
            val mediaIndices = media.getJSONArray("indices")
            val sessionKeyIndex = (0 until mediaIndices.length()).map { mediaIndices.getJSONObject(it) }
                .single { it.getString("name") == "index_workout_media_sessionKey" }
            assertEquals(
                normalizeSql(sessionKeyIndex.getString("createSql"), "workout_media"),
                normalizeSql(masterSql(db, "index", "index_workout_media_sessionKey"), "workout_media"),
            )
        } finally {
            upgraded.close()
            databaseDirectory.deleteRecursively()
        }
    }

    // ---- Datos de usuario sembrados en la base v28 --------------------------------------------------------------------

    private fun seedUserData(legacy: SQLiteDatabase) {
        val programJson = """{"id":"program-1","marker":"progreso-α","sessions":[{"id":"session-1","name":"Fuerza"}]}"""
        legacy.execSQL("INSERT INTO programs (id, name, data) VALUES (?, ?, ?)", arrayOf("program-1", "Programa α", programJson))
        legacy.execSQL("INSERT INTO active_program (rowId, data) VALUES (1, ?)", arrayOf("""{"programId":"program-1","cursor":3}"""))
        legacy.execSQL("INSERT INTO ongoing_workout (rowId, data) VALUES (1, ?)", arrayOf("""{"programId":"program-1","startTimeMs":1780000000000}"""))
        legacy.execSQL(
            "INSERT INTO session_templates (id, sourceType, name, sortOrder, isArchived, createdAt, data) VALUES (?, ?, ?, ?, ?, ?, ?)",
            arrayOf("session-1", "USER", "Día α", 3, 0, "2026-09-30T00:00:00Z", """{"id":"session-1","name":"Día α"}"""),
        )
        for (id in listOf("log-1", "log-2")) {
            legacy.execSQL(
                "INSERT INTO workout_logs (id, programId, sessionId, date, data) VALUES (?, ?, ?, ?, ?)",
                arrayOf(id, "program-1", "session-1", "2026-09-30T00:00:00Z", """{"id":"$id","notes":"historial sin cambios"}"""),
            )
        }
        legacy.execSQL(
            """
            INSERT INTO workout_media (
                id, kind, filePath, thumbPath, createdAtMs, sessionKey, workoutLogId, programId, sessionId, sessionName,
                exerciseId, canonicalExerciseId, exerciseName, setIndex, side, weightKg, reps, isPr, durationMs, width,
                height, caption, poseTrackPath
            ) VALUES (
                'media-photo', 'PHOTO', '/private/media/photo.jpg', '/private/thumbs/photo.jpg', 1780000000100,
                'program-1::session-1::1780000000000', 'log-1', 'program-1', 'session-1', 'Día α', 'squat', 'lower_squat',
                'Sentadilla', 2, 'left', 82.5, 5, 1, NULL, 1200, 640, 'captura α', '/private/media/pose.json'
            )
            """.trimIndent(),
        )
        legacy.execSQL(
            """
            INSERT INTO workout_media (id, kind, filePath, createdAtMs, sessionKey, workoutLogId, programId, sessionId,
                exerciseId, canonicalExerciseId, exerciseName, setIndex, weightKg, reps, isPr, durationMs, width, height)
            VALUES ('media-video', 'VIDEO', '/private/media/video.mp4', 1780000000200, 'program-1::session-1::1780000000000',
                NULL, 'program-1', 'session-1', 'squat', 'lower_squat', 'Sentadilla', 3, 90.0, 8, 0, 41000, 1080, 1920)
            """.trimIndent(),
        )
        legacy.execSQL(
            "INSERT INTO workout_media (id, kind, filePath, createdAtMs, isPr) VALUES ('media-bare', 'PHOTO', '/private/media/bare.jpg', 1780000000300, 0)",
        )
        legacy.execSQL(
            "INSERT INTO workout_media_session_associations (sessionKey, workoutLogId) VALUES ('program-1::session-1::1780000000000', 'log-1')",
        )
    }

    // ---- Esquemas exportados ------------------------------------------------------------------------------------------

    private fun findExportedSchema(version: Int): File {
        val relativePath = "schemas/com.example.kpkn.data.db.KpknDatabase/$version.json"
        val workingDirectory = File(System.getProperty("user.dir") ?: ".").canonicalFile
        val candidates = listOf(
            File(workingDirectory, relativePath),
            File(workingDirectory, "app/$relativePath"),
            File(workingDirectory, "android-native/app/$relativePath"),
            File(workingDirectory, "../android-native/app/$relativePath"),
        )
        return candidates.firstOrNull { it.isFile }
            ?: error("Room schema $version export was not found from ${workingDirectory.absolutePath}")
    }

    /** Crea el archivo de una base Room de [version] tal como la dejaría Room: tablas, índices y `room_master_table`. */
    private fun createPhysicalRoom(version: Int, databaseFile: File) {
        val databaseSchema = JSONObject(findExportedSchema(version).readText(Charsets.UTF_8)).getJSONObject("database")
        assertEquals(version, databaseSchema.getInt("version"))
        val legacy = SQLiteDatabase.openOrCreateDatabase(databaseFile, null)
        try {
            val entities = databaseSchema.getJSONArray("entities")
            for (index in 0 until entities.length()) {
                val entity = entities.getJSONObject(index)
                legacy.execSQL(entity.getString("createSql").replace("\${TABLE_NAME}", entity.getString("tableName")))
            }
            for (index in 0 until entities.length()) {
                val indices = entities.getJSONObject(index).optJSONArray("indices") ?: continue
                for (indexInTable in 0 until indices.length()) {
                    val createSql = indices.getJSONObject(indexInTable).getString("createSql")
                    legacy.execSQL(createSql.replace("\${TABLE_NAME}", entities.getJSONObject(index).getString("tableName")))
                }
            }
            databaseSchema.getJSONArray("setupQueries").let { setupQueries ->
                for (index in 0 until setupQueries.length()) legacy.execSQL(setupQueries.getString(index))
            }
            legacy.version = version
        } finally {
            legacy.close()
        }
    }

    // ---- Comparaciones ------------------------------------------------------------------------------------------------

    /** Sin espacios y sin «IF NOT EXISTS» (SQLite no lo guarda en sqlite_master), con el nombre de la tabla puesto. */
    private fun normalizeSql(sql: String, table: String): String =
        sql.replace("\${TABLE_NAME}", table).replace(Regex("\\s+"), "").replace("IFNOTEXISTS", "")

    private fun masterSql(db: SupportSQLiteDatabase, type: String, name: String): String =
        db.query("SELECT sql FROM sqlite_master WHERE type = ? AND name = ?", arrayOf<Any?>(type, name)).use { cursor ->
            check(cursor.moveToFirst()) { "No existe $type $name" }
            cursor.getString(0)
        }

    /** Columnas de una entidad del JSON exportado: «nombre AFINIDAD NOT NULL?», en orden. */
    private fun columnsOf(entity: JSONObject): List<String> {
        val fields = entity.getJSONArray("fields")
        return (0 until fields.length()).map { fields.getJSONObject(it) }.map { field ->
            val notNull = field.optBoolean("notNull", false)
            "${field.getString("columnName")} ${field.getString("affinity")}${if (notNull) " NOT NULL" else ""}"
        }
    }

    private fun columnsOfTable(db: SupportSQLiteDatabase, table: String): List<String> =
        // PRAGMA table_info: cid, name, type, notnull, dflt_value, pk (Robolectric usa un SQLite sin las funciones `pragma_…()`).
        db.query("PRAGMA table_info('$table')").readRows()
            .map { "${it[1]} ${it[2]}${if (it[3] == "1") " NOT NULL" else ""}" }

    private fun scalar(db: SupportSQLiteDatabase, sql: String): Long =
        db.query(sql).use { cursor -> check(cursor.moveToFirst()); cursor.getLong(0) }

    private fun Cursor.readRows(): List<List<String?>> = use { cursor ->
        buildList {
            while (cursor.moveToNext()) {
                add(List(cursor.columnCount) { index -> if (cursor.isNull(index)) null else cursor.getString(index) })
            }
        }
    }

    /**
     * El contenido de todas las tablas de usuario que existían en la v28, fila a fila y en el orden de inserción
     * (`workout_media` solo con sus columnas de la v28). Se excluyen las de Room (`room_…`), las de SQLite y la búsqueda de texto.
     */
    private fun snapshotUserTables(query: (String) -> List<List<String?>>): Map<String, List<List<String?>>> {
        val tables = query("SELECT name FROM sqlite_master WHERE type = 'table' ORDER BY name").map { it.single()!! }
            .filterNot { it.startsWith("sqlite_") || it.startsWith("room_") || it.startsWith("global_foods_fts") }
            .filterNot { it == "android_metadata" || it == "video_analysis" }
            // La comparación sigue limitada a las tablas anteriores a v29.
            .filterNot { it == "calibration_decisions" || it == "nutrition_day_marks" || it == "nutrition_checkins" }
        return tables.associateWith { table ->
            val columns = if (table == "workout_media") {
                V28_MEDIA_COLUMNS
            } else {
                query("PRAGMA table_info('$table')").map { it[1]!! }
            }
            query("SELECT ${columns.joinToString { "`$it`" }} FROM `$table` ORDER BY rowid")
        }
    }

    private companion object {
        val V28_MEDIA_COLUMNS = listOf(
            "id", "kind", "filePath", "thumbPath", "createdAtMs", "sessionKey", "workoutLogId", "programId", "sessionId",
            "sessionName", "exerciseId", "canonicalExerciseId", "exerciseName", "setIndex", "side", "weightKg", "reps", "isPr",
            "durationMs", "width", "height", "caption", "poseTrackPath",
        )
    }
}
