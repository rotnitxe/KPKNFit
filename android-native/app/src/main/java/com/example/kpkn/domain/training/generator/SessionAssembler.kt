package com.example.kpkn.domain.training.generator

import com.example.kpkn.data.models.CardioIntensity
import com.example.kpkn.data.models.CardioType
import com.example.kpkn.data.models.Exercise
import com.example.kpkn.data.models.ExerciseSet
import com.example.kpkn.data.models.IntensityMode
import com.example.kpkn.data.models.RepRange
import com.example.kpkn.data.models.Session
import com.example.kpkn.data.models.SessionOrigin
import com.example.kpkn.data.models.SessionPart
import com.example.kpkn.data.models.SessionRequirement
import com.example.kpkn.data.models.SupersetGroup
import com.example.kpkn.data.models.TrainingMode
import com.example.kpkn.data.protocols.LoadBasis
import com.example.kpkn.data.protocols.PlanLoadReference
import com.example.kpkn.data.protocols.PlanLoadReferenceKind
import com.example.kpkn.data.protocols.PlanLoadReferenceState
import com.example.kpkn.data.protocols.SlotRole
import com.example.kpkn.data.protocols.TechniqueModifier
import com.example.kpkn.domain.onboarding.TrainingPlace
import com.example.kpkn.domain.training.NativeLoadConventions
import com.example.kpkn.domain.training.approach.ApproachLevel
import com.example.kpkn.domain.training.approach.ApproachOptions
import com.example.kpkn.domain.training.approach.ApproachPlanner
import com.example.kpkn.data.models.LoadQuantityConvention
import kotlin.math.abs
import kotlin.math.ceil

/** Una sesión terminada, con lo que el resumen y el informe necesitan saber de ella. */
internal data class AssembledSession(
    val session: Session,
    val plan: SessionPlan,
    val dayOfWeek: Int,
    val place: TrainingPlace?,
    val kind: RoutineSessionKind,
    val seconds: Int,
    val minutes: Int,
    val inWindow: Boolean,
    val patterns: List<RoutinePattern>,
    val configurationIds: List<String>,
    val strengthCount: Int,
    val hasCardio: Boolean,
    val hasMobility: Boolean,
    val mainExerciseNames: List<String>,
)

/**
 * Arma UNA sesión: elige los ejercicios de cada hueco con el material del lugar del día, los prescribe, los ajusta a
 * minutos (`MinuteFitter`), añade cardio y movilidad, completa la aproximación (`ApproachPlanner`) y verifica el resultado
 * con el estimador común, corrigiéndolo si hace falta.
 *
 * Decisiones de dominio:
 * - Orden de ejecución: potencia, principales, secundarios, accesorios, aislamiento, acarreo y core. Un hueco prioritario
 *   (la persona pidió mejorar ese músculo) se adelanta dentro de su grupo.
 * - Superseries solo entre huecos marcados como pareja y solo con poco tiempo (≤ 40 min) o en sesiones funcionales: son
 *   la forma de meter más ejercicios sin acortar descansos de los compuestos. Si una pareja no entra (por tiempo o porque el
 *   presupuesto de volumen de uno de sus dos ejercicios está agotado) se intenta cada ejercicio por separado.
 * - Prioridades: tras un primer ajuste, cada hueco prioritario sube a +25 % de las series que consiguió (`Bundle.boostFloor`).
 *   El tiempo nunca se lo quita antes que a lo demás: lo prioritario es lo último que se recorta (`MinuteFitter.trimOrder`) y, si ni
 *   así cabe, la rutina lo dice en una nota.
 * - Si tras ajustar sobran más de 4 min, se añaden ejercicios de relleno (el patrón cuyo músculo está más lejos de su objetivo
 *   semanal, uno por patrón y como mucho dos de core) antes de alargar descansos, movilidad o cardio.
 * - Con más tiempo del que la fuerza absorbe de forma útil (tope por nivel: 70/80/110/130 min) o cuando el material y los
 *   techos de volumen ya no dan más ejercicios, el sobrante va a movilidad y cardio suave. Un bloque de cardio no pasa de 40
 *   min en intervalos ni de 75 continuo (`CardioBuilder.split`).
 * - La aproximación la pone `ApproachPlanner` (D3) al final, con el MISMO proveedor de datos del catálogo que el materializador de
 *   planes (`GeneratorCatalog.approachInfoOf`): su sobrecarga de tiempo (rampa y movilidad) se mide con el estimador común y se
 *   descuenta, y una segunda pasada del materializador no cambia nada.
 */
internal object SessionAssembler {

    private const val MIN_STRENGTH_ITEMS = 3
    private const val MAX_CORRECTIONS = 12

    private fun strengthCapSeconds(level: RoutineLevel): Int = when (level) {
        RoutineLevel.NOVICE -> 70
        RoutineLevel.RETURNING -> 80
        RoutineLevel.INTERMEDIATE -> 110
        RoutineLevel.ADVANCED -> 130
    } * 60

    private fun minutesOf(seconds: Int): Int = ceil(seconds / 60.0).toInt().coerceAtLeast(1)

