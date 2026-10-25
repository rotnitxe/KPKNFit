package com.example.kpkn.domain.body

import com.example.kpkn.data.models.BodyChartRange
import com.example.kpkn.data.models.BodyMetric
import com.example.kpkn.data.models.BodyObservation
import com.example.kpkn.data.models.GoalMetric
import com.example.kpkn.data.models.NutritionPlan
import com.example.kpkn.data.models.PlanDirection
import java.time.LocalDate
import java.time.YearMonth
import java.time.ZoneId
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * Tendencia del peso de Cuerpo (T4) y fecha estimada con margen (T5). Los valores esperados están escritos a mano: las fechas
 * salen de contar los días del calendario y las series sintéticas se validaron antes con una simulación independiente.
 */
class BodyWeightTrendTest {

    private val zone = ZoneId.of("UTC")
    private val today = LocalDate.of(2026, 10, 13)

    private fun weight(id: String, kg: Double, day: LocalDate, hour: Int = 12): BodyObservation = BodyObservation(
        id = id,
        metric = BodyMetric.WEIGHT,
        valueSi = kg,
        unitSi = "kg",
        timestampEpochMs = day.atTime(hour, 0).atZone(zone).toInstant().toEpochMilli(),
        zoneId = zone.id,
    )

    /** Un pesaje por día en [kgs], el último hoy. */
    private fun daily(vararg kgs: Double): List<BodyObservation> = kgs.mapIndexed { index, kg ->
        weight("d$index", kg, today.minusDays((kgs.size - 1 - index).toLong()))
    }

    private fun snapshot(
        daysOfData: Int,
        slope: Double?,
        stdErr: Double? = slope?.let { 0.3 },
        provisional: Boolean = slope == null,
    ) = BodyWeightTrendSnapshot(
        trendKg = 80.0,
        slopeKgPerWeek = slope,
        slopeStdErrKgPerWeek = stdErr,
        lastWeighIn = BodyMetricPoint(today, 80.0, emptyList()),
        isProvisional = provisional,
        daysOfData = daysOfData,
    )

    // ── Series sintéticas ───────────────────────────────────────────────────────────────────────────────────

    /** Ruido diario de ±0,3 kg, determinista, de una serie de 28 días. */
    private val ruido28 = doubleArrayOf(
        0.2, -0.3, 0.1, 0.3, -0.2, 0.0, 0.25, -0.15, 0.1, -0.3, 0.2, 0.05, -0.25, 0.3,
        -0.1, 0.15, -0.2, 0.0, 0.3, -0.25, 0.1, -0.05, 0.2, -0.3, 0.15, 0.0, -0.1, 0.25,
    )

    /** 28 pesajes diarios (el último hoy) de 82 kg bajando 0,5 kg por semana, con [ruido28] y un pico opcional el último día. */
    private fun bajada28(picoKg: Double = 0.0): List<BodyObservation> = (0 until 28).map { d ->
        val kg = 82.0 - 0.5 / 7.0 * d + ruido28[d] + if (d == 27) picoKg else 0.0
        weight("b$d", kg, today.minusDays(27L - d))
    }

    /** Días con pesaje de una persona que pesa 3 veces por semana durante 4 semanas (lunes, miércoles y viernes). */
    private val diasTresPorSemana = listOf(0, 2, 4, 7, 9, 11, 14, 16, 18, 21, 23, 25)

    private fun verdadero(day: Int, kgPorSemana: Double): Double = 80.0 + kgPorSemana / 7.0 * day

    private fun tresPorSemana(ruido: List<Double>, kgPorSemana: Double): List<BodyObservation> {
        val inicio = today.minusDays(27)
        return diasTresPorSemana.mapIndexed { i, d -> weight("s$d", verdadero(d, kgPorSemana) + ruido[i], inicio.plusDays(d.toLong())) }
    }

    // ── Sin datos suficientes ───────────────────────────────────────────────────────────────────────────────

