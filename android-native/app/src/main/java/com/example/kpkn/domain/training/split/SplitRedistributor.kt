package com.example.kpkn.domain.training.split

import com.example.kpkn.data.models.Exercise
import com.example.kpkn.data.models.Program
import com.example.kpkn.data.models.ProgramWeek
import com.example.kpkn.data.models.Session
import com.example.kpkn.data.models.isCompetitionMeet
import com.example.kpkn.data.models.resolvedSchedulePlan
import com.example.kpkn.data.programs.KpknMuscleGroup
import com.example.kpkn.data.protocols.PlanProvenanceClass
import com.example.kpkn.data.splits.SplitTag
import com.example.kpkn.data.splits.SplitTemplate
import com.example.kpkn.data.splits.isVisibleForApplication
import com.example.kpkn.domain.onboarding.TrainingGoalProfile
import com.example.kpkn.domain.text.SpanishPlurals
import com.example.kpkn.domain.training.OrderPrioritiesContract
import com.example.kpkn.domain.training.PatternFamily
import com.example.kpkn.domain.training.PlanMaterializer
import com.example.kpkn.domain.training.ProgramCalendarEngine
import com.example.kpkn.domain.training.SessionDurationEstimator
import com.example.kpkn.domain.training.VolumeCalculator
import com.example.kpkn.domain.training.approach.ApproachExerciseInfo
import com.example.kpkn.domain.training.approach.ApproachOptions
import com.example.kpkn.domain.training.approach.ApproachPlanner
import java.util.Locale
import kotlin.math.abs

/**
 * Ajustes de [SplitRedistributor.redistribute]. Todo tiene un valor por defecto sensato.
 */
data class RedistributionOptions(
    /**
     * Repite el mismo reparto en las demás semanas del plan cuando tienen la misma estructura (los mismos ejercicios en
     * las mismas sesiones, como las semanas de un plan propio que solo cambian de series o RIR). Una semana distinta se
     * deja como estaba y se avisa en las notas. Por defecto solo se redistribuye la semana pedida.
     */
    val propagateToOtherWeeks: Boolean = false,
    /** Tiempo por sesión que la persona dijo tener: solo para avisar de los días que lo superan (no recorta nada). */
    val targetMinutes: Int? = null,
    /**
     * Al terminar, vuelve a pasar cada sesión por la aproximación y movilidad obligatorias ([ApproachPlanner]): el primer
     * ejercicio de cada día nuevo puede ser otro y debe llevar su aproximación. Conserva lo que ya traía cada ejercicio.
     */
    val completeApproach: Boolean = true,
    val approach: ApproachOptions = ApproachOptions(),
    /**
     * En un plan con receta (propio o de autor) marca cada sesión nueva como «Sesión personalizada» para que una
     * reconstrucción posterior de la semana desde su receta no deshaga el reparto.
     */
    val protectFromRematerialization: Boolean = true,
    /** Marca de tiempo de esas marcas: fija (0) para que el resultado sea idéntico entre llamadas. */
    val nowMs: Long = 0L,
)

/** Una sesión y lo que dura según el estimador común (antes de redistribuir). */
data class SessionMinutes(val title: String, val weekday: Int?, val minutes: Int, val exerciseCount: Int)

/** Lo que quedó en cada día del reparto. */
data class DayReport(
    val index: Int,
    val weekday: Int,
    /** Etiqueta del reparto tal cual. */
    val label: String,
    /** Título de la sesión nueva («Torso A»). */
    val title: String,
    val minutes: Int,
    val exerciseCount: Int,
    /** Músculos principales del día («Pecho · Hombros · Tríceps»). */
    val focus: String,
    val exercises: List<String>,
)

/** De dónde venía un ejercicio y a qué día fue. */
data class ExerciseMove(
    val exerciseId: String,
    val name: String,
    val fromTitle: String,
    val fromWeekday: Int?,
    val toTitle: String,
    val toWeekday: Int,
) {
    /** Cambió de sesión de origen a sesión nueva con distinto título o día. */
    val moved: Boolean get() = fromTitle != toTitle || fromWeekday != toWeekday
}

