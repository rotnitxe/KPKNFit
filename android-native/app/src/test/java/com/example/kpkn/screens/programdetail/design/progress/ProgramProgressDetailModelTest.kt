package com.example.kpkn.screens.programdetail.design.progress

import androidx.compose.ui.graphics.Color
import com.example.kpkn.data.models.Block
import com.example.kpkn.data.models.CompletedExercise
import com.example.kpkn.data.models.CompletedSet
import com.example.kpkn.data.models.Macrocycle
import com.example.kpkn.data.models.Mesocycle
import com.example.kpkn.data.models.Program
import com.example.kpkn.data.models.ProgramWeek
import com.example.kpkn.data.models.WorkoutLog
import com.example.kpkn.domain.training.DecisionLine
import com.example.kpkn.domain.training.progress.WeekWork
import com.example.kpkn.screens.programdetail.components.PersonalizedVolumeTarget
import com.example.kpkn.screens.programdetail.components.volumeMuscleRows
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test
import java.time.LocalDate
import java.time.ZoneOffset

/**
 * Modelos de las escenas g-adh, g-lift, g-ranges y g-volume de Progreso: los cuadros del cumplimiento, el RIR frente al plan, la línea
 * de tiempo de decisiones, la hoja de un ejercicio, la línea de propuestas y las filas de músculos.
 */
class ProgramProgressDetailModelTest {

    private fun scopeWeek(id: String, position: Int, sessions: List<String>) = ProgressScopeWeek(
        id = id,
        blockId = "b",
        position = position,
        requiredSessionIds = sessions,
        sessionDays = emptyMap(),
        plannedSets = 6,
        plannedRir = null,
        isDeload = false,
        color = Color.White,
    )

    /** Hechas, faltas en una semana pasada y futuras en la actual: la semana en curso no penaliza. */
    @Test
    fun laRejillaMarcaHechasFaltasYFuturas() {
        val weeks = listOf(scopeWeek("w1", 0, listOf("a", "b")), scopeWeek("w2", 1, listOf("a", "b")))

        val adherence = progressAdherenceOf(
            scopeWeeks = weeks,
            currentPosition = 1,
            currentWeekId = "w2",
            loggedSessions = setOf("w1" to "a", "w2" to "a"),
            compliancePct = 67,
            scopeLogs = emptyList(),
        )!!

        assertEquals(listOf(AdherenceCell.DONE, AdherenceCell.MISSED), adherence.weeks[0].cells)
        assertEquals(listOf(AdherenceCell.DONE, AdherenceCell.FUTURE), adherence.weeks[1].cells)
        assertEquals(listOf("S1", "S2"), adherence.weeks.map { it.label })
        assertEquals(67, adherence.pct)
    }

    /** Sin sesiones obligatorias no hay módulo de cumplimiento. */
    @Test
    fun sinSesionesObligatoriasNoHayCumplimiento() {
        assertNull(progressAdherenceOf(listOf(scopeWeek("w1", 0, emptyList())), 0, "w1", emptySet(), null, emptyList()))
    }

    /** La semana con RIR medio 1,2 frente a un plan de 2 va marcada y nombrada en la nota, como en g-adh. */
    @Test
    fun elEsfuerzoMarcaLaSemanaMasCercaDelFallo() {
        val weeks = listOf(scopeWeek("w1", 0, listOf("a")), scopeWeek("w3", 2, listOf("a")))
        val work = listOf(
            WeekWork("w1", 6, 6, 0.0, 0, 0, 1, rirMean = 2.1, plannedRirMean = 2.0),
            WeekWork("w3", 6, 6, 0.0, 0, 0, 1, rirMean = 1.2, plannedRirMean = 2.0),
        )

        val effort = progressEffortOf(weeks, work)!!

        assertEquals(listOf(false, true), effort.rows.map { it.closerThanPlanned })
        assertEquals("1,2", formatRir(effort.rows[1].rir))
        assertEquals("2", formatPlannedRir(effort.rows[1].plannedRir!!))
        assertEquals(
            "En la semana 3 entrenaste más cerca del fallo de lo pedido. Si se repite con cargas iguales, es señal de fatiga.",
            effort.note,
        )
    }

    /** Las tres decisiones más recientes, con «Hoy», el día del mes en curso y, fuera de él, el mes. */
    @Test
    fun lasDecisionesSonLasTresUltimasConSuDia() {
        val today = LocalDate.of(2026, 10, 28)
        fun at(date: LocalDate) = date.atStartOfDay(ZoneOffset.UTC).toInstant().toEpochMilli() + 3_600_000
        val decisions = listOf(
            DecisionLine(at(LocalDate.of(2026, 9, 21)), "MANUAL", "Remo con barra → remo en máquina", "Cambio de ejercicio."),
            DecisionLine(at(today), "MANUAL", "Propuesta: +2,5 kg en sentadilla", "Cerraste 3 × 5 con RIR 3."),
            DecisionLine(at(LocalDate.of(2026, 10, 26)), "MANUAL", "Cambio de bloque: Fuerza", ""),
            DecisionLine(at(LocalDate.of(2026, 9, 28)), "MANUAL", "Press militar sin cambios", "Se mantiene la carga."),
        )

        val rows = progressDecisionRows(decisions, today, ZoneOffset.UTC)

        assertEquals(
            listOf("Propuesta: +2,5 kg en sentadilla", "Cambio de bloque: Fuerza", "Press militar sin cambios"),
            rows.map { it.title },
        )
        assertEquals(listOf("Hoy · Cerraste 3 × 5 con RIR 3.", "Lun 26", "Lun 28 sept · Se mantiene la carga."), rows.map { it.line })
    }

