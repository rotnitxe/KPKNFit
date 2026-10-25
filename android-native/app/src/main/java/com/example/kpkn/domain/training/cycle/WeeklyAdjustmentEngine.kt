package com.example.kpkn.domain.training.cycle

import com.example.kpkn.data.models.VolumeRecommendation
import com.example.kpkn.data.models.WeekExecutionKind
import com.example.kpkn.data.programs.KpknMuscleGroup
import com.example.kpkn.data.programs.VolumeLandmarks
import com.example.kpkn.data.protocols.SlotRecipe
import com.example.kpkn.data.protocols.SlotRole
import com.example.kpkn.data.protocols.WeekRecipe
import com.example.kpkn.domain.training.ExerciseCompositionMetadataProvider
import com.example.kpkn.domain.training.RecipeContractPolicy
import com.example.kpkn.domain.training.budgetMuscleGroups
import com.example.kpkn.domain.training.split.CatalogTraits
import java.text.Normalizer

data class WeeklyAdjustmentChange(val muscleGroup: KpknMuscleGroup, val setsDelta: Int, val rirDelta: Int, val reason: String)
data class WeeklyAdjustment(val weekRecipe: WeekRecipe, val changes: List<WeeklyAdjustmentChange>)

/** Propuesta editorial basada en respuestas originales. No estima fatiga, recuperación ni capacidad fisiológica. */
object WeeklyAdjustmentEngine {
    const val MAX_SETS_PER_SLOT = 8
    const val MIN_SETS_PER_SLOT = 2
    const val MIN_RIR = 1
    const val MAX_RIR = 4

    fun decide(performance: Int, soreness: Int): Pair<Int, Int> {
        require(performance in 1..4 && soreness in 1..4)
        return when {
            performance == 4 -> -1 to 1
            soreness == 4 -> -1 to 0
            performance == 3 || soreness == 3 -> 0 to 0
            performance == 1 -> 1 to -1
            else -> 1 to 0
        }
    }

    internal fun primaryGroups(slot: SlotRecipe, metadata: ExerciseCompositionMetadataProvider): Set<KpknMuscleGroup> {
        val meta = metadata.metadata(slot.lift.configurationId) ?: return emptySet()
        return meta.primaryMuscles.mapNotNull { CatalogTraits.atomOf(it, meta.movementPatternId.orEmpty(), meta.configurationId) }.toSet()
    }

    internal fun accessoryGroups(week: WeekRecipe, metadata: ExerciseCompositionMetadataProvider): Set<KpknMuscleGroup> =
        week.days.flatMap { it.slots }.filter { it.role == SlotRole.T3_ACCESSORY && it.sets.any { set -> !set.isWarmup } }
            .flatMap { primaryGroups(it, metadata) }.toSet()

