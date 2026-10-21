package com.example.kpkn.domain.training

import com.example.kpkn.data.models.ExerciseMuscleInfo
import com.example.kpkn.data.models.ExerciseSet
import com.example.kpkn.data.models.MuscleRole
import com.example.kpkn.data.models.Program
import com.example.kpkn.data.models.ProgramWeek
import com.example.kpkn.data.models.Session
import com.example.kpkn.data.models.resolveMuscleVolumeContribution
import com.example.kpkn.data.models.plannedRepAnchor
import com.example.kpkn.data.models.PostSessionFeedback
import com.example.kpkn.data.models.WorkoutLog
import com.example.kpkn.data.models.Exercise
import com.example.kpkn.data.models.InvolvedMuscle
import com.example.kpkn.domain.auge.AugeFatigueEngine
import com.example.kpkn.domain.exercises.ExerciseMuscleResolver
import com.example.kpkn.domain.exercises.MuscleHeadResolution
import com.example.kpkn.domain.exercises.exerciseDisplayName
import com.example.kpkn.domain.auge.AugeClassifiers

data class RoleSeparatedMuscleVolume(
    val directSets: Double = 0.0,
    val secondarySets: Double = 0.0,
    val stabilizerSets: Double = 0.0,
) {
    /** Indirect = secondary + stabilizer work; kept as a convenience getter. */
    val indirectSets: Double
        get() = secondarySets + stabilizerSets

    val totalSets: Double
        get() = directSets + indirectSets
}

data class MuscleVolumeEntry(
    val muscleId: String,
    val muscleName: String,
    val displayVolume: Double,
    val sets: Int,
)

data class CanonicalMuscleVolumeEntry(
    val muscleId: String,
    val muscleName: String,
    val weeklySets: Double,
)

enum class VolumeCountingMode {
    DIRECT, SECONDARY, STABILIZER;

    fun includes(role: MuscleRole): Boolean = when (role) {
        MuscleRole.PRIMARY -> true
        MuscleRole.SECONDARY -> this != DIRECT
        MuscleRole.STABILIZER -> this == STABILIZER
        MuscleRole.NEUTRALIZER -> false
    }
}

data class ExerciseVolumeContributor(
    val exerciseId: String,
    val exerciseName: String,
    val sets: Double,
    val effectiveSets: Int,
)

/** Exposición de una porción y un rol; varias porciones pueden compartir las mismas series. */
data class MusclePortionVolume(
    val name: String,
    val role: MuscleRole,
    val sets: Double,
    val exercises: List<ExerciseVolumeContributor>,
)

data class MuscleVolumeBreakdown(
    val muscleName: String,
    val sets: Double,
    val exercises: List<ExerciseVolumeContributor>,
    val portions: List<MusclePortionVolume>,
    val roles: RoleSeparatedMuscleVolume,
)

data class TrainingVolumeResult(
    val muscles: Map<String, MuscleVolumeBreakdown>,
    val unassignedCount: Int = 0,
) {
    fun weeklyEntries(): List<CanonicalMuscleVolumeEntry> = muscles.values.map { muscle ->
        CanonicalMuscleVolumeEntry(
            muscleId = muscle.muscleName.lowercase().replace(" ", "-"),
            muscleName = muscle.muscleName,
            weeklySets = muscle.sets,
        )
    }.sortedByDescending { it.weeklySets }
}

object VolumeCalculator {

    /** Muscles shown by the established volume UI. Other raw labels are never rows. */
    val standardVolumeMuscles: Set<String> = setOf(
        "Cuello",
        "Trapecio",
        "Deltoides",
        "Pectorales",
        "Bíceps",
        "Tríceps",
        "Antebrazo",
        "Dorsales",
        "Erectores Espinales",
        "Core",
        "Abdomen",
        "Glúteos",
        "Aductores",
        "Cuádriceps",
        "Isquiosurales",
        "Pantorrillas",
        "Romboides",
    )

