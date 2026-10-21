package com.example.kpkn.domain.training

import com.example.kpkn.data.models.ProgramRunState
import com.example.kpkn.data.models.ProgramStructure
import com.example.kpkn.data.models.SimpleProgramKind
import org.junit.Assert.*
import org.junit.Test
import java.time.LocalDate
import java.time.ZoneId

class TrainingWindowTest {
    private val f = TrainingVolumeFixtures

    @Test
    fun `seis sesiones en una semana son doce series semanales`() {
        val window = TrainingWindow.selectedWeek(f.today.minusDays(2), f.zone)
        val logs = (0..5).map { f.log("log-$it", f.today.toString()) }
        val result = VolumeCalculator.calculateCompletedVolume(logs, f.catalog, window)
        assertEquals(12.0, result.muscles.getValue("Pectorales").sets, 1e-9)
        assertEquals(1.0, window.weekDivisor, 0.0)
    }

    @Test
    fun `seis sesiones repartidas en tres semanas son cuatro series semanales`() {
        val start = f.today.minusWeeks(2)
        val window = TrainingWindow.prescribedAverage(start, 3, f.zone)
        val logs = (0..5).map { f.log("log-$it", start.plusDays((it / 2 * 7).toLong()).toString()) }
        val result = VolumeCalculator.calculateCompletedVolume(logs, f.catalog, window)
        assertEquals(4.0, result.muscles.getValue("Pectorales").sets, 1e-9)
    }

    @Test
    fun `la semana vacia permanece en el divisor del bloque`() {
        val weeks = listOf(f.week("w1", f.today.minusWeeks(1), sessions = 6), f.week("w2", f.today))
        val window = TrainingWindow.forProgramScope(f.program(weeks), weeks, true, f.zone, f.today)
        val logs = List(6) { f.log("log-$it", f.today.minusWeeks(1).toString()) }
        val result = VolumeCalculator.calculateCompletedVolume(logs, f.catalog, window)
        assertEquals(2.0, window.weekDivisor, 0.0)
        assertEquals(6.0, result.muscles.getValue("Pectorales").sets, 1e-9)
    }

    @Test
    fun `una semana seleccionada vacia conserva divisor uno`() {
        val week = f.week("vacia", f.today)
        val window = TrainingWindow.forProgramScope(f.program(listOf(week)), listOf(week), false, f.zone, f.today)
        assertEquals(1.0, window.weekDivisor, 0.0)
        assertTrue(VolumeCalculator.calculateCompletedVolume(emptyList(), f.catalog, window).muscles.isEmpty())
    }

    @Test
    fun `ultimos veintiocho dias incluyen hoy y el limite pero excluyen el dia veintinueve y el futuro`() {
        val window = TrainingWindow.last28Days(f.zone, f.today)
        val logs = listOf(
            f.log("hoy"), f.log("limite", f.today.minusDays(27).toString()),
            f.log("dia-29", f.today.minusDays(28).toString()), f.log("futuro", f.today.plusDays(1).toString()),
        )
        assertEquals(setOf("hoy", "limite"), window.selectLogs(logs).logs.map { it.id }.toSet())
        assertEquals(4.0, window.weekDivisor, 0.0)
        assertEquals(1.0, VolumeCalculator.calculateCompletedVolume(logs, f.catalog, window).muscles.getValue("Pectorales").sets, 1e-9)
    }

    @Test
    fun `ultimos siete dias y total declaran divisores distintos del promedio prescrito`() {
        val last7 = TrainingWindow.lastDays(7, f.today, f.zone)
        assertEquals(f.today.minusDays(6), last7.start)
        assertEquals(1.0, last7.weekDivisor, 0.0)
        val total = TrainingWindow.total(f.today.minusDays(27), f.today, f.zone)
        assertEquals(TrainingWindowType.TOTAL, total.type)
        assertEquals(1.0, total.weekDivisor, 0.0)
        val logs = listOf(f.log("hoy", sets = 8))
        assertEquals(8.0, VolumeCalculator.calculateCompletedVolume(logs, f.catalog, total).muscles.getValue("Pectorales").sets, 1e-9)
    }

