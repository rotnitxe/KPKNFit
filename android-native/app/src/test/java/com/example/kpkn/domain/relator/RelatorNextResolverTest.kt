package com.example.kpkn.domain.relator

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

/** «Siguiente» del descanso (LR1g, prueba 5): la serie que toca del mismo ejercicio, o el ejercicio siguiente. */
class RelatorNextResolverTest {

    @Test
    fun tras_la_serie_1_de_4_toca_la_serie_2_del_mismo_ejercicio() {
        val next = RelatorNextResolver.resolve(
            upcomingIndex = 1,
            setsTotal = 4,
            setKg = 95.0,
            setReps = 6,
            nextExerciseName = "Peso muerto rumano",
            nextExerciseKg = 71.0,
        )
        assertEquals(2, next.setNumber)
        assertEquals(4, next.setTotal)
        assertEquals(95.0, next.kg!!, 1e-9)
        assertEquals(6, next.reps)
        assertNull(next.exerciseName)
    }

    @Test
    fun tras_la_serie_4_de_4_toca_el_ejercicio_siguiente() {
        val next = RelatorNextResolver.resolve(
            upcomingIndex = 4,
            setsTotal = 4,
            setKg = 95.0,
            setReps = 6,
            nextExerciseName = "Peso muerto rumano",
            nextExerciseKg = 71.0,
        )
        assertNull(next.setNumber)
        assertEquals("Peso muerto rumano", next.exerciseName)
        assertEquals(71.0, next.kg!!, 1e-9)
    }

    @Test
    fun en_superserie_toca_la_pareja() {
        val next = RelatorNextResolver.resolve(
            upcomingIndex = 1,
            setsTotal = 4,
            setKg = 95.0,
            setReps = 6,
            nextExerciseName = "Peso muerto rumano",
            nextExerciseKg = 71.0,
            supersetPartnerName = "Remo con barra",
            supersetPartnerKg = 60.0,
        )
        assertNull(next.setNumber)
        assertEquals("Remo con barra", next.exerciseName)
        assertEquals(60.0, next.kg!!, 1e-9)
    }
}
