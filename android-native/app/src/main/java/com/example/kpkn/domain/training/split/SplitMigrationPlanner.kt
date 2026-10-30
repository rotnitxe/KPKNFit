package com.example.kpkn.domain.training.split

import com.example.kpkn.data.models.Exercise
import com.example.kpkn.data.models.Program
import com.example.kpkn.data.models.ProgramWeek
import com.example.kpkn.data.models.Session
import com.example.kpkn.data.models.SessionRequirement
import com.example.kpkn.data.models.SplitChangeSnapshot
import com.example.kpkn.data.models.SplitSnapshotWeek
import com.example.kpkn.data.models.WeekExecutionKind
import com.example.kpkn.data.models.isCompetitionMeet
import com.example.kpkn.data.models.resolvedSchedulePlan
import com.example.kpkn.data.splits.SplitDayDefinition
import com.example.kpkn.data.splits.SplitTemplate
import com.example.kpkn.data.splits.isVisibleForApplication
import com.example.kpkn.domain.text.SpanishPlurals
import com.example.kpkn.domain.training.PlanMaterializer
import com.example.kpkn.domain.training.ProgramCalendarEngine
import com.example.kpkn.domain.training.SessionDurationEstimator
import com.example.kpkn.domain.training.SplitApplicationEngine
import com.example.kpkn.domain.training.SplitTemporalScope
import com.example.kpkn.domain.training.approach.ApproachOptions
import com.example.kpkn.domain.training.approach.ApproachPlanner
import kotlinx.serialization.builtins.ListSerializer
import kotlinx.serialization.json.Json
import kotlin.math.abs

/**
 * Motor de migración de reparto (punto 23, contrato en `maps/reparto-motor.md`): cambia el reparto de una semana (o de varias)
 * **moviendo los ejercicios** a los días del reparto nuevo, con sus series, repeticiones, RIR, descansos, carga,
 * aproximación y movilidad, sin que la persona tenga que crear series otra vez.
 *
 * Es una fachada pura y determinista sobre las piezas de [SplitRedistributor] (unidades indivisibles, [DayAssigner],
 * [WeekBuilder], [DayTimeFit]) que añade lo que la pantalla necesita:
 *
 * - [plan]: el plan completo sin tocar nada (días, movimientos, volumen y frecuencia antes → después, avisos, decisiones,
 *   rechazos con su frase). El programa resultante va dentro del plan: **lo que ves es lo que se aplica**.
 * - [apply]: aplica exactamente ese plan (con su instantánea para deshacer). [undo] la restaura exacta.
 * - [rearrange]: arrastrar un día a otro día de la semana, sin recalcular quién lleva qué.
 *
 * Reglas (R0–R15 del contrato) en una línea cada una:
 * R0 puertas con su frase · R1 unidades indivisibles · R2 días en el orden del reparto · R3–R4 encaje y objetivo del
 * [DayAssigner] · R5 estabilidad (lo que ya estaba en un día tiende a quedarse) · R6 volumen intacto · R7 semanas paralelas
 * repiten el día y las distintas prefieren el del mismo ejercicio · R8 descarga sin recorte · R9 lo hecho no se toca ·
 * R10 decisiones con propuesta · R11 recorte por tiempo solo si se activa · R12 orden y metadatos · R13 avisos · R14 puro ·
 * R15 deshacer.
 */
object SplitMigrationPlanner {

    private const val MIXED_PLACES = "Cada sesión usa el material de su lugar: mueve las sesiones en lugar de cambiar el reparto."
    private const val EMPTY_REASON = "Tu programa no tiene ejercicios para repartir."
    private const val ALL_DONE = "Todas las sesiones de esta semana ya están hechas o fijadas: el reparto puede aplicarse desde la semana siguiente."
    private const val UNEXPECTED = "No se pudo adaptar el programa a este reparto."

    private val json = Json

    // ─── API pública ────────────────────────────────────────────────────────────────────────────

    /**
     * El plan de [request]. No toca el programa. Puro y determinista (misma entrada, mismo plan); pensado para
     * `Dispatchers.Default`. Un error inesperado no tumba la pantalla: sale como [MigrationPlan.Refused] con el detalle
     * técnico en `diagnostic`.
     */
    fun plan(request: MigrationRequest): MigrationPlan = try {
        planChecked(request)
    } catch (error: Exception) {
        MigrationPlan.Refused(UNEXPECTED, canUseCleanOrPrebuilt = false, diagnostic = "${error::class.simpleName}: ${error.message}")
    }

    /** Aplica el plan de [request]: el programa nuevo es exactamente `plan.program`. */
    fun apply(request: MigrationRequest): MigrationOutcome = when (val plan = plan(request)) {
        is MigrationPlan.Ok -> MigrationOutcome.Applied(apply(plan), plan)
        is MigrationPlan.Refused -> MigrationOutcome.NotApplied(plan)
    }

    /** Aplica un plan ya calculado (el que la persona vio, incluso tras arrastrar): devuelve su programa. */
    fun apply(plan: MigrationPlan.Ok): Program = plan.program

    /** Una petición nueva y su plan tras arrastrar un día. */
    data class Rearranged(val request: MigrationRequest, val plan: MigrationPlan)

