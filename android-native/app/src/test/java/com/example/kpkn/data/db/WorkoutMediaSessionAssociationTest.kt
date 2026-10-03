package com.example.kpkn.data.db

import android.app.Application
import android.content.Context
import androidx.room.withTransaction
import androidx.test.core.app.ApplicationProvider
import com.example.kpkn.data.models.CompletedExercise
import com.example.kpkn.data.models.CompletedSet
import com.example.kpkn.data.models.SessionMilestone
import com.example.kpkn.data.models.WorkoutLog
import com.example.kpkn.data.models.WorkoutMedia
import com.example.kpkn.data.models.WorkoutMediaKind
import com.example.kpkn.data.repository.WorkoutMediaRepository
import com.example.kpkn.domain.calculations.calculateHybrid1RM
import com.example.kpkn.domain.exercises.normalizedIdentityFields
import org.json.JSONObject
import kotlinx.coroutines.runBlocking
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import java.io.File

@RunWith(RobolectricTestRunner::class)
@Config(manifest = Config.NONE, sdk = [34], application = Application::class)
class WorkoutMediaSessionAssociationTest {
    private val context: Context get() = ApplicationProvider.getApplicationContext()
    private val databases = mutableListOf<KpknDatabase>()

    private fun db(): KpknDatabase = KpknDatabase.createInMemory(context).also(databases::add)

    @After
    fun tearDown() {
        databases.forEach { it.close() }
        databases.clear()
        File(context.filesDir, "workout_media_pending").deleteRecursively()
        File(context.filesDir, "workout_media").deleteRecursively()
    }

    @Test
    fun finalize_before_ingest_links_late_media_and_preserves_pr_flag(): Unit = runBlocking {
        val database = db()
        val log = sampleLog("log-late")
        val sessionKey = "program::session::1700000000000"
        database.withTransaction {
            database.workoutLogDao().insert(log.toEntity())
            database.bindWorkoutMediaSession(sessionKey, log)
        }

        val source = File(context.cacheDir, "late-media.jpg").apply { writeBytes(byteArrayOf(1, 2, 3, 4)) }
        val repository = WorkoutMediaRepository.forDatabase(context, database)
        val saved = repository.ingestFile(
            source = source,
            kind = WorkoutMediaKind.PHOTO,
            seed = sampleMedia("late", sessionKey),
        )

        assertEquals(log.id, saved?.workoutLogId)
        assertTrue(saved!!.isPr)
        assertEquals(log.id, database.workoutMediaDao().getById("late")?.workoutLogId)
        source.delete()
        Unit
    }

    @Test
    fun media_before_finalize_is_attached_transactionally_and_binding_retry_is_idempotent() = runBlocking {
        val database = db()
        val sessionKey = "program::session::1700000000001"
        val repository = WorkoutMediaRepository.forDatabase(context, database)
        repository.upsert(sampleMedia("early", sessionKey))
        val log = sampleLog("log-early")

        database.withTransaction {
            database.workoutLogDao().insert(log.toEntity())
            database.bindWorkoutMediaSession(sessionKey, log)
            database.bindWorkoutMediaSession(sessionKey, log)
        }

        val saved = database.workoutMediaDao().getById("early")!!
        assertEquals(log.id, saved.workoutLogId)
        assertTrue(saved.isPr)
        assertEquals(log.id, database.workoutMediaSessionAssociationDao().getBySessionKey(sessionKey)?.workoutLogId)
    }