/**
 * Qué hizo el redistribuidor: sesiones y minutos antes y después, qué se movió y el volumen semanal por músculo
 * (series, con el mismo criterio que el volumen de la app: primario 1, secundario 0,5) antes y después.
 */
data class RedistributionReport(
    val weekIndex: Int,
    val splitId: String,
    val splitName: String,
    val before: List<SessionMinutes>,
    val days: List<DayReport>,
    val moves: List<ExerciseMove>,
    val volumeBefore: Map<String, Double>,
    val volumeAfter: Map<String, Double>,
    /** La mayor diferencia, en series, de un músculo entre antes y después. Mover ejercicios enteros la deja en 0. */
    val maxVolumeDeviation: Double,
    /** Ejercicios que el resolutor no pudo clasificar (se colocaron en el día más corto). */
    val unclassified: List<String>,
) {
    /** Lo parejos que quedan los minutos: el día más corto entre el más largo (1 = idénticos). Sin días, 1. */
    val minutesRatio: Double
        get() {
            val longest = days.maxOfOrNull { it.minutes } ?: return 1.0
            val shortest = days.minOf { it.minutes }
            return if (longest <= 0) 1.0 else shortest.toDouble() / longest
        }

    /** Días que quedaron con menos de 3 ejercicios. */
    val shortDayCount: Int
        get() = days.count { it.exerciseCount < DayAssigner.MIN_EXERCISES }

    companion object {
        fun empty(weekIndex: Int, splitId: String, splitName: String) = RedistributionReport(
            weekIndex = weekIndex,
            splitId = splitId,
            splitName = splitName,
            before = emptyList(),
            days = emptyList(),
            moves = emptyList(),
            volumeBefore = emptyMap(),
            volumeAfter = emptyMap(),
            maxVolumeDeviation = 0.0,
            unclassified = emptyList(),
        )
    }
}

/**
 * Resultado de adaptar un programa a un reparto. Con [compatible] en `false` no se hizo nada: [program] es el mismo que
 * entró y [reason] dice por qué en una frase para la persona.
 */
data class RedistributionResult(
    val compatible: Boolean,
    val reason: String?,
    val program: Program,
    val report: RedistributionReport,
    /** Avisos honestos para mostrar junto al resultado (límites, cambios de estructura, días desparejos). */
    val notes: List<String>,
    /** Detalle técnico si el redistribuidor falló por un error inesperado (no es para la persona); null en los demás casos. */
    val diagnostic: String? = null,
)

/**
 * Adapta un programa a un reparto del catálogo **repartiendo sus ejercicios** entre los días del reparto (el motor
 * anterior solo reasignaba sesiones enteras a días). Puro y determinista: misma entrada, mismo programa.
 *
 * Qué conserva y qué decide:
 * - **Volumen**: los ejercicios se mueven enteros (con sus series, repeticiones, descansos, aproximaciones y movilidad),
 *   así que las series semanales por músculo quedan exactamente iguales (0 de diferencia; el brief admitía ±1).
 * - **Superseries** juntas; el cardio y la movilidad de una parte viajan como bloques; el calentamiento general de una
 *   sesión viaja con su primer ejercicio de fuerza.
 * - **Reparto**: cada ejercicio va al día cuyo foco encaja mejor, repartiendo los de un mismo músculo entre los días que
 *   lo permiten; los minutos quedan lo más parejos que el foco deje; ningún día vacío ni con menos de 3 ejercicios si hay
 *   de dónde sacar; compuestos pesados del mismo patrón no caen en días consecutivos si hay otro hueco ([DayAssigner]).
 * - **Orden dentro del día**: compuestos principales primero, y cada uno pegado a su aproximación y movilidad.
 * - Los **planes de autor** con receta fija solo se redistribuyen con permiso explícito (`allowAuthoredRecipes`).
 */
object SplitRedistributor {

    /** Motivo que se da cuando el plan trae su reparto de autor y la persona no lo pidió explícitamente. */
    const val AUTHORED_REASON = "Este plan trae su reparto de autor."