    /** Un ejercicio parado seis semanas: su hoja dice cuánto, explica el cálculo, sombrea las 3 últimas sesiones y da sus mejores series. */
    @Test
    fun laHojaDeUnEjercicioEstancadoExplicaElCalculo() {
        val dates = listOf("2026-01-05", "2026-01-12", "2026-01-19", "2026-01-26", "2026-02-02", "2026-02-09")
        val logs = dates.mapIndexed { index, day ->
            WorkoutLog(
                id = "l$index",
                programId = "p",
                sessionId = "s",
                sessionName = "Sesión",
                date = "${day}T10:00:00.000Z",
                durationMinutes = 60,
                completedExercises = listOf(
                    CompletedExercise(
                        exerciseId = "press",
                        exerciseName = "Press banca",
                        sets = listOf(CompletedSet(id = "l$index-0", weight = 100.0, reps = 1)),
                    ),
                ),
                weekId = "w1",
            )
        }
        val program = Program(
            id = "p",
            name = "Prueba",
            macrocycles = listOf(
                Macrocycle(
                    id = "m",
                    name = "Macrociclo",
                    blocks = listOf(
                        Block(
                            id = "b",
                            name = "Bloque",
                            mesocycles = listOf(Mesocycle(id = "meso", name = "Meso", weeks = listOf(ProgramWeek(id = "w1", name = "Semana 1")))),
                        ),
                    ),
                ),
            ),
        )

        val ui = buildProgramProgress(program, logs, ProgressScope.PROGRAM, null, null, currentWeekId = null, today = LocalDate.of(2026, 2, 9))
        val detail = ui.liftDetails.getValue(ui.lifts.single().key)

        assertEquals("Estancado 5 semanas", detail.title)
        val e1rm = kgText(detail.points.last().e1rmKg)
        assertEquals("El mejor e1RM de las 3 últimas sesiones ($e1rm kg) no supera al de las 3 anteriores ($e1rm kg).", detail.summary)
        assertEquals(3, detail.shadeFrom)
        assertEquals(listOf("Hoy", "Lun 5 ene"), detail.bestSets.map { it.dateLabel })
        assertEquals("100 kg × 1", detail.bestSets.first().detail)
        assertNull(detail.reading)
    }

    /** La línea de propuestas de g-ranges, con y sin semanas respondidas. */
    @Test
    fun laLineaDePropuestasCuentaMusculosYSemanas() {
        assertEquals("2 músculos con cambios propuestos tras 5 semanas respondidas · Revisar", rangesProposalsDetail(2, 5))
        assertEquals("1 músculo con cambios propuestos tras 1 semana respondida · Revisar", rangesProposalsDetail(1, 1))
        assertEquals("3 músculos con cambios propuestos · Revisar", rangesProposalsDetail(3, 0))
    }

    /**
     * Las filas del volumen: los nueve músculos del lienzo siempre, en su orden (con «Pantorrillas 0 / 0»); el resto solo con series. Las
     * prioridades del usuario van delante; sin registros, no hay cifra de lo hecho.
     */
    @Test
    fun lasFilasDelVolumenSiguenElOrdenDelLienzo() {
        val rows = volumeMuscleRows(
            planned = mapOf("Pectorales" to 12.0, "Cuádriceps" to 16.0, "Trapecio" to 4.0),
            done = mapOf("Cuádriceps" to 14.0),
            targets = mapOf("Cuádriceps" to PersonalizedVolumeTarget("Cuádriceps", 8, 16, 20)),
            priorityByMuscle = emptyMap(),
            hasLogs = true,
        )

        assertEquals(
            listOf("Cuádriceps", "Isquiosurales", "Glúteos", "Pectorales", "Dorsales", "Deltoides", "Bíceps", "Tríceps", "Pantorrillas", "Trapecio"),
            rows.map { it.name },
        )
        assertEquals(14.0, rows.first().real!!, 0.0)
        assertEquals(8, rows.first().zones!!.minEffective)
        assertNull(rows[1].zones)

        val prioritized = volumeMuscleRows(mapOf("Bíceps" to 6.0), emptyMap(), null, mapOf("Bíceps" to 3.0), hasLogs = false)
        assertEquals("Bíceps", prioritized.first().name)
        assertNull(prioritized.first().real)
    }
}
