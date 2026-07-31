package com.example.kpkn.domain.exercises

import com.example.kpkn.data.exercises.resolveExerciseId
import com.example.kpkn.data.models.CompletedExercise
import com.example.kpkn.data.models.Exercise
import com.example.kpkn.data.models.ExerciseMuscleInfo
import com.example.kpkn.data.models.ExerciseSet
import com.example.kpkn.data.models.ExerciseRelationshipType
import com.example.kpkn.data.models.ExerciseDiscomfortReport
import com.example.kpkn.data.models.ExerciseSetupDetails
import com.example.kpkn.data.models.TrainingMode
import com.example.kpkn.data.models.LoadModeV2
import com.example.kpkn.data.models.OngoingWorkoutState
import com.example.kpkn.data.models.Program
import com.example.kpkn.data.models.Session
import com.example.kpkn.data.models.SessionPart
import com.example.kpkn.data.models.UnitModeV2
import com.example.kpkn.data.models.WorkoutLog
import java.text.Normalizer

private val exerciseIdentityStripRegex = Regex("\\p{Mn}+")
private val exerciseIdentitySeparatorRegex = Regex("[^\\p{L}\\p{Nd}]+")

fun normalizeExerciseIdentityToken(value: String): String {
    if (value.isBlank()) return ""
    val stripped = Normalizer.normalize(value.trim(), Normalizer.Form.NFD)
        .replace(exerciseIdentityStripRegex, "")
    return stripped
        .lowercase()
        .replace(exerciseIdentitySeparatorRegex, " ")
        .trim()
}

private fun normalizeCanonicalId(value: String?): String? =
    value
        ?.trim()
        ?.lowercase()
        ?.takeIf { it.isNotBlank() }

fun resolveCanonicalExerciseId(
    explicitCanonicalId: String?,
    exerciseDbId: String?,
    exerciseId: String?,
    exerciseName: String,
    fallbackId: String? = null,
): String {
    normalizeCanonicalId(explicitCanonicalId)?.let { return it }

    resolveExerciseId(exerciseDbId ?: exerciseId)?.let { return it }

    normalizeCanonicalId(exerciseDbId)?.let { return it }

    val normalizedName = normalizeExerciseIdentityToken(exerciseName)
    if (normalizedName.isNotBlank()) {
        return "custom:$normalizedName"
    }

    normalizeCanonicalId(exerciseId)?.let { return "legacy:$it" }
    normalizeCanonicalId(fallbackId)?.let { return "local:$it" }
    return "unknown"
}

private fun normalizeRelationAnchorId(value: String?): String? =
    value
        ?.trim()
        ?.lowercase()
        ?.takeIf { it.isNotBlank() }

fun Exercise.resolvedCanonicalExerciseId(): String = resolveCanonicalExerciseId(
    explicitCanonicalId = canonicalExerciseId,
    exerciseDbId = exerciseDbId,
    exerciseId = exerciseId,
    exerciseName = name,
    fallbackId = id,
)

fun CompletedExercise.resolvedCanonicalExerciseId(): String = resolveCanonicalExerciseId(
    explicitCanonicalId = canonicalExerciseId,
    exerciseDbId = exerciseDbId,
    exerciseId = exerciseId,
    exerciseName = exerciseName,
    fallbackId = exerciseId,
)

fun Exercise.resolvedExerciseFamilyId(): String =
    normalizeRelationAnchorId(exerciseFamilyId) ?: resolvedCanonicalExerciseId()

fun Exercise.resolvedRelationAnchorId(): String =
    normalizeRelationAnchorId(relativeToCanonicalExerciseId) ?: resolvedCanonicalExerciseId()

fun CompletedExercise.resolvedRelationAnchorId(): String =
    normalizeRelationAnchorId(relativeToCanonicalExerciseId) ?: resolvedCanonicalExerciseId()

fun Exercise.analyticsExerciseKey(): String = "exercise:${resolvedCanonicalExerciseId()}"

fun CompletedExercise.analyticsExerciseKey(): String = "exercise:${resolvedCanonicalExerciseId()}"

fun Exercise.analyticsAnchorKey(): String = "anchor:${resolvedRelationAnchorId()}"

fun CompletedExercise.analyticsAnchorKey(): String = "anchor:${resolvedRelationAnchorId()}"

