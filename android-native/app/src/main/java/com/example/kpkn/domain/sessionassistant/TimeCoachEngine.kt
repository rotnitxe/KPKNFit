package com.example.kpkn.domain.sessionassistant

import com.example.kpkn.data.models.DropSetData
import com.example.kpkn.data.models.Exercise
import com.example.kpkn.data.models.ExerciseMuscleInfo
import com.example.kpkn.data.models.ExerciseSet
import com.example.kpkn.data.models.RestPauseData
import com.example.kpkn.data.models.Session
import com.example.kpkn.data.models.supersetGroupRefOrLegacyId
import com.example.kpkn.data.models.plannedRepAnchor
import com.example.kpkn.domain.calculations.SessionTimeBreakdown
import com.example.kpkn.domain.calculations.calculateSessionTimeBreakdown
import com.example.kpkn.domain.workout.SupersetRules
import java.util.UUID
import kotlin.math.max
import kotlin.math.roundToInt

enum class TimeCoachFatigueDelta {
    LOWER,
    SIMILAR,
    HIGHER,
}

sealed class TimeCoachAction {
    data class ReduceRests(
        val targetRestSeconds: Int,
        val perExerciseTargetRests: Map<String, Int> = emptyMap(),
        val alsoUpdateRuleDefaults: Boolean = false,
    ) : TimeCoachAction()

    data class CreateSuperset(
        val exerciseIdA: String,
        val exerciseIdB: String,
        val restBetween: Int = 30,
        val restAfter: Int = 90,
    ) : TimeCoachAction()

    data class ConvertToDropSets(
        val exerciseId: String,
    ) : TimeCoachAction()

    data class ConvertToRestPause(
        val exerciseId: String,
    ) : TimeCoachAction()

    data class RemoveLastSet(
        val exerciseId: String,
    ) : TimeCoachAction()

    data class ReplaceWithCompound(
        val removeExerciseIds: List<String>,
        val replacementDbId: String,
        val replacementName: String,
        val sets: Int,
        val reps: Int,
        val restSeconds: Int,
        val insertPartId: String?,
    ) : TimeCoachAction()

    data class RemoveExercise(
        val exerciseId: String,
    ) : TimeCoachAction()
}

data class TimeCoachSuggestion(
    val id: String,
    val title: String,
    val explanation: String,
    val minutesSaved: Int,
    val fatigueDelta: TimeCoachFatigueDelta,
    val action: TimeCoachAction,
)

object TimeCoachEngine {

    private enum class RestReductionProfile(
        val floorSeconds: Int,
        val stepSeconds: Int,
        val priority: Int,
    ) {
        ISOLATION_MACHINE_SMALL(45, 15, 0),
        ISOLATION_CABLE_SMALL(45, 15, 1),
        ISOLATION_GENERAL(60, 15, 2),
        GLUTE_MED_ABDUCTION(45, 15, 2),
        MACHINE_COMPOUND(90, 15, 3),
        FREE_WEIGHT_COMPOUND(120, 15, 4),
        DEFAULT(75, 15, 5),
    }

    private val curatedRestProfiles = RestReductionProfile.entries.associateBy { it.name }

    private const val MIN_GAP_MINUTES = 1
    private const val MAX_SUGGESTIONS = 8
    private const val REDUNDANT_PATTERN_MIN_SETS = 6.0
    private val COMMON_EQUIPMENT = setOf(
        "barbell", "barra", "dumbbell", "mancuerna", "machine", "maquina", "máquina",
        "cable", "polea", "smith", "bodyweight", "peso corporal",
    )
    private val NICHE_NAME_TOKENS = setOf(
        "zercher", "jefferson", "good morning", "buenos días", "sissy", "guillotine",
        "landmine 180", "meadows", "z press", "behind the neck",
    )

    fun generate(
        session: Session,
        breakdown: SessionTimeBreakdown,
        targetDurationMinutes: Int?,
        exerciseIndex: Map<String, ExerciseMuscleInfo>,
        dismissedIds: Set<String> = emptySet(),
    ): List<TimeCoachSuggestion> {
        val limit = targetDurationMinutes ?: return emptyList()
        if (limit <= 0) return emptyList()
        val gap = breakdown.totalMinutes - limit
        if (gap < MIN_GAP_MINUTES) return emptyList()

        val exercises = session.allExercises()
        if (exercises.isEmpty()) return emptyList()

        val candidates = mutableListOf<TimeCoachSuggestion>()

        buildRestSuggestions(session, exercises, exerciseIndex, breakdown, limit).let { candidates += it }
        buildSupersetSuggestions(session, exercises, exerciseIndex, breakdown).let { candidates += it }
        buildRedundantExerciseSuggestions(session, exercises, exerciseIndex, breakdown).let { candidates += it }
        buildDensitySuggestions(session, exercises, exerciseIndex, breakdown).let { candidates += it }
        buildReplacementSuggestions(session, exercises, exerciseIndex, breakdown).let { candidates += it }
        buildVolumeTrimSuggestions(session, exercises, exerciseIndex, breakdown, gap).let { candidates += it }

        return candidates
            .filter { it.id !in dismissedIds && it.minutesSaved > 0 }
            .dedupeSuggestions()
            .sortedWith(
                compareBy<TimeCoachSuggestion> { suggestionPriority(it) }
                    .thenByDescending { it.minutesSaved }
                    .thenBy { fatigueCost(it.fatigueDelta) },
            )
            .take(MAX_SUGGESTIONS)
    }

