package com.example.kpkn.domain.calibration

import com.example.kpkn.data.models.CalibrationScope
import com.example.kpkn.data.models.LoadModeV2
import com.example.kpkn.domain.calculations.calculateHybrid1RM
import com.example.kpkn.domain.calculations.calculateWeightFrom1RM
import com.example.kpkn.domain.workout.LoadSuggestionEngine

/** No lookups or side effects: every reference, timestamp, base load and material is supplied. */
object CalibrationEngine {
    fun eligibility(
        source: RecordedCalibrationSet,
        policy: CalibrationPolicySnapshot = CalibrationPolicy.defaults,
    ): CalibrationEligibility = when {
        source.fullReps < 0 || source.partialReps < 0 -> CalibrationEligibility.REPS_OUT_OF_RANGE
        source.context.loadMode == LoadModeV2.BODYWEIGHT -> CalibrationEligibility.BODYWEIGHT
        source.context.loadMode == LoadModeV2.ASSISTED ||
            !source.externalLoadKg.isFinite() || source.externalLoadKg <= 0.0 -> CalibrationEligibility.NO_EXTERNAL_LOAD
        source.fullReps == 0 && source.partialReps > 0 -> CalibrationEligibility.PARTIALS_ONLY
        source.reachedFailure -> CalibrationEligibility.OK
        source.declaredRir == null -> CalibrationEligibility.NO_EFFORT
        source.declaredRir > policy.maximumRir -> CalibrationEligibility.RIR_TOO_HIGH
        source.declaredRir < policy.minimumRir -> CalibrationEligibility.REPS_OUT_OF_RANGE
        else -> CalibrationEligibility.OK
    }

    fun calibrate(
        source: RecordedCalibrationSet,
        pendingSets: List<PendingCalibrationSet>,
        references: CalibrationReferenceCandidates,
        nowEpochMillis: Long,
        scope: CalibrationScope = CalibrationScope.SESSION,
        policy: CalibrationPolicySnapshot = CalibrationPolicy.defaults,
        equipment: CalibrationEquipment = CalibrationEquipment(policy.defaultLoadIncrementKg),
    ): CalibrationDecision {
        val eligibility = eligibility(source, policy)
        val reference = ReferenceResolver.resolve(source.context, references, nowEpochMillis, policy)
        fun decision(
            capacity: Double? = null,
            effectiveReps: Int? = null,
            ratio: Double? = null,
            factor: Double = 1.0,
            type: CalibrationDecisionType,
            affected: List<CalibrationLoadChange> = emptyList(),
            reason: String,
            note: String? = null,
        ) = CalibrationDecision(
            sourceSetId = source.setId, exerciseId = source.exerciseId, context = source.context,
            capacityKg = capacity, referenceKg = reference.capacityKg, referenceSource = reference.source,
            ratio = ratio, factor = factor, type = type, affectedSets = affected, eligibility = eligibility,
            reason = reason, note = note, scope = scope, effectiveRepsToFailure = effectiveReps, policy = policy,
        )
        if (eligibility != CalibrationEligibility.OK) return decision(
            type = CalibrationDecisionType.NOT_APPLIED, reason = ineligibilityReason(eligibility),
        )

        val compatible = if (scope == CalibrationScope.SESSION) pendingSets.filter {
            it.exerciseId == source.exerciseId && it.context == source.context && it.setId != source.setId &&
                !it.isCompleted && !it.isManuallyFixed && it.baseLoadKg.isFinite() && it.baseLoadKg >= 0.0
        } else emptyList()
        fun changes(factor: Double) = compatible.map { set ->
            CalibrationLoadChange(
                set.setId, set.baseLoadKg,
                // HOLD must not move an off-step prescription just to round it.
                if (factor == 1.0) set.baseLoadKg else LoadSuggestionEngine.roundLoad(set.baseLoadKg * factor, equipment.incrementKg),
            )
        }

        if (source.fullReps < policy.minimumFullReps) {
            if (scope == CalibrationScope.NEXT_SESSIONS) return decision(
                type = CalibrationDecisionType.NOT_APPLIED,
                reason = "No hay suficientes repeticiones completas para proponer una referencia futura.",
            )
            val affected = changes(policy.lowRepsFactor)
            val changed = affected.any { it.beforeKg != it.afterKg }
            return decision(
                factor = policy.lowRepsFactor,
                type = if (changed) CalibrationDecisionType.LOW_REPS_DROP else CalibrationDecisionType.HOLD,
                affected = affected,
                reason = when {
                    affected.isEmpty() -> "No quedan series compatibles para ajustar."
                    !changed -> "Mantener: el redondeo al material deja las cargas iguales."
                    else -> "Pocas repeticiones completas: bajada fija sin estimar capacidad."
                },
            )
        }

        // Cap the observed full reps BEFORE adding the declared reserve. Thus 10 @RIR1 = 11 to failure.
        val effectiveReps = source.fullReps.coerceAtMost(policy.maximumFullReps) +
            if (source.reachedFailure) 0 else source.declaredRir!!
        val capacity = calculateHybrid1RM(source.externalLoadKg, effectiveReps)
        val ratio = reference.capacityKg?.let { capacity / it }
        val factor = sessionCapacityAdjustmentFactor(capacity, reference.capacityKg, policy)
        val note = if (source.fullReps > policy.maximumFullReps) {
            "Para calibrar mejor, usa más carga la próxima vez."
        } else null

        val affected = if (reference.capacityKg == null) compatible.mapNotNull { set ->
            val reps = set.targetReps?.takeIf { it > 0 } ?: return@mapNotNull null
            if (set.targetRir < 0) return@mapNotNull null
            val repsToFailure = (reps.toLong() + set.targetRir).coerceAtMost(Int.MAX_VALUE.toLong()).toInt()
            val load = calculateWeightFrom1RM(capacity, repsToFailure)
            CalibrationLoadChange(set.setId, set.baseLoadKg, LoadSuggestionEngine.roundLoad(load, equipment.incrementKg))
        } else changes(factor)
        val type = when {
            scope == CalibrationScope.SESSION && affected.isNotEmpty() &&
                affected.none { it.beforeKg != it.afterKg } -> CalibrationDecisionType.HOLD
            reference.capacityKg == null -> CalibrationDecisionType.FIRST_REFERENCE
            scope == CalibrationScope.SESSION && affected.isEmpty() -> CalibrationDecisionType.HOLD
            factor > 1.0 -> CalibrationDecisionType.RAISE
            factor < 1.0 -> CalibrationDecisionType.LOWER
            else -> CalibrationDecisionType.HOLD
        }
        val reason = when {
            scope == CalibrationScope.NEXT_SESSIONS -> "Capacidad propuesta como referencia para próximas sesiones de este ejercicio; hoy se conservan las cargas."
            type == CalibrationDecisionType.FIRST_REFERENCE -> "Primera referencia: cargas calculadas desde la capacidad, las repeticiones y la reserva objetivo."
            reference.capacityKg == null -> "Primera referencia: mantener, el redondeo al material deja las cargas iguales."
            affected.isEmpty() -> "No quedan series compatibles para ajustar."
            type == CalibrationDecisionType.HOLD && factor != 1.0 -> "Mantener: el redondeo al material deja las cargas iguales."
            type == CalibrationDecisionType.HOLD -> "Mantener: la capacidad está en la zona muerta de la referencia."
            type == CalibrationDecisionType.RAISE -> raiseReason(capped = ratio != null && ratio > policy.maximumFactor, policy)
            else -> lowerReason(capped = ratio != null && ratio < policy.minimumFactor, policy)
        }
        return decision(capacity, effectiveReps, ratio, factor, type, affected, reason, note)
    }

