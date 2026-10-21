package com.example.kpkn.screens.sessioneditor.components

import com.example.kpkn.data.models.CardioBlockType
import com.example.kpkn.data.models.CardioDetails
import com.example.kpkn.data.models.CardioIntervalBlock
import com.example.kpkn.data.models.CardioType
import com.example.kpkn.domain.cardio.CardioRepeatGrammar
import com.example.kpkn.domain.cardio.CardioUniformRepeat
import org.junit.Assert.assertEquals
import org.junit.Test

/** La línea de dosis del cardio en sus tres formas, con los ejemplos de la especificación. */
class SessionCardioEditorDoseLineTest {

    @Test
    fun continuoConZonaDeFcDiceTiempoYZona() {
        val details = CardioDetails(
            type = CardioType.TREADMILL,
            targetDurationSeconds = 20 * 60,
            intensityLevel = 5,
            targetHrPercent = 65,
        )
        assertEquals("20 min · Z2", cardioDoseLine(details))
    }

    @Test
    fun continuoConDistanciaYRitmoDiceDistanciaYRitmo() {
        val details = CardioDetails(
            type = CardioType.RUN_OUTDOOR,
            targetDurationSeconds = 20 * 60,
            targetDistanceKm = 5.0,
            targetPaceSecondsPerKm = 330,
        )
        assertEquals("5 km · 5:30/km", cardioDoseLine(details))
    }

    @Test
    fun continuoSinObjetivoDiceLibreYElRpe() {
        val details = CardioDetails(type = CardioType.TREADMILL, targetDurationSeconds = null, intensityLevel = 5)
        assertEquals("Libre · RPE 5", cardioDoseLine(details))
    }

    @Test
    fun seriesDiceRondasEsfuerzoPausaYRpe() {
        val details = CardioRepeatGrammar.applyUniform(
            CardioDetails(type = CardioType.TREADMILL),
            CardioUniformRepeat(
                warmupSeconds = 180,
                workSeconds = 30,
                restSeconds = 30,
                rounds = 8,
                cooldownSeconds = 180,
                targetRpe = 9.0,
            ),
        )
        assertEquals("8 × 30 s / 30 s · RPE 9", cardioDoseLine(details))
    }

    @Test
    fun seriesSinPausaNoPoneLaBarra() {
        val details = CardioRepeatGrammar.applyUniform(
            CardioDetails(type = CardioType.TREADMILL),
            CardioUniformRepeat(
                warmupSeconds = 0,
                workSeconds = 20,
                restSeconds = 0,
                rounds = 4,
                cooldownSeconds = 0,
                targetRpe = 8.0,
            ),
        )
        assertEquals("4 × 20 s · RPE 8", cardioDoseLine(details))
    }

    @Test
    fun intervalosIrregularesDicenLosBloquesYElTotal() {
        val blocks = listOf(
            CardioIntervalBlock(id = "1", type = CardioBlockType.WORK, durationSeconds = 180, speedKmh = 9.0),
            CardioIntervalBlock(id = "2", type = CardioBlockType.RECOVER, durationSeconds = 120, speedKmh = 5.0),
            CardioIntervalBlock(id = "3", type = CardioBlockType.WORK, durationSeconds = 90, speedKmh = 11.0),
            CardioIntervalBlock(id = "4", type = CardioBlockType.RECOVER, durationSeconds = 60, speedKmh = 5.0),
            CardioIntervalBlock(id = "5", type = CardioBlockType.WORK, durationSeconds = 240, speedKmh = 8.5),
        )
        val details = CardioDetails(type = CardioType.TREADMILL, intervalBlocks = blocks, intervalRounds = 1)
        assertEquals("5 bloques · 11 min 30 s", cardioDoseLine(details))
    }

    @Test
    fun zonaDeFcEnBandasDeCinco() {
        assertEquals(1, cardioZoneOf(59))
        assertEquals(2, cardioZoneOf(60))
        assertEquals(2, cardioZoneOf(69))
        assertEquals(3, cardioZoneOf(70))
        assertEquals(4, cardioZoneOf(89))
        assertEquals(5, cardioZoneOf(90))
    }
}
