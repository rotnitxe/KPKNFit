package com.example.kpkn.data.db

import android.app.Application
import android.content.Context
import androidx.test.core.app.ApplicationProvider
import com.example.kpkn.data.models.WorkoutMedia
import com.example.kpkn.data.models.WorkoutMediaKind
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.withTimeout
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

/**
 * El DAO de `video_analysis` (caché del análisis, v29) sobre Room en memoria, y la regla que lo sostiene: la fila hija
 * cuelga de `workout_media` con CASCADE, así que ninguna escritura de `workout_media` puede borrar y reinsertar (REPLACE).
 */
@RunWith(RobolectricTestRunner::class)
@Config(manifest = Config.NONE, sdk = [34], application = Application::class)
class VideoAnalysisDaoTest {
    private val context: Context get() = ApplicationProvider.getApplicationContext()
    private val databases = mutableListOf<KpknDatabase>()

    private fun db(): KpknDatabase = KpknDatabase.createInMemory(context).also { databases += it }

    @After
    fun tearDown() {
        databases.forEach { it.close() }
        databases.clear()
        KpknDatabase.closeInstance()
    }

    private fun media(
        id: String,
        sessionKey: String? = "session-1",
        kind: WorkoutMediaKind = WorkoutMediaKind.VIDEO,
        createdAtMs: Long = 1_000L,
        exerciseId: String? = "squat",
        setIndex: Int? = 0,
        side: String? = null,
    ) = WorkoutMedia(
        id = id,
        kind = kind,
        filePath = "/private/$id.mp4",
        createdAtMs = createdAtMs,
        sessionKey = sessionKey,
        exerciseId = exerciseId,
        canonicalExerciseId = "lower_squat",
        exerciseName = "Sentadilla",
        setIndex = setIndex,
        side = side,
        weightKg = 80.0,
        reps = 5,
        catalogDefinitionId = "squat",
        catalogConfigurationId = "squat__barbell",
    )

    private fun analysis(
        mediaId: String,
        status: String = "DONE",
        progressPct: Int = 100,
        exerciseKey: String = "squat",
        exerciseName: String? = "Sentadilla",
        configurationKey: String? = "squat__barbell",
        recordedAtMs: Long = 1_000L,
        loadKg: Double? = 100.0,
        mcvBest: Double? = 0.45,
        repCount: Int? = 5,
    ) = VideoAnalysisEntity(
        mediaId = mediaId,
        status = status,
        progressPct = progressPct,
        engineVersion = 1,
        poseVersion = 1,
        exerciseKey = exerciseKey,
        configurationKey = configurationKey,
        exerciseName = exerciseName,
        loadKg = loadKg,
        recordedAtMs = recordedAtMs,
        confidence = "ALTA",
        repCount = repCount,
        partialReps = 1,
        failedReps = 0,
        velocityUnit = "MPS",
        mcvBest = mcvBest,
        flagsJson = """{"flags":[],"observations":[]}""",
        createdAtMs = 2_000L,
        updatedAtMs = 2_000L,
    )

    private suspend fun KpknDatabase.insertMedia(vararg items: WorkoutMedia) {
        items.forEach { workoutMediaDao().upsert(it.toEntity()) }
    }

    private suspend fun <T> firstOf(flow: kotlinx.coroutines.flow.Flow<T>): T = withTimeout(10_000) { flow.first() }

    @Test
    fun upsert_and_read_back_by_media_return_the_same_row() = runBlocking {
        val database = db()
        database.insertMedia(media("m1"))
        val dao = database.videoAnalysisDao()
        val row = analysis("m1")

        dao.upsert(row)

        assertEquals(row, dao.getByMedia("m1"))
        assertEquals(row, firstOf(dao.observeByMedia("m1")))
        assertNull(dao.getByMedia("missing"))
        assertNull(firstOf(dao.observeByMedia("missing")))
        assertEquals(1, dao.count())
        assertEquals(listOf("m1"), dao.allIds())
    }

