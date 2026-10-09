package com.example.kpkn.data.media

import android.app.Application
import android.content.Context
import androidx.test.core.app.ApplicationProvider
import com.example.kpkn.data.account.LocalProfile
import com.example.kpkn.data.account.ProfileStorage
import com.example.kpkn.data.db.KpknDatabase
import com.example.kpkn.data.models.WorkoutMedia
import com.example.kpkn.data.models.WorkoutMediaKind
import com.example.kpkn.data.repository.MediaPersistenceOwner
import com.example.kpkn.data.repository.WorkoutMediaRepository
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.runBlocking
import org.junit.After
import org.junit.Assert.*
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import java.io.File
import java.util.UUID

@RunWith(RobolectricTestRunner::class)
@Config(application = Application::class, manifest = Config.NONE, sdk = [34])
class WorkoutMediaDataSessionTest {
    private val context: Context = ApplicationProvider.getApplicationContext()

    @Before fun setup() {
        MediaPersistenceOwner.writes.resume()
        WorkoutMediaRepository.closeInstance()
        KpknDatabase.closeInstance()
        ProfileStorage.initialize(context)
        context.deleteDatabase("kpkn.db")
    }

    @After fun cleanup() {
        runBlocking { MediaPersistenceOwner.writes.pauseAndAwait() }
        MediaPersistenceOwner.writes.resume()
        WorkoutMediaRepository.closeInstance()
        KpknDatabase.closeInstance()
        context.deleteDatabase("kpkn.db")
        File(context.filesDir, "workout_media").deleteRecursively()
    }

    @Test fun closedRepositoryCannotRecreateErasedMediaFiles() = runBlocking(Dispatchers.IO) {
        val old = WorkoutMediaRepository.init(context)
        old.recoverPendingCaptures().await()
        // The same close invoked by data-session maintenance, followed by erasure.
        WorkoutMediaRepository.closeInstance()
        val root = File(context.filesDir, "workout_media")
        root.deleteRecursively()
        val late = runCatching {
            old.prepareCameraCaptureFile(UUID.randomUUID().toString(), WorkoutMediaKind.PHOTO, 1700000000000L)
        }
        assertTrue("A disposed repository must reject a late camera writer", late.isFailure)
        assertFalse("Erased personal storage must not be recreated", root.exists())
    }

    @Test fun profileSwitchAndGuestReturnRejectOldFileAndDatabaseWriters() = runBlocking(Dispatchers.IO) {
        val firstContext = ProfileStorage.context(context, LocalProfile(uid = "media-barrier-A"))
        val secondContext = ProfileStorage.context(context, LocalProfile(uid = "media-barrier-B"))
        val guestContext = ProfileStorage.context(context, LocalProfile(guestId = "media-barrier-guest"))
        val contexts = listOf(firstContext, secondContext, guestContext)
        val databases = contexts.map { KpknDatabase.openIsolated(it, "kpkn.db") }
        try {
            val old = WorkoutMediaRepository.forDatabase(firstContext, databases[0])
            old.recoverPendingCaptures().await()
            val original = WorkoutMedia("A-photo", WorkoutMediaKind.PHOTO,
                File(firstContext.filesDir, "workout_media/A.jpg").absolutePath, createdAtMs = 1700000000000L)
            old.upsert(original)
            MediaPersistenceOwner.writes.pauseAndAwait()
            assertTrue(runCatching { old.prepareCameraCaptureFile("late", WorkoutMediaKind.PHOTO, 1700000000000L) }.isFailure)
            MediaPersistenceOwner.writes.resume()
            val second = WorkoutMediaRepository.forDatabase(secondContext, databases[1])
            second.recoverPendingCaptures().await()
            assertTrue(runCatching { old.upsert(original.copy(id = "late-A")) }.isFailure)
            assertEquals(listOf("A-photo"), old.listAll().map { it.id })
            assertTrue(second.listAll().isEmpty())
            MediaPersistenceOwner.writes.pauseAndAwait()
            MediaPersistenceOwner.writes.resume()
            val guest = WorkoutMediaRepository.forDatabase(guestContext, databases[2])
            guest.recoverPendingCaptures().await()
            assertTrue(guest.listAll().isEmpty())
            assertTrue(runCatching { second.prepareCameraCaptureFile("late-B", WorkoutMediaKind.PHOTO, 1700000000000L) }.isFailure)
            assertFalse(File(secondContext.filesDir, "workout_media").exists())
        } finally {
            MediaPersistenceOwner.writes.pauseAndAwait()
            MediaPersistenceOwner.writes.resume()
            databases.forEach { it.close() }
            contexts.forEach { it.deleteDatabase("kpkn.db"); it.filesDir.deleteRecursively() }
        }
    }
}
