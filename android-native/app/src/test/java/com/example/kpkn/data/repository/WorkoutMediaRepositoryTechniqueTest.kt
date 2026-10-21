package com.example.kpkn.data.repository

import android.app.Application
import android.content.Context
import androidx.test.core.app.ApplicationProvider
import com.example.kpkn.data.db.KpknDatabase
import com.example.kpkn.data.db.VideoAnalysisEntity
import com.example.kpkn.data.db.dbJson
import com.example.kpkn.data.db.toEntity
import com.example.kpkn.data.media.WorkoutMediaStore
import com.example.kpkn.data.media.technique.TechniqueSidecars
import com.example.kpkn.data.models.CompletedSet
import com.example.kpkn.data.models.RecordedSetPayload
import com.example.kpkn.data.models.UnitModeV2
import com.example.kpkn.data.models.WorkoutMedia
import com.example.kpkn.data.models.WorkoutMediaKind
import kotlinx.coroutines.runBlocking
import kotlinx.serialization.encodeToString
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Rule
import org.junit.Test
import org.junit.rules.TemporaryFolder
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import java.io.File

/**
 * Lo que `WorkoutMediaRepository` gana en la v29: actualizaciones dirigidas del medio, `syncSetSnapshots`,
 * `assignExercise` y un `delete` que también se lleva el análisis y los sidecars. Sobre Room en memoria y un almacén
 * privado en una carpeta temporal.
 */
@RunWith(RobolectricTestRunner::class)
@Config(manifest = Config.NONE, sdk = [34], application = Application::class)
class WorkoutMediaRepositoryTechniqueTest {
    @Rule
    @JvmField
    val folder = TemporaryFolder()

    private val context: Context get() = ApplicationProvider.getApplicationContext()
    private val databases = mutableListOf<KpknDatabase>()
    private lateinit var store: WorkoutMediaStore

    @Before
    fun setUp() {
        store = WorkoutMediaStore(folder.newFolder("files"))
    }

    @After
    fun tearDown() {
        databases.forEach { it.close() }
        databases.clear()
        KpknDatabase.closeInstance()
    }

    private fun database(): KpknDatabase = KpknDatabase.createInMemory(context).also { databases += it }

    private fun repository(db: KpknDatabase) = WorkoutMediaRepository(context, db, store)

    private fun media(
        id: String,
        kind: WorkoutMediaKind = WorkoutMediaKind.VIDEO,
        sessionKey: String? = "S",
        exerciseId: String? = "squat",
        setIndex: Int? = 0,
        side: String? = null,
        reps: Int? = 5,
        weightKg: Double? = 80.0,
        filePath: String = "/private/$id.mp4",
        createdAtMs: Long = 1_000L,
    ) = WorkoutMedia(
        id = id,
        kind = kind,
        filePath = filePath,
        createdAtMs = createdAtMs,
        sessionKey = sessionKey,
        exerciseId = exerciseId,
        canonicalExerciseId = "lower_squat",
        exerciseName = "Sentadilla",
        setIndex = setIndex,
        side = side,
        weightKg = weightKg,
        reps = reps,
    )

    private fun analysis(mediaId: String) = VideoAnalysisEntity(
        mediaId = mediaId,
        status = "DONE",
        progressPct = 100,
        engineVersion = 1,
        poseVersion = 1,
        exerciseKey = "lower_squat",
        recordedAtMs = 1_000L,
        flagsJson = """{"flags":[],"observations":[]}""",
        createdAtMs = 2_000L,
        updatedAtMs = 2_000L,
    )

    private fun set(id: String, weight: Double, reps: Int, skipped: Boolean = false) =
        CompletedSet(id = id, weight = weight, reps = reps, skipped = skipped)