    /**
     * Arrastra el día que cae en [fromWeekday] al [toWeekday] de la semana base: a un día libre se muda; a un día ocupado
     * por otra sesión nueva, se intercambian. **No recalcula quién lleva qué** (los ejercicios de cada día se clavan con
     * `placements`); cambia los días, los títulos siguen siendo los del reparto, y los avisos se recalculan. Un día hecho
     * no se arrastra ni se pisa: devuelve el mismo plan. Desde la pantalla: se guarda [Rearranged.request] como la petición
     * vigente.
     */
    fun rearrange(request: MigrationRequest, plan: MigrationPlan.Ok, fromWeekday: Int, toWeekday: Int): Rearranged {
        val unchanged = Rearranged(request, plan)
        if (toWeekday !in 1..7 || toWeekday == fromWeekday) return unchanged
        val days = plan.base.days
        val from = days.firstOrNull { it.weekday == fromWeekday && it.kind == DayKind.OPEN } ?: return unchanged
        val occupant = days.firstOrNull { it.weekday == toWeekday && it.kind != DayKind.EXTRA }
        if (occupant != null && occupant.kind != DayKind.OPEN) return unchanged
        val weekdays = plan.weekdays.toMutableList()
        weekdays[from.dayIndex] = toWeekday
        if (occupant != null) weekdays[occupant.dayIndex] = fromWeekday
        val openIndexes = days.filter { it.kind == DayKind.OPEN }.map { it.dayIndex }.toSet()
        val pinned = plan.base.units.filter { it.dayIndex in openIndexes }.associate { it.key to it.dayIndex }
        val next = request.copy(weekdays = weekdays, placements = request.placements + pinned)
        return Rearranged(next, plan(next))
    }

    /**
     * Los días de la semana que propone la interfaz (R2): los actuales de la semana [weekId] si coinciden en número con los
     * del reparto; si no, los del propio reparto desde [startDay] (en el orden del reparto).
     */
    fun proposeWeekdays(program: Program, weekId: String, split: SplitTemplate, startDay: Int = 1): List<Int> {
        val needed = SplitCatalogRules.trainingDays(split)
        val week = ProgramWeeks.all(program).firstOrNull { it.week.id == weekId }?.week
        val current = week?.sessions?.mapNotNull { weekdayOf(it) }?.distinct()?.sorted().orEmpty()
        if (current.size == needed) return current
        return SplitApplicationEngine.patternToTrainingDays(split.pattern, startDay).map { it.dayOfWeek }
    }

    // ─── Deshacer (R15) ─────────────────────────────────────────────────────────────────────────

    /** ¿Hay un cambio de reparto que deshacer? [UndoState.EDITED] = ya se editó alguna de sus semanas. */
    fun undoState(program: Program): UndoState {
        val snapshot = program.lastSplitChange ?: return UndoState.NONE
        val current = ProgramWeeks.all(program).associateBy { it.position }
        snapshot.weeks.forEachIndexed { index, saved ->
            val now = current[WeekPosition(saved.macroIndex, saved.blockIndex, saved.mesoIndex, saved.weekIndex)]?.week
            val expected = snapshot.appliedFingerprints.getOrNull(index)
            if (now == null || (expected != null && fingerprint(now) != expected)) return UndoState.EDITED
        }
        return UndoState.AVAILABLE
    }

    /**
     * Deshace el último cambio de reparto: devuelve las semanas, las selecciones de reparto y las marcas de «Sesión
     * personalizada» tal como estaban (`undo(apply(p)) == p`). Sin nada que deshacer, o con ediciones posteriores y sin
     * [force], devuelve el programa tal cual. Quien llama decide si pregunta antes de pisar las ediciones.
     */
    fun undo(program: Program, force: Boolean = false): Program {
        val snapshot = program.lastSplitChange ?: return program
        if (!force && undoState(program) != UndoState.AVAILABLE) return program
        val saved = snapshot.weeks.associate { WeekPosition(it.macroIndex, it.blockIndex, it.mesoIndex, it.weekIndex) to it.week }
        val restored = ProgramWeeks.map(program) { ref -> saved[ref.position] }
        return restored.copy(
            startDay = snapshot.startDay,
            selectedSplitId = snapshot.selectedSplitId,
            customSplitPattern = snapshot.customSplitPattern,
            customSplitName = snapshot.customSplitName,
            customSplitDescription = snapshot.customSplitDescription,
            blockSplitSelections = snapshot.blockSplitSelections,
            weekSplitSelections = snapshot.weekSplitSelections,
            splitTrialSeen = snapshot.splitTrialSeen,
            schedulePlan = snapshot.schedulePlan,
            manualSessionOverrides = snapshot.manualSessionOverrides,
            lastSplitChange = snapshot.previousChange,
        )
    }

    private fun fingerprint(week: ProgramWeek): Int =
        json.encodeToString(ListSerializer(Session.serializer()), week.sessions).hashCode()

    // ─── Plan ───────────────────────────────────────────────────────────────────────────────────

    private class Ctx(
        val request: MigrationRequest,
        val split: SplitTemplate,
        val splitName: String,
        val definitions: List<SplitDayDefinition>,
        val titles: List<String>,
        val focus: List<DayFocus>,
        val foci: List<String>,
        val place: String?,
    ) {
        val resolver: ExerciseTraitResolver get() = request.resolver
        val dayCount: Int get() = definitions.size
    }

    private sealed interface Computation

    private class Failed(val reason: String, val canClean: Boolean) : Computation

    /** Una semana ya rehecha, con lo que las siguientes necesitan de ella. */
    private class Done(
        val ref: WeekRef,
        val mode: WeekMode,
        val sessions: List<Session>,
        val plan: WeekPlan,
        /** Día del reparto de cada unidad de la semana por su clave (el de la base lo copian las paralelas). */
        val dayOfKey: Map<String, Int>,
        /** Día del reparto por identidad de ejercicio (para el día preferido de las semanas distintas). */
        val dayOfExercise: Map<String, Int>,
        val weekdays: List<Int>,
        val decisions: List<PlanDecision>,
        val builtIds: List<String>,
    ) : Computation

