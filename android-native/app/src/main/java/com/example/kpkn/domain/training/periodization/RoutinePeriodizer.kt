package com.example.kpkn.domain.training.periodization

import com.example.kpkn.data.models.Block
import com.example.kpkn.data.models.BlockGoal
import com.example.kpkn.data.models.CardioDetails
import com.example.kpkn.data.models.Exercise
import com.example.kpkn.data.models.ExerciseSet
import com.example.kpkn.data.models.IntensityMode
import com.example.kpkn.data.models.Macrocycle
import com.example.kpkn.data.models.Mesocycle
import com.example.kpkn.data.models.MesocycleGoal
import com.example.kpkn.data.models.Program
import com.example.kpkn.data.models.ProgramStructure
import com.example.kpkn.data.models.ProgramWeek
import com.example.kpkn.data.models.RepRange
import com.example.kpkn.data.models.Session
import com.example.kpkn.data.models.SupersetVisualPlacement
import com.example.kpkn.data.models.effectiveRepRange
import com.example.kpkn.data.models.isCardio
import com.example.kpkn.data.protocols.LoadBasis
import com.example.kpkn.domain.text.SpanishPlurals
import com.example.kpkn.domain.training.ProgramExecutionContract
import com.example.kpkn.domain.training.SessionDurationEstimator
import com.example.kpkn.domain.training.generator.CatalogEntry
import com.example.kpkn.domain.training.generator.DayEquipment
import com.example.kpkn.domain.training.generator.Disciplines
import com.example.kpkn.domain.training.generator.ExKind
import com.example.kpkn.domain.training.generator.GeneratorCatalog
import com.example.kpkn.domain.training.generator.MovementPools
import com.example.kpkn.domain.training.generator.Prescriber
import com.example.kpkn.domain.training.generator.RoutineLevel
import com.example.kpkn.domain.training.generator.RoutineMode
import com.example.kpkn.domain.training.generator.RoutineRequest
import com.example.kpkn.domain.training.generator.RoutineGenerator
import com.example.kpkn.domain.training.generator.VolumeBudgets
import com.example.kpkn.domain.training.generator.VolumeLedger
import com.example.kpkn.domain.training.quality.SessionSlopRules
import kotlin.math.ceil
import kotlin.math.floor
import kotlin.math.roundToInt

/**
 * Periodizador (puro y determinista): convierte la semana única que arma el generador en un programa por bloques o
 * periodizado con TODAS sus semanas materializadas. No se deja nada a `BlockTransitionEngine`, que reescribiría el RPE y
 * dejaría el RIR y los kilos desfasados.
 *
 * Por semana aplica la fase: series (factor), repeticiones y RIR (con el mínimo por nivel en realización), kilos solo si el
 * ejercicio tiene 1RM de referencia, y el trato propio de cada modo. Después ajusta cada sesión a la ventana de minutos del
 * pedido y, si una semana pasa el techo de volumen, recorta accesorios. Nunca añade ejercicios; los ids de la semana base
 * solo cambian con el sufijo `-w<n>` para ser únicos en el programa.
 */
object RoutinePeriodizer {

    /** Revisión del periodizador; viaja en el bloque de cada programa periodizado. */
    const val REVISION = "routine-periodizer-v2"

    /** Nota cuando la competición está tan cerca que ni un bloque cabe: el programa queda en su versión simple. */
    private const val TOO_CLOSE_NOTE = "Tu competición está demasiado cerca para un programa por bloques."

    /** Techo de repeticiones de culturismo: solo sube hasta aquí. */
    private const val CULTURISMO_REP_CAP = 15

    /** RIR de partida si el generador no lo dejó (no debería pasar). */
    private const val DEFAULT_RIR = 2

    /** Correcciones máximas de minutos por sesión (como el ensamblador del generador). */
    private const val MAX_CORRECTIONS = 20

    /** Recortes máximos por techo de volumen en una semana. */
    private const val MAX_VOLUME_TRIMS = 200

    /** Series mínimas de un accesorio al recortarlo por tiempo o por volumen. */
    private const val MIN_ACCESSORY_SETS = 2

    /** Pesadas: powerlifting, halterofilia y strongman. */
    internal val HEAVY_MODES = setOf(
        RoutineMode.CUSTOM_POWERLIFTING,
        RoutineMode.DISCIPLINE_STRONGMAN,
        RoutineMode.DISCIPLINE_WEIGHTLIFTING_BASE,
    )

    /** Modos sin realización: sus semanas de realización pasan a intensificación. */
    private val NO_REALIZATION_MODES = setOf(RoutineMode.GENERAL_FUNCTIONAL, RoutineMode.CUSTOM_BODYBUILDING)