    /**
     * Repartos del catálogo que se pueden aplicar con [daysCount] días de entreno, para el selector del tablero de la
     * semana: visibles para la aplicación y, los de powerlifting, solo para el perfil de fuerza. Los recomendados por
     * KPKN primero; después, el orden del catálogo.
     */
    fun optionsFor(daysCount: Int, profile: TrainingGoalProfile? = null): List<SplitChoice> {
        val compatible = SplitCatalogRules.compatible(daysCount, profile)
        val (recommended, others) = compatible.partition { SplitTag.RECOMENDADO_KPKN in it.tags }
        return (recommended + others).map { SplitCatalogRules.choiceOf(it) }
    }

    /**
     * Adapta [program] a [split] en los días [weekdays] (1 = lunes … 7 = domingo, en el orden de la semana).
     *
     * @param weekIndex semana que se redistribuye: posición entre las semanas de entreno del programa (no cuentan las de
     *   descanso ni las extra de ciclo). Por defecto la primera.
     * @param allowAuthoredRecipes permiso explícito para redistribuir un plan de autor con receta fija.
     */
    fun redistribute(
        program: Program,
        split: SplitTemplate,
        weekdays: List<Int>,
        resolver: ExerciseTraitResolver,
        weekIndex: Int = 0,
        allowAuthoredRecipes: Boolean = false,
        options: RedistributionOptions = RedistributionOptions(),
    ): RedistributionResult = try {
        redistributeChecked(program, split, weekdays, resolver, weekIndex, allowAuthoredRecipes, options)
    } catch (error: Exception) {
        // Un programa con datos raros no debe tumbar la pantalla: se rechaza con un motivo y el detalle va aparte.
        RedistributionResult(
            compatible = false,
            reason = "No se pudo adaptar el programa a este reparto.",
            program = program,
            report = RedistributionReport.empty(weekIndex, split.id, SplitCatalogRules.displayName(split)),
            notes = emptyList(),
            diagnostic = "${error::class.simpleName}: ${error.message}",
        )
    }

