package com.example.kpkn.domain.training.generator

import com.example.kpkn.domain.onboarding.LiftMark

/** Un ejercicio elegido para un hueco, con todo lo que la prescripción y el ensamblado necesitan. */
internal data class Candidate(
    val entry: CatalogEntry,
    val label: String?,
    val kind: ExKind,
    val basic: Boolean,
    val mark: LiftMark?,
    val markFactor: Double,
    val reps: IntRange?,
    val seconds: Int?,
    val rank: Double,
    val groupId: String,
    val fromLadder: Boolean,
    /** Identidad de la escalera de peso corporal de la que sale (null si no sale de una): una por sesión. */
    val ladderKey: String? = null,
)

/** Lo que ya se usó en la sesión en curso (un ejercicio no se repite dentro de una sesión). */
internal class SessionUse {
    val configs: HashSet<String> = HashSet()
    val definitions: HashSet<String> = HashSet()

    /** Escaleras de peso corporal ya usadas: la misma escalera no da dos ejercicios en una sesión (flexión y flexión con rodillas). */
    val ladders: HashSet<String> = HashSet()

    fun add(candidate: Candidate) {
        configs += candidate.entry.id
        definitions += candidate.entry.definitionId
        candidate.ladderKey?.let { ladders += it }
    }
}

/**
 * Elige el ejercicio de cada hueco: filtra por material del día, nivel y dificultad técnica, ordena por la preferencia de
 * material del perfil (ver [tierRank]) y por las reglas de variedad de la semana, y rota con `variantSeed` entre
 * alternativas equivalentes del mismo grupo.
 *
 * Reglas:
 * - El novato (y quien vuelve) nunca recibe un ejercicio de dificultad técnica > 5,2, salvo los básicos de la reserva
 *   (sentadilla, banca, peso muerto, press…: se aprenden con carga ligera y aproximaciones); las rungs de peso corporal
 *   NO tienen esa exención.
 * - Un ejercicio no se repite en la sesión (ni su definición: nunca banca con barra y con mancuernas a la vez); en la
 *   semana, como mucho en 2 sesiones (3 los básicos). Pasado ese límite solo se repite si no hay otra opción (castigo
 *   de rango alto, no prohibición: el material mínimo no tiene tantas alternativas).
 */
internal object ExerciseSelector {

    private const val NOVICE_MAX_DIFFICULTY = 5.2
    private const val REPEAT_OVER_LIMIT_PENALTY = 10.0

    /** Preferencia de material (rango menor = antes). */
    fun tierRank(profile: RankProfile, tier: EquipmentTier): Double = when (profile) {
        RankProfile.STANDARD -> when (tier) {
            EquipmentTier.BARBELL -> 0.0
            EquipmentTier.DUMBBELL -> 1.0
            EquipmentTier.MACHINE -> 2.0
            EquipmentTier.CABLE -> 2.2
            EquipmentTier.SMITH -> 3.0
            EquipmentTier.KETTLEBELL -> 4.0
            EquipmentTier.BAND -> 5.0
            EquipmentTier.RINGS -> 5.5
            EquipmentTier.BODYWEIGHT -> 6.0
            EquipmentTier.OTHER -> 7.0
        }
        RankProfile.LEARNING -> when (tier) {
            EquipmentTier.MACHINE -> 0.0
            EquipmentTier.DUMBBELL -> 0.4
            EquipmentTier.CABLE -> 1.0
            EquipmentTier.SMITH -> 1.6
            EquipmentTier.BARBELL -> 2.4
            EquipmentTier.KETTLEBELL -> 3.0
            EquipmentTier.BAND -> 4.0
            EquipmentTier.RINGS -> 5.0
            EquipmentTier.BODYWEIGHT -> 5.5
            EquipmentTier.OTHER -> 7.0
        }
        RankProfile.FUNCTIONAL -> when (tier) {
            EquipmentTier.DUMBBELL -> 0.0
            EquipmentTier.KETTLEBELL -> 0.2
            EquipmentTier.RINGS -> 1.0
            EquipmentTier.BAND -> 1.5
            EquipmentTier.CABLE -> 2.0
            EquipmentTier.BARBELL -> 2.5
            EquipmentTier.BODYWEIGHT -> 3.0
            EquipmentTier.SMITH -> 4.0
            EquipmentTier.MACHINE -> 5.0
            EquipmentTier.OTHER -> 7.0
        }
        // Calistenia: peso corporal y anillas; la banda solo como ayuda (el resto de material ni siquiera entra).
        RankProfile.CALISTHENICS -> when (tier) {
            EquipmentTier.BODYWEIGHT -> 0.0
            EquipmentTier.RINGS -> 0.5
            EquipmentTier.BAND -> 2.0
            else -> 9.0
        }
    }

