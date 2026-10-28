package com.example.kpkn.screens.programdetail.design

import com.example.kpkn.data.models.CardioDetails
import com.example.kpkn.data.models.CardioType
import com.example.kpkn.data.models.Exercise
import com.example.kpkn.data.models.ExerciseMuscleInfo
import com.example.kpkn.data.models.InvolvedMuscle
import com.example.kpkn.data.models.Session
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

/** La agenda de la pestaña Semana (P2): los focos de una sesión, el plegado de los descansos y el total de una jornada. Sin Robolectric. */
class ProgramAgendaModelTest {

    /** Un ejercicio de prueba; con [cardio] lleva una ficha de cardio. */
    private fun exercise(id: String, cardio: Boolean = false): Exercise = Exercise(
        id = id,
        name = id,
        cardioDetails = if (cardio) CardioDetails(type = CardioType.TREADMILL) else null,
    )

    /** La ficha de un ejercicio cuyo músculo principal es [muscle], con su énfasis si lo tiene. */
    private fun info(muscle: String, emphasis: String? = null): ExerciseMuscleInfo = ExerciseMuscleInfo(
        id = muscle,
        name = muscle,
        involvedMuscles = listOf(InvolvedMuscle(muscle = muscle, emphasis = emphasis)),
    )

    /** Un día sin sesiones de la semana de prueba. */
    private fun restDay(id: Int, outside: Boolean = false, dropTarget: Boolean = false): ProgramDayUi = ProgramDayUi(
        id = id,
        name = "Día $id",
        short = "D$id",
        dateNumber = null,
        sessions = emptyList(),
        isToday = false,
        isStartDay = false,
        isOutsideProgram = outside,
        isDropTarget = dropTarget,
        dateEditable = false,
    )

    /** Un día con una sesión. */
    private fun sessionDay(id: Int): ProgramDayUi = restDay(id).copy(
        sessions = listOf(
            ProgramSessionUi(
                session = Session(id = "s$id", name = "Sesión $id"),
                exerciseCount = 0,
                minutes = 0,
                done = false,
                customized = false,
                restorable = false,
                optionalDateIso = null,
                optionalConfirmed = false,
            ),
        ),
    )

    @Test
    fun losFocosSalenDeLaFrecuenciaYNoPasanDeTres() {
        val exercises = listOf(exercise("a"), exercise("b"), exercise("c"), exercise("d"))
        val muscles = mapOf("a" to "Cuádriceps", "b" to "Cuádriceps", "c" to "Cuádriceps", "d" to "Abdomen")
        val focus = sessionFocus(exercises) { e -> info(muscles.getValue(e.id)) }
        assertEquals(listOf(SessionFocus.LEG, SessionFocus.LEG, SessionFocus.CORE), focus)
    }

    @Test
    fun dosGruposDanUnPuntoCadaUno() {
        val exercises = listOf(exercise("press"), exercise("remo"))
        val muscles = mapOf("press" to "Pectorales", "remo" to "Dorsales")
        assertEquals(
            listOf(SessionFocus.PUSH, SessionFocus.PULL),
            sessionFocus(exercises) { e -> info(muscles.getValue(e.id)) },
        )
    }

    @Test
    fun sinEjerciciosNoHayFocos() {
        assertEquals(emptyList<SessionFocus>(), sessionFocus(emptyList()) { null })
    }

    @Test
    fun elCardioEsSuPropioFoco() {
        assertEquals(listOf(SessionFocus.CARDIO), sessionFocus(listOf(exercise("bici", cardio = true))) { null })
    }

    @Test
    fun losDeltoidesPosterioresTiranYLosDemasEmpujan() {
        assertEquals(SessionFocus.PULL, focusOfCanonical("Deltoides", "posterior"))
        assertEquals(SessionFocus.PUSH, focusOfCanonical("Deltoides", null))
    }

    @Test
    fun tresDescansosSeguidosSePliegaNEnUnaFila() {
        val rows = agendaRows(listOf(restDay(1), restDay(2), restDay(3)), openRestRuns = emptySet(), dragging = false)
        assertEquals(1, rows.size)
        val run = rows.single() as AgendaRow.RestRun
        assertEquals(3, run.days.size)
    }

    @Test
    fun conLaFilaAbiertaSeVenLosTresDias() {
        val days = listOf(restDay(1), restDay(2), restDay(3))
        val rows = agendaRows(days, openRestRuns = setOf(1), dragging = false)
        assertEquals(3, rows.size)
        assertTrue(rows.all { it is AgendaRow.Day })
    }

    @Test
    fun mientrasSeArrastraNoSePliega() {
        val days = listOf(restDay(1), restDay(2), restDay(3))
        val rows = agendaRows(days, openRestRuns = emptySet(), dragging = true)
        assertEquals(3, rows.size)
        assertTrue(rows.all { it is AgendaRow.Day })
    }

    @Test
    fun unDescansoSueltoNoSePliega() {
        val rows = agendaRows(listOf(sessionDay(1), restDay(2), sessionDay(3)), openRestRuns = emptySet(), dragging = false)
        assertEquals(3, rows.size)
        assertTrue(rows.all { it is AgendaRow.Day })
    }

    @Test
    fun losDiasFueraDeProgramaNoSePliegan() {
        val rows = agendaRows(listOf(restDay(1, outside = true), restDay(2, outside = true)), openRestRuns = emptySet(), dragging = false)
        assertEquals(2, rows.size)
        assertTrue(rows.all { it is AgendaRow.Day })
    }

    @Test
    fun elTotalDeLaJornadaSeLeeEnHorasYMinutos() {
        assertEquals("≈ 45 min", dayTotalLabel(45))
        assertEquals("≈ 1 h 20 min", dayTotalLabel(80))
        assertEquals("≈ 2 h", dayTotalLabel(120))
    }
}