    fun apply(session: Session, action: TimeCoachAction): Session = when (action) {
        is TimeCoachAction.ReduceRests -> session.transformExercisesLocal { exercise ->
            if (exercise.supersetGroupRefOrLegacyId() != null) exercise
            else exercise.copy(
                restTime = action.perExerciseTargetRests[exercise.id]
                    ?: minOf(exercise.restTime ?: action.targetRestSeconds, action.targetRestSeconds),
            )
        }
        is TimeCoachAction.CreateSuperset -> createSupersetPair(
            session = session,
            exerciseIdA = action.exerciseIdA,
            exerciseIdB = action.exerciseIdB,
            restBetween = action.restBetween,
            restAfter = action.restAfter,
        )
        is TimeCoachAction.ConvertToDropSets -> convertToDropDensity(session, action.exerciseId)
        is TimeCoachAction.ConvertToRestPause -> convertToRestPauseDensity(session, action.exerciseId)
        is TimeCoachAction.RemoveLastSet -> session.transformExercisesLocal { exercise ->
            if (exercise.id != action.exerciseId || exercise.sets.size <= 1) exercise
            else exercise.copy(sets = exercise.sets.dropLast(1))
        }
        is TimeCoachAction.ReplaceWithCompound -> replaceWithCompound(session, action)
        is TimeCoachAction.RemoveExercise -> removeExercise(session, action.exerciseId)
    }

    private fun buildRestSuggestions(
        session: Session,
        exercises: List<Exercise>,
        exerciseIndex: Map<String, ExerciseMuscleInfo>,
        breakdown: SessionTimeBreakdown,
        limitMinutes: Int,
    ): List<TimeCoachSuggestion> {
        val standalone = exercises.filter { it.supersetGroupRefOrLegacyId() == null }
        if (standalone.isEmpty()) return emptyList()
        val plan = buildDynamicRestPlan(
            session = session,
            standalone = standalone,
            exerciseIndex = exerciseIndex,
            breakdown = breakdown,
            limitMinutes = limitMinutes,
        ) ?: return emptyList()
        val minTarget = plan.values.minOrNull() ?: return emptyList()
        val action = TimeCoachAction.ReduceRests(
            targetRestSeconds = minTarget,
            perExerciseTargetRests = plan,
            alsoUpdateRuleDefaults = false,
        )
        val after = runCatching { apply(session, action) }.getOrNull() ?: return emptyList()
        val afterBreakdown = calculateSessionTimeBreakdown(
            exercises = after.allExercises(),
            supersetGroups = after.allSupersetGroups(),
            sessionWarmup = after.warmup,
        )
        val saved = breakdown.totalMinutes - afterBreakdown.totalMinutes
        if (saved <= 0) return emptyList()
        val aggressiveCount = standalone.count { restReductionProfile(it, resolveInfo(it, exerciseIndex)).priority <= 2 }
        val compoundCount = standalone.size - aggressiveCount
        return listOf(
            TimeCoachSuggestion(
                id = "coach_rest_dynamic",
                title = "Ajustar descansos justo hasta el límite",
                explanation = "Recorta más en monoarticulares viables ($aggressiveCount) y conserva más los multiarticulares ($compoundCount) para acercarte al presupuesto sin pasarte de agresivo.",
                minutesSaved = saved,
                fatigueDelta = TimeCoachFatigueDelta.SIMILAR,
                action = action,
            )
        )
    }

    private fun buildSupersetSuggestions(
        session: Session,
        exercises: List<Exercise>,
        exerciseIndex: Map<String, ExerciseMuscleInfo>,
        breakdown: SessionTimeBreakdown,
    ): List<TimeCoachSuggestion> {
        val free = exercises.filter { it.supersetGroupRefOrLegacyId() == null }
        if (free.size < 2) return emptyList()
        val used = mutableSetOf<String>()
        val out = mutableListOf<TimeCoachSuggestion>()
        for (i in free.indices) {
            if (out.size >= 3) break
            val a = free[i]
            if (a.id in used) continue
            if (a.sets.any { it.isDropSet || it.isRestPause }) continue
            val aInfo = resolveInfo(a, exerciseIndex)
            val partner = free.drop(i + 1).firstOrNull { b ->
                b.id !in used &&
                    b.sets.none { it.isDropSet || it.isRestPause } &&
                    areGoodSupersetPair(aInfo, resolveInfo(b, exerciseIndex))
            } ?: continue
            used += a.id
            used += partner.id
            val action = TimeCoachAction.CreateSuperset(a.id, partner.id)
            val saved = minutesSaved(session, breakdown, action) ?: continue
            if (saved <= 0) continue
            out += TimeCoachSuggestion(
                id = "coach_ss_${a.id}_${partner.id}",
                title = "Superserie: ${a.name} + ${partner.name}",
                explanation = "Empareja estaciones compatibles o funciones distintas para recortar descansos muertos.",
                minutesSaved = saved,
                fatigueDelta = TimeCoachFatigueDelta.SIMILAR,
                action = action,
            )
        }
        return out
    }

