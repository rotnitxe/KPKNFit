package com.example.kpkn.data.media

import com.example.kpkn.data.models.WorkoutMedia
import com.example.kpkn.data.models.WorkoutMediaKind
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import java.time.ZoneOffset

class WorkoutAlbumGroupingTest {
    private val zone = ZoneOffset.UTC

    @Test
    fun groupsByWorkoutLogThenSessionKeyThenSessionDate() {
        val logA = media("1", workoutLogId = "log-a", sessionName = "Fuerza", createdAtMs = day("2026-09-01"))
        val logA2 = media("2", workoutLogId = "log-a", sessionName = "Fuerza", createdAtMs = day("2026-09-01") + 1)
        val live = media("3", sessionKey = "p::s::1", sessionName = "En curso", createdAtMs = day("2026-09-02"))
        val sess = media("4", sessionId = "sess-x", sessionName = "Vieja", createdAtMs = day("2026-08-01"))
        val sessNextDay = media("5", sessionId = "sess-x", sessionName = "Vieja", createdAtMs = day("2026-08-02"))
        val orphan = media("6", createdAtMs = day("2026-07-01"))

        val albums = WorkoutAlbumGrouping.group(
            listOf(logA, logA2, live, sess, sessNextDay, orphan),
            zone,
        )
        assertEquals(5, albums.size)
        assertEquals("log:log-a", albums.first { it.albumKey.startsWith("log:") }.albumKey)
        assertEquals(2, albums.first { it.albumKey == "log:log-a" }.mediaCount)
        assertEquals("live:p::s::1", albums.first { it.albumKey.startsWith("live:") }.albumKey)
        assertEquals(2, albums.count { it.albumKey.startsWith("session:sess-x:") })
        assertTrue(albums.any { it.albumKey == "day:2026-07-01" })
    }

    @Test
    fun prFilterAndExerciseQueryReduceItemsBeforeGrouping() {
        val benchPr = media("a", exerciseName = "Banca", canonicalExerciseId = "bench", isPr = true, createdAtMs = 10)
        val bench = media("b", exerciseName = "Banca", canonicalExerciseId = "bench", isPr = false, createdAtMs = 11)
        val squat = media("c", exerciseName = "Sentadilla", canonicalExerciseId = "squat", isPr = true, createdAtMs = 12)
        val all = listOf(benchPr, bench, squat)
        val prOnly = WorkoutAlbumGrouping.filterMedia(all, prOnly = true, exerciseQuery = "")
        assertEquals(listOf("a", "c"), prOnly.map { it.id })
        val benchOnly = WorkoutAlbumGrouping.filterMedia(all, prOnly = false, exerciseQuery = "banca")
        assertEquals(listOf("a", "b"), benchOnly.map { it.id })
        val albums = WorkoutAlbumGrouping.group(
            WorkoutAlbumGrouping.filterMedia(all, prOnly = true, exerciseQuery = "bench"),
            zone,
        )
        assertEquals(1, albums.single().mediaCount)
        assertEquals(1, albums.single().prCount)
    }

    @Test
    fun formatsDuration() {
        assertEquals("0:05", WorkoutAlbumGrouping.formatDurationMs(5_000))
        assertEquals("1:01", WorkoutAlbumGrouping.formatDurationMs(61_000))
        assertEquals(null, WorkoutAlbumGrouping.formatDurationMs(null))
        assertEquals(null, WorkoutAlbumGrouping.formatDurationMs(0))
    }

    private fun media(
        id: String,
        workoutLogId: String? = null,
        sessionKey: String? = null,
        sessionId: String? = null,
        sessionName: String? = null,
        exerciseName: String? = null,
        canonicalExerciseId: String? = null,
        isPr: Boolean = false,
        createdAtMs: Long,
    ) = WorkoutMedia(
        id = id,
        kind = WorkoutMediaKind.PHOTO,
        filePath = "/tmp/$id.jpg",
        createdAtMs = createdAtMs,
        sessionKey = sessionKey,
        workoutLogId = workoutLogId,
        sessionId = sessionId,
        sessionName = sessionName,
        exerciseName = exerciseName,
        canonicalExerciseId = canonicalExerciseId,
        isPr = isPr,
    )

    private fun day(isoDate: String): Long =
        java.time.LocalDate.parse(isoDate).atStartOfDay().toInstant(ZoneOffset.UTC).toEpochMilli()
}
