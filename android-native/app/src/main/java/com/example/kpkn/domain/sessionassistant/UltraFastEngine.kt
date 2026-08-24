package com.example.kpkn.domain.sessionassistant

import com.example.kpkn.data.models.DropSetData
import com.example.kpkn.data.models.Exercise
import com.example.kpkn.data.models.ExerciseMuscleInfo
import com.example.kpkn.data.models.ExerciseSet
import com.example.kpkn.data.models.RestPauseData
import com.example.kpkn.data.models.Session
import com.example.kpkn.data.models.SupersetGroup
import com.example.kpkn.data.models.plannedRepAnchor
import com.example.kpkn.data.models.supersetGroupRefOrLegacyId
import com.example.kpkn.domain.calculations.calculateSessionTimeBreakdown
import java.util.UUID

object UltraFastEngine {

    private val NICHE_TOKENS = setOf(
        "zercher", "jefferson", "good morning", "buenos días", "buenos dias",
        "sissy", "guillotine", "landmine 180", "meadows", "z press", "behind the neck",
    )

    // ── Public API ───────────────────────────────────────────────────────

    fun preview(
        session: Session,
        exerciseIndex: Map<String, ExerciseMuscleInfo>,
        manualOverrides: Map<String, Boolean> = emptyMap(),
    ): UltraFastPreview {
        val beforeBd = calculateSessionTimeBreakdown(
            exercises = session.allExercises(),
            supersetGroups = session.allSupersetGroups(),
            sessionWarmup = session.warmup,
        )
        val result = applyInternal(session, exerciseIndex, manualOverrides)
        val afterBd = calculateSessionTimeBreakdown(
            exercises = result.transformedExercisesFlat,
            supersetGroups = result.supersetGroups,
            sessionWarmup = session.warmup,
        )
        val saved = (beforeBd.totalSeconds - afterBd.totalSeconds).coerceAtLeast(0)
        return result.preview.copy(
            beforeSeconds = beforeBd.totalSeconds,
            afterSeconds = afterBd.totalSeconds,
            savedSeconds = saved,
        )
    }

    fun apply(
        session: Session,
        exerciseIndex: Map<String, ExerciseMuscleInfo>,
        manualOverrides: Map<String, Boolean> = emptyMap(),
    ): UltraFastApplyResult {
        val result = applyInternal(session, exerciseIndex, manualOverrides)
        val beforeBd = calculateSessionTimeBreakdown(session.allExercises(), session.allSupersetGroups(), session.warmup)
        val afterBd = calculateSessionTimeBreakdown(result.transformedExercisesFlat, result.supersetGroups, session.warmup)
        val preview = result.preview.copy(
            beforeSeconds = beforeBd.totalSeconds,
            afterSeconds = afterBd.totalSeconds,
            savedSeconds = (beforeBd.totalSeconds - afterBd.totalSeconds).coerceAtLeast(0),
        )
        return UltraFastApplyResult(
            transformedExercises = result.transformedExercisesFlat,
            supersetGroups = result.supersetGroups,
            preview = preview,
        )
    }

    fun isProtectedBasic(
        exercise: Exercise,
        info: ExerciseMuscleInfo?,
    ): Boolean {
        val nameLower = exercise.name.lowercase()
        val infoNameLower = info?.name?.lowercase().orEmpty()
        val combinedName = "$nameLower $infoNameLower"
        val equipmentRaw = info?.equipment ?: ""
        val equipmentLower = equipmentRaw.lowercase()

        // Catalog id direct hit (fast path)
        val dbId = exercise.exerciseDbId?.lowercase()
        val cfgId = exercise.catalogConfigurationId?.lowercase()
        if (dbId != null && dbId in UltraFastConfig.PROTECTED_CATALOG_IDS) return true
        if (cfgId != null && cfgId in UltraFastConfig.PROTECTED_CATALOG_IDS) return true

        // Family + bar check
        val isSquat = UltraFastConfig.isSquatFamily(combinedName)
        val isDeadlift = UltraFastConfig.isDeadliftFamily(combinedName)
        val isBench = UltraFastConfig.isBenchFamily(combinedName) && combinedName.contains("plano")

        if (isSquat) {
            if ("búlgara" in combinedName || "bulgara" in combinedName) return true
            if ("zercher" in combinedName) return true
            return true
        }
        if (isDeadlift) {
            if ("zercher" in combinedName) return true
            val isBar = UltraFastConfig.isBarbellEquipment(equipmentLower, combinedName)
            return isBar || "peso muerto" in combinedName
        }
        if (isBench) {
            val isBar = UltraFastConfig.isBarbellEquipment(equipmentLower, combinedName)
            return isBar
        }
        return false
    }