    private fun defaultRole(pattern: RoutinePattern): ItemRole = when (pattern) {
        RoutinePattern.SQUAT, RoutinePattern.HINGE, RoutinePattern.HORIZONTAL_PUSH, RoutinePattern.VERTICAL_PUSH,
        RoutinePattern.HORIZONTAL_PULL, RoutinePattern.VERTICAL_PULL,
        -> ItemRole.SECONDARY
        RoutinePattern.GLUTE, RoutinePattern.SINGLE_LEG -> ItemRole.ACCESSORY
        RoutinePattern.CORE_STABILITY, RoutinePattern.CORE_ROTATION, RoutinePattern.BACK_EXTENSION -> ItemRole.CORE
        RoutinePattern.CARRY -> ItemRole.CARRY
        RoutinePattern.POWER -> ItemRole.POWER
        else -> ItemRole.ISOLATION
    }

    /**
     * Patrones con los que se completa una sesión (de más a menos útil por defecto), filtrados por la región de la sesión:
     * una sesión de torso no recibe trabajo de pierna de relleno ni una de pierna, brazos.
     */
    private val FILLER_ORDER = listOf(
        RoutinePattern.CORE_STABILITY, RoutinePattern.BICEPS, RoutinePattern.TRICEPS, RoutinePattern.SHOULDER_LATERAL,
        RoutinePattern.CALF, RoutinePattern.REAR_DELT, RoutinePattern.GLUTE, RoutinePattern.HAMSTRING_CURL,
        RoutinePattern.QUAD_ISOLATION, RoutinePattern.CHEST_ISOLATION, RoutinePattern.TRAPS, RoutinePattern.SINGLE_LEG,
        RoutinePattern.BACK_EXTENSION, RoutinePattern.CORE_ROTATION, RoutinePattern.GRIP,
    )

    /** Relleno de la sesión: los patrones propios del plan (disciplinas) o los generales que admite su región. */
    private fun fillerPatterns(plan: SessionPlan): List<RoutinePattern> =
        plan.fillers ?: FILLER_ORDER.filter { PatternCatalog.accepts(plan.region, it) }

    /** Para llegar al mínimo de ejercicios: primero los propios de la disciplina y, si no alcanzan, los generales. */
    private fun minimumFillerPatterns(plan: SessionPlan): List<RoutinePattern> =
        (plan.fillers.orEmpty() + FILLER_ORDER.filter { PatternCatalog.accepts(plan.region, it) }).distinct()

    private val CORE_PATTERNS = setOf(RoutinePattern.CORE_STABILITY, RoutinePattern.CORE_ROTATION, RoutinePattern.BACK_EXTENSION)

    /** Tiempo libre por debajo del cual no se añade otro ejercicio de relleno (un accesorio de 3 series ronda los 5 min). */
    private const val FILL_SLACK_SECONDS = 4 * 60
    private const val MAX_FILLERS = 6

    private fun approachLevel(level: RoutineLevel): ApproachLevel = when (level) {
        RoutineLevel.NOVICE, RoutineLevel.RETURNING -> ApproachLevel.NOVICE
        RoutineLevel.INTERMEDIATE -> ApproachLevel.INTERMEDIATE
        RoutineLevel.ADVANCED -> ApproachLevel.ADVANCED
    }

    // ─── Estado mutable de la sesión mientras se ajusta ──────────────────────────────────────────────────────

    private class State(
        val bundles: List<Bundle>,
        var mobilitySeconds: Int,
        var cardioSeconds: Int,
        var cardioIntervals: Boolean,
        var fillerCardioSeconds: Int,
        val useMobilityPart: Boolean,
    )

    // ─── Entrada ───────────────────────────────────────────────────────────────────────────────────────────

