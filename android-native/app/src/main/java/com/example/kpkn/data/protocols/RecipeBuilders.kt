package com.example.kpkn.data.protocols

import com.example.kpkn.data.models.BlockGoal
import com.example.kpkn.data.models.WeekExecutionKind
import com.example.kpkn.data.programs.DaySlotTemplate

fun warmupPercentSets(
    basis: LoadBasis = LoadBasis.PERCENT_TM,
): List<SetRecipe> = listOf(
    SetRecipe(reps = 5, percent = 40.0, isWarmup = true, loadBasis = basis),
    SetRecipe(reps = 3, percent = 55.0, isWarmup = true, loadBasis = basis),
    SetRecipe(reps = 1, percent = 65.0, isWarmup = true, loadBasis = basis),
)

/**
 * Política de aproximación separada de [warmupPercentSets] (que conservan
 * intactas los protocolos de autor): preset del plan para el primer compuesto
 * de cada patrón de movimiento — 40 % × 8, 60 % × 5, 80 % × 3 — aplicado sobre
 * la carga de trabajo, nunca sobre el 1RM.
 */
fun firstCompoundWarmupPercentSets(
    basis: LoadBasis = LoadBasis.PERCENT_TM,
): List<SetRecipe> = listOf(
    SetRecipe(reps = 8, percent = 40.0, isWarmup = true, loadBasis = basis),
    SetRecipe(reps = 5, percent = 60.0, isWarmup = true, loadBasis = basis),
    SetRecipe(reps = 3, percent = 80.0, isWarmup = true, loadBasis = basis),
)

fun copies(count: Int, reps: Int, percent: Double, basis: LoadBasis = LoadBasis.PERCENT_TM): List<SetRecipe> =
    List(count) { SetRecipe(reps = reps, percent = percent, loadBasis = basis) }

fun percentSets(
    rest: Int,
    vararg steps: Pair<Int, Double>,
    amrapLast: Boolean = false,
    topLast: Boolean = false,
    basis: LoadBasis = LoadBasis.PERCENT_TM,
): List<SetRecipe> = steps.mapIndexed { index, (reps, percent) ->
    val last = index == steps.lastIndex
    SetRecipe(
        reps = reps,
        percent = percent,
        amrap = last && amrapLast,
        isTopSet = last && topLast,
        loadBasis = basis,
    )
}

fun repeatPercentSets(
    count: Int,
    reps: Int,
    percent: Double,
    rest: Int,
    amrapLast: Boolean = false,
    basis: LoadBasis = LoadBasis.PERCENT_TM,
): List<SetRecipe> = List(count) { index ->
    SetRecipe(
        reps = reps,
        percent = percent,
        amrap = amrapLast && index == count - 1,
        loadBasis = basis,
    )
}

fun rpeSets(
    count: Int,
    reps: Int,
    rpe: Double,
    rest: Int = 90,
    rir: Int? = null,
    repsMax: Int? = null,
): List<SetRecipe> = List(count) {
    SetRecipe(
        reps = reps,
        repsMax = repsMax,
        rpe = rpe,
        rir = rir,
        loadBasis = if (rir != null) LoadBasis.RPE else LoadBasis.RPE,
    )
}

fun rirSets(count: Int, reps: Int, rir: Int, rest: Int = 90, repsMax: Int? = null): List<SetRecipe> =
    List(count) {
        SetRecipe(reps = reps, repsMax = repsMax, rir = rir, rpe = (10 - rir).toDouble(), loadBasis = LoadBasis.RPE)
    }

fun rangeRirSets(count: Int, repsMin: Int, repsMax: Int, rir: Int): List<SetRecipe> =
    List(count) {
        SetRecipe(
            reps = repsMin,
            repsMin = repsMin,
            repsMax = repsMax,
            rir = rir,
            rpe = (10 - rir).toDouble(),
            loadBasis = LoadBasis.RPE,
        )
    }

fun slot(
    id: String,
    role: SlotRole,
    configurationId: String,
    sets: List<SetRecipe>,
    restSeconds: Int,
    liftSlot: LiftSlot? = null,
    technique: TechniqueModifier? = null,
    supplementalOf: String? = null,
    isCompetitionLift: Boolean = false,
    isUnilateral: Boolean = false,
    source: SlotSource = SlotSource.AUTHOR,
    priority: SlotPriority = SlotPriority.NORMAL,
): SlotRecipe = SlotRecipe(
    id = id,
    role = role,
    lift = LiftRef(configurationId, liftSlot),
    sets = sets,
    restSeconds = restSeconds,
    technique = technique,
    supplementalOf = supplementalOf,
    isCompetitionLift = isCompetitionLift,
    isUnilateral = isUnilateral,
    source = source,
    priority = priority,
)