    @Test
    fun observeRecent_orders_by_recording_date_limits_and_keeps_every_state() = runBlocking {
        val database = db()
        database.insertMedia(media("old"), media("latest"), media("tie"), media("failed"))
        val dao = database.videoAnalysisDao()
        dao.upsert(analysis("old", recordedAtMs = 1_000L))
        dao.upsert(analysis("latest", status = "RUNNING", progressPct = 42, repCount = null, recordedAtMs = 4_000L))
        dao.upsert(analysis("tie", status = "QUEUED", recordedAtMs = 4_000L))
        dao.upsert(analysis("failed", status = "FAILED", recordedAtMs = 2_000L))

        val recent = firstOf(dao.observeRecent(3))
        assertEquals(listOf("latest", "tie", "failed"), recent.map { it.mediaId })
        assertEquals(42, recent.first().progressPct)
        assertEquals("Sentadilla", recent.first().exerciseName)
        assertNull(recent.first().repCount)
        assertEquals(listOf("latest"), firstOf(dao.observeRecent(1)).map { it.mediaId })
        assertTrue(firstOf(dao.observeRecent(0)).isEmpty())
        assertEquals(4, firstOf(dao.observeRecent(10)).size)
    }

    @Test
    fun a_second_upsert_updates_the_same_row_in_place() = runBlocking {
        val database = db()
        database.insertMedia(media("m1"))
        val dao = database.videoAnalysisDao()
        dao.upsert(analysis("m1", status = "RUNNING", progressPct = 30, repCount = null))

        dao.upsert(analysis("m1", status = "DONE", progressPct = 100, repCount = 8))

        assertEquals(1, dao.count())
        assertEquals("DONE", dao.getByMedia("m1")?.status)
        assertEquals(8, dao.getByMedia("m1")?.repCount)
    }

    @Test
    fun an_analysis_without_its_media_row_is_rejected_by_the_foreign_key() = runBlocking {
        val dao = db().videoAnalysisDao()

        val failure = runCatching { dao.upsert(analysis("ghost")) }.exceptionOrNull()

        assertNotNull("un análisis sin vídeo no tiene sentido", failure)
        assertEquals(0, dao.count())
    }

    @Test
    fun observeBySessionKey_returns_only_the_videos_of_that_session_in_recording_order() = runBlocking {
        val database = db()
        database.insertMedia(
            media("late", createdAtMs = 3_000L, setIndex = 1, side = "left"),
            media("early", createdAtMs = 1_000L, setIndex = 0),
            media("other-session", sessionKey = "session-2", createdAtMs = 2_000L),
            media("photo", kind = WorkoutMediaKind.PHOTO, createdAtMs = 1_500L),
            media("no-analysis", createdAtMs = 2_500L),
        )
        val dao = database.videoAnalysisDao()
        dao.upsert(analysis("late", status = "RUNNING", progressPct = 42, repCount = null))
        dao.upsert(analysis("early", repCount = 8))
        dao.upsert(analysis("other-session"))
        dao.upsert(analysis("photo")) // una foto no es un vídeo: nunca debe salir

        val rows = firstOf(dao.observeBySessionKey("session-1"))

        assertEquals(listOf("early", "late"), rows.map { it.mediaId })
        val early = rows[0]
        assertEquals(VideoAnalysisSessionRow("early", "squat", 0, null, 1_000L, "DONE", 100, 8, 1, 0, "ALTA"), early)
        val late = rows[1]
        assertEquals("left", late.side)
        assertEquals(1, late.setIndex)
        assertEquals("RUNNING", late.status)
        assertEquals(42, late.progressPct)
        assertNull(late.repCount)
        assertTrue(firstOf(dao.observeBySessionKey("session-3")).isEmpty())
    }

