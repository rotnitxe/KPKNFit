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
    val sourceParts = if (source.parts.isNotEmpty()) {
        source.parts
    } else if (source.exercises.isEmpty()) {
        emptyList()
    } else {
        listOf(
            SessionPart(
                id = UUID.randomUUID().toString(),
                name = source.name.ifBlank { "Bloque importado" },
                exercises = source.exercises,
                color = PART_COLORS.firstOrNull(),
            ),
        )
    }

    val supersetIdMap = mutableMapOf<String, String>()
    val exerciseIdMap = mutableMapOf<String, String>()

    val clonedParts = sourceParts.mapNotNull { part ->
        val selected = part.exercises.filter(filter)
        if (selected.isEmpty()) return@mapNotNull null
        part.copy(
            id = UUID.randomUUID().toString(),
            exercises = selected.map { cloneExerciseForTransfer(it, supersetIdMap, exerciseIdMap) },
        )
    }

    val loose = if (source.parts.isNotEmpty()) {
        source.exercises.filter(filter).map { cloneExerciseForTransfer(it, supersetIdMap, exerciseIdMap) }
    } else {
        // When we wrapped loose exercises into a synthetic part above, avoid duplicating them.
        emptyList()
    }

    val clonedExerciseIds = (clonedParts.flatMap { it.exercises } + loose).map { it.id }.toSet()
    val clonedSupersetGroups = source.allSupersetGroups().mapNotNull { group ->
        val newId = supersetIdMap[group.id] ?: return@mapNotNull null
        val newOrder = group.exerciseOrder
            .mapNotNull(exerciseIdMap::get)
            .filter { it in clonedExerciseIds }
        group.copy(
            id = newId,
            exerciseOrder = newOrder,
            visualPlacement = group.visualPlacement?.let { placement ->
                placement.copy(
                    partId = null,
                    anchorExerciseId = placement.anchorExerciseId?.let(exerciseIdMap::get),
                )
            },
        ).takeIf { it.exerciseOrder.size >= 2 }
    }

    return ClonePayload(
        parts = clonedParts,
        looseExercises = loose,
        supersetGroups = clonedSupersetGroups,
    )
}

internal fun cloneExerciseForTransfer(
    exercise: Exercise,
    supersetIdMap: MutableMap<String, String>,
    exerciseIdMap: MutableMap<String, String>,
): Exercise {
    val newId = UUID.randomUUID().toString()
    exerciseIdMap[exercise.id] = newId
    val newSupersetId = exercise.supersetGroupRefOrLegacyId()?.let { old ->
        supersetIdMap.getOrPut(old) { UUID.randomUUID().toString() }
    }
    return exercise.copy(
        id = newId,
        supersetId = newSupersetId,
        supersetGroupRef = newSupersetId,
        warmupSets = exercise.warmupSets.map { it.copy(id = UUID.randomUUID().toString()) },
        sets = exercise.sets.map { it.copy(id = UUID.randomUUID().toString()) },
    )
}

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
            preserveIdentityFrom = base,
        )
    }
    return base.copy(
        exercises = base.exercises + payload.looseExercises,
        parts = base.parts + payload.parts,
        supersetGroups = base.allSupersetGroups() + payload.supersetGroups,
    )
}

internal fun createSessionFromPayload(
    source: Session,
    dayOfWeek: Int?,
    targetName: String,
    payload: ClonePayload,
    selectedExerciseIds: Set<String>?,
    existingId: String? = null,
    preserveIdentityFrom: Session? = null,
): Session {
    val name = when {
        selectedExerciseIds == null -> source.name.ifBlank { targetName.ifBlank { "Sesión" } }
        else -> targetName.ifBlank { source.name.ifBlank { "Sesión" } }
    }
    val identity = preserveIdentityFrom
    return if (identity != null) {
        // REPLACE into an existing day: keep destination identity/metadata, swap structure.
        identity.copy(
            name = if (selectedExerciseIds == null) name else identity.name,
            dayOfWeek = dayOfWeek ?: identity.dayOfWeek,
            exercises = payload.looseExercises,
            parts = payload.parts,
            supersetGroups = payload.supersetGroups,
            warmup = if (selectedExerciseIds == null) {
                source.warmup.map { it.copy(id = UUID.randomUUID().toString()) }
            } else {
                identity.warmup
            },
            isMainSession = true,
        )
    } else {
        source.copy(
            id = existingId ?: UUID.randomUUID().toString(),
            name = name,
            dayOfWeek = dayOfWeek,
            exercises = payload.looseExercises,
            parts = payload.parts,
            supersetGroups = payload.supersetGroups,
            warmup = source.warmup.map { it.copy(id = UUID.randomUUID().toString()) },
            isMainSession = true,
            isMeetDay = false,
            isCompetitionSession = false,
            competitionDetails = null,
            competitionRecordId = null,
            competitionKeyDateId = null,
            meetResults = null,
            trainingBackup = null,
        )
    }
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
        macro.blocks.forEach { block ->
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
                            existingExerciseCount = existing?.allExercises()?.size ?: 0,
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