fun Exercise.normalizedIdentityFields(): Exercise {
    val canonicalId = resolvedCanonicalExerciseId()
    val relationAnchor = normalizeRelationAnchorId(relativeToCanonicalExerciseId)
        ?.takeIf { it != canonicalId }
    return copy(
        canonicalExerciseId = canonicalId,
        exerciseFamilyId = normalizeRelationAnchorId(exerciseFamilyId) ?: canonicalId,
        relativeToCanonicalExerciseId = relationAnchor,
        relationshipNotes = relationshipNotes?.trim()?.takeIf { it.isNotBlank() },
    )
}

fun CompletedExercise.normalizedIdentityFields(): CompletedExercise {
    val canonicalId = resolvedCanonicalExerciseId()
    val relationAnchor = normalizeRelationAnchorId(relativeToCanonicalExerciseId)
        ?.takeIf { it != canonicalId }
    return copy(
        canonicalExerciseId = canonicalId,
        relativeToCanonicalExerciseId = relationAnchor,
    )
}

fun ExerciseDiscomfortReport.normalizedIdentityFields(): ExerciseDiscomfortReport {
    val canonicalId = resolveCanonicalExerciseId(
        explicitCanonicalId = canonicalExerciseId,
        exerciseDbId = exerciseDbId,
        exerciseId = exerciseId,
        exerciseName = exerciseName,
        fallbackId = exerciseId,
    )
    return copy(canonicalExerciseId = canonicalId)
}

fun Session.normalizedIdentityFields(): Session = copy(
    exercises = exercises.map { it.normalizedIdentityFields() },
    parts = parts.map { part ->
        SessionPart(
            id = part.id,
            name = part.name,
            exercises = part.exercises.map { it.normalizedIdentityFields() },
            color = part.color,
        )
    },
    sessionB = sessionB?.normalizedIdentityFields(),
    sessionC = sessionC?.normalizedIdentityFields(),
    sessionD = sessionD?.normalizedIdentityFields(),
    trainingBackup = trainingBackup?.copy(
        exercises = trainingBackup.exercises.map { it.normalizedIdentityFields() },
        parts = trainingBackup.parts.map { backupPart ->
            SessionPart(
                id = backupPart.id,
                name = backupPart.name,
                exercises = backupPart.exercises.map { it.normalizedIdentityFields() },
                color = backupPart.color,
            )
        },
    ),
)

fun Program.normalizedIdentityFields(): Program = copy(
    macrocycles = macrocycles.map { macro ->
        macro.copy(
            blocks = macro.blocks.map { block ->
                block.copy(
                    mesocycles = block.mesocycles.map { meso ->
                        meso.copy(
                            weeks = meso.weeks.map { week ->
                                week.copy(
                                    sessions = week.sessions.map { it.normalizedIdentityFields() },
                                )
                            },
                        )
                    },
                )
            },
        )
    },
)

fun WorkoutLog.normalizedIdentityFields(): WorkoutLog = copy(
    completedExercises = completedExercises.map { it.normalizedIdentityFields() },
    postExerciseReports = postExerciseReports.map { it.normalizedIdentityFields() },
)

fun OngoingWorkoutState.normalizedIdentityFields(): OngoingWorkoutState = copy(
    session = session.normalizedIdentityFields(),
)