    @Test
    fun normalized_legacy_log_upsert_preserves_binding_for_late_media(): Unit = runBlocking {
        val database = db()
        val log = sampleLog("log-normalized-late")
        val sessionKey = "program::session::1700000000003"
        assertTrue(log.completedExercises.single().canonicalExerciseId == null)

        database.withTransaction {
            database.workoutLogDao().insert(log.toEntity())
            database.bindWorkoutMediaSession(sessionKey, log)
        }

        val legacyEntity = requireNotNull(database.workoutLogDao().getById(log.id))
        val normalized = requireNotNull(legacyEntity.toWorkoutLog()).normalizedIdentityFields()
        val normalizedEntity = normalized.toEntity()
        assertTrue(normalized.completedExercises.single().canonicalExerciseId != null)
        assertTrue("The fixture must exercise the hydration rewrite", legacyEntity.data != normalizedEntity.data)

        // This is the same DAO operation used when repository hydration rewrites an old log.
        database.workoutLogDao().insert(normalizedEntity)
        assertEquals(
            log.id,
            database.workoutMediaSessionAssociationDao().getBySessionKey(sessionKey)?.workoutLogId,
        )

        val source = File(context.cacheDir, "late-normalized-${log.id}.jpg").apply {
            writeBytes(byteArrayOf(1, 2, 3, 4))
        }
        val saved = try {
            WorkoutMediaRepository.forDatabase(context, database).ingestFile(
                source = source,
                kind = WorkoutMediaKind.PHOTO,
                seed = sampleMedia("late-normalized-${log.id}", sessionKey),
            )
        } finally {
            source.delete()
        }

        assertEquals(log.id, saved?.workoutLogId)
        assertEquals(1, database.workoutMediaDao().getBySessionKey(sessionKey).size)
        assertEquals(
            log.id,
            database.workoutMediaSessionAssociationDao().getBySessionKey(sessionKey)?.workoutLogId,
        )
    }
    @Test
    fun v27_migration_logs_have_typed_normalization_golden_without_losing_sets(): Unit {
        val exerciseId = "re_e3866f71f020e8820f908768bac54081"
        val programId = "0d272e12-6fd9-4bf0-8525-b1fd9f45af4f"
        val sessionId = "rs_37de122c4df863f705db0aaa1116dda6"
        val legacyRows = listOf(
            WorkoutLogEntity(
                id = "repair-history-0",
                programId = programId,
                sessionId = sessionId,
                date = "2023-01-01T00:00:00Z",
                data = """{"id":"repair-history-0","programId":"0d272e12-6fd9-4bf0-8525-b1fd9f45af4f","sessionId":"rs_37de122c4df863f705db0aaa1116dda6","sessionName":"D\u00eda 1","date":"2023-01-01T00:00:00Z","durationMinutes":40,"totalVolume":240.0,"completedExercises":[{"exerciseId":"re_e3866f71f020e8820f908768bac54081","exerciseName":"Sentadilla Trasera con Barra Baja","exerciseDbId":"low_bar_back_squat__barbell","sets":[{"id":"repair-log-set-0","weight":20.0,"reps":6}]}]}""",
            ),
            WorkoutLogEntity(
                id = "repair-history-1",
                programId = programId,
                sessionId = sessionId,
                date = "2023-01-02T00:00:00Z",
                data = """{"id":"repair-history-1","programId":"0d272e12-6fd9-4bf0-8525-b1fd9f45af4f","sessionId":"rs_37de122c4df863f705db0aaa1116dda6","sessionName":"D\u00eda 1","date":"2023-01-02T00:00:00Z","durationMinutes":40,"totalVolume":240.0,"completedExercises":[{"exerciseId":"re_e3866f71f020e8820f908768bac54081","exerciseName":"Sentadilla Trasera con Barra Baja","exerciseDbId":"low_bar_back_squat__barbell","sets":[{"id":"repair-log-set-1","weight":20.0,"reps":6}]}]}""",
            ),
        )
        fun setSnapshots(log: WorkoutLog): List<String> = log.completedExercises.flatMap { exercise ->
            exercise.sets.map { set -> exercise.exerciseId + "|" + set.id + "|" + set.weight + "|" + set.reps }
        }
        val expectedDates = mapOf(
            "repair-history-0" to "2023-01-01T00:00:00Z",
            "repair-history-1" to "2023-01-02T00:00:00Z",
        )
        val expectedSets = mapOf(
            "repair-history-0" to listOf(exerciseId + "|repair-log-set-0|20.0|6"),
            "repair-history-1" to listOf(exerciseId + "|repair-log-set-1|20.0|6"),
        )
        val normalizedRows = linkedMapOf<String, String>()

        legacyRows.forEach { entity ->
            val legacy = requireNotNull(entity.toWorkoutLog())
            assertEquals(entity.id, legacy.id)
            assertEquals(programId, legacy.programId)
            assertEquals(sessionId, legacy.sessionId)
            assertEquals(expectedDates.getValue(entity.id), legacy.date)
            assertEquals(null, legacy.completedExercises.single().canonicalExerciseId)
            assertEquals(expectedSets.getValue(entity.id), setSnapshots(legacy))

            val normalized = legacy.normalizedIdentityFields()
            assertEquals(entity.id, normalized.id)
            assertEquals(programId, normalized.programId)
            assertEquals(sessionId, normalized.sessionId)
            assertEquals(expectedDates.getValue(entity.id), normalized.date)
            assertEquals("low_bar_back_squat__barbell", normalized.completedExercises.single().canonicalExerciseId)
            assertEquals(expectedSets.getValue(entity.id), setSnapshots(normalized))
            assertEquals(40, normalized.durationMinutes)
            assertEquals(240.0, normalized.totalVolume, 0.0)
            normalizedRows[normalized.id] = normalized.toEntity().data
        }

        val goldenJson = JSONObject().apply {
            normalizedRows.toSortedMap().forEach { (id, data) -> put(id, data) }
        }
        System.out.println("MIGRATION_NORMALIZED_EXPECTED_LOG_ROWS=" + goldenJson.toString())
    }
    @Test
    fun conflicting_session_key_binding_fails_without_replacing_original() = runBlocking {
        val database = db()
        val sessionKey = "program::session::1700000000002"
        val first = sampleLog("log-one")
        val second = sampleLog("log-two")
        database.withTransaction {
            database.workoutLogDao().insert(first.toEntity())
            database.workoutLogDao().insert(second.toEntity())
            database.bindWorkoutMediaSession(sessionKey, first)
        }

        val failure = runCatching {
            database.withTransaction { database.bindWorkoutMediaSession(sessionKey, second) }
        }.exceptionOrNull()

        assertTrue(failure is IllegalStateException)
        assertEquals(first.id, database.workoutMediaSessionAssociationDao().getBySessionKey(sessionKey)?.workoutLogId)
    }

