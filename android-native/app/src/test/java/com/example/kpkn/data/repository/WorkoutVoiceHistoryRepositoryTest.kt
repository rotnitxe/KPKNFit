package com.example.kpkn.data.repository

import android.app.Application
import android.content.Context
import androidx.test.core.app.ApplicationProvider
import com.example.kpkn.data.db.KpknDatabase
import com.example.kpkn.domain.workout.VoiceHistoryEntry
import com.example.kpkn.domain.workout.VoiceHistoryRetention
import com.example.kpkn.domain.workout.VoiceHistoryStatus
import com.example.kpkn.domain.workout.VoiceHistoryValues
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.runBlocking
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class)
@Config(manifest = Config.NONE, sdk = [34], application = Application::class)
class WorkoutVoiceHistoryRepositoryTest {
    private lateinit var db: KpknDatabase
    private lateinit var repository: WorkoutVoiceHistoryRepository
    private val context: Context get() = ApplicationProvider.getApplicationContext()

    @Before fun setUp() {
        db = KpknDatabase.createInMemory(context)
        repository = WorkoutVoiceHistoryRepository.forDatabase(context, db)
    }

    @After fun tearDown() {
        db.close()
    }

    @Test fun recordedCaptureKeepsTranscriptAndValuesInCreationOrder() = runBlocking {
        val now = System.currentTimeMillis()
        repository.record(entry("a", now, setIdx = 0))
        repository.record(entry("b", now + 1_000, setIdx = 1))

        val stored = repository.observe("session").first()

        assertEquals(listOf("a", "b"), stored.map { it.id })
        assertEquals("ochenta kilos ocho repeticiones rir dos", stored.first().transcript)
        assertEquals(VoiceHistoryValues(weightKg = 80.0, reps = 8, intensityValue = 2.0, intensityKind = "RIR"), stored.first().values)
        assertEquals(VoiceHistoryStatus.REVIEW, stored.first().status)
        assertEquals(1, stored.first().revision)
        assertNull(stored.first().audit)
    }

    @Test fun markRegisteredOnlyTouchesTheMatchingReviewCapture() = runBlocking {
        val now = System.currentTimeMillis()
        repository.record(entry("a", now, setIdx = 0))
        repository.record(entry("b", now + 1_000, setIdx = 1))

        repository.markRegistered("session", "squat", 0, null, now + 2_000)

        val stored = repository.observe("session").first()
        assertEquals(VoiceHistoryStatus.REGISTERED, stored.first { it.id == "a" }.status)
        assertEquals(VoiceHistoryStatus.REVIEW, stored.first { it.id == "b" }.status)
        assertEquals(1, stored.first { it.id == "a" }.revision)
    }

    @Test fun correctionIncrementsRevisionKeepsRegisteredStatusAndAuditsBothSides() = runBlocking {
        val now = System.currentTimeMillis()
        repository.record(entry("a", now, status = VoiceHistoryStatus.REGISTERED))
        val correctedValues = VoiceHistoryValues(weightKg = 82.5, reps = 7, intensityValue = 1.0, intensityKind = "RIR")

        val corrected = repository.correct("a", correctedValues, now + 5_000)

        assertNotNull(corrected)
        assertEquals(2, corrected!!.revision)
        assertEquals(VoiceHistoryStatus.REGISTERED, corrected.status)
        assertEquals("ochenta kilos ocho repeticiones rir dos", corrected.transcript)
        assertEquals(VoiceHistoryValues(weightKg = 80.0, reps = 8, intensityValue = 2.0, intensityKind = "RIR"), corrected.audit!!.previous)
        assertEquals(correctedValues, corrected.audit!!.next)
        assertEquals(now + 5_000, corrected.updatedAtEpochMs)
        assertEquals(corrected, repository.observe("session").first().single())
    }

    @Test fun retentionDropsCapturesOlderThanThirtyDaysOnInsert() = runBlocking {
        val now = System.currentTimeMillis()
        repository.record(entry("old", now - VoiceHistoryRetention.RETENTION_MS - 1_000))
        repository.record(entry("new", now, setIdx = 1))

        assertEquals(listOf("new"), repository.observe("session").first().map { it.id })
    }

    @Test fun retentionKeepsNewestFiveHundredCapturesPerSessionAndLeavesOtherSessions() = runBlocking {
        val now = System.currentTimeMillis()
        repeat(VoiceHistoryRetention.MAX_ENTRIES_PER_SESSION + 5) { index ->
            repository.record(entry("a$index", now + index.toLong(), setIdx = index))
        }
        repository.record(entry("other", now + 10_000, sessionKey = "other-session"))

        val stored = repository.observe("session").first()
        assertEquals(VoiceHistoryRetention.MAX_ENTRIES_PER_SESSION, stored.size)
        assertEquals("a5", stored.first().id)
        assertEquals("a${VoiceHistoryRetention.MAX_ENTRIES_PER_SESSION + 4}", stored.last().id)
        assertEquals(listOf("other"), repository.observe("other-session").first().map { it.id })
        assertTrue(stored.all { it.sessionKey == "session" })
    }

    private fun entry(
        id: String,
        createdAtEpochMs: Long,
        setIdx: Int = 0,
        sessionKey: String = "session",
        status: VoiceHistoryStatus = VoiceHistoryStatus.REVIEW,
    ) = VoiceHistoryEntry(
        id = id,
        sessionKey = sessionKey,
        exerciseId = "squat",
        exerciseName = "Sentadilla",
        setIdx = setIdx,
        transcript = "ochenta kilos ocho repeticiones rir dos",
        values = VoiceHistoryValues(weightKg = 80.0, reps = 8, intensityValue = 2.0, intensityKind = "RIR"),
        status = status,
        revision = 1,
        createdAtEpochMs = createdAtEpochMs,
        updatedAtEpochMs = createdAtEpochMs,
    )
}