    fun assemble(
        ctx: GenContext,
        plan: SessionPlan,
        dayOfWeek: Int,
        place: TrainingPlace?,
        equipment: DayEquipment,
        sessionIndex: Int,
        isMain: Boolean,
        caps: SessionCaps,
    ): AssembledSession {
        val sessionId = "${ctx.programId}-s$sessionIndex"
        val targetSec = ctx.targetSeconds
        val loMin = ctx.windowMinutes.first
        val hiMin = ctx.windowMinutes.last
        val loSec = (loMin - 1) * 60 + 1
        val hiSec = hiMin * 60

        // 1) Huecos → ejercicios con el material del día.
        val use = SessionUse()
        val drafts = ArrayList<DraftItem>()
        val strengthWanted = plan.slots.isNotEmpty()
        plan.slots.forEachIndexed { index, spec ->
            val candidate = ExerciseSelector.choose(spec.pattern, spec.role, ctx, equipment, use, salt = sessionIndex * 17 + index, region = plan.region, tag = spec.tag)
            if (candidate == null) {
                ctx.missing[spec.pattern] = (ctx.missing[spec.pattern] ?: 0) + 1
                return@forEachIndexed
            }
            use.add(candidate)
            drafts += draftOf(ctx, spec, candidate, drafts.size)
        }
        if (strengthWanted && plan.kind != RoutineSessionKind.CARDIO && drafts.size < MIN_STRENGTH_ITEMS && plan.cardio != CardioMode.INTERVALS_FILL) {
            for (pattern in minimumFillerPatterns(plan)) {
                if (drafts.size >= MIN_STRENGTH_ITEMS) break
                val role = defaultRole(pattern)
                val candidate = ExerciseSelector.choose(pattern, role, ctx, equipment, use, salt = sessionIndex * 17 + 99, region = plan.region) ?: continue
                use.add(candidate)
                drafts += draftOf(ctx, SlotSpec(pattern, role, extra = true, hard = true), candidate, drafts.size)
            }
        }

        // 2) Parejas (superseries).
        val useSupersets = plan.supersets || ctx.targetMinutes <= 40
        val bundles = ArrayList<Bundle>()
        var cursor = 0
        while (cursor < drafts.size) {
            val first = drafts[cursor]
            val second = drafts.getOrNull(cursor + 1)
            if (useSupersets && first.spec.pairKey != null && second != null && second.spec.pairKey == first.spec.pairKey) {
                bundles += Bundle(listOf(first, second))
                cursor += 2
            } else {
                bundles += Bundle(listOf(first))
                cursor += 1
            }
        }

        // 3) Cardio y movilidad fijos del plan.
        var mobilitySeconds = 0
        val mobilitySpec = plan.mobility
        if (mobilitySpec != null && !mobilitySpec.fill) {
            mobilitySeconds = when {
                ctx.targetMinutes >= 30 -> mobilitySpec.seconds
                ctx.targetMinutes >= 25 -> 120
                else -> 0
            }
        }
        var cardioSeconds = 0
        var cardioIntervals = false
        var strengthTargetSec = targetSec
        when (plan.cardio) {
            CardioMode.BLOCK -> {
                val block = ctx.cardioBlockMinutes() * 60
                val roomForStrength = targetSec - (block + CardioBuilder.SETUP_SECONDS) - mobilitySeconds
                // Con poco tiempo no cabe el bloque de cardio + una sesión de fuerza mínima: el cardio no se recorta, se omite
                // en esta sesión (y se avisa), porque la mitad de un bloque no es el bloque que la persona pidió.
                if (!strengthWanted || drafts.isEmpty() || roomForStrength >= MIN_STRENGTH_SECONDS) {
                    cardioSeconds = block
                } else {
                    ctx.notes += cardioOmittedNote(ctx)
                }
            }
            CardioMode.ZONE2_FILL -> {
                mobilitySeconds = if (ctx.targetMinutes >= 40) 300 else 0
                cardioSeconds = (targetSec - CardioBuilder.SETUP_SECONDS - mobilitySeconds).coerceAtLeast(300)
            }
            CardioMode.INTERVALS_FILL -> {
                cardioIntervals = true
                if (drafts.isNotEmpty()) {
                    strengthTargetSec = (targetSec * 0.35).toInt()
                    cardioSeconds = (targetSec - strengthTargetSec - CardioBuilder.SETUP_SECONDS).coerceAtLeast(0)
                } else {
                    cardioSeconds = (targetSec - CardioBuilder.SETUP_SECONDS).coerceAtLeast(300)
                }
            }
            CardioMode.LIGHT_FILL -> {
                mobilitySeconds = (targetSec * 0.4).toInt().coerceIn(8 * 60, 45 * 60)
                cardioSeconds = (targetSec - mobilitySeconds - CardioBuilder.SETUP_SECONDS).coerceAtLeast(300)
            }
            null -> Unit
        }
        if (plan.cardio == CardioMode.INTERVALS_FILL && cardioSeconds < 8 * 60) {
            // Poco tiempo para intervalos: sin potencia, toda la sesión es cardio.
            cardioSeconds = (targetSec - CardioBuilder.SETUP_SECONDS).coerceAtLeast(300)
            bundles.clear()
            drafts.clear()
        }

        val cardioPartSeconds = if (cardioSeconds > 0) TimeModel.cardioSeconds(cardioSeconds) else 0
        val mobilityBlockSeconds = if (mobilitySeconds > 0) mobilitySeconds else 0
        val fixedBase = cardioPartSeconds + mobilityBlockSeconds

        // 4) Ajuste a minutos (con la sobrecarga de aproximación medida en una primera pasada).
        val strengthTop = minOf(targetSec, fixedBase + strengthCapSeconds(ctx.level))
        val minItems = if (drafts.isEmpty()) 0 else minOf(MIN_STRENGTH_ITEMS, drafts.size).let { if (plan.cardio == CardioMode.INTERVALS_FILL) minOf(1, it) else it }
        val room = VolumeRoom(ctx, caps, sessionIndex)

        fun envWith(overhead: Int) = FitEnv(
            fixedSeconds = fixedBase + overhead,
            targetSec = strengthTop,
            loSec = loSec,
            hiSec = minOf(hiSec, strengthTop + (hiSec - targetSec).coerceAtLeast(0)),
            minItems = minItems,
            room = room,
        )

        if (bundles.isNotEmpty()) {
            fitRepairingAndFilling(ctx, plan, bundles, drafts, use, equipment, room, envWith(0), sessionIndex)
        }
        val state = State(
            bundles = bundles,
            mobilitySeconds = mobilitySeconds,
            cardioSeconds = cardioSeconds,
            cardioIntervals = cardioIntervals,
            fillerCardioSeconds = 0,
            useMobilityPart = mobilitySeconds > 0 || (mobilitySpec != null && mobilitySpec.fill),
        )
        var overhead = 0
        if (bundles.any { it.included }) {
            val unplanned = build(ctx, plan, state, sessionId, dayOfWeek, isMain, equipment, sessionIndex)
            val planned = approach(ctx, unplanned)
            overhead = (TimeModel.sessionSeconds(planned) - TimeModel.sessionSeconds(unplanned)).coerceAtLeast(0)
            if (overhead > 0) MinuteFitter.fit(bundles, envWith(overhead))
        }

        // 5) Relleno de tiempo cuando la fuerza ya no admite más (tope por nivel o material agotado).
        var fillerNote = false
        run {
            val unplanned = build(ctx, plan, state, sessionId, dayOfWeek, isMain, equipment, sessionIndex)
            val measured = TimeModel.sessionSeconds(approach(ctx, unplanned))
            if (measured < loSec && plan.kind != RoutineSessionKind.CARDIO) {
                val gap = targetSec - measured
                val extraMobility = if (state.mobilitySeconds < 12 * 60 && gap >= 150) minOf(gap / 2, 12 * 60 - state.mobilitySeconds) else 0
                state.mobilitySeconds += extraMobility
                val remaining = gap - extraMobility
                if (remaining >= 10 * 60 + CardioBuilder.SETUP_SECONDS) {
                    state.fillerCardioSeconds = remaining - CardioBuilder.SETUP_SECONDS
                } else if (remaining >= 90) {
                    state.mobilitySeconds += remaining
                }
                fillerNote = extraMobility >= 180 || state.fillerCardioSeconds > 0
            }
        }
        if (fillerNote) ctx.notes += longSessionNote(ctx)

        // 6) Verificación con el estimador y corrección fina.
        var best: Built? = null
        var iteration = 0
        while (true) {
            val unplanned = build(ctx, plan, state, sessionId, dayOfWeek, isMain, equipment, sessionIndex)
            val planned = approach(ctx, unplanned)
            val seconds = TimeModel.sessionSeconds(planned)
            val minutes = minutesOf(seconds)
            val distance = when {
                minutes < loMin -> loMin - minutes
                minutes > hiMin -> minutes - hiMin
                else -> 0
            }
            val current = Built(planned, seconds, distance, snapshot(state))
            if (best == null || current.distance < best.distance ||
                (current.distance == best.distance && abs(current.seconds - targetSec) < abs(best.seconds - targetSec))
            ) {
                best = current
            }
            if (distance == 0 && abs(seconds - targetSec) <= 90) break
            if (iteration >= MAX_CORRECTIONS) break
            val changed = if (seconds > hiSec) reduceOnce(state, ctx, MIN_STRENGTH_ITEMS.coerceAtMost(bundles.sumOf { it.items.size }))
            else if (seconds < targetSec) growOnce(state, ctx, room, plan) else false
            if (!changed) break
            iteration++
        }
        val final = requireNotNull(best)
        restore(state, final.snapshot)
        // Lo prioritario es lo último que el tiempo recorta; si aun así no cabe todo lo que se pidió, se dice (una sola nota por rutina).
        val squeezed = state.bundles.any { bundle ->
            if (!bundle.isPriority) return@any false
            val reachable = minOf(bundle.baseSets, room.maxSets(bundle, state.bundles))
            reachable >= bundle.minSets && (if (bundle.included) bundle.sets else 0) < reachable
        }
        if (squeezed) ctx.notes += priorityTimeNote(ctx)
        val finalSession = final.session.copy(targetDurationMinutes = minutesOf(final.seconds))
        val inWindow = minutesOf(final.seconds) in ctx.windowMinutes

        // 7) Consolidar uso semanal y volumen desde la sesión REAL.
        val strengthExercises = finalSession.exercises
        strengthExercises.forEach { exercise ->
            val id = exercise.catalogConfigurationId ?: return@forEach
            ctx.weeklyUse[id] = (ctx.weeklyUse[id] ?: 0) + 1
            ctx.catalog.entry(id)?.let { ctx.ledger.add(it, exercise.sets.size) }
        }
        val includedItems = state.bundles.filter { it.included }.flatMap { it.items }
        includedItems.forEach { ctx.covered += it.spec.pattern }
        val hasCardio = finalSession.parts.any { it.isCardioGroup }
        val hasMobility = finalSession.parts.any { it.isMobilityGroup && it.mobilitySeries.isNotEmpty() }
        if (hasCardio) ctx.covered += RoutinePattern.CARDIO
        if (hasMobility) ctx.covered += RoutinePattern.MOBILITY
        val kind = when {
            plan.kind == RoutineSessionKind.MOBILITY -> RoutineSessionKind.MOBILITY
            plan.cardio == CardioMode.ZONE2_FILL || plan.cardio == CardioMode.INTERVALS_FILL -> RoutineSessionKind.CARDIO
            strengthExercises.isNotEmpty() && hasCardio -> RoutineSessionKind.MIXED
            strengthExercises.isNotEmpty() -> RoutineSessionKind.STRENGTH
            else -> RoutineSessionKind.CARDIO
        }
        if (!inWindow) ctx.outside += plan.title to minutesOf(final.seconds)
        val ordered = includedItems.sortedBy { it.priority }
        return AssembledSession(
            session = finalSession,
            plan = plan,
            dayOfWeek = dayOfWeek,
            place = place,
            kind = kind,
            seconds = final.seconds,
            minutes = minutesOf(final.seconds),
            inWindow = inWindow,
            patterns = ordered.map { it.spec.pattern }.distinct() +
                listOfNotNull(RoutinePattern.CARDIO.takeIf { hasCardio }, RoutinePattern.MOBILITY.takeIf { hasMobility }),
            configurationIds = strengthExercises.mapNotNull { it.catalogConfigurationId },
            strengthCount = strengthExercises.size,
            hasCardio = hasCardio,
            hasMobility = hasMobility,
            mainExerciseNames = ordered.filter { it.spec.role == ItemRole.MAIN || it.spec.role == ItemRole.SECONDARY }
                .map { it.candidate.entry.legacy.name }.distinct().take(4),
        )
    }

