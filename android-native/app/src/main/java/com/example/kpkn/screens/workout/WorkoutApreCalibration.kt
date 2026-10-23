package com.example.kpkn.screens.workout

import com.example.kpkn.data.models.*
import com.example.kpkn.data.protocols.ProgressionRule
import com.example.kpkn.domain.calibration.*
import com.example.kpkn.domain.training.Apre
import com.example.kpkn.domain.training.LiveApreBlock
import com.example.kpkn.domain.training.resolveLiveApreBlock
import java.util.UUID

internal fun liveApreBlock(state: WorkoutUiState, exercise: Exercise): LiveApreBlock? = resolveLiveApreBlock(
    exercise, state.livePlanContext?.sourceProtocolId,
    state.livePlanContext?.progression is ProgressionRule.ApreXrm,
)

/** S3 propone exclusivamente su S4. La tabla usa reps completas, ya separadas de las parciales por CAL1. */
internal fun buildRecordedApreCalibration(
    state: WorkoutUiState, exercise: Exercise, index: Int, completed: CompletedSet, now: Long,
    inventory: EquipmentInventory? = null,
): CalibrationDecisionRecord? {
    val block = liveApreBlock(state, exercise) ?: return null
    if (index != block.thirdIndex || completed.skipped || completed.isWarmup ||
        completed.performedPurpose() != SetPurpose.AMRAP ||
        completed.amrapMinimumReps?.let { it != block.variant.xrmReps } == true) return null
    val context = calibrationContextForSet(state, exercise, index, completed.side, completed)
    val sourceLoad = completed.originalLoad ?: completed.recordedPayloadV3?.externalLoad
        ?: completed.weight.takeUnless { context.loadMode == LoadModeV2.LASTRE } ?: 0.0
    val increment = calibrationIncrement(exercise, exercise.sets[block.fourthIndex], inventory)
    // CompletedSet.reps es CAL1.fullReps. Restar partialReps aquí descontaría dos veces la técnica inválida.
    val valid = Apre.validReps(completed.reps)
    val eligibility = when {
        context.loadMode !in setOf(LoadModeV2.LOAD, LoadModeV2.LASTRE) -> CalibrationEligibility.BODYWEIGHT
        !sourceLoad.isFinite() || sourceLoad <= 0.0 -> CalibrationEligibility.NO_EXTERNAL_LOAD
        else -> CalibrationEligibility.OK
    }
    val targetKg = if (eligibility == CalibrationEligibility.OK)
        Apre.set4LoadKg(sourceLoad, valid, block.variant, increment) else 0.0
    val targetId = calibrationSetId(exercise.sets[block.fourthIndex], completed.side)
    val pending = pendingCalibrationSets(state, exercise, index, context).filter { it.setId == targetId }
    val target = pending.singleOrNull()
    val compatible = target != null && target.context == context && !target.isCompleted && !target.isManuallyFixed
    val canApply = eligibility == CalibrationEligibility.OK && compatible
    val reason = when {
        eligibility == CalibrationEligibility.BODYWEIGHT -> "APRE se registra; corporal y asistida no se ajustan en kg."
        eligibility == CalibrationEligibility.NO_EXTERNAL_LOAD -> "APRE se registra; falta una carga externa válida."
        target?.isCompleted == true -> "S4 ya está hecha: se conserva su carga registrada."
        target?.isManuallyFixed == true -> "S4 tiene una carga fijada: se conserva."
        !compatible -> "S4 cambió de contexto o de identidad: se conserva."
        else -> "${block.variant.label} · $valid reps válidas: S4 según la tabla de Mann."
    }
    val changes = target?.takeIf { it.context == context && eligibility == CalibrationEligibility.OK }
        ?.let { listOf(CalibrationLoadChange(it.setId, it.baseLoadKg, targetKg)) }.orEmpty()
    val type = when {
        !canApply -> CalibrationDecisionType.NOT_APPLIED
        targetKg > target!!.baseLoadKg -> CalibrationDecisionType.RAISE
        targetKg < target.baseLoadKg -> CalibrationDecisionType.LOWER
        else -> CalibrationDecisionType.HOLD
    }
    val decision = CalibrationDecision(
        sourceSetId = completed.id, exerciseId = exercise.id, context = context,
        capacityKg = null, referenceKg = null, referenceSource = CalibrationReferenceSource.NONE,
        ratio = null, factor = 1.0, type = type, affectedSets = changes,
        eligibility = eligibility, reason = reason, note = null, scope = CalibrationScope.SESSION,
        effectiveRepsToFailure = valid,
    )
    return CalibrationDecisionRecord(
        id = UUID.randomUUID().toString(), sourceSetId = completed.id,
        sessionLogId = calibrationExecutionId(state), programId = state.programId.takeIf { it.isNotBlank() },
        exerciseName = exercise.name, decision = decision, createdAt = now, loadIncrementKg = increment,
        pendingPrescriptions = pending.filter { it.context == context }.associate {
            it.setId to CalibrationPendingPrescription(it.baseLoadKg, it.targetReps, it.targetRir)
        },
        apre = ApreCalibrationDetails(block.variant.xrmReps, exercise.sets[index].id,
            exercise.sets[block.fourthIndex].id, valid, sourceLoad, targetKg),
    )
}

/** Snapshot de S4 guardado junto con CompletedSet antes de confirmar la escritura de la serie. */
internal fun buildRecordedApreXrm(
    state: WorkoutUiState, exercise: Exercise, index: Int, completed: CompletedSet, now: Long,
    inventory: EquipmentInventory? = null,
): ApreXrmCapture? = com.example.kpkn.domain.training.captureApreXrm(
    exercise, index, completed, state.livePlanContext?.sourceProtocolId,
    state.livePlanContext?.progression is ProgressionRule.ApreXrm,
    calibrationIncrement(exercise, exercise.sets[index], inventory), now,
    state.programId.takeIf { it.isNotBlank() }, calibrationContextForSet(state, exercise, index, completed.side, completed),
)
