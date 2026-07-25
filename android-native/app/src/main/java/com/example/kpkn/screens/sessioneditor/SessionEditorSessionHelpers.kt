package com.example.kpkn.screens.sessioneditor

import com.example.kpkn.data.models.*
import com.example.kpkn.domain.calculations.calculateHybrid1RM
import com.example.kpkn.domain.calculations.calculateSuggestedLoad
import com.example.kpkn.domain.calculations.resolveReferenceCapacity
import com.example.kpkn.domain.calculations.suggestRestSeconds
import com.example.kpkn.domain.exercises.TechnicalAspectEngine
import com.example.kpkn.domain.exercises.normalizedIdentityFields
import com.example.kpkn.domain.exercises.resolvedCanonicalExerciseId
import com.example.kpkn.domain.workout.SupersetRules
import java.time.LocalDate
import java.util.UUID
import kotlin.math.roundToInt

internal fun createExerciseFromInfo(info: ExerciseMuscleInfo, history: List<WorkoutLog>): Exercise {
        val trainingMode = TrainingMode.REPS
        val variantResult = VariantFlowResultCache.consume(info.id)

        val effectiveMuscles: List<InvolvedMuscle>? = if (variantResult != null) {
            val selectedOptions = variantResult.selectedAspects.mapNotNull { (aspectId, optId) ->
                info.technicalAspects
                    ?.firstOrNull { it.id == aspectId }
                    ?.options
                    ?.firstOrNull { it.id == optId }
            }
            val result = TechnicalAspectEngine.computeEffectiveMuscles(
                baseMuscles = info.involvedMuscles,
                selectedOptions = selectedOptions,
            )
            result.effectiveMuscles
        } else {
            null
        }

        return Exercise(
            id = UUID.randomUUID().toString(),
            name = info.name,
            exerciseDbId = info.id,
            exerciseId = info.id,
            canonicalExerciseId = info.id.lowercase(),
            exerciseFamilyId = info.id.lowercase(),
            variantName = variantResult?.variantName ?: info.variantName,
            variantGroupId = variantResult?.variantGroupId ?: info.variantGroupId,
            variantGroupName = variantResult?.variantGroupName ?: info.variantGroupName,
            selectedAspects = variantResult?.selectedAspects,
            effectiveMuscles = effectiveMuscles,
            trainingMode = trainingMode,
            restTime = suggestRestSeconds(3, 8.0),
            sets = listOf(
                ExerciseSet(
                    id = UUID.randomUUID().toString(),
                    targetReps = if (info.category.equals("Fuerza", true)) 5 else 8,
                    targetPercentageRM = null,
                    intensityMode = null,
                )
            ),
            setupCues = info.setupCues.orEmpty(),
            executionCues = info.executionCues.orEmpty(),
            selectedExecutionOption = info.executionOptions?.firstOrNull(),
            selectedMovementPattern = info.movementPattern,
        ).withSharedPerformanceFromHistory(history)
    }

internal fun Exercise.withSessionEditorDefaults(defaults: SessionEditorRuleDefaults): Exercise {
        if (!defaults.applyToNewItems || isCompetitionLift) return this
        val safeSetCount = defaults.setCount.coerceAtLeast(1)
        val safeReps = defaults.reps.coerceAtLeast(1)
        val safeRpe = defaults.rpe.coerceIn(1.0, 10.0)
        val nextSets = List(safeSetCount) { index ->
            val existing = sets.getOrNull(index) ?: ExerciseSet(id = UUID.randomUUID().toString())
            existing.copy(
                targetReps = safeReps,
                targetRPE = safeRpe,
                targetRIR = null,
                targetPercentageRM = null,
                intensityMode = IntensityMode.RPE,
                isFailure = false,
            )
        }
        return copy(
            restTime = defaults.normalRestSeconds.coerceAtLeast(0),
            restBetweenSidesSeconds = defaults.betweenSidesRestSeconds.takeIf { it > 0 },
            sets = nextSets,
        )
    }

internal fun createBlankExercise(): Exercise =
    Exercise(
    id = UUID.randomUUID().toString(),
    name = "",
    canonicalExerciseId = null,
    exerciseFamilyId = null,
    trainingMode = TrainingMode.REPS,
    restTime = 90,
    sets = listOf(
    ExerciseSet(
    id = UUID.randomUUID().toString(),
    targetReps = 8,
    )
    ),
    )

