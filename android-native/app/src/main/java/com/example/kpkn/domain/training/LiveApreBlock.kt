package com.example.kpkn.domain.training

import com.example.kpkn.data.models.Exercise
import com.example.kpkn.data.models.SetPurpose

/** Identidad estable del bloque: una AMRAP suelta nunca permite aplicar la tabla de Mann. */
data class LiveApreBlock(val variant: ApreVariant, val setIds: List<String>, val indices: List<Int>) {
    val thirdIndex: Int get() = indices[2]
    val fourthIndex: Int get() = indices[3]
}

/**
 * El marcador explícito manda. Si se invalida al editar, no se recupera por parecido.
 * El respaldo legado exige la receta y la regla APRE reales, además de sus cuatro series.
 */
fun resolveLiveApreBlock(
    exercise: Exercise,
    sourceProtocolId: String? = null,
    hasApreProgression: Boolean = false,
): LiveApreBlock? {
    val marker = exercise.apreBlock
    val ids = marker?.setIds ?: run {
        if (sourceProtocolId != "apre-mann" || !hasApreProgression || exercise.sets.size != 4) return null
        exercise.sets.map { it.id }
    }
    if (ids.size != 4 || ids.any { it.isBlank() } || ids.distinct().size != 4) return null
    // Ni un ID duplicado en el ejercicio ni una serie intercalada pueden cambiar la identidad de S3/S4.
    if (ids.any { id -> exercise.sets.count { it.id == id } != 1 }) return null
    val indices = ids.map { id -> exercise.sets.indexOfFirst { it.id == id } }
    if (indices.any { it < 0 } || indices.zipWithNext().any { (a, b) -> b != a + 1 }) return null
    val sets = indices.map { exercise.sets[it] }
    val variant = Apre.variantOfXrmReps(marker?.xrmReps ?: sets[2].amrapMinimumReps ?: return null) ?: return null
    if (sets[0].purpose != SetPurpose.NONE || sets[1].purpose != SetPurpose.NONE ||
        sets[2].purpose != SetPurpose.AMRAP || sets[3].purpose != SetPurpose.AMRAP) return null
    if (sets[0].targetReps != variant.set1Reps || sets[1].targetReps != variant.set2Reps ||
        sets[2].amrapMinimumReps != variant.xrmReps || sets[3].amrapMinimumReps != variant.xrmReps) return null
    if (sets.any { it.isEmptySlot }) return null
    return LiveApreBlock(variant, ids.toList(), indices)
}

/** A captured APRE working max is never a TM/1RM. Only a fully valid block consumes its exact range/configuration. */
fun observedApreXrmBlock(exercise: Exercise): LiveApreBlock? {
    val reference = exercise.loadReference ?: return null
    if (reference.kind != com.example.kpkn.data.protocols.PlanLoadReferenceKind.OBSERVED_WORKING_SET ||
        reference.state != com.example.kpkn.data.protocols.PlanLoadReferenceState.CAPTURED ||
        reference.metadata?.origin != ExerciseLoadReferenceStore.APRE_XRM ||
        reference.capturedLoadKg?.let { it.isFinite() && it > 0.0 } != true ||
        reference.configurationId != (exercise.catalogConfigurationId ?: exercise.exerciseDbId) ||
        reference.quantityConvention != exercise.loadQuantityConvention) return null
    return resolveLiveApreBlock(exercise)?.takeIf {
        reference.repMin == it.variant.xrmReps && reference.repMax == it.variant.xrmReps
    }
}