    /** Modos en los que la fase solo cambia las series (nunca el peldaño ni las repeticiones). */
    private val SETS_ONLY_MODES = setOf(RoutineMode.DISCIPLINE_CALISTHENICS, RoutineMode.DISCIPLINE_ARMWRESTLING)

    /** Fases en las que las pesadas dejan solo los básicos. */
    private val HEAVY_ONLY_PHASES = setOf(PhaseKind.REALIZATION, PhaseKind.PEAK, PhaseKind.TAPER)

    /**
     * Convierte [base] (la semana única del generador para [request]) en un programa de [variant] para [horizon].
     *
     * - [ProgramVariant.SIMPLE] devuelve [base] sin tocar.
     * - Si la competición está demasiado cerca para cualquier bloque, devuelve [base] con la nota correspondiente.
     * - Con el resto, el programa es COMPLEX con un macrociclo y un bloque por tramo de fase, y su id lleva el sufijo de la
     *   variante (`-blocks` o `-periodized`).
     */
    fun build(request: RoutineRequest, base: Program, variant: ProgramVariant, horizon: Horizon): PeriodizedRoutine {
        if (variant == ProgramVariant.SIMPLE) return PeriodizedRoutine(program = base, phases = emptyList(), notes = emptyList())
        val layout = PeriodLayout.phases(variant, horizon, heavy = request.mode in HEAVY_MODES)
        if (layout.isEmpty()) return PeriodizedRoutine(program = base, phases = emptyList(), notes = listOf(TOO_CLOSE_NOTE))

        val ctx = PeriodContext(
            request = request,
            learning = request.level == RoutineLevel.NOVICE || request.level == RoutineLevel.RETURNING,
            budgets = VolumeBudgets.of(request),
            catalog = GeneratorCatalog.of(request.catalog),
            kinds = kindIndex(request.mode),
            window = windowOf(request.targetMinutes),
        )
        val slots = slotsOf(layout, request.mode)
        val baseSessions = baseSessionsOf(base)
        val weeks = slots.map { slot ->
            trimVolume(fitWeek(periodizeWeek(baseSessions, slot, ctx), ctx), ctx)
        }.map { week ->
            week.map { session -> session.copy(targetDurationMinutes = minutesOf(session)) }
        }
        val program = assemble(base, ctx, variant, slots, weeks)
        ProgramExecutionContract.requireExecutable(program)
        return PeriodizedRoutine(
            program = program,
            phases = runsOf(slots).map { run -> PhaseSpan(run.first().kind, run.size) },
            notes = notesOf(weeks, ctx),
        )
    }

    // ─── Semanas y fases ───────────────────────────────────────────────────────────────────────────────────

    /** Una semana del programa: número (1-based), fase que se trabaja y fase de la plantilla de la que sale. */
    private data class WeekSlot(val number: Int, val kind: PhaseKind, val origin: PhaseKind)

    /** Expande los tramos a semanas. Sin realización (funcional y culturismo), la realización pasa a intensificación. */
    private fun slotsOf(layout: List<PhaseSpan>, mode: RoutineMode): List<WeekSlot> {
        val slots = ArrayList<WeekSlot>()
        layout.forEach { span ->
            val kind = if (span.kind == PhaseKind.REALIZATION && mode in NO_REALIZATION_MODES) PhaseKind.INTENSIFICATION else span.kind
            repeat(span.weeks) {
                slots.add(WeekSlot(number = slots.size + 1, kind = kind, origin = span.kind))
            }
        }
        return slots
    }

    /** Semanas seguidas con la misma fase: cada grupo es un bloque del programa. */
    private fun runsOf(slots: List<WeekSlot>): List<List<WeekSlot>> {
        val runs = ArrayList<MutableList<WeekSlot>>()
        slots.forEach { slot ->
            val current = runs.lastOrNull()
            if (current != null && current.last().kind == slot.kind) current.add(slot) else runs.add(mutableListOf(slot))
        }
        return runs
    }

    private fun baseSessionsOf(base: Program): List<Session> {
        val week = base.macrocycles.singleOrNull()?.blocks?.singleOrNull()?.mesocycles?.singleOrNull()?.weeks?.singleOrNull()
        return requireNotNull(week) { "El periodizador parte de la semana única del generador." }.sessions
    }

    private fun periodizeWeek(baseSessions: List<Session>, slot: WeekSlot, ctx: PeriodContext): List<Session> {
        val rule = ruleOf(slot, ctx)
        return baseSessions.mapIndexed { index, session ->
            val dayIndex = ctx.request.weekdays.indexOf(session.dayOfWeek).takeIf { it >= 0 } ?: index
            val place = RoutineGenerator.placeOf(ctx.request,dayIndex)
            val equipment = DayEquipment(RoutineGenerator.availabilityFor(ctx.request,place))
            periodizeSession(session,slot.number,rule,ctx,equipment)
        }
    }