    private fun buildDensitySuggestions(
        session: Session,
        exercises: List<Exercise>,
        exerciseIndex: Map<String, ExerciseMuscleInfo>,
        breakdown: SessionTimeBreakdown,
    ): List<TimeCoachSuggestion> {
        val out = mutableListOf<TimeCoachSuggestion>()
        exercises
            .filter { it.sets.size >= 3 && it.sets.none { s -> s.isDropSet || s.isRestPause } }
            .take(3)
            .forEach { ex ->
                val info = resolveInfo(ex, exerciseIndex)
                if (info != null && primaryMuscles(info).size <= 1) {
                    val dropAction = TimeCoachAction.ConvertToDropSets(ex.id)
                    minutesSaved(session, breakdown, dropAction)?.takeIf { it > 0 }?.let { saved ->
                        out += TimeCoachSuggestion(
                            id = "coach_drop_${ex.id}",
                            title = "Densidad con drops: ${ex.name}",
                            explanation = "Concentra el trabajo del mismo músculo en menos tiempo con una serie densa.",
                            minutesSaved = saved,
                            fatigueDelta = TimeCoachFatigueDelta.HIGHER,
                            action = dropAction,
                        )
                    }
                }
                val rpAction = TimeCoachAction.ConvertToRestPause(ex.id)
                minutesSaved(session, breakdown, rpAction)?.takeIf { it > 0 }?.let { saved ->
                    out += TimeCoachSuggestion(
                        id = "coach_rp_${ex.id}",
                        title = "Rest-pause: ${ex.name}",
                        explanation = "Concentra el volumen en menos series con pausas cortas. Más exigente, más rápido.",
                        minutesSaved = saved,
                        fatigueDelta = TimeCoachFatigueDelta.HIGHER,
                        action = rpAction,
                    )
                }
            }
        return out
    }

    private fun buildReplacementSuggestions(
        session: Session,
        exercises: List<Exercise>,
        exerciseIndex: Map<String, ExerciseMuscleInfo>,
        breakdown: SessionTimeBreakdown,
    ): List<TimeCoachSuggestion> {
        val isolations = exercises.filter { ex ->
            val info = resolveInfo(ex, exerciseIndex)
            info != null && isIsolation(info) && ex.supersetGroupRefOrLegacyId() == null
        }
        if (isolations.size < 2) return emptyList()
        val out = mutableListOf<TimeCoachSuggestion>()
        for (i in isolations.indices) {
            if (out.size >= 2) break
            for (j in i + 1 until isolations.size) {
                if (out.size >= 2) break
                val a = isolations[i]
                val b = isolations[j]
                val aInfo = resolveInfo(a, exerciseIndex) ?: continue
                val bInfo = resolveInfo(b, exerciseIndex) ?: continue
                val exactMuscles = exactPrimarySignature(aInfo, bInfo)
                if (exactMuscles.isEmpty()) continue
                val curatedConstraint = curatedReplacementConstraint(aInfo, bInfo) ?: continue
                val replacement = findCommonReplacementForExactMuscles(
                    exactMuscles = exactMuscles,
                    movementPattern = commonMovementPattern(aInfo, bInfo),
                    replacementGroup = curatedConstraint.group,
                    requireCuratedGroup = curatedConstraint.requireCuratedGroup,
                    index = exerciseIndex,
                    excludeIds = setOf(
                        a.exerciseDbId.orEmpty(),
                        b.exerciseDbId.orEmpty(),
                    ),
                ) ?: continue
                val partId = session.parts.firstOrNull { p ->
                    p.exercises.any { it.id == a.id || it.id == b.id }
                }?.id
                val sets = max(a.sets.size, b.sets.size).coerceIn(2, 4)
                val reps = a.sets.firstOrNull()?.plannedRepAnchor() ?: 8
                val rest = minOf(a.restTime ?: 90, b.restTime ?: 90).coerceAtLeast(60)
                val action = TimeCoachAction.ReplaceWithCompound(
                    removeExerciseIds = listOf(a.id, b.id),
                    replacementDbId = replacement.id,
                    replacementName = replacement.name,
                    sets = sets,
                    reps = reps,
                    restSeconds = rest,
                    insertPartId = partId,
                )
                val saved = minutesSaved(session, breakdown, action) ?: continue
                if (saved <= 0) continue
                out += TimeCoachSuggestion(
                    id = "coach_replace_${a.id}_${b.id}_${replacement.id}",
                    title = "Reemplazar por ${replacement.name}",
                    explanation = "Reemplaza ${a.name} y ${b.name} por un ejercicio común que cubre exactamente esos músculos principales.",
                    minutesSaved = saved,
                    fatigueDelta = TimeCoachFatigueDelta.SIMILAR,
                    action = action,
                )
            }
        }
        return out
    }

