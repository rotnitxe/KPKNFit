package com.example.kpkn.domain.body

import com.example.kpkn.data.models.BodyChartRange
import com.example.kpkn.data.models.BodyMetric
import com.example.kpkn.data.models.BodyObservation
import com.example.kpkn.data.models.GoalMetric
import java.time.LocalDate
import java.time.YearMonth
import java.time.ZoneId
import java.time.ZoneOffset
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class BodyWeightSummaryTest {

    private val zone = ZoneId.of("America/Santiago")
    private val today = LocalDate.of(2026, 10, 13)

    private fun observation(
        id: String,
        metric: BodyMetric,
        value: Double,
        date: LocalDate,
        sessionId: String? = null,
    ) = BodyObservation(
        id = id,
        metric = metric,
        valueSi = value,
        unitSi = if (metric == BodyMetric.WEIGHT) "kg" else "%",
        sessionId = sessionId,
        timestampEpochMs = date.atTime(12, 0).atZone(zone).toInstant().toEpochMilli(),
        zoneId = zone.id,
    )

    private fun weight(id: String, value: Double, date: LocalDate, sessionId: String? = null) =
        observation(id, BodyMetric.WEIGHT, value, date, sessionId)

    /** Un pesaje al día en los últimos 28 días, que acaban en [lastValue] y bajan [weeklyRate] por semana. */
    private fun trend(lastValue: Double, weeklyRate: Double): List<BodyObservation> = (0 until 28).map { index ->
        val daysBeforeLast = 27 - index
        weight(
            id = "t$index",
            value = lastValue - weeklyRate / 7.0 * daysBeforeLast,
            date = today.minusDays(daysBeforeLast.toLong()),
        )
    }

    @Test
    fun `el ultimo pesaje gana a una sesion de composicion mas antigua y a otra mas reciente sin peso`() {
        val observations = listOf(
            weight("w1", 80.0, LocalDate.of(2026, 9, 1), sessionId = "s1"),
            observation("f1", BodyMetric.BODY_FAT_PERCENT, 20.0, LocalDate.of(2026, 9, 1), sessionId = "s1"),
            weight("w2", 79.0, LocalDate.of(2026, 10, 3)),
            observation("f2", BodyMetric.BODY_FAT_PERCENT, 18.0, LocalDate.of(2026, 10, 10), sessionId = "s2"),
        )

        val summary = summarizeWeight(observations, BodyChartRange.ALL, goal = null, zone = zone, today = today)

        assertEquals(79.0, summary.current!!, 1e-9)
        assertEquals(LocalDate.of(2026, 10, 3), summary.currentDate)
    }

    @Test
    fun `el cambio usa el primer punto del rango y devuelve su fecha`() {
        val observations = listOf(
            weight("a", 85.0, LocalDate.of(2026, 8, 20)),
            weight("b", 82.0, LocalDate.of(2026, 10, 1)),
            weight("c", 80.5, LocalDate.of(2026, 10, 6)),
            weight("d", 79.0, LocalDate.of(2026, 10, 10)),
        )

        val oneMonth = summarizeWeight(observations, BodyChartRange.ONE_MONTH, goal = null, zone = zone, today = today)
        assertEquals(82.0, oneMonth.rangeStart!!, 1e-9)
        assertEquals(LocalDate.of(2026, 10, 1), oneMonth.rangeStartDate)
        assertEquals(-3.0, oneMonth.change!!, 1e-9)

        val threeMonths = summarizeWeight(observations, BodyChartRange.THREE_MONTHS, goal = null, zone = zone, today = today)
        assertEquals(LocalDate.of(2026, 8, 20), threeMonths.rangeStartDate)
        assertEquals(-6.0, threeMonths.change!!, 1e-9)
    }

    @Test
    fun `el ritmo es null con pocos datos`() {
        val observations = listOf(
            weight("a", 80.0, LocalDate.of(2026, 10, 1)),
            weight("b", 79.5, LocalDate.of(2026, 10, 4)),
            weight("c", 79.0, LocalDate.of(2026, 10, 8)),
        )

        assertNull(summarizeWeight(observations, BodyChartRange.ALL, goal = null, zone = zone, today = today).measuredWeeklyRate)
    }

    @Test
    fun `eta es null si te alejas de la meta o si el ritmo es menor de 0,05`() {
        // Bajando 0,5 kg por semana con la meta por encima: el peso se aleja de ella.
        val away = summarizeWeight(
            trend(lastValue = 78.0, weeklyRate = -0.5),
            BodyChartRange.ALL,
            ResolvedBodyGoal(GoalMetric.WEIGHT, 85.0),
            zone,
            today,
        )
        assertEquals(-0.5, away.measuredWeeklyRate!!, 1e-9)
        assertNull(away.eta)

        // Ritmo de 0,04 kg por semana hacia una meta más baja: hacia ella, pero sin ritmo suficiente.
        val slow = summarizeWeight(
            trend(lastValue = 78.0, weeklyRate = -0.04),
            BodyChartRange.ALL,
            ResolvedBodyGoal(GoalMetric.WEIGHT, 70.0),
            zone,
            today,
        )
        assertNull(slow.eta)
    }

    @Test
    fun `con ritmo hacia la meta, eta es el mes esperado`() {
        // De 78 kg a 70 kg bajando 0,5 kg por semana: 16 semanas, 112 días, a principios de febrero de 2027.
        val summary = summarizeWeight(
            trend(lastValue = 78.0, weeklyRate = -0.5),
            BodyChartRange.ALL,
            ResolvedBodyGoal(GoalMetric.WEIGHT, 70.0),
            zone,
            today,
        )

        assertEquals(-8.0, summary.remaining!!, 1e-9)
        assertEquals(YearMonth.of(2027, 2), summary.eta)
    }

    @Test
    fun `la linea de base es el primer pesaje desde createdAt`() {
        val observations = listOf(
            weight("old", 90.0, LocalDate.of(2026, 8, 1)),
            weight("first", 85.0, LocalDate.of(2026, 9, 10)),
            weight("now", 84.0, LocalDate.of(2026, 10, 10)),
        )
        val createdAt = LocalDate.of(2026, 9, 1).atStartOfDay(zone).toInstant().toEpochMilli()

        val summary = summarizeWeight(
            observations,
            BodyChartRange.ALL,
            ResolvedBodyGoal(GoalMetric.WEIGHT, 80.0, createdAt),
            zone,
            today,
        )

        // Base 85 → 84 hacia 80 es el 20 %; con la base de todos los tiempos (90) saldría el 60 %.
        assertEquals(20, summary.progressPercent)
        assertEquals(-4.0, summary.remaining!!, 1e-9)
    }

    @Test
    fun `una medida guardada a medianoche UTC cuenta en su dia al verla en Santiago`() {
        val legacy = BodyObservation(
            id = "legacy:x:WEIGHT",
            metric = BodyMetric.WEIGHT,
            valueSi = 81.0,
            unitSi = "kg",
            sessionId = "legacy:x",
            timestampEpochMs = LocalDate.of(2026, 10, 13).atStartOfDay(ZoneOffset.UTC).toInstant().toEpochMilli(),
            zoneId = "UTC",
        )

        val series = bodyDailySeries(listOf(legacy), zone)

        assertEquals(LocalDate.of(2026, 10, 13), series.single().date)
    }
}