    @Test
    fun `sin pesajes no hay tendencia`() {
        assertNull(bodyWeightTrendSnapshot(emptyList(), zone, today))
        val summary = summarizeWeight(emptyList(), BodyChartRange.ALL, ResolvedBodyGoal(GoalMetric.WEIGHT, 70.0), zone, today)
        assertNull(summary.trendKg)
        assertNull(summary.etaRange)
    }

    @Test
    fun `un unico pesaje no da tendencia ni ritmo ni fecha`() {
        val observations = daily(78.4)

        assertNull(bodyWeightTrendSnapshot(observations, zone, today))
        val summary = summarizeWeight(observations, BodyChartRange.ALL, ResolvedBodyGoal(GoalMetric.WEIGHT, 70.0), zone, today)
        assertEquals(78.4, summary.current!!, 1e-9)
        assertNull(summary.trendKg)
        assertNull(summary.trendWeeklyRate)
        assertNull(summary.trendRemaining)
        assertNull(summary.etaRange)
    }

    @Test
    fun `varios pesajes del mismo dia en un solo dia siguen sin ser tendencia`() {
        val observations = listOf(weight("a", 80.0, today, hour = 7), weight("b", 80.4, today, hour = 21))

        assertNull(bodyWeightTrendSnapshot(observations, zone, today))
    }

    @Test
    fun `dos dias con pesaje dan tendencia provisional sin ritmo`() {
        val snapshot = bodyWeightTrendSnapshot(daily(80.0, 79.0), zone, today)!!

        // La mediana de los dos primeros días es su promedio.
        assertEquals(79.5, snapshot.trendKg, 1e-9)
        assertNull(snapshot.slopeKgPerWeek)
        assertNull(snapshot.slopeStdErrKgPerWeek)
        assertTrue(snapshot.isProvisional)
        assertEquals(2, snapshot.daysOfData)
        assertEquals(79.0, snapshot.lastWeighIn.value, 1e-9)
    }

    @Test
    fun `con tres dias con pesaje ya no es provisional pero el ritmo aun no existe`() {
        val snapshot = bodyWeightTrendSnapshot(daily(80.0, 79.5, 79.2), zone, today)!!

        // El estimador arranca la pendiente en 0 con tres pesajes: eso no es una medida y no se muestra como ritmo.
        assertEquals(79.5, snapshot.trendKg, 1e-9)
        assertFalse(snapshot.isProvisional)
        assertNull(snapshot.slopeKgPerWeek)
    }

    @Test
    fun `con cuatro dias con pesaje aparece la pendiente`() {
        val snapshot = bodyWeightTrendSnapshot(daily(80.0, 79.5, 79.2, 78.9), zone, today)!!

        assertNotNull(snapshot.slopeKgPerWeek)
        assertTrue(snapshot.slopeStdErrKgPerWeek!! > 0.0)
        assertEquals(4, snapshot.daysOfData)
    }

    @Test
    fun `el ritmo de Cuerpo solo se muestra con 14 dias de datos`() {
        val goal = ResolvedBodyGoal(GoalMetric.WEIGHT, 70.0)
        val diezDias = (0 until 10).map { d -> weight("x$d", 80.0 - 0.07 * d, today.minusDays(9L - d)) }

        assertNull(summarizeWeight(diezDias, BodyChartRange.ALL, goal, zone, today).trendWeeklyRate)
        assertNotNull(summarizeWeight(bajada28(), BodyChartRange.ALL, goal, zone, today).trendWeeklyRate)
    }

    // ── Medianas y futuro ───────────────────────────────────────────────────────────────────────────────────