    private val muscleNormalization = mapOf(
        "cuádriceps" to "Cuádriceps",
        "cuadriceps" to "Cuádriceps",
        "recto femoral" to "Cuádriceps",
        "vasto" to "Cuádriceps",
        "glúteo" to "Glúteos",
        "gluteo" to "Glúteos",
        "tensor de la fascia lata" to "Glúteos",
        "femoral" to "Isquiosurales",
        "semitendinoso" to "Isquiosurales",
        "semimembranoso" to "Isquiosurales",
        "isquio" to "Isquiosurales",
        "trapecio" to "Trapecio",
        "romboides" to "Trapecio",
        "dorsal" to "Dorsales",
        "dorsales" to "Dorsales",
        "lat" to "Dorsales",
        "redondo" to "Dorsales",
        "erector" to "Erectores Espinales",
        "lumbar" to "Erectores Espinales",
        "espalda" to "Dorsales",
        "tríceps" to "Tríceps",
        "triceps" to "Tríceps",
        "antebrazo" to "Antebrazo",
        "adductor" to "Aductores",
        "pectíneo" to "Aductores",
        "gemelo" to "Pantorrillas",
        "gastrocnemio" to "Pantorrillas",
        "sóleo" to "Pantorrillas",
        "soleo" to "Pantorrillas",
        "pantorrilla" to "Pantorrillas",
        "tibial anterior" to "Tibial Anterior",
        "cuello" to "Cuello",
        "cervical" to "Cuello",
        "pectoral" to "Pectorales",
        "pecho" to "Pectorales",
        "abdominal" to "Abdomen",
        "oblicuo" to "Abdomen",
        "core" to "Core",
    )

    @Deprecated(
        message = "Use normalizeCanonicalMuscleGroup instead. This function previously mapped 'core' → 'Abdomen' " +
            "and splits deltoid heads, breaking volume aggregation when used as a re-normalizer.",
        replaceWith = ReplaceWith("normalizeCanonicalMuscleGroup(specificMuscle, emphasis)"),
    )
    fun normalizeMuscleGroup(specificMuscle: String, emphasis: String? = null): String {
        if (specificMuscle.isBlank()) return ""

        val lower = specificMuscle.lowercase().trim()

        // Hombros: separación por cabezas (solo si contiene deltoides/hombro)
        if (lower.contains("deltoides") || lower.contains("hombro")) {
            if (lower.contains("posterior")) return "Deltoides Posterior"
            if (lower.contains("lateral") || lower.contains("medio")) return "Deltoides Lateral"
            if (lower.contains("anterior") || lower.contains("frontal")) return "Deltoides Anterior"
            return "Deltoides Anterior"
        }

        // Bíceps (excluir femoral)
        if ((lower.contains("bíceps") || lower.contains("biceps") || lower.contains("braquial")) && !lower.contains("femoral")) {
            return "Bíceps"
        }

        // Buscar en mapa de normalización
        for ((key, normalized) in muscleNormalization) {
            if (lower.contains(key)) return normalized
        }

        // Fallback: capitalizar
        return specificMuscle.replaceFirstChar { it.uppercase() }
    }

    fun normalizeCanonicalMuscleGroup(specificMuscle: String, emphasis: String? = null): String {
        if (specificMuscle.isBlank()) return ""

        val lower = specificMuscle
            .lowercase()
            .replace("-", " ")
            .replace("_", " ")
            .trim()
        val emphasisLower = emphasis?.lowercase()?.trim().orEmpty()

        if (lower.contains("deltoides") || lower.contains("hombro")) return "Deltoides"
        if ((lower.contains("bíceps") || lower.contains("biceps") || lower.contains("braquial")) && !lower.contains("femoral")) return "Bíceps"
        if (lower.contains("tríceps") || lower.contains("triceps")) return "Tríceps"
        if (lower.contains("antebrazo") || lower.contains("braquiorradial")) return "Antebrazo"
        if (lower.contains("trapecio")) return "Trapecio"
        if (lower.contains("romboides")) return "Romboides"
        if (lower.contains("dorsal") || lower.contains("redondo") || lower.contains("espalda") ||
            lower.contains("lat ") || lower.endsWith(" lat") || lower == "lat" || lower.startsWith("lat ") || lower.contains("lats")
        ) return "Dorsales"
        if (lower.contains("erector") || lower.contains("lumbar")) return "Erectores Espinales"
        if (lower.contains("pectoral") || lower.contains("pecho")) return "Pectorales"
        if (lower.contains("cuádriceps") || lower.contains("cuadriceps") || lower.contains("recto femoral") || lower.contains("vasto")) return "Cuádriceps"
        if (lower.contains("isquio") || lower.contains("femoral") || lower.contains("semitendinoso") || lower.contains("semimembranoso")) return "Isquiosurales"
        if (lower.contains("glúteo") || lower.contains("gluteo") || lower.contains("tensor de la fascia lata") || lower.contains("tensor fascia")) return "Glúteos"
        if (lower.contains("psoas")) return "Core"
        if (lower.contains("adductor") || lower.contains("aductor") || lower.contains("pectíneo") || lower.contains("pectineo")) return "Aductores"
        if (lower.contains("gemelo") || lower.contains("pantorrilla") || lower.contains("gastrocnemio") || lower.contains("sóleo") || lower.contains("soleo")) return "Pantorrillas"
        if (lower.contains("tibial anterior") || lower == "tibial") return "Tibial Anterior"
        if (lower.contains("cuello") || lower.contains("cervical")) return "Cuello"
        // Core: deep stabilizers — must be checked BEFORE Abdomen to avoid "core" → "Abdomen".
        if (lower == "core" || lower.contains("transverso") || lower.contains("serrato") || emphasisLower.contains("core")) return "Core"
        // Abdomen: rectus abdominis + obliques.
        if (lower.contains("abdominal") || lower.contains("abdomen") || lower.contains("oblicuo") || lower.contains("recto del abdomen")) return "Abdomen"

        return specificMuscle.trim().replaceFirstChar { it.uppercase() }
    }

