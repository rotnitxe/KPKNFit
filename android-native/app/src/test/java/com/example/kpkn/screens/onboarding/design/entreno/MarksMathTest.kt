package com.example.kpkn.screens.onboarding.design.entreno

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import kotlin.math.abs

/**
 * Contratos puros de la regla de marcas: conversión kg ↔ lb sin deriva, escalón de 2,5 kg / 5 lb, saturación a
 * 20–400 kg / 44–880 lb, texto con coma decimal y la relación entre posición y valor de la regla.
 */
class MarksMathTest {

    private val eps = 1e-9

    // ─── Conversión ──────────────────────────────────────────────────────────

    @Test
    fun `kg y lb se convierten con la libra internacional`() {
        assertEquals(220.4622622, MarksMath.kgToLb(100.0), 1e-6)
        assertEquals(102.0582833, MarksMath.lbToKg(225.0), 1e-6)
        assertEquals(0.0, MarksMath.kgToLb(0.0), eps)
    }

    @Test
    fun `ida y vuelta kg a lb a kg no se desvia mas de 0,05 con el redondeo de presentacion`() {
        var kg = MarksMath.MIN_KG
        var worst = 0.0
        while (kg <= MarksMath.MAX_KG + 1e-9) {
            val shownLb = MarksMath.roundForDisplay(MarksMath.kgToLb(kg))
            val back = MarksMath.lbToKg(shownLb)
            worst = maxOf(worst, abs(back - kg))
            kg += 0.1
        }
        assertTrue("deriva máxima $worst kg", worst <= 0.05)
    }

    @Test
    fun `ida y vuelta lb a kg a lb conserva la decima mostrada`() {
        var lb = MarksMath.MIN_LB
        while (lb <= MarksMath.MAX_LB + 1e-9) {
            val back = MarksMath.displayValue(MarksMath.lbToKg(lb), MarksMath.UNIT_LB)
            assertEquals("lb $lb", lb, back, 0.1 + 1e-9)
            lb += 0.5
        }
    }

    @Test
    fun `alternar la unidad no acumula error`() {
        val original = 142.88
        var kg = original
        repeat(100) {
            val lb = MarksMath.kgToLb(kg)
            kg = MarksMath.lbToKg(lb)
        }
        assertEquals(original, kg, 1e-9)
    }

    @Test
    fun `el redondeo de presentacion es una decima a la mitad hacia arriba y no toca lo no finito`() {
        assertEquals(314.2, MarksMath.roundForDisplay(314.16), eps)
        assertEquals(142.5, MarksMath.roundForDisplay(142.5), eps)
        assertEquals(20.1, MarksMath.roundForDisplay(20.06), 1e-9)
        assertEquals(20.0, MarksMath.roundForDisplay(20.04), 1e-9)
        assertTrue(MarksMath.roundForDisplay(Double.NaN).isNaN())
        assertEquals(Double.POSITIVE_INFINITY, MarksMath.roundForDisplay(Double.POSITIVE_INFINITY), 0.0)
    }

    // ─── Unidad ──────────────────────────────────────────────────────────────

    @Test
    fun `la unidad se normaliza y cualquier otra cosa es kg`() {
        assertEquals("kg", MarksMath.normalizeUnit("kg"))
        assertEquals("kg", MarksMath.normalizeUnit("KG"))
        assertEquals("lb", MarksMath.normalizeUnit("lb"))
        assertEquals("lb", MarksMath.normalizeUnit(" LB "))
        assertEquals("kg", MarksMath.normalizeUnit("stone"))
        assertEquals("kg", MarksMath.normalizeUnit(""))
        assertEquals("lb", MarksMath.otherUnit("kg"))
        assertEquals("kg", MarksMath.otherUnit("lb"))
    }

    // ─── Escalón y rango ─────────────────────────────────────────────────────

    @Test
    fun `snapMark en kg ajusta a 2,5 y satura en 20 y 400`() {
        assertEquals(142.5, MarksMath.snapMark(142.4, "kg"), eps)
        assertEquals(142.5, MarksMath.snapMark(142.6, "kg"), eps)
        assertEquals(145.0, MarksMath.snapMark(143.8, "kg"), eps)
        assertEquals(142.5, MarksMath.snapMark(143.7, "kg"), eps)
        assertEquals(20.0, MarksMath.snapMark(20.0, "kg"), eps)
        assertEquals(20.0, MarksMath.snapMark(19.0, "kg"), eps)
        assertEquals(20.0, MarksMath.snapMark(-50.0, "kg"), eps)
        assertEquals(22.5, MarksMath.snapMark(21.3, "kg"), eps)
        assertEquals(400.0, MarksMath.snapMark(400.0, "kg"), eps)
        assertEquals(400.0, MarksMath.snapMark(399.0, "kg"), eps)
        assertEquals(400.0, MarksMath.snapMark(450.0, "kg"), eps)
        assertEquals(397.5, MarksMath.snapMark(398.7, "kg"), eps)
    }

