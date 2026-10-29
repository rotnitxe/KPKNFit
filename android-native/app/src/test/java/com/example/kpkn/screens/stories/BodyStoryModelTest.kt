package com.example.kpkn.screens.stories

import com.example.kpkn.domain.body.BodyHistoryRow
import com.example.kpkn.screens.onboarding.design.WizardMassUnit
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import java.time.LocalDate
import java.time.ZoneOffset

/** Pruebas puras del modelo de las historias de Cuerpo: semanas, cambios y privacidad (sin peso absoluto por defecto). */
class BodyStoryModelTest {

    private val inicio = LocalDate.of(2026, 8, 5)

    private fun medicion(semana: Int, peso: Double?, cintura: Double? = null, grasa: Double? = null): BodyHistoryRow {
        val fecha = inicio.plusWeeks(semana.toLong()).atTime(8, 0)
        return BodyHistoryRow(
            sessionId = "s$semana",
            timestampEpochMs = fecha.toInstant(ZoneOffset.UTC).toEpochMilli(),
            dateTime = fecha,
            weightKg = peso,
            bodyFatPercent = grasa,
            muscleMassPercent = null,
            waistCm = cintura,
            hipCm = null,
            chestCm = null,
            armCm = null,
            thighCm = null,
            neckCm = null,
            fromHealthConnect = false,
            observations = emptyList(),
        )
    }

    /** Doce semanas bajando 0,35 kg por semana, con cintura y grasa al principio y al final. */
    private val doceSemanas = (0..12).map { semana ->
        medicion(
            semana,
            peso = 84.0 - 0.35 * semana,
            cintura = if (semana == 0) 90.0 else if (semana == 12) 83.9 else null,
            grasa = if (semana == 0) 22.0 else if (semana == 12) 18.6 else null,
        )
    }

    @Test
    fun `Cambio con el lienzo y sin peso actual por defecto`() {
        val pagina = bodyStoryPage(BodyStoryTemplate.CAMBIO, doceSemanas, WizardMassUnit.KG)
        assertEquals("12 semanas", pagina.eyebrow)
        assertEquals("−4,2 kg", pagina.title)
        assertEquals("De forma constante: 0,35 kg por semana", pagina.sub)
        assertEquals(StoryPair("Cintura", "−6,1 cm"), pagina.rows.first())
        assertEquals(StoryPair("% grasa", "−3,4 puntos"), pagina.rows[1])
        assertFalse(pagina.rows.any { it.label == "Peso actual" })
        assertEquals("Mi progreso", BODY_FOOT)
    }

    @Test
    fun `Peso actual solo si se activa`() {
        val pagina = bodyStoryPage(BodyStoryTemplate.CAMBIO, doceSemanas, WizardMassUnit.KG, BodyStoryOptions(currentWeight = true))
        assertTrue(pagina.rows.any { it.label == "Peso actual" && it.value == "79,8 kg" })
    }

    @Test
    fun `sin cambio en kg el titulo va en porcentaje`() {
        val pagina = bodyStoryPage(BodyStoryTemplate.CAMBIO, doceSemanas, WizardMassUnit.KG, BodyStoryOptions(change = false))
        assertTrue(pagina.title.endsWith("%"))
        assertFalse(pagina.title.contains("kg"))
    }

    @Test
    fun `Medidas con la cintura y la cifra del fondo Eco`() {
        val pagina = bodyStoryPage(BodyStoryTemplate.MEDIDAS, doceSemanas, WizardMassUnit.KG)
        assertEquals("Medidas · 12 semanas", pagina.eyebrow)
        assertEquals("−6,1 cm", pagina.title)
        assertEquals("de cintura", pagina.sub)
        assertEquals(6, pagina.ecoNumber)
    }

    @Test
    fun `plantillas segun los datos`() {
        assertEquals(listOf(BodyStoryTemplate.CAMBIO, BodyStoryTemplate.MEDIDAS), bodyStoryTemplates(doceSemanas))
        assertTrue(bodyStoryTemplates(listOf(medicion(0, 80.0))).isEmpty())
    }
}
