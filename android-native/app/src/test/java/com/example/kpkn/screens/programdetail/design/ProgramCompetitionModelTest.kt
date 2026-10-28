package com.example.kpkn.screens.programdetail.design

import com.example.kpkn.data.models.CompetitionMovementType
import com.example.kpkn.data.models.CompetitionRecord
import com.example.kpkn.data.models.CompetitionRecordStatus
import com.example.kpkn.data.models.PlannedAttempt
import com.example.kpkn.screens.programdetail.ProgramCompetitionState
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

/** Los textos del módulo de competición de la portada (lienzo v6, p-comp). Sin Robolectric. */
class ProgramCompetitionModelTest {

    private fun state(
        days: Int?,
        status: CompetitionRecordStatus = CompetitionRecordStatus.PLANNED,
        target: Double? = null,
        rate: Double? = null,
    ) = ProgramCompetitionState(
        record = CompetitionRecord("m", "Open regional", eventDate = "2026-11-16", status = status, targetBodyweightKg = target),
        daysToMeet = days,
        forecast = null,
        resultSummary = null,
        expectedMaxes = emptyMap(),
        weeklyWeightRateKg = rate,
    )

    @Test
    fun laCuentaAtrasDiceLosDiasHoyOTerminada() {
        assertEquals(CompetitionCountdown("18", "días"), competitionCountdown(state(18)))
        assertEquals(CompetitionCountdown("1", "día"), competitionCountdown(state(1)))
        assertEquals(CompetitionCountdown("Hoy", null), competitionCountdown(state(0)))
        assertEquals(CompetitionCountdown("Terminada", null), competitionCountdown(state(-2)))
        assertEquals(CompetitionCountdown("Terminada", null), competitionCountdown(state(5, status = CompetitionRecordStatus.COMPLETED)))
        assertNull(competitionCountdown(state(null)))
    }

    @Test
    fun elTituloLlevaLaFechaOSinFecha() {
        assertEquals("Open regional · 16 nov", competitionTitleLine(CompetitionRecord("m", "Open regional", eventDate = "2026-11-16")))
        assertEquals("Open regional · sin fecha", competitionTitleLine(CompetitionRecord("m", "Open regional")))
    }

    @Test
    fun elPesajeLlevaLaTendenciaSiSeConoce() {
        assertEquals("Pesaje 82,5 kg · −0,2 kg/sem", competitionWeighInLine(state(18, target = 82.5, rate = -0.2)))
        assertEquals("Pesaje 83 kg", competitionWeighInLine(state(18, target = 83.0)))
        assertNull(competitionWeighInLine(state(18)))
    }

    @Test
    fun losIntentosSonLosPrimerosDeCadaMovimiento() {
        val attempts = listOf(
            PlannedAttempt(CompetitionMovementType.DEADLIFT, 1, 210.0),
            PlannedAttempt(CompetitionMovementType.SQUAT, 2, 192.5),
            PlannedAttempt(CompetitionMovementType.SQUAT, 1, 185.0),
            PlannedAttempt(CompetitionMovementType.BENCH, 1, 120.0),
        )
        assertEquals("185 · 120 · 210", competitionAttemptsLine(attempts))
        assertNull(competitionAttemptsLine(emptyList()))
    }
}
