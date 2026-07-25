package com.example.kpkn.screens.sessioneditor

import com.example.kpkn.data.models.*
import java.util.UUID

internal fun Session.buildCloneExerciseOptions(): List<SessionCloneExerciseOption> {
    val fromParts = parts.flatMap { part ->
        part.exercises.map { exercise ->
            SessionCloneExerciseOption(
                exerciseId = exercise.id,
                name = exercise.name.ifBlank { "Ejercicio" },
                sourcePartName = part.name,
            )
        }
    }
    val loose = exercises.map { exercise ->
        SessionCloneExerciseOption(
            exerciseId = exercise.id,
            name = exercise.name.ifBlank { "Ejercicio" },
            sourcePartName = null,
        )
    }
    return fromParts + loose
}

internal fun buildClonePayload(
    source: Session,
    selectedExerciseIds: Set<String>?,
): ClonePayload {
    val filter: (Exercise) -> Boolean = { exercise ->
        selectedExerciseIds == null || exercise.id in selectedExerciseIds
    }
    val sourceParts = if (source.parts.isNotEmpty()) source.parts else {
        if (source.exercises.isEmpty()) emptyList() else listOf(
            SessionPart(
                id = UUID.randomUUID().toString(),
                name = source.name.ifBlank { "Bloque importado" },
                exercises = source.exercises,
                color = PART_COLORS.firstOrNull(),
            )
        )
    }
    val clonedParts = sourceParts.mapNotNull { part ->
        val selected = part.exercises.filter(filter)
        if (selected.isEmpty()) return@mapNotNull null
        val supersetIds = selected.mapNotNull { it.supersetGroupRefOrLegacyId() }.distinct().associateWith { UUID.randomUUID().toString() }
        part.copy(
            id = UUID.randomUUID().toString(),
            exercises = selected.map { cloneExerciseForTransfer(it, supersetIds) },
        )
    }

    val loose = source.exercises
        .filter(filter)
        .map { cloneExerciseForTransfer(it, emptyMap()) }

    return ClonePayload(parts = clonedParts, looseExercises = loose)
}

internal fun cloneExerciseForTransfer(
    exercise: Exercise,
    supersetIds: Map<String, String>,
): Exercise = exercise.copy(
    id = UUID.randomUUID().toString(),
    supersetId = exercise.supersetGroupRefOrLegacyId()?.let(supersetIds::get),
    supersetGroupRef = exercise.supersetGroupRefOrLegacyId()?.let(supersetIds::get),
    warmupSets = exercise.warmupSets.map { it.copy(id = UUID.randomUUID().toString()) },
    sets = exercise.sets.map { it.copy(id = UUID.randomUUID().toString()) },
)

internal fun mergeSessionWithPayload(
    base: Session,
    source: Session,
    payload: ClonePayload,
    selectedExerciseIds: Set<String>?,
    applyMode: SessionCloneApplyMode,
): Session {
    if (applyMode == SessionCloneApplyMode.REPLACE) {
        return createSessionFromPayload(
            source = source,
            dayOfWeek = base.dayOfWeek,
            targetName = base.name,
            payload = payload,
            selectedExerciseIds = selectedExerciseIds,
            existingId = base.id,
            preserveBackgroundFrom = base,
        )
    }
    return base.copy(
        exercises = base.exercises + payload.looseExercises,
        parts = base.parts + payload.parts,
    )
}

internal fun createSessionFromPayload(
    source: Session,
    dayOfWeek: Int?,
    targetName: String,
    payload: ClonePayload,
    selectedExerciseIds: Set<String>?,
    existingId: String? = null,
    preserveBackgroundFrom: Session? = null,
): Session {
    val name = when {
        selectedExerciseIds == null -> source.name.ifBlank { targetName.ifBlank { "Sesión" } }
        else -> targetName.ifBlank { source.name.ifBlank { "Sesión" } }
    }
    val base = preserveBackgroundFrom ?: source
    return source.copy(
        id = existingId ?: UUID.randomUUID().toString(),
        name = name,
        dayOfWeek = dayOfWeek,
        exercises = payload.looseExercises,
        parts = payload.parts,
        background = base.background,
        coverStyle = base.coverStyle,
        isMainSession = true,
    )
}