    // ─── Reglas por fase y modo ────────────────────────────────────────────────────────────────────────────

    /** Fila base de la tabla de fases, antes de los ajustes por modo. */
    private class PhaseRow(
        val factor: Double,
        val repDelta: Int,
        val rirFixed: Int?,
        val rirStep: Int,
        val rirCap: Int?,
    )

    /** Reglas de una semana: factor de series, repeticiones, RIR y el trato por modo. */
    private class WeekRule(
        val phase: PhaseKind,
        val setFactor: Double,
        val repDelta: Int,
        val repCap: Int?,
        val rirFixed: Int?,
        val rirStep: Int,
        val rirCap: Int?,
        val principalsOnly: Boolean,
        val setsOnly: Boolean,
        val halveCardio: Boolean,
        val testWeek: Boolean,
    )

    private fun ruleOf(slot: WeekSlot, ctx: PeriodContext): WeekRule {
        val mode = ctx.request.mode
        val realizationRir = if (ctx.learning) 2 else 1
        val row = when (slot.kind) {
            PhaseKind.ACCUMULATION -> PhaseRow(factor = 1.15, repDelta = 2, rirFixed = null, rirStep = 1, rirCap = 3)
            PhaseKind.INTENSIFICATION -> PhaseRow(factor = 1.0, repDelta = 0, rirFixed = null, rirStep = 0, rirCap = null)
            PhaseKind.REALIZATION, PhaseKind.PEAK ->
                PhaseRow(factor = 0.8, repDelta = -2, rirFixed = realizationRir, rirStep = 0, rirCap = null)
            PhaseKind.DELOAD, PhaseKind.TAPER -> PhaseRow(factor = 0.55, repDelta = -1, rirFixed = null, rirStep = 2, rirCap = 4)
            PhaseKind.TEST -> PhaseRow(factor = 0.55, repDelta = -2, rirFixed = realizationRir, rirStep = 0, rirCap = null)
        }
        var repDelta = row.repDelta
        var repCap: Int? = null
        var rirFixed = row.rirFixed
        if (mode == RoutineMode.CUSTOM_BODYBUILDING) {
            // Culturismo: las repeticiones no bajan y suben hasta 15. Su antigua realización (ya intensificación) sube repeticiones.
            repDelta = if (slot.origin == PhaseKind.REALIZATION) 2 else maxOf(repDelta, 0)
            repCap = CULTURISMO_REP_CAP
        }
        if (mode == RoutineMode.GENERAL_FUNCTIONAL && slot.origin == PhaseKind.REALIZATION) {
            // Funcional: sin realización; sus semanas de intensificación llevan RIR 1 (con el mínimo por nivel).
            rirFixed = realizationRir
        }
        return WeekRule(
            phase = slot.kind,
            setFactor = row.factor,
            repDelta = repDelta,
            repCap = repCap,
            rirFixed = rirFixed,
            rirStep = row.rirStep,
            rirCap = row.rirCap,
            principalsOnly = slot.kind == PhaseKind.TEST || (mode in HEAVY_MODES && slot.kind in HEAVY_ONLY_PHASES),
            setsOnly = mode in SETS_ONLY_MODES,
            halveCardio = mode == RoutineMode.GENERAL_HYBRID && (slot.kind == PhaseKind.DELOAD || slot.kind == PhaseKind.TAPER),
            testWeek = slot.kind == PhaseKind.TEST,
        )
    }

    /** Series de la semana: `round(S·m)` acotado a 1..S+1; en la semana de test, un solo básico. */
    private fun setCount(rule: WeekRule, principal: Boolean, baseSets: Int): Int {
        if (rule.testWeek && principal) return 1
        return (baseSets * rule.setFactor).roundToInt().coerceIn(1, baseSets + 1)
    }

    private fun shiftReps(range: RepRange, rule: WeekRule): RepRange {
        val delta = rule.repDelta
        if (delta == 0) return range
        val cap = rule.repCap
        val max = if (delta > 0 && cap != null) minOf(range.max + delta, maxOf(range.max, cap)) else maxOf(1, range.max + delta)
        val min = minOf(maxOf(1, range.min + delta), max)
        return RepRange(min, max)
    }

    private fun rirFor(base: Int, rule: WeekRule): Int {
        rule.rirFixed?.let { return it }
        val cap = rule.rirCap ?: return base
        return minOf(base + rule.rirStep, maxOf(base, cap))
    }

    // ─── Sesiones y series ─────────────────────────────────────────────────────────────────────────────────

    /** Límites de series de un ejercicio de la semana: [min] para recortar, [max] para añadir. */
    private data class SetBounds(val min: Int, val max: Int)

