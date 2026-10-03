package com.example.kpkn.services.cardio

import com.example.kpkn.domain.cardio.GpsTrackPoint
import com.example.kpkn.domain.cardio.GpsTrackSnapshot
import java.io.File
import java.io.IOException
import java.nio.file.Files
import java.nio.file.StandardCopyOption.ATOMIC_MOVE
import java.nio.file.StandardCopyOption.REPLACE_EXISTING
import java.util.concurrent.TimeUnit
import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.Json
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.rules.TemporaryFolder

class CardioGpsPersistenceTest {
    @get:Rule
    val temporaryFolder = TemporaryFolder()

    private val json = Json { encodeDefaults = true }
    private val store = CardioGpsSnapshotFileStore(json) { source, destination ->
        Files.move(source.toPath(), destination.toPath(), ATOMIC_MOVE, REPLACE_EXISTING)
    }

    @Test
    fun valid_pending_snapshot_is_recovered_and_atomically_promoted() {
        val destination = File(temporaryFolder.root, "route.json")
        val first = snapshot("current-key", timestamps = listOf(1_000L))
        val recovered = snapshot("current-key", timestamps = listOf(1_000L, 2_000L))
        store.write(destination, first)
        File(destination.parentFile, "${destination.name}.pending")
            .writeText(json.encodeToString(GpsTrackSnapshot.serializer(), recovered))

        val result = store.read(destination, "current-key")

        assertNotNull(result)
        assertEquals(GpsSnapshotSource.CURRENT, result?.source)
        assertEquals(recovered, result?.snapshot)
        assertEquals(recovered, json.decodeFromString(GpsTrackSnapshot.serializer(), destination.readText()))
        assertFalse(File(destination.parentFile, "${destination.name}.pending").exists())
    }

    @Test
    fun failed_atomic_replace_keeps_previous_file_and_next_read_recovers_pending_payload() {
        val destination = File(temporaryFolder.root, "route.json")
        val committed = snapshot("current-key", timestamps = listOf(1_000L))
        val newer = snapshot("current-key", timestamps = listOf(1_000L, 2_000L))
        store.write(destination, committed)
        val failingStore = CardioGpsSnapshotFileStore(json) { _, _ ->
            throw IOException("simulated rename failure")
        }

        runCatching { failingStore.write(destination, newer) }

        assertEquals(committed, json.decodeFromString(GpsTrackSnapshot.serializer(), destination.readText()))
        assertTrue(File(destination.parentFile, "${destination.name}.pending").exists())
        val recovered = store.read(destination, "current-key")
        assertEquals(newer, recovered?.snapshot)
        assertEquals(newer, json.decodeFromString(GpsTrackSnapshot.serializer(), destination.readText()))
    }

    @Test
    fun legacy_read_keeps_legacy_identity_until_the_current_file_is_written() {
        val currentFile = File(temporaryFolder.root, "current.json")
        val legacyFile = File(temporaryFolder.root, "legacy.json")
        val legacy = snapshot("legacy-key", timestamps = listOf(2_000L))
        legacyFile.writeText(json.encodeToString(GpsTrackSnapshot.serializer(), legacy))

        val result = store.read(
            currentFile = currentFile,
            currentSessionKey = "current-key",
            legacyFile = legacyFile,
            legacySessionKey = "legacy-key",
        )

        assertEquals(GpsSnapshotSource.LEGACY, result?.source)
        assertEquals("legacy-key", result?.snapshot?.sessionKey)
        assertTrue(legacyFile.exists())
    }

    @Test
    fun serialized_clear_runs_after_queued_write_and_removes_pending_and_legacy_files() {
        val currentFile = File(temporaryFolder.root, "current.json")
        val legacyFile = File(temporaryFolder.root, "legacy.json")
        val queue = CardioGpsPersistenceQueue()
        File(currentFile.parentFile, "${currentFile.name}.pending").writeText("stale")
        legacyFile.writeText("legacy")

        try {
            queue.execute { store.write(currentFile, snapshot("current-key", listOf(1_000L))) }
            queue.execute { store.delete(currentFile, legacyFile) }
            queue.submit { Unit }.get(5L, TimeUnit.SECONDS)

            assertFalse(currentFile.exists())
            assertFalse(File(currentFile.parentFile, "${currentFile.name}.pending").exists())
            assertFalse(legacyFile.exists())
        } finally {
            queue.close()
        }
    }

    private fun snapshot(sessionKey: String, timestamps: List<Long>) = GpsTrackSnapshot(
        sessionKey = sessionKey,
        points = timestamps.mapIndexed { index, timestamp ->
            GpsTrackPoint(
                timestampEpochMs = timestamp,
                latitude = 51.0,
                longitude = -0.1 + index * 0.001,
                accuracyMeters = 5f,
            )
        },
        distanceMeters = timestamps.size.toDouble(),
        elapsedActiveSeconds = timestamps.size.toLong(),
        paused = true,
    )
}