    @Test
    fun `snapMark en lb ajusta a 5 y satura en 44 y 880`() {
        assertEquals(45.0, MarksMath.snapMark(44.0, "lb"), eps)
        assertEquals(45.0, MarksMath.snapMark(30.0, "lb"), eps)
        assertEquals(45.0, MarksMath.snapMark(47.4, "lb"), eps)
        assertEquals(50.0, MarksMath.snapMark(47.5, "lb"), eps)
        assertEquals(315.0, MarksMath.snapMark(314.2, "lb"), eps)
        assertEquals(880.0, MarksMath.snapMark(880.0, "lb"), eps)
        assertEquals(880.0, MarksMath.snapMark(900.0, "lb"), eps)
        assertEquals(875.0, MarksMath.snapMark(877.0, "lb"), eps)
    }

    @Test
    fun `snapMark nunca se sale del rango y es monotono`() {
        for (unit in listOf("kg", "lb")) {
            var last = -1.0
            var v = -20.0
            while (v <= 1000.0) {
                val s = MarksMath.snapMark(v, unit)
                assertTrue("$unit $v -> $s fuera de rango", s >= MarksMath.minDisplay(unit) && s <= MarksMath.maxDisplay(unit))
                assertTrue("$unit $v no es monótono ($last -> $s)", s >= last)
                last = s
                v += 0.37
            }
        }
    }

    @Test
    fun `lo que no es un numero cae en el extremo de abajo y el infinito en el de arriba`() {
        assertEquals(20.0, MarksMath.snapMark(Double.NaN, "kg"), eps)
        assertEquals(45.0, MarksMath.snapMark(Double.NaN, "lb"), eps)
        assertEquals(44.0, MarksMath.clampDisplay(Double.NaN, "lb"), eps)
        assertEquals(400.0, MarksMath.clampDisplay(Double.POSITIVE_INFINITY, "kg"), eps)
        assertEquals(20.0, MarksMath.clampDisplay(Double.NEGATIVE_INFINITY, "kg"), eps)
        assertEquals(20.0, MarksMath.clampKg(Double.NaN), eps)
        assertEquals(20.0, MarksMath.clampKg(5.0), eps)
        assertEquals(400.0, MarksMath.clampKg(512.0), eps)
        assertEquals(142.5, MarksMath.clampKg(142.5), eps)
    }

    @Test
    fun `los extremos en kg y en lb son el mismo rango visto desde la otra unidad`() {
        assertEquals(44.09, MarksMath.kgToLb(MarksMath.MIN_KG), 0.01)
        assertEquals(881.85, MarksMath.kgToLb(MarksMath.MAX_KG), 0.01)
        assertTrue(MarksMath.MIN_LB <= MarksMath.kgToLb(MarksMath.MIN_KG))
        assertTrue(MarksMath.MAX_LB <= MarksMath.kgToLb(MarksMath.MAX_KG))
    }

    @Test
    fun `lo que se guarda en lb es el kg de la marca ajustada`() {
        assertEquals(MarksMath.lbToKg(315.0), MarksMath.snapKg(314.2, "lb"), eps)
        assertEquals(142.5, MarksMath.snapKg(142.4, "kg"), eps)
        // 142,5 kg son 314,16 lb: el escalón de 5 lb más cercano es 315 lb.
        assertEquals(MarksMath.lbToKg(315.0), MarksMath.snapFromKg(142.5, "lb"), eps)
        assertEquals(142.5, MarksMath.snapFromKg(142.4, "kg"), eps)
        assertEquals(400.0, MarksMath.snapFromKg(900.0, "kg"), eps)
        assertEquals(20.0, MarksMath.snapFromKg(1.0, "kg"), eps)
    }

    @Test
    fun `ajustar dos veces da lo mismo y lo guardado se muestra como la marca ajustada`() {
        for (unit in listOf("kg", "lb")) {
            var v = MarksMath.minDisplay(unit) - 10
            while (v <= MarksMath.maxDisplay(unit) + 10) {
                val once = MarksMath.snapMark(v, unit)
                assertEquals("$unit $v idempotente", once, MarksMath.snapMark(once, unit), eps)
                // Lo que se guarda (kg) vuelve a verse exactamente como la marca ajustada.
                assertEquals("$unit $v se muestra igual", once, MarksMath.displayValue(MarksMath.snapKg(v, unit), unit), 0.1 + 1e-9)
                v += 0.41
            }
        }
    }

    // ─── Texto ───────────────────────────────────────────────────────────────

    @Test
    fun `la marca se lee con coma decimal y unidad`() {
        assertEquals("142,5 kg", MarksMath.formatMark(142.5, "kg"))
        assertEquals("142 kg", MarksMath.formatMark(142.0, "kg"))
        assertEquals("20 kg", MarksMath.formatMark(20.0, "kg"))
        assertEquals("400 kg", MarksMath.formatMark(400.0, "kg"))
        assertEquals("315 lb", MarksMath.formatMark(MarksMath.lbToKg(315.0), "lb"))
        assertEquals("314,2 lb", MarksMath.formatMark(142.5, "lb"))
        assertEquals("880 lb", MarksMath.formatMark(MarksMath.lbToKg(880.0), "lb"))
        assertEquals("102,5 kg", MarksMath.formatMark(102.5, "KG"))
    }

