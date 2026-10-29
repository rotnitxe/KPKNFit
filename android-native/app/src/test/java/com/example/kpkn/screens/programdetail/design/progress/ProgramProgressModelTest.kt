package com.example.kpkn.screens.programdetail.design.progress

import com.example.kpkn.data.models.Block
import com.example.kpkn.data.models.BlockGoal
import com.example.kpkn.data.models.CompletedExercise
import com.example.kpkn.data.models.CompletedSet
import com.example.kpkn.data.models.Exercise
import com.example.kpkn.data.models.ExerciseSet
import com.example.kpkn.data.models.Macrocycle
import com.example.kpkn.data.models.Mesocycle
import com.example.kpkn.data.models.Program
import com.example.kpkn.data.models.ProgramWeek
import com.example.kpkn.data.models.Session
import com.example.kpkn.data.models.WorkoutLog
import com.example.kpkn.domain.training.progress.LiftTrendStatus
import com.example.kpkn.screens.programdetail.design.programPhaseColor
import com.example.kpkn.screens.programdetail.design.programPhaseOf
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

/*
 * Programa de prueba: bloque 1 (acumulación) con las semanas w1 y w2; bloque 2 (intensificación) con w3 y w4. Cada semana tiene dos
 * sesiones de tres series, así que cada semana previsita 6 series y 2 sesiones.
 */
private val program = Program(
    id = "p",
    name = "Prueba",
    macrocycles = listOf(
        Macrocycle(
            id = "m",
            name = "Macrociclo",
            blocks = listOf(
                block("b1", BlockGoal.ACCUMULATION, week("w1"), week("w2")),
                block("b2", BlockGoal.INTENSIFICATION, week("w3"), week("w4")),
            ),
        ),
    ),
)

/** Un bloque con su objetivo y sus semanas en un único mesociclo. */
private fun block(id: String, goal: BlockGoal, vararg weeks: ProgramWeek) = Block(
    id = id,
    name = "Bloque $id",
    goal = goal,
    mesocycles = listOf(Mesocycle(id = "$id-meso", name = "Mesociclo $id", weeks = weeks.toList())),
)

/** Una semana con dos sesiones: «<id>-a» y «<id>-b». */
private fun week(id: String) = ProgramWeek(
    id = id,
    name = "Semana $id",
    sessions = listOf(session("$id-a"), session("$id-b")),
)

/** Una sesión con un ejercicio de tres series. */
private fun session(id: String) = Session(
    id = id,
    name = "Sesión $id",
    exercises = listOf(Exercise(id = "$id-ex", name = "Ejercicio $id", sets = List(3) { ExerciseSet(id = "$id-ex-$it") })),
)

/** Un entreno de [sessionId] en [weekId] con un ejercicio de [sets] series de 60 kg × 8 repeticiones. */
private fun sessionLog(
    id: String,
    weekId: String,
    sessionId: String,
    sets: Int = 3,
    date: String = "2026-01-05",
) = WorkoutLog(
    id = id,
    programId = "p",
    sessionId = sessionId,
    sessionName = "Sesión",
    date = "${date}T10:00:00.000Z",
    durationMinutes = 60,
    completedExercises = listOf(
        CompletedExercise(
            exerciseId = "press",
            exerciseName = "Press banca",
            sets = List(sets) { CompletedSet(id = "$id-$it", weight = 60.0, reps = 8) },
        ),
    ),
    weekId = weekId,
)

/** Un entreno en la semana w1 con una serie de 1 repetición por ejercicio: su e1RM es el peso. */
private fun liftLog(id: String, date: String, vararg lifts: Pair<String, Double>) = WorkoutLog(
    id = id,
    programId = "p",
    sessionId = "w1-a",
    sessionName = "Sesión",
    date = "${date}T10:00:00.000Z",
    durationMinutes = 60,
    completedExercises = lifts.map { (name, kg) ->
        CompletedExercise(
            exerciseId = name,
            exerciseName = name,
            sets = listOf(CompletedSet(id = "$id-$name", weight = kg, reps = 1)),
        )
    },
    weekId = "w1",
)

/** Seis sesiones semanales desde el 5 de enero: entre la primera y la última hay 35 días. */
private val weeklyDates = listOf("2026-01-05", "2026-01-12", "2026-01-19", "2026-01-26", "2026-02-02", "2026-02-09")

class ProgramProgressModelTest {

    /** Sin registros la cabecera lo dice, y con sesiones ya previstas el cumplimiento es 0 %. */
    @Test
    fun sinRegistrosLoDiceYElCumplimientoEsCeroConSesionesPrevistas() {
        val ui = buildProgramProgress(program, emptyList(), ProgressScope.PROGRAM, null, null, currentWeekId = "w2")

        assertEquals("Aún no hay entrenos registrados en este periodo.", ui.headline)
        assertEquals(4, ui.kpis.sessionsPlanned)
        assertEquals(0, ui.kpis.compliancePct)
    }