    private fun planChecked(request: MigrationRequest): MigrationPlan {
        val program = request.program
        val split = request.split
        val weekdays = request.weekdays
        val splitName = SplitCatalogRules.displayName(split)
        fun refuse(reason: String, canClean: Boolean = false) = MigrationPlan.Refused(reason, canClean)

        if (weekdays.isEmpty() || weekdays.any { it !in 1..7 } || weekdays.toSet().size != weekdays.size) {
            return refuse("Elige entre 1 y 7 días distintos de la semana.")
        }
        if (!request.allowAuthored && AuthoredPlans.hasFixedRecipe(program)) return refuse(SplitRedistributor.AUTHORED_REASON, true)
        if (!split.isVisibleForApplication && split.id != "custom") return refuse("Este reparto no está disponible.")
        val definitions = SplitCatalogRules.trainingDefinitions(split)
        if (definitions.isEmpty()) return refuse("Este reparto no tiene días de entreno.")
        if (definitions.size != weekdays.size) {
            return refuse("Este reparto tiene ${SpanishPlurals.days(definitions.size)} de entreno y elegiste ${weekdays.size}.")
        }
        val allRefs = ProgramWeeks.all(program)
        val places = allRefs.flatMap { it.week.sessions }.mapNotNull { it.placeId }.toSet()
        if (places.size > 1) return refuse(MIXED_PLACES, true)

        val baseRef = allRefs.firstOrNull { it.week.id == request.baseWeekId } ?: return refuse("Tu programa no tiene esa semana.")
        val titles = SplitCatalogRules.titlesOf(definitions.map { it.label })
        val ctx = Ctx(
            request = request,
            split = split,
            splitName = splitName,
            definitions = definitions,
            titles = titles,
            focus = definitions.map { DayFocusParser.parse(it) },
            foci = definitions.flatMap { it.foci }.distinct(),
            place = places.singleOrNull(),
        )

        val base = when (val computed = compute(ctx, baseRef, null, 0)) {
            is Failed -> return refuse(computed.reason, computed.canClean)
            is Done -> computed
        }

        val scopeIds = (listOf(request.baseWeekId) + request.scopeWeekIds).toSet()
        val scopeRefs = allRefs.filter { it.week.id in scopeIds }
        val computations = scopeRefs.mapIndexed { ordinal, ref ->
            if (ref.position == baseRef.position) {
                ref to base
            } else {
                ref to compute(ctx, ref, base, ordinal)
            }
        }
        val done = computations.mapNotNull { (_, c) -> c as? Done }
        val weekPlans = computations.map { (ref, c) ->
            when (c) {
                is Done -> c.plan
                is Failed -> WeekPlan(ref.week.id, ref.week.name, WeekMode.SKIPPED, skippedReason = c.reason)
            }
        }

        val program2 = assemble(ctx, base, done, baseRef)
        val scopeWarnings = scopeWarningsOf(weekPlans, done)
        val notes = notesOf(ctx, base, weekPlans, done, scopeIds.size)
        return MigrationPlan.Ok(
            splitId = split.id,
            splitName = splitName,
            weeks = weekPlans,
            baseWeekId = request.baseWeekId,
            program = program2,
            warnings = base.plan.warnings + scopeWarnings,
            decisions = base.decisions,
            notes = notes,
            weekdays = base.weekdays,
        )
    }

    // ─── Una semana ─────────────────────────────────────────────────────────────────────────────

    private enum class SessionState { OPEN, OCCUPY, EXTRA }

    /** Día de la semana de una sesión (el principal o el primero asignado), o null. */
    private fun weekdayOf(session: Session): Int? =
        session.dayOfWeek?.takeIf { it in 1..7 } ?: session.assignedDays.firstOrNull { it in 1..7 }

