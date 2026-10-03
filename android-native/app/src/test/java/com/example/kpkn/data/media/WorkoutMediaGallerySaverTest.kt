package com.example.kpkn.data.media

import android.app.Application
import android.content.ContentProvider
import android.content.ContentValues
import android.content.res.AssetFileDescriptor
import android.database.Cursor
import android.net.Uri
import android.os.ParcelFileDescriptor
import android.provider.MediaStore
import androidx.test.core.app.ApplicationProvider
import com.example.kpkn.data.models.WorkoutMedia
import com.example.kpkn.data.models.WorkoutMediaKind
import org.junit.Assert.assertArrayEquals
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.Robolectric
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import java.io.File
import java.util.UUID

@RunWith(RobolectricTestRunner::class)
@Config(manifest = Config.NONE, sdk = [34], application = Application::class)
class WorkoutMediaGallerySaverTest {
    private val context get() = ApplicationProvider.getApplicationContext<Application>()

    @Test
    fun null_output_stream_deletes_pending_uri_and_same_media_retry_leaves_one_published_row() {
        val provider = Robolectric.setupContentProvider(TrackingMediaStoreProvider::class.java, "media")
        val testDir = File(context.cacheDir, "gallery-saver-${UUID.randomUUID()}").apply { mkdirs() }
        provider.outputDirectory = testDir
        val fixtures = listOf(
            FormatFixture("png", "image/png", pngSignature()),
            FormatFixture("webp", "image/webp", webpSignature()),
        )

        try {
            fixtures.forEachIndexed { index, fixture ->
                val rowCountBeforeAttempt = provider.rows.size
                val deleteCountBeforeAttempt = provider.deleted.size
                val mediaId = "gallery-null-stream-$index-${UUID.randomUUID()}"
                val source = File(testDir, "$mediaId.${fixture.extension}").apply {
                    writeBytes(fixture.bytes)
                }
                val media = WorkoutMedia(
                    id = mediaId,
                    kind = WorkoutMediaKind.PHOTO,
                    filePath = source.absolutePath,
                    createdAtMs = 1_700_000_000_000L + index,
                    sessionKey = "program::session::$mediaId",
                    programId = "program",
                    sessionId = "session",
                )

                provider.returnNullAssetDescriptor = true
                assertFalse(WorkoutMediaGallerySaver.save(context, media))

                val failedInsert = provider.inserted.last()
                assertEquals(fixture.mime, failedInsert.mime)
                assertEquals(source.name, failedInsert.displayName)
                assertTrue("inserted row should start pending", failedInsert.pending)
                assertEquals("failure must leave no new pending row", rowCountBeforeAttempt, provider.rows.size)
                assertFalse(provider.rows.containsKey(failedInsert.uri))
                assertEquals(failedInsert.uri, provider.deleted.last())
                assertEquals(deleteCountBeforeAttempt + 1, provider.deleted.size)

                provider.returnNullAssetDescriptor = false
                assertTrue(WorkoutMediaGallerySaver.save(context, media))

                val retryInsert = provider.inserted.last()
                assertEquals(fixture.mime, retryInsert.mime)
                assertEquals(source.name, retryInsert.displayName)
                assertFalse("retry inserts a fresh MediaStore row", failedInsert.uri == retryInsert.uri)
                assertEquals("retry must not leave the first pending row behind", rowCountBeforeAttempt + 1, provider.rows.size)
                assertTrue(provider.rows.containsKey(retryInsert.uri))
                assertFalse("successful retry should publish the row", provider.rows.getValue(retryInsert.uri))
                assertTrue("all surviving rows should be published", provider.rows.values.none { it })
                assertEquals(deleteCountBeforeAttempt + 1, provider.deleted.size)
                assertArrayEquals(fixture.bytes, requireNotNull(provider.outputFiles.last()).readBytes())
            }
        } finally {
            testDir.deleteRecursively()
        }
    }

    private data class FormatFixture(
        val extension: String,
        val mime: String,
        val bytes: ByteArray,
    )

    private fun pngSignature() = byteArrayOf(
        0x89.toByte(), 0x50, 0x4E, 0x47, 0x0D, 0x0A, 0x1A, 0x0A,
    )

    private fun webpSignature() = byteArrayOf(
        0x52, 0x49, 0x46, 0x46, 0x04, 0x00, 0x00, 0x00, 0x57, 0x45, 0x42, 0x50,
    )

    class TrackingMediaStoreProvider : ContentProvider() {
        data class InsertedRow(val uri: Uri, val mime: String?, val displayName: String?, val pending: Boolean)

        val inserted = mutableListOf<InsertedRow>()
        val deleted = mutableListOf<Uri>()
        val rows = linkedMapOf<Uri, Boolean>()
        val outputFiles = mutableListOf<File>()
        var returnNullAssetDescriptor: Boolean = true
        lateinit var outputDirectory: File

        override fun onCreate(): Boolean = true

        override fun getType(uri: Uri): String? = null

        override fun query(
            uri: Uri,
            projection: Array<out String>?,
            selection: String?,
            selectionArgs: Array<out String>?,
            sortOrder: String?,
        ): Cursor? = null

        override fun insert(uri: Uri, values: ContentValues?): Uri? {
            val rowUri = Uri.withAppendedPath(uri, (inserted.size + 1).toString())
            val pending = values?.getAsInteger(MediaStore.MediaColumns.IS_PENDING) == 1
            inserted += InsertedRow(
                uri = rowUri,
                mime = values?.getAsString(MediaStore.MediaColumns.MIME_TYPE),
                displayName = values?.getAsString(MediaStore.MediaColumns.DISPLAY_NAME),
                pending = pending,
            )
            rows[rowUri] = pending
            return rowUri
        }

        override fun delete(uri: Uri, selection: String?, selectionArgs: Array<out String>?): Int {
            deleted += uri
            return if (rows.remove(uri) != null) 1 else 0
        }

        override fun update(
            uri: Uri,
            values: ContentValues?,
            selection: String?,
            selectionArgs: Array<out String>?,
        ): Int {
            if (uri !in rows) return 0
            val pending = values?.getAsInteger(MediaStore.MediaColumns.IS_PENDING)
            if (pending != null) rows[uri] = pending == 1
            return 1
        }

        override fun openAssetFile(uri: Uri, mode: String): AssetFileDescriptor? {
            if (returnNullAssetDescriptor) return null
            val output = File(outputDirectory, "gallery-provider-${uri.lastPathSegment}.bin")
            output.parentFile?.mkdirs()
            outputFiles += output
            val descriptor = ParcelFileDescriptor.open(
                output,
                ParcelFileDescriptor.MODE_CREATE or
                    ParcelFileDescriptor.MODE_TRUNCATE or
                    ParcelFileDescriptor.MODE_WRITE_ONLY,
            )
            return AssetFileDescriptor(descriptor, 0L, -1L)
        }
    }
}
