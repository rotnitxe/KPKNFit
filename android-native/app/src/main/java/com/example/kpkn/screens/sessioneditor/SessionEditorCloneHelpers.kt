package com.example.kpkn.screens.sessioneditor

import com.example.kpkn.data.models.*
import com.example.kpkn.domain.training.PlanMaterializer
import java.util.UUID

internal data class SessionTransferAffectedTarget(
    val option: SessionCloneDayOption,
    val session: Session,
)

internal data class SessionTransferOutcome(
    val program: Program,
    val affectedTargets: List<SessionTransferAffectedTarget>,
    val transferReceipt: String? = null,
    /** All selected live destinations, including ones already carrying this receipt. */
    val receiptTargets: List<SessionTransferAffectedTarget> = affectedTargets,
)

private data class SessionTransferLocation(val weekId: String, val dayOfWeek: Int)

/** Resolve target identities against the supplied current program before each write. */
internal fun applySessionTransfersToProgram(
    program: Program,
    currentSessionId: String,
    pending: PendingTransferToDays,
): SessionTransferOutcome {
    var updatedProgram = program
    val affected = mutableListOf<SessionTransferAffectedTarget>()
    val receiptTargets = mutableListOf<SessionTransferAffectedTarget>()
    val receipt = transferReceiptFor(pending)
    val selectedLocations = pending.targetKeys.mapNotNull(::transferLocationFromOptionKey).toSet()
    val currentTargets = buildCloneDayOptions(program, currentSessionId)
        .filter {
            !it.isCurrentSessionDay &&
                (it.key in pending.targetKeys || SessionTransferLocation(it.weekId, it.dayOfWeek) in selectedLocations)
        }
        .distinctBy { it.weekId to it.dayOfWeek }

    currentTargets.forEach { target ->
        val liveExisting = updatedProgram.findWeekById(target.weekId)
            ?.sessions
            ?.firstOrNull { it.id == target.existingSessionId || (target.existingSessionId == null && it.dayOfWeek == target.dayOfWeek) }
        if (liveExisting != null && receipt != null && updatedProgram.hasTransferReceipt(liveExisting.id, receipt, target.weekId)) {
            // Room may have committed the transfer before a caller/lifecycle
            // failed to acknowledge it. The receipt makes an APPEND retry safe.
            receiptTargets += SessionTransferAffectedTarget(target, liveExisting)
            return@forEach
        }
        val nextProgram = applySessionTransferTarget(
            program = updatedProgram,
            source = pending.sourceSession,
            target = target,
            selectedExerciseIds = pending.selectedExerciseIds,
            applyMode = pending.applyMode,
        )
        val resultingSession = nextProgram.findWeekById(target.weekId)
            ?.sessions
            ?.firstOrNull { it.id == target.existingSessionId || (target.existingSessionId == null && it.dayOfWeek == target.dayOfWeek) }
        if (resultingSession != null && nextProgram != updatedProgram) {
            updatedProgram = nextProgram
            val affectedTarget = SessionTransferAffectedTarget(target, resultingSession)
            affected += affectedTarget
            receiptTargets += affectedTarget
        }
    }

    return SessionTransferOutcome(updatedProgram, affected, receipt, receiptTargets)
}

/** Stable marker carried by the SESSION override so a committed transfer can be retried safely. */
internal fun transferReceiptFor(pending: PendingTransferToDays): String =
    "[editor-transfer:${pending.transferId}:${pending.sourceSession.withoutGeneratedEditorTimestamps().hashCode().toUInt().toString(16)}]"

internal fun Program.hasTransferReceipt(sessionId: String, receipt: String, weekId: String? = null): Boolean =
    manualSessionOverrides.any { override ->
        override.sessionId == sessionId &&
            override.scope == ManualOverrideScope.SESSION &&
            (weekId == null || override.weekId == weekId) &&
            receipt in override.reason
    }

internal fun Program.weekOccurrenceFor(weekId: String): Int? {
    for (macro in macrocycles) {
        for (block in macro.blocks) {
            for (meso in block.mesocycles) {
                val index = meso.weeks.indexOfFirst { it.id == weekId }
                if (index >= 0) return PlanMaterializer.weekOccurrenceOf(meso.weeks[index], index)
            }
        }
    }
    return null
}

/** Resolves only the destination day from the current occurrence's recipe snapshot. */
internal fun Program.recipeDayIdForTarget(weekId: String, dayOfWeek: Int): String? {
    val recipe = sourceRecipe ?: return null
    val source = PlanMaterializer.weekRecipeSourceFor(this, recipe, weekId) ?: return null
    val startDay = resolvedSchedulePlan().weekStartDay ?: this.startDay ?: 1
    return source.weekRecipe.days.firstNotNullOfOrNull { day ->
        val authoredWeekday = day.weekday ?: return@firstNotNullOfOrNull null
        val resolvedWeekday = ((startDay - 1) + (authoredWeekday - 1)).mod(7) + 1
        day.id.takeIf { resolvedWeekday == dayOfWeek }
    }
}

