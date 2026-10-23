package com.example.kpkn.domain.exercises

import com.example.kpkn.data.db.dbJson
import com.example.kpkn.data.models.CustomExerciseReference
import com.example.kpkn.data.models.ExerciseCover
import com.example.kpkn.data.models.ExerciseCoverKind
import com.example.kpkn.data.models.ExerciseMuscleInfo
import kotlinx.serialization.encodeToString
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * MOT · E4/E5 (AUD1 §3.5): la portada de un ejercicio propio es una elección (foto, imagen de referencia o ilustración) que sobrevive
 * al reinicio, no cambia nada más del ejercicio y nunca muestra una imagen que no corresponde.
 */
class ExerciseCoverResolverTest {
    private val rev = "v2-approved-2026-09-29-a"

    private fun own(
        name: String = "Sentadilla barra baja",
        equipment: String? = "Barra",
        cover: ExerciseCover? = null,
        reference: CustomExerciseReference? = CustomExerciseReference("low_bar_back_squat", "low_bar_back_squat__barbell", rev, confirmed = true),
    ) = ExerciseMuscleInfo(id = "custom:00000000-0000-0000-0000-000000000001", name = name, equipment = equipment, isCustom = true, cover = cover, creationReference = reference)

    @Test
    fun noChoice_showsThePatternIllustration_readFromTheName() {
        val presentation = ExerciseCoverResolver.resolve(own())
        assertTrue(presentation is ExerciseCoverPresentation.PatternIllustration)
        assertEquals("knee_dominant", (presentation as ExerciseCoverPresentation.PatternIllustration).patternId)
    }

    @Test
    fun userPhoto_isShownWhileItsMediaExists_andFallsBackWhenItIsGone() {
        val info = own(cover = ExerciseCover.userPhoto("media-1"))
        assertEquals(ExerciseCoverPresentation.UserPhoto("media-1"), ExerciseCoverResolver.resolve(info, mediaExists = { it == "media-1" }))
        // Medio borrado: el ejercicio y su descripción se conservan y la fila vuelve a la ilustración.
        assertTrue(ExerciseCoverResolver.resolve(info, mediaExists = { false }) is ExerciseCoverPresentation.PatternIllustration)
    }

    @Test
    fun catalogReference_needsTheSameRevisionAndAnAvailableImage() {
        val info = own(cover = ExerciseCover.catalogReference("low_bar_back_squat", "low_bar_back_squat__barbell", rev))
        val shown = ExerciseCoverResolver.resolve(info, catalogImageAvailable = { d, c -> d == "low_bar_back_squat" && c.endsWith("barbell") }, currentCatalogRevision = rev)
        assertEquals(ExerciseCoverPresentation.CatalogImage("low_bar_back_squat", "low_bar_back_squat__barbell"), shown)
        assertTrue(ExerciseCoverResolver.resolve(info, catalogImageAvailable = { _, _ -> false }, currentCatalogRevision = rev) is ExerciseCoverPresentation.PatternIllustration)
        assertTrue(
            "otra revisión del catálogo: no se muestra una imagen que puede ya no corresponder",
            ExerciseCoverResolver.resolve(info, catalogImageAvailable = { _, _ -> true }, currentCatalogRevision = "v2-otra") is ExerciseCoverPresentation.PatternIllustration,
        )
    }

    @Test
    fun suggestReferenceCover_onlyWhenTheMaterialMatchesAndAnImageExists() {
        val available = { d: String, c: String -> d == "low_bar_back_squat" && c == "low_bar_back_squat__barbell" }
        assertEquals(
            ExerciseCover.catalogReference("low_bar_back_squat", "low_bar_back_squat__barbell", rev),
            ExerciseCoverResolver.suggestReferenceCover(own(), referenceImplementId = "barbell", catalogImageAvailable = available),
        )
        // Cambiar polea por mancuernas no conserva una imagen técnica incompatible en silencio.
        assertNull(ExerciseCoverResolver.suggestReferenceCover(own(equipment = "Mancuerna"), "barbell", available))
        assertNull(ExerciseCoverResolver.suggestReferenceCover(own(), "cable", available))
        // Sin referencia confirmada, o sin imagen revisada para esa configuración, no hay nada que reutilizar.
        assertNull(ExerciseCoverResolver.suggestReferenceCover(own(reference = null), "barbell", available))
        assertNull(ExerciseCoverResolver.suggestReferenceCover(own(reference = CustomExerciseReference("low_bar_back_squat", "low_bar_back_squat__barbell", rev, confirmed = false)), "barbell", available))
        assertNull(ExerciseCoverResolver.suggestReferenceCover(own(), "barbell") { _, _ -> false })
    }

    @Test
    fun choosingACover_changesNothingElse_andSurvivesTheDatabaseJson() {
        val base = own().copy(description = "Mi texto.", descriptionOrigin = "USER", images = listOf("a"))
        val withCover = ExerciseCoverResolver.withCover(base, ExerciseCover.userPhoto("media-9"))
        assertEquals(base.copy(cover = ExerciseCover.userPhoto("media-9")), withCover)
        assertEquals(base.id, withCover.id)
        assertEquals(withCover, dbJson.decodeFromString<ExerciseMuscleInfo>(dbJson.encodeToString(withCover)))
        // Quitar la portada devuelve la ilustración del patrón.
        assertNull(ExerciseCoverResolver.withCover(withCover, null).cover)
    }

    @Test
    fun anUnknownCoverKindFromAnotherVersion_neverBreaksReadingTheExercise() {
        val json = """{"id":"custom:x","name":"X","isCustom":true,"cover":{"kind":"HOLOGRAM","patternId":"hip_hinge"}}"""
        val decoded = dbJson.decodeFromString<ExerciseMuscleInfo>(json)
        assertEquals(ExerciseCoverKind.PATTERN, decoded.cover!!.kind)
        assertEquals("hip_hinge", ExerciseCoverResolver.patternIdFor(decoded))
    }
}
