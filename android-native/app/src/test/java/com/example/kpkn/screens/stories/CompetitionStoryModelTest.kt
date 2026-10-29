package com.example.kpkn.screens.stories

import com.example.kpkn.data.models.CompetitionAttempt
import com.example.kpkn.data.models.CompetitionAttemptResult
import com.example.kpkn.data.models.CompetitionMediaKind
import com.example.kpkn.data.models.CompetitionMovementType
import com.example.kpkn.data.models.CompetitionPhoto
import com.example.kpkn.data.models.CompetitionRecord
import com.example.kpkn.data.models.CompetitionRecordStatus
import com.example.kpkn.data.models.CompetitionTechnicalBlock
import com.example.kpkn.data.models.CompetitionTemplateType
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import java.time.LocalDate

/** Pruebas puras del modelo de las historias de competencia (textos del lienzo Registros v4 y decisiones H-D4/H-D5). */
class CompetitionStoryModelTest {

    private val hoy = LocalDate.of(2026, 10, 28)

    /** Un bloque con un solo intento válido de [kg]. */
    private fun bloque(id: String, tipo: CompetitionMovementType, kg: Double): CompetitionTechnicalBlock = CompetitionTechnicalBlock(
        id = id,
        title = id,
        movementType = tipo,
        attempts = listOf(CompetitionAttempt(id = "$id-1", attemptNumber = 1, weightKg = kg, resultType = CompetitionAttemptResult.GOOD_LIFT)),
    )

    /** Una competencia de powerlifting terminada con su sentadilla, banca y peso muerto. */
    private fun meet(id: String, fecha: String, s: Double, b: Double, d: Double): CompetitionRecord = CompetitionRecord(
        id = id,
        title = "Copa $id",
        eventDate = fecha,
        sportType = CompetitionTemplateType.POWERLIFTING,
        status = CompetitionRecordStatus.COMPLETED,
        technicalBlocks = listOf(
            bloque("s", CompetitionMovementType.SQUAT, s),
            bloque("b", CompetitionMovementType.BENCH, b),
            bloque("d", CompetitionMovementType.DEADLIFT, d),
        ),
    )

    @Test
    fun `cifras en kg con coma y puntos de miles`() {
        assertEquals("552,5", storyKg(552.5))
        assertEquals("200", storyKg(200.0))
        assertEquals("1.204", storyKg(1204.0))
    }

    @Test
    fun `cuenta atras de la proxima competencia`() {
        assertEquals("Faltan 18 días", storyCountdown(18))
        assertEquals("Falta 1 día", storyCountdown(1))
        assertEquals("Es hoy", storyCountdown(0))
    }

    @Test
    fun `clase de peso y reloj`() {
        assertEquals("-83 kg", storyWeightClass("83"))
        assertEquals("120+ kg", storyWeightClass("120+"))
        assertNull(storyWeightClass(" "))
        assertEquals("1:38:12", storyClock(5892.0))
        assertEquals("4:21", storyClock(261.0))
    }

    @Test
    fun `sin competencia anterior no sale Mejor total`() {
        val actual = meet("otono", "2026-09-21", 200.0, 135.0, 217.5)
        assertNull(bestTotalBadge(actual, competitionTotalKg(actual), listOf(actual)))
    }

    @Test
    fun `Mejor total frente a la mejor competencia anterior`() {
        val antes = meet("primavera", "2026-04-12", 200.0, 130.0, 210.0)
        val actual = meet("otono", "2026-09-21", 200.0, 135.0, 217.5)
        assertEquals("Mejor total: +12,5 kg", bestTotalBadge(actual, competitionTotalKg(actual), listOf(antes, actual)))
    }

    @Test
    fun `fuerza terminada ofrece Total e Intentos y la pagina usa el total`() {
        val actual = meet("otono", "2026-09-21", 200.0, 135.0, 217.5)
        val disponibles = competitionStoryAvailability(actual, listOf(actual), hoy).templates
        assertTrue(CompetitionStoryTemplate.TOTAL in disponibles)
        assertTrue(CompetitionStoryTemplate.INTENTOS in disponibles)
        assertFalse(CompetitionStoryTemplate.FOTO in disponibles)
        val pagina = competitionStoryPage(CompetitionStoryTemplate.TOTAL, actual, listOf(actual), hoy)
        assertEquals("552,5 kg", pagina.title)
        assertEquals("Mi competencia", pagina.foot)
    }

    @Test
    fun `solo videos no ofrece la variante con foto`() {
        val conVideo = meet("otono", "2026-09-21", 200.0, 135.0, 217.5).copy(
            photos = listOf(CompetitionPhoto(id = "v", uri = "/x/v.mp4", kind = CompetitionMediaKind.VIDEO)),
        )
        val disponibilidad = competitionStoryAvailability(conVideo, listOf(conVideo), hoy)
        assertFalse(CompetitionStoryTemplate.FOTO in disponibilidad.templates)
        assertTrue(disponibilidad.photos.isEmpty())
    }

    @Test
    fun `culturismo sin foto no tiene historia`() {
        val tarima = CompetitionRecord(
            id = "c",
            title = "Clásico",
            eventDate = "2026-09-21",
            sportType = CompetitionTemplateType.BODYBUILDING,
            status = CompetitionRecordStatus.COMPLETED,
            placement = "2",
        )
        assertTrue(competitionStoryAvailability(tarima, listOf(tarima), hoy).templates.isEmpty())
    }
}
