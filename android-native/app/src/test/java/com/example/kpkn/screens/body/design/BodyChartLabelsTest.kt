package com.example.kpkn.screens.body.design

import java.time.LocalDate
import org.junit.Assert.assertEquals
import org.junit.Test

/** El eje de la gráfica de Cuerpo no repite fechas: dos pesajes del mismo día dejan una sola marca. */
class BodyChartLabelsTest {

    private fun point(date: String, value: Double) = BodyChartPoint(date = LocalDate.parse(date), value = value, trend = value)

    @Test
    fun `two weighings on the same day leave a single mark on the axis`() {
        val labels = bodyChartLabels(
            listOf(point("2026-10-13", 23.8), point("2026-10-13", 24.0), point("2026-10-14", 24.0)),
        )
        assertEquals(listOf("13 oct", "14 oct"), labels?.axis)
    }

    @Test
    fun `the middle mark never repeats an end of the range`() {
        val labels = bodyChartLabels(listOf(point("2026-10-13", 24.0), point("2026-10-14", 24.0)))
        assertEquals(listOf("13 oct", "14 oct"), labels?.axis)
    }

    @Test
    fun `a single day gives a single mark`() {
        val labels = bodyChartLabels(listOf(point("2026-10-13", 24.0), point("2026-10-13", 23.9)))
        assertEquals(listOf("13 oct"), labels?.axis)
    }

    @Test
    fun `a longer range keeps its three marks in order`() {
        val labels = bodyChartLabels(listOf(point("2026-10-11", 64.0), point("2026-10-12", 63.9), point("2026-10-14", 63.8)))
        assertEquals(listOf("11 oct", "12 oct", "14 oct"), labels?.axis)
    }
}
