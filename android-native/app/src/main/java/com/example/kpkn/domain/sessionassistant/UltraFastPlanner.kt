package com.example.kpkn.domain.sessionassistant

import com.example.kpkn.data.models.CardioBlockType
import com.example.kpkn.data.models.CardioDetails
import com.example.kpkn.data.models.Exercise
import com.example.kpkn.data.models.ExerciseMuscleInfo
import com.example.kpkn.data.models.ExerciseSet
import com.example.kpkn.data.models.LoadModeV2
import com.example.kpkn.data.models.Session
import com.example.kpkn.data.models.SupersetGroup
import com.example.kpkn.data.models.TechniqueType
import com.example.kpkn.data.models.isCardio
import com.example.kpkn.data.models.supersetGroupRefOrLegacyId
import com.example.kpkn.data.protocols.SlotRole
import com.example.kpkn.domain.calculations.calculateSessionTimeBreakdown
import com.example.kpkn.domain.cardio.CardioHiitProgramBuilder
import kotlin.math.ceil
import kotlin.math.roundToInt

/**
 * «Acortar sesión»: el modo ultrarrápido guiado por un objetivo. La persona solo dice cuánto quiere ahorrar (o nada: el motor
 * propone un objetivo según lo que falta y el límite de la sesión) y el motor elige, ejercicio por ejercicio, la táctica que menos
 * estímulo pierde.
 *
 * Cada ejercicio pendiente tiene una escalera de niveles acumulativos; cada nivel tiene un «coste» (tier) de 1 a 5:
 *  1. Descansos de compuestos y aislamientos, y de las superseries que ya existen (con suelo por tipo).
 *  2. Descanso de los compuestos pesados (suelo 2:00); fusionar las dos últimas series de un aislamiento en una con drop-set o
 *     rest-pause; nuevas superseries en la misma polea o smith; cardio −25 %.
 *  3. Fusionar las dos últimas series de un compuesto (una con técnica); el mínimo de un aislamiento (la mitad); cardio −50 %.
 *  4. Quitar una serie de bajo valor (la última) de un compuesto pesado sin bajar de dos; el mínimo de un compuesto; quitar un
 *     cardio accesorio.
 *  5. El mínimo de un compuesto pesado (las series principales), con rest-pause en la última solo si es seguro.
 *
 * El motor sube primero los niveles baratos y, dentro de un mismo coste, el que más ahorra (o el más pequeño que ya alcanza el
 * objetivo). «Lo mínimo eficaz» aplica todos. Solo toca trabajo PENDIENTE: las series registradas (y las omitidas) nunca cambian.
 */
object UltraFastPlanner {

    const val SUPERSET_ITEM_PREFIX = "ss:"
    const val PAIR_ITEM_PREFIX = "pair:"

    /** Suelos de descanso por papel: por debajo, el estímulo de la serie siguiente cae más de lo que se gana en tiempo. */
    const val HEAVY_REST_FLOOR_SECONDS = 120
    const val COMPOUND_REST_FLOOR_SECONDS = 75
    const val ISOLATION_REST_FLOOR_SECONDS = 45
    const val SUPERSET_REST_FLOOR_SECONDS = 60

    /** Un bloque de cardio no baja de 5 min: por debajo es mejor quitarlo (si es accesorio). */
    const val CARDIO_FLOOR_SECONDS = 5 * 60

    private const val DEFAULT_REST_SECONDS = 90
    private const val NEW_SUPERSET_REST_BETWEEN = 15
    private const val NEW_SUPERSET_REST_AFTER = 45

