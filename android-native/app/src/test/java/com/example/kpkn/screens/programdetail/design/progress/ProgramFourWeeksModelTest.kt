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
import com.example.kpkn.data.models.ProgramStructure
import com.example.kpkn.data.models.ProgramWeek
import com.example.kpkn.data.models.Session
import com.example.kpkn.data.models.WorkoutLog
import com.example.kpkn.domain.training.progress.LiftTrendStatus
import com.example.kpkn.screens.programdetail.design.ProgramVolumeMuscle
import com.example.kpkn.screens.programdetail.design.ProgramVolumeZones
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import java.time.LocalDate
import java.time.ZoneOffset

/*
 * Hoy es el jueves 29 de octubre de 2026: las 4 semanas de calendario empiezan los lunes 5, 12, 19 y 26 de octubre. El programa tiene 6
 * semanas (w1–w2 acumulación, w3–w5 intensificación, w6 descarga); cada semana, tres sesiones obligatorias el lunes, el miércoles y el
 * viernes, de un ejercicio con 3 series.
 */
private val today = LocalDate.of(2026, 10, 29)
private val utc = ZoneOffset.UTC

private val program = Program(
    id = "p",
    name = "Prueba",
    macrocycles = listOf(
        Macrocycle(
            id = "m",
            name = "Macrociclo",
            blocks = listOf(
                block("b1", BlockGoal.ACCUMULATION, week("w1"), week("w2")),
                block("b2", BlockGoal.INTENSIFICATION, week("w3"), week("w4"), week("w5")),
                block("b3", BlockGoal.DELOAD, week("w6")),
            ),
        ),
    ),
)

/** Una rutina: una sola semana (lunes «w1-a» y jueves «w1-b») que se repite. */
private val routine = Program(
    id = "p",
    name = "Rutina",
    structure = ProgramStructure.SIMPLE,
    macrocycles = listOf(
        Macrocycle(
            id = "m",
            name = "Macrociclo",
            blocks = listOf(
                Block(
                    id = "b1",
                    name = "Bloque",
                    goal = BlockGoal.ACCUMULATION,
                    mesocycles = listOf(
                        Mesocycle(
                            id = "meso",
                            name = "Meso",
                            weeks = listOf(ProgramWeek(id = "w1", name = "Semana", sessions = listOf(session("w1-a", 1), session("w1-b", 4)))),
                        ),
                    ),
                ),
            ),
        ),
    ),
)

private fun block(id: String, goal: BlockGoal, vararg weeks: ProgramWeek) = Block(
    id = id,
    name = "Bloque $id",
    goal = goal,
    mesocycles = listOf(Mesocycle(id = "$id-meso", name = "Mesociclo $id", weeks = weeks.toList())),
)

private fun week(id: String) = ProgramWeek(
    id = id,
    name = "Semana $id",
    sessions = listOf(session("$id-a", 1), session("$id-b", 3), session("$id-c", 5)),
)

private fun session(id: String, day: Int) = Session(
    id = id,
    name = "Sesión $id",
    dayOfWeek = day,
    exercises = listOf(Exercise(id = "$id-ex", name = "Ejercicio $id", sets = List(3) { ExerciseSet(id = "$id-ex-$it") })),
)

/** Un entreno de [sessionId] en [weekId] el día [date]: press banca, 3 series de [weight] kg × [reps], 60 minutos. */
private fun log(
    id: String,
    weekId: String,
    sessionId: String,
    date: String,
    discomforts: List<String> = emptyList(),
    weight: Double = 60.0,
    reps: Int = 8,
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
            sets = List(3) { CompletedSet(id = "$id-$it", weight = weight, reps = reps) },
        ),
    ),
    weekId = weekId,
    discomforts = discomforts,
)

/**
 * w1 entera; w2 sin el viernes 16; w3 solo el lunes 19; w4 (la de hoy) el lunes 26 y sin el miércoles 28. El viernes 30 aún no toca.
 * Molestia en el hombro el lunes 12 y el miércoles 14.
 */
private val logs = listOf(
    log("l1", "w1", "w1-a", "2026-10-05"),
    log("l2", "w1", "w1-b", "2026-10-07"),
    log("l3", "w1", "w1-c", "2026-10-09"),
    log("l4", "w2", "w2-a", "2026-10-12", discomforts = listOf("Hombro derecho")),
    log("l5", "w2", "w2-b", "2026-10-14", discomforts = listOf("Hombro derecho")),
    log("l6", "w3", "w3-a", "2026-10-19"),
    log("l7", "w4", "w4-a", "2026-10-26"),
)

private fun liftRow(name: String, status: LiftTrendStatus, delta: Double?, current: Double? = 100.0) = ProgressLiftRow(
    key = "name:$name",
    name = name,
    status = status,
    currentKg = current,
    deltaKg = delta,
    points = listOf(1.0, 2.0),
    caption = "6 sesiones",
)