internal fun createSessionForTargetDay(
    source: Session,
    dayOfWeek: Int,
    payload: ClonePayload,
    selectedExerciseIds: Set<String>?,
): Session = createSessionFromPayload(
    source = source,
    dayOfWeek = dayOfWeek,
    targetName = defaultSessionNameForDay(dayOfWeek),
    payload = payload,
    selectedExerciseIds = selectedExerciseIds,
)

internal fun mergeSessions(
    base: Session,
    incoming: Session,
    selectedExerciseIds: Set<String>?,
    applyMode: SessionCloneApplyMode,
): Session {
    val payload = buildClonePayload(incoming, selectedExerciseIds)
    return mergeSessionWithPayload(
        base = base,
        source = incoming,
        payload = payload,
        selectedExerciseIds = selectedExerciseIds,
        applyMode = applyMode,
    )
}

internal fun Program.findSessionInProgram(
    macroIndex: Int,
    mesoIndex: Int,
    weekId: String,
    sessionId: String,
): Session? {
    val macro = macrocycles.getOrNull(macroIndex) ?: return null
    val meso = macro.blocks.flatMap { it.mesocycles }.getOrNull(mesoIndex) ?: return null
    val week = meso.weeks.firstOrNull { it.id == weekId } ?: return null
    return week.sessions.firstOrNull { it.id == sessionId }
}

internal fun buildCloneDayOptions(
    program: Program,
    currentSessionId: String,
): List<SessionCloneDayOption> {
    val options = mutableListOf<SessionCloneDayOption>()
    var globalMesoIndex = 0
    program.macrocycles.forEachIndexed { macroIndex, macro ->
        macro.blocks.forEachIndexed { blockIndex, block ->
            block.mesocycles.forEach { meso ->
                meso.weeks.forEach { week ->
                    (1..7).forEach { day ->
                        val existing = week.sessions.firstOrNull { it.dayOfWeek == day }
                        options += SessionCloneDayOption(
                            key = "$macroIndex|$globalMesoIndex|${week.id}|$day",
                            macroIndex = macroIndex,
                            mesoIndex = globalMesoIndex,
                            weekId = week.id,
                            dayOfWeek = day,
                            macroName = macro.name,
                            blockName = block.name,
                            mesoName = meso.name,
                            weekName = week.name,
                            existingSessionId = existing?.id,
                            existingSessionName = existing?.name,
                            isCurrentSessionDay = existing?.id == currentSessionId,
                        )
                    }
                }
                globalMesoIndex++
            }
        }
    }
    return options
}

internal fun buildCloneSourceOptions(
    program: Program,
    currentSessionId: String,
): List<SessionCloneSourceOption> {
    val options = mutableListOf<SessionCloneSourceOption>()
    var globalMesoIndex = 0
    program.macrocycles.forEachIndexed { macroIndex, macro ->
        macro.blocks.forEach { block ->
            block.mesocycles.forEach { meso ->
                meso.weeks.forEach { week ->
                    week.sessions.forEach { session ->
                        if (session.id == currentSessionId) return@forEach
                        options += SessionCloneSourceOption(
                            sessionId = session.id,
                            dayOfWeek = session.dayOfWeek,
                            macroIndex = macroIndex,
                            mesoIndex = globalMesoIndex,
                            weekId = week.id,
                            macroName = macro.name,
                            blockName = block.name,
                            mesoName = meso.name,
                            weekName = week.name,
                            sessionName = session.name.ifBlank { "Sesión" },
                            exerciseCount = session.allExercises().size,
                            exercises = session.buildCloneExerciseOptions(),
                        )
                    }
                }
                globalMesoIndex++
            }
        }
    }
    return options
}