    fun plan(
        session: Session,
        exerciseIndex: Map<String, ExerciseMuscleInfo>,
        /** null: el motor propone el objetivo. */
        goal: ShortenGoal? = null,
        excludedItemIds: Set<String> = emptySet(),
        /** Series registradas u omitidas: no se quitan, no cambian y no cuentan como pendientes. */
        protectedSetIdsByExercise: Map<String, Set<String>> = emptyMap(),
        /** Ejercicios en curso que no se pueden tocar (p. ej. un cardio con el cronómetro en marcha). */
        lockedExerciseIds: Set<String> = emptySet(),
        /** Ejercicios omitidos: no cuentan como pendientes. */
        ignoredExerciseIds: Set<String> = emptySet(),
        /** El ejercicio actual se puede acortar, pero nunca quitar. */
        currentExerciseId: String? = null,
        elapsedSeconds: Int = 0,
        sessionLimitMinutes: Int? = null,
    ): UltraFastApplyResult {
        val ctx = Context(session, exerciseIndex, protectedSetIdsByExercise, lockedExerciseIds, ignoredExerciseIds, currentExerciseId)
        val ladders = buildLadders(ctx)
        val noLevels = emptyMap<String, Int>()
        val beforeSeconds = ctx.pendingSeconds(materialize(ctx, ladders, noLevels))

        val allLevels = ladders.associate { it.id to it.levels.size }
        val maxSaved = (beforeSeconds - ctx.pendingSeconds(materialize(ctx, ladders, allLevels))).coerceAtLeast(0)
        val resolvedGoal = goal ?: defaultGoal(beforeSeconds, maxSaved, elapsedSeconds, sessionLimitMinutes)
        val options = if (maxSaved < 60) emptyList() else goalOptions(resolvedGoal, maxSaved)

        val blocked = ladders.filter { isBlocked(it, excludedItemIds) }.map { it.id }.toSet()
        val levels = if (resolvedGoal.isMinimum) {
            ladders.filterNot { it.id in blocked }.associate { it.id to it.levels.size }
        } else {
            greedy(ctx, ladders, blocked, beforeSeconds, (resolvedGoal.minutes ?: 0) * 60)
        }

        val result = materialize(ctx, ladders, levels)
        val afterSeconds = ctx.pendingSeconds(result)
        val saved = (beforeSeconds - afterSeconds).coerceAtLeast(0)
        val items = describe(ctx, ladders, levels, excludedItemIds, result, afterSeconds)
        val preview = UltraFastPreview(
            beforeSeconds = beforeSeconds,
            afterSeconds = afterSeconds,
            savedSeconds = saved,
            perExercise = compatChanges(ctx, ladders, levels, result),
            supersets = result.newPairs.map { (a, b) ->
                UltraFastSupersetChange(a.id, b.id, a.name, b.name,
                    UltraFastConfig.machineKeyForSuperset(ctx.info(a)?.equipment, null) ?: "polea")
            },
            goal = resolvedGoal,
            goalOptions = options,
            items = items,
            excludedItemIds = excludedItemIds.filter { id -> ladders.any { it.id == id } }.toSet(),
            removedExerciseIds = result.removed,
            maxSavedSeconds = maxSaved,
            targetReached = resolvedGoal.isMinimum || saved >= (resolvedGoal.minutes ?: 0) * 60,
        )
        return UltraFastApplyResult(result.exercises, result.groups, preview, result.removed)
    }

    /** Papel de un ejercicio para acortar: decide la táctica. Puro y público para la interfaz y las pruebas. */
    fun roleOf(exercise: Exercise, info: ExerciseMuscleInfo?): ShortenRole = when {
        exercise.isCardio -> ShortenRole.CARDIO
        exercise.slotRole == SlotRole.T1_MAIN || exercise.slotRole == SlotRole.SPEED || exercise.slotRole == SlotRole.TECHNIQUE ||
            exercise.isCompetitionLift || UltraFastEngine.isProtectedBasic(exercise, info) ||
            UltraFastEngine.isDangerous(exercise, info) -> ShortenRole.HEAVY_COMPOUND
        exercise.slotRole == SlotRole.T3_ACCESSORY || UltraFastEngine.isIsolationEligible(exercise, info) -> ShortenRole.ISOLATION
        else -> ShortenRole.COMPOUND
    }

    /** Descanso acortado con suelo por papel; nunca sube un descanso que ya está por debajo del suelo. */
    fun shortenedRestSeconds(seconds: Int, role: ShortenRole): Int {
        val (factor, floor) = when (role) {
            ShortenRole.HEAVY_COMPOUND -> 0.75 to HEAVY_REST_FLOOR_SECONDS
            ShortenRole.COMPOUND -> 0.67 to COMPOUND_REST_FLOOR_SECONDS
            ShortenRole.ISOLATION -> 0.5 to ISOLATION_REST_FLOOR_SECONDS
            ShortenRole.SUPERSET, ShortenRole.CARDIO -> 0.67 to SUPERSET_REST_FLOOR_SECONDS
        }
        if (seconds <= floor) return seconds
        val scaled = ((seconds * factor).toInt() / 15) * 15
        return scaled.coerceAtLeast(floor).coerceAtMost(seconds)
    }

    // ── Escaleras ────────────────────────────────────────────────────────────────────────────────────────────────────────

    private class Variant(
        val tier: Int,
        val exercise: Exercise? = null,
        val removed: Boolean = false,
        val group: SupersetGroup? = null,
        val technique: SeriesTechnique? = null,
    )

    /** Un nivel de la escalera de fuerza: cuántas series pendientes quedan y con qué técnica la última. */
    private class Spec(val tier: Int, val keep: Int, val technique: SeriesTechnique?, val drops: Int)

    private class Ladder(
        val id: String,
        val kind: ShortenItemKind,
        val role: ShortenRole,
        val title: String,
        val order: Double,
        val levels: List<Variant>,
        val original: Exercise? = null,
        val originalGroup: SupersetGroup? = null,
        val pair: Pair<String, String>? = null,
    )