    /**
     * Agrupa aportes por músculo canónico. Dentro del mismo grupo (p. ej. cabezas
     * del deltoides), toma el MÁXIMO de las activaciones declaradas — nunca suma,
     * para evitar contar "deltoides anterior + deltoides lateral" como 2.0 sets.
     *
     * Si el JSON declara dos músculos canónicos distintos en el mismo ejercicio
     * (ej. Core y Abdomen), ambos reciben su contribución por separado, respetando
     * la decisión de mantenerlos como grupos independientes.
     *
     * Las claves del mapa resultante son ya canónicas (via [normalizeCanonicalMuscleGroup]);
     * no volver a normalizarlas con [normalizeMuscleGroup].
     */
    fun buildPerExerciseMuscleContributions(
        involvedMuscles: List<com.example.kpkn.data.models.InvolvedMuscle>,
    ): Map<String, Double> {
        if (involvedMuscles.isEmpty()) return emptyMap()
        val grouped = linkedMapOf<String, Double>()
        involvedMuscles.forEach { involvement ->
            val canonicalMuscle = normalizeCanonicalMuscleGroup(involvement.muscle, involvement.emphasis)
            if (canonicalMuscle !in standardVolumeMuscles) return@forEach
            val contribution = resolveMuscleVolumeContribution(involvement)
            val current = grouped[canonicalMuscle] ?: 0.0
            if (contribution > current) {
                grouped[canonicalMuscle] = contribution
            }
        }
        return grouped.filterValues { it > 0.0 }
    }

    fun isStandardVolumeMuscle(involvement: com.example.kpkn.data.models.InvolvedMuscle): Boolean =
        normalizeCanonicalMuscleGroup(involvement.muscle, involvement.emphasis) in standardVolumeMuscles

    fun countEffectiveSets(exerciseSets: List<ExerciseSet>): Int {
        return effectiveVolumeSets(exerciseSets).size
    }

    private fun effectiveVolumeSets(sets: List<ExerciseSet>): List<ExerciseSet> {
        val active = sets.filterNot { it.isIneffective }
        val counted = active.filter { set ->
            when {
                set.completedReps != null -> set.completedReps > 0
                else -> (set.plannedRepAnchor() ?: 0) > 0 || (set.weight ?: 0.0) > 0.0
            }
        }
        return counted.ifEmpty { active }
    }