    /** Pasa una sesión de la semana base a la semana [number]: ids con sufijo, series, kilos y bloques de cardio. */
    private fun periodizeSession(base: Session, number: Int, rule: WeekRule, ctx: PeriodContext, equipment: DayEquipment): Session {
        val suffix = "-w$number"
        val principals = principalIds(base, ctx)
        val rotations = rotationsFor(base, principals, rule, ctx, equipment)
        val kept = HashSet<String>()

        fun transform(exercise: Exercise): Exercise? {
            val changed = periodizeExercise(exercise, exercise.id in principals, rule, suffix, ctx, rotations[exercise.id]) ?: return null
            if (exercise.id in principals) ctx.principalOccurrences += changed.id
            kept.add(exercise.id)
            return changed
        }

        val mainExercises = base.exercises.mapNotNull { transform(it) }
        val partList = base.parts.map { part ->
            part.copy(
                id = part.id + suffix,
                exercises = part.exercises.mapNotNull { transform(it) },
                mobilitySeries = part.mobilitySeries.map { it.copy(id = it.id + suffix) },
            )
        }
        // Un bloque de superserie solo sobrevive con dos o más ejercicios; si no, sus ejercicios quedan sueltos.
        val survivingOld = base.supersetGroups
            .filter { group -> group.exerciseOrder.count { it in kept } >= 2 }
            .map { it.id }
            .toSet()
        val groups = base.supersetGroups.filter { it.id in survivingOld }.map { group ->
            group.copy(
                id = group.id + suffix,
                exerciseOrder = group.exerciseOrder.filter { it in kept }.map { it + suffix },
                visualPlacement = group.visualPlacement?.let { placement ->
                    SupersetVisualPlacement(
                        partId = placement.partId?.let { it + suffix },
                        anchorExerciseId = placement.anchorExerciseId?.takeIf { it in kept }?.let { it + suffix },
                    )
                },
            )
        }
        return base.copy(
            id = base.id + suffix,
            exercises = mainExercises.map { relink(it, survivingOld, suffix) },
            parts = partList.map { part -> part.copy(exercises = part.exercises.map { relink(it, survivingOld, suffix) }) },
            supersetGroups = groups,
            warmup = base.warmup.map { it.copy(id = it.id + suffix) },
        )
    }

    /** Ejercicio con el sufijo de la semana, sin cambiar aún sus series (eso lo hace [periodizeExercise]). */
    private fun renamed(exercise: Exercise, suffix: String): Exercise = exercise.copy(
        id = exercise.id + suffix,
        occurrenceId = exercise.occurrenceId?.let { it + suffix },
        warmupSets = exercise.warmupSets.map { it.copy(id = it.id + suffix) },
        mobilitySeries = exercise.mobilitySeries.map { it.copy(id = it.id + suffix) },
    )

    /** Null si la fase quita el ejercicio (solo pesadas en realización y taper, y la semana de test). */
    private fun periodizeExercise(
        exercise: Exercise,
        principal: Boolean,
        rule: WeekRule,
        suffix: String,
        ctx: PeriodContext,
        rotationTarget: String?,
    ): Exercise? {
        if (exercise.isCardio) {
            val cardio = if (rule.halveCardio) exercise.cardioDetails?.let { halved(it) } else exercise.cardioDetails
            return renamed(exercise, suffix).copy(cardioDetails = cardio)
        }
        if (exercise.sets.isEmpty()) return renamed(exercise, suffix)
        if (rule.principalsOnly && !principal) return null
        val baseSets = exercise.sets.size
        ctx.bounds[exercise.id + suffix] = SetBounds(
            min = if (principal) 1 else minOf(MIN_ACCESSORY_SETS, baseSets),
            max = if (rule.testWeek) 1 else baseSets + 1,
        )
        val current = if (principal && rotationTarget != null) rotateVariant(exercise, rotationTarget, ctx) else exercise
        val sets = (0 until setCount(rule, principal, baseSets)).map { index -> reshapeSet(current, index, rule, suffix) }
        return renamed(current, suffix).copy(sets = sets)
    }