fun Exercise.replacedWithCatalogExercise(
    info: ExerciseMuscleInfo,
    selectedAspects: Map<String, String>? = null,
    variantName: String? = null,
    variantGroupId: String? = null,
    variantGroupName: String? = null,
): Exercise {
    val setup = info.setupDetails?.let {
        ExerciseSetupDetails(
            seatPosition = it.seatPosition,
            pinPosition = it.pinPosition,
            equipmentNotes = it.equipmentNotes,
        )
    }
    val canonicalId = resolveCanonicalExerciseId(
        explicitCanonicalId = info.id,
        exerciseDbId = info.id,
        exerciseId = info.id,
        exerciseName = info.name,
        fallbackId = id,
    )
    val defaultLoadMode = defaultReplacementLoadMode(info)
    val effectiveMuscles = if (selectedAspects != null && !info.technicalAspects.isNullOrEmpty()) {
        val selectedOptions = selectedAspects.mapNotNull { (aspectId, optId) ->
            info.technicalAspects
                ?.firstOrNull { it.id == aspectId }
                ?.options
                ?.firstOrNull { it.id == optId }
        }
        TechnicalAspectEngine.computeEffectiveMuscles(
            baseMuscles = info.involvedMuscles,
            selectedOptions = selectedOptions,
        ).effectiveMuscles
    } else {
        null
    }
    return copy(
        name = info.name,
        exerciseDbId = info.id,
        exerciseId = info.id,
        canonicalExerciseId = canonicalId,
        exerciseFamilyId = canonicalId,
        trainingMode = TrainingMode.REPS,
        relativeToCanonicalExerciseId = null,
        relationshipType = null,
        relationshipNotes = null,
        sets = sets.map { it.resetForCatalogReplacement(defaultLoadMode) },
        warmupSets = emptyList(),
        reference1RM = null,
        targetSessionGoal = null,
        isStarTarget = false,
        setupDetails = setup,
        variantName = variantName ?: info.variantName,
        variantGroupId = variantGroupId ?: info.variantGroupId,
        variantGroupName = variantGroupName ?: info.variantGroupName,
        selectedAspects = selectedAspects,
        effectiveMuscles = effectiveMuscles,
        prFor1RM = null,
        consolidatedWeight = null,
        brandEquivalencies = emptyList(),
        goal1RM = null,
        calculated1RM = null,
        selectedMovementPattern = info.movementPattern,
        selectedExecutionOption = info.executionOptions?.firstOrNull(),
        setupCues = info.setupCues.orEmpty(),
        executionCues = info.executionCues.orEmpty(),
        contextProfilesV3 = emptyList(),
        defaultContextProfileIdV3 = null,
    ).normalizedIdentityFields()
}

private fun defaultReplacementLoadMode(info: ExerciseMuscleInfo): LoadModeV2 {
    val equipment = info.equipment
        ?.let(::normalizeExerciseIdentityToken)
        .orEmpty()
    val name = normalizeExerciseIdentityToken(info.name)
    return when {
        equipment.contains("peso corporal") ||
            equipment.contains("bodyweight") ||
            equipment.contains("calistenia") -> LoadModeV2.BODYWEIGHT
        equipment.contains("asist") ||
            name.contains("asist") ||
            equipment.contains("assisted") ||
            name.contains("assisted") -> LoadModeV2.ASSISTED
        else -> LoadModeV2.LOAD
    }
}

private fun ExerciseSet.resetForCatalogReplacement(defaultLoadMode: LoadModeV2): ExerciseSet {
    val isTimeExercise = targetDuration != null && targetReps == null
    val newUnitMode = if (isTimeExercise) UnitModeV2.TIME else UnitModeV2.REPS
    return copy(
        weight = null,
        targetPercentageRM = null,
        intensityMode = null,
        completedReps = null,
        completedDuration = null,
        completedRPE = null,
        completedRIR = null,
        machineBrand = null,
        technicalWeight = null,
        consolidatedWeight = null,
        attemptResult = null,
        judgingLights = emptyList(),
        technicalQuality = null,
        discomfortIds = emptyList(),
        refereeNotes = null,
        loadModeV2 = defaultLoadMode,
        unitModeV2 = newUnitMode,
        targetReps = if (newUnitMode == UnitModeV2.REPS) (targetReps ?: 10) else null,
        targetDuration = if (newUnitMode == UnitModeV2.TIME) targetDuration else null,
        plannedTargetV2 = null,
        tagId = null,
        setupId = null,
        contextKeyV2 = null,
        contextProfileIdV3 = null,
        defaultTagIdV3 = null,
        defaultSetupProfileIdV3 = null,
        leftTarget = leftTarget?.copy(weight = null),
        rightTarget = rightTarget?.copy(weight = null),
        dropSets = emptyList(),
        restPauses = emptyList(),
        plannedIntensityTechniques = emptyList(),
    )
}

fun ExerciseRelationshipType.displayLabel(): String = when (this) {
    ExerciseRelationshipType.VARIATION -> "Variacion"
    ExerciseRelationshipType.ASSISTANCE -> "Asistencia"
    ExerciseRelationshipType.OVERLOAD -> "Sobrecarga"
    ExerciseRelationshipType.TECHNIQUE -> "Tecnica"
}