    fun adjust(
        week: WeekRecipe,
        checkin: WeeklyCheckin,
        metadata: ExerciseCompositionMetadataProvider,
        /** Límites de planificación aportados explícitamente, nunca una capacidad personal medida. */
        recommendations: List<VolumeRecommendation> = emptyList(),
        allowRirChange: Boolean = true,
        protectedDayIndexes: Set<Int> = emptySet(),
    ): WeeklyAdjustment {
        if (checkin.skipped || week.kind != WeekExecutionKind.TRAINING) return WeeklyAdjustment(week, emptyList())
        val eligible = accessoryGroups(week, metadata)
        val answers = linkedMapOf<KpknMuscleGroup, Pair<Int, Int>>()
        if (checkin.muscles.isNotEmpty()) {
            if (checkin.muscles.any { it.performance !in 1..4 || it.soreness !in 1..4 } ||
                checkin.muscles.map { it.muscleGroup }.distinct().size != checkin.muscles.size) return WeeklyAdjustment(week, emptyList())
            checkin.muscles.forEach { answer ->
                KpknMuscleGroup.entries.firstOrNull { it.name == answer.muscleGroup && it in eligible }?.let {
                    answers[it] = answer.performance to answer.soreness
                }
            }
        } else {
            val p = checkin.globalPerformance ?: return WeeklyAdjustment(week, emptyList())
            val s = checkin.globalSoreness ?: return WeeklyAdjustment(week, emptyList())
            if (p !in 1..4 || s !in 1..4) return WeeklyAdjustment(week, emptyList())
            eligible.forEach { answers[it] = p to s }
        }
        var working = week
        val initialTotals = RecipeContractPolicy.weeklyEquivalentSets(week, metadata)
        val changes = mutableListOf<WeeklyAdjustmentChange>()
        val rirTouched = mutableSetOf<Pair<Int, Int>>()
        answers.toSortedMap(compareBy { it.ordinal }).forEach { (group, answer) ->
            val (desiredSets, desiredRir) = decide(answer.first, answer.second)
            val totals = RecipeContractPolicy.weeklyEquivalentSets(working, metadata)
            val (min, max) = bounds(group, recommendations)
            val current = totals[group] ?: 0.0
            val allowedSets = when {
                desiredSets > 0 && current < (initialTotals[group] ?: 0.0) + 1 && current + 1 <= max -> 1
                desiredSets < 0 && current > (initialTotals[group] ?: 0.0) - 1 && current - 1 >= min -> -1
                else -> 0
            }
            val candidates = working.days.flatMapIndexed { d, day -> day.slots.mapIndexedNotNull { s, slot ->
                (d to s).takeIf { d !in protectedDayIndexes && slot.role == SlotRole.T3_ACCESSORY && group in primaryGroups(slot, metadata) }
            } }
            val setTarget = (if (allowedSets < 0) candidates.reversed() else candidates).firstOrNull { (d, s) ->
                val slot = working.days[d].slots[s]
                val n = slot.sets.count { !it.isWarmup }
                val compatible = primaryGroups(slot, metadata).all { g -> answers[g]?.let { decide(it.first, it.second).first == desiredSets } != false }
                compatible && if (allowedSets > 0) n in 1 until MAX_SETS_PER_SLOT else allowedSets < 0 && n > MIN_SETS_PER_SLOT
            }
            var actualSets = 0
            if (setTarget != null) {
                val (d, s) = setTarget
                val slot = working.days[d].slots[s]
                val last = slot.sets.indexOfLast { !it.isWarmup }
                val sets = slot.sets.toMutableList().apply {
                    if (allowedSets > 0) add(last + 1, slot.sets[last]) else removeAt(last)
                }
                val candidate = replace(working, d, s, slot.copy(sets = sets))
                val after = RecipeContractPolicy.weeklyEquivalentSets(candidate, metadata)
                // Una serie compartida no puede rebasar el límite de otro grupo al añadirla.
                if (after.all { (g, value) ->
                    val (lower, upper) = bounds(g, recommendations)
                    value <= maxOf(totals[g] ?: 0.0, upper.toDouble()) &&
                        value >= minOf(totals[g] ?: 0.0, lower.toDouble())
                }) {
                    working = candidate
                    actualSets = allowedSets
                }
            }
            var actualRir = 0
            if (allowRirChange && desiredRir != 0) candidates.filterNot { it in rirTouched }.forEach { (d, s) ->
                val slot = working.days[d].slots[s]
                if (primaryGroups(slot, metadata).any { g -> answers[g]?.let { decide(it.first, it.second).second != desiredRir } == true }) return@forEach
                val sets = slot.sets.map { set ->
                    if (set.isWarmup || set.rir == null || set.rir !in MIN_RIR..MAX_RIR) set else set.copy(rir = (set.rir + desiredRir).coerceIn(MIN_RIR, MAX_RIR))
                }
                if (sets != slot.sets) {
                    working = replace(working, d, s, slot.copy(sets = sets))
                    rirTouched += d to s
                    actualRir = desiredRir
                }
            }
            if (actualSets != 0 || actualRir != 0) {
                val series = when (actualSets) { 1 -> "+1 serie"; -1 -> "−1 serie"; else -> "sin cambio de series (límite o tope de planificación)" }
                val rir = when (actualRir) { 1 -> ", +1 RIR"; -1 -> ", −1 RIR"; else -> "" }
                changes += WeeklyAdjustmentChange(group, actualSets, actualRir,
                    "${RecipeContractPolicy.volumeGroupLabel(group)}: respuesta de rendimiento ${answer.first}/4 y agujetas ${answer.second}/4 → $series$rir.")
            }
        }
        return WeeklyAdjustment(working, changes)
    }

    private fun replace(week: WeekRecipe, day: Int, slot: Int, value: SlotRecipe): WeekRecipe =
        week.copy(days = week.days.mapIndexed { d, old -> if (d != day) old else old.copy(slots = old.slots.mapIndexed { s, current -> if (s == slot) value else current }) })

    private fun normalized(value: String): String = Normalizer.normalize(value, Normalizer.Form.NFD)
        .replace(Regex("\\p{M}"), "").lowercase().trim()

    private fun bounds(group: KpknMuscleGroup, recommendations: List<VolumeRecommendation>): Pair<Int, Int> {
        val names = budgetMuscleGroups().filterValues { it == group }.keys
            .plus(RecipeContractPolicy.volumeGroupLabel(group)).plus(group.name).map(::normalized)
        val reference = VolumeLandmarks.byGroup.getValue(group)
        val custom = recommendations.firstOrNull { normalized(it.muscleGroup) in names && it.minEffectiveVolume >= 0 && it.maxRecoverableVolume >= it.minEffectiveVolume }
        return (custom?.minEffectiveVolume ?: reference.mev) to (custom?.maxRecoverableVolume ?: reference.mrv)
    }
}
