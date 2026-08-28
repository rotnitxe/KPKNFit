package com.example.kpkn.domain.workout

import com.example.kpkn.data.models.Exercise
import com.example.kpkn.data.models.Session
import com.example.kpkn.data.models.WeekVariant
import com.example.kpkn.data.models.supersetGroupRefOrLegacyId

/**
 * Pure session-structure transforms for the live workout editor.
 * ViewModel owns persistence / UI index resolution; this owns list surgery.
 */
object WorkoutStructuralEditor {

    fun withModeSession(base: Session, mode: WeekVariant, update: (Session) -> Session): Session = when (mode) {
        WeekVariant.A -> update(base)
        WeekVariant.B -> base.copy(sessionB = update(base.sessionB ?: base))
        WeekVariant.C -> base.copy(sessionC = update(base.sessionC ?: base))
        WeekVariant.D -> base.copy(sessionD = update(base.sessionD ?: base))
    }

    fun replaceExerciseById(
        session: Session,
        exerciseId: String,
        update: (Exercise) -> Exercise,
    ): Session {
        if (session.parts.isNotEmpty()) {
            var changed = false
            val newParts = session.parts.map { part ->
                val idx = part.exercises.indexOfFirst { it.id == exerciseId }
                if (idx < 0) return@map part
                changed = true
                val mutable = part.exercises.toMutableList()
                mutable[idx] = update(mutable[idx])
                part.copy(exercises = mutable)
            }
            return if (changed) session.copy(parts = newParts) else session
        }

        val idx = session.exercises.indexOfFirst { it.id == exerciseId }
        if (idx < 0) return session
        val mutable = session.exercises.toMutableList()
        mutable[idx] = update(mutable[idx])
        return session.copy(exercises = mutable)
    }

    fun moveExerciseById(session: Session, exerciseId: String, direction: Int): Session {
        if (session.parts.isNotEmpty()) {
            var changed = false
            val newParts = session.parts.map { part ->
                val idx = part.exercises.indexOfFirst { it.id == exerciseId }
                if (idx < 0 || part.exercises.size < 2) return@map part
                val target = (idx + direction).coerceIn(0, part.exercises.lastIndex)
                if (target == idx) return@map part
                changed = true
                val mutable = part.exercises.toMutableList()
                val moved = mutable.removeAt(idx)
                mutable.add(target, moved)
                part.copy(exercises = mutable)
            }
            return if (changed) session.copy(parts = newParts) else session
        }

        if (session.exercises.size < 2) return session
        val idx = session.exercises.indexOfFirst { it.id == exerciseId }
        if (idx < 0) return session
        val target = (idx + direction).coerceIn(0, session.exercises.lastIndex)
        if (target == idx) return session
        val mutable = session.exercises.toMutableList()
        val moved = mutable.removeAt(idx)
        mutable.add(target, moved)
        return session.copy(exercises = mutable)
    }

    fun reorderExercisesByIds(
        session: Session,
        partId: String?,
        orderedExerciseIds: List<String>,
    ): Session {
        fun reorderList(exercises: List<Exercise>): List<Exercise> {
            if (exercises.size < 2 || orderedExerciseIds.isEmpty()) return exercises
            val lookup = exercises.associateBy { it.id }
            val ordered = orderedExerciseIds.mapNotNull(lookup::get).toMutableList()
            if (ordered.size != exercises.size) {
                exercises.forEach { exercise ->
                    if (exercise.id !in orderedExerciseIds) {
                        ordered.add(exercise)
                    }
                }
            }
            return ordered
        }

        return if (partId == null) {
            val reordered = reorderList(session.exercises)
            if (reordered == session.exercises) session else session.copy(exercises = reordered)
        } else {
            var changed = false
            val updatedParts = session.parts.map { part ->
                if (part.id != partId) part
                else {
                    changed = true
                    part.copy(exercises = reorderList(part.exercises))
                }
            }
            if (changed) session.copy(parts = updatedParts) else session
        }
    }