    private fun compute(ctx: Ctx, ref: WeekRef, base: Done?, ordinal: Int): Computation {
        val request = ctx.request
        val resolver = ctx.resolver
        val week = ref.week
        val isBase = base == null
        fun fail(reason: String, canClean: Boolean = false) = Failed(reason, canClean)

        if (week.sessions.isEmpty() || week.executionKind == WeekExecutionKind.REST) {
            return if (isBase) fail(EMPTY_REASON, true) else fail("sin sesiones")
        }
        SplitRedistributor.weekProblem(week)?.let { return fail(it, true) }
        val isDeload = week.executionKind == WeekExecutionKind.DELOAD || week.isLoopWeek

        val allUnits = WeekGather.gather(week, resolver)
        val unitsBySession = allUnits.groupBy { it.originSession }

        // R9 · qué sesiones se quedan como están.
        val initialState = week.sessions.mapIndexed { index, session ->
            val units = unitsBySession[index].orEmpty()
            val done = session.id in request.completedSessionIds
            val hasStrength = units.any { it.kind == UnitKind.STRENGTH }
            val fixedByNature = session.requirement == SessionRequirement.OPTIONAL || session.isMeetDay ||
                session.isCompetitionMeet || session.sportDocuments.isNotEmpty()
            val onlyCoreOrCardio = units.isNotEmpty() && units.none { it.kind == UnitKind.STRENGTH && it.group != SplitGroup.CORE }
            when {
                fixedByNature -> SessionState.EXTRA
                done && hasStrength -> SessionState.OCCUPY
                done -> SessionState.EXTRA
                onlyCoreOrCardio -> SessionState.EXTRA
                else -> SessionState.OPEN
            }
        }
        val dayCount = ctx.dayCount

        // Cada sesión hecha ocupa el día del reparto que más se le parece (voraz; a igual encaje, el primero).
        val occupantOfDay = IntArray(dayCount) { -1 }
        val occupying = initialState.indices.filter { initialState[it] == SessionState.OCCUPY }
        if (occupying.isNotEmpty()) {
            val scores = occupying.flatMap { s ->
                (0 until dayCount).map { j -> Triple(s, j, sessionFit(unitsBySession[s].orEmpty(), ctx.focus[j])) }
            }.sortedWith(compareByDescending<Triple<Int, Int, Double>> { it.third }.thenBy { it.second }.thenBy { it.first })
            val usedSessions = hashSetOf<Int>()
            scores.forEach { (s, j, _) ->
                if (s in usedSessions || occupantOfDay[j] >= 0) return@forEach
                occupantOfDay[j] = s
                usedSessions += s
            }
        }
        val state = initialState.mapIndexed { index, st ->
            if (st == SessionState.OCCUPY && occupantOfDay.none { it == index }) SessionState.EXTRA else st
        }
        val openSessionIdx = state.indices.filter { state[it] == SessionState.OPEN }
        val keptIdx = state.indices.filter { state[it] != SessionState.OPEN }
        if (openSessionIdx.isEmpty()) return fail(if (isBase) ALL_DONE else "ya hecha")
        val openSet = openSessionIdx.toSet()
        val occupyingSet = state.indices.filter { state[it] == SessionState.OCCUPY }.toSet()

        val openUnits = allUnits.filter { it.originSession in openSet }
        val occupyingUnits = allUnits.filter { it.originSession in occupyingSet }
        if (openUnits.sumOf { it.exerciseCount } == 0) return fail(EMPTY_REASON, true)
        val openDays = (0 until dayCount).filter { occupantOfDay[it] < 0 }
        if (openDays.isEmpty()) return fail("Esta semana ya tiene hechas tantas sesiones como días tiene el reparto.")
        if (openUnits.size < openDays.size) {
            val total = openUnits.sumOf { it.exerciseCount }
            return fail("Hay menos ejercicios que días de entreno: ${SpanishPlurals.exercises(total)} para ${SpanishPlurals.days(openDays.size)}.", true)
        }
        SplitRedistributor.missingLift(ctx.foci, openUnits + occupyingUnits)?.let { lift ->
            return fail("Tu programa no tiene $lift y este reparto lo necesita.", true)
        }

        // R2/R9 · días de la semana de cada día del reparto.
        val current = request.todayWeekday != null && week.id == (request.currentWeekId ?: request.baseWeekId)
        val today = if (current) request.todayWeekday!! else 1
        val weekdayOfDay = IntArray(dayCount) { request.weekdays[it] }
        val lockedWeekdays = hashSetOf<Int>()
        for (j in 0 until dayCount) {
            val s = occupantOfDay[j]
            if (s >= 0) {
                val own = weekdayOf(week.sessions[s])
                if (own != null) {
                    weekdayOfDay[j] = own
                    lockedWeekdays += own
                }
            }
        }
        val freeWeekdays = (today..7).filter { it !in lockedWeekdays }
        if (openDays.size > freeWeekdays.size) {
            return fail(
                "Quedan ${SpanishPlurals.days(openDays.size)} de entreno y tu semana solo tiene " +
                    "${freeWeekdays.size} ${SpanishPlurals.choose(freeWeekdays.size, "libre", "libres")}.",
            )
        }
        val validWeekdays = openDays.filter { request.weekdays[it] in freeWeekdays }
        val usedWeekdays = validWeekdays.map { request.weekdays[it] }.toSet()
        val pool = freeWeekdays.filter { it !in usedWeekdays }.iterator()
        openDays.forEach { j -> if (j !in validWeekdays) weekdayOfDay[j] = pool.next() }

        val days = (0 until dayCount).map { j ->
            DayInfo(j, weekdayOfDay[j], ctx.definitions[j].label, ctx.titles[j], ctx.focus[j])
        }

        // Unidades que entran al reparto: las de las sesiones abiertas y las de las hechas (clavadas en su día).
        val units = allUnits.filter { it.originSession in openSet || it.originSession in occupyingSet }
        val dayOfSessionOccupant = HashMap<Int, Int>()
        for (j in 0 until dayCount) if (occupantOfDay[j] >= 0) dayOfSessionOccupant[occupantOfDay[j]] = j

        // R5 · día de origen de cada sesión abierta.
        val originOfSession = originDays(week, openSessionIdx, unitsBySession, days, openDays)

        val parallel = !isBase && base != null && occupyingSet.isEmpty() &&
            week.sessions.size == base.ref.week.sessions.size &&
            units.map { it.key }.toSet() == base.dayOfKey.keys && units.size == base.dayOfKey.size

        val dayOfUnit: IntArray
        var misfits: List<Int> = emptyList()
        if (parallel && base != null) {
            dayOfUnit = IntArray(units.size) { base.dayOfKey.getValue(units[it].key) }
        } else {
            val fixed = IntArray(units.size) { -1 }
            val originPref = IntArray(units.size) { -1 }
            val preferred = IntArray(units.size) { -1 }
            units.forEachIndexed { i, unit ->
                dayOfSessionOccupant[unit.originSession]?.let { fixed[i] = it }
                if (fixed[i] < 0 && (isBase)) {
                    request.placements[unit.key]?.takeIf { it in openDays }?.let { fixed[i] = it }
                }
                if (fixed[i] < 0) originPref[i] = originOfSession[unit.originSession] ?: -1
                if (fixed[i] < 0 && base != null) {
                    val prefDays = unit.exerciseKeys.mapNotNull { base.dayOfExercise[it] }.distinct()
                    if (prefDays.size == 1 && prefDays.first() in openDays) preferred[i] = prefDays.first()
                }
            }
            val assignment = DayAssigner.assign(units, days, AssignPrefs(fixed, originPref, preferred, closedDays = (0 until dayCount).filter { occupantOfDay[it] >= 0 }.toSet()))
            dayOfUnit = assignment.dayOfUnit
            misfits = assignment.misfits
        }

        // R12 · rehacer los días abiertos.
        val positionOfDay = openDays.withIndex().associate { it.value to it.index }
        val openDayInfos = openDays.map { days[it] }
        val openIdxInUnits = units.indices.filter { units[it].originSession in openSet }
        val byDay = openDays.map { j -> openIdxInUnits.filter { dayOfUnit[it] == j }.map { units[it] } }
        val openSessions = openSessionIdx.map { week.sessions[it] }
        val mainPositions = mainPositionsOf(openSessions, openSessionIdx, units, openIdxInUnits, dayOfUnit, positionOfDay, openDays.size)
        val describe = { day: DayInfo -> SplitDayTexts.description(day.label, ctx.split.sessionDescriptions) }
        var built = WeekBuilder.build(openSessions, week.id, openDayInfos, byDay, mainPositions, describe)

        val keptSessions = keptIdx.map { week.sessions[it] }
        val takenIds = keptSessions.mapTo(hashSetOf()) { it.id }
        built = built.map { session ->
            val id = if (session.id in takenIds) WeekBuilder.uniqueId(session.id, takenIds) else session.also { takenIds += it.id }.id
            if (id == session.id) session else session.copy(id = id)
        }
        val keptMainDays = keptSessions.filter { it.isMainSession }.mapNotNull { weekdayOf(it) }.toSet()
        built = built.map { session ->
            var next = session
            if (session.isMainSession && session.dayOfWeek in keptMainDays) next = next.copy(isMainSession = false)
            if (ctx.place != null) next = next.copy(placeId = ctx.place)
            next
        }

        val infoOf = { exercise: Exercise -> resolver.approachInfoOf(exercise) }
        val approached = built.map { ApproachPlanner.apply(it, ApproachOptions(), infoOf) }
        val target = request.targetMinutes?.takeIf { !isDeload }
        val outcomes = if (target != null) approached.map { DayTimeFit.fit(it, target, ordinal, resolver) } else emptyList()
        val fitted = if (target != null && request.fitToTime) outcomes.map { it.session } else approached
        val sealed = if (openSessions.any { it.targetDurationMinutes != null }) {
            fitted.map { it.copy(targetDurationMinutes = SessionDurationEstimator.estimate(it).totalMinutes) }
        } else {
            fitted
        }
        val trims = if (target != null && request.fitToTime) outcomes.flatMap { it.trims } else emptyList()
        val merges = if (target != null && request.fitToTime) outcomes.flatMap { it.merges } else emptyList()

        val finalSessions = (sealed + keptSessions).sortedBy { weekdayOf(it) ?: 99 }
        val weekdaysSet = finalSessions.mapNotNull { weekdayOf(it) }.toSet()

        // Informe de la semana.
        val dayPlans = dayPlansOf(ctx, finalSessions, sealed, openDays, occupantOfDay, week, resolver)
        val removedIds = merges.mapTo(hashSetOf()) { it.removedExerciseId }
        val sessionByDay = HashMap<Int, Session>()
        sealed.forEachIndexed { position, session -> sessionByDay[openDays[position]] = session }
        val moves = movesOf(units, dayOfUnit, week, days, sessionByDay, originOfSession, occupyingSet, removedIds)
        val volumeBefore = SplitMuscleGroups.volume(week.sessions, resolver)
        val volumeAfter = SplitMuscleGroups.volume(finalSessions, resolver)
        val volume = SplitMuscleGroups.ALL.map { it.label }.filter { (volumeBefore[it] ?: 0.0) > 0.0 || (volumeAfter[it] ?: 0.0) > 0.0 }
            .associateWith { (volumeBefore[it] ?: 0.0) to (volumeAfter[it] ?: 0.0) }
        val freqBefore = SplitMuscleGroups.frequency(week.sessions, resolver)
        val freqAfter = SplitMuscleGroups.frequency(finalSessions, resolver)
        val frequency = SplitMuscleGroups.ALL.map { it.label }.filter { (freqBefore[it] ?: 0) > 0 || (freqAfter[it] ?: 0) > 0 }
            .associateWith { (freqBefore[it] ?: 0) to (freqAfter[it] ?: 0) }
        val atomBefore = MuscleVolume.of(week.sessions, resolver)
        val atomAfter = MuscleVolume.of(finalSessions, resolver)
        val deviation = (atomBefore.keys + atomAfter.keys).maxOfOrNull { abs((atomAfter[it] ?: 0.0) - (atomBefore[it] ?: 0.0)) } ?: 0.0

        val unitPlans = units.indices.map { i ->
            val unit = units[i]
            UnitPlan(unit.key, unitNames(unit), unit.kind == UnitKind.CARDIO, dayOfUnit[i])
        }
        val misfitNames = misfits.flatMap { unitNames(units[it]) }.distinct()
        val unclassified = units.filter { it.kind == UnitKind.STRENGTH && !it.isClassified }
            .flatMap { unit -> unit.exercises.filterIndexed { i, _ -> unit.traits.getOrNull(i) == null }.map { it.name } }

        val warnings = PlanWarnings.evaluate(
            PlanWarnings.Input(
                before = week.sessions,
                after = finalSessions,
                days = dayPlans,
                resolver = resolver,
                beforeWeekdays = week.sessions.mapNotNull { weekdayOf(it) }.toSet(),
                afterWeekdays = weekdaysSet,
                targetMinutes = target,
                misfits = misfitNames,
                unclassified = unclassified,
                isDeload = isDeload,
                splitDays = dayCount,
                previousDays = week.sessions.mapNotNull { weekdayOf(it) }.toSet().size,
            ),
        )

        val mode = when {
            isBase -> WeekMode.BASE
            parallel -> WeekMode.PARALLEL
            else -> WeekMode.INDEPENDENT
        }
        val weekPlan = WeekPlan(
            weekId = week.id,
            weekName = week.name,
            mode = mode,
            days = dayPlans,
            moves = moves,
            volume = volume,
            frequency = frequency,
            trims = trims,
            merges = merges,
            volumeDeviation = deviation,
            isDeload = isDeload,
            warnings = warnings,
            units = unitPlans,
        )

        val decisions = if (isBase) {
            decisionsOf(ctx, units, dayOfUnit, misfits, openDays, openSessionIdx, approached, outcomes, target, isDeload)
        } else {
            emptyList()
        }

        val dayOfKey = units.indices.associate { units[it].key to dayOfUnit[it] }
        val dayOfExercise = LinkedHashMap<String, Int>()
        val conflicting = hashSetOf<String>()
        units.indices.forEach { i ->
            units[i].exerciseKeys.forEach { key ->
                val existing = dayOfExercise[key]
                if (existing == null) dayOfExercise[key] = dayOfUnit[i] else if (existing != dayOfUnit[i]) conflicting += key
            }
        }
        conflicting.forEach { dayOfExercise.remove(it) }

        return Done(
            ref = ref,
            mode = mode,
            sessions = finalSessions,
            plan = weekPlan,
            dayOfKey = dayOfKey,
            dayOfExercise = dayOfExercise,
            weekdays = weekdayOfDay.toList(),
            decisions = decisions,
            builtIds = sealed.map { it.id },
        )
    }

