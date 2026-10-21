package com.example.kpkn.screens.sessioneditor.components

import com.example.kpkn.data.models.CardioBlockType
import com.example.kpkn.data.models.CardioDetails
import com.example.kpkn.data.models.CardioIntervalBlock
import com.example.kpkn.data.models.CardioType
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

/** Cambiar de forma (Continuo · Series · Intervalos) conserva lo que la lógica del original conserva. */
class SessionCardioEditorFormSwitchTest {

    private fun cinta(duracion: Int? = 1200, distancia: Double? = null, gps: Boolean = false): CardioDetails = CardioDetails(
        type = CardioType.TREADMILL,
        targetDurationSeconds = duracion,
        targetDistanceKm = distancia,
        requiresGps = gps,
        intensityLevel = 6,
    )

    @Test
    fun deContinuoASeriesYDeVueltaConservaDistanciaGpsYDuracion() {
        val steady = cinta(distancia = 5.0, gps = true)

        val series = requireNotNull(switchCardioForm(steady, CardioForm.SERIES))
        assertEquals(CardioForm.SERIES, cardioFormOf(series))
        assertEquals(5.0, series.targetDistanceKm)
        assertTrue(series.requiresGps)

        val back = requireNotNull(switchCardioForm(series, CardioForm.CONTINUO))
        assertEquals(CardioForm.CONTINUO, cardioFormOf(back))
        assertEquals(5.0, back.targetDistanceKm)
        assertTrue(back.requiresGps)
        // La duración de vuelta es la de las series, como antes: no se pierde el tiempo programado.
        assertEquals(series.effectiveDurationSeconds(), back.targetDurationSeconds)
    }

    @Test
    fun deContinuoAIntervalosConservaDistanciaGpsYLaDuracionTotal() {
        val steady = cinta(duracion = 1200, distancia = 3.0, gps = true)

        val intervals = requireNotNull(switchCardioForm(steady, CardioForm.INTERVALOS))
        assertEquals(CardioForm.INTERVALOS, cardioFormOf(intervals))
        assertTrue(intervals.hasIntervals())
        assertEquals(3.0, intervals.targetDistanceKm)
        assertTrue(intervals.requiresGps)
        assertTrue(intervals.effectiveDurationSeconds() in 1190..1210)
    }

    @Test
    fun deIntervalosAContinuoConservaElTiempoProgramado() {
        val intervals = requireNotNull(switchCardioForm(cinta(duracion = 1200, gps = true), CardioForm.INTERVALOS))
        val steady = requireNotNull(switchCardioForm(intervals, CardioForm.CONTINUO))

        assertEquals(CardioForm.CONTINUO, cardioFormOf(steady))
        assertTrue(steady.requiresGps)
        assertEquals(intervals.effectiveDurationSeconds(), steady.targetDurationSeconds)
    }

    @Test
    fun elegirLaFormaEnLaQueYaSeEstaNoCambiaNada() {
        assertNull(switchCardioForm(cinta(), CardioForm.CONTINUO))
        val series = requireNotNull(switchCardioForm(cinta(), CardioForm.SERIES))
        assertNull(switchCardioForm(series, CardioForm.SERIES))
    }

    @Test
    fun duplicarBloqueLoInsertaDespuesConOtroIdYRecalculaElTotal() {
        val blocks = listOf(
            CardioIntervalBlock(id = "a", type = CardioBlockType.WORK, durationSeconds = 60, speedKmh = 9.0),
            CardioIntervalBlock(id = "b", type = CardioBlockType.RECOVER, durationSeconds = 60, speedKmh = 5.0),
            CardioIntervalBlock(id = "c", type = CardioBlockType.WORK, durationSeconds = 90, speedKmh = 10.0),
        )
        val details = CardioDetails(type = CardioType.TREADMILL, intervalBlocks = blocks, intervalRounds = 2, targetDurationSeconds = 420)

        val result = cardioDetailsWithDuplicatedBlock(details, index = 1, newId = { "copia" })

        assertEquals(4, result.intervalBlocks.size)
        assertEquals("b", result.intervalBlocks[1].id)
        assertEquals("copia", result.intervalBlocks[2].id)
        assertEquals(CardioBlockType.RECOVER, result.intervalBlocks[2].type)
        assertEquals(5.0, result.intervalBlocks[2].speedKmh)
        assertEquals("c", result.intervalBlocks[3].id)
        assertEquals((60 + 60 + 60 + 90) * 2, result.targetDurationSeconds)
    }

    @Test
    fun duplicarUnIndiceInexistenteNoCambiaElCardio() {
        val blocks = listOf(CardioIntervalBlock(id = "a", type = CardioBlockType.WORK, durationSeconds = 60))
        val details = CardioDetails(type = CardioType.TREADMILL, intervalBlocks = blocks)

        assertEquals(details, cardioDetailsWithDuplicatedBlock(details, index = 5, newId = { "x" }))
    }
}