    private fun redistributeChecked(
        program: Program,
        split: SplitTemplate,
        weekdays: List<Int>,
        resolver: ExerciseTraitResolver,
        weekIndex: Int,
        allowAuthoredRecipes: Boolean,
        options: RedistributionOptions,
    ): RedistributionResult {
        val splitName = SplitCatalogRules.displayName(split)
        fun refuse(reason: String) = RedistributionResult(
            compatible = false,
            reason = reason,
            program = program,
            report = RedistributionReport.empty(weekIndex, split.id, splitName),
            notes = emptyList(),
        )

        if (weekdays.isEmpty() || weekdays.any { it !in 1..7 } || weekdays.toSet().size != weekdays.size) {
            return refuse("Elige entre 1 y 7 días distintos de la semana.")
        }
        if (!allowAuthoredRecipes && AuthoredPlans.hasFixedRecipe(program)) return refuse(AUTHORED_REASON)
        if (!split.isVisibleForApplication) return refuse("Este reparto no está disponible.")
        val definitions = SplitCatalogRules.trainingDefinitions(split)
        if (definitions.isEmpty()) return refuse("Este reparto no tiene días de entreno.")
        if (definitions.size != weekdays.size) {
            return refuse("Este reparto tiene ${SpanishPlurals.days(definitions.size)} de entreno y elegiste ${weekdays.size}.")
        }

        val effectiveWeeks = ProgramWeeks.effective(program)
        val baseRef = effectiveWeeks.getOrNull(weekIndex) ?: return refuse("Tu programa no tiene esa semana.")
        val originals = baseRef.week.sessions
        weekProblem(baseRef.week)?.let { return refuse(it) }

        val units = WeekGather.gather(baseRef.week, resolver)
        val exerciseTotal = units.sumOf { it.exerciseCount }
        if (exerciseTotal == 0) return refuse("Tu programa no tiene ejercicios para repartir.")
        if (units.size < definitions.size) {
            return refuse("Hay menos ejercicios que días de entreno: ${SpanishPlurals.exercises(exerciseTotal)} para ${SpanishPlurals.days(definitions.size)}.")
        }
        missingLift(definitions.flatMap { it.foci }.distinct(), units)?.let { lift ->
            return refuse("Tu programa no tiene $lift y este reparto lo necesita.")
        }

        val titles = SplitCatalogRules.titlesOf(definitions.map { it.label })
        val days = definitions.mapIndexed { index, definition ->
            DayInfo(index, weekdays[index], definition.label, titles[index], DayFocusParser.parse(definition))
        }
        val assignment = DayAssigner.assign(units, days)
        val dayIndexByKey = units.indices.associate { units[it].key to assignment.dayOfUnit[it] }
        val describe = { day: DayInfo -> SplitDayTexts.description(day.label, split.sessionDescriptions) }
        val infoOf = { exercise: Exercise ->
            resolver.traitsOf(exercise)?.let { ApproachExerciseInfo(joints = it.joints, isCompound = it.isCompound, canBeHeavy = it.canBeHeavy) }
        }

        fun finish(sessions: List<Session>, origin: List<Session>): List<Session> {
            val completed = if (options.completeApproach) {
                sessions.map { ApproachPlanner.apply(it, options.approach, infoOf) }
            } else {
                sessions
            }
            return if (origin.any { it.targetDurationMinutes != null }) {
                completed.map { it.copy(targetDurationMinutes = SessionDurationEstimator.estimate(it).totalMinutes) }
            } else {
                completed
            }
        }

        fun buildWeek(week: ProgramWeek, weekUnits: List<MovableUnit>, dayOfUnit: IntArray): List<Session> {
            val byDay = days.map { day -> weekUnits.filterIndexed { index, _ -> dayOfUnit[index] == day.index } }
            val mainDays = mainDaysOf(week.sessions, weekUnits, dayOfUnit, days.size)
            return finish(WeekBuilder.build(week.sessions, week.id, days, byDay, mainDays, describe), week.sessions)
        }

        val trainingDays = weekdays.toSet()
        fun adapted(week: ProgramWeek, sessions: List<Session>): ProgramWeek =
            week.copy(sessions = sessions, trainingDayDates = week.trainingDayDates.filterKeys { it in trainingDays })

        val newSessions = buildWeek(baseRef.week, units, assignment.dayOfUnit)
        val replacements = linkedMapOf(baseRef.position to adapted(baseRef.week, newSessions))
        val covered = mutableListOf(baseRef)
        val skipped = mutableListOf<WeekRef>()
        var independentWeeks = 0
        if (options.propagateToOtherWeeks) {
            val foci = definitions.flatMap { it.foci }.distinct()
            effectiveWeeks.filter { it.position != baseRef.position }.forEach { other ->
                val otherUnits = WeekGather.gather(other.week, resolver)
                val usable = weekProblem(other.week) == null &&
                    otherUnits.sumOf { it.exerciseCount } > 0 &&
                    otherUnits.size >= definitions.size &&
                    missingLift(foci, otherUnits) == null
                if (!usable) {
                    skipped += other
                    return@forEach
                }
                // Una semana con la misma estructura repite el reparto ejercicio a ejercicio (cada hueco queda en su día);
                // una con otros ejercicios (p. ej. las semanas pares de un plan propio) se reparte por su cuenta.
                val parallel = other.week.sessions.size == originals.size &&
                    otherUnits.size == units.size &&
                    otherUnits.map { it.key }.toSet() == dayIndexByKey.keys
                val dayOfOther = if (parallel) {
                    IntArray(otherUnits.size) { dayIndexByKey.getValue(otherUnits[it].key) }
                } else {
                    independentWeeks++
                    DayAssigner.assign(otherUnits, days).dayOfUnit
                }
                replacements[other.position] = adapted(other.week, buildWeek(other.week, otherUnits, dayOfOther))
                covered += other
            }
        }

        var result = ProgramWeeks.map(program) { ref -> replacements[ref.position] }
        val coversEveryWeek = covered.size == effectiveWeeks.size
        result = result.copy(
            schedulePlan = result.resolvedSchedulePlan().copy(trainingDays = ProgramWeeks.trainingDays(result)),
            splitTrialSeen = false,
            selectedSplitId = if (coversEveryWeek) split.id else result.selectedSplitId,
            blockSplitSelections = if (coversEveryWeek) emptyMap() else result.blockSplitSelections,
            weekSplitSelections = if (coversEveryWeek) {
                emptyMap()
            } else {
                result.weekSplitSelections + covered.associate { it.week.id to split.id }
            },
        )
        if (ProgramCalendarEngine.isCalendarized(result)) result = ProgramCalendarEngine.materializeWeekDates(result)
        // Las sesiones de origen que ya no existen (había más sesiones que días) no dejan marcas huérfanas.
        val keptIds = covered.flatMap { ref -> replacements.getValue(ref.position).sessions.map { it.id } }.toSet()
        val droppedIds = covered.flatMap { ref -> ref.week.sessions.map { it.id } }.filterNot { it in keptIds }.toSet()
        if (droppedIds.isNotEmpty()) {
            result = result.copy(manualSessionOverrides = result.manualSessionOverrides.filterNot { it.sessionId in droppedIds })
        }
        if (options.protectFromRematerialization && result.sourceRecipe != null) {
            covered.forEach { ref ->
                replacements.getValue(ref.position).sessions.forEach { session ->
                    result = PlanMaterializer.withManualSessionOverride(
                        program = result,
                        sessionId = session.id,
                        weekId = ref.week.id,
                        weekOccurrence = PlanMaterializer.weekOccurrenceOf(ref.week, ref.weekIndex),
                        recipeDayId = null,
                        reason = "Reparto adaptado: $splitName",
                        nowMs = options.nowMs,
                    )
                }
            }
        }

        val report = reportOf(
            weekIndex = weekIndex,
            split = split,
            splitName = splitName,
            originals = originals,
            newSessions = newSessions,
            days = days,
            units = units,
            assignment = assignment,
            resolver = resolver,
        )
        val notes = notesOf(
            program = program,
            splitName = splitName,
            days = days,
            units = units,
            assignment = assignment,
            report = report,
            effectiveWeekCount = effectiveWeeks.size,
            coveredWeekCount = covered.size,
            skippedWeeks = skipped.size,
            independentWeeks = independentWeeks,
            baseWeekNumber = weekIndex + 1,
            targetMinutes = options.targetMinutes,
        )
        return RedistributionResult(compatible = true, reason = null, program = result, report = report, notes = notes)
    }