    @Test
    fun `un registro sin fecha asignable se excluye y se cuenta como sin asignar`() {
        val window = TrainingWindow.last28Days(f.zone, f.today)
        val result = VolumeCalculator.calculateCompletedVolume(listOf(f.log("sin-fecha", "ilegible")), f.catalog, window)
        assertTrue(result.muscles.isEmpty())
        assertEquals(1, result.unassignedCount)
    }

    @Test
    fun `las veintitres treinta locales permanecen en su dia aunque UTC ya sea manana`() {
        val local = f.today.atTime(23, 30).atZone(f.zone)
        val window = TrainingWindow.lastDays(7, f.today, f.zone)
        assertEquals(f.today.plusDays(1), local.toInstant().atZone(ZoneId.of("UTC")).toLocalDate())
        assertEquals(listOf("local"), window.selectLogs(listOf(f.log("local", local.toInstant().toString()))).logs.map { it.id })
        assertEquals(f.today, TrainingWindow.calendarDate(local.toOffsetDateTime().toString(), f.zone))
    }

    @Test
    fun `actualDate manda sobre date y no se rescata una fecha real corrupta`() {
        val window = TrainingWindow.last28Days(f.zone, f.today)
        val logs = listOf(
            f.log("movido", f.today.minusDays(40).toString()).copy(actualDate = f.today.toString()),
            f.log("corrupto").copy(actualDate = "ilegible"),
        )
        val result = window.selectLogs(logs)
        assertEquals(listOf("movido"), result.logs.map { it.id })
        assertEquals(1, result.unassignedCount)
    }

    @Test
    fun `la fecha asigna los ciclos historicos aunque el weekId coincida con otra semana`() {
        val weeks = listOf(f.week("w1", f.today.minusWeeks(1)), f.week("w2", f.today))
        val program = f.program(weeks).copy(runState = ProgramRunState("actual", cycleNumber = 4))
        val window = TrainingWindow.forProgramScope(program, listOf(weeks.first()), false, f.zone, f.today)
        val logs = listOf(
            f.log("historico", weeks.first().startDate!!, weekId = "w2").copy(programRunId = "anterior", cycleNumber = 1),
            f.log("otra-fecha", weekId = "w1").copy(cycleNumber = 4),
        )
        assertEquals(listOf("historico"), window.selectLogs(logs).logs.map { it.id })
    }

    @Test
    fun `un weekId de plantilla sin ciclo no asigna un registro sin fecha`() {
        val weeks = listOf(f.week("w1", f.today))
        val window = TrainingWindow.forProgramScope(f.program(weeks), weeks, false, f.zone, f.today)
        val result = window.selectLogs(listOf(f.log("ambiguo", "", weekId = "w1")))
        assertTrue(result.logs.isEmpty())
        assertEquals(1, result.unassignedCount)
    }

    @Test
    fun `una instancia con run y ciclo inequivocos permite asignar un registro sin fecha`() {
        val weeks = listOf(f.week("w1", f.today))
        val program = f.program(weeks).copy(runState = ProgramRunState("run", cycleNumber = 3, weekId = "w1"))
        val window = TrainingWindow.forProgramScope(program, weeks, false, f.zone, f.today)
        val log = f.log("asignado", "", weekId = "w1").copy(
            programRunId = "run", cycleNumber = 3, weekInstanceId = ProgramProgressEngine.instanceIdFor(3, "w1"),
        )
        assertEquals(listOf(log), window.selectLogs(listOf(log)).logs)
        assertEquals(0, window.selectLogs(listOf(log)).unassignedCount)
        assertEquals(1, window.selectLogs(listOf(log.copy(programRunId = "otro"))).unassignedCount)
    }