class ProgramFourWeeksModelTest {

    /** Las 4 semanas de calendario: sesiones hechas, saltadas (solo las que ya pasaron) y previstas = hechas + saltadas. */
    @Test
    fun cuentaSesionesHechasYSaltadasDeLasCuatroSemanas() {
        val ui = buildProgramFourWeeks(program, logs, currentWeekId = "w4", today = today, zone = utc)

        assertEquals(4, ui.countedWeeks)
        assertEquals(7, ui.sessionsDone)
        assertEquals(4, ui.skipped)
        assertEquals(11, ui.sessionsPlanned)
        assertEquals("Cumpliste 7 de 11 sesiones.", ui.lead.text)
        assertEquals("7 de 11 sesiones", ui.lead.bold)
    }

    /** El eyebrow dice las semanas del programa y el rango, del lunes de la primera a hoy, con la semana en curso. */
    @Test
    fun eyebrowYRangoConLaSemanaEnCurso() {
        val ui = buildProgramFourWeeks(program, logs, currentWeekId = "w4", today = today, zone = utc)

        assertEquals("Prueba · semanas 1 a 4", ui.eyebrow)
        assertEquals("5 – 29 oct · la semana 4 sigue en curso", ui.range)
    }

    /** Calendario de 28 días que empieza el lunes 5: hecho, saltado (con día) y hoy. */
    @Test
    fun calendarioMarcaHechasSaltadasYHoy() {
        val ui = buildProgramFourWeeks(program, logs, currentWeekId = "w4", today = today, zone = utc)
        val byDate = ui.days.associateBy { it.date }

        assertEquals(28, ui.days.size)
        assertEquals(LocalDate.of(2026, 10, 5), ui.days.first().date)
        assertEquals(FourWeeksDayState.DONE, byDate.getValue(LocalDate.of(2026, 10, 26)).state)
        assertEquals(FourWeeksDayState.SKIPPED, byDate.getValue(LocalDate.of(2026, 10, 16)).state)
        assertEquals(FourWeeksDayState.SKIPPED, byDate.getValue(LocalDate.of(2026, 10, 28)).state)
        assertEquals(FourWeeksDayState.REST, byDate.getValue(LocalDate.of(2026, 10, 30)).state)
        assertTrue(byDate.getValue(today).isToday)
        assertEquals(FourWeeksDayState.REST, byDate.getValue(today).state)
        assertEquals(4, ui.days.count { it.state == FourWeeksDayState.SKIPPED })
    }

    /** Series duras hechas frente a las previstas (enteras), tonelaje y tiempo, y una columna por semana con «S4 · hoy». */
    @Test
    fun cifrasYSemanaASemana() {
        val ui = buildProgramFourWeeks(program, logs, currentWeekId = "w4", today = today, zone = utc)

        assertEquals(21, ui.hardSetsDone)
        assertEquals(36, ui.hardSetsPlanned)
        assertEquals(10_080.0, ui.tonnageKg, 0.001)
        assertEquals("10,1", fourWeeksTonnes(ui.tonnageKg))
        assertEquals(420, ui.minutes)
        assertEquals(listOf("S1", "S2", "S3", "S4 · hoy"), ui.weeks.map { it.label })
        assertEquals(listOf(9, 6, 3, 3), ui.weeks.map { it.hardSets })
        assertEquals(listOf(9, 9, 9, 9), ui.weeks.map { it.plannedSets })
        assertTrue(ui.weeks.last().isCurrent)
    }

    /** Las molestias dicen cuántas veces y en qué semana; sin RIR registrado no hay fila de RIR. */
    @Test
    fun molestiasConSuSemana() {
        val ui = buildProgramFourWeeks(program, logs, currentWeekId = "w4", today = today, zone = utc)

        val note = ui.effort.single()
        assertEquals("Hombro derecho", note.title)
        assertEquals("2 veces, las dos en la semana 2", note.caption)
        assertEquals("Vigilar", note.tag)
        assertTrue(note.low)
    }

    /** Lo que viene: las dos semanas siguientes con su fase; la primera lleva la propuesta y la última cierra el programa. */
    @Test
    fun loQueVieneConFaseYPropuesta() {
        val ui = buildProgramFourWeeks(
            program,
            logs,
            currentWeekId = "w4",
            today = today,
            zone = utc,
            proposal = "propuesta: sentadilla a 105 kg",
        )

        assertEquals(listOf("w5", "w6"), ui.next.map { it.weekId })
        assertEquals("Semana 5 · Intensificación", ui.next[0].title)
        assertEquals("3 sesiones · 9 series · propuesta: sentadilla a 105 kg", ui.next[0].line)
        assertEquals("Semana 6 · Descarga", ui.next[1].title)
        assertEquals("3 sesiones · 9 series · cierra el programa", ui.next[1].line)
    }