    /**
     * Ajuste a minutos con dos reparaciones:
     * 1. Una pareja de superserie que no entró (por tiempo o porque el presupuesto de volumen de UNO de sus dos ejercicios
     *    está agotado) se deshace y se intenta cada ejercicio por separado: un hueco bloqueado no debe arrastrar al otro.
     * 2. Si tras el ajuste sobra más de 4 min y ningún bloque admite más series, se añaden ejercicios de relleno (accesorios
     *    del patrón cuyo músculo más lejos está de su objetivo semanal) antes de recurrir a descansos más largos, movilidad o
     *    cardio suave: más trabajo útil primero (el orden del brief: series, accesorios, descansos, finalizador).
     */
    private fun fitRepairingAndFilling(
        ctx: GenContext,
        plan: SessionPlan,
        bundles: ArrayList<Bundle>,
        drafts: ArrayList<DraftItem>,
        use: SessionUse,
        equipment: DayEquipment,
        room: VolumeRoom,
        env: FitEnv,
        sessionIndex: Int,
    ) {
        MinuteFitter.fit(bundles, env)
        // Prioridades: tras un primer ajuste cada hueco prioritario sube a +25 % de las series que consiguió y se ajusta de nuevo.
        val boosted = bundles.filter { bundle -> bundle.included && bundle.items.any { it.spec.boosted } }
        if (boosted.isNotEmpty()) {
            boosted.forEach { it.boostFloor = ceil(it.sets * 1.25).toInt() }
            MinuteFitter.fit(bundles, env)
        }
        if (bundles.any { it.isPair && !it.included }) {
            val rebuilt = ArrayList<Bundle>()
            bundles.forEach { bundle ->
                if (bundle.isPair && !bundle.included) bundle.items.forEach { rebuilt += Bundle(listOf(it)) } else rebuilt += bundle
            }
            bundles.clear()
            bundles.addAll(rebuilt)
            MinuteFitter.fit(bundles, env)
        }
        if (plan.region == SessionRegion.NONE || plan.kind == RoutineSessionKind.CARDIO) return
        var added = 0
        while (added < MAX_FILLERS && MinuteFitter.total(bundles, env) < env.targetSec - FILL_SLACK_SECONDS) {
            if (!addFiller(ctx, plan, bundles, drafts, use, equipment, room, env, sessionIndex, hard = false)) break
            added++
        }
        // Mínimo de ejercicios: si el objetivo semanal (MAV) de los músculos de relleno ya está agotado por las demás sesiones,
        // se acepta el techo duro (MRV) antes que dejar una sesión de fuerza con menos de 3 ejercicios.
        var guard = 0
        while (MinuteFitter.itemCount(bundles) < MIN_STRENGTH_ITEMS && guard++ < MAX_FILLERS) {
            if (!addFiller(ctx, plan, bundles, drafts, use, equipment, room, env, sessionIndex, hard = true)) break
        }
    }