    private class Materialized(
        val exercises: List<Exercise>,
        val groups: List<SupersetGroup>,
        val removed: Set<String>,
        val newPairs: List<Pair<Exercise, Exercise>>,
    )

    private class Context(
        val session: Session,
        val index: Map<String, ExerciseMuscleInfo>,
        val protectedIds: Map<String, Set<String>>,
        val locked: Set<String>,
        val ignored: Set<String>,
        val currentExerciseId: String?,
    ) {
        val all: List<Exercise> = session.allExercises()
        val anythingLogged = protectedIds.values.any { it.isNotEmpty() }
        val hasStrength = all.any { !it.isCardio && it.id !in ignored }

        fun info(exercise: Exercise): ExerciseMuscleInfo? = UltraFastEngine.resolveInfo(exercise, index)

        fun protectedOf(exercise: Exercise): Set<String> = protectedIds[exercise.id].orEmpty()

        fun pendingIndices(exercise: Exercise): List<Int> {
            val prot = protectedOf(exercise)
            return exercise.sets.indices.filter { exercise.sets[it].id !in prot && !exercise.sets[it].isEmptySlot }
        }

        /** Tiempo de lo que falta: solo series pendientes; la preparación de un ejercicio empezado ya pasó. */
        fun pendingSeconds(exercises: List<Exercise>, groups: List<SupersetGroup>): Int {
            val view = exercises.mapNotNull { exercise ->
                if (exercise.id in ignored) return@mapNotNull null
                val prot = protectedOf(exercise)
                if (exercise.isCardio) return@mapNotNull exercise.takeIf { prot.isEmpty() }
                val pending = exercise.sets.filter { it.id !in prot && !it.isEmptySlot }
                when {
                    pending.isEmpty() -> null
                    prot.isEmpty() -> exercise.copy(sets = pending)
                    else -> exercise.copy(sets = pending, warmupSets = emptyList(), mobilitySeries = emptyList())
                }
            }
            return calculateSessionTimeBreakdown(view, groups, if (anythingLogged) emptyList() else session.warmup).totalSeconds
        }

        fun pendingSeconds(materialized: Materialized): Int = pendingSeconds(materialized.exercises, materialized.groups)
    }

    private fun buildLadders(ctx: Context): List<Ladder> {
        val ladders = mutableListOf<Ladder>()
        ctx.all.forEachIndexed { position, exercise ->
            val ladder = if (exercise.isCardio) cardioLadder(ctx, exercise, position) else strengthLadder(ctx, exercise, position)
            ladder?.let(ladders::add)
        }
        ctx.session.allSupersetGroups().forEach { group -> supersetRestLadder(ctx, group)?.let(ladders::add) }
        ladders += pairLadders(ctx)
        return ladders.sortedBy { it.order }
    }

    private fun strengthLadder(ctx: Context, exercise: Exercise, position: Int): Ladder? {
        if (exercise.id in ctx.locked || exercise.id in ctx.ignored) return null
        val pending = ctx.pendingIndices(exercise)
        if (pending.isEmpty()) return null
        val info = ctx.info(exercise)
        val role = roleOf(exercise, info)
        val n = pending.size
        val protectedCount = exercise.sets.count { it.id in ctx.protectedOf(exercise) && !it.isEmptySlot }
        val total = protectedCount + n
        val shortenRest = exercise.supersetGroupRefOrLegacyId() == null
        val lockedTechnique = pending.any { exercise.sets[it].hasIntensiveTechnique() }
        val technique = techniqueFor(exercise, info, role)

        val specs = mutableListOf(Spec(if (role == ShortenRole.HEAVY_COMPOUND) 2 else 1, n, null, 0))
        if (!lockedTechnique) when (role) {
            ShortenRole.HEAVY_COMPOUND -> {
                if (n >= 2 && total - 1 >= 2) specs += Spec(4, n - 1, null, 0)
                val keep = (UltraFastEngine.reduceTarget(total) - protectedCount).coerceIn(0, n)
                val name = (exercise.name + " " + (info?.name ?: "")).lowercase()
                val restPauseIsSafe = !UltraFastEngine.isDangerous(exercise, info) &&
                    !UltraFastConfig.isSquatFamily(name) && !UltraFastConfig.isDeadliftFamily(name)
                specs += Spec(5, keep, if (restPauseIsSafe && keep >= 1) SeriesTechnique.REST_PAUSE else null, 2)
            }
            ShortenRole.COMPOUND -> {
                if (n >= 2) specs += Spec(3, n - 1, technique, 1)
                val keep = (UltraFastEngine.reduceTarget(total) - protectedCount).coerceIn(0, n)
                specs += Spec(4, keep, technique.takeIf { keep >= 1 }, 1)
            }
            ShortenRole.ISOLATION -> {
                if (n >= 2) specs += Spec(2, n - 1, technique, 2)
                val keep = (ceil(total / 2.0).toInt() - protectedCount).coerceIn(0, n)
                specs += Spec(3, keep, technique.takeIf { keep >= 1 }, 2)
            }
            else -> Unit
        }

        // Solo niveles que de verdad ahorran más que el anterior (p. ej. con una sola serie pendiente acortar el descanso no ahorra).
        var lastSaving = 0
        val originalSeconds = ctx.pendingSeconds(listOf(exercise), emptyList())
        val levels = specs.mapNotNull { spec ->
            val variant = buildStrengthVariant(ctx, exercise, role, spec.keep, spec.technique, spec.drops, shortenRest)
            val saving = originalSeconds - ctx.pendingSeconds(listOf(variant), emptyList())
            if (saving > lastSaving && variant != exercise) {
                lastSaving = saving
                Variant(spec.tier, exercise = variant, technique = spec.technique)
            } else null
        }
        if (levels.isEmpty()) return null
        return Ladder(exercise.id, ShortenItemKind.STRENGTH, role, exercise.name, position.toDouble(), levels, original = exercise)
    }