    @Test
    fun `varios pesajes el mismo dia cuentan por su mediana`() {
        val observations = listOf(
            weight("m1", 80.0, today.minusDays(1), hour = 6),
            weight("m2", 80.2, today.minusDays(1), hour = 12),
            weight("m3", 85.0, today.minusDays(1), hour = 22),
            weight("m4", 80.2, today),
        )

        val snapshot = bodyWeightTrendSnapshot(observations, zone, today)!!

        // Mediana del primer día: 80,2 (el promedio sería 81,7). Con dos días iguales, la tendencia es esa cifra.
        assertEquals(80.2, snapshot.trendKg, 1e-9)
        assertEquals(80.2, snapshot.lastWeighIn.value, 1e-9)
        assertEquals(today, snapshot.lastWeighIn.date)
    }

    @Test
    fun `los pesajes futuros se ignoran`() {
        val futuro = weight("f", 60.0, today.plusDays(1))

        val snapshot = bodyWeightTrendSnapshot(daily(80.0, 79.0) + futuro, zone, today)!!

        // El pesaje de mañana no cuenta: el último día es hoy, con dos días de datos.
        assertEquals(today, snapshot.lastWeighIn.date)
        assertEquals(79.5, snapshot.trendKg, 1e-9)
        assertEquals(2, snapshot.daysOfData)
        // Un pesaje de hoy y otro futuro no hacen dos días.
        assertNull(bodyWeightTrendSnapshot(daily(79.0) + futuro, zone, today))
    }

    @Test
    fun `los valores fuera de rango no cuentan`() {
        val observations = daily(80.0, 79.5, 79.2, 78.9) + weight("raro", 5.0, today)

        assertEquals(78.9, bodyWeightTrendSnapshot(observations, zone, today)!!.lastWeighIn.value, 1e-9)
    }

    // ── (a) Un pesaje atípico ───────────────────────────────────────────────────────────────────────────────

    @Test
    fun `un pesaje atipico de dos kilos el ultimo dia no cambia el mes ni invierte la pendiente`() {
        val goal = ResolvedBodyGoal(GoalMetric.WEIGHT, 76.0)

        val limpio = summarizeWeight(bajada28(), BodyChartRange.ALL, goal, zone, today)
        val conPico = summarizeWeight(bajada28(picoKg = 2.0), BodyChartRange.ALL, goal, zone, today)

        // 82 kg bajando 0,5 kg/semana: la tendencia ronda 80,2 kg y quedan 4,2 kg hacia 76 kg, unas 9 semanas: a mediados de diciembre.
        assertEquals(YearMonth.of(2026, 12), limpio.etaRange!!.central)
        assertEquals(YearMonth.of(2026, 12), conPico.etaRange!!.central)
        assertTrue(limpio.trendWeeklyRate!! < 0.0)
        assertTrue(conPico.trendWeeklyRate!! < 0.0)
        // El pico mueve la tendencia centésimas, no los 2 kg del pesaje.
        assertEquals(limpio.trendKg!!, conPico.trendKg!!, 0.1)
    }

    @Test
    fun `un pico negativo de dos kilos tampoco cambia el mes`() {
        val goal = ResolvedBodyGoal(GoalMetric.WEIGHT, 76.0)

        val conHueco = summarizeWeight(bajada28(picoKg = -2.0), BodyChartRange.ALL, goal, zone, today)

        assertEquals(YearMonth.of(2026, 12), conHueco.etaRange!!.central)
        assertTrue(conHueco.trendWeeklyRate!! < 0.0)
    }

    // ── (b) Ruido de ±0,6 kg, 3 pesajes por semana ──────────────────────────────────────────────────────────

