package com.example.kpkn.data.programs

import kotlinx.serialization.Serializable

@Serializable
enum class KpknMuscleGroup {
    CHEST,
    BACK_LATS,
    BACK_UPPER,
    QUADS,
    HAMS,
    GLUTES,
    ERECTORS,
    DELT_FRONT,
    DELT_LATERAL,
    DELT_REAR,
    BICEPS,
    TRICEPS,
    CALVES,
    CORE,
    FOREARMS,
    NECK,
    ADDUCTORS,
}

data class VolumeLandmark(
    val mev: Int,
    val mav: Int,
    val mrv: Int,
)

object VolumeLandmarks {
    val byGroup: Map<KpknMuscleGroup, VolumeLandmark> = mapOf(
        KpknMuscleGroup.CHEST to VolumeLandmark(8, 14, 22),
        KpknMuscleGroup.BACK_LATS to VolumeLandmark(10, 16, 25),
        KpknMuscleGroup.BACK_UPPER to VolumeLandmark(6, 12, 20),
        KpknMuscleGroup.QUADS to VolumeLandmark(8, 14, 20),
        KpknMuscleGroup.HAMS to VolumeLandmark(6, 12, 20),
        KpknMuscleGroup.GLUTES to VolumeLandmark(0, 8, 16),
        KpknMuscleGroup.ERECTORS to VolumeLandmark(0, 8, 16),
        KpknMuscleGroup.DELT_FRONT to VolumeLandmark(0, 8, 16),
        KpknMuscleGroup.DELT_LATERAL to VolumeLandmark(8, 16, 26),
        KpknMuscleGroup.DELT_REAR to VolumeLandmark(6, 12, 20),
        KpknMuscleGroup.BICEPS to VolumeLandmark(8, 14, 26),
        KpknMuscleGroup.TRICEPS to VolumeLandmark(6, 12, 18),
        KpknMuscleGroup.CALVES to VolumeLandmark(8, 12, 20),
        KpknMuscleGroup.CORE to VolumeLandmark(0, 8, 25),
        KpknMuscleGroup.FOREARMS to VolumeLandmark(0, 8, 16),
        KpknMuscleGroup.NECK to VolumeLandmark(0, 6, 12),
        KpknMuscleGroup.ADDUCTORS to VolumeLandmark(0, 6, 12),
    )
}