    /** Con registros pero sin ejercicio con tendencia (g-empty), la cabecera lo dice y la fila cuenta su única sesión. */
    @Test
    fun conRegistrosSinTendenciaLoDice() {
        val logs = listOf(sessionLog("l1", "w1", "w1-a"))

        val ui = buildProgramProgress(program, logs, ProgressScope.PROGRAM, null, null, currentWeekId = "w1")

        assertEquals("Aún no hay tendencia. Con 2 sesiones de cada ejercicio empezamos a comparar.", ui.headline)
        assertEquals("1 sesión", ui.lifts.single().caption)
        assertTrue(ui.early)
        assertEquals("Semana 1", ui.periodLabel)
        assertEquals("esta semana", ui.tonnageFooter)
    }

    /** Con la segunda semana en curso cuentan las dos primeras (4 sesiones previstas); 3 hechas dan el 75 %. */
    @Test
    fun cuentaSoloLasSemanasEmpezadasYElCumplimientoSaleDelTresDeCuatro() {
        val logs = listOf(
            sessionLog("l1", "w1", "w1-a"),
            sessionLog("l2", "w1", "w1-b"),
            sessionLog("l3", "w2", "w2-a"),
        )

        val ui = buildProgramProgress(program, logs, ProgressScope.PROGRAM, null, null, currentWeekId = "w2")

        assertEquals(3, ui.kpis.sessionsDone)
        assertEquals(4, ui.kpis.sessionsPlanned)
        assertEquals(75, ui.kpis.compliancePct)
        assertEquals("En 2 semanas", ui.periodLabel)
    }

    /** Cada semana del programa tiene su barra S1…S4, con sus series previstas, y solo la segunda va marcada como actual. */
    @Test
    fun unaBarraPorSemanaNumeradaYLaActualMarcada() {
        val ui = buildProgramProgress(program, emptyList(), ProgressScope.PROGRAM, null, null, currentWeekId = "w2")

        assertEquals(listOf("S1", "S2", "S3", "S4"), ui.weeks.map { it.label })
        assertEquals(listOf(false, true, false, false), ui.weeks.map { it.isCurrent })
        assertEquals(listOf(6, 6, 6, 6), ui.weeks.map { it.planned })
    }

    /** Un ejercicio con seis sesiones que suben: la cabecera cuenta «1 de 1» y la lista lo muestra con sus sesiones. */
    @Test
    fun unEjercicioQueSubeDiceSubesEn1De1() {
        val kg = listOf(100.0, 101.0, 102.0, 104.0, 106.0, 108.0)
        val logs = weeklyDates.zip(kg).mapIndexed { index, (day, weight) -> liftLog("l$index", day, "Press banca" to weight) }

        val ui = buildProgramProgress(program, logs, ProgressScope.PROGRAM, null, null, currentWeekId = "w4")

        assertEquals("Subes en 1 de 1 básicos.", ui.headline)
        assertEquals(1, ui.allLiftsCount)
        assertEquals(LiftTrendStatus.UP, ui.lifts.single().status)
        assertEquals("6 sesiones", ui.lifts.single().caption)
    }

    /** Un ejercicio parado y otro que baja se nombran en la cabecera, y el estancamiento cuenta sus semanas. */
    @Test
    fun estancadoYBajadaSeNombranEnLaCabecera() {
        val squat = listOf(100.0, 100.0, 100.0, 95.0, 95.0, 95.0)
        val logs = weeklyDates.mapIndexed { index, day ->
            liftLog("l$index", day, "Press banca" to 100.0, "Sentadilla" to squat[index])
        }

        val ui = buildProgramProgress(program, logs, ProgressScope.PROGRAM, null, null, currentWeekId = "w4")

        assertEquals("Subes en 0 de 2 básicos. Press banca lleva 5 semanas parado y Sentadilla baja.", ui.headline)
    }

    /** La lista enseña cinco ejercicios como mucho, y el contador cuenta todos los que tienen sesiones. */
    @Test
    fun laListaTopeaEnCincoYElContadorCuentaTodos() {
        val names = listOf("Banca", "Dominadas", "Peso muerto", "Press militar", "Remo", "Sentadilla")
        val logs = weeklyDates.mapIndexed { index, day ->
            val curl: List<Pair<String, Double>> = if (index < 4) listOf("Curl" to 20.0) else emptyList()
            val lifts = names.map { it to 100.0 } + curl
            liftLog("l$index", day, *lifts.toTypedArray())
        }

        val ui = buildProgramProgress(program, logs, ProgressScope.PROGRAM, null, null, currentWeekId = "w4")

        assertEquals(5, ui.lifts.size)
        assertEquals(7, ui.allLiftsCount)
        assertTrue(ui.lifts.none { it.name == "Curl" })
    }

