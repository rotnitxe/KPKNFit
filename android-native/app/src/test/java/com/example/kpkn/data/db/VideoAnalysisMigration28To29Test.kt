package com.example.kpkn.data.db

import android.app.Application
import android.database.sqlite.SQLiteException
import androidx.sqlite.db.SupportSQLiteDatabase
import androidx.sqlite.db.SupportSQLiteOpenHelper
import androidx.sqlite.db.framework.FrameworkSQLiteOpenHelperFactory
import androidx.test.core.app.ApplicationProvider
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertThrows
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

/**
 * La migración 28→29 con SQL en bruto (patrón de `WorkoutMediaMigration27To28Test`): una base v28 mínima con sus
 * medios, la migración encima, y se comprueba que solo AÑADE (columnas nulables, índice y tabla) y que la tabla nueva
 * cuelga de `workout_media` con CASCADE. El esquema exacto que valida Room lo comprueba `VideoAnalysisMigrationValidateTest`.
 */
@RunWith(RobolectricTestRunner::class)
@Config(manifest = Config.NONE, sdk = [34], application = Application::class)
class VideoAnalysisMigration28To29Test {
    private val v28MediaColumns = listOf(
        "id", "kind", "filePath", "thumbPath", "createdAtMs", "sessionKey", "workoutLogId", "programId", "sessionId",
        "sessionName", "exerciseId", "canonicalExerciseId", "exerciseName", "setIndex", "side", "weightKg", "reps", "isPr",
        "durationMs", "width", "height", "caption", "poseTrackPath",
    )

    /** `video_analysis` según §3.9: nombre, tipo y `NOT NULL`, en orden. Escrito aparte del DDL de la migración. */
    private val expectedAnalysisColumns = listOf(
        "mediaId TEXT NOT NULL", "status TEXT NOT NULL", "progressPct INTEGER NOT NULL", "errorCode TEXT",
        "engineVersion INTEGER NOT NULL", "poseVersion INTEGER NOT NULL", "poseEngine TEXT", "profileId TEXT",
        "exerciseKey TEXT NOT NULL", "configurationKey TEXT", "exerciseName TEXT", "setKey TEXT", "loadKg REAL",
        "recordedAtMs INTEGER NOT NULL", "sourceFps REAL", "analyzedFps REAL", "viewKind TEXT", "confidence TEXT",
        "repCount INTEGER", "partialReps INTEGER", "failedReps INTEGER",
        "tutMs INTEGER", "tempoEccMs INTEGER", "tempoPauseAfterEccMs INTEGER", "tempoConMs INTEGER", "tempoPauseAfterConMs INTEGER",
        "romMeanDeg REAL", "romCvPct REAL",
        "velocityUnit TEXT", "velocityLossPct REAL", "mcvFirst REAL", "mcvBest REAL", "mcvLast REAL", "peakBest REAL",
        "scaleSource TEXT", "plateDiameterMm INTEGER", "barDeviation REAL", "barDeviationUnit TEXT", "trunkMeanDeg REAL",
        "flagsJson TEXT NOT NULL", "reportPath TEXT", "createdAtMs INTEGER NOT NULL", "updatedAtMs INTEGER NOT NULL",
    )

