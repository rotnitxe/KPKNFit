package com.example.kpkn.screens.workout

import com.example.kpkn.data.models.Exercise
import com.example.kpkn.data.models.ExerciseSet
import com.example.kpkn.data.models.LoadModeV2
import com.example.kpkn.data.models.Session
import com.example.kpkn.data.models.UnitModeV2
import com.example.kpkn.data.models.resolveUnitModeV2

/**
 * Lo que la sesión en vivo recuerda de cómo se hace cada serie y que, sin esta regla, pisa lo que la persona edita en el plan (LV5, bug 4).
 *
 * El tipo de carga de una serie (externa, corporal, lastre, asistido) se resuelve por capas: borrador de la serie, luego el modo con el
 * que se registró la última serie (por serie y por ejercicio) y, al final, el plan ([resolveEffectiveLoadMode]). Eso es lo que permite que
 * «la serie siguiente arranque como acabas de hacer la anterior». Pero también hacía que, al cambiar el tipo de una serie en «Editar
 * series», el recuerdo viejo ganara al plan y el tipo «volviera al que había».
 */
internal data class LiveEditRuntime(
    val persistedLoadModeBySet: Map<String, LoadModeV2>,
    val persistedLoadModeByExercise: Map<String, LoadModeV2>,
    val setDrafts: Map<String, WorkoutSetDraft>,
)

/**
 * Concilia el estado en vivo con una edición del plan: [previous] y [next] son la sesión de antes y de después, y [runtime] ya viene con
 * sus claves reasignadas a la sesión nueva.
 *
 * Para cada ejercicio, en las series que aún no se han hecho:
 *  - si el plan cambió el tipo de carga de una serie, el plan nuevo manda: se escribe como su tipo vigente y se descarta el tipo (y la carga
 *    escrita con el tipo anterior) de su borrador;
 *  - las series pendientes que van detrás conservan el tipo con el que ya salían (se fija), para que el tipo nuevo de una serie no se
 *    herede hacia las siguientes por arrastre;
 *  - si cambió la unidad de la serie (reps ↔ tiempo…), se descarta el valor escrito de su borrador, que ya no significa lo mismo.
 * Las series hechas no se tocan: conservan el tipo con el que se registraron.
 */
internal fun reconcileRuntimeWithPlanEdit(
    previous: Session,
    next: Session,
    completedSets: Map<String, *>,
    exerciseTags: Map<String, String>,
    runtime: LiveEditRuntime,
): LiveEditRuntime {
    val previousById = previous.allExercises().associateBy { it.id }
    var result = runtime
    next.allExercises().forEach { exercise ->
        val before = previousById[exercise.id] ?: return@forEach
        result = reconcileExerciseWithPlanEdit(
            before = before,
            after = exercise,
            completedSets = completedSets,
            tagId = exerciseTags[exercise.id],
            runtime = result,
        )
    }
    return result
}

private fun reconcileExerciseWithPlanEdit(
    before: Exercise,
    after: Exercise,
    completedSets: Map<String, *>,
    tagId: String?,
    runtime: LiveEditRuntime,
): LiveEditRuntime {
    val previousSets = before.sets.associateBy { it.id }
    val loadChanged = after.sets
        .filter { set -> previousSets[set.id]?.let { it.effectiveLoadMode() != set.effectiveLoadMode() } == true }
        .map { it.id }
        .toSet()
    val unitChanged = after.sets
        .filter { set -> previousSets[set.id]?.let { effectiveUnitMode(before, it) != effectiveUnitMode(after, set) } == true }
        .map { it.id }
        .toSet()
    if (loadChanged.isEmpty() && unitChanged.isEmpty()) return runtime

    val exerciseId = after.id
    var bySet = runtime.persistedLoadModeBySet
    var drafts = runtime.setDrafts
    // Una serie con algo registrado (de cualquier lado) cuenta como hecha: conserva el tipo con el que se registró, aunque la
    // edición cambie a la vez entre bilateral y unilateral y sus claves aún lleven el lado de antes.
    val pending = after.sets.indices.filter { idx -> !hasAnyRecord(completedSets, exerciseId, idx) }
    val firstChangedPending = pending.firstOrNull { idx -> after.sets[idx].id in loadChanged }

    if (firstChangedPending != null) {
        pending.filter { it >= firstChangedPending }.forEach { idx ->
            val set = after.sets[idx]
            val effective = if (set.id in loadChanged) {
                set.effectiveLoadMode()
            } else {
                // Lo que esta serie ya enseñaba antes de la edición: el recuerdo, y si no lo había, su plan.
                resolvePersistedLoadModeForSet(
                    exerciseId = exerciseId,
                    setIdx = idx,
                    tagId = tagId,
                    persistedLoadModeBySet = runtime.persistedLoadModeBySet,
                    persistedLoadModeByExercise = runtime.persistedLoadModeByExercise,
                ) ?: set.effectiveLoadMode()
            }
            bySet = bySet.filterKeys { key -> !isLoadModeKeyOfSet(key, exerciseId, idx) } +
                (workoutSetKey(exerciseId, idx) to effective)
        }
    }

    pending.forEach { idx ->
        val set = after.sets[idx]
        val modeChanged = set.id in loadChanged
        val unitDidChange = set.id in unitChanged
        if (!modeChanged && !unitDidChange) return@forEach
        val planned = set.effectiveLoadMode()
        listOf(null, "left", "right").forEach { side ->
            val key = workoutSetKey(exerciseId, idx, side)
            val draft = drafts[key] ?: return@forEach
            var updated = draft
            if (modeChanged) {
                // Una carga escrita con el tipo anterior no vale para el nuevo (kg de lastre ≠ kg de asistencia).
                val sameMode = draft.loadMode == planned
                updated = updated.copy(
                    loadMode = if (sameMode) planned else null,
                    weightText = if (sameMode) draft.weightText else null,
                )
            }
            if (unitDidChange) updated = updated.copy(valueText = null)
            if (updated != draft) drafts = drafts + (key to updated)
        }
    }

    // Sin nada que conciliar (todo lo que cambió ya estaba hecho), se devuelve el mismo estado.
    return if (bySet === runtime.persistedLoadModeBySet && drafts === runtime.setDrafts) {
        runtime
    } else {
        runtime.copy(persistedLoadModeBySet = bySet, setDrafts = drafts)
    }
}

private fun hasAnyRecord(completedSets: Map<String, *>, exerciseId: String, setIdx: Int): Boolean =
    workoutSetKey(exerciseId, setIdx) in completedSets ||
        workoutSetKey(exerciseId, setIdx, "left") in completedSets ||
        workoutSetKey(exerciseId, setIdx, "right") in completedSets

private fun ExerciseSet.effectiveLoadMode(): LoadModeV2 = loadModeV2 ?: LoadModeV2.LOAD

/** La unidad de la serie tal como la ve la tarjeta: la guardada o, si falta, la que deduce el modo del ejercicio (reps, tiempo…). */
private fun effectiveUnitMode(exercise: Exercise, set: ExerciseSet): UnitModeV2 =
    set.resolveUnitModeV2(exercise.trainingMode)

/** True si [key] guarda el modo de carga de la serie [setIdx] del ejercicio: `id_serie[_L|_R]` o `id|serie|etiqueta`. */
private fun isLoadModeKeyOfSet(key: String, exerciseId: String, setIdx: Int): Boolean =
    key == "${exerciseId}_$setIdx" ||
        key == "${exerciseId}_${setIdx}_L" ||
        key == "${exerciseId}_${setIdx}_R" ||
        key.startsWith("$exerciseId|$setIdx|")
