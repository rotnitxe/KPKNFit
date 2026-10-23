package com.example.kpkn.domain.training

import com.example.kpkn.data.models.*
import com.example.kpkn.data.protocols.*
import com.example.kpkn.domain.workout.LoadSuggestionEngine

/** Una captura de trabajo APRE nunca representa una referencia de capacidad. */
fun validApreXrmCapture(capture: ApreXrmCapture): Boolean {
    val ref = capture.reference
    val variant = ref.repMin?.let(Apre::variantOfXrmReps) ?: return false
    return capture.sourceExerciseId.isNotBlank() && capture.sourcePlannedSetId.isNotBlank() &&
        ref.kind == PlanLoadReferenceKind.OBSERVED_WORKING_SET && ref.state == PlanLoadReferenceState.CAPTURED &&
        ref.repMax == variant.xrmReps && ref.metadata?.origin == ExerciseLoadReferenceStore.APRE_XRM &&
        ref.configurationId == capture.context.configurationId && ref.side == capture.context.side &&
        ref.quantityConvention == capture.context.loadQuantityConvention && ref.metadata.contextKey == capture.context.key &&
        !ref.metadata.sourceId.isNullOrBlank() && ref.metadata.sourceSetId == capture.sourcePlannedSetId &&
        ref.sourceSlotId == capture.sourceRecipeSlotId && capture.incrementKg.isFinite() && capture.incrementKg > 0.0 &&
        ref.capturedLoadKg?.let { it.isFinite() && it > 0.0 } == true &&
        capture.context.loadMode in setOf(LoadModeV2.LOAD, LoadModeV2.LASTRE)
}

/** Aplica kilos de trabajo sólo al mismo bloque y contexto; preserva por separado sus capacidades. */
fun applyApreXrmToExercise(exercise: Exercise, capture: ApreXrmCapture): Exercise {
    if (!validApreXrmCapture(capture)) return exercise
    val sameOccurrence = exercise.id == capture.sourceExerciseId
    val sameRecipeSlot = capture.sourceRecipeDayId != null && capture.sourceRecipeSlotId != null &&
        exercise.recipeDayId == capture.sourceRecipeDayId && exercise.recipeSlotId == capture.sourceRecipeSlotId
    if (!sameOccurrence && !sameRecipeSlot) return exercise
    val block = resolveLiveApreBlock(exercise) ?: return exercise
    if (block.variant.xrmReps != capture.reference.repMin || exercise.sets.size != 4) return exercise
    val side = capture.context.side
    if ((side == null && exercise.isEffectivelyUnilateral()) || (side != null && !exercise.isEffectivelyUnilateral())) return exercise
    if (block.indices.any { index -> plannedApreContext(exercise, exercise.sets[index], side) != capture.context ||
            exercise.sets[index].manualLoadRequiredSides.let { required ->
                if (side == null) required.isNotEmpty() else side in required || "bilateral" in required
            } }) return exercise
    val xrm = capture.reference.capturedLoadKg!!
    val factors = listOf(Apre.SET1_FRACTION, Apre.SET2_FRACTION, 1.0, 1.0)
    val rows = exercise.sets.mapIndexed { index, set ->
        val ordinal = block.indices.indexOf(index)
        if (ordinal < 0) set else {
            val kg = if (ordinal >= 2) xrm else LoadSuggestionEngine.roundLoad(xrm * factors[ordinal], capture.incrementKg)
            val planned = set.copy(targetPercentageRM = null, loadBasis = null)
            when (side) {
                "left" -> planned.copy(leftTarget = (set.leftTarget ?: UnilateralTarget()).copy(weight = kg))
                "right" -> planned.copy(rightTarget = (set.rightTarget ?: UnilateralTarget()).copy(weight = kg))
                null -> planned.copy(weight = kg)
                else -> return exercise
            }
        }
    }
    // APRE observado usa kg y reps de Mann, igual que el bloque del editor; no muestra un %TM ficticio.
    return exercise.copy(trainingMode = TrainingMode.REPS, sets = rows, loadReference = capture.reference)
}

/** Reaplica sólo snapshots ya vinculados a este ID por la bolsa; los contextos ajenos se conservan como historial. */
fun consumeApreXrmReferences(exercise: Exercise, references: List<PlanLoadReference>): Exercise {
    val block = resolveLiveApreBlock(exercise) ?: return exercise
    val candidates = references.filter { it.kind == PlanLoadReferenceKind.OBSERVED_WORKING_SET &&
        it.metadata?.origin == ExerciseLoadReferenceStore.APRE_XRM && it.repMin == block.variant.xrmReps &&
        it.repMax == block.variant.xrmReps && it.sourceSlotId == exercise.recipeSlotId }
    val identities = candidates.distinctBy { listOf(it.kind, it.configurationId, it.quantityConvention, it.side,
        it.repMin, it.repMax, it.sourceSlotId, it.metadata?.origin, it.metadata?.contextKey) }
    return identities.fold(exercise) { current, identity ->
        val ref = ExerciseLoadReferenceStore.resolve(candidates, identity) ?: return@fold current
        val source = ref.metadata?.sourceSetId ?: return@fold current
        val context = plannedApreContext(current, current.sets[block.fourthIndex], ref.side)
        applyApreXrmToExercise(current, ApreXrmCapture(current.id, source, context, ref,
            ref.metadata?.loadIncrementKg?.takeIf { it.isFinite() && it > 0.0 } ?: Apre.DEFAULT_INCREMENT_KG,
            current.recipeDayId, current.recipeSlotId))
    }
}