    // ─── Comprobaciones ─────────────────────────────────────────────────────────────────────────

    /** Motivo por el que una semana no se puede redistribuir, o null. */
    private fun weekProblem(week: ProgramWeek): String? = when {
        week.sessions.any { it.isCompetitionMeet } -> "La semana incluye una competición."
        week.sessions.any { it.sessionB != null || it.sessionC != null || it.sessionD != null } ->
            "La semana tiene sesiones con variantes (B, C o D)."
        else -> null
    }

    /** Un levantamiento que pide algún día del reparto (`foci`) y que el programa no trae, o null si los trae todos. */
    private fun missingLift(foci: List<String>, units: List<MovableUnit>): String? {
        foci.forEach { focus ->
            if (focusName(focus) != null && units.none { satisfies(focus, it) }) return focusName(focus)
        }
        return null
    }

    private fun focusName(focus: String): String? = when (focus.uppercase()) {
        "SQUAT" -> "sentadilla"
        "BENCH" -> "press de banca"
        "DEADLIFT" -> "peso muerto"
        else -> null
    }

    /** ¿Esta unidad es el levantamiento [focus]? (compuesto de su patrón). */
    private fun satisfies(focus: String, unit: MovableUnit): Boolean {
        if (unit.kind != UnitKind.STRENGTH || unit.tier > 1) return false
        return when (focus.uppercase()) {
            "SQUAT" -> unit.pattern == PatternFamily.SQUAT
            "BENCH" -> unit.pattern == PatternFamily.HORIZONTAL_PUSH && KpknMuscleGroup.CHEST in unit.primaryAtoms
            "DEADLIFT" -> unit.pattern == PatternFamily.HINGE
            else -> true
        }
    }

    // ─── Sesión principal ───────────────────────────────────────────────────────────────────────