    private fun buildVolumeTrimSuggestions(
        session: Session,
        exercises: List<Exercise>,
        exerciseIndex: Map<String, ExerciseMuscleInfo>,
        breakdown: SessionTimeBreakdown,
        gap: Int,
    ): List<TimeCoachSuggestion> {
        if (gap > 12) return emptyList()
        return exercises
            .asReversed()
            .filter { ex ->
                ex.sets.size > 1 &&
                    resolveInfo(ex, exerciseIndex)?.let { isIsolation(it) } == true
            }
            .take(2)
            .mapNotNull { ex ->
                val action = TimeCoachAction.RemoveLastSet(ex.id)
                val saved = minutesSaved(session, breakdown, action) ?: return@mapNotNull null
                if (saved <= 0) return@mapNotNull null
                TimeCoachSuggestion(
                    id = "coach_trim_${ex.id}",
                    title = "Quitar 1 serie de ${ex.name}",
                    explanation = "El gap es pequeño: recortar la última serie de un aislamiento suele bastar.",
                    minutesSaved = saved,
                    fatigueDelta = TimeCoachFatigueDelta.LOWER,
                    action = action,
                )
            }
    }

    private fun buildRedundantExerciseSuggestions(
        session: Session,
        exercises: List<Exercise>,
        exerciseIndex: Map<String, ExerciseMuscleInfo>,
        breakdown: SessionTimeBreakdown,
    ): List<TimeCoachSuggestion> {
        val out = mutableListOf<TimeCoachSuggestion>()
        for (i in exercises.indices) {
            val a = exercises[i]
            val aInfo = resolveInfo(a, exerciseIndex) ?: continue
            val aPattern = normalizedPattern(a, aInfo) ?: continue
            val aMuscles = primaryMuscles(aInfo)
            if (aMuscles.isEmpty()) continue
            for (j in i + 1 until exercises.size) {
                val b = exercises[j]
                val bInfo = resolveInfo(b, exerciseIndex) ?: continue
                val bPattern = normalizedPattern(b, bInfo) ?: continue
                val bMuscles = primaryMuscles(bInfo)
                if (aPattern != bPattern || aMuscles != bMuscles) continue
                val combinedSets = effectiveSetCount(a) + effectiveSetCount(b)
                if (combinedSets <= REDUNDANT_PATTERN_MIN_SETS) continue
                val toRemove = chooseRedundantExerciseToRemove(a, aInfo, b, bInfo)
                val action = TimeCoachAction.RemoveExercise(toRemove.id)
                val saved = minutesSaved(session, breakdown, action) ?: continue
                if (saved <= 0) continue
                val keep = if (toRemove.id == a.id) b else a
                out += TimeCoachSuggestion(
                    id = "coach_redundant_${a.id}_${b.id}",
                    title = "Volumen suficiente: quitar ${toRemove.name}",
                    explanation = "${a.name} y ${b.name} repiten patrón e implicación muscular. Con ${combinedSets.roundToInt()} series totales, ${keep.name} ya cubre el estímulo.",
                    minutesSaved = saved,
                    fatigueDelta = TimeCoachFatigueDelta.LOWER,
                    action = action,
                )
            }
        }
        return out
    }

    private fun buildDynamicRestPlan(
        session: Session,
        standalone: List<Exercise>,
        exerciseIndex: Map<String, ExerciseMuscleInfo>,
        breakdown: SessionTimeBreakdown,
        limitMinutes: Int,
    ): Map<String, Int>? {
        val initialGap = breakdown.totalMinutes - limitMinutes
        if (initialGap <= 0) return null
        val currentRests = standalone.associate { it.id to (it.restTime ?: 90) }.toMutableMap()
        val profiles = standalone.associate { exercise ->
            exercise.id to restReductionProfile(exercise, resolveInfo(exercise, exerciseIndex))
        }
        val floors = standalone.associate { exercise ->
            val current = exercise.restTime ?: 90
            val profile = profiles[exercise.id] ?: RestReductionProfile.DEFAULT
            exercise.id to minOf(current, profile.floorSeconds).coerceAtLeast(30)
        }
        val stepOrder = standalone.sortedWith(
            compareBy<Exercise> { profiles[it.id]?.priority ?: RestReductionProfile.DEFAULT.priority }
                .thenByDescending { (it.restTime ?: 90) - (floors[it.id] ?: 30) }
                .thenByDescending { effectiveSetCount(it) },
        )

        var bestAbove: Pair<Map<String, Int>, Int>? = null
        var bestAny: Pair<Map<String, Int>, Int>? = null
        var working = currentRests.toMap()
        val maxIterations = 256
        repeat(maxIterations) {
            val action = TimeCoachAction.ReduceRests(
                targetRestSeconds = working.values.minOrNull() ?: 30,
                perExerciseTargetRests = working,
            )
            val after = runCatching { apply(session, action) }.getOrNull() ?: return@repeat
            val afterBreakdown = calculateSessionTimeBreakdown(
                exercises = after.allExercises(),
                supersetGroups = after.allSupersetGroups(),
                sessionWarmup = after.warmup,
            )
            val diff = afterBreakdown.totalMinutes - limitMinutes
            if (bestAny == null || kotlin.math.abs(diff) < kotlin.math.abs(bestAny!!.second)) {
                bestAny = working.toMap() to diff
            }
            if (diff >= 0 && (bestAbove == null || diff < bestAbove!!.second)) {
                bestAbove = working.toMap() to diff
                if (diff == 0) return bestAbove!!.first
            }

            val nextExercise = stepOrder.firstOrNull { ex ->
                val current = working[ex.id] ?: 90
                val floor = floors[ex.id] ?: 30
                val step = profiles[ex.id]?.stepSeconds ?: 15
                current - step >= floor
            } ?: return@repeat

            val mutable = working.toMutableMap()
            val step = profiles[nextExercise.id]?.stepSeconds ?: 15
            mutable[nextExercise.id] = (mutable[nextExercise.id] ?: 90) - step
            working = mutable.toMap()
        }
        return bestAbove?.first ?: bestAny?.first
    }

