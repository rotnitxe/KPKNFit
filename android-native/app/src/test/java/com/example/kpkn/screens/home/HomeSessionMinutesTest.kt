package com.example.kpkn.screens.home

import com.example.kpkn.data.models.CardioDetails
import com.example.kpkn.data.models.CardioType
import com.example.kpkn.data.models.Exercise
import com.example.kpkn.data.models.ExerciseSet
import com.example.kpkn.data.models.Session
import com.example.kpkn.domain.training.SessionDurationEstimator
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

/** Inicio dice los minutos del estimador común (SE12), con el cardio dentro. */
class HomeSessionMinutesTest {

    private fun fuerza() = listOf(
        Exercise(
            id = "sentadilla",
            name = "Sentadilla",
            sets = (1..4).map { ExerciseSet(id = "sentadilla-$it", targetReps = 6) },
            restTime = 180,
        ),
    )

    @Test
    fun inicioDiceLosMinutosDelEstimadorComun() {
        val sesion = Session(id = "fuerza", name = "Fuerza", exercises = fuerza())

        assertEquals(SessionDurationEstimator.estimate(sesion).totalMinutes, estimatedSessionMinutes(sesion))
    }

    @Test
    fun elCardioEntraEnLosMinutosDeInicio() {
        val sinCardio = Session(id = "fuerza", name = "Fuerza", exercises = fuerza())
        val conCardio = sinCardio.copy(
            exercises = fuerza() + Exercise(
                id = "cinta",
                name = "Cinta de correr",
                cardioDetails = CardioDetails(type = CardioType.TREADMILL, targetDurationSeconds = 20 * 60),
            ),
        )

        assertTrue(estimatedSessionMinutes(conCardio)!! >= estimatedSessionMinutes(sinCardio)!! + 20)
    }

    @Test
    fun unaSesionVaciaNoMuestraMinutos() {
        assertNull(estimatedSessionMinutes(Session(id = "vacia", name = "Vacía")))
    }
}
