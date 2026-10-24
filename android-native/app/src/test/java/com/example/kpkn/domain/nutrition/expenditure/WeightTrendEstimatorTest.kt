package com.example.kpkn.domain.nutrition.expenditure

import java.time.LocalDate
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class WeightTrendEstimatorTest {
    private val start = LocalDate.of(2026, 1, 1)
    private val estimator = WeightTrendEstimator()

    private fun weights(count: Int = 84, slope: Double = -0.04) = (0 until count).map {
        WeightObservation(start.plusDays(it.toLong()), 80.0 + slope * it)
    }

    @Test
    fun `la mediana inicial usa solo los pesajes disponibles y declara provisionalidad`() {
        val input = listOf(81.0, 79.0, 80.1).mapIndexed { index, kg -> WeightObservation(start.plusDays(index.toLong()), kg) }
        val full = estimator.estimate(input, start, start.plusDays(2))
        assertEquals(81.0, full[0].trendKg, 0.0)
        assertEquals(80.0, full[1].trendKg, 0.0)
        assertEquals(80.1, full[2].trendKg, 0.0)
        assertTrue(full[0].isProvisional)
        assertTrue(full[1].isProvisional)
        assertFalse(full[2].isProvisional)
        assertEquals(full.take(1), estimator.estimate(input.take(1), start, start))
        assertEquals(full.take(2), estimator.estimate(input.take(2), start, start.plusDays(1)))
    }

    @Test
    fun `dos picos de agua de kilo y medio no invierten una pendiente descendente`() {
        val input = weights().mapIndexed { index, point -> if (index in 60..61) point.copy(kg = point.kg + 1.5) else point }
        val points = estimator.estimate(input, start, start.plusDays(83))
        assertTrue(points.slice(60..61).all { it.wasOutlier && it.slopeKgPerDay < 0.0 })
        assertTrue(points.last().slopeKgPerDay < 0.0)
    }

    @Test
    fun `dos picos negativos tampoco invierten una pendiente ascendente`() {
        val input = weights(slope = 0.04).mapIndexed { index, point -> if (index in 60..61) point.copy(kg = point.kg - 1.5) else point }
        val points = estimator.estimate(input, start, start.plusDays(83))
        assertTrue(points.slice(60..61).all { it.wasOutlier && it.slopeKgPerDay > 0.0 })
    }

    @Test
    fun `los huecos predicen cada dia y aumentan el error de la pendiente`() {
        val points = estimator.estimate(weights(56), start, start.plusDays(69))
        assertEquals(70, points.size)
        assertNull(points.last().observedKg)
        assertFalse(points.last().wasOutlier)
        assertTrue(points.last().slopeStdErrKgPerDay > points[55].slopeStdErrKgPerDay)
        assertTrue(points.last().trendKg < points[55].trendKg)
    }

    @Test
    fun `sin pesajes o con intervalo invertido no inventa una tendencia`() {
        assertTrue(estimator.estimate(emptyList(), start, start.plusDays(20)).isEmpty())
        assertTrue(estimator.estimate(weights(), start.plusDays(1), start).isEmpty())
    }

    @Test
    fun `no emite puntos anteriores al primer pesaje`() {
        val points = estimator.estimate(weights(2).map { it.copy(date = it.date.plusDays(5)) }, start, start.plusDays(9))
        assertEquals(start.plusDays(5), points.first().date)
        assertTrue(points.all { it.isProvisional })
    }

    @Test
    fun `recortar la salida conserva el estado causal acumulado antes de from`() {
        val input = weights()
        val full = estimator.estimate(input, start, start.plusDays(83))
        val cropped = estimator.estimate(input, start.plusDays(60), start.plusDays(83))
        assertEquals(full.drop(60), cropped)
    }

    @Test
    fun `ignora pesajes invalidos y futuros y usa mediana si se repite una fecha`() {
        val invalid = listOf(Double.NaN, Double.POSITIVE_INFINITY, 0.0, -1.0).map { WeightObservation(start, it) }
        val input = invalid + listOf(WeightObservation(start, 79.0), WeightObservation(start, 81.0), WeightObservation(start.plusDays(1), 200.0))
        assertEquals(80.0, estimator.estimate(input, start, start).single().trendKg, 0.0)
    }

    @Test
    fun `el ruido proporcional al peso produce incertidumbre mayor en cuerpos mas pesados`() {
        fun uncertainty(kg: Double) = estimator.estimate(
            weights(56, 0.0).map { it.copy(kg = kg) }, start, start.plusDays(55),
        ).last().slopeStdErrKgPerDay
        assertTrue(uncertainty(110.0) > uncertainty(50.0))
    }

    @Test
    fun `es determinista aunque los pesajes lleguen desordenados`() {
        val input = weights()
        val full = estimator.estimate(input, start, start.plusDays(83))
        assertEquals(full, estimator.estimate(input.reversed(), start, start.plusDays(83)))
        assertTrue(full.all { it.trendKg.isFinite() && it.slopeStdErrKgPerDay > 0.0 })
    }
}