    private fun unitNames(unit: MovableUnit): List<String> =
        if (unit.block != null) unit.block.exercises.map { it.name }.ifEmpty { listOf(unit.block.name) } else unit.exercises.map { it.name }

    /** Encaje medio de una sesión con un día, ponderado por segundos (para casar las sesiones hechas con un día del reparto). */
    private fun sessionFit(units: List<MovableUnit>, focus: DayFocus): Double {
        val strength = units.filter { it.kind == UnitKind.STRENGTH }
        val total = strength.sumOf { it.seconds.toDouble() }
        if (strength.isEmpty() || total <= 0.0) return SplitAffinity.NEUTRAL
        return strength.sumOf { unit ->
            val muscles = unit.muscles
            val fit = if (muscles == null) SplitAffinity.NEUTRAL else SplitAffinity.of(muscles, focus, unit.chain)
            fit * unit.seconds
        } / total
    }

    /**
     * R5 · el día de origen de cada sesión abierta: se empareja cada sesión con el día abierto de mayor encaje (ponderado por
     * segundos), una a una; a igual encaje (±0,04) gana el día más cercano en la semana.
     */
    private fun originDays(
        week: ProgramWeek,
        openSessionIdx: List<Int>,
        unitsBySession: Map<Int, List<MovableUnit>>,
        days: List<DayInfo>,
        openDays: List<Int>,
    ): Map<Int, Int> {
        class Pair3(val session: Int, val day: Int, val fit: Double, val distance: Int)
        val pairs = openSessionIdx.flatMap { s ->
            val own = weekdayOf(week.sessions[s])
            openDays.map { j ->
                val distance = if (own == null) 0 else minOf(abs(own - days[j].weekday), 7 - abs(own - days[j].weekday))
                Pair3(s, j, sessionFit(unitsBySession[s].orEmpty(), days[j].focus), distance)
            }
        }.toMutableList()
        val result = HashMap<Int, Int>()
        while (pairs.isNotEmpty()) {
            val best = pairs.maxOf { it.fit }
            val choice = pairs.filter { it.fit >= best - ORIGIN_TIE }
                .minWithOrNull(compareBy<Pair3> { it.distance }.thenBy { it.session }.thenBy { it.day }) ?: break
            result[choice.session] = choice.day
            pairs.removeAll { it.session == choice.session || it.day == choice.day }
        }
        return result
    }