    /** En el alcance de semana no hay barras y solo cuentan los registros de esa semana. */
    @Test
    fun elAlcanceDeSemanaSoloCuentaSusRegistros() {
        val logs = listOf(sessionLog("l1", "w1", "w1-a"), sessionLog("l2", "w2", "w2-a"))

        val ui = buildProgramProgress(program, logs, ProgressScope.WEEK, null, selectedWeekId = "w2", currentWeekId = "w2")

        assertTrue(ui.weeks.isEmpty())
        assertEquals("Semana 2", ui.periodLabel)
        assertEquals(1, ui.kpis.sessionsDone)
        assertEquals(2, ui.kpis.sessionsPlanned)
        assertEquals(3, ui.kpis.setsDone)
    }

    /** Una semana empezada, y no la actual, con seis series menos de las previstas se anota con las sesiones que faltaron. */
    @Test
    fun laPrimeraSemanaConSeisSeriesMenosSeAnota() {
        val logs = listOf(sessionLog("l1", "w2", "w2-a"), sessionLog("l2", "w2", "w2-b"))

        val ui = buildProgramProgress(program, logs, ProgressScope.PROGRAM, null, null, currentWeekId = "w3")

        assertEquals("Semana 1: 6 series menos de lo planificado (faltaron 2 sesiones).", ui.weekNote)
    }

    /** La semana actual no se anota aunque vaya atrasada, y una semana anterior al día tampoco. */
    @Test
    fun laSemanaActualNoSeAnotaNiLasAlDia() {
        val logs = listOf(sessionLog("l1", "w1", "w1-a"), sessionLog("l2", "w1", "w1-b"))

        val ui = buildProgramProgress(program, logs, ProgressScope.PROGRAM, null, null, currentWeekId = "w2")

        assertNull(ui.weekNote)
    }

    /** Una semana que aún no ha empezado no tiene sesiones previstas, y por eso su cumplimiento es nulo. */
    @Test
    fun unaSemanaFuturaNoTieneCumplimiento() {
        val ui = buildProgramProgress(program, emptyList(), ProgressScope.WEEK, null, selectedWeekId = "w4", currentWeekId = "w1")

        assertEquals(0, ui.kpis.sessionsPlanned)
        assertNull(ui.kpis.compliancePct)
    }

    /** Más sesiones hechas que previstas no pasan del 100 %. */
    @Test
    fun elCumplimientoNoPasaDelCienPorCiento() {
        val logs = listOf(sessionLog("l1", "w1", "w1-a"), sessionLog("l2", "w1", "w1-b"), sessionLog("l3", "w2", "w2-a"))

        val ui = buildProgramProgress(program, logs, ProgressScope.PROGRAM, null, null, currentWeekId = "w1")

        // Solo la primera semana ha empezado: 2 sesiones previstas y 3 hechas.
        assertEquals(2, ui.kpis.sessionsPlanned)
        assertEquals(100, ui.kpis.compliancePct)
    }

    /** El alcance de bloque trae solo sus semanas, numeradas dentro del programa; las de un bloque futuro no han empezado. */
    @Test
    fun elAlcanceDeBloqueNumeraSusSemanasDentroDelPrograma() {
        val logs = listOf(sessionLog("l1", "w3", "w3-a"))

        val ui = buildProgramProgress(
            program,
            logs,
            ProgressScope.BLOCK,
            selectedBlockId = "b2",
            selectedWeekId = null,
            currentWeekId = "w2",
        )

        assertEquals(listOf("S3", "S4"), ui.weeks.map { it.label })
        assertEquals("Aún sin empezar", ui.periodLabel)
        assertEquals(0, ui.kpis.sessionsPlanned)
        assertEquals(1, ui.kpis.sessionsDone)
    }

    /** Cada barra lleva el color de la fase de su bloque: acumulación en el primero, intensificación en el segundo. */
    @Test
    fun cadaBarraLlevaElColorDeLaFaseDeSuBloque() {
        val ui = buildProgramProgress(program, emptyList(), ProgressScope.PROGRAM, null, null, currentWeekId = "w1")

        assertEquals(programPhaseColor(programPhaseOf(BlockGoal.ACCUMULATION, hasCompetitionWeek = false)), ui.weeks[0].color)
        assertEquals(programPhaseColor(programPhaseOf(BlockGoal.INTENSIFICATION, hasCompetitionWeek = false)), ui.weeks[2].color)
    }
}
