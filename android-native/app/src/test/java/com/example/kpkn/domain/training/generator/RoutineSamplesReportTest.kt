package com.example.kpkn.domain.training.generator

import com.example.kpkn.data.models.CardioIntensity
import com.example.kpkn.data.models.CardioType
import com.example.kpkn.data.models.Exercise
import com.example.kpkn.data.models.Program
import com.example.kpkn.data.models.Session
import com.example.kpkn.domain.onboarding.CapabilityLevel
import com.example.kpkn.domain.onboarding.CapabilitySkill
import com.example.kpkn.domain.onboarding.LiftMark
import com.example.kpkn.domain.onboarding.MuscleSymbol
import com.example.kpkn.domain.training.CardioPreference
import java.io.File
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * Escribe `build/reports/routine-generator/samples.txt`: rutinas completas de escenarios representativos, para revisarlas
 * como las revisaría un entrenador (orden, series, descansos, superseries, cardio, movilidad y notas). No juzga nada.
 */
class RoutineSamplesReportTest {

    private val s = RoutineTestSupport

    private val dayNames = listOf("", "Lun", "Mar", "Mié", "Jue", "Vie", "Sáb", "Dom")

    private fun describe(exercise: Exercise): String {
        exercise.cardioDetails?.let { cardio ->
            val minutes = cardio.effectiveDurationSeconds() / 60
            val kind = if (cardio.intervalBlocks.isNotEmpty()) "intervalos" else "continuo"
            return "${exercise.name} $minutes min ($kind, ${cardio.type})"
        }
        val sets = exercise.sets
        val first = sets.firstOrNull()
        val reps = first?.targetRepsRange?.format() ?: first?.targetDuration?.let { "${it}s" } ?: "?"
        val effort = first?.targetRIR?.let { "RIR $it" } ?: ""
        val load = first?.weight?.let { " · ${it} kg (${first.targetPercentageRM}%)" } ?: if (exercise.loadReference != null) " · carga pendiente" else ""
        val variant = exercise.variantName?.let { " [$it]" } ?: ""
        val superset = exercise.supersetGroupRef?.let { " ⟷${it.takeLast(3)}" } ?: ""
        val warm = if (exercise.warmupSets.isNotEmpty()) " · ${exercise.warmupSets.size} aprox." else ""
        return "${exercise.name}$variant  ${sets.size} × $reps $effort · desc ${exercise.restTime}s$load$superset$warm  (${exercise.catalogConfigurationId})"
    }

    private fun describe(session: Session, minutes: Int, main: Boolean, place: String): String = buildString {
        appendLine("  [${dayNames[session.dayOfWeek ?: 0]}] ${session.name} · $minutes min${if (main) " · PRINCIPAL" else ""} $place — ${session.focus}")
        session.parts.filter { it.isMobilityGroup }.forEach { part ->
            appendLine("      movilidad: ${part.mobilitySeries.size} movimientos, ${part.mobilitySeries.sumOf { (it.durationSeconds ?: 0) * it.sets } / 60} min (${part.mobilitySeries.take(3).joinToString(", ") { it.name }}…)")
        }
        session.exercises.forEachIndexed { index, exercise -> appendLine("      ${index + 1}. ${describe(exercise)}") }
        session.parts.filter { it.isCardioGroup }.forEach { part ->
            part.exercises.forEach { appendLine("      cardio: ${describe(it)}") }
        }
        session.supersetGroups.forEach { group -> appendLine("      superserie ${group.id.takeLast(3)}: entre ${group.restBetweenExercises}s, tras ronda ${group.restAfterSuperset}s") }
    }

    private fun render(title: String, request: RoutineRequest, out: StringBuilder) {
        val routine = RoutineGenerator.generate(request)
        out.appendLine("=== $title")
        out.appendLine("  ${routine.summary.suggestedName} · ${routine.summary.oneLiner}")
        routine.program.macrocycles.single().blocks.single().mesocycles.single().weeks.single().sessions.forEachIndexed { index, session ->
            val report = routine.report.sessions[index]
            out.append(describe(session, report.estimatedMinutes, session.isMainSession, report.place?.name?.let { "($it)" } ?: ""))
        }
        out.appendLine("  volumen directo/semana: " + routine.report.weeklyDirectSets.entries.sortedByDescending { it.value }.joinToString(", ") { "${it.key} ${"%.0f".format(it.value)}/${routine.report.weeklyDirectCeilings[it.key]}" })
        out.appendLine("  patrones sin cubrir: ${routine.report.patternsMissing.map { it.name }}")
        routine.notes.forEach { out.appendLine("  nota: $it") }
        routine.summary.reasons.forEach { out.appendLine("  razón: $it") }
        out.appendLine()
    }

