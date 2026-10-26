package com.example.kpkn.screens.workout

import com.example.kpkn.data.exercises.catalogv2.toResolvedCatalogSnapshotJson
import com.example.kpkn.data.exercises.resolveCatalogExerciseInfoInIndex
import com.example.kpkn.data.models.CompletedExercise
import com.example.kpkn.data.models.CompletedSet
import com.example.kpkn.data.models.Exercise
import com.example.kpkn.data.models.ExerciseMuscleInfo
import com.example.kpkn.data.models.Session
import com.example.kpkn.data.models.effectiveRepEquivalent
import com.example.kpkn.data.models.supersetGroupRefOrLegacyId
import com.example.kpkn.domain.exercises.exerciseDisplayParts
import com.example.kpkn.domain.exercises.resolvedCanonicalExerciseId
import com.example.kpkn.domain.workout.SupersetRules

/**
 * Single builder of [CompletedExercise] for live drain, finish log, and share preview.
 */
internal fun toCompletedExercises(
    session: Session,
    completedSets: Map<String, CompletedSet>,
    skippedExerciseIds: Set<String> = emptySet(),
    catalogIndex: Map<String, ExerciseMuscleInfo> = emptyMap(),
    includeCardioDetails: Boolean = true,
    capturedAtEpochMs: Long = System.currentTimeMillis(),
    includeWarmupSets: Boolean = false,
): List<CompletedExercise> {
    return session.allExercises().mapNotNull { exercise ->
        val sets = completedSetsForExercise(exercise, completedSets).filterNot { it.isWarmup } +
            if (includeWarmupSets) exercise.warmupSets.mapNotNull { warmup -> completedSets[WorkoutStepRules.warmupStepKey(exercise.id, warmup.id)] } else emptyList()
        if (sets.isEmpty()) {
            if (exercise.id in skippedExerciseIds) return@mapNotNull null
            return@mapNotNull null
        }
        val catalogInfo = resolveCatalogExerciseInfoInIndex(
            index = catalogIndex,
            catalogConfigurationId = exercise.catalogConfigurationId,
            exerciseDbId = exercise.exerciseDbId,
            exerciseId = exercise.exerciseId,
            exerciseName = exercise.name,
        )
        val groupId = exercise.supersetGroupRefOrLegacyId()
        CompletedExercise(
            exerciseId = exercise.id,
            exerciseName = exerciseDisplayParts(exercise, catalogInfo).text,
            exerciseDbId = exercise.exerciseDbId ?: exercise.exerciseId ?: exercise.resolvedCanonicalExerciseId(),
            catalogRevision = exercise.catalogRevision,
            catalogDefinitionId = exercise.catalogDefinitionId,
            catalogConfigurationId = exercise.catalogConfigurationId,
            performanceProfileId = exercise.performanceProfileId,
            occurrenceId = exercise.occurrenceId ?: exercise.id,
            cardioDetails = if (includeCardioDetails) exercise.cardioDetails else null,
            canonicalExerciseId = exercise.canonicalExerciseId ?: exercise.resolvedCanonicalExerciseId(),
            relativeToCanonicalExerciseId = exercise.relativeToCanonicalExerciseId,
            variantName = exercise.variantName,
            selectedAspects = exercise.selectedAspects,
            effectiveMuscles = exercise.effectiveMuscles,
            restTime = exercise.restTime ?: 90,
            supersetId = groupId,
            supersetExerciseCount = groupId?.let { SupersetRules.orderedMembers(session, it).size } ?: 1,
            supersetRounds = groupId?.let { SupersetRules.roundCount(session, it) },
            supersetRestBetween = exercise.supersetRestBetween,
            supersetRestAfter = exercise.supersetRestAfter,
            sets = sets,
            resolvedProfileSnapshotJson = catalogInfo?.let { info ->
                exercise.toResolvedCatalogSnapshotJson(info, capturedAtEpochMs)
            },
        )
    }
}

internal fun completedSetsForExercise(
    exercise: Exercise,
    completedSets: Map<String, CompletedSet>,
): List<CompletedSet> {
    if (exercise.cardioDetails != null) {
        val keyed = completedSets
            .filterKeys { it == exercise.id || it.startsWith("${exercise.id}_") }
            .values
            .toList()
        if (keyed.isNotEmpty()) return keyed
    }
    return exercise.sets.indices.flatMap { setIdx ->
        listOfNotNull(
            completedSets["${exercise.id}_$setIdx"]?.copy(setIndex = setIdx),
            completedSets["${exercise.id}_${setIdx}_L"]?.let { it.copy(setIndex = setIdx, side = it.side ?: "left") },
            completedSets["${exercise.id}_${setIdx}_R"]?.let { it.copy(setIndex = setIdx, side = it.side ?: "right") },
        )
    }
}

internal fun sessionTonnage(exercises: List<CompletedExercise>): Double =
    exercises.sumOf { ex ->
        ex.sets.filter { !it.skipped && !it.isWarmup }.sumOf { set ->
            val main = set.weight * set.effectiveRepEquivalent()
            val drops = set.dropSets.sumOf { drop ->
                val body = set.recordedPayloadV3?.bodyWeightSnapshot ?: 0.0
                val load = when (set.loadMode ?: set.recordedPayloadV3?.loadInputMode) {
                    com.example.kpkn.data.models.LoadModeV2.LASTRE -> body + drop.weight
                    com.example.kpkn.data.models.LoadModeV2.ASSISTED -> (body - drop.weight).coerceAtLeast(0.0)
                    com.example.kpkn.data.models.LoadModeV2.BODYWEIGHT -> body
                    else -> drop.weight
                }
                load * drop.reps
            }
            val restPauses = set.restPauses.sumOf { rp -> set.weight * rp.reps }
            main + drops + restPauses
        }
    }

internal fun sessionLogicalSetCount(
    session: Session,
    completedSets: Map<String, CompletedSet>,
): Int = session.allExercises().sumOf { logicalWorkingSetCount(it, completedSets) }

/** D1 helper name: one plan `setIdx` counts once even if L and R are both logged. */
internal fun logicalSetCount(
    completedSets: Map<String, CompletedSet>,
    session: Session,
): Int = sessionLogicalSetCount(session, completedSets)

internal fun logicalSetCountFromCompleted(exercises: List<CompletedExercise>): Int =
    exercises.sumOf { exercise ->
        val working = exercise.sets.filter { !it.skipped && !it.isWarmup }
        val identified = working.filter { it.sourceRef != null || it.setIndex != null }
        val legacy = working - identified.toSet()
        identified.distinctBy { set -> set.sourceRef?.let { "${it.variant}|${it.exerciseOccurrenceId}|${it.setId}" }
            ?: "ordinal:${set.setIndex}" }.size +
            legacy.count { it.side.isNullOrBlank() } +
            maxOf(legacy.count { it.side?.lowercase() in listOf("left", "l") },
                  legacy.count { it.side?.lowercase() in listOf("right", "r") })
    }