internal fun Session.transformExercises(transform: (Exercise) -> Exercise): Session {
    var applied = false

    fun updateExercise(exercise: Exercise): Exercise {
        if (applied) return exercise
        val updated = transform(exercise)
        if (updated != exercise) {
            applied = true
        }
        return updated
    }

    val updatedParts = parts.map { part ->
        part.copy(exercises = part.exercises.map(::updateExercise))
    }
    val updatedExercises = exercises.map(::updateExercise)

    return copy(parts = updatedParts, exercises = updatedExercises)
}

internal data class LocatedSession(
    val session: Session,
    val week: ProgramWeek,
    val macroIndex: Int,
    val mesoIndex: Int,
)

internal const val SESSION_EDITOR_DRAFT_PREFS = "session_editor_drafts"
internal const val DEFAULT_SESSION_BACKGROUND = "solid://obsidian"
internal val DEFAULT_SESSION_BACKGROUNDS = listOf(
    DEFAULT_SESSION_BACKGROUND,
    "gradient://ember",
    "gradient://lagoon",
    "gradient://velvet",
    "gradient://forest",
)

internal fun SessionEditorViewModel.locateSession(
    program: Program,
    targetSessionId: String,
    targetWeekId: String?,
    targetMacroIndex: Int?,
    targetMesoIndex: Int?,
): LocatedSession? {
    program.macrocycles.forEachIndexed { macroIndex, macro ->
        var globalMesoIndex = 0
        macro.blocks.forEach { block ->
            block.mesocycles.forEach { meso ->
                val mesoIndex = globalMesoIndex++
                meso.weeks.forEach { week ->
                    if (targetWeekId != null && week.id != targetWeekId) return@forEach
                    val found = week.sessions.find { it.id == targetSessionId }
                    if (found != null) return LocatedSession(found, week, macroIndex, mesoIndex)
                }
            }
        }
    }
    if (targetMacroIndex != null && targetMesoIndex != null && !targetWeekId.isNullOrBlank()) {
        val week = findWeek(program, targetMacroIndex, targetMesoIndex, targetWeekId)
        val found = week?.sessions?.find { it.id == targetSessionId }
        if (found != null) return LocatedSession(found, week, targetMacroIndex, targetMesoIndex)
    }
    return null
}

internal fun SessionEditorViewModel.findWeek(
    program: Program,
    macroIndex: Int,
    mesoIndex: Int,
    weekId: String,
): ProgramWeek? {
    val macro = program.macrocycles.getOrNull(macroIndex) ?: return null
    return macro.blocks.flatMap { it.mesocycles }.getOrNull(mesoIndex)?.weeks?.find { it.id == weekId }
}

internal fun createDraftSession(sessionId: String, dayOfWeek: Int?): Session = Session(
    id = sessionId,
    name = "",
    lastModifiedAtMs = System.currentTimeMillis(),
    description = "",
    parts = emptyList(),
    dayOfWeek = dayOfWeek,
    background = SessionBackground(
    type = SessionBackgroundType.COLOR,
    value = DEFAULT_SESSION_BACKGROUND,
    style = SessionBackgroundStyle(blur = 0f, brightness = 0.92f),
    ),
    coverStyle = CoverStyle(filters = CoverFilters(), labelPosition = LabelPosition.BOTTOM_LEFT),
    isMainSession = true,
)

internal fun resolveNewestSession(
    existing: Session?,
    fallback: Session,
    persistedDraft: PersistedSessionEditorDraft?,
): Session {
    val persisted = persistedDraft?.session
    if (persisted == null) return existing ?: fallback
    val existingTimestamp = (existing ?: fallback).lastModifiedAtMs
    return if (persistedDraft.savedAtMs >= existingTimestamp) persisted else (existing ?: fallback)
}

internal fun ensureSessionInList(sessions: List<Session>, session: Session): List<Session> {
    val replaced = sessions.map { if (it.id == session.id) session else it }
    return if (replaced.any { it.id == session.id }) replaced else replaced + session
}

internal fun Session.ensureModifiedTimestamp(): Session =
    if (lastModifiedAtMs > 0L) this else copy(lastModifiedAtMs = System.currentTimeMillis())