    @Test
    fun `con ruido de 0,6 kg y tres pesajes por semana la tendencia queda a menos de 0,3 kg de la verdadera`() {
        val series = listOf(
            listOf(0.4, -0.5, 0.2, -0.3, 0.6, -0.1, -0.6, 0.3, 0.5, -0.4, 0.1, -0.2),
            listOf(-0.6, 0.5, -0.2, 0.6, -0.4, 0.1, 0.3, -0.5, -0.1, 0.4, -0.3, 0.2),
            listOf(0.1, 0.3, -0.2, -0.5, 0.6, 0.2, -0.4, 0.5, -0.6, 0.0, 0.3, -0.3),
        )
        for (ruido in series) {
            val snapshot = bodyWeightTrendSnapshot(tresPorSemana(ruido, kgPorSemana = -0.5), zone, today)!!

            // La serie verdadera baja 0,5 kg por semana; el último pesaje es el día 25.
            assertEquals(verdadero(25, -0.5), snapshot.trendKg, 0.3)
            assertTrue(snapshot.slopeKgPerWeek!! < 0.0)
        }
    }

    @Test
    fun `con peso estable la tendencia tampoco se inventa un cambio`() {
        val ruido = listOf(0.4, -0.5, 0.2, -0.3, 0.6, -0.1, -0.6, 0.3, 0.5, -0.4, 0.1, -0.2)

        val snapshot = bodyWeightTrendSnapshot(tresPorSemana(ruido, kgPorSemana = 0.0), zone, today)!!

        assertEquals(80.0, snapshot.trendKg, 0.3)
        assertTrue(kotlin.math.abs(snapshot.slopeKgPerWeek!!) < 0.3)
    }

    // ── (c) Mezcla con el ritmo del plan en los primeros 14 días ────────────────────────────────────────────

    @Test
    fun `el peso del ritmo medido crece linealmente del dia 3 al 14`() {
        assertEquals(0.0, trendDataWeight(1), 1e-12)
        assertEquals(0.0, trendDataWeight(3), 1e-12)
        assertEquals(5.0 / 11.0, trendDataWeight(8), 1e-12)
        assertEquals(1.0, trendDataWeight(14), 1e-12)
        assertEquals(1.0, trendDataWeight(40), 1e-12)
    }

    @Test
    fun `antes de 14 dias el ritmo mezcla lo medido con el del plan`() {
        // Día 8: w = 5/11. −0,4 medido y −0,6 del plan → (5·(−0,4) + 6·(−0,6)) / 11 = −5,6/11. El error: (5·0,3 + 6·0,09) / 11.
        val rate = blendedTrendRate(snapshot(daysOfData = 8, slope = -0.4, stdErr = 0.3), planRateKgPerWeek = -0.6)!!

        assertEquals(-5.6 / 11.0, rate.kgPerWeek, 1e-9)
        assertEquals((5.0 * 0.3 + 6.0 * 0.09) / 11.0, rate.stdErrKgPerWeek, 1e-9)
    }

    @Test
    fun `en el dia 3 solo cuenta el plan y desde el dia 14 solo lo medido`() {
        val alInicio = blendedTrendRate(snapshot(daysOfData = 3, slope = -0.4, stdErr = 0.3), planRateKgPerWeek = -0.6)!!
        assertEquals(-0.6, alInicio.kgPerWeek, 1e-9)
        assertEquals(0.09, alInicio.stdErrKgPerWeek, 1e-9)

        for (days in listOf(14, 30)) {
            val medido = blendedTrendRate(snapshot(daysOfData = days, slope = -0.4, stdErr = 0.3), planRateKgPerWeek = -0.6)!!
            assertEquals(-0.4, medido.kgPerWeek, 1e-9)
            assertEquals(0.3, medido.stdErrKgPerWeek, 1e-9)
        }
    }

    @Test
    fun `sin plan no hay mezcla`() {
        val rate = blendedTrendRate(snapshot(daysOfData = 8, slope = -0.4, stdErr = 0.3), planRateKgPerWeek = null)!!

        assertEquals(-0.4, rate.kgPerWeek, 1e-9)
        assertEquals(0.3, rate.stdErrKgPerWeek, 1e-9)
        assertNull(blendedTrendRate(snapshot(daysOfData = 2, slope = null), planRateKgPerWeek = null))
        assertNull(blendedTrendRate(null, planRateKgPerWeek = -0.6))
    }