    @Test
    fun the_targeted_updates_change_one_thing_and_report_whether_the_media_exists() = runBlocking {
        val db = database()
        val repo = repository(db)
        db.workoutMediaDao().upsert(media("m1").toEntity())
        db.videoAnalysisDao().upsert(analysis("m1"))
        val original = db.workoutMediaDao().getById("m1")!!

        assertTrue(repo.setPoseTrackPath("m1", "/private/m1.pose2.json.gz"))
        assertEquals(original.copy(poseTrackPath = "/private/m1.pose2.json.gz"), db.workoutMediaDao().getById("m1"))

        assertTrue(repo.setSetSnapshot("m1", 8, 92.5))
        assertEquals(
            original.copy(poseTrackPath = "/private/m1.pose2.json.gz", reps = 8, weightKg = 92.5),
            db.workoutMediaDao().getById("m1"),
        )

        assertTrue(repo.setPoseTrackPath("m1", null))
        assertNull(db.workoutMediaDao().getById("m1")?.poseTrackPath)
        assertNotNull("ninguna actualización dirigida borra el análisis", db.videoAnalysisDao().getByMedia("m1"))

        assertFalse(repo.setPoseTrackPath("missing", "x"))
        assertFalse(repo.setSetSnapshot("missing", 1, 1.0))
    }

    @Test
    fun syncSetSnapshots_puts_the_registered_reps_and_kg_on_the_videos_of_the_session() = runBlocking {
        val db = database()
        val repo = repository(db)
        val dao = db.workoutMediaDao()
        dao.upsert(media("v-bilateral", setIndex = 0).toEntity())
        dao.upsert(media("v-left", setIndex = 1, side = "left").toEntity())
        dao.upsert(media("v-fallback", setIndex = 2, side = "left").toEntity())
        dao.upsert(media("photo", kind = WorkoutMediaKind.PHOTO, setIndex = 0).toEntity())
        dao.upsert(media("no-set", setIndex = null).toEntity())
        dao.upsert(media("no-exercise", exerciseId = null).toEntity())
        dao.upsert(media("other-session", sessionKey = "T", setIndex = 0).toEntity())
        dao.upsert(media("unregistered", setIndex = 7).toEntity())
        db.videoAnalysisDao().upsert(analysis("v-bilateral"))
        val registered = mapOf(
            "squat_0" to set("squat_0", weight = 100.0, reps = 7),
            "squat_1_L" to set("squat_1_L", weight = 60.0, reps = 9),
            "squat_2" to set("squat_2", weight = 50.0, reps = 4), // sin lado: la que usa un vídeo de lado «left» sin serie propia
        )

        val changed = repo.syncSetSnapshots("S", registered)

        assertEquals(3, changed)
        assertEquals(7 to 100.0, dao.getById("v-bilateral")!!.let { it.reps to it.weightKg })
        assertEquals(9 to 60.0, dao.getById("v-left")!!.let { it.reps to it.weightKg })
        assertEquals(4 to 50.0, dao.getById("v-fallback")!!.let { it.reps to it.weightKg })
        // Lo que no es un vídeo de esa serie de esa sesión queda como estaba.
        for (id in listOf("photo", "no-set", "no-exercise", "other-session", "unregistered")) {
            assertEquals("«$id»", 5 to 80.0, dao.getById(id)!!.let { it.reps to it.weightKg })
        }
        assertNotNull("sincronizar no toca el análisis", db.videoAnalysisDao().getByMedia("v-bilateral"))
        assertEquals("sin cambios, sin escrituras", 0, repo.syncSetSnapshots("S", registered))
    }

    @Test
    fun syncSetSnapshots_never_blanks_a_snapshot_with_a_placeholder_set() = runBlocking {
        val db = database()
        val repo = repository(db)
        val dao = db.workoutMediaDao()
        dao.upsert(media("a", setIndex = 0).toEntity())
        dao.upsert(media("b", setIndex = 1).toEntity())
        dao.upsert(media("c", setIndex = 2).toEntity())

        val changed = repo.syncSetSnapshots(
            "S",
            mapOf(
                "squat_0" to set("squat_0", weight = 0.0, reps = 0), // sin repeticiones ni carga
                "squat_1" to set("squat_1", weight = 65.0, reps = 9, skipped = true), // serie omitida
                "squat_2" to set("squat_2", weight = 0.0, reps = 10), // peso corporal: conserva los kg que había
            ),
        )

        assertEquals(1, changed)
        assertEquals(5 to 80.0, dao.getById("a")!!.let { it.reps to it.weightKg })
        assertEquals(5 to 80.0, dao.getById("b")!!.let { it.reps to it.weightKg })
        assertEquals(10 to 80.0, dao.getById("c")!!.let { it.reps to it.weightKg })
        assertEquals(0, repo.syncSetSnapshots("", mapOf("squat_0" to set("squat_0", 1.0, 1))))
        assertEquals(0, repo.syncSetSnapshots("S", emptyMap()))
    }