    /**
     * Rotación del bloque decidida por sesión (id de ejercicio → configuración destino). Solo rota el primer básico de cada
     * familia por sesión y nunca hacia un ejercicio que ya está en la sesión: el destino no puede ser la configuración ni la
     * definición de otro ejercicio (original o ya rotado), ni compartir familia de movimiento con otro
     * ([SessionSlopRules.familyOf]); además debe caber en el material del día y no estar entre los evitados.
     */
    private fun rotationsFor(
        base: Session,
        principals: Set<String>,
        rule: WeekRule,
        ctx: PeriodContext,
        equipment: DayEquipment,
    ): Map<String, String> {
        if (ctx.request.mode !in HEAVY_MODES) return emptyMap()
        val avoided = ctx.request.avoided.flatMap { com.example.kpkn.domain.onboarding.AvoidGroups.definitionIdsOf(it) }.toSet()
        val exercises = base.allExercises()
        // Configuración y definición vigentes de cada ejercicio, con las rotaciones ya aceptadas.
        val currentConfig = HashMap<String, String?>()
        val currentDefinition = HashMap<String, String?>()
        exercises.forEach {
            currentConfig[it.id] = it.catalogConfigurationId
            currentDefinition[it.id] = it.catalogDefinitionId
        }
        val seenFamilies = HashSet<String>()
        val result = LinkedHashMap<String, String>()
        for (exercise in exercises) {
            if (exercise.id !in principals) continue
            val baseId = exercise.catalogConfigurationId ?: continue
            val family = BlockVariantRotation.familyOf(baseId) ?: continue
            if (!seenFamilies.add(family)) continue
            val targetId = BlockVariantRotation.variantFor(baseId, rule.phase, true)
            if (targetId == baseId) continue
            val target = ctx.catalog.entry(targetId) ?: continue
            if (!equipment.allows(target, emptyList()) || target.definitionId in avoided) continue
            val others = exercises.filter { it.id != exercise.id }
            val targetSlopFamily = SessionSlopRules.familyOf(targetId)
            val collides = others.any { other ->
                currentConfig[other.id] == targetId ||
                    currentDefinition[other.id] == target.definitionId ||
                    (targetSlopFamily != null && SessionSlopRules.familyOf(currentConfig[other.id]) == targetSlopFamily)
            }
            if (collides) continue
            result[exercise.id] = targetId
            currentConfig[exercise.id] = targetId
            currentDefinition[exercise.id] = target.definitionId
        }
        return result
    }

    private fun rotateVariant(exercise: Exercise, targetId: String, ctx: PeriodContext): Exercise {
        if (exercise.catalogConfigurationId == targetId) return exercise
        val target = ctx.catalog.entry(targetId) ?: return exercise
        val info = target.legacy
        // Una variante nueva no hereda el 1RM, el TM ni los contextos de otra configuración.
        return exercise.copy(name = info.name,exerciseDbId = targetId,exerciseId = targetId,canonicalExerciseId = targetId,
            exerciseFamilyId = target.familyId,catalogRevision = info.catalogRevision,catalogDefinitionId = info.catalogDefinitionId,
            catalogConfigurationId = targetId,performanceProfileId = info.performanceProfileId,effectiveMuscles = info.involvedMuscles,
            variantName = info.catalogVariantChips.joinToString(" · "),reference1RM = null,referenceTrainingMax = null,prFor1RM = null,
            loadReference = null,selectedAspects = null,contextProfilesV3 = emptyList(),defaultContextProfileIdV3 = null,
            sets = exercise.sets.map { it.copy(weight = null,targetPercentageRM = null,loadBasis = null,
                plannedTargetV2 = null,leftTarget = null,rightTarget = null,contextKeyV2 = null,contextProfileIdV3 = null,
                defaultTagIdV3 = null,defaultSetupProfileIdV3 = null,tagId = null,setupId = null) })
    }

    /**
     * Serie [index] del ejercicio para la fase: repeticiones y RIR de la fase y, si hay 1RM de referencia, kilos con
     * `Prescriber`. Las series que la semana base no tenía se copian de la última con un id propio.
     */
    private fun reshapeSet(exercise: Exercise, index: Int, rule: WeekRule, suffix: String): ExerciseSet {
        val exists = index < exercise.sets.size
        val source = exercise.sets[if (exists) index else exercise.sets.lastIndex]
        val id = if (exists) source.id + suffix else exercise.id + "-set-$index" + suffix
        if (rule.setsOnly || source.targetDuration != null) return source.copy(id = id)
        val range = source.effectiveRepRange() ?: return source.copy(id = id)
        val reps = shiftReps(range, rule)
        val rir = rirFor(source.targetRIR ?: DEFAULT_RIR, rule)
        val reference = exercise.reference1RM?.takeIf { it > 0.0 }
        if (reference == null) {
            return source.copy(
                id = id,
                targetReps = reps.max,
                targetRepsRange = reps,
                targetRIR = rir,
                intensityMode = IntensityMode.RIR,
                targetPercentageRM = null,
                weight = null,
                loadBasis = null,
            )
        }
        val percent = Prescriber.percentOfOneRm(reps.max, rir)
        return source.copy(
            id = id,
            targetReps = reps.max,
            targetRepsRange = reps,
            targetRIR = rir,
            intensityMode = IntensityMode.SOLO_RM,
            targetPercentageRM = percent,
            weight = Prescriber.roundLoad(reference * percent / 100.0),
            loadBasis = LoadBasis.PERCENT_1RM,
        )
    }

