package com.example.kpkn.screens.workout

import com.example.kpkn.data.models.Exercise
import com.example.kpkn.data.models.ExerciseMuscleInfo
import com.example.kpkn.data.models.ExerciseSet
import com.example.kpkn.data.models.LoadModeV2
import com.example.kpkn.data.models.TrainingMode
import com.example.kpkn.data.models.UnitModeV2
import com.example.kpkn.domain.exercises.replacedWithCatalogExercise
import java.util.UUID

/**
 * Qué hereda un ejercicio que reemplaza a otro en la sesión en vivo (LV5, bug 3).
 *
 * El hueco de la sesión pasa de uno a otro y lo que el producto define que se queda en el hueco es solo esto:
 *  - cuántas series hay planificadas, con sus repeticiones (o tiempo) objetivo;
 *  - el descanso (el del ejercicio y el de cada serie);
 *  - la intensidad objetivo (RPE o RIR, AMRAP y fallo);
 *  - a qué superserie pertenece y la identidad del hueco en la receta (para reconstruirlo), con su movilidad de preparación.
 *
 * Todo lo demás es del ejercicio que se va y no pasa al nuevo: el modo de carga, las cargas, la carga inteligente (1RM, PR, referencia
 * de carga), las técnicas, las etiquetas, lo unilateral, el seguimiento de ROM… La carga sugerida del nuevo sale de SU historial.
 *
 * Se construye desde un ejercicio vacío y se le copia lo del hueco (lista de permitidos), no al revés: así un campo que se añada al
 * modelo mañana nunca se cuela de un ejercicio a otro, y reemplazar B por C tampoco arrastra nada de A, que ya no estaba en B.
 */
internal fun replacementExerciseFor(
    old: Exercise,
    replacement: ExerciseMuscleInfo,
    selectedAspects: Map<String, String>? = null,
    variantName: String? = null,
    variantGroupId: String? = null,
    variantGroupName: String? = null,
): Exercise {
    val slot = Exercise(
        id = old.id,
        name = old.name,
        sets = old.sets.map { it.slotPlan() },
        restTime = old.restTime,
        occurrenceId = old.occurrenceId,
        supersetId = old.supersetId,
        supersetGroupRef = old.supersetGroupRef,
        supersetRestBetween = old.supersetRestBetween,
        supersetRestAfter = old.supersetRestAfter,
        recipeDayId = old.recipeDayId,
        recipeSlotId = old.recipeSlotId,
        slotRole = old.slotRole,
        mobilitySeries = old.mobilitySeries,
        mobilityConfig = old.mobilityConfig,
    )
    val replaced = slot.replacedWithCatalogExercise(
        info = replacement,
        selectedAspects = selectedAspects,
        variantName = variantName,
        variantGroupId = variantGroupId,
        variantGroupName = variantGroupName,
    )
    val defaultLoadMode = replaced.sets.firstOrNull()?.loadModeV2 ?: LoadModeV2.LOAD
    val preservedSets = replaced.sets.ifEmpty {
        listOf(
            ExerciseSet(
                id = UUID.randomUUID().toString(),
                loadModeV2 = defaultLoadMode,
                unitModeV2 = UnitModeV2.REPS,
            ),
        )
    }
    return replaced.copy(trainingMode = TrainingMode.REPS, sets = preservedSets)
}

/** La serie tal como la define el hueco: sin cargas, modo de carga, técnicas ni referencias; solo lo que el producto deja pasar. */
internal fun ExerciseSet.slotPlan(): ExerciseSet = ExerciseSet(
    id = id,
    isEmptySlot = isEmptySlot,
    targetReps = targetReps,
    targetRepsRange = targetRepsRange,
    targetDuration = targetDuration,
    targetRPE = targetRPE,
    targetRIR = targetRIR,
    isAmrap = isAmrap,
    isFailure = isFailure,
    isTopSet = isTopSet,
    restAfterSeconds = restAfterSeconds,
)

/** True si [key] es una clave de estado en vivo de ese ejercicio: `id`, `id_serie[_L|_R]` o `id|etiqueta[|serie]`. */
internal fun isRuntimeKeyOfExercise(key: String, exerciseId: String): Boolean =
    key == exerciseId || key.startsWith("${exerciseId}_") || key.startsWith("${exerciseId}|")

/**
 * Quita del estado en vivo lo que sobre el ejercicio [exerciseId] aprendió la sesión y no es del hueco: el modo de carga con el que se
 * registró (por serie y por ejercicio), los ajustes de carga por disponibilidad y los destellos de sugerencia. Si no, el ejercicio nuevo,
 * que conserva el mismo id de hueco, heredaría el modo de carga del anterior (corporal, lastre, asistido…) por encima del suyo.
 */
internal fun WorkoutUiState.withoutLoadStateOfExercise(exerciseId: String): WorkoutUiState = copy(
    persistedLoadModeBySet = persistedLoadModeBySet.filterKeys { !isRuntimeKeyOfExercise(it, exerciseId) },
    persistedLoadModeByExercise = persistedLoadModeByExercise.filterKeys { !isRuntimeKeyOfExercise(it, exerciseId) },
    readinessAdjustments = readinessAdjustments.filterKeys { !isRuntimeKeyOfExercise(it, exerciseId) },
    loadSuggestionPulseTokens = loadSuggestionPulseTokens.filterKeys { !isRuntimeKeyOfExercise(it, exerciseId) },
)
