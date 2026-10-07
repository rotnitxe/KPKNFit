package com.example.kpkn.domain.training.generator

import com.example.kpkn.data.exercises.catalogv2.toConfigurationDisplayNameLookup
import com.example.kpkn.data.exercises.catalogv2.catalogV2SelectionIssues
import com.example.kpkn.data.models.Program
import com.example.kpkn.data.models.Session
import com.example.kpkn.domain.training.ProgramExecutionContract
import com.example.kpkn.domain.training.SessionDurationEstimator
import java.io.File
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * Barrido determinista del generador: días 1–7 × minutos {20…180} × nivel × los tres modos generales × los seis perfiles de
 * material. Cada combinación debe producir un programa EJECUTABLE que respete el contrato del brief D1:
 * - días de la semana respetados y una sesión por día;
 * - minutos dentro de [85 %, 110 %] del objetivo (y ≥ 20), o la mejor aproximación con una nota que lo diga;
 * - cada sesión de fuerza ≥ 3 ejercicios, sin repetidos; identidad de catálogo v2 completa y nombres del catálogo;
 * - series directas semanales por músculo dentro de su techo (min de MAV y MRV por nivel);
 * - la sesión principal en el día más fresco (o el primer día de entreno posterior);
 * - un día = cuerpo completo; siete días no son siete sesiones de pesas;
 * - cada ejercicio es ejecutable con el material del LUGAR de su día (nunca se mezclan lugares).
 */
class RoutineGeneratorSweepTest {

    private val catalog get() = RoutineTestSupport.catalog
    private val entries by lazy { GeneratorCatalog.of(catalog) }
    private val displayNames by lazy { catalog.toConfigurationDisplayNameLookup() }

    private fun sessionsOf(program: Program): List<Session> =
        program.macrocycles.single().blocks.single().mesocycles.single().weeks.single().sessions

    private fun freshestFor(counter: Int, days: List<Int>): Int? = when (counter % 4) {
        0 -> null
        1 -> days.first()
        2 -> days.last()
        else -> (1..7).firstOrNull { it !in days } ?: days.first()
    }

    /** Un problema del barrido con su categoría (para el resumen: cuántos hay de cada tipo y dos ejemplos). */
    private data class Problem(val category: String, val text: String)

    private operator fun ArrayList<Problem>.plusAssign(text: String) {
        // La categoría es lo que sigue al rótulo de la celda («…180min: <categoría>»), con las cifras sustituidas por «#».
        val category = text.substringAfter("min: ", text).replace(Regex("[0-9]+([.][0-9]+)?"), "#").take(46)
        add(Problem(category, text))
    }