    @Test
    fun `una unidad desconocida se muestra en kg y un valor no finito no revienta`() {
        assertEquals("100 kg", MarksMath.formatMark(100.0, "otra"))
        assertEquals("— kg", MarksMath.formatMark(Double.NaN, "kg"))
        assertEquals("— lb", MarksMath.formatMark(Double.POSITIVE_INFINITY, "lb"))
        assertEquals("—", MarksMath.formatNumber(Double.NaN))
    }

    @Test
    fun `los numeros llevan a lo sumo un decimal`() {
        assertEquals("0", MarksMath.formatNumber(0.0))
        assertEquals("2,5", MarksMath.formatNumber(2.5))
        assertEquals("2,5", MarksMath.formatNumber(2.4999))
        assertEquals("3", MarksMath.formatNumber(2.96))
        assertEquals("5", MarksMath.formatNumber(5.0))
    }

    // ─── Marcas de la regla ──────────────────────────────────────────────────

    @Test
    fun `larga cada 10, intermedia cada 5 y corta el resto`() {
        assertEquals(MarksMath.Tick.LONG, MarksMath.tickOf(20))
        assertEquals(MarksMath.Tick.LONG, MarksMath.tickOf(400))
        assertEquals(MarksMath.Tick.LONG, MarksMath.tickOf(880))
        assertEquals(MarksMath.Tick.MID, MarksMath.tickOf(25))
        assertEquals(MarksMath.Tick.MID, MarksMath.tickOf(45))
        assertEquals(MarksMath.Tick.SHORT, MarksMath.tickOf(21))
        assertEquals(MarksMath.Tick.SHORT, MarksMath.tickOf(44))
        assertEquals(MarksMath.Tick.SHORT, MarksMath.tickOf(879))
    }

    @Test
    fun `las marcas empiezan y terminan en los extremos del rango`() {
        assertEquals(20, MarksMath.firstTick("kg"))
        assertEquals(400, MarksMath.lastTick("kg"))
        assertEquals(44, MarksMath.firstTick("lb"))
        assertEquals(880, MarksMath.lastTick("lb"))
        val longs = (MarksMath.firstTick("kg")..MarksMath.lastTick("kg")).count { MarksMath.tickOf(it) == MarksMath.Tick.LONG }
        assertEquals(39, longs) // 20, 30, … 400
    }

    // ─── Posición ↔ valor ────────────────────────────────────────────────────

    @Test
    fun `posicion y valor son inversos y el extremo de abajo esta en cero`() {
        for (unit in listOf("kg", "lb")) {
            val px = 8.4
            assertEquals(0.0, MarksMath.offsetOf(MarksMath.minDisplay(unit), unit, px), eps)
            assertEquals(MarksMath.lengthPx(unit, px), MarksMath.offsetOf(MarksMath.maxDisplay(unit), unit, px), 1e-6)
            var v = MarksMath.minDisplay(unit)
            while (v <= MarksMath.maxDisplay(unit)) {
                val back = MarksMath.valueAt(MarksMath.offsetOf(v, unit, px), unit, px)
                assertEquals("$unit $v", v, back, 1e-9)
                v += 2.5
            }
        }
    }

    @Test
    fun `la posicion se satura y un ancho de marca invalido cae en el minimo`() {
        assertEquals(400.0, MarksMath.valueAt(1e9, "kg", 8.0), eps)
        assertEquals(20.0, MarksMath.valueAt(-5.0, "kg", 8.0), eps)
        assertEquals(20.0, MarksMath.valueAt(100.0, "kg", 0.0), eps)
        assertEquals(20.0, MarksMath.valueAt(100.0, "kg", -3.0), eps)
        assertEquals(20.0, MarksMath.valueAt(Double.NaN, "kg", 8.0), eps)
        assertEquals(0.0, MarksMath.offsetOf(5.0, "kg", 8.0), eps)
        assertEquals(MarksMath.lengthPx("kg", 8.0), MarksMath.offsetOf(999.0, "kg", 8.0), 1e-6)
    }

    @Test
    fun `una posicion de la regla en lb corresponde al mismo kg que su valor`() {
        // 315 lb bajo el cursor es la marca de 142,88 kg, tanto en lb como convertida desde kg.
        val px = 4.6
        val off = MarksMath.offsetOf(315.0, "lb", px)
        val lbValue = MarksMath.valueAt(off, "lb", px)
        assertEquals(315.0, lbValue, 1e-9)
        assertEquals(MarksMath.lbToKg(315.0), MarksMath.toKg(lbValue, "lb"), 1e-9)
    }
}
