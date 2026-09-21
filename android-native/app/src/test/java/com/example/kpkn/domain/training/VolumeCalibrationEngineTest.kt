package com.example.kpkn.domain.training

import com.example.kpkn.data.models.AthleteProfileLevel
import com.example.kpkn.data.models.ProgramMode
import com.example.kpkn.data.models.TrainingStyle
import com.example.kpkn.data.models.VolumeRecommendation
import org.junit.Assert.assertEquals
import org.junit.Assert.assertSame
import org.junit.Assert.assertTrue
import org.junit.Test

class VolumeCalibrationEngineTest {
    @Test
    fun calibrationIsDeterministicAndKeepsRecommendationsWithinTheirContracts() {
        val low = VolumeCalibrationEngine.calculate(
            style = TrainingStyle.BODYBUILDER,
            technique = 1,
            consistency = 1,
            strength = 1,
            mobility = 1,
        )
        val repeated = VolumeCalibrationEngine.calculate(
            style = TrainingStyle.BODYBUILDER,
            technique = 1,
            consistency = 1,
            strength = 1,
            mobility = 1,
        )
        val high = VolumeCalibrationEngine.calculate(
            style = TrainingStyle.BODYBUILDER,
            technique = 3,
            consistency = 3,
            strength = 3,
            mobility = 3,
        )

        assertEquals(low, repeated)
        assertEquals(ProgramMode.HYPERTROPHY, low.mode)
        assertEquals(4, low.score.totalScore)
        assertEquals(AthleteProfileLevel.BEGINNER, low.score.profileLevel)
        assertEquals(12, high.score.totalScore)
        assertEquals(AthleteProfileLevel.ADVANCED, high.score.profileLevel)
        assertTrue(low.recommendations.isNotEmpty())
        low.recommendations.forEach { recommendation ->
            assertTrue(recommendation.minEffectiveVolume <= recommendation.maxAdaptiveVolume)
            assertTrue(recommendation.maxAdaptiveVolume < recommendation.maxRecoverableVolume)
            assertTrue(recommendation.frequencyCap > 0)
        }
    }

    @Test
    fun specificProgramRecommendationsTakePrecedenceOverGlobalCalibration() {
        val program = listOf(VolumeRecommendation("Pectorales", 10, 14, 18))
        val global = listOf(VolumeRecommendation("Pectorales", 8, 12, 16))
        val general = listOf(VolumeRecommendation("Pectorales", 6, 10, 14))

        assertSame(program, VolumeCalibrationEngine.effectiveRecommendations(program, global, general))
        assertSame(global, VolumeCalibrationEngine.effectiveRecommendations(emptyList(), global, general))
        assertSame(general, VolumeCalibrationEngine.effectiveRecommendations(emptyList(), emptyList(), general))
    }
}