internal fun SessionEditorViewModel.buildRoadmapOptions(program: Program): List<SessionRoadmapOption> {
    return program.macrocycles.flatMapIndexed { macroIndex, macro ->
        var mesoOffset = 0
        macro.blocks.flatMapIndexed { blockIndex, block ->
            val blockOffset = mesoOffset
            val items = block.mesocycles.flatMapIndexed { mesoIndex, meso ->
                meso.weeks.mapIndexed { weekIndex, week ->
                    SessionRoadmapOption(
                        macroIndex = macroIndex,
                        blockIndex = blockIndex,
                        mesoIndex = blockOffset + mesoIndex,
                        weekIndex = weekIndex,
                        weekId = week.id,
                        macroName = macro.name,
                        blockName = block.name,
                        weekName = week.name,
                        sessionCount = week.sessions.size,
                    )
                }
            }
            mesoOffset += block.mesocycles.size
            items
        }
    }
}

internal fun Program.updateWeekById(
    weekId: String,
    transform: (ProgramWeek) -> ProgramWeek,
): Program = copy(
    macrocycles = macrocycles.map { macro ->
        macro.copy(blocks = macro.blocks.map { block ->
            block.copy(mesocycles = block.mesocycles.map { meso ->
                meso.copy(weeks = meso.weeks.map { week ->
                    if (week.id == weekId) transform(week) else week
                })
            })
        })
    }
)

internal fun <T> moveItem(list: List<T>, targetId: String, direction: Int, key: (T) -> String): List<T> {
    val mutable = list.toMutableList()
    val index = mutable.indexOfFirst { key(it) == targetId }
    if (index == -1) return list
    val target = (index + direction).coerceIn(0, mutable.lastIndex)
    if (index == target) return list
    val moved = mutable.removeAt(index)
    mutable.add(target, moved)
    return mutable
}

internal fun Program.updateWeekSessions(
    macroIndex: Int,
    mesoIndex: Int,
    weekId: String,
    transform: (List<Session>) -> List<Session>,
): Program = copy(
    macrocycles = macrocycles.mapIndexed { currentMacroIndex, macro ->
        if (currentMacroIndex != macroIndex) return@mapIndexed macro
        macro.copy(blocks = macro.blocks.map { block ->
            block.copy(mesocycles = block.mesocycles.mapIndexed { currentMesoIndex, meso ->
                if (currentMesoIndex != mesoIndex) return@mapIndexed meso
                meso.copy(weeks = meso.weeks.map { week ->
                    if (week.id != weekId) week else week.copy(sessions = transform(week.sessions))
                })
            })
        })
    }
)

internal fun Session.normalizeSession(): Session {
    val normalizedBackground = background ?: SessionBackground(SessionBackgroundType.COLOR, DEFAULT_SESSION_BACKGROUND, SessionBackgroundStyle(0f, 0.92f))
    val normalizedCoverStyle = coverStyle ?: CoverStyle(filters = CoverFilters(), labelPosition = LabelPosition.BOTTOM_LEFT)
    val groupedParts = parts.filterNot { it.isUncategorizedPart() }
    val uncategorizedExercises = parts.filter { it.isUncategorizedPart() }.flatMap { it.exercises }
    val normalizedParts = groupedParts.map { part ->
        part.copy(exercises = part.exercises.map { it.normalizeExercise() })
    }
    val normalizedLooseExercises = (exercises + uncategorizedExercises).map { it.normalizeExercise() }
    return SupersetRules.normalizeSession(copy(
        description = description ?: "",
        exercises = normalizedLooseExercises,
        parts = normalizedParts,
        background = normalizedBackground,
        coverStyle = normalizedCoverStyle,
    ))
}

internal fun Exercise.normalizeExercise(): Exercise {
    val preservedLeftTargets = sets.map { it.leftTarget }
    val preservedRightTargets = sets.map { it.rightTarget }
    val normalizedSets = if (sets.isEmpty()) {
        listOf(ExerciseSet(UUID.randomUUID().toString(), targetReps = 8))
    } else sets.map { it.normalizeSet(this) }
    val restoredSets = normalizedSets.mapIndexed { index, set ->
        set.copy(
            leftTarget = preservedLeftTargets.getOrNull(index),
            rightTarget = preservedRightTargets.getOrNull(index),
        )
    }
    val normalizedIdentity = normalizedIdentityFields()
    val resolved1rm = resolveReferenceCapacity(normalizedIdentity.copy(sets = restoredSets))
    return normalizedIdentity.copy(
        restTime = restTime ?: suggestRestSeconds(restoredSets.size, restoredSets.mapNotNull { it.targetRPE }.averageOrNull() ?: 8.0),
        reference1RM = resolved1rm,
        sets = restoredSets,
    )
}

