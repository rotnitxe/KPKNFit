package com.example.kpkn.domain.training.generator

import com.example.kpkn.data.exercises.catalogv2.catalogV2SelectionIssues
import com.example.kpkn.data.exercises.catalogv2.toConfigurationDisplayNameLookup
import com.example.kpkn.data.models.Program
import com.example.kpkn.data.models.Session
import com.example.kpkn.domain.training.ProgramExecutionContract
import com.example.kpkn.domain.training.SessionDurationEstimator

/**
 * El contrato del brief D1 aplicado a UNA rutina generada (lo usan el barrido de los modos generales y el de las
 * disciplinas): ejecutable, días respetados, minutos dentro de la ventana (o con nota), sesiones de fuerza con ≥ 3 ejercicios sin
 * repetidos, identidad de catálogo v2 completa, material del lugar de cada día, techos de volumen, sesión principal en el día
 * más fresco, un día = cuerpo completo y siete días ≠ siete sesiones de pesas.
 */
internal object RoutineContractChecks {

    private val entries by lazy { GeneratorCatalog.of(RoutineTestSupport.catalog) }
    private val displayNames by lazy { RoutineTestSupport.catalog.toConfigurationDisplayNameLookup() }

    private fun sessionsOf(program: Program): List<Session> =
        program.macrocycles.single().blocks.single().mesocycles.single().weeks.single().sessions

    fun problems(profile: MaterialProfile, request: RoutineRequest, routine: GeneratedRoutine): List<String> {
        val problems = ArrayList<String>()
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
}