    /** Añade un ejercicio de relleno y reajusta; false si no hay ninguno posible o el ajuste no lo admite (entonces se retira). */
    private fun addFiller(
        ctx: GenContext,
        plan: SessionPlan,
        bundles: ArrayList<Bundle>,
        drafts: ArrayList<DraftItem>,
        use: SessionUse,
        equipment: DayEquipment,
        room: VolumeRoom,
        env: FitEnv,
        sessionIndex: Int,
        hard: Boolean,
    ): Boolean {
        val draft = pickFiller(ctx, plan, bundles, drafts, use, equipment, room, sessionIndex, hard) ?: return false
        val bundle = Bundle(listOf(draft))
        drafts += draft
        bundles += bundle
        use.add(draft.candidate)
        MinuteFitter.fit(bundles, env)
        if (bundle.included) return true
        bundles.remove(bundle)
        drafts.remove(draft)
        MinuteFitter.fit(bundles, env)
        return false
    }

    /**
     * El ejercicio de relleno de la sesión: entre los patrones de [FILLER_ORDER] que admite la región, el que tiene presupuesto
     * de volumen y trabaja los músculos con más series pendientes hasta su objetivo semanal (lo que ya hicieron las demás
     * sesiones y esta misma cuenta). Con el mismo déficit manda el orden de [FILLER_ORDER].
     */
    private fun pickFiller(
        ctx: GenContext,
        plan: SessionPlan,
        bundles: List<Bundle>,
        drafts: List<DraftItem>,
        use: SessionUse,
        equipment: DayEquipment,
        room: VolumeRoom,
        sessionIndex: Int,
        hard: Boolean,
    ): DraftItem? {
        val inSession = HashMap<String, Double>()
        bundles.filter { it.included }.forEach { bundle ->
            bundle.items.forEach { item ->
                item.candidate.entry.contributions.forEach { (muscle, c) ->
                    if (c.direct > 0.0) inSession[muscle] = (inSession[muscle] ?: 0.0) + bundle.sets * c.direct
                }
            }
        }
        var best: DraftItem? = null
        var bestScore = Double.NEGATIVE_INFINITY
        // Un patrón que la sesión ya trabaja no se repite de relleno (dos extensiones de cuádriceps no son más sesión) y el
        // core (estabilidad, rotación y extensión de espalda) no pasa de dos ejercicios entre los del plan y los de relleno.
        val present = drafts.mapTo(HashSet()) { it.spec.pattern }
        val coreCount = drafts.count { it.spec.pattern in CORE_PATTERNS }
        (if (hard) minimumFillerPatterns(plan) else fillerPatterns(plan)).forEachIndexed { order, pattern ->
            if (pattern in present) return@forEachIndexed
            if (pattern in CORE_PATTERNS && coreCount >= 2) return@forEachIndexed
            val role = defaultRole(pattern)
            val candidate = ExerciseSelector.choose(pattern, role, ctx, equipment, use, salt = sessionIndex * 17 + 200 + order, region = plan.region)
                ?: return@forEachIndexed
            val draft = draftOf(ctx, SlotSpec(pattern, role, extra = true, hard = hard), candidate, drafts.size)
            val probe = Bundle(listOf(draft))
            if (room.maxSets(probe, bundles) < probe.minSets) return@forEachIndexed
            val deficit = PatternCatalog.of(pattern).muscles.sumOf { muscle ->
                (ctx.budgets.target(muscle) - ctx.ledger.directOf(muscle) - (inSession[muscle] ?: 0.0)).coerceAtLeast(0.0)
            }
            val score = deficit - order * 0.01
            if (score > bestScore) {
                bestScore = score
                best = draft
            }
        }
        return best
    }

