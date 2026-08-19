package com.example.kpkn.screens.sessioneditor

import com.example.kpkn.data.models.Exercise
import com.example.kpkn.data.models.Session
import com.example.kpkn.data.models.SessionPart
import com.example.kpkn.data.models.isCardioPart
import com.example.kpkn.data.models.supersetGroupRefOrLegacyId
import com.example.kpkn.domain.workout.SupersetRules

internal fun SessionPart.isUncategorizedPart(): Boolean {
    val normalized = name.trim().lowercase()
    return normalized in setOf("sin categoría", "sin categoria", "sin grupo")
}

/**
 * Flattened list model for the session editor [LazyColumn].
 * Each exercise or superset block is its own lazy item for proper virtualization.
 */
sealed class SessionListItem {
    abstract val stableKey: String

    data object Hero : SessionListItem() {
        override val stableKey: String = "hero"
    }

    data object CompetitionEditor : SessionListItem() {
        override val stableKey: String = "competition-editor"
    }

    data class LooseSuperset(
        val groupId: String,
        val memberIds: List<String>,
        val indexInLoose: Int,
    ) : SessionListItem() {
        override val stableKey: String = "loose-superset-$groupId"
    }

    data class LooseExercise(
        val exerciseId: String,
        val indexInLoose: Int,
    ) : SessionListItem() {
        override val stableKey: String = "loose-exercise-$exerciseId"
    }

    data class PartHeader(
        val partId: String,
    ) : SessionListItem() {
        override val stableKey: String = "part-header-$partId"
    }

    data class PartSuperset(
        val partId: String,
        val groupId: String,
        val memberIds: List<String>,
        val indexInPart: Int,
    ) : SessionListItem() {
        override val stableKey: String = "part-$partId-superset-$groupId"
    }

    data class PartExercise(
        val partId: String,
        val exerciseId: String,
        val indexInPart: Int,
    ) : SessionListItem() {
        override val stableKey: String = "part-$partId-exercise-$exerciseId"
    }

    /** Footer button to add an exercise into an expanded group/part. */
    data class PartAddExercise(
        val partId: String,
    ) : SessionListItem() {
        override val stableKey: String = "part-add-$partId"
    }

    data object StrengthAddActions : SessionListItem() {
        override val stableKey: String = "strength-add-actions"
    }

    data object CardioDivider : SessionListItem() {
        override val stableKey: String = "cardio-divider"
    }

    data object StrengthDivider : SessionListItem() {
        override val stableKey: String = "strength-divider"
    }

    data object AddActions : SessionListItem() {
        override val stableKey: String = "add-actions"
    }
}

fun buildSessionListItems(
    session: Session,
    collapsedPartIds: Set<String> = emptySet(),
): List<SessionListItem> {
    val items = mutableListOf<SessionListItem>()
    items += SessionListItem.Hero

    if (session.isMeetDay) {
        items += SessionListItem.CompetitionEditor
        return items
    }

    val groupedParts = session.parts.filterNot { it.isUncategorizedPart() }
    val firstPartIsCardio = groupedParts.firstOrNull()?.isCardioPart() == true

    if (firstPartIsCardio) {
        // --- 1. BLOQUE CARDIO ARRIBA ---
        items += SessionListItem.CardioDivider
        val cardioParts = groupedParts.filter { it.isCardioPart() }
        cardioParts.forEach { part ->
            appendPartItems(items, part, session, collapsedPartIds)
        }

        // --- 2. BLOQUE FUERZA ABAJO ---
        items += SessionListItem.StrengthDivider
        if (session.exercises.isNotEmpty()) {
            appendExerciseItems(
                items = items,
                exercises = session.exercises,
                session = session,
                partId = null,
            )
        }
        val strengthParts = groupedParts.filterNot { it.isCardioPart() }
        strengthParts.forEach { part ->
            appendPartItems(items, part, session, collapsedPartIds)
        }
        items += SessionListItem.StrengthAddActions
    } else {
        // --- 1. BLOQUE FUERZA ARRIBA ---
        if (session.exercises.isNotEmpty()) {
            appendExerciseItems(
                items = items,
                exercises = session.exercises,
                session = session,
                partId = null,
            )
        }
        val strengthParts = groupedParts.filterNot { it.isCardioPart() }
        strengthParts.forEach { part ->
            appendPartItems(items, part, session, collapsedPartIds)
        }
        items += SessionListItem.StrengthAddActions

        // --- 2. BLOQUE CARDIO ABAJO (si existe) ---
        val cardioParts = groupedParts.filter { it.isCardioPart() }
        if (cardioParts.isNotEmpty()) {
            items += SessionListItem.CardioDivider
            cardioParts.forEach { part ->
                appendPartItems(items, part, session, collapsedPartIds)
            }
        }
    }

    return items
}

private fun appendPartItems(
    items: MutableList<SessionListItem>,
    part: SessionPart,
    session: Session,
    collapsedPartIds: Set<String>,
) {
    items += SessionListItem.PartHeader(part.id)
    if (part.id !in collapsedPartIds) {
        appendExerciseItems(
            items = items,
            exercises = part.exercises,
            session = session,
            partId = part.id,
        )
        items += SessionListItem.PartAddExercise(part.id)
    }
}

private fun appendExerciseItems(
    items: MutableList<SessionListItem>,
    exercises: List<Exercise>,
    session: Session,
    partId: String?,
) {
    exercises.forEachIndexed { index, exercise ->
        val supersetGroupId = exercise.supersetGroupRefOrLegacyId()
        if (supersetGroupId != null) {
            val isFirstMember = exercises.firstOrNull {
                it.supersetGroupRefOrLegacyId() == supersetGroupId
            }?.id == exercise.id
            if (!isFirstMember) return@forEachIndexed
            val supersetGroup = session.allSupersetGroups().firstOrNull { it.id == supersetGroupId }
            val members = SupersetRules.orderedMembers(session, supersetGroupId)
                .filter { member -> exercises.any { it.id == member.id } }
            if (supersetGroup != null && members.size >= 2) {
                val memberIds = members.map { it.id }
                if (partId == null) {
                    items += SessionListItem.LooseSuperset(supersetGroupId, memberIds, index)
                } else {
                    items += SessionListItem.PartSuperset(partId, supersetGroupId, memberIds, index)
                }
                return@forEachIndexed
            }
        }
        if (partId == null) {
            items += SessionListItem.LooseExercise(exercise.id, index)
        } else {
            items += SessionListItem.PartExercise(partId, exercise.id, index)
        }
    }
}

fun findListIndexForExercise(items: List<SessionListItem>, exerciseId: String): Int {
    return items.indexOfFirst { item ->
        when (item) {
            is SessionListItem.LooseExercise -> item.exerciseId == exerciseId
            is SessionListItem.LooseSuperset -> exerciseId in item.memberIds
            is SessionListItem.PartExercise -> item.exerciseId == exerciseId
            is SessionListItem.PartSuperset -> exerciseId in item.memberIds
            else -> false
        }
    }
}

/**
 * Maps an exercise id to the LazyColumn index used by SessionEditorScreen:
 * [0]=Hero, [1..n]=scrollable content, [n+1]=AddActions (optional).
 */
fun lazyColumnIndexForExercise(
    scrollableItems: List<SessionListItem>,
    exerciseId: String,
): Int {
    val contentIndex = findListIndexForExercise(scrollableItems, exerciseId)
    return if (contentIndex >= 0) contentIndex + 1 else -1
}
