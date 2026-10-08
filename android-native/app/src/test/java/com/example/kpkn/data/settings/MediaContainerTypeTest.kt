package com.example.kpkn.data.settings

import com.example.kpkn.data.sync.MediaFiles
import java.io.File
import java.nio.ByteBuffer
import org.junit.Assert.*
import org.junit.Test

class MediaContainerTypeTest {
    private fun classify(bytes: ByteArray): String? {
        val file = File.createTempFile("container-test-", ".bin")
        return try { file.writeBytes(bytes); MediaFiles.detectedType(file) } finally { file.delete() }
    }
    private fun box(major: String, compatible: String = major): ByteArray =
        ByteBuffer.allocate(24).putInt(24).put("ftyp".toByteArray()).put(major.toByteArray())
            .putInt(0).put(compatible.toByteArray()).put("mif1".toByteArray()).array()

    @Test fun heicStillImageIsNotVideo() { assertEquals("image/heic", classify(box("heic"))) }
    @Test fun genericHeifStillImageIsNotVideo() { assertEquals("image/heif", classify(box("mif1"))) }
    @Test fun avifStillImageIsNotVideo() { assertEquals("image/avif", classify(box("avif"))) }
    @Test fun compatibleImageBrandIsRead() { assertEquals("image/avif", classify(box("mif1", "avif"))) }
    @Test fun mp4ControlRetainsVideoType() {
        assertEquals("video/mp4", classify(ByteBuffer.allocate(20).putInt(20).put("ftypisom".toByteArray()).putInt(0).put("mp42".toByteArray()).array()))
    }
    @Test fun unknownContainerIsNotGuessedAsVideo() {
        assertNull(classify(ByteBuffer.allocate(20).putInt(20).put("ftypzzzz".toByteArray()).putInt(0).put("zzzz".toByteArray()).array()))
    }
    @Test fun dataOutsideFileTypeBoxCannotSmuggleAnImageBrand() {
        val bytes = ByteBuffer.allocate(24).putInt(16).put("ftypisom".toByteArray()).putInt(0).put("avifmif1".toByteArray()).array()
        assertEquals("video/mp4", classify(bytes))
    }
    @Test fun completeStillImageFixturesRetainTheirFormat() {
        for ((extension, mime) in mapOf("png" to "image/png", "jpg" to "image/jpeg", "webp" to "image/webp",
            "heic" to "image/heic", "heif" to "image/heif", "avif" to "image/avif")) {
            val bytes = requireNotNull(javaClass.getResourceAsStream("/media-formats/sample.$extension")).use { it.readBytes() }
            assertEquals(mime, classify(bytes))
        }
    }
}