    /**
     * Qué días nuevos son «sesión principal». Si todas las sesiones de origen lo eran (un día = una sesión principal),
     * todas; si solo algunas, el día que recibe el primer ejercicio de fuerza de cada una; si ninguna, ninguno.
     */
    private fun mainDaysOf(originals: List<Session>, units: List<MovableUnit>, dayOfUnit: IntArray, dayCount: Int): Set<Int> {
        if (originals.isEmpty()) return emptySet()
        if (originals.all { it.isMainSession }) return (0 until dayCount).toSet()
        val result = linkedSetOf<Int>()
        originals.forEachIndexed { sessionIndex, session ->
            if (!session.isMainSession) return@forEachIndexed
            val candidates = units.indices.filter { units[it].originSession == sessionIndex }
            val lead = candidates.filter { units[it].kind == UnitKind.STRENGTH }.minByOrNull { units[it].originPosition }
                ?: candidates.minByOrNull { units[it].originPosition }
            if (lead != null) result += dayOfUnit[lead]
        }
        return result
    }

    // ─── Informe y notas ────────────────────────────────────────────────────────────────────────

    private fun reportOf(
        weekIndex: Int,
        split: SplitTemplate,
        splitName: String,
        originals: List<Session>,
        newSessions: List<Session>,
        days: List<DayInfo>,
        units: List<MovableUnit>,
        assignment: Assignment,
        resolver: ExerciseTraitResolver,
    ): RedistributionReport {
        val before = originals.map { session ->
            SessionMinutes(
                title = session.name,
                weekday = session.dayOfWeek,
                minutes = SessionDurationEstimator.estimate(session).totalMinutes,
                exerciseCount = session.allExercises().size,
            )
        }
        val dayReports = days.mapIndexed { index, day ->
            val session = newSessions[index]
            DayReport(
                index = index,
                weekday = day.weekday,
                label = day.label,
                title = day.title,
                minutes = SessionDurationEstimator.estimate(session).totalMinutes,
                exerciseCount = session.allExercises().size,
                focus = FocusSummary.of(session, resolver),
                exercises = session.allExercises().map { it.name },
            )
        }
        val moves = units.indices.flatMap { index ->
            val unit = units[index]
            val origin = originals.getOrNull(unit.originSession)
            val day = days[assignment.dayOfUnit[index]]
            val exercises = if (unit.block != null) unit.block.exercises else unit.exercises
            exercises.map { exercise ->
                ExerciseMove(
                    exerciseId = exercise.id,
                    name = exercise.name,
                    fromTitle = origin?.name.orEmpty(),
                    fromWeekday = origin?.dayOfWeek,
                    toTitle = day.title,
                    toWeekday = day.weekday,
                )
            }
        }
        val volumeBefore = MuscleVolume.of(originals, resolver)
        val volumeAfter = MuscleVolume.of(newSessions, resolver)
        val deviation = (volumeBefore.keys + volumeAfter.keys).maxOfOrNull { muscle ->
            abs((volumeAfter[muscle] ?: 0.0) - (volumeBefore[muscle] ?: 0.0))
        } ?: 0.0
        val unclassified = units.filter { it.kind == UnitKind.STRENGTH && !it.isClassified }
            .flatMap { unit -> unit.exercises.filterIndexed { i, _ -> unit.traits.getOrNull(i) == null }.map { it.name } }
        return RedistributionReport(
            weekIndex = weekIndex,
            splitId = split.id,
            splitName = splitName,
            before = before,
            days = dayReports,
            moves = moves,
            volumeBefore = volumeBefore,
            volumeAfter = volumeAfter,
            maxVolumeDeviation = deviation,
            unclassified = unclassified,
        )
    }