    private fun minutesSaved(
        session: Session,
        before: SessionTimeBreakdown,
        action: TimeCoachAction,
    ): Int? {
        val afterSession = runCatching { apply(session, action) }.getOrNull() ?: return null
        val after = calculateSessionTimeBreakdown(
            exercises = afterSession.allExercises(),
            supersetGroups = afterSession.allSupersetGroups(),
            sessionWarmup = afterSession.warmup,
        )
        return before.totalMinutes - after.totalMinutes
    }

    private fun createSupersetPair(
        session: Session,
        exerciseIdA: String,
        exerciseIdB: String,
        restBetween: Int,
        restAfter: Int,
    ): Session {
        val ids = listOf(exerciseIdA, exerciseIdB)
        val all = session.allExercises()
        if (ids.any { id -> all.none { it.id == id } }) return session
        val groupId = "time_coach_ss_${UUID.randomUUID()}"
        val anchorPartId = session.parts.firstOrNull { part ->
            part.exercises.any { it.id == exerciseIdA }
        }?.id
        return SupersetRules.createSuperset(
            session = session,
            groupId = groupId,
            exerciseIds = ids,
            restBetweenExercises = restBetween,
            restAfterSuperset = restAfter,
            rounds = null,
            anchorPartId = anchorPartId,
            anchorExerciseId = exerciseIdA,
        )
    }

    private fun convertToDropDensity(session: Session, exerciseId: String): Session {
        return session.transformExercisesLocal { exercise ->
            if (exercise.id != exerciseId || exercise.sets.isEmpty()) return@transformExercisesLocal exercise
            val first = exercise.sets.first()
            val dense = first.copy(
                id = UUID.randomUUID().toString(),
                isDropSet = true,
                isRestPause = false,
                restPauses = emptyList(),
                dropSets = listOf(
                    DropSetData(weight = (first.weight ?: 0.0) * 0.85, reps = (first.plannedRepAnchor() ?: 8) / 2),
                    DropSetData(weight = (first.weight ?: 0.0) * 0.7, reps = (first.plannedRepAnchor() ?: 8) / 2),
                ),
            )
            exercise.copy(sets = listOf(dense))
        }
    }

    private fun convertToRestPauseDensity(session: Session, exerciseId: String): Session {
        return session.transformExercisesLocal { exercise ->
            if (exercise.id != exerciseId || exercise.sets.isEmpty()) return@transformExercisesLocal exercise
            val first = exercise.sets.first()
            val dense = first.copy(
                id = UUID.randomUUID().toString(),
                isRestPause = true,
                isDropSet = false,
                dropSets = emptyList(),
                restPauses = listOf(
                    RestPauseData(restTime = 15, reps = (first.plannedRepAnchor() ?: 8) / 2),
                    RestPauseData(restTime = 15, reps = (first.plannedRepAnchor() ?: 8) / 3),
                ),
            )
            exercise.copy(sets = listOf(dense))
        }
    }