    @Test
    fun `con la pendiente provisional y un plan, el ritmo es el del plan`() {
        val rate = blendedTrendRate(snapshot(daysOfData = 2, slope = null), planRateKgPerWeek = -0.6)!!

        assertEquals(-0.6, rate.kgPerWeek, 1e-9)
    }

    @Test
    fun `la fecha de los primeros dias se apoya en el plan y sin plan no hay fecha`() {
        // Cinco días a 80,0 kg: lo medido no se mueve (pendiente 0). Con un plan de −0,6 kg/semana, w = 2/11 → ritmo −0,4909…:
        // 6 kg hasta 74 kg a ese ritmo son 85,6 días → 86 días desde el 13 de octubre = 7 de enero de 2027.
        val observations = daily(80.0, 80.0, 80.0, 80.0, 80.0)
        val goal = ResolvedBodyGoal(GoalMetric.WEIGHT, 74.0)

        val conPlan = summarizeWeight(observations, BodyChartRange.ALL, goal, zone, today, planWeeklyRateKg = -0.6)
        val sinPlan = summarizeWeight(observations, BodyChartRange.ALL, goal, zone, today)

        assertEquals(YearMonth.of(2027, 1), conPlan.etaRange!!.central)
        assertNull(sinPlan.etaRange)
        // El plan no entra en ninguna otra cifra: ni ritmo mostrado ni tendencia.
        assertNull(conPlan.trendWeeklyRate)
        assertEquals(sinPlan.trendKg, conPlan.trendKg)
    }

    @Test
    fun `el ritmo del plan sale de la direccion y de los kilos por semana`() {
        assertEquals(-0.5, planWeeklyRateKg(NutritionPlan(direction = PlanDirection.DEFICIT, weeklyChangeKg = 0.5))!!, 1e-12)
        assertEquals(0.25, planWeeklyRateKg(NutritionPlan(direction = PlanDirection.SURPLUS, weeklyChangeKg = 0.25))!!, 1e-12)
        assertNull(planWeeklyRateKg(NutritionPlan(direction = PlanDirection.MAINTENANCE, weeklyChangeKg = 0.5)))
        assertNull(planWeeklyRateKg(NutritionPlan(direction = PlanDirection.PROFESSIONAL, weeklyChangeKg = 0.5)))
        assertNull(planWeeklyRateKg(NutritionPlan(direction = null, weeklyChangeKg = 0.5)))
        assertNull(planWeeklyRateKg(NutritionPlan(direction = PlanDirection.DEFICIT, weeklyChangeKg = 0.0)))
        assertNull(planWeeklyRateKg(NutritionPlan(direction = PlanDirection.DEFICIT, weeklyChangeKg = 0.5, weeklyChangeUnit = "lb/week")))
        assertNull(planWeeklyRateKg(null))
    }

    // ── Fecha estimada con intervalo ────────────────────────────────────────────────────────────────────────

    @Test
    fun `el rango va del ritmo mas rapido al mas lento dentro de una desviacion`() {
        // 78 → 70 kg: 8 kg. A 0,5 kg/semana, 112 días → 2 de febrero; a 0,6, 94 días → 15 de enero; a 0,4, 140 días → 2 de marzo.
        val range = goalEtaRange(current = 78.0, target = 70.0, slope = -0.5, stdErr = 0.1, today = today)!!

        assertEquals(YearMonth.of(2027, 2), range.central)
        assertEquals(YearMonth.of(2027, 1), range.earliest)
        assertEquals(YearMonth.of(2027, 3), range.latest)
        assertTrue(range.isRange)
    }

    @Test
    fun `subir de peso es simetrico`() {
        val range = goalEtaRange(current = 70.0, target = 78.0, slope = 0.5, stdErr = 0.1, today = today)!!

        assertEquals(YearMonth.of(2027, 2), range.central)
        assertEquals(YearMonth.of(2027, 1), range.earliest)
        assertEquals(YearMonth.of(2027, 3), range.latest)
    }

