package com.example.kpkn.screens.programdetail.components

import com.example.kpkn.data.models.TrainingStyle
import com.example.kpkn.data.models.VolumeRecommendation
import com.example.kpkn.domain.training.VolumeCalculator
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * Calibración de volumen (P7): los objetivos por músculo que sale de la calibración. Se quitaron las comprobaciones del texto de estado,
 * que dependían de una función sin uso; las de dominio se quedan.
 */
class ProgramHeroWidgetsCalibrationTest {

    @Test
    fun calibration_uses_canonical_deltoids_without_summing_heads() {
        val calibration = buildVolumeCalibration(
            style = TrainingStyle.BODYBUILDER,
            technique = 2,
            consistency = 2,
            strength = 2,
            mobility = 2,
        )

        val deltoidTargets = calibration.recommendations.canonicalTarget("Deltoides")

        assertTrue(deltoidTargets.minEffectiveVolume <= 16)
        assertTrue(deltoidTargets.maxAdaptiveVolume >= 16)
        assertFalse(
            calibration.recommendations.any {
                it.muscleGroup == "Deltoides Anterior" ||
                    it.muscleGroup == "Deltoides Lateral" ||
                    it.muscleGroup == "Deltoides Posterior"
            },
        )
    }

    @Test
    fun calibration_glute_minimum_exceeds_six_sets() {
        val calibration = buildVolumeCalibration(
            style = TrainingStyle.POWERLIFTER,
            technique = 1,
            consistency = 1,
            strength = 1,
            mobility = 1,
        )

        val gluteTargets = calibration.recommendations.canonicalTarget("Glúteos")
        assertTrue(gluteTargets.minEffectiveVolume > 6)
    }

    private fun List<VolumeRecommendation>.canonicalTarget(muscleName: String): VolumeRecommendation {
        val grouped = groupBy { VolumeCalculator.normalizeCanonicalMuscleGroup(it.muscleGroup) }
        val matches = grouped.getValue(muscleName)
        return VolumeRecommendation(
            muscleGroup = muscleName,
            minEffectiveVolume = matches.sumOf { it.minEffectiveVolume },
            maxAdaptiveVolume = matches.sumOf { it.maxAdaptiveVolume },
            maxRecoverableVolume = matches.sumOf { it.maxRecoverableVolume },
            frequencyCap = matches.maxOf { it.frequencyCap },
        )
    }
}
