package com.example.kpkn.screens.workout.components

import com.example.kpkn.data.models.WorkoutMedia
import com.example.kpkn.data.models.WorkoutMediaKind
import com.example.kpkn.ui.components.localMediaImageSource
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Rule
import org.junit.Test
import org.junit.rules.TemporaryFolder
import java.io.File

/**
 * `mediaPreviewSource` es lo que `WorkoutMediaThumb` resuelve en Dispatchers.IO: elige el
 * archivo a pintar (miniatura o foto original) y calcula la clave de caché de Coil.
 */
class WorkoutMediaPreviewSourceTest {

    @get:Rule
    val tmp = TemporaryFolder()

    private fun media(kind: WorkoutMediaKind, file: File, thumb: File?) = WorkoutMedia(
        id = "media-1",
        kind = kind,
        filePath = file.absolutePath,
        thumbPath = thumb?.absolutePath,
        createdAtMs = 1L,
    )

    @Test
    fun uses_the_thumbnail_when_it_exists_and_is_not_empty() {
        val original = tmp.newFile("photo.jpg").also { it.writeText("original") }
        val thumb = tmp.newFile("thumb.jpg").also { it.writeText("thumb") }

        val source = mediaPreviewSource(media(WorkoutMediaKind.PHOTO, original, thumb))

        assertNotNull(source)
        assertEquals(thumb, source!!.file)
        assertEquals(localMediaImageSource(thumb), source)
    }

    @Test
    fun photo_without_a_usable_thumbnail_falls_back_to_the_original() {
        val original = tmp.newFile("photo.jpg").also { it.writeText("original") }
        val emptyThumb = tmp.newFile("thumb.jpg")

        val source = mediaPreviewSource(media(WorkoutMediaKind.PHOTO, original, emptyThumb))

        assertEquals(original, source?.file)
    }

    @Test
    fun video_without_a_thumbnail_has_no_preview() {
        val video = tmp.newFile("clip.mp4").also { it.writeText("video") }

        assertNull(mediaPreviewSource(media(WorkoutMediaKind.VIDEO, video, thumb = null)))
    }

    @Test
    fun missing_files_have_no_preview() {
        val missing = File(tmp.root, "gone.jpg")

        assertNull(mediaPreviewSource(media(WorkoutMediaKind.PHOTO, missing, thumb = null)))
    }
}