    /** Un cálculo para la barra, los ejercicios y las porciones; el máximo se toma por ocurrencia. */
    fun calculateVolume(
        sessions: List<Session>,
        exerciseIndex: Map<String, ExerciseMuscleInfo>,
        divisor: Double = 1.0,
        mode: VolumeCountingMode = VolumeCountingMode.STABILIZER,
        adjustByIntensity: Boolean = false,
    ): TrainingVolumeResult {
        val safeDivisor = divisor.takeIf { it.isFinite() && it > 0.0 } ?: 1.0
        val index = exerciseIndex.mapKeys { it.key.lowercase() }
        val totals = linkedMapOf<String, MutableMuscleVolume>()
        sessions.forEach { session ->
            session.allExercises().forEach exerciseLoop@ { exercise ->
                val sets = effectiveVolumeSets(exercise.sets)
                if (sets.isEmpty()) return@exerciseLoop
                val weightedSets = if (adjustByIntensity) sets.sumOf { set ->
                    AugeClassifiers.getEffectiveVolumeMultiplier(set.volumeRpe())
                } else sets.size.toDouble()
                val muscles = ExerciseMuscleResolver.effectiveMusclesForVolume(exercise, index)
                    .filter { mode.includes(it.role) }
                    .groupBy { normalizeCanonicalMuscleGroup(it.muscle, it.emphasis) }
                val exerciseKey = exercise.catalogConfigurationId?.takeIf { it.isNotBlank() }
                    ?: exercise.exerciseDbId?.takeIf { it.isNotBlank() }
                    ?: exercise.exerciseId?.takeIf { it.isNotBlank() } ?: exercise.id
                val displayName = exerciseDisplayName(exercise, index)
                muscles.forEach muscleLoop@ { (canonical, involvements) ->
                    if (canonical !in standardVolumeMuscles) return@muscleLoop
                    val winner = involvements.maxWithOrNull(
                        compareBy<InvolvedMuscle>({ resolveMuscleVolumeContribution(it) }, { -it.role.ordinal }),
                    ) ?: return@muscleLoop
                    val contribution = resolveMuscleVolumeContribution(winner)
                    if (contribution <= 0.0) return@muscleLoop
                    val total = totals.getOrPut(canonical) { MutableMuscleVolume() }
                    val value = weightedSets * contribution / safeDivisor
                    total.exercises.addContribution(exerciseKey, displayName, value, sets.size)
                    when (winner.role) {
                        MuscleRole.PRIMARY -> total.roles.direct += value
                        MuscleRole.SECONDARY -> total.roles.secondary += value
                        MuscleRole.STABILIZER -> total.roles.stabilizer += value
                        MuscleRole.NEUTRALIZER -> Unit
                    }
                    // Colapsan las filas repetidas dentro de esta ejecución; entre ejecuciones suman.
                    involvements.groupBy { MuscleHeadResolution.resolveDisplayHead(it.muscle, it.emphasis) to it.role }
                        .forEach portionLoop@ { (portion, rows) ->
                            val name = portion.first ?: return@portionLoop
                            val coefficient = rows.maxOf { resolveMuscleVolumeContribution(it) }
                            if (coefficient <= 0.0) return@portionLoop
                            total.portions.getOrPut(name to portion.second) { linkedMapOf() }
                                .addContribution(exerciseKey, displayName, weightedSets * coefficient / safeDivisor, sets.size)
                        }
                }
            }
        }
        return TrainingVolumeResult(totals.mapValues { (name, total) ->
            val exercises = total.exercises.values.sortedByDescending { it.sets }
            MuscleVolumeBreakdown(
                muscleName = name,
                sets = exercises.sumOf { it.sets },
                exercises = exercises,
                portions = total.portions.map { (portion, contributors) ->
                    val rows = contributors.values.sortedByDescending { it.sets }
                    MusclePortionVolume(portion.first, portion.second, rows.sumOf { it.sets }, rows)
                },
                roles = RoleSeparatedMuscleVolume(total.roles.direct, total.roles.secondary, total.roles.stabilizer),
            )
        })
    }

    fun calculateCompletedVolume(
        logs: List<WorkoutLog>,
        exerciseIndex: Map<String, ExerciseMuscleInfo>,
        window: TrainingWindow,
        mode: VolumeCountingMode = VolumeCountingMode.STABILIZER,
        adjustByIntensity: Boolean = false,
        programId: String? = null,
    ): TrainingVolumeResult {
        val selection = window.selectLogs(logs, programId)
        return calculateVolume(
            completedSessions(selection.logs), exerciseIndex, window.weekDivisor, mode, adjustByIntensity,
        ).copy(unassignedCount = selection.unassignedCount)
    }

    private fun ExerciseSet.volumeRpe(): Double = when {
        isFailure || intensityMode == com.example.kpkn.data.models.IntensityMode.FAILURE -> 10.0
        completedRPE != null -> completedRPE.coerceIn(1.0, 10.0)
        completedRIR != null -> (10 - completedRIR).toDouble().coerceIn(1.0, 10.0)
        targetRPE != null -> targetRPE.coerceIn(1.0, 10.0)
        targetRIR != null -> (10 - targetRIR).toDouble().coerceIn(1.0, 10.0)
        else -> 8.0
    }

    private class MutableMuscleVolume {
        val exercises = linkedMapOf<String, ExerciseVolumeContributor>()
        val portions = linkedMapOf<Pair<String, MuscleRole>, MutableMap<String, ExerciseVolumeContributor>>()
        val roles = MutableRoleVolume()
    }