    /** Cardio a la mitad: intervalos con menos vueltas, o duración continua a la mitad (mínimo un minuto). */
    private fun halved(details: CardioDetails): CardioDetails =
        if (details.hasIntervals()) {
            details.copy(intervalRounds = maxOf(1, details.intervalRounds / 2))
        } else {
            details.copy(targetDurationSeconds = details.targetDurationSeconds?.let { maxOf(60, it / 2) })
        }

    /** Reenlaza un ejercicio con el grupo de superserie renombrado, o lo suelta si el grupo ya no existe. */
    private fun relink(exercise: Exercise, survivingGroups: Set<String>, suffix: String): Exercise {
        val group = exercise.supersetGroupRef?.takeIf { it in survivingGroups } ?: exercise.supersetId?.takeIf { it in survivingGroups }
        val ref = group?.let { it + suffix }
        return exercise.copy(
            supersetId = ref,
            supersetGroupRef = ref,
            supersetRestBetween = if (ref != null) exercise.supersetRestBetween else null,
            supersetRestAfter = if (ref != null) exercise.supersetRestAfter else null,
        )
    }

    // ─── Minutos ───────────────────────────────────────────────────────────────────────────────────────────

    /** Ventana de minutos del pedido: [85 %, 110 %] y nunca menos de 20 (la misma regla que `GenContext.windowMinutes`). */
    private fun windowOf(targetMinutes: Int): IntRange {
        val low = maxOf(20, ceil(targetMinutes * 0.85).toInt())
        val high = floor(targetMinutes * 1.10).toInt()
        return low..maxOf(low, high)
    }

    private fun minutesOf(session: Session): Int =
        ceil(SessionDurationEstimator.estimate(session).totalSeconds / 60.0).toInt().coerceAtLeast(1)

    /**
     * Ajusta una semana sesión a sesión: si una pasa de la ventana, quita una serie al último accesorio que esté por encima de
     * su mínimo; si queda corta, añade una serie a un básico hasta su máximo y solo si la semana sigue dentro de su techo.
     */
    private fun fitWeek(week: List<Session>, ctx: PeriodContext): List<Session> {
        val result = week.toMutableList()
        for (index in result.indices) {
            var session = result[index]
            var corrections = 0
            while (corrections < MAX_CORRECTIONS) {
                val minutes = minutesOf(session)
                val next: Session? = when {
                    minutes > ctx.window.last -> shrinkOnce(session, ctx)
                    minutes < ctx.window.first -> growOnce(session, index, result, ctx)
                    else -> null
                }
                if (next == null) break
                session = next
                result[index] = session
                corrections++
            }
        }
        return result
    }

    private fun shrinkOnce(session: Session, ctx: PeriodContext): Session? {
        val principals = principalIds(session, ctx)
        val target = session.strengthExercises().asReversed().firstOrNull { exercise ->
            exercise.id !in principals && exercise.sets.size > minSetsOf(exercise, ctx)
        } ?: return null
        return session.mapExercise(target.id) { dropOneSet(it) }
    }

    private fun growOnce(session: Session, index: Int, week: List<Session>, ctx: PeriodContext): Session? {
        val principals = principalIds(session, ctx)
        val candidates = session.strengthExercises().filter { exercise ->
            exercise.id in principals && exercise.sets.size < (ctx.bounds[exercise.id]?.max ?: 0)
        }
        for (candidate in candidates) {
            val grown = session.mapExercise(candidate.id) { exercise ->
                exercise.copy(sets = exercise.sets + cloneLastSet(exercise))
            }
            val trial = week.toMutableList()
            trial[index] = grown
            val touched = entryOf(candidate, ctx)?.contributions?.filterValues { it.direct > 0.0 }?.keys.orEmpty()
            val ledger = ledgerOf(trial, ctx)
            if (touched.all { ledger.directOf(it) <= ctx.budgets.ceiling(it).toDouble() }) return grown
        }
        return null
    }

    // ─── Volumen semanal ───────────────────────────────────────────────────────────────────────────────────

    /** Recorta accesorios de los músculos que pasan su techo semanal (`VolumeBudgets`); los básicos no se tocan. */
    private fun trimVolume(week: List<Session>, ctx: PeriodContext): List<Session> {
        var current = week
        var trims = 0
        while (trims < MAX_VOLUME_TRIMS) {
            val ledger = ledgerOf(current, ctx)
            val over = ctx.budgets.muscles.firstOrNull { ledger.directOf(it) > ctx.budgets.ceiling(it).toDouble() } ?: break
            val target = accessoryForMuscle(current, over, ctx)
            if (target == null) {
                ctx.overCeiling.add(over)
                break
            }
            current = current.mapIndexed { index, session ->
                if (index == target.first) session.mapExercise(target.second) { dropOneSet(it) } else session
            }
            ctx.trimmedMuscles.add(over)
            trims++
        }
        return current
    }