    @Test
    fun `si los extremos caen en el mismo mes no hay rango`() {
        val range = goalEtaRange(current = 78.0, target = 70.0, slope = -0.5, stdErr = 0.001, today = today)!!

        assertEquals(YearMonth.of(2027, 2), range.central)
        assertEquals(YearMonth.of(2027, 2), range.earliest)
        assertEquals(YearMonth.of(2027, 2), range.latest)
        assertFalse(range.isRange)
        assertNull(etaRangeText(range, today))
    }

    @Test
    fun `sin error estandar solo hay el mes central`() {
        val range = goalEtaRange(current = 78.0, target = 70.0, slope = -0.5, stdErr = null, today = today)!!

        assertEquals(YearMonth.of(2027, 2), range.central)
        assertFalse(range.isRange)
    }

    @Test
    fun `si el ritmo lento baja de 0,05 por semana el rango queda abierto`() {
        // 0,5 − 0,46 = 0,04 kg/semana: ya no hay fecha estimable por ese lado. Por el rápido (0,96), 59 días → 11 de diciembre.
        val range = goalEtaRange(current = 78.0, target = 70.0, slope = -0.5, stdErr = 0.46, today = today)!!

        assertEquals(YearMonth.of(2027, 2), range.central)
        assertEquals(YearMonth.of(2026, 12), range.earliest)
        assertNull(range.latest)
        assertTrue(range.isRange)
    }

    @Test
    fun `sin ritmo hacia la meta o con menos de 0,05 por semana no hay fecha`() {
        assertNull(goalEtaRange(78.0, 85.0, -0.5, 0.1, today))
        assertNull(goalEtaRange(78.0, 70.0, -0.04, 0.01, today))
        assertNull(goalEtaRange(78.0, 70.0, 0.5, 0.1, today))
        assertNull(goalEtaRange(70.0, 70.0, -0.5, 0.1, today))
        assertNull(goalEtaRange(null, 70.0, -0.5, 0.1, today))
        assertNull(goalEtaRange(78.0, null, -0.5, 0.1, today))
        assertNull(goalEtaRange(78.0, 70.0, null, 0.1, today))
    }

    @Test
    fun `una fecha a mas de 30 anos no se estima`() {
        assertNull(goalEtaRange(current = 500.0, target = 20.0, slope = -0.05, stdErr = 0.0, today = today))
    }

    @Test
    fun `goalEtaMonth conserva su comportamiento`() {
        assertEquals(YearMonth.of(2027, 2), goalEtaMonth(78.0, 70.0, -0.5, today))
        assertNull(goalEtaMonth(78.0, 85.0, -0.5, today))
        assertNull(goalEtaMonth(78.0, 70.0, -0.04, today))
        assertNull(goalEtaMonth(78.0, 70.0, null, today))
    }

    @Test
    fun `el texto del rango dice los meses y el ano solo cuando hace falta`() {
        fun range(central: YearMonth, earliest: YearMonth, latest: YearMonth?) = EtaRange(central, earliest, latest)

        assertEquals(
            "entre noviembre y diciembre",
            etaRangeText(range(YearMonth.of(2026, 12), YearMonth.of(2026, 11), YearMonth.of(2026, 12)), today),
        )
        assertEquals(
            "entre enero y marzo de 2027",
            etaRangeText(range(YearMonth.of(2027, 2), YearMonth.of(2027, 1), YearMonth.of(2027, 3)), today),
        )
        assertEquals(
            "entre noviembre de 2026 y enero de 2027",
            etaRangeText(range(YearMonth.of(2026, 12), YearMonth.of(2026, 11), YearMonth.of(2027, 1)), today),
        )
        assertEquals("a partir de diciembre", etaRangeText(range(YearMonth.of(2027, 2), YearMonth.of(2026, 12), null), today))
        assertEquals("a partir de marzo de 2027", etaRangeText(range(YearMonth.of(2027, 5), YearMonth.of(2027, 3), null), today))
    }