    private fun buildStrengthVariant(
        ctx: Context,
        exercise: Exercise,
        role: ShortenRole,
        keepPending: Int,
        technique: SeriesTechnique?,
        drops: Int,
        shortenRest: Boolean,
    ): Exercise {
        val prot = ctx.protectedOf(exercise)
        val pendingIds = ctx.pendingIndices(exercise).map { exercise.sets[it].id }
        val kept = pendingIds.take(keepPending).toSet()
        val lastKept = pendingIds.take(keepPending).lastOrNull()
        val sets = exercise.sets
            .filter { it.id in prot || it.id in kept || (it.isEmptySlot && it.id !in pendingIds) }
            .map { set ->
                var next = set
                if (set.id in kept && shortenRest) {
                    next = next.copy(restAfterSeconds = next.restAfterSeconds?.takeIf { it > 0 }?.let { shortenedRestSeconds(it, role) }
                        ?: next.restAfterSeconds)
                }
                if (technique != null && set.id == lastKept) next = stackTechnique(next, technique, drops)
                next
            }
        val rest = exercise.restTime ?: DEFAULT_REST_SECONDS
        val newRest = if (shortenRest) shortenedRestSeconds(rest, role) else null
        return exercise.copy(
            sets = sets,
            restTime = if (newRest != null && newRest != rest) newRest else exercise.restTime,
        )
    }

    private fun stackTechnique(set: ExerciseSet, technique: SeriesTechnique, count: Int): ExerciseSet {
        val stacked = set.withTechnique(technique)
        if (count <= 0) return stacked
        return stacked.copy(plannedIntensityTechniques = stacked.plannedIntensityTechniques.map { planned ->
            if (planned.type == TechniqueType.DROP_SET || planned.type == TechniqueType.REST_PAUSE) {
                planned.copy(params = planned.params + ("count" to count.toString()))
            } else planned
        })
    }

    /**
     * Drop-set donde bajar la carga es inmediato (polea, máquina, smith, mancuernas); rest-pause con barra libre, peso corporal o
     * en los compuestos pesados (cambiar discos a mitad de serie no ahorra tiempo y quita técnica).
     */
    private fun techniqueFor(exercise: Exercise, info: ExerciseMuscleInfo?, role: ShortenRole): SeriesTechnique {
        if (role == ShortenRole.HEAVY_COMPOUND) return SeriesTechnique.REST_PAUSE
        val equipment = info?.equipment.orEmpty().lowercase()
        val name = exercise.name.lowercase()
        val quickLoad = listOf("polea", "cable", "maquina", "máquina", "machine", "smith", "mancuerna", "dumbbell", "pec deck", "prensa")
            .any { it in equipment || it in name }
        if (quickLoad) return SeriesTechnique.DROPSET
        val bodyweight = exercise.sets.isNotEmpty() && exercise.sets.all {
            it.loadModeV2 == LoadModeV2.BODYWEIGHT || it.loadModeV2 == LoadModeV2.ASSISTED || it.loadModeV2 == LoadModeV2.LASTRE
        } || "corporal" in equipment || "bodyweight" in equipment
        val noLoad = exercise.sets.none { (it.weight ?: 0.0) > 0.0 }
        if (bodyweight || UltraFastConfig.isBarbellEquipment(equipment, name) || (equipment.isBlank() && noLoad)) return SeriesTechnique.REST_PAUSE
        return SeriesTechnique.DROPSET
    }

