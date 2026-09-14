package com.example.kpkn.data.media

import android.app.Application
import android.content.Context
import androidx.test.core.app.ApplicationProvider
import com.example.kpkn.data.db.KpknDatabase
import com.example.kpkn.data.db.toEntity
import com.example.kpkn.data.db.toSettings
import com.example.kpkn.data.db.toWorkoutLog
import com.example.kpkn.data.models.CompletedExercise
import com.example.kpkn.data.models.WorkoutLog
import com.example.kpkn.data.models.WorkoutMediaKind
import com.example.kpkn.data.repository.WorkoutMediaRepository
import kotlinx.coroutines.runBlocking
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
import java.io.File
import java.time.Instant

@RunWith(RobolectricTestRunner::class)
@Config(manifest = Config.NONE, sdk = [34], application = Application::class)
class WorkoutMediaLegacyImporterTest {
    private val context: Context get() = ApplicationProvider.getApplicationContext()
    private val databases = mutableListOf<KpknDatabase>()

    private fun db(): KpknDatabase =
        KpknDatabase.createInMemory(context).also { databases += it }

    @After
    fun tearDown() {
        databases.forEach { it.close() }
        databases.clear()
        KpknDatabase.closeInstance()
        WorkoutMediaRepository.closeInstance()
        File(context.filesDir, WorkoutMediaLegacyImporter.EXERCISE_USER_MEDIA_DIR).deleteRecursively()
        File(context.filesDir, WorkoutMediaLegacyImporter.WORKOUT_PHOTOS_DIR).deleteRecursively()
        File(context.filesDir, WorkoutMediaStore.ROOT_DIR).deleteRecursively()
        File(context.filesDir, "orphan").deleteRecursively()
    }

