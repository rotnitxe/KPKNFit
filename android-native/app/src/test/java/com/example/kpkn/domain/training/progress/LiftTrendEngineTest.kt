package com.example.kpkn.domain.training.progress

import com.example.kpkn.data.models.CompletedExercise
import com.example.kpkn.data.models.CompletedSet
import com.example.kpkn.data.models.WorkoutLog
import com.example.kpkn.domain.performance.StrengthModel
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class LiftTrendEngineTest {
    private val weekly = listOf("2026-01-05", "2026-01-12", "2026-01-19", "2026-01-26", "2026-02-02", "2026-02-09")

    private fun iso(day: String) = "${day}T10:00:00.000Z"
    private fun set(weight: Double, reps: Int = 1, skipped: Boolean = false, isWarmup: Boolean = false) =
        CompletedSet("s", weight = weight, reps = reps, skipped = skipped, isWarmup = isWarmup)
    private fun exercise(name: String, vararg sets: CompletedSet, configId: String? = null) =
        CompletedExercise(name, name, catalogConfigurationId = configId, sets = sets.toList())
    private fun log(id: String, day: String, vararg exercises: CompletedExercise) =
        WorkoutLog(id, "p", "s", "Sesión $id", iso(day), 60, exercises.toList())
    /** Una sesión por fecha con una serie de 1 repetición: e1RM = peso. */
    private fun sessions(days: List<String>, e1rms: List<Double>) = days.zip(e1rms).mapIndexed { i, (day, kg) ->
        log("l$i", day, exercise("Press banca", set(kg)))
    }
    private fun onlyTrend(logs: List<WorkoutLog>) = LiftTrendEngine.trends(logs).single()

    @Test
    fun tresSesionesNoBastanParaUnaTendencia() {
        val trend = onlyTrend(sessions(weekly.take(3), listOf(100.0, 101.0, 102.0)))
        assertEquals(LiftTrendStatus.NO_TREND, trend.status)
        assertEquals(3, trend.sessions)
        assertNull(trend.stalledWeeks)
    }

    @Test
    fun seisSesionesSubiendoSonUpConDeltaDeOchoKilos() {
        val trend = onlyTrend(sessions(weekly, listOf(100.0, 101.0, 102.0, 104.0, 106.0, 108.0)))
        assertEquals(LiftTrendStatus.UP, trend.status)
        assertEquals(8.0, trend.deltaKg!!, 1e-9)
        assertEquals(100.0, trend.firstE1rm!!, 1e-9)
        assertEquals(108.0, trend.currentE1rm!!, 1e-9)
        assertNull(trend.stalledWeeks)
    }

    @Test
    fun seisSesionesPlanasEnCincoSemanasSonStagnantConSemanasParadas() {
        val trend = onlyTrend(sessions(weekly, List(6) { 100.0 }))
        assertEquals(LiftTrendStatus.STAGNANT, trend.status)
        assertEquals(5, trend.stalledWeeks)
    }

    @Test
    fun veintiunDiasDeEstancamientoYaCuentanComoStagnant() {
        val days = listOf("2026-01-05", "2026-01-09", "2026-01-13", "2026-01-16", "2026-01-21", "2026-01-26")
        val trend = onlyTrend(sessions(days, List(6) { 100.0 }))
        assertEquals(LiftTrendStatus.STAGNANT, trend.status)
        assertEquals(3, trend.stalledWeeks)
    }

    @Test
    fun seisSesionesPlanasEnDiezDiasNoSonTendencia() {
        val days = listOf("2026-01-05", "2026-01-07", "2026-01-09", "2026-01-11", "2026-01-13", "2026-01-15")
        val trend = onlyTrend(sessions(days, List(6) { 100.0 }))
        assertEquals(LiftTrendStatus.NO_TREND, trend.status)
        assertNull(trend.stalledWeeks)
    }

    @Test
    fun bajadaSuperiorAl2Coma5PorcientoEsDown() {
        val trend = onlyTrend(sessions(weekly, listOf(100.0, 100.0, 100.0, 95.0, 95.0, 95.0)))
        assertEquals(LiftTrendStatus.DOWN, trend.status)
        assertEquals(-5.0, trend.deltaKg!!, 1e-9)
    }

    @Test
    fun bajadaDeDosPorcientoQuedaDentroDelUmbralYNoEsDown() {
        val trend = onlyTrend(sessions(weekly, listOf(100.0, 100.0, 100.0, 98.0, 98.0, 98.0)))
        assertEquals(LiftTrendStatus.STAGNANT, trend.status)
    }

    @Test
    fun elE1rmSaleDeStrengthModelYNoDelPesoBruto() {
        val series = set(100.0, reps = 5)
        val trend = onlyTrend(listOf(log("a", "2026-01-05", exercise("Press banca", series))))
        assertEquals(StrengthModel.estimate1RM(series), trend.points.single(), 1e-9)
        assertTrue(trend.points.single() > 100.0)
    }

    @Test
    fun seriesDeCalentamientoOmitidasYSesionesSinSerieDeTrabajoNoCuentan() {
        val logs = weekly.take(4).mapIndexed { i, day ->
            log("l$i", day, exercise("Press banca", set(100.0), set(150.0, isWarmup = true), set(200.0, skipped = true)))
        } + log("l9", "2026-02-02", exercise("Press banca", set(180.0, isWarmup = true), set(190.0, skipped = true)))
        val trend = onlyTrend(logs)
        assertEquals(listOf(100.0, 100.0, 100.0, 100.0), trend.points)
        assertEquals(4, trend.sessions)
    }

    @Test
    fun ejercicioSinSeriesDeTrabajoSaleSinPuntosNiDelta() {
        val trend = onlyTrend(listOf(log("a", "2026-01-05", exercise("Press banca", set(60.0, isWarmup = true)))))
        assertEquals(0, trend.sessions)
        assertTrue(trend.points.isEmpty())
        assertNull(trend.currentE1rm)
        assertNull(trend.firstE1rm)
        assertNull(trend.deltaKg)
        assertEquals(LiftTrendStatus.NO_TREND, trend.status)
    }

    @Test
    fun variasEntradasDelMismoEjercicioEnUnaSesionDanUnSoloPuntoConElMejor() {
        val logs = listOf(log("a", "2026-01-05", exercise("Press banca", set(90.0)), exercise("Press banca", set(100.0))))
        assertEquals(listOf(100.0), onlyTrend(logs).points)
    }

    @Test
    fun logConFechaIlegibleSeIgnora() {
        val logs = listOf(
            log("a", "2026-13-45", exercise("Press banca", set(100.0))),
            log("b", "2026-01-05", exercise("Press banca", set(90.0))),
        )
        assertEquals(listOf(90.0), onlyTrend(logs).points)
    }

    @Test
    fun agrupaPorIdentidadDeCatalogoYUsaElNombreDeLaSesionMasReciente() {
        val logs = listOf(
            log("a", "2026-01-05", exercise("Press plano", set(100.0), configId = "cfg-1")),
            log("b", "2026-01-12", exercise("Press banca", set(101.0), configId = "cfg-1")),
            log("c", "2026-01-19", exercise("Press banca", set(50.0), configId = "cfg-2")),
        )
        val trends = LiftTrendEngine.trends(logs)
        assertEquals(2, trends.size)
        val merged = trends.first { it.exerciseKey == "configuration:cfg-1" }
        assertEquals(listOf(100.0, 101.0), merged.points)
        assertEquals("Press banca", merged.exerciseName)
    }

    @Test
    fun ordenaPorSesionesDescendenteYLuegoPorNombre() {
        val logs = weekly.take(4).mapIndexed { i, day ->
            val extra = if (i < 2) listOf(exercise("Sentadilla", set(100.0)), exercise("Dominadas", set(50.0))) else emptyList()
            WorkoutLog("l$i", "p", "s", "Sesión $i", iso(day), 60, listOf(exercise("Press banca", set(100.0))) + extra)
        }
        assertEquals(listOf("Press banca", "Dominadas", "Sentadilla"), LiftTrendEngine.trends(logs).map { it.exerciseName })
    }
}