    private fun withV28Database(block: (SupportSQLiteDatabase) -> Unit) {
        val context = ApplicationProvider.getApplicationContext<Application>()
        val dbName = "migration-video-analysis-28-29.db"
        context.deleteDatabase(dbName)
        val helper = FrameworkSQLiteOpenHelperFactory().create(
            SupportSQLiteOpenHelper.Configuration.builder(context)
                .name(dbName)
                .callback(object : SupportSQLiteOpenHelper.Callback(28) {
                    override fun onCreate(db: SupportSQLiteDatabase) {
                        // `workout_media` tal como la deja la v28 (migración 25→26 y esquema exportado 28).
                        db.execSQL(
                            """
                            CREATE TABLE `workout_media` (
                                `id` TEXT NOT NULL, `kind` TEXT NOT NULL, `filePath` TEXT NOT NULL, `thumbPath` TEXT,
                                `createdAtMs` INTEGER NOT NULL, `sessionKey` TEXT, `workoutLogId` TEXT, `programId` TEXT,
                                `sessionId` TEXT, `sessionName` TEXT, `exerciseId` TEXT, `canonicalExerciseId` TEXT,
                                `exerciseName` TEXT, `setIndex` INTEGER, `side` TEXT, `weightKg` REAL, `reps` INTEGER,
                                `isPr` INTEGER NOT NULL, `durationMs` INTEGER, `width` INTEGER, `height` INTEGER,
                                `caption` TEXT, `poseTrackPath` TEXT,
                                PRIMARY KEY(`id`)
                            )
                            """.trimIndent(),
                        )
                        db.execSQL("CREATE INDEX `index_workout_media_workoutLogId` ON `workout_media` (`workoutLogId`)")
                        db.execSQL("CREATE INDEX `index_workout_media_canonicalExerciseId` ON `workout_media` (`canonicalExerciseId`)")
                        db.execSQL("CREATE INDEX `index_workout_media_createdAtMs` ON `workout_media` (`createdAtMs`)")
                        db.execSQL("CREATE INDEX `index_workout_media_isPr` ON `workout_media` (`isPr`)")
                    }

                    override fun onUpgrade(db: SupportSQLiteDatabase, oldVersion: Int, newVersion: Int) = Unit
                })
                .build(),
        )
        try {
            block(helper.writableDatabase)
        } finally {
            helper.close()
            context.deleteDatabase(dbName)
        }
    }

    private fun insertFullMedia(db: SupportSQLiteDatabase, id: String, sessionKey: String?) {
        db.execSQL(
            """
            INSERT INTO workout_media (
                id, kind, filePath, thumbPath, createdAtMs, sessionKey, workoutLogId, programId, sessionId, sessionName,
                exerciseId, canonicalExerciseId, exerciseName, setIndex, side, weightKg, reps, isPr, durationMs, width,
                height, caption, poseTrackPath
            ) VALUES (?, 'VIDEO', ?, '/private/thumbs/t.jpg', 1780000000100, ?, 'log-1', 'prog-1', 'ses-1', 'Día α',
                'squat', 'lower_squat', 'Sentadilla', 2, 'left', 82.5, 5, 1, 41000, 1080, 1920, 'serie α', '/private/pose.json')
            """.trimIndent(),
            arrayOf(id, "/private/media/$id.mp4", sessionKey),
        )
    }

    private fun insertMinimalMedia(db: SupportSQLiteDatabase, id: String) {
        db.execSQL(
            "INSERT INTO workout_media (id, kind, filePath, createdAtMs, isPr) VALUES (?, 'PHOTO', ?, 1780000000200, 0)",
            arrayOf(id, "/private/media/$id.jpg"),
        )
    }

    private fun insertAnalysis(db: SupportSQLiteDatabase, mediaId: String, status: String = "DONE") {
        db.execSQL(
            """
            INSERT INTO video_analysis (
                mediaId, status, progressPct, engineVersion, poseVersion, exerciseKey, recordedAtMs, flagsJson, createdAtMs, updatedAtMs
            ) VALUES (?, ?, 100, 1, 1, 'lower_squat', 1780000000100, '{}', 1780000009000, 1780000009000)
            """.trimIndent(),
            arrayOf(mediaId, status),
        )
    }

    private fun rows(db: SupportSQLiteDatabase, sql: String, args: Array<Any?> = emptyArray()): List<List<String?>> =
        db.query(sql, args).use { cursor ->
            buildList {
                while (cursor.moveToNext()) {
                    add(List(cursor.columnCount) { index -> if (cursor.isNull(index)) null else cursor.getString(index) })
                }
            }
        }

