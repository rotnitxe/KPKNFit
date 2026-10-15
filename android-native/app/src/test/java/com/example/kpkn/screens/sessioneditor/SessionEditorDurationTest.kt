package com.example.kpkn.screens.sessioneditor

import com.example.kpkn.data.models.CardioDetails
import com.example.kpkn.data.models.CardioType
import com.example.kpkn.data.models.Exercise
import com.example.kpkn.data.models.ExerciseSet
import com.example.kpkn.data.models.Session
import com.example.kpkn.data.models.WarmupExercise
import com.example.kpkn.domain.training.SessionDurationEstimator
import com.example.kpkn.screens.home.estimatedSessionMinutes
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

/** Una sola duración (SE12): la misma sesión dice los mismos minutos en el editor, en Inicio y en el programa. */
class SessionEditorDurationTest {

    /** Calentamiento, fuerza y cardio: la misma sesión que se ve en las tres pantallas. */
    private fun sesionCompleta() = Session(
        id = "pierna-a",
        name = "Pierna A",
        warmup = listOf(WarmupExercise(id = "movilidad", name = "Movilidad de cadera", duration = 120)),
        exercises = listOf(
            Exercise(
                id = "sentadilla",
                name = "Sentadilla trasera",
                sets = (1..4).map { ExerciseSet(id = "sentadilla-$it", targetReps = 6, targetRIR = 2) },
                restTime = 195,
            ),
            Exercise(
                id = "peso-muerto",
                name = "Peso muerto",
                sets = (1..3).map { ExerciseSet(id = "peso-muerto-$it", targetReps = 10) },
                restTime = 120,
            ),
            Exercise(
                id = "cinta",
                name = "Cinta de correr",
                cardioDetails = CardioDetails(type = CardioType.TREADMILL, targetDurationSeconds = 20 * 60),
            ),
        ),
    )

    @Test
    fun laMismaSesionDiceLosMismosMinutosEnElEditorEnInicioYEnElPrograma() {
        val sesion = sesionCompleta()
        // Lo que pinta el programa: `SessionDurationEstimator.estimate(session).totalMinutes` (DayView, PlanDetailsSummary).
        val programa = SessionDurationEstimator.estimate(sesion).totalMinutes

        assertEquals("Inicio", programa, estimatedSessionMinutes(sesion))
        assertEquals("resumen de la Auge del editor", programa, sessionEditorDurationMinutes(sesion))
        assertEquals(
            "cabecera y reglas del editor",
            programa,
            SessionDurationEstimator.estimate(sesion).toSessionTimeBreakdown(sesion).totalMinutes,
        )
    }

    @Test
    fun elDesgloseDelEditorSumaSuTotalEnMinutosEnteros() {
        val sesion = sesionCompleta()
        val desglose = SessionDurationEstimator.estimate(sesion).toSessionTimeBreakdown(sesion)

        assertEquals(0, desglose.totalSeconds % 60)
        assertEquals(
            desglose.totalSeconds,
            desglose.setupSeconds + desglose.executionSeconds + desglose.restSeconds + desglose.warmupSeconds,
        )
    }

    @Test
    fun unaSesionSinEjerciciosNoTieneDuracionQueMostrar() {
        val vacia = Session(id = "vacia", name = "Vacía")

        assertNull(estimatedSessionMinutes(vacia))
        assertEquals(0, SessionDurationEstimator.estimate(vacia).toSessionTimeBreakdown(vacia).totalSeconds)
    }
}