    /** Qué días nuevos son «sesión principal» (posiciones entre los días abiertos): la misma regla que el redistribuidor. */
    private fun mainPositionsOf(
        openSessions: List<Session>,
        openSessionIdx: List<Int>,
        units: List<MovableUnit>,
        openIdxInUnits: List<Int>,
        dayOfUnit: IntArray,
        positionOfDay: Map<Int, Int>,
        positions: Int,
    ): Set<Int> {
        if (openSessions.isEmpty()) return emptySet()
        if (openSessions.all { it.isMainSession }) return (0 until positions).toSet()
        val result = linkedSetOf<Int>()
        openSessions.forEachIndexed { k, session ->
            if (!session.isMainSession) return@forEachIndexed
            val candidates = openIdxInUnits.filter { units[it].originSession == openSessionIdx[k] }
            val lead = candidates.filter { units[it].kind == UnitKind.STRENGTH }.minByOrNull { units[it].originPosition }
                ?: candidates.minByOrNull { units[it].originPosition }
            if (lead != null) positionOfDay[dayOfUnit[lead]]?.let { result += it }
        }
        return result
    }

    private fun dayPlansOf(
        ctx: Ctx,
        finalSessions: List<Session>,
        built: List<Session>,
        openDays: List<Int>,
        occupantOfDay: IntArray,
        week: ProgramWeek,
        resolver: ExerciseTraitResolver,
    ): List<DayPlan> {
        val builtIndex = built.withIndex().associate { it.value.id to openDays[it.index] }
        val lockedIndex = (0 until ctx.dayCount).filter { occupantOfDay[it] >= 0 }
            .associate { week.sessions[occupantOfDay[it]].id to it }
        return finalSessions.map { session ->
            val open = builtIndex[session.id]
            val locked = lockedIndex[session.id]
            val (kind, index) = when {
                open != null -> DayKind.OPEN to open
                locked != null -> DayKind.LOCKED to locked
                else -> DayKind.EXTRA to -1
            }
            val label = if (kind == DayKind.OPEN) ctx.definitions[index].label else session.name
            DayPlan(
                dayIndex = index,
                weekday = weekdayOf(session) ?: 0,
                label = label,
                title = session.name,
                sessionId = session.id,
                kind = kind,
                exerciseCount = session.allExercises().size,
                minutes = SessionDurationEstimator.estimate(session).totalMinutes,
                focus = FocusSummary.of(session, resolver),
                mark = SplitDayMarks.of(label),
                exercises = session.allExercises().map { it.name },
            )
        }
    }

    private fun movesOf(
        units: List<MovableUnit>,
        dayOfUnit: IntArray,
        week: ProgramWeek,
        days: List<DayInfo>,
        sessionByDay: Map<Int, Session>,
        originOfSession: Map<Int, Int>,
        occupyingSet: Set<Int>,
        removedIds: Set<String>,
    ): List<PlannedMove> = units.indices.flatMap { i ->
        val unit = units[i]
        val origin = week.sessions.getOrNull(unit.originSession)
        val day = days[dayOfUnit[i]]
        val locked = unit.originSession in occupyingSet
        val kind = when {
            locked -> MoveKind.LOCKED
            originOfSession[unit.originSession] == dayOfUnit[i] -> MoveKind.STAYS
            else -> MoveKind.MOVED
        }
        val toTitle = if (locked) origin?.name.orEmpty() else sessionByDay[dayOfUnit[i]]?.name ?: day.title
        val toWeekday = if (locked) origin?.let { weekdayOf(it) } ?: day.weekday else day.weekday
        val exercises = if (unit.block != null) unit.block.exercises else unit.exercises
        exercises.filter { it.id !in removedIds }.map { exercise ->
            PlannedMove(
                exerciseId = exercise.id,
                name = exercise.name,
                fromTitle = origin?.name.orEmpty(),
                fromWeekday = origin?.let { weekdayOf(it) },
                toTitle = toTitle,
                toWeekday = toWeekday,
                kind = kind,
                unitKey = unit.key,
            )
        }
    }

