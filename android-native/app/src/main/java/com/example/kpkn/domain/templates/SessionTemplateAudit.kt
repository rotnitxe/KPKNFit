package com.example.kpkn.domain.templates

import com.example.kpkn.data.exercises.resolveCatalogExerciseInfoInIndex
import com.example.kpkn.data.models.Exercise
import com.example.kpkn.data.models.ExerciseMuscleInfo
import com.example.kpkn.data.models.CardioDetails
import com.example.kpkn.data.models.MuscleRole
import com.example.kpkn.data.models.Session
import com.example.kpkn.data.sessions.SessionTemplate
import com.example.kpkn.domain.training.VolumeCalculator
import kotlin.math.abs
import kotlin.math.roundToInt

enum class SessionTemplateAuditIssueKind {
    INCONSISTENT_METADATA,
    ABSURD_VOLUME,
    INTENSITY_OUT_OF_RANGE,
    DURATION_DIVERGENT,
}

data class SessionTemplateAuditIssue(
    val kind: SessionTemplateAuditIssueKind,
    val message: String,
)

/**
 * Snapshot of structural / training metrics for a session template (or raw [Session]).
 *
 * Counts and durations are derived from content + [ExerciseMuscleInfo], not from
 * the display fields cached on [SessionTemplate].
 */
data class SessionTemplateAuditResult(
    val exercises: List<Exercise>,
    val totalSets: Int,
    val exerciseCount: Int,
    val partCount: Int,
    val uniqueExerciseIds: Set<String>,
    val averageTargetRpe: Double?,
    val primaryMuscleSets: Map<String, Int>,
    val estimatedDurationMinutes: Int,
    val issues: List<SessionTemplateAuditIssue>,
)

/**
 * Pure Kotlin audit engine for session templates.
 *
 * Resolves catalog entries via [exerciseDbId] / [exerciseId] / [canonicalExerciseId]
 * (case-insensitive) against the provided [exerciseIndex].
 */
object SessionTemplateAudit {

    const val DEFAULT_SETUP_SECONDS = 90
    const val DEFAULT_REST_SECONDS = 90
    const val EXECUTION_SECONDS_PER_SET = 45
    const val MIN_DURATION_MINUTES = 10

    /** Soft upper bound for a single-session set count before flagging absurd volume. */
    const val ABSURD_TOTAL_SETS_MAX = 40

    /** Soft upper bound for primary sets on one canonical muscle in a session. */
    const val ABSURD_PRIMARY_MUSCLE_SETS_MAX = 20

    /** Declared duration is "very divergent" beyond this relative gap. */
    const val DURATION_DIVERGENCE_RATIO = 0.40

    fun audit(
        template: SessionTemplate,
        exerciseIndex: Map<String, ExerciseMuscleInfo>,
    ): SessionTemplateAuditResult {
        val index = normalizeIndex(exerciseIndex)
        val metrics = computeMetrics(template.session, index)
        val issues = detectIssues(
            metrics = metrics,
            declaredExerciseCount = template.exerciseCount,
            declaredPartCount = template.partCount,
            declaredDurationMinutes = template.estimatedDurationMinutes,
            templateLabel = template.name,
        )
        return metrics.copy(issues = issues)
    }

    fun audit(
        session: Session,
        exerciseIndex: Map<String, ExerciseMuscleInfo>,
        declaredExerciseCount: Int? = null,
        declaredPartCount: Int? = null,
        declaredDurationMinutes: Int? = null,
        label: String = session.name,
    ): SessionTemplateAuditResult {
        val index = normalizeIndex(exerciseIndex)
        val metrics = computeMetrics(session, index)
        val issues = detectIssues(
            metrics = metrics,
            declaredExerciseCount = declaredExerciseCount,
            declaredPartCount = declaredPartCount,
            declaredDurationMinutes = declaredDurationMinutes,
            templateLabel = label,
        )
        return metrics.copy(issues = issues)
    }

