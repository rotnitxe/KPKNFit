package com.example.kpkn.domain.workout

import com.example.kpkn.data.models.CompletedSet
import com.example.kpkn.data.models.LoadModeV2
import com.example.kpkn.domain.calculations.calculateHybrid1RM
import kotlin.math.abs
import kotlin.math.roundToInt

/**
 * Pure load-suggestion helpers for live workout sessions.
 * Android/Room lookups stay in the ViewModel adapter.
 */
object LoadSuggestionEngine {

    data class Suggestion(
        val suggestedWeight: Double,
        val reason: String,
        val suggestedLoadMode: LoadModeV2? = null,
    )

    fun roundLoad(weight: Double): Double {
        if (weight <= 0.0) return 0.0
        return ((weight / 0.5).roundToInt() * 0.5).coerceAtLeast(0.5)
    }

    fun fatigueFactorForPriorCompletedSets(priorCompletedCount: Int): Double =
        when (priorCompletedCount.coerceAtLeast(0)) {
            0 -> 1.0
            1 -> 0.8
            2 -> 0.6
            else -> 0.5
        }

    fun tagMultiplier(tag: String?): Double {
        if (tag.isNullOrBlank()) return 1.0
        return when (tag.trim().lowercase()) {
            "base" -> 1.0
            "top set" -> 1.08
            "pr" -> 1.15
            "pesado" -> 1.05
            "back-off" -> 0.90
            "tecnica", "control" -> 0.85
            "volumen" -> 0.90
            "ligero", "pump" -> 0.80
            "máquina" -> 1.10
            "sentado" -> 0.95
            "de pie" -> 1.00
            "cable" -> 0.90
            "unilateral" -> 0.85
            "inclinado" -> 0.85
            "declinado" -> 1.05
            else -> 1.0
        }
    }

    fun applyAssistedAdjustment(baseAssistance: Double, factor: Double): Double {
        val clampedFactor = factor.coerceIn(0.60, 1.50)
        val adjusted = if (clampedFactor > 0.0) baseAssistance / clampedFactor else baseAssistance
        return adjusted.coerceIn(0.5, baseAssistance * 1.50)
    }

    fun inputLoad(set: CompletedSet, loadMode: LoadModeV2): Double {
        val payload = set.recordedPayloadV3
        return when (loadMode) {
            LoadModeV2.ASSISTED -> payload?.assistedLoad ?: set.weight
            else -> payload?.externalLoad ?: set.weight
        }
    }

    fun estimatedCapacity(set: CompletedSet): Double? {
        set.homologatedResultV3?.estimatedRm?.takeIf { it > 0.0 }?.let { return it }
        return if (set.weight > 0.0 && set.reps in 1..36) {
            calculateHybrid1RM(set.weight, set.reps)
        } else {
            null
        }
    }

