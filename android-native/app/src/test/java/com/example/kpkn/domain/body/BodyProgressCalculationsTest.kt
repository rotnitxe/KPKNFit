package com.example.kpkn.domain.body

import com.example.kpkn.data.models.BodyMetric
import com.example.kpkn.data.models.BodyMetricSource
import com.example.kpkn.data.models.BodyObservation
import java.time.LocalDate
import java.time.ZoneOffset
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class BodyProgressCalculationsTest {

    private fun observation(
        id: String,
        metric: BodyMetric,
        value: Double,
        date: LocalDate,
        sessionId: String? = null,
        source: BodyMetricSource = BodyMetricSource.MANUAL,
    ) = BodyObservation(
        id = id,
        metric = metric,
        valueSi = value,
        unitSi = if (metric == BodyMetric.WEIGHT) "kg" else "%",
        sessionId = sessionId,
        timestampEpochMs = date.atStartOfDay(ZoneOffset.UTC).toInstant().toEpochMilli(),
        source = source,
    )

    @Test
    fun `validation rejects non finite values and keeps continuous bmi boundary`() {
        assertFalse(validateBodyValue(BodyMetric.WEIGHT, Double.NaN).valid)
        assertFalse(validateBodyValue(BodyMetric.BODY_FAT_PERCENT, Double.POSITIVE_INFINITY).valid)
        assertFalse(validateBodyValue(BodyMetric.WEIGHT, -1.0).valid)
        assertTrue(validateBodyValue(BodyMetric.BODY_FAT_PERCENT, 100.0).valid)
        assertEquals(BmiCategory.HEALTHY, bmiCategory(24.995))
        assertEquals(BmiCategory.OVERWEIGHT, bmiCategory(25.0))
    }

    @Test
    fun `daily median collapses duplicate readings instead of overweighting a day`() {
        val points = dailyMedianSeries(
            listOf(
                observation("a", BodyMetric.WEIGHT, 70.0, LocalDate.of(2026, 1, 1)),
                observation("b", BodyMetric.WEIGHT, 71.0, LocalDate.of(2026, 1, 1)),
                observation("c", BodyMetric.WEIGHT, 69.0, LocalDate.of(2026, 1, 1)),
                observation("d", BodyMetric.WEIGHT, 68.0, LocalDate.of(2026, 1, 2)),
            ),
        )

        assertEquals(2, points.size)
        assertEquals(70.0, points.first().value, 0.001)
        assertEquals(listOf("a", "b", "c"), points.first().sourceObservationIds)
    }

    @Test
    fun `ewma uses calendar gaps and weekly rate needs coverage`() {
        val points = (0 until 7).map { index ->
            BodyMetricPoint(LocalDate.of(2026, 1, 1).plusDays(index.toLong() * 3), 70.0 - index, listOf(index.toString()))
        }
        val trend = ewmaTrend(points)
        assertEquals(points.size, trend.size)
        assertTrue(trend[1].value < 70.0)
        assertTrue(trend[1].value > points[1].value)
        assertEquals(-7.0 / 3.0, weeklyRate(points)!!, 0.0001)
        assertNull(weeklyRate(points.take(6)))
    }

    @Test
    fun `composition only combines compatible sessions`() {
        val date = LocalDate.of(2026, 1, 1)
        assertNull(
            latestCompatibleComposition(
                listOf(
                    observation("w", BodyMetric.WEIGHT, 70.0, date, sessionId = "s1"),
                    observation("f", BodyMetric.BODY_FAT_PERCENT, 20.0, date, sessionId = "s2"),
                ),
            ),
        )

        val result = latestCompatibleComposition(
            listOf(
                observation("w", BodyMetric.WEIGHT, 70.0, date, sessionId = "s1"),
                observation("f", BodyMetric.BODY_FAT_PERCENT, 20.0, date, sessionId = "s1"),
                observation("m", BodyMetric.MUSCLE_MASS_PERCENT, 38.0, date, sessionId = "s1"),
            ),
        )!!
        assertEquals(70.0, result.weightKg, 0.001)
        assertEquals(20.0, result.bodyFatPercent!!, 0.001)
        assertEquals(38.0, result.muscleMassPercent!!, 0.001)
    }

    @Test
    fun `goal progress is direction aware and safe at equal baseline`() {
        assertEquals(50, goalProgressPercent(80.0, 75.0, 70.0))
        assertEquals(50, goalProgressPercent(70.0, 75.0, 80.0))
        assertEquals(100, goalProgressPercent(70.0, 70.0, 70.0))
        assertEquals(0, goalProgressPercent(70.0, 75.0, 70.0))
    }
}
