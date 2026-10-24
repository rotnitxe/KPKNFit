package com.example.kpkn.screens.home

import com.example.kpkn.data.models.BodyChartRange
import com.example.kpkn.data.models.BodyGoal
import com.example.kpkn.data.models.BodyMetric
import com.example.kpkn.data.models.BodyObservation
import com.example.kpkn.data.models.GoalMetric
import com.example.kpkn.domain.body.ResolvedBodyGoal
import com.example.kpkn.domain.body.bodyWeightTrendSnapshot
import com.example.kpkn.domain.body.etaMonthText
import com.example.kpkn.domain.body.etaRangeText
import com.example.kpkn.domain.body.summarizeWeight
import java.time.LocalDate
import java.time.ZoneId
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

/** La tarjeta de Cuerpo de Inicio dice lo mismo que la página de Cuerpo: misma tendencia, misma fecha y mismo margen. */
class HomeBodyTrendTest {

    private val zone = ZoneId.of("UTC")
    private val today = LocalDate.of(2026, 10, 13)

    private fun weight(daysAgo: Long, kg: Double): BodyObservation = BodyObservation(
        id = "w-$daysAgo",
        metric = BodyMetric.WEIGHT,
        valueSi = kg,
        unitSi = "kg",
        timestampEpochMs = today.minusDays(daysAgo).atTime(12, 0).atZone(zone).toInstant().toEpochMilli(),
        zoneId = "UTC",
    )

    private fun weightGoal(kg: Double): BodyGoal = BodyGoal(
        id = "goal-$kg",
        metric = BodyMetric.WEIGHT,
        targetValueSi = kg,
        unitSi = "kg",
        createdAtEpochMs = 0L,
        updatedAtEpochMs = 0L,
    )

    /** 29 pesajes diarios de 80,0 kg a 77,2 kg: −0,7 kg por semana. */
    private val bajada = (0..28).map { k -> weight(28L - k, 80.0 - 0.1 * k) }

    @Test
    fun `la cifra de Inicio es la tendencia de Cuerpo`() {
        val state = homeBodyState(bajada, emptyList(), null, today, zone)
        val snapshot = bodyWeightTrendSnapshot(bajada, zone, today)!!

        assertEquals(snapshot.trendKg, state.trendKg!!, 1e-12)
        // El último peso sigue ahí como dato: 77,2 kg.
        assertEquals(77.2, state.weightKg!!, 1e-9)
    }

    @Test
    fun `la fecha y el margen de Inicio coinciden con los de Cuerpo`() {
        val cuerpo = summarizeWeight(bajada, BodyChartRange.ALL, ResolvedBodyGoal(GoalMetric.WEIGHT, 72.0), zone, today)
            .etaRange!!

        val inicio = homeBodyState(bajada, listOf(weightGoal(72.0)), null, today, zone)

        assertEquals(etaMonthText(cuerpo.central, today), inicio.goalMonth)
        assertEquals(etaRangeText(cuerpo, today, compact = true), inicio.goalMonthRange)
        // 5,3 kg a unos 0,64 kg por semana: a principios de diciembre (la regla heredada daba el mismo mes).
        assertEquals("diciembre", inicio.goalMonth)
    }

    @Test
    fun `un pesaje atipico de dos kilos el ultimo dia no cambia el mes de Inicio`() {
        val conPico = bajada.dropLast(1) + weight(0, 77.2 + 2.0)

        val limpio = homeBodyState(bajada, listOf(weightGoal(72.0)), null, today, zone)
        val pico = homeBodyState(conPico, listOf(weightGoal(72.0)), null, today, zone)

        assertEquals("diciembre", limpio.goalMonth)
        assertEquals(limpio.goalMonth, pico.goalMonth)
    }

    @Test
    fun `con un solo pesaje no hay tendencia ni fecha y la cifra sigue siendo el pesaje`() {
        val state = homeBodyState(listOf(weight(0, 78.4)), listOf(weightGoal(72.0)), null, today, zone)

        assertNull(state.trendKg)
        assertNull(state.goalMonth)
        assertNull(state.goalMonthRange)
        assertEquals(78.4, state.weightKg!!, 1e-9)
    }

    @Test
    fun `sin objetivo no hay fecha aunque haya tendencia`() {
        val state = homeBodyState(bajada, emptyList(), null, today, zone)

        assertNotNull(state.trendKg)
        assertNull(state.goalMonth)
        assertNull(state.goalMonthRange)
    }

    @Test
    fun `si te alejas de la meta no hay fecha`() {
        val state = homeBodyState(bajada, listOf(weightGoal(85.0)), null, today, zone)

        assertNull(state.goalMonth)
    }

    @Test
    fun `el ritmo del plan solo entra en los primeros dias`() {
        // Cinco días a 80,0 kg y una meta de 74 kg: sin plan no hay ritmo; con un plan de −0,6 kg/semana la fecha es enero de 2027.
        val planos = (0..4).map { k -> weight(4L - k, 80.0) }

        val sinPlan = homeBodyState(planos, listOf(weightGoal(74.0)), null, today, zone)
        val conPlan = homeBodyState(planos, listOf(weightGoal(74.0)), null, today, zone, planWeeklyRateKg = -0.6)

        assertNull(sinPlan.goalMonth)
        assertEquals("enero de 2027", conPlan.goalMonth)
        assertEquals(sinPlan.trendKg!!, conPlan.trendKg!!, 1e-12)
    }

    @Test
    fun `el cambio de 30 dias por tendencia de dos pesajes es la mitad del heredado`() {
        val state = homeBodyState(listOf(weight(1, 80.0), weight(0, 79.0)), emptyList(), null, today, zone)

        assertEquals(-0.5, state.trendChange30d!!, 1e-9)
        assertEquals(-1.0, state.weightChange30d!!, 1e-9)
    }

    @Test
    fun `el cambio de 30 dias de Inicio es el mismo que el de Cuerpo`() {
        val cuerpo = summarizeWeight(bajada, BodyChartRange.ONE_MONTH, null, zone, today)

        val inicio = homeBodyState(bajada, emptyList(), null, today, zone)

        assertEquals(cuerpo.trendChange!!, inicio.trendChange30d!!, 1e-12)
    }

    @Test
    fun `un pico de dos kilos el ultimo dia no invierte el cambio ni su sentido hacia la meta`() {
        val conPico = bajada.dropLast(1) + weight(0, 77.2 + 2.0)

        val state = homeBodyState(conPico, listOf(weightGoal(72.0)), null, today, zone)

        assertTrue(state.trendChange30d!! < -2.0)
        assertEquals(true, state.trendChangeTowardGoal)
    }

    @Test
    fun `sin tendencia no hay cambio por tendencia`() {
        val state = homeBodyState(listOf(weight(0, 78.4)), listOf(weightGoal(72.0)), null, today, zone)

        assertNull(state.trendChange30d)
        assertNull(state.trendChangeTowardGoal)
    }
}
