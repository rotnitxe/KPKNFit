package com.example.kpkn.screens.sessioneditor.design

import com.example.kpkn.data.models.*
import org.junit.Assert.*
import org.junit.Test
import java.io.File

class CustomExerciseCoverPresentationTest {
    private val photo = WorkoutMedia("photo", WorkoutMediaKind.PHOTO, "/private/photo.jpg", thumbPath = "/private/thumb.jpg", createdAtMs = 1)
    private val own = ExerciseMuscleInfo("custom:x", "Propio", equipment = "Barra", isCustom = true)
    private fun present(info: ExerciseMuscleInfo, media: WorkoutMedia? = photo, original: Boolean = true,
        revision: String = "r1", material: String = "barbell", image: Int? = 42) =
        presentCustomExerciseCover(info, media, original, revision, { _, _ -> image }, { _, _ -> material })

    @Test fun fotoPrivadaExigeOriginalAunqueLaMiniaturaTodaviaExista() {
        val info = own.copy(cover = ExerciseCover.userPhoto(photo.id))
        assertEquals(File(photo.filePath), present(info).photoFile)
        val removed = present(info, original = false)
        assertNull(removed.photoFile)
        assertNotNull(removed.unavailableReason)
    }

    @Test fun noAceptaVideoNiIdDeOtroMedioComoFoto() {
        val info = own.copy(cover = ExerciseCover.userPhoto(photo.id))
        assertNull(present(info, media = photo.copy(kind = WorkoutMediaKind.VIDEO)).photoFile)
        assertNull(present(info, media = photo.copy(id = "other")).photoFile)
    }

    @Test fun referenciaSoloConMismaRevisionMaterialYConfiguracionConImagen() {
        val info = own.copy(cover = ExerciseCover.catalogReference("definition", "configuration", "r1"))
        assertEquals(42, present(info).imageResId)
        assertNull(present(info, revision = "r2").imageResId)
        assertNull(present(info, material = "cable").imageResId)
        assertNull(present(info, image = null).imageResId)
        assertNotNull(present(info, material = "cable").unavailableReason)
    }

    @Test fun sinPortadaLaIlustracionNoInventaUnaFoto() {
        val model = present(own.copy(cover = ExerciseCover(patternId = "horizontal_pull")))
        assertNull(model.photoFile)
        assertNull(model.imageResId)
        assertEquals("Ilustración del patrón", model.provenance)
    }
}