    @Test
    fun syncSetSnapshots_updates_zero_full_reps_only_for_positive_partials_in_a_rep_record() = runBlocking {
        val db = database()
        val repo = repository(db)
        val dao = db.workoutMediaDao()
        val partialOnly = CompletedSet("partial", weight = 80.0, reps = 0, partialReps = 2, isPartial = true)
        fun payload(unit: UnitModeV2) = RecordedSetPayload(exerciseId = "squat", unitMode = unit, completedReps = 0, partialReps = 2)
        val cases = listOf(
            partialOnly,
            partialOnly.copy(recordedPayloadV3 = payload(UnitModeV2.REPS)),
            partialOnly.copy(recordedPayloadV3 = payload(UnitModeV2.REPS), timeSeconds = 30, distanceKm = 2.5),
            partialOnly.copy(recordedPayloadV3 = payload(UnitModeV2.TIME)),
            partialOnly.copy(recordedPayloadV3 = payload(UnitModeV2.DISTANCE)),
            partialOnly.copy(timeSeconds = 30),
            partialOnly.copy(distanceKm = 2.5),
            partialOnly.copy(partialReps = 0),
            partialOnly.copy(partialReps = -1),
            partialOnly.copy(reps = -1),
            partialOnly.copy(skipped = true),
        )
        val registered = cases.mapIndexed { index, set ->
            dao.upsert(media("partial-$index", setIndex = index).toEntity())
            "squat_$index" to set.copy(id = "set-$index")
        }.toMap()
        db.videoAnalysisDao().upsert(analysis("partial-0"))

        assertEquals(3, repo.syncSetSnapshots("S", registered))
        cases.indices.forEach { index ->
            val snapshot = dao.getById("partial-$index")!!
            assertEquals("caso $index", if (index < 3) 0 else 5, snapshot.reps)
            assertEquals(80.0, snapshot.weightKg!!, 0.0)
        }
        assertNotNull(db.videoAnalysisDao().getByMedia("partial-0"))
        assertEquals(0, repo.syncSetSnapshots("S", registered))
    }

    @Test
    fun assignExercise_sets_the_exercise_and_the_catalog_ids_and_nothing_else() = runBlocking {
        val db = database()
        val repo = repository(db)
        val dao = db.workoutMediaDao()
        dao.upsert(media("m1", exerciseId = null).toEntity())
        db.videoAnalysisDao().upsert(analysis("m1"))
        val original = dao.getById("m1")!!

        assertTrue(repo.assignExercise("m1", "ex-1", "lower_back_squat", "  Sentadilla trasera ", "back_squat", "back_squat__barbell"))

        assertEquals(
            original.copy(
                exerciseId = "ex-1",
                canonicalExerciseId = "lower_back_squat",
                exerciseName = "Sentadilla trasera",
                catalogDefinitionId = "back_squat",
                catalogConfigurationId = "back_squat__barbell",
            ),
            dao.getById("m1"),
        )
        assertNotNull("reasignar no borra el análisis: quien reasigna pide el RECOMPUTE", db.videoAnalysisDao().getByMedia("m1"))

        // Los ids en blanco se guardan como «sin dato», nunca como una cadena vacía.
        assertTrue(repo.assignExercise("m1", " ", "", "   ", "", " "))
        val cleared = dao.getById("m1")!!
        assertNull(cleared.exerciseId)
        assertNull(cleared.canonicalExerciseId)
        assertNull(cleared.exerciseName)
        assertNull(cleared.catalogDefinitionId)
        assertNull(cleared.catalogConfigurationId)

        assertFalse(repo.assignExercise("missing", "a", "b", "c", "d", "e"))
    }

