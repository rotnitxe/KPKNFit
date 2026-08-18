package com.example.kpkn.domain.templates

import com.example.kpkn.data.models.ExerciseMuscleInfo
import com.example.kpkn.data.models.PredictedDrain
import com.example.kpkn.data.sessions.SESSION_TEMPLATES_SYSTEM
import com.example.kpkn.data.sessions.SessionTemplate
import com.example.kpkn.data.sessions.SessionTemplateDurationClass
import com.example.kpkn.data.sessions.SessionTemplateFocusCategory
import com.example.kpkn.data.splits.Difficulty
import com.example.kpkn.data.splits.SplitTemplate
import kotlin.math.abs

data class SuggestionPrefs(
    val preferredDifficulty: Difficulty? = null,
    /** Override por índice de día de entrenamiento (0-based en pattern sin Descanso). templateId forzado. */
    val forcedTemplateByDayIndex: Map<Int, String> = emptyMap(),
    /** Preferencia de músculo foco por dayIndex (ej. "Glúteos", "Isquiosurales", "Cuádriceps"). */
    val preferredFocusMuscleByDayIndex: Map<Int, String> = emptyMap(),
)

data class SuggestedDayPlan(
    val dayIndex: Int,
    val dayLabel: String,
    val template: SessionTemplate?,
    val alternatives: List<SessionTemplate>, // top 2 distintos del elegido
    val score: Double,
    val unavailabilityReason: String? = null,
)

data class SuggestedWeekPlan(
    val days: List<SuggestedDayPlan>,
    val weeklyDrain: PredictedDrain,
    val weeklyVolume: Map<String, Double>,
    val warnings: List<String>,
    val exceedsWeeklyBudget: Boolean,
)

object SessionTemplateSuggestionEngine {

    fun suggestWeek(
        split: SplitTemplate,
        templates: List<SessionTemplate> = SESSION_TEMPLATES_SYSTEM,
        exerciseIndex: Map<String, ExerciseMuscleInfo> = emptyMap(),
        prefs: SuggestionPrefs = SuggestionPrefs(),
    ): SuggestedWeekPlan {
        val trainingDays = split.pattern.filterNot { it.equals("Descanso", ignoreCase = true) }
        val targetDifficulty = prefs.preferredDifficulty ?: split.difficulty
        val metrics = TemplateScoreCache(exerciseIndex)

        val chosen = mutableListOf<SuggestedDayPlan>()
        val drains = mutableListOf<PredictedDrain>()
        val warnings = mutableListOf<String>()
        var weeklyVolume = emptyMap<String, Double>()

        trainingDays.forEachIndexed { dayIndex, dayLabel ->
            val allCandidates = SessionTemplateCatalogPolicy.templatesForSplitDay(
                splitId = split.id,
                dayLabel = dayLabel,
                templates = templates,
            )
            val candidates = if (exerciseIndex.isEmpty()) {
                allCandidates
            } else {
                allCandidates.filter { candidate ->
                    SessionTemplateQualityRules.audit(candidate, exerciseIndex).p0.isEmpty()
                }
            }
            val forcedId = prefs.forcedTemplateByDayIndex[dayIndex]
            val forced = forcedId?.let { id ->
                candidates.firstOrNull { it.id == id }
            }

            val preferredMuscle = prefs.preferredFocusMuscleByDayIndex[dayIndex]
            val softCategories = SessionTemplateCatalogPolicy.focusCategoriesForDay(dayLabel)

            data class Scored(val template: SessionTemplate, val score: Double)

            val scored = candidates.map { candidate ->
                Scored(
                    candidate,
                    scoreCandidate(
                        candidate = candidate,
                        dayLabel = dayLabel,
                        targetDifficulty = targetDifficulty,
                        preferredMuscle = preferredMuscle,
                        softCategories = softCategories,
                        chosen = chosen,
                        weeklyVolume = weeklyVolume,
                        drainsSoFar = drains,
                        metrics = metrics,
                    ),
                )
            }.sortedByDescending { it.score }

            val selected = when {
                forced != null -> forced
                else -> scored.firstOrNull()?.template
            }
            if (selected == null) {
                val reason = when {
                    allCandidates.isEmpty() && SessionTemplateCatalogPolicy.isSpecializedSplit(split.id) ->
                        "No existe una plantilla validada del mismo arquetipo especializado."
                    allCandidates.isNotEmpty() && candidates.isEmpty() ->
                        "Las candidatas del día no superan la validación estricta del catálogo V2."
                    else -> "No existe una plantilla compatible con el patrón del día."
                }
                warnings += "Sin plantilla para el día '$dayLabel' del split '${split.name}': $reason"
            }
            val selectedScore = when {
                selected == null -> 0.0
                forced != null -> scored.firstOrNull { it.template.id == selected.id }?.score
                    ?: scoreCandidate(
                        candidate = selected,
                        dayLabel = dayLabel,
                        targetDifficulty = targetDifficulty,
                        preferredMuscle = preferredMuscle,
                        softCategories = softCategories,
                        chosen = chosen,
                        weeklyVolume = weeklyVolume,
                        drainsSoFar = drains,
                        metrics = metrics,
                    )
                else -> scored.first().score
            }

            val alternatives = pickAlternatives(
                ranked = scored.map { it.template },
                selected = selected,
            )

            if (selected != null && exerciseIndex.isNotEmpty()) {
                weeklyVolume = mergeVolume(weeklyVolume, metrics.volume(selected))
                drains += metrics.drain(selected)
            } else if (selected != null) {
                drains += PredictedDrain(0, 0, 0)
            }

            chosen += SuggestedDayPlan(
                dayIndex = dayIndex,
                dayLabel = dayLabel,
                template = selected,
                alternatives = alternatives,
                score = selectedScore,
                unavailabilityReason = if (selected == null) warnings.lastOrNull() else null,
            )
        }

        val weeklyDrain = RingBudgetPolicy.aggregateWeeklyDrain(drains)
        val weeklyCaps = RingBudgetPolicy.weeklyWarningCaps()
        val exceeds = weeklyDrain.cns > weeklyCaps.cns ||
            weeklyDrain.muscular > weeklyCaps.muscular ||
            weeklyDrain.spinal > weeklyCaps.spinal

        if (weeklyDrain.cns > weeklyCaps.cns) {
            warnings += "Fatiga SNC semanal elevada (${weeklyDrain.cns}% > ${weeklyCaps.cns}%)"
        }
        if (weeklyDrain.muscular > weeklyCaps.muscular) {
            warnings += "Fatiga muscular semanal elevada (${weeklyDrain.muscular}% > ${weeklyCaps.muscular}%)"
        }
        if (weeklyDrain.spinal > weeklyCaps.spinal) {
            warnings += "Carga espinal semanal elevada (${weeklyDrain.spinal}% > ${weeklyCaps.spinal}%)"
        }

        return SuggestedWeekPlan(
            days = chosen,
            weeklyDrain = weeklyDrain,
            weeklyVolume = weeklyVolume,
            warnings = warnings,
            exceedsWeeklyBudget = exceeds,
        )
    }

