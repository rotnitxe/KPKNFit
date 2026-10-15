package com.example.kpkn.screens.sessioneditor

import com.example.kpkn.data.models.CardioDetails
import com.example.kpkn.data.models.CardioType
import com.example.kpkn.data.models.Exercise
import com.example.kpkn.data.models.ExerciseSet
import com.example.kpkn.data.models.Session
import com.example.kpkn.data.models.WarmupExercise
import com.example.kpkn.domain.calculations.SessionTimeBreakdown
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

    /** ER2b: el asistente y Reglas › Tiempo dicen el mismo desglose, con el calentamiento, y las partes suman el total. */
    @Test
    fun elDesgloseDelAsistenteYDeReglasEsElMismoYSumaElTotal() {
        val breakdown = SessionTimeBreakdown(
            setupSeconds = 7 * 60,
            executionSeconds = 20 * 60,
            restSeconds = 26 * 60,
            warmupSeconds = 8 * 60,
            totalSeconds = 61 * 60,
            exerciseCount = 3,
            totalSetCount = 12,
        )
        assertEquals(
            "Preparación 7\u00A0min · Ejecución 20\u00A0min · Descansos 26\u00A0min · Calentamiento 8\u00A0min",
            sessionTimeBreakdownCaption(breakdown),
        )
    }

    /** Sin calentamiento, el desglose no lo nombra. */
    @Test
    fun elDesgloseSinCalentamientoNoLoNombra() {
        val breakdown = SessionTimeBreakdown(
            setupSeconds = 4 * 60,
            executionSeconds = 30 * 60,
            restSeconds = 20 * 60,
            warmupSeconds = 0,
            totalSeconds = 54 * 60,
            exerciseCount = 2,
            totalSetCount = 8,
        )
        assertEquals(
            "Preparación 4\u00A0min · Ejecución 30\u00A0min · Descansos 20\u00A0min",
            sessionTimeBreakdownCaption(breakdown),
        )
    }

    /** ER2b: los minutos del botón «Reglas» van con espacio inseparable y su unidad, no con apóstrofo. */
    @Test
    fun losMinutosDelBotonLlevanEspacioInseparableYMin() {
        assertEquals("60\u00A0min", sessionMinutesLabel(60))
    }
}