    private fun check(profile: MaterialProfile, request: RoutineRequest, routine: GeneratedRoutine): List<Problem> {
        val problems = ArrayList<Problem>()
        val label = "${profile.id} ${request.mode.name} ${request.level.name} ${request.weekdays.size}d ${request.targetMinutes}min"
        val program = routine.program
        val sessions = sessionsOf(program)
        val contract = ProgramExecutionContract.validate(program)
        if (contract.isNotEmpty()) problems += "$label: no ejecutable (${contract.map { it.message }})"
        if (sessions.mapNotNull { it.dayOfWeek }.sorted() != request.weekdays.sorted()) problems += "$label: días ${sessions.map { it.dayOfWeek }} ≠ ${request.weekdays}"
        if (sessions.size != request.weekdays.size) problems += "$label: ${sessions.size} sesiones para ${request.weekdays.size} días"

        // Sesión principal.
        val mains = sessions.filter { it.isMainSession }
        if (mains.size != 1) problems += "$label: ${mains.size} sesiones principales"
        else if (mains.single().dayOfWeek != routine.summary.mainSessionDay) problems += "$label: la principal no coincide con el resumen"
        RoutineTestSupport.expectedMainDay(request.weekdays, request.freshestDay)?.let { expected ->
            if (routine.summary.mainSessionDay != expected) problems += "$label: principal en ${routine.summary.mainSessionDay}, esperaba $expected (fresco ${request.freshestDay})"
        }

        // Minutos.
        val window = request.targetMinutes.let { RoutineTestSupport.window(it) }
        sessions.forEachIndexed { index, session ->
            val minutes = SessionDurationEstimator.estimate(session).totalMinutes
            if (routine.report.sessionMinutes[index] != minutes) problems += "$label: informe ${routine.report.sessionMinutes[index]} ≠ estimador $minutes"
            if (session.targetDurationMinutes != minutes) problems += "$label: duración sellada ${session.targetDurationMinutes} ≠ $minutes"
            if (minutes !in window && routine.report.sessions[index].inWindow) problems += "$label: ${session.name} marcada en ventana con $minutes min"
        }
        if (routine.report.minutesMisses > 0 && routine.notes.none { it.startsWith("Tiempo:") }) {
            problems += "$label: ${routine.report.minutesMisses} sesiones fuera de ventana sin nota"
        }

        // Contenido de cada sesión.
        sessions.forEachIndexed { index, session ->
            val report = routine.report.sessions[index]
            val exercises = session.allExercises()
            val ids = exercises.mapNotNull { it.catalogConfigurationId }
            if (ids.size != ids.distinct().size) problems += "$label: ${session.name} repite ejercicios $ids"
            val definitions = exercises.mapNotNull { it.catalogDefinitionId }
            if (definitions.size != definitions.distinct().size) problems += "$label: ${session.name} repite definiciones"
            when (report.kind) {
                RoutineSessionKind.STRENGTH -> if (report.strengthExerciseCount < 3) problems += "$label: ${session.name} solo ${report.strengthExerciseCount} ejercicios de fuerza"
                RoutineSessionKind.MIXED -> if (exercises.size < 3) problems += "$label: ${session.name} mixta con ${exercises.size} ejercicios"
                else -> Unit
            }
            ids.forEach { id -> if (entries.entry(id) == null) problems += "$label: ${session.name} cita $id que no existe" }
            val issues = session.catalogV2SelectionIssues(displayNames)
            if (issues.isNotEmpty()) problems += "$label: ${session.name} rompe el cortafuegos del catálogo: ${issues.take(2)}"
            // Material del lugar del día.
            val availability = profile.byPlace[report.place] ?: profile.availability
            val equipment = DayEquipment(availability)
            exercises.filter { it.cardioDetails == null }.forEach { exercise ->
                val entry = entries.entry(exercise.catalogConfigurationId ?: return@forEach) ?: return@forEach
                if (!equipment.allows(entry, emptyList())) {
                    problems += "$label: ${session.name} (${report.place}) usa ${entry.id} que su lugar no permite"
                }
            }
        }

        // Aproximación: mientras `ApproachPlanner` sea la identidad nada lleva series de aproximación y no hay nada que exigir; en
        // cuanto aterrice (algún ejercicio con `warmupSets`), el primer compuesto pesado de una sesión de fuerza debe llevarlas.
        val approachActive = sessions.any { session -> session.allExercises().any { it.warmupSets.isNotEmpty() } }
        if (approachActive) {
            sessions.forEachIndexed { index, session ->
                val first = session.exercises.firstOrNull() ?: return@forEachIndexed
                val entry = first.catalogConfigurationId?.let { entries.entry(it) } ?: return@forEachIndexed
                val loadable = entry.tier == EquipmentTier.BARBELL || entry.tier == EquipmentTier.DUMBBELL || entry.tier == EquipmentTier.MACHINE
                if (routine.report.sessions[index].kind == RoutineSessionKind.STRENGTH && entry.isCompound && loadable &&
                    first.sets.firstOrNull()?.targetRepsRange?.let { it.max <= 6 } == true && first.warmupSets.isEmpty()
                ) {
                    problems += "$label: ${session.name} abre con ${entry.id} pesado sin series de aproximación"
                }
            }
        }

        // Volumen.
        routine.report.weeklyDirectSets.forEach { (muscle, sets) ->
            val ceiling = routine.report.weeklyDirectCeilings[muscle]
            if (ceiling != null && sets > ceiling + 1e-6) problems += "$label: $muscle $sets series directas > techo $ceiling"
        }

        // Un día = cuerpo completo; siete días no son siete sesiones de pesas.
        if (request.weekdays.size == 1) {
            val patterns = routine.report.sessions.single().patterns.toSet()
            val lower = patterns.any { it in setOf(RoutinePattern.SQUAT, RoutinePattern.HINGE, RoutinePattern.SINGLE_LEG, RoutinePattern.GLUTE) }
            val upper = patterns.any { it in setOf(RoutinePattern.HORIZONTAL_PUSH, RoutinePattern.VERTICAL_PUSH, RoutinePattern.HORIZONTAL_PULL, RoutinePattern.VERTICAL_PULL) }
            if (!lower || !upper) problems += "$label: un día sin cuerpo completo (patrones $patterns)"
        }
        if (request.weekdays.size == 7) {
            val liftingDays = routine.report.sessions.count { it.kind == RoutineSessionKind.STRENGTH || it.kind == RoutineSessionKind.MIXED }
            if (liftingDays > 6) problems += "$label: siete días de pesas"
        }
        if (routine.report.patternsMissing.isNotEmpty() && routine.notes.isEmpty()) problems += "$label: huecos ${routine.report.patternsMissing} sin nota"
        return problems
    }

