package com.example.kpkn.domain.training.generator

import java.io.File
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * Escribe `build/reports/routine-generator/matrix.txt`: por perfil de material, qué patrones y músculos NO se pudieron
 * cubrir (insumo para las altas del catálogo) y los minutos reales por celda (días × minutos × modo × nivel).
 * No juzga: el barrido ([RoutineGeneratorSweepTest]) es el que falla; esta prueba solo falla si no puede escribir el informe.
 *
 * Un patrón «falta siempre» cuando ninguna celda de ese modo lo cubre (el material o el catálogo no tienen ejercicio para él);
 * «falta a veces» cuando solo algunas celdas no lo cubren (p. ej. con 20 min no caben todos los huecos o, con varios lugares,
 * un lugar no tiene material para él).
 */
class RoutineMatrixReportTest {

    private val s = RoutineTestSupport

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
        out.appendLine("perfil | patrones que NO se cubren nunca (en ningún modo) | patrones que faltan a veces | sesiones fuera de ventana | peor desvío (min)")
        s.profiles.forEach { profile ->
            val mine = cells.filter { it.profile == profile }
            val alwaysByMode = s.generalModes.map { mode -> gaps(mine.filter { it.mode == mode }).first }
            val neverInAnyMode = alwaysByMode.reduce { a, b -> a intersect b }
            val everInAnyMode = alwaysByMode.flatten().toSet()
            val sometimes = mine.flatMap { it.routine.report.patternsMissing }.toSet() - everInAnyMode
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
        println(out.lines().take(14).joinToString("\n"))
        assertTrue(file.exists() && file.length() > 1000)
    }
}