    private fun scoreCandidate(
        candidate: SessionTemplate,
        dayLabel: String,
        targetDifficulty: Difficulty,
        preferredMuscle: String?,
        softCategories: Set<SessionTemplateFocusCategory>,
        chosen: List<SuggestedDayPlan>,
        weeklyVolume: Map<String, Double>,
        drainsSoFar: List<PredictedDrain>,
        metrics: TemplateScoreCache,
    ): Double {
        var score = 0.0

        if (candidate.difficulty == targetDifficulty) score += 100.0

        if (!candidate.primaryFocusMuscle.isNullOrBlank()) score += 15.0

        if (preferredMuscle != null &&
            candidate.primaryFocusMuscle.orEmpty().equals(preferredMuscle, ignoreCase = true)
        ) {
            score += 80.0
        }

        if (candidate.focusCategory != null && candidate.focusCategory in softCategories) {
            score += 50.0
        }

        if (metrics.hasIndex && metrics.p0Count(candidate) == 0) {
            score += 40.0
        }

        val sameLabelEarlier = chosen.filter { it.dayLabel.equals(dayLabel, ignoreCase = true) }
        val sameIdCount = sameLabelEarlier.count { it.template?.id == candidate.id }
        score -= 100.0 * sameIdCount

        val sameFocusMuscleCount = sameLabelEarlier.count { prior ->
            val a = prior.template?.primaryFocusMuscle
            val b = candidate.primaryFocusMuscle
            a != null && b != null && a.equals(b, ignoreCase = true)
        }
        score -= 60.0 * sameFocusMuscleCount

        val sameFocusCategoryCount = sameLabelEarlier.count { prior ->
            prior.template?.focusCategory != null &&
                prior.template.focusCategory == candidate.focusCategory
        }
        score -= 40.0 * sameFocusCategoryCount

        if (metrics.hasIndex) {
            val sessionVol = metrics.volume(candidate)
            val projectedVol = mergeVolume(weeklyVolume, sessionVol)
            score += volumeFitnessContribution(weeklyVolume, projectedVol)

            val drain = metrics.drain(candidate)
            val isPl = SessionTemplateCatalogPolicy.isPowerliftingTemplate(candidate)
            val sessionCaps = RingBudgetPolicy.sessionWarningCaps(isPl)
            if (drain.cns > sessionCaps.cns) score -= (drain.cns - sessionCaps.cns).toDouble()
            if (drain.muscular > sessionCaps.muscular) score -= (drain.muscular - sessionCaps.muscular).toDouble()
            if (drain.spinal > sessionCaps.spinal) score -= (drain.spinal - sessionCaps.spinal).toDouble()

            val projectedWeekly = RingBudgetPolicy.aggregateWeeklyDrain(drainsSoFar + drain)
            val weeklyCaps = RingBudgetPolicy.weeklyWarningCaps()
            val wouldExceed = projectedWeekly.cns > weeklyCaps.cns ||
                projectedWeekly.muscular > weeklyCaps.muscular ||
                projectedWeekly.spinal > weeklyCaps.spinal
            val nearBudget = projectedWeekly.cns >= (weeklyCaps.cns * 0.85).toInt() ||
                projectedWeekly.muscular >= (weeklyCaps.muscular * 0.85).toInt() ||
                projectedWeekly.spinal >= (weeklyCaps.spinal * 0.85).toInt()

            if (wouldExceed) score -= 200.0
            if (wouldExceed || nearBudget) {
                if (candidate.durationClass == SessionTemplateDurationClass.SHORT ||
                    candidate.id.endsWith("-low") ||
                    candidate.weeklyVolumePolicyId == "high_freq_low"
                ) {
                    score += 30.0
                }
            }
        }

        if (candidate.weeklyVolumePolicyId == "beginner_machine" &&
            targetDifficulty == Difficulty.PRINCIPIANTE
        ) {
            score += 10.0
        }

        // Tiny tie-breaker: prefer lower sortOrder for stable results
        score -= candidate.sortOrder * 0.001

        return score
    }

