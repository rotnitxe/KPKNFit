package com.example.kpkn.domain.training.generator

import java.io.File
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * Escribe `build/reports/routine-generator/matrix.txt`: por perfil de material, qué patrones y músculos NO se pudieron
 * cubrir (insumo para las altas del catálogo), cuántos ejercicios lleva cada sesión de fuerza, cuánto del catálogo alcanzable
 * se usa y los minutos reales por celda (días × minutos × modo × nivel).
 * No juzga: el barrido ([RoutineGeneratorSweepTest]) es el que falla; esta prueba solo falla si no puede escribir el informe.
 *
 * Un patrón «falta siempre» cuando ninguna celda de ese modo lo cubre (el material o el catálogo no tienen ejercicio para él);
 * «falta a veces» cuando solo algunas celdas no lo cubren (p. ej. con 20 min no caben todos los huecos o, con varios lugares,
 * un lugar no tiene material para él).
 */
class RoutineMatrixReportTest {

    private val s = RoutineTestSupport
    private val index by lazy { GeneratorCatalog.of(s.catalog) }

    private val referenceMuscles = listOf(
        "Pectorales", "Dorsales", "Deltoides", "Bíceps", "Tríceps", "Cuádriceps", "Isquiosurales",
        "Glúteos", "Pantorrillas", "Abdomen", "Trapecio", "Antebrazo",
    )

    private data class Cell(
        val profile: MaterialProfile,
        val mode: RoutineMode,
        val level: RoutineLevel,
        val days: Int,
        val minutes: Int,
        val routine: GeneratedRoutine,
    ) {
        val label: String get() = "${mode.name.removePrefix("GENERAL_").lowercase()} ${level.name.lowercase()} ${days}d ${minutes}min"
    }

    /** Patrones que faltan en todas las celdas de [cells] y los que faltan solo en algunas (con cuántas). */
    private fun gaps(cells: List<Cell>): Pair<Set<RoutinePattern>, Map<RoutinePattern, Int>> {
        val counts = HashMap<RoutinePattern, Int>()
        cells.forEach { cell -> cell.routine.report.patternsMissing.forEach { counts[it] = (counts[it] ?: 0) + 1 } }
        val always = counts.filterValues { it == cells.size }.keys
        val sometimes = counts.filterKeys { it !in always }
        return always to sometimes
    }

    private fun names(patterns: Collection<RoutinePattern>): String = patterns.sortedBy { it.ordinal }.joinToString(", ") { it.label }.ifEmpty { "ninguno" }

    /** Las sesiones con trabajo de fuerza (las de cardio y movilidad están exentas del mínimo de ejercicios). */
    private fun strengthSessions(cell: Cell): List<RoutineSessionReport> =
        cell.routine.report.sessions.filter { it.kind == RoutineSessionKind.STRENGTH || it.kind == RoutineSessionKind.MIXED }