    private class Snapshot(
        val bundles: List<Triple<Boolean, Int, Int>>,
        val mobilitySeconds: Int,
        val cardioSeconds: Int,
        val fillerCardioSeconds: Int,
    )

    private class Built(val session: Session, val seconds: Int, val distance: Int, val snapshot: Snapshot)

    private fun snapshot(state: State) = Snapshot(
        bundles = state.bundles.map { Triple(it.included, it.sets, it.rest) },
        mobilitySeconds = state.mobilitySeconds,
        cardioSeconds = state.cardioSeconds,
        fillerCardioSeconds = state.fillerCardioSeconds,
    )

    private fun restore(state: State, snapshot: Snapshot) {
        state.bundles.forEachIndexed { index, bundle ->
            val (included, sets, rest) = snapshot.bundles[index]
            bundle.included = included
            bundle.sets = sets
            bundle.rest = rest
        }
        state.mobilitySeconds = snapshot.mobilitySeconds
        state.cardioSeconds = snapshot.cardioSeconds
        state.fillerCardioSeconds = snapshot.fillerCardioSeconds
    }

    private const val MIN_STRENGTH_SECONDS = 13 * 60

    private fun approach(ctx: GenContext, session: Session): Session {
        if (session.exercises.isEmpty()) return session
        return ApproachPlanner.apply(session, ApproachOptions(level = approachLevel(ctx.level)), ctx.catalog.approachInfoOf)
    }

    // ─── Prescripción ──────────────────────────────────────────────────────────────────────────────────────

    private fun draftOf(ctx: GenContext, spec: SlotSpec, candidate: Candidate, priority: Int): DraftItem {
        if (spec.pattern == RoutinePattern.HINGE && candidate.fromLadder) ctx.flags += "hinge_bodyweight"
        val standard = Prescriber.rx(
            kind = candidate.kind,
            role = spec.role,
            level = ctx.level,
            mode = ctx.mode,
            pattern = spec.pattern,
            rungReps = candidate.reps,
            rungSeconds = candidate.seconds,
            entryReps = candidate.reps,
        )
        // Un hueco prioritario admite una serie más que el máximo habitual: si el ejercicio ya iba al máximo (los
        // aislamientos suben a 4 series cuando sobra tiempo), sin esta serie la prioridad no cambiaría nada.
        val rx = if (spec.boosted) standard.copy(maxSets = standard.maxSets + 1) else standard
        val base = if (spec.role == ItemRole.ISOLATION && spec.extra && !spec.boosted) maxOf(rx.minSets, rx.baseSets - 1) else rx.baseSets
        return DraftItem(spec, candidate, rx, priority, base.coerceIn(rx.minSets, rx.maxSets))
    }

    private fun execRank(item: DraftItem): Double {
        val base = item.spec.role.execRank
        return if (item.spec.boosted) base - (if (item.spec.role == ItemRole.MAIN) 0.1 else 0.5) else base
    }

    // ─── Construcción de la sesión ─────────────────────────────────────────────────────────────────────────