    @Test
    fun `una semana fechada no ciclica y unica puede asignar su registro sin fecha`() {
        val weeks = listOf(f.week("unica", f.today))
        val program = f.program(weeks).copy(structure = ProgramStructure.COMPLEX, simpleProgramKind = SimpleProgramKind.LINEAR)
        val window = TrainingWindow.forProgramScope(program, weeks, false, f.zone, f.today)
        assertEquals(0, window.selectLogs(listOf(f.log("unico", "", weekId = "unica"))).unassignedCount)
        assertEquals(1, window.selectLogs(listOf(f.log("unico", "", weekId = "unica"))).logs.size)
    }

    @Test
    fun `identidades contradictorias y sesiones repetidas no asignan registros sin fecha`() {
        val first = f.week("w1", f.today, sessions = 1)
        val second = f.week("w2", f.today.plusWeeks(1)).copy(sessions = first.sessions)
        val program = f.program(listOf(first, second)).copy(runState = ProgramRunState("run", cycleNumber = 2))
        val window = TrainingWindow.forProgramScope(program, listOf(first), false, f.zone, f.today)
        val shared = f.log("sesion-compartida", "").copy(programRunId = "run", cycleNumber = 2, sessionId = first.sessions.single().id)
        val conflict = shared.copy(weekId = "w1", weekInstanceId = ProgramProgressEngine.instanceIdFor(1, "w1"))
        assertEquals(2, window.selectLogs(listOf(shared, conflict)).unassignedCount)
    }

    @Test
    fun `la alineacion sin fechas usa el cursor y no la cantidad de sesiones`() {
        val weeks = listOf(f.week("w1", f.today), f.week("w2", f.today.plusWeeks(1)))
            .map { it.copy(startDate = null, endDate = null) }
        val program = f.program(weeks).copy(runState = ProgramRunState("run", cycleNumber = 2, weekId = "w2"))
        val window = TrainingWindow.forProgramScope(program, weeks, true, f.zone, f.today)
        assertEquals(LocalDate.of(2026, 10, 12), window.start)
        assertEquals(LocalDate.of(2026, 10, 25), window.endInclusive)
        assertEquals(2.0, window.weekDivisor, 0.0)
    }

    @Test
    fun `dos referencias a la misma instancia antigua no ocultan su ciclo`() {
        val weeks = listOf(f.week("w1", f.today))
        val program = f.program(weeks).copy(
            structure = ProgramStructure.COMPLEX,
            runState = ProgramRunState("run", cycleNumber = 3),
        )
        val window = TrainingWindow.forProgramScope(program, weeks, false, f.zone, f.today)
        val oldInstance = ProgramProgressEngine.instanceIdFor(1, "w1")
        val log = f.log("antiguo", "", weekId = oldInstance).copy(programRunId = "run", weekInstanceId = oldInstance)
        assertEquals(1, window.selectLogs(listOf(log)).unassignedCount)
    }

    @Test
    fun `un weekId duplicado necesita contexto de jerarquia incluso con ciclo conocido`() {
        val first = f.week("repetida", f.today)
        val second = f.week("repetida", f.today.plusWeeks(1))
        val program = f.program(listOf(first)).let { base ->
            val macro = base.macrocycles.single()
            val block = macro.blocks.single()
            val meso = block.mesocycles.single()
            base.copy(
                macrocycles = listOf(macro.copy(blocks = listOf(block.copy(mesocycles = listOf(meso, meso.copy(id = "meso2", weeks = listOf(second))))))),
                runState = ProgramRunState("run", cycleNumber = 2),
            )
        }
        val window = TrainingWindow.forProgramScope(program, listOf(second), false, f.zone, f.today)
        val log = f.log("ambiguo", "", weekId = "repetida").copy(programRunId = "run", cycleNumber = 2)
        assertEquals(1, window.selectLogs(listOf(log)).unassignedCount)
        assertEquals(0, window.selectLogs(listOf(log.copy(macroIndex = 0, mesoIndex = 1))).unassignedCount)
        assertEquals(1, window.selectLogs(listOf(log.copy(macroIndex = 0, mesoIndex = 1))).logs.size)
    }
}