    private fun MutableMap<String, ExerciseVolumeContributor>.addContribution(
        key: String, name: String, sets: Double, effectiveSets: Int,
    ) {
        val previous = this[key]
        this[key] = ExerciseVolumeContributor(key, name, (previous?.sets ?: 0.0) + sets, (previous?.effectiveSets ?: 0) + effectiveSets)
    }

    private fun volumeIndex(exerciseList: List<ExerciseMuscleInfo>, aliases: Map<String, String>): Map<String, ExerciseMuscleInfo> =
        exerciseList.associateBy { it.id.lowercase() }.toMutableMap().apply {
            aliases.forEach { (alias, canonical) ->
                this[canonical.lowercase()]?.let { putIfAbsent(alias.lowercase(), it) }
            }
        }

    /** El rol ganador explica el máximo canónico; los roles nunca duplican las series del padre. */
    fun calculateRoleSeparatedMuscleVolume(
        sessions: List<Session>,
        exerciseList: List<ExerciseMuscleInfo>,
        aliases: Map<String, String> = emptyMap(),
        divisor: Double = 1.0,
        adjustByIntensity: Boolean = false,
    ): Map<String, RoleSeparatedMuscleVolume> {
        return calculateVolume(sessions, volumeIndex(exerciseList, aliases), divisor, adjustByIntensity = adjustByIntensity)
            .muscles.mapValues { it.value.roles }
    }

    private data class MutableRoleVolume(
        var direct: Double = 0.0,
        var secondary: Double = 0.0,
        var stabilizer: Double = 0.0,
    )

    fun calculateUnifiedMuscleVolume(
        sessions: List<Session>,
        exerciseList: List<ExerciseMuscleInfo>,
        aliases: Map<String, String> = emptyMap(),
    ): List<MuscleVolumeEntry> {
        return calculateVolume(sessions, volumeIndex(exerciseList, aliases)).muscles.values
            .map { muscle ->
                MuscleVolumeEntry(
                    muscleId = muscle.muscleName.lowercase().replace(" ", "-"),
                    muscleName = muscle.muscleName,
                    displayVolume = muscle.sets,
                    sets = muscle.exercises.sumOf { it.effectiveSets },
                )
            }
            .sortedByDescending { it.displayVolume }
    }

    fun calculateCanonicalWeeklyMuscleVolume(
        program: Program,
        exerciseList: List<ExerciseMuscleInfo>,
    ): List<CanonicalMuscleVolumeEntry> {
        val weeks = program.macrocycles
            .flatMap { it.blocks }
            .flatMap { it.mesocycles }
            .flatMap { it.weeks }
        return calculateCanonicalWeeklyMuscleVolumeForWeeks(
            weeks = weeks,
            exerciseList = exerciseList,
            averageByWeek = false,
        )
    }

    fun calculateCanonicalWeeklyMuscleVolumeForWeeks(
        weeks: List<ProgramWeek>,
        exerciseList: List<ExerciseMuscleInfo>,
        averageByWeek: Boolean = false,
    ): List<CanonicalMuscleVolumeEntry> {
        val sessions = weeks.flatMap { it.sessions }
        val weekDivisor = if (averageByWeek) weeks.size.coerceAtLeast(1).toDouble() else 1.0
        return calculateCanonicalWeeklyMuscleVolumeForSessions(
            sessions = sessions,
            exerciseList = exerciseList,
            divisor = weekDivisor,
        )
    }

    fun calculateCanonicalWeeklyMuscleVolumeForSessions(
        sessions: List<Session>,
        exerciseList: List<ExerciseMuscleInfo>,
        divisor: Double = 1.0,
    ): List<CanonicalMuscleVolumeEntry> {
        return calculateVolume(sessions, volumeIndex(exerciseList, emptyMap()), divisor).weeklyEntries()
    }

