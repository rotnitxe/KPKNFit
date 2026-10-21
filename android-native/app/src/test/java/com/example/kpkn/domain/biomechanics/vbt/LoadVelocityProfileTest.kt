package com.example.kpkn.domain.biomechanics.vbt

import com.example.kpkn.domain.biomechanics.profiles.AnalysisProfileId
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import kotlin.math.abs

/** Perfil carga-velocidad y 1RM por velocidad (§7): el caso limpio y un caso para cada código de `NotEnough`. */
class LoadVelocityProfileTest {
    private val day = 86_400_000L

    private fun pts(vararg lv: Pair<Double, Double>, at: Long = 10 * day): List<LoadVelocityPoint> =
        lv.map { LoadVelocityPoint(it.first, it.second, at) }

    private fun notEnough(result: LoadVelocityResult): String {
        assertTrue("debería ser NotEnough: $result", result is LoadVelocityResult.NotEnough)
        return (result as LoadVelocityResult.NotEnough).reason
    }

    @Test
    fun aCleanProfileOfFourLoadsGivesTheOneRepMaxRoundedToTwoAndAHalfKilos() {
        // v = 1,3 − 0,0085·carga: 0,79, 0,62, 0,45 y 0,28 m/s con 60, 80, 100 y 120 kg. Con el MVT de 0,17 m/s: (0,17 − 1,3)/(−0,0085) = 132,94 → 132,5 kg.
        val points = pts(60.0 to 0.79, 80.0 to 0.62, 100.0 to 0.45, 120.0 to 0.28)
        val result = LoadVelocityProfile.estimate(points, 0.17)
        println("[carga-velocidad] limpio: $result")
        assertTrue("debería haber estimación: $result", result is LoadVelocityResult.Estimate)
        val e = result as LoadVelocityResult.Estimate
        assertEquals(132.5, e.e1rmKg, 1e-9)
        assertEquals(-0.0085, e.slope, 1e-9)
        assertEquals(1.3, e.intercept, 1e-9)
        assertEquals(1.0, e.r2, 1e-9)
        assertEquals(4, e.points)
    }

    @Test
    fun threePerfectPointsAlsoGiveAnEstimate() {
        val result = LoadVelocityProfile.estimate(pts(60.0 to 0.79, 80.0 to 0.62, 100.0 to 0.45), 0.17)
        println("[carga-velocidad] tres puntos: $result")
        assertTrue("$result", result is LoadVelocityResult.Estimate && (result as LoadVelocityResult.Estimate).points == 3)
    }

    @Test
    fun fewLoads() {
        // Dos cargas distintas (60 kg dos veces y 100 kg), o dos puntos.
        assertEquals("few_loads", notEnough(LoadVelocityProfile.estimate(pts(60.0 to 0.80, 60.0 to 0.78, 100.0 to 0.45), 0.17)))
        assertEquals("few_loads", notEnough(LoadVelocityProfile.estimate(pts(60.0 to 0.80, 100.0 to 0.45), 0.17)))
        assertEquals("few_loads", notEnough(LoadVelocityProfile.estimate(emptyList(), 0.17)))
        // Cargas a menos de 0,5 kg cuentan como la misma.
        assertEquals("few_loads", notEnough(LoadVelocityProfile.estimate(pts(100.0 to 0.50, 100.2 to 0.49, 100.4 to 0.51, 140.0 to 0.20), 0.17)))
    }

    @Test
    fun narrowRange() {
        // Un rango de carga del 9 % de la mayor (menos del 25 %).
        assertEquals("narrow_range", notEnough(LoadVelocityProfile.estimate(pts(100.0 to 0.80, 105.0 to 0.55, 110.0 to 0.30), 0.17)))
    }

    @Test
    fun lowVelocitySpread() {
        assertEquals("low_velocity_spread", notEnough(LoadVelocityProfile.estimate(pts(60.0 to 0.62, 80.0 to 0.60, 100.0 to 0.58), 0.17)))
    }

    @Test
    fun heaviestTooFast() {
        // El punto más pesado va a 0,65 m/s (más de 0,60).
        val points = pts(40.0 to 1.00, 60.0 to 0.80, 80.0 to 0.65)
        assertEquals("heaviest_too_fast", notEnough(LoadVelocityProfile.estimate(points, 0.17)))
        // En la banca el tope es 0,50: con 0,55 m/s en el más pesado no vale, y en otro ejercicio sí pasa ese corte.
        val bench = pts(40.0 to 1.00, 60.0 to 0.75, 80.0 to 0.55)
        assertEquals(0.50, LoadVelocityProfile.heaviestMaxMps(AnalysisProfileId.BENCH), 1e-9)
        assertEquals(0.60, LoadVelocityProfile.heaviestMaxMps(AnalysisProfileId.SQUAT), 1e-9)
        assertEquals("heaviest_too_fast", notEnough(LoadVelocityProfile.estimate(bench, 0.17, LoadVelocityProfile.heaviestMaxMps(AnalysisProfileId.BENCH))))
        assertTrue("fuera de la banca ese punto sí pasa: ${LoadVelocityProfile.estimate(bench, 0.17)}", LoadVelocityProfile.estimate(bench, 0.17) !is LoadVelocityResult.NotEnough || (LoadVelocityProfile.estimate(bench, 0.17) as LoadVelocityResult.NotEnough).reason != "heaviest_too_fast")
    }

