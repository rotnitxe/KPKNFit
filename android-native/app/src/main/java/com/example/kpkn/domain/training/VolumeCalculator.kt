package com.example.kpkn.domain.training

import com.example.kpkn.data.models.ExerciseMuscleInfo
import com.example.kpkn.data.models.ExerciseSet
import com.example.kpkn.data.models.HYPERTROPHY_ROLE_MULTIPLIERS
import com.example.kpkn.data.models.MuscleRole
import com.example.kpkn.data.models.Session

data class MuscleVolumeEntry(
    val muscleId: String,
    val muscleName: String,
    val displayVolume: Double,
    val sets: Int,
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
}
