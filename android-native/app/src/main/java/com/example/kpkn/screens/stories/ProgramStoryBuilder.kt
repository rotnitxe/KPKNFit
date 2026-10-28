package com.example.kpkn.screens.stories

import com.example.kpkn.data.models.BlockProgressionScheme
import com.example.kpkn.data.models.Program
import com.example.kpkn.data.models.ProgramWeek
import com.example.kpkn.data.models.Session
import com.example.kpkn.data.models.WorkoutLog
import com.example.kpkn.data.models.isSimpleProgram
import com.example.kpkn.data.models.totalProgramWeeks
import com.example.kpkn.domain.training.ProgramDetailHelpers
import com.example.kpkn.domain.training.progress.LiftTrend
import com.example.kpkn.domain.training.progress.LiftTrendEngine
import com.example.kpkn.screens.onboarding.design.entreno.plan.cover.LineCoverBackground
import com.example.kpkn.screens.onboarding.design.entreno.plan.cover.LineCoverSpec
import com.example.kpkn.screens.onboarding.design.entreno.plan.cover.LineCoverStyle
import com.example.kpkn.screens.onboarding.design.entreno.plan.cover.coverIdentityForProgram
import com.example.kpkn.screens.programdetail.components.focusModeLabel
import com.example.kpkn.screens.programdetail.components.getDynamicDays
import com.example.kpkn.screens.programdetail.design.structure.blockProgressionLabel
import com.example.kpkn.screens.programs.programRibbonOf
import java.time.LocalDate
import kotlin.math.roundToInt

/**
 * Arma los datos de las historias de un programa a partir de [Program] y de sus registros ([WorkoutLog]): las plantillas que se
 * ofrecen ([templates]) y el contenido de cada una ([build]). Nunca usa pesos corporales, molestias ni notas.
 */
internal object ProgramStoryBuilder {

    /** Letras de los días, de lunes (id 1) a domingo (id 7). */
    private val dayLetters = listOf("L", "M", "X", "J", "V", "S", "D")

    /** Meses en español y en minúsculas, para el eyebrow de Progreso (no depende del idioma del dispositivo). */
    private val monthsEs = listOf(
        "enero", "febrero", "marzo", "abril", "mayo", "junio",
        "julio", "agosto", "septiembre", "octubre", "noviembre", "diciembre",
    )

    /** El prefijo «Bloque N · » de un bloque, con «·», «:» o «-» como separador. */
    private val blockPrefix = Regex("""^Bloque\s+\d+\s*[·:\-]\s*""")

    /**
     * Las plantillas que ofrece [program]: Semana siempre; Bloques si tiene dos bloques o más; y Progreso si los registros de este
     * programa cubren al menos cuatro semanas distintas y hay algún ejercicio que haya subido.
     */
    fun templates(program: Program, logs: List<WorkoutLog>): List<ProgramStoryTemplate> {
        val own = logsOf(program, logs)
        return listOfNotNull(
            ProgramStoryTemplate.SEMANA,
            ProgramStoryTemplate.BLOQUES.takeIf { ProgramDetailHelpers.buildRoadmapBlocks(program).size >= 2 },
            ProgramStoryTemplate.PROGRESO.takeIf { loggedWeekIds(own).size >= 4 && liftGains(own).isNotEmpty() },
        )
    }

    /**
     * La historia de [template] para [program], con los registros [logs] y la fecha de hoy [today]. [style] fuerza el estilo de la
     * portada; sin él, cada plantilla usa el suyo: Semana, el de la portada del programa; Bloques, Espiral; Progreso, Relieve.
     */
    fun build(
        program: Program,
        template: ProgramStoryTemplate,
        logs: List<WorkoutLog>,
        today: LocalDate,
        style: LineCoverStyle? = null,
    ): ProgramStory {
        val base = baseCover(program)
        val defaultStyle = when (template) {
            ProgramStoryTemplate.SEMANA -> base.style
            ProgramStoryTemplate.BLOQUES -> LineCoverStyle.ESPIRAL
            ProgramStoryTemplate.PROGRESO -> LineCoverStyle.RELIEVE
        }
        val content = when (template) {
            ProgramStoryTemplate.SEMANA -> weekContent(program)
            ProgramStoryTemplate.BLOQUES -> blocksContent(program)
            ProgramStoryTemplate.PROGRESO -> progressContent(program, logsOf(program, logs))
        }
        return ProgramStory(
            name = program.name,
            eyebrow = eyebrowOf(program, template, today),
            cover = base.copy(style = style ?: defaultStyle),
            content = content,
        )
    }

