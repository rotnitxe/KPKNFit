package com.example.kpkn.screens.onboarding

import com.example.kpkn.data.models.Program
import com.example.kpkn.data.models.ProgramWeek
import com.example.kpkn.data.models.Session
import com.example.kpkn.data.models.WeekExecutionKind
import com.example.kpkn.data.programs.CatalogEntry
import com.example.kpkn.data.programs.CatalogLevel
import com.example.kpkn.data.programs.CatalogSource
import com.example.kpkn.data.programs.PlanLabels
import com.example.kpkn.data.programs.PlanOrigin
import com.example.kpkn.data.programs.label
import com.example.kpkn.domain.onboarding.TrainingGoalProfile
import com.example.kpkn.domain.onboarding.TrainingPlace
import com.example.kpkn.domain.text.SpanishPlurals
import com.example.kpkn.domain.training.SessionDurationEstimator
import com.example.kpkn.domain.training.generator.GeneratedRoutine

/*
 * Entreno v2 · lo que el paso PLAN revela de cada programa y lo que el paso WEEK_LAYOUT dibuja de la semana. Modelos
 * puros (sin Compose): el ViewModel los publica en `SetupWizardState` y los pasos los traducen a los modelos de los
 * componentes visuales (`PlanCardModel`, `PlanDetailModel`, `WeekLayoutSession`, `SplitOption`).
 */

/** Estado del barrido de programas del paso PLAN. */
enum class SetupPlanSweep {
    /** Sin barrido (faltan respuestas o el programa se aplazó). */
    IDLE,

    /** El barrido está en curso: el overlay «preparando…» sigue girando. */
    LOADING,

    /** El barrido terminó con al menos un programa viable. */
    READY,

    /** El barrido terminó sin ningún programa (o falló): aviso de COPY y «Reintentar». */
    FAILED,
}

/** Texto del fallo del barrido (COPY · Revelado del programa · Error). */
internal const val PLAN_PREPARE_FAILED_MESSAGE = "No pudimos preparar tu programa. Tus respuestas siguen guardadas."

/** Un día de la semana tipo del revelado ([day] = 1 lunes … 7 domingo). */
data class SetupPlanRevealDay(
    val day: Int,
    val title: String,
    val minutes: Int,
    val exerciseCount: Int,
    val isMain: Boolean,
    val place: TrainingPlace? = null,
)

/** Un tramo de la estructura del programa («Acumulación · Semanas 1–5 · …»). */
data class SetupPlanRevealBlock(val label: String, val weeksLabel: String, val detail: String)

/**
 * Todo lo que el revelado enseña de un programa viable, sacado del programa YA materializado (el mismo que se
 * previsualiza y se activa) y, en los programas «a medida», del resumen del generador.
 *
 * - [kicker]: «Hecho a tu medida», «Versión inicial», el autor del método («Jim Wendler») o «Plan KPKN».
 * - [blurb]: una o dos frases de hasta [SetupPlanReveals.BLURB_MAX] caracteres.
 * - [daysLabel] / [minutesLabel] / [levelLabel]: los tres datos de la portada («4 días», «~60 min», «Intermedio»).
 * - [reasons]: «Por qué este programa» (las razones del generador o las de la tarjeta del plan).
 * - [notes]: notas honestas (versión inicial, material que falta, tiempo por encima de lo pedido…).
 */
data class SetupPlanReveal(
    val planId: String,
    val title: String,
    val generated: Boolean,
    val profile: TrainingGoalProfile?,
    val kicker: String,
    val blurb: String,
    val description: String,
    val daysLabel: String,
    val minutesLabel: String,
    val levelLabel: String,
    val badge: String?,
    val coverSeed: Int,
    val mainExercises: List<String>,
    val blocks: List<SetupPlanRevealBlock>,
    val week: List<SetupPlanRevealDay>,
    val reasons: List<String>,
    val notes: List<String>,
    val attribution: String?,
    val isInitialVersion: Boolean = false,
)

/** Una sesión de la semana armada tal como la dibuja el tablero. */
data class SetupLayoutSession(
    val id: String,
    val title: String,
    val focus: String,
    val minutes: Int,
    val exerciseCount: Int,
    val isMain: Boolean,
    val place: TrainingPlace? = null,
)

/** Un reparto del catálogo al que se puede adaptar el programa (título corto por día de entreno). */
data class SetupSplitOption(val id: String, val name: String, val summary: String, val dayTitles: List<String>)