    @Test
    fun observeExerciseSummaries_groups_the_finished_analyses_by_exercise() = runBlocking {
        val database = db()
        database.insertMedia(
            media("s1"), media("s2"), media("s3"), media("s4"), media("b1"), media("d1"), media("d2"),
        )
        val dao = database.videoAnalysisDao()
        dao.upsert(analysis("s1", exerciseKey = "squat", exerciseName = "Sentadilla", recordedAtMs = 1_000L))
        dao.upsert(analysis("s2", exerciseKey = "squat", exerciseName = "Sentadilla trasera", recordedAtMs = 3_000L))
        dao.upsert(analysis("s3", exerciseKey = "squat", recordedAtMs = 9_000L, status = "FAILED", repCount = null))
        dao.upsert(analysis("s4", exerciseKey = "squat", recordedAtMs = 9_500L, status = "RUNNING", repCount = null))
        dao.upsert(analysis("b1", exerciseKey = "bench_press", exerciseName = "Press de banca", recordedAtMs = 5_000L))
        dao.upsert(analysis("d1", exerciseKey = "deadlift", exerciseName = "Peso muerto", status = "QUEUED", recordedAtMs = 7_000L))
        dao.upsert(analysis("d2", exerciseKey = "deadlift", exerciseName = "Peso muerto", status = "UNSUPPORTED", recordedAtMs = 8_000L))

        val rows = firstOf(dao.observeExerciseSummaries())

        // Solo cuentan los análisis terminados; un ejercicio sin ninguno no sale. Del más reciente al más antiguo.
        assertEquals(
            listOf(
                VideoAnalysisExerciseRow("bench_press", "Press de banca", 1, 5_000L),
                VideoAnalysisExerciseRow("squat", "Sentadilla trasera", 2, 3_000L),
            ),
            rows,
        )
    }

    @Test
    fun observeTrend_returns_the_finished_analyses_of_one_exercise_by_date() = runBlocking {
        val database = db()
        database.insertMedia(media("a"), media("b"), media("c"), media("d"), media("other"))
        val dao = database.videoAnalysisDao()
        dao.upsert(analysis("b", recordedAtMs = 5_000L))
        dao.upsert(analysis("a", recordedAtMs = 2_000L))
        dao.upsert(analysis("c", recordedAtMs = 3_000L, status = "FAILED", repCount = null))
        dao.upsert(analysis("d", recordedAtMs = 4_000L, status = "RUNNING", progressPct = 10, repCount = null))
        dao.upsert(analysis("other", exerciseKey = "bench_press", recordedAtMs = 1_000L))

        assertEquals(listOf("a", "b"), firstOf(dao.observeTrend("squat")).map { it.mediaId })
        assertEquals(listOf("other"), firstOf(dao.observeTrend("bench_press")).map { it.mediaId })
        assertTrue(firstOf(dao.observeTrend("deadlift")).isEmpty())
    }

    @Test
    fun observeConfigurationPoints_returns_load_and_best_velocity_of_that_configuration_only() = runBlocking {
        val database = db()
        database.insertMedia(media("p1"), media("p2"), media("no-load"), media("no-mcv"), media("failed"), media("other-config"))
        val dao = database.videoAnalysisDao()
        dao.upsert(analysis("p2", loadKg = 120.0, mcvBest = 0.38, recordedAtMs = 4_000L))
        dao.upsert(analysis("p1", loadKg = 100.0, mcvBest = 0.52, recordedAtMs = 2_000L))
        dao.upsert(analysis("no-load", loadKg = null, recordedAtMs = 3_000L))
        dao.upsert(analysis("no-mcv", mcvBest = null, recordedAtMs = 3_500L))
        dao.upsert(analysis("failed", status = "FAILED", recordedAtMs = 3_600L, repCount = null))
        dao.upsert(analysis("other-config", configurationKey = "squat__smith_machine", recordedAtMs = 1_000L))

        val points = firstOf(dao.observeConfigurationPoints("squat__barbell"))

        assertEquals(
            listOf(
                VideoAnalysisLoadPoint("p1", 100.0, 0.52, "MPS", 2_000L),
                VideoAnalysisLoadPoint("p2", 120.0, 0.38, "MPS", 4_000L),
            ),
            points,
        )
    }