    @Test
    fun import_copies_three_silos_without_deleting_sources_or_mutating_log_json() = runBlocking {
        val database = db()
        val filesDir = context.filesDir
        val squatPhoto = writeFile(File(filesDir, "exercise_user_media/squat/photo_1.jpg"), "squat-photo")
        val squatVideo = writeFile(File(filesDir, "exercise_user_media/squat/video_1.mp4"), "squat-video")
        val cockpit = writeFile(File(filesDir, "workout_photos/sess-a/session/cockpit.jpg"), "cockpit")
        val form = writeFile(File(filesDir, "workout_photos/sess-a/bench/form.jpg"), "form")
        val extra = writeFile(File(filesDir, "workout_photos/sess-a/deadlift/extra.jpg"), "extra")
        val jsonOnly = writeFile(File(filesDir, "orphan/from_log.jpg"), "json-only")
        val sept = Instant.parse("2026-09-01T12:00:00Z").toEpochMilli()
        assertTrue(form.setLastModified(sept))
        assertTrue(cockpit.setLastModified(sept))
        assertTrue(extra.setLastModified(sept))

        val oldLog = WorkoutLog(
            id = "log-old",
            programId = "prog",
            sessionId = "sess-a",
            sessionName = "Enero",
            date = "2026-01-01T12:00:00Z",
            durationMinutes = 40,
        )
        val newLog = WorkoutLog(
            id = "log-new",
            programId = "prog",
            sessionId = "sess-a",
            sessionName = "Septiembre",
            date = "2026-09-01T12:00:00Z",
            durationMinutes = 50,
            completedExercises = listOf(
                CompletedExercise(
                    exerciseId = "bench",
                    exerciseName = "Press banca",
                    canonicalExerciseId = "bench-can",
                ),
            ),
            exercisePhotos = mapOf("bench" to listOf(form.absolutePath)),
            sessionPhotos = listOf(cockpit.absolutePath, jsonOnly.absolutePath, "/missing/nope.jpg"),
        )
        database.workoutLogDao().insert(oldLog.toEntity())
        database.workoutLogDao().insert(newLog.toEntity())

        val importer = WorkoutMediaLegacyImporter(filesDir, database)
        val first = importer.importIfNeeded()
        assertFalse(first.skippedBecauseAlreadyDone)
        assertEquals(6, first.importedCount)
        assertTrue(first.skippedMissingCount >= 1)

        assertTrue(squatPhoto.exists())
        assertTrue(squatVideo.exists())
        assertTrue(cockpit.exists())
        assertTrue(form.exists())
        assertTrue(extra.exists())
        assertTrue(jsonOnly.exists())
        assertEquals("squat-photo", squatPhoto.readText())
        assertEquals("cockpit", cockpit.readText())

        val rows = database.workoutMediaDao().getAll()
        assertEquals(6, rows.size)
        assertTrue(rows.all { File(it.filePath).exists() })
        assertTrue(rows.all { WorkoutMediaStore.canonicalPath(File(it.filePath)).contains("/workout_media/") })
        assertTrue(rows.none { it.filePath == squatPhoto.absolutePath })

        val squatRows = rows.filter { it.canonicalExerciseId == "squat" }
        assertEquals(2, squatRows.size)
        assertEquals(1, squatRows.count { it.kind == WorkoutMediaKind.VIDEO.name })
        assertTrue(squatRows.all { it.workoutLogId == null })

        val cockpitRow = rows.single { File(it.filePath).readText() == "cockpit" }
        assertEquals("log-new", cockpitRow.workoutLogId)
        assertEquals("Septiembre", cockpitRow.sessionName)
        assertNull(cockpitRow.exerciseId)

        val formRow = rows.single { File(it.filePath).readText() == "form" }
        assertEquals("log-new", formRow.workoutLogId)
        assertEquals("bench", formRow.exerciseId)
        assertEquals("bench-can", formRow.canonicalExerciseId)
        assertEquals("Press banca", formRow.exerciseName)

        val extraRow = rows.single { File(it.filePath).readText() == "extra" }
        assertEquals("log-new", extraRow.workoutLogId)
        assertEquals("deadlift", extraRow.exerciseId)
        assertNull(extraRow.exerciseName)

        val jsonRow = rows.single { File(it.filePath).readText() == "json-only" }
        assertEquals("log-new", jsonRow.workoutLogId)

        val persistedLog = database.workoutLogDao().getById("log-new")!!.toWorkoutLog()!!
        assertEquals(newLog.sessionPhotos, persistedLog.sessionPhotos)
        assertEquals(newLog.exercisePhotos, persistedLog.exercisePhotos)

        val settings = database.settingsDao().get()!!.toSettings()
        assertTrue(settings.workoutMediaLegacyImportDone)

        val second = importer.importIfNeeded()
        assertTrue(second.skippedBecauseAlreadyDone)
        assertEquals(6, database.workoutMediaDao().count())

        val forced = importer.importIfNeeded(force = true)
        assertEquals(6, database.workoutMediaDao().count())
        assertEquals(0, forced.importedCount)
        assertTrue(forced.skippedDuplicateCount >= 6)

        val repo = WorkoutMediaRepository.forDatabase(context, database)
        assertEquals(6, repo.listAll().size)
        assertEquals(4, repo.listForWorkoutLog("log-new").size)
        assertEquals(2, repo.listForExercise("squat").size)
        assertNotNull(repo.store().root().takeIf { it.isDirectory })
    }

    @Test
    fun closest_log_picks_nearest_date() {
        val logs = listOf(
            WorkoutLog(id = "old", programId = "p", sessionId = "s", sessionName = "A", date = "2026-01-01T12:00:00Z", durationMinutes = 1),
            WorkoutLog(id = "new", programId = "p", sessionId = "s", sessionName = "B", date = "2026-09-01T12:00:00Z", durationMinutes = 1),
        )
        val sept = Instant.parse("2026-09-01T12:00:00Z").toEpochMilli()
        assertEquals("new", WorkoutMediaLegacyImporter.closestLog(logs, sept)?.id)
    }

    private fun writeFile(file: File, content: String): File {
        file.parentFile.mkdirs()
        file.writeText(content)
        return file
    }
}