    @Test
    fun a_media_row_keeps_its_catalog_ids_through_the_repository_and_its_analysis_through_an_upsert() = runBlocking {
        val db = database()
        val repo = repository(db)
        val withCatalog = media("m1").copy(catalogDefinitionId = "back_squat", catalogConfigurationId = "back_squat__barbell")
        repo.upsert(withCatalog)
        db.videoAnalysisDao().upsert(analysis("m1"))

        assertEquals(withCatalog, repo.getById("m1"))
        assertEquals(listOf(withCatalog), repo.listForSessionKey("S"))

        // Reescribir la fila entera (como hace la ingesta de una captura repetida) no dispara la cascada.
        repo.upsert(withCatalog.copy(reps = 11, caption = "otra"))

        assertEquals(11, repo.getById("m1")?.reps)
        assertNotNull(db.videoAnalysisDao().getByMedia("m1"))
    }

    @Test
    fun delete_removes_the_row_the_analysis_and_every_file_of_the_media_only() = runBlocking {
        val db = database()
        val repo = repository(db)
        val createdAt = 1_780_000_000_000L
        val outsidePose = folder.newFile("fuera-del-almacen.pose.json").apply { writeText("{}") }

        fun withFiles(id: String): Pair<WorkoutMedia, List<File>> {
            val video = store.destinationFile(id, WorkoutMediaKind.VIDEO, createdAt).apply { writeText("video") }
            val thumb = store.thumbFile(id).apply { writeText("miniatura") }
            val oldPose = File(video.parentFile, "$id.pose.json").apply { writeText("{}") }
            val sidecars = TechniqueSidecars.allFiles(video).onEach { it.writeText("x") }
            val row = media(id, filePath = video.absolutePath, createdAtMs = createdAt).copy(
                thumbPath = thumb.absolutePath,
                poseTrackPath = TechniqueSidecars.poseFile(video).absolutePath,
            )
            return row to (listOf(video, thumb, oldPose) + sidecars)
        }

        val (doomed, doomedFiles) = withFiles("vid-1")
        val (kept, keptFiles) = withFiles("vid-2")
        db.workoutMediaDao().upsert(doomed.copy(poseTrackPath = outsidePose.absolutePath).toEntity())
        db.workoutMediaDao().upsert(kept.toEntity())
        db.videoAnalysisDao().upsert(analysis("vid-1"))
        db.videoAnalysisDao().upsert(analysis("vid-2"))
        assertTrue(doomedFiles.all { it.exists() } && keptFiles.all { it.exists() })

        repo.delete("vid-1")

        assertNull(db.workoutMediaDao().getById("vid-1"))
        assertNull("el análisis se va con el medio", db.videoAnalysisDao().getByMedia("vid-1"))
        assertTrue("ni el vídeo, ni la miniatura, ni la pose antigua, ni un solo sidecar", doomedFiles.none { it.exists() })
        assertTrue("fuera del almacén privado no se borra nada", outsidePose.exists())
        // El otro medio queda entero.
        assertNotNull(db.workoutMediaDao().getById("vid-2"))
        assertNotNull(db.videoAnalysisDao().getByMedia("vid-2"))
        assertTrue(keptFiles.all { it.exists() })
    }

    @Test
    fun delete_of_an_unknown_media_is_harmless_and_leaves_the_others_alone() = runBlocking {
        val db = database()
        val repo = repository(db)
        db.workoutMediaDao().upsert(media("m1").toEntity())
        db.videoAnalysisDao().upsert(analysis("m1"))

        repo.delete("no-existe")

        assertEquals(1, db.workoutMediaDao().count())
        assertEquals(1, db.videoAnalysisDao().count())
    }

    @Test
    fun a_media_json_from_before_the_catalog_ids_still_decodes_and_a_new_one_carries_them() {
        val old = """{"id":"x","kind":"VIDEO","filePath":"/p/x.mp4","createdAtMs":1,"exerciseName":"Sentadilla"}"""

        val decoded = dbJson.decodeFromString(WorkoutMedia.serializer(), old)

        assertNull(decoded.catalogDefinitionId)
        assertNull(decoded.catalogConfigurationId)
        val current = decoded.copy(catalogDefinitionId = "back_squat", catalogConfigurationId = "back_squat__barbell")
        val text = dbJson.encodeToString(current)
        assertTrue("\"catalogDefinitionId\":\"back_squat\"" in text)
        assertEquals(current, dbJson.decodeFromString(WorkoutMedia.serializer(), text))
    }
}