    fun detectIssues(
        metrics: SessionTemplateAuditResult,
        declaredExerciseCount: Int? = null,
        declaredPartCount: Int? = null,
        declaredDurationMinutes: Int? = null,
        templateLabel: String = "sesión",
    ): List<SessionTemplateAuditIssue> {
        val issues = mutableListOf<SessionTemplateAuditIssue>()

        if (declaredExerciseCount != null && declaredExerciseCount != metrics.exerciseCount) {
            issues += SessionTemplateAuditIssue(
                kind = SessionTemplateAuditIssueKind.INCONSISTENT_METADATA,
                message = "'$templateLabel': exerciseCount declarado=$declaredExerciseCount, real=${metrics.exerciseCount}",
            )
        }
        if (declaredPartCount != null && declaredPartCount != metrics.partCount) {
            issues += SessionTemplateAuditIssue(
                kind = SessionTemplateAuditIssueKind.INCONSISTENT_METADATA,
                message = "'$templateLabel': partCount declarado=$declaredPartCount, real=${metrics.partCount}",
            )
        }

        if (metrics.exercises.isNotEmpty() && metrics.totalSets <= 0) {
            issues += SessionTemplateAuditIssue(
                kind = SessionTemplateAuditIssueKind.ABSURD_VOLUME,
                message = "'$templateLabel': hay ${metrics.exerciseCount} ejercicios pero 0 series",
            )
        }
        if (metrics.totalSets > ABSURD_TOTAL_SETS_MAX) {
            issues += SessionTemplateAuditIssue(
                kind = SessionTemplateAuditIssueKind.ABSURD_VOLUME,
                message = "'$templateLabel': volumen absurdo (${metrics.totalSets} series totales > $ABSURD_TOTAL_SETS_MAX)",
            )
        }
        metrics.primaryMuscleSets.forEach { (muscle, sets) ->
            if (sets > ABSURD_PRIMARY_MUSCLE_SETS_MAX) {
                issues += SessionTemplateAuditIssue(
                    kind = SessionTemplateAuditIssueKind.ABSURD_VOLUME,
                    message = "'$templateLabel': $sets series primarias en '$muscle' (máx $ABSURD_PRIMARY_MUSCLE_SETS_MAX)",
                )
            }
        }

        metrics.exercises.forEach { exercise ->
            exercise.sets.forEachIndexed { index, set ->
                val rpe = resolveSetTargetRpe(set.targetRPE, set.targetRIR)
                if (rpe != null && (rpe < 1.0 || rpe > 10.0)) {
                    issues += SessionTemplateAuditIssue(
                        kind = SessionTemplateAuditIssueKind.INTENSITY_OUT_OF_RANGE,
                        message = "'$templateLabel': '${exercise.name}' serie ${index + 1} con RPE $rpe fuera de 1–10",
                    )
                }
                val rir = set.targetRIR
                if (rir != null && (rir < 0 || rir > 10)) {
                    issues += SessionTemplateAuditIssue(
                        kind = SessionTemplateAuditIssueKind.INTENSITY_OUT_OF_RANGE,
                        message = "'$templateLabel': '${exercise.name}' serie ${index + 1} con RIR $rir fuera de 0–10",
                    )
                }
            }
        }

        if (declaredDurationMinutes != null && declaredDurationMinutes > 0 && metrics.estimatedDurationMinutes > 0) {
            val gap = abs(declaredDurationMinutes - metrics.estimatedDurationMinutes).toDouble()
            val ratio = gap / metrics.estimatedDurationMinutes.toDouble()
            if (ratio > DURATION_DIVERGENCE_RATIO) {
                issues += SessionTemplateAuditIssue(
                    kind = SessionTemplateAuditIssueKind.DURATION_DIVERGENT,
                    message = "'$templateLabel': duración declarada=${declaredDurationMinutes} min vs estimada=${metrics.estimatedDurationMinutes} min " +
                        "(desvío ${(ratio * 100).roundToInt()}% > ${(DURATION_DIVERGENCE_RATIO * 100).roundToInt()}%)",
                )
            }
        }

        return issues
    }