    private fun sampleLog(id: String): WorkoutLog {
        val exerciseId = "bench_press"
        val set = CompletedSet(id = "${exerciseId}_0", weight = 100.0, reps = 1)
        return WorkoutLog(
            id = id,
            programId = "program",
            sessionId = "session",
            sessionName = "Session",
            date = "2026-09-30T00:00:00Z",
            durationMinutes = 30,
            completedExercises = listOf(CompletedExercise(exerciseId, "Bench", sets = listOf(set))),
            sessionMilestones = listOf(
                SessionMilestone(
                    id = "pr",
                    exerciseId = exerciseId,
                    exerciseName = "Bench",
                    kind = "pr_e1rm",
                    label = "Best",
                    value = calculateHybrid1RM(set.weight, set.reps),
                ),
            ),
        )
    }

    private fun sampleMedia(id: String, sessionKey: String) = WorkoutMedia(
        id = id,
        kind = WorkoutMediaKind.PHOTO,
        filePath = "/not-used/$id.jpg",
        createdAtMs = 1L,
        sessionKey = sessionKey,
        programId = "program",
        sessionId = "session",
        exerciseId = "bench_press",
        canonicalExerciseId = "bench_press",
        setIndex = 0,
        weightKg = 100.0,
        reps = 1,
    )
}