    @Test
    fun `el texto compacto del rango cabe en la tarjeta de Inicio`() {
        fun range(central: YearMonth, earliest: YearMonth, latest: YearMonth?) = EtaRange(central, earliest, latest)

        assertEquals("nov – dic", etaRangeText(range(YearMonth.of(2026, 12), YearMonth.of(2026, 11), YearMonth.of(2026, 12)), today, compact = true))
        assertEquals("ene – mar 2027", etaRangeText(range(YearMonth.of(2027, 2), YearMonth.of(2027, 1), YearMonth.of(2027, 3)), today, compact = true))
        assertEquals(
            "nov – ene 2027",
            etaRangeText(range(YearMonth.of(2026, 12), YearMonth.of(2026, 11), YearMonth.of(2027, 1)), today, compact = true),
        )
        assertEquals("desde dic", etaRangeText(range(YearMonth.of(2027, 2), YearMonth.of(2026, 12), null), today, compact = true))
        assertEquals("desde mar 2027", etaRangeText(range(YearMonth.of(2027, 5), YearMonth.of(2027, 3), null), today, compact = true))
    }

    @Test
    fun `el mes central lleva el ano solo si no es el de hoy`() {
        assertEquals("diciembre", etaMonthText(YearMonth.of(2026, 12), today))
        assertEquals("febrero de 2027", etaMonthText(YearMonth.of(2027, 2), today))
    }

    // ── (e) Regresión: lo heredado no cambia ────────────────────────────────────────────────────────────────

    @Test
    fun `summarizeWeight conserva el ritmo y el mes heredados y suma los nuevos`() {
        // Misma serie que BodyWeightSummaryTest: 28 días que acaban en 78 kg y bajan 0,5 kg por semana, meta de 70 kg.
        val observations = (0 until 28).map { index ->
            val daysBeforeLast = 27 - index
            weight("t$index", 78.0 + 0.5 / 7.0 * daysBeforeLast, today.minusDays(daysBeforeLast.toLong()))
        }

        val summary = summarizeWeight(observations, BodyChartRange.ALL, ResolvedBodyGoal(GoalMetric.WEIGHT, 70.0), zone, today)

        assertEquals(-0.5, summary.measuredWeeklyRate!!, 1e-9)
        assertEquals(YearMonth.of(2027, 2), summary.eta)
        assertEquals(78.0, summary.current!!, 1e-9)
        assertEquals(-8.0, summary.remaining!!, 1e-9)
        // La tendencia de una recta sin ruido llega ≈ 0,06 kg por encima del último pesaje (valor validado con la simulación).
        assertEquals(78.06, summary.trendKg!!, 0.02)
        assertEquals(-8.06, summary.trendRemaining!!, 0.02)
        assertNotNull(summary.etaRange)
    }

    // ── Una sola tendencia: serie de la gráfica y cambio ────────────────────────────────────────────────────

    @Test
    fun `la serie de la tendencia termina exactamente en la cifra de la tendencia`() {
        val observations = bajada28()

        val series = bodyWeightTrendSeries(observations, zone, today)

        assertEquals(28, series.size)
        assertEquals(today.minusDays(27), series.first().date)
        assertEquals(today, series.last().date)
        assertEquals(bodyWeightTrendSnapshot(observations, zone, today)!!.trendKg, series.last().value, 1e-12)
    }

    @Test
    fun `la serie de la tendencia esta vacia con menos de dos dias con pesaje`() {
        assertTrue(bodyWeightTrendSeries(emptyList(), zone, today).isEmpty())
        assertTrue(bodyWeightTrendSeries(daily(78.4), zone, today).isEmpty())
    }