    private fun replaceWithCompound(session: Session, action: TimeCoachAction.ReplaceWithCompound): Session {
        val remove = action.removeExerciseIds.toSet()
        val newExercise = Exercise(
            id = UUID.randomUUID().toString(),
            name = action.replacementName,
            exerciseDbId = action.replacementDbId,
            restTime = action.restSeconds,
            sets = (1..action.sets).map { idx ->
                ExerciseSet(
                    id = UUID.randomUUID().toString(),
                    targetReps = action.reps,
                )
            },
        )
        val partId = action.insertPartId
        return if (partId != null && session.parts.any { it.id == partId }) {
            session.copy(
                parts = session.parts.map { part ->
                    if (part.id != partId) {
                        part.copy(exercises = part.exercises.filterNot { it.id in remove })
                    } else {
                        val kept = part.exercises.filterNot { it.id in remove }
                        val insertAt = part.exercises.indexOfFirst { it.id in remove }.coerceAtLeast(0)
                        val mutable = kept.toMutableList()
                        mutable.add(insertAt.coerceIn(0, mutable.size), newExercise)
                        part.copy(exercises = mutable)
                    }
                },
                exercises = session.exercises.filterNot { it.id in remove },
                supersetGroups = session.supersetGroups.mapNotNull { g ->
                    val order = g.exerciseOrder.filterNot { it in remove }
                    if (order.size < 2) null else g.copy(exerciseOrder = order)
                },
            )
        } else {
            session.copy(
                parts = session.parts.map { part ->
                    part.copy(exercises = part.exercises.filterNot { it.id in remove })
                },
                exercises = session.exercises.filterNot { it.id in remove } + newExercise,
                supersetGroups = session.supersetGroups.mapNotNull { g ->
                    val order = g.exerciseOrder.filterNot { it in remove }
                    if (order.size < 2) null else g.copy(exerciseOrder = order)
                },
            )
        }
    }

    private fun removeExercise(session: Session, exerciseId: String): Session {
        return session.copy(
            exercises = session.exercises.filterNot { it.id == exerciseId },
            parts = session.parts.map { part ->
                part.copy(exercises = part.exercises.filterNot { it.id == exerciseId })
            },
            supersetGroups = session.supersetGroups.mapNotNull { group ->
                val nextOrder = group.exerciseOrder.filterNot { it == exerciseId }
                if (nextOrder.size < 2) null else group.copy(exerciseOrder = nextOrder)
            },
        )
    }

    private fun Session.transformExercisesLocal(transform: (Exercise) -> Exercise): Session =
        copy(
            exercises = exercises.map(transform),
            parts = parts.map { it.copy(exercises = it.exercises.map(transform)) },
        )

    private fun resolveInfo(
        exercise: Exercise,
        index: Map<String, ExerciseMuscleInfo>,
    ): ExerciseMuscleInfo? {
        val key = exercise.exerciseDbId?.lowercase()?.takeIf { it.isNotBlank() }
            ?: exercise.name.lowercase()
        return index[key] ?: index[exercise.name.lowercase()]
    }

    private fun isIsolation(info: ExerciseMuscleInfo): Boolean {
        val type = info.type?.lowercase().orEmpty()
        return type.contains("aislam") || type.contains("accessor") || type.contains("isolation")
    }

    private fun isCompound(info: ExerciseMuscleInfo): Boolean {
        val type = info.type?.lowercase().orEmpty()
        return type.contains("básic") || type.contains("basic") || type.contains("compuest") ||
            type.contains("compound") ||
            info.involvedMuscles.count { it.role == com.example.kpkn.data.models.MuscleRole.PRIMARY } >= 2
    }

    private fun restReductionProfile(exercise: Exercise, info: ExerciseMuscleInfo?): RestReductionProfile {
        if (info == null) return RestReductionProfile.DEFAULT
        info.restReductionProfile
            ?.trim()
            ?.uppercase()
            ?.let(curatedRestProfiles::get)
            ?.let { return it }
        val primaries = primaryMuscles(info)
        val type = info.type?.lowercase().orEmpty()
        val equipment = info.equipment?.lowercase().orEmpty()
        val pattern = normalizedPattern(exercise, info).orEmpty()
        val isMachine = equipment.contains("machine") || equipment.contains("maquina") || equipment.contains("máquina")
        val isCable = equipment.contains("cable") || equipment.contains("polea")
        val smallSingle = primaries.size == 1 && primaries.any {
            it.contains("biceps") || it.contains("triceps") || it.contains("delto")
        }
        val gluteMed = primaries.any { it.contains("gluteo medio") } &&
            (pattern.contains("abdu") || info.name.lowercase().contains("abdu"))
        return when {
            gluteMed -> RestReductionProfile.GLUTE_MED_ABDUCTION
            type.contains("aislam") && smallSingle && isMachine -> RestReductionProfile.ISOLATION_MACHINE_SMALL
            type.contains("aislam") && smallSingle && isCable -> RestReductionProfile.ISOLATION_CABLE_SMALL
            type.contains("aislam") || primaries.size == 1 -> RestReductionProfile.ISOLATION_GENERAL
            isCompound(info) && isMachine -> RestReductionProfile.MACHINE_COMPOUND
            isCompound(info) -> RestReductionProfile.FREE_WEIGHT_COMPOUND
            else -> RestReductionProfile.DEFAULT
        }
    }

    private fun exactPrimarySignature(vararg infos: ExerciseMuscleInfo): Set<String> {
        return infos
            .flatMap { info ->
                info.involvedMuscles
                    .filter { it.role == com.example.kpkn.data.models.MuscleRole.PRIMARY }
                    .map { normalizeMuscle(it.muscle, it.emphasis) }
            }
            .filter { it.isNotBlank() }
            .toSet()
    }

