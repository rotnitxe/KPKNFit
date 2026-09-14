package com.example.kpkn.data.db

import android.app.Application
import android.content.Context
import androidx.test.core.app.ApplicationProvider
import com.example.kpkn.data.models.WorkoutMedia
import com.example.kpkn.data.models.WorkoutMediaKind
import kotlinx.coroutines.runBlocking
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class)
@Config(manifest = Config.NONE, sdk = [34], application = Application::class)
class WorkoutMediaDaoTest {
    private val context: Context get() = ApplicationProvider.getApplicationContext()
    private val databases = mutableListOf<KpknDatabase>()

    private fun db(): KpknDatabase =
        KpknDatabase.createInMemory(context).also { databases += it }

    @After
    fun tearDown() {
        databases.forEach { it.close() }
        databases.clear()
        KpknDatabase.closeInstance()
    }

    @Test
    fun upsert_and_queries_by_indexes_and_pr_range() = runBlocking {
        val dao = db().workoutMediaDao()
        dao.upsert(sample(id = "a", workoutLogId = "log-1", canonical = "bench", createdAtMs = 10L, isPr = true).toEntity())
        dao.upsert(sample(id = "b", workoutLogId = "log-1", canonical = "squat", createdAtMs = 30L, isPr = false).toEntity())
        dao.upsert(sample(id = "c", workoutLogId = "log-2", canonical = "bench", createdAtMs = 20L, isPr = true).toEntity())

        assertEquals(3, dao.count())
        assertEquals(listOf("b", "a"), dao.getByWorkoutLogId("log-1").map { it.id })
        assertEquals(listOf("c", "a"), dao.getByCanonicalExerciseId("bench").map { it.id })
        assertEquals(listOf("c", "a"), dao.getPrHighlights(0L, 25L).map { it.id })
        assertEquals("PHOTO", dao.getById("a")?.kind)
    }

    @Test
    fun insertIgnore_does_not_overwrite_and_attach_links_open_session() = runBlocking {
        val dao = db().workoutMediaDao()
        val first = sample(id = "live", sessionKey = "ongoing-1", workoutLogId = null, createdAtMs = 1L)
        dao.insertIgnore(first.toEntity())
        dao.insertIgnore(first.copy(filePath = "/changed.jpg").toEntity())
        assertEquals(first.filePath, dao.getById("live")?.filePath)

        dao.attachSessionKeyToLog("ongoing-1", "log-final")
        assertEquals("log-final", dao.getById("live")?.workoutLogId)

        dao.setPr("live", true)
        assertTrue(dao.getById("live")!!.isPr)
    }

    @Test
    fun markPrFlags_sets_isPr_for_matching_set() = runBlocking {
        val database = db()
        val repo = com.example.kpkn.data.repository.WorkoutMediaRepository.forDatabase(context, database)
        repo.upsert(
            sample(id = "hit", sessionKey = "sk-1", createdAtMs = 2L).copy(
                exerciseId = "bench",
                setIndex = 1,
            ),
        )
        repo.upsert(
            sample(id = "miss", sessionKey = "sk-1", createdAtMs = 3L).copy(
                exerciseId = "bench",
                setIndex = 0,
            ),
        )
        repo.markPrFlags(
            sessionKey = "sk-1",
            completedSets = mapOf(
                "bench_1" to com.example.kpkn.data.models.CompletedSet(
                    id = "s",
                    weight = 100.0,
                    reps = 3,
                    homologatedResultV3 = com.example.kpkn.data.models.HomologatedPerformanceResult(
                        contextKey = "c",
                        globalKey = "g",
                        loadMode = com.example.kpkn.data.models.LoadModeV2.LOAD,
                        unitMode = com.example.kpkn.data.models.UnitModeV2.REPS,
                        actualValue = 3.0,
                        metricType = "ERM",
                        metricValue = 100.0,
                        localPerformanceIndex = 50.0,
                        globalPerformanceIndex = 50.0,
                        contextPercentile = 50.0,
                        globalPercentile = 50.0,
                        contextEwma = 50.0,
                        contextStdDev = 5.0,
                        globalEwma = 50.0,
                        globalStdDev = 5.0,
                        isContextPr = false,
                        isGlobalPr = true,
                        historyColor = com.example.kpkn.data.models.HistoryColorV2.NEUTRAL,
                        difficultySignal = com.example.kpkn.data.models.DifficultySignalV2.MATCHED,
                        augeEquivalentLoad = 100.0,
                        augeEquivalentReps = 3,
                    ),
                ),
            ),
            milestones = emptyList(),
        )
        assertTrue(repo.getById("hit")!!.isPr)
        assertTrue(repo.getById("miss")?.isPr != true)
        repo.attachToLog("sk-1", "log-final")
        assertEquals("log-final", repo.getById("hit")?.workoutLogId)
    }

    private fun sample(
        id: String,
        workoutLogId: String? = null,
        sessionKey: String? = null,
        canonical: String? = null,
        createdAtMs: Long = 1L,
        isPr: Boolean = false,
    ) = WorkoutMedia(
        id = id,
        kind = WorkoutMediaKind.PHOTO,
        filePath = "/tmp/$id.jpg",
        createdAtMs = createdAtMs,
        sessionKey = sessionKey,
        workoutLogId = workoutLogId,
        canonicalExerciseId = canonical,
        isPr = isPr,
    )
}