internal fun Program.destinationRecipeDayId(weekId: String, dayOfWeek: Int, existing: Session?): String? {
    val existingOverride = existing?.let { session ->
        manualSessionOverrides.firstOrNull {
            it.sessionId == session.id && it.scope == ManualOverrideScope.SESSION && (it.weekId == null || it.weekId == weekId)
        }?.recipeDayId
    }
    // APPEND/REPLACE must retain an existing destination's authored metadata;
    // only a newly created target derives its identity from today's recipe.
    return existingOverride
        ?: existing?.allExercises()?.mapNotNull { it.recipeDayId }?.firstOrNull()
        ?: recipeDayIdForTarget(weekId, dayOfWeek)
}

internal fun Program.recordTransferReceipt(sessionId: String, receipt: String): Program {
    if (hasTransferReceipt(sessionId, receipt)) return this
    return copy(
        manualSessionOverrides = manualSessionOverrides.map { override ->
            if (override.sessionId == sessionId && override.scope == ManualOverrideScope.SESSION) {
                override.copy(reason = listOf(override.reason, receipt).filter(String::isNotBlank).distinct().joinToString(" · "))
            } else {
                override
            }
        },
    )
}

private fun transferLocationFromOptionKey(key: String): SessionTransferLocation? {
    val parts = key.split('|')
    if (parts.size < 4) return null
    val day = parts.last().toIntOrNull() ?: return null
    val weekId = parts[2]
    return weekId.takeIf(String::isNotBlank)?.let { SessionTransferLocation(it, day) }
}

internal fun applySessionTransferTarget(
    program: Program,
    source: Session,
    target: SessionCloneDayOption,
    selectedExerciseIds: Set<String>?,
    applyMode: SessionCloneApplyMode,
): Program {
    val payload = buildClonePayload(source, selectedExerciseIds)
    return program.updateWeekById(target.weekId) { week ->
        val sessions = week.sessions.toMutableList()
        val existingIndex = target.existingSessionId?.let { existingId -> sessions.indexOfFirst { it.id == existingId } } ?: -1
        if (existingIndex >= 0) {
            val existing = sessions[existingIndex]
            sessions[existingIndex] = mergeSessionWithPayload(
                base = existing,
                source = source,
                payload = payload,
                selectedExerciseIds = selectedExerciseIds,
                applyMode = applyMode,
            ).copy(dayOfWeek = target.dayOfWeek)
        } else {
            sessions += createSessionForTargetDay(
                source = source,
                dayOfWeek = target.dayOfWeek,
                payload = payload,
                selectedExerciseIds = selectedExerciseIds,
            )
        }
        week.copy(sessions = normalizeEditorMainSessions(sessions))
    }
}

internal fun Program.findWeekById(weekId: String): ProgramWeek? =
    macrocycles.asSequence()
        .flatMap { it.blocks.asSequence() }
        .flatMap { it.mesocycles.asSequence() }
        .flatMap { it.weeks.asSequence() }
        .firstOrNull { it.id == weekId }

internal fun normalizeEditorMainSessions(sessions: List<Session>): List<Session> {
    val distinctSessions = sessions.distinctBy { it.id }
    val mainByDay = mutableMapOf<Int, String>()
    val fallbackByDay = mutableMapOf<Int, String>()
    distinctSessions.forEach { session ->
        val day = session.dayOfWeek ?: 1
        fallbackByDay.putIfAbsent(day, session.id)
        if (session.isMainSession && day !in mainByDay) mainByDay[day] = session.id
    }
    fallbackByDay.forEach { (day, id) -> mainByDay.putIfAbsent(day, id) }
    return distinctSessions.map { session ->
        val day = session.dayOfWeek ?: 1
        session.copy(isMainSession = mainByDay[day] == session.id)
    }
}

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
        // B6: limpiar variantes y metadatos de competición desincronizados.
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
            sessionB = null,
            sessionC = null,
            sessionD = null,
            trainingBackup = null,
            isMeetDay = false,
            isCompetitionSession = false,
            competitionDetails = null,
            competitionRecordId = null,
            competitionKeyDateId = null,
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
                meso.weeks.forEachIndexed { weekIndex, week ->
                    val occurrence = PlanMaterializer.weekOccurrenceOf(week, weekIndex)
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
                            destinationRecipeDayId = program.destinationRecipeDayId(week.id, day, existing),
                            weekOccurrence = occurrence,
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