    /** Subida: dice el tope solo si el factor quedó recortado; los topes salen de la política, no de números a mano. */
    internal fun raiseReason(capped: Boolean, policy: CalibrationPolicySnapshot): String = if (capped) {
        "La capacidad supera la referencia. La subida se limita al ${percentOf(policy.maximumFactor - 1.0)} % y se redondea al material."
    } else {
        "La capacidad supera la referencia. Las cargas suben en la misma proporción, redondeadas al material."
    }

    /** Bajada: igual que [raiseReason], con el tope inferior. */
    internal fun lowerReason(capped: Boolean, policy: CalibrationPolicySnapshot): String = if (capped) {
        "La capacidad no alcanza la referencia. La bajada se limita al ${percentOf(1.0 - policy.minimumFactor)} % y se redondea al material."
    } else {
        "La capacidad no alcanza la referencia. Las cargas bajan en la misma proporción, redondeadas al material."
    }

    private fun percentOf(fraction: Double): Int = Math.round(fraction * 100.0).toInt()

    private fun ineligibilityReason(eligibility: CalibrationEligibility): String = when (eligibility) {
        CalibrationEligibility.NO_EFFORT -> "Falta declarar la reserva o el fallo."
        CalibrationEligibility.RIR_TOO_HIGH -> "La reserva declarada supera el máximo permitido para calibrar."
        CalibrationEligibility.BODYWEIGHT -> "El peso corporal se registra, pero no se calibra en kg."
        CalibrationEligibility.NO_EXTERNAL_LOAD -> "Se necesita carga externa o lastre positivo; la asistencia no se calibra en kg."
        CalibrationEligibility.PARTIALS_ONLY -> "Solo hay repeticiones parciales; se necesitan repeticiones completas válidas."
        CalibrationEligibility.REPS_OUT_OF_RANGE -> "Las repeticiones o la reserva declarada no son válidas."
        CalibrationEligibility.OK -> error("Eligible sets do not have an ineligibility reason")
    }
}