    private fun pickAlternatives(
        ranked: List<SessionTemplate>,
        selected: SessionTemplate?,
    ): List<SessionTemplate> {
        if (selected == null) return emptyList()
        val rest = ranked.filter { it.id != selected.id }
        val byDifferentMuscle = rest.filter {
            val a = it.primaryFocusMuscle
            val b = selected.primaryFocusMuscle
            a == null || b == null || !a.equals(b, ignoreCase = true)
        }
        val preferred = if (byDifferentMuscle.size >= 2) byDifferentMuscle else rest
        return preferred.take(2)
    }

    private fun mergeVolume(
        base: Map<String, Double>,
        add: Map<String, Double>,
    ): Map<String, Double> {
        if (add.isEmpty()) return base
        if (base.isEmpty()) return add
        val out = base.toMutableMap()
        add.forEach { (muscle, vol) -> out[muscle] = (out[muscle] ?: 0.0) + vol }
        return out
    }

    private fun volumeFitnessContribution(
        before: Map<String, Double>,
        after: Map<String, Double>,
    ): Double {
        var total = 0.0
        for ((muscle, range) in SessionTemplateCatalogPolicy.WEEKLY_VOLUME_RANGES) {
            val mid = (range.start + range.endInclusive) / 2.0
            val beforeV = before[muscle] ?: 0.0
            val afterV = after[muscle] ?: 0.0
            if (afterV == beforeV) continue
            val beforeDist = abs(beforeV - mid)
            val afterDist = abs(afterV - mid)
            val improvement = ((beforeDist - afterDist) / mid.coerceAtLeast(1.0)) * 20.0
            total += improvement
        }
        return total.coerceIn(-20.0, 20.0)
    }

    private class TemplateScoreCache(
        private val exerciseIndex: Map<String, ExerciseMuscleInfo>,
    ) {
        val hasIndex: Boolean = exerciseIndex.isNotEmpty()
        private val drainCache = mutableMapOf<String, PredictedDrain>()
        private val volumeCache = mutableMapOf<String, Map<String, Double>>()
        private val p0Cache = mutableMapOf<String, Int>()

        fun drain(template: SessionTemplate): PredictedDrain =
            drainCache.getOrPut(template.id) {
                SessionTemplateCatalogPolicy.evaluateTemplateRings(template, exerciseIndex)
            }

        fun volume(template: SessionTemplate): Map<String, Double> =
            volumeCache.getOrPut(template.id) {
                SessionTemplateCatalogPolicy.calculateSessionMuscleVolume(template.session, exerciseIndex)
            }

        fun p0Count(template: SessionTemplate): Int =
            p0Cache.getOrPut(template.id) {
                SessionTemplateQualityRules.audit(template, exerciseIndex).p0.size
            }
    }
}