    /**
     * History → suggestion from the last working set (homologated next-load or progression rules).
     */
    fun suggestFromLastWorkingSet(
        lastSet: CompletedSet,
        targetReps: Int,
        loadMode: LoadModeV2,
        activeTag: String?,
        baseEntryTag: String?,
        techniqueSignal: Int,
    ): Suggestion? {
        if (lastSet.weight <= 0.0 && inputLoad(lastSet, loadMode) <= 0.0) return null

        val scale = if (activeTag != baseEntryTag) {
            tagMultiplier(activeTag) / tagMultiplier(baseEntryTag)
        } else {
            1.0
        }

        fun adjustWithTechnique(baseLoad: Double): Double {
            if (techniqueSignal == 0) return baseLoad
            val factor = when {
                techniqueSignal > 0 -> 1.01
                else -> 0.99
            }
            return if (loadMode == LoadModeV2.ASSISTED) {
                applyAssistedAdjustment(baseLoad, factor)
            } else {
                (baseLoad * factor).coerceAtLeast(0.0)
            }
        }

        fun appendTechniqueReason(baseReason: String): String = when {
            techniqueSignal > 0 -> "$baseReason · Técnica en mejora"
            techniqueSignal < 0 -> "$baseReason · Técnica inestable"
            else -> baseReason
        }

        fun percentText(): String {
            if (scale == 1.0) return ""
            val percent = ((scale - 1.0) * 100).roundToInt()
            val sign = if (percent >= 0) "+" else ""
            return " ($sign$percent% por $activeTag)"
        }

        val homologatedSuggestion = lastSet.homologatedResultV3?.suggestedNextLoad
        if (homologatedSuggestion != null) {
            val scaledSug = homologatedSuggestion * scale
            val adjusted = (adjustWithTechnique(scaledSug) * 2).toLong() / 2.0
            val baseReason = lastSet.homologatedResultV3?.suggestionReason
                ?: if (activeTag != null && baseEntryTag == activeTag) {
                    "Historial contextual"
                } else {
                    "Historial homologado"
                }
            val finalReason = if (scale != 1.0) {
                "Homologado de ${baseEntryTag ?: "Base"}${percentText()}"
            } else {
                baseReason
            }
            return Suggestion(
                suggestedWeight = adjusted,
                reason = appendTechniqueReason(finalReason),
                suggestedLoadMode = lastSet.homologatedResultV3?.suggestedLoadMode,
            )
        }

        val lastSetWeight = inputLoad(lastSet, loadMode)
        if (lastSetWeight <= 0.0) return null
        val suggestedWeight = when {
            lastSet.isFailedSet || lastSet.isFailure -> lastSetWeight * 0.95
            lastSet.reps > 0 && targetReps <= lastSet.reps -> lastSetWeight * 1.025
            else -> lastSetWeight
        }
        val rounded = (adjustWithTechnique(suggestedWeight * scale) * 2).toLong() / 2.0
        val reason = if (activeTag != null && baseEntryTag == activeTag) {
            "Última sesión · $activeTag"
        } else if (scale != 1.0) {
            "Homologado de ${baseEntryTag ?: "Base"}${percentText()}"
        } else {
            "Última sesión"
        }
        return Suggestion(
            suggestedWeight = rounded,
            reason = appendTechniqueReason(reason),
        )
    }

    data class ContextualLoadResult(
        val suggestedWeight: Double,
        val originalWeightRounded: Double,
        val isRecalculated: Boolean,
        val reason: String,
    )

    /**
     * Pure core of session-contextual load suggestion (fatigue / eRM / floor clamps).
     * Callers supply already-resolved [baseWeight] / ratios; no Android/DB deps.
     */
    fun computeContextualWorkingLoad(
        baseWeight: Double,
        originalWeight: Double,
        fatigueFactor: Double,
        improvementFactor: Double,
        shouldRespectPlan: Boolean,
        hasManualOverride: Boolean,
        bestRatio: Double?,
        worstRatio: Double?,
        severePerformanceDrop: Boolean,
        isAssisted: Boolean,
        baseReason: String,
    ): ContextualLoadResult {
        var computedWeight = baseWeight * fatigueFactor
        if (!hasManualOverride) {
            computedWeight *= if (shouldRespectPlan) {
                improvementFactor.coerceIn(0.95, 1.05)
            } else {
                improvementFactor
            }
        }

        var reason = baseReason
        if (bestRatio != null && bestRatio >= 1.025) {
            val capped = (originalWeight * 1.05).coerceAtLeast(originalWeight)
            computedWeight = minOf(computedWeight, capped)
            if (computedWeight < baseWeight * fatigueFactor) {
                computedWeight = minOf(baseWeight * fatigueFactor, capped)
            }
            reason += " · eRM +${((bestRatio - 1.0) * 100).roundToInt()}%"
        }
        if (worstRatio != null && worstRatio <= 0.97) {
            computedWeight *= 0.97
            reason += " · Fatiga detectada"
        }

        if (!severePerformanceDrop) {
            computedWeight = maxOf(computedWeight, baseWeight * 0.80)
        } else {
            computedWeight = maxOf(computedWeight, baseWeight * 0.70)
            reason += " · Rendimiento muy bajo"
        }

        val finalWeight = if (isAssisted) {
            val adjustmentRatio = if (baseWeight > 0.0) {
                (computedWeight / baseWeight).coerceIn(0.60, 1.50)
            } else {
                1.0
            }
            roundLoad(applyAssistedAdjustment(baseWeight, adjustmentRatio)).coerceAtLeast(0.5)
        } else {
            roundLoad(computedWeight)
        }
        val originalRounded = roundLoad(originalWeight)
        return ContextualLoadResult(
            suggestedWeight = finalWeight,
            originalWeightRounded = originalRounded,
            isRecalculated = abs(finalWeight - originalWeight) >= 0.25,
            reason = reason,
        )
    }
}