    /** Último accesorio (de la última sesión a la primera) que trabaja [muscle] y aún tiene series por encima de su mínimo. */
    private fun accessoryForMuscle(week: List<Session>, muscle: String, ctx: PeriodContext): Pair<Int, String>? {
        for (sessionIndex in week.indices.reversed()) {
            val session = week[sessionIndex]
            val principals = principalIds(session, ctx)
            val found = session.strengthExercises().asReversed().firstOrNull { exercise ->
                exercise.id !in principals &&
                    exercise.sets.size > minSetsOf(exercise, ctx) &&
                    (entryOf(exercise, ctx)?.contributions?.get(muscle)?.direct ?: 0.0) > 0.0
            }
            if (found != null) return sessionIndex to found.id
        }
        return null
    }

    private fun ledgerOf(sessions: List<Session>, ctx: PeriodContext): VolumeLedger {
        val ledger = VolumeLedger(ctx.budgets)
        sessions.forEach { session ->
            session.strengthExercises().forEach { exercise ->
                entryOf(exercise, ctx)?.let { ledger.add(it, exercise.sets.size) }
            }
        }
        return ledger
    }

    // ─── Básicos y accesorios ──────────────────────────────────────────────────────────────────────────────

    /**
     * Básicos de una sesión: los de tipo HEAVY del catálogo o, si no hay ninguno, el primer ejercicio de fuerza. Se calculan
     * sobre la sesión que se está ajustando, así que son los mismos antes y después de quitar accesorios.
     */
    private fun principalIds(session: Session, ctx: PeriodContext): Set<String> {
        val strength = session.strengthExercises()
        val declared = strength.filter { it.id in ctx.principalOccurrences }
        if (declared.isNotEmpty()) return declared.map { it.id }.toSet()
        val heavy = strength.filter { kindOf(it, ctx) == ExKind.HEAVY }
        return heavy.ifEmpty { strength.take(1) }.map { it.id }.toSet()
    }

    private fun minSetsOf(exercise: Exercise, ctx: PeriodContext): Int = ctx.bounds[exercise.id]?.min ?: MIN_ACCESSORY_SETS

    private fun kindOf(exercise: Exercise, ctx: PeriodContext): ExKind? = exercise.catalogConfigurationId?.let { ctx.kinds[it] }

    private fun entryOf(exercise: Exercise, ctx: PeriodContext): CatalogEntry? =
        exercise.catalogConfigurationId?.let { ctx.catalog.entry(it) }

    /** Tipo de cada configuración según sus reservas (básico = HEAVY; un id con HEAVY en alguna reserva cuenta como HEAVY). */
    private fun kindIndex(mode: RoutineMode): Map<String, ExKind> {
        val groups = MovementPools.byPattern.values.flatten() +
            Disciplines.of(mode)?.pools?.values?.flatten().orEmpty()
        val index = HashMap<String, ExKind>()
        groups.forEach { group ->
            group.entries.forEach { entry ->
                if (entry.kind == ExKind.HEAVY || index[entry.id] == null) index[entry.id] = entry.kind
            }
        }
        return index
    }

    private fun cloneLastSet(exercise: Exercise): ExerciseSet =
        exercise.sets.last().copy(id = "${exercise.id}-set-${exercise.sets.size}")

    private fun dropOneSet(exercise: Exercise): Exercise = exercise.copy(sets = exercise.sets.dropLast(1))

    // ─── Notas y programa ──────────────────────────────────────────────────────────────────────────────────

    /**
     * Nota de las sesiones que no caben en la ventana de minutos: con una sola, «Hay 1 sesión… ajustarla»; con más, el plural.
     */
    internal fun outOfWindowNote(misses: Int, window: IntRange): String =
        "Hay ${SpanishPlurals.sessions(misses)} fuera de ${window.first}–${window.last} min: el material y los techos de " +
            "volumen no dejan ${SpanishPlurals.choose(misses, "ajustarla", "ajustarlas")} sin quitar básicos."

    private fun notesOf(weeks: List<List<Session>>, ctx: PeriodContext): List<String> {
        val misses = weeks.flatten().count { minutesOf(it) !in ctx.window }
        val notes = ArrayList<String>()
        if (misses > 0) notes.add(outOfWindowNote(misses, ctx.window))
        if (ctx.trimmedMuscles.isNotEmpty()) {
            notes.add("Para no pasar el techo semanal se recortaron series de accesorios de ${ctx.trimmedMuscles.joinToString(", ")}.")
        }
        if (ctx.overCeiling.isNotEmpty()) {
            notes.add("Solo con los básicos, ${ctx.overCeiling.joinToString(", ")} supera su techo semanal.")
        }
        return notes
    }