internal data class SharedExercisePerformance(
    val reference1RM: Double,
    val prReference: PrReference?,
    val consolidatedWeight: ConsolidatedWeight?,
    val suggestedNextLoad: Double?,
)

internal fun Exercise.withSharedPerformanceFromHistory(history: List<WorkoutLog>): Exercise {
    val normalized = normalizedIdentityFields()
    val shared = normalized.resolveSharedPerformance(history) ?: return normalized
    val withReferences = normalized.copy(
        reference1RM = normalized.reference1RM ?: shared.reference1RM,
        prFor1RM = normalized.prFor1RM ?: shared.prReference,
        consolidatedWeight = normalized.consolidatedWeight ?: shared.consolidatedWeight,
    )
    val hydratedSets = withReferences.sets.mapIndexed { index, set ->
        val normalizedSet = when {
            withReferences.trainingMode == TrainingMode.RM && set.targetPercentageRM == null -> {
                set.copy(
                    targetPercentageRM = 75.0,
                    intensityMode = IntensityMode.LOAD,
                )
            }
            else -> set
        }
        val suggested = calculateSuggestedLoad(withReferences, normalizedSet, history)
            ?: shared.suggestedNextLoad?.takeIf { index == 0 }
        if (normalizedSet.weight == null && suggested != null && suggested > 0.0) {
            normalizedSet.copy(weight = suggested)
        } else {
            normalizedSet
        }
    }
    return withReferences.copy(sets = hydratedSets)
}

internal fun Exercise.resolveSharedPerformance(history: List<WorkoutLog>): SharedExercisePerformance? {
    val canonicalId = resolvedCanonicalExerciseId()
    if (canonicalId.isBlank() || canonicalId == "unknown") return null

    val matchingExercises = history.asSequence()
        .flatMap { log ->
            log.completedExercises
                .filter { completed -> completed.resolvedCanonicalExerciseId() == canonicalId }
                .map { completed -> log.date to completed }
        }
        .toList()
    if (matchingExercises.isEmpty()) return null

    val completedSets = matchingExercises
        .flatMap { (date, completed) -> completed.sets.map { set -> date to set } }
        .filter { (_, set) -> !set.isWarmup && !set.skipped && set.weight > 0.0 && set.reps > 0 }
    if (completedSets.isEmpty()) return null

    fun estimatedRm(set: CompletedSet): Double =
        set.homologatedResultV3?.estimatedRm
            ?.takeIf { it > 0.0 }
            ?: calculateHybrid1RM(set.weight, set.reps)

    val bestSet = completedSets.maxByOrNull { (_, set) -> estimatedRm(set) } ?: return null
    val latestSet = completedSets.maxByOrNull { (date, _) -> date }?.second
    val bestEstimatedRm = estimatedRm(bestSet.second)
    val suggestedNextLoad = latestSet
        ?.homologatedResultV3
        ?.suggestedNextLoad
        ?.takeIf { it > 0.0 }

    return SharedExercisePerformance(
        reference1RM = bestEstimatedRm,
        prReference = PrReference(bestSet.second.weight, bestSet.second.reps),
        consolidatedWeight = latestSet?.let { ConsolidatedWeight(it.weight, it.reps) },
        suggestedNextLoad = suggestedNextLoad,
    )
}

internal fun ExerciseSet.normalizeSet(exercise: Exercise): ExerciseSet {
    val normalized = if (isFailure || intensityMode == IntensityMode.FAILURE) {
        copy(
            intensityMode = IntensityMode.FAILURE,
            targetRPE = null,
            targetRIR = null,
            isFailure = true,
        )
    } else when (exercise.trainingMode) {
        TrainingMode.RM -> copy(
            intensityMode = IntensityMode.LOAD,
            targetRPE = null,
            targetRIR = null,
            isFailure = false,
            isAmrap = false,
            targetPercentageRM = (targetPercentageRM ?: 75.0).coerceIn(40.0, 100.0),
        )
        TrainingMode.SOLO_RPE -> copy(
            intensityMode = IntensityMode.RPE,
            targetRPE = (targetRPE ?: 8.0).coerceIn(1.0, 10.0),
            targetRIR = null,
            targetPercentageRM = null,
            targetReps = null,
            targetDuration = null,
            isFailure = false,
            isAmrap = false,
        )
        else -> {
            if (intensityMode == IntensityMode.SOLO_RM) copy(intensityMode = null) else this
        }
    }
    val autoWeight = calculateSuggestedLoad(exercise, normalized)
    return normalized.copy(weight = autoWeight ?: normalized.weight)
}

