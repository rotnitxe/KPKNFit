package com.example.kpkn.screens.home

import com.example.kpkn.data.models.BodyGoal
import com.example.kpkn.data.models.BodyMetric
import com.example.kpkn.data.models.BodyObservation
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import java.time.LocalDate
import java.time.ZoneId

/** Cuerpo de hoy: cambio de 30 días, mes estimado, IMC y FFMI solo con altura real, y el estado vacío sin pesos. */
class HomeBodyStateTest {

    private val zone = ZoneId.of("UTC")
    private val today = LocalDate.of(2026, 10, 13)

    private fun observation(id: String, metric: BodyMetric, value: Double, day: LocalDate): BodyObservation = BodyObservation(
        id = id,
        metric = metric,
        valueSi = value,
        unitSi = if (metric == BodyMetric.WEIGHT) "kg" else "%",
        timestampEpochMs = day.atTime(12, 0).atZone(zone).toInstant().toEpochMilli(),
        zoneId = "UTC",
    )

    private fun weight(daysAgo: Long, kg: Double): BodyObservation =
        observation("w-$daysAgo", BodyMetric.WEIGHT, kg, today.minusDays(daysAgo))

    private fun weightGoal(kg: Double): BodyGoal = BodyGoal(
        id = "goal-$kg",
        metric = BodyMetric.WEIGHT,
        targetValueSi = kg,
        unitSi = "kg",
        createdAtEpochMs = 0L,
        updatedAtEpochMs = 0L,
    )

    @Test
    fun `el cambio de 30 dias va del primer al ultimo punto de la ventana`() {
        val state = homeBodyState(
            observations = listOf(weight(30, 80.0), weight(10, 79.0), weight(0, 78.8)),
            goals = emptyList(),
            heightCm = null,
            today = today,
            zone = zone,
        )

        assertEquals(78.8, state.weightKg!!, 1e-9)
        assertEquals(-1.2, state.weightChange30d!!, 1e-6)
        assertEquals(3, state.trend.size)
        assertEquals(today, state.windowEnd)
    }

    @Test
    fun `el mes estimado aparece solo si el ritmo va hacia la meta`() {
        // 80,0 kg hace 28 días y 77,2 kg hoy: −0,7 kg por semana. Faltan 5,2 kg, unos 52 días: 4 de diciembre.
        val observations = (0..28).map { k -> weight(28L - k, 80.0 - 0.1 * k) }

        val toward = homeBodyState(observations, listOf(weightGoal(72.0)), null, today, zone)
        assertEquals("diciembre", toward.goalMonth)
        assertTrue(toward.changeTowardGoal)

        val away = homeBodyState(observations, listOf(weightGoal(85.0)), null, today, zone)
        assertNull(away.goalMonth)
        assertFalse(away.changeTowardGoal)
    }

    @Test
    fun `sin altura no hay IMC ni FFMI`() {
        val observations = listOf(weight(0, 80.0), observation("f", BodyMetric.BODY_FAT_PERCENT, 20.0, today))

        val sinAltura = homeBodyState(observations, emptyList(), null, today, zone)
        assertNull(sinAltura.bmi)
        assertNull(sinAltura.ffmi)

        val conAltura = homeBodyState(observations, emptyList(), 180.0, today, zone)
        assertEquals(24.69, conAltura.bmi!!, 0.01)
        assertNotNull(conAltura.ffmi)
    }

    @Test
    fun `sin pesos el estado queda vacio`() {
        assertEquals(HomeBodyState(), homeBodyState(emptyList(), emptyList(), 180.0, today, zone))

        val soloGrasa = homeBodyState(
            listOf(observation("f", BodyMetric.BODY_FAT_PERCENT, 20.0, today)),
            emptyList(),
            180.0,
            today,
            zone,
        )
        assertNull(soloGrasa.weightKg)
        assertTrue(soloGrasa.trend.isEmpty())
        assertNull(soloGrasa.bmi)
        assertNull(soloGrasa.ffmi)
    }
}