    fun globalReorder(
        session: Session,
        orderedExerciseIds: List<String>,
        originalPartMap: Map<String, String>,
    ): Session {
        if (orderedExerciseIds.isEmpty()) return session
        if (session.parts.isEmpty()) {
            val lookup = session.exercises.associateBy { it.id }
            return session.copy(exercises = orderedExerciseIds.mapNotNull(lookup::get))
        }
        data class ExBlock(val partId: String?, val ids: List<String>)
        val blocks = mutableListOf<ExBlock>()
        var curPart: String? = originalPartMap[orderedExerciseIds[0]]
        var curIds = mutableListOf<String>()
        for (id in orderedExerciseIds) {
            val p = originalPartMap[id]
            if (p != curPart && curIds.isNotEmpty()) {
                blocks.add(ExBlock(curPart, curIds.toList()))
                curIds = mutableListOf()
                curPart = p
            }
            curIds.add(id)
        }
        if (curIds.isNotEmpty()) blocks.add(ExBlock(curPart, curIds.toList()))

        val newPartOf = mutableMapOf<String, String?>()
        for (i in blocks.indices) {
            val block = blocks[i]
            val ids = block.ids
            val originalBlockPart = block.partId
            if (ids.size == 1) {
                val prevPart = if (i > 0) blocks[i - 1].partId else null
                val nextPart = if (i < blocks.lastIndex) blocks[i + 1].partId else null
                if (prevPart != null && nextPart != null && prevPart == nextPart && prevPart != originalBlockPart) {
                    newPartOf[ids[0]] = prevPart
                    continue
                }
            }
            ids.forEach { id -> newPartOf[id] = originalBlockPart }
        }

        val allEx = session.allExercises().associateBy { it.id }
        val partGroups = mutableMapOf<String?, MutableList<Exercise>>()
        for (id in orderedExerciseIds) {
            val ex = allEx[id] ?: continue
            partGroups.getOrPut(newPartOf[id]) { mutableListOf() }.add(ex)
        }

        val newParts = session.parts.map { part ->
            val list = partGroups.remove(part.name) ?: emptyList()
            part.copy(exercises = list)
        }
        val topLevel = partGroups.remove(null) ?: emptyList()
        return session.copy(parts = newParts, exercises = topLevel + partGroups.values.flatten())
    }

    fun insertExerciseAfterSupersetMembers(
        session: Session,
        memberIds: List<String>,
        exercise: Exercise,
    ): Session {
        val memberIdSet = memberIds.toSet()
        if (session.parts.isNotEmpty()) {
            val partWithGroup = session.parts.firstOrNull { part -> part.exercises.any { it.id in memberIdSet } }
            if (partWithGroup != null) {
                return session.copy(parts = session.parts.map { part ->
                    if (part.id != partWithGroup.id) part
                    else {
                        val lastMemberIndex = part.exercises.indexOfLast { it.id in memberIdSet }
                        val insertionIndex = (lastMemberIndex + 1).coerceIn(0, part.exercises.size)
                        part.copy(exercises = part.exercises.toMutableList().apply { add(insertionIndex, exercise) })
                    }
                })
            }
        }

        val lastMemberIndex = session.exercises.indexOfLast { it.id in memberIdSet }
        val insertionIndex = (lastMemberIndex + 1).coerceIn(0, session.exercises.size)
        return session.copy(exercises = session.exercises.toMutableList().apply { add(insertionIndex, exercise) })
    }

    fun removeSetFromExercise(session: Session, exerciseId: String, setIndex: Int): Session {
        return replaceExerciseById(session, exerciseId) { exercise ->
            if (setIndex !in exercise.sets.indices || exercise.sets.size <= 1) exercise
            else {
                val remaining = exercise.sets.filterIndexed { index, _ -> index != setIndex }
                exercise.copy(sets = remaining.ifEmpty { exercise.sets.take(1) })
            }
        }
    }

    fun removeExerciseById(session: Session, exerciseId: String): Session {
        val all = session.allExercises()
        if (all.size <= 1) return session
        if (all.none { it.id == exerciseId }) return session
        val groupId = all.firstOrNull { it.id == exerciseId }?.supersetGroupRefOrLegacyId()
        val afterGroup = if (!groupId.isNullOrBlank()) {
            com.example.kpkn.domain.workout.SupersetRules.deleteExercise(session, groupId, exerciseId)
        } else {
            session
        }
        return afterGroup.copy(
            exercises = afterGroup.exercises.filterNot { it.id == exerciseId },
            parts = afterGroup.parts.map { part ->
                part.copy(exercises = part.exercises.filterNot { it.id == exerciseId })
            },
        )
    }

    fun removeExercisesByIds(session: Session, exerciseIds: Collection<String>): Session {
        var current = session
        exerciseIds.forEach { id ->
            current = removeExerciseById(current, id)
        }
        return current
    }
}