/**
 * Una sesión cuyos ejercicios no se pueden hacer con el material del lugar de su día (la persona la movió a un día de
 * otro lugar, o el plan de autor trae material que ese lugar no tiene). La persona manda: el movimiento se permite y esto
 * es el aviso honesto que ve en el tablero y en la revisión final.
 *
 * - [sessionPlace]: de qué lugar es el material de la sesión; null si ni el programa lo dice ni ningún lugar declarado lo cubre.
 * - [dayPlace]: dónde entrena ese día.
 */
data class SetupPlaceConflict(
    val sessionId: String,
    val title: String,
    val day: Int,
    val sessionPlace: TrainingPlace?,
    val dayPlace: TrainingPlace,
)

/**
 * La semana del programa previsualizado para el paso WEEK_LAYOUT.
 *
 * - [assignment]: día (1..7) → id de sesión; los días sin entrada son descanso.
 * - [selectedSplitId]: el reparto al que se adaptó (`adaptedSplitId`) o, si no, el del propio programa.
 * - [canReset]: hay algo que restablecer (sesiones movidas o reparto adaptado).
 * - [authoredStructure]: el programa trae su reparto de autor; adaptarlo pide el aviso de COPY antes de confirmar.
 * - [notes]: avisos honestos del último ajuste (días desparejos, estructura de autor cambiada…).
 * - [refusal]: por qué no se pudo adaptar el último reparto pedido (null si no hubo problema).
 * - [placeConflicts]: las sesiones que caen en un día cuyo lugar no tiene su material; el aviso persiste mientras siga así
 *   (se deshace moviendo otra vez o con «Restablecer»).
 */
data class SetupWeekLayout(
    val weekStartDay: Int,
    val sessions: List<SetupLayoutSession>,
    val assignment: Map<Int, String>,
    val splitOptions: List<SetupSplitOption>,
    val selectedSplitId: String?,
    val canReset: Boolean,
    val authoredStructure: Boolean,
    val notes: List<String> = emptyList(),
    val refusal: String? = null,
    val placeConflicts: List<SetupPlaceConflict> = emptyList(),
)

/**
 * Constructor puro de [SetupPlanReveal]. Todo sale del programa preparado (semana tipo, minutos, estructura,
 * ejercicios principales) y de la ficha editorial; los programas «a medida» suman el resumen del generador.
 */
internal object SetupPlanReveals {

    const val KICKER_TAILORED = "Hecho a tu medida"
    const val KICKER_INITIAL = "Versión inicial"
    const val KICKER_KPKN = "Plan KPKN"
    const val BADGE_ADAPTIVE = "Se adapta a ti"
    const val BLURB_MAX = 110

    /** Lo mínimo que debe quedar de una frase cortada en una coma para que se lea como frase (si no, se corta en una palabra). */
    const val BLURB_MIN_CLAUSE = 40
    const val MAX_MAIN_EXERCISES = 6

    /** Etiqueta de la estructura de una semana que se repite. */
    const val REPEATING_WEEK_LABEL = "Semana que se repite"
    const val REPEATING_WEEK_SPAN = "Cada semana"

    /**
     * Programa «a medida». [routine] es la salida del generador que produjo [program] (mismo pedido); [level] es el
     * nivel de la persona, para el que se armó.
     */
    fun forGenerated(
        entry: CatalogEntry,
        program: Program,
        routine: GeneratedRoutine?,
        profile: TrainingGoalProfile?,
        level: CatalogLevel,
        declaredMinutes: Int?,
        coverSeed: Int,
    ): SetupPlanReveal {
        val summary = routine?.summary
        val week = weekOf(program)
        val initial = summary?.isInitialVersion == true
        val oneLiner = summary?.oneLiner?.takeIf { it.isNotBlank() }
        return SetupPlanReveal(
            planId = entry.id,
            title = entry.displayName,
            generated = true,
            profile = profile,
            kicker = if (initial) KICKER_INITIAL else KICKER_TAILORED,
            blurb = blurbOf(oneLiner ?: entry.summary),
            description = listOfNotNull(oneLiner, entry.summary).joinToString(" "),
            daysLabel = daysLabel(week.size),
            minutesLabel = minutesLabel(week, declaredMinutes),
            levelLabel = level.label,
            badge = null,
            coverSeed = coverSeed,
            mainExercises = (summary?.mainExercises ?: mainExercisesOf(program)).distinct().take(MAX_MAIN_EXERCISES),
            blocks = listOf(
                SetupPlanRevealBlock(REPEATING_WEEK_LABEL, REPEATING_WEEK_SPAN, oneLiner ?: entry.summary),
            ),
            week = week,
            reasons = summary?.reasons.orEmpty(),
            notes = (routine?.notes.orEmpty() + listOfNotNull(timeNote(week, declaredMinutes))).distinct(),
            attribution = null,
            isInitialVersion = initial,
        )
    }

