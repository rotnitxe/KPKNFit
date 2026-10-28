package com.example.kpkn.screens.programdetail.design

import com.example.kpkn.data.models.CardioDetails
import com.example.kpkn.data.models.CardioType
import com.example.kpkn.data.models.Exercise
import com.example.kpkn.data.models.ExerciseMuscleInfo
import com.example.kpkn.data.models.ExerciseSet
import com.example.kpkn.data.models.InvolvedMuscle
import com.example.kpkn.data.models.LoadModeV2
import com.example.kpkn.data.models.NativeProgressionIdentity
import com.example.kpkn.data.models.NativeProgressionProposal
import com.example.kpkn.data.models.NativeProgressionProposalKind
import com.example.kpkn.data.models.Session
import com.example.kpkn.data.models.UnitModeV2
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * La agenda de la pestaña Semana (lienzo v6): los focos de una sesión, el plegado de los descansos, los días fuera del programa, el total
 * de una jornada y la cápsula de propuesta. Sin Robolectric.
 */
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
        val rows = agendaRows(listOf(restDay(1), restDay(2), restDay(3)), dragging = false)
        assertEquals(1, rows.size)
        val run = rows.single() as AgendaRow.RestRun
        assertEquals(3, run.days.size)
    }

    @Test
    fun mientrasSeArrastraNoSePliega() {
        val days = listOf(restDay(1), restDay(2), restDay(3))
        val rows = agendaRows(days, dragging = true)
        assertEquals(3, rows.size)
        assertTrue(rows.all { it is AgendaRow.Day })
    }

    @Test
    fun unDescansoSueltoNoSePliega() {
        val rows = agendaRows(listOf(sessionDay(1), restDay(2), sessionDay(3)), dragging = false)
        assertEquals(3, rows.size)
        assertTrue(rows.all { it is AgendaRow.Day })
    }

    @Test
    fun losDiasFueraDeProgramaSinSesionesNoSalen() {
        val rows = agendaRows(listOf(restDay(1, outside = true), restDay(2, outside = true), sessionDay(3)), dragging = false)
        assertEquals(listOf(3), rows.map { (it as AgendaRow.Day).day.id })
    }

    @Test
    fun unDiaFueraDeProgramaConSesionSeQueda() {
        val outsideWithSession = sessionDay(1).copy(isOutsideProgram = true)
        val rows = agendaRows(listOf(outsideWithSession, sessionDay(2)), dragging = false)
        assertEquals(listOf(1, 2), rows.map { (it as AgendaRow.Day).day.id })
    }

    @Test
    fun losDescansosDentroDeUnTramoSePlieganAunqueHayaDiasFuera() {
        val rows = agendaRows(listOf(restDay(1, outside = true), restDay(2), restDay(3), sessionDay(4)), dragging = false)
        assertEquals(2, rows.size)
        assertEquals(listOf(2, 3), (rows.first() as AgendaRow.RestRun).days.map { it.id })
    }

    @Test
    fun elTotalDeLaJornadaSeLeeEnHorasYMinutos() {
        assertEquals("≈ 45 min", dayTotalLabel(45))
        assertEquals("≈ 1 h 20 min", dayTotalLabel(80))
        assertEquals("≈ 2 h", dayTotalLabel(120))
    }

    /** Un ejercicio con su configuración del catálogo y una serie por carga de [loads]. */
    private fun loaded(config: String, vararg loads: Double): Exercise = Exercise(
        id = config,
        name = config,
        catalogConfigurationId = config,
        sets = loads.mapIndexed { index, load -> ExerciseSet(id = "$config-$index", targetReps = 5, weight = load) },
    )

    /** Una propuesta pendiente para la configuración [config], con su carga objetivo. */
    private fun proposal(config: String, target: Double?): NativeProgressionProposal = NativeProgressionProposal(
        proposalId = "p-$config",
        kind = NativeProgressionProposalKind.INCREASE_LOAD,
        identity = NativeProgressionIdentity(
            recipeId = "r",
            recipeContentVersion = 1,
            configurationId = config,
            loadMode = LoadModeV2.LOAD,
            unitMode = UnitModeV2.REPS,
            side = "bilateral",
            execution = "standard",
            slotPurpose = "main",
        ),
        sourceSessionId = "s",
        sourceLogIds = emptyList(),
        targetLoadKg = target,
        explanation = "",
        createdAtMs = 0L,
    )

    @Test
    fun laCapsulaDiceLoQueSubeLaCarga() {
        val exercises = listOf(loaded("squat", 100.0, 100.0), loaded("bench", 80.0))
        assertEquals("+2,5 kg", sessionProposalLabel(exercises, listOf(proposal("bench", 82.5))))
        assertEquals("+5 kg", sessionProposalLabel(exercises, listOf(proposal("squat", 105.0))))
        assertEquals("−2,5 kg", sessionProposalLabel(exercises, listOf(proposal("squat", 97.5))))
    }

    @Test
    fun sinPropuestaDeLaSesionNoHayCapsula() {
        val exercises = listOf(loaded("squat", 100.0))
        assertNull(sessionProposalLabel(exercises, emptyList()))
        // Otra configuración, sin carga objetivo o sin carga con la que comparar: nada.
        assertNull(sessionProposalLabel(exercises, listOf(proposal("deadlift", 150.0))))
        assertNull(sessionProposalLabel(exercises, listOf(proposal("squat", null))))
        assertNull(sessionProposalLabel(listOf(loaded("squat")), listOf(proposal("squat", 105.0))))
    }
}