    @Test
    fun positiveSlope() {
        // Más carga y más rápido: un perfil al revés.
        assertEquals("positive_slope", notEnough(LoadVelocityProfile.estimate(pts(60.0 to 0.20, 80.0 to 0.50, 100.0 to 0.58), 0.17)))
    }

    @Test
    fun lowR2() {
        // Cuatro puntos con R² = 0,67 (menos de 0,90).
        val result = LoadVelocityProfile.estimate(pts(60.0 to 0.80, 80.0 to 0.45, 100.0 to 0.60, 120.0 to 0.30), 0.17)
        assertEquals("low_r2", notEnough(result))
        // Con tres puntos se exige 0,95: R² = 0,92 no basta (con cuatro o más sí bastaría: el listón es 0,90).
        val three = LoadVelocityProfile.estimate(pts(60.0 to 0.80, 80.0 to 0.50, 100.0 to 0.40), 0.17)
        println("[carga-velocidad] tres puntos con R² < 0,95: $three")
        assertEquals("low_r2", notEnough(three))
    }

    @Test
    fun implausible() {
        // El 1RM sale por debajo de la carga mayor (la MCV del punto más pesado es de 0,20 y el MVT, 0,30).
        assertEquals("implausible", notEnough(LoadVelocityProfile.estimate(pts(60.0 to 0.90, 80.0 to 0.55, 100.0 to 0.20), 0.30)))
        // El 1RM sale por encima de 1,6 veces la carga mayor: 30, 60 y 100 kg con una recta muy tendida (extrapola a ≈ 200 kg).
        fun v(load: Double) = 0.90 - 0.0044 * (load - 30.0)
        val flat = pts(30.0 to v(30.0), 60.0 to v(60.0), 100.0 to v(100.0))
        assertEquals("implausible", notEnough(LoadVelocityProfile.estimate(flat, 0.17)))
        // Un MVT que no es un número.
        assertEquals("implausible", notEnough(LoadVelocityProfile.estimate(pts(60.0 to 0.79, 80.0 to 0.62, 100.0 to 0.45), Double.NaN)))
    }

    @Test
    fun wideInterval() {
        // R² = 0,98 y un 1RM de 132,5 kg, pero con 4 puntos el intervalo de predicción del 95 % es de ±23 % (más de ±10 %).
        val result = LoadVelocityProfile.estimate(pts(60.0 to 0.80, 80.0 to 0.60, 100.0 to 0.50, 120.0 to 0.25), 0.17)
        assertEquals("wide_interval", notEnough(result))
        // Con ruido pequeño (R² = 0,998) el intervalo es de ±7 % y sí hay 1RM.
        val ok = LoadVelocityProfile.estimate(pts(60.0 to 0.78, 80.0 to 0.64, 100.0 to 0.46, 120.0 to 0.30), 0.17)
        println("[carga-velocidad] con ruido pequeño: $ok")
        assertTrue("$ok", ok is LoadVelocityResult.Estimate && abs((ok as LoadVelocityResult.Estimate).e1rmKg - 137.5) < 1e-9)
    }

    @Test
    fun pointsOlderThanFourteenDaysAreIgnored() {
        val now = 100L * day
        val recent = listOf(
            LoadVelocityPoint(60.0, 0.79, now),
            LoadVelocityPoint(80.0, 0.62, now - 2 * day),
            LoadVelocityPoint(100.0, 0.45, now - 10 * day),
        )
        // Un punto de hace 30 días no entra: sigue habiendo 3 puntos buenos.
        val withOld = recent + LoadVelocityPoint(120.0, 0.10, now - 30 * day)
        val a = LoadVelocityProfile.estimate(withOld, 0.17)
        assertTrue("$a", a is LoadVelocityResult.Estimate && (a as LoadVelocityResult.Estimate).points == 3)
        // Con solo dos puntos recientes y dos viejos, faltan cargas.
        val stale = listOf(
            LoadVelocityPoint(60.0, 0.79, now),
            LoadVelocityPoint(80.0, 0.62, now - 3 * day),
            LoadVelocityPoint(100.0, 0.45, now - 40 * day),
            LoadVelocityPoint(120.0, 0.28, now - 45 * day),
        )
        assertEquals("few_loads", notEnough(LoadVelocityProfile.estimate(stale, 0.17)))
    }

    @Test
    fun invalidPointsAreDiscarded() {
        val points = pts(60.0 to 0.79, 80.0 to 0.62, 100.0 to 0.45, 0.0 to 1.0, -5.0 to 0.9, 70.0 to Double.NaN, 90.0 to -0.2)
        val result = LoadVelocityProfile.estimate(points, 0.17)
        assertTrue("$result", result is LoadVelocityResult.Estimate && (result as LoadVelocityResult.Estimate).points == 3)
    }
}