    fun isDangerous(
        exercise: Exercise,
        info: ExerciseMuscleInfo?,
    ): Boolean {
        val nameLower = (exercise.name + " " + (info?.name ?: "")).lowercase()
        if (info?.tier?.uppercase() == "T0") return true
        if (UltraFastConfig.DANGEROUS_NAME_TOKENS.any { it in nameLower }) return true
        if (NICHE_TOKENS.any { it in nameLower }) return true
        if ((info?.technicalDifficulty ?: 0.0) >= 4.5 && (info?.type?.lowercase()?.contains("básico") == true)) return true
        return false
    }

    fun isIsolationEligible(
        exercise: Exercise,
        info: ExerciseMuscleInfo?,
    ): Boolean {
        if (info == null) {
            val nm = exercise.name.lowercase()
            val isoHints = listOf("curl", "extension", "elevac", "apertura", "vuelo", "patada", "cruce", "polea", "pec deck", "lateral")
            return isoHints.any { it in nm }
        }
        val type = info.type?.lowercase().orEmpty()
        val isIsol = type.contains("aislam") || type.contains("aislamiento") || type.contains("isolation") || type.contains("accessor")
        if (isIsol) return true
        val primaries = info.involvedMuscles.count { it.role == com.example.kpkn.data.models.MuscleRole.PRIMARY }
        if (primaries == 1 && info.articulationType?.uppercase() == "AISLADO") return true
        if (primaries == 1 && type.contains("accesor")) return true
        return false
    }

    // ── Internal ───────────────────────────────────────────────────────

    private data class InternalResult(
        val transformedExercisesFlat: List<Exercise>,
        val supersetGroups: List<SupersetGroup>,
        val preview: UltraFastPreview,
    )

    private fun applyInternal(
        session: Session,
        exerciseIndex: Map<String, ExerciseMuscleInfo>,
        manualOverrides: Map<String, Boolean>,
    ): InternalResult {
        val all = session.allExercises()
        val transformed = mutableListOf<Exercise>()
        val perExerciseChanges = mutableListOf<UltraFastExerciseChange>()

        for (ex in all) {
            val info = resolveInfo(ex, exerciseIndex)
            val isProtected = isProtectedBasic(ex, info)
            val isDanger = isDangerous(ex, info)
            val override = manualOverrides[ex.id]
            val allowDensifyOnProtected = override == true

            val beforeSets = ex.sets.size
            val beforeTech = techniqueLabel(ex)

            var afterExercise = ex
            var reason = UltraFastReason.ISOLATION_DENSIFIED
            var wasReduced = false
            var wasDensified = false

            when {
                (isProtected || isDanger) && !allowDensifyOnProtected -> {
                    val target = reduceTarget(beforeSets)
                    if (target < beforeSets) {
                        afterExercise = ex.copy(sets = ex.sets.take(target))
                        wasReduced = true
                        reason = if (isProtected) UltraFastReason.PROTECTED_BASIC else UltraFastReason.DANGEROUS_COMPLEX
                    } else {
                        reason = if (isProtected) UltraFastReason.PROTECTED_BASIC else UltraFastReason.DANGEROUS_COMPLEX
                    }
                }
                (isProtected || isDanger) && allowDensifyOnProtected -> {
                    afterExercise = densifyExercise(ex, info)
                    wasDensified = true
                    reason = UltraFastReason.MANUAL_OVERRIDE_ALLOWED
                }
                isIsolationEligible(ex, info) -> {
                    afterExercise = densifyExercise(ex, info)
                    wasDensified = true
                    reason = UltraFastReason.ISOLATION_DENSIFIED
                }
                else -> {
                    reason = UltraFastReason.ISOLATION_DENSIFIED
                }
            }

            transformed += afterExercise
            perExerciseChanges += UltraFastExerciseChange(
                exerciseId = ex.id,
                exerciseName = ex.name,
                beforeSets = beforeSets,
                afterSets = afterExercise.sets.size,
                beforeTechnique = beforeTech,
                afterTechnique = techniqueLabel(afterExercise),
                reason = reason,
                wasDensified = wasDensified,
                wasReduced = wasReduced,
            )
        }

        // Step 3: supersets for same machine (polea/smith) + antagonistic
        val supersetChanges = mutableListOf<UltraFastSupersetChange>()
        val supersetGroupsToAdd = mutableListOf<SupersetGroup>()

        val byMachine = mutableMapOf<String, MutableList<Exercise>>()
        for (ex in transformed) {
            val info = resolveInfo(ex, exerciseIndex)
            val equipmentRaw = info?.equipment ?: ""
            val key = UltraFastConfig.machineKeyForSuperset(equipmentRaw, null) ?: continue
            byMachine.getOrPut(key) { mutableListOf() } += ex
        }

        for ((machineKey, bucket) in byMachine) {
            if (bucket.size < 2) continue
            val free = bucket.filter { ex ->
                val orig = all.find { it.id == ex.id }
                orig?.supersetGroupRefOrLegacyId() == null
            }
            if (free.size < 2) continue
            val used = mutableSetOf<String>()
            for (i in free.indices) {
                val a = free[i]
                if (a.id in used) continue
                val aInfo = resolveInfo(a, exerciseIndex)
                val partnerIdx = free.indexOfFirst { b ->
                    b.id !in used && b.id != a.id && areGoodAntagonistPair(aInfo, resolveInfo(b, exerciseIndex))
                }
                if (partnerIdx < 0) continue
                val b = free[partnerIdx]
                used += a.id
                used += b.id
                val groupId = "ultra_fast_ss_${UUID.randomUUID()}"
                val rounds = maxOf(a.sets.size, b.sets.size).coerceAtLeast(1)
                val group = SupersetGroup(
                    id = groupId,
                    exerciseOrder = listOf(a.id, b.id),
                    restBetweenExercises = 30,
                    restAfterSuperset = 90,
                    rounds = rounds,
                    roundRestBetweenExercises = (0 until rounds).associateWith { 30 },
                    roundRestAfterSuperset = (0 until rounds).associateWith { 90 },
                )
                supersetGroupsToAdd += group
                supersetChanges += UltraFastSupersetChange(
                    exerciseIdA = a.id,
                    exerciseIdB = b.id,
                    nameA = a.name,
                    nameB = b.name,
                    machineKey = machineKey,
                )
            }
        }

        val finalSupersets = session.allSupersetGroups().toMutableList()
        val exerciseById = transformed.associateBy { it.id }.toMutableMap()
        for (group in supersetGroupsToAdd) {
            for (exId in group.exerciseOrder) {
                val ex = exerciseById[exId] ?: continue
                exerciseById[exId] = ex.copy(
                    supersetGroupRef = group.id,
                    supersetId = group.id,
                    supersetRestBetween = group.restBetweenExercises,
                    supersetRestAfter = group.restAfterSuperset,
                )
            }
            finalSupersets += group
        }
        val finalFlat = all.map { orig -> exerciseById[orig.id] ?: orig }

        val preview = UltraFastPreview(
            beforeSeconds = 0,
            afterSeconds = 0,
            savedSeconds = 0,
            perExercise = perExerciseChanges,
            supersets = supersetChanges,
        )

        return InternalResult(finalFlat, finalSupersets, preview)
    }