    fun resolveCatalogInfo(
        exercise: Exercise,
        exerciseIndex: Map<String, ExerciseMuscleInfo>,
    ): ExerciseMuscleInfo? = resolveCatalogInfoNormalized(
        exercise = exercise,
        exerciseIndex = normalizeIndex(exerciseIndex),
    )

    /** Resolves against an index that has already been normalized once by [normalizeIndex]. */
    internal fun resolveCatalogInfoNormalized(
        exercise: Exercise,
        exerciseIndex: Map<String, ExerciseMuscleInfo>,
    ): ExerciseMuscleInfo? {
        // Catalog-backed V2 exercises are resolved by their exact materialized
        // configuration. Name/alias matching remains available only for old
        // user payloads that do not carry V2 identity.
        if (!exercise.catalogRevision.isNullOrBlank()) {
            return exercise.catalogConfigurationId
                ?.trim()
                ?.lowercase()
                ?.let(exerciseIndex::get)
        }
        return resolveCatalogExerciseInfoInIndex(
            index = exerciseIndex,
            catalogConfigurationId = exercise.catalogConfigurationId,
            exerciseDbId = exercise.exerciseDbId,
            exerciseId = exercise.exerciseId,
            exerciseName = exercise.name,
        )
    }

    private fun computeMetrics(
        session: Session,
        exerciseIndex: Map<String, ExerciseMuscleInfo>,
    ): SessionTemplateAuditResult {
        val exercises = session.allExercises()
        val totalSets = exercises.sumOf { it.sets.size }
        val uniqueIds = linkedSetOf<String>()
        val primaryMuscleSets = linkedMapOf<String, Int>()
        val targetRpes = mutableListOf<Double>()
        var durationSeconds = 0

        fun mobilityDurationSeconds(series: List<com.example.kpkn.data.models.MobilitySeries>): Int =
            series.sumOf { item ->
                val seconds = item.durationSeconds?.takeIf { it > 0 } ?: 0
                seconds * item.sets.coerceAtLeast(1)
            }

        fun exerciseDurationSeconds(exercise: Exercise): Int {
            val info = resolveCatalogInfoNormalized(exercise, exerciseIndex)
            val setup = info?.setupTime?.takeIf { it > 0 } ?: DEFAULT_SETUP_SECONDS
            val executable = if (exercise.cardioDetails != null) {
                setup + cardioDurationSeconds(exercise.cardioDetails)
            } else {
                val setCount = exercise.sets.size
                val rest = exercise.restTime?.takeIf { it > 0 }
                    ?: info?.averageRestSeconds?.takeIf { it > 0 }
                    ?: DEFAULT_REST_SECONDS
                val execution = setCount * EXECUTION_SECONDS_PER_SET
                val restTotal = if (setCount > 1) rest * (setCount - 1) else 0
                setup + execution + restTotal + mobilityDurationSeconds(exercise.mobilitySeries)
            }
            // Exercise target is a minimum for that exercise, never an extra
            // block added on top of its executable estimate.
            return maxOf(executable, (exercise.targetDurationMinutes ?: 0).coerceAtLeast(0) * 60)
        }

        exercises.forEach { exercise ->
            val info = resolveCatalogInfoNormalized(exercise, exerciseIndex)
            val resolvedId = info?.id?.trim()?.lowercase()
                ?: listOfNotNull(
                    exercise.exerciseDbId,
                    exercise.exerciseId,
                    exercise.canonicalExerciseId,
                ).firstOrNull { it.isNotBlank() }?.trim()?.lowercase()
            if (resolvedId != null) uniqueIds += resolvedId

            val setCount = exercise.sets.size
            if (info != null && setCount > 0) {
                val primaryCanonical = info.involvedMuscles
                    .asSequence()
                    .filter { it.role == MuscleRole.PRIMARY }
                    .map { VolumeCalculator.normalizeCanonicalMuscleGroup(it.muscle, it.emphasis) }
                    .filter { it.isNotBlank() }
                    .toSet()
                primaryCanonical.forEach { muscle ->
                    primaryMuscleSets[muscle] = (primaryMuscleSets[muscle] ?: 0) + setCount
                }
            }

            exercise.sets.forEach { set ->
                resolveSetTargetRpe(set.targetRPE, set.targetRIR)?.let { targetRpes += it }
            }

        }

        // Sum loose exercises and parts separately so a part target/config acts
        // as a floor for that part instead of being counted twice.
        durationSeconds += session.exercises.sumOf(::exerciseDurationSeconds)
        session.parts.forEach { part ->
            val executable = part.exercises.sumOf(::exerciseDurationSeconds) +
                mobilityDurationSeconds(part.mobilitySeries)
            val contractual = maxOf(
                (part.targetDurationMinutes ?: 0).coerceAtLeast(0) * 60,
                (part.mobilityConfig?.totalMinutes ?: 0).coerceAtLeast(0) * 60,
            )
            durationSeconds += maxOf(executable, contractual)
        }

        // Warm-up is real scheduled work and must be included even when no
        // strength exercise is present in the session.
        session.warmup.forEach { warmup ->
            // Calculations.calculateSessionTimeBreakdown treats an explicit
            // duration as the total for this warm-up item, not per set.  When
            // it is absent, use the same 4 s/rep estimate plus the 15 s
            // transition between warm-up items.
            val itemSeconds = if ((warmup.duration ?: 0) > 0) {
                warmup.duration!!
            } else {
                val sets = warmup.sets?.coerceAtLeast(1) ?: 1
                val reps = warmup.reps?.filter(Char::isDigit)?.toIntOrNull() ?: 10
                sets * reps * 4
            }
            durationSeconds += itemSeconds + 15
        }

        // Session target wraps the whole schedule. It is a contractual floor,
        // not another block to sum with part/exercise targets.
        durationSeconds = maxOf(
            durationSeconds,
            (session.targetDurationMinutes ?: 0).coerceAtLeast(0) * 60,
        )

        val estimatedDurationMinutes = (durationSeconds / 60.0)
            .roundToInt()
            .coerceAtLeast(if (exercises.isEmpty()) 0 else MIN_DURATION_MINUTES)

        val averageTargetRpe = if (targetRpes.isEmpty()) {
            null
        } else {
            (targetRpes.average() * 10.0).roundToInt() / 10.0
        }

        return SessionTemplateAuditResult(
            exercises = exercises,
            totalSets = totalSets,
            exerciseCount = exercises.size,
            partCount = session.parts.size,
            uniqueExerciseIds = uniqueIds,
            averageTargetRpe = averageTargetRpe,
            primaryMuscleSets = primaryMuscleSets,
            estimatedDurationMinutes = estimatedDurationMinutes,
            issues = emptyList(),
        )
    }