    // ─── Decisiones (R10, R11) ──────────────────────────────────────────────────────────────────

    private fun decisionsOf(
        ctx: Ctx,
        units: List<MovableUnit>,
        dayOfUnit: IntArray,
        misfits: List<Int>,
        openDays: List<Int>,
        openSessionIdx: List<Int>,
        approached: List<Session>,
        outcomes: List<DayTimeFit.Outcome>,
        target: Int?,
        isDeload: Boolean,
    ): List<PlanDecision> {
        val request = ctx.request
        val openSet = openSessionIdx.toSet()
        val misfitSet = misfits.toSet()
        val placeUnits = units.indices.mapNotNull { i ->
            val unit = units[i]
            if (unit.originSession !in openSet) return@mapNotNull null
            val reason = when {
                unit.kind == UnitKind.CARDIO -> PlaceReason.CARDIO
                unit.kind == UnitKind.STRENGTH && !unit.isClassified -> PlaceReason.UNCLASSIFIED
                unit.kind == UnitKind.STRENGTH && unit.group == SplitGroup.CORE -> PlaceReason.CORE
                i in misfitSet -> PlaceReason.MISFIT
                else -> null
            } ?: return@mapNotNull null
            PlanDecision.PlaceUnit(
                unitKey = unit.key,
                exerciseNames = unitNames(unit),
                reason = reason,
                options = openDays,
                proposedDay = dayOfUnit[i],
                chosen = request.placements[unit.key]?.let { it in openDays } == true,
            )
        }
        val fit = if (target != null && !isDeload) {
            val over = approached.filter { SessionDurationEstimator.estimate(it).totalMinutes > target * DayTimeFit.TOLERANCE }
            if (over.isEmpty()) {
                null
            } else {
                val after = outcomes.map { it.session }
                PlanDecision.FitTime(
                    targetMinutes = target,
                    overDays = over.associate { it.name to SessionDurationEstimator.estimate(it).totalMinutes },
                    wouldRemoveSets = outcomes.sumOf { o -> o.trims.sumOf { it.removedSets } },
                    wouldMergeExercises = outcomes.sumOf { it.merges.size },
                    stillOverDays = after.filter { SessionDurationEstimator.estimate(it).totalMinutes > target * DayTimeFit.TOLERANCE }
                        .associate { it.name to SessionDurationEstimator.estimate(it).totalMinutes },
                    applied = request.fitToTime,
                )
            }
        } else {
            null
        }
        return placeUnits + listOfNotNull(fit)
    }

    // ─── Programa (R12, R15) ────────────────────────────────────────────────────────────────────

    private fun assemble(ctx: Ctx, base: Done, done: List<Done>, baseRef: WeekRef): Program {
        val request = ctx.request
        val program = request.program
        val replacements = done.associate { d ->
            val trainingDays = d.sessions.mapNotNull { weekdayOf(it) }.toSet()
            d.ref.position to d.ref.week.copy(
                sessions = d.sessions,
                trainingDayDates = d.ref.week.trainingDayDates.filterKeys { it in trainingDays },
            )
        }
        var result = ProgramWeeks.map(program) { ref -> replacements[ref.position] }

        val calendarized = ProgramCalendarEngine.isCalendarized(program)
        val newStart = request.startDay?.takeIf { !calendarized && it in 1..7 }
        val coveredIds = done.map { it.ref.week.id }
        val selectedBlockId = request.blockId ?: program.macrocycles.getOrNull(baseRef.macroIndex)?.blocks?.getOrNull(baseRef.blockIndex)?.id
        var selectedSplitId = program.selectedSplitId
        var blockSelections = program.blockSplitSelections
        var weekSelections = program.weekSplitSelections
        when (request.scope) {
            SplitTemporalScope.WHOLE_PROGRAM -> {
                selectedSplitId = ctx.split.id
                blockSelections = emptyMap()
                weekSelections = emptyMap()
            }
            SplitTemporalScope.CURRENT_BLOCK -> {
                if (selectedBlockId != null) blockSelections = blockSelections + (selectedBlockId to ctx.split.id)
                weekSelections = weekSelections - coveredIds.toSet()
            }
            else -> weekSelections = weekSelections + coveredIds.associateWith { ctx.split.id }
        }
        val custom = ctx.split.id == "custom"
        result = result.copy(
            startDay = newStart ?: result.startDay,
            schedulePlan = result.resolvedSchedulePlan().copy(
                trainingDays = ProgramWeeks.trainingDays(result),
                weekStartDay = newStart ?: result.resolvedSchedulePlan().weekStartDay,
            ),
            splitTrialSeen = false,
            selectedSplitId = selectedSplitId,
            blockSplitSelections = blockSelections,
            weekSplitSelections = weekSelections,
            customSplitPattern = if (custom) ctx.split.pattern else result.customSplitPattern,
            customSplitName = if (custom) ctx.split.name else result.customSplitName,
            customSplitDescription = if (custom) ctx.split.description else result.customSplitDescription,
        )
        if (calendarized) result = ProgramCalendarEngine.materializeWeekDates(result)

        // Las sesiones de origen que ya no existen no dejan marcas huérfanas.
        val keptIds = done.flatMap { it.sessions.map { s -> s.id } }.toSet()
        val droppedIds = done.flatMap { it.ref.week.sessions.map { s -> s.id } }.filterNot { it in keptIds }.toSet()
        if (droppedIds.isNotEmpty()) {
            result = result.copy(manualSessionOverrides = result.manualSessionOverrides.filterNot { it.sessionId in droppedIds })
        }
        // En un plan con receta, cada sesión nueva se marca para que una reconstrucción posterior no deshaga el reparto.
        if (result.sourceRecipe != null) {
            done.forEach { d ->
                d.builtIds.forEach { id ->
                    result = PlanMaterializer.withManualSessionOverride(
                        program = result,
                        sessionId = id,
                        weekId = d.ref.week.id,
                        weekOccurrence = PlanMaterializer.weekOccurrenceOf(d.ref.week, d.ref.weekIndex),
                        recipeDayId = null,
                        reason = "Reparto adaptado: ${ctx.splitName}",
                        nowMs = request.nowMs,
                    )
                }
            }
        }

        // R15 · instantánea de lo que cambió.
        val before = ProgramWeeks.all(program)
        val after = ProgramWeeks.all(result).associateBy { it.position }
        val changed = before.filter { ref -> after[ref.position]?.week != ref.week }
        val snapshot = SplitChangeSnapshot(
            splitId = ctx.split.id,
            splitName = ctx.splitName,
            appliedAtMs = request.nowMs,
            weeks = changed.map { SplitSnapshotWeek(it.macroIndex, it.blockIndex, it.mesoIndex, it.weekIndex, it.week) },
            appliedFingerprints = changed.map { fingerprint(after.getValue(it.position).week) },
            startDay = program.startDay,
            selectedSplitId = program.selectedSplitId,
            customSplitPattern = program.customSplitPattern,
            customSplitName = program.customSplitName,
            customSplitDescription = program.customSplitDescription,
            blockSplitSelections = program.blockSplitSelections,
            weekSplitSelections = program.weekSplitSelections,
            splitTrialSeen = program.splitTrialSeen,
            schedulePlan = program.schedulePlan,
            manualSessionOverrides = program.manualSessionOverrides,
            // Se guarda el cambio anterior (sin su propio anterior) para no crecer sin fin: deshacer vuelve dos cambios atrás como mucho.
            previousChange = program.lastSplitChange?.copy(previousChange = null),
        )
        return result.copy(lastSplitChange = snapshot)
    }