    private fun reduceTarget(n: Int): Int = when (n) {
        4 -> 2
        3 -> 2
        2 -> 1
        1 -> 1
        else -> when {
            n >= 5 -> 2
            n <= 0 -> 0
            else -> n.coerceAtMost(2)
        }
    }

    private fun densifyExercise(exercise: Exercise, info: ExerciseMuscleInfo?): Exercise {
        if (exercise.sets.isEmpty()) return exercise
        val first = exercise.sets.first()
        val anchor = first.plannedRepAnchor() ?: 8
        val weight = first.weight ?: 0.0
        val equipLower = (info?.equipment ?: "").lowercase()
        val nameLower = exercise.name.lowercase()
        val isPolea = UltraFastConfig.isPoleaEquipment(equipLower) || UltraFastConfig.isPoleaEquipment(nameLower)
        return if (isPolea) {
            val dense = first.copy(
                id = UUID.randomUUID().toString(),
                isRestPause = true,
                isDropSet = false,
                dropSets = emptyList(),
                restPauses = listOf(
                    RestPauseData(restTime = 15, reps = (anchor / 2).coerceAtLeast(3)),
                    RestPauseData(restTime = 15, reps = (anchor / 3).coerceAtLeast(2)),
                ),
            )
            exercise.copy(sets = listOf(dense))
        } else {
            val dense = first.copy(
                id = UUID.randomUUID().toString(),
                isDropSet = true,
                isRestPause = false,
                restPauses = emptyList(),
                dropSets = listOf(
                    DropSetData(weight = (weight * 0.85).let { if (it <= 0) 0.0 else it }, reps = (anchor / 2).coerceAtLeast(4)),
                    DropSetData(weight = (weight * 0.70).let { if (it <= 0) 0.0 else it }, reps = (anchor / 2).coerceAtLeast(4)),
                ),
            )
            exercise.copy(sets = listOf(dense))
        }
    }

    private fun techniqueLabel(exercise: Exercise): String {
        val s = exercise.sets.firstOrNull() ?: return "Normal"
        return when {
            s.isDropSet -> "Dropset"
            s.isRestPause -> "Rest-Pause"
            else -> "Normal"
        }
    }

