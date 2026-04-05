package com.example.kpkn.domain.training

import com.example.kpkn.data.models.ExerciseMuscleInfo
import com.example.kpkn.data.models.ExerciseSet
import com.example.kpkn.data.models.HYPERTROPHY_ROLE_MULTIPLIERS
import com.example.kpkn.data.models.MuscleRole
import com.example.kpkn.data.models.Program
import com.example.kpkn.data.models.Session
import com.example.kpkn.data.models.totalProgramWeeks

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
        if (lower == "core" || lower.contains("transverso") || lower.contains("serrato") || emphasisLower.contains("core")) return "Core"
        if (lower.contains("abdominal") || lower.contains("abdomen") || lower.contains("oblicuo")) return "Abdomen"

        return normalizeMuscleGroup(specificMuscle, emphasis)
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
                val validSetsCount = exercise.sets.count { set ->
                    !set.isIneffective && ((set.completedReps ?: set.targetReps ?: 0) > 0 || (set.weight ?: 0.0) > 0.0)
                }

                if (validSetsCount > 0) {
                    val dbInfo = exercise.exerciseDbId?.let { exIndex[it.lowercase()] }
                    val involvedMuscles = dbInfo?.involvedMuscles ?: emptyList()

                    if (involvedMuscles.isNotEmpty()) {
                        val uniqueMultipliers = mutableMapOf<String, Double>()

                        for (m in involvedMuscles) {
                            val muscleName = normalizeMuscleGroup(m.muscle)
                            val multiplier = HYPERTROPHY_ROLE_MULTIPLIERS[m.role] ?: 0.5
                            val currentMax = uniqueMultipliers[muscleName] ?: 0.0
                            if (multiplier > currentMax) {
                                uniqueMultipliers[muscleName] = multiplier
                            }
                        }

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
        val totalWeeks = program.totalProgramWeeks.coerceAtLeast(1)
        val sessions = program.macrocycles
            .flatMap { it.blocks }
            .flatMap { it.mesocycles }
            .flatMap { it.weeks }
            .flatMap { it.sessions }

        val exerciseIndex = exerciseList.associateBy { it.id.lowercase() }
        val volumeMap = mutableMapOf<String, Double>()

        for (session in sessions) {
            val allExercises = if (session.parts.isNotEmpty()) {
                session.parts.flatMap { it.exercises }
            } else {
                session.exercises
            }

            for (exercise in allExercises) {
                val effectiveSets = exercise.sets.count { set ->
                    !set.isIneffective && ((set.completedReps ?: set.targetReps ?: 0) > 0 || (set.weight ?: 0.0) > 0.0)
                }
                if (effectiveSets <= 0) continue

                val dbInfo = exercise.exerciseDbId?.let { exerciseIndex[it.lowercase()] } ?: continue
                if (dbInfo.involvedMuscles.isEmpty()) continue

                val perExerciseMuscles = mutableMapOf<String, Double>()
                dbInfo.involvedMuscles.forEach { muscle ->
                    val canonicalMuscle = normalizeCanonicalMuscleGroup(muscle.muscle, muscle.emphasis)
                    val multiplier = HYPERTROPHY_ROLE_MULTIPLIERS[muscle.role] ?: 0.5
                    val current = perExerciseMuscles[canonicalMuscle] ?: 0.0
                    if (multiplier > current) {
                        perExerciseMuscles[canonicalMuscle] = multiplier
                    }
                }

                perExerciseMuscles.forEach { (muscleName, multiplier) ->
                    val current = volumeMap[muscleName] ?: 0.0
                    volumeMap[muscleName] = current + (effectiveSets * multiplier)
                }
            }
        }

        return volumeMap.entries
            .map { (muscleName, totalSets) ->
                CanonicalMuscleVolumeEntry(
                    muscleId = muscleName.lowercase().replace(" ", "-"),
                    muscleName = muscleName,
                    weeklySets = ((totalSets / totalWeeks) * 10.0).toInt() / 10.0,
                )
            }
            .sortedByDescending { it.weeklySets }
    }
}