    private fun notesOf(
        program: Program,
        splitName: String,
        days: List<DayInfo>,
        units: List<MovableUnit>,
        assignment: Assignment,
        report: RedistributionReport,
        effectiveWeekCount: Int,
        coveredWeekCount: Int,
        skippedWeeks: Int,
        independentWeeks: Int,
        baseWeekNumber: Int,
        targetMinutes: Int?,
    ): List<String> = buildList {
        if (program.sourceRecipe != null || AuthoredPlans.hasFixedRecipe(program)) {
            add("El plan «${program.name}» cambia su estructura original: sus ejercicios quedan repartidos según «$splitName».")
        }
        if (effectiveWeekCount > 1) {
            when {
                skippedWeeks > 0 -> add("Se adaptaron $coveredWeekCount de $effectiveWeekCount semanas; las que no se pudieron adaptar conservan su reparto.")
                coveredWeekCount == effectiveWeekCount -> add("Se aplicó a las $effectiveWeekCount semanas del plan.")
                else -> add("Solo se adaptó la semana $baseWeekNumber de $effectiveWeekCount; las demás conservan su reparto.")
            }
            if (independentWeeks > 0) {
                add("${SpanishPlurals.withNoun(independentWeeks, "semana trae", "semanas traen")} otros ejercicios: su reparto se calculó por separado.")
            }
        }
        if (report.unclassified.isNotEmpty()) {
            add("No se pudo clasificar ${report.unclassified.distinct().joinToString(", ")}: quedó en el día más corto.")
        }
        val strengthTotal = units.sumOf { it.strengthExerciseCount }
        val shortDays = report.days.filter { it.exerciseCount < DayAssigner.MIN_EXERCISES }
        if (shortDays.isNotEmpty()) {
            if (strengthTotal < DayAssigner.MIN_EXERCISES * days.size) {
                add("Tu programa trae ${SpanishPlurals.exercises(strengthTotal)} para ${SpanishPlurals.days(days.size)}: algunos días quedan cortos.")
            } else {
                shortDays.forEach { day ->
                    add("«${day.title}» queda con ${SpanishPlurals.exercises(day.exerciseCount)}: no hay más que encajen con su foco.")
                }
            }
        }
        if (assignment.misfits.isNotEmpty()) {
            // Un ejercicio que se repite en varias sesiones (la sentadilla de cada día) se nombra una sola vez.
            val names = assignment.misfits.flatMap { units[it].exercises.map { exercise -> exercise.name } }.distinct()
            add("Para llenar todos los días, ${names.joinToString(", ")} no encaja del todo con su día.")
        }
        days.forEachIndexed { index, day ->
            day.focus.foci.mapNotNull { focus -> focusName(focus)?.let { focus to it } }.forEach { (focus, name) ->
                val here = units.indices.filter { assignment.dayOfUnit[it] == index }.map { units[it] }
                if (here.none { satisfies(focus, it) }) {
                    add("«${day.title}» no lleva $name: tu programa solo trae ${SpanishPlurals.exercises(units.count { satisfies(focus, it) })} de ese tipo.")
                }
            }
        }
        val minutes = report.days.map { it.minutes }
        val shortest = minutes.minOrNull() ?: 0
        val longest = minutes.maxOrNull() ?: 0
        if (shortest > 0 && longest.toDouble() / shortest > IMBALANCE_RATIO) {
            val longestDay = report.days.first { it.minutes == longest }
            add("Los días quedan de $shortest a $longest min: «${longestDay.title}» concentra más trabajo porque cada día conserva su foco.")
        }
        if (targetMinutes != null) {
            report.days.filter { it.minutes > targetMinutes * OVER_TARGET_RATIO }.forEach { day ->
                add("«${day.title}» dura unos ${day.minutes} min y tu tiempo por sesión es de $targetMinutes min.")
            }
        }
        if (report.maxVolumeDeviation > 0.5) {
            add("El volumen semanal de algún músculo cambió en ${String.format(Locale.ROOT, "%.1f", report.maxVolumeDeviation)} series.")
        }
    }

    /** Si el día más largo supera al más corto en más de esta proporción se avisa de que los días quedan desparejos. */
    private const val IMBALANCE_RATIO = 1.4

    /** Un día que supera el tiempo por sesión pedido en más de esta proporción se avisa. */
    private const val OVER_TARGET_RATIO = 1.15
}

/** ¿El programa trae su reparto de autor? (planes de autor, plantillas y protocolos con receta fija). */
internal object AuthoredPlans {

    fun hasFixedRecipe(program: Program): Boolean {
        val category = program.planProvenance?.category
        if (category == PlanProvenanceClass.ORIGINAL || category == PlanProvenanceClass.ADAPTED) return true
        if (!program.sourceProtocolId.isNullOrBlank()) return true
        val recipe = program.sourceRecipe ?: return false
        return OrderPrioritiesContract.recipeFixesOrder(recipe)
    }
}

/** Series semanales por músculo de unas sesiones, con el criterio del volumen de la app (primario 1, secundario 0,5). */
internal object MuscleVolume {

    const val UNCLASSIFIED = "Sin clasificar"