    private fun ExerciseSet.hasIntensiveTechnique(): Boolean = isDropSet || isRestPause ||
        plannedIntensityTechniques.any { it.type == TechniqueType.DROP_SET || it.type == TechniqueType.REST_PAUSE }

    private fun cardioLadder(ctx: Context, exercise: Exercise, position: Int): Ladder? {
        if (exercise.id in ctx.locked || exercise.id in ctx.ignored || ctx.protectedOf(exercise).isNotEmpty()) return null
        val details = exercise.cardioDetails ?: return null
        val levels = mutableListOf<Variant>()
        var lastSeconds = details.effectiveDurationSeconds()
        for ((factor, tier) in listOf(0.75 to 2, 0.5 to 3)) {
            val shorter = shortenCardio(details, factor) ?: continue
            val seconds = shorter.effectiveDurationSeconds()
            if (seconds in 1 until lastSeconds) {
                lastSeconds = seconds
                levels += Variant(tier, exercise = exercise.copy(cardioDetails = shorter))
            }
        }
        // Quitarlo solo si es accesorio (hay fuerza en la sesión) y no es el ejercicio en curso.
        if (ctx.hasStrength && exercise.id != ctx.currentExerciseId) levels += Variant(4, removed = true)
        if (levels.isEmpty()) return null
        return Ladder(exercise.id, ShortenItemKind.CARDIO, ShortenRole.CARDIO, exercise.name, position.toDouble(), levels, original = exercise)
    }

    /** Cardio más corto conservando su naturaleza: menos rondas en HIIT/intervalos, menos minutos en continuo (suelo 5 min). */
    internal fun shortenCardio(details: CardioDetails, factor: Double): CardioDetails? {
        val hiit = details.hiit
        if (hiit != null) {
            val effective = CardioHiitProgramBuilder.effectiveStructure(hiit)
            val target = effective.workTimeTargetSeconds
            val config = if (target != null) {
                val shorter = (target * factor).toInt().coerceAtLeast(hiit.workSeconds.coerceAtLeast(1))
                if (shorter >= target) null else hiit.copy(workTargetValue = shorter.toDouble())
            } else {
                val rounds = ceil(hiit.rounds * factor).toInt().coerceAtLeast(2)
                if (rounds >= hiit.rounds) null else hiit.copy(rounds = rounds)
            }
            return config?.let { CardioHiitProgramBuilder.buildDetails(it, details.type, details) }
        }
        if (details.hasIntervals()) {
            if (details.intervalRounds >= 2) {
                val rounds = ceil(details.intervalRounds * factor).toInt().coerceAtLeast(1)
                return if (rounds >= details.intervalRounds) null else details.copy(intervalRounds = rounds)
            }
            val blocks = details.intervalBlocks
            val lead = blocks.takeWhile { it.type == CardioBlockType.WARMUP }
            val tail = blocks.drop(lead.size).takeLastWhile { it.type == CardioBlockType.COOLDOWN }
            val middle = blocks.drop(lead.size).dropLast(tail.size)
            if (middle.size < 2) return null
            var kept = middle.take(ceil(middle.size * factor).toInt().coerceAtLeast(1))
            if (kept.size > 1 && kept.last().type == CardioBlockType.RECOVER) kept = kept.dropLast(1)
            if (kept.size >= middle.size) return null
            val next = lead + kept + tail
            return details.copy(intervalBlocks = next, targetDurationSeconds = next.sumOf { it.durationSeconds })
        }
        val duration = details.targetDurationSeconds ?: return null
        if (duration <= CARDIO_FLOOR_SECONDS) return null
        val shorter = (((duration * factor) / 60).toInt() * 60).coerceAtLeast(CARDIO_FLOOR_SECONDS)
        if (shorter >= duration) return null
        val ratio = shorter.toDouble() / duration
        return details.copy(
            targetDurationSeconds = shorter,
            targetDistanceKm = details.targetDistanceKm?.let { (it * ratio * 10).roundToInt() / 10.0 },
        )
    }