    /**
     * Plan propio o de autor del catálogo. [reasons] son las de su tarjeta (encaje con la semana, material, nivel,
     * prioridades) y [declaredMinutes] el tiempo por sesión pedido (para la nota «~N min por sesión»).
     */
    fun forCatalog(
        entry: CatalogEntry,
        program: Program,
        reasons: List<String>,
        profile: TrainingGoalProfile?,
        declaredMinutes: Int?,
        coverSeed: Int,
    ): SetupPlanReveal {
        val week = weekOf(program)
        val adaptive = entry.source == CatalogSource.NATIVE
        return SetupPlanReveal(
            planId = entry.id,
            title = entry.displayName,
            generated = false,
            profile = profile,
            kicker = kickerOf(entry),
            blurb = blurbOf(entry.summary),
            description = entry.summary,
            daysLabel = daysLabel(week.size),
            minutesLabel = minutesLabel(week, declaredMinutes),
            levelLabel = PlanLabels.levelLabel(entry.levels),
            badge = if (adaptive) BADGE_ADAPTIVE else null,
            coverSeed = coverSeed,
            mainExercises = mainExercisesOf(program),
            blocks = blocksOf(program),
            week = week,
            reasons = reasons,
            notes = (entry.notes + listOfNotNull(timeNote(week, declaredMinutes))).distinct(),
            attribution = entry.attributionLine?.takeIf { entry.origin != PlanOrigin.KPKN && it.isNotBlank() },
        )
    }

    /** El autor del método («Jim Wendler») o «Plan KPKN» en los propios. */
    fun kickerOf(entry: CatalogEntry): String {
        if (entry.origin == PlanOrigin.KPKN) return KICKER_KPKN
        val author = (entry.provenance?.sourceAuthor ?: entry.sourceAuthor)?.trim()?.takeIf { it.isNotEmpty() }
        return author?.substringBefore(" (")?.substringBefore(" / ") ?: KICKER_KPKN
    }

    /**
     * Una o dos frases de hasta [BLURB_MAX] caracteres: las frases enteras que caben y, si ni la primera cabe, la
     * primera cortada en su último signo de pausa (coma, punto y coma o dos puntos) que cabe y cerrada con punto (lo que
     * se pierde suele ser el inciso final, como «, unos 90 min por sesión», que la portada ya dice: así no queda
     * «…con movilidad, unos…» ni «…siempre dejas…»); sin una pausa que deje un trozo con sentido ([BLURB_MIN_CLAUSE]),
     * recortada en una palabra con «…».
     */
    fun blurbOf(text: String): String {
        val clean = text.trim().replace(Regex("""\s+"""), " ")
        if (clean.length <= BLURB_MAX) return clean
        val sentences = Regex("""(?<=[.!?])\s+""").split(clean).filter { it.isNotBlank() }
        var out = ""
        for (sentence in sentences) {
            val next = if (out.isEmpty()) sentence else "$out $sentence"
            if (next.length > BLURB_MAX) break
            out = next
        }
        if (out.isNotEmpty()) return out
        val head = clean.take(BLURB_MAX - 1)
        val lastPause = head.lastIndexOfAny(charArrayOf(',', ';', ':'))
        if (lastPause >= BLURB_MIN_CLAUSE) return head.take(lastPause).trimEnd(',', ';', ':', ' ') + "."
        val cut = head.substringBeforeLast(' ').trimEnd(',', ';', ':', ' ')
        return "$cut…"
    }

    /** «1 día» / «4 días». */
    fun daysLabel(days: Int): String = SpanishPlurals.days(days)

    /**
     * «~60 min»: la sesión más larga de la semana tipo según el estimador común; sin sesiones, el tiempo pedido.
     */
    fun minutesLabel(week: List<SetupPlanRevealDay>, declaredMinutes: Int?): String {
        val minutes = week.maxOfOrNull { it.minutes }?.takeIf { it > 0 } ?: declaredMinutes ?: 0
        return "~$minutes min"
    }

