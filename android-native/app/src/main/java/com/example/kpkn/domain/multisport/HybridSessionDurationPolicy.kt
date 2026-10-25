package com.example.kpkn.domain.multisport

import com.example.kpkn.data.models.Session
import com.example.kpkn.data.models.SessionPart
import com.example.kpkn.data.models.isCardioPart
import com.example.kpkn.domain.training.SessionDurationEstimator
import com.example.kpkn.domain.multisport.tracking.*

/** How the session length used by placement was obtained. */
enum class HybridSessionDurationProvenance { ESTIMATE, BUDGET, DECLARED_SPORT_BLOCKS, ESTIMATE_PLUS_DECLARED_BLOCKS }

/** The author of a placement budget, kept separate from the measured estimate. */
enum class HybridSessionDurationBudgetSource { BINDING, SESSION_TARGET }

data class HybridSessionDurationResolution(
    /** Minutes reserved in the placement window. */
    val placementMinutes: Int,
    val provenance: HybridSessionDurationProvenance,
    /** Estimate from the materialized base session, independent of targetDurationMinutes. */
    val estimatedMinutes: Int?,
    val budgetMinutes: Int? = null,
    val budgetSource: HybridSessionDurationBudgetSource? = null,
    /** Separately labelled authored time; never presented as a measurement or a native estimate. */
    val declaredSportSeconds: Long? = null,
    /** Content estimate/authored block time, excluding any spare placement budget. */
    val trainingMinutes: Int = estimatedMinutes ?: placementMinutes,
)

data class HybridSessionDurationPlan(
    val sessionId: String,
    val resolution: HybridSessionDurationResolution,
)

sealed interface HybridSessionDurationPolicyError {
    data class NoExecutableContent(
        val budgetMinutes: Int?,
        val budgetSource: HybridSessionDurationBudgetSource?,
    ) : HybridSessionDurationPolicyError
    data class InvalidBudget(
        val minutes: Int,
        val source: HybridSessionDurationBudgetSource,
    ) : HybridSessionDurationPolicyError
    data class BudgetTooShort(
        val estimatedMinutes: Int,
        val budgetMinutes: Int,
        val source: HybridSessionDurationBudgetSource,
    ) : HybridSessionDurationPolicyError
    data object EstimateUnavailable : HybridSessionDurationPolicyError
    data class InvalidSportDocuments(val issues: List<SportDocumentCollectionIssue>) : HybridSessionDurationPolicyError
    data object AmbiguousSportDuration : HybridSessionDurationPolicyError
    data class SportDurationUnavailable(val documentId: String) : HybridSessionDurationPolicyError
    data object SportDurationOutOfRange : HybridSessionDurationPolicyError
    data class DeclaredSessionShorterThanNativeContent(val declaredSeconds: Long, val estimatedMinutes: Int) : HybridSessionDurationPolicyError
    data class SportBudgetTooShort(val requiredMinutes: Int, val budgetMinutes: Int, val source: HybridSessionDurationBudgetSource) : HybridSessionDurationPolicyError
}

sealed interface HybridSessionDurationPolicyResult {
    data class Ready(val resolution: HybridSessionDurationResolution) : HybridSessionDurationPolicyResult
    data class Invalid(val error: HybridSessionDurationPolicyError) : HybridSessionDurationPolicyResult
}

/**
 * Resolves a conservative placement duration without changing the authored session.
 * A binding budget has priority over the legacy session target. The estimator supplies the
 * content duration estimate; a budget is only used when it can contain that estimate.
 */
object HybridSessionDurationPolicy {
    fun resolve(
        session: Session,
        bindingBudgetMinutes: Int?,
        registry: SportTrackingRegistry = NativeSportTrackingEngines.registry,
    ): HybridSessionDurationPolicyResult {
        val (budget, budgetSource) = when {
            bindingBudgetMinutes != null -> bindingBudgetMinutes to HybridSessionDurationBudgetSource.BINDING
            session.targetDurationMinutes != null ->
                session.targetDurationMinutes to HybridSessionDurationBudgetSource.SESSION_TARGET
            else -> null to null
        }

        if (budget != null && budget <= 0) {
            return HybridSessionDurationPolicyResult.Invalid(
                HybridSessionDurationPolicyError.InvalidBudget(budget, checkNotNull(budgetSource)),
            )
        }
        if (session.sportDocuments.isNotEmpty()) {
            val issues = registry.validateAll(session.sportDocuments, setOf(SportDocumentPurpose.PLAN))
            if (issues.isNotEmpty()) return HybridSessionDurationPolicyResult.Invalid(
                HybridSessionDurationPolicyError.InvalidSportDocuments(issues),
            )
            if (session.sportDocuments.any { it.durationContribution != SportDurationContribution.NONE.name }) {
                return resolveSportBlocks(session, budget, budgetSource, registry)
            }
        }
        if (!session.hasExecutableTrainingContent()) {
            return HybridSessionDurationPolicyResult.Invalid(
                HybridSessionDurationPolicyError.NoExecutableContent(budget, budgetSource),
            )
        }

        // A distance-only cardio prescription has content but no duration. The estimator's
        // per-exercise setup floor would otherwise make it look like a one-minute session.
        if (session.allExercises().any { exercise ->
                val cardio = exercise.cardioDetails ?: return@any false
                cardio.effectiveDurationSeconds() <= 0
            }
        ) {
            return HybridSessionDurationPolicyResult.Invalid(
                HybridSessionDurationPolicyError.EstimateUnavailable,
            )
        }

        val estimate = runCatching { SessionDurationEstimator.estimate(session) }.getOrNull()
            ?.takeIf { it.totalSeconds > 0 }
            ?: return HybridSessionDurationPolicyResult.Invalid(
                HybridSessionDurationPolicyError.EstimateUnavailable,
            )
        val estimatedMinutes = estimate.totalMinutes

        if (budget != null) {
            if (estimatedMinutes > budget) {
                return HybridSessionDurationPolicyResult.Invalid(
                    HybridSessionDurationPolicyError.BudgetTooShort(
                        estimatedMinutes = estimatedMinutes,
                        budgetMinutes = budget,
                        source = checkNotNull(budgetSource),
                    ),
                )
            }
            return HybridSessionDurationPolicyResult.Ready(
                HybridSessionDurationResolution(
                    placementMinutes = budget,
                    provenance = HybridSessionDurationProvenance.BUDGET,
                    estimatedMinutes = estimatedMinutes,
                    budgetMinutes = budget,
                    budgetSource = budgetSource,
                ),
            )
        }

        return HybridSessionDurationPolicyResult.Ready(
            HybridSessionDurationResolution(
                placementMinutes = estimatedMinutes,
                provenance = HybridSessionDurationProvenance.ESTIMATE,
                estimatedMinutes = estimatedMinutes,
            ),
        )
    }

