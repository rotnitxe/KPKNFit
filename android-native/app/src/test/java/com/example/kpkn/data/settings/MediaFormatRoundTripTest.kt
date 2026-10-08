package com.example.kpkn.data.settings

import android.app.Application
import android.content.Context
import androidx.test.core.app.ApplicationProvider
import com.example.kpkn.data.account.*
import com.example.kpkn.data.backup.PortableBackup
import com.example.kpkn.data.db.*
import com.example.kpkn.data.media.WorkoutMediaGallerySaver
import com.example.kpkn.data.sync.*
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.runBlocking
import org.junit.*
import org.junit.Assert.*
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import java.io.*
import java.util.UUID

@RunWith(RobolectricTestRunner::class)
@Config(application = Application::class, manifest = Config.NONE, sdk = [26, 34])
class MediaFormatRoundTripTest {
    private lateinit var context: Context
    private val formats = linkedMapOf(
        "image/png" to byteArrayOf(0x89.toByte(), 0x50, 0x4e, 0x47, 0x0d, 0x0a, 0x1a, 0x0a, 0, 0, 0, 0),
        "image/webp" to ("RIFF".toByteArray() + byteArrayOf(4, 0, 0, 0) + "WEBP".toByteArray()),
        "image/jpeg" to byteArrayOf(0xff.toByte(), 0xd8.toByte(), 0xff.toByte(), 0xe0.toByte()),
        "video/mp4" to (byteArrayOf(0, 0, 0, 20) + "ftypisom".toByteArray()),
        "image/heic" to fixture("sample.heic"),
        "image/heif" to fixture("sample.heif"),
        "image/avif" to fixture("sample.avif"),
        "application/json" to """{"frames":[]}""".toByteArray(),
    )
    private fun fixture(name: String): ByteArray = requireNotNull(javaClass.getResourceAsStream("/media-formats/$name")).use { it.readBytes() }
    @Before fun setup() {
        val base: Context = ApplicationProvider.getApplicationContext()
        ProfileStorage.initialize(base); KpknDatabase.closeInstance()
        context = ProfileStorage.context(base, LocalProfile(uid = UUID.randomUUID().toString()))
    }
    @After fun cleanup() {
        KpknDatabase.closeInstance(); context.deleteDatabase("kpkn.db")
        context.filesDir.deleteRecursively(); context.noBackupFilesDir.deleteRecursively()
    }
    @Test fun formatsRoundTrip() = runBlocking(Dispatchers.IO) {
        val database = KpknDatabase.getInstance(context)
        val references = formats.map { (type, bytes) ->
            val stage = File(context.noBackupFilesDir, UUID.randomUUID().toString()).also { it.writeBytes(bytes) }
            val reference = MediaFileReference(if (type == "application/json") "pose" else "main", MediaFiles.hash(stage), bytes.size.toLong(), type)
            val downloaded = MediaFiles.publish(context, stage, reference)
            if (type != "application/json") database.openHelper.writableDatabase.execSQL(
                "INSERT INTO workout_media(id,kind,filePath,createdAtMs,isPr) VALUES(?,?,?,?,?)",
                arrayOf(type.replace('/', '-'), if (type == "video/mp4") "VIDEO" else "PHOTO", downloaded.absolutePath, 1L, 0))
            reference
        }
        val archive = ByteArrayOutputStream(); PortableBackup.export(context, archive)
        File(context.filesDir, "workout_media").deleteRecursively()
        PortableBackup.restore(context, ByteArrayInputStream(archive.toByteArray()))
        references.forEach { reference ->
            val restored = MediaFiles.local(context, reference)
            MediaFiles.verify(restored, reference)
            assertEquals(MediaFiles.extension(reference.contentType), restored.extension)
            if (reference.role != "pose") {
                val descriptor = requireNotNull(WorkoutMediaGallerySaver.verifiedExportType(restored))
                assertEquals(reference.contentType, descriptor.second)
                assertTrue(descriptor.first.endsWith("." + restored.extension))
                assertEquals(restored.absolutePath, database.workoutMediaDao().getById(reference.contentType.replace('/', '-'))!!.filePath)
            }
        }
    }
    @Test fun wrongDeclaredTypeCannotBePublished() {
        val file = File(context.noBackupFilesDir, "mismatch").also { it.writeBytes(formats.getValue("image/png")) }
        assertThrows(IllegalArgumentException::class.java) {
            MediaFiles.publish(context, file, MediaFileReference("main", MediaFiles.hash(file), file.length(), "image/jpeg"))
        }
    }
    @Test fun oldHashOnlyGalleryFileIsDetectedByContent() {
        val file = File(context.filesDir, "old-hash").also { it.writeBytes(formats.getValue("image/webp")) }
        assertEquals("old-hash.webp" to "image/webp", WorkoutMediaGallerySaver.verifiedExportType(file))
    }
}