    /**
     * Nota honesta cuando la sesión más larga pasa de lo que se pidió («~70 min por sesión: un poco más de los 60 que
     * pediste.»). Los planes de autor que caben con una tolerancia del 15 % siguen siendo viables con esta nota.
     */
    fun timeNote(week: List<SetupPlanRevealDay>, declaredMinutes: Int?): String? {
        val declared = declaredMinutes ?: return null
        val longest = week.maxOfOrNull { it.minutes } ?: return null
        return if (longest > declared) {
            "~$longest min por sesión: un poco más de los $declared que pediste."
        } else {
            null
        }
    }

    // ─── Semana tipo, ejercicios y estructura del programa preparado ──────────────────────────────

    /** La primera semana de entreno del programa (las de descanso no cuentan). */
    fun firstTrainingWeek(program: Program): ProgramWeek? = program.macrocycles.asSequence()
        .flatMap { it.blocks.asSequence() }
        .flatMap { it.mesocycles.asSequence() }
        .flatMap { it.weeks.asSequence() }
        .firstOrNull { it.executionKind != WeekExecutionKind.REST && it.sessions.isNotEmpty() }

    /** Los días de la semana tipo, en el orden de la semana (1 = lunes). */
    fun weekOf(program: Program): List<SetupPlanRevealDay> {
        val sessions = firstTrainingWeek(program)?.sessions.orEmpty()
        val singleMain = sessions.count { it.isMainSession } == 1
        return sessions.mapNotNull { session ->
            val day = dayOf(session) ?: return@mapNotNull null
            SetupPlanRevealDay(
                day = day,
                title = session.name,
                minutes = SessionDurationEstimator.estimate(session).totalMinutes,
                exerciseCount = session.allExercises().size,
                isMain = singleMain && session.isMainSession,
                place = session.placeId?.let { id -> TrainingPlace.entries.firstOrNull { it.name == id } },
            )
        }.sortedBy { it.day }
    }

    fun dayOf(session: Session): Int? =
        session.dayOfWeek?.takeIf { it in 1..7 } ?: session.assignedDays.firstOrNull { it in 1..7 }

    /** El primer ejercicio de cada sesión de la semana tipo (los principales), sin repetir. */
    fun mainExercisesOf(program: Program): List<String> =
        firstTrainingWeek(program)?.sessions.orEmpty()
            .sortedBy { dayOf(it) ?: Int.MAX_VALUE }
            .mapNotNull { session -> session.allExercises().firstOrNull { it.cardioDetails == null }?.name }
            .filter { it.isNotBlank() }
            .distinct()
            .take(MAX_MAIN_EXERCISES)

    /**
     * Estructura del programa: un tramo por mesociclo (su objetivo y sus semanas) y, dentro de él, las semanas de
     * descarga aparte. Un programa de una sola semana es «Semana que se repite».
     */
    fun blocksOf(program: Program): List<SetupPlanRevealBlock> {
        val segments = mutableListOf<Triple<String, IntRange, String>>()
        var week = 0
        val blocks = program.macrocycles.flatMap { it.blocks }
        blocks.forEach { block ->
            block.mesocycles.forEach { meso ->
                var current: Pair<String, Int>? = null
                meso.weeks.forEach { programWeek ->
                    week += 1
                    val label = if (programWeek.executionKind == WeekExecutionKind.DELOAD) {
                        DELOAD_LABEL
                    } else {
                        meso.customGoal?.takeIf { it.isNotBlank() } ?: meso.goal.label
                    }
                    val open = current
                    if (open == null || open.first != label) {
                        open?.let { segments += Triple(it.first, it.second until week, detailOf(block.name, blocks.size)) }
                        current = label to week
                    }
                }
                current?.let { segments += Triple(it.first, it.second..week, detailOf(block.name, blocks.size)) }
            }
        }
        if (week <= 1) return listOf(SetupPlanRevealBlock(REPEATING_WEEK_LABEL, REPEATING_WEEK_SPAN, ""))
        return segments.map { (label, range, detail) -> SetupPlanRevealBlock(label, weeksLabel(range), detail) }
    }

    private const val DELOAD_LABEL = "Descarga"

    private fun detailOf(blockName: String, blockCount: Int): String = if (blockCount > 1) blockName else ""

    /** «Semana 6» / «Semanas 1–5». */
    fun weeksLabel(range: IntRange): String =
        if (range.first == range.last) "Semana ${range.first}" else "Semanas ${range.first}–${range.last}"
}