    private fun resolveSportBlocks(
        session: Session,
        budget: Int?,
        budgetSource: HybridSessionDurationBudgetSource?,
        registry: SportTrackingRegistry,
    ): HybridSessionDurationPolicyResult {
        fun invalid(error: HybridSessionDurationPolicyError) = HybridSessionDurationPolicyResult.Invalid(error)
        val documents = session.sportDocuments.filter { it.durationContribution != SportDurationContribution.NONE.name }
        val totals = documents.filter { it.durationContribution == SportDurationContribution.SESSION_TOTAL.name }
        if (totals.size > 1 || (totals.isNotEmpty() && documents.size > 1)) {
            return invalid(HybridSessionDurationPolicyError.AmbiguousSportDuration)
        }
        val times = documents.map { document ->
            registry.plannedDurationSeconds(document)
                ?: return invalid(HybridSessionDurationPolicyError.SportDurationUnavailable(document.id))
        }
        val declaredSeconds = try { times.fold(0L) { sum, next -> Math.addExact(sum, next) } }
        catch (_: ArithmeticException) { return invalid(HybridSessionDurationPolicyError.SportDurationOutOfRange) }
        val native = if (session.hasExecutableTrainingContent()) {
            when (val estimate = resolve(session.copy(sportDocuments = emptyList(), targetDurationMinutes = null), null, registry)) {
                is HybridSessionDurationPolicyResult.Invalid -> return estimate
                is HybridSessionDurationPolicyResult.Ready -> estimate.resolution.estimatedMinutes
            }
        } else null
        if (totals.isNotEmpty() && native != null && declaredSeconds < native.toLong() * 60L) {
            return invalid(HybridSessionDurationPolicyError.DeclaredSessionShorterThanNativeContent(declaredSeconds, native))
        }
        val totalSeconds = try {
            if (totals.isNotEmpty()) declaredSeconds else Math.addExact(declaredSeconds, (native ?: 0).toLong() * 60L)
        } catch (_: ArithmeticException) { return invalid(HybridSessionDurationPolicyError.SportDurationOutOfRange) }
        if (totalSeconds !in 1..86400L) return invalid(HybridSessionDurationPolicyError.SportDurationOutOfRange)
        val requiredMinutes = ((totalSeconds + 59) / 60).toInt()
        if (budget != null && budget < requiredMinutes) return invalid(
            HybridSessionDurationPolicyError.SportBudgetTooShort(requiredMinutes, budget, requireNotNull(budgetSource)),
        )
        return HybridSessionDurationPolicyResult.Ready(HybridSessionDurationResolution(
            placementMinutes = budget ?: requiredMinutes,
            provenance = if (budget != null) HybridSessionDurationProvenance.BUDGET
                else if (native != null && totals.isEmpty()) HybridSessionDurationProvenance.ESTIMATE_PLUS_DECLARED_BLOCKS
                else HybridSessionDurationProvenance.DECLARED_SPORT_BLOCKS,
            estimatedMinutes = native,
            budgetMinutes = budget,
            budgetSource = budgetSource,
            declaredSportSeconds = declaredSeconds,
            trainingMinutes = requiredMinutes,
        ))
    }

    private fun Session.hasExecutableTrainingContent(): Boolean {
        val exercises = allExercises().distinctBy { it.id }
        val hasResistanceDose = exercises.any { exercise ->
            exercise.cardioDetails == null && exercise.sets.any { set -> !set.isEmptySlot }
        }
        val hasCardioPrescription = exercises.any { it.cardioDetails != null }
        val hasExerciseMobility = exercises.any { it.mobilitySeries.isNotEmpty() }
        val hasPartMobility = parts.any { it.hasExecutableMobility() }
        val hasTimedEmptyCardioPart = parts.any { part ->
            part.isCardioPart() && part.exercises.none { it.cardioDetails != null } &&
                (part.targetDurationMinutes ?: 0) > 0
        }
        return hasResistanceDose || hasCardioPrescription || hasExerciseMobility ||
            hasPartMobility || hasTimedEmptyCardioPart
    }

    private fun SessionPart.hasExecutableMobility(): Boolean =
        mobilitySeries.isNotEmpty() || (isMobilityGroup && (mobilityConfig?.totalMinutes ?: 0) > 0)
}
