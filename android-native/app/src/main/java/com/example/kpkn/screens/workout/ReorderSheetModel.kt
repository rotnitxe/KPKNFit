package com.example.kpkn.screens.workout

import com.example.kpkn.data.models.CompletedSet
import com.example.kpkn.data.models.Exercise
import com.example.kpkn.data.models.isCardio
import com.example.kpkn.data.models.supersetGroupRefOrLegacyId
import com.example.kpkn.domain.workout.LiveSessionReorder

/** Cómo va un ejercicio en la sesión, para la hoja «Orden de la sesión» (LV11, A3; prototipo C3). */
internal enum class ReorderStatus {
    /** Todas sus series están hechas: fijo arriba, con ✓. */
    DONE,

    /** Tiene series hechas y le faltan otras: fijo arriba, «en curso · 1/4». */
    IN_PROGRESS,

    /** Sin ninguna serie hecha: se puede arrastrar. */
    PENDING,
}

/**
 * Una fila de la hoja de orden: un ejercicio suelto o una superserie entera (la superserie se mueve como una unidad). [key] es el id de
 * su primer miembro: estable mientras la fila existe, aunque cambie de sitio.
 */
internal data class ReorderRow(
    val key: String,
    val memberIds: List<String>,
    val memberNames: List<String>,
    val title: String,
    val subtitle: String?,
    val status: ReorderStatus,
    val isSuperset: Boolean,
    val isCardio: Boolean,
) {
    /** Lo hecho y lo que está en curso queda fijo: sin asa, apagado y sin poder marcarse. */
    val locked: Boolean get() = status != ReorderStatus.PENDING
}

/**
 * Las filas de la hoja en el orden [order] (el plano que se está editando). Un ejercicio de [order] que ya no está en [exercises]
 * (omitido o quitado con la hoja abierta) no sale. El estado de cada fila sale de lo registrado en [completedSets] con la misma cuenta
 * que la tarjeta del carrusel («1/4»); las series omitidas no cuentan.
 */
internal fun buildReorderRows(
    order: List<String>,
    exercises: Map<String, Exercise>,
    completedSets: Map<String, CompletedSet>,
    omittedSetKeys: Set<String>,
    supersetRounds: (groupId: String) -> Int? = { null },
    displayName: (Exercise) -> String = { it.name },
): List<ReorderRow> {
    val present = order.filter { it in exercises }
    val units = LiveSessionReorder.units(present) { id -> exercises[id]?.supersetGroupRefOrLegacyId() }
    return units.map { unit ->
        val members = unit.mapNotNull(exercises::get)
        val groupId = members.firstOrNull()?.supersetGroupRefOrLegacyId()
        val isSuperset = groupId != null && members.size > 1
        val total = WorkoutStepRules.totalRoadmapSlotsForExercises(members, omittedSetKeys)
        val done = WorkoutStepRules.completedRoadmapSlotsForExercises(members, completedSets, omittedSetKeys)
        val status = when {
            total > 0 && done >= total -> ReorderStatus.DONE
            done > 0 -> ReorderStatus.IN_PROGRESS
            else -> ReorderStatus.PENDING
        }
        val rounds = if (isSuperset) {
            groupId?.let(supersetRounds)?.takeIf { it > 0 } ?: members.maxOfOrNull { it.sets.size } ?: 0
        } else {
            0
        }
        val names = members.map(displayName)
        val first = members.first()
        val subtitle = when {
            status == ReorderStatus.DONE -> null
            isSuperset && status == ReorderStatus.IN_PROGRESS ->
                "superserie · en curso · ${completedRounds(members, completedSets, omittedSetKeys, rounds)}/$rounds rondas"
            isSuperset -> "superserie · ${roundsText(rounds)}"
            status == ReorderStatus.IN_PROGRESS -> "en curso · $done/$total"
            first.isCardio -> cardioDurationText(first)?.let { "cardio · $it" } ?: "cardio"
            else -> seriesText(first, omittedSetKeys)
        }
        ReorderRow(
            key = unit.first(),
            memberIds = unit,
            memberNames = names,
            title = names.joinToString(" + "),
            subtitle = subtitle,
            status = status,
            isSuperset = isSuperset,
            isCardio = members.all { it.isCardio },
        )
    }
}

private fun roundsText(rounds: Int): String = if (rounds == 1) "1 ronda" else "$rounds rondas"

private fun seriesText(exercise: Exercise, omittedSetKeys: Set<String>): String {
    val count = exercise.sets.indices.count { !WorkoutStepRules.isSetOmitted(exercise.id, it, omittedSetKeys) && !exercise.sets[it].isEmptySlot }
    return if (count == 1) "1 serie" else "$count series"
}

private fun cardioDurationText(exercise: Exercise): String? {
    val seconds = exercise.cardioDetails?.effectiveDurationSeconds()?.takeIf { it > 0 } ?: return null
    val minutes = seconds / 60
    val rest = seconds % 60
    return when {
        minutes > 0 && rest > 0 -> "${minutes}m ${rest}s"
        minutes > 0 -> "$minutes min"
        else -> "$rest s"
    }
}

/** Rondas de una superserie en las que todos los miembros ya hicieron su serie. */
private fun completedRounds(
    members: List<Exercise>,
    completedSets: Map<String, CompletedSet>,
    omittedSetKeys: Set<String>,
    rounds: Int,
): Int = (0 until rounds).count { round ->
    val keys = members.filter { round in it.sets.indices && !WorkoutStepRules.isSetOmitted(it.id, round, omittedSetKeys) }
        .flatMap { WorkoutStepRules.completionKeysForRoadmapSet(it, round) }
    keys.isNotEmpty() && keys.all { it in completedSets }
}

/** Los ejercicios fijos de las [rows]: los de las filas hechas o en curso (una superserie entera si uno de sus miembros lo está). */
internal fun lockedExerciseIds(rows: List<ReorderRow>): Set<String> =
    rows.filter { it.locked }.flatMapTo(HashSet()) { it.memberIds }

/**
 * El orden tras pedir [candidate] sobre [current], con las reglas de la hoja: lo hecho y lo en curso queda fijo arriba (nada se pone
 * delante) y el cardio no se mezcla con la fuerza. Si lo pedido no se puede, el orden se queda como estaba.
 */
internal fun sanitizeReorder(
    candidate: List<String>,
    current: List<String>,
    locked: Set<String>,
    isCardio: (String) -> Boolean,
): List<String> {
    val sanitized = LiveSessionReorder.keepLockedFirst(candidate, current, locked)
    // Cada zona en su sitio: el cardio sigue donde estaba respecto a la fuerza.
    if (sanitized.map(isCardio) != current.map(isCardio)) return current
    return sanitized
}

/** Los ejercicios (no las filas) de las filas marcadas en [selected]: lo fijo no se marca, así que nunca entra. */
internal fun selectedExerciseIds(rows: List<ReorderRow>, selected: Set<String>): List<String> =
    rows.filter { it.key in selected && !it.locked }.flatMap { it.memberIds }