    @Test
    fun updateProgress_changes_only_the_state_the_progress_and_the_timestamp() = runBlocking {
        val database = db()
        database.insertMedia(media("m1"))
        val dao = database.videoAnalysisDao()
        val queued = analysis("m1", status = "QUEUED", progressPct = 0, repCount = null)
        dao.upsert(queued)

        assertEquals(1, dao.updateProgress("m1", "RUNNING", 35, 9_000L))

        assertEquals(queued.copy(status = "RUNNING", progressPct = 35, updatedAtMs = 9_000L), dao.getByMedia("m1"))
        assertEquals(0, dao.updateProgress("missing", "RUNNING", 35, 9_000L))
    }

    @Test
    fun delete_removes_one_row_and_allIds_lists_the_rest() = runBlocking {
        val database = db()
        database.insertMedia(media("m1"), media("m2"))
        val dao = database.videoAnalysisDao()
        dao.upsert(analysis("m1"))
        dao.upsert(analysis("m2"))

        assertEquals(1, dao.delete("m1"))
        assertEquals(0, dao.delete("m1"))

        assertEquals(listOf("m2"), dao.allIds())
        assertNull(dao.getByMedia("m1"))
        assertEquals("borrar el análisis no borra el medio", 2, database.workoutMediaDao().count())
        assertNotNull(database.workoutMediaDao().getById("m1"))
    }

    @Test
    fun deleting_the_media_row_cascades_to_its_analysis_and_only_to_its_analysis() = runBlocking {
        val database = db()
        database.insertMedia(media("m1"), media("m2"))
        val dao = database.videoAnalysisDao()
        dao.upsert(analysis("m1"))
        dao.upsert(analysis("m2"))

        database.workoutMediaDao().delete("m1")

        assertNull(dao.getByMedia("m1"))
        assertNotNull(dao.getByMedia("m2"))
        assertEquals(listOf("m2"), dao.allIds())
    }

    @Test
    fun upsert_on_workout_media_does_not_delete_the_analysis() = runBlocking {
        val database = db()
        val mediaDao = database.workoutMediaDao()
        val original = media("m1")
        mediaDao.upsert(original.toEntity())
        val dao = database.videoAnalysisDao()
        val row = analysis("m1")
        dao.upsert(row)

        // Cualquier escritura normal de la fila del medio: @Upsert, insertIgnore y las actualizaciones de siempre.
        mediaDao.upsert(original.copy(reps = 9, caption = "otra", isPr = true, filePath = "/private/otro.mp4").toEntity())
        mediaDao.insertIgnore(original.toEntity())
        mediaDao.setPr("m1", false)
        mediaDao.setCaption("m1", "texto")
        mediaDao.attachSessionKeyToLog("session-1", "log-1")

        assertEquals("el @Upsert actualiza en sitio: no dispara la cascada", row, dao.getByMedia("m1"))
        assertEquals(9, mediaDao.getById("m1")?.reps)
    }