    private fun resolveSetTargetRpe(targetRpe: Double?, targetRir: Int?): Double? {
        if (targetRpe != null) return targetRpe
        if (targetRir != null) return (10 - targetRir).toDouble()
        return null
    }

    private fun normalizeIndex(
        exerciseIndex: Map<String, ExerciseMuscleInfo>,
    ): Map<String, ExerciseMuscleInfo> {
        if (exerciseIndex.keys.all { it == it.trim().lowercase() }) return exerciseIndex
        return exerciseIndex.mapKeys { it.key.trim().lowercase() }
    }

    private fun cardioDurationSeconds(details: CardioDetails): Int {
        val hiit = details.hiit
        if (hiit != null) {
            val rounds = hiit.rounds.coerceAtLeast(1)
            val sets = hiit.sets.coerceAtLeast(1)
            val interval = hiit.workSeconds.coerceAtLeast(0) + hiit.restSeconds.coerceAtLeast(0)
            val betweenSets = (sets - 1).coerceAtLeast(0) * hiit.restBetweenSetsSeconds.coerceAtLeast(0)
            return hiit.warmupSeconds.coerceAtLeast(0) + hiit.cooldownSeconds.coerceAtLeast(0) +
                sets * (rounds * interval - hiit.restSeconds.coerceAtLeast(0)) + betweenSets
        }
        return details.effectiveDurationSeconds()
    }
}
