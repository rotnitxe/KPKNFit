package com.example.kpkn.domain.nutrition.expenditure

import java.time.LocalDate
import kotlin.math.abs
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class ExpenditureEstimatorTest {
    private val start = LocalDate.of(2026, 1, 1)
    private val estimator = ExpenditureEstimator()

    private fun input(count: Int = 84, intake: Double = 2500.0) = ExpenditureInput(
        initialKcal = 2500.0,
        floorKcal = 1200.0,
        days = (0 until count).map { DayObservation(start.plusDays(it.toLong()), intake) },
        weights = (0 until count).map { WeightObservation(start.plusDays(it.toLong()), 80.0) },
        through = start.plusDays(count.toLong() - 1),
    )

    @Test
    fun `con el mismo peso trescientas kcal adicionales cambian el gasto en trescientas`() {
        val low = estimator.estimateSeries(input(intake = 2200.0)).last()
        val high = estimator.estimateSeries(input(intake = 2500.0)).last()
        assertEquals(300.0, high.kcal - low.kcal, 30.0)
    }

    @Test
    fun `catorce dias de colaciones sin revisar no habilitan la estimacion`() {
        val result = estimator.estimateSeries(input(14, 150.0)).last()
        assertEquals(ExpenditureState.PAUSED, result.state)
        assertEquals(PauseReason.INSUFFICIENT_LOGGING, result.pauseReason)
        assertEquals(0, result.quality.usedDays)
        assertEquals(14, result.quality.excluded[ExclusionReason.UNREVIEWED_LOW])
        assertEquals(2500.0, result.kcal, 0.0)
    }

    @Test
    fun `la confirmacion expresa permite usar una ingesta baja sin cambiar registros`() {
        val original = input(14, 150.0)
        val confirmed = original.copy(days = original.days.map { it.copy(mark = DayMark.COMPLETE_CONFIRMED) })
        val result = estimator.estimateSeries(confirmed).last()
        assertEquals(14, result.quality.usedDays)
        assertEquals(ExpenditureState.LEARNING, result.state)
        assertNull(result.pauseReason)
        assertTrue(result.kcal < confirmed.initialKcal)
        assertTrue(original.days.all { it.mark == DayMark.NONE })
    }

    @Test
    fun `ayuno cuenta como cero y vacio sin marca queda fuera`() {
        val original = input(21)
        val fastingDate = start.plusDays(19)
        val missingDate = start.plusDays(20)
        val days = original.days.map {
            when (it.date) {
                fastingDate -> it.copy(intakeKcal = null, mark = DayMark.FASTING)
                missingDate -> it.copy(intakeKcal = null)
                else -> it
            }
        }
        val result = estimator.estimateSeries(original.copy(days = days)).last()
        assertEquals(20, result.quality.usedDays)
        assertEquals(mapOf(missingDate to ExclusionReason.NO_LOG), result.quality.excludedDates)
        assertTrue(result.kcal < 2500.0)
    }

    @Test
    fun `enumera cada exclusion y ni la confirmacion legitima una cifra imposible`() {
        val original = input(21)
        val values = listOf<Double?>(null, 0.0, -80.0, Double.NaN, Double.POSITIVE_INFINITY, 18_001.0, 150.0, 2400.0)
        val days = original.days.mapIndexed { index, day ->
            if (index < 13) day else day.copy(
                intakeKcal = values[index - 13],
                mark = when (index) { 19 -> DayMark.NONE; 20 -> DayMark.PARTIAL; else -> DayMark.COMPLETE_CONFIRMED },
            )
        }
        val quality = estimator.estimateSeries(original.copy(days = days)).last().quality
        assertEquals(ExclusionReason.NO_LOG, quality.excludedDates[start.plusDays(13)])
        for (index in 14..18) assertEquals(ExclusionReason.IMPLAUSIBLE, quality.excludedDates[start.plusDays(index.toLong())])
        assertEquals(ExclusionReason.UNREVIEWED_LOW, quality.excludedDates[start.plusDays(19)])
        assertEquals(ExclusionReason.PARTIAL, quality.excludedDates[start.plusDays(20)])
        assertEquals(quality.windowDays, quality.usedDays + quality.excluded.values.sum())
        assertEquals(quality.excludedDates.size, quality.excluded.values.sum())
    }

    @Test
    fun `futuros alimentos pesajes y cambios de objetivo no alteran ningun resultado`() {
        val original = input()
        val future = original.through.plusDays(1)
        val extended = original.copy(
            days = original.days + DayObservation(future, 17_000.0),
            weights = original.weights + WeightObservation(future, 150.0),
            goalChangeDates = listOf(future),
        )
        assertEquals(estimator.estimateSeries(original), estimator.estimateSeries(extended))
    }

    @Test
    fun `editar una ingesta pasada conserva exactamente todas las estimaciones anteriores`() {
        val original = input()
        val edited = original.copy(days = original.days.mapIndexed { index, day -> if (index == 60) day.copy(intakeKcal = 3100.0) else day })
        val before = estimator.estimateSeries(original)
        val after = estimator.estimateSeries(edited)
        assertEquals(before.take(60), after.take(60))
        assertTrue(before[60] != after[60])
        assertTrue(before.last().kcal != after.last().kcal)
    }

    @Test
    fun `editar un pesaje pasado conserva exactamente el prefijo de la serie`() {
        val original = input()
        val edited = original.copy(weights = original.weights.mapIndexed { index, point -> if (index == 60) point.copy(kg = 80.5) else point })
        val before = estimator.estimateSeries(original)
        val after = estimator.estimateSeries(edited)
        assertEquals(before.take(60), after.take(60))
        assertTrue(before[60] != after[60])
    }

    @Test
    fun `siete dias sin registro pausan y congelan el valor hasta reanudar sin salto`() {
        val original = input(112, 2800.0)
        val withGap = original.copy(days = original.days.filterIndexed { index, _ -> index !in 56..62 })
        val series = estimator.estimateSeries(withGap)
        assertEquals(ExpenditureState.PAUSED, series[62].state)
        assertEquals(PauseReason.INSUFFICIENT_LOGGING, series[62].pauseReason)
        for (index in 1 until series.size) {
            if (series[index].state == ExpenditureState.PAUSED) assertEquals(series[index - 1].kcal, series[index].kcal, 0.0)
            assertTrue(abs(series[index].kcal - series[index - 1].kcal) <= ExpenditureParams.V1.maximumDailyChangeKcal + 1e-9)
        }
        assertFalse(series.last().state == ExpenditureState.PAUSED)
    }

    @Test
    fun `pocos pesajes poca separacion o un peso antiguo explican la pausa`() {
        val original = input(28)
        val cases = listOf(
            original.weights.take(2),
            original.weights.takeLast(4),
            original.weights.dropLast(7),
        )
        for (weights in cases) {
            val last = estimator.estimateSeries(original.copy(weights = weights)).last()
            assertEquals(ExpenditureState.PAUSED, last.state)
            assertEquals(PauseReason.INSUFFICIENT_WEIGHTS, last.pauseReason)
        }
    }

    @Test
    fun `la ventana se acorta de uno en uno hasta recuperar registros suficientes`() {
        val original = input(42)
        val recent = original.copy(days = original.days.filterIndexed { index, _ -> index !in 21..26 })
        val last = estimator.estimateSeries(recent).last()
        assertEquals(17, last.quality.windowDays)
        assertEquals(15, last.quality.usedDays)
        assertNull(last.pauseReason)
    }

    @Test
    fun `la calidad no incluye dias anteriores a la ventana ni depende de historiales ajenos`() {
        val original = input()
        val result = estimator.estimateSeries(original).last()
        assertEquals(21, result.quality.windowDays)
        assertEquals(21, result.quality.usedDays)
        assertEquals(21, result.quality.weighIns)
        assertTrue(result.quality.excludedDates.isEmpty())
        assertEquals(EXPENDITURE_MODEL_VERSION, result.modelVersion)
    }

    @Test
    fun `inicio aprendizaje y estabilidad se distinguen y la rampa conserva el punto inicial`() {
        val series = estimator.estimateSeries(input(intake = 3000.0))
        assertEquals(ExpenditureState.STARTING, series[12].state)
        assertEquals(2500.0, series[12].kcal, 0.0)
        assertEquals(ExpenditureState.LEARNING, series[13].state)
        assertTrue(series[13].kcal - 2500.0 < 10.0)
        assertEquals(ExpenditureState.STABLE, series.last().state)
    }

    @Test
    fun `respeta el suelo y ordena la banda incluso con un punto inicial inferior`() {
        val original = input(intake = 600.0).copy(initialKcal = 800.0, floorKcal = 1300.0)
        val series = estimator.estimateSeries(original)
        assertTrue(series.all { it.kcal >= 1300.0 && it.lowKcal >= 1300.0 && it.lowKcal <= it.kcal && it.kcal <= it.highKcal })
    }

    @Test
    fun `un cambio de objetivo amortigua el paso y ensancha la banda durante catorce dias`() {
        val original = input(intake = 3000.0)
        val goalDate = start.plusDays(35)
        val normal = estimator.estimateSeries(original)
        val guarded = estimator.estimateSeries(original.copy(goalChangeDates = listOf(goalDate)))
        assertEquals(normal.take(35), guarded.take(35))
        assertEquals(ExpenditureState.LEARNING, guarded[48].state)
        for (index in 35..48) {
            val previous = guarded[index - 1]
            val point = guarded[index]
            assertTrue(abs(point.kcal - previous.kcal) <= ExpenditureParams.V1.maximumDailyChangeKcal * ExpenditureParams.V1.goalUpdateFactor + 1e-9)
            assertTrue(point.highKcal - point.lowKcal > normal[index].highKcal - normal[index].lowKcal)
        }
        assertEquals(ExpenditureState.STABLE, guarded.last().state)
    }

    @Test
    fun `sin datos no fabrica una serie y si solo son futuros tampoco`() {
        val original = input()
        assertTrue(estimator.estimateSeries(original.copy(days = emptyList(), weights = emptyList())).isEmpty())
        assertTrue(estimator.estimateSeries(original.copy(through = start.minusDays(1))).isEmpty())
    }

    @Test
    fun `es determinista y acepta datos desordenados`() {
        val original = input()
        assertEquals(estimator.estimateSeries(original), estimator.estimateSeries(original.copy(days = original.days.reversed(), weights = original.weights.reversed())))
    }

    @Test(expected = IllegalArgumentException::class)
    fun `rechaza totales diarios duplicados en vez de sumarlos silenciosamente`() {
        val original = input()
        estimator.estimateSeries(original.copy(days = original.days + original.days.first()))
    }

    @Test(expected = IllegalArgumentException::class)
    fun `rechaza un punto de partida no finito`() {
        estimator.estimateSeries(input().copy(initialKcal = Double.NaN))
    }
}