    @Test
    fun writes_sample_routines_for_review() {
        val out = StringBuilder()
        val strength = RoutineMode.GENERAL_STRENGTH_MUSCLE
        val hybrid = RoutineMode.GENERAL_HYBRID
        val functional = RoutineMode.GENERAL_FUNCTIONAL
        render("gimnasio intermedio fuerza y masa 4d 60 min (fresco jueves)", s.request(s.gym, strength, RoutineLevel.INTERMEDIATE, 4, 60, freshest = 4), out)
        render("gimnasio novato fuerza y masa 3d 45 min", s.request(s.gym, strength, RoutineLevel.NOVICE, 3, 45), out)
        render("solo cuerpo intermedio fuerza y masa 5d 60 min", s.request(s.bodyOnly, strength, RoutineLevel.INTERMEDIATE, 5, 60), out)
        render(
            "parque intermedio funcional 3d 45 min, dominadas aún no",
            s.request(s.park, functional, RoutineLevel.INTERMEDIATE, 3, 45, capabilities = mapOf(CapabilitySkill.PULL_UP to CapabilityLevel.NONE, CapabilitySkill.PUSH_UP to CapabilityLevel.SOME)),
            out,
        )
        render(
            "casa mancuernas+banda retorno híbrido 4d 60 min con cardio de 15",
            s.request(s.homeDumbbellsBand, hybrid, RoutineLevel.RETURNING, 4, 60, cardio = CardioPreference(CardioType.WALK, 15, CardioIntensity.BAJA)),
            out,
        )
        render(
            "casa barra avanzado fuerza y masa 6d 90 min con prioridades y marcas",
            s.request(
                s.homeBarbell, strength, RoutineLevel.ADVANCED, 6, 90,
                priorities = listOf(MuscleSymbol.BACK, MuscleSymbol.SHOULDERS),
                marks = mapOf(LiftMark.SQUAT to 160.0, LiftMark.BENCH to 110.0, LiftMark.DEADLIFT to 200.0, LiftMark.OVERHEAD_PRESS to 70.0),
            ),
            out,
        )
        render("gimnasio intermedio funcional 5d 90 min", s.request(s.gym, functional, RoutineLevel.INTERMEDIATE, 5, 90), out)
        render("gimnasio+casa intermedio fuerza y masa 4d 60 min", s.request(s.gymAndHome, strength, RoutineLevel.INTERMEDIATE, 4, 60), out)
        render("gimnasio avanzado híbrido 3d 150 min", s.request(s.gym, hybrid, RoutineLevel.ADVANCED, 3, 150), out)
        render("solo cuerpo novato fuerza y masa 1d 20 min", s.request(s.bodyOnly, strength, RoutineLevel.NOVICE, 1, 20), out)
        render("gimnasio intermedio fuerza y masa 7d 30 min", s.request(s.gym, strength, RoutineLevel.INTERMEDIATE, 7, 30), out)
        render("gimnasio novato funcional 2d 30 min", s.request(s.gym, functional, RoutineLevel.NOVICE, 2, 30), out)
        render("gimnasio avanzado fuerza y masa 2d 180 min", s.request(s.gym, strength, RoutineLevel.ADVANCED, 2, 180), out)
        // Paquete D1b: el catálogo ampliado (peso corporal BW-1) y el material acreditado (anillas, cajón, sala de máquinas, extras de gimnasio).
        render("D1b solo cuerpo novato fuerza y masa 4d 45 min", s.request(s.bodyOnly, strength, RoutineLevel.NOVICE, 4, 45), out)
        render(
            "D1b solo cuerpo avanzado fuerza y masa 4d 60 min (varias flexiones y dominadas)",
            s.request(
                s.bodyOnly, strength, RoutineLevel.ADVANCED, 4, 60,
                capabilities = mapOf(CapabilitySkill.PUSH_UP to CapabilityLevel.MANY, CapabilitySkill.PULL_UP to CapabilityLevel.MANY),
            ),
            out,
        )
        render("D1b parque intermedio fuerza y masa 4d 60 min", s.request(s.park, strength, RoutineLevel.INTERMEDIATE, 4, 60), out)
        render(
            "D1b parque avanzado fuerza y masa 3d 60 min (pistol y fondos: algunas)",
            s.request(
                s.park, strength, RoutineLevel.ADVANCED, 3, 60,
                capabilities = mapOf(CapabilitySkill.PISTOL_SQUAT to CapabilityLevel.SOME, CapabilitySkill.DIP to CapabilityLevel.SOME),
            ),
            out,
        )
        render("D1b casa con anillas y cajón intermedio fuerza y masa 4d 60 min", s.request(s.homeRingsBox, strength, RoutineLevel.INTERMEDIATE, 4, 60), out)
        render("D1b casa mancuernas+banda intermedio fuerza y masa 4d 60 min", s.request(s.homeDumbbellsBand, strength, RoutineLevel.INTERMEDIATE, 4, 60), out)
        render("D1b gimnasio (sala de máquinas) novato fuerza y masa 4d 60 min", s.request(s.gym, strength, RoutineLevel.NOVICE, 4, 60), out)
        render("D1b gimnasio avanzado fuerza y masa 5d 90 min, otra versión (semilla 2)", s.request(s.gym, strength, RoutineLevel.ADVANCED, 5, 90, seed = 2), out)
        val marks = mapOf(LiftMark.SQUAT to 140.0, LiftMark.BENCH to 100.0, LiftMark.DEADLIFT to 180.0, LiftMark.OVERHEAD_PRESS to 65.0)
        render("DISCIPLINA calistenia intermedio 4d 60 min (gimnasio, sin pesas)", s.request(s.gym, RoutineMode.DISCIPLINE_CALISTHENICS, RoutineLevel.INTERMEDIATE, 4, 60), out)
        render("DISCIPLINA armwrestling intermedio 4d 75 min", s.request(s.gym, RoutineMode.DISCIPLINE_ARMWRESTLING, RoutineLevel.INTERMEDIATE, 4, 75), out)
        render("DISCIPLINA strongman intermedio 4d 90 min", s.request(s.gym, RoutineMode.DISCIPLINE_STRONGMAN, RoutineLevel.INTERMEDIATE, 4, 90, marks = marks), out)
        render("DISCIPLINA base de halterofilia intermedio 3d 60 min", s.request(s.gym, RoutineMode.DISCIPLINE_WEIGHTLIFTING_BASE, RoutineLevel.INTERMEDIATE, 3, 60, marks = marks), out)
        // Paquete D1b · lote OL-1 (levantamientos olímpicos y acarreos): las marcas del arranque y de los dos tiempos se preguntan desde el nivel intermedio.
        val olympicMarks = marks + mapOf(LiftMark.SNATCH to 80.0, LiftMark.CLEAN_AND_JERK to 100.0)
        render("OL-1 base de halterofilia avanzado 4d 90 min con marcas de arranque y dos tiempos (gimnasio)", s.request(s.gym, RoutineMode.DISCIPLINE_WEIGHTLIFTING_BASE, RoutineLevel.ADVANCED, 4, 90, marks = olympicMarks), out)
        render("OL-1 base de halterofilia intermedio 1d 60 min (gimnasio)", s.request(s.gym, RoutineMode.DISCIPLINE_WEIGHTLIFTING_BASE, RoutineLevel.INTERMEDIATE, 1, 60), out)
        render("OL-1 base de halterofilia intermedio 5d 75 min (casa con barra, rack y banco)", s.request(s.homeBarbell, RoutineMode.DISCIPLINE_WEIGHTLIFTING_BASE, RoutineLevel.INTERMEDIATE, 5, 75), out)
        render("OL-1 base de halterofilia novato 3d 60 min (gimnasio)", s.request(s.gym, RoutineMode.DISCIPLINE_WEIGHTLIFTING_BASE, RoutineLevel.NOVICE, 3, 60), out)
        render("OL-1 base de halterofilia intermedio 3d 60 min (casa con mancuernas y banda: sin barra)", s.request(s.homeDumbbellsBand, RoutineMode.DISCIPLINE_WEIGHTLIFTING_BASE, RoutineLevel.INTERMEDIATE, 3, 60), out)
        render("OL-1 strongman avanzado 5d 90 min (gimnasio)", s.request(s.gym, RoutineMode.DISCIPLINE_STRONGMAN, RoutineLevel.ADVANCED, 5, 90, marks = olympicMarks), out)
        render("OL-1 strongman novato 4d 60 min (casa con mancuernas y banda)", s.request(s.homeDumbbellsBand, RoutineMode.DISCIPLINE_STRONGMAN, RoutineLevel.NOVICE, 4, 60), out)
        render("DISCIPLINA powerlifting avanzado 4d 90 min con marcas (casa con barra)", s.request(s.homeBarbell, RoutineMode.CUSTOM_POWERLIFTING, RoutineLevel.ADVANCED, 4, 90, marks = marks), out)
        render("DISCIPLINA powerbuilding intermedio 4d 75 min", s.request(s.gym, RoutineMode.CUSTOM_POWERBUILDING, RoutineLevel.INTERMEDIATE, 4, 75, marks = marks), out)
        render("DISCIPLINA culturismo intermedio 5d 75 min", s.request(s.gym, RoutineMode.CUSTOM_BODYBUILDING, RoutineLevel.INTERMEDIATE, 5, 75), out)
        val file = File("build/reports/routine-generator/samples.txt")
        file.parentFile.mkdirs()
        file.writeText(out.toString())
        assertTrue(file.length() > 2000)
    }
}