    private fun ladderRank(ladder: Ladder, tier: LadderTier, profile: RankProfile): Double {
        val easy = tier == LadderTier.EASY
        return when (profile) {
            RankProfile.CALISTHENICS -> when (ladder) {
                BodyweightLadders.pullUp -> if (easy) 0.4 else 0.2
                BodyweightLadders.pushUp -> if (easy) 0.5 else 0.2
                BodyweightLadders.dips -> 0.3
                BodyweightLadders.row -> if (easy) 0.6 else 0.4
                BodyweightLadders.singleLeg -> if (easy) 0.7 else 0.3
                BodyweightLadders.core -> 0.2
                BodyweightLadders.glute, BodyweightLadders.hinge -> 0.8
                else -> 1.0
            }
            RankProfile.FUNCTIONAL -> when (ladder) {
                BodyweightLadders.pullUp, BodyweightLadders.pushUp -> if (easy) 0.7 else 0.3
                BodyweightLadders.row -> if (easy) 0.9 else 0.5
                BodyweightLadders.dips -> 0.8
                BodyweightLadders.singleLeg -> if (easy) 1.4 else 1.0
                BodyweightLadders.core -> 0.4
                else -> 3.5
            }
            RankProfile.LEARNING -> when (ladder) {
                BodyweightLadders.pullUp -> if (easy) 3.0 else 1.9
                BodyweightLadders.pushUp -> if (easy) 5.2 else 4.8
                BodyweightLadders.row -> if (easy) 5.3 else 5.1
                BodyweightLadders.dips -> 4.4
                BodyweightLadders.singleLeg -> if (easy) 5.0 else 4.5
                BodyweightLadders.core -> 1.0
                BodyweightLadders.glute, BodyweightLadders.calf -> 6.0
                else -> 6.5
            }
            RankProfile.STANDARD -> when (ladder) {
                BodyweightLadders.pullUp -> if (easy) 2.9 else 1.6
                BodyweightLadders.pushUp -> if (easy) 5.6 else 5.0
                BodyweightLadders.row -> if (easy) 5.6 else 5.3
                BodyweightLadders.dips -> 4.2
                BodyweightLadders.singleLeg -> if (easy) 5.0 else 4.5
                BodyweightLadders.core -> 1.0
                BodyweightLadders.glute, BodyweightLadders.calf -> 6.0
                else -> 6.5
            }
        }
    }

    /** Montaje corto con poco tiempo, montaje «caro» con mucho (solo desempata entre alternativas). */
    private fun setupAdjust(setupSeconds: Int, targetMinutes: Int): Double = when {
        targetMinutes <= 30 -> (setupSeconds - 30) / 60.0
        targetMinutes >= 90 -> -(setupSeconds - 30) / 120.0
        else -> 0.0
    }

    private fun levelAllows(level: RoutineLevel, minimum: RoutineLevel): Boolean = level.ordinal >= minimum.ordinal

    private fun roleMismatch(fits: ItemRole?, role: ItemRole): Double {
        if (fits == null || fits == role) return 0.0
        val compound = setOf(ItemRole.MAIN, ItemRole.SECONDARY, ItemRole.ACCESSORY)
        return if (fits in compound && role in compound) 1.2 else 0.0
    }

    private fun usagePenalty(ctx: GenContext, id: String, basic: Boolean): Double {
        val uses = ctx.weeklyUse[id] ?: 0
        val limit = if (basic) 3 else 2
        val perUse = if (basic) 0.2 else 0.9
        return uses * perUse + if (uses >= limit) REPEAT_OVER_LIMIT_PENALTY else 0.0
    }

    /**
     * Todas las candidatas ejecutables del hueco (ya con su rango), sin elegir todavía.
     *
     * [region] es la de la sesión: la potencia con peso corporal (sentadilla y flexión «rápidas», sin salto porque el catálogo
     * no trae saltos) solo se ofrece en la sesión dedicada de cardio y potencia ([SessionRegion.NONE]). En una sesión de
     * cuerpo completo ocuparía la única sentadilla y la única flexión sin carga y dejaría sin cubrir el patrón de verdad.
     */
    fun candidates(
        pattern: RoutinePattern,
        role: ItemRole,
        ctx: GenContext,
        equipment: DayEquipment,
        use: SessionUse,
        region: SessionRegion = SessionRegion.FULL,
        tag: String? = null,
    ): List<Candidate> {
        val result = ArrayList<Candidate>()
        val novice = ctx.level == RoutineLevel.NOVICE
        var order = 0
        ctx.groupsFor(pattern).forEachIndexed { groupIndex, group ->
            val allowed = group.entries.mapNotNull { poolEntry ->
                if (tag != null && poolEntry.tag != tag) return@mapNotNull null
                val entry = ctx.catalog.entry(poolEntry.id) ?: return@mapNotNull null
                if (!ctx.tierAllowed(entry)) return@mapNotNull null
                if (!levelAllows(ctx.level, poolEntry.minLevel)) return@mapNotNull null
                if (novice && entry.difficulty > NOVICE_MAX_DIFFICULTY && !poolEntry.basic) return@mapNotNull null
                if (entry.id in use.configs || entry.definitionId in use.definitions) return@mapNotNull null
                if (!equipment.allows(entry, poolEntry.requires)) return@mapNotNull null
                poolEntry to entry
            }
            if (allowed.isEmpty()) return@forEachIndexed
            val groupBase = tierRank(ctx.rankProfile, allowed.first().second.tier) + group.bias +
                setupAdjust(allowed.first().second.setupSeconds, ctx.targetMinutes)
            allowed.forEach { (poolEntry, entry) ->
                val rank = groupBase + roleMismatch(poolEntry.fitsRole, role) + usagePenalty(ctx, entry.id, poolEntry.basic)
                result += Candidate(
                    entry = entry,
                    label = poolEntry.variant,
                    kind = poolEntry.kind,
                    basic = poolEntry.basic,
                    mark = poolEntry.mark,
                    markFactor = poolEntry.markFactor,
                    reps = poolEntry.reps,
                    seconds = null,
                    rank = rank + order * 1e-7,
                    groupId = "g$groupIndex",
                    fromLadder = false,
                )
                order++
            }
        }
        val ladders = if ((pattern == RoutinePattern.POWER && region != SessionRegion.NONE) || tag != null) {
            emptyList()
        } else {
            BodyweightLadders.byPattern[pattern].orEmpty()
        }
        ladders.forEachIndexed { ladderIndex, ladder ->
            ladderCandidates(ladder, ladderIndex, ctx, equipment, use).forEach { candidate ->
                result += candidate.copy(rank = candidate.rank + order * 1e-7)
                order++
            }
        }
        return result
    }

