package com.example.kpkn.data.settings

import android.app.Application
import android.content.Context
import androidx.test.core.app.ApplicationProvider
import com.example.kpkn.data.account.LocalProfile
import com.example.kpkn.data.account.ProfileStorage
import com.example.kpkn.data.sync.MediaFiles
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.jsonObject
import org.junit.Assert.*
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import java.io.File
import java.util.UUID

@RunWith(RobolectricTestRunner::class)
@Config(application = Application::class, manifest = Config.NONE, sdk = [34])
class MediaFilesTest {
    @Test fun immutableStages() {
        val base: Context = ApplicationProvider.getApplicationContext(); ProfileStorage.initialize(base)
        val context = ProfileStorage.context(base, LocalProfile(uid = UUID.randomUUID().toString()))
        val firstBytes = byteArrayOf(0xff.toByte(), 0xd8.toByte(), 0xff.toByte()) + "first".toByteArray()
        val secondBytes = byteArrayOf(0xff.toByte(), 0xd8.toByte(), 0xff.toByte()) + "second".toByteArray()
        val original = File(context.filesDir, "workout_media/photo.jpg").also { it.parentFile!!.mkdirs(); it.writeBytes(firstBytes) }
        try {
            val first = MediaFiles.stage(context, original.path, "main", false)!!
            original.writeBytes(secondBytes)
            val second = MediaFiles.stage(context, original.path, "main", false)!!
            assertNotEquals(first.sha256, second.sha256)
            assertArrayEquals(firstBytes, File(context.noBackupFilesDir, "sync-media/${first.sha256}").readBytes())
            assertArrayEquals(secondBytes, File(context.noBackupFilesDir, "sync-media/${second.sha256}").readBytes())
        } finally { context.filesDir.deleteRecursively(); context.noBackupFilesDir.deleteRecursively() }
    }
    @Test fun crossProfileRejected() {
        val base: Context = ApplicationProvider.getApplicationContext(); ProfileStorage.initialize(base)
        val a = ProfileStorage.context(base, LocalProfile(uid = UUID.randomUUID().toString()))
        val b = ProfileStorage.context(base, LocalProfile(uid = UUID.randomUUID().toString()))
        val file = File(a.filesDir, "workout_media/private.jpg").also { it.parentFile!!.mkdirs(); it.writeText("private") }
        try { assertThrows(IllegalArgumentException::class.java) { MediaFiles.stage(b, file.path, "main", false) } }
        finally { a.filesDir.deleteRecursively(); b.filesDir.deleteRecursively() }
    }
    @Test fun forgedReferencesRejected() {
        val payload = Json.parseToJsonElement("""{"mediaFiles":[{"role":"main","sha256":"../../other-profile","bytes":5,"contentType":"image/jpeg"}]}""").jsonObject
        assertThrows(IllegalArgumentException::class.java) { MediaFiles.references(payload) }
    }
    @Test fun cloudFilesWithoutExtensionsKeepTheirImageType() {
        val base: Context = ApplicationProvider.getApplicationContext(); ProfileStorage.initialize(base)
        val context = ProfileStorage.context(base, LocalProfile(uid = UUID.randomUUID().toString()))
        val image = File(context.filesDir, "workout_media/cloud/hash-only-name").also {
            it.parentFile!!.mkdirs()
            it.writeBytes(byteArrayOf(0x89.toByte(), 0x50, 0x4e, 0x47, 0x0d, 0x0a, 0x1a, 0x0a, 0, 0, 0, 0))
        }
        try { assertEquals("image/png", MediaFiles.stage(context, image.path, "main", false)!!.contentType) }
        finally { context.filesDir.deleteRecursively(); context.noBackupFilesDir.deleteRecursively() }
    }
}