    /**
     * La portada base del programa: la de líneas que ya tiene (la de Home y la cabecera), o Perfil sobre Carbón si no tiene ninguna.
     */
    internal fun baseCover(program: Program): LineCoverSpec =
        coverIdentityForProgram(program).lines ?: LineCoverSpec(LineCoverStyle.PERFIL, LineCoverBackground.CARBON, seed = 3)

    /**
     * La ganancia en kilos, con coma decimal y sin «,0», redondeada a medio kilo: 7.5 → «+7,5 kg», 4.0 → «+4 kg», 2.3 → «+2,5 kg».
     */
    internal fun formatGainKg(kg: Double): String {
        val halves = halfKgSteps(kg)
        val decimal = if (halves % 2 == 0) "" else ",5"
        return "+${halves / 2}$decimal kg"
    }

    /**
     * El eyebrow: «Rutina» si es un programa simple de una semana; «Programa · N semanas» si no. En Progreso, el mes de hoy seguido de
     * «progreso».
     */
    private fun eyebrowOf(program: Program, template: ProgramStoryTemplate, today: LocalDate): String {
        if (template == ProgramStoryTemplate.PROGRESO) return "${monthsEs[today.monthValue - 1]} · progreso"
        val weeks = program.totalProgramWeeks
        if (program.isSimpleProgram && weeks <= 1) return "Rutina"
        return "Programa · ${if (weeks == 1) "1 semana" else "$weeks semanas"}"
    }

    /**
     * Semana: la semana en curso del programa (la de su ejecución o, si no existe, la primera con sesiones), con una fila por día del
     * orden de la semana. La carga de cada día son sus series entre las de la semana más cargada.
     */
    private fun weekContent(program: Program): ProgramStoryContent.Week {
        val sessions = focusWeek(program)?.sessions.orEmpty()
        val days = getDynamicDays(program.startDay ?: 1, program.weekDays ?: 7)
        val maxSets = days.maxOfOrNull { setsOn(sessions, it.id) } ?: 0
        val rows = days.map { day ->
            val daySessions = sessions.filter { it.dayOfWeek == day.id }
            StoryDayRow(
                letter = dayLetters[(day.id - 1).mod(dayLetters.size)],
                label = daySessions.map { it.name.trim() }.filter { it.isNotEmpty() }.joinToString(" · ").ifEmpty { null },
                load = if (daySessions.isEmpty() || maxSets == 0) 0f else setsOn(sessions, day.id).toFloat() / maxSets,
            )
        }
        val trained = rows.count { it.label != null }
        val frequency = if (trained == 1) "1 día por semana" else "$trained días por semana"
        return ProgramStoryContent.Week(meta = "${focusModeLabel(program.mode)} · $frequency", days = rows)
    }

    /**
     * Bloques: una pastilla por semana, coloreada por la fase de su bloque, y una fila por bloque con sus semanas y su progresión (si
     * tiene una distinta de «Sin progresión»).
     */
    private fun blocksContent(program: Program): ProgramStoryContent.Blocks {
        val roadmap = ProgramDetailHelpers.buildRoadmapBlocks(program)
        val rows = roadmap.map { block ->
            val weeks = ProgramDetailHelpers.getWeeksForBlock(block.id, roadmap, program).size
            val scheme = block.progressionScheme
            val detail = if (scheme == null || scheme == BlockProgressionScheme.NONE) {
                "$weeks sem"
            } else {
                "$weeks sem · ${blockProgressionLabel(scheme)}"
            }
            StoryBlockRow(name = blockName(block.name), detail = detail)
        }
        val blockCount = if (roadmap.size == 1) "1 bloque" else "${roadmap.size} bloques"
        return ProgramStoryContent.Blocks(
            meta = "${focusModeLabel(program.mode)} · $blockCount",
            weekColors = programRibbonOf(program, null).map { it.color },
            blocks = rows,
        )
    }