    private fun primaryMuscles(info: ExerciseMuscleInfo): Set<String> =
        info.involvedMuscles
            .filter { it.role == com.example.kpkn.data.models.MuscleRole.PRIMARY }
            .map { normalizeMuscle(it.muscle, it.emphasis) }
            .filter { it.isNotBlank() }
            .toSet()
            .ifEmpty {
                info.involvedMuscles.map { normalizeMuscle(it.muscle, it.emphasis) }.filter { it.isNotBlank() }.toSet()
            }

    private fun areGoodSupersetPair(
        a: ExerciseMuscleInfo?,
        b: ExerciseMuscleInfo?,
    ): Boolean {
        if (a == null || b == null) return true
        val sameStation = !a.equipment.isNullOrBlank() &&
            a.equipment.equals(b.equipment, ignoreCase = true)
        val antagonistic = antagonisticForces(a.force, b.force) ||
            antagonisticChains(a.chain, b.chain)
        val differentMuscles = primaryMuscles(a).intersect(primaryMuscles(b)).isEmpty()
        return sameStation || antagonistic || differentMuscles
    }

    private fun antagonisticForces(a: String?, b: String?): Boolean {
        val x = a?.lowercase().orEmpty()
        val y = b?.lowercase().orEmpty()
        if (x.isBlank() || y.isBlank()) return false
        val push = listOf("empuje", "push", "press")
        val pull = listOf("tirón", "pull", "row")
        return (push.any { it in x } && pull.any { it in y }) ||
            (pull.any { it in x } && push.any { it in y })
    }

    private fun antagonisticChains(a: String?, b: String?): Boolean {
        val x = a?.lowercase().orEmpty()
        val y = b?.lowercase().orEmpty()
        return (x.contains("anterior") && y.contains("posterior")) ||
            (x.contains("posterior") && y.contains("anterior"))
    }

    private fun findCompoundCovering(
        muscles: Set<String>,
        index: Map<String, ExerciseMuscleInfo>,
        excludeIds: Set<String>,
    ): ExerciseMuscleInfo? {
        return index.values
            .asSequence()
            .filter { it.id !in excludeIds }
            .filter { isCompound(it) }
            .map { info ->
                val cover = primaryMuscles(info)
                val overlap = cover.intersect(muscles).size
                info to overlap
            }
            .filter { it.second >= 1 && it.second >= (muscles.size / 2).coerceAtLeast(1) }
            .sortedByDescending { it.second }
            .map { it.first }
            .firstOrNull()
    }

    private fun findCommonReplacementForExactMuscles(
        exactMuscles: Set<String>,
        movementPattern: String?,
        replacementGroup: String?,
        requireCuratedGroup: Boolean,
        index: Map<String, ExerciseMuscleInfo>,
        excludeIds: Set<String>,
    ): ExerciseMuscleInfo? {
        return index.values
            .asSequence()
            .distinctBy { it.id }
            .filter { it.id !in excludeIds }
            .filter { isCommonExercise(it) }
            .filter { primaryMuscles(it) == exactMuscles }
            .filter { candidate ->
                movementPattern == null || normalizedPattern(candidate) == movementPattern
            }
            .filter { candidate ->
                when {
                    replacementGroup != null -> candidate.replacementGroup == replacementGroup
                    requireCuratedGroup -> false
                    else -> true
                }
            }
            .sortedWith(
                compareBy<ExerciseMuscleInfo> { it.replacementPriority ?: Int.MAX_VALUE }
                    .thenByDescending { popularityScore(it) }
                    .thenByDescending { it.bodybuildingScore ?: 0.0 },
            )
            .firstOrNull()
    }

    private fun isCommonExercise(info: ExerciseMuscleInfo): Boolean {
        info.isCommon?.let { return it }
        val equipment = info.equipment?.lowercase().orEmpty()
        val name = info.name.lowercase()
        val commonEquipment = equipment.isBlank() || COMMON_EQUIPMENT.any { it in equipment }
        val notNicheByName = NICHE_NAME_TOKENS.none { it in name }
        val notAdvancedTier = info.tier?.uppercase() !in setOf("T0", "T4")
        return commonEquipment && notNicheByName && notAdvancedTier && popularityScore(info) >= 4
    }

    private data class CuratedReplacementConstraint(
        val group: String?,
        val requireCuratedGroup: Boolean,
    )

    private fun curatedReplacementConstraint(
        a: ExerciseMuscleInfo,
        b: ExerciseMuscleInfo,
    ): CuratedReplacementConstraint? {
        val ga = a.replacementGroup?.trim()?.takeIf { it.isNotBlank() }
        val gb = b.replacementGroup?.trim()?.takeIf { it.isNotBlank() }
        return when {
            ga == null && gb == null -> CuratedReplacementConstraint(group = null, requireCuratedGroup = false)
            ga != null && gb != null && ga == gb ->
                CuratedReplacementConstraint(group = ga, requireCuratedGroup = true)
            else -> null
        }
    }

