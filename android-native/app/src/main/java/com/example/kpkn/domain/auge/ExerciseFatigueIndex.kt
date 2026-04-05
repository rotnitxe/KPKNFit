package com.example.kpkn.domain.auge

import kotlin.math.roundToInt

data class ExerciseFatigueUserIndex(
    val muscle: Int,
    val snc: Int,
    val spinal: Int,
    val overall: Int,
)

object ExerciseFatigueIndex {
    private const val MAX_EFC = 5.0
    private const val MAX_CNC = 5.0
    private const val MAX_SSC = 2.0

    private const val DEFAULT_EFC = 2.5
    private const val DEFAULT_CNC = 2.5
    private const val DEFAULT_SSC = 0.5

    // Same relative impact used by AUGE drains (muscular/cns/spinal)
    private const val W_MUSCLE = 3.5
    private const val W_SNC = 3.0
    private const val W_SPINAL = 4.0

    fun fromIntrinsic(
        efc: Double?,
        cnc: Double?,
        ssc: Double?,
    ): ExerciseFatigueUserIndex {
        val muscle = scaleToTen((efc ?: DEFAULT_EFC).coerceIn(1.0, MAX_EFC), MAX_EFC)
        val snc = scaleToTen((cnc ?: DEFAULT_CNC).coerceIn(1.0, MAX_CNC), MAX_CNC)
        val spinal = scaleToTen((ssc ?: DEFAULT_SSC).coerceIn(0.0, MAX_SSC), MAX_SSC)

        val weightedOverall = (
            (muscle * W_MUSCLE) +
                (snc * W_SNC) +
                (spinal * W_SPINAL)
            ) / (W_MUSCLE + W_SNC + W_SPINAL)

        return ExerciseFatigueUserIndex(
            muscle = muscle,
            snc = snc,
            spinal = spinal,
            overall = weightedOverall.roundToInt().coerceIn(1, 10),
        )
    }

    private fun scaleToTen(value: Double, max: Double): Int {
        if (max <= 0.0) return 1
        return ((value / max) * 10.0).roundToInt().coerceIn(1, 10)
    }
}