    fun calculateMuscleVolume(
        sessions: List<Session>,
        exerciseList: List<ExerciseMuscleInfo>,
        useFilter: Boolean = false,
    ): Map<String, Double> {
        val exerciseIndex = exerciseList.associateBy { it.id.lowercase() }
        if (!useFilter) return calculateVolume(sessions, exerciseIndex).muscles.mapValues { it.value.sets }
        val volumeMap = mutableMapOf<String, Double>()

        for (session in sessions) {
            for (exercise in session.allExercises()) {
                val effectiveSets = countEffectiveSets(exercise.sets)
                if (effectiveSets <= 0) continue

                val musclesToCount = if (useFilter) {
                    // Preserve the legacy SSC-filtered path only for explicit
                    // callers; normal volume accounting is role-based.
                    ExerciseMuscleResolver.effectiveMuscles(exercise, exerciseIndex)
                } else {
                    ExerciseMuscleResolver.effectiveMusclesForVolume(exercise, exerciseIndex)
                }
                if (musclesToCount.isEmpty()) continue
                val contributions = buildPerExerciseMuscleContributions(musclesToCount)
                for ((canonical, multiplier) in contributions) {
                    volumeMap[canonical] = (volumeMap[canonical] ?: 0.0) + effectiveSets * multiplier
                }
            }
        }
        return volumeMap
    }

    fun calculateVolumeAdjustment(
        muscle: String,
        feedbackHistory: List<PostSessionFeedback>,
    ): Double {
        if (feedbackHistory.isEmpty()) return 1.0

        val normalizedTarget = normalizeCanonicalMuscleGroup(muscle).lowercase().trim()

        val muscleLogs = feedbackHistory.filter { log ->
            log.muscleFeedback.keys.any { key ->
                normalizeCanonicalMuscleGroup(key).lowercase().trim() == normalizedTarget
            }
        }
        if (muscleLogs.isEmpty()) return 1.0

        val recent = muscleLogs
            .sortedByDescending { it.date }
            .take(3)

        var totalDoms = 0.0
        var totalStr = 0.0
        var count = 0

        for (log in recent) {
            val entryKey = log.muscleFeedback.keys.find { key ->
                normalizeCanonicalMuscleGroup(key).lowercase().trim() == normalizedTarget
            } ?: continue
            val entry = log.muscleFeedback[entryKey] ?: continue
            totalDoms += entry.doms
            totalStr += entry.strengthCapacity
            count++
        }

        if (count == 0) return 1.0

        val avgDoms = totalDoms / count
        val avgStr = totalStr / count

        return when {
            avgDoms >= 3.5 || avgStr <= 5.0 -> 0.85 // Deuda de recuperación
            avgDoms <= 1.5 && avgStr >= 8.0 -> 1.10 // Subentrenamiento
            else -> 1.0 // Óptimo
        }
    }

    fun calculateCompletedWeeklyMuscleVolume(
        logs: List<WorkoutLog>,
        exerciseList: List<ExerciseMuscleInfo>,
        weeksCount: Int = 1,
        aliases: Map<String, String> = emptyMap(),
    ): List<CanonicalMuscleVolumeEntry> {
        return calculateVolume(
            sessions = completedSessions(logs),
            exerciseIndex = volumeIndex(exerciseList, aliases),
            divisor = weeksCount.coerceAtLeast(1).toDouble(),
        ).weeklyEntries()
    }

    private fun completedSessions(logs: List<WorkoutLog>): List<Session> = logs.map { log ->
            Session(
                id = log.id,
                name = log.sessionName,
                exercises = log.completedExercises.map { ex ->
                    Exercise(
                        id = ex.exerciseId,
                        name = ex.exerciseName,
                        exerciseDbId = ex.exerciseDbId,
                        exerciseId = ex.canonicalExerciseId,
                        canonicalExerciseId = ex.canonicalExerciseId,
                        catalogRevision = ex.catalogRevision,
                        catalogDefinitionId = ex.catalogDefinitionId,
                        catalogConfigurationId = ex.catalogConfigurationId,
                        performanceProfileId = ex.performanceProfileId,
                        occurrenceId = ex.occurrenceId,
                        variantName = ex.variantName,
                        selectedAspects = ex.selectedAspects,
                        effectiveMuscles = ex.effectiveMuscles,
                        sets = ex.sets.map { set ->
                            ExerciseSet(
                                id = set.id,
                                targetReps = set.reps,
                                weight = set.weight,
                                completedReps = if (set.skipped) 0 else set.reps,
                                completedRPE = set.rpe ?: set.actualIntensityValue
                                    ?.takeIf { set.actualIntensityMode == com.example.kpkn.data.models.IntensityMode.RPE },
                                completedRIR = set.rir ?: set.actualIntensityValue
                                    ?.takeIf { set.actualIntensityMode == com.example.kpkn.data.models.IntensityMode.RIR }?.toInt(),
                                isFailure = set.isFailure,
                                intensityMode = set.actualIntensityMode,
                                isIneffective = set.isWarmup || !AugeFatigueEngine.isSetEffective(set),
                            )
                        }
                    )
                }
            )
        }
}
