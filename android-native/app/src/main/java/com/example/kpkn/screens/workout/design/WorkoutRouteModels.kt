package com.example.kpkn.screens.workout.design

import com.example.kpkn.data.exercises.displayNameWithSelectedChips
import com.example.kpkn.data.models.CompletedSet
import com.example.kpkn.data.models.Exercise
import com.example.kpkn.data.models.SupersetGroup
import com.example.kpkn.data.models.isCardio
import com.example.kpkn.data.models.isEffectivelyUnilateral
import com.example.kpkn.data.models.supersetGroupRefOrLegacyId
import com.example.kpkn.domain.workout.isSetDone
import com.example.kpkn.screens.workout.WorkoutStepRules

/**
 * Un ejercicio de la ruta, con lo que pinta su fila (KPKN LS4, §4). No tiene reglas: las rellena [workoutRouteGroups].
 * [stepKey] es el paso al que lleva tocarla dentro de una superserie (ronda y lado); en un ejercicio suelto es nulo.
 */
data class WorkoutRouteMember(
    val exerciseId: String,
    val exerciseIndex: Int,
    val name: String,
    val setsDone: Int,
    val setsTotal: Int,
    val isCurrent: Boolean,
    val stepKey: String?,
) {
    val isDone: Boolean get() = setsTotal > 0 && setsDone >= setsTotal
}

/**
 * Un bloque de la ruta: un ejercicio suelto o una superserie entera, que se reordena como una sola fila. [caption] («Superserie A ·
 * ronda 2 de 3») solo existe en una superserie. [isCardio] marca el bloque de cardio, que lleva el divisor «Cardio» si es el primero.
 */
data class WorkoutRouteGroup(
    val groupId: String?,
    val caption: String?,
    val isCardio: Boolean,
    val members: List<WorkoutRouteMember>,
) {
    /** Clave estable para la lista y para el gesto: no cambia al mover el bloque. */
    val key: String get() = groupId?.let { "group:$it" } ?: "exercise:${members.firstOrNull()?.exerciseId.orEmpty()}"

    val isSuperset: Boolean get() = groupId != null && members.size > 1
}

/**
 * Bloques de la ruta en el orden de la sesión, con las mismas reglas del roadmap de antes: la tabla de superseries manda y una superserie
 * se pinta donde está su primer ejercicio. En la superserie que contiene el ejercicio actual, la ronda es la de la serie en curso; en
 * las demás, la primera ronda con una serie pendiente (no se inventa «ronda 1»).
 */
fun workoutRouteGroups(
    exercises: List<Exercise>,
    supersetGroups: List<SupersetGroup>,
    currentIdx: Int,
    currentSetIdx: Int,
    completedSets: Map<String, CompletedSet>,
    omittedSetKeys: Set<String>,
): List<WorkoutRouteGroup> {
    val groupIdByExerciseId = buildMap {
        supersetGroups.forEach { group -> group.exerciseOrder.forEach { put(it, group.id) } }
        exercises.forEach { exercise -> exercise.supersetGroupRefOrLegacyId()?.let { put(exercise.id, it) } }
    }
    val indexById = exercises.withIndex().associate { (index, exercise) -> exercise.id to index }
    val currentId = exercises.getOrNull(currentIdx)?.id
    // La letra de la superserie sale del orden de aparición, igual que el eyebrow de la serie.
    val letterOrder = exercises.mapNotNull { groupIdByExerciseId[it.id] }.distinct()
    val emitted = mutableSetOf<String>()
    val result = mutableListOf<WorkoutRouteGroup>()
    for (exercise in exercises) {
        val groupId = groupIdByExerciseId[exercise.id]
        if (groupId != null && !emitted.add(groupId)) continue
        val block = if (groupId == null) listOf(exercise) else routeMembers(groupId, exercises, supersetGroups, groupIdByExerciseId)
        val isSuperset = groupId != null && block.size > 1
        val rounds = supersetGroups.firstOrNull { it.id == groupId }?.rounds?.takeIf { it > 0 }
            ?: block.maxOfOrNull { it.sets.size }
            ?: 0
        val roundCount = rounds.coerceAtLeast(1)
        val isCurrentBlock = block.any { it.id == currentId }
        val round = when {
            !isSuperset -> 0
            isCurrentBlock -> currentSetIdx.coerceIn(0, roundCount - 1)
            else -> (0 until roundCount).firstOrNull { r ->
                block.any { !it.roundSettled(r, completedSets, omittedSetKeys) }
            } ?: (roundCount - 1)
        }
        val blockDone = isSuperset && block.all { member ->
            (0 until roundCount).all { member.roundSettled(it, completedSets, omittedSetKeys) }
        }
        val caption = if (groupId != null && isSuperset) {
            val letter = 'A' + letterOrder.indexOf(groupId)
            if (blockDone) "Superserie $letter · completada" else "Superserie $letter · ronda ${round + 1} de $roundCount"
        } else {
            null
        }
        val members = block.map { member ->
            WorkoutRouteMember(
                exerciseId = member.id,
                exerciseIndex = indexById[member.id] ?: 0,
                name = member.displayNameWithSelectedChips(),
                setsDone = WorkoutStepRules.completedRoadmapSlotsForExercise(member, completedSets, omittedSetKeys),
                setsTotal = WorkoutStepRules.totalRoadmapSlotsForExercise(member, omittedSetKeys),
                isCurrent = member.id == currentId,
                stepKey = if (isSuperset) member.routeStepKey(round, completedSets) else null,
            )
        }
        result += WorkoutRouteGroup(
            groupId = groupId,
            caption = caption,
            isCardio = block.firstOrNull()?.isCardio == true,
            members = members,
        )
    }
    return result
}