    @Test
    fun every_combination_is_executable_and_meets_the_contract() {
        val started = System.nanoTime()
        val problems = ArrayList<Problem>()
        var counter = 0
        var routines = 0
        var worstMs = 0L
        for (profile in RoutineTestSupport.profiles) {
            for (mode in RoutineTestSupport.generalModes) {
                for (dayCount in 1..7) {
                    for (level in RoutineTestSupport.levels) {
                        for (minutes in RoutineTestSupport.minutes) {
                            val request = RoutineTestSupport.request(
                                profile, mode, level, dayCount, minutes,
                                freshest = freshestFor(counter++, RoutineTestSupport.weekdays(dayCount)),
                            )
                            val t0 = System.nanoTime()
                            val routine = try {
                                RoutineGenerator.generate(request)
                            } catch (t: Throwable) {
                                problems += Problem("EXCEPCIÓN", "${profile.id} ${mode.name} ${level.name} ${dayCount}d ${minutes}min: EXCEPCIÓN ${t::class.simpleName}: ${t.message}")
                                continue
                            }
                            worstMs = maxOf(worstMs, (System.nanoTime() - t0) / 1_000_000)
                            routines++
                            problems += check(profile, request, routine)
                        }
                    }
                }
            }
        }
        val elapsedSeconds = (System.nanoTime() - started) / 1_000_000_000.0
        println("Barrido: $routines rutinas en ${"%.1f".format(elapsedSeconds)} s (peor ${worstMs} ms); problemas: ${problems.size}")
        val report = File("build/reports/routine-generator/sweep-problems.txt")
        report.parentFile.mkdirs()
        report.writeText(problems.joinToString("\n") { it.text })
        val summary = problems.groupBy { it.category }.entries.sortedByDescending { it.value.size }.take(12).joinToString("\n") { (category, items) ->
            "  [${items.size}] $category\n      p. ej.: ${items.take(2).joinToString(" | ") { it.text }}"
        }
        assertTrue("problemas del barrido (${problems.size}; todos en ${report.path}):\n$summary", problems.isEmpty())
        assertTrue("el barrido debe ser rápido (< 90 s) y tardó $elapsedSeconds s", elapsedSeconds < 90.0)
    }

    @Test
    fun the_window_helper_matches_the_documented_rule() {
        assertEquals(20..22, RoutineTestSupport.window(20))
        assertEquals(26..33, RoutineTestSupport.window(30))
        assertEquals(39..49, RoutineTestSupport.window(45))
        assertEquals(51..66, RoutineTestSupport.window(60))
        assertEquals(153..198, RoutineTestSupport.window(180))
    }
}
