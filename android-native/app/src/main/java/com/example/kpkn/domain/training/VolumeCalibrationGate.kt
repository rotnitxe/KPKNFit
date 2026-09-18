package com.example.kpkn.domain.training

import com.example.kpkn.data.models.Program

/**
 * Puerta única de calibración de volumen. Calibrado =
 * recommendations no vacías + athleteProfileScore presente
 * (mismo criterio que VolumeView/ProgramDetailScreen/SessionEditorAUGE).
 */
object VolumeCalibrationGate {
    fun isVolumeCalibrated(program: Program): Boolean =
        program.volumeRecommendations.isNotEmpty() && program.athleteProfileScore != null
}