fun day(
    label: String,
    slots: List<SlotRecipe>,
    archetype: DaySlotTemplate? = null,
    priority: SlotPriority = SlotPriority.NORMAL,
    weekday: Int? = null,
): DayRecipe = DayRecipe(label = label, slots = slots, archetype = archetype, priority = priority, weekday = weekday)

fun weekRecipe(
    weekNumber: Int,
    blockIndex: Int,
    blockName: String,
    blockGoal: BlockGoal,
    days: List<DayRecipe>,
    kind: WeekExecutionKind = WeekExecutionKind.TRAINING,
    weekName: String = "",
): WeekRecipe = WeekRecipe(
    weekNumber = weekNumber,
    blockIndex = blockIndex,
    blockName = blockName,
    blockGoal = blockGoal,
    kind = kind,
    days = days,
    weekName = weekName,
)

fun DayRecipe.dropT3(n: Int): DayRecipe {
    if (n <= 0) return this
    return copy(
        slots = slots.map { slot ->
            if (slot.role != SlotRole.T3_ACCESSORY) slot
            else slot.copy(sets = slot.sets.take((slot.sets.size - n).coerceAtLeast(1)))
        },
    )
}

fun topSetAndBackoff(
    topSets: Int,
    topReps: Int,
    topPct: Double,
    backSets: Int,
    backReps: Int,
    backPct: Double,
    rpe: Double? = null,
    basis: LoadBasis = LoadBasis.PERCENT_TM,
): List<SetRecipe> {
    val top = List(topSets) { index ->
        SetRecipe(
            reps = topReps,
            percent = topPct,
            isTopSet = index == topSets - 1,
            rpe = rpe,
            loadBasis = basis,
        )
    }
    val back = List(backSets) {
        SetRecipe(reps = backReps, percent = backPct, loadBasis = basis)
    }
    return top + back
}

fun DayRecipe.replaceT1Work(
    work: List<SetRecipe>,
    configurationId: String? = null,
    technique: TechniqueModifier? = null,
    keepWarmup: Boolean = true,
): DayRecipe = copy(
    slots = slots.map { slot ->
        if (slot.role != SlotRole.T1_MAIN) slot
        else {
            val warmups = if (keepWarmup) slot.sets.filter { it.isWarmup } else emptyList()
            slot.copy(
                lift = configurationId?.let { slot.lift.copy(configurationId = it) } ?: slot.lift,
                technique = technique ?: slot.technique,
                sets = warmups + work,
            )
        }
    },
)

fun DayRecipe.replaceT2Lift(
    configurationId: String,
    technique: TechniqueModifier? = null,
): DayRecipe = copy(
    slots = slots.map { slot ->
        if (slot.role != SlotRole.T2_SUPPLEMENTAL) slot
        else slot.copy(
            lift = slot.lift.copy(configurationId = configurationId),
            technique = technique ?: slot.technique,
        )
    },
)

fun DayRecipe.asKpknSuggested(): DayRecipe = copy(
    slots = slots.map { it.copy(source = SlotSource.KPKN_DEFAULT) },
)

fun DayRecipe.withMinRir(minRir: Int): DayRecipe = copy(
    slots = slots.map { slot ->
        slot.copy(
            sets = slot.sets.map { set ->
                if (set.isWarmup) set
                else set.copy(
                    rir = maxOf(set.rir ?: minRir, minRir),
                    rpe = minOf(set.rpe ?: (10 - minRir).toDouble(), (10 - minRir).toDouble()),
                )
            },
        )
    },
)

fun attributed(
    primaryReference: String,
    primaryUrl: String,
    author: String,
    variant: String? = null,
    evidenceUrl: String? = null,
): ProtocolSource = ProtocolSource(
    primaryReference = primaryReference,
    primaryUrl = primaryUrl,
    evidenceUrl = evidenceUrl ?: primaryUrl,
    variant = variant,
    disclaimer = "No afiliado a $author",
    reviewedAt = "2026-09-13",
    catalogRevision = "v2-approved-2026-08-12-a",
    approvedBy = "KPKN Editorial",
)