    private fun scalar(db: SupportSQLiteDatabase, sql: String, args: Array<Any?> = emptyArray()): Long =
        db.query(sql, args).use { cursor -> check(cursor.moveToFirst()); cursor.getLong(0) }

    // Robolectric usa un SQLite antiguo (sqlite4java) sin las funciones `pragma_…()`: se leen con PRAGMA a secas.

    /** Una fila por columna, en orden: nombre, tipo, notnull, valor por defecto y pk (los `PRAGMA table_info` sin su `cid`). */
    private fun tableColumns(db: SupportSQLiteDatabase, table: String): List<List<String?>> =
        rows(db, "PRAGMA table_info('$table')").map { it.drop(1) }

    /** Los índices creados con CREATE INDEX (los `sqlite_autoindex_…` de las claves primarias TEXT no cuentan). */
    private fun indexNames(db: SupportSQLiteDatabase, table: String): Set<String> =
        rows(db, "PRAGMA index_list('$table')").map { it[1]!! }.filter { it.startsWith("index_") }.toSet()

    private fun indexColumns(db: SupportSQLiteDatabase, index: String): List<String> =
        rows(db, "PRAGMA index_info('$index')").sortedBy { it[0]!!.toInt() }.map { it[2]!! }

    @Test
    fun the_migration_only_adds_and_keeps_every_existing_media_row_byte_for_byte() = withV28Database { db ->
        insertFullMedia(db, "media-full", "key-1")
        insertMinimalMedia(db, "media-min")
        val before = rows(db, "SELECT ${v28MediaColumns.joinToString()} FROM workout_media ORDER BY id")
        assertEquals(2, before.size)

        videoAnalysisMigration(28).migrate(db)

        // Las filas de antes, columna a columna, y las dos columnas nuevas vacías.
        assertEquals(before, rows(db, "SELECT ${v28MediaColumns.joinToString()} FROM workout_media ORDER BY id"))
        assertEquals(
            listOf(listOf(null, null), listOf(null, null)),
            rows(db, "SELECT catalogDefinitionId, catalogConfigurationId FROM workout_media ORDER BY id"),
        )
        assertEquals(2L, scalar(db, "SELECT COUNT(*) FROM workout_media"))

        // Las columnas nuevas son TEXT nulables, sin DEFAULT (Room no espera ninguno), y van al final.
        val columns = tableColumns(db, "workout_media")
        assertEquals(v28MediaColumns + listOf("catalogDefinitionId", "catalogConfigurationId"), columns.map { it[0]!! })
        assertEquals(listOf("catalogDefinitionId", "TEXT", "0", null, "0"), columns[23])
        assertEquals(listOf("catalogConfigurationId", "TEXT", "0", null, "0"), columns[24])

        // Los índices de antes siguen y hay uno nuevo por sessionKey.
        assertEquals(
            setOf(
                "index_workout_media_workoutLogId", "index_workout_media_canonicalExerciseId",
                "index_workout_media_createdAtMs", "index_workout_media_isPr", "index_workout_media_sessionKey",
            ),
            indexNames(db, "workout_media"),
        )
        assertEquals(listOf("sessionKey"), indexColumns(db, "index_workout_media_sessionKey"))
        // PRAGMA index_list: seq, name, unique. El índice nuevo no es único.
        assertEquals("0", rows(db, "PRAGMA index_list('workout_media')").single { it[1] == "index_workout_media_sessionKey" }[2])
    }