    @Test
    fun `un pico de dos kilos el ultimo dia no mueve el ultimo punto de la linea mas de 0,3 kg`() {
        fun lineaDeLaGrafica(observations: List<BodyObservation>): List<BodyMetricPoint> = weightChartTrend(
            bodyWeightTrendSeries(observations, zone, today),
            bodyDailySeries(observations, zone),
            firstVisibleDay = null,
        )

        val limpia = lineaDeLaGrafica(bajada28())
        val conPico = lineaDeLaGrafica(bajada28(picoKg = 2.0))

        assertEquals(limpia.last().value, conPico.last().value, 0.3)
        // La línea no se dobla hacia arriba con el pico: su último punto sigue por debajo del anterior más el pico.
        assertTrue(conPico.last().value < conPico[conPico.size - 2].value + 0.3)
    }

    @Test
    fun `la linea de la grafica se recorta al rango visible`() {
        val observations = bajada28()
        val trendSeries = bodyWeightTrendSeries(observations, zone, today)
        val primerDia = today.minusDays(9)

        val linea = weightChartTrend(trendSeries, bodyDailySeries(observations, zone), primerDia)

        assertEquals(10, linea.size)
        assertEquals(primerDia, linea.first().date)
    }

    @Test
    fun `sin serie de tendencia la linea de la grafica es la EWMA de siempre`() {
        val serie = bodyDailySeries(daily(80.0), zone)

        assertEquals(ewmaTrend(serie), weightChartTrend(emptyList(), serie, firstVisibleDay = null))
    }

    @Test
    fun `el cambio por tendencia de dos pesajes seguidos es la mitad del cambio heredado`() {
        // Tendencia: 80,0 el primer día (un solo dato) y 79,5 el segundo (mediana de 80 y 79). El cambio heredado es 79 − 80.
        val summary = summarizeWeight(daily(80.0, 79.0), BodyChartRange.ALL, null, zone, today)

        assertEquals(-0.5, summary.trendChange!!, 1e-9)
        assertEquals(-1.0, summary.change!!, 1e-9)
    }

    @Test
    fun `un pico de dos kilos no invierte el signo del cambio mostrado`() {
        val limpio = summarizeWeight(bajada28(), BodyChartRange.ALL, null, zone, today)
        val conPico = summarizeWeight(bajada28(picoKg = 2.0), BodyChartRange.ALL, null, zone, today)

        // De 82,2 kg (primer día) a una tendencia de ≈ 80,17 kg: −2,03 kg, con o sin el pico.
        assertEquals(-2.03, limpio.trendChange!!, 0.05)
        assertEquals(-2.02, conPico.trendChange!!, 0.05)
        // El cambio heredado (último pesaje menos primero) sí se da la vuelta con el pico y no cambia de cálculo.
        assertTrue(conPico.change!! > 0.0)
    }

    @Test
    fun `sin tendencia no hay cambio por tendencia y el heredado se conserva`() {
        val summary = summarizeWeight(daily(78.4), BodyChartRange.ALL, null, zone, today)

        assertNull(summary.trendChange)
        assertEquals(0.0, summary.change!!, 1e-9)
    }

    @Test
    fun `el cambio usa el dia con dato mas cercano al inicio`() {
        val d = today
        val serie = listOf(
            BodyMetricPoint(d.minusDays(4), 80.0, emptyList()),
            BodyMetricPoint(d.minusDays(2), 79.0, emptyList()),
            BodyMetricPoint(d, 78.0, emptyList()),
        )

        // Antes del primer dato: el más cercano es el primero. En un día con dato: ese día.
        assertEquals(-2.0, bodyTrendChange(serie, d.minusDays(9))!!, 1e-12)
        assertEquals(-1.0, bodyTrendChange(serie, d.minusDays(2))!!, 1e-12)
        // A igual distancia de dos días con dato gana el anterior.
        assertEquals(-2.0, bodyTrendChange(serie, d.minusDays(3))!!, 1e-12)
        assertNull(bodyTrendChange(emptyList(), d))
    }
}