    private fun build(
        ctx: GenContext,
        plan: SessionPlan,
        state: State,
        sessionId: String,
        dayOfWeek: Int,
        isMain: Boolean,
        equipment: DayEquipment,
        sessionIndex: Int,
    ): Session {
        val ordered = state.bundles.filter { it.included }.sortedWith(
            compareBy<Bundle> { bundle -> bundle.items.minOf { execRank(it) } }.thenBy { it.priority },
        )
        val exercises = ArrayList<Exercise>()
        val groups = ArrayList<SupersetGroup>()
        ordered.forEach { bundle ->
            val groupId = if (bundle.isPair) "$sessionId-g${groups.size}" else null
            val ids = bundle.items.map { "$sessionId-e${exercises.size + bundle.items.indexOf(it)}" }
            bundle.items.forEachIndexed { index, item ->
                exercises += buildExercise(ctx, item, ids[index], bundle, groupId)
            }
            if (groupId != null) {
                groups += SupersetGroup(
                    id = groupId,
                    exerciseOrder = ids,
                    restBetweenExercises = bundle.between,
                    restAfterSuperset = bundle.rest,
                )
            }
        }
        val parts = ArrayList<SessionPart>()
        if (state.mobilitySeconds > 0) {
            val focus = plan.mobility?.focus ?: when (plan.region) {
                SessionRegion.LEGS -> MobilityFocus.LOWER
                SessionRegion.PUSH, SessionRegion.PULL, SessionRegion.UPPER -> MobilityFocus.UPPER
                else -> MobilityFocus.FULL
            }
            val series = MobilityBuilder.series(focus, state.mobilitySeconds, "$sessionId-mob", ctx.seed + sessionIndex)
            if (series.isNotEmpty()) parts += MobilityBuilder.part("$sessionId-part-mobility", series)
        }
        val cardioExercises = ArrayList<Exercise>()
        if (state.cardioSeconds > 0) {
            val intervals = state.cardioIntervals
            val blocks = CardioBuilder.split(
                state.cardioSeconds,
                if (intervals) CardioBuilder.INTERVALS_MAX_SECONDS else CardioBuilder.STEADY_MAX_SECONDS,
            )
            blocks.forEachIndexed { index, seconds ->
                val asIntervals = intervals && index == 0
                val type = CardioBuilder.typeFor(ctx, equipment, asIntervals, salt = sessionIndex + index)
                val blockId = if (index == 0) "$sessionId-cardio" else "$sessionId-cardio-b$index"
                val details = if (asIntervals) {
                    CardioBuilder.intervals(type, seconds, ctx.level, blockId)
                } else {
                    CardioBuilder.steady(type, seconds, if (plan.cardio == CardioMode.LIGHT_FILL || index > 0) CardioIntensity.BAJA else CardioBuilder.intensityFor(ctx))
                }
                cardioExercises += CardioBuilder.exercise(blockId, details)
            }
        }
        if (state.fillerCardioSeconds > 0) {
            val type = if (CardioType.WALK in equipment.cardioTypes) CardioType.WALK else equipment.cardioTypes.first()
            cardioExercises += CardioBuilder.exercise(
                "$sessionId-cardio-suave",
                CardioBuilder.steady(type, state.fillerCardioSeconds, CardioIntensity.BAJA),
            )
        }
        if (cardioExercises.isNotEmpty()) parts += CardioBuilder.part("$sessionId-part-cardio", cardioExercises)
        return Session(
            id = sessionId,
            name = plan.title,
            description = plan.focus,
            exercises = exercises,
            parts = parts,
            dayOfWeek = dayOfWeek,
            scheduleLabel = plan.title,
            assignedDays = listOf(dayOfWeek),
            isMainSession = isMain,
            focus = plan.focus,
            supersetGroups = groups,
            origin = SessionOrigin.USER_DRAFT,
            requirement = SessionRequirement.REQUIRED,
            cardioFirst = false,
        )
    }