    @Test
    fun video_analysis_has_the_columns_of_section_3_9_and_starts_empty() = withV28Database { db ->
        videoAnalysisMigration(28).migrate(db)

        val columns = tableColumns(db, "video_analysis").map { row ->
            val notNull = if (row[2] == "1") " NOT NULL" else ""
            "${row[0]} ${row[1]}$notNull"
        }
        assertEquals(expectedAnalysisColumns, columns)
        // PK en mediaId y ningún DEFAULT (una columna con DEFAULT que Room no espera rompe la validación).
        assertEquals(listOf("mediaId"), tableColumns(db, "video_analysis").filter { it[4] != "0" }.map { it[0]!! })
        assertTrue(tableColumns(db, "video_analysis").all { it[3] == null })
        // El nombre de la columna de la vista no es la palabra reservada VIEW.
        assertTrue("viewKind" in columns.map { it.substringBefore(' ') })
        assertFalse("view" in columns.map { it.substringBefore(' ') })
        assertEquals(0L, scalar(db, "SELECT COUNT(*) FROM video_analysis"))

        assertEquals(
            setOf("index_video_analysis_status", "index_video_analysis_exerciseKey_recordedAtMs"),
            indexNames(db, "video_analysis"),
        )
        assertEquals(listOf("status"), indexColumns(db, "index_video_analysis_status"))
        assertEquals(listOf("exerciseKey", "recordedAtMs"), indexColumns(db, "index_video_analysis_exerciseKey_recordedAtMs"))
    }

    @Test
    fun video_analysis_hangs_from_workout_media_with_cascade() = withV28Database { db ->
        videoAnalysisMigration(28).migrate(db)

        // PRAGMA foreign_key_list: id, seq, table, from, to, on_update, on_delete, match.
        val foreignKeys = rows(db, "PRAGMA foreign_key_list('video_analysis')").map { it.subList(2, 7) }
        assertEquals(listOf(listOf("workout_media", "mediaId", "id", "NO ACTION", "CASCADE")), foreignKeys)
    }

    @Test
    fun with_foreign_keys_on_deleting_the_media_row_deletes_its_analysis_and_only_its_analysis() = withV28Database { db ->
        insertFullMedia(db, "media-a", "key-1")
        insertFullMedia(db, "media-b", "key-1")
        videoAnalysisMigration(28).migrate(db)
        db.setForeignKeyConstraintsEnabled(true)
        insertAnalysis(db, "media-a")
        insertAnalysis(db, "media-b")

        db.execSQL("DELETE FROM workout_media WHERE id = 'media-a'")

        assertEquals(listOf("media-b"), rows(db, "SELECT mediaId FROM video_analysis").map { it.single() })
        assertEquals(1L, scalar(db, "SELECT COUNT(*) FROM workout_media"))

        // La restauración de copias hace DELETE FROM workout_media: la caché se vacía entera, sin error.
        db.execSQL("DELETE FROM workout_media")
        assertEquals(0L, scalar(db, "SELECT COUNT(*) FROM video_analysis"))
    }

    @Test
    fun an_analysis_cannot_point_to_a_media_row_that_does_not_exist() = withV28Database { db ->
        videoAnalysisMigration(28).migrate(db)
        db.setForeignKeyConstraintsEnabled(true)

        assertThrows(SQLiteException::class.java) { insertAnalysis(db, "ghost") }
        assertEquals(0L, scalar(db, "SELECT COUNT(*) FROM video_analysis"))
    }

    @Test
    fun the_migration_is_idempotent_and_never_loses_data_when_it_runs_again() = withV28Database { db ->
        insertFullMedia(db, "media-a", "key-1")
        val migration = videoAnalysisMigration(28)
        migration.migrate(db)
        insertAnalysis(db, "media-a")

        migration.migrate(db)

        assertEquals(1L, scalar(db, "SELECT COUNT(*) FROM workout_media"))
        assertEquals(1L, scalar(db, "SELECT COUNT(*) FROM video_analysis"))
        assertEquals(25, tableColumns(db, "workout_media").size)
        assertEquals(43, tableColumns(db, "video_analysis").size)
        assertNull(rows(db, "SELECT catalogDefinitionId FROM workout_media").single().single())
    }

    @Test
    fun the_migration_can_be_renumbered() {
        val first = videoAnalysisMigration(28)
        assertEquals(28, first.startVersion)
        assertEquals(29, first.endVersion)
        val shifted = videoAnalysisMigration(30)
        assertEquals(30, shifted.startVersion)
        assertEquals(31, shifted.endVersion)
    }
}