    private fun supersetRestLadder(ctx: Context, group: SupersetGroup): Ladder? {
        val members = group.exerciseOrder.mapNotNull { id -> ctx.all.firstOrNull { it.id == id } }
        if (members.isEmpty() || members.any { it.id in ctx.locked }) return null
        if (members.none { it.id !in ctx.ignored && ctx.pendingIndices(it).isNotEmpty() }) return null
        val between = { seconds: Int -> if (seconds > 15) ((seconds / 2) / 5 * 5).coerceAtLeast(10) else seconds }
        val after = { seconds: Int -> shortenedRestSeconds(seconds, ShortenRole.SUPERSET) }
        val shorter = group.copy(
            restBetweenExercises = between(group.restBetweenExercises),
            restAfterSuperset = after(group.restAfterSuperset),
            roundRestBetweenExercises = group.roundRestBetweenExercises.mapValues { between(it.value) },
            roundRestAfterSuperset = group.roundRestAfterSuperset.mapValues { after(it.value) },
        )
        if (shorter == group) return null
        val position = members.minOf { member -> ctx.all.indexOfFirst { it.id == member.id } }
        return Ladder(SUPERSET_ITEM_PREFIX + group.id, ShortenItemKind.SUPERSET_REST, ShortenRole.SUPERSET,
            "Superserie: " + members.joinToString(" + ") { it.name }, position + 0.3, listOf(Variant(1, group = shorter)),
            originalGroup = group)
    }

    /** Superseries nuevas solo donde ya hay soporte: misma polea o smith, antagonistas o de músculos distintos, sin empezar. */
    private fun pairLadders(ctx: Context): List<Ladder> {
        val buckets = linkedMapOf<String, MutableList<Exercise>>()
        ctx.all.forEach { exercise ->
            if (exercise.isCardio || exercise.id in ctx.locked || exercise.id in ctx.ignored) return@forEach
            if (exercise.supersetGroupRefOrLegacyId() != null || ctx.protectedOf(exercise).isNotEmpty()) return@forEach
            if (ctx.pendingIndices(exercise).isEmpty()) return@forEach
            val info = ctx.info(exercise)
            if (roleOf(exercise, info) == ShortenRole.HEAVY_COMPOUND) return@forEach
            val key = UltraFastConfig.machineKeyForSuperset(info?.equipment, null) ?: return@forEach
            buckets.getOrPut(key) { mutableListOf() } += exercise
        }
        val ladders = mutableListOf<Ladder>()
        buckets.values.forEach { bucket ->
            val used = mutableSetOf<String>()
            bucket.forEach { a ->
                if (a.id in used) return@forEach
                val b = bucket.firstOrNull { it.id != a.id && it.id !in used &&
                    UltraFastEngine.areGoodAntagonistPair(ctx.info(a), ctx.info(it)) } ?: return@forEach
                used += a.id
                used += b.id
                val position = minOf(ctx.all.indexOfFirst { it.id == a.id }, ctx.all.indexOfFirst { it.id == b.id })
                ladders += Ladder(PAIR_ITEM_PREFIX + a.id + "|" + b.id, ShortenItemKind.NEW_SUPERSET, ShortenRole.SUPERSET,
                    a.name + " + " + b.name, position + 0.6, listOf(Variant(2)), pair = a.id to b.id)
            }
        }
        return ladders
    }

    private fun isBlocked(ladder: Ladder, excluded: Set<String>): Boolean =
        ladder.id in excluded || ladder.pair?.let { (a, b) -> a in excluded || b in excluded } == true

    // ── Materializar y elegir ────────────────────────────────────────────────────────────────────────────────────────────

    private fun materialize(ctx: Context, ladders: List<Ladder>, levels: Map<String, Int>): Materialized {
        val byId = ladders.associateBy { it.id }
        val removed = mutableSetOf<String>()
        val flat = ctx.all.mapNotNull { exercise ->
            val ladder = byId[exercise.id]
            val level = levels[exercise.id] ?: 0
            if (ladder == null || level == 0) return@mapNotNull exercise
            val variant = ladder.levels[level - 1]
            if (variant.removed) {
                removed += exercise.id
                null
            } else variant.exercise ?: exercise
        }.toMutableList()
        val groups = ctx.session.allSupersetGroups().map { group ->
            val id = SUPERSET_ITEM_PREFIX + group.id
            val level = levels[id] ?: 0
            if (level == 0) group else byId[id]?.levels?.get(level - 1)?.group ?: group
        }.toMutableList()
        val pairs = mutableListOf<Pair<Exercise, Exercise>>()
        ladders.filter { it.kind == ShortenItemKind.NEW_SUPERSET && (levels[it.id] ?: 0) > 0 }.forEach { ladder ->
            val (aId, bId) = ladder.pair ?: return@forEach
            val ia = flat.indexOfFirst { it.id == aId }
            val ib = flat.indexOfFirst { it.id == bId }
            if (ia < 0 || ib < 0) return@forEach
            val groupId = "ultra_fast_ss_${aId}_$bId"
            val rounds = maxOf(flat[ia].sets.size, flat[ib].sets.size).coerceAtLeast(1)
            groups += SupersetGroup(
                id = groupId,
                exerciseOrder = listOf(aId, bId),
                restBetweenExercises = NEW_SUPERSET_REST_BETWEEN,
                restAfterSuperset = NEW_SUPERSET_REST_AFTER,
                rounds = rounds,
                roundRestBetweenExercises = (0 until rounds).associateWith { NEW_SUPERSET_REST_BETWEEN },
                roundRestAfterSuperset = (0 until rounds).associateWith { NEW_SUPERSET_REST_AFTER },
            )
            listOf(ia, ib).forEach { i ->
                flat[i] = flat[i].copy(supersetGroupRef = groupId, supersetId = groupId,
                    supersetRestBetween = NEW_SUPERSET_REST_BETWEEN, supersetRestAfter = NEW_SUPERSET_REST_AFTER)
            }
            pairs += flat[ia] to flat[ib]
        }
        return Materialized(flat, groups, removed, pairs)
    }