    private fun buildExercise(ctx: GenContext, item: DraftItem, id: String, bundle: Bundle, groupId: String?): Exercise {
        val entry = item.candidate.entry
        val legacy = entry.legacy
        val rx = item.rx
        val timed = rx.seconds != null
        val markKg = if (timed) null else item.candidate.mark?.let { ctx.request.marks[it] }?.takeIf { it > 0.0 }
        val oneRm = markKg?.let { it * item.candidate.markFactor }
        val percent = oneRm?.let { Prescriber.percentOfOneRm(rx.repMax, rx.rir) }
        val weight = if (oneRm != null && percent != null) Prescriber.roundLoad(oneRm * percent / 100.0) else null
        val convention = NativeLoadConventions.forEquipment(entry.equipmentId)
        val sets = List(bundle.sets) { index ->
            val setId = "$id-set-$index"
            when {
                timed -> ExerciseSet(id = setId, targetDuration = rx.seconds, targetRIR = null, restAfterSeconds = bundle.rest)
                weight != null && percent != null -> ExerciseSet(
                    id = setId,
                    targetReps = rx.repMax,
                    targetRepsRange = RepRange(rx.repMin, rx.repMax),
                    targetRIR = rx.rir,
                    intensityMode = IntensityMode.SOLO_RM,
                    targetPercentageRM = percent,
                    weight = weight,
                    loadBasis = LoadBasis.PERCENT_1RM,
                    restAfterSeconds = bundle.rest,
                    loadQuantityConvention = convention,
                )
                else -> ExerciseSet(
                    id = setId,
                    targetReps = rx.repMax,
                    targetRepsRange = RepRange(rx.repMin, rx.repMax),
                    targetRIR = rx.rir,
                    intensityMode = IntensityMode.RIR,
                    restAfterSeconds = bundle.rest,
                )
            }
        }
        val externalLoad = convention != LoadQuantityConvention.UNSPECIFIED
        val pendingReference = if (!timed && weight == null && externalLoad) {
            PlanLoadReference(
                kind = PlanLoadReferenceKind.OBSERVED_WORKING_SET,
                configurationId = entry.id,
                quantityConvention = convention,
                repMin = rx.repMin,
                repMax = rx.repMax,
                state = PlanLoadReferenceState.PENDING,
            )
        } else {
            null
        }
        val speed = item.spec.role == ItemRole.POWER && item.candidate.fromLadder
        return Exercise(
            id = id,
            name = legacy.name,
            exerciseDbId = entry.id,
            exerciseId = entry.id,
            canonicalExerciseId = entry.id,
            catalogRevision = legacy.catalogRevision,
            catalogDefinitionId = legacy.catalogDefinitionId,
            catalogConfigurationId = legacy.catalogConfigurationId,
            performanceProfileId = legacy.performanceProfileId,
            occurrenceId = id,
            effectiveMuscles = legacy.involvedMuscles,
            sets = sets,
            restTime = bundle.rest,
            trainingMode = when {
                timed -> TrainingMode.TIME
                weight != null -> TrainingMode.RM
                else -> TrainingMode.REPS
            },
            reference1RM = oneRm,
            variantName = item.candidate.label,
            supersetId = groupId,
            supersetGroupRef = groupId,
            supersetRestBetween = if (groupId != null) bundle.between else null,
            supersetRestAfter = if (groupId != null) bundle.rest else null,
            slotRole = if (speed) SlotRole.SPEED else null,
            techniqueModifier = if (speed) TechniqueModifier.SPEED else null,
            loadQuantityConvention = if (externalLoad) convention else LoadQuantityConvention.UNSPECIFIED,
            loadReference = pendingReference,
        )
    }

    // ─── Corrección fina con el estimador ──────────────────────────────────────────────────────────────────

    /** Un paso para acortar la sesión; false si ya no queda ninguno. */
    private fun reduceOnce(state: State, ctx: GenContext, minItems: Int): Boolean {
        if (state.fillerCardioSeconds > 0) {
            state.fillerCardioSeconds = (state.fillerCardioSeconds - 120).coerceAtLeast(0)
            if (state.fillerCardioSeconds < 8 * 60) state.fillerCardioSeconds = 0
            return true
        }
        // De atrás hacia delante y lo prioritario al final: solo se toca si no queda otra cosa.
        val included = MinuteFitter.trimOrder(state.bundles)
        included.firstOrNull { it.rest > it.minRest }?.let { it.rest = (it.rest - 15).coerceAtLeast(it.minRest); return true }
        included.firstOrNull { !it.isPriority && it.sets > it.minSets }?.let { it.sets--; return true }
        if (state.mobilitySeconds >= 120 && state.mobilitySeconds > 0) {
            state.mobilitySeconds -= 60
            return true
        }
        included.firstOrNull { it.isPriority && it.sets > it.minSets }?.let { it.sets--; return true }
        val itemCount = included.sumOf { it.items.size }
        val dropped = included.firstOrNull { itemCount - it.items.size >= minItems }
        if (dropped != null) {
            dropped.included = false
            return true
        }
        return false
    }

    /** Un paso para alargar la sesión; false si ya no queda ninguno. */
    private fun growOnce(state: State, ctx: GenContext, room: VolumeRoom, plan: SessionPlan): Boolean {
        // Lo prioritario recibe el tiempo sobrante primero.
        val included = MinuteFitter.growOrder(state.bundles)
        included.firstOrNull { it.sets < minOf(it.maxSets, room.maxSets(it, state.bundles, soft = true)) }?.let { it.sets++; return true }
        included.firstOrNull { it.restExtendable && it.rest + 15 <= it.maxRest }?.let { it.rest += 15; return true }
        if (plan.cardio != CardioMode.INTERVALS_FILL && plan.cardio != CardioMode.ZONE2_FILL && state.mobilitySeconds < 20 * 60) {
            state.mobilitySeconds += 60
            return true
        }
        return false
    }

    // ─── Notas ─────────────────────────────────────────────────────────────────────────────────────────────

    private fun cardioOmittedNote(ctx: GenContext): String =
        "Con ${ctx.targetMinutes} min por sesión no cabe un bloque de cardio y una sesión de fuerza completa: " +
            "el cardio no se recorta, así que va en otras sesiones. Con más minutos se suma al final de cada sesión de fuerza."

    private fun priorityTimeNote(ctx: GenContext): String =
        "Con ${ctx.targetMinutes} min por sesión no caben todas las series que piden tus prioridades en alguna sesión: " +
            "con más minutos por sesión o con menos prioridades llegan completas."

    private fun longSessionNote(ctx: GenContext): String =
        "Con ${ctx.targetMinutes} min por sesión no hay más trabajo de fuerza útil con tu material y tus techos de volumen semanal: " +
            "más series o ejercicios repetidos no suman. El tiempo restante va a movilidad y cardio suave."
}
