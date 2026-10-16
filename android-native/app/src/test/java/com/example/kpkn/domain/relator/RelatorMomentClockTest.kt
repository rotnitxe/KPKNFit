package com.example.kpkn.domain.relator

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

/** Reloj de momentos del relator (LR1g): cuándo habla y cuántas veces cambia el mensaje. */
class RelatorMomentClockTest {

    /** Descanso de [plannedSeconds] con un tic por segundo y sin más eventos, tras registrar la serie 1. */
    private fun restMoments(plannedSeconds: Int): List<Pair<Int, RelatorMoment>> {
        val clock = RelatorMomentClock()
        clock.step(RelatorMomentInput(nowMs = 0L, setKey = "S1", exerciseId = "E", resting = false))
        val out = mutableListOf<Pair<Int, RelatorMoment>>()
        for (t in 0 until plannedSeconds) {
            val input = RelatorMomentInput(
                nowMs = t * 1_000L,
                setKey = "S2",
                exerciseId = "E",
                resting = true,
                loggedKey = if (t == 0) "S1" else null,
                restRemainingSeconds = plannedSeconds - t,
                restPlannedSeconds = plannedSeconds,
            )
            clock.step(input)?.let { out += t to it }
        }
        return out
    }

    @Test
    fun descanso_de_195_s_sin_eventos_da_como_mucho_tres_momentos_en_orden() {
        val moments = restMoments(plannedSeconds = 195)
        assertEquals(
            listOf(RelatorMoment.SET_LOGGED, RelatorMoment.REST_MID, RelatorMoment.REST_PREP),
            moments.map { it.second },
        )
        assertEquals(listOf(0, 98, 180), moments.map { it.first })
    }

    @Test
    fun serie_de_180_s_sin_eventos_da_como_mucho_dos_momentos() {
        val clock = RelatorMomentClock()
        val moments = mutableListOf<Pair<Int, RelatorMoment>>()
        for (t in 0..180) {
            clock.step(RelatorMomentInput(nowMs = t * 1_000L, setKey = "S1", exerciseId = "E", resting = false))
                ?.let { moments += t to it }
        }
        assertTrue("momentos: $moments", moments.size <= 2)
        assertEquals(RelatorMoment.IDLE_NUDGE, moments.last().second)
        assertEquals(120, moments.last().first)
    }

    @Test
    fun una_edicion_cambia_el_mensaje_en_1_5_s() {
        assertEquals(listOf(1_500L), editMomentTimes(editsAtMs = listOf(0L)))
    }

    @Test
    fun dos_ediciones_en_menos_de_un_segundo_dan_un_solo_cambio() {
        assertEquals(listOf(2_300L), editMomentTimes(editsAtMs = listOf(0L, 800L)))
    }

    @Test
    fun un_descanso_que_avisa_antes_de_12_s_espera_a_cumplirlos() {
        val clock = RelatorMomentClock()
        clock.step(RelatorMomentInput(nowMs = 0L, setKey = "S1", exerciseId = "E", resting = false))
        val out = mutableListOf<Pair<Long, RelatorMoment>>()
        var ms = 0L
        while (ms <= 20_000L) {
            val remaining = ((20_000L - ms) / 1_000L).toInt()
            val input = RelatorMomentInput(
                nowMs = ms,
                setKey = "S2",
                exerciseId = "E",
                resting = true,
                loggedKey = if (ms == 0L) "S1" else null,
                restRemainingSeconds = remaining,
                restPlannedSeconds = 20,
            )
            clock.step(input)?.let { out += ms to it }
            ms += 500L
        }
        // REST_PREP se detecta a los 5 s (quedan 15), pero el mensaje de la reacción lleva 0 s: espera a los 12 s.
        assertEquals(
            listOf(0L to RelatorMoment.SET_LOGGED, 12_000L to RelatorMoment.REST_PREP),
            out,
        )
    }

    /** Ediciones de la serie actual en [editsAtMs]; devuelve los instantes en que el reloj dispara USER_EDIT. */
    private fun editMomentTimes(editsAtMs: List<Long>): List<Long> {
        val clock = RelatorMomentClock()
        clock.step(RelatorMomentInput(nowMs = 0L, setKey = "S1", exerciseId = "E", resting = false))
        val out = mutableListOf<Long>()
        var revision = 0L
        var ms = 0L
        while (ms <= 3_000L) {
            if (ms in editsAtMs) revision += 1L
            val moment = clock.step(
                RelatorMomentInput(
                    nowMs = ms,
                    setKey = "S1",
                    exerciseId = "E",
                    resting = false,
                    editRevision = revision,
                ),
            )
            if (moment == RelatorMoment.USER_EDIT) out += ms
            ms += 100L
        }
        return out
    }
}