    /** Con el programa empezado hace dos semanas cuentan solo esas dos, y el eyebrow y el rango lo dicen. */
    @Test
    fun conMenosSemanasCuentaLasQueHay() {
        val short = listOf(
            log("s1", "w1", "w1-a", "2026-10-19"),
            log("s2", "w1", "w1-b", "2026-10-21"),
            log("s3", "w1", "w1-c", "2026-10-23"),
            log("s4", "w2", "w2-a", "2026-10-26"),
            log("s5", "w2", "w2-b", "2026-10-28"),
        )

        val ui = buildProgramFourWeeks(program, short, currentWeekId = "w2", today = today, zone = utc)

        assertEquals(2, ui.countedWeeks)
        assertEquals(14, ui.shownDays.size)
        assertEquals("Prueba · semanas 1 a 2", ui.eyebrow)
        assertEquals("19 – 29 oct · la semana 2 sigue en curso", ui.range)
        assertEquals(5, ui.sessionsDone)
        assertEquals(0, ui.skipped)
        assertEquals(listOf("S1", "S2 · hoy"), ui.weeks.map { it.label })
    }

    /** Una rutina cuenta por semana de calendario desde su primer registro, como Progreso. */
    @Test
    fun rutinaPorSemanaDeCalendario() {
        val routineLogs = listOf(
            log("r1", "w1", "w1-a", "2026-10-12"),
            log("r2", "w1", "w1-a", "2026-10-19"),
            log("r3", "w1", "w1-b", "2026-10-22"),
            log("r4", "w1", "w1-a", "2026-10-26"),
        )

        val ui = buildProgramFourWeeks(routine, routineLogs, currentWeekId = "w1", today = today, zone = utc)

        assertEquals(3, ui.countedWeeks)
        assertEquals("Rutina · semanas 1 a 3", ui.eyebrow)
        assertEquals(4, ui.sessionsDone)
        // El jueves 15 faltó; el jueves 29 es hoy y aún no cuenta.
        assertEquals(1, ui.skipped)
        assertEquals(5, ui.sessionsPlanned)
        assertTrue(ui.next.isEmpty())
    }

    /** Sin semana en curso ni registros, la página se queda con la semana de hoy y sin sesiones. */
    @Test
    fun sinDatosSoloLaSemanaDeHoy() {
        val ui = buildProgramFourWeeks(program, emptyList(), currentWeekId = null, today = today, zone = utc)

        assertEquals(1, ui.countedWeeks)
        assertEquals("Prueba", ui.eyebrow)
        assertEquals("26 – 29 oct", ui.range)
        assertEquals("Aún no hay sesiones registradas en estas semanas.", ui.lead.text)
        assertNull(ui.entryLine)
        assertTrue(ui.records.isEmpty())
    }

    /** Un récord: la serie de la ventana que supera a todas las anteriores del programa; una primera vez no cuenta. */
    @Test
    fun recordDeLaVentanaFrenteAlHistorial() {
        val history = listOf(
            log("h1", "w1", "w1-a", "2026-09-28", weight = 100.0, reps = 5),
            log("h2", "w1", "w1-a", "2026-10-19", weight = 105.0, reps = 5),
        )

        val ui = buildProgramFourWeeks(program, history, currentWeekId = "w4", today = today, zone = utc)

        val record = ui.records.single()
        assertEquals("Press banca", record.exercise)
        assertEquals("105 kg × 5 · lun 19", record.detail)
        assertEquals("Mejor serie", record.kind)
        assertTrue(ui.entryLine!!.endsWith("1 récord"))
    }

    /** La frase: lo que sube y, en gris, lo que baja con sus semanas. */
    @Test
    fun fraseConLoQueSubeYLoQueBaja() {
        val rows = listOf(
            liftRow("Sentadilla trasera", LiftTrendStatus.UP, 7.5),
            liftRow("Press banca", LiftTrendStatus.UP, 4.0),
            liftRow("Press militar", LiftTrendStatus.DOWN, -2.5),
        )

        val lead = fourWeeksLeadOf(done = 14, planned = 16, rows = rows, downWeeksOf = { 2 })

        assertEquals("Cumpliste ", lead.start)
        assertEquals("14 de 16 sesiones", lead.bold)
        assertEquals(". Sentadilla trasera y press banca suben; ", lead.rest)
        assertEquals("press militar baja desde hace 2 semanas.", lead.muted)
    }