    private fun assemble(
        base: Program,
        ctx: PeriodContext,
        variant: ProgramVariant,
        slots: List<WeekSlot>,
        weeks: List<List<Session>>,
    ): Program {
        val suffix = if (variant == ProgramVariant.BLOCKS) "blocks" else "periodized"
        val programId = "${base.id}-$suffix"
        val modeKey = ctx.request.mode.name.lowercase()
        val programWeeks = slots.mapIndexed { index, slot ->
            ProgramWeek(
                id = "$programId-w${slot.number}",
                name = "Semana ${slot.number}",
                description = labelOf(slot.kind),
                sessions = weeks[index],
            )
        }
        val occurrences = HashMap<PhaseKind, Int>()
        val blocks = runsOf(slots).mapIndexed { blockIndex, run ->
            val kind = run.first().kind
            val occurrence = (occurrences[kind] ?: 0) + 1
            occurrences[kind] = occurrence
            val label = if (occurrence == 1) labelOf(kind) else "${labelOf(kind)} $occurrence"
            Block(
                id = "$programId-b${blockIndex + 1}",
                name = label,
                goal = blockGoalOf(kind),
                mesocycles = listOf(
                    Mesocycle(
                        id = "$programId-m${blockIndex + 1}",
                        name = label,
                        goal = mesocycleGoalOf(kind),
                        weeks = run.map { programWeeks[it.number - 1] },
                    ),
                ),
                sourceDefinitionId = "generated:$modeKey:$suffix",
                sourceRevision = REVISION,
                prescriptionOrigin = "KPKN_GENERATED",
            )
        }
        return base.copy(
            id = programId,
            structure = ProgramStructure.COMPLEX,
            macrocycles = listOf(Macrocycle(id = "$programId-macro", name = "Mi plan", blocks = blocks)),
            tags = base.tags + variant.name,
        )
    }

    private fun labelOf(kind: PhaseKind): String = when (kind) {
        PhaseKind.ACCUMULATION -> "Acumulación"
        PhaseKind.INTENSIFICATION -> "Intensificación"
        PhaseKind.REALIZATION -> "Realización"
        PhaseKind.PEAK -> "Pico"
        PhaseKind.DELOAD -> "Descarga"
        PhaseKind.TAPER -> "Puesta a punto"
        PhaseKind.TEST -> "Test"
    }

    private fun blockGoalOf(kind: PhaseKind): BlockGoal = when (kind) {
        PhaseKind.ACCUMULATION -> BlockGoal.ACCUMULATION
        PhaseKind.INTENSIFICATION -> BlockGoal.INTENSIFICATION
        PhaseKind.REALIZATION, PhaseKind.TEST -> BlockGoal.REALIZATION
        PhaseKind.PEAK -> BlockGoal.PEAK
        PhaseKind.DELOAD -> BlockGoal.DELOAD
        PhaseKind.TAPER -> BlockGoal.TAPER
    }

    private fun mesocycleGoalOf(kind: PhaseKind): MesocycleGoal = when (kind) {
        PhaseKind.ACCUMULATION -> MesocycleGoal.ACCUMULATION
        PhaseKind.INTENSIFICATION -> MesocycleGoal.INTENSIFICATION
        PhaseKind.REALIZATION, PhaseKind.PEAK, PhaseKind.TEST -> MesocycleGoal.REALIZATION
        PhaseKind.DELOAD, PhaseKind.TAPER -> MesocycleGoal.DELOAD
    }

    /** Estado de una generación: pedido, índice del catálogo, techos y límites; vive solo durante [build]. */
    private class PeriodContext(
        val request: RoutineRequest,
        val learning: Boolean,
        val budgets: VolumeBudgets,
        val catalog: GeneratorCatalog,
        val kinds: Map<String, ExKind>,
        val window: IntRange,
    ) {
        val principalOccurrences = HashSet<String>()
        /** Límites de series por ejercicio (clave: id ya con sufijo de semana). */
        val bounds = HashMap<String, SetBounds>()

        /** Músculos a los que se recortaron accesorios por techo semanal. */
        val trimmedMuscles = LinkedHashSet<String>()

        /** Músculos que superan su techo solo con básicos. */
        val overCeiling = LinkedHashSet<String>()
    }
}

/** Ejercicios de fuerza de la sesión (sin cardio), en el orden de ejecución. */
private fun Session.strengthExercises(): List<Exercise> = allExercises().filter { !it.isCardio && it.sets.isNotEmpty() }

/** Sustituye el ejercicio [exerciseId] (en bloques principales y en partes). */
private fun Session.mapExercise(exerciseId: String, transform: (Exercise) -> Exercise): Session = copy(
    exercises = exercises.map { if (it.id == exerciseId) transform(it) else it },
    parts = parts.map { part ->
        part.copy(exercises = part.exercises.map { if (it.id == exerciseId) transform(it) else it })
    },
)