    private fun resolveInfo(
        exercise: Exercise,
        index: Map<String, ExerciseMuscleInfo>,
    ): ExerciseMuscleInfo? {
        val key = exercise.exerciseDbId?.lowercase()?.takeIf { it.isNotBlank() }
            ?: exercise.catalogConfigurationId?.lowercase()?.takeIf { it.isNotBlank() }
            ?: exercise.name.lowercase()
        return index[key] ?: index[exercise.name.lowercase()] ?: index[exercise.exerciseId?.lowercase() ?: ""]
    }

    private fun areGoodAntagonistPair(
        a: ExerciseMuscleInfo?,
        b: ExerciseMuscleInfo?,
    ): Boolean {
        if (a == null || b == null) return false
        val antagonistic = antagonisticForces(a.force, b.force) || antagonisticChains(a.chain, b.chain)
        val differentMuscles = primaryMuscles(a).intersect(primaryMuscles(b)).isEmpty()
        return antagonistic || differentMuscles
    }

    private fun antagonisticForces(a: String?, b: String?): Boolean {
        val x = a?.lowercase().orEmpty()
        val y = b?.lowercase().orEmpty()
        if (x.isBlank() || y.isBlank()) return false
        val push = listOf("empuje", "push", "press")
        val pull = listOf("tirón", "tiron", "pull", "row", "remo")
        return (push.any { it in x } && pull.any { it in y }) ||
            (pull.any { it in x } && push.any { it in y })
    }

    private fun antagonisticChains(a: String?, b: String?): Boolean {
        val x = a?.lowercase().orEmpty()
        val y = b?.lowercase().orEmpty()
        return (x.contains("anterior") && y.contains("posterior")) ||
            (x.contains("posterior") && y.contains("anterior"))
    }

    private fun primaryMuscles(info: ExerciseMuscleInfo): Set<String> =
        info.involvedMuscles
            .filter { it.role == com.example.kpkn.data.models.MuscleRole.PRIMARY }
            .map { (it.muscle + " " + (it.emphasis ?: "")).trim().lowercase() }
            .filter { it.isNotBlank() }
            .toSet()
            .ifEmpty {
                info.involvedMuscles.map { (it.muscle + " " + (it.emphasis ?: "")).trim().lowercase() }.filter { it.isNotBlank() }.toSet()
            }
}

// ── Helpers for series technique editing ──────────────────────────────────

fun ExerciseSet.withTechnique(technique: SeriesTechnique): ExerciseSet = when (technique) {
    SeriesTechnique.NORMAL -> copy(
        isDropSet = false,
        isRestPause = false,
        dropSets = emptyList(),
        restPauses = emptyList(),
    )
    SeriesTechnique.DROPSET -> {
        val anchor = plannedRepAnchor() ?: 8
        val w = weight ?: 0.0
        copy(
            isDropSet = true,
            isRestPause = false,
            restPauses = emptyList(),
            dropSets = if (dropSets.isNotEmpty()) dropSets else listOf(
                DropSetData(weight = (w * 0.85).let { if (it <= 0) 0.0 else it }, reps = (anchor / 2).coerceAtLeast(4)),
                DropSetData(weight = (w * 0.70).let { if (it <= 0) 0.0 else it }, reps = (anchor / 2).coerceAtLeast(4)),
            ),
        )
    }
    SeriesTechnique.REST_PAUSE -> {
        val anchor = plannedRepAnchor() ?: 8
        copy(
            isRestPause = true,
            isDropSet = false,
            dropSets = emptyList(),
            restPauses = if (restPauses.isNotEmpty()) restPauses else listOf(
                RestPauseData(restTime = 15, reps = (anchor / 2).coerceAtLeast(3)),
                RestPauseData(restTime = 15, reps = (anchor / 3).coerceAtLeast(2)),
            ),
        )
    }
}

fun Exercise.withSeriesTechniqueRange(
    fromIdx: Int,
    toIdx: Int,
    technique: SeriesTechnique,
    onlyFuture: Boolean = false,
    currentIdx: Int = -1,
): Exercise {
    if (sets.isEmpty()) return this
    val safeFrom = fromIdx.coerceIn(0, sets.lastIndex)
    val safeTo = toIdx.coerceIn(safeFrom, sets.lastIndex)
    val newSets = sets.mapIndexed { idx, set ->
        if (idx < safeFrom || idx > safeTo) set
        else {
            if (onlyFuture && idx <= currentIdx) set
            else set.withTechnique(technique)
        }
    }
    return copy(sets = newSets)
}

fun Session.transformExercisesFlat(transform: (Exercise) -> Exercise): Session {
    return copy(
        exercises = exercises.map(transform),
        parts = parts.map { part -> part.copy(exercises = part.exercises.map(transform)) },
    )
}
