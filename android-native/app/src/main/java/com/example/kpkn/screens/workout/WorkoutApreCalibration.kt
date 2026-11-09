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

internal data class ApreApplicationContract(
    val canApply: Boolean,
    val reviewReason: String? = null,
)

/** Shared presentation/commit contract: the exact S3→S4 proposal must still match the live block and its pending target. */
internal fun validateApreApplicationContract(
    state: WorkoutUiState,
    exercise: Exercise,
    record: CalibrationDecisionRecord,
): ApreApplicationContract {
    val apre = record.apre ?: return ApreApplicationContract(canApply = true)
    val block = liveApreBlock(state, exercise)
        ?: return ApreApplicationContract(false, "El bloque APRE cambió; revisa S3 antes de aplicar.")
    if (block.variant.xrmReps != apre.xrmReps) {
        return ApreApplicationContract(false, "Cambió el XRM de APRE; revisa S3 y su serie de destino.")
    }
    if (exercise.sets.getOrNull(block.thirdIndex)?.id != apre.sourcePlannedSetId) {
        return ApreApplicationContract(false, "La identidad de S3 cambió; revisa el resultado registrado.")
    }
    val target = exercise.sets.getOrNull(block.fourthIndex)
        ?: return ApreApplicationContract(false, "S4 ya no está disponible; revisa el bloque APRE.")
    if (target.id != apre.targetPlannedSetId) {
        return ApreApplicationContract(false, "La identidad de S4 cambió; revisa la serie de destino.")
    }

    val sourceEntry = state.completedSets.entries.firstOrNull { it.value.id == record.sourceSetId }
        ?: return ApreApplicationContract(false, "El resultado de S3 ya no está disponible; revísalo antes de aplicar.")
    val sourceKey = parseCompletedSetKey(sourceEntry.key)
        ?: return ApreApplicationContract(false, "La identidad registrada de S3 no se puede verificar; revisa el resultado.")
    if (sourceKey.exerciseId != exercise.id || sourceKey.setIdx != block.thirdIndex ||
        sourceKey.side != record.decision.context.side ||
        sourceEntry.value.performedPurpose() != SetPurpose.AMRAP ||
        sourceEntry.value.amrapMinimumReps?.let { it != apre.xrmReps } == true) {
        return ApreApplicationContract(false, "El marcador de S3 no coincide con esta propuesta; revisa el resultado.")
    }

    val targetId = calibrationSetId(target, sourceKey.side)
    val targetKey = workoutSetKey(exercise.id, block.fourthIndex, sourceKey.side)
    if (target.completedReps != null || targetKey in state.completedSets ||
        workoutSetKey(exercise.id, block.fourthIndex) in state.completedSets) {
        return ApreApplicationContract(false, "S4 ya está completada; revisa la carga registrada.")
    }
    val targetPending = pendingCalibrationSets(state, exercise, block.thirdIndex, record.decision.context)
        .firstOrNull { it.setId == targetId }
    if (targetPending == null) {
        return ApreApplicationContract(false, "S4 ya no está pendiente; revisa el bloque APRE.")
    }
    if (targetPending.isCompleted) {
        return ApreApplicationContract(false, "S4 ya está completada; revisa la carga registrada.")
    }
    if (targetPending.isManuallyFixed) {
        return ApreApplicationContract(false, "S4 tiene una carga fijada; revisa su valor antes de aplicar.")
    }
    val savedPrescription = record.pendingPrescriptions[targetId]
    if (targetPending.context != record.decision.context || savedPrescription?.let {
            it.targetReps != targetPending.targetReps || it.targetRir != targetPending.targetRir
        } == true) {
        return ApreApplicationContract(false, "La prescripción de S4 cambió; revisa la serie de destino.")
    }
    val proposedChange = record.decision.affectedSets.firstOrNull { it.setId == targetId }
    if (proposedChange == null || proposedChange.beforeKg != targetPending.baseLoadKg) {
        return ApreApplicationContract(false, "La carga base de S4 cambió; revisa la propuesta.")
    }
    return ApreApplicationContract(canApply = true)
}

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