    private fun greedy(ctx: Context, ladders: List<Ladder>, blocked: Set<String>, beforeSeconds: Int, targetSeconds: Int): Map<String, Int> {
        val levels = mutableMapOf<String, Int>()
        var saved = 0
        while (saved < targetSeconds) {
            val candidates = ladders.filter { it.id !in blocked && (levels[it.id] ?: 0) < it.levels.size }
            if (candidates.isEmpty()) break
            val tier = candidates.minOf { it.levels[levels[it.id] ?: 0].tier }
            val scored = candidates.filter { it.levels[levels[it.id] ?: 0].tier == tier }.map { ladder ->
                val trial = levels + (ladder.id to (levels[ladder.id] ?: 0) + 1)
                ladder to (beforeSeconds - ctx.pendingSeconds(materialize(ctx, ladders, trial)) - saved)
            }
            val remaining = targetSeconds - saved
            val useful = scored.filter { it.second > 0 }
            val pick = useful.filter { it.second >= remaining }.minByOrNull { it.second }
                ?: useful.maxByOrNull { it.second }
            if (pick == null) {
                // Nada en este coste ahorra en el contexto actual: subirlos igual para no bloquear sus niveles siguientes.
                scored.forEach { (ladder, _) -> levels[ladder.id] = (levels[ladder.id] ?: 0) + 1 }
            } else {
                levels[pick.first.id] = (levels[pick.first.id] ?: 0) + 1
            }
            saved = beforeSeconds - ctx.pendingSeconds(materialize(ctx, ladders, levels))
        }
        return levels
    }

    // ── Objetivo ─────────────────────────────────────────────────────────────────────────────────────────────────────────

    /**
     * Con límite de sesión y una proyección que lo pasa: el exceso redondeado a 5 min. Sin límite: un cuarto de lo que falta,
     * entre 5 y 20 min. Si no se alcanza, «lo mínimo eficaz».
     */
    internal fun defaultGoal(pendingSeconds: Int, maxSavedSeconds: Int, elapsedSeconds: Int, limitMinutes: Int?): ShortenGoal {
        val excess = limitMinutes?.takeIf { it > 0 }?.let { elapsedSeconds + pendingSeconds - it * 60 }
        val wanted = if (excess != null && excess > 0) {
            val minutes = ceil(excess / 60.0).toInt()
            ((minutes + 4) / 5) * 5
        } else {
            ((pendingSeconds / 60.0 * 0.25 / 5).roundToInt() * 5).coerceIn(5, 20)
        }
        return if (wanted * 60 >= maxSavedSeconds) ShortenGoal.MINIMUM else ShortenGoal(wanted)
    }

    internal fun goalOptions(goal: ShortenGoal, maxSavedSeconds: Int): List<ShortenGoal> {
        val reachable = maxSavedSeconds / 60
        var minutes = (listOfNotNull(goal.minutes) + listOf(10, 20)).distinct().filter { it in 1 until reachable }.sorted()
        if (minutes.isEmpty() && reachable > 5) minutes = listOf(5)
        return minutes.take(3).map(::ShortenGoal) + ShortenGoal.MINIMUM
    }

    // ── Descripción ──────────────────────────────────────────────────────────────────────────────────────────────────────