    @Test
    fun writes_the_coverage_matrix_report() {
        val cells = ArrayList<Cell>()
        for (profile in s.profiles) for (mode in s.generalModes) for (days in 1..7) for (level in s.levels) for (minutes in s.minutes) {
            val routine = RoutineGenerator.generate(s.request(profile, mode, level, days, minutes))
            cells += Cell(profile, mode, level, days, minutes, routine)
        }
        val out = StringBuilder()
        out.appendLine("KPKN · matriz de cobertura del generador de rutinas (${RoutineGenerator.REVISION}, catálogo ${s.catalog.catalogRevision})")
        out.appendLine("Celdas: ${cells.size} = ${s.profiles.size} perfiles × ${s.generalModes.size} modos × 7 días × ${s.levels.size} niveles × ${s.minutes.size} minutos")
        out.appendLine()
        out.appendLine("## Resumen por perfil de material")
        out.appendLine("perfil | patrones que NO se cubren nunca (modo fuerza y masa, todas las celdas) | patrones que faltan a veces (cualquier modo) | sesiones fuera de ventana | peor desvío (min)")
        s.profiles.forEach { profile ->
            val mine = cells.filter { it.profile == profile }
            // El modo de fuerza y masa es el que más patrones pide: lo que no cubre NUNCA con ese material es lo que más falta hace.
            val neverInAnyMode = gaps(mine.filter { it.mode == RoutineMode.GENERAL_STRENGTH_MUSCLE }).first
            val sometimes = mine.flatMap { it.routine.report.patternsMissing }.toSet() - neverInAnyMode
            val sessions = mine.sumOf { it.routine.report.sessions.size }
            val misses = mine.sumOf { it.routine.report.minutesMisses }
            val worst = mine.maxOf { cell ->
                cell.routine.report.sessionMinutes.maxOf { m ->
                    val window = cell.routine.report.minutesWindow
                    if (m < window.first) window.first - m else if (m > window.last) m - window.last else 0
                }
            }
            out.appendLine("${profile.id} | ${names(neverInAnyMode)} | ${names(sometimes)} | $misses de $sessions | $worst")
        }
        out.appendLine()

        // Recuento de huecos: lo que permite comparar dos versiones del generador sin leer línea a línea.
        out.appendLine("## Recuento de huecos por perfil (los tres modos)")
        out.appendLine("«siempre» = patrones que ninguna celda de ALGÚN modo cubre; «a veces» = el resto de los que fallan en alguna celda; huecos = suma de patrones sin cubrir de todas las celdas.")
        out.appendLine("perfil | siempre | a veces | huecos (celdas × patrones)")
        s.profiles.forEach { profile ->
            val mine = cells.filter { it.profile == profile }
            val always = LinkedHashSet<RoutinePattern>()
            s.generalModes.forEach { mode -> always += gaps(mine.filter { it.mode == mode }).first }
            val sometimes = mine.flatMap { it.routine.report.patternsMissing }.toSet() - always
            out.appendLine("${profile.id} | ${always.size} | ${sometimes.size} | ${mine.sumOf { it.routine.report.patternsMissing.size }}")
        }
        out.appendLine()

        // Ejercicios por sesión: el síntoma que se ve en pantalla («sesiones delgadas»).
        out.appendLine("## Ejercicios de fuerza por sesión (media de las sesiones de fuerza y mixtas; todos los niveles y días)")
        out.appendLine("perfil | modo | mínimo | media | " + s.minutes.joinToString(" | ") { "$it min" })
        s.profiles.forEach { profile ->
            val mine = cells.filter { it.profile == profile }
            fun row(label: String, rowCells: List<Cell>) {
                val all = rowCells.flatMap { cell -> strengthSessions(cell).map { it.strengthExerciseCount } }
                val byMinutes = s.minutes.joinToString(" | ") { minutes ->
                    val counts = rowCells.filter { it.minutes == minutes }.flatMap { cell -> strengthSessions(cell).map { it.strengthExerciseCount } }
                    if (counts.isEmpty()) "-" else "%.1f".format(counts.average())
                }
                out.appendLine("${profile.id} | $label | ${all.minOrNull() ?: 0} | ${if (all.isEmpty()) "-" else "%.2f".format(all.average())} | $byMinutes")
            }
            s.generalModes.forEach { mode -> row(mode.label, mine.filter { it.mode == mode }) }
            row("los tres modos", mine)
        }
        out.appendLine()

        // Cuántas sesiones quedan fuera de la ventana de minutos y por cuánto: lo que la aproximación y la movilidad obligatorias
        // hacen a las sesiones cortas (el mínimo de ejercicios y la rampa del primer ejercicio no caben en 20 min).
        out.appendLine("## Sesiones fuera de la ventana de minutos, por minutos objetivo (los tres modos, todos los niveles y días)")
        out.appendLine("«n/N (±d)» = sesiones fuera de la ventana / sesiones, y la mayor desviación en minutos.")
        out.appendLine("perfil | " + s.minutes.joinToString(" | ") { "$it min" })
        s.profiles.forEach { profile ->
            val mine = cells.filter { it.profile == profile }
            val row = s.minutes.joinToString(" | ") { minutes ->
                val ofMinutes = mine.filter { it.minutes == minutes }
                val sessions = ofMinutes.sumOf { it.routine.report.sessions.size }
                var outside = 0
                var worst = 0
                ofMinutes.forEach { cell ->
                    val window = cell.routine.report.minutesWindow
                    cell.routine.report.sessionMinutes.forEach { m ->
                        val deviation = if (m < window.first) window.first - m else if (m > window.last) m - window.last else 0
                        if (deviation > 0) outside++
                        worst = maxOf(worst, deviation)
                    }
                }
                if (outside == 0) "0/$sessions" else "$outside/$sessions (±$worst)"
            }
            out.appendLine("${profile.id} | $row")
        }
        out.appendLine()

        // Cuánto del catálogo que el material abre se usa de verdad (con las máquinas: cuántas de las alcanzables).
        out.appendLine("## Uso del catálogo por perfil (barrido de los tres modos)")
        out.appendLine("perfil | configuraciones alcanzables | usadas en el barrido | máquinas alcanzables | máquinas usadas")
        s.profiles.forEach { profile ->
            val mine = cells.filter { it.profile == profile }
            val equipments = profile.byPlace.values.ifEmpty { listOf(profile.availability) }.map { DayEquipment(it) }
            val reachable = index.entries.values.filter { entry -> equipments.any { it.allows(entry, emptyList()) } }
            val used = mine.flatMap { it.routine.report.sessions }.flatMap { it.configurationIds }.toSet()
            val reachableMachines = reachable.count { it.equipmentId == "machine" }
            val usedMachines = used.count { index.entry(it)?.equipmentId == "machine" }
            out.appendLine("${profile.id} | ${reachable.size} | ${used.size} | $reachableMachines | $usedMachines")
        }
        out.appendLine()

        s.profiles.forEach { profile ->
            val mine = cells.filter { it.profile == profile }
            out.appendLine("## ${profile.id}")
            s.generalModes.forEach { mode ->
                val modeCells = mine.filter { it.mode == mode }
                val (always, sometimes) = gaps(modeCells)
                out.appendLine("- ${mode.label}: faltan siempre → ${names(always)}")
                if (sometimes.isNotEmpty()) {
                    out.appendLine("  faltan a veces → " + sometimes.entries.sortedBy { it.key.ordinal }.joinToString(", ") { "${it.key.label} (${it.value} de ${modeCells.size} celdas)" })
                }
                val reference = modeCells.first { it.level == RoutineLevel.INTERMEDIATE && it.days == 5 && it.minutes == 90 }
                val zero = referenceMuscles.filter { (reference.routine.report.weeklyDirectSets[it] ?: 0.0) <= 0.0 }
                val belowMev = referenceMuscles.filter {
                    val sets = reference.routine.report.weeklyDirectSets[it] ?: 0.0
                    val mev = reference.routine.report.weeklyMinimums[it] ?: 0
                    sets > 0.0 && sets < mev
                }
                out.appendLine("  músculos sin series directas (5 d, 90 min, intermedio): ${zero.joinToString(", ").ifEmpty { "ninguno" }}")
                out.appendLine("  músculos por debajo de su volumen mínimo efectivo (mismo caso): ${belowMev.joinToString(", ").ifEmpty { "ninguno" }}")
            }
            out.appendLine("  minutos reales por celda (intermedio; min–máx entre sesiones; * = fuera de ventana):")
            s.generalModes.forEach { mode ->
                out.appendLine("  ${mode.label}")
                out.append("    días\\min ")
                s.minutes.forEach { out.append("%-9d".format(it)) }
                out.appendLine()
                for (days in 1..7) {
                    out.append("    %-8d ".format(days))
                    s.minutes.forEach { minutes ->
                        val cell = mine.first { it.mode == mode && it.level == RoutineLevel.INTERMEDIATE && it.days == days && it.minutes == minutes }
                        val sessions = cell.routine.report.sessionMinutes
                        val flag = if (cell.routine.report.minutesMisses > 0) "*" else ""
                        out.append("%-9s".format("${sessions.min()}–${sessions.max()}$flag"))
                    }
                    out.appendLine()
                }
            }
            val outside = mine.filter { it.routine.report.minutesMisses > 0 }
            out.appendLine("  celdas con alguna sesión fuera de ventana: ${outside.size} de ${mine.size}")
            outside.take(25).forEach { cell ->
                out.appendLine("    ${cell.label}: ${cell.routine.report.sessionMinutes} (ventana ${cell.routine.report.minutesWindow})")
            }
            val topNotes = mine.flatMap { it.routine.notes }.groupingBy { it.take(90) }.eachCount().entries.sortedByDescending { it.value }.take(6)
            out.appendLine("  notas más frecuentes:")
            topNotes.forEach { out.appendLine("    (${it.value}) ${it.key}") }
            out.appendLine()
        }
        val file = File("build/reports/routine-generator/matrix.txt")
        file.parentFile.mkdirs()
        file.writeText(out.toString())
        println(out.lines().take(40).joinToString("\n"))
        assertTrue(file.exists() && file.length() > 1000)
        // Con 45 min o más la rampa y la movilidad del planificador real caben en la ventana: ninguna sesión fuera, en ningún perfil,
        // modo, nivel ni número de días. Con 20 y 30 min los tres ejercicios mínimos (con sus descansos mínimos), el calentamiento y la
        // aproximación pueden pasarse y la rutina lo dice en una nota «Tiempo:» (lo comprueba el barrido).
        val outsideFrom45 = cells.filter { it.minutes >= 45 }.sumOf { it.routine.report.minutesMisses }
        assertEquals("sesiones fuera de la ventana con 45 min o más", 0, outsideFrom45)
    }
}