    private fun ladderCandidates(
        ladder: Ladder,
        ladderIndex: Int,
        ctx: GenContext,
        equipment: DayEquipment,
        use: SessionUse,
    ): List<Candidate> {
        val ladderKey = "${ladder.pattern.name}/${ladder.skill?.name}"
        if (ladderKey in use.ladders) return emptyList()
        val requested = BodyweightLadders.tierFor(ladder, ctx.request.capabilities, ctx.level)
        val tiers = when (requested) {
            LadderTier.HARD -> listOf(LadderTier.HARD, LadderTier.STANDARD, LadderTier.EASY)
            LadderTier.STANDARD -> listOf(LadderTier.STANDARD, LadderTier.EASY)
            LadderTier.EASY -> listOf(LadderTier.EASY)
        }
        val novice = ctx.level == RoutineLevel.NOVICE
        val out = ArrayList<Candidate>()
        var offset = 0.0
        for ((tierIndex, tier) in tiers.withIndex()) {
            var firstInTier = true
            var rungs = ladder.rungs(tier)
            // El novato con capacidad nula empieza por la regresión más baja (rodillas antes que inclinada).
            if (tier == LadderTier.EASY && novice && ladder.pattern == RoutinePattern.HORIZONTAL_PUSH && ladder.skill != null) {
                rungs = rungs.reversed()
            }
            for (rung in rungs) {
                val entry = ctx.catalog.entry(rung.id) ?: continue
                if (!ctx.tierAllowed(entry)) continue
                if (novice && entry.difficulty > NOVICE_MAX_DIFFICULTY) continue
                if (entry.id in use.configs || entry.definitionId in use.definitions) continue
                if (!equipment.allows(entry, rung.requires)) continue
                val base = ladderRank(ladder, tier, ctx.rankProfile) + offset + (if (firstInTier) 0.0 else 0.15) +
                    usagePenalty(ctx, entry.id, basic = false)
                out += Candidate(
                    entry = entry,
                    label = rung.label,
                    kind = rung.kind,
                    basic = false,
                    mark = null,
                    markFactor = 1.0,
                    reps = rung.reps,
                    seconds = rung.seconds,
                    rank = base,
                    groupId = "l$ladderIndex${tier.name}",
                    fromLadder = true,
                    ladderKey = ladderKey,
                )
                firstInTier = false
                if (out.size >= 3) return out
            }
            // Lo que cae a un tramo más fácil va detrás del tramo pedido.
            if (tierIndex < tiers.lastIndex && out.isNotEmpty()) offset += 0.6
        }
        return out
    }

    /** Elige la mejor candidata; entre las empatadas del mismo grupo rota con `variantSeed`. */
    fun choose(
        pattern: RoutinePattern,
        role: ItemRole,
        ctx: GenContext,
        equipment: DayEquipment,
        use: SessionUse,
        salt: Int,
        region: SessionRegion = SessionRegion.FULL,
        tag: String? = null,
    ): Candidate? {
        val all = candidates(pattern, role, ctx, equipment, use, region, tag)
        if (all.isEmpty()) return null
        val sorted = all.sortedBy { it.rank }
        val best = sorted.first()
        val ties = sorted.filter { it.groupId == best.groupId && kotlin.math.abs(it.rank - best.rank) < 1e-4 }
        if (ties.size <= 1) return best
        return ties[Math.floorMod(ctx.seed + salt, ties.size)]
    }
}
