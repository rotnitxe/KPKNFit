package com.example.kpkn.screens.sessioneditor

import com.example.kpkn.data.models.CardioBlockType
import com.example.kpkn.data.models.CardioDetails
import com.example.kpkn.data.models.CardioHiitConfig
import com.example.kpkn.data.models.CardioIntensity
import com.example.kpkn.data.models.CardioIntervalBlock
import com.example.kpkn.data.models.CardioType
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

/** Dosis corta del cardio (SE5): continuo por tiempo con zona, por distancia con ritmo, intervalos y HIIT, y sin datos no hay dosis. */
class SessionCardioDoseTest {

    private fun steady(
        durationSeconds: Int? = 1800,
        distanceKm: Double? = null,
        paceSecondsPerKm: Int? = null,
        hrPercent: Int? = null,
    ) = CardioDetails(
        type = CardioType.TREADMILL,
        intensity = CardioIntensity.BAJA,
        targetDurationSeconds = durationSeconds,
        targetDistanceKm = distanceKm,
        targetPaceSecondsPerKm = paceSecondsPerKm,
        targetHrPercent = hrPercent,
    )

    private fun hiit(work: Int, rest: Int, rounds: Int, warmup: Int = 0, cooldown: Int = 0) = CardioDetails(
        type = CardioType.BIKE_STATIONARY,
        hiit = CardioHiitConfig(
            warmupSeconds = warmup,
            workSeconds = work,
            restSeconds = rest,
            rounds = rounds,
            cooldownSeconds = cooldown,
        ),
    )

    @Test
    fun continuoPorTiempoConZona() {
        assertEquals("30 min · zona 2", SessionCardioDose.of(steady(durationSeconds = 1800, hrPercent = 65)))
    }

    @Test
    fun continuoPorTiempoSinZonaNoInventaUna() {
        assertEquals("20 min", SessionCardioDose.of(steady(durationSeconds = 1200)))
    }

    @Test
    fun porDistanciaConRitmoOmiteElTiempoPorDefecto() {
        // Con distancia, el tiempo por defecto del modelo no se lee.
        assertEquals("5 km · 5:30 /km", SessionCardioDose.of(steady(durationSeconds = 1200, distanceKm = 5.0, paceSecondsPerKm = 330)))
    }

    @Test
    fun distanciaFraccionadaUsaLaComaDecimal() {
        assertEquals("2,5 km", SessionCardioDose.of(steady(durationSeconds = null, distanceKm = 2.5)))
    }

    @Test
    fun hiitDaRondasTrabajoYPausa() {
        assertEquals("8 × 0:30 / 1:00", SessionCardioDose.of(hiit(work = 30, rest = 60, rounds = 8)))
    }

    @Test
    fun intervalosIrregularesCuentanBloquesYTotal() {
        val details = CardioDetails(
            type = CardioType.TREADMILL,
            intervalBlocks = listOf(
                CardioIntervalBlock(id = "a", type = CardioBlockType.WORK, durationSeconds = 60),
                CardioIntervalBlock(id = "b", type = CardioBlockType.RECOVER, durationSeconds = 30),
                CardioIntervalBlock(id = "c", type = CardioBlockType.WORK, durationSeconds = 90),
            ),
        )
        assertEquals("3 bloques · 3 min", SessionCardioDose.of(details))
    }

    @Test
    fun sinDatosNoHayDosis() {
        assertNull(SessionCardioDose.of(steady(durationSeconds = null)))
    }

    @Test
    fun zonaSaleDeLaFrecuenciaCardiacaPorBandas() {
        assertEquals(1, SessionCardioDose.zoneOf(55))
        assertEquals(2, SessionCardioDose.zoneOf(65))
        assertEquals(3, SessionCardioDose.zoneOf(75))
        assertEquals(4, SessionCardioDose.zoneOf(85))
        assertEquals(5, SessionCardioDose.zoneOf(95))
    }

    @Test
    fun lineaDeTiempoHiitTieneUnaBarraPorTramo() {
        // Calentamiento, 8 esfuerzos con 7 pausas entre ellos y vuelta a la calma: 1 + 15 + 1 tramos.
        val segments = SessionCardioDose.timelineOf(hiit(work = 30, rest = 60, rounds = 8, warmup = 180, cooldown = 120))
        assertEquals(17, segments.size)
        assertEquals(8, segments.count { it.work })
    }

    @Test
    fun cardioContinuoNoTieneLineaDeTiempo() {
        assertEquals(emptyList<CardioTimelineSegment>(), SessionCardioDose.timelineOf(steady()))
    }

    @Test
    fun formatosDeDuracionYRitmo() {
        assertEquals("1 min 30 s", SessionCardioDose.minutesText(90))
        assertEquals("1 h", SessionCardioDose.minutesText(3600))
        assertEquals("1 h 5 min", SessionCardioDose.minutesText(3900))
        assertEquals("45 s", SessionCardioDose.minutesText(45))
        assertEquals("0:30", SessionCardioDose.clock(30))
        assertEquals("1:30", SessionCardioDose.clock(90))
        assertEquals("5:30 /km", SessionCardioDose.paceText(330))
        assertEquals("5 km", SessionCardioDose.kmText(5.0))
    }
}