    private fun scopeWarningsOf(weekPlans: List<WeekPlan>, done: List<Done>): List<PlanWarning> = buildList {
        val lockedIds = done.flatMap { d -> d.plan.days.filter { it.kind == DayKind.LOCKED }.map { it.sessionId } }
        if (lockedIds.isNotEmpty()) add(PlanWarning(WarningKind.LOCKED_KEPT, WarningSeverity.INFO, mapOf("sessionIds" to lockedIds)))
        weekPlans.filter { it.mode == WeekMode.SKIPPED }.forEach {
            add(PlanWarning(WarningKind.SKIPPED_WEEK, WarningSeverity.INFO, mapOf("weekId" to it.weekId, "reason" to it.skippedReason.orEmpty())))
        }
        weekPlans.filter { it.isDeload && it.mode != WeekMode.SKIPPED }.forEach {
            add(PlanWarning(WarningKind.DELOAD_KEPT, WarningSeverity.INFO, mapOf("weekId" to it.weekId)))
        }
    }

    private fun notesOf(ctx: Ctx, base: Done, weekPlans: List<WeekPlan>, done: List<Done>, scopeSize: Int): List<String> = buildList {
        val request = ctx.request
        if (request.program.sourceRecipe != null || AuthoredPlans.hasFixedRecipe(request.program)) {
            add("El plan «${request.program.name}» cambia su estructura original: sus ejercicios quedan repartidos según «${ctx.splitName}».")
        }
        if (scopeSize > 1) {
            val skipped = weekPlans.count { it.mode == WeekMode.SKIPPED }
            if (skipped > 0) {
                add("Se adaptaron ${done.size} de $scopeSize semanas; las que no se pudieron adaptar conservan su reparto.")
            } else {
                add("Se aplicó a las $scopeSize semanas elegidas.")
            }
            val independent = weekPlans.count { it.mode == WeekMode.INDEPENDENT }
            if (independent > 0) {
                add("${SpanishPlurals.withNoun(independent, "semana trae", "semanas traen")} otros ejercicios: su reparto se calculó por separado.")
            }
        }
        val minutes = base.plan.days.filter { it.kind == DayKind.OPEN }.map { it.minutes }
        val shortest = minutes.minOrNull() ?: 0
        val longest = minutes.maxOrNull() ?: 0
        if (shortest > 0 && longest.toDouble() / shortest > IMBALANCE_RATIO) {
            val day = base.plan.days.first { it.kind == DayKind.OPEN && it.minutes == longest }
            add("Los días quedan de $shortest a $longest min: «${day.title}» concentra más trabajo porque cada día conserva su foco.")
        }
        val target = request.targetMinutes
        if (target != null && request.fitToTime) {
            base.plan.days.filter { it.kind == DayKind.OPEN }.forEach { day ->
                val removed = base.plan.trims.filter { it.dayTitle == day.title }.sumOf { it.removedSets }
                if (removed > 0) {
                    add(
                        "Para que quepa en $target min, ${SplitDayTexts.onWeekday(day.weekday)} lleva " +
                            SpanishPlurals.withNoun(removed, "serie menos de accesorios", "series menos de accesorios") + ".",
                    )
                }
            }
        }
        if (base.plan.volumeDeviation > 0.5) {
            add("El volumen semanal de algún músculo cambió en ${String.format(java.util.Locale.ROOT, "%.1f", base.plan.volumeDeviation)} series.")
        }
    }

    /** A igual encaje (±0,04) gana el día más cercano (R5). */
    private const val ORIGIN_TIE = 0.04

    /** Si el día más largo supera al más corto en más de esta proporción se avisa de que los días quedan desparejos. */
    private const val IMBALANCE_RATIO = 1.4
}