    /**
     * Progreso: lo que más ha subido cada ejercicio (hasta tres) y el cumplimiento de las semanas registradas. Son las sesiones hechas
     * (pares semana y sesión distintos) sobre las planificadas en esas semanas, sin pasar del 100 %.
     */
    private fun progressContent(program: Program, own: List<WorkoutLog>): ProgramStoryContent.Progress {
        val weekIds = loggedWeekIds(own)
        val planned = allWeeks(program).filter { it.id in weekIds }.sumOf { it.sessions.size }
        val done = own.mapNotNull { log -> log.weekId?.let { week -> week to log.sessionId } }.toSet().size
        val percent = if (planned == 0) 0 else (100.0 * minOf(done, planned) / planned).roundToInt()
        val weeks = if (weekIds.size == 1) "1 semana" else "${weekIds.size} semanas"
        return ProgramStoryContent.Progress(
            meta = "$weeks · $percent % de cumplimiento",
            lifts = liftGains(own).map { StoryLiftRow(name = it.exerciseName, gain = formatGainKg(it.deltaKg ?: 0.0)) },
        )
    }

    /** La semana que muestra Semana: la de la ejecución del programa si existe en él; si no, la primera con sesiones. */
    private fun focusWeek(program: Program): ProgramWeek? {
        val weeks = allWeeks(program)
        val runWeekId = program.runState?.weekId
        return runWeekId?.let { id -> weeks.firstOrNull { it.id == id } } ?: weeks.firstOrNull { it.sessions.isNotEmpty() }
    }

    /** Todas las semanas del programa, en orden. */
    private fun allWeeks(program: Program): List<ProgramWeek> =
        program.macrocycles.flatMap { it.blocks }.flatMap { it.mesocycles }.flatMap { it.weeks }

    /** Las sesiones de [sessions] que caen en el día [dayId]. */
    private fun sessionsOn(sessions: List<Session>, dayId: Int): List<Session> = sessions.filter { it.dayOfWeek == dayId }

    /** Series planificadas en las sesiones de [sessions] que caen en el día [dayId]. */
    private fun setsOn(sessions: List<Session>, dayId: Int): Int =
        sessionsOn(sessions, dayId).sumOf { session -> session.allExercises().sumOf { it.sets.size } }

    /** El nombre del bloque sin su «Bloque N · »; si al quitarlo no queda nada, el nombre entero. */
    private fun blockName(name: String): String = name.replace(blockPrefix, "").trim().ifEmpty { name }

    /** Los registros de [program]: los de otros programas no cuentan. */
    private fun logsOf(program: Program, logs: List<WorkoutLog>): List<WorkoutLog> = logs.filter { it.programId == program.id }

    /** Las semanas distintas con algún registro; los registros sin semana no cuentan. */
    private fun loggedWeekIds(own: List<WorkoutLog>): Set<String> = own.mapNotNullTo(mutableSetOf<String>()) { it.weekId }

    /**
     * Los hasta tres ejercicios que más han subido en el periodo, de mayor a menor ganancia. Solo cuentan los que suben medio kilo o
     * más: una subida menor se redondearía a «+0 kg».
     */
    private fun liftGains(own: List<WorkoutLog>): List<LiftTrend> = LiftTrendEngine.trends(own)
        .filter { trend -> trend.sessions > 0 && halfKgSteps(trend.deltaKg ?: 0.0) > 0 }
        .sortedByDescending { it.deltaKg ?: 0.0 }
        .take(3)

    /** Cuántos medios kilos hay en [kg], redondeando. */
    private fun halfKgSteps(kg: Double): Int = (kg * 2).roundToInt()
}