    /** Solo lo que se estanca: va en gris y con mayúscula. */
    @Test
    fun fraseSoloConEstancado() {
        val lead = fourWeeksLeadOf(
            done = 3,
            planned = 4,
            rows = listOf(liftRow("Peso muerto rumano", LiftTrendStatus.STAGNANT, 0.0)),
            stalledWeeksOf = { 3 },
        )

        assertEquals("Cumpliste 3 de 4 sesiones. Peso muerto rumano lleva 3 semanas parado.", lead.text)
        assertEquals("Peso muerto rumano lleva 3 semanas parado.", lead.muted)
    }

    /** La línea de la entrada: sesiones, el que más sube y los récords; sin dato, cada parte se omite. */
    @Test
    fun lineaDeLaEntrada() {
        val rows = listOf(
            liftRow("Press banca", LiftTrendStatus.UP, 4.0),
            liftRow("Sentadilla", LiftTrendStatus.UP, 7.5),
            liftRow("Press militar", LiftTrendStatus.DOWN, -2.5),
        )

        assertEquals("14 de 16 sesiones · sentadilla +7,5 kg · 2 récords", fourWeeksEntryLine(14, 16, rows, 2))
        assertEquals("14 de 16 sesiones", fourWeeksEntryLine(14, 16, emptyList(), 0))
        assertNull(fourWeeksEntryLine(0, 0, emptyList(), 0))
    }

    /** El cambio de fuerza: verde con «+», rojo con «−» y «=» sin cambio. */
    @Test
    fun cambioDeFuerza() {
        assertEquals("+7,5 kg", fourWeeksDeltaText(7.5))
        assertEquals("−2,5 kg", fourWeeksDeltaText(-2.5))
        assertEquals("+4 kg", fourWeeksDeltaText(4.0))
        assertEquals("=", fourWeeksDeltaText(0.0))
        assertEquals("=", fourWeeksDeltaText(null))
        assertEquals("142,5 kg · 6 sesiones", fourWeeksLiftCaption(liftRow("Sentadilla", LiftTrendStatus.UP, 7.5, current = 142.5)))
    }

    /** Músculos: en rango, justo (tercio bajo del rango), por debajo y sin series; la primera cifra dice «series». */
    @Test
    fun estadoPorMusculo() {
        val rows = listOf(
            ProgramVolumeMuscle("Cuádriceps", 16.0, ProgramVolumeZones(8, 16, 20), 14.0, 0.0),
            ProgramVolumeMuscle("Isquiosurales", 10.0, ProgramVolumeZones(6, 12, 16), 9.0, 0.0),
            ProgramVolumeMuscle("Tríceps", 8.0, ProgramVolumeZones(6, 14, 18), 5.0, 0.0),
            ProgramVolumeMuscle("Pantorrillas", 0.0, ProgramVolumeZones(6, 12, 16), 0.0, 0.0),
            ProgramVolumeMuscle("Dorsales", 16.0, ProgramVolumeZones(8, 18, 22), 15.0, 0.0),
        )

        val muscles = fourWeeksMusclesOf(rows)

        assertEquals(listOf("Cuádriceps", "Dorsales", "Isquiosurales", "Tríceps", "Pantorrillas"), muscles.map { it.name })
        assertEquals("14 series por semana · rango 8–20", muscles[0].caption)
        assertEquals(FourWeeksMuscleTag.IN_RANGE, muscles[0].tag)
        assertEquals("15 por semana · rango 8–22", muscles[1].caption)
        assertEquals(FourWeeksMuscleTag.JUST, muscles[2].tag)
        assertEquals("5 por semana · mínimo útil 6", muscles[3].caption)
        assertEquals(FourWeeksMuscleTag.BELOW, muscles[3].tag)
        assertEquals("Sin trabajo directo en el plan", muscles[4].caption)
        assertEquals(FourWeeksMuscleTag.NONE, muscles[4].tag)
    }

    /** El RIR medio frente al pedido. */
    @Test
    fun rirFrenteAlPlan() {
        val asked = fourWeeksRirNote(2.1, 2.0)!!
        assertEquals("RIR medio 2,1", asked.title)
        assertEquals("El plan pedía 2: vas donde toca", asked.caption)
        assertEquals("Según el plan", asked.tag)

        val closer = fourWeeksRirNote(1.2, 2.0)!!
        assertEquals("Más cerca del fallo", closer.tag)
        assertTrue(closer.low)

        assertEquals("El plan no fija un RIR", fourWeeksRirNote(2.0, null)!!.caption)
        assertNull(fourWeeksRirNote(null, 2.0))
    }

    /** El rango cruza de mes y, sin semana abierta, va sin «sigue en curso». */
    @Test
    fun rangoQueCruzaDeMes() {
        assertEquals("28 sept – 22 oct", fourWeeksRangeOf(LocalDate.of(2026, 9, 28), LocalDate.of(2026, 10, 22), null))
        assertEquals("Prueba · semana 3", fourWeeksEyebrowOf("Prueba", 3, 3))
    }
}