    private fun describe(
        ctx: Context,
        ladders: List<Ladder>,
        levels: Map<String, Int>,
        excluded: Set<String>,
        result: Materialized,
        afterSeconds: Int,
    ): List<ShortenItemChange> {
        val paired = result.newPairs.flatMap { listOf(it.first.id, it.second.id) }.toSet()
        return ladders.mapNotNull { ladder ->
            if (ladder.id in excluded) {
                return@mapNotNull ShortenItemChange(ladder.id, ladder.kind, ladder.role, ladder.title, "", 0, excluded = true)
            }
            val level = levels[ladder.id] ?: 0
            if (level == 0) return@mapNotNull null
            val variant = ladder.levels[level - 1]
            val detail = when (ladder.kind) {
                ShortenItemKind.STRENGTH -> describeStrength(ctx, ladder.original!!, variant, ladder.id in paired)
                ShortenItemKind.CARDIO -> if (variant.removed) "Quitar de hoy" else describeCardio(ladder.original!!, variant.exercise!!)
                ShortenItemKind.SUPERSET_REST -> "descanso " + clock(ladder.originalGroup!!.restAfterSuperset) + " → " +
                    clock(variant.group!!.restAfterSuperset)
                ShortenItemKind.NEW_SUPERSET -> "en superserie · descanso " + clock(NEW_SUPERSET_REST_AFTER) + " entre rondas"
            }
            if (detail.isBlank()) return@mapNotNull null
            val without = levels - ladder.id
            val share = (ctx.pendingSeconds(materialize(ctx, ladders, without)) - afterSeconds).coerceAtLeast(0)
            ShortenItemChange(ladder.id, ladder.kind, ladder.role, ladder.title, detail, share,
                removesExercise = variant.removed)
        }
    }

    private fun describeStrength(ctx: Context, original: Exercise, variant: Variant, inNewSuperset: Boolean): String {
        val after = variant.exercise ?: return ""
        val parts = mutableListOf<String>()
        val before = original.sets.count { !it.isEmptySlot }
        val now = after.sets.count { !it.isEmptySlot }
        if (now < before) parts += "$before → $now series"
        if (now < before && ctx.pendingIndices(after).isEmpty()) parts += "terminar con lo hecho"
        when (variant.technique.takeIf { ctx.pendingIndices(after).isNotEmpty() }) {
            SeriesTechnique.DROPSET -> parts += "última en drop-set"
            SeriesTechnique.REST_PAUSE -> parts += "última en rest-pause"
            else -> Unit
        }
        val restBefore = original.restTime ?: DEFAULT_REST_SECONDS
        val restAfter = after.restTime ?: DEFAULT_REST_SECONDS
        if (!inNewSuperset && restAfter != restBefore) parts += "descanso " + clock(restBefore) + " → " + clock(restAfter)
        return parts.joinToString(" · ")
    }

    private fun describeCardio(original: Exercise, after: Exercise): String {
        val a = original.cardioDetails ?: return ""
        val b = after.cardioDetails ?: return ""
        val parts = mutableListOf<String>()
        val roundsBefore = a.hiit?.let { CardioHiitProgramBuilder.effectiveStructure(it).rounds } ?: a.intervalRounds
        val roundsAfter = b.hiit?.let { CardioHiitProgramBuilder.effectiveStructure(it).rounds } ?: b.intervalRounds
        if ((a.hiit != null || a.hasIntervals()) && roundsAfter < roundsBefore) parts += "$roundsBefore → $roundsAfter rondas"
        parts += "${minutes(a.effectiveDurationSeconds())} → ${minutes(b.effectiveDurationSeconds())} min"
        return parts.joinToString(" · ")
    }

    private fun compatChanges(ctx: Context, ladders: List<Ladder>, levels: Map<String, Int>, result: Materialized): List<UltraFastExerciseChange> =
        ladders.filter { it.kind == ShortenItemKind.STRENGTH && (levels[it.id] ?: 0) > 0 }.mapNotNull { ladder ->
            val original = ladder.original ?: return@mapNotNull null
            val after = result.exercises.firstOrNull { it.id == original.id } ?: return@mapNotNull null
            val variant = ladder.levels[(levels[ladder.id] ?: 1) - 1]
            val reason = when (ladder.role) {
                ShortenRole.HEAVY_COMPOUND -> if (UltraFastEngine.isDangerous(original, ctx.info(original))) {
                    UltraFastReason.DANGEROUS_COMPLEX
                } else UltraFastReason.PROTECTED_BASIC
                ShortenRole.ISOLATION -> UltraFastReason.ISOLATION_DENSIFIED
                else -> UltraFastReason.COMPOUND_REDUCED
            }
            UltraFastExerciseChange(
                exerciseId = original.id,
                exerciseName = original.name,
                beforeSets = original.sets.size,
                afterSets = after.sets.size,
                beforeTechnique = "Normal",
                afterTechnique = when (variant.technique) {
                    SeriesTechnique.DROPSET -> "Dropset"
                    SeriesTechnique.REST_PAUSE -> "Rest-Pause"
                    else -> "Normal"
                },
                reason = reason,
                wasDensified = variant.technique != null,
                wasReduced = after.sets.size < original.sets.size,
            )
        }

    private fun minutes(seconds: Int): Int = (seconds + 30) / 60

    /** 90 → «1:30». */
    fun clock(seconds: Int): String {
        val s = seconds.coerceAtLeast(0)
        return "${s / 60}:${(s % 60).toString().padStart(2, '0')}"
    }
}