internal fun createNextSetTemplate(exercise: Exercise, template: ExerciseSet): ExerciseSet {
    val base = template.copy(id = UUID.randomUUID().toString())
    // En modo RM se estima reducción de carga por fatiga; el resto mantiene la intensidad elegida
    if (exercise.trainingMode != TrainingMode.RM) return base

    val reps = template.targetReps ?: 1
    val fatigueDrop = when {
        reps >= 10 -> 5.0
        reps >= 6  -> 4.0
        reps >= 3  -> 3.0
        else       -> 2.0
    }
    return base.copy(
        targetPercentageRM = ((template.targetPercentageRM ?: 100.0) - fatigueDrop).coerceAtLeast(45.0),
        intensityMode = IntensityMode.LOAD,
    )
}

internal fun Exercise.asCompetitionMovement(): Exercise {
    return copy(
        isCompetitionLift = true,
        sets = emptyList(),
        warmupSets = emptyList(),
        restTime = null,
        supersetId = null,
        supersetGroupRef = null,
        supersetRestBetween = null,
        supersetRestAfter = null,
    )
}

internal fun formatOneDecimal(value: Double): String = ((value * 10.0).roundToInt() / 10.0).toString()

internal fun dayLabel(dayOfWeek: Int?): String = when (dayOfWeek) {
    1 -> "Lunes"
    2 -> "Martes"
    3 -> "Miércoles"
    4 -> "Jueves"
    5 -> "Viernes"
    6 -> "Sábado"
    7 -> "Domingo"
    else -> "día"
}

internal fun dayLabelShort(dayOfWeek: Int?): String = when (dayOfWeek) {
    1 -> "Lun"
    2 -> "Mar"
    3 -> "Mié"
    4 -> "Jue"
    5 -> "Vie"
    6 -> "Sáb"
    7 -> "Dom"
    else -> "Día"
}

internal fun List<Double>.averageOrNull(): Double? = if (isEmpty()) null else average()

internal fun defaultSessionNameForDay(dayOfWeek: Int?): String = "Sesion ${dayLabel(dayOfWeek)}"

internal fun buildCompetitionKeyDaysInWeek(program: Program, week: ProgramWeek?): Set<Int> {
    if (week == null) return emptySet()
    return (1..7).filter { day ->
        findCompetitionKeyDateForWeekDay(program, week, day) != null
    }.toSet()
}

internal fun findCompetitionKeyDateForWeekDay(
    program: Program,
    week: ProgramWeek,
    dayOfWeek: Int,
): ProgramKeyDate? {
    val dayDate = resolveWeekDayDate(week, dayOfWeek) ?: return null
    return program.keyDates.firstOrNull { keyDate ->
        keyDate.type == KeyDateType.COMPETITION &&
            (keyDate.eventDate ?: keyDate.startDate).toLocalDateOrNull() == dayDate
    }
}

internal fun resolveWeekDayDate(week: ProgramWeek, dayOfWeek: Int): LocalDate? {
    val explicit = week.trainingDayDates[dayOfWeek].toLocalDateOrNull()
    if (explicit != null) return explicit
    val weekStart = week.startDate.toLocalDateOrNull() ?: return null
    return weekStart.plusDays((dayOfWeek.coerceIn(1, 7) - 1).toLong())
}

internal fun String?.toLocalDateOrNull(): LocalDate? =
    this?.takeIf { it.isNotBlank() }?.let { runCatching { LocalDate.parse(it) }.getOrNull() }

internal fun defaultCompetitionSportType(mode: ProgramMode): CompetitionTemplateType = when (mode) {
    ProgramMode.POWERLIFTING -> CompetitionTemplateType.POWERLIFTING
    ProgramMode.HYPERTROPHY -> CompetitionTemplateType.BODYBUILDING
    ProgramMode.POWERBUILDING -> CompetitionTemplateType.POWERLIFTING
}