    fun of(sessions: List<Session>, resolver: ExerciseTraitResolver): Map<String, Double> {
        val totals = LinkedHashMap<String, Double>()
        sessions.forEach { session ->
            session.allExercises().distinctBy { it.id }.forEach { exercise ->
                if (exercise.cardioDetails != null) return@forEach
                val sets = VolumeCalculator.countEffectiveSets(exercise.sets)
                if (sets <= 0) return@forEach
                val traits = resolver.traitsOf(exercise)
                if (traits == null) {
                    totals.merge(UNCLASSIFIED, sets.toDouble()) { a, b -> a + b }
                } else {
                    traits.muscles.forEach { (atom, weight) ->
                        totals.merge(SplitMuscles.label(atom), sets * weight) { a, b -> a + b }
                    }
                }
            }
        }
        return totals.entries.sortedBy { it.key }.associate { it.key to it.value }
    }
}

/** Los músculos que más trabaja una sesión, en una línea corta para el tablero («Pecho · Hombros · Tríceps»). */
internal object FocusSummary {

    fun of(session: Session, resolver: ExerciseTraitResolver, limit: Int = 3): String {
        val totals = LinkedHashMap<String, Double>()
        session.allExercises().forEach { exercise ->
            val traits = resolver.traitsOf(exercise) ?: return@forEach
            val sets = VolumeCalculator.countEffectiveSets(exercise.sets)
            traits.muscles.forEach { (atom, weight) ->
                totals.merge(SplitMuscles.focusLabel(atom), sets * weight) { a, b -> a + b }
            }
        }
        return totals.entries
            .sortedWith(compareByDescending<Map.Entry<String, Double>> { it.value }.thenBy { it.key })
            .take(limit)
            .joinToString(" · ") { it.key }
    }
}

/** Texto de la descripción de una sesión nueva: el del reparto si lo trae; si no, uno según la etiqueta. */
internal object SplitDayTexts {

    fun description(label: String, custom: Map<String, String>): String {
        custom[label]?.let { return it }
        val lower = label.lowercase()
        return when {
            lower.contains("empuje") || lower.contains("push") -> "Sesión enfocada en patrones de empuje: pecho, hombro anterior/lateral y tríceps."
            lower.contains("tirón") || lower.contains("tiron") || lower.contains("pull") -> "Sesión enfocada en tracción: espalda, deltoide posterior y bíceps."
            lower.contains("pierna") || lower.contains("lower") -> "Sesión de tren inferior: cuádriceps, isquiosurales, glúteos y pantorrillas según prioridad."
            lower.contains("torso") || lower.contains("upper") -> "Sesión de tren superior con empujes y tracciones balanceadas."
            lower.contains("cuerpo completo") || lower.contains("full body") -> "Sesión full body para distribuir volumen entre tren superior e inferior."
            lower.contains("pecho") && lower.contains("espalda") -> "Sesión antagonista para pecho y espalda, útil para alto volumen de torso."
            lower.contains("hombro") && lower.contains("brazo") -> "Sesión de especialización para deltoides, bíceps y tríceps."
            lower.contains("sentadilla") || lower.contains("squat") -> "Sesión con prioridad en sentadilla y accesorios compatibles."
            lower.contains("peso muerto") || lower.contains("deadlift") -> "Sesión con prioridad en peso muerto, cadena posterior y accesorios compatibles."
            lower.contains("banca") || lower.contains("bench") -> "Sesión con prioridad en press banca y musculatura de soporte."
            lower.contains("pesado") || lower.contains("max") -> "Día de mayor intensidad; mantén el volumen accesorio controlado."
            lower.contains("liviano") || lower.contains("recuperación") || lower.contains("recuperacion") -> "Día técnico o liviano para practicar patrones sin acumular demasiada fatiga."
            lower.contains("moderado") || lower.contains("volumen") -> "Día de volumen moderado para acumular trabajo sin llegar al máximo esfuerzo."
            lower.contains("accesorios") || lower.contains("hipertrofia") -> "Día accesorio para reforzar puntos débiles y completar volumen muscular."
            else -> "Sesión creada desde el reparto $label; ajusta ejercicios, volumen e intensidad según el objetivo de la semana."
        }
    }
}