    private fun popularityScore(info: ExerciseMuscleInfo): Int {
        var score = 0
        if ((info.bodybuildingScore ?: 0.0) >= 7.5) score += 4
        if ((info.communityOpinion?.size ?: 0) >= 2) score += 3
        if (COMMON_EQUIPMENT.any { it in info.equipment?.lowercase().orEmpty() }) score += 2
        if (info.name.length <= 28) score += 1
        return score
    }

    private fun normalizedPattern(exercise: Exercise, info: ExerciseMuscleInfo): String? {
        return exercise.selectedMovementPattern?.trim()?.lowercase()?.takeIf { it.isNotBlank() }
            ?: normalizedPattern(info)
    }

    private fun normalizedPattern(info: ExerciseMuscleInfo): String? {
        return info.movementPattern?.trim()?.lowercase()?.takeIf { it.isNotBlank() }
            ?: info.force?.trim()?.lowercase()?.takeIf { it.isNotBlank() }
    }

    private fun commonMovementPattern(a: ExerciseMuscleInfo, b: ExerciseMuscleInfo): String? {
        val aPattern = normalizedPattern(a)
        val bPattern = normalizedPattern(b)
        return if (aPattern == bPattern) aPattern else null
    }

    private fun chooseRedundantExerciseToRemove(
        a: Exercise,
        aInfo: ExerciseMuscleInfo,
        b: Exercise,
        bInfo: ExerciseMuscleInfo,
    ): Exercise {
        val aScore = keepScore(a, aInfo)
        val bScore = keepScore(b, bInfo)
        return if (aScore >= bScore) b else a
    }

    private fun keepScore(exercise: Exercise, info: ExerciseMuscleInfo): Double {
        var score = 0.0
        score += popularityScore(info).toDouble()
        score += effectiveSetCount(exercise)
        score += (info.bodybuildingScore ?: 0.0) / 2.0
        return score
    }

    private fun effectiveSetCount(exercise: Exercise): Double {
        return exercise.sets.count { !it.isIneffective }.coerceAtLeast(1).toDouble()
    }

    private fun normalizeMuscle(muscle: String, emphasis: String?): String {
        val raw = muscle.trim().lowercase()
        val emph = emphasis?.trim()?.lowercase().orEmpty()
        return when {
            raw.contains("glute") && (emph.contains("med") || emph.contains("medio")) -> "gluteo medio"
            raw.contains("glute") && (emph.contains("max") || emph.contains("mayor")) -> "gluteo mayor"
            else -> raw
        }
    }

    private fun List<TimeCoachSuggestion>.dedupeSuggestions(): List<TimeCoachSuggestion> {
        val seen = linkedMapOf<String, TimeCoachSuggestion>()
        for (suggestion in this) {
            val key = dedupeKey(suggestion)
            val current = seen[key]
            if (current == null || isBetterSuggestion(suggestion, current)) {
                seen[key] = suggestion
            }
        }
        return seen.values.toList()
    }

    private fun dedupeKey(suggestion: TimeCoachSuggestion): String = when (val action = suggestion.action) {
        is TimeCoachAction.ReduceRests -> "rest"
        is TimeCoachAction.CreateSuperset -> {
            val ids = listOf(action.exerciseIdA, action.exerciseIdB).sorted()
            "superset:${ids.joinToString("|")}"
        }
        is TimeCoachAction.ConvertToDropSets -> "density:${action.exerciseId}"
        is TimeCoachAction.ConvertToRestPause -> "density:${action.exerciseId}"
        is TimeCoachAction.RemoveLastSet -> "trim:${action.exerciseId}"
        is TimeCoachAction.ReplaceWithCompound -> {
            val ids = action.removeExerciseIds.sorted()
            "replace:${ids.joinToString("|")}"
        }
        is TimeCoachAction.RemoveExercise -> "remove:${action.exerciseId}"
    }

    private fun isBetterSuggestion(a: TimeCoachSuggestion, b: TimeCoachSuggestion): Boolean {
        return suggestionPriority(a) < suggestionPriority(b) ||
            (suggestionPriority(a) == suggestionPriority(b) && a.minutesSaved > b.minutesSaved)
    }

    private fun suggestionPriority(suggestion: TimeCoachSuggestion): Int = when (suggestion.action) {
        is TimeCoachAction.ReduceRests -> 0
        is TimeCoachAction.RemoveExercise -> 1
        is TimeCoachAction.RemoveLastSet -> 2
        is TimeCoachAction.CreateSuperset -> 3
        is TimeCoachAction.ReplaceWithCompound -> 4
        is TimeCoachAction.ConvertToRestPause -> 5
        is TimeCoachAction.ConvertToDropSets -> 6
    }

    private fun fatigueCost(delta: TimeCoachFatigueDelta): Int = when (delta) {
        TimeCoachFatigueDelta.LOWER -> 0
        TimeCoachFatigueDelta.SIMILAR -> 1
        TimeCoachFatigueDelta.HIGHER -> 2
    }

    private fun formatRest(seconds: Int): String {
        val m = seconds / 60
        val s = seconds % 60
        return if (m > 0 && s == 0) "${m}m" else if (m > 0) "${m}:${"%02d".format(s)}" else "${s}s"
    }
}