/** Resumen de la hoja: «Ejercicio 3 de 6 · 14 de 22 series». Sin ejercicios, lo dice en una frase. */
fun workoutRouteSummary(currentIdx: Int, exerciseCount: Int, setsDone: Int, setsTotal: Int): String =
    if (exerciseCount <= 0) {
        "Esta sesión no tiene ejercicios"
    } else {
        val position = (currentIdx + 1).coerceIn(1, exerciseCount)
        "Ejercicio $position de $exerciseCount · ${workoutRouteSeriesLabel(setsDone, setsTotal)}"
    }

/** «2 de 4 series», con el singular para una sola serie. */
fun workoutRouteSeriesLabel(done: Int, total: Int): String =
    "$done de $total ${if (total == 1) "serie" else "series"}"

/** Orden de los ejercicios tras mover el bloque [fromIndex] a [toIndex]. Los bloques se mueven enteros. */
internal fun workoutRouteOrderedIds(groups: List<WorkoutRouteGroup>, fromIndex: Int, toIndex: Int): List<String> =
    swappedGroupedExerciseIds(groups.map { group -> group.members.map { it.exerciseId } }, fromIndex, toIndex)

internal fun swappedGroupedExerciseIds(
    groups: List<List<String>>,
    fromIndex: Int,
    toIndex: Int,
): List<String> {
    if (fromIndex !in groups.indices || toIndex !in groups.indices) return groups.flatten()
    val mutable = groups.toMutableList()
    val moved = mutable.removeAt(fromIndex)
    mutable.add(toIndex, moved)
    return mutable.flatten()
}

/** Ejercicios de una superserie, en su orden (la tabla de superseries manda; si no hay orden, los que llevan la referencia). */
private fun routeMembers(
    groupId: String,
    exercises: List<Exercise>,
    supersetGroups: List<SupersetGroup>,
    groupIdByExerciseId: Map<String, String>,
): List<Exercise> {
    val byOrder = supersetGroups.firstOrNull { it.id == groupId }?.exerciseOrder
        ?.mapNotNull { memberId -> exercises.firstOrNull { it.id == memberId } }
    val tagged = exercises.filter { groupIdByExerciseId[it.id] == groupId }
    return (byOrder.orEmpty() + tagged).distinctBy { it.id }.takeIf { it.size >= 2 } ?: tagged
}

/** Una ronda está cerrada para un ejercicio si no existe, si su serie se omitió o si ya está registrada. */
private fun Exercise.roundSettled(
    round: Int,
    completedSets: Map<String, CompletedSet>,
    omittedSetKeys: Set<String>,
): Boolean = round >= sets.size || WorkoutStepRules.isSetOmitted(id, round, omittedSetKeys) || isSetDone(completedSets, round)

/** Paso al que lleva tocar un ejercicio de la superserie: la ronda y, si es unilateral, el primer lado sin registrar. */
private fun Exercise.routeStepKey(round: Int, completedSets: Map<String, CompletedSet>): String {
    val side = if (isEffectivelyUnilateral()) {
        WorkoutStepRules.workingSidesForSet(this, round).firstOrNull { candidate ->
            !completedSets.containsKey("${id}_${round}_${candidate.take(1).uppercase()}")
        }?.lowercase()
    } else {
        null
    }
    return WorkoutStepRules.workingStepKey(id, round, side)
}