    @Test
    fun the_targeted_updates_change_only_their_columns_and_keep_the_analysis() = runBlocking {
        val database = db()
        val mediaDao = database.workoutMediaDao()
        mediaDao.upsert(media("m1").toEntity())
        mediaDao.upsert(media("m2").toEntity())
        val dao = database.videoAnalysisDao()
        dao.upsert(analysis("m1"))
        val original = mediaDao.getById("m1")!!
        val untouched = mediaDao.getById("m2")!!

        assertEquals(1, mediaDao.setPoseTrackPath("m1", "/private/m1.pose2.json.gz"))
        var expected = original.copy(poseTrackPath = "/private/m1.pose2.json.gz")
        assertEquals(expected, mediaDao.getById("m1"))

        assertEquals(1, mediaDao.setSetSnapshot("m1", 9, 102.5))
        expected = expected.copy(reps = 9, weightKg = 102.5)
        assertEquals(expected, mediaDao.getById("m1"))

        assertEquals(1, mediaDao.setCatalogIds("m1", "back_squat", "back_squat__barbell"))
        expected = expected.copy(catalogDefinitionId = "back_squat", catalogConfigurationId = "back_squat__barbell")
        assertEquals(expected, mediaDao.getById("m1"))

        assertEquals(1, mediaDao.setExerciseAssignment("m1", "ex-2", "lower_front_squat", "Sentadilla frontal", "front_squat", null))
        expected = expected.copy(
            exerciseId = "ex-2",
            canonicalExerciseId = "lower_front_squat",
            exerciseName = "Sentadilla frontal",
            catalogDefinitionId = "front_squat",
            catalogConfigurationId = null,
        )
        assertEquals(expected, mediaDao.getById("m1"))

        // Quitar la ruta de la pose también es dirigido, y el resto de medios ni se entera.
        assertEquals(1, mediaDao.setPoseTrackPath("m1", null))
        assertNull(mediaDao.getById("m1")?.poseTrackPath)
        assertEquals(untouched, mediaDao.getById("m2"))
        assertNotNull("ninguna actualización dirigida toca el análisis", dao.getByMedia("m1"))

        // Un medio que ya no existe: 0 filas, sin error.
        assertEquals(0, mediaDao.setPoseTrackPath("missing", "x"))
        assertEquals(0, mediaDao.setSetSnapshot("missing", 1, 1.0))
        assertEquals(0, mediaDao.setCatalogIds("missing", "a", "b"))
        assertEquals(0, mediaDao.setExerciseAssignment("missing", null, null, null, null, null))
    }

    @Test
    fun restoring_a_backup_empties_the_cache_through_the_cascade_and_never_restores_it() = runBlocking {
        val database = db()
        database.insertMedia(media("m1"), media("m2"))
        val dao = database.videoAnalysisDao()
        dao.upsert(analysis("m1"))
        dao.upsert(analysis("m2"))

        // Lo que hace la restauración JSON cuando la copia trae medios: DELETE FROM workout_media y reinserción.
        val tablesToClear = BackupTableManifest.tablesToClearForImport(
            schemaVersion = BackupTableManifest.JSON_BACKUP_SCHEMA_VERSION,
            hasCompetitionSection = true,
            hasWorkoutV2Section = true,
            hasPerformanceSection = true,
            hasWorkoutMediaSection = true,
            hasCalibrationSection = true,
            hasSetupSection = true,
        )
        assertTrue("workout_media" in tablesToClear)
        assertFalse("video_analysis es caché: la copia nunca la limpia ni la restaura", "video_analysis" in tablesToClear)
        BackupTableManifest.deleteRows(database.openHelper.writableDatabase, listOf("workout_media"))
        database.insertMedia(media("m1"), media("m2"))

        assertEquals(0, dao.count())
        assertEquals(2, database.workoutMediaDao().count())
    }

    @Test
    fun every_table_of_the_database_has_a_category_in_the_backup_manifest() {
        val database = db()
        val tables = database.openHelper.writableDatabase.query("SELECT name FROM sqlite_master WHERE type = 'table'").use { cursor ->
            buildList { while (cursor.moveToNext()) add(cursor.getString(0)) }
        }.filterNot { it.startsWith("sqlite_") || it.startsWith("room_") || it.startsWith("global_foods_fts_") || it == "android_metadata" }

        assertTrue(tables.isNotEmpty())
        assertEquals(emptyList<String>(), tables.filter { it !in BackupTableManifest.tableCategory })
        assertEquals(BackupTableManifest.Category.CACHE, BackupTableManifest.tableCategory["video_analysis"])
        assertEquals(BackupTableManifest.Category.USER_DATA, BackupTableManifest.tableCategory["workout_media_session_associations"])
        assertEquals(BackupTableManifest.Category.USER_DATA, BackupTableManifest.tableCategory["workout_media"])
    }
}
