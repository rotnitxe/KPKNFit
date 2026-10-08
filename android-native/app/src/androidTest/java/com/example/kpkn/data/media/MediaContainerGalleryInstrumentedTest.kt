package com.example.kpkn.data.media

import android.graphics.ImageDecoder
import android.provider.MediaStore
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.filters.SdkSuppress
import androidx.test.platform.app.InstrumentationRegistry
import com.example.kpkn.data.models.WorkoutMedia
import com.example.kpkn.data.models.WorkoutMediaKind
import org.junit.Assert.*
import org.junit.Test
import org.junit.runner.RunWith
import java.io.File
import java.util.UUID

@RunWith(AndroidJUnit4::class)
@SdkSuppress(minSdkVersion = 31)
class MediaContainerGalleryInstrumentedTest {
    @Test fun heicPhotoDecodesAndExportsOriginalBytes() = export("heic", "image/heic")
    @Test fun heifPhotoDecodesAndExportsOriginalBytes() = export("heif", "image/heif")
    @Test fun avifPhotoDecodesAndExportsOriginalBytes() = export("avif", "image/avif")

    private fun export(extension: String, mime: String) {
        val instrumentation = InstrumentationRegistry.getInstrumentation()
        val context = instrumentation.targetContext
        val bytes = instrumentation.context.assets.open("media-formats/sample.$extension").use { it.readBytes() }
        val id = "kpkn-media-regression-${UUID.randomUUID()}"
        // The historical local file may have a wrong extension; content determines export metadata.
        val source = File(context.cacheDir, "$id.jpg").also { it.writeBytes(bytes) }
        val collection = MediaStore.Images.Media.getContentUri(MediaStore.VOLUME_EXTERNAL_PRIMARY)
        var exported: android.net.Uri? = null
        try {
            val bitmap = ImageDecoder.decodeBitmap(ImageDecoder.createSource(source))
            assertEquals(16, bitmap.width); assertEquals(16, bitmap.height); bitmap.recycle()
            assertTrue(WorkoutMediaGallerySaver.save(context, WorkoutMedia(id = id, kind = WorkoutMediaKind.PHOTO,
                filePath = source.absolutePath, createdAtMs = 1L)))
            context.contentResolver.query(collection, arrayOf(MediaStore.Images.Media._ID, MediaStore.MediaColumns.MIME_TYPE),
                "${MediaStore.MediaColumns.DISPLAY_NAME} = ?", arrayOf("$id.$extension"), null)!!.use { cursor ->
                assertTrue(cursor.moveToFirst())
                assertEquals(mime, cursor.getString(1))
                exported = android.content.ContentUris.withAppendedId(collection, cursor.getLong(0))
                assertFalse(cursor.moveToNext())
            }
            assertArrayEquals(bytes, context.contentResolver.openInputStream(requireNotNull(exported))!!.use { it.readBytes() })
        } finally {
            exported?.let { context.contentResolver.delete(it, null, null) }
            source.delete()
        }
    }
}
