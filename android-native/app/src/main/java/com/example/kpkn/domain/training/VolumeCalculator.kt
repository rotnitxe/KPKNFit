package com.example.kpkn.domain.training

import com.example.kpkn.data.models.ExerciseMuscleInfo
import com.example.kpkn.data.models.ExerciseSet
import com.example.kpkn.data.models.MuscleRole
import com.example.kpkn.data.models.Program
import com.example.kpkn.data.models.ProgramWeek
import com.example.kpkn.data.models.Session
import com.example.kpkn.data.models.resolveMuscleVolumeContribution
import com.example.kpkn.domain.auge.SessionMuscleFilter

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

object VolumeCalculator {

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
        "cuello" to "Cuello",
        "cervical" to "Cuello",
        "pectoral" to "Pectorales",
        "pecho" to "Pectorales",
        "abdominal" to "Abdomen",
        "oblicuo" to "Abdomen",
        "core" to "Abdomen",
    )

    @Deprecated(
        message = "Use normalizeCanonicalMuscleGroup instead. This function maps 'core' → 'Abdomen' " +
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

        if (lower.contains("deltoides") || lower.contains("hombro")) {
            return when {
                emphasisLower.contains("anterior") -> "Deltoides anterior"
                emphasisLower.contains("lateral") || emphasisLower.contains("medio") -> "Deltoides lateral"
                emphasisLower.contains("posterior") -> "Deltoides posterior"
                else -> "Deltoides"
            }
        }
        if ((lower.contains("bíceps") || lower.contains("biceps") || lower.contains("braquial")) && !lower.contains("femoral")) return "Bíceps"
        if (lower.contains("tríceps") || lower.contains("triceps")) return "Tríceps"
        if (lower.contains("antebrazo") || lower.contains("braquiorradial")) return "Antebrazo"
        if (lower.contains("trapecio") || lower.contains("romboides")) return "Trapecio"
        if (lower.contains("dorsal") || lower.contains("lat") || lower.contains("redondo") || lower.contains("espalda")) return "Dorsales"
        if (lower.contains("erector") || lower.contains("lumbar")) return "Erectores Espinales"
        if (lower.contains("pectoral") || lower.contains("pecho")) return "Pectorales"
        if (lower.contains("cuádriceps") || lower.contains("cuadriceps") || lower.contains("recto femoral") || lower.contains("vasto")) return "Cuádriceps"
        if (lower.contains("isquio") || lower.contains("femoral") || lower.contains("semitendinoso") || lower.contains("semimembranoso")) return "Isquiosurales"
        if (lower.contains("glúteo") || lower.contains("gluteo") || lower.contains("tensor de la fascia lata")) return "Glúteos"
        if (lower.contains("adductor") || lower.contains("aductor") || lower.contains("pectíneo") || lower.contains("pectineo")) return "Aductores"
        if (lower.contains("gemelo") || lower.contains("pantorrilla") || lower.contains("gastrocnemio") || lower.contains("sóleo") || lower.contains("soleo")) return "Pantorrillas"
        if (lower.contains("cuello") || lower.contains("cervical")) return "Cuello"
        // Core: deep stabilizers — must be checked BEFORE Abdomen to avoid "core" → "Abdomen".
        if (lower == "core" || lower.contains("transverso") || lower.contains("serrato") || emphasisLower.contains("core")) return "Core"
        // Abdomen: rectus abdominis + obliques.
        if (lower.contains("abdominal") || lower.contains("abdomen") || lower.contains("oblicuo") || lower.contains("recto del abdomen")) return "Abdomen"

        return normalizeMuscleGroup(specificMuscle, emphasis)
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
            val contribution = resolveMuscleVolumeContribution(involvement)
            val current = grouped[canonicalMuscle] ?: 0.0
            if (contribution > current) {
                grouped[canonicalMuscle] = contribution
            }
        }
        return grouped.filterValues { it > 0.0 }
    }

    private fun countEffectiveSets(exerciseSets: List<ExerciseSet>): Int {
        val counted = exerciseSets.count { set ->
            !set.isIneffective && ((set.completedReps ?: set.targetReps ?: 0) > 0 || (set.weight ?: 0.0) > 0.0)
        }
        return if (counted == 0) exerciseSets.count { !it.isIneffective } else counted
    }

    fun calculateUnifiedMuscleVolume(
        sessions: List<Session>,
        exerciseList: List<ExerciseMuscleInfo>,
    ): List<MuscleVolumeEntry> {
        val volumeMap = mutableMapOf<String, Pair<Double, Int>>()
        val exIndex = exerciseList.associateBy { it.id.lowercase() }

        for (session in sessions) {
            val allExercises = if (session.parts.isNotEmpty()) {
                session.parts.flatMap { it.exercises }
            } else {
                session.exercises
            }

            for (exercise in allExercises) {
                val validSetsCount = countEffectiveSets(exercise.sets)

                if (validSetsCount > 0) {
                    val dbInfo = exercise.exerciseDbId?.let { exIndex[it.lowercase()] }
                    val involvedMuscles = dbInfo?.involvedMuscles ?: emptyList()

                    if (involvedMuscles.isNotEmpty()) {
                        // buildPerExerciseMuscleContributions already returns canonical keys;
                        // do NOT re-apply normalizeMuscleGroup here or "Core" collapses into "Abdomen".
                        val uniqueMultipliers = buildPerExerciseMuscleContributions(involvedMuscles)

                        for ((muscleName, maxMultiplier) in uniqueMultipliers) {
                            val (currentVol, currentSets) = volumeMap[muscleName] ?: (0.0 to 0)
                            volumeMap[muscleName] = currentVol + validSetsCount * maxMultiplier to currentSets + validSetsCount
                        }
                    }
                }
            }
        }

        return volumeMap.entries
            .filter { it.key != "General" }
            .map { (muscleName, vol) ->
                MuscleVolumeEntry(
                    muscleId = muscleName.lowercase().replace(" ", "-"),
                    muscleName = muscleName,
                    displayVolume = (vol.first * 10).toInt().toDouble() / 10,
                    sets = vol.second,
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
        val safeDivisor = divisor.takeIf { it > 0.0 } ?: 1.0

        val exerciseIndex = exerciseList.associateBy { it.id.lowercase() }
        val volumeMap = mutableMapOf<String, Double>()

        for (session in sessions) {
            val sessionVolume = calculateSessionAssistantMuscleVolume(session, exerciseIndex)
            sessionVolume.forEach { (muscleName, sessionSets) ->
                volumeMap[muscleName] = (volumeMap[muscleName] ?: 0.0) + sessionSets
            }
        }

        return volumeMap.entries
            .map { (muscleName, totalSets) ->
                val weeklySets = totalSets / safeDivisor
                CanonicalMuscleVolumeEntry(
                    muscleId = muscleName.lowercase().replace(" ", "-"),
                    muscleName = muscleName,
                    weeklySets = (weeklySets * 10.0).toInt() / 10.0,
                )
            }
            .sortedByDescending { it.weeklySets }
    }

    private fun calculateSessionAssistantMuscleVolume(
        session: Session,
        exerciseIndex: Map<String, ExerciseMuscleInfo>,
    ): Map<String, Double> {
        val volumeMap = mutableMapOf<String, Double>()

        session.allExercises().forEach { exercise ->
            val effectiveSets = countEffectiveSets(exercise.sets)
            if (effectiveSets <= 0) return@forEach

            val dbInfo = exercise.exerciseDbId?.let { exerciseIndex[it.lowercase()] } ?: return@forEach
            SessionMuscleFilter.relevantMusclesFor(dbInfo).forEach { muscle ->
                val normalized = normalizeCanonicalMuscleGroup(muscle.muscle, muscle.emphasis)
                val contribution = resolveMuscleVolumeContribution(muscle)
                if (contribution > 0.0) {
                    volumeMap[normalized] = (volumeMap[normalized] ?: 0.0) + (effectiveSets * contribution)
                }
            }
        }

        return volumeMap
    }
}
