package com.example.kpkn.ui.components

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotEquals
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.rules.TemporaryFolder
import java.io.File

/**
 * La clave de caché de memoria de Coil se calcula fuera de Main (ver LocalMediaImage.kt).
 * Debe seguir invalidándose cuando el archivo se reemplaza en la misma ruta, que es lo que
 * hacía el `FileKeyer` por defecto con `lastModified()`.
 */
class LocalMediaImageSourceTest {

    @get:Rule
    val tmp = TemporaryFolder()

    private fun fileWith(name: String, content: String, lastModified: Long): File =
        tmp.newFile(name).also {
            it.writeText(content)
            assertTrue(it.setLastModified(lastModified))
        }

    @Test
    fun key_is_stable_while_the_file_does_not_change() {
        val file = fileWith("thumb.jpg", "abc", lastModified = 1_700_000_000_000L)

        assertEquals(localMediaImageSource(file), localMediaImageSource(file))
        assertEquals(file, localMediaImageSource(file).file)
    }

    @Test
    fun key_changes_when_the_file_is_replaced_in_place() {
        val file = fileWith("thumb.jpg", "abc", lastModified = 1_700_000_000_000L)
        val before = localMediaImageSource(file).memoryCacheKey

        file.writeText("abcdef")
        assertTrue(file.setLastModified(1_700_000_050_000L))

        assertNotEquals(before, localMediaImageSource(file).memoryCacheKey)
    }

    @Test
    fun key_changes_when_only_the_modification_time_changes() {
        val file = fileWith("thumb.jpg", "abc", lastModified = 1_700_000_000_000L)
        val before = localMediaImageSource(file).memoryCacheKey

        assertTrue(file.setLastModified(1_700_000_090_000L))

        assertNotEquals(before, localMediaImageSource(file).memoryCacheKey)
    }

    @Test
    fun key_changes_when_only_the_length_changes() {
        val file = fileWith("thumb.jpg", "abc", lastModified = 1_700_000_000_000L)
        val before = localMediaImageSource(file).memoryCacheKey

        file.writeText("abcd")
        assertTrue(file.setLastModified(1_700_000_000_000L))

        assertNotEquals(before, localMediaImageSource(file).memoryCacheKey)
    }

    @Test
    fun key_differs_between_files_with_identical_content() {
        val a = fileWith("a.jpg", "same", lastModified = 1_700_000_000_000L)
        val b = fileWith("b.jpg", "same", lastModified = 1_700_000_000_000L)

        assertNotEquals(localMediaImageSource(a).memoryCacheKey, localMediaImageSource(b).memoryCacheKey)
    }
}
