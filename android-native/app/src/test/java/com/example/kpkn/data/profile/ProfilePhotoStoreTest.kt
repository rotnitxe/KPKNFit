package com.example.kpkn.data.profile

import android.graphics.Bitmap
import android.util.Base64
import androidx.test.core.app.ApplicationProvider
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import java.io.ByteArrayOutputStream

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [34])
class ProfilePhotoStoreTest {
    private val context = ApplicationProvider.getApplicationContext<android.content.Context>()

    @After
    fun tearDown() {
        ProfilePhotoStore.delete(context)
    }

    @Test
    fun `base64 round trip normalizes avatar to private 512px jpeg`() {
        val source = Bitmap.createBitmap(1024, 256, Bitmap.Config.ARGB_8888)
        val sourceBytes = ByteArrayOutputStream().also { output ->
            source.compress(Bitmap.CompressFormat.PNG, 100, output)
        }.toByteArray()
        source.recycle()

        val token = ProfilePhotoStore.saveBase64(context, Base64.encodeToString(sourceBytes, Base64.NO_WRAP))
        val encoded = ProfilePhotoStore.readBase64(context, token)
        val restored = ProfilePhotoStore.loadBitmap(context, token)

        assertEquals(ProfilePhotoStore.STORAGE_TOKEN, token)
        assertNotNull(encoded)
        assertTrue(Base64.decode(encoded, Base64.DEFAULT).size <= 512 * 1024)
        assertNotNull(restored)
        assertTrue(restored!!.width <= 512)
        assertTrue(restored.height <= 512)
        restored.recycle()
    }

    @Test
    fun `external token and invalid image never resolve as avatar`() {
        assertNull(ProfilePhotoStore.readBase64(context, "content://gallery/avatar.jpg"))
        val invalid = runCatching { ProfilePhotoStore.saveBase64(context, "not-an-image") }
        assertTrue(invalid.isFailure)
        assertNull(ProfilePhotoStore.loadBitmap(context, "content://gallery/avatar.jpg"))
    }
}
