package com.example.kpkn.domain.training

import com.example.kpkn.data.models.*
import com.example.kpkn.data.protocols.*
import com.example.kpkn.domain.calibration.CalibrationContext
import com.example.kpkn.domain.exercises.resolvedCanonicalExerciseId

/** Sólo usa el contexto persistido; una etiqueta técnica desconocida no se adivina ni se transfiere. */
fun plannedApreContext(exercise: Exercise, set: ExerciseSet, side: String?, recorded: CompletedSet? = null): CalibrationContext {
    val profile = exercise.contextProfilesV3.firstOrNull { it.id == set.contextProfileIdV3 }
    val mode = recorded?.loadMode ?: set.loadModeV2 ?: profile?.loadMode ?: LoadModeV2.LOAD
    val equipmentKey = recorded?.homologatedResultV3?.contextKey ?: recorded?.setOutcomeV2?.contextKey
        ?: buildWorkoutContextKey(exercise.resolvedCanonicalExerciseId(),
            recorded?.machineBrand ?: profile?.machineBrand ?: set.machineBrand,
            recorded?.tagId ?: profile?.tagId, mode, set.unitModeV2 ?: UnitModeV2.REPS)
    return CalibrationContext(exercise.catalogConfigurationId ?: exercise.resolvedCanonicalExerciseId(), equipmentKey,
        side, recorded?.setupProfileId ?: profile?.setupProfileId ?: set.defaultSetupProfileIdV3 ?: set.setupId,
        mode, set.loadQuantityConvention.takeUnless { it == LoadQuantityConvention.UNSPECIFIED }
            ?: exercise.loadQuantityConvention)
}

/** La carga y las reps válidas de S4 alimentan exclusivamente la tabla de Mann. */
fun captureApreXrm(
    exercise: Exercise, index: Int, completed: CompletedSet,
    sourceProtocolId: String? = null, hasApreProgression: Boolean = false,
    incrementKg: Double = Apre.DEFAULT_INCREMENT_KG, capturedAtMs: Long? = null,
    sourceProgramId: String? = null, contextOverride: CalibrationContext? = null,
): ApreXrmCapture? {
    val block = resolveLiveApreBlock(exercise, sourceProtocolId, hasApreProgression) ?: return null
    if (index != block.fourthIndex || completed.skipped || completed.isWarmup ||
        completed.performedPurpose() != SetPurpose.AMRAP || completed.reps < 0 ||
        !incrementKg.isFinite() || incrementKg <= 0.0) return null
    val context = contextOverride ?: plannedApreContext(exercise, exercise.sets[index], completed.side, completed)
    if (context.loadMode !in setOf(LoadModeV2.LOAD, LoadModeV2.LASTRE) || context.side != completed.side) return null
    val load = completed.originalLoad ?: completed.recordedPayloadV3?.externalLoad
        ?: completed.weight.takeUnless { context.loadMode == LoadModeV2.LASTRE } ?: return null
    if (!load.isFinite() || load <= 0.0) return null
    val valid = Apre.validReps(completed.reps) // CAL1 ya separó las parciales: no descontarlas otra vez.
    val next = Apre.nextWeekXrmKg(load, valid, block.variant, incrementKg)
    if (!next.isFinite() || next <= 0.0) return null
    val reason = "${block.variant.label}: ${block.variant.xrmReps}RM ${NativeProgressionText.formatKg(next)} kg " +
        "para la próxima semana (tabla de Mann, $valid reps válidas con ${NativeProgressionText.formatKg(load)} kg)."
    val reference = PlanLoadReference(
        kind = PlanLoadReferenceKind.OBSERVED_WORKING_SET, configurationId = context.configurationId,
        quantityConvention = context.loadQuantityConvention, sourceSlotId = exercise.recipeSlotId,
        repMin = block.variant.xrmReps, repMax = block.variant.xrmReps, side = completed.side,
        sourceProgramId = sourceProgramId, state = PlanLoadReferenceState.CAPTURED,
        capturedLoadKg = next, capturedAtMs = capturedAtMs,
        metadata = PlanLoadReferenceMetadata(origin = ExerciseLoadReferenceStore.APRE_XRM, reason = reason,
            contextKey = context.key, sourceId = completed.id, sourceSetId = exercise.sets[index].id),
    )
    return ApreXrmCapture(exercise.id, exercise.sets[index].id, context, reference, incrementKg,
        exercise.recipeDayId, exercise.recipeSlotId)
}
